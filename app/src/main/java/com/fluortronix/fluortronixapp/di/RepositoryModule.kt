package com.fluortronix.fluortronixapp.di

import com.fluortronix.fluortronixapp.data.repository.DeviceRepository
import com.fluortronix.fluortronixapp.data.repository.DeviceRepositoryImpl
import com.fluortronix.fluortronixapp.data.repository.RoomRepository
import com.fluortronix.fluortronixapp.data.repository.RoomRepositoryImpl
import com.fluortronix.fluortronixapp.data.repository.RoutineRepository
import com.fluortronix.fluortronixapp.data.repository.RoutineRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRoutineRepository(
        routineRepositoryImpl: RoutineRepositoryImpl
    ): RoutineRepository

    @Binds
    @Singleton
    abstract fun bindDeviceRepository(
        deviceRepositoryImpl: DeviceRepositoryImpl
    ): DeviceRepository

    @Binds
    @Singleton
    abstract fun bindRoomRepository(
        roomRepositoryImpl: RoomRepositoryImpl
    ): RoomRepository
} 