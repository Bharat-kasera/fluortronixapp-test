package com.fluortronix.fluortronixapp.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.fluortronix.fluortronixapp.R
import com.fluortronix.fluortronixapp.presentation.components.*
import com.fluortronix.fluortronixapp.presentation.viewmodels.RoomsViewModel
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomsScreen(
    navController: NavController,
    onRoomClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RoomsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Dialog states
    var showAddRoomDialog by remember { mutableStateOf(false) }
    var showEditRoomDialog by remember { mutableStateOf(false) }
    var selectedRoomForEdit by remember { mutableStateOf<com.fluortronix.fluortronixapp.data.models.Room?>(null) }
    
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
                painter = painterResource(id = R.drawable.roomheaderimg),
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
                        text = "Room Management",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Organize your devices",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }

        // Snackbar Host
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
                    // Rooms section
                    item {
                        SectionHeader(
                            title = "My Rooms",
                            subtitle = "${uiState.rooms.size} room${if (uiState.rooms.size != 1) "s" else ""}",
                            onActionClick = { showAddRoomDialog = true },
                            actionText = "Add Room"
                        )
                    }

                    if (uiState.rooms.isEmpty()) {
                        item {
                            EmptyRoomsCard(
                                onCreateRoomClick = { showAddRoomDialog = true }
                            )
                        }
                    } else {
                        items(uiState.rooms) { room ->
                            RoomCard(
                                room = room,
                                roomStats = uiState.roomStats[room.id],
                                onClick = { onRoomClick(room.id) },
                                onPowerToggle = { viewModel.toggleRoomPower(room.id) },
                                onEditClick = {
                                    selectedRoomForEdit = room
                                    showEditRoomDialog = true
                                }
                            )
                        }
                    }

                    // Unassigned devices section
                    if (uiState.unassignedDevices.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionHeader(
                                title = "Unassigned Devices",
                                subtitle = "${uiState.unassignedDevices.size} device${if (uiState.unassignedDevices.size != 1) "s" else ""}"
                            )
                        }

                        items(uiState.unassignedDevices) { device ->
                            UnassignedDeviceCard(
                                device = device,
                                availableRooms = uiState.rooms,
                                onAssignToRoom = { deviceId, roomId ->
                                    viewModel.assignDeviceToRoom(deviceId, roomId)
                                }
                            )
                        }
                    }

                    // Bottom padding for navigation bar
                    item {
                        Spacer(modifier = Modifier.height(110.dp))
                    }

                }
            }
        }
        
        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.padding(bottom = 40.dp),
            snackbar = { snackbarData ->
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = Color(0xFFE57373),
                    contentColor = Color.White
                )
            }
        )
    }
    
    // Add Room Dialog
    AddRoomDialog(
        isVisible = showAddRoomDialog,
        availableDevices = uiState.unassignedDevices,
        onDismiss = { showAddRoomDialog = false },
        onCreateRoom = { roomData ->
            val success = viewModel.createRoom(roomData)
            if (success) {
                showAddRoomDialog = false
            }
        },
        isCreating = uiState.isCreatingRoom,
        error = if (showAddRoomDialog) uiState.error else null
    )
    
    // Edit Room Dialog
    EditRoomDialog(
        isVisible = showEditRoomDialog,
        room = selectedRoomForEdit,
        onDismiss = { 
            showEditRoomDialog = false
            selectedRoomForEdit = null
        },
        onUpdateRoom = { roomId, newName ->
            val success = viewModel.updateRoom(roomId, newName)
            if (success) {
                showEditRoomDialog = false
                selectedRoomForEdit = null
            }
        },
        onDeleteRoom = { roomId ->
            viewModel.deleteRoom(roomId)
            showEditRoomDialog = false
            selectedRoomForEdit = null
        },
        isUpdating = uiState.isEditingRoom,
        error = if (showEditRoomDialog) uiState.error else null
    )
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionText: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            
            subtitle?.let {
                Text(
                    text = it,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        
        if (onActionClick != null && actionText != null) {
            TextButton(
                onClick = onActionClick,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color.White,
                ),
                modifier = Modifier
                    .background(
                        color = Color(0xFFF0F4F9),
                        shape = RoundedCornerShape(50.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 2.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(26.dp)
                        .background(
                            color = Color(0xFF345AFA),
                            shape = CircleShape
                        )
                        .padding(6.dp),
                    tint = Color.White,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = actionText,
                    color = Color.Black,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun EmptyRoomsCard(
    onCreateRoomClick: () -> Unit
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
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        color = PrimaryBlue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(16.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(32.dp)
                )
            }
            
            Text(
                text = "No Rooms Yet",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            
            Text(
                text = "Create your first room to organize your devices. Group devices by location or purpose for easier management.",
                fontSize = 14.sp,
                color = Color.Gray,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
            
            Button(
                onClick = onCreateRoomClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryBlue
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create First Room")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnassignedDeviceCard(
    device: com.fluortronix.fluortronixapp.data.models.Device,
    availableRooms: List<com.fluortronix.fluortronixapp.data.models.Room>,
    onAssignToRoom: (String, String) -> Unit
) {
    var showRoomSelection by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFE)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DeviceCard(
                device = device,
                modifier = Modifier.weight(1f)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            OutlinedButton(
                onClick = { showRoomSelection = true },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PrimaryBlue
                ),
                shape = RoundedCornerShape(8.dp),

            ) {
                Text("Assign", fontSize = 12.sp)
            }
        }
    }
    
    // Room selection dropdown
    if (showRoomSelection) {
        RoomSelectionDialog(
            device = device,
            availableRooms = availableRooms,
            onDismiss = { showRoomSelection = false },
            onRoomSelected = { roomId ->
                onAssignToRoom(device.id, roomId)
                showRoomSelection = false
            }
        )
    }
}

@Composable
private fun RoomSelectionDialog(
    device: com.fluortronix.fluortronixapp.data.models.Device,
    availableRooms: List<com.fluortronix.fluortronixapp.data.models.Room>,
    onDismiss: () -> Unit,
    onRoomSelected: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign ${device.name}") },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val compatibleRooms = availableRooms.filter { room ->
                    room.canAddDeviceModel(device.deviceModel)
                }
                
                if (compatibleRooms.isEmpty()) {
                    item {
                        Text(
                            text = "No compatible rooms available. Device model '${device.deviceModel}' cannot be mixed with existing devices in any room.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    items(compatibleRooms) { room ->
                        CompactRoomCard(
                            room = room,
                            onClick = { onRoomSelected(room.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RoomsSummaryCard(
    totalRooms: Int,
    totalDevices: Int,
    assignedDevices: Int,
    roomStats: Map<String, com.fluortronix.fluortronixapp.data.models.RoomStats>
) {
    val onlineDevices = roomStats.values.sumOf { it.onlineDevices }
    val devicesOn = roomStats.values.sumOf { it.devicesOn }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Summary",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                SummaryItem("Rooms", totalRooms.toString(), PrimaryBlue)
                SummaryItem("Total Devices", totalDevices.toString(), Color.Gray)
                SummaryItem("Assigned", assignedDevices.toString(), Color(0xFF4CAF50))
                SummaryItem("Online", onlineDevices.toString(), Color(0xFF2196F3))
            }
        }
    }
}

@Composable
private fun SummaryItem(
    label: String,
    value: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
} 