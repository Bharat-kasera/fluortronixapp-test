package com.fluortronix.fluortronixapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluortronix.fluortronixapp.data.models.SPDData
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue

@Composable
fun SPDChart(
    spdData: SPDData?,
    modifier: Modifier = Modifier,
    title: String = "Spectral Power Distribution",
    showTitle: Boolean = true,
    isRealTime: Boolean = false
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Chart header
            if (showTitle) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Chart",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(20.dp)
                        )
                        
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.Black
                        )
                    }
                    
                    if (isRealTime) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(Color(0xFF4CAF50))
                            )
                            Text(
                                text = "Live",
                                fontSize = 12.sp,
                                color = Color(0xFF4CAF50),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Chart content placeholder
            if (spdData != null && spdData.wavelengths.isNotEmpty()) {
                // Simple visualization for now - we'll replace with proper chart library later
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Gray.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "Chart",
                            tint = PrimaryBlue,
                            modifier = Modifier.size(32.dp)
                        )
                        
                        Text(
                            text = "SPD Chart Visualization",
                            fontSize = 14.sp,
                            color = Color.Black,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Text(
                            text = "${spdData.wavelengths.size} data points",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                        
                        Text(
                            text = "Peak: ${spdData.wavelengths.firstOrNull()?.toInt() ?: "--"}nm",
                            fontSize = 12.sp,
                            color = PrimaryBlue
                        )
                    }
                }
            } else {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = "No data",
                            tint = Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                        
                        Text(
                            text = "No SPD data available",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                        
                        Text(
                            text = "Connect to device to view spectrum",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpectralColorBar(
    modifier: Modifier = Modifier,
    wavelengthRange: ClosedFloatingPointRange<Float> = 380f..750f
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Text(
                text = "Wavelength Spectrum",
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            // Simplified color bar representing the visible spectrum
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                Color(0xFF8B00FF), // Violet (~380nm)
                                Color(0xFF4B0082), // Indigo (~450nm)
                                Color(0xFF0000FF), // Blue (~475nm)
                                Color(0xFF00FF00), // Green (~510nm)
                                Color(0xFFFFFF00), // Yellow (~570nm)
                                Color(0xFFFF7F00), // Orange (~590nm)
                                Color(0xFFFF0000)  // Red (~700nm)
                            )
                        )
                    )
            )
            
            // Wavelength labels
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${wavelengthRange.start.toInt()}nm",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                Text(
                    text = "${wavelengthRange.endInclusive.toInt()}nm",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun SPDStats(
    spdData: SPDData?,
    modifier: Modifier = Modifier
) {
    if (spdData == null) return
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Spectrum Statistics",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val maxIntensity = spdData.intensities.maxOrNull() ?: 0f
            val avgIntensity = if (spdData.intensities.isNotEmpty()) {
                spdData.intensities.average().toFloat()
            } else 0f
            
            val peakWavelength = if (spdData.intensities.isNotEmpty()) {
                val maxIndex = spdData.intensities.indexOf(maxIntensity)
                if (maxIndex != -1 && maxIndex < spdData.wavelengths.size) {
                    spdData.wavelengths[maxIndex]
                } else null
            } else null
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Peak",
                    value = "${peakWavelength?.toInt() ?: "--"}nm",
                    color = PrimaryBlue
                )
                
                StatItem(
                    label = "Max Intensity",
                    value = String.format("%.2f", maxIntensity),
                    color = Color(0xFF4CAF50)
                )
                
                StatItem(
                    label = "Avg Intensity",
                    value = String.format("%.2f", avgIntensity),
                    color = Color(0xFFFF9800)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Preview(showBackground = true)
@Composable
fun SPDChartPreview() {
    val sampleData = SPDData(
        wavelengths = (380..750 step 10).map { it.toFloat() },
        intensities = (380..750 step 10).map { 
            when {
                it < 450 -> 0.3f
                it < 550 -> 0.8f
                it < 650 -> 1.0f
                else -> 0.5f
            }
        }
    )
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF5F5F5))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SPDChart(
            spdData = sampleData,
            isRealTime = true
        )
        
        SpectralColorBar()
        
        SPDStats(spdData = sampleData)
    }
} 