"""PROPS - the telescope, the anchor, the map block (an open sea chart) and the framed sea chart (2026-10-02).

    python tools/gen_props_assets.py            # everything
    python tools/gen_props_assets.py tex|models|lang

tex   : textures/block/sea_chart (32x32: parchment, islands, a compass rose, a dotted course, the X), parchment (16x16)
models: telescope (brass tube on a tripod, angled up), anchor_block (3D: ring, wooden stock, shank, crown, arms + flukes),
        map_block (a chart lying open: weights, a rolled chart, dividers, inkwell + quill), sea_chart (a framed chart on
        the wall) + blockstates by facing, item models, previews in tools/previews/props/
bottles: SHIPS IN BOTTLES (2026-10-04, the glassblower, townhouse #29): a green glass bottle on a wooden cradle with a
        tiny ship inside - sloop (oak, one mast), brig (dark oak, two masts, red pennant), galleon (dark oak + gilt, three
        masts, red-cross sails), ghost ship (loot only: drowned grey hull, torn glowing sails). Textures bottle_glass,
        ghost_hull, ghost_sail; translucent render layer (HomesteadClient).
"""
import json, math, random, sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import BM, RES, item_model  # noqa: E402
from gen_tavern_assets import noise, facing_states  # noqa: E402

TEX = RES / "textures"
PREV = Path(__file__).parent / "previews/props"


def save(img, rel):
    p = TEX / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)
    print("wrote", rel)


def tex():
    # THE SEA CHART: aged parchment, two islands with a reef, rhumb lines from a compass rose, a dotted course to the X
    im = Image.new("RGBA", (32, 32))
    noise(im, (0, 0, 32, 32), (222, 198, 150), 6, 91)
    d = ImageDraw.Draw(im)
    r = random.Random(92)
    for _ in range(18):                                                             # foxing
        x, y = r.randrange(32), r.randrange(32)
        im.putpixel((x, y), (196, 168, 118, 255))
    for x in range(32):                                                             # browned edges
        for y in (0, 31):
            im.putpixel((x, y), (150, 116, 70, 255))
    for y in range(32):
        for x in (0, 31):
            im.putpixel((x, y), (150, 116, 70, 255))
    sea, land, ink = (190, 196, 170, 255), (178, 150, 96, 255), (86, 60, 40, 255)
    for i in range(0, 32, 8):                                                       # rhumb lines
        d.line([(5, 25), (i, 0)], fill=(205, 182, 136, 255))
        d.line([(5, 25), (31, i)], fill=(205, 182, 136, 255))
    d.polygon([(19, 4), (25, 3), (28, 7), (26, 12), (21, 11), (18, 8)], fill=land, outline=ink)    # the big island
    d.polygon([(8, 7), (12, 6), (13, 10), (9, 11)], fill=land, outline=ink)                        # the small island
    for x, y in ((14, 14), (16, 15), (15, 17)):
        d.point((x, y), fill=ink)                                                   # a reef
    d.line([(24, 8), (25, 6)], fill=(70, 120, 60, 255))                             # a palm on it
    for i, (x, y) in enumerate(((7, 24), (10, 22), (13, 21), (16, 21), (19, 19), (21, 16), (23, 13))):
        if i % 2 == 0:
            d.point((x, y), fill=(150, 40, 30, 255))                                # the dotted course
    d.line([(22, 8), (24, 10)], fill=(170, 30, 30, 255)); d.line([(24, 8), (22, 10)], fill=(170, 30, 30, 255))   # X
    cx, cy = 5, 25                                                                   # the compass rose
    d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], outline=ink)
    d.line([(cx, cy - 4), (cx, cy + 4)], fill=ink); d.line([(cx - 4, cy), (cx + 4, cy)], fill=ink)
    d.point((cx, cy - 4), fill=(170, 30, 30, 255))
    save(im, "block/sea_chart.png")

    # parchment for the rolled chart and the paper edges
    im = Image.new("RGBA", (16, 16))
    noise(im, (0, 0, 16, 16), (226, 206, 160), 5, 93)
    d = ImageDraw.Draw(im)
    for y in (3, 8, 13):
        d.line([(0, y), (15, y)], fill=(200, 176, 128, 255))
    save(im, "block/parchment.png")


IRON, WOOD = "minecraft:block/anvil", "minecraft:block/spruce_planks"
BRASS, BAND = "minecraft:block/cut_copper", "minecraft:block/gold_block"


def models():
    built = []

    # --- THE TELESCOPE: a tripod, the mount, a brass tube tilted skyward to the north, the big lens, the eyepiece
    m = BM("telescope", {"leg": "minecraft:block/stripped_dark_oak_log", "mount": "minecraft:block/polished_blackstone", "brass": BRASS,
                         "band": BAND, "lens": "minecraft:block/light_blue_stained_glass", "eye": "minecraft:block/polished_blackstone",
                         "particle": BRASS})
    for axis, ang, f in (("x", 22.5, [7.5, 0, 7.5]), ("z", 22.5, [7.5, 0, 7.5]), ("z", -22.5, [7.5, 0, 7.5])):
        m.box(f, [f[0] + 1, 10, f[2] + 1], all="#leg", rot=(axis, ang, [8, 10, 8]))
    m.box([7, 9.5, 7], [9, 11.5, 9], all="#mount")
    tilt = ("x", 22.5, [8, 12.5, 8])
    m.box([6.75, 11, 1], [9.25, 13.5, 14], all="#brass", rot=tilt)
    for z in (3, 10):
        m.box([6.5, 10.75, z], [9.5, 13.75, z + 1], all="#band", rot=tilt)
    m.box([6.25, 10.5, -0.5], [9.75, 14, 1.5], faces={"north": "#lens", "south": "#band", "east": "#band", "west": "#band", "up": "#band", "down": "#band"}, rot=tilt)
    m.box([7.25, 11.5, 14], [8.75, 13, 16], all="#eye", rot=tilt)
    m.write()
    facing_states("telescope", {"": "pixelpirates:block/telescope"})
    item_model("telescope", "pixelpirates:block/telescope")
    built.append(m)

    # --- THE ANCHOR: standing on its crown, arms + flukes in the x-y plane, the wooden stock across it (along z)
    m = BM("anchor_block", {"iron": IRON, "wood": WOOD, "particle": IRON})
    m.box([7.25, 0, 7.5], [8.75, 1, 8.5], all="#iron")                              # the tip of the crown
    m.box([6, 1, 7], [10, 3, 9], all="#iron")                                       # the crown
    m.box([7, 3, 7], [9, 14, 9], all="#iron")                                       # the shank
    for s in (-1, 1):                                                                # arms stepping out, flukes
        c = 8 + s * 3
        m.box([min(c, c - s * 2), 2, 7.25], [max(c, c - s * 2), 4, 8.75], all="#iron")
        c2 = 8 + s * 4
        m.box([min(c2, c2 - s * 2), 4, 7.25], [max(c2, c2 - s * 2), 6, 8.75], all="#iron")
        c3 = 8 + s * 6.5
        m.box([min(c3, c3 - s * 3), 6, 7], [max(c3, c3 - s * 3), 8, 9], all="#iron")
    m.box([7.25, 11.5, 1.5], [8.75, 13, 14.5], all="#wood")                         # the stock
    for z in (1, 14):
        m.box([7, 11.25, z], [9, 13.25, z + 1], all="#iron")                         # its iron caps
    m.box([6, 14, 7.5], [7, 16.5, 8.5], all="#iron")                                # the ring
    m.box([9, 14, 7.5], [10, 16.5, 8.5], all="#iron")
    m.box([6, 16.5, 7.5], [10, 17.5, 8.5], all="#iron")
    m.write()
    facing_states("anchor_block", {"": "pixelpirates:block/anchor_block"})
    item_model("anchor_block", "pixelpirates:block/anchor_block")
    built.append(m)

    # --- THE MAP BLOCK: a sea chart lying open - brass weights, a rolled chart, dividers, an inkwell + quill
    m = BM("map_block", {"chart": "pixelpirates:block/sea_chart", "paper": "pixelpirates:block/parchment", "brass": BRASS,
                         "iron": "minecraft:block/iron_block", "ink": "minecraft:block/black_concrete", "quill": "minecraft:block/white_concrete",
                         "particle": "pixelpirates:block/sea_chart"})
    m.box([1, 0, 1], [15, 0.5, 15], faces={"up": "#chart", "down": "#paper", "north": "#paper", "south": "#paper", "east": "#paper", "west": "#paper"},
          uv={"up": [0, 0, 16, 16]})
    m.box([1.5, 0.5, 1.5], [3, 1.75, 3], all="#brass")
    m.box([13, 0.5, 1.5], [14.5, 1.75, 3], all="#brass")
    m.box([2, 0.5, 12.5], [11, 2.25, 14.25], all="#paper")                         # a rolled chart
    m.box([1.75, 0.75, 12.75], [2, 2, 14], all="#brass"); m.box([11, 0.75, 12.75], [11.25, 2, 14], all="#brass")
    m.box([7.75, 0.5, 4], [8.25, 0.9, 10.5], all="#iron", rot=("y", 22.5, [8, 0.7, 7]))    # the dividers
    m.box([8.25, 0.5, 4], [8.75, 0.9, 10.5], all="#iron", rot=("y", -22.5, [8, 0.7, 7]))
    m.box([12, 0.5, 9], [14, 2.25, 11], all="#ink")                                 # the inkwell + quill
    m.box([12.75, 2.25, 9.75], [13.25, 6, 10.25], all="#quill", rot=("z", 22.5, [13, 2.25, 10]))
    m.write()
    facing_states("map_block", {"": "pixelpirates:block/map_block"})
    item_model("map_block", "pixelpirates:block/map_block")
    built.append(m)

    # --- THE SEA CHART: framed on the wall (front = north, the back against the wall at +z)
    m = BM("sea_chart", {"chart": "pixelpirates:block/sea_chart", "frame": "minecraft:block/dark_oak_planks", "particle": "minecraft:block/dark_oak_planks"})
    m.box([1, 1, 15], [15, 15, 16], faces={"north": "#chart", "south": "#frame", "east": "#frame", "west": "#frame", "up": "#frame", "down": "#frame"},
          uv={"north": [0, 0, 16, 16]})
    for f, t in (([0.5, 15, 14.5], [15.5, 16, 16]), ([0.5, 0, 14.5], [15.5, 1, 16]), ([0, 0, 14.5], [1, 16, 16]), ([15, 0, 14.5], [16, 16, 16])):
        m.box(f, t, all="#frame")
    m.write()
    facing_states("sea_chart", {"": "pixelpirates:block/sea_chart"})
    item_model("sea_chart", "pixelpirates:block/sea_chart")
    built.append(m)

    PREV.mkdir(parents=True, exist_ok=True)
    for b in built:
        b.preview(PREV / f"{b.name}.png")
    print("models:", len(built))


# ------------------------------------------------------------------------------------------------ SHIPS IN BOTTLES
def bottle_tex():
    # bottle glass: pale sea-green, mostly see-through, a bright highlight streak along the top and darker edges
    im = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            a = 70
            if y in (0, 15) or x in (0, 15):
                a = 130
            if y in (2, 3) and 2 <= x <= 13:
                a = 170
            g = (200, 236, 214) if a == 170 else (120, 186, 150)
            im.putpixel((x, y), g + (a,))
    save(im, "block/bottle_glass.png")
    # ghost hull: drowned grey-green planks with barnacle specks
    im = Image.new("RGBA", (16, 16))
    noise(im, (0, 0, 16, 16), (92, 108, 100), 8, 77)
    for y in range(0, 16, 4):
        for x in range(16):
            im.putpixel((x, y), (60, 72, 68, 255))
    r = random.Random(78)
    for _ in range(10):
        im.putpixel((r.randrange(16), r.randrange(16)), (170, 190, 176, 255))
    save(im, "block/ghost_hull.png")
    # ghost sail: pale cyan glow, torn (holes)
    im = Image.new("RGBA", (16, 16))
    r = random.Random(79)
    for x in range(16):
        for y in range(16):
            if r.random() < 0.12 or (y > 11 and r.random() < 0.4):
                continue
            v = 200 + r.randrange(40)
            im.putpixel((x, y), (int(v * 0.7), v, int(v * 0.95), 210))
    save(im, "block/ghost_sail.png")


BOTTLES = {   # name: hull, deck/trim, sail, masts [(x, top)], pennant colour (or None)
    "sloop": ("minecraft:block/oak_planks", "minecraft:block/spruce_planks", "minecraft:block/white_wool", [(7.5, 7)], None),
    "brig": ("minecraft:block/dark_oak_planks", "minecraft:block/spruce_planks", "minecraft:block/white_wool", [(6, 6.75), (9, 7.25)], "minecraft:block/red_wool"),
    "galleon": ("minecraft:block/dark_oak_planks", "minecraft:block/spruce_planks", "pixelpirates:block/galleon_sail", [(5, 6.5), (7.5, 7.25), (10, 6.75)], "minecraft:block/red_wool"),
    "ghost": ("pixelpirates:block/ghost_hull", "pixelpirates:block/ghost_hull", "pixelpirates:block/ghost_sail", [(5.5, 6.75), (8.5, 7.25)], None),
}


def galleon_sail():
    im = Image.new("RGBA", (16, 16))
    noise(im, (0, 0, 16, 16), (236, 226, 196), 4, 81)
    for i in range(16):
        im.putpixel((7, i), (176, 36, 32, 255)); im.putpixel((8, i), (176, 36, 32, 255))
        im.putpixel((i, 7), (176, 36, 32, 255)); im.putpixel((i, 8), (176, 36, 32, 255))
    save(im, "block/galleon_sail.png")


def bottles():
    built = []
    for name, (hull, trim, sail, masts, pennant) in BOTTLES.items():
        tx = {"glass": "pixelpirates:block/bottle_glass", "cork": "minecraft:block/stripped_oak_log", "cradle": "minecraft:block/dark_oak_planks",
              "hull": hull, "trim": trim, "sail": sail, "mast": "minecraft:block/stripped_spruce_log", "particle": "pixelpirates:block/bottle_glass"}
        if pennant:
            tx["pennant"] = pennant
        m = BM(f"ship_in_bottle_{name}", tx)
        # the cradle: two little saddles under the bottle
        for x in (3, 9.5):
            m.box([x, 0, 4.5], [x + 1.5, 1, 11.5], all="#cradle")
            m.box([x, 1, 4.5], [x + 1.5, 2, 5.5], all="#cradle"); m.box([x, 1, 10.5], [x + 1.5, 2, 11.5], all="#cradle")
        # the ship inside: hull, deck, raised stern, bowsprit, masts + sails
        m.box([3.5, 2.25, 7], [10.5, 3.5, 9], all="#hull")
        m.box([2.5, 2.75, 7.25], [3.5, 3.5, 8.75], all="#hull")
        m.box([10.5, 2.5, 7.5], [11.5, 3.5, 8.5], all="#hull")
        m.box([3.5, 3.5, 7.1], [10.5, 3.75, 8.9], all="#trim")
        if name == "galleon":                                                         # the gilt band
            m.textures["gilt"] = "minecraft:block/gold_block"
            m.box([3.5, 2.9, 6.95], [10.5, 3.2, 9.05], all="#gilt")
        m.box([2.75, 3.5, 7.25], [4.5, 4.5, 8.75], all="#hull")
        m.box([11.5, 3.25, 7.85], [12.75, 3.55, 8.15], all="#mast")
        for x, top in masts:
            m.box([x - 0.2, 3.75, 7.8], [x + 0.2, top, 8.2], all="#mast")
            m.box([x - 1.1, 4.25, 7.95], [x + 1.1, top - 0.6, 8.05], all="#sail")
        if pennant:
            x, top = max(masts, key=lambda mt: mt[1])
            m.box([x + 0.2, top - 0.6, 7.95], [x + 1.4, top - 0.2, 8.05], all="#pennant")
        # the bottle: body, shoulder, neck, cork - translucent glass round the ship
        m.box([1.5, 2, 5], [12.5, 7.5, 11], all="#glass")
        m.box([12.5, 3, 6], [13.5, 6.5, 10], all="#glass")
        m.box([13.5, 3.75, 6.75], [15, 5.75, 9.25], all="#glass")
        m.box([15, 4, 7], [16, 5.5, 9], all="#cork")
        m.write()
        facing_states(m.name, {"": f"pixelpirates:block/{m.name}"})
        item_model(m.name, f"pixelpirates:block/{m.name}")
        built.append(m)
    PREV.mkdir(parents=True, exist_ok=True)
    for b in built:
        b.preview(PREV / f"{b.name}.png", size=256)
    print("bottle models:", len(built))


# ------------------------------------------------------------------------------------------------ THE TATTOO CHAIR
def tattoo_chair():
    """The tattooist's chair (townhouse #33), rebuilt 2026-10-04 after the user's screenshots: no tilted elements (they read
    oddly in game) - a barber-style chair on an iron pedestal, a tufted oxblood leather seat, a back that reclines in
    steps, a headrest, padded armrests on iron brackets and a footrest. The sitter faces north (the back at +z)."""
    im = Image.new("RGBA", (16, 16))
    noise(im, (0, 0, 16, 16), (122, 30, 34), 6, 211)
    for x, y in ((3, 3), (11, 3), (7, 7), (3, 11), (11, 11)):                         # button tufts + their creases
        im.putpixel((x, y), (60, 14, 16, 255))
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            im.putpixel((x + dx, y + dy), (96, 22, 26, 255))
    for i in range(16):
        im.putpixel((i, 0), (150, 52, 52, 255)); im.putpixel((i, 15), (70, 16, 18, 255))  # piping
    save(im, "block/tattoo_leather.png")
    m = BM("tattoo_chair", {"leather": "pixelpirates:block/tattoo_leather", "iron": "minecraft:block/anvil",
                            "steel": "minecraft:block/iron_block", "wood": "minecraft:block/dark_oak_planks",
                            "particle": "pixelpirates:block/tattoo_leather"})
    m.box([3, 0, 3], [13, 1, 13], all="#iron")                                         # the base plate
    m.box([6.5, 1, 6.5], [9.5, 5, 9.5], all="#steel")                                  # the pedestal
    m.box([2, 5, 2], [14, 6, 13], all="#wood")                                         # the seat frame
    m.box([2.5, 6, 1.5], [13.5, 8.5, 12], all="#leather")                              # the cushion
    for i, (z1, z2, y1, y2) in enumerate(((12, 14.5, 6, 11), (12.75, 15.25, 11, 14), (13.5, 16, 14, 16.5))):
        m.box([2.5, y1, z1], [13.5, y2, z2], all="#leather")                           # the back, reclining in steps
    m.box([5, 16.5, 13], [11, 18.5, 16], all="#leather")                               # the headrest
    for x in (0.5, 13.5):
        m.box([x, 8.5, 3], [x + 2, 10, 11.5], all="#leather")                          # padded armrests
        m.box([x + 0.5, 6, 9.5], [x + 1.5, 8.5, 10.5], all="#steel")                   # on their brackets
    m.box([4, 3.5, -1], [12, 5, 1.5], all="#leather")                                  # the footrest
    m.box([7.25, 3.5, 1.5], [8.75, 5, 3], all="#steel")
    m.write()
    facing_states("tattoo_chair", {"": "pixelpirates:block/tattoo_chair"})
    item_model("tattoo_chair", "pixelpirates:block/tattoo_chair")
    PREV.mkdir(parents=True, exist_ok=True)
    m.preview(PREV / "tattoo_chair.png", size=256)
    m.preview(PREV / "tattoo_chair_back.png", size=256, yaw=145)
    print("tattoo chair")

# ------------------------------------------------------------------------------------------------ THE CATTERY COUNTER
def cattery_counter():
    """The ship's-cat keeper's counter (townhouse #36): a spruce counter with a birch top, a wicker basket with a cushion
    (a cat's bed for sale), a little price slate and a bowl. Faces north (the customer's side)."""
    m = BM("cattery_counter", {"wood": "minecraft:block/spruce_planks", "top": "minecraft:block/stripped_birch_log",
                               "wicker": "minecraft:block/hay_block_side", "cushion": "minecraft:block/red_wool",
                               "slate": "minecraft:block/black_concrete", "bowl": "minecraft:block/terracotta",
                               "particle": "minecraft:block/spruce_planks"})
    m.box([0, 0, 2], [16, 12, 14], all="#wood")
    m.box([0, 2, 1.5], [16, 3, 2], all="#top"); m.box([0, 9, 1.5], [16, 10, 2], all="#top")    # panel mouldings
    m.box([-0.5, 12, 1.5], [16.5, 13, 14.5], all="#top")                                       # the top
    m.box([1, 13, 4], [8, 15.5, 12], all="#wicker")                                            # the basket
    m.box([2, 14, 5], [7, 15.75, 11], all="#cushion")
    m.box([10, 13, 9], [15, 17, 9.75], all="#slate")                                           # the price slate
    m.box([11, 13, 4], [14, 14.25, 7], all="#bowl")                                            # a bowl
    m.write()
    facing_states("cattery_counter", {"": "pixelpirates:block/cattery_counter"})
    item_model("cattery_counter", "pixelpirates:block/cattery_counter")
    m.preview(PREV / "cattery_counter.png", size=256)
    print("cattery counter")


# ------------------------------------------------------------------------------------------------ CAPTAIN WREN'S MUSIC BOX
def music_box():
    """The Beach Wreck's easter egg (#45): a dark-oak box with brass corners and an inlaid band, the lid standing open at
    the back (red velvet inside), the brass cylinder + steel comb showing, a winding key on the side. Faces north."""
    m = BM("wren_music_box", {"wood": "minecraft:block/dark_oak_planks", "brass": "minecraft:block/gold_block",
                              "velvet": "minecraft:block/red_wool", "comb": "minecraft:block/iron_block",
                              "inlay": "minecraft:block/stripped_birch_log", "particle": "minecraft:block/dark_oak_planks"})
    m.box([3, 0, 4], [13, 5, 12], all="#wood")                                                 # the body
    m.box([4, 2, 3.8], [12, 3.25, 4], faces={"north": "#inlay"})                               # the inlaid band
    for x, z in ((2.8, 3.8), (12, 3.8), (2.8, 11), (12, 11)):
        m.box([x, 0, z], [x + 1.2, 5.5, z + 1.2], all="#brass")                                # brass corners
    m.box([4, 5, 5], [12, 5.25, 11], all="#velvet")                                            # the lining
    m.box([3, 5, 4], [13, 6.5, 5], all="#wood"); m.box([3, 5, 5], [4, 6.5, 11], all="#wood")   # the rim
    m.box([12, 5, 5], [13, 6.5, 11], all="#wood")
    m.box([5, 5.25, 8], [11, 7, 9.5], all="#brass")                                            # the pinned cylinder
    m.box([5, 5.25, 6], [11, 5.9, 7.5], all="#comb")                                           # the steel comb
    m.box([3, 5, 11], [13, 14.5, 12], all="#wood")                                             # the lid, standing open
    m.box([4, 6, 10.9], [12, 13.5, 11], faces={"north": "#velvet"})
    m.box([3, 14.5, 11], [13, 15, 12], all="#brass")
    m.box([13, 2.5, 7.5], [13.75, 3.5, 8.5], all="#brass")                                     # the winding key
    m.box([13.75, 1.5, 7], [14.25, 4.5, 9], all="#brass")
    m.write()
    facing_states("wren_music_box", {"": "pixelpirates:block/wren_music_box"})
    item_model("wren_music_box", "pixelpirates:block/wren_music_box")
    m.preview(PREV / "wren_music_box.png", size=256)
    print("music box")


LANG_E = {"block.pixelpirates.telescope": "Telescope", "block.pixelpirates.sea_chart": "Sea Chart",
          "block.pixelpirates.ship_in_bottle_sloop": "Ship in a Bottle (Sloop)", "block.pixelpirates.ship_in_bottle_brig": "Ship in a Bottle (Brig)",
          "block.pixelpirates.tattoo_chair": "Tattoo Chair", "block.pixelpirates.cattery_counter": "Cattery Counter",
          "block.pixelpirates.wren_music_box": "Captain Wren's Music Box",
          "block.pixelpirates.ship_in_bottle_galleon": "Ship in a Bottle (Galleon)", "block.pixelpirates.ship_in_bottle_ghost": "Ship in a Bottle (Ghost Ship)"}


def lang():
    p = RES / "lang/en_us.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    d.update(LANG_E)
    p.write_bytes(json.dumps(d, indent=2, ensure_ascii=False).encode("utf-8"))
    print("lang +", len(LANG_E))


if __name__ == "__main__":
    what = sys.argv[1:] or ["tex", "models", "bottles", "lang"]
    if "bottles" in what:
        bottle_tex(); galleon_sail(); bottles()
    if "chair" in what or "bottles" in what:
        tattoo_chair()
    if "counter" in what or "bottles" in what:
        cattery_counter()
    if "musicbox" in what or "bottles" in what:
        music_box()
    if "tex" in what:
        tex()
    if "models" in what:
        models()
    if "lang" in what:
        lang()
