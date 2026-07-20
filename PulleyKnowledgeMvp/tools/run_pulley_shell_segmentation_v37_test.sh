#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pulley-shell-v37"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PulleyShellRansacCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PulleyMetricScaleCore.java" \
  "$ROOT/tools/PulleyShellSegmentationV37Test.java"
java -cp "$OUT" PulleyShellSegmentationV37Test
