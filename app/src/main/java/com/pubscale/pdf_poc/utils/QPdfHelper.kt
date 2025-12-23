package com.yourpackage

import java.io.File

/**
 * QPdfHelper - Kotlin wrapper for QPDF native library (Enhanced Compression)
 *
 * IMPORTANT: Change "com.yourpackage" to your actual package name
 */
object QPdfHelper {

    init {
        System.loadLibrary("qpdf-jni")
    }

    // ============================================
    // Native Methods
    // ============================================

    /**
     * Standard compression - Good balance of speed and size reduction
     */
    @Throws(Exception::class)
    external fun compressPdf(inputPath: String, outputPath: String): Boolean

    /**
     * Aggressive compression - Maximum size reduction, slower processing
     * @param imageQuality Quality for image compression (0-100, lower = smaller file)
     */
    @Throws(Exception::class)
    external fun compressPdfAggressive(
        inputPath: String,
        outputPath: String,
        imageQuality: Int
    ): Boolean

    /**
     * Custom compression with configurable options
     * @param removeMetadata Remove document metadata to save space
     * @param linearize Optimize for web viewing (fast display)
     * @param compressionLevel Zlib compression level (0-9, higher = better compression but slower)
     */
    @Throws(Exception::class)
    external fun compressPdfCustom(
        inputPath: String,
        outputPath: String,
        removeMetadata: Boolean,
        linearize: Boolean,
        compressionLevel: Int
    ): Boolean

    // ============================================
    // Kotlin Helper Functions (File-based)
    // ============================================

    /**
     * Standard compression using File objects
     */
    @Throws(Exception::class)
    fun compressPdf(inputFile: File, outputFile: File): Boolean {
        validateInput(inputFile)
        return compressPdf(inputFile.absolutePath, outputFile.absolutePath)
    }

    /**
     * Aggressive compression using File objects
     * @param imageQuality 0-100, default is 75. Lower = smaller file but lower quality
     */
    @Throws(Exception::class)
    fun compressPdfAggressive(
        inputFile: File,
        outputFile: File,
        imageQuality: Int = 75
    ): Boolean {
        validateInput(inputFile)
        require(imageQuality in 0..100) { "Image quality must be between 0 and 100" }
        return compressPdfAggressive(
            inputFile.absolutePath,
            outputFile.absolutePath,
            imageQuality
        )
    }

    /**
     * Custom compression using File objects
     */
    @Throws(Exception::class)
    fun compressPdfCustom(
        inputFile: File,
        outputFile: File,
        removeMetadata: Boolean = true,
        linearize: Boolean = true,
        compressionLevel: Int = 9
    ): Boolean {
        validateInput(inputFile)
        require(compressionLevel in 0..9) { "Compression level must be between 0 and 9" }
        return compressPdfCustom(
            inputFile.absolutePath,
            outputFile.absolutePath,
            removeMetadata,
            linearize,
            compressionLevel
        )
    }

    // ============================================
    // Compression with Statistics
    // ============================================

    /**
     * Standard compression with statistics
     */
    @Throws(Exception::class)
    fun compressPdfWithStats(inputFile: File, outputFile: File): CompressionResult {
        return compressWithStats(inputFile, outputFile, CompressionMode.STANDARD)
    }

    /**
     * Aggressive compression with statistics
     */
    @Throws(Exception::class)
    fun compressPdfAggressiveWithStats(
        inputFile: File,
        outputFile: File,
        imageQuality: Int = 75
    ): CompressionResult {
        return compressWithStats(inputFile, outputFile, CompressionMode.AGGRESSIVE, imageQuality)
    }

    /**
     * Custom compression with statistics
     */
    @Throws(Exception::class)
    fun compressPdfCustomWithStats(
        inputFile: File,
        outputFile: File,
        removeMetadata: Boolean = true,
        linearize: Boolean = true,
        compressionLevel: Int = 9
    ): CompressionResult {
        validateInput(inputFile)

        val originalSize = inputFile.length()
        val success = compressPdfCustom(
            inputFile, outputFile, removeMetadata, linearize, compressionLevel
        )

        return createCompressionResult(inputFile, outputFile, success, originalSize)
    }

    // ============================================
    // Private Helper Methods
    // ============================================

    private fun validateInput(inputFile: File) {
        if (!inputFile.exists()) {
            throw Exception("Input file does not exist: ${inputFile.absolutePath}")
        }
    }

    private fun compressWithStats(
        inputFile: File,
        outputFile: File,
        mode: CompressionMode,
        imageQuality: Int = 75
    ): CompressionResult {
        validateInput(inputFile)

        val originalSize = inputFile.length()
        val success = when (mode) {
            CompressionMode.STANDARD -> compressPdf(inputFile, outputFile)
            CompressionMode.AGGRESSIVE -> compressPdfAggressive(inputFile, outputFile, imageQuality)
        }

        return createCompressionResult(inputFile, outputFile, success, originalSize)
    }

    private fun createCompressionResult(
        inputFile: File,
        outputFile: File,
        success: Boolean,
        originalSize: Long
    ): CompressionResult {
        if (!success) {
            throw Exception("Compression failed")
        }

        val compressedSize = outputFile.length()
        val savedBytes = originalSize - compressedSize
        val compressionRatio = if (originalSize > 0) {
            (compressedSize.toDouble() / originalSize.toDouble() * 100).toInt()
        } else {
            100
        }

        return CompressionResult(
            originalSize = originalSize,
            compressedSize = compressedSize,
            savedBytes = savedBytes,
            compressionRatio = compressionRatio,
            success = true
        )
    }

    // ============================================
    // Data Classes and Enums
    // ============================================

    private enum class CompressionMode {
        STANDARD,
        AGGRESSIVE
    }

    /**
     * Compression result with detailed statistics
     */
    data class CompressionResult(
        val originalSize: Long,
        val compressedSize: Long,
        val savedBytes: Long,
        val compressionRatio: Int,
        val success: Boolean
    ) {
        val savedPercentage: Int
            get() = 100 - compressionRatio

        val isWorthwhile: Boolean
            get() = savedPercentage >= 5

        fun getOriginalSizeMB(): String = formatMB(originalSize)
        fun getCompressedSizeMB(): String = formatMB(compressedSize)
        fun getSavedMB(): String = formatMB(savedBytes)

        fun getOriginalSizeKB(): String = formatKB(originalSize)
        fun getCompressedSizeKB(): String = formatKB(compressedSize)
        fun getSavedKB(): String = formatKB(savedBytes)

        private fun formatMB(bytes: Long): String {
            return "%.2f MB".format(bytes / 1024.0 / 1024.0)
        }

        private fun formatKB(bytes: Long): String {
            return "%.2f KB".format(bytes / 1024.0)
        }

        fun getHumanReadableSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "%.2f KB".format(bytes / 1024.0)
                bytes < 1024 * 1024 * 1024 -> "%.2f MB".format(bytes / 1024.0 / 1024.0)
                else -> "%.2f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
            }
        }

        override fun toString(): String {
            return """
                Compression Result:
                Original: ${getHumanReadableSize(originalSize)}
                Compressed: ${getHumanReadableSize(compressedSize)}
                Saved: ${getHumanReadableSize(savedBytes)} ($savedPercentage%)
                Worthwhile: ${if (isWorthwhile) "Yes" else "No"}
            """.trimIndent()
        }
    }
}