"""THE WAVEBREAK FORGE - the forge blocks, the forged weapons and the smith's hammer (2026-10-02).

    python tools/gen_forge_assets.py            # everything
    python tools/gen_forge_assets.py items|tex|models|lang

items : 16x16 icons through gen_overhaul_textures' sprite engine (lit top-left, dark outline): crabclaw_sabre,
        pearlguard_rapier, pistol_cutlass, obsidian_halberd, soulreaver, inkfang, smiths_hammer
tex   : smithy_sign_board (64x32 hanging-sign board), pattern_board (32x32 chalk patterns)
models: forge_hearth (cold + lit, by fuel x facing), forge_anvil (anvil on a stump), bellows, pattern_board (wall board),
        smithy_sign (shared hanging-sign model) + blockstates, item models, previews in tools/previews/forge/
"""
import json, sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import BM, RES, write_blockstate, item_model  # noqa: E402
from gen_tavern_assets import noise, text, text_w, hanging_sign, facing_states  # noqa: E402
from gen_overhaul_textures import sprite, ramp, blade_pal, CUTLASS, SABRE, RAPIER, DAGGER  # noqa: E402

TEX = RES / "textures"
PREV = Path(__file__).parent / "previews/forge"
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def save(img, rel):
    p = TEX / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)
    print("wrote", rel)


# ------------------------------------------------------------------------------------------------ item icons
PISTOL_CUTLASS = [
    "................",
    "..............e.",
    ".............eb.",
    "............ebb.",
    "...........ebb..",
    "..........ebb..m",
    ".........ebb..k.",
    "........ebb..k..",
    ".......ebb..k...",
    "......ebb..k....",
    "..g..ebb..k.....",
    "..ggebb.ll......",
    "..gghg..........",
    "...hgg..........",
    "..ph............",
    "................"]
HALBERD = [
    "..............e.",
    ".............eb.",
    "..........vvobbb",
    ".........vooobb.",
    ".........voogb..",
    "..........vog...",
    "..........h.....",
    ".........h......",
    "........h.......",
    ".......h........",
    "......h.........",
    ".....h..........",
    "....h...........",
    "...r............",
    "..hr............",
    ".p.............."]


def hammer_grid():
    """A smith's hammer: the haft '/' from the bottom-left, the head across it '\\' at the top."""
    g = [["."] * 16 for _ in range(16)]
    for y in range(1, 9):
        for x in range(6, 15):
            d = (x - y) - 6
            if abs(d) <= 1 and 2 <= y + x // 3:
                if (x, y) in ((6, 1), (14, 9)):
                    continue
                g[y][x] = "w" if d == -1 else "i"
    for k in range(8):
        x, y = 9 - k, 6 + k
        if 0 <= x < 16 and y < 16 and g[y][x] == ".":
            g[y][x] = "h" if k < 6 else "r"
    g[14][1] = "p"
    return ["".join(r) for r in g]


ITEMS = {
    "crabclaw_sabre":    (SABRE, blade_pal("#ffe0c8", "#d4643c", "#a03a22", "#5a3a28", "#e88a5a")),
    "pearlguard_rapier": (RAPIER, blade_pal("#ffffff", "#c4d2e4", "#ece6f4", "#2a3a6a", "#f8f4ff")),
    "pistol_cutlass":    (PISTOL_CUTLASS, {**blade_pal("#f5f8fc", "#b4bcc8", "#deb43c", "#462d1e", "#f0c850"),
                                           "k": ramp("#a4aab8"), "m": ramp("#d8a040"), "l": ramp("#c9a040")}),
    "obsidian_halberd":  (HALBERD, {**blade_pal("#d8c8e8", "#4a3460", "#a06a30", "#3a2a1e", "#4a3460", "#8a6a3a"),
                                    "o": ramp("#2a1a3a"), "v": ramp("#ff8a30")}),
    "soulreaver":        (CUTLASS, blade_pal("#d8fff0", "#2f8a78", "#4a2a5a", "#2a1e2e", "#9a6ae0")),
    "inkfang":           (DAGGER, blade_pal("#c8b8e8", "#3a2a52", "#1a1a24", "#2a1e3a", "#6a3aa0")),
}


def items():
    for name, (shape, pal) in ITEMS.items():
        save(sprite(shape, pal, flat="vm"), f"item/{name}.png")
    save(sprite(hammer_grid(), {"i": ramp("#7c7f88"), "w": ramp("#b4b8c2"), "h": ramp("#6a4a2c"), "r": ramp("#3a2a1a"), "p": ramp("#4a4e58")}),
         "item/smiths_hammer.png")


# ------------------------------------------------------------------------------------------------ block textures
def tex():
    # THE SMITHY SIGN: a soot-black board, an anvil under a hammer, "SMITHY" in forge-orange, "& FORGE"
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (38, 34, 32), 4, 71)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 31], outline=(14, 12, 10, 255))
    d.rectangle([1, 1, 62, 30], outline=(150, 96, 40, 255))
    d.rectangle([2, 2, 61, 29], outline=(70, 60, 54, 255))
    iron, hi = (120, 124, 132, 255), (176, 180, 188, 255)
    d.rectangle([5, 19, 18, 21], fill=iron); d.rectangle([4, 18, 19, 18], fill=hi)          # the anvil face
    d.polygon([(19, 18), (22, 18), (19, 21)], fill=iron)                                    # its horn
    d.rectangle([8, 22, 15, 23], fill=(90, 92, 100, 255)); d.rectangle([6, 24, 17, 26], fill=(80, 82, 90, 255))
    d.line([(6, 6), (14, 14)], fill=(110, 76, 44, 255), width=2)                            # the hammer
    d.rectangle([4, 5, 9, 9], fill=hi, outline=(60, 62, 70, 255))
    for x, y in ((17, 14), (19, 12), (16, 11), (20, 15)):
        d.point((x, y), fill=(255, 190, 80, 255))                                           # sparks
    orange, cream, sh = (240, 140, 50, 255), (226, 214, 190, 255), (10, 8, 6, 255)
    text(im, "FORGE", 41 - text_w("FORGE", 2) // 2, 5, orange, 2, sh)
    text(im, "SMITHY", 41 - text_w("SMITHY") // 2, 21, cream, 1, None)
    save(im, "block/smithy_sign_board.png")

    # THE PATTERN BOARD: a dark slate in a wooden frame, chalk outlines of the forged blades, "PATTERNS"
    im = Image.new("RGBA", (32, 32))
    noise(im, (0, 0, 32, 32), (40, 44, 46), 4, 72)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 31, 31], outline=(96, 66, 38, 255)); d.rectangle([1, 1, 30, 30], outline=(120, 84, 48, 255))
    chalk, dim = (226, 226, 214, 255), (150, 150, 144, 255)
    text(im, "PATTERNS", 16 - text_w("PATTERNS") // 2, 3, chalk)
    d.line([(3, 9), (28, 9)], fill=dim)
    for i, (x0, y0, ln) in enumerate(((4, 26, 13), (12, 26, 13), (20, 26, 11))):            # three blade outlines
        d.line([(x0, y0), (x0 + ln // 2 + 2, y0 - ln)], fill=chalk)
        d.line([(x0 + 2, y0), (x0 + ln // 2 + 3, y0 - ln + 1)], fill=dim)
        d.line([(x0 - 1, y0 - 2), (x0 + 3, y0 - 2)], fill=chalk)
    d.ellipse([24, 11, 28, 15], outline=chalk)                                              # a pearl
    d.line([(25, 18), (28, 21)], fill=(220, 120, 60, 255)); d.line([(28, 18), (25, 21)], fill=(220, 120, 60, 255))
    save(im, "block/pattern_board.png")


# ------------------------------------------------------------------------------------------------ models
def models():
    built = [hanging_sign("smithy_sign")]

    # --- FORGE HEARTH: brick body, a firebox door with grate bars in front, a coal tray inside a brick rim; lit = glowing
    for lit in (False, True):
        name = "forge_hearth_lit" if lit else "forge_hearth"
        m = BM(name, {"brick": "minecraft:block/bricks", "top": "minecraft:block/polished_blackstone",
                      "coal": "minecraft:block/magma" if lit else "minecraft:block/coal_block",
                      "box": "minecraft:block/magma" if lit else "minecraft:block/blackstone", "bars": "minecraft:block/iron_bars",
                      "particle": "minecraft:block/bricks"})
        m.box([0, 0, 0], [16, 12, 16], all="#brick")
        m.box([2, 11, 2], [14, 12.5, 14], all="#coal", shade=not lit)
        for f, t in (([0, 12, 0], [16, 15, 2]), ([0, 12, 14], [16, 15, 16]), ([0, 12, 2], [2, 15, 14]), ([14, 12, 2], [16, 15, 14])):
            m.box(f, t, all="#brick")
        for f, t in (([0, 15, 0], [16, 16, 2]), ([0, 15, 14], [16, 16, 16]), ([0, 15, 2], [2, 16, 14]), ([14, 15, 2], [16, 16, 14])):
            m.box(f, t, all="#top")
        m.box([4, 2, -0.01], [12, 8, 0.5], faces={"north": "#box"}, shade=not lit)
        m.box([4, 2, -0.2], [12, 8, -0.2], faces={"north": "#bars", "south": "#bars"}, uv={"north": [4, 8, 12, 14], "south": [4, 8, 12, 14]})
        m.write()
        built.append(m)
    v = {}
    for f, r in ROT.items():
        for fuel in range(9):
            mod = "pixelpirates:block/forge_hearth" + ("_lit" if fuel else "")
            v[f"facing={f},fuel={fuel}"] = {"model": mod, "y": r} if r else {"model": mod}
    write_blockstate("forge_hearth", {"variants": v})
    item_model("forge_hearth", "pixelpirates:block/forge_hearth_lit")

    # --- FORGE ANVIL: a heavy anvil on an oak stump banded with iron (front = north; the face runs along x)
    m = BM("forge_anvil", {"log": "minecraft:block/oak_log", "logtop": "minecraft:block/oak_log_top", "anvil": "minecraft:block/anvil",
                           "face": "minecraft:block/anvil_top", "iron": "minecraft:block/iron_block", "particle": "minecraft:block/anvil"})
    m.box([3, 0, 4], [13, 6, 12], faces={"north": "#log", "south": "#log", "east": "#log", "west": "#log", "up": "#logtop", "down": "#logtop"})
    m.box([2.9, 4, 3.9], [13.1, 5, 12.1], all="#iron")
    m.box([4, 6, 5], [12, 8, 11], all="#anvil")
    m.box([5.5, 8, 6], [10.5, 11, 10], all="#anvil")
    m.box([1, 11, 4.5], [14, 15, 11.5], faces={"north": "#anvil", "south": "#anvil", "east": "#anvil", "west": "#anvil", "down": "#anvil", "up": "#face"})
    m.box([14, 12, 6], [16, 14.5, 10], all="#anvil")
    m.box([0, 13, 6.5], [1, 15, 9.5], all="#anvil")
    m.write()
    facing_states("forge_anvil", {"": "pixelpirates:block/forge_anvil"})
    item_model("forge_anvil", "pixelpirates:block/forge_anvil")
    built.append(m)

    # --- BELLOWS: two boards hinged at the iron nozzle (front = north, the nozzle toward the fire), leather between
    m = BM("bellows", {"wood": "minecraft:block/spruce_planks", "leather": "minecraft:block/brown_terracotta",
                       "iron": "minecraft:block/iron_block", "particle": "minecraft:block/spruce_planks"})
    m.box([1, 0, 3], [15, 1, 15], all="#wood")
    m.box([2, 1, 4], [14, 5, 14], all="#leather")
    m.box([1, 5, 3], [15, 6, 15], all="#wood", rot=("x", 22.5, [8, 5, 3]))
    m.box([7, 1, 0], [9, 3, 4], all="#iron")
    m.box([6, 0.5, 15], [10, 1.5, 16], all="#wood")
    m.write()
    facing_states("bellows", {"": "pixelpirates:block/bellows"})
    item_model("bellows", "pixelpirates:block/bellows")
    built.append(m)

    # --- PATTERN BOARD: a framed slate on the wall (front = north)
    m = BM("pattern_board", {"board": "pixelpirates:block/pattern_board", "frame": "minecraft:block/dark_oak_planks", "particle": "minecraft:block/dark_oak_planks"})
    m.box([1, 1, 15], [15, 15, 16], faces={"north": "#board", "south": "#frame", "east": "#frame", "west": "#frame", "up": "#frame", "down": "#frame"},
          uv={"north": [0, 0, 16, 16]})
    for f, t in (([0.5, 15, 14.5], [15.5, 16, 16]), ([0.5, 0, 14.5], [15.5, 1, 16]), ([0, 0, 14.5], [1, 16, 16]), ([15, 0, 14.5], [16, 16, 16])):
        m.box(f, t, all="#frame")
    m.write()
    facing_states("pattern_board", {"": "pixelpirates:block/pattern_board"})
    item_model("pattern_board", "pixelpirates:block/pattern_board")
    built.append(m)

    PREV.mkdir(parents=True, exist_ok=True)
    for b in built:
        b.preview(PREV / f"{b.name}.png")
    print("models:", len(built))


LANG_E = {"block.pixelpirates.forge_hearth": "Forge Hearth", "block.pixelpirates.forge_anvil": "Forge Anvil",
          "block.pixelpirates.bellows": "Bellows", "block.pixelpirates.pattern_board": "Smith's Pattern Board",
          "block.pixelpirates.smithy_sign": "Smithy Sign", "item.pixelpirates.smiths_hammer": "Smith's Hammer",
          "item.pixelpirates.crabclaw_sabre": "Crabclaw Sabre", "item.pixelpirates.pearlguard_rapier": "Pearlguard Rapier",
          "item.pixelpirates.pistol_cutlass": "Pistol Cutlass", "item.pixelpirates.obsidian_halberd": "Obsidian Halberd",
          "item.pixelpirates.soulreaver": "Soulreaver", "item.pixelpirates.inkfang": "Inkfang"}


def lang():
    p = RES / "lang/en_us.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    d.update(LANG_E)
    p.write_bytes(json.dumps(d, indent=2, ensure_ascii=False).encode("utf-8"))
    print("lang +", len(LANG_E))


if __name__ == "__main__":
    what = sys.argv[1:] or ["items", "tex", "models", "lang"]
    if "items" in what:
        items()
    if "tex" in what:
        tex()
    if "models" in what:
        models()
    if "lang" in what:
        lang()
