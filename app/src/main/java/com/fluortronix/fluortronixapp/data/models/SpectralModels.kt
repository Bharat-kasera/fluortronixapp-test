package com.fluortronix.fluortronixapp.data.models

import androidx.compose.ui.graphics.Color

// Holds all static info for a single light source
data class LightSource(
    val name: String,
    val colorCode: String,
    val intensityFactor: Float,
    val initialPower: Float // The power percentage from the Excel file
) {
    // Helper function to convert hex color to Compose Color
    fun getComposeColor(): Color {
        return try {
            val cleanColorCode = colorCode.trim().let { 
                if (it.startsWith("#")) it.substring(1) else it 
            }
            
            // Ensure the color code is 6 or 8 characters (RGB or ARGB)
            val normalizedColor = when (cleanColorCode.length) {
                6 -> "FF$cleanColorCode" // Add alpha if missing
                8 -> cleanColorCode
                3 -> {
                    // Convert RGB shorthand (e.g., "F0A") to full form
                    val r = cleanColorCode[0]
                    val g = cleanColorCode[1] 
                    val b = cleanColorCode[2]
                    "FF$r$r$g$g$b$b"
                }
                else -> "FF808080" // Default to gray
            }
            
            val colorLong = normalizedColor.toLong(16)
            Color(android.graphics.Color.parseColor("#$normalizedColor"))
        } catch (e: Exception) {
            println("DEBUG: Failed to parse color '$colorCode': ${e.message}")
            Color.Gray
        }
    }
}

// Represents the base intensity values for all sources at one wavelength
data class SpectralPoint(
    val wavelength: Int,
    val baseIntensities: Map<String, Float> // Key: LightSource.name, Value: base intensity
)

// The final, combined data structure returned by the parser
data class SpectralProfile(
    val sources: List<LightSource>,
    val spectrum: List<SpectralPoint>,
    val deviceModel: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    // Get only the active light sources (initial power > 0)
    fun getActiveSources(): List<LightSource> = sources.filter { it.initialPower > 0 }
    
    // Check if this profile is compatible with a device model
    fun isCompatibleWith(deviceModel: String?): Boolean {
        return this.deviceModel == null || this.deviceModel == deviceModel
    }
}

// A single point for the final graph
data class GraphPoint(
    val wavelength: Int,
    val finalIntensity: Float
)

// Spectrum preset configuration
data class SpectrumPreset(
    val id: String,
    val name: String,
    val sliderValues: Map<String, Float>,
    val createdAt: Long = System.currentTimeMillis(),
    val description: String? = null
)

// Master slider configuration
data class MasterSliderConfig(
    val isEnabled: Boolean = false,
    val masterValue: Float = 1.0f,
    val frozenSliders: Set<String> = emptySet(), // Which individual sliders are frozen
    val baseSliderValues: Map<String, Float> = emptyMap() // Base values when master slider was enabled
)

// Data class for room spectral settings
data class RoomSpectralData(
    val spectralProfile: SpectralProfile? = null,
    val currentSliderValues: Map<String, Float> = emptyMap(),
    val excelFileName: String? = null,
    val lastCalculated: Long = System.currentTimeMillis(),
    val spectrumPresets: List<SpectrumPreset> = emptyList(),
    val masterSliderConfig: MasterSliderConfig = MasterSliderConfig()
) {
    // Check if spectral data is available
    fun hasSpectralData(): Boolean = spectralProfile != null
    
    // Get slider value for a specific source
    fun getSliderValue(sourceName: String): Float {
        return currentSliderValues[sourceName] ?: spectralProfile?.sources?.find { it.name == sourceName }?.initialPower ?: 0f
    }
    
    // Get preset by ID
    fun getPreset(presetId: String): SpectrumPreset? {
        return spectrumPresets.find { it.id == presetId }
    }
    
    // Check if a slider is frozen in master mode
    fun isSliderFrozen(sourceName: String): Boolean {
        return masterSliderConfig.isEnabled && masterSliderConfig.frozenSliders.contains(sourceName)
    }
} 