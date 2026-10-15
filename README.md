# Gazette

Notification → statement. Gazette is a one-screen Android app that reads your
payment notifications on-device and keeps a plain ledger: money received,
money sent, and nothing else. No accounts, no cloud, no analytics, and no
INTERNET permission — the app literally cannot phone home.

The reading is done by Gemma 3 1B running entirely on the phone through
[LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM). There are no
hardcoded bank formats: any payment notification in the `Rs` shape is
understood from the text itself, and uncertain parses resolve to nothing.

## Install (prebuilt APK)

Grab the signed APK for arm64 phones (~41 MB):

```
apk/Gazette-v0.1.0-arm64.apk
```

or download it directly:
[apk/Gazette-v0.1.0-arm64.apk](https://github.com/Burry071/gazette/raw/main/apk/Gazette-v0.1.0-arm64.apk)

First launch asks for two things:

1. **Notification access** — tap ENABLE ACCESS and allow Gazette in the
   system list. This is what lets the app see payment alerts; it is the only
   sensitive permission Gazette holds.
2. **The offline reading file** — tap GET THE FILE to open the model page,
   accept the Gemma license there, download `gemma3-1b-it-int4.litertlm`
   (~557 MB), then back in Gazette tap I HAVE THE FILE and pick it from your
   Downloads. The file is copied into the app's private storage and never
   leaves the phone.

The first read after import warms the model up and takes a couple of minutes
on a mid-range phone; entries after that are faster. When a payment alert
arrives you'll see a READING line in the statement, then the row.

## Building from source

- Android 9+ (API 28) device or x86_64 emulator, Android SDK with platform
  tools (`adb`)
- Build and install: `./gradlew :app:installDebug`
- Grant notification access from the app's ENABLE ACCESS link
- Model file: either import it in-app as above (works on debug builds too),
  or push it:

  ```
  adb push gemma3-1b-it-int4.litertlm /sdcard/Android/data/com.gazette.app/files/model.litertlm
  ```

  Relaunch Gazette — it moves the file into private storage on next start.

Release builds are arm64-only and sign with a local keystore described by
`keystore/keystore.properties` (git-ignored); without that file they build
unsigned. Debug builds run on the x86_64 emulator as well.

## Gemma

This repository bundles no model weights. The app loads
[Gemma 3 1B IT](https://ai.google.dev/gemma) (int4, LiteRT format), which you
download separately and which runs only on your device. Use of Gemma is
governed by the [Gemma Terms of Use](https://ai.google.dev/gemma/terms) and
the [Gemma Prohibited Use Policy](https://ai.google.dev/gemma/prohibited_use_policy).

## Repository layout

- `app/` — the Android app (Kotlin, Jetpack Compose, Room)
  - `pipeline/` — the five-layer reading: gate → classify votes → extract
    votes → vote parsing → reconciliation (rules, no model)
  - `engine/` — LiteRT-LM runtime adapter and the serialized work queue
  - `service/` — the NotificationListenerService
  - `ui/` — the statement screen
- `apk/` — the prebuilt signed release APK
- `scripts/` — the desktop calibration harness the pipeline was ported from,
  plus its archived runs
- `corpus/` — the labeled notification corpus (sanitized) and results
- `DESIGN.md` — the design specification the UI is built against
