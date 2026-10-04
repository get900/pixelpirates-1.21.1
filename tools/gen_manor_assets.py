"""THE GOVERNOR'S RESIDENCE - statues, chandeliers and the portrait (2026-10-01).

    python tools/gen_manor_assets.py            # everything
    python tools/gen_manor_assets.py tex|models|lang

tex   : textures/block/marble (veined white stone), portrait_governor (32x32 painted portrait in a gilt frame)
models: governor_statue (2 blocks tall), lion_statue, sea_god_statue (2 tall), marble_bust, garden_urn,
        crystal_chandelier, governor_portrait (+ blockstates by facing, item models, previews in tools/previews/manor/)
"""
import json, math, random, sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import BM, RES, write_blockstate, item_model  # noqa: E402
from gen_tavern_assets import noise  # noqa: E402

TEX = RES / "textures"
PREV = Path(__file__).parent / "previews/manor"
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
M, G = "pixelpirates:block/marble", "minecraft:block/gold_block"


def save(img, rel):
    p = TEX / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)
    print("wrote", rel)


def tex():
    # marble: warm white, soft grey veins
    im = Image.new("RGBA", (16, 16))
    noise(im, (0, 0, 16, 16), (236, 232, 224), 4, 50)
    d = ImageDraw.Draw(im)
    r = random.Random(51)
    for _ in range(3):
        x, y = r.randint(0, 15), 0
        while y < 16:
            im.putpixel((x % 16, y), (196, 194, 190, 255))
            x += r.choice((-1, 0, 1)); y += 1
    save(im, "block/marble.png")
    # the governor's portrait: gilt frame, a dark oil ground, red coat, white wig, tricorn, a ship behind
    im = Image.new("RGBA", (32, 32))
    noise(im, (0, 0, 32, 32), (36, 30, 26), 6, 52)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 31, 31], outline=(150, 110, 30, 255)); d.rectangle([1, 1, 30, 30], outline=(232, 190, 70, 255)); d.rectangle([2, 2, 29, 29], outline=(170, 128, 40, 255))
    d.rectangle([3, 3, 28, 14], fill=(70, 86, 100, 255))                                   # a grey sea sky
    d.rectangle([3, 12, 28, 15], fill=(40, 60, 80, 255))
    d.polygon([(20, 11), (27, 11), (26, 13), (21, 13)], fill=(60, 40, 24, 255))            # a ship on the horizon
    d.line([(23, 6), (23, 11)], fill=(50, 34, 20, 255)); d.polygon([(23, 6), (26, 9), (23, 10)], fill=(220, 214, 200, 255))
    d.rectangle([9, 18, 22, 28], fill=(150, 30, 36, 255))                                   # the red coat
    d.rectangle([14, 18, 17, 28], fill=(232, 222, 200, 255))                                # the waistcoat
    d.line([(15, 19), (15, 27)], fill=(214, 172, 60, 255))
    d.rectangle([9, 18, 11, 22], fill=(214, 172, 60, 255)); d.rectangle([20, 18, 22, 22], fill=(214, 172, 60, 255))   # epaulettes
    d.ellipse([12, 9, 19, 17], fill=(226, 190, 160, 255))                                    # face
    d.rectangle([11, 10, 12, 16], fill=(236, 236, 230, 255)); d.rectangle([19, 10, 20, 16], fill=(236, 236, 230, 255))   # wig
    d.polygon([(9, 9), (22, 9), (19, 6), (15, 5), (12, 6)], fill=(28, 24, 22, 255))         # tricorn
    d.line([(10, 9), (21, 9)], fill=(214, 172, 60, 255))
    d.point((14, 12), fill=(40, 30, 30, 255)); d.point((17, 12), fill=(40, 30, 30, 255)); d.line([(14, 15), (17, 15)], fill=(150, 80, 70, 255))
    save(im, "block/portrait_governor.png")


def facing_states(name, models):
    v = {}
    for f, r in ROT.items():
        for extra, model in models.items():
            key = f"facing={f}" + ("," + extra if extra else "")
            v[key] = {"model": model, "y": r} if r else {"model": model}
    write_blockstate(name, {"variants": v})


def simple(name, m, display=None):
    m.write()
    facing_states(name, {"": f"pixelpirates:block/{name}"})
    if display:
        (RES / f"models/item/{name}.json").write_bytes(json.dumps({"parent": f"pixelpirates:block/{name}", "display": display}, indent=1).encode())
    else:
        item_model(name, f"pixelpirates:block/{name}")
    return m


TALL = {"gui": {"rotation": [30, 225, 0], "translation": [0, -3, 0], "scale": [0.42, 0.42, 0.42]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]},
        "fixed": {"rotation": [0, 0, 0], "translation": [0, -2, 0], "scale": [0.4, 0.4, 0.4]},
        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.25, 0.25, 0.25]},
        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.3, 0.3, 0.3]}}


def models():
    built = []
    mt = {"m": M, "g": G, "particle": M}

    # --- the GOVERNOR: two blocks tall - boots, coat with flared skirts, sash, sword at his side, wig + tricorn (front = north)
    m = BM("governor_statue", {**mt, "iron": "minecraft:block/iron_block"})
    m.box([2, 0, 3], [14, 2, 13], all="#m")                                  # the plinth
    m.box([5.5, 2, 6.5], [7.8, 11, 9], all="#m"); m.box([8.2, 2, 6.5], [10.5, 11, 9], all="#m")          # legs
    m.box([5, 2, 5.5], [8, 3.5, 9], all="#m"); m.box([8, 2, 5.5], [11, 3.5, 9], all="#m")                # boots
    m.box([4, 9, 5.5], [12, 15, 10.5], all="#m")                                                         # coat skirts
    m.box([4.5, 15, 6], [11.5, 22, 10], all="#m")                                                        # torso
    m.box([5, 17, 5.6], [11, 18, 6], all="#g")                                                           # sash
    m.box([3, 15, 6.5], [4.5, 22, 9], all="#m"); m.box([2.5, 21, 6], [5, 22.5, 9.5], all="#g")         # right arm + epaulette
    m.box([11.5, 18, 6.5], [13, 22, 9], all="#m"); m.box([11, 21, 6], [13.5, 22.5, 9.5], all="#g")      # left arm, epaulette
    m.box([12, 15, 6], [13.5, 18, 8], all="#m")                                                          # hand on hip
    m.box([2.6, 7, 7.2], [3.2, 15, 7.8], all="#iron"); m.box([2.2, 15, 6.6], [3.6, 16, 8.4], all="#g")  # the sword
    m.box([6, 22, 6.5], [10, 26.5, 10], all="#m")                                                        # head
    m.box([5.5, 23, 8], [6, 26, 10], all="#m"); m.box([10, 23, 8], [10.5, 26, 10], all="#m")            # wig curls
    m.box([4.5, 26.5, 5.5], [11.5, 27.5, 11], all="#m"); m.box([6, 27.5, 6.5], [10, 29.5, 10], all="#m")   # tricorn
    m.box([4.5, 27.4, 5.5], [11.5, 27.6, 5.8], all="#g")
    built.append(simple("governor_statue", m, TALL))

    # --- the LION: a sitting lion on a little plinth (front = north)
    m = BM("lion_statue", mt)
    m.box([1, 0, 1], [15, 2, 15], all="#m")
    m.box([4, 2, 7], [12, 9, 15], all="#m")                                  # haunches
    m.box([5, 2, 3], [7, 9, 6], all="#m"); m.box([9, 2, 3], [11, 9, 6], all="#m")            # forelegs
    m.box([4.5, 2, 2], [7.5, 3.5, 5], all="#m"); m.box([8.5, 2, 2], [11.5, 3.5, 5], all="#m")  # paws
    m.box([4.5, 7, 4], [11.5, 13, 10], all="#m")                             # chest
    m.box([3.5, 10, 3.5], [12.5, 17, 9], all="#m")                           # the mane
    m.box([5.5, 11, 1.5], [10.5, 15.5, 4], all="#m")                         # face
    m.box([6.8, 11, 0.8], [9.2, 12.8, 1.5], all="#m")                        # muzzle
    m.box([11.5, 2, 13], [13, 4, 15], all="#m"); m.box([12.5, 4, 13.5], [14, 7, 14.5], all="#m")   # tail curl
    built.append(simple("lion_statue", m))

    # --- the SEA GOD for the fountain: on a great shell, trident raised, gilt crown (2 tall, front = north)
    m = BM("sea_god_statue", {**mt, "pris": "minecraft:block/prismarine"})
    m.box([1, 0, 2], [15, 3, 14], all="#m"); m.box([3, 3, 4], [13, 5, 12], all="#m")              # the shell
    for x in (2, 5, 8, 11):
        m.box([x, 3, 2.5], [x + 2, 4.5, 3.2], all="#pris")
    m.box([5, 5, 6], [11, 13, 11], all="#pris")                                                   # the fish tail coil
    m.box([10, 5, 10], [13, 9, 13], all="#pris")
    m.box([5.5, 13, 6.5], [10.5, 21, 10.5], all="#m")                                            # torso
    m.box([6.5, 21, 7], [9.5, 25, 10], all="#m")                                                 # head
    m.box([6, 23.5, 6.8], [10, 24.5, 10.2], all="#m")                                            # beard
    m.box([6.3, 25, 7.2], [9.7, 26.5, 9.8], all="#g")                                            # crown
    for x in (6.3, 7.8, 9.2):
        m.box([x, 26.5, 8.2], [x + 0.5, 27.5, 8.8], all="#g")
    m.box([10.5, 17, 7.5], [12, 23, 9.5], all="#m")                                              # raised arm
    m.box([11, 12, 8.2], [11.6, 31, 8.8], all="#g")                                              # the trident shaft
    for x in (9.8, 11, 12.2):
        m.box([x, 28, 8.2], [x + 0.6, 31.5, 8.8], all="#g")
    m.box([9.8, 28, 8.2], [12.8, 28.6, 8.8], all="#g")
    m.box([4, 14, 7.5], [5.5, 20, 9.5], all="#m")                                                # other arm
    built.append(simple("sea_god_statue", m, TALL))

    # --- a marble BUST on a column
    m = BM("marble_bust", mt)
    m.box([4, 0, 4], [12, 1.5, 12], all="#m"); m.box([5.5, 1.5, 5.5], [10.5, 9, 10.5], all="#m"); m.box([4.5, 9, 4.5], [11.5, 10, 11.5], all="#m")
    m.box([4.5, 10, 6], [11.5, 13, 10], all="#m")                            # shoulders
    m.box([6, 13, 6.5], [10, 17, 10], all="#m")                              # head
    m.box([5.6, 14, 8], [6, 17, 10], all="#m"); m.box([10, 14, 8], [10.4, 17, 10], all="#m")
    built.append(simple("marble_bust", m))

    # --- a GARDEN URN brimming with flowers
    m = BM("garden_urn", {**mt, "leaf": "minecraft:block/flowering_azalea_leaves", "particle": M})
    m.box([5, 0, 5], [11, 2, 11], all="#m"); m.box([6.5, 2, 6.5], [9.5, 4, 9.5], all="#m")
    m.box([4, 4, 4], [12, 9, 12], all="#m"); m.box([3, 9, 3], [13, 10.5, 13], all="#m")
    m.box([4, 10.5, 4], [12, 14, 12], all="#leaf")
    built.append(simple("garden_urn", m))

    # --- the CRYSTAL CHANDELIER: gilt ring and arms, candles, hanging crystals (hangs from the ceiling)
    m = BM("crystal_chandelier", {"g": G, "crystal": "minecraft:block/amethyst_block", "glass": "minecraft:block/white_stained_glass",
                                  "candle": "minecraft:block/white_wool", "flame": "minecraft:block/shroomlight", "chain": "minecraft:block/chain",
                                  "particle": G}, ao=False)
    m.box([7.5, 11, 7.5], [8.5, 16, 8.5], all="#g")
    m.box([1, 5, 1], [15, 6, 2], all="#g"); m.box([1, 5, 14], [15, 6, 15], all="#g"); m.box([1, 5, 2], [2, 6, 14], all="#g"); m.box([14, 5, 2], [15, 6, 14], all="#g")
    m.box([7, 5, 7], [9, 11, 9], all="#g")
    m.box([2, 6.5, 7.5], [14, 7.5, 8.5], all="#g"); m.box([7.5, 6.5, 2], [8.5, 7.5, 14], all="#g")
    for x, z in ((1.25, 1.25), (13.75, 1.25), (1.25, 13.75), (13.75, 13.75), (7.75, 1.25), (7.75, 13.75), (1.25, 7.75), (13.75, 7.75)):
        m.box([x, 6, z], [x + 1, 8.5, z + 1], all="#candle")
        m.box([x + 0.25, 8.5, z + 0.25], [x + 0.75, 9.5, z + 0.75], all="#flame")
        m.box([x + 0.2, 2.5, z + 0.2], [x + 0.8, 5, z + 0.8], all="#crystal")
    m.box([6.5, 1, 6.5], [9.5, 5, 9.5], all="#glass"); m.box([7.3, 0, 7.3], [8.7, 1, 8.7], all="#crystal")
    built.append(simple("crystal_chandelier", m))

    # --- the GOVERNOR'S PORTRAIT: a gilt-framed oil painting on the wall (front = north)
    m = BM("governor_portrait", {"art": "pixelpirates:block/portrait_governor", "g": G, "particle": G})
    m.box([0, 0, 15], [16, 16, 16], faces={"north": "#art", "south": "#g", "east": "#g", "west": "#g", "up": "#g", "down": "#g"}, uv={"north": [0, 0, 16, 16]})
    built.append(simple("governor_portrait", m))

    PREV.mkdir(parents=True, exist_ok=True)
    for b in built:
        b.preview(PREV / f"{b.name}.png")
    print("models:", len(built))


LANG_E = {"block.pixelpirates.governor_statue": "Statue of the Governor", "block.pixelpirates.lion_statue": "Marble Lion",
          "block.pixelpirates.sea_god_statue": "Statue of the Sea God", "block.pixelpirates.marble_bust": "Marble Bust",
          "block.pixelpirates.garden_urn": "Garden Urn", "block.pixelpirates.crystal_chandelier": "Crystal Chandelier",
          "block.pixelpirates.governor_portrait": "Portrait of the Governor"}


def lang():
    p = RES / "lang/en_us.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    d.update(LANG_E)
    p.write_bytes(json.dumps(d, indent=2, ensure_ascii=False).encode("utf-8"))
    print("lang +", len(LANG_E))


if __name__ == "__main__":
    what = sys.argv[1:] or ["tex", "models", "lang"]
    if "tex" in what: tex()
    if "models" in what: models()
    if "lang" in what: lang()
