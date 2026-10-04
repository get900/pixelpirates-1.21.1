"""Chess (2026-10-04): the chess table + giant chess pedestal models, the board / piece textures, the 12 screen icons, lang.

    python tools/gen_chess_assets.py

The 3D pieces are drawn in code (homestead/client/ChessRenderer) with textures/entity/chess_ivory.png + chess_ebony.png.
The table's board top is at y 15 (16ths), a-file at +x, white's side at the model's north (-z) - ChessRenderer matches."""
import json, random, sys
from pathlib import Path

from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import BM, RES, write_blockstate, item_model  # noqa: E402

PREV = Path(__file__).parent / "previews/chess"
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def save(img, rel):
    p = RES / "textures" / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)


def grain(base, seed, size=64, streak=True):
    r = random.Random(seed)
    img = Image.new("RGBA", (size, size))
    for y in range(size):
        band = r.randint(-6, 6)
        for x in range(size):
            n = r.randint(-7, 7) + (band if streak else 0)
            img.putpixel((x, y), tuple(max(0, min(255, c + n)) for c in base) + (255,))
    return img


def textures():
    save(grain((236, 224, 196), 1), "entity/chess_ivory.png")
    save(grain((236, 224, 196), 1), "block/chess_ivory.png")   # block models need it in the block atlas (chess trophy)
    save(grain((46, 34, 30), 2), "entity/chess_ebony.png")
    board = Image.new("RGBA", (16, 16))
    for y in range(16):
        for x in range(16):
            light = ((x // 2) + (y // 2)) % 2 == 0
            board.putpixel((x, y), (232, 214, 176, 255) if light else (126, 84, 52, 255))
    save(board, "block/chess_board.png")
    clock = Image.new("RGBA", (16, 16), (40, 30, 24, 255))
    d = ImageDraw.Draw(clock)
    for cx in (4, 11):
        d.ellipse([cx - 3, 4, cx + 3, 10], fill=(238, 232, 214, 255), outline=(180, 150, 60, 255))
        d.line([cx, 7, cx, 5], fill=(30, 30, 30, 255)); d.line([cx, 7, cx + 2, 7], fill=(30, 30, 30, 255))
    d.rectangle([2, 1, 5, 2], fill=(200, 170, 70, 255)); d.rectangle([10, 1, 13, 2], fill=(200, 170, 70, 255))
    save(clock, "block/chess_clock.png")


# 16x16 silhouettes for the screen (row strings, '#' = body, 'o' = outline-dark detail)
ICONS = {
    "p": ["................", "................", "................", "......####......", ".....######.....", ".....######.....", "......####......",
          ".....######.....", "......####......", "......####......", ".....######.....", "....########....", "...##########...", "...##########...", "................", "................"],
    "n": ["................", "................", "......##.#......", ".....######.....", "....########....", "...####o#####...", "..##########.#..", "..####..######..",
          "........######..", ".......######...", "......######....", ".....########...", "....##########..", "....##########..", "................", "................"],
    "b": ["................", ".......##.......", "......####......", ".....##o###.....", ".....#o####.....", ".....######.....", "......####......", "....########....",
          "......####......", "......####......", ".....######.....", "....########....", "...##########...", "...##########...", "................", "................"],
    "r": ["................", "................", "...##.####.##...", "...##.####.##...", "...##########...", "....########....", ".....######.....", ".....######.....",
          ".....######.....", ".....######.....", "....########....", "...##########...", "...##########...", "...##########...", "................", "................"],
    "q": ["................", "..#..#.##.#..#..", "..#..#.##.#..#..", "..##.######.##..", "...##########...", "...##########...", "....########....", ".....######.....",
          ".....######.....", ".....######.....", "....########....", "...##########...", "...##########...", "...##########...", "................", "................"],
    "k": [".......##.......", "......####......", ".......##.......", "....###..###....", "...##########...", "...##########...", "....########....", ".....######.....",
          ".....######.....", ".....######.....", "....########....", "...##########...", "...##########...", "...##########...", "................", "................"],
}


def icons():
    for side, body, edge, detail in (("w", (244, 236, 214), (60, 44, 30), (150, 130, 100)), ("b", (52, 40, 36), (230, 214, 180), (120, 100, 90))):
        for k, rows in ICONS.items():
            img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
            filled = {(x, y) for y, row in enumerate(rows) for x, ch in enumerate(row) if ch in "#o"}
            for (x, y) in filled:
                img.putpixel((x, y), body + (255,) if rows[y][x] == "#" else detail + (255,))
            for (x, y) in list(filled):                                                      # an outline so both colours read on both squares
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    q = (x + dx, y + dy)
                    if 0 <= q[0] < 16 and 0 <= q[1] < 16 and q not in filled and img.getpixel(q)[3] == 0:
                        img.putpixel(q, edge + (255,))
            save(img, f"gui/chess/{side}{k}.png")


def table():
    m = BM("chess_table", {"wood": "minecraft:block/dark_oak_planks", "leg": "minecraft:block/stripped_dark_oak_log", "board": "pixelpirates:block/chess_board",
                           "edge": "minecraft:block/spruce_planks", "particle": "minecraft:block/dark_oak_planks"})
    m.box([0, 13, 0], [16, 15, 16], faces={"up": "#board", "down": "#wood", "north": "#edge", "south": "#edge", "east": "#edge", "west": "#edge"})
    for x, z in ((1, 1), (13, 1), (1, 13), (13, 13)):
        m.box([x, 0, z], [x + 2, 13, z + 2], all="#leg")
    m.box([1, 4, 2], [15, 5, 3], all="#wood"); m.box([1, 4, 13], [15, 5, 14], all="#wood")
    m.box([6.5, 10, -0.5], [9.5, 12, 0.5], all="#edge")                                       # a drawer pull on white's side
    m.write()
    write_blockstate("chess_table", {"variants": {f"facing={f}": ({"model": "pixelpirates:block/chess_table", "y": r} if r else
                                                                 {"model": "pixelpirates:block/chess_table"}) for f, r in ROT.items()}})
    item_model("chess_table", "pixelpirates:block/chess_table")
    return m


def pedestal():
    m = BM("giant_chess", {"stone": "minecraft:block/polished_andesite", "trim": "minecraft:block/quartz_block_bottom", "clock": "pixelpirates:block/chess_clock",
                           "wood": "minecraft:block/dark_oak_planks", "brass": "minecraft:block/gold_block", "particle": "minecraft:block/polished_andesite"})
    m.box([3, 0, 3], [13, 2, 13], all="#trim")
    m.box([4, 2, 4], [12, 10, 12], all="#stone")
    m.box([3, 10, 3], [13, 11, 13], all="#trim")
    m.box([3.5, 11, 6], [12.5, 14, 10], faces={"north": "#clock", "south": "#wood", "up": "#wood", "east": "#wood", "west": "#wood", "down": "#wood"})
    m.box([5, 14, 7.5], [6, 15, 8.5], all="#brass"); m.box([10, 14, 7.5], [11, 15, 8.5], all="#brass")
    m.write()
    write_blockstate("giant_chess", {"variants": {f"facing={f}": ({"model": "pixelpirates:block/giant_chess", "y": (r + 180) % 360} if (r + 180) % 360 else
                                                                 {"model": "pixelpirates:block/giant_chess"}) for f, r in ROT.items()}})
    item_model("giant_chess", "pixelpirates:block/giant_chess")
    return m


def league_board():
    """THE CHESS LEAGUE board (2026-10-05): two posts and a notice panel - a chequered header over the standings."""
    img = Image.new("RGBA", (32, 32), (226, 210, 168, 255))
    r = random.Random(7)
    for y in range(32):
        for x in range(32):
            c = img.getpixel((x, y))
            n = r.randint(-8, 6)
            img.putpixel((x, y), (c[0] + n, c[1] + n, c[2] + n, 255))
    for y in range(2, 8):                                                   # the header: a strip of chessboard
        for x in range(2, 30):
            img.putpixel((x, y), (30, 26, 24, 255) if ((x - 2) // 3 + (y - 2) // 3) % 2 else (236, 228, 206, 255))
    for i, y in enumerate(range(11, 30, 3)):                               # the standings: name ... rating
        ink = (150, 30, 30, 255) if i == 0 else (60, 44, 30, 255)
        for x in range(3, 3 + 12 + r.randint(0, 6)): img.putpixel((x, y), ink)
        for x in range(24, 29): img.putpixel((x, y), ink)
    for x in range(32):
        for y in (0, 31): img.putpixel((x, y), (84, 56, 32, 255))
    for y in range(32):
        for x in (0, 31): img.putpixel((x, y), (84, 56, 32, 255))
    save(img, "block/league_board.png")
    m = BM("league_board", {"post": "minecraft:block/dark_oak_log", "wood": "minecraft:block/dark_oak_planks",
                            "panel": "pixelpirates:block/league_board", "particle": "minecraft:block/dark_oak_planks"})
    m.box([1, 0, 7], [3, 16, 9], all="#post"); m.box([13, 0, 7], [15, 16, 9], all="#post")
    m.box([0, 5, 7.5], [16, 15, 8.5], faces={"north": "#panel", "south": "#wood", "east": "#wood", "west": "#wood", "up": "#wood", "down": "#wood"},
          uv={"north": [0, 0, 16, 16]})
    m.box([0, 15, 7], [16, 16, 9], all="#wood")                             # a little roof rail
    m.write()
    write_blockstate("league_board", {"variants": {f"facing={f}": ({"model": "pixelpirates:block/league_board", "y": r} if r else
                                                                  {"model": "pixelpirates:block/league_board"}) for f, r in ROT.items()}})
    item_model("league_board", "pixelpirates:block/league_board")
    return m


LANG = {"block.pixelpirates.chess_table": "Chess Table", "block.pixelpirates.giant_chess": "Giant Chess Set",
        "block.pixelpirates.league_board": "Chess League Board"}

if __name__ == "__main__":
    PREV.mkdir(parents=True, exist_ok=True)
    textures(); icons()
    table().preview(PREV / "chess_table.png", size=256)
    pedestal().preview(PREV / "giant_chess.png", size=256)
    league_board().preview(PREV / "league_board.png", size=256)
    p = RES / "lang/en_us.json"
    d = json.loads(p.read_text(encoding="utf-8")); d.update(LANG)
    p.write_bytes(json.dumps(d, indent=2, ensure_ascii=False).encode("utf-8"))
    sheet = Image.new("RGBA", (16 * 12 * 3, 48), (120, 90, 60, 255))
    for i, n in enumerate([s + k for s in "wb" for k in "pnbrqk"]):
        sheet.paste(Image.open(RES / f"textures/gui/chess/{n}.png").resize((48, 48), Image.NEAREST), (i * 48, 0), Image.open(RES / f"textures/gui/chess/{n}.png").resize((48, 48), Image.NEAREST))
    sheet.save(PREV / "icons.png")
    print("chess assets ok")
