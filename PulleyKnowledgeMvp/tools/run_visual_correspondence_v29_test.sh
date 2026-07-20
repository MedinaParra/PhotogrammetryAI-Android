#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-v29"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/VisualFeatureCore.java" \
  "$ROOT/tools/VisualFeatureCoreV29Test.java"
java -cp "$OUT" VisualFeatureCoreV29Test
