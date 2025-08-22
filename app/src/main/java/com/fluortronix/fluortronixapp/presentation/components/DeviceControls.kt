package com.fluortronix.fluortronixapp.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.PowerOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PowerToggle(
    isOn: Boolean,
    onToggle: () -> Unit,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isOn) 1.1f else 1f,
        animationSpec = tween(300),
        label = "power_scale"
    )
    
    Card(
        onClick = { if (isEnabled) onToggle() },
        modifier = modifier
            .size(120.dp)
            .scale(scale),
        colors = CardDefaults.cardColors(
            containerColor = if (isOn) PrimaryBlue else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isOn) 8.dp else 4.dp
        ),
        shape = CircleShape,
        enabled = isEnabled
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isOn) Icons.Default.Power else Icons.Default.PowerOff,
                    contentDescription = if (isOn) "Turn Off" else "Turn On",
                    tint = if (isOn) Color.White else Color.Gray,
                    modifier = Modifier.size(32.dp)
                )
                
                Text(
                    text = if (isOn) "ON" else "OFF",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isOn) Color.White else Color.Gray
                )
            }
        }
    }
}

@Composable
fun PWMSlider(
    name: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int = 0,
    maxValue: Int = 255,
    isEnabled: Boolean = true,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    var sliderValue by remember(value) { mutableStateOf(value.toFloat()) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Slider header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Text(
                        text = name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }
                
                // Value display
                Box(
                    modifier = Modifier
                        .background(
                            color = PrimaryBlue.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${value}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Slider
            Slider(
                value = sliderValue,
                onValueChange = { 
                    sliderValue = it
                },
                onValueChangeFinished = {
                    onValueChange(sliderValue.toInt())
                },
                valueRange = minValue.toFloat()..maxValue.toFloat(),
                enabled = isEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = PrimaryBlue,
                    activeTrackColor = PrimaryBlue,
                    inactiveTrackColor = PrimaryBlue.copy(alpha = 0.3f),
                    disabledThumbColor = Color.Gray,
                    disabledActiveTrackColor = Color.Gray.copy(alpha = 0.5f),
                    disabledInactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            // Value range labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = minValue.toString(),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                
                Text(
                    text = maxValue.toString(),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun DeviceControlPanel(
    isDeviceOn: Boolean,
    onPowerToggle: () -> Unit,
    sliderNames: List<String>,
    sliderValues: List<Int>,
    onSliderChange: (Int, Int) -> Unit,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Power control section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = Color.White
            ),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 4.dp
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Device Power",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Black
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                PowerToggle(
                    isOn = isDeviceOn,
                    onToggle = onPowerToggle,
                    isEnabled = isEnabled
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = if (isDeviceOn) "Device is active" else "Device is inactive",
                    fontSize = 14.sp,
                    color = if (isDeviceOn) PrimaryBlue else Color.Gray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        
        // Sliders section
        if (sliderNames.isNotEmpty() && sliderValues.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = 4.dp
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Controls",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(24.dp)
                        )
                        
                        Text(
                            text = "PWM Controls",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Dynamic sliders based on device capabilities
                    sliderNames.take(6).forEachIndexed { index, sliderName ->
                        if (index < sliderValues.size) {
                            val icon = getSliderIcon(sliderName)
                            
                            PWMSlider(
                                name = sliderName,
                                value = sliderValues[index],
                                onValueChange = { newValue ->
                                    onSliderChange(index, newValue)
                                },
                                isEnabled = isEnabled && isDeviceOn,
                                icon = icon
                            )
                            
                            if (index < sliderNames.lastIndex && index < 5) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                    
                    // Show warning if device is off
                    if (!isDeviceOn && sliderNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFFFFF3E0),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "Turn on the device to adjust controls",
                                fontSize = 14.sp,
                                color = Color(0xFFE65100),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Returns appropriate icon based on slider name
 */
private fun getSliderIcon(sliderName: String): ImageVector? {
    val name = sliderName.lowercase()
    return when {
        name.contains("brightness") || name.contains("dim") -> Icons.Default.Brightness6
        name.contains("power") -> Icons.Default.Power
        else -> null
    }
}

@Composable
fun ConnectionStatus(
    isConnected: Boolean,
    isConnecting: Boolean,
    lastUpdateTime: Long,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when {
                isConnecting -> Color(0xFFFFF3E0)
                isConnected -> Color(0xFFE8F5E8)
                else -> Color(0xFFFFEBEE)
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isConnecting -> Color(0xFFFF9800)
                            isConnected -> Color(0xFF4CAF50)
                            else -> Color(0xFFE57373)
                        }
                    )
            )
            
            Text(
                text = when {
                    isConnecting -> "Connecting to device..."
                    isConnected -> "Connected • Real-time updates"
                    else -> "Disconnected"
                },
                fontSize = 14.sp,
                color = when {
                    isConnecting -> Color(0xFFE65100)
                    isConnected -> Color(0xFF2E7D32)
                    else -> Color(0xFFC62828)
                },
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            if (isConnected && lastUpdateTime > 0) {
                val timeDiff = (System.currentTimeMillis() - lastUpdateTime) / 1000
                Text(
                    text = when {
                        timeDiff < 60 -> "${timeDiff}s ago"
                        timeDiff < 3600 -> "${timeDiff / 60}m ago"
                        else -> "${timeDiff / 3600}h ago"
                    },
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DeviceControlsPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionStatus(
            isConnected = true,
            isConnecting = false,
            lastUpdateTime = System.currentTimeMillis() - 5000
        )
        
        DeviceControlPanel(
            isDeviceOn = true,
            onPowerToggle = { },
            sliderNames = listOf("Brightness", "Red", "Green", "Blue"),
            sliderValues = listOf(255, 128, 200, 64),
            onSliderChange = { _, _ -> }
        )
    }
} 