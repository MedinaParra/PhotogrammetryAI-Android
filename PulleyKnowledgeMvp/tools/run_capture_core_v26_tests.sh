#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-v26"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/CoveragePlanner.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImageQualityMath.java" \
  "$ROOT/tools/CaptureCoverageV26Test.java" \
  "$ROOT/tools/ImageQualityMathV26Test.java"
java -cp "$OUT" CaptureCoverageV26Test
java -cp "$OUT" ImageQualityMathV26Test
