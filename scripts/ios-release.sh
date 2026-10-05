#!/bin/bash
# iOS: build → App Store Connect'ga yuklash (TestFlight'da paydo bo'ladi).
# Ishlatish: ./scripts/ios-release.sh <TEAM_ID> [VERSION]   masalan: ./scripts/ios-release.sh AB12CD34EF 1.0
# Oldin: Xcode → Settings → Accounts'da pullik akkaunt kirgan, App Store Connect'da uz.zinfurn.app app'i yaratilgan bo'lsin.
set -euo pipefail

TEAM_ID=${1:?"Team ID kerak: ./scripts/ios-release.sh <TEAM_ID> [VERSION]"}
VERSION=${2:-1.0}
BUILD=$(date +%y%m%d.%H%M)   # har yuklashda o'sib boradi — Apple bir xil build raqamini qabul qilmaydi

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SITE=$ROOT/../zinfurn-next
OUT=$ROOT/ios/App/build
ARCHIVE=$OUT/Zinfurn.xcarchive
cd "$ROOT"

[[ $TEAM_ID =~ ^[A-Z0-9]{10}$ ]] || { echo "Team ID 10 belgidan iborat bo'ladi (developer.apple.com → Membership)"; exit 1; }

ruby scripts/ios-setup.rb "$TEAM_ID" "$VERSION" "$BUILD"
npx cap sync ios

rm -rf "$ARCHIVE"
xcodebuild -project ios/App/App.xcodeproj -scheme App -configuration Release \
	-destination 'generic/platform=iOS' -archivePath "$ARCHIVE" \
	-allowProvisioningUpdates archive | tail -5

/usr/libexec/PlistBuddy -c "Set :teamID $TEAM_ID" scripts/ExportOptions.plist 2>/dev/null \
	|| /usr/libexec/PlistBuddy -c "Add :teamID string $TEAM_ID" scripts/ExportOptions.plist
xcodebuild -exportArchive -archivePath "$ARCHIVE" -exportOptionsPlist scripts/ExportOptions.plist \
	-exportPath "$OUT/export" -allowProvisioningUpdates | tail -5

# Universal links: zinfurn.uz havolalari app'da ochilishi uchun saytga AASA fayli
AASA=$SITE/public/.well-known/apple-app-site-association
cat > "$AASA" <<JSON
{
	"applinks": {
		"details": [{ "appIDs": ["$TEAM_ID.uz.zinfurn.app"], "components": [{ "/": "*" }] }]
	},
	"webcredentials": { "apps": ["$TEAM_ID.uz.zinfurn.app"] }
}
JSON

echo
echo "✅ Yuklandi: Zinfurn $VERSION ($BUILD). 10-30 daqiqada App Store Connect → TestFlight'da chiqadi."
if ! git -C "$SITE" diff --quiet -- "$AASA" 2>/dev/null || [ -n "$(git -C "$SITE" status --porcelain -- "$AASA")" ]; then
	echo "Sayt uchun AASA yangilandi — deploy qiling:"
	echo "  cd $SITE && git add public/.well-known/apple-app-site-association && git commit -m 'feat: apple app site association' && git push origin main"
fi
