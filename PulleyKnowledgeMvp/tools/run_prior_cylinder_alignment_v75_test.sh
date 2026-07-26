#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/prior-cylinder-alignment-v75"
rm -rf "$OUT"
mkdir -p "$OUT" artifacts

javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PointCloudExportCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/PulleyCylinderPriorCore.java" \
  "$ROOT/tools/tests/PulleyCylinderPriorCoreTest.java"
java -cp "$OUT" cl.skm.pulleyai.PulleyCylinderPriorCoreTest \
  2>&1 | tee artifacts/prior-cylinder-alignment-v75.log

CORE="$ROOT/app/src/main/java/cl/skm/pulleyai/PulleyCylinderPriorCore.java"
VIEWER="$ROOT/app/src/main/java/cl/skm/pulleyai/PulleyCylinderViewerActivity.java"
UI="$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessActivity.java"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"

grep -q "versionCode 60" "$ROOT/app/build.gradle"
grep -q "versionName '0.18.0-alpha60'" "$ROOT/app/build.gradle"
grep -q 'FIT_ACCEPTED' "$CORE"
grep -q 'FIT_WEAK' "$CORE"
grep -q 'PRIOR_ONLY' "$CORE"
grep -q 'scaleConsistency' "$CORE"
grep -q 'radialRmsMm' "$CORE"
grep -q 'shellLengthMm' "$CORE"
grep -q 'shellDiameterMm' "$CORE"
grep -q 'PulleyCylinderViewerActivity' "$MANIFEST"
grep -q 'Largo conocido del manto' "$UI"
grep -q 'Diámetro conocido del manto' "$UI"
grep -q 'openCylinderModel' "$UI"
grep -q 'openRawPointCloud' "$UI"
grep -q 'drawCylinder' "$VIEWER"
grep -q 'CILINDRO CELESTE=PRIOR' "$VIEWER"
grep -q 'RESULTADO EXPERIMENTAL' "$VIEWER"
grep -q 'onSaveInstanceState' "$VIEWER"
grep -q 'ACTION_POINTER_UP' "$VIEWER"
if grep -Eq 'GLSurfaceView|android\.opengl|GLES20|EGLConfig|GL10' "$VIEWER"; then
  echo 'ERROR: alpha60 cylinder viewer must remain Canvas-only' >&2
  exit 1
fi

echo 'prior-constrained cylinder alignment v75 + alpha60 viewer PASS'
