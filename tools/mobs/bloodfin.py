"""THE BLOODFIN (boss 6/10) - rebuilt 2026-09-29. There is no concept render for it, so it is designed here:
a scarred, hump-backed great white, ~48 px long x renderScale 1.8 = ~5.4 blocks. Slate back, bone belly, a tall
notched dorsal with a blood-red edge, three broken whalers' harpoons still stuck in its back (rope trailing), a
hinged jaw that opens to ~70 degrees with interlocking upper/lower teeth and a dark-red mouth.

FLESH: six chunk bones `flesh0..5` (flanks, hump, back) sit over glowing wound cubes. BloodfinEntity hides one
per damage threshold (ModMob.toggleBones) - the flesh tears away and the wound underneath shows.

Skins (same geometry): normal, and enraged "Blood Frenzy" - flayed: raw red muscle split by white rib bands,
shredded fins (cutout holes), blazing eyes, glowing blood veins.
Clips: idle, move, attack, lunge, tail_slap, devour, breach, thrash, flipped, feed, call, hunt, gag, wake_slam, frenzy.
"""
from mobkit import A, Rig, lit, noise, over, solid, spots, veins, drips, counter, bands, anim, keys, wave, S

L0, L1 = -14, 14         # torso z range (front is -Z)


def bloodfin(seed, skin="normal"):
    fr = skin == "enraged"
    m = Rig("bloodfin", 128, 128, seed)
    BACK = ["#2e3a42", "#36444e", "#3e4e58", "#46565f"]
    BELLY = ["#c8c4ba", "#d6d2c8", "#e2ded4"]
    if fr:
        back = lambda: over(bands(noise(["#6a1414", "#7e1a1a", "#921f1f", "#a82626"]), (2, 3, solid("#e6dcc8")), (6, 7, solid("#e6dcc8"))),
                            veins("#ff3a2a", 0.06, glow=True, length=(2, 6)), drips("#3a0606", 0.4, maxlen=3))
        body = lambda: over(counter(["#6a1414", "#7e1a1a", "#921f1f"], ["#b8a898", "#c8b8a8"], 0.58),
                            spots("#e6dcc8", 0.05), veins("#ff3a2a", 0.05, glow=True, length=(2, 6)),
                            drips("#3a0606", 0.4, maxlen=3))
        FIN = ["#4a0e0e", "#5a1414", "#6a1a1a"]
        eye = "!#fff0c0"
        wound = lit("#ff2a1a")
    else:
        back = lambda: over(noise(BACK), veins("#b8c0c4", 0.05, glow=False, faces=("east", "west", "up"), length=(2, 4)))
        body = lambda: over(counter(BACK, BELLY, 0.58), veins("#b8c0c4", 0.04, glow=False, faces=("east", "west", "up"), length=(2, 4)))
        FIN = ["#2a343c", "#323e46", "#3a4750"]
        eye = "!#ff3a2a"
        wound = over(lit("#c01818"), spots("#ff5a3a", 0.3, glow=True))
    fin = lambda: over(noise(FIN), drips("#8a1a1a", 0.6, faces=("north", "south", "east", "west", "up"), maxlen=1))
    flesh = lambda: over(noise(BACK if not fr else ["#5a1010", "#6a1414"]), spots("#8a1a1a" if fr else "#b8c0c4", 0.05))
    TOOTH = solid("#ece6d2", edge=0)
    MOUTH = "#4a0a0e"

    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 7, 0], "root")
    # ------------------------------------------------------------------ torso + hump
    gill = dict(g="#1a2024" if not fr else "#2a0606")
    m.cube("body", [-7, 0, L0], [14, 13, L1 - L0], body(),
           art={"east": A.at(28, 13, {(x, y): "g" for x in (1, 3, 5, 7) for y in range(3, 9)}, **gill),
                "west": A.at(28, 13, {(x, y): "g" for x in (20, 22, 24, 26) for y in range(3, 9)}, **gill)})
    m.cube("body", [-6, 13, -10], [12, 2, 14], back())                                       # shoulder hump
    # tall notched dorsal, blood edge (enraged: shredded)
    notch = {(0, 2): "_", (0, 3): "_", (0, 9): "_", (0, 10): "_", (0, 11): "_"}
    if fr:
        notch.update({(0, 5): "_", (0, 6): "_", (0, 13): "_"})
    dorsal_art = A.at(8, 15, {**{(x, y): "_" for (xx, y) in notch for x in (xx, 1)}})
    m.cube("body", [-0.5, 14, -6], [1, 15, 8], fin(), rot=[-32, 0, 0], pivot=[0, 14, -2],
           art={"east": dorsal_art, "west": dorsal_art})
    # three broken harpoons in the back, rope trailing from one
    for (x, z, rx, rz) in ((-3, -4, 28, -18), (2.5, 3, 35, 14), (-1.5, 9, 22, -8)):
        m.cube("body", [x, 12, z], [1, 9, 1], noise(["#4a3620", "#5a4228"]), rot=[rx, 0, rz], pivot=[x + 0.5, 12, z + 0.5])
    m.cube("body", [2.5, 11, 3.5], [1, 1, 9], noise(["#b8a070", "#a08a5a"]), rot=[-10, 0, 0], pivot=[3, 11.5, 4])
    # ------------------------------------------------------------------ flesh chunks over glowing wounds
    FLESH = [((7, 3, -10), (1.5, 7, 8), (6.5, 4, -9), (1, 5, 6)),
             ((-8.5, 3, -10), (1.5, 7, 8), (-7.5, 4, -9), (1, 5, 6)),
             ((7, 2, 2), (1.5, 8, 9), (6.5, 3, 3), (1, 6, 7)),
             ((-8.5, 2, 2), (1.5, 8, 9), (-7.5, 3, 3), (1, 6, 7)),
             ((-4, 15, -9), (8, 1.5, 7), (-3, 14.5, -8), (6, 1, 5)),
             ((-4, 13, 5), (8, 1.5, 8), (-3, 12.5, 6), (6, 1, 6))]
    for i, (o, s, wo, ws) in enumerate(FLESH):
        m.cube("body", list(wo), list(ws), wound)
        m.bone(f"flesh{i}", [o[0] + s[0] / 2, o[1] + s[1] / 2, o[2] + s[2] / 2], "body")
        m.cube(f"flesh{i}", list(o), list(s), flesh())
    # ------------------------------------------------------------------ head, snout, teeth, eyes
    m.bone("head", [0, 7, L0], "body")
    ek = eye
    m.cube("head", [-6.5, 4, -26], [13, 8, 12], body(),
           art={"east": A.at(12, 8, {(3, 2): "e", (4, 2): "e", (3, 3): "E"}, e=ek, E=ek),
                "west": A.at(12, 8, {(8, 2): "e", (7, 2): "e", (8, 3): "E"}, e=ek, E=ek),
                "down": A.at(13, 12, {(x, y): "m" for x in range(1, 12) for y in range(0, 11)}, m=MOUTH)})
    m.cube("head", [-5, 5, -31], [10, 6, 5], back())                                           # snout
    m.cube("head", [-3.5, 6, -33], [7, 4, 2], back())                                          # nose tip
    for i, x in enumerate(range(-5, 6, 2)):                                                    # upper teeth (front + sides)
        m.cube("head", [x, 2.5, -25.5], [1, 1.5, 1], TOOTH)
    for z in (-24, -21, -18):
        m.cube("head", [-6, 2.5, z], [1, 1.5, 1], TOOTH)
        m.cube("head", [5, 2.5, z], [1, 1.5, 1], TOOTH)
    # jaw: hinge at the back of the head, dark-red mouth on top
    m.bone("jaw", [0, 3, -16], "head")
    m.cube("jaw", [-6, -1, -29], [12, 5, 13], over(noise(BELLY if not fr else ["#b8a898", "#c8b8a8"]), spots("#8a1a1a", 0.05 if not fr else 0.2)),
           art={"up": A.at(12, 13, {(x, y): "m" for x in range(1, 11) for y in range(0, 12)}, m=MOUTH)})
    for x in range(-4, 5, 2):                                                                  # lower teeth
        m.cube("jaw", [x, 4, -28.5], [1, 1.5, 1], TOOTH)
    for z in (-26, -23, -20):
        m.cube("jaw", [-5.5, 4, z], [1, 1.5, 1], TOOTH)
        m.cube("jaw", [4.5, 4, z], [1, 1.5, 1], TOOTH)
    # ------------------------------------------------------------------ pectorals
    m.bone("fin_r", [-7, 3, -8], "body")
    m.bone("fin_l", [7, 3, -8], "body")
    m.cube("fin_r", [-20, 2.5, -11], [13, 1, 7], fin(), rot=[0, 20, -25], pivot=[-7, 3, -8])
    m.mirror("fin_l", [-20, 2.5, -11], [13, 1, 7], fin(), rot=[0, 20, -25], pivot=[-7, 3, -8])
    # ------------------------------------------------------------------ tail: stock, wrist, two-lobed caudal
    m.bone("tail", [0, 7, L1], "body")
    m.cube("tail", [-4.5, 2, L1], [9, 9, 10], body())
    m.cube("tail", [-0.5, 10, L1 + 3], [1, 4, 3], fin(), rot=[-30, 0, 0], pivot=[0, 10, L1 + 4])       # 2nd dorsal
    m.pair("tail", "tail", [-8, 2, L1 + 2], [3, 1, 3], fin(), rot=[0, 20, -30], pivot=[-4.5, 2.5, L1 + 3])
    m.bone("tail2", [0, 7, L1 + 10], "tail")
    m.cube("tail2", [-3, 3.5, L1 + 10], [6, 6, 6], body())
    m.bone("tailfin", [0, 7, L1 + 16], "tail2")
    lobe = A.at(5, 17, {(4, y): "_" for y in (3, 4, 11)} if fr else {})
    m.cube("tailfin", [-0.5, 7, L1 + 15], [1, 17, 5], fin(), rot=[-38, 0, 0], pivot=[0, 7, L1 + 17], art={"east": lobe, "west": lobe})
    m.cube("tailfin", [-0.5, -3, L1 + 15], [1, 10, 4], fin(), rot=[40, 0, 0], pivot=[0, 7, L1 + 17])

    animations(m)
    return m


def swim(length, a):
    return {"tail": {"rotation": wave(length, lambda q: [0, S(q) * 14 * a, 0])},
            "tail2": {"rotation": wave(length, lambda q: [0, S(q - 0.7) * 16 * a, 0])},
            "tailfin": {"rotation": wave(length, lambda q: [0, S(q - 1.4) * 22 * a, 0])},
            "body": {"rotation": wave(length, lambda q: [0, -S(q) * 3.5 * a, S(q) * 1.5])},
            "head": {"rotation": wave(length, lambda q: [0, -S(q + 0.6) * 3 * a, 0])},
            "fin_r": {"rotation": wave(length, lambda q: [0, 0, S(q) * 6 * a])},
            "fin_l": {"rotation": wave(length, lambda q: [0, 0, -S(q) * 6 * a])},
            "jaw": {"rotation": wave(length, lambda q: [4 + S(q * 0.5) * 3, 0, 0])}}


def jaw(*kf):
    return {"rotation": keys(*[(t, [a, 0, 0]) for t, a in kf])}


def animations(m):
    anim(m, "idle", 2.8, swim(2.8, 0.5))
    anim(m, "move", 1.0, swim(1.0, 1.0))
    anim(m, "attack", 0.6, {"jaw": jaw((0, 4), (0.15, 50), (0.3, 0), (0.6, 4)),
                            "head": {"rotation": keys((0, [0, 0, 0]), (0.15, [-14, 0, 0]), (0.3, [8, 0, 0]), (0.6, [0, 0, 0]))},
                            "root": {"position": keys((0, [0, 0, 0]), (0.15, [0, 0, 2]), (0.3, [0, 0, -6]), (0.6, [0, 0, 0]))}}, loop=False)
    anim(m, "lunge", 0.8, {"jaw": jaw((0, 4), (0.2, 55), (0.45, 0), (0.8, 4)),
                           "body": {"rotation": keys((0, [0, 0, 0]), (0.2, [-8, 0, 0]), (0.45, [6, 0, 0]), (0.8, [0, 0, 0]))},
                           "tail": {"rotation": keys((0, [0, 0, 0]), (0.1, [0, 30, 0]), (0.25, [0, -30, 0]), (0.4, [0, 20, 0]), (0.8, [0, 0, 0]))}}, loop=False)
    anim(m, "tail_slap", 0.9, {"tail": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 50, 0]), (0.5, [0, -60, 0]), (0.9, [0, 0, 0]))},
                               "tailfin": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 35, 0]), (0.5, [0, -50, 0]), (0.9, [0, 0, 0]))},
                               "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, -18, 0]), (0.5, [0, 18, 0]), (0.9, [0, 0, 0]))}}, loop=False)
    anim(m, "wake_slam", 1.1, {"tail": {"rotation": keys((0, [0, 0, 0]), (0.4, [-40, 0, 0]), (0.6, [45, 0, 0]), (1.1, [0, 0, 0]))},
                               "tailfin": {"rotation": keys((0, [0, 0, 0]), (0.4, [-30, 0, 0]), (0.6, [40, 0, 0]), (1.1, [0, 0, 0]))},
                               "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [20, 0, 0]), (0.6, [-10, 0, 0]), (1.1, [0, 0, 0]))}}, loop=False)
    # DEVOUR: jaw gapes wide (0.1-0.45), snaps shut on the victim at 0.5
    anim(m, "devour", 0.9, {"jaw": jaw((0, 4), (0.25, 72), (0.45, 72), (0.55, 6), (0.9, 6)),
                            "head": {"rotation": keys((0, [0, 0, 0]), (0.25, [-22, 0, 0]), (0.5, [10, 0, 0]), (0.9, [0, 0, 0]))},
                            "root": {"position": keys((0, [0, 0, 0]), (0.25, [0, 0, 3]), (0.5, [0, 0, -8]), (0.9, [0, 0, 0]))}}, loop=False)
    # BREACH (2.4 s): nose up out of the water, a full barrel roll at the apex, nose down into the crash
    anim(m, "breach", 2.4, {"body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-65, 0, 0]), (1.0, [-40, 0, 0]), (1.2, [0, 0, 180]),
                                                      (1.5, [30, 0, 360]), (2.0, [70, 0, 360]), (2.4, [0, 0, 360]))},
                            "tail": {"rotation": wave(2.4, lambda q: [0, S(q * 6) * 30, 0])},
                            "tailfin": {"rotation": wave(2.4, lambda q: [0, S(q * 6 - 1) * 35, 0])},
                            "fin_r": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, -25]), (2.0, [0, 0, -25]), (2.4, [0, 0, 0]))},
                            "fin_l": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 25]), (2.0, [0, 0, 25]), (2.4, [0, 0, 0]))},
                            "jaw": jaw((0, 6), (2.4, 6))}, loop=False)
    # THRASH: hooked - violent side-to-side, head whipping, jaw snapping
    anim(m, "thrash", 1.0, {"body": {"rotation": keys((0, [0, 0, 0]), (0.15, [0, 25, 20]), (0.35, [0, -25, -20]), (0.55, [0, 25, 20]),
                                                      (0.75, [0, -25, -20]), (1.0, [0, 0, 0]))},
                            "head": {"rotation": keys((0, [0, 0, 0]), (0.2, [0, -25, 0]), (0.4, [0, 25, 0]), (0.6, [0, -25, 0]), (0.8, [0, 25, 0]), (1.0, [0, 0, 0]))},
                            "tail": {"rotation": wave(1.0, lambda q: [0, S(q * 4) * 45, 0])},
                            "jaw": jaw((0, 4), (0.2, 45), (0.3, 0), (0.6, 45), (0.7, 0), (1.0, 4))}, loop=False)
    # FLIPPED - tonic immobility (10 s): rolled onto its back, belly up, fins limp, the odd weak twitch
    anim(m, "flipped", 10.0, {"body": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 0, 180]), (9.5, [0, 0, 180]), (10.0, [0, 0, 360])),
                                       "position": keys((0, [0, 0, 0]), (0.5, [0, 3, 0]), (9.5, [0, 3, 0]), (10.0, [0, 0, 0]))},
                              "fin_r": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 0, 30]), (9.5, [0, 0, 30]), (10.0, [0, 0, 0]))},
                              "fin_l": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 0, -30]), (9.5, [0, 0, -30]), (10.0, [0, 0, 0]))},
                              "tail": {"rotation": keys((0, [0, 0, 0]), (3.0, [0, 0, 0]), (3.2, [0, 10, 0]), (3.4, [0, 0, 0]), (6.5, [0, 0, 0]),
                                                        (6.7, [0, -12, 0]), (6.9, [0, 0, 0]), (10.0, [0, 0, 0]))},
                              "jaw": jaw((0, 4), (0.5, 25), (9.5, 25), (10.0, 4))}, loop=False)
    # FEED: tearing at bait, head shaking, jaw chomping
    anim(m, "feed", 1.6, {"head": {"rotation": keys((0, [0, 0, 0]), (0.2, [10, -20, 0]), (0.4, [10, 20, 0]), (0.6, [10, -20, 0]),
                                                    (0.8, [10, 20, 0]), (1.0, [10, -20, 0]), (1.2, [10, 20, 0]), (1.6, [0, 0, 0]))},
                          "jaw": jaw((0, 4), (0.1, 40), (0.3, 2), (0.5, 40), (0.7, 2), (0.9, 40), (1.1, 2), (1.6, 4)),
                          "body": {"rotation": keys((0, [0, 0, 0]), (0.2, [15, 0, 0]), (1.4, [15, 0, 0]), (1.6, [0, 0, 0]))}}, loop=False)
    # CALL: a surfacing roar that calls the sharks
    anim(m, "call", 1.4, {"body": {"rotation": keys((0, [0, 0, 0]), (0.4, [-25, 0, 0]), (1.0, [-25, 0, 0]), (1.4, [0, 0, 0]))},
                          "jaw": jaw((0, 4), (0.4, 60), (1.0, 60), (1.4, 4)),
                          "head": {"rotation": wave(1.4, lambda q: [0, S(q * 3) * 8, 0])}}, loop=False)
    # HUNT: dives nose-first into the deep
    anim(m, "hunt", 1.0, {"body": {"rotation": keys((0, [0, 0, 0]), (0.4, [55, 0, 0]), (0.8, [55, 0, 0]), (1.0, [0, 0, 0]))},
                          "tail": {"rotation": wave(1.0, lambda q: [0, S(q * 2) * 30, 0])}}, loop=False)
    # GAG: spits the victim out, choking
    anim(m, "gag", 1.2, {"jaw": jaw((0, 6), (0.15, 75), (0.6, 75), (0.8, 10), (1.0, 50), (1.2, 4)),
                         "head": {"rotation": keys((0, [0, 0, 0]), (0.15, [-30, 0, 0]), (0.4, [-30, 15, 0]), (0.6, [-30, -15, 0]), (1.2, [0, 0, 0]))},
                         "body": {"rotation": keys((0, [0, 0, 0]), (0.2, [-10, 0, 0]), (1.2, [0, 0, 0]))}}, loop=False)
    # BLOOD FRENZY (enrage): rolls, jaw agape, tail beating - the flesh tears away
    anim(m, "frenzy", 2.0, {"body": {"rotation": keys((0, [0, 0, 0]), (0.5, [-30, 0, 90]), (1.0, [-30, 0, 270]), (1.5, [-10, 0, 360]), (2.0, [0, 0, 360]))},
                            "jaw": jaw((0, 4), (0.3, 75), (1.6, 75), (2.0, 4)),
                            "tail": {"rotation": wave(2.0, lambda q: [0, S(q * 5) * 45, 0])},
                            "head": {"rotation": wave(2.0, lambda q: [S(q * 4) * 10, S(q * 3) * 15, 0])}}, loop=False)
