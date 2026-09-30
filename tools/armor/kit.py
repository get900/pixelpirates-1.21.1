"""Pixel Pirates - 3D ARMOR kit (GeckoLib 4.4.9 GeoArmorRenderer), on top of tools/mobkit.py.

PLAYER SPACE (geo units, 16 = 1 block): feet at y 0, the wearer FACES -Z, and the wearer's RIGHT side is -X
(GeckoLib mirrors X when it bakes). The vanilla body: head x -4..4 y 24..32 z -4..4, body x -4..4 y 12..24 z -2..2,
right arm x -8..-4 y 12..24, left arm x 4..8, right leg x -4..0 y 0..12, left leg x 0..4.

BONES (names GeoArmorRenderer looks up; only the ones for the worn slot are drawn):
  HEAD -> armorHead   CHEST -> armorBody, armorRightArm, armorLeftArm   LEGS -> armorRightLeg, armorLeftLeg
  FEET -> armorRightBoot, armorLeftBoot
GeckoLib copies the vanilla parts' rotations onto them every frame, so a cube on armorRightArm swings with the arm.
Build the RIGHT-side part and `pair(...)` mirrors it onto the left bone (art flips too).
"""
from __future__ import annotations

import json
import pathlib
import random
import sys

TOOLS = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))
from mobkit import *          # noqa: E402,F401  (Rig, A, noise, bands, over, lit, veins, spots, border, drips, ...)
from mobkit import Rig, A, SIDES   # noqa: E402

from gen_mob_assets import FACE_LIGHT, hexc, mul, mix   # noqa: E402

ASSETS = TOOLS.parent / "src/main/resources/assets/pixelpirates"


# =====================================================================================
# STRUCTURED MATERIALS (round 2: the concepts read as armor because their surfaces have structure -
# plates, scales, stitching, folds - not random blotches)
# =====================================================================================
def _pick(cs, t):
    return cs[min(len(cs) - 1, max(0, int(t * len(cs))))]


def _cell(*k):
    return random.Random(hash(k)).random()


def plates(cols, rivet=None, ph=4, pw=5, glow_rivet=False):
    """Riveted plate rows: each ph-tall row has a lit top edge, a dark bottom lip and staggered vertical seams;
    rivets sit beside the seams. Up/down faces: plain, slightly darker plate."""
    cs = [hexc(c) for c in cols]
    rc = hexc(rivet) if rivet else None

    def paint(face, w, h, rng):
        L = FACE_LIGHT[face]
        seed = rng.randrange(1 << 20)
        px = [[None] * w for _ in range(h)]
        gl = [[False] * w for _ in range(h)]
        for y in range(h):
            row, ry = y // ph, y % ph
            off = (row * 3) % pw
            for x in range(w):
                col = (x + off) // pw
                c = _pick(cs, 0.25 + 0.5 * _cell(seed, row, col) + 0.25 * rng.random())
                f = L
                if face in ("up", "down"):
                    f *= 0.92
                else:
                    if ry == 0:
                        f *= 1.22
                    elif ry == ph - 1:
                        f *= 0.55
                    if (x + off) % pw == 0 and ry not in (0, ph - 1):
                        f *= 0.72
                px[y][x] = mul(c, f)
                if rc and face not in ("up", "down") and ry == 1 and (x + off) % pw == 1:
                    px[y][x] = rc if glow_rivet else mul(rc, L)
                    gl[y][x] = glow_rivet
        return px, gl
    return paint


def scales(cols, sw=3, sh=2, rim=0.62):
    """Overlapping scales in offset rows: each scale has a dark lower rim, a lit top-left and its own tone."""
    cs = [hexc(c) for c in cols]

    def paint(face, w, h, rng):
        L = FACE_LIGHT[face]
        seed = rng.randrange(1 << 20)
        px = [[None] * w for _ in range(h)]
        for y in range(h):
            row, ly = y // sh, y % sh
            for x in range(w):
                xx = x + (row % 2) * (sw // 2 + 1)
                col, lx = xx // sw, xx % sw
                c = _pick(cs, _cell(seed, row, col) * 0.85 + rng.random() * 0.15)
                f = L
                if ly == sh - 1:
                    f *= rim
                elif lx == 0:
                    f *= 0.84
                elif ly == 0 and lx == 1:
                    f *= 1.18
                px[y][x] = mul(c, f)
        return px, [[False] * w for _ in range(h)]
    return paint


def leather(cols, stitch=None, blot=2):
    """Leather: soft blotches, darker edges, a dashed stitch line one pixel inside the border."""
    cs = [hexc(c) for c in cols]
    sc = hexc(stitch) if stitch else None

    def paint(face, w, h, rng):
        L = FACE_LIGHT[face]
        seed = rng.randrange(1 << 20)
        px = [[None] * w for _ in range(h)]
        for y in range(h):
            for x in range(w):
                c = _pick(cs, _cell(seed, x // blot, y // blot) * 0.7 + rng.random() * 0.3)
                edge = (x in (0, w - 1) or y in (0, h - 1)) and w > 2 and h > 2
                px[y][x] = mul(c, L * (0.8 if edge else 1))
                if sc and w > 4 and h > 4 and 0 < x < w - 1 and 0 < y < h - 1 and (x in (1, w - 2) or y in (1, h - 2)) and (x + y) % 2 == 0:
                    px[y][x] = mul(sc, L)
        return px, [[False] * w for _ in range(h)]
    return paint


def cloth(cols, fold=4):
    """Hanging cloth: soft vertical folds over a fine weave, darker toward the hem."""
    cs = [hexc(c) for c in cols]

    def paint(face, w, h, rng):
        L = FACE_LIGHT[face]
        px = [[None] * w for _ in range(h)]
        for y in range(h):
            for x in range(w):
                c = _pick(cs, 0.35 + rng.random() * 0.4)
                ph = (x % fold) / max(1, fold - 1)
                f = L * (0.8 + 0.3 * abs(ph - 0.5) * 2) * (1 - 0.18 * y / max(1, h - 1))
                if (x + y) % 2 == 0:
                    f *= 0.96
                px[y][x] = mul(c, f)
        return px, [[False] * w for _ in range(h)]
    return paint


def network(color, density=0.05, glow=True, faces=None, length=(6, 14)):
    """A branching crack/vein network (ember seams, gold veins): walks run mostly downward and split."""
    c = hexc(color)

    def o(face, w, h, px, gl, rng):
        if faces and face not in faces:
            return
        seeds = max(1, int(w * h * density / 8)) if w * h >= 12 else 0
        stack = [(rng.randrange(w), rng.randrange(h), rng.randint(*length)) for _ in range(seeds)]
        while stack:
            x, y, n = stack.pop()
            for _ in range(n):
                if 0 <= x < w and 0 <= y < h:
                    px[y][x] = c if glow else mul(c, FACE_LIGHT[face])
                    gl[y][x] = glow
                r = rng.random()
                if r < 0.55:
                    y += 1
                elif r < 0.78:
                    x += 1
                else:
                    x -= 1
                if rng.random() < 0.12 and n > 4:
                    stack.append((x, y, n // 2))
    return o


def trim(color, faces=SIDES, top=True, bottom=False, sides=False, width=1):
    """A metal/gold edge along the top and/or bottom (optionally the sides) of the side faces."""
    c = hexc(color)

    def o(face, w, h, px, gl, rng):
        if face not in faces:
            return
        for y in range(h):
            for x in range(w):
                if (top and y < width) or (bottom and y >= h - width) or (sides and (x < width or x >= w - width)):
                    px[y][x] = mul(c, FACE_LIGHT[face] * (0.95 if y < width else 0.75))
                    gl[y][x] = False
    return o


class AR(Rig):
    """An armor model: the GeckoLib armor skeleton + shells for each slot + helpers."""

    def __init__(self, name, seed, tex=128):
        super().__init__(name, tex, tex, seed)
        self.rng = random.Random(seed * 7 + 3)
        b = self.bone
        b("bipedHead", [0, 24, 0]); b("armorHead", [0, 24, 0], "bipedHead")
        b("bipedBody", [0, 24, 0]); b("armorBody", [0, 24, 0], "bipedBody")
        b("bipedRightArm", [-5, 22, 0]); b("armorRightArm", [-5, 22, 0], "bipedRightArm")
        b("bipedLeftArm", [5, 22, 0]); b("armorLeftArm", [5, 22, 0], "bipedLeftArm")
        b("bipedRightLeg", [-1.9, 12, 0]); b("armorRightLeg", [-1.9, 12, 0], "bipedRightLeg"); b("armorRightBoot", [-1.9, 12, 0], "bipedRightLeg")
        b("bipedLeftLeg", [1.9, 12, 0]); b("armorLeftLeg", [1.9, 12, 0], "bipedLeftLeg"); b("armorLeftBoot", [1.9, 12, 0], "bipedLeftLeg")

    # ------------------------------------------------------------------ the shells (one per slot)
    def helm(self, mat, art=None, h=10, y0=23):
        """Full helmet box 10x10x10 round the head (y 23..33). art = {"north": A(...)} etc."""
        return self.cube("armorHead", [-5, y0, -5], [10, h, 10], mat, art=art)

    def chest(self, mat, art=None):
        return self.cube("armorBody", [-5, 11, -3], [10, 14, 6], mat, art=art)

    def arms(self, mat, art=None, y0=11.5, h=13):
        return self.pair("armorRightArm", "armorLeftArm", [-9, y0, -3], [6, h, 6], mat, art=art)

    def legs(self, mat, art=None, y0=3.5, h=9):
        return self.pair("armorRightLeg", "armorLeftLeg", [-4.5, y0, -2.5], [5, h, 5], mat, art=art)

    def boots(self, mat, art=None, h=6):
        return self.pair("armorRightBoot", "armorLeftBoot", [-5, 0, -3], [6, h, 6], mat, art=art)

    # ------------------------------------------------------------------ helpers
    def R(self, bone_r, origin, size, mat, **kw):
        """A cube on a right bone + its mirror on the matching left bone."""
        left = bone_r.replace("Right", "Left")
        return self.pair(bone_r, left, origin, size, mat, **kw)

    def rag(self, size, depth=3, faces=SIDES, keep_top=0, seed=None):
        """Face art that cuts a ragged bottom edge (torn cloth, kelp, fringe): up to `depth` pixels per column."""
        w, h, d = [int(round(v)) for v in size]
        rng = random.Random(seed if seed is not None else self.rng.randrange(1 << 30))
        out = {}
        for f in faces:
            fw = w if f in ("north", "south") else d
            cuts = [rng.randint(0, depth) for _ in range(fw)]
            rows = []
            for y in range(h):
                rows.append("".join("_" if y >= h - cuts[x] and y >= keep_top else "." for x in range(fw)))
            out[f] = A(rows)
        return out

    def cape(self, bone, x0, x1, y_top, y_bot, z, mat, depth=4, seed=None):
        """A thin cloth panel (1 px) from y_top down to y_bot with a ragged hem."""
        size = [x1 - x0, y_top - y_bot, 1]
        return self.cube(bone, [x0, y_bot, z], size, mat, art=self.rag(size, depth, seed=seed))

    def strands(self, bone, x0, x1, y_top, z, mat, lengths, width=1, depth=1):
        """Hanging strands (kelp, tentacles, fringe, feathers) side by side from y_top downward."""
        x = x0
        i = 0
        while x + width <= x1 + 1e-6:
            L = lengths[i % len(lengths)]
            self.cube(bone, [x, y_top - L, z], [width, L, depth], mat)
            x += width
            i += 1

    def spike(self, bone, base, length, mat, rot, thick=1):
        """A horn/spike/fin: a thin box from `base` pointing up (+y) `length`, then rotated about its base."""
        # rot z is given as "lean OUTWARD" for a spike on the right (-X) side, "inward" on the left - i.e. positive z tips
        # the tip toward -X. GeckoLib's +Z roll tips it toward +X, so the sign is flipped here (x/y are GeckoLib's own).
        o = [base[0] - thick / 2, base[1], base[2] - thick / 2]
        return self.cube(bone, o, [thick, length, thick], mat, rot=[rot[0], rot[1], -rot[2]], pivot=list(base))

    # ------------------------------------------------------------------ bigger silhouettes (round 2)
    def pauldron(self, mats, tiers=3, flare=8, top=22.5, depth=8, width=7, bone="armorRightArm"):
        """Stacked shoulder plates: `tiers` plates stepping down the arm, each a little narrower, flaring outward."""
        mats = mats if isinstance(mats, (list, tuple)) else [mats] * tiers
        for i in range(tiers):
            w, d = width - i * 0.5, depth - i * 0.5
            y = top - i * 2.6
            self.R(bone, [-3 - w + i * 0.25, y, -d / 2], [w, 3, d], mats[i % len(mats)],
                   rot=[0, 0, (flare + i * 3) * 0.5], pivot=[-3, y + 3, 0])

    def gauntlet(self, mat, h=4, y0=11):
        self.R("armorRightArm", [-9.5, y0, -3.5], [7, h, 7], mat)

    def knee(self, mat, y0=6, h=4):
        self.R("armorRightLeg", [-4.75, y0, -3.25], [5, h, 1], mat)

    def cuff(self, mat, y0=5.5, h=2):
        self.R("armorRightBoot", [-5.5, y0, -3.5], [7, h, 7], mat)

    def flare_cape(self, mat, top=23.5, bottom=2, width=11, depth=6, layers=2, flare=7, bone="armorBody", seed=None):
        """A cape wider than the body, flaring back off the legs, in ragged layers (each outer one shorter)."""
        for k in range(layers):
            w = width - k * 2
            b = bottom + k * 5
            size = [w, top - b, 1]
            self.cube(bone, [-w / 2, b, 3.5 + k * 0.6], size, mat,
                      art=self.rag(size, depth - k, seed=None if seed is None else seed + k),
                      rot=[flare + k * 3, 0, 0], pivot=[0, top, 3.5])

    def blade(self, bone, base, length, width, mat, rot, thin="z"):
        """A flat fin/feather/crystal blade from `base`: `length` up, `width` across. thin z = faces the front,
        thin x = faces the side. rot z = lean OUTWARD on the right side (as spike)."""
        if thin == "z":
            o, size = [base[0] - width / 2, base[1], base[2] - 0.5], [width, length, 1]
        else:
            o, size = [base[0] - 0.5, base[1], base[2] - width / 2], [1, length, width]
        return self.cube(bone, o, size, mat, rot=[rot[0], rot[1], -rot[2]], pivot=list(base))

    def chain(self, bone, p0, p1, mat, links=None):
        """A chain of alternating 1-px links from p0 to p1."""
        import math as _m
        n = links or max(2, int(_m.dist(p0, p1)))
        for i in range(n + 1):
            t = i / n
            x, y, z = [p0[j] + (p1[j] - p0[j]) * t for j in range(3)]
            wide = i % 2 == 0
            self.cube(bone, [x - (0.5 if wide else 0.25), y - 0.5, z - (0.25 if wide else 0.5)], [1, 1, 1], mat)

    def ring(self, bone, centre, r, mat, axis="y"):
        """A square ring (manacle, band) of four bars round `centre`, half-size r, flat (axis y) or upright facing z."""
        x, y, z = centre
        if axis == "y":
            self.cube(bone, [x - r, y, z - r], [2 * r, 1, 1], mat)
            self.cube(bone, [x - r, y, z + r - 1], [2 * r, 1, 1], mat)
            self.cube(bone, [x - r, y, z - r + 1], [1, 1, 2 * r - 2], mat)
            self.cube(bone, [x + r - 1, y, z - r + 1], [1, 1, 2 * r - 2], mat)
        else:
            self.cube(bone, [x - r, y - r, z], [2 * r, 1, 1], mat)
            self.cube(bone, [x - r, y + r - 1, z], [2 * r, 1, 1], mat)
            self.cube(bone, [x - r, y - r + 1, z], [1, 2 * r - 2, 1], mat)
            self.cube(bone, [x + r - 1, y - r + 1, z], [1, 2 * r - 2, 1], mat)

    def tentacle(self, bone, base, length, mat, tip_mat=None, rot=(0, 0, 0), width=2, sucker="#d8b8e0", skin="#4e2a62"):
        """A hanging tentacle: a thick upper part with suckers down the front and a thinner curling tip."""
        L1 = max(2, round(length * 0.6)); L2 = max(1, round(length * 0.4))
        suck = {"north": A(["TO", "TT", "OT", "TT"] * L1, T=skin, O=sucker)}
        self.cube(bone, [base[0] - width / 2, base[1] - L1, base[2] - width / 2], [width, L1, width], mat,
                  rot=list(rot), pivot=list(base), art=suck)
        self.cube(bone, [base[0] - 0.5, base[1] - L1 - L2, base[2] - 0.5], [1, L2, 1], tip_mat or mat,
                  rot=[rot[0] - 14, rot[1], rot[2] * 1.4], pivot=list(base))

    # ------------------------------------------------------------------ output
    def write_armor(self):
        self.pack()
        geo_dir = ASSETS / "geo/armor"; tex_dir = ASSETS / "textures/armor"; an_dir = ASSETS / "animations/armor"
        for d in (geo_dir, tex_dir, an_dir):
            d.mkdir(parents=True, exist_ok=True)
        (geo_dir / f"{self.name}.geo.json").write_text(json.dumps(self.geo(), indent=1), encoding="utf-8")
        img, gimg = self.paint()
        img.save(tex_dir / f"{self.name}.png")
        gpath = tex_dir / f"{self.name}_glowmask.png"
        if gimg.getbbox():
            gimg.save(gpath)
        elif gpath.exists():
            gpath.unlink()                              # never ship an empty glowmask (GeckoLib throws - crash cause #8)
        (an_dir / f"{self.name}.animation.json").write_text(json.dumps({"format_version": "1.8.0", "animations": {}}), encoding="utf-8")
        return self.tw, self.th
