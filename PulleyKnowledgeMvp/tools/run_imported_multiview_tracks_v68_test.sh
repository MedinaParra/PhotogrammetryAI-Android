#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/test-v68"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -source 8 -target 8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedTrackAssemblerCore.java" \
  "$ROOT/tools/ImportedTrackAssemblerCoreV68Test.java"
java -cp "$OUT" cl.skm.pulleyai.ImportedTrackAssemblerCoreV68Test
