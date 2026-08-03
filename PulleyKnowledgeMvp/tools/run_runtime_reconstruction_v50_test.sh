#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-runtime-reconstruction-v50"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PhotogrammetrySafetyGateCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/LocalBundleAdjustmentCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeReconstructionDecisionCore.java" \
  "$ROOT/tools/RuntimeReconstructionV50Test.java"
java -cp "$OUT" RuntimeReconstructionV50Test
