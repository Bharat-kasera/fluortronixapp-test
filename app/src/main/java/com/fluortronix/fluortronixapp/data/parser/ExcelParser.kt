package com.fluortronix.fluortronixapp.data.parser

import com.fluortronix.fluortronixapp.data.models.LightSource
import com.fluortronix.fluortronixapp.data.models.SpectralPoint
import com.fluortronix.fluortronixapp.data.models.SpectralProfile
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcelParser @Inject constructor() {

    /**
     * Parse Excel file and return SpectralProfile
     * Expected format:
     * Sheet 1: "Spectrum Values"
     * - Row 1: Power percentages for each light source
     * - Row 2: Intensity factors for each light source
     * - Row 3: Light source names (headers)
     * - Rows 4+: Wavelength data with intensities
     * 
     * Sheet 2: "Color Codes"
     * - Column A: Index
     * - Column B: Light source name
     * - Column C: Color code (hex)
     */
    fun parse(inputStream: InputStream, deviceModel: String? = null): SpectralProfile {
        println("DEBUG: Starting Excel parsing...")
        
        val workbook = try {
            XSSFWorkbook(inputStream)
        } catch (e: Exception) {
            println("DEBUG: Failed to open Excel workbook: ${e.message}")
            throw IllegalArgumentException("Failed to open Excel file: ${e.message}", e)
        }
        
        try {
            // --- Sheet 1: "Spectrum Values" ---
            val spectrumSheet = workbook.getSheet("Spectrum Values") 
                ?: throw IllegalArgumentException("Sheet 'Spectrum Values' not found")

            // Read headers (light source names) from Row 3 (index 2)
            val headerRow = spectrumSheet.getRow(2) 
                ?: throw IllegalArgumentException("Header row (row 3) not found")
            
            val headers = mutableListOf<String>()
            val headerIterator = headerRow.cellIterator()
            
            // Skip first cell (should be "WL(nm)")
            if (headerIterator.hasNext()) headerIterator.next()
            
            // Read light source names
            while (headerIterator.hasNext()) {
                val cell = headerIterator.next()
                val cellValue = getCellStringValue(cell)
                if (cellValue.isNotBlank()) {
                    headers.add(cellValue)
                } else {
                    break
                }
            }

            if (headers.isEmpty()) {
                throw IllegalArgumentException("No light source headers found")
            }

            // Read power percentages from Row 1 (index 0)
            val powerRow = spectrumSheet.getRow(0)
                ?: throw IllegalArgumentException("Power percentage row (row 1) not found")
            
            val powerPercentages = mutableMapOf<String, Float>()
            headers.forEachIndexed { index, name ->
                val cell = powerRow.getCell(index + 1) // +1 to skip first column
                powerPercentages[name] = getCellNumericValue(cell)
            }

            // Read intensity factors from Row 2 (index 1)
            val intensityRow = spectrumSheet.getRow(1)
                ?: throw IllegalArgumentException("Intensity factor row (row 2) not found")
            
            val intensityFactors = mutableMapOf<String, Float>()
            headers.forEachIndexed { index, name ->
                val cell = intensityRow.getCell(index + 1) // +1 to skip first column
                intensityFactors[name] = getCellNumericValue(cell)
            }

            // --- Sheet 2: "Color Codes" ---
            val colorSheet = workbook.getSheet("Color Codes")
                ?: throw IllegalArgumentException("Sheet 'Color Codes' not found")
            
            val colorCodes = mutableMapOf<String, String>()
            val colorRowIterator = colorSheet.rowIterator()
            
            // Skip header row
            if (colorRowIterator.hasNext()) colorRowIterator.next()
            
            while (colorRowIterator.hasNext()) {
                val row = colorRowIterator.next()
                val nameCell = row.getCell(1) // Column B
                val colorCell = row.getCell(2) // Column C
                
                if (nameCell != null && colorCell != null) {
                    val name = getCellStringValue(nameCell)
                    val color = getCellStringValue(colorCell)
                    if (name.isNotBlank() && color.isNotBlank()) {
                        colorCodes[name] = color
                    }
                }
            }

            // --- Combine into LightSource objects ---
            val lightSources = headers.map { name ->
                LightSource(
                    name = name,
                    colorCode = colorCodes[name] ?: "#FFFFFF",
                    intensityFactor = intensityFactors[name] ?: 0f,
                    initialPower = powerPercentages[name] ?: 0f
                )
            }

            // --- Read the full spectrum data ---
            val spectralData = mutableListOf<SpectralPoint>()
            val dataRowIterator = spectrumSheet.rowIterator()
            
            // Skip first 3 rows (power, intensity, headers)
            repeat(3) {
                if (dataRowIterator.hasNext()) dataRowIterator.next()
            }
            
            while (dataRowIterator.hasNext()) {
                val row = dataRowIterator.next()
                val wavelengthCell = row.getCell(0)
                
                if (wavelengthCell != null) {
                    val wavelength = getCellNumericValue(wavelengthCell).toInt()
                    
                    val intensities = mutableMapOf<String, Float>()
                    headers.forEachIndexed { index, name ->
                        val intensityCell = row.getCell(index + 1)
                        intensities[name] = getCellNumericValue(intensityCell)
                    }
                    
                    spectralData.add(SpectralPoint(wavelength, intensities))
                }
            }

            return SpectralProfile(
                sources = lightSources,
                spectrum = spectralData,
                deviceModel = deviceModel
            )
            
        } finally {
            workbook.close()
        }
    }

    /**
     * Helper function to safely get numeric value from cell
     */
    private fun getCellNumericValue(cell: Cell?): Float {
        return when {
            cell == null -> 0f
            cell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toFloat()
            cell.cellType == org.apache.poi.ss.usermodel.CellType.STRING -> {
                try {
                    cell.stringCellValue.toFloat()
                } catch (e: NumberFormatException) {
                    0f
                }
            }
            else -> 0f
        }
    }

    /**
     * Helper function to safely get string value from cell
     */
    private fun getCellStringValue(cell: Cell?): String {
        return when {
            cell == null -> ""
            cell.cellType == org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue.trim()
            cell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toString()
            else -> ""
        }
    }
} 