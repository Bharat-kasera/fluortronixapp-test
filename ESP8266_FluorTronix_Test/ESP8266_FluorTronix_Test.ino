/*
 * FluorTronix ESP8266 LED Controller
 * 
 * This firmware controls a 6-channel LED grow light system with:
 * - WiFi provisioning and connection management
 * - REST API for remote control
 * - Automated scheduling/routines with time sync
 * - EEPROM storage for settings and routines
 * - PWM control of LED channels
 * 
 * Hardware: ESP8266 with 6 PWM outputs for LED drivers
 * API Base: /api/device/ and /api/routines/
 */

#include <ESP8266WiFi.h>
#include <ESP8266WebServer.h>
#include <ESP8266mDNS.h>
#include <EEPROM.h>
#include <ArduinoJson.h>
#include <FS.h>
#include <time.h>

// Hardware Configuration - PWM pins for LED channels (avoiding GPIO2 built-in LED)
const int LED_PINS[] = {5, 4, 0, 15, 14, 12};
const int NUM_SLIDERS = 6;

// Device Identity
String deviceModel = "FluorTronix-FLUO-Model-01";
String firmwareVersion = "1.0.0";
String sliderNames[] = {"Channel 1", "Channel 2", "Channel 3", "Channel 4", "Channel 5", "Channel 6"};

// Device State Management
bool isDeviceOn = false;
int sliderValues[NUM_SLIDERS] = {0, 0, 0, 0, 0, 0};
int previousSliderValues[NUM_SLIDERS] = {0, 0, 0, 0, 0, 0}; // Restored when powering back on

// Routine/Schedule Structure - Stores automated lighting schedules
struct ESPRoutine {
  uint8_t id;                              // Unique routine identifier
  char name[32];                           // Human-readable routine name
  uint8_t hour;                            // Execution hour (0-23)
  uint8_t minute;                          // Execution minute (0-59)
  uint8_t days;                            // Active days bitmask: bit0=MON, bit1=TUE...bit6=SUN
  bool isEnabled;                          // Whether routine is active
  bool isOffRoutine;                       // true=power OFF, false=apply preset
  uint8_t sliderValues[NUM_SLIDERS];       // LED channel values for preset routines
  char presetName[32];                     // Name of the preset being applied
  uint32_t createdAt;                      // Creation timestamp
};

// Routine Management
const int MAX_ROUTINES = 6;
ESPRoutine loadedRoutines[MAX_ROUTINES];
int routineCount = 0;
unsigned long lastRoutineCheck = 0;       // Prevents routine spam-execution

// EEPROM Memory Layout:
// 0-255:     WiFi credentials (256 bytes)
// 256-447:   Slider names (192 bytes = 6 channels × 32 bytes)
// 448+:      Routines (variable size based on routine count)
const int ROUTINE_EEPROM_START = 448;
const int ROUTINE_SIZE = sizeof(ESPRoutine);

// Time Management
unsigned long deviceStartTime = 0;
bool timeInitialized = false;             // NTP sync status
struct tm timeinfo;

// Network Configuration
String savedSSID = "";
String savedPassword = "";
ESP8266WebServer server(80);

// Access Point for WiFi provisioning (SSID generated from MAC address)
String AP_SSID = "";
const char* AP_PASSWORD = "12345678";

/*
 * SETUP - Device Initialization Sequence
 * 1. Hardware setup (Serial, LEDs, EEPROM, SPIFFS)
 * 2. Generate unique AP SSID from MAC address
 * 3. Configure NTP for time synchronization
 * 4. Load saved configuration from EEPROM
 * 5. Attempt WiFi connection or start provisioning AP
 */
void setup() {
  Serial.begin(115200);
  delay(1000);
  
  Serial.println("\n=== FluorTronix ESP8266 Test Device ===");
  Serial.println("Device Model: " + deviceModel);
  
  // Create unique AP SSID using last 6 digits of MAC address
  String mac = WiFi.macAddress();
  mac.replace(":", "");
  String macSuffix = mac.substring(6);
  AP_SSID = "FLUO-" + macSuffix;
  
  Serial.println("Device MAC: " + WiFi.macAddress());
  Serial.println("Generated AP SSID: " + AP_SSID);
  
  // Initialize hardware: Set all LED outputs to OFF state
  for (int i = 0; i < NUM_SLIDERS; i++) {
    pinMode(LED_PINS[i], OUTPUT);
    analogWrite(LED_PINS[i], 0);
  }
  
  // Initialize storage systems
  EEPROM.begin(2048);
  
  if (!SPIFFS.begin()) {
    Serial.println("SPIFFS initialization failed!");
  } else {
    Serial.println("SPIFFS initialized successfully");
  }
  
  // Configure NTP for IST timezone (+5:30 = 19800 seconds offset)
  deviceStartTime = millis();
  configTime(19800, 0, "pool.ntp.org", "time.nist.gov");
  Serial.println("Waiting for NTP time sync (IST +5:30)...");
  
  // Load all saved configuration from EEPROM
  loadWiFiCredentials();
  loadSliderNames();
  loadRoutines();
  
  // Attempt connection to saved WiFi network
  if (savedSSID.length() > 0) {
    Serial.println("Attempting to connect to saved WiFi: " + savedSSID);
    WiFi.begin(savedSSID.c_str(), savedPassword.c_str());
    
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 20) {
      delay(500);
      Serial.print(".");
      attempts++;
    }
    
    if (WiFi.status() == WL_CONNECTED) {
      Serial.println("\nConnected to WiFi!");
      Serial.println("IP address: " + WiFi.localIP().toString());
      startWebServer();
      return;
    } else {
      Serial.println("\nFailed to connect to saved WiFi: " + savedSSID);
      Serial.println("WiFi Status: " + String(WiFi.status()));
    }
  } else {
    Serial.println("No saved WiFi credentials found");
  }
  
  // Fallback: Start WiFi provisioning mode
  Serial.println("\nStarting SoftAP for provisioning...");
  startSoftAP();
}

/*
 * MAIN LOOP - Device Operation Cycle
 * 1. Handle incoming HTTP requests
 * 2. Update mDNS in station mode
 * 3. Monitor and sync time via NTP
 * 4. Execute scheduled routines (check every minute)
 * 5. Monitor WiFi connection status changes
 */
void loop() {
  // Process incoming HTTP API requests
  server.handleClient();
  if (WiFi.getMode() == WIFI_STA) {
    MDNS.update();
  }
  
  // Check for NTP time synchronization (only when WiFi connected)
  if (WiFi.status() == WL_CONNECTED && !timeInitialized) {
    time_t now = time(nullptr);
    if (now > 8 * 3600 * 2) { // Validate time is reasonable (post-1970 + buffer)
      timeInitialized = true;
      Serial.println("Time synchronized!");
      Serial.println("Current time: " + String(ctime(&now)));
    }
  }
  
  // Routine execution scheduler (runs every 60 seconds)
  if (timeInitialized && (millis() - lastRoutineCheck) >= 60000) {
    lastRoutineCheck = millis();
    
    if (routineCount > 0) {
      // Verbose logging when routines are configured
      time_t now;
      time(&now);
      struct tm* timeinfo = localtime(&now);
      Serial.printf("=== ROUTINE CHECK ===\n");
      Serial.printf("Current time: %02d:%02d:%02d\n", timeinfo->tm_hour, timeinfo->tm_min, timeinfo->tm_sec);
      Serial.printf("Day of week: %d (0=Sun, 1=Mon...6=Sat)\n", timeinfo->tm_wday);
      Serial.printf("Loaded routines: %d\n", routineCount);
      
      checkAndExecuteRoutines();
      Serial.println("=====================");
    } else {
      // Silent check when no routines exist
      checkAndExecuteRoutines();
    }
  }
  
  // WiFi connection state monitoring and logging
  static bool wasWifiConnected = true;
  bool isWifiConnected = (WiFi.status() == WL_CONNECTED);
  
  if (wasWifiConnected && !isWifiConnected) {
    Serial.println("=== WiFi DISCONNECTED ===");
    Serial.println("Routines will continue to execute using stored time");
    Serial.println("API endpoints will be unavailable until reconnection");
    Serial.println("========================");
  } else if (!wasWifiConnected && isWifiConnected) {
    Serial.println("=== WiFi RECONNECTED ===");
    Serial.println("API endpoints are now available");
    Serial.println("Time will be re-synchronized on next NTP update");
    Serial.println("========================");
  }
  
  wasWifiConnected = isWifiConnected;
}

/*
 * Start WiFi Access Point for initial device provisioning
 * Creates a temporary WiFi network for configuration
 */
void startSoftAP() {
  WiFi.mode(WIFI_AP);
  WiFi.softAP(AP_SSID.c_str(), AP_PASSWORD);
  
  Serial.println("SoftAP started");
  Serial.println("SSID: " + AP_SSID);
  Serial.println("Password: " + String(AP_PASSWORD));
  Serial.println("IP address: " + WiFi.softAPIP().toString());
  
  setupAPIRoutes();
  server.begin();
  Serial.println("Provisioning server started on port 80");
}

/*
 * Start main web server after WiFi connection
 * Enables mDNS discovery and all API endpoints
 */
void startWebServer() {
  Serial.println("=== STARTING WEB SERVER ===");
  
  // Clean restart of web server
  server.stop();
  delay(500);
  
  setupAPIRoutes();
  
  server.begin();
  delay(500);
  
  // Enable mDNS for easy discovery (fluortronix.local)
  if (MDNS.begin("fluortronix")) {
    Serial.println("mDNS responder started - Device discoverable as fluortronix.local");
    MDNS.addService("http", "tcp", 80);
  } else {
    Serial.println("mDNS failed to start");
  }
  
  // Display available API endpoints
  Serial.println("HTTP server started on port 80");
  Serial.println("API endpoints ready:");
  Serial.println("  - GET  /api/device/info");
  Serial.println("  - GET  /api/device/status");
  Serial.println("  - POST /api/device/power");
  Serial.println("  - POST /api/device/slider");
  Serial.println("  - GET  /api/routines");
  Serial.println("Device IP: " + WiFi.localIP().toString());
  Serial.println("============================");
}

/*
 * Configure all HTTP API routes and CORS handling
 * Sets up REST endpoints for device control, configuration, and monitoring
 */
void setupAPIRoutes() {
  // Handle CORS preflight requests and 404s
  server.onNotFound([]() {
    if (server.method() == HTTP_OPTIONS) {
      server.sendHeader("Access-Control-Allow-Origin", "*");
      server.sendHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
      server.sendHeader("Access-Control-Allow-Headers", "Content-Type");
      server.send(200);
    } else {
      server.send(404, "text/plain", "Not found");
    }
  });

  // Device Information & Status APIs
  server.on("/api/device/info", HTTP_GET, handleDeviceInfo);          // Get device details & current state
  server.on("/api/device/status", HTTP_GET, handleStatus);            // System health check
  server.on("/api/device/spd", HTTP_GET, handleSPDFile);              // Spectral Power Distribution data
  server.on("/data/data.xlsx", HTTP_GET, handleExcelFile);            // Excel data file download
  
  // Device Control APIs
  server.on("/api/device/power", HTTP_POST, handlePowerControl);      // Turn device on/off
  server.on("/api/device/slider", HTTP_POST, handleSliderControl);    // Control single LED channel
  server.on("/api/device/sliders", HTTP_POST, handleMultipleSliders); // Control multiple channels at once
  
  // Configuration APIs
  server.on("/api/prov/config_wifi", HTTP_POST, handleWiFiConfig);    // WiFi provisioning
  server.on("/api/device/slider_names", HTTP_POST, handleSliderNamesConfig); // Rename LED channels
  
  // Routine/Schedule Management APIs
  server.on("/api/routines/sync", HTTP_POST, handleRoutineSync);      // Upload routines from app
  server.on("/api/routines", HTTP_GET, handleGetRoutines);            // Get current routines
  server.on("/api/time", HTTP_POST, handleTimeSync);                  // Manual time synchronization
  
  server.on("/", HTTP_GET, []() {
    String html = "<h1>FluorTronix ESP Device</h1>";
    html += "<p>Device Model: " + deviceModel + "</p>";
    html += "<p>Firmware: " + firmwareVersion + "</p>";
    html += "<p>Status: " + String(isDeviceOn ? "ON" : "OFF") + "</p>";
    html += "<p>IP: " + WiFi.localIP().toString() + "</p>";
    html += "<p>Time Sync: " + String(timeInitialized ? "YES" : "NO") + "</p>";
    html += "<h3>PWM Channel Configuration:</h3>";
    for (int i = 0; i < NUM_SLIDERS; i++) {
      html += "<p>" + String(sliderNames[i]) + ": " + String(sliderValues[i]) + "/255</p>";
    }
    html += "<h3>Routine Information:</h3>";
    html += "<p>Routines loaded: " + String(routineCount) + "/" + String(MAX_ROUTINES) + "</p>";
    for (int i = 0; i < routineCount; i++) {
      html += "<p>" + String(i+1) + ". " + String(loadedRoutines[i].name) + " - ";
      html += String(loadedRoutines[i].hour) + ":" + 
              String(loadedRoutines[i].minute < 10 ? "0" : "") + String(loadedRoutines[i].minute);
      html += " (" + String(loadedRoutines[i].isOffRoutine ? "OFF" : "PRESET") + ")";
      html += " [" + String(loadedRoutines[i].isEnabled ? "ENABLED" : "DISABLED") + "]</p>";
    }
    html += "<h3>API Endpoints:</h3>";
    html += "<p><a href='/api/device/info'>Device Info JSON</a></p>";
    html += "<p><a href='/api/routines'>Routines JSON</a></p>";
    html += "<p>POST /api/device/power - Toggle device power</p>";
    html += "<p>POST /api/device/slider - Set single slider</p>";
    html += "<p>POST /api/device/sliders - Set multiple sliders</p>";
    html += "<p>POST /api/device/slider_names - Update slider names</p>";
    html += "<p>POST /api/routines/sync - Sync routines from app</p>";
    html += "<p>POST /api/time - Sync time from app</p>";
    server.send(200, "text/html", html);
  });
}

void handleDeviceInfo() {
  addCORSHeaders();
  
  DynamicJsonDocument doc(1024);
  doc["device_model"] = deviceModel;
  doc["firmware_version"] = firmwareVersion;
  doc["ip_address"] = WiFi.localIP().toString();
  doc["num_sliders"] = NUM_SLIDERS;
  doc["is_on"] = isDeviceOn;
  
  JsonArray sliderNamesArray = doc.createNestedArray("slider_names");
  JsonArray sliderValuesArray = doc.createNestedArray("slider_values");
  
  for (int i = 0; i < NUM_SLIDERS; i++) {
    sliderNamesArray.add(sliderNames[i]);
    sliderValuesArray.add(sliderValues[i]);
  }
  
  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
}

void handleExcelFile() {
  addCORSHeaders();
  
  Serial.println("Excel file requested: /data/data.xlsx");
  
  if (SPIFFS.exists("/data.xlsx")) {
    File file = SPIFFS.open("/data.xlsx", "r");
    if (file) {
      Serial.println("Serving Excel file from SPIFFS");
      
      server.sendHeader("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      server.sendHeader("Content-Disposition", "attachment; filename=data.xlsx");
      
      server.streamFile(file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
      file.close();
      
      Serial.println("Excel file sent successfully");
    } else {
      Serial.println("Failed to open Excel file from SPIFFS");
      server.send(500, "text/plain", "Failed to open Excel file");
    }
  } else {
    Serial.println("Excel file not found in SPIFFS");
    server.send(404, "text/plain", "Excel file not found. Please upload data.xlsx to SPIFFS using Arduino IDE data upload tool.");
  }
}

void handleSPDFile() {
  addCORSHeaders();
  
  String spdData = "Wavelength,Intensity\n";
  
  // Generate sample SPD data for grow light spectrum
  for (int wavelength = 380; wavelength <= 750; wavelength += 10) {
    float intensity;
    if (wavelength < 420) {
      intensity = 0.1; // Low UV
    } else if (wavelength < 480) {
      intensity = 0.8; // Blue peak
    } else if (wavelength < 550) {
      intensity = 0.6; // Green
    } else if (wavelength < 600) {
      intensity = 0.7; // Yellow
    } else if (wavelength < 660) {
      intensity = 1.0; // Red peak
    } else {
      intensity = 0.3; // Far red
    }
    
    if (isDeviceOn) {
      if (wavelength > 620 && sliderValues[0] > 0) { // Red slider
        intensity *= (sliderValues[0] / 255.0);
      }
      if (wavelength >= 495 && wavelength <= 570 && sliderValues[1] > 0) { // Green slider
        intensity *= (sliderValues[1] / 255.0);
      }
      if (wavelength < 495 && sliderValues[2] > 0) { // Blue slider
        intensity *= (sliderValues[2] / 255.0);
      }
    } else {
      intensity = 0;
    }
    
    spdData += String(wavelength) + "," + String(intensity, 3) + "\n";
  }
  
  server.sendHeader("Content-Disposition", "attachment; filename=spd_data.csv");
  server.send(200, "text/csv", spdData);
  
  Serial.println("SPD file requested");
}

void handlePowerControl() {
  addCORSHeaders();
  
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"No data received\"}");
    return;
  }
  
  DynamicJsonDocument doc(256);
  deserializeJson(doc, server.arg("plain"));
  
  if (doc.containsKey("power")) {
    bool newPowerState = doc["power"];
    
    if (!newPowerState && isDeviceOn) {
      // Turning OFF - store current values as previous values
      for (int i = 0; i < NUM_SLIDERS; i++) {
        previousSliderValues[i] = sliderValues[i];
        sliderValues[i] = 0; // Set all to 0 when turning off
      }
      Serial.println("Power OFF - Previous values stored");
    } else if (newPowerState && !isDeviceOn) {
      // Turning ON - restore previous values if they exist
      bool hasPreviousValues = false;
      for (int i = 0; i < NUM_SLIDERS; i++) {
        if (previousSliderValues[i] > 0) {
          hasPreviousValues = true;
          break;
        }
      }
      
      if (hasPreviousValues) {
        for (int i = 0; i < NUM_SLIDERS; i++) {
          sliderValues[i] = previousSliderValues[i];
        }
        Serial.println("Power ON - Previous values restored");
      } else {
        Serial.println("Power ON - No previous values to restore");
      }
    }
    
    isDeviceOn = newPowerState;
    Serial.println("Power " + String(isDeviceOn ? "ON" : "OFF"));
    
    updateLEDs();
    
    DynamicJsonDocument response(256);
    response["success"] = true;
    response["message"] = "Power state updated";
    response["data"]["power"] = isDeviceOn;
    
    String responseStr;
    serializeJson(response, responseStr);
    server.send(200, "application/json", responseStr);
  } else {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"Invalid power data\"}");
  }
}

void handleSliderControl() {
  addCORSHeaders();
  
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"No data received\"}");
    return;
  }
  
  DynamicJsonDocument doc(256);
  deserializeJson(doc, server.arg("plain"));
  
  if (doc.containsKey("slider_id") && doc.containsKey("value")) {
    int sliderId = doc["slider_id"];
    int value = doc["value"];
    
    if (sliderId >= 0 && sliderId < NUM_SLIDERS && value >= 0 && value <= 255) {
      sliderValues[sliderId] = value;
      // Update previous values when device is ON (for future power cycles)
      if (isDeviceOn) {
        previousSliderValues[sliderId] = value;
      }
      
      Serial.println("Slider " + String(sliderId) + " (" + sliderNames[sliderId] + ") set to " + String(value));
      
      updateLEDs();
      
      DynamicJsonDocument response(256);
      response["success"] = true;
      response["message"] = "Slider updated";
      
      String responseStr;
      serializeJson(response, responseStr);
      server.send(200, "application/json", responseStr);
    } else {
      server.send(400, "application/json", "{\"success\":false,\"message\":\"Invalid slider data\"}");
    }
  } else {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"Missing slider_id or value\"}");
  }
}

void handleMultipleSliders() {
  addCORSHeaders();
  
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"No data received\"}");
    return;
  }
  
  DynamicJsonDocument doc(1024);
  deserializeJson(doc, server.arg("plain"));
  
  if (doc.containsKey("sliders")) {
    JsonArray sliders = doc["sliders"];
    
    Serial.println("Setting multiple sliders:");
    Serial.println("Received " + String(sliders.size()) + " slider(s) from app");
    
    for (JsonObject slider : sliders) {
      if (slider.containsKey("slider_id") && slider.containsKey("value")) {
        int sliderId = slider["slider_id"];
        int value = slider["value"];
        
        if (sliderId >= 0 && sliderId < NUM_SLIDERS && value >= 0 && value <= 255) {
          sliderValues[sliderId] = value;
          // Update previous values when device is ON (for future power cycles)
          if (isDeviceOn) {
            previousSliderValues[sliderId] = value;
          }
          Serial.println("  " + String(sliderNames[sliderId]) + ": " + String(value));
        }
      }
    }
    
    updateLEDs();
    
    DynamicJsonDocument response(256);
    response["success"] = true;
    response["message"] = "Multiple sliders updated";
    
    String responseStr;
    serializeJson(response, responseStr);
    server.send(200, "application/json", responseStr);
  } else {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"Invalid sliders data\"}");
  }
}

void handleStatus() {
  addCORSHeaders();
  
  DynamicJsonDocument doc(256);
  doc["success"] = true;
  doc["message"] = "Device is online";
  doc["data"]["uptime"] = millis();
  doc["data"]["free_heap"] = ESP.getFreeHeap();
  
  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
}

void handleWiFiConfig() {
  addCORSHeaders();
  
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"No data received\"}");
    return;
  }
  
  DynamicJsonDocument doc(256);
  deserializeJson(doc, server.arg("plain"));
  
  if (doc.containsKey("ssid") && doc.containsKey("pass")) {
    String ssid = doc["ssid"];
    String password = doc["pass"];
    
    Serial.println("=== WIFI CONFIGURATION STARTED ===");
    Serial.println("Received WiFi credentials:");
    Serial.println("SSID: " + ssid);
    Serial.println("Password: [hidden]");
    
    // Save credentials
    saveWiFiCredentials(ssid, password);
    
    // Send immediate response to acknowledge receipt
    DynamicJsonDocument response(256);
    response["success"] = true;
    response["message"] = "WiFi credentials received, attempting connection...";
    
    String responseStr;
    serializeJson(response, responseStr);
    server.send(200, "application/json", responseStr);
    
    Serial.println("Sent acknowledgment response, preparing to switch networks...");
    
    // Give time for the response to be sent before switching modes
    delay(2000);
    
    // Properly stop the current server and clean up SoftAP mode
    Serial.println("Stopping SoftAP server and switching to Station mode...");
    server.stop();
    delay(500);
    
    // Disconnect from SoftAP and switch to Station mode
    WiFi.softAPdisconnect(true);
    delay(500);
    
    // Now try to connect to the new WiFi
    WiFi.mode(WIFI_STA);
    delay(1000); // Give mode switch time to complete
    
    Serial.println("Starting WiFi connection to: " + ssid);
    WiFi.begin(ssid.c_str(), password.c_str());
    
    // Wait for connection (up to 20 seconds)
    int attempts = 0;
    while (WiFi.status() != WL_CONNECTED && attempts < 40) {
      delay(500);
      Serial.print(".");
      attempts++;
      
      // Print detailed status every 10 attempts
      if (attempts % 10 == 0) {
        Serial.println();
        Serial.println("WiFi Status: " + String(WiFi.status()));
        Serial.println("Attempt " + String(attempts) + "/40");
      }
    }
    
    if (WiFi.status() == WL_CONNECTED) {
      String newIP = WiFi.localIP().toString();
      Serial.println("\n=== WIFI CONNECTION SUCCESS ===");
      Serial.println("Connected to WiFi: " + ssid);
      Serial.println("New IP address: " + newIP);
      Serial.println("MAC Address: " + WiFi.macAddress());
      Serial.println("Signal Strength: " + String(WiFi.RSSI()) + " dBm");
      
      // Additional delay to ensure network is fully stable
      delay(2000);
      
      // Start web server on new network
      Serial.println("Starting web server on new network...");
      startWebServer();
      
      Serial.println("=== TRANSITION COMPLETE ===");
      Serial.println("Device is now accessible at: http://" + newIP);
      Serial.println("============================");
    } else {
      Serial.println("\n=== WIFI CONNECTION FAILED ===");
      Serial.println("Failed to connect to WiFi: " + ssid);
      Serial.println("WiFi Status: " + String(WiFi.status()));
      Serial.println("Will restart in 3 seconds to retry...");
      Serial.println("==============================");
      
      // Restart in 3 seconds to try again
      delay(3000);
      ESP.restart();
    }
  } else {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"Missing SSID or password\"}");
  }
}

void handleSliderNamesConfig() {
  addCORSHeaders();
  
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"No data received\"}");
    return;
  }
  
  DynamicJsonDocument doc(1024);
  deserializeJson(doc, server.arg("plain"));
  
  if (doc.containsKey("slider_names")) {
    JsonArray sliderNamesArray = doc["slider_names"];
    
    if (sliderNamesArray.size() == NUM_SLIDERS) {
      Serial.println("Updating slider names:");
      
      for (int i = 0; i < NUM_SLIDERS; i++) {
        String newName = sliderNamesArray[i];
        if (newName.length() > 0 && newName.length() <= 31) {
          sliderNames[i] = newName;
          Serial.println("  " + String(i) + ": " + sliderNames[i]);
        }
      }
      
      // Save to EEPROM
      saveSliderNames();
      
      DynamicJsonDocument response(256);
      response["success"] = true;
      response["message"] = "Slider names updated successfully";
      
      String responseStr;
      serializeJson(response, responseStr);
      server.send(200, "application/json", responseStr);
    } else {
      server.send(400, "application/json", "{\"success\":false,\"message\":\"Slider names array must have exactly " + String(NUM_SLIDERS) + " elements\"}");
    }
  } else {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"Missing slider_names array\"}");
  }
}

/*
 * Update LED PWM outputs based on current device state
 * Applies sliderValues to hardware pins when device is ON
 * Forces all channels to 0 when device is OFF
 */
void updateLEDs() {
  if (isDeviceOn) {
    // Count active channels for logging
    int channelsWithValues = 0;
    for (int i = 0; i < NUM_SLIDERS; i++) {
      if (sliderValues[i] > 0) {
        channelsWithValues++;
      }
    }
    
    // Apply PWM values to all LED driver pins (0-255 range)
    for (int i = 0; i < NUM_SLIDERS; i++) {
      analogWrite(LED_PINS[i], sliderValues[i]);
    }
    
    Serial.println("=== PWM UPDATE ===");
    Serial.println("Device: ON | PWM Output: ACTIVE");
    Serial.println("Active Channels: " + String(channelsWithValues) + "/" + String(NUM_SLIDERS));
    
    for (int i = 0; i < NUM_SLIDERS; i++) {
      Serial.println(sliderNames[i] + ": " + String(sliderValues[i]) + "/255");
    }
    Serial.println("==================");
    } else {
    // Device OFF: Force all channels to zero output
      for (int i = 0; i < NUM_SLIDERS; i++) {
      analogWrite(LED_PINS[i], 0);
    }
    
    Serial.println("=== PWM UPDATE ===");
    Serial.println("Device: OFF | PWM Output: DISABLED");
    Serial.println("==================");
  }
}

/*
 * Add CORS headers to HTTP response for cross-origin web app access
 */
void addCORSHeaders() {
  server.sendHeader("Access-Control-Allow-Origin", "*");
  server.sendHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
  server.sendHeader("Access-Control-Allow-Headers", "Content-Type");
}

/*
 * Convert day index to human-readable name
 * dayIndex: 0=Sunday, 1=Monday, ..., 6=Saturday
 */
String getDayName(int dayIndex) {
  const char* dayNames[] = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
  if (dayIndex >= 0 && dayIndex <= 6) {
    return String(dayNames[dayIndex]);
  }
  return "Unknown";
}

/*
 * Time Range Validation Functions (currently unused but kept for future features)
 * These would be useful for duration-based routines instead of single-time triggers
 */

bool isTimeInRange(int currentHour, int currentMinute, int startHour, int startMinute, int endHour, int endMinute) {
  int currentMinutes = currentHour * 60 + currentMinute;
  int startMinutes = startHour * 60 + startMinute;
  int endMinutes = endHour * 60 + endMinute;
  
  if (endMinutes < startMinutes) {
    // Handle routines spanning midnight (e.g., 22:00 to 06:00)
    return (currentMinutes >= startMinutes || currentMinutes <= endMinutes);
  } else {
    // Normal same-day routine
    return (currentMinutes >= startMinutes && currentMinutes <= endMinutes);
  }
}

bool isStartTime(int currentHour, int currentMinute, int startHour, int startMinute) {
  return (currentHour == startHour && currentMinute == startMinute);
}

bool isEndTime(int currentHour, int currentMinute, int endHour, int endMinute) {
  return (currentHour == endHour && currentMinute == endMinute);
}

void loadSliderNames() {
  int offset = 256;
  
  for (int i = 0; i < NUM_SLIDERS; i++) {
    int nameLength = EEPROM.read(offset);
    offset++;
    
    if (nameLength > 0 && nameLength < 32) {
      String sliderName = "";
      for (int j = 0; j < nameLength; j++) {
        sliderName += char(EEPROM.read(offset));
        offset++;
      }
      sliderNames[i] = sliderName;
    }
    
    offset = 256 + (i + 1) * 32; // 32 bytes per name
  }
}

void saveSliderNames() {
  int offset = 256;
  
  for (int i = 0; i < NUM_SLIDERS; i++) {
    String name = sliderNames[i];
    int nameLength = min((int)name.length(), 31);
    
    EEPROM.write(offset, nameLength);
    offset++;
    
    for (int j = 0; j < nameLength; j++) {
      EEPROM.write(offset, name[j]);
      offset++;
    }
    
    for (int j = nameLength; j < 31; j++) {
      EEPROM.write(offset, 0);
      offset++;
    }
    
    offset = 256 + (i + 1) * 32; // 32 bytes per name
  }
  
  EEPROM.commit();
  Serial.println("Slider names saved to EEPROM");
}

void saveWiFiCredentials(String ssid, String password) {
  for (int i = 0; i < 512; i++) {
    EEPROM.write(i, 0);
  }
  
  EEPROM.write(0, ssid.length());
  for (int i = 0; i < ssid.length(); i++) {
    EEPROM.write(1 + i, ssid[i]);
  }
  
  int passwordStart = 1 + ssid.length();
  EEPROM.write(passwordStart, password.length());
  for (int i = 0; i < password.length(); i++) {
    EEPROM.write(passwordStart + 1 + i, password[i]);
  }
  
  EEPROM.commit();
  
  savedSSID = ssid;
  savedPassword = password;
  
  Serial.println("WiFi credentials saved to EEPROM");
}

void loadWiFiCredentials() {
  Serial.println("Loading WiFi credentials from EEPROM...");
  
  int ssidLength = EEPROM.read(0);
  Serial.println("SSID length from EEPROM: " + String(ssidLength));
  
  if (ssidLength > 0 && ssidLength < 32) {
    savedSSID = "";
    for (int i = 0; i < ssidLength; i++) {
      savedSSID += char(EEPROM.read(1 + i));
    }
    
    int passwordStart = 1 + ssidLength;
    int passwordLength = EEPROM.read(passwordStart);
    Serial.println("Password length from EEPROM: " + String(passwordLength));
    
    if (passwordLength > 0 && passwordLength < 64) {
      savedPassword = "";
      for (int i = 0; i < passwordLength; i++) {
        savedPassword += char(EEPROM.read(passwordStart + 1 + i));
      }
      Serial.println("✅ WiFi credentials loaded successfully");
    Serial.println("SSID: " + savedSSID);
      Serial.println("Password: [" + String(passwordLength) + " chars]");
    } else {
      Serial.println("❌ Invalid password length: " + String(passwordLength));
      savedSSID = "";
      savedPassword = "";
    }
    } else {
    Serial.println("❌ Invalid SSID length: " + String(ssidLength));
    savedSSID = "";
    savedPassword = "";
  }
} 

/*
 * ===== ROUTINE MANAGEMENT FUNCTIONS =====
 * Functions for loading, saving, and executing automated lighting schedules
 * Routines can turn device ON/OFF or apply specific LED presets at scheduled times
 */





/*
 * Load all saved routines from EEPROM with corruption detection
 * Validates routine data and skips corrupted entries
 */
void loadRoutines() {
  Serial.println("Loading routines from EEPROM...");
  
  routineCount = EEPROM.read(ROUTINE_EEPROM_START);
  if (routineCount > MAX_ROUTINES || routineCount < 0) {
    Serial.println("EEPROM corruption detected - invalid routine count: " + String(routineCount));
    Serial.println("Resetting routines to 0 and marking EEPROM area as clean");
    routineCount = 0;
    
    // Clear the routine area in EEPROM to prevent further corruption
    for (int i = ROUTINE_EEPROM_START; i < ROUTINE_EEPROM_START + (MAX_ROUTINES * ROUTINE_SIZE) + 1; i++) {
      EEPROM.write(i, 0);
    }
    EEPROM.commit();
    return;
  }
  
  Serial.println("Found " + String(routineCount) + " routines in EEPROM");
  
  // Load and validate each routine
  int validRoutines = 0;
  for (int i = 0; i < routineCount; i++) {
    int offset = ROUTINE_EEPROM_START + 1 + (i * ROUTINE_SIZE);
    
    // Read routine data
    EEPROM.get(offset, loadedRoutines[validRoutines]);
    
    // Validate routine data
    ESPRoutine& routine = loadedRoutines[validRoutines];
    
    // Check for corruption in critical fields
    bool isCorrupted = false;
    if (routine.hour > 23 || routine.hour < 0) {
      Serial.println("Corrupted routine " + String(i) + " - invalid hour: " + String(routine.hour));
      isCorrupted = true;
    }
    if (routine.minute > 59 || routine.minute < 0) {
      Serial.println("Corrupted routine " + String(i) + " - invalid minute: " + String(routine.minute));
      isCorrupted = true;
    }
    if (routine.days > 127) { // 7-bit mask maximum is 1111111 = 127
      Serial.println("Corrupted routine " + String(i) + " - invalid days mask: " + String(routine.days));
      isCorrupted = true;
    }
    
    // Check if name contains null bytes or invalid characters
    bool nameValid = true;
    for (int j = 0; j < 32; j++) {
      if (j == 31 && routine.name[j] != '\0') {
        nameValid = false;
        break;
      }
      if (routine.name[j] != '\0' && (routine.name[j] < 32 || routine.name[j] > 126)) {
        nameValid = false;
        break;
      }
    }
    
    if (!nameValid) {
      Serial.println("Corrupted routine " + String(i) + " - invalid name");
      isCorrupted = true;
    }
    
    if (isCorrupted) {
      Serial.println("Skipping corrupted routine " + String(i));
      continue;
    }
    
    Serial.println("Loaded routine " + String(validRoutines + 1) + ": " + String(routine.name));
    Serial.println("  Time: " + String(routine.hour) + ":" + 
                   String(routine.minute < 10 ? "0" : "") + String(routine.minute));
    Serial.println("  Type: " + String(routine.isOffRoutine ? "OFF" : "PRESET"));
    Serial.println("  Enabled: " + String(routine.isEnabled ? "YES" : "NO"));
    
    validRoutines++;
  }
  
  // Update routine count to valid routines only
  if (validRoutines != routineCount) {
    Serial.println("EEPROM corruption detected - " + String(routineCount - validRoutines) + " corrupted routines removed");
    routineCount = validRoutines;
    saveRoutines(); // Save the cleaned data back
  }
}

void saveRoutines() {
  Serial.println("Saving " + String(routineCount) + " routines to EEPROM...");
  
  EEPROM.write(ROUTINE_EEPROM_START, routineCount);
  
  for (int i = 0; i < routineCount; i++) {
    int offset = ROUTINE_EEPROM_START + 1 + (i * ROUTINE_SIZE);
    EEPROM.put(offset, loadedRoutines[i]);
  }
  
  EEPROM.commit();
  Serial.println("Routines saved successfully");
}

bool isRoutineDayActive(uint8_t dayMask, int currentDay) {
  // Convert Sunday=0 to our bit mapping (MON=0, TUE=1, ..., SUN=6)
  int bitIndex;
  if (currentDay == 0) {
    bitIndex = 6; // Sunday
  } else {
    bitIndex = currentDay - 1; // Monday=1 becomes 0, etc.
  }
  
  return (dayMask & (1 << bitIndex)) != 0;
}

/*
 * Execute a scheduled routine (either turn OFF or apply preset)
 * Changes device power state and LED values, then updates hardware
 */
void executeRoutine(const ESPRoutine& routine) {
  Serial.println("=== ROUTINE EXECUTED ===");
  Serial.println("Routine: " + String(routine.name));
  Serial.println("Type: " + String(routine.isOffRoutine ? "OFF" : "PRESET"));
  
  if (routine.isOffRoutine) {
    // Turn device OFF and zero all channels
    isDeviceOn = false;
    for (int i = 0; i < NUM_SLIDERS; i++) {
      sliderValues[i] = 0;
    }
    Serial.println("Device turned OFF - All channels set to 0");
  } else {
    // Turn device ON and apply preset LED values
    isDeviceOn = true;
    for (int i = 0; i < NUM_SLIDERS; i++) {
      sliderValues[i] = routine.sliderValues[i];
    }
    Serial.println("Device turned ON - Preset applied: " + String(routine.presetName));
  }
  
  // Apply changes to hardware
  updateLEDs();
  
  Serial.println("========================");
}

/*
 * Check current time against all enabled routines and execute matches
 * Handles routine conflicts by executing all matches (last one wins)
 * Called every minute from main loop when time is synchronized
 */
void checkAndExecuteRoutines() {
  if (routineCount == 0) return;
  
  time_t now;
  time(&now);
  struct tm* timeinfo = localtime(&now);
  
  int currentHour = timeinfo->tm_hour;
  int currentMinute = timeinfo->tm_min;
  int currentDay = timeinfo->tm_wday; // 0=Sunday, 1=Monday, etc.
  
  int matchingRoutines = 0;
  String conflictInfo = "";
  
  // Check each routine for time/day match
  for (int i = 0; i < routineCount; i++) {
    const ESPRoutine& routine = loadedRoutines[i];
    
    if (routine.isEnabled && 
        routine.hour == currentHour && 
        routine.minute == currentMinute &&
        isRoutineDayActive(routine.days, currentDay)) {
      
      matchingRoutines++;
      Serial.printf("*** ROUTINE MATCH FOUND: %s ***\n", routine.name);
      Serial.printf("  Time: %02d:%02d, Type: %s\n", 
                    routine.hour, routine.minute, routine.isOffRoutine ? "OFF" : "PRESET");
      
      if (matchingRoutines == 1) {
        conflictInfo = String(routine.name) + " (" + String(routine.isOffRoutine ? "OFF" : "PRESET") + ")";
      } else {
        conflictInfo += ", " + String(routine.name) + " (" + String(routine.isOffRoutine ? "OFF" : "PRESET") + ")";
      }
      
      executeRoutine(routine);
    }
  }
  
  // Log conflicts if multiple routines triggered simultaneously
  if (matchingRoutines > 1) {
    Serial.println("=== ROUTINE CONFLICT DETECTED ===");
    Serial.println("Multiple routines executed at " + String(currentHour) + ":" + 
                   String(currentMinute < 10 ? "0" : "") + String(currentMinute));
    Serial.println("Conflicting routines: " + conflictInfo);
    Serial.println("Last routine applied wins (current behavior)");
    Serial.println("================================");
  }
}

/*
 * API: POST /api/routines/sync
 * Receives routine schedules from mobile app and stores them in EEPROM
 * Validates routine data and handles parsing from JSON format
 */
void handleRoutineSync() {
  addCORSHeaders();
  
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"No data received\"}");
    return;
  }
  
  DynamicJsonDocument doc(4096); // Larger buffer for multiple routines
  DeserializationError error = deserializeJson(doc, server.arg("plain"));
  
  if (error) {
    Serial.println("JSON parsing failed: " + String(error.c_str()));
    server.send(400, "application/json", "{\"success\":false,\"message\":\"Invalid JSON format: " + String(error.c_str()) + "\"}");
    return;
  }
  
  if (!doc.containsKey("routines")) {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"Missing routines array\"}");
    return;
  }
  
  JsonArray routines = doc["routines"];
  int newRoutineCount = routines.size();
  
  if (newRoutineCount > MAX_ROUTINES) {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"Too many routines. Maximum is " + String(MAX_ROUTINES) + "\"}");
    return;
  }
  
  Serial.println("=== ROUTINE SYNC STARTED ===");
  Serial.println("Received " + String(newRoutineCount) + " routines from app");
  
  routineCount = 0;
  
  for (int i = 0; i < newRoutineCount; i++) {
    JsonObject routineObj = routines[i];
    ESPRoutine& routine = loadedRoutines[i];
    
    routine.id = routineObj["id"].as<int>();
    
    String routineName = routineObj["name"].as<String>();
    if (routineName.length() == 0 || routineName.length() > 31) {
      Serial.println("Invalid routine name length: " + routineName);
      continue; // Skip this routine
    }
    strncpy(routine.name, routineName.c_str(), 31);
    routine.name[31] = '\0';
    
    int hour = routineObj["hour"];
    int minute = routineObj["minute"];
    if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
      Serial.println("Invalid time: " + String(hour) + ":" + String(minute) + " for routine " + routineName);
      continue; // Skip this routine
    }
    routine.hour = hour;
    routine.minute = minute;
    
    // Parse binary string like "1111111" to integer
    String daysStr = routineObj["daysOfWeek"].as<String>();
    routine.days = 0;
    for (int j = 0; j < daysStr.length() && j < 7; j++) {
      if (daysStr[j] == '1') {
        routine.days |= (1 << j);
      }
    }
    routine.isEnabled = routineObj["isEnabled"];
    routine.isOffRoutine = !routineObj["devicePower"]; // If devicePower=false, it's an OFF routine
    routine.createdAt = routineObj["createdAt"];
    
    String presetName = routineObj["presetName"].as<String>();
    if (presetName.length() > 31) {
      presetName = presetName.substring(0, 31);
      Serial.println("Warning: Preset name truncated to 31 characters");
    }
    strncpy(routine.presetName, presetName.c_str(), 31);
    routine.presetName[31] = '\0';
    
    if (!routine.isOffRoutine) {
      JsonArray sliderArray = routineObj["sliderPreset"];
      for (int j = 0; j < NUM_SLIDERS && j < sliderArray.size(); j++) {
        routine.sliderValues[j] = sliderArray[j].as<int>();
      }
    } else {
      for (int j = 0; j < NUM_SLIDERS; j++) {
        routine.sliderValues[j] = 0;
      }
    }
    
    Serial.println("Parsed routine " + String(i + 1) + ": " + String(routine.name));
    Serial.println("  Time: " + String(routine.hour) + ":" + 
                   String(routine.minute < 10 ? "0" : "") + String(routine.minute));
    Serial.println("  Days: " + String(routine.days) + " (binary)");
    Serial.println("  Type: " + String(routine.isOffRoutine ? "OFF" : "PRESET"));
    Serial.println("  Preset: " + String(routine.presetName));
    Serial.println("  Enabled: " + String(routine.isEnabled ? "YES" : "NO"));
    
    routineCount++;
  }
  
  saveRoutines();
  
  DynamicJsonDocument response(512);
  response["success"] = true;
  response["message"] = "Routines synced successfully";
  response["data"]["routineCount"] = routineCount;
  
  String responseStr;
  serializeJson(response, responseStr);
  server.send(200, "application/json", responseStr);
  
  Serial.println("=== ROUTINE SYNC COMPLETED ===");
  Serial.println("Successfully synced " + String(routineCount) + " routines");
  Serial.println("===============================");
}

void handleGetRoutines() {
  addCORSHeaders();
  
  DynamicJsonDocument doc(2048);
  doc["success"] = true;
  doc["message"] = "Routines retrieved successfully";
  doc["data"]["routineCount"] = routineCount;
  
  JsonArray routinesArray = doc["data"].createNestedArray("routines");
  
  for (int i = 0; i < routineCount; i++) {
    JsonObject routineObj = routinesArray.createNestedObject();
    const ESPRoutine& routine = loadedRoutines[i];
    
    routineObj["id"] = routine.id;
    routineObj["name"] = routine.name;
    routineObj["hour"] = routine.hour;
    routineObj["minute"] = routine.minute;
    routineObj["days"] = routine.days;
    routineObj["isEnabled"] = routine.isEnabled;
    routineObj["isOffRoutine"] = routine.isOffRoutine;
    routineObj["presetName"] = routine.presetName;
    
    JsonArray sliderArray = routineObj.createNestedArray("sliderValues");
    for (int j = 0; j < NUM_SLIDERS; j++) {
      sliderArray.add(routine.sliderValues[j]);
    }
  }
  
  String response;
  serializeJson(doc, response);
  server.send(200, "application/json", response);
}

/*
 * API: POST /api/time
 * Manual time synchronization from mobile app when NTP is unavailable
 * Accepts Unix timestamp and sets system time for routine execution
 */
void handleTimeSync() {
  addCORSHeaders();
  
  if (!server.hasArg("plain")) {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"No data received\"}");
    return;
  }
  
  DynamicJsonDocument doc(256);
  DeserializationError error = deserializeJson(doc, server.arg("plain"));
  
  if (error) {
    Serial.println("Time sync JSON parsing failed: " + String(error.c_str()));
    server.send(400, "application/json", "{\"success\":false,\"message\":\"Invalid JSON format: " + String(error.c_str()) + "\"}");
    return;
  }
  
  if (doc.containsKey("timestamp")) {
    uint32_t timestamp = doc["timestamp"];
    
    Serial.println("=== TIME SYNC ===");
    Serial.println("Received timestamp: " + String(timestamp));
    
    struct timeval tv;
    tv.tv_sec = timestamp;
    tv.tv_usec = 0;
    settimeofday(&tv, NULL);
    
    timeInitialized = true;
    
    time_t now;
    time(&now);
    struct tm* timeinfo = localtime(&now);
    Serial.println("Current time set to: " + String(ctime(&now)));
    Serial.printf("Parsed time: %02d:%02d:%02d on day %d\n", 
                  timeinfo->tm_hour, timeinfo->tm_min, timeinfo->tm_sec, timeinfo->tm_wday);
    Serial.println("==================");
    
    DynamicJsonDocument response(256);
    response["success"] = true;
    response["message"] = "Time synchronized successfully";
    
    String responseStr;
    serializeJson(response, responseStr);
    server.send(200, "application/json", responseStr);
  } else {
    server.send(400, "application/json", "{\"success\":false,\"message\":\"Missing timestamp\"}");
  }
}
