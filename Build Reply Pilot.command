#!/bin/bash
# Double-click to build the Reply Pilot installer (APK) on this Mac.
# First run: downloads Android's build tools into ~/Library/Android (and Java 21 if
# Android Studio's Java is too new), and asks YOU to accept Google's SDK license.
cd "$(dirname "$0")" || exit 1
LOG="build-log.txt"
SDK="$HOME/Library/Android/sdk"
STUDIO_JAVA="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
LOCAL_JDK="$HOME/Library/Android/jdk-21"
SIGNER="667cf8c2758f1a7674674c2308385b30e629260c6dd9f5d883b5b03f59ea8bdb"
major(){ "$1/bin/java" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/{print $2}' | cut -d. -f1; }
fail(){ echo "PROBLEM: $1"; echo "BUILD RESULT: FAILED"; exit 1; }
{
echo "Reply Pilot build started $(date)"
[ -f .local-signing/debug.keystore ] || fail "The signing key .local-signing/debug.keystore is missing. Without it your phone rejects the update."

# 1. Java 17-23 (this project's Gradle 8.13 cannot run on newer Java).
JAVA_HOME=""
for candidate in "$STUDIO_JAVA" "$LOCAL_JDK/Contents/Home"; do
  if [ -x "$candidate/bin/java" ]; then m=$(major "$candidate"); if [ -n "$m" ] && [ "$m" -ge 17 ] && [ "$m" -le 23 ]; then JAVA_HOME="$candidate"; break; fi; fi
done
if [ -z "$JAVA_HOME" ]; then
  echo "Downloading Java 21 for the build (one time)..."
  TMP=$(mktemp -d)
  curl -fL --progress-bar "https://api.adoptium.net/v3/binary/latest/21/ga/mac/aarch64/jdk/hotspot/normal/eclipse" -o "$TMP/jdk.tar.gz" || fail "Could not download Java 21."
  tar -xzf "$TMP/jdk.tar.gz" -C "$TMP" || fail "Could not unpack Java 21."
  mkdir -p "$HOME/Library/Android" && mv "$TMP"/jdk-21* "$LOCAL_JDK" || fail "Could not install Java 21."
  rm -rf "$TMP"; JAVA_HOME="$LOCAL_JDK/Contents/Home"
fi
export JAVA_HOME
echo "Using Java $(major "$JAVA_HOME") from $JAVA_HOME"

# 2. Android SDK in the standard place Android Studio also uses.
SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  echo "Downloading Android's command-line tools (one time)..."
  ZIP=$(curl -fsSL https://dl.google.com/android/repository/repository2-3.xml | grep -o 'commandlinetools-mac-[0-9]*_latest\.zip' | sort -t- -k3 -n | tail -1)
  [ -n "$ZIP" ] || fail "Could not find Android's command-line tools download."
  TMP=$(mktemp -d)
  curl -fL --progress-bar "https://dl.google.com/android/repository/$ZIP" -o "$TMP/tools.zip" || fail "Could not download Android's command-line tools."
  unzip -q "$TMP/tools.zip" -d "$TMP" && mkdir -p "$SDK/cmdline-tools" && mv "$TMP/cmdline-tools" "$SDK/cmdline-tools/latest" || fail "Could not install Android's command-line tools."
  rm -rf "$TMP"
fi
if [ ! -d "$SDK/platforms/android-36" ] || [ ! -d "$SDK/build-tools/36.0.0" ]; then
  echo
  echo "=================================================================="
  echo " Android's SDK requires you to accept Google's license terms."
  echo " Read each one below, then type  y  and press Return to accept."
  echo "=================================================================="
  "$SDKMANAGER" --sdk_root="$SDK" --licenses || fail "The Android SDK licenses were not accepted."
  "$SDKMANAGER" --sdk_root="$SDK" "platforms;android-36" "build-tools;36.0.0" "platform-tools" || echo "Note: the SDK install reported a problem; the build will try to fetch what it needs."
fi
echo "sdk.dir=$SDK" > local.properties

# 3. Test and build the signed release.
chmod +x ./gradlew
./gradlew --no-daemon :app:testReleaseUnitTest :app:assembleRelease || fail "Gradle build failed (details above)."
VERSION=$(grep -o "versionName '[^']*'" app/build.gradle | cut -d"'" -f2)
mkdir -p dist && cp app/build/outputs/apk/release/app-release.apk "dist/Reply-Pilot-$VERSION.apk" || fail "The APK was not produced."
echo "Saved dist/Reply-Pilot-$VERSION.apk"
if command -v python3 >/dev/null 2>&1; then python3 tools/check-apk-signer.py "dist/Reply-Pilot-$VERSION.apk" "$SIGNER" || fail "The APK is not signed with your phone's key."; fi
echo "BUILD RESULT: SUCCESS"
} 2>&1 | tee "$LOG"
[ -n "$REPLY_PILOT_AUTO" ] || { echo; echo "Done. You can close this window."; }
