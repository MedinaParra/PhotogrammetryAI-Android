#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="$ROOT/sdk"
CACHE="$ROOT/downloads"
mkdir -p "$SDK/cmdline-tools" "$CACHE"
TOOLS=commandlinetools-linux-14742923_latest.zip
TOOLS_URL="https://dl.google.com/android/repository/$TOOLS"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  curl -L --fail --retry 3 "$TOOLS_URL" -o "$CACHE/$TOOLS"
  rm -rf "$SDK/cmdline-tools/latest" "$CACHE/cmdline-tools"
  unzip -q "$CACHE/$TOOLS" -d "$CACHE"
  mv "$CACHE/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
export PATH="$SDK/cmdline-tools/latest/bin:$SDK/platform-tools:$PATH"
yes | sdkmanager --licenses >/dev/null || true
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
GRADLE_VERSION=8.9
if [ ! -x "$ROOT/gradle-$GRADLE_VERSION/bin/gradle" ]; then
  curl -L --fail --retry 3 "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" -o "$CACHE/gradle.zip"
  unzip -q "$CACHE/gradle.zip" -d "$ROOT"
fi
cd "$ROOT/PhotogrammetryAI"
"$ROOT/gradle-$GRADLE_VERSION/bin/gradle" wrapper --gradle-version "$GRADLE_VERSION"
echo "sdk.dir=$SDK" > local.properties
./gradlew assembleDebug
printf '\nAPK: %s\n' "$ROOT/PhotogrammetryAI/app/build/outputs/apk/debug/app-debug.apk"
