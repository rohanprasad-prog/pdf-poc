#!/bin/bash

# Script to build libjpeg-turbo for Android
# Run this from app/src/main/cpp/ directory

set -e

ANDROID_NDK=${ANDROID_NDK_HOME}
ANDROID_API=24

if [ -z "$ANDROID_NDK" ]; then
    echo "Error: ANDROID_NDK_HOME not set"
    echo "Example: export ANDROID_NDK_HOME=/path/to/Android/Sdk/ndk/26.1.10909125"
    exit 1
fi

# Get absolute path of current directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
echo "Working directory: $SCRIPT_DIR"

# Check if libjpeg-turbo-main exists
if [ ! -d "$SCRIPT_DIR/libjpeg-turbo-main" ]; then
    echo "Error: libjpeg-turbo-main directory not found"
    echo "Please download and extract libjpeg-turbo first"
    exit 1
fi

# Check if CMakeLists.txt exists
if [ ! -f "$SCRIPT_DIR/libjpeg-turbo-main/CMakeLists.txt" ]; then
    echo "Error: CMakeLists.txt not found in libjpeg-turbo-main/"
    exit 1
fi

# Create output directory
OUTPUT_DIR="$SCRIPT_DIR/libjpeg-turbo"
mkdir -p "$OUTPUT_DIR"
echo "Output directory: $OUTPUT_DIR"

cd "$SCRIPT_DIR/libjpeg-turbo-main"

# Build for each ABI
for ABI in arm64-v8a armeabi-v7a x86 x86_64; do
    echo ""
    echo "========================================="
    echo "Building libjpeg-turbo for $ABI..."
    echo "========================================="

    BUILD_DIR="$SCRIPT_DIR/libjpeg-turbo-main/build-$ABI"
    INSTALL_DIR="$OUTPUT_DIR/$ABI"

    echo "Build dir: $BUILD_DIR"
    echo "Install dir: $INSTALL_DIR"

    # Clean and create build directory
    rm -rf "$BUILD_DIR"
    mkdir -p "$BUILD_DIR"
    mkdir -p "$INSTALL_DIR"

    cd "$BUILD_DIR"

    cmake "$SCRIPT_DIR/libjpeg-turbo-main" \
        -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK/build/cmake/android.toolchain.cmake" \
        -DANDROID_ABI=$ABI \
        -DANDROID_PLATFORM=android-$ANDROID_API \
        -DCMAKE_BUILD_TYPE=Release \
        -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR" \
        -DENABLE_SHARED=OFF \
        -DENABLE_STATIC=ON \
        -DWITH_TURBOJPEG=OFF

    cmake --build . --config Release -j$(nproc 2>/dev/null || echo 4)
    cmake --install .

    # Verify files were created
    if [ -f "$INSTALL_DIR/lib/libjpeg.a" ]; then
        echo "✓ Successfully built and installed $ABI"
        ls -lh "$INSTALL_DIR/lib/libjpeg.a"
    else
        echo "✗ Error: libjpeg.a not found for $ABI"
        echo "Expected at: $INSTALL_DIR/lib/libjpeg.a"
    fi
done

cd "$SCRIPT_DIR"

echo ""
echo "========================================="
echo "✓ Build complete!"
echo "========================================="
echo ""
echo "Checking installation..."
for ABI in arm64-v8a armeabi-v7a x86 x86_64; do
    if [ -f "$OUTPUT_DIR/$ABI/lib/libjpeg.a" ]; then
        SIZE=$(du -h "$OUTPUT_DIR/$ABI/lib/libjpeg.a" | cut -f1)
        echo "✓ $ABI: libjpeg.a ($SIZE)"
    else
        echo "✗ $ABI: NOT FOUND"
    fi
done

echo ""
echo "Directory structure:"
if [ -d "$OUTPUT_DIR" ]; then
    ls -la "$OUTPUT_DIR"
else
    echo "Error: Output directory not created!"
fi

echo ""
echo "You can now build your Android project"