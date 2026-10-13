---
status: accepted
---

# A Roll of undeveloped Captures replaces the single in-flight Capture

Every shutter press opened Review, and the next shot overwrote the one before it, so a Capture had
to be developed (or thrown away) before the next could be taken. Shooting several photos in a row
meant stopping to pick a look after each one.

**Decision:** Captures queue on a **Roll** (see `CONTEXT.md`) and are developed later.

- A shutter press writes the JPEG to `filesDir/roll/<epoch-millis>.jpg` and stays on the Viewfinder.
  The id is the time taken, so a Capture's age needs no extra bookkeeping. App-private storage, not
  the cache dir, so the system can't clear it and the Roll survives process death.
- A thumbnail of the newest Capture, with a count, sits beside the shutter and opens the Roll (a
  grid). It is the only way in; there is no top-bar button. Tapping a Capture opens Review.
- A **"Develop after each shot"** toggle in the top bar (sparkle icon, amber when on, remembered across
  launches) brings back the old one-shot flow: Review opens on the new Capture straight away, and
  leaving it returns to the Viewfinder. Off (the default) is the batch flow above. Either way the shot
  is on the Roll first, so Back from that Review loses nothing.
- Review is unchanged for Export, Done and Discard (ADR 0009 still holds). Back leaves the Capture
  on the Roll, and which looks were already Exported is remembered while the app runs, so it isn't
  saved twice. Finishing a Capture returns to the Roll, so a batch can be developed in one sitting.
- **Limits:** 50 Captures; a full Roll refuses the shutter with a message rather than silently
  dropping a shot. Captures older than 30 days are deleted when the app starts, and a tile shows
  "Nd left" in its last 7 days. The rules live in `RollPolicy` and are unit-tested.

**Not decided / deferred:**
- Swiping from one Capture to the next inside Review.
- Which looks were Exported is not kept across the app being killed, so an Export of the same look
  could be repeated after a restart.
