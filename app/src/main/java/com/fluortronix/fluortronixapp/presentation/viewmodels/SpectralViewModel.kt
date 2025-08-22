package com.fluortronix.fluortronixapp.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluortronix.fluortronixapp.data.models.*
import com.fluortronix.fluortronixapp.data.parser.ExcelParser
import com.fluortronix.fluortronixapp.data.PreferencesManager
import com.fluortronix.fluortronixapp.data.datasource.ESPDeviceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.io.InputStream
import javax.inject.Inject

data class SpectralUiState(
    val activeLightSources: List<LightSource> = emptyList(),
    val sliderValues: Map<String, Float> = emptyMap(),
    val graphData: List<GraphPoint> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasSpectralData: Boolean = false,
    val excelFileName: String? = null,
    val spectrumPresets: List<SpectrumPreset> = emptyList(),
    val masterSliderConfig: MasterSliderConfig = MasterSliderConfig(),
    // Updated ESP communication properties for multiple devices
    val connectedDevices: List<Device> = emptyList(),
    val isEspCommunicationEnabled: Boolean = false,
    val espCommunicationError: String? = null,
    // Store previous PWM values for each device for power toggle
    val devicePreviousValues: Map<String, List<Int>> = emptyMap()
)

// Data class for debounced slider changes
private data class SliderChange(
    val sourceName: String,
    val value: Float,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class SpectralViewModel @Inject constructor(
    private val excelParser: ExcelParser,
    private val preferencesManager: PreferencesManager,
    private val espDeviceService: ESPDeviceService
) : ViewModel() {

    private val _uiState = MutableStateFlow(SpectralUiState())
    val uiState: StateFlow<SpectralUiState> = _uiState.asStateFlow()

    private var currentProfile: SpectralProfile? = null
    private var currentRoomId: String? = null
    
    // ESP Communication state
    private val sliderChangeFlow = MutableSharedFlow<SliderChange>()
    private var espCommunicationJob: Job? = null
    
    companion object {
        private const val ESP_DEBOUNCE_DELAY = 300L // 300ms debounce to prevent ESP overload
        private const val ESP_MAX_PWM_VALUE = 255
        private const val SPECTRAL_SLIDER_MAX = 1.0f
    }

    init {
        // Set up debounced ESP communication
        setupEspCommunication()
    }

    /**
     * Sets up debounced ESP communication for real-time slider control
     */
    private fun setupEspCommunication() {
        espCommunicationJob?.cancel()
        espCommunicationJob = viewModelScope.launch {
            sliderChangeFlow
                .debounce(ESP_DEBOUNCE_DELAY) // Debounce to prevent overloading ESP
                .collect { sliderChange ->
                    sendSliderChangeToEsp(sliderChange)
                }
        }
    }

    /**
     * Sends slider change to all connected ESP devices
     */
    private suspend fun sendSliderChangeToEsp(sliderChange: SliderChange) {
        val connectedDevices = _uiState.value.connectedDevices
        println("DEBUG: sendSliderChangeToEsp called - ${sliderChange.sourceName} = ${sliderChange.value}, Connected devices: ${connectedDevices.size}, ESP enabled: ${_uiState.value.isEspCommunicationEnabled}")
        if (connectedDevices.isEmpty() || !_uiState.value.isEspCommunicationEnabled) {
            println("DEBUG: Skipping ESP send - no connected devices or ESP communication disabled")
            return
        }

        try {
            // Clear any previous ESP communication errors
            _uiState.update { it.copy(espCommunicationError = null) }
            
            // Convert spectral slider value (0.0-1.0) to PWM value (0-255)
            val pwmValue = (sliderChange.value * ESP_MAX_PWM_VALUE).toInt().coerceIn(0, ESP_MAX_PWM_VALUE)
            println("DEBUG: Slider ${sliderChange.sourceName}: ${sliderChange.value * 100}% -> PWM: $pwmValue")
            
            // Send to all connected devices simultaneously
            connectedDevices.forEach { device ->
                viewModelScope.launch {
                    val sliderIndex = findSliderIndexForSource(sliderChange.sourceName, device)
                    
                    if (sliderIndex >= 0) {
                        // Send single slider value to ESP with optimized retry
                        val result = espDeviceService.setSliderValueOptimized(device, sliderIndex, pwmValue)
                        
                        if (result.isFailure) {
                            _uiState.update { 
                                it.copy(espCommunicationError = "Failed to send slider data to ${device.name}: ${result.exceptionOrNull()?.message}")
                            }
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(espCommunicationError = "ESP communication error: ${e.message}")
            }
        }
    }

    /**
     * Maps spectral light source names to ESP slider indices
     */
    private fun findSliderIndexForSource(sourceName: String, device: Device): Int {
        // Try to match by exact name first
        val exactIndex = device.sliderNames.indexOfFirst { it.equals(sourceName, ignoreCase = true) }
        if (exactIndex >= 0) return exactIndex
        
        // Try to match by partial name (for common light sources)
        val partialMatches = mapOf(
            "red" to listOf("red", "r", "660"),
            "blue" to listOf("blue", "b", "450", "470"),
            "green" to listOf("green", "g", "530"),
            "white" to listOf("white", "w", "cool", "warm"),
            "uv" to listOf("uv", "ultraviolet", "365", "385"),
            "far red" to listOf("far", "730", "fr"),
            "violet" to listOf("violet", "v", "420"),
            "cyan" to listOf("cyan", "c", "490")
        )
        
        val normalizedSource = sourceName.lowercase()
        partialMatches[normalizedSource]?.let { keywords ->
            device.sliderNames.forEachIndexed { index, sliderName ->
                if (keywords.any { sliderName.lowercase().contains(it) }) {
                    return index
                }
            }
        }
        
        // If no match found, try to use the index based on order in light sources
        val currentSources = _uiState.value.activeLightSources
        val sourceIndex = currentSources.indexOfFirst { it.name == sourceName }
        return if (sourceIndex >= 0 && sourceIndex < device.sliderNames.size) sourceIndex else -1
    }

    /**
     * Send preset slider values to all connected ESP devices
     */
    private suspend fun sendPresetToEspDevices(presetSliderValues: Map<String, Float>) {
        try {
            _uiState.update { it.copy(espCommunicationError = null) }
            
            val currentState = _uiState.value
            val connectedDevices = currentState.connectedDevices
            
            connectedDevices.forEach { device ->
                viewModelScope.launch {
                    val sliderUpdates = mutableMapOf<Int, Int>()
                    
                    // Collect all slider updates for this device from preset values
                    for ((sourceName, value) in presetSliderValues) {
                        val sliderIndex = findSliderIndexForSource(sourceName, device)
                        if (sliderIndex >= 0) {
                            val pwmValue = (value * ESP_MAX_PWM_VALUE).toInt().coerceIn(0, ESP_MAX_PWM_VALUE)
                            sliderUpdates[sliderIndex] = pwmValue
                            println("DEBUG: Preset Slider $sourceName: ${value * 100}% -> PWM: $pwmValue")
                        }
                    }
                    
                    // Send batch update for all preset sliders at once
                    if (sliderUpdates.isNotEmpty()) {
                        val result = espDeviceService.batchUpdateSliders(device, sliderUpdates)
                        if (result.isFailure) {
                            _uiState.update { 
                                it.copy(espCommunicationError = "Failed to send preset to ${device.name}: ${result.exceptionOrNull()?.message}")
                            }
                        } else {
                            println("Successfully applied preset to ${device.name}: ${sliderUpdates.size} sliders updated")
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(espCommunicationError = "Failed to send preset to ESP devices: ${e.message}")
            }
        }
    }

    /**
     * Connect to ESP device for real-time control (supports multiple devices)
     */
    fun connectToEspDevice(device: Device) {
        viewModelScope.launch {
            try {
                // Test connection with optimized method
                if (device.ipAddress != null) {
                    val result = espDeviceService.testConnection(device)
                    if (result.isSuccess) {
                        // Add device to connected devices list
                        _uiState.update { currentState ->
                            val updatedDevices = currentState.connectedDevices.toMutableList()
                            if (!updatedDevices.any { it.id == device.id }) {
                                updatedDevices.add(device)
                            }
                            
                            currentState.copy(
                                connectedDevices = updatedDevices,
                                isEspCommunicationEnabled = updatedDevices.isNotEmpty(),
                                espCommunicationError = null
                            )
                        }
                    } else {
                        _uiState.update { 
                            it.copy(espCommunicationError = "Failed to connect to ESP device ${device.name}: ${result.exceptionOrNull()?.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(espCommunicationError = "ESP connection error for ${device.name}: ${e.message}")
                }
            }
        }
    }

    /**
     * Disconnect from specific ESP device
     */
    fun disconnectFromEspDevice(device: Device) {
        _uiState.update { currentState ->
            val updatedDevices = currentState.connectedDevices.filter { it.id != device.id }
            currentState.copy(
                connectedDevices = updatedDevices,
                isEspCommunicationEnabled = updatedDevices.isNotEmpty(),
                espCommunicationError = null
            )
        }
    }

    /**
     * Disconnect from all ESP devices
     */
    fun disconnectFromAllEspDevices() {
        _uiState.update { 
            it.copy(
                connectedDevices = emptyList(),
                isEspCommunicationEnabled = false,
                espCommunicationError = null
            )
        }
    }

    /**
     * Enable or disable ESP communication for all connected devices
     */
    fun setEspCommunicationEnabled(enabled: Boolean) {
        _uiState.update { 
            it.copy(
                isEspCommunicationEnabled = enabled && it.connectedDevices.isNotEmpty(),
                espCommunicationError = if (!enabled) null else it.espCommunicationError
            )
        }
    }

    /**
     * Toggle power state for all connected ESP devices
     * Turn OFF: Set all PWM values to 0
     * Turn ON: Restore previous PWM values
     */
    fun toggleEspDevicesPower(turnOn: Boolean) {
        val connectedDevices = _uiState.value.connectedDevices
        if (connectedDevices.isEmpty() || !_uiState.value.isEspCommunicationEnabled) {
            return
        }

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(espCommunicationError = null) }
                
                connectedDevices.forEach { device ->
                    launch {
                        if (turnOn) {
                            // Send explicit POWER ON command first
                            val powerResult = espDeviceService.toggleDevicePower(device.copy(isOn = false))
                            if (powerResult.isFailure) {
                                println("Failed to send power ON command to ${device.name}: ${powerResult.exceptionOrNull()?.message}")
                            }
                            
                            // Then restore previous PWM values from persistent storage
                            println("DEBUG: Attempting to restore values for device: ${device.name} (ID: ${device.id})")
                            val previousValues = preferencesManager.getDevicePreviousValues(device.id)
                            println("DEBUG: Retrieved previous values: $previousValues")
                            
                            if (previousValues.isNotEmpty()) {
                                // Only restore values for channels that actually exist and have non-zero previous values
                                val sliderUpdates = mutableMapOf<Int, Int>()
                                
                                // Get current device info to know actual channel count
                                val deviceInfo = espDeviceService.getDeviceInfo(device)
                                if (deviceInfo.isSuccess) {
                                    val currentDevice = deviceInfo.getOrThrow()
                                    val actualChannels = currentDevice.sliderNames.size
                                    println("DEBUG: Device ${device.name} has $actualChannels actual channels")
                                    
                                    // Only restore values for existing channels
                                    previousValues.forEachIndexed { index, value ->
                                        if (index < actualChannels && value > 0) {
                                            sliderUpdates[index] = value
                                        }
                                    }
                                    
                                    if (sliderUpdates.isNotEmpty()) {
                                        espDeviceService.batchUpdateSliders(device, sliderUpdates)
                                        println("Successfully restored previous values for ${device.name}: $sliderUpdates")
                                    } else {
                                        // Previous values were all zeros, use reasonable defaults
                                        val defaultValues = (0 until actualChannels).associateWith { 128 }
                                        espDeviceService.batchUpdateSliders(device, defaultValues)
                                        println("Previous values were zeros, using defaults for ${device.name}: $defaultValues")
                                    }
                                }
                            } else {
                                // No previous values stored, use device's current values or reasonable defaults
                                println("DEBUG: No previous values found for ${device.name}")
                                val deviceInfo = espDeviceService.getDeviceInfo(device)
                                if (deviceInfo.isSuccess) {
                                    val currentDevice = deviceInfo.getOrThrow()
                                    val actualChannels = currentDevice.sliderNames.size
                                    
                                    // Use device's current non-zero values, or default to 128 for empty channels
                                    val defaultValues = (0 until actualChannels).associateWith { index ->
                                        if (index < currentDevice.sliderValues.size && currentDevice.sliderValues[index] > 0) {
                                            currentDevice.sliderValues[index]
                                        } else {
                                            128 // 50% brightness default
                                        }
                                    }
                                    
                                    espDeviceService.batchUpdateSliders(device, defaultValues)
                                    println("Using defaults for ${device.name} ($actualChannels channels): $defaultValues")
                                }
                            }
                        } else {
                            // Store current values in persistent storage first
                            println("DEBUG: Turning OFF device: ${device.name} (ID: ${device.id})")
                            val deviceInfo = espDeviceService.getDeviceInfo(device)
                            if (deviceInfo.isSuccess) {
                                val updatedDevice = deviceInfo.getOrThrow()
                                val actualChannels = updatedDevice.sliderNames.size
                                
                                println("DEBUG: Device ${device.name} current values: ${updatedDevice.sliderValues}")
                                println("DEBUG: Device reports ${updatedDevice.numSliders} sliders, actual channels: $actualChannels")
                                
                                // Only store values for channels that actually exist
                                val valuesToStore = if (actualChannels < updatedDevice.sliderValues.size) {
                                    updatedDevice.sliderValues.take(actualChannels)
                                } else {
                                    updatedDevice.sliderValues
                                }
                                
                                // Store current values in persistent storage for later restoration
                                preferencesManager.storeDevicePreviousValues(device.id, valuesToStore)
                                println("DEBUG: Stored previous values for ${device.name}: $valuesToStore")
                                
                                // Set only actual channels to 0
                                val zeroUpdates = (0 until actualChannels).associateWith { 0 }
                                espDeviceService.batchUpdateSliders(device, zeroUpdates)
                                println("DEBUG: Set channels to zero: $zeroUpdates")
                            }
                            
                            // Send explicit POWER OFF command
                            val powerResult = espDeviceService.toggleDevicePower(device.copy(isOn = true))
                            if (powerResult.isFailure) {
                                println("Failed to send power OFF command to ${device.name}: ${powerResult.exceptionOrNull()?.message}")
                            }
                        }
                    }
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(espCommunicationError = "Power toggle error: ${e.message}")
                }
            }
        }
    }

    /**
     * Initialize with room data - automatically download Excel if needed
     */
    fun initializeWithRoom(roomId: String) {
        currentRoomId = roomId
        viewModelScope.launch {
            try {
                val room = preferencesManager.getRoomData(roomId)
                
                if (room?.spectralData?.hasSpectralData() == true) {
                    // Load existing spectral data
                    loadSpectralData(room.spectralData!!)
                    
                    // Auto-connect to ESP devices in this room
                    autoConnectToEspDevices(roomId)
                } else {
                    // No spectral data - automatically download from ESP device
                    autoDownloadExcelFromRoom(roomId)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to load room spectral data: ${e.message}") }
            }
        }
    }

    /**
     * Automatically discover and connect to ESP devices in the room
     */
    private suspend fun autoConnectToEspDevices(roomId: String) {
        try {
            // Get devices in the room
            val devices = preferencesManager.getAllSavedDevices()
            println("DEBUG: SpectralViewModel autoConnectToEspDevices - Total devices: ${devices.size}")
            
            // Debug all devices first
            devices.forEach { device ->
                println("DEBUG: All devices - ID: ${device.id}, Name: ${device.name}, RoomId: ${device.roomId}, IP: ${device.ipAddress}, SliderNames: ${device.sliderNames.size}")
            }
            
            val roomDevices = devices.filter { 
                it.roomId == roomId && 
                it.ipAddress != null && 
                it.ipAddress!!.isNotEmpty()
            }
            
            println("DEBUG: SpectralViewModel autoConnectToEspDevices - Room $roomId devices with IP: ${roomDevices.size}")
            roomDevices.forEach { device ->
                println("DEBUG: Found room device: ${device.name}, IP: ${device.ipAddress}, Sliders: ${device.sliderNames.size}")
            }
            
            if (roomDevices.isNotEmpty()) {
                roomDevices.forEach { device ->
                    // Test connection to each device
                    println("DEBUG: Testing connection to ESP device: ${device.name} (${device.ipAddress})")
                    val testResult = espDeviceService.testConnection(device)
                    if (testResult.isSuccess) {
                        // Add to connected devices
                        _uiState.update { currentState ->
                            val updatedDevices = currentState.connectedDevices.toMutableList()
                            if (!updatedDevices.any { it.id == device.id }) {
                                updatedDevices.add(device)
                            }
                            val newState = currentState.copy(
                                connectedDevices = updatedDevices,
                                isEspCommunicationEnabled = updatedDevices.isNotEmpty()
                            )
                            println("DEBUG: ESP communication enabled: ${newState.isEspCommunicationEnabled}, Connected devices: ${updatedDevices.size}")
                            newState
                        }
                        println("DEBUG: Successfully auto-connected to ESP device: ${device.name} (${device.ipAddress})")
                    } else {
                        println("DEBUG: Failed to auto-connect to ESP device: ${device.name} - ${testResult.exceptionOrNull()?.message}")
                    }
                }
                
                // Show connection status if some devices failed
                val failedConnections = roomDevices.size - _uiState.value.connectedDevices.size
                if (failedConnections > 0) {
                    _uiState.update { 
                        it.copy(
                            espCommunicationError = "$failedConnections ESP device(s) couldn't connect. Check network connection."
                        )
                    }
                }
            } else {
                println("DEBUG: No ESP devices found for room $roomId - trying fallback connection")
                // FALLBACK: Try to connect to any available ESP device with an IP address
                val fallbackDevices = devices.filter { it.ipAddress != null && it.ipAddress!!.isNotEmpty() }
                println("DEBUG: Fallback - Found ${fallbackDevices.size} devices with IP addresses")
                
                fallbackDevices.forEach { device ->
                    println("DEBUG: Fallback testing: ${device.name} (${device.ipAddress})")
                    val testResult = espDeviceService.testConnection(device)
                    if (testResult.isSuccess) {
                        _uiState.update { currentState ->
                            val updatedDevices = currentState.connectedDevices.toMutableList()
                            if (!updatedDevices.any { it.id == device.id }) {
                                updatedDevices.add(device)
                            }
                            currentState.copy(
                                connectedDevices = updatedDevices,
                                isEspCommunicationEnabled = updatedDevices.isNotEmpty()
                            )
                        }
                        println("DEBUG: Fallback connected to ESP device: ${device.name}")
                    }
                }
            }
            
        } catch (e: Exception) {
            println("Error in auto ESP connection: ${e.message}")
            _uiState.update { 
                it.copy(
                    espCommunicationError = "ESP devices couldn't connect. Ensure devices are on the same network."
                )
            }
        }
    }

    /**
     * Automatically download Excel file from room devices
     */
    private suspend fun autoDownloadExcelFromRoom(roomId: String) {
        try {
            val room = preferencesManager.getRoomData(roomId)
            val roomDevices = room?.deviceIds?.mapNotNull { deviceId ->
                preferencesManager.getDeviceData(deviceId)
            } ?: emptyList()
            
            if (roomDevices.isEmpty()) {
                _uiState.update { it.copy(error = "No devices found in room to download spectral data from") }
                return
            }
            
            // Find first online device with IP address
            val onlineDevice = roomDevices.find { it.isOnline && !it.ipAddress.isNullOrBlank() }
            
            if (onlineDevice != null) {
                _uiState.update { it.copy(isLoading = true, error = null) }
                println("DEBUG: Auto-downloading Excel from device: ${onlineDevice.name}")
                println("DEBUG: Device IP address: ${onlineDevice.ipAddress}")
                
                // Check if IP address is mDNS name and try to find actual IP
                if (onlineDevice.ipAddress?.contains(".local") == true) {
                    println("DEBUG: Device has mDNS address, trying to find actual IP...")
                    // Try to find device with actual IP address
                    val deviceWithRealIP = roomDevices.find { 
                        it.isOnline && it.ipAddress != null && 
                        !it.ipAddress.contains(".local") && 
                        it.ipAddress.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))
                    }
                    
                    if (deviceWithRealIP != null) {
                        println("DEBUG: Found device with real IP: ${deviceWithRealIP.ipAddress}")
                        downloadExcelFromDevice(deviceWithRealIP)
                    } else {
                        downloadExcelFromDevice(onlineDevice)
                    }
                } else {
                    downloadExcelFromDevice(onlineDevice)
                }
            } else {
                // Show upload card as fallback
                _uiState.update { 
                    it.copy(
                        hasSpectralData = false,
                        error = "No online devices available. Please ensure at least one device in the room is online and connected."
                    )
                }
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isLoading = false,
                    error = "Failed to auto-download spectral data: ${e.message}"
                )
            }
        }
    }

    /**
     * Load spectral data from Excel file
     */
    fun loadDataFromStream(inputStream: InputStream, deviceModel: String?, fileName: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Parse the Excel file
                currentProfile = excelParser.parse(inputStream, deviceModel)
                
                // Initialize UI state
                val activeSources = currentProfile!!.getActiveSources()
                val initialSliderValues = activeSources.associate { it.name to it.initialPower }
                
                _uiState.update { state ->
                    state.copy(
                        activeLightSources = activeSources,
                        sliderValues = initialSliderValues,
                        hasSpectralData = true,
                        excelFileName = fileName,
                        isLoading = false,
                        // Keep existing presets and reset master slider config
                        masterSliderConfig = MasterSliderConfig()
                    )
                }
                
                // Calculate initial SPD graph
                recalculateSpdGraph()
                
                // Save spectral data to room if we have a room ID
                currentRoomId?.let { roomId ->
                    saveSpectralDataToRoom(roomId, fileName)
                }
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Failed to parse Excel file: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Update slider value and recalculate SPD
     */
    fun updateSliderValue(sourceName: String, newValue: Float) {
        val currentState = _uiState.value
        
        // Don't update if slider is frozen in master mode
        if (currentState.masterSliderConfig.isEnabled && 
            currentState.masterSliderConfig.frozenSliders.contains(sourceName)) {
            return
        }
        
        _uiState.update { state ->
            state.copy(
                sliderValues = state.sliderValues.toMutableMap().apply {
                    this[sourceName] = newValue
                }
            )
        }
        recalculateSpdGraph()
        
        // Send real-time change to ESP device if connected
        println("DEBUG: SpectralViewModel updateSliderValue - ESP communication enabled: ${currentState.isEspCommunicationEnabled}, Connected devices: ${currentState.connectedDevices.size}")
        if (currentState.isEspCommunicationEnabled) {
            println("DEBUG: Emitting slider change to ESP: $sourceName = $newValue")
            viewModelScope.launch {
                sliderChangeFlow.emit(SliderChange(sourceName, newValue))
            }
        } else {
            println("DEBUG: ESP communication disabled - slider change not sent to ESP")
        }
        
        // Save updated slider values to room
        currentRoomId?.let { roomId ->
            saveSliderValuesToRoom(roomId)
        }
    }
    
    /**
     * Update master slider value
     */
    fun updateMasterSliderValue(newValue: Float) {
        val currentState = _uiState.value
        if (!currentState.masterSliderConfig.isEnabled) return
        
        // Calculate new slider values based on master value and base values
        val updatedSliderValues = currentState.sliderValues.toMutableMap()
        val baseValues = currentState.masterSliderConfig.baseSliderValues
        
        // Only update non-frozen sliders
        for ((sourceName, baseValue) in baseValues) {
            if (!currentState.masterSliderConfig.frozenSliders.contains(sourceName)) {
                updatedSliderValues[sourceName] = (baseValue * newValue).coerceIn(0f, 1f)
            }
        }
        
        _uiState.update { state ->
            state.copy(
                sliderValues = updatedSliderValues,
                masterSliderConfig = state.masterSliderConfig.copy(masterValue = newValue)
            )
        }
        
        recalculateSpdGraph()
        
        // Send real-time changes to ESP device if connected - BATCH UPDATE
        if (currentState.isEspCommunicationEnabled) {
            viewModelScope.launch {
                // Send batch update for all changed sliders to avoid debounce issues
                currentState.connectedDevices.forEach { device ->
                    launch {
                        val sliderUpdates = mutableMapOf<Int, Int>()
                        
                        // Collect all slider updates for this device
                        for ((sourceName, value) in updatedSliderValues) {
                            if (!currentState.masterSliderConfig.frozenSliders.contains(sourceName)) {
                                val sliderIndex = findSliderIndexForSource(sourceName, device)
                                if (sliderIndex >= 0) {
                                    val pwmValue = (value * ESP_MAX_PWM_VALUE).toInt().coerceIn(0, ESP_MAX_PWM_VALUE)
                                    sliderUpdates[sliderIndex] = pwmValue
                                }
                            }
                        }
                        
                        // Send batch update for all sliders at once
                        if (sliderUpdates.isNotEmpty()) {
                            val result = espDeviceService.batchUpdateSliders(device, sliderUpdates)
                            if (result.isFailure) {
                                _uiState.update { 
                                    it.copy(espCommunicationError = "Failed to send master slider data to ${device.name}: ${result.exceptionOrNull()?.message}")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Save updated values to room
        currentRoomId?.let { roomId ->
            saveSliderValuesToRoom(roomId)
        }
    }
    
    /**
     * Toggle master slider mode
     */
    fun toggleMasterSliderMode() {
        val currentState = _uiState.value
        
        if (currentState.masterSliderConfig.isEnabled) {
            // Disable master mode
            _uiState.update { state ->
                state.copy(
                    masterSliderConfig = MasterSliderConfig(isEnabled = false)
                )
            }
        } else {
            // Enable master mode - save current values as base
            _uiState.update { state ->
                state.copy(
                    masterSliderConfig = MasterSliderConfig(
                        isEnabled = true,
                        masterValue = 1.0f,
                        baseSliderValues = state.sliderValues
                    )
                )
            }
        }
        
        currentRoomId?.let { roomId ->
            saveSliderValuesToRoom(roomId)
        }
    }
    
    /**
     * Toggle freeze state for a specific slider in master mode
     */
    fun toggleSliderFreeze(sourceName: String) {
        val currentState = _uiState.value
        if (!currentState.masterSliderConfig.isEnabled) return
        
        val frozenSliders = currentState.masterSliderConfig.frozenSliders.toMutableSet()
        if (frozenSliders.contains(sourceName)) {
            frozenSliders.remove(sourceName)
        } else {
            frozenSliders.add(sourceName)
        }
        
        _uiState.update { state ->
            state.copy(
                masterSliderConfig = state.masterSliderConfig.copy(
                    frozenSliders = frozenSliders
                )
            )
        }
        
        currentRoomId?.let { roomId ->
            saveSliderValuesToRoom(roomId)
        }
    }
    
    /**
     * Save current slider configuration as a preset
     */
    fun saveCurrentAsPreset(presetName: String) {
        if (presetName.isBlank()) return
        
        val currentState = _uiState.value
        val newPreset = SpectrumPreset(
            id = "preset_${System.currentTimeMillis()}",
            name = presetName.trim(),
            sliderValues = currentState.sliderValues
        )
        
        _uiState.update { state ->
            state.copy(
                spectrumPresets = state.spectrumPresets + newPreset
            )
        }
        
        currentRoomId?.let { roomId ->
            saveSliderValuesToRoom(roomId)
        }
    }
    
    /**
     * Load a spectrum preset
     */
    fun loadPreset(presetId: String) {
        val currentState = _uiState.value
        val preset = currentState.spectrumPresets.find { it.id == presetId } ?: return
        
        _uiState.update { state ->
            state.copy(
                sliderValues = preset.sliderValues,
                // Disable master mode when loading preset
                masterSliderConfig = MasterSliderConfig(isEnabled = false)
            )
        }
        
        recalculateSpdGraph()
        
        // Send preset configuration to connected ESP devices
        if (currentState.isEspCommunicationEnabled && currentState.connectedDevices.isNotEmpty()) {
            viewModelScope.launch {
                sendPresetToEspDevices(preset.sliderValues)
            }
        }
        
        currentRoomId?.let { roomId ->
            saveSliderValuesToRoom(roomId)
        }
    }
    
    /**
     * Delete a spectrum preset
     */
    fun deletePreset(presetId: String) {
        _uiState.update { state ->
            state.copy(
                spectrumPresets = state.spectrumPresets.filter { it.id != presetId }
            )
        }
        
        currentRoomId?.let { roomId ->
            saveSliderValuesToRoom(roomId)
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Recalculate SPD graph based on current slider values
     */
    private fun recalculateSpdGraph() {
        val profile = currentProfile ?: return
        val currentSliderValues = _uiState.value.sliderValues
        val allSources = profile.sources.associateBy { it.name }

        // Step 1: Calculate the "Resultant" curve
        val resultantCurve = profile.spectrum.map { point ->
            val resultantIntensity = currentSliderValues.entries.sumOf { (name, sliderValue) ->
                val source = allSources[name]
                val baseIntensity = point.baseIntensities[name] ?: 0f
                // Core formula: baseIntensity * sliderValue * intensityFactor
                (baseIntensity * sliderValue * (source?.intensityFactor ?: 0f)).toDouble()
            }
            point.wavelength to resultantIntensity.toFloat()
        }

        // Step 2: Normalize to get the "Final" curve
        val maxResultant = resultantCurve.maxOfOrNull { it.second } ?: 1f
        
        val finalGraphPoints = resultantCurve.map { (wavelength, resultantIntensity) ->
            val finalIntensity = if (maxResultant > 0) resultantIntensity / maxResultant else 0f
            GraphPoint(wavelength, finalIntensity)
        }

        _uiState.update { it.copy(graphData = finalGraphPoints) }
    }

    /**
     * Load existing spectral data
     */
    private fun loadSpectralData(spectralData: RoomSpectralData) {
        currentProfile = spectralData.spectralProfile
        
        if (currentProfile != null) {
            val activeSources = currentProfile!!.getActiveSources()
            val sliderValues = activeSources.associate { source ->
                source.name to spectralData.getSliderValue(source.name)
            }
            
            _uiState.update { state ->
                state.copy(
                    activeLightSources = activeSources,
                    sliderValues = sliderValues,
                    hasSpectralData = true,
                    excelFileName = spectralData.excelFileName,
                    spectrumPresets = spectralData.spectrumPresets,
                    masterSliderConfig = spectralData.masterSliderConfig
                )
            }
            
            recalculateSpdGraph()
        }
    }

    /**
     * Save spectral data to room
     */
    private fun saveSpectralDataToRoom(roomId: String, fileName: String?) {
        viewModelScope.launch {
            try {
                val room = preferencesManager.getRoomData(roomId) ?: return@launch
                val currentState = _uiState.value
                
                val spectralData = RoomSpectralData(
                    spectralProfile = currentProfile,
                    currentSliderValues = currentState.sliderValues,
                    excelFileName = fileName,
                    spectrumPresets = currentState.spectrumPresets,
                    masterSliderConfig = currentState.masterSliderConfig
                )
                
                val updatedRoom = room.withSpectralData(spectralData)
                preferencesManager.saveRoomData(roomId, updatedRoom)
                
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to save spectral data: ${e.message}") }
            }
        }
    }

    /**
     * Save slider values to room
     */
    private fun saveSliderValuesToRoom(roomId: String) {
        viewModelScope.launch {
            try {
                val room = preferencesManager.getRoomData(roomId) ?: return@launch
                val currentState = _uiState.value
                
                val updatedSpectralData = room.spectralData?.copy(
                    currentSliderValues = currentState.sliderValues,
                    lastCalculated = System.currentTimeMillis(),
                    spectrumPresets = currentState.spectrumPresets,
                    masterSliderConfig = currentState.masterSliderConfig
                )
                
                if (updatedSpectralData != null) {
                    val updatedRoom = room.withSpectralData(updatedSpectralData)
                    preferencesManager.saveRoomData(roomId, updatedRoom)
                }
                
            } catch (e: Exception) {
                // Silent fail for slider value saves to avoid interrupting UX
            }
        }
    }

    /**
     * Reset sliders to initial values
     */
    fun resetSlidersToInitial() {
        currentProfile?.let { profile ->
            val activeSources = profile.getActiveSources()
            val initialSliderValues = activeSources.associate { it.name to it.initialPower }
            
            _uiState.update { it.copy(sliderValues = initialSliderValues) }
            recalculateSpdGraph()
            
            // Send reset values to connected ESP devices
            val currentState = _uiState.value
            if (currentState.isEspCommunicationEnabled && currentState.connectedDevices.isNotEmpty()) {
                viewModelScope.launch {
                    sendPresetToEspDevices(initialSliderValues)
                }
            }
            
            currentRoomId?.let { roomId ->
                saveSliderValuesToRoom(roomId)
            }
        }
    }

    /**
     * Download Excel file from an ESP device in the room
     */
    fun downloadExcelFromDevice(device: Device) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                val result = espDeviceService.downloadExcelFile(device)
                
                if (result.isSuccess) {
                    val inputStream = result.getOrThrow()
                    val fileName = "data_${device.name}_${System.currentTimeMillis()}.xlsx"
                    
                    // Parse the downloaded Excel file
                    loadDataFromStream(inputStream, device.deviceModel, fileName)
                } else {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Failed to download Excel file from ${device.name}: ${result.exceptionOrNull()?.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false,
                        error = "Error downloading Excel file: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Download Excel file from the first online device in the room
     */
    fun downloadExcelFromRoomDevice() {
        currentRoomId?.let { roomId ->
            viewModelScope.launch {
                try {
                    val room = preferencesManager.getRoomData(roomId)
                    val roomDevices = room?.deviceIds?.mapNotNull { deviceId ->
                        preferencesManager.getDeviceData(deviceId)
                    } ?: emptyList()
                    
                    val onlineDevice = roomDevices.find { it.isOnline && !it.ipAddress.isNullOrBlank() }
                    
                    if (onlineDevice != null) {
                        downloadExcelFromDevice(onlineDevice)
                    } else {
                        _uiState.update { 
                            it.copy(error = "No online devices found in room to download Excel file from")
                        }
                    }
                } catch (e: Exception) {
                    _uiState.update { 
                        it.copy(error = "Failed to find devices in room: ${e.message}")
                    }
                }
            }
        }
    }
} 
