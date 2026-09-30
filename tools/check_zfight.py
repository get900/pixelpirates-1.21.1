"""Find z-fighting in a generated geo: pairs of cubes on the same bone (same rotation/pivot) with a
face on the same plane whose areas overlap. usage: python tools/check_zfight.py <mob> [...]"""
import json, sys, pathlib
A = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/pixelpirates/geo"

def box(c):
    i = c.get("inflate", 0)
    o, s = c["origin"], c["size"]
    return [(o[k] - i, o[k] + s[k] + i) for k in range(3)]

def check(mob):
    bones = json.loads((A / f"{mob}.geo.json").read_text())["minecraft:geometry"][0]["bones"]
    hits = []
    for b in bones:
        cs = b.get("cubes", [])
        for i in range(len(cs)):
            for j in range(i + 1, len(cs)):
                a, c = cs[i], cs[j]
                if (a.get("rotation"), a.get("pivot")) != (c.get("rotation"), c.get("pivot")):
                    continue
                A_, C_ = box(a), box(c)
                for ax in range(3):
                    others = [k for k in range(3) if k != ax]
                    ov = all(min(A_[k][1], C_[k][1]) - max(A_[k][0], C_[k][0]) > 1e-3 for k in others)
                    if not ov:
                        continue
                    for pa in A_[ax]:
                        for pc in C_[ax]:
                            if abs(pa - pc) < 1e-3:
                                # same-facing faces on one plane fight; a face buried inside the other cube is hidden
                                same = (pa == A_[ax][0]) == (pc == C_[ax][0])
                                if same:
                                    hits.append((b["name"], i, j, "xyz"[ax], pa))
    for h in hits:
        print(f"{mob}: bone {h[0]} cubes {h[1]} & {h[2]} share the {h[3]}={h[4]:g} plane")
    return hits

if __name__ == "__main__":
    n = sum(len(check(m)) for m in sys.argv[1:])
    print(f"{n} coplanar overlaps")
