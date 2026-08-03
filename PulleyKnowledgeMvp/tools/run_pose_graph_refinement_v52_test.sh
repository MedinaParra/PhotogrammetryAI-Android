#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-pose-graph-v52"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/BoundedPoseGraphRefinementCore.java" \
  "$ROOT/tools/PoseGraphRefinementV52Test.java"
java -cp "$OUT" PoseGraphRefinementV52Test
