#!/usr/bin/env bash
# Builds from local assets using official Android SDK tools; no Gradle or Maven dependencies.
set -euo pipefail
cd "$(dirname "$0")/.."
# This public build never reads signing credentials or generates a key.
# Signing approved artifacts belongs to the separately authorized private workflow.
if [[ "$#" != 1 || "$1" != "--unsigned" ]]; then
  echo 'Usage: bash scripts/build-apk.sh --unsigned (sign separately with the existing approved key)' >&2
  exit 2
fi
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or newer}"
: "${ANDROID_HOME:?Set ANDROID_HOME to your licensed Android SDK directory}"
BT="$ANDROID_HOME/build-tools/35.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-35/android.jar"
for executable in aapt2 d8 zipalign; do
  test -x "$BT/$executable" || { echo "Missing Android build-tools;35.0.0 ($executable)" >&2; exit 1; }
done
test -f "$PLATFORM" || { echo 'Missing platforms;android-35' >&2; exit 1; }
export PATH="$JAVA_HOME/bin:$PATH"
# Some official runtime packages retain jdk.compiler but omit the javac launcher.
if test -x "$JAVA_HOME/bin/javac"; then
  JAVAC=("$JAVA_HOME/bin/javac")
else
  JAVAC=("$JAVA_HOME/bin/java" --module jdk.compiler/com.sun.tools.javac.Main)
fi
"${JAVAC[@]}" -version >/dev/null || { echo 'JDK compiler module required' >&2; exit 1; }
# A failed build must not leave an earlier artifact looking like this run's output.
rm -f output/Terreno-premium-unsigned.apk output/unsigned-manifest.txt output/unsigned-SHA256SUMS.txt
npm test
npm run build
# Rebuild intermediates from scratch to avoid packaging stale classes/dex.
rm -rf build/apk
mkdir -p build/apk/{classes,generated,dex} output
rm -f build/apk/resources.zip build/apk/unsigned.apk build/apk/aligned.apk
"$BT/aapt2" compile --dir android/res -o build/apk/resources.zip
"$BT/aapt2" link -o build/apk/unsigned.apk -I "$PLATFORM" --manifest android/AndroidManifest.xml \
  --java build/apk/generated -A dist --min-sdk-version 28 --target-sdk-version 35 build/apk/resources.zip
find android/java build/apk/generated -name '*.java' -print | sort > build/apk/sources.txt
# Prefer the JDK's Java 8 API signatures when shipped. The lean local runtime
# retains the compiler but lacks ct.sym; source/bytecode targeting still builds,
# with platform API compatibility requiring Android runtime QA.
JAVA_TARGET=(--release 8)
if ! test -f "$JAVA_HOME/lib/ct.sym"; then JAVA_TARGET=(-source 8 -target 8); fi
"${JAVAC[@]}" -encoding UTF-8 "${JAVA_TARGET[@]}" -classpath "$PLATFORM" -d build/apk/classes @build/apk/sources.txt
find build/apk/classes -name '*.class' -print | sort > build/apk/classfiles.txt
"$BT/d8" --release --min-api 28 --lib "$PLATFORM" --output build/apk/dex @build/apk/classfiles.txt
python3 - <<'PY'
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
with ZipFile('build/apk/unsigned.apk','a') as apk:
    for path in sorted(Path('build/apk/dex').glob('*.dex')):
        apk.write(path,path.name,compress_type=ZIP_DEFLATED)
PY
"$BT/zipalign" -f -p 4 build/apk/unsigned.apk build/apk/aligned.apk
"$BT/zipalign" -c -p 4 build/apk/aligned.apk
cp build/apk/aligned.apk output/Terreno-premium-unsigned.apk
"$BT/aapt2" dump badging output/Terreno-premium-unsigned.apk > output/unsigned-manifest.txt
sha256sum output/Terreno-premium-unsigned.apk > output/unsigned-SHA256SUMS.txt
printf '%s\n' 'Unsigned build only: output/Terreno-premium-unsigned.apk. Not an installable signed release.'
