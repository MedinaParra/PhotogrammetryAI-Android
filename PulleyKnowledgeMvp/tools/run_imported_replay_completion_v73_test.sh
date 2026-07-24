#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/imported-replay-completion-v73"
rm -rf "$OUT"
mkdir -p "$OUT" artifacts
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedReplayCompletionCore.java" \
  "$ROOT/tools/tests/ImportedReplayCompletionCoreTest.java"
java -cp "$OUT" cl.skm.pulleyai.ImportedReplayCompletionCoreTest \
  2>&1 | tee artifacts/imported-replay-completion-v73.log

grep -q 'ImportedReplayCompletionAnalyzer.process' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessActivity.java"
grep -q 'ImportedReplayCompletionCore.evaluate' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedReplayCompletionAnalyzer.java"
grep -q "versionName '0.18.0-alpha53'" "$ROOT/app/build.gradle"
echo 'imported replay completion v73 PASS'
