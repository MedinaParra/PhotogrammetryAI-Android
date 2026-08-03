#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/imported-seed-geometry-v70"
rm -rf "$OUT"
mkdir -p "$OUT" artifacts
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/FundamentalMatrixCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/EssentialPoseCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/SparseTriangulationCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedSeedGeometryCore.java" \
  "$ROOT/tools/tests/ImportedSeedGeometryCoreTest.java"
set +e
java -cp "$OUT" cl.skm.pulleyai.ImportedSeedGeometryCoreTest \
  2>&1 | tee artifacts/imported-seed-geometry-v70.log
STATUS=${PIPESTATUS[0]}
set -e
if [ "$STATUS" -ne 0 ]; then
  exit "$STATUS"
fi

grep -q 'ImportedSeedGeometryZipAnalyzer.process' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessForegroundService.java"
grep -q 'ImportedSeedGeometryCore.solve' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedSeedGeometryZipAnalyzer.java"
grep -q "applicationId 'cl.skm.pulleyai.lab2'" "$ROOT/app/build.gradle"
grep -q 'seedPointCount' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessForegroundService.java"
echo 'imported seed geometry v70 + persisted cloud state PASS'