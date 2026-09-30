"""Shaped block models authored in Python -> Minecraft block model JSON (+ blockstates) + an isometric preview PNG.

    from blockmodels import BM, blockstate_facing, preview
    m = BM("rum_still", {"copper": "minecraft:block/cut_copper", "particle": "minecraft:block/cut_copper"})
    m.box([2, 0, 2], [14, 10, 14], all="#copper")
    m.write(); m.preview("tools/previews/rum_still.png")

Faces without an explicit uv get Minecraft's automatic uv (taken from the element's coordinates), and the preview
uses exactly the same rule. Element rotation: one axis, angle in {-45,-22.5,0,22.5,45} (the vanilla limit).
Textures are read from src/main/resources (pixelpirates:) or the 1.20.1 client jar (minecraft:).
"""
from __future__ import annotations

import io
import json
import math
import zipfile
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "src/main/resources/assets/pixelpirates"
JAR = Path.home() / ".gradle/caches/fabric-loom/1.20.1/minecraft-client.jar"
_zip = None
_tex_cache: dict[str, Image.Image] = {}


def texture(ref: str) -> Image.Image:
    global _zip
    if ref in _tex_cache:
        return _tex_cache[ref]
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    if ns == "pixelpirates":
        p = RES / "textures" / f"{path}.png"
        img = Image.open(p).convert("RGBA") if p.exists() else Image.new("RGBA", (16, 16), (255, 0, 255, 255))
    else:
        if _zip is None:
            _zip = zipfile.ZipFile(JAR)
        try:
            img = Image.open(io.BytesIO(_zip.read(f"assets/minecraft/textures/{path}.png"))).convert("RGBA")
        except KeyError:
            img = Image.new("RGBA", (16, 16), (255, 0, 255, 255))
    if img.height > img.width:                       # animated strip: first frame
        img = img.crop((0, 0, img.width, img.width))
    _tex_cache[ref] = img
    return img


FACES = ("north", "south", "east", "west", "up", "down")


def auto_uv(face, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {"north": [16 - x2, 16 - y2, 16 - x1, 16 - y1], "south": [x1, 16 - y2, x2, 16 - y1],
            "east": [16 - z2, 16 - y2, 16 - z1, 16 - y1], "west": [z1, 16 - y2, z2, 16 - y1],
            "up": [x1, z1, x2, z2], "down": [x1, 16 - z2, x2, 16 - z1]}[face]


class BM:
    def __init__(self, name: str, textures: dict, parent: str | None = None, ao: bool = True):
        self.name, self.textures, self.parent, self.ao = name, dict(textures), parent, ao
        self.elements = []

    def box(self, f, t, all=None, faces=None, rot=None, uv=None, cull=False, shade=True, skip=(), uvrot=None):
        """faces: {face: '#tex'}; all: '#tex' for every face; skip: faces to leave out; uv: {face: [u1,v1,u2,v2]};
        rot: (axis, angle, origin)."""
        fc = {}
        for face in FACES:
            if face in skip:
                continue
            tex = (faces or {}).get(face, all)
            if tex is None:
                continue
            d = {"texture": tex}
            if uv and face in uv:
                d["uv"] = uv[face]
            else:                                              # auto uv outside 0..16 (parts above y16) -> wrap it back in
                au = auto_uv(face, f, t)
                if min(au) < 0 or max(au) > 16:
                    u1, v1, u2, v2 = au
                    while v1 < 0: v1, v2 = v1 + 16, v2 + 16
                    while u1 < 0: u1, u2 = u1 + 16, u2 + 16
                    d["uv"] = [max(0, min(16, u1)), max(0, min(16, v1)), max(0, min(16, u2)), max(0, min(16, v2))]
            if uvrot and face in uvrot:
                d["rotation"] = uvrot[face]
            if cull:
                d["cullface"] = face
            fc[face] = d
        e = {"from": list(f), "to": list(t), "faces": fc}
        if rot:
            axis, angle, origin = rot
            e["rotation"] = {"origin": list(origin), "axis": axis, "angle": angle}
        if not shade:
            e["shade"] = False
        self.elements.append(e)
        return self

    def json(self):
        j = {}
        if self.parent:
            j["parent"] = self.parent
        if not self.ao:
            j["ambientocclusion"] = False
        j["textures"] = self.textures
        j["elements"] = self.elements
        return j

    def write(self, folder="block"):
        p = RES / "models" / folder / f"{self.name}.json"
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_bytes(json.dumps(self.json(), indent=1).encode("utf-8"))
        return p

    # ---------------------------------------------------------------- preview
    def preview(self, out, size=320, yaw=-35.0, pitch=30.0, extra=()):
        preview([self] + list(extra), out, size, yaw, pitch)


def resolve_tex(model: BM, ref: str) -> str:
    seen = 0
    while ref.startswith("#") and seen < 8:
        ref = model.textures.get(ref[1:], "minecraft:block/missing")
        seen += 1
    return ref


def rotate(p, rot):
    if not rot:
        return p
    axis, angle, o = rot["axis"], math.radians(rot["angle"]), rot["origin"]
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(angle), math.sin(angle)
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def face_corners(face, f, t):
    """4 corners (in texture order: top-left, top-right, bottom-right, bottom-left as seen from outside)."""
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {"north": [(x2, y2, z1), (x1, y2, z1), (x1, y1, z1), (x2, y1, z1)],
            "south": [(x1, y2, z2), (x2, y2, z2), (x2, y1, z2), (x1, y1, z2)],
            "east": [(x2, y2, z2), (x2, y2, z1), (x2, y1, z1), (x2, y1, z2)],
            "west": [(x1, y2, z1), (x1, y2, z2), (x1, y1, z2), (x1, y1, z1)],
            "up": [(x1, y2, z1), (x2, y2, z1), (x2, y2, z2), (x1, y2, z2)],
            "down": [(x1, y1, z2), (x2, y1, z2), (x2, y1, z1), (x1, y1, z1)]}[face]


SHADE = {"up": 1.0, "down": 0.5, "north": 0.8, "south": 0.8, "east": 0.6, "west": 0.6}
NORMAL = {"up": (0, 1, 0), "down": (0, -1, 0), "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0)}


def preview(models, out, size=320, yaw=-35.0, pitch=30.0):
    """Render one or more models (each may carry .offset = (x,y,z) in blocks) isometrically."""
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))

    def view(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x, z = x * cy - z * sy, x * sy + z * cy
        x = -x                                            # screen right = the viewer's right (no mirror)
        y, z = y * cp + z * sp, -y * sp + z * cp
        return x, y, z

    quads = []
    for m in models:
        off = getattr(m, "offset", (0, 0, 0))
        for e in m.elements:
            f = e["from"]
            t = e["to"]
            rot = e.get("rotation")
            for face, d in e["faces"].items():
                tex = texture(resolve_tex(m, d["texture"]))
                u1, v1, u2, v2 = d.get("uv", auto_uv(face, f, t))
                corners = [rotate(c, rot) for c in face_corners(face, f, t)]
                corners = [(c[0] + off[0] * 16, c[1] + off[1] * 16, c[2] + off[2] * 16) for c in corners]
                n = NORMAL[face]
                if rot:
                    n = tuple(a - b for a, b in zip(rotate(n, {"axis": rot["axis"], "angle": rot["angle"], "origin": [0, 0, 0]}), (0, 0, 0)))
                vn = view((n[0] + 8, n[1] + 8, n[2] + 8))
                if vn[2] > 0.01:                          # facing away (camera looks down +z in view space)
                    continue
                quads.append((face, corners, tex, (u1, v1, u2, v2), SHADE[face] if e.get("shade", True) else 1.0, d.get("rotation", 0)))
    # draw texel by texel, far to near
    polys = []
    for face, c, tex, (u1, v1, u2, v2), sh, urot in quads:
        tw, th = tex.size
        su, sv = tw / 16.0, th / 16.0
        # the uv rectangle as its own image (flips for reversed uvs), turned by the face's uv rotation
        sub = tex.crop((int(min(u1, u2) * su), int(min(v1, v2) * sv), max(int(min(u1, u2) * su) + 1, int(max(u1, u2) * su)),
                        max(int(min(v1, v2) * sv) + 1, int(max(v1, v2) * sv))))
        if u2 < u1: sub = sub.transpose(Image.FLIP_LEFT_RIGHT)
        if v2 < v1: sub = sub.transpose(Image.FLIP_TOP_BOTTOM)
        if urot: sub = sub.rotate(-urot, expand=True)
        tex, u1, v1 = sub, 0, 0
        u2, v2 = sub.width / su, sub.height / sv
        tw, th = tex.size
        edge_u = abs(c[1][0] - c[0][0]) + abs(c[1][1] - c[0][1]) + abs(c[1][2] - c[0][2])
        edge_v = abs(c[3][0] - c[0][0]) + abs(c[3][1] - c[0][1]) + abs(c[3][2] - c[0][2])
        nu = max(1, int(round(edge_u)))
        nv = max(1, int(round(edge_v)))
        for i in range(nu):
            for j in range(nv):
                a0, a1, b0, b1 = i / nu, (i + 1) / nu, j / nv, (j + 1) / nv

                def P(a, b):
                    top = [c[0][k] + (c[1][k] - c[0][k]) * a for k in range(3)]
                    bot = [c[3][k] + (c[2][k] - c[3][k]) * a for k in range(3)]
                    return [top[k] + (bot[k] - top[k]) * b for k in range(3)]
                pts = [view(P(a0, b0)), view(P(a1, b0)), view(P(a1, b1)), view(P(a0, b1))]
                tu = u1 + (u2 - u1) * (i + 0.5) / nu
                tv = v1 + (v2 - v1) * (j + 0.5) / nv
                px = tex.getpixel((min(tw - 1, max(0, int(tu * su))), min(th - 1, max(0, int(tv * sv)))))
                if px[3] < 128:
                    continue
                col = tuple(int(ch * sh) for ch in px[:3])
                depth = sum(p[2] for p in pts) / 4
                polys.append((depth, pts, col))
    polys.sort(key=lambda q: -q[0])
    allx = [p[0] for _, pts, _ in polys for p in pts] or [0]
    ally = [p[1] for _, pts, _ in polys for p in pts] or [0]
    span = max(max(allx) - min(allx), max(ally) - min(ally), 1)
    k = size * 0.86 / span
    ox = size / 2 - (max(allx) + min(allx)) / 2 * k
    oy = size / 2 + (max(ally) + min(ally)) / 2 * k
    img = Image.new("RGBA", (size, size), (44, 48, 58, 255))
    dr = ImageDraw.Draw(img)
    for _, pts, col in polys:
        dr.polygon([(ox + p[0] * k, oy - p[1] * k) for p in pts], fill=col)
    Path(out).parent.mkdir(parents=True, exist_ok=True)
    img.save(out)


# ---------------------------------------------------------------- blockstates
def write_blockstate(name, obj):
    p = RES / "blockstates" / f"{name}.json"
    p.write_bytes(json.dumps(obj, indent=1).encode("utf-8"))


def blockstate_facing(name, model=None, extra: dict | None = None, south_default=False):
    """Horizontal FACING variants (model authored facing NORTH). extra: {"prop=val": model-suffix} combos not supported;
    use variants_facing for multi-property blocks."""
    model = model or f"pixelpirates:block/{name}"
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    write_blockstate(name, {"variants": {f"facing={f}": ({"model": model, "y": r} if r else {"model": model}) for f, r in rot.items()}})


def variants_facing(name, props: dict[str, str]):
    """props: {"lit=true": "pixelpirates:block/x_lit", ...} combined with facing."""
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    v = {}
    for f, r in rot.items():
        for pv, model in props.items():
            key = f"facing={f}" + ("," + pv if pv else "")
            v[key] = {"model": model, "y": r} if r else {"model": model}
    write_blockstate(name, {"variants": v})


def item_model(name, parent):
    p = RES / "models/item" / f"{name}.json"
    p.write_bytes(json.dumps({"parent": parent}, indent=1).encode("utf-8"))
