package com.fluortronix.fluortronixapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue

enum class ErrorType {
    DEVICE_OFFLINE,
    NETWORK_ERROR,
    CONNECTION_TIMEOUT,
    INVALID_SPD_DATA,
    DEVICE_NOT_FOUND,
    PERMISSION_DENIED,
    UNKNOWN_ERROR
}

@Composable
fun ErrorStateCard(
    errorType: ErrorType,
    errorMessage: String? = null,
    onRetryClick: (() -> Unit)? = null,
    onDismissClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (icon, title, description, backgroundColor, iconColor) = getErrorDetails(errorType)
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = "Error",
                tint = iconColor,
                modifier = Modifier.size(48.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = errorMessage ?: description,
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (onRetryClick != null) {
                    Button(
                        onClick = onRetryClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PrimaryBlue
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Retry")
                    }
                }
                
                if (onDismissClick != null) {
                    OutlinedButton(
                        onClick = onDismissClick,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Dismiss")
                    }
                }
            }
        }
    }
}

@Composable
fun NetworkErrorBanner(
    isVisible: Boolean,
    errorMessage: String,
    onRetryClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!isVisible) return
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFEBEE)
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
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "Network Error",
                tint = Color(0xFFE57373),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Connection Error",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFC62828)
                )
                Text(
                    text = errorMessage,
                    fontSize = 12.sp,
                    color = Color(0xFFC62828)
                )
            }
            
            TextButton(
                onClick = onRetryClick,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = Color(0xFFC62828)
                )
            ) {
                Text("Retry", fontSize = 12.sp)
            }
            
            IconButton(
                onClick = onDismissClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Dismiss",
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun DeviceOfflineIndicator(
    deviceName: String,
    lastSeenTime: Long,
    onReconnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeDiff = remember(lastSeenTime) {
        (System.currentTimeMillis() - lastSeenTime) / 1000
    }
    
    val timeText = when {
        timeDiff < 60 -> "Last seen ${timeDiff}s ago"
        timeDiff < 3600 -> "Last seen ${timeDiff / 60}m ago"
        timeDiff < 86400 -> "Last seen ${timeDiff / 3600}h ago"
        else -> "Last seen ${timeDiff / 86400}d ago"
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = "Device Offline",
                tint = Color(0xFFE65100),
                modifier = Modifier.size(24.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "$deviceName is offline",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFE65100)
                )
                Text(
                    text = timeText,
                    fontSize = 12.sp,
                    color = Color(0xFFE65100)
                )
            }
            
            Button(
                onClick = onReconnectClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE65100)
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "Reconnect",
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun SPDDataErrorCard(
    onReloadClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFFFF3E0)
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "SPD Error",
                tint = Color(0xFFE65100),
                modifier = Modifier.size(32.dp)
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = "Invalid SPD Data",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            
            Text(
                text = "The spectral power distribution data is corrupted or invalid. Using default spectrum for visualization.",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = onReloadClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE65100)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reload SPD Data")
            }
        }
    }
}

private fun getErrorDetails(errorType: ErrorType): ErrorDetails {
    return when (errorType) {
        ErrorType.DEVICE_OFFLINE -> ErrorDetails(
            icon = Icons.Default.WifiOff,
            title = "Device Offline",
            description = "The device is not responding. Check if it's powered on and connected to WiFi.",
            backgroundColor = Color(0xFFFFF3E0),
            iconColor = Color(0xFFE65100)
        )
        ErrorType.NETWORK_ERROR -> ErrorDetails(
            icon = Icons.Default.NetworkCheck,
            title = "Network Error",
            description = "Unable to communicate with the device. Check your WiFi connection.",
            backgroundColor = Color(0xFFFFEBEE),
            iconColor = Color(0xFFE57373)
        )
        ErrorType.CONNECTION_TIMEOUT -> ErrorDetails(
            icon = Icons.Default.NetworkCheck,
            title = "Connection Timeout",
            description = "The device is taking too long to respond. It may be busy or have poor connection.",
            backgroundColor = Color(0xFFFFF3E0),
            iconColor = Color(0xFFE65100)
        )
        ErrorType.INVALID_SPD_DATA -> ErrorDetails(
            icon = Icons.Default.Error,
            title = "Invalid SPD Data",
            description = "The spectral data from the device is corrupted or in an unsupported format.",
            backgroundColor = Color(0xFFFFF3E0),
            iconColor = Color(0xFFE65100)
        )
        ErrorType.DEVICE_NOT_FOUND -> ErrorDetails(
            icon = Icons.Default.Error,
            title = "Device Not Found",
            description = "The device could not be found. It may have been removed or is offline.",
            backgroundColor = Color(0xFFFFEBEE),
            iconColor = Color(0xFFE57373)
        )
        ErrorType.PERMISSION_DENIED -> ErrorDetails(
            icon = Icons.Default.Error,
            title = "Permission Denied",
            description = "The app doesn't have permission to access network features.",
            backgroundColor = Color(0xFFFFEBEE),
            iconColor = Color(0xFFE57373)
        )
        ErrorType.UNKNOWN_ERROR -> ErrorDetails(
            icon = Icons.Default.Error,
            title = "Unknown Error",
            description = "An unexpected error occurred. Please try again.",
            backgroundColor = Color(0xFFFFEBEE),
            iconColor = Color(0xFFE57373)
        )
    }
}

private data class ErrorDetails(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val backgroundColor: Color,
    val iconColor: Color
)

@Preview(showBackground = true)
@Composable
fun ErrorHandlingPreview() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ErrorStateCard(
            errorType = ErrorType.DEVICE_OFFLINE,
            onRetryClick = { },
            onDismissClick = { }
        )
        
        NetworkErrorBanner(
            isVisible = true,
            errorMessage = "Failed to connect to ESP device",
            onRetryClick = { },
            onDismissClick = { }
        )
        
        DeviceOfflineIndicator(
            deviceName = "DIM-2452",
            lastSeenTime = System.currentTimeMillis() - 300000, // 5 minutes ago
            onReconnectClick = { }
        )
    }
} 