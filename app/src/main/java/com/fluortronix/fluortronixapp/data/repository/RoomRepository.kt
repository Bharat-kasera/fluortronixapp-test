package com.fluortronix.fluortronixapp.data.repository

import com.fluortronix.fluortronixapp.data.PreferencesManager
import com.fluortronix.fluortronixapp.data.models.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

interface RoomRepository {
    fun getRooms(): Flow<List<Room>>
    suspend fun addRoom(room: Room)
    suspend fun updateRoom(room: Room)
    suspend fun removeRoom(roomId: String)
    suspend fun getRoom(roomId: String): Room?
}

class RoomRepositoryImpl @Inject constructor(
    private val preferencesManager: PreferencesManager
) : RoomRepository {

    override fun getRooms(): Flow<List<Room>> = flow {
        try {
            val rooms = preferencesManager.getAllSavedRooms()
            emit(rooms)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }

    override suspend fun addRoom(room: Room) {
        try {
            preferencesManager.saveRoomData(room.id, room)
            println("DEBUG: Room ${room.name} saved to preferences")
        } catch (e: Exception) {
            println("DEBUG: Failed to save room ${room.name}: ${e.message}")
        }
    }

    override suspend fun updateRoom(room: Room) {
        try {
            preferencesManager.saveRoomData(room.id, room)
        } catch (e: Exception) {
            println("DEBUG: Failed to update room ${room.name}: ${e.message}")
        }
    }

    override suspend fun removeRoom(roomId: String) {
        try {
            preferencesManager.removeRoomData(roomId)
        } catch (e: Exception) {
            println("DEBUG: Failed to remove room $roomId: ${e.message}")
        }
    }

    override suspend fun getRoom(roomId: String): Room? {
        return try {
            preferencesManager.getRoomData(roomId)
        } catch (e: Exception) {
            null
        }
    }
} 