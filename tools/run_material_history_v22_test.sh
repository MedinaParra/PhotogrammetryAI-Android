#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_ROOT="$ROOT/PhotogrammetryAI/app/src/main/java"
BUILD_DIR="$ROOT/.material-history-v22"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

find "$SOURCE_ROOT/cl/ingenieria/photogrammetryai/core/materialhistory" \
  -name '*.java' -print0 \
  | xargs -0 javac -encoding UTF-8 -d "$BUILD_DIR"

javac -encoding UTF-8 \
  -cp "$BUILD_DIR" \
  -d "$BUILD_DIR" \
  "$ROOT/tools/MaterialHistoryV22Test.java"

java -cp "$BUILD_DIR" MaterialHistoryV22Test
