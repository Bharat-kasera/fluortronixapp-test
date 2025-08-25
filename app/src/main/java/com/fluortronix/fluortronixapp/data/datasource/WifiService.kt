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
                
                // For target WiFi networks (not ESP), just clear ESP binding and skip reconnection
                if (!isEspDevice) {
                    println("DEBUG: 🔄 Clearing ESP binding - user will manually reconnect to '$ssid'")
                    
                    // Clear ESP binding to restore normal WiFi management
                    try {
                        connectivityManager.bindProcessToNetwork(null)
                        println("DEBUG: ✅ Cleared ESP binding - WiFi management restored to Android")
                    } catch (e: Exception) {
                        println("DEBUG: Failed to clear ESP binding: ${e.message}")
                    }
                    
                    // Give a moment for the binding to clear, then return success
                    GlobalScope.launch {
                        delay(1000) // Brief delay to ensure binding is cleared
                        
                        // Return success - the app will proceed to device discovery
                        // User should manually reconnect to home WiFi first
                        if (continuation.isActive) {
                            println("DEBUG: ✅ ESP binding cleared - ready for manual WiFi reconnection")
                            
                            // Find any available network to return as success result
                            // The network object itself doesn't matter for manual reconnection
                            val activeNetwork = connectivityManager.activeNetwork
                            val availableNetworks = connectivityManager.allNetworks
                            
                            val networkToReturn = activeNetwork ?: availableNetworks.firstOrNull()
                            
                            if (networkToReturn != null) {
                                // Return success with any available network
                                continuation.resume(Result.success(networkToReturn))
                            } else {
                                // This should be extremely rare - no networks available at all
                                // In this case, we'll return failure but with a clear message
                                println("DEBUG: Warning: No networks available - user must manually connect to WiFi")
                                continuation.resume(Result.failure(Exception("ESP binding cleared - please manually connect to WiFi (no networks currently available)")))
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