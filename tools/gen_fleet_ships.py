"""THE FLEET (2026-10-04): three more ships for each faction, built with the ship kit in gen_ship_blueprints.py.

    python tools/gen_fleet_ships.py [name ...]

Each faction now sails a ladder of four hulls (small -> flagship); the zone each one appears in is set in
world/AiShipConfig.generateDefaults (minZone/maxZone) and its commission zone in world/ShipTiers.

  PIRATES   pirate_cutter (z1)  corsair_xebec (z2)   pirate_brig (z3)          pirate_galleon (z4-5)
  MERCHANTS merchant_lugger(z1) merchantman (z1-2)   merchant_fluyt (z2-3)     merchant_indiaman (z3-5)
  NAVY      navy_cutter (z1-2)  navy_corvette (z2-3) navy_frigate (z4)         navy_man_o_war (z5)
  UNDEAD    undead_wraith (z3)  drowned_hulk (z4)    undead_bone_galley (z3-4) undead_phantom_galleon (z5)

Ships hand-edited in game with /ppship capture (ships/captured.txt) are skipped unless --force.
Same rules as the kit: the helm is (0,0,0) on the deck, the bow points +Z, every block face-connected to the helm,
a ship_waterline at sea level, speed = ship_mast blocks vs mass, trim() balances the centre of mass on the keel line.
"""
import sys

import gen_ship_blueprints as g
from gen_ship_blueprints import Ship, B, stair, slab, rails, cannon, essentials, preview

g.CONFIGS = [g.ROOT / "run/config/pixelpirates/ships"]           # never the user's own server

# ------------------------------------------------------------------------------------------------ shared pieces
def deck_w(s, z):
    """Half-width of the deck at z (or -1 outside the hull)."""
    return s.hw[z][max(s.hw[z])] if z in s.hw else -1


def paint(s, base, bands):
    """Repaint the hull planks by height: bands = {y: block}."""
    for (x, y, z), st in list(s.b.items()):
        if st == base and y in bands: s.set(x, y, z, bands[y])


def patchwork(s, base, others, chance):
    """Swap a share of the hull planks for odd boards (a patched-up pirate hull)."""
    for (x, y, z), st in list(s.b.items()):
        if st == base and s.r.random() < chance: s.set(x, y, z, s.r.choice(others))


def waterline(s, y, z):
    """The ship_waterline in the port side planking at height y (= sea level)."""
    w = s.hw[z][y]
    s.set(-w, y, z, B("pixelpirates:ship_waterline"))


def castle(s, z0, z1, y0, h, wall, roof, rim, fence, windows=(), stern_windows=(), door=True, post=None):
    """A deckhouse/sterncastle from z0 (aft) to z1 (fore) following the hull: walls y0..y0+h-1, roof y0+h, rail on top.
    windows: (y, z) pairs on both sides; stern_windows: (y, x) pairs in the aft wall; post: corner post block."""
    top = y0 + h
    for z in range(z0, z1 + 1):
        w = deck_w(s, z)
        if w < 0: continue
        for x in range(-w, w + 1):
            for y in range(y0, top):
                if abs(x) == w or z in (z0, z1):
                    st = wall
                    if post and abs(x) == w and z in (z0, z1): st = post
                    if abs(x) == w and (y, z) in windows: st = B("glass_pane")
                    if z == z0 and (y, abs(x)) in stern_windows: st = B("glass_pane")
                    s.set(x, y, z, st)
            s.set(x, top, z, roof if abs(x) < w else rim)
        s.put_if_air(-w, top + 1, z, fence); s.put_if_air(w, top + 1, z, fence)
    for x in range(-deck_w(s, z0), deck_w(s, z0) + 1): s.put_if_air(x, top + 1, z0, fence)
    if door:
        s.set(0, y0, z1, None); s.set(0, y0 + 1, z1, None)
    return top


def ladder_up(s, x, z_face, y0, y1):
    """A ladder on the fore face of a castle (the wall at z_face), climbing from y0 to y1."""
    for y in range(y0, y1 + 1): s.set(x, y, z_face + 1, B("ladder", facing="south", waterlogged="false"))


def gaff(s, z, base, height, back, sail, spar, patch=None):
    """A fore-and-aft gaff sail AFT of the mast at z: a boom along the foot (y = base), a gaff rising aft along the top,
    the sail filling between. patch(z, y) may return a block to swap in (an emblem)."""
    for i in range(0, back + 1):
        zz = z - i
        topy = base + height - 2 + round(2 * i / max(1, back))
        if (0, base, zz) not in s.b: s.set(0, base, zz, spar)
        if (0, topy, zz) not in s.b: s.set(0, topy, zz, spar)
        for y in range(base + 1, topy):
            if (0, y, zz) not in s.b: s.set(0, y, zz, (patch(zz, y) if patch else None) or sail)


def jib(s, z0, y0, n, sail):
    """A triangular headsail from the foremast (at z0 - 1) forward over the bow: tall at the mast, short at the tip."""
    for i in range(n):
        for y in range(y0, y0 + n + 1 - i): s.put_if_air(0, y, z0 + i, sail)


def square_rig(s, z, base, top, courses, sail, yard, pattern=None, cap=None):
    s.mast(z, base, top)
    for (y0, y1, half) in courses:
        s.square_sail(z, y0, y1, half, sail, yard=yard, belly=True, pattern=pattern)
    if cap:
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)): s.set(dx, top - 2, z + dz, cap)


def flag(s, z, y, cols, mast_top=True):
    """A pennant streaming aft from the top of the mast at z."""
    if mast_top: s.set(0, y, z, B("pixelpirates:ship_mast", axis="y"))
    for i, c in enumerate(cols): s.set(0, y, z - 1 - i, B(c))


def ensign(s, z_stern, y_base, pole, rows):
    """A flagstaff over the stern carrying a flag: rows top-down, each a list of colours streaming aft."""
    h = len(rows)
    for y in range(y_base, y_base + h + 2): s.set(0, y, z_stern - 1, pole)
    for r, row in enumerate(rows):
        for i, c in enumerate(row): s.set(0, y_base + h + 1 - r, z_stern - 2 - i, B(c))


def guns(s, y, zs, half=None):
    for z in zs:
        w = half if half is not None else s.hw[z].get(y, deck_w(s, z))
        for x in (-w, w): s.set(x, y, z, None); cannon(s, x, y, z)


def gun_deck(s, floor, z0, z1, plank):
    for z in range(z0, z1 + 1):
        w = s.hw.get(z, {}).get(floor + 1, -1)
        for x in range(-w + 1, w): s.set(x, floor, z, plank)


def lantern(s, x, y, z, hanging=False, soul=False):
    s.set(x, y, z, B("soul_lantern" if soul else "lantern", hanging="true" if hanging else "false", waterlogged="false"))


def bow(s, z_tip, y_sprit, n, every, spar, figure, sail=None, jib_n=0):
    """Bowsprit + figurehead (+ a jib from the sprit up toward the foremast)."""
    tip = s.sprit(y_sprit, z_tip + 1, n, every, spar)
    s.set(0, y_sprit - 1, z_tip + 1, B("pixelpirates:" + figure, facing="south") if figure else spar)   # (links the sprit to the stem)
    if sail and jib_n:                                    # each column stands on the sprit (so it stays connected)
        for i in range(jib_n):
            z = z_tip + 1 + i
            ys = max(y for (x, y, zz) in s.b if x == 0 and zz == z and y <= tip)
            for y in range(ys + 1, tip + jib_n + 1 - i): s.put_if_air(0, y, z, sail)
    return tip


W_CANVAS, BLACK, CRIMSON, ROGER, STRIPE, SPECTRAL = (B("pixelpirates:white_sail_canvas"), B("pixelpirates:black_sail_canvas"),
    B("pixelpirates:crimson_sail_canvas"), B("pixelpirates:jolly_roger_sail_canvas"), B("pixelpirates:striped_sail_canvas"),
    B("pixelpirates:spectral_sail"))


def roger_patch(cx_lo, cx_hi, ys):
    """Pattern: the Jolly Roger canvas in a block of the sail, plain sail elsewhere (returns None = use the sail)."""
    return lambda x, y: ROGER if cx_lo <= x <= cx_hi and y in ys else None


def pat(base, special):
    return lambda x, y: special(x, y) or base


# ================================================================================================ PIRATES
def pirate_cutter():
    """THE BILGE RAT - a patched-up single-masted cutter, the first pirate a new captain meets: a hull of odd boards
    (dark oak patched with spruce and oak), a red strake, a big black gaff mainsail with the Jolly Roger, a black topsail,
    a jib, two guns a side, a skull on the bow. Small, quick, fragile."""
    s = Ship("pirate_cutter", "pirates", 101)
    P = B("dark_oak_planks")
    def width(z):
        if z < -5: return -1
        if z == -5: return 1
        if z >= 8: return max(0, 2 - (z - 8))
        return 2
    s.hull(-5, 10, width, lambda z: -3 if -3 <= z <= 7 else -2, 0, P, "dark_oak_stairs", rim=B("stripped_dark_oak_log", axis="z"), bilge=1.4)
    paint(s, P, {-1: B("red_terracotta")})
    patchwork(s, P, [B("spruce_planks"), B("oak_planks"), B("pixelpirates:destroyed_planks")], 0.22)
    waterline(s, -1, 2)
    essentials(s, anchor=(1, 1, 8), bed=(-1, 1, -4))
    guns(s, 1, (0, 5))
    rails(s, -5, 9, 1, B("dark_oak_fence"))
    s.set(0, 1, -5, B("dark_oak_fence")); lantern(s, 0, 2, -5)
    s.set(1, 1, -4, B("barrel", facing="up", open="false")); s.set(1, 1, -3, B("pixelpirates:rope_coil", facing="north"))
    s.mast(3, -2, 13)
    gaff(s, 3, 3, 8, 6, BLACK, B("dark_oak_fence"), patch=lambda z, y: ROGER if -1 <= z <= 1 and 6 <= y <= 8 else None)
    s.square_sail(3, 10, 12, 2, BLACK, yard=B("dark_oak_fence"), belly=False)
    jib(s, 4, 3, 5, BLACK)
    flag(s, 3, 14, ["black_wool", "red_wool"])
    bow(s, 10, 1, 4, 2, B("stripped_dark_oak_log", axis="z"), "dread_skull_figurehead")
    s.save()
    return s


def pirate_brig():
    """THE BLACKHEART - a two-masted pirate brig: a tarred black bottom, a crimson strake, a stern cabin with a glass
    gallery and the captain's table, square black sails stacked on both masts (the main course carries the Jolly Roger),
    a kraken writhing on the bow, four guns a side. The pirates' workhorse of the middle seas."""
    s = Ship("pirate_brig", "pirates", 102)
    P = B("dark_oak_planks")
    def width(z):
        if z < -10: return -1
        if z <= -8: return 2 + (z + 10)
        if z >= 14: return max(0, 4 - (z - 14))
        return 4
    s.hull(-10, 18, width, lambda z: -5 if -7 <= z <= 13 else -4, 0, P, "dark_oak_stairs", rim=B("stripped_dark_oak_log", axis="z"), bilge=2.0)
    paint(s, P, {-1: B("red_nether_bricks"), -4: B("blackstone"), -5: B("blackstone")})
    waterline(s, -2, 2)
    top = castle(s, -10, -3, 1, 3, P, B("spruce_planks"), B("stripped_dark_oak_log", axis="z"), B("dark_oak_fence"),
                 windows=((2, -8), (2, -5)), stern_windows=((2, 1), (2, 2)), post=B("stripped_dark_oak_log", axis="y"))
    for x in (-3, 3): ladder_up(s, x, -3, 1, top)
    s.set(-2, 1, -8, B("pixelpirates:map_block")); s.set(2, 1, -8, B("pixelpirates:sea_chest", facing="west"))
    s.set(0, 1, -9, B("pixelpirates:captains_chair", facing="south")); lantern(s, 0, 3, -6, hanging=True)
    s.set(-2, 1, -5, B("pixelpirates:ship_bedroll"))
    for x in (-2, 2): lantern(s, x, top + 1, -10)
    essentials(s, anchor=(-2, 1, 15))
    guns(s, 1, (0, 4, 8, 12))
    rails(s, -2, 17, 1, B("dark_oak_fence"))
    for (x, z) in ((2, -1), (-2, 6), (2, 6)): s.set(x, 1, z, B("barrel", facing="up", open="false"))
    s.set(-1, 1, 10, B("pixelpirates:rope_coil", facing="north"))
    yard = B("dark_oak_fence")
    square_rig(s, 11, -4, 19, [(3, 8, 4), (10, 14, 3)], BLACK, yard)
    square_rig(s, 3, -4, 22, [(3, 9, 5), (11, 17, 4)], BLACK, yard, pattern=pat(BLACK, roger_patch(-1, 1, range(5, 8))))
    s.shrouds(4, 9, 2, 6); s.shrouds(12, 8, 2, 5)
    flag(s, 3, 23, ["black_wool", "red_wool", "black_wool"])
    ensign(s, -10, top + 1, B("dark_oak_fence"), [["black_wool", "black_wool", "black_wool"], ["black_wool", "white_wool", "black_wool"], ["black_wool", "black_wool", "black_wool"]])
    bow(s, 18, 1, 6, 3, B("stripped_dark_oak_log", axis="z"), "kraken_figurehead", BLACK, 5)
    s.save()
    return s


def pirate_galleon():
    """THE DREAD GALLEON - the pirate flagship: a towering two-storey sterncastle with a gilded double gallery, a closed
    gun deck (six guns a side behind a crimson band) and four more on the weather deck, a raised forecastle, crimson and
    black square sails on fore and main (the Jolly Roger across the main course), a crimson lateen on the mizzen, the
    black flag over the stern, a dread skull on the beak."""
    s = Ship("pirate_galleon", "pirates", 103)
    P = B("dark_oak_planks"); GOLD = B("gold_block")
    def width(z):
        if z < -14: return -1
        if z <= -12: return 3 + (z + 14)
        if z >= 19: return max(0, 5 - (z - 19))
        return 5
    s.hull(-14, 24, width, lambda z: -7 if -10 <= z <= 17 else -6, 0, P, "dark_oak_stairs", rim=B("stripped_dark_oak_log", axis="z"), bilge=2.4)
    paint(s, P, {-1: B("red_nether_bricks"), -2: B("red_nether_bricks"), -5: B("blackstone"), -6: B("blackstone"), -7: B("blackstone")})
    waterline(s, -4, 2)
    gun_deck(s, -3, -11, 17, B("spruce_planks"))
    guns(s, -2, (-8, -4, 0, 4, 8, 12))
    for z in (-6, 2, 10): lantern(s, 0, -1, z, hanging=True)
    s.set(3, 0, 6, None); s.set(3, -1, 6, B("ladder", facing="north", waterlogged="false")); s.set(3, -2, 6, B("ladder", facing="north", waterlogged="false"))
    s.set(3, -1, 7, B("spruce_planks")); s.set(3, -2, 7, B("spruce_planks"))
    # the sterncastle: lower storey z-14..-4, upper storey z-14..-9, a gilded double gallery
    WOOD = B("stripped_dark_oak_log", axis="z")
    t1 = castle(s, -14, -4, 1, 3, P, B("spruce_planks"), WOOD, B("dark_oak_fence"), windows=((2, -11), (2, -7)),
                stern_windows=((2, 1), (2, 2), (2, 3)), post=GOLD)
    t2 = castle(s, -14, -9, t1 + 1, 3, B("red_nether_bricks"), B("spruce_planks"), WOOD, B("dark_oak_fence"), windows=((t1 + 2, -12),),
                stern_windows=((t1 + 2, 0), (t1 + 2, 1), (t1 + 2, 2)), post=GOLD, door=True)
    for x in (-4, 4): ladder_up(s, x, -4, 1, t1)
    for x in (-3, 3): ladder_up(s, x, -9, t1 + 1, t2)
    for x in range(-3, 4): s.set(x, t1, -15, slab("dark_oak_slab", "top")); s.set(x, t1 + 1, -15, B("dark_oak_fence"))   # the gallery
    for x in (-3, 3): lantern(s, x, t1 + 2, -15)
    s.set(0, 1, -13, B("pixelpirates:captains_chair", facing="south")); s.set(-3, 1, -10, B("pixelpirates:map_block"))
    s.set(3, 1, -10, B("pixelpirates:sea_chest", facing="west")); s.set(3, 1, -6, B("pixelpirates:sea_chest", facing="west"))
    s.set(-3, 1, -6, B("pixelpirates:ship_bedroll")); lantern(s, 0, 3, -9, hanging=True)
    s.set(0, t1 + 1, -13, B("pixelpirates:captains_desk", facing="south")); lantern(s, 0, t1 + 3, -11, hanging=True)
    # the forecastle: a raised deck over the bow
    for z in range(16, 25):
        w = deck_w(s, z)
        if w < 0: continue
        for x in range(-w, w + 1):
            s.set(x, 2, z, B("spruce_planks") if abs(x) < w else WOOD)
            if abs(x) == w: s.set(x, 1, z, P)
        s.put_if_air(-w, 3, z, B("dark_oak_fence")); s.put_if_air(w, 3, z, B("dark_oak_fence"))
    for x in range(-4, 5):
        if x not in (-1, 0, 1): s.set(x, 1, 16, P)
    s.set(0, 1, 16, None)
    essentials(s, anchor=(-2, 3, 20))
    guns(s, 1, (-1, 9))
    rails(s, -3, 15, 1, B("dark_oak_fence"))
    yard = B("dark_oak_fence")
    square_rig(s, 14, -6, 22, [(5, 10, 4), (13, 18, 3)], CRIMSON, yard)
    square_rig(s, 4, -6, 26, [(4, 11, 5), (14, 21, 4)], BLACK, yard, pattern=pat(BLACK, roger_patch(-1, 1, range(6, 10))))
    s.shrouds(5, 11, 2, 6); s.shrouds(15, 10, 3, 5)
    s.mast(-11, t2 + 1, t2 + 12)
    s.lateen(-11, t2 + 2, 10, 3, 3, CRIMSON, yard)
    flag(s, 4, 27, ["black_wool", "red_wool", "black_wool", "red_wool"])
    ensign(s, -14, t2 + 1, B("dark_oak_fence"), [["black_wool"] * 4, ["black_wool", "white_wool", "white_wool", "black_wool"],
                                                ["black_wool", "black_wool", "black_wool", "black_wool"]])
    bow(s, 24, 1, 8, 3, WOOD, "dread_skull_figurehead", CRIMSON, 6)
    s.save()
    return s


# ================================================================================================ MERCHANTS
def merchant_lugger():
    """THE HERRING LASS - a little coasting lugger of the Emerald Trading Co.: pale oak with a green strake, two lug sails
    in the Company's green and white stripes and a tiny mizzen, a deck crowded with crates, barrels and lobster pots,
    a net drying on the rail, one swivel gun a side. Slow, harmless, worth boarding."""
    s = Ship("merchant_lugger", "merchants", 201)
    P = B("oak_planks")
    def width(z):
        if z < -5: return -1
        if z == -5: return 2
        if z >= 8: return max(0, 3 - (z - 8))
        return 3
    s.hull(-5, 11, width, lambda z: -3 if -3 <= z <= 7 else -2, 0, P, "oak_stairs", rim=B("stripped_birch_log", axis="z"), bilge=1.3)
    paint(s, P, {-1: B("green_terracotta")})
    waterline(s, -1, 2)
    essentials(s, anchor=(1, 1, 9), bed=(-2, 1, -4))
    guns(s, 1, (-2,))
    rails(s, -5, 10, 1, B("oak_fence"))
    for (x, z, st) in ((-2, 3, B("pixelpirates:cargo_crate", facing="north")), (-2, 4, B("pixelpirates:cargo_crate", facing="east")),
                       (2, 3, B("barrel", facing="up", open="false")), (2, 4, B("barrel", facing="up", open="false")),
                       (1, 6, B("pixelpirates:lobster_pot", facing="north")), (-1, 6, B("pixelpirates:lobster_pot", facing="north")),
                       (2, -3, B("pixelpirates:fish_trap", facing="west")), (0, 4, B("pixelpirates:rope_coil", facing="north"))):
        s.set(x, 1, z, st)
    s.set(-2, 2, 3, B("pixelpirates:cargo_crate", facing="north"))
    for z in (5, 6): s.set(-3, 2, z, B("pixelpirates:hanging_net", facing="west")); s.set(3, 2, z, B("pixelpirates:hanging_net", facing="east"))
    s.set(2, 2, 3, B("barrel", facing="up", open="false"))
    stripes = lambda x, y: B("green_wool") if (x + 10) % 2 == 0 else W_CANVAS
    s.mast(7, -2, 11); s.square_sail(7, 3, 9, 2, None, yard=B("oak_fence"), pattern=stripes)
    s.mast(1, -2, 13); s.square_sail(1, 4, 11, 3, None, yard=B("oak_fence"), pattern=stripes)
    s.mast(-4, 1, 7); s.lateen(-4, 3, 4, 1, 2, W_CANVAS, B("oak_fence"))
    flag(s, 1, 14, ["green_wool", "lime_wool"])
    bow(s, 11, 1, 3, 2, B("stripped_birch_log", axis="z"), "mermaid_figurehead")
    lantern(s, 0, 1, -5)
    s.save()
    return s


def merchant_fluyt():
    """THE SILVER HERRING - a Company fluyt: the pear-shaped cargo carrier, deep and full-bellied with a tall narrow stern
    painted in the Company's colours (yellow, green and white panels round the cabin windows), three masts of green-and-
    white striped square sails, cargo hatches down the waist, two guns a side and a mermaid on the bow."""
    s = Ship("merchant_fluyt", "merchants", 202)
    P = B("spruce_planks")
    def width(z):
        if z < -10: return -1
        if z <= -8: return 2 + (z + 10)
        if z >= 14: return max(0, 4 - (z - 14))
        return 4
    s.hull(-10, 18, width, lambda z: -6 if -7 <= z <= 13 else -5, 0, P, "spruce_stairs", rim=B("stripped_birch_log", axis="z"), bilge=1.2)
    paint(s, P, {-1: B("yellow_terracotta"), -2: B("green_terracotta")})
    waterline(s, -3, 2)
    top = castle(s, -10, -4, 1, 4, P, B("spruce_planks"), B("stripped_birch_log", axis="z"), B("spruce_fence"),
                 windows=((2, -8), (3, -6)), stern_windows=((2, 1), (3, 1)), post=B("stripped_birch_log", axis="y"))
    for x in range(-2, 3):                                                   # the painted stern
        for y in range(1, top):
            if s.get(x, y, -10) == P: s.set(x, y, -10, B("yellow_terracotta") if (x + y) % 2 == 0 else B("green_terracotta"))
    for x in (-3, 3): ladder_up(s, x, -4, 1, top)
    s.set(-2, 1, -8, B("pixelpirates:map_block")); s.set(2, 1, -8, B("pixelpirates:sea_chest", facing="west"))
    s.set(0, 1, -9, B("pixelpirates:captains_desk", facing="south")); s.set(-2, 1, -6, B("pixelpirates:ship_bedroll"))
    lantern(s, 0, 4, -7, hanging=True); lantern(s, 0, top + 1, -10)
    essentials(s, anchor=(-2, 1, 15))
    guns(s, 1, (1, 9))
    rails(s, -3, 17, 1, B("spruce_fence"))
    for z in range(5, 9):                                                    # cargo hatches
        for x in (-1, 0, 1):
            s.set(x, 0, z, B("spruce_trapdoor", facing="north", half="top", open="false", powered="false", waterlogged="false"))
    for (x, z) in ((-3, 5), (3, 7), (-3, 8)): s.set(x, 1, z, B("pixelpirates:cargo_crate", facing="north"))
    for (x, z) in ((3, 5), (2, 5)): s.set(x, 1, z, B("barrel", facing="up", open="false"))
    yard = B("spruce_fence")
    stripes = lambda x, y: B("green_wool") if (x + 10) % 3 == 0 else W_CANVAS
    square_rig(s, 11, -5, 17, [(3, 8, 4), (10, 13, 3)], None, yard, pattern=stripes)
    square_rig(s, 3, -5, 20, [(3, 9, 4), (11, 16, 3)], None, yard, pattern=stripes)
    s.shrouds(4, 9, 2, 5)
    s.mast(-7, top + 1, top + 10); s.lateen(-7, top + 2, 8, 2, 3, W_CANVAS, yard)
    flag(s, 3, 21, ["green_wool", "lime_wool", "green_wool"])
    bow(s, 18, 1, 5, 3, B("stripped_birch_log", axis="z"), "mermaid_figurehead", W_CANVAS, 4)
    s.save()
    return s


def merchant_indiaman():
    """THE EMERALD EMPRESS - the Company's East Indiaman, a merchant built like a warship: a dark hull with an ochre gun
    band and a green stripe, four guns a side on a closed gun deck, a broad cabin with a stern gallery of glass and gilt,
    a forecastle, three masts of white square sails with the Company's green emblem, and a fortune in the hold."""
    s = Ship("merchant_indiaman", "merchants", 203)
    P = B("dark_oak_planks"); GOLD = B("gold_block")
    def width(z):
        if z < -13: return -1
        if z <= -11: return 3 + (z + 13)
        if z >= 17: return max(0, 5 - (z - 17))
        return 5
    s.hull(-13, 22, width, lambda z: -7 if -9 <= z <= 16 else -6, 0, P, "dark_oak_stairs", rim=B("stripped_birch_log", axis="z"), bilge=2.2)
    paint(s, P, {-1: B("yellow_terracotta"), -2: B("yellow_terracotta"), -3: B("green_terracotta")})
    waterline(s, -4, 2)
    gun_deck(s, -3, -10, 16, B("spruce_planks"))
    guns(s, -2, (-6, -1, 4, 9))
    for (x, z) in ((-3, 12), (-3, 13), (3, 12), (2, 13), (-2, 0), (3, 0)): s.set(x, -2, z, B("pixelpirates:cargo_crate", facing="north"))
    for z in (-4, 6): lantern(s, 0, -1, z, hanging=True)
    s.set(-3, 0, 2, None); s.set(-3, -1, 2, B("ladder", facing="north", waterlogged="false")); s.set(-3, -2, 2, B("ladder", facing="north", waterlogged="false"))
    s.set(-3, -1, 3, B("spruce_planks")); s.set(-3, -2, 3, B("spruce_planks"))
    top = castle(s, -13, -4, 1, 3, P, B("spruce_planks"), B("stripped_birch_log", axis="z"), B("spruce_fence"),
                 windows=((2, -10), (2, -7)), stern_windows=((2, 1), (2, 2), (2, 3)), post=GOLD)
    for x in (-4, 4): ladder_up(s, x, -4, 1, top)
    for x in range(-3, 4): s.set(x, 1, -14, slab("spruce_slab", "top")); s.set(x, 2, -14, B("spruce_fence"))
    s.set(0, 1, -12, B("pixelpirates:captains_desk", facing="south")); s.set(-3, 1, -9, B("pixelpirates:map_block"))
    s.set(3, 1, -9, B("pixelpirates:sea_chest", facing="west")); s.set(-3, 1, -6, B("pixelpirates:ship_bedroll"))
    lantern(s, 0, 3, -8, hanging=True)
    for x in (-3, 3): lantern(s, x, top + 1, -13)
    for z in range(16, 23):
        w = deck_w(s, z)
        if w < 0: continue
        for x in range(-w, w + 1): s.set(x, 1, z, slab("spruce_slab"))
    essentials(s, anchor=(-2, 2, 18))
    s.set(-2, 1, 18, B("spruce_planks"))
    rails(s, -3, 15, 1, B("spruce_fence"))
    for (x, z) in ((-3, 8), (3, 8), (2, 3)): s.set(x, 1, z, B("barrel", facing="up", open="false"))
    yard = B("spruce_fence")
    emblem = lambda x, y: B("green_wool") if abs(x) <= 1 and 5 <= y <= 7 else None
    square_rig(s, 13, -6, 21, [(4, 9, 4), (12, 16, 3)], W_CANVAS, yard)
    square_rig(s, 4, -6, 25, [(3, 10, 5), (13, 19, 4)], W_CANVAS, yard, pattern=pat(W_CANVAS, emblem))
    square_rig(s, -9, top + 1, 20, [(8, 12, 3), (14, 17, 2)], W_CANVAS, yard)
    s.shrouds(5, 10, 2, 6); s.shrouds(14, 9, 2, 5)
    flag(s, 4, 26, ["green_wool", "white_wool", "green_wool"])
    ensign(s, -13, top + 1, B("spruce_fence"), [["green_wool", "green_wool", "green_wool"], ["white_wool", "green_wool", "white_wool"]])
    bow(s, 22, 1, 7, 3, B("stripped_birch_log", axis="z"), "mermaid_figurehead", W_CANVAS, 5)
    s.save()
    return s


# ================================================================================================ NAVY
def navy_cutter():
    """THE REVENUE CUTTER HMS SWIFT - the Armada's customs runner: a sleek dark hull with a white band over a blue strake,
    a raking single mast carrying a big white gaff mainsail and a square topsail, a long jib, two guns a side, the blue
    pennant at the masthead and the eagle on the bow. The fastest thing in the starter seas."""
    s = Ship("navy_cutter", "navy", 301)
    P = B("dark_oak_planks")
    def width(z):
        if z < -6: return -1
        if z == -6: return 2
        if z >= 9: return max(0, 3 - (z - 9))
        return 3
    s.hull(-6, 12, width, lambda z: -3 if -4 <= z <= 8 else -2, 0, P, "dark_oak_stairs", rim=B("stripped_dark_oak_log", axis="z"), bilge=1.6)
    paint(s, P, {-1: B("white_concrete"), -2: B("blue_terracotta")})
    waterline(s, -2, 2)
    essentials(s, anchor=(1, 1, 10), bed=(1, 1, -5))
    guns(s, 1, (-1, 6))
    rails(s, -6, 11, 1, B("dark_oak_fence"))
    s.set(-1, 1, -5, B("pixelpirates:sea_chest", facing="east")); lantern(s, 0, 1, -6)
    s.mast(3, -2, 16)
    gaff(s, 3, 3, 9, 7, W_CANVAS, B("dark_oak_fence"))
    s.square_sail(3, 12, 14, 2, W_CANVAS, yard=B("dark_oak_fence"), belly=False)
    jib(s, 4, 3, 6, W_CANVAS)
    flag(s, 3, 17, ["blue_wool", "blue_wool", "white_wool"])
    bow(s, 12, 1, 5, 2, B("stripped_dark_oak_log", axis="z"), "navy_eagle_figurehead")
    s.save()
    return s


def navy_corvette():
    """THE SLOOP-OF-WAR HMS VIGILANT - a flush-decked three-master: white gun band over a blue strake, four guns a side on
    the open deck, a low poop cabin with gilt stern lanterns, square white sails on fore and main, a gaff spanker and
    topsail on the mizzen, the Armada's blue ensign astern and the eagle on the bow."""
    s = Ship("navy_corvette", "navy", 302)
    P = B("dark_oak_planks")
    def width(z):
        if z < -9: return -1
        if z <= -7: return 2 + (z + 9)
        if z >= 14: return max(0, 4 - (z - 14))
        return 4
    s.hull(-9, 18, width, lambda z: -5 if -6 <= z <= 13 else -4, 0, P, "dark_oak_stairs", rim=B("stripped_dark_oak_log", axis="z"), bilge=2.2)
    paint(s, P, {-1: B("white_concrete"), -2: B("blue_terracotta")})
    waterline(s, -3, 2)
    top = castle(s, -9, -4, 1, 2, P, B("spruce_planks"), B("stripped_dark_oak_log", axis="z"), B("dark_oak_fence"),
                 windows=((1, -7),), stern_windows=((1, 1), (1, 2)), post=B("white_concrete"))
    for x in (-3, 3): ladder_up(s, x, -4, 1, top)
    s.set(-2, 1, -7, B("pixelpirates:map_block")); s.set(2, 1, -7, B("pixelpirates:sea_chest", facing="west"))
    s.set(0, 1, -8, B("pixelpirates:ship_bedroll")); lantern(s, 0, 2, -6, hanging=True)
    for x in (-2, 2): lantern(s, x, top + 1, -9)
    essentials(s, anchor=(-2, 1, 15))
    guns(s, 1, (-2, 2, 7, 11))
    rails(s, -3, 17, 1, B("dark_oak_fence"))
    yard = B("dark_oak_fence")
    square_rig(s, 12, -4, 18, [(3, 7, 3), (9, 12, 2)], W_CANVAS, yard)
    square_rig(s, 4, -4, 21, [(3, 9, 4), (11, 15, 3)], W_CANVAS, yard)
    s.shrouds(5, 9, 2, 5)
    s.mast(-6, top + 1, top + 13)
    gaff(s, -6, top + 2, 7, 3, W_CANVAS, yard)
    s.square_sail(-6, top + 10, top + 12, 2, W_CANVAS, yard=yard, belly=False)
    flag(s, 4, 22, ["blue_wool", "white_wool", "blue_wool"])
    ensign(s, -9, top + 1, yard, [["blue_wool", "blue_wool", "blue_wool"], ["blue_wool", "white_wool", "blue_wool"], ["blue_wool", "blue_wool", "blue_wool"]])
    bow(s, 18, 1, 6, 3, B("stripped_dark_oak_log", axis="z"), "navy_eagle_figurehead", W_CANVAS, 5)
    s.save()
    return s


def navy_man_o_war():
    """HMS SOVEREIGN - the Armada's ship of the line: two closed gun decks (six guns a side on each, behind white bands),
    two more a side on the weather deck, a two-storey gilded stern with a double glass gallery, a forecastle, three tall
    masts of stacked white square sails, the great blue ensign astern and the eagle on the bow. The deadliest ship afloat."""
    s = Ship("navy_man_o_war", "navy", 303)
    P = B("dark_oak_planks"); GOLD = B("gold_block"); WHITE = B("white_concrete")
    def width(z):
        if z < -14: return -1
        if z <= -12: return 3 + (z + 14)
        if z >= 18: return max(0, 5 - (z - 18))
        return 5
    s.hull(-14, 23, width, lambda z: -9 if -10 <= z <= 16 else -8, 0, P, "dark_oak_stairs", rim=B("stripped_dark_oak_log", axis="z"), bilge=2.8)
    paint(s, P, {-1: WHITE, -2: WHITE, -3: B("blue_terracotta"), -4: WHITE, -5: WHITE, -6: B("blue_terracotta")})
    gun_deck(s, -3, -11, 16, B("spruce_planks"))
    gun_deck(s, -6, -10, 15, B("spruce_planks"))
    waterline(s, -7, 2)
    guns(s, -2, (-8, -4, 0, 4, 8, 12))
    guns(s, -5, (-6, -2, 2, 6, 10, 14))
    for (y, z) in ((-1, -6), (-1, 6), (-4, -2), (-4, 8)): lantern(s, 0, y, z, hanging=True)
    for y in (-1, -2, -4, -5): s.set(3, y, 6, B("ladder", facing="north", waterlogged="false"))
    s.set(3, 0, 6, None); s.set(3, -3, 6, None)
    for y in (-1, -2, -4, -5): s.set(3, y, 7, B("spruce_planks"))
    WOOD = B("stripped_dark_oak_log", axis="z")
    t1 = castle(s, -14, -4, 1, 3, P, B("spruce_planks"), WOOD, B("dark_oak_fence"), windows=((2, -11), (2, -7)),
                stern_windows=((2, 1), (2, 2), (2, 3)), post=GOLD)
    t2 = castle(s, -14, -9, t1 + 1, 3, WHITE, B("spruce_planks"), WOOD, B("dark_oak_fence"), windows=((t1 + 2, -12),),
                stern_windows=((t1 + 2, 0), (t1 + 2, 1), (t1 + 2, 2)), post=GOLD)
    for x in (-4, 4): ladder_up(s, x, -4, 1, t1)
    for x in (-3, 3): ladder_up(s, x, -9, t1 + 1, t2)
    for x in range(-3, 4):                                                   # the double stern gallery
        s.set(x, 1, -15, slab("dark_oak_slab", "top")); s.set(x, 2, -15, B("dark_oak_fence"))
        s.set(x, t1, -15, slab("dark_oak_slab", "top")); s.set(x, t1 + 1, -15, B("dark_oak_fence"))
    s.set(0, 1, -13, B("pixelpirates:captains_chair", facing="south")); s.set(-3, 1, -10, B("pixelpirates:map_block"))
    s.set(3, 1, -10, B("pixelpirates:sea_chest", facing="west")); s.set(-3, 1, -6, B("pixelpirates:ship_bedroll"))
    s.set(0, t1 + 1, -13, B("pixelpirates:captains_desk", facing="south"))
    lantern(s, 0, 3, -9, hanging=True); lantern(s, 0, t1 + 3, -11, hanging=True)
    for x in (-3, 3): lantern(s, x, t2 + 1, -14)
    for z in range(15, 24):
        w = deck_w(s, z)
        if w < 0: continue
        for x in range(-w, w + 1): s.set(x, 1, z, slab("dark_oak_slab"))
    essentials(s, anchor=(-2, 2, 19))
    s.set(-2, 1, 19, B("dark_oak_planks"))
    guns(s, 1, (-1, 10))
    rails(s, -3, 14, 1, B("dark_oak_fence"))
    yard = B("dark_oak_fence")
    square_rig(s, 14, -8, 24, [(4, 10, 4), (12, 17, 3), (19, 22, 2)], W_CANVAS, yard)
    square_rig(s, 4, -8, 28, [(3, 11, 5), (13, 19, 4), (21, 25, 3)], W_CANVAS, yard)
    square_rig(s, -11, t2 + 1, t2 + 15, [(t2 + 3, t2 + 7, 3), (t2 + 9, t2 + 12, 2)], W_CANVAS, yard)
    s.shrouds(5, 11, 2, 6); s.shrouds(15, 10, 2, 5)
    flag(s, 4, 29, ["blue_wool", "blue_wool", "white_wool", "blue_wool"])
    ensign(s, -14, t2 + 1, yard, [["blue_wool"] * 4, ["blue_wool", "white_wool", "white_wool", "blue_wool"], ["blue_wool"] * 4])
    bow(s, 23, 1, 8, 3, WOOD, "navy_eagle_figurehead", W_CANVAS, 6)
    s.save()
    return s


# ================================================================================================ UNDEAD
def undead_wraith():
    """THE WRAITH - a ghost sloop that sails out of the fog: a hull of pale translucent ghostwood, a glowing spectral gaff
    sail and topsail, soul lanterns fore and aft, cobwebs in the rigging, glow lichen on the strakes, a soul lantern
    swinging from the bowsprit. Two ghostly guns a side."""
    s = Ship("undead_wraith", "undead", 401)
    P = B("pixelpirates:ghostwood_planks")
    def width(z):
        if z < -5: return -1
        if z == -5: return 1
        if z >= 8: return max(0, 2 - (z - 8))
        return 2
    s.hull(-5, 10, width, lambda z: -3 if -3 <= z <= 7 else -2, 0, P, "dark_oak_stairs", rim=B("pixelpirates:ghostwood_log", axis="z"), bilge=1.4)
    waterline(s, -1, 2)
    essentials(s, anchor=(1, 1, 8), bed=(-1, 1, -4))
    guns(s, 1, (0, 5))
    rails(s, -5, 9, 1, B("warped_fence"))
    s.set(0, 1, -5, B("warped_fence")); lantern(s, 0, 2, -5, soul=True)
    s.mast(3, -2, 13)
    gaff(s, 3, 3, 8, 6, SPECTRAL, B("warped_fence"))
    s.square_sail(3, 10, 12, 2, SPECTRAL, yard=B("warped_fence"), belly=False)
    s.set(-2, 13, 4, B("cobweb")); s.set(0, 9, -3, B("cobweb")) if s.get(0, 9, -3) is None else None
    flag(s, 3, 14, ["light_gray_wool", "gray_wool"])
    tip = bow(s, 10, 1, 4, 2, B("pixelpirates:ghostwood_log", axis="z"), None)
    lantern(s, 0, tip - 1, 14, hanging=True)
    s.set(1, 1, -4, B("pixelpirates:sea_chest", facing="west"))
    s.save()
    return s


def undead_bone_galley():
    """THE BONE GALLEY - a long, low oared galley of the Drowned Fleet, its hull of rotten dark oak ribbed with bone, a
    bone ram on the bow under a dread skull, eight bone oars a side, a single tattered black lateen on the main and a
    spectral lateen on the mizzen, soul lanterns on the stern posts, two guns a side."""
    s = Ship("undead_bone_galley", "undead", 402)
    P = B("dark_oak_planks"); BONE = B("pixelpirates:bone_planks")
    def width(z):
        if z < -8: return -1
        if z <= -7: return 2 + (z + 8)
        if z >= 13: return max(0, 3 - (z - 13) // 2)
        return 3
    s.hull(-8, 18, width, lambda z: -3 if -6 <= z <= 14 else -2, 0, P, "dark_oak_stairs", rim=B("bone_block", axis="z"), bilge=1.2)
    for (x, y, z), st in list(s.b.items()):                      # bone ribs every other frame, rot elsewhere
        if st == P and z % 2 == 0 and abs(x) == deck_w(s, z): s.set(x, y, z, BONE)
        elif st == P and s.r.random() < 0.25: s.set(x, y, z, s.r.choice([B("mud_bricks"), B("pixelpirates:destroyed_planks"), B("mossy_cobblestone")]))
    waterline(s, -1, 3)
    for z in range(-4, 13, 2):                                   # the oars
        for sx in (-1, 1):
            s.set(sx * 4, 0, z, B("bone_block", axis="x")); s.set(sx * 5, -1, z, B("bone_block", axis="x")); s.set(sx * 4, -1, z, B("bone_block", axis="x"))
    essentials(s, anchor=(1, 1, 15), bed=(-1, 1, -6))
    guns(s, 1, (2, 9))
    rails(s, -8, 16, 1, B("dark_oak_fence"))
    for x in (-2, 2):
        s.set(x, 1, -8, B("bone_block", axis="y")); s.set(x, 2, -8, B("bone_block", axis="y")); lantern(s, x, 3, -8, soul=True)
    s.set(0, 1, -7, B("pixelpirates:sea_chest", facing="south"))
    s.set(1, 1, -5, B("skeleton_skull", rotation="8"))
    s.mast(5, -2, 15)
    s.lateen(5, 2, 12, 6, 5, B("black_wool"), B("dark_oak_fence"))
    for (x, y, z), st in list(s.b.items()):                      # tatter the main
        if st == B("black_wool") and s.r.random() < 0.15 and s.get(0, y - 1, z) not in (None,):
            s.set(x, y, z, B("cobweb") if s.r.random() < 0.5 else B("gray_wool"))
    s.mast(-5, 1, 10); s.lateen(-5, 3, 6, 2, 2, SPECTRAL, B("dark_oak_fence"))
    flag(s, 5, 16, ["green_wool", "lime_wool", "green_wool"])
    s.sprit(0, 19, 5, 2, B("bone_block", axis="z"))
    s.set(0, -1, 19, B("pixelpirates:dread_skull_figurehead", facing="south"))
    s.save()
    return s


def undead_phantom_galleon():
    """THE PHANTOM GALLEON - the flagship of the Drowned Fleet, a dead galleon sailing on as a ghost: a hull of glowing
    translucent ghostwood over a rotten dark-prismarine keel, a broken-windowed sterncastle with soul fire in its
    lanterns, a gun deck of five ghostly guns a side, three masts of tattered spectral square sails with cobwebs in the
    yards, green weed hanging from the rails, a dread skull on the bow."""
    s = Ship("undead_phantom_galleon", "undead", 403)
    P = B("pixelpirates:ghostwood_planks"); LOG = B("pixelpirates:ghostwood_log", axis="z")
    def width(z):
        if z < -13: return -1
        if z <= -11: return 3 + (z + 13)
        if z >= 17: return max(0, 5 - (z - 17))
        return 5
    s.hull(-13, 22, width, lambda z: -7 if -9 <= z <= 16 else -6, 0, P, "dark_oak_stairs", rim=LOG, bilge=2.3)
    paint(s, P, {-5: B("dark_prismarine"), -6: B("dark_prismarine"), -7: B("dark_prismarine")})
    for (x, y, z), st in list(s.b.items()):
        if st == P and y < -1 and s.r.random() < 0.15: s.set(x, y, z, B("prismarine"))
    waterline(s, -4, 2)
    gun_deck(s, -3, -10, 16, B("dark_oak_planks"))
    guns(s, -2, (-6, -2, 2, 6, 10))
    for z in (-4, 4, 12): lantern(s, 0, -1, z, hanging=True, soul=True)
    s.set(3, 0, 6, None); s.set(3, -1, 6, B("ladder", facing="north", waterlogged="false")); s.set(3, -2, 6, B("ladder", facing="north", waterlogged="false"))
    s.set(3, -1, 7, B("dark_oak_planks")); s.set(3, -2, 7, B("dark_oak_planks"))
    top = castle(s, -13, -4, 1, 4, P, B("dark_oak_planks"), LOG, B("warped_fence"), windows=((2, -10), (3, -7)),
                 stern_windows=((2, 1), (3, 2)), post=B("pixelpirates:ghostwood_log", axis="y"))
    for x in (-4, 4): ladder_up(s, x, -4, 1, top)
    s.set(0, 1, -12, B("pixelpirates:captains_chair", facing="south")); s.set(0, 2, -12, B("skeleton_skull", rotation="8"))
    s.set(-3, 1, -9, B("pixelpirates:map_block")); s.set(3, 1, -9, B("pixelpirates:sea_chest", facing="west"))
    s.set(-3, 1, -6, B("pixelpirates:ship_bedroll"))
    for (x, y, z) in ((2, 4, -11), (-3, 4, -6), (3, 3, -5)): s.set(x, y, z, B("cobweb"))
    lantern(s, 0, 4, -8, hanging=True, soul=True)
    for x in (-3, 3): lantern(s, x, top + 1, -13, soul=True)
    essentials(s, anchor=(-2, 1, 19))
    rails(s, -3, 21, 1, B("warped_fence"))
    for (x, z) in ((-5, 8), (5, 0), (-5, -1), (5, 13)): s.set(x, 2, z, B("soul_lantern", hanging="false", waterlogged="false"))
    yard = B("warped_fence")
    def tattered(x, y):
        return B("cobweb") if s.r.random() < 0.08 else SPECTRAL
    square_rig(s, 13, -6, 21, [(4, 9, 4), (12, 16, 3)], None, yard, pattern=tattered)
    square_rig(s, 4, -6, 25, [(3, 10, 5), (13, 19, 4)], None, yard, pattern=tattered)
    square_rig(s, -9, top + 1, top + 14, [(top + 3, top + 7, 3), (top + 9, top + 12, 2)], None, yard, pattern=tattered)
    s.shrouds(5, 10, 2, 6)
    flag(s, 4, 26, ["light_gray_wool", "gray_wool", "light_gray_wool"])
    for z in range(-10, 20, 3):                                   # weed trailing from the rails (mirrored, stays level)
        w = deck_w(s, z)
        for sx in (-1, 1):
            for y in range(-2, 1):
                if (sx * w, y, z) in s.b and (sx * (w + 1), y, z) not in s.b:
                    s.set(sx * (w + 1), y, z, B("vine", **{"east": "true" if sx < 0 else "false", "west": "true" if sx > 0 else "false",
                                                         "north": "false", "south": "false", "up": "false"}))
    bow(s, 22, 1, 7, 3, LOG, "dread_skull_figurehead", SPECTRAL, 5)
    s.save()
    return s


SHIPS = {"pirate_cutter": pirate_cutter, "pirate_brig": pirate_brig, "pirate_galleon": pirate_galleon,
         "merchant_lugger": merchant_lugger, "merchant_fluyt": merchant_fluyt, "merchant_indiaman": merchant_indiaman,
         "navy_cutter": navy_cutter, "navy_corvette": navy_corvette, "navy_man_o_war": navy_man_o_war,
         "undead_wraith": undead_wraith, "undead_bone_galley": undead_bone_galley, "undead_phantom_galleon": undead_phantom_galleon}

if __name__ == "__main__":
    for n in [a for a in sys.argv[1:] if not a.startswith("--")] or list(SHIPS):
        preview(SHIPS[n]())
