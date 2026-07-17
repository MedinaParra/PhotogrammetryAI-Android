#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
export ANDROID_HOME="$ROOT/sdk"
export ANDROID_SDK_ROOT="$ROOT/sdk"
export PATH="$ROOT/sdk/platform-tools:$ROOT/sdk/cmdline-tools/latest/bin:$PATH"
cd "$ROOT/PhotogrammetryAI"
./gradlew assembleDebug
