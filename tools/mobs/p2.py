"""Phase 2 roster: kraken_tentacle, reefback_fish, void_squid, sea_serpent, kraken (boss)."""
import math

from mobkit import A, Rig, bands, counter, lit, noise, over, solid, spots, veins, drips, speckle, anim, keys, wave, S, C

INK = ["#0c0e12", "#12151a", "#181c22", "#1e232a"]           # kraken skin (near-black, blue-grey sheen)
AMBER = ["#8a5a1e", "#a8702a", "#c08838"]
SEABED = ["#1a1c20", "#22262b", "#2a2f35", "#323840"]


def sucker_col(m, bone, x, y0, z, n, step, w=3, h=2):
    """A column of amber sucker pads with dark centres on the FRONT (-Z) of a segment."""
    for i in range(n):
        m.cube(bone, [x - w / 2, y0 + i * step, z - 1], [w, h, 1], noise(AMBER),
               art={"north": A.at(w, h, {(w // 2, h // 2): "d"}, d="#3a2410")})


# =====================================================================================
# KRAKEN TENTACLE - black tentacle rising from a stone plinth, curling forward
# =====================================================================================
def tentacle_chain(m, root, base_y, widths, lengths, curl, skin, suck=True, prefix="t", tip_glow=None):
    """Stacked segments (each its own bone, pivot at its base). `curl` = rest X-rotation per segment
    (+X tips it FORWARD). Returns bone names."""
    prev, y, names = root, base_y, []
    for i, (w, L, rx) in enumerate(zip(widths, lengths, curl)):
        b = f"{prefix}{i}"
        m.bone(b, [0, y, 0], prev, rotation=[rx, 0, 0] if rx else None)
        m.cube(b, [-w / 2, y, -w / 2], [w, L, w], skin)
        if suck and w >= 3:
            sucker_col(m, b, 0, y + 1, -w / 2, max(1, L // 3), 3, w=max(2, w - 2))
        names.append(b)
        prev, y = b, y + L
    if tip_glow:
        m.cube(prev, [-0.5, y, -0.5], [1, 1, 1], lit(tip_glow))
    return names


def tentacle_anims(m, segs, sway=6.0):
    """Offsets only - GeckoLib ADDS keyframes to the bones' rest curl."""
    idle = {s: {"rotation": wave(3.0, lambda q, i=i: [S(q - i * 0.6) * sway * (0.4 + i * 0.25), 0,
                                                       S(q * 0.5 - i * 0.4) * sway * 0.5])} for i, s in enumerate(segs)}
    anim(m, "idle", 3.0, idle)
    anim(m, "move", 1.5, {s: {"rotation": wave(1.5, lambda q, i=i: [S(q - i * 0.7) * sway * 1.8, 0, S(q - i) * sway])}
                          for i, s in enumerate(segs)})
    # attack: rear back then whip down
    anim(m, "attack", 0.7, {s: {"rotation": keys((0, [0, 0, 0]), (0.25, [-25 - i * 6, 0, 0]), (0.4, [25 + i * 5, 0, 0]), (0.7, [0, 0, 0]))}
                            for i, s in enumerate(segs)}, loop=False)
    anim(m, "special", 1.2, {s: {"rotation": keys((0, [0, 0, 0]), (0.4, [-20 - i * 6, 0, 0]), (0.6, [-20 - i * 6, 0, 0]),
                                                  (0.8, [30, 0, 0]), (1.2, [0, 0, 0]))} for i, s in enumerate(segs)}, loop=False)
    anim(m, "special2", 1.0, {s: {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 0, 35 + i * 8]), (0.7, [0, 0, -35 - i * 8]), (1.0, [0, 0, 0]))}
                              for i, s in enumerate(segs)}, loop=False)


def kraken_tentacle(seed):
    m = Rig("kraken_tentacle", 64, 64, seed)
    m.bone("root", [0, 0, 0])
    m.cube("root", [-8, 0, -8], [16, 2, 16], over(noise(SEABED), speckle("#3a4048", 0.06)),
           art={"up": A.at(16, 16, {(x, y): "k" for x in range(16) for y in range(16)
                                    if (x in (1, 14) or y in (1, 14)) and 1 <= x <= 14 and 1 <= y <= 14}, k="#14161a")})
    widths, lengths, curl = [7, 6, 6, 5, 4, 3], [9, 7, 6, 5, 5, 4], [0, -4, 18, 36, 42, 38]
    segs = tentacle_chain(m, "root", 2, widths, lengths, curl, over(noise(INK), speckle("#2c333c", 0.08)))
    tentacle_anims(m, segs)
    return m


# =====================================================================================
# REEFBACK FISH - gentle pale-blue fish carrying a coral reef on its back
# =====================================================================================
def coral_branch(m, bone, x, y, z, color, h=6, seed=0, glow=False):
    """Thin branching coral: trunk + 2-3 side twigs (1 px wide)."""
    mat = lit(color) if glow else noise([color, color])
    m.cube(bone, [x, y, z], [1, h, 1], mat)
    m.cube(bone, [x - 2, y + h // 2, z], [2, 1, 1], mat)
    m.cube(bone, [x - 2, y + h // 2 + 1, z], [1, 2, 1], mat)
    m.cube(bone, [x + 1, y + h // 3, z], [2, 1, 1], mat)
    m.cube(bone, [x + 2, y + h // 3 + 1, z], [1, 3, 1], mat)


def reefback_fish(seed):
    m = Rig("reefback_fish", 64, 64, seed)
    BLUE = ["#3a8ec0", "#4a9ecf", "#56a8d6", "#62b2dc"]
    PALE = ["#7cc4e4", "#8acdea", "#98d6ee"]
    m.bone("root", [0, 5, 0])
    m.bone("body", [0, 5, 0], "root")
    body = over(counter(BLUE, PALE, 0.72, jag=False), spots("#d8d49a", 0.05, faces=("east", "west")))
    m.cube("body", [-5, 1, -8], [10, 9, 14], body)
    m.bone("head", [0, 5, -8], "body")
    eye = {(1, 2): "w", (2, 2): "w", (1, 3): "k", (2, 3): "W", (0, 2): "o", (3, 2): "o"}
    m.cube("head", [-5, 1, -15], [10, 9, 7], counter(BLUE, PALE, 0.72, jag=False),
           art={"east": A.at(7, 9, {(x + 1, y): c for (x, y), c in eye.items()} | {(x, 6): "m" for x in range(0, 6)},
                             w="#ece8f0", k="#141820", W="#b8b4d8", o="#2a6a9a", m="#1e4e72"),
                "west": A.at(7, 9, {(5 - x, y): c for (x, y), c in eye.items()} | {(x, 6): "m" for x in range(1, 7)},
                             w="#ece8f0", k="#141820", W="#b8b4d8", o="#2a6a9a", m="#1e4e72"),
                "north": A.at(10, 9, {(x, 6): "m" for x in range(1, 9)}, m="#1e4e72")})
    # tail stock + forked fin
    m.bone("tail", [0, 5, 6], "body")
    m.cube("tail", [-3, 3, 6], [6, 5, 4], counter(BLUE, PALE, 0.7, jag=False))
    m.bone("tailfin", [0, 5.5, 10], "tail")
    FIN = over(noise(["#5aaed8", "#6ab8de", "#7cc4e4"]), spots("#a8dcf0", 0.1))
    m.cube("tailfin", [-0.5, 5, 9], [1, 6, 5], FIN, rot=[-35, 0, 0], pivot=[0, 5.5, 10])
    m.cube("tailfin", [-0.5, 1, 9], [1, 5, 5], FIN, rot=[35, 0, 0], pivot=[0, 5.5, 10])
    # pectorals, small dorsal
    m.bone("fin_r", [-5, 3, -4], "body")
    m.bone("fin_l", [5, 3, -4], "body")
    m.cube("fin_r", [-11, 2.5, -6], [6, 1, 3], FIN, rot=[0, 25, -30], pivot=[-5, 3, -4])
    m.mirror("fin_l", [-11, 2.5, -6], [6, 1, 3], FIN, rot=[0, 25, -30], pivot=[-5, 3, -4])
    m.cube("body", [-0.5, 10, -9], [1, 3, 4], FIN, rot=[-15, 0, 0], pivot=[0, 10, -7])
    m.pair("body", "body", [-4, 0, -4], [2, 1, 3], FIN, rot=[0, 0, -20], pivot=[-3, 1, -3])
    # the reef on its back: rock mound + coral
    ROCK = over(noise(["#5a4642", "#6a544e", "#7a605a", "#86706a"]), speckle("#4a3a36", 0.1))
    m.cube("body", [-4, 10, -5], [8, 3, 9], ROCK)
    m.cube("body", [-3, 13, -3], [6, 2, 6], ROCK)
    m.cube("body", [-1, 15, -2], [3, 1, 3], ROCK)
    coral_branch(m, "body", -2, 13, -6, "#c44a4a", h=7)
    coral_branch(m, "body", 2, 13, 2, "#6a4ad8", h=6)
    m.cube("body", [2, 13, -5], [1, 3, 1], noise(["#e07a3a", "#e88a44"]))
    m.cube("body", [-3, 13, 3], [1, 2, 1], noise(["#d8c84a", "#e0d05a"]))
    m.cube("body", [0, 16, -1], [1, 2, 1], lit("#f07cc0"))             # a glowing polyp (glowmask)

    def swim(length, a):
        return {"tail": {"rotation": wave(length, lambda q: [0, S(q) * 14 * a, 0])},
                "tailfin": {"rotation": wave(length, lambda q: [0, S(q - 0.9) * 22 * a, 0])},
                "body": {"rotation": wave(length, lambda q: [0, -S(q) * 3 * a, 0])},
                "fin_r": {"rotation": wave(length, lambda q: [0, 0, S(q) * 12 * a])},
                "fin_l": {"rotation": wave(length, lambda q: [0, 0, -S(q) * 12 * a])},
                "head": {"rotation": wave(length, lambda q: [0, -S(q + 0.5) * 2 * a, 0])}}
    anim(m, "idle", 3.0, swim(3.0, 0.5))
    anim(m, "move", 1.4, swim(1.4, 1.0))
    anim(m, "attack", 0.6, {"head": {"rotation": keys((0, [0, 0, 0]), (0.2, [-10, 0, 0]), (0.35, [8, 0, 0]), (0.6, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.2, {"body": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 0, 25]), (0.9, [0, 0, -25]), (1.2, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 0.8, {"tail": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 40, 0]), (0.55, [0, -40, 0]), (0.8, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# VOID SQUID - black cube with one cyan eye and long jointed tentacles
# =====================================================================================
def void_squid(seed):
    m = Rig("void_squid", 64, 64, seed)
    VOID = ["#0e1216", "#13181d", "#181e24", "#1e252c"]
    m.bone("root", [0, 12, 0])
    m.bone("head", [0, 12, 0], "root")
    eye = {}
    for x in range(1, 9):
        for y in range(2, 8):
            edge = x in (1, 8) or y in (2, 7)
            eye[(x, y)] = "f" if edge else ("E" if (x in (3, 4) and y in (3, 4)) else "e")
    m.cube("head", [-5, 12, -5], [10, 10, 10], over(noise(VOID), speckle("#2a333c", 0.08)),
           art={"north": A.at(10, 10, eye, f="#2a3440", e="!#2fc8e8", E="!#9af4ff")})
    # 8 tentacles: upper segment splays out, lower segment bends back down; cyan photophores underneath
    n = 8
    for i in range(n):
        ang = 2 * math.pi * (i + 0.5) / n
        x, z = math.cos(ang) * 3.5, math.sin(ang) * 3.5
        b1, b2, b3 = f"t{i}a", f"t{i}b", f"t{i}c"
        yaw = -math.degrees(ang) + 90          # local -Z (tilt direction) points AWAY from the centre
        m.bone(b1, [x, 12, z], "head", rotation=[38, yaw, 0])
        m.cube(b1, [x - 1, 5, z - 1], [2, 7, 2], noise(VOID), art={"north": A.at(2, 7, {(0, 2): "c", (1, 5): "c"}, c="!#2fc8e8")})
        m.bone(b2, [x, 5, z], b1, rotation=[-30, 0, 0])
        m.cube(b2, [x - 1, -1, z - 1], [2, 6, 2], noise(VOID), art={"north": A.at(2, 6, {(1, 1): "c", (0, 4): "c"}, c="!#2fc8e8")})
        m.bone(b3, [x, -1, z], b2, rotation=[-20, 0, 0])
        m.cube(b3, [x - 0.5, -5, z - 0.5], [1, 4, 1], noise(VOID), art={"north": A.at(1, 4, {(0, 2): "c"}, c="!#2fc8e8")})

    def tb(length, amp, base=(0, 0, 0)):          # offsets from the rest splay
        d = {}
        for i in range(n):
            d[f"t{i}a"] = {"rotation": wave(length, lambda q, i=i: [base[0] + S(q + i * 0.8) * amp, 0, 0])}
            d[f"t{i}b"] = {"rotation": wave(length, lambda q, i=i: [base[1] + S(q + i * 0.8 - 1) * amp * 1.3, 0, 0])}
            d[f"t{i}c"] = {"rotation": wave(length, lambda q, i=i: [base[2] + S(q + i * 0.8 - 2) * amp * 1.5, 0, 0])}
        return d
    idle = tb(2.4, 8)
    idle["root"] = {"position": wave(2.4, lambda q: [0, S(q) * 1.2, 0])}
    anim(m, "idle", 2.4, idle)
    mv = tb(1.0, 16, base=(-18, 15, 10))           # tentacles stream back while jetting
    mv["head"] = {"rotation": wave(1.0, lambda q: [10, 0, S(q) * 4])}
    anim(m, "move", 1.0, mv)
    atk = {f"t{i}a": {"rotation": keys((0, [0, 0, 0]), (0.2, [32, 0, 0]), (0.4, [-28, 0, 0]), (0.6, [0, 0, 0]))} for i in range(n)}
    atk["head"] = {"rotation": keys((0, [0, 0, 0]), (0.2, [-15, 0, 0]), (0.4, [10, 0, 0]), (0.6, [0, 0, 0]))}
    anim(m, "attack", 0.6, atk, loop=False)
    anim(m, "special", 1.0, {"head": {"scale": keys((0, [1, 1, 1]), (0.3, [1.3, 0.85, 1.3]), (0.6, [0.9, 1.1, 0.9]), (1.0, [1, 1, 1]))}}, loop=False)
    anim(m, "special2", 1.0, {"root": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 180, 0]), (1.0, [0, 360, 0]))}}, loop=False)
    return m


# =====================================================================================
# SEA SERPENT - boss; coiled teal wyrm with indigo spines and glowing yellow coral
# =====================================================================================
def serpent_body(m, *, segs, sizes, y, skin, belly, spine, spine_h, head_w, head_len, eye, horns=None,
                 coral=None, fin=None, teeth="#e6e0cc", mouth="#2a1016", curl_y=None, curl_x=None, legs=None):
    """Head + jaw + a chain of segment bones (each parented to the previous, so rest yaw/pitch
    accumulate into a curve). `curl_y` / `curl_x` = rest rotation per segment."""
    curl_y = curl_y or [0] * segs
    curl_x = curl_x or [0] * segs
    m.bone("root", [0, y, 0])
    m.bone("head", [0, y, -1], "root")
    hw, hl = head_w, head_len
    hh = int(hw * 0.8)
    HEAD = over(counter(skin, belly, 0.7, jag=False), speckle(skin[0], 0.06))
    eye_pts_e = {(hl - 5, 1): "E", (hl - 4, 1): "E", (hl - 5, 2): "e", (hl - 4, 2): "E"}
    eye_pts_w = {(4, 1): "E", (3, 1): "E", (4, 2): "E", (3, 2): "e"}
    m.cube("head", [-hw / 2, y - hh / 2 + 1, -hl - 1], [hw, hh, hl], HEAD,
           art={"east": A.at(hl, hh, eye_pts_e, E="!" + eye, e="!#fff4b0"),
                "west": A.at(hl, hh, eye_pts_w, E="!" + eye, e="!#fff4b0"),
                "north": A.at(hw, hh, {(1, 1): "n", (hw - 2, 1): "n"}, n="#0a0c10")})
    m.cube("head", [-hw / 2 + 1, y + hh / 2 + 1, -hl + 2], [hw - 2, 2, hl - 4], noise(skin))      # brow ridge
    m.bone("jaw", [0, y - hh / 2 + 1, -2], "head", rotation=[18, 0, 0])
    m.cube("jaw", [-hw / 2 + 1, y - hh / 2 - 1, -hl], [hw - 2, 2, hl - 1], noise(belly),
           art={"up": A.at(hw - 2, hl - 1, {(x, y2): "m" for x in range(1, hw - 3) for y2 in range(1, hl - 2)}, m=mouth)})
    for x in (-hw / 2 + 1, hw / 2 - 2):
        for z in (-hl, -hl + 3, -hl + 6):
            m.cube("jaw", [x, y - hh / 2 + 1, z], [1, 2, 1], solid(teeth, edge=0))
            m.cube("head", [x, y - hh / 2, z + 1], [1, 2, 1], solid(teeth, edge=0))
    if horns:
        for sx in (-1, 1):
            m.cube("head", [sx * (hw / 2) - (1 if sx > 0 else 0) + sx * 0, y + hh / 2 - 1, -3], [1, 2, 7], noise(horns),
                   rot=[-25, sx * 20, 0], pivot=[sx * hw / 2, y + hh / 2, 0])
            m.cube("head", [sx * (hw / 2) - (1 if sx > 0 else 0), y - 1, -2], [1, 5, 1], noise(horns),
                   rot=[-40, 0, -sx * 35], pivot=[sx * hw / 2, y, -1])
    # the body chain
    prev, z = "head", 0
    names = []
    SP = noise(spine) if spine else None
    for i in range(segs):
        s = sizes[i]
        b = f"seg{i}"
        m.bone(b, [0, y, z], prev, rotation=[curl_x[i], curl_y[i], 0] if (curl_x[i] or curl_y[i]) else None)
        m.cube(b, [-s / 2, y - s / 2, z], [s, s, s + 1], over(counter(skin, belly, 0.66, jag=False), speckle(skin[-1], 0.05)))
        if spine:
            m.cube(b, [-0.5, y + s / 2 - 1, z + 1], [1, spine_h(i), s - 1], SP, rot=[-30, 0, 0], pivot=[0, y + s / 2, z + 1])
        if coral and i in coral:
            cx = -s / 2 - 1 if i % 2 else s / 2
            coral_branch(m, b, cx, y - 1, z + s / 2, "#ffd24a", h=5, glow=True)
        if legs:
            m.pair(b, b, [-s / 2 - 1, y - s / 2 - legs + 1, z + s / 2], [1, legs, 1], noise(skin), rot=[20, 0, -30],
                   pivot=[-s / 2, y - s / 2 + 1, z + s / 2])
        names.append(b)
        prev, z = b, z + s + 1
    m.bone("tailfin", [0, y, z], prev)
    tf = fin or spine or skin
    m.cube("tailfin", [-0.5, y - 1, z - 1], [1, sizes[-1] + 5, 5], noise(tf), rot=[-40, 0, 0], pivot=[0, y, z])
    m.cube("tailfin", [-0.5, y - sizes[-1] - 3, z - 1], [1, sizes[-1] + 3, 4], noise(tf), rot=[40, 0, 0], pivot=[0, y, z])
    return names


def serpent_anims(m, names, curl_y=None, curl_x=None, amp=9.0, speed=1.0, k=0.75):
    """Serpentine swimming: a travelling curvature wave down a STRAIGHT spine.
    Each segment bends by amp_i * sin(q - k*i) relative to its parent; amp_i grows head->tail, so the
    body makes an S that rolls backward and the tail sweeps equally to BOTH sides. (The old version
    oscillated around a baked-in coil, so the tail only ever swung to one side.) The head gets a small
    counter-yaw so it keeps pointing where the creature is going."""
    n = len(names)

    def amp_i(i, a):
        return a * (0.35 + 0.65 * i / max(1, n - 1))

    def wv(length, a):
        d = {nm: {"rotation": wave(length, lambda q, i=i: [S(q * 0.5 - i * 0.5) * a * 0.12, S(q - k * i) * amp_i(i, a), 0])}
             for i, nm in enumerate(names)}
        d["tailfin"] = {"rotation": wave(length, lambda q: [0, S(q - k * n) * a * 1.6, 0])}
        d["head"] = {"rotation": wave(length, lambda q: [S(q * 0.5) * 3, -S(q + 0.6) * a * 0.35, 0])}
        d["jaw"] = {"rotation": wave(length, lambda q: [S(q) * 4, 0, 0])}
        return d
    anim(m, "idle", 3.0 * speed, wv(3.0 * speed, amp * 0.5))
    anim(m, "move", 1.4 * speed, wv(1.4 * speed, amp))
    anim(m, "attack", 0.6, {"head": {"rotation": keys((0, [0, 0, 0]), (0.18, [-22, 0, 0]), (0.32, [14, 0, 0]), (0.6, [0, 0, 0])),
                                     "position": keys((0, [0, 0, 0]), (0.18, [0, 1, 2]), (0.32, [0, 0, -6]), (0.6, [0, 0, 0]))},
                            "jaw": {"rotation": keys((0, [0, 0, 0]), (0.18, [32, 0, 0]), (0.32, [-13, 0, 0]), (0.6, [0, 0, 0]))}}, loop=False)
    sp = {"head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-45, 0, 0]), (1.0, [-45, 0, 0]), (1.4, [0, 0, 0]))},
          "jaw": {"rotation": keys((0, [0, 0, 0]), (0.4, [37, 0, 0]), (1.0, [37, 0, 0]), (1.4, [0, 0, 0]))}}
    for i, nm in enumerate(names[:3]):
        sp[nm] = {"rotation": keys((0, [0, 0, 0]), (0.4, [-30 if i == 0 else 20, 0, 0]),
                                   (1.0, [-30 if i == 0 else 20, 0, 0]), (1.4, [0, 0, 0]))}
    anim(m, "special", 1.4, sp, loop=False)
    anim(m, "special2", 1.0, {nm: {"rotation": keys((0, [0, 0, 0]), (0.4, [0, (i + 1) * 5, 0]),
                                                    (0.7, [0, -(i + 1) * 5, 0]), (1.0, [0, 0, 0]))}
                              for i, nm in enumerate(names)}, loop=False)


def sea_serpent(seed):
    m = Rig("sea_serpent", 128, 128, seed)
    SKIN = ["#123a44", "#17474f", "#1c545a", "#236266"]
    BELLY = ["#2a7a74", "#338a80", "#3c988c"]
    sizes = [9, 9, 9, 8, 8, 7, 7, 6, 5, 4]
    curl = [0] * 10                                   # straight spine: the swim wave does the curving
    names = serpent_body(m, segs=10, sizes=sizes, y=7, skin=SKIN, belly=BELLY, spine=["#241a4a", "#2e2260", "#3a2c78"],
                         spine_h=lambda i: max(3, 7 - i // 2), head_w=10, head_len=14, eye="#ffd24a",
                         horns=["#2e2260", "#3a2c78"], coral={2, 5, 8}, curl_y=curl)
    serpent_anims(m, names, amp=9)
    return m


# =====================================================================================
# KRAKEN - boss; a colossal black mantle erupting from the seabed, eight sucker-lined arms
# =====================================================================================
def kraken(seed):
    m = Rig("kraken", 128, 128, seed)
    SK = over(noise(INK), speckle("#2c333c", 0.06), veins("#3a2030", 0.02, glow=False))
    m.bone("root", [0, 0, 0])
    m.cube("root", [-13, 0, -13], [26, 3, 26], over(noise(SEABED), speckle("#3a4048", 0.06)))
    m.bone("heart", [0, 3, 0], "root")
    eyes = {}
    for (cx, cy) in ((3, 5), (12, 5)):
        for dx in range(-1, 2):
            for dy in range(-1, 1):
                eyes[(cx + dx, cy + dy)] = "E" if dx == 0 and dy == 0 else "e"
        eyes[(cx, cy + 1)] = "k"
    m.cube("heart", [-9, 3, -9], [18, 15, 18], SK, art={"north": A.at(18, 15, {(x + 1, y + 1): c for (x, y), c in eyes.items()},
                                                                         e="!#e89a2a", E="!#ffe07a", k="#1a0e06")})
    m.cube("heart", [-8, 18, -8], [16, 6, 16], SK)
    m.cube("heart", [-6, 24, -6], [12, 4, 12], SK)
    m.cube("heart", [-3, 28, -3], [6, 2, 6], SK)
    m.cube("heart", [-2, 5, -10], [4, 4, 3], noise(["#2a2018", "#3a2c20"]))   # beak
    m.cube("heart", [-1, 4, -11], [2, 2, 1], noise(["#1a120c"]))
    segs_all = []
    widths, lengths = [6, 5, 5, 4, 3], [8, 7, 6, 5, 4]
    curl = [8, 14, 18, 22, 26]
    for k in range(8):
        a = math.radians(-30 + k * 250 / 7)          # sides + back; the front sector (225-315 deg) stays clear
        x, z = math.cos(a) * 13, math.sin(a) * 13
        yaw = -math.degrees(a) - 90                    # face each arm's front (suckers) inward/up
        m.bone(f"c{k}", [x, 3, z], "root", rotation=[0, yaw + 180, 0])
        prev, y = f"c{k}", 3
        names = []
        for i, (w, L, rx) in enumerate(zip(widths, lengths, curl)):
            b = f"c{k}_{i}"
            m.bone(b, [x, y, z], prev, rotation=[-rx - (10 if i == 0 else 0), 0, 0])
            m.cube(b, [x - w / 2, y, z - w / 2], [w, L, w], SK)
            if w >= 4:
                sucker_col(m, b, x, y + 1, z + w / 2 + 1, max(1, L // 3), 3, w=w - 2)
            names.append(b)
            prev, y = b, y + L
        m.cube(prev, [x - 0.5, y, z - 0.5], [1, 2, 1], lit("#e89a2a"))
        segs_all.append(names)
    base = {nm: -c - (10 if i == 0 else 0) for names in segs_all for i, (nm, c) in enumerate(zip(names, curl))}

    def sway(length, amp):
        d = {}
        for k, names in enumerate(segs_all):
            for i, nm in enumerate(names):
                d[nm] = {"rotation": wave(length, lambda q, k=k, i=i, nm=nm: [S(q + k * 0.9 - i * 0.6) * amp * (0.4 + i * 0.2), 0,
                                                                              S(q * 0.6 + k) * amp * 0.4])}
        return d
    idle = sway(3.5, 8)
    idle["heart"] = {"scale": wave(3.5, lambda q: [1 + 0.03 * S(q), 1 + 0.04 * S(q), 1 + 0.03 * S(q)])}
    anim(m, "idle", 3.5, idle)
    anim(m, "move", 1.6, sway(1.6, 16))
    atk = {}
    for names in segs_all[:3]:
        for i, nm in enumerate(names):
            atk[nm] = {"rotation": keys((0, [0, 0, 0]), (0.25, [20, 0, 0]), (0.4, [-45, 0, 0]), (0.7, [0, 0, 0]))}
    anim(m, "attack", 0.7, atk, loop=False)
    sp = {nm: {"rotation": keys((0, [0, 0, 0]), (0.4, [-25, 0, 0]), (0.8, [10, 0, 0]), (1.2, [0, 0, 0]))}
          for names in segs_all for nm in names}
    sp["heart"] = {"scale": keys((0, [1, 1, 1]), (0.4, [1.12, 1.12, 1.12]), (0.8, [0.97, 0.97, 0.97]), (1.2, [1, 1, 1]))}
    anim(m, "special", 1.2, sp, loop=False)
    anim(m, "special2", 1.0, {nm: {"rotation": keys((0, [0, 0, 0]), (0.4, [-35, 0, 0]), (0.6, [-35, 0, 0]),
                                                    (1.0, [0, 0, 0]))} for nm in segs_all[0] + segs_all[4]}, loop=False)
    return m


# =====================================================================================
# SIREN (SirenEntity: idle / swim loops, sing / attack triggers)
# =====================================================================================
def siren(seed):
    m = Rig("siren", 128, 128, seed)
    SKIN = noise(["#a8c8a0", "#b4d2aa", "#c0dcb4", "#cce4c0"])
    HAIR = over(noise(["#0e3234", "#123c3e", "#164648", "#1c5254"]), spots("#0a2628", 0.08))
    TAIL = over(noise(["#10403e", "#164c4a", "#1c5856", "#226462"]), spots("#2e7a74", 0.1))
    FIN = over(noise(["#1c5856", "#226462", "#2a706c"]), veins("#0e3a38", 0.08, glow=False))
    GOLD = noise(["#a87a1e", "#c8962a", "#e0b84a"])
    P = dict(h="#123c3e", H="#0a2628", e="!#6ff0ff", E="!#d8ffff", s="#a8c8a0", m="#5a1a1a", M="#8a2a2a")
    m.bone("body", [0, 19, 0])
    m.cube("body", [-4, 19, -2], [8, 10, 4], SKIN,
           art={"north": A.at(8, 10, {**{(x, 5): "g" for x in range(8)}, (1, 4): "g", (2, 4): "G", (5, 4): "G", (6, 4): "g",
                                     (2, 5): "b", (5, 5): "b", (3, 8): "d", (4, 8): "d"}, g="#c8962a", G="#e0b84a", b="#5a4a2a", d="#90b088")})
    m.bone("head", [0, 29, 0], "body")
    face = {**{(x, y): "h" for x in range(8) for y in (0,)}, (0, 1): "h", (7, 1): "h", (0, 2): "h", (7, 2): "h", (0, 3): "H", (7, 3): "H",
            (1, 3): "e", (2, 3): "E", (5, 3): "E", (6, 3): "e", (3, 5): "m", (4, 5): "m", (3, 6): "M", (4, 6): "m"}
    m.cube("head", [-4, 29, -4], [8, 8, 8], SKIN, art={"north": A.at(8, 8, face, P),
                                                      "sides": A.at(8, 8, {(x, y): "h" for x in range(8) for y in range(8) if y < 3 or x > 4}, P),
                                                      "south": A.at(8, 8, {(x, y): "h" for x in range(8) for y in range(8)}, P),
                                                      "up": A.at(8, 8, {(x, y): "h" for x in range(8) for y in range(8)}, P)})
    # hair: crown, side curtains, a long back fall and loose strands drifting out
    m.cube("head", [-4.5, 36, -4.5], [9, 2, 9], HAIR)
    m.pair("head", "head", [-5, 25, -3], [1, 12, 7], HAIR, art={"sides": A.at(7, 12, {(0, 11): "_", (2, 11): "_", (1, 10): "_", (5, 11): "_"})})
    m.bone("hair", [0, 37, 4], "head")
    m.cube("hair", [-4.5, 19, 3.5], [9, 18, 2], HAIR, art={"south": A.at(9, 18, {(0, 17): "_", (1, 16): "_", (3, 17): "_", (6, 17): "_", (8, 16): "_", (8, 17): "_"})})
    for (x, y, rz, L) in ((-6, 30, 40, 7), (5, 30, -45, 8), (-6, 24, 25, 6), (5, 23, -30, 7)):
        m.cube("hair", [x, y - L, 3], [1, L, 1], HAIR, rot=[20, 0, rz], pivot=[x, y, 3])
    for side, sx in (("rarm", -1), ("larm", 1)):
        m.bone(side, [5.5 * sx, 28, 0], "body")
        o = [4, 18, -1.5] if sx > 0 else [-7, 18, -1.5]
        m.cube(side, o, [3, 11, 3], SKIN)
        m.cube(side, [o[0] - 0.5, 24, -2], [4, 1, 4], GOLD)                   # upper-arm band
        m.cube(side, [o[0] - 0.5, 20, -2], [4, 2, 4], GOLD)                   # bracelet
    # tail: tapering, curling back into a big forked fin
    m.cube("body", [-4.5, 17, -2.5], [9, 2, 5], GOLD)                          # hip band
    m.bone("tail1", [0, 19, 0], "body", rotation=[8, 0, 0])
    m.cube("tail1", [-4, 12, -2], [8, 7, 4], TAIL)
    m.bone("tail2", [0, 12, 0], "tail1", rotation=[14, 0, 0])
    m.cube("tail2", [-3.5, 6, -1.75], [7, 6, 4], TAIL)
    m.bone("tail3", [0, 6, 0], "tail2", rotation=[18, 0, 0])
    m.cube("tail3", [-2.5, 1, -1.5], [5, 5, 3], TAIL)
    m.bone("fin", [0, 1, 0], "tail3", rotation=[16, 0, 0])
    m.cube("fin", [-1.5, -2, -1], [3, 3, 2], TAIL)
    m.cube("fin", [-1, -9, -0.5], [1, 8, 1], FIN, rot=[0, 0, 35], pivot=[0, -1, 0])
    m.cube("fin", [-7, -9, -0.5], [7, 8, 1], FIN, rot=[0, 0, 35], pivot=[0, -1, 0],
           art={"north": A.at(7, 8, {(x, y): "_" for x in range(7) for y in range(8) if x < 6 - y // 1.4}), "south": A.at(7, 8, {(x, y): "_" for x in range(7) for y in range(8) if x < 6 - y // 1.4})})
    m.mirror("fin", [-7, -9, -0.5], [7, 8, 1], FIN, rot=[0, 0, 35], pivot=[0, -1, 0],
             art={"north": A.at(7, 8, {(x, y): "_" for x in range(7) for y in range(8) if x < 6 - y // 1.4}), "south": A.at(7, 8, {(x, y): "_" for x in range(7) for y in range(8) if x < 6 - y // 1.4})})

    anim(m, "idle", 3.0, {
        "body": {"position": wave(3.0, lambda p: [0, S(p) * 0.6, 0]), "rotation": wave(3.0, lambda p: [S(p) * 2, 0, 0])},
        "head": {"rotation": wave(3.0, lambda p: [0, S(p) * 10, S(p + 1) * 3])},
        "hair": {"rotation": wave(3.0, lambda p: [S(p - 0.6) * 5 + 3, 0, 0])},
        "rarm": {"rotation": wave(3.0, lambda p: [S(p) * 8, 0, 8 + S(p) * 6])},
        "larm": {"rotation": wave(3.0, lambda p: [-S(p) * 8, 0, -8 - S(p) * 6])},
        "tail1": {"rotation": wave(3.0, lambda p: [S(p) * 5, 0, 0])},
        "tail2": {"rotation": wave(3.0, lambda p: [S(p - 0.7) * 8, 0, 0])},
        "tail3": {"rotation": wave(3.0, lambda p: [S(p - 1.4) * 12, 0, 0])},
        "fin": {"rotation": wave(3.0, lambda p: [S(p - 2.1) * 16, 0, 0])}})
    anim(m, "swim", 1.2, {
        "body": {"rotation": wave(1.2, lambda p: [55, 0, S(p) * 3])},
        "head": {"rotation": wave(1.2, lambda p: [-40, 0, 0])},
        "hair": {"rotation": wave(1.2, lambda p: [25 + S(p - 1) * 8, 0, 0])},
        "rarm": {"rotation": wave(1.2, lambda p: [25 + S(p) * 30, 0, 10])},
        "larm": {"rotation": wave(1.2, lambda p: [25 + S(p) * 30, 0, -10])},
        "tail1": {"rotation": wave(1.2, lambda p: [-8 + S(p) * 12, 0, 0])},
        "tail2": {"rotation": wave(1.2, lambda p: [-14 + S(p - 0.8) * 18, 0, 0])},
        "tail3": {"rotation": wave(1.2, lambda p: [-18 + S(p - 1.6) * 24, 0, 0])},
        "fin": {"rotation": wave(1.2, lambda p: [-16 + S(p - 2.4) * 30, 0, 0])}})
    anim(m, "sing", 3.0, {
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-22, 0, 0]), (1.5, [-18, 10, 4]), (2.6, [-22, -10, -4]), (3.0, [0, 0, 0]))},
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-40, 0, 60]), (1.5, [-55, 0, 50]), (2.6, [-40, 0, 65]), (3.0, [0, 0, 0]))},
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-40, 0, -60]), (1.5, [-55, 0, -50]), (2.6, [-40, 0, -65]), (3.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [-6, 0, 0]), (2.6, [-6, 0, 0]), (3.0, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (1.5, [0, 1.5, 0]), (3.0, [0, 0, 0]))},
        "hair": {"rotation": keys((0, [0, 0, 0]), (0.8, [10, 0, 0]), (1.8, [-6, 0, 0]), (3.0, [0, 0, 0]))}}, loop=False)
    anim(m, "attack", 0.6, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.2, [-130, 0, 20]), (0.35, [20, 0, -10]), (0.6, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.2, [0, -20, 0]), (0.35, [8, 18, 0]), (0.6, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.2, [-10, 10, 0]), (0.6, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# CORAL JELLY (CoralJellyEntity: pulse loop, sting trigger)
# =====================================================================================
FIN_ART = """
_YY_____
YWYY____
YWWYY___
_YWWYY__
_YYWWYY_
__YYWWYY
__YYYWWY
___YYYYY"""


def coral_jelly(seed):
    m = Rig("coral_jelly", 64, 64, seed)
    BELL = over(noise(["#12545a", "#18626a", "#1e7078", "#247e86"]), spots("#3aa8a8", 0.06))
    RIM = {}
    for x in range(10):
        for y in range(3):
            if y == 0 or (y == 1 and (x * 7) % 3) or (y == 2 and x % 4 == 1):
                RIM[(x, y)] = "roypRO"[(x + y * 2) % 6]
    rim_pal = dict(r="#c83a3a", o="#e8762a", y="#e8b83a", p="#8a3ac8", R="#a82a4a", O="#f09a3a")
    m.bone("root", [0, 12, 0])
    m.bone("bell", [0, 12, 0], "root")
    m.cube("bell", [-5, 12, -5], [10, 10, 10], BELL, art={"sides": A.at(10, 10, RIM, rim_pal),
                                                         "up": A.at(10, 10, {(x, y): "roypRO"[(x * 3 + y) % 6] for x in range(10) for y in range(10) if (x + y) % 3 != 0}, rim_pal)})
    for (x, z, h, c) in ((-4, -3, 5, "#c83a4a"), (-3, -2, 3, "#a82a4a"), (-1, -1, 4, "#e8942a"), (1, -2, 5, "#e8b83a"),
                         (3, 1, 4, "#8a3ac8"), (-2, 2, 3, "#c83a3a"), (2, 3, 2, "#f09a3a")):
        m.cube("bell", [x, 22, z], [2, h, 2], noise([c, c]))
        if h >= 4:
            m.cube("bell", [x + (1 if x < 0 else -1), 22 + h - 2, z], [2, 2, 2], noise([c, c]))
    FP = dict(Y="!#ffd23a", W="!#fff4b0")
    for side, sx in (("fin_l", 1), ("fin_r", -1)):
        m.bone(side, [5 * sx, 17, 0], "bell")
        m.cube(side, [5 if sx > 0 else -5, 13, -4], [0, 8, 8], lit("#ffd23a"),
               art={"east": A(FIN_ART, FP), "west": A(FIN_ART, FP).flipped()}, rot=[0, 0, -sx * 20], pivot=[5 * sx, 17, 0])
    TENT = over(noise(["#1e7a78", "#248886", "#2a9694"]), spots("#6fe0d0", 0.1, glow=True))
    spots_ = ((-3, -3), (3, -3), (0, 0), (-3, 3), (3, 3))
    for i, (x, z) in enumerate(spots_):
        a, b, c = f"tent{i}a", f"tent{i}b", f"tent{i}c"
        m.bone(a, [x, 12, z], "root", rotation=[z * 4, 0, -x * 4])
        m.cube(a, [x - 0.5, 6, z - 0.5], [1, 6, 1], TENT)
        m.bone(b, [x, 6, z], a, rotation=[-z * 5, 0, x * 5])
        m.cube(b, [x - 0.5, 1, z - 0.5], [1, 5, 1], TENT)
        m.bone(c, [x, 1, z], b)
        m.cube(c, [x - 0.5, -3, z - 0.5], [1, 4, 1], lit("#9ff5e6"))
    bones = {"bell": {"scale": wave(2.0, lambda p: [1 + 0.08 * max(0, S(p)), 1 - 0.1 * max(0, S(p)), 1 + 0.08 * max(0, S(p))]),
                      "position": wave(2.0, lambda p: [0, S(p - 1) * 0.8, 0])},
             "fin_l": {"rotation": wave(2.0, lambda p: [0, 0, -S(p) * 25])},
             "fin_r": {"rotation": wave(2.0, lambda p: [0, 0, S(p) * 25])}}
    for i, (ox, oz) in enumerate(spots_):
        bones[f"tent{i}a"] = {"rotation": wave(2.0, lambda p, i=i, ox=ox, oz=oz: [S(p - 0.5 - i * 0.3) * 10 * (1 if oz >= 0 else -1), 0,
                                                                                   -S(p - 0.5 - i * 0.3) * 8 * (1 if ox >= 0 else -1)])}
        bones[f"tent{i}b"] = {"rotation": wave(2.0, lambda p, i=i: [S(p - 1.2 - i * 0.3) * 14, 0, 0])}
        bones[f"tent{i}c"] = {"rotation": wave(2.0, lambda p, i=i: [S(p - 1.9 - i * 0.3) * 18, 0, 0])}
    anim(m, "pulse", 2.0, bones)
    sting = {f"tent{i}a": {"rotation": keys((0, [0, 0, 0]), (0.15, [-40 + i * 5, 0, 20 - i * 10]), (0.4, [30, 0, -10]), (0.7, [0, 0, 0]))}
             for i in range(5)}
    sting["bell"] = {"scale": keys((0, [1, 1, 1]), (0.15, [1.2, 0.8, 1.2]), (0.4, [0.95, 1.08, 0.95]), (0.7, [1, 1, 1]))}
    anim(m, "sting", 0.7, sting, loop=False)
    return m


from mobs.serpent import sea_serpent as _serpent  # noqa: E402  (rebuilt 2026-09-29, own module)

from mobs.kraken import kraken as _kraken, kraken_arm as _kraken_arm, harpoon as _harpoon  # noqa: E402  (rebuilt 2026-09-29, own module)

MOBS = {"siren": siren, "coral_jelly": coral_jelly, "kraken_tentacle": kraken_tentacle, "reefback_fish": reefback_fish, "void_squid": void_squid,
        "sea_serpent": _serpent, "kraken": _kraken, "kraken_arm": _kraken_arm, "harpoon": _harpoon}
SKINS = {"sea_serpent": {"enraged": lambda seed: _serpent(seed, "enraged")},
         "kraken": {"enraged": lambda seed: _kraken(seed, "enraged")}}
