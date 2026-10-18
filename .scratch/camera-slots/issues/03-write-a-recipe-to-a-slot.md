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

## Comments
