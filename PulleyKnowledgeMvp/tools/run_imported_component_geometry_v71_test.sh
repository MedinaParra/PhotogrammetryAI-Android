#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/imported-component-geometry-v71"
rm -rf "$OUT"
mkdir -p "$OUT" artifacts
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/FundamentalMatrixCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/EssentialPoseCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/SparseTriangulationCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedSeedGeometryCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedTrackAssemblerCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedComponentGeometryCore.java" \
  "$ROOT/tools/tests/ImportedComponentGeometryCoreTest.java"
java -cp "$OUT" cl.skm.pulleyai.ImportedComponentGeometryCoreTest \
  2>&1 | tee artifacts/imported-component-geometry-v71.log

grep -q 'ImportedComponentGeometryAnalyzer.process' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessForegroundService.java"
grep -q 'ImportedComponentGeometryCore.analyze' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedComponentGeometryAnalyzer.java"
grep -q "applicationId 'cl.skm.pulleyai.lab2'" "$ROOT/app/build.gradle"
echo 'imported component geometry v71 + foreground ownership PASS'