# 02 - Test which recipe fields the camera honours

Status: in-progress
Type: prototype
Blocked by: 01

Before a Recipe can be more than a film simulation, find out which fields of the camera's
625-byte recipe block (property 0xD185) actually change a rendered result.

The RAF Spike has buttons for grain (strong, large), Color Chrome (strong), exposure +1 EV
(parameter 5) and highlight/shadow tone (parameters 16, 17, values times 10). Each result is saved
to Pictures/RAF Spike. Done when each field is marked as honoured or ignored below.

Still to find positions and encodings for: white balance mode and shift (12 to 15), colour (18),
sharpness (19), noise reduction (20, a lookup), clarity (27), Color Chrome FX Blue (25).

The spike now also has a button per researched recipe (15) and Paste recipe, which log any
settings not applied. Results saved to Pictures/RAF Spike, named after the RAF and recipe id.

## Results, 2026-10-18 (DSCF2196, compared with the camera's own JPEG, by eye and by pixel measurement)

| Field (parameter) | Result |
|---|---|
| Exposure bias (5; 1000 = +1 EV) | Confirmed: a large brightness increase |
| Shadow tone (17, times 10) | Confirmed: strong darkening in the shaded forest and reflections (the RAF had -1, the test sent +2) |
| Color Chrome Effect (10; 1 off, 2 weak, 3 strong) | Confirmed: deeper greens and reds, a darker image |
| Grain (9; 5 = strong large) | Probably applied: fine texture changes, hard to see at normal size; judge at 100% on a flat area |
| Highlight tone (16, times 10) | Not a valid test: the RAF already had -2, so sending -2 changed nothing. Retest with a different value |

Still untested: white balance mode, kelvin and the R/B shifts (12 to 15), Color (18), Sharpness (19),
Clarity (27), Color Chrome FX Blue (25), D Range Priority (7), and highlight with a value other
than the RAF's own. The recipe buttons in the spike exercise most of these at once.

## Comments
