"""THE ROULETTE TABLE (homestead extra, 2026-09-30): GeckoLib block model.

A dark-wood gaming table (2 blocks wide visually, 1 block of collision under the wheel end) with green baize, a betting
layout of red/black cells and the green zero, and at the -X end a brass-rimmed wheel bowl. Bones:
  wheel  - the spinning wheel head: 37 pockets round the rim in European order (see POCKETS; pocket i sits at angle
           i * 360/37 degrees, measured from -Z and turning clockwise seen from above), spun procedurally by
           RouletteRenderer (GeoModel.setCustomAnimations) so it stops on the server's result.
  ball   - the ivory ball, orbiting the wheel centre (also procedural).
Clips: idle (nothing moves - a still table).
"""
import math
from mobkit import A, Rig, noise, over, solid, speckle, anim

POCKETS = [0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22, 18, 29, 7, 28, 12,
           35, 3, 26]
RED = {1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36}
WOOD = ["#3a1e10", "#4a2814", "#2e180c"]
FELT = ["#1e5a2e", "#22643a", "#1a522a"]


def wheel_face(n, flip_rows):
    """n x n top-face art: rim of 37 pockets, a wooden cone and a brass hub. Box-UV 'up' faces have the model's BACK
    (+Z) at their top row, so `flip_rows` puts -Z at the bottom row."""
    pts = {}
    c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            dx, dz = x - c, (y - c) * (-1 if flip_rows else 1)       # dz: + = toward +Z
            r = math.hypot(dx, dz)
            if r > n / 2:
                pts[(x, y)] = "_"
                continue
            if r > n / 2 - 2.2:                                      # pocket ring
                ang = math.degrees(math.atan2(dx, -dz)) % 360       # 0 at -Z, clockwise from above (+X = 90)
                i = int(ang / (360 / 37) + 0.5) % 37
                num = POCKETS[i]
                pts[(x, y)] = "g" if num == 0 else ("r" if num in RED else "k")
            elif r > n / 2 - 3:
                pts[(x, y)] = "b"                                    # brass fret ring
            elif r < 1.2:
                pts[(x, y)] = "B"
            else:
                pts[(x, y)] = "w" if (int(math.degrees(math.atan2(dx, dz)) // 45) % 2) else "W"
    return A.at(n, n, pts, g="#1e8a3a", r="#b01e1e", k="#141414", b="#c8a040", B="#f0d070", w="#5a3018", W="#6a3a1e")


def layout_face(w, h):
    """The betting layout on the felt: a zero cell, 3 x 12 number cells in red/black, outside bets along the edge."""
    pts = {}
    for y in range(h):
        for x in range(w):
            if x in (0, w - 1) or y in (0, h - 1):
                pts[(x, y)] = "y"                                    # gold border line
    for col in range(12):
        for row in range(3):
            num = col * 3 + (3 - row)
            ch = "r" if num in RED else "k"
            x0, y0 = 17 + col, 2 + row * 2       # clear of the wheel bowl (art x < 16)
            if x0 < w - 1:
                pts[(x0, y0)] = ch
                pts[(x0, y0 + 1)] = ch if row < 2 else ch
    for y in range(2, 8):
        pts[(16, y)] = "g"                                           # the zero
    for x in range(17, w - 1, 3):
        pts[(x, h - 3)] = "y"
    return A.at(w, h, pts, y="#d0aa4a", r="#b01e1e", k="#141414", g="#1e8a3a")


def roulette_table(seed):
    m = Rig("roulette_table", 128, 128, seed)
    W_ = over(noise(WOOD), speckle("#1e0e06", 0.08))
    m.bone("root", [0, 0, 0])
    # the table: top 30 x 18 at y 12..14 (x -7..23: the wheel end sits over the block, the layout overhangs east)
    m.cube("root", [-7, 12, -9], [30, 2, 18], noise(FELT), art={"up": layout_face(30, 18)})
    m.cube("root", [-8, 11, -10], [32, 1, 20], W_)                         # the wooden rim under the felt
    m.cube("root", [-8, 14, -10], [32, 1, 1], W_)                          # raised edge rails
    m.cube("root", [-8, 14, 9], [32, 1, 1], W_)
    m.cube("root", [23, 14, -9], [1, 1, 18], W_)
    for x, z in ((-6, -8), (-6, 6), (20, -8), (20, 6)):                    # turned legs
        m.cube("root", [x, 0, z], [2, 11, 2], W_)
    m.cube("root", [-6, 3, -1], [28, 1, 2], W_)                            # stretcher
    # the wheel bowl on the -X end of the table (centre x 0, z 0), 16 across, brass rim
    m.cube("root", [-8, 15, -8], [16, 2, 16], over(noise(WOOD), speckle("#c8a040", 0.05)))
    m.cube("root", [-8.5, 17, -8.5], [17, 1, 1], solid("#c8a040"))
    m.cube("root", [-8.5, 17, 7.5], [17, 1, 1], solid("#c8a040"))
    m.cube("root", [-8.5, 17, -7.5], [1, 1, 15], solid("#c8a040"))
    m.cube("root", [7.5, 17, -7.5], [1, 1, 15], solid("#c8a040"))
    m.cube("root", [-0.5, 18, -8.5], [1, 2, 1], solid("#f0d070"))          # the marker pin at -Z (where the ball comes to rest)
    # the spinning wheel head
    m.bone("wheel", [0, 17, 0], "root")
    m.cube("wheel", [-7, 17, -7], [14, 1, 14], solid("#5a3018"), art={"up": wheel_face(14, True)})
    m.cube("wheel", [-1, 18, -1], [2, 2, 2], solid("#f0d070"))             # the hub / turret
    m.cube("wheel", [-3, 20, -0.5], [6, 1, 1], solid("#d0aa4a"))           # its cross arms, on top of the hub
    m.cube("wheel", [-0.5, 20, -3], [1, 1, 2.5], solid("#d0aa4a"))
    m.cube("wheel", [-0.5, 20, 0.5], [1, 1, 2.5], solid("#d0aa4a"))
    # the ball rides the pocket ring at radius 5.5 toward -Z (under the marker when its bone angle is 0)
    m.bone("ball", [0, 17, 0], "root")
    m.cube("ball", [-0.5, 18, -6], [1, 1, 1], solid("#f4f0e4"))
    anim(m, "idle", 1.0, {})
    return m


MOBS = {"roulette_table": roulette_table}
