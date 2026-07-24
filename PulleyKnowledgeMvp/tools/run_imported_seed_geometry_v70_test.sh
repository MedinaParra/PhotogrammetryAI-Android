#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/imported-seed-geometry-v70"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/FundamentalMatrixCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/EssentialPoseCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/SparseTriangulationCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedSeedGeometryCore.java" \
  "$ROOT/tools/tests/ImportedSeedGeometryCoreTest.java"
java -cp "$OUT" cl.skm.pulleyai.ImportedSeedGeometryCoreTest

grep -q 'ImportedSeedGeometryCore' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessActivity.java"
grep -q "versionName '0.18.0-alpha50'" "$ROOT/app/build.gradle"
echo 'imported seed geometry v70 PASS'
