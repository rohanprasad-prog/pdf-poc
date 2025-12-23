package com.pubscale.pdf_poc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.graphics.createBitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.yourpackage.QPdfHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream


class PdfCompressionProcessor : FeatureProcessor {

    suspend fun compressPdf(
        context: Context,
        inputUri: Uri,
        outputFile: File,
        scaleFactor: Float = 0.6f,
        jpegQuality: Int = 70
    ): Result<File> = withContext(Dispatchers.IO) {

        try {
            val fileDescriptor =
                context.contentResolver.openFileDescriptor(inputUri, "r")
                    ?: return@withContext Result.failure(Exception("Invalid PDF"))

            val pdfRenderer = PdfRenderer(fileDescriptor)
            val outputPdf = PdfDocument()

            for (pageIndex in 0 until pdfRenderer.pageCount) {

                val page = pdfRenderer.openPage(pageIndex)

                val width = (page.width * scaleFactor).toInt()
                val height = (page.height * scaleFactor).toInt()

                val bitmap = createBitmap(width, height)

                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)

                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                )

                val pageInfo = PdfDocument.PageInfo.Builder(
                    bitmap.width,
                    bitmap.height,
                    pageIndex + 1
                ).create()

                val pdfPage = outputPdf.startPage(pageInfo)

                val imageStream = ByteArrayOutputStream()
                bitmap.compress(
                    Bitmap.CompressFormat.JPEG,
                    jpegQuality,
                    imageStream
                )

                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)


                outputPdf.finishPage(pdfPage)

                page.close()
                bitmap.recycle()
            }

            pdfRenderer.close()
            fileDescriptor.close()

            FileOutputStream(outputFile).use {
                outputPdf.writeTo(it)
            }

            outputPdf.close()

            Result.success(outputFile)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun process(context: Context, inputUris: List<Uri>,text: String?,): FeatureResult {

        val outputFile = File(
            context.filesDir,
            "compressed_${System.currentTimeMillis()}.pdf"
        )
        val uri = inputUris.first()
        val inputFile = context.uriToTempFile(uri, context.getFileName(uri))

        val inputSizeKb = context.getFileSizeInKB(inputUris.first())

        val pdf = QPdfHelper.compressPdfCustom(inputFile, outputFile)





        if (true) {
            val outputSizeKb = outputFile.length() / 1024

            val savedPercent =
                ((inputSizeKb - outputSizeKb) * 100f / inputSizeKb).toInt()

            Log.d(
                "PDF",
                "Compressed | ${inputSizeKb} KB → ${outputSizeKb} KB (${savedPercent}% saved)"
            )
            return FeatureResult.Success("PDF compressed successfully \n input file size $inputSizeKb KB \n Output file size ${outputFile.length() / 1024} KB \n ${outputFile.canonicalPath}", outputFile)
        }

        return FeatureResult.Error("Failed to compress PDF")
    }
}

fun Context.uriToTempFile(uri: Uri, fileName: String): File {
    val tempFile = File(cacheDir, fileName)

    contentResolver.openInputStream(uri)?.use { input ->
        tempFile.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: throw IllegalArgumentException("Unable to open URI")

    return tempFile
}


fun Context.getFileSizeInKB(uri: Uri): Long {
    return contentResolver.openFileDescriptor(uri, "r")?.use {
        it.statSize / 1024
    } ?: -1L
}

fun Context.getFileName(uri: Uri): String {
    var name = "file"
    contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            name = cursor.getString(
                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
            )
        }
    }
    return name
}
