#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-v34"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/FundamentalMatrixCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/EssentialPoseCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/SparseTriangulationCore.java" \
  "$ROOT/tools/SparseTriangulationCoreV34Test.java"
java -cp "$OUT" SparseTriangulationCoreV34Test
