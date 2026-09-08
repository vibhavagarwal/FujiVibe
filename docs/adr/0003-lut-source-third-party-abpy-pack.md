---
status: accepted
---

# LUT source: third-party abpy/FujifilmCameraProfiles pack, not Fuji's official X-T5 download

We assumed Fujifilm's official X-T5 `.cube` download covered the desired Film Simulations (Provia, Velvia, Astia, Classic Chrome, Reala Ace, Pro Neg. Hi, Pro Neg. Std, Classic Neg., Nostalgic Neg.). After actually downloading and inspecting it, that pack only contains 4 conversions per log format (F-Log/F-Log2): to ETERNA, to ETERNA Bleach Bypass, an F-Log passthrough, and a WDR gamma variant — it's built for grading log *video* footage, not a stills film-simulation library. None of the 9 launch simulations are in it; the only two looks it does provide (Eterna, Eterna Bleach Bypass) are the two we'd already decided not to need.

We're using the `abpy/FujifilmCameraProfiles` GitHub pack instead, which covers all 9 launch simulations (including Reala Ace, added in a November 2025 update) as `.cube` files. It's licensed CC-BY-NC-SA 4.0 — fine for this app's personal, noncommercial, sideloaded use, but it carries a real constraint: **if this app is ever distributed to anyone else, the bundled LUTs would need attribution and share-alike licensing, or re-sourcing entirely.** That's a live constraint on any future decision to publish, not just a historical note.

Acros is still not covered by this pack either — it remains a stretch goal with no identified source, unchanged from before.
