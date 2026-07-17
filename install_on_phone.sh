#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
ADB="$ROOT/sdk/platform-tools/adb"
APK="$ROOT/PhotogrammetryAI/app/build/outputs/apk/debug/app-debug.apk"
"$ADB" devices
"$ADB" install -r "$APK"
