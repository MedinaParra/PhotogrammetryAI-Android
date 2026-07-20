#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-v28"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/CoveragePlanner.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/LandscapeCaptureMath.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/CaptureReadiness.java" \
  "$ROOT/tools/LandscapeCaptureMathV28Test.java" \
  "$ROOT/tools/CaptureReadinessV28Test.java"
java -cp "$OUT" LandscapeCaptureMathV28Test
java -cp "$OUT" CaptureReadinessV28Test
