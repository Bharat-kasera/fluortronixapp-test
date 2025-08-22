package com.fluortronix.fluortronixapp.data.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    @TypeConverter
    fun fromString(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",").map { it.trim() }
    }

    @TypeConverter
    fun fromList(list: List<String>): String {
        return list.joinToString(",")
    }
    
    @TypeConverter
    fun fromSliderValuesMap(value: Map<String, Float>): String {
        return Gson().toJson(value)
    }

    @TypeConverter
    fun toSliderValuesMap(value: String): Map<String, Float> {
        return if (value.isEmpty()) {
            emptyMap()
        } else {
            try {
                val type = object : TypeToken<Map<String, Float>>() {}.type
                Gson().fromJson(value, type) ?: emptyMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
} 