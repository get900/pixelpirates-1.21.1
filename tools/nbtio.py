"""Minimal Java-edition NBT read/write (gzip, big-endian) for the ship blueprints (world/ShipSchematic: root compound,
"blocks" = list of {x, y, z, state}). Values keep their tag type: ints/strings/compounds/lists round-trip exactly.

    from nbtio import load, save
    root = load(path); ...; save(path, root)
"""
import gzip, struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BARR, STRING, LIST, COMPOUND, IARR, LARR = range(13)


class Tag:
    """A typed value: t = tag id, v = the value (dict for compounds, (elem_type, [Tag]) for lists)."""
    __slots__ = ("t", "v")

    def __init__(self, t, v): self.t, self.v = t, v

    def __repr__(self): return f"Tag({self.t}, {self.v!r})"


def _read(b, i, t):
    if t == BYTE: return Tag(t, struct.unpack_from(">b", b, i)[0]), i + 1
    if t == SHORT: return Tag(t, struct.unpack_from(">h", b, i)[0]), i + 2
    if t == INT: return Tag(t, struct.unpack_from(">i", b, i)[0]), i + 4
    if t == LONG: return Tag(t, struct.unpack_from(">q", b, i)[0]), i + 8
    if t == FLOAT: return Tag(t, struct.unpack_from(">f", b, i)[0]), i + 4
    if t == DOUBLE: return Tag(t, struct.unpack_from(">d", b, i)[0]), i + 8
    if t in (BARR, IARR, LARR):
        n = struct.unpack_from(">i", b, i)[0]; i += 4
        f, s = {BARR: ("b", 1), IARR: ("i", 4), LARR: ("q", 8)}[t]
        return Tag(t, list(struct.unpack_from(f">{n}{f}", b, i))), i + n * s
    if t == STRING:
        n = struct.unpack_from(">H", b, i)[0]; i += 2
        return Tag(t, b[i:i + n].decode("utf-8")), i + n
    if t == LIST:
        et = b[i]; n = struct.unpack_from(">i", b, i + 1)[0]; i += 5
        out = []
        for _ in range(n):
            x, i = _read(b, i, et); out.append(x)
        return Tag(t, (et, out)), i
    if t == COMPOUND:
        d = {}
        while True:
            ct = b[i]; i += 1
            if ct == END: return Tag(t, d), i
            n = struct.unpack_from(">H", b, i)[0]; i += 2
            name = b[i:i + n].decode("utf-8"); i += n
            d[name], i = _read(b, i, ct)
    raise ValueError(f"tag {t}")


def _write(out, tag):
    t, v = tag.t, tag.v
    if t == BYTE: out += struct.pack(">b", v)
    elif t == SHORT: out += struct.pack(">h", v)
    elif t == INT: out += struct.pack(">i", v)
    elif t == LONG: out += struct.pack(">q", v)
    elif t == FLOAT: out += struct.pack(">f", v)
    elif t == DOUBLE: out += struct.pack(">d", v)
    elif t in (BARR, IARR, LARR):
        f = {BARR: "b", IARR: "i", LARR: "q"}[t]
        out += struct.pack(">i", len(v)) + struct.pack(f">{len(v)}{f}", *v)
    elif t == STRING:
        e = v.encode("utf-8"); out += struct.pack(">H", len(e)) + e
    elif t == LIST:
        et, items = v
        out += struct.pack(">bi", et if items else (et or END), len(items))
        for x in items: _write(out, x)
    elif t == COMPOUND:
        for name, x in v.items():
            e = name.encode("utf-8")
            out += struct.pack(">bH", x.t, len(e)) + e
            _write(out, x)
        out += b"\x00"


def load(path):
    b = gzip.decompress(open(path, "rb").read())
    assert b[0] == COMPOUND
    n = struct.unpack_from(">H", b, 1)[0]
    root, _ = _read(b, 3 + n, COMPOUND)
    return root


def save(path, root):
    out = bytearray(b"\x0a\x00\x00")                                    # unnamed root compound
    _write(out, root)
    open(path, "wb").write(gzip.compress(bytes(out)))
