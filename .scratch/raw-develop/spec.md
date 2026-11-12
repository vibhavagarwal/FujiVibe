# Develop RAFs with the camera

Status: parked (2026-10-18). The user decided the recipe should be applied while shooting on the
X-T5, with X RAW STUDIO on a computer for RAW work; see .scratch/camera-slots. What was built here
(the PTP layer, the Recipe model and the 15 recipes, the RAF Spike) is the base for that.

A second part of the app. The user shoots on an X-T5, copies RAFs to the phone, plugs the camera
into the phone, and tries stored Recipes on them; the camera does the rendering (ADR 0013). A
liked result is saved at full resolution to the Library and the gallery.

## Flow (target)

1. RAFs on the phone (copied from the card in the camera's card-reader mode, or via the XApp).
2. Camera in USB RAW CONV. mode, on the cable.
3. Pick one or more RAFs, move through Recipes; each is rendered by the camera and shown.
4. Export the ones liked, through the existing Export path.

## Known constraints

- In USB RAW CONV. mode the camera exposes no card, so RAFs come from phone storage.
- The camera only develops RAFs from its own model.
- One render at a time; each takes seconds. Cache per RAF and Recipe; render neighbours ahead.
- ISO and exposure may not carry over in a Recipe (Fuji X Weekly found this for slot-writing apps).
- The protocol is unofficial (FilmKit, libfuji; both MIT) and could change with firmware.

## Answered by the spike (ticket 01)

- The camera renders from an Android USB host. After the first upload each look takes about 1 s,
  and uploading a 45 MB RAF about 2 s.
- The X-T5's recipe block is 625 bytes, FilmKit's layout for the X100VI, with the film simulation
  at parameter 8.
- The rendered JPEG is 10 MP (3888x2592), not 40 MP. Parked, ticket 06.

## Open questions

- Which Recipe fields the camera honours when developing (ticket 02).
- How the user's existing Recipes are stored on the phone, and how they should be imported (ticket 04).
- Whether 10 MP is enough (ticket 06).
