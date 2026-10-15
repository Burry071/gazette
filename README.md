# Gazette

Notification → statement. Gazette is a one-screen Android app that reads your
payment notifications on-device and keeps a plain ledger: money received,
money sent, and nothing else. No accounts, no cloud, no analytics, and no
INTERNET permission — the app literally cannot phone home.

The reading is done by Gemma 3 1B running entirely on the phone through
[LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM). There are no
hardcoded bank formats: any payment SMS/notification in the `Rs` shape is
understood from the text itself, and uncertain parses resolve to nothing.

## Requirements

- Android 9+ (API 28), arm64 device or x86_64 emulator
- Android SDK platform tools (`adb`) on your machine
- The model file (below)

## Setup

1. **Build and install**

   ```
   ./gradlew :app:installDebug
   ```

2. **Grant notification access** — open Gazette and tap ENABLE ACCESS; allow
   "Gazette" in the system list.

3. **Drop in the offline reading file** (one step)

   Download `gemma3-1b-it-int4.litertlm` (~557 MB) from
   [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT)
   (accept the Gemma license there first), then:

   ```
   adb push gemma3-1b-it-int4.litertlm /sdcard/Android/data/com.gazette.app/files/model.litertlm
   ```

   Relaunch Gazette. On the next launch it moves the file into its private
   storage, loads it, and the statement starts filling from new payment
   notifications. (Debug builds also accept:

   ```
   adb push gemma3-1b-it-int4.litertlm /data/local/tmp/
   adb shell chmod 644 /data/local/tmp/gemma3-1b-it-int4.litertlm
   adb shell run-as com.gazette.app cp /data/local/tmp/gemma3-1b-it-int4.litertlm files/model.litertlm
   ```)

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
- `scripts/` — the desktop calibration harness the pipeline was ported from,
  plus its archived runs
- `corpus/` — the labeled notification corpus (sanitized) and results
- `DESIGN.md` — the design specification the UI is built against
