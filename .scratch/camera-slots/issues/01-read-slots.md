# 01 - Read the camera's slots and learn the encodings

Status: resolved
Type: prototype

The RAF Spike's Read slots button selects each slot C1 to C7, reads its name and the 24 settings
(0xD18E to 0xD1A5), logs them, saves a raw backup file, and puts the camera back on the slot it was on.
It writes nothing except the slot selector.

To learn the encodings, set a slot on the camera to known values (or use one already set), read it,
and compare. Done when each setting's encoding on the X-T5 is written below.

## Answer (2026-10-18, X-T5 firmware 4.31)

Read from the user's own slots (C1 DEFAULT, C2 PORTRA, C3 CC NEG, C4 KODA64; C5 to C7 empty). The
slots hold recipes the user had loaded, so each value was matched to the known recipe. Encodings of
each slot property (all 16-bit little-endian; negatives are two's complement):

| Property | Meaning | Encoding |
|---|---|---|
| D18C | slot select | 1 to 7 |
| D18D | name | PTP string, capitals and digits, up to 7 seen |
| D18E | image size | 7 = L 3:2 |
| D18F | image quality | 2 Fine, 4 seen with RAW + JPEG |
| D190 | dynamic range | 100, 200, 400 |
| D191 | D Range Priority | 0 off, **1 weak**, **2 strong**, **32768 (0x8000) auto** (each read from a slot the user set to it) |
| D192 | film simulation | same codes as the RAW block (1 Provia, 11 Classic Chrome, 17 Classic Neg.) |
| D193, D194 | monochrome WC, MG | times 10 (unconfirmed, no sample) |
| D195 | grain | 2 to 5 as in the RAW block, **off is 6** (1 in the block) |
| D196 | Color Chrome | 1 off, 2 weak, 3 strong |
| D197 | Color Chrome FX Blue | 1 off, 2 weak, 3 strong |
| D198 | smooth skin | 1 off |
| D199 | white balance mode | 2 auto, 4 daylight (as the RAW block) |
| D19A, D19B | WB shift R, B | signed, e.g. 65532 = -4 |
| D19C | colour temperature | K; 10000 in unused slots |
| D19D to D1A0 | highlight, shadow, color, sharpness | times 10, signed (65526 = -1) |
| D1A1 | high ISO NR | code: 0 is 8192 (0x2000), -4 is 32768 (0x8000); others unknown |
| D1A2 | clarity | times 10, signed |
| D1A3, D1A4, D1A5 | unknown | 1, 1, 7 in every slot |

`RecipeToSlotTest` rebuilds the three recipe slots from recipes and checks every property.


> The RAF Spike screen this ticket describes was removed on 2026-10-18 once the Camera recipes screen replaced it; it is in git history (last present at commit c78e633 on branch raw-develop).

## Comments
