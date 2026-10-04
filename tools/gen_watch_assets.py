"""THE WAVEBREAK WATCH - the guardhouse's sign, wanted posters and weapon racks (2026-10-02).

    python tools/gen_watch_assets.py            # everything
    python tools/gen_watch_assets.py tex|models|lang

tex   : textures/block/watch_sign_board (64x32, the hanging sign's board), wanted_poster (32x32 cork board, three posters)
models: watch_sign (the shared hanging-sign model from gen_tavern_assets), wanted_poster (wall plate), weapon_rack (wall rack:
        two rails on posts holding a cutlass, a boarding pike and an officer's sabre - the mod's own item sprites stood
        upright) + blockstates by facing, item models, previews in tools/previews/watch/
"""
import json, random, sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import BM, RES, item_model  # noqa: E402
from gen_tavern_assets import FONT5, noise, text, text_w, hanging_sign, facing_states  # noqa: E402

TEX = RES / "textures"
PREV = Path(__file__).parent / "previews/watch"


def save(img, rel):
    p = TEX / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)
    print("wrote", rel)


def lantern(size=14):
    """A watchman's lantern: ring, cap, a glowing glass body in a dark frame, a base."""
    im = Image.new("RGBA", (size, size + 4))
    d = ImageDraw.Draw(im)
    c, iron, glow, hot = size // 2, (40, 40, 46, 255), (250, 210, 100, 255), (255, 244, 190, 255)
    d.ellipse([c - 2, 0, c + 1, 3], outline=iron)                                  # the ring
    d.polygon([(c - 4, 6), (c + 3, 6), (c + 1, 3), (c - 2, 3)], fill=iron)          # the cap
    d.rectangle([c - 4, 6, c + 3, size], fill=glow, outline=iron)                   # the body
    d.rectangle([c - 2, 8, c + 1, size - 2], fill=hot)
    d.line([(c - 1, 6), (c - 1, size)], fill=iron)                                  # the frame bar
    d.rectangle([c - 5, size + 1, c + 4, size + 2], fill=iron)                      # the base
    return im


def tex():
    # THE WATCH SIGN: a navy board, gold double border, the watchman's lantern, "WATCH" / "HOUSE"
    im = Image.new("RGBA", (64, 32))
    noise(im, (0, 0, 64, 32), (30, 46, 92), 4, 61)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 63, 31], outline=(20, 16, 12, 255))
    d.rectangle([1, 1, 62, 30], outline=(214, 170, 64, 255))
    d.rectangle([3, 3, 60, 28], outline=(150, 112, 40, 255))
    lan = lantern(12)
    im.alpha_composite(lan, (4, 7))
    gold, cream, sh = (238, 196, 80, 255), (232, 226, 206, 255), (10, 14, 30, 255)
    w3 = FONT5["W"]
    FONT5["W"] = ["10001", "10001", "10101", "10101", "01010"]                   # a proper W at sign size
    text(im, "WATCH", 17, 6, gold, 2, sh)
    FONT5["W"] = w3
    text(im, "HOUSE", 38 - text_w("HOUSE") // 2, 21, cream, 1, None)
    save(im, "block/watch_sign_board.png")

    # THE WANTED POSTERS: a cork board, a big WANTED bill (a scowling face, the reward), a small one, a torn note; red pins
    im = Image.new("RGBA", (32, 32))
    noise(im, (0, 0, 32, 32), (150, 108, 66), 9, 62)
    r = random.Random(63)
    for _ in range(40):
        im.putpixel((r.randrange(32), r.randrange(32)), (118, 82, 48, 255))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 31, 31], outline=(70, 46, 26, 255))
    paper, ink, red = (232, 220, 186, 255), (52, 40, 30, 255), (170, 40, 34, 255)
    d.rectangle([1, 2, 25, 29], fill=paper, outline=(196, 180, 140, 255))          # the big bill
    text(im, "WANTED", 2, 4, ink)
    d.line([(3, 10), (23, 10)], fill=(150, 130, 100, 255))
    d.rectangle([7, 12, 19, 22], fill=(214, 200, 164, 255))                          # the sketch
    d.ellipse([9, 13, 17, 21], fill=(198, 160, 120, 255), outline=ink)
    d.line([(8, 14), (18, 14)], fill=ink); d.rectangle([10, 12, 16, 13], fill=ink)   # tricorn brim + crown
    d.point((11, 16), fill=ink); d.point((15, 16), fill=ink)
    d.line([(10, 17), (10, 18)], fill=(150, 40, 40, 255))                            # the scar
    d.rectangle([11, 19, 15, 21], fill=(70, 50, 34, 255))                            # the beard
    text(im, "500", 13 - text_w("500") // 2, 24, red)
    d.ellipse([12, 1, 14, 3], fill=red)
    d.polygon([(26, 5), (30, 4), (30, 27), (27, 28), (28, 20), (26, 18)], fill=(222, 210, 170, 255), outline=(186, 168, 126, 255))  # a torn note
    for y in (8, 10, 12, 14, 22, 24):
        d.line([(28, y), (29, y)], fill=(100, 84, 66, 255))
    d.ellipse([27, 2, 29, 4], fill=(40, 70, 160, 255))
    save(im, "block/wanted_poster.png")


def models():
    built = [hanging_sign("watch_sign")]

    # --- the wanted posters: a thin cork board on the wall (front = north)
    m = BM("wanted_poster", {"board": "pixelpirates:block/wanted_poster", "frame": "minecraft:block/spruce_planks", "particle": "minecraft:block/spruce_planks"})
    m.box([0, 0, 15], [16, 16, 16], faces={"north": "#board", "south": "#frame", "east": "#frame", "west": "#frame", "up": "#frame", "down": "#frame"},
          uv={"north": [0, 0, 16, 16]})
    m.write()
    facing_states("wanted_poster", {"": "pixelpirates:block/wanted_poster"})
    item_model("wanted_poster", "pixelpirates:block/wanted_poster")
    built.append(m)

    # --- the weapon rack: two posts, two rails with pegs, three weapons stood upright against it (front = north)
    m = BM("weapon_rack", {"wood": "minecraft:block/dark_oak_planks", "log": "minecraft:block/dark_oak_log", "iron": "minecraft:block/iron_block",
                           "cutlass": "pixelpirates:item/cutlass", "pike": "pixelpirates:item/boarding_pike", "sabre": "pixelpirates:item/officers_sabre",
                           "particle": "minecraft:block/dark_oak_planks"})
    for x in (0.5, 13.5):
        m.box([x, 0, 14], [x + 2, 16, 16], all="#log")
    for y in (3, 11):
        m.box([0, y, 13], [16, y + 1.5, 16], all="#wood")
        for x in (4, 8, 12):
            m.box([x - 0.5, y + 1.5, 12], [x + 0.5, y + 2.25, 13], all="#iron")
    for x, z, t in ((4, 12.4, "#cutlass"), (8, 12.6, "#pike"), (12, 12.5, "#sabre")):
        m.box([x - 8, 0, z], [x + 8, 16, z], faces={"north": t, "south": t}, uv={"north": [0, 0, 16, 16], "south": [16, 0, 0, 16]},
              rot=("z", -45, [x, 8, z]), shade=False)
    m.write()
    facing_states("weapon_rack", {"": "pixelpirates:block/weapon_rack"})
    item_model("weapon_rack", "pixelpirates:block/weapon_rack")
    built.append(m)

    PREV.mkdir(parents=True, exist_ok=True)
    for b in built:
        b.preview(PREV / f"{b.name}.png")
    print("models:", len(built))


LANG_E = {"block.pixelpirates.watch_sign": "Watch House Sign", "block.pixelpirates.wanted_poster": "Wanted Posters",
          "block.pixelpirates.weapon_rack": "Weapon Rack"}


def lang():
    p = RES / "lang/en_us.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    d.update(LANG_E)
    p.write_bytes(json.dumps(d, indent=2, ensure_ascii=False).encode("utf-8"))
    print("lang +", len(LANG_E))


if __name__ == "__main__":
    what = sys.argv[1:] or ["tex", "models", "lang"]
    if "tex" in what:
        tex()
    if "models" in what:
        models()
    if "lang" in what:
        lang()
