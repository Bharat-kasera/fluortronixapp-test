package com.fluortronix.fluortronixapp.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (provisioningState) {
            is ProvisioningState.Idle -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Initializing...")
            }
            is ProvisioningState.ConnectingToDevice -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Connecting to device...")
            }
            is ProvisioningState.SendingCredentials -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Sending credentials...")
            }
            is ProvisioningState.DiscoveringDevice -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text("Discovering device on network...")
            }
            is ProvisioningState.Success -> {
                Text("Device provisioned successfully!")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Redirecting to Room Management...")
                
                // Automatically navigate to Rooms screen after success
                LaunchedEffect(Unit) {
                    kotlinx.coroutines.delay(1500) // Wait 1.5 seconds to show success message
                    navController.navigate("rooms") {
                        popUpTo(0) { inclusive = false }
                    }
                }
            }
            is ProvisioningState.Failure -> {
                val message = (provisioningState as ProvisioningState.Failure).message
                Text("Failed to provision device: $message")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.provisionDevice() }) {
                    Text("Retry")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("Go Back")
            }
            }
        }
    }
} 