"""Pixel Pirates - HOMESTEAD textures (2026-09-30 overnight build).

    python tools/gen_homestead_textures.py

Everything the homestead package needs: crop stages, produce + galley food, and (as features land) building
blocks, furniture, tools, guns, fish... Built on the sprite engine and noise helpers of gen_overhaul_textures.py
(char-grid art lit from the top-left, dark outline; tileable noise blocks). Writes tools/homestead_sheet.png to
review everything at once. Never overwrites a texture owned by gen_overhaul_textures.py.
"""
from pathlib import Path

from PIL import Image

import gen_overhaul_textures as G
from gen_overhaul_textures import sprite, ramp, save, rgb, shade, mix

ITEM, BLOCK = G.ITEM, G.BLOCK
MODELS_ITEM = Path(__file__).resolve().parents[1] / "src/main/resources/assets/pixelpirates/models/item"
WRITTEN: list[tuple[str, Path]] = []


def out(img, folder, name):
    save(img, folder, name)
    WRITTEN.append((name, folder))


# =====================================================================================
# #11 TROPICAL CROPS  (cutout crop textures, no outline; 4 stages each)
# =====================================================================================
LEAF = {"l": ramp("#4f8f3a"), "L": ramp("#6fb04a"), "d": ramp("#2f6a2a"), "s": ramp("#7a6a3a")}

PINE_STAGES = [
    ["................"] * 11 + [
        "......l..l......",
        ".......ll.......",
        "......dlld......",
        ".......dd.......",
        "................"],
    ["................"] * 8 + [
        "...l........l...",
        "....l..l...l....",
        ".....l.l..l.....",
        "..l...lLll...l..",
        "...ll.lLLl.ll...",
        ".....ldLLdl.....",
        "......dddd......",
        "................"],
    ["................"] * 4 + [
        ".l............l.",
        "..l....l.....l..",
        "...l...l....l...",
        "....l..l...l....",
        ".l...l.L..l...l.",
        "..l..lLLLl...l..",
        "...ll.LLL.ll....",
        "..l..lLLLLl..l..",
        ".....dLLLLd.....",
        "....ddLLLLdd....",
        "......dddd......",
        "................"],
    ["......l..l......",
     ".....l.ll.l.....",
     "......lLLl......",
     ".......LL.......",
     "......yYYy......",
     ".....yYyYYy.....",
     ".l...YyYyYY...l.",
     "..l..yYYyYy..l..",
     "...l.YyYYyY.l...",
     "....lyYyYYyl....",
     ".l...lyYYyl...l.",
     "..ll.lLLLLl.ll..",
     "....llLLLLll....",
     "...l.dLLLLd.l...",
     ".....ddLLdd.....",
     "......dddd......"],
]
LIME_STAGES = [
    ["................"] * 11 + [
        ".......l........",
        "......lLl.......",
        ".......s........",
        ".......s........",
        "................"],
    ["................"] * 7 + [
        "......lL........",
        ".....lLLl.lL....",
        "......lLllLLl...",
        "...lL..sLl......",
        "..lLLl.s........",
        "....lls.........",
        ".......s........",
        ".......s........",
        "................"],
    ["................"] * 3 + [
        ".....lLLl.......",
        "...lLLLLLll.....",
        "..lLLdLLLLLl....",
        "..lLLLLLdLLLl...",
        "...lLdLLLLLl....",
        "....llLsLLl.....",
        "..lLl..s..lLl...",
        ".lLLLl.s.lLLL...",
        "..ll..ss...ll...",
        "......s.........",
        ".......s........",
        ".......s........",
        "................"],
    ["................",
     "....lLLLl.......",
     "..lLLgGLLll.....",
     ".lLLLGgLLLLl....",
     ".lLdLLLLgGLLl...",
     ".lLLLLdLLGgLl...",
     "..lgGLLLLLLl....",
     "..lGgLdLLLl.....",
     "...llLLsLgGl....",
     "..lLl..sLGgl....",
     ".lLLLl.s.lLLl...",
     "..ll..ss...ll...",
     "......s.........",
     ".......s........",
     ".......s........",
     "................"],
]
CHILI_STAGES = [
    ["................"] * 11 + [
        ".......l........",
        "......l.l.......",
        ".......s........",
        ".......s........",
        "................"],
    ["................"] * 8 + [
        ".......lL.......",
        "......lLLl......",
        "....lL.s.Ll.....",
        "...lLL.s.LLl....",
        "......ls........",
        ".......s........",
        ".......s........",
        "................"],
    ["................"] * 4 + [
        "......lLl.......",
        ".....lLLLl......",
        "...lL..s..Ll....",
        "..lLLl.s.lLLl...",
        "...ll..s..ll....",
        ".....lLs........",
        "....lLLsLl......",
        "......ls.LLl....",
        ".......s..l.....",
        ".......s........",
        ".......s........",
        "................"],
    ["......lLl.......",
     ".....lLLLl......",
     "...lL..s..Ll....",
     "..lLLl.sr.lLl...",
     "...ll.rsR..l....",
     "......Rs.r......",
     "....lLLsLlR.....",
     "..rLLl.s.LLl....",
     "..R.ll.s.rll....",
     "..R....sR.R.....",
     "...r..lsLr......",
     "......LsLL......",
     ".......s........",
     ".......s........",
     ".......s........",
     "................"],
]


def build_crops():
    fruit = {"y": ramp("#d8a02a"), "Y": ramp("#f0c84a"), "g": ramp("#c8e03a"), "G": ramp("#f0f878"),
             "r": ramp("#c8261e"), "R": ramp("#e8402a")}
    pal = {**LEAF, **fruit}
    for name, stages in (("pineapple_crop", PINE_STAGES), ("lime_crop", LIME_STAGES), ("chili_crop", CHILI_STAGES)):
        for i, rows in enumerate(stages):
            out(sprite(rows, pal, outline=False), BLOCK, f"{name}_stage{i}")


# =====================================================================================
# produce + the galley
# =====================================================================================
PINEAPPLE = [
    ".....l..l.......",
    "......l.l..l....",
    "...l..lLl.l.....",
    "....l.LLLl......",
    ".....lLLl.......",
    "......yyy.......",
    ".....yYyYy......",
    "....yYyYyYy.....",
    "....YyYyYyY.....",
    "....yYyYyYy.....",
    "....YyYyYyY.....",
    "....yYyYyYy.....",
    ".....YyYyY......",
    "......yyy.......",
    "................",
    "................"]
CROWN = [
    "................",
    "................",
    "....l.....l.....",
    ".....l...l......",
    "..l...l.l...l...",
    "...l..lLl..l....",
    "....l.LLL.l.....",
    ".....lLLLl......",
    "..lllLLLLLlll...",
    "....lLLLLLl.....",
    ".....yyyyy......",
    ".....YyYyY......",
    "................",
    "................",
    "................",
    "................"]
LIME = [
    "................",
    "................",
    "................",
    ".........ll.....",
    "........lL......",
    ".....gggg.......",
    "....gGGggg......",
    "...gGGgggggg....",
    "...gGggggggg....",
    "...gggggggdg....",
    "...ggggggddg....",
    "....gggggdg.....",
    ".....gggg.......",
    "................",
    "................",
    "................"]
CHILI = [
    "................",
    "................",
    "..........l.....",
    ".........ll.....",
    "........lLl.....",
    ".......rRr......",
    "......rRrr......",
    ".....rRrr.......",
    "....rRrr........",
    "...rRrr.........",
    "...rrr..........",
    "..rrr...........",
    "..rr............",
    "..r.............",
    "................",
    "................"]
SEEDS = [
    "................",
    "................",
    "................",
    "................",
    "......s.........",
    "...s......s.....",
    ".......s........",
    "....s.......s...",
    "........s.......",
    "..s..s......s...",
    "..........s.....",
    ".....s..s.......",
    "................",
    "................",
    "................",
    "................"]
BOWL = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..wwwwwwwwwwww..",
    "..wAAABBBAAABw..",
    "..wBBAAACCBAAw..",
    "..bbbbbbbbbbbb..",
    "..bbbbbbbbbbbb..",
    "...bbbbbbbbbb...",
    "....bbbbbbbb....",
    ".....dddddd.....",
    "................",
    "................",
    "................"]
DOUBLOON = [
    "................",
    "................",
    "................",
    "......cccc......",
    "....cCCCCCCc....",
    "...cCbbbbbbCc...",
    "...CbbBbbBbbC...",
    "...CbBbbbbBbC...",
    "...CbbbBBbbbC...",
    "...CbBbbbbBbC...",
    "...CbbBbbBbbC...",
    "...cCbbbbbbCc...",
    "....cCCCCCCc....",
    "......cccc......",
    "................",
    "................"]
BOTTLE = [
    "................",
    "......kk........",
    "......cc........",
    "......gg........",
    ".....gAAg.......",
    "....gAAAAg......",
    "...gAAAAAAg.....",
    "...gBAAAAAg.....",
    "...gBBAAAAg.....",
    "...gABBAAAg.....",
    "...gAAAAAAg.....",
    "...gAAAAAAg.....",
    "....gggggg......",
    "................",
    "................",
    "................"]


def build_food():
    leaf = {"l": ramp("#4f8f3a"), "L": ramp("#7ab84a")}
    out(sprite(PINEAPPLE, {**leaf, "y": ramp("#c8902a"), "Y": ramp("#e8b83a")}), ITEM, "pineapple")
    out(sprite(CROWN, {**leaf, "y": ramp("#a8782a"), "Y": ramp("#c8902a")}), ITEM, "pineapple_crown")
    out(sprite(LIME, {**leaf, "g": ramp("#6ab432"), "G": ramp("#b8e870"), "d": ramp("#4a8a22")}, flat="G"), ITEM, "lime")
    out(sprite(CHILI, {**leaf, "r": ramp("#c42418"), "R": ramp("#f05a3a")}, flat="R"), ITEM, "chili_pepper")
    out(sprite(SEEDS, {"s": ramp("#a8c26a")}, flat="s"), ITEM, "lime_seeds")
    out(sprite(SEEDS, {"s": ramp("#e8d27a")}, flat="s"), ITEM, "chili_seeds")
    bowl = {"w": ramp("#b48c5c"), "b": ramp("#8e6a44"), "d": ramp("#6a4c30")}
    out(sprite(BOWL, {**bowl, "A": ramp("#e8b83a"), "B": ramp("#f0e070"), "C": ramp("#c83a2a")}, flat="ABC"), ITEM, "tropical_fruit_salad")
    out(sprite(BOWL, {**bowl, "A": ramp("#f0e0d0"), "B": ramp("#9ad05a"), "C": ramp("#d8402a")}, flat="ABC"), ITEM, "ceviche")
    out(sprite(BOWL, {**bowl, "A": ramp("#e8d0a0"), "B": ramp("#d8702a"), "C": ramp("#b82a1a")}, flat="ABC"), ITEM, "spicy_chowder")
    out(sprite(DOUBLOON, {"c": ramp("#5a3418"), "C": ramp("#7a4a24"), "b": ramp("#6a3e1e"), "B": ramp("#b8862e")}, flat="B"), ITEM, "chocolate_doubloon")
    out(sprite(BOTTLE, {"k": ramp("#8a6a42"), "c": ramp("#d8d0c0"), "g": ramp("#c8e0e8"), "A": ramp("#e8b83a"), "B": ramp("#f8e8a0")}, flat="AB"),
        ITEM, "pineapple_grog")


# =====================================================================================
# #12 RUM
# =====================================================================================
RUM_BOTTLE = [
    "................",
    ".......kk.......",
    ".......cc.......",
    ".......gg.......",
    ".......gg.......",
    "......gAAg......",
    ".....gAAAAg.....",
    ".....gBAAAg.....",
    ".....gLLLLg.....",
    ".....gLllLg.....",
    ".....gLLLLg.....",
    ".....gAAAAg.....",
    ".....gAAAAg.....",
    "......gggg......",
    "................",
    "................"]
JUG = [
    "................",
    "................",
    "......kk........",
    "......cc........",
    ".....cddc.......",
    "....cddddc.hh...",
    "...cddddddc..h..",
    "...cdLLLLdc..h..",
    "...cdLllLdc.h...",
    "...cdLLLLdch....",
    "...cddddddc.....",
    "...cddddddc.....",
    "....cddddc......",
    "................",
    "................",
    "................"]
TIPSY = [
    "..................",
    "..................",
    "..................",
    "......kk..........",
    "......cc....y.....",
    "......gg...y.y....",
    ".....gAAg...y.....",
    "....gAAAAg........",
    "....gBAAAg..y.....",
    "....gLLLLg.y.y....",
    "....gLllLg..y.....",
    "....gAAAAg........",
    "....gAAAAg........",
    ".....gggg.........",
    "..................",
    "..................",
    "..................",
    ".................."]


def build_rum():
    glass = {"k": ramp("#8a6a42"), "c": ramp("#d8d0c0"), "g": ramp("#9ab8c0")}
    out(sprite(JUG, {"k": ramp("#8a6a42"), "c": ramp("#6a4a2a"), "d": ramp("#3a1a0a"), "h": ramp("#6a4a2a"), "L": ramp("#e8d8b0"), "l": ramp("#8a5a2a")},
               flat="l"), ITEM, "molasses")
    for name, liquid, label, mark in (("raw_rum", "#e0c890", "#e8dcc0", "#8a7a5a"), ("aged_rum", "#c8782a", "#e8d8b0", "#8a2a1a"),
                                      ("vintage_rum", "#6a2a14", "#1a1a1a", "#e0b84a")):
        out(sprite(RUM_BOTTLE, {**glass, "A": ramp(liquid), "B": ramp(liquid, 1.5, 0.9), "L": ramp(label), "l": ramp(mark)}, flat="Bl"), ITEM, name)
    fx = G.TEX / "mob_effect"
    fx.mkdir(exist_ok=True)
    out(sprite(TIPSY, {**glass, "A": ramp("#c8782a"), "B": ramp("#f0b050"), "L": ramp("#e8d8b0"), "l": ramp("#8a2a1a"), "y": ramp("#f0e070")},
               flat="Bly", size=18), fx, "tipsy")


# =====================================================================================
# #5 PALM & TROPICAL BUILDING
# =====================================================================================
def grid(rows, pal):
    """Exact-pixel texture from a 16-row char grid (no lighting/outline); '.' = transparent."""
    img = Image.new("RGBA", (len(rows[0]), len(rows)), (0, 0, 0, 0))
    px = img.load()
    for y, r in enumerate(rows):
        for x, c in enumerate(r):
            if c in pal:
                px[x, y] = (*rgb(pal[c]), 255)
    return img


def thatch():
    """Tileable straw: vertical fibres in yellow-tan, a darker tie line every 8 px."""
    import numpy as np
    v = G.fbm(301, 2, 8) * 0.7 + np.random.RandomState(302).rand(16, 16) * 0.3
    img = G.palette_map(v, ["#8a6a2a", "#a8843a", "#c8a24a", "#dcbc62", "#ecd488"])
    px = img.load()
    for x in range(16):
        for y in (5, 13):
            px[x, y] = (*rgb("#6a4e1e"), 255)
        px[x, 6] = (*rgb("#9a7a34"), 255)
        px[x, 14] = (*rgb("#9a7a34"), 255)
    return img


def weave(base, dark, light, holes=False):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            over = ((x // 4) + (y // 4)) % 2 == 0
            u = (y % 4) if over else (x % 4)
            c = light if u == 1 else dark if u == 3 else base
            if holes and (x % 4 == 3 and y % 4 == 3):
                px[x, y] = (0, 0, 0, 0)
                continue
            px[x, y] = (*rgb(c), 255)
    return img


ROPE_LADDER = [
    ".rR..........Rr.",
    ".Rr..........rR.",
    ".rR..........Rr.",
    ".RwwwwwwwwwwwwR.",
    ".rWWWWWWWWWWWWr.",
    ".Rr..........rR.",
    ".rR..........Rr.",
    ".Rr..........rR.",
    ".rR..........Rr.",
    ".Rr..........rR.",
    ".rR..........Rr.",
    ".RwwwwwwwwwwwwR.",
    ".rWWWWWWWWWWWWr.",
    ".Rr..........rR.",
    ".rR..........Rr.",
    ".Rr..........rR."]
DOOR_TOP = [
    "fffffffffffffff f".replace(" ", ""),
    "fppppppppppppppf",
    "fpPpp.....pPpppf",
    "fpPp.......Pp.pf",
    "fpPp.......Pp.pf",
    "fpPpp.....pPpppf",
    "fppppppppppppppf",
    "fwwwwwwwwwwwwwwf",
    "fppppppppppppppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPppf",
    "fppppppppppppppf"]
DOOR_BOTTOM = [
    "fppppppppppppppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPkkf",
    "fpPppppPppppPkKf",
    "fpPppppPppppPppf",
    "fwwwwwwwwwwwwwwf",
    "fppppppppppppppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPppf",
    "fpPppppPppppPppf",
    "fwwwwwwwwwwwwwwf",
    "ffffffffffffffff"]
TRAPDOOR = [
    "ffffffffffffffff",
    "fppppp.pp.pppppf",
    "fpPpp..pp..pPppf",
    "fppppp.pp.pppppf",
    "fwwwwwwwwwwwwwwf",
    "fppppppppppppppf",
    "fpPppppppppppPpf",
    "fppppppppppppppf",
    "fppppppppppppppf",
    "fpPppppppppppPpf",
    "fppppppppppppppf",
    "fwwwwwwwwwwwwwwf",
    "fppppp.pp.pppppf",
    "fpPpp..pp..pPppf",
    "fppppp.pp.pppppf",
    "ffffffffffffffff"]
DOOR_ITEM = [
    "....ffffffff....",
    "....fpp..ppf....",
    "....fp....pf....",
    "....fpp..ppf....",
    "....fwwwwwwf....",
    "....fpPppPpf....",
    "....fpPppPpf....",
    "....fpPppkKf....",
    "....fpPppPpf....",
    "....fwwwwwwf....",
    "....fpPppPpf....",
    "....fpPppPpf....",
    "....fpPppPpf....",
    "....fwwwwwwf....",
    "....ffffffff....",
    "................"]


def build_building():
    out(thatch(), BLOCK, "thatch")
    out(weave("#c8a458", "#8a6a30", "#e8cc80", holes=True), BLOCK, "woven_palm_screen")
    edge = Image.new("RGBA", (16, 16))
    ep = edge.load()
    for y in range(16):
        for x in range(16):
            ep[x, y] = (*rgb("#8a6a30" if (y // 2) % 2 else "#b08a44"), 255)
    out(edge, BLOCK, "woven_palm_screen_edge")
    out(weave("#b89a58", "#7a5a2a", "#dcc080"), BLOCK, "woven_mat")
    rope = {"r": "#8a6e44", "R": "#b8986a", "w": "#6a4a2a", "W": "#8e6a3e"}
    out(grid(ROPE_LADDER, rope), BLOCK, "rope_ladder")
    rp = Image.new("RGBA", (16, 16))
    q = rp.load()
    for y in range(16):
        for x in range(16):
            q[x, y] = (*rgb("#b8986a" if (x + y) % 4 < 2 else "#8a6e44"), 255)
    out(rp, BLOCK, "rope")
    wood = {"f": "#5a3e22", "p": "#9a7040", "P": "#7a5430", "w": "#6a4a28", "k": "#3a3a3e", "K": "#8a8a90"}
    out(grid(DOOR_TOP, wood), BLOCK, "palm_door_top")
    out(grid(DOOR_BOTTOM, wood), BLOCK, "palm_door_bottom")
    out(grid(TRAPDOOR, wood), BLOCK, "palm_trapdoor")
    out(grid(DOOR_ITEM, wood), ITEM, "palm_door")


# =====================================================================================
# #2 FURNITURE
# =====================================================================================
def build_furniture():
    # cargo crate: planks boxed in a frame with an X brace
    crate = G.planks(411, "#9a7a4a", "#5a4228", "#b8945a")
    px = crate.load()
    frame, brace = rgb("#5a3e22"), rgb("#7a5630")
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i), (i, 1), (i, 14), (1, i), (14, i)):
            px[x, y] = (*frame, 255)
    for i in range(2, 14):
        px[i, i] = (*brace, 255)
        px[15 - i, i] = (*brace, 255)
        px[i, min(13, i + 1)] = (*shade(brace, 0.8), 255)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = (*rgb("#8a8a90"), 255)
    out(crate, BLOCK, "cargo_crate")
    top = G.planks(412, "#9a7a4a", "#5a4228", "#b8945a")
    tp = top.load()
    for i in range(16):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            tp[x, y] = (*frame, 255)
    for (x, y) in ((6, 6), (7, 6), (8, 6), (9, 6), (6, 9), (7, 9), (8, 9), (9, 9)):
        tp[x, y] = (*rgb("#2a2a2a"), 255)                                           # a stencilled mark
    out(top, BLOCK, "cargo_crate_top")
    # sea chart: parchment, coastline, a dotted course and an X
    chart = Image.new("RGBA", (16, 16))
    cp = chart.load()
    import random
    rs = random.Random(7)
    for y in range(16):
        for x in range(16):
            c = mix(rgb("#e6d3a3"), rgb("#cdb47e"), rs.random() * 0.5)
            if x in (0, 15) or y in (0, 15):
                c = rgb("#a8905c")
            cp[x, y] = (*c, 255)
    land = [(3, 3), (4, 3), (4, 4), (5, 4), (3, 4), (5, 5), (4, 5), (11, 10), (12, 10), (11, 11), (12, 11), (10, 11), (12, 12)]
    for (x, y) in land:
        cp[x, y] = (*rgb("#6a9a5a"), 255)
    for (x, y) in ((6, 6), (8, 7), (9, 9)):
        cp[x, y] = (*rgb("#5a4a3a"), 255)
    for (x, y) in ((11, 3), (13, 5), (12, 4), (12, 3), (11, 5), (13, 3)):
        pass
    for (x, y) in ((11, 3), (12, 4), (13, 5), (13, 3), (11, 5)):
        cp[x, y] = (*rgb("#b82a1a"), 255)
    for y in (2, 6, 10, 13):
        for x in range(1, 15, 3):
            if cp[x, y][:3] == cp[x, y][:3]:
                pass
    out(chart, BLOCK, "sea_chart")
    # fishing net: knotted mesh with holes (cutout)
    net = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    np_ = net.load()
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0 or (x - y) % 4 == 0:
                np_[x, y] = (*rgb("#c8b080" if (x * 3 + y) % 5 else "#9a845a"), 255)
    out(net, BLOCK, "fishing_net")


# =====================================================================================
# #21 GUN AMMO
# =====================================================================================
CARTRIDGE = [
    "................",
    "................",
    "..........kk....",
    ".........kppk...",
    "........kpPpk...",
    ".......kpPppk...",
    "......kpPppk....",
    ".....kpPppk.....",
    "....kpPppk......",
    "...kbbPpk.......",
    "..kbBbbk........",
    "..kbbbk.........",
    "...kkk..........",
    "................",
    "................",
    "................"]
SHOT = [
    "................",
    "................",
    ".......rr.......",
    "......rkkr......",
    ".......rr.......",
    "....cccccccc....",
    "...cCCCCCCCCc...",
    "..cCssCsCssCCc..",
    "..cCsSsCsSsCCc..",
    "..cCssCssCssCc..",
    "..cCCsSsCsSCCc..",
    "..cCCCCCCCCCCc..",
    "...cCCCCCCCCc...",
    "....cccccccc....",
    "................",
    "................"]


def build_guns():
    out(sprite(CARTRIDGE, {"k": ramp("#6a5a3a"), "p": ramp("#e8dcc0"), "P": ramp("#c8b894"), "b": ramp("#7a7e86"), "B": ramp("#b0b4bc")}, flat="B"),
        ITEM, "paper_cartridge")
    out(sprite(SHOT, {"r": ramp("#8a6a3a"), "k": ramp("#5a4020"), "c": ramp("#8a7050"), "C": ramp("#b09470"), "s": ramp("#6a6e76"), "S": ramp("#a8acb4")},
               flat="sS"), ITEM, "scattershot")


# =====================================================================================
# #24 REGION TOOLS (h head, H head light, a accent, s handle)
# =====================================================================================
PICK = [
    "................",
    "....hhhhhh......",
    "...hHHHHHHh.....",
    "..hHaaahhhHh....",
    ".hh.....hssHh...",
    "........shhhh...",
    ".......s..hHh...",
    "......s....hHh..",
    ".....s......hh..",
    "....s.......hh..",
    "...s............",
    "..s.............",
    ".s..............",
    "s...............",
    "................",
    "................"]
AXE = [
    "................",
    "........hhh.....",
    ".......hHHhh....",
    "......hHaHHhh...",
    "......hHaaHHh...",
    ".......hsHHhh...",
    "......s.hhhh....",
    ".....s..........",
    "....s...........",
    "...s............",
    "..s.............",
    ".s..............",
    "s...............",
    "................",
    "................",
    "................"]
SHOVEL = [
    "................",
    "..........hhh...",
    ".........hHHhh..",
    "........hHaHHh..",
    "........hHaHhh..",
    ".........hsHh...",
    "........s.hh....",
    ".......s........",
    "......s.........",
    ".....s..........",
    "....s...........",
    "...s............",
    "..s.............",
    ".s..............",
    "................",
    "................"]
HOE = [
    "................",
    "......hhhhh.....",
    ".....hHHaHHh....",
    "....hh...sHh....",
    ".........s.h....",
    "........s.......",
    ".......s........",
    "......s.........",
    ".....s..........",
    "....s...........",
    "...s............",
    "..s.............",
    ".s..............",
    "................",
    "................",
    "................"]


def build_tools():
    mats = {"ember": ("#8a2a10", "#e86a2a", "#ffd24a"), "kraken": ("#1e4a5a", "#4ab0b8", "#b8f0e0"), "bone": ("#8a8470", "#e0d8c0", "#3fe0c0")}
    handle = {"ember": "#3a2a24", "kraken": "#2a3a44", "bone": "#4a3a2a"}
    for name, (dark, light, accent) in mats.items():
        pal = {"h": ramp(dark, 1.25, 0.7), "H": ramp(light), "a": ramp(accent), "s": ramp(handle[name])}
        for shape, grid_ in (("pickaxe", PICK), ("axe", AXE), ("shovel", SHOVEL), ("hoe", HOE)):
            out(sprite(grid_, pal, flat="a"), ITEM, f"{name}_{shape}")


# =====================================================================================
# #3 HOARD: a tileable heap of gold coins
# =====================================================================================
def build_hoard():
    import random
    rs = random.Random(33)
    img = Image.new("RGBA", (16, 16), (*rgb("#8a6418"), 255))
    px = img.load()
    for _ in range(26):
        cx, cy = rs.randrange(16), rs.randrange(16)
        rim, face, hi = rgb("#a87a1a"), rgb("#e8b83a"), rgb("#fff0a0")
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, -1), (1, -1), (-1, 1)):
            x, y = (cx + dx) % 16, (cy + dy) % 16
            px[x, y] = (*(face if abs(dx) + abs(dy) < 2 else rim), 255)
        px[(cx - 1) % 16, (cy - 1) % 16] = (*hi, 255)
    out(img, BLOCK, "coin_pile")


# =====================================================================================
# #13 FISH (b body, B back/stripe, w belly, f fins, e eye)
# =====================================================================================
FISH_STD = [
    "................",
    "................",
    "................",
    "........ff......",
    ".......fBBBf....",
    "f.....BBBBBBB...",
    "ff...BBbBbBbeB..",
    ".fffbbbbbbbbbbb.",
    ".fffbbbbbbbbbbb.",
    "ff...wwwwwwwww..",
    "f.....wwwwwww...",
    ".......ff.......",
    "................",
    "................",
    "................",
    "................"]
FISH_EEL = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "..........BBBB..",
    ".........BbbbeB.",
    "..BBB...Bbbbbbb.",
    ".BbbbB.Bbbbww...",
    "Bbb.bbBbbww.....",
    "bb...bbbww......",
    "f.....ww........",
    "................",
    "................",
    "................",
    "................"]
FISH_SPINY = [
    "................",
    "................",
    "......f.f.f.....",
    ".....f.f.f.f....",
    "......BBBBB.....",
    "f....BbBbBbBB...",
    "ff..BBbBbBbeBB..",
    ".fffbbbbbbbbbbb.",
    ".fffbBbBbBbBbbb.",
    "ff..wwwwwwwwww..",
    "f....wwwwwwww...",
    ".....f.f.f......",
    "....f.f.f.......",
    "................",
    "................",
    "................"]
FISH_ANGLER = [
    "................",
    "..........ll....",
    "...........l....",
    "...........l....",
    "......BBBBBl....",
    "f...BBBBBBBBB...",
    "ff.BbbbbbbbbeB..",
    ".fbbbbbbbbbbbbb.",
    ".fbbbbbbbbbbwww.",
    "ff.wwwwwwwwtwtw.",
    "f...wwwwwwwwww..",
    "......ff........",
    "................",
    "................",
    "................",
    "................"]
FILLET = [
    "................",
    "................",
    "................",
    "................",
    "......ccccc.....",
    "....ccCCCCCcc...",
    "...cCCccCCCCCc..",
    "..cCCCCCcCCCCCc.",
    "..cCcCCCCCcCCCc.",
    "...cCCCCcCCCc...",
    "....cccCCCcc....",
    ".......ccc......",
    "................",
    "................",
    "................",
    "................"]
LOBSTER = [
    "................",
    "..r..........r..",
    "...r........r...",
    ".RR.r......r.RR.",
    "RRRr.r....r.rRRR",
    ".RR...rrrr...RR.",
    "..R..rRRRRr..R..",
    "...RrRRRRRRrR...",
    ".....rRRRRr.....",
    "....r.rRRr.r....",
    "......rRRr......",
    "......rRRr......",
    ".....rRRRRr.....",
    "....rRr..rRr....",
    "................",
    "................"]
CLAW = [
    "................",
    "................",
    ".....RRR........",
    "....RRRRR.......",
    "...RRr..........",
    "...RRr..........",
    "...RRRR.........",
    "....RRRRRR......",
    ".....RRRRRRr....",
    "......rRRRRRr...",
    "........rRRRRr..",
    "..........rRRr..",
    "...........rr...",
    "................",
    "................",
    "................"]
HOOK = [
    "................",
    "............w...",
    "...........w....",
    "..........w.....",
    ".........w......",
    "........w.......",
    ".......w........",
    "......w.........",
    ".....w..........",
    "....s...........",
    "...s............",
    "..s.............",
    ".s..............",
    "s...............",
    "................",
    "................"]
HOOK_HEAD = [
    "............ii..",
    "...........i..i.",
    "..........i....i",
    "...........i....",
    "................"]


def build_fish():
    species = {
        "parrotfish": (FISH_STD, "#3ab8a0", "#6ae0c8", "#f0e070", "#e8804a"),
        "red_snapper": (FISH_STD, "#c8403a", "#e8705a", "#f0c0b0", "#a82a2a"),
        "mahi_mahi": (FISH_STD, "#3aa04a", "#e8d03a", "#f0e8a0", "#3a8ac8"),
        "lionfish": (FISH_SPINY, "#e8e0d0", "#c8402a", "#f8f0e8", "#c8402a"),
        "moonfish": (FISH_STD, "#b8c8e0", "#e8f0ff", "#ffffff", "#8a9ab8"),
        "emberfin": (FISH_SPINY, "#e8602a", "#ffb03a", "#ffd88a", "#c8301a"),
        "lava_eel": (FISH_EEL, "#3a2a24", "#e8602a", "#ffb03a", "#3a2a24"),
        "ghostfin": (FISH_STD, "#8ad8c8", "#c8fff0", "#e8fff8", "#6ab8a8"),
        "bonefish": (FISH_STD, "#c8c0a8", "#e8e0c8", "#f8f4e8", "#8a846e"),
        "anglerfry": (FISH_ANGLER, "#2a2e4a", "#3a4068", "#8a90b8", "#1a1e30"),
        "voidfin": (FISH_SPINY, "#3a1e52", "#8a4ac8", "#b88ae8", "#1a0e28"),
    }
    for name, (shape, body, back, belly, fin) in species.items():
        pal = {"b": ramp(body), "B": ramp(back), "w": ramp(belly), "f": ramp(fin), "e": ramp("#101010"), "l": ramp("#f0ff8a"), "t": ramp("#f0f0e0")}
        out(sprite(shape, pal, flat="elt"), ITEM, name)
    out(sprite(FILLET, {"c": ramp("#b8703a"), "C": ramp("#e8a860")}), ITEM, "cooked_fish_fillet")
    out(sprite(LOBSTER, {"r": ramp("#6a2a1e"), "R": ramp("#3a4a6a")}), ITEM, "lobster")
    out(sprite(LOBSTER, {"r": ramp("#a8301e"), "R": ramp("#e8502a")}), ITEM, "cooked_lobster")
    out(sprite(CLAW, {"r": ramp("#8a2a1a"), "R": ramp("#d8482a")}), ITEM, "crab_claw")
    hook = sprite(HOOK + [], {"s": ramp("#6a4a2a"), "w": ramp("#d8d0c0")}, flat="w")
    head = sprite(HOOK_HEAD + ["................"] * 11, {"i": ramp("#8a8e96")})
    hook.alpha_composite(head)
    out(hook, ITEM, "salvage_hook")
    cast = sprite(HOOK[:9] + ["....s..........."] * 0 + HOOK[9:], {"s": ramp("#6a4a2a"), "w": ramp("#d8d0c0")}, flat="w")
    out(cast, ITEM, "salvage_hook_cast")
    # trophy fish body textures (plaques)
    import random
    for name, cols in (("golden_marlin_trophy", ("#d8a02a", "#3a6ab8", "#f0e0a0")), ("ghost_swordfish_trophy", ("#6ac8b8", "#c8fff0", "#2a6a60")),
                       ("coelacanth_trophy", ("#2a3a6a", "#e8e8f0", "#1a2240"))):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        rs = random.Random(hash(name) & 0xffff)
        for y in range(16):
            for x in range(16):
                c = rgb(cols[0]) if y > 5 else rgb(cols[1]) if y < 3 else mix(rgb(cols[1]), rgb(cols[0]), (y - 3) / 3)
                if y > 11: c = mix(c, rgb("#ffffff"), 0.3)
                if rs.random() < 0.08: c = rgb(cols[2])
                px[x, y] = (*c, 255)
        out(img, BLOCK, name)


# =====================================================================================
# #9 SALVAGE CRATE: the cargo crate after years on the bottom - greened, barnacled
# =====================================================================================
def build_salvage():
    import random
    for src, dst in (("cargo_crate", "salvage_crate"), ("cargo_crate_top", "salvage_crate_top")):
        img = Image.open(BLOCK / f"{src}.png").convert("RGBA")
        px = img.load()
        rs = random.Random(hash(dst) & 0xffff)
        for y in range(16):
            for x in range(16):
                r_, g, b, a = px[x, y]
                c = mix((r_, g, b), rgb("#4a6a4a"), 0.35 + 0.15 * (y / 15))
                if rs.random() < 0.07:
                    c = rgb("#d8d0c0") if rs.random() < 0.5 else rgb("#8a9a8a")        # barnacles
                px[x, y] = (*c, 255)
        out(img, BLOCK, dst)


# =====================================================================================
# #16 BOUNTY BOARD: a WANTED poster (skull + crossbones, reward line) and a written notice
# =====================================================================================
def build_bounty():
    paper, dark, ink, red = rgb("#e4d4a8"), rgb("#c8b484"), rgb("#2a1e14"), rgb("#8a2020")
    import random
    for name, rows in (("wanted_poster", [
            "cccccccccccccccc",
            "cpppppppppppppdc",
            "cprrprrprrprrrpc",
            "cppppppppppppppc",
            "cpppppkkkkpppppc",
            "cppppkkkkkkppppc",
            "cppppkwkkwkppppc",
            "cppppkkkkkkppppc",
            "cpppppkkkkpppppc",
            "cppkpppkkpppkppc",
            "cpppkpppppkppppc",
            "cppppkkpkkpppppc",
            "cppppppppppppppc",
            "cpiiipiipiiiippc",
            "cdppppppppppppdc",
            "cccccccccccccccc"]),
                       ("bounty_notice", [
            "cccccccccccccccc",
            "cpppppppppppppdc",
            "cpiiiiiiiippppdc",
            "cppppppppppppppc",
            "cpiiiipiiiiiiipc",
            "cpiiiiiiipiiiipc",
            "cpiiipiiiiiippdc",
            "cppppppppppppppc",
            "cpiiiiiipiiiiipc",
            "cpiiipiiiiipppdc",
            "cppppppppppppppc",
            "cpiiiiiiiiiiippc",
            "cpiiiiiippppppdc",
            "cppppppppprrrppc",
            "cdpppppppprrrpdc",
            "cccccccccccccccc"])):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        rs = random.Random(name)
        pal = {"c": dark, "p": paper, "d": mix(paper, dark, 0.6), "r": red, "k": ink, "w": paper, "i": mix(ink, paper, 0.3)}
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                col = pal[ch]
                if ch == "p" and rs.random() < 0.12:
                    col = mix(col, dark, 0.5)
                px[x, y] = (*col, 255)
        out(img, BLOCK, name)


# =====================================================================================
# #17 CAPTAIN'S SPYGLASS (vanilla spyglass re-cased in brass and red leather) + #18 COMPASS OF DESIRE (32 frames)
# =====================================================================================
def build_nav():
    import zipfile, io, json, math
    jar = Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"
    with zipfile.ZipFile(jar) as z:
        sg = Image.open(io.BytesIO(z.read("assets/minecraft/textures/item/spyglass.png"))).convert("RGBA")
    px = sg.load()
    for y in range(sg.height):
        for x in range(sg.width):
            r_, g, b, a = px[x, y]
            if not a:
                continue
            lum = (r_ + g + b) / 3 / 255
            if r_ > g + 20:                                           # the copper tube -> polished brass
                c = mix(rgb("#6a4a14"), rgb("#f0d070"), lum)
            elif lum < 0.35:                                          # the dark grip -> oxblood leather
                c = mix(rgb("#2a0c0a"), rgb("#8a2a20"), lum / 0.35)
            else:
                c = mix(rgb("#3a3040"), rgb("#c8e0f0"), lum)          # the lens
            px[x, y] = (*c, a)
    out(sg, ITEM, "captains_spyglass")

    ring, ring_d, face, face_l = rgb("#c8962e"), rgb("#7a5418"), rgb("#2a1a30"), rgb("#4a2e58")
    needle, tail, gem = rgb("#e02a3a"), rgb("#e8e0c8"), rgb("#b060e0")
    overrides = []
    for k in range(32):
        img = Image.new("RGBA", (16, 16))
        p = img.load()
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 7.5, y - 7.5)
                if d <= 7.2:
                    p[x, y] = (*(ring if d > 5.6 else (face_l if (x + y) % 5 == 0 else face)), 255)
                    if 5.6 < d <= 7.2 and (x < 7.5 and y < 7.5):
                        p[x, y] = (*mix(ring, rgb("#f8e08a"), 0.5), 255)
                    elif 6.6 < d <= 7.2:
                        p[x, y] = (*ring_d, 255)
        for (x, y) in ((7, 2), (8, 2), (7, 13), (8, 13), (2, 7), (2, 8), (13, 7), (13, 8)):
            p[x, y] = (*rgb("#f8e08a"), 255)                          # cardinal studs
        ang = math.radians((k - 16) * 11.25)                          # frame 16 = straight up (vanilla convention)
        dx, dy = math.sin(ang), -math.cos(ang)
        for t in [i / 4 for i in range(0, 20)]:
            x, y = 7.5 + dx * t, 7.5 + dy * t
            p[int(x), int(y)] = (*needle, 255)
        for t in [i / 4 for i in range(1, 11)]:
            x, y = 7.5 - dx * t, 7.5 - dy * t
            p[int(x), int(y)] = (*tail, 255)
        p[7, 7] = p[8, 8] = (*gem, 255)
        name = f"compass_of_desire_{k:02d}"
        out(img, ITEM, name)
        (MODELS_ITEM / f"{name}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"pixelpirates:item/{name}"}}, indent=2))
    # overrides exactly like vanilla's compass.json: angle 0 -> 16, then every 1/32 the next frame, wrapping to 16
    overrides.append({"predicate": {"angle": 0.0}, "model": "pixelpirates:item/compass_of_desire_16"})
    for i in range(31):
        overrides.append({"predicate": {"angle": round(0.015625 + i * 0.03125, 6)}, "model": f"pixelpirates:item/compass_of_desire_{(17 + i) % 32:02d}"})
    overrides.append({"predicate": {"angle": 0.984375}, "model": "pixelpirates:item/compass_of_desire_16"})
    (MODELS_ITEM / "compass_of_desire.json").write_text(json.dumps({"parent": "minecraft:item/generated",
        "textures": {"layer0": "pixelpirates:item/compass_of_desire_16"}, "overrides": overrides}, indent=2))


# =====================================================================================
# #1 THE JOLLY ROGER item sprite: a pole with the black flag and a tiny skull
# =====================================================================================
def build_hideout():
    pal = dict(p=rgb("#4a3220"), P=rgb("#2e1e12"), g=rgb("#d0aa4a"), k=rgb("#141214"), K=rgb("#2a282c"), w=rgb("#e8e4d8"))
    rows = ["  g             ",
            "  pkkkkkkkkkK   ",
            "  pkkkkkkkkkkK  ",
            "  pkkkkwwwkkkK  ",
            "  pkkkwkwkwkkkK ",
            "  pkkkkwwwkkkkK ",
            "  pkkwkkwkkwkkK ",
            "  pkkkwwkwwkkK  ",
            "  pkkwkkkkkwkK  ",
            "  pkkkkkkkkkK   ",
            "  p             ",
            "  p             ",
            "  P             ",
            "  p             ",
            " PpP            ",
            " PPP            "]
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch != " ":
                px[x, y] = (*pal[ch], 255)
    out(img, ITEM, "jolly_roger")


# =====================================================================================
# #22 GRAPPLING HOOK: a three-pronged iron hook on a coil of rope
# =====================================================================================
def build_grapple():
    pal = dict(i=rgb("#8a9098"), I=rgb("#c8ced4"), d=rgb("#4a5058"), r=rgb("#9a7444"), R=rgb("#6a4a28"))
    rows = ["                ",
            " I          I   ",
            " iI        Ii   ",
            "  di   I  id    ",
            "   di iIi id    ",
            "    dddiddd     ",
            "      did       ",
            "      did       ",
            "      ddd       ",
            "      rRr       ",
            "    rRr rRr     ",
            "   rR     Rr    ",
            "   rR     Rr    ",
            "    rRr rRr     ",
            "      rRr       ",
            "                "]
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y, r in enumerate(rows):
        for x, ch in enumerate(r[:16]):
            if ch != " ":
                px[x, y] = (*pal[ch], 255)
    out(img, ITEM, "grappling_hook")


# =====================================================================================
# #25 SAIL CANVAS (5 cloths, tileable) + CHAIN SHOT / GRAPE SHOT icons
# =====================================================================================
def build_ship():
    import random

    def cloth(name, base, paint=None):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        rs = random.Random(name)
        for y in range(16):
            for x in range(16):
                c = base(x, y)
                weave = 0.06 if (x + y) % 2 else -0.04
                if y % 8 == 7:
                    weave -= 0.1                                            # the seam of the canvas panels
                c = shade(c, 1 + weave + rs.uniform(-0.03, 0.03))
                px[x, y] = (*c, 255)
        if paint:
            paint(px)
        out(img, BLOCK, name)

    cloth("white_sail_canvas", lambda x, y: rgb("#e8e0cc"))
    cloth("black_sail_canvas", lambda x, y: rgb("#2a282c"))
    cloth("crimson_sail_canvas", lambda x, y: rgb("#8a2020"))
    cloth("striped_sail_canvas", lambda x, y: rgb("#e8e0cc") if (x // 4) % 2 == 0 else rgb("#9a2424"))

    def skull(px):
        rows = ["................", "................", ".....wwwwww.....", "....wwwwwwww....", "....wkkwwkkw....", "....wkkwwkkw....",
                "....wwwkkwww....", ".....wwwwww.....", ".....wkwwkw.....", "..w..........w..", "...ww......ww...", ".....ww..ww.....",
                ".......ww.......", ".....ww..ww.....", "...ww......ww...", "..w..........w.."]
        for y, r in enumerate(rows):
            for x, ch in enumerate(r):
                if ch == "w":
                    px[x, y] = (*rgb("#e8e4d8"), 255)
                elif ch == "k":
                    px[x, y] = (*rgb("#141214"), 255)
    cloth("jolly_roger_sail_canvas", lambda x, y: rgb("#1e1c20"), skull)

    def icon(name, rows, pal):
        img = Image.new("RGBA", (16, 16))
        px = img.load()
        for y, r in enumerate(rows):
            for x, ch in enumerate(r[:16]):
                if ch != " ":
                    px[x, y] = (*pal[ch], 255)
        out(img, ITEM, name)

    ball = dict(b=rgb("#2a2c30"), B=rgb("#4a4e56"), h=rgb("#7a808a"), c=rgb("#8a9098"), C=rgb("#5a6068"))
    icon("chain_shot", ["                ",
                        "  bbb           ",
                        " bhBBb          ",
                        " bBBBb          ",
                        " bBBBb          ",
                        "  bbbc          ",
                        "      C         ",
                        "       c        ",
                        "        C       ",
                        "         c      ",
                        "          cbbb  ",
                        "          bhBBb ",
                        "          bBBBb ",
                        "          bBBBb ",
                        "           bbb  ",
                        "                "], ball)
    icon("grape_shot", ["                ",
                        "      tt        ",
                        "     tTTt       ",
                        "    tTTTTt      ",
                        "   bhbbhbbb     ",
                        "   bBbhbBbh     ",
                        "  hbbBbbbhbb    ",
                        "  bhbbhbBbbb    ",
                        "  bBbhbbbhbB    ",
                        "  bbhbBbhbbb    ",
                        "   bbbbhbbb     ",
                        "   tTTTTTTt     ",
                        "    tttttt      ",
                        "                ",
                        "                ",
                        "                "], dict(b=rgb("#2a2c30"), B=rgb("#4a4e56"), h=rgb("#8a909a"), t=rgb("#5a3a1e"), T=rgb("#8a5a30")))


# =====================================================================================
# #20 CAPTAIN'S LOGBOOK: a salt-stained leather log with brass corners and a ribbon
# =====================================================================================
def build_logbook():
    pal = dict(l=rgb("#6a3a1e"), L=rgb("#8a5028"), d=rgb("#3a1e0e"), b=rgb("#d0aa4a"), p=rgb("#e8dcc0"), r=rgb("#a82020"), s=rgb("#b8a888"))
    rows = ["                ",
            "  bllllllllb    ",
            "  lLLLLLLLLlp   ",
            "  lLddddddLlp   ",
            "  lLdbbbbdLlp   ",
            "  lLdbddbdLlp   ",
            "  lLdbbbbdLlp   ",
            "  lLddddddLlp   ",
            "  lLLLLLLLLlp   ",
            "  lLLsLLLLLlp   ",
            "  lLLLLLsLLlp   ",
            "  lLLLLLLLLlp   ",
            "  bllllllllbp   ",
            "   pppppppppp   ",
            "      r         ",
            "      r         "]
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y, r in enumerate(rows):
        for x, ch in enumerate(r[:16]):
            if ch != " ":
                px[x, y] = (*pal[ch], 255)
    out(img, ITEM, "captains_logbook")


# =====================================================================================
# EXTRA: ROULETTE TABLE item sprite - the wheel seen from above on a dark-wood table
# =====================================================================================
def build_roulette():
    import math
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    red = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}
    order = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12, 35, 3, 26]
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            r = math.hypot(dx, dy)
            if r > 7.6:
                continue
            if r > 6.6:
                c = rgb("#c8a040")
            elif r > 4.6:
                i = int((math.degrees(math.atan2(dx, -dy)) % 360) / (360 / 37) + 0.5) % 37
                n = order[i]
                c = rgb("#1e8a3a") if n == 0 else (rgb("#b01e1e") if n in red else rgb("#141414"))
            elif r > 3.8:
                c = rgb("#c8a040")
            elif r < 1.3:
                c = rgb("#f0d070")
            else:
                c = rgb("#6a3a1e") if (x + y) % 2 else rgb("#5a3018")
            px[x, y] = (*c, 255)
    px[7, 2] = px[8, 2] = (*rgb("#f4f0e4"), 255)                           # the ball
    out(img, ITEM, "roulette_table")


# =====================================================================================
def write_sheet():
    cells = [(n, f) for n, f in WRITTEN]
    cols = 12
    rows = (len(cells) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * 68, rows * 68), (40, 44, 52, 255))
    for i, (n, f) in enumerate(cells):
        im = Image.open(f / f"{n}.png").convert("RGBA")
        im = im.resize((64, 64 * im.height // im.width), Image.NEAREST).crop((0, 0, 64, 64))
        sheet.alpha_composite(im, ((i % cols) * 68 + 2, (i // cols) * 68 + 2))
    sheet.save(Path(__file__).parent / "homestead_sheet.png")


BUILDERS = [build_crops, build_food, build_rum, build_building, build_furniture, build_guns, build_tools, build_hoard, build_fish, build_salvage, build_bounty, build_nav, build_hideout, build_grapple, build_ship, build_logbook, build_roulette]

if __name__ == "__main__":
    for b in BUILDERS:
        b()
    write_sheet()
    print(f"wrote {len(WRITTEN)} textures")
