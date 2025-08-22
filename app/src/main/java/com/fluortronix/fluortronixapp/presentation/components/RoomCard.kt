package com.fluortronix.fluortronixapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluortronix.fluortronixapp.R
import com.fluortronix.fluortronixapp.data.models.Room
import com.fluortronix.fluortronixapp.data.models.RoomStats
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomCard(
    room: Room,
    roomStats: RoomStats?,
    onClick: () -> Unit = {},
    onPowerToggle: () -> Unit = {},
    onEditClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    showControls: Boolean = true
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            ,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFEFE)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center

        ) {
            // Header row with room info and controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Room info section
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Room icon with power state indicator
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (room.isAllDevicesOn && room.deviceCount > 0) 
                                    PrimaryBlue.copy(alpha = 0.2f) 
                                else PrimaryBlue.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter=painterResource(R.drawable.roomicon),
                            contentDescription = "Room",
                            tint = if (room.isAllDevicesOn && room.deviceCount > 0) 
                                PrimaryBlue 
                            else Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    // Room details
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = room.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${room.deviceCount} device${if (room.deviceCount != 1) "s" else ""}",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                            
                            if (room.allowedDeviceModel != null) {
                                Text(
                                    text = "•",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = room.allowedDeviceModel,
                                    fontSize = 12.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                
                // Controls section
                if (showControls) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {

                        // More options button
                        IconButton(
                            onClick = { onEditClick() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = Color.Gray,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(
    count: Int,
    total: Int,
    label: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        
        Text(
            text = "$count/$total $label",
            fontSize = 11.sp,
            color = Color.Gray
        )
    }
}

// Simplified room card for compact views
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactRoomCard(
    room: Room,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Room info
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(
                            color = PrimaryBlue.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(14.dp)
                    )
                }
                
                Column {
                    Text(
                        text = room.name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (room.deviceCount > 0) {
                        Text(
                            text = "${room.deviceCount} devices",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            
            // Status indicator
            if (room.deviceCount > 0) {
                Icon(
                    imageVector = Icons.Default.Circle,
                    contentDescription = null,
                    tint = if (room.isAllDevicesOn) PrimaryBlue else Color.Gray,
                    modifier = Modifier.size(8.dp)
                )
            }
        }
    }
}

// Horizontal room card for carousel view
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomCardHorizontal(
    room: Room,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(120.dp)
            .height(120.dp)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFFFFFFFF).copy(alpha = 0.9f), // Bright white with slight transparency
                        Color(0xFF40C4FF).copy(alpha = 0.7f), // Vibrant sky blue
                        Color(0xFFD81B60).copy(alpha = 0.5f), // Soft magenta for depth
                        Color(0xFF8E24AA).copy(alpha = 0.3f) // Subtle purple for a richer gradient
                    ),
                    start = Offset(0f, 0f), // Gradient starts from top-left
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY), // Diagonal gradient to bottom-right
                    tileMode = TileMode.Clamp // Ensures gradient doesn't repeat
                ),
                shape = RoundedCornerShape(12.dp)
            )
        ,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFEFE)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Room name at top
            Column(
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = room.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Device count indicator at bottom right
            if (room.deviceCount >= 0) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // device icon
                    Icon(
                        painter = painterResource(id = R.drawable.device_count),
                        contentDescription = "Device count",
                        tint = Color(0xFFB3B3B3),
                        modifier = Modifier.size(18.dp)
                    )
                    
                    // Device count text
                    Text(
                        text = if (room.deviceCount == 0) "0" else room.deviceCount.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

