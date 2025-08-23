package com.fluortronix.fluortronixapp.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.fluortronix.fluortronixapp.presentation.viewmodels.DeviceOnboardingViewModel
import com.fluortronix.fluortronixapp.presentation.viewmodels.ProvisioningState

@Composable
fun ProvisioningScreen(
    navController: NavController,
    viewModel: DeviceOnboardingViewModel
) {
    val provisioningState by viewModel.provisioningState.collectAsState()
    val debugLogs by viewModel.debugLogs.collectAsState()
    val isCopied by viewModel.isCopied.collectAsState()
    val listState = rememberLazyListState()
    val scrollState = rememberScrollState()
    val configuration = LocalConfiguration.current
    
    // Responsive sizing based on screen size
    val isSmallScreen = configuration.screenHeightDp < 700 || configuration.screenWidthDp < 400
    val statusCardHeight = if (isSmallScreen) 120.dp else 180.dp
    val logsCardMinHeight = if (isSmallScreen) 250.dp else 300.dp
    val paddingSize = if (isSmallScreen) 12.dp else 16.dp
    val textScaling = if (isSmallScreen) 1.1f else 1f // Make text BIGGER on small screens

    // Auto-scroll to bottom when new logs arrive
    LaunchedEffect(debugLogs.size) {
        if (debugLogs.isNotEmpty()) {
            listState.animateScrollToItem(debugLogs.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(
                start = paddingSize,
                top = if (isSmallScreen) 20.dp else paddingSize, 
                end = paddingSize,
                bottom = if (isSmallScreen) 16.dp else paddingSize
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 8.dp else paddingSize)
    ) {
        // Main status display (top section)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = statusCardHeight)
                .padding(bottom = if (isSmallScreen) 8.dp else 0.dp),
            shape = RoundedCornerShape(if (isSmallScreen) 8.dp else 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isSmallScreen) 16.dp else 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (provisioningState) {
                    is ProvisioningState.Idle -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(if (isSmallScreen) 32.dp else 40.dp)
                        )
                        Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 16.dp))
                        Text(
                            "Initializing...", 
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = if (isSmallScreen) 18.sp else MaterialTheme.typography.titleMedium.fontSize
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                    is ProvisioningState.ConnectingToDevice -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(if (isSmallScreen) 32.dp else 40.dp)
                        )
                        Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 16.dp))
                                                    Text(
                                "Connecting to device...", 
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = if (isSmallScreen) 18.sp else MaterialTheme.typography.titleMedium.fontSize
                                ),
                                textAlign = TextAlign.Center
                            )
                    }
                    is ProvisioningState.SendingCredentials -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(if (isSmallScreen) 32.dp else 40.dp)
                        )
                        Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 16.dp))
                        Text(
                            "Sending credentials...", 
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = if (isSmallScreen) 18.sp else MaterialTheme.typography.titleMedium.fontSize
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                    is ProvisioningState.DiscoveringDevice -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(if (isSmallScreen) 32.dp else 40.dp)
                        )
                        Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 16.dp))
                        Text(
                            "Discovering device on network...", 
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = if (isSmallScreen) 18.sp else MaterialTheme.typography.titleMedium.fontSize
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                    is ProvisioningState.Success -> {
                        Text(
                            "✅ Device provisioned successfully!", 
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = if (isSmallScreen) 18.sp else MaterialTheme.typography.titleMedium.fontSize
                            ),
                            color = Color(0xFF4CAF50),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 16.dp))
                        Text(
                            "Redirecting to Room Management...",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = if (isSmallScreen) 16.sp else MaterialTheme.typography.bodyMedium.fontSize
                            ),
                            textAlign = TextAlign.Center
                        )
                        
                        // Automatically navigate to Rooms screen after success
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(1500)
                            navController.navigate("rooms") {
                                popUpTo(0) { inclusive = false }
                            }
                        }
                    }
                    is ProvisioningState.Failure -> {
                        val message = (provisioningState as ProvisioningState.Failure).message
                        Text(
                            "❌ Failed to provision device", 
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = if (isSmallScreen) 18.sp else MaterialTheme.typography.titleMedium.fontSize
                            ),
                            color = Color(0xFFE57373),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(if (isSmallScreen) 8.dp else 8.dp))
                        Text(
                            message, 
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = if (isSmallScreen) 14.sp else MaterialTheme.typography.bodyMedium.fontSize
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = if (isSmallScreen) 4 else 4,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(if (isSmallScreen) 12.dp else 16.dp))
                        
                        if (isSmallScreen) {
                            // Stack buttons vertically on small screens
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.provisionDevice() },
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                ) {
                                    Text("Retry", fontSize = 14.sp)
                                }
                                Button(
                                    onClick = { navController.popBackStack() },
                                    modifier = Modifier.fillMaxWidth(0.7f)
                                ) {
                                    Text("Go Back", fontSize = 14.sp)
                                }
                            }
                        } else {
                            // Side by side on larger screens
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(onClick = { viewModel.provisionDevice() }) {
                                    Text("Retry")
                                }
                                Button(onClick = { navController.popBackStack() }) {
                                    Text("Go Back")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Debug logs section (bottom section)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Take remaining space
                .heightIn(min = logsCardMinHeight, max = if (isSmallScreen) 400.dp else 500.dp)
                .padding(bottom = if (isSmallScreen) 8.dp else 0.dp), // Extra bottom margin on small screens
            shape = RoundedCornerShape(if (isSmallScreen) 8.dp else 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isSmallScreen) 12.dp else 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Debug Logs",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontSize = MaterialTheme.typography.titleSmall.fontSize * textScaling
                        ),
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    if (isSmallScreen) {
                        // Stack buttons vertically on small screens
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { viewModel.copyDebugLogs() },
                                modifier = Modifier
                                    .height(36.dp)
                                    .fillMaxWidth(0.5f),
                                enabled = debugLogs.isNotEmpty()
                            ) {
                                Text(
                                    if (isCopied) "✓ Copied" else "Copy",
                                    fontSize = 12.sp,
                                    color = if (isCopied) Color(0xFF4CAF50) else Color.Unspecified
                                )
                            }
                            
                            Button(
                                onClick = { viewModel.clearDebugLogs() },
                                modifier = Modifier
                                    .height(36.dp)
                                    .fillMaxWidth(0.5f)
                            ) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    } else {
                        // Side by side on larger screens
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { viewModel.copyDebugLogs() },
                                modifier = Modifier.height(32.dp),
                                enabled = debugLogs.isNotEmpty()
                            ) {
                                Text(
                                    if (isCopied) "✓ Copied" else "Copy",
                                    fontSize = 12.sp,
                                    color = if (isCopied) Color(0xFF4CAF50) else Color.Unspecified
                                )
                            }
                            
                            Button(
                                onClick = { viewModel.clearDebugLogs() },
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Clear", fontSize = 12.sp)
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(if (isSmallScreen) 6.dp else 8.dp))
                
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(alpha = 0.03f),
                            RoundedCornerShape(if (isSmallScreen) 6.dp else 8.dp)
                        )
                        .padding(if (isSmallScreen) 12.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isSmallScreen) 6.dp else 4.dp),
                    contentPadding = PaddingValues(bottom = if (isSmallScreen) 24.dp else 16.dp)
                ) {
                    if (debugLogs.isEmpty()) {
                        item {
                            Text(
                                "No debug logs yet...",
                                color = Color.Gray,
                                fontSize = if (isSmallScreen) 14.sp else 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else {
                        items(debugLogs) { log ->
                            Text(
                                text = log,
                                fontSize = if (isSmallScreen) 13.sp else 11.sp, // Much larger font for mobile
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    log.contains("❌") -> Color(0xFFE57373)
                                    log.contains("✅") -> Color(0xFF4CAF50)
                                    log.contains("⏳") -> Color(0xFFFF9800)
                                    log.contains("🔍") -> Color(0xFF2196F3)
                                    else -> Color.Black.copy(alpha = 0.9f)
                                },
                                lineHeight = if (isSmallScreen) 18.sp else 14.sp, // Better line spacing
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = if (isSmallScreen) 2.dp else 1.dp)
                            )
                        }
                    }
                }
            }
        }
        
        // Bottom spacer for better spacing from bottom bar
        Spacer(modifier = Modifier.height(110.dp))
    }
} 