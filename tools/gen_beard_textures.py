"""Facial hair textures (2026-10-04): textures/entity/beard/<style>.png for homestead/client/BeardFeature.

    python tools/gen_beard_textures.py

Each is 256 x 256 = the 64 x 64 skin layout at 4x, drawn WHITE/GREY (the colour is a tint at render time):
  the face layer = the head cuboid (box UV at 0,0): front 32..64 x 32..64, right side 0..32 x 32..64 (its FRONT edge on the
  right, x 31), left side 64..96 x 32..64 (front edge on the left), under the chin 64..96 x 0..32 (front edge at the
  BOTTOM row - box-UV down faces have the model's back at the top);
  the hanging piece below the chin (box UV at 0,32 for an 8 x 8 x 2 cuboid, long styles only): front 8..40 x 136..168,
  sides 0..8 / 40..48 x 136..168, back 48..80 x 136..168, top 8..40 x 128..136, bottom 40..72 x 128..136.
Writes tools/previews/beards.png (each style on a skin-coloured face)."""
import random
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/pixelpirates/textures/entity/beard"
PREV = ROOT / "tools/previews/beards.png"

FRONT, RIGHT, LEFT, UNDER = (32, 32), (0, 32), (64, 32), (64, 0)
H_FRONT, H_RIGHT, H_LEFT, H_BACK, H_BOTTOM, H_TOP = (8, 136), (0, 136), (40, 136), (48, 136), (40, 128), (8, 128)


class Tex:
    def __init__(self, seed):
        self.img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
        self.r = random.Random(seed)

    def px(self, ox, oy, x, y, alpha=255, dens=1.0, w=None, h=None):
        if (w is not None and not 0 <= x < w) or (h is not None and not 0 <= y < h): return
        if self.r.random() > dens: return
        v = self.r.choice([255, 244, 232, 218, 205]) if alpha > 200 else 235
        if (x + y * 3) % 7 == 0 and alpha > 200: v = 180                          # a darker strand here and there
        self.img.putpixel((ox + x, oy + y), (v, v, v, alpha))

    def rect(self, region, x0, y0, x1, y1, alpha=255, dens=1.0, w=32, h=32):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1): self.px(region[0], region[1], x, y, alpha, dens, w, h)

    def front_sym(self, fn, alpha=255, dens=1.0):
        """fn(x, y) -> bool over the left half of the front (x 0..15); mirrored to the right."""
        for y in range(32):
            for x in range(16):
                if fn(x, y):
                    self.px(*FRONT, x, y, alpha, dens)
                    self.px(*FRONT, 31 - x, y, alpha, dens)

    def sides(self, y0, depth=16, y1=31, alpha=255, dens=1.0):
        """Hair on both cheeks: the `depth` columns nearest the face, rows y0..y1."""
        for y in range(y0, y1 + 1):
            for d in range(depth):
                self.px(*RIGHT, 31 - d, y, alpha, dens)
                self.px(*LEFT, d, y, alpha, dens)

    def under(self, rows=16, alpha=255, dens=1.0):
        """Under the chin: the rows nearest the front (the bottom of the under-chin region)."""
        for y in range(32 - rows, 32):
            for x in range(32): self.px(*UNDER, x, y, alpha, dens)

    def hang(self, shape):
        """The hanging piece: shape(x, y) over its 32 x 32 front (y 0 = at the chin). Sides/back follow the outline."""
        for y in range(32):
            for x in range(32):
                if shape(x, y): self.px(*H_FRONT, x, y)
            if shape(0, y) or shape(1, y):
                for d in range(8): self.px(*H_RIGHT, d, y)
            if shape(31, y) or shape(30, y):
                for d in range(8): self.px(*H_LEFT, d, y)
            for x in range(32):
                if shape(31 - x, y): self.px(*H_BACK, x, y, 255, 0.85)
        for x in range(32):
            for d in range(8): self.px(*H_TOP, x, d)
            low = max([y for y in range(32) if shape(x, y)], default=-1)
            if low >= 28:
                for d in range(8): self.px(*H_BOTTOM, x, d)


def mouth(x, y): return 11 <= x and 26 <= y <= 27                               # the gap for the mouth (left half coords)


# ------------------------------------------------------------------------------------------------ the styles
def stubble(t):
    t.front_sym(lambda x, y: y >= 22 or (x <= 4 and y >= 14) or (x <= 8 and y >= 20), alpha=150, dens=0.45)
    t.sides(14, alpha=150, dens=0.45)
    t.under(alpha=150, dens=0.45)


def moustache(t):
    t.front_sym(lambda x, y: 22 <= y <= 25 and x >= 7 - (y - 22) and not (y == 22 and x < 9))


def goatee(t):
    t.front_sym(lambda x, y: (23 <= y <= 24 and x >= 9) or (24 <= y <= 31 and 10 <= x <= 11) or (28 <= y <= 31 and x >= 10))
    t.under(rows=10)


def chinstrap(t):
    t.front_sym(lambda x, y: (x <= 2 and y >= 14) or y >= 29)
    t.sides(10, depth=4)
    for y in range(26, 32): t.rect(RIGHT, 0, y, 31, y); t.rect(LEFT, 0, y, 31, y)
    t.under(rows=6)


def mutton_chops(t):
    t.front_sym(lambda x, y: (x <= 6 and 10 <= y <= 28) or (22 <= y <= 25 and x <= 15))
    t.sides(8, depth=20, y1=31)


def short_beard(t):
    t.front_sym(lambda x, y: (y >= 23 and not mouth(x, y)) or (x <= 4 and y >= 13) or (x <= 8 and y >= 20))
    t.sides(12, depth=20)
    t.under()


def french_moustache(t):
    t.front_sym(lambda x, y: (23 <= y <= 24 and x >= 8) or (x in (6, 7) and 21 <= y <= 23) or (x in (4, 5) and 19 <= y <= 21) or (x == 4 and y == 18))


def handlebar(t):
    t.front_sym(lambda x, y: (22 <= y <= 25 and x >= 6) or (3 <= x <= 6 and 18 <= y <= 23) or (1 <= x <= 3 and 15 <= y <= 19))


def full_face(t):
    t.front_sym(lambda x, y: (y >= 23 and not mouth(x, y)) or (x <= 5 and y >= 11) or (x <= 9 and y >= 19))
    t.sides(9, depth=24)
    t.under()


def full_beard(t):
    full_face(t)
    t.hang(lambda x, y: abs(x - 15.5) <= 16 - y * 0.32)


def forked_beard(t):
    full_face(t)
    t.hang(lambda x, y: (y < 14 and abs(x - 15.5) <= 16 - y * 0.25) or (y >= 14 and 3 <= abs(x - 15.5) <= 12 - (y - 14) * 0.45))


def braided_beard(t):
    full_face(t)
    def braid(x, y):
        if y < 8: return abs(x - 15.5) <= 15 - y * 1.2
        return abs(x - 15.5) <= 4 - (1 if (y // 3) % 2 else 0)
    t.hang(braid)
    for y in (18, 27):                                                        # two beads (left un-tinted grey)
        for x in range(12, 20):
            for yy in range(y, y + 3): t.img.putpixel((H_FRONT[0] + x, H_FRONT[1] + yy), (120, 120, 120, 255))


def captains_beard(t):
    full_face(t)
    t.front_sym(lambda x, y: 21 <= y <= 25 and x >= 4)                             # a heavy moustache over it
    def shape(x, y):
        body = abs(x - 15.5) <= 15 - y * 0.3 and y < 24
        braids = y >= 20 and (abs(x - 8) <= 2 or abs(x - 23) <= 2)
        return body or braids
    t.hang(shape)


STYLES = {"stubble": stubble, "moustache": moustache, "goatee": goatee, "chinstrap": chinstrap, "mutton_chops": mutton_chops,
          "short_beard": short_beard, "french_moustache": french_moustache, "handlebar": handlebar, "full_beard": full_beard,
          "forked_beard": forked_beard, "braided_beard": braided_beard, "captains_beard": captains_beard}


def preview(imgs):
    cols, cell = 6, 96
    sheet = Image.new("RGBA", (cols * cell, ((len(imgs) + cols - 1) // cols) * (cell + 40)), (60, 50, 44, 255))
    for i, (name, img) in enumerate(imgs.items()):
        face = Image.new("RGBA", (32, 72), (210, 160, 120, 255))              # skin, then the beard (front + hang) tinted brown
        tint = Image.new("RGBA", (32, 72), (0, 0, 0, 0))
        tint.paste(img.crop((32, 32, 64, 64)), (0, 0))
        tint.paste(img.crop((8, 136, 40, 168)), (0, 32))
        r, g, b, a = tint.split()
        brown = Image.merge("RGBA", (r.point(lambda v: v * 0.47), g.point(lambda v: v * 0.31), b.point(lambda v: v * 0.18), a))
        face.alpha_composite(brown)
        for (ex, ey) in ((8, 16), (20, 16)):
            for x in range(4):
                for y in range(4): face.putpixel((ex + x, ey + y), (250, 250, 250, 255) if x < 2 else (40, 60, 120, 255))
        face = face.resize((48, 108), Image.NEAREST)
        x, y = (i % cols) * cell + 24, (i // cols) * (cell + 40) + 8
        sheet.alpha_composite(face, (x, y))
    sheet.save(PREV)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    out = {}
    for i, (name, fn) in enumerate(STYLES.items()):
        t = Tex(100 + i)
        fn(t)
        t.img.save(OUT / f"{name}.png")
        out[name] = t.img
    preview(out)
    print("beards:", len(out))
