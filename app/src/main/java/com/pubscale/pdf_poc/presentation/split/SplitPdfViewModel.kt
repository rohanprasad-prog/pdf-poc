package com.pubscale.pdf_poc.presentation.split

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pubscale.pdf_poc.utils.PdfViewerEditor
import kotlinx.coroutines.launch
import java.io.File

class SplitPdfViewModel : ViewModel() {

    private var editor: PdfViewerEditor? = null

    var isProcessing by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun splitEachPage(
        context: Context,
        input: File,
        onSuccess: (List<File>) -> Unit
    ) {
        editor = PdfViewerEditor(context)

        viewModelScope.launch {
            isProcessing = true
            error = null

            val outputDir =
                File(context.cacheDir, "split_${System.currentTimeMillis()}")

            editor!!
                .splitIntoPages(input, outputDir)
                .onSuccess(onSuccess)
                .onFailure { error = it.message }

            isProcessing = false
        }
    }

    fun splitAtPages(
        context: Context,
        input: File,
        pages: List<Int>,
        onSuccess: (List<File>) -> Unit
    ) {
        editor = PdfViewerEditor(context)

        viewModelScope.launch {
            isProcessing = true
            error = null

            val outputDir =
                File(context.cacheDir, "split_${System.currentTimeMillis()}")

            editor!!
                .splitPdf(input, outputDir, pages)
                .onSuccess(onSuccess)
                .onFailure { error = it.message }

            isProcessing = false
        }
    }
}
