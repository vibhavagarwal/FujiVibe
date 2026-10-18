# 03 - Write a Recipe into a chosen slot and read it back

Status: needs-testing
Type: task

Translate a Recipe (com.fujivibe.recipe) into slot values, write to a slot the user picked, read it
back and compare, and report any setting that did not take. Blocked by 01 and 02.

## Built (RAF Spike)

"Write to: C#" picks a slot, then a recipe button asks to confirm and writes: it reads and backs up
all seven slots first, refuses any slot that has a name, writes the name and the 24 settings, reads
each back and reports mismatches. To test: pick an unnamed slot (C5 to C7), write Pacific Blues,
look at the slot on the camera's menu, shoot a frame with it. Not yet run on a camera.

## First test on the camera, 2026-10-18

Writing Pacific Blues to C7 failed at property D193 (monochrome WC) with response 0x201C (invalid
device property value): a colour simulation refuses the monochrome colour properties, even with
their own value. C7 was left partly written (its name PACIFIC and the first few settings; a backup
was taken just before). Fixed: the writer now skips settings that already equal the slot's,
reports any the camera refuses and carries on, and a slot carrying this recipe's own name may be
written again. A Restore button puts a slot back from the oldest backup file.

Other settings are probably conditional the same way (FilmKit: colour temperature only in the
kelvin mode, colour on monochrome sims), so expect a few "Camera refused" lines to appear.

## Second test, 2026-10-18

Writing Pacific Blues to C7 worked: 12 settings written (the rest already matched), and every
property read back as written, including the 5800K kelvin white balance (mode 0x8007, D19C=5800).
One flaw: the white balance shifts (D19A R+1, D19B B-3) read 0 in a later read. A read straight
after each write had passed, so writing the temperature afterwards (it came after the shifts)
probably reset them. Fixed: mode, then temperature, then shifts; and the read-back now happens once,
after all the writing, so such side effects are caught.

## Comments
