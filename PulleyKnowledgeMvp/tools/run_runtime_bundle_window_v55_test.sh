#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-runtime-ba-v55"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PhotogrammetrySafetyGateCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/LocalBundleAdjustmentCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeBundleWindowCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeBundleWindowSerializer.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeTelemetryCore.java" \
  "$ROOT/tools/RuntimeBundleWindowV55Test.java"
java -cp "$OUT" RuntimeBundleWindowV55Test
