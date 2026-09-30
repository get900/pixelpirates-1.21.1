"""Pixel Pirates - block textures + lang for THE TITAN'S CHEST (Abyssal Heart, boss 9/10). Re-runnable.

    python tools/gen_heart_assets.py        (pip install pillow numpy)

Uses the tileable-noise helpers of gen_overhaul_textures.py (16x16 blocks, lit from the top-left).
  living_flesh          6-frame pulsing meat (animated, .mcmeta written here)
  flesh_vein(_lit)      flesh with a branching vessel - dim, and flared on the heartbeat
  heart_valve           a shut valve: three muscular cusps meeting in a Y
  galvanic_pylon(_window|_charged|_top)  verdigris coil round a glass core: dark / green (strike now) / blue-white
"""
import json
import random
from pathlib import Path

import numpy as np
from PIL import Image

from gen_overhaul_textures import BLOCK, N, fbm, mix, palette_map, rgb, save, shade, stone, sprinkle, vnoise

ROOT = Path(__file__).resolve().parents[1]
LANG = ROOT / "src/main/resources/assets/pixelpirates/lang/en_us.json"

FLESH = ["#3a0a10", "#5a1018", "#761a20", "#8e2428", "#a83434", "#c04a44"]


def flesh(seed, bright=1.0, swell=0.0) -> Image.Image:
    """Meat: clustered noise, darker sinew streaks, a few wet highlights. `bright` scales the whole ramp."""
    v = fbm(seed, 4) * 0.8 + vnoise(seed + 5, 2, 8) * 0.2
    v = np.clip(v + swell * (v - 0.5), 0, 1)
    cols = [shade(rgb(c), bright) for c in FLESH]
    img = palette_map(v, cols)
    px = img.load()
    rs = random.Random(seed)
    for _ in range(3):                                            # sinew: dark curving strands
        x, y = rs.randrange(N), rs.randrange(N)
        for _ in range(rs.randint(5, 9)):
            px[x % N, y % N] = (*shade(rgb("#2a060a"), bright), 255)
            x += rs.choice((-1, 0, 1)); y += 1
    for _ in range(4):                                            # glistening
        x, y = rs.randrange(N), rs.randrange(N)
        px[x, y] = (*mix(shade(rgb("#e88a80"), bright), (255, 255, 255), 0.15), 255)
    return img


def vessel(img, seed, core, edge):
    """A branching vessel across the tile (wraps), core colour with a darker/brighter rim."""
    px = img.load()
    rs = random.Random(seed)
    c, e = rgb(core), rgb(edge)
    paths = [(rs.randrange(N), 0, 0)]
    cells = set()
    while paths:
        x, y, depth = paths.pop()
        for _ in range(N):
            cells.add((x % N, y % N))
            if depth < 2 and rs.random() < 0.12:
                paths.append((x, y, depth + 1))
            x += rs.choice((-1, 0, 0, 1)) if depth == 0 else rs.choice((-1, 1))
            y += 1
            if depth > 0 and rs.random() < 0.15:
                break
    for (x, y) in cells:
        for dx, dy in ((1, 0), (-1, 0)):
            q = ((x + dx) % N, y)
            if q not in cells:
                px[q] = (*e, 255)
    for (x, y) in cells:
        px[x, y] = (*c, 255)
    return img


def build():
    # living flesh: 6 frames, the swell rising and falling (one lub-dub per cycle)
    curve = [0.0, 0.55, 0.25, 0.45, 0.1, 0.0]
    strip = Image.new("RGBA", (N, N * len(curve)))
    for i, s in enumerate(curve):
        strip.paste(flesh(301, 0.92 + 0.22 * s, swell=0.35 * s), (0, i * N))
    save(strip, BLOCK, "living_flesh")
    (BLOCK / "living_flesh.png.mcmeta").write_bytes(json.dumps({"animation": {"frametime": 4, "interpolate": True}}, indent=2).encode())

    save(vessel(flesh(302, 0.85), 303, "#c8581e", "#5a1410"), BLOCK, "flesh_vein")
    lit = vessel(flesh(302, 1.15), 303, "#ffe08a", "#ff6a2a")
    sprinkle(lit, 304, "#fff4c8", 3)
    save(lit, BLOCK, "flesh_vein_lit")

    # heart valve: three cusps meeting in a Y at the centre, each a ridged muscle fan
    im = flesh(305, 0.8)
    px = im.load()
    cx = cy = 7.5
    import math
    for y in range(N):
        for x in range(N):
            a = math.atan2(y - cy, x - cx)
            d = math.hypot(x - cx, y - cy)
            seam = min(abs(((a - k * 2 * math.pi / 3 + math.pi / 2 + math.pi) % (2 * math.pi)) - math.pi) for k in range(3))
            if d < 8 and seam * d < 0.9:
                px[x, y] = (*rgb("#1e0408"), 255)                     # the closed seams
            elif d < 8 and int(d * 1.3) % 2 == 0:
                px[x, y] = (*shade(px[x, y][:3], 1.12), 255)   # muscle ridges
            if 6.8 <= d <= 8.2:
                px[x, y] = (*rgb("#4a0c12"), 255)                     # the fibrous ring
    save(im, BLOCK, "heart_valve")

    # galvanic pylon: vertical verdigris coil bands round a glass core
    cop = ["#1e4a40", "#2a6456", "#3a7e6a", "#4e987e"]
    for name, core, arc in (("galvanic_pylon", "#1a2a2a", None), ("galvanic_pylon_window", "#4aff6a", "#c8ffd0"),
                            ("galvanic_pylon_charged", "#8ad8ff", "#ffffff")):
        im = stone(311, cop, "#123028", 2, cell=4)
        px = im.load()
        for y in range(N):
            for x in range(N):
                if y % 3 == 0:
                    px[x, y] = (*rgb("#a8702a" if (x + y) % 5 else "#c8963c"), 255)      # copper windings
                if 5 <= x <= 10 and 2 <= y <= 13:
                    edge = x in (5, 10) or y in (2, 13)
                    px[x, y] = (*(rgb("#0c1414") if edge else rgb(core)), 255)
        if arc:
            rs = random.Random(312)
            for _ in range(2):
                x, y = rs.randrange(6, 10), 3
                while y < 13:
                    px[x, y] = (*rgb(arc), 255)
                    x = max(6, min(9, x + rs.choice((-1, 1)))); y += 1
        save(im, BLOCK, name)
    top = stone(313, cop, "#123028", 3, cell=4)
    px = top.load()
    for (x, y) in ((8, 2), (7, 3), (7, 4), (6, 5), (6, 6), (7, 6), (8, 6), (9, 6), (8, 7), (8, 8), (7, 9), (7, 10), (6, 11), (6, 12)):
        px[x, y] = (*rgb("#e0b84a"), 255)                             # a lightning rune
    for i in range(N):
        px[i, 0] = px[i, N - 1] = px[0, i] = px[N - 1, i] = (*rgb("#a8702a"), 255)
    save(top, BLOCK, "galvanic_pylon_top")


LANG_ENTRIES = {
    "block.pixelpirates.living_flesh": "Living Flesh",
    "block.pixelpirates.flesh_vein": "Flesh Vein",
    "block.pixelpirates.heart_valve": "Heart Valve",
    "block.pixelpirates.galvanic_pylon": "Galvanic Pylon",
    "entity.pixelpirates.abyssal_heart": "The Abyssal Heart",
    "entity.pixelpirates.rival_eye": "Eye of Thalassar",
    "entity.pixelpirates.heart_node": "Heart Node",
    "entity.pixelpirates.blood_clot": "Blood Clot",
    "entity.pixelpirates.embolism": "Embolism",
    "entity.pixelpirates.heart_phantasm": "Phantasm",
    "entity.pixelpirates.drowned_keeper": "The Last Keeper",
    "item.pixelpirates.rival_eye_spawn_egg": "Eye of Thalassar Spawn Egg",
    "item.pixelpirates.heart_node_spawn_egg": "Heart Node Spawn Egg",
    "item.pixelpirates.blood_clot_spawn_egg": "Blood Clot Spawn Egg",
    "item.pixelpirates.embolism_spawn_egg": "Embolism Spawn Egg",
    "item.pixelpirates.heart_phantasm_spawn_egg": "Phantasm Spawn Egg",
    "item.pixelpirates.drowned_keeper_spawn_egg": "Last Keeper Spawn Egg",
}


def merge_lang():
    d = json.loads(LANG.read_text(encoding="utf-8"))
    d.update(LANG_ENTRIES)
    LANG.write_text(json.dumps(d, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    build()
    merge_lang()
    print("heart block textures + lang written")
