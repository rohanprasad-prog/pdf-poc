package com.pubscale.pdf_poc

import android.content.Context
import android.net.Uri
import com.pubscale.pdf_poc.utils.ImageToPdfConverter
import kotlinx.coroutines.delay
import java.io.File

class ImageToPdfProcessor : FeatureProcessor {

    override suspend fun process(
        context: Context,
        inputUris: List<Uri>,
        text: String?,
    ): FeatureResult {
        val outputFile = File(
            context.filesDir,
            "image_to_pdf_${System.currentTimeMillis()}.pdf"
        )

        val converter = ImageToPdfConverter(context)

        val result = converter.convertImagesToPdf(
            imageUris = inputUris,
            outputFile = outputFile)

        if (result.isSuccess) {
            return FeatureResult.Success(
                "Images converted to PDF",
                outputFile
            )
        }


        return FeatureResult.Error(
            "Failed to convert images to PDF "
        )
    }
}