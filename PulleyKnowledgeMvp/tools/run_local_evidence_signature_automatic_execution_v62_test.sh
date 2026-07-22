#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-v62"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/LocalEvidenceSignatureCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RuntimeExecutionEvidenceCore.java" \
  "$ROOT/tools/LocalEvidenceSignatureAutomaticExecutionV62Test.java"
java -cp "$OUT" LocalEvidenceSignatureAutomaticExecutionV62Test
