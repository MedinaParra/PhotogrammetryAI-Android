#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OCCT_SOURCE_DIR="${1:-${OCCT_SOURCE_DIR:-}}"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-26}"
BUILD_TYPE="${BUILD_TYPE:-Release}"

if [[ -z "$OCCT_SOURCE_DIR" || ! -f "$OCCT_SOURCE_DIR/CMakeLists.txt" ]]; then
  echo "Usage: $0 /absolute/path/to/opencascade-sources" >&2
  echo "Or set OCCT_SOURCE_DIR." >&2
  exit 2
fi

if [[ -z "$ANDROID_NDK_HOME" || ! -f "$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" ]]; then
  echo "ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) must point to a valid Android NDK." >&2
  exit 2
fi

for command in cmake ninja; do
  if ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing required command: $command" >&2
    exit 2
  fi
done

BUILD_DIR="$ROOT/.native-build/occt-${ANDROID_ABI}"
INSTALL_DIR="$ROOT/third-party/opencascade/${ANDROID_ABI}"
mkdir -p "$BUILD_DIR" "$INSTALL_DIR"

cmake -S "$OCCT_SOURCE_DIR" -B "$BUILD_DIR" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ANDROID_ABI" \
  -DANDROID_PLATFORM="android-${ANDROID_API_LEVEL}" \
  -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
  -DCMAKE_INSTALL_PREFIX="$INSTALL_DIR" \
  -DBUILD_LIBRARY_TYPE=Shared \
  -DBUILD_MODULE_FoundationClasses=ON \
  -DBUILD_MODULE_ModelingData=ON \
  -DBUILD_MODULE_ModelingAlgorithms=ON \
  -DBUILD_MODULE_DataExchange=ON \
  -DBUILD_MODULE_Visualization=OFF \
  -DBUILD_MODULE_ApplicationFramework=OFF \
  -DBUILD_MODULE_Draw=OFF \
  -DUSE_TBB=OFF \
  -DUSE_FREETYPE=OFF \
  -DUSE_FREEIMAGE=OFF \
  -DUSE_RAPIDJSON=OFF \
  -DUSE_GLES2=OFF

cmake --build "$BUILD_DIR" --parallel
cmake --install "$BUILD_DIR"

if [[ ! -f "$INSTALL_DIR/include/opencascade/Standard.hxx" ]]; then
  echo "OCCT installation is incomplete: Standard.hxx is missing." >&2
  exit 1
fi

required_libraries=(
  TKernel TKMath TKG2d TKG3d TKGeomBase TKBRep TKTopAlgo TKMesh
  TKXSBase TKSTEPBase TKSTEPAttr TKSTEP209 TKSTEP
)
for library in "${required_libraries[@]}"; do
  if [[ ! -f "$INSTALL_DIR/lib/lib${library}.so" ]]; then
    echo "OCCT installation is incomplete: lib${library}.so is missing." >&2
    exit 1
  fi
done

echo "OpenCASCADE Android package ready at: $INSTALL_DIR"
