"""HIDEOUT set pieces (homestead #1, 2026-09-30).

  jolly_roger - THE HIDEOUT FLAG: a tarred mast on a stone foot with a brass finial, a crossbar yard, and a black
                flag with a white skull and crossbones in four hinged segments that wave (GeckoLib block, clip `wave`;
                `raise` one-shot runs the flag up the pole when it is claimed). The flag flies toward +X.
"""
from mobkit import A, Rig, noise, over, solid, speckle, anim, keys, wave, S

SKULL = [   # 20 x 12, the flag face ('w' bone, 'k' black cloth, 'e' eye socket) - symmetric
    "kkkkkkkkkkkkkkkkkkkk",
    "kkkkkkkwwwwwwkkkkkkk",
    "kkkkkkwwwwwwwwkkkkkk",
    "kkkkkkweewweewkkkkkk",
    "kkkkkkweewweewkkkkkk",
    "kkkkkkwwwkkwwwkkkkkk",
    "kkkkkkkwwwwwwkkkkkkk",
    "kkkkkkkwkwwkwkkkkkkk",
    "kkkwwkkkkkkkkkkwwkkk",
    "kkkkkwwwwkkwwwwkkkkk",
    "kkkkkkkkwwwwkkkkkkkk",
    "kkkwwkkkkkkkkkkwwkkk",
]


def jolly_roger(seed):
    m = Rig("jolly_roger", 64, 64, seed)
    TAR = over(noise(["#2a1e14", "#3a2a1a", "#241810"]), speckle("#1a120a", 0.1))
    STONE = noise(["#6a6a6a", "#7a7a7a", "#5a5a5a"])
    BRASS = noise(["#b8923a", "#d0aa4a", "#9a7a2a"])
    m.bone("root", [0, 0, 0])
    m.cube("root", [-4, 0, -4], [8, 3, 8], STONE)                        # stone foot
    m.cube("root", [-3, 3, -3], [6, 2, 6], STONE)
    m.cube("root", [-1, 5, -1], [2, 36, 2], TAR)                          # the mast
    m.cube("root", [-1.5, 41, -1.5], [3, 2, 3], BRASS)                    # finial
    m.cube("root", [-4, 39, -0.5], [3, 1, 1], TAR)                        # a stub of yard behind the flag
    # the flag: 4 hinged segments, 5 px each, hanging from y 27..39 along +X
    m.bone("flag", [1, 33, 0], "root")
    parent = "flag"
    for i in range(4):
        b = f"seg{i}"
        x0 = 1 + 5 * i
        m.bone(b, [x0, 33, 0], parent)
        pal = dict(k="#141214", w="#e8e4d8", e="#141214")
        cols = {(x, y): SKULL[y][i * 5 + x] for x in range(5) for y in range(12)}
        cols_r = {(x, y): SKULL[y][i * 5 + (4 - x)] for x in range(5) for y in range(12)}   # the back face reads mirrored
        m.cube(b, [x0, 27, -0.5], [5, 12, 1], solid("#141214"),
               art={"north": A.at(5, 12, cols_r, **pal), "south": A.at(5, 12, cols, **pal)})
        parent = b
    anim(m, "wave", 2.0, {f"seg{i}": {"rotation": wave(2.0, lambda q, i=i: [0, S(q - i * 0.9) * (8 + i * 5), S(q * 2 - i) * 2])} for i in range(4)})
    anim(m, "raise", 1.5, {"flag": {"position": keys((0, [0, -20, 0]), (1.2, [0, 1, 0]), (1.5, [0, 0, 0]))}}, loop=False)
    return m


MOBS = {"jolly_roger": jolly_roger}
