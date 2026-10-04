"""Phase 5 roster (the abyss)."""
import math

from mobkit import A, Rig, bands, counter, grad, lit, noise, over, solid, spots, veins, drips, speckle, anim, keys, wave, S
from mobs.common import biped, biped_anims
from mobs.p2 import serpent_body, serpent_anims, coral_branch

DEEP = ["#0e1a22", "#12212a", "#172832", "#1c2f3a"]
CYAN = "#3fe0f0"
CYAN_HI = "#aaf6ff"


def legpair(m, parent, x, y, z, sx, up_len, down_len, mat, name, splay=35, knee=70, thick=2):
    """Arched arthropod leg: coxa bone reaching out and up, then a knee bone dropping to the ground."""
    b1 = f"{name}"
    m.bone(b1, [x, y, z], parent, rotation=[0, 0, -sx * splay])
    x0 = x if sx > 0 else x - up_len
    m.cube(b1, [x0, y - thick / 2, z - thick / 2], [up_len, thick, thick], mat)
    kx = x + sx * up_len
    m.bone(b1 + "k", [kx, y, z], b1, rotation=[0, 0, sx * knee])
    m.cube(b1 + "k", [kx - (0 if sx > 0 else down_len), y - thick / 2, z - thick / 2], [down_len, thick, thick], mat)
    return [b1, b1 + "k"]


def walk_legs(names_by_pair, length=0.6, amp=18):
    d = {}
    for i, (r, l) in enumerate(names_by_pair):
        ph = i * 1.3
        d[r] = {"rotation": wave(length, lambda q, ph=ph: [0, S(q + ph) * amp, max(0, S(q + ph)) * 10])}
        d[l] = {"rotation": wave(length, lambda q, ph=ph: [0, S(q + ph + math.pi) * amp, -max(0, S(q + ph + math.pi)) * 10])}
    return d


# =====================================================================================
# ABYSS CRAB
# =====================================================================================
def abyss_crab(seed):
    m = Rig("abyss_crab", 64, 64, seed)
    SH = over(noise(DEEP), speckle("#26404e", 0.06))
    zig = {}
    for x in range(1, 13):
        zig[(x, 3 + (x // 2) % 2)] = "c"
    for x in range(2, 11):
        zig[(x, 8 + ((x + 1) // 2) % 2)] = "c"
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 9, 0], "root")
    m.cube("body", [-7, 6, -6], [14, 5, 12], SH, art={"up": A.at(14, 12, zig, c="!" + CYAN), "north": A.at(14, 5, {(x, 4): "t" for x in range(2, 12, 2)}, t="#26404e")})
    m.cube("body", [-5, 11, -5], [10, 2, 9], SH, art={"up": A.at(10, 9, {(x, 4): "c" for x in range(1, 9) if x % 3}, c="!" + CYAN)})
    # face: two big glowing eyes + stalk nubs + a fringe of teeth below
    m.cube("body", [-5, 7, -7], [10, 3, 1], SH, art={"north": A.at(10, 3, {(1, 0): "E", (2, 0): "E", (1, 1): "E", (2, 1): "e",
                                                                          (7, 0): "E", (8, 0): "E", (7, 1): "e", (8, 1): "E"}, E="!" + CYAN, e="!" + CYAN_HI)})
    for x in (-3, 2):
        m.cube("body", [x, 13, -4], [1, 2, 1], SH)
    for x in range(-4, 4, 2):
        m.cube("body", [x, 5, -6.5], [1, 1, 1], solid("#26404e"))
    legs = []
    for i, z in enumerate((-3, 0, 3)):
        r = legpair(m, "body", -7, 8, z, -1, 5, 11, SH, f"leg{i}r", splay=45, knee=112)
        l = legpair(m, "body", 7, 8, z, 1, 5, 11, SH, f"leg{i}l", splay=45, knee=112)
        legs.append((r[0], l[0]))
    for side, sx in (("rclaw", -1), ("lclaw", 1)):
        m.bone(side, [sx * 6, 8, -6], "body", rotation=[0, -sx * 25, 0])
        x0 = -9 if sx < 0 else 3
        m.cube(side, [x0, 6, -12], [6, 5, 7], SH, art={"up": A.at(6, 7, {(2, 1): "c", (3, 2): "c", (3, 3): "c", (4, 4): "c", (4, 5): "c"}, c="!" + CYAN)})
        m.bone(side + "_f", [sx * 6, 6, -11], side)
        m.cube(side + "_f", [x0 + 1, 4, -15], [4, 2, 5], SH)
        m.cube(side, [x0 + 1, 8, -15], [4, 3, 3], SH)
    idle = {"body": {"position": wave(2.5, lambda q: [0, S(q) * 0.3, 0])},
            "rclaw_f": {"rotation": wave(2.5, lambda q: [max(0, S(q)) * 15, 0, 0])},
            "lclaw_f": {"rotation": wave(2.5, lambda q: [max(0, S(q + 1)) * 15, 0, 0])}}
    anim(m, "idle", 2.5, idle)
    mv = walk_legs(legs); mv["body"] = {"position": wave(0.6, lambda q: [0, abs(S(q)) * 0.4, 0]), "rotation": wave(0.6, lambda q: [0, 0, S(q) * 2])}
    anim(m, "move", 0.6, mv)
    anim(m, "attack", 0.5, {"rclaw": {"rotation": keys((0, [0, 0, 0]), (0.15, [-35, 15, 0]), (0.3, [10, -5, 0]), (0.5, [0, 0, 0]))},
                            "rclaw_f": {"rotation": keys((0, [0, 0, 0]), (0.15, [30, 0, 0]), (0.3, [0, 0, 0]), (0.5, [0, 0, 0]))},
                            "lclaw": {"rotation": keys((0, [0, 0, 0]), (0.2, [-35, -15, 0]), (0.35, [10, 5, 0]), (0.5, [0, 0, 0]))},
                            "lclaw_f": {"rotation": keys((0, [0, 0, 0]), (0.2, [30, 0, 0]), (0.35, [0, 0, 0]), (0.5, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.0, {"body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-20, 0, 0]), (0.7, [-20, 0, 0]), (1.0, [0, 0, 0]))},
                             "rclaw": {"rotation": keys((0, [0, 0, 0]), (0.3, [-50, 0, -25]), (0.7, [-50, 0, -25]), (1.0, [0, 0, 0]))},
                             "lclaw": {"rotation": keys((0, [0, 0, 0]), (0.3, [-50, 0, 25]), (0.7, [-50, 0, 25]), (1.0, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 0.8, {"body": {"position": keys((0, [0, 0, 0]), (0.3, [0, -3, 0]), (0.5, [0, 2, -4]), (0.8, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# BRAIN FISH
# =====================================================================================
def brain_fish(seed):
    m = Rig("brain_fish", 64, 64, seed)
    SK = noise(["#0e1e2e", "#12263a", "#162e44", "#1a364e"])
    m.bone("root", [0, 4, 0])
    m.bone("body", [0, 4, 0], "root")
    m.cube("body", [-4, 0, -5], [8, 8, 12], SK)
    m.bone("head", [0, 4, -5], "body")
    eye = {(1, 2): "E", (2, 2): "e", (1, 3): "e", (2, 3): "e"}
    m.cube("head", [-4, 0, -11], [8, 7, 6], SK, art={"east": A.at(6, 7, {(x + 1, y): c for (x, y), c in eye.items()}, E="!" + CYAN_HI, e="!#4ac8e8"),
                                                      "west": A.at(6, 7, {(4 - x, y): c for (x, y), c in eye.items()}, E="!" + CYAN_HI, e="!#4ac8e8")})
    m.cube("head", [-3, 0, -13], [6, 4, 2], SK)
    # the brain: two lobes, glowing folded veins
    BR = over(lit("#3a5ae0"), veins("#a8f0ff", 0.25, glow=True, length=(4, 9)), veins("#b08aff", 0.08, glow=True))
    m.bone("brain", [0, 8, -4], "body")
    m.cube("brain", [-4.5, 7, -10], [9, 5, 11], BR)
    m.cube("brain", [-3.5, 12, -9], [7, 2, 9], BR)
    m.cube("brain", [-0.5, 12, -9.5], [1, 2, 10], lit("#1a2a8a"))                    # the fissure
    # fins
    FIN = noise(["#12263a", "#1a364e", "#22405a"])
    m.bone("tail", [0, 4, 7], "body")
    m.cube("tail", [-2.5, 1.5, 7], [5, 5, 4], SK)
    m.cube("tail", [-0.5, 4, 10], [1, 7, 3], FIN, rot=[-30, 0, 0], pivot=[0, 4, 11])
    m.cube("tail", [-0.5, -2, 10], [1, 6, 3], FIN, rot=[30, 0, 0], pivot=[0, 4, 11])
    m.cube("body", [-0.5, 8, 2], [1, 5, 3], FIN, rot=[-20, 0, 0], pivot=[0, 8, 3])
    m.bone("fin_r", [-4, 1, -2], "body"); m.bone("fin_l", [4, 1, -2], "body")
    m.cube("fin_r", [-9, 0.5, -4], [5, 1, 4], FIN, rot=[0, 20, -25], pivot=[-4, 1, -2])
    m.mirror("fin_l", [-9, 0.5, -4], [5, 1, 4], FIN, rot=[0, 20, -25], pivot=[-4, 1, -2])
    m.cube("body", [-1, -2, -2], [2, 2, 3], FIN)

    def swim(length, a):
        return {"tail": {"rotation": wave(length, lambda q: [0, S(q) * 20 * a, 0])},
                "body": {"rotation": wave(length, lambda q: [0, -S(q) * 4 * a, 0])},
                "fin_r": {"rotation": wave(length, lambda q: [0, 0, S(q) * 12 * a])},
                "fin_l": {"rotation": wave(length, lambda q: [0, 0, -S(q) * 12 * a])},
                "brain": {"scale": wave(length, lambda q: [1 + 0.03 * S(q * 2), 1 + 0.05 * S(q * 2), 1 + 0.03 * S(q * 2)])}}
    anim(m, "idle", 2.4, swim(2.4, 0.5))
    anim(m, "move", 1.0, swim(1.0, 1.0))
    anim(m, "attack", 0.6, {"head": {"rotation": keys((0, [0, 0, 0]), (0.15, [-12, 0, 0]), (0.3, [8, 0, 0]), (0.6, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.2, {"brain": {"scale": keys((0, [1, 1, 1]), (0.4, [1.25, 1.35, 1.25]), (0.8, [1.25, 1.35, 1.25]), (1.2, [1, 1, 1]))},
                             "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [-15, 0, 0]), (1.2, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 1.0, {"brain": {"scale": keys((0, [1, 1, 1]), (0.3, [1.15, 1.15, 1.15]), (0.5, [0.9, 0.9, 0.9]), (1.0, [1, 1, 1]))},
                              "root": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 0, 20]), (1.0, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# ABYSSAL CENTIPEDE
# =====================================================================================
def abyssal_centipede(seed):
    m = Rig("abyssal_centipede", 64, 64, seed)
    SEG = noise(["#141a1c", "#1a2226", "#20292e", "#262f34"])
    TOP = {(x, y): "p" for x in range(1, 7) for y in range(1, 5) if x in (1, 6) or y in (1, 4)}
    m.bone("root", [0, 0, 0])
    m.bone("head", [0, 4, -8], "root")
    m.cube("head", [-3.5, 2, -13], [7, 5, 5], noise(["#2a4448", "#325054", "#3a5c60"]),
           art={"north": A.at(7, 5, {(1, 1): "e", (2, 1): "E", (4, 1): "E", (5, 1): "e", (1, 3): "k", (5, 3): "k"}, e="!#a8f0ff", E="!#e8ffff", k="#141a1c")})
    m.cube("head", [-3, 1, -14], [6, 2, 2], noise(["#1a2226", "#262f34"]))           # mandibles
    for sx in (-1, 1):
        ant = f"ant_{'r' if sx < 0 else 'l'}"
        m.bone(ant, [sx * 2, 7, -12], "head", rotation=[-40, 0, -sx * 25])
        m.cube(ant, [sx * 2 - 0.5, 7, -12.5], [1, 6, 1], SEG)
        m.bone(ant + "2", [sx * 2, 13, -12], ant, rotation=[80, 0, 0])
        m.cube(ant + "2", [sx * 2 - 0.5, 13, -12.5], [1, 4, 1], SEG)
    prev, z = "head", -8
    segs, legs = [], []
    for i in range(6):
        b = f"seg{i}"
        m.bone(b, [0, 4, z], prev)
        m.cube(b, [-4, 2, z], [8, 5, 6], SEG, art={"up": A.at(8, 6, {(x, y + 1): c for (x, y), c in TOP.items()}, p="#3a4a50")})
        r = legpair(m, b, -4, 3, z + 3, -1, 4, 5, SEG, f"leg{i}r", splay=15, knee=80, thick=1)
        l = legpair(m, b, 4, 3, z + 3, 1, 4, 5, SEG, f"leg{i}l", splay=15, knee=80, thick=1)
        legs.append((r[0], l[0]))
        segs.append(b)
        prev, z = b, z + 6
    m.cube(prev, [-0.5, 3, z], [1, 1, 4], SEG, rot=[-20, 20, 0], pivot=[0, 4, z])
    m.cube(prev, [-0.5, 3, z], [1, 1, 4], SEG, rot=[-20, -20, 0], pivot=[0, 4, z])
    m.cube("seg2", [-1, 6.5, 5], [2, 1, 2], lit("#3fd0c0"))                            # a faint glow gland
    wig = lambda length, a: {b: {"rotation": wave(length, lambda q, i=i: [0, S(q - i * 0.9) * a, 0])} for i, b in enumerate(segs)}
    idle = wig(3.0, 4)
    idle["ant_r"] = {"rotation": wave(1.5, lambda q: [S(q) * 10, 0, 0])}
    idle["ant_l"] = {"rotation": wave(1.5, lambda q: [S(q + 1) * 10, 0, 0])}
    anim(m, "idle", 3.0, idle)
    mv = wig(0.8, 10); mv.update(walk_legs(legs, 0.4, 22))
    anim(m, "move", 0.8, mv)
    anim(m, "attack", 0.5, {"head": {"rotation": keys((0, [0, 0, 0]), (0.15, [-25, 0, 0]), (0.3, [15, 0, 0]), (0.5, [0, 0, 0]))},
                            "seg0": {"rotation": keys((0, [0, 0, 0]), (0.15, [-15, 0, 0]), (0.3, [8, 0, 0]), (0.5, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.0, {"head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-40, 0, 0]), (0.7, [-40, 0, 0]), (1.0, [0, 0, 0]))},
                             "seg0": {"rotation": keys((0, [0, 0, 0]), (0.4, [-30, 0, 0]), (0.7, [-30, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 0.6, {"head": {"rotation": keys((0, [0, 0, 0]), (0.2, [-20, 0, 0]), (0.35, [20, 0, 0]), (0.6, [0, 0, 0])),
                                       "position": keys((0, [0, 0, 0]), (0.2, [0, 1, 3]), (0.35, [0, 0, -6]), (0.6, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# SHADOW - a featureless silhouette with the faintest eyes
# =====================================================================================
def shadow(seed):
    m = Rig("shadow", 64, 64, seed)
    BL = noise(["#050506", "#08080a", "#0b0b0e", "#0e0e12"])
    ring = {(x, y): "r" for x in range(1, 7) for y in range(1, 7) if x in (1, 6) or y in (1, 6)}
    ring.update({(2, 3): "e", (5, 3): "e"})
    biped(m, head=BL, body=BL, arm=BL, leg=BL, art={"head": {"north": A.at(8, 8, ring, r="#16161c", e="!#2a1a3e")}})
    biped_anims(m, "claw")
    m.anims["idle"]["bones"]["root"] = {"scale": wave(2.0, lambda q: [1 + 0.02 * S(q), 1, 1 + 0.02 * S(q)])}
    return m


# =====================================================================================
# CORAL WHALE - gentle giant; reef garden on its back
# =====================================================================================
def tube(m, bone, x, y, z, w, h, color, glow=False):
    mat = lit(color) if glow else noise([color, color])
    m.cube(bone, [x, y, z], [w, h, w], mat, art={"up": A.at(w, w, {(w // 2, w // 2): "k", ((w - 1) // 2, (w - 1) // 2): "k"}, k="#101820")})


def coral_whale(seed):
    m = Rig("coral_whale", 128, 128, seed)
    BACK = over(grad(["#101c3a", "#142246", "#182852", "#1c2e5c"], ["#1e5a8a", "#2a6a98", "#3478a4"]), spots("#3fd0e8", 0.02, glow=True))
    BELLY = noise(["#2a6a98", "#3478a4", "#3e84ae"])
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 8, 0], "root")
    m.cube("body", [-9, 0, -12], [18, 16, 30], BACK)
    m.bone("head", [0, 8, -12], "body")
    m.cube("head", [-8.5, 3, -26], [17, 13, 14], BACK,
           art={"east": A.at(14, 13, {(9, 6): "y", (10, 6): "y", (9, 7): "k", (10, 7): "k"}, y="!#e0c030", k="#0a0e18"),
                "west": A.at(14, 13, {(4, 6): "y", (3, 6): "y", (4, 7): "k", (3, 7): "k"}, y="!#e0c030", k="#0a0e18"),
                "up": A.at(17, 14, {(8, 4): "c", (7, 5): "c", (9, 5): "c", (8, 6): "c", (5, 9): "c", (11, 8): "c"}, c="!" + CYAN)})
    m.bone("jaw", [0, 3, -13], "head")
    m.cube("jaw", [-8, -1, -26], [16, 4, 14], BELLY)
    # back garden: tube corals (hollow tops), glowing seagrass blades, coral blocks
    tube(m, "body", -6, 16, -10, 4, 5, "#3fc8e0", glow=True)
    tube(m, "body", -1, 16, -8, 4, 7, "#c83a9a")
    tube(m, "body", 3, 16, -11, 3, 3, "#4a8a3a")
    tube(m, "body", 4, 16, -5, 4, 4, "#5aa040")
    m.cube("body", [-5, 16, -4], [3, 3, 3], lit("#8af0ff"))
    for (x, z, h) in ((-7, -2, 4), (-5, 2, 5), (-2, 0, 3), (6, 3, 4), (2, 6, 3), (-6, 8, 4)):
        m.cube("body", [x, 16, z], [1, h, 1], lit("#3fc8c0"))
    # flippers (long, angled back), tail stock + upright fluke
    FL = noise(["#142246", "#182852", "#1c2e5c", "#22386a"])
    m.bone("fin_r", [-9, 4, -6], "body"); m.bone("fin_l", [9, 4, -6], "body")
    m.cube("fin_r", [-27, 3.5, -9], [18, 2, 6], FL, rot=[0, 40, -20], pivot=[-9, 4, -6])
    m.mirror("fin_l", [-27, 3.5, -9], [18, 2, 6], FL, rot=[0, 40, -20], pivot=[-9, 4, -6])
    m.bone("tail", [0, 8, 18], "body")
    m.cube("tail", [-6, 3, 18], [12, 10, 10], BACK)
    m.bone("fluke", [0, 8, 28], "tail")
    m.cube("fluke", [-4, 4, 27], [8, 6, 6], BACK)
    m.cube("fluke", [-1, 8, 30], [2, 14, 5], FL, rot=[-25, 0, 0], pivot=[0, 8, 31])
    m.cube("fluke", [-1, -3, 30], [2, 11, 5], FL, rot=[25, 0, 0], pivot=[0, 8, 31])

    def swim(length, a):
        return {"tail": {"rotation": wave(length, lambda q: [S(q) * 6 * a, S(q) * 6 * a, 0])},
                "fluke": {"rotation": wave(length, lambda q: [0, S(q - 0.9) * 14 * a, 0])},
                "body": {"rotation": wave(length, lambda q: [S(q + 1) * 1.5 * a, -S(q) * 2 * a, 0])},
                "fin_r": {"rotation": wave(length, lambda q: [0, 0, S(q) * 10 * a])},
                "fin_l": {"rotation": wave(length, lambda q: [0, 0, -S(q) * 10 * a])},
                "jaw": {"rotation": wave(length, lambda q: [max(0, S(q * 0.5)) * 4, 0, 0])}}
    anim(m, "idle", 4.0, swim(4.0, 0.6))
    anim(m, "move", 2.2, swim(2.2, 1.0))
    anim(m, "attack", 1.0, {"jaw": {"rotation": keys((0, [0, 0, 0]), (0.4, [18, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 2.0, {"jaw": {"rotation": keys((0, [0, 0, 0]), (0.6, [20, 0, 0]), (1.4, [20, 0, 0]), (2.0, [0, 0, 0]))},
                             "head": {"rotation": keys((0, [0, 0, 0]), (0.6, [-12, 0, 0]), (2.0, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 1.5, {"body": {"rotation": keys((0, [0, 0, 0]), (0.7, [0, 0, 30]), (1.5, [0, 0, 0]))}}, loop=False)
    # fed a krill cluster (CoralWhaleEntity): one happy barrel roll round its long axis, flippers out, jaw open
    anim(m, "roll", 1.6, {"body": {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 0, 90]), (0.8, [0, 0, 180]), (1.2, [0, 0, 270]), (1.6, [0, 0, 360]))},
                          "fin_r": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 25]), (1.3, [0, 0, 25]), (1.6, [0, 0, 0]))},
                          "fin_l": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, -25]), (1.3, [0, 0, -25]), (1.6, [0, 0, 0]))},
                          "jaw": {"rotation": keys((0, [0, 0, 0]), (0.4, [14, 0, 0]), (1.2, [14, 0, 0]), (1.6, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# LUMINOUS ISOPOD
# =====================================================================================
def luminous_isopod(seed):
    m = Rig("luminous_isopod", 64, 64, seed)
    PLATE = noise(["#4a6a70", "#567a80", "#628890", "#6e969c"])
    m.bone("root", [0, 0, 0])
    m.bone("head", [0, 4, -8], "root")
    m.cube("head", [-4, 1, -12], [8, 5, 5], PLATE, art={"north": A.at(8, 5, {(1, 1): "r", (2, 1): "r", (1, 2): "r", (5, 1): "r", (6, 1): "r", (6, 2): "r"}, r="#4a1418")})
    for sx in (-1, 1):
        m.cube("head", [sx * 2 - 0.5, 0, -14], [1, 1, 3], PLATE, rot=[20, sx * 20, 0], pivot=[sx * 2, 1, -12])
    prev, z = "head", -8
    segs, legs = [], []
    for i in range(5):
        b = f"seg{i}"
        m.bone(b, [0, 4, z], prev)
        w = 8 if i < 4 else 7
        glow_up = {(w // 2, 2): "g", (w // 2 - 1, 2): "g"} if i % 2 else {}
        m.cube(b, [-w / 2, 1, z], [w, 6, 5], PLATE,
               art={"east": A.at(5, 6, {(2, 2): "g", (2, 3): "g", (3, 2): "G"}, g="!" + CYAN, G="!" + CYAN_HI),
                    "west": A.at(5, 6, {(2, 2): "g", (2, 3): "g", (1, 2): "G"}, g="!" + CYAN, G="!" + CYAN_HI),
                    "up": A.at(w, 5, glow_up, g="!" + CYAN)})
        m.cube(b, [-w / 2 - 0.5, 6, z + 4], [w + 1, 1, 1], noise(["#3a5a60", "#4a6a70"]))
        r = legpair(m, b, -3, 1.5, z + 2.5, -1, 2, 3, PLATE, f"leg{i}r", splay=10, knee=80, thick=1)
        l = legpair(m, b, 3, 1.5, z + 2.5, 1, 2, 3, PLATE, f"leg{i}l", splay=10, knee=80, thick=1)
        legs.append((r[0], l[0]))
        segs.append(b)
        prev, z = b, z + 5
    m.cube(prev, [-3, 1, z], [6, 3, 3], PLATE)
    for sx in (-1, 1):
        m.cube(prev, [sx * 2 - 0.5, 2, z + 2], [1, 1, 4], PLATE, rot=[0, sx * 30, 0], pivot=[sx * 2, 2, z + 2])
    idle = {b: {"rotation": wave(3.0, lambda q, i=i: [S(q - i) * 1.5, S(q - i * 0.7) * 2, 0])} for i, b in enumerate(segs)}
    anim(m, "idle", 3.0, idle)
    mv = {b: {"rotation": wave(0.8, lambda q, i=i: [0, S(q - i * 0.9) * 5, 0])} for i, b in enumerate(segs)}
    mv.update(walk_legs(legs, 0.4, 20))
    anim(m, "move", 0.8, mv)
    anim(m, "attack", 0.5, {"head": {"rotation": keys((0, [0, 0, 0]), (0.15, [-20, 0, 0]), (0.3, [12, 0, 0]), (0.5, [0, 0, 0]))}}, loop=False)
    # special: curl into a ball
    anim(m, "special", 1.4, {**{b: {"rotation": keys((0, [0, 0, 0]), (0.4, [-38, 0, 0]), (1.0, [-38, 0, 0]), (1.4, [0, 0, 0]))} for b in segs},
                             "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [40, 0, 0]), (1.0, [40, 0, 0]), (1.4, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 1.0, {"root": {"scale": keys((0, [1, 1, 1]), (0.3, [1.1, 1.1, 1.1]), (0.6, [1, 1, 1]), (1.0, [1, 1, 1]))}}, loop=False)
    return m


# =====================================================================================
# DEEP LURKER - crouching mossy horror with a bone muzzle
# =====================================================================================
def deep_lurker(seed):
    m = Rig("deep_lurker", 64, 64, seed)
    MOSS = over(noise(["#12281a", "#183222", "#1e3c28", "#26482e"]), spots("#5a7a2a", 0.08), spots("#8a9a3a", 0.03))
    BONE = noise(["#b8b09a", "#c8c0a8", "#d4ccb4"])
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 8, 2], "root", rotation=[-25, 0, 0])
    m.cube("body", [-5, 5, -4], [10, 8, 12], MOSS)
    for z in range(-2, 8, 3):
        m.cube("body", [-0.5, 13, z], [1, 3, 2], BONE, rot=[-30, 0, 0], pivot=[0, 13, z + 1])
    m.bone("head", [0, 12, -4], "body")
    eyes = {(1, 2): "E", (2, 2): "e", (1, 3): "e", (2, 3): "e", (5, 2): "e", (6, 2): "E", (5, 3): "e", (6, 3): "e"}
    m.cube("head", [-4, 9, -11], [8, 7, 7], MOSS, art={"north": A.at(8, 7, eyes, E="!#aafff0", e="!#3fe0d0")})
    # bone muzzle with upper fangs, lower jaw
    m.cube("head", [-3.5, 8, -15], [7, 3, 5], BONE)
    for x in (-3, -1, 1, 2.5):
        m.cube("head", [x, 6, -15], [1, 2, 1], BONE)
    m.bone("jaw", [0, 8, -10], "head", rotation=[15, 0, 0])
    m.cube("jaw", [-3, 6, -15], [6, 2, 5], BONE)
    for x in (-2.5, 1.5):
        m.cube("jaw", [x, 8, -15], [1, 2, 1], BONE)
    # forelimbs (bony claws gripping) and haunches
    for side, sx in (("rarm", -1), ("larm", 1)):
        m.bone(side, [sx * 5, 9, -6], "root")
        x0 = -8 if sx < 0 else 5
        m.cube(side, [x0, 2, -8], [3, 8, 4], MOSS)
        m.cube(side, [x0 - 0.5, 0, -10], [4, 2, 5], MOSS)
        for dx in (0, 1.5, 3):
            m.cube(side, [x0 + dx - 0.5, 0, -12], [1, 2, 3], BONE, rot=[30, 0, 0], pivot=[x0 + dx, 1, -10])
    for side, sx in (("rleg", -1), ("lleg", 1)):
        m.bone(side, [sx * 5, 6, 6], "root")
        x0 = -9 if sx < 0 else 5
        m.cube(side, [x0, 2, 2], [4, 7, 7], MOSS)
        m.cube(side, [x0, 0, -1], [4, 2, 5], MOSS)
    m.bone("tail", [0, 6, 9], "root", rotation=[30, 0, 0])
    m.cube("tail", [-2, 3, 9], [4, 4, 7], MOSS)
    m.bone("tail2", [0, 5, 16], "tail", rotation=[30, 0, 0])
    m.cube("tail2", [-1.5, 3.5, 16], [3, 3, 6], MOSS)
    anim(m, "idle", 3.0, {"body": {"rotation": wave(3.0, lambda q: [S(q) * 2, 0, 0])},
                          "head": {"rotation": wave(3.0, lambda q: [S(q) * 3, S(q * 0.5) * 12, 0])},
                          "jaw": {"rotation": wave(3.0, lambda q: [max(0, S(q)) * 6, 0, 0])},
                          "tail": {"rotation": wave(3.0, lambda q: [0, S(q) * 10, 0])}})
    anim(m, "move", 0.8, {"rarm": {"rotation": wave(0.8, lambda q: [S(q) * 30, 0, 0])},
                          "larm": {"rotation": wave(0.8, lambda q: [-S(q) * 30, 0, 0])},
                          "rleg": {"rotation": wave(0.8, lambda q: [-S(q) * 25, 0, 0])},
                          "lleg": {"rotation": wave(0.8, lambda q: [S(q) * 25, 0, 0])},
                          "body": {"position": wave(0.8, lambda q: [0, abs(S(q)) * 0.8, 0])},
                          "tail": {"rotation": wave(0.8, lambda q: [0, S(q) * 18, 0])}})
    anim(m, "attack", 0.5, {"jaw": {"rotation": keys((0, [0, 0, 0]), (0.15, [35, 0, 0]), (0.3, [-10, 0, 0]), (0.5, [0, 0, 0]))},
                            "head": {"rotation": keys((0, [0, 0, 0]), (0.15, [-20, 0, 0]), (0.3, [15, 0, 0]), (0.5, [0, 0, 0]))},
                            "rarm": {"rotation": keys((0, [0, 0, 0]), (0.15, [-70, 0, 0]), (0.3, [10, 0, 0]), (0.5, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.0, {"jaw": {"rotation": keys((0, [0, 0, 0]), (0.3, [40, 0, 0]), (0.8, [40, 0, 0]), (1.0, [0, 0, 0]))},
                             "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-30, 0, 0]), (0.8, [-30, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 0.8, {"root": {"position": keys((0, [0, 0, 0]), (0.2, [0, -2, 2]), (0.45, [0, 6, -10]), (0.8, [0, 0, 0]))},
                              "rarm": {"rotation": keys((0, [0, 0, 0]), (0.45, [-80, 0, 0]), (0.8, [0, 0, 0]))},
                              "larm": {"rotation": keys((0, [0, 0, 0]), (0.45, [-80, 0, 0]), (0.8, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# CORRUPTED DIVER
# =====================================================================================
def coral_growth(m, bone, x, y, z, color="#a83a7a", glow=False, flip=1):
    mat = lit(color) if glow else noise([color, "#8a2a62", "#c04a8e"])
    m.cube(bone, [x, y, z], [2, 3, 2], mat)
    m.cube(bone, [x + flip, y + 2, z], [1, 3, 1], mat)
    m.cube(bone, [x - flip, y + 3, z + 1], [1, 2, 1], mat)
    m.cube(bone, [x + 2 * flip, y + 1, z + 1], [1, 2, 1], mat)


def corrupted_diver(seed):
    m = Rig("corrupted_diver", 64, 64, seed)
    HELM = noise(["#1e3a3e", "#26464a", "#2e5256", "#365e62"])
    SUIT = noise(["#14282c", "#1a3236", "#203c40"])
    visor = {(x, y): "v" for x in range(1, 7) for y in range(2, 6)}
    visor.update({(1, 3): "e", (2, 3): "E", (5, 3): "E", (6, 3): "e"})
    visor.update({(3, y): "v" for y in range(6, 8)}); visor.update({(4, y): "v" for y in range(6, 8)})
    a = biped(m, head=over(HELM, spots("#5a7a7e", 0.05)), body=over(SUIT, spots("#2a4a4e", 0.08)),
              arm=noise(["#1a1416", "#221a1c", "#2a2024"]), leg=bands(noise(["#1e1618", "#2a1e20", "#34262a"]), (10, 12, noise(["#1e3a3e", "#26464a"]))),
              art={"head": {"north": A.at(8, 8, visor, v="#081012", e="!#6fe8ff", E="!#caffff")}})
    t = a["top"]
    m.cube("head", [-4.5, t + 7, -4.5], [9, 2, 9], HELM, art={"up": A.at(9, 9, {(2, 2): "b", (6, 3): "b", (4, 6): "b"}, b="#5a7a7e")})
    m.cube("rarm", [-9, t - 5, -3], [6, 6, 6], noise(["#16181a", "#1e2022", "#26282a"]),
           art={"sides": A.at(6, 6, {(1, 1): "r", (4, 2): "r", (2, 4): "r"}, r="#9aa0a8")})     # riveted gauntlet
    m.cube("rarm", [-8.6, t - 13, -2.6], [5, 5, 5], noise(["#16181a", "#1e2022"]))
    coral_growth(m, "body", -1, t - 7, -3, flip=1)
    coral_growth(m, "larm", 5, t - 1, -1, flip=-1)
    coral_growth(m, "lleg", 1, 5, -3, flip=1)
    # rusted mace in the left hand
    lx, hy = a["lx"], a["hand_y"]
    pv = [lx, hy, 0]
    m.cube("lhand", [lx - 0.5, hy - 1, -8], [1, 1, 9], noise(["#2a2016", "#3a2c1e"]), rot=[-50, 0, 0], pivot=pv)
    m.cube("lhand", [lx - 1.5, hy - 2, -12], [3, 3, 4], noise(["#4a4e56", "#5a5e66", "#6a6e76"]), rot=[-50, 0, 0], pivot=pv)
    biped_anims(m, "heavy")
    return m


# =====================================================================================
# ABYSS EEL
# =====================================================================================
def abyss_eel(seed):
    m = Rig("abyss_eel", 128, 128, seed)
    SK = ["#1a1018", "#221420", "#2a1828", "#321e30"]
    BEL = ["#1e6a5e", "#2a8474", "#36a08a"]
    sizes = [7, 7, 7, 6, 6, 6, 5, 5]
    curl = [0] * 8
    names = serpent_body(m, segs=8, sizes=sizes, y=5, skin=SK, belly=BEL, spine=None, spine_h=lambda i: 0, head_w=8, head_len=11,
                         eye="#3fe0c0", curl_y=curl, mouth="#6a1420", teeth="#d8c8c8")
    for i, nm in enumerate(names):          # glowing belly scutes
        s = sizes[i]
        c = next(c for c in m.cubes if c.bone == nm)
        m.cube(nm, [-s / 2 + 0.5, 5 - s / 2 - 0.2, c.origin[2] + 1], [s - 1, 1, s - 1], lit("#2ac8a8"))
    serpent_anims(m, names, amp=12, speed=0.8)
    return m


# =====================================================================================
# CRYSTAL GOLEM
# =====================================================================================
def crystal_golem(seed):
    m = Rig("crystal_golem", 128, 128, seed)
    BLK = noise(["#0c0e10", "#121416", "#181a1c", "#1e2022"])
    xcr = lambda w, h, cx, cy: A.at(w, h, {(cx + d * sx, cy + d * sy): ("C" if d == 0 else "c") for d in range(0, 3) for sx in (-1, 1) for sy in (-1, 1)
                                          if 0 <= cx + d * sx < w and 0 <= cy + d * sy < h}, c="!#2ab8a8", C="!#8affe8")
    m.bone("root", [0, 0, 0])
    for side, sx in (("rleg", -1), ("lleg", 1)):
        m.bone(side, [sx * 3.5, 11, 0], "root")
        x0 = -7 if sx < 0 else 0
        m.cube(side, [x0, 2, -3], [7, 9, 6], BLK)
        m.cube(side, [x0 - 0.5, 0, -4], [8, 3, 7], BLK)
    m.bone("body", [0, 11, 0], "root")
    m.cube("body", [-6, 11, -4], [12, 7, 8], BLK, art={"north": xcr(12, 7, 6, 3)})
    m.cube("body", [-9, 18, -5], [18, 10, 10], BLK, art={"north": xcr(18, 10, 5, 4)})
    m.bone("head", [0, 28, -1], "body")
    eye = {(x, y): "e" for x in (3, 4, 5) for y in (2, 3, 4)}; eye[(4, 3)] = "E"
    m.cube("head", [-4, 26, -6], [8, 9, 8], BLK, art={"north": A.at(8, 9, eye, e="!#2ab8a8", E="!#aaffee")})
    # crystal cluster on the left shoulder
    for (x, z, h) in ((10, -2, 5), (12, 0, 7), (14, -1, 4)):
        m.cube("body", [x - 13, 28, z], [2, h, 2], over(noise(["#1a6a5a", "#248474", "#2ea08a"]), spots("#8affe8", 0.2, glow=True)))
    for side, sx in (("rarm", -1), ("larm", 1)):
        m.bone(side, [sx * 11, 26, 0], "body")
        ax = -16 if sx < 0 else 9
        m.cube(side, [ax, 20, -4], [7, 8, 8], BLK, art={"north": xcr(7, 8, 3, 4)} if sx > 0 else None)
        m.bone(side + "_fist", [sx * 12.5, 20, 0], side)
        m.cube(side + "_fist", [ax, 5, -4.5], [7, 15, 9], BLK, art={"north": xcr(7, 15, 3, 6)} if sx < 0 else None)
        m.cube(side + "_fist", [ax, 5, -5.5], [2, 5, 1], BLK)                         # claw-fingers
        m.cube(side + "_fist", [ax + 5, 5, -5.5], [2, 5, 1], BLK)
    anim(m, "idle", 3.0, {"body": {"rotation": wave(3.0, lambda q: [S(q) * 1.5, 0, 0])},
                          "head": {"rotation": wave(3.0, lambda q: [S(q) * 2, S(q * 0.5) * 8, 0])},
                          "rarm": {"rotation": wave(3.0, lambda q: [S(q) * 3, 0, 3])},
                          "larm": {"rotation": wave(3.0, lambda q: [-S(q) * 3, 0, -3])}})
    anim(m, "move", 1.5, {"rleg": {"rotation": wave(1.5, lambda q: [-S(q) * 22, 0, 0])},
                          "lleg": {"rotation": wave(1.5, lambda q: [S(q) * 22, 0, 0])},
                          "rarm": {"rotation": wave(1.5, lambda q: [S(q) * 16, 0, 3])},
                          "larm": {"rotation": wave(1.5, lambda q: [-S(q) * 16, 0, -3])},
                          "body": {"rotation": wave(1.5, lambda q: [3, S(q) * 3, 0]), "position": wave(1.5, lambda q: [0, -abs(S(q)) * 1.0, 0])}})
    anim(m, "attack", 0.8, {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-120, 10, 10]), (0.45, [15, 0, 0]), (0.8, [0, 0, 0]))},
                            "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-5, 20, 0]), (0.45, [10, -16, 0]), (0.8, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.6, {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-165, 0, 10]), (0.65, [-165, 0, 10]), (0.85, [25, 0, 0]), (1.2, [25, 0, 0]), (1.6, [0, 0, 0]))},
                             "larm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-165, 0, -10]), (0.65, [-165, 0, -10]), (0.85, [25, 0, 0]), (1.2, [25, 0, 0]), (1.6, [0, 0, 0]))},
                             "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [-10, 0, 0]), (0.85, [22, 0, 0]), (1.2, [22, 0, 0]), (1.6, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 1.0, {"body": {"rotation": keys((0, [0, 0, 0]), (0.35, [0, 30, 0]), (0.6, [0, -30, 0]), (1.0, [0, 0, 0]))},
                              "rarm": {"rotation": keys((0, [0, 0, 0]), (0.35, [-90, 0, 40]), (0.6, [-90, 0, -20]), (1.0, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# DROWNED SAILOR
# =====================================================================================
def drowned_sailor(seed):
    m = Rig("drowned_sailor", 64, 64, seed)
    SKIN = noise(["#1e5a5e", "#26666a", "#2e7276", "#367e80"])
    CLOTH = over(noise(["#18484c", "#1e5256", "#245c60"]), spots("#122e30", 0.06))
    face = {(1, 2): "e", (2, 2): "E", (1, 3): "e", (2, 3): "e", (5, 2): "E", (6, 2): "e", (5, 3): "e", (6, 3): "e",
            (3, 5): "k", (4, 5): "k", **{(x, 0): "b" for x in range(8)}, **{(x, 1): "b" for x in range(8)}}
    a = biped(m, head=SKIN, body=CLOTH, arm=bands(CLOTH, (9, 12, SKIN)), leg=bands(noise(["#101e2a", "#162634", "#1c2e3e"]), (7, 9, noise(["#8a9a8a", "#a8b8a8"]))),
              art={"head": {"north": A.at(8, 8, face, e="!#3fe0f0", E="!#caffff", k="#0a1a1c", b="#0e2224")},
                   "body": {"north": A.at(8, 12, {**{(x, 7): "L" for x in range(8)}, **{(x, 8): "L" for x in range(8)}, (3, 7): "g", (4, 8): "g",
                                                  (4, 2): "k", (3, 3): "k", (4, 4): "k"}, L="#1a1614", g="#8a9a8a", k="#0e2224")}})
    t = a["top"]
    m.cube("head", [-4.5, t + 5, -4.5], [9, 2, 9], noise(["#0e2224", "#142a2c"]))          # headband
    m.cube("head", [3.5, t + 3, 1], [2, 3, 1], noise(["#3a3230", "#4a403c"]), rot=[0, 30, 0])  # bandana knot
    m.cube("larm", [4.5, t - 6, -3], [4, 4, 1], noise(["#c8c0b0", "#d8d0c0", "#b8b0a0"]),
           art={"north": A.at(4, 4, {(1, 1): "d", (2, 2): "d", (1, 2): "d"}, d="#8a8274")})     # barnacle shell
    # ghostly glowing boat hook in the right hand
    rx, hy = a["rx"], a["hand_y"]
    pv = [rx, hy, 0]
    m.cube("rhand", [rx - 0.5, hy - 1, -10], [1, 1, 11], lit("#8ae0d8"), rot=[-45, 0, 0], pivot=pv)
    m.cube("rhand", [rx - 0.5, hy - 1, -13], [1, 4, 1], lit("#aaf0e8"), rot=[-45, 0, 0], pivot=pv)
    m.cube("rhand", [rx - 0.5, hy + 2, -13], [1, 1, 3], lit("#aaf0e8"), rot=[-45, 0, 0], pivot=pv)
    biped_anims(m, "sword", hold=[-15, 0, 0])
    return m


# =====================================================================================
# JELLY SKULL
# =====================================================================================
def jelly_skull(seed):
    m = Rig("jelly_skull", 64, 64, seed)
    JEL = over(noise(["#5a2a3a", "#7a3a4a", "#8a4454", "#a8546a"]), spots("#c86a7a", 0.1))
    SKF = {}
    for x in range(1, 9):
        for y in range(1, 9):
            if (x in (1, 8) and 2 <= y <= 6) or (y == 1 and 2 <= x <= 7) or (y in (6, 7) and 3 <= x <= 6):
                SKF[(x, y)] = "s"
    for (x, y) in ((2, 3), (3, 3), (2, 4), (3, 4), (6, 3), (7, 3), (6, 4), (7, 4)):
        SKF[(x, y)] = "k"
    SKF.update({(4, 2): "s", (5, 2): "s", (4, 5): "k", (5, 5): "k", (4, 6): "k", (5, 7): "k", (4, 8): "s", (5, 8): "s"})
    m.bone("root", [0, 12, 0])
    m.bone("head", [0, 12, 0], "root")
    m.cube("head", [-5, 11, -5], [10, 10, 10], JEL, art={"north": A.at(10, 10, SKF, s="!#d8d8a8", k="#1a0a10"),
                                                           "east": A.at(10, 10, {(3, 3): "s", (4, 3): "s", (4, 4): "s", (3, 5): "s"}, s="!#c8c898")})
    m.cube("head", [-4, 21, -4], [8, 1, 8], lit("#e8e8b8"))                             # glowing crown
    m.cube("head", [-5.5, 20, -5.5], [11, 1, 11], noise(["#3a1a2a", "#4a2234"]))
    tents = []
    for i, (x, z) in enumerate(((-3, -2), (1, -3), (3, 1), (-2, 2), (0, 0))):
        b = f"t{i}"
        L = 11 + (i % 3) * 2
        m.bone(b, [x, 11, z], "head")
        m.cube(b, [x - 1, 11 - L, z - 1], [2, L, 2], over(noise(["#2a1624", "#3a1e30", "#5a2a3e"]), spots("#8a3a4a", 0.12)))
        tents.append(b)
    idle = {b: {"rotation": wave(2.4, lambda q, i=i: [S(q + i) * 6, 0, S(q * 0.7 + i) * 4])} for i, b in enumerate(tents)}
    idle["root"] = {"position": wave(2.4, lambda q: [0, S(q) * 1.2, 0])}
    idle["head"] = {"scale": wave(2.4, lambda q: [1 + 0.04 * S(q), 1 - 0.04 * S(q), 1 + 0.04 * S(q)])}
    anim(m, "idle", 2.4, idle)
    mv = {b: {"rotation": wave(1.0, lambda q, i=i: [15 + S(q + i) * 12, 0, 0])} for i, b in enumerate(tents)}
    mv["head"] = {"scale": wave(1.0, lambda q: [1 + 0.08 * S(q), 1 - 0.08 * S(q), 1 + 0.08 * S(q)])}
    anim(m, "move", 1.0, mv)
    anim(m, "attack", 0.6, {b: {"rotation": keys((0, [0, 0, 0]), (0.2, [-50, 0, 0]), (0.35, [30, 0, 0]), (0.6, [0, 0, 0]))} for b in tents}, loop=False)
    anim(m, "special", 1.0, {"head": {"scale": keys((0, [1, 1, 1]), (0.3, [1.3, 1.3, 1.3]), (0.6, [0.9, 0.9, 0.9]), (1.0, [1, 1, 1]))}}, loop=False)
    anim(m, "special2", 1.0, {"head": {"scale": keys((0, [1, 1, 1]), (0.4, [1.2, 0.8, 1.2]), (1.0, [1, 1, 1]))},
                              **{b: {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 0, 40 * (1 if i % 2 else -1)]), (1.0, [0, 0, 0]))} for i, b in enumerate(tents)}}, loop=False)
    return m


# =====================================================================================
# ANEMONE EYE
# =====================================================================================
def anemone_eye(seed):
    m = Rig("anemone_eye", 64, 64, seed)
    STONE = over(noise(["#3a3028", "#4a3e34", "#56483c", "#62544a"]), speckle("#2a221c", 0.08))
    RED = over(noise(["#7a1a2a", "#8a2232", "#9a2a3c", "#aa3448"]), spots("#c84a5a", 0.08))
    m.bone("root", [0, 0, 0])
    m.cube("root", [-5, 0, -3], [10, 6, 6], STONE)
    fingers = []
    for i, (ang, L) in enumerate(((-38, 15), (-14, 20), (10, 18), (32, 15), (52, 11))):
        b = f"p{i}"
        x = -4 + i * 2
        m.bone(b, [x, 6, 1], "root", rotation=[4, 0, -ang])
        m.cube(b, [x - 1.5, 6, -1.5], [3, L, 3], RED)
        m.cube(b, [x - 1, 6 + L - 3, -2], [2, 2, 1], lit("#e05a6a"))
        fingers.append(b)
    # the eye: layered flower disc with a red cross pupil
    m.bone("eye", [0, 9, -2], "root")
    m.cube("eye", [-3.5, 5, -3.5], [7, 7, 1], noise(["#d89aa0", "#e0a8ac"]),
           art={"north": A.at(8, 8, {**{(x, y): "p" for x in range(1, 7) for y in range(1, 7)},
                                     **{(x, y): "w" for x in range(2, 6) for y in range(2, 6)},
                                     (3, 2): "r", (4, 2): "r", (3, 3): "r", (4, 3): "R", (3, 4): "R", (4, 4): "r", (2, 3): "r", (5, 3): "r",
                                     (2, 4): "r", (5, 4): "r", (3, 5): "r", (4, 5): "r",
                                     (0, 0): "_", (7, 0): "_", (0, 7): "_", (7, 7): "_"}, p="!#f0c0c4", w="!#fae4e4", r="!#c02030", R="!#ff4050")})
    m.cube("eye", [-0.5, 3, -3], [1, 2, 1], RED)
    idle = {b: {"rotation": wave(3.0, lambda q, i=i: [S(q + i) * 5, 0, S(q * 0.7 + i) * 6])} for i, b in enumerate(fingers)}
    idle["eye"] = {"rotation": wave(4.0, lambda q: [S(q) * 6, S(q * 0.5) * 18, 0])}
    anim(m, "idle", 3.0, idle)
    anim(m, "move", 1.5, {b: {"rotation": wave(1.5, lambda q, i=i: [S(q + i) * 12, 0, S(q + i) * 10])} for i, b in enumerate(fingers)})
    anim(m, "attack", 0.6, {b: {"rotation": keys((0, [0, 0, 0]), (0.2, [25, 0, 0]), (0.35, [-50, 0, 0]), (0.6, [0, 0, 0]))} for b in fingers}, loop=False)
    anim(m, "special", 1.2, {"eye": {"scale": keys((0, [1, 1, 1]), (0.3, [1.4, 1.4, 1.4]), (0.9, [1.4, 1.4, 1.4]), (1.2, [1, 1, 1]))},
                             **{b: {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, (-25 if i < 2 else 25)]), (1.2, [0, 0, 0]))} for i, b in enumerate(fingers)}}, loop=False)
    anim(m, "special2", 1.0, {b: {"rotation": keys((0, [0, 0, 0]), (0.4, [-35, 0, 0]), (1.0, [0, 0, 0]))} for b in fingers}, loop=False)
    return m


# =====================================================================================
# VOID MANTA
# =====================================================================================
def void_manta(seed):
    m = Rig("void_manta", 128, 128, seed)
    DK = noise(["#0a1014", "#0e161c", "#121c22", "#162228"])
    bars = lambda w, h: A.at(w, h, {(x, y): ("G" if x in (3, 8) else "g") for (x0, y) in ((2, 3), (7, 5), (12, 7)) for x in range(x0, x0 + 5)
                                    if x < w and y < h}, g="!#6fe8f0", G="!#caffff")
    m.bone("root", [0, 3, 0])
    m.bone("body", [0, 3, 0], "root")
    m.cube("body", [-5, 1, -10], [10, 4, 20], DK)
    face = {(0, 0): "e", (7, 0): "e", **{(x, y): "k" for x in range(2, 6) for y in (1, 2, 3)}, (2, 1): "t", (4, 1): "t", (3, 3): "t", (5, 3): "t"}
    m.cube("body", [-4, 0, -14], [8, 5, 4], DK, art={"north": A.at(8, 5, face, e="!#6fe8f0", k="#030507", t="#d8d8d8")})
    for sx in (-1, 1):
        m.cube("body", [sx * 3 - 1, -4, -14], [2, 5, 2], DK, rot=[25, 0, 0], pivot=[sx * 3, 0, -13])   # cephalic lobes
    for side, sx in (("wing_r", -1), ("wing_l", 1)):
        m.bone(side, [sx * 5, 3, -2], "body")
        m.bone(side + "2", [sx * 17, 3, -2], side)
    m.cube("wing_r", [-17, 2.5, -8], [12, 1, 14], DK, art={"up": bars(12, 14)})
    m.mirror("wing_l", [-17, 2.5, -8], [12, 1, 14], DK, art={"up": bars(12, 14)})
    m.cube("wing_r2", [-26, 2.5, -4], [9, 1, 9], DK, rot=[0, 0, 25], pivot=[-17, 3, -2], art={"up": A.at(9, 9, {(x, 4): "g" for x in range(1, 5)}, g="!#6fe8f0")})
    m.mirror("wing_l2", [-26, 2.5, -4], [9, 1, 9], DK, rot=[0, 0, 25], pivot=[-17, 3, -2], art={"up": A.at(9, 9, {(x, 4): "g" for x in range(1, 5)}, g="!#6fe8f0")})
    m.bone("tail", [0, 3, 10], "body")
    m.cube("tail", [-0.5, 2.5, 10], [1, 1, 22], DK, rot=[-8, 0, 0], pivot=[0, 3, 10])
    m.cube("tail", [-1, 2, 18], [2, 2, 2], DK)

    def flap(length, a):
        return {"wing_r": {"rotation": wave(length, lambda q: [0, 0, S(q) * 20 * a])},
                "wing_l": {"rotation": wave(length, lambda q: [0, 0, -S(q) * 20 * a])},
                "wing_r2": {"rotation": wave(length, lambda q: [0, 0, S(q - 0.7) * 22 * a])},
                "wing_l2": {"rotation": wave(length, lambda q: [0, 0, -S(q - 0.7) * 22 * a])},
                "tail": {"rotation": wave(length, lambda q: [S(q) * 6, S(q * 0.5) * 8, 0])},
                "root": {"position": wave(length, lambda q: [0, -S(q) * 0.8 * a, 0])}}
    anim(m, "idle", 3.0, flap(3.0, 0.5))
    anim(m, "move", 1.4, flap(1.4, 1.0))
    anim(m, "attack", 0.6, {"body": {"rotation": keys((0, [0, 0, 0]), (0.2, [-15, 0, 0]), (0.35, [20, 0, 0]), (0.6, [0, 0, 0]))},
                            "root": {"position": keys((0, [0, 0, 0]), (0.2, [0, 2, 2]), (0.35, [0, -2, -6]), (0.6, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.2, {"body": {"rotation": keys((0, [0, 0, 0]), (0.6, [0, 0, 180]), (1.2, [0, 0, 360]))}}, loop=False)
    anim(m, "special2", 0.8, {"wing_r": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, -40]), (0.5, [0, 0, 35]), (0.8, [0, 0, 0]))},
                              "wing_l": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 40]), (0.5, [0, 0, -35]), (0.8, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# LANTERN SQUID
# =====================================================================================
def lantern_squid(seed):
    m = Rig("lantern_squid", 64, 64, seed)
    SH = over(noise(["#1a2226", "#222a2e", "#2a3236", "#323a3e"]), speckle("#3a4448", 0.05))
    face = {}
    for x in range(1, 9):
        for y in range(1, 10 if x not in (1, 8) else 8):
            face[(x, y)] = "b"
    for (cx, cy, big) in ((4, 2, False), (2, 4, True), (6, 5, True), (5, 7, False)):
        for dx in (-1, 0, 1) if big else (0,):
            for dy in (-1, 0, 1) if big else (0,):
                if big and abs(dx) + abs(dy) == 2:
                    continue
                face[(cx + dx, cy + dy)] = "g"
        face[(cx, cy)] = "G"
    for (x, y) in ((1, 1), (8, 3), (8, 6), (1, 8), (3, 9)):
        face[(x, y)] = "g"
    m.bone("root", [0, 12, 0])
    m.bone("head", [0, 12, 0], "root")
    m.cube("head", [-5, 12, -5], [10, 10, 10], SH, art={"north": A.at(10, 10, face, b="#2a1e16", g="!#4ad8c0", G="!#caffee")})
    tents = []
    for i, (x, z) in enumerate(((-3, -3), (0, -3.5), (3, -3), (-3, 2), (0, 2.5), (3, 2))):
        b = f"t{i}"
        m.bone(b, [x, 12, z], "head", rotation=[(8 if z < 0 else -8), 0, (x * 3)])
        L = 7
        m.cube(b, [x - 1, 12 - L, z - 1], [2, L, 2], SH)
        m.bone(b + "b", [x, 12 - L, z], b, rotation=[(-6 if z < 0 else 6), 0, 0])
        m.cube(b + "b", [x - 1, 12 - 2 * L, z - 1], [2, L, 2], SH)
        tents.append(b)
    idle = {b: {"rotation": wave(2.6, lambda q, i=i: [S(q + i) * 6, 0, S(q * 0.7 + i) * 4])} for i, b in enumerate(tents)}
    idle.update({b + "b": {"rotation": wave(2.6, lambda q, i=i: [S(q + i - 1) * 9, 0, 0])} for i, b in enumerate(tents)})
    idle["root"] = {"position": wave(2.6, lambda q: [0, S(q) * 1.2, 0])}
    anim(m, "idle", 2.6, idle)
    mv = {b: {"rotation": wave(1.0, lambda q, i=i: [(14 if i < 3 else -14) + S(q + i) * 10, 0, 0])} for i, b in enumerate(tents)}
    mv["head"] = {"rotation": wave(1.0, lambda q: [12, 0, S(q) * 3])}
    anim(m, "move", 1.0, mv)
    anim(m, "attack", 0.6, {b: {"rotation": keys((0, [0, 0, 0]), (0.2, [-40, 0, 0]), (0.35, [30, 0, 0]), (0.6, [0, 0, 0]))} for b in tents[:3]}, loop=False)
    anim(m, "special", 1.0, {"head": {"scale": keys((0, [1, 1, 1]), (0.25, [1.25, 1.25, 1.25]), (0.5, [0.95, 0.95, 0.95]), (1.0, [1, 1, 1]))}}, loop=False)
    anim(m, "special2", 1.0, {"root": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 180, 0]), (1.0, [0, 360, 0]))}}, loop=False)
    return m


# =====================================================================================
# ABYSSAL KING - boss
# =====================================================================================
def abyssal_king(seed):
    m = Rig("abyssal_king", 128, 128, seed)
    TEAL = over(noise(["#12383a", "#184446", "#1e5052", "#265c5e"]), spots("#3a8a86", 0.06))
    WEED = over(noise(["#3a2e1e", "#4a3a26", "#5a4630"]), spots("#6a5a3a", 0.1))
    face = {(3, 1): "E", (4, 1): "E", (3, 2): "E", (4, 2): "C", (2, 1): "e", (5, 1): "e", (2, 2): "e", (5, 2): "e",
            (3, 3): "k", (4, 3): "k", (2, 5): "k", (5, 5): "k", (3, 6): "k", (4, 6): "k"}
    core = {(x, y): "e" for x in (3, 4) for y in (2, 3)}; core.update({(3, 2): "C", (4, 2): "E", (3, 3): "E", (4, 3): "E"})
    a = biped(m, head=TEAL, body=TEAL, arm=TEAL, leg=bands(TEAL, (7, 9, WEED)), scale=1,
              art={"head": {"north": A.at(8, 8, face, e="!#2ab8a8", E="!#4ae8e0", C="!#caffff", k="#0a1a1a")},
                   "body": {"north": A.at(8, 12, {**core, **{(x, 9): "b" for x in range(8)}, **{(x, 10): "b" for x in range(8)}}, e="!#2ab8a8", E="!#4ae8e0", C="!#caffff", b="#2a2016")}})
    t = a["top"]
    # crown of teal and driftwood spikes
    m.cube("head", [-4.5, t + 7, -4.5], [9, 2, 9], TEAL)
    for (x, z, h, mat) in ((-4, -4.5, 4, TEAL), (-1.5, -4.5, 5, TEAL), (1.5, -4.5, 3, WEED), (3.5, -4.5, 4, TEAL), (-4, 3.5, 3, WEED),
                           (3.5, 3.5, 3, TEAL), (-4, -0.5, 3, TEAL), (3.5, -0.5, 4, WEED)):
        m.cube("head", [x, t + 9, z], [1, h, 1], mat)
    # seaweed pauldrons
    for sx, bone in ((-1, "rarm"), (1, "larm")):
        x0 = -9 if sx < 0 else 4
        m.cube(bone, [x0, t - 5, -3], [5, 5, 6], WEED)
        for (dx, dz, h) in ((0, -2, 4), (2, 0, 5), (4, 1, 3), (1, 2, 4)):
            m.cube(bone, [x0 + dx, t, dz - 0.5], [1, h, 1], WEED)
    m.cube("body", [-4.5, t - 15, -2.5], [9, 2, 5], noise(["#2a2016", "#3a2c1e"]))            # belt
    # spear in the right hand, kite shield on the left arm
    rx, hy = a["rx"], a["hand_y"]
    m.bone("spear", [rx, hy, 0], "rhand")
    m.cube("spear", [rx - 0.5, hy - 12, -1.5], [1, 34, 1], noise(["#2a2016", "#3a2c1e"]))
    m.cube("spear", [rx - 1.5, hy + 22, -2.5], [3, 5, 3], over(noise(["#2a8a86", "#3aa89e"]), spots("#aaffee", 0.2, glow=True)))
    m.cube("spear", [rx - 0.5, hy + 27, -1.5], [1, 3, 1], lit("#8affe8"))
    lx = a["lx"]
    kite = {}
    for y in range(16):
        half = 5 if y < 10 else max(0, 5 - (y - 9))
        for x in range(10):
            if abs(x - 4.5) > half:
                kite[(x, y)] = "_"
            elif abs(x - 4.5) > half - 1 or y == 0:
                kite[(x, y)] = "r"
    for y in range(3, 12):
        kite[(4, y)] = "g"; kite[(5, y)] = "g"
    m.bone("shield", [lx + 2, t - 8, 0], "larm")
    m.cube("shield", [lx + 2.5, t - 18, -5], [1, 16, 10], TEAL, art={"west": A.at(10, 16, kite, r="#4a9a94", g="!#6fe8d0"),
                                                                      "east": A.at(10, 16, {k: v for k, v in kite.items() if v == "_"})})
    biped_anims(m, "thrust", hold=[-20, 0, 0])
    m.anims["idle"]["bones"]["larm"] = {"rotation": wave(3.0, lambda q: [-20 + S(q) * 3, 0, -8])}
    return m


# =====================================================================================
# ABYSSAL HEART - boss; a pulsing heart-core caged in coral-grown pillars
# =====================================================================================
def sphere(m, bone, cx, cy, cz, r, mat, art_front=None):
    """Voxel sphere from stacked cross-shaped slabs (reads round from every side)."""
    for yy in range(-r, r + 1):
        rr = int(round(math.sqrt(max(0, r * r - yy * yy)) + 0.3))
        if rr <= 0:
            continue
        inner = max(1, int(round(rr * 0.72)))
        m.cube(bone, [cx - rr, cy + yy, cz - inner], [2 * rr, 1, 2 * inner], mat)
        m.cube(bone, [cx - inner, cy + yy, cz - rr], [2 * inner, 1, 2 * rr], mat)


def abyssal_heart(seed):
    m = Rig("abyssal_heart", 128, 128, seed)
    FLESH = over(noise(["#8a2a14", "#a8361a", "#c04420", "#d0562a"]), veins("#5a1a0e", 0.12, glow=False, length=(3, 8)),
                 veins("#ff9a3a", 0.03, glow=True))
    STONE = over(noise(["#0e1a1a", "#142222", "#1a2a2a", "#203232"]), speckle("#2a3e3e", 0.08))
    CORAL = noise(["#1e6a60", "#247a6e", "#2c8a7c"])
    m.bone("root", [0, 0, 0])
    m.cube("root", [-12, 0, -12], [24, 3, 24], STONE)
    # four cage pillars + low walls, coral growing on them, glowing gem nodes
    for (x, z) in ((-11, -11), (7, -11), (-11, 7), (7, 7)):
        m.cube("root", [x, 3, z], [4, 20, 4], STONE)
        coral_branch(m, "root", x + 1, 23, z + 1, "#247a6e", h=6)
    for (x, z, w, d) in ((-11, -11, 22, 2), (-11, 9, 22, 2), (-11, -11, 2, 22), (9, -11, 2, 22)):
        m.cube("root", [x, 3, z], [w, 5, d], STONE)
    for (x, y, z) in ((-12, 12, -3), (10, 9, 2), (-3, 6, -12), (4, 14, 10)):
        m.cube("root", [x, y, z], [2, 2, 2], lit("#3fe0c0"))
    for (x, z) in ((-9, -12), (5, -12), (-12, 4), (10, -4)):
        coral_branch(m, "root", x, 8, z, "#1e6a60", h=5)
    for (x, z) in ((-6, 11), (8, 11)):
        coral_branch(m, "root", x, 3, z, "#6a2a22", h=4)
    # the heart
    m.bone("heart", [0, 15, 0], "root")
    sphere(m, "heart", 0, 15, 0, 8, FLESH)
    m.cube("heart", [-2, 13, -8.2], [4, 4, 1], lit("#ffe07a"), art={"north": A.at(4, 4, {(1, 1): "W", (2, 1): "W", (1, 2): "W"}, W="!#fff8d0")})
    m.cube("heart", [-3, 12, -7.8], [6, 6, 1], lit("#ff9a2a"), art={"north": A.at(6, 6, {(0, 0): "_", (5, 0): "_", (0, 5): "_", (5, 5): "_"})})
    for (x, z) in ((-2, -2), (2, 1), (-1, 3)):
        m.cube("heart", [x, 23, z], [2, 3, 2], FLESH)                                      # severed vessels
    beat = lambda length: wave(length, lambda q: [1 + 0.07 * max(0, S(q)) ** 3, 1 + 0.07 * max(0, S(q)) ** 3, 1 + 0.07 * max(0, S(q)) ** 3])
    anim(m, "idle", 1.4, {"heart": {"scale": beat(1.4)}})
    anim(m, "move", 0.8, {"heart": {"scale": beat(0.8)}})
    anim(m, "attack", 0.7, {"heart": {"scale": keys((0, [1, 1, 1]), (0.2, [0.9, 0.9, 0.9]), (0.35, [1.2, 1.2, 1.2]), (0.7, [1, 1, 1]))}}, loop=False)
    anim(m, "special", 1.2, {"heart": {"scale": keys((0, [1, 1, 1]), (0.4, [1.3, 1.3, 1.3]), (0.8, [0.95, 0.95, 0.95]), (1.2, [1, 1, 1])),
                                       "rotation": keys((0, [0, 0, 0]), (0.4, [0, 20, 0]), (1.2, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 1.0, {"heart": {"scale": keys((0, [1, 1, 1]), (0.3, [1.15, 1.15, 1.15]), (0.5, [1.05, 1.05, 1.05]), (0.7, [1.2, 1.2, 1.2]), (1.0, [1, 1, 1]))}}, loop=False)
    return m


# =====================================================================================
# LEVIATHAN - boss; a colossal dark serpent with a maroon mane
# =====================================================================================
def leviathan(seed):
    m = Rig("leviathan", 128, 128, seed)
    SKIN = ["#15191c", "#1c2124", "#23292c", "#2a3034"]
    BELLY = ["#4a5054", "#586064", "#666e72"]
    MANE = ["#3a1420", "#4a1a28", "#5a2232"]
    sizes = [12, 12, 12, 11, 11, 10, 9, 8, 7, 6, 5, 4]
    curl = [0] * 12
    names = serpent_body(m, segs=12, sizes=sizes, y=9, skin=SKIN, belly=BELLY, spine=MANE, spine_h=lambda i: max(4, 10 - i),
                         head_w=13, head_len=18, eye="#f0f0e8", horns=MANE, curl_y=curl, mouth="#3a0e14", teeth="#e8e0cc")
    # mane frills along the neck sides
    for i, nm in enumerate(names[:4]):
        s = sizes[i]
        z0 = next(c for c in m.cubes if c.bone == nm).origin[2]
        m.pair(nm, nm, [-s / 2 - 1, 9, z0 + 1], [1, 4, s - 2], noise(MANE), rot=[0, 0, 35], pivot=[-s / 2, 10, z0 + s / 2])
    serpent_anims(m, names, amp=8, speed=1.5)
    return m


from mobs.abyssking import abyssal_king as _king, tide_warden  # noqa: E402  (rebuilt 2026-09-29, own module)

MOBS = {"abyssal_king": _king, "tide_warden": tide_warden, "abyssal_heart": abyssal_heart, "leviathan": leviathan,
        "abyss_crab": abyss_crab, "brain_fish": brain_fish, "abyssal_centipede": abyssal_centipede, "shadow": shadow,
        "coral_whale": coral_whale, "luminous_isopod": luminous_isopod, "deep_lurker": deep_lurker,
        "corrupted_diver": corrupted_diver, "abyss_eel": abyss_eel, "crystal_golem": crystal_golem,
        "drowned_sailor": drowned_sailor, "jelly_skull": jelly_skull, "anemone_eye": anemone_eye,
        "void_manta": void_manta, "lantern_squid": lantern_squid}

SKINS = {"abyssal_king": {"enraged": lambda seed: _king(seed, "enraged"), "stranded": lambda seed: _king(seed, "stranded")}}

# THE TITAN'S CHEST cast (boss 9/10 overhaul, 2026-09-30) - replaces the old abyssal_heart builder above
from mobs.heart import MOBS as _HEART_MOBS, SKINS as _HEART_SKINS  # noqa: E402
MOBS.update(_HEART_MOBS)
SKINS.update(_HEART_SKINS)

# THE LEVIATHAN (boss 10/10, 2026-09-30) - its head, body segments, tail per form + the fin
from mobs.leviathan import MOBS as _LEV_MOBS, SKINS as _LEV_SKINS  # noqa: E402
MOBS.update(_LEV_MOBS)
SKINS.update(_LEV_SKINS)
