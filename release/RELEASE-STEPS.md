# Zinfurn — Store'ga chiqarish qadamlari

## Tayyor (kodda)
- Account deletion: My Page → Profil → Delete account (+ https://zinfurn.uz/privacy#delete-account)
- Privacy / Terms: https://zinfurn.uz/privacy , https://zinfurn.uz/terms
- UGC: post/izoh/profilda ⋮ → Report / Block user (backend: `reportContent`, `blockMember`)
- Sign in with Apple: backend `/auth/apple` + iOS tugma
- Android release: `zinfurn-1.4-5.aab` (Play) va `zinfurn-1.4.apk` (sayt, https://zinfurn.uz/about#app)
- iOS: faqat iPhone, PrivacyInfo.xcprivacy, App.entitlements (Apple login/Push/Universal links — `ios-release.sh` yoqadi)
- Sayt: `NEXT_PUBLIC_APPLE_SIGNIN=true` (serverda yoqilgan, faqat iOS app'da ko'rinadi)
- Store matnlari, Data safety, App Privacy: `store-listing.md`
- Screenshots, icon 512, feature graphic: `screenshots/`, `store-assets/`
- Offline: service worker keshi (ochilgan sahifa/rasm/ma'lumot internetsiz ko'rinadi), iOS'da app-bound domains bilan
- Kamera bilan qidiruv: Products → qidiruv maydonidagi 📷 (Gemini vision, server `GEMINI_API_KEY`)
- Widget: iOS (WidgetKit, small/medium) + Android — flash sale yoki eng yangi mahsulot, soatda yangilanadi
- Push: kod tayyor, flag bilan YOPIQ (pastdagi "Push notification" bo'limi)

## ⚠️ Keystore — YO'QOTMANG
`android/app/zinfurn-release.keystore` + `android/keystore.properties` git'da YO'Q. Ikkalasini
Google Drive/fleshkaga nusxalang. Yo'qolsa Play'dagi app'ni yangilab bo'lmaydi.

## Google Play ($25, bir marta) — to'lovdan keyin ~1 soat
1. https://play.google.com/console → Personal akkaunt → pasport bilan verification (1–3 kun).
2. **Create app**: nomi `Zinfurn`, til English (US), App, Free.
3. **Testing → Closed testing → Create track** → Countries: Uzbekistan (+ kerakli) →
   Testers: Google Group yoki email ro'yxat → `release/zinfurn-1.4-5.aab` yuklang →
   Release notes: "First release" → **Review & rollout**.
4. **Setup → App signing** → "App signing key certificate" SHA-256 ni nusxalab menga bering —
   saytdagi `assetlinks.json` ga qo'shaman (havolalar app'da ochilishi uchun).
5. **Dashboard'dagi "Set up your app"** — hammasi `store-listing.md` da tayyor:
   Privacy policy, App access (review login), Ads: No, Content rating (anketa), Target audience 13+,
   Data safety, Store listing (matn, icon `store-assets/play-icon-512.png`,
   feature graphic `store-assets/play-feature-1024x500.png`, `screenshots/android/`).
6. **12 tester × 14 kun** (Personal akkaunt talabi): pastdagi xabarni tarqating. 12+ odam
   opt-in qilib app'ni O'RNATSIN va 14 kun o'chirmasin. Keyin Dashboard → **Apply for production**.

Keyingi versiyalar: `./scripts/android-release.sh 1.2` → yangi AAB'ni Play'ga yuklang.

### Testerlar uchun xabar (Telegram)
> Zinfurn ilovasini Play Store'da birinchilardan bo'lib sinab ko'ring! 🙌
> 1) Shu Gmail'ingizni menga yuboring (Play Store'dagi akkaunt).
> 2) Men yuborgan havola orqali "Become a tester" ni bosing.
> 3) Play Store'dan Zinfurn'ni o'rnating va **14 kun o'chirmang** — har kuni bir marta ochib qo'ysangiz ideal.
> Rahmat!

## Apple App Store ($99/yil) — to'lovdan keyin ~1 soat
1. iPhone → **Apple Developer** app → Enroll → Individual → to'lov.
   "Welcome to the Apple Developer Program" xatini kuting (soatlar – 2 kun).
2. **Team ID**: https://developer.apple.com/account → Membership details → 10 belgili kod.
3. Xcode → Settings → Accounts → Apple ID'ni tanlab yangi team ko'rinishini tekshiring.
4. https://appstoreconnect.apple.com → Agreements'ni qabul qiling → **Apps → + New App**:
   iOS, Name `Zinfurn`, til English, Bundle ID `uz.zinfurn.app`
   (ro'yxatda bo'lmasa — avval 5-qadamni bajaring, keyin qaytib keling), SKU `zinfurn`.
5. Terminal: `cd ~/Projects/zinfurn/zinfurn-app && ./scripts/ios-release.sh <TEAM_ID> 1.0`
   — team, Sign in with Apple, Push, Universal Links'ni yoqadi, build qilib Apple'ga yuklaydi.
   Oxirida chiqqan `git push` buyrug'ini ham ishga tushiring (saytga AASA fayli).
6. App Store Connect → **TestFlight** (10–30 daqiqadan keyin build chiqadi):
   - External Testing → **+** guruh → build qo'shing → Beta review'ga yuboring →
     tasdiqlangach **Public Link**ni yoqing → havolani tarqating.
7. **Push'ni yoqing** (4.2 rad etish xavfini kamaytiradi): developer.apple.com → Keys → + →
   APNs → `.p8` faylni menga bering — backend `.env` va `NEXT_PUBLIC_PUSH_IOS=true` ni men qo'yaman.
   Push yoqilmasa, pastdagi Review notes'dan "push notifications" so'zini olib tashlang.
8. **App Store** tab: `store-listing.md` dan matn, `screenshots/ios-6.9/`, Privacy URL,
   App Privacy, Age rating 12+, Category Shopping, review login + pastdagi Review notes → **Submit**.

Keyingi versiyalar: `./scripts/ios-release.sh <TEAM_ID> 1.1`.

### Review notes (Apple'ga, inglizcha)
> Zinfurn is a furniture marketplace in Uzbekistan. Physical goods only (no digital content).
> Native features: home screen widget (flash sale / newest product), Sign in with Apple,
> push notifications for orders and community activity, camera-based furniture search,
> native share sheet, haptics, universal links, offline cache of viewed pages.
> Login: Sign In → Email or Nickname / Password (credentials in the fields above).
> Account deletion: My Page → Profile → Delete account.
> UGC moderation: ⋮ on any post/comment/profile → Report / Block user.

## Apple 4.2 ("faqat sayt") xavfini kamaytirish
Hozir app'da native: splash, status bar, haptics, native share, deep/universal links,
tizim brauzerida OAuth, Sign in with Apple. Rad etilsa keyingi qadam — **push notification**
(APNs key Apple akkaunt ochilgach). Review notes'da native funksiyalarni sanab o'ting.

## Apple akkaunt o'chirishda token revoke
Apple orqali kirgan user o'chirilsa Apple REST `/auth/revoke` chaqirilishi tavsiya etiladi —
buning uchun Team ID, Key ID va `.p8` kerak (akkaunt ochilgach qo'shiladi).

## Push notification (yoqish)
Backend kodi (FCM + APNs) va app plugin tayyor; kalitlar qo'shilgach ishlaydi.

**Android (Firebase):**
1. https://console.firebase.google.com → Add project → Android app `uz.zinfurn.app`.
2. `google-services.json` ni `android/app/` ga qo'ying → `npx cap sync` → AAB qayta build.
3. Project settings → Service accounts → Generate new private key (JSON).
4. Backend serverdagi `.env` ga: `FIREBASE_SERVICE_ACCOUNT=<JSON yoki base64>` → backend restart.
5. Frontend `.env.production` ga `NEXT_PUBLIC_PUSH_ANDROID=true` → redeploy.
   (google-services.json'siz true qilmang — Android app crash bo'ladi.)

**iOS (APNs, Apple Developer akkaunt kerak):**
1. Xcode → App target → Signing & Capabilities → **+ Push Notifications**.
2. developer.apple.com → Keys → + → Apple Push Notifications service → `.p8` yuklab oling.
3. Backend `.env`: `APNS_KEY_ID`, `APNS_TEAM_ID`, `APNS_KEY` (`.p8` matni, qatorlar `\n` bilan),
   `APNS_BUNDLE_ID=uz.zinfurn.app`, `APNS_SANDBOX=false` (TestFlight/App Store) → restart.
4. Frontend `.env.production`: `NEXT_PUBLIC_PUSH_IOS=true` → redeploy.

Push qachon keladi: buyurtma to'landi / holati o'zgardi, like/izoh/follow bildirishnomalari.

## Widget (iOS)
`ZinfurnWidget` extension target'i bor (bundle `uz.zinfurn.app.ZinfurnWidget`). Pullik team'ga
o'tganda ikkala target'da ham Team'ni tanlang — Archive'da avtomatik birga ketadi.

## Apple Pay
Toss Payments Apple Pay'ni qo'llaydi, lekin **alohida shartnoma** kerak (Toss bilan qo'shimcha
kontrakt, live merchant). Shartnoma bo'lgach Toss to'lov oynasida avtomatik chiqadi —
kodda o'zgarish shart emas (hozir test rejimda, `method: 'CARD'` oynasi).

## Push bildirishnomalar (kod tayyor, faqat kalitlar kerak)
1. console.firebase.google.com → yangi project → Android app `uz.zinfurn.app` → `google-services.json` ni `android/app/` ga qo'y.
2. Project settings → Service accounts → "Generate new private key" → JSON'ni bir qatorga qilib serverdagi backend `.env` ga `FIREBASE_SERVICE_ACCOUNT=...`.
3. iOS (pullik Apple account): developer.apple.com → Keys → APNs key (.p8) → `.env` ga `APNS_KEY_ID`, `APNS_TEAM_ID`, `APNS_KEY` (p8 matni), `APNS_BUNDLE_ID=uz.zinfurn.app`.
4. Frontend `.env.production` da `NEXT_PUBLIC_PUSH_ANDROID=true` / `NEXT_PUBLIC_PUSH_IOS=true` → push (auto-deploy).

## iOS widget sevimlilari — App Group
`group.uz.zinfurn.app` App Group pullik team'da kerak: developer.apple.com → Identifiers → App Groups → yarat, so'ng `uz.zinfurn.app` va widget ID'ga biriktir.
`scripts/ios-release.sh` entitlements'ni o'zi ulaydi (App + ZinfurnWidget). App Group bo'lmasa widget eng yangi/sale mahsulotni ko'rsataveradi.
