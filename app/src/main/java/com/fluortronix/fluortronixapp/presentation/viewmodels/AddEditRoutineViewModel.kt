package com.fluortronix.fluortronixapp.presentation.viewmodels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluortronix.fluortronixapp.data.models.*
import com.fluortronix.fluortronixapp.data.repository.RoomRepository
import com.fluortronix.fluortronixapp.data.repository.RoutineRepository
import com.fluortronix.fluortronixapp.data.repository.DeviceRepository
import com.fluortronix.fluortronixapp.data.datasource.ESPDeviceService
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddEditRoutineViewModel @Inject constructor(
    private val routineRepository: RoutineRepository,
    private val roomRepository: RoomRepository,
    private val deviceRepository: DeviceRepository,
    private val espDeviceService: ESPDeviceService,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms = _rooms.asStateFlow()

    private val _routine = MutableStateFlow<Routine?>(null)
    val routine = _routine.asStateFlow()

    private val _selectedRoom = MutableStateFlow<Room?>(null)
    val selectedRoom = _selectedRoom.asStateFlow()

    private val _availablePresets = MutableStateFlow<List<SpectrumPreset>>(emptyList())
    val availablePresets = _availablePresets.asStateFlow()

    // UI State Management
    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()
    
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()
    
    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess = _saveSuccess.asStateFlow()

    val routineName = MutableStateFlow("")
    val targetId = MutableStateFlow("") // Room ID
    val time = MutableStateFlow("12:00")
    val devicePower = MutableStateFlow(true)
    val selectedPresetId = MutableStateFlow<String?>(null)
    val days = MutableStateFlow<List<String>>(listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"))

    init {
        // Load all rooms
        viewModelScope.launch {
            roomRepository.getRooms().collect { _rooms.value = it }
        }

        // Auto-select room from navigation parameter
        val roomIdFromNav = savedStateHandle.get<String>("roomId")
        if (roomIdFromNav != null) {
            viewModelScope.launch {
                onRoomSelected(roomIdFromNav)
            }
        }

        // Load existing routine if editing
        val routineId = savedStateHandle.get<Int>("routineId")
        if (routineId != null && routineId != -1) {
            viewModelScope.launch {
                routineRepository.getRoutines().map { routines ->
                    routines.find { it.id == routineId }
                }.collect { routine ->
                    if (routine != null) {
                        _routine.value = routine
                        routineName.value = routine.name
                        targetId.value = routine.targetId
                        time.value = routine.time
                        devicePower.value = routine.devicePower
                        selectedPresetId.value = routine.spectrumPresetId
                        days.value = routine.days
                        
                        // Load the room and its presets
                        loadRoomData(routine.targetId)
                    }
                }
            }
        }
    }

    /**
     * Load room data and available spectrum presets
     */
    private suspend fun loadRoomData(roomId: String) {
        val room = roomRepository.getRoom(roomId)
        if (room != null) {
            _selectedRoom.value = room
            _availablePresets.value = room.spectralData?.spectrumPresets ?: emptyList()
        }
    }

    /**
     * Called when user selects a room
     */
    fun onRoomSelected(roomId: String) {
        targetId.value = roomId
        viewModelScope.launch {
            loadRoomData(roomId)
            // Reset preset selection when room changes
            selectedPresetId.value = null
        }
    }

    /**
     * Called when user selects a spectrum preset
     */
    fun onPresetSelected(presetId: String?) {
        selectedPresetId.value = presetId
    }
    
    /**
     * Clear error message
     */
    fun clearError() {
        _errorMessage.value = null
    }
    
    /**
     * Clear save success state
     */
    fun clearSaveSuccess() {
        _saveSuccess.value = false
    }

    fun saveRoutine() = viewModelScope.launch {
        _isLoading.value = true
        _errorMessage.value = null
        
        try {
            // Validation
            if (routineName.value.isBlank()) {
                throw Exception("Routine name cannot be empty")
            }
            
            if (targetId.value.isBlank()) {
                throw Exception("Please select a room")
            }
            
            if (days.value.isEmpty()) {
                throw Exception("Please select at least one day")
            }
            
            if (devicePower.value && selectedPresetId.value == null) {
                throw Exception("Please select a spectrum preset for ON routines")
            }
            
            val selectedRoom = _selectedRoom.value
            val selectedPreset = availablePresets.value.find { it.id == selectedPresetId.value }
            
            // Validate preset still exists
            if (devicePower.value && selectedPresetId.value != null && selectedPreset == null) {
                throw Exception("Selected preset no longer exists. Please choose another preset.")
            }
            
            // Get slider values from selected preset
            val presetSliderValues = if (devicePower.value && selectedPreset != null) {
                println("DEBUG: AddEditRoutineViewModel - Selected preset '${selectedPreset.name}' slider values: ${selectedPreset.sliderValues}")
                selectedPreset.sliderValues
            } else {
                emptyMap() // For OFF routines
            }
            
            val routineToSave = _routine.value?.copy(
                name = routineName.value.trim(),
                targetType = TargetType.ROOM,
                targetId = targetId.value,
                time = time.value,
                actionType = ActionType.DEVICE_POWER_AND_PRESET,
                actionValue = if (devicePower.value) "ON" else "OFF",
                days = days.value,
                devicePower = devicePower.value,
                spectrumPresetId = selectedPresetId.value,
                spectrumPresetName = selectedPreset?.name ?: "Off",
                sliderValues = presetSliderValues,
                roomName = selectedRoom?.name,
                lastSyncedToDevices = 0
            ) ?: Routine(
                name = routineName.value.trim(),
                targetType = TargetType.ROOM,
                targetId = targetId.value,
                time = time.value,
                actionType = ActionType.DEVICE_POWER_AND_PRESET,
                actionValue = if (devicePower.value) "ON" else "OFF",
                days = days.value,
                devicePower = devicePower.value,
                spectrumPresetId = selectedPresetId.value,
                spectrumPresetName = selectedPreset?.name ?: "Off",
                sliderValues = presetSliderValues,
                roomName = selectedRoom?.name
            )

            // Check routine limit for new routines (6 max per room)
            if (_routine.value == null) {
                val existingRoutineCount = routineRepository.getRoutineCountForRoom(targetId.value)
                if (existingRoutineCount >= 6) {
                    throw Exception("Maximum 6 routines allowed per room. Current count: $existingRoutineCount")
                }
            }
            
            // Check for duplicate routine names in the same room
            val existingRoutines = routineRepository.getRoutinesForRoomSync(targetId.value)
            val trimmedName = routineName.value.trim()
            val duplicateRoutine = existingRoutines.find { 
                it.name.equals(trimmedName, ignoreCase = true) && 
                it.id != (_routine.value?.id ?: -1) // Exclude current routine when editing
            }
            if (duplicateRoutine != null) {
                throw Exception("A routine with the name '$trimmedName' already exists in this room")
            }
            
            // Save the routine
            if (_routine.value == null) {
                routineRepository.addRoutine(routineToSave)
            } else {
                routineRepository.updateRoutine(routineToSave)
            }

            // Auto-sync to ESP devices
            autoSyncToDevices(routineToSave.targetId)
            
            _saveSuccess.value = true
            
        } catch (e: Exception) {
            _errorMessage.value = e.message ?: "Failed to save routine"
            println("DEBUG: Failed to save routine: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    /**
     * Automatically sync routines to ESP devices after saving
     */
    private suspend fun autoSyncToDevices(roomId: String) {
        try {
            val routines = routineRepository.getRoutinesForRoomSync(roomId)
            val devices = deviceRepository.getDevicesForRoom(roomId)
            
            if (devices.isNotEmpty()) {
                println("DEBUG: Auto-syncing ${routines.size} routines to ${devices.size} devices in room $roomId")
                
                val result = espDeviceService.syncRoutinesToRoom(devices, routines)
                
                if (result.isSuccess) {
                    val syncResults = result.getOrNull() ?: emptyMap()
                    val successCount = syncResults.values.count { it == "Success" }
                    
                    // Mark routines as synced
                    routineRepository.markRoutinesAsSynced(roomId)
                    
                    println("DEBUG: Auto-sync completed: $successCount/${devices.size} devices synced successfully")
                } else {
                    println("DEBUG: Auto-sync failed: ${result.exceptionOrNull()?.message}")
                }
            } else {
                println("DEBUG: No devices found in room $roomId for auto-sync")
            }
        } catch (e: Exception) {
            println("DEBUG: Auto-sync exception: ${e.message}")
        }
    }
} 