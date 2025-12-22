package com.pubscale.pdf_poc.presentation

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pubscale.pdf_poc.presentation.rearrange.RearrangePdfScreen
import com.pubscale.pdf_poc.utils.PdfViewerEditor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

// ==========================================
// PDF VIEWER SCREEN
// ==========================================

/**
 * Main PDF Viewer Screen (Compose)
 *
 * Usage:
 * ```
 * PdfViewerScreen(
 *     pdfUri = uri,
 *     onNavigateBack = { navController.popBackStack() }
 * )
 * ```
 */
@RequiresApi(Build.VERSION_CODES.P)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    pdfUri: Uri,
    onNavigateBack: () -> Unit,
    viewModel: PdfViewerViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(pdfUri) {
        viewModel.loadPdf(context, pdfUri)
    }

    var selectedImageBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val imagePicker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            uri?.let {
                val source = ImageDecoder.createSource(
                    context.contentResolver,
                    uri
                )

                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }

                Log.d(
                    "BitmapCheck",
                    "w=${bitmap.width}, h=${bitmap.height}, config=${bitmap.config}"
                )


                selectedImageBitmap = bitmap
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.fileName,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Page counter
                    Text(
                        text = "${uiState.currentPage + 1} / ${uiState.totalPages}",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            )
        },
        bottomBar = {
            PdfViewerBottomBar(
                currentPage = uiState.currentPage,
                totalPages = uiState.totalPages,
                viewMode = uiState.viewMode,
                onPageChanged = { viewModel.goToPage(it) },
                onViewModeChanged = { viewModel.changeViewMode(it) },
                onZoomIn = { viewModel.zoomIn() },
                onZoomOut = { viewModel.zoomOut() }, viewModel, uiState.invertColors, { imagePicker.launch("image/*")}
            )
        }
    ) { paddingValues ->


        Box(
            modifier = Modifier
                .background(Color.White)
                .fillMaxSize()
                .padding(paddingValues)

        ) {



            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.error != null -> {
                    ErrorView(
                        error = uiState.error!!,
                        onRetry = { viewModel.loadPdf(context, pdfUri) }
                    )
                }

                uiState.showRearrange -> {
                    RearrangePdfScreen(
                        pages = uiState.allBitmaps,
                        onBack = { viewModel.hideRearrange() },
                        onSave = {
                            viewModel.rearrangePages(context, it, {
                                viewModel.hideRearrange()
                            }, { error ->
                                Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                            })
                        }
                    )

                }
                else -> {
                    when (uiState.viewMode) {
                        ViewMode.SINGLE_PAGE -> {
                            SinglePageView(
                                bitmap = uiState.currentBitmap,
                                zoomState = uiState.zoomState,
                                invertColors = uiState.invertColors,
                                onZoomChange = { viewModel.updateZoom(it) },
                                onTap = { tapOffset, width,height ->
                                    viewModel.placeImageAt(
                                        context = context,
                                        tapOffset = tapOffset,
                                        viewWidth = width,
                                        viewHeight = height,
                                        imageBitmap = selectedImageBitmap
                                    )
                                }

                            )
                        }
                        ViewMode.CONTINUOUS -> {
                            ContinuousPageView(
                                bitmaps = uiState.allBitmaps,
                                currentPage = uiState.currentPage,
                                onPageChanged = { viewModel.updateCurrentPage(it) }
                            )
                        }
                    }
                }
            }

            // Loading overlay for page changes
            if (uiState.isEditing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

        }
    }
}

// ==========================================
// SINGLE PAGE VIEW
// ==========================================

@Composable
fun SinglePageView(
    bitmap: Bitmap?,
    zoomState: ZoomState,
    invertColors: Boolean,
    onZoomChange: (ZoomState) -> Unit,
    onTap: (Offset, Float, Float) -> Unit
) {
    val minScale = 1f
    val maxScale = 5f

    val colorMatrix = remember(invertColors) {
        if (invertColors) {
            ColorMatrix(
                floatArrayOf(
                    -1f, 0f, 0f, 0f, 255f,
                    0f, -1f, 0f, 0f, 255f,
                    0f, 0f, -1f, 0f, 255f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        } else {
            ColorMatrix()
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        bitmap?.let {

            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            var transformDetected = false

                            do {
                                val event = awaitPointerEvent()

                                val zoom = event.calculateZoom()
                                val pan = event.calculatePan()

                                if (zoom != 1f || pan != Offset.Zero) {
                                    transformDetected = true

                                    val newScale =
                                        (zoomState.scale * zoom).coerceIn(minScale, maxScale)

                                    val newOffset =
                                        if (newScale == minScale) Offset.Zero
                                        else zoomState.offset + pan

                                    onZoomChange(
                                        zoomState.copy(
                                            scale = newScale,
                                            offset = newOffset
                                        )
                                    )
                                }
                            } while (event.changes.any { it.pressed })

                            // 👇 This is a TAP (no transform)
                            if (!transformDetected) {
                                val tapPosition = down.position

                                // 👇 THIS is size
                                val viewWidth = size.width.toFloat()
                                val viewHeight = size.height.toFloat()

                                onTap(tapPosition, viewWidth, viewHeight)
                            }
                        }
                    }

                    .graphicsLayer {
                        scaleX = zoomState.scale
                        scaleY = zoomState.scale
                        translationX = zoomState.offset.x
                        translationY = zoomState.offset.y
                    },
                colorFilter = ColorFilter.colorMatrix(colorMatrix),
                contentScale = ContentScale.Fit,

            )
        }
    }
}


// ==========================================
// CONTINUOUS PAGE VIEW
// ==========================================

@Composable
fun ContinuousPageView(
    bitmaps: List<Bitmap>,
    currentPage: Int,
    onPageChanged: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentPage) {
        listState.animateScrollToItem(currentPage)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF303030)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(bitmaps) { bitmap ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "PDF Page",
                    modifier = Modifier.fillMaxWidth(),
                    contentScale = ContentScale.FillWidth
                )
            }
        }
    }

    // Track visible page
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { page ->
                onPageChanged(page)
            }
    }
}

// ==========================================
// BOTTOM BAR
// ==========================================

@Composable
fun PdfViewerBottomBar(
    currentPage: Int,
    totalPages: Int,
    viewMode: ViewMode,
    onPageChanged: (Int) -> Unit,
    onViewModeChanged: (ViewMode) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    viewModel: PdfViewerViewModel,
    invertColors : Boolean,
     imagePicker: () -> Unit
) {
    BottomAppBar {

        val context = LocalContext.current
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            )
            {
                // Previous page
                IconButton(
                    onClick = { if (currentPage > 0) onPageChanged(currentPage - 1) },
                    enabled = currentPage > 0
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Previous")
                }

                // Zoom out
                IconButton(onClick = onZoomOut) {
                    Icon(Icons.Default.ZoomOut, "Zoom Out")
                }

                // View mode toggle
                IconButton(
                    onClick = {
                        val newMode = when (viewMode) {
                            ViewMode.SINGLE_PAGE -> ViewMode.CONTINUOUS
                            ViewMode.CONTINUOUS -> ViewMode.SINGLE_PAGE
                        }
                        onViewModeChanged(newMode)
                    }
                ) {
                    Icon(
                        imageVector = when (viewMode) {
                            ViewMode.SINGLE_PAGE -> Icons.Default.ContactPage
                            ViewMode.CONTINUOUS -> Icons.Default.Pages
                        },
                        contentDescription = "Toggle View Mode"
                    )
                }

                // Zoom in
                IconButton(onClick = onZoomIn) {
                    Icon(Icons.Default.ZoomIn, "Zoom In")
                }

                // Next page
                IconButton(
                    onClick = { if (currentPage < totalPages - 1) onPageChanged(currentPage + 1) },
                    enabled = currentPage < totalPages - 1
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, "Next")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                IconButton(onClick = { viewModel.addText("Hello", 100f, 700f) }) {
                    Icon(Icons.Default.Edit, "Add Text")
                }

                IconButton(onClick = { viewModel.rotateCurrentPage() }) {
                    Icon(Icons.AutoMirrored.Filled.RotateRight, "Rotate")
                }

                IconButton(onClick = { viewModel.deleteCurrentPage() }) {
                    Icon(Icons.Default.Delete, "Delete Page")
                }

                IconButton(onClick = { viewModel.addWatermark("DRAFT") }) {
                    Icon(Icons.Default.WaterDrop, "Watermark")
                }

                IconButton(onClick = { viewModel.removeDuplicatePages("Delete duplicate pages") }) {
                    Icon(Icons.Default.DeleteSweep, "Delete duplicate pages")
                }

                IconButton(
                    onClick = {
                        viewModel.showRearrange()

                    }
                ) {
                    Icon(Icons.Default.Reorder, "Rearrange")
                }

                IconButton(onClick = { viewModel.toggleInvert() }) {
                    Icon(
                        imageVector = Icons.Default.InvertColors,
                        contentDescription = "Invert Colors",
                         tint = if (invertColors)
                            MaterialTheme.colorScheme.primary
                        else
                            LocalContentColor.current

                    )
                }

                IconButton(
                    onClick = {
                        imagePicker()

                    }
                ) {
                    Icon(Icons.Default.Image, "Rearrange")
                }



                IconButton(onClick = { viewModel.savePdfToStorage(context, {
                    Toast.makeText(context, "Saved to $it", Toast.LENGTH_SHORT).show()
                }, {
                    Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
                }) }, ) {
                    Icon(Icons.Default.Save, "Save")
                }
            }
        }
    }
}

// ==========================================
// ERROR VIEW
// ==========================================

@Composable
fun ErrorView(
    error: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Error,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Failed to load PDF",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, "Retry")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry")
        }
    }
}

fun screenToPdfCoords(
    tapX: Float,
    tapY: Float,
    viewWidth: Float,
    viewHeight: Float,
    pdfPageWidth: Float,
    pdfPageHeight: Float
): Pair<Float, Float> {

    val scaleX = pdfPageWidth / viewWidth
    val scaleY = pdfPageHeight / viewHeight

    val pdfX = tapX * scaleX
    val pdfY = pdfPageHeight - (tapY * scaleY)

    return pdfX to pdfY
}


@Stable
data class ZoomState(
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero
)


// ==========================================
// VIEW MODEL
// ==========================================

class PdfViewerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PdfViewerUiState())
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    fun showRearrange() {
        _uiState.update { it.copy(showRearrange = true) }
    }

    fun hideRearrange() {
        _uiState.update { it.copy(showRearrange = false) }
    }


    private var pdfEditor: PdfViewerEditor? = null
    private var pdfFile: File? = null

    fun toggleInvert() {
        _uiState.update {
            it.copy(invertColors = !it.invertColors)
        }
    }

    fun placeImageAt(
        context: Context,
        tapOffset: Offset,
        viewWidth: Float,
        viewHeight: Float,
        imageBitmap: Bitmap?
    ) {

        if (imageBitmap == null) return
        val input = pdfFile ?: return
        val editor = pdfEditor ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isEditing = true) }

            try {
                // 1️⃣ Get PDF page size
                val pageSize = withContext(Dispatchers.IO) {
                    PDDocument.load(input).use { doc ->
                        val page = doc.getPage(_uiState.value.currentPage)
                        page.mediaBox
                    }
                }

                val zoom = _uiState.value.zoomState

                // 2️⃣ Undo pan
                val unpannedX = (tapOffset.x - zoom.offset.x)
                val unpannedY = (tapOffset.y - zoom.offset.y)

                // 3️⃣ Undo zoom
                val unscaledX = unpannedX / zoom.scale
                val unscaledY = unpannedY / zoom.scale

                // 4️⃣ Convert screen → PDF coordinates
                val pdfX = (unscaledX / viewWidth) * pageSize.width
                val pdfY =
                    pageSize.height - ((unscaledY / viewHeight) * pageSize.height)

                // 5️⃣ Image size in PDF units
                val imageWidthPdf = pageSize.width * 0.2f
                val imageHeightPdf =
                    imageWidthPdf * imageBitmap.height / imageBitmap.width

                // 6️⃣ Output file
                val output =
                    File(context.cacheDir, "image_${System.currentTimeMillis()}.pdf")

                editor.addImageToPdf(
                    pdfFile = input,
                    outputFile = output,
                    pageIndex = _uiState.value.currentPage,
                    imageBitmap = imageBitmap,
                    x = pdfX,
                    y = pdfY,
                    width = imageWidthPdf,
                    height = imageHeightPdf
                ).onSuccess {
                    pdfFile = it
                    reloadAfterEdit()
                }.onFailure {
                    _uiState.update { s -> s.copy(error = it.message) }
                }
            } finally {
                _uiState.update { it.copy(isEditing = false) }
            }
        }
    }



    fun loadPdf(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                pdfEditor = PdfViewerEditor(context)

                // Load PDF from URI
                val loadResult = pdfEditor!!.loadPdfFromUri(uri)

                loadResult.onSuccess { file ->
                    pdfFile = file

                    // Get PDF info
                    val infoResult = pdfEditor!!.getPdfInfo(file)
                    infoResult.onSuccess { info ->
                        _uiState.update {
                            it.copy(
                                totalPages = info.pageCount,
                                fileName = info.title.ifEmpty { "Document.pdf" }
                            )
                        }
                    }

                    // Load first page or all pages based on view mode
                    when (_uiState.value.viewMode) {
                        ViewMode.SINGLE_PAGE -> loadPage(0)
                        ViewMode.CONTINUOUS -> loadAllPages()
                    }

                    _uiState.update { it.copy(isLoading = false) }
                }.onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Unknown error"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
            }
        }
    }

    fun updateZoom(zoomState: ZoomState) {
        _uiState.update { it.copy(zoomState = zoomState) }
    }

    fun zoomIn() {
        val z = _uiState.value.zoomState
        updateZoom(z.copy(scale = (z.scale + 0.5f).coerceAtMost(5f)))
    }

    fun zoomOut() {
        val z = _uiState.value.zoomState
        updateZoom(z.copy(scale = (z.scale - 0.5f).coerceAtLeast(1f)))
    }

    fun rearrangePages(
        context: Context,
        newOrder: List<Int>,
        onSuccess: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val input = pdfFile ?: return
        val editor = PdfViewerEditor(context)

        viewModelScope.launch {
            val output =
                File(context.cacheDir, "reordered_${System.currentTimeMillis()}.pdf")

            editor
                .reorderPages(input, output, newOrder)
                .onSuccess {
                    pdfFile = it
                    reloadAfterEdit()
                    onSuccess(it)
                }
                .onFailure {
                    onError(it.message ?: "Reorder failed")
                }
        }
    }


    fun savePdfToStorage(
        context: Context,
        onSaved: (Uri) -> Unit,
        onError: (String) -> Unit
    ) {
        val file = pdfFile ?: return

        viewModelScope.launch {
            pdfEditor?.savePdfToDownloads(
                context = context,
                inputFile = file,
                displayName = uiState.value.fileName
            )
                ?.onSuccess(onSaved)
                ?.onFailure { onError(it.message ?: "Save failed") }
        }
    }


    private suspend fun loadPage(pageIndex: Int) {
        pdfFile?.let { file ->
            _uiState.update {
                it.copy(
                    isLoadingPage = true,
                    zoomState = ZoomState() // reset zoom
                )
            }

            val result = pdfEditor?.renderPageToBitmap(file, pageIndex, _uiState.value.scale)

            result?.onSuccess { bitmap ->
                _uiState.update {
                    it.copy(
                        currentPage = pageIndex,
                        currentBitmap = bitmap,
                        isLoadingPage = false
                    )
                }
            }?.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoadingPage = false,
                        error = error.message
                    )
                }
            }
        }
    }

    private suspend fun loadAllPages() {
        pdfFile?.let { file ->
            _uiState.update { it.copy(isLoadingPage = true) }

            val bitmaps = mutableListOf<Bitmap>()
            val totalPages = _uiState.value.totalPages

            for (i in 0 until totalPages) {
                val result = pdfEditor?.renderPageToBitmap(file, i, 1.0f)
                result?.onSuccess { bitmap ->
                    bitmaps.add(bitmap)
                }
            }

            _uiState.update {
                it.copy(
                    allBitmaps = bitmaps,
                    isLoadingPage = false
                )
            }
        }
    }

    fun addImageToPage(
        context: Context,
        imageBitmap: Bitmap,
        pdfX: Float,
        pdfY: Float,
        pdfWidth: Float,
        pdfHeight: Float
    ) {
        val input = pdfFile ?: return
        val editor = pdfEditor ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isEditing = true) }

            val output =
                File(context.cacheDir, "image_${System.currentTimeMillis()}.pdf")

            editor.addImageToPdf(
                pdfFile = input,
                outputFile = output,
                pageIndex = _uiState.value.currentPage,
                imageBitmap = imageBitmap,
                x = pdfX,
                y = pdfY,
                width = pdfWidth,
                height = pdfHeight
            )
                .onSuccess {
                    pdfFile = it
                    reloadAfterEdit()
                }
                .onFailure {
                    _uiState.update { s -> s.copy(error = it.message) }
                }

            _uiState.update { it.copy(isEditing = false) }
        }
    }


    fun addText(
        text: String,
        x: Float,
        y: Float,
        fontSize: Float = 16f
    ) {
        val input = pdfFile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isEditing = true) }

            val output = File(input.parent, "edited_${System.currentTimeMillis()}.pdf")

            pdfEditor!!
                .addTextToPdf(
                    pdfFile = input,
                    outputFile = output,
                    pageIndex = _uiState.value.currentPage,
                    text = text,
                    x = x,
                    y = y,
                    fontSize = fontSize
                )
                .onSuccess {
                    pdfFile = it
                    reloadAfterEdit()
                }
                .onFailure {
                    _uiState.update { s -> s.copy(error = it.message) }
                }

            _uiState.update { it.copy(isEditing = false) }
        }
    }

    fun rotateCurrentPage(rotation: Int = 90) {
        val input = pdfFile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isEditing = true) }

            val output = File(input.parent, "rotated_${System.currentTimeMillis()}.pdf")

            pdfEditor!!
                .rotatePages(
                    pdfFile = input,
                    outputFile = output,
                    pageIndices = listOf(_uiState.value.currentPage),
                    rotation = rotation
                )
                .onSuccess {
                    pdfFile = it
                    reloadAfterEdit()
                }

            _uiState.update { it.copy(isEditing = false) }
        }
    }

    fun deleteCurrentPage() {
        val input = pdfFile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isEditing = true) }

            val output = File(input.parent, "deleted_${System.currentTimeMillis()}.pdf")

            pdfEditor!!
                .deletePages(
                    pdfFile = input,
                    outputFile = output,
                    pageIndices = listOf(_uiState.value.currentPage)
                )
                .onSuccess {
                    pdfFile = it
                    _uiState.update { s ->
                        s.copy(
                            currentPage = maxOf(0, s.currentPage - 1)
                        )
                    }
                    reloadAfterEdit()
                }

            _uiState.update { it.copy(isEditing = false) }
        }
    }

    fun removeDuplicatePages(text: String) {
        val input = pdfFile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isEditing = true) }

            val output = File(input.parent, "watermarked_${System.currentTimeMillis()}.pdf")

            pdfEditor!!
                .deleteDuplicatePages(
                    pdfFile = input,
                    outputFile = output,
                )
                .onSuccess {
                    pdfFile = it
                    reloadAfterEdit()
                }

            _uiState.update { it.copy(isEditing = false) }
        }
    }

    fun addWatermark(text: String) {
        val input = pdfFile ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isEditing = true) }

            val output = File(input.parent, "watermarked_${System.currentTimeMillis()}.pdf")

            pdfEditor!!
                .addWatermarkToAllPages(
                    pdfFile = input,
                    outputFile = output,
                    watermarkText = text
                )
                .onSuccess {
                    pdfFile = it
                    reloadAfterEdit()
                }

            _uiState.update { it.copy(isEditing = false) }
        }
    }

    private suspend fun reloadAfterEdit() {
        when (_uiState.value.viewMode) {
            ViewMode.SINGLE_PAGE -> loadPage(_uiState.value.currentPage)
            ViewMode.CONTINUOUS -> loadAllPages()
        }
    }


    fun goToPage(pageIndex: Int) {
        if (pageIndex in 0 until _uiState.value.totalPages) {
            viewModelScope.launch {
                when (_uiState.value.viewMode) {
                    ViewMode.SINGLE_PAGE -> loadPage(pageIndex)
                    ViewMode.CONTINUOUS -> {
                        _uiState.update { it.copy(currentPage = pageIndex) }
                    }
                }
            }
        }
    }

    fun updateCurrentPage(pageIndex: Int) {
        _uiState.update { it.copy(currentPage = pageIndex) }
    }

    fun changeViewMode(viewMode: ViewMode) {
        viewModelScope.launch {
            _uiState.update { it.copy(viewMode = viewMode) }

            when (viewMode) {
                ViewMode.SINGLE_PAGE -> loadPage(_uiState.value.currentPage)
                ViewMode.CONTINUOUS -> loadAllPages()
            }
        }
    }


    fun updateScale(scale: Float) {
        _uiState.update { it.copy(scale = scale) }

        if (_uiState.value.viewMode == ViewMode.SINGLE_PAGE) {
            viewModelScope.launch {
                loadPage(_uiState.value.currentPage)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up temp file
        pdfFile?.delete()
    }
}

fun Bitmap.toSoftwareBitmap(): Bitmap {
    return if (config == Bitmap.Config.HARDWARE) {
        copy(Bitmap.Config.ARGB_8888, false)
    } else {
        this
    }
}


// ==========================================
// UI STATE
// ==========================================

data class PdfViewerUiState(
    val isLoading: Boolean = false,
    val isLoadingPage: Boolean = false,
    val isEditing: Boolean = false,
    val error: String? = null,
    val fileName: String = "Document.pdf",
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val currentBitmap: Bitmap? = null,
    val allBitmaps: List<Bitmap> = emptyList(),
    val scale: Float = 1.0f,
    val viewMode: ViewMode = ViewMode.SINGLE_PAGE,
    val zoomState: ZoomState = ZoomState(),
    val showRearrange: Boolean = false,
    val invertColors: Boolean = false
)

enum class ViewMode {
    SINGLE_PAGE,
    CONTINUOUS
}
