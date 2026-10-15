# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

delegated: Kotlin + Jetpack Compose (Material 3 base) — single-module app chosen for the on-device model toolchain (LiteRT-LM, MediaPipe fallback); recorded in DESIGN_SPEC.md.

## Users

Primary: Pakistani Android users who receive payment notifications from wallet apps and banks (NayaPay, JazzCash, EasyPaisa, bank SMS in mixed Urdu/English). Their job: know what money actually moved, without opening several bank apps or parsing notification text by hand.

First real user: the author himself (his own NayaPay/JazzCash notifications feed it daily).

Demo audience: DEV/Hacktoberfest "Side Hustle" (Week 2) judges — Writing Quality is the heaviest weight; Best Use of Gemma is the category being targeted.

## Product Purpose

A standalone Android app: a NotificationListenerService catches payment notifications from any app or bank, and a tiny on-device model answers exactly three questions — is this a real transaction, how much, out or in. The result is one simple ledger: sent and received totals on top, newest first. Success = impresses judges on criteria, then gets used daily by its author. Nothing else is in scope.

## Positioning

Zero hardcoded bank formats: no per-bank regex lists, no bank login, no account linking. The model reads any notification format on-device; deterministic code only reconciles and vetoes. Fully offline by construction — the app ships with no INTERNET permission.

## Operating Context

- Amounts are PKR ("Rs"), often in mixed Urdu/English SMS from NayaPay, JazzCash, EasyPaisa, Zong and banks.
- Sample real formats: QR till payments ("Rs 70.00 paid to …, Till ID … via QR Code"), RAAST/IBFT transfers ("…successfully sent to … of IBAN No: ****6116"), NayaPay pushes ("Rs. 2 received from … Just the kind of notification we like.").
- Notification traffic includes heavy junk: OTPs, promos, balance messages, loan offers, data bundles.
- Challenge constraints: brand-new project; publish post planned 2026-10-17; deadline Mon 2026-10-19 11:59 AM PKT; bonus for landing a first real user.
- Repo may go public: all captured sample data stays sanitized (pseudonyms, masked account numbers).

## Capabilities and Constraints

- Capture: NotificationListenerService, global, dedupe identical text within 60s.
- Inference: on-device Gemma 3 1B (int4, ~557 MB) via LiteRT-LM; mediapipe tasks-genai as fallback. Desktop-validated pipeline: structural amount gate → one-word PAYMENT/OTHER (3 votes) → amount+direction extraction (3 votes) → deterministic reconciliation (verb roles, balance veto, OTP veto).
- Accuracy evidence: 28/28 real captures + 2/2 synthetic exact with 1B + reconciliation (4B scores 28/28 raw, zero overrides).
- Fail-safe principle: any uncertainty resolves to "not a transaction" — a missed payment is acceptable; showing junk or an OTP as a transaction is not.
- Privacy: OTP notifications are never displayed as transactions; corpus in the repo is sanitized; never read OTPs or personal messages off a user's phone.
- Scope exclusions (locked by the user): no merchant field, no categories, no budgets, no bank login, no cloud, no connection to any other app.
- Open decisions: applicationId packaging (com.gazette.app proposed); minSdk from LiteRT-LM AAR metadata; on-device latency (desktop: 15–30 s per transaction; lever: single vote).

## Brand Commitments

- Name: "Gazette" (locked 2026-10-14) — a globally readable word (the official-notice sense of "gazette") replacing the regional working title "Paisa Ledger".
- Pinned visual direction (user's words): clean, premium, minimal.
- Durable UI constraints from the user's prior app feedback: no model/AI wording anywhere in UI chrome; name actions instead of vague icons; one accent color; no per-state color clutter; cap badges/decoration.
- The model is an implementation detail the UI never mentions.

## Evidence on Hand

- `corpus/corpus_v1.txt` — 28 real sanitized notifications (10 transactions, 18 junk) + 2 synthetic sent-side pushes.
- `corpus/results_*.jsonl` — full vote logs for 1B and 4B, prompt v1→v4.
- `scripts/extract_test_v3.js`, `scripts/reconcile.js` — the validated pipeline; prompts are normative.
- `DESIGN_SPEC.md` — demo script, architecture, evidence table, risks, open items.
- No user testimonials, no benchmarks beyond the corpus, no pricing — do not fabricate.

## Product Principles

1. Fail safe, never fail loud: uncertainty means "not a transaction".
2. Zero setup: install, grant notification access, done — no accounts, no formats, no rules.
3. Offline by construction: no INTERNET permission is a feature, not a limitation.
4. Statement-grade legibility: the ledger should read like a well-set bank statement — amount and direction in under two seconds.
5. Demo-first: hackathon speed; polish happens after judges, not before.

## Accessibility & Inclusion

- WCAG AA contrast for all text (body ≥4.5:1).
- Tabular numerals and right-aligned amounts — legibility at a glance for all users.
- Touch targets 48dp minimum on anything interactive.
- Light theme only in v1 (daylight outdoor use is the scene); dark theme recorded as an open item.
