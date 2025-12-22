package com.pubscale.pdf_poc


import android.content.Context
import android.net.Uri
import com.pubscale.pdf_poc.utils.PdfConverter
import com.pubscale.pdf_poc.utils.StringToPdfConverter
import java.io.File

class TextToPdfProcessor : FeatureProcessor {

    override suspend fun process(
        context: Context,
        inputUris: List<Uri>,
        text: String?,
    ): FeatureResult {

        val outputFile = File(
            context.filesDir,
            "text_to_pdf_${System.currentTimeMillis()}.pdf"
        )

        val converter = StringToPdfConverter(context)

        val result = converter.convertStringToPdf(text!!,
            outputFile = outputFile,
        )

        if (result.isSuccess) {
            return FeatureResult.Success(
                "Text converted to PDF",
                outputFile
            )
        }


        return FeatureResult.Error(
            "Failed to convert "
        )
    }
}
