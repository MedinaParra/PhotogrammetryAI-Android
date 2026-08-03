#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-v64"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PulleyTargetLockCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/BridgeCapturePlanCore.java" \
  "$ROOT/tools/TargetLockBridgeRemediationV64Test.java"
java -cp "$OUT" TargetLockBridgeRemediationV64Test
