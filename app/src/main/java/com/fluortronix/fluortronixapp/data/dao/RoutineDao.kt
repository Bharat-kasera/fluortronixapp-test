package com.fluortronix.fluortronixapp.data.dao

import androidx.room.*
import com.fluortronix.fluortronixapp.data.models.Routine
import kotlinx.coroutines.flow.Flow

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY time ASC")
    fun getRoutines(): Flow<List<Routine>>

    @Query("SELECT * FROM routines WHERE targetId = :roomId ORDER BY time ASC")
    fun getRoutinesForRoom(roomId: String): Flow<List<Routine>>

    @Query("SELECT * FROM routines WHERE targetId = :roomId")
    suspend fun getRoutinesForRoomSync(roomId: String): List<Routine>

    @Query("SELECT * FROM routines WHERE isEnabled = 1 ORDER BY time ASC")
    fun getEnabledRoutines(): Flow<List<Routine>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: Routine): Long

    @Update
    suspend fun updateRoutine(routine: Routine)

    @Delete
    suspend fun deleteRoutine(routine: Routine)

    @Query("DELETE FROM routines WHERE targetId = :roomId")
    suspend fun deleteRoutinesForRoom(roomId: String)

    @Query("SELECT COUNT(*) FROM routines WHERE targetType = 'ROOM' AND targetId = :roomId")
    suspend fun getRoutineCountForRoom(roomId: String): Int

    @Query("UPDATE routines SET lastSyncedToDevices = :timestamp WHERE targetId = :roomId")
    suspend fun markRoutinesAsSynced(roomId: String, timestamp: Long)

    @Query("SELECT * FROM routines WHERE lastSyncedToDevices < createdAt")
    suspend fun getUnsyncedRoutines(): List<Routine>

    @Query("SELECT DISTINCT targetId FROM routines WHERE targetType = 'ROOM'")
    suspend fun getRoomsWithRoutines(): List<String>
} 