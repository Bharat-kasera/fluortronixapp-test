package com.fluortronix.fluortronixapp.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.fluortronix.fluortronixapp.data.models.Device
import com.fluortronix.fluortronixapp.data.models.Room
import com.fluortronix.fluortronixapp.presentation.viewmodels.DeviceOnboardingViewModel

@Composable
fun AssignRoomScreen(
    navController: NavController,
    viewModel: DeviceOnboardingViewModel
) {
    val newDevice by viewModel.newDevice.collectAsState()
    val rooms by viewModel.rooms.collectAsState()
    var selectedRoomId by remember { mutableStateOf<String?>(null) }

    val compatibleRooms = rooms.filter { it.canAddDeviceModel(newDevice?.deviceModel) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        newDevice?.let { device ->
            DeviceDetailsCard(device)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("Select a room for the device:")
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(compatibleRooms) { room ->
                RoomItem(room, selectedRoomId == room.id) {
                    selectedRoomId = room.id
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(onClick = { /* TODO: Show create room dialog */ }) {
            Text("Create New Room")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                selectedRoomId?.let {
                    viewModel.assignDeviceToRoom(it)
                    navController.navigate("home") {
                        popUpTo("home") { inclusive = true }
                    }
                }
            },
            enabled = selectedRoomId != null
        ) {
            Text("Finish")
        }
    }
}

@Composable
fun DeviceDetailsCard(device: Device) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Device Details", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
            Text("Model: ${device.deviceModel}")
            Text("Firmware: ${device.firmwareVersion}")
            Text("IP Address: ${device.ipAddress}")
        }
    }
}

@Composable
fun RoomItem(room: Room, isSelected: Boolean, onRoomSelected: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onRoomSelected() }
            .padding(vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = room.name,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Text("✓")
            }
        }
    }
} 