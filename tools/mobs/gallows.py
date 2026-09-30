"""The Gallows Grotto set pieces (2026-09-29, Revenant round 2):

  gallowbrand        - THE HANGMAN'S GREATSWORD: the executioner's blade that pinned the Chained Revenant. A broad
                       blued-steel blade with a squared-off executioner's tip and a hanging-eye hole, soul-teal runes
                       down the fuller, a guard of down-swept quillons ending in open manacles, a two-hand grip in
                       tarred rope, chain wound round the ricasso and a skull pommel. Item model (GeoItem) AND the
                       thrown sword (clip `spin`: end over end). Blade along +Y, flat faces toward +/-X.
  gallowbrand_stone  - the same sword, 1.25x, driven point-first into a cracked, soul-veined boulder and lashed down
                       by four chains (GeckoLib block). Clips: idle (the blade hums, chains sway), empty (the chains lie
                       broken, the sword shrunk away inside the rock) and pull (it wrenches free, chains snap).
  lamplighter        - OLD WICK, the Lamplighter of Gallows Landing: a hunched old harbour keeper in a faded oilskin and
                       sou'wester with a long white beard, one milky glowing eye, and a long lantern pole whose soul
                       lantern he keeps lit "so he stays asleep". Clips: idle, move, attack (unused), talk.
"""
from mobkit import A, Rig, noise, over, solid, lit, spots, speckle, veins, drips, anim, keys, wave, S

STEEL = ["#2c343e", "#343e4a", "#3c4654", "#44505e"]
EDGE = ["#8a949e", "#9aa4ae", "#aab4bc"]
IRON = ["#1e2024", "#26292e", "#2e3238"]
ROPE = ["#3a2a1a", "#4a3622", "#2e2014"]
BONE = ["#d8d0b8", "#c8c0a6", "#e4dcc6"]
GLOW = "!#3fe0c0"


def link(w, h, col="#6a7078"):
    return A.at(w, h, {(x, y): "l" for x in range(w) for y in range(h) if (x + y) % 3 == 0}, l=col)


def sword(m, bone, cx=0.0, gy=0.0, cz=0.0):
    """The Gallowbrand standing point-UP with the bottom of its guard at y = gy (centre cx, cz). Whole-pixel sizes only
    (the geo export rounds sizes), and parts touch rather than overlap (check_zfight)."""
    def c(o, s, mat, **kw):
        m.cube(bone, [cx + o[0], gy + o[1], cz + o[2]], s, mat, **kw)

    L = 32                                                               # blade length above the ricasso
    runes = {(3, y): "g" for y in range(3, L - 7) if y % 5 != 2}
    runes.update({(2, y): "g" for y in range(4, L - 8, 5)})
    runes.update({(4, y): "g" for y in range(6, L - 8, 5)})
    hole = {(x, y): "_" for x in (2, 3, 4) for y in (2, 3, 4) if not (x != 3 and y != 3)}   # the executioner's eye
    face = A.at(7, L, {**runes, **hole}, g=GLOW)
    c([-1, 5, -3.5], [2, L, 7], noise(STEEL), art={"east": face, "west": face})           # the blade
    c([-0.5, 5, -4.5], [1, L, 1], noise(EDGE))                                             # bright edges
    c([-0.5, 5, 3.5], [1, L, 1], noise(EDGE))
    c([-1, 5 + L, -4.5], [2, 1, 9], noise(EDGE))                                          # squared executioner's tip
    c([-1, 3, -3], [2, 2, 6], noise(STEEL))                                                # the ricasso
    for y in (3.5, 6, 8.5):                                                                # chain wound round it
        c([-1.5, y, -4], [3, 1, 8], noise(["#4a4f56", "#5a6068"]), art={s: link(8, 1) for s in ("north", "south", "east", "west")})
    # the guard: a heavy bar with down-swept quillons ending in open manacle cuffs (glowing keyholes)
    c([-1.5, 1, -6], [3, 2, 12], noise(IRON))
    kh = A.at(3, 2, {(1, 0): "g"}, g=GLOW)
    for z0 in (-8, 6):
        c([-1, -1, z0], [2, 3, 2], noise(IRON))                                            # swept-down tips
        c([-1.5, -3, z0 - 0.5], [3, 2, 3], noise(["#34373c", "#3e4248"]), art={"east": kh, "west": kh})
    boss = A.at(2, 3, {(0, 1): "g", (1, 1): "g"}, g=GLOW)
    c([-2, 0.5, -1], [4, 3, 2], noise(["#3a3e44", "#34373c"]), art={"east": boss, "west": boss})   # the guard boss
    # the grip: tarred rope, a two-hand length, bound with hangman's knots
    rope = A.at(2, 12, {(x, y): "r" for x in range(2) for y in range(12) if (x + y) % 2 == 0}, r="#5a4430")
    c([-1, -11.5, -1], [2, 12, 2], over(noise(ROPE), spots("#1a120a", 0.2)), art={s: rope for s in ("north", "south", "east", "west")})
    c([-1.5, -3, -1.5], [3, 2, 3], noise(["#6a5a3a", "#5a4a2e"]))
    c([-1.5, -9, -1.5], [3, 1, 3], noise(["#6a5a3a", "#5a4a2e"]))
    # the pommel: a skull with glowing sockets
    skull = A.at(4, 4, {(0, 1): "e", (3, 1): "e", (1, 3): "t", (2, 3): "t"}, e=GLOW, t="#1a1612")
    c([-2, -15, -2], [4, 4, 4], noise(BONE), art={"north": skull, "south": skull, "east": skull, "west": skull})
    c([-1.5, -16, -1.5], [3, 1, 3], noise(IRON))
    c([-0.5, -18, -0.5], [1, 2, 1], noise(IRON))


# =====================================================================================
def gallowbrand(seed):
    m = Rig("gallowbrand", 64, 64, seed)
    m.bone("root", [0, 8, 0])                                             # pivot ~ the balance point (spin axis)
    sword(m, "root", 0, 0, 0)
    anim(m, "idle", 1.0, {"root": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, 0]))}})
    # thrown: end over end (about X - in the blade's flat plane), 3 turns a second
    anim(m, "spin", 0.35, {"root": {"rotation": keys((0, [0, 0, 0]), (0.175, [-180, 0, 0]), (0.35, [-360, 0, 0]))}})
    return m


# =====================================================================================
def gallowbrand_stone(seed):
    m = Rig("gallowbrand_stone", 128, 128, seed)
    ROCK = lambda: over(noise(["#1c1e22", "#24272c", "#2c3036", "#34383e"]), veins("#3fe0c0", 0.03, glow=True, length=(3, 8)),
                        speckle("#3a4a2a", 0.05))
    MOSS = over(noise(["#2a3a22", "#34462a", "#3e5230"]), speckle("#1c2618", 0.1))
    m.bone("root", [0, 0, 0])
    m.bone("rock", [0, 0, 0], "root")
    # a squat, broken boulder (~2.4 blocks across, ~1.3 tall), stepped so it reads as a crag rather than a box
    m.cube("rock", [-19, 0, -16], [38, 9, 32], ROCK())
    m.cube("rock", [-16, 9, -13], [30, 6, 27], ROCK())
    m.cube("rock", [-11, 15, -9], [21, 4, 18], ROCK())
    m.cube("rock", [-6, 19, -5], [12, 2, 10], ROCK())
    m.cube("rock", [-22, 0, -6], [3, 6, 12], ROCK())                      # shoulders breaking out of the sides
    m.cube("rock", [19, 0, -9], [4, 7, 10], ROCK())
    m.cube("rock", [-8, 0, -19], [14, 5, 3], ROCK())
    m.cube("rock", [-4, 0, 16], [12, 6, 3], ROCK())
    m.cube("rock", [-16, 15, -7], [5, 3, 6], ROCK(), rot=[0, 0, 18], pivot=[-13, 15, -4])   # tilted slabs
    m.cube("rock", [11, 13, 2], [5, 4, 7], ROCK(), rot=[0, 0, -22], pivot=[13, 13, 5])
    m.cube("rock", [-12, 9, -16], [10, 1, 3], MOSS)                       # moss on the ledges
    m.cube("rock", [5, 19, -9], [5, 1, 4], MOSS)
    # bones round the foot: a skull, a ribcage, scattered bones
    sk = A.at(4, 4, {(0, 1): "k", (1, 1): "k", (2, 1): "k", (3, 1): "k", (1, 3): "k", (2, 3): "k"}, k="#1a1612")
    m.cube("rock", [-12, 9, -17], [5, 5, 5], noise(BONE), art={"north": sk}, rot=[0, 20, 0], pivot=[-10, 9, -15])
    for i in range(4):
        m.cube("rock", [8 + i * 2.2, 9, -16], [1, 3 - (i % 2), 3], noise(BONE))
    m.cube("rock", [-20, 6, 7], [7, 1.2, 1.2], noise(BONE), rot=[0, 30, 0], pivot=[-17, 6, 8])
    m.cube("rock", [14, 7, 10], [6, 1.2, 1.2], noise(BONE), rot=[0, -40, 0], pivot=[17, 7, 11])
    # candles burnt down on the rock
    for (x, y, z) in ((-14, 15, 6), (12, 17, -6), (-3, 21, 6)):
        m.cube("rock", [x, y, z], [1.2, 2.2, 1.2], solid("#d8cca8"))
        m.cube("rock", [x + 0.35, y + 2.2, z + 0.35], [0.5, 0.7, 0.5], lit("#aaf0ff"))

    # THE SWORD: 1.25x, point down, driven 14 px deep, leaning a little
    # (the block renderer draws the whole model at 1.25x, so the sword reads as a giant's blade)
    m.bone("sword", [0, 21, 0], "root", rotation=[180 + 6, 90, 0])
    # built point-UP with its guard 23 px under the pivot: the bone flips it, so 23 px of blade stand above the rock
    sword(m, "sword", 0, 21 - 23, 0)
    # FOUR CHAINS lashing the blade down: bone at each rock anchor, cube running up to the guard
    L = 36
    for i, (x, z, rx, rz) in enumerate(((-14, -10, -17, 20), (14, -10, -17, -20), (-13, 10, 17, 20), (13, 10, 17, -20))):
        b = f"chain{i}"
        m.bone(b, [x, 14, z], "root", rotation=[rx, 0, rz])
        m.cube(b, [x - 0.75, 14, z - 0.75], [1.5, L, 1.5], noise(["#3a3e44", "#4a4f56", "#5a6068"]),
               art={s: link(2, L) for s in ("north", "south", "east", "west")})
        m.cube(b, [x - 1.5, 12, z - 1.5], [3, 2.5, 3], noise(IRON))       # the staple in the rock

    chains = [f"chain{i}" for i in range(4)]
    idle = {"sword": {"rotation": wave(2.0, lambda q: [S(q * 6) * 0.4, 0, S(q * 5) * 0.4])}}   # a faint hum
    for i, c in enumerate(chains):
        idle[c] = {"rotation": wave(4.0, lambda q, i=i: [S(q + i) * 1.5, 0, S(q * 0.5 + i) * 1.5])}
    anim(m, "idle", 4.0, idle)
    empty = {"sword": {"scale": keys((0, [0.01, 0.01, 0.01]), (1.0, [0.01, 0.01, 0.01]))}}
    for i, c in enumerate(chains):
        sx = -1 if i % 2 == 0 else 1
        sz = -1 if i < 2 else 1
        # snapped: short stubs of chain left in the staples, splayed outward
        empty[c] = {"rotation": keys((0, [-40 * sz, 0, 30 * sx]), (1.0, [-40 * sz, 0, 30 * sx])),
                    "scale": keys((0, [1, 0.3, 1]), (1.0, [1, 0.3, 1]))}
    anim(m, "empty", 1.0, empty)
    pull = {"sword": {"position": keys((0, [0, 0, 0]), (0.25, [0.3, 1, 0]), (0.35, [-0.3, 2, 0]), (0.45, [0.3, 4, 0]), (0.8, [0, 22, 0]),
                                       (1.0, [0, 30, 0])),
                      "scale": keys((0, [1, 1, 1]), (0.8, [1, 1, 1]), (1.0, [0.01, 0.01, 0.01]))}}
    for i, c in enumerate(chains):
        sx = -1 if i % 2 == 0 else 1
        sz = -1 if i < 2 else 1
        pull[c] = {"rotation": keys((0, [0, 0, 0]), (0.3, [4 * sz, 0, -4 * sx]), (0.45, [-30 * sz, 0, 30 * sx]),
                                    (0.7, [-60 * sz, 0, 45 * sx]), (1.0, [-40 * sz, 0, 30 * sx])),
                   "scale": keys((0, [1, 1, 1]), (0.45, [1, 1, 1]), (0.5, [1, 0.3, 1]), (1.0, [1, 0.3, 1]))}
    anim(m, "pull", 1.0, pull, loop=False)
    return m


# =====================================================================================
def lamplighter(seed):
    m = Rig("lamplighter", 64, 64, seed)
    OIL = over(noise(["#8a7430", "#9a8238", "#a88e40", "#7a6628"]), spots("#5a4a1e", 0.08), drips("#6a5a24", 0.15, maxlen=3))
    PATCH = noise(["#4a3a5a", "#3a2e48"])
    TROU = noise(["#2e2a26", "#36312c", "#3e3832"])
    BOOT = noise(["#3a2a1c", "#46321f", "#2e2216"])
    SKIN = noise(["#c89a7a", "#b88a6a", "#d4a888"])
    BEARD = noise(["#d8d8d0", "#c8c8c0", "#e8e8e0", "#b8b8b0"])
    HAT = over(noise(["#b8962e", "#c8a238", "#a8862a"]), speckle("#6a5a1e", 0.06))
    m.bone("root", [0, 0, 0])
    # legs: thin, bowed, in tall sea boots
    for b, x in (("rleg", -2.2), ("lleg", 2.2)):
        m.bone(b, [x, 11, 0], "root")
        m.cube(b, [x - 1.6, 4, -1.6], [3.2, 7, 3.2], TROU)
        m.cube(b, [x - 2, 0, -2.2], [4, 5, 4.4], BOOT)
        m.cube(b, [x - 2.2, 4.5, -2.4], [4.4, 1, 4.8], noise(["#2a241c"]))   # boot cuff
    m.cube("lleg", [0.6, 7, -1.7], [2, 2, 0.1], PATCH)
    # the torso: hunched forward from the hips
    m.bone("body", [0, 11, 0], "root", rotation=[18, 0, 0])
    m.cube("body", [-4.5, 11, -2.8], [9, 10, 5.6], OIL,
           art={"north": A.at(9, 10, {**{(4, y): "b" for y in range(10)}, (4, 2): "k", (4, 5): "k", (4, 8): "k"}, b="#6a5a24", k="#c8b060")})
    m.cube("body", [-4, 17, 2.4], [8, 5, 2.6], OIL)                            # the hump
    m.cube("body", [-5.2, 18.5, -3.3], [10.4, 3, 6.6], OIL)                    # the cape collar
    m.cube("body", [-4.6, 11.5, -3], [9.2, 1.2, 6], noise(["#2a1e14", "#3a2a1a"]))   # belt
    m.cube("body", [-1, 11.3, -3.3], [2, 1.6, 0.5], solid("#a88a3a"))          # buckle
    # a ring of old keys + a coil of rope at the hip
    m.cube("body", [3.6, 8.3, -2.2], [1, 3, 3], noise(["#5a5448", "#6a6456"]))
    m.cube("body", [-5.8, 13, -2.2], [1.5, 4, 4.4], noise(["#8a7852", "#a8946a"]))
    # the oilskin's skirt, split at the front
    m.bone("skirt", [0, 11.5, 0], "body")
    skirt = A.at(10, 7, {**{(x, 6): "_" for x in (0, 3, 7, 9)}, **{(4, y): "_" for y in range(3, 7)}, **{(5, y): "_" for y in range(2, 7)}})
    m.cube("skirt", [-5, 4.5, -3.1], [10, 7, 6.2], OIL, art={"north": skirt, "south": A.at(10, 7, {(x, 6): "_" for x in (1, 4, 8)})})
    m.cube("skirt", [2.5, 7, -3.2], [2.2, 2.2, 0.1], PATCH)
    # the head, tipped back to look out from under the hunch
    m.bone("head", [0, 21, -1], "body", rotation=[-22, 0, 0])
    face = {(1, 2): "w", (2, 2): "w", (4, 2): "w", (5, 2): "w",       # bushy brows
            (1, 3): "E", (2, 3): "e",                                 # the milky glowing eye (his right)
            (4, 3): "k", (5, 3): "k",                                 # the squint
            (0, 4): "r", (6, 4): "r", (1, 5): "l", (5, 5): "l"}
    m.cube("head", [-3.5, 21, -4.5], [7, 7, 7], SKIN,
           art={"north": A.at(7, 7, face, w="#e8e8e0", E="!#e8f8ff", e="!#a8e8f0", k="#2a1a14", r="#c07a6a", l="#8a6a54")})
    m.cube("head", [-1, 22.5, -5.8], [2, 2.6, 1.4], noise(["#c8826a", "#d8927a"]))  # the nose
    m.cube("head", [-3.3, 22, -5.2], [6.6, 1, 0.8], BEARD)                           # moustache
    m.bone("beard", [0, 22, -4.6], "head")
    bd = A.at(6, 8, {(0, 7): "_", (5, 7): "_", (0, 6): "_", (5, 6): "_", (2, 7): "_", (4, 5): "d", (1, 3): "d", (3, 6): "d"}, d="#a8a8a0")
    m.cube("beard", [-3, 14.5, -5.4], [6, 8, 1.4], BEARD, art={"north": bd})
    m.cube("head", [-3.7, 22, -3.8], [0.6, 4, 6], BEARD)                              # side whiskers
    m.cube("head", [3.1, 22, -3.8], [0.6, 4, 6], BEARD)
    # the sou'wester: crown, a brim that widens into a long back flap
    m.cube("head", [-3.8, 27.8, -4.8], [7.6, 2.6, 7.6], HAT)
    m.cube("head", [-3, 30.4, -4], [6, 1, 6], HAT)
    m.cube("head", [-4.6, 27.6, -5.9], [9.2, 0.6, 2.4], HAT)                          # front brim (turned up)
    m.cube("head", [-5, 26.2, 1.6], [10, 1.8, 3.8], HAT, rot=[-28, 0, 0], pivot=[0, 27.8, 2])   # back flap
    m.cube("head", [-4.6, 24, -3], [1, 4, 5], HAT)                                    # ear flaps
    m.cube("head", [3.6, 24, -3], [1, 4, 5], HAT)
    # right arm forward, gripping the lantern pole; left arm crooked
    m.bone("rarm", [-5.6, 20, -0.5], "body", rotation=[-40, 0, 4])
    m.cube("rarm", [-7.2, 12, -1.8], [3.2, 8.5, 3.6], OIL)
    m.cube("rarm", [-7.4, 11.6, -2], [3.6, 2, 4], noise(["#6a5a24", "#7a6628"]))       # cuff
    m.cube("rarm", [-7, 10.2, -1.6], [2.8, 1.8, 3.2], SKIN)                          # hand
    m.bone("larm", [5.6, 20, -0.5], "body", rotation=[-20, 0, -6])
    m.cube("larm", [4, 12, -1.8], [3.2, 8.5, 3.6], OIL)
    m.cube("larm", [3.8, 11.6, -2], [3.6, 2, 4], noise(["#6a5a24", "#7a6628"]))
    m.cube("larm", [4.2, 10.2, -1.6], [2.8, 1.8, 3.2], SKIN)
    m.cube("larm", [4.6, 12.5, 1.9], [2, 2, 0.1], PATCH)
    m.bone("finger", [5.6, 10.3, -0.2], "larm")
    m.cube("finger", [5.2, 7.6, -0.6], [0.9, 2.7, 0.9], SKIN)                         # the warning finger
    # THE LANTERN POLE: held upright in the right fist (counter-rotated so it stands straight)
    m.bone("pole", [-5.6, 11, -0.2], "rarm", rotation=[22, 0, -4])
    m.cube("pole", [-6.2, -9, -0.8], [1.2, 40, 1.2], noise(["#3a2616", "#4a3220", "#2e1e12"]))
    m.cube("pole", [-6.5, 29, -0.9], [1.8, 1.2, 1.4], noise(IRON))                    # iron ferrule
    m.cube("pole", [-6.1, 30.2, -6], [1, 1, 6.2], noise(IRON))                          # the hook arm, reaching forward
    m.cube("pole", [-6.1, 29.2, -6], [1, 1, 1], noise(IRON))
    m.bone("lantern", [-5.6, 29.2, -5.5], "pole")
    m.cube("lantern", [-5.9, 27.6, -5.8], [0.6, 1.6, 0.6], noise(["#4a4f56"]))       # chain
    m.cube("lantern", [-7.2, 26.8, -7.1], [3.2, 0.8, 3.2], noise(IRON))               # cap
    m.cube("lantern", [-6.9, 23.8, -6.8], [2.6, 3, 2.6], lit("#5ff0d8"))             # the soul flame
    m.cube("lantern", [-7.2, 23, -7.1], [3.2, 0.8, 3.2], noise(IRON))                 # base
    for (dx, dz) in ((0, 0), (2.8, 0), (0, 2.8), (2.8, 2.8)):
        m.cube("lantern", [-7.3 + dx, 23.8, -7.2 + dz], [0.4, 3, 0.4], noise(IRON))   # frame posts

    anim(m, "idle", 6.0, {
        "body": {"rotation": wave(6.0, lambda q: [S(q * 2) * 1.5, 0, 0])},
        "head": {"rotation": wave(6.0, lambda q: [S(q * 2) * 3, S(q) * 14, 0])},
        "beard": {"rotation": wave(6.0, lambda q: [S(q * 2 + 1) * 3, 0, 0])},
        "lantern": {"rotation": wave(6.0, lambda q: [S(q * 2) * 8, 0, S(q * 2 + 1.2) * 6])},
        "larm": {"rotation": wave(6.0, lambda q: [S(q * 2) * 3, 0, 0])}})
    anim(m, "move", 1.6, {
        "rleg": {"rotation": wave(1.6, lambda q: [-S(q) * 20, 0, 0])},
        "lleg": {"rotation": wave(1.6, lambda q: [S(q) * 14, 0, 0])},                       # the bad leg drags
        "body": {"rotation": wave(1.6, lambda q: [2, S(q) * 4, S(q) * 4]), "position": wave(1.6, lambda q: [0, -abs(S(q)) * 0.6, 0])},
        "rarm": {"rotation": wave(1.6, lambda q: [S(q) * 10, 0, 0])},
        "pole": {"rotation": wave(1.6, lambda q: [-S(q) * 10, 0, 0])},
        "lantern": {"rotation": wave(1.6, lambda q: [S(q + 0.8) * 16, 0, S(q) * 6])},
        "larm": {"rotation": wave(1.6, lambda q: [-S(q) * 12, 0, 0])},
        "head": {"rotation": wave(1.6, lambda q: [S(q * 2) * 2, 0, 0])}})
    anim(m, "attack", 0.5, {"larm": {"rotation": keys((0, [0, 0, 0]), (0.25, [-40, 0, 0]), (0.5, [0, 0, 0]))}}, loop=False)
    # TALK: raises the lantern, wags a bony finger, shakes his head - "never tread down there"
    anim(m, "talk", 2.4, {
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-95, 0, 8]), (2.0, [-95, 0, 8]), (2.4, [0, 0, 0]))},
        "finger": {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 0, 0]), (0.6, [0, 0, 25]), (0.8, [0, 0, -25]), (1.0, [0, 0, 25]), (1.2, [0, 0, -25]),
                                    (1.4, [0, 0, 25]), (1.6, [0, 0, 0]), (2.4, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-8, 0, 0]), (0.6, [-8, 14, 0]), (0.9, [-8, -14, 0]), (1.2, [-8, 14, 0]), (1.5, [-8, -14, 0]),
                                  (1.8, [-8, 0, 0]), (2.4, [0, 0, 0]))},
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-18, 0, 0]), (2.0, [-18, 0, 0]), (2.4, [0, 0, 0]))},
        "pole": {"rotation": keys((0, [0, 0, 0]), (0.4, [18, 0, 0]), (2.0, [18, 0, 0]), (2.4, [0, 0, 0]))},
        "lantern": {"rotation": wave(2.4, lambda q: [S(q * 2) * 12, 0, S(q * 3) * 8])}}, loop=False)
    return m


MOBS = {"gallowbrand": gallowbrand, "gallowbrand_stone": gallowbrand_stone, "lamplighter": lamplighter}
