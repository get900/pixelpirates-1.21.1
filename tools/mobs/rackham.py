"""CAPTAIN RACKHAM (boss 1/10) - hand-built rig, rebuilt 2026-09-29.

Crimson greatcoat with gold lapels and turned-back cuffs, three-cornered tricorn with a plume and a
skull badge, eyepatch, braided black beard with gold beads, a bandolier of dynamite across the chest,
a PEG LEG on the right, cutlass in the right hand, flintlock in the left, and Polly (a scarlet macaw)
on his left shoulder.

Two skins share ONE geometry (same cubes, same order -> identical box-UV layout):
  normal  - textures/entity/captain_rackham.png
  enraged - textures/entity/captain_rackham_enraged.png ("Powder-Mad", swapped in by
            GlowingMobRenderer while ModBoss#isEnraged): scorched coat with ember seams, glowing eye,
            lit dynamite fuses, a red-hot cutlass edge, embers in the beard.
Never add a cube to only one skin - the UVs would shift and the swapped texture would scramble.

Clips (ModMob contract + CaptainRackhamEntity): idle, move, attack, pistol, flurry, keg, polly, whistle,
barrage, broadside, powder_mad (enrage), grog (last-stand swig).
"""
from mobkit import A, Rig, bands, lit, noise, over, solid, spots, veins, drips, anim, keys, wave, S

CRIMSON = ["#4e0e12", "#5e1216", "#6e181c", "#7c1e22"]
SCORCHED = ["#2a0a0c", "#3a0e10", "#4a1214", "#26100c"]
GOLD = ["#a87a1e", "#c8962a", "#e0b84a"]
SOOT_GOLD = ["#6a4a18", "#806020", "#9a7428"]
TROUSER = ["#1c1618", "#241c1e", "#2c2224"]
BOOT = ["#141010", "#1e1816", "#2a221e"]
HAT = ["#141012", "#1c1618", "#241e20"]
WOOD = ["#5a3c22", "#6a4628", "#78512e"]
SKIN = ["#b88a60", "#c89a70", "#d0a478"]
BEARD = ["#141010", "#1e1816", "#261e1a"]


def captain_rackham(seed, skin="normal"):
    mad = skin == "enraged"
    m = Rig("captain_rackham", 64, 64, seed)

    coat = noise(SCORCHED) if mad else noise(CRIMSON)
    if mad:   # powder-burnt: soot blotches and glowing ember seams
        coat = over(coat, spots("#140808", 0.12, size=2), veins("#ff7a1a", 0.06, glow=True, length=(2, 5)))
    gold = noise(SOOT_GOLD) if mad else noise(GOLD)
    beard = over(noise(BEARD), spots("#ff9a3a", 0.06, glow=True)) if mad else noise(BEARD)
    fuse = lit("#ffd24a") if mad else solid("#2a2016")
    blade = over(noise(["#9aa0a8", "#b8bec6", "#c8ced6"]), spots("#ff8a2a", 0.25, glow=True)) if mad \
        else over(noise(["#9aa0a8", "#b8bec6", "#c8ced6"]), spots("#e6ecf2", 0.08))
    eye = "!ff8a2a" if mad else "#101010"

    # ------------------------------------------------------------------ faces
    P = dict(s="#c89a70", S="#a67a52", k="#141010", K="#2a2220", b="#1a1210", P="#0e0c0c", p="#2a2018",
             w="#ece6dc", E=eye, G=eye, n="#b07c54", h="#141010", H="#2c221c", m="#1a1412", r="#8a3a2e",
             o="#3a0a08", t="#e8e0d0", g="#e0b84a", R="#8a1e22")
    face = A("""
kkkkkkkk
SbbSSbbS
PPPsswEs
pPPsssss
ssSnnSss
hmmrrmmh
hhhHHhhh
hhhhhhhh""", P) if not mad else A("""
kkkkkkkk
bbbSSbbb
PPPssGGs
pPPsSsss
ssSnnSss
hmoooomh
hhtootth
hhhhhhhh""", P)
    left = A("""
kkkkkkkk
kkksssss
kkksswss
kkgsssss
kkSssnss
hhhsssss
hhhhhmss
hhhhhhhh""", P)                                   # east = character's LEFT (earring), front on the right
    right = A("""
kkkkkkkk
ssssspkk
sPssspkk
sssssppk
ssnSsskk
ssssshhh
ssmhhhhh
hhhhhhhh""", P)                                   # west = character's RIGHT (patch strap)
    back = A("""
kkkkkkkk
kkkkkkkk
kkkkkkkk
pppppppp
kkkkkkkk
kkkkkkkk
kkkRRkkk
kkkkkkkk""", P)

    C = dict(c=SCORCHED[2] if mad else "#6e181c", L="#8a6a20" if mad else "#e0b84a", W="#b8b0a0" if mad else "#e6e0d4",
             j="#d8d2c4" if mad else "#f4f0e8", G="#9a7428" if mad else "#f0c850", B="#1a1210", Y="#e0b84a", y="#8a6a22",
             T="#241c1e")
    chest = A("""
cLWjjWLc
cLWjjWLc
cLWWjWLc
cLWWWWLc
cGWWWWGc
cLWWWWLc
cGWWWWGc
cLWWWWLc
BBBYYBBB
BBBYyBBB
cLTTTTLc
cLTTTTLc""", C)
    back_coat = A("""
cccccccc
cccLLccc
ccccLccc
ccccLccc
cccccccc
cccccccc
cccccccc
cccccccc
BBBBBBBB
BBBBBBBB
ccccLccc
ccccLccc""", C)

    # ------------------------------------------------------------------ rig (standard skin proportions)
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 12, 0], "root")
    m.cube("body", [-4, 12, -2], [8, 12, 4], coat, art={"north": chest, "south": back_coat})
    m.bone("head", [0, 24, 0], "body")
    m.cube("head", [-4, 24, -4], [8, 8, 8], noise(SKIN), art={"north": face, "east": left, "west": right, "south": back,
                                                           "up": A("kkkkkkkk\n" * 7 + "kkkkkkkk", P)})
    # right leg: trouser thigh + wooden PEG with an iron band
    m.bone("rleg", [-2, 12, 0], "root")
    m.cube("rleg", [-4, 6, -2], [4, 6, 4], noise(TROUSER))
    m.cube("rleg", [-3, 0, -1], [2, 6, 2], over(noise(WOOD), veins("#3e2a16", 0.08, glow=False)))
    m.cube("rleg", [-3, 3, -1], [2, 1, 2], solid("#4a4e56"), inflate=0.3)
    m.cube("rleg", [-4, 5, -2], [4, 1, 4], noise(BOOT), inflate=0.2)                     # stump strapping
    # left leg: trouser + tall boot with a turned-down cuff and buckle
    m.bone("lleg", [2, 12, 0], "root")
    m.cube("lleg", [0, 0, -2], [4, 12, 4], bands(noise(TROUSER), (4, 12, noise(BOOT))),
           art={"north": A("....\n....\n....\n....\n....\n....\n....\n....\n....\n.gg.\n....\n....", g="#e0b84a")})
    m.cube("lleg", [0, 7, -2], [4, 2, 4], noise(BOOT), inflate=0.35)
    # arms: sleeves with big turned-back cuffs, skin hands
    arm = bands(coat, (10, 12, noise(SKIN)))
    m.bone("rarm", [-6, 22, 0], "body")
    m.cube("rarm", [-8, 12, -2], [4, 12, 4], arm)
    m.bone("larm", [6, 22, 0], "body")
    m.cube("larm", [4, 12, -2], [4, 12, 4], arm)
    cuff = over(coat, drips("#e0b84a" if not mad else "#9a7428", 1.0, maxlen=1))
    m.pair("rarm", "larm", [-8, 14, -2], [4, 4, 4], cuff, inflate=0.45)
    # epaulettes: plate + fringe
    m.pair("rarm", "larm", [-8.5, 24, -2.5], [5, 1, 5], gold)                      # sits ON the arm top (no shared plane)
    m.pair("rarm", "larm", [-8.5, 22, -2.5], [5, 2, 5], over(gold, drips("#5a4010", 0.5, maxlen=2)), inflate=0.1)
    m.bone("rhand", [-6, 13, 0], "rarm")
    m.bone("lhand", [6, 13, 0], "larm")

    # ------------------------------------------------------------------ greatcoat skirt (own bone)
    m.bone("coat", [0, 12, 0], "body")
    m.pair("coat", "coat", [-4.2, 3, -2.6], [3, 9, 1], coat,
           art={"north": A("cL.\ncL.\ncL.\ncL.\ncL.\ncL.\ncL.\ncL.\nLLL".replace(".", "c"), C)})
    m.pair("coat", "coat", [-4.7, 2.5, -2.2], [1, 9, 4], coat)
    m.cube("coat", [-4.5, 2, 2.1], [9, 10, 1], coat,
           art={"south": A("LcccLcccL\nLcccLcccL\nLcccLcccL\nLcccLcccL\nLccc_cccL\nLccc_cccL\nLccc_cccL\nLccc_cccL\nLccc_cccL\nLLLL_LLLL", C)})

    # ------------------------------------------------------------------ beard + braids
    m.cube("head", [-3, 21, -4.6], [6, 4, 1], beard)
    m.cube("head", [-2, 19, -4.5], [4, 2, 1], beard)
    for x in (-2.5, 1.5):
        m.cube("head", [x, 16, -4.4], [1, 4, 1], beard)
        m.cube("head", [x, 15.2, -4.4], [1, 1, 1], lit("#e0b84a") if not mad else lit("#ffb84a"), inflate=0.15)
    m.cube("head", [4.2, 26, -1], [1, 1, 1], lit("#e0b84a"), inflate=-0.2)               # earring (character's left)

    # ------------------------------------------------------------------ tricorn
    m.bone("hat", [0, 32, 0], "head")
    H = noise(HAT) if not mad else over(noise(HAT), spots("#ff6a1a", 0.05, glow=True))
    trim = dict(g="#e0b84a" if not mad else "#9a7428", k="#141012")
    m.cube("hat", [-4.5, 32, -4.5], [9, 3, 9], H, inflate=0.1)
    m.cube("hat", [-6.5, 32, 4.2], [13, 3, 1], H, rot=[-18, 0, 0], pivot=[0, 32, 4.7],
           art={"south": A("g" * 13 + "\n" + "k" * 13 + "\n" + "k" * 13, trim), "north": A("g" * 13 + "\n" + "k" * 13 + "\n" + "k" * 13, trim),
                "up": A("g" * 13, trim)})
    m.pair("hat", "hat", [-0.9, 32, -8.2], [1, 3, 13], H, rot=[0, -27, -14], pivot=[-0.4, 32, -7.6],
           art={"east": A("g" * 13 + "\n" + "k" * 13 + "\n" + "k" * 13, trim), "west": A("g" * 13 + "\n" + "k" * 13 + "\n" + "k" * 13, trim),
                "up": A("g" * 13, trim)})
    m.cube("hat", [-1, 32.5, -8.9], [2, 2, 1], solid("#e6e0d4"),
           art={"north": A("ww\nkk", dict(w="#e6e0d4", k="#141012"))})                    # skull badge on the point
    # plume on the character's left
    m.cube("hat", [4, 33, 1], [1, 7, 2], noise(["#e6e0d4", "#f4f0e8"]) if not mad else noise(["#6a625a", "#7a7068"]),
           rot=[-35, 0, -20], pivot=[4.5, 33, 2])
    m.cube("hat", [4, 40, 1], [1, 2, 2], noise(["#a01818", "#c02020"]) if not mad else lit("#ff7a1a"),
           rot=[-35, 0, -20], pivot=[4.5, 33, 2])

    # ------------------------------------------------------------------ bandolier of dynamite
    m.bone("bandolier", [0, 18, -2.5], "body", rotation=[0, 0, -38])
    m.cube("bandolier", [-1, 10, -2.9], [2, 16, 1], noise(["#2a1a12", "#3a2618"]))
    for y in (12, 16.5, 21):
        m.cube("bandolier", [-0.5, y, -4], [1, 3, 1], noise(["#a01818", "#c02a1e"]) if not mad else noise(["#b02018", "#d0301e"]))
        m.cube("bandolier", [-0.5, y + 3, -4], [1, 1, 1], fuse, inflate=-0.25)

    # ------------------------------------------------------------------ cutlass (right) + flintlock (left)
    pv = [-6, 13, 0]
    tilt = [-50, 0, 0]
    m.cube("rhand", [-6.5, 12, -1], [1, 3, 2], solid("#3a2616"), rot=tilt, pivot=pv)
    m.cube("rhand", [-7.5, 11.5, -3], [3, 3, 1], gold, rot=tilt, pivot=pv)                 # basket guard
    m.cube("rhand", [-6.5, 12, -16], [1, 2, 13], blade, rot=tilt, pivot=pv)
    m.cube("rhand", [-6.5, 14, -15], [1, 1, 2], blade, rot=tilt, pivot=pv)               # swept tip
    pl = [6, 13, 0]
    ptilt = [75, 0, 0]
    m.cube("lhand", [5.5, 11, -1.5], [1, 3, 2], noise(WOOD), rot=ptilt, pivot=pl)            # stock
    m.cube("lhand", [5.5, 12.5, -9.5], [1, 1, 8], solid("#3a3e46"), rot=ptilt, pivot=pl)       # barrel
    m.cube("lhand", [5.5, 12.5, -6], [1, 1, 1], gold, inflate=0.2, rot=ptilt, pivot=pl)      # brass band
    m.cube("lhand", [5.5, 13.5, -2.5], [1, 1, 1], solid("#2a2e36"), rot=ptilt, pivot=pl)       # hammer

    # ------------------------------------------------------------------ Polly (scarlet macaw, left shoulder)
    RED = ["#b01818", "#c82020", "#d82a22"]
    BLUE = ["#1e4aa0", "#2a5ac0"]
    m.bone("parrot", [6, 25, 0], "body")
    m.cube("parrot", [4.5, 25.5, -1.5], [3, 4, 3], noise(RED))
    m.cube("parrot", [5.5, 21.5, 1], [1, 5, 1], bands(noise(RED), (2, 5, noise(BLUE))), rot=[-25, 0, 0], pivot=[6, 25.5, 1.5])
    m.cube("parrot", [4.8, 25, -0.8], [1, 1, 1], solid("#5a5a5a"), inflate=-0.2)
    m.cube("parrot", [6.2, 25, -0.8], [1, 1, 1], solid("#5a5a5a"), inflate=-0.2)
    m.bone("parrot_head", [6, 29.5, -0.5], "parrot")
    m.cube("parrot_head", [4.5, 29.5, -2], [3, 3, 3], noise(RED),
           art={"east": A("rrr\nwer\nrrr", dict(r="#c82020", w="#f4f0e8", e="#101010")),
                "west": A("rrr\nrew\nrrr", dict(r="#c82020", w="#f4f0e8", e="#101010"))})
    m.cube("parrot_head", [5.5, 29.5, -3.2], [1, 2, 1], solid("#e8e0d0"),
           art={"north": A("w\nk", dict(w="#e8e0d0", k="#2a2420"))})
    m.bone("pwing_r", [4.5, 29, 0], "parrot")
    m.cube("pwing_r", [3.5, 25.5, -1.5], [1, 4, 3], bands(noise(RED), (1, 3, noise(["#e0b020", "#f0c830"])), (3, 4, noise(BLUE))))
    m.bone("pwing_l", [7.5, 29, 0], "parrot")
    m.cube("pwing_l", [7.5, 25.5, -1.5], [1, 4, 3], bands(noise(RED), (1, 3, noise(["#e0b020", "#f0c830"])), (3, 4, noise(BLUE))))

    animations(m)
    return m


# =====================================================================================
# clips - rest pose lives on the bones; every key is an OFFSET
# =====================================================================================
SWORD = -25     # sword arm carried forward
PISTOL = -20    # pistol arm


def animations(m):
    anim(m, "idle", 3.0, {
        "body": {"rotation": wave(3.0, lambda q: [S(q) * 1.2, 0, 0])},
        "head": {"rotation": wave(3.0, lambda q: [S(q) * 2.5, S(q * 0.5) * 12, 0])},
        "rarm": {"rotation": wave(3.0, lambda q: [SWORD - S(q) * 3, 0, 3 + S(q) * 2])},
        "larm": {"rotation": wave(3.0, lambda q: [PISTOL + S(q) * 3, 0, -4])},
        "coat": {"rotation": wave(3.0, lambda q: [3 + S(q) * 2, 0, 0])},
        "parrot_head": {"rotation": wave(3.0, lambda q: [S(q * 2) * 12, S(q) * 30, 0])},
        "parrot": {"position": wave(3.0, lambda q: [0, max(0.0, S(q * 3)) * 0.4, 0])},
        "pwing_l": {"rotation": wave(3.0, lambda q: [0, 0, -max(0.0, S(q * 3)) * 18])},
        "pwing_r": {"rotation": wave(3.0, lambda q: [0, 0, max(0.0, S(q * 3)) * 18])},
    })
    # walking on a peg: the wooden leg swings stiffly and short, the body lurches onto it
    anim(m, "move", 1.0, {
        "rleg": {"rotation": wave(1.0, lambda q: [-S(q) * 20, 0, 0])},
        "lleg": {"rotation": wave(1.0, lambda q: [S(q) * 32, 0, 0])},
        "body": {"rotation": wave(1.0, lambda q: [3, S(q) * 4, S(q) * 5]),
                 "position": wave(1.0, lambda q: [0, -abs(S(q)) * 0.7, 0])},
        "head": {"rotation": wave(1.0, lambda q: [-2, -S(q) * 4, -S(q) * 4])},
        "rarm": {"rotation": wave(1.0, lambda q: [SWORD + S(q) * 18, 0, 4])},
        "larm": {"rotation": wave(1.0, lambda q: [PISTOL - S(q) * 18, 0, -4])},
        "coat": {"rotation": wave(1.0, lambda q: [12 + abs(S(q)) * 12, 0, 0])},
        "parrot_head": {"rotation": wave(1.0, lambda q: [S(q * 2) * 10, 0, 0])},
        "pwing_l": {"rotation": wave(1.0, lambda q: [0, 0, -abs(S(q * 2)) * 25])},
        "pwing_r": {"rotation": wave(1.0, lambda q: [0, 0, abs(S(q * 2)) * 25])},
    })
    # overhead cutlass chop
    anim(m, "attack", 0.5, {
        "rarm": {"rotation": keys((0, [SWORD, 0, 0]), (0.15, [-160, 10, 25]), (0.3, [-20, -20, -5]), (0.5, [SWORD, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.15, [-5, 20, 0]), (0.3, [9, -15, 0]), (0.5, [0, 0, 0]))},
    }, loop=False)
    # flintlock: raise and aim (0.35), hold, BANG at 0.7 (windup 14 ticks) with recoil
    anim(m, "pistol", 1.1, {
        "larm": {"rotation": keys((0, [PISTOL, 0, 0]), (0.35, [-92, -12, 0]), (0.68, [-92, -12, 0]), (0.76, [-118, -12, 0]),
                                  (0.9, [-95, -12, 0]), (1.1, [PISTOL, 0, 0]))},
        "lhand": {"rotation": keys((0, [0, 0, 0]), (0.35, [18, 0, 0]), (0.9, [18, 0, 0]), (1.1, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.35, [0, -22, 0]), (0.7, [0, -22, 0]), (0.76, [-5, -24, 0]), (1.1, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.35, [4, 18, 0]), (0.9, [4, 18, 0]), (1.1, [0, 0, 0]))},
        "parrot": {"rotation": keys((0, [0, 0, 0]), (0.7, [0, 0, 0]), (0.78, [-20, 0, 12]), (1.0, [0, 0, 0]))},
    }, loop=False)
    # cutlass flurry: forehand at 0.3, backhand at 0.55, overhead finisher at 0.8
    anim(m, "flurry", 1.2, {
        "rarm": {"rotation": keys((0, [SWORD, 0, 0]), (0.18, [-140, 30, 40]), (0.3, [-40, -35, -25]), (0.42, [-110, -40, -35]),
                                  (0.55, [-45, 35, 25]), (0.68, [-170, 0, 10]), (0.8, [-15, 0, 0]), (1.2, [SWORD, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.18, [0, 30, 0]), (0.3, [6, -25, 0]), (0.42, [0, -30, 0]), (0.55, [6, 25, 0]),
                                  (0.68, [-10, 0, 0]), (0.8, [16, 0, 0]), (1.2, [0, 0, 0]))},
        "root": {"position": keys((0, [0, 0, 0]), (0.2, [0, 0, -2]), (0.8, [0, 0, -3]), (1.2, [0, 0, 0]))},
        "larm": {"rotation": keys((0, [PISTOL, 0, 0]), (0.3, [-10, 0, -30]), (0.8, [-10, 0, -30]), (1.2, [PISTOL, 0, 0]))},
    }, loop=False)
    # powder keg: stoop, grab, heave it rolling at 0.8
    anim(m, "keg", 1.2, {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.45, [32, 0, 0]), (0.8, [22, 0, 0]), (1.2, [0, 0, 0]))},
        "rarm": {"rotation": keys((0, [SWORD, 0, 0]), (0.45, [-40, 0, 20]), (0.8, [-95, 0, 10]), (1.2, [SWORD, 0, 0]))},
        "larm": {"rotation": keys((0, [PISTOL, 0, 0]), (0.45, [-40, 0, -20]), (0.8, [-95, 0, -10]), (1.2, [PISTOL, 0, 0]))},
        "rleg": {"rotation": keys((0, [0, 0, 0]), (0.45, [-20, 0, 0]), (1.2, [0, 0, 0]))},
        "lleg": {"rotation": keys((0, [0, 0, 0]), (0.45, [25, 0, 0]), (1.2, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.45, [-25, 0, 0]), (1.2, [0, 0, 0]))},
    }, loop=False)
    # Polly! - he points, she launches off the shoulder at 0.6 (windup 12), pecks, flies home
    flap = keys((0, [0, 0, 0]), *[(i * 0.1, [0, 0, (-50 if i % 2 else 10)]) for i in range(3, 11)], (1.2, [0, 0, 0]))
    flap_r = keys((0, [0, 0, 0]), *[(i * 0.1, [0, 0, (50 if i % 2 else -10)]) for i in range(3, 11)], (1.2, [0, 0, 0]))
    anim(m, "polly", 1.2, {
        "larm": {"rotation": keys((0, [PISTOL, 0, 0]), (0.25, [-100, -10, 0]), (0.8, [-100, -10, 0]), (1.2, [PISTOL, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.25, [0, 20, 0]), (0.9, [0, 20, 0]), (1.2, [0, 0, 0]))},
        "parrot": {"position": keys((0, [0, 0, 0]), (0.3, [0, 4, -4]), (0.6, [0, 2, -16]), (0.8, [0, 6, -10]), (1.1, [0, 2, -2]), (1.2, [0, 0, 0])),
                   "rotation": keys((0, [0, 0, 0]), (0.3, [20, 0, 0]), (0.6, [35, 0, 0]), (0.8, [-10, 0, 0]), (1.2, [0, 0, 0]))},
        "parrot_head": {"rotation": keys((0, [0, 0, 0]), (0.55, [0, 0, 0]), (0.62, [35, 0, 0]), (0.7, [-10, 0, 0]), (1.2, [0, 0, 0]))},
        "pwing_l": {"rotation": flap},
        "pwing_r": {"rotation": flap_r},
    }, loop=False)
    # bosun's whistle: hand to mouth, then wave the crew on with the cutlass
    anim(m, "whistle", 1.3, {
        "larm": {"rotation": keys((0, [PISTOL, 0, 0]), (0.25, [-145, 0, -35]), (0.8, [-145, 0, -35]), (1.0, [PISTOL, 0, 0]), (1.3, [PISTOL, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.25, [-12, 0, 0]), (0.8, [-12, 0, 0]), (1.3, [0, 0, 0]))},
        "rarm": {"rotation": keys((0, [SWORD, 0, 0]), (0.8, [SWORD, 0, 0]), (0.95, [-170, 0, 20]), (1.1, [-150, 0, -10]), (1.3, [SWORD, 0, 0]))},
    }, loop=False)
    # dynamite barrage: wind back, lob overhand at 0.6 (windup 12)
    anim(m, "barrage", 1.1, {
        "larm": {"rotation": keys((0, [PISTOL, 0, 0]), (0.35, [-175, 0, -15]), (0.6, [-45, 0, -5]), (0.8, [-20, 0, 0]), (1.1, [PISTOL, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.35, [-8, -25, 0]), (0.6, [12, 20, 0]), (1.1, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.35, [-10, 15, 0]), (0.6, [5, -10, 0]), (1.1, [0, 0, 0]))},
    }, loop=False)
    # BROADSIDE: cutlass raised to the sky, then chopped down at the target at 0.8 (windup 16)
    anim(m, "broadside", 1.5, {
        "rarm": {"rotation": keys((0, [SWORD, 0, 0]), (0.4, [-175, 0, 15]), (0.72, [-175, 0, 15]), (0.85, [-85, 0, 0]),
                                  (1.2, [-85, 0, 0]), (1.5, [SWORD, 0, 0]))},
        "larm": {"rotation": keys((0, [PISTOL, 0, 0]), (0.4, [-20, 0, -40]), (1.2, [-20, 0, -40]), (1.5, [PISTOL, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-30, 0, 0]), (0.85, [8, 0, 0]), (1.5, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [-10, 0, 0]), (0.85, [12, 0, 0]), (1.5, [0, 0, 0]))},
    }, loop=False)
    # POWDER-MAD (enrage): hunch, then a roar with arms flung wide; the hat pops
    anim(m, "powder_mad", 1.6, {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [22, 0, 0]), (0.7, [-18, 0, 0]), (1.2, [-18, 0, 0]), (1.6, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [20, 0, 0]), (0.7, [-32, 0, 0]), (1.2, [-32, 0, 0]), (1.6, [0, 0, 0]))},
        "rarm": {"rotation": keys((0, [SWORD, 0, 0]), (0.3, [-20, 0, 10]), (0.7, [-60, 0, 75]), (1.2, [-60, 0, 75]), (1.6, [SWORD, 0, 0]))},
        "larm": {"rotation": keys((0, [PISTOL, 0, 0]), (0.3, [-20, 0, -10]), (0.7, [-60, 0, -75]), (1.2, [-60, 0, -75]), (1.6, [PISTOL, 0, 0]))},
        "hat": {"position": keys((0, [0, 0, 0]), (0.7, [0, 3, 0]), (0.85, [0, 0, 0]), (1.6, [0, 0, 0])),
                "rotation": keys((0, [0, 0, 0]), (0.7, [-15, 0, 10]), (0.85, [0, 0, 0]), (1.6, [0, 0, 0]))},
        "parrot": {"position": keys((0, [0, 0, 0]), (0.7, [0, 5, 2]), (1.2, [0, 3, 0]), (1.6, [0, 0, 0]))},
        "pwing_l": {"rotation": keys((0, [0, 0, 0]), (0.7, [0, 0, -60]), (0.8, [0, 0, 0]), (0.9, [0, 0, -60]), (1.0, [0, 0, 0]), (1.6, [0, 0, 0]))},
        "pwing_r": {"rotation": keys((0, [0, 0, 0]), (0.7, [0, 0, 60]), (0.8, [0, 0, 0]), (0.9, [0, 0, 60]), (1.0, [0, 0, 0]), (1.6, [0, 0, 0]))},
    }, loop=False)
    # last stand: a long pull of grog, head thrown back
    anim(m, "grog", 1.4, {
        "larm": {"rotation": keys((0, [PISTOL, 0, 0]), (0.35, [-150, 0, -30]), (1.0, [-150, 0, -30]), (1.4, [PISTOL, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.35, [-35, 0, 0]), (1.0, [-35, 0, 0]), (1.4, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.35, [-8, 0, 0]), (1.0, [-8, 0, 0]), (1.4, [0, 0, 0]))},
    }, loop=False)
