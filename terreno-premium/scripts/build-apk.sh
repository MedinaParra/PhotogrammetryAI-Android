#!/usr/bin/env bash
# Builds from local assets using official Android SDK tools; no Gradle or Maven dependencies.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or newer}"
: "${ANDROID_HOME:?Set ANDROID_HOME to your licensed Android SDK directory}"
BT="$ANDROID_HOME/build-tools/35.0.0"
PLATFORM="$ANDROID_HOME/platforms/android-35/android.jar"
for executable in aapt2 d8 zipalign apksigner; do
  test -x "$BT/$executable" || { echo "Missing Android build-tools;35.0.0 ($executable)" >&2; exit 1; }
done
test -f "$PLATFORM" || { echo 'Missing platforms;android-35' >&2; exit 1; }
export PATH="$JAVA_HOME/bin:$PATH"
npm test
npm run build
mkdir -p build/apk/{classes,generated,dex} output
rm -f build/apk/resources.zip build/apk/unsigned.apk build/apk/aligned.apk
"$BT/aapt2" compile --dir android/res -o build/apk/resources.zip
"$BT/aapt2" link -o build/apk/unsigned.apk -I "$PLATFORM" --manifest android/AndroidManifest.xml \
  --java build/apk/generated -A dist --min-sdk-version 28 --target-sdk-version 35 build/apk/resources.zip
find android/java build/apk/generated -name '*.java' -print | sort > build/apk/sources.txt
javac -encoding UTF-8 --release 8 -classpath "$PLATFORM" -d build/apk/classes @build/apk/sources.txt
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
# Stable signing is REQUIRED from 1.2 onward. Never silently create another key.
: "${APK_KEYSTORE:?Stable Terreno signing key required; no ephemeral fallback}"
: "${APK_EXPECTED_CERT_SHA256:?Set the approved public signing certificate SHA256}"
[[ "$APK_EXPECTED_CERT_SHA256" =~ ^[0-9a-fA-F]{64}$ ]] || { echo 'Invalid expected certificate digest' >&2; exit 1; }
test -r "$APK_KEYSTORE" || { echo 'Signing keystore unavailable' >&2; exit 1; }
: "${APK_KEY_ALIAS:?Set APK_KEY_ALIAS for the selected signing key}"
: "${APK_KEY_PASS:?Set APK_KEY_PASS for the selected signing key}"
export APK_KEY_PASS
"$BT/apksigner" sign --ks "$APK_KEYSTORE" --ks-key-alias "$APK_KEY_ALIAS" --ks-pass env:APK_KEY_PASS \
  --key-pass env:APK_KEY_PASS --v4-signing-enabled false --out output/Terreno-1.3.0-piloto.apk build/apk/aligned.apk
"$BT/apksigner" verify --verbose --print-certs output/Terreno-1.3.0-piloto.apk | tee output/firma-apk.txt
actual_cert="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' output/firma-apk.txt)"
test "${actual_cert,,}" = "${APK_EXPECTED_CERT_SHA256,,}" || { rm -f output/Terreno-1.3.0-piloto.apk; echo 'Signing certificate mismatch: APK withheld' >&2; exit 1; }
"$BT/zipalign" -c -p 4 output/Terreno-1.3.0-piloto.apk
"$BT/aapt2" dump badging output/Terreno-1.3.0-piloto.apk | tee output/manifiesto-apk.txt
sha256sum output/Terreno-1.3.0-piloto.apk | tee output/SHA256SUMS.txt
