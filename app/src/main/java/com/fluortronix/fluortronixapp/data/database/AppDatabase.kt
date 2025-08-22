package com.fluortronix.fluortronixapp.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.fluortronix.fluortronixapp.data.dao.RoutineDao
import com.fluortronix.fluortronixapp.data.models.Routine

@Database(entities = [Routine::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routineDao(): RoutineDao
} 