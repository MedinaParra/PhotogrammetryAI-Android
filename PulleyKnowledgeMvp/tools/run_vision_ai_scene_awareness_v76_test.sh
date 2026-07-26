#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/vision-ai-scene-awareness-v76"
rm -rf "$OUT"
mkdir -p "$OUT" artifacts

javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/VisionTensorContractCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PulleySceneAwarenessCore.java" \
  "$ROOT/tools/tests/PulleyVisionAiCoreTest.java"
java -cp "$OUT" cl.skm.pulleyai.PulleyVisionAiCoreTest \
  2>&1 | tee artifacts/vision-ai-scene-awareness-v76.log

GRADLE="$ROOT/app/build.gradle"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
STORE="$ROOT/app/src/main/java/cl/skm/pulleyai/VisionModelStore.java"
RUNTIME="$ROOT/app/src/main/java/cl/skm/pulleyai/VisionAiRuntime.java"
MANAGER="$ROOT/app/src/main/java/cl/skm/pulleyai/VisionModelManagerActivity.java"
ENTRY="$ROOT/app/src/main/java/cl/skm/pulleyai/VisionEntryActivity.java"

grep -q "versionCode 60" "$GRADLE"
grep -q "versionName '0.18.0-alpha60'" "$GRADLE"
grep -q "play-services-tflite-java:16.5.0" "$GRADLE"
grep -q 'SKM Polea AI Lab2 alpha60' "$MANIFEST"
grep -q 'VisionEntryActivity' "$MANIFEST"
grep -q 'VisionModelManagerActivity' "$MANIFEST"
grep -q 'pulley_yolo11n_seg_int8.tflite' "$STORE"
grep -q 'capture_quality_mobilenetv3_small_int8.tflite' "$STORE"
grep -q 'MessageDigest.getInstance("SHA-256")' "$STORE"
grep -q 'TfLite.initialize(context)' "$RUNTIME"
grep -q 'TfLiteRuntime.FROM_SYSTEM_ONLY' "$RUNTIME"
grep -q 'validateYolo' "$RUNTIME"
grep -q 'validateClassifier' "$RUNTIME"
grep -q 'GPU deshabilitada hasta benchmark físico' "$RUNTIME"
grep -q 'MODELOS IA' "$ENTRY"
grep -q 'IMPORTAR PAQUETE IA VERIFICADO' "$MANAGER"

if find "$ROOT/app/src/main" -type f \( -name '*.tflite' -o -name '*.lite' \) | grep -q .; then
  echo 'ERROR: alpha60 must not bundle untrained or unverified model weights' >&2
  exit 1
fi

echo 'alpha60 YOLO11n-seg + MobileNetV3-Small scene-awareness contract PASS'
