package com.pubscale.pdf_poc.presentation.edit

import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import com.pubscale.pdf_poc.utils.PositionTextStripper
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import kotlin.math.max
import kotlin.math.min

@Composable
fun PdfPageView(
    bitmap: Bitmap,
    onSelectionComplete: (RectF) -> Unit
) {
    var start by remember { mutableStateOf<Offset?>(null) }
    var end by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { start = it },
                    onDragEnd = {
                        if (start != null && end != null) {
                            onSelectionComplete(
                                RectF(
                                    start!!.x,
                                    start!!.y,
                                    end!!.x,
                                    end!!.y
                                )
                            )
                        }
                        start = null
                        end = null
                    },
                    onDrag = { _, dragAmount ->
                        end = (end ?: start)?.plus(dragAmount)
                    }
                )
            }
    ) {
        Image(bitmap.asImageBitmap(), null)

        if (start != null && end != null) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    color = Color.Blue.copy(alpha = 0.3f),
                    topLeft = start!!,
                    size = Size(end!!.x - start!!.x, end!!.y - start!!.y)
                )
            }
        }
    }
}

fun screenToPdf(
    rect: RectF,
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Float,
    pageHeight: Float
): RectF {
    val scaleX = pageWidth / viewWidth
    val scaleY = pageHeight / viewHeight

    return RectF(
        rect.left * scaleX,
        pageHeight - rect.bottom * scaleY,
        rect.right * scaleX,
        pageHeight - rect.top * scaleY
    )
}


fun findTextInRect(
    document: PDDocument,
    pageIndex: Int,
    targetRect: RectF
): List<PositionTextStripper.TextPositionData> {

    val stripper = PositionTextStripper()
    stripper.startPage = pageIndex + 1
    stripper.endPage = pageIndex + 1
    stripper.getText(document)

    Log.d("Tests", "${stripper.positions}")
    Log.d("Tests", "${targetRect}")

    return stripper.positions.filter { pos ->
        RectF(
            pos.x,
            pos.y,
            pos.x + pos.width,
            pos.y + pos.height
        ).intersect(targetRect)
    }
}

fun applyUnderline(
    document: PDDocument,
    pageIndex: Int,
    textPositions: List<PositionTextStripper.TextPositionData>
) {
    if (textPositions.isEmpty()) return

    val page = document.pages[pageIndex]
    val cropBox = page.cropBox

    val minX = textPositions.minOf { it.x }
    val maxX = textPositions.maxOf { it.x + it.width }

    val underlineHeight = 1.5f

    // 🔥 CRITICAL FIX: convert text Y → page Y
    val baselineY =
        cropBox.height - (textPositions.minOf { it.y } + 1.5f)

    val underline = PDAnnotationTextMarkup(
        PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
    )

    underline.color = PDColor(
        floatArrayOf(0f, 0f, 0f),
        PDDeviceRGB.INSTANCE
    )

    underline.rectangle = PDRectangle(
        minX,
        baselineY,
        maxX - minX,
        underlineHeight
    )

    underline.quadPoints = floatArrayOf(
        minX, baselineY + underlineHeight,
        maxX, baselineY + underlineHeight,
        minX, baselineY,
        maxX, baselineY
    )

    page.annotations.add(underline)
}

fun applyStrikeThrough(
    document: PDDocument,
    pageIndex: Int,
    textPositions: List<PositionTextStripper.TextPositionData>
) {
    if (textPositions.isEmpty()) return

    val page = document.pages[pageIndex]
    val cropBox = page.cropBox
    val pageHeight = cropBox.height

    val minX = textPositions.minOf { it.x }
    val maxX = textPositions.maxOf { it.x + it.width }

    // 🔥 vertical center of text (strike position)
    val centerYTextSpace =
        textPositions.minOf { it.y } +
                (textPositions.maxOf { it.height } / 2f)

    // 🔥 convert text Y → page Y
    val centerYPageSpace =
        pageHeight - centerYTextSpace

    val strikeHeight = 1.5f

    val strike = PDAnnotationTextMarkup(
        PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
    )

    strike.color = PDColor(
        floatArrayOf(0f, 0f, 0f),
        PDDeviceRGB.INSTANCE
    )

    strike.rectangle = PDRectangle(
        minX,
        centerYPageSpace - (strikeHeight / 2f),
        maxX - minX,
        strikeHeight
    )

    strike.quadPoints = floatArrayOf(
        minX, centerYPageSpace + strikeHeight,
        maxX, centerYPageSpace + strikeHeight,
        minX, centerYPageSpace,
        maxX, centerYPageSpace
    )

    page.annotations.add(strike)

    // 🔍 debug
    val p = textPositions.first()
    Log.d(
        "PDF_DEBUG", """
--- STRIKE DEBUG ---
Text         = '${p.text}'
Text Y       = ${p.y}
Text Height  = ${p.height}
Center Y     = $centerYTextSpace
Page Y       = $centerYPageSpace
""".trimIndent()
    )
}





fun normalizeRect(rect: RectF): RectF {
    return RectF(
        min(rect.left, rect.right),
        min(rect.top, rect.bottom),
        max(rect.left, rect.right),
        max(rect.top, rect.bottom)
    )
}

fun screenToPdfRect(
    screenRect: RectF,
    viewWidth: Float,
    viewHeight: Float,
    pageWidth: Float,
    pageHeight: Float
): RectF {

    val r = normalizeRect(screenRect)

    val scaleX = pageWidth / viewWidth
    val scaleY = pageHeight / viewHeight

    // ❗ DO NOT invert Y here
    val left = r.left * scaleX
    val right = r.right * scaleX

    val bottom = r.top * scaleY
    val top = r.bottom * scaleY

    return RectF(
        min(left, right),
        min(bottom, top),
        max(left, right),
        max(bottom, top)
    )
}





