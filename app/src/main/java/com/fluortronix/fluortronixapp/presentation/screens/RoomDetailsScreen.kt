package com.fluortronix.fluortronixapp.presentation.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fluortronix.fluortronixapp.data.models.Room
import com.fluortronix.fluortronixapp.data.models.RoomStats
import com.fluortronix.fluortronixapp.presentation.components.*
import com.fluortronix.fluortronixapp.presentation.viewmodels.RoomsViewModel
import com.fluortronix.fluortronixapp.presentation.viewmodels.SpectralViewModel
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue
import android.graphics.BlurMaskFilter
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomDetailsScreen(
    roomId: String,
    onNavigateBack: () -> Unit,
    onDeviceClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: RoomsViewModel = hiltViewModel(),
    spectralViewModel: SpectralViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val spectralUiState by spectralViewModel.uiState.collectAsState()

    // Find the room and its devices
    val room = uiState.rooms.find { it.id == roomId }
    val roomDevices = uiState.devices.filter { it.roomId == roomId }
    val roomStats = uiState.roomStats[roomId]

    // Dialog states
    var showEditRoomDialog by remember { mutableStateOf(false) }
    var showAssignDeviceDialog by remember { mutableStateOf(false) }

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

    // Handle spectral error messages
    LaunchedEffect(spectralUiState.error) {
        spectralUiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Dismiss"
            )
            spectralViewModel.clearError()
        }
    }

    // Load data when screen opens
    LaunchedEffect(roomId) {
        viewModel.loadData()
        room?.let { viewModel.selectRoom(it) }
        spectralViewModel.initializeWithRoom(roomId)
    }
    
    // Connect SpectralViewModel to room devices when devices are loaded
    LaunchedEffect(roomDevices) {
        if (roomDevices.isNotEmpty()) {
            roomDevices.forEach { device ->
                if (device.ipAddress != null) {
                    println("DEBUG: RoomDetailsScreen connecting device ${device.name} to SpectralViewModel")
                    spectralViewModel.connectToEspDevice(device)
                }
            }
        }
    }

    if (room == null) {
        // Room not found
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Room Not Found",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "This room may have been deleted",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Button(
                    onClick = onNavigateBack,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Go Back")
                }
            }
        }
        return
    }

    Scaffold(
        topBar = {
            RoomDetailsTopBar(
                room = room,
                onNavigateBack = onNavigateBack,
                onEditClick = { showEditRoomDialog = true },
                onRefresh = { viewModel.loadData() }
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding().padding(bottom = 40.dp), // Add padding for bottom nav
                snackbar = { snackbarData ->
                    Snackbar(
                        snackbarData = snackbarData,
                        containerColor = Color(0xFFE57373),
                        contentColor = Color.White
                    )
                }
            )
        },
        containerColor = Color.White
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = PrimaryBlue)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Room overview card
                item {
                    RoomOverviewCard(
                        room = room,
                        roomStats = roomStats,
                        onPowerToggle = { 
                            // Toggle room power state first
                            viewModel.toggleRoomPower(room.id)
                            
                            // Also toggle ESP devices if any are connected to spectral controls
                            if (spectralUiState.connectedDevices.isNotEmpty()) {
                                spectralViewModel.toggleEspDevicesPower(!room.isAllDevicesOn)
                            }
                        },
                        onAssignDevice = { showAssignDeviceDialog = true }
                    )
                }


                // Spectral controls section
                item {
                    SpectralControlsSection(
                        room = room,
                        spectralUiState = spectralUiState,
                        spectralViewModel = spectralViewModel,
                        onSliderChange = { sourceName: String, value: Float ->
                            spectralViewModel.updateSliderValue(sourceName, value)
                        },
                        onResetSliders = {
                            spectralViewModel.resetSlidersToInitial()
                        }
                    )
                }

                // Devices section
                item {
                    DevicesInRoomHeader(
                        deviceCount = roomDevices.size,
                        onAssignDevice = { showAssignDeviceDialog = true }
                    )
                }

                if (roomDevices.isEmpty()) {
                    item {
                        EmptyRoomDevicesCard(
                            roomName = room.name,
                            onAssignDevice = { showAssignDeviceDialog = true }
                        )
                    }
                } else {
                    items(roomDevices) { device ->
                        DeviceInRoomCard(
                            device = device,
                            onClick = { onDeviceClick(device.id) },
                            onRemoveFromRoom = {
                                viewModel.removeDeviceFromRoom(device.id)
                            }
                        )
                    }
                }

                // Bottom padding for navigation bar
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }

    // Edit Room Dialog
    EditRoomDialog(
        isVisible = showEditRoomDialog,
        room = room,
        onDismiss = { showEditRoomDialog = false },
        onUpdateRoom = { roomId, newName ->
            val success = viewModel.updateRoom(roomId, newName)
            if (success) {
                showEditRoomDialog = false
            }
        },
        onDeleteRoom = { roomId ->
            viewModel.deleteRoom(roomId)
            showEditRoomDialog = false
            onNavigateBack()
        },
        isUpdating = uiState.isEditingRoom,
        error = if (showEditRoomDialog) uiState.error else null
    )

    // Assign Device Dialog
    if (showAssignDeviceDialog) {
        AssignDeviceToRoomDialog(
            room = room,
            availableDevices = uiState.unassignedDevices,
            onDismiss = { showAssignDeviceDialog = false },
            onAssignDevice = { deviceId: String ->
                viewModel.assignDeviceToRoom(deviceId, room.id)
                showAssignDeviceDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomDetailsTopBar(
    room: Room,
    onNavigateBack: () -> Unit,
    onEditClick: () -> Unit,
    onRefresh: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = room.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    text = "${room.deviceCount} device${if (room.deviceCount != 1) "s" else ""} • ${room.getDeviceModelDisplay()}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.Black
                )
            }
        },
        actions = {
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = Color.Gray
                )
            }
            IconButton(onClick = onEditClick) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit Room",
                    tint = PrimaryBlue
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.White
        )
    )
}

@Composable
private fun RoomOverviewCard(
    room: Room,
    roomStats: RoomStats?,
    onPowerToggle: () -> Unit,
    onAssignDevice: () -> Unit
) {
    // Shadow parameters defined here for easy tweaking
    val borderRadius = 12.dp
    val shadowColor = Color.Black.copy(alpha = 0.2f)
    val blurRadius = 20.dp
    val offsetY = 6.dp
    val spread = 1.dp
    Card(
        modifier = Modifier
            .fillMaxWidth()
            // The entire shadow logic is now here, inside .drawBehind
            .drawBehind {
                this.drawIntoCanvas { canvas ->
                    val paint = Paint()
                    val frameworkPaint = paint.asFrameworkPaint()
                    frameworkPaint.maskFilter = (BlurMaskFilter(
                        blurRadius.toPx(),
                        BlurMaskFilter.Blur.NORMAL
                    ))
                    frameworkPaint.color = shadowColor.toArgb()

                    val spreadPixel = spread.toPx()
                    val leftPixel = (0f - spreadPixel)
                    val topPixel = (0f - spreadPixel) + offsetY.toPx()
                    val rightPixel = (this.size.width + spreadPixel)
                    val bottomPixel = (this.size.height + spreadPixel) + offsetY.toPx()

                    canvas.drawRoundRect(
                        left = leftPixel,
                        top = topPixel,
                        right = rightPixel,
                        bottom = bottomPixel,
                        radiusX = borderRadius.toPx(),
                        radiusY = borderRadius.toPx(),
                        paint
                    )
                }
            },

        colors = CardDefaults.cardColors(containerColor = Color(0xFF120E00)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Text(
                text = "Room Overview",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Info Items Section
            Column(
                verticalArrangement = Arrangement.spacedBy(4 .dp)


            ) {
                InfoItem("Device Model", room.getDeviceModelDisplay())
                InfoItem("Total Devices", room.deviceCount.toString())
                roomStats?.let { stats ->
                    InfoItem("Online", "${stats.onlineDevices}/${stats.totalDevices}")
                    InfoItem("Powered On", "${stats.devicesOn}/${stats.totalDevices}")
                }
            }

            // Action Buttons Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Power Toggle Button (only show if devices exist)
                if (room.deviceCount > 0) {
                    Button(
                        onClick = onPowerToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (room.isAllDevicesOn) {
                                Color(0xFFDB0E10)
                            } else {
                                PrimaryBlue
                            }
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = if (room.isAllDevicesOn) {
                                Icons.Default.PowerOff
                            } else {
                                Icons.Default.Power
                            },
                            contentDescription = if (room.isAllDevicesOn) {
                                "Turn off all devices"
                            } else {
                                "Turn on all devices"
                            },
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (room.isAllDevicesOn) "Turn Off All" else "Turn On All",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }

                // Assign Device Button
                OutlinedButton(
                    onClick = onAssignDevice,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White ,
                        containerColor = Color.Transparent
                    ),
                    border = BorderStroke(1.5.dp, Color.White),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text(
                        text = "Assign Device",
                        fontSize = 14.sp,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun SpectralControlsSection(
    room: Room?,
    spectralUiState: com.fluortronix.fluortronixapp.presentation.viewmodels.SpectralUiState,
    spectralViewModel: com.fluortronix.fluortronixapp.presentation.viewmodels.SpectralViewModel,
    onSliderChange: (String, Float) -> Unit,
    onResetSliders: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        // Show loader when automatically loading SPD data
        if (!spectralUiState.hasSpectralData && spectralUiState.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = PrimaryBlue,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Loading SPD...",
                        fontSize = 14.sp,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Spectral Controls and Chart
        if (spectralUiState.hasSpectralData) {
            // Spectral Power Distribution Chart
                SpectralPowerDistributionChart(
                    graphData = spectralUiState.graphData,
                    title = "SPD Result",
                    modifier = Modifier.padding(10.dp)
                )
//            Spacer(modifier = Modifier.height(16.dp))

            // Spectral Control Panel
            SpectralControlPanel(
                lightSources = spectralUiState.activeLightSources,
                sliderValues = spectralUiState.sliderValues,
                onSliderChange = onSliderChange,
                onResetSliders = onResetSliders,
                isEnabled = true,
                spectrumPresets = spectralUiState.spectrumPresets,
                masterSliderConfig = spectralUiState.masterSliderConfig,
                onSavePreset = { presetName ->
                    spectralViewModel.saveCurrentAsPreset(presetName)
                },
                onLoadPreset = { presetId ->
                    spectralViewModel.loadPreset(presetId)
                },
                onDeletePreset = { presetId ->
                    spectralViewModel.deletePreset(presetId)
                },
                onToggleMasterSlider = {
                    spectralViewModel.toggleMasterSliderMode()
                },
                onMasterSliderChange = { value ->
                    spectralViewModel.updateMasterSliderValue(value)
                },
                onToggleSliderFreeze = { sourceName ->
                    spectralViewModel.toggleSliderFreeze(sourceName)
                }
            )

        }
    }
}



@Composable
private fun DevicesInRoomHeader(
    deviceCount: Int,
    onAssignDevice: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "Devices in Room",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = "$deviceCount device${if (deviceCount != 1) "s" else ""}",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }

        TextButton(
            onClick = onAssignDevice,
            colors = ButtonDefaults.textButtonColors(contentColor = PrimaryBlue)
        ) {
            Text("+ Assign Device")
        }
    }
}

@Composable
private fun EmptyRoomDevicesCard(
    roomName: String,
    onAssignDevice: () -> Unit
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
            Text(
                text = "No Devices",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "This room doesn't have any devices yet",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAssignDevice,
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Assign Device")
            }
        }
    }
}

@Composable
private fun DeviceInRoomCard(
    device: com.fluortronix.fluortronixapp.data.models.Device,
    onClick: () -> Unit,
    onRemoveFromRoom: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF345AFA)),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    text = device.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White // High contrast (8.6:1), kept as is
                )
                Text(
                    text = "${device.deviceModel ?: "Unknown"} • ${if (device.isOnline) "Online" else "Offline"}",
                    fontSize = 14.sp,
                    color = if (device.isOnline) Color(0xFFA5D6A7) else Color(0xFFB0BEC5) // Accessible colors
                )
            }

            Row {
                // Status indicator
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (device.isOnline) Color(0xFFA5D6A7) else Color(0xFFB0BEC5),
                            shape = RoundedCornerShape(50)
                        )
                )
            }
        }
    }
}

@Composable
private fun RoomStatisticsCard(roomStats: RoomStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "Room Statistics",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                QuickStat("Total", roomStats.totalDevices.toString(), Color.Black)
                QuickStat("Online", roomStats.onlineDevices.toString(), Color(0xFF4CAF50))
                QuickStat("On", roomStats.devicesOn.toString(), PrimaryBlue)
                QuickStat("Off", roomStats.devicesOff.toString(), Color.Gray)
            }
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun QuickStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
private fun AutoDownloadCard(
    isLoading: Boolean,
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    color = PrimaryBlue,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Auto-Downloading",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            } else {
                Icon(
                    imageVector = if (onRetry != null) Icons.Default.Refresh else Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = if (onRetry != null) Color(0xFFE57373) else PrimaryBlue,
                    modifier = Modifier.size(32.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (onRetry != null) "Download Failed" else "Spectral Controls",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                fontSize = 14.sp,
                color = if (onRetry != null) Color(0xFFE57373) else Color.Gray,
                lineHeight = 20.sp
            )

            if (onRetry != null) {
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Retry Download")
                }
            }
        }
    }
}

@Composable
private fun AssignDeviceToRoomDialog(
    room: Room,
    availableDevices: List<com.fluortronix.fluortronixapp.data.models.Device>,
    onDismiss: () -> Unit,
    onAssignDevice: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Assign Device to ${room.name}",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (availableDevices.isEmpty()) {
                Column(
                ) {
                    Text(
                        text = "No compatible devices available",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All devices are already assigned to rooms or incompatible with this room's device model (${room.allowedDeviceModel ?: "None"}).",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn {
                    items(availableDevices) { device ->
                        Card(
                            onClick = { onAssignDevice(device.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = device.name,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.Black
                                )
                                Text(
                                    text = "${device.deviceModel ?: "Unknown Model"} • ${if (device.isOnline) "Online" else "Offline"}",
                                    fontSize = 14.sp,
                                    color = if (device.isOnline) Color(0xFF4CAF50) else Color.Gray
                                )
                                if (!device.ipAddress.isNullOrBlank()) {
                                    Text(
                                        text = "IP: ${device.ipAddress}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (availableDevices.isEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text("OK", color = PrimaryBlue)
                }
            }
        },
        dismissButton = {
            if (availableDevices.isNotEmpty()) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        },
        containerColor = Color(0xFFFFFEFE)  )
}