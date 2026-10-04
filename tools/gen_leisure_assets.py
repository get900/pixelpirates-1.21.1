"""The easel, the swings and the barber's chair (2026-10-04): block models, blockstates, item icons, the canvas texture, lang.

    python tools/gen_leisure_assets.py

Models face NORTH (the front / the way a swing swings is along Z) and are rotated by facing in the blockstates.
Two-block-tall pieces keep all their geometry on the LOWER half's model (up to y 32); the upper half is an empty model.
The swing's moving seat (block/swing_seat_moving) is drawn by homestead/client/SwingRenderer about its pivot (y 30.4)."""
import json, random, sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import BM, RES, write_blockstate, item_model  # noqa: E402

PREV = Path(__file__).parent / "previews/leisure"
OAK, STRIP, ROPE = "minecraft:block/oak_planks", "minecraft:block/stripped_oak_log", "pixelpirates:block/rope"
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def save(img, rel):
    p = RES / "textures" / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)


def empty(name, particle):
    p = RES / "models/block" / f"{name}.json"
    p.write_bytes(json.dumps({"textures": {"particle": particle}, "elements": []}, indent=1).encode("utf-8"))


def tall_states(name, lower, upper, extra=None):
    """facing x half (x extra boolean) -> models (lower model id(s), upper model id)."""
    v = {}
    for f, r in ROT.items():
        for half in ("lower", "upper"):
            for ex in (extra or [None]):
                key = f"facing={f},half={half}" + (f",{ex[0]}" if ex else "")
                model = (ex[1] if ex else lower) if half == "lower" else upper
                v[key] = {"model": model, "y": r} if r else {"model": model}
    write_blockstate(name, {"variants": v})


# ------------------------------------------------------------------------------------------------ textures
def canvas_tex():
    r = random.Random(5)
    img = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            base = 236 + (4 if (x + y) % 2 == 0 else -3) + r.randint(-3, 3)
            img.putpixel((x, y), (base, base - 6, base - 20, 255))
    save(img, "block/canvas.png")


def icon(name, rows, pal):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch != ".": img.putpixel((x, y), pal[ch])
    save(img, f"item/{name}.png")
    if name in ("blank_canvas", "player_painting"): return               # items: their models come from datagen (src/main/generated)
    p = RES / "models/item" / f"{name}.json"
    p.write_bytes(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"pixelpirates:item/{name}"}}, indent=1).encode("utf-8"))


W = {"w": (120, 84, 50, 255), "d": (70, 46, 26, 255), "c": (240, 232, 214, 255), "s": (205, 196, 178, 255), "r": (200, 60, 50, 255),
     "b": (60, 110, 200, 255), "y": (240, 200, 70, 255), "g": (80, 150, 60, 255), "k": (40, 30, 24, 255), "t": (170, 140, 90, 255)}

EASEL_I = ["......d.........", ".....dwd........", "..dddddddddddd..", "..dccccccccccd..", "..dcbbbbbbbbcd..", "..dcbbbyybbbcd..",
           "..dcbbbbbbggcd..", "..dcgggbbgggcd..", "..dcggggggggcd..", "..dddddddddddd..", "..dwwwwwwwwwwd..", "...w...w...w....",
           "...w...w...w....", "..w....w....w...", "..w....w....w...", ".w.....w.....w.."]
CANVAS_I = ["................", "..dddddddddddd..", "..dccccccccccd..", "..dcsccccccscd..", "..dccccccccccd..", "..dccccscccccd..",
            "..dccccccccccd..", "..dcccccccsccd..", "..dccccccccccd..", "..dcsccccccccd..", "..dccccccccccd..", "..dccccccccscd..",
            "..dccccccccccd..", "..dddddddddddd..", "................", "................"]
PAINT_I = ["................", ".dddddddddddddd.", ".dyyyyyyyyyyyyd.", ".dybbbbbbbbbbyd.", ".dybbbbbbyybbyd.", ".dybbbbbbyybbyd.",
           ".dybbbbbbbbbbyd.", ".dybbbbbbbbbgyd.", ".dybbgbbbbbggyd.", ".dybgggbbbgggyd.", ".dygggggggggyd.", ".dygggggggggyyd.",
           ".dyyyyyyyyyyyyd.", ".dddddddddddddd.", "................", "................"]
SWING_I = ["dddddddddddddddd", "d..............d", "d...t......t...d", "d...t......t...d", "d...t......t...d", "d...t......t...d",
           "d...t......t...d", "d...t......t...d", "d...t......t...d", "d...wwwwwwww...d", "d...dddddddd...d", "d..............d",
           "d..............d", "d..............d", "dd............dd", "................"]
HSWING_I = ["....kk....kk....", "....t......t....", "....t......t....", "....t......t....", "....t......t....", "....t......t....",
            "....t......t....", "....t......t....", "....t......t....", "....t......t....", "...wwwwwwwwww...", "...dddddddddd...",
            "................", "................", "................", "................"]


# ------------------------------------------------------------------------------------------------ models
def easel():
    m = BM("easel", {"wood": STRIP, "plank": OAK, "canvas": "pixelpirates:block/canvas", "particle": OAK})
    for x in (2, 12.5):                                                            # the front legs
        m.box([x, 0, 7], [x + 1.5, 30, 8.5], all="#wood")
    m.box([7.25, 0, 7.5], [8.75, 28, 9], all="#wood", rot=("x", -22.5, [8, 28, 8.5]))  # the back leg, raked
    m.box([2, 8, 7.25], [14, 9, 8.25], all="#wood")                                 # the crossbar
    m.box([0, 12, 4], [16, 13.5, 8], all="#plank")                                  # the tray
    m.box([0, 13, 6], [16, 29, 7], faces={"north": "#canvas", "south": "#plank", "up": "#plank", "down": "#plank", "east": "#plank", "west": "#plank"})
    m.box([6, 28.5, 5.5], [10, 30.5, 8], all="#wood")                               # the clamp
    m.box([1, 13.5, 4.5], [3, 14.5, 5.5], all="#wood")                              # a brush on the tray
    m.box([12, 13.5, 4.5], [14.5, 14.2, 6.5], faces={"up": "pixelpirates:block/canvas"}, skip=())
    m.write()
    empty("easel_top", OAK)
    tall_states("easel", "pixelpirates:block/easel", "pixelpirates:block/easel_top")
    icon("easel", EASEL_I, W)
    return m


def swings():
    t = {"wood": STRIP, "plank": OAK, "rope": ROPE, "particle": OAK}
    def seat(m):
        for x in (3.5, 12):                                                           # the ropes
            m.box([x, 8, 7.75], [x + 0.5, 30.4, 8.25], all="#rope")
        m.box([3, 6.5, 5.5], [13, 8, 10.5], all="#plank")                              # the seat
    def frame(m):
        for x in (0, 14.5):
            m.box([x, 0, 7.25], [x + 1.5, 31, 8.75], all="#wood")                      # the posts
            m.box([x, 0, 0], [x + 1.5, 1.5, 16], all="#wood")                          # the feet
            m.box([x, 1.5, 3], [x + 1.5, 3, 4], all="#wood"); m.box([x, 1.5, 12], [x + 1.5, 3, 13], all="#wood")
        m.box([0, 29.5, 7.25], [16, 31, 8.75], all="#wood")                            # the top beam
    a = BM("swing", t); frame(a); seat(a); a.write()
    b = BM("swing_occupied", t); frame(b); b.write()
    c = BM("hanging_swing", t)
    for x in (3.5, 12): c.box([x, 30.4, 7.75], [x + 0.5, 32, 8.25], all="#rope")
    seat(c); c.write()
    d = BM("hanging_swing_occupied", t)
    for x in (3.5, 12): d.box([x, 30.4, 7.75], [x + 0.5, 32, 8.25], all="#rope")      # the knots stay
    d.write()
    e = BM("swing_seat_moving", t); seat(e); e.write()
    empty("swing_top", OAK)
    tall_states("swing", None, "pixelpirates:block/swing_top",
                extra=[("occupied=false", "pixelpirates:block/swing"), ("occupied=true", "pixelpirates:block/swing_occupied")])
    # the upper half's states need the occupied key too
    v = json.loads((RES / "blockstates/swing.json").read_text())["variants"]
    for k in list(v):
        if "half=upper" in k: v[k]["model"] = "pixelpirates:block/swing_top"
    write_blockstate("swing", {"variants": v})
    tall_states("hanging_swing", None, "pixelpirates:block/swing_top",
                extra=[("occupied=false", "pixelpirates:block/hanging_swing"), ("occupied=true", "pixelpirates:block/hanging_swing_occupied")])
    icon("swing", SWING_I, W)
    icon("hanging_swing", HSWING_I, W)
    return a, c


def barber_chair():
    m = BM("barber_chair", {"leather": "minecraft:block/red_wool", "brass": "minecraft:block/gold_block", "iron": "minecraft:block/iron_block",
                            "particle": "minecraft:block/red_wool"})
    m.box([4, 0, 4], [12, 1, 12], all="#brass")                                    # the base
    m.box([7, 1, 7], [9, 5, 9], all="#iron")                                       # the pedestal (the pump)
    m.box([2, 5, 2], [14, 8, 13], all="#leather")                                  # the seat
    m.box([2, 8, 12], [14, 21, 14], all="#leather")                                # the back
    m.box([5, 21, 12.5], [11, 24, 13.5], all="#leather")                           # the headrest
    m.box([2, 20.5, 11.5], [14, 21.5, 14.5], all="#brass")
    for x in (1, 13):
        m.box([x, 9, 3], [x + 2, 10.5, 12], all="#leather")                         # armrests
        m.box([x + 0.5, 5, 10], [x + 1.5, 9, 11], all="#brass")
    m.box([4, 2, -1], [12, 3, 2], all="#iron")                                     # the footrest
    m.box([7.5, 3, 1], [8.5, 5, 2], all="#iron")
    m.write()
    write_blockstate("barber_chair", {"variants": {f"facing={f}": ({"model": "pixelpirates:block/barber_chair", "y": r} if r else
                                                                  {"model": "pixelpirates:block/barber_chair"}) for f, r in ROT.items()}})
    item_model("barber_chair", "pixelpirates:block/barber_chair")
    return m


LANG = {"block.pixelpirates.easel": "Easel", "block.pixelpirates.swing": "Swing", "block.pixelpirates.hanging_swing": "Hanging Swing",
        "block.pixelpirates.barber_chair": "Barber's Chair", "item.pixelpirates.blank_canvas": "Blank Canvas",
        "item.pixelpirates.player_painting": "Painting", "entity.pixelpirates.player_painting": "Painting",
        "entity.pixelpirates.swing_seat": "Swing"}


def lang():
    p = RES / "lang/en_us.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    d.update(LANG)
    p.write_bytes(json.dumps(d, indent=2, ensure_ascii=False).encode("utf-8"))


if __name__ == "__main__":
    PREV.mkdir(parents=True, exist_ok=True)
    canvas_tex()
    icon("blank_canvas", CANVAS_I, W)
    icon("player_painting", PAINT_I, W)
    e = easel(); e.preview(PREV / "easel.png", size=320)
    s, h = swings(); s.preview(PREV / "swing.png", size=320); h.preview(PREV / "hanging_swing.png", size=320)
    b = barber_chair(); b.preview(PREV / "barber_chair.png", size=256)
    lang()
    print("ok")
