"""THE KRAKEN (boss 7/10) and its ARMS - rebuilt 2026-09-29. No concept render exists for the head (only the
tentacle one: near-black skin, amber sucker pads), so it is designed here in that style.

KRAKEN (phase 2, rises out of the abyss): a towering mantle leaning back like a hood, crowned with a ridge of bony
knobs and crusted with barnacles and pale scars; two huge amber eyes on the front corners with horizontal slit
pupils under heavy brow ridges; at the front of its base a lipped MAW (bone `maw`) with a hooked two-part beak
(`beak_top` / `beak_bot`) and a dark-red throat; seven thick arms sprawl across the floor with their tips curling up
(suckers showing), and two long FEEDING TENTACLES (`whip_r` / `whip_l`) rise in front ending in hooked clubs.
~48 px tall x renderScale 2.4 = ~7 blocks, arms reaching ~8 blocks out.
Skins: normal, and enraged "Abyssal Wrath" (the veins burn ember-red, eyes blood-red).
Clips: idle, move, attack (beak crush), inhale (3 s suck), spit, swat (a whip smashes down), ink, quake (all arms
slam the floor), whirl, rise (surfacing from the abyss), wrath (enrage), hurt_arm.

KRAKEN ARM (phase 1, bursts from the seabed): one colossal tentacle, 8 segments (~64 px x renderScale 2.0 = ~8 blocks),
rubble mound at its base, hooked spines along its back, a clawed tip. Clips: idle, move, attack (slam), sweep, grab,
fling, rise, retract.
"""
import math
from mobkit import A, Rig, lit, noise, over, solid, spots, speckle, veins, drips, anim, keys, wave, S

INK = ["#0c0e12", "#12151a", "#181c22", "#1e232a"]
AMBER = ["#8a5a1e", "#a8702a", "#c08838"]
SEABED = ["#1a1c20", "#22262b", "#2a2f35", "#323840"]


def skin_of(rage):
    if rage:
        return over(noise(["#12080a", "#1a0c0e", "#221012", "#2a1416"]), veins("#ff4a1a", 0.05, glow=True, length=(3, 8)),
                    spots("#6a1a1a", 0.08), speckle("#3a2a2a", 0.05))
    return over(noise(INK), spots("#2e1622", 0.07, size=2), speckle("#3a4048", 0.05), veins("#5a6a70", 0.012, glow=False, length=(2, 5)))


def barnacles(rage):
    return over(noise(["#4a4a46", "#5a5a54", "#6a6a62"]), spots("#2a2a28", 0.3), spots("#ff6a2a" if rage else "#8a8a80", 0.08, glow=rage))


def suckers(m, bone, x, y0, z, n, step, w=3, h=2, rage=False, face="north"):
    """A column of amber sucker pads (dark centres) on one face of a segment."""
    for i in range(n):
        if face == "north":
            m.cube(bone, [x - w / 2, y0 + i * step, z - 1], [w, h, 1], noise(AMBER) if not rage else lit("#c05a1a"),
                   art={"north": A.at(w, h, {(w // 2, h // 2): "d"}, d="#2a1408")})


# =====================================================================================
# KRAKEN - the head
# =====================================================================================
def kraken(seed, skin="normal"):
    rage = skin == "enraged"
    m = Rig("kraken", 256, 256, seed)
    SK = lambda: skin_of(rage)
    BARN = barnacles(rage)
    eye_iris = "!#ff3a2a" if rage else "!#e89a2a"
    eye_hot = "!#ffd0a0" if rage else "!#ffe07a"

    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 9, 0], "root")
    # ------------------------------------------------------------------ the base the arms grow from (octagon-ish)
    m.cube("body", [-13, 0, -8], [26, 9, 18], SK())
    m.cube("body", [-8, 0.5, -11], [16, 9, 24], SK())
    # ------------------------------------------------------------------ the mantle: a tall hooded dome leaning back
    m.bone("mantle", [0, 9, 2], "body")
    # stacked slabs: a tall hood, widest at the eyes, leaning back as it rises
    slabs = [([-12, 9, -8], [24, 12, 21]), ([-11, 21, -6], [22, 12, 20]), ([-9, 33, -3], [18, 9, 17]),
             ([-6.5, 42, 1], [13, 7, 13]), ([-4, 49, 5], [8, 4, 9]), ([-2, 53, 8], [4, 3, 5])]
    for o, sz in slabs:
        m.cube("mantle", o, sz, SK())
    m.cube("mantle", [-13, 11.5, -6], [26, 8, 16], SK())                  # the flanks bulge
    m.cube("mantle", [-12, 24.5, -4], [24, 6, 15], SK())
    # bony ridge knobs down the crown, barnacle crusts, a torn fin-flap on each side
    for (y, z) in ((21.5, 14), (33.5, 13.5), (42.5, 13.5), (49.5, 13.5), (53.5, 12.5)):
        m.cube("mantle", [-1, y, z], [2, 2, 2], BARN)
    for (x, y, z, w, h) in ((-12.5, 14, 4, 2, 5), (10.5, 16, 0, 2, 4), (-8, 37, 13.5, 5, 2), (4, 44, 13.5, 3, 2), (-7, 15, 12.5, 6, 2)):
        m.cube("mantle", [x, y, z], [w, h, 2 if w == 2 else 1.5], BARN)
    m.pair("mantle", "mantle", [-14, 30, 2], [2, 12, 9], SK(), rot=[0, 0, 18], pivot=[-12, 30, 6])
    # ------------------------------------------------------------------ eyes: huge amber slit-pupils under heavy brows
    eye_art = A("""
aaaaaa
aoooaa
ooHooo
kkkkkk
oooooo
aoooaa
aaaaaa""", dict(a="#1e232a" if not rage else "#2a1416", o=eye_iris, H=eye_hot, k="#050303"))
    side = A("""
aaaaa
aooaa
oooHo
kkkkk
ooooo
aoooa
aaaaa""", dict(a="#1e232a" if not rage else "#2a1416", o=eye_iris, H=eye_hot, k="#050303"))
    big = A("""
aaoooooa
aooooooa
ooHHooooo
kkkkkkkk
kkkkkkkk
oooooooo
aoooooaa
aaooooaa""".replace("ooHHooooo", "ooHHoooo"), dict(a="#1e232a" if not rage else "#2a1416", o=eye_iris, H=eye_hot, k="#050303"))
    for bone, x0, sx in (("eye_r", -15, -1), ("eye_l", 7, 1)):
        m.bone(bone, [x0 + 4, 25, -8], "mantle")
        m.cube(bone, [x0, 21, -10], [8, 8, 6], SK(), art={"north": big if sx < 0 else big.flipped(),
                                                          ("west" if sx < 0 else "east"): side})
        # a heavy brow slanting down toward the middle (furious), a lower lid ridge
        m.cube(bone, [x0 - 0.5, 29, -11], [9, 2.5, 7], SK(), rot=[0, 0, -16 * sx], pivot=[x0 + 4, 30, -8])
        m.cube(bone, [x0 + 0.5, 19.5, -10.5], [7, 1.5, 5], SK())
    # a second, smaller pair of eyes above - it watches from everywhere
    small = A("""
aooa
okko
aooa""", dict(a="#1e232a" if not rage else "#2a1416", o=eye_iris, k="#050303"))
    m.pair("mantle", "mantle", [-7, 34.5, -4], [4, 3, 2], SK(), art={"north": small})
    # ------------------------------------------------------------------ the maw: lips, throat, two-part hooked beak
    m.bone("maw", [0, 7.5, -12], "body")
    LIP = over(noise(["#1a1014", "#22141a", "#2a181f"]), spots("#4a1a2a", 0.12))
    m.cube("maw", [-4, 3, -14], [8, 2, 3], LIP)                           # lips: four folds, open corners
    m.cube("maw", [-4, 11, -14], [8, 2, 3], LIP)
    m.cube("maw", [-6, 5, -14], [2, 6, 3], LIP)
    m.cube("maw", [4, 5, -14], [2, 6, 3], LIP)
    for (x, y, rx, rz) in ((-3, 4.8, -30, 0), (-1, 4.8, -30, 0), (1, 4.8, -30, 0), (-3, 10, 30, 0), (-1, 10, 30, 0), (1, 10, 30, 0),
                           (-4.8, 7, 0, 30), (3.8, 7, 0, -30)):
        m.cube("maw", [x + 0.5, y, -14.5], [1, 1.5, 1], solid("#e6dcc8"), rot=[rx, 0, rz], pivot=[x + 1, y + 0.5, -14])   # fangs
    m.cube("maw", [-4, 5.5, -12], [8, 5, 1], lit("#3a0608") if rage else solid("#2a0508"),
           art={"north": A.at(8, 5, {(x, y): "d" for x in range(2, 6) for y in range(1, 4)}, d="#0a0102")})       # throat
    m.bone("beak_top", [0, 10, -13], "maw")
    HORN = over(noise(["#2a2018", "#3a2c20", "#4a3a2a"]), spots("#1a120c", 0.15))
    m.cube("beak_top", [-3, 8, -16], [6, 3, 4], HORN)
    m.cube("beak_top", [-1.5, 6.5, -17], [3, 2, 2], HORN)                 # the hook
    m.bone("beak_bot", [0, 6, -13], "maw")
    m.cube("beak_bot", [-2.5, 5, -15.5], [5, 2.5, 3], HORN)
    # ------------------------------------------------------------------ seven sprawling arms (front sector left open)
    widths, lengths = [8, 7, 6, 5, 3], [10, 10, 9, 8, 7]
    curl = [86, -4, -8, -20, -42]                                         # sprawled flat, only the tips curl up
    arms = []
    for k, deg in enumerate((-40, 0, 40, 90, 140, 180, 220)):
        a = math.radians(deg)
        cx, cz = math.cos(a) * 10, math.sin(a) * 10
        yaw = math.degrees(math.atan2(-math.cos(a), -math.sin(a)))
        root = f"arm{k}"
        m.bone(root, [cx, 3, cz], "body", rotation=[0, yaw, 0])
        prev, y, names = root, 3, []
        for i, (w, L, rx) in enumerate(zip(widths, lengths, curl)):
            b = f"arm{k}_{i}"
            m.bone(b, [cx, y, cz], prev, rotation=[rx, 0, 0])
            m.cube(b, [cx - w / 2, y, cz - w / 2], [w, L, w], SK())
            if w >= 5:
                suckers(m, b, cx, y + 1, cz - w / 2, max(1, L // 3), 3, w=w - 2, rage=rage)
            names.append(b)
            prev, y = b, y + L
        m.cube(prev, [cx - 0.5, y, cz - 0.5], [1, 3, 1], lit("#ff6a2a") if rage else lit("#e89a2a"))
        arms.append(names)
    # ------------------------------------------------------------------ two feeding tentacles with hooked clubs
    ww, wl = [5, 5, 4, 4, 3, 3, 3], [8, 8, 8, 7, 7, 6, 5]
    wcurl = [-8, -6, 6, 12, 16, 22, 30]
    whips = []
    for side, x in (("whip_r", -9), ("whip_l", 9)):
        m.bone(side, [x, 9, -10], "body", rotation=[0, 0, -16 if x < 0 else 16])    # splayed outward
        prev, y, names = side, 9, []
        for i, (w, L, rx) in enumerate(zip(ww, wl, wcurl)):
            b = f"{side}_{i}"
            m.bone(b, [x, y, -10], prev, rotation=[rx, 0, 0])
            m.cube(b, [x - w / 2, y, -10 - w / 2], [w, L, w], SK())
            if i >= 4:
                suckers(m, b, x, y + 1, -10 - w / 2, 2, 3, w=max(2, w - 1), rage=rage)
            names.append(b)
            prev, y = b, y + L
        club = f"{side}_club"
        m.bone(club, [x, y, -10], prev, rotation=[20, 0, 0])
        m.cube(club, [x - 3.5, y, -13.5], [7, 9, 7], SK())
        suckers(m, club, x, y + 1, -13.5, 3, 3, w=5, rage=rage)
        for (dx, dy) in ((-3, 3), (3, 5), (-2, 7), (2, 1)):                    # hooks on the club
            m.cube(club, [x + dx - 0.5, y + dy, -14.5], [1, 1, 1], solid("#e6dcc8"), rot=[40, 0, 0], pivot=[x + dx, y + dy, -14])
        m.cube(club, [x - 0.5, y + 9, -10.5], [1, 3, 1], solid("#e6dcc8"))       # the tip hook
        whips.append(names + [club])

    animations(m, arms, whips)
    return m


def animations(m, arms, whips):
    allarm = [nm for names in arms for nm in names]

    def arm_sway(length, amp):
        d = {}
        for k, names in enumerate(arms):
            for i, nm in enumerate(names):
                d[nm] = {"rotation": wave(length, lambda q, k=k, i=i: [S(q + k * 0.9 - i * 0.6) * amp * (0.3 + i * 0.22), 0,
                                                                         S(q * 0.6 + k) * amp * 0.35])}
        return d

    def whip_sway(length, amp):
        d = {}
        for names in whips:
            for i, nm in enumerate(names):
                d[nm] = {"rotation": wave(length, lambda q, i=i: [S(q - i * 0.5) * amp * (0.3 + i * 0.12), 0, S(q * 0.7 - i * 0.4) * amp * 0.5])}
        return d

    idle = {**arm_sway(4.0, 7), **whip_sway(4.0, 6)}
    idle["mantle"] = {"rotation": wave(4.0, lambda q: [S(q) * 2, S(q * 0.5) * 3, 0]),
                      "scale": wave(4.0, lambda q: [1 + 0.025 * S(q), 1 + 0.04 * S(q), 1 + 0.025 * S(q)])}
    idle["maw"] = {"scale": wave(4.0, lambda q: [1 + 0.05 * S(q * 2), 1 + 0.05 * S(q * 2), 1])}
    idle["beak_bot"] = {"rotation": wave(4.0, lambda q: [8 + S(q * 2) * 6, 0, 0])}
    idle["eye_r"] = {"rotation": keys((0, [0, 0, 0]), (2.9, [0, 0, 0]), (3.0, [0, 12, 0]), (3.6, [0, 12, 0]), (3.7, [0, 0, 0]), (4.0, [0, 0, 0]))}
    idle["eye_l"] = {"rotation": keys((0, [0, 0, 0]), (2.9, [0, 0, 0]), (3.0, [0, -12, 0]), (3.6, [0, -12, 0]), (3.7, [0, 0, 0]), (4.0, [0, 0, 0]))}
    anim(m, "idle", 4.0, idle)
    mv = {**arm_sway(1.6, 14), **whip_sway(1.6, 12)}
    anim(m, "move", 1.6, mv)
    # BEAK CRUSH: lunges forward, beak snaps
    anim(m, "attack", 0.8, {"body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-12, 0, 0]), (0.5, [14, 0, 0]), (0.8, [0, 0, 0]))},
                            "beak_bot": {"rotation": keys((0, [8, 0, 0]), (0.3, [50, 0, 0]), (0.5, [0, 0, 0]), (0.8, [8, 0, 0]))},
                            "beak_top": {"rotation": keys((0, [0, 0, 0]), (0.3, [-30, 0, 0]), (0.5, [0, 0, 0]), (0.8, [0, 0, 0]))}}, loop=False)
    # INHALE (3.0 s): the maw gapes and swells, the mantle heaves, arms draw inward
    inh = {"maw": {"scale": keys((0, [1, 1, 1]), (0.4, [1.5, 1.6, 1.2]), (2.6, [1.6, 1.7, 1.2]), (3.0, [1, 1, 1]))},
           "beak_bot": {"rotation": keys((0, [8, 0, 0]), (0.4, [65, 0, 0]), (2.6, [65, 0, 0]), (3.0, [8, 0, 0]))},
           "beak_top": {"rotation": keys((0, [0, 0, 0]), (0.4, [-40, 0, 0]), (2.6, [-40, 0, 0]), (3.0, [0, 0, 0]))},
           "mantle": {"scale": keys((0, [1, 1, 1]), (1.5, [1.12, 1.08, 1.12]), (2.6, [1.15, 1.1, 1.15]), (3.0, [1, 1, 1])),
                      "rotation": keys((0, [0, 0, 0]), (0.4, [-8, 0, 0]), (2.6, [-8, 0, 0]), (3.0, [0, 0, 0]))}}
    for names in arms:
        for i, nm in enumerate(names):
            inh[nm] = {"rotation": keys((0, [0, 0, 0]), (0.5, [-10 - i * 4, 0, 0]), (2.6, [-10 - i * 4, 0, 0]), (3.0, [0, 0, 0]))}
    anim(m, "inhale", 3.0, inh, loop=False)
    # SPIT: a violent heave
    anim(m, "spit", 1.0, {"maw": {"scale": keys((0, [1.5, 1.6, 1.2]), (0.15, [0.8, 0.8, 1.4]), (0.35, [1.4, 1.4, 1.3]), (1.0, [1, 1, 1]))},
                          "mantle": {"rotation": keys((0, [-8, 0, 0]), (0.15, [18, 0, 0]), (0.5, [-4, 0, 0]), (1.0, [0, 0, 0])),
                                     "scale": keys((0, [1.12, 1.08, 1.12]), (0.15, [0.9, 0.95, 0.9]), (1.0, [1, 1, 1]))},
                          "beak_bot": {"rotation": keys((0, [60, 0, 0]), (0.3, [70, 0, 0]), (1.0, [8, 0, 0]))}}, loop=False)
    # SWAT: the right feeding tentacle rears back and smashes forward-up (hits at 0.55)
    sw = {}
    wr = whips[0]
    for i, nm in enumerate(wr):
        sw[nm] = {"rotation": keys((0, [0, 0, 0]), (0.35, [-35 - i * 4, 0, 0]), (0.55, [30 + i * 3, 0, 0]), (0.8, [20, 0, 0]), (1.1, [0, 0, 0]))}
    sw["body"] = {"rotation": keys((0, [0, 0, 0]), (0.35, [0, 10, 0]), (0.55, [0, -8, 0]), (1.1, [0, 0, 0]))}
    anim(m, "swat", 1.1, sw, loop=False)
    # INK: the mantle contracts, arms flare
    ink = {"mantle": {"scale": keys((0, [1, 1, 1]), (0.3, [1.15, 1.05, 1.15]), (0.5, [0.88, 0.95, 0.88]), (1.0, [1, 1, 1]))}}
    for names in arms:
        for i, nm in enumerate(names):
            ink[nm] = {"rotation": keys((0, [0, 0, 0]), (0.4, [-18, 0, 0]), (0.6, [10, 0, 0]), (1.0, [0, 0, 0]))}
    anim(m, "ink", 1.0, ink, loop=False)
    # QUAKE: every arm rears then slams the floor (at 0.7)
    qk = {"body": {"position": keys((0, [0, 0, 0]), (0.5, [0, 2, 0]), (0.7, [0, -1, 0]), (1.2, [0, 0, 0]))}}
    for names in arms:
        for i, nm in enumerate(names):
            qk[nm] = {"rotation": keys((0, [0, 0, 0]), (0.5, [-30 - i * 6, 0, 0]), (0.7, [18, 0, 0]), (1.2, [0, 0, 0]))}
    for names in whips:
        for i, nm in enumerate(names):
            qk[nm] = {"rotation": keys((0, [0, 0, 0]), (0.5, [-20, 0, 0]), (0.7, [25, 0, 0]), (1.2, [0, 0, 0]))}
    anim(m, "quake", 1.2, qk, loop=False)
    # WHIRL (enraged maelstrom): the whole body turns, arms trailing
    wh = {"body": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 180, 0]), (2.0, [0, 360, 0]), (2.5, [0, 360, 0]))}}
    for names in arms:
        for i, nm in enumerate(names):
            wh[nm] = {"rotation": keys((0, [0, 0, 0]), (0.3, [-12, 0, -20 - i * 5]), (2.2, [-12, 0, -20 - i * 5]), (2.5, [0, 0, 0]))}
    anim(m, "whirl", 2.5, wh, loop=False)
    # RISE (surfacing from the abyss, 3 s): coiled tight, then unfurling, eyes wide
    rs = {"root": {"position": keys((0, [0, -20, 0]), (2.2, [0, 0, 0]), (3.0, [0, 0, 0]))},
          "mantle": {"rotation": keys((0, [25, 0, 0]), (2.2, [-15, 0, 0]), (3.0, [0, 0, 0]))}}
    for names in arms:
        for i, nm in enumerate(names):
            rs[nm] = {"rotation": keys((0, [-40 - i * 10, 0, 0]), (2.2, [-40 - i * 10, 0, 0]), (2.6, [10, 0, 0]), (3.0, [0, 0, 0]))}
    rs["beak_bot"] = {"rotation": keys((0, [8, 0, 0]), (2.2, [8, 0, 0]), (2.5, [60, 0, 0]), (3.0, [8, 0, 0]))}
    anim(m, "rise", 3.0, rs, loop=False)
    # ABYSSAL WRATH (enrage): rears up, every arm raised, maw roaring
    wr_ = {"body": {"rotation": keys((0, [0, 0, 0]), (0.6, [-18, 0, 0]), (1.6, [-18, 0, 0]), (2.0, [0, 0, 0])),
                    "position": keys((0, [0, 0, 0]), (0.6, [0, 4, 0]), (1.6, [0, 4, 0]), (2.0, [0, 0, 0]))},
           "maw": {"scale": keys((0, [1, 1, 1]), (0.6, [1.6, 1.7, 1.2]), (1.6, [1.6, 1.7, 1.2]), (2.0, [1, 1, 1]))},
           "beak_bot": {"rotation": keys((0, [8, 0, 0]), (0.6, [70, 0, 0]), (1.6, [70, 0, 0]), (2.0, [8, 0, 0]))}}
    for names in arms + whips:
        for i, nm in enumerate(names):
            wr_[nm] = {"rotation": keys((0, [0, 0, 0]), (0.6, [-35 - i * 6, 0, 0]), (1.6, [-35 - i * 6, 0, 0]), (2.0, [0, 0, 0]))}
    anim(m, "wrath", 2.0, wr_, loop=False)
    # HARPOON SPIT: rears back, then the maw snaps forward and spits (fires at 0.6)
    anim(m, "harpoon_spit", 1.0, {
        "mantle": {"rotation": keys((0, [0, 0, 0]), (0.4, [-14, 0, 0]), (0.6, [12, 0, 0]), (1.0, [0, 0, 0]))},
        "maw": {"scale": keys((0, [1, 1, 1]), (0.4, [1.3, 1.3, 1.1]), (0.55, [0.8, 0.8, 1.5]), (0.7, [1.4, 1.4, 1.2]), (1.0, [1, 1, 1]))},
        "beak_bot": {"rotation": keys((0, [8, 0, 0]), (0.5, [20, 0, 0]), (0.6, [70, 0, 0]), (1.0, [8, 0, 0]))},
        "beak_top": {"rotation": keys((0, [0, 0, 0]), (0.5, [-10, 0, 0]), (0.6, [-40, 0, 0]), (1.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [-6, 0, 0]), (0.6, [8, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    # SHIELD (2.6 s): every arm rears up and folds in over the mantle like a cage, trembling with strain
    shd = {"mantle": {"scale": keys((0, [1, 1, 1]), (0.6, [0.92, 0.95, 0.92]), (2.6, [0.9, 0.93, 0.9]))}}
    for names in arms:
        for i, nm in enumerate(names):
            base = [-112, 0, 0] if i == 0 else [30 + i * 6, 0, 0]
            shd[nm] = {"rotation": keys((0, [0, 0, 0]), (0.6, base), (1.4, [base[0] - 2, 0, 2]), (2.0, [base[0] + 2, 0, -2]),
                                        (2.6, [base[0], 0, 0]))}
    for names in whips:
        for i, nm in enumerate(names):
            shd[nm] = {"rotation": keys((0, [0, 0, 0]), (0.6, [18 + i * 4, 0, 0]), (2.6, [18 + i * 4, 0, 0]))}
    anim(m, "shield", 2.6, shd, loop=False)
    # BURST: the cage explodes outward (at 0.15), arms flung wide, then settle
    bst = {"mantle": {"scale": keys((0, [0.9, 0.93, 0.9]), (0.15, [1.15, 1.1, 1.15]), (0.5, [1, 1, 1]), (1.2, [1, 1, 1]))},
           "body": {"position": keys((0, [0, 0, 0]), (0.15, [0, 2, 0]), (0.6, [0, 0, 0]))}}
    for names in arms:
        for i, nm in enumerate(names):
            start = [-112, 0, 0] if i == 0 else [30 + i * 6, 0, 0]
            bst[nm] = {"rotation": keys((0, start), (0.15, [12, 0, 0] if i == 0 else [-18 - i * 4, 0, 0]), (0.6, [4, 0, 0] if i == 0 else [-8, 0, 0]),
                                        (1.2, [0, 0, 0]))}
    for names in whips:
        for i, nm in enumerate(names):
            bst[nm] = {"rotation": keys((0, [18 + i * 4, 0, 0]), (0.15, [-25, 0, 0]), (1.2, [0, 0, 0]))}
    anim(m, "burst", 1.2, bst, loop=False)
    # an arm regrows / shudders when an arm dies
    anim(m, "hurt_arm", 0.8, {"mantle": {"rotation": keys((0, [0, 0, 0]), (0.2, [10, 8, 0]), (0.5, [-6, -6, 0]), (0.8, [0, 0, 0]))},
                              "beak_bot": {"rotation": keys((0, [8, 0, 0]), (0.2, [55, 0, 0]), (0.8, [8, 0, 0]))}}, loop=False)


# =====================================================================================
# KRAKEN ARM - phase 1: a colossal tentacle bursting from the seabed
# =====================================================================================
def kraken_arm(seed, skin="normal"):
    rage = skin == "enraged"
    m = Rig("kraken_arm", 128, 128, seed)
    SK = lambda: skin_of(rage)
    m.bone("root", [0, 0, 0])
    # the burst seabed mound it erupted from
    RUB = over(noise(SEABED), speckle("#3a4048", 0.08), spots("#0a0a0c", 0.1))
    m.cube("root", [-9, 0, -9], [18, 2, 18], RUB)
    for (x, z, w, h) in ((-9, -9, 5, 4), (5, -8, 4, 3), (-8, 5, 4, 3), (6, 6, 3, 4), (-2, 7, 5, 2), (-10, -1, 3, 3)):
        m.cube("root", [x, 2, z], [w, h, w - 1], RUB)
    widths = [11, 10, 9, 8, 7, 5, 4, 3]
    lengths = [10, 10, 9, 9, 8, 7, 6, 5]
    curl = [0, -6, -8, 4, 14, 24, 32, 38]
    prev, y, names = "root", 2, []
    for i, (w, L, rx) in enumerate(zip(widths, lengths, curl)):
        b = f"s{i}"
        m.bone(b, [0, y, 0], prev, rotation=[rx, 0, 0] if rx else None)
        m.cube(b, [-w / 2, y, -w / 2], [w, L, w], SK())
        if w >= 4:
            suckers(m, b, 0, y + 1, -w / 2, max(1, L // 3), 3, w=max(2, w - 3), h=2, rage=rage)
        if i in (1, 3, 5):                                                       # hooked spines on the back
            m.cube(b, [-0.5, y + L / 2, w / 2], [1, 1, 3], solid("#d8ccb4"), rot=[-35, 0, 0], pivot=[0, y + L / 2, w / 2])
        names.append(b)
        prev, y = b, y + L
    m.bone("claw", [0, y, 0], prev, rotation=[30, 0, 0])
    m.cube("claw", [-1, y, -1], [2, 3, 2], SK())
    m.cube("claw", [-0.5, y + 2, -2.5], [1, 1, 3], solid("#e6dcc8"), rot=[35, 0, 0], pivot=[0, y + 2.5, -1])
    m.cube("claw", [-0.5, y + 3, -0.5], [1, 3, 1], lit("#ff6a2a") if rage else solid("#e6dcc8"))
    segs = names + ["claw"]

    def sway(length, amp):
        return {s: {"rotation": wave(length, lambda q, i=i: [S(q - i * 0.55) * amp * (0.35 + i * 0.18), 0,
                                                             S(q * 0.5 - i * 0.4) * amp * 0.45])} for i, s in enumerate(segs)}
    anim(m, "idle", 3.2, sway(3.2, 6))
    anim(m, "move", 1.4, sway(1.4, 11))
    # SLAM: rears back, crashes forward onto the floor (impact at 0.9)
    anim(m, "attack", 1.3, {s: {"rotation": keys((0, [0, 0, 0]), (0.6, [-18 - i * 5, 0, 0]), (0.9, [22 + i * 7, 0, 0]),
                                                 (1.1, [20 + i * 6, 0, 0]), (1.3, [0, 0, 0]))} for i, s in enumerate(segs)}, loop=False)
    # SWEEP: winds to one side, lashes across (hit at 0.7)
    anim(m, "sweep", 1.2, {s: {"rotation": keys((0, [0, 0, 0]), (0.45, [8, 0, 30 + i * 6]), (0.7, [10, 0, -35 - i * 7]),
                                                (0.9, [8, 0, -30 - i * 6]), (1.2, [0, 0, 0]))} for i, s in enumerate(segs)}, loop=False)
    # GRAB: lashes out and coils tight around its prey, holding it high (2.5 s)
    anim(m, "grab", 2.5, {s: {"rotation": keys((0, [0, 0, 0]), (0.3, [20 + i * 4, 0, 0]), (0.6, [-6 + i * 12, 0, 0]),
                                               (2.2, [-6 + i * 12, 0, 0]), (2.5, [0, 0, 0]))} for i, s in enumerate(segs)}, loop=False)
    # FLING: uncoils violently toward the abyss
    anim(m, "fling", 0.9, {s: {"rotation": keys((0, [-6 + i * 12, 0, 0]), (0.25, [-30 - i * 4, 0, 0]), (0.5, [35, 0, 0]), (0.9, [0, 0, 0]))}
                           for i, s in enumerate(segs)}, loop=False)
    # RISE: bursts up out of the seabed
    rise = {s: {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 0, 0]), (0.9, [-10, 0, 0]), (1.4, [0, 0, 0]))} for s in segs}
    rise["s0"] = {"position": keys((0, [0, -60, 0]), (0.9, [0, 4, 0]), (1.4, [0, 0, 0]))}
    anim(m, "rise", 1.4, rise, loop=False)
    # RETRACT (death): writhes and sinks into the seabed
    ret = {s: {"rotation": wave(1.8, lambda q, i=i: [S(q * 3 - i) * 25, 0, S(q * 2 - i) * 20])} for i, s in enumerate(segs)}
    ret["s0"] = {"position": keys((0, [0, 0, 0]), (0.6, [0, 2, 0]), (1.8, [0, -64, 0]))}
    anim(m, "retract", 1.8, ret, loop=False)
    return m


# =====================================================================================
# HARPOON - the Kraken spits these (KrakenHarpoonEntity) and the whalers' winches fire them (HarpoonEntity).
# A heavy toggle harpoon pointing -Z: barnacle-crusted iron head with a four-way barb, iron socket, rope lashing,
# a long wet shaft, a length of rope trailing from the butt. ~40 px long (2.5 blocks at scale 1).
# =====================================================================================
def harpoon(seed):
    m = Rig("harpoon", 64, 64, seed)
    IRON = over(noise(["#4a4e56", "#5a5f68", "#6a707a"]), spots("#7a4a2a", 0.18), spots("#a8a89a", 0.06))   # rust + barnacles
    EDGE = noise(["#8a9098", "#a0a6ae"])
    WOOD = over(noise(["#3a2616", "#4a3220", "#2e1e12"]), spots("#1a120a", 0.1))
    ROPE = over(noise(["#a8946a", "#8a7852", "#b8a47a"]), spots("#6a5a3a", 0.2))
    m.bone("root", [0, 0, 0])
    m.bone("head", [0, 0, -8], "root")
    # a broad flat blade + the same blade standing vertical = a four-way barbed head
    for (z, w) in ((-12, 5), (-14, 3.5), (-16, 2.5)):
        m.cube("head", [-w / 2, -0.5, z], [w, 1, 2], IRON if w > 3 else EDGE)
    m.cube("head", [-0.5, -0.5, -18], [1, 1, 2], EDGE)                                      # the point
    for (z, h) in ((-12, 5), (-14, 3.5), (-16, 2.5)):
        m.cube("head", [-0.4, -h / 2, z + 0.01], [0.8, h, 2], IRON if h > 3 else EDGE)
    m.pair("head", "head", [-4, -0.5, -12], [1.5, 1, 3], EDGE, rot=[0, -32, 0], pivot=[-2.5, 0, -12])   # side barbs
    m.cube("head", [-0.4, 2.2, -12], [0.8, 1.5, 3], EDGE, rot=[32, 0, 0], pivot=[0, 2.5, -12])          # top/bottom barbs
    m.cube("head", [-0.4, -3.7, -12], [0.8, 1.5, 3], EDGE, rot=[-32, 0, 0], pivot=[0, -2.5, -12])
    m.cube("head", [-1, -1, -10], [2, 2, 3], IRON)                                          # socket
    m.bone("shaft", [0, 0, -7], "root")
    m.cube("shaft", [-0.6, -0.6, -7], [1.2, 1.2, 30], WOOD)
    for z in (-6, -3.5, 14):
        m.cube("shaft", [-0.9, -0.9, z], [1.8, 1.8, 1.2], ROPE)                                # lashings
    m.bone("rope", [0, 0, 23], "shaft")
    for i, (x, y) in enumerate(((0, 0), (0.6, -0.4), (1.2, -1.2), (1.0, -2.2), (0.2, -2.9))):
        m.cube("rope", [x - 0.4, y - 0.4, 23 + i * 2], [0.8, 0.8, 2], ROPE)
    anim(m, "idle", 1.0, {"rope": {"rotation": wave(1.0, lambda q: [S(q) * 10, S(q * 2) * 12, 0])}})
    return m
