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

ASSETS = TOOLS.parent / "src/main/resources/assets/pixelpirates"


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
