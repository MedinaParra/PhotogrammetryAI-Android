#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-visual-degeneracy-v51"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PhotogrammetrySafetyGateCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/HomographyModelCompetitionCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/VisualDegradationAggregationCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PhotogrammetrySupplementalMetricsCore.java" \
  "$ROOT/tools/VisualDegeneracyV51Test.java"
java -cp "$OUT" VisualDegeneracyV51Test
