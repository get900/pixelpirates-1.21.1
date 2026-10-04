"""Pixel Pirates - texture overhaul generator.

Run from the repo root:
    pip install pillow numpy
    python tools/gen_overhaul_textures.py

What it does (all output is deterministic - re-running is safe):
  1. ILLUSTRATED ITEMS  - the hand-painted 1024px masters in art_src/item/ are cropped,
     box-downscaled to 32x32, palette-quantized and edge-cleaned. The masters stay in
     art_src/ (NOT shipped in the jar - they were ~55 MB of the old jar).
  2. TEMPLATE SPRITES   - 16x16 icons authored as character grids. Each palette entry is a
     3-tone ramp; the engine lights every region from the top-left and adds a dark outline,
     so all gear/tool icons share one consistent look.
  3. BLOCK TEXTURES     - 16x16 tileable procedural textures (bark, log rings, cutout leaves,
     planks, stone, sand...) built on wrap-around value noise so every face tiles seamlessly.

Also writes tools/overhaul_sheet.png - a 6x contact sheet of everything, for review.
"""
from __future__ import annotations

import math
import random
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art_src"
TEX = ROOT / "src/main/resources/assets/pixelpirates/textures"
ITEM = TEX / "item"
BLOCK = TEX / "block"

SHEET: list[tuple[str, Image.Image]] = []


def save(img: Image.Image, folder: Path, name: str) -> None:
    folder.mkdir(parents=True, exist_ok=True)
    img.save(folder / f"{name}.png", optimize=True)
    SHEET.append((name, img))


def rgb(h: str) -> tuple[int, int, int]:
    h = h.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def mix(a, b, t: float):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def shade(c, f: float):
    """f<1 darkens (with a slight cool/saturated shift), f>1 lightens toward warm white."""
    if f < 1:
        return tuple(max(0, int(c[i] * f)) for i in range(3))
    return mix(c, (255, 250, 235), min(1.0, f - 1))


def ramp(base, hi=1.28, lo=0.68):
    b = rgb(base) if isinstance(base, str) else base
    return shade(b, hi), b, shade(b, lo)


# =====================================================================================
# 1. ILLUSTRATED ITEMS  (1024px masters -> 32x32)
# =====================================================================================
def downscale_master(src: Path, n: int = 32, colors: int = 20, margin: int = 1) -> Image.Image:
    im = Image.open(src).convert("RGBA")
    a = np.asarray(im)
    ys, xs = np.where(a[:, :, 3] > 40)
    im = im.crop((xs.min(), ys.min(), xs.max() + 1, ys.max() + 1))
    w, h = im.size
    s = max(w, h)
    inner = n - 2 * margin
    tw, th = max(1, round(w / s * inner)), max(1, round(h / s * inner))

    # premultiplied-alpha box filter so transparent fringe never bleeds dark halos in
    arr = np.asarray(im).astype(np.float64)
    arr[:, :, :3] *= arr[:, :, 3:4] / 255.0
    small = np.asarray(Image.fromarray(arr.round().astype(np.uint8), "RGBA").resize((tw, th), Image.BOX)).astype(np.float64)
    alpha = small[:, :, 3]
    col = np.where(alpha[:, :, None] > 0, small[:, :, :3] * 255.0 / np.maximum(alpha[:, :, None], 1), 0)
    mask = alpha > 110

    q = Image.fromarray(np.clip(col, 0, 255).astype(np.uint8), "RGB").quantize(colors, method=Image.Quantize.MEDIANCUT).convert("RGB")
    out = np.zeros((n, n, 4), np.uint8)
    ox, oy = (n - tw) // 2, (n - th) // 2
    out[oy:oy + th, ox:ox + tw, :3] = np.asarray(q)
    out[oy:oy + th, ox:ox + tw, 3] = mask * 255

    # drop orphan pixels (no opaque 4-neighbour) - downscale noise at the silhouette
    op = out[:, :, 3] > 0
    nb = np.zeros_like(op)
    nb[1:] |= op[:-1]; nb[:-1] |= op[1:]; nb[:, 1:] |= op[:, :-1]; nb[:, :-1] |= op[:, 1:]
    out[op & ~nb] = 0

    # crisp silhouette: darken opaque pixels that touch transparency (reads like an outline)
    op = out[:, :, 3] > 0
    edge = np.zeros_like(op)
    edge[1:] |= ~op[:-1]; edge[:-1] |= ~op[1:]; edge[:, 1:] |= ~op[:, :-1]; edge[:, :-1] |= ~op[:, 1:]
    edge[0, :] = edge[-1, :] = edge[:, 0] = edge[:, -1] = True
    e = op & edge
    out[e, :3] = (out[e, :3].astype(np.float64) * 0.62).astype(np.uint8)
    return Image.fromarray(out, "RGBA")


ILLUSTRATED = [
    "banana", "boarding_axe", "broken_shovel", "cannon", "cannon_ball", "coconut", "coin",
    "cooked_salted_swimmer", "cooked_shark_meat", "cutlass", "dagger", "driftwood", "dynamite",
    "grog", "kraken_ink", "mast", "mast_with_sails", "pirate_boots", "pirate_chestplate",
    "pirate_coin", "pirate_hat", "pirate_helmet", "pirate_leggings", "raft", "raw_salted_swimmer",
    "raw_shark_meat", "rope", "rusted_cutlass", "sail", "ship", "tattered_cloth",
]


def build_illustrated() -> None:
    for name in ILLUSTRATED:
        src = ART / "item" / f"{name}.png"
        if src.exists():
            save(downscale_master(src), ITEM, name)


# =====================================================================================
# 2. TEMPLATE SPRITE ENGINE (16x16)
# =====================================================================================
def sprite(rows: list[str], pal: dict, outline=True, flat: str = "", size: int = 16) -> Image.Image:
    """rows: char grid ('.' = empty). pal: char -> ramp (hi, mid, lo) or single colour.

    Every char region is lit from the top-left: a pixel whose up/left neighbour is a different
    region gets the highlight, one whose down/right neighbour differs gets the shadow. Chars in
    `flat` are drawn with their mid tone only (use for 1px details like rivets and gems).
    Transparent pixels touching the sprite get a dark outline derived from the neighbour."""
    h, w = size, size
    grid = [(r + "." * w)[:w] for r in rows] + ["." * w] * (h - len(rows))
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()

    def at(x, y):
        return grid[y][x] if 0 <= x < w and 0 <= y < h else "."

    for y in range(h):
        for x in range(w):
            c = grid[y][x]
            if c == "." or c not in pal:
                continue
            p = pal[c]
            if isinstance(p[0], int):
                px[x, y] = (*p, 255)
                continue
            hi, mid, lo = p
            if c in flat:
                col = mid
            else:
                lit = at(x, y - 1) != c or at(x - 1, y) != c
                dark = at(x, y + 1) != c or at(x + 1, y) != c
                col = mid if lit and dark else hi if lit else lo if dark else mid
            px[x, y] = (*col, 255)

    if outline:
        src = img.copy().load()
        for y in range(h):
            for x in range(w):
                if src[x, y][3]:
                    continue
                ns = [src[x + dx, y + dy] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))
                      if 0 <= x + dx < w and 0 <= y + dy < h and src[x + dx, y + dy][3]]
                if ns:
                    d = min(ns, key=lambda q: q[0] + q[1] + q[2])
                    px[x, y] = (*shade(d[:3], 0.42), 255)
    return img


# ---------------------------------------------------------------- shared blade shapes
# e = cutting edge, b = blade body, g = guard, h = grip, p = pommel, r = rope/wrap
CUTLASS = [
    "................",
    "..............e.",
    ".............eb.",
    "............ebb.",
    "...........ebb..",
    "..........ebb...",
    ".........ebb....",
    "........ebb.....",
    ".......ebb......",
    "......ebb.......",
    "..g..ebb........",
    "..ggebb.........",
    "..gghg..........",
    "...hgg..........",
    "..ph............",
    "................"]
SABRE = [
    "................",
    ".............ee.",
    "............ebb.",
    "...........ebb..",
    "..........ebb...",
    ".........ebb....",
    "........ebb.....",
    ".......ebb......",
    "......ebb.......",
    ".....ebb........",
    "....gbb.........",
    "...gggg.........",
    "..hhg.g.........",
    ".phh............",
    "................",
    "................"]
RAPIER = [
    "................",
    "..............e.",
    ".............eb.",
    "............eb..",
    "...........eb...",
    "..........eb....",
    ".........eb.....",
    "........eb......",
    ".......eb.......",
    "......eb........",
    ".....gb.........",
    "....ggg.........",
    "...g.hg.........",
    "..hhgg..........",
    ".ph.............",
    "................"]
DAGGER = [
    "................",
    "................",
    "................",
    "...........e....",
    "..........ebb...",
    ".........ebb....",
    "........ebb.....",
    ".......ebb......",
    "......gbb.......",
    ".....ggg........",
    "....hhg.........",
    "...phh..........",
    "...p............",
    "................",
    "................",
    "................"]
KNIFE = [
    "................",
    "................",
    "...........e....",
    "..........eb....",
    ".........ebb....",
    "........ebb.....",
    ".......ebb......",
    "......ebb.......",
    "......gb........",
    ".....hg.........",
    "....rh..........",
    "...hr...........",
    "..ph............",
    "................",
    "................",
    "................"]
PIKE = [
    "..............e.",
    ".............eb.",
    "............ebbb",
    "...........ebbb.",
    "............gb..",
    "...........g....",
    "..........h.....",
    ".........h......",
    "........h.......",
    ".......h........",
    "......h.........",
    ".....h..........",
    "....h...........",
    "...r............",
    "..hr............",
    ".p.............."]
HARPOON = [
    "..............e.",
    ".............eb.",
    "...........eebb.",
    "..........e.bb..",
    "..........ebb...",
    ".........ebgb...",
    "........eb......",
    ".......hb.......",
    "......h.........",
    ".....h..........",
    "....h...........",
    "...hr...........",
    "..hr............",
    ".phr............",
    "..rr............",
    "...r............"]


def blade_pal(edge, body, guard, grip, pommel, rope=None):
    p = {"e": ramp(edge, 1.15, 0.85), "b": ramp(body), "g": ramp(guard), "h": ramp(grip), "p": ramp(pommel)}
    p["r"] = ramp(rope or grip)
    return p


WEAPONS = {
    "marlinspike":     (DAGGER,  blade_pal("#d2d4da", "#8f949c", "#5a4632", "#7a5a3a", "#4e3a26")),
    "boarding_sabre":  (SABRE,   blade_pal("#e6e9ee", "#a4aab4", "#7a5630", "#8c643c", "#5f462a")),
    "naval_rapier":    (RAPIER,  blade_pal("#f2f4fa", "#b8c0cc", "#d4af37", "#3c3c5a", "#e6c34f")),
    "officers_sabre":  (SABRE,   blade_pal("#eef1f6", "#aeb5c2", "#d4af37", "#28304c", "#f0c850")),
    "throwing_knife":  (KNIFE,   blade_pal("#dcdfe5", "#9aa0aa", "#6e5034", "#5e4430", "#8c6946", "#b8966a")),
    "corsair_cutlass": (CUTLASS, blade_pal("#f5f8fc", "#b4bcc8", "#deb43c", "#462d1e", "#f0c850")),
    "boarding_pike":   (PIKE,    blade_pal("#e0e4ea", "#a0a6ae", "#5a4028", "#7a583a", "#4e3a26", "#b8966a")),
    "emberbrand":      (CUTLASS, blade_pal("#ffe08a", "#eb6e24", "#46302a", "#2e2424", "#ffb43c")),
    "soulrender":      (CUTLASS, blade_pal("#e6fff4", "#7fcfb4", "#463c37", "#322a28", "#78ebbe")),
    "wraithblade":     (SABRE,   blade_pal("#d6f2ff", "#86b8d6", "#2e3746", "#232837", "#aae6fa")),
    "kraken_fang":     (DAGGER,  blade_pal("#f5f0e1", "#c8bea0", "#5a3c78", "#3c2855", "#aa6edc")),
    "stormcaller":     (CUTLASS, blade_pal("#fafad2", "#9fb4d2", "#e1be46", "#37415f", "#fff08c")),
    "boss_slayer":     (CUTLASS, blade_pal("#ff6a6a", "#3a0c14", "#f0c850", "#1a1014", "#ff3a3a")),   # debug weapon
    "abyssal_harpoon": (HARPOON, blade_pal("#c0f0eb", "#5aafaa", "#2d505f", "#2d505f", "#8cdcd2", "#b4966a")),
}

BONE = [
    "................",
    "..bb............",
    ".bbbb...........",
    ".bbbbb..........",
    "..bbbbb.........",
    "...bbbbb........",
    "....bbbbb.......",
    ".....bbbbb......",
    "......bbbbb.....",
    ".......bbbbb....",
    "........bbbbbb..",
    ".........bbbbb..",
    "..........bbbb..",
    "...........bb...",
    "................",
    "................"]
SCALE = [
    "................",
    "................",
    ".....ssss.......",
    "....sbbbbs......",
    "...sbbbbbbs.....",
    "...sbwbbwbs.....",
    "...sbbbbbbs.....",
    "...sbwbbwbs.....",
    "....sbbbbs......",
    "....sbbbbs......",
    ".....sbbs.......",
    "......ss........",
    "................",
    "................",
    "................",
    "................"]
EMBER = [
    "................",
    "................",
    ".......d........",
    "......ddd.......",
    ".....dbbbd......",
    ".....dbobd......",
    "....dbooobd.....",
    "....dboyobd.....",
    "....dbooobd.....",
    ".....dbobd......",
    ".....dbbd.......",
    "......dd........",
    "................",
    "................",
    "................",
    "................"]

HELM_BANDANA = [
    "................",
    "................",
    "................",
    "................",
    ".....mmmmmm.....",
    "....mmmmmmmm....",
    "...mmmmwmmmmm...",
    "...mmmmmmmmmm...",
    "...aaaaaaaaaa...",
    "...m.......mm...",
    "...........mmm..",
    "............mm..",
    "................",
    "................",
    "................",
    "................"]
HELM_TRICORN = [
    "................",
    "................",
    "................",
    "................",
    "......mmmm......",
    ".....mmmmmm.....",
    "..m..mmmmmm..m..",
    ".mmm.mmmmmm.mmm.",
    ".mmmmmmmmmmmmmm.",
    "..mmaaaaaaaamm..",
    "...mmmmmmmmmm...",
    ".....mmmmmm.....",
    "................",
    "................",
    "................",
    "................"]
HELM_FULL = [
    "................",
    "................",
    "................",
    ".....mmmmmm.....",
    "....mmmmmmmm....",
    "...mmmmmmmmmm...",
    "...mmmmmmmmmm...",
    "...aaaaaaaaaa...",
    "...mm.aaaa.mm...",
    "...mm.mmmm.mm...",
    "...mm......mm...",
    "...m........m...",
    "................",
    "................",
    "................",
    "................"]
CHEST = [
    "................",
    "................",
    "..mmm......mmm..",
    ".mmmmmm..mmmmmm.",
    ".mmmmmmmmmmmmmm.",
    ".mmmmmmmmmmmmmm.",
    ".mm.mmmmmmmm.mm.",
    "....mmmaammm....",
    "....mmmaammm....",
    "....mmmmmmmm....",
    "....mmmmmmmm....",
    "....mmmaammm....",
    "....mmmmmmmm....",
    "................",
    "................",
    "................"]
LEGS = [
    "................",
    "................",
    "...aaaaaaaaaa...",
    "...mmmmmmmmmm...",
    "...mmmmmmmmmm...",
    "...mmmm..mmmm...",
    "...mmm....mmm...",
    "...mmm....mmm...",
    "...mmm....mmm...",
    "...mmm....mmm...",
    "...mmm....mmm...",
    "...mmm....mmm...",
    "...mmm....mmm...",
    "................",
    "................",
    "................"]
BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "...mmm....mmm...",
    "...mmm....mmm...",
    "...mmm....mmm...",
    "...aaa....aaa...",
    "...mmm....mmm...",
    "..mmmm...mmmm...",
    "..mmmm...mmmm...",
    "................",
    "................",
    "................",
    "................"]

ARMOR_SETS = {
    # set: (main, accent, helmet shape)
    "castaway":     ("#aa8c64", "#5f3c28", HELM_BANDANA),
    "navy_officer": ("#28457a", "#e1b950", HELM_TRICORN),
    "corsair":      ("#8c2830", "#e1b950", HELM_TRICORN),
    "ashen":        ("#4a4542", "#f07828", HELM_FULL),
    "cursed_bone":  ("#c8cdb9", "#2d4641", HELM_FULL),
    "kraken_scale": ("#2d7878", "#8250b4", HELM_FULL),
}


def build_gear() -> None:
    for name, (shape, pal) in WEAPONS.items():
        save(sprite(shape, pal), ITEM, name)
    save(sprite(BONE, {"b": ramp("#d6dcc4")}), ITEM, "cursed_bone")
    save(sprite(SCALE, {"s": ramp("#1f5a64"), "b": ramp("#46a09b"), "w": ramp("#9ae6d7")}, flat="w"), ITEM, "kraken_scale")
    save(sprite(EMBER, {"d": ramp("#3c2d2d"), "b": ramp("#8c3c23"), "o": ramp("#f07828"), "y": ramp("#ffe070")}, flat="oy"), ITEM, "volcanic_ember")
    for set_name, (main, accent, helm) in ARMOR_SETS.items():
        pal = {"m": ramp(main), "a": ramp(accent), "w": ramp(shade(rgb(main), 1.25))}
        for piece, shape in (("helmet", helm), ("chestplate", CHEST), ("leggings", LEGS), ("boots", BOOTS)):
            save(sprite(shape, pal), ITEM, f"{set_name}_{piece}")


# ---------------------------------------------------------------- utility / quest items
REPAIR_KIT = [
    "................",
    "................",
    "......hhhh......",
    ".....h....h.....",
    "..bbbbbbbbbbbb..",
    "..bwwwwwwwwwwb..",
    "..bbbbbbbbbbbb..",
    "..bbbbbllbbbbb..",
    "..bbbbbllbbbbb..",
    "..bbbbbbbbbbbb..",
    "..bwwwwwwwwwwb..",
    "..bbbbbbbbbbbb..",
    "................",
    "................",
    "................",
    "................"]
BLUEPRINT = [
    "................",
    "..rr........rr..",
    "..rppppppppppr..",
    "...pllllllllp...",
    "...plpppppplp...",
    "...plppwwpplp...",
    "...plpwwwwplp...",
    "...plppwwpplp...",
    "...plwwwwwwlp...",
    "...plpwwwwplp...",
    "...plppppppplp..",
    "...pllllllllp...",
    "..rppppppppppr..",
    "..rr........rr..",
    "................",
    "................"]
JOURNAL = [
    "................",
    "................",
    "...ccccccccc....",
    "...cbbbbbbbbp...",
    "...cbbbbbbbbp...",
    "...cbbgggbbbp...",
    "...cbbgbgbbbp...",
    "...cbbgggbbbp...",
    "...cbbbbbbbbp...",
    "...cbbbbbbbbp...",
    "...cbbbbbbbbp...",
    "...cbbbbbbbbp...",
    "...cccccccccp...",
    "....s.pppppp....",
    "....s...........",
    "................"]
MAP = [
    "................",
    "................",
    "..pppppppppppp..",
    "..pdpppppppppp..",
    "..pppppppdpppp..",
    "..ppllpppppppp..",
    "..pplllppppxpp..",
    "..ppplllpxpxpp..",
    "..pppplllppxpp..",
    "..pppppllpppdp..",
    "..pdpppppppppp..",
    "..pppppppppppp..",
    "..pppppppppppp..",
    "................",
    "................",
    "................"]
BOUNTY = [
    "................",
    "..nn........nn..",
    "..npppppppppn...",
    "...pppppppppp...",
    "...ppkkkkkkpp...",
    "...ppkwwwwkpp...",
    "...ppkwkkwkpp...",
    "...ppkwwwwkpp...",
    "...pppkwwkppp...",
    "...pppppppppp...",
    "...pllllllllp...",
    "...pppppppppp...",
    "...pllllllllp...",
    "..npppppppppn...",
    "..nn........nn..",
    "................"]
TOKEN = [
    "................",
    "................",
    ".....gggggg.....",
    "....gyyyyyyg....",
    "...gyyssssyyg...",
    "...gysyyyysyg...",
    "...gysyssysyg...",
    "...gysysyysyg...",
    "...gysyssysyg...",
    "...gysyyyysyg...",
    "...gyyssssyyg...",
    "....gyyyyyyg....",
    ".....gggggg.....",
    "................",
    "................",
    "................"]
KEY = [
    "................",
    "..........ggg...",
    ".........gcccg..",
    "........gc.gcg..",
    "........gcg.cg..",
    ".........gcccg..",
    "........gggg....",
    ".......gg.......",
    "......gg........",
    ".....gg.........",
    "....gg..........",
    "...ggg..........",
    "..gg.gg.........",
    "..g..g..........",
    "................",
    "................"]
AMULET = [
    "................",
    "....cc....cc....",
    "...c........c...",
    "...c........c...",
    "....c......c....",
    ".....c....c.....",
    "......gggg......",
    ".....gjjjjg.....",
    "....gjjwjjjg....",
    "....gjjjjjjg....",
    "....gjjjjjjg....",
    ".....gjjjjg.....",
    "......gggg......",
    "................",
    "................",
    "................"]

# ---------------------------------------------------------------- boss relics (entity/mob/BossProgression)
DIVING_CHARM = [
    "................",
    "...cc......cc...",
    "..c..........c..",
    "...c........c...",
    "....c.gggg.c....",
    ".....gggggg.....",
    "....gggggggg....",
    "...ggrwwwwrgg...",
    "...ggwwwwwwgg...",
    "...ggwwhwwwgg...",
    "...ggrwwwwrgg...",
    "....gggggggg....",
    "....gg.gg.gg....",
    "...gggggggggg...",
    "................",
    "................"]
TIDE_PEARL = [
    "................",
    "................",
    "......pppp......",
    ".....pppppp.....",
    "....ppwwpppp....",
    "....ppwppppp....",
    "....pppppppp....",
    ".....pppppp.....",
    "...s.spppps.s...",
    "..sss.ssss.sss..",
    "..ssssssssssss..",
    "...ssbssssbss...",
    "....ssbssbss....",
    ".....ssssss.....",
    "................",
    "................"]
LANTERN = [
    "................",
    ".......hh.......",
    "......h..h......",
    "......hhhh......",
    ".....ffffff.....",
    "....fyyyyyyf....",
    "....fyyoyyyf....",
    "....fyooowyf....",
    "....fyoooyyf....",
    "....fyyoyyyf....",
    "....fyyyyyyf....",
    ".....ffffff.....",
    "......ffff......",
    "................",
    "................",
    "................"]
ANCHOR = [
    "................",
    ".......aa.......",
    "......a..a......",
    ".......aa.......",
    "....aaaaaaaa....",
    ".......aa.......",
    ".......aa.......",
    ".......aa.......",
    ".......aa.......",
    "..a....aa....a..",
    "..aa...aa...aa..",
    "...aa..aa..aa...",
    "....aaaaaaaa....",
    "......aaaa......",
    "................",
    "................"]
SIGIL = [
    "................",
    ".....gggggg.....",
    "...ggbbbbbbgg...",
    "..gbbtbttbtbbg..",
    "..gbbtbttbtbbg..",
    ".gbbbttttttbbbg.",
    ".gbbbbbttbbbbbg.",
    ".gbbbbbttbbbbbg.",
    ".gbbbbbttbbbbbg.",
    "..gbbbbttbbbbg..",
    "..gbbbbttbbbbg..",
    "...ggbbttbbgg...",
    ".....gggggg.....",
    "................",
    "................",
    "................"]
RAZOR_TOOTH = [
    "................",
    "...c........c...",
    "....c......c....",
    ".....cccccc.....",
    "....dttttttd....",
    "....tttttttt....",
    ".....dttttd.....",
    ".....tttttt.....",
    "......dttd......",
    "......tttt......",
    ".......tt.......",
    ".......rr.......",
    "................",
    "................",
    "................",
    "................"]
INK_HEART = [
    "................",
    "................",
    "...hhh....hhh...",
    "..hhhhh..hhhhh..",
    "..hhwhhhhhhhhh..",
    "..hhhhhhhhhhhh..",
    "..hhhhhhhhhhhh..",
    "...hhhhhhhhhh...",
    "....hhhhhhhh....",
    ".....hhhhhh.....",
    "......hhhh......",
    ".......hh.......",
    ".......dd.......",
    "........d.......",
    "................",
    "................"]
SHACKLE = [
    "................",
    ".....iiiii......",
    "....ii...ii.....",
    "...ii.....ii....",
    "...i.......i....",
    "...i............",
    "...i.......k....",
    "...ii.....kk....",
    "....ii...ii.....",
    ".....iiiii......",
    "...........ll...",
    "..........l..l..",
    "...........ll...",
    ".............ll.",
    "............l..l",
    ".............ll."]
HEARTSTONE = [
    "................",
    "................",
    "......rrrr......",
    ".....rrrrrr.....",
    "....rryrrrrr....",
    "...rrryrrrrrr...",
    "...rrrryyrrrr...",
    "...rrrrryrrrr...",
    "....rrrryrrr....",
    ".....rrryrr.....",
    "......rrrr......",
    ".......rr.......",
    "................",
    "................",
    "................",
    "................"]

HARPOON = [
    "............ii..",
    "...........iiw..",
    "..........iii...",
    ".........bii....",
    "........bb.i....",
    ".......ww.......",
    "......ww........",
    ".....ww.........",
    "....ww..........",
    "...ww...........",
    "..ww............",
    ".ww.............",
    "rr..............",
    "r...............",
    "................",
    "................"]

CHUM = [
    "................",
    "................",
    "....rr..........",
    "...rRRr.bb......",
    "..rRRRRrbbb.....",
    "..rRRdRRrbb.....",
    "..rRddRRRr......",
    "...rRRRRRRr.....",
    "....rRRdRRRr....",
    ".....rRRRRRr....",
    "......rrRRr.....",
    "........rr......",
    "................",
    "................",
    "................",
    "................"]

FLESH = [
    "................",
    "................",
    "......ssss......",
    "....ssssssss....",
    "...sssssssssr...",
    "..ssssssssssrr..",
    "..rRRRRRRRRRRr..",
    "..rRRRRwRRRRRr..",
    "..rRRRwwRRRRRr..",
    "...rRRRRRRRRr...",
    "....rRRRRRRr....",
    ".....rrrrrr.....",
    "................",
    "................",
    "................",
    "................"]

TIDESHACKLE = [
    "......pppp......",
    ".....pP..Pp.....",
    ".....p....p.....",
    ".....pP..Pp.....",
    "......pbbp......",
    ".......cc.......",
    "......c..c......",
    ".......cc.......",
    "......c..c......",
    ".......cc.......",
    "......c..c......",
    ".......cc.......",
    "......g..g......",
    ".......gg.......",
    "................",
    "................"]

DEPTH_CHARGE = [
    "................",
    ".......ff.......",
    "......f.........",
    ".......bb.......",
    ".....kkkkkk.....",
    "....krrrrrrk....",
    "...krkkkkkkrk...",
    "...kbbbbbbbbk...",
    "...kkkggkkkkk...",
    "...kkgkkgkkkk...",
    "...kbbbbbbbbk...",
    "...krkkkkkkrk...",
    "....krrrrrrk....",
    ".....kkkkkk.....",
    "................",
    "................"]

TIDEBREAKER = [
    "........ccpp....",
    ".......cCCppp...",
    "......cCCCppp...",
    ".....cCCCCpp....",
    ".....bCCCpp.....",
    "......bbbb......",
    ".....hbb........",
    "....hh..........",
    "...hh...........",
    "..hh............",
    ".hh.............",
    "gh..............",
    "................",
    "................",
    "................",
    "................"]

# ---------------------------------------------------------------- early-game food + healing (2026-09-30 playtest)
ROASTED_BANANA = [
    "................",
    "................",
    "............s...",
    "...........ss...",
    "..........yy....",
    ".........yYy....",
    "........yYyk....",
    ".......yYyk.....",
    "......yYyyk.....",
    ".....yYyyk......",
    "...kyYyyk.......",
    "..kyyyykk.......",
    "..kkkkk.........",
    "................",
    "................",
    "................"]
COCONUT_WATER = [
    "................",
    "......cc........",
    "......kk........",
    ".....gwwg.......",
    ".....gwwg.......",
    "....gwwwwg......",
    "...gwwwwwwg.....",
    "...gwwhwwwg.....",
    "...gwhhwwwg.....",
    "...gwwwwwwg.....",
    "...gbbbbbbg.....",
    "....gbbbbg......",
    ".....gggg.......",
    "................",
    "................",
    "................"]
SEA_BANDAGE = [
    "................",
    "................",
    "................",
    ".....wwwww......",
    "....wwwwwwww....",
    "...wwhwwwwwwww..",
    "...whhhwwwwrwww.",
    "...whhhwwwrrrww.",
    "...wwhwwwwwrwww.",
    "....wwwwwwwwww..",
    ".....wwwww......",
    "................",
    "................",
    "................",
    "................",
    "................"]
ISLAND_SKEWER = [
    "................",
    "..............s.",
    ".............s..",
    "..........ffs...",
    ".........fFfs...",
    "........yyfs....",
    ".......yYys.....",
    "......ffsy......",
    ".....fFsf.......",
    "....yysy........",
    "...yYs..........",
    "...ss...........",
    "..s.............",
    ".s..............",
    "................",
    "................"]
# ---------------------------------------------------------------- galley foods
BANANA_BREAD = [
    "................",
    "................",
    "................",
    "................",
    "....cccccccc....",
    "...cbbybbybbc...",
    "..cbbbbbbbbbbc..",
    "..bbybbbbybbbb..",
    "..bbbbbbbbbbbb..",
    "..bbbbbbbbbbbb..",
    "...bbbbbbbbbb...",
    "................",
    "................",
    "................",
    "................",
    "................"]
HARDTACK = [
    "................",
    "................",
    "................",
    "...bbbbbbbbbb...",
    "..bbbbbbbbbbbb..",
    "..bbhbbbbbhbbb..",
    "..bbbbbbbbbbbb..",
    "..bbbbbhbbbbbb..",
    "..bbhbbbbbhbbb..",
    "..bbbbbbbbbbbb..",
    "..bbbbbbbbbbbb..",
    "...bbbbbbbbbb...",
    "................",
    "................",
    "................",
    "................"]
CALAMARI = [
    "................",
    "................",
    "....ooo.........",
    "...o...o........",
    "...o...o..ooo...",
    "....ooo..o...o..",
    ".........o...o..",
    "..ooo.....ooo...",
    ".o...o..........",
    ".o...o....ooo...",
    "..ooo....o...o..",
    ".........o...o..",
    "..........ooo...",
    "................",
    "................",
    "................"]
COCONUT_GROG = [
    "..........s.....",
    ".........s......",
    ".........s......",
    "....ffffsf......",
    "...ffffffff.....",
    "...cccccccccc...",
    "...cbbbbbbbbc...",
    "...cbbbbbbbbc...",
    "...cbbbbbbbbc...",
    "....cbbbbbbc....",
    "....cbbbbbbc....",
    ".....cbbbbc.....",
    "......cccc......",
    "................",
    "................",
    "................"]
STEW = [
    "................",
    "................",
    "................",
    "................",
    "..wwwwwwwwwwww..",
    "..wsoosmoosmow..",
    "..wossommossow..",
    "..bbbbbbbbbbbb..",
    "..bbbbbbbbbbbb..",
    "...bbbbbbbbbb...",
    "....bbbbbbbb....",
    ".....dddddd.....",
    "................",
    "................",
    "................",
    "................"]


def build_items16() -> None:
    wood = ramp("#7a5433")
    save(sprite(REPAIR_KIT, {"b": wood, "w": ramp("#b58a55"), "h": ramp("#5a5a60"), "l": ramp("#d9b04a")}, flat="l"), ITEM, "ship_repair_kit")
    save(sprite(BLUEPRINT, {"p": ramp("#2f5b9a"), "l": ramp("#6f9ad6"), "w": ramp("#dce8f8"), "r": ramp("#c9b28a")}, flat="lw"), ITEM, "ship_blueprint")
    save(sprite(JOURNAL, {"c": ramp("#6b3a22"), "b": ramp("#8e4f2c"), "p": ramp("#efe3c4"), "g": ramp("#d9b04a"), "s": ramp("#b3262c")}, flat="gp"), ITEM, "pirate_journal")
    parch = ramp("#e6d3a3", 1.12, 0.8)
    save(sprite(MAP, {"p": parch, "l": ramp("#5c9a5a"), "x": ramp("#c23a2a"), "d": ramp("#b89c68")}, flat="pxd"), ITEM, "treasure_map_common")
    save(sprite(MAP, {"p": ramp("#d9c38c", 1.1, 0.8), "l": ramp("#3f86b8"), "x": ramp("#c23a2a"), "d": ramp("#8a6a3a")}, flat="pxd"), ITEM, "treasure_map_rare")
    save(sprite(MAP, {"p": ramp("#c9a86a", 1.1, 0.8), "l": ramp("#8c4fc2"), "x": ramp("#ffd24a"), "d": ramp("#6a4a22")}, flat="pxd"), ITEM, "treasure_map_legendary")
    save(sprite(BOUNTY, {"p": parch, "n": ramp("#8a6a3a"), "k": ramp("#2b2522"), "w": ramp("#efe7d4"), "l": ramp("#9c8660")}, flat="pkwl"), ITEM, "bounty_map")
    save(sprite(TOKEN, {"g": ramp("#8a6a1e"), "y": ramp("#e8c24a"), "s": ramp("#b08a2a")}, flat="ys"), ITEM, "seafarers_token")
    save(sprite(KEY, {"g": ramp("#6a5a8c"), "c": ramp("#b58cff")}), ITEM, "dimension_key")
    chain = ramp("#b8a06a")
    save(sprite(AMULET, {"c": chain, "g": ramp("#d9b04a"), "j": ramp("#e8542a"), "w": ramp("#ffe08a")}, flat="w"), ITEM, "heat_amulet")
    save(sprite(AMULET, {"c": chain, "g": ramp("#c0c4cc"), "j": ramp("#4ab8a0"), "w": ramp("#c8fff0")}, flat="w"), ITEM, "sanity_amulet")

    cord = ramp("#6a4a2a")
    save(sprite(DIVING_CHARM, {"c": cord, "g": ramp("#c8963c"), "r": ramp("#6a4a1e"), "w": ramp("#3f86b8"), "h": ramp("#d6f0ff")}, flat="rh"), ITEM, "rackhams_diving_charm")
    save(sprite(TIDE_PEARL, {"p": ramp("#dfe8f2", 1.12, 0.78), "w": ramp("#ffffff"), "s": ramp("#3a8a9a"), "b": ramp("#1f5e6a")}, flat="wb"), ITEM, "serpents_tide_pearl")
    save(sprite(LANTERN, {"h": ramp("#4a4a50"), "f": ramp("#3a3230"), "y": ramp("#ffae3a"), "o": ramp("#ffe08a"), "w": ramp("#fffbe0")}, flat="ow"), ITEM, "everburning_lantern")
    save(sprite(ANCHOR, {"a": ramp("#5ad6c0", 1.3, 0.62)}), ITEM, "spectral_anchor")
    save(sprite(SIGIL, {"g": ramp("#d9b04a"), "b": ramp("#1e5a70"), "t": ramp("#8ff0e0")}, flat="t"), ITEM, "royal_tide_sigil")
    save(sprite(RAZOR_TOOTH, {"c": cord, "t": ramp("#f0ead8", 1.1, 0.8), "d": ramp("#b8ad90"), "r": ramp("#b3262c")}, flat="dr"), ITEM, "bloodfin_razor_tooth")
    save(sprite(INK_HEART, {"h": ramp("#3a1e52", 1.5, 0.6), "w": ramp("#b58cff"), "d": ramp("#1a0e24")}, flat="wd"), ITEM, "krakens_ink_heart")
    save(sprite(SHACKLE, {"i": ramp("#8a8e96"), "k": ramp("#6a5a4a"), "l": ramp("#6e727a")}, flat="k"), ITEM, "broken_shackle")
    save(sprite(HEARTSTONE, {"r": ramp("#a01830", 1.35, 0.6), "y": ramp("#ffd070")}, flat="y"), ITEM, "abyssal_heartstone")
    save(sprite(DEPTH_CHARGE, {"k": ramp("#3a4a52"), "b": ramp("#b8862e"), "r": ramp("#6a7a82"), "f": ramp("#e8542a"), "g": ramp("#8affe8")},
                flat="fg"), ITEM, "depth_charge")
    save(sprite(HARPOON, {"i": ramp("#b8bec6"), "w": ramp("#8a6a42"), "b": ramp("#5a5e66"), "r": ramp("#c8b48a")}, flat="r"), ITEM, "harpoon")
    save(sprite(CHUM, {"r": ramp("#6a1414"), "R": ramp("#a82626"), "d": ramp("#4a0a0a"), "b": ramp("#e6dcc8")}, flat="d"), ITEM, "chum")
    save(sprite(FLESH, {"s": ramp("#46565f"), "r": ramp("#6a1414"), "R": ramp("#b8302a"), "w": ramp("#f0e6d2")}, flat="w"), ITEM, "bloodfin_flesh")
    save(sprite(TIDESHACKLE, {"p": ramp("#3a8a86"), "P": ramp("#8affe8"), "b": ramp("#2a6a66"), "c": ramp("#6a7078"), "g": ramp("#6fe8d0")},
                flat="Pg"), ITEM, "tideshackle")
    save(sprite(TIDEBREAKER, {"c": ramp("#e8806a"), "C": ramp("#f4a08a"), "p": ramp("#d8f0ec", 1.1, 0.8), "b": ramp("#b8862e"),
                              "h": ramp("#6a4a2a"), "g": ramp("#e0b84a")}, flat="g"), ITEM, "tidebreaker")

    save(sprite(ROASTED_BANANA, {"y": ramp("#c89a3a"), "Y": ramp("#e8c46a"), "k": ramp("#4a2e1a"), "s": ramp("#5a3a1e")}, flat="Y"), ITEM, "roasted_banana")
    save(sprite(COCONUT_WATER, {"g": ramp("#b8d8e0", 1.1, 0.75), "w": ramp("#f4f0e4"), "h": ramp("#ffffff"), "b": ramp("#e6dcc0"),
                                "c": ramp("#8a6a42"), "k": ramp("#6a4a2a")}, flat="h"), ITEM, "coconut_water")
    save(sprite(SEA_BANDAGE, {"w": ramp("#ece2c8"), "h": ramp("#c8b894"), "r": ramp("#b8302a")}, flat="r"), ITEM, "sea_bandage")
    save(sprite(ISLAND_SKEWER, {"s": ramp("#8a6a42"), "f": ramp("#c8783a"), "F": ramp("#e8a860"), "y": ramp("#e0b84a"), "Y": ramp("#f6e27a")},
                flat="FY"), ITEM, "island_skewer")
    save(sprite(BANANA_BREAD, {"c": ramp("#8a5a30"), "b": ramp("#c4904e"), "y": ramp("#f0dc6e")}, flat="y"), ITEM, "banana_bread")
    save(sprite(HARDTACK, {"b": ramp("#d2b882"), "h": ramp("#8e7446")}, flat="h"), ITEM, "hardtack")
    save(sprite(CALAMARI, {"o": ramp("#e1ae5a")}), ITEM, "kraken_calamari")
    save(sprite(COCONUT_GROG, {"s": ramp("#f5dc82"), "f": ramp("#faf5e6"), "c": ramp("#73502f"), "b": ramp("#5f4028")}, flat="s"), ITEM, "coconut_grog")
    save(sprite(STEW, {"w": ramp("#b48c5c"), "s": ramp("#e48e34"), "o": ramp("#c06826"), "m": ramp("#8a3a2a"), "b": ramp("#8e6a44"), "d": ramp("#6a4c30")}, flat="som"), ITEM, "pirates_stew")


# =====================================================================================
# 3. BLOCK TEXTURES (16x16, tileable)
# =====================================================================================
N = 16


def vnoise(seed: int, cx: int, cy: int | None = None) -> np.ndarray:
    """Tileable value noise, 16x16 in [0,1]. cx/cy = lattice cell size (must divide 16)."""
    cy = cy or cx
    rs = np.random.RandomState(seed)
    gx, gy = N // cx, N // cy
    lat = rs.rand(gy, gx)
    out = np.zeros((N, N))
    for y in range(N):
        for x in range(N):
            fx, fy = x / cx, y / cy
            x0, y0 = int(fx) % gx, int(fy) % gy
            x1, y1 = (x0 + 1) % gx, (y0 + 1) % gy
            tx, ty = fx - int(fx), fy - int(fy)
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = lat[y0, x0] + (lat[y0, x1] - lat[y0, x0]) * tx
            b = lat[y1, x0] + (lat[y1, x1] - lat[y1, x0]) * tx
            out[y, x] = a + (b - a) * ty
    return out


def fbm(seed: int, cx=8, cy=None) -> np.ndarray:
    n = vnoise(seed, cx, cy) * 0.6 + vnoise(seed + 1, max(1, cx // 2), max(1, (cy or cx) // 2)) * 0.3
    n += np.random.RandomState(seed + 2).rand(N, N) * 0.1
    return (n - n.min()) / (n.max() - n.min() + 1e-9)


def palette_map(v: np.ndarray, cols: list) -> Image.Image:
    """Quantize a [0,1] field into a list of colours (dark -> light)."""
    cols = [rgb(c) if isinstance(c, str) else c for c in cols]
    img = Image.new("RGBA", (N, N))
    px = img.load()
    k = len(cols)
    for y in range(N):
        for x in range(N):
            px[x, y] = (*cols[min(k - 1, int(v[y, x] * k))], 255)
    return img


def bark(seed, cols, fissure, streak=None) -> Image.Image:
    """Log side: vertical grain, wandering dark fissures (tile vertically and horizontally)."""
    v = fbm(seed, 2, 8) * 0.75 + vnoise(seed + 9, 4, 16) * 0.25
    img = palette_map(v, cols)
    px = img.load()
    rs = random.Random(seed)
    fc = rgb(fissure)
    for start in rs.sample(range(N), 4):
        x = start
        for y in range(N):
            px[x % N, y] = (*fc, 255)
            if rs.random() < 0.28:
                x += rs.choice((-1, 1))
        # close the loop so the fissure tiles vertically
        while x % N != start:
            x += 1 if (start - x) % N < N // 2 else -1
            px[x % N, N - 1] = (*fc, 255)
    if streak:
        sc = rgb(streak)
        for _ in range(6):
            x, y = rs.randrange(N), rs.randrange(N)
            for d in range(rs.randint(2, 4)):
                px[x, (y + d) % N] = (*sc, 255)
    return img


def log_top(seed, bark_img: Image.Image, rings: list, core: str) -> Image.Image:
    """Log end: single set of concentric rounded-square rings inside a 1px bark rim."""
    rs = np.random.RandomState(seed)
    wob = rs.rand(N, N) * 0.55
    img = Image.new("RGBA", (N, N))
    px, bp = img.load(), bark_img.load()
    rc = [rgb(c) for c in rings]
    for y in range(N):
        for x in range(N):
            if x in (0, N - 1) or y in (0, N - 1):
                px[x, y] = bp[x, y]
                continue
            dx, dy = abs(x - 7.5), abs(y - 7.5)
            d = max(dx, dy) * 0.6 + math.hypot(dx, dy) * 0.4 + wob[y, x]
            px[x, y] = (*(rgb(core) if d < 1.3 else rc[int(d) % len(rc)]), 255)
    return img


def planks(seed, base, seam, light, dark=None, speck=None) -> Image.Image:
    """Vanilla-style: 4 boards with seams, staggered butt joints, grain streaks, nail dots."""
    rs = random.Random(seed)
    grain = fbm(seed, 8, 2)
    b, s, l = rgb(base), rgb(seam), rgb(light)
    d = rgb(dark) if dark else shade(b, 0.85)
    img = Image.new("RGBA", (N, N))
    px = img.load()
    for y in range(N):
        for x in range(N):
            g = grain[y, x]
            px[x, y] = (*(d if g < 0.3 else l if g > 0.78 else b), 255)
    for board in range(4):
        y0 = board * 4
        for x in range(N):
            px[x, y0 + 3] = (*s, 255)
        joint = (board * 7 + 5) % N
        for dy in range(3):
            px[joint, y0 + dy] = (*s, 255)
        px[(joint + 1) % N, y0] = (*l, 255)
    if speck:
        sc = rgb(speck)
        for _ in range(4):
            px[rs.randrange(N), rs.randrange(N)] = (*sc, 255)
    return img


def leaves(seed, cols, accent=None, accent_n=0, holes=0.22) -> Image.Image:
    """Cutout leaves: clustered tones, see-through gaps (render layer must be cutout)."""
    v = fbm(seed, 4)
    img = palette_map(v, cols)
    px = img.load()
    h = vnoise(seed + 5, 2) * 0.7 + np.random.RandomState(seed + 6).rand(N, N) * 0.3
    thr = np.quantile(h, holes)
    for y in range(N):
        for x in range(N):
            if h[y, x] < thr:
                px[x, y] = (0, 0, 0, 0)
    if accent:
        rs = random.Random(seed)
        ac = rgb(accent)
        for _ in range(accent_n):
            x, y = rs.randrange(N), rs.randrange(N)
            px[x, y] = (*ac, 255)
    return img


def stone(seed, cols, cracks=None, crack_n=3, cell=4) -> Image.Image:
    img = palette_map(fbm(seed, cell), cols)
    if cracks:
        px = img.load()
        rs = random.Random(seed)
        cc = rgb(cracks)
        for _ in range(crack_n):
            x, y = rs.randrange(N), rs.randrange(N)
            for _ in range(rs.randint(3, 6)):
                px[x % N, y % N] = (*cc, 255)
                x += rs.choice((-1, 0, 1)); y += rs.choice((0, 1))
    return img


def sprinkle(img: Image.Image, seed, color, n, glow=None) -> Image.Image:
    """Crystal/pebble flecks; optional 1px glow cross around each."""
    px = img.load()
    rs = random.Random(seed)
    c = rgb(color)
    g = rgb(glow) if glow else None
    for _ in range(n):
        x, y = rs.randrange(N), rs.randrange(N)
        if g:
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                px[(x + dx) % N, (y + dy) % N] = (*g, 255)
        px[x, y] = (*c, 255)
    return img


def mode_downsample(src: Path, n=16) -> Image.Image:
    """Exact recovery of a true 16px texture that was upscaled (most common colour per cell)."""
    im = Image.open(src).convert("RGBA")
    a = np.asarray(im)
    ch, cw = a.shape[0] // n, a.shape[1] // n
    out = np.zeros((n, n, 4), np.uint8)
    for y in range(n):
        for x in range(n):
            cell = a[y * ch + ch // 4:(y + 1) * ch - ch // 4, x * cw + cw // 4:(x + 1) * cw - cw // 4].reshape(-1, 4)
            vals, counts = np.unique(cell // 4 * 4, axis=0, return_counts=True)
            out[y, x] = vals[counts.argmax()]
    out[:, :, 3] = np.where(out[:, :, 3] > 100, 255, 0)
    return Image.fromarray(out, "RGBA")


def banded(img: Image.Image, seed, color, every=4) -> Image.Image:
    """Horizontal growth rings on a trunk (palm) / bedding planes (slate)."""
    px = img.load()
    rs = random.Random(seed)
    c = rgb(color)
    for y0 in range(1, N, every):
        for x in range(N):
            px[x, (y0 + (1 if rs.random() < 0.25 else 0)) % N] = (*c, 255)
    return img


def wood_set(name, seed, bark_cols, fissure, rings, core, streak=None, band=None, planks_args=None):
    side = bark(seed, bark_cols, fissure, streak)
    if band:
        banded(side, seed, band)
    save(side, BLOCK, f"{name}_log")
    save(log_top(seed, side, rings, core), BLOCK, f"{name}_log_top")
    if planks_args:
        save(planks(seed + 50, *planks_args), BLOCK, f"{name}_planks")
    return side


def spectral_sail(seed) -> Image.Image:
    """Tattered ghost sailcloth: pale teal weave with a faint glow and ragged transparent holes."""
    rs = np.random.RandomState(seed)
    v = fbm(seed, 4)
    img = palette_map(v, ["#8ec8bc", "#a2d8cc", "#b4e6da", "#c8f2e8"]).convert("RGBA")
    px = img.load()
    for y in range(N):
        for x in range(N):
            if (x % 4 == 0) and rs.rand() < 0.5:           # woven seams
                r, g, b, _ = px[x, y]
                px[x, y] = (int(r * 0.88), int(g * 0.88), int(b * 0.88), 255)
    for _ in range(4):                                     # ragged holes
        cx, cy = rs.randint(0, N), rs.randint(0, N)
        for dy in range(-1, 2):
            for dx in range(-1, 2):
                if rs.rand() < 0.75: px[(cx + dx) % N, (cy + dy) % N] = (0, 0, 0, 0)
    for x in range(N):                                     # frayed bottom edge
        for y in range(N - 1 - rs.randint(0, 3), N):
            px[x, y] = (0, 0, 0, 0)
    return img


def verdigris(src) -> Image.Image:
    """Recolour an existing texture to corroded green-bronze with soul-glow flecks."""
    im = Image.open(src).convert("RGBA")
    px = im.load()
    rs = np.random.RandomState(7)
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0: continue
            l = (r * 0.3 + g * 0.59 + b * 0.11) / 255
            col = (int(40 + 80 * l), int(90 + 110 * l), int(84 + 96 * l))
            if rs.rand() < 0.04: col = (170, 250, 235)
            px[x, y] = (*col, a)
    return im

def build_blocks() -> None:
    # ---- Shorewood: the side masters are genuine 16px art at 64x scale - recover them exactly.
    # The end-grain and leaf masters were painted on a finer grid (garbled at 16px), so those
    # are regenerated procedurally in the same palette.
    sw = mode_downsample(ART / "block" / "shorewood_log.png")
    ssw = mode_downsample(ART / "block" / "stripped_shorewood_log.png")
    save(sw, BLOCK, "shorewood_log")
    save(sw, BLOCK, "shorewood_wood")
    save(ssw, BLOCK, "stripped_shorewood_log")
    save(ssw, BLOCK, "stripped_shorewood_wood")
    save(log_top(1, sw, ["#d6a860", "#b8863e"], "#9a6a2c"), BLOCK, "shorewood_log_top")
    save(log_top(2, ssw, ["#e8c078", "#d0a45a"], "#b88a44"), BLOCK, "stripped_shorewood_log_top")
    save(leaves(3, ["#3e7a2e", "#5a9a3e", "#78b44e", "#96cc62"], "#b4e07a", 6), BLOCK, "shorewood_leaves")

    # ---- Phase woods (log, log_top, planks, leaves)
    wood_set("palm", 11, ["#6e5234", "#86673f", "#9c7a4c", "#b08c5a"], "#7a5c38",
             ["#c9a46c", "#b48c58"], "#8a6a40", band="#5a4228",
             planks_args=("#be9664", "#82623c", "#d2aa78"))
    save(leaves(12, ["#2f6e2a", "#3f8a33", "#52a33c", "#6cbf48"], "#9ada5a", 5), BLOCK, "palm_leaves")

    wood_set("tidewood", 21, ["#3e5a56", "#4e6e68", "#5e807a", "#6f928a"], "#2a3e3b",
             ["#8fb2a8", "#7a9e94"], "#5e8078", streak="#8ab4aa",
             planks_args=("#7a968c", "#4e645e", "#94b2a8"))
    save(leaves(22, ["#1f5e5a", "#2c7a70", "#3c9486", "#58b09c"], "#bfeee0", 6), BLOCK, "tidewood_leaves")

    # the Flying Dutchman: pale rotted ghostwood, tattered glowing sailcloth, a soul-lit buoy, verdigris cannons
    wood_set("ghostwood", 41, ["#3e4a4a", "#4c5a58", "#5c6c68", "#6e807a"], "#28302f",
             ["#8aa39c", "#76908a"], "#5e7a74", streak="#9ec8bc",
             planks_args=("#6a7e78", "#3a4644", "#86a098"))
    save(spectral_sail(43), BLOCK, "spectral_sail")
    save(sprinkle(stone(45, ["#1e5a58", "#2a6e6a", "#3a827c", "#4c968e"], "#12302e", 3, cell=4), 46, "#bff8ec", 14, glow="#8ff0e0"), BLOCK, "phantom_buoy")
    for part in ("ship_cannon", "ship_cannon_carriage", "ship_cannon_muzzle"):
        save(verdigris(BLOCK / f"{part}.png"), BLOCK, part.replace("ship_", "ghost_"))
    save(banded(planks(47, "#7a948e", "#3a4644", "#a0bcb4"), 48, "#bff8ec", 4), BLOCK, "ghost_mast")
    # ghostly: the Dutchman's wood, masts and sails are drawn see-through (translucent render layer)
    for name, alpha in (("ghostwood_log", 200), ("ghostwood_log_top", 200), ("ghostwood_planks", 200), ("ghost_mast", 190), ("spectral_sail", 150)):
        f = BLOCK / f"{name}.png"
        im = Image.open(f).convert("RGBA")
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                r, g, b, a = px[x, y]
                if a: px[x, y] = (r, g, b, min(a, alpha))
        im.save(f)

    wood_set("charred", 31, ["#1e1a18", "#2a2421", "#342d29", "#40362f"], "#e0641e",
             ["#4a3e36", "#3a302a"], "#ff9a3c", streak="#8a3a1a",
             planks_args=("#3a3430", "#1e1a18", "#4a423c", None, "#dc6420"))
    save(leaves(32, ["#6a1a0e", "#9a2e12", "#d0501a", "#f07a28"], "#ffd26a", 9, holes=0.26), BLOCK, "ember_leaves")

    wood_set("wispwood", 41, ["#9a9c94", "#b0b2a8", "#c4c6bc", "#d6d8ce"], "#6e706a",
             ["#dcdcd2", "#c8c8be"], "#aab4b0", streak="#9adcd6",
             planks_args=("#c8cac0", "#8e9088", "#dadcd2"))
    save(leaves(42, ["#5e7a80", "#7896a0", "#94b4bc", "#b4d4da"], "#c8fff8", 7, holes=0.3), BLOCK, "wisp_leaves")

    wood_set("voidbloom", 51, ["#2e2250", "#3c2e66", "#4a3a7a", "#5a4890"], "#a070ff",
             ["#6a58a8", "#58488e"], "#c8a0ff", streak="#7a5ac8",
             planks_args=("#54448a", "#2e2450", "#6a58a6", None, "#b48cff"))
    save(leaves(52, ["#4a1e6e", "#6a2c8e", "#8e40b4", "#b25ad6"], "#ff9ae8", 8, holes=0.24), BLOCK, "voidbloom_leaves")

    # ---- Ashen (P3 volcanic ash trees) - previously had no textures at all
    ashen = bark(61, ["#3a3634", "#4a4542", "#5a5450", "#6a6460"], "#26221f", "#8a3a1a")
    save(ashen, BLOCK, "ashen_log")
    save(ashen, BLOCK, "ashen_wood")
    save(log_top(61, ashen, ["#7a726c", "#686058"], "#e0641e"), BLOCK, "ashen_log_top")
    s_ashen = bark(62, ["#8a8480", "#9a948e", "#aaa49e", "#bab4ae"], "#6e6864")
    save(s_ashen, BLOCK, "stripped_ashen_log")
    save(s_ashen, BLOCK, "stripped_ashen_wood")
    save(log_top(62, s_ashen, ["#b4aea8", "#a29c96"], "#8a8480"), BLOCK, "stripped_ashen_log_top")

    # ---- Driftwood (bleached, salt-worn)
    drift = bark(71, ["#8a847a", "#a09a8e", "#b6b0a2", "#ccc6b8"], "#6a645c", "#e0dccf")
    save(drift, BLOCK, "driftwood_block")
    save(log_top(71, drift, ["#c4bca8", "#b0a894"], "#9a927e"), BLOCK, "driftwood_block_top")

    # ---- Ambience blocks
    save(sprinkle(stone(81, ["#c8b48a", "#d6c49a", "#e2d2aa", "#ecdcb6"], cell=4), 82, "#f6e6e0", 10, glow="#e0a8a0"), BLOCK, "shell_block")
    save(sprinkle(stone(83, ["#4e5a56", "#5e6a64", "#6e7a72", "#808c82"], "#3a4440"), 84, "#5a8a4a", 8), BLOCK, "tide_pool_rock")
    save(sprinkle(stone(85, ["#b85a5a", "#cc6e6a", "#dc8478", "#e89a8a"], "#8a3a3e", 5), 86, "#f4c4b0", 6), BLOCK, "coral_rock")
    save(sprinkle(stone(87, ["#d6d2dc", "#e2dee8", "#ece8f2", "#f6f4fa"], cell=8), 88, "#ffffff", 6, glow="#c8e6ff"), BLOCK, "pearl_block")
    save(sprinkle(stone(89, ["#8a4a26", "#a0582c", "#b46a36", "#c47c44"], cell=2), 90, "#2a1a14", 10), BLOCK, "scorched_sand")
    save(stone(91, ["#b89a1e", "#d0b42a", "#e2c83a", "#f0dc5a"], "#8a6e10", 5), BLOCK, "sulfur_block")
    save(sprinkle(stone(93, ["#4a443e", "#56504a", "#625c54", "#6e6860"], cell=2), 94, "#c8c4b4", 6), BLOCK, "grave_silt")
    save(sprinkle(stone(95, ["#1e2428", "#283034", "#323a3e", "#3c4448"], "#14181a"), 96, "#8affff", 7, glow="#2e8a94"), BLOCK, "soul_barnacle")
    # Quench Valve: oxidized copper plate with a dark iron wheel
    save(sprinkle(stone(105, ["#3a6a5e", "#4a7a6a", "#5a8a78", "#6a9a86"], "#2a3a36", 3, cell=4), 106, "#1e2424", 18), BLOCK, "quench_valve")
    # Harpoon Winch (Bloodfin): tarred spruce drum lashed with rope; front shows the loaded harpoon tip (or an empty slot)
    wood = planks(109, "#5a4028", "#3a2818", "#6e5236")
    save(wood, BLOCK, "harpoon_winch_side")
    top_im = planks(110, "#5a4028", "#3a2818", "#6e5236")
    px = top_im.load()
    for y in range(16):
        for x in range(16):
            if 3 <= x <= 12 and y in (4, 5, 10, 11):
                px[x, y] = rgb("#c8b48a" if (x + y) % 2 else "#a8946a") + (255,)                  # rope turns
    save(top_im, BLOCK, "harpoon_winch_top")
    for name, loaded in (("harpoon_winch_front", True), ("harpoon_winch_front_empty", False)):
        im = planks(111, "#5a4028", "#3a2818", "#6e5236")
        px = im.load()
        for y in range(16):
            for x in range(16):
                d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
                if 4.2 <= d <= 5.4: px[x, y] = rgb("#5a5e66") + (255,)                          # iron muzzle ring
                elif d < 4.2: px[x, y] = rgb("#1a1410") + (255,)
        if loaded:
            for (x, y) in ((7, 5), (8, 5), (6, 6), (9, 6), (7, 6), (8, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9)):
                px[x, y] = rgb("#d8dee6" if y < 7 else "#8a6a42") + (255,)                     # harpoon tip + shaft
        save(im, BLOCK, name)
    # Manacle Anchor (Chained Revenant): a riveted iron plate with a hanging shackle, soul-light in the rivets
    iron = stone(113, ["#2a2c30", "#34373c", "#3e4248", "#484c52"], "#1a1c20", 3, cell=4)
    save(iron, BLOCK, "manacle_anchor_side")
    im = iron.copy()
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x in (1, 14) or y in (1, 14):
                px[x, y] = rgb("#5a5e66") + (255,)
            if (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
                px[x, y] = rgb("#6fe8d0") + (255,)                                             # glowing rivets
            d = ((x - 7.5) ** 2 + (y - 6.0) ** 2) ** 0.5
            if 2.6 <= d <= 3.8:
                px[x, y] = rgb("#8a9098" if (x + y) % 2 else "#6a7078") + (255,)             # the shackle ring
            if 6 <= x <= 9 and 10 <= y <= 12 and not (7 <= x <= 8 and y == 11):
                px[x, y] = rgb("#4a4e56") + (255,)                                             # the staple
    save(im, BLOCK, "manacle_anchor_front")
    # Tide Sluice (Abyssal King): dark prismarine frame, a bronze floodgate wheel over a drain grate.
    # Closed: grate dark, a faint cyan rune at the hub. Open: the grate glows and water streaks into it.
    sl_cols = ["#163a3a", "#1e4a48", "#265654", "#2e625e"]
    save(sprinkle(stone(107, sl_cols, "#0e2826", 4, cell=4), 108, "#6fe8d0", 5), BLOCK, "tide_sluice_side")
    for name, is_open in (("tide_sluice_front", False), ("tide_sluice_front_open", True)):
        im = stone(107, sl_cols, "#0e2826", 2, cell=4)
        px = im.load()
        for y in range(16):
            for x in range(16):
                if x in (0, 15) or y in (0, 15):
                    px[x, y] = rgb("#0c2220") + (255,)
                elif 2 <= x <= 13 and 2 <= y <= 13 and (x % 3 == 1 or y % 3 == 1):
                    px[x, y] = (rgb("#5ae8e0") if is_open else rgb("#0a1616")) + (255,)          # the grate
                elif 2 <= x <= 13 and 2 <= y <= 13:
                    px[x, y] = (rgb("#2a8a90") if is_open and (x + y) % 4 == 0 else rgb("#122a2a")) + (255,)
        cx, cy = 7.5, 7.5
        for y in range(16):
            for x in range(16):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if 5.0 <= d <= 6.2:
                    px[x, y] = rgb("#c8963c" if (x + y) % 3 else "#e0b84a") + (255,)            # wheel rim
                elif d < 5.0 and (abs(x - cx) < 0.6 or abs(y - cy) < 0.6):
                    px[x, y] = rgb("#a8782e") + (255,)                                            # spokes
                if d < 1.3:
                    px[x, y] = rgb("#8affe8" if is_open else "#3a9a90") + (255,)                  # hub rune
        save(im, BLOCK, name)
    # dynamite-only vault seal: broken rock, splintered timber, deep cracks
    # Tideward Stone: dark prismarine rune-block with a glowing pearl core seam
    save(sprinkle(sprinkle(stone(101, ["#1e4a4a", "#285a58", "#326a66", "#3c7a72"], "#6fe8ff", 4, cell=4), 102, "#d8f0ec", 10, glow="#9ff4ff"), 103, "#6fe8ff", 6), BLOCK, "serpent_ward")
    save(sprinkle(sprinkle(stone(97, ["#3e3a36", "#524c46", "#666058", "#7a746a"], "#1a1614", 7, cell=2), 98, "#6a4a2a", 12), 99, "#9a9284", 6), BLOCK, "blast_rubble")
    slate = stone(97, ["#141626", "#1c1e32", "#24263e", "#2c2e4a"], cell=2)
    banded(slate, 97, "#0c0e1a", every=5)
    save(slate, BLOCK, "abyssal_slate")
    lum = stone(99, ["#101420", "#161c2a", "#1c2434", "#222a3c"], cell=4)
    px = lum.load()
    rs = random.Random(99)
    for _ in range(3):
        x, y = rs.randrange(N), rs.randrange(N)
        for _ in range(9):
            px[x % N, y % N] = (120, 255, 240, 255)
            px[(x + 1) % N, y % N] = (40, 150, 170, 255)
            x += rs.choice((-1, 1)); y += rs.choice((0, 1, 1))
    save(lum, BLOCK, "luminous_vein")

    build_furniture()


# ---------------------------------------------------------------- shaped / functional blocks
def grid_img(rows: list[str], pal: dict) -> Image.Image:
    """Flat (no auto-shading) block sprite from a char grid."""
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = img.load()
    for y, r in enumerate(rows):
        for x, ch in enumerate(r):
            if ch in pal:
                c = pal[ch]
                px[x, y] = (*(rgb(c) if isinstance(c, str) else c), 255)
    return img


def overlay_noise(img: Image.Image, seed, amount=0.1) -> Image.Image:
    """Subtle per-pixel brightness jitter on opaque pixels (kills the flat-fill look)."""
    px = img.load()
    rs = random.Random(seed)
    for y in range(N):
        for x in range(N):
            r, g, b, a = px[x, y]
            if a:
                f = 1 + (rs.random() - 0.5) * 2 * amount
                px[x, y] = (min(255, int(r * f)), min(255, int(g * f)), min(255, int(b * f)), a)
    return img


def disc(fn) -> Image.Image:
    """16x16 image from fn(x, y, d) -> colour hex (d = distance from centre)."""
    img = Image.new("RGBA", (N, N))
    px = img.load()
    for y in range(N):
        for x in range(N):
            px[x, y] = (*rgb(fn(x, y, math.hypot(x - 7.5, y - 7.5))), 255)
    return img


def build_furniture() -> None:
    # Grog barrel: vertical staves + iron hoops + brass tap; lid with bung hole
    staves = ["#6a4428", "#7a5030", "#8a5c38", "#7a5030"]
    side = Image.new("RGBA", (N, N))
    px = side.load()
    for y in range(N):
        for x in range(N):
            c = rgb(staves[(x // 4) % 4])
            if x % 4 == 0:
                c = shade(c, 0.7)
            if y in (2, 3, 12, 13):
                c = rgb("#5a5a62") if y in (2, 12) else rgb("#2e2e34")
            px[x, y] = (*c, 255)
    for x, y in ((6, 7), (7, 7), (8, 7), (9, 7), (7, 8), (8, 8)):
        px[x, y] = (*rgb("#d9b04a" if y == 7 else "#8a6a1e"), 255)
    save(overlay_noise(side, 101, 0.06), BLOCK, "grog_barrel_side")

    def lid_px(x, y, d):
        if d > 7.2 or x in (0, 15) or y in (0, 15):
            return "#2e2e34"
        if d > 6.2:
            return "#5a5a62"
        c = staves[(y // 3) % 4]
        return c if y % 3 else "#4e321e"
    lid = disc(lid_px)
    save(overlay_noise(lid.copy(), 102, 0.05), BLOCK, "grog_barrel_bottom")
    for x, y in ((10, 5), (11, 5), (10, 6), (11, 6)):
        lid.load()[x, y] = (*rgb("#2a180c"), 255)
    save(overlay_noise(lid, 103, 0.05), BLOCK, "grog_barrel_top")

    # Ship waterline: tarred hull planks, white boot-top stripe, copper-red antifouling below
    wl = planks(111, "#4a3424", "#2a1c12", "#5a4230")
    px = wl.load()
    for y in range(8, N):
        for x in range(N):
            r, g, b, _ = px[x, y]
            px[x, y] = (232, 228, 214, 255) if y == 8 else (min(255, r + 70), max(0, g - 8), max(0, b - 6), 255)
    save(wl, BLOCK, "ship_waterline")

    # Ship mast: vertical spar with iron hoop + rope lashing; end-grain top
    mast = bark(121, ["#6a4a2c", "#7a5634", "#8a623c", "#9a6e44"], "#4e3620")
    px = mast.load()
    for x in range(N):
        px[x, 6] = (*rgb("#3a3a40"), 255)
        px[x, 7] = (*rgb("#5a5a62"), 255)
        for y in (11, 13):
            px[x, y] = (*rgb("#c8aa6e" if (x + y) % 2 else "#a08650"), 255)
        px[x, 12] = (*rgb("#b09460" if x % 2 else "#8a7040"), 255)
    save(mast, BLOCK, "ship_mast")
    save(log_top(121, mast, ["#b08a5a", "#9a7648"], "#7a5634"), BLOCK, "ship_mast_top")

    # Ship cannon: cast-iron barrel with reinforce rings, oak carriage w/ iron straps, bore
    iron = stone(131, ["#26282e", "#30333a", "#3a3d46", "#454852"], cell=8)
    px = iron.load()
    for y in (3, 4, 11, 12):
        for x in range(N):
            px[x, y] = (*rgb("#5a5e6a" if y in (3, 11) else "#1c1e22"), 255)
    save(iron, BLOCK, "ship_cannon")
    carriage = planks(132, "#7a5634", "#4a321e", "#8e663e")
    px = carriage.load()
    for y in range(N):
        for x in (1, 14):
            px[x, y] = (*rgb("#3a3a40"), 255)
    for x, y in ((1, 2), (14, 2), (1, 13), (14, 13)):
        px[x, y] = (*rgb("#8a8e98"), 255)
    save(carriage, BLOCK, "ship_cannon_carriage")
    save(disc(lambda x, y, d: "#0a0a0c" if d < 3.2 else "#5a5e6a" if d < 4.4 else "#3a3d46"), BLOCK, "ship_cannon_muzzle")

    # Ship helm: flat spoked wheel (cutout sprite) + wooden pedestal
    wheel = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = wheel.load()
    for y in range(N):
        for x in range(N):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            on_spoke = abs(dx) < 0.9 or abs(dy) < 0.9 or abs(abs(dx) - abs(dy)) < 0.9
            if 4.7 < d < 6.3:
                px[x, y] = (*rgb("#8a5c34" if (x + y) % 3 else "#6a4428"), 255)
            elif d <= 1.7:
                px[x, y] = (*rgb("#e6c35a" if d < 1.0 else "#9a7a2a"), 255)
            elif d <= 4.7 and on_spoke:
                px[x, y] = (*rgb("#7a5030"), 255)
            elif d >= 6.3 and on_spoke and d < 7.9:
                px[x, y] = (*rgb("#a06c3c"), 255)  # handles poke past the rim
    save(wheel, BLOCK, "ship_helm")
    save(planks(141, "#6a4428", "#3e2818", "#80563a"), BLOCK, "ship_helm_post")

    # Hammock: striped sailcloth
    ham = Image.new("RGBA", (N, N))
    px = ham.load()
    for y in range(N):
        for x in range(N):
            px[x, y] = (*rgb("#e8dcc0" if (x // 3) % 2 == 0 else "#b8423a"), 255)
    save(overlay_noise(ham, 151, 0.06), BLOCK, "hammock")

    # Bedroll: navy wool plaid with a pale stitched border
    bed = Image.new("RGBA", (N, N))
    px = bed.load()
    for y in range(N):
        for x in range(N):
            c = rgb("#2e3e6a")
            if x % 5 == 2 or y % 5 == 2:
                c = rgb("#4a5e94")
            if x % 5 == 2 and y % 5 == 2:
                c = rgb("#8a3a3a")
            if x in (0, 15) or y in (0, 15):
                c = rgb("#d8ccae")
            px[x, y] = (*c, 255)
    save(overlay_noise(bed, 152, 0.05), BLOCK, "ship_bedroll")

    # Water light: prismarine-and-brass frame around a rippling water core; ON is animated
    def water_frame(t: float, on: bool) -> Image.Image:
        img = Image.new("RGBA", (N, N))
        p = img.load()
        for y in range(N):
            for x in range(N):
                edge = min(x, y, 15 - x, 15 - y)
                if edge == 0:
                    c = rgb("#2e5a5a")
                elif edge == 1:
                    c = rgb("#c8a24a") if (x in (1, 14) and y in (1, 14)) else rgb("#4a8a82")
                else:
                    w = math.sin((x + y) * 0.9 + t) * 0.5 + math.sin((x - y) * 0.6 - t * 1.3) * 0.5
                    if on:
                        c = mix(rgb("#6ae6f0"), rgb("#f0ffff"), max(0.0, w) ** 1.5)
                    else:
                        c = mix(rgb("#1e4a6a"), rgb("#3e7a9a"), (w + 1) / 2)
                p[x, y] = (*c, 255)
        return img

    save(water_frame(0.0, False), BLOCK, "water_light_block")
    frames = [water_frame(i * (2 * math.pi / 8), True) for i in range(8)]
    strip = Image.new("RGBA", (N, N * 8))
    for i, f in enumerate(frames):
        strip.paste(f, (0, i * N))
    strip.save(BLOCK / "water_light_block_on.png", optimize=True)
    (BLOCK / "water_light_block_on.png.mcmeta").write_text(
        '{\n  "animation": {\n    "frametime": 3,\n    "interpolate": true\n  }\n}\n', encoding="ascii")
    SHEET.append(("water_light_block_on", frames[0]))

    # Hanging fruit: banana bunch = cross-model sprite; coconut = shell for a small cube
    save(grid_img([
        ".......ss.......",
        ".......ss.......",
        "......sggs......",
        ".....yyggyy.....",
        "....yYyggyYy....",
        "...yYy.gg.yYy...",
        "...yYyyggyyYy...",
        "..yYy.yggy.yYy..",
        "..yYy.yYYy.yYy..",
        "..yy..yYYy..yy..",
        "..b...yYYy...b..",
        "......yyyy......",
        "......b..b......",
        "................",
        "................",
        "................"], {"s": "#4a6a2a", "g": "#6a8a3a", "y": "#e8c83a", "Y": "#f6e27a", "b": "#5a3a1e"}), BLOCK, "banana_block")
    save(grid_img([
        ".......ss.......",
        ".......ss.......",
        "......sggs......",
        ".....yyggyy.....",
        "....yYyggyYy....",
        "...yYy.gg.yYy...",
        "...yYyyggyyYy...",
        "..yYy.yggy.yYy..",
        "..yYy.yYYy.yYy..",
        "..yy..yYYy..yy..",
        "..b...yYYy...b..",
        "......yyyy......",
        "......b..b......",
        "................",
        "................",
        "................"], {"s": "#4a6a2a", "g": "#6a8a3a", "y": "#7aa83a", "Y": "#a6cc5a", "b": "#5a3a1e"}), BLOCK, "banana_block_unripe")
    shell = stone(161, ["#4a2e1a", "#5a3820", "#6a4428", "#7a5030"], cell=2)
    px = shell.load()
    for x, y in ((6, 5), (9, 5), (7, 8)):
        px[x, y] = (*rgb("#1e120a"), 255)
    save(shell, BLOCK, "coconut_block")

    save(grid_img([
        "................",
        "................",
        "....ll....ll....",
        "...lLLl..lLLl...",
        "..lLl.ll.l.lLl..",
        "..l....lLl...l..",
        ".....lllLlll....",
        "....lL..t..Ll...",
        "...l....t....l..",
        "........t.......",
        "........t.......",
        ".......tt.......",
        ".......t........",
        ".......t........",
        "......ttt.......",
        "................"], {"l": "#3f8a33", "L": "#6cbf48", "t": "#7a5634"}), BLOCK, "shorewood_sapling")


# =====================================================================================
def write_sheet() -> None:
    scale, cols = 6, 12
    cell = 16 * scale + 8
    rows = math.ceil(len(SHEET) / cols)
    sheet = Image.new("RGBA", (cols * cell, rows * cell), (58, 58, 68, 255))
    for i, (_, img) in enumerate(SHEET):
        im = img if img.size[1] == img.size[0] else img.crop((0, 0, img.size[0], img.size[0]))
        im = im.resize((16 * scale, 16 * scale), Image.NEAREST)
        sheet.alpha_composite(im, ((i % cols) * cell + 4, (i // cols) * cell + 4))
    sheet.save(ROOT / "tools" / "overhaul_sheet.png")


if __name__ == "__main__":
    build_illustrated()
    build_gear()
    build_items16()
    build_blocks()
    write_sheet()
    print(f"wrote {len(SHEET)} textures; review tools/overhaul_sheet.png")
