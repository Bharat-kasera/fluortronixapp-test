package com.fluortronix.fluortronixapp.data.datasource

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCacheService @Inject constructor(
    @ApplicationContext private val context: Context
) {
    
    companion object {
        private const val CACHE_DIR_NAME = "device_images"
        private const val IMAGE_QUALITY = 85 // JPEG compression quality
        private const val MAX_IMAGE_SIZE = 1024 // Max width/height in pixels
    }
    
    private val cacheDir: File by lazy {
        File(context.filesDir, CACHE_DIR_NAME).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * Save device image to cache
     */
    suspend fun saveDeviceImage(
        deviceId: String, 
        imageStream: InputStream
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            println("DEBUG: Saving device image for device: $deviceId")
            
            // Decode and optimize the image
            val bitmap = BitmapFactory.decodeStream(imageStream)
                ?: return@withContext Result.failure(Exception("Failed to decode image"))
            
            // Resize if too large to save storage space
            val optimizedBitmap = if (bitmap.width > MAX_IMAGE_SIZE || bitmap.height > MAX_IMAGE_SIZE) {
                val scale = minOf(
                    MAX_IMAGE_SIZE.toFloat() / bitmap.width,
                    MAX_IMAGE_SIZE.toFloat() / bitmap.height
                )
                val newWidth = (bitmap.width * scale).toInt()
                val newHeight = (bitmap.height * scale).toInt()
                
                Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }
            
            // Save to cache directory
            val imageFile = getImageFile(deviceId)
            FileOutputStream(imageFile).use { outputStream ->
                optimizedBitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_QUALITY, outputStream)
            }
            
            optimizedBitmap.recycle()
            
            println("DEBUG: Device image saved successfully: ${imageFile.absolutePath}")
            println("DEBUG: Image file size: ${imageFile.length()} bytes")
            
            Result.success(imageFile.absolutePath)
        } catch (e: Exception) {
            println("DEBUG: Failed to save device image for $deviceId: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }
    
    /**
     * Load device image from cache
     */
    suspend fun loadDeviceImage(deviceId: String): Result<ImageBitmap> = withContext(Dispatchers.IO) {
        try {
            val imageFile = getImageFile(deviceId)
            
            if (!imageFile.exists()) {
                return@withContext Result.failure(Exception("Image not found in cache"))
            }
            
            val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                ?: return@withContext Result.failure(Exception("Failed to decode cached image"))
            
            val imageBitmap = bitmap.asImageBitmap()
            
            println("DEBUG: Device image loaded from cache: ${imageFile.absolutePath}")
            Result.success(imageBitmap)
        } catch (e: Exception) {
            println("DEBUG: Failed to load device image for $deviceId: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Check if device image exists in cache
     */
    fun hasDeviceImage(deviceId: String): Boolean {
        val imageFile = getImageFile(deviceId)
        val exists = imageFile.exists() && imageFile.length() > 0
        println("DEBUG: Device image cache check for $deviceId: $exists")
        return exists
    }
    
    /**
     * Delete device image from cache
     */
    suspend fun deleteDeviceImage(deviceId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val imageFile = getImageFile(deviceId)
            val deleted = if (imageFile.exists()) {
                imageFile.delete()
            } else {
                true // Already doesn't exist
            }
            
            println("DEBUG: Device image deleted for $deviceId: $deleted")
            Result.success(deleted)
        } catch (e: Exception) {
            println("DEBUG: Failed to delete device image for $deviceId: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Get image file for device
     */
    private fun getImageFile(deviceId: String): File {
        // Sanitize device ID for filename
        val sanitizedDeviceId = deviceId.replace("[^a-zA-Z0-9_-]".toRegex(), "_")
        return File(cacheDir, "device_${sanitizedDeviceId}.jpg")
    }
    
    /**
     * Clear all cached images
     */
    suspend fun clearAllImages(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val files = cacheDir.listFiles() ?: emptyArray()
            var deletedCount = 0
            
            files.forEach { file ->
                if (file.isFile && file.delete()) {
                    deletedCount++
                }
            }
            
            println("DEBUG: Cleared $deletedCount cached device images")
            Result.success(deletedCount)
        } catch (e: Exception) {
            println("DEBUG: Failed to clear image cache: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Get cache statistics
     */
    fun getCacheStats(): CacheStats {
        return try {
            val files = cacheDir.listFiles() ?: emptyArray()
            val imageFiles = files.filter { it.isFile && it.name.endsWith(".jpg") }
            val totalSize = imageFiles.sumOf { it.length() }
            
            CacheStats(
                imageCount = imageFiles.size,
                totalSizeBytes = totalSize,
                cacheDirectory = cacheDir.absolutePath
            )
        } catch (e: Exception) {
            CacheStats(0, 0, cacheDir.absolutePath)
        }
    }
}

/**
 * Cache statistics data class
 */
data class CacheStats(
    val imageCount: Int,
    val totalSizeBytes: Long,
    val cacheDirectory: String
) {
    fun getTotalSizeMB(): Float = totalSizeBytes / (1024f * 1024f)
}
