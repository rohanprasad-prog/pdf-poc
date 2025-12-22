package com.pubscale.pdf_poc

import android.content.Context
import android.net.Uri
import com.pubscale.pdf_poc.utils.PdfConverter
import java.io.File

class PdfToWordProcessor : FeatureProcessor {

    override suspend fun process(
        context: Context,
        inputUris: List<Uri>,
        text: String?,
    ): FeatureResult {
        val outputFile = File(
            context.filesDir,
            "pdf_to_word_${System.currentTimeMillis()}.docx"
        )

        val converter = PdfConverter(context)

        val result = converter.convertPdfToWordAdvanced(
            outputFile = outputFile,
            pdfFile = context.uriToTempFile(inputUris.first(), context.getFileName(inputUris.first())),
            )

        if (result.isSuccess) {
            return FeatureResult.Success(
                "Images converted to PDF",
                outputFile
            )
        }


        return FeatureResult.Error(
            "Failed to convert "
        )
    }
}
