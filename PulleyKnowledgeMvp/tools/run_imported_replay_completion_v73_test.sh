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
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessForegroundService.java"
grep -q 'ImportedReplayCompletionCore.evaluate' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ImportedReplayCompletionAnalyzer.java"
grep -q "versionName '0.18.0-alpha56'" "$ROOT/app/build.gradle"
grep -q 'startForegroundService' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessActivity.java"
grep -q 'PowerManager.PARTIAL_WAKE_LOCK' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessForegroundService.java"
grep -q 'START_REDELIVER_INTENT' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessForegroundService.java"
grep -q 'foregroundServiceType="dataSync"' "$ROOT/app/src/main/AndroidManifest.xml"
VIEWER="$ROOT/app/src/main/java/cl/skm/pulleyai/PointCloudViewerActivity.java"
grep -q 'GLES20.GL_LINES' "$VIEWER"
grep -q 'GLES20.GL_POINTS' "$VIEWER"
grep -q 'glClearColor(0f, 0f, 0f, 1f)' "$VIEWER"
grep -q 'drawPoints(points, pointCount, pointSize, 1f, 1f, 1f, 1f)' "$VIEWER"
grep -q 'X ROJO' "$VIEWER"
grep -q 'Y VERDE' "$VIEWER"
grep -q 'Z AZUL' "$VIEWER"
grep -q 'Double.isFinite(x)' "$VIEWER"
echo 'imported replay completion v73 + alpha56 high-contrast point cloud axes PASS'
