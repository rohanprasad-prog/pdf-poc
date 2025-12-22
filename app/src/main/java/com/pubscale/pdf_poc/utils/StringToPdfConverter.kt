package com.pubscale.pdf_poc.utils

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StringToPdfConverter(private val context: Context) {

    init {
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Convert plain text string to PDF
     *
     * @param text Content to write into PDF
     * @param outputFile Destination PDF file
     */
    suspend fun convertStringToPdf(
        text: String,
        outputFile: File
    ): Result<File> = withContext(Dispatchers.IO) {

        var document: PDDocument? = null

        try {
            document = PDDocument()

            val font = PDType1Font.HELVETICA
            val fontSize = 12f
            val leading = 1.5f * fontSize

            var page = PDPage(PDRectangle.A4)
            document.addPage(page)

            var contentStream = PDPageContentStream(document, page)

            val margin = 50f
            val width = page.mediaBox.width - 2 * margin
            var yPosition = page.mediaBox.height - margin

            contentStream.beginText()
            contentStream.setFont(font, fontSize)
            contentStream.newLineAtOffset(margin, yPosition)

            val lines = wrapText(text, font, fontSize, width)

            for (line in lines) {
                if (yPosition <= margin) {
                    // Close current page
                    contentStream.endText()
                    contentStream.close()

                    // New page
                    page = PDPage(PDRectangle.A4)
                    document.addPage(page)

                    contentStream = PDPageContentStream(document, page)
                    yPosition = page.mediaBox.height - margin

                    contentStream.beginText()
                    contentStream.setFont(font, fontSize)
                    contentStream.newLineAtOffset(margin, yPosition)
                }

                contentStream.showText(line)
                contentStream.newLineAtOffset(0f, -leading)
                yPosition -= leading
            }

            contentStream.endText()
            contentStream.close()

            document.save(outputFile)
            Result.success(outputFile)
        } catch (e: Exception) {
            if (outputFile.exists()) outputFile.delete()
            Result.failure(e)
        } finally {
            try { document?.close() } catch (_: Exception) {}
        }
    }

    // ==========================================
    // HELPERS
    // ==========================================

    /**
     * Wrap text to fit PDF width
     */
    private fun wrapText(
        text: String,
        font: PDFont,
        fontSize: Float,
        maxWidth: Float
    ): List<String> {

        val result = mutableListOf<String>()
        val paragraphs = text.split("\n")

        for (paragraph in paragraphs) {
            var line = ""

            for (word in paragraph.split(" ")) {
                val testLine = if (line.isEmpty()) word else "$line $word"
                val textWidth =
                    font.getStringWidth(testLine) / 1000 * fontSize

                if (textWidth > maxWidth) {
                    result.add(line)
                    line = word
                } else {
                    line = testLine
                }
            }

            if (line.isNotEmpty()) result.add(line)
            result.add("") // Paragraph spacing
        }

        return result
    }
}
