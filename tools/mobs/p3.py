"""Phase 3 roster: molten_warlord, obsidian_golem, flame_sprite, ember_wraith, lava_scorpion, fire_pirate."""
from mobkit import A, Rig, bands, grad, lit, noise, over, solid, spots, veins, drips, speckle, anim, keys, wave, S
from mobs.common import biped, biped_anims

BASALT = ["#1a1716", "#221e1c", "#2a2523", "#332d2a"]
OBS = ["#0c0b10", "#131118", "#1a1720", "#221e2a"]
LAVA = "#ff7a1e"
LAVA_HOT = "#ffb03a"
EMBER_GRAD = [("#ffd23a",), ("#ff9a1e",), ("#e0521a",), ("#8a2412",)]


def lava_glow(top_hot=True):
    """Emissive gradient block (molten sword, burning arm): bright at one end."""
    def paint(face, w, h, rng):
        from mobkit import hexc, mix
        a, b = hexc("#ffd23a"), hexc("#c8401a")
        px, gl = [], []
        for y in range(h):
            t = y / max(1, h - 1) if top_hot else 1 - y / max(1, h - 1)
            row = []
            for x in range(w):
                c = mix(a, b, min(1, max(0, t + (rng.random() - 0.5) * 0.35)))
                row.append(c)
            px.append(row); gl.append([True] * w)
        return px, gl
    return paint


def fire_band(h_frac=0.5):
    """Dark stone that burns orange toward the bottom (cuffs, shins)."""
    def paint(face, w, h, rng):
        from mobkit import hexc, mix, mul
        from gen_mob_assets import FACE_LIGHT
        base, _ = noise(OBS)(face, w, h, rng)
        px = [list(r) for r in base]; gl = [[False] * w for _ in range(h)]
        if face in ("up", "down"):
            return px, gl
        for x in range(w):
            flame = int(h * h_frac) + rng.randint(-1, 1)
            for y in range(h - flame, h):
                t = (y - (h - flame)) / max(1, flame)
                c = mix(hexc("#8a2412"), hexc("#ffb03a"), t)
                if rng.random() < 0.8:
                    px[y][x] = c; gl[y][x] = True
        return px, gl
    return paint


# =====================================================================================
# MOLTEN WARLORD - boss
# =====================================================================================
def molten_warlord(seed):
    m = Rig("molten_warlord", 128, 128, seed)
    ROCK = over(noise(BASALT), veins(LAVA, 0.035), spots(LAVA_HOT, 0.01, glow=True))
    ROCK2 = over(noise(BASALT), veins(LAVA, 0.02))
    m.bone("root", [0, 0, 0])
    # legs + boots
    for side, sx in (("rleg", -1), ("lleg", 1)):
        m.bone(side, [sx * 4, 12, 0], "root")
        x0 = -8 if sx < 0 else 1
        m.cube(side, [x0, 2, -3], [7, 10, 6], ROCK2)
        m.cube(side, [x0 - 0.5, 0, -4.5], [8, 3, 8], ROCK2)
    m.bone("body", [0, 12, 0], "root")
    # waist + loincloth of molten drips
    m.cube("body", [-7, 12, -4], [14, 4, 8], ROCK2)
    m.cube("body", [-3, 5, -4.6], [6, 8, 1], over(noise(BASALT), drips(LAVA, 0.6, glow=True, maxlen=8)),
           art={"north": A.at(6, 8, {(2, 7): "_", (4, 6): "_", (4, 7): "_", (0, 7): "_"})})
    # chest: broad, lava vein chevrons
    chest = {}
    for i in range(6):
        chest[(2 + i, 3 + i // 2)] = "L"; chest[(15 - i, 3 + i // 2)] = "L"
    for y in range(6, 12):
        chest[(8, y)] = "L"; chest[(9, y)] = "L" if y % 2 else "H"
    m.cube("body", [-9, 16, -5], [18, 13, 10], ROCK, art={"north": A.at(18, 13, chest, L="!" + LAVA, H="!" + LAVA_HOT)})
    # head: helm-like block, glowing slit eyes + a molten line down the face, crown nubs
    m.bone("head", [0, 29, -2], "body")
    face = {(1, 3): "E", (2, 3): "E", (3, 4): "E", (6, 3): "E", (5, 3): "E", (4, 4): "E",
            (3, 5): "L", (4, 5): "L", (3, 6): "L", (4, 7): "L"}
    m.cube("head", [-4, 27, -8], [8, 9, 8], ROCK2, art={"north": A.at(8, 9, face, E="!#ffd23a", L="!" + LAVA)})
    m.cube("head", [-3, 36, -7], [1, 1, 1], lit(LAVA)); m.cube("head", [2, 36, -7], [1, 1, 1], lit(LAVA))
    # pauldrons + spikes
    for side, sx in (("rarm", -1), ("larm", 1)):
        m.bone(side, [sx * 11, 27, 0], "body")
        ax = -15 if sx < 0 else 9
        m.cube(side, [ax - 0.5, 22, -4.5], [7, 8, 9], ROCK)                    # pauldron
        m.cube(side, [ax + 2, 28, -1], [2, 8, 2], noise(["#141110", "#1c1816"]), rot=[0, 0, -sx * 25], pivot=[ax + 3, 29, 0],
               art={"up": A("L", L="!" + LAVA)})
        m.cube(side, [ax + 0.5, 13, -3], [5, 10, 6], ROCK2)                    # upper arm
        m.bone(side + "_fist", [sx * 12, 13, 0], side)
        m.cube(side + "_fist", [ax, 4, -3.5], [6, 9, 7], ROCK)                 # forearm + fist
        m.cube(side + "_fist", [ax - 0.5, 10, -4], [7, 2, 8], noise(["#2a2320", "#3a302a"]))   # bracer
    # molten greatsword in the right fist, pointing forward-up
    pv = [-12, 6, 0]
    m.bone("sword", pv, "rarm_fist")
    m.cube("sword", [-13, 4, -2], [2, 4, 3], noise(["#141010", "#241c18"]), rot=[-60, 0, 0], pivot=pv)
    m.cube("sword", [-14.5, 5, -5], [5, 2, 2], noise(["#2a2320", "#3a2c24"]), rot=[-60, 0, 0], pivot=pv)
    m.cube("sword", [-13.5, 5, -24], [3, 2, 19], lava_glow(False), rot=[-60, 0, 0], pivot=pv)
    # ball & chain hanging from the left fist
    m.bone("chain", [12, 4, 0], "larm_fist")
    CH = noise(["#2a2a2e", "#3a3a40", "#4a4a52"])
    m.cube("chain", [13, 3, -1], [2, 2, 1], CH, rot=[0, 0, -40], pivot=[13, 4, 0])       # links draping out of the fist
    m.cube("chain", [14.5, 1.5, -0.5], [1, 2, 2], CH, rot=[0, 0, -40], pivot=[15, 2.5, 0])
    m.cube("chain", [14, -1, -3], [6, 6, 6], ROCK2)                                         # the weight, resting on the ground
    biped_like = {"body": {"rotation": wave(3.0, lambda q: [S(q) * 1.5, 0, 0])},
                  "head": {"rotation": wave(3.0, lambda q: [S(q) * 2, S(q * 0.5) * 8, 0])},
                  "rarm": {"rotation": wave(3.0, lambda q: [-15 + S(q) * 3, 0, 4])},
                  "larm": {"rotation": wave(3.0, lambda q: [S(q) * 3, 0, -4])},
                  "chain": {"rotation": wave(3.0, lambda q: [S(q) * 8, 0, S(q * 0.5) * 5])}}
    anim(m, "idle", 3.0, biped_like)
    anim(m, "move", 1.4, {"rleg": {"rotation": wave(1.4, lambda q: [-S(q) * 24, 0, 0])},
                          "lleg": {"rotation": wave(1.4, lambda q: [S(q) * 24, 0, 0])},
                          "rarm": {"rotation": wave(1.4, lambda q: [-15 + S(q) * 14, 0, 4])},
                          "larm": {"rotation": wave(1.4, lambda q: [-S(q) * 14, 0, -4])},
                          "body": {"rotation": wave(1.4, lambda q: [4, S(q) * 4, 0]), "position": wave(1.4, lambda q: [0, -abs(S(q)) * 1.2, 0])},
                          "chain": {"rotation": wave(1.4, lambda q: [S(q - 1) * 18, 0, 0])}})
    anim(m, "attack", 0.8, {"rarm": {"rotation": keys((0, [-15, 0, 4]), (0.3, [-165, 15, 20]), (0.5, [-10, -15, 0]), (0.8, [-15, 0, 4]))},
                            "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-6, 20, 0]), (0.5, [12, -16, 0]), (0.8, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.6, {"rarm": {"rotation": keys((0, [-15, 0, 4]), (0.5, [-170, 0, 10]), (0.65, [-170, 0, 10]), (0.85, [20, 0, 0]), (1.2, [20, 0, 0]), (1.6, [-15, 0, 4]))},
                             "larm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-170, 0, -10]), (0.65, [-170, 0, -10]), (0.85, [20, 0, 0]), (1.2, [20, 0, 0]), (1.6, [0, 0, 0]))},
                             "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [-12, 0, 0]), (0.85, [24, 0, 0]), (1.2, [24, 0, 0]), (1.6, [0, 0, 0])),
                                      "position": keys((0, [0, 0, 0]), (0.5, [0, 2, 0]), (0.85, [0, -3, 0]), (1.6, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 1.0, {"larm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-60, -40, -80]), (0.55, [-80, 50, -20]), (1.0, [0, 0, 0]))},
                              "chain": {"rotation": keys((0, [0, 0, 0]), (0.3, [-90, 0, 0]), (0.55, [-150, 0, 0]), (1.0, [0, 0, 0]))},
                              "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, -25, 0]), (0.55, [0, 30, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# OBSIDIAN GOLEM - tall, huge pauldrons, burning forearms and ankles
# =====================================================================================
def obsidian_golem(seed):
    m = Rig("obsidian_golem", 128, 128, seed)
    OB = over(noise(OBS), speckle("#2e2838", 0.06))
    m.bone("root", [0, 0, 0])
    for side, sx in (("rleg", -1), ("lleg", 1)):
        m.bone(side, [sx * 3.5, 11, 0], "root")
        x0 = -7 if sx < 0 else 0
        m.cube(side, [x0, 3, -3], [7, 9, 6], fire_band(0.35))
        m.cube(side, [x0 - 0.5, 0, -4.5], [8, 3, 8], OB)
    m.bone("body", [0, 11, 0], "root")
    m.cube("body", [-5, 11, -3], [10, 6, 6], OB)                                  # waist
    crack = {(7, 1): "L", (7, 2): "L", (8, 3): "L", (7, 4): "L", (7, 5): "H", (8, 6): "L", (6, 5): "L", (9, 7): "L", (8, 8): "L"}
    m.cube("body", [-8, 17, -4.5], [16, 11, 9], OB, art={"north": A.at(16, 11, crack, L="!" + LAVA, H="!" + LAVA_HOT)})
    m.bone("head", [0, 28, -1], "body")
    eyes = {(2, 3): "e", (3, 3): "E", (2, 4): "e", (3, 4): "e", (5, 3): "E", (6, 3): "e", (5, 4): "e", (6, 4): "e"}
    m.cube("head", [-4, 26, -6], [8, 8, 8], OB, art={"north": A.at(8, 8, eyes, e="!#ff6a1a", E="!#ffd23a")})
    for side, sx in (("rarm", -1), ("larm", 1)):
        m.bone(side, [sx * 11, 26, 0], "body")
        ax = -16 if sx < 0 else 8
        m.cube(side, [ax, 22, -4.5], [8, 8, 9], OB)                                 # pauldron
        m.cube(side, [ax + 2, 15, -2], [4, 7, 4], over(noise(OBS), veins(LAVA, 0.03)))
        m.bone(side + "_fist", [sx * 12, 15, 0], side)
        m.cube(side + "_fist", [ax + 0.5, 9, -3.5], [7, 6, 7], fire_band(0.4))      # burning cuff
        m.cube(side + "_fist", [ax + 1, 3, -3], [6, 6, 6], OB)                       # fist, knuckles at shin height
    biped_like = {"body": {"rotation": wave(3.0, lambda q: [S(q) * 1.5, 0, 0])},
                  "head": {"rotation": wave(3.0, lambda q: [S(q) * 2, S(q * 0.5) * 8, 0])},
                  "rarm": {"rotation": wave(3.0, lambda q: [S(q) * 3, 0, 3])},
                  "larm": {"rotation": wave(3.0, lambda q: [-S(q) * 3, 0, -3])}}
    anim(m, "idle", 3.0, biped_like)
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
    anim(m, "special2", 1.0, {"body": {"rotation": keys((0, [0, 0, 0]), (0.35, [-18, 0, 0]), (0.6, [28, 0, 0]), (1.0, [0, 0, 0])),
                                       "position": keys((0, [0, 0, 0]), (0.35, [0, 0, 3]), (0.6, [0, -1, -6]), (1.0, [0, 0, 0]))},
                              "rarm": {"rotation": keys((0, [0, 0, 0]), (0.35, [30, 0, 0]), (0.6, [-80, 0, 0]), (1.0, [0, 0, 0]))},
                              "larm": {"rotation": keys((0, [0, 0, 0]), (0.35, [30, 0, 0]), (0.6, [-80, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# FLAME SPRITE - maroon ember cube wreathed in flame
# =====================================================================================
FLAME = """
_____OO_____
_____OO_____
____OYYO____
____OYYO__O_
_O_OYWWYO_O_
_OOOYWWYOOO_
OOYYWWWWYYOO
OYYWWWWWWYYO
OYWWWWWWWWYO
OOYWWWWWWYOO
_OOYYYYYYOO_
__OOOOOOOO__"""


def flame_sprite(seed):
    m = Rig("flame_sprite", 64, 64, seed)
    FP = dict(O="!#e8541a", Y="!#ff9a1e", W="!#ffd23a")
    m.bone("root", [0, 6, 0])
    m.bone("flame", [0, 8, 0], "root")
    # two crossed flame planes behind the head (cutout art)
    m.cube("flame", [-7, 6, 1], [14, 16, 0], lit("#ff9a1e"), art={"north": A(FLAME, FP), "south": A(FLAME, FP)})
    m.cube("flame", [0, 6, -6], [0, 14, 12], lit("#ff9a1e"), art={"east": A(FLAME, FP), "west": A(FLAME, FP)})
    m.bone("head", [0, 8, 0], "root")
    HEAD = over(noise(["#5a1210", "#6e1a14", "#7e2016", "#8a2a18"]), spots("#a83a1c", 0.05))
    eyes = {(1, 2): "Y", (2, 2): "W", (1, 3): "Y", (2, 3): "Y", (5, 2): "W", (6, 2): "Y", (5, 3): "Y", (6, 3): "Y",
            (3, 5): "d", (4, 5): "d", (3, 6): "d", (4, 6): "d"}
    m.cube("head", [-4, 6, -4], [8, 7, 8], HEAD, art={"north": A.at(8, 7, eyes, Y="!#ffd23a", W="!#fff4b0", d="#2a0808")})
    m.cube("head", [-2, 4, -3], [4, 2, 5], HEAD)                                   # chin/body nub
    # stubby arms
    m.bone("rarm", [-4, 9, 0], "head")
    m.bone("larm", [4, 9, 0], "head")
    m.cube("rarm", [-7, 5, -1.5], [3, 5, 3], HEAD, rot=[0, 0, 30], pivot=[-4, 9, 0])
    m.mirror("larm", [-7, 5, -1.5], [3, 5, 3], HEAD, rot=[0, 0, 30], pivot=[-4, 9, 0])
    # flame tail
    m.bone("tail", [0, 4, 0], "root")
    m.cube("tail", [-1.5, -1, -1.5], [3, 5, 3], lit("#ff8a1e"))
    m.cube("tail", [-1, -4, -1], [2, 3, 2], lit("#ffb03a"))
    m.cube("tail", [0, -6, -0.5], [1, 2, 1], lit("#ffd23a"))
    idle = {"root": {"position": wave(1.6, lambda q: [0, S(q) * 1.5, 0])},
            "flame": {"scale": wave(0.8, lambda q: [1 + 0.06 * S(q), 1 + 0.12 * S(q * 2), 1]),
                      "rotation": wave(1.6, lambda q: [0, S(q) * 10, S(q * 2) * 3])},
            "rarm": {"rotation": wave(1.6, lambda q: [S(q) * 15, 0, S(q) * 10])},
            "larm": {"rotation": wave(1.6, lambda q: [-S(q) * 15, 0, -S(q) * 10])},
            "tail": {"rotation": wave(1.6, lambda q: [S(q) * 12, 0, S(q * 2) * 8])}}
    anim(m, "idle", 1.6, idle)
    mv = dict(idle)
    mv["root"] = {"rotation": wave(1.0, lambda q: [15, 0, S(q) * 5]), "position": wave(1.0, lambda q: [0, S(q) * 1.5, 0])}
    anim(m, "move", 1.0, mv)
    anim(m, "attack", 0.6, {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.2, [-120, 0, 20]), (0.35, [-60, 0, 0]), (0.6, [0, 0, 0]))},
                            "flame": {"scale": keys((0, [1, 1, 1]), (0.2, [1.3, 1.3, 1.3]), (0.6, [1, 1, 1]))}}, loop=False)
    anim(m, "special", 1.0, {"flame": {"scale": keys((0, [1, 1, 1]), (0.4, [1.6, 1.7, 1.6]), (0.7, [0.8, 0.8, 0.8]), (1.0, [1, 1, 1]))},
                             "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-20, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 0.8, {"root": {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 180, 0]), (0.8, [0, 360, 0])),
                                       "scale": keys((0, [1, 1, 1]), (0.4, [0.5, 0.5, 0.5]), (0.8, [1, 1, 1]))}}, loop=False)
    return m


# =====================================================================================
# EMBER WRAITH - hooded basalt spectre, molten face, burning arm
# =====================================================================================
def ember_wraith(seed):
    m = Rig("ember_wraith", 64, 64, seed)
    ROBE = noise(["#141314", "#1c1a1c", "#242224", "#2c2a2c"])
    face = {}
    for x in range(1, 7):
        for y in range(2, 6):
            face[(x, y)] = "o"
    for (x, y) in ((1, 5), (6, 5), (2, 6), (5, 6)):
        face[(x, y)] = "_x"
    for (x, y) in ((2, 3), (5, 3), (3, 4), (4, 4), (3, 3), (4, 3)):
        face[(x, y)] = "y"
    face[(3, 6)] = "o"; face[(4, 6)] = "o"
    face = {k: ("r" if v == "_x" else v) for k, v in face.items()}
    art = {"head": {"north": A.at(8, 8, face, o="!#e8541a", y="!#ffd23a", r="#1c1a1c")},
           "larm": {"north": None}}
    a = biped(m, head=ROBE, body=ROBE, arm=ROBE, arm_l=lava_glow(False), leg=bands(ROBE, (5, 9, fire_band(1.0))),
              art={"head": art["head"]})
    t = a["top"]
    # hood: overhang in front, raised crown
    m.cube("head", [-4.5, t + 7, -5.5], [9, 2, 1], ROBE)
    m.cube("head", [-4.5, t + 8, -4.5], [9, 1, 9], ROBE)
    m.pair("head", "head", [-5, t - 1, -4.5], [1, 9, 9], ROBE)
    # tattered robe skirt over the legs (open at the front)
    m.bone("robe", [0, t - 12, 0], "body")
    m.cube("robe", [-4.5, t - 22, 1.6], [9, 10, 1], ROBE, art={"south": A("..........\n" * 8 + "._.__._.__\n__._.___._", {})})
    m.pair("robe", "robe", [-4.6, t - 21, -2.4], [1, 9, 4], ROBE, art={"sides": A("....\n" * 7 + "._._\n_.__", {})})
    m.cube("robe", [-4.5, t - 20, -2.6], [3, 8, 1], ROBE)
    m.cube("robe", [1.5, t - 20, -2.6], [3, 8, 1], ROBE)
    # the burning arm throws flame particles in-game; model a flame cuff
    m.cube("larm", [3.5, t - 13, -2.5], [5, 3, 5], lit("#ffd23a"), inflate=0.1)
    biped_anims(m, "claw", cape="robe")
    return m


# =====================================================================================
# LAVA SCORPION
# =====================================================================================
def lava_scorpion(seed):
    m = Rig("lava_scorpion", 128, 128, seed)
    SHELL = over(noise(["#1c1210", "#241814", "#2e1e18", "#38241c"]), veins(LAVA, 0.05), spots(LAVA_HOT, 0.012, glow=True))
    SHELL2 = over(noise(["#1c1210", "#241814", "#2e1e18"]), veins(LAVA, 0.02))
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 6, 0], "root")
    m.cube("body", [-7, 4, -8], [14, 7, 14], SHELL,
           art={"up": A.at(14, 14, {**{(x, 7): "L" for x in range(1, 13)}, **{(7, y): "L" for y in range(1, 13)}}, L="!" + LAVA)})
    m.bone("head", [0, 7, -8], "body")
    m.cube("head", [-5, 4, -12], [10, 6, 5], SHELL2,
           art={"north": A.at(10, 6, {(2, 1): "E", (3, 1): "E", (6, 1): "E", (7, 1): "E", (2, 2): "e", (7, 2): "e"}, E="!#ffd23a", e="!" + LAVA)})
    # pincers: arm + two-part claw (upper fixed, lower "finger" bone)
    for side, sx in (("rclaw", -1), ("lclaw", 1)):
        m.bone(side, [sx * 5, 7, -10], "head", rotation=[0, -sx * 20, 0])
        cx = -9 if sx < 0 else 4
        m.cube(side, [cx, 5, -16], [5, 4, 7], SHELL2)
        m.cube(side, [cx - 1, 5, -24], [7, 4, 8], SHELL)
        m.bone(side + "_f", [sx * 6.5, 5, -18], side)
        m.cube(side + "_f", [cx - 1, 2, -25], [7, 3, 8], SHELL2)
    # legs: 3 thick jointed pairs
    for i, z in enumerate((-5, -1, 3)):
        for sx, sd in ((-1, "r"), (1, "l")):
            b = f"leg{i}{sd}"
            m.bone(b, [sx * 7, 8, z], "body", rotation=[0, sx * (i - 1) * -20, 0])
            x0 = -13 if sx < 0 else 7
            m.cube(b, [x0, 7, z - 1.5], [6, 3, 3], SHELL2)
            m.bone(b + "_2", [sx * 13, 8, z], b)
            m.cube(b + "_2", [(x0 + 5) if sx > 0 else x0 - 2, 0, z - 1.5], [3, 9, 3], SHELL2, rot=[0, 0, sx * 10],
                   pivot=[sx * 13, 8, z])
    # tail: 5 segments curling up and over, stinger
    prev = "body"
    y, z = 8, 6
    sizes = [5, 5, 4, 4, 4]
    curl = [35, 35, 40, 35, 30]            # tail points +Z: +X lifts its far end
    tails = []
    for i, s in enumerate(sizes):
        b = f"tail{i}"
        m.bone(b, [0, y, z], prev, rotation=[curl[i], 0, 0])
        m.cube(b, [-s / 2, y - s / 2, z], [s, s, 5], SHELL)
        tails.append(b)
        prev, z = b, z + 5
    m.bone("stinger", [0, y, z], prev, rotation=[40, 0, 0])
    m.cube("stinger", [-2.5, y - 2.5, z], [5, 5, 5], SHELL2)
    m.cube("stinger", [-1, y - 1, z + 5], [2, 2, 3], lit(LAVA_HOT), rot=[30, 0, 0], pivot=[0, y, z + 5])
    legs = {}
    for i in range(3):
        for sd, sx in (("r", -1), ("l", 1)):
            ph = i * 1.4 + (0 if sd == "r" else 3.14)
            legs[f"leg{i}{sd}"] = {"rotation": wave(0.6, lambda q, ph=ph, sx=sx: [0, S(q + ph) * 18, -sx * max(0, S(q + ph)) * 12])}
    tail_idle = {b: {"rotation": wave(2.5, lambda q, i=i: [S(q - i * 0.4) * 4, S(q * 0.5 - i * 0.3) * 4, 0])} for i, b in enumerate(tails)}
    idle = dict(tail_idle)
    idle["rclaw_f"] = {"rotation": wave(2.5, lambda q: [max(0, S(q)) * 10, 0, 0])}
    idle["lclaw_f"] = {"rotation": wave(2.5, lambda q: [max(0, S(q + 1)) * 10, 0, 0])}
    anim(m, "idle", 2.5, idle)
    mv = dict(legs); mv.update(tail_idle)
    mv["body"] = {"position": wave(0.6, lambda q: [0, abs(S(q)) * 0.4, 0])}
    anim(m, "move", 0.6, mv)
    anim(m, "attack", 0.5, {"tail4": {"rotation": keys((0, [0, 0, 0]), (0.15, [-30, 0, 0]), (0.3, [45, 0, 0]), (0.5, [0, 0, 0]))},
                            "stinger": {"rotation": keys((0, [0, 0, 0]), (0.15, [-20, 0, 0]), (0.3, [50, 0, 0]), (0.5, [0, 0, 0]))},
                            "tail2": {"rotation": keys((0, [0, 0, 0]), (0.15, [-10, 0, 0]), (0.3, [20, 0, 0]), (0.5, [0, 0, 0]))},
                            "rclaw_f": {"rotation": keys((0, [0, 0, 0]), (0.15, [25, 0, 0]), (0.3, [0, 0, 0]), (0.5, [0, 0, 0]))},
                            "lclaw_f": {"rotation": keys((0, [0, 0, 0]), (0.15, [25, 0, 0]), (0.3, [0, 0, 0]), (0.5, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.0, {"body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-20, 0, 0]), (0.7, [-20, 0, 0]), (1.0, [0, 0, 0])),
                                      "position": keys((0, [0, 0, 0]), (0.3, [0, 2, 0]), (0.7, [0, 2, 0]), (1.0, [0, 0, 0]))},
                             "rclaw": {"rotation": keys((0, [0, 0, 0]), (0.3, [-30, 0, -20]), (0.7, [-30, 0, -20]), (1.0, [0, 0, 0]))},
                             "lclaw": {"rotation": keys((0, [0, 0, 0]), (0.3, [-30, 0, 20]), (0.7, [-30, 0, 20]), (1.0, [0, 0, 0]))}}, loop=False)
    anim(m, "special2", 1.0, {"root": {"position": keys((0, [0, 0, 0]), (0.35, [0, -10, 0]), (0.7, [0, -10, 0]), (1.0, [0, 0, 0]))},
                              "body": {"rotation": keys((0, [0, 0, 0]), (0.35, [0, 180, 0]), (1.0, [0, 360, 0]))}}, loop=False)
    return m


# =====================================================================================
# FIRE PIRATE - charred buccaneer, flaming cutlass and a burning hand
# =====================================================================================
def fire_pirate(seed):
    m = Rig("fire_pirate", 64, 64, seed)
    CHAR = ["#161414", "#1e1b1a", "#262221", "#2e2928"]
    P = dict(b="#6e1a16", B="#4e120e", c="#1e1b1a", C="#101010", e="!#ffb03a", E="!#ffd23a", k="#0a0a0a", r="#3a2a28")
    head = A("""
bBbbbbBb
bbbbbbbb
BbbbbbbB
cCCcccec
cCCccEEc
cccrrccc
ccrccrcc
cccccccc""", P)
    coat = A("""
BbcCcbbB
bbcCcbbb
bbcccbbb
bbcCcbbb
bbccCbbb
bbcccbbb
bbcCcbbb
bbcccbbb
LLLGGLLL
LLLGgLLL
bbcccbbb
bbcccbbb""", dict(b="#5a1a14", B="#3e120e", c="#1e1b1a", C="#101010", L="#2a1a12", G="#d4a53a", g="#8a6a22"))
    a = biped(m, head=noise(CHAR), body=noise(["#3e120e", "#4a1610", "#5a1a14"]),
              arm=bands(noise(["#3e120e", "#4a1610", "#5a1a14"]), (9, 12, noise(CHAR))),
              leg=bands(noise(["#1e1614", "#2a1e1a", "#322420"]), (9, 12, noise(["#0e0c0c", "#161212"]))),
              art={"head": {"north": head, "up": A("bbbbbbbb\nbBbbbbBb\nbbbbbbbb\nbbbBbbbb\nbbbbbbbb\nbBbbbbbb\nbbbbbBbb\nbbbbbbbb", P)},
                   "body": {"north": coat}})
    t = a["top"]
    # bandana knot tails + singed coat tails
    m.cube("head", [3, t + 4, 4], [2, 2, 1], noise(["#4e120e", "#6e1a16"]))
    m.cube("head", [4, t + 1, 4.2], [1, 4, 1], noise(["#4e120e", "#6e1a16"]), rot=[0, 0, 20])
    m.bone("coat", [0, t - 12, 0], "body")
    m.cube("coat", [-4.5, t - 20, 1.5], [9, 8, 1], noise(["#3e120e", "#4a1610"]),
           art={"south": A("........." * 1 + "\n" + ".........\n" * 5 + "._.__.._.\n_._.___._", {})})
    # flaming cutlass: dark hilt, blade glowing hotter toward the tip
    rx, hy = a["rx"], a["hand_y"]
    pv = [rx, hy, 0]
    m.cube("rhand", [rx - 0.5, hy - 1, -1], [1, 3, 2], solid("#1a1210"), rot=[-50, 0, 0], pivot=pv)
    m.cube("rhand", [rx - 1.5, hy - 1.5, -3], [3, 3, 1], solid("#2a2426"), rot=[-50, 0, 0], pivot=pv)
    m.cube("rhand", [rx - 0.5, hy - 1, -15], [1, 2, 12], lava_glow(True), rot=[-50, 0, 0], pivot=pv)
    # burning left hand: flame cluster
    lx = a["lx"]
    for (dx, dy, dz, s, c) in ((-2, -2, -2, 4, "#ff8a1e"), (-1.5, 1, -1.5, 3, "#ffb03a"), (-1, 3, -1, 2, "#ffd23a"),
                               (0.5, 2, 0, 2, "#ff6a1a"), (-2.5, 0, 0.5, 2, "#e8541a")):
        m.cube("lhand", [lx + dx, hy + dy - 2, dz], [s, s, s], lit(c))
    biped_anims(m, "sword", cape="coat", hold=[-20, 0, 0],
                extra_idle={"lhand": {"scale": wave(0.6, lambda q: [1 + 0.08 * S(q), 1 + 0.15 * S(q * 2), 1 + 0.08 * S(q)])}})
    return m


from mobs.warlord import molten_warlord as _warlord  # noqa: E402  (rebuilt 2026-09-29, own module)

MOBS = {"molten_warlord": _warlord, "obsidian_golem": obsidian_golem, "flame_sprite": flame_sprite,
        "ember_wraith": ember_wraith, "lava_scorpion": lava_scorpion, "fire_pirate": fire_pirate}
SKINS = {"molten_warlord": {"enraged": lambda seed: _warlord(seed, "enraged"), "quenched": lambda seed: _warlord(seed, "quenched")}}
