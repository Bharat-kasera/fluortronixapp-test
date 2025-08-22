package com.fluortronix.fluortronixapp.data.repository

import com.fluortronix.fluortronixapp.data.dao.RoutineDao
import com.fluortronix.fluortronixapp.data.models.Routine
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface RoutineRepository {
    fun getRoutines(): Flow<List<Routine>>
    fun getRoutinesForRoom(roomId: String): Flow<List<Routine>>
    suspend fun getRoutinesForRoomSync(roomId: String): List<Routine>
    fun getEnabledRoutines(): Flow<List<Routine>>
    suspend fun addRoutine(routine: Routine): Long
    suspend fun updateRoutine(routine: Routine)
    suspend fun deleteRoutine(routine: Routine)
    suspend fun deleteRoutinesForRoom(roomId: String)
    suspend fun getRoutineCountForRoom(roomId: String): Int
    suspend fun markRoutinesAsSynced(roomId: String, timestamp: Long = System.currentTimeMillis())
    suspend fun getUnsyncedRoutines(): List<Routine>
    suspend fun getRoomsWithRoutines(): List<String>
}

class RoutineRepositoryImpl @Inject constructor(
    private val routineDao: RoutineDao
) : RoutineRepository {
    override fun getRoutines(): Flow<List<Routine>> = routineDao.getRoutines()

    override fun getRoutinesForRoom(roomId: String): Flow<List<Routine>> = 
        routineDao.getRoutinesForRoom(roomId)

    override suspend fun getRoutinesForRoomSync(roomId: String): List<Routine> = 
        routineDao.getRoutinesForRoomSync(roomId)

    override fun getEnabledRoutines(): Flow<List<Routine>> = 
        routineDao.getEnabledRoutines()

    override suspend fun addRoutine(routine: Routine): Long {
        return routineDao.insertRoutine(routine)
    }

    override suspend fun updateRoutine(routine: Routine) {
        routineDao.updateRoutine(routine)
    }

    override suspend fun deleteRoutine(routine: Routine) {
        routineDao.deleteRoutine(routine)
    }

    override suspend fun deleteRoutinesForRoom(roomId: String) {
        routineDao.deleteRoutinesForRoom(roomId)
    }

    override suspend fun getRoutineCountForRoom(roomId: String): Int {
        return routineDao.getRoutineCountForRoom(roomId)
    }

    override suspend fun markRoutinesAsSynced(roomId: String, timestamp: Long) {
        routineDao.markRoutinesAsSynced(roomId, timestamp)
    }

    override suspend fun getUnsyncedRoutines(): List<Routine> {
        return routineDao.getUnsyncedRoutines()
    }

    override suspend fun getRoomsWithRoutines(): List<String> {
        return routineDao.getRoomsWithRoutines()
    }
} 