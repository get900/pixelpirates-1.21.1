"""Darts (2026-10-05): the dartboard (block + item model, blockstate, its 128px face) and the 3D dart item model, lang.

    python tools/gen_darts_assets.py

The face is drawn from the SAME geometry the game scores with (homestead/darts/Darts.java - keep R_* and ORDER in step):
radius in board pixels, 20 at the top, clockwise as the thrower sees it. The board element spans x/y 0.5..15.5, so the
128px texture covers 15 px of board: 128/15 texels a pixel."""
import json, math, sys
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import BM, RES, write_blockstate, item_model  # noqa: E402

ORDER = [20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5]
R_BULL, R_OUTER, R_TREBLE_IN, R_TREBLE_OUT, R_DOUBLE_IN, R_DOUBLE = 0.6, 1.3, 3.6, 4.3, 6.2, 7.0
FACE_DEPTH = 2.5
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
PREV = Path(__file__).parent / "previews/darts"

BLACK, CREAM, RED, GREEN = (28, 26, 24), (226, 214, 180), (176, 34, 30), (34, 120, 60)
WIRE, RIM, WOOD = (168, 168, 160), (20, 18, 16), (92, 60, 34)


def face(size=128):
    img = Image.new("RGBA", (size, size))
    k = size / 15.0                                                  # texels per board pixel
    for y in range(size):
        for x in range(size):
            u, v = (x + 0.5 - size / 2) / k, -(y + 0.5 - size / 2) / k
            r = math.hypot(u, v)
            deg = math.degrees(math.atan2(u, v))
            i = int(math.floor((deg + 9) / 18)) % 20
            dark = i % 2 == 0
            if r <= R_BULL: c = RED
            elif r <= R_OUTER: c = GREEN
            elif r <= R_DOUBLE:
                ring = r >= R_DOUBLE_IN or R_TREBLE_IN <= r <= R_TREBLE_OUT
                c = (RED if dark else GREEN) if ring else (BLACK if dark else CREAM)
            elif r <= 7.5: c = RIM
            else: c = WOOD
            # the wires: ring edges and segment edges, a texel wide
            edge = 0.5 / k * 1.6
            if r <= R_DOUBLE + edge and r > R_OUTER:
                near_ring = any(abs(r - b) < edge for b in (R_TREBLE_IN, R_TREBLE_OUT, R_DOUBLE_IN, R_DOUBLE))
                off = (deg + 9) % 18
                near_seg = min(off, 18 - off) * math.pi / 180 * r < edge
                if near_ring or near_seg: c = WIRE
            elif abs(r - R_OUTER) < edge or abs(r - R_BULL) < edge: c = WIRE
            # a little grain on the sisal
            n = ((x * 73856093) ^ (y * 19349663)) % 7 - 3
            img.putpixel((x, y), tuple(max(0, min(255, ch + n)) for ch in c) + (255,))
    # the numbers ring: a light tick for each segment (numbers would be unreadable at this size)
    for i in range(20):
        a = math.radians(i * 18)
        for t in (7.15, 7.3):
            x, y = size / 2 + math.sin(a) * t * k, size / 2 - math.cos(a) * t * k
            img.putpixel((int(x), int(y)), (200, 190, 160, 255))
    return img


def rim():
    img = Image.new("RGBA", (16, 16), RIM + (255,))
    for x in range(16):
        img.putpixel((x, 0), (40, 36, 32, 255)); img.putpixel((x, 15), (12, 10, 8, 255))
    return img


def board():
    m = BM("dartboard", {"wood": "minecraft:block/dark_oak_planks", "face": "pixelpirates:block/dartboard_face",
                         "rim": "pixelpirates:block/dartboard_rim", "particle": "pixelpirates:block/dartboard_face"})
    m.box([0, 0, 15], [16, 16, 16], all="#wood")                                     # the back plate on the wall
    m.box([0.5, 0.5, 16 - FACE_DEPTH], [15.5, 15.5, 15], faces={"north": "#face", "east": "#rim", "west": "#rim", "up": "#rim", "down": "#rim"},
          uv={"north": [0, 0, 16, 16]})
    m.write()
    write_blockstate("dartboard", {"variants": {f"facing={f}": ({"model": "pixelpirates:block/dartboard", "y": r} if r else
                                                               {"model": "pixelpirates:block/dartboard"}) for f, r in ROT.items()}})
    item_model("dartboard", "pixelpirates:block/dartboard")
    return m


def dart():
    """The dart, tip toward +Z (z 15), centred on x/y 8 - DartRenderer relies on this."""
    tx = {"tip": "minecraft:block/iron_block", "brass": "minecraft:block/gold_block", "shaft": "minecraft:block/black_concrete",
          "flight": "minecraft:block/red_wool", "particle": "minecraft:block/gold_block"}
    els = []

    def box(f, t, tex):
        faces = {d: {"texture": tex} for d in ("north", "south", "east", "west", "up", "down")}
        els.append({"from": f, "to": t, "faces": faces})
    box([7.75, 7.75, 13], [8.25, 8.25, 15], "#tip")
    box([7.5, 7.5, 9], [8.5, 8.5, 13], "#brass")
    box([7.75, 7.75, 5], [8.25, 8.25, 9], "#shaft")
    box([6.5, 8, 1], [9.5, 8, 5], "#flight")                                         # two crossed flights (flat)
    box([8, 6.5, 1], [8, 9.5, 5], "#flight")
    model = {"textures": tx, "elements": els, "display": {
        "gui": {"rotation": [0, 90, -45], "scale": [1.6, 1.6, 1.6]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
        "fixed": {"rotation": [0, 90, -45], "scale": [1.4, 1.4, 1.4]},
        "thirdperson_righthand": {"rotation": [-80, 0, 0], "translation": [0, 3, 1], "scale": [0.9, 0.9, 0.9]},
        "thirdperson_lefthand": {"rotation": [-80, 0, 0], "translation": [0, 3, 1], "scale": [0.9, 0.9, 0.9]},
        "firstperson_righthand": {"rotation": [-10, 180, 0], "translation": [1, 4, 1], "scale": [0.9, 0.9, 0.9]},
        "firstperson_lefthand": {"rotation": [-10, 180, 0], "translation": [1, 4, 1], "scale": [0.9, 0.9, 0.9]}}}
    p = RES / "models/item/dart.json"
    p.write_bytes(json.dumps(model, indent=1).encode("utf-8"))


LANG = {"block.pixelpirates.dartboard": "Dartboard", "item.pixelpirates.dart": "Dart", "entity.pixelpirates.dart": "Dart"}

if __name__ == "__main__":
    PREV.mkdir(parents=True, exist_ok=True)
    f = face()
    f.save(RES / "textures/block/dartboard_face.png")
    rim().save(RES / "textures/block/dartboard_rim.png")
    f.resize((384, 384), Image.NEAREST).save(PREV / "face.png")
    board().preview(PREV / "dartboard.png", size=256)
    dart()
    p = RES / "lang/en_us.json"
    d = json.loads(p.read_text(encoding="utf-8")); d.update(LANG)
    p.write_bytes(json.dumps(d, indent=2, ensure_ascii=False).encode("utf-8"))
    print("darts assets ok")
