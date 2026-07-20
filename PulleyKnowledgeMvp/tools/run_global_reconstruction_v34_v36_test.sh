#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/global-reconstruction-v34-v36"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/MultiViewTrackCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/GlobalPoseGraphCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/GlobalSparseCloudCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/CylinderFitCore.java" \
  "$ROOT/tools/GlobalReconstructionV34V36Test.java"
java -cp "$OUT" GlobalReconstructionV34V36Test
