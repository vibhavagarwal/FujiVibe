# 06 - Full-resolution renders

Status: parked (2026-10-18: the user prefers applying recipes while shooting; see .scratch/camera-slots)
Type: research

The camera renders about 10 MP over USB from the phone, but 40 MP from X RAW STUDIO. See ADR 0013
and ticket 01 for what has been ruled out. Picked up only if 10 MP proves not good enough for the
user.

Options, in order: (1) capture X RAW STUDIO's USB traffic with Wireshark and USBPcap and diff it
against the phone's (the libfuji repo has a Wireshark dissector, docs/wireshark_dissector.lua);
(2) decode the RAF with LibRaw through the NDK for full-resolution exports and keep the camera for
previews.

## Comments
