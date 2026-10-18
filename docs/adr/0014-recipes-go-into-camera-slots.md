---
status: proposed
---

# Recipes reach the X-T5 by being written into its custom slots over USB

The user shoots on an X-T5 and wants a Fuji recipe applied as they shoot, so no RAW development is
needed afterwards. An X-T5 keeps seven custom settings, C1 to C7, each holding a full recipe (film
simulation, grain, Color Chrome, white balance, tone and so on). Apps such as Fuji X Weekly's and
FujiShare write them over USB.

**Decision:** FujiVibe gets a part that picks recipes from its recipe set (`app/src/main/assets/recipes.json`)
and writes them into the camera's slots over the same PTP connection built for ADR 0013, with the
camera in its USB RAW CONV./BACKUP RESTORE mode.

- **Read before write.** The camera's slot properties (0xD18C selects the slot, 0xD18D is its name,
  0xD18E to 0xD1A5 hold the settings) were taken from FilmKit's notes, from another camera, and some
  values differ on the X-T5 (a custom slot reads grain as 6 where the RAW recipe block says 1 for
  off). So the first step only reads the slots, with the user's known settings, to learn the encodings.
- **Back up first.** Before any write, every slot's raw values are saved; a restore puts them back.
  Writing only ever happens to a slot the user chose.
- **Verify by reading back** what was written, before telling the user it worked.
- **Settings the slot cannot hold** (for example ISO and exposure compensation, which recipes list
  as shooting advice) are shown, not written.
- The phone's own camera side (LUT looks on a phone photo) is unchanged.

**Consequences:**
- CONTEXT.md gains **Slot**.
- Seven slots mean the user chooses which seven recipes to carry; a recommendation engine (the
  recipe set already carries best-use tags and a recommendation text) could later suggest the seven
  for a trip or a scene.
- The protocol is unofficial and could change with camera firmware.
