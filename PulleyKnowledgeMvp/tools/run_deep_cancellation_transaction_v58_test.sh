#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-deep-cancellation-v58"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeExecutionControlCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeCancellationBridge.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/VisualFeatureCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeEvidenceTransactionCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/CampaignEvidenceManifestCore.java" \
  "$ROOT/tools/DeepCancellationTransactionalEvidenceV58Test.java"
java -cp "$OUT" DeepCancellationTransactionalEvidenceV58Test