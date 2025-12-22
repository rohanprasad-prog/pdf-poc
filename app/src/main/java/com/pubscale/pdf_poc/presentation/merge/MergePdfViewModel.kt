package com.pubscale.pdf_poc.presentation.merge

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pubscale.pdf_poc.utils.PdfViewerEditor
import kotlinx.coroutines.launch
import java.io.File

class MergePdfViewModel : ViewModel() {

    private var editor: PdfViewerEditor? = null

    var isProcessing by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun merge(
        context: Context,
        inputFiles: List<File>,
        onSuccess: (File) -> Unit
    ) {
        if (inputFiles.size < 2) {
            error = "Select at least 2 PDFs"
            return
        }

        editor = PdfViewerEditor(context)

        viewModelScope.launch {
            isProcessing = true
            error = null

            val outputFile =
                File(context.cacheDir, "merged_${System.currentTimeMillis()}.pdf")

            editor!!
                .mergePdfs(inputFiles, outputFile)
                .onSuccess(onSuccess)
                .onFailure { error = it.message }

            isProcessing = false
        }
    }
}
