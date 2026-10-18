# 02 - Test which recipe fields the camera honours

Status: needs-testing
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

## Comments
