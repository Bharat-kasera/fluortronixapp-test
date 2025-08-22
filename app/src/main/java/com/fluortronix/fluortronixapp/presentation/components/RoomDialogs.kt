package com.fluortronix.fluortronixapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.fluortronix.fluortronixapp.data.models.Device
import com.fluortronix.fluortronixapp.data.models.Room
import com.fluortronix.fluortronixapp.data.models.RoomCreationData
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue

@Composable
fun AddRoomDialog(
    isVisible: Boolean,
    availableDevices: List<Device>,
    onDismiss: () -> Unit,
    onCreateRoom: (RoomCreationData) -> Unit,
    isCreating: Boolean = false,
    error: String? = null
) {
    if (isVisible) {
        Dialog(onDismissRequest = onDismiss) {
            AddRoomDialogContent(
                availableDevices = availableDevices,
                onDismiss = onDismiss,
                onCreateRoom = onCreateRoom,
                isCreating = isCreating,
                error = error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRoomDialogContent(
    availableDevices: List<Device>,
    onDismiss: () -> Unit,
    onCreateRoom: (RoomCreationData) -> Unit,
    isCreating: Boolean,
    error: String?
) {
    var roomName by remember { mutableStateOf("") }
    var selectedDevices by remember { mutableStateOf<Set<String>>(emptySet()) }
    var nameError by remember { mutableStateOf<String?>(null) }

    // Validate room name
    fun validateName(): Boolean {
        nameError = when {
            roomName.isBlank() -> "Room name cannot be empty"
            roomName.length < 2 -> "Room name must be at least 2 characters"
            roomName.length > 50 -> "Room name cannot exceed 50 characters"
            else -> null
        }
        return nameError == null
    }

    // Get device model constraint
    val selectedDeviceModels = availableDevices
        .filter { it.id in selectedDevices }
        .mapNotNull { it.deviceModel }
        .distinct()

    val hasModelConflict = selectedDeviceModels.size > 1

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Create New Room",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                IconButton(
                    onClick = onDismiss,
                    enabled = !isCreating
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Gray
                    )
                }
            }

            // Room name input
            OutlinedTextField(
                value = roomName,
                onValueChange = {
                    roomName = it
                    if (nameError != null) validateName()
                },
                label = { Text("Room Name") },
                placeholder = { Text("e.g., Living Room, Bedroom") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isCreating,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Next
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryBlue,
                    focusedLabelColor = PrimaryBlue
                )
            )

            // Device selection section
            if (availableDevices.isNotEmpty()) {
                Text(
                    text = "Assign Devices (Optional)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )

                // Model conflict warning
                if (hasModelConflict) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFFFF3E0)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFFF9800),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Devices with different models cannot be in the same room",
                                fontSize = 12.sp,
                                color = Color(0xFF795548)
                            )
                        }
                    }
                }

                // Device list
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(availableDevices) { device ->
                            DeviceSelectionItem(
                                device = device,
                                isSelected = device.id in selectedDevices,
                                isEnabled = !isCreating && !hasModelConflict ||
                                           selectedDeviceModels.isEmpty() ||
                                           device.deviceModel in selectedDeviceModels,
                                onSelectionChange = { isSelected ->
                                    selectedDevices = if (isSelected) {
                                        selectedDevices + device.id
                                    } else {
                                        selectedDevices - device.id
                                    }
                                }
                            )
                        }
                    }
                }

                if (selectedDevices.isNotEmpty()) {
                    Text(
                        text = "${selectedDevices.size} device(s) selected",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            } else {
                Text(
                    text = "No available devices to assign",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(8.dp)
                )
            }

            // Error message
            error?.let { errorMsg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFEBEE)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = Color(0xFFE57373),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = errorMsg,
                            fontSize = 12.sp,
                            color = Color(0xFFD32F2F)
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)
            ) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !isCreating
                ) {
                    Text("Cancel", color = Color.Gray)
                }

                Button(
                    onClick = {
                        if (validateName() && !hasModelConflict) {
                            val roomData = RoomCreationData(
                                name = roomName,
                                deviceIds = selectedDevices.toList(),
                                allowedDeviceModel = selectedDeviceModels.firstOrNull()
                            )
                            onCreateRoom(roomData)
                        }
                    },
                    enabled = !isCreating && roomName.isNotBlank() && !hasModelConflict,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue
                    )
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Create Room")
                }
            }
        }
    }
}

@Composable
fun EditRoomDialog(
    isVisible: Boolean,
    room: Room?,
    onDismiss: () -> Unit,
    onUpdateRoom: (String, String) -> Unit,
    onDeleteRoom: (String) -> Unit,
    isUpdating: Boolean = false,
    error: String? = null
) {
    if (isVisible && room != null) {
        Dialog(onDismissRequest = onDismiss) {
            EditRoomDialogContent(
                room = room,
                onDismiss = onDismiss,
                onUpdateRoom = onUpdateRoom,
                onDeleteRoom = onDeleteRoom,
                isUpdating = isUpdating,
                error = error
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRoomDialogContent(
    room: Room,
    onDismiss: () -> Unit,
    onUpdateRoom: (String, String) -> Unit,
    onDeleteRoom: (String) -> Unit,
    isUpdating: Boolean,
    error: String?
) {
    var roomName by remember { mutableStateOf(room.name) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    // Validate room name
    fun validateName(): Boolean {
        nameError = when {
            roomName.isBlank() -> "Room name cannot be empty"
            roomName.length < 2 -> "Room name must be at least 2 characters"
            roomName.length > 50 -> "Room name cannot exceed 50 characters"
            else -> null
        }
        return nameError == null
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFFFEFE),
        tonalElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 550.dp) // Prevent overflow on small screens
        ) {
            // Header with close button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit Room",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )

                IconButton(
                    onClick = onDismiss,
                    enabled = !isUpdating
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.Gray
                    )
                }
            }

            // Scrollable content area
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f), // Take available space
                contentPadding = PaddingValues(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Room name input
                item {
                    OutlinedTextField(
                        value = roomName,
                        onValueChange = {
                            roomName = it
                            if (nameError != null) validateName()
                        },
                        label = { Text("Room Name") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isUpdating,
                        isError = nameError != null,
                        supportingText = nameError?.let { 
                            { 
                                Text(
                                    text = it, 
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp
                                ) 
                            } 
                        },
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryBlue,
                            focusedLabelColor = PrimaryBlue
                        )
                    )
                }

                // Room information section
                item {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Room Information",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF8F9FA)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CompactInfoRow(
                                    label = "Devices", 
                                    value = "${room.deviceCount}",

                                )
                                CompactInfoRow(
                                    label = "Model", 
                                    value = room.getDeviceModelDisplay(),

                                )
                                CompactInfoRow(
                                    label = "Created", 
                                    value = java.text.SimpleDateFormat("MMM dd, yyyy",
                                        java.util.Locale.getDefault()).format(java.util.Date(room.createdAt)),

                                )
                            }
                        }
                    }
                }

                // Error message
                error?.let { errorMsg ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFFEBEE)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFE57373),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = errorMsg,
                                    fontSize = 14.sp,
                                    color = Color(0xFFD32F2F),
                                    lineHeight = 20.sp
                                )
                            }
                        }
                    }
                }

            }

            // Fixed bottom action buttons
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFFEFE),
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Update button
                    Button(
                        onClick = {
                            if (validateName()) {
                                onUpdateRoom(room.id, roomName)
                            }
                        },
                        enabled = !isUpdating && roomName.isNotBlank() && roomName != room.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Updating...",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "Update Room",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Bottom row with Cancel and Delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Cancel button
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = !isUpdating,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.Gray
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Cancel",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.Black
                            )
                        }

                        // Delete button
                        Button(
                            onClick = { showDeleteConfirmation = true },
                            enabled = !isUpdating,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(Color(0xFFDB0E10)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "Delete",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White


                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Room") },
            text = {
                Text("Are you sure you want to delete \"${room.name}\"? All devices will be unassigned from this room.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRoom(room.id)
                        showDeleteConfirmation = false
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE57373)
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            },
            containerColor = Color(0xFFFFFEFE)
        )
    }
}

@Composable
private fun DeviceSelectionItem(
    device: Device,
    isSelected: Boolean,
    isEnabled: Boolean,
    onSelectionChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                enabled = isEnabled,
                role = Role.Checkbox,
                onClick = { onSelectionChange(!isSelected) }
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryBlue.copy(alpha = 0.1f) else Color.White
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = onSelectionChange,
                enabled = isEnabled,
                colors = CheckboxDefaults.colors(
                    checkedColor = PrimaryBlue
                )
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isEnabled) Color.Black else Color.Gray
                )

                Text(
                    text = device.deviceModel ?: "Unknown Model",
                    fontSize = 12.sp,
                    color = if (isEnabled) Color.Gray else Color.LightGray
                )
            }

            // Online indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (device.isOnline) Color(0xFF4CAF50) else Color.Gray,
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
        Text(
            text = value,
            fontSize = 12.sp,
            color = Color.Black,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CompactInfoRow(
    label: String, 
    value: String, 
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.Black,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ESPDeviceSetupDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    onContinue: () -> Unit
) {
    if (isVisible) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Icon
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(48.dp)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Title
                    Text(
                        text = "Device Setup",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Subtitle
                    Text(
                        text = "Please make sure your device is ready",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Instructions
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        InstructionItem(
                            number = "1",
                            text = "Make sure your device is powered on"
                        )
                        InstructionItem(
                            number = "2",
                            text = "Ensure the device is in setup mode (if applicable)"
                        )
                        InstructionItem(
                            number = "3",
                            text = "The device should create its own Wi-Fi network"
                        )
                        InstructionItem(
                            number = "4",
                            text = "Keep your phone's Wi-Fi and location services enabled"
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.Gray
                            )
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = onContinue,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue
                            )
                        ) {
                            Text("Continue")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructionItem(number: String, text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            modifier = Modifier.size(24.dp),
            shape = RoundedCornerShape(12.dp),
            color = PrimaryBlue
        ) {
            Box(
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    fontSize = 12.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )
    }
}