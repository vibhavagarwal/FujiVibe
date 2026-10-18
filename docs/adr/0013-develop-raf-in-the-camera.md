---
status: accepted
---

# RAF files are developed by the camera over USB, not by the app

The Camera side develops a phone JPEG with a LUT. The second part of the app takes **RAF** files
shot on a Fuji X-T5 and applies a **Recipe** to them. An X-T5 RAF is Fuji-specific X-Trans sensor
data that Android cannot decode, and the in-camera look (grain, Color Chrome, tone curves) is more
than a LUT can reproduce.

**Decision:** the app does not decode RAFs. It sends each one to the camera over USB, which renders
it with the recipe and returns a JPEG, the same way Fujifilm's X RAW STUDIO does.

- The phone is the USB host. The camera is in its USB RAW CONV. mode, in which it exposes no card,
  so the RAFs are picked from the phone's own storage and uploaded to the camera.
- The RAF is uploaded once; each look is then a recipe written to the camera and a trigger, so
  trying another look does not send the file again.
- The protocol is Fuji's private PTP extension (vendor operations 0x900C/0x900D, properties
  0xD183/0xD185), not a published API. It is ported from FilmKit and libfuji (both MIT), and a
  firmware update could change it.
- Verified: X RAW STUDIO on a PC develops this user's X-T5 RAFs (firmware 4.31) in acceptable
  time, and so does the spike on an Android phone over USB (`.scratch/raw-develop/issues/01-usb-develop-spike.md`):
  once a RAF is uploaded, each look renders and downloads in about 0.7 to 1 second, as a JPEG of
  about 4 MB. The X-T5 is USB product id 0x2FC.
- **Known limit: the rendered JPEG is about 10 MP (3888x2592, ~4.4 MB), not the camera's 40 MP
  (7728x5152).** X RAW STUDIO on the same camera gives full size, so the camera can. Over our
  conversation, the size field of the recipe block (parameter 3 in the camera's 625-byte block;
  the block has one more leading field than libfuji's 632-byte format) sets only the aspect ratio:
  every size code from 1 to 9 renders at S. Parameters 0 and 4 make no difference either. What
  X RAW STUDIO sends differently is unknown; capturing its USB traffic (Wireshark with USBPcap)
  and diffing it against ours would show it. Parked by the user (2026-10-18) until the 10 MP
  results prove not good enough. The fallback for full size is decoding the RAF ourselves (see below).
- Still to check: which recipe fields beyond the film simulation the camera honours (grain,
  Color Chrome, white balance shift, tone, exposure). Upload of a 45 MB RAF took about 1.9 s.

**Alternatives considered:**
- **LibRaw through the NDK plus the existing LUTs.** Works with no camera attached, but the look is
  an approximation, the X-Trans decode is a big dependency, and the LUTs would need a proper
  linear-to-F-Log step. Kept as the fallback if the USB route fails on Android.
- **The RAF's embedded JPEG.** It already has the camera's own simulation baked in, so it cannot
  be re-developed.

**Consequences:**
- `CONTEXT.md` no longer excludes RAW entirely: **RAF** is now in scope, and **Raw** still means
  only `.dng`.
- This part of the app needs the camera on a cable; the Camera side does not.
- Render time per look is the camera's, not the phone's: about one second after the first upload,
  so a slider can render on demand, with the neighbouring looks rendered ahead to feel instant.
- Results are 10 MP until the size limit above is solved.
