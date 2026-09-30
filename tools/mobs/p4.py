"""Phase 4 roster: skeleton_pirate, trident_skeleton, ghost_shark, drowned_hands, phantom_pirate,
ghost_captain (boss), chained_revenant (boss)."""
import math

from mobkit import A, Rig, bands, grad, lit, noise, over, solid, spots, veins, drips, speckle, anim, keys, wave, S
from mobs.common import biped, biped_anims

BONE = ["#b8b2a0", "#c8c2ae", "#d6d0bc", "#e0dac8"]
BONE_Y = ["#a89a6a", "#b8aa78", "#c8ba86", "#d4c692"]       # sun-yellowed bone (trident skeleton)
CHAIN = ["#1e2024", "#2a2c30", "#34373c", "#3e4248"]
GHOST = ["#1e6a66", "#247672", "#2a827c", "#329088"]


def skull(w=8, h=8, eye="k", glow=None):
    """Skull face pixels: sockets, nose, teeth row."""
    pts = {}
    for (x, y) in ((1, 3), (2, 3), (1, 4), (2, 4), (5, 3), (6, 3), (5, 4), (6, 4)):
        pts[(x, y)] = eye
    if glow:
        pts[(2, 4)] = "G"; pts[(5, 4)] = "G"
    pts[(3, 5)] = "k"; pts[(4, 5)] = "k"
    for x in range(1, 7):
        pts[(x, 7)] = "k" if x % 2 else "t"
    pts[(1, 6)] = "k"; pts[(6, 6)] = "k"
    return pts


def ribcage(w=8, h=12):
    pts = {}
    for y in (1, 3, 5, 7):
        for x in range(1, 7):
            pts[(x, y)] = "b"
        pts[(0, y)] = "."; pts[(7, y)] = "."
    for y in range(0, 10):
        pts[(3, y)] = "b"; pts[(4, y)] = "b"
    for y in (0, 2, 4, 6, 8):
        for x in (1, 2, 5, 6):
            pts[(x, y)] = "d"
    return pts


# =====================================================================================
# SKELETON PIRATE
# =====================================================================================
def skeleton_pirate(seed):
    m = Rig("skeleton_pirate", 64, 64, seed)
    P = dict(k="#141210", t="#e0dac8", r="#7a3a22", R="#5a2a18", G="!#6fe0d0", b="#d6d0bc", d="#1a1612",
             c="#4a3a2a", C="#3a2c1e", L="#2a1e14", g="#c89a3a")
    head = {**skull(glow=True), **{(x, y): ("r" if (x + y) % 3 else "R") for x in range(8) for y in (0, 1)}}
    coat = {**{(x, y): "C" if x in (0, 7) else "c" for x in (0, 1, 6, 7) for y in range(12)},
            **{(x, y): c for (x, y), c in ribcage().items() if 2 <= x <= 5 and y < 9},
            **{(x, 9): "L" for x in range(8)}, **{(x, 10): "L" for x in range(8)}, (3, 9): "g", (4, 9): "g", (4, 10): "g"}
    COAT = noise(["#3a2c1e", "#4a3a2a", "#54432f"])
    a = biped(m, head=noise(BONE), body=noise(["#1a1612", "#221c16"]), arm=bands(noise(BONE), (0, 5, COAT)),
              leg=noise(["#22303a", "#2a3a46", "#324450"]), arm_w=3,
              art={"head": {"north": A.at(8, 8, head, P), "up": A.at(8, 8, {(x, y): "r" for x in range(8) for y in range(8)}, P),
                            "south": A.at(8, 8, {(x, y): "r" for x in range(8) for y in (0, 1, 2)}, P)},
                   "body": {"north": A.at(8, 12, coat, P)}})
    t = a["top"]
    # bone legs visible below torn trousers; a boot on one foot
    m.cube("rleg", [-3.5, 0, -2.5], [4, 3, 5], noise(["#2a1c14", "#3a2618"]))
    m.cube("lleg", [0.5, 0, -1], [2, 5, 2], noise(BONE))
    m.cube("head", [-4.5, t + 5, -4.5], [9, 3, 9], noise(["#5a2a18", "#7a3a22"]), art={"sides": A("rRrrRr\nRrrRrr\nrrRrrR", P)})
    m.cube("head", [3, t + 3, 4], [1, 3, 1], noise(["#5a2a18", "#7a3a22"]), rot=[0, 0, 15])
    m.cube("head", [-3, t - 2, -3.5], [6, 2, 5], noise(BONE),
           art={"north": A.at(6, 2, {(x, 0): "k" if x % 2 else "t" for x in range(6)}, P)})   # jaw
    # coat flaps
    m.bone("coat", [0, t - 12, 0], "body")
    m.cube("coat", [-4.5, t - 20, 1.5], [9, 8, 1], COAT, art={"south": A(".........\n" * 6 + "_.__._._.\n__.___._.", {})})
    m.pair("coat", "coat", [-4.6, t - 19, -2.4], [1, 7, 4], COAT)
    # rusty cutlass
    rx, hy = a["rx"] + 0.5, a["hand_y"]
    pv = [rx, hy, 0]
    m.cube("rhand", [rx - 0.5, hy - 1, -1], [1, 3, 2], solid("#3a2616"), rot=[-40, 0, 0], pivot=pv)
    m.cube("rhand", [rx - 1.5, hy - 1.5, -3], [3, 3, 1], solid("#5a3a22"), rot=[-40, 0, 0], pivot=pv)
    m.cube("rhand", [rx - 0.5, hy - 1, -13], [1, 2, 10], over(noise(["#6a5a4a", "#7a6a58", "#8a6e52"]), spots("#8a4a22", 0.2)),
           rot=[-40, 0, 0], pivot=pv)
    biped_anims(m, "sword", cape="coat", hold=[-15, 0, 0])
    return m


def bone_limbs(m, B, J):
    """Swap the solid biped limbs for skeletal ones: thin shafts with knobbly joints, blocky feet/hands."""
    m.cubes = [c for c in m.cubes if c.bone not in ("rleg", "lleg", "rarm", "larm")]
    for bone, sx in (("rleg", -1), ("lleg", 1)):
        cx = sx * 2
        m.cube(bone, [cx - 1, 6, -1], [2, 6, 2], B)
        m.cube(bone, [cx - 1.5, 5, -1.5], [3, 2, 3], J)
        m.cube(bone, [cx - 1, 1, -1], [2, 5, 2], B)
        m.cube(bone, [cx - 1.5, 0, -2.5], [3, 2, 4], J)
    for bone, sx in (("rarm", -1), ("larm", 1)):
        cx = sx * 5.5
        m.cube(bone, [cx - 1, 17, -1], [2, 6, 2], B)
        m.cube(bone, [cx - 1.5, 16, -1.5], [3, 2, 3], J)
        m.cube(bone, [cx - 1, 12, -1], [2, 5, 2], B)
        m.cube(bone, [cx - 1.5, 10, -1.5], [3, 3, 3], J)


# =====================================================================================
# TRIDENT SKELETON - coral-crested, teal pauldrons, rusted vest, trident
# =====================================================================================
def trident_skeleton(seed):
    m = Rig("trident_skeleton", 64, 64, seed)
    P = dict(k="#141210", t="#e0dac8", b="#c8ba86", d="#2a2216", r="#8a3a22", R="#a84a2a", o="#c86a3a")
    TEAL = noise(["#2a6a66", "#327a74", "#3a8a82", "#48968c"])
    RUST = over(noise(["#6a2a18", "#7a3420", "#8a3e26"]), spots("#a85a34", 0.1))
    a = biped(m, head=noise(BONE_Y), body=noise(["#1e1a12", "#2a2418"]), arm=noise(BONE_Y), leg=noise(BONE_Y), arm_w=3,
              art={"head": {"north": A.at(8, 8, {**skull(), (0, 0): "r", (1, 0): "R", (6, 0): "o"}, P)},
                   "body": {"north": A.at(8, 12, {**{k: v for k, v in ribcage().items()}, **{(x, 11): "d" for x in range(8)}}, P, b="#c8ba86")}})
    t = a["top"]
    bone_limbs(m, noise(BONE_Y), noise(["#98895a", "#a89a6a"]))
    # coral crest
    for (x, z, h, c) in ((-2, -2, 3, "#8a3a22"), (0, -1, 4, "#a84a2a"), (1, 1, 3, "#c86a3a"), (-1, 2, 2, "#8a3a22"), (-3, 0, 2, "#a84a2a")):
        m.cube("head", [x, t + 8, z], [2, h, 2], noise([c, c, "#6a2a18"]))
    m.cube("head", [-4.2, t + 5, -4.2], [3, 3, 3], noise(["#8a3a22", "#a84a2a"]))
    # rusted rib-vest and a teal belt with a torn loincloth
    m.cube("body", [-4.5, t - 9, -2.5], [9, 8, 5], RUST, art={"north": A.at(9, 8, {(4, y): "_" for y in range(8)} | {(3, y): "_" for y in range(4)} | {(5, y): "_" for y in range(4)})})
    m.cube("body", [-4.5, t - 13, -2.5], [9, 2, 5], TEAL)
    m.cube("body", [-3, t - 17, -2.7], [6, 4, 1], RUST, art={"north": A.at(6, 4, {(0, 3): "_", (2, 3): "_", (5, 3): "_", (5, 2): "_"})})
    # spiky teal pauldrons
    for sx, bone in ((-1, "rarm"), (1, "larm")):
        x0 = -8.5 if sx < 0 else 3.5
        m.cube(bone, [x0, t - 4, -3], [5, 4, 6], TEAL)
        for (dx, dz, h) in ((1, -2, 2), (3, 0, 3), (2, 2, 2)):
            m.cube(bone, [x0 + dx, t, dz - 0.5], [1, h, 1], TEAL)
    # trident (dark iron), held upright
    rx, hy = a["rx"] + 0.5, a["hand_y"]
    IRON = noise(["#4a5446", "#566252", "#62705e"])
    m.bone("trident", [rx, hy, 0], "rhand")
    m.cube("trident", [rx - 0.5, hy - 8, -1.5], [1, 24, 1], noise(["#4a3a26", "#5a4630"]))
    m.cube("trident", [rx - 3.5, hy + 16, -1.5], [7, 1, 1], IRON)
    for dx in (-3.5, -0.5, 2.5):
        m.cube("trident", [rx + dx, hy + 17, -1.5], [1, 5 if dx == -0.5 else 4, 1], IRON)
    m.cube("trident", [rx - 1.5, hy + 22, -2.5], [3, 1, 3], IRON)
    biped_anims(m, "thrust", hold=[-30, 0, 0])
    return m


# =====================================================================================
# GHOST SHARK - spectral skeleton shark
# =====================================================================================
def ghost_shark(seed):
    m = Rig("ghost_shark", 64, 64, seed)
    SP = lit("#8ad8d4")
    SP2 = lit("#a8ece6")
    m.bone("root", [0, 6, 0])
    m.bone("body", [0, 6, 0], "root")
    # skull head: box with dark eye sockets, open lower jaw with teeth
    m.bone("head", [0, 6, -6], "body")
    eyes = {(3, 2): "k", (4, 2): "k", (3, 3): "k", (4, 3): "k"}
    m.cube("head", [-5, 4, -15], [10, 7, 10], SP2, art={"east": A.at(10, 7, eyes, k="#1a3a3a"),
                                                         "west": A.at(10, 7, {(9 - x, y): c for (x, y), c in eyes.items()}, k="#1a3a3a"),
                                                         "north": A.at(10, 7, {(x, 6): "k" for x in range(2, 8)}, k="#1a3a3a")})
    m.cube("head", [-4, 5, -18], [8, 4, 3], SP2)                                    # snout
    m.bone("jaw", [0, 4, -6], "head", rotation=[25, 0, 0])
    m.cube("jaw", [-4.5, 2, -17], [9, 2, 11], SP)
    for x in (-4, -2, 1, 3):
        m.cube("jaw", [x, 4, -17], [1, 2, 1], SP2)
    # spine + ribs (separate arcs), all emissive
    m.cube("body", [-1, 9, -6], [2, 2, 20], SP)
    for i, z in enumerate((-4, -1, 2, 5)):
        h = 8 - i
        m.pair("body", "body", [-4, 9 - h, z], [1, h, 1], SP2, rot=[0, 0, 10], pivot=[-3, 10, z])
        m.cube("body", [-3, 9, z], [6, 1, 1], SP)
    # dorsal, pectorals
    m.cube("body", [-0.5, 10, -3], [1, 8, 5], lit("#9ae6e0"), rot=[-25, 0, 0], pivot=[0, 11, -1],
           art={"sides": A("____..\n___...\n__....\n_.....\n......\n......\n......\n......", {})})
    m.bone("fin_r", [-3, 3, -5], "body"); m.bone("fin_l", [3, 3, -5], "body")
    m.cube("fin_r", [-10, 2.5, -7], [7, 1, 4], SP, rot=[0, 20, -25], pivot=[-3, 3, -5])
    m.mirror("fin_l", [-10, 2.5, -7], [7, 1, 4], SP, rot=[0, 20, -25], pivot=[-3, 3, -5])
    m.bone("tail", [0, 10, 14], "body")
    m.cube("tail", [-1, 9, 14], [2, 2, 6], SP)
    m.bone("tailfin", [0, 10, 20], "tail")
    m.cube("tailfin", [-0.5, 10, 18], [1, 10, 3], lit("#9ae6e0"), rot=[-35, 0, 0], pivot=[0, 10, 19])
    m.cube("tailfin", [-0.5, 3, 18], [1, 7, 3], lit("#9ae6e0"), rot=[35, 0, 0], pivot=[0, 10, 19])

    def swim(length, a):
        return {"tail": {"rotation": wave(length, lambda q: [0, S(q) * 18 * a, 0])},
                "tailfin": {"rotation": wave(length, lambda q: [0, S(q - 0.9) * 26 * a, 0])},
                "body": {"rotation": wave(length, lambda q: [0, -S(q) * 4 * a, 0])},
                "fin_r": {"rotation": wave(length, lambda q: [0, 0, S(q) * 8 * a])},
                "fin_l": {"rotation": wave(length, lambda q: [0, 0, -S(q) * 8 * a])},
                "jaw": {"rotation": wave(length, lambda q: [S(q * 0.5) * 6, 0, 0])},
                "root": {"position": wave(length, lambda q: [0, S(q) * 0.6, 0])}}
    anim(m, "idle", 2.4, swim(2.4, 0.5))
    anim(m, "move", 1.0, swim(1.0, 1.0))
    anim(m, "attack", 0.6, {"jaw": {"rotation": keys((0, [0, 0, 0]), (0.15, [25, 0, 0]), (0.3, [-20, 0, 0]), (0.6, [0, 0, 0]))},
                            "root": {"position": keys((0, [0, 0, 0]), (0.15, [0, 0, 2]), (0.3, [0, 0, -5]), (0.6, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.2, {"body": {"rotation": keys((0, [0, 0, 0]), (0.4, [-25, 0, 0]), (0.8, [15, 0, 180]), (1.2, [0, 0, 360]))}}, loop=False)
    anim(m, "special2", 0.8, {"root": {"scale": keys((0, [1, 1, 1]), (0.2, [0.4, 1.2, 0.4]), (0.5, [1.1, 0.9, 1.1]), (0.8, [1, 1, 1]))}}, loop=False)
    return m


# =====================================================================================
# DROWNED HANDS - a cluster of grasping drowned arms on a grave slab
# =====================================================================================
def drowned_hands(seed):
    m = Rig("drowned_hands", 64, 64, seed)
    SK = over(noise(["#4a7a74", "#568880", "#62948c", "#6ea098"]), spots("#8ab8b0", 0.06))
    DARK = noise(["#1e3a36", "#264440", "#2e4e48"])
    m.bone("root", [0, 0, 0])
    m.cube("root", [-8, 0, -8], [16, 2, 16], noise(["#3a3e40", "#464a4c", "#525658"]),
           art={"up": A.at(16, 16, {(x, y): "k" for x in range(2, 14) for y in range(2, 14) if x in (2, 13) or y in (2, 13)}, k="#22262a")})
    hands = [(-4, -3, 14, 0), (3, -2, 18, 1), (0, 4, 11, 2), (-5, 4, 9, 3)]
    names = []
    for k, (x, z, L, i) in enumerate(hands):
        b = f"h{k}"
        m.bone(b, [x, 2, z], "root", rotation=[-8 + i * 4, i * 25, (i - 1.5) * 6])
        m.cube(b, [x - 1.5, 2, z - 1.5], [3, L, 3], SK if k % 2 == 0 else over(noise(["#3a6a64", "#467670", "#52827c"]), drips("#1e3a36", 0.3)),
               art={"sides": A.at(3, L, {(1, L - 1 - j): "d" for j in range(0, 3)}, d="#1e3a36")})
        if k == 1:
            for j in range(3):
                m.cube(b, [x - 2, 4 + j * 3, z - 2], [4, 1, 4], noise(CHAIN))     # a shackled one
        # palm + fingers + thumb
        m.bone(b + "p", [x, 2 + L, z], b)
        m.cube(b + "p", [x - 2.5, 2 + L, z - 1], [5, 4, 2], SK)
        for f, fx in enumerate((-2.5, -1, 0.5, 2)):
            fl = (3, 4, 4, 3)[f]
            m.cube(b + "p", [x + fx, 6 + L, z - 1], [1, fl, 1], SK, rot=[-10 + f * 3, 0, 0], pivot=[x, 6 + L, z])
        m.cube(b + "p", [x + 2.5, 3 + L, z - 1], [1, 3, 1], SK, rot=[0, 0, -40], pivot=[x + 2.5, 3 + L, z])
        names.append(b)
    m.cube("root", [-2, 2, -2], [4, 1, 4], lit("#3fd0c0"))                               # ghostly glow at the root
    idle = {b: {"rotation": wave(3.0, lambda q, i=i: [S(q + i) * 5, S(q * 0.5 + i) * 6, S(q - i) * 4])} for i, b in enumerate(names)}
    idle.update({b + "p": {"rotation": wave(3.0, lambda q, i=i: [max(0, S(q + i * 1.3)) * 25, 0, 0])} for i, b in enumerate(names)})
    anim(m, "idle", 3.0, idle)
    anim(m, "move", 1.2, {b: {"rotation": wave(1.2, lambda q, i=i: [S(q + i) * 12, 0, S(q - i) * 8])} for i, b in enumerate(names)})
    anim(m, "attack", 0.7, {**{b: {"rotation": keys((0, [0, 0, 0]), (0.2, [20, 0, 0]), (0.35, [-35, 0, 0]), (0.7, [0, 0, 0]))} for b in names},
                            **{b + "p": {"rotation": keys((0, [0, 0, 0]), (0.2, [-10, 0, 0]), (0.35, [60, 0, 0]), (0.7, [0, 0, 0]))} for b in names}}, loop=False)
    anim(m, "special", 1.2, {b: {"rotation": keys((0, [0, 0, 0]), (0.4, [-25, 0, 0]), (0.8, [40, 0, 0]), (1.2, [0, 0, 0]))} for b in names}, loop=False)
    anim(m, "special2", 1.0, {b: {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 90, 0]), (1.0, [0, 0, 0]))} for b in names}, loop=False)
    return m


# =====================================================================================
# PHANTOM PIRATE - translucent-teal ghost, tricorn, beard, fading into a wisp
# =====================================================================================
def ghost_pirate(m, name, *, hat_col, skin, coat, eye, boss=False, beard_len=6, tentacle_beard=False, peg=False):
    P = dict(s=skin[1], S=skin[0], e="!" + eye, E="!#e8ffff", h=skin[0], H=coat[0], c=coat[1], C=coat[0], k="#0a1414",
             g="#7a8a88", b="#2a2420", B="#1a1614", G="#3a4a48")
    head = A.at(8, 8, {(1, 3): "e", (2, 3): "E", (5, 3): "E", (6, 3): "e", (0, 2): "S", (7, 2): "S",
                       **{(x, 5): "h" for x in range(2, 6)}, **{(x, 6): "h" for x in range(1, 7)},
                       **{(x, 7): "h" for x in range(2, 6)}, (3, 4): "S", (4, 4): "S"}, P)
    coat_front = A.at(8, 12, {**{(x, y): "C" for x in (0, 1, 6, 7) for y in range(12)}, **{(3, y): "k" for y in range(0, 7)},
                              **{(x, 7): "b" for x in range(8)}, **{(x, 8): "b" for x in range(8)}, (3, 7): "g", (4, 7): "g", (4, 8): "g",
                              (2, 3): "G", (5, 3): "G", (2, 5): "G", (5, 5): "G"}, P)
    SK = noise(skin)
    CO = noise(coat)
    a = biped(m, head=SK, body=CO, arm=bands(CO, (9, 12, SK)), leg=CO if not boss else noise(coat),
              art={"head": {"north": head}, "body": {"north": coat_front}})
    t = a["top"]
    if not peg:
        # ghost: no legs - replace with a tapering wisp
        m.cubes = [c for c in m.cubes if c.bone not in ("rleg", "lleg")]
        m.bone("wisp", [0, 12, 0], "body")
        m.cube("wisp", [-4, 6, -2], [8, 6, 4], CO, art={"sides": A("........\n........\n._.__._.\n__.__.__", {})})
        m.cube("wisp", [-3, 1, -1.5], [6, 5, 3], lit(skin[2]), art={"sides": A("......\n.._...\n_.._._\n__.__.\n___.__", {})})
        m.cube("wisp", [-1.5, -3, -1], [3, 4, 2], lit(skin[3]), art={"sides": A("...\n_._\n._.\n__.", {})})
    else:
        # the captain keeps one leg - the other is a peg
        m.cubes = [c for c in m.cubes if c.bone != "rleg"]
        m.cube("rleg", [-3, 0, -1], [2, 12, 2], noise(["#2a2016", "#3a2c1e"]))
        m.cube("rleg", [-3.5, 9, -1.5], [3, 3, 3], noise(["#1a1612", "#2a2420"]))
    # beard strands
    m.cube("head", [-3, t - beard_len + 1, -4.6], [6, beard_len - 1, 1], SK if not tentacle_beard else lit(skin[2]),
           art={"north": A.at(6, beard_len - 1, {(0, beard_len - 2): "_", (5, beard_len - 2): "_", (2, beard_len - 2): "_"})})
    if tentacle_beard:
        for dx in (-2.5, -0.5, 1.5):
            m.cube("head", [dx, t - beard_len - 3, -4.5], [1, 4, 1], lit(skin[3]), rot=[10, 0, dx * 4], pivot=[dx, t - beard_len + 1, -4])
    # tricorn
    HAT = noise(hat_col)
    m.bone("hat", [0, t + 8, 0], "head")
    m.cube("hat", [-6, t + 7.5, -6], [12, 1, 12], HAT)
    m.cube("hat", [-4.5, t + 8.5, -4.5], [9, 3, 9], HAT)
    m.cube("hat", [-5, t + 8.5, -6.5], [10, 3, 1], HAT, rot=[-18, 0, 0], pivot=[0, t + 8.5, -6])
    m.pair("hat", "hat", [-6.5, t + 8.5, -5], [1, 3, 10], HAT, rot=[0, 0, 18], pivot=[-6, t + 8.5, 0])
    m.cube("hat", [-4.5, t + 8.5, 5.5], [9, 3, 1], HAT, rot=[18, 0, 0], pivot=[0, t + 8.5, 6])
    # coat tails
    m.bone("coat", [0, t - 12, 0], "body")
    m.cube("coat", [-4.5, t - 22, 1.5], [9, 10, 1], CO, art={"south": A(".........\n" * 7 + "._.._..._\n_.__._.__\n__.__.___", {})})
    m.pair("coat", "coat", [-4.6, t - 21, -2.4], [1, 9, 4], CO, art={"sides": A("....\n" * 7 + "._..\n_.__", {})})
    return a


def phantom_pirate(seed):
    m = Rig("phantom_pirate", 64, 64, seed)
    ghost_pirate(m, "phantom_pirate", hat_col=["#0a2e2c", "#0e3a38", "#124644"], skin=["#1e7a74", "#2a8a82", "#3aa89e", "#5ac8bc"],
                 coat=["#0e3a38", "#124644", "#16524e"], eye="#8af8ff", beard_len=7)
    biped_anims(m, "claw", legs=False, cape="coat")
    m.anims["idle"]["bones"]["wisp"] = {"rotation": wave(3.0, lambda q: [S(q - 1) * 10, 0, S(q) * 6])}
    m.anims["move"]["bones"]["wisp"] = {"rotation": wave(1.0, lambda q: [25 + S(q - 1) * 12, 0, 0])}
    return m


def ghost_captain(seed):
    m = Rig("ghost_captain", 64, 64, seed)
    a = ghost_pirate(m, "ghost_captain", hat_col=["#161818", "#1e2020", "#262828"], skin=["#2a4644", "#34524e", "#3e5e5a", "#4a6c66"],
                     coat=["#141616", "#1c1e1e", "#242626"], eye="#8af0ff", boss=True, beard_len=7, tentacle_beard=True, peg=True)
    t = a["top"]
    m.cube("hat", [-4.6, t + 8.4, -4.6], [9, 1, 9], noise(["#4a1a16", "#5a221c"]))       # blood-red hat band
    # cursed cutlass with a notched blade
    rx, hy = a["rx"], a["hand_y"]
    pv = [rx, hy, 0]
    m.cube("rhand", [rx - 0.5, hy - 1, -1], [1, 3, 2], solid("#1a1616"), rot=[-55, 0, 0], pivot=pv)
    m.cube("rhand", [rx - 1.5, hy - 1.5, -3], [3, 3, 1], solid("#2a2c2c"), rot=[-55, 0, 0], pivot=pv)
    m.cube("rhand", [rx - 0.5, hy - 1, -16], [1, 3, 13], noise(["#5a6a6a", "#6a7a7a", "#7a8a88"]), rot=[-55, 0, 0], pivot=pv,
           art={"east": A.at(13, 3, {(2, 0): "_", (5, 0): "_", (9, 0): "_"}), "west": A.at(13, 3, {(3, 0): "_", (7, 0): "_", (10, 0): "_"})})
    # big coat cuffs
    m.pair("rarm", "larm", [-8.4, t - 10, -2.4], [5, 2, 5], noise(["#2a2c2c", "#343636"]))
    biped_anims(m, "sword", cape="coat", hold=[-25, 0, 0])
    return m


# =====================================================================================
# CHAINED REVENANT - boss; hulking hooded wraith bound in chains, shackle weights
# =====================================================================================
def chained_revenant(seed):
    m = Rig("chained_revenant", 128, 128, seed)
    STONE = over(noise(["#161a1c", "#1e2224", "#262a2c", "#2e3234"]), speckle("#3a2e22", 0.04))
    CH = noise(CHAIN)
    link = lambda w, h: A.at(w, h, {(x, y): "l" for x in range(w) for y in range(h) if (x + y) % 3 == 0}, l="#50545a")
    m.bone("root", [0, 0, 0])
    for side, sx in (("rleg", -1), ("lleg", 1)):
        m.bone(side, [sx * 3.5, 12, 0], "root")
        x0 = -7 if sx < 0 else 0
        m.cube(side, [x0, 2, -3], [7, 10, 6], STONE)
        m.cube(side, [x0 - 0.5, 0, -4], [8, 3, 8], STONE)
        m.cube(side, [x0 - 0.5, 4, -3.5], [8, 2, 7], CH, art={"sides": link(8, 2)})        # leg shackles
    m.bone("body", [0, 12, 0], "root")
    m.cube("body", [-7, 12, -4], [14, 6, 8], STONE)
    m.cube("body", [-6, 6, -4.4], [12, 7, 1], STONE, art={"north": A.at(12, 7, {(0, 6): "_", (2, 6): "_", (3, 5): "_", (7, 6): "_", (9, 6): "_", (10, 5): "_", (11, 6): "_"})})
    m.cube("body", [-8, 18, -5], [16, 12, 10], STONE)
    # crossing chains over the chest (rotated bands)
    m.cube("body", [-9, 23, -5.6], [18, 2, 1], CH, rot=[0, 0, 32], pivot=[0, 24, -5], art={"north": link(18, 2)})
    m.cube("body", [-9, 23, -5.7], [18, 2, 1], CH, rot=[0, 0, -32], pivot=[0, 24, -5], art={"north": link(18, 2)})
    m.cube("body", [-8.5, 16, -5.5], [17, 2, 11], CH, art={"sides": link(17, 2)})
    # hooded head: glowing teal eyes + skull teeth, chains wrapped around the hood
    m.bone("head", [0, 30, -2], "body")
    face = {(1, 3): "e", (2, 3): "E", (5, 3): "E", (6, 3): "e", (3, 5): "e", (4, 5): "e",
            (2, 6): "t", (3, 7): "t", (4, 6): "t", (5, 7): "t"}
    m.cube("head", [-4, 29, -7], [8, 9, 8], noise(["#101214", "#16181a", "#1c1e20"]), art={"north": A.at(8, 9, {(x, y + 1): c for (x, y), c in face.items()}, e="!#3fe0c0", E="!#aafff0", t="!#3fe0c0")})
    m.cube("head", [-5, 29, -6], [10, 11, 10], noise(["#1a1c1e", "#222426"]), art={"north": A.at(10, 11, {(x, y): "_" for x in range(1, 9) for y in range(1, 11)})})
    m.cube("head", [-5.5, 33, -6.5], [11, 2, 11], CH, rot=[0, 0, 12], pivot=[0, 34, 0], art={"sides": link(11, 2)})
    # arms: massive, chain-wrapped, shackle weights on chains
    for side, sx in (("rarm", -1), ("larm", 1)):
        m.bone(side, [sx * 11, 28, 0], "body")
        ax = -15 if sx < 0 else 8
        m.cube(side, [ax - 0.5, 23, -4.5], [8, 7, 9], STONE)
        m.cube(side, [ax + 0.5, 15, -3], [6, 8, 6], STONE)
        m.cube(side, [ax, 17, -3.5], [7, 2, 7], CH, art={"sides": link(7, 2)})
        m.bone(side + "_fist", [sx * 11.5, 15, 0], side)
        m.cube(side + "_fist", [ax, 8, -3.5], [7, 7, 7], STONE)
        m.cube(side + "_fist", [ax - 0.5, 12, -4], [8, 2, 8], CH, art={"sides": link(8, 2)})
        m.bone(side + "_chain", [sx * 11.5, 8, 0], side + "_fist")
        m.cube(side + "_chain", [ax + 3, 5, -1], [2, 3, 1], CH)
        m.cube(side + "_chain", [ax + 3.5, 3, -1.5], [1, 3, 2], CH)
        if sx < 0:
            m.cube(side + "_chain", [ax, -1, -3], [6, 5, 6], noise(["#1e2022", "#2a2c2e"]))    # shackle weight on the ground
        else:
            m.cube(side + "_chain", [ax + 3, 0, -2], [2, 4, 2], CH)                              # hook
            m.cube(side + "_chain", [ax + 3, -1, -2], [4, 2, 2], CH)
            m.cube(side + "_chain", [ax + 6, 0, -2], [1, 3, 2], CH)
    anim(m, "idle", 3.0, {"body": {"rotation": wave(3.0, lambda q: [4 + S(q) * 1.5, 0, 0])},
                          "head": {"rotation": wave(3.0, lambda q: [S(q) * 3, S(q * 0.5) * 10, 0])},
                          "rarm": {"rotation": wave(3.0, lambda q: [S(q) * 3, 0, 4])},
                          "larm": {"rotation": wave(3.0, lambda q: [-S(q) * 3, 0, -4])},
                          "rarm_chain": {"rotation": wave(3.0, lambda q: [S(q) * 8, 0, S(q * 0.5) * 6])},
                          "larm_chain": {"rotation": wave(3.0, lambda q: [S(q + 1) * 8, 0, S(q * 0.5 + 1) * 6])}})
    anim(m, "move", 1.5, {"rleg": {"rotation": wave(1.5, lambda q: [-S(q) * 22, 0, 0])},
                          "lleg": {"rotation": wave(1.5, lambda q: [S(q) * 22, 0, 0])},
                          "rarm": {"rotation": wave(1.5, lambda q: [S(q) * 14, 0, 4])},
                          "larm": {"rotation": wave(1.5, lambda q: [-S(q) * 14, 0, -4])},
                          "body": {"rotation": wave(1.5, lambda q: [6, S(q) * 4, 0]), "position": wave(1.5, lambda q: [0, -abs(S(q)) * 1.2, 0])},
                          "rarm_chain": {"rotation": wave(1.5, lambda q: [S(q - 1) * 20, 0, 0])},
                          "larm_chain": {"rotation": wave(1.5, lambda q: [-S(q - 1) * 20, 0, 0])}})
    anim(m, "attack", 0.8, {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-130, 10, 10]), (0.45, [15, 0, 0]), (0.8, [0, 0, 0]))},
                            "rarm_chain": {"rotation": keys((0, [0, 0, 0]), (0.3, [-40, 0, 0]), (0.45, [60, 0, 0]), (0.8, [0, 0, 0]))},
                            "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-5, 20, 0]), (0.45, [12, -16, 0]), (0.8, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.6, {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-165, 0, 10]), (0.65, [-165, 0, 10]), (0.85, [25, 0, 0]), (1.2, [25, 0, 0]), (1.6, [0, 0, 0]))},
                             "larm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-165, 0, -10]), (0.65, [-165, 0, -10]), (0.85, [25, 0, 0]), (1.2, [25, 0, 0]), (1.6, [0, 0, 0]))},
                             "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [-12, 0, 0]), (0.85, [22, 0, 0]), (1.2, [22, 0, 0]), (1.6, [0, 0, 0]))},
                             "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [-30, 0, 0]), (1.6, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 1.0, {"larm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-70, -40, -60]), (0.55, [-90, 50, -10]), (1.0, [0, 0, 0]))},
                              "larm_chain": {"rotation": keys((0, [0, 0, 0]), (0.3, [-90, 0, 0]), (0.55, [-160, 0, 0]), (1.0, [0, 0, 0]))},
                              "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, -25, 0]), (0.55, [0, 30, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    return m


from mobs.ghostcaptain import ghost_captain as _ghostcap  # noqa: E402  (rebuilt 2026-09-29, own module)

import mobs.revenant as _rev  # noqa: E402  (rebuilt 2026-09-29, own module)

MOBS = {"skeleton_pirate": skeleton_pirate, "trident_skeleton": trident_skeleton, "ghost_shark": ghost_shark,
        "drowned_hands": drowned_hands, "phantom_pirate": phantom_pirate, "ghost_captain": _ghostcap,
        "chained_revenant": _rev.chained_revenant,
        "revenant_arm_r": lambda seed: _rev.revenant_arm(seed, "r"), "revenant_arm_l": lambda seed: _rev.revenant_arm(seed, "l"),
        "revenant_leg_r": lambda seed: _rev.revenant_leg(seed, "r"), "revenant_leg_l": lambda seed: _rev.revenant_leg(seed, "l"),
        "revenant_head": _rev.revenant_head}
import mobs.gallows as _gal  # noqa: E402  (Gallows Grotto set pieces: the Gallowbrand, its stone, Old Wick)
MOBS.update(_gal.MOBS)
import mobs.guns as _guns  # noqa: E402  (homestead guns: GeckoLib item models)
MOBS.update(_guns.MOBS)
SKINS = {"ghost_captain": {"enraged": lambda seed: _ghostcap(seed, "enraged")},
         "chained_revenant": {"enraged": lambda seed: _rev.chained_revenant(seed, "enraged")},
         "revenant_arm_r": {"enraged": lambda seed: _rev.revenant_arm(seed, "r", "enraged")},
         "revenant_arm_l": {"enraged": lambda seed: _rev.revenant_arm(seed, "l", "enraged")},
         "revenant_leg_r": {"enraged": lambda seed: _rev.revenant_leg(seed, "r", "enraged")},
         "revenant_leg_l": {"enraged": lambda seed: _rev.revenant_leg(seed, "l", "enraged")},
         "revenant_head": {"enraged": lambda seed: _rev.revenant_head(seed, "enraged")}}
import mobs.traders as _traders  # noqa: E402  (homestead port traders: one geometry, four profession skins)
MOBS.update(_traders.MOBS)
SKINS.update(_traders.SKINS)
import mobs.hideout as _hideout  # noqa: E402  (homestead hideout flag: GeckoLib block)
MOBS.update(_hideout.MOBS)
import mobs.roulette as _roulette  # noqa: E402  (homestead roulette table: GeckoLib block)
MOBS.update(_roulette.MOBS)
