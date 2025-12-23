cmake_minimum_required(VERSION 3.18.1)
project("qpdf-android")

# Set C++ standard
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Find required libraries from Android NDK
find_library(log-lib log)
find_library(z-lib z)

# ============================================
# Configure libjpeg-turbo BEFORE adding qpdf
# ============================================
set(JPEG_TURBO_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo/${ANDROID_ABI})

# Verify libjpeg exists
if(NOT EXISTS ${JPEG_TURBO_DIR}/lib/libjpeg.a)
    message(FATAL_ERROR "libjpeg.a not found at: ${JPEG_TURBO_DIR}/lib/libjpeg.a
    Please run build-libjpeg.sh first from app/src/main/cpp/")
endif()

message(STATUS "Found libjpeg at: ${JPEG_TURBO_DIR}")

# Add libjpeg directory to CMAKE search paths
list(APPEND CMAKE_PREFIX_PATH ${JPEG_TURBO_DIR})
list(APPEND CMAKE_INCLUDE_PATH ${JPEG_TURBO_DIR}/include)
list(APPEND CMAKE_LIBRARY_PATH ${JPEG_TURBO_DIR}/lib)

# Set specific hints for find_path and find_library
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE BOTH)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY BOTH)

# Explicitly set the variables that qpdf's find_path/find_library will find
set(LIBJPEG_H_PATH ${JPEG_TURBO_DIR}/include CACHE PATH "JPEG include path" FORCE)
set(LIBJPEG_LIB_PATH ${JPEG_TURBO_DIR}/lib/libjpeg.a CACHE FILEPATH "JPEG library path" FORCE)

# Also set standard CMake JPEG variables as backup
set(JPEG_FOUND TRUE CACHE BOOL "JPEG library found" FORCE)
set(JPEG_LIBRARIES ${JPEG_TURBO_DIR}/lib/libjpeg.a CACHE FILEPATH "JPEG library" FORCE)
set(JPEG_INCLUDE_DIRS ${JPEG_TURBO_DIR}/include CACHE PATH "JPEG include directory" FORCE)
set(JPEG_INCLUDE_DIR ${JPEG_TURBO_DIR}/include CACHE PATH "JPEG include directory" FORCE)
set(JPEG_LIBRARY ${JPEG_TURBO_DIR}/lib/libjpeg.a CACHE FILEPATH "JPEG library" FORCE)

# ============================================
# Configure qpdf build options for Android
# ============================================

# CRITICAL: Disable building tests, examples, and command-line tools
set(BUILD_TESTING OFF CACHE BOOL "Disable tests" FORCE)
set(ENABLE_TESTING OFF CACHE BOOL "Disable testing" FORCE)

set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
set(BUILD_STATIC_LIBS OFF CACHE BOOL "" FORCE)
set(BUILD_DOC OFF CACHE BOOL "" FORCE)
set(INSTALL_MANUAL OFF CACHE BOOL "" FORCE)
set(INSTALL_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ENABLE_QTC OFF CACHE BOOL "" FORCE)
set(WERROR OFF CACHE BOOL "" FORCE)
set(MAINTAINER_MODE OFF CACHE BOOL "" FORCE)
set(GENERATE_AUTO_JOB OFF CACHE BOOL "" FORCE)

# Disable features not needed for Android
set(INSTALL_PKGCONFIG OFF CACHE BOOL "" FORCE)
set(INSTALL_CMAKE_PACKAGE OFF CACHE BOOL "" FORCE)

# Disable building command-line tools and other executables
# We only need the library (libqpdf)
set(BUILD_CLI OFF CACHE BOOL "Don't build CLI tools" FORCE)

# Add qpdf subdirectory - it will only build the library now
add_subdirectory(qpdf EXCLUDE_FROM_ALL)

# ============================================
# Your JNI library
# ============================================
add_library(qpdf-jni SHARED
        qpdf_jni.cpp
)

# Include qpdf headers
target_include_directories(qpdf-jni PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/qpdf/include
        ${CMAKE_CURRENT_SOURCE_DIR}/qpdf/libqpdf
)

# Link against libqpdf (the LIBRARY), not qpdf (the EXECUTABLE)
target_link_libraries(qpdf-jni
        libqpdf
        ${log-lib}
        ${z-lib}
)

# Set output directory and 16KB page alignment
set_target_properties(qpdf-jni PROPERTIES
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_LIBRARY_OUTPUT_DIRECTORY}"
        LINK_FLAGS "-Wl,-z,max-page-size=16384"
)

# Also set 16KB alignment for libqpdf if needed
if(TARGET libqpdf)
    set_target_properties(libqpdf PROPERTIES
            LINK_FLAGS "-Wl,-z,max-page-size=16384"
    )
endif()