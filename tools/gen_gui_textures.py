"""GUI art for the Pirate Journal (skill tree) and the Shipwright (2026-10-01).

    python tools/gen_gui_textures.py   -> textures/gui/journal.png, textures/gui/shipyard.png (+ tools/previews/gui_*.png)

Both are 512x256 atlases; the sprite rectangles below MUST match the constants in
client/screen/PirateSkillScreen.java (J_*) and client/screen/ShipwrightScreen.java (S_*).
"""
import math, pathlib, random
from PIL import Image, ImageDraw

ROOT = pathlib.Path(__file__).resolve().parent.parent
GUI = ROOT / "src/main/resources/assets/pixelpirates/textures/gui"
PREV = ROOT / "tools/previews"


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def noise2(w, h, seed, cell=6):
    """Smooth value noise in [0,1]."""
    rnd = random.Random(seed)
    gw, gh = w // cell + 2, h // cell + 2
    g = [[rnd.random() for _ in range(gw)] for _ in range(gh)]
    out = [[0.0] * w for _ in range(h)]
    for y in range(h):
        for x in range(w):
            fx, fy = x / cell, y / cell
            x0, y0 = int(fx), int(fy)
            tx, ty = fx - x0, fy - y0
            tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
            a = g[y0][x0] * (1 - tx) + g[y0][x0 + 1] * tx
            b = g[y0 + 1][x0] * (1 - tx) + g[y0 + 1][x0 + 1] * tx
            out[y][x] = a * (1 - ty) + b * ty
    return out


def fill_tex(img, box, base, dark, seed, cell=6, speck=0.0, speck_col=None, edge=0):
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    n = noise2(w, h, seed, cell)
    n2 = noise2(w, h, seed + 7, 2)
    rnd = random.Random(seed + 3)
    px = img.load()
    for y in range(h):
        for x in range(w):
            t = n[y][x] * 0.7 + n2[y][x] * 0.3
            c = mix(base, dark, t * 0.8)
            if edge:
                d = min(x, y, w - 1 - x, h - 1 - y)
                if d < edge:
                    c = mix(c, dark, (1 - d / edge) * 0.55)
            if speck and rnd.random() < speck:
                c = speck_col or mix(c, dark, 0.6)
            px[x0 + x, y0 + y] = (*c, 255)


def rect(img, box, col, width=1):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = box
    for i in range(width):
        d.rectangle([x0 + i, y0 + i, x1 - 1 - i, y1 - 1 - i], outline=(*col, 255))


def stain(img, cx, cy, r, col, alpha=0.18, seed=1):
    px = img.load()
    rnd = random.Random(seed)
    for y in range(int(cy - r), int(cy + r)):
        for x in range(int(cx - r), int(cx + r)):
            d = math.hypot(x - cx, y - cy)
            ring = abs(d - r * 0.85) < 1.2
            if d < r and 0 <= x < img.width and 0 <= y < img.height:
                a = alpha * (0.35 if not ring else 1.6) * (0.8 + 0.4 * rnd.random())
                c = px[x, y][:3]
                px[x, y] = (*mix(c, col, min(1, a)), 255)


# =====================================================================================
# THE PIRATE JOURNAL
# =====================================================================================
LEATHER, LEATHER_D = rgb("#5a3420"), rgb("#2a160c")
PARCH, PARCH_D = rgb("#ecdcb4"), rgb("#b89a64")
INK = rgb("#3b2a1a")
GOLD, GOLD_D, GOLD_L = rgb("#d4a52a"), rgb("#7a5a14"), rgb("#ffe08a")
IRON, IRON_D = rgb("#8a8e96"), rgb("#3a3e46")
BRONZE, BRONZE_D = rgb("#b87a3a"), rgb("#5a3418")
WAX, WAX_D, WAX_L = rgb("#9a1c1c"), rgb("#5a0c0c"), rgb("#d04a3a")


def medallion(img, x, y, size, rim, rim_d, inner, inner_d, glow=None, gems=False, seed=0):
    """A round framed socket for a skill icon: outer rim, bevel, recessed centre."""
    px = img.load()
    r = size / 2 - 0.5
    cx, cy = x + size / 2 - 0.5, y + size / 2 - 0.5
    rnd = random.Random(seed)
    for j in range(size):
        for i in range(size):
            d = math.hypot(i + x - cx, j + y - cy)
            if d > r + 0.5:
                continue
            if d > r - 3:                         # rim with light from the top-left
                t = ((i - size / 2) + (j - size / 2)) / size * 0.9 + 0.5
                c = mix(rim, rim_d, max(0, min(1, t)))
                if d > r - 0.6: c = mix(c, rim_d, 0.6)
                if gems and abs(abs(i - size / 2 + 0.5) - abs(j - size / 2 + 0.5)) < 1 and d > r - 2.6:
                    c = rgb("#3ad8b8")
            else:                                 # recessed field
                t = ((i - size / 2) + (j - size / 2)) / size * -0.8 + 0.5
                c = mix(inner, inner_d, max(0, min(1, t * 0.8 + rnd.random() * 0.1)))
                if d > r - 4.2: c = mix(c, (0, 0, 0), 0.35)
            px[x + i, y + j] = (*c, 255)
    if glow:
        for j in range(size):
            for i in range(size):
                d = math.hypot(i + x - cx, j + y - cy)
                if r - 3.6 < d < r - 2.4:
                    c = px[x + i, y + j][:3]
                    px[x + i, y + j] = (*mix(c, glow, 0.7), 255)


def button(img, x, y, w, h, state):
    """A wax-seal style button bar: 0 normal, 1 hover, 2 disabled."""
    base, dark, hi = (WAX, WAX_D, WAX_L) if state < 2 else (rgb("#7a7066"), rgb("#4a443e"), rgb("#9a9086"))
    if state == 1:
        base, hi = mix(base, hi, 0.35), mix(hi, (255, 230, 200), 0.3)
    fill_tex(img, (x, y, x + w, y + h), base, dark, 400 + state, cell=3, edge=3)
    d = ImageDraw.Draw(img)
    d.line([x + 2, y + 1, x + w - 3, y + 1], fill=(*hi, 255))
    rect(img, (x, y, x + w, y + h), dark)


def journal():
    img = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    W, H = 320, 210
    # the leather cover
    fill_tex(img, (0, 0, W, H), LEATHER, LEATHER_D, 11, cell=4, speck=0.02, edge=6)
    d = ImageDraw.Draw(img)
    for (a, b) in [((3, 3), (W - 4, 3)), ((3, H - 4), (W - 4, H - 4)), ((3, 3), (3, H - 4)), ((W - 4, 3), (W - 4, H - 4))]:
        # stitching
        x0, y0 = a; x1, y1 = b
        n = int(max(abs(x1 - x0), abs(y1 - y0)) / 4)
        for k in range(n):
            t0, t1 = k / n, (k + 0.5) / n
            d.line([x0 + (x1 - x0) * t0, y0 + (y1 - y0) * t0, x0 + (x1 - x0) * t1, y0 + (y1 - y0) * t1], fill=(*rgb("#c8a870"), 255))
    # gold corner protectors
    for (cx, cy) in [(0, 0), (W - 14, 0), (0, H - 14), (W - 14, H - 14)]:
        for j in range(14):
            for i in range(14):
                ii = i if cx == 0 else 13 - i
                jj = j if cy == 0 else 13 - j
                if ii + jj < 14 and (ii < 3 or jj < 3 or ii + jj > 10):
                    img.putpixel((cx + i, cy + j), (*mix(GOLD, GOLD_D, (ii + jj) / 26), 255))
    # two parchment pages
    for (x0, x1, sd) in [(8, 158, 21), (162, 312, 22)]:
        fill_tex(img, (x0, 8, x1, H - 8), PARCH, PARCH_D, sd, cell=9, speck=0.01, edge=5)
    stain(img, 60, 150, 16, rgb("#8a6a3a"), seed=3)
    stain(img, 280, 40, 11, rgb("#8a6a3a"), seed=4)
    # spine shadow
    for x in range(150, 170):
        t = 1 - abs(x - 160) / 10
        for y in range(8, H - 8):
            c = img.getpixel((x, y))[:3]
            img.putpixel((x, y), (*mix(c, LEATHER_D, t * 0.8), 255))
    # faint ruled lines on the right page, a compass rose on the left
    for y in range(30, H - 14, 11):
        for x in range(170, 305):
            c = img.getpixel((x, y))[:3]
            img.putpixel((x, y), (*mix(c, PARCH_D, 0.22), 255))
    cx, cy = 83, 105
    for k in range(16):
        a = k * math.pi / 8
        L = 44 if k % 4 == 0 else (28 if k % 2 == 0 else 18)
        for t in range(L):
            x, y = int(cx + math.cos(a) * t), int(cy + math.sin(a) * t)
            c = img.getpixel((x, y))[:3]
            img.putpixel((x, y), (*mix(c, PARCH_D, 0.18), 255))
    for a in range(360):
        for rr in (20, 34):
            x, y = int(cx + math.cos(math.radians(a)) * rr), int(cy + math.sin(math.radians(a)) * rr)
            c = img.getpixel((x, y))[:3]
            img.putpixel((x, y), (*mix(c, PARCH_D, 0.15), 255))

    # node medallions 26x26 at v=212: locked, open, learned, maxed
    y = 212
    medallion(img, 0, y, 26, IRON, IRON_D, rgb("#4a4a50"), rgb("#2a2a30"), seed=1)
    medallion(img, 26, y, 26, BRONZE, BRONZE_D, rgb("#6a4a2a"), rgb("#3a2414"), glow=rgb("#ffcf6a"), seed=2)
    medallion(img, 52, y, 26, GOLD, GOLD_D, rgb("#5a3a1c"), rgb("#2e1a0c"), seed=3)
    medallion(img, 78, y, 26, GOLD_L, GOLD_D, rgb("#5a3a1c"), rgb("#2e1a0c"), glow=rgb("#fff4c0"), gems=True, seed=4)
    # capstone medallions 34x34 at u 104/138/172: locked, open, learned
    medallion(img, 104, y, 34, IRON, IRON_D, rgb("#3a3a42"), rgb("#1a1a20"), seed=5)
    medallion(img, 138, y, 34, BRONZE, BRONZE_D, rgb("#5a3a1c"), rgb("#2a1a0c"), glow=rgb("#ffcf6a"), gems=True, seed=6)
    medallion(img, 172, y, 34, GOLD_L, GOLD_D, rgb("#4a2a14"), rgb("#200e06"), glow=rgb("#fff4c0"), gems=True, seed=7)
    # selection ring (30x30) at u 206
    for j in range(30):
        for i in range(30):
            dd = math.hypot(i - 14.5, j - 14.5)
            if 13 < dd < 15:
                img.putpixel((206 + i, y + j), (*GOLD_L, 255 if (i + j) % 2 == 0 else 180))
    # buttons 64x18 (normal/hover/disabled) at u=330 v=0,18,36; small 84x14 at v=56,70,84
    for k in range(3):
        button(img, 330, k * 18, 64, 18, k)
        button(img, 330, 56 + k * 14, 84, 14, k)
    # bookmark ribbons 28x26 at u=330 v=100 (grey - tinted in code) : inactive, active (longer)
    for k, hgt in enumerate((20, 26)):
        x0 = 330 + k * 30
        fill_tex(img, (x0, 100, x0 + 28, 100 + hgt), rgb("#e8e8e8"), rgb("#9a9a9a"), 50 + k, cell=3, edge=2)
        for i in range(28):                          # swallow-tail notch at the bottom
            cut = abs(i - 13.5) < 5 and (100 + hgt - 1 - (5 - abs(i - 13.5)) < 100 + hgt)
            for t in range(int(5 - abs(i - 13.5)) if abs(i - 13.5) < 5 else 0):
                img.putpixel((x0 + i, 100 + hgt - 1 - t), (0, 0, 0, 0))
    # XP bar: rope frame 104x8 at v=130, fill 100x4 at v=140 (teal sea-glass)
    fill_tex(img, (330, 130, 434, 138), rgb("#8a6a3e"), rgb("#4a341c"), 60, cell=2, edge=2)
    for i in range(330, 434, 3):
        img.putpixel((i, 130), (*rgb("#c8a870"), 255)); img.putpixel((i + 1, 137), (*rgb("#c8a870"), 255))
    for i in range(332, 432):
        for j in range(132, 136):
            img.putpixel((i, j), (*rgb("#2a1a10"), 255))
    fill_tex(img, (330, 140, 430, 144), rgb("#4ae0c8"), rgb("#1a8a7a"), 61, cell=2)
    for i in range(330, 430):
        img.putpixel((i, 140), (*rgb("#c8fff4"), 255))
    # coin icon 9x9 at 330,150
    for j in range(9):
        for i in range(9):
            dd = math.hypot(i - 4, j - 4)
            if dd < 4.5:
                img.putpixel((330 + i, 150 + j), (*(mix(GOLD_L, GOLD_D, (i + j) / 16) if dd < 3.4 else GOLD_D), 255))
    return img


# =====================================================================================
# THE SHIPYARD LEDGER (Shipwright)
# =====================================================================================
BLUE, BLUE_D = rgb("#1e4a7a"), rgb("#0e2440")
WOOD, WOOD_D = rgb("#6a4526"), rgb("#2e1a0c")


def shipyard():
    img = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    W, H = 300, 206
    # a timber board frame (planks) with brass corners
    fill_tex(img, (0, 0, W, H), WOOD, WOOD_D, 71, cell=5, edge=5)
    d = ImageDraw.Draw(img)
    for y in range(0, H, 12):
        d.line([0, y, W, y], fill=(*mix(WOOD, WOOD_D, 0.7), 255))
    # blueprint sheet
    fill_tex(img, (8, 8, W - 8, H - 8), BLUE, BLUE_D, 72, cell=11, edge=6)
    for x in range(8, W - 8, 10):
        for y in range(8, H - 8):
            c = img.getpixel((x, y))[:3]; img.putpixel((x, y), (*mix(c, rgb("#6aa0d8"), 0.18), 255))
    for y in range(8, H - 8, 10):
        for x in range(8, W - 8):
            c = img.getpixel((x, y))[:3]; img.putpixel((x, y), (*mix(c, rgb("#6aa0d8"), 0.18), 255))
    # a faint hull drawing in the background
    for t in range(0, 360):
        a = math.radians(t)
        x, y = 150 + math.cos(a) * 110, 118 + math.sin(a) * 34
        if y > 118:
            for w in (0, 1):
                c = img.getpixel((int(x), int(y) + w))[:3]; img.putpixel((int(x), int(y) + w), (*mix(c, rgb("#a8d0ff"), 0.22), 255))
    for x in range(44, 258):
        c = img.getpixel((x, 118))[:3]; img.putpixel((x, 118), (*mix(c, rgb("#a8d0ff"), 0.22), 255))
    for y in range(40, 118):
        for x in (148, 149):
            c = img.getpixel((x, y))[:3]; img.putpixel((x, y), (*mix(c, rgb("#a8d0ff"), 0.2), 255))
    # brass corners + pins
    for (cx, cy) in [(4, 4), (W - 9, 4), (4, H - 9), (W - 9, H - 9)]:
        for j in range(5):
            for i in range(5):
                if math.hypot(i - 2, j - 2) < 2.6:
                    img.putpixel((cx + i, cy + j), (*mix(GOLD_L, GOLD_D, (i + j) / 8), 255))
    # card 132x28 (normal / hover / selected / locked) at u=310, v=0,28,56,84
    for k, (base, dark, border) in enumerate([(rgb("#2a5a8e"), rgb("#15304e"), rgb("#8ac0f0")),
                                              (rgb("#3a70a8"), rgb("#1e4068"), rgb("#d8f0ff")),
                                              (rgb("#6a5a2a"), rgb("#3a2e14"), GOLD_L),
                                              (rgb("#3a4450"), rgb("#1e242c"), rgb("#6a7480"))]):
        y0 = k * 28
        fill_tex(img, (310, y0, 442, y0 + 28), base, dark, 80 + k, cell=5, edge=3)
        rect(img, (310, y0, 442, y0 + 28), border)
    # brass buttons 64x18 at u=310 v=120/138/156 (normal/hover/disabled)
    for k in range(3):
        base, dark = [(rgb("#b88a2e"), rgb("#5a3e10")), (rgb("#d8aa4a"), rgb("#7a5a1e")), (rgb("#7a7066"), rgb("#4a443e"))][k]
        fill_tex(img, (310, 120 + k * 18, 374, 138 + k * 18), base, dark, 90 + k, cell=3, edge=3)
        rect(img, (310, 120 + k * 18, 374, 138 + k * 18), dark)
    # tab plates 96x18 (inactive/active) at u=380 v=120/138
    for k in range(2):
        base, dark = [(WOOD, WOOD_D), (rgb("#a8783e"), rgb("#4a2e12"))][k]
        fill_tex(img, (380, 120 + k * 18, 476, 138 + k * 18), base, dark, 95 + k, cell=3, edge=2)
        rect(img, (380, 120 + k * 18, 476, 138 + k * 18), GOLD_D if k else WOOD_D)
    # hull bar frame 150x8 at v=180, fills 146x4 at v=190 (green) / v=196 (red)
    fill_tex(img, (310, 180, 460, 188), rgb("#8a6a3e"), rgb("#4a341c"), 97, cell=2, edge=2)
    for i in range(312, 458):
        for j in range(182, 186):
            img.putpixel((i, j), (*rgb("#141414"), 255))
    fill_tex(img, (310, 190, 456, 194), rgb("#4ac86a"), rgb("#1a6a2e"), 98, cell=2)
    fill_tex(img, (310, 196, 456, 200), rgb("#e05a3a"), rgb("#7a1a0e"), 99, cell=2)
    # level pip 6x6 empty/full at u=310/318 v=204
    for k in range(2):
        for j in range(6):
            for i in range(6):
                edge = i in (0, 5) or j in (0, 5)
                img.putpixel((310 + k * 8 + i, 204 + j), (*(GOLD_D if edge else (GOLD if k else rgb("#1a2e44"))), 255))
    return img


# =====================================================================================
# THE PORT TRADERS' COUNTER (vanilla villager2 layout, repainted - mixin/PortTraderScreenMixin swaps it in)
# =====================================================================================
def merchant():
    """Repaint vanilla's villager2.png pixel-for-pixel (so every slot, arrow and scrollbar still lines up) in the
    port's colours: parchment panels, a dark-wood offer list and frame. Offer buttons (88x20) at u=380 v=150/170/190."""
    import io, zipfile
    jar = pathlib.Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"
    src = Image.open(io.BytesIO(zipfile.ZipFile(jar).read("assets/minecraft/textures/gui/container/villager2.png"))).convert("RGBA")
    img = src.copy()
    W, H = 276, 166
    par = Image.new("RGBA", (W, H)); fill_tex(par, (0, 0, W, H), PARCH, PARCH_D, 301, cell=9, speck=0.01)
    wood = Image.new("RGBA", (W, H)); fill_tex(wood, (0, 0, W, H), rgb("#5a3a20"), rgb("#2a160c"), 302, cell=4)
    pp, wp, sp, ip = par.load(), wood.load(), src.load(), img.load()
    for y in range(256):
        for x in range(512):
            c = sp[x, y]
            if c[3] == 0: continue
            k = c[:3]
            inside = x < W and y < H
            if k == (198, 198, 198):                        # panel face
                ip[x, y] = pp[x % W, y % H] if inside else (*PARCH, 255)
            elif k == (139, 139, 139):                      # slots + the offer list well
                ip[x, y] = (*mix(wp[x % W, y % H][:3], (0, 0, 0), 0.15), 255) if (inside and x < 100) else (*rgb("#b89a64"), 255)
            elif k == (255, 255, 255):
                ip[x, y] = (*rgb("#fff4d8"), 255)
            elif k in ((85, 85, 85), (55, 55, 55), (74, 74, 74), (45, 45, 45)):
                ip[x, y] = (*rgb("#4a2e16"), 255)
            elif k == (0, 0, 0):
                ip[x, y] = (*rgb("#1e0e06"), 255)
    # a wooden frame round the panel with gold corners
    for y in range(H):
        for x in range(W):
            d = min(x, y, W - 1 - x, H - 1 - y)
            if 0 < d < 4 and sp[x, y][3] and sp[x, y][:3] != (0, 0, 0):
                ip[x, y] = wp[x, y]
    for (cx, cy) in [(1, 1), (W - 7, 1), (1, H - 7), (W - 7, H - 7)]:
        for j in range(6):
            for i in range(6):
                if i + j < 6 and (i < 2 or j < 2):
                    ii, jj = (i if cx < 10 else 5 - i), (j if cy < 10 else 5 - j)
                    ip[cx + ii, cy + jj] = (*mix(GOLD, GOLD_D, (i + j) / 10), 255)
    # offer buttons 88x20: normal / hover / out of stock
    for k, (base, dark) in enumerate([(PARCH, PARCH_D), (rgb("#fff0c8"), rgb("#d8b870")), (rgb("#9a8a70"), rgb("#6a5a44"))]):
        fill_tex(img, (380, 150 + k * 20, 468, 170 + k * 20), base, dark, 310 + k, cell=5, edge=3)
        rect(img, (380, 150 + k * 20, 468, 170 + k * 20), rgb("#4a2e16"))
    return img


# =====================================================================================
# THE MAP MERCHANT'S SEA CHART
# =====================================================================================
def chart():
    """A sepia sea chart (300x210): coastlines, rhumb lines, a compass rose; cards 92x44 at u=320 v=0/44/88 (normal,
    hover, owned/can't afford); a wax seal 14x14 at u=320 v=140."""
    img = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    W, H = 300, 210
    fill_tex(img, (0, 0, W, H), rgb("#e0c890"), rgb("#9a7a44"), 201, cell=10, speck=0.012, edge=8)
    px = img.load()
    rnd = random.Random(9)
    # coastlines: a few blobby islands in darker sepia with a hatched shore
    for (cx, cy, r) in [(60, 60, 26), (230, 150, 32), (120, 170, 18), (250, 40, 14)]:
        pts = [(cx + math.cos(a) * r * (0.7 + 0.3 * math.sin(a * 3 + cx)), cy + math.sin(a) * r * (0.75 + 0.25 * math.cos(a * 2 + cy)))
               for a in [i * math.pi / 24 for i in range(48)]]
        d = ImageDraw.Draw(img)
        d.polygon(pts, fill=(*rgb("#c8a868"), 255), outline=(*rgb("#6a4a24"), 255))
        for t in range(0, 48, 2):
            x, y = pts[t]
            for k in range(1, 4):
                xx, yy = int(cx + (x - cx) * (1 + k * 0.06)), int(cy + (y - cy) * (1 + k * 0.06))
                if 0 <= xx < W and 0 <= yy < H:
                    c = px[xx, yy][:3]; px[xx, yy] = (*mix(c, rgb("#8a6a3a"), 0.4), 255)
    # rhumb lines from a compass rose
    rx, ry = 262, 178
    for k in range(16):
        a = k * math.pi / 8
        for t in range(0, 420, 1):
            x, y = int(rx + math.cos(a) * t), int(ry + math.sin(a) * t)
            if 6 < x < W - 6 and 6 < y < H - 6 and t % 4 < 3:
                c = px[x, y][:3]; px[x, y] = (*mix(c, rgb("#7a5a30"), 0.18), 255)
    for k in range(8):
        a = k * math.pi / 4
        L = 22 if k % 2 == 0 else 13
        for t in range(L):
            for w in range(-1 if k % 2 == 0 else 0, 2 if k % 2 == 0 else 1):
                x, y = int(rx + math.cos(a) * t - math.sin(a) * w), int(ry + math.sin(a) * t + math.cos(a) * w)
                px[x, y] = (*(rgb("#8a2a1a") if k == 6 else rgb("#4a3018")), 255)
    for a in range(360):
        for rr in (8, 9):
            px[int(rx + math.cos(math.radians(a)) * rr), int(ry + math.sin(math.radians(a)) * rr)] = (*rgb("#4a3018"), 255)
    # an inked border
    rect(img, (4, 4, W - 4, H - 4), rgb("#5a3a1a"))
    rect(img, (6, 6, W - 6, H - 6), rgb("#8a6a3a"))
    # cards 92x44: normal / hover / owned-or-unaffordable
    for k, (base, dark, edge) in enumerate([(rgb("#f0e0b8"), rgb("#c8a870"), rgb("#6a4a24")),
                                            (rgb("#fff4d0"), rgb("#e0c080"), rgb("#8a2a1a")),
                                            (rgb("#c8b898"), rgb("#9a8a6a"), rgb("#6a5a44"))]):
        fill_tex(img, (320, k * 44, 412, k * 44 + 44), base, dark, 220 + k, cell=6, edge=3)
        rect(img, (320, k * 44, 412, k * 44 + 44), edge)
        for (x, y) in [(323, k * 44 + 3), (408, k * 44 + 3)]:            # brass pins
            for j in range(2):
                for i in range(2):
                    px[x + i, y + j] = (*GOLD, 255)
    # a wax seal 14x14
    for j in range(14):
        for i in range(14):
            dd = math.hypot(i - 6.5, j - 6.5) + (rnd.random() - 0.5) * 0.8
            if dd < 6.8:
                px[320 + i, 140 + j] = (*(mix(WAX_L, WAX_D, (i + j) / 26) if dd < 5 else WAX_D), 255)
    return img

def bounty():
    """BOUNTY BOARD screen (BountyScreen): a 300x200 cork board in a dark wooden frame at 0,0; wanted posters 88x120 at
    u=400 v=0 (open) and v=122 (paid: green tick stamp); wax buttons 120x18 at v=210, u=0/122/244 (normal/hover/disabled)."""
    img = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    W, H = 300, 200
    fill_tex(img, (0, 0, W, H), rgb("#5a3a20"), rgb("#2a1a0c"), 301, cell=4, edge=2)            # frame
    for y in range(4, H - 4, 9):                                                                    # plank seams
        rect(img, (2, y, 10, y + 1), rgb("#3a2410")); rect(img, (W - 10, y, W - 2, y + 1), rgb("#3a2410"))
    fill_tex(img, (10, 10, W - 10, H - 10), rgb("#b8864a"), rgb("#7a5228"), 302, cell=2, speck=0.08, speck_col=rgb("#5a3818"), edge=3)
    rect(img, (10, 10, W - 10, H - 10), rgb("#2a1a0c"))
    px = img.load()
    rnd = random.Random(3)
    for _ in range(14):                                                                             # old pin holes + scraps
        x, y = rnd.randint(16, W - 18), rnd.randint(16, H - 18)
        px[x, y] = (*rgb("#3a2410"), 255)
    for k, (base, dark) in enumerate([(rgb("#ecd9a8"), rgb("#b8995e")), (rgb("#dfe4b8"), rgb("#a8a870"))]):
        x0, y0 = 400, k * 122
        fill_tex(img, (x0, y0, x0 + 88, y0 + 120), base, dark, 310 + k, cell=5, speck=0.01, edge=4)
        for i in range(0, 88, 3):                                                                 # torn bottom edge
            for j in range(rnd.randint(0, 3)):
                px[x0 + i, y0 + 119 - j] = (0, 0, 0, 0)
                if i + 1 < 88: px[x0 + i + 1, y0 + 119 - j] = (0, 0, 0, 0)
        rect(img, (x0 + 4, y0 + 4, x0 + 84, y0 + 20), rgb("#6a2a1a"))                              # header box
        for j in range(4):                                                                         # red pin
            for i in range(4):
                if (i - 1.5) ** 2 + (j - 1.5) ** 2 < 4.5: px[x0 + 42 + i, y0 + 1 + j] = (*(WAX_L if i + j < 3 else WAX), 255)
        if k == 1:                                                                                 # PAID stamp
            for j in range(26):
                for i in range(26):
                    dd = math.hypot(i - 12.5, j - 12.5)
                    tick = (6 <= i <= 11 and abs(j - (i + 7)) < 2) or (11 <= i <= 20 and abs(j - (29 - i)) < 2)
                    if 10 < dd < 12.5 or tick: px[x0 + 56 + i, y0 + 86 + j] = (*rgb("#2a7a2a"), 230)
    for st in range(3):
        button(img, st * 122, 210, 120, 18, st)
    return img


def radar():
    """SHIP RADAR as a brass compass (ShipRadarHud): body 96x96 at 0,0 (brass rim + sea-chart dial with a rose and N/E/S/W);
    needle 10x44 at u=100 (pivot at its centre); own-ship pip 9x9 at u=112 v=0; enemy pip 9x9 at u=112 v=10;
    glass glint 96x96 at u=128 (drawn over everything)."""
    img = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    px = img.load()
    c, R = 47.5, 47.5
    n = noise2(96, 96, 77, cell=5)
    for y in range(96):
        for x in range(96):
            d = math.hypot(x - c, y - c)
            if d > R: continue
            if d > R - 7:                                                    # brass rim, lit from the top-left
                t = (x - y) / 96 * 0.5 + 0.5
                col = mix(GOLD_D, GOLD_L, t * 0.8 + n[y][x] * 0.2)
                if R - 7 < d < R - 6 or d > R - 1: col = mix(col, GOLD_D, 0.7)
            else:                                                            # parchment sea chart
                col = mix(rgb("#e8d4a0"), rgb("#b8995e"), n[y][x] * 0.6 + d / R * 0.35)
            px[x, y] = (*col, 255)
    # rose: 16 rhumb lines + an 8-point star + rings
    for k in range(16):
        a = k * math.pi / 8
        for t in range(6, 40):
            x, y = int(c + math.cos(a) * t), int(c + math.sin(a) * t)
            if t % 3: cc = px[x, y][:3]; px[x, y] = (*mix(cc, rgb("#7a5a30"), 0.25), 255)
    dr = ImageDraw.Draw(img)
    for k in (1, 3, 5, 7, 0, 2, 4, 6):                                       # minor points first, cardinal points on top
        a = k * math.pi / 4 - math.pi / 2
        L, w = (31, 4.5) if k % 2 == 0 else (19, 3.0)
        tip = (c + math.cos(a) * L, c + math.sin(a) * L)
        left = (c + math.cos(a - math.pi / 2) * w, c + math.sin(a - math.pi / 2) * w)
        right = (c + math.cos(a + math.pi / 2) * w, c + math.sin(a + math.pi / 2) * w)
        dark = rgb("#8a2a1a") if k == 0 else rgb("#4a3018")
        light = rgb("#c8503a") if k == 0 else rgb("#9a7a48")
        dr.polygon([(c, c), left, tip], fill=(*light, 255))
        dr.polygon([(c, c), right, tip], fill=(*dark, 255))
    for rr in (12, 38.5):
        for a in range(720):
            x, y = int(c + math.cos(math.radians(a / 2)) * rr), int(c + math.sin(math.radians(a / 2)) * rr)
            px[x, y] = (*rgb("#5a3a1a"), 255)
    # N E S W engraved on the dial
    letters = {"N": ["0110", "1001", "1101", "1011", "1001"], "E": ["1111", "1000", "1110", "1000", "1111"],
               "S": ["0111", "1000", "0110", "0001", "1110"], "W": ["1001", "1001", "1011", "1111", "1001"]}
    for ch, (lx, ly) in {"N": (46, 9), "S": (46, 82), "W": (9, 45), "E": (83, 45)}.items():
        for j, row in enumerate(letters[ch]):
            for i, b in enumerate(row):
                if b == "1": px[lx - 2 + i, ly + j] = (*(rgb("#8a2a1a") if ch == "N" else INK), 255)
    # needle 10x44: red north half, steel south half, brass cap
    for y in range(44):
        half = (22 - abs(y - 21.5)) / 22 * 4.5
        for x in range(10):
            if abs(x - 4.5) <= half:
                col = mix(WAX, WAX_L, 0.5 if x < 5 else 0) if y < 22 else mix(rgb("#8a9aa8"), rgb("#d8e4ec"), 0.5 if x < 5 else 0)
                px[100 + x, y] = (*col, 255)
    for j in range(4):
        for i in range(4):
            px[100 + 3 + i, 20 + j] = (*GOLD_L, 255) if i + j < 3 else (*GOLD, 255)
    # pips 9x9: own ship (gold sail on a hull), enemy (red skull disc)
    ship = ["....s....", "...ss....", "..sss....", ".ssss....", "...m.....", "hhhhhhhh.", ".hhhhhh..", "..hhhh...", "........."]
    for j, row in enumerate(ship):
        for i, ch in enumerate(row):
            if ch != ".": px[112 + i, j] = (*{"s": rgb("#fff0c0"), "m": rgb("#5a3a1a"), "h": GOLD}[ch], 255)
    skull = [".rrrrrrr.", "rrwwwwwrr", "rwwwwwwwr", "rwkwwwkwr", "rwwwkwwwr", "rrwwwwwrr", ".rwkwkwr.", "..rrrrr..", "........."]
    for j, row in enumerate(skull):
        for i, ch in enumerate(row):
            if ch != ".": px[112 + i, 10 + j] = (*{"r": rgb("#a01818"), "w": rgb("#f0e8d8"), "k": rgb("#1a1010")}[ch], 255)
    # glass glint: a soft crescent of highlight over the dial
    for y in range(96):
        for x in range(96):
            d = math.hypot(x - c, y - c)
            if d < R - 7:
                g = math.hypot(x - 34, y - 30)
                if 18 < g < 30 and x + y < 80: px[128 + x, y] = (255, 255, 255, int(70 * (1 - abs(g - 24) / 6)))
    return img


def strongbox():
    """STRONGBOX REEL (CaseScreen): a 240x96 iron-bound chest-lid panel at 0,0 with a dark reel window (x 12..228,
    y 26..60); pointer arrows 9x6 at (0,100) (down) and (10,100) (up)."""
    img = Image.new("RGBA", (256, 128), (0, 0, 0, 0))
    W, H = 240, 96
    fill_tex(img, (0, 0, W, H), rgb("#6a4424"), rgb("#3a2410"), 501, cell=4, edge=3)               # oak lid
    for y in range(6, H, 11):
        rect(img, (3, y, W - 3, y + 1), rgb("#4a2c14"))                                             # plank seams
    for x0 in (0, W - 10):                                                                          # iron bands
        fill_tex(img, (x0, 0, x0 + 10, H), rgb("#6a6a70"), rgb("#3a3a40"), 502 + x0, cell=2, edge=2)
    fill_tex(img, (0, 0, W, 6), rgb("#6a6a70"), rgb("#3a3a40"), 504, cell=2, edge=1)
    fill_tex(img, (0, H - 6, W, H), rgb("#6a6a70"), rgb("#3a3a40"), 505, cell=2, edge=1)
    px = img.load()
    for (x, y) in [(4, 2), (W - 6, 2), (4, H - 4), (W - 6, H - 4), (4, 46), (W - 6, 46)]:          # rivets
        px[x, y] = (*rgb("#d8d8e0"), 255); px[x + 1, y + 1] = (*rgb("#2a2a30"), 255)
    fill_tex(img, (12, 26, W - 12, 60), rgb("#1a120a"), rgb("#0a0604"), 506, cell=3, edge=0)      # reel window
    rect(img, (11, 25, W - 11, 61), GOLD_D)
    rect(img, (10, 24, W - 10, 62), GOLD)
    for k, flip in ((0, False), (10, True)):                                                        # pointers
        for j in range(6):
            for i in range(9):
                jj = 5 - j if flip else j
                if abs(i - 4) <= 4 - jj * 0.8:
                    px[k + i, 100 + j] = (*(GOLD_L if i < 4 else GOLD), 255)
    return img


def roost():
    """THE PARROT ROOST screen (client/screen/ParrotRoostScreen, 2026-10-03): a 290x214 panel at 0,0 - dark-wood frame,
    brass corner studs, a jungle-green felt sheet with a faint leaf pattern; slot plates 50x56 at u=300, v=0 (normal),
    58 (hover), 116 (locked). The rarity stripe and the birds are drawn by the screen."""
    img = Image.new("RGBA", (512, 256), (0, 0, 0, 0))
    W, H = 290, 214
    fill_tex(img, (0, 0, W, H), WOOD, WOOD_D, 401, cell=5, edge=5)                                  # the frame
    d = ImageDraw.Draw(img)
    for y in range(0, H, 12):
        d.line([0, y, W - 1, y], fill=(*mix(WOOD, WOOD_D, 0.7), 255))
    fill_tex(img, (8, 8, W - 8, H - 8), rgb("#3e6a3a"), rgb("#203a1e"), 402, cell=4, speck=0.03, speck_col=rgb("#5a8a4a"), edge=5)
    rect(img, (8, 8, W - 8, H - 8), rgb("#1a120a"))
    px = img.load()
    rnd = random.Random(41)
    for _ in range(46):                                                                             # faint leaves in the felt
        cx, cy, a = rnd.randint(14, W - 16), rnd.randint(30, H - 14), rnd.random() * math.pi
        for t in range(-4, 5):
            for wdt in (-1, 0, 1):
                if abs(wdt) * 4 > 4 - abs(t): continue
                x = int(cx + math.cos(a) * t - math.sin(a) * wdt); y = int(cy + math.sin(a) * t + math.cos(a) * wdt)
                if 9 < x < W - 9 and 9 < y < H - 9:
                    c = px[x, y][:3]; px[x, y] = (*mix(c, rgb("#7aa860"), 0.16), 255)
    fill_tex(img, (60, 8, W - 60, 24), rgb("#6a4422"), rgb("#3a2410"), 403, cell=3, edge=2)        # the title plate
    rect(img, (60, 8, W - 60, 24), GOLD_D)
    for (cx, cy) in [(3, 3), (W - 8, 3), (3, H - 8), (W - 8, H - 8)]:                             # brass studs
        for j in range(5):
            for i in range(5):
                if math.hypot(i - 2, j - 2) < 2.6: px[cx + i, cy + j] = (*mix(GOLD_L, GOLD_D, (i + j) / 8), 255)
    for k, (base, dark, border) in enumerate([(rgb("#5a3e22"), rgb("#2e1e0e"), rgb("#8a6a3a")),       # slot plates
                                              (rgb("#7a5630"), rgb("#3e2a14"), GOLD_L),
                                              (rgb("#2a2420"), rgb("#141210"), rgb("#4a4038"))]):
        y0 = k * 58
        fill_tex(img, (300, y0, 350, y0 + 56), base, dark, 410 + k, cell=4, edge=4)
        rect(img, (300, y0, 350, y0 + 56), border)
        for i in range(306, 344):                                                                   # the perch bar the bird stands on
            for j in (y0 + 44, y0 + 45):
                px[i, j] = (*mix(rgb("#8a5a2a"), rgb("#4a2e12"), 0.3 + 0.4 * (j - y0 - 44) + (0.25 if k == 2 else 0)), 255)
    return img


def main():
    GUI.mkdir(parents=True, exist_ok=True)
    PREV.mkdir(parents=True, exist_ok=True)
    j = journal(); j.save(GUI / "journal.png"); j.resize((1024, 512), Image.NEAREST).save(PREV / "gui_journal.png")
    s = shipyard(); s.save(GUI / "shipyard.png"); s.resize((1024, 512), Image.NEAREST).save(PREV / "gui_shipyard.png")
    m = merchant(); m.save(GUI / "merchant.png"); m.resize((1024, 512), Image.NEAREST).save(PREV / "gui_merchant.png")
    c = chart(); c.save(GUI / "chart.png"); c.resize((1024, 512), Image.NEAREST).save(PREV / "gui_chart.png")
    b = bounty(); b.save(GUI / "bounty.png"); b.resize((1024, 512), Image.NEAREST).save(PREV / "gui_bounty.png")
    r = radar(); r.save(GUI / "radar.png"); r.resize((1024, 512), Image.NEAREST).save(PREV / "gui_radar.png")
    sb = strongbox(); sb.save(GUI / "strongbox.png"); sb.resize((1024, 512), Image.NEAREST).save(PREV / "gui_strongbox.png")
    ro = roost(); ro.save(GUI / "roost.png"); ro.resize((1024, 512), Image.NEAREST).save(PREV / "gui_roost.png")
    print("wrote journal.png, shipyard.png, merchant.png, chart.png, bounty.png, radar.png, strongbox.png, roost.png")


if __name__ == "__main__":
    main()
