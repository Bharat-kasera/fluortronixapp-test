package com.fluortronix.fluortronixapp.presentation.viewmodels

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.wifi.ScanResult
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fluortronix.fluortronixapp.data.datasource.ESPDeviceService
import com.fluortronix.fluortronixapp.data.datasource.WifiService
import com.fluortronix.fluortronixapp.data.models.Device
import com.fluortronix.fluortronixapp.data.models.Room
import com.fluortronix.fluortronixapp.data.repository.DeviceRepository
import com.fluortronix.fluortronixapp.data.repository.RoomRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.timeout
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds
import javax.inject.Inject

@HiltViewModel
class DeviceOnboardingViewModel @Inject constructor(
    private val wifiService: WifiService,
    private val espDeviceService: ESPDeviceService,
    private val deviceRepository: DeviceRepository,
    private val roomRepository: RoomRepository,
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()
    
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()
    
    private val _showSetupDialog = MutableStateFlow(true)
    val showSetupDialog: StateFlow<Boolean> = _showSetupDialog.asStateFlow()
    
    private val _scanError = MutableStateFlow<String?>(null)
    val scanError: StateFlow<String?> = _scanError.asStateFlow()

    private val _ssid = MutableStateFlow("")
    val ssid: StateFlow<String> = _ssid.asStateFlow()

    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password.asStateFlow()

    private val _selectedEspDeviceSSID = MutableStateFlow("")
    val selectedEspDeviceSSID: StateFlow<String> = _selectedEspDeviceSSID.asStateFlow()

    private val _provisioningState = MutableStateFlow<ProvisioningState>(ProvisioningState.Idle)
    val provisioningState: StateFlow<ProvisioningState> = _provisioningState.asStateFlow()

    private val _newDevice = MutableStateFlow<Device?>(null)
    val newDevice: StateFlow<Device?> = _newDevice.asStateFlow()

    private val _rooms = MutableStateFlow<List<Room>>(emptyList())
    val rooms: StateFlow<List<Room>> = _rooms.asStateFlow()

    // ESP Auto-Connect States
    private val _espConnectionState = MutableStateFlow<ESPConnectionState>(ESPConnectionState.Idle)
    val espConnectionState: StateFlow<ESPConnectionState> = _espConnectionState.asStateFlow()

    private val _selectedESPDevice = MutableStateFlow<ScanResult?>(null)
    val selectedESPDevice: StateFlow<ScanResult?> = _selectedESPDevice.asStateFlow()

    // Debug logs for UI display
    private val _debugLogs = MutableStateFlow<List<String>>(emptyList())
    val debugLogs: StateFlow<List<String>> = _debugLogs.asStateFlow()

    // Copy status for UI feedback
    private val _isCopied = MutableStateFlow(false)
    val isCopied: StateFlow<Boolean> = _isCopied.asStateFlow()

    init {
        savedStateHandle.get<String>("ssid")?.let {
            _ssid.value = it // Preserve original input for display
            // Also check if this is an ESP device SSID from navigation
            if (it.startsWith("FLUO-")) {
                _selectedEspDeviceSSID.value = it
            }
        }
        try {
            fetchRooms()
        } catch (e: Exception) {
            // Handle or log the exception, e.g., update a state to show an error
        }
    }

    private fun fetchRooms() {
        viewModelScope.launch {
            roomRepository.getRooms().collect {
                _rooms.value = it
            }
        }
    }

    /**
     * Add debug log to both console and UI
     */
    private fun addDebugLog(message: String) {
        println("DEBUG: $message") // Keep console logging
        
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        val logMessage = "[$timestamp] $message"
        
        val currentLogs = _debugLogs.value.toMutableList()
        currentLogs.add(logMessage)
        
        // Keep only last 20 logs to prevent memory issues
        if (currentLogs.size > 20) {
            currentLogs.removeAt(0)
        }
        
        _debugLogs.value = currentLogs
    }

    /**
     * Clear debug logs 
     */
    fun clearDebugLogs() {
        _debugLogs.value = emptyList()
        _isCopied.value = false
    }

    /**
     * Copy all debug logs to clipboard in formatted text
     */
    fun copyDebugLogs() {
        try {
            val logs = _debugLogs.value
            if (logs.isEmpty()) {
                return
            }

            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            
            // Format logs nicely for sharing
            val formattedLogs = buildString {
                appendLine("=== FluorTronix Debug Logs ===")
                appendLine("Generated: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
                appendLine("Device: ${android.os.Build.MODEL} (${android.os.Build.VERSION.RELEASE})")
                appendLine("App Version: FluorTronix Android")
                appendLine("================================")
                appendLine()
                
                logs.forEach { log ->
                    appendLine(log)
                }
                
                appendLine()
                appendLine("=== End Debug Logs ===")
            }

            val clipData = ClipData.newPlainText("FluorTronix Debug Logs", formattedLogs)
            clipboardManager.setPrimaryClip(clipData)
            
            // Show copied feedback
            _isCopied.value = true
            
            // Reset copy feedback after 2 seconds
            viewModelScope.launch {
                delay(2000)
                _isCopied.value = false
            }
            
        } catch (e: Exception) {
            addDebugLog("❌ Failed to copy logs: ${e.message}")
        }
    }

    fun onSsidChange(ssid: String) {
        _ssid.value = ssid // Allow spaces during typing
    }

    fun onPasswordChange(password: String) {
        _password.value = password // Allow spaces during typing
    }

    fun provisionDevice() {
        viewModelScope.launch {
            try {
                clearDebugLogs() // Clear previous logs
                _provisioningState.value = ProvisioningState.ConnectingToDevice
                
                // Trim trailing spaces from credentials before using them
                val trimmedSSID = ssid.value.trimEnd()
                val trimmedPassword = password.value.trimEnd()
                
                addDebugLog("Starting provisioning - Target SSID: '$trimmedSSID'")
                addDebugLog("Selected ESP Device SSID: ${_selectedEspDeviceSSID.value}")

                // Ensure we're connected to the ESP device's access point
                val espSSID = if (_selectedEspDeviceSSID.value.isNotEmpty()) {
                    _selectedEspDeviceSSID.value
                } else {
                    // Fallback to generic name for backward compatibility
                    "FLUO-Setup"
                }
                
                addDebugLog("Ensuring connection to ESP access point: $espSSID")
                val espConnectionResult = wifiService.connectToWifiAsync(espSSID, "12345678", isEspDevice = true)
                
                if (!espConnectionResult.isSuccess) {
                    val error = espConnectionResult.exceptionOrNull()?.message ?: "Failed to connect to ESP device"
                    addDebugLog("❌ Failed to connect to ESP device: $error")
                    _provisioningState.value = ProvisioningState.Failure("Failed to connect to ESP device: $error")
                    return@launch
                }
                
                addDebugLog("✅ Successfully connected to ESP device: $espSSID")
                // Small delay to ensure connection is stable
                delay(1000)

                _provisioningState.value = ProvisioningState.SendingCredentials
                addDebugLog("Sending credentials to ESP8266...")
                
                // Important: Send credentials immediately while still connected to ESP AP
                val result = espDeviceService.provisionDevice(trimmedSSID, trimmedPassword)
                addDebugLog("Provisioning result: ${result.isSuccess}, ${result.exceptionOrNull()?.message}")

            if (result.isSuccess) {
                    addDebugLog("✅ Credentials sent successfully, ESP will switch networks")
                    
                    // After successful provisioning, the device will disconnect and connect to the new network.
                    // We need to switch back to that network and wait for the device to come online.
                    addDebugLog("Switching phone back to target WiFi network...")
                    val targetConnectionResult = wifiService.connectToWifiAsync(trimmedSSID, trimmedPassword, isEspDevice = false)
                    
                    if (!targetConnectionResult.isSuccess) {
                        val error = targetConnectionResult.exceptionOrNull()?.message ?: "Failed to connect to target WiFi"
                        addDebugLog("❌ Failed to connect to target WiFi: $error")
                        _provisioningState.value = ProvisioningState.Failure("Failed to connect to target WiFi: $error")
                        return@launch
                    }
                    
                    addDebugLog("✅ Successfully connected to target WiFi: ${ssid.value}")
                    
                    // Give time for the ESP8266 to complete its transition:
                    // - Stop SoftAP server
                    // - Switch to Station mode  
                    // - Connect to new WiFi (up to 20 seconds)
                    // - Start web server
                    // Reduced wait time since we have optimized parallel discovery
                    addDebugLog("⏳ Waiting 20 seconds for ESP device to complete network transition...")
                    delay(20000) 
                    
                    _provisioningState.value = ProvisioningState.DiscoveringDevice
                    addDebugLog("🔍 Starting device discovery on new network...")

                    // Debug current network state before discovery
                    wifiService.getCurrentNetworkInfo()
                    
                    // Ensure we're bound to WiFi network for discovery
                    wifiService.ensureWifiBinding()
                    
                    // Small delay to ensure network state has stabilized
                    delay(2000)

                    // Try network discovery with retries to find the device on the new network
                    var discoveryResult: Result<Device?>
                    var discoveryAttempts = 0
                    val maxDiscoveryAttempts = 3
                    
                    do {
                        discoveryAttempts++
                        addDebugLog("🔍 Device discovery attempt $discoveryAttempts/$maxDiscoveryAttempts")
                        
                        discoveryResult = espDeviceService.discoverProvisionedDevice(_selectedEspDeviceSSID.value)
                        
                        if (!discoveryResult.isSuccess && discoveryAttempts < maxDiscoveryAttempts) {
                            addDebugLog("⏳ Discovery attempt $discoveryAttempts failed, waiting 5 seconds before retry...")
                            delay(5000)
                            // Re-ensure WiFi binding before retry
                            wifiService.ensureWifiBinding()
                        }
                    } while (!discoveryResult.isSuccess && discoveryAttempts < maxDiscoveryAttempts)
                    
                    if (discoveryResult.isSuccess) {
                        val discoveredDevice = discoveryResult.getOrNull()
                        if (discoveredDevice != null) {
                            // Save the device to repository so it shows up in home screen
                            deviceRepository.addDevice(discoveredDevice)
                            _newDevice.value = discoveredDevice
                            _provisioningState.value = ProvisioningState.Success
                            addDebugLog("✅ Device discovered and saved successfully!")
                        } else {
                            addDebugLog("❌ Device discovery returned null result")
                            _provisioningState.value = ProvisioningState.Failure("Device discovery returned null result")
                        }
                    } else {
                        addDebugLog("❌ Device discovery failed: ${discoveryResult.exceptionOrNull()?.message}")
                        _provisioningState.value = ProvisioningState.Failure("Could not find device on network. Please ensure both phone and device are connected to the same WiFi. Error: ${discoveryResult.exceptionOrNull()?.message}")
                    }
                } else {
                    addDebugLog("❌ Failed to send credentials: ${result.exceptionOrNull()?.message}")
                    _provisioningState.value = ProvisioningState.Failure(result.exceptionOrNull()?.message ?: "Unknown error")
                }
            } catch (e: Exception) {
                addDebugLog("❌ Exception in provisionDevice: ${e.message}")
                e.printStackTrace()
                _provisioningState.value = ProvisioningState.Failure("Error: ${e.message}")
            }
        }
    }

    fun assignDeviceToRoom(roomId: String) {
        viewModelScope.launch {
            newDevice.value?.let { device ->
                val updatedDevice = device.copy(roomId = roomId)
                deviceRepository.addDevice(updatedDevice)
            }
        }
    }

    // ESP Auto-Connect Functions
    fun connectToESPDevice(espDevice: ScanResult) {
        viewModelScope.launch {
            try {
                _selectedESPDevice.value = espDevice
                _espConnectionState.value = ESPConnectionState.Connecting
                
                println("DEBUG: Auto-connecting to ESP device: ${espDevice.SSID}")
                
                // Connect to ESP device with hardcoded password and wait for actual connection
                val connectionResult = wifiService.connectToWifiAsync(espDevice.SSID, "12345678", isEspDevice = true)
                
                if (connectionResult.isSuccess) {
                    _espConnectionState.value = ESPConnectionState.Connected
                    _selectedEspDeviceSSID.value = espDevice.SSID
                    println("DEBUG: Successfully connected to ESP device: ${espDevice.SSID}")
                } else {
                    val error = connectionResult.exceptionOrNull()?.message ?: "Unknown connection error"
                    println("DEBUG: Failed to connect to ESP device: $error")
                    _espConnectionState.value = ESPConnectionState.Error("Failed to connect: $error")
                }
                
            } catch (e: Exception) {
                println("DEBUG: Exception in connectToESPDevice: ${e.message}")
                _espConnectionState.value = ESPConnectionState.Error("Failed to connect: ${e.message}")
            }
        }
    }

    fun resetESPConnectionState() {
        _espConnectionState.value = ESPConnectionState.Idle
        _selectedESPDevice.value = null
    }

    fun proceedToCredentialsScreen() {
        // This will be called when user wants to proceed to credentials screen after ESP connection
        _espConnectionState.value = ESPConnectionState.ReadyForCredentials
    }

    fun startWifiScan() {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            _scanError.value = null
            _scanResults.value = emptyList()
            
            try {
                wifiService.getNetworks()
                    .timeout(30.seconds)
                    .catch { e ->
                        _scanState.value = ScanState.Error
                        _scanError.value = when {
                            e.message?.contains("timeout", ignoreCase = true) == true -> "Scan timeout. Please try again."
                            e.message?.contains("wifi", ignoreCase = true) == true -> "Wi-Fi is disabled. Please enable Wi-Fi and try again."
                            e.message?.contains("permission", ignoreCase = true) == true -> "Location permission is required for Wi-Fi scanning."
                            else -> e.message ?: "Failed to scan for networks"
                        }
                    }
                    .collect { results ->
                        val allNetworks = results.filter { result ->
                            result.SSID != null && result.SSID.isNotBlank()
                        }
                        
                        // Separate ESP devices and other networks, prioritize ESP devices
                        val espDevices = allNetworks.filter { isESPDevice(it) }
                        val otherNetworks = allNetworks.filter { !isESPDevice(it) }
                        
                        // Combine with ESP devices first (sorted by signal strength), then other networks
                        val sortedNetworks = espDevices.sortedByDescending { it.level } + 
                                           otherNetworks.sortedByDescending { it.level }
                        
                        _scanResults.value = sortedNetworks
                        
                        // Check if we found any ESP devices
                        val foundESPDevices = espDevices.isNotEmpty()
                        _scanState.value = when {
                            foundESPDevices -> ScanState.Success
                            allNetworks.isNotEmpty() -> ScanState.Success
                            else -> ScanState.NoDevicesFound
                        }
                        
                        println("DEBUG: Found ${espDevices.size} ESP devices and ${otherNetworks.size} other networks")
                        espDevices.forEach { device ->
                            println("DEBUG: ESP Device found: ${device.SSID} (${device.level} dBm)")
                        }
                    }
            } catch (e: Exception) {
                _scanState.value = ScanState.Error
                _scanError.value = when {
                    e.message?.contains("wifi", ignoreCase = true) == true -> "Wi-Fi is disabled. Please enable Wi-Fi and try again."
                    e.message?.contains("permission", ignoreCase = true) == true -> "Location permission is required for Wi-Fi scanning."
                    else -> e.message ?: "An unexpected error occurred"
                }
            }
        }
    }
    
    /**
     * Helper function to check if a WiFi network is an ESP device
     */
    private fun isESPDevice(scanResult: ScanResult): Boolean {
        return scanResult.SSID?.startsWith("FLUO-", ignoreCase = true) == true
    }
    
    fun dismissSetupDialog() {
        _showSetupDialog.value = false
    }
    
    fun showSetupDialog() {
        _showSetupDialog.value = true
    }
    
    fun resetScanState() {
        _scanState.value = ScanState.Idle
        _scanError.value = null
        _scanResults.value = emptyList()
    }
    
    fun onScreenLeft() {
        resetScanState()
        _showSetupDialog.value = true
    }
}

sealed class ProvisioningState {
    object Idle : ProvisioningState()
    object ConnectingToDevice : ProvisioningState()
    object SendingCredentials : ProvisioningState()
    object DiscoveringDevice : ProvisioningState()
    object Success : ProvisioningState()
    data class Failure(val message: String) : ProvisioningState()
}

sealed class ScanState {
    object Idle : ScanState()
    object Scanning : ScanState()
    object Success : ScanState()
    object NoDevicesFound : ScanState()
    object Error : ScanState()
}

sealed class ESPConnectionState {
    object Idle : ESPConnectionState()
    object Connecting : ESPConnectionState()
    object Connected : ESPConnectionState()
    object ReadyForCredentials : ESPConnectionState()
    data class Error(val message: String) : ESPConnectionState()
}