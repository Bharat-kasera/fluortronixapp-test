package com.fluortronix.fluortronixapp.data.models

data class Device(
    val id: String,
    val name: String,
    val roomId: String? = null,
    val roomName: String? = null,
    val ipAddress: String? = null,
    val isOnline: Boolean = false,
    val deviceType: String = "ESP32", // Can be expanded for different device types
    val lastSeen: Long = System.currentTimeMillis(),
    // Extended ESP device properties
    val deviceModel: String? = null,
    val firmwareVersion: String? = null,
    val numSliders: Int = 0,
    val sliderNames: List<String> = emptyList(),
    val spdData: SPDData? = null,
    val isOn: Boolean = false,
    val sliderValues: List<Int> = emptyList(), // PWM values 0-255 for each slider
    val maxSliders: Int = 6 // Maximum 6 independent PWM sliders
)

data class SPDData(
    val wavelengths: List<Float>, // Wavelength values in nm
    val intensities: List<Float>, // Intensity values
    val fileName: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)

data class DeviceCapabilities(
    val hasOnOffControl: Boolean = true,
    val hasBrightnessControl: Boolean = false,
    val hasColorControl: Boolean = false,
    val hasSpectrumControl: Boolean = false,
    val supportedSliders: List<SliderInfo> = emptyList()
)

data class SliderInfo(
    val id: String,
    val name: String,
    val minValue: Int = 0,
    val maxValue: Int = 255,
    val currentValue: Int = 0,
    val unit: String = "PWM"
)

data class DeviceCommand(
    val type: CommandType,
    val deviceId: String,
    val parameters: Map<String, Any> = emptyMap()
)

enum class CommandType {
    TOGGLE_POWER,
    SET_SLIDER_VALUE,
    GET_DEVICE_INFO,
    GET_SPD_DATA,
    SET_MULTIPLE_SLIDERS
} 