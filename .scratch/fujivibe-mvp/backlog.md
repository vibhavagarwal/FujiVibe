# FujiVibe post-v1 backlog

Ideas raised after v1 (tickets 01-04) was working end-to-end and verified on-device. None of these are `ready-for-agent` tickets yet — each needs a scoping/decision pass before implementation, unlike the numbered issues in `issues/`. Captured 2026-09-08, after ticket 04 (Export) was merged and verified.

## 1. Export shouldn't end the Review session

**What was asked:** after Export, the app currently jumps back to the Viewfinder (per spec/ticket 04's explicit design). The user wants to instead stay on Review and keep swiping, so they can export more than one look from the same Capture (e.g. save both Classic Neg. and Nostalgic Neg. from one shot).

**Why this isn't a small tweak:** the current spec deliberately makes Export end the Capture's lifecycle — see `CONTEXT.md`'s Export definition ("Ends the Capture's lifecycle"), story 19 ("clean up temp cache after Export or Discard"), and the "exactly one in-flight temp Capture, no queue/history" model in spec.md's Implementation Decisions. Ticket 04 was built and reviewed directly against "Export returns to the Viewfinder ready for the next shot, the same as Discard does" as an acceptance criterion.

**What would actually need to change:**
- Export can no longer delete the temp Capture immediately on success — it'd need to stay alive until the user explicitly signals they're done with it.
- A new, separate "I'm done with this shot" action is needed, since "return to Viewfinder" can no longer be an automatic side effect of Export.
- Multiple exports per Capture becomes a real case to design for — does re-exporting the same look twice save a duplicate file? Any cap?

**Recommendation:** treat as its own small ticket/spec amendment (a domain-modeling/grilling pass on the Capture lifecycle) rather than folding into an existing ticket, since it changes a contract other tickets already built against.

## 2. General UX rough spots

Rough-prioritized, from building/testing tickets 01-04:

- **Export gives no confirmation.** Silently returns to Viewfinder on success; on failure there's currently no UI signal at all that anything went wrong (story 20 wants the shot preserved on failure, but the user isn't told a failure happened — the Capture is just quietly still there with no explanation).
- **Swipe position isn't visible.** Only the text label ("Original" / "Classic Neg." / "Nostalgic Neg.") shows where you are in the cycle — a simple dot indicator (e.g. `●○○`) would make the 3-entry cycle easier to read at a glance.
- **Camera-permission-denied is a dead end.** If the user permanently denies camera permission, `ViewfinderScreen` just shows static text with no retry or "open Settings" affordance.
- **Full-resolution render latency.** CPU trilinear LUT interpolation is roughly ~1µs/pixel (see the `fujivibe-render-pipeline-perf` note from ticket 03/04) — a full-res Export can take 10-40+ seconds. Ticket 04 added a loading spinner to cover this, but it's worth deciding whether that wait is acceptable long-term or worth pipeline-parallelization work.
- **Visual polish is essentially default Material.** Plain text labels, stock buttons, no real branding/hierarchy — Export and Discard currently look equally weighted despite Export being the primary/happy-path action.

No decisions made on any of these yet — this file just records the discussion so it doesn't need to be re-derived.
