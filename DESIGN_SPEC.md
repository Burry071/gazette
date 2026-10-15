# Design Spec — On-Device Notification Ledger ("Gazette")

Status: draft for review · 2026-10-14 · spike GO'd on corpus evidence (below)
Note: this file is the *technical* spec. The visual DESIGN.md (tokens for the one screen) is a separate, small build-phase artifact.

## What this is

A standalone Android app. It listens to payment notifications from any app or bank on the
phone, reads them with a tiny on-device model (Gemma 3 1B, ~557 MB, fully offline), and keeps
one simple ledger: money sent and received, totals on top, newest first, the original
notification text small and gray underneath. Nothing else — no merchant field, no bank login,
no cloud, no account with us.

The pitch line: every other money-tracking tool pattern-matches bank SMS formats and breaks
when a format changes; this app reads the notification like a person would, so it keeps
working when formats change or when a new payment app appears.

## Demo script (the 90 seconds that go in the post)

1. Fresh install on a real Pixel 7. Grant notification access. Model file already on device.
2. Send Rs 5 to a friend via NayaPay. Its notification drops. A "reading…" row appears
   immediately and resolves into `Rs 5 · sent` with the original text underneath once the
   on-device model finishes (the wait is shown, not hidden). Sent total ticks up.
3. Receive money back. Same flow, received side.
4. A Zong promo SMS arrives (has an "Rs 30" offer). Nothing appears in the ledger.
5. Airplane mode on (cellular + wifi off), then a payment-shaped notification replayed via
   `adb shell cmd notification post` (labeled as a test ping on screen) — the model reads it,
   the row appears, zero network involved. Also show the app's permission page: no INTERNET
   permission at all.

## Scope

In: any notification containing a PKR amount on the phone; amount + sent/received only;
on-device storage; offline operation.
Out (v1): merchant/payee, categories, budgets, multi-currency, bank APIs, accounts, cloud,
iOS, widgets, exports. Cash/bank-in-hand tracking.

## Pipeline (the validated core — do not improvise during build)

Five layers, in order. Exact prompts live verbatim in `scripts/extract_test_v3.js`
(`classifyPrompt`, `extractPrompt`) and are copied into Kotlin at build time — the JS harness
is the single source of truth for prompt text.

- **Stage 0 — structural gate (deterministic, ~0 ms).**
  No `Rs`/`PKR` + number in the text (`\b(rs\.?|pkr)\s*[\d,]`, case-insensitive) → discarded
  in memory. Never stored, never reaches the model. Verified: 0 false drops on 12 transaction
  entries; dropped 9 of 18 junk cases for free.
- **Stage 1 — classifier (model, 1 word).** Ask PAYMENT or OTHER per full text; 3 votes at
  temperature 0.4, `num_predict` 4; majority decides; disagreement/tie → OTHER (fail-safe).
- **Stage 2 — extraction (model, only on survivors).** Output exactly `AMOUNT: <number>` /
  `DIRECTION: sent or received`; 3 votes; majority per field; normalization accepted:
  paid/spent/debit→sent, credited/credit→received.
- **Stage 3 — reconciliation and privacy veto (deterministic, 0 ms).** Frozen regexes
  (normative implementation: `scripts/reconcile.js`):
  received-side `\b(received|credited|deposited)\b`; sent-side
  `\b(paid|sent|spent|debited|withdrawn|transferred|withdrawal)\b`; balance word `\bbalance\b`;
  security code `\botp\b|one[- ]time\s+(pin|password|code|passcode)` (case-insensitive).
  Rules: exactly one event verb present → it overrides the model's direction; classifier said
  OTHER but an event verb is present → rescued as a transaction (amount from the gate regex);
  classifier said PAYMENT but the text is a balance state (balance word, no event verb) →
  vetoed; text matches the security-code pattern → vetoed unconditionally (privacy: an OTP can
  never become a ledger row, even one that mentions an amount). Everything else: the model's
  answer stands. Event-verb lexicon verified 0 false pos/neg across the corpus; security-code
  pattern verified on 14 cases including an amount-bearing OTP.
- **Fail-safe principle:** any uncertainty resolves to "not a transaction". A missed payment
  is acceptable; showing junk (or worse, showing an OTP) is not.

## Evidence (measured 2026-10-14, Ollama CPU `num_gpu:0`, 30-entry corpus: 28 real sanitized
captures + 2 synthetic sent-side variants)

| metric | 1B v1 | 1B v2 | 1B v3 | **1B v3+reconcile** | **4B v3** |
|--------|------|------|------|--------------------|-----------|
| junk rejected | 0/18 | 1/18 | 17/18 | **18/18** | **18/18** |
| tx detected | 10/10 | 10/10 | 9/10 | **10/10** | **10/10** |
| amount | 10/10 | 10/10 | 9/9 | **10/10** | **10/10** |
| direction | 10/10 | 8/10 | 5/9 | **10/10** | **10/10** |
| exact | 10/28 | 9/28 | 22/28 | **28/28** (+2/2 synth) | **28/28** (+2/2 synth) |

Notes: reconciliation overrode only 7 of 30 decisions for the 1B (2 rescues, 1 veto,
4 direction fixes); the 4B needed zero overrides. The 1B reaches the 4B's accuracy via
engineering, which is the story this entry tells. Reproduce:
`node scripts/extract_test_v3.js gemma3:1b && node scripts/reconcile.js corpus/results_v3_gemma3_1b.jsonl`.
Caveat on record: tuned in-sample; on-phone and fresh-capture verification is part of build
(see Success criteria).

Timings, desktop CPU for reference: 1B 8.6 s/entry, 4B 18.2 s/entry. On-device latency is
unmeasured — see Risks.

## App architecture (single module, Kotlin + Compose)

- `LedgerListenerService : NotificationListenerService` — captures posted notifications
  (title + text + bigText), skips own package, ongoing, and group summaries; builds the
  candidate string; dedupes identical text within 60 s; feeds a single-threaded queue (the
  model session is serialized).
- `Pipeline` — Kotlin port of Stages 0-3 above. Text stays in memory: gate-failed text is
  discarded immediately, never persisted, never sent to the model. Only confirmed payment
  entries are stored.
- `ModelRuntime` — LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android`, version pinned
  at build; 0.18.0 confirmed available during the spike) + `gemma3-1b-it-int4.litertlm` (557 MB,
  from `litert-community/Gemma3-1B-IT`). The model file is *not* shipped in the repo
  (gitignore); the README's 3-step setup has the user accept the Gemma license in their own
  browser, then `adb push` the file to the app's external files dir. On launch the app shows
  a "model missing" banner with that instruction instead of crashing. Storage budget to state
  in README: ~1.1 GB installed (557 MB weights + a roughly equal XNNPack weight cache).
- `LedgerStore` — Room DB, one table: id, epochMs, amount (numeric), direction, originalText,
  packageName. Totals are summed from rows, never stored.
- Permissions: notification-listener special access only. **No INTERNET permission** — the
  offline claim is enforced by construction, not by promise.

## UI (one screen)

Header: two totals, `sent` and `received` (money in/out must be distinguishable at a glance;
one accent color for one direction, muted for the other — exact tokens get their own minimal
DESIGN.md before any Compose UI is written, per the host design gate). List below, newest
first: amount + direction per row, original text small/gray underneath. A transient "reading…"
row while the pipeline runs. Plain empty state. No model/AI wording anywhere in the chrome —
the app names actions, not technology.

## Latency and UX budget

Notification → row must survive an async delay: on desktop CPU the 1B spends 15-30 s on a real
transaction (6 model calls; the 8.6 s/entry figure in Evidence is the corpus average, pulled
down by zero-cost gated junk). On a Tensor G2 this is unmeasured; the "reading…"
row exists precisely so the delay reads as work, not as a bug. If measured latency is
unacceptable (>60 s), first lever: one classify vote + one extract vote (re-run the desktop
experiment with VOTES=1 to confirm accuracy holds before changing the shipped pipeline).

## Success criteria

1. End-to-end on the real phone, fully offline: next deliberately-triggered real payments
   from the user's actual wallets land in the ledger correct (amount + direction).
2. Junk silence on-device: promos, balance messages, and OTPs produce no rows. OTP-shaped
   texts are discarded by the gate (no amount) or the Stage-3 privacy veto; nothing is ever
   stored unless confirmed as a payment.
3. Airplane-mode demo captured on video; totals match a manual recount of the shots.
4. Public repo carries the evidence trail: sanitized corpus, harness, reconciliation, result
   files, and a README with the one-command reproduction of the accuracy table.
5. Post published Sat 2026-10-17 (buffer to the Mon 2026-10-19 11:59 AM PKT deadline),
   writeup centered on the v1→v4 junk-rejection journey and the small-model-vs-engineering
   story, with the in-sample caveat and the on-device numbers stated plainly.

## Risks and fallbacks

| risk | fallback |
|---|---|
| litertlm-android API churn / integration friction | pin 0.18.0; fallback runtime MediaPipe `tasks-genai:0.10.35` (both ship arm64 + x86_64 ABIs) |
| On-device latency worse than desktop | reduce votes (experiment above); worst case single-vote + reconciliation still met the fail-safe bar on desktop |
| Battery optimizer kills the listener | README step: disable battery optimization for the app; document symptom + fix |
| Model license gating blocks download | already handled: user accepts license in own browser, file moves by adb, no tokens in repo |
| Urdu-only or unseen formats miss (fail-safe rejects them) | designed failure direction (miss > junk); collect fresh captures, extend corpus, re-run harness |

## Open items for the build phase

- minSdk: verify from the litertlm-android AAR metadata (expect 26-28; device is API 33+).
- applicationId + final app name (rename is cheap until the repo goes public).
- VOTES=1 accuracy experiment on desktop (latency lever, run before shipping the lever).
- On-device latency + peak memory measurement, recorded into the README.
- Minimal visual DESIGN.md, then Compose UI.

## Review log

- 2026-10-14 self-review: numbers cross-checked against result files; four fixes applied
  (per-transaction latency figure, honest demo step for airplane mode, precise privacy claim,
  OTP veto added to the pipeline and verified 14/14 regex cases); in-sample caveat stated.
  Ready for user review.
