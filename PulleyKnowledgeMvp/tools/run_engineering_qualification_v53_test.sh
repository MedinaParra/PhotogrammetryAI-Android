#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-engineering-qualification-v53"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/DeviceQualificationCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/EngineeringReleaseGateCore.java" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/QualificationEvidenceManifestCore.java" \
  "$ROOT/tools/EngineeringQualificationV53Test.java"
java -cp "$OUT" EngineeringQualificationV53Test
