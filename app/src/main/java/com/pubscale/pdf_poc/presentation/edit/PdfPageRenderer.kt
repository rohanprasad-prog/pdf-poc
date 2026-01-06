package com.pubscale.pdf_poc.presentation.edit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File

class PdfPageRenderer(
    context: Context,
    file: File
) {
    private val fileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fileDescriptor)

    fun renderPage(pageIndex: Int, bitmap: Bitmap) {
        renderer.openPage(pageIndex).use { page ->
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        }
    }

    fun pageCount() = renderer.pageCount
}
