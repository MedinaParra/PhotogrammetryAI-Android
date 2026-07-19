#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ANDROID_NDK_HOME="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-}}"
ANDROID_ABI="${ANDROID_ABI:-arm64-v8a}"
ANDROID_API_LEVEL="${ANDROID_API_LEVEL:-26}"
BUILD_TYPE="${BUILD_TYPE:-Release}"
OCCT_ROOT="${PGAI_OCCT_ROOT:-$ROOT/third-party/opencascade/${ANDROID_ABI}}"
SOURCE_DIR="$ROOT/PhotogrammetryAI/app/src/main/cpp/cadcore"
BUILD_DIR="$ROOT/.native-build/cadcore-${ANDROID_ABI}"
OUTPUT_DIR="$ROOT/PhotogrammetryAI/app/src/main/jniLibs/${ANDROID_ABI}"

if [[ -z "$ANDROID_NDK_HOME" || ! -f "$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" ]]; then
  echo "ANDROID_NDK_HOME (or ANDROID_NDK_ROOT) must point to a valid Android NDK." >&2
  exit 2
fi

if [[ ! -f "$OCCT_ROOT/include/opencascade/Standard.hxx" ]]; then
  echo "OpenCASCADE Android package not found at: $OCCT_ROOT" >&2
  echo "Run scripts/build_occt_android_for_cadcore.sh first." >&2
  exit 2
fi

mkdir -p "$BUILD_DIR" "$OUTPUT_DIR"

cmake -S "$SOURCE_DIR" -B "$BUILD_DIR" -G Ninja \
  -DCMAKE_TOOLCHAIN_FILE="$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI="$ANDROID_ABI" \
  -DANDROID_PLATFORM="android-${ANDROID_API_LEVEL}" \
  -DANDROID_STL=c++_shared \
  -DCMAKE_BUILD_TYPE="$BUILD_TYPE" \
  -DPGAI_OCCT_ROOT="$OCCT_ROOT" \
  -DPGAI_REQUIRE_OCCT=ON

cmake --build "$BUILD_DIR" --parallel

CADCORE_LIBRARY="$BUILD_DIR/libphotogrammetry_cadcore.so"
if [[ ! -f "$CADCORE_LIBRARY" ]]; then
  echo "Native build completed but libphotogrammetry_cadcore.so was not produced." >&2
  exit 1
fi

cp "$CADCORE_LIBRARY" "$OUTPUT_DIR/"
find "$OCCT_ROOT/lib" -maxdepth 1 -name '*.so' -exec cp '{}' "$OUTPUT_DIR/" ';'

CXX_SHARED="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt"
CXX_LIBRARY="$(find "$CXX_SHARED" -path "*/sysroot/usr/lib/${ANDROID_ABI}/libc++_shared.so" -print -quit || true)"
if [[ -n "$CXX_LIBRARY" ]]; then
  cp "$CXX_LIBRARY" "$OUTPUT_DIR/"
fi

echo "cadcore JNI and OCCT libraries copied to: $OUTPUT_DIR"
