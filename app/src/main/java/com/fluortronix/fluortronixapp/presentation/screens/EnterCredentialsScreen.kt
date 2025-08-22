package com.fluortronix.fluortronixapp.presentation.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.fluortronix.fluortronixapp.R
import com.fluortronix.fluortronixapp.presentation.viewmodels.DeviceOnboardingViewModel
import com.fluortronix.fluortronixapp.presentation.viewmodels.ProvisioningState

@Composable
fun EnterCredentialsScreen(
    navController: NavController,
    viewModel: DeviceOnboardingViewModel = hiltViewModel()
) {
    val ssid by viewModel.ssid.collectAsState()
    val password by viewModel.password.collectAsState()
    val provisioningState by viewModel.provisioningState.collectAsState()
    val selectedEspDeviceSSID by viewModel.selectedEspDeviceSSID.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    var isProvisioning by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val keyboardController = LocalSoftwareKeyboardController.current

    // Navigate to provisioning screen when provisioning starts
    LaunchedEffect(provisioningState) {
        when (provisioningState) {
            is ProvisioningState.ConnectingToDevice,
            is ProvisioningState.SendingCredentials -> {
                if (!isProvisioning) {
                    isProvisioning = true
                    navController.navigate("provisioning")
                }
            }
            else -> {
                if (provisioningState is ProvisioningState.Idle) {
                    isProvisioning = false
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Background image (remains static)
        Image(
            painter = painterResource(id = R.drawable.wifisetupscreenbg),
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop
        )

        // Scrollable content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Connected ESP Device Info
            if (selectedEspDeviceSSID.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFE8F5E8)
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
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Connected to device",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF2E7D32)
                            )
                            Text(
                                text = selectedEspDeviceSSID,
                                fontSize = 12.sp,
                                color = Color(0xFF388E3C)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Card for Wi-Fi Credentials Input
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFFEFE)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Enter the Wi-Fi network you want your ESP device to connect to",
                        fontSize = 14.sp,
                        color = Color.Black,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = ssid,
                        onValueChange = { viewModel.onSsidChange(it) },
                        label = { Text("SSID") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        enabled = !isProvisioning
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { viewModel.onPasswordChange(it) },
                        label = { Text("Password") },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            val image = if (passwordVisible)
                                Icons.Filled.Visibility
                            else Icons.Filled.VisibilityOff

                            val description = if (passwordVisible) "Hide password" else "Show password"

                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(imageVector = image, contentDescription = description)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(),
                        enabled = !isProvisioning
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            keyboardController?.hide()
                            viewModel.provisionDevice()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isProvisioning && ssid.isNotBlank() && password.isNotBlank()
                    ) {
                        if (isProvisioning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Starting...")
                        } else {
                            Text("Connect")
                        }
                    }
                }
            }
            // Add extra space at the bottom to allow scrolling when keyboard is open
            Spacer(modifier = Modifier.height(300.dp))
        }
    }
}