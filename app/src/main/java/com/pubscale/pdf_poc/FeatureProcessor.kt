package com.pubscale.pdf_poc

import android.content.Context
import android.net.Uri

interface FeatureProcessor {
    suspend fun process(
        context: Context,
        inputUris: List<Uri>,
        text : String? = null
    ): FeatureResult
}