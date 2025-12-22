package com.pubscale.pdf_poc

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pubscale.pdf_poc.presentation.PdfViewerScreen
import com.pubscale.pdf_poc.presentation.merge.MergePdfScreen
import com.pubscale.pdf_poc.presentation.rearrange.RearrangePdfScreen
import com.pubscale.pdf_poc.presentation.rearrange.weight
import com.pubscale.pdf_poc.presentation.split.SplitPdfScreen
import kotlinx.coroutines.launch
import java.io.File

enum class Screens {
    EDIT, MERGE, SPLIT
}
@Composable
fun PlaygroundScreen(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedFeature by remember { mutableStateOf(PdfFeature.PDF_COMPRESSION) }
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var resultText by remember { mutableStateOf("") }
    var resultState by remember { mutableStateOf<FeatureResult?>(null) }
    var inputText by remember { mutableStateOf("") }
    var showPDFView by remember { mutableStateOf<File?>(null) }
    var screen by remember { mutableStateOf<Screens?>(null) }




    val launcher = rememberLauncherForActivityResult(
        contract = OpenMultipleDocuments()
    ) { uris ->
        selectedUris = uris
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ){


        when(screen){
            Screens.EDIT -> {
                showPDFView?.let {
                    PdfViewerScreen(it.toContentUri(context), {
                        showPDFView = null
                        resultState =  null
                        screen = null
                    })
                }
            }
            Screens.MERGE ->  showPDFView?.let {
                MergePdfScreen(selectedUris.map {
                    context.uriToTempFile(it, context.getFileName(it))
                }, {
                    showPDFView = null
                    resultState =  null
                    screen = null
                }, { files ->
                    Toast.makeText(context, "Merge successful files : ${files}", Toast.LENGTH_SHORT).show()
                    openFile(context, files.toContentUri(context), "application/pdf", "Open PDF")
                    screen = null
                    showPDFView = null
                    resultState =  null
                })
            }
            Screens.SPLIT ->  showPDFView?.let {
                SplitPdfScreen(it, {
                    showPDFView = null
                    resultState =  null
                    screen = null
                }, { files ->
                    Toast.makeText(context, "Split successful files : ${files.size}", Toast.LENGTH_SHORT).show()
                    screen = null
                    showPDFView = null
                    resultState =  null
                })
            }

            null -> {
                Column(
                    modifier = Modifier
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                )
                {



                    // Feature Dropdown
                    FeatureDropdown(
                        selectedFeature = selectedFeature,
                        onFeatureSelected = { it ->
                            selectedFeature = it
                            selectedUris = emptyList()
                            resultText = ""
                        }
                    )

                    if(selectedFeature == PdfFeature.TEXT_TO_PDF) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it}
                        )
                    }else {

                        // File Picker
                        Button(onClick = {
                            launcher.launch(
                                selectedFeature.supportedMimeTypes.toTypedArray()
                            )
                        }) {
                            Text("Select File(s)")
                        }

                        Text("Selected: ${selectedUris.size} file(s)")
                    }

                    // Run Button
                    Button(
                        enabled = selectedUris.isNotEmpty() || (selectedFeature == PdfFeature.TEXT_TO_PDF && inputText.isNotEmpty()),
                        onClick = {
                            scope.launch {
                                val processor =
                                    FeatureProcessorFactory.get(selectedFeature, context)

                                val result =
                                    processor.process(context, selectedUris, inputText)

                                resultState = result

                                resultText = when (result) {
                                    is FeatureResult.Success -> if(selectedFeature != PdfFeature.EXTRACT_TEXT) result.message else "Text extracted successfully"
                                    is FeatureResult.Error -> result.error
                                }
                            }
                        }
                    ) {
                        Text("Run Feature")
                    }

                    // Result
                    Text(
                        text = resultText,
                        modifier = Modifier.padding(top = 16.dp)
                    )

                    val context = LocalContext.current

                    if(resultState is FeatureResult.Success){

                        if(selectedFeature == PdfFeature.EXTRACT_TEXT){
                            Text(
                                text = (resultState as FeatureResult.Success).message,
                                modifier = Modifier
                                    .fillMaxWidth(0.96f)
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState())
                                    .border(1.dp, Color.Black,RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                            )

                            return

                        }
                        (resultState as FeatureResult.Success).file?.let { file ->

                            // 🖼 Images (jpg, png, webp, heic…)

                            // 📄 PDF

                            // 📝 Word

                            // 📊 Excel

                            // 📽 PowerPoint
                            when (selectedFeature) {
                                PdfFeature.PDF_EDITOR -> {
                                    showPDFView = file
                                    screen = Screens.EDIT
                                    return
                                }

                                PdfFeature.MERGE_PDF -> {
                                    showPDFView = context.uriToTempFile(selectedUris.first(), context.getFileName(selectedUris.first()))
                                    screen = Screens.MERGE
                                    return
                                }

                                PdfFeature.SPLIT_PDF -> {
                                    showPDFView = context.uriToTempFile(selectedUris.first(), context.getFileName(selectedUris.first()))
                                    screen = Screens.SPLIT
                                    return
                                }



                                else -> {
                                    val uri = file.toContentUri(context)
                                    val mimeType = context.contentResolver.getType(uri)

                                    when {

                                        // 🖼 Images (jpg, png, webp, heic…)
                                        mimeType?.startsWith("image/") == true -> {
                                            openFile(context, uri, mimeType, "Open Image")
                                        }

                                        // 📄 PDF
                                        mimeType == "application/pdf" -> {
                                            openFile(context, uri, "application/pdf", "Open PDF")
                                        }

                                        // 📝 Word
                                        mimeType == "application/msword" ||
                                                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> {
                                            openFile(context, uri, mimeType, "Open Document")
                                        }

                                        // 📊 Excel
                                        mimeType == "application/vnd.ms-excel" ||
                                                mimeType == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> {
                                            openFile(context, uri, mimeType, "Open Spreadsheet")
                                        }

                                        // 📽 PowerPoint
                                        mimeType == "application/vnd.ms-powerpoint" ||
                                                mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> {
                                            openFile(context, uri, mimeType, "Open Presentation")
                                        }

                                        else -> {
                                            Toast.makeText(
                                                context,
                                                "Unsupported file type",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                            }
                        }



                    }
                }
            }
        }




    }


}

fun openFile(
    context: Context,
    uri: Uri,
    mimeType: String,
    title: String
) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    try {
        context.startActivity(Intent.createChooser(intent, title))
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No app found to open this file", Toast.LENGTH_LONG).show()
    }
}


@Composable
fun FeatureDropdown(
    selectedFeature: PdfFeature,
    onFeatureSelected: (PdfFeature) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text(selectedFeature.title)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            PdfFeature.entries.forEach {
                DropdownMenuItem(
                    text = { Text(it.title) },
                    onClick = {
                        expanded = false
                        onFeatureSelected(it)
                    }
                )
            }
        }
    }
}

fun File.toContentUri(context: Context): Uri {
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        this
    )
}


fun copyPdfToDownloads(
    context: Context,
    sourceFile: File,
    fileName: String
): Uri? {

    val resolver = context.contentResolver

    val values = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, fileName)
        put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
        put(MediaStore.Downloads.IS_PENDING, 1)
    }

    val downloadUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        resolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values
        ) ?: return null
    } else {
        null
    }

    downloadUri?.let { resolver.openOutputStream(it) }?.use { out ->
        sourceFile.inputStream().use { input ->
            input.copyTo(out)
        }
    }

    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    downloadUri?.let { resolver.update(it, values, null, null) }

    return downloadUri
}

