"""THE WAVEBREAK GARDENS - the park's hanging sign (2026-10-02).

    python tools/gen_park_assets.py

Writes textures/block/park_sign_board.png (64x32: a palm on a green board, "GARDENS" / "WAVEBREAK"), the hanging-sign
model + blockstate + item model (shared builder from gen_tavern_assets) and the lang entry; preview in tools/previews/park/.
"""
import json, sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import RES  # noqa: E402
from gen_tavern_assets import noise, text, text_w, hanging_sign  # noqa: E402

PREV = Path(__file__).parent / "previews/park"


def tex():
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (40, 92, 56), 4, 81)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 31], outline=(20, 30, 18, 255))
    d.rectangle([1, 1, 62, 30], outline=(226, 196, 110, 255))
    d.rectangle([3, 3, 60, 28], outline=(150, 120, 60, 255))
    trunk, leaf, dark = (140, 100, 60, 255), (120, 200, 90, 255), (70, 140, 60, 255)
    for i, y in enumerate(range(26, 12, -2)):                                   # a leaning palm trunk
        d.rectangle([10 + i // 2, y, 11 + i // 2, y + 1], fill=trunk)
    for dx, dy in ((-6, 2), (6, 2), (-4, -2), (4, -2), (0, -3)):                # fronds
        d.line([(14, 12), (14 + dx, 12 + dy)], fill=leaf, width=2)
        d.point((14 + dx, 13 + dy), fill=dark)
    d.ellipse([12, 13, 14, 15], fill=(110, 70, 40, 255))
    d.line([(5, 27), (22, 27)], fill=(70, 150, 200, 255))                      # the pond
    cream, gold, sh = (240, 232, 200, 255), (236, 200, 90, 255), (16, 30, 16, 255)
    text(im, "GARDENS", 41 - text_w("GARDENS") // 2, 8, gold, 1, sh)
    text(im, "WAVEBREAK", 41 - text_w("WAVEBREAK") // 2, 18, cream, 1, None)
    p = RES / "textures/block/park_sign_board.png"
    im.save(p)
    print("wrote", p.name)


if __name__ == "__main__":
    tex()
    m = hanging_sign("park_sign")
    PREV.mkdir(parents=True, exist_ok=True)
    m.preview(PREV / "park_sign.png")
    lang = RES / "lang/en_us.json"
    d = json.loads(lang.read_text(encoding="utf-8"))
    d["block.pixelpirates.park_sign"] = "Gardens Sign"
    lang.write_bytes(json.dumps(d, indent=2, ensure_ascii=False).encode("utf-8"))
    print("lang + 1")
