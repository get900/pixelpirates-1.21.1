"""Build the 3D armor sets (tools/armor/sets.py): geo + texture (+ glowmask) + empty animation per set, a
preview sheet per set (tools/previews/armor/<set>.png, on a grey mannequin) and the four inventory icons
(textures/item/<item>_<piece>.png, 32x32 renders of each piece).
    python tools/gen_armor_models.py [set ...]        (needs pillow + numpy)"""
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "armor"))
sys.path.insert(0, str(HERE))
from sets import BUILDERS          # noqa: E402
from kit import ASSETS              # noqa: E402
import preview_geo                  # noqa: E402
from PIL import Image               # noqa: E402

ITEM_PREFIX = {"pirate_armor": "pirate"}          # the material's items are named pirate_helmet etc.
PIECES = {"helmet": ["armorHead"], "chestplate": ["armorBody", "armorRightArm", "armorLeftArm"],
          "leggings": ["armorRightLeg", "armorLeftLeg"], "boots": ["armorRightBoot", "armorLeftBoot"]}
SKIN = (170, 140, 115)
MANNEQUIN = [([-4, 24, -4], [8, 8, 8], SKIN), ([-4, 12, -2], [8, 12, 4], (90, 110, 150)),
             ([-8, 12, -2], [4, 12, 4], SKIN), ([4, 12, -2], [4, 12, 4], SKIN),
             ([-4, 0, -2], [4, 12, 4], (60, 60, 110)), ([0, 0, -2], [4, 12, 4], (60, 60, 110))]
KEY = (255, 0, 255)


def paths(name):
    return str(ASSETS / f"geo/armor/{name}.geo.json"), str(ASSETS / f"textures/armor/{name}.png")


def preview(name):
    g, t = paths(name)
    out = HERE / "previews/armor"; out.mkdir(parents=True, exist_ok=True)
    views = [(25, 10), (155, 10), (90, 5), (-25, 25)]
    frames = [preview_geo.render(None, geo_file=g, tex_file=t, yaw=y, pitch=p, size=360, solids=MANNEQUIN) for y, p in views]
    sheet = Image.new("RGB", (360 * len(frames), 360))
    for i, f in enumerate(frames):
        sheet.paste(f, (360 * i, 0))
    sheet.save(out / f"{name}.png")


def icons(name):
    g, t = paths(name)
    prefix = ITEM_PREFIX.get(name, name)
    def shot(bones, yaw, pitch):
        im = preview_geo.render(None, geo_file=g, tex_file=t, yaw=yaw, pitch=pitch, size=256, only=set(bones), bg=KEY, pad=4).convert("RGBA")
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                r, gg, b, _ = px[x, y]
                if abs(r - 255) + gg + abs(b - 255) < 40:
                    px[x, y] = (0, 0, 0, 0)
        return im.crop(im.getbbox())

    for piece, bones in PIECES.items():
        if piece in ("leggings", "boots"):                           # the two legs apart, like vanilla's icons
            r, l = shot([bones[0]], 15, 8), shot([bones[1]], 15, 8)
            gap = max(2, r.width // 8)
            img = Image.new("RGBA", (r.width + l.width + gap, max(r.height, l.height)), (0, 0, 0, 0))
            img.paste(l, (0, 0), l); img.paste(r, (l.width + gap, 0), r)
        else:
            img = shot(bones, 30, 18)
        s = max(img.width, img.height)
        sq = Image.new("RGBA", (s, s), (0, 0, 0, 0)); sq.paste(img, ((s - img.width) // 2, (s - img.height) // 2))
        small = sq.resize((30, 30), Image.LANCZOS)
        icon = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); icon.paste(small, (1, 1))
        ip = icon.load()
        for y in range(32):                                          # crisp alpha + a dark 1-px outline
            for x in range(32):
                r, gg, b, a = ip[x, y]
                ip[x, y] = (r, gg, b, 255) if a > 110 else (0, 0, 0, 0)
        out = icon.copy(); op = out.load()
        for y in range(32):
            for x in range(32):
                if ip[x, y][3] == 0 and any(0 <= x + dx < 32 and 0 <= y + dy < 32 and ip[x + dx, y + dy][3] for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                    op[x, y] = (18, 14, 12, 255)
        out.save(ASSETS / f"textures/item/{prefix}_{piece}.png")


def main(names):
    for n in names or BUILDERS:
        m = BUILDERS[n]()
        tw, th = m.write_armor()
        preview(n)
        icons(n)
        print(f"{n}: {len(m.cubes)} cubes, texture {tw}x{th}")


if __name__ == "__main__":
    main(sys.argv[1:])
