# Roll Spec: shoot now, pick the look later

**Status:** done; checked on the device by the user (2026-10-12).

## Problem
Every shot opened Review and the next shot overwrote the Capture, so taking several photos in a row meant stopping to
pick a look after each one, or losing the shot.

## Decisions (from the user, 2026-10-12)
- Captures queue on a persistent **Roll**; the shutter keeps the Viewfinder up.
- Cap of **50**; a full Roll refuses new shots with a message.
- Entry point is the **thumbnail beside the shutter only** (no top-bar button).
- Auto-expiry after **30 days**.
- A top-bar toggle, **Develop after each shot**, for opening Review right after a shot (2026-10-12).

See `docs/adr/0012-roll-of-undeveloped-captures.md`.

## Tickets
- `issues/01-roll.md`
