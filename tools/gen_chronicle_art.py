"""Art for THE WEATHERED CHRONICLE (the lore book, 2026-10-01).

    python tools/gen_chronicle_art.py            -> everything
    python tools/gen_chronicle_art.py painted    -> only the painted art (art_src/chronicle/gen)
    python tools/gen_chronicle_art.py sketches   -> the old model-render ink sketches (sketch_<id>.png, unused)

Writes into assets/pixelpirates/textures/gui/chronicle/:
  book.png            1024x512 atlas at 2 texels per GUI pixel. The spread (704x440 = GUI 352x220) sits at 0,0; the
                      parts to its right MUST match the constants in client/screen/ChronicleScreen.java (B_*).
  art/<name>.png      illustrations. Cut from the concept sheet art_src/chronicle/weathered_chronicles.png (the user's
                      reference, NOT shipped) - "sketch" crops become ink with a transparent paper, "paint" crops keep
                      their colour inside a torn-paper edge.
  art/boss_<id>.png   sepia ink sketches of the ten chain bosses, rendered from their real GeckoLib models through
                      tools/preview_geo.py.
and textures/item/weathered_chronicle.png (32x32 from the concept's front cover).
Preview: tools/previews/chronicle_sheet.png. Needs `pip install pillow numpy`.
"""
import math, pathlib, random, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src/main/resources/assets/pixelpirates"
OUT = ASSETS / "textures/gui/chronicle"
ART = OUT / "art"
CONCEPT = ROOT / "art_src/chronicle/weathered_chronicles.png"
PREV = ROOT / "tools/previews"

INK = np.array([52, 34, 20], float)
S = 2                                   # texels per GUI pixel


def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], float)


def vnoise(w, h, cell, seed):
    """Smooth value noise in [0,1], shape (h, w)."""
    rnd = np.random.default_rng(seed)
    gw, gh = int(w / cell) + 3, int(h / cell) + 3
    g = rnd.random((gh, gw))
    ys, xs = np.mgrid[0:h, 0:w] / cell
    x0, y0 = xs.astype(int), ys.astype(int)
    tx, ty = xs - x0, ys - y0
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = g[y0, x0] * (1 - tx) + g[y0, x0 + 1] * tx
    b = g[y0 + 1, x0] * (1 - tx) + g[y0 + 1, x0 + 1] * tx
    return a * (1 - ty) + b * ty


def fbm(w, h, cell, seed, octaves=4):
    out, amp, tot = np.zeros((h, w)), 1.0, 0.0
    for o in range(octaves):
        out += vnoise(w, h, max(1.0, cell / 2 ** o), seed + o * 17) * amp
        tot += amp
        amp *= 0.5
    return out / tot


def lerp(a, b, t):
    return a + (b - a) * t[..., None]


# =====================================================================================
# THE BOOK
# =====================================================================================
LEATHER, LEATHER_D, LEATHER_L = rgb("#5e2e1c"), rgb("#24100a"), rgb("#8a4a2c")
PAPER, PAPER_D, BURN = rgb("#efdcb2"), rgb("#c9a56c"), rgb("#6a3e1c")
BRASS, BRASS_D, BRASS_L = rgb("#b8862e"), rgb("#5a3a10"), rgb("#f2d27a")
RED, RED_D = rgb("#8e1a14"), rgb("#4a0a08")

BW, BH = 352 * S, 220 * S               # the spread
# page rectangles in texels (GUI: left 12..174, right 178..340, y 8..212)
LP = (12 * S, 8 * S, 175 * S, 212 * S)
RP = (177 * S, 8 * S, 340 * S, 212 * S)


def leather(img):
    w, h = BW, BH
    n = fbm(w, h, 40, 3) * 0.6 + fbm(w, h, 6, 9) * 0.4
    col = lerp(LEATHER, LEATHER_D, np.clip(n * 1.2 - 0.2, 0, 1))
    ys, xs = np.mgrid[0:h, 0:w]
    edge = np.minimum.reduce([xs, ys, w - 1 - xs, h - 1 - ys])
    col = lerp(col, LEATHER_D, np.clip(1 - edge / 18, 0, 1) * 0.7)
    scr = vnoise(w, h, 3, 21) > 0.93                          # scuffs
    col[scr] = col[scr] * 0.55 + LEATHER_L * 0.45
    a = np.full((h, w), 255.0)
    # rounded corners
    r = 14
    for cx, cy in ((r, r), (w - r - 1, r), (r, h - r - 1), (w - r - 1, h - r - 1)):
        m = ((xs - cx) ** 2 + (ys - cy) ** 2 > r * r) & (np.abs(xs - cx) <= r) & (np.abs(ys - cy) <= r) \
            & (((xs < cx) if cx < w / 2 else (xs > cx)) & ((ys < cy) if cy < h / 2 else (ys > cy)))
        a[m] = 0
    img[..., :3] = col
    img[..., 3] = a
    # stitching 7 px in from the edge
    for x in range(20, w - 20, 10):
        for yy in (7, h - 9):
            img[yy:yy + 2, x:x + 5, :3] = rgb("#c8955a")
    for y in range(20, h - 20, 10):
        for xx in (7, w - 9):
            img[y:y + 5, xx:xx + 2, :3] = rgb("#c8955a")


def page_block(img, box, left):
    """Stacked page edges peeking out under a page (bottom and the outer side)."""
    x0, y0, x1, y1 = box
    for i in range(1, 7):
        c = PAPER_D * (0.72 + 0.04 * (i % 3)) if i % 2 else PAPER_D * 0.9
        if left:
            img[y0 + i:y1 + i, x0 - i:x0 - i + 1, :3] = c
        else:
            img[y0 + i:y1 + i, x1 + i - 1:x1 + i, :3] = c
        img[y1 + i - 1:y1 + i, x0 - (i if left else 0):x1 + (0 if left else i), :3] = c


def parchment(img, box, seed, left):
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    n = fbm(w, h, 60, seed) * 0.55 + fbm(w, h, 9, seed + 5) * 0.3 + fbm(w, h, 2, seed + 9, 2) * 0.15
    col = lerp(PAPER, PAPER_D, np.clip((n - 0.3) * 1.4, 0, 1))
    ys, xs = np.mgrid[0:h, 0:w]
    # outer edge (away from the spine) is ragged and burnt; the spine side falls into shadow
    outer = xs if left else (w - 1 - xs)
    inner = (w - 1 - xs) if left else xs
    rag = fbm(w, h, 7, seed + 30) * 10
    d = np.minimum.reduce([outer - rag * 0.6, ys - rag * 0.45, h - 1 - ys - rag * 0.45, inner + 30])
    col = lerp(col, BURN, np.clip(1 - d / 26, 0, 1) ** 2 * 0.85)
    col = lerp(col, PAPER_D * 0.55, np.clip(1 - inner / 34, 0, 1) ** 1.6 * 0.8)
    # coffee rings and blotches
    rnd = random.Random(seed)
    for _ in range(1):
        cx, cy, r = rnd.uniform(0.15, 0.85) * w, rnd.uniform(0.6, 0.9) * h, rnd.uniform(18, 34)
        dd = np.hypot(xs - cx, ys - cy)
        ring = np.exp(-((dd - r) / 2.2) ** 2) * 0.12 + (dd < r) * 0.05
        col = lerp(col, BURN, np.clip(ring * (0.6 + 0.8 * vnoise(w, h, 8, rnd.randrange(999))), 0, 1))
    for _ in range(5):
        cx, cy, r = rnd.uniform(0, 1) * w, rnd.uniform(0, 1) * h, rnd.uniform(20, 70)
        dd = np.hypot(xs - cx, ys - cy) / r
        col = lerp(col, PAPER_D * 0.85, np.clip(1 - dd, 0, 1) ** 2 * 0.35)
    # fibre specks
    sp = vnoise(w, h, 1.3, seed + 44) > 0.965
    col[sp] = col[sp] * 0.86
    alpha = np.where((outer < rag * 0.35) | (ys < rag * 0.25) | (h - 1 - ys < rag * 0.25), 0, 255)
    sub = img[y0:y1, x0:x1]
    m = alpha > 0
    sub[m, :3] = col[m]
    sub[m, 3] = 255


def brass_corner(img, cx, cy, fx, fy):
    """A brass corner plate over the cover corner; fx/fy = +1/-1 which way it opens."""
    size = 40
    for j in range(size):
        for i in range(size):
            if i + j > size:
                continue
            x, y = cx + i * fx, cy + j * fy
            if not (0 <= x < BW and 0 <= y < BH) or img[y, x, 3] == 0:
                continue
            edge = size - (i + j)
            t = (i + j) / size
            c = BRASS * (1 - t * 0.4) + BRASS_D * t * 0.4
            if edge < 3 or i < 2 or j < 2:
                c = BRASS_D
            elif edge < 5:
                c = BRASS_L * 0.7 + c * 0.3
            img[y, x, :3] = c
    for (i, j) in ((8, 8), (22, 6), (6, 22)):
        x, y = cx + i * fx, cy + j * fy
        img[y - 2:y + 3, x - 2:x + 3, :3] = BRASS_D
        img[y - 1:y + 1, x - 1:x + 1, :3] = BRASS_L


def book():
    img = np.zeros((512, 1024, 4), float)
    spread = np.zeros((BH, BW, 4), float)
    leather(spread)
    page_block(spread, LP, True)
    page_block(spread, RP, False)
    parchment(spread, LP, 11, True)
    parchment(spread, RP, 12, False)
    # the spine valley between the pages
    sx0, sx1 = LP[2], RP[0]
    spread[LP[1]:LP[3], sx0:sx1, :3] = LEATHER_D
    brass_corner(spread, 0, 0, 1, 1)
    brass_corner(spread, BW - 1, 0, -1, 1)
    brass_corner(spread, 0, BH - 1, 1, -1)
    brass_corner(spread, BW - 1, BH - 1, -1, -1)
    img[:BH, :BW] = spread
    parts(img)
    Image.fromarray(img.clip(0, 255).astype(np.uint8), "RGBA").save(OUT / "book.png")


# --- parts (positions in texels; GUI = /2) --------------------------------------------
TAB = (720, 0, 108, 36)          # tag GUI 54x18; selected tag is the next one down
ARROW = (720, 80, 36, 24)        # next page; prev = mirrored at +40; hover row at +26
STAMP_X = (704, 140, 128, 128)   # the red X over a slain boss portrait (GUI 64x64)
STAMP_SKULL = (840, 140, 72, 72) # skull seal (GUI 36x36)
SEAL = (920, 140, 56, 56)        # wax seal on a sealed page (GUI 28x28)
RULE = (704, 280, 280, 16)       # divider flourish (GUI 140x8)
TORN = (704, 300, 300, 120)      # torn-out stub (GUI 150x60), 9-sliced by the screen


def tag(img, x, y, w, h, bright):
    """A parchment label tag with a torn right end and a nail hole, like the concept's tabs."""
    rnd = np.random.default_rng(7 + bright)
    n = fbm(w, h, 10, 70 + bright)
    base = lerp(PAPER if bright else PAPER * 0.86, PAPER_D * (0.8 if bright else 0.7), np.clip(n * 1.2 - 0.2, 0, 1))
    ys, xs = np.mgrid[0:h, 0:w]
    rag = fbm(w, h, 4, 90 + bright) * 6
    d = np.minimum.reduce([ys - rag * 0.3, h - 1 - ys - rag * 0.3, w - 1 - xs - rag])
    base = lerp(base, BURN, np.clip(1 - d / 7, 0, 1) ** 1.5 * 0.9)
    a = np.where((w - 1 - xs) < rag * 0.5, 0, 255)
    img[y:y + h, x:x + w, :3] = base
    img[y:y + h, x:x + w, 3] = a
    img[y + h // 2 - 2:y + h // 2 + 2, x + 6:x + 10, :3] = LEATHER_D   # nail


def arrow(img, x, y, w, h, hover, flip):
    """A curling page corner with an ink arrow."""
    sub = np.zeros((h, w, 4))
    ys, xs = np.mgrid[0:h, 0:w]
    tri = xs + ys * (w / h) >= w - 1
    sub[tri, :3] = PAPER_D * (1.05 if hover else 0.9)
    sub[tri, 3] = 255
    sub[(np.abs(xs + ys * (w / h) - (w - 1)) < 1.5), :3] = BURN
    # arrow head
    ax, ay = w - 11, h - 8
    for i in range(7):
        sub[ay - i // 2 - 1:ay + i // 2 + 1, ax + i - 6:ax + i - 5, :3] = INK if not hover else RED
    sub[ay - 1:ay + 1, ax - 14:ax - 5, :3] = INK if not hover else RED
    if flip:
        sub = sub[:, ::-1]
    img[y:y + h, x:x + w] = sub


def stamp_x(img, x, y, w, h):
    """A slashed red ink X, bleeding at the ends."""
    im = Image.new("L", (w * 2, h * 2), 0)
    d = ImageDraw.Draw(im)
    rnd = random.Random(4)
    for (a, b) in (((14, 18), (w * 2 - 16, h * 2 - 12)), ((w * 2 - 18, 14), (16, h * 2 - 18))):
        for k in range(9):
            o = rnd.uniform(-5, 5)
            d.line([(a[0] + o, a[1] + rnd.uniform(-4, 4)), (b[0] + o, b[1] + rnd.uniform(-4, 4))], fill=255, width=rnd.randint(9, 15))
        for k in range(6):                         # drips / splatter at the ends
            px, py = (b[0] + rnd.uniform(-10, 10), b[1] + rnd.uniform(0, 14))
            r = rnd.uniform(2, 6)
            d.ellipse([px - r, py - r, px + r, py + r], fill=255)
    im = im.filter(ImageFilter.GaussianBlur(1.2)).resize((w, h), Image.LANCZOS)
    m = np.asarray(im, float) / 255
    n = fbm(w, h, 5, 88)
    a = np.clip(m * (0.75 + 0.35 * n) * 1.2, 0, 1) * 235
    col = lerp(np.broadcast_to(RED, (h, w, 3)), np.broadcast_to(RED_D, (h, w, 3)), n)
    img[y:y + h, x:x + w, :3] = col
    img[y:y + h, x:x + w, 3] = a


def skull(img, x, y, w, h, body, dark, light):
    """A small skull-and-crossbones seal."""
    im = Image.new("RGBA", (w * 4, h * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    W, H = w * 4, h * 4
    c = tuple(int(v) for v in body) + (255,)
    k = tuple(int(v) for v in dark) + (255,)
    l = tuple(int(v) for v in light) + (255,)
    d.ellipse([W * 0.06, H * 0.06, W * 0.94, H * 0.94], fill=k)
    d.ellipse([W * 0.12, H * 0.12, W * 0.88, H * 0.88], fill=c)
    for s in (-1, 1):                               # crossbones
        d.line([(W * 0.5 - s * W * 0.3, H * 0.62), (W * 0.5 + s * W * 0.3, H * 0.86)], fill=l, width=int(W * 0.07))
    d.ellipse([W * 0.3, H * 0.2, W * 0.7, H * 0.6], fill=l)
    d.rectangle([W * 0.38, H * 0.5, W * 0.62, H * 0.68], fill=l)
    d.ellipse([W * 0.36, H * 0.34, W * 0.47, H * 0.46], fill=k)
    d.ellipse([W * 0.53, H * 0.34, W * 0.64, H * 0.46], fill=k)
    d.polygon([(W * 0.5, H * 0.47), (W * 0.47, H * 0.53), (W * 0.53, H * 0.53)], fill=k)
    for i in range(3):
        d.line([(W * (0.43 + i * 0.07), H * 0.58), (W * (0.43 + i * 0.07), H * 0.67)], fill=k, width=3)
    im = im.resize((w, h), Image.LANCZOS)
    img[y:y + h, x:x + w] = np.asarray(im, float)


def rule(img, x, y, w, h):
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = tuple(int(v) for v in INK) + (200,)
    m = h // 2
    d.line([(20, m), (w // 2 - 16, m)], fill=c, width=2)
    d.line([(w // 2 + 16, m), (w - 20, m)], fill=c, width=2)
    d.polygon([(w // 2, m - 6), (w // 2 + 10, m), (w // 2, m + 6), (w // 2 - 10, m)], outline=c, fill=(*[int(v) for v in RED], 220))
    for s in (1, -1):
        ex = 16 if s == 1 else w - 16
        d.ellipse([ex - 3, m - 3, ex + 3, m + 3], fill=c)
    img[y:y + h, x:x + w] = np.asarray(im, float)


def torn(img, x, y, w, h):
    """The stub of a page torn from the binding: darker paper with ragged edges and a shadow."""
    n = fbm(w, h, 12, 123)
    col = lerp(np.broadcast_to(PAPER * 0.9, (h, w, 3)), np.broadcast_to(PAPER_D * 0.8, (h, w, 3)), n)
    ys, xs = np.mgrid[0:h, 0:w]
    rag = fbm(w, h, 5, 124) * 14
    d = np.minimum.reduce([xs - rag * 0.3, ys - rag * 0.5, w - 1 - xs - rag * 0.3, h - 1 - ys - rag])
    col = lerp(col, BURN, np.clip(1 - d / 10, 0, 1) ** 1.4 * 0.85)
    a = np.where(d < 0, 0, 255)
    img[y:y + h, x:x + w, :3] = col
    img[y:y + h, x:x + w, 3] = a


def parts(img):
    tag(img, TAB[0], TAB[1], TAB[2], TAB[3], 0)
    tag(img, TAB[0], TAB[1] + TAB[3] + 2, TAB[2], TAB[3], 1)
    for hov in (0, 1):
        arrow(img, ARROW[0], ARROW[1] + hov * 26, ARROW[2], ARROW[3], hov, False)
        arrow(img, ARROW[0] + 40, ARROW[1] + hov * 26, ARROW[2], ARROW[3], hov, True)
    stamp_x(img, *STAMP_X)
    skull(img, *STAMP_SKULL, RED, RED_D, rgb("#f0d8b0"))
    skull(img, *SEAL, rgb("#7a1410"), rgb("#3a0806"), rgb("#c0503a"))
    rule(img, *RULE)
    torn(img, *TORN)


# =====================================================================================
# ILLUSTRATIONS FROM THE CONCEPT SHEET
# =====================================================================================
# name: (box in the concept, mode, target GUI width). sketch = ink on transparent paper; paint = keep colour, torn edge.
CROPS = {
    "seascape": ((866, 175, 1150, 362), "sketch", 150),
    "compass": ((1232, 52, 1404, 222), "sketch", 90),
    "flag_corsairs": ((553, 562, 650, 620), "paint", 48),
    "flag_armada": ((553, 503, 650, 561), "paint", 48),
    "flag_merchants": ((553, 620, 650, 680), "paint", 48),
    "flag_drowned": ((553, 441, 650, 500), "paint", 48),
    "monkey": ((405, 735, 482, 812), "sketch", 56),
    "shark": ((278, 860, 398, 955), "sketch", 80),
    "skeleton": ((392, 830, 488, 982), "sketch", 60),
    "wreck": ((553, 915, 745, 975), "paint", 140),
    "cutlass": ((1056, 733, 1232, 783), "sketch", 90),
    "pistol": ((1060, 786, 1200, 845), "sketch", 70),
    "rum": ((1218, 738, 1278, 852), "sketch", 30),
    "palms": ((1395, 470, 1515, 672), "sketch", 70),
    "mountains": ((28, 575, 268, 668), "sketch", 140),
}


def to_sketch(im):
    """Ink on paper -> ink with alpha: the local paper colour is estimated by a wide blur of the brightest tones."""
    a = np.asarray(im.convert("RGB"), float)
    lum = a.mean(axis=2)
    paper = np.asarray(Image.fromarray(lum.astype(np.uint8)).filter(ImageFilter.MaxFilter(9)).filter(ImageFilter.GaussianBlur(12)), float)
    dark = np.clip((paper - lum) / np.maximum(paper, 1) * 1.9, 0, 1)
    dark = np.where(dark < 0.08, 0, dark)
    col = a * 0.55 + INK * 0.45
    out = np.dstack([col, dark * 255])
    # fade the crop border so no hard rectangle shows
    h, w = lum.shape
    ys, xs = np.mgrid[0:h, 0:w]
    e = np.minimum.reduce([xs, ys, w - 1 - xs, h - 1 - ys]) / (0.06 * min(w, h) + 1)
    out[..., 3] *= np.clip(e, 0, 1)
    return Image.fromarray(out.clip(0, 255).astype(np.uint8), "RGBA")


def to_paint(im, seed):
    """Keep the colour; give it a torn, burnt paper edge."""
    a = np.asarray(im.convert("RGB"), float)
    h, w = a.shape[:2]
    ys, xs = np.mgrid[0:h, 0:w]
    rag = fbm(w, h, max(2, w / 40), seed) * (0.08 * min(w, h))
    d = np.minimum.reduce([xs, ys, w - 1 - xs, h - 1 - ys]) - rag
    a = lerp(a, BURN * 0.8, np.clip(1 - d / (0.06 * min(w, h) + 1), 0, 1) ** 2 * 0.8)
    alpha = np.where(d < 0, 0, 255)
    return Image.fromarray(np.dstack([a, alpha]).clip(0, 255).astype(np.uint8), "RGBA")


def tint(im, rgbmul):
    a = np.asarray(im, float)
    a[..., :3] *= np.array(rgbmul)
    return Image.fromarray(a.clip(0, 255).astype(np.uint8), "RGBA")


def illustrations():
    src = Image.open(CONCEPT).convert("RGB")
    for i, (name, (box, mode, gw)) in enumerate(CROPS.items()):
        c = src.crop(box)
        tw = gw * S
        th = round(c.height * tw / c.width)
        c = c.resize((tw, th), Image.LANCZOS)
        out = to_sketch(c) if mode == "sketch" else to_paint(c, 300 + i)
        out.save(ART / f"{name}.png")


# =====================================================================================
# BOSS SKETCHES (from the real models)
# =====================================================================================
BOSSES = [  # (id, model, texture, yaw, pitch)
    ("captain_rackham", "captain_rackham", None, 28, 8),
    ("sea_serpent", "sea_serpent", None, 55, 12),
    ("molten_warlord", "molten_warlord", None, 25, 8),
    ("ghost_captain", "ghost_captain", None, 28, 8),
    ("abyssal_king", "abyssal_king", None, 25, 8),
    ("bloodfin", "bloodfin", None, 40, 14),
    ("kraken", "kraken", None, 20, 18),
    ("chained_revenant", "chained_revenant", None, 25, 8),
    ("abyssal_heart", "abyssal_heart", None, 30, 10),
    ("leviathan", "leviathan", None, 55, 14),
]
KEY = (255, 0, 255)


def ink_sketch(rgb_img):
    """A flat-shaded model render -> sepia ink: bold outlines, hatching by (normalised) tone, a faint wash."""
    a = np.asarray(rgb_img, float)
    mask = ~((np.abs(a - np.array(KEY)).sum(axis=2)) < 30)
    lum = a @ np.array([0.3, 0.55, 0.15]) / 255
    lo, hi = np.percentile(lum[mask], 4), np.percentile(lum[mask], 97)
    t = np.clip((lum - lo) / max(hi - lo, 1e-3), 0, 1)
    tone = np.where(mask, 1 - t, 0)                         # 0 light .. 1 dark
    tone = np.asarray(Image.fromarray((tone * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2.2)), float) / 255
    h, w = lum.shape
    gx = np.zeros((h, w)); gy = np.zeros((h, w))
    gx[:, 1:-1] = np.abs(a[:, 2:] - a[:, :-2]).sum(axis=2) / 3
    gy[1:-1, :] = np.abs(a[2:, :] - a[:-2, :]).sum(axis=2) / 3
    edge = (np.hypot(gx, gy) > 40) & mask
    m = Image.fromarray((mask * 255).astype(np.uint8))
    sil = (np.asarray(m.filter(ImageFilter.MaxFilter(7)), float) > 0) & ~(np.asarray(m.filter(ImageFilter.MinFilter(3)), float) > 0)
    edge = np.asarray(Image.fromarray((edge * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(3)), float) > 0
    ys, xs = np.mgrid[0:h, 0:w]
    sp = 9                                                   # hatch spacing at 512 px (-> ~4.5 at 256)
    ink = np.zeros((h, w))
    ink = np.maximum(ink, ((xs + ys) % sp < 3) * (tone > 0.28))
    ink = np.maximum(ink, ((xs - ys) % sp < 3) * (tone > 0.52))
    ink = np.maximum(ink, (ys % sp < 3) * (tone > 0.74))
    ink = np.maximum(ink, (tone > 0.9) * 1.0)
    ink = ink * mask * 0.85
    wash = mask * 0.16
    alpha = np.maximum.reduce([edge * 0.95, sil * 1.0, ink, wash])
    col = np.broadcast_to(INK, (h, w, 3)).copy()
    washcol = a * 0.2 + rgb("#9a7048") * 0.8
    linew = np.maximum.reduce([edge * 1.0, sil * 1.0, ink])
    col = lerp(washcol, col, np.clip(linew, 0, 1))
    out = np.dstack([col, np.clip(alpha, 0, 1) * 255])
    return Image.fromarray(out.clip(0, 255).astype(np.uint8), "RGBA")


def portraits():
    sys.path.insert(0, str(ROOT / "tools"))
    from preview_geo import render
    for bid, model, tex, yaw, pitch in BOSSES:
        im = render(model, "idle", 0.0, tex, yaw=yaw, pitch=pitch, size=512, bg=KEY, pad=24)
        sk = ink_sketch(im).resize((256, 256), Image.LANCZOS)
        sk.save(ART / f"sketch_{bid}.png")
        print("sketched", bid)


# =====================================================================================
# THE PAINTED ART (the user's ChatGPT images, art_src/chronicle/gen/ - prompts in
# D:/Minecraft Modding/Chronicle Art - Image Prompts.txt). 3 texels per GUI pixel; the screen draws them smoothed.
# =====================================================================================
GEN = ROOT / "art_src/chronicle/gen"
D = 3                                         # texels per GUI pixel for the paintings
# name: (mode, GUI width, crop box as fractions or None)
PAINTED = {
    **{f"boss_{b}": ("card", 88, (0.0, 0.03, 1.0, 0.86)) for b, *_ in [x for x in [
        ("captain_rackham",), ("sea_serpent",), ("molten_warlord",), ("ghost_captain",), ("abyssal_king",),
        ("bloodfin",), ("kraken",), ("chained_revenant",), ("abyssal_heart",), ("leviathan",)]]},
    **{f"sea_{i}": ("torn", 138, None) for i in range(1, 6)},
    **{f"place_{n}": ("torn", 138, None) for n in ("wavebreak", "rackhams_hold", "serpent_gate", "cinder_citadel",
                                                     "flying_dutchman", "sunken_court", "whalers_grave", "krakens_maw",
                                                     "gallows_grotto", "titans_chest", "leviathan_spire")},
    **{f"chapter_{n}": ("ink", 138, None) for n in ("world", "hunt", "fishing", "homestead", "log")},
}


def key_white(im):
    """Paintings delivered on a white surround (outside their own torn border): flood the near-white from the image
    edge to transparent, feathered by a pixel."""
    from scipy import ndimage
    a = np.asarray(im.convert("RGB"), float)
    white = (a.min(axis=2) > 228) & (a.max(axis=2) - a.min(axis=2) < 22)
    lab, _ = ndimage.label(white)
    edge = set(np.unique(np.concatenate([lab[0], lab[-1], lab[:, 0], lab[:, -1]]))) - {0}
    out = np.isin(lab, list(edge))
    alpha = np.where(out, 0, 255).astype(np.uint8)
    alpha = np.asarray(Image.fromarray(alpha).filter(ImageFilter.GaussianBlur(1.2)), float)
    return Image.fromarray(np.dstack([a, alpha]).clip(0, 255).astype(np.uint8), "RGBA")


def to_ink(im):
    """Sepia drawings on parchment: the paper dissolves into the book's own page, the strokes keep their colour."""
    a = np.asarray(im.convert("RGB"), float)
    lum = a.mean(axis=2)
    paper = np.asarray(Image.fromarray(lum.astype(np.uint8)).filter(ImageFilter.MaxFilter(15)).filter(ImageFilter.GaussianBlur(20)), float)
    dark = np.clip((paper - lum) / np.maximum(paper, 1) * 2.4, 0, 1) ** 0.9
    h, w = lum.shape
    ys, xs = np.mgrid[0:h, 0:w]
    e = np.minimum.reduce([xs, ys, w - 1 - xs, h - 1 - ys]) / (0.07 * min(w, h))
    alpha = dark * np.clip(e, 0, 1) * 255
    return Image.fromarray(np.dstack([a * 0.9, alpha]).clip(0, 255).astype(np.uint8), "RGBA")


def painted():
    for i, (name, (mode, gw, crop)) in enumerate(PAINTED.items()):
        f = next(iter(GEN.glob(name + ".*")), None)
        if f is None:
            print("missing painting", name); continue
        im = Image.open(f)
        if crop:
            W, H = im.size
            im = im.crop((int(crop[0] * W), int(crop[1] * H), int(crop[2] * W), int(crop[3] * H)))
        a = np.asarray(im.convert("RGB"))
        corners = [a[2, 2], a[2, -3], a[-3, 2], a[-3, -3]]
        on_white = sum(1 for c in corners if c.min() > 228) >= 3
        if mode == "ink":
            out = to_ink(im)
        elif mode == "torn" and on_white:
            out = key_white(im)
        else:                                       # full-bleed: give it a torn, burnt edge like the rest of the book
            out = to_paint(im.convert("RGB"), 700 + i)
        tw = gw * D
        th = round(out.height * tw / out.width)
        if mode == "torn" and on_white:             # trim the transparent margin first so the painting fills its width
            bb = out.getbbox()
            if bb: out = out.crop(bb); th = round(out.height * tw / out.width)
        small = out.resize((tw, th), Image.LANCZOS)
        # 256 colours with dithering: looks the same at book size, a quarter of the bytes in the jar
        small.quantize(256, method=Image.FASTOCTREE, dither=Image.FLOYDSTEINBERG).save(ART / f"{name}.png", optimize=True)
    print("painted", len(PAINTED))


# =====================================================================================
# ITEM ICON
# =====================================================================================
def item_icon():
    """The Weathered Chronicle's 32x32 icon, drawn pixel by pixel (the downscaled painting was mud at this size):
    worn red-brown leather with a darker spine and bands, brass corners, page edges showing on the right, a gold
    compass rose with a bone skull at its heart, and the torn red ribbon hanging out below."""
    C = {
        "K": (34, 16, 9), "L": (118, 54, 30), "l": (86, 38, 21), "d": (62, 26, 14), "h": (150, 78, 44),
        "G": (226, 172, 56), "g": (150, 100, 28), "Y": (255, 232, 140),
        "W": (238, 228, 204), "w": (176, 160, 132), "E": (40, 18, 12),
        "R": (178, 34, 30), "r": (112, 16, 14), "P": (236, 220, 184), "p": (186, 164, 124),
    }
    g = [["."] * 32 for _ in range(32)]
    def px(x, y, c):
        if 0 <= x < 32 and 0 <= y < 32: g[y][x] = c
    X0, X1, Y0, Y1 = 5, 25, 2, 28                    # the front cover
    rnd = random.Random(9)
    for y in range(Y0, Y1 + 1):
        for x in range(X0, X1 + 1):
            c = "L"
            if rnd.random() < 0.18: c = "l"
            if rnd.random() < 0.05: c = "h"
            if x == X0 + 1 or y == Y0 + 1: c = "h" if rnd.random() < 0.6 else c      # worn light edge top-left
            if x >= X1 - 1 or y >= Y1 - 1: c = "l"                                     # shade bottom-right
            px(x, y, c)
    for y in range(Y0, Y1 + 1):                                                         # spine
        for x in (X0 - 2, X0 - 1, X0):
            px(x, y, "d" if x < X0 else "l")
    for y in (Y0 + 4, Y0 + 5, Y1 - 5, Y1 - 4):                                          # spine bands
        for x in (X0 - 2, X0 - 1): px(x, y, "g" if y % 2 else "G")
    for y in range(Y0 + 1, Y1):                                                         # page edges
        px(X1 + 1, y, "P" if y % 2 else "p"); px(X1 + 2, y, "p")
    for x in range(X0 + 1, X1 + 2): px(x, Y1 + 1, "p")
    corners = [(X0, Y0, 1, 1), (X1, Y0, -1, 1), (X0, Y1, 1, -1), (X1, Y1, -1, -1)]      # brass corners
    for (cx, cy, sx, sy) in corners:
        for k in range(4):
            for m in range(4 - k):
                px(cx + sx * k, cy + sy * m, "G" if k + m < 2 else "g")
        px(cx + sx, cy + sy, "Y")
    ex, ey = 15, 14                                                                    # the compass rose
    for k in range(1, 8):
        c = "Y" if k < 3 else ("G" if k < 6 else "g")
        for (dx, dy) in ((0, -k), (0, k), (-k, 0), (k, 0)): px(ex + dx, ey + dy, c)
        if k < 4:
            for (dx, dy) in ((0, -k), (0, k), (-k, 0), (k, 0)):
                if dx == 0: px(ex - 1, ey + dy, "g"); px(ex + 1, ey + dy, "g")
                else: px(ex + dx, ey - 1, "g"); px(ex + dx, ey + 1, "g")
    for k in range(1, 5):                                                              # diagonal points
        for (sx, sy) in ((1, 1), (1, -1), (-1, 1), (-1, -1)): px(ex + sx * k, ey + sy * k, "g" if k > 2 else "G")
    for a in range(0, 360, 12):                                                        # the ring
        import math as _m
        px(round(ex + 5.5 * _m.cos(_m.radians(a))), round(ey + 5.5 * _m.sin(_m.radians(a))), "G")
    skull = ["  WWW  ",
             " WWWWW ",
             " WEWEW ",
             " WWWWW ",
             "  wEw  ",
             "  WwW  "]
    for j2, row in enumerate(skull):
        for i2, ch in enumerate(row):
            if ch != " ": px(ex - 3 + i2, ey - 3 + j2, ch)
    for y in range(Y1 - 3, 32):                                                        # the ribbon, torn at the end
        for x in (20, 21):
            px(x, y, "R" if x == 20 else "r")
    px(20, 31, "."); px(21, 30, "r"); px(19, 31, "."); px(21, 31, "R")
    # outline everything
    out = [row[:] for row in g]
    for y in range(32):
        for x in range(32):
            if g[y][x] != ".": continue
            if any(0 <= x + dx < 32 and 0 <= y + dy < 32 and g[y + dy][x + dx] != "." for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out[y][x] = "K"
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for y in range(32):
        for x in range(32):
            if out[y][x] != ".": img.putpixel((x, y), (*C[out[y][x]], 255))
    img.save(ASSETS / "textures/item/weathered_chronicle.png")
    img.resize((256, 256), Image.NEAREST).save(PREV / "chronicle_icon.png")


def preview():
    files = [OUT / "book.png"] + sorted(ART.glob("*.png"))
    sheet = Image.new("RGBA", (1400, 1500), (40, 34, 30, 255))
    x = y = rowh = 0
    for f in files:
        im = Image.open(f).convert("RGBA")
        if im.width > 700: im = im.resize((im.width // 1, im.height // 1))
        if x + im.width > 1400: x, y, rowh = 0, y + rowh + 6, 0
        bg = Image.new("RGBA", im.size, (232, 214, 172, 255)) if f.parent == ART else Image.new("RGBA", im.size, (40, 34, 30, 255))
        sheet.alpha_composite(Image.alpha_composite(bg, im), (x, y))
        x += im.width + 6
        rowh = max(rowh, im.height)
    sheet.save(PREV / "chronicle_sheet.png")


if __name__ == "__main__":
    ART.mkdir(parents=True, exist_ok=True)
    PREV.mkdir(parents=True, exist_ok=True)
    only = sys.argv[1:]
    if not only or "book" in only: book()
    if not only or "art" in only: illustrations()
    if "sketches" in only: portraits()                # old model sketches (sketch_<id>.png), superseded by the paintings
    if not only or "painted" in only: painted()
    if not only or "icon" in only: item_icon()
    preview()
