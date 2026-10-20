# 🕶️ StegCloak Android

Native Android app for **[StegCloak](https://github.com/KuroLabs/stegcloak)** — hide encrypted messages inside ordinary-looking text using invisible zero-width Unicode characters.

اپلیکیشن اندروید **استگ‌کلوک** — پنهان‌سازی پیامِ رمزنگاری‌شده در دلِ یک متن کاملاً معمولی، با کاراکترهای نامرئی یونیکد (Zero-Width Characters). 🇮🇷

## Download

Grab the APK from **[Releases](../../releases)** — works offline on Android 5.0+ with **zero permissions**.

## Features

- 🫥 **Hide**: secret message + cover text + optional password → text that looks perfectly normal
- 🔍 **Reveal**: paste the text → auto-detects encryption/HMAC flags, extracts the secret
- 🔐 **Real crypto**: AES-256-CTR, key derived with PBKDF2-HMAC-SHA512 (10,000 iterations), optional HMAC-SHA256 integrity check — identical to upstream
- 🗜️ **Compression**: faithful port of LZUTF8 (lzutf8.js v0.5.5) + the ZWC Huffman stage
- 🌙 Dark, minimal UI (Persian / فارسی), clipboard auto-copy, share sheet, random cover generator

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

## Repository layout

```
app/src/main/java/com/stegcloak/android/
├── StegCloak.java      # core algorithm — faithful port of stegcloak.js
├── StegCrypto.java     # PBKDF2-HMAC-SHA512 + AES-256-CTR + HMAC-SHA256
├── Lzutf8.java         # LZUTF8 compression port (format-compatible with lzutf8.js)
└── MainActivity.java   # UI
test/CliTest.java       # desktop harness for cross-implementation round-trip tests
vectors/                # concealed-text vectors produced by the ORIGINAL StegCloak
```

## Credits & License

MIT © StegCloak Android contributors.

Contains ports of MIT-licensed work:
- [StegCloak](https://github.com/KuroLabs/stegcloak) © KuroLabs (algorithm, encryption & ZWC scheme)
- [LZUTF8.js](https://github.com/rotemdan/lzutf8.js) © Rotem Dan (compression format)
