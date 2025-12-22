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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Simple Image to PDF Converter
 *
 * Add to build.gradle:
 * implementation 'com.tom-roush:pdfbox-android:2.0.27.0'
 * implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
 */
class ImageToPdfConverter(private val context: Context) {

    init {
        // Initialize PDFBox (required once)
        PDFBoxResourceLoader.init(context)
    }

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
                // Load image from URI
                val bitmap = loadBitmapFromUri(uri) ?: continue

                // Create page with A4 dimensions
                val pageWidth = 595f  // A4 width in points
                val pageHeight = 842f // A4 height in points
                val page = PDPage(PDRectangle(pageWidth, pageHeight))
                document.addPage(page)

                // Convert bitmap to PDF image
                val pdImage = JPEGFactory.createFromImage(document, bitmap, quality / 100f)

                // Draw image on page (fit to page while maintaining aspect ratio)
                PDPageContentStream(document, page).use { contentStream ->
                    val imgRatio = bitmap.width.toFloat() / bitmap.height
                    val pageRatio = pageWidth / pageHeight

                    val (drawWidth, drawHeight) = if (imgRatio > pageRatio) {
                        // Image is wider - fit to width
                        Pair(pageWidth, pageWidth / imgRatio)
                    } else {
                        // Image is taller - fit to height
                        Pair(pageHeight * imgRatio, pageHeight)
                    }

                    // Center image on page
                    val x = (pageWidth - drawWidth) / 2
                    val y = (pageHeight - drawHeight) / 2

                    contentStream.drawImage(pdImage, x, y, drawWidth, drawHeight)
                }

                bitmap.recycle()
            }

            // Save PDF
            document.save(outputFile)
            document.close()

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load bitmap from URI
     */
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
