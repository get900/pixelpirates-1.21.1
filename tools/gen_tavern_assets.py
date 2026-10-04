"""THE GROG BARREL - assets for the tavern decor, game tables, drinks and the game screens (2026-10-01).

    python tools/gen_tavern_assets.py            # everything
    python tools/gen_tavern_assets.py tex|models|gui|lang

Writes: textures/block (tavern_sign_board, drinks_menu, crown_anchor_cloth, die, liars_felt), textures/item (the five
drinks), textures/gui/tavern_games.png (atlas for LiarsDiceScreen / CrownAnchorScreen - coordinates are fixed in those
classes), block models + blockstates + item models (blockmodels.BM), lang entries, previews in tools/previews/tavern/.
"""
import json, math, random, sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import BM, RES, write_blockstate, item_model  # noqa: E402

TEX = RES / "textures"
PREV = Path(__file__).parent / "previews/tavern"
LANG = RES / "lang/en_us.json"

# ------------------------------------------------------------------------------------------------ a little pixel font
FONT5 = {  # 3x5
    "A": ["010", "101", "111", "101", "101"], "B": ["110", "101", "110", "101", "110"], "C": ["011", "100", "100", "100", "011"],
    "D": ["110", "101", "101", "101", "110"], "E": ["111", "100", "110", "100", "111"], "F": ["111", "100", "110", "100", "100"], "G": ["011", "100", "101", "101", "011"], "J": ["001", "001", "001", "101", "010"], "Q": ["010", "101", "101", "110", "011"], "V": ["101", "101", "101", "101", "010"], "X": ["101", "101", "010", "101", "101"], "Z": ["111", "001", "010", "100", "111"], "7": ["111", "001", "010", "010", "010"], "8": ["010", "101", "010", "101", "010"], "9": ["010", "101", "011", "001", "110"], "0": ["010", "101", "101", "101", "010"],
    "H": ["101", "101", "111", "101", "101"], "I": ["111", "010", "010", "010", "111"], "K": ["101", "101", "110", "101", "101"],
    "L": ["100", "100", "100", "100", "111"], "M": ["101", "111", "111", "101", "101"], "N": ["110", "101", "101", "101", "101"],
    "O": ["010", "101", "101", "101", "010"], "P": ["110", "101", "110", "100", "100"], "R": ["110", "101", "110", "101", "101"],
    "S": ["011", "100", "010", "001", "110"], "T": ["111", "010", "010", "010", "010"], "U": ["101", "101", "101", "101", "111"],
    "W": ["101", "101", "111", "111", "101"], "Y": ["101", "101", "010", "010", "010"], "1": ["010", "110", "010", "010", "111"],
    "2": ["110", "001", "010", "100", "111"], "3": ["110", "001", "010", "001", "110"], "4": ["101", "101", "111", "001", "001"],
    "5": ["111", "100", "110", "001", "110"], "6": ["011", "100", "110", "101", "010"], "'": ["1", "1", "0", "0", "0"],
    " ": ["0", "0", "0", "0", "0"], "-": ["000", "000", "111", "000", "000"], ".": ["0", "0", "0", "0", "1"], "&": ["010", "101", "010", "101", "011"],
}


def text(img, s, x, y, col, scale=1, shadow=None):
    d = ImageDraw.Draw(img)
    for ch in s:
        g = FONT5.get(ch, FONT5[" "])
        for r, row in enumerate(g):
            for c, v in enumerate(row):
                if v == "1":
                    if shadow:
                        d.rectangle([x + c * scale + 1, y + r * scale + 1, x + c * scale + scale, y + r * scale + scale], fill=shadow)
                    d.rectangle([x + c * scale, y + r * scale, x + c * scale + scale - 1, y + r * scale + scale - 1], fill=col)
        x += (len(g[0]) + 1) * scale
    return x


def text_w(s, scale=1):
    return sum((len(FONT5.get(ch, FONT5[" "])[0]) + 1) * scale for ch in s) - scale


def noise(img, box, base, amt, seed=1):
    r = random.Random(seed)
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = r.randint(-amt, amt)
            img.putpixel((x, y), tuple(max(0, min(255, c + k)) for c in base) + (255,))


def save(img, rel):
    p = TEX / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)
    print("wrote", rel)


# ------------------------------------------------------------------------------------------------ symbols (crown & anchor)
SYM_COL = {"crown": (232, 188, 60), "anchor": (70, 74, 86), "heart": (200, 40, 40), "diamond": (210, 50, 50), "club": (30, 30, 34), "spade": (30, 30, 34)}


def draw_symbol(name, size=28):
    S = size * 4
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = SYM_COL[name] + (255,)
    k = S / 28
    P = lambda pts: [(x * k, y * k) for x, y in pts]
    if name == "crown":
        d.polygon(P([(4, 21), (4, 9), (9, 15), (14, 6), (19, 15), (24, 9), (24, 21)]), fill=c, outline=(120, 80, 20, 255))
        d.rectangle([4 * k, 21 * k, 24 * k, 24 * k], fill=(200, 150, 40, 255))
        for x in (4, 14, 24):
            d.ellipse([(x - 1.6) * k, 4.5 * k + (2 if x != 14 else 0) * k, (x + 1.6) * k, 7.7 * k + (2 if x != 14 else 0) * k], fill=(220, 40, 60, 255))
    elif name == "anchor":
        d.ellipse([11.5 * k, 3 * k, 16.5 * k, 8 * k], outline=c, width=int(1.8 * k))
        d.rectangle([13 * k, 7 * k, 15 * k, 23 * k], fill=c)
        d.rectangle([8 * k, 10 * k, 20 * k, 12 * k], fill=c)
        d.arc([5 * k, 12 * k, 23 * k, 26 * k], 20, 160, fill=c, width=int(2 * k))
        d.polygon(P([(4, 16), (7, 21), (9, 17)]), fill=c)
        d.polygon(P([(24, 16), (21, 21), (19, 17)]), fill=c)
    elif name == "heart":
        d.ellipse([4 * k, 5 * k, 14.5 * k, 15 * k], fill=c)
        d.ellipse([13.5 * k, 5 * k, 24 * k, 15 * k], fill=c)
        d.polygon(P([(4.6, 12), (23.4, 12), (14, 24)]), fill=c)
    elif name == "diamond":
        d.polygon(P([(14, 3), (23, 14), (14, 25), (5, 14)]), fill=c)
    elif name == "club":
        for (x, y) in ((14, 8.5), (8.5, 15), (19.5, 15)):
            d.ellipse([(x - 5) * k, (y - 5) * k, (x + 5) * k, (y + 5) * k], fill=c)
        d.polygon(P([(14, 14), (11, 25), (17, 25)]), fill=c)
    elif name == "spade":
        d.ellipse([4.5 * k, 10 * k, 14.5 * k, 20 * k], fill=c)
        d.ellipse([13.5 * k, 10 * k, 23.5 * k, 20 * k], fill=c)
        d.polygon(P([(5, 15), (23, 15), (14, 3)]), fill=c)
        d.polygon(P([(14, 15), (10.5, 25.5), (17.5, 25.5)]), fill=c)
    return im.resize((size, size), Image.LANCZOS)


SYMBOLS = ["crown", "anchor", "heart", "diamond", "club", "spade"]

# ------------------------------------------------------------------------------------------------ dice
PIPS = {1: [(1, 1)], 2: [(0, 0), (2, 2)], 3: [(0, 0), (1, 1), (2, 2)], 4: [(0, 0), (2, 0), (0, 2), (2, 2)],
        5: [(0, 0), (2, 0), (1, 1), (0, 2), (2, 2)], 6: [(0, 0), (0, 1), (0, 2), (2, 0), (2, 1), (2, 2)]}


def die(size, face, ivory=(238, 228, 205), pip=(25, 22, 22)):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r = max(1, size // 6)
    d.rounded_rectangle([0, 0, size - 1, size - 1], r, fill=(150, 135, 110, 255))
    d.rounded_rectangle([0, 0, size - 2, size - 2], r, fill=ivory + (255,))
    d.line([(r, size - 2), (size - 2, size - 2)], fill=(200, 188, 160, 255))
    if face:
        m = size * 0.24
        step = (size - 2 * m) / 2
        pr = max(0.8, size * 0.085)
        for gx, gy in PIPS[face]:
            cx, cy = m + gx * step - 0.5, m + gy * step - 0.5
            col = (190, 30, 30) if face == 1 else pip
            if size <= 16:                                   # crisp square pips on the small dice
                q = 1 if size <= 10 else 2
                x0, y0 = int(round(cx - q / 2)), int(round(cy - q / 2))
                d.rectangle([x0, y0, x0 + q - 1, y0 + q - 1], fill=col + (255,))
            else:
                d.ellipse([cx - pr, cy - pr, cx + pr, cy + pr], fill=col + (255,))
    return im


def cup(size):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    w = size
    d.polygon([(w * 0.18, 1), (w * 0.82, 1), (w * 0.95, w - 2), (w * 0.05, w - 2)], fill=(110, 66, 36, 255), outline=(60, 34, 18, 255))
    d.rectangle([w * 0.05, w - 3, w * 0.95, w - 1], fill=(80, 48, 24, 255))
    d.line([(w * 0.3, 3), (w * 0.22, w - 4)], fill=(150, 100, 60, 255))
    d.rectangle([w * 0.18, 1, w * 0.82, 2], fill=(150, 104, 62, 255))
    return im


# ------------------------------------------------------------------------------------------------ textures
def tex_block():
    # THE SIGN: painted board 64x32
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (58, 36, 22), 6, 3)
    d = ImageDraw.Draw(im)
    for y in range(4, 32, 7):
        d.line([(1, y), (62, y)], fill=(44, 27, 16, 255))
    d.rectangle([0, 0, 63, 31], outline=(30, 18, 10, 255))
    d.rectangle([1, 1, 62, 30], outline=(200, 156, 60, 255))
    d.rectangle([2, 2, 61, 29], outline=(120, 84, 34, 255))
    # the barrel
    bx, by = 5, 7
    d.rounded_rectangle([bx, by, bx + 12, by + 17], 4, fill=(150, 96, 50, 255), outline=(60, 36, 18, 255))
    for yy in (by + 3, by + 13):
        d.line([(bx + 1, yy), (bx + 11, yy)], fill=(70, 70, 76, 255))
    for xx in (bx + 4, bx + 8):
        d.line([(xx, by + 1), (xx, by + 16)], fill=(120, 74, 38, 255))
    d.rectangle([bx + 5, by + 8, bx + 7, by + 9], fill=(200, 156, 60, 255))   # the bung
    gold, sh = (240, 196, 70, 255), (40, 20, 8, 255)
    text(im, "THE", 22, 4, (232, 220, 190, 255), 1, sh)
    text(im, "GROG", 22, 11, gold, 2, sh)
    text(im, "BARREL", 22, 23, gold, 1, sh)
    save(im, "block/tavern_sign_board.png")

    # THE CHANDLER'S SIGN: a navy board, a ship's lantern and an anchor, "SHIP CHANDLER"
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (30, 46, 74), 5, 4)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 31], outline=(14, 20, 34, 255))
    d.rectangle([1, 1, 62, 30], outline=(226, 222, 206, 255))
    d.rectangle([2, 2, 61, 29], outline=(80, 110, 150, 255))
    # lantern (left)
    d.rectangle([7, 6, 13, 7], fill=(40, 40, 46, 255)); d.rectangle([9, 4, 11, 5], fill=(40, 40, 46, 255))
    d.rectangle([6, 8, 14, 20], fill=(46, 46, 52, 255)); d.rectangle([8, 10, 12, 18], fill=(250, 200, 90, 255))
    d.rectangle([9, 12, 11, 16], fill=(255, 240, 170, 255)); d.rectangle([5, 21, 15, 23], fill=(40, 40, 46, 255))
    # anchor (right)
    an = draw_symbol("anchor", 16).convert("RGBA")
    px = an.load()
    for yy in range(16):
        for xx in range(16):
            if px[xx, yy][3] > 60: px[xx, yy] = (226, 222, 206, 255)
            else: px[xx, yy] = (0, 0, 0, 0)
    im.alpha_composite(an, (46, 8))
    white, sh = (238, 234, 220, 255), (10, 14, 26, 255)
    text(im, "SHIP", 30 - text_w("SHIP") // 2, 6, white, 1, sh)
    text(im, "CHANDLER", 30 - text_w("CHANDLER") // 2, 13, (240, 200, 90, 255), 1, sh)
    text(im, "ROPE SAIL", 30 - text_w("ROPE SAIL") // 2, 21, (180, 200, 220, 255), 1, sh)
    save(im, "block/chandlery_sign_board.png")

    # THE BAKERY SIGN: a cream board, a golden loaf + a pretzel, "BAKERY" / "BREAD & CAKES"
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (236, 220, 180), 5, 6)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 31], outline=(120, 70, 36, 255))
    d.rectangle([1, 1, 62, 30], outline=(214, 120, 120, 255))
    d.rectangle([2, 2, 61, 29], outline=(170, 110, 60, 255))
    d.ellipse([4, 9, 20, 22], fill=(196, 128, 50, 255), outline=(120, 70, 30, 255))          # the loaf
    for k in range(3):
        d.line([(8 + k * 4, 11), (6 + k * 4, 16)], fill=(240, 200, 130, 255), width=1)
    d.arc([44, 8, 52, 18], 180, 360, fill=(170, 100, 40, 255), width=2)                      # the pretzel
    d.arc([51, 8, 59, 18], 180, 360, fill=(170, 100, 40, 255), width=2)
    d.arc([45, 10, 58, 24], 0, 180, fill=(170, 100, 40, 255), width=2)
    d.line([(48, 14), (55, 22)], fill=(170, 100, 40, 255), width=2)
    d.line([(55, 14), (48, 22)], fill=(170, 100, 40, 255), width=2)
    brown, sh = (110, 56, 26, 255), (250, 240, 214, 255)
    text(im, "BAKERY", 32 - text_w("BAKERY", 1) // 2, 8, brown, 1, None)
    text(im, "BREAD", 32 - text_w("BREAD") // 2, 16, (200, 90, 90, 255))
    text(im, "CAKES", 32 - text_w("CAKES") // 2, 22, (200, 90, 90, 255))
    save(im, "block/bakery_sign_board.png")

    # THE HARBOUR MASTER'S SIGN: sea-green board, a gilt ship's wheel, "HARBOUR" / "MASTER"
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (40, 104, 100), 5, 8)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 31], outline=(16, 40, 40, 255))
    d.rectangle([1, 1, 62, 30], outline=(236, 232, 214, 255))
    d.rectangle([2, 2, 61, 29], outline=(24, 70, 70, 255))
    cx, cy, gold = 11, 16, (232, 186, 70, 255)
    for k in range(8):                                                     # the wheel: spokes, rim, hub
        a_ = k * math.pi / 4
        d.line([(cx, cy), (cx + 8 * math.cos(a_), cy + 8 * math.sin(a_))], fill=gold, width=1)
    d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], outline=gold, width=2)
    d.ellipse([cx - 1, cy - 1, cx + 1, cy + 1], fill=(150, 100, 30, 255))
    white, sh = (240, 238, 226, 255), (10, 30, 30, 255)
    text(im, "HARBOUR", 39 - text_w("HARBOUR") // 2, 7, white, 1, sh)
    text(im, "MASTER", 39 - text_w("MASTER") // 2, 15, gold, 1, sh)
    d.line([(24, 23), (54, 23)], fill=(236, 232, 214, 255))
    for x in range(26, 54, 6):
        d.line([(x, 25), (x + 3, 26)], fill=(180, 220, 220, 255))
    save(im, "block/harbour_sign_board.png")

    # THE TRADING COMPANY'S SIGN: a brown board, a crate + a sack, "TRADING" / "COMPANY"
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (92, 52, 30), 5, 10)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 31], outline=(40, 20, 10, 255))
    d.rectangle([1, 1, 62, 30], outline=(226, 184, 80, 255))
    d.rectangle([2, 2, 61, 29], outline=(120, 70, 36, 255))
    d.rectangle([4, 10, 15, 21], fill=(176, 126, 72, 255), outline=(90, 56, 28, 255))      # the crate
    d.line([(4, 10), (15, 21)], fill=(110, 70, 36, 255)); d.line([(15, 10), (4, 21)], fill=(110, 70, 36, 255))
    d.line([(4, 15), (15, 15)], fill=(110, 70, 36, 255))
    d.ellipse([47, 9, 59, 23], fill=(214, 196, 150, 255), outline=(120, 96, 60, 255))      # the sack
    d.rectangle([51, 7, 55, 10], fill=(190, 170, 120, 255))
    d.line([(50, 16), (56, 16)], fill=(170, 60, 40, 255), width=2)
    gold, sh = (240, 200, 90, 255), (30, 14, 6, 255)
    text(im, "TRADING", 32 - text_w("TRADING") // 2, 9, gold, 1, sh)
    text(im, "COMPANY", 32 - text_w("COMPANY") // 2, 17, (236, 226, 200, 255), 1, sh)
    save(im, "block/warehouse_sign_board.png")

    # THE DISTILLERY SIGN: a black board, a rum bottle + a cask, "RUM" / "DISTILLERY"
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (28, 24, 22), 4, 12)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 31], outline=(8, 6, 6, 255))
    d.rectangle([1, 1, 62, 30], outline=(214, 132, 60, 255))
    d.rectangle([2, 2, 61, 29], outline=(80, 44, 20, 255))
    im.alpha_composite(bottle((150, 70, 24), label=(232, 210, 160), cork=(90, 60, 40)).resize((16, 16), Image.NEAREST), (3, 8))
    d.rounded_rectangle([47, 10, 59, 22], 3, fill=(130, 80, 40, 255), outline=(60, 36, 18, 255))     # the cask
    d.line([(47, 13), (59, 13)], fill=(80, 80, 86, 255)); d.line([(47, 19), (59, 19)], fill=(80, 80, 86, 255))
    d.ellipse([51, 14, 55, 18], fill=(214, 160, 90, 255))
    amber, sh = (236, 150, 60, 255), (0, 0, 0, 255)
    text(im, "RUM", 33 - text_w("RUM", 2) // 2, 5, amber, 2, sh)
    text(im, "DISTILLERY", 32 - text_w("DISTILLERY") // 2, 20, (230, 220, 200, 255), 1, sh)
    save(im, "block/distillery_sign_board.png")

    # THE FISH MARKET SIGN: a pale-blue board, a leaping fish, "FISH" / "MARKET"
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (180, 214, 230), 5, 14)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 31], outline=(30, 50, 80, 255))
    d.rectangle([1, 1, 62, 30], outline=(250, 250, 250, 255))
    d.rectangle([2, 2, 61, 29], outline=(60, 110, 160, 255))
    d.ellipse([5, 10, 19, 20], fill=(60, 120, 170, 255), outline=(20, 50, 90, 255))           # the fish
    d.polygon([(18, 15), (23, 10), (23, 20)], fill=(60, 120, 170, 255), outline=(20, 50, 90, 255))
    d.ellipse([7, 13, 9, 15], fill=(255, 255, 255, 255)); d.point((8, 14), fill=(0, 0, 0, 255))
    d.line([(10, 18), (17, 18)], fill=(150, 200, 230, 255))
    for x in range(6, 22, 4):
        d.arc([x, 22, x + 4, 26], 180, 360, fill=(40, 90, 150, 255))                        # waves
    navy, red = (24, 44, 90, 255), (200, 50, 50, 255)
    text(im, "FISH", 43 - text_w("FISH", 2) // 2, 4, navy, 2, None)
    text(im, "MARKET", 43 - text_w("MARKET") // 2, 19, red, 1, None)
    save(im, "block/fish_sign_board.png")

    inn_board()

    # THE DOCK OFFICE SIGN: oak board, coiled rope round an anchor ring, "DOCK" / "OFFICE"
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (150, 110, 64), 5, 16)
    d = ImageDraw.Draw(im)
    for y in range(5, 30, 6):
        d.line([(2, y), (61, y)], fill=(126, 90, 50, 255))
    d.rectangle([0, 0, 63, 31], outline=(60, 36, 18, 255))
    d.rectangle([1, 1, 62, 30], outline=(28, 48, 90, 255))
    d.rectangle([2, 2, 61, 29], outline=(230, 220, 190, 255))
    d.ellipse([4, 8, 18, 22], outline=(214, 180, 110, 255), width=3)                           # coiled rope
    d.ellipse([7, 11, 15, 19], outline=(170, 130, 70, 255), width=1)
    an = draw_symbol("anchor", 12)
    im.alpha_composite(an, (5, 9))
    navy, sh = (24, 40, 86, 255), (240, 230, 200, 255)
    text(im, "DOCK", 40 - text_w("DOCK", 2) // 2, 4, navy, 2, None)
    text(im, "OFFICE", 40 - text_w("OFFICE") // 2, 20, (150, 30, 30, 255), 1, None)
    save(im, "block/dock_sign_board.png")

    # THE DUES BOARD (32x32 wall board): "DUES", doubloon icons, the rates
    im = Image.new("RGBA", (32, 32))
    noise(im, (0, 0, 32, 32), (60, 44, 30), 4, 18)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 31, 31], outline=(30, 20, 12, 255))
    d.rectangle([1, 1, 30, 30], outline=(200, 160, 70, 255))
    text(im, "DUES", 16 - text_w("DUES") // 2, 3, (240, 220, 170, 255))
    d.line([(4, 9), (27, 9)], fill=(200, 160, 70, 255))
    for i, (txt, n) in enumerate((("3-30", 1), ("3 DAYS", 0))):
        y = 12 + i * 7
        if n: d.ellipse([3, y, 7, y + 4], fill=(230, 190, 70, 255), outline=(150, 110, 30, 255))
        text(im, txt, 9 if n else 16 - text_w(txt) // 2, y, (230, 230, 220, 255))
    d.rectangle([4, 26, 27, 27], fill=(150, 40, 40, 255))
    save(im, "block/dues_board.png")

    # THE DUES LEDGER's open page (16x16): two ruled pages, a coin stack, ink + quill
    im = Image.new("RGBA", (16, 16))
    noise(im, (0, 0, 16, 16), (232, 222, 196), 4, 19)
    d = ImageDraw.Draw(im)
    d.line([(8, 0), (8, 15)], fill=(150, 130, 100, 255))
    for y in range(3, 15, 2):
        d.line([(1, y), (6, y)], fill=(120, 110, 140, 255)); d.line([(10, y), (14, y)], fill=(120, 110, 140, 255))
    d.rectangle([0, 0, 15, 15], outline=(120, 70, 40, 255))
    save(im, "block/dues_ledger_page.png")

    # THE DRINKS MENU: a chalkboard 32x32
    im = Image.new("RGBA", (32, 32))
    noise(im, (0, 0, 32, 32), (34, 40, 36), 4, 5)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 31, 31], outline=(92, 60, 34, 255))
    d.rectangle([1, 1, 30, 30], outline=(120, 80, 46, 255))
    chalk = (226, 226, 214, 255)
    text(im, "MENU", 8, 3, chalk)
    d.line([(4, 9), (27, 9)], fill=(160, 160, 150, 255))
    rows = [((230, 196, 80), 4), ((200, 150, 60), 5), ((180, 50, 50), 3), ((150, 100, 50), 6), ((120, 70, 160), 7)]
    for i, (col, n) in enumerate(rows):
        y = 12 + i * 4
        d.rectangle([4, y, 5, y + 1], fill=col + (255,))
        for k in range(3):
            d.line([(8 + k * 5, y + 1), (11 + k * 5, y + (k % 2))], fill=(200, 200, 190, 255))
        text(im, str(n), 25, y - 1, chalk) if str(n) in FONT5 else None
    save(im, "block/drinks_menu.png")

    # CROWN & ANCHOR CLOTH 32x32: 3x2 symbol squares on blue baize
    im = Image.new("RGBA", (32, 32))
    noise(im, (0, 0, 32, 32), (34, 52, 110), 5, 7)
    d = ImageDraw.Draw(im)
    for i, s in enumerate(SYMBOLS):
        cx, cy = 1 + (i % 3) * 10, 6 + (i // 3) * 12
        d.rectangle([cx, cy, cx + 9, cy + 10], fill=(232, 220, 190, 255), outline=(200, 160, 60, 255))
        sym = draw_symbol(s, 8)
        im.alpha_composite(sym, (cx + 1, cy + 1))
    d.rectangle([0, 0, 31, 31], outline=(200, 160, 60, 255))
    save(im, "block/crown_anchor_cloth.png")

    # A DIE face (16x16, five pips) and LIAR'S DICE felt
    save(die(16, 5).convert("RGBA"), "block/die.png")
    im = Image.new("RGBA", (16, 16))
    noise(im, (0, 0, 16, 16), (40, 96, 54), 5, 9)
    ImageDraw.Draw(im).rectangle([0, 0, 15, 15], outline=(196, 156, 64, 255))
    save(im, "block/liars_felt.png")


def inn_board():
    # THE INN SIGN (2026-10-03): a deep sea-teal board, a mermaid on a rock, "THE" / "MERMAID'S" / "REST"
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (24, 84, 92), 5, 20)
    d = ImageDraw.Draw(im)
    for x in range(4, 22, 5):
        d.arc([x, 24, x + 5, 28], 180, 360, fill=(70, 150, 160, 255))                          # waves under the rock
    d.rectangle([0, 0, 63, 31], outline=(10, 34, 40, 255))
    d.rectangle([1, 1, 62, 30], outline=(236, 196, 120, 255))
    d.rectangle([2, 2, 61, 29], outline=(18, 60, 66, 255))
    d.ellipse([5, 20, 17, 27], fill=(110, 104, 98, 255), outline=(60, 56, 52, 255))           # the rock
    tail, tail_d = (60, 190, 150, 255), (30, 120, 100, 255)
    d.polygon([(9, 15), (13, 15), (15, 19), (16, 21), (13, 21), (10, 19)], fill=tail, outline=tail_d)   # the tail, curled over the rock
    d.polygon([(15, 20), (20, 17), (19, 22), (21, 25), (16, 22)], fill=tail, outline=tail_d)            # the fin
    d.rectangle([9, 10, 12, 14], fill=(236, 190, 150, 255))                                   # body
    d.rectangle([9, 12, 12, 13], fill=(200, 80, 140, 255))                                    # shell top
    d.ellipse([8, 5, 13, 10], fill=(236, 190, 150, 255))                                       # head
    d.line([(8, 5), (13, 5)], fill=(200, 70, 40, 255)); d.line([(7, 6), (7, 13)], fill=(200, 70, 40, 255))   # red hair
    d.line([(8, 6), (8, 12)], fill=(220, 90, 50, 255)); d.point((12, 7), fill=(20, 30, 40, 255))
    gold, cream, sh = (240, 200, 100, 255), (240, 232, 214, 255), (6, 24, 28, 255)
    text(im, "THE", 41 - text_w("THE") // 2, 4, cream, 1, sh)
    text(im, "MERMAID'S", 41 - text_w("MERMAID'S") // 2, 10, gold, 1, sh)
    text(im, "REST", 41 - text_w("REST", 2) // 2, 17, cream, 2, sh)
    save(im, "block/inn_sign_board.png")


def bottle(body, liquid=None, label=(232, 220, 190), cork=(160, 120, 70), seed=0, glow=False):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    dark = tuple(max(0, c - 50) for c in body)
    d.rectangle([7, 1, 8, 2], fill=cork + (255,))                       # cork
    d.rectangle([7, 3, 8, 5], fill=body + (255,))                       # neck
    d.rounded_rectangle([4, 6, 11, 14], 2, fill=body + (255,), outline=dark + (255,))
    d.rectangle([5, 9, 10, 12], fill=label + (255,))                    # label
    d.line([(6, 10), (9, 10)], fill=(90, 60, 40, 255))
    d.line([(5, 7), (5, 8)], fill=(255, 255, 255, 170))                 # glint
    if glow:
        for p in ((6, 13), (9, 7), (10, 13)):
            im.putpixel(p, (200, 140, 255, 255))
    d.line([(4, 15), (11, 15)], fill=(0, 0, 0, 0))
    return im


def tex_items():
    # Tankard of ale: wooden mug, iron bands, foam
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([3, 5, 10, 14], fill=(130, 86, 46, 255), outline=(66, 40, 20, 255))
    for x in (5, 7, 9):
        d.line([(x, 6), (x, 13)], fill=(110, 70, 36, 255))
    d.line([(3, 7), (10, 7)], fill=(150, 150, 160, 255))
    d.line([(3, 12), (10, 12)], fill=(150, 150, 160, 255))
    d.arc([8, 6, 14, 12], 270, 90, fill=(66, 40, 20, 255), width=2)
    d.ellipse([2, 2, 7, 6], fill=(250, 246, 232, 255))
    d.ellipse([6, 1, 11, 6], fill=(250, 246, 232, 255))
    d.point((4, 7), fill=(250, 246, 232, 255))
    d.point((8, 8), fill=(250, 246, 232, 255))
    save(im, "item/tankard_of_ale.png")
    save(bottle((214, 150, 40), label=(250, 230, 160), cork=(200, 160, 60)), "item/honey_mead.png")
    save(bottle((120, 20, 34), label=(230, 200, 170)), "item/spiced_wine.png")
    save(bottle((150, 92, 40), label=(210, 200, 170), cork=(90, 60, 40)), "item/bilge_whiskey.png")
    save(bottle((60, 36, 110), label=(40, 200, 190), cork=(40, 30, 60), glow=True), "item/krakens_kiss.png")


# ------------------------------------------------------------------------------------------------ the GUI atlas
def gui():
    W, H = 512, 256
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # panel 256x232: wooden frame + felt
    noise(im, (0, 0, 256, 232), (78, 50, 30), 8, 11)
    for y in range(0, 232, 5):
        d.line([(0, y), (255, y)], fill=(66, 42, 25, 255))
    d.rectangle([0, 0, 255, 231], outline=(30, 18, 10, 255))
    d.rectangle([1, 1, 254, 230], outline=(130, 92, 52, 255))
    noise(im, (6, 6, 250, 203), (36, 88, 50), 6, 12)
    d.rectangle([5, 5, 250, 203], outline=(200, 160, 60, 255))
    d.rectangle([6, 6, 249, 202], outline=(24, 56, 32, 255))
    d.rectangle([6, 205, 249, 226], fill=(28, 18, 12, 255), outline=(110, 80, 44, 255))
    # brass corner studs
    for (x, y) in ((3, 3), (251, 3), (3, 227), (251, 227)):
        d.ellipse([x - 2, y - 2, x + 2, y + 2], fill=(214, 172, 70, 255), outline=(90, 60, 20, 255))
    # big dice 20x20, cup
    for f in range(1, 7):
        im.alpha_composite(die(20, f), (256 + (f - 1) * 20, 0))
    im.alpha_composite(cup(20), (376, 0))
    # mini dice 10x10, mini cup
    for f in range(1, 7):
        im.alpha_composite(die(10, f), (256 + (f - 1) * 10, 20))
    im.alpha_composite(cup(10), (316, 20))
    # symbols 28x28
    for i, s in enumerate(SYMBOLS):
        im.alpha_composite(draw_symbol(s, 28), (256 + i * 28, 32))
    # seat cards 40x56: normal, their turn (gold), out (dim)
    for k, (fill, edge) in enumerate((((60, 38, 22), (130, 92, 52)), ((76, 50, 24), (240, 196, 70)), ((40, 34, 30), (80, 70, 60)))):
        x = 256 + k * 40
        d.rounded_rectangle([x, 64, x + 39, 119], 3, fill=fill + (255,), outline=edge + (255,))
        d.rectangle([x + 2, 74, x + 37, 106], fill=(30, 70, 40, 255) if k < 2 else (36, 40, 36, 255))
        if k == 1:
            d.rounded_rectangle([x + 1, 65, x + 38, 118], 3, outline=(255, 226, 120, 255))
    # crown & anchor squares 40x48: normal + lit
    for k, edge in enumerate(((200, 160, 60), (255, 226, 120))):
        x = 376 + k * 40
        d.rectangle([x, 64, x + 39, 111], fill=(232, 222, 196, 255) if k == 0 else (250, 238, 200, 255), outline=edge + (255,))
        d.rectangle([x + 1, 65, x + 38, 110], outline=(150, 120, 60, 255))
        d.rectangle([x + 2, 94, x + 37, 109], fill=(44, 62, 120, 255))
    # tray die 32x32 (blank)
    im.alpha_composite(die(32, 0), (256, 124))
    save(im, "gui/tavern_games.png")
    PREV.mkdir(parents=True, exist_ok=True)
    im.resize((W * 2, H * 2), Image.NEAREST).save(PREV / "tavern_games_atlas.png")


# ------------------------------------------------------------------------------------------------ models
DO, SP = "minecraft:block/dark_oak_planks", "minecraft:block/spruce_planks"


def facing_states(name, models):
    """models: {extra_prop_string: model_id}; writes facing x extra variants."""
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    v = {}
    for f, r in rot.items():
        for extra, model in models.items():
            key = f"facing={f}" + ("," + extra if extra else "")
            v[key] = {"model": model, "y": r} if r else {"model": model}
    write_blockstate(name, {"variants": v})


def hanging_sign(name):
    m = BM(name, {"board": f"pixelpirates:block/{name}_board", "iron": "minecraft:block/anvil", "wood": "minecraft:block/dark_oak_log",
                  "particle": "minecraft:block/dark_oak_planks"})
    m.box([5.5, 9, 15], [10.5, 16, 16], all="#iron")
    m.box([7, 13, -6], [9, 14.5, 16], all="#iron")
    m.box([7.5, 14.5, -6], [8.5, 15, -5], all="#iron")
    m.box([7.75, 11, -3.5], [8.25, 13, -3], all="#iron")
    m.box([7.75, 11, 12], [8.25, 13, 12.5], all="#iron")
    m.box([7.5, 1, -5], [8.5, 11, 15], faces={"east": "#board", "west": "#board", "up": "#wood", "down": "#wood", "north": "#wood", "south": "#wood"},
          uv={"east": [0, 0, 16, 16], "west": [0, 0, 16, 16]})
    m.write()
    facing_states(name, {"": f"pixelpirates:block/{name}"})
    (RES / f"models/item/{name}.json").write_bytes(json.dumps({"parent": f"pixelpirates:block/{name}", "display": {
        "gui": {"rotation": [30, 135, 0], "translation": [1, -1, 0], "scale": [0.45, 0.45, 0.45]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
        "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]}}}, indent=1).encode())
    return m


def perch_branch():
    """THE PERCH BRANCH (2026-10-03): a jungle branch out from a wall (wall at +z, tip at -z) with a twig + an azalea tuft."""
    m = BM("perch_branch", {"bark": "minecraft:block/jungle_log", "end": "minecraft:block/jungle_log_top",
                            "leaf": "minecraft:block/azalea_leaves", "particle": "minecraft:block/jungle_log"})
    m.box([5, 5, 15], [11, 10, 16], all="#bark")                                   # the flared base against the wall
    m.box([6.5, 6, 0], [9.5, 9, 15], faces={"north": "#end", "south": "#bark", "east": "#bark", "west": "#bark", "up": "#bark", "down": "#bark"},
          uv={"north": [6, 6, 10, 10]})                                            # the limb - parrots stand on top (y 9)
    m.box([5.5, 9, 9], [7, 13, 10.5], all="#bark")                                 # a twig rising near the wall
    m.box([3.5, 12, 7.5], [8.5, 15.5, 12], all="#leaf")                            # its azalea tuft
    m.box([9.5, 7, 4], [12.5, 8.5, 5.5], all="#bark")                              # a side twig near the tip
    m.box([11.5, 7.5, 3], [14, 10, 6.5], all="#leaf")
    m.write()
    facing_states("perch_branch", {"": "pixelpirates:block/perch_branch"})
    item_model("perch_branch", "pixelpirates:block/perch_branch")
    return m


def parrot_roost():
    """THE PARROT ROOST (2026-10-03): a T-perch stand on a round foot, seed cups at the bar ends, a brass ring on top."""
    m = BM("parrot_roost", {"post": "minecraft:block/stripped_jungle_log", "bar": "minecraft:block/jungle_log",
                            "foot": "minecraft:block/dark_oak_planks", "cup": "minecraft:block/terracotta",
                            "brass": "minecraft:block/gold_block", "particle": "minecraft:block/stripped_jungle_log"})
    m.box([4, 0, 4], [12, 1.5, 12], all="#foot")
    m.box([5.5, 1.5, 5.5], [10.5, 2.5, 10.5], all="#foot")
    m.box([7, 2.5, 7], [9, 13, 9], all="#post")
    m.box([1, 12, 7.25], [15, 13.5, 8.75], all="#bar")                                     # the perch bar
    for x in (0.5, 13):
        m.box([x, 11, 6.5], [x + 2.5, 12, 9.5], all="#cup")                               # seed cups under the bar ends
    m.box([7.5, 13.5, 7.5], [8.5, 15, 8.5], all="#post")
    m.box([6.5, 15, 7.75], [9.5, 15.5, 8.25], all="#brass")                               # a little brass ring
    m.write()
    facing_states("parrot_roost", {"": "pixelpirates:block/parrot_roost"})
    item_model("parrot_roost", "pixelpirates:block/parrot_roost")
    return m


def models():
    built = []
    built.append(perch_branch())
    built.append(parrot_roost())
    # --- the hanging signs: wall plate at the back (+z), arm out to the front, the board hanging under it
    for sign in ("tavern_sign", "chandlery_sign", "bakery_sign", "harbour_sign", "warehouse_sign", "distillery_sign", "fish_sign", "dock_sign", "inn_sign"):
        built.append(hanging_sign(sign))
    m = None
    if False:
        m = BM("unused", {})

    # --- the drinks menu: a framed chalkboard on the wall (front = north)
    m = BM("drinks_menu", {"board": "pixelpirates:block/drinks_menu", "frame": "minecraft:block/dark_oak_planks", "particle": "minecraft:block/dark_oak_planks"})
    m.box([1, 1, 15], [15, 15, 16], faces={"north": "#board", "south": "#frame", "east": "#frame", "west": "#frame", "up": "#frame", "down": "#frame"},
          uv={"north": [0, 0, 16, 16]})
    m.box([0.5, 15, 14.5], [15.5, 16, 16], all="#frame")
    m.box([0.5, 0, 14.5], [15.5, 1, 16], all="#frame")
    m.box([0, 0, 14.5], [1, 16, 16], all="#frame")
    m.box([15, 0, 14.5], [16, 16, 16], all="#frame")
    m.box([3, 0.5, 14], [13, 1.2, 14.5], all="#frame")          # the chalk ledge
    m.write()
    facing_states("drinks_menu", {"": "pixelpirates:block/drinks_menu"})
    item_model("drinks_menu", "pixelpirates:block/drinks_menu")
    built.append(m)

    # --- tankards (1-3)
    tt = {"wood": "minecraft:block/stripped_spruce_log", "iron": "minecraft:block/iron_block", "foam": "minecraft:block/white_concrete_powder",
          "particle": "minecraft:block/stripped_spruce_log"}
    spots = [(6, 6), (3, 9), (9, 10)]
    for n in (1, 2, 3):
        m = BM(f"tankard_{n}", tt)
        for i in range(n):
            x, z = spots[i] if n > 1 else (6, 6)
            m.box([x, 0, z], [x + 4, 5.5, z + 4], all="#wood")
            m.box([x - 0.2, 1, z - 0.2], [x + 4.2, 1.6, z + 4.2], all="#iron")
            m.box([x - 0.2, 4, z - 0.2], [x + 4.2, 4.6, z + 4.2], all="#iron")
            m.box([x + 0.5, 5.5, z + 0.5], [x + 3.5, 6.3, z + 3.5], all="#foam")
            m.box([x + 4, 1.5, z + 1.5], [x + 5.5, 4.5, z + 2.5], all="#wood")
        m.write()
        built.append(m)
    facing_states("tankard", {f"count={n}": f"pixelpirates:block/tankard_{n}" for n in (1, 2, 3)})
    item_model("tankard", "pixelpirates:block/tankard_1")

    # --- spirit bottles (1-4)
    bt = {"a": "minecraft:block/brown_terracotta", "b": "minecraft:block/green_terracotta", "c": "minecraft:block/red_terracotta",
          "d": "minecraft:block/black_terracotta", "cork": "minecraft:block/stripped_oak_log", "label": "minecraft:block/white_terracotta",
          "particle": "minecraft:block/brown_terracotta"}
    bs = [(7, 7, "a", 9), (3.5, 4, "b", 8), (10, 4.5, "c", 10), (4, 10, "d", 9)]
    for n in (1, 2, 3, 4):
        m = BM(f"spirit_bottles_{n}", bt)
        for i in range(n):
            x, z, t, h = bs[i]
            m.box([x, 0, z], [x + 2.5, h - 3, z + 2.5], all="#" + t)
            m.box([x + 0.75, h - 3, z + 0.75], [x + 1.75, h - 0.6, z + 1.75], all="#" + t)
            m.box([x + 0.7, h - 0.6, z + 0.7], [x + 1.8, h, z + 1.8], all="#cork")
            m.box([x - 0.05, 2, z - 0.05], [x + 2.55, 4, z + 0.3], all="#label")
        m.write()
        built.append(m)
    facing_states("spirit_bottles", {f"count={n}": f"pixelpirates:block/spirit_bottles_{n}" for n in (1, 2, 3, 4)})
    item_model("spirit_bottles", "pixelpirates:block/spirit_bottles_3")

    # --- the keg: a cask on its side (ends east/west), cradle, brass tap at the front, a painted plaque per drink
    labels = ["minecraft:block/yellow_terracotta", "minecraft:block/orange_terracotta", "minecraft:block/red_terracotta"]
    for k in range(3):
        m = BM(f"tavern_keg_{k}", {"side": "minecraft:block/barrel_side", "end": "minecraft:block/barrel_top", "wood": "minecraft:block/spruce_log",
                                   "iron": "minecraft:block/iron_block", "tap": "minecraft:block/cut_copper", "label": labels[k],
                                   "particle": "minecraft:block/barrel_side"})
        m.box([2, 3, 3], [14, 14, 14], faces={"east": "#end", "west": "#end", "north": "#side", "south": "#side", "up": "#side", "down": "#side"},
              uvrot={"north": 90, "south": 90, "up": 90, "down": 90})
        for x in (4, 11.5):
            m.box([x, 2.7, 2.7], [x + 0.6, 14.3, 14.3], all="#iron")
        for x in (3, 11):
            m.box([x, 0, 4], [x + 2, 3.5, 13], all="#wood")
        m.box([7, 5, 1.5], [9, 7, 3], all="#tap")
        m.box([7.5, 3.5, 1.5], [8.5, 5, 2.5], all="#tap")
        m.box([7.4, 7, 2], [8.6, 8.5, 2.6], all="#tap")
        m.box([5.5, 9, 2.8], [10.5, 11.5, 3], all="#label")
        m.write()
        built.append(m)
    facing_states("tavern_keg", {f"drink={k}": f"pixelpirates:block/tavern_keg_{k}" for k in range(3)})
    item_model("tavern_keg", "pixelpirates:block/tavern_keg_0")

    # --- the dice cup (with two dice tipped out beside it)
    m = BM("dice_cup", {"leather": "minecraft:block/brown_terracotta", "rim": "minecraft:block/dark_oak_planks", "die": "pixelpirates:block/die",
                        "particle": "minecraft:block/brown_terracotta"})
    m.box([5, 0, 5], [10, 5, 10], all="#leather")
    m.box([4.6, 4.5, 4.6], [10.4, 5.5, 10.4], all="#rim")
    m.box([10.5, 0, 6], [12.5, 2, 8], all="#die", uv={f: [0, 0, 16, 16] for f in ("north", "south", "east", "west", "up")})
    m.box([8, 0, 10.5], [10, 2, 12.5], all="#die", uv={f: [0, 0, 16, 16] for f in ("north", "south", "east", "west", "up")},
          rot=("y", 22.5, [9, 1, 11.5]))
    m.write()
    facing_states("dice_cup", {"": "pixelpirates:block/dice_cup"})
    item_model("dice_cup", "pixelpirates:block/dice_cup")
    built.append(m)

    # --- LIAR'S DICE TABLE: round-ish felt top, pedestal, cups and dice on it
    m = BM("liars_dice_table", {"wood": DO, "log": "minecraft:block/dark_oak_log", "felt": "pixelpirates:block/liars_felt",
                                "gold": "minecraft:block/gold_block", "leather": "minecraft:block/brown_terracotta", "die": "pixelpirates:block/die",
                                "particle": DO})
    m.box([1, 13, 3], [15, 15, 13], all="#wood")
    m.box([3, 13, 1], [13, 15, 15], all="#wood")
    m.box([2, 13, 2], [14, 15, 14], all="#wood")
    m.box([2.5, 15, 3.5], [13.5, 15.2, 12.5], faces={"up": "#felt"})
    m.box([3.5, 15, 2.5], [12.5, 15.2, 13.5], faces={"up": "#felt"})
    m.box([6.5, 2, 6.5], [9.5, 13, 9.5], all="#log")
    m.box([3, 0, 7], [13, 2, 9], all="#wood")
    m.box([7, 0, 3], [9, 2, 13], all="#wood")
    m.box([2.6, 14.5, 2.6], [3.4, 15.4, 3.4], all="#gold")
    for (x, z) in ((3, 5), (10, 10)):                                  # two cups upturned
        m.box([x, 15.2, z], [x + 3, 18.5, z + 3], all="#leather")
    for (x, z) in ((10, 4), (11.5, 6), (5, 10.5)):                     # loose dice
        m.box([x, 15.2, z], [x + 1.6, 16.8, z + 1.6], all="#die", uv={f: [0, 0, 16, 16] for f in ("north", "south", "east", "west", "up")})
    m.write()
    facing_states("liars_dice_table", {"": "pixelpirates:block/liars_dice_table"})
    item_model("liars_dice_table", "pixelpirates:block/liars_dice_table")
    built.append(m)

    # --- CROWN & ANCHOR TABLE: the board cloth on top, the banker's three big dice and his cup
    m = BM("crown_anchor_table", {"wood": SP, "log": "minecraft:block/spruce_log", "cloth": "pixelpirates:block/crown_anchor_cloth",
                                  "die": "pixelpirates:block/die", "leather": "minecraft:block/brown_terracotta", "gold": "minecraft:block/gold_block",
                                  "particle": SP})
    m.box([0, 13, 0], [16, 15, 16], faces={"up": "#cloth", "north": "#wood", "south": "#wood", "east": "#wood", "west": "#wood", "down": "#wood"},
          uv={"up": [0, 0, 16, 16]})
    for (x, z) in ((1, 1), (13, 1), (1, 13), (13, 13)):
        m.box([x, 0, z], [x + 2, 13, z + 2], all="#log")
    m.box([1, 4, 1], [15, 5, 3], all="#wood")
    m.box([1, 4, 13], [15, 5, 15], all="#wood")
    for k, (x, z) in enumerate(((5, 12.5), (8, 12.6), (11, 12.4))):
        m.box([x - 1.2, 15, z - 1.2], [x + 1.2, 17.4, z + 1.2], all="#die", uv={f: [0, 0, 16, 16] for f in ("north", "south", "east", "west", "up")})
    m.box([12.5, 15, 1], [15, 18, 3.5], all="#leather")
    m.box([0.5, 15, 0.5], [2, 15.6, 2], all="#gold")
    m.write()
    facing_states("crown_anchor_table", {"": "pixelpirates:block/crown_anchor_table"})
    item_model("crown_anchor_table", "pixelpirates:block/crown_anchor_table")
    built.append(m)

    # --- the dues board: a framed wall board (front = north), like the drinks menu
    m = BM("dues_board", {"board": "pixelpirates:block/dues_board", "frame": "minecraft:block/dark_oak_planks", "particle": "minecraft:block/dark_oak_planks"})
    m.box([1, 1, 15], [15, 15, 16], faces={"north": "#board", "south": "#frame", "east": "#frame", "west": "#frame", "up": "#frame", "down": "#frame"},
          uv={"north": [0, 0, 16, 16]})
    for f, t in (([0.5, 15, 14.5], [15.5, 16, 16]), ([0.5, 0, 14.5], [15.5, 1, 16]), ([0, 0, 14.5], [1, 16, 16]), ([15, 0, 14.5], [16, 16, 16])):
        m.box(f, t, all="#frame")
    m.write()
    facing_states("dues_board", {"": "pixelpirates:block/dues_board"})
    item_model("dues_board", "pixelpirates:block/dues_board")
    built.append(m)

    # --- the dues ledger: a clerk's desk, the big ledger open on a slope, a stack of doubloons, ink + quill
    m = BM("dues_ledger", {"wood": DO, "log": "minecraft:block/dark_oak_log", "page": "pixelpirates:block/dues_ledger_page",
                           "cover": "minecraft:block/red_terracotta", "gold": "minecraft:block/gold_block", "ink": "minecraft:block/black_concrete",
                           "quill": "minecraft:block/white_wool", "particle": DO})
    m.box([1, 11, 2], [15, 13, 14], all="#wood")
    m.box([2, 0, 3], [4, 11, 5], all="#log"); m.box([12, 0, 3], [14, 11, 5], all="#log")
    m.box([2, 0, 11], [4, 11, 13], all="#log"); m.box([12, 0, 11], [14, 11, 13], all="#log")
    m.box([2, 3, 4], [14, 4, 12], all="#wood")
    m.box([3, 13, 4], [13, 14, 11], faces={"up": "#page", "north": "#cover", "south": "#cover", "east": "#cover", "west": "#cover", "down": "#cover"},
          uv={"up": [0, 0, 16, 16]}, rot=("x", -22.5, [8, 13, 8]))
    m.box([12.5, 13, 11.5], [14.5, 14.5, 13.5], all="#gold"); m.box([12.8, 14.5, 11.8], [14.2, 15.5, 13.2], all="#gold")
    m.box([2, 13, 11.5], [3.5, 14.5, 13], all="#ink")
    m.box([2.6, 14.5, 12.1], [2.9, 18, 12.4], all="#quill", rot=("z", 22.5, [2.75, 14.5, 12.25]))
    m.write()
    facing_states("dues_ledger", {"": "pixelpirates:block/dues_ledger"})
    item_model("dues_ledger", "pixelpirates:block/dues_ledger")
    built.append(m)

    # --- the berth bollard: stone plinth, black iron bollard with a cap and horns, hawser round it, a brass dues box
    m = BM("berth_bollard", {"stone": "minecraft:block/stone_bricks", "iron": "minecraft:block/anvil", "black": "minecraft:block/polished_blackstone",
                             "rope": "pixelpirates:block/rope", "gold": "minecraft:block/gold_block", "slot": "minecraft:block/black_concrete",
                             "particle": "minecraft:block/polished_blackstone"})
    m.box([3, 0, 3], [13, 2, 13], all="#stone")
    m.box([5, 2, 5], [11, 10, 11], all="#black")
    m.box([4, 10, 4], [12, 12, 12], all="#black")
    m.box([3, 8, 7], [13, 9, 9], all="#iron")
    m.box([4.6, 4, 4.6], [11.4, 6, 11.4], all="#rope")
    m.box([6.5, 4, 2.6], [9.5, 7, 4.6], all="#gold")
    m.box([7.3, 6.2, 2.4], [8.7, 6.6, 2.6], all="#slot")
    m.write()
    facing_states("berth_bollard", {"": "pixelpirates:block/berth_bollard"})
    item_model("berth_bollard", "pixelpirates:block/berth_bollard")
    built.append(m)

    PREV.mkdir(parents=True, exist_ok=True)
    for b in built:
        b.preview(PREV / f"{b.name}.png")
    print("models:", len(built))


# ------------------------------------------------------------------------------------------------ lang
LANG_E = {
    "block.pixelpirates.tavern_sign": "Tavern Sign", "block.pixelpirates.chandlery_sign": "Chandler's Sign", "block.pixelpirates.bakery_sign": "Bakery Sign", "block.pixelpirates.harbour_sign": "Harbour Master's Sign", "block.pixelpirates.warehouse_sign": "Trading Company Sign", "block.pixelpirates.distillery_sign": "Distillery Sign", "block.pixelpirates.fish_sign": "Fish Market Sign", "block.pixelpirates.dock_sign": "Dock Office Sign", "block.pixelpirates.inn_sign": "Inn Sign", "block.pixelpirates.perch_branch": "Perch Branch", "block.pixelpirates.parrot_roost": "Parrot Roost", "block.pixelpirates.dues_ledger": "Harbour Dues Ledger", "block.pixelpirates.berth_bollard": "Berth Bollard", "block.pixelpirates.dues_board": "Harbour Dues Board", "block.pixelpirates.drinks_menu": "Drinks Menu",
    "block.pixelpirates.tankard": "Tankard", "block.pixelpirates.spirit_bottles": "Spirit Bottles", "block.pixelpirates.tavern_keg": "Tavern Keg",
    "block.pixelpirates.dice_cup": "Dice Cup", "block.pixelpirates.liars_dice_table": "Liar's Dice Table",
    "block.pixelpirates.crown_anchor_table": "Crown & Anchor Table",
    "item.pixelpirates.tankard_of_ale": "Tankard of Ale", "item.pixelpirates.honey_mead": "Honey Mead",
    "item.pixelpirates.spiced_wine": "Spiced Wine", "item.pixelpirates.bilge_whiskey": "Bilge-Rat Whiskey",
    "item.pixelpirates.krakens_kiss": "Kraken's Kiss",
}


def lang():
    d = json.loads(LANG.read_text(encoding="utf-8"))
    d.update(LANG_E)
    LANG.write_bytes(json.dumps(d, indent=2, ensure_ascii=False).encode("utf-8"))
    print("lang +", len(LANG_E))


if __name__ == "__main__":
    what = sys.argv[1:] or ["tex", "gui", "models", "lang"]
    if "tex" in what:
        tex_block(); tex_items()
    if "gui" in what:
        gui()
    if "models" in what:
        models()
    if "lang" in what:
        lang()
