"""The Shipwright's drafting table (2026-10-01, replaces the plain cube_bottom_top block).

    python tools/gen_shipwright_model.py   -> textures/block/shipwright_{blueprint,scroll,quill}.png,
                                             models/block/shipwright_table.json, blockstates/shipwright_table.json,
                                             models/item/shipwright_table.json, tools/previews/shipwright_table.png

A drafting table on four legs with stretchers and a lower shelf; on top a blueprint sheet lying askew, a half-built
sloop model (hull, mast, sail), rolled plans, an ink pot and a quill. Model faces NORTH (the paper toward the player
who placed it); blockstate rotates it by FACING. Hand-authored in resources/ - keep it OUT of ModModelProvider.
"""
import math, random, sys, pathlib
from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from blockmodels import BM, blockstate_facing, item_model, RES          # noqa: E402

TEX = RES / "textures/block"


def rgb(h):
    h = h.lstrip("#"); return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def textures():
    rnd = random.Random(5)
    # blueprint paper: blue with a grid + a white hull elevation
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = rgb("#24558c") if (x + y) % 5 else rgb("#2a5e98")
            if x % 4 == 0 or y % 4 == 0: base = rgb("#3a70aa")
            img.putpixel((x, y), (*base, 255))
    for x in range(2, 14):
        y = int(10 + 2.5 * math.sin((x - 2) / 11 * math.pi))
        img.putpixel((x, y), (230, 244, 255, 255))
    for x in range(2, 14): img.putpixel((x, 9), (230, 244, 255, 255))
    for y in range(3, 9): img.putpixel((8, y), (230, 244, 255, 255))
    for i in range(4): img.putpixel((9 + i, 4 + i), (200, 226, 255, 255))
    img.save(TEX / "shipwright_blueprint.png")
    # rolled plans: parchment with a red tie
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            c = rgb("#e8d4a4") if (y // 2) % 2 else rgb("#d8bc84")
            if 6 <= x <= 7: c = rgb("#9a1c1c")
            img.putpixel((x, y), (*c, 255))
    img.save(TEX / "shipwright_scroll.png")
    # quill feather (cutout)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y in range(16):
        w = max(0, 3 - abs(y - 7) // 3) if y < 13 else 0
        for x in range(8 - w, 9 + w):
            img.putpixel((x, y), (*(rgb("#f0ece0") if x != 8 else rgb("#8a7a60")), 255))
    for y in range(13, 16): img.putpixel((8, y), (*rgb("#3a2a1a"), 255))
    img.save(TEX / "shipwright_quill.png")


def model():
    m = BM("shipwright_table", {"top": "minecraft:block/dark_oak_planks", "leg": "minecraft:block/stripped_dark_oak_log",
                                "shelf": "minecraft:block/spruce_planks", "paper": "pixelpirates:block/shipwright_blueprint",
                                "hull": "minecraft:block/spruce_planks", "mast": "minecraft:block/stripped_spruce_log",
                                "sail": "minecraft:block/white_wool", "scroll": "pixelpirates:block/shipwright_scroll",
                                "ink": "minecraft:block/black_terracotta", "quill": "pixelpirates:block/shipwright_quill",
                                "brass": "minecraft:block/gold_block", "particle": "minecraft:block/dark_oak_planks"})
    for x in (1, 13):
        for z in (1, 13):
            m.box([x, 0, z], [x + 2, 12, z + 2], all="#leg")
    m.box([3, 3, 1.5], [13, 4.5, 2.5], all="#leg"); m.box([3, 3, 13.5], [13, 4.5, 14.5], all="#leg")
    m.box([1.5, 3, 3], [2.5, 4.5, 13], all="#leg"); m.box([13.5, 3, 3], [14.5, 4.5, 13], all="#leg")
    m.box([2.5, 4.5, 2.5], [13.5, 5.5, 13.5], all="#shelf")                                    # lower shelf
    m.box([3.5, 5.5, 4], [9.5, 7, 5.5], all="#scroll"); m.box([4, 5.5, 6], [10, 7, 7.5], all="#scroll")
    m.box([1, 10.5, 1], [15, 12, 15], all="#shelf")                                           # apron
    m.box([0, 12, 0], [16, 14, 16], all="#top")                                               # table top
    for x in (0, 15):                                                                          # brass corner caps
        for z in (0, 15):
            m.box([x, 13.5, z], [x + 1, 14.2, z + 1], all="#brass")
    # the blueprint, lying askew
    m.box([2, 14, 1.5], [12, 14.1, 9.5], faces={"up": "#paper"}, uv={"up": [0, 0, 16, 16]}, rot=("y", 22.5, [7, 14, 5.5]))
    # the half-built sloop at the back
    m.box([4, 14, 10.5], [11, 15.5, 13.5], all="#hull")
    m.box([11, 14.3, 11], [12.5, 15.3, 13], all="#hull")                                       # bow
    m.box([3, 14.2, 11], [4, 16, 13], all="#hull")                                             # stern castle
    m.box([7.2, 15.5, 11.6], [8, 22, 12.4], all="#mast")
    m.box([5, 16.5, 12], [10.4, 21.5, 12], faces={"north": "#sail", "south": "#sail"})
    m.box([8, 21.5, 11.8], [10, 22.5, 12.2], faces={"north": "#brass", "south": "#brass"})    # pennant
    # rolled plans, ink pot + quill
    m.box([12.5, 14, 6], [14, 15.5, 13], all="#scroll")
    m.box([12.7, 15.5, 7], [14.2, 17, 12], all="#scroll", rot=("y", -22.5, [13.4, 16, 9.5]))
    m.box([12.5, 14, 1.5], [14.5, 15.5, 3.5], all="#ink")
    m.box([13.5, 15, 2], [13.5, 20, 3], faces={"east": "#quill", "west": "#quill"}, uv={"east": [5, 0, 11, 16], "west": [5, 0, 11, 16]},
          rot=("x", -22.5, [13.5, 15.5, 2.5]))
    m.write()
    blockstate_facing("shipwright_table")
    item_model("shipwright_table", "pixelpirates:block/shipwright_table")
    m.preview(HERE / "previews/shipwright_table.png", size=380)


if __name__ == "__main__":
    textures()
    model()
    print("shipwright table written")
