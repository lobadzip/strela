#!/usr/bin/env bash
# Builds the release APK and installs it on the phone connected over USB (USB debugging on).
# The app runs its demo city by itself; scripts/demo.sh is only needed to try it against the server.
set -euo pipefail
cd "$(dirname "$0")/.."

sdk="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties 2>/dev/null)}"
adb="${sdk:+$sdk/platform-tools/}adb"

./gradlew --quiet :androidApp:assembleRelease
apk=androidApp/build/outputs/apk/release/androidApp-release.apk

if ! "$adb" get-state >/dev/null 2>&1; then
  echo "No phone found. Enable USB debugging, plug it in and accept the prompt on the screen."
  echo "The APK is here if you would rather copy it over: $apk"
  exit 1
fi
"$adb" install -r "$apk"
"$adb" shell am start -n io.github.lobadzip.strela/.MainActivity >/dev/null
echo "Installed and launched."
