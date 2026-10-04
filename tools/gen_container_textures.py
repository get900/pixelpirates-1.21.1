"""PIRATE CONTAINER SCREENS (2026-10-03): the vanilla inventory / chest / crafting table GUIs repainted to match our own
screens (gen_gui_textures.py) - parchment panels, warm tan slots, dark-wood outlines.

    python tools/gen_container_textures.py

Reads each vanilla texture from the Minecraft client jar in the gradle cache (vanilla art never goes in the repo) and
swaps vanilla's exact GUI greys for our palette pixel by pixel, so every slot, bevel and arrow stays where the game
expects it; the panel gets a light parchment grain. Anything that isn't one of those greys (icons, the effect-bar
colours) is left alone. Writes assets/minecraft/textures/gui/container/<name>.png (overrides vanilla; a resource pack
still wins) + tools/previews/gui_containers.png. Vanilla's dark-grey labels stay readable on the parchment.
"""
import glob, io, random, zipfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "src/main/resources/assets/minecraft/textures/gui/container"
PREV = ROOT / "tools/previews/gui_containers.png"

# every container screen (2026-10-03: batch 1 inventory/chest/crafting, then the rest). Not repainted: gamemode_switcher
# (the F3+F4 overlay), stats_icons (statistics screen). generic_54 = chest, large chest, ender chest, barrel. villager2 =
# vanilla villagers (our port traders have their own skin, merchant.png).
NAMES = ["inventory", "generic_54", "crafting_table", "furnace", "smoker", "blast_furnace", "hopper", "dispenser", "anvil",
         "enchanting_table", "shulker_box", "brewing_stand", "beacon", "grindstone", "stonecutter", "loom", "cartography_table",
         "smithing", "legacy_smithing", "horse", "bundle", "villager2",
         "creative_inventory/tab_items", "creative_inventory/tab_inventory", "creative_inventory/tab_item_search",
         "creative_inventory/tabs"]

PANEL = (216, 196, 156)
MAP = {
    (0xC6, 0xC6, 0xC6): None,               # the panel -> parchment with grain (below)
    (0xFF, 0xFF, 0xFF): (246, 234, 206),     # bevel highlight
    (0x55, 0x55, 0x55): (122, 88, 52),       # bevel shadow -> wood
    (0x00, 0x00, 0x00): (36, 23, 12),        # outline / the player-preview box -> dark wood
    (0x8B, 0x8B, 0x8B): (152, 121, 84),      # slot
    (0x37, 0x37, 0x37): (72, 49, 27),        # slot shadow
    (0x21, 0x21, 0x21): (42, 29, 17),
}


def vanilla(name):
    jar = glob.glob(str(Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"))[0]
    with zipfile.ZipFile(jar) as z:
        return Image.open(io.BytesIO(z.read(f"assets/minecraft/textures/gui/container/{name}.png"))).convert("RGBA")


def grain(x, y, seed):
    """Soft parchment grain: low-frequency blotches + a little per-pixel speckle."""
    r = random.Random((x // 6) * 7919 + (y // 6) * 104729 + seed)
    return r.randint(-5, 5) + random.Random(x * 31 + y * 17 + seed).randint(-3, 3)


def repaint(im, seed):
    out = im.copy()
    px = out.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0 or (r, g, b) not in MAP:
                continue
            c = MAP[(r, g, b)]
            if c is None:
                k = grain(x, y, seed)
                c = tuple(max(0, min(255, v + k)) for v in PANEL)
            px[x, y] = c + (a,)
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    done = []
    for i, n in enumerate(NAMES):
        im = repaint(vanilla(n), 500 + i)
        (OUT / f"{n}.png").parent.mkdir(parents=True, exist_ok=True)
        im.save(OUT / f"{n}.png")
        done.append(im)
        print("wrote", n)
    cols = 6
    sheet = Image.new("RGBA", (cols * 260, ((len(done) + cols - 1) // cols) * 260), (30, 30, 34, 255))
    for i, im in enumerate(done):
        sheet.alpha_composite(im.crop((0, 0, 256, 256)), ((i % cols) * 260 + 2, (i // cols) * 260 + 2))
    PREV.parent.mkdir(parents=True, exist_ok=True)
    sheet.save(PREV)


if __name__ == "__main__":
    main()
