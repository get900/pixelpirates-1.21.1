"""PARROT TYPES (2026-10-03, docs/parrot_ideas.md phase 1): the 10 extra parrot textures.

    python tools/gen_parrot_textures.py

Each one recolours the vanilla parrot's body parts (read from the Minecraft client jar in the gradle cache - vanilla art is
never copied into the repo) keeping its per-pixel shading, then paints the type's own marks (cheeks, sunglasses, speckles).
Writes assets/pixelpirates/textures/entity/parrot/<id>.png (32x32, the vanilla parrot UV layout) + tools/previews/parrots.png.
Ids must match homestead/parrot/ParrotTypes.
"""
import glob, io, random, zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/pixelpirates/textures/entity/parrot"
PREV = ROOT / "tools/previews/parrots.png"

# body-part boxes on the 32x32 sheet (from ParrotEntityModel's UVs), x1, y1, x2, y2 exclusive
PARTS = {
    "head": (2, 2, 10, 7), "crest": (10, 0, 22, 5), "feather": (2, 22, 10, 27), "body": (2, 8, 14, 17),
    "wings": (19, 8, 27, 16), "tail": (22, 1, 30, 6), "beak": (11, 7, 20, 10), "legs": (14, 18, 18, 21),
}
EYES = [(3, 4), (6, 4)]                    # the dark eye pixels on the head's two side faces
CHEEKS = [(3, 5), (6, 5)]


def vanilla(name):
    jars = glob.glob(str(Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"))
    with zipfile.ZipFile(jars[0]) as z:
        return Image.open(io.BytesIO(z.read(f"assets/minecraft/textures/entity/parrot/parrot_{name}.png"))).convert("RGBA")


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def recolour(base, colours):
    """colours: part -> (r,g,b). Each pixel = colour x (its brightness / the part's mean brightness), eyes kept."""
    im = base.copy()
    px = im.load()
    for part, col in colours.items():
        x1, y1, x2, y2 = PARTS[part]
        cells = [(x, y) for x in range(x1, x2) for y in range(y1, y2) if px[x, y][3] > 0 and (x, y) not in EYES]
        if not cells:
            continue
        mean = sum(lum(px[c]) for c in cells) / len(cells) or 1
        for c in cells:
            k = max(0.65, min(1.35, lum(px[c]) / mean))
            px[c] = tuple(max(0, min(255, int(v * k))) for v in col) + (255,)
    return im


def put(im, cells, col):
    px = im.load()
    for c in cells:
        px[c] = col + (255,) if len(col) == 3 else col


def rows(part, y_from_bottom, n=1):
    """The cells of a part's bottom rows (y_from_bottom 0 = last row)."""
    x1, y1, x2, y2 = PARTS[part]
    return [(x, y) for x in range(x1, x2) for y in range(y2 - y_from_bottom - n, y2 - y_from_bottom)]


def speckle(im, part, col, chance, seed):
    r = random.Random(seed)
    x1, y1, x2, y2 = PARTS[part]
    put(im, [(x, y) for x in range(x1, x2) for y in range(y1, y2) if r.random() < chance], col)


def make(base):
    grey = base
    t = {}

    im = recolour(grey, {"head": (255, 205, 40), "crest": (255, 180, 40), "feather": (255, 170, 40), "body": (250, 128, 32),
                         "wings": (245, 120, 30), "tail": (60, 165, 60), "beak": (60, 60, 64), "legs": (120, 120, 120)})
    put(im, rows("wings", 0, 2), (60, 165, 60)); put(im, rows("body", 0, 2), (235, 90, 30))
    t["sunset_conure"] = im

    im = recolour(grey, {"head": (240, 240, 234), "crest": (250, 228, 110), "feather": (250, 225, 100), "body": (238, 238, 232),
                         "wings": (232, 232, 226), "tail": (236, 236, 230), "beak": (60, 60, 66), "legs": (130, 130, 134)})
    t["cockatoo"] = im

    im = recolour(grey, {"head": (50, 80, 205), "crest": (50, 90, 210), "feather": (60, 100, 215), "body": (60, 170, 50),
                         "wings": (60, 165, 50), "tail": (60, 160, 50), "beak": (230, 80, 40), "legs": (120, 120, 120)})
    put(im, [(x, y) for x in range(5, 8) for y in range(11, 17)], (250, 125, 30))      # the orange chest (body front)
    put(im, [(x, 11) for x in range(5, 8)], (250, 215, 40))
    put(im, rows("tail", 0), (240, 210, 40))
    t["rainbow_lorikeet"] = im

    im = recolour(grey, {"head": (255, 225, 70), "crest": (255, 222, 80), "feather": (255, 220, 80), "body": (248, 236, 180),
                         "wings": (244, 232, 176), "tail": (246, 236, 186), "beak": (150, 140, 130), "legs": (150, 150, 150)})
    put(im, CHEEKS, (235, 110, 40))
    t["cockatiel"] = im

    im = recolour(grey, {"head": (40, 40, 44), "crest": (36, 36, 40), "feather": (34, 34, 38), "body": (38, 38, 42),
                         "wings": (36, 36, 40), "tail": (34, 34, 38), "beak": (22, 22, 24), "legs": (60, 60, 60)})
    put(im, [(2, 5), (3, 5), (2, 6), (3, 6), (6, 5), (7, 5), (6, 6), (7, 6)], (205, 40, 40))
    t["black_palm_cockatoo"] = im

    im = recolour(grey, {"head": (165, 160, 90), "crest": (120, 145, 55), "feather": (115, 140, 55), "body": (110, 140, 50),
                         "wings": (105, 135, 48), "tail": (100, 130, 46), "beak": (205, 195, 165), "legs": (150, 140, 120)})
    for p, s in (("body", 1), ("wings", 2), ("tail", 3), ("crest", 4)): speckle(im, p, (185, 180, 70), 0.22, s)
    put(im, [(4, 5), (5, 5)], (190, 180, 120))                                          # the pale owl face
    t["kakapo"] = im

    im = recolour(grey, {"head": (205, 228, 242), "crest": (215, 235, 248), "feather": (220, 238, 250), "body": (200, 225, 240),
                         "wings": (190, 218, 236), "tail": (196, 222, 240), "beak": (160, 190, 210), "legs": (170, 196, 214)})
    put(im, EYES, (110, 225, 235))
    t["ghost_parrot"] = im

    im = recolour(grey, {"head": (232, 182, 52), "crest": (240, 196, 70), "feather": (240, 196, 70), "body": (228, 176, 48),
                         "wings": (222, 170, 44), "tail": (226, 174, 46), "beak": (140, 96, 20), "legs": (160, 120, 40)})
    for p, s in (("body", 5), ("wings", 6), ("head", 7)): speckle(im, p, (255, 244, 160), 0.12, s)
    t["gilded_parrot"] = im

    im = recolour(grey, {"head": (90, 18, 14), "crest": (200, 60, 20), "feather": (220, 80, 20), "body": (100, 20, 15),
                         "wings": (70, 14, 12), "tail": (60, 12, 10), "beak": (30, 24, 22), "legs": (50, 40, 36)})
    put(im, rows("wings", 0, 2), (255, 130, 30)); put(im, rows("tail", 0), (255, 150, 40))
    put(im, [(x, 4) for x in range(2, 9)], (14, 14, 16))                                # SUNGLASSES: a black band across the eyes
    put(im, [(4, 5), (5, 5)], (14, 14, 16)); put(im, [(2, 4), (7, 4)], (120, 220, 255))   # + a lens glint each side
    t["ember_macaw"] = im

    im = recolour(grey, {"head": (48, 26, 64), "crest": (70, 36, 92), "feather": (80, 40, 100), "body": (44, 24, 60),
                         "wings": (40, 22, 56), "tail": (36, 20, 52), "beak": (24, 16, 30), "legs": (40, 30, 50)})
    put(im, EYES, (40, 235, 210))
    put(im, [(20, 10), (24, 10), (21, 13), (25, 13), (5, 12), (9, 14)], (40, 220, 200))   # teal spots on the wings + back
    t["krakens_pet"] = im
    return t


def crate_icon():
    """16x16 item icon: a wicker travelling crate, a green parrot peeking through the bars."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    wick, dark, bar = (196, 150, 84, 255), (120, 82, 40, 255), (70, 50, 30, 255)
    for x in range(2, 14):
        for y in range(4, 15):
            px[x, y] = dark if x in (2, 13) or y in (4, 14) else (wick if (x + y) % 2 else (176, 130, 70, 255))
    for x in range(4, 13, 2):                                     # the barred window
        for y in range(6, 12): px[x, y] = bar
    for x, y in ((5, 8), (6, 8), (7, 8), (5, 9), (6, 9), (7, 9), (6, 7)): px[x, y] = (70, 180, 60, 255)
    px[7, 8] = (20, 20, 20, 255); px[8, 9] = (240, 170, 40, 255)
    for x in range(6, 10): px[x, 3] = dark                         # the handle
    px[5, 2] = px[10, 2] = dark; px[6, 2] = px[9, 2] = dark
    d = ROOT / "src/main/resources/assets/pixelpirates/textures/item"
    im.save(d / "parrot_crate.png")
    print("wrote parrot_crate icon")


def glowmask(im, keep):
    """Emissive layer (drawn full-bright over the parrot): only the pixels keep(x, y, rgba) -> new rgba or None.
    Never all-transparent (crash cause #8's rule)."""
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    src, dst = im.load(), out.load()
    for x in range(im.width):
        for y in range(im.height):
            if src[x, y][3]:
                v = keep(x, y, src[x, y])
                if v: dst[x, y] = v
    assert out.getbbox(), "empty glowmask"
    return out


def glows(t):
    def inside(x, y, part):
        x1, y1, x2, y2 = PARTS[part]
        return x1 <= x < x2 and y1 <= y < y2
    parts = [k for k in PARTS]
    on_bird = lambda x, y: any(inside(x, y, k) for k in parts)
    g = {}
    # the ghost: the whole bird glows faintly (a dim copy of itself)
    g["ghost_parrot"] = glowmask(t["ghost_parrot"], lambda x, y, c: (c[0] * 45 // 100, c[1] * 50 // 100, c[2] * 55 // 100, 255) if on_bird(x, y) else None)
    # the ember: its orange wing edges, tail tips and crest
    g["ember_macaw"] = glowmask(t["ember_macaw"], lambda x, y, c: c if on_bird(x, y) and c[0] > 180 and c[1] > 50 and c[2] < 80 else None)
    # the kraken's pet: the teal eyes and spots
    g["krakens_pet"] = glowmask(t["krakens_pet"], lambda x, y, c: c if on_bird(x, y) and c[1] > 180 and c[2] > 160 and c[0] < 80 else None)
    return g


def main():
    crate_icon()
    OUT.mkdir(parents=True, exist_ok=True)
    tex = make(vanilla("grey"))
    for k, im in tex.items():
        im.save(OUT / f"{k}.png")
        print("wrote", k)
    for k, im in glows(tex).items():
        im.save(OUT / f"{k}_glow.png")
        print("wrote", k, "glow", sum(1 for p in im.getdata() if p[3]), "px")
    sheet = Image.new("RGBA", (len(tex) * 200, 220), (40, 40, 44, 255))
    for i, (k, im) in enumerate(tex.items()):
        sheet.alpha_composite(im.resize((192, 192), Image.NEAREST), (i * 200 + 4, 4))
    PREV.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREV)


if __name__ == "__main__":
    main()
