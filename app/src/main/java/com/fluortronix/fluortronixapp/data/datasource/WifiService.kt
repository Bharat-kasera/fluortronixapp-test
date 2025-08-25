package com.fluortronix.fluortronixapp.data.datasource

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.core.app.ActivityCompat
import java.net.InetAddress
import java.net.NetworkInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.coroutines.resume
import javax.inject.Inject

class WifiService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun getNetworks(): Flow<List<ScanResult>> = callbackFlow {
        val wifiScanReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
                if (success) {
                    if (ActivityCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        trySend(wifiManager.scanResults)
                    }
                }
            }
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        context.registerReceiver(wifiScanReceiver, intentFilter)

        wifiManager.startScan()

        awaitClose {
            context.unregisterReceiver(wifiScanReceiver)
        }
    }

    fun connectToWifi(ssid: String, pass: String, isEspDevice: Boolean = false) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Use WifiNetworkSpecifier for API 29+
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(pass)
                .build()

            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            connectivityManager.requestNetwork(networkRequest, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    if (isEspDevice) {
                    connectivityManager.bindProcessToNetwork(network)
                }
            }

                override fun onUnavailable() {
                    super.onUnavailable()
                    // Handle connection failure
                }
            })
        } else {
            // For API levels < 29, show a message to user to manually connect
            // or implement legacy WifiManager approach if needed
            // Note: WifiManager.addNetwork() was deprecated in API 29
        }
    }

    // Store the last connected WiFi network for rebinding
    private var lastWifiNetwork: android.net.Network? = null
    private val connectivityManager by lazy { 
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager 
    }

    suspend fun connectToWifiAsync(ssid: String, pass: String, isEspDevice: Boolean = false): Result<android.net.Network> {
        return suspendCancellableCoroutine { continuation ->
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                
                // For target WiFi networks (not ESP), find and switch to existing saved network
                if (!isEspDevice) {
                    println("DEBUG: 🔄 Switching back to saved WiFi network '$ssid'")
                    
                    GlobalScope.launch {
                        try {
                            // First, clear ESP binding to restore normal WiFi management
                            connectivityManager.bindProcessToNetwork(null)
                            println("DEBUG: ✅ Cleared ESP binding")
                            
                            // Give Android a moment to process the binding change
                            delay(1500)
                            
                            // Strategy 1: Look for existing saved WiFi network that matches target SSID
                            val allNetworks = connectivityManager.allNetworks
                            var targetSavedNetwork: android.net.Network? = null
                            
                            for (network in allNetworks) {
                                try {
                                    val networkCaps = connectivityManager.getNetworkCapabilities(network)
                                    if (networkCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                                        
                                        // Check if this network is validated and not ephemeral
                                        val isValidated = networkCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                                        val isEphemeral = networkCaps.transportInfo?.toString()?.contains("Ephemeral: true") == true
                                        
                                        println("DEBUG: Checking network $network - Validated: $isValidated, Ephemeral: $isEphemeral")
                                        
                                        // Prefer validated, non-ephemeral networks (these are saved networks)
                                        if (isValidated && !isEphemeral) {
                                            targetSavedNetwork = network
                                            println("DEBUG: ✅ Found saved WiFi network: $network")
                                            break
                                        }
                                    }
                                } catch (e: Exception) {
                                    println("DEBUG: Error checking network $network: ${e.message}")
                                }
                            }
                            
                            // If we found a saved network, bind to it
                            if (targetSavedNetwork != null) {
                                try {
                                    connectivityManager.bindProcessToNetwork(targetSavedNetwork)
                                    lastWifiNetwork = targetSavedNetwork
                                    println("DEBUG: 🎉 Successfully bound to saved WiFi network!")
                                    
                                    // Verify the connection by checking SSID
                                    delay(1000) // Brief delay for connection to stabilize
                                    val currentWifiInfo = wifiManager.connectionInfo
                                    val currentSSID = currentWifiInfo?.ssid?.replace("\"", "")
                                    
                                    if (currentSSID == ssid) {
                                        println("DEBUG: ✅ Confirmed connection to '$ssid' using saved network")
                                        if (continuation.isActive) {
                                            continuation.resume(Result.success(targetSavedNetwork))
                                        }
                                        return@launch
                                    } else {
                                        println("DEBUG: ⚠️ Connected to saved network but wrong SSID: '$currentSSID' vs '$ssid'")
                                    }
                                } catch (e: Exception) {
                                    println("DEBUG: Failed to bind to saved network: ${e.message}")
                                }
                            }
                            
                            // Strategy 2: Wait for Android to automatically reconnect
                            println("DEBUG: 🔄 Waiting for Android to automatically reconnect to '$ssid'...")
                            var attemptCount = 0
                            val maxAttempts = 8 // 8 seconds
                            
                            while (attemptCount < maxAttempts) {
                                delay(1000)
                                attemptCount++
                                
                                val currentWifiInfo = wifiManager.connectionInfo
                                val currentSSID = currentWifiInfo?.ssid?.replace("\"", "")
                                
                                println("DEBUG: Auto-reconnect check $attemptCount/$maxAttempts - Current: '$currentSSID', Target: '$ssid'")
                                
                                if (currentSSID == ssid) {
                                    println("DEBUG: 🎉 Android automatically reconnected to '$ssid'!")
                                    
                                    // Find and bind to the current WiFi network
                                    val currentNetworks = connectivityManager.allNetworks
                                    for (network in currentNetworks) {
                                        val networkCaps = connectivityManager.getNetworkCapabilities(network)
                                        if (networkCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                                            networkCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                                            
                                            lastWifiNetwork = network
                                            println("DEBUG: ✅ Using auto-reconnected network: $network")
                                            
                                            if (continuation.isActive) {
                                                continuation.resume(Result.success(network))
                                            }
                                            return@launch
                                        }
                                    }
                                }
                            }
                            
                            // Strategy 3: Final verification - check if we're actually connected to target SSID
                            println("DEBUG: ⚠️ Automatic methods failed - performing final SSID verification...")
                            delay(1000) // Give one more second for potential connection
                            
                            val finalWifiInfo = wifiManager.connectionInfo
                            val finalSSID = finalWifiInfo?.ssid?.replace("\"", "")
                            val finalIP = formatIpAddress(finalWifiInfo?.ipAddress ?: 0)
                            
                            println("DEBUG: Final verification - SSID: '$finalSSID', IP: $finalIP, Target: '$ssid'")
                            
                            if (finalSSID == ssid && finalIP != "0.0.0.0") {
                                println("DEBUG: ✅ Final verification SUCCESS - connected to '$ssid' with IP $finalIP")
                                
                                // Find the actual network for this connection
                                val verifiedNetworks = connectivityManager.allNetworks
                                for (network in verifiedNetworks) {
                                    val caps = connectivityManager.getNetworkCapabilities(network)
                                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                                        lastWifiNetwork = network
                                        if (continuation.isActive) {
                                            continuation.resume(Result.success(network))
                                        }
                                        return@launch
                                    }
                                }
                            }
                            
                            // If we get here, automatic reconnection completely failed
                            println("DEBUG: ❌ AUTOMATIC RECONNECTION FAILED")
                            println("DEBUG: Current SSID: '$finalSSID', Target: '$ssid', IP: $finalIP")
                            
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(Exception("Automatic WiFi reconnection failed - need manual connection to '$ssid'")))
                            }
                            
                        } catch (e: Exception) {
                            println("DEBUG: Exception in automatic WiFi reconnection: ${e.message}")
                            if (continuation.isActive) {
                                continuation.resume(Result.failure(Exception("WiFi reconnection failed: ${e.message}")))
                            }
                        }
                    }
                    
                    return@suspendCancellableCoroutine
                }
                
                // For ESP devices, create ephemeral connection as before
                println("DEBUG: Creating new ESP connection to $ssid")
                
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid(ssid)
                    .setWpa2Passphrase(pass)
                    .build()

                val networkRequest = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .setNetworkSpecifier(specifier)
                    .build()

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    super.onAvailable(network)
                    println("DEBUG: ESP WiFi connection available for $ssid")
                    
                    connectivityManager.bindProcessToNetwork(network)
                    println("DEBUG: Process bound to ESP network")
                    
                    if (continuation.isActive) {
                        continuation.resume(Result.success(network))
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    println("DEBUG: ESP WiFi connection unavailable for $ssid")
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(Exception("ESP WiFi connection unavailable")))
                    }
                }

                override fun onLost(network: android.net.Network) {
                    super.onLost(network)
                    println("DEBUG: ESP WiFi connection lost for $ssid")
                }
            }

                connectivityManager.requestNetwork(networkRequest, callback)
                
                continuation.invokeOnCancellation {
                    connectivityManager.unregisterNetworkCallback(callback)
                }
            } else {
                // For API levels < 29, return failure indicating manual connection needed
                if (continuation.isActive) {
                    continuation.resume(Result.failure(Exception("WiFi connection requires manual setup on Android versions below 10")))
                }
            }
        }
    }

    /**
     * Ensures the process is bound to WiFi network for local device discovery
     * Enhanced to handle dual-transport scenarios (WiFi + Cellular)
     */
    fun ensureWifiBinding() {
        println("DEBUG: Starting WiFi binding process...")
        
        // First, clear any existing binding to allow fresh binding
        try {
            connectivityManager.bindProcessToNetwork(null)
            println("DEBUG: Cleared existing network binding")
        } catch (e: Exception) {
            println("DEBUG: Failed to clear binding (this may be normal): ${e.message}")
        }
        
        // Method 1: Try using stored WiFi network first
        lastWifiNetwork?.let { network ->
            val networkCaps = connectivityManager.getNetworkCapabilities(network)
            if (networkCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                networkCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                
                try {
                    connectivityManager.bindProcessToNetwork(network)
                    println("DEBUG: Re-bound process to stored WiFi network for discovery")
                    
                    // Verify binding worked by checking active network
                    if (verifyWifiBinding()) {
                        return
                    } else {
                        println("DEBUG: Stored WiFi binding verification failed")
                    }
                } catch (e: Exception) {
                    println("DEBUG: Failed to bind to stored network: ${e.message}")
                }
            }
        }
        
        // Method 2: Find and bind to current WiFi network with enhanced selection
        println("DEBUG: Searching for best available WiFi network...")
        val allNetworks = connectivityManager.allNetworks
        var bestWifiNetwork: android.net.Network? = null
        var bestNetworkCaps: NetworkCapabilities? = null
        
        // Find the best WiFi network (validated and connected)
        for (network in allNetworks) {
            val networkCaps = connectivityManager.getNetworkCapabilities(network)
            if (networkCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                println("DEBUG: Found WiFi network: $network, capabilities: $networkCaps")
                
                // Prefer validated networks
                if (networkCaps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                    bestWifiNetwork = network
                    bestNetworkCaps = networkCaps
                    println("DEBUG: Selected validated WiFi network: $network")
                    break
                } else if (bestWifiNetwork == null) {
                    // Fallback to any WiFi network
                    bestWifiNetwork = network
                    bestNetworkCaps = networkCaps
                    println("DEBUG: Selected fallback WiFi network: $network")
                }
            }
        }
        
        // Bind to the best WiFi network found
        bestWifiNetwork?.let { network ->
            try {
                connectivityManager.bindProcessToNetwork(network)
                lastWifiNetwork = network
                println("DEBUG: Found and bound to WiFi network: $network")
                println("DEBUG: Network capabilities: $bestNetworkCaps")
                
                // Verify binding worked
                if (verifyWifiBinding()) {
                    return
                } else {
                    println("DEBUG: WiFi binding verification failed, attempting aggressive rebind...")
                    
                    // Aggressive rebind attempt
                    Thread.sleep(1000) // Short delay
                    connectivityManager.bindProcessToNetwork(null)
                    Thread.sleep(500)
                    connectivityManager.bindProcessToNetwork(network)
                    
                    if (verifyWifiBinding()) {
                        println("DEBUG: Aggressive rebind succeeded")
                        return
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Failed to bind to WiFi network: ${e.message}")
            }
        }
        
        println("DEBUG: No WiFi network available for binding or all binding attempts failed")
    }
    
    /**
     * Opens WiFi settings for manual connection
     */
    fun openWifiSettings() {
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            println("DEBUG: ✅ Opened WiFi settings for manual connection")
        } catch (e: Exception) {
            println("DEBUG: ❌ Failed to open WiFi settings: ${e.message}")
        }
    }
    
    /**
     * Restores normal internet access by clearing app network bindings
     * Call this if you need normal internet access after ESP provisioning
     * WARNING: This may make ESP devices temporarily unreachable
     */
    fun restoreInternetAccess() {
        try {
            println("DEBUG: *** CLEARING ALL NETWORK BINDINGS ***")
            
            // Clear the process network binding to allow Android to manage WiFi naturally
            connectivityManager.bindProcessToNetwork(null)
            
            // Clear stored network reference
            lastWifiNetwork = null
            
            println("DEBUG: ✅ Network bindings cleared successfully")
            
            // Give Android time to restore natural network management
            Thread.sleep(2000)
            
            // Verify that Android has restored proper network state
            val activeNetwork = connectivityManager.activeNetwork
            val networkCaps = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            println("DEBUG: Post-cleanup network state:")
            println("DEBUG: Active network: $activeNetwork")
            println("DEBUG: Network capabilities: $networkCaps")
            
            if (activeNetwork != null && networkCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                println("DEBUG: ✅ Android successfully restored WiFi network state")
            } else {
                println("DEBUG: ⚠️ Android network state unclear - may need manual reconnection")
                
                // Try to help Android by briefly toggling WiFi binding to current network
                val allNetworks = connectivityManager.allNetworks
                for (network in allNetworks) {
                    val caps = connectivityManager.getNetworkCapabilities(network)
                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true &&
                        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        
                        println("DEBUG: Attempting to help Android restore WiFi binding...")
                        try {
                            connectivityManager.bindProcessToNetwork(network)
                            Thread.sleep(500)
                            connectivityManager.bindProcessToNetwork(null)
                            println("DEBUG: ✅ Helped Android restore network state")
                            break
                        } catch (e: Exception) {
                            println("DEBUG: Helper binding failed: ${e.message}")
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            println("DEBUG: ⚠️ Failed to clear network bindings: ${e.message}")
            // Even if this fails, Android might still recover automatically
        }
    }
    
    /**
     * Verifies that the current network binding is actually using WiFi
     */
    private fun verifyWifiBinding(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val activeCaps = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            println("DEBUG: Verifying binding - Active network: $activeNetwork")
            println("DEBUG: Verifying binding - Network capabilities: $activeCaps")
            
            val isWifiActive = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            if (isWifiActive) {
                println("DEBUG: ✅ WiFi binding verification SUCCESS - traffic will use WiFi")
            } else {
                println("DEBUG: ❌ WiFi binding verification FAILED - traffic still using cellular/other")
            }
            
            isWifiActive
        } catch (e: Exception) {
            println("DEBUG: WiFi binding verification error: ${e.message}")
            false
        }
    }

    /**
     * Gets current network information for debugging and subnet discovery
     */
    fun getCurrentNetworkInfo(): String {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
            
            val wifiInfo = wifiManager.connectionInfo
            
            var info = "=== NETWORK DEBUG INFO ===\n"
            info += "Active network: $activeNetwork\n"
            info += "Network capabilities: $networkCapabilities\n"
            info += "WiFi SSID: ${wifiInfo?.ssid}\n"
            info += "WiFi IP: ${formatIpAddress(wifiInfo?.ipAddress ?: 0)}\n"
            
            // Get all network interfaces
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in networkInterfaces) {
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    info += "Interface ${networkInterface.name}:\n"
                    for (inetAddress in networkInterface.inetAddresses) {
                        if (!inetAddress.isLoopbackAddress && inetAddress.hostAddress.contains(".")) {
                            info += "  IP: ${inetAddress.hostAddress}\n"
                        }
                    }
                }
            }
            
            info += "Link properties: ${linkProperties?.toString()}\n"
            info += "========================="
            
            println("DEBUG: $info")
            info
        } catch (e: Exception) {
            val error = "Failed to get network info: ${e.message}"
            println("DEBUG: $error")
            error
        }
    }

    /**
     * Gets the current WiFi subnet for ESP device discovery using multiple methods
     */
    fun getCurrentSubnet(): String? {
        return try {
            // Check permissions first
            checkWifiPermissions()
            
            // Method 1: Try WifiManager approach first
            val wifiSubnet = getWifiSubnetFromWifiManager()
            if (wifiSubnet != null) {
                println("DEBUG: WiFi subnet from WifiManager: $wifiSubnet.x")
                return wifiSubnet
            }
            
            // Method 2: Try NetworkInterface approach as fallback
            val networkSubnet = getWifiSubnetFromNetworkInterface()
            if (networkSubnet != null) {
                println("DEBUG: WiFi subnet from NetworkInterface: $networkSubnet.x")
                return networkSubnet
            }
            
            // Method 3: Try ConnectivityManager approach
            val connectivitySubnet = getWifiSubnetFromConnectivity()
            if (connectivitySubnet != null) {
                println("DEBUG: WiFi subnet from ConnectivityManager: $connectivitySubnet.x")
                return connectivitySubnet
            }
            
            println("DEBUG: All WiFi subnet detection methods failed - device may not be connected to WiFi")
            null
        } catch (e: Exception) {
            println("DEBUG: Failed to get current subnet: ${e.message}")
            null
        }
    }
    
    /**
     * Checks if required WiFi permissions are granted
     */
    private fun checkWifiPermissions() {
        val hasWifiPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED
        
        val hasLocationPermission = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        
        println("DEBUG: WiFi permission: $hasWifiPermission")
        println("DEBUG: Location permission: $hasLocationPermission")
        
        if (!hasWifiPermission) {
            println("DEBUG: Missing ACCESS_WIFI_STATE permission")
        }
        
        if (!hasLocationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            println("DEBUG: Missing ACCESS_FINE_LOCATION permission (required on Android 10+)")
        }
    }
    
    /**
     * Method 1: Get WiFi subnet using WifiManager (traditional approach)
     */
    private fun getWifiSubnetFromWifiManager(): String? {
        return try {
            val wifiInfo = wifiManager.connectionInfo
            if (wifiInfo == null) {
                println("DEBUG: WifiInfo is null")
                return null
            }
            
            val ipAddress = wifiInfo.ipAddress
            println("DEBUG: Raw WiFi IP from WifiManager: $ipAddress")
            
            if (ipAddress == 0) {
                println("DEBUG: WiFi IP is 0 - not connected to WiFi or permission issue")
                return null
            }
            
            val formattedIp = formatIpAddress(ipAddress)
            println("DEBUG: Formatted WiFi IP: $formattedIp")
            
            // Validate IP is not localhost or invalid
            if (formattedIp == "0.0.0.0" || formattedIp.startsWith("127.")) {
                println("DEBUG: Invalid WiFi IP detected: $formattedIp")
                return null
            }
            
            val parts = formattedIp.split(".")
            if (parts.size == 4) {
                return "${parts[0]}.${parts[1]}.${parts[2]}"
            }
            
            null
        } catch (e: Exception) {
            println("DEBUG: WifiManager method failed: ${e.message}")
            null
        }
    }
    
    /**
     * Method 2: Get WiFi subnet using NetworkInterface (works on newer Android versions)
     */
    private fun getWifiSubnetFromNetworkInterface(): String? {
        return try {
            val networkInterfaces = NetworkInterface.getNetworkInterfaces()
            for (networkInterface in networkInterfaces) {
                if (networkInterface.isUp && !networkInterface.isLoopback) {
                    println("DEBUG: Checking network interface: ${networkInterface.name}")
                    
                    // Look for WiFi interfaces (common names)
                    val interfaceName = networkInterface.name.lowercase()
                    if (interfaceName.contains("wlan") || interfaceName.contains("wifi")) {
                        for (inetAddress in networkInterface.inetAddresses) {
                            if (!inetAddress.isLoopbackAddress && inetAddress.hostAddress.contains(".")) {
                                val ip = inetAddress.hostAddress
                                println("DEBUG: Found WiFi interface IP: $ip")
                                
                                val parts = ip.split(".")
                                if (parts.size == 4 && !ip.startsWith("127.")) {
                                    return "${parts[0]}.${parts[1]}.${parts[2]}"
                                }
                            }
                        }
                    }
                }
            }
            null
        } catch (e: Exception) {
            println("DEBUG: NetworkInterface method failed: ${e.message}")
            null
        }
    }
    
    /**
     * Method 3: Get WiFi subnet using ConnectivityManager
     */
    private fun getWifiSubnetFromConnectivity(): String? {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            println("DEBUG: Active network: $activeNetwork")
            println("DEBUG: Network capabilities: $networkCapabilities")
            
            // Check if we're actually on WiFi
            if (networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                linkProperties?.linkAddresses?.forEach { linkAddress ->
                    val address = linkAddress.address.hostAddress
                    if (address != null && address.contains(".") && !address.startsWith("127.")) {
                        println("DEBUG: Found WiFi address from ConnectivityManager: $address")
                        val parts = address.split(".")
                        if (parts.size == 4) {
                            return "${parts[0]}.${parts[1]}.${parts[2]}"
                        }
                    }
                }
            } else {
                println("DEBUG: Not connected to WiFi network - using cellular or other transport")
            }
            
            null
        } catch (e: Exception) {
            println("DEBUG: ConnectivityManager method failed: ${e.message}")
            null
        }
    }

    private fun formatIpAddress(ip: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            (ip shr 8) and 0xff,
            (ip shr 16) and 0xff,
            (ip shr 24) and 0xff
        )
    }
} 