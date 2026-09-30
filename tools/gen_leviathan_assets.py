"""Pixel Pirates - assets for THE LEVIATHAN HUNT (boss 10/10). Re-runnable.

    python tools/gen_leviathan_assets.py        (pip install pillow numpy)

Writes: block textures (rift seal, tide bell), item icons (powder barge, bane shaft, leviathan scale, crown of the
drowned), shaped block models (anchor winch, tide bell, bane ballista loaded/unloaded, watchers' horn - mostly vanilla
textures), every blockstate variant for LeviathanBlock (facing x ringing x loaded), item models, the lairs' and ports'
loot tables, and the lang entries.
"""
import json
import random
from pathlib import Path

from PIL import Image

from gen_overhaul_textures import BLOCK, ITEM, N, mix, ramp, rgb, save, shade, sprite, sprinkle, stone

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/pixelpirates"
DATA = ROOT / "src/main/resources/data/pixelpirates"


def dump(path: Path, obj):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(json.dumps(obj, indent=2).encode("utf-8"))


# =====================================================================================
# textures
# =====================================================================================
def textures():
    # the Rift Seal: ancient deepslate, and on top a sigil - a coiled serpent in chains, glowing teal
    side = stone(401, ["#1a1c24", "#22242e", "#2a2c38", "#323442"], "#0c0e14", 4, cell=4)
    px = side.load()
    for i in range(N):
        px[i, 0] = px[i, N - 1] = (*rgb("#4a4e5e"), 255)
    for x in range(2, 14, 3): px[x, 7] = (*rgb("#3ae0d0"), 255)
    save(side, BLOCK, "rift_seal_side")
    top = stone(402, ["#1a1c24", "#22242e", "#2a2c38"], None, 0, cell=4)
    px = top.load()
    import math
    for t in range(0, 360, 6):                                                    # the coiled serpent (a spiral)
        a = math.radians(t)
        r = 1.5 + t / 360 * 5.5
        x, y = int(round(7.5 + math.cos(a * 1.6) * r)), int(round(7.5 + math.sin(a * 1.6) * r))
        if 0 <= x < N and 0 <= y < N: px[x, y] = (*rgb("#3ae0d0"), 255)
    for i in range(N):                                                            # the chains across it
        px[i, 3] = px[i, 12] = (*rgb("#7a808a" if i % 2 else "#5a5e66"), 255)
    px[7, 7] = px[8, 8] = (*rgb("#ff3a2a"), 255)                                  # its eye
    save(top, BLOCK, "rift_seal_top")
    # the Tide Bell: verdigris bronze, silent and ringing (glowing runes)
    for name, runes in (("tide_bell", "#2a6a5a"), ("tide_bell_ringing", "#8affe8")):
        im = stone(403, ["#8a6a2a", "#a07a30", "#b88e3a", "#c89e46"], "#5a4418", 3, cell=4)
        sprinkle(im, 404, "#3aa08a", 10)
        px = im.load()
        for x in range(1, N - 1, 2): px[x, 5] = (*rgb(runes), 255)
        for x in range(2, N - 2, 3): px[x, 10] = (*rgb(runes), 255)
        save(im, BLOCK, name)

    # item icons
    save(sprite([
        "................",
        "................",
        "......rf........",
        "......rF........",
        "....bbrbb.......",
        "...bBBrBBb......",
        "...bBtBBBb.bb...",
        "..bbbbbbbbbBBb..",
        ".bBBBbbBBBbBBb..",
        ".bBBBb.bBBbbbb..",
        "wwwwwwwwwwwwwww.",
        "WwWwWwWwWwWwWwW.",
        ".~~~~~~~~~~~~~..",
        "................",
        "................",
        "................"], {"b": ramp("#6a4a2a"), "B": ramp("#8a6a3a"), "t": ramp("#c0302a"), "r": ramp("#5a4030"),
                              "f": ramp("#c02a2a"), "F": ramp("#a02020"), "w": ramp("#7a5a3a"), "W": ramp("#5a4028"), "~": ramp("#3a7ab8")},
        flat="tfF~"), ITEM, "powder_barge")
    save(sprite([
        "..............ii",
        ".............iIi",
        "............iIi.",
        "...........wIi..",
        "..........ww....",
        ".........ww.....",
        "........ww......",
        ".......ww.......",
        "......rw........",
        ".....rr.........",
        "....ww..........",
        "...ww...........",
        "..ww............",
        ".ww.............",
        "ww..............",
        "w..............."], {"w": ramp("#6a5a44"), "r": ramp("#8a6a4a"), "i": ramp("#9aa0a6"), "I": ramp("#d0d4d8")}, flat="I"), ITEM, "bane_shaft")
    save(sprite([
        "................",
        "................",
        ".....kkkkk......",
        "....kKKKKKk.....",
        "...kKsKKKKKk....",
        "...kKKKrKKKk....",
        "..kKKKrRrKKKk...",
        "..kKKKKrKKKKk...",
        "..kKKKKKKKKsk...",
        "...kKKKKKKKk....",
        "...kKKKKKKKk....",
        "....kKKKKKk.....",
        ".....kKKKk......",
        "......kKk.......",
        ".......k........",
        "................"], {"k": ramp("#0e0e14"), "K": ramp("#1c1c26"), "s": ramp("#4a4a5a"), "r": ramp("#ff2a2a"), "R": ramp("#ffb0a0")}, flat="rRs"), ITEM, "leviathan_scale")
    save(sprite([
        "................",
        "................",
        "..k...k..k...k..",
        "..kk.kkkkkk.kk..",
        "..kKkKKrKKKkKk..",
        "..kKKKKKKKKKKk..",
        "..kKrKKKKKKrKk..",
        "..kKKKKKKKKKKk..",
        "..kggggggggggk..",
        "..kgGgGgGgGgGk..",
        "...kkkkkkkkkk...",
        "................",
        "................",
        "................",
        "................",
        "................"], {"k": ramp("#0e0e14"), "K": ramp("#23232e"), "r": ramp("#ff2020"), "g": ramp("#8a6a2a"), "G": ramp("#e0b84a")}, flat="rG"),
        ITEM, "crown_of_the_drowned")


# =====================================================================================
# models + blockstates
# =====================================================================================
def box(a, b, tex, faces=("north", "south", "east", "west", "up", "down")):
    return {"from": a, "to": b, "faces": {f: {"texture": tex} for f in faces}}


def models():
    M = ASSETS / "models/block"
    dump(M / "rift_seal.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": "pixelpirates:block/rift_seal_top", "side": "pixelpirates:block/rift_seal_side", "bottom": "minecraft:block/deepslate"}})
    # the anchor winch: a drum between two posts on a deepslate base, a crank on the side
    dump(M / "anchor_winch.json", {"textures": {"base": "minecraft:block/polished_deepslate", "post": "minecraft:block/dark_oak_log",
                                                 "drum": "minecraft:block/stripped_spruce_log", "iron": "minecraft:block/iron_block",
                                                 "particle": "minecraft:block/polished_deepslate"},
                                    "elements": [box([0, 0, 0], [16, 4, 16], "#base"), box([1, 4, 5], [4, 14, 11], "#post"), box([12, 4, 5], [15, 14, 11], "#post"),
                                                 box([4, 7, 5], [12, 13, 11], "#drum"), box([4, 9, 4], [12, 11, 12], "#iron"),
                                                 box([15, 9, 7], [17, 11, 9], "#iron"), box([16, 9, 7], [17, 16, 8], "#post")]})
    # the tide bell under a little yoke
    for name, tex in (("tide_bell", "pixelpirates:block/tide_bell"), ("tide_bell_ringing", "pixelpirates:block/tide_bell_ringing")):
        dump(M / f"{name}.json", {"textures": {"bell": tex, "wood": "minecraft:block/dark_oak_log", "particle": tex},
                                   "elements": [box([1, 0, 7], [3, 16, 9], "#wood"), box([13, 0, 7], [15, 16, 9], "#wood"), box([1, 14, 7], [15, 16, 9], "#wood"),
                                                box([5, 5, 5], [11, 13, 11], "#bell"), box([4, 3, 4], [12, 5, 12], "#bell")]})
    # THE BANE: a harpoon ballista - base, stock, bow arms spanning 2 blocks, string; loaded = the bolt in the track
    base = [box([0, 0, 0], [16, 3, 16], "#plank"), box([6, 3, 0], [10, 8, 16], "#stock"), box([-8, 8, 3], [24, 10, 6], "#arm"),
            box([-7, 8, 6], [23, 9, 7], "#string"), box([5, 3, 12], [11, 11, 15], "#stock"), box([7, 10, 13], [9, 12, 16], "#iron")]
    tex = {"plank": "minecraft:block/dark_oak_planks", "stock": "minecraft:block/spruce_log", "arm": "minecraft:block/dark_oak_log",
           "string": "minecraft:block/white_wool", "iron": "minecraft:block/iron_block", "bolt": "minecraft:block/stripped_dark_oak_log",
           "tip": "minecraft:block/iron_block", "particle": "minecraft:block/dark_oak_planks"}
    dump(M / "bane_ballista.json", {"textures": tex, "elements": base})
    dump(M / "bane_ballista_loaded.json", {"textures": tex, "elements": base + [box([7, 8, -8], [9, 10, 18], "#bolt"), box([6, 7, -12], [10, 11, -8], "#tip")]})
    # the Watchers' Horn: a great curled horn of bone on a stand
    dump(M / "watchers_horn.json", {"textures": {"stand": "minecraft:block/dark_oak_log", "bone": "minecraft:block/bone_block_side",
                                                  "rim": "minecraft:block/calcite", "band": "minecraft:block/gold_block", "particle": "minecraft:block/bone_block_side"},
                                     "elements": [box([6, 0, 6], [10, 7, 10], "#stand"), box([6, 7, 8], [10, 10, 14], "#bone"), box([5, 8, 2], [11, 13, 8], "#bone"),
                                                  box([5, 12, 3], [11, 13, 7], "#band"), box([4, 9, -4], [12, 17, 2], "#bone"), box([3, 8, -6], [13, 18, -4], "#rim")]})
    # blockstates: every facing x ringing x loaded combination
    for block, pick in (("rift_seal", lambda r, l: "rift_seal"), ("anchor_winch", lambda r, l: "anchor_winch"),
                        ("tide_bell", lambda r, l: "tide_bell_ringing" if r else "tide_bell"),
                        ("bane_ballista", lambda r, l: "bane_ballista_loaded" if l else "bane_ballista"),
                        ("watchers_horn", lambda r, l: "watchers_horn")):
        v = {}
        for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for r in (False, True):
                for l in (False, True):
                    e = {"model": f"pixelpirates:block/{pick(r, l)}"}
                    if y and block != "rift_seal": e["y"] = y
                    v[f"facing={f},loaded={str(l).lower()},ringing={str(r).lower()}"] = e
        dump(ASSETS / f"blockstates/{block}.json", {"variants": v})
        dump(ASSETS / f"models/item/{block}.json", {"parent": f"pixelpirates:block/{block}"})
    for item in ("powder_barge", "bane_shaft", "leviathan_scale", "crown_of_the_drowned"):
        dump(ASSETS / f"models/item/{item}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"pixelpirates:item/{item}"}})


# =====================================================================================
# loot
# =====================================================================================
def item(name, w, lo=1, hi=1):
    e = {"type": "minecraft:item", "name": name if ":" in name else "minecraft:" + name, "weight": w}
    if hi > 1: e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e


def pool(lo, hi, *entries):
    return {"rolls": {"type": "minecraft:uniform", "min": lo, "max": hi}, "bonus_rolls": 0, "entries": list(entries)}


P = "pixelpirates:"
LOOT = {
    "leviathan_rift_altar": [pool(3, 5, item("diamond", 4, 1, 3), item("prismarine_crystals", 6, 4, 10), item(P + "depth_charge", 5, 2, 4),
                                     item("gunpowder", 6, 4, 10), item(P + "pirate_coin", 8, 16, 32), item("heart_of_the_sea", 1))],
    "leviathan_rift_hoard": [pool(1, 1, item("netherite_scrap", 3, 1, 3), item("nether_star", 1)),
                             pool(4, 6, item(P + "pirate_coin", 10, 32, 64), item("gold_block", 5, 1, 3), item("diamond", 4, 2, 5), item(P + "kraken_scale", 5, 3, 6),
                                  item("gunpowder", 5, 8, 16), item("enchanted_golden_apple", 1))],
    "leviathan_gullet_wreck": [pool(3, 5, item(P + "pirate_coin", 10, 8, 24), item("gunpowder", 8, 2, 8), item(P + "chum", 5, 1, 3), item(P + "rope", 4, 2, 6),
                                       item(P + "powder_barge", 2), item(P + "cannon_ball", 4, 2, 6), item("gold_ingot", 3, 1, 4), item("bone", 5, 2, 6))],
    "leviathan_gullet_supplies": [pool(1, 2, item(P + "powder_barge", 1)),
                                  pool(3, 5, item("gunpowder", 8, 4, 12), item(P + "chum", 7, 2, 4), item(P + "depth_charge", 4, 2, 4), item(P + "dynamite", 5, 2, 4),
                                       item(P + "hardtack", 4, 2, 6), item(P + "pirate_coin", 5, 8, 16))],
    "leviathan_spire_ruin": [pool(3, 5, item("gunpowder", 10, 6, 16), item(P + "pirate_coin", 8, 16, 32), item("diamond", 3, 1, 3), item(P + "powder_barge", 2),
                                     item("prismarine_shard", 5, 4, 10), item(P + "depth_charge", 4, 1, 3))],
    "saltmarrow_tavern": [pool(3, 6, item(P + "grog", 8, 1, 3), item("bread", 6, 2, 5), item("cooked_cod", 6, 2, 6), item(P + "hardtack", 5, 2, 6),
                                  item(P + "pirate_coin", 6, 4, 12))],
    "saltmarrow_refinery": [pool(3, 5, item("coal", 6, 4, 10), item("glow_ink_sac", 4, 1, 3), item("candle", 5, 2, 6), item("gunpowder", 3, 1, 4), item("bone", 6, 3, 8))],
    "saltmarrow_fishery": [pool(3, 5, item("cod", 8, 3, 8), item("salmon", 6, 2, 6), item("string", 6, 3, 9), item("fishing_rod", 2), item(P + "rope", 4, 2, 5))],
    "saltmarrow_ruin": [pool(1, 1, item(P + "leviathan_scale", 1)),
                        pool(3, 5, item("gunpowder", 8, 4, 10), item(P + "pirate_coin", 6, 6, 16), item("bone", 6, 2, 6), item(P + "charred_planks", 5, 2, 6),
                             item(P + "grog", 3))],
    "brightwater_market": [pool(3, 6, item("emerald", 6, 2, 6), item("bread", 5, 2, 5), item("apple", 5, 2, 5), item(P + "pirate_coin", 8, 8, 20),
                                   item(P + "banana_bread", 4, 1, 3), item("sugar", 3, 2, 6))],
    "brightwater_warehouse": [pool(3, 6, item("gunpowder", 8, 4, 12), item("iron_ingot", 6, 2, 6), item(P + "rope", 6, 3, 8), item(P + "sail", 3, 1, 2),
                                      item(P + "cannon_ball", 5, 4, 10), item("spruce_planks", 5, 8, 16))],
    "brightwater_harbourmaster": [pool(3, 5, item("compass", 3), item("map", 4), item("paper", 5, 3, 8), item(P + "pirate_coin", 8, 12, 24), item("clock", 2))],
    "brightwater_shipyard": [pool(3, 5, item("spruce_planks", 8, 8, 24), item("iron_ingot", 5, 2, 6), item(P + "rope", 6, 3, 8), item(P + "sail", 4, 1, 2),
                                     item(P + "cannon_ball", 4, 2, 6))],
    "brightwater_tavern": [pool(3, 6, item(P + "grog", 8, 1, 3), item("bread", 5, 2, 5), item("cooked_beef", 5, 2, 5), item(P + "pirate_coin", 6, 4, 12),
                                   item(P + "banana_bread", 3, 1, 2))],
    "brightwater_governor": [pool(3, 5, item("gold_ingot", 6, 2, 6), item("diamond", 3, 1, 3), item("emerald", 6, 2, 6), item(P + "pirate_coin", 8, 16, 40),
                                     item("golden_apple", 2))],
    "brightwater_ruin": [pool(1, 1, item(P + "leviathan_scale", 1)),
                         pool(3, 5, item("gunpowder", 8, 6, 14), item(P + "pirate_coin", 6, 8, 20), item("iron_ingot", 4, 2, 6), item(P + "charred_planks", 5, 2, 6),
                              item("bone", 5, 2, 5))],
}


def loot():
    for name, pools in LOOT.items():
        dump(DATA / f"loot_tables/chests/{name}.json", {"type": "minecraft:chest", "pools": pools})


LANG = {
    "block.pixelpirates.rift_seal": "Rift Seal",
    "block.pixelpirates.anchor_winch": "Anchor Winch",
    "block.pixelpirates.tide_bell": "Tide Bell",
    "block.pixelpirates.bane_ballista": "The Bane",
    "block.pixelpirates.watchers_horn": "Watchers' Horn",
    "item.pixelpirates.powder_barge": "Powder Barge",
    "item.pixelpirates.bane_shaft": "Bane Shaft",
    "item.pixelpirates.leviathan_scale": "Leviathan Scale",
    "item.pixelpirates.crown_of_the_drowned": "Crown of the Drowned",
    "tooltip.pixelpirates.crown": "Water breathing, Dolphin's Grace, Conduit Power and night vision in the sea",
    "tooltip.pixelpirates.crown.lore": "It wore the bells of two towns. Now you wear it",
    "entity.pixelpirates.leviathan": "The Leviathan",
    "entity.pixelpirates.leviathan_segment": "The Leviathan",
    "entity.pixelpirates.leviathan_wake": "The Leviathan's Wake",
    "entity.pixelpirates.powder_barge": "Powder Barge",
    "entity.pixelpirates.bane_bolt": "The Bane",
    "entity.pixelpirates.debris": "Wreckage",
}


def lang():
    path = ASSETS / "lang/en_us.json"
    d = json.loads(path.read_text(encoding="utf-8"))
    d.update(LANG)
    path.write_text(json.dumps(d, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    textures()
    models()
    loot()
    lang()
    print("leviathan assets written")
