package com.pubscale.pdf_poc


enum class PdfFeature(
    val title: String,
    val supportedMimeTypes: List<String>,
    val allowMultiple: Boolean
) {
    PDF_COMPRESSION(
        title = "PDF Compression",
        supportedMimeTypes = listOf("application/pdf"),
        allowMultiple = false
    ),

    IMAGE_TO_PDF(
        title = "Image to PDF",
        supportedMimeTypes = listOf("image/*"),
        allowMultiple = true
    ),

    PDF_TO_WORD(
        title = "PDF to Word",
        supportedMimeTypes = listOf("application/pdf"),
        allowMultiple = false
    ),

    PDF_TO_EXCEL(
        title = "PDF to Excel",
        supportedMimeTypes = listOf("application/pdf"),
        allowMultiple = false
    ),

    PDF_TO_PPT(
        title = "PDF to PPT",
        supportedMimeTypes = listOf("application/pdf"),
        allowMultiple = false
    ),

    PDF_TO_IMAGE(
        title = "PDF to Image",
        supportedMimeTypes = listOf("application/pdf"),
        allowMultiple = false
    ),

    TEXT_TO_PDF(
        title = "Text to PDF",
        supportedMimeTypes = listOf("text/plain"),
        allowMultiple = false
    ),

    PDF_EDITOR(
    title = "Edit PDF",
        supportedMimeTypes = listOf("application/pdf"),
    allowMultiple = false
    ),

    SPLIT_PDF(
        title = "Split PDF",
        supportedMimeTypes = listOf("application/pdf"),
        allowMultiple = false
    ),

    MERGE_PDF(
        title = "Merge PDF",
        supportedMimeTypes = listOf("application/pdf"),
        allowMultiple = true
    ),

    EXTRACT_IMAGE(
        title = "Extract Image",
        supportedMimeTypes = listOf("application/pdf"),
        allowMultiple = true
    ),

    EXTRACT_TEXT(
        title = "Extract Text",
        supportedMimeTypes = listOf("application/pdf"),
        allowMultiple = true
    ),

    ENHANCE_IMAGE(
        title = "Enhance Image",
        supportedMimeTypes = listOf("image/*"),
        allowMultiple = true
    ),

}
