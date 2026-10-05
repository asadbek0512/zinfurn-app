#!/bin/bash
# Android: versiyani oshiradi, Play Store uchun AAB va sayt uchun APK yasaydi.
# Ishlatish: ./scripts/android-release.sh <VERSION>   masalan: ./scripts/android-release.sh 1.2
set -euo pipefail

VERSION=${1:?"Versiya kerak: ./scripts/android-release.sh 1.2"}
ROOT=$(cd "$(dirname "$0")/.." && pwd)
SITE=$ROOT/../zinfurn-next
GRADLE=$ROOT/android/app/build.gradle
cd "$ROOT"

export JAVA_HOME=${JAVA_HOME:-/opt/homebrew/opt/openjdk@21}
export ANDROID_HOME=${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}
[ -f android/keystore.properties ] || { echo "android/keystore.properties yo'q — release keystore'siz imzolab bo'lmaydi"; exit 1; }

CODE=$(( $(sed -nE 's/.*versionCode ([0-9]+).*/\1/p' "$GRADLE") + 1 ))
sed -i '' -E "s/versionCode [0-9]+/versionCode $CODE/; s/versionName \"[^\"]+\"/versionName \"$VERSION\"/" "$GRADLE"

npx cap sync android
(cd android && ./gradlew -q clean bundleRelease assembleRelease)

mkdir -p release
cp android/app/build/outputs/bundle/release/app-release.aab "release/zinfurn-$VERSION-$CODE.aab"
cp android/app/build/outputs/apk/release/app-release.apk "release/zinfurn-$VERSION.apk"

# Sayt: yangi APK + eski app'dagilarga "yangilang" banneri
cp "release/zinfurn-$VERSION.apk" "$SITE/public/downloads/zinfurn.apk"
sed -i '' -E "s/LATEST_ANDROID_APP_VERSION = '[^']+'/LATEST_ANDROID_APP_VERSION = '$VERSION'/" "$SITE/libs/config/appDownload.ts"

echo
echo "✅ Android $VERSION ($CODE)"
echo "  Play Store: release/zinfurn-$VERSION-$CODE.aab"
echo "  Sayt APK:   cd $SITE && git add public/downloads/zinfurn.apk libs/config/appDownload.ts && git commit -m 'chore: android app $VERSION' && git push origin main"
