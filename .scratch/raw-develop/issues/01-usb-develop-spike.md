# 01 - USB develop spike

Status: resolved
Type: prototype

Prove that an Android phone can have an X-T5 develop a RAF over USB. Throwaway code: delete it, or
fold it into the real feature, once the answer is in.

## What was built

`com.fujivibe.camera`: `Ptp.kt` (containers, strings, ObjectInfo, DeviceInfo), `PtpConnection.kt`
(command/data/response over a `UsbPipe`), `FujiRawSession.kt` (open, upload a RAF, render with a
recipe), `RawConvProfile.kt` (the 0xD185 recipe block), `UsbPtpLink.kt` (Android USB host), and
`UsbSpikeActivity` (a second launcher icon, "RAF Spike"). The pure parts are unit-tested
(`app/src/test/.../camera`). Nothing in the real app calls any of it.

## How to try it

1. Install the debug APK. Copy a RAF from the X-T5 to the phone first (card reader mode or XApp).
2. Camera: set USB to RAW CONV. mode, battery charged, cable into the phone's USB-C port.
3. Open "RAF Spike". Tap Connect and accept the USB permission prompt.
4. Pick the RAF, then tap "As shot" or a film simulation.
5. Read the log: the camera's model and firmware, whether it lists the conversion operations, the
   recipe it read from the RAF, and the time for upload and for each render.

## What to report back

- Does Connect reach "X-T5 ... firmware ...", and do the three "Raw conversion" lines say yes?
- Does the recipe line show the film simulation the RAF was shot with (checks the field table)?
- Upload time, and render + download time for a look after the first.
- Any failure line, verbatim.

## Known risks in this code

- Chunk size (16 KB per USB transfer) and the empty-packet handling are untested on real hardware.
- The first read of a container assumes the camera sends the header in the first packet.
- FilmKit's film simulation codes come from an X100VI; an X-T5 may differ.

## Comments

## Answer

Works. On the user's Pixel, with the X-T5 (USB product id 0x2FC, firmware 4.31) in USB RAW CONV.
mode and the phone as USB host, Connect took 30 ms and showed all three conversion capabilities as
supported. A RAF picked from the phone was uploaded once; after that each film simulation
(Acros, Classic Neg., Eterna, Reala Ace and others) rendered and downloaded in about 0.7 to 1.0
seconds as a JPEG of about 4 MB.

Getting there took some USB host setup, which the real feature has to tell the user about: the
camera must be awake with its screen lit, well charged, and the phone must be the USB host
(Settings, Connected devices, USB, "USB controlled by: This device").

Follow-ups, 2026-10-18:
- Upload of a 45 MB RAF: about 1.9 s.
- The rendered JPEG is 10 MP (3888x2592), not 40 MP. X RAW STUDIO on the same camera produces
  7728x5152. The recipe block's size field (parameter 3, code 7 = L 3:2 in libfuji's table) only
  changes the aspect ratio; codes 1 to 9 all render at S. Parameters 0 and 4 have no effect.
  The 61 device properties were dumped and none is an obvious image-size setting. Next step, if
  needed: capture X RAW STUDIO's USB traffic with Wireshark and USBPcap and diff it against ours.
  Parked for now; see ADR 0013.
- Recipe fields besides the film simulation (grain, Color Chrome, exposure, tone) are not yet tested.
- A mistake to remember: the camera's native 625-byte block is libfuji's 632-byte layout shifted by
  one (size is parameter 3, quality 4, exposure bias 5, dynamic range 6, film simulation 8).
