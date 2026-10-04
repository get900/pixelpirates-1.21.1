"""Z-fighting: two faces that point the same way on the same plane and overlap flicker in game (the
"glitchy texture" look). find_geo/fix_geo handle GeckoLib geo, find_elements/fix_elements vanilla block/item
models. The writers (gen_mob_assets.dump, armor/kit.write_armor, blockmodels.BM.write) call the fixers, so every
regenerated model comes out clean; tools/check_zfight.py runs them over the shipped files.

Two cubes can only fight when they sit in the same space at rest: the same chain of rotated bones (pivot +
rotation) and the same own rotation. Unrotated bones don't move cubes (geo origins are model-space), so cubes on
different unrotated bones are compared too - they fight in the rest pose.

The fix inflates the SMALLER cube (the detail: a belt, hair, an eye plane) by STEP so its faces sit just in front
of the bigger one's. Inflate leaves box UVs alone, so textures don't move. STEP is 0.05 px (~0.003 blocks): big
enough for the depth buffer at normal view distances, too small to see."""

STEP = 0.05
EPS = 1e-4


def _key_chain(bones):
    by = {b["name"]: b for b in bones}
    memo = {}

    def chain(name):
        if name in memo:
            return memo[name]
        b = by.get(name)
        if b is None:
            return ()
        up = chain(b["parent"]) if b.get("parent") else ()
        r = b.get("rotation")
        if r and any(abs(v) > EPS for v in r):
            up = up + ((tuple(b.get("pivot", (0, 0, 0))), tuple(r)),)
        memo[name] = up
        return up
    return chain


def _box(c):
    i = c.get("inflate", 0)
    o, s = c["origin"], c["size"]
    return [(o[k] - i, o[k] + s[k] + i) for k in range(3)]


def _vol(b):
    return (b[0][1] - b[0][0]) * (b[1][1] - b[1][0]) * (b[2][1] - b[2][0])


def _fights(A, C):
    """The first axis/plane on which boxes A and C have same-facing overlapping faces, else None."""
    for ax in range(3):
        others = [k for k in range(3) if k != ax]
        if not all(min(A[k][1], C[k][1]) - max(A[k][0], C[k][0]) > EPS for k in others):
            continue
        for side in (0, 1):                                    # 0 = min face, 1 = max face
            if abs(A[ax][side] - C[ax][side]) < EPS:
                return ax, A[ax][side]
    return None


def _geo_cubes(geo):
    out = []
    for g in geo["minecraft:geometry"]:
        bones = g.get("bones", [])
        chain = _key_chain(bones)
        for b in bones:
            for c in b.get("cubes", []):
                r = c.get("rotation")
                own = (tuple(c.get("pivot", (0, 0, 0))), tuple(r)) if r and any(abs(v) > EPS for v in r) else None
                out.append((b["name"], c, (chain(b["name"]), own)))
    return out


def _pairs(items, box_of):
    """Yield (i, j, axis, plane) for every fighting pair; items are (label, obj, key)."""
    groups = {}
    for n, it in enumerate(items):
        groups.setdefault(repr(it[2]), []).append(n)
    for idx in groups.values():
        boxes = {n: box_of(items[n][1]) for n in idx}
        for a in range(len(idx)):
            for b in range(a + 1, len(idx)):
                i, j = idx[a], idx[b]
                f = _fights(boxes[i], boxes[j])
                if f:
                    yield i, j, f[0], f[1]


def find_geo(geo):
    cs = _geo_cubes(geo)
    return [(cs[i][0], cs[j][0], "xyz"[ax], p) for i, j, ax, p in _pairs(cs, _box)]


def fix_geo(geo, limit=400):
    """Inflate the smaller cube of a fighting pair, one at a time (nudging several at once can land two of a
    stack on the same plane again), until none are left. Returns the number of nudges."""
    for n in range(limit):
        cs = _geo_cubes(geo)
        pair = next(_pairs(cs, _box), None)
        if pair is None:
            return n
        i, j = pair[0], pair[1]
        k = j if _vol(_box(cs[j][1])) <= _vol(_box(cs[i][1])) else i   # equal: the later (drawn-on-top) one
        c = cs[k][1]
        c["inflate"] = round(c.get("inflate", 0) + STEP, 4)
    return limit


# ---------------------------------------------------------------- vanilla block / item models
def _ebox(e):
    return [(min(e["from"][k], e["to"][k]), max(e["from"][k], e["to"][k])) for k in range(3)]


def _ekey(e):
    r = e.get("rotation")
    if not r or abs(r.get("angle", 0)) < EPS:
        return None
    return (r.get("axis"), r.get("angle"), tuple(r.get("origin", (8, 8, 8))))


def _face_on(e, ax, side):
    return {(0, 0): "west", (0, 1): "east", (1, 0): "down", (1, 1): "up",
            (2, 0): "north", (2, 1): "south"}[(ax, side)] in e.get("faces", {})


def _epairs(els):
    items = [(n, e, _ekey(e)) for n, e in enumerate(els)]
    for i, j, ax, p in _pairs(items, _ebox):
        A, C = _ebox(els[i]), _ebox(els[j])
        side = 0 if abs(A[ax][0] - p) < EPS and abs(C[ax][0] - p) < EPS else 1
        if _face_on(els[i], ax, side) and _face_on(els[j], ax, side):   # a face left out can't fight
            yield i, j, ax, p


def find_elements(model):
    els = model.get("elements", [])
    return [(i, j, "xyz"[ax], p) for i, j, ax, p in _epairs(els)]


def fix_elements(model, limit=400):
    """Grow the smaller element of a fighting pair by STEP/2 px a side, one at a time (block coords allow -16..32)."""
    els = model.get("elements", [])
    d = STEP / 2
    for n in range(limit):
        pair = next(_epairs(els), None)
        if pair is None:
            return n
        i, j = pair[0], pair[1]
        k = j if _vol(_ebox(els[j])) <= _vol(_ebox(els[i])) else i
        b = _ebox(els[k])
        els[k]["from"] = [round(max(-16, b[a][0] - d), 4) for a in range(3)]
        els[k]["to"] = [round(min(32, b[a][1] + d), 4) for a in range(3)]
    return limit
