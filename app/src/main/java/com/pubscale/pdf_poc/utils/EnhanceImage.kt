package com.pubscale.pdf_poc.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import com.pubscale.pdf_poc.FeatureProcessor
import com.pubscale.pdf_poc.FeatureResult
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream

class EnhanceImage : FeatureProcessor {

    @RequiresApi(Build.VERSION_CODES.P)
    override suspend fun process(
        context: Context,
        inputUris: List<Uri>,
        text: String?
    ): FeatureResult {

        val source = ImageDecoder.createSource(
            context.contentResolver,
            inputUris.first()
        )

        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = false
        }

        val res = enhanceDocumentClean(bitmap)

        return FeatureResult.Success(
            message = "Image enhanced successfully",
            file = bitmapToImageFile(context, res, "enhanced.png")
        )

    }

    fun bitmapToImageFile(
        context: Context,
        bitmap: Bitmap,
        fileName: String,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        quality: Int = 100
    ): File {
        val file = File(context.filesDir, fileName)

        FileOutputStream(file).use { out ->
            bitmap.compress(format, quality, out)
            out.flush()
        }

        return file
    }

    fun enhanceDocumentClean(bitmap: Bitmap): Bitmap {
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        // 1️⃣ Remove background using morphological opening
        val kernelBg = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(25.0, 25.0) // VERY important (20–40)
        )
        val bg = Mat()
        Imgproc.morphologyEx(gray, bg, Imgproc.MORPH_OPEN, kernelBg)

        // 2️⃣ Normalize image (remove shadows)
        val normalized = Mat()
        Core.subtract(gray, bg, normalized)
        Core.normalize(normalized, normalized, 0.0, 255.0, Core.NORM_MINMAX)

        // 3️⃣ Increase contrast
        Imgproc.GaussianBlur(normalized, normalized, Size(3.0, 3.0), 0.0)

        // 4️⃣ Adaptive Threshold (gentler params)
        val bw = Mat()
        Imgproc.adaptiveThreshold(
            normalized,
            bw,
            255.0,
            Imgproc.ADAPTIVE_THRESH_MEAN_C,
            Imgproc.THRESH_BINARY,
            21,   // larger block = smoother
            10.0  // higher C = less noise
        )

        // 5️⃣ Remove small noise dots
        val kernelClean = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(2.0, 2.0)
        )
        Imgproc.morphologyEx(bw, bw, Imgproc.MORPH_OPEN, kernelClean)

        // 6️⃣ Optional: thin lines cleanup
        Imgproc.medianBlur(bw, bw, 3)

        val result = Bitmap.createBitmap(bw.cols(), bw.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(bw, result)

        src.release()
        gray.release()
        bg.release()
        normalized.release()
        bw.release()

        return result
    }



    fun enhanceDocument(bitmap: Bitmap): Bitmap {
        // Convert Bitmap → Mat
        val src = Mat()
        Utils.bitmapToMat(bitmap, src)

        // 1. Convert to grayscale
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)

        // 2. Remove noise
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        // 3. Adaptive Threshold (MOST IMPORTANT)
        val bw = Mat()
        Imgproc.adaptiveThreshold(
            gray,
            bw,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            11,   // block size (try 11–21)
            2.0   // constant subtracted (try 2–10)
        )

        // Convert Mat → Bitmap
        val result = Bitmap.createBitmap(
            bw.cols(),
            bw.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(bw, result)

        // Cleanup
        src.release()
        gray.release()
        bw.release()

        return result
    }

}