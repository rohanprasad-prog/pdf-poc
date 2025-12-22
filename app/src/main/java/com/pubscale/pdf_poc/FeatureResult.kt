package com.pubscale.pdf_poc

import android.app.Application
import android.net.Uri
import android.util.Log
import org.opencv.android.OpenCVLoader
import java.io.File

sealed class FeatureResult {
    data class Success(val message: String,val file: File? = null) : FeatureResult()
    data class Error(val error: String) : FeatureResult()
}

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "❌ OpenCV init failed")
        } else {
            Log.d("OpenCV", "✅ OpenCV initialized")
        }
    }


    companion object {
        init {
            try {
                System.loadLibrary("opencv_java4")
                Log.d("OpenCV", "Library loaded")
            } catch (e: UnsatisfiedLinkError) {
                Log.e("OpenCV", "Failed to load OpenCV", e)
            }
        }
    }

}