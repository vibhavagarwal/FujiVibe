# 02 - Back up the slots, and restore them

Status: needs-testing
Type: task

Save every slot's raw values before any write, in a file the user can find, and restore them on
request. Blocked by 01.

Built in `SlotLoader` (every load backs up all seven slots first; `restore` writes C2 to C7 back).

## Comments
