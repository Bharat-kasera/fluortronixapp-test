package com.fluortronix.fluortronixapp.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.fluortronix.fluortronixapp.data.models.Device
import com.fluortronix.fluortronixapp.data.models.Room
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context,
    private val gson: Gson = Gson()
) {
    companion object {
        private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        private val DEVICE_DATA_PREFIX = "device_data_"
        private val SAVED_DEVICES_LIST = stringPreferencesKey("saved_devices_list")
        private val ROOM_DATA_PREFIX = "room_data_"
        private val SAVED_ROOMS_LIST = stringPreferencesKey("saved_rooms_list")
    }

    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data
        .map { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING] ?: false
        }

    suspend fun setOnboardingCompleted() {
        context.dataStore.edit { preferences ->
            preferences[HAS_COMPLETED_ONBOARDING] = true
        }
    }
    
    /**
     * Saves device data to local storage
     */
    suspend fun saveDeviceData(deviceId: String, device: Device) {
        val deviceKey = stringPreferencesKey("${DEVICE_DATA_PREFIX}$deviceId")
        val deviceJson = gson.toJson(device)
        
        context.dataStore.edit { preferences ->
            preferences[deviceKey] = deviceJson
            
            // Also update the list of saved device IDs
            val currentDeviceIds = getSavedDeviceIds().toMutableSet()
            currentDeviceIds.add(deviceId)
            preferences[SAVED_DEVICES_LIST] = gson.toJson(currentDeviceIds.toList())
        }
    }
    
    /**
     * Retrieves device data from local storage
     */
    suspend fun getDeviceData(deviceId: String): Device? {
        val deviceKey = stringPreferencesKey("${DEVICE_DATA_PREFIX}$deviceId")
        
        return try {
            val preferences = context.dataStore.data.first()
            val deviceJson = preferences[deviceKey]
            
            if (deviceJson != null) {
                gson.fromJson(deviceJson, Device::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets all saved device IDs
     */
    suspend fun getSavedDeviceIds(): List<String> {
        return try {
            val preferences = context.dataStore.data.first()
            val deviceIdsJson = preferences[SAVED_DEVICES_LIST]
            
            if (deviceIdsJson != null) {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(deviceIdsJson, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Gets all saved devices
     */
    suspend fun getAllSavedDevices(): List<Device> {
        val deviceIds = getSavedDeviceIds()
        return deviceIds.mapNotNull { deviceId ->
            getDeviceData(deviceId)
        }
    }
    
    /**
     * Removes device data from storage
     */
    suspend fun removeDeviceData(deviceId: String) {
        val deviceKey = stringPreferencesKey("${DEVICE_DATA_PREFIX}$deviceId")
        
        context.dataStore.edit { preferences ->
            preferences.remove(deviceKey)
            
            // Update the list of saved device IDs
            val currentDeviceIds = getSavedDeviceIds().toMutableSet()
            currentDeviceIds.remove(deviceId)
            preferences[SAVED_DEVICES_LIST] = gson.toJson(currentDeviceIds.toList())
        }
    }
    
    /**
     * Clears all device data
     */
    suspend fun clearAllDeviceData() {
        val deviceIds = getSavedDeviceIds()
        
        context.dataStore.edit { preferences ->
            // Remove all device data
            deviceIds.forEach { deviceId ->
                val deviceKey = stringPreferencesKey("${DEVICE_DATA_PREFIX}$deviceId")
                preferences.remove(deviceKey)
            }
            
            // Clear the device list
            preferences.remove(SAVED_DEVICES_LIST)
        }
    }
    
    /**
     * Saves device slider settings for quick access
     */
    suspend fun saveDeviceSliderSettings(deviceId: String, sliderValues: List<Int>) {
        val device = getDeviceData(deviceId)
        if (device != null) {
            val updatedDevice = device.copy(
                sliderValues = sliderValues,
                lastSeen = System.currentTimeMillis()
            )
            saveDeviceData(deviceId, updatedDevice)
        }
    }
    
    /**
     * Saves device power state
     */
    suspend fun saveDevicePowerState(deviceId: String, isOn: Boolean) {
        val device = getDeviceData(deviceId)
        if (device != null) {
            val updatedDevice = device.copy(
                isOn = isOn,
                lastSeen = System.currentTimeMillis()
            )
            saveDeviceData(deviceId, updatedDevice)
        }
    }
    
    /**
     * Saves room data to local storage
     */
    suspend fun saveRoomData(roomId: String, room: Room) {
        val roomKey = stringPreferencesKey("${ROOM_DATA_PREFIX}$roomId")
        val roomJson = gson.toJson(room)
        
        context.dataStore.edit { preferences ->
            preferences[roomKey] = roomJson
            
            // Also update the list of saved room IDs
            val currentRoomIds = getSavedRoomIds().toMutableSet()
            currentRoomIds.add(roomId)
            preferences[SAVED_ROOMS_LIST] = gson.toJson(currentRoomIds.toList())
        }
    }
    
    /**
     * Retrieves room data from local storage
     */
    suspend fun getRoomData(roomId: String): Room? {
        val roomKey = stringPreferencesKey("${ROOM_DATA_PREFIX}$roomId")
        
        return try {
            val preferences = context.dataStore.data.first()
            val roomJson = preferences[roomKey]
            
            if (roomJson != null) {
                gson.fromJson(roomJson, Room::class.java)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Gets all saved room IDs
     */
    suspend fun getSavedRoomIds(): List<String> {
        return try {
            val preferences = context.dataStore.data.first()
            val roomIdsJson = preferences[SAVED_ROOMS_LIST]
            
            if (roomIdsJson != null) {
                val type = object : TypeToken<List<String>>() {}.type
                gson.fromJson(roomIdsJson, type) ?: emptyList()
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * Gets all saved rooms
     */
    suspend fun getAllSavedRooms(): List<Room> {
        val roomIds = getSavedRoomIds()
        return roomIds.mapNotNull { roomId ->
            getRoomData(roomId)
        }
    }
    
    /**
     * Removes room data from storage
     */
    suspend fun removeRoomData(roomId: String) {
        val roomKey = stringPreferencesKey("${ROOM_DATA_PREFIX}$roomId")
        
        context.dataStore.edit { preferences ->
            preferences.remove(roomKey)
            
            // Update the list of saved room IDs
            val currentRoomIds = getSavedRoomIds().toMutableSet()
            currentRoomIds.remove(roomId)
            preferences[SAVED_ROOMS_LIST] = gson.toJson(currentRoomIds.toList())
        }
    }
    
    /**
     * Clears all room data
     */
    suspend fun clearAllRoomData() {
        val roomIds = getSavedRoomIds()
        
        context.dataStore.edit { preferences ->
            // Remove all room data
            roomIds.forEach { roomId ->
                val roomKey = stringPreferencesKey("${ROOM_DATA_PREFIX}$roomId")
                preferences.remove(roomKey)
            }
            
            // Clear the room list
            preferences.remove(SAVED_ROOMS_LIST)
        }
    }
    
    /**
     * Assigns a device to a room and updates both entities
     */
    suspend fun assignDeviceToRoom(deviceId: String, roomId: String): Boolean {
        return try {
            println("DEBUG: PreferencesManager assigning device $deviceId to room $roomId")
            
            val device = getDeviceData(deviceId)
            val room = getRoomData(roomId)
            
            if (device == null) {
                println("DEBUG: Device $deviceId not found")
                return false
            }
            
            if (room == null) {
                println("DEBUG: Room $roomId not found")
                return false
            }
            
            println("DEBUG: Found device: ${device.name}, Room: ${room.name}")
            
            // Check if device model is compatible with room
            if (!room.canAddDeviceModel(device.deviceModel)) {
                println("DEBUG: Device model ${device.deviceModel} not compatible with room ${room.name}")
                return false
            }
            
            // Update device with room assignment
            val updatedDevice = device.copy(
                roomId = roomId,
                roomName = room.name
            )
            
            // Update room with new device
            val updatedDeviceIds = room.deviceIds.toMutableList()
            if (!updatedDeviceIds.contains(deviceId)) {
                updatedDeviceIds.add(deviceId)
            }
            
            val updatedRoom = room.withDevices(
                devices = updatedDeviceIds,
                newAllowedModel = device.deviceModel
            )
            
            println("DEBUG: Saving device with room assignment: ${updatedDevice.name} -> ${updatedDevice.roomName}")
            println("DEBUG: Updated room ${updatedRoom.name} now has ${updatedRoom.deviceCount} devices")
            
            // Save both entities
            saveDeviceData(deviceId, updatedDevice)
            saveRoomData(roomId, updatedRoom)
            
            println("DEBUG: Successfully assigned device ${device.name} to room ${room.name}")
            
            true
        } catch (e: Exception) {
            println("DEBUG: Failed to assign device to room: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    /**
     * Removes a device from its current room
     */
    suspend fun removeDeviceFromRoom(deviceId: String): Boolean {
        return try {
            val device = getDeviceData(deviceId) ?: return false
            val roomId = device.roomId ?: return true // Already not in a room
            val room = getRoomData(roomId) ?: return false
            
            // Update device to remove room assignment
            val updatedDevice = device.copy(
                roomId = null,
                roomName = null
            )
            
            // Update room to remove device
            val updatedDeviceIds = room.deviceIds.toMutableList()
            updatedDeviceIds.remove(deviceId)
            
            val updatedRoom = if (updatedDeviceIds.isEmpty()) {
                // Room is now empty - reset both device model AND spectral data constraints
                room.withDevices(
                    devices = updatedDeviceIds,
                    newAllowedModel = null
                ).withSpectralData(null)
            } else {
                // Room still has devices - keep existing constraints
                room.withDevices(
                    devices = updatedDeviceIds,
                    newAllowedModel = room.allowedDeviceModel
                )
            }
            
            // Save both entities
            saveDeviceData(deviceId, updatedDevice)
            saveRoomData(roomId, updatedRoom)
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Updates room power state and all devices in the room
     */
    suspend fun setRoomPowerState(roomId: String, isOn: Boolean): Boolean {
        return try {
            val room = getRoomData(roomId) ?: return false
            
            // Update all devices in the room
            room.deviceIds.forEach { deviceId ->
                saveDevicePowerState(deviceId, isOn)
            }
            
            // Update room power state
            val updatedRoom = room.withPowerState(isOn)
            saveRoomData(roomId, updatedRoom)
            
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Store previous PWM values for a device (used for power toggle)
     */
    suspend fun storeDevicePreviousValues(deviceId: String, values: List<Int>): Boolean {
        return try {
            val editor = context.getSharedPreferences("device_previous_values", Context.MODE_PRIVATE).edit()
            val valuesString = values.joinToString(",")
            editor.putString("device_$deviceId", valuesString)
            editor.apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get previous PWM values for a device (used for power toggle)
     */
    suspend fun getDevicePreviousValues(deviceId: String): List<Int> {
        return try {
            val prefs = context.getSharedPreferences("device_previous_values", Context.MODE_PRIVATE)
            val valuesString = prefs.getString("device_$deviceId", "") ?: ""
            if (valuesString.isNotEmpty()) {
                valuesString.split(",").mapNotNull { it.toIntOrNull() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Clear previous PWM values for a device
     */
    suspend fun clearDevicePreviousValues(deviceId: String): Boolean {
        return try {
            val editor = context.getSharedPreferences("device_previous_values", Context.MODE_PRIVATE).edit()
            editor.remove("device_$deviceId")
            editor.apply()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Syncs room power state with actual device states to prevent inconsistencies
     */
    suspend fun syncRoomPowerState(roomId: String): Boolean {
        return try {
            val room = getRoomData(roomId) ?: return false
            val roomDevices = room.deviceIds.mapNotNull { getDeviceData(it) }
            
            if (roomDevices.isEmpty()) {
                // No devices in room, set power state to false
                val updatedRoom = room.withPowerState(false)
                saveRoomData(roomId, updatedRoom)
                return true
            }
            
            // Calculate actual power state based on devices
            val allDevicesOn = roomDevices.all { it.isOn }
            
            // Update room power state if it doesn't match
            if (room.isAllDevicesOn != allDevicesOn) {
                val updatedRoom = room.withPowerState(allDevicesOn)
                saveRoomData(roomId, updatedRoom)
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Updates room device count and device list to prevent inconsistencies
     */
    suspend fun syncRoomDeviceCount(roomId: String): Boolean {
        return try {
            val room = getRoomData(roomId) ?: return false
            val actualDevices = getAllSavedDevices().filter { it.roomId == roomId }
            val actualDeviceIds = actualDevices.map { it.id }
            
            // Check if room data is consistent
            val isConsistent = room.deviceIds.sorted() == actualDeviceIds.sorted() && 
                              room.deviceCount == actualDevices.size
            
            if (!isConsistent) {
                // Update room with actual device data
                val updatedRoom = room.withDevices(
                    devices = actualDeviceIds,
                    newAllowedModel = actualDevices.firstOrNull()?.deviceModel
                )
                saveRoomData(roomId, updatedRoom)
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Validates and fixes all room data inconsistencies
     */
    suspend fun validateAndFixRoomData(): Boolean {
        return try {
            val allRooms = getAllSavedRooms()
            
            allRooms.forEach { room ->
                syncRoomDeviceCount(room.id)
                syncRoomPowerState(room.id)
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Handles orphaned devices (devices that reference non-existent rooms)
     */
    suspend fun fixOrphanedDevices(): Boolean {
        return try {
            val allDevices = getAllSavedDevices()
            val allRooms = getAllSavedRooms()
            val validRoomIds = allRooms.map { it.id }.toSet()
            
            allDevices.forEach { device ->
                if (device.roomId != null && !validRoomIds.contains(device.roomId)) {
                    // Device references a non-existent room, remove room assignment
                    val updatedDevice = device.copy(
                        roomId = null,
                        roomName = null
                    )
                    saveDeviceData(device.id, updatedDevice)
                }
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
} 