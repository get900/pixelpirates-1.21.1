"""Pixel Pirates - GeckoLib mob asset generator (model + texture + glowmask + animations).

Run from the repo root:
    pip install pillow numpy
    python tools/gen_mob_assets.py            # writes all mobs
    python tools/gen_mob_assets.py siren      # just one

Each mob is authored here as bones + cubes (Bedrock units: 16 = 1 block, feet at y=0,
FRONT = -Z / north). The script:
  * packs every cube's box-UV into the texture and paints it from a material
    (scales, basalt-with-glowing-cracks, chest planks, jelly...) plus symmetric face decals,
  * writes <mob>.geo.json with format_version "1.12.0" (the only one GeckoLib 4.4.9 accepts),
  * writes <mob>.png and <mob>_glowmask.png (GeckoLib AutoGlowingGeoLayer),
  * writes <mob>.animation.json - loops are sampled sine curves, one-shots explicit keyframes.
All output is BOM-free UTF-8 (a BOM crashes GeckoLib - see CLAUDE.md crash cause #3).

Rotation convention (verified against GeckoLib 4.4.9 source - see tools/mobkit.py): +X pitches a
bone's FRONT (-Z) DOWN, so a hanging arm swings forward with NEGATIVE X; +Y turns the front toward
the character's RIGHT; +Z rolls the bottom toward the character's RIGHT, which is -X in the geo
file (GeckoLib mirrors X at bake time). Keyframes are ADDED to a bone's rest rotation.
Face decals are drawn left-right symmetric so they can't come out mirrored.
"""
from __future__ import annotations

import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/pixelpirates"


def hexc(h):
    h = h.lstrip("#")
    return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16))


def mul(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


# =====================================================================================
# Materials: painter(face, w, h, rng) -> (pixels[h][w] colours, glow[h][w] bools)
# =====================================================================================
FACE_LIGHT = {"up": 1.12, "down": 0.7, "north": 1.0, "south": 0.9, "east": 0.84, "west": 0.84}


def _grid(w, h, v):
    return [[v for _ in range(w)] for _ in range(h)]


def flat(base, noise=0.06, edge=True):
    b = hexc(base)

    def paint(face, w, h, rng):
        px = _grid(w, h, b)
        for y in range(h):
            for x in range(w):
                f = FACE_LIGHT[face] * (1 + (rng.random() - 0.5) * 2 * noise)
                if edge and w > 2 and h > 2 and (x in (0, w - 1) or y in (0, h - 1)):
                    f *= 0.86
                px[y][x] = mul(b, f)
        return px, _grid(w, h, False)
    return paint


def glow(base, noise=0.05):
    inner = flat(base, noise, edge=False)

    def paint(face, w, h, rng):
        px, _ = inner(face, w, h, rng)
        px = [[mul(c, 1 / FACE_LIGHT[face]) for c in row] for row in px]  # emissive: no face shading
        return px, _grid(w, h, True)
    return paint


def scales(base, dark, light=None):
    b, d = hexc(base), hexc(dark)
    l = hexc(light) if light else mul(b, 1.25)

    def paint(face, w, h, rng):
        px = _grid(w, h, b)
        for y in range(h):
            for x in range(w):
                off = (y // 2) % 2
                c = d if (x + off) % 2 == 0 and y % 2 == 1 else (l if (x + off) % 2 == 1 and y % 2 == 0 and rng.random() < 0.5 else b)
                px[y][x] = mul(c, FACE_LIGHT[face] * (1 + (rng.random() - 0.5) * 0.08))
        return px, _grid(w, h, False)
    return paint


def stone(cols, cracks=None, crack_density=0.0, glow_cracks=True):
    cs = [hexc(c) for c in cols]
    cc = hexc(cracks) if cracks else None

    def paint(face, w, h, rng):
        px = _grid(w, h, cs[0])
        gl = _grid(w, h, False)
        # blotchy 2x2 clusters
        for y in range(h):
            for x in range(w):
                r = random.Random(hash((x // 2, y // 2, face, w, h, rng.random() < 2)) ^ rng.randrange(1 << 20))
                c = cs[min(len(cs) - 1, int((r.random() * 0.6 + rng.random() * 0.4) * len(cs)))]
                f = FACE_LIGHT[face]
                if w > 2 and h > 2 and (x in (0, w - 1) or y in (0, h - 1)):
                    f *= 0.85
                px[y][x] = mul(c, f)
        if cc and crack_density > 0:
            walks = max(1, int(w * h * crack_density / 6))
            for _ in range(walks):
                x, y = rng.randrange(w), rng.randrange(h)
                for _ in range(rng.randint(3, 7)):
                    if 0 <= x < w and 0 <= y < h:
                        px[y][x] = cc if glow_cracks else mul(cc, FACE_LIGHT[face])
                        gl[y][x] = glow_cracks
                    x += rng.choice((-1, 0, 1)); y += rng.choice((-1, 1, 1))
        return px, gl
    return paint


def planks(base, seam, border):
    b, s, bd = hexc(base), hexc(seam), hexc(border)

    def paint(face, w, h, rng):
        px = _grid(w, h, b)
        for y in range(h):
            for x in range(w):
                c = b
                if face not in ("up", "down") and y % 4 == 3:
                    c = s
                if face in ("up", "down") and x % 4 == 3:
                    c = s
                if x in (0, w - 1) or y in (0, h - 1):
                    c = bd
                px[y][x] = mul(c, FACE_LIGHT[face] * (1 + (rng.random() - 0.5) * 0.1))
        return px, _grid(w, h, False)
    return paint


def hair(base):
    b = hexc(base)

    def paint(face, w, h, rng):
        cols = [1 + (rng.random() - 0.5) * 0.35 for _ in range(w)]
        px = [[mul(b, FACE_LIGHT[face] * cols[x] * (1 + (rng.random() - 0.5) * 0.06)) for x in range(w)] for _ in range(h)]
        return px, _grid(w, h, False)
    return paint


def jelly(base, light, spot):
    b, l, s = hexc(base), hexc(light), hexc(spot)

    def paint(face, w, h, rng):
        px = _grid(w, h, b)
        gl = _grid(w, h, False)
        for y in range(h):
            for x in range(w):
                t = 1 - y / max(1, h - 1) if face not in ("up", "down") else 0.8
                c = mix(b, l, t * 0.6)
                if rng.random() < 0.07:
                    c = s
                    gl[y][x] = True
                px[y][x] = mul(c, FACE_LIGHT[face]) if not gl[y][x] else c
        return px, gl
    return paint


def mouth(base):
    """Dark-red mouth interior (top of the mimic's base)."""
    b = hexc(base)

    def paint(face, w, h, rng):
        px = [[mul(b, 0.6 + 0.5 * rng.random() * (0.4 if face == "up" else 1)) for _ in range(w)] for _ in range(h)]
        return px, _grid(w, h, False)
    return paint


# =====================================================================================
# Model
# =====================================================================================
class Cube:
    def __init__(self, bone, origin, size, mat, decal=None, faces=None, inflate=0.0):
        self.bone, self.origin, self.size, self.mat = bone, origin, size, mat
        self.decal, self.faces, self.inflate = decal, faces, inflate
        self.uv = None


class Model:
    def __init__(self, name, tex_w, tex_h, seed):
        self.name, self.tw, self.th, self.seed = name, tex_w, tex_h, seed
        self.bones: list[dict] = []
        self.cubes: list[Cube] = []
        self.anims: dict = {}

    def bone(self, name, pivot, parent=None, rotation=None):
        b = {"name": name, "pivot": list(pivot)}
        if parent:
            b["parent"] = parent
        if rotation:
            b["rotation"] = list(rotation)
        self.bones.append(b)
        return name

    def cube(self, bone, origin, size, mat, decal=None, inflate=0.0):
        size = [max(0, int(round(v))) for v in size]          # box UV needs whole-pixel sizes
        c = Cube(bone, list(origin), size, mat, decal, inflate=inflate)
        self.cubes.append(c)
        return c

    def mirror_x(self, bone, origin, size, mat, decal=None):
        """Same cube reflected across x=0 (for left/right pairs)."""
        return self.cube(bone, [-(origin[0] + size[0]), origin[1], origin[2]], size, mat, decal)

    # ---------------------------------------------------------------- UV packing
    def pack(self):
        """Shelf-pack every cube's box UV; doubles the sheet (up to 512) until everything fits."""
        items = sorted(self.cubes, key=lambda c: -(c.size[2] + c.size[1]))
        while True:
            x = y = row_h = 0
            ok = True
            for c in items:
                w, h, d = c.size
                bw, bh = 2 * d + 2 * w, d + h
                if x + bw > self.tw:
                    x, y, row_h = 0, y + row_h, 0
                if y + bh > self.th or bw > self.tw:
                    ok = False
                    break
                c.uv = [x, y]
                x += bw
                row_h = max(row_h, bh)
            if ok:
                return
            if self.tw >= 512 and self.th >= 512:
                raise SystemExit(f"{self.name}: does not fit a 512x512 texture")
            if self.th <= self.tw:
                self.th *= 2
            else:
                self.tw *= 2

    # ---------------------------------------------------------------- painting
    def paint(self, variant=None):
        img = Image.new("RGBA", (self.tw, self.th), (0, 0, 0, 0))
        gimg = Image.new("RGBA", (self.tw, self.th), (0, 0, 0, 0))
        p, gp = img.load(), gimg.load()
        rng = random.Random(self.seed)
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
                if c.decal:
                    c.decal(face, fw, fh, px, gl, variant)
                for yy in range(fh):
                    for xx in range(fw):
                        col = px[yy][xx]
                        p[fx + xx, fy + yy] = (*col, 255)
                        if gl[yy][xx]:
                            gp[fx + xx, fy + yy] = (*col, 255)
        return img, gimg

    # ---------------------------------------------------------------- output
    def geo(self):
        bones = []
        for b in self.bones:
            bb = dict(b)
            cs = [c for c in self.cubes if c.bone == b["name"]]
            if cs:
                bb["cubes"] = []
                for c in cs:
                    e = {"origin": c.origin, "size": c.size, "uv": c.uv}
                    if c.inflate:
                        e["inflate"] = c.inflate
                    bb["cubes"].append(e)
            bones.append(bb)
        xs = [abs(c.origin[0]) + c.size[0] for c in self.cubes] + [abs(c.origin[2]) + c.size[2] for c in self.cubes]
        ys = [c.origin[1] + c.size[1] for c in self.cubes]
        return {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": f"geometry.{self.name}", "texture_width": self.tw, "texture_height": self.th,
                            "visible_bounds_width": math.ceil(max(xs) / 8) + 2, "visible_bounds_height": math.ceil(max(ys) / 16) + 2,
                            "visible_bounds_offset": [0, max(ys) / 32, 0]},
            "bones": bones}]}

    def write(self, variants=(None,)):
        self.pack()
        (ASSETS / "geo").mkdir(parents=True, exist_ok=True)
        dump(ASSETS / "geo" / f"{self.name}.geo.json", self.geo())
        for var in variants:
            img, gimg = self.paint(var)
            stem = self.name if var is None else f"{self.name}_{var}"
            img.save(ASSETS / "textures/entity" / f"{stem}.png")
            gpath = ASSETS / "textures/entity" / f"{stem}_glowmask.png"
            if gimg.getbbox() is None:
                # GeckoLib 4.4.9 crashes on a glowmask with zero lit pixels; the renderer must skip
                # the glow layer for this variant instead (see GlowingMobRenderer - mimic dormant).
                gpath.unlink(missing_ok=True)
            else:
                gimg.save(gpath)
        dump(ASSETS / "animations" / f"{self.name}.animation.json", {"format_version": "1.8.0", "animations": self.anims})
        print(f"wrote {self.name}: {len(self.bones)} bones, {len(self.cubes)} cubes, anims={list(self.anims)}")


def dump(path: Path, obj):
    path.write_bytes(json.dumps(obj, indent=1).encode("utf-8"))  # no BOM


# =====================================================================================
# Animation helpers
# =====================================================================================
def r3(v):
    return [round(float(a), 3) for a in v]


def wave(length, fn, samples=12):
    """Loop channel sampled from fn(phase 0..2pi) -> [x,y,z]; last key equals the first."""
    keys = {}
    for i in range(samples + 1):
        t = length * i / samples
        keys[f"{round(t, 4)}"] = r3(fn(2 * math.pi * i / samples))
    return keys


def keys(*pairs):
    """Explicit keyframes: keys((0, [..]), (0.2, [..]), ...)"""
    return {f"{round(t, 4)}": r3(v) for t, v in pairs}


def anim(model, name, length, bones, loop=True, hold=False):
    a = {"animation_length": length, "bones": bones}
    if loop:
        a["loop"] = True
    elif hold:
        a["loop"] = "hold_on_last_frame"
    model.anims[name] = a


S = math.sin
C = math.cos


# =====================================================================================
# P1 - SIREN
# =====================================================================================
def siren():
    m = Model("siren", 128, 128, 101)
    skin = flat("#8fc6ad")
    tail = scales("#2f8f86", "#1d5e5a", "#62c4b4")
    hair_m = hair("#1f4a48")
    gold = flat("#e0b84a")

    def face(face, w, h, px, gl, var):
        if face != "north":
            return
        eye = hexc("#8ffcff")
        for x in (1, 2, 5, 6):
            px[4][x] = eye; gl[4][x] = True
        for x in (1, 2, 5, 6):
            px[3][x] = hexc("#1a3a38")          # brows
        px[6][3] = px[6][4] = hexc("#3a5a52")   # mouth
        px[5][3] = px[5][4] = mul(hexc("#8fc6ad"), 0.9)

    m.bone("body", [0, 19, 0])
    m.cube("body", [-4, 19, -2], [8, 10, 4], skin)
    m.cube("body", [-4, 24, -2.5], [8, 3, 1], flat("#d88fa0"))            # shell top
    m.cube("body", [-4.5, 18, -2.5], [9, 2, 5], gold)                      # belt
    m.bone("head", [0, 29, 0], "body")
    m.cube("head", [-4, 29, -4], [8, 8, 8], skin, face)
    m.cube("head", [-4.5, 35, -4.5], [9, 2, 9], hair_m)                    # crown of hair
    m.cube("head", [3.5, 26, -3], [1, 9, 7], hair_m)                       # side locks
    m.mirror_x("head", [3.5, 26, -3], [1, 9, 7], hair_m)
    m.bone("hair", [0, 36, 4], "head")
    m.cube("hair", [-4.5, 21, 3.5], [9, 16, 2], hair_m)                    # long back hair
    for side, sx in (("rarm", -1), ("larm", 1)):
        m.bone(side, [5.5 * sx, 28, 0], "body")
        o = [4, 18, -1.5] if sx > 0 else [-7, 18, -1.5]
        m.cube(side, o, [3, 11, 3], skin)
        m.cube(side, [o[0] - 0.5, 21, -2], [4, 1, 4], gold)                # arm band
    m.bone("tail1", [0, 19, 0], "body")
    m.cube("tail1", [-4, 13, -2], [8, 6, 4], tail)
    m.bone("tail2", [0, 13, 0], "tail1")
    m.cube("tail2", [-3, 8, -1.5], [6, 5, 3], tail)
    m.bone("tail3", [0, 8, 0], "tail2")
    m.cube("tail3", [-2, 3, -1], [4, 5, 2], tail)
    m.bone("fin", [0, 3, 0], "tail3")
    m.cube("fin", [-5, 0, -0.5], [10, 3, 1], flat("#5fc9b8"))
    m.cube("fin", [-1, 2, -1], [2, 1, 2], flat("#2f8f86"))

    anim(m, "idle", 3.0, {
        "body": {"position": wave(3.0, lambda p: [0, S(p) * 0.6, 0]), "rotation": wave(3.0, lambda p: [S(p) * 2, 0, 0])},
        "head": {"rotation": wave(3.0, lambda p: [0, S(p) * 10, S(p + 1) * 3])},
        "hair": {"rotation": wave(3.0, lambda p: [S(p - 0.6) * 5 - 3, 0, 0])},
        "rarm": {"rotation": wave(3.0, lambda p: [S(p) * 8, 0, -8 - S(p) * 6])},
        "larm": {"rotation": wave(3.0, lambda p: [-S(p) * 8, 0, 8 + S(p) * 6])},
        "tail1": {"rotation": wave(3.0, lambda p: [S(p) * 6, 0, 0])},
        "tail2": {"rotation": wave(3.0, lambda p: [S(p - 0.7) * 10, 0, 0])},
        "tail3": {"rotation": wave(3.0, lambda p: [S(p - 1.4) * 14, 0, 0])},
        "fin": {"rotation": wave(3.0, lambda p: [S(p - 2.1) * 20, 0, 0])},
    })
    anim(m, "swim", 1.2, {
        "body": {"rotation": wave(1.2, lambda p: [55, 0, S(p) * 3])},
        "head": {"rotation": wave(1.2, lambda p: [-40, 0, 0])},
        "hair": {"rotation": wave(1.2, lambda p: [15 + S(p - 1) * 8, 0, 0])},
        "rarm": {"rotation": wave(1.2, lambda p: [25 + S(p) * 30, 0, -10])},
        "larm": {"rotation": wave(1.2, lambda p: [25 + S(p) * 30, 0, 10])},
        "tail1": {"rotation": wave(1.2, lambda p: [S(p) * 12, 0, 0])},
        "tail2": {"rotation": wave(1.2, lambda p: [S(p - 0.8) * 20, 0, 0])},
        "tail3": {"rotation": wave(1.2, lambda p: [S(p - 1.6) * 26, 0, 0])},
        "fin": {"rotation": wave(1.2, lambda p: [S(p - 2.4) * 32, 0, 0])},
    })
    anim(m, "sing", 3.0, {
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-22, 0, 0]), (1.5, [-18, 10, 4]), (2.6, [-22, -10, -4]), (3.0, [0, 0, 0]))},
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-40, 0, -60]), (1.5, [-55, 0, -50]), (2.6, [-40, 0, -65]), (3.0, [0, 0, 0]))},
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-40, 0, 60]), (1.5, [-55, 0, 50]), (2.6, [-40, 0, 65]), (3.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [-6, 0, 0]), (2.6, [-6, 0, 0]), (3.0, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (1.5, [0, 1.5, 0]), (3.0, [0, 0, 0]))},
        "hair": {"rotation": keys((0, [0, 0, 0]), (0.8, [10, 0, 0]), (1.8, [-6, 0, 0]), (3.0, [0, 0, 0]))},
    }, loop=False)
    anim(m, "attack", 0.6, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.2, [-130, 0, -20]), (0.35, [20, 0, 10]), (0.6, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.2, [0, -20, 0]), (0.35, [8, 18, 0]), (0.6, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.2, [-10, 10, 0]), (0.6, [0, 0, 0]))},
    }, loop=False)
    return m


# =====================================================================================
# P2 - CORAL JELLY
# =====================================================================================
def coral_jelly():
    m = Model("coral_jelly", 64, 64, 202)
    bell = jelly("#249c96", "#7fe0d0", "#d8fff6")
    m.bone("root", [0, 12, 0])
    m.bone("bell", [0, 12, 0], "root")
    m.cube("bell", [-5, 12, -5], [10, 8, 10], bell)
    m.cube("bell", [-5.5, 11, -5.5], [11, 2, 11], flat("#1c7a76"))           # skirt rim
    for (x, z, hgt, col) in ((-3, -3, 4, "#d23c3c"), (0, 1, 5, "#f07c28"), (2, -3, 3, "#9a4ad6"),
                             (-4, 2, 3, "#e0508a"), (2, 2, 4, "#d23c3c"), (-1, -2, 2, "#f0c040")):
        m.cube("bell", [x, 20, z], [2, hgt, 2], flat(col))                   # coral crown
    for side, sx in (("fin_l", 1), ("fin_r", -1)):
        m.bone(side, [5 * sx, 16, 0], "bell")
        m.cube(side, [5 if sx > 0 else -6, 13, -3], [1, 6, 6], glow("#ffd84a"))
    for i, (x, z) in enumerate(((-3, -3), (3, -3), (0, 0), (-3, 3), (3, 3))):
        a, b = f"tent{i}a", f"tent{i}b"
        m.bone(a, [x, 12, z], "root")
        m.cube(a, [x - 1, 7, z - 1], [2, 5, 2], flat("#3fc0b0"))
        m.bone(b, [x, 7, z], a)
        m.cube(b, [x - 1, 2, z - 1], [2, 5, 2], glow("#9ff5e6"))

    def tent_wave(i, amp, spd_off):
        ox, oz = ((-3, -3), (3, -3), (0, 0), (-3, 3), (3, 3))[i]
        # contraction flares tentacles outward, then they trail back in
        return lambda p: [S(p + spd_off) * amp + (oz * 3), 0, -(S(p + spd_off) * amp * 0.6) - ox * 3]

    bones = {"bell": {"scale": wave(2.0, lambda p: [1 + 0.1 * max(0, S(p)), 1 - 0.14 * max(0, S(p)), 1 + 0.1 * max(0, S(p))]),
                      "position": wave(2.0, lambda p: [0, S(p - 1) * 0.8, 0])},
             "fin_l": {"rotation": wave(2.0, lambda p: [0, 0, S(p) * 30])},
             "fin_r": {"rotation": wave(2.0, lambda p: [0, 0, -S(p) * 30])}}
    for i in range(5):
        bones[f"tent{i}a"] = {"rotation": wave(2.0, tent_wave(i, 12, -0.5 - i * 0.3))}
        bones[f"tent{i}b"] = {"rotation": wave(2.0, lambda p, i=i: [S(p - 1.4 - i * 0.3) * 18, 0, 0])}
    anim(m, "pulse", 2.0, bones)
    sting = {f"tent{i}a": {"rotation": keys((0, [0, 0, 0]), (0.15, [-45 + i * 5, 0, 20 - i * 10]), (0.4, [30, 0, -10]), (0.7, [0, 0, 0]))}
             for i in range(5)}
    sting["bell"] = {"scale": keys((0, [1, 1, 1]), (0.15, [1.2, 0.8, 1.2]), (0.4, [0.95, 1.08, 0.95]), (0.7, [1, 1, 1]))}
    anim(m, "sting", 0.7, sting, loop=False)
    return m


# =====================================================================================
# P3 - MAGMA BRUTE
# =====================================================================================
def magma_brute():
    m = Model("magma_brute", 128, 128, 303)
    basalt = stone(["#1e1c1c", "#2a2727", "#353131", "#403b3a"], "#ff7a1e", 0.03)
    basalt_hot = stone(["#2a2320", "#352b26", "#40332c", "#4c3a30"], "#ffae3a", 0.09)
    ember = hexc("#ff8a2a")
    core = hexc("#ffd070")

    def chest_rune(face, w, h, px, gl, var):
        if face != "north":
            return
        # glowing Y-rune down the chest (symmetric about the centre line)
        pts = []
        for i in range(6):
            pts += [(2 + i, 4 + i), (w - 3 - i, 4 + i)]
        for y in range(10, 14):
            pts += [(w // 2 - 1, y), (w // 2, y)]
        pts += [(w // 2 - 1, 9), (w // 2, 9), (w // 2 - 2, 14), (w // 2 + 1, 14)]
        for x, y in pts:
            if 0 <= x < w and 0 <= y < h:
                px[y][x] = core if (x in (w // 2 - 1, w // 2)) else ember
                gl[y][x] = True

    def brute_face(face, w, h, px, gl, var):
        if face != "north":
            return
        for x in (1, 2, 5, 6):
            px[3][x] = core; gl[3][x] = True
            px[2][x] = hexc("#141212")
        for x in (2, 3, 4, 5):
            px[6][x] = ember; gl[6][x] = True

    m.bone("root", [0, 0, 0])
    m.bone("torso", [0, 9, 0], "root")
    m.cube("torso", [-8, 9, -5], [16, 16, 10], basalt, chest_rune)
    m.cube("torso", [-6, 25, -3], [12, 2, 7], basalt)                       # neck hump
    m.bone("head", [0, 24, -3], "torso")
    m.cube("head", [-4, 22, -8], [8, 8, 8], basalt, brute_face)
    for side, sx in (("rarm", -1), ("larm", 1)):
        fist = side + "_fist"
        m.bone(side, [10 * sx, 23, 0], "torso")
        m.cube(side, [8 if sx > 0 else -13, 13, -3], [5, 11, 6], basalt)
        m.cube(side, [8 if sx > 0 else -14, 21, -4], [6, 5, 8], basalt)     # shoulder boulder
        m.bone(fist, [10.5 * sx, 13, 0], side)
        m.cube(fist, [7 if sx > 0 else -14, 3, -4], [7, 10, 8], basalt_hot)
    for side, sx in (("rleg", -1), ("lleg", 1)):
        m.bone(side, [3.5 * sx, 9, 0], "root")
        m.cube(side, [1 if sx > 0 else -6, 0, -2.5], [5, 9, 5], basalt)

    anim(m, "idle", 3.0, {
        "torso": {"rotation": wave(3.0, lambda p: [S(p) * 2, 0, 0]), "position": wave(3.0, lambda p: [0, S(p) * 0.4, 0])},
        "head": {"rotation": wave(3.0, lambda p: [S(p * 1) * 3, S(p) * 12, 0])},
        "rarm": {"rotation": wave(3.0, lambda p: [S(p) * 3, 0, -4 - S(p) * 3])},
        "larm": {"rotation": wave(3.0, lambda p: [-S(p) * 3, 0, 4 + S(p) * 3])},
    })
    anim(m, "walk", 1.4, {
        "lleg": {"rotation": wave(1.4, lambda p: [S(p) * 26, 0, 0])},
        "rleg": {"rotation": wave(1.4, lambda p: [-S(p) * 26, 0, 0])},
        "rarm": {"rotation": wave(1.4, lambda p: [S(p) * 18, 0, -4])},
        "larm": {"rotation": wave(1.4, lambda p: [-S(p) * 18, 0, 4])},
        "torso": {"rotation": wave(1.4, lambda p: [4, 0, S(p) * 4]),
                  "position": wave(1.4, lambda p: [0, -abs(S(p)) * 1.2, 0])},
        "head": {"rotation": wave(1.4, lambda p: [-4, S(p) * 5, 0])},
    })
    anim(m, "attack", 0.8, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.25, [-110, 10, -10]), (0.45, [20, 0, 0]), (0.8, [0, 0, 0]))},
        "torso": {"rotation": keys((0, [0, 0, 0]), (0.25, [-5, 22, 0]), (0.45, [10, -18, 0]), (0.8, [0, 0, 0]))},
    }, loop=False)
    anim(m, "slam", 1.6, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-165, 0, -10]), (0.6, [-165, 0, -10]), (0.8, [25, 0, 0]), (1.1, [25, 0, 0]), (1.6, [0, 0, 0]))},
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-165, 0, 10]), (0.6, [-165, 0, 10]), (0.8, [25, 0, 0]), (1.1, [25, 0, 0]), (1.6, [0, 0, 0]))},
        "torso": {"rotation": keys((0, [0, 0, 0]), (0.5, [-12, 0, 0]), (0.8, [22, 0, 0]), (1.1, [22, 0, 0]), (1.6, [0, 0, 0])),
                  "position": keys((0, [0, 0, 0]), (0.5, [0, 1.5, 0]), (0.8, [0, -2, 0]), (1.6, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [-15, 0, 0]), (0.8, [-10, 0, 0]), (1.6, [0, 0, 0]))},
    }, loop=False)
    return m


# =====================================================================================
# P4 - MIMIC  (two texture variants: dormant = plain chest, awake = eyes + glow)
# =====================================================================================
def mimic():
    m = Model("mimic", 128, 64, 404)
    wood = planks("#9a6a32", "#6e4a22", "#4a2e14")
    gold = flat("#d9b04a")
    tooth = flat("#ece6d2", edge=False)

    def lid_eyes(face, w, h, px, gl, var):
        if face != "north" or var != "awake":
            return
        eye, pupil = hexc("#7ff6ff"), hexc("#0e2a30")
        for x in (2, 3, w - 4, w - 3):
            px[1][x] = eye; gl[1][x] = True
            px[2][x] = eye; gl[2][x] = True
        px[2][3] = px[2][w - 4] = pupil
        gl[2][3] = gl[2][w - 4] = False

    def base_top(face, w, h, px, gl, var):
        if face == "up":
            mo = mouth("#6e1a1e")(face, w, h, random.Random(7))[0]
            for y in range(1, h - 1):
                for x in range(1, w - 1):
                    px[y][x] = mo[y][x]

    m.bone("root", [0, 0, 0])
    m.bone("base", [0, 0, 0], "root")
    m.cube("base", [-7, 0, -7], [14, 10, 14], wood, base_top)
    for x in (-6, -4, -2, 1, 3, 5):
        m.cube("base", [x, 10, -6.5], [1, 2, 1], tooth)                     # lower fangs (hidden when shut)
    m.bone("tongue", [0, 10, 2], "base")
    m.cube("tongue", [-2, 10, -6], [4, 1, 8], flat("#c0404a"))
    m.bone("lid", [0, 10, 7], "root")
    m.cube("lid", [-7, 10, -7], [14, 4, 14], wood, lid_eyes)
    m.cube("lid", [-1, 8, -8], [2, 4, 1], gold)                             # latch
    for x in (-5, -3, 0, 2, 4):
        m.cube("lid", [x, 8, -6.5], [1, 2, 1], tooth)                       # upper fangs

    anim(m, "dormant", 1.0, {"lid": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, 0]))}})
    anim(m, "awaken", 1.0, {
        "lid": {"rotation": keys((0, [0, 0, 0]), (0.15, [-75, 0, 0]), (0.4, [-50, 0, 0]), (0.6, [-62, 0, 0]), (1.0, [-38, 0, 0]))},
        "tongue": {"rotation": keys((0, [0, 0, 0]), (0.25, [0, 0, 0]), (0.45, [28, 0, 0]), (0.7, [12, 12, 0]), (1.0, [14, 0, 0]))},
        "root": {"position": keys((0, [0, 0, 0]), (0.15, [0, 3, 0]), (0.35, [0, 0, 0]), (0.5, [0, 1, 0]), (0.6, [0, 0, 0]))},
    }, loop=False)  # NOT hold_on_last_frame: a held trigger never ends and would pin the lid
    anim(m, "idle", 2.0, {
        "lid": {"rotation": wave(2.0, lambda p: [-38 + S(p) * 7, 0, 0])},
        "tongue": {"rotation": wave(2.0, lambda p: [14 + S(p * 2) * 8, S(p) * 10, 0])},
    })
    anim(m, "walk", 0.6, {
        "root": {"position": wave(0.6, lambda p: [0, max(0, S(p)) * 4, 0]),
                 "rotation": wave(0.6, lambda p: [-max(0, S(p)) * 8, 0, 0])},
        "lid": {"rotation": wave(0.6, lambda p: [-30 - max(0, S(p)) * 15, 0, 0])},
        "tongue": {"rotation": wave(0.6, lambda p: [20 + S(p - 1) * 12, 0, 0])},
    })
    anim(m, "bite", 0.5, {
        "lid": {"rotation": keys((0, [-38, 0, 0]), (0.12, [-85, 0, 0]), (0.22, [0, 0, 0]), (0.3, [-5, 0, 0]), (0.5, [-38, 0, 0]))},
        "root": {"rotation": keys((0, [0, 0, 0]), (0.12, [-10, 0, 0]), (0.22, [8, 0, 0]), (0.5, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (0.12, [0, 1, -2]), (0.22, [0, 0, -3]), (0.5, [0, 0, 0]))},
        "tongue": {"rotation": keys((0, [14, 0, 0]), (0.12, [-10, 0, 0]), (0.22, [0, 0, 0]), (0.5, [14, 0, 0]))},
    }, loop=False)
    return m


# =====================================================================================
# P5 - ABYSSAL ANGLER
# =====================================================================================
def abyssal_angler():
    m = Model("abyssal_angler", 128, 128, 505)
    hide = stone(["#1c2a2e", "#22343a", "#2a3e44", "#34494f"], "#3fd0ff", 0.02)
    fin_m = flat("#16242a")
    tooth = flat("#e8e2cc", edge=False)
    m.bone("root", [0, 8, 0])
    m.bone("body", [0, 8, 0], "root")
    m.cube("body", [-7, 5, -8], [14, 11, 16], hide)
    for x in (-6, -4, -2, 1, 3, 5):
        m.cube("body", [x, 2, -7.5], [1, 3, 1], tooth)                      # upper fangs
    m.cube("body", [7, 11, -5], [1, 2, 2], glow("#3fd0ff"))                 # eyes
    m.cube("body", [-8, 11, -5], [1, 2, 2], glow("#3fd0ff"))
    m.cube("body", [-0.5, 16, 1], [1, 3, 6], fin_m)                         # dorsal fin
    m.bone("jaw", [0, 5, 3], "root")
    m.cube("jaw", [-7.5, 0, -9], [15, 5, 12], hide)
    for x in (-7, -5, -3, -1, 1, 3, 5):
        m.cube("jaw", [x + 0.5, 5, -9], [1, 3, 1], tooth)                   # lower fangs
    m.bone("stalk1", [0, 16, -3], "body")
    m.cube("stalk1", [-0.5, 16, -3.5], [1, 6, 1], fin_m)
    m.bone("stalk2", [0, 22, -3], "stalk1")
    m.cube("stalk2", [-0.5, 21.5, -9.5], [1, 1, 7], fin_m)
    m.bone("lure", [0, 21, -9], "stalk2")
    m.cube("lure", [-0.5, 20, -9.5], [1, 2, 1], fin_m)
    m.cube("lure", [-1.5, 17, -10.5], [3, 3, 3], glow("#ffd84a"))
    for side, sx in (("fin_l", 1), ("fin_r", -1)):
        m.bone(side, [7 * sx, 8, 0], "body")
        m.cube(side, [7 if sx > 0 else -11, 7, -2], [4, 1, 5], fin_m)
    m.bone("tail", [0, 10, 8], "root")
    m.cube("tail", [-4, 7, 8], [8, 6, 5], hide)
    m.bone("tailfin", [0, 10, 13], "tail")
    m.cube("tailfin", [-0.5, 3, 13], [1, 14, 6], fin_m)

    def swim_bones(length, amp):
        return {
            "tail": {"rotation": wave(length, lambda p: [0, S(p) * 20 * amp, 0])},
            "tailfin": {"rotation": wave(length, lambda p: [0, S(p - 0.8) * 30 * amp, 0])},
            "fin_l": {"rotation": wave(length, lambda p: [0, 0, S(p) * 25 * amp])},
            "fin_r": {"rotation": wave(length, lambda p: [0, 0, -S(p) * 25 * amp])},
            "jaw": {"rotation": wave(length, lambda p: [6 + S(p) * 4 * amp, 0, 0])},
            "stalk1": {"rotation": wave(length, lambda p: [S(p - 0.5) * 8, 0, 0])},
            "lure": {"rotation": wave(length, lambda p: [S(p - 1.2) * 14, 0, S(p) * 6])},
            "body": {"rotation": wave(length, lambda p: [0, -S(p) * 4 * amp, 0])},
        }
    anim(m, "swim", 1.0, swim_bones(1.0, 1.0))
    anim(m, "idle", 2.4, swim_bones(2.4, 0.45))
    anim(m, "bite", 0.6, {
        "jaw": {"rotation": keys((0, [6, 0, 0]), (0.15, [48, 0, 0]), (0.3, [0, 0, 0]), (0.6, [6, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.15, [-12, 0, 0]), (0.3, [4, 0, 0]), (0.6, [0, 0, 0]))},
        "root": {"position": keys((0, [0, 0, 0]), (0.15, [0, 0, 2]), (0.3, [0, 0, -4]), (0.6, [0, 0, 0]))},
    }, loop=False)
    anim(m, "lure_flash", 1.0, {
        "lure": {"scale": keys((0, [1, 1, 1]), (0.25, [1.9, 1.9, 1.9]), (0.5, [1.4, 1.4, 1.4]), (0.75, [1.9, 1.9, 1.9]), (1.0, [1, 1, 1]))},
        "stalk1": {"rotation": keys((0, [0, 0, 0]), (0.3, [-18, 0, 0]), (1.0, [0, 0, 0]))},
    }, loop=False)
    return m


# siren() and coral_jelly() above are SUPERSEDED by tools/mobs/p2.py (built with mobkit, run via
# gen_mob_roster.py) and are kept only for reference - they are not in MOBS so running this script
# never overwrites the newer models.
MOBS = {"magma_brute": (magma_brute, (None,)), "mimic": (mimic, ("dormant", "awake")),
        "abyssal_angler": (abyssal_angler, (None,))}

if __name__ == "__main__":
    for name in sys.argv[1:] or MOBS:
        fn, variants = MOBS[name]
        fn().write(variants)
