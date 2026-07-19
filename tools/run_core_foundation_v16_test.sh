#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_ROOT="$ROOT/PhotogrammetryAI/app/src/main/java"
BUILD_DIR="$ROOT/.core-foundation-v16"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

find "$SOURCE_ROOT/cl/ingenieria/photogrammetryai/core/foundation" \
  -name '*.java' -print0 \
  | xargs -0 javac -encoding UTF-8 -d "$BUILD_DIR"

javac -encoding UTF-8 \
  -cp "$BUILD_DIR" \
  -d "$BUILD_DIR" \
  "$ROOT/tools/CoreFoundationV16Test.java"

java -cp "$BUILD_DIR" CoreFoundationV16Test
