package com.fluortronix.fluortronixapp.presentation.components

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluortronix.fluortronixapp.data.models.Device

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCard(
    device: Device,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)

        ,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFFEFE)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Device info section
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = device.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )

                Text(
                    text = device.roomName ?: "Unassigned",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Online/Offline status
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            if (device.isOnline) Color(0xFF4CAF50) else Color(0xFFFF5722)
                        )
                )

                Text(
                    text = if (device.isOnline) "Online" else "Offline",
                    fontSize = 12.sp,
                    color = if (device.isOnline) Color(0xFF4CAF50) else Color(0xFFFF5722),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceCardHorizontal(
    device: Device,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(122.dp)
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFE)),
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
            // Device info at top
            Column(
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                // Device name
                Text(
                    text = device.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black,
                    style = androidx.compose.ui.text.TextStyle(
                        baselineShift = androidx.compose.ui.text.style.BaselineShift.None,
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )

                // Room name
                Text(
                    text = device.roomName ?: "Unassigned",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            // Online status indicator at bottom right
            Box(
                modifier = Modifier
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(
                        if (device.isOnline) Color(0xFF4CAF50) else Color(0xFFFF5722)
                    )
                    .align(Alignment.BottomEnd)
            )
        }
    }
}

