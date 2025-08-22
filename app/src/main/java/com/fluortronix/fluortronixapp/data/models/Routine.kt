package com.fluortronix.fluortronixapp.data.models

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TargetType {
    DEVICE,
    ROOM
}

enum class ActionType {
    POWER,
    SPECTRUM_PRESET,
    DEVICE_POWER_AND_PRESET // Combined action for device power + spectrum preset
}

@Entity(tableName = "routines")
data class Routine(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val targetType: TargetType = TargetType.ROOM,
    val targetId: String, // Room ID for room-based routines
    val time: String, // Store time as "HH:mm" (24-hour format)
    val actionType: ActionType = ActionType.DEVICE_POWER_AND_PRESET,
    val actionValue: String = "ON", // "ON", "OFF", or preset_id for backward compatibility
    val isEnabled: Boolean = true,
    val days: List<String> = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"), // Days of week
    // New fields for enhanced routine functionality
    val devicePower: Boolean = true, // Whether to turn devices on/off
    val spectrumPresetId: String? = null, // ID of spectrum preset to apply
    val spectrumPresetName: String? = null, // Name of spectrum preset for display
    val sliderValues: Map<String, Float> = emptyMap(), // Direct slider values as backup
    val createdAt: Long = System.currentTimeMillis(),
    val lastSyncedToDevices: Long = 0, // When this routine was last synced to ESP devices
    val roomName: String? = null // Cached room name for display
) {
    /**
     * Get time components
     */
    fun getHour(): Int = time.split(":")[0].toIntOrNull() ?: 0
    fun getMinute(): Int = time.split(":")[1].toIntOrNull() ?: 0
    
    /**
     * Get formatted time for display
     */
    fun getFormattedTime(): String {
        val hour = getHour()
        val minute = getMinute()
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        return String.format("%d:%02d %s", displayHour, minute, amPm)
    }
    
    /**
     * Get days as binary string for ESP device (1111111 for all days)
     */
    fun getDaysAsBinaryString(): String {
        val dayMapping = mapOf(
            "MON" to 0, "TUE" to 1, "WED" to 2, "THU" to 3,
            "FRI" to 4, "SAT" to 5, "SUN" to 6
        )
        val result = CharArray(7) { '0' }
        days.forEach { day ->
            dayMapping[day]?.let { index ->
                result[index] = '1'
            }
        }
        return String(result)
    }
    
    /**
     * Convert slider values to PWM values (0-255)
     * 
     * Important: This function maps spectral sources to PWM channels sequentially
     * based on the order they appear in the slider values map. The ESP device
     * will apply these PWM values to channels 1-6 in order.
     */
    fun getSliderValuesAsPWM(): List<Int> {
        if (sliderValues.isEmpty()) {
            // Return 6 zero values for OFF routines or empty presets
            return List(6) { 0 }
        }
        
        // Convert slider values to PWM in the order they appear
        // This preserves the spectral profile source order
        val sliderEntries = sliderValues.entries.toList()
        
        val pwmValues = mutableListOf<Int>()
        for (i in 0..5) { // Support up to 6 channels
            val pwmValue = if (i < sliderEntries.size) {
                val (sourceName, sliderValue) = sliderEntries[i]
                
                // sliderValues in routines are always stored in 0.0-1.0 range (normalized)
                val pwm = (sliderValue * 255).toInt().coerceIn(0, 255)
                
                println("DEBUG: Routine PWM conversion - Channel ${i+1} (${sourceName}): ${sliderValue} -> $pwm/255 PWM")
                pwm
            } else {
                0 // Default to 0 for unused channels
            }
            pwmValues.add(pwmValue)
        }
        
        println("DEBUG: Routine getSliderValuesAsPWM() result: $pwmValues")
        return pwmValues
    }
    
    /**
     * Check if routine should run today
     */
    fun shouldRunToday(): Boolean {
        val currentDay = when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.MONDAY -> "MON"
            java.util.Calendar.TUESDAY -> "TUE"
            java.util.Calendar.WEDNESDAY -> "WED"
            java.util.Calendar.THURSDAY -> "THU"
            java.util.Calendar.FRIDAY -> "FRI"
            java.util.Calendar.SATURDAY -> "SAT"
            java.util.Calendar.SUNDAY -> "SUN"
            else -> ""
        }
        return days.contains(currentDay)
    }
    
    /**
     * Check if this routine needs to be synced to devices
     */
    fun needsSync(): Boolean {
        return createdAt > lastSyncedToDevices
    }
} 