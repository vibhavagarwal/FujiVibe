"""Procedurally paints a synthetic landscape (no real photo) for the README demo."""
import sys
import numpy as np
from PIL import Image, ImageFilter

W, H = 1200, 800
rng = np.random.default_rng(7)
y, x = np.mgrid[0:H, 0:W].astype(np.float32)

def lerp(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return np.array(a, np.float32) * (1 - t) + np.array(b, np.float32) * t

def ridge(base, amp, freqs, seed):
    r = np.random.default_rng(seed)
    xs = np.arange(W, dtype=np.float32)
    h = np.full(W, base, np.float32)
    for f, a in zip(freqs, amp):
        h += a * np.sin(xs / W * f * 2 * np.pi + r.uniform(0, 6.28))
    return h

horizon = 470
# Sky: deep blue at top to pale warm near the horizon.
img = lerp((52, 110, 190), (238, 214, 180), (y / horizon) ** 1.3)
# Sun glow.
d = np.hypot(x - 880, y - 300)
img += (np.exp(-(d / 140) ** 2) * 60)[..., None] * np.array([1.0, 0.85, 0.55])
# Soft clouds.
cloud = np.zeros((H, W), np.float32)
for _ in range(14):
    cx, cy = rng.uniform(0, W), rng.uniform(60, 300)
    cloud += np.exp(-(((x - cx) / rng.uniform(80, 200)) ** 2 + ((y - cy) / rng.uniform(18, 40)) ** 2))
cloud = np.clip(cloud, 0, 1) * 0.75
img = img * (1 - cloud[..., None]) + np.array([248, 246, 242]) * cloud[..., None]

# Far mountains (hazy blue), near mountains (darker), with snowcaps.
far = ridge(330, [40, 18, 8], [1.3, 3.1, 7.7], 1)
mask = y > far[None, :]
img[mask] = lerp((120, 140, 170), (95, 115, 140), (y - 330) / 150)[mask]
snow = mask & (y < far[None, :] + 25) & (far[None, :] < 320)
img[snow] = np.array([235, 238, 245])
near = ridge(390, [35, 14, 6], [0.9, 2.3, 9.1], 2)
mask = y > near[None, :]
img[mask] = lerp((70, 88, 80), (48, 66, 52), (y - 390) / 100)[mask]

# Lake with reflected sky.
lake_top = horizon
mask = (y >= lake_top) & (y < 560)
refl = lerp((150, 175, 205), (70, 110, 160), (y - lake_top) / 90)
img[mask] = refl[mask]

# Green meadow foreground, warmer toward the bottom.
ground = ridge(560, [10, 4], [1.7, 6.3], 3)
mask = y > ground[None, :]
img[mask] = lerp((96, 150, 70), (130, 140, 52), (y - 560) / 240)[mask]
# Red wildflowers and yellow flowers.
for colour, n in (((200, 50, 45), 500), ((235, 200, 60), 350)):
    fx, fy = rng.uniform(0, W, n), rng.uniform(610, H, n)
    for px, py in zip(fx.astype(int), fy.astype(int)):
        s = 2 + int((py - 600) / 60)
        img[py - s:py + s, px - s:px + s] = colour

# Red cabin with a dark roof and a warm window.
img[505:560, 250:340] = (170, 45, 38)
for i in range(30):
    img[475 + i, 245 + i:345 - i] = (60, 45, 40)
img[520:540, 280:300] = (250, 200, 110)

# Grain.
img += rng.normal(0, 3.0, img.shape)
out = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(0.6))
out.save(sys.argv[1], quality=95)
