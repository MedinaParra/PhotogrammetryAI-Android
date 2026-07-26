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
grep -q "versionName '0.18.0-alpha59'" "$ROOT/app/build.gradle"
grep -q 'startForegroundService' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessActivity.java"
grep -q 'PowerManager.PARTIAL_WAKE_LOCK' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessForegroundService.java"
grep -q 'START_REDELIVER_INTENT' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessForegroundService.java"
grep -q 'foregroundServiceType="dataSync"' "$ROOT/app/src/main/AndroidManifest.xml"
VIEWER="$ROOT/app/src/main/java/cl/skm/pulleyai/PointCloudViewerActivity.java"
grep -q 'extends View' "$VIEWER"
grep -q 'canvas.drawColor(Color.BLACK)' "$VIEWER"
grep -q 'canvas.drawCircle(point.x, point.y, pointRadius, pointPaint)' "$VIEWER"
grep -q 'drawAxes(canvas' "$VIEWER"
grep -q 'X ROJO' "$VIEWER"
grep -q 'Y VERDE' "$VIEWER"
grep -q 'Z AZUL' "$VIEWER"
grep -q 'VISOR CANVAS DIAGNÓSTICO' "$VIEWER"
grep -q 'Double.isFinite(x)' "$VIEWER"
if grep -Eq 'GLSurfaceView|android\.opengl|GLES20|EGLConfig|GL10' "$VIEWER"; then
  echo 'ERROR: raw alpha59 viewer must not depend on GLSurfaceView/OpenGL/EGL' >&2
  exit 1
fi
echo 'imported replay completion v73 + alpha59 raw Canvas viewer PASS'
