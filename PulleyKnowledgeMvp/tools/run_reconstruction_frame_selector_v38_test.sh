#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/frame-selector-v38"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ReconstructionFrameSelectorCore.java" \
  "$ROOT/tools/ReconstructionFrameSelectorV38Test.java"
java -cp "$OUT" ReconstructionFrameSelectorV38Test
