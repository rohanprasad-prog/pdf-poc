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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class ExtractImages: FeatureProcessor {

    suspend fun extractImagesFromPdf(
        pdfFile: File,
        outputDir: File
    ): Result<List<File>> = withContext(Dispatchers.IO) {

        val outputImages = mutableListOf<File>()

        try {
            if (!outputDir.exists()) outputDir.mkdirs()

            PDDocument.load(pdfFile).use { document ->

                var imageIndex = 1

                for (pageIndex in 0 until document.numberOfPages) {
                    val page = document.getPage(pageIndex)
                    val resources = page.resources ?: continue

                    for (xObjectName in resources.xObjectNames) {
                        val xObject = resources.getXObject(xObjectName)

                        if (xObject is PDImageXObject) {

                            // 1️⃣ Convert PDImageXObject → Bitmap
                            val bufferedImage = xObject.image
                            val bitmap = bufferedImage

                            // 2️⃣ Decide format
                            val format = when (xObject.suffix?.lowercase()) {
                                "jpg", "jpeg" -> Bitmap.CompressFormat.JPEG
                                else -> Bitmap.CompressFormat.PNG
                            }

                            val ext = if (format == Bitmap.CompressFormat.JPEG) "jpg" else "png"

                            // 3️⃣ Save bitmap
                            val outFile = File(
                                outputDir,
                                "page_${pageIndex + 1}_img_${imageIndex}.$ext"
                            )

                            FileOutputStream(outFile).use { out ->
                                bitmap.compress(format, 100, out)
                                return@use out
                            }

                            outputImages.add(outFile)
                            imageIndex++
                        }
                    }
                }
            }

            Result.success(outputImages)
        } catch (e: Exception) {
            outputImages.forEach { it.delete() }
            Result.failure(e)
        }
    }




    override suspend fun process(
        context: Context,
        inputUris: List<Uri>,
        text: String?
    ): FeatureResult {


        val result = extractImagesFromPdf(context.uriToTempFile(inputUris.first(), context.getFileName(inputUris.first())), context.filesDir)




        if (result.isSuccess) {
            return FeatureResult.Success(
                "Images converted to PDF",
                result.getOrNull()?.first()
            )
        }


        return FeatureResult.Error(
            "Failed to convert "
        )
    }
}
