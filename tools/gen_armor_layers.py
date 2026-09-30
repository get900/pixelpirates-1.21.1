"""Pixel Pirates - worn armor textures (the model on the PLAYER), 64x32 vanilla armor layout.

    python tools/gen_armor_layers.py            # all sets
    python tools/gen_armor_layers.py corsair

Writes assets/minecraft/textures/models/armor/<material>_layer_1.png (helmet, chestplate, boots) and
_layer_2.png (leggings). Vanilla resolves worn armor from the MINECRAFT namespace by ArmorMaterial
name - see CLAUDE.md. Also writes tools/armor_sheet.png (8x preview of every layer).

Layout (legacy 64x32 skin UVs; left limbs reuse the right-limb UVs mirrored):
  head  box 8x8x8   at (0,0)    body box 8x12x4 at (16,16)
  arm   box 4x12x4  at (40,16)  leg  box 4x12x4 at (0,16)
Face rects for a box at (u,v) size (w,h,d): top (u+d,v,w,d)  bottom (u+d+w,v,w,d)
  right/outer (u,v+d,d,h)  front (u+d,v+d,w,h)  left/inner (u+d+w,v+d,d,h)  back (u+2d+w,v+d,w,h)
Transparent pixels are simply not drawn (armor renders cutout), which is how helmets get a face
opening and boots stop at the ankle.
"""
from __future__ import annotations

import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "src/main/resources/assets/minecraft/textures/models/armor"


def hexc(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)


def shade(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3]) + (255,)


class Box:
    """One armor box (head / body / arm / leg) on a layer; faces addressed by name."""

    def __init__(self, img, u, v, w, h, d):
        self.img, self.u, self.v, self.w, self.h, self.d = img, u, v, w, h, d
        self.faces = {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "outer": (u, v + d, d, h),
                      "front": (u + d, v + d, w, h), "inner": (u + d + w, v + d, d, h), "back": (u + 2 * d + w, v + d, w, h)}

    def put(self, face, x, y, c):
        fx, fy, fw, fh = self.faces[face]
        if 0 <= x < fw and 0 <= y < fh:
            self.img.putpixel((fx + x, fy + y), c if len(c) == 4 else c + (255,))

    def clear(self, face, x, y):
        fx, fy, fw, fh = self.faces[face]
        if 0 <= x < fw and 0 <= y < fh:
            self.img.putpixel((fx + x, fy + y), (0, 0, 0, 0))

    def size(self, face):
        return self.faces[face][2:]

    def sides(self):
        return ("outer", "front", "inner", "back")


LIGHT = {"top": 1.12, "bottom": 0.72, "front": 1.0, "back": 0.86, "outer": 0.9, "inner": 0.8}


def material(ramp, rng, blot=2):
    """Clustered-noise painter over a dark->light ramp (Minecraft texture feel)."""
    cols = [hexc(c) for c in ramp]
    cache = {}

    def pick(face, x, y):
        key = (face, x // blot, y // blot)
        if key not in cache:
            cache[key] = rng.random()
        t = cache[key] * 0.7 + rng.random() * 0.3
        return cols[min(len(cols) - 1, int(t * len(cols)))]
    return pick


def paint_box(box, mat, rows=None, faces=None, outline=0.72, light=True):
    """Fill faces (optionally only rows y0..y1 counted from the TOP of the side faces) with a material,
    darkening the face border for a crisp, plated look."""
    faces = faces or list(box.faces)
    for f in faces:
        fw, fh = box.size(f)
        for y in range(fh):
            if rows and f not in ("top", "bottom") and not (rows[0] <= y <= rows[1]):
                continue
            for x in range(fw):
                c = mat(f, x, y)
                k = LIGHT[f] if light else 1.0
                edge = x in (0, fw - 1) or y in (0, fh - 1) or (rows and f not in ("top", "bottom") and y in rows)
                if outline and edge and fw > 2:
                    k *= outline
                box.put(f, x, y, shade(c, k))


def art(box, face, rows, pal, x0=0, y0=0):
    """Pixel art onto a face: '.' = leave, '_' = transparent, other chars from pal (hex); shading applied."""
    k = LIGHT[face]
    for yy, row in enumerate(rows):
        for xx, ch in enumerate(row):
            if ch == ".":
                continue
            if ch == "_":
                box.clear(face, x0 + xx, y0 + yy)
                continue
            col = pal[ch]
            bright = col.startswith("!")
            c = hexc(col.lstrip("!"))
            box.put(face, x0 + xx, y0 + yy, c if bright else shade(c, k))


def hline(box, faces, y, col, every=None, offset=0):
    for f in faces:
        fw, fh = box.size(f)
        for x in range(fw):
            if every is None or (x + offset) % every == 0:
                box.put(f, x, y if y >= 0 else fh + y, shade(hexc(col), LIGHT[f]))


def vline(box, face, x, y0, y1, col):
    for y in range(y0, y1 + 1):
        box.put(face, x, y, shade(hexc(col), LIGHT[face]))


def dots(box, faces, pts, col):
    for f in faces:
        for (x, y) in pts:
            box.put(f, x, y, shade(hexc(col), LIGHT[f]))


def layers():
    l1 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    l2 = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    return l1, l2, {
        "head": Box(l1, 0, 0, 8, 8, 8), "body": Box(l1, 16, 16, 8, 12, 4), "arm": Box(l1, 40, 16, 4, 12, 4),
        "boot": Box(l1, 0, 16, 4, 12, 4), "waist": Box(l2, 16, 16, 8, 12, 4), "leg": Box(l2, 0, 16, 4, 12, 4)}


def boots(b, mat, top_row=7, cuff=None, sole="#1a1412", buckle=None):
    """Boots: only the bottom of the leg box (transparent above top_row), dark sole, optional cuff/buckle."""
    paint_box(b, mat, rows=(top_row, 11), faces=["outer", "front", "inner", "back"])
    paint_box(b, mat, faces=["bottom"])
    hline(b, b.sides(), 11, sole)
    if cuff:
        hline(b, b.sides(), top_row, cuff)
    if buckle:
        b.put("front", 1, top_row + 2, hexc(buckle)); b.put("front", 2, top_row + 2, hexc(buckle))


def waist(b, mat, rows=(8, 11)):
    """Leggings waist on layer 2: only the bottom rows of the body box."""
    paint_box(b, mat, rows=rows, faces=["outer", "front", "inner", "back", "bottom"])


# =====================================================================================
# THE SETS
# =====================================================================================
def castaway(rng):
    l1, l2, B = layers()
    STRAW = material(["#9a7a3a", "#b08c46", "#c49e52", "#d4b062"], rng, 1)
    SACK = material(["#8a7454", "#9a8260", "#a8906c", "#b49c78"], rng)
    PATCH = material(["#6a4a2e", "#7a5636"], rng)
    ROPE = "#c8b07a"
    # straw hat: crown + band (no brim possible on the head box - the band sells it)
    h = B["head"]
    paint_box(h, STRAW, faces=["top"], outline=0.85)
    for f in h.sides():
        for y in range(0, 4):
            for x in range(8):
                h.put(f, x, y, shade(STRAW(f, x, y), LIGHT[f] * (0.9 if (x + y) % 2 else 1.0)))
        hline(h, [f], 3, "#5a3a22")
    art(h, "front", ["________"] * 4, {}, 0, 4)                           # open face
    for f in ("outer", "inner", "back"):
        art(h, f, ["________"] * 4, {}, 0, 4)
    # sackcloth vest, open front, rope belt with a knot
    bd = B["body"]
    paint_box(bd, SACK)
    art(bd, "front", ["..____..", "..____..", "...__...", "...__...", "........", "........",
                      "........", "........", "rrrrrrrr", "rrrRRrrr", "....R...", "........"],
        {"r": ROPE, "R": "#9a8050"})
    for f in ("outer", "inner", "back"):
        hline(bd, [f], 8, ROPE); hline(bd, [f], 9, ROPE)
    for (x, y) in ((1, 5), (2, 5), (1, 6), (2, 6)):
        bd.put("back", x, y, shade(PATCH("back", x, y), 0.86))
    # sleeves: short, frayed
    a = B["arm"]
    paint_box(a, SACK, rows=(0, 4), faces=["outer", "front", "inner", "back", "top"])
    for f in a.sides():
        a.clear(f, 1, 4); a.clear(f, 3, 4)
    # rag-wrapped feet
    boots(B["boot"], material(["#7a6a50", "#8a7a5c", "#9a8a68"], rng), top_row=8, sole="#4a3a28")
    hline(B["boot"], B["boot"].sides(), 9, "#5a4a36")
    # patched trousers, rope at the waist, torn at the shin
    lg = B["leg"]
    paint_box(lg, SACK, rows=(0, 8))
    for f in lg.sides():
        lg.clear(f, 0, 8); lg.clear(f, 2, 8)
    for (x, y) in ((1, 3), (2, 3), (1, 4), (2, 4)):
        lg.put("front", x, y, shade(PATCH("front", x, y), 1.0))
    waist(B["waist"], SACK, rows=(9, 11))
    hline(B["waist"], B["waist"].sides(), 9, ROPE)
    return l1, l2


def pirate_armor(rng):
    l1, l2, B = layers()
    RED = material(["#6e1616", "#8a1e1e", "#a02626", "#b83030"], rng)
    LEATHER = material(["#3e2616", "#4c301c", "#5a3a22", "#684428"], rng)
    DARK = material(["#1e1a1c", "#262124", "#2e282b"], rng)
    GOLD = "#e0b84a"
    h = B["head"]
    paint_box(h, RED)
    for f in ("front", "outer", "inner"):
        art(h, f, ["________"] * 5, {}, 0, 3)
    art(h, "front", ["RrRrRrRr", "rrrrrrrr", "RRRRRRRR"], {"R": "#6e1616", "r": "#a02626"})
    art(h, "back", ["........", "........", "........", "...kk...", "..kkkk..", "...kk...", "..k..k..", ".k....k."],
        {"k": "#8a1e1e"})                                                  # knot + trailing tails
    bd = B["body"]
    paint_box(bd, LEATHER)
    art(bd, "front", ["LL.ww.LL", "L..ww..L", "L..ww..L", "L..ww..L", "L.gwwg.L", "L..ww..L", "L.gwwg.L", "L..ww..L",
                      "bbbGGbbb", "bbbGgbbb", "L......L", "L......L"],
        {"L": "#3e2616", "w": "#d8d0c0", "g": GOLD, "b": "#1e140e", "G": GOLD})
    hline(bd, ["outer", "inner", "back"], 8, "#1e140e"); hline(bd, ["outer", "inner", "back"], 9, "#1e140e")
    a = B["arm"]
    paint_box(a, LEATHER)
    hline(a, a.sides(), 9, GOLD); hline(a, a.sides(), 10, "#d8d0c0"); hline(a, a.sides(), 11, "#d8d0c0")
    boots(B["boot"], DARK, top_row=6, cuff="#4a3a2a", buckle=GOLD)
    lg = B["leg"]
    paint_box(lg, DARK)
    for f in ("outer",):
        vline(lg, f, 1, 0, 11, "#3a2a22")
    waist(B["waist"], DARK)
    return l1, l2


def navy_officer(rng):
    l1, l2, B = layers()
    NAVY = material(["#101a3a", "#162248", "#1c2a56", "#223264"], rng)
    WHITE = material(["#c8c8c0", "#d8d8d0", "#e6e6de"], rng)
    BLACK = material(["#101012", "#18181a", "#202024"], rng)
    GOLD = "#e0b84a"
    h = B["head"]
    paint_box(h, BLACK)
    for f in ("front", "outer", "inner", "back"):
        art(h, f, ["________"] * 4, {}, 0, 4)
        hline(h, [f], 3, GOLD)
    art(h, "front", ["...cc...", "..cWWc..", "...cc..."], {"c": "#2a3a8a", "W": "#e6e6de"}, 0, 0)   # cockade
    for f in ("outer", "inner"):
        art(h, f, ["g......g", ".g....g.", "..gggg.."], {"g": GOLD}, 0, 0)
    bd = B["body"]
    paint_box(bd, NAVY)
    art(bd, "front", ["NNwNNwNN", "NNNwwNNN", "N.gwwg.N", "N..ww..N", "N.gwwg.N", "N..ww..N", "N.gwwg.N", "N..ww..N",
                      "wwwwwwww", "wwwGGwww", "N......N", "N......N"],
        {"N": "#101a3a", "w": "#e6e6de", "g": GOLD, "G": GOLD})
    art(bd, "back", ["........", ".w....w.", "..w..w..", "...ww...", "...ww...", "..w..w..", ".w....w.", "........",
                     "wwwwwwww", "wwwwwwww"], {"w": "#d8d8d0"})
    a = B["arm"]
    paint_box(a, NAVY)
    paint_box(a, material(["#c8962a", "#e0b84a", "#f0d070"], rng), faces=["top"])   # epaulette tops
    hline(a, a.sides(), 0, GOLD); hline(a, a.sides(), 1, GOLD, every=2)
    hline(a, a.sides(), 9, GOLD); hline(a, a.sides(), 10, "#e6e6de")
    boots(B["boot"], BLACK, top_row=3, cuff="#2a2a30")
    B["boot"].put("front", 1, 4, hexc("#3a3a44")); B["boot"].put("front", 2, 4, hexc("#3a3a44"))
    lg = B["leg"]
    paint_box(lg, WHITE)
    vline(lg, "outer", 1, 0, 11, GOLD)
    waist(B["waist"], WHITE)
    hline(B["waist"], B["waist"].sides(), 8, GOLD)
    return l1, l2


def corsair(rng):
    l1, l2, B = layers()
    BLK = material(["#141214", "#1c1a1c", "#242124", "#2c292c"], rng)
    SASH = material(["#6a1018", "#841620", "#9c1e28"], rng)
    STUD = "#8a8e98"
    h = B["head"]
    paint_box(h, BLK)
    # hood + face scarf: only an eye slit is open
    art(h, "front", ["kkkkkkkk", "k......k", "k......k", "k______k", "ssssssss", "sSssssSs", "ssssssss", "ssssssss"],
        {"k": "#141214", "s": "#841620", "S": "#6a1018"})
    for f in ("outer", "inner"):
        art(h, f, ["........", "........", "........", "........", "ssss....", "ssss....", "sss.....", "ss......"],
            {"s": "#841620"})
    bd = B["body"]
    paint_box(bd, BLK)
    art(bd, "front", ["kk....kk", "k.o..o.k", "k......k", "k.o..o.k", "k......k", "k.o..o.k", "k......k", "sssSssss",
                      "ssSsssss", "sSssssss", "k.o..o.k", "kk....kk"], {"k": "#141214", "o": STUD, "s": "#9c1e28", "S": "#6a1018"})
    for f in ("outer", "inner", "back"):
        hline(bd, [f], 7, "#9c1e28"); hline(bd, [f], 8, "#841620"); hline(bd, [f], 9, "#841620")
    dots(bd, ["back"], [(1, 1), (6, 1), (1, 4), (6, 4), (3, 2), (4, 2)], STUD)
    a = B["arm"]
    paint_box(a, BLK)
    dots(a, a.sides(), [(0, 1), (3, 1), (1, 5), (2, 5)], STUD)
    hline(a, a.sides(), 9, "#3a3438"); hline(a, a.sides(), 10, "#3a3438")
    boots(B["boot"], BLK, top_row=5, cuff="#3a3438", buckle="#c8ccd4")
    B["boot"].put("front", 1, 8, hexc("#c8ccd4")); B["boot"].put("front", 2, 8, hexc("#c8ccd4"))
    lg = B["leg"]
    paint_box(lg, BLK)
    art(lg, "outer", ["s...", "Ss..", "sS..", ".s..", ".S.."], {"s": "#9c1e28", "S": "#6a1018"})   # trailing sash end
    waist(B["waist"], SASH, rows=(7, 11))
    return l1, l2


def ashen(rng):
    l1, l2, B = layers()
    BAS = material(["#1a1716", "#221e1c", "#2a2523", "#332d2a"], rng)
    EMB = ["#ff7a1e", "#ffb03a", "#e8541a"]

    def seams(box, faces, n, rngl):
        for f in faces:
            fw, fh = box.size(f)
            for _ in range(n):
                x, y = rngl.randrange(fw), rngl.randrange(fh)
                for _ in range(rngl.randint(2, 4)):
                    box.put(f, x, y, hexc(rngl.choice(EMB)))
                    x += rngl.choice((-1, 0, 1)); y += 1
    h = B["head"]
    paint_box(h, BAS)
    art(h, "front", ["kkkkkkkk", "k......k", "k......k", "kOOkkOOk", "k______k", "k______k", "kk.kk.kk", "kkkkkkkk"],
        {"k": "#141110", "O": "!#ffb03a"})                                 # glowing visor slits over an open mouth guard
    art(h, "top", ["...rr...", "..rRRr..", "...rr..."], {"r": "#2a2320", "R": "#ff7a1e"}, 0, 2)   # crest
    seams(h, ["outer", "inner", "back"], 1, rng)
    bd = B["body"]
    paint_box(bd, BAS)
    art(bd, "front", ["kkkkkkkk", "k.L..L.k", "k..LL..k", "k.L..L.k", "kkkLLkkk", "k..LL..k", "k...L..k", "kkkkkkkk",
                      "k......k", "k.L..L.k", "k......k", "kkkkkkkk"], {"k": "#141110", "L": "!#ff7a1e"})
    seams(bd, ["back", "outer", "inner"], 2, rng)
    a = B["arm"]
    paint_box(a, BAS)
    paint_box(a, material(["#2a2320", "#3a302a"], rng), faces=["top"])
    hline(a, a.sides(), 0, "#141110"); hline(a, a.sides(), 4, "#141110")
    seams(a, a.sides(), 1, rng)
    boots(B["boot"], BAS, top_row=5, cuff="#141110")
    for x in range(4):
        if x % 2 == 0:
            B["boot"].put("front", x, 11, hexc("#ff7a1e"))
    lg = B["leg"]
    paint_box(lg, BAS)
    hline(lg, lg.sides(), 5, "#141110")
    seams(lg, ["front", "outer"], 1, rng)
    waist(B["waist"], BAS)
    hline(B["waist"], B["waist"].sides(), 9, "#ff7a1e", every=3)
    return l1, l2


def cursed_bone(rng):
    l1, l2, B = layers()
    BONE = material(["#b8b2a0", "#c8c2ae", "#d6d0bc", "#e0dac8"], rng)
    CLOTH = material(["#16181a", "#1e2022", "#26282a"], rng)
    TEAL = "#3fe0c0"
    h = B["head"]
    paint_box(h, BONE)
    art(h, "front", ["bbbbbbbb", "b.b..b.b", "bkkbbkkb", "bkEbbEkb", "bbb__bbb", "b_k__k_b", "bt_tt_tb", "_tktktk_"],
        {"b": "#d6d0bc", "k": "#1a1612", "E": "!" + TEAL, "t": "#e8e2cc"})
    for f in ("outer", "inner"):
        art(h, f, ["........", "........", "........", "........", "........", "..____..", ".______.", "________"], {})
    art(h, "top", ["...ss...", "..s..s..", "...ss..."], {"s": "#a8a28c"}, 0, 3)     # cranial suture
    bd = B["body"]
    paint_box(bd, CLOTH)
    art(bd, "front", ["cbbbbbbc", "c..bb..c", "bbbbbbbb", "c..bb..c", "bbbbbbbb", "c..bb..c", "bbbbbbbb", "c..bb..c",
                      "c.bbbb.c", "c..bb..c", "cc.bb.cc", "cc....cc"], {"b": "#d6d0bc", "c": "#16181a"})
    art(bd, "back", ["...bb...", "..bbbb..", "...bb...", "..bbbb..", "...bb...", "..bbbb..", "...bb...", "..bbbb..",
                     "...bb...", "..bbbb.."], {"b": "#c8c2ae"})                   # vertebrae
    a = B["arm"]
    paint_box(a, CLOTH)
    paint_box(a, BONE, rows=(0, 3), faces=["outer", "front", "inner", "back", "top"])
    art(a, "top", ["b..b", ".bb.", ".bb.", "b..b"], {"b": "#e0dac8"})             # knuckle spikes on the pauldron
    hline(a, a.sides(), 8, "#d6d0bc"); hline(a, a.sides(), 10, "#d6d0bc")
    boots(B["boot"], BONE, top_row=6, cuff="#a8a28c")
    for x in (0, 3):
        B["boot"].put("front", x, 7, hexc("#1a1612"))
    lg = B["leg"]
    paint_box(lg, CLOTH)
    for f in ("front", "outer"):
        art(lg, f, ["....", ".bb.", ".bb.", "....", ".bb.", ".bb.", "....", "bbbb", ".bb."], {"b": "#d6d0bc"})
    waist(B["waist"], CLOTH)
    hline(B["waist"], B["waist"].sides(), 8, "#d6d0bc")
    return l1, l2


def kraken_scale(rng):
    l1, l2, B = layers()

    def scales(face, x, y):
        # overlapping scale rows: a dark crescent under each scale, a light rim on top, rare purple sheen
        off = (y // 2) % 2
        if y % 2 == 1 and (x + off) % 2 == 0:
            return hexc("#0c2830")
        if y % 2 == 0 and (x + off) % 2 == 0:
            return hexc("#2e7a7a" if rng.random() < 0.85 else "#6a4a9a")
        return hexc(["#17474f", "#1c545a", "#1c545a", "#236266"][int(rng.random() * 4)])
    SC = scales
    INK = material(["#0e1418", "#141c22", "#1a242c"], rng)
    AMBER = "!#ffb03a"
    SUCK = "#c89a58"
    h = B["head"]
    paint_box(h, SC)
    art(h, "front", ["kkkkkkkk", "k.a..a.k", "k......k", "k______k", "k______k", "k______k", "kd____dk", "kdk__kdk"],
        {"k": "#0e1418", "a": AMBER, "d": SUCK})                           # hood mantle with amber eye gems, tentacle cheeks
    for f in ("outer", "inner"):
        art(h, f, ["........", "........", "........", "......__", ".....___", "....d___", "...d.d__", "..d...d_"], {"d": SUCK})
    bd = B["body"]
    paint_box(bd, SC)
    art(bd, "front", ["kkkkkkkk", "k......k", "k..aa..k", "k.a..a.k", "k..aa..k", "k......k", "d......d", ".d....d.",
                      "kkkkkkkk", "k.dddd.k", "k......k", "kkkkkkkk"], {"k": "#0e1418", "a": AMBER, "d": SUCK})
    art(bd, "back", ["........", "...dd...", "..d..d..", ".d....d.", "d......d", "..d..d..", "...dd...", "........"], {"d": SUCK})
    a = B["arm"]
    paint_box(a, SC)
    for f in a.sides():
        for y in range(2, 11, 3):
            a.put(f, 1, y, hexc(SUCK)); a.put(f, 2, y + 1, hexc(SUCK))
    boots(B["boot"], INK, top_row=6, cuff="#236266")
    B["boot"].put("front", 1, 8, hexc(SUCK)); B["boot"].put("front", 2, 9, hexc(SUCK))
    lg = B["leg"]
    paint_box(lg, SC)
    vline(lg, "front", 1, 1, 10, "#0e1418")
    waist(B["waist"], INK)
    hline(B["waist"], B["waist"].sides(), 9, SUCK, every=2)
    return l1, l2


SETS = {"castaway": castaway, "pirate_armor": pirate_armor, "navy_officer": navy_officer, "corsair": corsair,
        "ashen": ashen, "cursed_bone": cursed_bone, "kraken_scale": kraken_scale}


def write_sheet(done):
    S = 8
    sheet = Image.new("RGBA", (64 * S * 2 + 24, (32 * S + 12) * len(done)), (40, 44, 52, 255))
    for i, (name, (l1, l2)) in enumerate(done):
        y = i * (32 * S + 12)
        for j, im in enumerate((l1, l2)):
            big = im.resize((64 * S, 32 * S), Image.NEAREST)
            sheet.alpha_composite(big, (j * (64 * S + 24), y))
    sheet.save(ROOT / "tools/armor_sheet.png")


if __name__ == "__main__":
    names = sys.argv[1:] or list(SETS)
    done = []
    for n in names:
        l1, l2 = SETS[n](random.Random(sum(map(ord, n))))
        l1.save(OUT / f"{n}_layer_1.png")
        l2.save(OUT / f"{n}_layer_2.png")
        done.append((n, (l1, l2)))
        print("wrote", n)
    write_sheet(done)
