package com.pubscale.pdf_poc

import android.content.Context
import android.net.Uri
import com.pubscale.pdf_poc.utils.PdfConverter
import com.pubscale.pdf_poc.utils.PdfToExcelConverter
import java.io.File

class PdfToExcelProcessor : FeatureProcessor {

    override suspend fun process(
        context: Context,
        inputUris: List<Uri>,
        text: String?,
    ): FeatureResult {
        val outputFile = File(
            context.filesDir,
            "pdf_to_word_${System.currentTimeMillis()}.xlsx"
        )

        val converter = PdfToExcelConverter(context)

        val result = converter.convertPdfToExcelAdvanced(
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
