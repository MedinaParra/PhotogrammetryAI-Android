#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-v30"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ViewGraphCore.java" \
  "$ROOT/tools/ViewGraphCoreV30Test.java"
java -cp "$OUT" ViewGraphCoreV30Test
