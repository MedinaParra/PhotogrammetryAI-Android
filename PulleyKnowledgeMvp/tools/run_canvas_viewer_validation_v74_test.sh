#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
VIEWER="$ROOT/app/src/main/java/cl/skm/pulleyai/PointCloudViewerActivity.java"
GRADLE="$ROOT/app/build.gradle"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
WORKFLOW="$ROOT/../.github/workflows/build-pulley-mvp-apk.yml"

test -f "$VIEWER"
grep -q "applicationId 'cl.skm.pulleyai.lab2'" "$GRADLE"
grep -q 'versionCode 60' "$GRADLE"
grep -q "versionName '0.18.0-alpha60'" "$GRADLE"
grep -q 'SKM Polea AI Lab2 alpha60' "$MANIFEST"

grep -q 'extends View' "$VIEWER"
grep -q 'canvas.drawColor(Color.BLACK)' "$VIEWER"
grep -q 'drawGrid(canvas' "$VIEWER"
grep -q 'drawAxes(canvas' "$VIEWER"
grep -q 'originPaint' "$VIEWER"
grep -q 'drawCircle(point.x, point.y, pointRadius, pointPaint)' "$VIEWER"
grep -q 'visiblePointCount' "$VIEWER"
grep -q 'Canvas recibió puntos pero proyectó 0' "$VIEWER"
grep -q 'BBox original' "$VIEWER"
grep -q 'BBox normalizado' "$VIEWER"
grep -q 'P0 original' "$VIEWER"
grep -q 'render %.2f ms' "$VIEWER"
grep -q 'drawCallCount' "$VIEWER"
grep -q 'onSaveInstanceState' "$VIEWER"
grep -q 'restoreState' "$VIEWER"
grep -q 'STATE_YAW' "$VIEWER"
grep -q 'STATE_PITCH' "$VIEWER"
grep -q 'STATE_ZOOM' "$VIEWER"
grep -q 'STATE_POINT_RADIUS' "$VIEWER"
grep -q 'ACTION_POINTER_UP' "$VIEWER"
grep -q 'postInvalidateOnAnimation' "$VIEWER"
grep -q 'requestApplyInsets' "$VIEWER"
grep -q 'CENTRAR' "$VIEWER"
grep -q 'X ROJO' "$VIEWER"
grep -q 'Y VERDE' "$VIEWER"
grep -q 'Z AZUL' "$VIEWER"
grep -q 'ORIGEN AMARILLO' "$VIEWER"
grep -q 'Double.isFinite(x)' "$VIEWER"

if grep -Eq 'GLSurfaceView|android\.opengl|GLES20|EGLConfig|GL10|shader' "$VIEWER"; then
  echo 'ERROR: alpha60 PointCloudViewerActivity must remain independent from GLSurfaceView/OpenGL/EGL/shaders' >&2
  exit 1
fi

grep -q 'Test Canvas viewer diagnostics and state restoration' "$WORKFLOW"
grep -q 'SKM-Polea-AI-Lab2-CAD-STEP-v0.18.0-alpha60-clean.zip' "$WORKFLOW"

python3 - "$VIEWER" <<'PY'
from pathlib import Path
import sys
source = Path(sys.argv[1]).read_text(encoding="utf-8")
required = {
    "canvas dimensions": "Canvas %d×%d",
    "read count": "puntos leídos %d",
    "visible projection count": "dentro del área %d",
    "original bounds": "BBox original %s",
    "normalized bounds": "BBox normalizado %s",
    "view state": "zoom %.3f · yaw %.1f° · pitch %.1f°",
    "first original point": "P0 original %s",
    "first normalized point": "normalizado %s",
    "first projected point": "proyectado %s",
    "render timing": "render %.2f ms",
    "draw counter": "onDraw %d",
}
missing = [label for label, token in required.items() if token not in source]
if missing:
    raise SystemExit("Missing alpha60 Canvas diagnostics: " + ", ".join(missing))
if source.count("postInvalidateOnAnimation") < 5:
    raise SystemExit("Expected foreground, size and gesture redraw hooks")
print("alpha60 raw Canvas viewer diagnostics/state contract PASS")
PY
