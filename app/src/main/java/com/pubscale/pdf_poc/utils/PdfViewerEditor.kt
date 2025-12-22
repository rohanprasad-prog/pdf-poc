package com.pubscale.pdf_poc.utils

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.pubscale.pdf_poc.FeatureProcessor
import com.pubscale.pdf_poc.FeatureResult
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.rendering.PDFRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * PDF Viewer & Editor for Android
 * Provides comprehensive PDF viewing and editing capabilities
 *
 * Features:
 * - View PDF pages as bitmaps
 * - Add text annotations
 * - Add images/watermarks
 * - Merge PDFs
 * - Split PDFs
 * - Delete pages
 * - Rotate pages
 * - Extract pages
 * - Add blank pages
 */
class PdfViewerEditor(private val context: Context) : FeatureProcessor {

    override suspend fun process(
        context: Context,
        inputUris: List<Uri>,
        text: String?
    ): FeatureResult {
        val res = loadPdfFromUri(inputUris.first())


        return FeatureResult.Success("opened", res.getOrNull())
    }

    init {
        PDFBoxResourceLoader.init(context)
    }

    // ==========================================
    // PDF VIEWING
    // ==========================================

    /**
     * Render PDF page to Bitmap for viewing
     * Uses Android's PdfRenderer (API 21+)
     */
    @RequiresApi(21)
    suspend fun renderPageToBitmap(
        pdfFile: File,
        pageIndex: Int,
        scale: Float = 1.0f
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
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

            val width = (page.width * scale).toInt()
            val height = (page.height * scale).toInt()

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

            page.close()
            Result.success(bitmap)
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
     * Get PDF metadata
     */
    suspend fun getPdfInfo(pdfFile: File): Result<PdfInfo> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                val info = PdfInfo(
                    pageCount = document.numberOfPages,
                    title = document.documentInformation.title ?: "",
                    author = document.documentInformation.author ?: "",
                    subject = document.documentInformation.subject ?: "",
                    keywords = document.documentInformation.keywords ?: "",
                    creator = document.documentInformation.creator ?: "",
                    producer = document.documentInformation.producer ?: "",
                    creationDate = document.documentInformation.creationDate?.timeInMillis,
                    modificationDate = document.documentInformation.modificationDate?.timeInMillis
                )
                Result.success(info)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun savePdfToDownloads(
        context: Context,
        inputFile: File,
        displayName: String
    ): Result<Uri> {
        return try {
            val resolver = context.contentResolver

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/PDFs"
                )
            }

            val uri = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return Result.failure(Exception("Failed to create file"))

            resolver.openOutputStream(uri)?.use { output ->
                inputFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }

            Result.success(uri)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun hashBitmap(bitmap: Bitmap): String {
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, buffer)
        val bytes = buffer.toByteArray()

        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString("") { "%02x".format(it) }
    }

    suspend fun deleteDuplicatePages(
        pdfFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        var renderer: PdfRenderer? = null
        var pfd: ParcelFileDescriptor? = null

        try {
            pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)

            val seenHashes = HashSet<String>()
            val pagesToDelete = mutableListOf<Int>()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)

                val bitmap = Bitmap.createBitmap(
                    page.width,
                    page.height,
                    Bitmap.Config.ARGB_8888
                )

                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )
                page.close()

                val hash = hashBitmap(bitmap)

                if (!seenHashes.add(hash)) {
                    pagesToDelete.add(i)
                }

                bitmap.recycle()
            }

            renderer.close()
            pfd.close()

            if (pagesToDelete.isEmpty()) {
                return@withContext Result.success(pdfFile)
            }

            // Remove duplicate pages using PDFBox
            PDDocument.load(pdfFile).use { document ->
                pagesToDelete
                    .sortedDescending() // VERY IMPORTANT
                    .forEach { index ->
                        if (index < document.numberOfPages) {
                            document.removePage(index)
                        }
                    }

                document.save(outputFile)
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            Result.failure(e)
        } finally {
            try { renderer?.close() } catch (_: Exception) {}
            try { pfd?.close() } catch (_: Exception) {}
        }
    }



    // ==========================================
    // PDF EDITING - ADD TEXT
    // ==========================================

    /**
     * Add text to PDF page
     */

    suspend fun addTextToPdf(
        pdfFile: File,
        outputFile: File,
        pageIndex: Int,
        text: String,
        x: Float,
        y: Float,
        fontSize: Float = 12f,
        textColor: Int = Color.BLACK
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                if (pageIndex >= document.numberOfPages) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Page $pageIndex does not exist")
                    )
                }

                val page = document.getPage(pageIndex)

                PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { contentStream ->
                    contentStream.beginText()
                    contentStream.setFont(PDType1Font.HELVETICA, fontSize)

                    // Convert Android color to RGB
                    val r = Color.red(textColor) / 255f
                    val g = Color.green(textColor) / 255f
                    val b = Color.blue(textColor) / 255f
                    contentStream.setNonStrokingColor(r, g, b)

                    contentStream.newLineAtOffset(x, y)
                    contentStream.showText(text)
                    contentStream.endText()
                }

                document.save(outputFile)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * Add multiple text annotations to PDF
     */
    suspend fun addMultipleTexts(
        pdfFile: File,
        outputFile: File,
        annotations: List<TextAnnotation>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                for (annotation in annotations) {
                    if (annotation.pageIndex >= document.numberOfPages) {
                        continue // Skip invalid pages
                    }

                    val page = document.getPage(annotation.pageIndex)

                    PDPageContentStream(
                        document,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { contentStream ->
                        contentStream.beginText()
                        contentStream.setFont(PDType1Font.HELVETICA, annotation.fontSize)

                        val r = Color.red(annotation.color) / 255f
                        val g = Color.green(annotation.color) / 255f
                        val b = Color.blue(annotation.color) / 255f
                        contentStream.setNonStrokingColor(r, g, b)

                        contentStream.newLineAtOffset(annotation.x, annotation.y)
                        contentStream.showText(annotation.text)
                        contentStream.endText()
                    }
                }

                document.save(outputFile)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        }
    }

    // ==========================================
    // PDF EDITING - ADD IMAGE/WATERMARK
    // ==========================================

    /**
     * Add image to PDF page
     */
    suspend fun addImageToPdf(
        pdfFile: File,
        outputFile: File,
        pageIndex: Int,
        imageBitmap: Bitmap,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        useJpeg: Boolean = false
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                if (pageIndex >= document.numberOfPages) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Page $pageIndex does not exist")
                    )
                }

                val page = document.getPage(pageIndex)

                // Convert bitmap to PDImageXObject
                val pdImage = if (useJpeg) {
                    JPEGFactory.createFromImage(document, imageBitmap)
                } else {
                    LosslessFactory.createFromImage(document, imageBitmap)
                }

                PDPageContentStream(
                    document,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { contentStream ->
                    contentStream.drawImage(pdImage, x, y, width, height)
                }

                document.save(outputFile)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * Add watermark to all pages
     */
    suspend fun addWatermarkToAllPages(
        pdfFile: File,
        outputFile: File,
        watermarkText: String,
        fontSize: Float = 48f,
        alpha: Float = 0.3f
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                for (i in 0 until document.numberOfPages) {
                    val page = document.getPage(i)
                    val pageWidth = page.mediaBox.width
                    val pageHeight = page.mediaBox.height

                    // Center watermark diagonally
                    val x = pageWidth / 4
                    val y = pageHeight / 2

                    PDPageContentStream(
                        document,
                        page,
                        PDPageContentStream.AppendMode.APPEND,
                        true,
                        true
                    ).use { contentStream ->
                        contentStream.beginText()
                        contentStream.setFont(PDType1Font.HELVETICA_BOLD, fontSize)
                        contentStream.setNonStrokingColor(0.5f, 0.5f, 0.5f) // Gray
                        contentStream.newLineAtOffset(x, y)
                        contentStream.showText(watermarkText)
                        contentStream.endText()
                    }
                }

                document.save(outputFile)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        }
    }

    // ==========================================
    // PDF EDITING - PAGE OPERATIONS
    // ==========================================

    /**
     * Delete specific pages from PDF
     */
    suspend fun deletePages(
        pdfFile: File,
        outputFile: File,
        pageIndices: List<Int>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                // Sort in descending order to avoid index issues
                val sortedIndices = pageIndices.sortedDescending()

                for (pageIndex in sortedIndices) {
                    if (pageIndex < document.numberOfPages) {
                        document.removePage(pageIndex)
                    }
                }

                if (document.numberOfPages == 0) {
                    return@withContext Result.failure(
                        IllegalStateException("Cannot create PDF with 0 pages")
                    )
                }

                document.save(outputFile)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * Extract specific pages to new PDF
     */
    suspend fun extractPages(
        pdfFile: File,
        outputFile: File,
        pageIndices: List<Int>
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sourcePdf = PDDocument.load(pdfFile)
            val outputPdf = PDDocument()

            try {
                for (pageIndex in pageIndices.sorted()) {
                    if (pageIndex < sourcePdf.numberOfPages) {
                        val page = sourcePdf.getPage(pageIndex)
                        outputPdf.addPage(page)
                    }
                }

                if (outputPdf.numberOfPages == 0) {
                    return@withContext Result.failure(
                        IllegalStateException("No valid pages to extract")
                    )
                }

                outputPdf.save(outputFile)
            } finally {
                sourcePdf.close()
                outputPdf.close()
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * Rotate pages
     */
    suspend fun rotatePages(
        pdfFile: File,
        outputFile: File,
        pageIndices: List<Int>,
        rotation: Int // 90, 180, 270, or 360
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                for (pageIndex in pageIndices) {
                    if (pageIndex < document.numberOfPages) {
                        val page = document.getPage(pageIndex)
                        val currentRotation = page.rotation
                        page.rotation = (currentRotation + rotation) % 360
                    }
                }

                document.save(outputFile)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * Reorder pages
     */
    suspend fun reorderPages(
        pdfFile: File,
        outputFile: File,
        newOrder: List<Int> // New order of page indices
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val sourcePdf = PDDocument.load(pdfFile)
            val outputPdf = PDDocument()

            try {
                // Validate new order
                if (newOrder.size != sourcePdf.numberOfPages) {
                    return@withContext Result.failure(
                        IllegalArgumentException("New order must contain all pages")
                    )
                }

                for (pageIndex in newOrder) {
                    if (pageIndex < sourcePdf.numberOfPages) {
                        val page = sourcePdf.getPage(pageIndex)
                        outputPdf.addPage(page)
                    }
                }

                outputPdf.save(outputFile)
            } finally {
                sourcePdf.close()
                outputPdf.close()
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        }
    }

    /**
     * Add blank page
     */
    suspend fun addBlankPage(
        pdfFile: File,
        outputFile: File,
        insertAt: Int = -1, // -1 = append at end
        pageSize: PDRectangle = PDRectangle.A4
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                val blankPage = PDPage(pageSize)

                if (insertAt == -1 || insertAt >= document.numberOfPages) {
                    document.addPage(blankPage)
                } else {
                    document.pages.insertBefore(blankPage, document.getPage(insertAt))
                }

                document.save(outputFile)
            }
            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) {
                outputFile.delete()
            }
            Result.failure(e)
        }
    }

    // ==========================================
    // PDF MERGING & SPLITTING
    // ==========================================

    /**
     * Merge multiple PDFs into one
     */
    suspend fun mergePdfs(
        pdfFiles: List<File>,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val merger = PDFMergerUtility().apply {
                destinationFileName = outputFile.absolutePath
            }

            pdfFiles.forEach { file ->
                merger.addSource(file)
            }

            merger.mergeDocuments(null)

            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            Result.failure(e)
        }
    }


    /**
     * Split PDF into multiple files
     */
    suspend fun splitPdf(
        pdfFile: File,
        outputDir: File,
        splitAt: List<Int>, // Page indices where to split
        fileNamePattern: String = "split_%d.pdf"
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        val outputFiles = mutableListOf<File>()

        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            PDDocument.load(pdfFile).use { document ->
                val sortedSplits = (listOf(0) + splitAt.sorted() + document.numberOfPages).distinct()

                for (i in 0 until sortedSplits.size - 1) {
                    val start = sortedSplits[i]
                    val end = sortedSplits[i + 1]

                    val outputPdf = PDDocument()

                    for (pageIndex in start until end) {
                        if (pageIndex < document.numberOfPages) {
                            val page = document.getPage(pageIndex)
                            outputPdf.addPage(page)
                        }
                    }

                    if (outputPdf.numberOfPages > 0) {
                        val fileName = String.format(fileNamePattern, i + 1)
                        val outputFile = File(outputDir, fileName)
                        outputPdf.save(outputFile)
                        outputFiles.add(outputFile)
                    }

                    outputPdf.close()
                }
            }

            Result.success(outputFiles)
        } catch (e: Exception) {
            outputFiles.forEach { it.delete() }
            Result.failure(e)
        }
    }

    /**
     * Split PDF into individual pages
     */
    suspend fun splitIntoPages(
        pdfFile: File,
        outputDir: File,
        fileNamePattern: String = "page_%d.pdf"
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        val outputFiles = mutableListOf<File>()

        try {
            if (!outputDir.exists()) {
                outputDir.mkdirs()
            }

            PDDocument.load(pdfFile).use { document ->
                for (i in 0 until document.numberOfPages) {
                    val outputPdf = PDDocument()
                    val page = document.getPage(i)
                    outputPdf.addPage(page)

                    val fileName = String.format(fileNamePattern, i + 1)
                    val outputFile = File(outputDir, fileName)
                    outputPdf.save(outputFile)
                    outputFiles.add(outputFile)
                    outputPdf.close()
                }
            }

            Result.success(outputFiles)
        } catch (e: Exception) {
            outputFiles.forEach { it.delete() }
            Result.failure(e)
        }
    }

    // ==========================================
    // URI SUPPORT
    // ==========================================

    /**
     * Load PDF from URI
     */
    suspend fun loadPdfFromUri(uri: Uri): Result<File> = withContext(Dispatchers.IO) {
        try {
            val tempPdf = File(context.cacheDir, "temp_${System.currentTimeMillis()}.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempPdf.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Result.success(tempPdf)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

// ==========================================
// DATA CLASSES
// ==========================================

data class PdfInfo(
    val pageCount: Int,
    val title: String,
    val author: String,
    val subject: String,
    val keywords: String,
    val creator: String,
    val producer: String,
    val creationDate: Long?,
    val modificationDate: Long?
)

data class TextAnnotation(
    val pageIndex: Int,
    val text: String,
    val x: Float,
    val y: Float,
    val fontSize: Float = 12f,
    val color: Int = Color.BLACK
)

// ==========================================
// USAGE EXAMPLES
// ==========================================

/*
val editor = PdfViewerEditor(context)
val pdfFile = File(context.filesDir, "document.pdf")

// Example 1: View PDF page as bitmap
lifecycleScope.launch {
    val result = editor.renderPageToBitmap(pdfFile, pageIndex = 0, scale = 2.0f)
    result.onSuccess { bitmap ->
        imageView.setImageBitmap(bitmap)
    }
}

// Example 2: Get PDF info
lifecycleScope.launch {
    val result = editor.getPdfInfo(pdfFile)
    result.onSuccess { info ->
        Log.d("PDF", "Title: ${info.title}")
        Log.d("PDF", "Pages: ${info.pageCount}")
        Log.d("PDF", "Author: ${info.author}")
    }
}

// Example 3: Add text to PDF
lifecycleScope.launch {
    val outputFile = File(context.filesDir, "annotated.pdf")
    val result = editor.addTextToPdf(
        pdfFile = pdfFile,
        outputFile = outputFile,
        pageIndex = 0,
        text = "CONFIDENTIAL",
        x = 200f,
        y = 750f,
        fontSize = 24f,
        textColor = Color.RED
    )
}

// Example 4: Add multiple text annotations
lifecycleScope.launch {
    val annotations = listOf(
        TextAnnotation(0, "Page 1 Header", 50f, 800f, 16f, Color.BLUE),
        TextAnnotation(0, "Footer text", 50f, 50f, 12f, Color.GRAY),
        TextAnnotation(1, "Page 2 Note", 100f, 700f, 14f, Color.BLACK)
    )

    val outputFile = File(context.filesDir, "annotated.pdf")
    editor.addMultipleTexts(pdfFile, outputFile, annotations)
}

// Example 5: Add image/logo to PDF
lifecycleScope.launch {
    val logoBitmap = BitmapFactory.decodeResource(resources, R.drawable.logo)
    val outputFile = File(context.filesDir, "with_logo.pdf")

    editor.addImageToPdf(
        pdfFile = pdfFile,
        outputFile = outputFile,
        pageIndex = 0,
        imageBitmap = logoBitmap,
        x = 450f,
        y = 750f,
        width = 100f,
        height = 50f
    )
}

// Example 6: Add watermark to all pages
lifecycleScope.launch {
    val outputFile = File(context.filesDir, "watermarked.pdf")
    editor.addWatermarkToAllPages(
        pdfFile = pdfFile,
        outputFile = outputFile,
        watermarkText = "DRAFT",
        fontSize = 72f,
        alpha = 0.2f
    )
}

// Example 7: Delete pages
lifecycleScope.launch {
    val outputFile = File(context.filesDir, "deleted_pages.pdf")
    val pagesToDelete = listOf(2, 5, 7) // Delete pages 3, 6, and 8 (0-indexed)

    editor.deletePages(pdfFile, outputFile, pagesToDelete)
}

// Example 8: Extract specific pages
lifecycleScope.launch {
    val outputFile = File(context.filesDir, "extracted.pdf")
    val pagesToExtract = listOf(0, 2, 4) // Extract pages 1, 3, and 5

    editor.extractPages(pdfFile, outputFile, pagesToExtract)
}

// Example 9: Rotate pages
lifecycleScope.launch {
    val outputFile = File(context.filesDir, "rotated.pdf")
    val pagesToRotate = listOf(0, 1) // Rotate first two pages

    editor.rotatePages(pdfFile, outputFile, pagesToRotate, rotation = 90)
}

// Example 10: Reorder pages
lifecycleScope.launch {
    val outputFile = File(context.filesDir, "reordered.pdf")
    // If PDF has 5 pages, reorder as: 5, 1, 3, 2, 4
    val newOrder = listOf(4, 0, 2, 1, 3)

    editor.reorderPages(pdfFile, outputFile, newOrder)
}

// Example 11: Add blank page
lifecycleScope.launch {
    val outputFile = File(context.filesDir, "with_blank.pdf")
    editor.addBlankPage(
        pdfFile = pdfFile,
        outputFile = outputFile,
        insertAt = 2, // Insert at position 2 (before page 3)
        pageSize = PDRectangle.A4
    )
}

// Example 12: Merge PDFs
lifecycleScope.launch {
    val pdf1 = File(context.filesDir, "doc1.pdf")
    val pdf2 = File(context.filesDir, "doc2.pdf")
    val pdf3 = File(context.filesDir, "doc3.pdf")
    val outputFile = File(context.filesDir, "merged.pdf")

    editor.mergePdfs(listOf(pdf1, pdf2, pdf3), outputFile)
}

// Example 13: Split PDF at specific pages
lifecycleScope.launch {
    val outputDir = File(context.filesDir, "split_pdfs")
    val splitPoints = listOf(5, 10, 15) // Split at pages 5, 10, and 15

    val result = editor.splitPdf(
        pdfFile = pdfFile,
        outputDir = outputDir,
        splitAt = splitPoints,
        fileNamePattern = "part_%d.pdf"
    )

    result.onSuccess { files ->
        Log.d("Split", "Created ${files.size} PDF files")
    }
}

// Example 14: Split into individual pages
lifecycleScope.launch {
    val outputDir = File(context.filesDir, "individual_pages")
    val result = editor.splitIntoPages(
        pdfFile = pdfFile,
        outputDir = outputDir,
        fileNamePattern = "page_%d.pdf"
    )
}

// Example 15: Load and process PDF from URI
lifecycleScope.launch {
    val uri = // ... URI from file picker

    val loadResult = editor.loadPdfFromUri(uri)
    loadResult.onSuccess { tempPdf ->
        // Now process the PDF
        val outputFile = File(context.filesDir, "processed.pdf")
        editor.addWatermarkToAllPages(tempPdf, outputFile, "PROCESSED")

        // Clean up temp file
        tempPdf.delete()
    }
}
*/