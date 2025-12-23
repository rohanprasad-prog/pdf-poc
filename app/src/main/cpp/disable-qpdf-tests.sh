#!/bin/bash

# Disable building tests and executables in qpdf
# Run this from app/src/main/cpp/ directory

set -e

echo "Disabling qpdf tests and executables..."

# Patch main CMakeLists.txt
MAIN_CMAKE="qpdf/CMakeLists.txt"
if [ -f "$MAIN_CMAKE" ]; then
    echo "Patching $MAIN_CMAKE..."

    # Comment out add_subdirectory lines for tests and executables
    sed -i.bak 's/^add_subdirectory(qpdf)/# add_subdirectory(qpdf) # Disabled for Android/' "$MAIN_CMAKE"
    sed -i.bak 's/^add_subdirectory(libtests)/# add_subdirectory(libtests) # Disabled for Android/' "$MAIN_CMAKE"
    sed -i.bak 's/^add_subdirectory(examples)/# add_subdirectory(examples) # Disabled for Android/' "$MAIN_CMAKE"
    sed -i.bak 's/^add_subdirectory(zlib-flate)/# add_subdirectory(zlib-flate) # Disabled for Android/' "$MAIN_CMAKE"
    sed -i.bak 's/^add_subdirectory(compare-for-test)/# add_subdirectory(compare-for-test) # Disabled for Android/' "$MAIN_CMAKE"
    sed -i.bak 's/^add_subdirectory(fuzz)/# add_subdirectory(fuzz) # Disabled for Android/' "$MAIN_CMAKE"

    echo "✓ Patched $MAIN_CMAKE"
else
    echo "Error: $MAIN_CMAKE not found"
    exit 1
fi

# Also disable enable_testing() calls
sed -i.bak 's/^enable_testing()/# enable_testing() # Disabled for Android/' "$MAIN_CMAKE"

echo ""
echo "✓ Successfully disabled tests and executables"
echo "Backup saved as: $MAIN_CMAKE.bak"
echo ""
echo "Only libqpdf will be built now"