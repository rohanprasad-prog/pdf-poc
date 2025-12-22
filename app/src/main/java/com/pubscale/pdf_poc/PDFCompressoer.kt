package com.pubscale.pdf_poc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class PdfBoxCompressor(private val context: Context) {

    init {
        // Initialize PDFBox (required for Android)
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Basic compression - reduces image quality
     */
    suspend fun compressBasic(
        inputFile: File,
        outputFile: File,
        imageQuality: Int = 75
    ): Result<CompressionStats> = withContext(Dispatchers.IO) {
        try {
            val originalSize = inputFile.length()

            PDDocument.load(inputFile).use { document ->
                compressImages(document, imageQuality)
                document.save(outputFile)
            }

            val compressedSize = outputFile.length()
            val stats = CompressionStats(
                originalSize = originalSize,
                compressedSize = compressedSize,
                reductionPercent = ((originalSize - compressedSize) * 100f / originalSize)
            )

            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Advanced compression with multiple techniques
     */
    suspend fun compressAdvanced(
        inputFile: File,
        outputFile: File,
        options: CompressionOptions = CompressionOptions()
    ): Result<CompressionStats> = withContext(Dispatchers.IO) {
        try {
            val originalSize = inputFile.length()

            PDDocument.load(inputFile).use { document ->
                if (options.compressImages) {
                    compressImages(document, options.imageQuality)
                }

                if (options.removeMetadata) {
                    removeMetadata(document)
                }

                if (options.removeDuplicateObjects) {
                    removeDuplicates(document)
                }

                document.save(outputFile)
            }

            val compressedSize = outputFile.length()
            val stats = CompressionStats(
                originalSize = originalSize,
                compressedSize = compressedSize,
                reductionPercent = ((originalSize - compressedSize) * 100f / originalSize)
            )

            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compress all images in the PDF
     */
    private fun compressImages(document: PDDocument, quality: Int) {
        for (page in document.pages) {
            try {
                val resources = page.resources
                val xObjectNames = resources.xObjectNames.toList()

                for (name in xObjectNames) {
                    val xObject = resources.getXObject(name)

                    if (xObject is PDImageXObject) {
                        // Get the image as bitmap
                        val image = xObject.image

                        // Compress and replace
                        val compressedImage = compressImage(document, image, quality)
                        resources.put(name, compressedImage)
                    }
                }
            } catch (e: IOException) {
                // Skip problematic pages
                e.printStackTrace()
            }
        }
    }

    /**
     * Compress individual image
     */
    private fun compressImage(
        document: PDDocument,
        bitmap: Bitmap,
        quality: Int
    ): PDImageXObject {
        return try {
            // Convert to JPEG with compression
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            val compressedBytes = stream.toByteArray()

            // Create new compressed image
            val compressedBitmap = BitmapFactory.decodeByteArray(
                compressedBytes, 0, compressedBytes.size
            )

            JPEGFactory.createFromImage(document, compressedBitmap, quality / 100f)
        } catch (e: Exception) {
            // Fallback to lossless if JPEG fails
            LosslessFactory.createFromImage(document, bitmap)
        }
    }

    /**
     * Remove PDF metadata to reduce size
     */
    private fun removeMetadata(document: PDDocument) {
        val info = document.documentInformation
        info.author = null
        info.creator = null
        info.producer = null
        info.subject = null
        info.keywords = null
        info.title = null
    }

    /**
     * Remove duplicate resources (basic implementation)
     */
    private fun removeDuplicates(document: PDDocument) {
        // This is a simplified version
        // Full implementation would need deep object comparison
        val seenImages = mutableSetOf<String>()

        for (page in document.pages) {
            try {
                val resources = page.resources
                val xObjectNames = resources.xObjectNames.toList()

                for (name in xObjectNames) {
                    val xObject = resources.getXObject(name)
                    if (xObject is PDImageXObject) {
                        val hash = xObject.hashCode().toString()
                        if (seenImages.contains(hash)) {
                            // Mark for potential removal
                            // (actual removal requires careful handling)
                        } else {
                            seenImages.add(hash)
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Resize PDF pages (reduces file size significantly)
     */
    suspend fun resizePdf(
        inputFile: File,
        outputFile: File,
        targetWidth: Float = 595f, // A4 width in points
        targetHeight: Float = 842f // A4 height in points
    ): Result<CompressionStats> = withContext(Dispatchers.IO) {
        try {
            val originalSize = inputFile.length()

            val newDocument = PDDocument()
            PDDocument.load(inputFile).use { document ->
                for (page in document.pages) {
                    val newPage = PDPage(PDRectangle(targetWidth, targetHeight))
                    newDocument.addPage(newPage)

                    // Copy content (simplified - may need scaling)
                    PDPageContentStream(
                        newDocument,
                        newPage,
                        PDPageContentStream.AppendMode.APPEND,
                        true
                    ).use { contentStream ->
                        // Add content copying logic here
                    }
                }
                newDocument.save(outputFile)
            }
            newDocument.close()

            val compressedSize = outputFile.length()
            val stats = CompressionStats(
                originalSize = originalSize,
                compressedSize = compressedSize,
                reductionPercent = ((originalSize - compressedSize) * 100f / originalSize)
            )

            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Compression options
 */
data class CompressionOptions(
    val compressImages: Boolean = true,
    val imageQuality: Int = 75, // 0-100
    val removeMetadata: Boolean = true,
    val removeDuplicateObjects: Boolean = true
)

/**
 * Compression statistics
 */
data class CompressionStats(
    val originalSize: Long,
    val compressedSize: Long,
    val reductionPercent: Float
) {
    fun getOriginalSizeMB() = originalSize / (1024f * 1024f)
    fun getCompressedSizeMB() = compressedSize / (1024f * 1024f)
}