package com.fluortronix.fluortronixapp.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluortronix.fluortronixapp.data.datasource.ESPDeviceService
import com.fluortronix.fluortronixapp.data.models.Device
import com.fluortronix.fluortronixapp.data.models.Room
import com.fluortronix.fluortronixapp.data.PreferencesManager
import com.fluortronix.fluortronixapp.data.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject

data class DeviceDetailsUiState(
    val device: Device? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    // Real-time control state
    val isRealTimeControlEnabled: Boolean = false,
    val espCommunicationError: String? = null,
    val lastSliderValues: List<Int> = emptyList(),
    // New properties for device management
    val isBlinking: Boolean = false,
    val availableRooms: List<Room> = emptyList(),
    val showDeleteConfirmation: Boolean = false,
    val showRoomSelection: Boolean = false
)

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

// Data class for debounced PWM slider changes
private data class PwmSliderChange(
    val sliderIndex: Int,
    val value: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val espDeviceService: ESPDeviceService,
    private val deviceRepository: DeviceRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(DeviceDetailsUiState())
    val uiState: StateFlow<DeviceDetailsUiState> = _uiState.asStateFlow()
    
    // ESP Communication state
    private val pwmSliderChangeFlow = MutableSharedFlow<PwmSliderChange>()
    private var espCommunicationJob: Job? = null
    
    companion object {
        private const val PWM_DEBOUNCE_DELAY = 200L // 200ms debounce for PWM sliders
        private const val PWM_MIN_VALUE = 0
        private const val PWM_MAX_VALUE = 255
    }
    
    init {
        // Set up debounced ESP communication for PWM sliders
        setupPwmCommunication()
    }
    
    /**
     * Initializes the view model with a device ID
     */
    fun initialize(deviceId: String) {
        loadDeviceDetails(deviceId)
    }
    
    /**
     * Loads device details and validates network connectivity
     */
    private fun loadDeviceDetails(deviceId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // Load device from local storage
                val savedDevice = loadDeviceFromStorage(deviceId)
                if (savedDevice != null) {
                    // Set initial device state as offline until validated
                    val initialDevice = savedDevice.copy(isOnline = false)
                    
                    _uiState.value = _uiState.value.copy(
                        device = initialDevice,
                        connectionStatus = ConnectionStatus.DISCONNECTED,
                        isLoading = false
                    )
                    
                    // Validate network connectivity if device has IP address
                    if (!savedDevice.ipAddress.isNullOrBlank()) {
                        _uiState.value = _uiState.value.copy(connectionStatus = ConnectionStatus.CONNECTING)
                        
                        val networkValidation = espDeviceService.validateDeviceNetworkConnectivity(savedDevice)
                        if (networkValidation.isSuccess) {
                            // Network validation passed, now get device info
                            val deviceInfo = espDeviceService.getDeviceInfo(savedDevice)
                            if (deviceInfo.isSuccess) {
                                val updatedDevice = deviceInfo.getOrThrow()
                                _uiState.value = _uiState.value.copy(
                                    device = updatedDevice,
                                    connectionStatus = ConnectionStatus.CONNECTED
                                )
                                
                                // Save updated device state
                                preferencesManager.saveDeviceData(deviceId, updatedDevice)
                            } else {
                                _uiState.value = _uiState.value.copy(
                                    connectionStatus = ConnectionStatus.ERROR,
                                    error = "Device unreachable: ${deviceInfo.exceptionOrNull()?.message}"
                                )
                            }
                        } else {
                            _uiState.value = _uiState.value.copy(
                                connectionStatus = ConnectionStatus.ERROR,
                                error = "Network validation failed: ${networkValidation.exceptionOrNull()?.message}"
                            )
                        }
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Device not found",
                        isLoading = false
                    )
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load device: ${e.message}",
                    isLoading = false,
                    connectionStatus = ConnectionStatus.ERROR
                )
            }
        }
    }
    
    /**
     * Sets up debounced ESP communication for real-time PWM slider control
     */
    private fun setupPwmCommunication() {
        espCommunicationJob?.cancel()
        espCommunicationJob = viewModelScope.launch {
            pwmSliderChangeFlow
                .debounce(PWM_DEBOUNCE_DELAY) // Debounce to prevent overloading ESP
                .collect { pwmChange ->
                    sendPwmChangeToEsp(pwmChange)
                }
        }
    }
    
    /**
     * Sends PWM slider change to ESP device
     */
    private suspend fun sendPwmChangeToEsp(pwmChange: PwmSliderChange) {
        val device = _uiState.value.device
        if (device?.ipAddress == null || !_uiState.value.isRealTimeControlEnabled) {
            return
        }

        try {
            // Clear any previous ESP communication errors
            _uiState.update { it.copy(espCommunicationError = null) }
            
            // Ensure PWM value is within valid range
            val pwmValue = pwmChange.value.coerceIn(PWM_MIN_VALUE, PWM_MAX_VALUE)
            
            // Send single slider value to ESP
            val result = espDeviceService.setSliderValue(device, pwmChange.sliderIndex, pwmValue)
            
            if (result.isFailure) {
                _uiState.update { 
                    it.copy(espCommunicationError = "Failed to send PWM data: ${result.exceptionOrNull()?.message}")
                }
            } else {
                // Update last known slider values on success
                val currentValues = _uiState.value.lastSliderValues.toMutableList()
                if (pwmChange.sliderIndex < currentValues.size) {
                    currentValues[pwmChange.sliderIndex] = pwmValue
                    _uiState.update { it.copy(lastSliderValues = currentValues) }
                }
            }
            
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(espCommunicationError = "ESP communication error: ${e.message}")
            }
        }
    }
    
    /**
     * Handle PWM slider change with real-time ESP communication
     */
    fun onPwmSliderChange(sliderIndex: Int, value: Int) {
        if (_uiState.value.isRealTimeControlEnabled) {
            viewModelScope.launch {
                pwmSliderChangeFlow.emit(PwmSliderChange(sliderIndex, value))
            }
        }
        
        // Update local state immediately for responsive UI
        val currentValues = _uiState.value.lastSliderValues.toMutableList()
        while (currentValues.size <= sliderIndex) {
            currentValues.add(0)
        }
        currentValues[sliderIndex] = value
        _uiState.update { it.copy(lastSliderValues = currentValues) }
    }
    
    /**
     * Enable real-time ESP control with network validation
     */
    fun enableRealTimeControl() {
        val device = _uiState.value.device
        if (device?.ipAddress != null) {
            _uiState.update { 
                it.copy(
                    isRealTimeControlEnabled = true,
                    espCommunicationError = null,
                    connectionStatus = ConnectionStatus.CONNECTING
                )
            }
            
            // Validate network connectivity first, then sync current values
            viewModelScope.launch {
                try {
                    // First validate network connectivity
                    val networkValidation = espDeviceService.validateDeviceNetworkConnectivity(device)
                    if (!networkValidation.isSuccess) {
                        _uiState.update { 
                            it.copy(
                                isRealTimeControlEnabled = false,
                                espCommunicationError = "Network validation failed: ${networkValidation.exceptionOrNull()?.message}",
                                connectionStatus = ConnectionStatus.ERROR
                            )
                        }
                        return@launch
                    }
                    
                    // Network validation passed, now get device info
                    val result = espDeviceService.getDeviceInfo(device)
                    if (result.isSuccess) {
                        val updatedDevice = result.getOrThrow()
                        _uiState.update { 
                            it.copy(
                                device = updatedDevice,
                                lastSliderValues = updatedDevice.sliderValues,
                                connectionStatus = ConnectionStatus.CONNECTED
                            )
                        }
                        
                        // Setup PWM communication for real-time control
                        setupPwmCommunication()
                    } else {
                        _uiState.update { 
                            it.copy(
                                isRealTimeControlEnabled = false,
                                espCommunicationError = "Failed to connect to ESP device: ${result.exceptionOrNull()?.message}",
                                connectionStatus = ConnectionStatus.ERROR
                            )
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { 
                        it.copy(
                            isRealTimeControlEnabled = false,
                            espCommunicationError = "ESP connection error: ${e.message}",
                            connectionStatus = ConnectionStatus.ERROR
                        )
                    }
                }
            }
        } else {
            _uiState.update { 
                it.copy(espCommunicationError = "Device IP address not available")
            }
        }
    }
    
    /**
     * Disable real-time ESP control
     */
    fun disableRealTimeControl() {
        _uiState.update { 
            it.copy(
                isRealTimeControlEnabled = false,
                espCommunicationError = null,
                connectionStatus = ConnectionStatus.DISCONNECTED
            )
        }
    }

    /**
     * Load available rooms for device reassignment
     */
    fun loadAvailableRooms() {
        viewModelScope.launch {
            try {
                val rooms = preferencesManager.getAllSavedRooms()
                val device = _uiState.value.device
                
                // Filter rooms that can accept this device model
                val compatibleRooms = if (device != null) {
                    rooms.filter { room ->
                        room.canAddDeviceModel(device.deviceModel) &&
                        room.id != device.roomId // Exclude current room
                    }
                } else {
                    rooms
                }
                
                _uiState.update { 
                    it.copy(availableRooms = compatibleRooms)
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(espCommunicationError = "Failed to load rooms: ${e.message}")
                }
            }
        }
    }

    /**
     * Show delete confirmation dialog
     */
    fun showDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = true) }
    }

    /**
     * Hide delete confirmation dialog
     */
    fun hideDeleteConfirmation() {
        _uiState.update { it.copy(showDeleteConfirmation = false) }
    }

    /**
     * Show room selection dialog
     */
    fun showRoomSelection() {
        loadAvailableRooms()
        _uiState.update { it.copy(showRoomSelection = true) }
    }

    /**
     * Hide room selection dialog
     */
    fun hideRoomSelection() {
        _uiState.update { it.copy(showRoomSelection = false) }
    }

    /**
     * Delete the device from local storage
     */
    fun deleteDevice(onSuccess: () -> Unit) {
        val deviceId = _uiState.value.device?.id ?: return
        
        viewModelScope.launch {
            try {
                // Remove device from room first if assigned
                _uiState.value.device?.roomId?.let { roomId ->
                    preferencesManager.removeDeviceFromRoom(deviceId)
                }
                
                // Delete device data using repository (this should trigger home screen refresh)
                deviceRepository.removeDevice(deviceId)
                
                // Clear any stored previous values
                preferencesManager.clearDevicePreviousValues(deviceId)
                
                // Navigate back on successful deletion
                onSuccess()
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        espCommunicationError = "Failed to delete device: ${e.message}",
                        showDeleteConfirmation = false
                    )
                }
            }
        }
    }

    /**
     * Change device room assignment
     */
    fun changeDeviceRoom(newRoomId: String) {
        val device = _uiState.value.device ?: return
        
        viewModelScope.launch {
            try {
                // Remove from current room first
                if (device.roomId != null) {
                    preferencesManager.removeDeviceFromRoom(device.id)
                }
                
                // Assign to new room
                val success = preferencesManager.assignDeviceToRoom(device.id, newRoomId)
                
                if (success) {
                    // Reload device data to reflect changes
                    initialize(device.id)
                    _uiState.update { 
                        it.copy(
                            showRoomSelection = false,
                            espCommunicationError = null
                        )
                    }
                } else {
                    _uiState.update { 
                        it.copy(espCommunicationError = "Failed to change room assignment")
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(espCommunicationError = "Failed to change room: ${e.message}")
                }
            }
        }
    }

    /**
     * Make device blink for identification (blink LEDs 5 times)
     */
    fun blinkDevice() {
        val device = _uiState.value.device
        if (device?.ipAddress == null || device.sliderNames.isEmpty()) {
            _uiState.update { 
                it.copy(espCommunicationError = "Device not available for blinking")
            }
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isBlinking = true, espCommunicationError = null) }
                
                println("DEBUG: Blinking device ${device.name}, getting current ESP slider values")
                
                // Get current slider values directly from ESP device (actual current state)
                val deviceInfo = espDeviceService.getDeviceInfo(device)
                val originalValues = if (deviceInfo.isSuccess) {
                    val currentDevice = deviceInfo.getOrThrow()
                    currentDevice.sliderValues.mapIndexed { index, value ->
                        index to value
                    }.toMap()
                } else {
                    // Fallback to empty map if we can't get current state
                    emptyMap<Int, Int>()
                }
                
                // Blink sequence: 5 cycles of on/off
                repeat(5) { cycle ->
                    // Turn on (set to max brightness)
                    val maxValues = (0 until device.numSliders).associateWith { 255 }
                    espDeviceService.batchUpdateSliders(device, maxValues)
                    delay(300) // On for 300ms
                    
                    // Turn off
                    val zeroValues = (0 until device.numSliders).associateWith { 0 }
                    espDeviceService.batchUpdateSliders(device, zeroValues)
                    delay(300) // Off for 300ms
                }
                
                // Restore original ESP values
                if (originalValues.isNotEmpty()) {
                    println("DEBUG: Restoring original ESP values after blink: $originalValues")
                    espDeviceService.batchUpdateSliders(device, originalValues)
                } else {
                    println("DEBUG: No original values available, device remains at current state")
                }
                
                _uiState.update { it.copy(isBlinking = false) }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isBlinking = false,
                        espCommunicationError = "Blink failed: ${e.message}"
                    )
                }
            }
        }
    }
    

    
    /**
     * Clears any error messages
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Loads device data from local storage
     */
    private suspend fun loadDeviceFromStorage(deviceId: String): Device? {
        return try {
            preferencesManager.getDeviceData(deviceId)
        } catch (e: Exception) {
            null
        }
    }
} 