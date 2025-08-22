package com.fluortronix.fluortronixapp.presentation.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.fluortronix.fluortronixapp.presentation.viewmodels.AddEditRoutineViewModel
import com.fluortronix.fluortronixapp.ui.theme.FluortronixappTheme
import com.fluortronix.fluortronixapp.R
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRoutineScreen(
    navController: NavController,
    viewModel: AddEditRoutineViewModel = hiltViewModel()
) {
    val routineName by viewModel.routineName.collectAsState()
    val targetId by viewModel.targetId.collectAsState()
    val time by viewModel.time.collectAsState()
    val devicePower by viewModel.devicePower.collectAsState()
    val selectedPresetId by viewModel.selectedPresetId.collectAsState()
    val days by viewModel.days.collectAsState()

    val rooms by viewModel.rooms.collectAsState()
    val selectedRoom by viewModel.selectedRoom.collectAsState()
    val availablePresets by viewModel.availablePresets.collectAsState()
    
    // Error and loading states
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val saveSuccess by viewModel.saveSuccess.collectAsState()
    
    // Handle save success
    LaunchedEffect(saveSuccess) {
        if (saveSuccess) {
            viewModel.clearSaveSuccess()
            navController.popBackStack()
        }
    }

    FluortronixappTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            // Header with Background Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 44.dp, start = 16.dp, end = 16.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(16.dp))
            ) {
                // Background Image
                Image(
                    painter = painterResource(id = R.drawable.scheduleimg),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    contentScale = ContentScale.Crop
                )
                
                // Content Row - Centered in the Box
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = Color.White,
                                shape = RoundedCornerShape(50.dp)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "Add/Edit Routine",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Create or modify routine settings",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                OutlinedTextField(
                    value = routineName,
                    onValueChange = { viewModel.routineName.value = it },
                    label = { Text("Routine Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Room Selection
                Text(
                    text = "Target Room",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Dropdown(
                    label = "Select Room",
                    items = rooms.map { it.name },
                    selectedValue = rooms.find { it.id == targetId }?.name ?: "",
                    onItemSelected = { index -> 
                        val selectedRoomId = rooms[index].id
                        viewModel.onRoomSelected(selectedRoomId)
                    }
                )

                // Show room info if selected
                selectedRoom?.let { room ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = PrimaryBlue
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Text(
                                text = "Room: ${room.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Devices: ${room.deviceCount}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (room.spectralData?.hasSpectralData() == true) {
                                Text(
                                    text = "SPD Data: Available (${availablePresets.size} presets)",
                                    style = MaterialTheme.typography.bodySmall,

                                )
                            } else {
                                Text(
                                    text = "SPD Data: Not available",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }

                // Time Picker
                Text(
                    text = "Schedule Time",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                TimePickerField(time = time, onTimeSelected = { viewModel.time.value = it })

                // Routine Type Selection
                Text(
                    text = "Routine Type",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Switch(
                        checked = devicePower,
                        onCheckedChange = { 
                            viewModel.devicePower.value = it
                            // Clear preset selection when switching to OFF
                            if (!it) {
                                viewModel.onPresetSelected(null)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (devicePower) "Apply Preset (Turn ON + Set Spectrum)" else "Turn devices OFF",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                // Spectrum Preset Selection (only show if device power is ON)
                if (devicePower) {
                    Text(
                        text = "Spectrum Preset",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    
                    if (availablePresets.isNotEmpty()) {
                        Dropdown(
                            label = "Select Spectrum Preset (Required)",
                            items = availablePresets.map { it.name },
                            selectedValue = availablePresets.find { it.id == selectedPresetId }?.name ?: "Select a preset...",
                            onItemSelected = { index ->
                                val preset = availablePresets[index]
                                viewModel.onPresetSelected(preset.id)
                            }
                        )
                        
                        // Show preset details if one is selected
                        selectedPresetId?.let { presetId ->
                            availablePresets.find { it.id == presetId }?.let { preset ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(12.dp)
                                    ) {
                                        Text(
                                            text = "Preset: ${preset.name}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        preset.description?.let { desc ->
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Text(
                                            text = "Slider values: ${preset.sliderValues.size} configured",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = "No spectrum presets available for this room.\nPlease upload and configure SPD data first.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(12.dp),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Day Selector
                Text(
                    text = "Repeat on",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                DaySelector(selectedDays = days, onDaySelected = { day ->
                    val currentDays = days.toMutableList()
                    if (currentDays.contains(day)) {
                        currentDays.remove(day)
                    } else {
                        currentDays.add(day)
                    }
                    viewModel.days.value = currentDays
                })

                Spacer(modifier = Modifier.height(16.dp))

                // Error Message Display
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = { viewModel.clearError() }
                            ) {
                                Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Validation and Save Button
                val isFormValid = routineName.isNotBlank() && 
                                 targetId.isNotBlank() && 
                                 days.isNotEmpty() &&
                                 (!devicePower || (devicePower && selectedPresetId != null)) &&
                                 !isLoading

                if (!isFormValid) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            text = "Please complete all required fields:\n" +
                                   "${if (routineName.isBlank()) "• Routine name is required\n" else ""}" +
                                   "${if (targetId.isBlank()) "• Room selection is required\n" else ""}" +
                                   "${if (days.isEmpty()) "• At least one day must be selected\n" else ""}" +
                                   "${if (devicePower && selectedPresetId == null) "• Preset selection is required for ON routines" else ""}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                Button(
                    onClick = {
                        viewModel.saveRoutine()
                    },
                    enabled = isFormValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (isLoading) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Text(
                                text = "Saving...",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else {
                        Text(
                            text = "Save Routine",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.height(110.dp))
            }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Dropdown(label: String, items: List<String>, selectedValue: String, onItemSelected: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedValue,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEachIndexed { index, text ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        onItemSelected(index)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun TimePickerField(time: String, onTimeSelected: (String) -> Unit) {
    val context = LocalContext.current
    
    // Parse current time or use current system time as default
    val timeParts = time.split(":")
    val defaultHour = if (timeParts.size == 2) timeParts[0].toIntOrNull() ?: 12 else 12
    val defaultMinute = if (timeParts.size == 2) timeParts[1].toIntOrNull() ?: 0 else 0

    val timePickerDialog = remember {
        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                onTimeSelected(String.format("%02d:%02d", selectedHour, selectedMinute))
            }, 
            defaultHour, 
            defaultMinute, 
            true
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { timePickerDialog.show() }
    ) {
        OutlinedTextField(
            value = time,
            onValueChange = {},
            label = { Text("Time") },
            readOnly = true,
            enabled = false,
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurface,
            ),
            trailingIcon = {
                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = "Select time",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaySelector(selectedDays: List<String>, onDaySelected: (String) -> Unit) {
    val days = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")
    val dayLabels = listOf("M", "T", "W", "T", "F", "S", "S")
    
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            days.forEachIndexed { index, day ->
                FilterChip(
                    selected = selectedDays.contains(day),
                    onClick = { onDaySelected(day) },
                    label = { 
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayLabels[index],
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                color = if (selectedDays.contains(day)) Color.White else PrimaryBlue,
                                textAlign = TextAlign.Center
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryBlue,
                        selectedLabelColor = Color.White,
                        containerColor = Color.White,
                        labelColor = PrimaryBlue
                    ),
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier
                        .size(48.dp)
                )
            }
        }
        
        // Show selected days info
        if (selectedDays.isNotEmpty()) {
            Text(
                text = "Selected: ${selectedDays.size} day${if (selectedDays.size > 1) "s" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
} 