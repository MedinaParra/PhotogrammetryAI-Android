#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_ROOT="$ROOT/PhotogrammetryAI/app/src/main/java"
BUILD_DIR="$ROOT/.pulley-knowledge-v21"

rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

find "$SOURCE_ROOT/cl/ingenieria/photogrammetryai/core/foundation" \
     "$SOURCE_ROOT/cl/ingenieria/photogrammetryai/core/fewview" \
     "$SOURCE_ROOT/cl/ingenieria/photogrammetryai/core/pulleyknowledge" \
     -name '*.java' -print0 \
  | xargs -0 javac -encoding UTF-8 -d "$BUILD_DIR"

javac -encoding UTF-8 \
  -cp "$BUILD_DIR" \
  -d "$BUILD_DIR" \
  "$ROOT/tools/PulleyKnowledgeV21Test.java"

java -cp "$BUILD_DIR" PulleyKnowledgeV21Test
