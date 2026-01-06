package com.pubscale.pdf_poc.utils

import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition

class PositionTextStripper : PDFTextStripper() {

    data class TextPositionData(
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val page: Int
    )

    val positions = mutableListOf<TextPositionData>()

    override fun writeString(
        text: String,
        textPositions: MutableList<TextPosition>
    ) {
        for (pos in textPositions) {
            positions.add(
                TextPositionData(
                    text = pos.unicode,
                    x = pos.xDirAdj,
                    y = pos.yDirAdj,
                    width = pos.widthDirAdj,
                    height = pos.heightDir,
                    page = currentPageNo
                )
            )
        }
    }
}
