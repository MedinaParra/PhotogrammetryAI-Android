#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-rotational-ba-v59"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PhotogrammetrySafetyGateCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/LocalBundleAdjustmentCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RotationalBundleAdjustmentCore.java" \
  "$ROOT/tools/RotationalBundleAdjustmentV59Test.java"
java -cp "$OUT" RotationalBundleAdjustmentV59Test