# 01 - Read the camera's slots and learn the encodings

Status: needs-testing
Type: prototype

The RAF Spike's Read slots button selects each slot C1 to C7, reads its name and the 24 settings
(0xD18E to 0xD1A5), logs them, saves a raw backup file, and puts the camera back on the slot it was on.
It writes nothing except the slot selector.

To learn the encodings, set a slot on the camera to known values (or use one already set), read it,
and compare. Done when each setting's encoding on the X-T5 is written below.

## Comments
