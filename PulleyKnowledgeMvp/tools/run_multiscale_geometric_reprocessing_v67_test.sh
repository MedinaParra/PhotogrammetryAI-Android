#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/test-v67"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -source 8 -target 8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/AffineRansacCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/MultiScaleOrientedFeatureCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/MultiScaleGeometricDiagnosticsCore.java" \
  "$ROOT/tools/MultiScaleOrientedFeatureCoreV67Test.java"
java -cp "$OUT" cl.skm.pulleyai.MultiScaleOrientedFeatureCoreV67Test
