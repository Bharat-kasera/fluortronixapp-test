package com.fluortronix.fluortronixapp.data.datasource

import com.fluortronix.fluortronixapp.data.models.*
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import retrofit2.Response
import retrofit2.http.*
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import android.net.ConnectivityManager
import dagger.hilt.android.qualifiers.ApplicationContext

// ESP Device API responses
data class ESPDeviceInfoResponse(
    @SerializedName("device_model") val deviceModel: String,
    @SerializedName("firmware_version") val firmwareVersion: String,
    @SerializedName("ip_address") val ipAddress: String,
    @SerializedName("num_sliders") val numSliders: Int,
    @SerializedName("slider_names") val sliderNames: List<String>,
    @SerializedName("is_on") val isOn: Boolean,
    @SerializedName("slider_values") val sliderValues: List<Int>
)

data class ESPCommandResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String,
    @SerializedName("data") val data: Map<String, Any>? = null
)

data class ESPSliderRequest(
    @SerializedName("slider_id") val sliderId: Int,
    @SerializedName("value") val value: Int
)

data class ESPMultiSliderRequest(
    @SerializedName("sliders") val sliders: List<ESPSliderRequest>
)

data class ESPPowerRequest(
    @SerializedName("power") val power: Boolean
)

data class ESPWifiConfigRequest(
    @SerializedName("ssid") val ssid: String,
    @SerializedName("pass") val pass: String
)

// Routine synchronization data classes
data class ESPRoutineData(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("roomId") val roomId: String,
    @SerializedName("hour") val hour: Int,
    @SerializedName("minute") val minute: Int,
    @SerializedName("isEnabled") val isEnabled: Boolean,
    @SerializedName("devicePower") val devicePower: Boolean,
    @SerializedName("daysOfWeek") val daysOfWeek: String,
    @SerializedName("presetName") val presetName: String,
    @SerializedName("sliderPreset") val sliderPreset: List<Int>,
    @SerializedName("createdAt") val createdAt: Long
)

data class ESPRoutineSyncRequest(
    @SerializedName("routines") val routines: List<ESPRoutineData>
)

data class ESPTimeRequest(
    @SerializedName("timestamp") val timestamp: Long
)

// Retrofit API interface for ESP device communication
interface ESPDeviceApi {
    @GET("/api/device/info")
    suspend fun getDeviceInfo(): Response<ESPDeviceInfoResponse>
    

    
    @GET("/data/data.xlsx")
    suspend fun getExcelFile(): Response<okhttp3.ResponseBody>
    
    @GET("/data/device.jpg")
    suspend fun getDeviceImage(): Response<okhttp3.ResponseBody>
    
    @POST("/api/device/power")
    suspend fun setPower(@Body request: ESPPowerRequest): Response<ESPCommandResponse>
    
    @POST("/api/device/slider")
    suspend fun setSliderValue(@Body request: ESPSliderRequest): Response<ESPCommandResponse>
    
    @POST("/api/device/sliders")
    suspend fun setMultipleSliders(@Body request: ESPMultiSliderRequest): Response<ESPCommandResponse>
    
    @GET("/api/device/status")
    suspend fun getStatus(): Response<ESPCommandResponse>

    @POST("/api/prov/config_wifi")
    suspend fun configureWifi(@Body request: ESPWifiConfigRequest): Response<ESPCommandResponse>
    
    // Routine management endpoints
    @GET("/api/routines")
    suspend fun getRoutines(): Response<ESPCommandResponse>
    
    @POST("/api/routines/sync")
    suspend fun syncRoutines(@Body request: ESPRoutineSyncRequest): Response<ESPCommandResponse>
    
    @GET("/api/time")
    suspend fun getTime(): Response<ESPCommandResponse>
    
    @POST("/api/time")
    suspend fun setTime(@Body request: ESPTimeRequest): Response<ESPCommandResponse>
}

@Singleton
class ESPDeviceService @Inject constructor(
    private val wifiService: WifiService,
    private val imageCacheService: ImageCacheService,
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val ESP_PORT = 80
        private const val CONNECTION_TIMEOUT = 5000L
        private const val READ_TIMEOUT = 10000L
        private const val DISCOVERY_CONNECTION_TIMEOUT = 1500L  // Much faster timeout for discovery scanning
        private const val DISCOVERY_READ_TIMEOUT = 2000L
    }
    
    /**
     * Creates a dynamic Retrofit API instance for the given device IP
     */
    private fun createApiForDevice(ipAddress: String, isDiscovery: Boolean = false): ESPDeviceApi {
        val connectTimeout = if (isDiscovery) DISCOVERY_CONNECTION_TIMEOUT else CONNECTION_TIMEOUT
        val readTimeout = if (isDiscovery) DISCOVERY_READ_TIMEOUT else READ_TIMEOUT
        
        val okHttpClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(connectTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(false) // Don't retry for faster discovery
            .addInterceptor(okhttp3.logging.HttpLoggingInterceptor().apply {
                level = if (isDiscovery) {
                    okhttp3.logging.HttpLoggingInterceptor.Level.BASIC // Less verbose for discovery
                } else {
                    okhttp3.logging.HttpLoggingInterceptor.Level.BODY
                }
            })
            .build()
            
        val retrofit = retrofit2.Retrofit.Builder()
            .baseUrl("http://$ipAddress:$ESP_PORT")
            .client(okHttpClient)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            
        return retrofit.create(ESPDeviceApi::class.java)
    }

    /**
     * Sends Wi-Fi credentials to the ESP device during provisioning.
     * This should be called when connected to the device's Soft AP.
     */
    suspend fun provisionDevice(ssid: String, pass: String): Result<String?> {
        // The device in Soft AP mode usually has a static IP, e.g., 192.168.4.1
        val softApIp = "192.168.4.1"
        return try {
            println("DEBUG: Attempting to provision device at $softApIp")
            println("DEBUG: Target SSID: $ssid")
            
            // Test network connectivity first
            println("DEBUG: Testing network connectivity to ESP...")
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            println("DEBUG: Active network: $activeNetwork")
            println("DEBUG: Network capabilities: $networkCapabilities")
            
            val api = createApiForDevice(softApIp)
            val request = ESPWifiConfigRequest(ssid, pass)
            println("DEBUG: Sending POST to /api/prov/config_wifi")
            val response = api.configureWifi(request)
            println("DEBUG: HTTP Response code: ${response.code()}")

            if (response.isSuccessful) {
                val commandResponse = response.body()
                println("DEBUG: Response body: $commandResponse")
                if (commandResponse?.success == true) {
                    println("DEBUG: Provisioning successful - ESP8266 received credentials and will switch networks")
                    Result.success(null) // No IP address provided in acknowledgment response
                } else {
                    println("DEBUG: Provisioning failed: ${commandResponse?.message}")
                    Result.failure(Exception(commandResponse?.message ?: "Failed to configure Wi-Fi"))
                }
            } else {
                println("DEBUG: HTTP request failed with code: ${response.code()}")
                val errorBody = response.errorBody()?.string()
                println("DEBUG: Error body: $errorBody")
                Result.failure(Exception("Failed to send Wi-Fi config: ${response.code()} - $errorBody"))
            }
        } catch (e: Exception) {
            println("DEBUG: Exception in provisionDevice: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Fetches comprehensive device information from ESP device
     */
    suspend fun getDeviceInfo(device: Device, isDiscovery: Boolean = false): Result<Device> {
        return try {
            val ipAddress = device.ipAddress ?: return Result.failure(
                Exception("Device IP address is not available")
            )
            
            val api = createApiForDevice(ipAddress, isDiscovery)
            val response = api.getDeviceInfo()
            
            if (response.isSuccessful) {
                val deviceInfo = response.body() ?: return Result.failure(
                    Exception("Empty response from device")
                )
                
                val updatedDevice = device.copy(
                    deviceModel = deviceInfo.deviceModel,
                    firmwareVersion = deviceInfo.firmwareVersion,
                    numSliders = deviceInfo.numSliders,
                    sliderNames = deviceInfo.sliderNames,
                    isOn = deviceInfo.isOn,
                    sliderValues = deviceInfo.sliderValues,
                    isOnline = true,
                    lastSeen = System.currentTimeMillis()
                )
                
                Result.success(updatedDevice)
            } else {
                Result.failure(Exception("Failed to fetch device info: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    

    
    /**
     * Toggles device power state
     */
    suspend fun toggleDevicePower(device: Device): Result<Boolean> {
        return try {
            val ipAddress = device.ipAddress ?: return Result.failure(
                Exception("Device IP address is not available")
            )
            
            val api = createApiForDevice(ipAddress)
            val request = ESPPowerRequest(!device.isOn)
            val response = api.setPower(request)
            
            if (response.isSuccessful) {
                val commandResponse = response.body()
                if (commandResponse?.success == true) {
                    Result.success(!device.isOn)
                } else {
                    Result.failure(Exception(commandResponse?.message ?: "Failed to toggle power"))
                }
            } else {
                Result.failure(Exception("Failed to send power command: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sets value for a specific slider
     */
    suspend fun setSliderValue(device: Device, sliderId: Int, value: Int): Result<Boolean> {
        return try {
            val ipAddress = device.ipAddress ?: return Result.failure(
                Exception("Device IP address is not available")
            )
            
            if (sliderId >= device.numSliders) {
                return Result.failure(Exception("Invalid slider ID: $sliderId"))
            }
            
            if (value !in 0..255) {
                return Result.failure(Exception("Invalid slider value: $value (must be 0-255)"))
            }
            
            val api = createApiForDevice(ipAddress)
            val request = ESPSliderRequest(sliderId, value)
            val response = api.setSliderValue(request)
            
            if (response.isSuccessful) {
                val commandResponse = response.body()
                if (commandResponse?.success == true) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(commandResponse?.message ?: "Failed to set slider value"))
                }
            } else {
                Result.failure(Exception("Failed to send slider command: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sets multiple slider values simultaneously
     */
    suspend fun setMultipleSliders(device: Device, sliderValues: List<Int>): Result<Boolean> {
        return try {
            val ipAddress = device.ipAddress ?: return Result.failure(
                Exception("Device IP address is not available")
            )
            
            if (sliderValues.size != device.numSliders) {
                return Result.failure(
                    Exception("Slider values count (${sliderValues.size}) doesn't match device sliders (${device.numSliders})")
                )
            }
            
            val sliders = sliderValues.mapIndexed { index, value ->
                if (value !in 0..255) {
                    return Result.failure(Exception("Invalid slider value at index $index: $value"))
                }
                ESPSliderRequest(index, value)
            }
            
            val api = createApiForDevice(ipAddress)
            val request = ESPMultiSliderRequest(sliders)
            val response = api.setMultipleSliders(request)
            
            if (response.isSuccessful) {
                val commandResponse = response.body()
                if (commandResponse?.success == true) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(commandResponse?.message ?: "Failed to set slider values"))
                }
            } else {
                Result.failure(Exception("Failed to send multi-slider command: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Monitors device status with real-time updates including network connectivity validation
     */
    fun monitorDeviceStatus(device: Device): Flow<Device> = flow {
        while (true) {
            try {
                // First validate network connectivity
                val networkValidation = validateDeviceNetworkConnectivity(device)
                if (!networkValidation.isSuccess) {
                    // Device is not reachable due to network issues
                    emit(device.copy(isOnline = false))
                    kotlinx.coroutines.delay(10000) // Wait longer when network is not available
                    continue
                }
                
                // If network validation passes, get device info
                val updatedDevice = getDeviceInfo(device)
                if (updatedDevice.isSuccess) {
                    emit(updatedDevice.getOrThrow())
                } else {
                    // Emit device as offline if communication fails
                    emit(device.copy(isOnline = false))
                }
                
                // Poll every 5 seconds
                kotlinx.coroutines.delay(5000)
            } catch (e: Exception) {
                emit(device.copy(isOnline = false))
                kotlinx.coroutines.delay(10000) // Wait longer on error
            }
        }
    }
    
    /**
     * Tests connectivity to the device
     */
    suspend fun testDeviceConnection(ipAddress: String): Result<Boolean> {
        return try {
            val api = createApiForDevice(ipAddress)
            val response = api.getStatus()
            
            if (response.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("Device not reachable: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Validates if device is reachable on current network
     */
    suspend fun validateDeviceNetworkConnectivity(device: Device): Result<Boolean> {
        return try {
            val ipAddress = device.ipAddress ?: return Result.failure(
                Exception("Device IP address is not available")
            )
            
            println("DEBUG: Validating network connectivity for device ${device.name} (${ipAddress})")
            
            // First check if we're on a WiFi network
            val currentSubnet = wifiService.getCurrentSubnet()
            if (currentSubnet == null) {
                return Result.failure(Exception("Not connected to WiFi network or unable to detect WiFi IP"))
            }
            
            // Check if device IP is in current subnet
            val deviceParts = ipAddress.split(".")
            if (deviceParts.size == 4) {
                val deviceSubnet = "${deviceParts[0]}.${deviceParts[1]}.${deviceParts[2]}"
                println("DEBUG: Comparing subnets - Device: $deviceSubnet.x, Current: $currentSubnet.x")
                
                if (deviceSubnet != currentSubnet) {
                    return Result.failure(Exception("Device is not on current network (Device: $deviceSubnet.x, Current: $currentSubnet.x)"))
                }
            }
            
            // Test actual connectivity
            println("DEBUG: Subnet validation passed, testing device connectivity...")
            testDeviceConnection(ipAddress)
        } catch (e: Exception) {
            println("DEBUG: Network validation failed for device ${device.name}: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Scans network for ESP devices using enhanced parallel scanning strategy
     */
    suspend fun scanForESPDevices(): Result<List<String>> {
        return try {
            // Print current network information for debugging
            wifiService.getCurrentNetworkInfo()
            
            val currentSubnet = wifiService.getCurrentSubnet()
            if (currentSubnet == null) {
                println("DEBUG: No current subnet detected, scanning fallback ranges only")
                return scanFallbackRanges()
            }
            
            println("DEBUG: Enhanced scanning for subnet: $currentSubnet.x")
            
            // Phase 1: Quick scan of common ranges (25 IPs)
            val priorityIPs = buildPriorityIPList(currentSubnet)
            println("DEBUG: Phase 1 - Scanning ${priorityIPs.size} priority IPs...")
            val priorityResults = scanIPsInParallel(priorityIPs)
            
            if (priorityResults.isNotEmpty()) {
                println("DEBUG: Found ${priorityResults.size} ESP device(s) in priority ranges")
                return Result.success(priorityResults)
            }
            
            // Phase 2: Comprehensive subnet scan (up to 100 more IPs)
            println("DEBUG: Phase 2 - No devices in priority ranges, scanning broader subnet...")
            val comprehensiveIPs = buildComprehensiveIPList(currentSubnet, excludeIPs = priorityIPs)
            val comprehensiveResults = scanIPsInParallel(comprehensiveIPs)
            
            if (comprehensiveResults.isNotEmpty()) {
                println("DEBUG: Found ${comprehensiveResults.size} ESP device(s) in comprehensive scan")
                return Result.success(comprehensiveResults)
            }
            
            // Phase 3: Fallback to other network ranges
            println("DEBUG: Phase 3 - No devices found in current subnet, scanning fallback ranges...")
            val fallbackResult = scanFallbackRanges()
            
            Result.success(priorityResults + comprehensiveResults + fallbackResult.getOrElse { emptyList() })
        } catch (e: Exception) {
            println("DEBUG: Exception in scanForESPDevices: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Build priority IP list for quick scanning
     */
    private fun buildPriorityIPList(currentSubnet: String): List<String> {
        return listOf(
            // Router gateway (often .1)
            "$currentSubnet.1",
            // Common DHCP start ranges
            "$currentSubnet.20", "$currentSubnet.21", "$currentSubnet.22", "$currentSubnet.23", "$currentSubnet.24",
            "$currentSubnet.50", "$currentSubnet.51", "$currentSubnet.52", "$currentSubnet.53", "$currentSubnet.54",
            "$currentSubnet.100", "$currentSubnet.101", "$currentSubnet.102", "$currentSubnet.103", "$currentSubnet.104",
            "$currentSubnet.150", "$currentSubnet.151", "$currentSubnet.152", "$currentSubnet.153", "$currentSubnet.154",
            "$currentSubnet.200", "$currentSubnet.201", "$currentSubnet.202", "$currentSubnet.203", "$currentSubnet.204",
            // Common static IP ranges
            "$currentSubnet.2", "$currentSubnet.10", "$currentSubnet.11", "$currentSubnet.12"
        )
    }
    
    /**
     * Build comprehensive IP list covering most DHCP ranges
     */
    private fun buildComprehensiveIPList(currentSubnet: String, excludeIPs: List<String>): List<String> {
        val comprehensiveList = mutableListOf<String>()
        
        // Scan ranges that routers commonly use for DHCP
        val ranges = listOf(
            2..19,      // Early range (some routers)
            25..49,     // Mid-low range
            55..99,     // Mid range
            105..149,   // Mid-high range
            155..199,   // High range
            205..230    // Very high range (some routers go up to 254)
        )
        
        for (range in ranges) {
            for (ip in range) {
                val fullIP = "$currentSubnet.$ip"
                if (fullIP !in excludeIPs) {
                    comprehensiveList.add(fullIP)
                }
            }
        }
        
        // Limit to reasonable number to avoid timeout
        return comprehensiveList.take(100)
    }
    
    /**
     * Scan fallback ranges for different router types
     */
    private suspend fun scanFallbackRanges(): Result<List<String>> {
        val fallbackIPs = listOf(
            // ESP SoftAP mode
            "192.168.4.1", "192.168.4.2",
            // Common home router ranges
            "192.168.1.100", "192.168.1.101", "192.168.1.20", "192.168.1.50",
            "192.168.0.100", "192.168.0.101", "192.168.0.20", "192.168.0.50",
            // Mobile hotspots
            "192.168.43.100", "192.168.43.101", "192.168.43.20", "192.168.43.50",
            // Other common ranges
            "10.0.0.100", "10.0.0.101", "10.0.0.20", "10.0.0.50",
            "172.16.0.100", "172.16.0.101", "172.16.0.20", "172.16.0.50"
        )
        
        val results = scanIPsInParallel(fallbackIPs)
        return Result.success(results)
    }
    
    /**
     * Scans a list of IPs in parallel for much faster discovery
     */
    private suspend fun scanIPsInParallel(ips: List<String>): List<String> {
        return try {
            coroutineScope {
                val results = ips.map { ipAddress ->
                    async {
                        try {
                            val api = createApiForDevice(ipAddress, isDiscovery = true)
                    val response = api.getStatus()
                            
                    if (response.isSuccessful) {
                        // Double-check it's our device
                        val infoResponse = api.getDeviceInfo()
                        if (infoResponse.isSuccessful) {
                            val deviceInfo = infoResponse.body()
                            if (deviceInfo?.deviceModel?.contains("FluorTronix", ignoreCase = true) == true ||
                                deviceInfo?.deviceModel?.contains("ESP8266", ignoreCase = true) == true) {
                                        println("DEBUG: Found ESP device at IP: $ipAddress (${deviceInfo.deviceModel})")
                                        return@async ipAddress
                                    }
                                }
                            }
                            null
                        } catch (e: Exception) {
                            null // Expected for non-ESP devices
                        }
                    }
                }
                
                // Wait for all parallel scans to complete and filter non-null results
                results.awaitAll().filterNotNull()
            }
        } catch (e: Exception) {
            println("DEBUG: Exception in parallel scanning: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Discovers ESP device on the network after provisioning
     */
    suspend fun discoverProvisionedDevice(expectedDeviceName: String? = null): Result<Device?> {
        return try {
            println("DEBUG: Starting device discovery for: $expectedDeviceName")
            
            // Skip mDNS and go straight to IP scanning for reliability
            // mDNS (.local) hostnames often fail on Android
            val scanResult = scanForESPDevices()
            if (scanResult.isSuccess) {
                val espIPs = scanResult.getOrThrow()
                println("DEBUG: Found ESP devices at IPs: $espIPs")
                
                if (espIPs.isNotEmpty()) {
                    // Filter out .local hostnames, use only real IP addresses
                    val realIPs = espIPs.filter { ip ->
                        !ip.contains(".local") && 
                        ip.matches(Regex("\\d+\\.\\d+\\.\\d+\\.\\d+"))
                    }
                    
                    if (realIPs.isNotEmpty()) {
                        val deviceName = expectedDeviceName ?: "FLUO Device"
                        
                        // Try each real IP until we find a working device
                        for (realIP in realIPs) {
                        val deviceInfoResult = getDeviceInfo(Device(
                            id = "discovered_${System.currentTimeMillis()}",
                            name = deviceName,
                                ipAddress = realIP
                            ), isDiscovery = true)
                        
                        if (deviceInfoResult.isSuccess) {
                            val discoveredDevice = deviceInfoResult.getOrThrow()
                            println("DEBUG: Successfully discovered device with real IP: ${discoveredDevice.ipAddress}")
                            return Result.success(discoveredDevice)
                            } else {
                                println("DEBUG: Failed to get device info from $realIP, trying next IP...")
                            }
                        }
                        
                        println("DEBUG: Failed to get device info from any discovered IP")
                    } else {
                        println("DEBUG: Only found .local hostnames, no real IPs discovered")
                    }
                }
            }
            
            Result.failure(Exception("No ESP devices with real IP addresses found on network"))
        } catch (e: Exception) {
            println("DEBUG: Exception in discoverProvisionedDevice: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Try to discover device using mDNS
     */
    private suspend fun tryMDNSDiscovery(expectedDeviceName: String? = null): Result<Device?> {
        return try {
            // Try the mDNS name from ESP8266 code
            val mDnsIp = "fluortronix.local"
            val deviceName = expectedDeviceName ?: "FLUO Device"
            val deviceInfoResult = getDeviceInfo(Device(
                id = "mdns_device",
                name = deviceName,
                ipAddress = mDnsIp
            ), isDiscovery = true)
            
            if (deviceInfoResult.isSuccess) {
                Result.success(deviceInfoResult.getOrThrow())
            } else {
                Result.failure(Exception("mDNS discovery failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download Excel file from ESP device
     */
    suspend fun downloadExcelFile(device: Device): Result<InputStream> {
        return try {
            val ipAddress = device.ipAddress ?: return Result.failure(
                Exception("Device IP address is not available")
            )
            
            val api = createApiForDevice(ipAddress)
            val response = api.getExcelFile()
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    Result.success(responseBody.byteStream())
                } else {
                    Result.failure(Exception("Empty response from device"))
                }
            } else {
                Result.failure(Exception("Failed to download file: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Download and cache device image from ESP device
     * Returns the cached image or downloads it if not available
     */
    suspend fun getDeviceImage(device: Device, forceRefresh: Boolean = false): Result<androidx.compose.ui.graphics.ImageBitmap> {
        return try {
            val deviceId = device.id
            
            // Check if image exists in cache and force refresh is not requested
            if (!forceRefresh && imageCacheService.hasDeviceImage(deviceId)) {
                println("DEBUG: Loading device image from cache for device: ${device.name}")
                val cachedImageResult = imageCacheService.loadDeviceImage(deviceId)
                if (cachedImageResult.isSuccess) {
                    return cachedImageResult
                } else {
                    println("DEBUG: Failed to load cached image, will download fresh: ${cachedImageResult.exceptionOrNull()?.message}")
                }
            }
            
            // Download image from device
            val downloadResult = downloadDeviceImageFromESP(device)
            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Image download failed"))
            }
            
            val imageStream = downloadResult.getOrThrow()
            
            // Save to cache
            val saveResult = imageCacheService.saveDeviceImage(deviceId, imageStream)
            if (saveResult.isFailure) {
                println("DEBUG: Failed to save device image to cache: ${saveResult.exceptionOrNull()?.message}")
                // Continue anyway, we can still return the downloaded image
            }
            
            // Load the saved image from cache
            val loadResult = imageCacheService.loadDeviceImage(deviceId)
            if (loadResult.isSuccess) {
                println("DEBUG: Device image downloaded and cached successfully for: ${device.name}")
                loadResult
            } else {
                Result.failure(Exception("Failed to load saved device image: ${loadResult.exceptionOrNull()?.message}"))
            }
            
        } catch (e: Exception) {
            println("DEBUG: Exception in getDeviceImage for ${device.name}: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Download device image from ESP device
     * Private helper method for downloading the raw image stream
     */
    private suspend fun downloadDeviceImageFromESP(device: Device): Result<InputStream> {
        return try {
            val ipAddress = device.ipAddress ?: return Result.failure(
                Exception("Device IP address is not available")
            )
            
            println("DEBUG: Downloading device image from ESP at $ipAddress")
            
            val api = createApiForDevice(ipAddress)
            val response = api.getDeviceImage()
            
            if (response.isSuccessful) {
                val responseBody = response.body()
                if (responseBody != null) {
                    val contentLength = responseBody.contentLength()
                    println("DEBUG: Device image download successful, size: $contentLength bytes")
                    
                    // Validate that we received an image (basic check)
                    if (contentLength > 0) {
                        Result.success(responseBody.byteStream())
                    } else {
                        Result.failure(Exception("Downloaded image is empty"))
                    }
                } else {
                    Result.failure(Exception("Empty response body from device"))
                }
            } else {
                val errorMsg = when (response.code()) {
                    404 -> "Device image not found on ESP device (device.jpg missing)"
                    500 -> "ESP device internal error while accessing image"
                    503 -> "ESP device temporarily unavailable"
                    else -> "Failed to download device image: HTTP ${response.code()}"
                }
                println("DEBUG: Device image download failed: $errorMsg")
                Result.failure(Exception(errorMsg))
            }
        } catch (e: java.net.ConnectException) {
            Result.failure(Exception("Cannot connect to device - check network connection"))
        } catch (e: java.net.SocketTimeoutException) {
            Result.failure(Exception("Device image download timeout - device may be busy"))
        } catch (e: Exception) {
            println("DEBUG: Exception downloading device image: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Clear cached device image
     */
    suspend fun clearDeviceImageCache(deviceId: String): Result<Boolean> {
        return imageCacheService.deleteDeviceImage(deviceId)
    }

    /**
     * Batch update multiple slider values efficiently for real-time control
     */
    suspend fun batchUpdateSliders(
        device: Device,
        sliderUpdates: Map<Int, Int>
    ): Result<Boolean> {
        return try {
            val ipAddress = device.ipAddress ?: return Result.failure(
                Exception("Device IP address is not available")
            )
            
            if (sliderUpdates.isEmpty()) {
                return Result.success(true)
            }
            
            // Validate all slider values
            for ((index, value) in sliderUpdates) {
                if (index < 0 || index >= device.numSliders) {
                    return Result.failure(Exception("Invalid slider index: $index"))
                }
                if (value !in 0..255) {
                    return Result.failure(Exception("Invalid slider value: $value"))
                }
            }
            
            // Convert to ESPSliderRequest list
            val sliders = sliderUpdates.map { (index, value) ->
                ESPSliderRequest(index, value)
            }
            
            val api = createApiForDevice(ipAddress)
            val request = ESPMultiSliderRequest(sliders)
            val response = api.setMultipleSliders(request)
            
            if (response.isSuccessful) {
                val commandResponse = response.body()
                if (commandResponse?.success == true) {
                    Result.success(true)
                } else {
                    Result.failure(Exception(commandResponse?.message ?: "Failed to update sliders"))
                }
            } else {
                Result.failure(Exception("Failed to send batch slider update: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Quick connection test for real-time control setup
     */
    suspend fun testConnection(device: Device): Result<Boolean> {
        return try {
            val result = getDeviceInfo(device)
            Result.success(result.isSuccess)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send optimized slider value for real-time control with retry
     */
    suspend fun setSliderValueOptimized(
        device: Device, 
        sliderId: Int, 
        value: Int,
        retryCount: Int = 1
    ): Result<Boolean> {
        repeat(retryCount + 1) { attempt ->
            val result = setSliderValue(device, sliderId, value)
            if (result.isSuccess) {
                return result
            }
            
            // If this is not the last attempt, wait briefly before retry
            if (attempt < retryCount) {
                kotlinx.coroutines.delay(100)
            }
        }
        
        return setSliderValue(device, sliderId, value)
    }

        /**
     * Synchronize routines to an ESP device with retry mechanism
     */
suspend fun syncRoutinesToDevice(
        ipAddress: String, 
        routines: List<com.fluortronix.fluortronixapp.data.models.Routine>,
        retryCount: Int = 2
    ): Result<String> {
        var lastException: Exception? = null
        
        repeat(retryCount + 1) { attempt ->
            try {
                println("DEBUG: Syncing ${routines.size} routines to device at $ipAddress (attempt ${attempt + 1}/${retryCount + 1})")
                
                val api = createApiForDevice(ipAddress)
                
                // Convert app routines to ESP format
                val espRoutines = routines.map { routine ->
                    println("DEBUG: Converting routine '${routine.name}' to ESP format")
                    println("DEBUG: Raw slider values: ${routine.sliderValues}")
                    val pwmValues = routine.getSliderValuesAsPWM()
                    println("DEBUG: Converted PWM values: $pwmValues")
                    
                    ESPRoutineData(
                        id = routine.id.toString(),
                        name = routine.name,
                        roomId = routine.targetId,
                        hour = routine.getHour(),
                        minute = routine.getMinute(),
                        isEnabled = routine.isEnabled,
                        devicePower = routine.devicePower,
                        daysOfWeek = routine.getDaysAsBinaryString(),
                        presetName = routine.spectrumPresetName ?: "Custom",
                        sliderPreset = pwmValues,
                        createdAt = routine.createdAt
                    )
                }
                
                val request = ESPRoutineSyncRequest(espRoutines)
                println("DEBUG: Sending routine sync request to $ipAddress")
                
                val response = api.syncRoutines(request)
                println("DEBUG: Routine sync response code: ${response.code()}")
                
                if (response.isSuccessful) {
                    val commandResponse = response.body()
                    if (commandResponse?.success == true) {
                        println("DEBUG: Successfully synced routines to $ipAddress")
                        return Result.success("Routines synced successfully")
                    } else {
                        val error = commandResponse?.message ?: "Failed to sync routines"
                        println("DEBUG: Routine sync failed: $error")
                        lastException = Exception(error)
                        
                        // Don't retry for application-level errors
                        if (error.contains("Too many routines") || error.contains("Invalid")) {
                            return Result.failure(lastException!!)
                        }
                    }
                } else {
                    val error = "HTTP ${response.code()}: ${response.message()}"
                    println("DEBUG: Routine sync HTTP error: $error")
                    lastException = Exception(error)
                    
                    // Don't retry for client errors (4xx)
                    if (response.code() in 400..499) {
                        return Result.failure(lastException!!)
                    }
                }
            } catch (e: Exception) {
                println("DEBUG: Exception during routine sync to $ipAddress (attempt ${attempt + 1}): ${e.message}")
                lastException = e
                
                // Don't retry for certain exceptions
                if (e is java.net.UnknownHostException || e.message?.contains("timeout") == false) {
                    // Network structure issues, don't retry
                    return Result.failure(e)
                }
            }
            
            // Wait before retry (except on last attempt)
            if (attempt < retryCount) {
                val delayMs = (attempt + 1) * 1000L // Progressive delay: 1s, 2s, 3s...
                println("DEBUG: Waiting ${delayMs}ms before retry...")
                kotlinx.coroutines.delay(delayMs)
            }
        }
        
        return Result.failure(lastException ?: Exception("Unknown error during routine sync"))
    }

    /**
     * Synchronize current time to an ESP device
     */
    suspend fun syncTimeToDevice(ipAddress: String): Result<String> {
        return try {
            println("DEBUG: Syncing time to device at $ipAddress")
            
            val api = createApiForDevice(ipAddress)
            val currentTime = System.currentTimeMillis() / 1000 // Convert to seconds
            val request = ESPTimeRequest(currentTime)
            
            println("DEBUG: Sending time sync request to $ipAddress (timestamp: $currentTime)")
            
            val response = api.setTime(request)
            println("DEBUG: Time sync response code: ${response.code()}")
            
            if (response.isSuccessful) {
                val commandResponse = response.body()
                if (commandResponse?.success == true) {
                    println("DEBUG: Successfully synced time to $ipAddress")
                    Result.success("Time synced successfully")
                } else {
                    val error = commandResponse?.message ?: "Failed to sync time"
                    println("DEBUG: Time sync failed: $error")
                    Result.failure(Exception(error))
                }
            } else {
                val error = "HTTP ${response.code()}: ${response.message()}"
                println("DEBUG: Time sync HTTP error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            println("DEBUG: Exception during time sync to $ipAddress: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Get device time status
     */
    suspend fun getDeviceTime(ipAddress: String): Result<Map<String, Any>> {
        return try {
            println("DEBUG: Getting time from device at $ipAddress")
            
            val api = createApiForDevice(ipAddress)
            val response = api.getTime()
            
            if (response.isSuccessful) {
                val commandResponse = response.body()
                if (commandResponse?.success == true) {
                    val timeData = commandResponse.data ?: emptyMap()
                    println("DEBUG: Successfully got time from $ipAddress: $timeData")
                    Result.success(timeData)
                } else {
                    val error = commandResponse?.message ?: "Failed to get time"
                    println("DEBUG: Get time failed: $error")
                    Result.failure(Exception(error))
                }
            } else {
                val error = "HTTP ${response.code()}: ${response.message()}"
                println("DEBUG: Get time HTTP error: $error")
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            println("DEBUG: Exception during get time from $ipAddress: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Synchronize routines to all devices in a room
     */
    suspend fun syncRoutinesToRoom(
        devices: List<com.fluortronix.fluortronixapp.data.models.Device>, 
        routines: List<com.fluortronix.fluortronixapp.data.models.Routine>
    ): Result<Map<String, String>> {
        println("DEBUG: Starting room routine sync for ${devices.size} devices with ${routines.size} routines")
        
        val results = mutableMapOf<String, String>()
        
        for (device in devices) {
            if (device.ipAddress != null && device.isOnline) {
                try {
                    // First sync time to ensure accurate scheduling
                    val timeResult = syncTimeToDevice(device.ipAddress)
                    if (timeResult.isFailure) {
                        println("DEBUG: Failed to sync time to ${device.name}: ${timeResult.exceptionOrNull()?.message}")
                        results[device.id] = "Time sync failed: ${timeResult.exceptionOrNull()?.message}"
                        continue
                    }
                    
                    // Then sync routines
                    val routineResult = syncRoutinesToDevice(device.ipAddress, routines)
                    if (routineResult.isSuccess) {
                        results[device.id] = "Success"
                        println("DEBUG: Successfully synced routines to ${device.name}")
                    } else {
                        results[device.id] = "Failed: ${routineResult.exceptionOrNull()?.message}"
                        println("DEBUG: Failed to sync routines to ${device.name}: ${routineResult.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    results[device.id] = "Exception: ${e.message}"
                    println("DEBUG: Exception syncing to ${device.name}: ${e.message}")
                }
            } else {
                results[device.id] = "Device offline or no IP address"
                println("DEBUG: Skipping ${device.name} - offline or no IP")
            }
        }
        
        val successCount = results.values.count { it == "Success" }
        val totalCount = devices.size
        
        return if (successCount > 0) {
            Result.success(results)
        } else {
            Result.failure(Exception("Failed to sync routines to any device in the room"))
        }
    }
} 