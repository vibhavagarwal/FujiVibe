# 03 - Develop screen: pick a RAF, try looks, Export

Status: ready-for-agent
Type: task
Blocked by: 01

The first real slice of "Develop RAFs with the camera" (spec.md), at 10 MP (see ADR 0013).

- An entry on the Viewfinder's top bar opens a Develop screen.
- It connects to the camera (USB permission, the PTP session), shows plain, specific messages when
  it cannot: no camera seen, camera asleep or off, wrong USB mode, phone not the USB host.
- The user picks a RAF from the phone (document picker). Show its embedded JPEG as an instant
  placeholder (it already has the camera's own simulation baked in, so label it).
- Swipe through looks (the 12 film simulations first), as Review does for Captures. Each look is
  rendered by the camera on demand, the neighbours rendered ahead, results cached per RAF and look.
- Pinch-zoom, and Export a look to the gallery and the Library through the existing Export path.
  Looks are named in the file as on the Camera side.
- Lift the spike's `com.fujivibe.camera` code into the app properly; keep the spike screen until
  this replaces it, then delete it.

Uses the glossary terms RAF and Recipe (CONTEXT.md); a look here is rendered by the camera, not by
a LUT, so keep the two apart in the UI as well.

## Comments
