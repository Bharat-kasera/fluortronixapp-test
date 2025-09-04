package com.fluortronix.fluortronixapp.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.fluortronix.fluortronixapp.data.PreferencesManager
import com.fluortronix.fluortronixapp.data.dao.RoutineDao
import com.fluortronix.fluortronixapp.data.database.AppDatabase
import com.fluortronix.fluortronixapp.data.datasource.ESPDeviceService
import com.fluortronix.fluortronixapp.data.datasource.ImageCacheService
import com.fluortronix.fluortronixapp.data.datasource.WifiService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setLenient()
            .create()
    }

    @Provides
    @Singleton
    fun providePreferencesManager(
        @ApplicationContext context: Context,
        gson: Gson
    ): PreferencesManager {
        return PreferencesManager(context, gson)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        val migration1To2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new columns to routines table
                database.execSQL("ALTER TABLE routines ADD COLUMN devicePower INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE routines ADD COLUMN spectrumPresetId TEXT")
                database.execSQL("ALTER TABLE routines ADD COLUMN spectrumPresetName TEXT")
                database.execSQL("ALTER TABLE routines ADD COLUMN sliderValues TEXT NOT NULL DEFAULT '{}'")
                database.execSQL("ALTER TABLE routines ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE routines ADD COLUMN lastSyncedToDevices INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE routines ADD COLUMN roomName TEXT")
                
                // Update existing records to have default values for targetType
                database.execSQL("UPDATE routines SET targetType = 'ROOM' WHERE targetType IS NULL")
                database.execSQL("UPDATE routines SET actionType = 'DEVICE_POWER_AND_PRESET' WHERE actionType = 'POWER'")
            }
        }
        
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "fluortronix_database"
        )
        .addMigrations(migration1To2)
        .fallbackToDestructiveMigration() // For development - remove in production
        .build()
    }

    @Provides
    @Singleton
    fun provideRoutineDao(database: AppDatabase): RoutineDao {
        return database.routineDao()
    }

    @Provides
    @Singleton
    fun provideImageCacheService(
        @ApplicationContext context: Context
    ): ImageCacheService {
        return ImageCacheService(context)
    }

    @Provides
    @Singleton
    fun provideESPDeviceService(
        wifiService: WifiService,
        imageCacheService: ImageCacheService,
        @ApplicationContext context: Context
    ): ESPDeviceService {
        return ESPDeviceService(wifiService, imageCacheService, context)
    }
} 