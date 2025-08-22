package com.fluortronix.fluortronixapp.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluortronix.fluortronixapp.data.models.Routine
import com.fluortronix.fluortronixapp.data.models.Room
import com.fluortronix.fluortronixapp.data.models.Device
import com.fluortronix.fluortronixapp.data.models.SpectrumPreset
import com.fluortronix.fluortronixapp.data.repository.RoutineRepository
import com.fluortronix.fluortronixapp.data.repository.RoomRepository
import com.fluortronix.fluortronixapp.data.repository.DeviceRepository
import com.fluortronix.fluortronixapp.data.datasource.ESPDeviceService
import com.fluortronix.fluortronixapp.data.PreferencesManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject


data class ScheduleUiState(
    val selectedRoom: Room? = null,
    val rooms: List<Room> = emptyList(),
    val routinesForSelectedRoom: List<Routine> = emptyList(),
    val allRoutines: List<Routine> = emptyList(),
    val devicesInSelectedRoom: List<Device> = emptyList(),
    val availablePresets: List<SpectrumPreset> = emptyList(),
    val isLoading: Boolean = false,
    val isSyncingToDevices: Boolean = false,
    val error: String? = null,
    val syncResults: Map<String, String> = emptyMap(),
    val maxRoutinesPerRoom: Int = 6
)

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val roomRepository: RoomRepository,
    private val deviceRepository: DeviceRepository,
    private val espDeviceService: ESPDeviceService,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState = _uiState.asStateFlow()

    // Legacy flow for backward compatibility
    val routines = _uiState.map { it.allRoutines }

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Load all data streams
                combine(
                    routineRepository.getRoutines(),
                    roomRepository.getRooms(),
                    deviceRepository.getDevices()
                ) { routines, rooms, devices ->
                    Triple(routines, rooms, devices)
                }.catch { error ->
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            error = "Failed to load data: ${error.message}"
                        ) 
                    }
                }.collect { (routines, rooms, devices) ->
                    val selectedRoom = _uiState.value.selectedRoom
                    val routinesForRoom = if (selectedRoom != null) {
                        routines.filter { it.targetId == selectedRoom.id }
                    } else emptyList()
                    
                    val devicesInRoom = if (selectedRoom != null) {
                        devices.filter { it.roomId == selectedRoom.id }
                    } else emptyList()
                    
                    // Load available spectrum presets for selected room
                    val presets = loadPresetsForRoom(selectedRoom?.id)
                    
                    _uiState.update { 
                        it.copy(
                            allRoutines = routines,
                            rooms = rooms,
                            routinesForSelectedRoom = routinesForRoom,
                            devicesInSelectedRoom = devicesInRoom,
                            availablePresets = presets,
                            isLoading = false,
                            error = null
                        ) 
                    }
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = "Failed to initialize: ${e.message}"
                    ) 
                }
            }
        }
    }

    fun selectRoom(room: Room) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(selectedRoom = room, isLoading = true) }
                
                // Load routines for this room
                val routines = routineRepository.getRoutinesForRoomSync(room.id)
                val devices = deviceRepository.getDevicesForRoom(room.id)
                val presets = loadPresetsForRoom(room.id)
                
                _uiState.update { 
                    it.copy(
                        routinesForSelectedRoom = routines,
                        devicesInSelectedRoom = devices,
                        availablePresets = presets,
                        isLoading = false,
                        error = null
                    ) 
                }
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        isLoading = false, 
                        error = "Failed to load room data: ${e.message}"
                    ) 
                }
            }
        }
    }

    fun addRoutine(routine: Routine) = viewModelScope.launch {
        try {
            val selectedRoom = _uiState.value.selectedRoom
            if (selectedRoom == null) {
                _uiState.update { it.copy(error = "Please select a room first") }
                return@launch
            }
            
            // Check routine limit
            val currentCount = _uiState.value.routinesForSelectedRoom.size
            if (currentCount >= _uiState.value.maxRoutinesPerRoom) {
                _uiState.update { 
                    it.copy(error = "Maximum ${_uiState.value.maxRoutinesPerRoom} routines allowed per room") 
                }
                return@launch
            }
            
            // Add room info to routine
            val routineWithRoom = routine.copy(
                targetId = selectedRoom.id,
                roomName = selectedRoom.name
            )
            
            val routineId = routineRepository.addRoutine(routineWithRoom)
            
            // Sync to ESP devices if devices are available
            syncRoutinesToESPDevices(selectedRoom.id)
            
            _uiState.update { it.copy(error = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to add routine: ${e.message}") }
        }
    }

    fun updateRoutine(routine: Routine) = viewModelScope.launch {
        try {
            routineRepository.updateRoutine(routine)
            
            // Sync to ESP devices
            routine.targetId.let { roomId ->
                syncRoutinesToESPDevices(roomId)
            }
            
            _uiState.update { it.copy(error = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to update routine: ${e.message}") }
        }
    }

    fun deleteRoutine(routine: Routine) = viewModelScope.launch {
        try {
            routineRepository.deleteRoutine(routine)
            
            // Sync to ESP devices
            routine.targetId.let { roomId ->
                syncRoutinesToESPDevices(roomId)
            }
            
            _uiState.update { it.copy(error = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to delete routine: ${e.message}") }
        }
    }

    fun toggleRoutineEnabled(routine: Routine) = viewModelScope.launch {
        try {
            val updatedRoutine = routine.copy(isEnabled = !routine.isEnabled)
            routineRepository.updateRoutine(updatedRoutine)
            
            // Sync to ESP devices
            syncRoutinesToESPDevices(routine.targetId)
            
            _uiState.update { it.copy(error = null) }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Failed to toggle routine: ${e.message}") }
        }
    }

    fun syncRoutinesToESPDevices(roomId: String) = viewModelScope.launch {
        try {
            _uiState.update { it.copy(isSyncingToDevices = true, syncResults = emptyMap()) }
            
            val routines = routineRepository.getRoutinesForRoomSync(roomId)
            val devices = deviceRepository.getDevicesForRoom(roomId)
            
            if (devices.isEmpty()) {
                _uiState.update { 
                    it.copy(
                        isSyncingToDevices = false,
                        error = "No devices found in selected room"
                    ) 
                }
                return@launch
            }
            
            println("DEBUG: Syncing ${routines.size} routines to ${devices.size} devices in room $roomId")
            
            val result = espDeviceService.syncRoutinesToRoom(devices, routines)
            
            if (result.isSuccess) {
                val syncResults = result.getOrNull() ?: emptyMap()
                val successCount = syncResults.values.count { it == "Success" }
                
                // Mark routines as synced
                routineRepository.markRoutinesAsSynced(roomId)
                
                _uiState.update { 
                    it.copy(
                        isSyncingToDevices = false,
                        syncResults = syncResults,
                        error = if (successCount > 0) null else "Failed to sync to any device"
                    ) 
                }
            } else {
                _uiState.update { 
                    it.copy(
                        isSyncingToDevices = false,
                        error = "Sync failed: ${result.exceptionOrNull()?.message}"
                    ) 
                }
            }
        } catch (e: Exception) {
            _uiState.update { 
                it.copy(
                    isSyncingToDevices = false,
                    error = "Sync error: ${e.message}"
                ) 
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSyncResults() {
        _uiState.update { it.copy(syncResults = emptyMap()) }
    }

    private suspend fun loadPresetsForRoom(roomId: String?): List<SpectrumPreset> {
        return if (roomId != null) {
            try {
                val room = preferencesManager.getRoomData(roomId)
                room?.spectralData?.spectrumPresets ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    fun canAddMoreRoutines(): Boolean {
        return _uiState.value.routinesForSelectedRoom.size < _uiState.value.maxRoutinesPerRoom
    }

    fun getRemainingRoutineSlots(): Int {
        return _uiState.value.maxRoutinesPerRoom - _uiState.value.routinesForSelectedRoom.size
    }

    // Backward compatibility methods
    fun addRoutine(routine: Routine, onComplete: (Boolean) -> Unit = {}) = viewModelScope.launch {
        try {
            addRoutine(routine)
            onComplete(true)
        } catch (e: Exception) {
            onComplete(false)
        }
    }
} 