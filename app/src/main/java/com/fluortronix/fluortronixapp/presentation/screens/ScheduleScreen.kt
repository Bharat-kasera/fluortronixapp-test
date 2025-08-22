package com.fluortronix.fluortronixapp.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.fluortronix.fluortronixapp.data.models.Routine
import com.fluortronix.fluortronixapp.data.models.Room
import com.fluortronix.fluortronixapp.presentation.viewmodels.ScheduleViewModel
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue
import com.fluortronix.fluortronixapp.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    navController: NavController,
    viewModel: ScheduleViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Handle error messages
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Dismiss"
            )
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Profile Header with Background Image
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
                    onClick = { 
                        navController.navigate("home") {
                            popUpTo("home") { inclusive = false }
                        }
                    },
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
                        text = "Schedule Manager",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    if (uiState.selectedRoom != null) {
                        Text(
                            text = "${uiState.selectedRoom?.name} • ${uiState.routinesForSelectedRoom.size}/${uiState.maxRoutinesPerRoom} routines",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    } else {
                        Text(
                            text = "Manage your routines",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (uiState.isSyncingToDevices) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White
                    )
                }
            }
        }

        // Main content with FloatingActionButton overlay
        Box(modifier = Modifier.fillMaxSize()) {
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Room selection section
                    item {
                        RoomSelectionCard(
                            rooms = uiState.rooms,
                            selectedRoom = uiState.selectedRoom,
                            onRoomSelected = { room ->
                                viewModel.selectRoom(room)
                            }
                        )
                    }
                    
                    // Selected room info and sync status
                    if (uiState.selectedRoom != null) {
                        item {
                            SelectedRoomInfoCard(
                                room = uiState.selectedRoom!!,
                                routineCount = uiState.routinesForSelectedRoom.size,
                                maxRoutines = uiState.maxRoutinesPerRoom,
                                deviceCount = uiState.devicesInSelectedRoom.size,
                                isSyncing = uiState.isSyncingToDevices,
                                syncResults = uiState.syncResults,
                                onSyncToDevices = {
                                    viewModel.syncRoutinesToESPDevices(uiState.selectedRoom!!.id)
                                },
                                onClearSyncResults = {
                                    viewModel.clearSyncResults()
                                }
                            )
                        }
                        
                        // Routines for selected room
                        if (uiState.routinesForSelectedRoom.isEmpty()) {
                            item {
                                EmptyRoutinesCard(
                                    roomName = uiState.selectedRoom!!.name,
                                    onAddRoutine = {
                                        navController.navigate("add_edit_routine?roomId=${uiState.selectedRoom!!.id}")
                                    }
                                )
                            }
                        } else {
                            items(uiState.routinesForSelectedRoom) { routine ->
                                RoutineCard(
                                    routine = routine,
                                    onEdit = { 
                                        navController.navigate("add_edit_routine/${routine.id}?roomId=${uiState.selectedRoom?.id}")
                                    },
                                    onDelete = { viewModel.deleteRoutine(routine) },
                                    onToggleEnabled = { viewModel.toggleRoutineEnabled(routine) }
                                )
                            }
                            
                            // Add routine prompt if not at max
                            if (viewModel.canAddMoreRoutines()) {
                                item {
                                    AddMoreRoutinesCard(
                                        remainingSlots = viewModel.getRemainingRoutineSlots(),
                                        onAddRoutine = {
                                            navController.navigate("add_edit_routine?roomId=${uiState.selectedRoom?.id}")
                                        }
                                    )
                                }
                            } else {
                                item {
                                    MaxRoutinesReachedCard()
                                }
                            }
                        }
                    } else {
                        // No room selected
                        item {
                            SelectRoomPromptCard()
                        }
                    }
                    
                    // Bottom spacing for navigation bar
                    item {
                        Spacer(modifier = Modifier.height(110.dp))
                    }
                }
            }
            

            
            // Snackbar Host
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 40.dp),
                snackbar = { snackbarData ->
                    Snackbar(
                        snackbarData = snackbarData,
                        containerColor = Color(0xFFE57373),
                        contentColor = Color.White
                    )
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSelectionCard(
    rooms: List<Room>,
    selectedRoom: Room?,
    onRoomSelected: (Room) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFE)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Select Room",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedRoom?.name ?: "Choose a room",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = "Dropdown"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    rooms.forEach { room ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = room.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${room.deviceCount} device${if (room.deviceCount != 1) "s" else ""}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            },
                            onClick = {
                                onRoomSelected(room)
                                expanded = false
                            }
                        )
                    }
                }
            }
            
            if (rooms.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No rooms available. Create a room first.",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
private fun SelectedRoomInfoCard(
    room: Room,
    routineCount: Int,
    maxRoutines: Int,
    deviceCount: Int,
    isSyncing: Boolean,
    syncResults: Map<String, String>,
    onSyncToDevices: () -> Unit,
    onClearSyncResults: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF345AFA)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = room.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "$routineCount/$maxRoutines routines • $deviceCount device${if (deviceCount != 1) "s" else ""}",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
            }
            
            // Sync results
            if (syncResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sync Results:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )
                    
                    IconButton(
                        onClick = onClearSyncResults,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear results",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                ) {
                    syncResults.forEach { (deviceId, result) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = deviceId.take(8) + "...",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = result,
                                fontSize = 12.sp,
                                color = if (result == "Success") Color(0xFFA5D6A7) else Color(0xFFE57373),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleEnabled: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (routine.isEnabled) Color(0xFFFFFEFE) else Color.Gray.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = routine.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (routine.isEnabled) Color.Black else Color.Gray
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = routine.getFormattedTime(),
                        fontSize = 14.sp,
                        color = if (routine.isEnabled) PrimaryBlue else Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Text(
                        text = "Days: ${routine.days.joinToString(", ")}",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    
                    if (routine.spectrumPresetName != null) {
                        Text(
                            text = "Preset: ${routine.spectrumPresetName}",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    Text(
                        text = "Power: ${if (routine.devicePower) "ON" else "OFF"}",
                        fontSize = 12.sp,
                        color = if (routine.devicePower) Color(0xFF4CAF50) else Color(0xFFE57373)
                    )
                }
                
//                Row {
//                    Switch(
//                        checked = routine.isEnabled,
//                        onCheckedChange = { onToggleEnabled() },
//                        colors = SwitchDefaults.colors(
//                            checkedThumbColor = PrimaryBlue,
//                            checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f)
//                        )
//                    )
//                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFE57373))
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun EmptyRoutinesCard(
    roomName: String,
    onAddRoutine: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "No routines",
                modifier = Modifier.size(48.dp),
                tint = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "No Routines Yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Create routines to automatically control devices in $roomName",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onAddRoutine,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create First Routine")
            }
        }
    }
}

@Composable
private fun AddMoreRoutinesCard(
    remainingSlots: Int,
    onAddRoutine: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Add More Routines",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black
                )
                Text(
                    text = "$remainingSlots slot${if (remainingSlots != 1) "s" else ""} remaining",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            
            OutlinedButton(
                onClick = onAddRoutine,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add")
            }
        }
    }
}

@Composable
private fun MaxRoutinesReachedCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = "Info",
                tint = Color(0xFFE65100),
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = "Maximum routines reached (6/6). Delete a routine to add another.",
                fontSize = 14.sp,
                color = Color(0xFFE65100)
            )
        }
    }
}

@Composable
private fun SelectRoomPromptCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painter = painterResource(R.drawable.roomicon),
                contentDescription = "Select room",
                modifier = Modifier.size(48.dp),
                tint = Color.Gray
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Select a Room",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Choose a room above to manage its routines and sync them to ESP devices",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
} 