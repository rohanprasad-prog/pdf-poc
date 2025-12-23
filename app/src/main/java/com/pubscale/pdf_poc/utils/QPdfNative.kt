package com.pubscale.pdf_poc.utils

object QPdfNative {
    init {
        System.loadLibrary("qpdf_native")
    }

    external fun compress(input: String, output: String): Boolean
}
