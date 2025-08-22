package com.fluortronix.fluortronixapp.data.repository

import com.fluortronix.fluortronixapp.data.PreferencesManager
import com.fluortronix.fluortronixapp.data.models.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

interface DeviceRepository {
    fun getDevices(): Flow<List<Device>>
    suspend fun getDevicesForRoom(roomId: String): List<Device>
    suspend fun addDevice(device: Device)
    suspend fun updateDevice(device: Device)
    suspend fun removeDevice(deviceId: String)
    suspend fun getDevice(deviceId: String): Device?
}

class DeviceRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager
) : DeviceRepository {

    override fun getDevices(): Flow<List<Device>> = flow {
        try {
            val devices = preferencesManager.getAllSavedDevices()
            emit(devices)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun getDevicesForRoom(roomId: String): List<Device> {
        return try {
            val allDevices = preferencesManager.getAllSavedDevices()
            allDevices.filter { it.roomId == roomId }
        } catch (e: Exception) {
            println("DEBUG: Failed to get devices for room $roomId: ${e.message}")
            emptyList()
        }
    }

    override suspend fun addDevice(device: Device) {
        try {
            preferencesManager.saveDeviceData(device.id, device)
            println("DEBUG: Device ${device.name} saved to preferences")
        } catch (e: Exception) {
            println("DEBUG: Failed to save device ${device.name}: ${e.message}")
        }
    }

    override suspend fun updateDevice(device: Device) {
        try {
            preferencesManager.saveDeviceData(device.id, device)
        } catch (e: Exception) {
            println("DEBUG: Failed to update device ${device.name}: ${e.message}")
        }
    }

    override suspend fun removeDevice(deviceId: String) {
        try {
            preferencesManager.removeDeviceData(deviceId)
        } catch (e: Exception) {
            println("DEBUG: Failed to remove device $deviceId: ${e.message}")
        }
    }

    override suspend fun getDevice(deviceId: String): Device? {
        return try {
            preferencesManager.getDeviceData(deviceId)
        } catch (e: Exception) {
            null
        }
    }
} 