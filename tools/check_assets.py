"""Static asset check (no game needed): for every block + item id registered in the given Java files, verify the
blockstate / item model exist (generated/ or resources/), follow model parents + texture references to real PNGs,
check the lang has a name, and flag JSON with a BOM.

    python tools/check_assets.py src/main/java/net/get900/pixelpirates/homestead
"""
import json, re, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ASSET_DIRS = [ROOT / "src/main/generated/assets/pixelpirates", ROOT / "src/main/resources/assets/pixelpirates"]
LANG = json.loads((ROOT / "src/main/resources/assets/pixelpirates/lang/en_us.json").read_text(encoding="utf-8"))
problems = []


def find(rel):
    for d in ASSET_DIRS:
        p = d / rel
        if p.exists():
            return p
    return None


def load(rel):
    p = find(rel)
    if p is None:
        return None
    raw = p.read_bytes()
    if raw[:3] == b"\xef\xbb\xbf":
        problems.append(f"BOM in {p}")
    return json.loads(raw.decode("utf-8-sig"))


def check_model(ref, seen, who):
    if ref in seen:
        return
    seen.add(ref)
    if ref.startswith("minecraft:") or ref.startswith("builtin/"):
        return
    ns, path = ref.split(":", 1) if ":" in ref else ("pixelpirates", ref)
    if ns != "pixelpirates":
        return
    m = load(f"models/{path}.json")
    if m is None:
        problems.append(f"{who}: missing model {ref}")
        return
    for k, t in (m.get("textures") or {}).items():
        if t.startswith("#"):
            continue
        tns, tp = t.split(":", 1) if ":" in t else ("minecraft", t)
        if tns == "pixelpirates" and find(f"textures/{tp}.png") is None:
            problems.append(f"{who}: model {ref} texture {k} -> missing {t}.png")
    if "parent" in m:
        check_model(m["parent"], seen, who)
    for o in m.get("overrides", []):
        check_model(o["model"], seen, who)


def vanilla_textures():
    """Texture paths in the Minecraft client jar (Loom's cache), or None if it isn't there."""
    import glob, os, zipfile
    for j in glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/1.20.1/minecraft-client*.jar")):
        names = zipfile.ZipFile(j).namelist()
        return {n[len("assets/minecraft/textures/"):-4] for n in names if n.startswith("assets/minecraft/textures/") and n.endswith(".png")}
    return None


def check_all_model_textures():
    """Every block/item model file (registered or not): a baked model can only use textures in the block atlas
    (block/ or item/ folders - an entity/ texture shows as missing), and a minecraft: texture must really exist
    (minecraft:block/smooth_quartz does not - it's quartz_block_bottom). Both bit the chess set 2026-10-05."""
    vanilla = vanilla_textures()
    for d in ASSET_DIRS:
        for p in sorted((d / "models").rglob("*.json")):
            m = json.loads(p.read_text(encoding="utf-8-sig"))
            for k, t in (m.get("textures") or {}).items():
                if t.startswith("#"):
                    continue
                tns, tp = t.split(":", 1) if ":" in t else ("minecraft", t)
                who = f"model {p.relative_to(d).as_posix()} texture {k} -> {t}"
                if tns == "pixelpirates" and not tp.startswith(("block/", "item/")):
                    problems.append(f"{who}: not in the block atlas (move it under textures/block/)")
                if tns == "minecraft" and vanilla is not None and tp not in vanilla and not tp.startswith("trims/"):   # trims are built at load
                    problems.append(f"{who}: no such vanilla texture")


def scan(java_root):
    blocks, items = set(), set()
    for f in Path(java_root).rglob("*.java"):
        s = f.read_text(encoding="utf-8")
        for m in re.finditer(r'block\("([a-z0-9_]+)",\s*new [^;]*?,\s*(true|false)\)', s, re.S):
            blocks.add((m.group(1), m.group(2) == "true"))
        for m in re.finditer(r'(?:item|tool|special|blockItem|regionTool|fish)\("([a-z0-9_]+)"', s):
            items.add(m.group(1))
        for m in re.finditer(r'(?:trophy)\("([a-z0-9_]+)"\)', s):
            blocks.add((m.group(1), True))
    return blocks, items


if __name__ == "__main__":
    blocks, items = scan(sys.argv[1] if len(sys.argv) > 1 else ROOT / "src/main/java/net/get900/pixelpirates/homestead")
    for name, has_item in sorted(blocks):
        bs = load(f"blockstates/{name}.json")
        if bs is None:
            problems.append(f"block {name}: no blockstate")
        else:
            refs = []
            for v in (bs.get("variants") or {}).values():
                refs += [x["model"] for x in (v if isinstance(v, list) else [v])]
            for part in bs.get("multipart", []):
                a = part["apply"]
                refs += [x["model"] for x in (a if isinstance(a, list) else [a])]
            for r in set(refs):
                check_model(r, set(), f"block {name}")
        if f"block.pixelpirates.{name}" not in LANG:
            problems.append(f"block {name}: no lang")
        if has_item:
            items.add(name)
    for name in sorted(items):
        if load(f"models/item/{name}.json") is None:
            problems.append(f"item {name}: no item model")
        else:
            check_model(f"pixelpirates:item/{name}", set(), f"item {name}")
        if f"item.pixelpirates.{name}" not in LANG and f"block.pixelpirates.{name}" not in LANG:
            problems.append(f"item {name}: no lang")
    check_all_model_textures()
    print(f"checked {len(blocks)} blocks, {len(items)} items, every model's textures")
    for p in problems:
        print("  PROBLEM", p)
    sys.exit(1 if problems else 0)
