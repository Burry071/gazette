<!-- DRAFT — challenge #hf26challenge (Week 2 Side Hustle). Fill the two
[ON-DEVICE] placeholders after the first real-device run, then publish. -->

# Gazette — any payment notification becomes a ledger, entirely on your phone

You're standing in a shop and someone says they've sent you Rs 2,000. You
check your phone. The notification is gone — or it's there, wedged between a
pizza promo and an OTP, and you can't tell if that Rs figure is the money
arriving or your balance after it left.

Every wallet and bank in Pakistan writes these notifications differently.
Existing ledger apps solve this with hundreds of hardcoded format rules —
one per bank, per app, per phrasing — and they break the day a bank rewords
a push notification.

**Gazette takes a different bet: a small language model, running entirely on
your phone, reads the notification the way you do.** No format tables. No
cloud. No INTERNET permission — the app is physically incapable of sending
your notifications anywhere.

## What it is

One screen. A statement.

Money received and money sent at the top under an accounting double rule.
Below, every detected payment, newest first: amount right-aligned in a
column, and the original notification text underneath, verbatim — it's
evidence, not paraphrase. Received amounts are set in a deep pine green;
sent amounts stay ink. Nothing else. No cards, no icons, no merchant
database to maintain.

Payments arrive as notifications from *any* app. Gazette listens, reads,
and files them.

## The stack, bottom up

Gemma 3 1B (int4) runs on-device through LiteRT-LM. Around it, five layers:

1. **A structural gate.** If the text doesn't contain an `Rs`/`PKR` amount
   pattern, it never reaches the model. Most notification traffic — promos,
   OTPs, service messages — dies here in zero milliseconds.
2. **A classifier vote.** The model answers one word: is this a payment?
   Three samples, majority decides. Not sure means *no* — the fail-safe
   direction is always "don't record".
3. **An extraction vote.** Survivors get read for amount and direction,
   again as three samples.
4. **Vote parsing** with exact, tested semantics.
5. **Reconciliation — rules, no model.** A verb lexicon (*paid, sent,
   debited, received, credited…*), a balance-state veto, an event-verb
   rescue, and an OTP veto that discards any OTP-shaped text even if it
   carries an amount. Your one-time codes are never shown, never stored.

## The part I'm proud of: small model, honest numbers

I calibrated on a corpus I collected myself — 28 real payment notifications
across NayaPay-style QR, RAAST, and IBFT transfers (sanitized: pseudonyms,
masked accounts), 18 junk negatives, 2 synthetic sent-side pushes. Every
number below is from archived runs, replayable from the repo.

| Configuration | Real payments | Junk rejected | Amount | Direction |
|---|---|---|---|---|
| Gemma 3 1B, raw | 10/10 | 0/18 | 10/10 | — |
| Gemma 3 4B, + gate & classifier | 28/28 | 18/18 | — | — |
| **Gemma 3 1B, full five layers** | **28/28** | **18/18** | **10/10** | **10/10** |

The 4B needed zero help: the rules overrode nothing. The 1B reached the same
accuracy with the reconciliation layer overriding exactly 7 decisions — two
rescues, one veto, four direction fixes. That's the thesis of Gazette: the
engineering compensates for the model size, deterministically, and every
override is a written rule you can audit — not a bigger model you have to
trust.

On desktop CPU the 1B pipeline averaged 8.6s per notification across all 30
corpus entries (the 4B: 18.2s). [ON-DEVICE TIMING — placeholder, measured
after first phone run.]

**Honest caveat:** that corpus is one phone's notifications, tuned in-sample.
It proves the pipeline works end-to-end and that nothing about it is
bank-specific; it does not prove universal coverage. Fresh captures keep
getting added.

## Privacy, structurally

- No INTERNET permission. Not "we promise not to call home" — the OS forbids it.
- The model file is downloaded by *you*, from Google's Gemma release, and
  pushed to the phone. The app never downloads anything.
- OTPs are vetoed by rule, not by model judgment.
- The notification text is stored as received — on your device, in a local
  Room database, and nowhere else.

## The design

The screen is typeset like a statement because that's what it is. One type
family (Schibsted Grotesk), tabular figures so the amount column aligns like
a printed ledger, hairline rules instead of cards, one accent color reserved
for money received. Zero border radius anywhere — a statement isn't a chat
bubble. While the model reads a fresh notification, a single READING row
pulses at the top and then either files itself or disappears.

## Try it

Kotlin, Jetpack Compose, Room, LiteRT-LM. Android 9+, arm64.

1. `./gradlew :app:installDebug`
2. Grant notification access.
3. Drop in the model file (one `adb push`, ~557 MB, from
   [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT)).

Full instructions in the repo README.

## What's next

- Fresh-capture accuracy runs on phones I don't own
- A sent-side capture corpus (my received-side corpus is real; sent-side is
  currently 2 synthetic pushes)
- Dark theme — v1 is deliberately daylight-only, because the moment this
  screen gets used is standing in a bazaar at noon

Repo: [GITHUB URL — added at publish]
