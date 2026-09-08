# Product Requirement Document (PRD)
## Project: FujiVibe — Minimalist Fujifilm Simulation Camera App

### 1. Project Overview & Goal
FujiVibe is a streamlined, minimalist Android camera app designed for enthusiasts who love the Fujifilm color aesthetic. Instead of heavy real-time processing or complex text-based recipe configurations, this app focuses on a **"Shoot, Preview, and Apply"** workflow. Users capture a clean image and manually select authentic 3D LUT (Lookup Table) film simulations on a dedicated review screen before saving to their gallery.

---

### 2. Core User Workflow
1. **Launch:** App opens instantly into a clean, full-screen live camera viewfinder.
2. **Shoot:** User taps a minimalist shutter button; the app captures a high-quality JPEG and stores it in a temporary cache.
3. **Review & Simulate:** The app instantly opens a review screen displaying the captured image. A bottom carousel allows the user to tap and instantly preview different Fujifilm simulations (*Classic Chrome, Acros, Velvia, etc.*).
4. **Export:** User taps a checkmark/save button; the app processes the high-resolution image in the background, applies organic film grain adjustments, and saves the final file to the system gallery.

---

### 3. Technical Architecture & Constraints
To ensure successful "vibe coding" with AI models like Claude, the application is strictly partitioned to avoid threading deadlocks and memory management crashes.

* **Target Platform:** Android (Kotlin, Jetpack Compose, CameraX API).
* **Image Source:** **High-Quality JPEG only.** RAW (.dng) data is strictly excluded to optimize rendering performance, prevent memory allocation crashes, and simplify the code complexity.
* **Color Processing Engine:** Post-capture processing utilizing 3D Lookup Tables (LUTs). The application loads open-source `.cube` files directly from the `src/main/assets/luts/` directory.
* **Thread Separation:** Viewfinder layout lifecycle is kept completely separate from the image processing engine. Filter calculations run safely on background coroutines (`Dispatchers.Default`).

---

### 4. Feature Requirements

#### Feature 1: Clean Viewfinder Screen
* **Functional Requirements:**
  * Must request and verify Android Camera permissions upon initial launch.
  * Displays a full-screen, un-distorted live preview via CameraX.
  * Features a single tactile floating shutter button anchored at the bottom-center.
* **Vibe Coding Focus:** Keep this script entirely isolated from any filter processing pipelines to ensure stable performance.

#### Feature 2: Post-Capture Interactive Review Screen
* **Functional Requirements:**
  * Automatically transitions from the viewfinder once a temporary JPEG is generated.
  * Loads a memory-optimized, downscaled bitmap preview for instant rendering feedback.
  * Bottom horizontal scrolling selector displays available simulations.
* **Vibe Coding Focus:** Swapping filters must be non-blocking. Tapping a filter button recalculates the pixel matrix on the preview bitmap instantly.

#### Feature 3: 3D LUT Injection Engine
* **Functional Requirements:**
  * Reads `.cube` data tables stored locally within app assets.
  * Linearizes standard phone JPEG color matrices prior to applying LUT transformations.
  * Blends a procedurally generated noise overlay mask to simulate physical silver halide film grain structure.
* **Vibe Coding Focus:** Use established open-source matrix transformation frameworks (such as GPUImage for Android) to minimize complex code writing.

#### Feature 4: High-Resolution Export
* **Functional Requirements:**
  * Applies the selected 3D LUT configuration directly to the original high-resolution cached JPEG.
  * Automatically exports the finalized artifact into the public Android `Pictures/FujiVibe/` directory.
  * Refreshes the MediaStore indexer so the file is immediately visible in the system photo gallery.
  * Safely flushes the temporary cache directory to prevent local storage leaks.

---

### 5. Implementation Roadmap for AI-Assisted Development
To build this app safely without breaking configurations, execute prompts sequentially using this specific order:

* **Phase 1:** Project Initialization & Camera Viewfinder (Set up gradle configurations, permissions, and basic CameraX lifecycle).
* **Phase 2:** Shutter Capture & Caching (Save standard high-quality JPEGs to cache storage and navigate to a blank second screen).
* **Phase 3:** Review Layout & Local Asset Loading (Build the preview interface and load `.cube` files as static assets).
* **Phase 4:** Image Filter Pipeline (Implement background coroutines to process the active LUT selection over the bitmap matrix).
* **Phase 5:** High-Res Export & Media Storage (Apply transformations to original files, write to public directories, and flush temporary cache).

---

### 6. Superseded by grilling session (2026-09-07)

The following decisions from this PRD were revisited and changed during requirements grilling. See `CONTEXT.md` and `docs/adr/` for the resolved model:

* Feature 2's "bottom horizontal scrolling selector" → replaced with swipe-left/right-on-photo cycling, per user preference.
* Feature 3's film grain overlay → deferred past v1.
* Feature 3's GPUImage/OpenGL processing → replaced with CPU-based coroutine trilinear interpolation (see ADR 0001).
* Feature 3's "linearizes... prior to applying LUT" → for v1, LUTs are applied directly to JPEG RGB with no color-space pre-transform (see ADR 0002); the official Fuji LUTs used are F-Log-targeted, not designed for phone-JPEG input.
* Simulation source is now specifically Fujifilm's own official X-T5 `.cube` pack (user-downloaded), with a fixed 9-simulation launch set — not an open, unspecified "Classic Chrome, Acros, Velvia, etc." list.
