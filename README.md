# 🕶️ StegCloak Android

Native Android app for **[StegCloak](https://github.com/KuroLabs/stegcloak)** — hide encrypted messages inside ordinary-looking text using invisible zero-width Unicode characters.

اپلیکیشن اندروید **استگ‌کلوک** — پنهان‌سازی پیامِ رمزنگاری‌شده در دلِ یک متن کاملاً معمولی، با کاراکترهای نامرئی یونیکد (Zero-Width Characters). 🇮🇷

> **Bilingual • دو زبانه** — English & فارسی with in-app toggle (🌐). Package name stays strictly English: `com.stegcloak.android`.

## Download

Grab the APK from **[Releases](../../releases)** — works **offline** on Android 5.0+ with **zero permissions**.

## ✨ Features

- 🫥 **Hide**: secret message + cover text + optional password → text that looks perfectly normal
- 🔍 **Reveal**: paste the text → auto-detects encryption/HMAC flags, extracts the secret
- 🔐 **Real crypto**: AES-256-CTR, key derived with PBKDF2-HMAC-SHA512 (10,000 iterations), optional HMAC-SHA256 integrity check — identical to upstream
- 🗜️ **Compression**: faithful port of LZUTF8 (lzutf8.js v0.5.5) + the ZWC Huffman stage
- 🌗 **Bilingual UI**: English / فارسی — toggle with one tap, choice persisted; RTL layout auto-adjusts, per-app language supported on Android 13+ (`locales_config`)
- 👁️ **Fixed eye toggle**: password fields now correctly show/hide with state-safe logic (previously always stayed hidden due to faulty InputType bitmask)
- 📤 **Share-in**: share plain text from any app → StegCloak opens directly in Reveal mode
- 🌙 Dark, minimal UI, clipboard auto-copy, share sheet, random cover generator

## 🌐 Languages / زبان‌ها

| Language | File | Notes |
|----------|------|-------|
| English (default) | `res/values/strings.xml` | LTR layout, English sample covers |
| فارسی | `res/values-fa/strings.xml` | RTL layout, Persian sample covers |

Toggle at the very top of the app (🌐 **English / فارسی**). The choice is saved in `SharedPreferences` (`stegcloak_prefs`) and reapplied via `LocaleHelper.updateBaseContextLocale()` — survives restarts and configuration changes. App name also localized: **StegCloak** ↔ **استگ‌کلوک**, while `applicationId` remains pure English.

## Package name / نام پکیج

The package is guaranteed English as requested:

```gradle
namespace 'com.stegcloak.android'
applicationId "com.stegcloak.android"
```

No Persian characters in code identifiers, package, or manifest `applicationId`. Only user-visible strings are localized.

## Compatibility

Output is **fully interchangeable** with the upstream CLI / web app (npm `stegcloak`, stegcloak.surge.sh):

| Direction | Status |
|---|---|
| Concealed by **this app** → revealed by **upstream** | ✅ verified |
| Concealed by **upstream** → revealed by **this app** | ✅ verified |

Verified with automated round-trip tests across 7 scenarios (Persian text, emoji, empty password, `--nocrypt` mode, 300-char repetitive secrets, tiny payloads) plus byte-identical PBKDF2 output against Node.js.

## How it works

```
message → LZUTF8 compress → bitwise NOT → AES-256-CTR encrypt (8-byte salt)
        → every 2 bits = one invisible char (U+200C / U+200D / U+2061 / U+2062)
        → Huffman over the invisible stream (U+2063 / U+2064 as flags)
        → prepended to a mid-cover word
```

## Build

```bash
# local.properties → sdk.dir=<your Android SDK path>
gradle assembleDebug   # or open in Android Studio
```

Tested on compileSdk 33 / minSdk 21 / targetSdk 33.

## Repository layout

```
app/src/main/
├── AndroidManifest.xml               # @string/app_name, @style/Theme.StegCloak, locales_config, SEND filter
├── res/
│   ├── values/strings.xml            # English
│   ├── values-fa/strings.xml         # Persian
│   ├── values/colors.xml
│   ├── values/themes.xml
│   ├── xml/locales_config.xml
│   └── drawable/ic_launcher.xml
└── java/com/stegcloak/android/
    ├── StegCloak.java      # core algorithm — faithful port of stegcloak.js
    ├── StegCrypto.java     # PBKDF2-HMAC-SHA512 + AES-256-CTR + HMAC-SHA256
    ├── Lzutf8.java         # LZUTF8 compression port (format-compatible with lzutf8.js)
    ├── LocaleHelper.java   # bilingual persistence & Config update
    └── MainActivity.java   # UI (programmatic, fully localized, eye-fixed)

test/CliTest.java       # desktop harness for cross-implementation round-trip tests
vectors/                # concealed-text vectors produced by the ORIGINAL StegCloak (7 scenarios)
```

## Bugfix: Eye button 👁️

**Previous code:**
```java
boolean hidden = (field.getInputType() & TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) == 0;
field.setInputType(hidden ? VISIBLE : PASSWORD);
```
Mask test always evaluated to *not zero* for both hidden (`0x81 & 0x90 = 0x80`) and visible (`0x91 & 0x90 = 0x90`), so `hidden` was constantly `false` and the field never became visible.

**Fixed code:** store visibility in `View.setTag(Boolean)` and toggle explicitly, restoring `Typeface.DEFAULT` and cursor position on each flip. Each password field keeps its own independent state; icons toggle 👁 ↔ 🙈 with proper content-description for accessibility.

## Credits & License

MIT © StegCloak Android contributors.

Contains ports of MIT-licensed work:
- [StegCloak](https://github.com/KuroLabs/stegcloak) © KuroLabs (algorithm, encryption & ZWC scheme)
- [LZUTF8.js](https://github.com/rotemdan/lzutf8.js) © Rotem Dan (compression format)

---

### 🇮🇷 توضیح فارسی

برنامه کاملاً دو زبانه است (انگلیسی و فارسی). با دکمه 🌐 در بالای صفحه زبان را عوض کنید؛ انتخاب شما ذخیره می‌شود و حتی بعد از بستن برنامه باقی می‌ماند. نام پکیج حتماً انگلیسی است (`com.stegcloak.android`) و باگ دکمهٔ چشمِ کنار رمز عبور برطرف شده است — حالا با هر بار لمس، رمز به‌درستی نمایش/مخفی می‌شود.
