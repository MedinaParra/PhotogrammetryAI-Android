#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-runtime-control-v57"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeExecutionControlCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/DeviceDiagnosticsCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/InitialDeviceCampaignCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PhotogrammetrySafetyGateCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/LocalBundleAdjustmentCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeReconstructionDecisionCore.java" \
  "$ROOT/tools/RuntimeControlDeviceDiagnosticsV57Test.java"
java -cp "$OUT" RuntimeControlDeviceDiagnosticsV57Test