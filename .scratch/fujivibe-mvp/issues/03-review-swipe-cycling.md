# 03: Review: swipe cycling between Original and the two Film Simulations

**What to build:** On the Review screen, swiping left advances through an ordered cycle of `[Original, Classic Neg., Nostalgic Neg.]`, swiping right retreats, and both directions wrap at the ends (last Film Simulation → Original → first Film Simulation) rather than dead-ending. A label on screen always names the current entry, including "Original". Each swipe renders its preview through the render pipeline from ticket 02, fast enough to feel instant rather than laggy. Loading a new Capture always resets the cycle to Original, regardless of whichever entry was showing the last time Review was open.

**Blocked by:** 01, 02

**Status:** done — implemented (9c6768a), reviewed via mattpocock-skills:code-review with a label/bitmap-desync fix applied (6fa1a40), verified on a real device by the user (2026-09-08). On-device testing surfaced one real bug not caught by JVM tests: the default preview size (1024px cap) landed at ~2040px/~3.1MP, making CPU LUT interpolation take ~3.2s/swipe — fixed by shrinking the default to ~480px/~510px-bracket (4c96fcf). Swiping and the Film Simulation color shift both confirmed working after the fix.

- [ ] Swiping left/right moves to the next/previous entry in `[Original, Classic Neg., Nostalgic Neg.]`
- [ ] The cycle wraps in both directions with no dead end
- [ ] An on-screen label always shows the name of the currently displayed entry
- [ ] Each entry's preview is rendered via the render pipeline's downscaled path and feels instant while swiping
- [ ] Opening Review for a new Capture always starts at Original, independent of prior Review state
- [ ] Render pipeline output is exercised through the UI without any UI-specific rendering shortcuts or duplicated LUT logic
