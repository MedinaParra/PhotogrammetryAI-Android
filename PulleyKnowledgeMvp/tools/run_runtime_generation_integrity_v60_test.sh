#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-runtime-generation-integrity-v60"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeEvidenceTransactionCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeGenerationIntegrityCore.java" \
  "$ROOT/tools/RuntimeGenerationIntegrityV60Test.java"
java -cp "$OUT" RuntimeGenerationIntegrityV60Test