# 01: Roll of undeveloped Captures

**Status:** done; checked on the device by the user.

- [x] `CaptureStore` keeps many Captures in `filesDir/roll/<epoch-millis>.jpg`; `StoredCapture` is one of them
- [x] `RollPolicy` (cap 50, 30-day expiry, days left), unit-tested (`RollPolicyTest`)
- [x] Shutter stays on the Viewfinder; refuses with a toast when the Roll is full; a failed shot's partial file is deleted
- [x] Thumbnail + count beside the shutter (upright and sideways) opens the Roll
- [x] Roll grid (`ui/roll/RollScreen.kt`) with "Nd left" in a Capture's last 7 days
- [x] Review: back arrow and system back keep the Capture on the Roll; Done/Discard delete it and return to the Roll
- [x] Expired Captures purged on app start
- [x] "Develop after each shot" top-bar toggle (persisted): opens Review on the new shot, and leaving returns to the Viewfinder
- [x] `CONTEXT.md` defines **Roll**; ADR 0012
- [x] User check: take several shots in a row, open the Roll, develop two with different looks, back out of one and return to it
- [x] User check: the toggle on and off (icon amber when on, Review opens right after the shot, Back/Done return to the Viewfinder)
- [x] User check: thumbnail position in portrait and landscape, and the "Roll is full" message at 50
