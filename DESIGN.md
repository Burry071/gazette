---
name: Gazette
description: The statement — a phone-sized bank statement typeset with ledger precision; ink on paper, hairlines, tabular figures, one deep pine accent for money received.
colors:
  surface: "#F6F8F4"
  on-surface: "#161A17"
  on-surface-muted: "#5E6660"
  rule: "#E0E4DC"
  accent: "#0E4F34"
  accent-press: "#0A3B27"
  shimmer: "#C9CFC6"
typography:
  wordmark:
    fontFamily: Schibsted Grotesk
    fontSize: 14sp
    fontWeight: 600
    lineHeight: 1.2
  total:
    fontFamily: Schibsted Grotesk
    fontSize: 34sp
    fontWeight: 600
    lineHeight: 1.1
    letterSpacing: -0.01em
    fontFeature: tnum
  amount:
    fontFamily: Schibsted Grotesk
    fontSize: 17sp
    fontWeight: 600
    lineHeight: 1.2
    fontFeature: tnum
  label:
    fontFamily: Schibsted Grotesk
    fontSize: 11sp
    fontWeight: 600
    lineHeight: 1.2
    letterSpacing: 0.08em
  body:
    fontFamily: Schibsted Grotesk
    fontSize: 13sp
    fontWeight: 400
    lineHeight: 1.45
rounded:
  none: 0px
  shimmer: 2px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  2xl: 32px
  3xl: 48px
components:
  statement-head:
    backgroundColor: "{colors.surface}"
    padding: "{spacing.xl}"
  total-received:
    textColor: "{colors.accent}"
    typography: "{typography.total}"
  total-sent:
    textColor: "{colors.on-surface}"
    typography: "{typography.total}"
  micro-label:
    textColor: "{colors.on-surface-muted}"
    typography: "{typography.label}"
  closing-rule:
    backgroundColor: "{colors.rule}"
    height: 1px
  day-group:
    textColor: "{colors.on-surface-muted}"
    typography: "{typography.label}"
    padding: "{spacing.md}"
  ledger-row:
    backgroundColor: "{colors.surface}"
    padding: "{spacing.lg}"
    rounded: "{rounded.none}"
  row-amount-received:
    textColor: "{colors.accent}"
    typography: "{typography.amount}"
  row-amount-sent:
    textColor: "{colors.on-surface}"
    typography: "{typography.amount}"
  row-source-text:
    textColor: "{colors.on-surface-muted}"
    typography: "{typography.body}"
  reading-bar:
    backgroundColor: "{colors.shimmer}"
    rounded: "{rounded.shimmer}"
    height: 14px
  ledger-hairline:
    backgroundColor: "{colors.rule}"
    height: 1px
---

# Gazette

## Overview

Gazette turns Android payment notifications into a single statement. The
screen it ships is the statement itself: money received and money sent at the
top, then every detected payment newest-first, each with its original
notification text beneath it. Nothing else lives on the surface — no
navigation, no cards, no controls to learn.

The pinned direction (the user's words): **clean, premium, minimal**. The
world chosen for that brief is *the statement* — the typographic tradition of
a well-set bank statement, where hierarchy comes from scale, alignment, and
hairline rules instead of surfaces, color, or chrome. Premium reads as
precision: exact columns, tabular figures, an accounting rule closing the
totals, generous margins, and a palette of ink on paper with a single deep
pine accent reserved for money in.

The use scene forces light: a user standing in Karachi daylight checking
whether a payment landed. Sunlight rules out dark surfaces; the paper ground
(#F6F8F4) keeps it from reading as an unstyled white screen.

The reference shelf consulted (see DESIGN_SPEC.md for the technical spec):
Wise's DESIGN.md (via the awesome-design-md collection) for how a money
product structures accent tokens; Copilot Money as the premium-craft bar in
personal finance apps; MyFonts' and Type Network's figure-style references for
tabular numerals in money columns. WSJ's 2025 best-budgeting-apps roundup
confirmed the competitive field this statement look must not resemble
(dashboard-card mosaics).

## Colors

- **Surface (#F6F8F4) — "paper":** the whole screen. A near-white with the
  faintest green breath, so the accent belongs to the same family. No cards:
  rows sit directly on paper separated by hairlines.
- **On-surface (#161A17) — "ink":** all primary text and sent amounts. A
  near-black with a green cast, never pure #000.
- **On-surface-muted (#5E6660) — "ink-soft":** labels, times, the original
  notification text, the currency mark. Tinted from the ink family, not gray.
  Reads at ≈5.5:1 on paper (AA for body text).
- **Rule (#E0E4DC):** every hairline — row separators, the closing rules.
  Structure is drawn with 1px lines, never with fills or shadows.
- **Accent (#0E4F34) — "pine":** one hue, one meaning: money received —
  received totals, received amounts, the RECEIVED micro-label. Used nowhere
  else. Pressed/darker state: #0A3B27.
- **Shimmer (#C9CFC6):** the single transient element — the placeholder bar in
  a row still being read.

Money sent is deliberately **not** red. Sending money is not an error; the
distinction that matters (in vs out) is carried by the accent versus ink.

## Typography

One family, one voice: **Schibsted Grotesk** (OFL, bundled as static TTFs at
weights 400/500/600). Its modern-grotesque precision suits the statement
world, and it carries `tnum` — tabular figures — verified in the font's
OpenType feature set (aalt, calt, case, ccmp, dlig, dnom, frac, kern, liga,
locl, mark, mkmk, numr, ordn, pnum, sups, **tnum**, zero). Tabular figures are
non-negotiable: every amount in the column is a right-aligned number whose
digits must align vertically, like a printed statement. The platform font is
a runtime fallback only, never the design.

- **Total (34sp / 600 / tnum):** the two header figures. The largest type on
  the screen; the ledger's answer.
- **Amount (17sp / 600 / tnum):** per-row amounts, right-aligned in a single
  column that spans the list.
- **Body (13sp / 400):** the original notification text under each row. Muted.
- **Label (11sp / 600 / +0.08em / uppercase):** the clerical voice —
  RECEIVED, SENT, READING, day groups, the month. Tracking is wide because
  these are structural marks, not sentences.
- **Currency mark:** "Rs" is set inline at 0.62× the number's size, weight
  500, in ink-soft — the digits carry the emphasis, not the symbol.

Line lengths stay short on purpose (phone measure); the one long string — a
raw notification — may wrap to two lines and then truncates.

## Layout

A single column with 24px side margins. Top to bottom:

1. **Statement head:** wordmark "Gazette" (14sp/600) left, the month
   ("OCTOBER") as a micro-label right.
2. **Totals block:** RECEIVED and SENT as micro-labels above their 34sp
   figures, laid side by side, received first and leftmost.
3. **The closing rule — the signature.** Two 1px hairlines 3px apart span the
   full content width: the classic accounting double rule that closes a
   ledger's total. It is the one ornament on the screen, and it is authentic
   bookkeeping grammar, not decoration.
4. **The ledger list:** day groups (micro-label, "TODAY") separating
   hairline-ruled rows. Each row is two lines:
   - line 1: left — direction micro-label (RECEIVED in pine / SENT in
     ink-soft) and the time ("6:12 PM") in ink-soft; right — the amount.
   - line 2: the original notification text, 13sp ink-soft, up to two lines.
   Rows have 16px vertical padding and a 1px rule between them (no rule above
   the first row of a group, none after the last: a rule above the day label
   only when a new group starts).
5. **Transient reading row:** a new notification enters as a top row with
   READING as its micro-label and a shimmer bar in the amount slot; it either
   resolves into a real row or disappears.

The amount column is a fixed right-aligned anchor — the eye should be able to
scan it like a statement column. Vertical rhythm: 24 between blocks, 16 inside
rows; space above a day group is larger than space below it.

## Elevation & Depth

There is none, by decision. Depth is replaced by **rules and tonal
hierarchy**: hairlines carry structure, the ink/ink-soft split carries
hierarchy, and paper stays paper. No elevation, no cards, no fills, no
shadows — a statement is flat. Focus and press states at build time flip to
the pressed accent (#0A3B27) or a 4% ink wash, never to a shadow.

## Shapes

Radius is **zero** everywhere: the statement is print, not a rounded mobile
app. The single exception is the 2px on the reading shimmer bar, where a
sharp capsule would look like a glitch. Icons are absent from the main
surface; where one is unavoidable (system permission flow), it is a 1.5px
stroke line icon in ink, never filled or colored.

## Components

- **Statement-head** — wordmark + month label. No settings icon, no overflow;
  configuration lives in the notification-access grant, not here.
- **Totals block** — the two 34sp figures. Received figure and label in pine;
  sent figure in ink; labels above figures.
- **Closing rule** — the double hairline. Full content width, 3px gap.
- **Day group** — micro-label in ink-soft; above it 24px, below 8px.
- **Ledger row** — the core unit described in Layout. Received rows: pine
  amount, pine RECEIVED label. Sent rows: ink amount, ink-soft SENT label.
  The row's raw notification text is set exactly as received (never
  paraphrased, never model-written), truncated at two lines.
- **Reading row** — as described; shimmer is the only motion-bound element on
  the screen.
- **Empty state** — the statement with nothing in it: received and sent both
  "Rs 0", the closing rule still drawn, and one quiet sentence under the rule
  in ink-soft: "No payments yet. New payment notifications will appear here."
  The empty statement must still look typeset — rules and margin intact.

## Motion

Three moves, all quiet: a new row fades in over 160ms ease-out (opacity +
2px rise); the shimmer bar pulses its opacity on a 1.2s loop while reading;
the totals swap values with a 120ms crossfade. No bounces, no parallax, no
count-up animations — a statement does not perform.

## Do's and Don'ts

- Do keep every amount right-aligned in one column with tabular figures.
- Do keep pine for money received and nothing else.
- Do let whitespace and hairlines do the structuring.
- Do show OTP/junk notifications never — uncertain parses resolve to nothing
  (product principle), so the surface is silent by default.
- Don't use cards, rounded corners, shadows, or filled surfaces anywhere.
- Don't use red for sent money, or any second accent.
- Don't put model or AI wording in the UI — no "AI", "smart", "parsed by".
  The reading row says READING, and nothing explains it.
- Don't add badges, chips, avatars, merchant logos, or category icons —
  direction and amount are the entire row language.
- Don't use gradients, glass, or illustration. The statement is flat ink.
- Don't paraphrase notification text; it is evidence, shown verbatim.
- Don't introduce dark theme in v1 (open item; the scene is daylight).
