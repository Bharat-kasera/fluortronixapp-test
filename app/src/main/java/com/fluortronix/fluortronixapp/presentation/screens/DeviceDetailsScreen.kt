package com.fluortronix.fluortronixapp.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fluortronix.fluortronixapp.data.models.Device
import com.fluortronix.fluortronixapp.presentation.viewmodels.DeviceDetailsViewModel
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.fluortronix.fluortronixapp.R
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.border
import com.airbnb.lottie.compose.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetailsScreen(
    deviceId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DeviceDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Initialize viewmodel with device ID
    LaunchedEffect(deviceId) {
        viewModel.initialize(deviceId)
    }

    // Handle error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(
                message = error,
                actionLabel = "Dismiss"
            )
            viewModel.clearError()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image
//        Image(
//            painter = painterResource(id = R.drawable.devicescreenbgimg),
//            contentDescription = "Background",
//            modifier = Modifier.fillMaxSize(),
//            contentScale = androidx.compose.ui.layout.ContentScale.Crop
//        )

        Scaffold(
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
            containerColor = Color.Transparent // Make scaffold background transparent
        ) { paddingValues ->
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = PrimaryBlue)
                        Text(
                            text = "Loading device information...",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Device Image with Device Info
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Device Image on the left
                            DeviceImageDisplay(
                                deviceImage = uiState.deviceImage,
                                isLoadingImage = uiState.isLoadingImage,
                                imageError = uiState.imageError,
                                onRefreshImage = { viewModel.refreshDeviceImage() }
                            )
                            
                            // Device name and room info on the right
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 16.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.End
                            ) {
                                uiState.device?.let { device ->
                                    Text(
                                        text = device.name,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue,
                                        textAlign = TextAlign.End
                                    )
                                    
                                    device.roomName?.let { roomName ->
                                        Text(
                                            text = roomName,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.Gray,
                                            textAlign = TextAlign.End,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                } ?: run {
                                    Text(
                                        text = "Loading...",
                                        fontSize = 16.sp,
                                        color = Color.Gray,
                                        textAlign = TextAlign.End
                                    )
                                }
                            }
                        }
                    }

                    // Device information card
                    item {
                        DeviceInfoCard(
                            device = uiState.device,
                            onNavigateBack = onNavigateBack,
                            isBlinking = uiState.isBlinking,
                            onBlinkDevice = { viewModel.blinkDevice() }
                        )
                    }

                    // Device management actions
                    uiState.device?.let { device ->
                        item {
                            DeviceManagementCard(
                                device = device,
                                onDeleteDevice = { viewModel.showDeleteConfirmation() },
                                onChangeRoom = { viewModel.showRoomSelection() }
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
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirmation) {
        DeleteDeviceDialog(
            deviceName = uiState.device?.name ?: "Unknown Device",
            onConfirm = {
                viewModel.deleteDevice { onNavigateBack() }
            },
            onDismiss = { viewModel.hideDeleteConfirmation() }
        )
    }

    // Room selection dialog
    if (uiState.showRoomSelection) {
        RoomSelectionDialog(
            deviceName = uiState.device?.name ?: "Unknown Device",
            availableRooms = uiState.availableRooms,
            onRoomSelected = { roomId ->
                viewModel.changeDeviceRoom(roomId)
            },
            onDismiss = { viewModel.hideRoomSelection() }
        )
    }
}

@Composable
private fun DeviceInfoCard(
    device: Device?,
    onNavigateBack: () -> Unit,
    isBlinking: Boolean = false,
    onBlinkDevice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(236.dp)
    ) {
        // Background image
        Image(
            painter = painterResource(id = R.drawable.rectangle2),
            contentDescription = "Background",
            modifier = Modifier
                .fillMaxSize()
                .clip(shape = RoundedCornerShape(30.dp))
        )

        // Blink button (left side)
        if (device?.ipAddress != null && device.sliderNames.isNotEmpty()) {
            Button(
                onClick = onBlinkDevice,
                enabled = !isBlinking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF21D3D5),
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(50.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .height(46.dp)
                    .offset(x = (-55).dp, y = 3.dp)
            ) {
                if (isBlinking) {
                    Text("Blinking...")
                } else {
                    Text("Blink")
                }
            }
        }

        // Back button
        IconButton(
            onClick = onNavigateBack,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-10).dp, y = 10.dp)
                .size(30.dp)
                .background(
                    color = Color(0xFF21D3D5),
                    shape = RoundedCornerShape(50)
                )
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.Black
            )
        }

        // Device Information label with glassmorphism effect
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 10.dp, y = 10.dp)
                .width(160.dp)
                .height(44.dp)
                .clip(shape = RoundedCornerShape(50.dp))
                .background(color = Color.White.copy(alpha = 0.1f))
                .border(
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(50.dp)
                )
                .padding(all = 10.dp)
        ) {
            Text(
                text = "Device Information",
                color = Color.White,
                textAlign = TextAlign.Center,
                style = TextStyle(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }

        // Device information content
        Column(
            modifier = Modifier
                .align(alignment = Alignment.TopStart)
                .offset(x = 20.dp, y = 64.dp)
                .fillMaxWidth()
                .padding(end = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            if (device != null) {
                DeviceInfoRow("Device Name:", device.name)
                DeviceInfoRow("Model:", device.deviceModel ?: "Unknown")
                DeviceInfoRow("IP Address:", device.ipAddress ?: "Not available")
                DeviceInfoRow("Status:", if (device.isOnline) "Online" else "Offline")

                if (device.roomName != null) {
                    DeviceInfoRow("Room:", device.roomName)
                }
            } else {
                Text(
                    text = "Device information not available",
                    fontSize = 14.sp,
                    color = Color.White,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            fontSize = 14.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun DeviceManagementCard(
    device: Device,
    onDeleteDevice: () -> Unit,
    onChangeRoom: () -> Unit,
    modifier: Modifier = Modifier
) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
//                .padding(10.dp)
        ) {
            // Row for Change Room and Delete buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Change room button
                Button(
                    onClick = onChangeRoom,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF345AFA),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(
                        painter= painterResource(R.drawable.roomicon),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (device.roomName != null) "Change" else "Assign",
                        fontSize = 16.sp
                    )
                }

                // Delete button
                Button(
                    onClick = onDeleteDevice,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDB0E10),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(50.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Delete",
                        fontSize = 16.sp
                    )
                }
            }
        }

}

@Composable
private fun DeleteDeviceDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Device",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete \"$deviceName\"?\n\nThis action cannot be undone and will remove all device data from the app.",
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE57373),
                    contentColor = Color.White
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFFFFFEFE)
    )
}

@Composable
private fun RoomSelectionDialog(
    deviceName: String,
    availableRooms: List<com.fluortronix.fluortronixapp.data.models.Room>,
    onRoomSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Change Room",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                if (availableRooms.isEmpty()) {
                    Text(
                        text = "No compatible rooms available for \"$deviceName\".\n\nCreate a new room or check device compatibility.",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(availableRooms) { room ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onRoomSelected(room.id) },
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFF8F9FA)
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = 2.dp
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = room.name,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${room.deviceCount} devices",
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
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = Color(0xFFFFFEFE)
    )
}

@Composable
private fun DeviceImageDisplay(
    deviceImage: ImageBitmap?,
    isLoadingImage: Boolean,
    imageError: String?,
    onRefreshImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    // ========== ADAPTIVE FRAME CUSTOMIZATION OPTIONS ==========
    val adaptiveModifier = remember(deviceImage) {
        if (deviceImage != null) {
            val imageAspectRatio = deviceImage.width.toFloat() / deviceImage.height.toFloat()
            
            // OPTION 1: FIXED WIDTH, ADAPTIVE HEIGHT (Current)
            val frameWidth = 120.dp  // ← ADJUST: Base width (80dp-150dp recommended)
            val frameHeight = frameWidth / imageAspectRatio
            val clampedHeight = frameHeight.coerceIn(80.dp, 160.dp)  // ← ADJUST: Height limits
            
            // OPTION 2: COMPLETELY FLEXIBLE (Uncomment to use)
            // val maxSize = 140.dp
            // val minSize = 80.dp
            // if (imageAspectRatio > 1) {
            //     // Wide image: fix width, adjust height
            //     modifier.size(width = maxSize, height = (maxSize / imageAspectRatio).coerceAtLeast(minSize))
            // } else {
            //     // Tall image: fix height, adjust width
            //     modifier.size(width = (maxSize * imageAspectRatio).coerceAtLeast(minSize), height = maxSize)
            // }
            
            // OPTION 3: SQUARE WITH MAXIMUM SIZE (Uncomment to use)
            // val squareSize = minOf(120.dp, maxOf(80.dp, 100.dp / maxOf(imageAspectRatio, 1f/imageAspectRatio)))
            // modifier.size(squareSize)
            
            // CURRENT CHOICE: Using Option 1
            modifier.size(
                width = frameWidth,
                height = clampedHeight
            )
        } else {
            // Default square size for loading/error states
            modifier.size(120.dp)  // ← ADJUST: Match your chosen base size above
        }
    }
    
    Box(
        modifier = adaptiveModifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                color = PrimaryBlue.copy(alpha = 0.1f),
                shape = RoundedCornerShape(16.dp)
            )
            .border(
                border = BorderStroke(2.dp, PrimaryBlue.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            // Loading state
            isLoadingImage -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        color = PrimaryBlue,
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Loading",
                        fontSize = 10.sp,
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            // Image loaded successfully
            deviceImage != null -> {
                Image(
                    bitmap = deviceImage,
                    contentDescription = "Device Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp)), // Slightly smaller radius for inner content
                    contentScale = ContentScale.Fit // Options:
                    // ContentScale.Fit       - Fits entire image, may have padding (current)
                    // ContentScale.Crop      - Fills frame, crops excess (original)
                    // ContentScale.FillBounds - Stretches to fill exactly
                    // ContentScale.Inside    - Like Fit but never scales up
                    // ContentScale.FillWidth - Fits width, may crop height
                    // ContentScale.FillHeight - Fits height, may crop width
                )
            }
            
            // Error state with retry option
            imageError != null -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Image Error",
                        tint = Color(0xFFE57373),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Image\nFailed",
                        fontSize = 9.sp,
                        color = Color(0xFFE57373),
                        textAlign = TextAlign.Center,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(
                        onClick = onRefreshImage,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Retry",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            
            // Fallback to Lottie animation
            else -> {
                val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.device))
                val progress by animateLottieCompositionAsState(
                    composition = composition,
                    iterations = LottieConstants.IterateForever
                )
                LottieAnimation(
                    composition = composition,
                    progress = { progress },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        

    }
}