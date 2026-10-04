"""SHIP'S CATS (2026-10-04, homestead/cat/CatCoats): the 8 extra cat coats + their glow layers.

    python tools/gen_cat_textures.py

Each coat takes a vanilla cat texture (read from the Minecraft client jar in the gradle cache - vanilla art is never copied
into the repo) and maps its per-pixel brightness onto the coat's own colour ramp, so the vanilla stripes / points / patches
and shading carry over in the new colours; the eyes are kept (or recoloured), then the coat's own marks are painted on
(bengal rosettes, gilt shine). Writes assets/pixelpirates/textures/entity/cat/<id>.png (64x32, the vanilla cat UV layout),
<id>_glow.png for the glowing coats, and tools/previews/cats.png. Ids must match CatCoats.
"""
import io, random, zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/pixelpirates/textures/entity/cat"
PREV = ROOT / "tools/previews/cats.png"
EYES = [(5, 6), (6, 6), (8, 6), (9, 6)]          # iris + pupil, both eyes (the head's front face)
HEAD_FRONT = (5, 5, 10, 9)                      # x1, y1, x2, y2 exclusive: the face (nose, muzzle)


def vanilla(name):
    with zipfile.ZipFile(Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar") as z:
        return Image.open(io.BytesIO(z.read(f"assets/minecraft/textures/entity/cat/{name}.png"))).convert("RGBA")


def lum(c):
    return (0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]) / 255.0


def ramp(stops, t):
    """stops: [(t, (r,g,b)), ...] ascending; linear in between."""
    t = max(0.0, min(1.0, t))
    for (t0, c0), (t1, c1) in zip(stops, stops[1:]):
        if t <= t1:
            f = 0 if t1 == t0 else (t - t0) / (t1 - t0)
            return tuple(int(a + (b - a) * f) for a, b in zip(c0, c1))
    return stops[-1][1]


def remap(base, stops, eyes=None, stretch=True):
    im = base.copy()
    px = im.load()
    ls = [lum(px[x, y]) for y in range(im.height) for x in range(im.width) if px[x, y][3] > 0 and (x, y) not in EYES]
    lo, hi = (min(ls), max(ls)) if stretch else (0.0, 1.0)
    for y in range(im.height):
        for x in range(im.width):
            c = px[x, y]
            if c[3] == 0:
                continue
            if (x, y) in EYES:
                if eyes and (x, y) in ((5, 6), (9, 6)):
                    px[x, y] = eyes + (255,)
                continue
            t = (lum(c) - lo) / max(1e-6, hi - lo)
            px[x, y] = ramp(stops, t) + (255,)
    return im


COATS = {}


def coat(cid, base, stops, eyes=None, glow=None, marks=None):
    COATS[cid] = (base, stops, eyes, glow, marks)


def rosettes(im, rng):
    """Bengal: dark-rimmed rosettes with a rufous centre over the back and sides."""
    px = im.load()
    for _ in range(46):
        x, y = rng.randrange(20, 62), rng.randrange(0, 32)
        if px[x, y][3] == 0:
            continue
        px[x, y] = (150, 84, 34, 255)
        for dx, dy in ((1, 0), (-1, 0), (0, 1)):
            if 0 <= x + dx < 64 and 0 <= y + dy < 32 and px[x + dx, y + dy][3] > 0:
                px[x + dx, y + dy] = (52, 32, 18, 255)


def shine(im, rng):
    """Gilded: bright coin-glints scattered over the gold."""
    px = im.load()
    for _ in range(40):
        x, y = rng.randrange(64), rng.randrange(32)
        if px[x, y][3] > 0 and (x, y) not in EYES:
            px[x, y] = (255, 246, 190, 255)


def spots(im, rng):
    """Sea witch: a few glowing teal flecks (drawn on the glow layer too)."""
    px = im.load()
    pts = []
    for _ in range(18):
        x, y = rng.randrange(20, 62), rng.randrange(0, 32)
        if px[x, y][3] > 0:
            px[x, y] = (60, 230, 210, 255)
            pts.append((x, y))
    return pts


# the coats (base texture, colour ramp dark -> light, eye colour)
coat("marmalade", "tabby", [(0, (120, 52, 14)), (0.45, (214, 118, 40)), (0.8, (246, 182, 96)), (1, (255, 236, 200))])
coat("tortoiseshell", "calico", [(0, (24, 18, 16)), (0.35, (70, 42, 26)), (0.6, (186, 96, 36)), (1, (226, 160, 92))], eyes=(214, 170, 60))
coat("smoke", "british_shorthair", [(0, (52, 58, 72)), (0.5, (104, 114, 132)), (1, (168, 178, 194))], eyes=(224, 150, 50))
coat("snowshoe", "siamese", [(0, (70, 54, 44)), (0.5, (170, 150, 128)), (0.8, (236, 226, 208)), (1, (252, 250, 244))], eyes=(80, 150, 230))
coat("bengal", "tabby", [(0, (70, 40, 18)), (0.5, (190, 132, 58)), (1, (246, 204, 124))], eyes=(120, 190, 70), marks=rosettes)
coat("ghost_cat", "white", [(0, (120, 160, 190)), (0.6, (186, 220, 236)), (1, (232, 248, 255))], eyes=(150, 255, 250), glow="whole")
coat("gilded_cat", "british_shorthair", [(0, (110, 72, 16)), (0.5, (214, 164, 44)), (1, (255, 226, 110))], eyes=(40, 140, 80), marks=shine)
coat("sea_witch", "all_black", [(0, (8, 18, 24)), (0.6, (20, 46, 54)), (1, (40, 84, 92))], eyes=(60, 255, 220), glow="eyes", marks=spots)


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sheet = Image.new("RGBA", (64 * 4 * 2, 32 * 2 * 2), (40, 40, 48, 255))
    for i, (cid, (base, stops, eyes, glow, marks)) in enumerate(COATS.items()):
        im = remap(vanilla(base), stops, eyes)
        rng = random.Random(cid)
        flecks = marks(im, rng) if marks else None
        im.save(OUT / f"{cid}.png")
        if glow:
            g = Image.new("RGBA", im.size, (0, 0, 0, 0))
            gp, ip = g.load(), im.load()
            if glow == "whole":                                         # a dim copy of the whole cat
                for y in range(im.height):
                    for x in range(im.width):
                        c = ip[x, y]
                        if c[3] > 0:
                            gp[x, y] = (c[0] // 2, c[1] // 2, c[2] // 2, 255)
            for x, y in [(5, 6), (9, 6)] + (flecks or []):                # eyes (+ flecks) at full glow
                gp[x, y] = ip[x, y]
            g.save(OUT / f"{cid}_glow.png")
        sheet.alpha_composite(im.resize((128, 64), Image.NEAREST), ((i % 4) * 128, (i // 4) * 64))
    PREV.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREV)
    print("cats:", len(COATS), "->", OUT)


if __name__ == "__main__":
    main()
