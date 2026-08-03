#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-conditioned-focal-v60"
ARTIFACTS="$(cd "$ROOT/.." && pwd)/artifacts"
LOG="$ARTIFACTS/conditioned-focal-test.log"
rm -rf "$OUT"
mkdir -p "$OUT" "$ARTIFACTS"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PhotogrammetrySafetyGateCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/LocalBundleAdjustmentCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RotationalBundleAdjustmentCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ConditionedFocalBundleAdjustmentCore.java" \
  "$ROOT/tools/ConditionedFocalBundleAdjustmentV60Test.java"
java -cp "$OUT" ConditionedFocalBundleAdjustmentV60Test 2>&1 | tee "$LOG"