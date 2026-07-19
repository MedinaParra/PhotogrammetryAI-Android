#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-v31"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/AffineRansacCore.java" \
  "$ROOT/tools/AffineRansacCoreV31Test.java"
java -cp "$OUT" AffineRansacCoreV31Test
