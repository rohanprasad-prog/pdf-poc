package com.pubscale.pdf_poc

import android.content.Context
import android.net.Uri
import com.pubscale.pdf_poc.utils.EnhanceImage
import com.pubscale.pdf_poc.utils.ExtractImages
import com.pubscale.pdf_poc.utils.ExtractText
import com.pubscale.pdf_poc.utils.PdfViewerEditor


object FeatureProcessorFactory {

    fun get(feature: PdfFeature, context : Context): FeatureProcessor {
        return when (feature) {
            PdfFeature.PDF_COMPRESSION -> PdfCompressionProcessor()
            PdfFeature.IMAGE_TO_PDF -> ImageToPdfProcessor()
            PdfFeature.PDF_TO_WORD -> PdfToWordProcessor()
            PdfFeature.PDF_TO_EXCEL -> PdfToExcelProcessor()
            PdfFeature.PDF_TO_PPT -> PdfToPPtProcessor()
            PdfFeature.PDF_TO_IMAGE -> PdfToImageProcessor()
            PdfFeature.TEXT_TO_PDF -> TextToPdfProcessor()
            PdfFeature.PDF_EDITOR -> PdfViewerEditor(context)
            PdfFeature.SPLIT_PDF -> SuccessesProcessor()
            PdfFeature.MERGE_PDF ->  SuccessesProcessor()
            PdfFeature.EXTRACT_IMAGE -> ExtractImages()
            PdfFeature.EXTRACT_TEXT -> ExtractText()
            PdfFeature.ENHANCE_IMAGE -> EnhanceImage()
        }
    }
}

class SuccessesProcessor : FeatureProcessor {
    override suspend fun process(
        context: Context,
        inputUris: List<Uri>,
        text: String?
    ): FeatureResult {
        return FeatureResult.Success( inputUris.first().toString(),context.uriToTempFile(inputUris.first(), context.getFileName(inputUris.first())))

    }
}