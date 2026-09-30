"""GHOST CAPTAIN of the Flying Dutchman (boss 4/10) - rebuilt 2026-09-29.

A drowned captain out of the Flying Dutchman legend: octopus-like head with glowing sea-green eyes and a
writhing six-tentacle beard (each tentacle two bones), a barnacle-crusted bicorn, a rotted captain's coat whose
hem frays into ghostly wisps instead of legs (he hovers), a great crab claw for a left hand (pincer bones
`claw_top` / `claw_bot`) and a barnacled cutlass in the right.

Skins (same geometry): normal, and enraged "Cursed Tide" - soul fire licking along the coat, eyes white-hot,
glowing tentacle tips.
Clips: idle, move, attack, slash, blink, beam, anchor, broadside, crew, calm, curse, cursed_tide (enrage), board.
"""
from mobkit import A, Rig, lit, noise, over, solid, spots, veins, drips, anim, keys, wave, S

Y0 = 10    # body bottom - he floats; the coat wisps hang below


def ghost_captain(seed, skin="normal"):
    tide = skin == "enraged"
    m = Rig("ghost_captain", 128, 128, seed)
    SKIN = ["#2e5a50", "#386a5e", "#427a6a", "#4e8a76"] if not tide else ["#1e3a38", "#24463f", "#2a5248", "#305e52"]
    COAT = ["#141a1e", "#1a2226", "#222c30", "#2a363a"]
    skin_m = lambda: over(noise(SKIN), spots("#c8c0a8", 0.05), spots("#6a8a6a", 0.05))
    coat_m = (lambda: over(noise(COAT), veins("#6fe8ff", 0.08, glow=True, length=(3, 7)), drips("#2a5a3a", 0.2, maxlen=2))) if tide \
        else (lambda: over(noise(COAT), spots("#c8c0a8", 0.03), drips("#2a5a3a", 0.25, maxlen=3)))
    trim = noise(["#6a5a2a", "#7a6a32"]) if not tide else over(noise(["#3a4a4a", "#46585a"]), spots("#8ff0ff", 0.2, glow=True))
    eye = "!#ffffff" if tide else "!#8ff0e0"
    tip = lit("#8ff0ff") if tide else noise(SKIN)

    m.bone("root", [0, 0, 0])
    # ------------------------------------------------------------------ body + coat (no legs: wisps)
    m.bone("body", [0, Y0 + 2, 0], "root")
    C = dict(c=COAT[1], L="#6a5a2a" if not tide else "!#6fe8ff", W="#4e6a60", b="#c8c0a8")
    m.cube("body", [-4, Y0 + 2, -2], [8, 12, 4], coat_m(),
           art={"north": A("""
cLWWWWLc
cLWbWWLc
cLWWWWLc
cLWWWbLc
cLWWWWLc
cLWWWWLc
cLccccLc
cLccccLc
cLccccLc
cLccccLc
cLccccLc
cLccccLc""", C)})
    m.cube("body", [-4, Y0 + 12, -3], [8, 2, 6], trim, inflate=0.2)                       # collar
    m.bone("skirt", [0, Y0 + 2, 0], "body")
    WISP = over(coat_m(), drips("#000000", 0.0, maxlen=1))
    frayed = A("cccccccc\ncccccccc\ncccccccc\nc_cc_ccc\n__c__c_c\n_____c__", c=COAT[1])
    m.cube("skirt", [-4.5, Y0 - 4, -2.6], [9, 6, 1], coat_m(), art={"north": frayed, "south": frayed})
    m.cube("skirt", [-4.5, Y0 - 5, 1.6], [9, 7, 1], coat_m(), art={"north": frayed, "south": frayed})
    m.pair("skirt", "skirt", [-5.2, Y0 - 3, -2], [1, 4, 4], coat_m(), art={"east": A("cccc\ncccc\nc_cc\n_c__\n____", c=COAT[1]),
                                                                          "west": A("cccc\ncccc\nc_cc\n_c__\n____", c=COAT[1])})
    m.bone("wisp", [0, Y0 - 4, 0], "skirt")
    m.cube("wisp", [-2, Y0 - 9, -1], [4, 5, 2], lit("#6fb8a8") if not tide else lit("#6fe8ff"),
           art={"north": A("cccc\nc_c_\n_c_c\nc_c_\n_c__", c="!#6fb8a8" if not tide else "!#6fe8ff"),
                "south": A("cccc\n_c_c\nc_c_\n_c_c\n__c_", c="!#6fb8a8" if not tide else "!#6fe8ff")})
    # ------------------------------------------------------------------ head: octopus face, bicorn, tentacle beard
    m.bone("head", [0, Y0 + 14, 0], "body")
    H = dict(s=SKIN[2], S=SKIN[0], E=eye, e="#0a1614", b="#c8c0a8")
    m.cube("head", [-4, Y0 + 14, -4], [8, 8, 8], skin_m(),
           art={"north": A("""
SSSssSSS
sSSssSSs
sEEssEEs
ssssssss
ssSssSss
sSsbbsSs
ssssssss
ssssssss""", H)})
    m.cube("head", [-3.5, Y0 + 18, 4], [7, 6, 4], over(skin_m(), spots(SKIN[0], 0.2)))       # octopus mantle
    m.bone("hat", [0, Y0 + 22, 0], "head")
    HAT = over(noise(["#12161a", "#1a2024", "#222a2e"]), spots("#c8c0a8", 0.08))
    m.cube("hat", [-7, Y0 + 22, -2], [14, 3, 4], HAT, art={"north": A("g" * 14 + "\n" + "k" * 14 + "\n" + "k" * 14, g="#6a5a2a", k="#1a2024")})
    m.cube("hat", [-4, Y0 + 25, -2], [8, 2, 4], HAT)
    m.cube("hat", [-1, Y0 + 23.5, -2.8], [2, 2, 1], solid("#e6e0cc"))                        # skull cockade
    for i, x in enumerate((-2.5, -1.5, -0.5, 0.5, 1.5, 2.5)):
        a, b = f"tent{i}a", f"tent{i}b"
        m.bone(a, [x, Y0 + 15, -4], "head")
        m.cube(a, [x - 0.5, Y0 + 11, -4.8], [1, 4, 1], noise(SKIN))
        m.bone(b, [x, Y0 + 11, -4.3], a)
        m.cube(b, [x - 0.5, Y0 + 7 - (i % 2), -4.8], [1, 4 + (i % 2), 1], over(noise(SKIN), spots("#8ff0e0" if tide else "#6a8a6a", 0.2, glow=tide)))
        m.cube(b, [x - 0.5, Y0 + 6 - (i % 2), -4.8], [1, 1, 1], tip)
    # ------------------------------------------------------------------ right arm + cutlass
    m.bone("rarm", [-6, Y0 + 12, 0], "body")
    m.cube("rarm", [-8, Y0 + 2, -2], [4, 12, 4], coat_m())
    m.cube("rarm", [-8, Y0 + 2, -2], [4, 2, 4], trim, inflate=0.3)                         # cuff
    m.bone("rhand", [-6, Y0 + 3, 0], "rarm")
    pv, tilt = [-6, Y0 + 3, 0], [-45, 0, 0]
    m.cube("rhand", [-6.5, Y0 + 2, -1], [1, 3, 2], solid("#2a2016"), rot=tilt, pivot=pv)
    m.cube("rhand", [-7.5, Y0 + 1.5, -3], [3, 3, 1], trim, rot=tilt, pivot=pv)
    m.cube("rhand", [-6.5, Y0 + 2, -15], [1, 2, 12], over(noise(["#6a7a78", "#7a8a88", "#8a9a98"]), spots("#c8c0a8", 0.15), spots("#2a5a3a", 0.1)),
           rot=tilt, pivot=pv)
    # ------------------------------------------------------------------ left arm: the crab claw
    m.bone("larm", [6, Y0 + 12, 0], "body")
    m.cube("larm", [4, Y0 + 5, -2], [4, 9, 4], coat_m())
    CLAW = over(noise(["#6a2a22", "#7a3428", "#8a3e2e"]), spots("#c8c0a8", 0.12))
    m.cube("larm", [3.5, Y0 + 1, -2.5], [5, 4, 5], CLAW)                                    # claw wrist
    m.bone("claw_top", [6, Y0 + 3, -2.5], "larm")
    m.cube("claw_top", [4, Y0 + 3, -9], [4, 2, 7], CLAW)
    m.cube("claw_top", [4.5, Y0 + 2, -9], [3, 1, 1], solid("#e6e0cc"))                      # teeth
    m.bone("claw_bot", [6, Y0 + 1, -2.5], "larm")
    m.cube("claw_bot", [4.5, Y0 - 0.5, -7], [3, 2, 5], CLAW)

    animations(m)
    return m


SW = -25
TENT = [f"tent{i}{s}" for i in range(6) for s in "ab"]


def tentacles(length, amp):
    d = {}
    for i in range(6):
        d[f"tent{i}a"] = {"rotation": wave(length, lambda q, i=i: [S(q + i) * amp, 0, S(q * 0.5 + i) * amp * 0.5])}
        d[f"tent{i}b"] = {"rotation": wave(length, lambda q, i=i: [S(q + i + 1.2) * amp * 1.6, 0, 0])}
    return d


def animations(m):
    idle = {"root": {"position": wave(3.0, lambda q: [0, S(q) * 1.2, 0])},
            "body": {"rotation": wave(3.0, lambda q: [S(q) * 2, 0, S(q * 0.5) * 2])},
            "head": {"rotation": wave(3.0, lambda q: [S(q) * 3, S(q * 0.5) * 10, 0])},
            "rarm": {"rotation": wave(3.0, lambda q: [SW + S(q) * 3, 0, 5])},
            "larm": {"rotation": wave(3.0, lambda q: [-15 - S(q) * 3, 0, -6])},
            "claw_top": {"rotation": wave(3.0, lambda q: [-max(0.0, S(q * 2)) * 18, 0, 0])},
            "skirt": {"rotation": wave(3.0, lambda q: [5 + S(q) * 4, 0, 0])},
            "wisp": {"rotation": wave(3.0, lambda q: [S(q + 1) * 12, S(q) * 10, 0])}}
    idle.update(tentacles(3.0, 8))
    anim(m, "idle", 3.0, idle)
    move = {"root": {"position": wave(1.6, lambda q: [0, S(q) * 0.8, 0])},
            "body": {"rotation": wave(1.6, lambda q: [14, 0, S(q) * 3])},
            "rarm": {"rotation": wave(1.6, lambda q: [SW + 15, 0, 8])},
            "larm": {"rotation": wave(1.6, lambda q: [0, 0, -10])},
            "skirt": {"rotation": wave(1.6, lambda q: [28 + S(q) * 6, 0, 0])},
            "wisp": {"rotation": wave(1.6, lambda q: [30 + S(q) * 10, S(q) * 14, 0])}}
    move.update(tentacles(1.6, 14))
    anim(m, "move", 1.6, move)
    slash = {"rarm": {"rotation": keys((0, [SW, 0, 0]), (0.2, [-160, 20, 30]), (0.35, [-30, -30, -15]), (0.7, [SW, 0, 0]))},
             "body": {"rotation": keys((0, [0, 0, 0]), (0.2, [-6, 25, 0]), (0.35, [10, -20, 0]), (0.7, [0, 0, 0]))}}
    anim(m, "attack", 0.7, slash, loop=False)
    anim(m, "slash", 0.7, slash, loop=False)
    anim(m, "blink", 0.7, {"root": {"position": keys((0, [0, 0, 0]), (0.25, [0, -3, 0]), (0.3, [0, 0, 0]), (0.7, [0, 0, 0]))},
                           "body": {"rotation": keys((0, [0, 0, 0]), (0.25, [30, 0, 0]), (0.45, [-10, 0, 0]), (0.7, [0, 0, 0]))}}, loop=False)
    anim(m, "beam", 1.0, {"head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-12, 0, 0]), (0.8, [-12, 0, 0]), (1.0, [0, 0, 0]))},
                          "larm": {"rotation": keys((0, [-15, 0, -6]), (0.4, [-95, 0, 0]), (0.8, [-95, 0, 0]), (1.0, [-15, 0, -6]))},
                          "claw_top": {"rotation": keys((0, [0, 0, 0]), (0.4, [-35, 0, 0]), (0.8, [-35, 0, 0]), (1.0, [0, 0, 0]))},
                          "claw_bot": {"rotation": keys((0, [0, 0, 0]), (0.4, [25, 0, 0]), (0.8, [25, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    anim(m, "anchor", 1.1, {"larm": {"rotation": keys((0, [-15, 0, -6]), (0.35, [-170, 0, -20]), (0.6, [-60, 0, 0]), (0.85, [-30, 0, -10]), (1.1, [-15, 0, -6]))},
                            "body": {"rotation": keys((0, [0, 0, 0]), (0.35, [-10, -25, 0]), (0.6, [12, 20, 0]), (0.85, [-15, 0, 0]), (1.1, [0, 0, 0]))}}, loop=False)
    anim(m, "broadside", 1.3, {"rarm": {"rotation": keys((0, [SW, 0, 0]), (0.4, [-170, 0, 10]), (0.8, [-170, 0, 10]), (0.95, [-80, 0, 0]), (1.3, [SW, 0, 0]))},
                               "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-25, 0, 0]), (0.95, [5, 0, 0]), (1.3, [0, 0, 0]))}}, loop=False)
    anim(m, "crew", 1.1, {"rarm": {"rotation": keys((0, [SW, 0, 0]), (0.4, [-60, 0, 60]), (0.8, [-60, 0, 60]), (1.1, [SW, 0, 0]))},
                          "larm": {"rotation": keys((0, [-15, 0, -6]), (0.4, [-60, 0, -60]), (0.8, [-60, 0, -60]), (1.1, [-15, 0, -6]))}}, loop=False)
    anim(m, "calm", 1.0, {"larm": {"rotation": keys((0, [-15, 0, -6]), (0.4, [-150, 0, -10]), (0.8, [-150, 0, -10]), (1.0, [-15, 0, -6]))},
                          "claw_top": {"rotation": keys((0, [0, 0, 0]), (0.4, [-40, 0, 0]), (0.6, [0, 0, 0]), (0.8, [-40, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    curse = {"body": {"rotation": keys((0, [0, 0, 0]), (0.3, [20, 0, 0]), (0.7, [-15, 0, 0]), (1.0, [0, 0, 0]))}}
    curse.update({t: {"rotation": keys((0, [0, 0, 0]), (0.3, [-40, 0, 0]), (0.7, [30, 0, 0]), (1.0, [0, 0, 0]))} for t in TENT})
    anim(m, "curse", 1.0, curse, loop=False)
    tide = {"body": {"rotation": keys((0, [0, 0, 0]), (0.4, [25, 0, 0]), (0.9, [-20, 0, 0]), (1.4, [-20, 0, 0]), (1.8, [0, 0, 0]))},
            "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [20, 0, 0]), (0.9, [-35, 0, 0]), (1.4, [-35, 0, 0]), (1.8, [0, 0, 0]))},
            "rarm": {"rotation": keys((0, [SW, 0, 0]), (0.9, [-70, 0, 70]), (1.4, [-70, 0, 70]), (1.8, [SW, 0, 0]))},
            "larm": {"rotation": keys((0, [-15, 0, -6]), (0.9, [-70, 0, -70]), (1.4, [-70, 0, -70]), (1.8, [-15, 0, -6]))},
            "claw_top": {"rotation": keys((0, [0, 0, 0]), (0.9, [-45, 0, 0]), (1.4, [-45, 0, 0]), (1.8, [0, 0, 0]))}}
    tide.update({t: {"rotation": keys((0, [0, 0, 0]), (0.9, [-60, 0, 0]), (1.4, [-50, 0, 0]), (1.8, [0, 0, 0]))} for t in TENT if t.endswith("a")})
    anim(m, "cursed_tide", 1.8, tide, loop=False)
    anim(m, "board", 1.2, {"root": {"position": keys((0, [0, 6, 0]), (0.4, [0, -2, 0]), (0.7, [0, 0, 0]), (1.2, [0, 0, 0]))},
                           "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [30, 0, 0]), (0.8, [-10, 0, 0]), (1.2, [0, 0, 0]))},
                           "rarm": {"rotation": keys((0, [SW, 0, 0]), (0.4, [-10, 0, 20]), (0.8, [-120, 0, 0]), (1.2, [SW, 0, 0]))}}, loop=False)
