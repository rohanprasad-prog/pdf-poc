package com.pubscale.pdf_poc.utils

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.SlideLayout
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFSlide
import java.io.File
import java.io.FileOutputStream

/**
 * PDF to PPT Converter for Android
 *
 * IMPORTANT NOTE: Creating complex PowerPoint slides with custom positioning
 * is NOT fully supported on Android because Apache POI's PowerPoint library
 * requires java.awt classes which are not available on Android.
 *
 * This converter uses a simpler approach that works with slide layouts
 * and placeholders instead of manual shape positioning.
 */
class PdfToPptConverter(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    // ==========================================
    // PDF TO PPTX (PowerPoint format)
    // ==========================================

    /**
     * Convert PDF to PowerPoint (.pptx) - Basic Version
     * Uses slide layouts and placeholders (Android-compatible)
     *
     * @param pdfFile Input PDF file
     * @param outputFile Output PowerPoint file (.pptx)
     * @return Result with output file or error
     */
    suspend fun convertPdfToPpt(
        pdfFile: File,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {
        var presentation: XMLSlideShow? = null
        var fileOut: FileOutputStream? = null

        try {
            // Extract text from PDF by pages
            val pdfPages = extractTextFromPdfByPages(pdfFile)

            // Create PowerPoint presentation
            presentation = XMLSlideShow()

            // Get master slide for layouts
            val slideMaster = presentation.slideMasters[0]

            // Process each page as a slide
            for ((pageIndex, pageText) in pdfPages.withIndex()) {
                if (pageText.trim().isEmpty()) continue

                val lines = pageText.lines().filter { it.trim().isNotEmpty() }
                if (lines.isEmpty()) continue

                // Use title and content layout
                val layout = slideMaster.getLayout(SlideLayout.TITLE_AND_CONTENT)
                val slide = presentation.createSlide(layout)

                // Set title (first line)
                val titlePlaceholder = slide.getPlaceholder(0)
                if (titlePlaceholder != null) {
                    titlePlaceholder.text = lines.first().take(150)
                }

                // Set content (remaining lines)
                if (lines.size > 1) {
                    val contentPlaceholder = slide.getPlaceholder(1)
                    if (contentPlaceholder != null) {
                        val contentText = lines.drop(1).joinToString("\n")
                        contentPlaceholder.text = contentText
                    }
                }
            }

            // IMPORTANT: Write to file and flush
            fileOut = FileOutputStream(outputFile)
            presentation.write(fileOut)
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
                presentation?.close()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    /**
     * Convert PDF to PowerPoint with custom options
     * Uses different slide layouts
     */
    suspend fun convertPdfToPptAdvanced(
        pdfFile: File,
        outputFile: File,
        options: PptConversionOptions = PptConversionOptions()
    ): Result<File> = withContext(Dispatchers.IO) {
        var presentation: XMLSlideShow? = null
        var fileOut: FileOutputStream? = null

        try {
            val pdfPages = extractTextFromPdfByPages(pdfFile)
            presentation = XMLSlideShow()

            val slideMaster = presentation.slideMasters[0]

            for ((pageIndex, pageText) in pdfPages.withIndex()) {
                if (pageText.trim().isEmpty()) continue

                val lines = pageText.lines().filter { it.trim().isNotEmpty() }
                if (lines.isEmpty()) continue

                // Select layout based on options
                val slideLayout = when (options.slideLayoutType) {
                    SlideLayoutType.TITLE_AND_CONTENT -> SlideLayout.TITLE_AND_CONTENT
                    SlideLayoutType.TITLE_ONLY -> SlideLayout.TITLE_ONLY
                    SlideLayoutType.BLANK -> SlideLayout.BLANK
                    SlideLayoutType.SECTION_HEADER -> SlideLayout.SECTION_HEADER
                }

                val layout = slideMaster.getLayout(slideLayout)
                val slide = presentation.createSlide(layout)

                when (options.slideLayoutType) {
                    SlideLayoutType.TITLE_AND_CONTENT -> {
                        fillTitleAndContentSlide(slide, lines)
                    }
                    SlideLayoutType.TITLE_ONLY -> {
                        fillTitleOnlySlide(slide, lines)
                    }
                    SlideLayoutType.BLANK -> {
                        // Blank slides have no placeholders
                        // Content would need custom shapes which aren't fully supported
                    }
                    SlideLayoutType.SECTION_HEADER -> {
                        fillSectionHeaderSlide(slide, lines)
                    }
                }
            }

            // CRITICAL: Proper file writing
            fileOut = FileOutputStream(outputFile)
            presentation.write(fileOut)
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
                presentation?.close()
            } catch (e: Exception) { }
        }
    }

    // ==========================================
    // SLIDE FILLING HELPERS
    // ==========================================

    private fun fillTitleAndContentSlide(slide: XSLFSlide, lines: List<String>) {
        // Placeholder 0 is usually the title
        val titlePlaceholder = slide.getPlaceholder(0)
        if (titlePlaceholder != null && lines.isNotEmpty()) {
            titlePlaceholder.text = lines.first().take(150)
        }

        // Placeholder 1 is usually the content
        val contentPlaceholder = slide.getPlaceholder(1)
        if (contentPlaceholder != null && lines.size > 1) {
            val contentText = lines.drop(1).joinToString("\n")
            contentPlaceholder.text = contentText
        }
    }

    private fun fillTitleOnlySlide(slide: XSLFSlide, lines: List<String>) {
        val titlePlaceholder = slide.getPlaceholder(0)
        if (titlePlaceholder != null) {
            titlePlaceholder.text = lines.joinToString("\n").take(300)
        }
    }

    private fun fillSectionHeaderSlide(slide: XSLFSlide, lines: List<String>) {
        val titlePlaceholder = slide.getPlaceholder(0)
        if (titlePlaceholder != null && lines.isNotEmpty()) {
            titlePlaceholder.text = lines.first().take(150)
        }

        // Section header might have a subtitle placeholder
        val subtitlePlaceholder = slide.getPlaceholder(1)
        if (subtitlePlaceholder != null && lines.size > 1) {
            subtitlePlaceholder.text = lines.drop(1).joinToString("\n").take(150)
        }
    }

    // ==========================================
    // HELPER METHODS
    // ==========================================

    /**
     * Extract text from PDF page by page
     */
    private fun extractTextFromPdfByPages(pdfFile: File): List<String> {
        return PDDocument.load(pdfFile).use { document ->
            val stripper = PDFTextStripper()
            val pages = mutableListOf<String>()

            for (pageNum in 1..document.numberOfPages) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum
                pages.add(stripper.getText(document))
            }

            pages
        }
    }

    /**
     * Convert PDF from URI
     */
    suspend fun convertPdfToPptFromUri(
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
            val result = convertPdfToPpt(tempPdf, outputFile)

            // Cleanup
            tempPdf.delete()

            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Convert PDF with progress callback
     */
    suspend fun convertPdfToPptWithProgress(
        pdfFile: File,
        outputFile: File,
        onProgress: (Int, Int) -> Unit // currentPage, totalPages
    ): Result<File> = withContext(Dispatchers.IO) {
        var presentation: XMLSlideShow? = null
        var fileOut: FileOutputStream? = null

        try {
            val pdfPages = extractTextFromPdfByPages(pdfFile)
            val totalPages = pdfPages.size

            presentation = XMLSlideShow()
            val slideMaster = presentation.slideMasters[0]
            val layout = slideMaster.getLayout(SlideLayout.TITLE_AND_CONTENT)

            for ((pageIndex, pageText) in pdfPages.withIndex()) {
                if (pageText.trim().isEmpty()) continue

                val lines = pageText.lines().filter { it.trim().isNotEmpty() }
                if (lines.isNotEmpty()) {
                    val slide = presentation.createSlide(layout)
                    fillTitleAndContentSlide(slide, lines)
                }

                // Report progress
                withContext(Dispatchers.Main) {
                    onProgress(pageIndex + 1, totalPages)
                }
            }

            fileOut = FileOutputStream(outputFile)
            presentation.write(fileOut)
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
                presentation?.close()
            } catch (e: Exception) { }
        }
    }
}

// ==========================================
// DATA CLASSES
// ==========================================

data class PptConversionOptions(
    val slideLayoutType: SlideLayoutType = SlideLayoutType.TITLE_AND_CONTENT
)

enum class SlideLayoutType {
    TITLE_AND_CONTENT,  // Title at top, content below
    TITLE_ONLY,         // Only title (centered)
    SECTION_HEADER,     // Section header with title and subtitle
    BLANK               // Blank slide (limited support on Android)
}

// ==========================================
// USAGE EXAMPLE
// ==========================================

/*
// Basic conversion
val converter = PdfToPptConverter(context)
val pdfFile = File(context.filesDir, "document.pdf")
val outputFile = File(context.filesDir, "presentation.pptx")

lifecycleScope.launch {
    val result = converter.convertPdfToPpt(pdfFile, outputFile)
    result.onSuccess { file ->
        Log.d("Converter", "PPT created: ${file.absolutePath}")
    }.onFailure { error ->
        Log.e("Converter", "Conversion failed", error)
    }
}

// Advanced conversion with options
val options = PptConversionOptions(
    slideLayoutType = SlideLayoutType.TITLE_AND_CONTENT
)

val result = converter.convertPdfToPptAdvanced(pdfFile, outputFile, options)

// Conversion with progress
converter.convertPdfToPptWithProgress(pdfFile, outputFile) { current, total ->
    val progress = (current * 100) / total
    Log.d("Progress", "$progress% - Page $current of $total")
}

// IMPORTANT NOTES:
// 1. This uses slide layouts and placeholders instead of custom shapes
// 2. Custom positioning (setAnchor) is NOT supported on Android
// 3. Advanced formatting options are limited
// 4. The output will use PowerPoint's built-in layouts
*/