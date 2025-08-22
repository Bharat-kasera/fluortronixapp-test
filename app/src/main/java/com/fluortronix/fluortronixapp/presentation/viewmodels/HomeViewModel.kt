package com.fluortronix.fluortronixapp.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluortronix.fluortronixapp.data.datasource.ESPDeviceService
import com.fluortronix.fluortronixapp.data.models.Device
import com.fluortronix.fluortronixapp.data.models.Room
import com.fluortronix.fluortronixapp.data.repository.DeviceRepository
import com.fluortronix.fluortronixapp.data.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val deviceRepository: DeviceRepository,
    private val roomRepository: RoomRepository,
    private val espDeviceService: ESPDeviceService
) : ViewModel() {
    
    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()
    
    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private var deviceMonitoringJobs = mutableMapOf<String, Job>()
    
    init {
        loadData()
    }
    
    /**
     * Refresh data from repositories
     */
    fun refreshData() {
        loadData()
    }
    
    private fun loadData() {
        // Load rooms
        viewModelScope.launch {
            try {
                roomRepository.getRooms().collect { roomList ->
                    _rooms.value = roomList
                }
            } catch (e: Exception) {
                _rooms.value = emptyList()
            }
        }
        
        // Load devices and start monitoring
        viewModelScope.launch {
            _isLoading.value = true
            
            try {
                // Load initial devices from repository
                deviceRepository.getDevices().collect { savedDevices ->
                    println("DEBUG: HomeViewModel loaded ${savedDevices.size} devices from repository")
                    savedDevices.forEach { device ->
                        println("DEBUG: Device: ${device.name}, Room: ${device.roomName ?: "Unassigned"}, IP: ${device.ipAddress ?: "No IP"}")
                    }
                    
                    // Cancel existing monitoring jobs
                    stopDeviceMonitoring()
                    
                    // Set initial device state (offline until proven online)
                    val initialDevices = savedDevices.map { it.copy(isOnline = false) }
                    _devices.value = initialDevices
                    _isLoading.value = false
                    
                    println("DEBUG: HomeViewModel set ${initialDevices.size} devices in UI state")
                    
                    // Start monitoring for each device
                    startDeviceMonitoring(savedDevices)
                }
            } catch (e: Exception) {
                println("DEBUG: HomeViewModel failed to load devices: ${e.message}")
                e.printStackTrace()
                _devices.value = emptyList()
                _isLoading.value = false
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
                        println("DEBUG: Monitoring failed for device ${device.name}: ${e.message}")
                        // If monitoring fails, ensure device remains in list but marked as offline
                        updateDeviceInList(device.copy(isOnline = false))
                    }
                }
                deviceMonitoringJobs[device.id] = monitoringJob
            } else {
                println("DEBUG: Device ${device.name} has no IP address, skipping monitoring")
            }
        }
        
        println("DEBUG: Started monitoring for ${deviceMonitoringJobs.size} devices out of ${devices.size} total devices")
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
                    val latestDeviceData = deviceRepository.getDevice(updatedDevice.id)
                    
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
                    
                    println("DEBUG: HomeViewModel updated device ${deviceWithRoomInfo.name}, Room: ${deviceWithRoomInfo.roomName ?: "Unassigned"}, Online: ${deviceWithRoomInfo.isOnline}")
                    
                    // Save updated device state to preferences (but don't overwrite room assignments)
                    try {
                        deviceRepository.updateDevice(deviceWithRoomInfo)
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
                    
                    println("DEBUG: HomeViewModel updated device ${deviceWithRoomInfo.name} (fallback), Room: ${deviceWithRoomInfo.roomName ?: "Unassigned"}, Online: ${deviceWithRoomInfo.isOnline}")
                }
            }
        }
    }



    fun clearAllData() {
        stopDeviceMonitoring()
        viewModelScope.launch {
            _devices.value = emptyList()
            _rooms.value = emptyList()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopDeviceMonitoring()
    }
    

    
    fun refreshDevices() {
        // Reload data from repositories
        refreshData()
    }
    
    /**
     * Public method to refresh all data - can be called when room assignments change
     */
    fun refreshAllData() {
        println("DEBUG: HomeViewModel refreshing all data...")
        loadData()
    }
    
    fun addDevice(device: Device) {
        viewModelScope.launch {
            deviceRepository.addDevice(device)
            // Refresh data to update UI
            refreshData()
        }
    }
    
    fun addRoom(room: Room) {
        viewModelScope.launch {
            roomRepository.addRoom(room)
            // Refresh data to update UI  
            refreshData()
        }
    }
} 