package com.fluortronix.fluortronixapp.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fluortronix.fluortronixapp.data.models.GraphPoint
import com.fluortronix.fluortronixapp.data.models.LightSource
import com.fluortronix.fluortronixapp.data.models.SpectrumPreset
import com.fluortronix.fluortronixapp.data.models.MasterSliderConfig
import com.fluortronix.fluortronixapp.ui.theme.PrimaryBlue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow


@Composable
fun SpectralControlPanel(
    lightSources: List<LightSource>,
    sliderValues: Map<String, Float>,
    onSliderChange: (String, Float) -> Unit,
    onResetSliders: () -> Unit,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    // New parameters for preset and master slider functionality
    spectrumPresets: List<SpectrumPreset> = emptyList(),
    masterSliderConfig: MasterSliderConfig = MasterSliderConfig(),
    onSavePreset: (String) -> Unit = {},
    onLoadPreset: (String) -> Unit = {},
    onDeletePreset: (String) -> Unit = {},
    onToggleMasterSlider: () -> Unit = {},
    onMasterSliderChange: (Float) -> Unit = {},
    onToggleSliderFreeze: (String) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(20.dp) // Maintain the padding that was inside the Card
    ) {
        // Light source sliders
        lightSources.forEach { source ->
            val currentValue = sliderValues[source.name] ?: source.initialPower
            val isFrozen = masterSliderConfig.frozenSliders.contains(source.name)
            val isSliderEnabled = isEnabled && (!masterSliderConfig.isEnabled || isFrozen)

            SpectralSlider(
                lightSource = source,
                value = currentValue,
                onValueChange = { newValue ->
                    onSliderChange(source.name, newValue)
                },
                isEnabled = isSliderEnabled,
                isFrozen = isFrozen,
                showMasterMode = masterSliderConfig.isEnabled,
                onToggleFreeze = { onToggleSliderFreeze(source.name) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))
        }

        // Reset button placed below sliders
        if (isEnabled) {
            // Master Slider Section
            MasterSliderCard(
                masterSliderConfig = masterSliderConfig,
                onToggleMasterSlider = onToggleMasterSlider,
                onMasterSliderChange = onMasterSliderChange
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Preset Management Section
            PresetManagementCard(
                presets = spectrumPresets,
                onSavePreset = onSavePreset,
                onLoadPreset = onLoadPreset,
                onDeletePreset = onDeletePreset
            )

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                OutlinedButton(
                    onClick = onResetSliders,
                    enabled = isEnabled,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset All Sliders")
                }
            }
        }

        // Info text
        if (!isEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFFFF3E0),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(12.dp)
            ) {
                Text(
                    text = "Controls are disabled when no spectral data is available",
                    fontSize = 14.sp,
                    color = Color(0xFFE65100),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SpectralSlider(
    lightSource: LightSource,
    value: Float,
    onValueChange: (Float) -> Unit,
    isEnabled: Boolean = true,
    modifier: Modifier = Modifier,
    isFrozen: Boolean = false,
    showMasterMode: Boolean = false,
    onToggleFreeze: () -> Unit = {}
) {
    var sliderValue by remember(value) { mutableStateOf(value) }

    Column(modifier = modifier) {
        // Slider header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Freeze checkbox (only show in master mode)
                if (showMasterMode) {
                    Checkbox(
                        checked = isFrozen,
                        onCheckedChange = { onToggleFreeze() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = PrimaryBlue,
                            uncheckedColor = Color.Gray
                        ),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = lightSource.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isFrozen && showMasterMode) Color.Gray else Color.Black
                )
                
                // Frozen indicator
                if (isFrozen && showMasterMode) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Frozen",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Value display
            Box(
                modifier = Modifier
                    .background(
                        color = if (isFrozen && showMasterMode) 
                            Color.Gray.copy(alpha = 0.1f) 
                        else 
                            PrimaryBlue.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${(value * 100).toInt()}%",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFrozen && showMasterMode) Color.Gray else PrimaryBlue
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Slider
        Slider(
            value = sliderValue,
            onValueChange = {
                sliderValue = it
            },
            onValueChangeFinished = {
                onValueChange(sliderValue)
            },
            valueRange = 0f..1f,
            enabled = isEnabled,
            colors = SliderDefaults.colors(
                thumbColor = if (isFrozen && showMasterMode) Color.Gray else PrimaryBlue,
                activeTrackColor = if (isFrozen && showMasterMode) 
                    Color.Gray.copy(alpha = 0.8f) 
                else 
                    PrimaryBlue.copy(alpha = 0.8f),
                inactiveTrackColor = if (isFrozen && showMasterMode) 
                    Color.Gray.copy(alpha = 0.3f) 
                else 
                    PrimaryBlue.copy(alpha = 0.3f),
                disabledThumbColor = Color.Gray,
                disabledActiveTrackColor = Color.Gray.copy(alpha = 0.5f),
                disabledInactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
fun SpectralPowerDistributionChart(
    graphData: List<GraphPoint>,
    title: String = "Spectral Power Distribution",
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(6.dp)
    ) {
        // Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.ShowChart,
                contentDescription = "SPD Chart",
                tint = PrimaryBlue,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.Black
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (graphData.isNotEmpty()) {
            // Custom SPD Chart using Canvas
            val textMeasurer = rememberTextMeasurer()

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)  // ← DECREASED: Was 350.dp, now 250.dp
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White)
                    .padding(2.dp)
            ) {
                drawSPDChart(
                    graphData = graphData,
                    textMeasurer = textMeasurer,
                    canvasWidth = size.width,
                    canvasHeight = size.height
                )
            }
        } else {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
                    .background(
                        Color.Gray.copy(alpha = 0.1f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = "No Data",
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )

                    Text(
                        text = "No spectral data available",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )

                    Text(
                        text = "Upload Excel file to view SPD",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

/**
 * Convert wavelength to RGB color based on the visible spectrum
 * Based on Dan Bruton's algorithm: http://www.physics.sfasu.edu/astro/color/spectra.html
 */
private fun wavelengthToRGB(wavelength: Double, gamma: Double = 0.8): Color {
    val wl = wavelength.toFloat()
    val r: Float
    val g: Float
    val b: Float
    
    when {
        wl >= 380 && wl <= 440 -> {
            val attenuation = 0.3f + 0.7f * (wl - 380) / (440 - 380)
            r = (-(wl - 440) / (440 - 380) * attenuation).pow(gamma.toFloat())
            g = 0.0f
            b = (1.0f * attenuation).pow(gamma.toFloat())
        }
        wl >= 440 && wl <= 490 -> {
            r = 0.0f
            g = ((wl - 440) / (490 - 440)).pow(gamma.toFloat())
            b = 1.0f
        }
        wl >= 490 && wl <= 510 -> {
            r = 0.0f
            g = 1.0f
            b = (-(wl - 510) / (510 - 490)).pow(gamma.toFloat())
        }
        wl >= 510 && wl <= 580 -> {
            r = ((wl - 510) / (580 - 510)).pow(gamma.toFloat())
            g = 1.0f
            b = 0.0f
        }
        wl >= 580 && wl <= 645 -> {
            r = 1.0f
            g = (-(wl - 645) / (645 - 580)).pow(gamma.toFloat())
            b = 0.0f
        }
        wl >= 645 && wl <= 750 -> {
            val attenuation = 0.3f + 0.7f * (750 - wl) / (750 - 645)
            r = (1.0f * attenuation).pow(gamma.toFloat())
            g = 0.0f
            b = 0.0f
        }
        else -> {
            r = 0.0f
            g = 0.0f
            b = 0.0f
        }
    }
    
    return Color(r, g, b, 1.0f)
}

private fun DrawScope.drawSPDChart(
    graphData: List<GraphPoint>,
    textMeasurer: TextMeasurer,
    canvasWidth: Float,
    canvasHeight: Float
) {
    if (graphData.isEmpty()) return

    val paddingHorizontal = 12.dp.toPx()
    val paddingTop = 12.dp.toPx()
    val paddingBottom = 35.dp.toPx() // Extra space for x-axis labels
    val chartWidth = canvasWidth - (paddingHorizontal * 2)
    val chartHeight = canvasHeight - paddingTop - paddingBottom

    // Find min and max values for scaling
    val minWavelength = graphData.minOfOrNull { it.wavelength } ?: 350
    val maxWavelength = graphData.maxOfOrNull { it.wavelength } ?: 800 // Corrected this to maxOfOrNull
    val maxIntensity = graphData.maxOfOrNull { it.finalIntensity } ?: 1f

    // Create path for the line and filled area
    val linePath = Path()
    val fillPath = Path()

            // Calculate points
        val points = graphData.map { point ->
            val x = paddingHorizontal + ((point.wavelength - minWavelength).toFloat() / (maxWavelength - minWavelength)) * chartWidth
            val y = paddingTop + (1f - (point.finalIntensity / maxIntensity)) * chartHeight
            Offset(x, y)
        }

    if (points.isNotEmpty()) {
        // Start the line path
        linePath.moveTo(points[0].x, points[0].y)

                    // Start the fill path from bottom
            fillPath.moveTo(points[0].x, paddingTop + chartHeight)
            fillPath.lineTo(points[0].x, points[0].y)

        // Draw smooth curve through all points
        for (i in 1 until points.size) {
            linePath.lineTo(points[i].x, points[i].y)
            fillPath.lineTo(points[i].x, points[i].y)
        }

                    // Close the fill path to bottom
            fillPath.lineTo(points.last().x, paddingTop + chartHeight)
            fillPath.close()

        // Draw spectrum-colored filled areas for each segment
        for (i in 0 until graphData.size - 1) {
            val currentPoint = graphData[i]
            val nextPoint = graphData[i + 1]
            
            // Get spectrum color for current wavelength with enhanced saturation
            val spectrumColor = wavelengthToRGB(currentPoint.wavelength.toDouble(), gamma = 0.6)
            
            // Create individual segment path for filled area
            val segmentPath = Path()
            val x1 = paddingHorizontal + ((currentPoint.wavelength - minWavelength).toFloat() / (maxWavelength - minWavelength)) * chartWidth
            val y1 = paddingTop + (1f - (currentPoint.finalIntensity / maxIntensity)) * chartHeight
            val x2 = paddingHorizontal + ((nextPoint.wavelength - minWavelength).toFloat() / (maxWavelength - minWavelength)) * chartWidth
            val y2 = paddingTop + (1f - (nextPoint.finalIntensity / maxIntensity)) * chartHeight
            
            // Create filled segment
            segmentPath.moveTo(x1, paddingTop + chartHeight) // Bottom left
            segmentPath.lineTo(x1, y1) // Top left (data point)
            segmentPath.lineTo(x2, y2) // Top right (next data point)
            segmentPath.lineTo(x2, paddingTop + chartHeight) // Bottom right
            segmentPath.close()
            
            // Draw filled segment with spectrum color and vertical gradient
            drawPath(
                path = segmentPath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        spectrumColor.copy(alpha = 0.9f),
                        spectrumColor.copy(alpha = 0.6f)
                    ),
                    startY = paddingTop,
                    endY = paddingTop + chartHeight
                )
            )
        }

        // Draw the line with slightly thicker stroke for better visibility
        drawPath(
            path = linePath,
            color = Color.Black.copy(alpha = 0.7f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
        )
    }

    // --- REMOVED X-axis line and Y-axis line ---

    // --- Responsive X-axis labels based on available width ---
    // Calculate how many labels can fit based on chart width
    val labelWidth = 50.dp.toPx() // Approximate width needed per label (e.g., "380nm")
    val maxLabels = (chartWidth / labelWidth).toInt().coerceAtLeast(3).coerceAtMost(8)
    val xLabelCount = maxLabels - 1 // For proper spacing (0 to count)
    
    // Responsive font size based on available space
    val fontSize = when {
        chartWidth < 200.dp.toPx() -> 8.sp
        chartWidth < 300.dp.toPx() -> 9.sp
        else -> 10.sp
    }
    
    for (i in 0..xLabelCount) {
        val wavelength = minWavelength + (maxWavelength - minWavelength) * i / xLabelCount
        val x = paddingHorizontal + (i.toFloat() / xLabelCount) * chartWidth

        // Create label text
        val labelText = "${wavelength}nm"
        
        // Measure text to center it properly
        val textStyle = TextStyle(fontSize = fontSize)
        val textSize = textMeasurer.measure(
            text = labelText,
            style = textStyle
        )
        
        // Calculate centered position with bounds checking
        val textX = (x - textSize.size.width / 2)
            .coerceAtLeast(paddingHorizontal / 2)
            .coerceAtMost(canvasWidth - paddingHorizontal / 2 - textSize.size.width)

        // Draw label
        drawText(
            textMeasurer = textMeasurer,
            text = labelText,
            topLeft = Offset(textX, paddingTop + chartHeight + 8.dp.toPx()),
            style = TextStyle(
                color = Color.Gray,
                fontSize = fontSize
            )
        )
    }


}

@Composable
private fun PresetManagementCard(
    presets: List<SpectrumPreset>,
    onSavePreset: (String) -> Unit,
    onLoadPreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFEFE)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with save button
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
                        imageVector = Icons.Default.Bookmark,
                        contentDescription = "Presets",
                        tint = PrimaryBlue,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Spectrum Presets",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.Black
                    )
                }

                Button(
                    onClick = { showSaveDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = "Save",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save", fontSize = 14.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Presets list
            if (presets.isEmpty()) {
                Text(
                    text = "No saved presets",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                presets.forEach { preset ->
                    PresetItem(
                        preset = preset,
                        onLoad = { onLoadPreset(preset.id) },
                        onDelete = { onDeletePreset(preset.id) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // Save preset dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSaveDialog = false
                presetName = ""
            },
            title = { Text("Save Spectrum Preset") },
            text = {
                Column {
                    Text("Enter a name for this spectrum configuration:")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = presetName,
                        onValueChange = { presetName = it },
                        placeholder = { Text("Preset name") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (presetName.isNotBlank()) {
                            onSavePreset(presetName)
                            showSaveDialog = false
                            presetName = ""
                        }
                    }
                ) {
                    Text("Save", color = PrimaryBlue)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSaveDialog = false
                        presetName = ""
                    }
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            containerColor = Color(0xFFFFFEFE)

        )
    }
}

@Composable
private fun PresetItem(
    preset: SpectrumPreset,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Color(0xFFFFFEFE),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            Text(
                text = "Saved ${android.text.format.DateFormat.format("MMM dd, HH:mm", preset.createdAt).toString()}",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onLoad,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowCircleUp,
                    contentDescription = "Load",
                    tint = PrimaryBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun MasterSliderCard(
    masterSliderConfig: MasterSliderConfig,
    onToggleMasterSlider: () -> Unit,
    onMasterSliderChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (masterSliderConfig.isEnabled)
                Color(0xFFFFFEFE)
            else
                Color(0xFF684BE9)

        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header with toggle
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
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Master Control",
                        tint = if (masterSliderConfig.isEnabled) PrimaryBlue else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Set Spectrum Mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (masterSliderConfig.isEnabled) PrimaryBlue else Color.White
                    )
                }

                Switch(
                    checked = masterSliderConfig.isEnabled,
                    onCheckedChange = { onToggleMasterSlider() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = PrimaryBlue,
                        checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f)
                    )
                )
            }

            if (masterSliderConfig.isEnabled) {
                Spacer(modifier = Modifier.height(16.dp))

                // Master slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Master Intensity",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black
                    )

                    Box(
                        modifier = Modifier
                            .background(
                                color = PrimaryBlue.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${(masterSliderConfig.masterValue * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Slider(
                    value = masterSliderConfig.masterValue,
                    onValueChange = onMasterSliderChange,
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = PrimaryBlue,
                        activeTrackColor = PrimaryBlue.copy(alpha = 0.8f),
                        inactiveTrackColor = PrimaryBlue.copy(alpha = 0.3f)
                    )
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Check individual sliders to freeze them at current values",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }
    }
}