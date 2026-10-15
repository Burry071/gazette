---
version: 1
slug: "in-java-com-gazette-ui-statementscreen-kt-6bc518e4"
primary_target: "app/src/main/java/com/gazette/ui/StatementScreen.kt"
related_targets: []
---

# Surface brief — Statement screen (main ledger)

Scope: the app's single home screen. Mode: Operate.
Audience/job: anyone receiving payment notifications; job = know what money moved, in seconds.
Action: none beyond reading; reaching the screen is the outcome of the notification-access onboarding (separate surface).
Proof/content: real payment rows — amount, direction, time, verbatim source notification text; month totals on top.
Constraints: offline, no model/AI wording, one accent, no cards / rounded corners / shadows; light only (daylight scene); DESIGN.md tokens are law; OTPs and junk never render.
Memorable moment: the accounting double rule closing the totals; the shimmer READING row resolving into a real row.
Unresolved: dark theme (post-v1); permission-grant flow visuals; applicationId packaging.

## Direction contract

THESIS: The screen is a bank statement, not a feed — printed precision; it refuses the dashboard-card mosaic this category always ships.
OWN-WORLD: ink #161A17 on green-tinged paper #F6F8F4, hairlines #E0E4DC, one deep pine accent #0E4F34 for money-in only, Schibsted Grotesk with tabular figures, zero radius, zero shadow, zero cards.
STORY: In two seconds the user knows what came in and went out this month; they trust it because every row shows the raw notification text verbatim.
FIRST VIEWPORT: wordmark ("Gazette") + month micro-label; RECEIVED / SENT 34sp figures (received in pine); double closing rule; TODAY day group; rows with right-aligned tabular amounts over muted source text; one shimmer READING row at top.
FORM: the statement (typographic ledger); user-pinned direction — "clean, premium, minimal" — so no dice roll and no seed key; pinned direction beats the roll.
FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance.

## Approval

Approved by the user 2026-10-14: the name "Gazette" and both comps — `.impeccable/mocks/statement-screen.png` and `.impeccable/mocks/empty-state.png` (sidecar JSONs flipped to approved:true). Must not be literalized from the comps: wordmark scale (comp renders it over-scale; the DESIGN.md 14sp token is law), typeface (comp approximates Schibsted Grotesk; the real font gets bundled), and row content (illustrative sanitized sample data).
