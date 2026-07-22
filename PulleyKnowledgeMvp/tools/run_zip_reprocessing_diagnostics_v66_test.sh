#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/zip-reprocessing-v66"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/VisualFeatureCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/AdaptivePairDiagnosticsCore.java" \
  "$ROOT/tools/AdaptivePairDiagnosticsCoreV66Test.java"
java -cp "$OUT" cl.skm.pulleyai.AdaptivePairDiagnosticsCoreV66Test
