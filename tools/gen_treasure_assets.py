"""THE TREASURE BLOCK (2026-10-03): a 3D treasure heap instead of the old textured cube.

    python tools/gen_treasure_assets.py

A stepped mound of gold coins, an open dark-oak chest (gold bands) half sunk at the back spilling coins, a gold goblet,
gems (emerald, diamond, amethyst, ruby) and a few loose coins. Writes the coin-heap texture
(textures/block/treasure_coins.png), the block model, blockstate and item model (all hand-authored in resources/;
nothing for treasure_block comes from datagen except the loot table) + tools/previews/treasure_coins.png.
"""
import json, math, random, sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import BM, RES  # noqa: E402

TEX = RES / "textures/block"


def coins():
    """16x16 heap of overlapping coins: gold base, round coins with a bright rim + a dark edge, a few glints."""
    r = random.Random(7)
    im = Image.new("RGBA", (16, 16), (196, 140, 34, 255))
    px = im.load()
    for y in range(16):
        for x in range(16):
            k = r.randint(-10, 10)
            px[x, y] = (196 + k, 140 + k, 34 + k // 2, 255)
    for _ in range(14):                                                            # coins, drawn over each other
        cx, cy, rad = r.uniform(0, 16), r.uniform(0, 16), r.choice((2.2, 2.6, 3.0))
        for y in range(16):
            for x in range(16):
                for ox in (-16, 0, 16):                                            # wrap so the texture tiles
                    for oy in (-16, 0, 16):
                        d = math.hypot(x + 0.5 - cx - ox, y + 0.5 - cy - oy)
                        if d < rad - 0.9: px[x, y] = (238, 190, 60, 255) if (x + y) % 3 else (226, 176, 48, 255)
                        elif d < rad: px[x, y] = (150, 98, 20, 255) if (x - cx - ox) + (y - cy - oy) > 0 else (255, 226, 120, 255)
    for _ in range(4):
        x, y = r.randrange(16), r.randrange(16)
        px[x, y] = (255, 250, 210, 255)
    im.save(TEX / "treasure_coins.png")
    return im


def model():
    m = BM("treasure_block", {"coins": "pixelpirates:block/treasure_coins", "wood": "minecraft:block/dark_oak_planks",
                              "band": "minecraft:block/gold_block", "emerald": "minecraft:block/emerald_block",
                              "diamond": "minecraft:block/diamond_block", "amethyst": "minecraft:block/amethyst_block",
                              "ruby": "minecraft:block/redstone_block", "particle": "pixelpirates:block/treasure_coins"},
           parent="minecraft:block/block")
    # the mound, in steps
    m.box([1, 0, 1], [15, 3, 15], all="#coins")
    m.box([2.5, 3, 2.5], [13.5, 5.5, 13.5], all="#coins")
    m.box([4.5, 5.5, 4], [11.5, 7.5, 11], all="#coins")
    m.box([6, 7.5, 5.5], [10, 9, 9], all="#coins")
    # the chest, half sunk at the back right, lid thrown open, coins heaped in it
    m.box([8, 4, 9.5], [15, 9, 14.5], all="#wood")
    m.box([7.9, 4.5, 9.4], [15.1, 5.2, 14.6], all="#band")
    m.box([7.9, 7.8, 9.4], [15.1, 8.5, 14.6], all="#band")
    m.box([11, 6.5, 9.2], [12, 7.8, 9.5], all="#band")                               # the lock plate
    m.box([8.5, 9, 10], [14.5, 10, 14], all="#coins")                                # the spill
    m.box([8, 9, 14], [15, 14, 15.2], all="#wood", rot=("x", -22.5, [11.5, 9, 14.5]))           # the lid
    m.box([7.9, 9.5, 13.9], [15.1, 10.2, 15.3], all="#band", rot=("x", -22.5, [11.5, 9, 14.5]))
    # a gold goblet on the front left
    m.box([2.5, 3, 3], [5.5, 3.5, 6], all="#band")
    m.box([3.5, 3.5, 4], [4.5, 6, 5], all="#band")
    m.box([2.75, 6, 3.25], [5.25, 8.5, 5.75], all="#band")
    # gems
    m.box([10.5, 3, 3], [12.5, 5, 5], all="#emerald", rot=("y", 22.5, [11.5, 4, 4]))
    m.box([5, 5.5, 8.5], [6.5, 7, 10], all="#diamond", rot=("y", -22.5, [5.75, 6, 9.25]))
    m.box([8.5, 7.5, 4.5], [10, 9, 6], all="#amethyst")
    m.box([1.5, 3, 11], [3, 4.5, 12.5], all="#ruby", rot=("y", 45, [2.25, 3.75, 11.75]))
    # loose coins on the floor
    for x, z in ((13.5, 1.2), (0.6, 7), (6, 15.2)):
        m.box([x, 0, z], [x + 1.6, 0.4, z + 1.6], all="#band")
    m.write()
    (RES / "blockstates/treasure_block.json").write_bytes(json.dumps({"variants": {"": {"model": "pixelpirates:block/treasure_block"}}}, indent=1).encode())
    (RES / "models/item/treasure_block.json").write_bytes(json.dumps({"parent": "pixelpirates:block/treasure_block"}, indent=1).encode())


if __name__ == "__main__":
    im = coins()
    model()
    prev = Path(__file__).parent / "previews/treasure_coins.png"
    im.resize((256, 256), Image.NEAREST).save(prev)
    print("wrote treasure_coins.png, treasure_block model/blockstate/item")
