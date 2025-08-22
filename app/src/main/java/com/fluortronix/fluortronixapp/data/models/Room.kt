package com.fluortronix.fluortronixapp.data.models

data class Room(
    val id: String,
    val name: String,
    val deviceIds: List<String> = emptyList(),
    val deviceCount: Int = 0,
    val allowedDeviceModel: String? = null, // Constraint: only one device model per room
    val isAllDevicesOn: Boolean = false, // Room-level power state
    val spectralData: RoomSpectralData? = null, // Room-level spectral data from Excel
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
) {
    /**
     * Check if a device model can be added to this room
     */
    fun canAddDeviceModel(deviceModel: String?): Boolean {
        if (deviceModel == null) return false
        return allowedDeviceModel == null || allowedDeviceModel == deviceModel
    }
    
    /**
     * Get display name for the allowed device model
     */
    fun getDeviceModelDisplay(): String {
        return allowedDeviceModel ?: "No devices"
    }
    
    /**
     * Check if room is empty
     */
    fun isEmpty(): Boolean = deviceIds.isEmpty()
    
    /**
     * Create a copy with updated device list
     */
    fun withDevices(devices: List<String>, newAllowedModel: String? = null): Room {
        return copy(
            deviceIds = devices,
            deviceCount = devices.size,
            allowedDeviceModel = newAllowedModel ?: allowedDeviceModel,
            lastModified = System.currentTimeMillis()
        )
    }
    
    /**
     * Create a copy with updated power state
     */
    fun withPowerState(isOn: Boolean): Room {
        return copy(
            isAllDevicesOn = isOn,
            lastModified = System.currentTimeMillis()
        )
    }
    
    /**
     * Create a copy with updated spectral data
     */
    fun withSpectralData(newSpectralData: RoomSpectralData?): Room {
        return copy(
            spectralData = newSpectralData,
            lastModified = System.currentTimeMillis()
        )
    }
    
    /**
     * Check if room has spectral data available
     */
    fun hasSpectralData(): Boolean = spectralData?.hasSpectralData() == true
    
    /**
     * Check if a device model is compatible with existing spectral data
     */
    fun isDeviceModelCompatibleWithSpectral(deviceModel: String?): Boolean {
        val spectralProfile = spectralData?.spectralProfile
        return spectralProfile?.isCompatibleWith(deviceModel) != false
    }
}

// Data class for room creation/editing
data class RoomCreationData(
    val name: String,
    val deviceIds: List<String> = emptyList(),
    val allowedDeviceModel: String? = null
)

// Data class for room statistics
data class RoomStats(
    val totalDevices: Int,
    val onlineDevices: Int,
    val offlineDevices: Int,
    val devicesOn: Int,
    val devicesOff: Int,
    val deviceModel: String?
) 