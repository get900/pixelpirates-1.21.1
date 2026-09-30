"""ABYSSAL KING (boss 5/10) and his TIDE WARDENS - rebuilt 2026-09-29 from the concept render.

The drowned sovereign of the Sunken Court: tall teal plate armour, a great helm with one blazing cyclopean eye,
a jagged crown of teal spires and bleached driftwood, pauldrons overgrown with driftwood + kelp, a glowing tide
core in the chest, a brown war belt and knee bands, a tattered kelp cape (cape > cape2), a trident taller than he
is (bone `trident`, prongs glow) and a kite shield on the left arm (bone `shield`).
~46 px of model x renderScale 1.75 = ~5 blocks in game (hitbox 1.6 x 4.6 in MobSpecs).

Skins (one geometry, tools/gen_mob_roster.py SKINS checks the UVs):
  normal   - teal armour, cyan glow
  enraged  - "Wrath of the Deep": abyssal navy plate split by violet veins, magenta-white eye and core
  stranded - the hall drained: salt-crusted, sun-bleached grey-green, every light out (no glowmask)
Clips: idle, move, attack, riptide, pressure, reseal, throw, summon, decree, maelstrom, undertow, tide_call,
stranded, sweep, wrath (enrage).

Tide Warden: a drowned knight of the court (2.1 blocks): barbute helm with a glowing visor slit, prismarine
scale armour, trident + round buckler. Clips: idle, move, attack, seal (hands on the sluice wheel).
"""
from mobkit import A, Rig, lit, noise, over, solid, spots, veins, anim, keys, wave, S
from mobs.common import biped, biped_anims


def palette(skin):
    if skin == "enraged":
        return dict(ARM=["#141a2e", "#1a2240", "#20284a", "#283056"], vein=("#c070ff", True), eye="!#ffffff", core="!#ff7af0",
                    glow="!#c070ff", weed=["#2a1a2a", "#3a2236", "#4a2a44"], drift=["#8a7a9a", "#9a8aaa"], trim=["#6a4a9a", "#7a58aa"],
                    prong=lit("#ff9af4"), belt=["#1e1420", "#2a1a2a"])
    if skin == "stranded":
        return dict(ARM=["#4a5a56", "#56645e", "#627068", "#6e7a72"], vein=("#3a4642", False), eye="#2a3432", core="#6a8a86",
                    glow="#7a948e", weed=["#5a4a36", "#6a5a42", "#7a6a4e"], drift=["#c8c0b0", "#d6d0c2"], trim=["#6e7a72", "#7a8680"],
                    prong=noise(["#8a9a94", "#9aaaa4"]), belt=["#4a3e30", "#5a4c3a"])
    return dict(ARM=["#12383a", "#184446", "#1e5052", "#265c5e"], vein=("#2ab8a8", True), eye="!#caffff", core="!#4ae8e0",
                glow="!#6fe8d0", weed=["#3a2e1e", "#4a3a26", "#5a4630"], drift=["#8a7e68", "#a09478"], trim=["#2a6a66", "#347a74"],
                prong=lit("#8affe8"), belt=["#2a2016", "#3a2c1e"])


def abyssal_king(seed, skin="normal"):
    P = palette(skin)
    stranded = skin == "stranded"
    m = Rig("abyssal_king", 128, 128, seed)
    salt = spots("#e6e2d6", 0.14) if stranded else spots(P["ARM"][3], 0.04)
    armor = lambda: over(noise(P["ARM"]), veins(P["vein"][0], 0.035 if skin == "enraged" else 0.012, glow=P["vein"][1], length=(2, 5)), salt)
    trim = noise(P["trim"])
    weed = over(noise(P["weed"]), spots(P["weed"][2], 0.12))
    drift = noise(P["drift"])
    belt = noise(P["belt"])

    m.bone("root", [0, 0, 0])
    # ------------------------------------------------------------------ legs: greave, brown knee band, heavy boot
    for side, x0, pv in (("rleg", -5.5, -2.75), ("lleg", 0.5, 2.75)):
        m.bone(side, [pv, 14, 0], "root")
        m.cube(side, [x0, 3, -2.5], [5, 11, 5], armor())
        m.cube(side, [x0, 7, -2.5], [5, 2, 5], belt, inflate=0.4)                          # knee band
        m.cube(side, [x0 - 0.5, 0, -3.5], [6, 3, 7], over(noise(P["ARM"][:2]), salt))     # boot
    # ------------------------------------------------------------------ torso: plate with the tide core
    m.bone("body", [0, 14, 0], "root")
    a0 = P["ARM"]
    V = dict(a=a0[2], d=a0[0], c=P["core"], g=P["glow"], t=P["trim"][1])
    m.cube("body", [-6, 14, -3.5], [12, 14, 7], armor(),
           art={"north": A("""
aattaaaattaa
adaataataada
aadaaaaaadaa
aaadaccadaaa
aaaaccccaaaa
aaaaccccaaaa
aaaadccdaaaa
aaaaaddaaaaa
aadaaggaadaa
aaadaggadaaa
aaaaaaaaaaaa
aaaaaaaaaaaa
aaaaaaaaaaaa
aaaaaaaaaaaa""", V)})
    m.cube("body", [-6, 14, -3.5], [12, 2, 7], belt, inflate=0.5)                          # war belt
    m.cube("body", [-1.5, 14, -4.6], [3, 2, 1], noise(P["drift"]), inflate=0.2)             # bone buckle
    m.cube("body", [-5, 8, -4.2], [10, 6, 1], weed,                                          # kelp + leather tassets
           art={"north": A("wwwwwwwwww\nwwwwwwwwww\nwwwwwwwwww\nw_ww_www_w\n__w__w_w__\n_____w____", w=P["weed"][1])})
    m.pair("body", "body", [-6.8, 9, -3], [1, 5, 6], weed)
    # ------------------------------------------------------------------ kelp cape, two segments
    m.bone("cape", [0, 28, 3.5], "body")
    frayed = A("kkkkkkkkkkkk\nkkkkkkkkkkkk\nkkkkkkkkkkkk\nkkkkkkkkkkkk\nkkkkkkkkkkkk\nkkkkkkkkkkkk\nkkkkkkkkkkkk\nkkkkkkkkkkkk\n"
               "kkkkkkkkkkkk\nkkkkkkkkkkkk\nkkkkkkkkkkkk", k=P["weed"][1])
    m.cube("cape", [-6, 17, 3.6], [12, 11, 1], weed, art={"south": frayed, "north": frayed})
    m.bone("cape2", [0, 17, 4.1], "cape")
    tail = A("kkkkkkkkkkkk\nkkkkkkkkkkkk\nkkkkkkkkkkkk\nkkkkkkkkkkkk\nkkkkkkkkkkkk\nkkkkkkkkkkkk\nkk_kkkk_kkkk\n"
             "k__kk_k__kk_\n___k__k___k_\n___k______k_", k=P["weed"][0])
    m.cube("cape2", [-6, 7, 3.6], [12, 10, 1], weed, art={"south": tail, "north": tail})
    # ------------------------------------------------------------------ head: great helm with one blazing eye
    m.bone("head", [0, 28, 0], "body")
    H = dict(a=a0[2], d=a0[0], k="#081414" if not stranded else "#2a3230", E=P["eye"], e=P["core"], t=P["trim"][1])
    m.cube("head", [-5, 28, -5], [10, 10, 10], armor(),
           art={"north": A("""
aaaaaaaaaa
atttttttta
aakkkkkkaa
akkeEEekka
akkEEEEkka
akkeEEekka
aakkkkkkaa
adakkkkada
aadakkadaa
aaadaadaaa""", H)})
    m.cube("head", [-3, 28, -6], [6, 3, 1], armor())                                     # jaw guard
    # crown: a rim and a ragged ring of teal spires + bleached driftwood, gems glowing between
    m.bone("crown", [0, 38, 0], "head")
    m.cube("crown", [-5.5, 38, -5.5], [11, 2, 11], trim, inflate=0.1)
    spires = [(-5, -5.5, 5, 0), (-3, -5.5, 3, 1), (-1, -5.5, 7, 0), (1.5, -5.5, 4, 1), (3.5, -5.5, 6, 0),
              (4.5, -2, 4, 1), (4.5, 1.5, 5, 0), (4.5, 4.5, 3, 1), (2, 4.5, 5, 0), (-1, 4.5, 4, 1), (-3.5, 4.5, 6, 0),
              (-5.5, 3, 3, 1), (-5.5, 0, 5, 0), (-5.5, -3, 4, 1)]
    for (x, z, h, kind) in spires:
        m.cube("crown", [x, 40, z], [1, h, 1], trim if kind == 0 else drift)
    for (x, z) in ((-2, -5.9), (2.5, -5.9), (5.6, 0), (-6.1, 0)):
        m.cube("crown", [x, 38.5, z], [1, 1, 0.3], lit(P["core"].lstrip("!")) if not stranded else solid(P["core"]))
    # ------------------------------------------------------------------ arms: plate, pauldron clumps, gauntlets
    for side, x0, pv, sx in (("rarm", -11, -8.5, -1), ("larm", 6, 8.5, 1)):
        m.bone(side, [pv, 26, 0], "body")
        m.cube(side, [x0, 13, -2.5], [5, 13, 5], armor())
        m.cube(side, [x0 - 0.5, 12, -3], [6, 4, 6], over(noise(P["ARM"][:2]), salt))        # gauntlet
        m.cube(side, [x0 - 1, 23, -3.5], [7, 5, 7], armor())                                # pauldron
        m.cube(side, [x0 - 1, 23, -3.5], [7, 1, 7], trim, inflate=0.2)
        for k, (dx, dz, h, mat) in enumerate(((0, -3, 4, drift), (2, -1, 6, weed), (4, 1, 3, drift), (1, 2, 5, weed), (5, -2, 4, weed))):
            px = x0 - 1 + dx if sx < 0 else x0 + 6 - dx
            m.cube(side, [px, 28, dz], [1, h, 1], mat, rot=[0, 0, -sx * (10 + 6 * k)], pivot=[px + 0.5, 28, dz + 0.5])
    # ------------------------------------------------------------------ the trident
    m.bone("rhand", [-8.5, 14, 0], "rarm")
    m.bone("trident", [-8.5, 14, 0], "rhand")
    SH = noise(["#2a2016", "#3a2c1e"]) if not stranded else noise(["#5a4c3a", "#6a5a46"])
    m.cube("trident", [-9, -6, -0.5], [1, 42, 1], SH)
    m.cube("trident", [-9.5, 11, -1], [2, 5, 2], belt, inflate=0.1)                         # grip wrap
    m.cube("trident", [-12, 38, -0.5], [7, 1, 1], trim)                                     # crossbar
    m.cube("trident", [-9.5, 36, -1], [2, 2, 2], trim)                                      # socket
    for (x, h) in ((-12, 6), (-9, 9), (-6, 6)):
        m.cube("trident", [x, 39, -0.5], [1, h, 1], trim)
        m.cube("trident", [x, 39 + h, -0.5], [1, 1, 1], P["prong"])
    m.cube("trident", [-13, 42, -0.5], [1, 1, 1], trim)                                     # outer barbs
    m.cube("trident", [-5, 42, -0.5], [1, 1, 1], trim)
    m.cube("trident", [-9, -7, -0.5], [1, 1, 1], P["prong"])                                # butt spike
    # ------------------------------------------------------------------ kite shield on the left forearm
    m.bone("shield", [11, 18, -3], "larm")
    kite = {}
    for y in range(18):
        half = 6 if y < 11 else max(0.5, 6 - (y - 10) * 0.85)
        for x in range(12):
            if abs(x - 5.5) > half:
                kite[(x, y)] = "_"
            elif abs(x - 5.5) > half - 1 or y == 0:
                kite[(x, y)] = "r"
    for y in range(3, 14):
        kite[(5, y)] = "g"; kite[(6, y)] = "g"
    for x in range(3, 9):
        kite[(x, 6)] = "g"
    shield_face = A.at(12, 18, kite, r=P["trim"][1], g=P["glow"])
    m.cube("shield", [7, 5, -5], [12, 18, 1], armor(), rot=[0, -40, 0], pivot=[11, 14, -4.5],
           art={"north": shield_face, "south": A.at(12, 18, {k: v for k, v in kite.items() if v == "_"})})

    animations(m)
    return m


# rest: trident arm slightly forward so the shaft stands upright beside him; shield arm across the body
RA = [-14, 0, 6]
LA = [-22, 0, -6]


def ra(v):
    return [v[0], v[1], v[2]]


def animations(m):
    anim(m, "idle", 4.0, {
        "body": {"rotation": wave(4.0, lambda q: [S(q) * 1.2, 0, 0])},
        "head": {"rotation": wave(4.0, lambda q: [S(q) * 2, S(q * 0.5) * 7, 0])},
        "rarm": {"rotation": wave(4.0, lambda q: [RA[0] + S(q) * 2, 0, RA[2]])},
        "larm": {"rotation": wave(4.0, lambda q: [LA[0] - S(q) * 2, 0, LA[2]])},
        "cape": {"rotation": wave(4.0, lambda q: [8 + S(q) * 4, 0, S(q * 0.5) * 2])},
        "cape2": {"rotation": wave(4.0, lambda q: [6 + S(q - 0.8) * 6, 0, 0])},
    })
    anim(m, "move", 1.4, {
        "rleg": {"rotation": wave(1.4, lambda q: [-S(q) * 26, 0, 0])},
        "lleg": {"rotation": wave(1.4, lambda q: [S(q) * 26, 0, 0])},
        "body": {"rotation": wave(1.4, lambda q: [4, S(q) * 3, 0]), "position": wave(1.4, lambda q: [0, -abs(S(q)) * 0.7, 0])},
        "rarm": {"rotation": wave(1.4, lambda q: [RA[0] + S(q) * 10, 0, RA[2]])},
        "larm": {"rotation": wave(1.4, lambda q: [LA[0] - S(q) * 12, 0, LA[2]])},
        "head": {"rotation": wave(1.4, lambda q: [-3, -S(q) * 3, 0])},
        "cape": {"rotation": wave(1.4, lambda q: [22 + S(q * 2) * 5, 0, 0])},
        "cape2": {"rotation": wave(1.4, lambda q: [14 + S(q * 2 - 1) * 8, 0, 0])},
    })
    # trident jab
    anim(m, "attack", 0.6, {
        "rarm": {"rotation": keys((0, RA), (0.18, [-40, 0, 14]), (0.3, [-92, 0, 0]), (0.6, RA))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.18, [0, 22, 0]), (0.3, [8, -14, 0]), (0.6, [0, 0, 0]))},
    }, loop=False)
    # RIPTIDE CHARGE: 0-0.7 coiled back (telegraph), 0.7-1.1 rockets forward spinning, 1.1-1.5 recovers
    anim(m, "riptide", 1.5, {
        "rarm": {"rotation": keys((0, RA), (0.5, [-40, 0, 25]), (0.7, [-170, 0, 0]), (1.1, [-170, 0, 0]), (1.5, RA))},
        "larm": {"rotation": keys((0, LA), (0.7, [10, 0, -20]), (1.1, [10, 0, -20]), (1.5, LA))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [-10, 30, 0]), (0.7, [62, 0, 0]), (0.9, [62, 0, 360]), (1.1, [62, 0, 720]), (1.5, [0, 0, 720])),
                 "position": keys((0, [0, 0, 0]), (0.7, [0, 4, 0]), (1.1, [0, 4, 0]), (1.5, [0, 0, 0]))},
        "rleg": {"rotation": keys((0, [0, 0, 0]), (0.7, [20, 0, 0]), (1.1, [20, 0, 0]), (1.5, [0, 0, 0]))},
        "lleg": {"rotation": keys((0, [0, 0, 0]), (0.7, [30, 0, 0]), (1.1, [30, 0, 0]), (1.5, [0, 0, 0]))},
        "cape": {"rotation": keys((0, [8, 0, 0]), (0.7, [70, 0, 0]), (1.1, [70, 0, 0]), (1.5, [8, 0, 0]))},
    }, loop=False)
    # CRUSHING DEPTHS: trident raised high, butt slammed down at 0.8
    anim(m, "pressure", 1.3, {
        "rarm": {"rotation": keys((0, RA), (0.45, [-175, 0, 10]), (0.8, [-20, 0, 0]), (1.3, RA))},
        "larm": {"rotation": keys((0, LA), (0.45, [-60, 0, -40]), (0.8, [-10, 0, -20]), (1.3, LA))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.45, [-14, 0, 0]), (0.8, [20, 0, 0]), (1.3, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.45, [-25, 0, 0]), (0.8, [10, 0, 0]), (1.3, [0, 0, 0]))},
        "root": {"position": keys((0, [0, 0, 0]), (0.45, [0, 1.5, 0]), (0.8, [0, -1, 0]), (1.3, [0, 0, 0]))},
    }, loop=False)
    # RESEAL: trident levelled at the sluice, channelling (2.5 s), trembling with the strain
    anim(m, "reseal", 2.6, {
        "rarm": {"rotation": keys((0, RA), (0.3, [-95, 0, 0]), (0.9, [-97, 2, 0]), (1.5, [-93, -2, 0]), (2.2, [-97, 2, 0]), (2.6, RA))},
        "larm": {"rotation": keys((0, LA), (0.3, [-80, 0, -15]), (2.2, [-80, 0, -15]), (2.6, LA))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [6, 18, 0]), (2.2, [6, 18, 0]), (2.6, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [5, -10, 0]), (2.2, [5, -10, 0]), (2.6, [0, 0, 0]))},
        "trident": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 0]), (1.0, [0, 90, 0]), (1.7, [0, 180, 0]), (2.2, [0, 270, 0]), (2.6, [0, 360, 0]))},
    }, loop=False)
    # TRIDENT THROW: a volley of spectral tridents
    anim(m, "throw", 0.9, {
        "rarm": {"rotation": keys((0, RA), (0.3, [-175, 0, 15]), (0.45, [-80, 0, 0]), (0.9, RA))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-6, 25, 0]), (0.45, [10, -18, 0]), (0.9, [0, 0, 0]))},
        "larm": {"rotation": keys((0, LA), (0.3, [-40, 0, -25]), (0.45, [10, 0, -10]), (0.9, LA))},
    }, loop=False)
    # ROYAL GUARD: shield raised, trident aloft - a summons
    anim(m, "summon", 1.4, {
        "rarm": {"rotation": keys((0, RA), (0.4, [-170, 0, 12]), (1.0, [-170, 0, 12]), (1.4, RA))},
        "larm": {"rotation": keys((0, LA), (0.4, [-120, 0, -20]), (1.0, [-120, 0, -20]), (1.4, LA))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-20, 0, 0]), (1.0, [-20, 0, 0]), (1.4, [0, 0, 0]))},
    }, loop=False)
    # ABYSSAL DECREE: arms thrown wide, head back
    anim(m, "decree", 1.2, {
        "rarm": {"rotation": keys((0, RA), (0.35, [-60, 0, 70]), (0.9, [-60, 0, 70]), (1.2, RA))},
        "larm": {"rotation": keys((0, LA), (0.35, [-60, 0, -70]), (0.9, [-60, 0, -70]), (1.2, LA))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.35, [-30, 0, 0]), (0.9, [-30, 0, 0]), (1.2, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.35, [-8, 0, 0]), (1.2, [0, 0, 0]))},
    }, loop=False)
    # MAELSTROM: trident overhead, spun like a wheel (2.5 s)
    anim(m, "maelstrom", 2.6, {
        "rarm": {"rotation": keys((0, RA), (0.3, [-180, 0, 0]), (2.3, [-180, 0, 0]), (2.6, RA))},
        "trident": {"rotation": keys((0, [0, 0, 0]), (0.3, [90, 0, 0]), (0.8, [90, 360, 0]), (1.3, [90, 720, 0]), (1.8, [90, 1080, 0]),
                                     (2.3, [90, 1440, 0]), (2.6, [0, 1440, 0]))},
        "larm": {"rotation": keys((0, LA), (0.3, [-40, 0, -50]), (2.3, [-40, 0, -50]), (2.6, LA))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-20, 0, 0]), (2.3, [-20, 0, 0]), (2.6, [0, 0, 0]))},
        "cape": {"rotation": wave(2.6, lambda q: [30 + S(q * 4) * 12, 0, S(q * 4) * 10])},
    }, loop=False)
    # UNDERTOW: a wide horizontal sweep that sends the pressure ring out (at 0.6)
    anim(m, "undertow", 1.2, {
        "rarm": {"rotation": keys((0, RA), (0.4, [-80, 0, 75]), (0.6, [-80, 0, -30]), (0.8, [-80, 0, -45]), (1.2, RA))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 40, 0]), (0.6, [4, -35, 0]), (0.8, [4, -40, 0]), (1.2, [0, 0, 0]))},
        "larm": {"rotation": keys((0, LA), (0.4, [-30, 0, -40]), (0.8, [-30, 0, -20]), (1.2, LA))},
    }, loop=False)
    # TIDE CALL: rises to full height roaring, arms up - the hall floods again
    anim(m, "tide_call", 2.0, {
        "rarm": {"rotation": keys((0, RA), (0.5, [-170, 0, 25]), (1.6, [-170, 0, 25]), (2.0, RA))},
        "larm": {"rotation": keys((0, LA), (0.5, [-170, 0, -25]), (1.6, [-170, 0, -25]), (2.0, LA))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [-35, 0, 0]), (1.6, [-35, 0, 0]), (2.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [-12, 0, 0]), (1.6, [-12, 0, 0]), (2.0, [0, 0, 0]))},
        "root": {"position": keys((0, [0, 0, 0]), (0.5, [0, 2, 0]), (1.6, [0, 2, 0]), (2.0, [0, 0, 0]))},
        "cape": {"rotation": keys((0, [8, 0, 0]), (0.5, [55, 0, 0]), (1.6, [55, 0, 0]), (2.0, [8, 0, 0]))},
    }, loop=False)
    # STRANDED: the water gone, he drops to a knee on his trident, head bowed, gasping (3 s)
    kneel = lambda t0, t1: {
        "body": {"rotation": keys((0, [0, 0, 0]), (t0, [26, 0, 6]), (t1, [26, 0, 6]), (t1 + 0.4, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (t0, [0, -5, 0]), (t1, [0, -5, 0]), (t1 + 0.4, [0, 0, 0]))},
        "rleg": {"rotation": keys((0, [0, 0, 0]), (t0, [-80, 0, 0]), (t1, [-80, 0, 0]), (t1 + 0.4, [0, 0, 0]))},
        "lleg": {"rotation": keys((0, [0, 0, 0]), (t0, [45, 0, 0]), (t1, [45, 0, 0]), (t1 + 0.4, [0, 0, 0]))},
        "rarm": {"rotation": keys((0, RA), (t0, [-30, 0, 10]), (t1, [-30, 0, 10]), (t1 + 0.4, RA))},
        "larm": {"rotation": keys((0, LA), (t0, [-10, 0, -5]), (t1, [-10, 0, -5]), (t1 + 0.4, LA))},
    }
    st = kneel(0.4, 2.6)
    st["head"] = {"rotation": keys((0, [0, 0, 0]), (0.4, [35, 0, 0]), (0.9, [20, 0, 0]), (1.4, [35, 0, 0]), (1.9, [20, 0, 0]),
                                   (2.6, [35, 0, 0]), (3.0, [0, 0, 0]))}
    anim(m, "stranded", 3.0, st, loop=False)
    # GASPING SWEEP (while stranded): a low, desperate swing of the trident
    anim(m, "sweep", 0.9, {
        "rarm": {"rotation": keys((0, RA), (0.3, [-60, 0, 80]), (0.5, [-50, 0, -40]), (0.9, RA))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [14, 45, 0]), (0.5, [18, -35, 0]), (0.9, [0, 0, 0]))},
        "root": {"position": keys((0, [0, 0, 0]), (0.3, [0, -2, 0]), (0.5, [0, -2, 0]), (0.9, [0, 0, 0]))},
    }, loop=False)
    # WRATH OF THE DEEP (enrage): crouch, then rise with shield and trident thrown out
    wr = kneel(0.4, 0.8)
    wr["rarm"] = {"rotation": keys((0, RA), (0.4, [-30, 0, 10]), (1.0, [-120, 0, 60]), (1.6, [-120, 0, 60]), (2.0, RA))}
    wr["larm"] = {"rotation": keys((0, LA), (0.4, [-10, 0, -5]), (1.0, [-120, 0, -60]), (1.6, [-120, 0, -60]), (2.0, LA))}
    wr["head"] = {"rotation": keys((0, [0, 0, 0]), (0.4, [30, 0, 0]), (1.0, [-40, 0, 0]), (1.6, [-40, 0, 0]), (2.0, [0, 0, 0]))}
    anim(m, "wrath", 2.0, wr, loop=False)


# =====================================================================================
# TIDE WARDEN - the King's guard; seals the sluices
# =====================================================================================
def tide_warden(seed):
    m = Rig("tide_warden", 64, 64, seed)
    PR = over(noise(["#2a6a66", "#327672", "#3a827c", "#448e86"]), spots("#8ae8d8", 0.04))
    SCALE = over(noise(["#1e4a48", "#265654", "#2e625e"]), spots("#6fb8a8", 0.1))
    KELP = noise(["#3a2e1e", "#4a3a26"])
    face = A("""
pppppppp
pppppppp
pkkkkkkp
pEEkkEEp
pkkkkkkp
ppkppkpp
pppppppp
pppppppp""", dict(p="#327672", k="#0a1a1a", E="!#8affe8"))
    a = biped(m, head=PR, body=SCALE, arm=SCALE, leg=over(SCALE, spots("#2a2016", 0.05)),
              art={"head": {"north": face}})
    t = a["top"]
    m.cube("head", [-4, t + 8, -4], [8, 1, 8], noise(["#2a6a66", "#327672"]), inflate=0.2)        # helm rim
    m.cube("head", [-0.5, t + 9, -3], [1, 2, 6], KELP)                                            # kelp crest
    m.cube("body", [-4, 12, -2], [8, 2, 4], KELP, inflate=0.4)                                    # belt
    m.pair("rarm", "larm", [-8.5, t - 2.5, -2.5], [5, 3, 5], PR)                                    # pauldrons
    rx, hy = a["rx"], a["hand_y"]
    m.bone("spear", [rx, hy, 0], "rhand")
    m.cube("spear", [rx - 0.5, hy - 10, -0.5], [1, 28, 1], noise(["#2a2016", "#3a2c1e"]))
    m.cube("spear", [rx - 2, hy + 18, -0.5], [4, 1, 1], PR)
    for x in (rx - 2, rx - 0.5, rx + 1):
        m.cube("spear", [x, hy + 19, -0.5], [1, 3, 1], PR)
    m.cube("spear", [rx - 0.5, hy + 22, -0.5], [1, 1, 1], lit("#8affe8"))
    lx = a["lx"]
    m.bone("buckler", [lx + 2, t - 7, 0], "larm")
    m.cube("buckler", [lx + 2, t - 11, -3], [1, 6, 6], PR,
           art={"east": A("_rrrr_\nrrggrr\nrggggr\nrggggr\nrrggrr\n_rrrr_", r="#3a827c", g="!#6fe8d0")})
    biped_anims(m, "thrust", hold=[-15, 0, 0])
    anim(m, "seal", 1.0, {
        "rarm": {"rotation": keys((0, [-15, 0, 0]), (0.25, [-80, 0, 20]), (0.5, [-80, 0, -10]), (0.75, [-80, 0, 20]), (1.0, [-15, 0, 0]))},
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.25, [-80, 0, -20]), (0.5, [-80, 0, 10]), (0.75, [-80, 0, -20]), (1.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [10, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    return m
