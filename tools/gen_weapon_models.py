"""RELIC WEAPONS (2026-09-30): GeckoLib item models for the ten boss-relic weapons, built from the concept sheets in
D:\\Minecraft Modding\\weaponrenders\\<id>.png. Writes geo/<id>.geo.json, textures/entity/<id>.png (+ _glowmask),
animations/<id>.animation.json (read by NamedGeoModel) and a preview sheet tools/previews/weapons/<id>.png.
    python tools/gen_weapon_models.py [id ...]

Conventions (they match the item display transforms in models/item/<id>.json):
  GUNS  (rackham_blunderbuss, dutchmans_hand_cannon, krakenmaw_harpoon_gun): the hand grips the origin, the barrel
        points -Z, the grip hangs down -Y, the lock on +X - exactly like tools/mobs/guns.py.
  BLADES/POLES/BOW/FLAIL: the hand grips the origin, the weapon points UP (+Y), its edge/fins toward -Z (forward),
        thin across X - like the Gallowbrand.
"""
import math
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "armor"))
sys.path.insert(0, str(HERE))
from kit import A, noise, over, lit, veins, spots, speckle, drips, plates, scales, leather, cloth, network, trim   # noqa: E402
from mobkit import Rig, anim, keys                                                                                 # noqa: E402
import preview_geo                                                                                                 # noqa: E402
from PIL import Image                                                                                              # noqa: E402

ASSETS = HERE.parent / "src/main/resources/assets/pixelpirates"


def pal(*c):
    return noise(list(c))


GOLD = pal("#a07818", "#d4a52a", "#f2cf5a")
BRASS = over(pal("#8a6a22", "#b88a2e", "#d9b04a"), spots("#6a4a14", 0.05))
IRON = plates(["#2e3238", "#3e434a", "#50565e"], ph=2, pw=3)
RUST = over(pal("#3a2a24", "#4a3024", "#6a3a22", "#2e2a28"), spots("#8a4a22", 0.12), speckle("#5a5a5a", 0.06))
BONE = over(pal("#b8a888", "#d2c4a4", "#e8dcc0"), speckle("#8a7c5e", 0.06))
WOOD = leather(["#3a2214", "#4a2c18", "#5a3620"])
RED_CORD = cloth(["#6a1414", "#8a1c1c", "#a82a24"], fold=2)


def new(name, seed):
    m = Rig(name, 64, 64, seed)
    m.bone("root", [0, 0, 0])
    return m


def chain_path(m, pts, mat, bone="root"):
    """A continuous chain along the (z, y) points: alternating links every block (in the x = 0 plane)."""
    k = 0
    for (z0, y0), (z1, y1) in zip(pts, pts[1:]):
        n = max(1, int(round(math.dist((z0, y0), (z1, y1)))))
        for i in range(n):
            z, y = z0 + (z1 - z0) * i / n, y0 + (y1 - y0) * i / n
            wide = k % 2 == 0
            m.cube(bone, [-0.75 if wide else -0.25, y - 0.5, z - 0.5], [1.5 * 0 + (2 if wide else 1), 1, 1], mat)
            k += 1


def wv(length, fn, samples=16):
    """Looping channel sampled from fn(phase 0..2pi) -> [x, y, z]."""
    return {f"{round(length * i / samples, 4)}": [round(float(v), 3) for v in fn(2 * math.pi * i / samples)]
            for i in range(samples + 1)}


S, C = math.sin, math.cos


def band(m, y, r, mat, h=1):
    """A ring round a vertical shaft of half-width r."""
    m.cube("root", [-r, y, -r], [2 * r, h, 2 * r], mat)


# =====================================================================================
# 1 RACKHAM'S BLUNDERBUSS (gun)
# =====================================================================================
def rackham_blunderbuss():
    m = new("rackham_blunderbuss", 11)
    c = m.cube
    skull = A.at(8, 4, {(3, 0): "S", (4, 0): "S", (2, 1): "S", (3, 1): "K", (4, 1): "S", (5, 1): "K", (3, 2): "S", (4, 2): "S", (3, 3): "S"}, S="#d8ccb0", K="#2a1a10")
    c("root", [-1.5, -4, 1], [3, 5, 8], WOOD, rot=[-10, 0, 0], pivot=[0, 0, 1], art={"east": skull, "west": skull.flipped()})   # the stock
    c("root", [-1.5, -5, 8.5], [3, 6, 1], BRASS, rot=[-10, 0, 0], pivot=[0, 0, 1])                                              # butt plate
    c("root", [-1.5, -3, 3], [3, 1, 1], RED_CORD); c("root", [-1.5, -3.5, 5], [3, 1, 1], RED_CORD)
    c("root", [-1, -2, -3], [2, 3, 5], WOOD)                                                     # wrist/grip
    c("root", [-1, 0, -21], [2, 2, 19], over(IRON, speckle("#6a6a70", 0.1)))                     # the barrel
    c("root", [-1.5, -1, -17], [3, 1, 14], WOOD)                                                 # forestock
    for z in (-7, -13):
        c("root", [-1.5, -1.5, z], [3, 4, 1], RED_CORD); c("root", [-1.5, -1.5, z - 1.5], [3, 4, 1], RED_CORD)
    for z in (-4, -10, -16):
        c("root", [-1.25, -1.25, z], [2.5 * 0 + 3, 3.5 * 0 + 4, 1], BRASS)
    c("root", [1, 0, -5], [1, 2, 4], IRON)                                                       # lock plate
    m.bone("cock", [1.75, 2, -2.5], "root")
    m.bone("charm", [0, -3, -1], "root")
    c("cock", [1.25, 2, -3], [1, 3, 1], IRON, rot=[-20, 0, 0], pivot=[1.75, 2, -2.5])            # the cock
    c("cock", [1.25, 4.25, -4], [1, 1, 1], pal("#5a5a60"))
    c("root", [-0.5, -3, -4], [1, 1, 4], BRASS)                                                  # trigger guard
    c("root", [-0.5, -2, -2], [1, 1, 1], IRON)
    c("root", [-2, -1, -24], [4, 4, 3], BRASS)                                                   # the bell
    c("root", [-2.5, -1.5, -26], [5, 5, 2], BRASS)
    c("root", [-3, -2, -27], [6, 6, 1], BRASS, art={"north": A(["BBBBBB", "B____B", "BKKKKB", "BKKKKB", "B____B", "BBBBBB"], B="#d9b04a", K="#140e0a")})
    c("charm", [-0.5, -5, -1.5], [1, 2, 1], leather(["#3a2416"]))                                # the charm on its cord
    c("charm", [-1, -7, -2], [2, 2, 2], BRASS, art={"sides": A(["BG", "GB"], B="#b88a2e", G="!3ad8b8")})
    anim(m, "idle", 3.0, {"charm": {"rotation": wv(3.0, lambda q: [10 * S(q), 0, 6 * C(q)])}})
    anim(m, "fire", 0.5, {"root": {"rotation": keys((0, [0, 0, 0]), (0.05, [-16, 0, 0]), (0.5, [0, 0, 0])),
                                   "position": keys((0, [0, 0, 0]), (0.05, [0, 0.5, 3]), (0.5, [0, 0, 0]))},
                          "cock": {"rotation": keys((0, [0, 0, 0]), (0.03, [35, 0, 0]), (0.4, [0, 0, 0]))},
                          "charm": {"rotation": keys((0, [0, 0, 0]), (0.1, [-40, 0, 0]), (0.5, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 0.8, {"root": {"rotation": keys((0, [0, 0, 0]), (0.3, [-30, 0, 0]), (0.45, [8, 0, 0]), (0.8, [0, 0, 0])),
                                      "position": keys((0, [0, 0, 0]), (0.3, [0, 1.5, 1]), (0.8, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# 2 SERPENTSPINE LONGBOW (upright bow; limbs curve back toward the archer, +Z)
# =====================================================================================
def serpentspine_longbow():
    m = new("serpentspine_longbow", 22)
    c = m.cube
    TEAL = over(scales(["#0e3a44", "#15505a", "#1f6a72"], sw=2, sh=2), veins("#7ae8ff", 0.05, glow=True))
    FIN = over(pal("#1f6a6a", "#2a8a86", "#3aa8a0"), trim("#8af0e8", top=True))
    KELP = cloth(["#1f4a24", "#2a5e2e", "#34703a"])
    # bones for the DRAW (driven per frame from the real pull in RelicWeaponRenderer, see bow_pose):
    # limbs pivot at the grip ends, each string half hangs from its limb tip, the arrow sits on the nock
    m.bone("upper_limb", [0, 3, 0], "root")
    m.bone("lower_limb", [0, -3, 0], "root")
    m.bone("string_top", [0, 23, 6.5], "upper_limb")
    m.bone("string_bottom", [0, -23, 6.5], "lower_limb")
    m.bone("arrow", [0, 0, 6.5], "root")
    for k in range(-12, 13):
        if abs(k) < 2:
            continue
        t = k / 12.0
        y, z = k * 2, 6 * t * t
        limb = "upper_limb" if k > 0 else "lower_limb"
        c(limb, [-1, y, z - 1], [2, 2, 2], TEAL)
        if k % 2 == 0:
            c(limb, [-1.5, y, z - 1.5], [3, 1, 1], BONE)                           # vertebra knobs
            c(limb, [-0.5, y, z - 2.5], [1, 1, 1], BONE)                           # spines on the outer side
    for s in (-1, 1):                                                               # fin tips
        yt = s * 24
        for k, a in enumerate((-30, 0, 30)):
            c("upper_limb" if s > 0 else "lower_limb", [-0.5, yt, 6 - 1], [1, 4, 2], FIN,
              rot=[a + (0 if s > 0 else 180), 0, 0], pivot=[0, yt, 6])
    c("root", [-1.5, -3, -1.5], [3, 6, 3], KELP)                                    # grip, kelp-wrapped
    c("root", [-2, -2, -2.5], [4, 4, 1], BONE)                                      # the pearl's setting
    c("root", [-1, -1, -3], [2, 2, 1], lit("#c8f4ff"))                              # the Tide Pearl
    c("string_top", [0, 0, 6.5], [0, 23, 1], KELP)                                  # the string, in two halves
    c("string_bottom", [0, -23, 6.5], [0, 23, 1], KELP)
    c("arrow", [-0.5, -0.5, -12], [1, 1, 19], WOOD)                                  # the nocked tide arrow
    c("arrow", [-1, -1, -15], [2, 2, 3], over(pal("#6ad8c8", "#8af0e0"), trim("#e8fffa", top=True)))
    c("arrow", [-1.5, -0.5, 4], [3, 1, 3], FIN)                                      # fletching
    # the draw pose at full pull (for previews + the sign reference of RelicWeaponRenderer.bow_pose)
    anim(m, "draw_full", 1.0, {"upper_limb": {"rotation": keys((0, [-14, 0, 0]))},
                               "lower_limb": {"rotation": keys((0, [14, 0, 0]))},
                               "string_top": {"rotation": keys((0, [36, 0, 0]))},
                               "string_bottom": {"rotation": keys((0, [-36, 0, 0]))},
                               "arrow": {"position": keys((0, [0, 0, 10]))}}, loop=False)
    anim(m, "idle", 1.0, {"root": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, 0]))}})
    return m


# =====================================================================================
# 3 EVERBURNING FLAIL (upright handle, the cage hangs off the top)
# =====================================================================================
def everburning_flail():
    m = new("everburning_flail", 33)
    c = m.cube
    EMBER = over(plates(["#1e1a1a", "#2a2424", "#3a3232"], ph=3, pw=3), network("#ff7a1e", 0.08))
    c("root", [-1.5, -10, -1.5], [3, 20, 3], EMBER)                                 # handle
    for y in (-6, -3, 1, 4):
        band(m, y, 1.75, RED_CORD)
    c("root", [-2, -12, -2], [4, 2, 4], EMBER); c("root", [-0.5, -14, -0.5], [1, 2, 1], IRON)   # pommel + spike
    c("root", [-2, 10, -2], [4, 2, 4], EMBER)                                       # cap
    m.bone("chain_head", [0, 12, 0], "root")                                         # chain + cage swing from the cap
    cz, cy = -9, -1                                                                  # the burning cage
    m.bone("cage", [0, cy, cz], "chain_head")
    m.bone("flame", [0, cy, cz], "cage")
    pts = [(0, 13), (-2, 15), (-4.5, 15.5), (-7, 14), (-8.5, 11.5), (-9, 8.5), (-9, 5.5)]     # the chain arcs over, forward (-Z)
    chain_path(m, pts, over(RUST, spots("#ff7a1e", 0.2, glow=True)), "chain_head")
    c("flame", [-3, cy - 3, cz - 3], [6, 6, 6], lit("#ffb030"), art={"sides": A(["FYYF", "YWWY", "YWWY", "FYYF"], F="!ff6a10", Y="!ffb030", W="!fff0a0")})
    for dx in (-3.5, 2.5):
        for dz in (-3.5, 2.5):
            c("cage", [dx, cy - 3.5, cz + dz], [1, 7, 1], EMBER)
    c("cage", [-3.5, cy + 3, cz - 3.5], [7, 1, 7], EMBER); c("cage", [-3.5, cy - 4, cz - 3.5], [7, 1, 7], EMBER)
    c("cage", [-1, cy + 4, cz - 1], [2, 1, 2], EMBER)
    for (dx, dy, dz, r) in [(0, 0, -4, [90, 0, 0]), (0, 0, 4, [-90, 0, 0]), (4, 0, 0, [0, 0, -90]), (-4, 0, 0, [0, 0, 90]), (0, -5, 0, [180, 0, 0])]:
        c("cage", [-0.5 + dx, cy + dy, cz + dz - 0.5], [1, 2, 1], IRON, rot=r, pivot=[dx, cy + dy, cz + dz])
    anim(m, "idle", 2.4, {"chain_head": {"rotation": wv(2.4, lambda q: [7 * S(q), 0, 4 * C(q)])},
                          "flame": {"scale": wv(2.4, lambda q: [1 + 0.07 * S(3 * q), 1 + 0.1 * S(3 * q + 1), 1 + 0.07 * S(3 * q)])}})
    anim(m, "ability", 0.8, {"chain_head": {"rotation": keys((0, [0, 0, 0]), (0.25, [70, 0, 0]), (0.5, [-120, 0, 0]),
                                                             (0.62, [-100, 0, 0]), (0.8, [0, 0, 0]))},
                             "flame": {"scale": keys((0, [1, 1, 1]), (0.5, [1.4, 1.4, 1.4]), (0.8, [1, 1, 1]))}}, loop=False)
    return m


# =====================================================================================
# 4 DUTCHMAN'S HAND CANNON (gun)
# =====================================================================================
def dutchmans_hand_cannon():
    m = new("dutchmans_hand_cannon", 44)
    c = m.cube
    GHOSTWOOD = over(leather(["#1a3430", "#224540", "#2c5650"]), speckle("#0e1e1a", 0.1))
    BARREL = over(RUST, network("#6af8e8", 0.05), spots("#c8c0a8", 0.05))
    KELP = cloth(["#3a4a26", "#4a5e2e", "#5a7038"])
    c("root", [-2, -5, 1], [4, 6, 9], GHOSTWOOD, rot=[-8, 0, 0], pivot=[0, 0, 1])   # stock
    c("root", [-2, -6, 9.5], [4, 7, 1], IRON, rot=[-8, 0, 0], pivot=[0, 0, 1])
    c("root", [-1.5, -3, -3], [3, 4, 5], GHOSTWOOD)                                   # grip block
    c("root", [-0.5, -4, -4], [1, 1, 4], IRON)                                         # trigger guard
    c("root", [-2.5, -1, -20], [5, 5, 18], BARREL)                                     # the barrel
    for z in (-6, -12, -17):
        c("root", [-3, -1.5, z], [6, 6, 1], IRON)
    c("root", [-3.5, -2, -24], [7, 7, 4], BARREL, art={"north": A(["IIIIIII", "IGGGGGI", "IGWWWGI", "IGWWWGI", "IGWWWGI", "IGGGGGI", "IIIIIII"], I="#3e434a", G="!3ae8d8", W="!c8fff8")})
    c("root", [2, 1, -5], [1, 2, 4], IRON); c("root", [2.25, 3, -3], [1, 3, 1], IRON, rot=[-20, 0, 0], pivot=[2.75, 3, -2.5])  # lock
    for (x, y, z) in [(-3, 2, -9), (2.5, 3, -14), (-3, 0, -16), (2.5, -1, -8), (-1, 4, -11)]:
        c("root", [x, y, z], [1, 1, 1], BONE)                                          # barnacles
    for k, z in enumerate((-6, -9, -12, -15)):
        L = 3 + k % 2 * 2
        m.bone(f"kelp{k}", [0, -1, z + 0.5], "root")
        c(f"kelp{k}", [-0.5 + (k % 2), -1 - L, z], [1, L, 1], KELP)                    # seaweed
    m.bone("charm", [0, -5, 4.5], "root")
    for i in range(3):
        c("charm", [-0.25, -6 - i, 4], [1, 1, 1], RUST)                                # anchor chain + charm
    c("charm", [-0.5, -12, 4], [1, 3, 1], over(pal("#3a6a60", "#4e8270"), spots("#6af8e8", 0.2, glow=True)))
    c("charm", [-2, -12, 4], [4, 1, 1], pal("#3a6a60", "#4e8270"))
    idle = {"charm": {"rotation": wv(3.0, lambda q: [12 * S(q), 0, 5 * C(q)])}}
    for k in range(4):
        idle[f"kelp{k}"] = {"rotation": wv(3.0, lambda q, k=k: [14 * S(q + k * 1.3), 0, 8 * C(q + k)])}
    anim(m, "idle", 3.0, idle)
    anim(m, "fire", 0.6, {"root": {"rotation": keys((0, [0, 0, 0]), (0.06, [-20, 0, 0]), (0.6, [0, 0, 0])),
                                   "position": keys((0, [0, 0, 0]), (0.06, [0, 1, 3.5]), (0.6, [0, 0, 0]))},
                          "charm": {"rotation": keys((0, [0, 0, 0]), (0.12, [-45, 0, 0]), (0.6, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.0, {"root": {"rotation": keys((0, [0, 0, 0]), (0.25, [-40, 0, 0]), (0.75, [-40, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# 5 TRIDENT OF THE SUNKEN COURT (upright)
# =====================================================================================
def sunken_trident():
    m = new("sunken_trident", 55)
    c = m.cube
    SHAFT = over(pal("#12302c", "#1a403a", "#224e48"), spots("#c8aa40", 0.08))
    CORAL = pal("#8a2424", "#b03a30", "#d05a44")
    GLASS = over(pal("#6ad8c8", "#8af0e0", "#b8fff0"), trim("#e8fffa", top=True))
    PEARLM = pal("#dcd6c8", "#eee8dc", "#ffffff")
    c("root", [-1, -26, -1], [2, 42, 2], SHAFT)
    for y in (-20, -8, 4):
        band(m, y, 1.5, GOLD); c("root", [-1.5, y + 1, -1.5], [3, 2, 3], PEARLM); band(m, y + 3, 1.5, GOLD)
    for k, (y, z, a) in enumerate([(-14, -1, -40), (-2, 1, 40), (8, -1, -35), (-24, 1, 30)]):
        m.bone(f"coral{k}", [0, y, z], "root")
        c(f"coral{k}", [-0.5, y, z - 0.5], [1, 4, 1], CORAL, rot=[a, 0, 0], pivot=[0, y, z])
    c("root", [-1.5, -30, -1.5], [3, 4, 3], GLASS, rot=[180, 0, 0], pivot=[0, -26, 0])  # butt spike
    c("root", [-6.5, 16, -1.5], [13, 2, 3], GOLD)                                        # crossbar
    c("root", [-3, 15, -2.25], [6, 6, 1], GOLD, art={"north": A(["GGGGGG", "GTTTTG", "GT_T_T", "GTTTTG", "GGTTGG", "GGGGGG"], G="#d4a52a", T="!3affd8")})
    for x, L in ((-5.5, 8), (0, 12), (5.5, 8)):
        c("root", [x - 0.75, 18, -0.75], [1.5 * 0 + 2, L, 2], GOLD)
        c("root", [x - 1.5, 18 + L, -1.5], [3, 5, 3], GLASS)
        c("root", [x - 0.5, 23 + L, -0.5], [1, 2, 1], GLASS)
    for x in (-3, 3):
        c("root", [x - 0.5, 18, -0.5], [1, 6, 1], CORAL, rot=[0, 0, 20 if x < 0 else -20], pivot=[x, 18, 0])
        c("root", [x - 0.5 + (1 if x < 0 else -1), 22, -0.5], [1, 3, 1], CORAL, rot=[0, 0, -40 if x < 0 else 40], pivot=[x, 22, 0])
    anim(m, "idle", 3.2, {f"coral{k}": {"rotation": wv(3.2, lambda q, k=k: [12 * S(q + k * 1.6), 0, 6 * C(q + k)])} for k in range(4)})
    anim(m, "dash", 0.6, {"root": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 360, 0]), (0.6, [0, 720, 0]))}}, loop=False)
    return m


# =====================================================================================
# 6 BLOODFIN'S MAW (upright saw-cleaver, teeth on the front edge)
# =====================================================================================
def bloodfin_maw():
    m = new("bloodfin_maw", 66)
    c = m.cube
    SKIN = over(pal("#2e2e34", "#3a3a42", "#4a4a52"), veins("#8a1a1a", 0.05, glow=False), speckle("#5a5a64", 0.08))
    TOOTH = over(pal("#d8ccb0", "#e8e0cc", "#f6f0e2"), trim("#a01818", top=False, bottom=True))
    GRIP = leather(["#141414", "#1e1e1e", "#2a2a2a"])
    c("root", [-1.5, -11, -1.5], [3, 11, 3], GRIP)
    for y in (-9, -5, -2):
        band(m, y, 1.75, IRON)
    c("root", [-1, -15, -1], [2, 4, 2], TOOTH, rot=[180, 0, 0], pivot=[0, -11, 0])    # the pommel tooth
    c("root", [-2.5, 0, -4.5], [5, 2, 9], over(BONE, spots("#3a3a3a", 0.1)))          # studded bone guard
    c("root", [-1, 2, -4], [2, 30, 8], SKIN, art={"east": A.at(8, 30, {(0, y): "R" for y in range(0, 30, 3)}, R="#9a1818")})
    c("root", [-1.25, 2, 3.5], [2, 28, 1], BONE)                                       # the jawbone spine
    c("root", [-0.75, 31, -3], [1.5 * 0 + 2, 3, 5], SKIN, rot=[20, 0, 0], pivot=[0, 31, 0])
    for k in range(9):                                                                  # the teeth
        y = 3.5 + k * 3.2
        m.bone(f"tooth{k}", [0, y + 1, -5], "root")
        c(f"tooth{k}", [-0.75, y, -6], [1.5 * 0 + 2, 2, 2], TOOTH)
        c(f"tooth{k}", [-0.5, y + 0.5, -7.5], [1, 1, 2], TOOTH)
    for y, L in ((0, 5), (-0.5, 3)):
        c("root", [-3, y - L, -1], [1, L, 1], pal("#7a1010", "#9a1818"))                # blood drips off the guard
    # teeth ripple up the blade like a saw; FRENZY (6 s) they chatter
    anim(m, "idle", 2.0, {f"tooth{k}": {"position": wv(2.0, lambda q, k=k: [0, 0, -0.35 * max(0, S(q - k * 0.7))])} for k in range(9)})
    anim(m, "ability", 6.0, {f"tooth{k}": {"position": wv(6.0, lambda q, k=k: [0, 0, -0.9 * abs(S(15 * q - k * 1.1))], 180),
                                          "rotation": wv(6.0, lambda q, k=k: [10 * S(15 * q - k * 1.1), 0, 0], 180)} for k in range(9)}, loop=False)
    return m


# =====================================================================================
# 7 KRAKENMAW HARPOON GUN (gun)
# =====================================================================================
def krakenmaw_harpoon_gun():
    m = new("krakenmaw_harpoon_gun", 77)
    c = m.cube
    FLESH = over(scales(["#2a1238", "#3a1a4e", "#4e2466", "#62307a"], sw=2, sh=2), spots("#8a4aa0", 0.05))
    SUCK = pal("#e0b8c8", "#f0d0dc")
    BEAK = over(pal("#141218", "#1e1a24", "#2a2632"), trim("#4a4656", top=True))
    INK = pal("#3a1060", "#4e1680")
    c("root", [-1.5, -3, 2], [3, 4, 8], WOOD)                                          # stock
    for z in (3.5, 6.5):
        c("root", [-1.75, -3.25, z], [3.5 * 0 + 4, 4.5 * 0 + 5, 1], leather(["#6a4a24", "#8a6a36"]))
    c("root", [-1.5, -6, -2], [3, 6, 3], FLESH, rot=[-15, 0, 0], pivot=[0, 0, -1])     # grip wrapped in tentacle
    c("root", [-0.5, -3, -4], [1, 1, 3], IRON)
    c("root", [-2.5, -1, -16], [5, 6, 18], FLESH,
      art={"east": A.at(16, 6, {**{(x, 1): "S" for x in range(2, 14, 2)}, (4, 3): "E", (5, 3): "E", (4, 4): "E", (5, 4): "E"}, S="#f0d0dc", E="!d060ff"),
           "west": A.at(16, 6, {**{(x, 1): "S" for x in range(2, 14, 2)}, (10, 3): "E", (11, 3): "E", (10, 4): "E", (11, 4): "E"}, S="#f0d0dc", E="!d060ff")})
    c("root", [-0.5, 5, -14], [1, 1, 12], leather(["#6a4a24"]))                        # the rope rail
    m.bone("beak_upper", [0, 4, -16], "root")
    m.bone("beak_lower", [0, 0, -16], "root")
    m.bone("harpoon", [0, 2, -20], "root")
    c("beak_upper", [-1.5, 3, -21], [3, 2, 6], BEAK, rot=[22, 0, 0], pivot=[0, 4, -16])      # the upper beak
    c("beak_lower", [-1.5, -1, -21], [3, 2, 6], BEAK, rot=[-22, 0, 0], pivot=[0, 0, -16])    # the lower beak
    c("harpoon", [-0.5, 1.5, -24], [1, 1, 10], WOOD)                                         # the loaded harpoon
    c("harpoon", [-1, 1, -28], [2, 2, 4], over(IRON, spots("#6a2a8a", 0.2)))
    c("harpoon", [-2, 1.5, -26], [4, 1, 1], IRON)                                            # barbs
    for (y, z, L) in [(-1, -18, 3), (-1.5, -14, 2), (-1, -9, 4), (-1.5, -21, 2)]:
        c("root", [-0.5 + (z % 2), y - L, z], [1, L, 1], INK)                          # dripping ink
    for k, (z, a) in enumerate(((-6, 20), (-11, -15))):
        m.bone(f"tentacle{k}", [0, 2, z + 1], "root")
        c(f"tentacle{k}", [-3, -1.5, z], [6, 1, 2], FLESH, rot=[0, 0, a], pivot=[0, 2, z])     # tentacles coiling round the body
    anim(m, "idle", 3.0, {"beak_upper": {"rotation": wv(3.0, lambda q: [-5 * max(0, S(q)), 0, 0])},
                          "beak_lower": {"rotation": wv(3.0, lambda q: [5 * max(0, S(q)), 0, 0])},
                          "tentacle0": {"rotation": wv(3.0, lambda q: [0, 0, 8 * S(q)])},
                          "tentacle1": {"rotation": wv(3.0, lambda q: [0, 0, -8 * S(q + 1.5)])}})
    anim(m, "fire", 1.2, {"beak_upper": {"rotation": keys((0, [0, 0, 0]), (0.05, [-30, 0, 0]), (0.25, [-30, 0, 0]), (0.4, [0, 0, 0]))},
                          "beak_lower": {"rotation": keys((0, [0, 0, 0]), (0.05, [30, 0, 0]), (0.25, [30, 0, 0]), (0.4, [0, 0, 0]))},
                          "harpoon": {"position": keys((0, [0, 0, 0]), (0.1, [0, 0, -24]), (0.11, [0, 0, 6]), (1.0, [0, 0, 6]), (1.2, [0, 0, 0])),
                                      "scale": keys((0, [1, 1, 1]), (0.1, [1, 1, 1]), (0.11, [0, 0, 0]), (0.9, [0, 0, 0]), (1.2, [1, 1, 1]))},
                          "root": {"rotation": keys((0, [0, 0, 0]), (0.05, [-10, 0, 0]), (0.5, [0, 0, 0])),
                                   "position": keys((0, [0, 0, 0]), (0.05, [0, 0, 2]), (0.5, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.0, {"beak_upper": {"rotation": keys((0, [0, 0, 0]), (0.15, [-35, 0, 0]), (0.7, [-35, 0, 0]), (1.0, [0, 0, 0]))},
                             "beak_lower": {"rotation": keys((0, [0, 0, 0]), (0.15, [35, 0, 0]), (0.7, [35, 0, 0]), (1.0, [0, 0, 0]))},
                             "tentacle0": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 30]), (1.0, [0, 0, 0]))},
                             "tentacle1": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, -30]), (1.0, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# 8 CHAINBREAKER (upright chain-sickle; the chain + broken shackle hang off the pommel)
# =====================================================================================
def chainbreaker():
    m = new("chainbreaker", 88)
    c = m.cube
    ROPE = over(pal("#6a5030", "#8a6a3e", "#a8854c"), spots("#4a3418", 0.12))
    EDGE = over(pal("#8a8078", "#b0a8a0", "#d0c8c0"), speckle("#6a4a3a", 0.1))
    MIST = over(RUST, spots("#8ae0b0", 0.12, glow=True))
    c("root", [-1.5, -10, -1.5], [3, 13, 3], ROPE)
    band(m, -10.5, 1.75, IRON, h=2); band(m, 2, 1.75, IRON, h=2)
    m.bone("sickle", [0, 4, 0], "root")
    m.bone("chain", [0, -11, 0], "root")
    c("sickle", [-1, 4, -10], [2, 3, 12], over(RUST, speckle("#9a6a4a", 0.1)))          # the sickle arm, forward
    c("sickle", [-1, -1, -13], [2, 7, 3], RUST, rot=[18, 0, 0], pivot=[0, 5, -11])      # hooking down
    c("sickle", [-0.5, -5, -13.5], [1, 5, 2], EDGE, rot=[30, 0, 0], pivot=[0, 0, -12])  # the blade tip
    for z in (-3, -6, -9):
        c("sickle", [-0.5, 2.5, z], [1, 2, 1], EDGE)                                     # teeth under the arm
    for k, z in enumerate((-1, -4)):
        c("root", [-1 + k, 0, z], [1, 4, 1], ROPE)                                     # frayed rope tassels
    pts = [(0, -12), (1, -14), (2.5, -16), (3.5, -18), (4, -20), (4, -22)]
    chain_path(m, pts, MIST, "chain")
    cx, cy = 4, -26                                                                     # the broken shackle
    c("chain", [-1, cy - 3, cx - 3], [2, 1, 6], MIST); c("chain", [-1, cy + 2, cx - 3], [2, 1, 6], MIST)
    c("chain", [-1, cy - 2, cx - 3], [2, 4, 1], MIST)
    c("chain", [-1, cy - 2, cx + 2], [2, 2, 1], MIST)                                    # (the other side snapped)
    anim(m, "idle", 2.6, {"chain": {"rotation": wv(2.6, lambda q: [9 * S(q), 0, 6 * C(q)])}})
    anim(m, "ability", 0.7, {"sickle": {"position": keys((0, [0, 0, 0]), (0.12, [0, 0, -6]), (0.5, [0, 0, 0])),
                                        "rotation": keys((0, [0, 0, 0]), (0.12, [15, 0, 0]), (0.5, [0, 0, 0]))},
                             "chain": {"rotation": keys((0, [0, 0, 0]), (0.18, [100, 0, 0]), (0.7, [0, 0, 0]))}}, loop=False)
    return m


# =====================================================================================
# 9 HEARTSEEKER (upright greatsword, a beating heart in the guard)
# =====================================================================================
def heartseeker():
    m = new("heartseeker", 99)
    c = m.cube
    STONE = over(pal("#1a0a0c", "#2a1014", "#3a161a"), network("#ff2a2a", 0.07), network("#ff8080", 0.015))
    HEART = over(pal("#5a1014", "#7a1a1e", "#9a2428"), veins("#ff3a3a", 0.08, glow=True))
    GRIP = over(cloth(["#3a0c10", "#4e1216"]), spots("#1a0a0a", 0.1))
    c("root", [-1.5, -12, -1.5], [3, 12, 3], GRIP)
    for y in (-11, -6, -1):
        band(m, y, 1.75, GOLD)
    c("root", [-2, -16, -2], [4, 4, 4], GOLD, art={"sides": A(["GBBG", "BRRB", "BRRB", "GBBG"], G="#d4a52a", B="#d2c4a4", R="!ff2a2a")})
    c("root", [-9, 0, -1.5], [18, 2, 3], GOLD)                                           # guard
    for x in (-10, 8):
        c("root", [x, -0.5, -2], [2, 3, 4], lit("#ff2a2a"))                              # its gem tips
    # heart_beat: scaled every frame on the REAL beat (RelicWeaponRenderer); heart: the systole clip
    m.bone("heart_beat", [0, 3, 0], "root")
    m.bone("heart", [0, 3, 0], "heart_beat")
    c("heart", [-3, 0, -3], [6, 6, 6], HEART)                                              # the heart
    for x in (-3.5, 2.5):
        c("heart", [x, -0.5, -3.5], [1, 7, 7], GOLD)                                        # gold straps
    for x, a in ((-4, 25), (3, -25)):
        c("root", [x, 1, -1], [1, 6, 2], BONE, rot=[0, 0, a], pivot=[x + 0.5, 1, 0])       # bone ribs round it
    c("root", [-1.5, 6, -4], [3, 32, 8], STONE)                                            # the blade
    for y in range(8, 36, 5):
        c("root", [-1, y, -5], [2, 2, 1], STONE); c("root", [-1, y + 2, 4], [2, 2, 1], STONE)   # jagged edges
    c("root", [-1, 38, -3], [2, 3, 6], STONE); c("root", [-0.5, 41, -1.5], [1, 3, 3], STONE)
    for (x, L) in ((-6, 5), (-2, 8), (4, 6)):
        c("root", [x, 0 - L, -0.5], [1, L, 1], pal("#6a0a0e", "#8a1014"))                    # blood dripping off the guard
    anim(m, "idle", 1.0, {"root": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, 0]))}})
    anim(m, "ability", 1.2, {"heart": {"scale": keys((0, [1, 1, 1]), (0.3, [0.8, 0.8, 0.8]), (0.9, [1.55, 1.55, 1.55]), (1.2, [1, 1, 1]))}}, loop=False)
    return m


# =====================================================================================
# 10 THE TIDEFATHER'S WRATH (colossal glaive, the fin blade sweeping forward)
# =====================================================================================
def tidefathers_wrath():
    m = new("tidefathers_wrath", 1010)
    c = m.cube
    SHAFT = over(scales(["#082822", "#0e3a30", "#144a3e"], sw=2, sh=2), network("#ffcc3a", 0.05))
    FIN = over(scales(["#0e3a30", "#16503f", "#1f6a52", "#2a7a60"], sw=2, sh=2), veins("#ffd24a", 0.03, glow=True))
    GOLDG = over(GOLD, spots("#fff0a0", 0.08, glow=True))
    KELP = cloth(["#12362c", "#1a4a3a", "#256050"])
    c("root", [-1, -30, -1], [2, 50, 2], SHAFT)
    for y in (-26, -8, 8):
        band(m, y, 1.5, GOLD, h=2)
    c("root", [-1.5, -35, -1.5], [3, 5, 3], GOLDG)                                          # the butt ornament
    c("root", [-1, -34, -1.5], [2, 2, 1], lit("#ffcc3a"))
    for a in (-35, 35):
        c("root", [-0.5, -34, -0.5], [1, 4, 1], GOLD, rot=[a, 0, 0], pivot=[0, -32, 0])
    c("root", [-2.5, 18, -3], [5, 6, 6], GOLDG)                                              # the eye's setting
    m.bone("eye", [0, 21, -3.5], "root")
    m.bone("fin", [0, 20, -2], "root")
    c("eye", [-1.5, 19, -3.5], [3, 4, 1], lit("#ffcc3a"), art={"north": A(["YOOY", "ODDO", "ODDO", "YOOY"], Y="!ffcc3a", O="!fff0a0", D="#2a1400")})
    for y in range(20, 48, 2):                                                                 # the great crescent fin blade
        t = (y - 20) / 27.0
        outer = -3 - math.sin(t * math.pi * 0.85) * 14 - t * 5                                    # its leading (sharp) edge
        w = max(2, round(9 * (1 - t) + 2))
        c("fin", [-1, y, outer + 1], [2, 2, w], FIN)
        c("fin", [-1.25, y, outer], [2, 2, 1], lit("#f2cf5a"))                                    # the glowing gold edge
    for k, (y, L, a) in enumerate([(24, 8, 40), (27, 10, 30), (30, 8, 20)]):                   # fin spikes behind
        c("fin", [-0.5, y, 1], [1, L, 2], over(FIN, trim("#f2cf5a", top=True)), rot=[a, 0, 0], pivot=[0, y, 2])
    for k, (z, L) in enumerate(((-2, 7), (0, 10), (2, 6))):
        m.bone(f"kelp{k}", [0, 18, z + 0.5], "root")
        c(f"kelp{k}", [-0.5, 18 - L, z], [1, L, 1], KELP)                                      # kelp hanging from the head
    idle = {f"kelp{k}": {"rotation": wv(4.0, lambda q, k=k: [10 * S(q + k * 1.4), 0, 7 * C(q + k * 0.9)])} for k in range(3)}
    idle["eye"] = {"scale": keys((0, [1, 1, 1]), (3.5, [1, 1, 1]), (3.62, [1, 0.1, 1]), (3.75, [1, 1, 1]), (4.0, [1, 1, 1]))}
    idle["fin"] = {"rotation": wv(4.0, lambda q: [2.5 * S(q), 0, 0])}
    anim(m, "idle", 4.0, idle)
    anim(m, "ability", 1.2, {"fin": {"rotation": keys((0, [0, 0, 0]), (0.3, [-18, 0, 0]), (0.7, [10, 0, 0]), (1.2, [0, 0, 0])),
                                     "scale": keys((0, [1, 1, 1]), (0.3, [1.15, 1.15, 1.15]), (1.2, [1, 1, 1]))},
                             "eye": {"scale": keys((0, [1, 1, 1]), (0.3, [1.5, 1.5, 1.5]), (1.2, [1, 1, 1]))}}, loop=False)
    return m


BUILDERS = {f.__name__: f for f in (rackham_blunderbuss, serpentspine_longbow, everburning_flail, dutchmans_hand_cannon,
                                    sunken_trident, bloodfin_maw, krakenmaw_harpoon_gun, chainbreaker, heartseeker,
                                    tidefathers_wrath)}


def preview(name):
    out = HERE / "previews/weapons"; out.mkdir(parents=True, exist_ok=True)
    frames = [preview_geo.render(name, yaw=y, pitch=p, size=360) for y, p in ((90, 5), (-90, 5), (40, 20))]
    sheet = Image.new("RGB", (360 * 3, 360))
    for i, f in enumerate(frames):
        sheet.paste(f, (360 * i, 0))
    sheet.save(out / f"{name}.png")


if __name__ == "__main__":
    for n in sys.argv[1:] or BUILDERS:
        m = BUILDERS[n]()
        m.write()
        preview(n)
        print(f"{n}: {len(m.cubes)} cubes, texture {m.tw}x{m.th}")
