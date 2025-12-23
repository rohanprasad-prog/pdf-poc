#!/bin/bash

# Patch qpdf's CMakeLists.txt to use our libjpeg
# Run this from app/src/main/cpp/ directory

set -e

QPDF_CMAKE="qpdf/libqpdf/CMakeLists.txt"

if [ ! -f "$QPDF_CMAKE" ]; then
    echo "Error: $QPDF_CMAKE not found"
    exit 1
fi

echo "Backing up original CMakeLists.txt..."
cp "$QPDF_CMAKE" "$QPDF_CMAKE.backup"

echo "Patching qpdf CMakeLists.txt..."

# Replace the libjpeg detection section
# Find the section between lines 165-174 and replace it
sed -i.tmp '165,174d' "$QPDF_CMAKE"

# Insert our custom libjpeg configuration at line 165
sed -i.tmp '165i\
    # Custom libjpeg configuration for Android\
    if(DEFINED LIBJPEG_H_PATH AND DEFINED LIBJPEG_LIB_PATH)\
      message(STATUS "Using provided libjpeg: ${LIBJPEG_LIB_PATH}")\
      list(APPEND dep_include_directories ${LIBJPEG_H_PATH})\
      list(APPEND dep_link_libraries ${LIBJPEG_LIB_PATH})\
      set(JPEG_INCLUDE ${LIBJPEG_H_PATH})\
    else()\
      find_path(LIBJPEG_H_PATH jpeglib.h)\
      find_library(LIBJPEG_LIB_PATH jpeg)\
      if(LIBJPEG_H_PATH AND LIBJPEG_LIB_PATH)\
        list(APPEND dep_include_directories ${LIBJPEG_H_PATH})\
        list(APPEND dep_link_libraries ${LIBJPEG_LIB_PATH})\
        set(JPEG_INCLUDE ${LIBJPEG_H_PATH})\
      else()\
        message(SEND_ERROR "libjpeg not found")\
        set(ANYTHING_MISSING 1)\
      endif()\
    endif()
' "$QPDF_CMAKE"

# Clean up temp file
rm -f "$QPDF_CMAKE.tmp"

echo "✓ Successfully patched $QPDF_CMAKE"
echo ""
echo "Original file backed up to: $QPDF_CMAKE.backup"
echo "To restore: cp $QPDF_CMAKE.backup $QPDF_CMAKE"