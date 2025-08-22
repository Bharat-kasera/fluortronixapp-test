# FluorTronix - ESP8266 based IOT Android Application

FluorTronix is a complete smart lighting solution designed for grow lights and spectral control applications. It consists of ESP8266-based hardware controllers and an Android mobile app that work together to provide precise control over lighting systems.

## What is FluorTronix app?

This project lets you:
- **Control LED grow lights** with 6 independent PWM channels 
- **Schedule automated routines** for lighting on/off and preset changes
- **Analyze spectral data** by uploading Excel files with wavelength/intensity data
- **Manage multiple devices** organized by rooms
- **Provision devices via WiFi** with a simple setup process

## Project Structure

```
fluortronixapp/
├── ESP8266_FluorTronix_Test/           # Hardware firmware (Arduino C++)
│   ├── ESP8266_FluorTronix_Test.ino    # Main ESP8266 firmware
│   └── data/
│       └── data.xlsx                   # Sample spectral data
├── app/                                # Android mobile app (Kotlin)
│   ├── src/main/java/com/fluortronix/fluortronixapp/
│   │   ├── data/                       # Data layer (API, database, models)
│   │   ├── presentation/               # UI layer (screens, components, viewmodels)
│   │   └── di/                         # Dependency injection
│   └── build.gradle.kts                # App dependencies
└── README.md                           # This file
```

## Hardware Components

### ESP8266 Device Specifications
- **Microcontroller**: ESP8266 (80MHz, 4MB Flash recommended)
- **PWM Channels**: 6 independent outputs (0-255 resolution, ~1kHz frequency)
- **GPIO Mapping**: 
  ```
  Channel 1: GPIO 5  (D1 on NodeMCU)
  Channel 2: GPIO 4  (D2 on NodeMCU) 
  Channel 3: GPIO 0  (D3 on NodeMCU)
  Channel 4: GPIO 15 (D8 on NodeMCU)
  Channel 5: GPIO 14 (D5 on NodeMCU)
  Channel 6: GPIO 12 (D6 on NodeMCU)
  ```


### Circuit Connections
```
ESP8266          LED Driver          LED Strip/Array
GPIO 5    -----> PWM Input    -----> Channel 1 (Red)
GPIO 4    -----> PWM Input    -----> Channel 2 (Blue)  
GPIO 0    -----> PWM Input    -----> Channel 3 (White)
GPIO 15   -----> PWM Input    -----> Channel 4 (UV)
GPIO 14   -----> PWM Input    -----> Channel 5 (IR)
GPIO 12   -----> PWM Input    -----> Channel 6 (Green)

3.3V      -----> VCC (Logic)
GND       -----> GND (Common)
```

## Software Components

### Android App Features
- **Device discovery and provisioning** 
- **Real-time device control** with slider interface
- **Room-based organization** of devices
- **Automated scheduling** for routines
- **Spectral data analysis** from Excel files
- **Material Design 3** modern UI

### Tech Stack
- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM with Clean Architecture
- **Database**: Room (SQLite)
- **Networking**: Retrofit + OkHttp
- **Dependency Injection**: Hilt
- **Charts**: Vico for spectral visualization
- **File Parsing**: Apache POI for Excel files

## Getting Started

### Prerequisites
- **Android Studio** 
- **Arduino IDE** 1.8.18 
- **ESP8266 Core** v3.0.0+ for Arduino
- **Android Device**: 
  - API 26+ (Android 8.0+)
  - 2GB RAM minimum
  - WiFi capability
  - Location permissions for network scanning
- **ESP8266 Board**: NodeMCU, Wemos D1 Mini, or ESP-12E module
- **Development Tools**:
  - Git for version control
  - USB cable for ESP8266 programming
  - Multimeter for circuit debugging

### 1. Setting Up the Hardware

1. **Install Arduino IDE and ESP8266 board support**:
   - Add board manager URL: `http://arduino.esp8266.com/stable/package_esp8266com_index.json`
   - Install "ESP8266" board package

2. **Install required libraries**:
   ```
   ESP8266WiFi (included with ESP8266 core)
   ESP8266WebServer (included with ESP8266 core)
   ESP8266mDNS (included with ESP8266 core)
   ArduinoJson v6.21.0+ (Library Manager)
   EEPROM (included with ESP8266 core)
   FS (included with ESP8266 core)
   ```

3. **Configure Arduino IDE**:
   - **Board**: NodeMCU 1.0 (ESP-12E Module) or Generic ESP8266
   - **Upload Speed**: 115200 or 921600
   - **CPU Frequency**: 80MHz
   - **Flash Size**: 4MB (FS:2MB OTA:~1019KB)
   - **Debug Port**: Serial
   - **Debug Level**: None (or Core for debugging)

4. **Flash the firmware**:
   - Open `ESP8266_FluorTronix_Test/ESP8266_FluorTronix_Test.ino`
   - Connect ESP8266 via USB
   - Select correct board and port in Tools menu
   - Click Upload (takes ~30 seconds)
   - Monitor Serial at 115200 baud for startup messages

5. **Verify installation**:
   - Serial output should show device MAC address
   - Device creates WiFi hotspot "FLUO-XXXXXX"
   - Web interface accessible at 192.168.4.1

### 2. Setting Up the Android App

1. **Clone and open project**:
   ```bash
   git clone <repository-url>
   cd fluortronixapp
   ```

2. **Open in Android Studio**:
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the project folder

3. **Build and run**:
   - Let Gradle sync dependencies
   - Connect your Android device or start an emulator
   - Click "Run" to install the app

### 3. First Time Setup

1. **Device Provisioning**:
   - Power on your ESP8266 device
   - Open the FluorTronix app
   - Go through the onboarding flow
   - The app will find devices with SSID starting with "FLUO-"
   - Enter your WiFi credentials to connect the device

2. **Create a Room**:
   - Add a new room in the app
   - Assign your connected device to the room

3. **Start Controlling**:
   - Use sliders to control LED brightness
   - Set up routines for automated scheduling
   - Upload Excel files for spectral analysis

## API Documentation

The ESP8266 exposes a REST API on port 80. All endpoints support CORS for web app integration.

### Device Information
```http
GET /api/device/info
```
**Response**:
```json
{
  "device_model": "FluorTronix-Model-01",
  "firmware_version": "1.0.0",
  "ip_address": "192.168.1.100",
  "num_sliders": 6,
  "is_on": true,
  "slider_names": ["Channel 1", "Channel 2", "Channel 3", "Channel 4", "Channel 5", "Channel 6"],
  "slider_values": [255, 128, 0, 64, 200, 100]
}
```

### System Status
```http
GET /api/device/status
```
**Response**:
```json
{
  "success": true,
  "message": "Device is online",
  "data": {
    "uptime": 1234567,
    "free_heap": 45032
  }
}
```

### Device Control
```http
POST /api/device/power
Content-Type: application/json

{
  "power": true
}
```
**Response**:
```json
{
  "success": true,
  "message": "Power state updated",
  "data": {
    "power": true
  }
}
```

```http
POST /api/device/slider
Content-Type: application/json

{
  "slider_id": 0,
  "value": 128
}
```
**Response**:
```json
{
  "success": true,
  "message": "Slider updated"
}
```

```http
POST /api/device/sliders
Content-Type: application/json

{
  "sliders": [
    {"slider_id": 0, "value": 255},
    {"slider_id": 1, "value": 128}
  ]
}
```

### Routine Management
```http
POST /api/routines/sync
Content-Type: application/json

{
  "routines": [
    {
      "id": 1,
      "name": "Morning Light",
      "hour": 8,
      "minute": 0,
      "daysOfWeek": "1111100",
      "isEnabled": true,
      "devicePower": true,
      "presetName": "Sunrise Preset",
      "sliderPreset": [255, 200, 150, 100, 50, 0],
      "createdAt": 1640995200
    }
  ]
}
```

```http
GET /api/routines
```
**Response**:
```json
{
  "success": true,
  "message": "Routines retrieved successfully",
  "data": {
    "routineCount": 2,
    "routines": [
      {
        "id": 1,
        "name": "Morning Light",
        "hour": 8,
        "minute": 0,
        "days": 124,
        "isEnabled": true,
        "isOffRoutine": false,
        "presetName": "Sunrise Preset",
        "sliderValues": [255, 200, 150, 100, 50, 0]
      }
    ]
  }
}
```

### Configuration APIs
```http
POST /api/prov/config_wifi
Content-Type: application/json

{
  "ssid": "YourWiFiNetwork",
  "pass": "YourPassword"
}
```

```http
POST /api/device/slider_names
Content-Type: application/json

{
  "slider_names": ["Red", "Blue", "White", "UV", "IR", "Green"]
}
```

```http
POST /api/time
Content-Type: application/json

{
  "timestamp": 1640995200
}
```

### Data Export
```http
GET /api/device/spd
```
Returns CSV spectral power distribution data.

```http
GET /data/data.xlsx
```
Downloads Excel file from device SPIFFS storage.

## Excel File Format for Spectral Data

### Critical File Structure Requirements
**The Excel file must have EXACTLY same structure as in data.xlsx file or parsing will fail:**

#### Sheet 1: "Spectrum Values"
**Required name**: Must be named "Spectrum Values" (case-sensitive)

**Row Structure** (exact order required)

#### Sheet 2: "Color Codes"
**Required name**: Must be named "Color Codes" (case-sensitive)


### SPD Calculation Formula

The app uses this multi-step calculation to generate the final spectral power distribution:

#### Step 1: Resultant Intensity Calculation
For each wavelength point, the resultant intensity is calculated as:

```
ResultantIntensity(wavelength) = Σ (BaseIntensity × SliderValue × IntensityFactor)
```

Where:
- **BaseIntensity**: Raw spectral data from Excel (0.0-1.0 normalized)
- **SliderValue**: Current UI slider position (0.0-1.0)
- **IntensityFactor**: Multiplication factor from Row 2 of Excel

**Code Implementation**:
```kotlin
val resultantIntensity = currentSliderValues.entries.sumOf { (name, sliderValue) ->
    val source = allSources[name]
    val baseIntensity = point.baseIntensities[name] ?: 0f
    // Core formula: baseIntensity * sliderValue * intensityFactor
    (baseIntensity * sliderValue * (source?.intensityFactor ?: 0f)).toDouble()
}
```

#### Step 2: Normalization
The resultant curve is normalized to 0-1 range:

```
FinalIntensity(wavelength) = ResultantIntensity(wavelength) / MaxResultantIntensity
```

#### Step 3: PWM Conversion
For ESP8266 device control, normalized values convert to PWM:

```
PWM_Value = SliderValue × 255 (rounded to integer, 0-255 range)
```

### Data Validation Rules

**Excel File Validation**:
1. **File format**: Must be .xlsx (not .xls)
2. **Sheet names**: Exact case-sensitive names required
3. **Row positions**: Power%, Intensity Factors, Headers must be in rows 1,2,3
4. **Column alignment**: Source names in headers must match Color Codes sheet
5. **Data types**: Wavelengths as integers, intensities as floats
6. **Range limits**: Wavelengths 350-800nm, intensities 0.0-1.0

**Runtime Validation**:
```kotlin
// Power percentages: 0-100 range
powerPercentages[name] = getCellNumericValue(cell)

// Intensity factors: any positive float  
intensityFactors[name] = getCellNumericValue(cell)

// Base intensities: 0.0-1.0 normalized
intensities[name] = getCellNumericValue(intensityCell)
```

### Common Excel File Errors

**Parsing Failures**:
- Sheet name mismatch: "Spectrum Values" ≠ "spectrum values"
- Wrong row order: Headers in row 1 instead of row 3
- Missing Color Codes sheet
- Non-numeric wavelength values
- Source name mismatch between sheets

**Formula Calculation Issues**:
- Intensity factors of 0 will null out that source
- Power percentages only affect initial slider positions
- Base intensities > 1.0 may cause overflow before normalization

## Troubleshooting

### Device Connection Issues

**Device not found in WiFi scan**:
- Verify ESP8266 power (3.3V, LED indicators)
- Check serial output for boot messages
- Look for "FLUO-XXXXXX" network in phone's WiFi settings
- Reset device if hanging in boot loop

**WiFi provisioning fails**:
- Check WiFi credentials (case-sensitive)
- Ensure 2.4GHz network (ESP8266 doesn't support 5GHz)
- Verify network allows new device connections
- Check router MAC filtering settings

**API connection timeout**:
- Ping device IP from phone terminal
- Check if device and phone on same subnet
- Verify no firewall blocking port 80
- Test web interface in browser at device IP

**mDNS discovery fails**:
- Use IP address instead of fluortronix.local
- Check router mDNS/Bonjour support
- Network isolation may block mDNS broadcasts

### App-Specific Issues

**Location permission denied**:
```
Error: Location access needed for WiFi scanning
Solution: Settings > Apps > FluorTronix > Permissions > Location > Allow
```

**Excel file parsing errors**:
```
Error: "Invalid file format"
- Check file is .xlsx (not .xls)
- Verify first row contains headers
- Ensure Wavelength column has numeric values

Error: "No spectral data found"
- Check column names match expected sources
- Verify intensity values are 0.0-1.0 range
- Remove empty rows/columns
```

**Routine synchronization fails**:
```
Error: HTTP 400 - Invalid routine data
- Check time format (24-hour)
- Verify day selection not empty
- Ensure routine names under 32 characters

Error: HTTP 500 - Device memory full
- Reduce number of routines (max 6)
- Delete unused routines on device
```

**App crashes on startup**:
- Clear app data: Settings > Apps > FluorTronix > Storage > Clear Data
- Check Android version compatibility (API 26+)
- Free up device storage space (min 100MB)

### ESP8266 Hardware Issues

**Device boot loops**:
```
Serial output: "wdt reset" or "rst cause:4"
- Check power supply (stable 3.3V, 500mA capability)
- Add decoupling capacitors (100µF + 10µF)
- Verify GPIO 0 and GPIO 15 pull-up resistors (10kΩ)
```

**WiFi connection drops frequently**:
```
Serial output: "WiFi DISCONNECTED"
- Check signal strength (RSSI > -70dBm)
- Power supply voltage drops during transmission
- Router may be dropping idle connections
```

**EEPROM corruption**:
```
Serial output: "EEPROM corruption detected"
- Power loss during write operations
- Flash memory wear (100k write cycles)
- Reset device clears corrupted data
```

**Memory allocation failures**:
```
Serial output: "free_heap" < 10000
- Reduce routine count
- Clear device settings and restart
- May indicate firmware memory leak
```

**PWM output not working**:
- Check GPIO pin assignments match code
- Verify LED driver connections and power
- Use multimeter to measure PWM voltage (0-3.3V)
- Test with simple analogWrite(pin, 255) in setup()

### Network and Infrastructure

**Router compatibility issues**:
- Some routers block device-to-device communication
- Corporate networks may have client isolation
- Guest networks often restrict local access
- Try mobile hotspot for testing

**Time synchronization failures**:
```
NTP servers blocked: pool.ntp.org, time.nist.gov
- Configure router to allow NTP (port 123)
- Use manual time sync from app
- Corporate firewalls may block external time servers
```

### Development and Debugging

**Arduino IDE compilation errors**:
```
Error: "ESP8266WiFi.h: No such file"
- Install ESP8266 board package via Board Manager
- Select correct board (NodeMCU 1.0)

Error: "ArduinoJson.h: No such file" 
- Install ArduinoJson library v6.21.0+ via Library Manager
```

**Upload failures**:
- Hold FLASH button during upload on some boards
- Check COM port selection
- Try lower upload speed (115200)
- Verify USB cable supports data (not power-only)

**Android Studio build issues**:
```
Error: "Duplicate class" or "Conflicts with dependency"
- Clean and rebuild project
- Check excluded dependencies in build.gradle
- Update to latest Android Studio version
```

## Development

### Architecture Overview

**ESP8266 Firmware Architecture**:
```
Main Loop (60s cycle)
├── HTTP Request Handler
├── mDNS Updates  
├── NTP Time Sync
├── Routine Scheduler
└── WiFi Status Monitor

EEPROM Layout:
├── 0-255:   WiFi Credentials
├── 256-447: Slider Names (6×32 bytes)
└── 448+:    Routines (variable)
```

**Android App Architecture**:
```
Presentation Layer (Jetpack Compose)
├── MainActivity (Navigation)
├── Screens (UI)
├── Components (Reusable UI)
└── ViewModels (State Management)

Data Layer
├── Repository (Data Coordination)
├── ESPDeviceService (Network API)
├── Room Database (Local Storage)
└── PreferencesManager (Settings)

Dependency Injection (Hilt)
```

### Adding New Features

**ESP8266 API Endpoints**:
1. Define handler function: `void handleNewFeature()`
2. Add route in `setupAPIRoutes()`: `server.on("/api/new", HTTP_POST, handleNewFeature)`
3. Add CORS headers: `addCORSHeaders()`
4. Parse JSON input: `DynamicJsonDocument doc(512)`
5. Validate and process request
6. Return JSON response with success/error status

**Android App Development**:
1. **Data Models** (`data/models/`):
   ```kotlin
   data class NewFeature(
       val id: String,
       val name: String,
       val value: Int
   )
   ```

2. **API Service** (`ESPDeviceService.kt`):
   ```kotlin
   @POST("api/new")
   suspend fun newFeature(@Body request: NewFeatureRequest): Response<ApiResponse>
   ```

3. **Repository** (`repository/`):
   ```kotlin
   suspend fun updateFeature(deviceId: String, data: NewFeature): Result<Boolean>
   ```

4. **ViewModel** (`viewmodels/`):
   ```kotlin
   class FeatureViewModel @Inject constructor(
       private val repository: DeviceRepository
   ) : ViewModel()
   ```

5. **UI Component** (`components/`):
   ```kotlin
   @Composable
   fun FeatureControl(
       value: Int,
       onValueChange: (Int) -> Unit
   )
   ```

### Code Style and Standards

**Kotlin Code Style**:
- Use `camelCase` for variables and functions
- Use `PascalCase` for classes and composables
- Maximum line length: 120 characters
- Use meaningful variable names
- Add KDoc comments for public APIs

**ESP8266 Code Style**:
- Use `camelCase` for functions
- Use `UPPER_CASE` for constants
- Add detailed comments for complex logic
- Handle all error cases with appropriate responses
- Log debug information to Serial

### Testing Strategy

**ESP8266 Testing**:
1. **Serial Monitor Testing**:
   ```
   - Boot sequence validation
   - API request/response logging
   - Memory usage monitoring
   - Error condition handling
   ```

2. **Manual API Testing** (curl/Postman):
   ```bash
   curl -X POST http://192.168.1.100/api/device/power \
        -H "Content-Type: application/json" \
        -d '{"power":true}'
   ```

3. **Web Interface Testing**:
   - Visit device IP in browser
   - Test all endpoints via web form
   - Verify JSON response formatting

**Android App Testing**:
1. **Unit Tests** (`src/test/`):
   ```kotlin
   @Test
   fun `test routine time calculation`() {
       val routine = Routine(time = "08:30")
       assertEquals(8, routine.getHour())
       assertEquals(30, routine.getMinute())
   }
   ```

2. **UI Tests** (`src/androidTest/`):
   ```kotlin
   @Test
   fun testDeviceControlSlider() {
       composeTestRule.setContent {
           DeviceControls(device = testDevice)
       }
       composeTestRule.onNodeWithTag("slider_0").performGesture()
   }
   ```

3. **Integration Tests**:
   - Mock ESP8266 responses
   - Test complete user flows
   - Verify error handling

### Build Process

**Debug Build**:
```bash
./gradlew assembleDebug
# Output: app/build/outputs/apk/debug/app-debug.apk
```

**Release Build**:
```bash
# Generate keystore (first time only)
keytool -genkey -v -keystore fluortronix.keystore -alias fluortronix -keyalg RSA -keysize 2048 -validity 10000

# Build signed release
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

**ESP8266 Compilation**:
```bash
# Arduino CLI approach
arduino-cli compile --fqbn esp8266:esp8266:nodemcuv2 ESP8266_FluorTronix_Test
arduino-cli upload -p COM3 --fqbn esp8266:esp8266:nodemcuv2 ESP8266_FluorTronix_Test
```

### Version Management

**Android App Versions** (`app/build.gradle.kts`):
```kotlin
versionCode = 1        // Increment for each release
versionName = "1.0.0"  // Semantic versioning
```

**ESP8266 Firmware Version** (`ESP8266_FluorTronix_Test.ino`):
```cpp
String firmwareVersion = "1.0.0";  // Update in code
```

**Compatibility Matrix**:
- ESP8266 Firmware 1.0.x ↔ Android App 1.0.x
- API version compatibility checked in `/api/device/info`
- Breaking changes require major version bump

### Performance Considerations

**ESP8266 Optimization**:
- Minimize EEPROM writes (100k cycle limit)
- Use `yield()` in long loops to prevent watchdog resets
- Monitor heap memory (`ESP.getFreeHeap()`)
- Limit concurrent HTTP connections (ESP8266 limitation)

**Android App Optimization**:
- Use `LazyColumn` for large device lists
- Cache device states to reduce API calls
- Implement proper lifecycle handling in ViewModels
- Use `remember` for expensive calculations in Compose

## Configuration Details

### ESP8266 Memory Configuration
```
Flash Layout (4MB):
├── 0x00000000: Bootloader (~16KB)
├── 0x00010000: Firmware (~1MB)
├── 0x00200000: SPIFFS (2MB) - File storage
└── 0x003FB000: WiFi Config (16KB)

EEPROM Usage (2KB):
├── 0-255:     WiFi credentials
├── 256-447:   Slider names (6 × 32 bytes)
├── 448-959:   Routines (6 × 85 bytes max)
└── 960-2047:  Reserved/unused
```

### Network Configuration
```
Provisioning Mode:
- SSID: FLUO-XXXXXX (XXXXXX = last 6 MAC digits)
- Password: 12345678
- IP: 192.168.4.1
- Gateway: 192.168.4.1

Station Mode:
- DHCP client on configured network
- mDNS: fluortronix.local
- HTTP server: Port 80
- NTP clients: pool.ntp.org, time.nist.gov
```

### Android App Configuration
```
Build Configuration:
- Target SDK: 34 (Android 14)
- Min SDK: 26 (Android 8.0)
- Compile SDK: 35
- Java: Version 11
- Kotlin: 2.0.0

Database Schema:
- Room database version: 1
- Tables: routines, rooms (via preferences)
- Storage: Internal app storage
```

### Default Settings
```cpp
// ESP8266 defaults
const int LED_PINS[] = {5, 4, 0, 15, 14, 12};
const int NUM_SLIDERS = 6;
const int MAX_ROUTINES = 6;
const char* AP_PASSWORD = "12345678";
String sliderNames[] = {"Channel 1", "Channel 2", "Channel 3", "Channel 4", "Channel 5", "Channel 6"};
```

## Security Considerations

### Network Security
- **No authentication** on HTTP API (local network only)
- **Plain text** WiFi credentials stored in EEPROM
- **Open HTTP** communication (no HTTPS)
- **Default password** for provisioning AP

### Recommendations for Production
1. Implement API authentication tokens
2. Add HTTPS support with device certificates
3. Encrypt EEPROM stored credentials
4. Use WPA3 for WiFi connections
5. Implement firmware update signing

## Limitations

### Hardware Limitations
- **ESP8266 constraints**: Single-core, limited RAM (80KB)
- **PWM resolution**: 10-bit (0-1023), software-scaled to 8-bit (0-255)
- **Concurrent connections**: Limited to ~4 simultaneous HTTP clients
- **Flash endurance**: 100,000 erase/write cycles for EEPROM
- **WiFi only**: 2.4GHz networks, no 5GHz support

### Software Limitations
- **Android only**: No iOS app available
- **Local network**: No cloud/remote access
- **Time sync dependency**: Requires internet for NTP or manual sync
- **Memory constraints**: Limited routine storage (max 6)
- **No OTA updates**: Firmware updates require USB cable


### Known Issues
- mDNS discovery unreliable on some Android devices
- PWM frequency not configurable (fixed ~1kHz)
- Excel parser limited to specific .xlsx format
- Routine conflicts resolved by last-execution-wins

## Contributing

### Development Setup
1. Fork the repository
2. Clone locally: `git clone <your-fork-url>`
3. Create feature branch: `git checkout -b feature/new-feature`
4. Set up development environment (Android Studio + Arduino IDE)
5. Make changes and test thoroughly
6. Submit pull request with:
   - Clear description of changes
   - Test results on both hardware and software
   - Screenshots/videos for UI changes

### Code Contribution Guidelines
- Follow existing code style and patterns
- Add unit tests for new functionality
- Update documentation for API changes
- Test on real hardware before submitting
- Include hardware setup details if circuit changes required

---



