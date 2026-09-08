---
status: superseded by ADR-0004
---

# Apply F-Log-targeted LUTs directly to JPEG input, no pre-transform (for now)

**Superseded:** this ADR was written when the LUT source was assumed to be Fuji's official F-Log-targeted pack. That source was replaced (ADR 0003), and the v1 launch set was then narrowed to the two simulations that don't have a color-space mismatch problem at all (ADR 0004). Kept for history — the general "ship direct-apply, revisit if it looks wrong" posture carried forward into ADR 0004's reasoning even though the specific F-Log scenario described below no longer applies.

Fujifilm's official `.cube` files are built to convert F-Log (flat, log-encoded video) footage into each Film Simulation's look, not ordinary gamma-encoded JPEG stills. The technically correct approach would add a Rec.709(sRGB)→F-Log encoding curve before applying the LUT. For v1 we're applying the LUT directly to the JPEG's RGB values instead and judging the result by eye, because implementing the pre-transform is real color-science work and we don't yet know if it's necessary — some LUTs may look fine without it.

This is deliberately provisional: if the direct-apply output looks visibly wrong (crushed shadows, blown highlights) on real photos, revisit this decision and implement the pre-transform rather than tuning around the mismatch.
