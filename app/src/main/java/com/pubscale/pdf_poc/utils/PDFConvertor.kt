package com.pubscale.pdf_poc.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.XWPFDocument
import java.io.File
import java.io.FileOutputStream

/**
 * PDF Converter - Images to PDF & PDF to Word
 *
 * Add to build.gradle:
 * implementation 'com.tom-roush:pdfbox-android:2.0.27.0'
 * implementation 'org.apache.poi:poi-ooxml:5.2.3'
 * implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
 */
class PdfConverter(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    // ==========================================
    // IMAGES TO PDF
    // ==========================================

    /**
     * Convert multiple images to a single PDF
     *
     * @param imageUris List of image URIs from gallery/camera
     * @param outputFile Where to save the PDF
     * @param quality JPEG quality (0-100), default 85
     * @return Result with output file or error
     */
    suspend fun convertImagesToPdf(
        imageUris: List<Uri>,
        outputFile: File,
        quality: Int = 85
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val document = PDDocument()

            for (uri in imageUris) {
                val bitmap = loadBitmapFromUri(uri) ?: continue

                val pageWidth = 595f  // A4 width
                val pageHeight = 842f // A4 height
                val page = PDPage(PDRectangle(pageWidth, pageHeight))
                document.addPage(page)

                val pdImage = JPEGFactory.createFromImage(document, bitmap, quality / 100f)

                PDPageContentStream(document, page).use { contentStream ->
                    val imgRatio = bitmap.width.toFloat() / bitmap.height
                    val pageRatio = pageWidth / pageHeight

                    val (drawWidth, drawHeight) = if (imgRatio > pageRatio) {
                        Pair(pageWidth, pageWidth / imgRatio)
                    } else {
                        Pair(pageHeight * imgRatio, pageHeight)
                    }

                    val x = (pageWidth - drawWidth) / 2
                    val y = (pageHeight - drawHeight) / 2

                    contentStream.drawImage(pdImage, x, y, drawWidth, drawHeight)
                }

                bitmap.recycle()
            }

            document.save(outputFile)
            document.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // PDF TO WORD
    // ==========================================

    /**
     * Convert PDF to Word document (.docx)
     *
     * @param pdfFile Input PDF file
     * @param outputFile Where to save the Word document
     * @return Result with output file or error
     */
    suspend fun convertPdfToWord(
        pdfFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            // Load PDF
            PDDocument.load(pdfFile).use { document ->
                // Extract text from PDF
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)

                // Create Word document
                val wordDoc = XWPFDocument()

                // Split text by pages if needed
                val pages = text.split("\u000c") // Form feed character separates pages

                for ((index, pageText) in pages.withIndex()) {
                    if (pageText.trim().isEmpty()) continue

                    // Add page break between pages (except first)
                    if (index > 0) {
                        val paragraph = wordDoc.createParagraph()
                        paragraph.isPageBreak = true
                    }

                    // Split into paragraphs
                    val paragraphs = pageText.split("\n\n")

                    for (paraText in paragraphs) {
                        if (paraText.trim().isEmpty()) continue

                        val paragraph = wordDoc.createParagraph()
                        val run = paragraph.createRun()
                        run.setText(paraText.trim())
                        run.fontSize = 11
                    }
                }

                // Save Word document
                FileOutputStream(outputFile).use { out ->
                    wordDoc.write(out)
                }
                wordDoc.close()
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert PDF to Word with custom formatting
     */
    suspend fun convertPdfToWordAdvanced(
        pdfFile: File,
        outputFile: File,
        options: WordConversionOptions = WordConversionOptions()
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)

                val wordDoc = XWPFDocument()

                // Split by lines
                val lines = text.split("\n")

                for (line in lines) {
                    if (line.trim().isEmpty()) continue

                    val paragraph = wordDoc.createParagraph()
                    val run = paragraph.createRun()
                    run.setText(line.trim())
                    run.fontSize = options.fontSize
                    run.fontFamily = options.fontFamily

                    // Auto-detect headings (simple heuristic)
                    if (options.autoDetectHeadings && line.length < 100 &&
                        line.trim().matches(Regex("^[A-Z][A-Za-z\\s]+$"))) {
                        run.isBold = true
                        run.fontSize = options.fontSize + 2
                    }
                }

                FileOutputStream(outputFile).use { out ->
                    wordDoc.write(out)
                }
                wordDoc.close()
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert PDF to plain text
     */
    suspend fun convertPdfToText(
        pdfFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)

                outputFile.writeText(text)
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Word conversion options
 */
data class WordConversionOptions(
    val fontSize: Int = 11,
    val fontFamily: String = "Calibri",
    val autoDetectHeadings: Boolean = true
)

