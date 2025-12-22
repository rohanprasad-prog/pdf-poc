package com.pubscale.pdf_poc.presentation.merge


import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
fun MergePdfScreen(
    selectedFiles: List<File>,
    onBack: () -> Unit,
    onMergeSuccess: (File) -> Unit,
    viewModel: MergePdfViewModel = viewModel()
) {

    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Merge PDFs") },
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
                    viewModel.merge(
                        context = context,
                        inputFiles = selectedFiles,
                        onSuccess = onMergeSuccess
                    )
                }
            ) {
                Text("Merge ${selectedFiles.size} PDFs")
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            LazyColumn {
                itemsIndexed(selectedFiles) { index, file ->
                    ListItem(
                        headlineContent = { Text(file.name) },
                        leadingContent = {
                            Text("${index + 1}")
                        }
                    )
                }
            }

            viewModel.error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        if (viewModel.isProcessing) {
            ProcessingOverlay("Merging PDFs…")
        }
    }
}
