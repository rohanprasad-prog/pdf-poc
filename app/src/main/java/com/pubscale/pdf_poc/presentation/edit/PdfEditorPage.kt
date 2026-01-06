package com.pubscale.pdf_poc.presentation.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.File

@Composable
fun PdfEditorPage(
    renderer: PdfPageRenderer,
    pageIndex: Int,
    pdfFile: File,
    outputFile: File
) {
    val context = LocalContext.current

    // PDF page size
    val pageBitmap = remember {
        val bitmap = Bitmap.createBitmap(
            1080,  // you can calculate dynamically
            1920,
            Bitmap.Config.ARGB_8888
        )
        renderer.renderPage(pageIndex, bitmap)
        bitmap
    }

    PdfPageView(
        bitmap = pageBitmap,
        onSelectionComplete = { screenRect ->
            applyUnderlineFromSelection(
                context = context,
                pdfFile = pdfFile,
                outputFile = outputFile,
                pageIndex = pageIndex,
                screenRect = screenRect,
                viewWidth = pageBitmap.width.toFloat(),
                viewHeight = pageBitmap.height.toFloat()
            )
        }
    )
}

fun applyUnderlineFromSelection(
    context: Context,
    pdfFile: File,
    outputFile: File,
    pageIndex: Int,
    screenRect: RectF,
    viewWidth: Float,
    viewHeight: Float
) {
    PDDocument.load(pdfFile).use { document ->

        val page = document.pages[pageIndex]

        val rotation = page.rotation ?: 0
        val pageHeight = page.cropBox.height
        val pageWidth = page.cropBox.width


        Log.d("PDF_DEBUG", """
--- PAGE DEBUG ---
Rotation     = ${page.rotation}
MediaBox     = ${page.mediaBox}
CropBox      = ${page.cropBox}
BleedBox     = ${page.bleedBox}
TrimBox      = ${page.trimBox}
""".trimIndent())


        // Screen → PDF coords
        val pdfRect = screenToPdfRect(
            screenRect = screenRect,
            viewWidth = viewWidth,
            viewHeight = viewHeight,
            pageWidth = pageWidth,
            pageHeight = pageHeight
        )

        // Find text inside rect
        val positions = findTextInRect(
            document = document,
            pageIndex = pageIndex,
            targetRect = pdfRect
        )

        Log.d("Tests", "${positions}")

        // Apply underline
        applyUnderline(
            document = document,
            pageIndex = pageIndex,
            textPositions = positions
        )

        document.save(outputFile)
    }
}

