#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/build/pure-java-v62"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -encoding UTF-8 -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/CoveragePlanner.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/CaptureReadiness.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/GuidedCaptureAdmissionCore.java" \
  "$ROOT/tools/GuidedCaptureAdmissionV62Test.java"
java -cp "$OUT" GuidedCaptureAdmissionV62Test
