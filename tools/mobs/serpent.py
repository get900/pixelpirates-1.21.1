"""SEA SERPENT (boss 2/10) - rebuilt 2026-09-29 against the phase-2 concept render.

A dark-teal wyrm: big crested head with swept horns, barbels and a hinged toothed jaw, glowing yellow
eyes, indigo dorsal spines, glowing yellow coral growing along the flanks, pectoral fins and a broad
tail fluke. Four PEARL PLATES ride on its back (bones plate0..plate3) - SeaSerpentEntity hides one per
Tideward Stone destroyed.

Skins share one geometry (tools/gen_mob_roster.py verifies the UV layout):
  normal  - textures/entity/sea_serpent.png
  enraged - textures/entity/sea_serpent_enraged.png, "Stormscale": blackened scales split by glowing
            cyan lightning, white-hot eyes, electric coral, cracked plates.
Clips: idle, move, attack (bite), lash, coil, jet, ambush, maelstrom, surge, brood, stormscale (enrage), shed.
"""
from mobkit import A, Rig, counter, lit, noise, over, solid, speckle, spots, veins, drips, anim, keys, wave, S

SIZES = [8, 8, 8, 8, 7, 7, 6, 6, 5, 4, 3, 3]
PLATE_SEGS = [1, 3, 5, 7]
CORAL_SEGS = {2, 5, 8}
Y = 8


def sea_serpent(seed, skin="normal"):
    storm = skin == "enraged"
    m = Rig("sea_serpent", 128, 128, seed)
    SKIN = ["#0a1c26", "#0e2430", "#122a38", "#16323e"] if storm else ["#123a44", "#17474f", "#1c545a", "#236266"]
    BELLY = ["#1a3a44", "#1e4450", "#24505a"] if storm else ["#2a7a74", "#338a80", "#3c988c"]
    SPINE = ["#1a1238", "#221848", "#2a1e58"] if storm else ["#241a4a", "#2e2260", "#3a2c78"]
    skin_ov = [veins("#6fe8ff", 0.05, glow=True, length=(3, 7))] if storm else [speckle("#0e2e36", 0.05)]
    body = lambda: over(counter(SKIN, BELLY, 0.62, jag=False), *skin_ov)
    spine = over(noise(SPINE), drips("#9ff4ff", 0.5, glow=True, maxlen=1)) if storm else noise(SPINE)
    eye = "!#e8ffff" if storm else "!#ffd24a"
    coral = lit("#6fe8ff") if storm else lit("#ffd24a")
    pearl = over(noise(["#9aa8ac", "#aeb8bc", "#c0c8cc"]), veins("#6fe8ff", 0.08, glow=True, length=(2, 5))) if storm \
        else over(noise(["#8cbcc0", "#a4ccce", "#bcdcd8"]), spots("#e8c8dc", 0.10), spots("#d8f0ec", 0.06), spots("#6a9ca4", 0.05))

    m.bone("root", [0, Y, 0])
    # ------------------------------------------------------------------ head
    m.bone("head", [0, Y, -1], "root")
    hw, hl, hh = 12, 16, 10
    top = Y + hh / 2
    E = dict(E=eye, e="!#fff4b0" if not storm else "!#ffffff", k="#0a0c10")
    m.cube("head", [-hw / 2, Y - hh / 2 + 1, -hl - 1], [hw, hh, hl], body(),
           art={"east": A.at(hl, hh, {(hl - 7, 2): "E", (hl - 6, 2): "E", (hl - 5, 2): "E", (hl - 7, 3): "E", (hl - 6, 3): "e", (hl - 5, 3): "E", (hl - 8, 2): "k", (hl - 8, 3): "k"}, E),
                "west": A.at(hl, hh, {(6, 2): "E", (5, 2): "E", (4, 2): "E", (6, 3): "E", (5, 3): "e", (4, 3): "E", (7, 2): "k", (7, 3): "k"}, E)})
    m.cube("head", [-hw / 2 + 2, Y - hh / 2 + 2, -hl - 5], [hw - 4, hh - 3, 4], body(),                  # snout
           art={"north": A.at(hw - 4, hh - 3, {(1, 1): "k", (hw - 6, 1): "k"}, E)})
    m.cube("head", [-hw / 2 + 1, top + 1, -hl + 1], [hw - 2, 2, hl - 3], noise(SKIN))                   # brow ridge
    # crest: three fin plates rising along the skull
    for i, (z, h) in enumerate(((-12, 4), (-8, 6), (-4, 5))):
        m.cube("head", [-0.5, top + 2, z], [1, h, 4], spine, rot=[-25, 0, 0], pivot=[0, top + 2, z + 2])
    # swept-back horns + cheek spikes
    for sx in (-1, 1):
        m.cube("head", [sx * (hw / 2) - (1 if sx > 0 else 0), top, -6], [1, 2, 9], noise(SPINE),
               rot=[-22, sx * 18, 0], pivot=[sx * hw / 2, top, -2])
        m.cube("head", [sx * (hw / 2) - (1 if sx > 0 else 0), Y - 1, -4], [1, 5, 1], noise(SPINE),
               rot=[-40, 0, -sx * 35], pivot=[sx * hw / 2, Y, -3])
    # barbels: two long whiskers trailing from the snout
    m.bone("barbel_r", [-hw / 2 + 2, Y - 1, -hl - 3], "head")
    m.cube("barbel_r", [-hw / 2 + 1, Y - 8, -hl - 3], [1, 7, 1], noise(BELLY), rot=[25, 0, 20], pivot=[-hw / 2 + 2, Y - 1, -hl - 3])
    m.bone("barbel_l", [hw / 2 - 2, Y - 1, -hl - 3], "head")
    m.cube("barbel_l", [hw / 2 - 2, Y - 8, -hl - 3], [1, 7, 1], noise(BELLY), rot=[25, 0, -20], pivot=[hw / 2 - 2, Y - 1, -hl - 3])
    # jaw with a double row of teeth
    m.bone("jaw", [0, Y - hh / 2 + 1, -2], "head", rotation=[14, 0, 0])
    m.cube("jaw", [-hw / 2 + 1, Y - hh / 2 - 2, -hl - 3], [hw - 2, 3, hl + 1], noise(BELLY),
           art={"up": A.at(hw - 2, hl + 1, {(x, z): "m" for x in range(1, hw - 3) for z in range(1, hl - 1)}, m="#2a1016")})
    for x in (-hw / 2 + 1, hw / 2 - 2):
        for z in (-hl - 3, -hl, -hl + 3, -hl + 6):
            m.cube("jaw", [x, Y - hh / 2 + 1, z], [1, 2, 1], solid("#e6e0cc", edge=0))
            m.cube("head", [x, Y - hh / 2 - 1, z + 1], [1, 2, 1], solid("#e6e0cc", edge=0))

    # ------------------------------------------------------------------ body chain
    prev, z = "head", 0
    names = []
    for i, s in enumerate(SIZES):
        b = f"seg{i}"
        m.bone(b, [0, Y, z], prev)
        m.cube(b, [-s / 2, Y - s / 2, z], [s, s, s + 1], body())
        m.cube(b, [-0.5, Y + s / 2 - 1, z + 1], [1, max(3, 8 - i // 2), s - 1], spine, rot=[-32, 0, 0], pivot=[0, Y + s / 2, z + 1])
        if i in CORAL_SEGS:
            cx = -s / 2 - 1 if i % 2 else s / 2
            m.cube(b, [cx, Y - 1, z + s / 2 - 1], [1, 2, 1], coral)            # stem, branch and tip stack
            m.cube(b, [cx, Y + 1, z + s / 2 - 2], [1, 1, 3], coral)            # (touching, never overlapping)
            m.cube(b, [cx, Y + 2, z + s / 2], [1, 2, 1], coral)
        if i == 0:   # pectoral fins
            m.pair(b, b, [-s / 2 - 5, Y - 3, z + 2], [5, 1, 6], spine, rot=[0, 20, -25], pivot=[-s / 2, Y - 2, z + 4])
        if i in PLATE_SEGS:
            k = PLATE_SEGS.index(i)
            pb = f"plate{k}"
            m.bone(pb, [0, Y + s / 2, z], b)
            m.cube(pb, [-s / 2 - 1, Y + s / 2, z + 1], [s + 2, 1, s - 1], pearl)            # rests ON the segment top
            m.pair(pb, pb, [-s / 2 - 2, Y + s / 2 - 3, z + 1], [1, 4, s - 1], pearl)         # side lips
        names.append(b)
        prev, z = b, z + s + 1
    m.bone("tailfin", [0, Y, z], prev)
    m.cube("tailfin", [-0.5, Y - 1, z - 1], [1, 9, 7], spine, rot=[-40, 0, 0], pivot=[0, Y, z])
    m.cube("tailfin", [-0.5, Y - 8, z - 1], [1, 7, 6], spine, rot=[40, 0, 0], pivot=[0, Y, z])
    animations(m, names)
    return m


def animations(m, names, k=0.7):
    n = len(names)

    def amp_i(i, a):
        return a * (0.35 + 0.65 * i / max(1, n - 1))

    def swim(length, a):
        d = {nm: {"rotation": wave(length, lambda q, i=i: [S(q * 0.5 - i * 0.5) * a * 0.12, S(q - k * i) * amp_i(i, a), 0])}
             for i, nm in enumerate(names)}
        d["tailfin"] = {"rotation": wave(length, lambda q: [0, S(q - k * n) * a * 1.6, 0])}
        d["head"] = {"rotation": wave(length, lambda q: [S(q * 0.5) * 3, -S(q + 0.6) * a * 0.35, 0])}
        d["jaw"] = {"rotation": wave(length, lambda q: [S(q) * 4, 0, 0])}
        d["barbel_r"] = {"rotation": wave(length, lambda q: [S(q - 1) * 12, 0, S(q) * 8])}
        d["barbel_l"] = {"rotation": wave(length, lambda q: [S(q - 1) * 12, 0, -S(q) * 8])}
        return d

    anim(m, "idle", 3.0, swim(3.0, 4.5))
    anim(m, "move", 1.4, swim(1.4, 9.0))
    anim(m, "attack", 0.6, {
        "head": {"rotation": keys((0, [0, 0, 0]), (0.18, [-22, 0, 0]), (0.32, [14, 0, 0]), (0.6, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (0.18, [0, 1, 2]), (0.32, [0, 0, -7]), (0.6, [0, 0, 0]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.18, [38, 0, 0]), (0.32, [-12, 0, 0]), (0.6, [0, 0, 0]))}}, loop=False)
    # tail lash: the back half winds up, then whips across (impact 0.5 = windup 10)
    lash = {}
    for i, nm in enumerate(names):
        w = max(0.0, (i - 4) / (n - 4))
        lash[nm] = {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 35 * w, 0]), (0.5, [0, -55 * w, 0]), (0.9, [0, 0, 0]))}
    lash["tailfin"] = {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 40, 0]), (0.5, [0, -70, 0]), (0.9, [0, 0, 0]))}
    anim(m, "lash", 0.9, lash, loop=False)
    # constrict: the body wraps into a loop (grab lands at 0.6 = windup 12)
    coil = {nm: {"rotation": keys((0, [0, 0, 0]), (0.6, [0, 30, 4]), (1.0, [0, 30, 4]), (1.3, [0, 0, 0]))} for nm in names}
    coil["head"] = {"rotation": keys((0, [0, 0, 0]), (0.6, [10, -25, 0]), (1.0, [10, -25, 0]), (1.3, [0, 0, 0]))}
    coil["jaw"] = {"rotation": keys((0, [0, 0, 0]), (0.5, [30, 0, 0]), (0.62, [0, 0, 0]), (1.3, [0, 0, 0]))}
    anim(m, "coil", 1.3, coil, loop=False)
    # pressure jet: rear back, jaws gape, blast at 0.8 (windup 16)
    anim(m, "jet", 1.2, {
        "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [-28, 0, 0]), (0.8, [6, 0, 0]), (1.0, [6, 0, 0]), (1.2, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (0.5, [0, 1, 3]), (0.8, [0, 0, -2]), (1.2, [0, 0, 0]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.5, [20, 0, 0]), (0.8, [45, 0, 0]), (1.0, [45, 0, 0]), (1.2, [0, 0, 0]))},
        names[0]: {"rotation": keys((0, [0, 0, 0]), (0.5, [-12, 0, 0]), (0.8, [8, 0, 0]), (1.2, [0, 0, 0]))}}, loop=False)
    # silt ambush: a steep dive out of sight (it turns invisible at 0.4)
    anim(m, "ambush", 1.8, {
        "root": {"rotation": keys((0, [0, 0, 0]), (0.4, [45, 0, 0]), (1.6, [45, 0, 0]), (1.8, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (0.4, [0, -10, 0]), (1.6, [0, -10, 0]), (1.8, [0, 0, 0]))}}, loop=False)
    # maelstrom: a full spinning roll that drags the water with it
    mael = {"root": {"rotation": keys((0, [0, 0, 0]), (0.7, [0, 180, 0]), (1.4, [0, 360, 0]))}}
    for i, nm in enumerate(names):
        mael[nm] = {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 12, 0]), (1.0, [0, 12, 0]), (1.4, [0, 0, 0]))}
    anim(m, "maelstrom", 1.4, mael, loop=False)
    # storm surge: a rigid, shivering charge, then release
    surge = {"head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-20, 0, 0]), (1.1, [-20, 0, 0]), (1.5, [0, 0, 0]))},
             "jaw": {"rotation": keys((0, [0, 0, 0]), (0.3, [25, 0, 0]), (1.1, [35, 0, 0]), (1.5, [0, 0, 0]))}}
    for i, nm in enumerate(names):
        surge[nm] = {"rotation": keys(*[(t * 0.1, [((-1) ** (t + i)) * 3 if 2 < t < 11 else 0, 0, 0]) for t in range(16)])}
    anim(m, "surge", 1.5, surge, loop=False)
    anim(m, "brood", 1.0, {
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-40, 0, 0]), (0.8, [-40, 0, 0]), (1.0, [0, 0, 0]))},
        "jaw": {"rotation": keys((0, [0, 0, 0]), (0.4, [40, 0, 0]), (0.8, [40, 0, 0]), (1.0, [0, 0, 0]))},
        names[0]: {"rotation": keys((0, [0, 0, 0]), (0.4, [-20, 0, 0]), (0.8, [-20, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    # STORMSCALE (enrage): head flung up in a roar, the whole body convulses
    storm = {"head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-50, 0, 0]), (1.4, [-50, 0, 0]), (1.8, [0, 0, 0]))},
             "jaw": {"rotation": keys((0, [0, 0, 0]), (0.4, [50, 0, 0]), (1.4, [50, 0, 0]), (1.8, [0, 0, 0]))}}
    for i, nm in enumerate(names):
        storm[nm] = {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 18 * (-1) ** i, 0]), (0.8, [0, -18 * (-1) ** i, 0]),
                                      (1.2, [0, 12 * (-1) ** i, 0]), (1.8, [0, 0, 0]))}
    anim(m, "stormscale", 1.8, storm, loop=False)
    # a plate shattered off: the body jerks as if struck
    shed = {"head": {"rotation": keys((0, [0, 0, 0]), (0.15, [25, 0, 0]), (0.5, [-15, 0, 0]), (1.2, [0, 0, 0]))}}
    for i, nm in enumerate(names):
        shed[nm] = {"rotation": keys((0, [0, 0, 0]), (0.15, [(-1) ** i * 10, 0, 0]), (0.5, [0, (-1) ** i * 8, 0]), (1.2, [0, 0, 0]))}
    anim(m, "shed", 1.2, shed, loop=False)
