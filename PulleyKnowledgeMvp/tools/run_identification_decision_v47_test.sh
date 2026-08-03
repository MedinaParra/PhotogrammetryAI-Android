#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-identification-v47"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/RevisionedPulleyKnowledgeCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PulleyIdentificationDecisionCore.java" \
  "$ROOT/tools/PulleyIdentificationDecisionV47Test.java"
java -cp "$OUT" PulleyIdentificationDecisionV47Test
