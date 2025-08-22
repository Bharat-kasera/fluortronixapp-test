package com.fluortronix.fluortronixapp.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.hilt.navigation.compose.hiltViewModel
import com.fluortronix.fluortronixapp.R
import com.fluortronix.fluortronixapp.data.models.RoomStats
import com.fluortronix.fluortronixapp.presentation.components.*
import com.fluortronix.fluortronixapp.presentation.viewmodels.HomeViewModel
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.drawBehind
@Composable
fun HomeScreen(
    onAddDeviceClick: () -> Unit = {},
    onCreateRoomClick: () -> Unit = {},
    onDeviceClick: (String) -> Unit = {},
    onRoomClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val devices by viewModel.devices.collectAsState()
    val rooms by viewModel.rooms.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Refresh data when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshData()
    }


    // Helper function to calculate room stats
    fun calculateRoomStats(roomId: String): RoomStats? {
        val roomDevices = devices.filter { it.roomId == roomId }
        if (roomDevices.isEmpty()) return null

        val onlineDevices = roomDevices.count { it.isOnline }
        val devicesOn = roomDevices.count { it.isOn }

        return RoomStats(
            totalDevices = roomDevices.size,
            onlineDevices = onlineDevices,
            offlineDevices = roomDevices.size - onlineDevices,
            devicesOn = devicesOn,
            devicesOff = roomDevices.size - devicesOn,
            deviceModel = roomDevices.firstOrNull()?.deviceModel
        )
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = PrimaryBlue)
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFFFFFFF))
            .padding(top = 48.dp)
    ) {
        // Welcome Section
        Column(
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Welcome!",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Text(
                text = "Manage your devices and rooms",
                fontSize = 16.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp,bottom = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Image Section
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp)),
        ) {
            Image(
                painter = painterResource(id = R.drawable.homebgimage),
                contentDescription = "Home Screen Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Content Box with overlap
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .offset(y = (-50).dp),
            contentPadding = PaddingValues(bottom = 100.dp) // Space for bottom nav
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color.White,
                            shape = RoundedCornerShape(topStart = 44.dp, topEnd = 44.dp)
                        )
                        .padding(20.dp)
                ) {
                    Column {
                        // My Devices Section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "My Devices",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                            TextButton(
                                onClick = onAddDeviceClick,
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
                                    text = "Add Device",
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,

                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (devices.isEmpty()) {
                            EmptyDevicesState()
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                items(devices) { device ->
                                    DeviceCardHorizontal(
                                        device = device,
                                        onClick = { onDeviceClick(device.id) }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // My Rooms Section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "My Rooms",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black
                            )
                            TextButton(
                                onClick = onCreateRoomClick,
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
                                    text = "Add Room",
                                    color = Color.Black,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        if (rooms.isEmpty()) {
                            EmptyRoomsState()
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                items(rooms) { room ->
                                    RoomCardHorizontal(
                                        room = room,
                                        onClick = { onRoomClick(room.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

