"""HOMESTEAD shaped block models (json in src/main/resources, previews in tools/previews/homestead/).

    python tools/homestead_models.py            # every model
    python tools/homestead_models.py rum_still  # just one

Each builder writes the block model(s), the blockstate and the item model, and renders a preview. Models are authored
facing NORTH (the front is the -z face); blockstates rotate them for the other facings.
"""
import sys
from pathlib import Path

from blockmodels import BM, blockstate_facing, variants_facing, item_model, write_blockstate, preview

PREV = Path(__file__).parent / "previews/homestead"
BUILT = {}


def model(fn):
    BUILT[fn.__name__] = fn
    return fn


# =====================================================================================
# #12 RUM
# =====================================================================================
@model
def rum_still():
    m = BM("rum_still", {"cu": "minecraft:block/copper_block", "cut": "minecraft:block/cut_copper", "ox": "minecraft:block/exposed_copper",
                         "iron": "minecraft:block/anvil", "keg": "minecraft:block/barrel_side", "kegtop": "minecraft:block/barrel_top",
                         "coal": "minecraft:block/coal_block", "particle": "minecraft:block/copper_block"})
    # iron legs and a firebox grate
    for x, z in ((2, 2), (9, 2), (2, 9), (9, 9)):
        m.box([x, 0, z], [x + 1, 2, z + 1], all="#iron")
    m.box([2, 2, 2], [10, 3, 10], all="#iron")
    # the pot: a squat bulb (three stacked boxes), a neck, the onion dome
    m.box([2, 3, 2], [10, 4, 10], all="#ox")
    m.box([1.5, 4, 1.5], [10.5, 9, 10.5], all="#cu")
    m.box([2.5, 9, 2.5], [9.5, 10, 9.5], all="#cut")
    m.box([4, 10, 4], [8, 12, 8], all="#cu")
    m.box([3.5, 12, 3.5], [8.5, 14, 8.5], all="#cut")
    m.box([5, 14, 5], [7, 15, 7], all="#ox")
    # the swan neck: out of the dome, across to the condenser keg
    m.box([7, 13, 5.5], [13, 14, 6.5], all="#cu")
    m.box([12, 7, 5.5], [13, 13, 6.5], all="#cu")
    # the condenser keg (worm tub) on the right, with its spout at the front
    m.box([10.5, 0, 7], [15.5, 7, 13], faces={"north": "#keg", "south": "#keg", "east": "#keg", "west": "#keg", "up": "#kegtop", "down": "#kegtop"})
    m.box([10.3, 1, 6.8], [15.7, 2, 13.2], all="#iron", skip=("up", "down"))
    m.box([10.3, 5, 6.8], [15.7, 6, 13.2], all="#iron", skip=("up", "down"))
    m.box([12.5, 2.5, 5], [13.5, 3.5, 7], all="#cu")
    m.box([12.5, 1.5, 5], [13.5, 2.5, 6], all="#cu")
    m.write()
    lit = BM("rum_still_lit", dict(m.textures))
    lit.elements = [dict(e) for e in m.elements]
    lit.box([3, 2.1, 3], [9, 2.9, 9], all="#coal")                    # (glowing embers - LIT)
    lit.textures["coal"] = "minecraft:block/magma"
    lit.write()
    variants_facing("rum_still", {"lit=false": "pixelpirates:block/rum_still", "lit=true": "pixelpirates:block/rum_still_lit"})
    item_model("rum_still", "pixelpirates:block/rum_still")
    return m


@model
def aging_cask():
    m = BM("aging_cask", {"side": "minecraft:block/barrel_side", "end": "minecraft:block/barrel_top", "iron": "minecraft:block/polished_blackstone",
                          "wood": "minecraft:block/dark_oak_planks", "spruce": "minecraft:block/spruce_log", "particle": "minecraft:block/barrel_side"})
    # the cradle: two chocks
    m.box([1, 0, 2], [15, 3, 4], all="#wood")
    m.box([1, 0, 12], [15, 3, 14], all="#wood")
    # the cask lying along z (staves run along its length: side textures turned 90)
    m.box([2, 2, 1], [14, 14, 15], faces={"north": "#end", "south": "#end", "east": "#side", "west": "#side", "up": "#side", "down": "#side"},
          uvrot={"east": 90, "west": 90, "up": 90, "down": 90})
    m.box([1.9, 1.9, 3], [14.1, 14.1, 4], all="#iron", skip=("north", "south"))           # hoops
    m.box([1.9, 1.9, 12], [14.1, 14.1, 13], all="#iron", skip=("north", "south"))
    # the tap in the head
    m.box([7, 4, 0], [9, 6, 1], all="#iron")
    m.box([7.5, 3, 0], [8.5, 4, 0.8], all="#iron")
    m.box([7.5, 6, 0.2], [8.5, 7.5, 0.8], all="#spruce")
    m.write()
    blockstate_facing("aging_cask")
    item_model("aging_cask", "pixelpirates:block/aging_cask")
    return m


# =====================================================================================
# #5 PALM & TROPICAL BUILDING
# =====================================================================================
@model
def woven_palm_screen():
    """A glass-pane shaped woven screen: the vanilla pane templates with our weave as pane + edge."""
    import json
    from blockmodels import RES
    t = {"pane": "pixelpirates:block/woven_palm_screen", "edge": "pixelpirates:block/woven_palm_screen_edge"}
    for part in ("post", "side", "side_alt", "noside", "noside_alt"):
        (RES / "models/block" / f"woven_palm_screen_{part}.json").write_bytes(
            json.dumps({"parent": f"minecraft:block/template_glass_pane_{part}", "textures": t}, indent=1).encode())
    M = "pixelpirates:block/woven_palm_screen_"
    write_blockstate("woven_palm_screen", {"multipart": [
        {"apply": {"model": M + "post"}},
        {"when": {"north": "true"}, "apply": {"model": M + "side"}},
        {"when": {"east": "true"}, "apply": {"model": M + "side", "y": 90}},
        {"when": {"south": "true"}, "apply": {"model": M + "side_alt"}},
        {"when": {"west": "true"}, "apply": {"model": M + "side_alt", "y": 90}},
        {"when": {"north": "false"}, "apply": {"model": M + "noside"}},
        {"when": {"east": "false"}, "apply": {"model": M + "noside_alt"}},
        {"when": {"south": "false"}, "apply": {"model": M + "noside_alt", "y": 90}},
        {"when": {"west": "false"}, "apply": {"model": M + "noside", "y": 270}}]})
    (RES / "models/item/woven_palm_screen.json").write_bytes(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "pixelpirates:block/woven_palm_screen"}}, indent=1).encode())
    m = BM("_screen_preview", t)
    m.box([7, 0, 0], [9, 16, 16], faces={"east": "#pane", "west": "#pane", "north": "#edge", "south": "#edge", "up": "#edge", "down": "#edge"})
    return m


@model
def rope_ladder():
    import json
    from blockmodels import RES
    m = BM("rope_ladder", {"texture": "pixelpirates:block/rope_ladder", "particle": "pixelpirates:block/rope_ladder"}, ao=False)
    m.box([0, 0, 15.2], [16, 16, 15.2], faces={"north": "#texture", "south": "#texture"})
    m.write()
    blockstate_facing("rope_ladder")
    (RES / "models/item/rope_ladder.json").write_bytes(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "pixelpirates:block/rope_ladder"}}, indent=1).encode())
    return m


@model
def rope_bridge():
    m = BM("rope_bridge", {"slat": "minecraft:block/spruce_planks", "rope": "pixelpirates:block/rope", "post": "minecraft:block/spruce_log",
                           "particle": "minecraft:block/spruce_planks"})
    for z in range(0, 16, 4):                                                           # slats across, gaps between
        m.box([0.5, 0, z + 0.3], [15.5, 1.5, z + 3.3], all="#slat")
    m.box([2, 1.5, 0], [3, 2, 16], all="#rope")                                        # the lashing ropes under foot
    m.box([13, 1.5, 0], [14, 2, 16], all="#rope")
    for x in (0, 15):                                                                   # the rails
        m.box([x, 10, 0], [x + 1, 11, 16], all="#rope")
        m.box([x, 0, 7], [x + 1, 11, 9], all="#post")
        for z in (2, 5, 11, 14):                                                        # hand lines down to the slats
            m.box([x + 0.25, 1.5, z], [x + 0.75, 10, z + 0.5], all="#rope")
    m.write()
    from blockmodels import write_blockstate as wb
    wb("rope_bridge", {"variants": {"axis=z": {"model": "pixelpirates:block/rope_bridge"},
                                    "axis=x": {"model": "pixelpirates:block/rope_bridge", "y": 90}}})
    item_model("rope_bridge", "pixelpirates:block/rope_bridge")
    return m


@model
def tiki_torch():
    m = BM("tiki_torch", {"pole": "pixelpirates:block/palm_log", "top": "pixelpirates:block/palm_log_top", "weave": "pixelpirates:block/thatch",
                          "fire": "minecraft:block/fire_0", "particle": "pixelpirates:block/palm_log"}, ao=False)
    m.box([7, 0, 7], [9, 11, 9], all="#pole")
    m.box([6.5, 3, 6.5], [9.5, 4, 9.5], all="#weave")                                   # a lashing
    m.box([5.5, 11, 5.5], [10.5, 12, 10.5], all="#weave")                               # the basket
    m.box([5, 12, 5], [11, 15, 11], all="#weave", skip=("up",))
    m.box([5.5, 14.9, 5.5], [10.5, 15, 10.5], all="#top", skip=("down",))
    for (x, z, r) in ((8, 8, 45), (8, 8, -45)):
        m.box([4, 14.5, 8], [12, 22.5, 8], faces={"north": "#fire", "south": "#fire"}, rot=("y", r, [8, 8, 8]), shade=False,
              uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]})
    m.write()
    from blockmodels import write_blockstate as wb
    wb("tiki_torch", {"variants": {"": {"model": "pixelpirates:block/tiki_torch"}}})
    item_model("tiki_torch", "pixelpirates:block/tiki_torch")
    return m


# =====================================================================================
# #2 PIRATE FURNITURE
# =====================================================================================
DO = "minecraft:block/dark_oak_planks"


@model
def captains_desk():
    m = BM("captains_desk", {"wood": DO, "log": "minecraft:block/stripped_dark_oak_log", "gold": "minecraft:block/gold_block",
                             "chart": "pixelpirates:block/sea_chart", "feather": "minecraft:item/feather", "ink": "minecraft:block/black_concrete",
                             "candle": "minecraft:block/candle", "particle": DO})
    m.box([0, 13, 2], [16, 15, 16], all="#wood")                                    # the desktop
    m.box([1, 0, 3], [5, 13, 15], all="#wood")                                      # two drawer pedestals
    m.box([11, 0, 3], [15, 13, 15], all="#wood")
    m.box([5, 5, 14], [11, 13, 15], all="#log")                                     # the modesty panel
    for x0 in (1, 11):
        for y0 in (1.5, 5.5, 9.5):
            m.box([x0 + 0.5, y0, 2.6], [x0 + 3.5, y0 + 3, 3], all="#log")          # drawer fronts
            m.box([x0 + 1.5, y0 + 1.2, 2.2], [x0 + 2.5, y0 + 1.8, 2.6], all="#gold")   # knobs
    m.box([2.5, 15, 5], [9.5, 15.1, 12], faces={"up": "#chart"})                    # a chart, weighted by the inkwell
    m.box([10, 15, 10], [11.5, 16.5, 11.5], all="#ink")
    m.box([10.6, 15.5, 10.6], [10.9, 19, 10.9], all="#ink")
    m.box([10.75, 16, 8], [10.75, 20, 12], faces={"east": "#feather", "west": "#feather"}, rot=("x", 22.5, [10.75, 16, 10.75]))
    m.box([12.5, 15, 4], [13.5, 17.5, 5], all="#candle", uv={"north": [0, 8, 2, 14], "south": [0, 8, 2, 14], "east": [0, 8, 2, 14], "west": [0, 8, 2, 14]})
    m.write()
    blockstate_facing("captains_desk")
    item_model("captains_desk", "pixelpirates:block/captains_desk")
    return m


@model
def sea_chest():
    m = BM("sea_chest", {"wood": DO, "band": "minecraft:block/polished_blackstone", "gold": "minecraft:block/gold_block",
                         "lid": "minecraft:block/stripped_dark_oak_log", "particle": DO})
    m.box([1, 0, 3], [15, 9, 13], all="#wood")
    m.box([1, 9, 3], [15, 12, 13], all="#lid")
    m.box([1.5, 12, 4], [14.5, 13, 12], all="#lid")                                   # the domed lid
    for x in (2.5, 12.5):                                                            # iron straps over the lid
        m.box([x, 0, 2.8], [x + 1, 13.2, 13.2], all="#band", skip=("down",))
    m.box([0.8, 8.5, 2.8], [15.2, 9.5, 13.2], all="#band", skip=("up", "down"))       # the lid's rim
    m.box([7, 6, 2.5], [9, 9.5, 2.8], all="#gold")                                    # the lock plate
    m.box([7.6, 6.6, 2.3], [8.4, 7.6, 2.5], all="#band")
    for x, z in ((0.8, 2.8), (14.2, 2.8), (0.8, 12.2), (14.2, 12.2)):                 # brass corner caps
        m.box([x, 0, z], [x + 1, 2, z + 1], all="#gold")
    m.write()
    blockstate_facing("sea_chest")
    item_model("sea_chest", "pixelpirates:block/sea_chest")
    return m


@model
def cargo_crate():
    m = BM("cargo_crate", {"side": "pixelpirates:block/cargo_crate", "top": "pixelpirates:block/cargo_crate_top", "particle": "pixelpirates:block/cargo_crate"})
    m.box([1, 0, 1], [15, 14, 15], faces={"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#top"})
    m.write()
    blockstate_facing("cargo_crate")
    item_model("cargo_crate", "pixelpirates:block/cargo_crate")
    return m


@model
def treasure_pedestal():
    m = BM("treasure_pedestal", {"stone": "minecraft:block/polished_blackstone", "trim": "minecraft:block/gold_block",
                                 "velvet": "minecraft:block/red_wool", "brick": "minecraft:block/polished_blackstone_bricks",
                                 "particle": "minecraft:block/polished_blackstone"})
    m.box([2, 0, 2], [14, 2, 14], all="#stone")
    m.box([2.5, 2, 2.5], [13.5, 2.5, 13.5], all="#trim")
    m.box([4, 2.5, 4], [12, 8, 12], all="#brick")
    m.box([3, 8, 3], [13, 10, 13], all="#stone")
    m.box([3.5, 9.5, 3.5], [12.5, 10, 12.5], all="#trim", skip=("up", "down"))
    m.box([4, 10, 4], [12, 11, 12], all="#velvet")
    m.write()
    blockstate_facing("treasure_pedestal")
    item_model("treasure_pedestal", "pixelpirates:block/treasure_pedestal")
    return m


@model
def rum_rack():
    tex = {"wood": DO, "glass": "minecraft:block/brown_terracotta", "raw": "minecraft:block/yellow_terracotta",
           "cork": "minecraft:block/stripped_oak_log", "particle": DO}
    slots = [(3.2, 1), (8, 1), (12.8, 1), (3.2, 8.5), (8, 8.5), (12.8, 8.5)]
    last = None
    for n in range(7):
        m = BM(f"rum_rack_{n}", tex)
        m.box([0, 0, 15], [16, 16, 16], all="#wood")                                   # the backboard
        m.box([0, 0, 10], [1, 16, 15], all="#wood")
        m.box([15, 0, 10], [16, 16, 15], all="#wood")
        for y in (0, 7.5, 15):
            m.box([1, y, 10], [15, y + 1, 15], all="#wood")
        for x in (5.3, 10.3):
            m.box([x, 1, 11], [x + 0.5, 15, 15], all="#wood")
        for i in range(n):                                                            # the bottles, lying cork-out
            x, y = slots[i]
            m.box([x - 1.3, y, 11], [x + 1.3, y + 2.6, 15], all="#glass" if i % 3 else "#raw")
            m.box([x - 0.5, y + 0.8, 10.2], [x + 0.5, y + 1.8, 11], all="#cork")
        m.write()
        last = last or m
    from blockmodels import variants_facing as vf
    vf("rum_rack", {f"bottles={n}": f"pixelpirates:block/rum_rack_{n}" for n in range(7)})
    item_model("rum_rack", "pixelpirates:block/rum_rack_4")
    import copy
    full = BM("rum_rack_4", tex)
    import json
    from blockmodels import RES
    full.elements = json.loads((RES / "models/block/rum_rack_4.json").read_text())["elements"]
    return full


@model
def hanging_net():
    m = BM("hanging_net", {"net": "pixelpirates:block/fishing_net", "rope": "pixelpirates:block/rope", "cork": "minecraft:block/stripped_oak_log",
                           "particle": "pixelpirates:block/fishing_net"}, ao=False)
    m.box([0, 0, 15], [16, 16, 15], faces={"north": "#net", "south": "#net"})
    m.box([0, 15, 14.5], [16, 16, 15.5], all="#rope")
    for x, y in ((3, 5), (11, 9), (7, 2)):                                             # cork floats caught in it
        m.box([x, y, 14.2], [x + 2, y + 2, 15], all="#cork")
    m.write()
    blockstate_facing("hanging_net")
    import json
    from blockmodels import RES
    (RES / "models/item/hanging_net.json").write_bytes(json.dumps(
        {"parent": "minecraft:item/generated", "textures": {"layer0": "pixelpirates:block/fishing_net"}}, indent=1).encode())
    return m


@model
def rope_coil():
    m = BM("rope_coil", {"rope": "pixelpirates:block/rope", "particle": "pixelpirates:block/rope"})
    for i, (lo, hi) in enumerate(((2, 14), (2.5, 13.5), (3, 13))):
        y = i * 1.5
        m.box([lo, y, lo], [hi, y + 1.5, lo + 2], all="#rope")
        m.box([lo, y, hi - 2], [hi, y + 1.5, hi], all="#rope")
        m.box([lo, y, lo + 2], [lo + 2, y + 1.5, hi - 2], all="#rope")
        m.box([hi - 2, y, lo + 2], [hi, y + 1.5, hi - 2], all="#rope")
    m.box([6, 0, 1], [7.5, 1, 3], all="#rope")                                         # the loose end
    m.write()
    blockstate_facing("rope_coil")
    item_model("rope_coil", "pixelpirates:block/rope_coil")
    return m


@model
def hanging_rope():
    t = {"rope": "pixelpirates:block/rope", "particle": "pixelpirates:block/rope"}
    m = BM("hanging_rope", t)
    m.box([7, 0, 7], [9, 16, 9], all="#rope")
    m.write()
    e = BM("hanging_rope_end", t)
    e.box([7, 3, 7], [9, 16, 9], all="#rope")
    e.box([6, 0, 6], [10, 3, 10], all="#rope")                                         # the knot
    e.write()
    from blockmodels import write_blockstate as wb
    wb("hanging_rope", {"variants": {"end=false": {"model": "pixelpirates:block/hanging_rope"}, "end=true": {"model": "pixelpirates:block/hanging_rope_end"}}})
    item_model("hanging_rope", "pixelpirates:block/hanging_rope_end")
    return e


@model
def ships_wheel():
    m = BM("ships_wheel", {"wood": "minecraft:block/stripped_dark_oak_log", "hub": "minecraft:block/gold_block", "grip": DO,
                           "particle": "minecraft:block/stripped_dark_oak_log"})
    m.box([6.5, 6.5, 13], [9.5, 9.5, 16], all="#hub")
    m.box([7.5, 0, 14], [8.5, 16, 15], all="#wood")                                    # spokes
    m.box([0, 7.5, 14], [16, 8.5, 15], all="#wood")
    m.box([7.5, 0, 14.05], [8.5, 16, 14.95], all="#wood", rot=("z", 45, [8, 8, 14.5]))
    m.box([0, 7.5, 14.05], [16, 8.5, 14.95], all="#wood", rot=("z", 45, [8, 8, 14.5]))
    for r in (0, 45):                                                                  # the rim: two squares = an octagon
        rot = ("z", r, [8, 8, 14.5]) if r else None
        m.box([2.5, 2.5, 13.8], [13.5, 3.5, 15.2], all="#grip", rot=rot)
        m.box([2.5, 12.5, 13.8], [13.5, 13.5, 15.2], all="#grip", rot=rot)
        m.box([2.5, 3.5, 13.8], [3.5, 12.5, 15.2], all="#grip", rot=rot)
        m.box([12.5, 3.5, 13.8], [13.5, 12.5, 15.2], all="#grip", rot=rot)
    m.write()
    blockstate_facing("ships_wheel")
    item_model("ships_wheel", "pixelpirates:block/ships_wheel")
    return m


@model
def display_cannon():
    blockstate_facing("display_cannon", model="pixelpirates:block/ship_cannon")
    item_model("display_cannon", "pixelpirates:block/ship_cannon")
    import json
    from blockmodels import RES
    m = BM("ship_cannon", json.loads((RES / "models/block/ship_cannon.json").read_text())["textures"])
    m.elements = json.loads((RES / "models/block/ship_cannon.json").read_text())["elements"]
    return m


@model
def map_table():
    m = BM("map_table", {"wood": DO, "log": "minecraft:block/dark_oak_log", "chart": "pixelpirates:block/sea_chart",
                         "compass": "minecraft:item/compass_16", "candle": "minecraft:block/candle", "brass": "minecraft:block/gold_block",
                         "particle": DO})
    m.box([0, 12, 0], [16, 14, 16], all="#wood")
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        m.box([x, 0, z], [x + 2, 12, z + 2], all="#log")
    m.box([1, 3, 1.5], [15, 4, 2.5], all="#log")                                       # stretchers
    m.box([1, 3, 13.5], [15, 4, 14.5], all="#log")
    m.box([1.5, 14, 1.5], [14.5, 14.1, 14.5], faces={"up": "#chart"})
    m.box([10, 14.1, 3], [13, 14.3, 6], faces={"up": "#compass"})
    m.box([9.8, 14.05, 2.8], [13.2, 14.35, 6.2], all="#brass", skip=("up",))
    m.box([2.5, 14, 11.5], [3.5, 16.5, 12.5], all="#candle", uv={"north": [0, 8, 2, 14], "south": [0, 8, 2, 14], "east": [0, 8, 2, 14], "west": [0, 8, 2, 14]})
    m.write()
    blockstate_facing("map_table")
    item_model("map_table", "pixelpirates:block/map_table")
    return m


@model
def captains_chair():
    m = BM("captains_chair", {"wood": DO, "velvet": "minecraft:block/red_wool", "gold": "minecraft:block/gold_block", "particle": DO})
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
        m.box([x, 0, z], [x + 2, 8, z + 2], all="#wood")
    m.box([2, 7, 2], [14, 8.5, 14], all="#wood")
    m.box([2.5, 8.5, 2.5], [13.5, 10, 12], all="#velvet")                               # the seat cushion
    m.box([2, 8.5, 12], [14, 23, 14], all="#wood")                                      # the tall back
    m.box([3.5, 10, 11.5], [12.5, 21, 12], all="#velvet")
    m.box([4, 23, 12], [12, 24.5, 14], all="#gold")                                     # the crest
    m.box([2, 8.5, 3], [3.5, 13, 12], all="#wood")                                      # armrests
    m.box([12.5, 8.5, 3], [14, 13, 12], all="#wood")
    m.box([1.8, 12.5, 2.5], [3.7, 13.5, 4], all="#gold")
    m.box([12.3, 12.5, 2.5], [14.2, 13.5, 4], all="#gold")
    m.write()
    blockstate_facing("captains_chair")
    item_model("captains_chair", "pixelpirates:block/captains_chair")
    return m


@model
def barrel_stool():
    m = BM("barrel_stool", {"side": "minecraft:block/barrel_side", "top": "minecraft:block/barrel_top", "velvet": "minecraft:block/red_wool",
                            "particle": "minecraft:block/barrel_side"})
    m.box([3, 0, 3], [13, 9, 13], faces={"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#top"})
    m.box([3.5, 9, 3.5], [12.5, 10, 12.5], all="#velvet")
    m.write()
    blockstate_facing("barrel_stool")
    item_model("barrel_stool", "pixelpirates:block/barrel_stool")
    return m


# =====================================================================================
# #3 TREASURE HOARD - a pile of coins that grows with the hoard (level 0-7)
# =====================================================================================
@model
def treasure_hoard():
    import random
    t = {"coins": "pixelpirates:block/coin_pile", "gold": "minecraft:block/gold_block", "cup": "minecraft:block/raw_gold_block",
         "gem": "minecraft:block/emerald_block", "wood": DO, "particle": "pixelpirates:block/coin_pile"}
    last = None
    for lvl in range(8):
        m = BM(f"treasure_hoard_{lvl}", t)
        m.box([1, 0, 1], [15, 1, 15], all="#wood")                                     # a plank base the pile sits on
        rng = random.Random(40 + lvl)
        for layer in range(lvl + 1):                                                   # stacked, narrowing slabs of coins
            inset = 1 + layer * (6 / 8.0) * (1 + (layer > 3))
            if inset >= 7.5:
                break
            m.box([1 + inset, 1 + layer * 2, 1 + inset], [15 - inset, 3 + layer * 2, 15 - inset], all="#coins")
        for i in range(lvl * 2):                                                       # coin stacks + loot poking out
            x, z = rng.uniform(2, 12), rng.uniform(2, 12)
            h = rng.uniform(1, 2 + lvl)
            m.box([x, 1, z], [x + 1.5, 1 + h, z + 1.5], all="#gold")
        if lvl >= 4:
            m.box([10, 1, 3], [12, 5, 5], all="#cup")                                  # a goblet
        if lvl >= 6:
            m.box([4, 2 + lvl, 9], [5.5, 3.5 + lvl, 10.5], all="#gem")                  # a gem on top
        m.write()
        last = m
    from blockmodels import write_blockstate as wb
    wb("treasure_hoard", {"variants": {f"level={l}": {"model": f"pixelpirates:block/treasure_hoard_{l}"} for l in range(8)}})
    item_model("treasure_hoard", "pixelpirates:block/treasure_hoard_4")
    return last


# =====================================================================================
# #13 FISHING: trap net, lobster pot, trophy plaques
# =====================================================================================
@model
def fish_trap():
    m = BM("fish_trap", {"net": "pixelpirates:block/fishing_net", "stick": "minecraft:block/stripped_oak_log", "cork": "minecraft:block/stripped_birch_log",
                         "particle": "pixelpirates:block/fishing_net"}, ao=False)
    for x, z in ((0, 0), (15, 0), (0, 15), (15, 15)):                                   # corner poles
        m.box([x, 0, z], [x + 1, 12, z + 1], all="#stick")
    m.box([0.5, 1, 0.5], [15.5, 11, 15.5], all="#net", skip=("up",))                   # the net bag
    m.box([0, 11, 0], [16, 12, 1], all="#stick")                                       # top frame
    m.box([0, 11, 15], [16, 12, 16], all="#stick")
    m.box([0, 11, 1], [1, 12, 15], all="#stick")
    m.box([15, 11, 1], [16, 12, 15], all="#stick")
    for x, z in ((3, -0.5), (12, 15.5), (-0.5, 8)):                                    # floats on the frame
        m.box([x, 11, z], [x + 2, 13, z + 1], all="#cork")
    m.write()
    from blockmodels import write_blockstate as wb
    wb("fish_trap", {"variants": {"waterlogged=false": {"model": "pixelpirates:block/fish_trap"}, "waterlogged=true": {"model": "pixelpirates:block/fish_trap"}}})
    item_model("fish_trap", "pixelpirates:block/fish_trap")
    return m


@model
def lobster_pot():
    m = BM("lobster_pot", {"slat": "minecraft:block/spruce_planks", "net": "pixelpirates:block/fishing_net", "rope": "pixelpirates:block/rope",
                           "particle": "minecraft:block/spruce_planks"}, ao=False)
    m.box([2, 0, 2], [14, 1, 14], all="#slat")                                         # the base
    for x in (2, 13):                                                                  # hooped slats over the top
        m.box([x, 1, 2], [x + 1, 9, 14], all="#slat")
    m.box([2, 9, 2], [14, 10, 14], faces={"up": "#slat", "down": "#slat", "north": "#slat", "south": "#slat"})
    m.box([2.2, 1, 2.2], [13.8, 9, 13.8], all="#net", skip=("up", "down"))              # netting sides
    m.box([6, 3, 1.8], [10, 7, 2.2], all="#rope")                                      # the funnel mouth ring
    m.box([7.5, 10, 7.5], [8.5, 13, 8.5], all="#rope")                                 # the rope to the buoy
    m.write()
    from blockmodels import write_blockstate as wb
    wb("lobster_pot", {"variants": {"waterlogged=false": {"model": "pixelpirates:block/lobster_pot"}, "waterlogged=true": {"model": "pixelpirates:block/lobster_pot"}}})
    item_model("lobster_pot", "pixelpirates:block/lobster_pot")
    return m


def trophy(name, bill=False, spines=False):
    m = BM(name, {"wood": DO, "fish": f"pixelpirates:block/{name}", "gold": "minecraft:block/gold_block", "particle": DO})
    m.box([1, 2, 15], [15, 14, 16], all="#wood")                                       # the plaque
    m.box([0.5, 1.5, 15.2], [15.5, 2.5, 16], all="#gold")                               # its brass rail
    m.box([3, 5, 13.5], [12, 10, 15], all="#fish")                                     # the body
    m.box([12, 6, 13.8], [13, 9, 14.8], all="#fish")                                   # tail stalk
    m.box([13, 4, 14], [15, 11, 14.6], all="#fish")                                    # the tail
    m.box([5, 10, 14], [10, 12, 14.6], all="#fish")                                    # dorsal fin
    if spines:
        for x in (5.5, 7, 8.5):
            m.box([x, 12, 14.1], [x + 0.6, 13.5, 14.5], all="#fish")
    if bill:
        m.box([0.5, 7.2, 14], [3, 7.8, 14.6], all="#fish")                             # the sword
    m.box([3.6, 8, 13.3], [4.4, 8.8, 13.5], all="#gold")                                # glass eye
    m.write()
    blockstate_facing(name)
    item_model(name, f"pixelpirates:block/{name}")
    return m


@model
def golden_marlin_trophy(): return trophy("golden_marlin_trophy", bill=True, spines=True)


@model
def ghost_swordfish_trophy(): return trophy("ghost_swordfish_trophy", bill=True)


@model
def coelacanth_trophy(): return trophy("coelacanth_trophy", spines=True)


# =====================================================================================
# #9 SALVAGE CRATE (the cargo crate, sea-worn)
# =====================================================================================
@model
def salvage_crate():
    m = BM("salvage_crate", {"side": "pixelpirates:block/salvage_crate", "top": "pixelpirates:block/salvage_crate_top",
                             "particle": "pixelpirates:block/salvage_crate"})
    m.box([1, 0, 1], [15, 14, 15], faces={"north": "#side", "south": "#side", "east": "#side", "west": "#side", "up": "#top", "down": "#top"})
    m.write()
    from blockmodels import write_blockstate as wb
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    v = {}
    for f, r in rot.items():
        for wl in ("true", "false"):
            v[f"facing={f},waterlogged={wl}"] = {"model": "pixelpirates:block/salvage_crate", "y": r} if r else {"model": "pixelpirates:block/salvage_crate"}
    wb("salvage_crate", {"variants": v})
    item_model("salvage_crate", "pixelpirates:block/salvage_crate")
    return m


# =====================================================================================
# #15 TRADING POST: a merchant's counter - planked front, a brass balance, stacked coins and a ledger
# =====================================================================================
@model
def trading_post():
    m = BM("trading_post", {"wood": "minecraft:block/spruce_planks", "top": "minecraft:block/stripped_spruce_log", "log": "minecraft:block/spruce_log",
                            "gold": "minecraft:block/gold_block", "brass": "minecraft:block/raw_gold_block", "book": "minecraft:block/red_wool",
                            "page": "minecraft:block/white_wool", "cloth": "minecraft:block/green_wool", "particle": "minecraft:block/spruce_planks"})
    m.box([0, 12, 3], [16, 14, 16], all="#top")                                  # the counter top
    m.box([1, 0, 4], [15, 12, 15], all="#wood")                                  # the body
    m.box([0, 0, 3], [2, 12, 5], all="#log")                                     # corner posts
    m.box([14, 0, 3], [16, 12, 5], all="#log")
    m.box([2, 4, 3.5], [14, 11, 4], all="#cloth")                                # a green baize panel on the front
    m.box([5, 14, 9], [7, 14.5, 11], all="#brass")                               # the balance: foot
    m.box([5.5, 14.5, 9.5], [6.5, 19, 10.5], all="#brass")                       #   pillar
    m.box([2, 19, 9.5], [10, 19.5, 10.5], all="#brass")                          #   beam
    m.box([2, 17, 9], [4, 17.5, 11], all="#brass")                               #   pans
    m.box([8, 17.5, 9], [10, 18, 11], all="#brass")
    m.box([2.5, 17.5, 9.5], [3.5, 18, 10.5], all="#gold")                        #   a coin on the heavy pan
    for i, (x, z, h) in enumerate(((11, 5, 2), (12.5, 7, 3), (10.5, 8, 1))):     # coin stacks
        m.box([x, 14, z], [x + 1.5, 14 + h * 0.5, z + 1.5], all="#gold")
    m.box([9, 14, 12], [14, 15, 15], all="#book")                                # the ledger, open
    m.box([9.25, 15, 12.25], [13.75, 15.2, 14.75], all="#page")
    m.write()
    blockstate_facing("trading_post")
    item_model("trading_post", "pixelpirates:block/trading_post")
    return m


# =====================================================================================
# #16 BOUNTY BOARD: two posts, a plank board under a little roof, pinned wanted posters and notices
# =====================================================================================
@model
def bounty_board():
    m = BM("bounty_board", {"post": "minecraft:block/spruce_log", "board": "minecraft:block/dark_oak_planks", "roof": "minecraft:block/spruce_planks",
                            "wanted": "pixelpirates:block/wanted_poster", "notice": "pixelpirates:block/bounty_notice",
                            "iron": "minecraft:block/iron_block", "particle": "minecraft:block/dark_oak_planks"})
    m.box([0, 0, 7], [2, 16, 9], all="#post")
    m.box([14, 0, 7], [16, 16, 9], all="#post")
    m.box([2, 3, 7.5], [14, 15, 8.5], all="#board")
    m.box([-0.5, 15.5, 6], [16.5, 16, 10], all="#roof")                         # little roof
    m.box([2.5, 7, 7], [7.5, 14, 7.5], faces={"north": "#wanted", "south": "#wanted"}, uv={"north": [0, 0, 16, 16], "south": [0, 0, 16, 16]})
    m.box([8.5, 8.5, 7.1], [13.5, 14, 7.5], faces={"north": "#notice"}, uv={"north": [0, 0, 16, 16]})
    m.box([8, 3.5, 7.2], [12, 7.5, 7.5], faces={"north": "#wanted"}, uv={"north": [0, 0, 16, 16]})
    m.box([3, 3.5, 7.2], [7, 6.5, 7.5], faces={"north": "#notice"}, uv={"north": [0, 0, 16, 16]})
    for x, y in ((4.8, 13.5), (10.8, 13.5), (9.8, 7), (4.8, 6)):
        m.box([x, y, 6.8], [x + 0.5, y + 0.5, 7.2], all="#iron")                  # tacks
    m.write()
    blockstate_facing("bounty_board")
    item_model("bounty_board", "pixelpirates:block/bounty_board")
    return m


# =====================================================================================
# #4 MOORING POST: a squat tarred bollard with an iron cap and a coil of hawser round its waist
# =====================================================================================
@model
def mooring_post():
    m = BM("mooring_post", {"log": "minecraft:block/dark_oak_log", "top": "minecraft:block/dark_oak_log_top", "iron": "minecraft:block/iron_block",
                            "rope": "pixelpirates:block/rope", "particle": "minecraft:block/dark_oak_log"})
    m.box([4, 0, 4], [12, 12, 12], faces={"north": "#log", "south": "#log", "east": "#log", "west": "#log", "up": "#top", "down": "#top"})
    m.box([3.5, 12, 3.5], [12.5, 14, 12.5], all="#iron")                         # iron cap
    m.box([3, 10, 6], [13, 11, 10], all="#iron")                                  # the horn bar
    m.box([3.5, 4, 3.5], [12.5, 7, 12.5], all="#rope")                            # hawser coil
    m.write()
    from blockmodels import write_blockstate as wb
    wb("mooring_post", {"variants": {"": {"model": "pixelpirates:block/mooring_post"}}})
    item_model("mooring_post", "pixelpirates:block/mooring_post")
    return m


# =====================================================================================
# #25 FIGUREHEADS: a carved bust leaning out from a bracket; the bow is -Z (north, the side facing the placer)
# =====================================================================================
def _figurehead(name, tex, build):
    m = BM(name, {**tex, "wood": "minecraft:block/stripped_spruce_log", "gold": "minecraft:block/gold_block", "particle": "minecraft:block/stripped_spruce_log"})
    m.box([5, 0, 10], [11, 16, 12], all="#wood")                                   # the stem post against the hull
    m.box([4, 0, 8], [12, 3, 12], all="#wood")                                     # bracket foot
    m.box([4.5, 13, 9.5], [11.5, 14, 12.5], all="#gold")                           # gilt band
    build(m)
    m.write()
    blockstate_facing(name)
    item_model(name, f"pixelpirates:block/{name}")
    return m


@model
def mermaid_figurehead():
    def b(m):
        m.box([5.5, 3, 5], [10.5, 9, 10], all="#tail")                           # the tail sweeping back
        m.box([6, 9, 4], [10, 14, 8], all="#skin")                              # torso, leaning out
        m.box([6.5, 14, 3], [9.5, 17, 6], all="#skin")                          # head
        m.box([6, 12, 5.5], [10, 17.5, 8.5], all="#hair")                       # long hair behind
        m.box([5, 1, 7], [11, 3, 10], all="#tail")                              # fluke
    return _figurehead("mermaid_figurehead", {"skin": "minecraft:block/birch_planks", "hair": "minecraft:block/orange_terracotta",
                                               "tail": "minecraft:block/prismarine"}, b)


@model
def kraken_figurehead():
    def b(m):
        m.box([5, 8, 3], [11, 15, 9], all="#flesh")                              # the mantle
        m.box([5.5, 15, 4], [10.5, 17, 8], all="#flesh")
        m.box([5, 11, 2.5], [6.5, 12.5, 3], all="#eye")                         # eyes
        m.box([9.5, 11, 2.5], [11, 12.5, 3], all="#eye")
        for x in (5, 7.25, 9.5):                                                  # arms curling down the stem
            m.box([x, 2, 3.5], [x + 1.5, 8, 5], all="#flesh")
            m.box([x, 1, 5], [x + 1.5, 2.5, 7], all="#flesh")
    return _figurehead("kraken_figurehead", {"flesh": "minecraft:block/purple_terracotta", "eye": "minecraft:block/glowstone"}, b)


@model
def dread_skull_figurehead():
    def b(m):
        m.box([4.5, 7, 3], [11.5, 14, 9], all="#bone")                           # the skull
        m.box([5.5, 4, 3.5], [10.5, 7, 8], all="#bone")                          # jaw
        m.box([5.5, 10, 2.5], [7.5, 12, 3], all="#dark")                         # sockets
        m.box([8.5, 10, 2.5], [10.5, 12, 3], all="#dark")
        m.box([7.5, 8, 2.5], [8.5, 9, 3], all="#dark")                           # nose
        m.box([2, 5, 5], [14, 6, 6], all="#bone", rot=("z", 22.5, [8, 5.5, 5.5]))       # crossbones
        m.box([2, 5, 6], [14, 6, 7], all="#bone", rot=("z", -22.5, [8, 5.5, 6.5]))
    return _figurehead("dread_skull_figurehead", {"bone": "minecraft:block/bone_block_side", "dark": "minecraft:block/black_concrete"}, b)


@model
def navy_eagle_figurehead():
    def b(m):
        m.box([6, 7, 4], [10, 13, 9], all="#body")                               # breast
        m.box([6.5, 13, 3], [9.5, 16, 7], all="#head")                           # white head
        m.box([7.25, 13.5, 1.5], [8.75, 15, 3], all="#beak")                     # beak
        m.box([1, 9, 6], [6, 14, 7], all="#body", rot=("z", 22.5, [6, 11, 6.5]))        # spread wings
        m.box([10, 9, 6], [15, 14, 7], all="#body", rot=("z", -22.5, [10, 11, 6.5]))
        m.box([6.5, 4, 5], [9.5, 7, 8], all="#beak")                             # talons on the bracket
    return _figurehead("navy_eagle_figurehead", {"body": "minecraft:block/brown_terracotta", "head": "minecraft:block/white_concrete",
                                                  "beak": "minecraft:block/gold_block"}, b)


if __name__ == "__main__":
    PREV.mkdir(parents=True, exist_ok=True)
    names = sys.argv[1:] or list(BUILT)
    for n in names:
        m = BUILT[n]()
        m.preview(PREV / f"{n}.png")
        print("wrote", n)
