#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT="$ROOT/build/capture-zip-preflight-v69"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/CaptureZipFrameResolverCore.java" \
  "$ROOT/tools/tests/CaptureZipFrameResolverCoreTest.java"
java -cp "$OUT" cl.skm.pulleyai.CaptureZipFrameResolverCoreTest

grep -q 'CaptureZipFrameResolverCore' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessActivity.java"
grep -q 'ALPHA49' \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/ZipReprocessActivity.java"
grep -q "applicationId 'cl.skm.pulleyai.lab2'" "$ROOT/app/build.gradle"
grep -q "versionName '0.18.0-alpha49'" "$ROOT/app/build.gradle"
echo 'capture ZIP preflight v69 PASS'
