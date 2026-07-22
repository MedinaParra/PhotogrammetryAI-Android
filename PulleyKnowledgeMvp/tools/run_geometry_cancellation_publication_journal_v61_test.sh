#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-geometry-journal-v61"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeExecutionControlCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeCancellationBridge.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/FundamentalMatrixCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/EssentialPoseCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/SparseTriangulationCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimePublicationJournalBridge.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeEvidenceTransactionCore.java" \
  "$ROOT/tools/GeometryCancellationPublicationJournalV61Test.java"
java -cp "$OUT" cl.skm.pulleyai.GeometryCancellationPublicationJournalV61Test
