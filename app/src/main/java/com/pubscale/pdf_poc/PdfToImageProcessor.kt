package com.pubscale.pdf_poc

import android.content.Context
import android.net.Uri
import com.pubscale.pdf_poc.utils.PdfToImageConverter
import com.pubscale.pdf_poc.utils.PdfToPptConverter
import java.io.File

class PdfToImageProcessor : FeatureProcessor {

    override suspend fun process(
        context: Context,
        inputUris: List<Uri>,
        text: String?,
    ): FeatureResult {
        val outputFile = File(
            context.filesDir,
            "pdf_to_word_${System.currentTimeMillis()}.png"
        )

        val converter = PdfToImageConverter(context)

        val result = converter.convertPageToImage(
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
