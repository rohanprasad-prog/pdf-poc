package com.pubscale.pdf_poc.presentation.edit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.io.File

@Composable
fun PdfEditorScreen(
    pdfFile: File,
    outputFile: File
) {
    val context = LocalContext.current

    // Renderer (remember across recompositions)
    val renderer = remember {
        PdfPageRenderer(context, pdfFile)
    }

    val pageIndex = 0 // start with first page

    PdfEditorPage(
        renderer = renderer,
        pageIndex = pageIndex,
        pdfFile = pdfFile,
        outputFile = outputFile
    )
}
