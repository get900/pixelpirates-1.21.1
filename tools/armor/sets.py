"""The twelve 3D armor sets, built from the concept sheets in D:\\Minecraft Modding\\armorrenders\\<set>.png.
Each builder returns an AR (tools/armor/kit.py). Geo coordinates: feet y 0, the wearer faces -Z, RIGHT side = -X.
Build the right-side piece with m.R(...) / m.pair(...) and it is mirrored onto the left bone.
"""
from kit import AR, A, noise, bands, over, lit, veins, spots, border, drips, speckle, solid, SIDES

H, B, RA, RL, RB = "armorHead", "armorBody", "armorRightArm", "armorRightLeg", "armorRightBoot"


def pal(*c):
    return noise(list(c))


# shared materials
BRASS = pal("#7a5a1e", "#a8822e", "#d2a84a")
GOLD = pal("#a07818", "#d4a52a", "#f2cf5a")
IRON = pal("#3e4248", "#555a62", "#6c727a")
STEEL = pal("#8a9098", "#a8aeb6", "#c8ced6")
ROPE = pal("#7a5a2e", "#9a7640", "#b8935a")
BONE = pal("#a89a7a", "#c8b894", "#e2d6b2")
CHAIN = over(pal("#34373c", "#4a4e54", "#62666c"), speckle("#8a8e94", 0.15))


def buckle(w, h, frame="G", center="."):
    """A belt-buckle face: a hollow square frame of `frame` in the middle of a w x h face."""
    pts = {}
    bw = min(4, w - 2); bh = min(h, 3 if h < 4 else 4)
    x0 = (w - bw) // 2; y0 = (h - bh) // 2
    for x in range(bw):
        for y in range(bh):
            if x in (0, bw - 1) or y in (0, bh - 1):
                pts[(x0 + x, y0 + y)] = frame
    return pts


# =====================================================================================
# BOSS SET I - POWDER-MONKEY'S BRIGANDINE
# =====================================================================================
def powder_monkey():
    m = AR("powder_monkey", 101)
    TAR = pal("#1e1712", "#2b2119", "#3a2c20")
    LEATHER = pal("#3a2618", "#4c3220", "#5e3e28")
    STUD = over(TAR, spots("#b8902e", 0.08))
    RED = pal("#6a1414", "#8a1c1c", "#a82a24")
    TEAL = pal("#1f4f4c", "#2e6f6a", "#3f8a82")
    CLOTH = pal("#8a847a", "#a8a296", "#c4beb2")
    BREECH = over(pal("#2e2a28", "#3c3634", "#4a4440"), spots("#3f6f68", 0.05, size=2), speckle("#5a3a24", 0.05))
    # head: leather skullcap under a red bandana, the face open; serpent-scale neckguard + knot at the back
    face = A(["RRRRRRRRRR", "RRRRRRRRRR", "__________", "__________", "__________",
              "__________", "__________", "__________", "__________", "__________"], R="#8a1c1c")
    m.cube(H, [-5, 30, -5], [10, 3, 10], TAR, art={"north": A(["RRRRRRRRRR", "RRRRRRRRRR", "TTTTTTTTTT"], R="#8a1c1c", T="#2b2119")})
    m.cube(H, [-5.25, 29.5, -5.25], [10, 2, 10], RED, art={"north": A(["R" * 10, "K" * 10], R="#9a2020", K="#6a1414")})
    for side in (-1, 1):                                            # the cap comes down the sides + back
        m.cube(H, [4.75 if side > 0 else -5.25, 25, -3], [0.5 * 0 + 1, 5, 8], TAR)
    m.cube(H, [-5, 24, 4.25], [10, 6, 1], TEAL, art=m.rag([10, 6, 1], 2))           # the neckguard of scales
    m.cube(H, [-5.5, 22, 4.75], [11, 3, 1], over(TEAL, spots("#57a79a", 0.2)), art=m.rag([11, 3, 1], 2))
    m.cube(H, [-1.5, 28.5, 5.25], [3, 3, 2], RED)                    # the knot
    m.cube(H, [-2, 22.5, 5.5], [1, 6, 1], RED, rot=[12, 0, 8]); m.cube(H, [0.5, 23, 5.5], [1, 5, 1], RED, rot=[10, 0, -10])
    # chest: studded brigandine, bandolier of powder horns + dynamite, belt, lantern, red sash
    m.chest(STUD, art={"north": A.at(10, 14, {**{(4, y): "L" for y in range(0, 5)}, **{(5, y): "L" for y in range(0, 5)},
                                              (4, 1): "W", (5, 1): "W"}, L="#4c3220", W="#a8a296")})
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], LEATHER, art={"north": A.at(11, 2, {(4, 0): "G", (5, 0): "G", (6, 0): "G", (4, 1): "G", (6, 1): "G"}, G="#d2a84a")})
    m.cube(B, [-1, 13, -4.25], [2, 15, 1], LEATHER, rot=[0, 0, -38], pivot=[0, 18.5, -3.75])      # the bandolier
    for i, (x, y) in enumerate([(-3.2, 21.2), (-1.5, 19.3), (0.3, 17.4), (2.0, 15.5)]):
        m.cube(B, [x - 1, y - 1, -5.5], [2, 2, 2], BRASS if i % 2 == 0 else pal("#8a6a2e", "#b08a3e"), rot=[0, 0, -38], pivot=[x, y, -4.5])
    for k in range(3):                                              # dynamite bundle
        m.cube(B, [1.2 + k * 1.05, 17.5, -5.25], [1, 3, 1], over(RED, spots("#c8b894", 0.1)))
    m.cube(B, [4.25, 7.5, -2], [2, 3, 2], over(BRASS, spots("#1e1712", 0.1)), art={"north": A(["BB", "LL", "BB"], B="#7a5a1e", L="!ffd060")})  # lantern
    m.cube(B, [4.75, 10.5, -1.5], [1, 1, 1], BRASS)
    m.cube(B, [-5.25, 4, -2.5], [1, 8, 2], RED, art=m.rag([1, 8, 2], 3))              # sash tails
    m.cube(B, [-4.25, 5, -3.25], [1, 6, 1], RED, art=m.rag([1, 6, 1], 2))
    # arms: soot-grey cloth sleeves, leather bracers with brass buckles, studded shoulder caps
    arm = bands(CLOTH, (0, 2, TAR), (7, 13, over(LEATHER, spots("#d2a84a", 0.06))))
    m.arms(arm)
    m.R(RA, [-9.5, 21.5, -3.5], [7, 3, 7], STUD)
    m.R(RA, [-9.5, 13, -3.5], [1, 3, 7], LEATHER, art={"west": A.at(7, 3, buckle(7, 3, "G"), G="#d2a84a")})
    # legs: patched breeches, a strap round each thigh
    m.legs(bands(BREECH, (2, 3, LEATHER)))
    m.R(RL, [-5, 9, -3], [6, 1, 6], LEATHER)
    # boots: tall dark boots, brass buckles, iron toe caps
    m.boots(bands(LEATHER, (0, 1, TAR), (4, 5, over(TAR, spots("#d2a84a", 0.3)))), h=7)
    m.R(RB, [-5.25, 0, -3.75], [6, 2, 1], IRON)
    m.R(RB, [-5.5, 6.5, -3.5], [7, 1, 7], TAR)
    return m


# =====================================================================================
# BOSS SET II - FORGEGUARD PLATE
# =====================================================================================
def forgeguard():
    m = AR("forgeguard", 202)
    IRONB = over(pal("#15151a", "#22222a", "#303038"), veins("#ff7a26", 0.05), veins("#ffb040", 0.015))
    VERD = over(pal("#2f5a4c", "#3e6e60", "#4e8270"), speckle("#8a6a3e", 0.12))
    BANNER = over(pal("#4a1010", "#5e1616", "#721c1c"), speckle("#3a0c0c", 0.1))
    # head: a closed great-helm, glowing cyan T-visor, verdigris cross-band + crest
    visor = A(["..........", "..........", "..........", "..........", ".VVVVVVVV.", ".GGGGGGGG.",
               "....GG....", "....GG....", "....GG....", ".........."], V="#62a08a", G="!8ff6ff")
    m.helm(IRONB, art={"north": visor})
    m.cube(H, [-5.5, 29, -5.5], [11, 1, 11], VERD)
    m.cube(H, [-1, 33, -5.5], [2, 1, 11], VERD)                                   # crest
    m.cube(H, [-0.5, 34, -4], [1, 1, 8], over(IRONB, spots("#ff7a26", 0.3, glow=True)))
    # chest: black plate with ember seams, anchor emblem, gorget, chain, tabard + banner cape
    anchor = A.at(10, 14, {(4, 2): "V", (5, 2): "V", (4, 3): "V", (5, 3): "V", **{(4, y): "V" for y in range(4, 10)},
                           **{(5, y): "V" for y in range(4, 10)}, (2, 7): "V", (3, 7): "V", (6, 7): "V", (7, 7): "V",
                           (2, 8): "V", (7, 8): "V", (3, 9): "V", (6, 9): "V", (3, 5): "V", (6, 5): "V"}, V="#62a08a")
    m.chest(IRONB, art={"north": anchor})
    m.cube(B, [-4, 23.5, -3.75], [8, 2, 7], VERD)                                 # gorget
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], pal("#3a2618", "#4c3220"), art={"north": A.at(11, 2, buckle(11, 2, "V"), V="#62a08a")})
    m.cube(B, [-2.5, 3, -4], [5, 9, 1], BANNER, art=m.rag([5, 9, 1], 3))         # front tabard
    m.cube(B, [-4.5, 1, 3.5], [9, 22, 1], BANNER, art={**m.rag([9, 22, 1], 5), "south": A.at(9, 22, {(4, 5): "C", (4, 6): "C", (4, 7): "C", (4, 8): "C", (4, 9): "C", (3, 7): "C", (5, 7): "C", (2, 7): "C", (6, 7): "C", (4, 4): "C"}, C="#d8c8a8")})
    for i in range(6):                                                            # chain across the back
        m.cube(B, [-4 + i * 1.6, 22 - abs(i - 2.5) * 0.9, 3.9], [1, 1, 1], GOLD)
    # arms: huge layered pauldrons, ember-seamed plate, verdigris-banded gauntlets
    m.arms(bands(IRONB, (9, 11, VERD)))
    m.R(RA, [-10.5, 21, -4], [7, 4, 8], over(IRONB, border("#4e8270", ("north", "south", "east", "west"))))
    m.R(RA, [-10, 19, -3.75], [6, 2, 7], over(IRONB, border("#4e8270", SIDES)))
    m.R(RA, [-11, 24.5, -3], [4, 1, 6], VERD)
    # legs: plate with ember seams, knee cops
    m.legs(bands(IRONB, (0, 1, VERD)))
    m.R(RL, [-4.75, 5, -3.25], [5, 3, 1], VERD)
    # boots: chunky sabatons with toe plates
    m.boots(bands(IRONB, (0, 1, VERD), (4, 6, VERD)), h=7)
    m.R(RB, [-5.5, 0, -4.5], [7, 3, 2], over(IRONB, border("#4e8270", SIDES)))
    return m


# =====================================================================================
# BOSS SET III - TIDECOURT REGALIA
# =====================================================================================
def tidecourt():
    m = AR("tidecourt", 303)
    SCALE = over(pal("#0c2a30", "#15474f", "#1f6264", "#2e7a70"), speckle("#3a9a86", 0.08))
    DSCALE = over(pal("#081a20", "#0f2f36", "#16434a"), speckle("#2a6a60", 0.08))
    CORAL = pal("#8a2424", "#b03a30", "#d05a44")
    TOOTH = pal("#c8b894", "#e2d6b2", "#f2ead0")
    KELP = over(pal("#12362c", "#1a4a3a", "#256050"), speckle("#2f7a60", 0.1))
    # head: a scaled helm, glowing teal visor slits, a gold crown ringed with bone spikes + coral
    visor = A(["..........", "..........", "..........", ".GG....GG.", ".GG....GG.", ".GG....GG.",
               "..........", "..........", "..........", ".........."], G="!4ff0d8")
    m.helm(SCALE, art={"north": visor})
    m.cube(H, [-5.5, 31, -5.5], [11, 2, 11], GOLD)                                # the crown band
    m.cube(H, [-1, 32, -6.25], [2, 3, 1], lit("#3affd8"))                         # the crown jewel
    for i, x in enumerate([-4.5, -2.5, 1.5, 3.5]):
        m.cube(H, [x, 33, -5.5], [1, 3 + (i % 2), 1], TOOTH)
        m.cube(H, [x, 33, 4.5], [1, 3 + ((i + 1) % 2), 1], TOOTH)
    for z in (-3, 0, 3):
        m.cube(H, [-5.5, 33, z], [1, 3, 1], TOOTH); m.cube(H, [4.5, 33, z], [1, 3, 1], TOOTH)
    for (x, z, r) in [(-4, -2, [0, 0, 18]), (3.5, -1, [0, 0, -18]), (-3, 3, [-15, 0, 10]), (2.5, 3.5, [-15, 0, -12])]:   # coral branches
        m.spike(H, [x, 33, z], 5, CORAL, r)
        m.spike(H, [x + (1 if x < 0 else -1) * 0.2, 36, z], 2, CORAL, [0, 0, 55 if x < 0 else -55])
    # chest: sea-green scale mail, gold necklace with pearls, pearl belt, kelp cape
    m.chest(SCALE)
    m.cube(B, [-4.5, 22.5, -3.5], [9, 1, 1], GOLD)
    for x in (-3.5, -1.5, 1.5, 3.5):
        m.cube(B, [x - 0.5, 21, -3.75], [1, 1, 1], GOLD)
    m.cube(B, [-1, 18.5, -4], [2, 2, 1], pal("#e2dccc", "#f4f0e6", "#ffffff"))  # the great pearl
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], GOLD, art={"north": A.at(11, 2, {(5, 0): "P", (5, 1): "P"}, P="#f4f0e6")})
    m.cube(B, [-5.25, 3, 3.5], [10.5, 21, 1], KELP, art=m.rag([10, 21, 1], 7))  # kelp cape
    m.cube(B, [-4, 4.5, -3.75], [8, 7, 1], KELP, art=m.rag([8, 7, 1], 4))       # front kelp skirt
    # arms: pauldrons of coral and the Bloodfin's teeth, gold-banded bracers
    m.arms(bands(DSCALE, (6, 8, GOLD), (10, 12, GOLD)))
    m.R(RA, [-10, 21, -3.75], [7, 4, 7.5 * 0 + 7], SCALE)
    for k, z in enumerate((-3, -0.5, 2)):
        m.spike(RA, [-9.5, 24, z], 4, TOOTH, [0, 0, 35 + k * 5])
        m.R(RA, [-10.5, 24.5, z - 0.5], [1, 3, 1], CORAL, rot=[0, 0, 20], pivot=[-10, 24.5, z])
    m.spike("armorLeftArm", [9.5, 24, -3], 4, TOOTH, [0, 0, -35]); m.spike("armorLeftArm", [9.5, 24, -0.5], 4, TOOTH, [0, 0, -40])
    m.spike("armorLeftArm", [9.5, 24, 2], 4, TOOTH, [0, 0, -45])
    # legs: dark scale, gold bands, knee roundels with a glowing gem
    m.legs(bands(DSCALE, (0, 1, GOLD), (5, 6, GOLD)))
    m.R(RL, [-4, 6, -3.25], [4, 3, 1], GOLD, art={"north": A.at(4, 3, {(1, 1): "!", (2, 1): "!"}, **{"!": "!3affd8"})})
    # boots: gold-banded, a sprig of coral at the ankle
    m.boots(bands(DSCALE, (0, 1, GOLD), (3, 4, GOLD)))
    m.spike(RB, [-5, 5, 1], 3, CORAL, [0, 0, 25]); m.spike("armorLeftBoot", [5, 5, 1], 3, CORAL, [0, 0, -25])
    return m


# =====================================================================================
# BOSS SET IV - GALLOWBREAKER HARNESS
# =====================================================================================
def gallowbreaker():
    m = AR("gallowbreaker", 404)
    PLATE = over(pal("#141218", "#1e1c24", "#2a2832"), drips("#3a1f4a", 0.3, maxlen=5), speckle("#4a2a5e", 0.06))
    HOOD = over(pal("#1c1424", "#2a1c36", "#3a2648", "#4e2e62"), speckle("#5e3a78", 0.08))
    BEAK = pal("#6a6660", "#8a857c", "#a8a296")
    MAIL = over(pal("#2a2c30", "#3a3c42"), spots("#5a5e66", 0.3))
    SUCKER = over(pal("#b8a88a", "#d2c4a4"), spots("#6a5a7a", 0.12))
    # head: a ragged ink-purple hood, a bone plague-beak mask with hollow eyes
    m.cube(H, [-5.5, 22.5, -5.5], [11, 11, 11], HOOD, art={**m.rag([11, 11, 11], 3, keep_top=7),
                                                           "north": A(["HHHHHHHHHHH", "HHHHHHHHHHH", "HH_______HH", "H_________H", "H_________H", "H_________H",
                                                                       "H_________H", "H_________H", "H_________H", "H_________H", "HH_______HH"], H="#2a1c36")})
    m.cube(H, [-4, 24, -5], [8, 7, 1], BEAK, art={"north": A(["BBBBBBBB", "B__BB__B", "B__BB__B", "BBBBBBBB", "BBB..BBB", "BBBBBBBB", "BBBBBBBB"], B="#8a857c")})
    m.cube(H, [-1, 24, -9], [2, 3, 4], BEAK, rot=[18, 0, 0], pivot=[0, 27, -5])  # the beak
    m.cube(H, [-0.5, 23, -10], [1, 2, 2], BEAK, rot=[40, 0, 0], pivot=[0, 25, -8.5])
    # chest: blackened plate, chain mail middle, chains across the chest + a ring, ragged ink cloak
    m.chest(PLATE, art={"north": A.at(10, 14, {(x, y): "M" for x in range(2, 8) for y in range(6, 10)}, M="#3a3c42")})
    for i in range(7):
        m.cube(B, [-4.5 + i * 1.4, 22 - i * 1.4, -3.75], [1, 1, 1], CHAIN)
        m.cube(B, [3.5 - i * 1.4, 22 - i * 1.4, -3.75], [1, 1, 1], CHAIN)
    m.cube(B, [-1.5, 15, -4.25], [3, 3, 1], IRON, art={"north": A(["III", "I_I", "III"], I="#555a62")})
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], pal("#2a1c14", "#3a2618"), art={"north": A.at(11, 2, buckle(11, 2, "I"), I="#6c727a")})
    m.cube(B, [-5, 1, 3.5], [10, 23, 1], HOOD, art=m.rag([10, 23, 1], 7))       # the cloak
    m.cube(B, [-4.5, 3, -3.75], [9, 8, 1], HOOD, art=m.rag([9, 8, 1], 4))
    # arms: sucker-studded shoulders, broken manacles + dangling chains
    m.arms(bands(PLATE, (4, 6, IRON), (10, 13, IRON)))
    m.R(RA, [-10, 21, -3.75], [7, 4, 7], over(HOOD, spots("#d2c4a4", 0.15, size=1)))
    for (x, y, z) in [(-10.25, 23, -2), (-10.25, 22, 1), (-9, 25, -0.5)]:
        m.R(RA, [x, y, z], [2, 2, 2], SUCKER, art={"sides": A(["SS", "S_"], S="#d2c4a4")})
    m.R(RA, [-9.5, 11, -3.5], [7, 3, 7], IRON)                                   # the manacle
    for k in range(4):
        m.R(RA, [-7, 10 - k * 1.2, -0.5], [1, 1, 1], CHAIN)
    m.R(RA, [-8, 5.25, -1.5], [3, 1, 3], IRON, art={"up": A(["III", "I_I", "III"], I="#6c727a")})
    # legs: a chain-mail skirt, iron bands, ink-purple rag flaps
    m.legs(bands(MAIL, (0, 2, PLATE), (6, 7, IRON)))
    m.R(RL, [-4.75, 6, -3], [5, 6, 1], HOOD, art=m.rag([5, 6, 1], 3))
    # boots: iron-banded, a buckle on each
    m.boots(bands(PLATE, (1, 2, IRON), (4, 5, IRON)))
    m.R(RB, [-5.5, 0, -3.5], [7, 2, 1], IRON)
    return m


# =====================================================================================
# BOSS SET V - MANTLE OF THALASSAR
# =====================================================================================
def thalassar():
    m = AR("thalassar", 505)
    HIDE = over(pal("#06201c", "#0a2e26", "#0e3c32", "#134a3e"), veins("#e8b830", 0.012, length=(3, 6)), spots("#5af0ff", 0.008, glow=True))
    DARK = over(pal("#041612", "#08271f", "#0d3a2e"), speckle("#1f6a54", 0.08))
    FIN = over(pal("#1f5a48", "#2f7a5e", "#c8a040"), veins("#ffd24a", 0.06, glow=True))
    GOLDG = lit("#ffcc3a")
    # head: the eye-socket helm - a great glowing gold eye at the brow, cyan visor, gold fin crests
    eye = A(["..........", "...EEEE...", "..EOOOOE..", "..EOPPOE..", "...EEEE...", "..........",
             ".CC....CC.", "..........", "..........", ".........."], E="!ffcc3a", O="!fff0a0", P="#1a0a00", C="!5af0ff")
    m.helm(HIDE, art={"north": eye})
    for (x, z, r, L, t) in [(-4.5, -3, [-10, 0, 30], 10, 2), (-4.5, 1, [15, 0, 42], 9, 2), (4.5, -3, [-10, 0, -30], 10, 2),
                            (4.5, 1, [15, 0, -42], 9, 2), (-2, 3.5, [45, 0, 14], 9, 2), (2, 3.5, [45, 0, -14], 9, 2),
                            (0, -3, [-22, 0, 0], 7, 2), (-3, -1, [0, 0, 18], 8, 1), (3, -1, [0, 0, -18], 8, 1)]:
        m.spike(H, [x, 32, z], L, FIN, r, thick=t)
    # chest: dark scale with the gold veins, a glowing heart-line down the middle, the dorsal-fin mantle
    m.chest(HIDE, art={"north": A.at(10, 14, {**{(4, y): "G" for y in range(1, 13)}, **{(5, y): "G" for y in range(1, 13)},
                                              (3, 4): "G", (2, 3): "G", (6, 4): "G", (7, 3): "G", (3, 8): "G", (6, 8): "G"}, G="!ffcc3a")})
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], DARK, art={"north": A.at(11, 2, {(5, 0): "G", (5, 1): "G", (4, 0): "G", (6, 0): "G"}, G="!ffcc3a")})
    m.cube(B, [-5.5, 0, 3.5], [11, 24, 1], over(DARK, veins("#2f7a5e", 0.05, glow=False)),
           art={**m.rag([11, 24, 1], 7), "south": A.at(11, 24, {(5, y): "G" for y in range(0, 20)}, G="!ffcc3a")})
    m.cube(B, [-4.5, 3, -3.75], [9, 8, 1], DARK, art=m.rag([9, 8, 1], 4))
    # arms: fin pauldrons fanning up and out, bioluminescent bracers with fins
    m.arms(bands(HIDE, (8, 10, GOLDG)))
    m.R(RA, [-10, 21, -3.75], [7, 4, 7], HIDE)
    for k, (z, L) in enumerate([(-3, 10), (-1, 12), (1, 11), (3, 9)]):
        m.spike(RA, [-9.5, 24, z], L, FIN, [0, 0, 24 + k * 10], thick=2)
        m.spike("armorLeftArm", [9.5, 24, z], L, FIN, [0, 0, -(24 + k * 10)], thick=2)
    m.spike(RA, [-9, 15, 0], 4, FIN, [0, 0, 70]); m.spike("armorLeftArm", [9, 15, 0], 4, FIN, [0, 0, -70])
    # legs: dark scale, gold knee fins
    m.legs(bands(DARK, (0, 1, GOLDG)))
    m.spike(RL, [-3.5, 8, -3], 5, FIN, [-35, 0, 20]); m.spike("armorLeftLeg", [3.5, 8, -3], 5, FIN, [-35, 0, -20])
    # boots: scaled sabatons, gold bands, ankle fins
    m.boots(bands(HIDE, (0, 1, GOLDG), (4, 5, GOLDG)))
    m.spike(RB, [-5, 3, 1], 4, FIN, [20, 0, 50]); m.spike("armorLeftBoot", [5, 3, 1], 4, FIN, [20, 0, -50])
    return m


# =====================================================================================
# PIRATE ARMOR (starter)
# =====================================================================================
def tricorn(m, felt, trim, y=30.5, scale=1.0, feather=None):
    """A black tricorn: crown + three upturned brim walls with gold edging (the face stays open)."""
    m.cube(H, [-4.5, y + 1, -4.5], [9, 4, 9], felt, art={"sides": A(["F" * 9, "F" * 9, "F" * 9, "T" * 9], F="#1a1a1e", T=trim)})
    m.cube(H, [-7, y, -7], [14, 1, 14], felt, art={"up": A.at(14, 14, {**{(i, 0): "T" for i in range(14)}, **{(i, 13): "T" for i in range(14)},
                                                                    **{(0, i): "T" for i in range(14)}, **{(13, i): "T" for i in range(14)}}, T=trim)})
    wall = {"sides": A(["TTTTTTTTTTTT", "FFFFFFFFFFFF", "FFFFFFFFFFFF"], F="#1a1a1e", T=trim)}
    m.cube(H, [-6, y + 1, 6], [12, 3, 1], felt, art=wall)                                         # back wall
    m.cube(H, [-6.5, y + 1, -6], [1, 3, 11], felt, art=wall, rot=[0, 18, 0], pivot=[-6, y + 1, 0])   # right wall
    m.cube(H, [5.5, y + 1, -6], [1, 3, 11], felt, art=wall, rot=[0, -18, 0], pivot=[6, y + 1, 0])    # left wall
    if feather:                                                     # a big plume sweeping up + back off the right side
        for k in range(7):
            m.cube(H, [-6.5 + (k % 3) * 0.7, y + 2.5, 1.5 - k * 1.1], [1, 7 - k % 3, 2], feather[k % len(feather)],
                   rot=[30 - k * 9, 0, 35 - (k % 2) * 12], pivot=[-6, y + 2.5, 1 - k])


def pirate_armor():
    m = AR("pirate_armor", 606)
    FELT = pal("#141418", "#1c1c22", "#26262c")
    RED = pal("#7a1616", "#9a2020", "#b02a26")
    WAIST = over(pal("#3a2416", "#4c301c", "#5a3a22"), spots("#b8902e", 0.05))
    STRIPE = bands(pal("#d8d0c0", "#e6dfd0"), *[(y, y + 2, RED) for y in range(0, 13, 4)])
    tricorn(m, FELT, "#c89a3a")
    m.cube(H, [-5, 27.5, -5], [10, 3, 10], RED, art={"north": A(["R" * 10, "R" * 10, "_" * 10], R="#9a2020")})   # bandana under the hat
    m.cube(H, [-1.5, 26, 5.25], [3, 3, 1], RED)
    m.strands(H, -1.5, 1.5, 26.5, 5.5, RED, [5, 4, 6])
    # chest: white shirt, brown waistcoat open at the front, crossed belts, gold buckle, sash
    m.chest(WAIST, art={"north": A.at(10, 14, {**{(x, y): "S" for x in (4, 5) for y in range(3, 14)}, (4, 1): "S", (5, 1): "S", (3, 2): "S", (6, 2): "S"}, S="#e6dfd0")})
    m.cube(B, [-1, 13, -4], [2, 14, 1], pal("#4a2c18", "#5a3620"), rot=[0, 0, 35], pivot=[0, 19, -3.5])
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], pal("#3a2416", "#4a2e1c"), art={"north": A.at(11, 2, buckle(11, 2, "G"), G="#d2a84a")})
    m.cube(B, [3.75, 4, -3], [1, 8, 2], RED, art=m.rag([1, 8, 2], 3))
    m.cube(B, [-5, 6.5, -2], [2, 3, 2], BRASS, art={"north": A(["BB", "LL", "BB"], B="#7a5a1e", L="!ffd060")})
    # arms: red-and-white striped sleeves, leather cuffs
    m.arms(bands(STRIPE, (9, 13, WAIST)))
    # legs: dark torn trousers
    m.legs(over(pal("#222226", "#2e2e34", "#3a3a40"), spots("#5a4a3e", 0.05, size=2)))
    # boots: brown folded-top boots with gold buckles
    m.boots(bands(pal("#3a2416", "#4a2e1c", "#5a3a22"), (3, 4, over(pal("#2a1a10"), spots("#d2a84a", 0.3)))), h=6)
    m.R(RB, [-5.5, 6, -3.5], [7, 2, 7], pal("#4a2e1c", "#5e3a24"))
    return m


# =====================================================================================
# CASTAWAY (ring 1)
# =====================================================================================
def castaway():
    m = AR("castaway", 707)
    PALM = over(pal("#3a5a1e", "#4e7426", "#66902e"), speckle("#2a4214", 0.1))
    STRAW = pal("#6a5a2a", "#8a7636", "#a89048")
    SAIL = over(pal("#c8bca0", "#d8ceb4", "#e8e0cc"), spots("#9a7a4a", 0.05, size=2), speckle("#6a8a4a", 0.03))
    WOOD = over(pal("#6a6258", "#8a8074", "#a89e90"), speckle("#4a4238", 0.1))
    GREEN = pal("#1e3a24", "#284a2e", "#325a38")
    # head: a woven palm-leaf hat with a drooping frond brim + rope band; a green cloth mask over the mouth
    m.cube(H, [-4.5, 31, -4.5], [9, 4, 9], STRAW)
    m.cube(H, [-4.75, 31.5, -4.75], [9.5 * 0 + 10, 1, 10], ROPE)
    m.cube(H, [-8, 30.5, -8], [16, 1, 16], PALM)
    for side in range(4):                                           # fronds drooping off the brim
        for i in range(8):
            x = -8 + i * 2 + 0.5
            L = 2 + (i * 7 + side * 3) % 3
            if side == 0: m.cube(H, [x, 30.5 - L, -8.25], [1, L, 1], PALM)
            if side == 1: m.cube(H, [x, 30.5 - L, 7.25], [1, L, 1], PALM)
            if side == 2: m.cube(H, [-8.25, 30.5 - L, x], [1, L, 1], PALM)
            if side == 3: m.cube(H, [7.25, 30.5 - L, x], [1, L, 1], PALM)
    m.cube(H, [-5, 23, -5.25], [10, 4, 1], GREEN)                                  # mask
    m.cube(H, [-5, 23, -5], [1, 4, 10], GREEN); m.cube(H, [4, 23, -5], [1, 4, 10], GREEN)
    # chest: patched sailcloth tunic, rope crossing, driftwood-plank shoulder guards, rope belt
    m.chest(SAIL, art=m.rag([10, 14, 6], 3, faces=("north", "south")))
    m.cube(B, [-1, 13, -3.75], [1, 12, 1], ROPE, rot=[0, 0, 30], pivot=[0, 19, -3.5])
    m.cube(B, [0, 13, -3.75], [1, 12, 1], ROPE, rot=[0, 0, -30], pivot=[0, 19, -3.5])
    m.cube(B, [-5.5, 11.5, -3.5], [11, 1, 7], ROPE)
    m.cube(B, [-4.5, 4, -3.5], [9, 7, 1], GREEN, art=m.rag([9, 7, 1], 3))
    # arms: stacked driftwood planks on the shoulders, rope-wrapped forearms
    m.arms(bands(SAIL, (7, 8, ROPE), (9, 10, ROPE), (11, 12, ROPE)))
    for k in range(3):
        m.R(RA, [-10 + k * 0.3, 23.5 - k * 1.3, -3.5], [7, 1, 7], WOOD, rot=[0, 0, 8 + k * 3], pivot=[-6, 23, 0])
    m.R(RA, [-9.5, 13, -3.5], [1, 3, 7], WOOD)
    # legs: tattered green-brown trousers, driftwood knee guards tied with rope
    m.legs(over(pal("#3a2c1c", "#4a3a26", "#2e4a2a"), spots("#5a7a3a", 0.06, size=2)))
    m.R(RL, [-4.5, 6, -3.25], [4, 4, 1], WOOD)
    m.R(RL, [-5, 7.5, -3], [6, 1, 6], ROPE)
    # boots: bark-and-rope sandals
    m.R(RB, [-5, 0, -4], [6, 1, 7], pal("#5a3a1e", "#6a4a26"))
    m.R(RB, [-5, 1, -3], [6, 1, 6], ROPE); m.R(RB, [-5, 3, -3], [6, 1, 6], ROPE)
    return m


# =====================================================================================
# NAVY OFFICER (ring 2)
# =====================================================================================
def navy_officer():
    m = AR("navy_officer", 808)
    NAVY = over(pal("#10162e", "#18203e", "#212a4e"), speckle("#2a3460", 0.05))
    WHITE = pal("#c8c4bc", "#dcd8d0", "#ece8e0")
    TRIM = "#d2a84a"
    # head: a navy bicorne worn side to side, gold edging, white plume, blue cockade
    m.cube(H, [-8, 30.5, -2.5], [16, 4, 5], NAVY, art={"sides": A(["TTTTTTTTTTTTTTTT", "NNNNNNNNNNNNNNNN", "NNNNNNNNNNNNNNNN", "TTTTTTTTTTTTTTTT"], T=TRIM, N="#18203e")})
    m.cube(H, [-5, 34.5, -2], [10, 2, 4], NAVY, art={"sides": A(["TTTTTTTTTT", "NNNNNNNNNN"], T=TRIM, N="#18203e")})
    m.cube(H, [-1, 32, -3], [2, 2, 1], pal("#1a3a8a", "#2a5acc"), art={"north": A(["GG", "GG"], G="!3a7aff")})
    for k in range(4):
        m.cube(H, [4 + k * 0.8, 34 - k * 0.3, 1 - k], [1, 4, 1], WHITE, rot=[-20, 0, -35], pivot=[5, 34, 0])
    # chest: navy coat, gold buttons, steel breastplate, gold epaulettes, sash + belt, long coat tails
    m.chest(NAVY, art={"north": A.at(10, 14, {**{(x, y): "S" for x in range(3, 7) for y in range(3, 12)},
                                              **{(2, y): "G" for y in range(2, 13, 2)}, **{(7, y): "G" for y in range(2, 13, 2)},
                                              (4, 0): "W", (5, 0): "W", (4, 1): "W", (5, 1): "W"}, S="#a8aeb6", G="#d2a84a", W="#ece8e0")})
    m.cube(B, [-1, 13, -4], [2, 14, 1], pal("#4a2c18", "#5a3620"), rot=[0, 0, -35], pivot=[0, 19, -3.5])
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], pal("#3a2416", "#4a2e1c"), art={"north": A.at(11, 2, buckle(11, 2, "G"), G=TRIM)})
    m.cube(B, [-5, 2, 3.25], [10, 11, 1], NAVY, art={"south": A.at(10, 11, {**{(0, y): "G" for y in range(11)}, **{(9, y): "G" for y in range(11)},
                                                                            **{(x, 10): "G" for x in range(10)}, **{(4, y): "_" for y in range(5, 11)}, **{(5, y): "_" for y in range(5, 11)}}, G=TRIM)})
    m.cube(B, [-5.25, 2, -3.5], [2, 9, 1], NAVY, art={"north": A.at(2, 9, {(1, y): "G" for y in range(9)}, G=TRIM)})
    m.cube(B, [3.25, 2, -3.5], [2, 9, 1], NAVY, art={"north": A.at(2, 9, {(0, y): "G" for y in range(9)}, G=TRIM)})
    # arms: navy sleeves, white turned-back cuffs with gold buttons, fringed gold epaulettes
    m.arms(bands(NAVY, (9, 13, over(WHITE, spots(TRIM, 0.1)))))
    m.R(RA, [-10, 23.5, -3.5], [7, 1, 7], GOLD)
    m.strands(RA, -10, -3, 23.5, -3.75, GOLD, [2, 3, 2, 3, 2, 3, 2])
    m.strands(RA, -10, -3, 23.5, 3.75, GOLD, [2, 3, 2, 3, 2, 3, 2])
    # legs: white breeches, steel knee guards
    m.legs(bands(WHITE, (0, 1, pal("#3a2416"))))
    m.R(RL, [-4.5, 6, -3.25], [4, 3, 1], STEEL)
    # boots: polished black boots, gold buckles, a steel band
    m.boots(bands(pal("#0e0e12", "#16161c", "#1e1e26"), (2, 3, STEEL), (4, 5, over(pal("#0e0e12"), spots(TRIM, 0.3)))), h=7)
    return m


# =====================================================================================
# CORSAIR (ring 3)
# =====================================================================================
def corsair():
    m = AR("corsair", 909)
    CRIM = over(pal("#4a0c10", "#621218", "#7a1a1e"), speckle("#8a2226", 0.06))
    BLACK = pal("#141014", "#1e181c", "#2a2226")
    WHITE = pal("#c8c0b0", "#dcd4c4", "#ece6d8")
    TRIM = "#e0b040"
    tricorn(m, BLACK, TRIM, feather=[pal("#8a1818", "#b02424"), pal("#d8d0c0", "#eee8dc"), pal("#8a1818", "#b02424")])
    m.cube(H, [-1, 32, -5.25], [2, 2, 1], GOLD, art={"north": A(["GG", "RR"], G=TRIM, R="!d02020")})
    m.cube(H, [-5, 27.5, -5], [10, 3, 10], pal("#6a1414", "#8a1c1c"), art={"north": A(["R" * 10, "R" * 10, "_" * 10], R="#8a1c1c")})
    m.strands(H, -1.5, 1.5, 27, 5.5, pal("#6a1414", "#8a1c1c"), [6, 5, 7])
    # chest: crimson coat, gold trim + coins, white cravat, crossed belts, red sash, flared tails
    m.chest(CRIM, art={"north": A.at(10, 14, {**{(x, y): "W" for x in (4, 5) for y in range(0, 5)}, (3, 0): "W", (6, 0): "W",
                                              **{(3, y): "G" for y in range(5, 14)}, **{(6, y): "G" for y in range(5, 14)}}, W="#dcd4c4", G=TRIM)})
    m.cube(B, [-1, 13, -4], [2, 14, 1], pal("#3a2416", "#4a2e1c"), rot=[0, 0, 35], pivot=[0, 19, -3.5])
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], pal("#7a1418", "#9a1c20"), art={"north": A.at(11, 2, buckle(11, 2, "G"), G=TRIM)})
    for x in (-3, -1, 2):
        m.cube(B, [x, 12 - (x + 3) % 3, -4], [1, 1, 1], GOLD)                     # coins on the chain
    m.cube(B, [-5.5, 1.5, 3.25], [11, 10, 1], CRIM, art={**m.rag([11, 10, 1], 4), "south": A.at(11, 10, {**{(0, y): "G" for y in range(10)}, **{(10, y): "G" for y in range(10)}}, G=TRIM)})
    m.cube(B, [-5.75, 2.5, -3.5], [2, 9, 1], CRIM, art={**m.rag([2, 9, 1], 3), "north": A.at(2, 9, {(1, y): "G" for y in range(9)}, G=TRIM)})
    m.cube(B, [3.75, 2.5, -3.5], [2, 9, 1], CRIM, art={**m.rag([2, 9, 1], 3), "north": A.at(2, 9, {(0, y): "G" for y in range(9)}, G=TRIM)})
    # arms: crimson sleeves, white cuffs, black studded bracers, gold-fringed epaulettes
    m.arms(bands(CRIM, (6, 8, WHITE), (8, 13, over(BLACK, spots(TRIM, 0.12)))))
    m.R(RA, [-10, 23.5, -3.5], [7, 1, 7], GOLD)
    m.strands(RA, -10, -3, 23.5, -3.75, GOLD, [2, 3, 2, 3, 2, 3, 2])
    # legs: black trousers with gold-buckled straps
    m.legs(bands(BLACK, (3, 4, over(BLACK, spots(TRIM, 0.3)))))
    # boots: black boots, gold buckles
    m.boots(bands(BLACK, (1, 2, over(BLACK, spots(TRIM, 0.35))), (4, 5, over(BLACK, spots(TRIM, 0.35)))), h=7)
    m.R(RB, [-5.5, 6.5, -3.5], [7, 1, 7], BLACK)
    return m


# =====================================================================================
# ASHEN (volcanic)
# =====================================================================================
def ashen():
    m = AR("ashen", 1010)
    BASALT = over(pal("#1c1a1c", "#2a2628", "#3a3436", "#4a4446"), veins("#ff7a1e", 0.04), veins("#ffb040", 0.012))
    OBS = over(pal("#120e18", "#1e1628", "#2a2036"), veins("#ff6a1a", 0.05))
    CLOTH = over(pal("#8a8070", "#a0968a", "#b8ae9e"), speckle("#5a5248", 0.1))
    # head: basalt helm, glowing T-visor, obsidian spikes on the crown
    visor = A(["..........", "..........", "..........", ".GGGGGGGG.", "....GG....", "....GG....",
               "....GG....", "....GG....", "..........", ".........."], G="!ff9a2a")
    m.helm(BASALT, art={"north": visor})
    for (x, z, r, L) in [(-3, -2, [-10, 0, 20], 5), (0, -3, [-20, 0, 0], 6), (3, -2, [-10, 0, -20], 5), (-2, 2, [15, 0, 15], 4), (2, 2, [15, 0, -15], 4)]:
        m.spike(H, [x, 33, z], L, OBS, r, thick=2)
    # chest: basalt plates with ember cracks, an ash scarf round the shoulders, crossed belts, ash cloak
    m.chest(BASALT)
    m.cube(B, [-5.5, 20.5, -3.75], [11, 4, 7.5 * 0 + 8], CLOTH, art=m.rag([11, 4, 8], 2))
    m.cube(B, [-1, 13, -4.25], [2, 13, 1], pal("#3a2416", "#4a2e1c"), rot=[0, 0, 35], pivot=[0, 19, -3.75])
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], pal("#3a2416", "#4a2e1c"), art={"north": A.at(11, 2, buckle(11, 2, "B"), B="#b8902e")})
    m.cube(B, [-4.5, 3, -3.75], [9, 8, 1], CLOTH, art=m.rag([9, 8, 1], 4))
    m.cube(B, [-5, 1, 3.5], [10, 21, 1], over(CLOTH, drips("#ff7a1e", 0.2, glow=True, faces=("south",), maxlen=2)), art=m.rag([10, 21, 1], 6))
    # arms: obsidian spike clusters on the shoulders, ember-cracked bracers
    m.arms(bands(BASALT, (8, 10, OBS)))
    m.R(RA, [-10, 21, -3.75], [7, 4, 7], BASALT)
    for k, (z, L) in enumerate([(-2.5, 5), (0, 6), (2.5, 4)]):
        m.spike(RA, [-9, 24.5, z], L, OBS, [10 - k * 10, 0, 30], thick=2)
        m.spike("armorLeftArm", [9, 24.5, z], L, OBS, [10 - k * 10, 0, -30], thick=2)
    # legs + boots
    m.legs(bands(BASALT, (0, 1, OBS)))
    m.R(RL, [-4.5, 5.5, -3.25], [4, 4, 1], OBS)
    m.boots(bands(BASALT, (0, 1, OBS), (4, 5, OBS)), h=7)
    return m


# =====================================================================================
# CURSED BONE (ring 4)
# =====================================================================================
def cursed_bone():
    m = AR("cursed_bone", 1111)
    CLOTH = over(pal("#141410", "#1e1e18", "#2a2a20"), speckle("#2a4a30", 0.08))
    GHOST = lit("#3aff8a")
    TRIM = "#b8902e"
    # head: a bone skull-helm, green-burning eye sockets, curved horns, a tattered black hat + ghost flames
    skull = A(["BBBBBBBBBB", "BBBBBBBBBB", "BBBBBBBBBB", "B__BBBB__B", "B!!BBBB!!B", "B__BB_B__B",
               "BBBB__BBBB", "BTBTBTBTBB", "B_B_B_B_BB", "BBBBBBBBBB"], B="#c8b894", T="#efe6c8", **{"!": "!3aff8a"})
    m.helm(BONE, art={"north": skull})
    m.cube(H, [-4.5, 32.5, -4.5], [9, 2, 9], CLOTH)
    m.cube(H, [-6, 32.5, -6], [12, 1, 12], CLOTH, art={"up": A.at(12, 12, {**{(i, 0): "T" for i in range(12)}, **{(i, 11): "T" for i in range(12)}}, T=TRIM)})
    for side in (-1, 1):
        m.spike(H, [side * 5, 30, -1], 5, BONE, [0, 0, -side * 55], thick=2)
        m.spike(H, [side * 7.5, 33.5, -1], 3, BONE, [0, 0, -side * 15])
    for (x, z) in [(-3, -3), (3, -3), (-4, 3)]:
        m.cube(H, [x, 34.5, z], [1, 2, 1], GHOST)
    # chest: a bone ribcage over black cloth with ghost-light between the ribs, skull buckle, cloak
    ribs = {(x, y): "R" for y in (2, 4, 6, 8, 10) for x in list(range(0, 4)) + list(range(6, 10))}
    ribs.update({(x, y): "!" for y in (3, 5, 7, 9) for x in (1, 2, 7, 8)})
    ribs.update({(4, y): "R" for y in range(1, 12)}); ribs.update({(5, y): "R" for y in range(1, 12)})
    m.chest(CLOTH, art={"north": A.at(10, 14, ribs, R="#c8b894", **{"!": "!3aff8a"})})
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], pal("#2a1c12", "#3a2618"), art={"north": A.at(11, 2, {(5, 0): "S", (5, 1): "S", (4, 0): "S", (6, 0): "S"}, S="#e2d6b2")})
    m.cube(B, [-5, 1, 3.5], [10, 22, 1], over(CLOTH, drips("#2a6a3a", 0.3, faces=("south", "north"), maxlen=8)), art=m.rag([10, 22, 1], 6))
    m.cube(B, [-2.5, 16, 4.25], [5, 5, 1], GOLD, art={"sides": A(["G_G_G", "_GGG_", "GG_GG", "_GGG_", "G_G_G"], G=TRIM)})   # the ship's wheel
    # arms: skull pauldrons with glowing eyes, bone bands
    m.arms(bands(CLOTH, (1, 3, BONE), (5, 7, BONE), (9, 11, BONE)))
    m.R(RA, [-10, 21, -3.5], [7, 4, 7], BONE, art={"west": A(["BBBBBBB", "B!BB!BB", "BBBBBBB", "B_B_B_B"], B="#c8b894", **{"!": "!3aff8a"})})
    m.spike(RA, [-9.5, 24.5, 0], 3, BONE, [0, 0, 40]); m.spike("armorLeftArm", [9.5, 24.5, 0], 3, BONE, [0, 0, -40])
    # legs: bone bands on dark trousers, a tattered skirt
    m.legs(bands(CLOTH, (1, 2, BONE), (4, 5, BONE), (7, 8, BONE)))
    m.R(RL, [-4.75, 6, -3], [5, 6, 1], CLOTH, art=m.rag([5, 6, 1], 3))
    # boots: bone-clawed boots
    m.boots(bands(CLOTH, (1, 2, BONE), (4, 5, BONE)))
    for k in range(3):
        m.R(RB, [-4.75 + k * 2, 0, -4.25], [1, 1, 2], BONE)
    return m


# =====================================================================================
# KRAKEN SCALE (ring 5)
# =====================================================================================
def kraken_scale():
    m = AR("kraken_scale", 1212)
    SCALE = over(pal("#140e2a", "#1f1640", "#2c1f58", "#3a2a70"), speckle("#2a5a8a", 0.06), spots("#5ab4ff", 0.02, glow=True))
    TENT = over(pal("#3a1e4a", "#4e2a62", "#62367a"), spots("#c8a8d8", 0.1))
    BONEG = pal("#a89470", "#c8b48c", "#e0d0a8")
    CAPE = over(pal("#1a2448", "#223a5e", "#2a5070"), drips("#3a2a70", 0.3, maxlen=6))
    # head: scaled helm, glowing blue visor slits, bone trim swept back, tentacles hanging behind
    visor = A(["..........", "..........", "..........", ".G.G..G.G.", ".G.G..G.G.", ".G.G..G.G.",
               "..G....G..", "..........", "..........", ".........."], G="!5ab4ff")
    m.helm(SCALE, art={"north": visor})
    m.cube(H, [-1, 23, -5.5], [2, 10, 1], BONEG)
    m.spike(H, [-4.5, 32, -2], 6, BONEG, [60, 0, 20]); m.spike(H, [4.5, 32, -2], 6, BONEG, [60, 0, -20])
    for k, (x, L) in enumerate([(-4, 10), (-1, 13), (2, 12), (4.5, 9)]):        # tentacles down the back
        m.cube(H, [x - 0.75, 33 - L, 5 + (k % 2) * 0.6], [2, L, 2], TENT, rot=[-10 - k * 3, 0, (k - 1.5) * 6], pivot=[x, 33, 5])
    # chest: scale mail, bone-trimmed pauldrons, a glowing pearl clasp, belt with anchor, tentacle cape
    m.chest(SCALE)
    m.cube(B, [-1, 20, -3.75], [2, 2, 1], lit("#b8d8ff"))
    m.cube(B, [-5.5, 11.5, -3.5], [11, 2, 7], pal("#3a2416", "#4a2e1c"), art={"north": A.at(11, 2, buckle(11, 2, "G"), G="#d2a84a")})
    m.cube(B, [-5, 1, 3.5], [10, 22, 1], CAPE, art=m.rag([10, 22, 1], 7))
    for k, (x, L) in enumerate([(-4.5, 16), (-2, 20), (0.5, 14), (3, 19)]):
        m.cube(B, [x, 23 - L, 4.25 + (k % 2) * 0.5], [2, L, 1], TENT, rot=[-6, 0, (k - 1.5) * 4], pivot=[x + 1, 23, 4.5])
    m.cube(B, [-4, 4, -3.75], [8, 7, 1], CAPE, art=m.rag([8, 7, 1], 4))
    # arms: bone-trimmed spiked pauldrons with glowing spots, tentacle-wrapped bracers
    m.arms(bands(SCALE, (7, 12, TENT)))
    m.R(RA, [-10, 21, -3.75], [7, 4, 7], over(SCALE, border("#c8b48c", SIDES)))
    for k, z in enumerate((-2.5, 0, 2.5)):
        m.spike(RA, [-9.5, 24.5, z], 4, BONEG, [0, 0, 30 + k * 8])
        m.spike("armorLeftArm", [9.5, 24.5, z], 4, BONEG, [0, 0, -(30 + k * 8)])
    # legs: scale with tentacle shin guards, bone spurs
    m.legs(SCALE)
    m.R(RL, [-4.5, 4, -3.25], [4, 7, 1], TENT, art={"north": A(["T_TT", "TTOT", "TOTT", "TTTT", "TTOT", "TOTT", "TTTT"], T="#4e2a62", O="#e0c8e8")})
    m.spike(RL, [-4.75, 8, 0], 3, BONEG, [0, 0, 45]); m.spike("armorLeftLeg", [4.75, 8, 0], 3, BONEG, [0, 0, -45])
    # boots
    m.boots(bands(SCALE, (0, 1, BONEG), (4, 5, BONEG)))
    m.R(RB, [-5.5, 0, -3.75], [7, 2, 1], BONEG)
    return m


BUILDERS = {f.__name__: f for f in (powder_monkey, forgeguard, tidecourt, gallowbreaker, thalassar,
                                    pirate_armor, castaway, navy_officer, corsair, ashen, cursed_bone, kraken_scale)}
