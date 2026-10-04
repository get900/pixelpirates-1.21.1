"""Find (and with --fix, remove) z-fighting - same-facing faces on one plane, the in-game flicker - in the shipped
models: GeckoLib geo (geo/**) and vanilla block/item models with elements (models/**). Rules: tools/zfight.py.
usage: python tools/check_zfight.py [--fix] [-v] (--all | <geo name, e.g. rackham or armor/corsair> ...)"""
import json, sys, pathlib
import zfight

ROOT = pathlib.Path(__file__).resolve().parents[1]
ASSETS = [ROOT / "src/main/resources/assets", ROOT / "src/main/generated/assets"]
GEO = ROOT / "src/main/resources/assets/pixelpirates/geo"


def load(p):
    return json.loads(p.read_text(encoding="utf-8-sig"))


def _inline_arrays(s):
    """Blockbench style: arrays of plain numbers on one line ("origin": [1, 2, 3])."""
    import re
    num = r"-?\d+(?:\.\d+)?(?:[eE][-+]?\d+)?"
    return re.sub(r"\[\s*(" + num + r"(?:,\s*" + num + r")*)\s*\]",
                  lambda m: "[" + ", ".join(x.strip() for x in m.group(1).split(",")) + "]", s)


def _styles():
    for ind in ("\t", 1, 2, 4, 3, None):
        for inline in (False, True):
            yield ind, inline


def _render(obj, style):
    ind, inline = style
    s = json.dumps(obj, indent=ind, ensure_ascii=False) if ind is not None else json.dumps(obj, separators=(",", ":"))
    return _inline_arrays(s) if inline else s


def _cubes(geo):
    return [c for g in geo["minecraft:geometry"] for b in g.get("bones", []) for c in b.get("cubes", [])]


def _patch_inflates(text, before, after):
    """Set "inflate" in the text of each cube whose inflate changed; everything else stays byte-for-byte."""
    import re
    oc, fc = _cubes(before), _cubes(after)
    starts = [m.start() for m in re.finditer(r'\{\s*"origin"', text)]
    assert len(starts) == len(oc) == len(fc), "cube objects must open with \"origin\""
    for k in reversed(range(len(oc))):
        v = fc[k].get("inflate", 0)
        if oc[k].get("inflate", 0) == v:
            continue
        s, depth = starts[k], 0
        for e in range(s, len(text)):
            depth += {"{": 1, "}": -1}.get(text[e], 0)
            if depth == 0:
                break
        seg = text[s:e + 1]
        sp = " " if '"origin": ' in seg else ""
        if '"inflate"' in seg:
            seg = re.sub(r'"inflate"\s*:\s*[-\d.eE+]+', f'"inflate":{sp}{v}', seg, count=1)
        else:
            seg = re.sub(r'^\{(\s*)"origin"', lambda m: '{' + m.group(1) + f'"inflate":{sp}{v},{sp}"origin"', seg, count=1)
        text = text[:s] + seg + text[e + 1:]
    assert json.loads(text) == after
    return text


def save(p, obj, original_obj):
    """Write in the file's own formatting (indent, inline number arrays, BOM, newline at the end) so the diff
    shows only the nudges."""
    raw = p.read_bytes()
    bom = raw.startswith(b"\xef\xbb\xbf")
    text = raw.decode("utf-8-sig").replace("\r\n", "\n")
    body, nl = (text[:-1], "\n") if text.endswith("\n") else (text, "")
    style = next((st for st in _styles() if _render(original_obj, st) == body), None)
    if style is not None:
        out = _render(obj, style) + nl
    elif p.name.endswith(".geo.json"):
        out = _patch_inflates(text, original_obj, obj)     # hand-made Blockbench file: touch only the nudged cubes
    else:
        print(f"    (formatting not recognised - rewritten with indent 1)")
        out = _render(obj, (1, False)) + nl
    if "\r\n" in raw.decode("utf-8-sig"):
        out = out.replace("\n", "\r\n")
    p.write_bytes((b"\xef\xbb\xbf" if bom else b"") + out.encode("utf-8"))


def run(paths, fix, verbose):
    total = 0
    for p in paths:
        obj = load(p)
        is_geo = p.name.endswith(".geo.json")
        hits = zfight.find_geo(obj) if is_geo else zfight.find_elements(obj)
        if not hits:
            continue
        total += len(hits)
        print(f"{p.relative_to(ROOT)}: {len(hits)} coplanar overlaps")
        if verbose:
            for h in hits:
                print(f"    {h[0]} & {h[1]} share the {h[2]}={h[3]:g} plane")
        if fix:
            original = json.loads(json.dumps(obj))
            (zfight.fix_geo if is_geo else zfight.fix_elements)(obj)
            left = zfight.find_geo(obj) if is_geo else zfight.find_elements(obj)
            if left:
                print(f"    !! {len(left)} could not be fixed")
            save(p, obj, original)
    print(f"{total} coplanar overlaps" + (" (fixed)" if fix and total else ""))
    return total


if __name__ == "__main__":
    args = sys.argv[1:]
    fix, verbose = "--fix" in args, "-v" in args
    names = [a for a in args if not a.startswith("-")]
    if "--all" in args:
        paths = sorted(GEO.rglob("*.geo.json"))
        for a in ASSETS:
            paths += sorted(q for q in a.glob("*/models/**/*.json") if "elements" in q.read_text(encoding="utf-8-sig"))
    else:
        paths = [GEO / f"{n}.geo.json" for n in names]
    sys.exit(1 if run(paths, fix, verbose) and not fix else 0)
