"""THE CHAPEL - assets for the pews, bell rope, organ, and the chapel's furnishings (2026-10-01).

    python tools/gen_chapel_assets.py            # everything
    python tools/gen_chapel_assets.py sound|tex|gui|models|lang

sound : sounds/block/organ/diapason.ogg - a synthesized organ pipe at F#4 (the note-block pitch 1.0), merged into
        sounds.json as pixelpirates:block.organ.diapason (needs numpy + ffmpeg on PATH)
tex   : textures/block/organ_pipe, organ_keys, hymn_board, memorial_plaque, altar_cloth, font_water
gui   : textures/gui/organ.png (OrganScreen: panel 256x150; white keys 16x70 at (0|16|32,150); black keys 10x44 at (48|58|68,150))
models: chapel_pew (arms by LEFT/RIGHT), bell_rope, organ_console, organ_pipes (TOP), chapel_altar, altar_cross, wall_cross,
        candelabra, votive_rack, baptismal_font, hymn_board, memorial_plaque, votive_ship (+ blockstates, item models, previews)
"""
import json, math, random, subprocess, sys, tempfile, wave
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
from blockmodels import BM, RES, write_blockstate, item_model  # noqa: E402
from gen_tavern_assets import text, text_w, noise, draw_symbol  # noqa: E402

TEX = RES / "textures"
PREV = Path(__file__).parent / "previews/chapel"


def save(img, rel):
    p = TEX / rel
    p.parent.mkdir(parents=True, exist_ok=True)
    img.save(p)
    print("wrote", rel)


# ------------------------------------------------------------------------------------------------ the organ pipe
def sound():
    sr, dur, f0 = 44100, 1.9, 369.99
    t = np.arange(int(sr * dur)) / sr
    harm = [(1, 1.0), (2, 0.55), (3, 0.38), (4, 0.22), (5, 0.14), (6, 0.09), (8, 0.05)]
    y = np.zeros_like(t)
    for cents in (-2.0, 0.0, 2.5):                                # three ranks, gently out of tune = the organ's chorus
        f = f0 * 2 ** (cents / 1200)
        for k, a in harm:
            y += a * np.sin(2 * np.pi * f * k * t + k * 0.7)
    y *= 1 + 0.025 * np.sin(2 * np.pi * 5.2 * t)                 # a little wind in the pipes
    rng = np.random.default_rng(3)
    chiff = rng.standard_normal(len(t)) * np.exp(-t * 40) * 0.25  # the pipe's speech at the start
    y = y / np.max(np.abs(y)) + chiff
    env = np.minimum(1, t / 0.05) * np.where(t > dur - 0.35, np.maximum(0, (dur - t) / 0.35), 1)
    y = y * env
    y = (y / np.max(np.abs(y)) * 0.85 * 32767).astype(np.int16)
    out = RES / "sounds/block/organ/diapason.ogg"
    out.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as td:
        wav = Path(td) / "d.wav"
        with wave.open(str(wav), "wb") as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(sr); w.writeframes(y.tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(wav), "-c:a", "libvorbis", "-q:a", "5", str(out)], check=True)
    sj = RES / "sounds.json"
    d = json.loads(sj.read_text(encoding="utf-8"))
    d["block.organ.diapason"] = {"sounds": [{"name": "pixelpirates:block/organ/diapason", "attenuation_distance": 48}], "subtitle": "subtitles.pixelpirates.organ"}
    sj.write_bytes(json.dumps(d, indent=2).encode("utf-8"))
    print("wrote sounds/block/organ/diapason.ogg + sounds.json")


# ------------------------------------------------------------------------------------------------ textures
def tex():
    # organ pipe metal: tin with a gilt band, lit from the left
    im = Image.new("RGBA", (16, 16))
    for x in range(16):
        for y in range(16):
            v = 0.55 + 0.45 * math.sin((x % 4) / 4 * math.pi)
            c = (int(196 * v + 30), int(186 * v + 26), int(160 * v + 20))
            im.putpixel((x, y), c + (255,))
    for x in range(16):
        im.putpixel((x, 0), (226, 186, 80, 255)); im.putpixel((x, 15), (150, 110, 40, 255))
    save(im, "block/organ_pipe.png")
    # the keyboard (16x16 top view): white keys with black ones between
    im = Image.new("RGBA", (16, 16), (240, 236, 222, 255))
    d = ImageDraw.Draw(im)
    for x in range(0, 16, 2):
        d.line([(x, 0), (x, 15)], fill=(150, 140, 120, 255))
    for x in (1, 3, 7, 9, 11, 15):
        d.rectangle([x, 0, x + 1, 9], fill=(30, 26, 24, 255))
    save(im, "block/organ_keys.png")
    # hymn board: dark wood, gilt frame, three hymn numbers
    im = Image.new("RGBA", (32, 32))
    noise(im, (0, 0, 32, 32), (58, 36, 22), 4, 30)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 31, 31], outline=(30, 18, 10, 255)); d.rectangle([1, 1, 30, 30], outline=(214, 172, 70, 255))
    text(im, "HYMNS", 16 - text_w("HYMNS") // 2, 3, (240, 220, 170, 255))
    for i, n in enumerate(("12", "47", "103")):
        text(im, n, 16 - text_w(n) // 2, 11 + i * 7, (250, 250, 240, 255))
    save(im, "block/hymn_board.png")
    # memorial plaque: grey slate, an anchor, "LOST AT SEA"
    im = Image.new("RGBA", (32, 16))
    noise(im, (0, 0, 32, 16), (96, 100, 104), 5, 31)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, 31, 15], outline=(60, 62, 66, 255))
    an = draw_symbol("anchor", 10)
    px = an.load()
    for yy in range(10):
        for xx in range(10):
            px[xx, yy] = (230, 220, 190, 255) if px[xx, yy][3] > 60 else (0, 0, 0, 0)
    im.alpha_composite(an, (2, 3))
    text(im, "LOST", 13, 2, (236, 228, 200, 255)); text(im, "AT SEA", 13, 9, (236, 228, 200, 255))
    save(im, "block/memorial_plaque.png")
    # altar cloth: white linen, a gold border and a gold cross
    im = Image.new("RGBA", (16, 16))
    noise(im, (0, 0, 16, 16), (238, 236, 228), 3, 32)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 13, 15, 15], fill=(214, 172, 70, 255))
    d.rectangle([7, 3, 8, 11], fill=(214, 172, 70, 255)); d.rectangle([5, 5, 10, 6], fill=(214, 172, 70, 255))
    save(im, "block/altar_cloth.png")
    # font water: still blue
    im = Image.new("RGBA", (16, 16))
    noise(im, (0, 0, 16, 16), (70, 120, 200), 10, 33)
    save(im, "block/font_water.png")


def gui():
    im = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    noise(im, (0, 0, 256, 150), (54, 32, 20), 7, 40)
    d = ImageDraw.Draw(im)
    for y in range(0, 150, 6):
        d.line([(0, y), (255, y)], fill=(46, 28, 18, 255))
    d.rectangle([0, 0, 255, 149], outline=(20, 12, 8, 255))
    d.rectangle([2, 2, 253, 147], outline=(214, 172, 70, 255))
    d.rectangle([4, 4, 251, 145], outline=(110, 74, 34, 255))
    for x in range(20, 240, 12):                                      # a row of little gilt pipes along the top
        h = 4 + (x * 7) % 9
        d.rectangle([x, 4, x + 6, 4 + h], fill=(222, 196, 120, 255), outline=(150, 110, 40, 255))
    d.rectangle([12, 54, 243, 132], fill=(26, 16, 10, 255))           # the key bed
    # keys: white normal / hover / pressed; black normal / hover / pressed
    for k, (col, edge) in enumerate((((244, 240, 228), (150, 140, 120)), ((255, 248, 210), (214, 172, 70)), ((214, 196, 140), (150, 110, 40)))):
        x = k * 16
        d.rectangle([x, 150, x + 15, 219], fill=col + (255,), outline=edge + (255,))
        d.rectangle([x + 1, 216, x + 14, 218], fill=tuple(max(0, c - 40) for c in col) + (255,))
    for k, col in enumerate(((28, 24, 22), (70, 56, 40), (140, 100, 40))):
        x = 48 + k * 10
        d.rectangle([x, 150, x + 9, 193], fill=col + (255,), outline=(10, 8, 6, 255))
        d.line([(x + 2, 151), (x + 2, 190)], fill=tuple(min(255, c + 50) for c in col) + (255,))
    save(im, "gui/organ.png")


# ------------------------------------------------------------------------------------------------ models
SP, DO = "minecraft:block/spruce_planks", "minecraft:block/dark_oak_planks"
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}


def facing_states(name, models):
    v = {}
    for f, r in ROT.items():
        for extra, model in models.items():
            key = f"facing={f}" + ("," + extra if extra else "")
            v[key] = {"model": model, "y": r} if r else {"model": model}
    write_blockstate(name, {"variants": v})


def simple(name, m):
    m.write()
    facing_states(name, {"": f"pixelpirates:block/{name}"})
    item_model(name, f"pixelpirates:block/{name}")
    return m


def models():
    built = []
    # --- pews: seat, backrest, kneeler; end panels where LEFT / RIGHT (sitter's left = west on a north-facing pew)
    tx = {"wood": SP, "dark": DO, "cushion": "minecraft:block/red_wool", "particle": SP}
    for left in (True, False):
        for right in (True, False):
            n = f"chapel_pew_{'l' if left else ''}{'r' if right else ''}" if (left or right) else "chapel_pew_mid"
            m = BM(n, tx)
            m.box([0, 6, 3], [16, 7.5, 12], all="#wood")
            m.box([0, 7.5, 4], [16, 8, 11.5], all="#cushion")
            m.box([0, 7.5, 12], [16, 18, 13.5], all="#wood")
            m.box([0, 17, 11.5], [16, 18.5, 14], all="#dark")
            m.box([0, 1, 1], [16, 2, 2.5], all="#dark")
            if left:
                m.box([0, 0, 2], [1.5, 13, 14], all="#dark"); m.box([0, 13, 3], [1.5, 15, 13], all="#dark")
            if right:
                m.box([14.5, 0, 2], [16, 13, 14], all="#dark"); m.box([14.5, 13, 3], [16, 15, 13], all="#dark")
            if not left and not right:
                m.box([7, 0, 6], [9, 6, 10], all="#dark")
            m.write()
            built.append(m)
    v = {}
    for f, r in ROT.items():
        for left in (True, False):
            for right in (True, False):
                n = f"chapel_pew_{'l' if left else ''}{'r' if right else ''}" if (left or right) else "chapel_pew_mid"
                key = f"facing={f},left={'true' if left else 'false'},right={'true' if right else 'false'}"
                v[key] = {"model": f"pixelpirates:block/{n}", "y": r} if r else {"model": f"pixelpirates:block/{n}"}
    write_blockstate("chapel_pew", {"variants": v})
    item_model("chapel_pew", "pixelpirates:block/chapel_pew_lr")

    # --- bell rope: a rope down the block with a striped woollen sally to grip
    m = BM("bell_rope", {"rope": "pixelpirates:block/rope", "red": "minecraft:block/red_wool", "white": "minecraft:block/white_wool",
                         "particle": "pixelpirates:block/rope"}, ao=False)
    m.box([7.5, 0, 7.5], [8.5, 16, 8.5], all="#rope")
    for i, y in enumerate((5, 6.5, 8, 9.5)):
        m.box([6.5, y, 6.5], [9.5, y + 1.5, 9.5], all="#red" if i % 2 == 0 else "#white")
    m.box([7, 0, 7], [9, 1.5, 9], all="#red")
    built.append(simple("bell_rope", m))

    # --- organ console: cabinet, two manuals, stop knobs, a music stand, pedalboard
    m = BM("organ_console", {"wood": DO, "keys": "pixelpirates:block/organ_keys", "gold": "minecraft:block/gold_block",
                             "ivory": "minecraft:block/white_concrete", "red": "minecraft:block/red_wool", "particle": DO})
    m.box([0, 0, 8], [16, 13, 16], all="#wood")
    m.box([0, 8, 4], [16, 9, 8], faces={"up": "#keys", "north": "#wood", "south": "#wood", "east": "#wood", "west": "#wood", "down": "#wood"})
    m.box([0, 10, 6], [16, 11, 9], faces={"up": "#keys", "north": "#wood", "south": "#wood", "east": "#wood", "west": "#wood", "down": "#wood"})
    m.box([0, 7, 4], [16, 8, 9], all="#wood")
    m.box([1, 13, 11], [15, 18, 12], all="#wood")
    m.box([2, 14, 10.5], [14, 17, 11], all="#ivory")
    for x in (1, 2.5, 13, 14.5):
        m.box([x, 11.5, 8.5], [x + 1, 12.5, 9], all="#gold" if x < 8 else "#red")
    m.box([1, 0, 0], [15, 1, 4], all="#wood")
    for x in range(2, 15, 2):
        m.box([x, 1, 0.5], [x + 1, 1.5, 3.5], all="#ivory")
    built.append(simple("organ_console", m))

    # --- organ pipes: plain courses + a crown course (TOP) with tapered tips and mouths
    pt = {"pipe": "pixelpirates:block/organ_pipe", "wood": DO, "dark": "minecraft:block/black_concrete", "gold": "minecraft:block/gold_block",
          "particle": "pixelpirates:block/organ_pipe"}
    xs = [0.5, 3.6, 6.7, 9.8, 12.9]
    for top in (False, True):
        m = BM("organ_pipes_top" if top else "organ_pipes", pt)
        m.box([0, 0, 13], [16, 16 if not top else 6, 16], all="#wood")
        for i, x in enumerate(xs):
            h = 16 if not top else [10, 13, 15.5, 13, 10][i]
            m.box([x, 0, 9.5], [x + 2.6, h, 12.1], all="#pipe")
            if top:
                m.box([x + 0.5, h, 10], [x + 2.1, h + 1.2, 11.6], all="#pipe")
                m.box([x + 0.8, 2, 9.3], [x + 1.8, 4, 9.5], all="#dark")
        if top:
            m.box([0, 0, 12.5], [16, 1, 13], all="#gold")
        m.write()
        built.append(m)
    facing_states("organ_pipes", {"top=false": "pixelpirates:block/organ_pipes", "top=true": "pixelpirates:block/organ_pipes_top"})
    item_model("organ_pipes", "pixelpirates:block/organ_pipes_top")

    # --- the altar: a stone table with a linen cloth over it and a red runner
    m = BM("chapel_altar", {"stone": "minecraft:block/smooth_stone", "quartz": "minecraft:block/quartz_block_side", "cloth": "pixelpirates:block/altar_cloth",
                            "linen": "minecraft:block/white_wool", "red": "minecraft:block/red_wool", "particle": "minecraft:block/quartz_block_side"})
    m.box([0, 0, 2], [16, 2, 14], all="#stone")
    m.box([1, 2, 3], [15, 13, 13], all="#quartz")
    m.box([-0.2, 13, 1.8], [16.2, 15, 14.2], faces={"up": "#linen", "north": "#cloth", "south": "#linen", "east": "#linen", "west": "#linen", "down": "#linen"},
          uv={"north": [0, 0, 16, 16]})
    m.box([-0.1, 6, 1.9], [16.1, 13, 2.1], faces={"north": "#cloth"}, uv={"north": [0, 0, 16, 16]})
    m.box([6, 15, 1.7], [10, 15.2, 14.3], all="#red")
    built.append(simple("chapel_altar", m))

    # --- altar cross (gold, on a stone step) and wall cross (dark oak, gilt boss)
    m = BM("altar_cross", {"gold": "minecraft:block/gold_block", "stone": "minecraft:block/polished_andesite", "particle": "minecraft:block/gold_block"})
    m.box([5, 0, 6], [11, 2, 10], all="#stone")
    m.box([7, 2, 7], [9, 16, 9], all="#gold")
    m.box([4, 10, 7], [12, 12, 9], all="#gold")
    built.append(simple("altar_cross", m))
    m = BM("wall_cross", {"wood": DO, "gold": "minecraft:block/gold_block", "particle": DO})
    m.box([7, 1, 14.5], [9, 15, 16], all="#wood")
    m.box([3, 9, 14.5], [13, 11, 16], all="#wood")
    m.box([7.25, 9.25, 14], [8.75, 10.75, 14.5], all="#gold")
    built.append(simple("wall_cross", m))

    # --- candelabra (three lit candles) and the votive rack (tiers of small candles)
    ct = {"gold": "minecraft:block/gold_block", "candle": "minecraft:block/white_wool", "flame": "minecraft:block/shroomlight",
          "iron": "minecraft:block/anvil", "red": "minecraft:block/red_wool", "particle": "minecraft:block/gold_block"}
    m = BM("candelabra", ct, ao=False)
    m.box([5, 0, 5], [11, 1, 11], all="#gold")
    m.box([7.5, 1, 7.5], [8.5, 11, 8.5], all="#gold")
    m.box([3, 10, 7.5], [13, 11, 8.5], all="#gold")
    for x in (3, 7.25, 11.5):
        m.box([x, 11, 7.25], [x + 1.5, 14.5, 8.75], all="#candle")
        m.box([x + 0.5, 14.5, 7.75], [x + 1, 15.6, 8.25], all="#flame")
    built.append(simple("candelabra", m))
    m = BM("votive_rack", ct, ao=False)
    for t in range(3):
        m.box([0, t * 3.5, 4 + t * 2.5], [16, t * 3.5 + 1, 6.5 + t * 2.5], all="#iron")
        for k in range(5):
            x = 1.5 + k * 3
            m.box([x, t * 3.5 + 1, 4.5 + t * 2.5], [x + 1.2, t * 3.5 + 2.6, 5.7 + t * 2.5], all="#red" if (k + t) % 2 else "#candle")
            m.box([x + 0.4, t * 3.5 + 2.6, 4.9 + t * 2.5], [x + 0.8, t * 3.5 + 3.3, 5.3 + t * 2.5], all="#flame")
    m.box([0, 0, 11.5], [1, 9, 12.5], all="#iron"); m.box([15, 0, 11.5], [16, 9, 12.5], all="#iron")
    built.append(simple("votive_rack", m))

    # --- baptismal font: an octagonal-ish basin on a pedestal, still water inside
    m = BM("baptismal_font", {"stone": "minecraft:block/polished_diorite", "dark": "minecraft:block/polished_andesite",
                              "water": "pixelpirates:block/font_water", "particle": "minecraft:block/polished_diorite"})
    m.box([4, 0, 4], [12, 2, 12], all="#dark")
    m.box([6, 2, 6], [10, 8, 10], all="#stone")
    m.box([2, 8, 3], [14, 10, 13], all="#stone"); m.box([3, 8, 2], [13, 10, 14], all="#stone")
    for f, t in (([2, 10, 3], [3.5, 14, 13]), ([12.5, 10, 3], [14, 14, 13]), ([3.5, 10, 2], [12.5, 14, 3.5]), ([3.5, 10, 12.5], [12.5, 14, 14])):
        m.box(f, t, all="#stone")
    m.box([3.5, 10, 3.5], [12.5, 12.5, 12.5], faces={"up": "#water", "down": "#stone"})
    m.box([1.5, 14, 2.5], [14.5, 14.6, 3.5], all="#dark"); m.box([1.5, 14, 12.5], [14.5, 14.6, 13.5], all="#dark")
    built.append(simple("baptismal_font", m))

    # --- hymn board + memorial plaque (wall boards, front = north)
    m = BM("hymn_board", {"board": "pixelpirates:block/hymn_board", "frame": DO, "particle": DO})
    m.box([2, 0, 15], [14, 16, 16], faces={"north": "#board", "south": "#frame", "east": "#frame", "west": "#frame", "up": "#frame", "down": "#frame"},
          uv={"north": [0, 0, 16, 16]})
    built.append(simple("hymn_board", m))
    m = BM("memorial_plaque", {"slab": "pixelpirates:block/memorial_plaque", "edge": "minecraft:block/polished_andesite", "particle": "minecraft:block/polished_andesite"})
    m.box([1, 3, 15], [15, 13, 16], faces={"north": "#slab", "south": "#edge", "east": "#edge", "west": "#edge", "up": "#edge", "down": "#edge"},
          uv={"north": [0, 0, 16, 16]})
    built.append(simple("memorial_plaque", m))

    # --- votive ship: a little three-master hung from the ceiling on cords
    m = BM("votive_ship", {"hull": DO, "deck": SP, "sail": "minecraft:block/white_wool", "red": "minecraft:block/red_wool",
                           "cord": "pixelpirates:block/rope", "particle": DO})
    m.box([7.75, 13, 7.75], [8.25, 16, 8.25], all="#cord")
    m.box([4, 3, 6.5], [12, 5.5, 9.5], all="#hull"); m.box([2.5, 3.5, 7], [4, 5.5, 9], all="#hull"); m.box([12, 4, 7], [13.5, 6.5, 9], all="#hull")
    m.box([4, 5.5, 6.7], [12, 5.8, 9.3], all="#deck")
    for x, h in ((5.5, 11), (8, 13), (10.5, 10.5)):
        m.box([x - 0.25, 5.8, 7.75], [x + 0.25, h, 8.25], all="#hull")
        m.box([x - 1.5, 7, 7.95], [x + 1.5, h - 1, 8.05], all="#sail")
    m.box([1, 5, 7.85], [2.5, 5.4, 8.15], all="#hull")
    m.box([7.5, 12.6, 7.9], [8.5, 13, 8.1], all="#red")
    built.append(simple("votive_ship", m))

    PREV.mkdir(parents=True, exist_ok=True)
    for b in built:
        b.preview(PREV / f"{b.name}.png")
    print("models:", len(built))


LANG_E = {
    "block.pixelpirates.chapel_pew": "Pew", "block.pixelpirates.bell_rope": "Bell Rope", "block.pixelpirates.organ_console": "Organ Console",
    "block.pixelpirates.organ_pipes": "Organ Pipes", "block.pixelpirates.chapel_altar": "Altar", "block.pixelpirates.altar_cross": "Altar Cross",
    "block.pixelpirates.wall_cross": "Wall Cross", "block.pixelpirates.candelabra": "Candelabra", "block.pixelpirates.votive_rack": "Votive Candle Rack",
    "block.pixelpirates.baptismal_font": "Baptismal Font", "block.pixelpirates.hymn_board": "Hymn Board",
    "block.pixelpirates.memorial_plaque": "Sailors' Memorial Plaque", "block.pixelpirates.votive_ship": "Votive Ship",
    "subtitles.pixelpirates.organ": "Organ plays",
}


def lang():
    p = RES / "lang/en_us.json"
    d = json.loads(p.read_text(encoding="utf-8"))
    d.update(LANG_E)
    p.write_bytes(json.dumps(d, indent=2, ensure_ascii=False).encode("utf-8"))
    print("lang +", len(LANG_E))


if __name__ == "__main__":
    what = sys.argv[1:] or ["sound", "tex", "gui", "models", "lang"]
    if "sound" in what: sound()
    if "tex" in what: tex()
    if "gui" in what: gui()
    if "models" in what: models()
    if "lang" in what: lang()
