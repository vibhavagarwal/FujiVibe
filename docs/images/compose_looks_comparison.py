"""Tiles the app's own renders of one scene into the README's looks-comparison image.

Steps (run from the repo root):

    python docs/images/synthetic_landscape.py scene.png
    ./gradlew :render:previewFilmSimulation --args="scene.png strip.png ORIGINAL,CLASSIC_NEG_PIXEL,NOSTALGIC_NEG_PIXEL,KODA64_PIXEL,CHARCOAL"
    python docs/images/compose_looks_comparison.py strip.png docs/images/looks-comparison.jpg "Original" "Classic Neg. (Pixel)" "Nostalgic Neg. (Pixel)" "Koda64 (Pixel)" "Charcoal"

`strip.png` is the preview tool's side-by-side output: one equally wide panel per name, left to right. The labels passed
here must be in that same order. The scene is computer-generated, so no personal photos are published.
"""
import sys
from PIL import Image, ImageDraw, ImageFont

PANEL_W, PANEL_H = 600, 400
GAP = 10
PER_ROW = 3
BACKGROUND = (26, 26, 26)


def load_font(size):
    for name in ("arial.ttf", "segoeui.ttf", "DejaVuSans.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main(strip_path, out_path, labels):
    strip = Image.open(strip_path).convert("RGB")
    count = len(labels)
    panel_source_w = strip.width // count
    font = load_font(22)

    rows = [labels[i:i + PER_ROW] for i in range(0, count, PER_ROW)]
    width = PER_ROW * PANEL_W + (PER_ROW - 1) * GAP
    height = len(rows) * PANEL_H + (len(rows) - 1) * GAP
    canvas = Image.new("RGB", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(canvas)

    index = 0
    for row_number, row in enumerate(rows):
        # A short last row is centred rather than left-aligned with a hole beside it.
        row_width = len(row) * PANEL_W + (len(row) - 1) * GAP
        x0 = (width - row_width) // 2
        y0 = row_number * (PANEL_H + GAP)
        for column, label in enumerate(row):
            panel = strip.crop((index * panel_source_w, 0, (index + 1) * panel_source_w, strip.height))
            panel = panel.resize((PANEL_W, PANEL_H), Image.LANCZOS)
            x = x0 + column * (PANEL_W + GAP)
            canvas.paste(panel, (x, y0))

            box = draw.textbbox((0, 0), label, font=font)
            text_w, text_h = box[2] - box[0], box[3] - box[1]
            draw.rectangle((x + 10, y0 + 10, x + 10 + text_w + 24, y0 + 10 + text_h + 20), fill=(0, 0, 0))
            draw.text((x + 22, y0 + 18 - box[1]), label, font=font, fill=(255, 255, 255))
            index += 1

    canvas.save(out_path, quality=92)
    print(f"Wrote {out_path} ({canvas.width}x{canvas.height}, {count} panels)")


if __name__ == "__main__":
    if len(sys.argv) < 4:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], sys.argv[3:])
