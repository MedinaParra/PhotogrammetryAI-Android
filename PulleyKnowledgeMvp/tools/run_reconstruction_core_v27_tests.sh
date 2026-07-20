#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-v27"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/CoveragePlanner.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ScaleEvidenceResolver.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ViewBaselineSelector.java" \
  "$ROOT/tools/ScaleEvidenceV27Test.java" \
  "$ROOT/tools/ViewBaselineV27Test.java"
java -cp "$OUT" ScaleEvidenceV27Test
java -cp "$OUT" ViewBaselineV27Test
