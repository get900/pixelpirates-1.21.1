"""Wearable head items drawn by vanilla's HeadFeatureRenderer (2026-10-01): the Crown of the Drowned (rebuilt) and the
Leviathan Head. Item models in src/main/resources/assets/pixelpirates/models/item/, previews in tools/previews/.

    python tools/gen_wearables.py

Frame: an item worn on the head renders at 0.625 scale, so the player's head spans about x/z 1.6..14.4 and y 0..12.8
in model units; the FRONT of the face is the north (low z) side.
"""
import json, pathlib, random, sys
sys.path.insert(0, str(pathlib.Path(__file__).parent))
from blockmodels import BM, RES
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent
PREV = ROOT / "tools/previews"
TEX = RES / "textures/block"

DISPLAY_CROWN = {"head": {"translation": [0, 0, 0], "scale": [1, 1, 1]},
                 "gui": {"rotation": [30, 225, 0], "translation": [0, -4.5, 0], "scale": [0.7, 0.7, 0.7]},
                 "ground": {"translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
                 "fixed": {"translation": [0, -4, 0], "scale": [0.7, 0.7, 0.7]},
                 "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, -1, 2], "scale": [0.4, 0.4, 0.4]},
                 "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]}}


def write_item(m: BM, display: dict):
    p = m.write("item")
    d = json.loads(p.read_text(encoding="utf-8"))
    d["display"] = display
    d.pop("parent", None)
    p.write_bytes(json.dumps(d, indent=1).encode("utf-8"))


def crown():
    m = BM("crown_of_the_drowned", {"gold": "minecraft:block/gold_block", "verd": "minecraft:block/oxidized_cut_copper",
                                    "coral": "minecraft:block/tube_coral_block", "teal": "minecraft:block/prismarine",
                                    "pearl": "minecraft:block/calcite", "gem": "minecraft:block/sea_lantern",
                                    "kelp": "minecraft:block/dried_kelp_side", "particle": "minecraft:block/gold_block"})
    # the band: gold with a verdigris lower rim, four sides
    for f, t in (([1.5, 12, 1.5], [14.5, 15, 2.5]), ([1.5, 12, 13.5], [14.5, 15, 14.5]),
                 ([1.5, 12, 2.5], [2.5, 15, 13.5]), ([13.5, 12, 2.5], [14.5, 15, 13.5])):
        m.box(f, t, all="#gold")
    for f, t in (([1.3, 11.6, 1.3], [14.7, 12.4, 2.7]), ([1.3, 11.6, 13.3], [14.7, 12.4, 14.7]),
                 ([1.3, 11.6, 2.7], [2.7, 12.4, 13.3]), ([13.3, 11.6, 2.7], [14.7, 12.4, 13.3])):
        m.box(f, t, all="#verd")
    # the sea-gem on the brow, set in gold
    m.box([6.5, 12.5, 0.9], [9.5, 15.5, 1.5], all="#gold")
    m.box([7, 13, 0.6], [9, 15, 1.0], all="#gem")
    # eight points round the band: coral spires at the corners + front/back middle, gold points with pearls between
    spires = [(1.5, 1.5, 6, "#teal"), (12.5, 1.5, 6, "#teal"), (1.5, 12.5, 5, "#coral"), (12.5, 12.5, 5, "#coral"), (7, 12.5, 7, "#teal")]
    for x, z, h, tex in spires:
        m.box([x, 15, z], [x + 2, 15 + h * 0.5, z + 1], all=tex)
        m.box([x + 0.5, 15 + h * 0.5, z + 0.15], [x + 1.5, 15 + h, z + 0.85], all=tex)
        m.box([x + 0.7, 15 + h, z + 0.25], [x + 1.3, 15 + h + 0.6, z + 0.75], all="#pearl")             # a pearl atop
    for x, z in ((4.5, 1.5), (9.5, 1.5), (1.5, 6.5), (13.5, 6.5)):
        w = 1.0 if x in (1.5, 13.5) else 2.0
        d = 2.0 if x in (1.5, 13.5) else 1.0
        m.box([x, 15, z], [x + w, 18, z + d], all="#gold")
        m.box([x + w / 2 - 0.5, 18, z + d / 2 - 0.5], [x + w / 2 + 0.5, 19, z + d / 2 + 0.5], all="#pearl")
    # short pearl strings at the temples
    for x in (1.0, 14.2):
        m.box([x, 10.2, 5], [x + 0.8, 11.6, 5.6], all="#verd")
        m.box([x - 0.1, 9.4, 4.9], [x + 0.9, 10.2, 5.7], all="#pearl")
    write_item(m, DISPLAY_CROWN)
    return m


def hide_texture():
    """leviathan_hide.png: dark blue-black scales with a faint red rim on each scale."""
    rnd = random.Random(5)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            row = y // 4
            sx = (x + (2 if row % 2 else 0)) % 4
            sy = y % 4
            base = (22, 30, 40) if (sx + sy) > 1 else (40, 52, 66)
            if sy == 3: base = (70, 18, 22)                                   # red scale edge
            n = rnd.randint(-5, 5)
            px[x, y] = (max(0, base[0] + n), max(0, base[1] + n), max(0, base[2] + n), 255)
    img.save(TEX / "leviathan_hide.png")


def leviathan_head():
    hide_texture()
    m = BM("leviathan_head", {"hide": "pixelpirates:block/leviathan_hide", "bone": "minecraft:block/bone_block_side",
                              "eye": "minecraft:block/redstone_block", "dark": "minecraft:block/deepslate_tiles",
                              "particle": "pixelpirates:block/leviathan_hide"})
    m.box([0.5, 9.5, 0.5], [15.5, 15, 15.5], all="#hide")                      # skull cap over the head
    m.box([2.5, 10, -5.5], [13.5, 14.5, 0.5], all="#hide")                      # the upper jaw jutting over the brow
    m.box([3.5, 14.5, -4.5], [12.5, 15.5, 0.5], all="#dark")                    # snout ridge
    for i, x in enumerate((3, 4.6, 6.2, 7.8, 9.4, 11, 12.4)):                  # a row of teeth hanging from the jaw
        h = 2.2 if i % 2 == 0 else 1.4
        m.box([x, 10 - h, -5.3], [x + 0.9, 10, -4.4], all="#bone")
    for x in (2.6, 12.9):                                                       # side teeth
        for z in (-3.5, -1.5):
            m.box([x, 8.6, z], [x + 0.6, 10, z + 0.8], all="#bone")
    for x0, x1 in ((-0.5, 1.5), (14.5, 16.5)):                                  # cheek guards (lower jaw hinges)
        m.box([x0, 2.5, -2], [x1, 9.5, 9], all="#hide")
        m.box([x0, 1.8, -2.5], [x1, 3, 2], all="#bone")
    m.box([3.6, 11.2, -5.6], [5.4, 12.6, -5.4], all="#eye")                     # eyes glaring from the jaw
    m.box([10.6, 11.2, -5.6], [12.4, 12.6, -5.4], all="#eye")
    m.box([0.5, 3, 14.5], [15.5, 9.5, 16.5], all="#hide")                       # neck guard at the back
    for x, sgn in ((2, 1), (12, -1)):                                           # horns sweeping back
        m.box([x, 14, 9], [x + 2, 21, 11], all="#bone", rot=("x", -22.5, [x + 1, 14, 10]))
        m.box([x + 0.3, 20.5, 12], [x + 1.7, 24, 13.4], all="#bone", rot=("x", -45, [x + 1, 20.5, 12.5]))
    m.box([7.5, 15, 2], [8.5, 21, 14], all="#dark")                             # fin crest
    m.box([7.6, 21, 5], [8.4, 23, 12], all="#hide")
    write_item(m, {**DISPLAY_CROWN, "gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.6, 0.6, 0.6]}})
    return m


def main():
    PREV.mkdir(parents=True, exist_ok=True)
    c = crown(); c.preview(PREV / "crown_of_the_drowned.png")
    h = leviathan_head(); h.preview(PREV / "leviathan_head.png", yaw=-145, pitch=15)
    print("wrote crown_of_the_drowned, leviathan_head (+ previews)")


if __name__ == "__main__":
    main()
