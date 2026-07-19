#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-v32"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/FundamentalMatrixCore.java" \
  "$ROOT/tools/FundamentalMatrixCoreV32Test.java"
java -cp "$OUT" FundamentalMatrixCoreV32Test
