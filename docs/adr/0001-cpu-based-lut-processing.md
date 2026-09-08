# CPU-based LUT processing instead of GPU/OpenGL

The PRD names GPUImage (an OpenGL/shader-based framework) as the intended processing engine, but Review only needs to re-render a static preview bitmap on each swipe, not a live video feed — so GPU shaders solve a problem this app doesn't have. We chose CPU-side trilinear interpolation over the `.cube` LUT, dispatched on `Dispatchers.Default` coroutines, instead. This avoids OpenGL context/surface lifecycle complexity and matches the threading model the PRD itself specifies, at the cost of being slower per-frame than a GPU shader would be.
