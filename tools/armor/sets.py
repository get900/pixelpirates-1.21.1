"""The twelve 3D armor sets, built from the concept sheets in D:\\Minecraft Modding\\armorrenders\\<set>.png.
Each builder returns an AR (tools/armor/kit.py). Geo coordinates: feet y 0, the wearer faces -Z, RIGHT side = -X.
Build the right-side piece with m.R(...) and it is mirrored onto the left bone.
Round 2 (2026-09-30, after comparing every set with its concept): structured surfaces (plates / scales / leather /
cloth / crack networks), stacked pauldrons, gauntlets, knee guards, boot cuffs, flaring layered capes, flat blades.
"""
from kit import (AR, A, noise, bands, over, lit, veins, spots, border, drips, speckle, SIDES,
                 plates, scales, leather, cloth, network, trim)

H, B, RA, LA, RL, RB = "armorHead", "armorBody", "armorRightArm", "armorLeftArm", "armorRightLeg", "armorRightBoot"


def pal(*c):
    return noise(list(c))


BRASS = pal("#7a5a1e", "#a8822e", "#d2a84a")
GOLD = pal("#a07818", "#d4a52a", "#f2cf5a")
IRON = plates(["#3e4248", "#555a62", "#6c727a"], ph=2, pw=3)
STEEL = plates(["#8a9098", "#a8aeb6", "#c8ced6"], ph=3, pw=4)
ROPE = over(pal("#6a4c24", "#8a6a36", "#a8854c"), spots("#4a3418", 0.15))
BONE = pal("#a89a7a", "#c8b894", "#e2d6b2")
CHAIN = over(pal("#34373c", "#4a4e54", "#62666c"), speckle("#8a8e94", 0.15))
PEARL = pal("#dcd6c8", "#eee8dc", "#ffffff")


def buckle(w, h, frame="G"):
    pts = {}
    bw = min(4, w - 2); bh = min(h, 3 if h < 4 else 4)
    x0 = (w - bw) // 2; y0 = (h - bh) // 2
    for x in range(bw):
        for y in range(bh):
            if x in (0, bw - 1) or y in (0, bh - 1):
                pts[(x0 + x, y0 + y)] = frame
    return pts


def belt(m, mat, buckle_col, y=11.5, h=2):
    m.cube(B, [-5.5, y, -3.5], [11, h, 7], mat, art={"north": A.at(11, h, buckle(11, h, "G"), G=buckle_col)})


def tricorn(m, felt, trim_col, y=30.5, feather=None, cockade=None):
    """A tricorn: crown + a wide brim whose three walls turn UP and lean outward, gold-edged; the face stays open."""
    edge = {"sides": A(["T" * 12, "F" * 12, "F" * 12, "F" * 12], F="#16161a", T=trim_col)}
    m.cube(H, [-4.5, y + 1, -4.5], [9, 4, 9], felt, art={"sides": A(["F" * 9] * 3 + ["T" * 9], F="#1a1a1e", T=trim_col)})
    m.cube(H, [-7, y, -7], [14, 1, 14], felt, art={"up": A.at(14, 14, {**{(i, 0): "T" for i in range(14)}, **{(i, 13): "T" for i in range(14)},
                                                                    **{(0, i): "T" for i in range(14)}, **{(13, i): "T" for i in range(14)}}, T=trim_col)})
    m.cube(H, [-6, y + 0.5, 6], [12, 4, 1], felt, art=edge, rot=[-22, 0, 0], pivot=[0, y + 0.5, 6.5])          # back wall
    m.cube(H, [-7, y + 0.5, -6], [1, 4, 12], felt, art=edge, rot=[0, 20, 22], pivot=[-6.5, y + 0.5, 0])        # right wall
    m.cube(H, [6, y + 0.5, -6], [1, 4, 12], felt, art=edge, rot=[0, -20, -22], pivot=[6.5, y + 0.5, 0])        # left wall
    if cockade:
        m.cube(H, [-1, y + 1.5, -5.25], [2, 2, 1], GOLD, art={"north": A(["GG", "RR"], G=trim_col, R=cockade)})
    if feather:                                                          # a big plume off the right side, sweeping back
        for k in range(8):
            m.blade(H, [-5.5 + (k % 2) * 0.6, y + 3, 2 - k * 0.9], 8 + (k % 3) * 2, 2, feather[k % len(feather)],
                    [18 + k * 7, 0, 30 + (k % 3) * 8])


# =====================================================================================
# BOSS SET I - POWDER-MONKEY'S BRIGANDINE
# =====================================================================================
def powder_monkey():
    m = AR("powder_monkey", 101)
    TARP = plates(["#1e1712", "#2b2119", "#3a2c20"], rivet="#c8a040", ph=3, pw=4)
    LEATH = leather(["#3a2618", "#4c3220", "#5e3e28"], stitch="#8a6a3e")
    SOOT = cloth(["#6e6a62", "#8a847a", "#a8a296"])
    RED = cloth(["#6a1414", "#8a1c1c", "#a82a24"], fold=3)
    TEAL = scales(["#1f4f4c", "#2e6f6a", "#3f8a82"], sw=2, sh=2)
    BREECH = over(cloth(["#2e2a28", "#3c3634", "#4a4440"]), spots("#3f6f68", 0.04, size=2), speckle("#5a3a24", 0.05))
    m.cube(H, [-5, 30, -5], [10, 3, 10], LEATH, art={"north": A(["RRRRRRRRRR", "RRRRRRRRRR", "LLLLLLLLLL"], R="#8a1c1c", L="#4c3220")})
    m.cube(H, [-5.5, 29.5, -5.5], [11, 2, 11], RED)
    m.hang(H, [-5.5, 22.5, 4.5], [11, 7, 1], TEAL, [0, 29.5, 5], kind="cape", art=m.rag([11, 7, 1], 2))
    m.R(H, [-5.5, 23.5, -2.5], [1, 6, 7], TEAL, art=m.rag([1, 6, 7], 2))
    m.cube(H, [-1.5, 28.5, 5.25], [3, 3, 2], RED)
    for k, (x, L, r) in enumerate([(-1.5, 7, [12, 0, 10]), (0.5, 6, [10, 0, -8]), (-0.5, 8, [15, 0, 0])]):
        m.hang(H, [x, 29 - L, 5.75], [1, L, 1], RED, [x, 29, 6], rot=r)
    m.chest(TARP)
    m.cube(B, [-4.5, 23.25, -3.5], [9, 2, 7], LEATH)
    m.cube(B, [-1, 13, -4.25], [2, 15, 1], LEATH, rot=[0, 0, -38], pivot=[0, 18.5, -3.75])
    for i, (x, y) in enumerate([(-3.2, 21.2), (-1.5, 19.3), (0.3, 17.4), (2.0, 15.5)]):
        m.cube(B, [x - 1, y - 1, -5.75], [2, 2, 2], over(BRASS, trim("#f0d070", top=True, bottom=False)), rot=[0, 0, -38], pivot=[x, y, -4.5])
    for k in range(3):
        m.cube(B, [1.1 + k * 1.05, 16.5, -5.25], [1, 4, 1], over(RED, trim("#d8c8a0", top=True, bottom=False)))
    m.cube(B, [2.3, 20.5, -5.25], [1, 1, 1], pal("#1a1a1a"))
    belt(m, LEATH, "#d2a84a")
    m.cube(B, [-4.75, 9, -3.75], [2, 2, 1], LEATH)
    m.cube(B, [4.25, 7.5, -2], [2, 3, 2], over(BRASS, spots("#1e1712", 0.1)), art={"north": A(["BB", "LL", "BB"], B="#7a5a1e", L="!ffd060")})
    m.cube(B, [4.75, 10.5, -1.5], [1, 1, 1], BRASS)
    m.cube(B, [-5.75, 10, -2], [2, 3, 3], RED)
    for k, (z, L) in enumerate([(-1.5, 9), (0, 11), (1.5, 8)]):
        m.hang(B, [-5.75, 10 - L, z - 0.5], [1, L, 1], RED, [-5.25, 10, z], art=m.rag([1, L, 1], 2), rot=[0, 0, 4 + k * 3])
    m.arms(bands(SOOT, (7, 13, over(LEATH, spots("#d2a84a", 0.05)))))
    m.pauldron([TARP, over(LEATH, trim("#b8902e"))], tiers=2, flare=6)
    m.gauntlet(over(LEATH, trim("#b8902e", top=True, bottom=False)), h=3, y0=11)
    m.legs(bands(BREECH, (2, 3, LEATH)))
    m.R(RL, [-5, 9, -3], [6, 1, 6], LEATH)
    m.knee(over(LEATH, spots("#d2a84a", 0.1)), y0=5.5, h=3)
    m.boots(bands(LEATH, (1, 2, over(LEATH, spots("#d2a84a", 0.35))), (4, 5, over(LEATH, spots("#d2a84a", 0.35)))), h=7)
    m.R(RB, [-5.25, 0, -3.75], [6, 2, 1], IRON)
    m.cuff(LEATH, y0=6.5, h=2)
    return m


# =====================================================================================
# BOSS SET II - FORGEGUARD PLATE
# =====================================================================================
def forgeguard():
    m = AR("forgeguard", 202)
    IRONP = over(plates(["#15151a", "#1e1e24", "#2a2a30"], rivet="#7a8a6a", ph=3, pw=4), network("#ff7a26", 0.06), network("#ffc060", 0.015))
    VT = "#3a6456"
    TRIMMED = over(IRONP, trim(VT, top=True, bottom=True))
    BANNER = over(cloth(["#4a1010", "#5e1616", "#721c1c"]), speckle("#3a0c0c", 0.08))
    visor = A(["..........", "..........", "..........", "..........", ".VVVVVVVV.", ".GGGGGGGG.",
               "....GG....", "....GG....", "....GG....", ".........."], V=VT, G="!8ff6ff")
    m.helm(IRONP, art={"north": visor})
    m.cube(H, [-5.5, 29.5, -5.5], [11, 1, 11], pal("#2f5a4c", "#3e6e60", VT))
    m.cube(H, [-0.5, 33, -5.5], [1, 1, 11], pal("#2f5a4c", "#3e6e60", VT))
    m.cube(H, [-0.5, 34, -4], [1, 1, 8], over(IRONP, spots("#ff7a26", 0.3, glow=True)))
    anchor = A.at(10, 14, {**{(4, y): "V" for y in range(2, 10)}, **{(5, y): "V" for y in range(2, 10)}, (3, 3): "V", (6, 3): "V",
                           (2, 8): "V", (3, 9): "V", (6, 9): "V", (7, 8): "V", (2, 7): "V", (7, 7): "V"}, V=VT)
    m.chest(IRONP, art={"north": anchor})
    m.cube(B, [-4, 23.5, -3.75], [8, 2, 8], TRIMMED)
    m.chain(B, [-4.5, 22.5, -4], [0, 19, -4.25], GOLD); m.chain(B, [0, 19, -4.25], [4.5, 22.5, -4], GOLD)
    belt(m, leather(["#2a1c12", "#3a2618"]), VT)
    m.cube(B, [-2.5, 2.5, -4], [5, 9, 1], BANNER, art={**m.rag([5, 9, 1], 3), "north": A.at(5, 9, {(2, 2): "C", (2, 3): "C", (2, 4): "C", (1, 3): "C", (3, 3): "C"}, C="#d8c8a8")})
    m.cube(B, [-5.5, 1, 3.5], [11, 22, 1], BANNER, rot=[7, 0, 0], pivot=[0, 23, 3.5],
           art={**m.rag([11, 22, 1], 5), "south": A.at(11, 22, {**{(5, y): "C" for y in range(3, 12)}, **{(x, 6): "C" for x in range(2, 9)},
                                                               (4, 4): "C", (6, 4): "C", (3, 10): "C", (7, 10): "C"}, C="#d8c8a8")})
    m.arms(IRONP)
    m.pauldron([TRIMMED, IRONP, IRONP], tiers=3, flare=8, width=8, depth=9)
    m.gauntlet(IRONP, h=4)
    m.legs(IRONP)
    m.R(RL, [-5, 9.5, -3.25], [6, 3, 1], IRONP)
    m.knee(TRIMMED, y0=5.5, h=3)
    m.boots(IRONP, h=7)
    m.R(RB, [-5.5, 0, -4.5], [7, 3, 2], IRONP)
    m.cuff(TRIMMED, y0=6.5, h=2)
    return m


# =====================================================================================
# BOSS SET III - TIDECOURT REGALIA
# =====================================================================================
def tidecourt():
    m = AR("tidecourt", 303)
    SCALE = scales(["#0c2a30", "#15474f", "#1f6264", "#2e7a70"])
    DARK = scales(["#081a20", "#0f2f36", "#16434a", "#1f5a5e"])
    CORAL = pal("#8a2424", "#b03a30", "#d05a44")
    TOOTH = over(pal("#c8b894", "#e2d6b2", "#f2ead0"), trim("#fff8e8", top=True, bottom=False))
    KELP = over(cloth(["#12362c", "#1a4a3a", "#256050"]), speckle("#2f7a60", 0.08))
    GT = "#d4a52a"
    visor = A(["..........", "..........", "..........", ".GG....GG.", ".GG....GG.", ".GG....GG.",
               "....GG....", "..........", "..........", ".........."], G="!4ff0d8")
    m.helm(SCALE, art={"north": visor})
    m.cube(H, [-5.5, 31, -5.5], [11, 2, 11], over(GOLD, spots("#3affd8", 0.05, glow=True)))
    m.cube(H, [-1, 32, -6.25], [2, 4, 1], lit("#3affd8"))
    for x in (-4.5, -2.5, 1.5, 3.5):
        m.blade(H, [x + 0.5, 33, -5], 4, 1, TOOTH, [-8, 0, 0]); m.blade(H, [x + 0.5, 33, 5], 4, 1, TOOTH, [8, 0, 0])
    for z in (-3, 0, 3):
        m.blade(H, [-5, 33, z + 0.5], 4, 1, TOOTH, [0, 0, 8]); m.blade(H, [5, 33, z + 0.5], 4, 1, TOOTH, [0, 0, -8])
    for (x, z, r) in [(-4, -2, [0, 0, 18]), (3.5, -1, [0, 0, -18]), (-3, 3, [-15, 0, 10]), (2.5, 3.5, [-15, 0, -12]), (0, 4, [-20, 0, 0])]:
        m.spike(H, [x, 33, z], 6, CORAL, r)
        m.spike(H, [x, 36, z], 3, CORAL, [0, 0, 55 if x <= 0 else -55])
    m.chest(SCALE)
    m.chain(B, [-4.5, 23, -3.6], [0, 19.5, -3.9], GOLD); m.chain(B, [0, 19.5, -3.9], [4.5, 23, -3.6], GOLD)
    for (x, y) in [(-3, 21.8), (-1.5, 20.6), (1.5, 20.6), (3, 21.8)]:
        m.cube(B, [x - 0.5, y - 0.5, -4.25], [1, 1, 1], PEARL)
    m.cube(B, [-1, 17.5, -4.25], [2, 2, 1], PEARL)
    belt(m, GOLD, "#f4f0e6")
    for x in (-4, -2, 2, 4):
        m.blade(B, [x, 11.5, -3.75], 3, 1, TOOTH, [180, 0, 0])
    m.flare_cape(KELP, width=12, layers=2, depth=8)
    m.cube(B, [-4, 4, -3.75], [8, 7, 1], KELP, art=m.rag([8, 7, 1], 4))
    m.arms(bands(DARK, (6, 8, GOLD)))
    m.pauldron([over(SCALE, trim(GT)), over(DARK, trim(GT))], tiers=2, flare=8)
    for k, z in enumerate((-3, -1, 1, 3)):
        m.blade(RA, [-9.5, 24.5, z], 5 + (k % 2), 1, TOOTH, [0, 0, 35 + k * 6])
        m.blade(LA, [9.5, 24.5, z], 5 + (k % 2), 1, TOOTH, [0, 0, -(35 + k * 6)])
    for z, L in ((-2, 5), (1.5, 6)):
        m.spike(RA, [-10, 24.5, z], L, CORAL, [0, 0, 20]); m.spike(LA, [10, 24.5, z], L, CORAL, [0, 0, -20])
        m.spike(RA, [-11, 27, z], 2, CORAL, [0, 0, 60]); m.spike(LA, [11, 27, z], 2, CORAL, [0, 0, -60])
    m.gauntlet(over(DARK, trim(GT)), h=3)
    m.legs(bands(DARK, (0, 1, GOLD), (5, 6, GOLD)))
    m.knee(GOLD, y0=6, h=3)
    m.R(RL, [-3.5, 7, -3.75], [2, 1, 1], lit("#3affd8"))
    m.boots(bands(DARK, (0, 1, GOLD), (3, 4, GOLD)))
    m.R(RB, [-5.25, 0, -3.75], [6, 2, 1], GOLD)
    m.spike(RB, [-5, 5, 1], 4, CORAL, [0, 0, 25]); m.spike("armorLeftBoot", [5, 5, 1], 4, CORAL, [0, 0, -25])
    return m


# =====================================================================================
# BOSS SET IV - GALLOWBREAKER HARNESS
# =====================================================================================
def gallowbreaker():
    m = AR("gallowbreaker", 404)
    PLATE = over(plates(["#141218", "#1e1c24", "#2a2832"], rivet="#6c727a", ph=3, pw=4), drips("#3a1f4a", 0.3, maxlen=5))
    HOOD = over(cloth(["#1c1424", "#2a1c36", "#3a2648", "#4e2e62"]), speckle("#5e3a78", 0.06))
    BEAK = over(pal("#6a6660", "#8a857c", "#a8a296"), speckle("#4a4640", 0.1))
    MAIL = over(pal("#2a2c30", "#3a3c42"), spots("#5a5e66", 0.3))
    SUCKER = pal("#b8a88a", "#d2c4a4")
    m.cube(H, [-5.5, 22.5, -5.5], [11, 11, 11], HOOD, art={**m.rag([11, 11, 11], 3, keep_top=7),
        "north": A(["HHHHHHHHHHH", "HHHHHHHHHHH", "HH_______HH", "H_________H", "H_________H", "H_________H",
                    "H_________H", "H_________H", "H_________H", "H_________H", "HH_______HH"], H="#2a1c36")})
    m.cube(H, [-6, 30.5, -6], [12, 3, 12], HOOD, art=m.rag([12, 3, 12], 2))
    m.cube(H, [-4, 24, -5], [8, 7, 1], BEAK, art={"north": A(["BBBBBBBB", "B__BB__B", "B__BB__B", "BBBBBBBB", "BBB..BBB", "BBBBBBBB", "BBBBBBBB"], B="#8a857c")})
    m.cube(H, [-1, 24, -10], [2, 3, 5], BEAK, rot=[18, 0, 0], pivot=[0, 27, -5])
    m.cube(H, [-0.5, 22.5, -12], [1, 2, 3], BEAK, rot=[45, 0, 0], pivot=[0, 26, -9])
    m.chest(PLATE, art={"north": A.at(10, 14, {(x, y): "M" for x in range(2, 8) for y in range(6, 10)}, M="#3a3c42")})
    m.cube(B, [-6, 20.5, -3.75], [12, 4, 8], HOOD, art=m.rag([12, 4, 8], 2))
    m.chain(B, [-4.5, 22, -4.25], [3.5, 13, -4.25], CHAIN); m.chain(B, [4.5, 22, -4.25], [-3.5, 13, -4.25], CHAIN)
    m.ring(B, [0, 17.5, -4.5], 2, IRON, axis="z")
    belt(m, leather(["#2a1c14", "#3a2618"]), "#6c727a")
    m.flare_cape(HOOD, width=12, layers=2, depth=8)
    m.cube(B, [-4.5, 3, -3.75], [9, 8, 1], HOOD, art=m.rag([9, 8, 1], 4))
    m.arms(bands(PLATE, (4, 6, IRON)))
    m.pauldron([over(HOOD, spots("#d2c4a4", 0.12)), PLATE], tiers=2, flare=6)
    for (x, y, z) in [(-10.5, 23, -2.5), (-10.5, 22, 0.5), (-9.5, 25, -1), (-10, 24.5, 2.5), (-11, 21, -0.5)]:
        m.R(RA, [x, y, z], [2, 2, 2], SUCKER, art={"sides": A(["SS", "S_"], S="#d2c4a4"), "up": A(["S_", "SS"], S="#d2c4a4")})
    m.gauntlet(IRON, h=3)
    m.chain(RA, [-7, 11, 0], [-7.5, 5.5, -0.5], CHAIN); m.chain(LA, [7, 11, 0], [7.5, 5.5, -0.5], CHAIN)
    m.ring(RA, [-7.5, 3.5, -0.5], 2, IRON, axis="z"); m.ring(LA, [7.5, 3.5, -0.5], 2, IRON, axis="z")
    m.legs(bands(MAIL, (0, 2, PLATE), (6, 7, IRON)))
    m.R(RL, [-4.75, 5.5, -3], [5, 6, 1], HOOD, art=m.rag([5, 6, 1], 3))
    m.R(RL, [-4.75, 6.5, 2.5], [5, 5, 1], HOOD, art=m.rag([5, 5, 1], 3))
    m.knee(IRON, y0=5.5, h=2)
    m.boots(bands(PLATE, (1, 2, IRON), (4, 5, IRON)))
    m.R(RB, [-5.5, 0, -3.5], [7, 2, 1], IRON)
    m.cuff(PLATE, y0=6, h=1)
    return m


# =====================================================================================
# BOSS SET V - MANTLE OF THALASSAR
# =====================================================================================
def thalassar():
    m = AR("thalassar", 505)
    HIDE = over(scales(["#06201c", "#0a2e26", "#0e3c32", "#134a3e"]), network("#ffcc3a", 0.035), spots("#5af0ff", 0.01, glow=True))
    DARK = scales(["#041612", "#08271f", "#0d3a2e", "#124836"])
    FIN = over(scales(["#0e3a30", "#15463a", "#1f5a48", "#2a6a56"], sw=2, sh=2), trim("#e8b830", top=True, bottom=False, sides=False), veins("#ffd24a", 0.02, glow=True))
    GOLDG = lit("#ffcc3a")
    eye = A(["...EEEE...", "..EOOOOE..", ".EOOPPOOE.", "..EOOOOE..", "...EEEE...", "..........",
             ".CC....CC.", "..........", "..........", ".........."], E="!ffcc3a", O="!fff0a0", P="#1a0a00", C="!5af0ff")
    m.helm(HIDE, art={"north": eye})
    m.blade(H, [0, 33, -1], 9, 5, FIN, [-18, 0, 0], thin="x")
    for k, (z, L, a) in enumerate([(-3, 10, 25), (-0.5, 12, 35), (2, 10, 45), (4, 8, 55)]):
        m.blade(H, [-4, 32, z], L, 3, FIN, [8 + k * 6, 0, a]); m.blade(H, [4, 32, z], L, 3, FIN, [8 + k * 6, 0, -a])
    heart = {(4, y): "G" for y in range(0, 14)}; heart.update({(5, y): "G" for y in range(0, 14)})
    heart.update({(3, 3): "G", (2, 2): "G", (1, 1): "G", (6, 3): "G", (7, 2): "G", (8, 1): "G",
                  (3, 8): "G", (2, 9): "G", (6, 8): "G", (7, 9): "G", (2, 5): "G", (7, 5): "G"})
    m.chest(HIDE, art={"north": A.at(10, 14, heart, G="!ffcc3a")})
    belt(m, DARK, "!ffcc3a")
    m.cube(B, [-1, 11.25, -4], [2, 2, 1], lit("#ffcc3a"))
    m.cube(B, [-6, 0, 3.5], [12, 23, 1], over(DARK, network("#2f7a5e", 0.04, glow=False)), rot=[8, 0, 0], pivot=[0, 23, 3.5],
           art={**m.rag([12, 23, 1], 7), "south": A.at(12, 23, {**{(5, y): "G" for y in range(0, 19)}, **{(6, y): "G" for y in range(0, 19)}}, G="!ffcc3a")})
    for x in (-5, -2.5, 2.5, 5):
        m.blade(B, [x, 10, 5], 7, 2, FIN, [-160, 0, -12 if x < 0 else 12])
    m.cube(B, [-4.5, 3, -3.75], [9, 8, 1], DARK, art=m.rag([9, 8, 1], 4))
    m.arms(over(DARK, spots("#5af0ff", 0.03, glow=True)))
    m.pauldron([over(HIDE, trim("#ffcc3a")), HIDE], tiers=2, flare=8)
    for k, (z, L) in enumerate([(-3, 10), (-1, 13), (1, 12), (3, 9)]):
        m.blade(RA, [-9.5, 24.5, z], L, 3, FIN, [0, 0, 22 + k * 11])
        m.blade(LA, [9.5, 24.5, z], L, 3, FIN, [0, 0, -(22 + k * 11)])
    m.gauntlet(over(HIDE, trim("#ffcc3a")), h=3)
    m.blade(RA, [-9.5, 13, 0], 5, 2, FIN, [0, 0, 65]); m.blade(LA, [9.5, 13, 0], 5, 2, FIN, [0, 0, -65])
    m.legs(bands(DARK, (0, 1, GOLDG)))
    m.knee(over(HIDE, trim("#ffcc3a")), y0=6, h=3)
    m.blade(RL, [-4.5, 8, 0], 6, 2, FIN, [0, 0, 40]); m.blade("armorLeftLeg", [4.5, 8, 0], 6, 2, FIN, [0, 0, -40])
    m.boots(bands(HIDE, (0, 1, GOLDG), (4, 5, GOLDG)))
    m.R(RB, [-5.25, 0, -3.75], [6, 2, 1], GOLD)
    m.blade(RB, [-5, 3, 1], 5, 2, FIN, [20, 0, 50]); m.blade("armorLeftBoot", [5, 3, 1], 5, 2, FIN, [20, 0, -50])
    return m


# =====================================================================================
# PIRATE ARMOR (starter)
# =====================================================================================
def pirate_armor():
    m = AR("pirate_armor", 606)
    FELT = pal("#141418", "#1c1c22", "#26262c")
    RED = cloth(["#7a1616", "#9a2020", "#b02a26"], fold=3)
    WAIST = leather(["#3a2416", "#4c301c", "#5a3a22"], stitch="#7a5a36")
    SHIRT = cloth(["#cfc6b4", "#ddd6c6", "#ebe6da"])
    STRIPE = bands(SHIRT, *[(y, y + 2, RED) for y in range(0, 13, 4)])
    tricorn(m, FELT, "#c89a3a")
    m.cube(H, [-5, 27.5, -5], [10, 3, 10], RED, art={"north": A(["R" * 10, "R" * 10, "_" * 10], R="#9a2020")})
    m.cube(H, [-1.5, 26, 5.25], [3, 3, 1], RED)
    for k, (x, L) in enumerate([(-1.5, 6), (-0.25, 8), (1, 5)]):
        m.hang(H, [x, 27 - L, 5.5], [1, L, 1], RED, [x, 27, 5.5], rot=[10, 0, (k - 1) * 8])
    m.chest(WAIST, art={"north": A.at(10, 14, {**{(x, y): "S" for x in (4, 5) for y in range(2, 14)}, (4, 0): "S", (5, 0): "S",
                                              (3, 1): "S", (6, 1): "S", (4, 1): "S", (5, 1): "S"}, S="#e6dfd0")})
    m.cube(B, [-1, 12.5, -4], [2, 15, 1], leather(["#4a2c18", "#5a3620"]), rot=[0, 0, 35], pivot=[0, 19, -3.5])
    m.cube(B, [-1, 12.5, -4.25], [2, 15, 1], leather(["#4a2c18", "#5a3620"]), rot=[0, 0, -35], pivot=[0, 19, -3.75])
    belt(m, leather(["#3a2416", "#4a2e1c"]), "#d2a84a", h=3, y=11)
    for k, (z, L) in enumerate([(-1.5, 8), (0, 10), (1.5, 7)]):
        m.hang(B, [4.75, 11 - L, z - 0.5], [1, L, 1], RED, [5.25, 11, z], art=m.rag([1, L, 1], 2), rot=[0, 0, -(4 + k * 3)])
    m.cube(B, [-5.25, 6.5, -2], [2, 3, 2], BRASS, art={"north": A(["BB", "LL", "BB"], B="#7a5a1e", L="!ffd060")})
    m.arms(STRIPE)
    m.gauntlet(over(WAIST, spots("#d2a84a", 0.12)), h=3)
    m.legs(bands(over(cloth(["#222226", "#2e2e34", "#3a3a40"]), spots("#5a4a3e", 0.05, size=2)), (1, 2, WAIST)))
    m.boots(bands(WAIST, (3, 4, over(leather(["#2a1a10"]), spots("#d2a84a", 0.3)))), h=6)
    m.cuff(over(WAIST, trim("#b8902e", top=True, bottom=False)), y0=5.5, h=3)
    return m


# =====================================================================================
# CASTAWAY (ring 1)
# =====================================================================================
def castaway():
    m = AR("castaway", 707)
    PALM = over(pal("#3a5a1e", "#4e7426", "#66902e"), speckle("#2a4214", 0.1))
    STRAW = over(pal("#6a5a2a", "#8a7636", "#a89048"), spots("#5a4a20", 0.15))
    SAIL = over(cloth(["#c8bca0", "#d8ceb4", "#e8e0cc"]), spots("#9a7a4a", 0.05, size=2), speckle("#6a8a4a", 0.03))
    WOOD = plates(["#6a6258", "#8a8074", "#a89e90"], ph=2, pw=6)
    GREEN = cloth(["#1e3a24", "#284a2e", "#325a38"])
    m.cube(H, [-4.5, 31, -4.5], [9, 4, 9], STRAW)
    m.cube(H, [-5, 31.5, -5], [10, 1, 10], ROPE)
    m.cube(H, [-8, 30.5, -8], [16, 1, 16], PALM)
    for side in range(4):
        for i in range(8):
            x = -8 + i * 2 + 0.5
            L = 2 + (i * 7 + side * 3) % 3
            if side == 0: m.hang(H, [x, 30.5 - L, -8.25], [1, L, 1], PALM, [x + 0.5, 30.5, -7.75], kind="fringe")
            if side == 1: m.hang(H, [x, 30.5 - L, 7.25], [1, L, 1], PALM, [x + 0.5, 30.5, 7.75], kind="fringe")
            if side == 2: m.hang(H, [-8.25, 30.5 - L, x], [1, L, 1], PALM, [-7.75, 30.5, x + 0.5], kind="fringe")
            if side == 3: m.hang(H, [7.25, 30.5 - L, x], [1, L, 1], PALM, [7.75, 30.5, x + 0.5], kind="fringe")
    m.cube(H, [-5, 23, -5.25], [10, 4, 1], GREEN)
    m.R(H, [-5, 23, -5], [1, 4, 10], GREEN)
    m.chest(SAIL)
    m.cube(B, [-5.5, 20.5, -3.5], [11, 4, 7], GREEN, art=m.rag([11, 4, 7], 2))
    m.cube(B, [-1, 13, -3.75], [1, 12, 1], ROPE, rot=[0, 0, 30], pivot=[0, 19, -3.5])
    m.cube(B, [0, 13, -3.75], [1, 12, 1], ROPE, rot=[0, 0, -30], pivot=[0, 19, -3.5])
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], ROPE)
    m.hang(B, [-5, 3.5, -3.5], [10, 8, 1], SAIL, [0, 11.5, -3], kind="skirt", art=m.rag([10, 8, 1], 4))
    m.hang(B, [-5, 3.5, 2.75], [10, 8, 1], SAIL, [0, 11.5, 3.25], kind="skirt", art=m.rag([10, 8, 1], 4))
    m.arms(bands(SAIL, (7, 8, ROPE), (9, 10, ROPE), (11, 12, ROPE)))
    m.pauldron(WOOD, tiers=3, flare=10, width=8, depth=8)
    m.gauntlet(ROPE, h=2, y0=13)
    m.gauntlet(ROPE, h=1, y0=11)
    m.legs(over(cloth(["#3a2c1c", "#4a3a26", "#2e4a2a"]), spots("#5a7a3a", 0.06, size=2)))
    m.knee(WOOD, y0=5.5, h=4)
    m.R(RL, [-5, 7, -3], [6, 1, 6], ROPE)
    m.R(RB, [-5, 0, -4], [6, 1, 7], pal("#5a3a1e", "#6a4a26"))
    m.R(RB, [-5, 1, -3], [6, 1, 6], ROPE); m.R(RB, [-5, 3, -3], [6, 1, 6], ROPE)
    return m


# =====================================================================================
# NAVY OFFICER (ring 2)
# =====================================================================================
def navy_officer():
    m = AR("navy_officer", 808)
    NAVY = over(cloth(["#10162e", "#18203e", "#212a4e"]), speckle("#2a3460", 0.04))
    WHITE = cloth(["#c8c4bc", "#dcd8d0", "#ece8e0"])
    T = "#d2a84a"
    m.cube(H, [-8, 30.5, -2.5], [16, 4, 5], NAVY, art={"sides": A(["TTTTTTTTTTTTTTTT", "NTNNNNNNNNNNNNTN", "NNNNNNNNNNNNNNNN", "TTTTTTTTTTTTTTTT"], T=T, N="#18203e")})
    m.cube(H, [-5.5, 34.5, -2], [11, 2, 4], NAVY, art={"sides": A(["TTTTTTTTTTT", "NNNNNNNNNNN"], T=T, N="#18203e")})
    m.cube(H, [-2, 36.5, -1.5], [4, 1, 3], NAVY, art={"sides": A(["TTTT"], T=T)})
    m.cube(H, [-1, 32, -3], [2, 2, 1], GOLD, art={"north": A(["GG", "GG"], G="!3a7aff")})
    for k in range(5):
        m.blade(H, [4.5 + k * 0.4, 34, 1.2 - k * 0.9], 5 + (k % 2), 2, WHITE, [-15 - k * 8, 0, -30])
    m.chest(NAVY, art={"north": A.at(10, 14, {**{(x, y): "S" for x in range(3, 7) for y in range(3, 12)},
                                              **{(2, y): "G" for y in range(2, 13, 2)}, **{(7, y): "G" for y in range(2, 13, 2)},
                                              (4, 0): "W", (5, 0): "W", (4, 1): "W", (5, 1): "W"}, S="#a8aeb6", G=T, W="#ece8e0")})
    m.cube(B, [-1, 13, -4], [2, 14, 1], leather(["#4a2c18", "#5a3620"]), rot=[0, 0, -35], pivot=[0, 19, -3.5])
    belt(m, leather(["#3a2416", "#4a2e1c"]), T)
    m.cube(B, [-5.5, 1, 3.25], [11, 11, 1], over(NAVY, trim(T, top=False, bottom=True, sides=True)), rot=[6, 0, 0], pivot=[0, 12, 3.25],
           art={"south": A.at(11, 11, {(5, y): "_" for y in range(5, 11)})})
    for side in (-1, 1):
        x = -5.75 if side < 0 else 3.75
        m.cube(B, [x, 1, -3.5], [2, 10, 1], over(NAVY, trim(T, top=False, bottom=True, sides=True)),
               rot=[-5, 0, side * -8], pivot=[x + 1, 11, -3.5])
    m.arms(bands(NAVY, (9, 13, over(WHITE, spots(T, 0.1)))))
    m.R(RA, [-10, 23.5, -3.5], [7, 1, 7], GOLD)
    m.strands(RA, -10, -3, 23.5, -3.75, GOLD, [3, 4, 3, 4, 3, 4, 3])
    m.strands(RA, -10, -3, 23.5, 3.75, GOLD, [3, 4, 3, 4, 3, 4, 3])
    m.R(RA, [-10.25, 20, -3.5], [1, 4, 7], GOLD)
    m.legs(bands(WHITE, (0, 1, leather(["#3a2416"]))))
    m.knee(STEEL, y0=5.5, h=3)
    m.boots(bands(leather(["#0e0e12", "#16161c", "#1e1e26"]), (2, 3, STEEL), (4, 5, over(pal("#0e0e12"), spots(T, 0.3)))), h=7)
    m.cuff(over(leather(["#0e0e12", "#16161c"]), trim(T, top=True, bottom=False)), y0=6.5, h=2)
    return m


# =====================================================================================
# CORSAIR (ring 3)
# =====================================================================================
def corsair():
    m = AR("corsair", 909)
    CRIM = over(cloth(["#4a0c10", "#621218", "#7a1a1e"]), speckle("#8a2226", 0.05))
    BLACK = leather(["#141014", "#1e181c", "#2a2226"], stitch="#3a2a2a")
    WHITE = cloth(["#c8c0b0", "#dcd4c4", "#ece6d8"])
    SASH = cloth(["#7a1418", "#9a1c20", "#b02428"], fold=3)
    T = "#e0b040"
    tricorn(m, pal("#141014", "#1e181c"), T, feather=[pal("#8a1818", "#b02424"), pal("#d8d0c0", "#eee8dc"), pal("#9a1c1c", "#c02a2a")],
            cockade="!d02020")
    m.cube(H, [-5, 27.5, -5], [10, 3, 10], SASH, art={"north": A(["R" * 10, "R" * 10, "_" * 10], R="#8a1c1c")})
    for k, (x, L) in enumerate([(-1.5, 7), (-0.25, 9), (1, 6)]):
        m.cube(H, [x, 28 - L, 5.5], [1, L, 1], SASH, rot=[10, 0, (k - 1) * 8], pivot=[x, 28, 5.5])
    m.chest(CRIM, art={"north": A.at(10, 14, {**{(x, y): "W" for x in (4, 5) for y in range(0, 5)}, (3, 0): "W", (6, 0): "W",
                                              **{(3, y): "G" for y in range(5, 14)}, **{(6, y): "G" for y in range(5, 14)}}, W="#dcd4c4", G=T)})
    m.cube(B, [-1, 13, -4], [2, 14, 1], BLACK, rot=[0, 0, 35], pivot=[0, 19, -3.5])
    m.chain(B, [-4, 16, -4], [0.5, 13.5, -4.25], GOLD); m.chain(B, [0.5, 13.5, -4.25], [4.5, 16.5, -4], GOLD)
    for (x, y) in ((-2, 14.5), (2.5, 14.5)):
        m.cube(B, [x - 0.5, y - 1, -4.5], [1, 1, 1], GOLD)
    m.cube(B, [0, 12.5, -4.5], [2, 2, 1], GOLD, art={"north": A(["GG", "RG"], G=T, R="!d02020")})
    m.cube(B, [-5.5, 11, -3.5], [11, 3, 7], SASH)
    m.cube(B, [-6, 10, -2], [2, 3, 3], SASH)
    for k, (z, L) in enumerate([(-1.5, 9), (0, 11), (1.5, 8)]):
        m.cube(B, [-6, 10 - L, z - 0.5], [1, L, 1], SASH, art=m.rag([1, L, 1], 2), rot=[0, 0, 4 + k * 3], pivot=[-5.5, 10, z])
    m.cube(B, [-6, 1, 3.25], [12, 10, 1], over(CRIM, trim(T, top=False, bottom=True, sides=True)),
           art=m.rag([12, 10, 1], 4), rot=[7, 0, 0], pivot=[0, 11, 3.25])
    for side in (-1, 1):
        x = -6 if side < 0 else 4
        m.cube(B, [x, 1.5, -3.5], [2, 9, 1], over(CRIM, trim(T, top=False, bottom=True, sides=True)), art=m.rag([2, 9, 1], 3),
               rot=[-5, 0, side * -10], pivot=[x + 1, 10.5, -3.5])
    m.arms(bands(CRIM, (6, 8, WHITE)))
    m.gauntlet(over(BLACK, spots(T, 0.14)), h=4)
    m.R(RA, [-10, 23.5, -3.5], [7, 1, 7], GOLD)
    m.strands(RA, -10, -3, 23.5, -3.75, GOLD, [3, 4, 3, 4, 3, 4, 3])
    m.strands(RA, -10, -3, 23.5, 3.75, GOLD, [3, 4, 3, 4, 3, 4, 3])
    m.legs(bands(BLACK, (3, 4, over(BLACK, spots(T, 0.3)))))
    m.boots(bands(BLACK, (1, 2, over(BLACK, spots(T, 0.35))), (4, 5, over(BLACK, spots(T, 0.35)))), h=7)
    m.cuff(over(BLACK, trim(T, top=True, bottom=False)), y0=6.5, h=2)
    return m


# =====================================================================================
# ASHEN (volcanic)
# =====================================================================================
def ashen():
    m = AR("ashen", 1010)
    BASALT = over(plates(["#1c1a1c", "#2a2628", "#3a3436", "#4a4446"], ph=3, pw=5), network("#ff7a1e", 0.06), network("#ffb040", 0.015))
    OBS = over(pal("#120e18", "#1e1628", "#2a2036"), network("#ff6a1a", 0.08), trim("#3a2a4a", top=False, bottom=False, sides=True))
    ASH = over(cloth(["#7a7064", "#948a7e", "#aca296"]), speckle("#4a4238", 0.08))
    visor = A(["..........", "..........", "..........", ".GGGGGGGG.", "....GG....", "....GG....",
               "....GG....", "....GG....", "..........", ".........."], G="!ff9a2a")
    m.helm(BASALT, art={"north": visor})
    for (x, z, r, L, w) in [(-3, -2, [-10, 0, 22], 7, 2), (0, -3, [-22, 0, 0], 8, 2), (3, -2, [-10, 0, -22], 7, 2),
                            (-2.5, 2, [18, 0, 15], 6, 2), (2.5, 2, [18, 0, -15], 6, 2), (0, 1, [8, 0, 0], 9, 3)]:
        m.blade(H, [x, 33, z], L, w, OBS, r)
    m.chest(BASALT)
    m.cube(B, [-6, 19.5, -4], [12, 5, 8], ASH, art=m.rag([12, 5, 8], 2))
    m.cube(B, [-4, 13, -4.5], [3, 7, 1], ASH, art=m.rag([3, 7, 1], 2), rot=[0, 0, -8], pivot=[-2.5, 20, -4.5])
    m.cube(B, [-1, 12, -4.75], [2, 14, 1], leather(["#3a2416", "#4a2e1c"]), rot=[0, 0, 35], pivot=[0, 19, -4.25])
    belt(m, leather(["#3a2416", "#4a2e1c"]), "#b8902e")
    m.chain(B, [2, 11.5, -4], [4.5, 9.5, -3.5], CHAIN)
    m.cube(B, [-4.5, 3, -3.75], [9, 8, 1], ASH, art=m.rag([9, 8, 1], 4))
    m.flare_cape(over(ASH, drips("#ff7a1e", 0.25, glow=True, faces=("south", "north"), maxlen=2)), width=12, layers=2, depth=7)
    m.arms(BASALT)
    m.pauldron([BASALT, OBS], tiers=2, flare=8)
    for k, (z, L, w) in enumerate([(-2.5, 7, 2), (0, 9, 3), (2.5, 6, 2)]):
        m.blade(RA, [-9, 24.5, z], L, w, OBS, [10 - k * 10, 0, 30])
        m.blade(LA, [9, 24.5, z], L, w, OBS, [10 - k * 10, 0, -30])
    m.gauntlet(over(OBS, trim("#4a3a4a")), h=4)
    m.legs(BASALT)
    m.knee(OBS, y0=5.5, h=4)
    m.boots(BASALT, h=7)
    m.cuff(OBS, y0=6.5, h=2)
    m.R(RB, [-5.5, 0, -4.25], [7, 2, 1], OBS)
    return m


# =====================================================================================
# CURSED BONE (ring 4)
# =====================================================================================
def cursed_bone():
    m = AR("cursed_bone", 1111)
    CLOTH = over(cloth(["#141410", "#1e1e18", "#2a2a20"]), speckle("#2a4a30", 0.06))
    RAG = over(cloth(["#10140e", "#1a2418", "#24362a"]), drips("#2a6a3a", 0.35, faces=("south", "north"), maxlen=8))
    BONEP = over(plates(["#a89a7a", "#c8b894", "#e2d6b2"], ph=2, pw=4), speckle("#8a7c5e", 0.05))
    GHOST = lit("#3aff8a")
    T = "#b8902e"
    skull = A(["BBBBBBBBBB", "BBBBBBBBBB", "B__BBBB__B", "B!!BBBB!!B", "B__BBBB__B", "BBBB__BBBB",
               "BBB_BB_BBB", "BTBTBTBTBB", "B_B_B_B_BB", "BBBBBBBBBB"], B="#c8b894", T="#efe6c8", **{"!": "!3aff8a"})
    m.helm(BONE, art={"north": skull}, y0=23, h=9)
    m.cube(H, [-4, 21, -5.25], [8, 2, 3], BONE, art={"north": A(["TTTTTTTT", "BBBBBBBB"], T="#efe6c8", B="#b8a884")})
    tricorn(m, pal("#141410", "#1e1e18"), T, y=31)
    for side in (-1, 1):
        m.spike(H, [side * 7, 32, -1], 5, BONE, [0, 0, -side * 45], thick=2)
        m.spike(H, [side * 5.5, 35.5, 3], 3, BONE, [20, 0, -side * 20])
    for (x, z) in [(-3, -3), (3, -3), (-4, 3), (2, 2)]:
        m.cube(H, [x, 35.5, z], [1, 2, 1], GHOST)
    ribs = {(x, y): "R" for y in (2, 4, 6, 8, 10) for x in list(range(0, 4)) + list(range(6, 10))}
    ribs.update({(x, y): "!" for y in (3, 5, 7, 9) for x in (1, 2, 7, 8)})
    ribs.update({(4, y): "R" for y in range(1, 12)}); ribs.update({(5, y): "R" for y in range(1, 12)})
    m.chest(CLOTH, art={"north": A.at(10, 14, ribs, R="#c8b894", **{"!": "!3aff8a"})})
    belt(m, leather(["#2a1c12", "#3a2618"]), "#e2d6b2")
    m.cube(B, [-1, 11.25, -4.25], [2, 2, 1], BONE, art={"north": A(["!!", "BB"], B="#e2d6b2", **{"!": "!3aff8a"})})
    m.chain(B, [-4.5, 11, -3.75], [-2, 8, -4], GOLD)
    m.cube(B, [3.75, 6.5, -2], [2, 3, 2], BRASS, art={"north": A(["BB", "LL", "BB"], B="#7a5a1e", L="!3aff8a")})
    m.flare_cape(RAG, width=12, layers=2, depth=8)
    m.cube(B, [-4.5, 2.5, -3.75], [9, 9, 1], RAG, art=m.rag([9, 9, 1], 5))
    m.cube(B, [-2.5, 16, 4.5], [5, 5, 1], GOLD, art={"sides": A(["G_G_G", "_GGG_", "GG_GG", "_GGG_", "G_G_G"], G=T)})
    m.arms(bands(CLOTH, (1, 3, BONEP), (5, 7, BONEP), (9, 11, BONEP)))
    m.R(RA, [-10.5, 21, -4], [7, 5, 8], BONE,
        art={"west": A(["BBBBBBBB", "B!!BB!!B", "B__BB__B", "BBBB_BBB", "BTBTBTBB"], B="#c8b894", T="#efe6c8", **{"!": "!3aff8a"})})
    m.spike(RA, [-10, 26, -1], 4, BONE, [0, 0, 35]); m.spike(LA, [10, 26, -1], 4, BONE, [0, 0, -35])
    m.spike(RA, [-10, 26, 2], 3, BONE, [0, 0, 50]); m.spike(LA, [10, 26, 2], 3, BONE, [0, 0, -50])
    m.gauntlet(BONEP, h=3)
    m.legs(bands(CLOTH, (1, 2, BONEP), (4, 5, BONEP), (7, 8, BONEP)))
    m.R(RL, [-4.75, 6, -3], [5, 6, 1], RAG, art=m.rag([5, 6, 1], 3))
    m.knee(BONE, y0=6, h=3)
    m.boots(bands(CLOTH, (1, 2, BONEP), (4, 5, BONEP)))
    for k in range(3):
        m.R(RB, [-4.75 + k * 2, 0, -4.5], [1, 1, 2], BONE)
    return m


# =====================================================================================
# KRAKEN SCALE (ring 5)
# =====================================================================================
def kraken_scale():
    m = AR("kraken_scale", 1212)
    SCALE = over(scales(["#140e2a", "#1f1640", "#2c1f58", "#3a2a70", "#2a4a7a"]), spots("#5ab4ff", 0.02, glow=True))
    TENT = over(pal("#3a1e4a", "#4e2a62", "#62367a"), spots("#8a5aa0", 0.05))
    BONEG = over(pal("#a89470", "#c8b48c", "#e0d0a8"), trim("#f0e4c4", top=True, bottom=False))
    CAPE = scales(["#101a38", "#182a4e", "#223e62", "#2a5070"], sw=2, sh=3)
    visor = A(["..........", "..........", "..........", ".G.G..G.G.", ".G.G..G.G.", ".G.G..G.G.",
               "..G....G..", "..........", "..........", ".........."], G="!5ab4ff")
    m.helm(SCALE, art={"north": visor})
    m.cube(H, [-1, 23, -5.5], [2, 10, 1], BONEG)
    m.blade(H, [-4, 32, -2], 7, 2, BONEG, [55, 0, 20]); m.blade(H, [4, 32, -2], 7, 2, BONEG, [55, 0, -20])
    for k, (x, z, L, r) in enumerate([(-4.5, 4.5, 11, [-8, 0, 12]), (-2.5, 5, 14, [-12, 0, 5]), (-0.5, 5.2, 13, [-10, 0, 0]),
                                       (1.5, 5, 15, [-14, 0, -4]), (3.5, 4.8, 12, [-9, 0, -10]), (-5.2, 1.5, 10, [0, 0, 18]),
                                       (5.2, 1.5, 10, [0, 0, -18]), (-5.2, -1.5, 8, [4, 0, 14]), (5.2, -1.5, 8, [4, 0, -14])]):
        m.tentacle(H, [x, 32, z], L, TENT, rot=r)
    m.chest(SCALE)
    m.cube(B, [-1, 20, -3.75], [2, 2, 1], lit("#b8d8ff"))
    m.cube(B, [-1, 13, -4], [2, 13, 1], leather(["#3a2416", "#4a2e1c"]), rot=[0, 0, 35], pivot=[0, 19, -3.5])
    belt(m, leather(["#3a2416", "#4a2e1c"]), "#d2a84a")
    m.cube(B, [-1, 8, -4], [2, 3, 1], GOLD, art={"north": A(["G_", "GG", "_G"], G="#d2a84a")})
    m.flare_cape(CAPE, width=12, layers=2, depth=8)
    for k, (x, L) in enumerate([(-4.5, 16), (-1.5, 20), (1.5, 18), (4.5, 15)]):
        m.tentacle(B, [x, 23, 5.25], L, TENT, rot=[8, 0, (k - 1.5) * 4])
    m.cube(B, [-4, 4, -3.75], [8, 7, 1], CAPE, art=m.rag([8, 7, 1], 4))
    m.arms(bands(SCALE, (7, 12, over(TENT, spots("#d8b8e0", 0.12)))))
    m.pauldron([over(SCALE, trim("#c8b48c")), SCALE], tiers=2, flare=8)
    for k, z in enumerate((-2.5, 0, 2.5)):
        m.blade(RA, [-9.5, 24.5, z], 5, 2, BONEG, [0, 0, 30 + k * 8]); m.blade(LA, [9.5, 24.5, z], 5, 2, BONEG, [0, 0, -(30 + k * 8)])
    m.gauntlet(over(SCALE, trim("#c8b48c")), h=3)
    m.legs(SCALE)
    m.R(RL, [-4.5, 3.5, -3.25], [4, 8, 1], TENT, art={"north": A(["T_TT", "TTOT", "TOTT", "TTTT", "TTOT", "TOTT", "TTTT", "TOTT"], T="#4e2a62", O="#e0c8e8")})
    m.blade(RL, [-4.75, 8, 0], 4, 2, BONEG, [0, 0, 45]); m.blade("armorLeftLeg", [4.75, 8, 0], 4, 2, BONEG, [0, 0, -45])
    m.boots(bands(SCALE, (0, 1, BONEG), (4, 5, BONEG)))
    m.R(RB, [-5.5, 0, -3.75], [7, 2, 1], BONEG)
    m.cuff(over(SCALE, trim("#c8b48c")), y0=6, h=1)
    return m


BUILDERS = {f.__name__: f for f in (powder_monkey, forgeguard, tidecourt, gallowbreaker, thalassar,
                                    pirate_armor, castaway, navy_officer, corsair, ashen, cursed_bone, kraken_scale)}
