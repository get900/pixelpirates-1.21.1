"""Ship blueprints built in code (2026-10-01): four faction ships for players and the AI.

    python tools/gen_ship_blueprints.py [name ...]

Writes blueprint files in EXACTLY the ShipSchematic format (gzip NBT: mast_count + blocks[{x,y,z,state}]) to
  src/main/resources/data/pixelpirates/ships/<name>.nbt   (bundled; copied into config/pixelpirates/ships on start)
  run/config/pixelpirates/ships/   (so the dev client has them; never write into the user's own server)
and a preview sheet tools/previews/ships/<name>.png (isometric bow + stern, side, top).

ORIENTATION (same as the hand-built sloop/skipper/brigantine): the SHIP_HELM is the origin (0,0,0) on the deck,
the BOW points +Z, the stern -Z; +Y up. ShipSteeringManager pushes along ship-space +Z, so a ship authored any other
way sails sideways or backwards. Every block must be face-connected to the helm (assembly and saving flood-fill from
it) - the script checks and refuses otherwise. Speed = number of pixelpirates:ship_mast blocks vs the ship's mass.
The SHIP_WATERLINE block ends up at sea level.
"""
import gzip, io, math, pathlib, random, struct, sys
from collections import deque

ROOT = pathlib.Path(__file__).resolve().parent.parent
BUNDLE = ROOT / "src/main/resources/data/pixelpirates/ships"
CONFIGS = [ROOT / "run/config/pixelpirates/ships"]          # (never the user's own server)
PREV = ROOT / "tools/previews/ships"

OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}

# Ships saved by hand in game with /ppship capture are listed in BUNDLE/captured.txt - never overwrite them unless --force.
FORCE = "--force" in sys.argv


def captured():
    f = BUNDLE / "captured.txt"
    if not f.exists(): return set()
    return {l.strip() for l in f.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")}


# =====================================================================================
# NBT writer
# =====================================================================================
def _tag(t, v, out):
    if t == "int": out.write(struct.pack(">i", v))
    elif t == "str":
        b = v.encode(); out.write(struct.pack(">H", len(b))); out.write(b)


def _named(tid, name, out):
    out.write(bytes([tid])); b = name.encode(); out.write(struct.pack(">H", len(b))); out.write(b)


def _compound(d, out):
    for k, v in d.items():
        if isinstance(v, int):
            _named(3, k, out); out.write(struct.pack(">i", v))
        elif isinstance(v, str):
            _named(8, k, out); _tag("str", v, out)
        elif isinstance(v, dict):
            _named(10, k, out); _compound(v, out)
        elif isinstance(v, list):
            _named(9, k, out); out.write(bytes([10])); out.write(struct.pack(">i", len(v)))
            for e in v: _compound(e, out)
    out.write(b"\x00")


def write_nbt(path, root):
    buf = io.BytesIO()
    _named(10, "", buf)
    _compound(root, buf)
    path.parent.mkdir(parents=True, exist_ok=True)
    with gzip.open(path, "wb") as f:
        f.write(buf.getvalue())


# =====================================================================================
# the ship kit
# =====================================================================================
def B(name, **props):
    if ":" not in name: name = "minecraft:" + name
    return (name, tuple(sorted((k, str(v).lower()) for k, v in props.items())))


FENCE_LIKE = ("fence", "iron_bars", "glass_pane", "_wall", "chain")


class Ship:
    def __init__(self, name, faction, seed):
        self.name, self.faction = name, faction
        self.b = {}
        self.r = random.Random(seed)

    def set(self, x, y, z, st):
        if st is None: self.b.pop((x, y, z), None)
        else: self.b[(x, y, z)] = st

    def get(self, x, y, z): return self.b.get((x, y, z))

    def fill(self, x0, y0, z0, x1, y1, z1, st):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, st)

    def put_if_air(self, x, y, z, st):
        if (x, y, z) not in self.b: self.b[(x, y, z)] = st

    # -------------------------------------------------------------- hull
    def hull(self, z0, z1, width, keel, deck, plank, stair, *, rim=None, flare=1.0, bilge=2.0):
        """Round-bellied hull from z0 (stern) to z1 (bow). width(z) = half-width at the deck, keel(z) = bottom y.
        The shell is planks with upside-down stairs where the next layer down steps in (a curved bilge)."""
        self.hw = {}
        for z in range(z0, z1 + 1):
            W, kb = width(z), keel(z)
            if W < 0: continue
            rows = {}
            for y in range(kb, deck + 1):
                t = (y - kb) / max(1, deck - kb)                           # 0 at the keel, 1 at the deck
                rows[y] = max(0, round(W * (1 - (1 - t) ** bilge) ** (1 / flare))) if y < deck else W
            self.hw[z] = rows
        for z, rows in self.hw.items():
            for y, w in rows.items():
                below = rows.get(y - 1, -1)
                for x in range(-w, w + 1):
                    outer = abs(x) == w
                    fore = self.hw.get(z + 1, {}).get(y, -1)
                    aft = self.hw.get(z - 1, {}).get(y, -1)
                    shell = outer or y == min(rows) or abs(x) >= below or abs(x) > fore or abs(x) > aft or y == deck
                    if not shell: continue
                    st = plank
                    if y < deck and outer and w > 0 and below < w and y > min(rows):
                        st = B(stair, facing="east" if x < 0 else "west", half="top", shape="straight", waterlogged="false")
                    elif y < deck and abs(x) > fore and not outer and fore >= 0 and y > min(rows):
                        st = B(stair, facing="north", half="top", shape="straight", waterlogged="false")
                    elif y < deck and abs(x) > aft and not outer and aft >= 0 and y > min(rows):
                        st = B(stair, facing="south", half="top", shape="straight", waterlogged="false")
                    if y == deck and rim is not None and outer: st = rim
                    self.set(x, y, z, st)

    def hollow_to(self, deck):
        """Nothing to do - hull() only writes the shell; interiors stay air."""

    # -------------------------------------------------------------- rigging
    def mast(self, z, base, top, x=0):
        for y in range(base, top + 1): self.set(x, y, z, B("pixelpirates:ship_mast", axis="y"))

    def square_sail(self, z, y0, y1, half, sail, yard=None, belly=True, pattern=None):
        """A square sail on the FORE side of a mast at z (sails sit bow-ward of the mast, like the brigantine)."""
        for y in range(y0, y1 + 1):
            w = half - (1 if y in (y0,) else 0)
            for x in range(-w, w + 1):
                st = pattern(x, y) if pattern else sail
                bz = z + 1 + (1 if belly and abs(x) < w - 1 and y0 < y < y1 else 0)
                self.set(x, y, bz, st)
                if bz != z + 1: self.set(x, y, z + 1, st)
        if yard:
            for x in range(-half - 1, half + 2): self.set(x, y1 + 1, z + 1, yard)
            self.set(0, y1 + 1, z, yard) if self.get(0, y1 + 1, z) is None else None

    def lateen(self, z, base, height, back, fwd, sail, spar):
        """A raked triangular sail on a slanted yard: the yard runs from (z - back, base) up to (z + fwd, base + height)."""
        pts = []
        n = max(back + fwd, height)
        for i in range(n + 1):
            t = i / n
            pts.append((round(z - back + t * (back + fwd)), round(base + t * height)))
        for (pz, py) in pts: self.set(0, py, pz, spar)
        # the boom along the foot, and the sail filling every column between boom and yard
        for pz in range(z - back, z + fwd + 1):
            if (0, base, pz) not in self.b: self.set(0, base, pz, spar)
        for (pz, py) in pts:
            for y in range(base + 1, py):
                if (0, y, pz) not in self.b: self.set(0, y, pz, sail)

    def sprit(self, y0, z0, n, every, st):
        """A bowsprit climbing 1 block every `every` blocks forward; each rise overlaps so the spar stays connected."""
        prev = y0
        for i in range(n):
            y = y0 + i // every
            if y != prev: self.set(0, y, z0 + i - 1, st)
            self.set(0, y, z0 + i, st)
            prev = y
        return prev

    def shrouds(self, z, y_top, y_bottom, half):
        """Vertical chain lines from each end of a yard down to the rail - reads as rigging, and it is symmetric."""
        for sx in (-1, 1):
            for y in range(y_bottom, y_top + 1):
                self.put_if_air(sx * half, y, z, B("chain", axis="y", waterlogged="false"))

    def pennant(self, x, y, z, cols):
        for i, c in enumerate(cols): self.set(x, y, z - i, c)

    # -------------------------------------------------------------- finish
    def connect_fences(self):
        def solid(st):
            if st is None: return False
            n = st[0]
            return not any(k in n for k in ("stairs", "slab", "lantern", "torch", "ladder", "button", "carpet", "trapdoor", "banner", "cannon", "figurehead", "chain"))
        for (x, y, z), st in list(self.b.items()):
            n = st[0]
            if not any(k in n for k in ("fence", "iron_bars", "glass_pane")): continue
            props = dict(st[1])
            for d, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("west", (-1, 0)), ("east", (1, 0))):
                o = self.b.get((x + dx, y, z + dz))
                props[d] = "true" if o is not None and (any(k in o[0] for k in FENCE_LIKE) or solid(o)) and "gate" not in o[0] else "false"
            props["waterlogged"] = "false"
            self.b[(x, y, z)] = (n, tuple(sorted(props.items())))

    def check(self):
        assert self.get(0, 0, 0) and self.get(0, 0, 0)[0] == "pixelpirates:ship_helm", "the helm must be at 0,0,0"
        assert any(s[0] == "pixelpirates:ship_waterline" for s in self.b.values()), "needs a ship_waterline"
        seen, q = {(0, 0, 0)}, deque([(0, 0, 0)])
        while q:
            x, y, z = q.popleft()
            for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
                p = (x + d[0], y + d[1], z + d[2])
                if p in self.b and p not in seen: seen.add(p); q.append(p)
        loose = [p for p in self.b if p not in seen]
        assert not loose, f"{self.name}: {len(loose)} blocks not connected to the helm, e.g. {loose[:6]}"
        assert len(self.b) < 4000, f"{self.name}: {len(self.b)} blocks (ShipSchematic saves cap at 4096)"

    def masts(self): return sum(1 for s in self.b.values() if s[0] == "pixelpirates:ship_mast")

    # VS2 2.4 block masses in kg (data/valkyrienskies/vs_mass in the VS2 jar); anything unlisted uses the default 100
    WEIGHT = (("cobweb", .01), ("lantern", 2.5), ("sea_pickle", 2), ("skull", 2.5), ("ladder", 5), ("glass_pane", 25),
              ("wool", 50), ("canvas", 100), ("fence", 50), ("slab", 25), ("stairs", 37.5), ("trapdoor", 50),
              ("_log", 80), ("planks", 50), ("terracotta", 200), ("concrete", 240), ("bone_block", 200), ("barrel", 40),
              ("chest", 100), ("chain", 100), ("glow_lichen", 1), ("vine", 1))

    def weight(self, name):
        for k, w in self.WEIGHT:
            if k in name: return w
        return 100.0

    def trim(self, ballast="brown_terracotta"):
        """Hidden ballast in the bilge on the light side until the centre of mass sits on the keel line (x = 0).
        VS2 treats the ship as one rigid body: a heavier side makes it list and pull sideways under thrust."""
        w = self.weight(ballast)
        for _ in range(200):
            cx = self.balance()[0]
            if abs(cx) < 0.01: return
            side = -1 if cx > 0 else 1                     # add weight on the side opposite the lean
            spot = None
            for (x, y, z) in sorted(self.b, key=lambda p: (p[1], -abs(p[0]))):
                if x * side <= 0: continue
                q = (x, y + 1, z)
                if q in self.b or (-x, y + 1, z) in self.b and False: continue
                if self.b[(x, y, z)][0].endswith("planks") or "stairs" in self.b[(x, y, z)][0] or "terracotta" in self.b[(x, y, z)][0]:
                    # inside the hull: something above within a few blocks (the deck) and not outboard
                    if any((x, y + k, z) in self.b for k in range(2, 6)) and abs(x) < max(1, self.hw.get(z, {}).get(y + 1, 0)):
                        spot = q; break
            if spot is None: return
            self.set(*spot, B(ballast))

    def balance(self):
        """Centre of mass (rough relative block weights). x must sit on the keel line or the ship lists and pulls sideways."""
        tot = sx = sy = sz = 0.0
        for (x, y, z), st in self.b.items():
            w = self.weight(st[0]); tot += w; sx += w * x; sy += w * y; sz += w * z
        return sx / tot, sy / tot, sz / tot

    def save(self):
        if self.name in captured() and not FORCE:
            print(f"{self.name:15s} SKIPPED - hand-edited in game (/ppship capture); run with --force to rebuild it from code")
            return
        self.connect_fences()
        self.trim()
        self.check()
        blocks = []
        for (x, y, z), (n, props) in sorted(self.b.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            st = {"Name": n}
            if props: st["Properties"] = dict(props)
            blocks.append({"x": x, "y": y, "z": z, "state": st})
        root = {"mast_count": self.masts(), "blocks": blocks}
        for d in [BUNDLE] + [c for c in CONFIGS if c.parent.exists()]:
            write_nbt(d / f"{self.name}.nbt", root)
        xs = [p[0] for p in self.b]; ys = [p[1] for p in self.b]; zs = [p[2] for p in self.b]
        cx, cy, cz = self.balance()
        print(f"   centre of mass x {cx:+.3f}  y {cy:+.2f}  z {cz:+.2f}" + ("   <-- LISTS" if abs(cx) > 0.02 else "   balanced"))
        print(f"{self.name:15s} {len(self.b):5d} blocks  {self.masts():3d} mast blocks  "
              f"x {min(xs)}..{max(xs)}  y {min(ys)}..{max(ys)}  z {min(zs)}..{max(zs)} (bow +z)")


# =====================================================================================
# common pieces
# =====================================================================================
def stair(name, facing, half="bottom"):
    return B(name, facing=facing, half=half, shape="straight", waterlogged="false")


def slab(name, t="bottom"):
    return B(name, type=t, waterlogged="false")


def rails(s, z0, z1, y, fence, skip=()):
    """A rail along both gunwales at height y, from z0 to z1, following the hull's deck width."""
    for z in range(z0, z1 + 1):
        if z not in s.hw: continue
        w = s.hw[z][max(s.hw[z])]
        for sx in (-1, 1):
            if (sx * w, z) in skip: continue
            s.put_if_air(sx * w, y, z, fence)


def cannon(s, x, y, z):
    s.set(x, y, z, B("pixelpirates:ship_cannon", facing="west" if x < 0 else "east", loaded="false"))


def essentials(s, anchor, map_at=None, bed=None):
    s.set(0, 0, 0, B("pixelpirates:ship_helm"))
    s.set(*anchor, B("pixelpirates:anchor_block"))
    if map_at: s.set(*map_at, B("pixelpirates:map_block"))
    if bed: s.set(*bed, B("pixelpirates:ship_bedroll"))


# =====================================================================================
# 1. THE MERCHANTMAN - Emerald Trading Co.
# =====================================================================================
def merchantman():
    """A fat, bluff-bowed trading cog: deep round belly, tall aft castle with the helm on top, a forecastle,
    an open cargo well amidships full of crates and barrels with a derrick to swing them out, one huge striped
    square sail on the main and a small lateen mizzen, a green trade pennant and a mermaid on the bow.
    Slow and steady - lots of hull for its sails."""
    s = Ship("merchantman", "merchants", 11)
    P, S_ = B("spruce_planks"), "spruce_stairs"
    TRIM, GREEN = B("stripped_birch_log", axis="z"), B("green_terracotta")
    DECK = -3                                     # main deck; the helm stands on the aft castle roof at 0
    def width(z):
        if z < -8: return -1
        if z <= -6: return 3 + (z + 8)            # rounded transom
        if z >= 11: return max(1, 5 - (z - 11))    # bluff bow
        return 5
    keel = lambda z: DECK - 5 if -5 <= z <= 9 else DECK - 4
    s.hull(-8, 15, width, keel, DECK, P, S_, rim=TRIM, bilge=1.6)
    # green waterline band two below the deck
    for (x, y, z), st in list(s.b.items()):
        if y == DECK - 2 and st == P: s.set(x, y, z, GREEN)
    s.set(-5, DECK - 2, 3, B("pixelpirates:ship_waterline"))                   # in the green band = sea level
    # decks: open cargo well z 0..6 (a floor 3 down), decked elsewhere
    for z in range(0, 7):
        for x in range(-4, 5):
            s.set(x, DECK, z, None)
            s.set(x, DECK - 3, z, B("oak_planks"))
    # cargo in the well
    for (x, z) in ((-3, 1), (-3, 2), (3, 1), (-2, 5), (3, 5), (3, 4)):
        s.set(x, DECK - 2, z, B("pixelpirates:cargo_crate", facing="north"))
    for (x, z) in ((-3, 4), (-3, 5), (2, 1), (3, 2), (0, 5), (-1, 5)):
        s.set(x, DECK - 2, z, B("barrel", facing="up", open="false"))
    s.set(-3, DECK - 1, 1, B("barrel", facing="up", open="false"))
    s.set(3, DECK - 1, 5, B("pixelpirates:cargo_crate", facing="west"))
    s.set(0, DECK - 2, 3, B("pixelpirates:rope_coil", facing="north"))
    # AFT CASTLE z -8..-1, two storeys: a cabin under the helm deck (y DECK+1..-1), the helm on its roof (y 0)
    for z in range(-8, 0):
        w = width(z)
        for x in range(-w, w + 1):
            for y in range(DECK + 1, 0):
                wall = abs(x) == w or z == -8 or z == -1
                if wall:
                    win = y == DECK + 2 and ((abs(x) == w and z in (-6, -3)) or (z == -8 and abs(x) in (1, 2)))
                    s.set(x, y, z, B("glass_pane") if win else (B("stripped_spruce_log", axis="y") if abs(x) == w and z in (-8, -1) else P))
            s.set(x, 0, z, B("spruce_planks") if abs(x) < w else TRIM)
    s.set(0, DECK + 1, -1, None); s.set(0, DECK + 2, -1, None)          # cabin door
    s.set(0, DECK + 1, -1, B("spruce_trapdoor", facing="south", half="bottom", open="true", powered="false", waterlogged="false"))
    s.set(0, 0, 0, None)
    # castle roof rail + stair up from the main deck on the port side
    for z in range(-8, 0):
        w = width(z)
        s.put_if_air(-w, 1, z, B("spruce_fence")); s.put_if_air(w, 1, z, B("spruce_fence"))
    for x in range(-4, 5): s.put_if_air(x, 1, -8, B("spruce_fence"))
    for x in (-3, 3):
        s.set(x, DECK + 1, 0, stair("spruce_stairs", "north"))
        s.set(x, DECK + 2, -1, None); s.set(x, DECK + 1, -1, stair("spruce_stairs", "north"))
        s.set(x, DECK + 2, -1, stair("spruce_stairs", "north"))
        s.set(x, DECK + 3, -1, None) if (x, DECK + 3, -1) in s.b else None
    # the helm on the castle roof, the cabin: map table, bedroll, lantern
    essentials(s, anchor=(0, DECK + 1, 13), map_at=(2, DECK + 1, -6), bed=(-3, DECK + 1, -6))
    s.set(0, DECK + 1, -7, B("pixelpirates:sea_chest", facing="south"))
    s.set(3, DECK + 1, -4, B("barrel", facing="up", open="false"))
    s.set(0, -1, -4, B("lantern", hanging="true", waterlogged="false"))
    s.set(0, 1, -7, B("lantern", hanging="false", waterlogged="false"))
    # stern gallery: a railed balcony across the transom under the cabin windows, cornice round the castle roof
    for x in range(-3, 4):
        s.set(x, DECK + 1, -9, B("spruce_slab", type="top", waterlogged="false"))
        s.set(x, DECK + 2, -9, B("spruce_fence"))
    for x in (-3, 3): s.set(x, DECK + 3, -9, B("lantern", hanging="false", waterlogged="false"))
    for z in range(-8, 0):
        w = width(z)
        for sx in (-1, 1):
            s.put_if_air(sx * (w + 1), -1, z, stair("spruce_stairs", "east" if sx < 0 else "west", "top"))
    # FORECASTLE z 9..15, one storey
    for z in range(9, 16):
        w = width(z)
        for x in range(-w, w + 1):
            s.set(x, DECK + 2, z, B("spruce_planks") if abs(x) < w else TRIM)
            if abs(x) == w or z == 15: s.set(x, DECK + 1, z, P)
        s.put_if_air(-w, DECK + 3, z, B("spruce_fence")); s.put_if_air(w, DECK + 3, z, B("spruce_fence"))
    for x in range(-3, 4):
        if x != 0: s.set(x, DECK + 1, 9, P)
    s.set(0, DECK + 1, 9, None)
    s.set(0, DECK + 1, 10, B("pixelpirates:hanging_net", facing="north"))
    s.set(1, DECK + 3, 13, B("lantern", hanging="false", waterlogged="false"))
    # main deck rails between the castles (open well gets a rail too)
    for z in range(0, 9):
        s.put_if_air(-5, DECK + 1, z, B("spruce_fence")); s.put_if_air(5, DECK + 1, z, B("spruce_fence"))
    # MAIN MAST z 7 through the deck, one huge striped square sail, crow's nest
    s.mast(7, DECK - 4, DECK + 16)
    stripe = lambda x, y: B("green_wool") if (x + 20) % 4 < 2 else B("pixelpirates:white_sail_canvas")
    s.square_sail(7, DECK + 5, DECK + 13, 5, None, yard=B("spruce_fence"), pattern=stripe)
    for x in range(-6, 7): s.set(x, DECK + 4, 8, B("spruce_fence"))               # the boom (foot yard)
    for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
        s.set(dx, DECK + 15, 7 + dz, B("spruce_slab", type="bottom", waterlogged="false"))
    for dx, dz in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        s.set(dx, DECK + 15, 7 + dz, B("spruce_slab", type="bottom", waterlogged="false"))
        s.set(dx, DECK + 16, 7 + dz, B("spruce_fence"))
    s.pennant(0, DECK + 17, 7, [B("green_wool"), B("green_wool"), B("lime_wool"), B("green_wool")])
    s.shrouds(8, DECK + 13, DECK + 5, 6)                    # rigging from the yard ends down to the boom
    s.set(0, DECK + 17, 7, B("pixelpirates:ship_mast", axis="y"))
    # MIZZEN z -5 on the castle roof, a small lateen
    s.mast(-5, 1, 8)
    s.lateen(-5, 2, 7, 3, 3, B("pixelpirates:white_sail_canvas"), B("spruce_fence"))
    # the derrick: a boom from the main mast over the port side, with a crate on the hook
    for i in range(0, 7): s.set(-i, DECK + 3, 6, B("spruce_fence"))
    s.set(-6, DECK + 2, 6, B("chain", axis="y")); s.set(-6, DECK + 1, 6, B("pixelpirates:cargo_crate", facing="north"))
    # bowsprit + mermaid figurehead
    tip = s.sprit(DECK + 1, 16, 6, 3, B("stripped_spruce_log", axis="z"))
    for i in range(0, 5):                                   # a small spritsail/jib from the bowsprit up to the fore
        for y in range(tip + 1, tip + 6 - i):
            s.put_if_air(0, y, 16 + i, B("pixelpirates:white_sail_canvas"))
    s.set(0, DECK, 16, B("pixelpirates:mermaid_figurehead", facing="south"))
    # two defensive guns a side, in the cargo well rail
    for x in (-5, 5):
        for z in (2, 5): s.set(x, DECK + 1, z, None); cannon(s, x, DECK + 1, z)
    s.save()
    return s


# =====================================================================================
# 2. THE NAVY FRIGATE - Iron Armada
# =====================================================================================
def frigate():
    """A long three-masted frigate: dark hull with a white gun-deck band, a closed gun deck with five cannons a
    side behind gunports, two more a side on the quarterdeck, a stern gallery of glass with gilt lanterns,
    stacked white square sails on all three masts with a blue ensign, a long bowsprit with a jib and the
    Iron Armada eagle on the bow. Heavy, heavily armed, well canvassed."""
    s = Ship("navy_frigate", "navy", 22)
    P, S_ = B("dark_oak_planks"), "dark_oak_stairs"
    WHITE, BLUE, GOLD = B("white_concrete"), B("blue_terracotta"), B("stripped_bamboo_block", axis="y")
    DECK, GUN = 0, -3                          # the helm's weather deck; the gun deck floor is 3 below
    def width(z):
        if z < -12: return -1
        if z <= -10: return 3 + (z + 12)       # square stern
        if z >= 16: return max(0, 5 - (z - 15))
        return 5
    keel = lambda z: -7 if -8 <= z <= 13 else -6
    s.hull(-12, 20, width, keel, DECK, P, S_, rim=B("stripped_dark_oak_log", axis="z"), bilge=2.6)
    # paint: white band at the gun deck (y -2..-1), blue strake under it
    for (x, y, z), st in list(s.b.items()):
        if y in (-2, -1) and st == P: s.set(x, y, z, WHITE)
        elif y == -3 and st == P: s.set(x, y, z, BLUE)
    s.set(0, -5, 2, B("pixelpirates:ship_waterline"))
    # gun deck floor + gunports: five cannons a side at y -2 through the white band
    for z in range(-9, 16):
        w = width(z)
        for x in range(-w + 1, w):
            s.set(x, GUN, z, B("spruce_planks"))
    for z in (-6, -2, 2, 6, 10):
        for x in (-5, 5): cannon(s, x, -2, z)
    s.set(0, -2, 13, B("lantern", hanging="false", waterlogged="false"))
    s.set(0, -1, 4, B("lantern", hanging="true", waterlogged="false"))
    s.set(0, -1, -4, B("lantern", hanging="true", waterlogged="false"))
    s.set(0, -2, 8, B("barrel", facing="up", open="false")); s.set(1, -2, 8, B("barrel", facing="up", open="false"))
    s.set(-1, -2, -8, B("pixelpirates:ship_bedroll"))
    # a hatch down to the gun deck (ladder)
    s.set(3, DECK, 1, None); s.set(3, -1, 1, B("ladder", facing="north", waterlogged="false")); s.set(3, -2, 1, B("ladder", facing="north", waterlogged="false"))
    s.set(3, -1, 0, B("spruce_planks")); s.set(3, -2, 0, B("spruce_planks"))
    # QUARTERDECK: raised aft z -12..-3 (y 1..), the helm on it... the helm is the origin, so the quarterdeck
    # floor is at y -1 relative -> we keep the helm at deck level and raise a poop house behind it instead
    for z in range(-12, -3):
        w = width(z)
        for x in range(-w, w + 1):
            for y in range(1, 4):
                wall = abs(x) == w or z == -12 or z == -4
                if not wall: continue
                gal = z == -12 and y == 2 and abs(x) <= 2
                win = abs(x) == w and y == 2 and z in (-10, -7)
                s.set(x, y, z, B("glass_pane") if gal or win else (WHITE if y == 1 else P))
            s.set(x, 4, z, B("spruce_planks") if abs(x) < w else B("stripped_dark_oak_log", axis="z"))
    for x in (-3, 3):                                                                  # gilt stern pillars
        for y in (1, 2, 3): s.set(x, y, -12, GOLD)
    s.set(0, 3, -12, B("pixelpirates:dread_skull_figurehead", facing="north")) if False else None
    s.set(0, 1, -4, None); s.set(0, 2, -4, None)                                       # cabin door
    s.set(0, 1, -11, B("pixelpirates:captains_chair", facing="south"))
    s.set(-2, 1, -8, B("pixelpirates:map_block")); s.set(2, 1, -8, B("pixelpirates:sea_chest", facing="west"))
    s.set(0, 3, -8, B("lantern", hanging="true", waterlogged="false"))
    for z in range(-12, -3):
        w = width(z)
        s.put_if_air(-w, 5, z, B("dark_oak_fence")); s.put_if_air(w, 5, z, B("dark_oak_fence"))
    for x in range(-4, 5): s.put_if_air(x, 5, -12, B("dark_oak_fence"))
    for x in (-3, 3): s.set(x, 5, -12, B("lantern", hanging="false", waterlogged="false"))
    # ladders up to the poop deck
    for x in (-4, 4): s.set(x, 1, -3, B("ladder", facing="south", waterlogged="false")); s.set(x, 2, -3, B("ladder", facing="south", waterlogged="false")); s.set(x, 3, -3, B("ladder", facing="south", waterlogged="false"))
    # quarterdeck guns flanking the helm and the forecastle
    for x in (-5, 5):
        cannon(s, x, 1, -2); cannon(s, x, 1, 12)
    rails(s, -3, 19, 1, B("dark_oak_fence"))
    # FORECASTLE deck z 14..20 (slab roof over the bow)
    for z in range(14, 21):
        w = width(z)
        for x in range(-w, w + 1): s.set(x, 1, z, slab("dark_oak_slab"))
    essentials(s, anchor=(-2, 2, 17))
    s.set(-2, 1, 17, B("dark_oak_planks"))
    # THREE MASTS with stacked square sails (course + topsail), yards of fences
    WHITE_SAIL = B("pixelpirates:white_sail_canvas")
    def rig(z, base, top, courses):
        s.mast(z, base, top)
        for (y0, y1, half) in courses:
            s.square_sail(z, y0, y1, half, WHITE_SAIL, yard=B("dark_oak_fence"), belly=False)
        for dx in (-1, 1): s.set(dx, top - 3, z, B("dark_oak_slab", type="top", waterlogged="false"))
    rig(12, -6, 19, [(4, 9, 4), (12, 16, 3)])       # fore
    rig(3, -6, 22, [(3, 10, 5), (13, 19, 4)])                # main
    s.shrouds(4, 10, 2, 6); s.shrouds(13, 9, 2, 5)          # rigging on main and fore
    rig(-8, 5, 20, [(8, 12, 3), (15, 18, 2)])        # mizzen (on the poop deck)
    # ensign + pennant
    s.pennant(0, 23, 3, [B("pixelpirates:ship_mast", axis="y")])
    for i, c in enumerate([BLUE, BLUE, WHITE, BLUE, BLUE]): s.set(0, 23, 2 - i, B("blue_wool") if c == BLUE else B("white_wool"))
    for y in range(6, 11): s.set(0, y, -13, B("dark_oak_fence"))
    for (dy, dz), c in {(0, -14): "blue_wool", (0, -15): "white_wool", (-1, -14): "blue_wool", (-1, -15): "blue_wool",
                        (-2, -14): "white_wool", (-2, -15): "blue_wool"}.items():
        s.set(0, 10 + dy, dz, B(c))
    s.set(0, 5, -13, B("dark_oak_fence"))
    # bowsprit, jib, eagle
    top = s.sprit(1, 21, 8, 3, B("stripped_dark_oak_log", axis="z"))
    for i in range(0, 6):                         # the jib: a triangle from the sprit up to the fore mast
        for y in range(top + 1, top + 7 - i):
            s.put_if_air(0, y, 22 + i, WHITE_SAIL)
    s.set(0, 0, 21, B("pixelpirates:navy_eagle_figurehead", facing="south"))
    s.save()
    return s


# =====================================================================================
# 3. THE CORSAIR XEBEC - Crimson Corsairs
# =====================================================================================
def xebec():
    """A lean Barbary-style raider: long, narrow and low, a beaked bow and an overhanging stern gallery, three
    forward-raked masts carrying huge LATEEN sails in crimson and black (the main flies the Jolly Roger), a row of
    oars down each side, a dread skull on the beak, bow chasers and two guns a side. Light and very fast."""
    s = Ship("corsair_xebec", "pirates", 33)
    P, S_ = B("dark_oak_planks"), "dark_oak_stairs"
    RED = B("red_nether_bricks")
    DECK = 0
    def width(z):
        if z < -9: return -1
        if z <= -7: return 1 + (z + 9)         # overhanging stern
        if z >= 12: return max(0, 3 - (z - 12) // 2)
        return 3
    keel = lambda z: -4 if -5 <= z <= 13 else -3
    s.hull(-9, 18, width, keel, DECK, P, S_, rim=B("stripped_dark_oak_log", axis="z"), bilge=1.3)
    for (x, y, z), st in list(s.b.items()):
        if y == -1 and st == P: s.set(x, y, z, RED)                   # the red strake
        elif y <= -3 and st == P: s.set(x, y, z, B("blackstone"))      # tarred bottom
    s.set(0, -2, 3, B("pixelpirates:ship_waterline"))
    # OARS: fences angling down from the gunwale, seven a side
    for z in range(-3, 12, 2):
        for sx in (-1, 1):
            s.set(sx * 4, 0, z, B("dark_oak_fence")); s.set(sx * 5, -1, z, B("dark_oak_fence"))
            s.set(sx * 5, 0, z, B("dark_oak_fence")) if False else None
            s.set(sx * 4, -1, z, B("dark_oak_fence"))
    # the raised stern deck z -9..-3 around the helm, with an awning
    for z in range(-9, -2):
        w = width(z)
        for x in range(-w, w + 1):
            s.put_if_air(x, 0, z, B("dark_oak_planks"))
        s.put_if_air(-w, 1, z, B("dark_oak_fence")); s.put_if_air(w, 1, z, B("dark_oak_fence"))
    for x in range(-2, 3): s.put_if_air(x, 1, -9, B("dark_oak_fence"))
    for (x, z) in ((-2, -8), (2, -8), (-2, -4), (2, -4)):
        s.set(x, 2, z, B("dark_oak_fence")); s.set(x, 1, z, B("dark_oak_fence"))
    for z in range(-9, -3):                          # the awning, striped fore-and-aft
        for x in range(-3, 4): s.set(x, 3, z, B("red_wool") if (x + 3) % 2 == 0 else B("white_wool"))
    for z in range(-8, -3, 4):
        for x in (-2, 2): s.set(x, 3, z, B("dark_oak_fence")) if False else None
    s.set(0, 2, -6, B("lantern", hanging="true", waterlogged="false"))
    s.set(-1, 1, -7, B("pixelpirates:captains_chair", facing="north")) if False else None
    essentials(s, anchor=(1, 1, 14), map_at=(-2, 1, -7), bed=(1, -2, -6))
    s.set(1, 0, 14, B("dark_oak_planks"))
    # a hatch down into the hold under the stern deck: rum and plunder
    s.set(-1, -3, -5, B("barrel", facing="up", open="false")); s.set(1, -3, -4, B("pixelpirates:sea_chest", facing="north"))
    s.set(-1, -3, -3, B("barrel", facing="up", open="false"))
    s.set(0, 0, -2, B("dark_oak_trapdoor", facing="south", half="top", open="false", powered="false", waterlogged="false"))
    # rails along the waist
    rails(s, -2, 15, 1, B("dark_oak_fence"))
    # three raked lateen rigs: fore (z 13), main (z 5), mizzen (z -4)
    s.mast(13, -3, 10); s.lateen(13, 3, 9, 4, 5, B("red_wool"), B("dark_oak_fence"))
    s.mast(5, -3, 15)
    s.lateen(5, 2, 13, 6, 6, B("pixelpirates:crimson_sail_canvas"), B("dark_oak_fence"))
    for y in range(7, 10):                                            # the Jolly Roger in the middle of the main
        for z in range(4, 7):
            if s.get(0, y, z) and "sail" in s.get(0, y, z)[0]: s.set(0, y, z, B("pixelpirates:jolly_roger_sail_canvas"))
    s.mast(-4, 1, 11); s.lateen(-4, 4, 7, 3, 3, B("pixelpirates:black_sail_canvas"), B("dark_oak_fence"))
    for y in range(15, 17): s.set(0, y, 5, B("pixelpirates:ship_mast", axis="y"))
    for i, c in enumerate(["black_wool", "red_wool", "black_wool"]): s.set(0, 16, 4 - i, B(c))
    # beak + dread skull, bow chasers
    s.sprit(0, 19, 5, 2, B("stripped_dark_oak_log", axis="z"))
    s.set(0, -1, 19, B("pixelpirates:dread_skull_figurehead", facing="south"))
    for x in (-3, 3): cannon(s, x, 1, 8); cannon(s, x, 1, 1)
    s.set(-1, 1, 16, B("pixelpirates:ship_cannon", facing="south", loaded="false")) if False else None
    s.save()
    return s


# =====================================================================================
# 4. THE DROWNED HULK - the Drowned Fleet
# =====================================================================================
def drowned_hulk():
    """A galleon that went down and came back up: a rotten, listing hull of mossy and stripped wood, holes stove
    in along the waterline, barnacles of soul lanterns and prismarine, one mast snapped and hanging, torn black and
    grey sails with gaps, cobwebs in the rigging, seagrass kelp-green trim, a bone figurehead."""
    s = Ship("drowned_hulk", "undead", 44)
    r = s.r
    DECK = 0
    rot = [B("dark_oak_planks"), B("dark_oak_planks"), B("stripped_dark_oak_log", axis="z"), B("mossy_cobblestone"),
           B("dark_oak_planks"), B("mangrove_roots", waterlogged="false"), B("mud_bricks")]
    def width(z):
        if z < -10: return -1
        if z <= -8: return 3 + (z + 10)
        if z >= 13: return max(0, 4 - (z - 13))
        return 4
    keel = lambda z: -6 if -6 <= z <= 11 else -5
    s.hull(-10, 17, width, keel, DECK, B("dark_oak_planks"), "dark_oak_stairs", rim=B("stripped_dark_oak_log", axis="z"), bilge=1.5)
    for (x, y, z), st in list(s.b.items()):
        if st[0] == "minecraft:dark_oak_planks" and r.random() < 0.35: s.set(x, y, z, r.choice(rot))
        if y == -2 and abs(x) == width(z) and r.random() < 0.18: s.set(x, y, z, B("prismarine"))
    # holes stove in (never the keel line, keeps it connected)
    for (x, y, z) in ((-4, -1, 3), (-4, -2, 3), (4, -1, -3), (4, -2, 6), (-4, -1, 8)):
        s.set(x, y, z, None)
    s.set(-4, -2, 1, B("pixelpirates:ship_waterline"))                   # in the side planking = sea level
    # glow: soul lanterns hung on the rails, sea pickles and glow lichen-green
    rails(s, -9, 15, 1, B("dark_oak_fence"), skip={(-4, 5), (4, -1), (4, 0)})
    for (x, z) in ((-4, -6), (4, 2), (-4, 10), (4, 12)):
        s.set(x, 2, z, B("soul_lantern", hanging="false", waterlogged="false"))
    for (x, z) in ((-2, 4), (1, 9), (2, -3)): s.set(x, 1, z, B("sea_pickle", pickles="3", waterlogged="false"))
    # sterncastle: a broken cabin with cobwebs
    for z in range(-10, -4):
        w = width(z)
        for x in range(-w, w + 1):
            for y in range(1, 4):
                if (abs(x) == w or z == -10 or z == -5) and not (y == 3 and r.random() < 0.3):
                    s.set(x, y, z, r.choice(rot) if r.random() < 0.4 else B("dark_oak_planks"))
    for x in range(-2, 3):
        for z in range(-9, -5):
            if r.random() < 0.7: s.set(x, 4, z, B("dark_oak_slab", type="bottom", waterlogged="false"))
    s.set(0, 1, -5, None); s.set(0, 2, -5, None)
    s.set(1, 3, -7, B("cobweb")); s.set(-2, 2, -9, B("cobweb")); s.set(0, 3, -8, B("soul_lantern", hanging="true", waterlogged="false"))
    essentials(s, anchor=(0, 1, 14), map_at=(-2, 1, -9), bed=(2, 1, -8))
    s.set(0, 0, 14, B("dark_oak_planks"))
    s.set(-3, 1, -6, B("pixelpirates:sea_chest", facing="east"))
    # MAIN MAST standing, torn sails in black/grey with holes, cobwebs in the yards
    s.mast(4, -5, 17)
    def torn(x, y):
        if r.random() < 0.18: return None
        return B(r.choice(["black_wool", "gray_wool", "black_wool", "light_gray_wool"]))
    for y in range(4, 14):
        w = 4 - (1 if y == 4 else 0)
        for x in range(-w, w + 1):
            st = torn(x, y)
            if st and (y > 11 or abs(x) < 4 or r.random() < 0.6): s.set(x, y, 5, st)
    for x in range(-5, 6): s.set(x, 14, 5, B("dark_oak_fence"))
    for x in range(-4, 5):                                   # keep every sail column hanging from the yard
        for y in range(13, 3, -1):
            if s.get(x, y, 5) is None and s.get(x, y + 1, 5) is not None: s.set(x, y, 5, B("cobweb")); break
    s.set(-3, 15, 5, B("cobweb")); s.set(2, 13, 4, B("cobweb")) if False else None
    # FORE MAST snapped at the top, the broken spar hanging down over the side
    s.mast(11, -5, 7)
    for i in range(1, 6):                      # the spar hangs at a slant, each step overlapping the last
        s.set(i, 7 - i, 11, B("stripped_dark_oak_log", axis="x")); s.set(i, 8 - i, 11, B("stripped_dark_oak_log", axis="x"))
    s.set(5, 1, 11, B("stripped_dark_oak_log", axis="x"))
    for y in range(4, 7):
        for x in range(-3, 1): s.set(x, y, 12, torn(x, y) or B("cobweb"))
    # MIZZEN a stump with a ragged green pennant
    s.mast(-7, 1, 9)
    for i, c in enumerate(["green_wool", "lime_wool", "green_wool"]): s.set(0, 9, -8 - i, B(c))
    for y in range(5, 9):
        for z in (-6,):
            for x in range(-2, 3):
                st = torn(x, y)
                if st and s.get(x, y, z) is None: s.set(x, y, z, st)
    # bone figurehead, bowsprit bones
    s.sprit(1, 18, 4, 2, B("bone_block", axis="z"))
    s.set(0, 0, 18, B("pixelpirates:dread_skull_figurehead", facing="south"))
    for x in (-4, 4): cannon(s, x, 1, 7); cannon(s, x, 1, -2)
    # drowned dressing: glow lichen crusting the hull and weed trailing from the rails (mirrored, so it stays level)
    for z in range(-8, 15):
        w = width(z)
        for y in range(-4, 1):
            if r.random() < 0.16:
                for sx in (-1, 1):
                    if (sx * w, y, z) in s.b and (sx * (w + 1), y, z) not in s.b:
                        s.set(sx * (w + 1), y, z, B("glow_lichen", **{"east": "true" if sx < 0 else "false", "west": "true" if sx > 0 else "false",
                                                                    "north": "false", "south": "false", "up": "false", "down": "false", "waterlogged": "false"}))
        if z % 3 == 0 and r.random() < 0.7:
            for sx in (-1, 1):
                for y in range(-2, 1):
                    if (sx * w, y, z) in s.b and (sx * (w + 1), y, z) not in s.b:
                        s.set(sx * (w + 1), y, z, B("vine", **{"east": "true" if sx < 0 else "false", "west": "true" if sx > 0 else "false",
                                                             "north": "false", "south": "false", "up": "false"}))
    for x in (-2, -1, 1, 2): s.set(x, 2, -10, B("glass_pane") if abs(x) == 1 else None)        # broken stern windows
    s.set(-2, 2, -10, B("cobweb"))
    s.save()
    return s


# =====================================================================================
# preview (isometric + side + top) - flat-shaded cubes, good enough to judge the silhouette
# =====================================================================================
COL = {
    "ship_helm": (160, 110, 60), "ship_mast": (120, 84, 50), "ship_cannon": (50, 50, 55), "ship_waterline": (40, 90, 170),
    "anchor_block": (80, 80, 90), "map_block": (200, 180, 120), "ship_bedroll": (160, 40, 40),
    "ghostwood": (150, 215, 225), "spectral": (150, 235, 235), "bone_planks": (225, 220, 195), "warped": (40, 110, 110),
    "dark_prismarine": (50, 90, 80), "spruce": (110, 80, 50), "dark_oak": (70, 48, 30), "oak": (160, 130, 80), "birch": (210, 200, 160), "crimson": (130, 50, 70),
    "green_terracotta": (76, 83, 42), "red_terracotta": (143, 61, 46), "red_nether": (90, 20, 20), "blue_terracotta": (74, 60, 91),
    "white_concrete": (220, 222, 222), "gold_block": (240, 200, 60), "blackstone": (40, 36, 42),
    "white_sail": (235, 232, 220), "striped_sail": (190, 120, 110), "crimson_sail": (160, 30, 30), "black_sail": (35, 35, 40),
    "jolly_roger": (20, 20, 20), "white_wool": (235, 235, 235), "black_wool": (30, 30, 35), "gray_wool": (80, 80, 85),
    "light_gray_wool": (150, 150, 150), "red_wool": (170, 40, 35), "green_wool": (80, 110, 30), "lime_wool": (120, 190, 40),
    "blue_wool": (50, 60, 160), "glass": (180, 220, 230), "lantern": (250, 200, 90), "soul_lantern": (90, 220, 230),
    "barrel": (130, 95, 55), "cargo_crate": (170, 130, 80), "sea_chest": (120, 80, 40), "cobweb": (230, 230, 230),
    "prismarine": (90, 160, 150), "mossy": (90, 110, 80), "mangrove": (80, 60, 40), "mud": (140, 110, 90), "bone": (220, 215, 190),
    "figurehead": (220, 180, 70), "chain": (60, 60, 70), "ladder": (150, 110, 60), "sea_pickle": (100, 150, 60),
}


def color(name):
    n = name.split(":")[1]
    for k in sorted(COL, key=len, reverse=True):           # most specific name first (crimson_sail before crimson)
        if k in n: return COL[k]
    return (150, 150, 150)


def preview(s):
    from PIL import Image, ImageDraw
    PREV.mkdir(parents=True, exist_ok=True)
    blocks = [(p, st[0]) for p, st in s.b.items()]
    def iso(yaw_deg, size=520):
        a = math.radians(yaw_deg)
        ca, sa = math.cos(a), math.sin(a)
        def proj(x, y, z):
            rx, rz = x * ca - z * sa, x * sa + z * ca
            return rx, -y - rz * 0.5, rz
        faces = []
        for (x, y, z), n in blocks:
            c = color(n)
            for (nx, ny, nz), quad, shade in (
                    ((0, 1, 0), [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)], 1.0),
                    ((1, 0, 0), [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)], 0.8),
                    ((-1, 0, 0), [(0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1)], 0.8),
                    ((0, 0, 1), [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)], 0.65),
                    ((0, 0, -1), [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], 0.65)):
                if (x + nx, y + ny, z + nz) in s.b: continue
                pts = [proj(x + qx, y + qy, z + qz) for qx, qy, qz in quad]
                vn = nx * sa + nz * ca                         # toward the viewer?
                if ny == 0 and vn > 0.05: continue
                depth = sum(p[2] for p in pts) / 4
                faces.append((depth, [(p[0], p[1]) for p in pts], tuple(int(v * shade) for v in c)))
        faces.sort(key=lambda f: -f[0])
        xs = [q[0] for f in faces for q in f[1]]; ys = [q[1] for f in faces for q in f[1]]
        sc = (size - 30) / max(max(xs) - min(xs), max(ys) - min(ys))
        img = Image.new("RGB", (size, size), (70, 110, 150))
        d = ImageDraw.Draw(img)
        for _, pts, c in faces:
            d.polygon([((px - min(xs)) * sc + 15, (py - min(ys)) * sc + 15) for px, py in pts], fill=c, outline=tuple(max(0, v - 25) for v in c))
        return img
    views = [iso(215), iso(35), iso(270), iso(180)]
    sheet = Image.new("RGB", (520 * 4, 520))
    for i, v in enumerate(views): sheet.paste(v, (i * 520, 0))
    sheet.save(PREV / f"{s.name}.png")


SHIPS = {"merchantman": merchantman, "navy_frigate": frigate, "corsair_xebec": xebec, "drowned_hulk": drowned_hulk}

if __name__ == "__main__":
    names = [a for a in sys.argv[1:] if not a.startswith("--")] or list(SHIPS)
    for n in names:
        preview(SHIPS[n]())
