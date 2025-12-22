package com.pubscale.pdf_poc.presentation.split

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pubscale.pdf_poc.presentation.ProcessingOverlay
import com.pubscale.pdf_poc.utils.PdfViewerEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitPdfScreen(
    pdfFile: File,
    onBack: () -> Unit,
    onSplitSuccess: (List<File>) -> Unit,
    viewModel: SplitPdfViewModel = viewModel()
) {
    val context = LocalContext.current
    var splitMode by remember { mutableStateOf(SplitMode.EACH_PAGE) }
    var pageInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Split PDF") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        },
        bottomBar = {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                enabled = !viewModel.isProcessing,
                onClick = {


                    when (splitMode) {
                        SplitMode.EACH_PAGE ->
                            viewModel.splitEachPage(
                                context,
                                pdfFile,
                                onSplitSuccess
                            )

                        SplitMode.CUSTOM_RANGE ->
                            viewModel.splitAtPages(
                                context,
                                pdfFile,
                                pageInput.split(",")
                                    .mapNotNull { it.trim().toIntOrNull() }
                                    .map { it - 1 },
                                onSplitSuccess
                            )
                    }
                }
            ) {
                Text("Split PDF")
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
        ) {

            Text("Split Mode")

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = splitMode == SplitMode.EACH_PAGE,
                    onClick = { splitMode = SplitMode.EACH_PAGE }
                )
                Text("Each page")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = splitMode == SplitMode.CUSTOM_RANGE,
                    onClick = { splitMode = SplitMode.CUSTOM_RANGE }
                )
                Text("Custom pages (e.g. 2,4,6)")
            }

            if (splitMode == SplitMode.CUSTOM_RANGE) {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it },
                    label = { Text("Page numbers") }
                )
            }

            viewModel.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }

        if (viewModel.isProcessing) {
            ProcessingOverlay("Splitting PDF…")
        }
    }
}
