package com.fluortronix.fluortronixapp.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluortronix.fluortronixapp.data.PreferencesManager
import com.fluortronix.fluortronixapp.data.models.Device
import com.fluortronix.fluortronixapp.data.models.Room
import com.fluortronix.fluortronixapp.data.models.RoomCreationData
import com.fluortronix.fluortronixapp.data.models.RoomStats
import com.fluortronix.fluortronixapp.data.datasource.ESPDeviceService
import com.fluortronix.fluortronixapp.data.repository.RoutineRepository
import com.fluortronix.fluortronixapp.data.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.UUID
import javax.inject.Inject

data class RoomsUiState(
    val rooms: List<Room> = emptyList(),
    val devices: List<Device> = emptyList(),
    val unassignedDevices: List<Device> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedRoom: Room? = null,
    val roomStats: Map<String, RoomStats> = emptyMap(),
    val isCreatingRoom: Boolean = false,
    val isEditingRoom: Boolean = false
)

@HiltViewModel
class RoomsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val espDeviceService: ESPDeviceService,
    private val routineRepository: RoutineRepository,
    private val roomRepository: RoomRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(RoomsUiState())
    val uiState: StateFlow<RoomsUiState> = _uiState.asStateFlow()
    
    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    
    private var deviceMonitoringJobs = mutableMapOf<String, Job>()
    
    init {
        loadData()
        observeDataChanges()
    }
    
    /**
     * Observes changes in rooms and devices to update UI state
     */
    private fun observeDataChanges() {
        viewModelScope.launch {
            combine(_rooms, _devices) { rooms, devices ->
                val unassignedDevices = devices.filter { it.roomId == null }
                val roomStats = calculateRoomStats(rooms, devices)
                
                println("DEBUG: UI State Update - Total devices: ${devices.size}, Unassigned: ${unassignedDevices.size}, Rooms: ${rooms.size}")
                unassignedDevices.forEach { device ->
                    println("DEBUG: Unassigned device: ${device.name}, Model: ${device.deviceModel}")
                }
                
                _uiState.value = _uiState.value.copy(
                    rooms = rooms,
                    devices = devices,
                    unassignedDevices = unassignedDevices,
                    roomStats = roomStats,
                    isLoading = false
                )
            }.collect {}
        }
    }
    
    /**
     * Loads all rooms and devices from storage and starts real-time monitoring
     */
    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            
            try {
                // First, validate and fix any data inconsistencies
                preferencesManager.validateAndFixRoomData()
                preferencesManager.fixOrphanedDevices()
                
                // Load the validated data
                val rooms = preferencesManager.getAllSavedRooms()
                val savedDevices = preferencesManager.getAllSavedDevices()
                
                println("DEBUG: RoomsViewModel loaded ${rooms.size} rooms and ${savedDevices.size} devices")
                rooms.forEach { room ->
                    println("DEBUG: Room: ${room.name}, DeviceCount: ${room.deviceCount}, DeviceIds: ${room.deviceIds}")
                }
                savedDevices.forEach { device ->
                    println("DEBUG: Device: ${device.name}, RoomId: ${device.roomId ?: "None"}, RoomName: ${device.roomName ?: "Unassigned"}")
                }
                
                // Stop existing monitoring
                stopDeviceMonitoring()
                
                // Set initial device state (offline until proven online)
                val initialDevices = savedDevices.map { it.copy(isOnline = false) }
                
                _rooms.value = rooms
                _devices.value = initialDevices
                
                // Manually trigger UI update to ensure loading state is cleared
                val unassignedDevices = initialDevices.filter { it.roomId == null }
                val roomStats = calculateRoomStats(rooms, initialDevices)
                
                _uiState.value = _uiState.value.copy(
                    rooms = rooms,
                    devices = initialDevices,
                    unassignedDevices = unassignedDevices,
                    roomStats = roomStats,
                    isLoading = false
                )
                
                // Start monitoring for devices with IP addresses
                startDeviceMonitoring(savedDevices)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load data: ${e.message}",
                    isLoading = false
                )
            }
        }
    }
    
    /**
     * Starts real-time monitoring for all devices
     */
    private fun startDeviceMonitoring(devices: List<Device>) {
        devices.forEach { device ->
            if (device.ipAddress != null) {
                val monitoringJob = viewModelScope.launch {
                    try {
                        espDeviceService.monitorDeviceStatus(device).collect { updatedDevice ->
                            updateDeviceInList(updatedDevice)
                        }
                    } catch (e: Exception) {
                        println("DEBUG: RoomsViewModel monitoring failed for device ${device.name}: ${e.message}")
                        // If monitoring fails, ensure device remains in list but marked as offline
                        updateDeviceInList(device.copy(isOnline = false))
                    }
                }
                deviceMonitoringJobs[device.id] = monitoringJob
            } else {
                println("DEBUG: RoomsViewModel device ${device.name} has no IP address, skipping monitoring")
            }
        }
        
        println("DEBUG: RoomsViewModel started monitoring for ${deviceMonitoringJobs.size} devices out of ${devices.size} total devices")
    }
    
    /**
     * Stops all device monitoring
     */
    private fun stopDeviceMonitoring() {
        deviceMonitoringJobs.values.forEach { job ->
            job.cancel()
        }
        deviceMonitoringJobs.clear()
    }
    
    /**
     * Updates a specific device in the devices list while preserving room assignment
     */
    private fun updateDeviceInList(updatedDevice: Device) {
        val currentDevices = _devices.value.toMutableList()
        val index = currentDevices.indexOfFirst { it.id == updatedDevice.id }
        if (index != -1) {
            val existingDevice = currentDevices[index]
            
            // Get the latest room assignment data from storage to avoid overwriting recent assignments
            viewModelScope.launch {
                try {
                    val latestDeviceData = preferencesManager.getDeviceData(updatedDevice.id)
                    
                    // Use the latest room assignment from storage, or fall back to existing device info
                    val roomId = latestDeviceData?.roomId ?: existingDevice.roomId
                    val roomName = latestDeviceData?.roomName ?: existingDevice.roomName
                    
                    // Preserve room assignment information when updating device status
                    val deviceWithRoomInfo = updatedDevice.copy(
                        roomId = roomId,
                        roomName = roomName
                    )
                    
                    // Update UI
                    currentDevices[index] = deviceWithRoomInfo
                    _devices.value = currentDevices
                    
                    // Recalculate room stats with updated devices
                    val roomStats = calculateRoomStats(_rooms.value, currentDevices)
                    val unassignedDevices = currentDevices.filter { it.roomId == null }
                    
                    _uiState.value = _uiState.value.copy(
                        devices = currentDevices,
                        unassignedDevices = unassignedDevices,
                        roomStats = roomStats
                    )
                    
                    println("DEBUG: RoomsViewModel updated device ${deviceWithRoomInfo.name}, Room: ${deviceWithRoomInfo.roomName ?: "Unassigned"}, Online: ${deviceWithRoomInfo.isOnline}")
                    
                    // Save updated device state to preferences (but don't overwrite room assignments)
                    try {
                        preferencesManager.saveDeviceData(deviceWithRoomInfo.id, deviceWithRoomInfo)
                    } catch (e: Exception) {
                        // Ignore save errors to prevent disrupting monitoring
                        println("DEBUG: Failed to save device ${deviceWithRoomInfo.name}: ${e.message}")
                    }
                } catch (e: Exception) {
                    // Fallback: use existing device room info
                    val deviceWithRoomInfo = updatedDevice.copy(
                        roomId = existingDevice.roomId,
                        roomName = existingDevice.roomName
                    )
                    
                    currentDevices[index] = deviceWithRoomInfo
                    _devices.value = currentDevices
                    
                    // Recalculate room stats
                    val roomStats = calculateRoomStats(_rooms.value, currentDevices)
                    val unassignedDevices = currentDevices.filter { it.roomId == null }
                    
                    _uiState.value = _uiState.value.copy(
                        devices = currentDevices,
                        unassignedDevices = unassignedDevices,
                        roomStats = roomStats
                    )
                    
                    println("DEBUG: RoomsViewModel updated device ${deviceWithRoomInfo.name} (fallback), Room: ${deviceWithRoomInfo.roomName ?: "Unassigned"}, Online: ${deviceWithRoomInfo.isOnline}")
                }
            }
        }
    }
    
    /**
     * Creates a new room
     */
    fun createRoom(roomData: RoomCreationData): Boolean {
        if (roomData.name.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Room name cannot be empty")
            return false
        }
        
        // Check for duplicate room names
        if (_rooms.value.any { it.name.equals(roomData.name, ignoreCase = true) }) {
            _uiState.value = _uiState.value.copy(error = "A room with this name already exists")
            return false
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isCreatingRoom = true, error = null)
            
            try {
                val roomId = UUID.randomUUID().toString()
                val room = Room(
                    id = roomId,
                    name = roomData.name.trim(),
                    deviceIds = roomData.deviceIds,
                    deviceCount = roomData.deviceIds.size,
                    allowedDeviceModel = roomData.allowedDeviceModel
                )
                
                // Save room to storage
                preferencesManager.saveRoomData(roomId, room)
                
                // Assign devices to room if any
                roomData.deviceIds.forEach { deviceId ->
                    preferencesManager.assignDeviceToRoom(deviceId, roomId)
                }
                
                // Reload data to refresh UI
                loadData()
                
                _uiState.value = _uiState.value.copy(isCreatingRoom = false)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to create room: ${e.message}",
                    isCreatingRoom = false
                )
            }
        }
        
        return true
    }
    
    /**
     * Updates an existing room
     */
    fun updateRoom(roomId: String, newName: String): Boolean {
        if (newName.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Room name cannot be empty")
            return false
        }
        
        // Check for duplicate room names (excluding current room)
        if (_rooms.value.any { it.id != roomId && it.name.equals(newName, ignoreCase = true) }) {
            _uiState.value = _uiState.value.copy(error = "A room with this name already exists")
            return false
        }
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isEditingRoom = true, error = null)
            
            try {
                val currentRoom = _rooms.value.find { it.id == roomId }
                if (currentRoom != null) {
                    val updatedRoom = currentRoom.copy(
                        name = newName.trim(),
                        lastModified = System.currentTimeMillis()
                    )
                    
                    preferencesManager.saveRoomData(roomId, updatedRoom)
                    
                    // Update room name in all assigned devices
                    currentRoom.deviceIds.forEach { deviceId ->
                        val device = _devices.value.find { it.id == deviceId }
                        if (device != null) {
                            val updatedDevice = device.copy(roomName = newName.trim())
                            preferencesManager.saveDeviceData(deviceId, updatedDevice)
                        }
                    }
                    
                    loadData()
                }
                
                _uiState.value = _uiState.value.copy(isEditingRoom = false)
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update room: ${e.message}",
                    isEditingRoom = false
                )
            }
        }
        
        return true
    }
    
    /**
     * Deletes a room and unassigns all its devices
     */
    fun deleteRoom(roomId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(error = null)
            
            try {
                val room = _rooms.value.find { it.id == roomId }
                if (room != null) {
                    // Unassign all devices from the room
                    room.deviceIds.forEach { deviceId ->
                        preferencesManager.removeDeviceFromRoom(deviceId)
                    }
                    
                    // Delete routines for this room
                    routineRepository.deleteRoutinesForRoom(roomId)
                    
                    // Delete the room using repository (this should trigger home screen refresh)
                    roomRepository.removeRoom(roomId)
                    
                    // Clear selection if this room was selected
                    if (_uiState.value.selectedRoom?.id == roomId) {
                        _uiState.value = _uiState.value.copy(selectedRoom = null)
                    }
                    
                    loadData()
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to delete room: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Assigns a device to a room with validation
     */
    fun assignDeviceToRoom(deviceId: String, roomId: String): Boolean {
        val device = _devices.value.find { it.id == deviceId }
        
        if (device == null) {
            _uiState.value = _uiState.value.copy(error = "Device not found")
            return false
        }
        
        viewModelScope.launch {
            try {
                println("DEBUG: RoomsViewModel attempting to assign device $deviceId to room $roomId")
                
                // Stop device monitoring temporarily to prevent race conditions
                stopDeviceMonitoring()
                
                // Get FRESH room data from storage to avoid stale cache issues
                val room = preferencesManager.getRoomData(roomId)
                if (room == null) {
                    _uiState.value = _uiState.value.copy(error = "Room not found")
                    loadData()
                    return@launch
                }
                
                println("DEBUG: Fresh room data - allowedDeviceModel: ${room.allowedDeviceModel}, hasSpectralData: ${room.hasSpectralData()}")
                
                // Validate device model compatibility with FRESH room data
                if (!room.canAddDeviceModel(device.deviceModel)) {
                    _uiState.value = _uiState.value.copy(
                        error = "Device model '${device.deviceModel}' is not compatible with room '${room.name}'. " +
                                "Room already contains devices of model '${room.allowedDeviceModel}'"
                    )
                    loadData()
                    return@launch
                }
                
                // Validate spectral data compatibility if room has spectral data
                if (room.hasSpectralData() && !room.isDeviceModelCompatibleWithSpectral(device.deviceModel)) {
                    _uiState.value = _uiState.value.copy(
                        error = "Device model '${device.deviceModel}' is not compatible with the spectral data in room '${room.name}'. " +
                                "The room's spectral profile was configured for model '${room.spectralData?.spectralProfile?.deviceModel}'"
                    )
                    loadData()
                    return@launch
                }
                
                val success = preferencesManager.assignDeviceToRoom(deviceId, roomId)
                if (success) {
                    println("DEBUG: Device assignment successful, reloading data...")
                    
                    // Add a small delay to ensure data is properly saved
                    kotlinx.coroutines.delay(100)
                    
                    loadData()
                } else {
                    println("DEBUG: Device assignment failed")
                    _uiState.value = _uiState.value.copy(error = "Failed to assign device to room")
                    
                    // Restart monitoring even if assignment failed
                    loadData()
                }
            } catch (e: Exception) {
                println("DEBUG: Exception during device assignment: ${e.message}")
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    error = "Failed to assign device: ${e.message}"
                )
                
                // Restart monitoring on error
                loadData()
            }
        }
        
        return true
    }
    
    /**
     * Removes a device from its current room
     */
    fun removeDeviceFromRoom(deviceId: String) {
        viewModelScope.launch {
            try {
                val success = preferencesManager.removeDeviceFromRoom(deviceId)
                if (success) {
                    loadData()
                } else {
                    _uiState.value = _uiState.value.copy(error = "Failed to remove device from room")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to remove device: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Toggles power state for all devices in a room with ESP communication
     */
    fun toggleRoomPower(roomId: String) {
        val room = _rooms.value.find { it.id == roomId } ?: return
        val newPowerState = !room.isAllDevicesOn
        
        viewModelScope.launch {
            try {
                // Get ESP devices in the room for real-time control
                val roomDevices = _devices.value.filter { it.roomId == roomId }
                val espDevices = roomDevices.filter { it.ipAddress != null && it.sliderNames.isNotEmpty() }
                
                // Update local database state first
                val success = preferencesManager.setRoomPowerState(roomId, newPowerState)
                if (success) {
                    // Communicate with ESP devices for real-time control
                    if (espDevices.isNotEmpty()) {
                        espDevices.forEach { device ->
                            launch {
                                try {
                                    if (newPowerState) {
                                        // Turn on: Restore previous PWM values or set to default
                                        restoreDevicePreviousValues(device)
                                    } else {
                                        // Turn off: Store current values and set all to 0
                                        storeCurrentValuesAndTurnOff(device)
                                    }
                                } catch (e: Exception) {
                                    // Log ESP communication error but don't fail the entire operation
                                    println("ESP communication failed for device ${device.name}: ${e.message}")
                                }
                            }
                        }
                    }
                    
                    // Sync the power state to ensure consistency
                    preferencesManager.syncRoomPowerState(roomId)
                    loadData()
                } else {
                    _uiState.value = _uiState.value.copy(error = "Failed to toggle room power")
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to toggle room power: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Restore current app slider values to device when turning on
     */
    private suspend fun restoreDevicePreviousValues(device: Device) {
        try {
            println("DEBUG: Restoring current app slider values for device ${device.name}")
            
            // Send explicit POWER ON command first
            val powerResult = espDeviceService.toggleDevicePower(device.copy(isOn = false))
            if (powerResult.isFailure) {
                println("Failed to send power ON command to ${device.name}: ${powerResult.exceptionOrNull()?.message}")
            }
            
            // Get current slider values from room's spectral data (what the app currently shows)
            val currentAppValues = getCurrentAppSliderValues(device)
            if (currentAppValues.isNotEmpty()) {
                println("DEBUG: Sending current app slider values: $currentAppValues")
                espDeviceService.batchUpdateSliders(device, currentAppValues)
            } else {
                println("DEBUG: No current app values available, device will remain at current state")
            }
            
        } catch (e: Exception) {
            println("Failed to restore values for device ${device.name}: ${e.message}")
        }
    }
    
    /**
     * Get current slider values from room's spectral data (what the app is currently displaying)
     */
    private suspend fun getCurrentAppSliderValues(device: Device): Map<Int, Int> {
        try {
            val roomId = device.roomId ?: return emptyMap()
            val room = preferencesManager.getRoomData(roomId) ?: return emptyMap()
            
            // Get current slider values from room's spectral data
            val currentSliderValues = room.spectralData?.currentSliderValues
            val spectralProfile = room.spectralData?.spectralProfile
            
            if (currentSliderValues != null && spectralProfile != null) {
                println("DEBUG: Using current app slider values from room spectral data")
                
                return spectralProfile.getActiveSources().mapIndexed { index, source ->
                    val sliderValue = currentSliderValues[source.name]
                    val pwmValue = if (sliderValue != null) {
                        // currentSliderValues are in 0.0-1.0 range from UI sliders
                        (sliderValue * 255).toInt().coerceIn(0, 255)
                    } else {
                        // source.initialPower is in 0-100 percentage range from Excel
                        (source.initialPower * 255 / 100).toInt().coerceIn(0, 255)
                    }
                    println("DEBUG:   Slider $index (${source.name}): ${sliderValue?.let { "${(it * 100).toInt()}%" } ?: "${source.initialPower.toInt()}%"} -> $pwmValue/255 PWM")
                    index to pwmValue
                }.toMap()
            } else {
                println("DEBUG: No current app values, using spectral defaults")
                // Fall back to initial power values from spectral profile (0-100 range)
                return spectralProfile?.getActiveSources()?.mapIndexed { index, source ->
                    val pwmValue = (source.initialPower * 255 / 100).toInt().coerceIn(0, 255)
                    index to pwmValue
                }?.toMap() ?: emptyMap()
            }
            
        } catch (e: Exception) {
            println("DEBUG: Failed to get current app slider values: ${e.message}")
            return emptyMap()
        }
    }
    
    /**
     * Store current PWM values and turn off device
     */
    private suspend fun storeCurrentValuesAndTurnOff(device: Device) {
        try {
            // Get current device state first
            val deviceInfo = espDeviceService.getDeviceInfo(device)
            if (deviceInfo.isSuccess) {
                val currentDevice = deviceInfo.getOrThrow()
                
                // Store current values for later restoration
                preferencesManager.storeDevicePreviousValues(device.id, currentDevice.sliderValues)
                
                // Set all sliders to 0
                val zeroUpdates = (0 until currentDevice.numSliders).associateWith { 0 }
                espDeviceService.batchUpdateSliders(device, zeroUpdates)
            }
            
            // Send explicit POWER OFF command
            val powerResult = espDeviceService.toggleDevicePower(device.copy(isOn = true))
            if (powerResult.isFailure) {
                println("Failed to send power OFF command to ${device.name}: ${powerResult.exceptionOrNull()?.message}")
            }
            
        } catch (e: Exception) {
            println("Failed to turn off device ${device.name}: ${e.message}")
        }
    }
    
    /**
     * Selects a room for detailed view
     */
    fun selectRoom(room: Room?) {
        _uiState.value = _uiState.value.copy(selectedRoom = room)
    }
    
    /**
     * Gets devices assigned to a specific room
     */
    fun getDevicesInRoom(roomId: String): List<Device> {
        return _devices.value.filter { it.roomId == roomId }
    }
    
    /**
     * Gets available device models for room assignment
     */
    fun getAvailableDeviceModels(): List<String> {
        return _devices.value
            .mapNotNull { it.deviceModel }
            .distinct()
            .sorted()
    }
    
    /**
     * Clears current error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
    
    /**
     * Calculates statistics for each room
     */
    private fun calculateRoomStats(rooms: List<Room>, devices: List<Device>): Map<String, RoomStats> {
        return rooms.associate { room ->
            val roomDevices = devices.filter { it.roomId == room.id }
            
            val stats = RoomStats(
                totalDevices = roomDevices.size,
                onlineDevices = roomDevices.count { it.isOnline },
                offlineDevices = roomDevices.count { !it.isOnline },
                devicesOn = roomDevices.count { it.isOn },
                devicesOff = roomDevices.count { !it.isOn },
                deviceModel = room.allowedDeviceModel
            )
            
            room.id to stats
        }
    }
    
    /**
     * Gets devices that can be assigned to a specific room (compatible model)
     */
    fun getCompatibleDevicesForRoom(roomId: String): List<Device> {
        // For now, use cached room data but with enhanced empty room logic
        // The assignment validation will use fresh data when actually assigning
        val room = _rooms.value.find { it.id == roomId } ?: return emptyList()
        
        return _devices.value.filter { device ->
            // Device must be unassigned or already in this room
            (device.roomId == null || device.roomId == roomId) &&
            // For empty rooms, always allow any device model
            (room.deviceIds.isEmpty() || (
                // Device model must be compatible with room's allowed model
                (room.allowedDeviceModel == null || room.allowedDeviceModel == device.deviceModel) &&
                // Device model must be compatible with room's spectral data (if any)
                room.isDeviceModelCompatibleWithSpectral(device.deviceModel)
            ))
        }
    }
    
    /**
     * Validates if a room name is available
     */
    fun isRoomNameAvailable(name: String, excludeRoomId: String? = null): Boolean {
        return !_rooms.value.any { 
            it.name.equals(name.trim(), ignoreCase = true) && it.id != excludeRoomId 
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopDeviceMonitoring()
    }
} 