"""Isometric preview of a slice of the spawn island layout (PortCityLayout), for the building-by-building overhaul.

    1) export blocks:  java -cp <layout classes> net.get900.pixelpirates.world.gen.Export x1 y1 z1 x2 y2 z2 region.txt
    2) render:         python tools/render_layout_iso.py region.txt out.png [view 0-3] [scale]

view = which corner you look from (0 = south-east, 1 = south-west, 2 = north-west, 3 = north-east). Blocks are
approximated: colours from the block name, slabs/carpets/fences/lanterns drawn at their rough size.
"""
import sys, re
from PIL import Image, ImageDraw

COL = [  # (substring, rgb) - first match wins
    ("gold_block", (232, 190, 60)),
    ("mud_brick", (138, 104, 78)),
    ("dark_prismarine", (50, 95, 80)), ("prismarine", (95, 150, 135)), ("quartz", (236, 232, 226)),
    ("sandstone", (226, 212, 160)), ("thatch", (196, 164, 84)), ("calcite", (228, 228, 222)),
    ("water", (50, 100, 200)), ("blue_ice", (120, 170, 235)), ("packed_ice", (150, 190, 235)), ("ice", (170, 205, 240)),
    ("lantern", (255, 210, 110)), ("glowstone", (250, 215, 130)), ("sea_lantern", (190, 235, 225)), ("campfire", (230, 120, 40)),
    ("leaves", (60, 135, 50)), ("grass_block", (95, 155, 65)), ("grass", (95, 160, 70)), ("dirt_path", (150, 120, 70)),
    ("dirt", (120, 85, 55)), ("dark_oak_log", (60, 42, 25)), ("dark_oak", (78, 55, 32)), ("spruce_log", (70, 50, 30)),
    ("stripped_spruce", (115, 85, 50)), ("spruce", (112, 82, 50)), ("birch", (205, 190, 145)), ("jungle", (160, 115, 80)),
    ("mangrove", (120, 50, 45)), ("oak_log", (105, 85, 50)), ("oak", (165, 135, 85)), ("pirate_planks", (150, 110, 65)),
    ("palm", (175, 140, 90)), ("barrel", (130, 95, 55)), ("chest", (160, 115, 50)), ("crate", (150, 110, 60)),
    ("smoker", (90, 85, 80)), ("furnace", (110, 110, 110)), ("cauldron", (60, 60, 65)), ("anvil", (70, 70, 75)),
    ("chain", (80, 85, 95)), ("iron", (190, 190, 195)), ("polished_blackstone", (40, 38, 45)), ("blackstone", (35, 33, 38)),
    ("deepslate", (70, 70, 75)), ("mossy", (100, 120, 90)), ("cracked", (125, 125, 125)), ("stone_brick", (135, 135, 135)),
    ("andesite", (140, 140, 138)), ("diorite", (215, 215, 215)), ("granite", (160, 110, 90)), ("cobble", (120, 120, 120)),
    ("smooth_stone", (165, 165, 165)), ("stone", (128, 128, 128)), ("calcite", (225, 225, 220)), ("terracotta", (165, 95, 70)),
    ("brick", (150, 75, 60)), ("sandstone", (220, 205, 150)), ("sand", (220, 205, 150)), ("gravel", (130, 125, 120)),
    ("white_wool", (235, 235, 235)), ("red_wool", (180, 50, 45)), ("blue_wool", (55, 75, 170)), ("yellow_wool", (220, 200, 60)),
    ("wool", (200, 200, 200)), ("carpet", (190, 180, 160)), ("glass", (190, 220, 235)), ("bookshelf", (140, 100, 60)),
    ("hay", (210, 180, 60)), ("kelp", (60, 90, 40)), ("coral", (220, 110, 150)), ("bone", (225, 220, 200)),
    ("cobweb", (220, 220, 220)), ("flower", (230, 80, 120)), ("dandelion", (240, 220, 60)), ("poppy", (220, 40, 40)),
    ("dark_prismarine", (50, 95, 80)), ("prismarine", (95, 150, 135)), ("copper", (95, 160, 135)), ("tripwire", (150, 150, 150)),
    ("pixelpirates:", (175, 125, 90)),
]
THIN = ("fence", "wall_banner", "_wall", "bars", "chain", "pane", "rod", "torch", "banner", "hanging_rope", "hanging_net")
SMALL = ("lantern", "pot", "candle", "flower", "dandelion", "poppy", "daisy", "cornflower", "pickle", "skull", "head", "button", "trophy")


DYES = {"white": (235, 235, 235), "light_gray": (160, 160, 155), "gray": (75, 80, 85), "black": (30, 30, 35),
        "brown": (115, 75, 45), "red": (170, 45, 40), "orange": (235, 120, 30), "yellow": (245, 200, 50),
        "lime": (120, 190, 40), "green": (85, 110, 30), "cyan": (25, 140, 150), "light_blue": (70, 170, 215),
        "blue": (50, 60, 160), "purple": (120, 45, 170), "magenta": (190, 70, 180), "pink": (235, 140, 170)}
TERRACOTTA = {"white": (210, 180, 160), "light_gray": (135, 105, 95), "gray": (75, 55, 45), "black": (40, 25, 20),
              "brown": (80, 52, 36), "red": (145, 60, 45), "orange": (160, 85, 40), "yellow": (185, 135, 35),
              "lime": (105, 120, 55), "green": (75, 85, 40), "cyan": (85, 90, 90), "light_blue": (115, 110, 140),
              "blue": (75, 60, 90), "purple": (120, 70, 85), "magenta": (150, 90, 110), "pink": (160, 80, 80)}
WOODS = {"cherry": (225, 175, 170), "warped": (45, 135, 130), "crimson": (125, 55, 80), "mangrove": (120, 50, 45),
         "bamboo": (195, 175, 85), "acacia": (170, 90, 50), "calcite": (225, 225, 220)}


def colour(name):
    base = name.split("[")[0].replace("minecraft:", "")
    for d in sorted(DYES, key=len, reverse=True):
        if base.startswith(d + "_"):
            return TERRACOTTA[d] if "terracotta" in base else DYES[d]
    for w, c in WOODS.items():
        if base.startswith(w):
            return c
    for k, c in COL:
        if k in name:
            return c
    return (170, 130, 110)


def shape(name):
    """(x0, y0, x1, y1) in block units, footprint inset + height (z inset = x inset)."""
    base = name.split("[")[0]
    if "carpet" in base or "pressure_plate" in base or "snow" == base[-4:] or "trapdoor" in base and "open=false" in name:
        return (0, 0, 1, 0.12)
    if "slab" in base:
        return (0, 0.5, 1, 1) if "type=top" in name else (0, 0, 1, 0.5) if "type=bottom" in name else (0, 0, 1, 1)
    if any(k in base for k in SMALL):
        return (0.3, 0, 0.7, 0.45)
    if any(k in base for k in THIN):
        return (0.38, 0, 0.62, 1)
    return (0, 0, 1, 1)


def main():
    src, out = sys.argv[1], sys.argv[2]
    view = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    s = int(sys.argv[4]) if len(sys.argv) > 4 else 14
    blocks = {}
    for line in open(src, encoding="utf-8"):
        x, y, z, name = line.split(" ", 3)
        blocks[(int(x), int(y), int(z))] = name.strip()
    if not blocks:
        print("no blocks"); return
    solid = {p for p, n in blocks.items() if shape(n) == (0, 0, 1, 1) and "glass" not in n and "leaves" not in n}

    def rot(x, z):
        return [(x, z), (-z, x), (-x, -z), (z, -x)][view]

    # hide fully buried blocks (all six neighbours solid)
    vis = [p for p in blocks if not all(((p[0] + dx, p[1] + dy, p[2] + dz) in solid)
                                        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)))]
    pts = []
    for p in vis:
        rx, rz = rot(p[0], p[2])
        pts.append(((rx - rz) * s, (rx + rz) * s / 2 - p[1] * s))
    minx = min(a for a, b in pts) - 2 * s; maxx = max(a for a, b in pts) + 2 * s
    miny = min(b for a, b in pts) - 2 * s; maxy = max(b for a, b in pts) + 2 * s
    img = Image.new("RGB", (int(maxx - minx), int(maxy - miny)), (38, 44, 54))
    d = ImageDraw.Draw(img)

    def proj(x, y, z):
        rx, rz = rot(x, z)
        return ((rx - rz) * s - minx, (rx + rz) * s / 2 - y * s - miny)

    def order(p):
        rx, rz = rot(p[0], p[2])
        return (rx + rz, p[1])

    for p in sorted(vis, key=order):
        name = blocks[p]
        c = colour(name)
        x0, y0, x1, y1 = shape(name)
        # the block's box in world units, rotated corners: pick the 3 visible faces for this view by projecting all
        bx, by, bz = p
        X = (bx + x0, bx + x1); Y = (by + y0, by + y1); Z = (bz + x0, bz + x1)
        corner = lambda i, j, k: proj(X[i], Y[j], Z[k])
        faces = []
        # top
        top = [corner(0, 1, 0), corner(1, 1, 0), corner(1, 1, 1), corner(0, 1, 1)]
        # the two side faces that face the viewer: depends on view (which world +x/+z point toward screen bottom)
        fx = 1 if view in (0, 3) else 0          # x face toward viewer
        fz = 1 if view in (0, 1) else 0          # z face toward viewer
        side_x = [corner(fx, 0, 0), corner(fx, 0, 1), corner(fx, 1, 1), corner(fx, 1, 0)]
        side_z = [corner(0, 0, fz), corner(1, 0, fz), corner(1, 1, fz), corner(0, 1, fz)]
        sh = lambda f: tuple(int(v * f) for v in c)
        d.polygon(side_x, fill=sh(0.72), outline=sh(0.55))
        d.polygon(side_z, fill=sh(0.86), outline=sh(0.62))
        d.polygon(top, fill=sh(1.0), outline=sh(0.8))
    img.save(out)
    print("wrote", out, img.size)


if __name__ == "__main__":
    main()
