package com.pubscale.pdf_poc.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.pubscale.pdf_poc.FeatureProcessor
import com.pubscale.pdf_poc.FeatureResult
import com.pubscale.pdf_poc.getFileName
import com.pubscale.pdf_poc.uriToTempFile
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ExtractText: FeatureProcessor {

    suspend fun extractTextFromPdf(
        pdfFile: File
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            PDDocument.load(pdfFile).use { document ->
                val stripper = PDFTextStripper().apply {
                    sortByPosition = true
                }
                Result.success(stripper.getText(document))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }



    override suspend fun process(
        context: Context,
        inputUris: List<Uri>,
        text: String?
    ): FeatureResult {


        val result = extractTextFromPdf(context.uriToTempFile(inputUris.first(), context.getFileName(inputUris.first())))




        if (result.isSuccess) {
            return FeatureResult.Success(
                result.getOrNull() ?: "",
                null
            )
        }


        return FeatureResult.Error(
            "Failed to convert "
        )
    }
}
