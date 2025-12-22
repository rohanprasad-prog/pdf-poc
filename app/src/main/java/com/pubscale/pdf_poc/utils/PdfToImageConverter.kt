package com.pubscale.pdf_poc.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * PDF to Image Converter for Android
 * Converts PDF pages to image files (PNG, JPEG, WEBP)
 *
 * Requires minSdk 21+ for PdfRenderer
 */
class PdfToImageConverter(private val context: Context) {

    // ==========================================
    // PDF TO IMAGE - SINGLE PAGE
    // ==========================================

    /**
     * Convert a single PDF page to image
     *
     * @param pdfFile Input PDF file
     * @param outputFile Output image file (.png, .jpg, .webp)
     * @param pageIndex Page number (0-indexed)
     * @param options Conversion options
     * @return Result with output file or error
     */
    @RequiresApi(21)
    suspend fun convertPageToImage(
        pdfFile: File,
        outputFile: File,
        pageIndex: Int = 0,
        options: ImageConversionOptions = ImageConversionOptions()
    ): Result<File> = withContext(Dispatchers.IO) {
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null

        try {
            fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            pdfRenderer = PdfRenderer(fileDescriptor)

            if (pageIndex >= pdfRenderer.pageCount) {
                return@withContext Result.failure(
                    IllegalArgumentException("Page $pageIndex does not exist. PDF has ${pdfRenderer.pageCount} pages")
                )
            }

            val page = pdfRenderer.openPage(pageIndex)

            // Calculate dimensions
            val width = if (options.width > 0) {
                options.width
            } else {
                (page.width * options.scale).toInt()
            }

            val height = if (options.height > 0) {
                options.height
            } else {
                (page.height * options.scale).toInt()
            }

            // Create bitmap
            val bitmap = Bitmap.createBitmap(
                width,
                height,
                Bitmap.Config.ARGB_8888
            )

            // Render page to bitmap
            page.render(
                bitmap,
                null,
                null,
                PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
            )

            // Save bitmap to file
            saveBitmap(bitmap, outputFile, options)

            page.close()
            bitmap.recycle()

            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        } finally {
            try {
                pdfRenderer?.close()
            } catch (e: Exception) { }
            try {
                fileDescriptor?.close()
            } catch (e: Exception) { }
        }
    }

    // ==========================================
    // PDF TO IMAGE - ALL PAGES
    // ==========================================

    /**
     * Convert all PDF pages to images
     *
     * @param pdfFile Input PDF file
     * @param outputDir Output directory for images
     * @param fileNamePattern Pattern for output files (e.g., "page_%d.png")
     * @param options Conversion options
     * @return Result with list of output files or error
     */
    @RequiresApi(21)
    suspend fun convertAllPagesToImages(
        pdfFile: File,
        outputDir: File,
        fileNamePattern: String = "page_%d.png",
        options: ImageConversionOptions = ImageConversionOptions()
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        val outputFiles = mutableListOf<File>()

        try {
            // Ensure output directory exists
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            pdfRenderer = PdfRenderer(fileDescriptor)

            // Convert each page
            for (i in 0 until pdfRenderer.pageCount) {
                val page = pdfRenderer.openPage(i)

                // Calculate dimensions
                val width = if (options.width > 0) {
                    options.width
                } else {
                    (page.width * options.scale).toInt()
                }

                val height = if (options.height > 0) {
                    options.height
                } else {
                    (page.height * options.scale).toInt()
                }

                // Create bitmap
                val bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

                // Render page
                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                // Save to file
                val fileName = String.format(fileNamePattern, i + 1)
                val outputFile = File(outputDir, fileName)
                saveBitmap(bitmap, outputFile, options)
                outputFiles.add(outputFile)

                page.close()
                bitmap.recycle()
            }

            Result.success(outputFiles)
        } catch (e: Exception) {
            // Clean up created files
            outputFiles.forEach { it.delete() }
            Result.failure(e)
        } finally {
            try {
                pdfRenderer?.close()
            } catch (e: Exception) { }
            try {
                fileDescriptor?.close()
            } catch (e: Exception) { }
        }
    }

    /**
     * Convert specific pages to images
     *
     * @param pdfFile Input PDF file
     * @param outputDir Output directory for images
     * @param pageIndices List of page indices to convert (0-indexed)
     * @param fileNamePattern Pattern for output files
     * @param options Conversion options
     * @return Result with list of output files or error
     */
    @RequiresApi(21)
    suspend fun convertSpecificPagesToImages(
        pdfFile: File,
        outputDir: File,
        pageIndices: List<Int>,
        fileNamePattern: String = "page_%d.png",
        options: ImageConversionOptions = ImageConversionOptions()
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        val outputFiles = mutableListOf<File>()

        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            pdfRenderer = PdfRenderer(fileDescriptor)

            for (pageIndex in pageIndices) {
                if (pageIndex >= pdfRenderer.pageCount) {
                    continue // Skip invalid page indices
                }

                val page = pdfRenderer.openPage(pageIndex)

                val width = if (options.width > 0) {
                    options.width
                } else {
                    (page.width * options.scale).toInt()
                }

                val height = if (options.height > 0) {
                    options.height
                } else {
                    (page.height * options.scale).toInt()
                }

                val bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                val fileName = String.format(fileNamePattern, pageIndex + 1)
                val outputFile = File(outputDir, fileName)
                saveBitmap(bitmap, outputFile, options)
                outputFiles.add(outputFile)

                page.close()
                bitmap.recycle()
            }

            Result.success(outputFiles)
        } catch (e: Exception) {
            outputFiles.forEach { it.delete() }
            Result.failure(e)
        } finally {
            try {
                pdfRenderer?.close()
            } catch (e: Exception) { }
            try {
                fileDescriptor?.close()
            } catch (e: Exception) { }
        }
    }

    // ==========================================
    // PDF TO IMAGE - WITH PROGRESS
    // ==========================================

    /**
     * Convert all pages with progress callback
     *
     * @param pdfFile Input PDF file
     * @param outputDir Output directory
     * @param fileNamePattern Pattern for output files
     * @param options Conversion options
     * @param onProgress Callback with (currentPage, totalPages, outputFile)
     * @return Result with list of output files
     */
    @RequiresApi(21)
    suspend fun convertAllPagesWithProgress(
        pdfFile: File,
        outputDir: File,
        fileNamePattern: String = "page_%d.png",
        options: ImageConversionOptions = ImageConversionOptions(),
        onProgress: (Int, Int, File) -> Unit
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null
        val outputFiles = mutableListOf<File>()

        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            pdfRenderer = PdfRenderer(fileDescriptor)
            val totalPages = pdfRenderer.pageCount

            for (i in 0 until totalPages) {
                val page = pdfRenderer.openPage(i)

                val width = if (options.width > 0) {
                    options.width
                } else {
                    (page.width * options.scale).toInt()
                }

                val height = if (options.height > 0) {
                    options.height
                } else {
                    (page.height * options.scale).toInt()
                }

                val bitmap = Bitmap.createBitmap(
                    width,
                    height,
                    Bitmap.Config.ARGB_8888
                )

                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                val fileName = String.format(fileNamePattern, i + 1)
                val outputFile = File(outputDir, fileName)
                saveBitmap(bitmap, outputFile, options)
                outputFiles.add(outputFile)

                page.close()
                bitmap.recycle()

                // Report progress
                withContext(Dispatchers.Main) {
                    onProgress(i + 1, totalPages, outputFile)
                }
            }

            Result.success(outputFiles)
        } catch (e: Exception) {
            outputFiles.forEach { it.delete() }
            Result.failure(e)
        } finally {
            try {
                pdfRenderer?.close()
            } catch (e: Exception) { }
            try {
                fileDescriptor?.close()
            } catch (e: Exception) { }
        }
    }

    // ==========================================
    // URI SUPPORT
    // ==========================================

    /**
     * Convert PDF from URI to images
     */
    @RequiresApi(21)
    suspend fun convertPdfFromUri(
        uri: Uri,
        outputDir: File,
        fileNamePattern: String = "page_%d.png",
        options: ImageConversionOptions = ImageConversionOptions()
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        try {
            // Copy URI to temp file
            val tempPdf = File(context.cacheDir, "temp_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempPdf.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Convert
            val result = convertAllPagesToImages(tempPdf, outputDir, fileNamePattern, options)

            // Cleanup
            tempPdf.delete()

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // GET PDF INFO
    // ==========================================

    /**
     * Get PDF page count
     */
    @RequiresApi(21)
    suspend fun getPdfPageCount(pdfFile: File): Result<Int> = withContext(Dispatchers.IO) {
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null

        try {
            fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            pdfRenderer = PdfRenderer(fileDescriptor)
            Result.success(pdfRenderer.pageCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                pdfRenderer?.close()
            } catch (e: Exception) { }
            try {
                fileDescriptor?.close()
            } catch (e: Exception) { }
        }
    }

    /**
     * Get PDF page dimensions
     */
    @RequiresApi(21)
    suspend fun getPdfPageDimensions(
        pdfFile: File,
        pageIndex: Int = 0
    ): Result<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null

        try {
            fileDescriptor = ParcelFileDescriptor.open(
                pdfFile,
                ParcelFileDescriptor.MODE_READ_ONLY
            )
            pdfRenderer = PdfRenderer(fileDescriptor)

            if (pageIndex >= pdfRenderer.pageCount) {
                return@withContext Result.failure(
                    IllegalArgumentException("Page $pageIndex does not exist")
                )
            }

            val page = pdfRenderer.openPage(pageIndex)
            val dimensions = Pair(page.width, page.height)
            page.close()

            Result.success(dimensions)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try {
                pdfRenderer?.close()
            } catch (e: Exception) { }
            try {
                fileDescriptor?.close()
            } catch (e: Exception) { }
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    /**
     * Save bitmap to file with specified format and quality
     */
    private fun saveBitmap(
        bitmap: Bitmap,
        outputFile: File,
        options: ImageConversionOptions
    ) {
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(options.format, options.quality, out)
            out.flush()
        }
    }
}

// ==========================================
// DATA CLASSES
// ==========================================

data class ImageConversionOptions(
    /**
     * Image format (PNG, JPEG, WEBP)
     */
    val format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,

    /**
     * Image quality (0-100, only for JPEG and WEBP)
     * PNG ignores this value
     */
    val quality: Int = 100,

    /**
     * Scale factor (1.0 = original size, 2.0 = double size)
     * Ignored if width or height is specified
     */
    val scale: Float = 1.0f,

    /**
     * Fixed width in pixels (0 = use scale factor)
     */
    val width: Int = 0,

    /**
     * Fixed height in pixels (0 = use scale factor)
     */
    val height: Int = 0
)

// ==========================================
// USAGE EXAMPLES
// ==========================================

/*
val converter = PdfToImageConverter(context)
val pdfFile = File(context.filesDir, "document.pdf")

// Example 1: Convert single page to PNG
lifecycleScope.launch {
    val outputFile = File(context.filesDir, "page1.png")
    val result = converter.convertPageToImage(pdfFile, outputFile, pageIndex = 0)

    result.onSuccess { file ->
        Log.d("Converter", "Image created: ${file.absolutePath}")
    }.onFailure { error ->
        Log.e("Converter", "Conversion failed", error)
    }
}

// Example 2: Convert all pages to PNG images
lifecycleScope.launch {
    val outputDir = File(context.filesDir, "pdf_images")
    val result = converter.convertAllPagesToImages(
        pdfFile = pdfFile,
        outputDir = outputDir,
        fileNamePattern = "page_%d.png"
    )

    result.onSuccess { files ->
        Log.d("Converter", "Created ${files.size} images")
        files.forEach { Log.d("Converter", "- ${it.name}") }
    }
}

// Example 3: Convert with custom options (high quality JPEG)
lifecycleScope.launch {
    val options = ImageConversionOptions(
        format = Bitmap.CompressFormat.JPEG,
        quality = 95,
        scale = 2.0f // Double the size
    )

    val outputFile = File(context.filesDir, "page1.jpg")
    val result = converter.convertPageToImage(pdfFile, outputFile, 0, options)
}

// Example 4: Convert with fixed dimensions
lifecycleScope.launch {
    val options = ImageConversionOptions(
        format = Bitmap.CompressFormat.PNG,
        width = 1920,  // Fixed width
        height = 1080  // Fixed height
    )

    val outputFile = File(context.filesDir, "page1_hd.png")
    converter.convertPageToImage(pdfFile, outputFile, 0, options)
}

// Example 5: Convert specific pages only
lifecycleScope.launch {
    val outputDir = File(context.filesDir, "selected_pages")
    val pagesToConvert = listOf(0, 2, 4) // Pages 1, 3, and 5

    val result = converter.convertSpecificPagesToImages(
        pdfFile = pdfFile,
        outputDir = outputDir,
        pageIndices = pagesToConvert,
        fileNamePattern = "page_%d.png"
    )
}

// Example 6: Convert with progress tracking
lifecycleScope.launch {
    val outputDir = File(context.filesDir, "pdf_images")

    converter.convertAllPagesWithProgress(
        pdfFile = pdfFile,
        outputDir = outputDir,
        fileNamePattern = "page_%d.png"
    ) { current, total, file ->
        val progress = (current * 100) / total
        Log.d("Progress", "$progress% - Page $current/$total - ${file.name}")
        // Update UI progress bar here
    }
}

// Example 7: Convert from URI (e.g., from file picker)
lifecycleScope.launch {
    val uri = // ... URI from file picker
    val outputDir = File(context.filesDir, "pdf_images")

    val result = converter.convertPdfFromUri(uri, outputDir)
}

// Example 8: Get PDF info before converting
lifecycleScope.launch {
    val pageCountResult = converter.getPdfPageCount(pdfFile)
    pageCountResult.onSuccess { count ->
        Log.d("Info", "PDF has $count pages")
    }

    val dimensionsResult = converter.getPdfPageDimensions(pdfFile, 0)
    dimensionsResult.onSuccess { (width, height) ->
        Log.d("Info", "Page 1 dimensions: ${width}x${height}")
    }
}

// Example 9: High resolution PNG for printing
lifecycleScope.launch {
    val options = ImageConversionOptions(
        format = Bitmap.CompressFormat.PNG,
        scale = 3.0f // Triple size for high DPI
    )

    val outputDir = File(context.filesDir, "high_res")
    converter.convertAllPagesToImages(pdfFile, outputDir, "page_%d.png", options)
}

// Example 10: Compressed WEBP for web use
lifecycleScope.launch {
    val options = ImageConversionOptions(
        format = Bitmap.CompressFormat.WEBP,
        quality = 80, // Good balance of quality and size
        scale = 1.0f
    )

    val outputDir = File(context.filesDir, "web_images")
    converter.convertAllPagesToImages(pdfFile, outputDir, "page_%d.webp", options)
}
*/