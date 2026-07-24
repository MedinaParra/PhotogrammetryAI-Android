#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/imported-bridge-evidence-v72"
rm -rf "$OUT"
mkdir -p "$OUT" artifacts
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/FundamentalMatrixCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/EssentialPoseCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/SparseTriangulationCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedSeedGeometryCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedTrackAssemblerCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedComponentGeometryCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedBridgeEvidenceCore.java" \
  "$ROOT/tools/tests/ImportedBridgeEvidenceCoreTest.java"
java -cp "$OUT" cl.skm.pulleyai.ImportedBridgeEvidenceCoreTest \
  2>&1 | tee artifacts/imported-bridge-evidence-v72.log

grep -q 'ImportedBridgeEvidenceAnalyzer.process' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessActivity.java"
grep -q 'ImportedBridgeEvidenceCore.analyze' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedBridgeEvidenceAnalyzer.java"
grep -q "versionName '0.18.0-alpha52'" "$ROOT/app/build.gradle"
echo 'imported bridge evidence v72 PASS'
