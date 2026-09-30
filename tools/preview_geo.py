"""GeckoLib-exact software preview of a generated mob (tools/preview_geo.py).

Mirrors GeckoLib 4.4.9 (BakedModelFactory / RenderUtils / BakedAnimationsAdapter):
  * bake: every x (cube origin, bone pivot, cube pivot) is negated  -> "game space"
  * rotation (bone, cube, keyframes): radians(-rx), radians(-ry), radians(rz), applied as Rz * Ry * Rx
  * keyframe position: translate(-px, py, pz)
  * bone matrix = T(pos) T(pivot) R S T(-pivot), parent first
The model faces -Z in game space; the camera sits in front of it (at -Z) looking +Z, so the
character's right hand (+X game = -X in the geo file) appears on the viewer's LEFT.

usage: preview2.py <mob> out.png [anim:t ...] [tex=<name>] [yaw=<deg>] [pitch=<deg>] [size=<px>]
"""
import json, math, sys
import numpy as np
from PIL import Image, ImageDraw

import pathlib
A = str(pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/pixelpirates")


def Rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def Ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def Rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def geck_rot(r):
    """Bedrock degrees -> GeckoLib game-space rotation matrix."""
    x, y, z = math.radians(-r[0]), math.radians(-r[1]), math.radians(r[2])
    return Rz(z) @ Ry(y) @ Rx(x)


def sample(ch, t):
    ks = sorted((float(k), v) for k, v in ch.items())
    if t <= ks[0][0]: return np.array(ks[0][1], float)
    for (t0, v0), (t1, v1) in zip(ks, ks[1:]):
        if t0 <= t <= t1:
            f = (t - t0) / (t1 - t0) if t1 > t0 else 0
            return np.array(v0, float) + (np.array(v1, float) - np.array(v0, float)) * f
    return np.array(ks[-1][1], float)


def T(v):
    m = np.eye(4); m[:3, 3] = v
    return m


def M3(r, s=(1, 1, 1)):
    m = np.eye(4); m[:3, :3] = r @ np.diag(s)
    return m


def render(mob, anim=None, t=0.0, tex_name=None, yaw=30, pitch=15, size=300, bg=(46, 52, 64),
           geo_file=None, tex_file=None, only=None, solids=(), pad=20):
    """geo_file/tex_file: explicit paths (armor); only: draw just these bones and their children;
    solids: extra flat-coloured boxes [(origin, size, rgb), ...] in geo space (a mannequin under armor)."""
    geo = json.load(open(geo_file or f"{A}/geo/{mob}.geo.json"))["minecraft:geometry"][0]
    tex = Image.open(tex_file or f"{A}/textures/entity/{tex_name or mob}.png").convert("RGBA")
    tw, th = geo["description"]["texture_width"], geo["description"]["texture_height"]
    sx, sy = tex.width / tw, tex.height / th
    tp = tex.load()
    try:
        anims = json.load(open(f"{A}/animations/{mob}.animation.json"))["animations"]
    except FileNotFoundError:
        anims = {}
    ab = anims[anim]["bones"] if anim else {}
    bones = {b["name"]: b for b in geo["bones"]}
    world = {}

    def xf(name):
        if name in world: return world[name]
        b = bones[name]
        piv = np.array(b["pivot"], float) * np.array([-1, 1, 1])
        r = np.array(b.get("rotation", [0, 0, 0]), float)
        pos = np.zeros(3); scl = np.ones(3)
        if name in ab:
            if "rotation" in ab[name]: r = r + sample(ab[name]["rotation"], t)
            if "position" in ab[name]: pos = sample(ab[name]["position"], t) * np.array([-1, 1, 1])
            if "scale" in ab[name]: scl = sample(ab[name]["scale"], t)
        L = T(pos) @ T(piv) @ M3(geck_rot(r), scl) @ T(-piv)
        W = (xf(b["parent"]) @ L) if "parent" in b else L
        world[name] = W
        return W

    cam = M3(Rx(math.radians(-pitch)) @ Ry(math.radians(-yaw)))
    polys = []

    def kept(name):
        while True:
            if name in only: return True
            name = bones[name].get("parent")
            if name is None: return False

    for (ox, oy, oz), (w, h, d), rgb in solids:
        x0, x1 = -(ox + w), -ox
        corners = {"north": [(x0, oy + h, oz), (x1, oy + h, oz), (x1, oy, oz), (x0, oy, oz)],
                   "south": [(x0, oy + h, oz + d), (x1, oy + h, oz + d), (x1, oy, oz + d), (x0, oy, oz + d)],
                   "east": [(x1, oy + h, oz), (x1, oy + h, oz + d), (x1, oy, oz + d), (x1, oy, oz)],
                   "west": [(x0, oy + h, oz), (x0, oy + h, oz + d), (x0, oy, oz + d), (x0, oy, oz)],
                   "up": [(x0, oy + h, oz), (x1, oy + h, oz), (x1, oy + h, oz + d), (x0, oy + h, oz + d)],
                   "down": [(x0, oy, oz), (x1, oy, oz), (x1, oy, oz + d), (x0, oy, oz + d)]}
        shade = {"north": 1.0, "south": 0.7, "east": 0.8, "west": 0.8, "up": 1.1, "down": 0.6}
        for f, cs in corners.items():                     # split into unit cells so the depth sort holds up
            a, b_, c_, d_ = [np.array(c, float) for c in cs]
            nu = max(1, int(round(np.linalg.norm(b_ - a)))); nv = max(1, int(round(np.linalg.norm(d_ - a))))
            for i in range(nu):
                for j in range(nv):
                    quad = [a + (b_ - a) * (i / nu) + (d_ - a) * (j / nv), a + (b_ - a) * ((i + 1) / nu) + (d_ - a) * (j / nv),
                            a + (b_ - a) * ((i + 1) / nu) + (d_ - a) * ((j + 1) / nv), a + (b_ - a) * (i / nu) + (d_ - a) * ((j + 1) / nv)]
                    pts = []
                    for c in quad:
                        q = cam @ np.array([*c, 1.0]); pts.append((-q[0], q[1], q[2]))
                    polys.append((sum(q[2] for q in pts) / 4, pts, tuple(min(255, int(v * shade[f])) for v in rgb)))
    for b in geo["bones"]:
        if only and not kept(b["name"]): continue
        W = xf(b["name"])
        for c in b.get("cubes", []):
            (ox, oy, oz), (w, h, d) = c["origin"], c["size"]
            u, v = c["uv"]
            inf = c.get("inflate", 0)
            # game-space box (x negated at bake, like GeckoLib)
            gx0 = -(ox + w)
            x0, y0, z0, x1, y1, z1 = gx0 - inf, oy - inf, oz - inf, gx0 + w + inf, oy + h + inf, oz + d + inf
            CM = np.eye(4)
            if "rotation" in c:
                cp = np.array(c.get("pivot", [0, 0, 0]), float) * np.array([-1, 1, 1])
                CM = T(cp) @ M3(geck_rot(c["rotation"])) @ T(-cp)
            Wc = cam @ W @ CM
            # GeckoLib box UV, decoded from BakedModelFactory.buildQuad + VertexSet.quadX + GeoQuad.build
            # (non-mirrored: vertex0 gets the rect's RIGHT u).  s = 0..1 across the rect from its left
            # edge, q = 0..1 down from its top edge.
            faces = {
                "north": ((u + d, v + d, w, h), lambda s, q: (x1 - s * (x1 - x0), y1 - q * (y1 - y0), z0)),
                "south": ((u + 2 * d + w, v + d, w, h), lambda s, q: (x0 + s * (x1 - x0), y1 - q * (y1 - y0), z1)),
                "east": ((u, v + d, d, h), lambda s, q: (x1, y1 - q * (y1 - y0), z1 - s * (z1 - z0))),
                "west": ((u + d + w, v + d, d, h), lambda s, q: (x0, y1 - q * (y1 - y0), z0 + s * (z1 - z0))),
                "up": ((u + d, v, w, d), lambda s, q: (x1 - s * (x1 - x0), y1, z1 - q * (z1 - z0))),
                "down": ((u + d + w, v, w, d), lambda s, q: (x1 - s * (x1 - x0), y0, z1 - q * (z1 - z0))),
            }
            for fname, ((fu, fv, fw, fh), fn) in faces.items():
                if fw == 0 or fh == 0: continue
                for i in range(fw):
                    for j in range(fh):
                        px_, py_ = int((fu + i) * sx), int((fv + j) * sy)
                        col = tp[px_, py_] if 0 <= px_ < tex.width and 0 <= py_ < tex.height else (255, 0, 255, 255)
                        if col[3] == 0: continue
                        pts = []
                        for (s, q) in ((i / fw, j / fh), ((i + 1) / fw, j / fh), ((i + 1) / fw, (j + 1) / fh), (i / fw, (j + 1) / fh)):
                            p = Wc @ np.array([*fn(s, q), 1.0])
                            pts.append((-p[0], p[1], p[2]))      # viewer looks +Z from -Z: screen right = -X
                        depth = sum(p[2] for p in pts) / 4
                        polys.append((depth, pts, col[:3]))
    polys.sort(key=lambda p: -p[0])
    allp = [q for _, pts, _ in polys for q in pts]
    minx, maxx = min(p[0] for p in allp), max(p[0] for p in allp)
    miny, maxy = min(p[1] for p in allp), max(p[1] for p in allp)
    sc = (size - pad) / max(maxx - minx, maxy - miny, 1)
    ox_ = (size - (maxx - minx) * sc) / 2
    oy_ = (size - (maxy - miny) * sc) / 2
    img = Image.new("RGB", (size, size), bg)
    dr = ImageDraw.Draw(img)
    for _, pts, col in polys:
        dr.polygon([((p[0] - minx) * sc + ox_, size - oy_ - (p[1] - miny) * sc) for p in pts], fill=col)
    return img


def sheet(mob, out, specs, **kw):
    frames = []
    tex = None
    for s in specs:
        if s.startswith("tex="): tex = s[4:]; continue
        a, t = s.split(":")
        frames.append(render(mob, None if a == "-" else a, float(t), tex, **kw))
    size = kw.get("size", 300)
    img = Image.new("RGB", (size * len(frames), size))
    for i, f in enumerate(frames): img.paste(f, (size * i, 0))
    img.save(out)


if __name__ == "__main__":
    mob, out = sys.argv[1], sys.argv[2]
    kw, specs = {}, []
    for a in sys.argv[3:]:
        k, _, v = a.partition("=")
        if k in ("yaw", "pitch", "size") and v:
            kw[k] = int(v)
        else:
            specs.append(a)
    sheet(mob, out, specs or ["-:0"], **kw)
