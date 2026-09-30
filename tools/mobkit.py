"""Pixel Pirates - mob modelling kit (used by gen_mob_roster.py).

Builds on gen_mob_assets.Model and adds what a faithful voxel model needs:
  * per-cube rotation + pivot (angled fins, horns, jaws, splayed legs)
  * hand-drawn pixel art per face: art={"north": A(rows)} - rows of palette characters,
    '.' keeps the cube's base material, '_' is a transparent (cutout) pixel, a palette value
    starting with '!' glows (goes into the GeckoLib glowmask)
  * banded materials (boots, belts, sleeves), clustered-noise surfaces and overlays

COORDINATES are Bedrock/geo-file units (16 px = 1 block, feet at y = 0, the model FACES -Z).
GeckoLib mirrors X when it bakes the model, so in game the character's RIGHT hand is at -X in
this file. Rotation signs as they appear in game (verified against GeckoLib 4.4.9 source):
  +X pitches the front (-Z) down      -> a hanging arm swings FORWARD with negative X
  +Y turns the front toward the character's RIGHT
  +Z rolls the bottom toward the character's RIGHT (-X here) -> right arm flares out with +Z,
     left arm flares out with -Z
Face art is drawn as seen from outside the face (north = looking at the face; up = seen from
above by someone standing in front, so the top row is the model's BACK).
"""
from __future__ import annotations

import math
import random

from gen_mob_assets import (FACE_LIGHT, Model, anim, hexc, keys, mix, mul, wave, S, C)

__all__ = ["Rig", "A", "noise", "grad", "counter", "bands", "over", "solid", "lit", "spots", "veins",
           "drips", "speckle", "border", "rows_band", "anim", "keys", "wave", "S", "C", "hexc", "mix", "mul"]

SIDES = ("north", "south", "east", "west")


# =====================================================================================
# pixel art
# =====================================================================================
class A:
    """Face art: rows of palette characters (top row first, as seen looking at the face)."""

    def __init__(self, rows, pal=None, **kw):
        self.rows = [r for r in (rows.strip("\n").split("\n") if isinstance(rows, str) else rows)]
        self.pal = dict(pal or {})
        self.pal.update(kw)

    @staticmethod
    def at(w, h, pts, pal=None, /, **kw):   # positional-only: palette keys may be any letter
        """Exact-pixel art for a w x h face: pts = {(x, y): char} or [(x, y, char), ...]; the rest keeps
        the base material. Negative x/y count from the right/bottom edge."""
        grid = [["."] * w for _ in range(h)]
        items = pts.items() if isinstance(pts, dict) else [((p[0], p[1]), p[2]) for p in pts]
        for (x, y), ch in items:
            grid[y % h][x % w] = ch
        return A(["".join(r) for r in grid], pal, **kw)

    def flipped(self):
        return A([r[::-1] for r in self.rows], self.pal)

    def apply(self, face, w, h, px, gl, alpha):
        rows = self.rows
        rh, rw = len(rows), max(len(r) for r in rows)
        for y in range(h):
            ry = min(rh - 1, int(y * rh / h))
            row = rows[ry]
            for x in range(w):
                rx = min(rw - 1, int(x * rw / w))
                ch = row[rx] if rx < len(row) else "."
                if ch == ".":
                    continue
                if ch == "_":
                    alpha[y][x] = 0
                    continue
                val = self.pal.get(ch)
                if val is None:
                    raise KeyError(f"palette has no '{ch}'")
                if val.startswith("!"):
                    px[y][x] = hexc(val[1:]); gl[y][x] = True
                else:
                    shade = FACE_LIGHT[face] * (1 + (random.random() - 0.5) * 0.05)
                    px[y][x] = mul(hexc(val), shade); gl[y][x] = False
                alpha[y][x] = 255


# =====================================================================================
# materials:  painter(face, w, h, rng) -> (px[h][w], glow[h][w])
# =====================================================================================
def _g(w, h, v):
    return [[v for _ in range(w)] for _ in range(h)]


def solid(color, edge=0.88, jitter=0.04):
    b = hexc(color)

    def paint(face, w, h, rng):
        px = _g(w, h, b)
        for y in range(h):
            for x in range(w):
                f = FACE_LIGHT[face] * (1 + (rng.random() - 0.5) * 2 * jitter)
                if edge and w > 2 and h > 2 and (x in (0, w - 1) or y in (0, h - 1)):
                    f *= edge
                px[y][x] = mul(b, f)
        return px, _g(w, h, False)
    return paint


def lit(color, jitter=0.05):
    """Emissive: full brightness on every face, all pixels in the glowmask."""
    b = hexc(color)

    def paint(face, w, h, rng):
        px = [[mul(b, 1 + (rng.random() - 0.5) * 2 * jitter) for _ in range(w)] for _ in range(h)]
        return px, _g(w, h, True)
    return paint


def noise(cols, blot=2, edge=0.86, seed=0):
    """Minecraft-texture look: blotchy clusters picked from a dark->light palette."""
    cs = [hexc(c) for c in cols]

    def paint(face, w, h, rng):
        r0 = rng.randrange(1 << 30) ^ seed
        px = _g(w, h, cs[0])
        for y in range(h):
            for x in range(w):
                rr = random.Random(hash((x // blot, y // blot, r0)))
                t = rr.random() * 0.65 + rng.random() * 0.35
                c = cs[min(len(cs) - 1, int(t * len(cs)))]
                f = FACE_LIGHT[face]
                if edge and w > 2 and h > 2 and (x in (0, w - 1) or y in (0, h - 1)):
                    f *= edge
                px[y][x] = mul(c, f)
        return px, _g(w, h, False)
    return paint


def grad(top, bottom, blot=2, edge=0.88):
    """Side faces fade top->bottom between two palettes; up = top, down = bottom."""
    nt, nb = noise(top, blot, edge), noise(bottom, blot, edge)

    def paint(face, w, h, rng):
        if face == "up":
            return nt(face, w, h, rng)
        if face == "down":
            return nb(face, w, h, rng)
        a, _ = nt(face, w, h, rng)
        b, _ = nb(face, w, h, rng)
        px = [[mix(a[y][x], b[y][x], y / max(1, h - 1)) for x in range(w)] for y in range(h)]
        return px, _g(w, h, False)
    return paint


def counter(top, belly, split=0.6, jag=True):
    """Countershading (sharks, fish): dark back, pale belly, a ragged line between them."""
    nt, nb = noise(top), noise(belly)

    def paint(face, w, h, rng):
        if face == "up":
            return nt(face, w, h, rng)
        if face == "down":
            return nb(face, w, h, rng)
        a, _ = nt(face, w, h, rng)
        b, _ = nb(face, w, h, rng)
        px = _g(w, h, (0, 0, 0))
        for x in range(w):
            cut = int(h * split) + ((1 if (x // 2) % 2 else 0) if jag else 0)
            for y in range(h):
                px[y][x] = a[y][x] if y < cut else b[y][x]
        return px, _g(w, h, False)
    return paint


def bands(base, *bs):
    """Horizontal bands on the side faces: bands(base, (y0, y1, material), ...) with y from the
    TOP of the cube in pixels (negative = from the bottom). The down face takes the lowest band."""
    def paint(face, w, h, rng):
        px, gl = base(face, w, h, rng)
        for (y0, y1, mat) in bs:
            a0 = y0 if y0 >= 0 else h + y0
            a1 = y1 if y1 > 0 else h + y1
            if face in SIDES:
                bp, bg = mat(face, w, h, rng)
                for y in range(max(0, a0), min(h, a1)):
                    px[y] = list(bp[y]); gl[y] = list(bg[y])
            elif face == "down" and a1 >= h:
                return mat(face, w, h, rng)
            elif face == "up" and a0 <= 0:
                return mat(face, w, h, rng)
        return px, gl
    return paint


def rows_band(*bs):
    return bs


def over(base, *overlays):
    """Apply overlay functions f(face, w, h, px, gl, rng) on top of a base material."""
    def paint(face, w, h, rng):
        px, gl = base(face, w, h, rng)
        px = [list(r) for r in px]; gl = [list(r) for r in gl]
        for o in overlays:
            o(face, w, h, px, gl, rng)
        return px, gl
    return paint


def spots(color, density=0.08, glow=False, faces=None, size=1):
    c = hexc(color)

    def o(face, w, h, px, gl, rng):
        if faces and face not in faces:
            return
        n = max(0, int(round(w * h * density / (size * size))))
        for _ in range(n):
            x0, y0 = rng.randrange(w), rng.randrange(h)
            for dy in range(size):
                for dx in range(size):
                    x, y = x0 + dx, y0 + dy
                    if x < w and y < h:
                        px[y][x] = c if glow else mul(c, FACE_LIGHT[face]); gl[y][x] = glow
    return o


def speckle(color, density=0.05, faces=None):
    return spots(color, density, False, faces)


def veins(color, density=0.04, glow=True, faces=None, length=(3, 7)):
    """Random-walk cracks/veins (lava cracks, bioluminescent lines)."""
    c = hexc(color)

    def o(face, w, h, px, gl, rng):
        if faces and face not in faces:
            return
        walks = max(1, int(w * h * density / 5)) if w * h >= 6 else 0
        for _ in range(walks):
            x, y = rng.randrange(w), rng.randrange(h)
            for _ in range(rng.randint(*length)):
                if 0 <= x < w and 0 <= y < h:
                    px[y][x] = c if glow else mul(c, FACE_LIGHT[face]); gl[y][x] = glow
                if rng.random() < 0.5:
                    x += rng.choice((-1, 1))
                else:
                    y += rng.choice((-1, 1, 1))
    return o


def drips(color, density=0.25, glow=False, faces=SIDES, maxlen=4):
    """Streaks running down from the top edge (slime, lava, seaweed)."""
    c = hexc(color)

    def o(face, w, h, px, gl, rng):
        if face not in faces:
            return
        for x in range(w):
            if rng.random() < density:
                for y in range(min(h, rng.randint(1, maxlen))):
                    px[y][x] = c if glow else mul(c, FACE_LIGHT[face]); gl[y][x] = glow
    return o


def border(color, faces=SIDES, width=1):
    c = hexc(color)

    def o(face, w, h, px, gl, rng):
        if face not in faces:
            return
        for y in range(h):
            for x in range(w):
                if x < width or y < width or x >= w - width or y >= h - width:
                    px[y][x] = mul(c, FACE_LIGHT[face]); gl[y][x] = False
    return o


# =====================================================================================
# Rig = Model + rotated cubes + face art + alpha
# =====================================================================================
class Rig(Model):
    def cube(self, bone, origin, size, mat, decal=None, inflate=0.0, rot=None, pivot=None, art=None):
        c = super().cube(bone, origin, size, mat, decal, inflate)
        c.rot = list(rot) if rot else None
        if pivot is None and rot:
            pivot = [origin[0] + size[0] / 2, origin[1] + size[1] / 2, origin[2] + size[2] / 2]
        c.pivot = list(pivot) if pivot else None
        c.art = art or {}
        return c

    def mirror(self, bone, origin, size, mat, decal=None, inflate=0.0, rot=None, pivot=None, art=None):
        """Cube reflected across x = 0 (rotations mirrored: y and z flip sign)."""
        o = [-(origin[0] + size[0]), origin[1], origin[2]]
        r = [rot[0], -rot[1], -rot[2]] if rot else None
        p = [-pivot[0], pivot[1], pivot[2]] if pivot else None
        if art:   # a true mirror image: east/west trade places and every face flips left-right
            art = {({"east": "west", "west": "east"}.get(f, f)): a.flipped() for f, a in art.items()}
        return self.cube(bone, o, size, mat, decal, inflate, r, p, art)

    def pair(self, bone_r, bone_l, origin, size, mat, **kw):
        """A cube on the character's right (origin as given, x < 0 side) and its mirror on the left bone."""
        self.cube(bone_r, origin, size, mat, **kw)
        return self.mirror(bone_l, origin, size, mat, **kw)

    def paint(self, variant=None):
        from PIL import Image
        img = Image.new("RGBA", (self.tw, self.th), (0, 0, 0, 0))
        gimg = Image.new("RGBA", (self.tw, self.th), (0, 0, 0, 0))
        p, gp = img.load(), gimg.load()
        rng = random.Random(self.seed)
        random.seed(self.seed)
        body_alpha = getattr(self, "alpha", 255)          # < 255: a translucent (ghost) skin - render it translucent
        for c in self.cubes:
            w, h, d = c.size
            u, v = c.uv
            rects = {"up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
                     "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
                     "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h)}
            for face, (fx, fy, fw, fh) in rects.items():
                if fw == 0 or fh == 0:
                    continue
                px, gl = c.mat(face, fw, fh, rng)
                px = [list(r) for r in px]; gl = [list(r) for r in gl]
                if c.decal:
                    c.decal(face, fw, fh, px, gl, variant)
                alpha = _g(fw, fh, 255)
                a = c.art.get(face) or (c.art.get("sides") if face in SIDES else None) or c.art.get("all")
                if a:
                    a.apply(face, fw, fh, px, gl, alpha)
                for yy in range(fh):
                    for xx in range(fw):
                        if alpha[yy][xx] == 0:
                            continue
                        col = px[yy][xx]
                        p[fx + xx, fy + yy] = (*col, body_alpha)
                        if gl[yy][xx]:
                            gp[fx + xx, fy + yy] = (*col, 255)
        return img, gimg

    def geo(self):
        g = super().geo()
        by_bone = {}
        for c in self.cubes:
            by_bone.setdefault(c.bone, []).append(c)
        for b in g["minecraft:geometry"][0]["bones"]:
            for e, c in zip(b.get("cubes", []), by_bone.get(b["name"], [])):
                if c.rot:
                    e["pivot"] = [round(v, 3) for v in c.pivot]
                    e["rotation"] = [round(v, 3) for v in c.rot]
        return g
