#include <jni.h>
#include <string>
#include <qpdf/QPDF.hh>
#include <qpdf/QPDFWriter.hh>
#include <qpdf/QPDFPageDocumentHelper.hh>
#include <qpdf/QPDFPageObjectHelper.hh>
#include <android/log.h>

#define LOG_TAG "QPDF-JNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// Helper function to throw Java exceptions
void throwJavaException(JNIEnv* env, const char* message) {
    jclass exClass = env->FindClass("java/lang/Exception");
    if (exClass != nullptr) {
        env->ThrowNew(exClass, message);
    }
}

// Maximum compression with available optimization techniques
JNIEXPORT jboolean JNICALL
Java_com_yourpackage_QPdfHelper_compressPdf(
        JNIEnv* env,
        jobject /* this */,
        jstring inputPath,
        jstring outputPath) {

    try {
        const char* input = env->GetStringUTFChars(inputPath, nullptr);
        const char* output = env->GetStringUTFChars(outputPath, nullptr);

        LOGD("Starting PDF compression: %s -> %s", input, output);

        QPDF pdf;
        pdf.processFile(input);

        QPDFWriter writer(pdf, output);

        // ============================================
        // MAXIMUM COMPRESSION SETTINGS
        // ============================================

        // 1. Stream compression (compress all content streams)
        writer.setStreamDataMode(qpdf_s_compress);
        writer.setCompressStreams(true);
        writer.setRecompressFlate(true);

        // 2. Decode and re-encode streams for better compression
        writer.setDecodeLevel(qpdf_dl_all);

        // 3. Use object streams (PDF 1.5+) - groups objects together
        writer.setObjectStreamMode(qpdf_o_generate);

        // 4. Linearization (fast web view) - also helps compression
        writer.setLinearization(true);

        // 5. Remove unreferenced objects
        writer.setPreserveUnreferencedObjects(false);

        // 6. Use newer PDF version for better compression
        writer.setMinimumPDFVersion("1.5");

        writer.write();

        env->ReleaseStringUTFChars(inputPath, input);
        env->ReleaseStringUTFChars(outputPath, output);

        LOGD("Successfully compressed PDF");
        return JNI_TRUE;

    } catch (std::exception& e) {
        LOGE("Error compressing PDF: %s", e.what());
        throwJavaException(env, e.what());
        return JNI_FALSE;
    }
}

// Aggressive compression with metadata removal
JNIEXPORT jboolean JNICALL
Java_com_yourpackage_QPdfHelper_compressPdfAggressive(
        JNIEnv* env,
        jobject /* this */,
        jstring inputPath,
        jstring outputPath,
        jint imageQuality) {

    try {
        const char* input = env->GetStringUTFChars(inputPath, nullptr);
        const char* output = env->GetStringUTFChars(outputPath, nullptr);

        LOGD("Starting aggressive PDF compression: %s -> %s (quality: %d)",
             input, output, imageQuality);

        QPDF pdf;
        pdf.processFile(input);

        // Remove metadata to save space
        pdf.getTrailer().removeKey("/Info");

        // Remove unused objects
//        pdf.removeUnreferencedResources();

        QPDFWriter writer(pdf, output);

        // All maximum compression settings
        writer.setStreamDataMode(qpdf_s_compress);
        writer.setCompressStreams(true);
        writer.setRecompressFlate(true);
        writer.setDecodeLevel(qpdf_dl_all);
        writer.setObjectStreamMode(qpdf_o_generate);
        writer.setLinearization(true);
        writer.setPreserveUnreferencedObjects(false);
        writer.setMinimumPDFVersion("1.5");

        writer.write();

        env->ReleaseStringUTFChars(inputPath, input);
        env->ReleaseStringUTFChars(outputPath, output);

        LOGD("Successfully compressed PDF aggressively");
        return JNI_TRUE;

    } catch (std::exception& e) {
        LOGE("Error compressing PDF aggressively: %s", e.what());
        throwJavaException(env, e.what());
        return JNI_FALSE;
    }
}

// Custom compression with configurable options
JNIEXPORT jboolean JNICALL
Java_com_yourpackage_QPdfHelper_compressPdfCustom(
        JNIEnv* env,
        jobject /* this */,
        jstring inputPath,
        jstring outputPath,
        jboolean removeMetadata,
        jboolean linearize,
        jint compressionLevel) {

    try {
        const char* input = env->GetStringUTFChars(inputPath, nullptr);
        const char* output = env->GetStringUTFChars(outputPath, nullptr);

        LOGD("Starting custom PDF compression: %s -> %s", input, output);

        QPDF pdf;
        pdf.processFile(input);

        // Optionally remove metadata
        if (removeMetadata) {
            pdf.getTrailer().removeKey("/Info");
//            pdf.removeUnreferencedResources();
            LOGD("Removed metadata and unused resources");
        }

        QPDFWriter writer(pdf, output);

        // Standard compression
        writer.setStreamDataMode(qpdf_s_compress);
        writer.setCompressStreams(true);
        writer.setRecompressFlate(true);
        writer.setDecodeLevel(qpdf_dl_all);
        writer.setObjectStreamMode(qpdf_o_generate);
        writer.setPreserveUnreferencedObjects(false);
        writer.setMinimumPDFVersion("1.5");

        // Optional linearization
        if (linearize) {
            writer.setLinearization(true);
            LOGD("Enabled linearization");
        }

        // Note: compressionLevel parameter is ignored as QPDFWriter doesn't expose
        // zlib compression level in this version. It uses default compression.

        writer.write();

        env->ReleaseStringUTFChars(inputPath, input);
        env->ReleaseStringUTFChars(outputPath, output);

        LOGD("Successfully compressed PDF with custom settings");
        return JNI_TRUE;

    } catch (std::exception& e) {
        LOGE("Error compressing PDF: %s", e.what());
        throwJavaException(env, e.what());
        return JNI_FALSE;
    }
}

} // extern "C"