#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/skm-device-campaign-v54"
rm -rf "$OUT"
mkdir -p "$OUT"
javac -d "$OUT" \
  "$ROOT/app/src/main/java/cl/skm/pulleyai/InitialDeviceCampaignCore.java" \
  "$ROOT/tools/InitialDeviceCampaignV54Test.java"
java -cp "$OUT" InitialDeviceCampaignV54Test
