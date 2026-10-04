"""Town life round 3 (2026-10-04): the STREET LAMP (lit / unlit, standing / hanging) and the COMMODORE'S CHESS TROPHY.

    python tools/gen_town_assets.py

The street lamp reuses vanilla's lantern shapes (template_lantern / template_hanging_lantern); lit = vanilla's lantern
texture, unlit = its first frame with the flame and glass darkened (cut from the client jar Loom keeps)."""
import io, json, sys, zipfile
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import BM, RES, write_blockstate, item_model  # noqa: E402

PREV = Path(__file__).parent / "previews/town"
JAR = Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def street_lamp():
    with zipfile.ZipFile(JAR) as z:
        img = Image.open(io.BytesIO(z.read("assets/minecraft/textures/block/lantern.png"))).convert("RGBA")
    frame = img.crop((0, 0, 16, 16))
    off = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            r, g, b, a = frame.getpixel((x, y))
            if a and r > 150 and g > 110:                                          # the flame + lit glass -> cold dark glass
                v = int((r + g + b) / 3 * 0.25)
                off.putpixel((x, y), (v, v + 6, v + 12, a))
            else:
                off.putpixel((x, y), (r, g, b, a))
    p = RES / "textures/block/street_lamp_off.png"
    p.parent.mkdir(parents=True, exist_ok=True)
    off.save(p)
    for lit in (True, False):
        for hanging in (False, True):
            name = "street_lamp" + ("_on" if lit else "_off") + ("_hanging" if hanging else "")
            model = {"parent": "minecraft:block/template_hanging_lantern" if hanging else "minecraft:block/template_lantern",
                     "textures": {"lantern": "minecraft:block/lantern" if lit else "pixelpirates:block/street_lamp_off"}}
            (RES / "models/block" / f"{name}.json").write_bytes(json.dumps(model, indent=1).encode("utf-8"))
    write_blockstate("street_lamp", {"variants": {
        f"hanging={str(h).lower()},lit={str(l).lower()}": {"model": "pixelpirates:block/street_lamp" + ("_on" if l else "_off") + ("_hanging" if h else "")}
        for h in (False, True) for l in (False, True)}})
    item_model("street_lamp", "pixelpirates:block/street_lamp_on")


def trophy():
    m = BM("chess_trophy", {"gold": "minecraft:block/gold_block", "base": "minecraft:block/polished_blackstone",
                            "ivory": "pixelpirates:block/chess_ivory", "particle": "minecraft:block/gold_block"})
    m.box([4, 0, 4], [12, 2, 12], all="#base")
    m.box([5, 2, 5], [11, 3, 11], all="#gold")
    m.box([7, 3, 7], [9, 6, 9], all="#gold")                                     # the stem
    m.box([5, 6, 5], [11, 7, 11], all="#gold")
    m.box([4.5, 7, 4.5], [11.5, 12, 11.5], all="#gold")                          # the cup
    m.box([3, 9, 7.25], [4.5, 11, 8.75], all="#gold"); m.box([11.5, 9, 7.25], [13, 11, 8.75], all="#gold")   # handles
    # a little ivory knight on the lid
    m.box([7, 12, 7], [9, 13, 9], all="#ivory")
    m.box([7.25, 13, 7.5], [8.75, 16, 9], all="#ivory")
    m.box([7.25, 15, 6.5], [8.75, 16.5, 8.5], all="#ivory")
    m.write()
    write_blockstate("chess_trophy", {"variants": {f"facing={f}": ({"model": "pixelpirates:block/chess_trophy", "y": r} if r else
                                                                    {"model": "pixelpirates:block/chess_trophy"}) for f, r in ROT.items()}})
    item_model("chess_trophy", "pixelpirates:block/chess_trophy")
    return m


LANG = {"block.pixelpirates.street_lamp": "Street Lamp", "block.pixelpirates.chess_trophy": "The Commodore's Chess Trophy",
        "entity.pixelpirates.seagull": "Seagull"}

if __name__ == "__main__":
    PREV.mkdir(parents=True, exist_ok=True)
    street_lamp()
    trophy().preview(PREV / "chess_trophy.png", size=256)
    p = RES / "lang/en_us.json"
    d = json.loads(p.read_text(encoding="utf-8")); d.update(LANG)
    p.write_bytes(json.dumps(d, indent=2, ensure_ascii=False).encode("utf-8"))
    print("town assets ok")
