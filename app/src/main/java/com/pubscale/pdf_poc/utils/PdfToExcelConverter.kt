package com.pubscale.pdf_poc.utils

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream

/**
 * PDF to Excel Converter
 *
 * GRADLE DEPENDENCIES:
 * ===================
 *
 * OPTION 1 - For XLSX (requires minSdk 26+):
 * dependencies {
 *     implementation 'com.tom-roush:pdfbox-android:2.0.27.0'
 *     implementation 'org.apache.poi:poi-ooxml:5.2.3'
 *     implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
 * }
 *
 * android {
 *     defaultConfig {
 *         minSdk 26  // Required for Apache POI
 *     }
 * }
 *
 * OPTION 2 - For CSV (works on minSdk 21+):
 * dependencies {
 *     implementation 'com.tom-roush:pdfbox-android:2.0.27.0'
 *     implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
 * }
 */
class PdfToExcelConverter(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    // ==========================================
    // PDF TO XLSX (Excel format)
    // ==========================================

    /**
     * Convert PDF to Excel (.xlsx) - FIXED VERSION
     * Requires minSdk 26+
     *
     * @param pdfFile Input PDF file
     * @param outputFile Output Excel file (.xlsx)
     * @return Result with output file or error
     */
    suspend fun convertPdfToExcel(
        pdfFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        var workbook: XSSFWorkbook? = null
        var fileOut: FileOutputStream? = null

        try {
            // Extract text from PDF
            val pdfText = extractTextFromPdf(pdfFile)

            // Create Excel workbook
            workbook = XSSFWorkbook()
            val sheet = workbook.createSheet("PDF Content")

            // Parse text into rows
            val lines = pdfText.lines().filter { it.trim().isNotEmpty() }

            var rowIndex = 0
            for (line in lines) {
                val row = sheet.createRow(rowIndex++)

                // Try to detect if line has tabular data
                val cells = detectColumns(line)

                for ((colIndex, cellText) in cells.withIndex()) {
                    val cell = row.createCell(colIndex)

                    // Try to set as number if possible
                    val trimmed = cellText.trim()
                    val numValue = trimmed.replace(",", "").toDoubleOrNull()

                    if (numValue != null && trimmed.matches(Regex("^[0-9.,]+$"))) {
                        cell.setCellValue(numValue)
                    } else {
                        cell.setCellValue(trimmed)
                    }
                }
            }

            // Auto-size columns (limit to prevent issues)
            val maxCol = minOf(sheet.getRow(0)?.lastCellNum?.toInt() ?: 0, 20)
            for (i in 0 until maxCol) {
                try {
                    sheet.autoSizeColumn(i)
                } catch (e: Exception) {
                    // Ignore column sizing errors
                }
            }

            // IMPORTANT: Write to file and flush
            fileOut = FileOutputStream(outputFile)
            workbook.write(fileOut)
            fileOut.flush()

            Result.success(outputFile)
        } catch (e: Exception) {
            // Clean up corrupted file
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        } finally {
            // Properly close everything
            try {
                fileOut?.close()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                workbook?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Convert PDF to Excel with custom parsing - FIXED VERSION
     * Better for structured/tabular PDFs
     */
    suspend fun convertPdfToExcelAdvanced(
        pdfFile: File,
        outputFile: File,
        options: ExcelConversionOptions = ExcelConversionOptions()
    ): Result<File> = withContext(Dispatchers.IO) {
        var workbook: XSSFWorkbook? = null
        var fileOut: FileOutputStream? = null

        try {
            val pdfText = extractTextFromPdf(pdfFile)
            workbook = XSSFWorkbook()
            val sheet = workbook.createSheet(options.sheetName)

            // Create header style
            val headerStyle = workbook.createCellStyle()
            val headerFont = workbook.createFont()
            headerFont.bold = true
            headerFont.fontHeightInPoints = 12
            headerStyle.setFont(headerFont)

            val lines = pdfText.lines().filter { it.trim().isNotEmpty() }

            var rowIndex = 0

            // Process lines
            for ((lineIndex, line) in lines.withIndex()) {
                val row = sheet.createRow(rowIndex++)
                val cells = when (options.delimiter) {
                    DelimiterType.AUTO -> detectColumns(line)
                    DelimiterType.TAB -> line.split("\t")
                    DelimiterType.COMMA -> line.split(",")
                    DelimiterType.SPACE -> line.split(Regex("\\s{2,}"))
                    DelimiterType.PIPE -> line.split("|")
                }

                for ((colIndex, cellText) in cells.withIndex()) {
                    val cell = row.createCell(colIndex)

                    // Try to parse as number
                    val value = cellText.trim()
                    val numValue = value.replace(",", "").toDoubleOrNull()

                    if (numValue != null && value.matches(Regex("^[0-9.,]+$"))) {
                        cell.setCellValue(numValue)
                    } else {
                        cell.setCellValue(value)
                    }

                    // Apply header style to first row
                    if (lineIndex == 0 && options.firstRowAsHeader) {
                        cell.cellStyle = headerStyle
                    }
                }
            }

            // Auto-size columns (safe limit)
            val maxCol = minOf(sheet.getRow(0)?.lastCellNum?.toInt() ?: 0, 20)
            for (i in 0 until maxCol) {
                try {
                    // Basic version - fixed width
                    sheet.setColumnWidth(i, 5120)  // 20 characters wide

// Advanced version - smart width calculation
                    var maxLength = 10
                    for (row in sheet) {
                        val cellValue = row.getCell(i)?.stringCellValue ?: ""
                        maxLength = maxOf(maxLength, cellValue.length)
                    }
                    sheet.setColumnWidth(i, minOf(maxLength, 30) * 256)
                } catch (e: Exception) {
                    // Skip problematic columns
                }
            }

            // CRITICAL: Proper file writing
            fileOut = FileOutputStream(outputFile)
            workbook.write(fileOut)
            fileOut.flush()

            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        } finally {
            try {
                fileOut?.close()
            } catch (e: Exception) { }
            try {
                workbook?.close()
            } catch (e: Exception) { }
        }
    }

    // ==========================================
    // PDF TO CSV (Works on minSdk 21+)
    // ==========================================

    /**
     * Convert PDF to CSV (Comma Separated Values)
     * Works on any Android version (minSdk 21+)
     * CSV files open in Excel, Google Sheets, etc.
     */
    suspend fun convertPdfToCsv(
        pdfFile: File,
        outputFile: File,
        delimiter: String = ","
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val pdfText = extractTextFromPdf(pdfFile)

            val csvContent = buildString {
                val lines = pdfText.lines()

                for (line in lines) {
                    if (line.trim().isEmpty()) continue

                    // Detect columns
                    val cells = detectColumns(line)

                    // Escape and join cells
                    val escapedCells = cells.map { cell ->
                        val escaped = cell.replace("\"", "\"\"") // Escape quotes
                        if (cell.contains(delimiter) || cell.contains("\"") || cell.contains("\n")) {
                            "\"$escaped\"" // Wrap in quotes if contains delimiter
                        } else {
                            escaped
                        }
                    }

                    appendLine(escapedCells.joinToString(delimiter))
                }
            }

            outputFile.writeText(csvContent)
            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert PDF to TSV (Tab Separated Values)
     * Opens in Excel without formatting issues
     */
    suspend fun convertPdfToTsv(
        pdfFile: File,
        outputFile: File
    ): Result<File> = convertPdfToCsv(pdfFile, outputFile, delimiter = "\t")

    // ==========================================
    // HELPER METHODS
    // ==========================================

    /**
     * Extract all text from PDF
     */
    private fun extractTextFromPdf(pdfFile: File): String {
        return PDDocument.load(pdfFile).use { document ->
            val stripper = PDFTextStripper()
            stripper.getText(document)
        }
    }

    /**
     * Detect columns in a line of text
     * Uses multiple strategies to identify column boundaries
     */
    private fun detectColumns(line: String): List<String> {
        return when {
            // Tab-separated
            line.contains("\t") -> line.split("\t")

            // Comma-separated (but not in sentences)
            line.count { it == ',' } >= 2 && !line.contains(". ") ->
                line.split(",")

            // Multiple spaces (common in tables)
            line.contains(Regex("\\s{2,}")) ->
                line.split(Regex("\\s{2,}"))

            // Pipe-separated
            line.contains("|") ->
                line.split("|")

            // Single column
            else -> listOf(line)
        }.map { it.trim() }
    }

    /**
     * Convert PDF from URI
     */
    suspend fun convertPdfToExcelFromUri(
        uri: Uri,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Copy URI to temp file
            val tempPdf = File(context.cacheDir, "temp_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempPdf.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Convert
            val result = convertPdfToExcel(tempPdf, outputFile)

            // Cleanup
            tempPdf.delete()

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ==========================================
// DATA CLASSES
// ==========================================

data class ExcelConversionOptions(
    val sheetName: String = "Sheet1",
    val firstRowAsHeader: Boolean = true,
    val delimiter: DelimiterType = DelimiterType.AUTO
)

enum class DelimiterType {
    AUTO,    // Auto-detect
    TAB,     // Tab-separated
    COMMA,   // Comma-separated
    SPACE,   // Multiple spaces
    PIPE     // Pipe-separated (|)
}
