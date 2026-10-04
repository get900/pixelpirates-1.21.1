"""Item model JSONs (builtin/entity + display transforms) for every 3D hand-held weapon, COMPUTED from the hand frames
instead of guessed (round 3, 2026-09-30 - round 1/2 had blades lying on their side and guns hanging under the fist).

    python tools/gen_weapon_item_models.py

How Minecraft places a builtin/entity item (1.20.1, read from HeldItemFeatureRenderer / HeldItemRenderer / Transformation):
  * THIRD PERSON: arm frame, rotX(-90), rotY(180), translate(1/16, 2/16, -10/16) -> hand frame H with
    +X = outward (away from the body), +Y = FORWARD, +Z = UP; origin = front-bottom of the fist. Vanilla puts a sword's
    handle at H (0, -2, +1.5) px -> HAND_3P.
  * FIRST PERSON: camera frame (+X right, +Y up, -Z forward), origin at the equip offset (lower right of the screen).
    Vanilla's sword handle lands at (1.1, -1.3, -0.5) px -> HAND_1P.
  * Display transform = translate(T px) * rotationXYZ(rx, ry, rz) (= Rx*Ry*Rz) * scale(s); then GeoItemRenderer draws the
    geo with X mirrored, 1 px = 1/16 block, geo origin at the item origin.
So for a transform we choose the rotation R (what each geo axis should point at) and solve T = target - R*s*anchor.
Model conventions (tools/gen_weapon_models.py, tools/mobs/guns.py, tools/mobs/gallows.py):
  blades/poles/bow/flail: grip at the origin, pointing +Y, edge -Z;  guns: barrel -Z, grip hangs -Y, `GRIP` = hand centre.
"""
import json, math, pathlib
import numpy as np

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/assets/pixelpirates"
GUNS = {  # id: geo point the hand closes around (x, y, z) px
    "rackham_blunderbuss": (0, -0.5, -0.5),
    "dutchmans_hand_cannon": (0, -1, -0.5),
    "krakenmaw_harpoon_gun": (0, -3, -0.5),
    "flintlock_pistol": (0, -3.5, 2),
    "blunderbuss": (0, -1, 0),
}
MELEE = ["serpentspine_longbow", "everburning_flail", "sunken_trident", "bloodfin_maw", "chainbreaker", "heartseeker",
         "tidefathers_wrath", "gallowbrand"]
PARTICLE = {"rackham_blunderbuss": "minecraft:block/dark_oak_planks", "serpentspine_longbow": "minecraft:block/prismarine",
            "everburning_flail": "minecraft:block/magma", "dutchmans_hand_cannon": "minecraft:block/soul_sand",
            "sunken_trident": "minecraft:block/gold_block", "bloodfin_maw": "minecraft:block/nether_wart_block",
            "krakenmaw_harpoon_gun": "minecraft:block/black_concrete", "chainbreaker": "minecraft:block/iron_block",
            "heartseeker": "minecraft:block/crimson_nylium", "tidefathers_wrath": "minecraft:block/dark_prismarine",
            "gallowbrand": "minecraft:block/deepslate", "flintlock_pistol": "minecraft:block/dark_oak_planks",
            "blunderbuss": "minecraft:block/dark_oak_planks"}
# weapons whose broad face is the geo XY plane (spread sideways, like the trident's prongs) instead of YZ
FACE_Z = {"sunken_trident"}
# where the hand closes when it is not the origin (geo px); the chainbreaker's haft runs y -10..3
MELEE_GRIP = {"chainbreaker": (0, -4, 0)}
# first-person size caps for bulky heads
FP_CAP = {"everburning_flail": 0.3}
# first-person scale overrides: the longbow is drawn toward the screen centre by vanilla's bow pose - keep it big enough to
# see the limbs flex and the arrow
FP_SCALE = {"serpentspine_longbow": 0.31}
FP_RAISE = {"serpentspine_longbow": 5.0}
BOW_FP = {"serpentspine_longbow": (0, 25, 10)}      # rx, ry, rz (Rx*Ry*Rz): 25 deg toward the crosshair, top canted in 10      # px up, so the nock + arrow stay on screen while drawing
HAND_3P = np.array([0.0, -2.0, 1.5])
HAND_1P = np.array([1.1, -1.3, -0.5])


def Rx(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def Ry(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def Rz(a):
    a = math.radians(a); c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def euler(rx, ry, rz):
    return Rx(rx) @ Ry(ry) @ Rz(rz)


def to_euler(M):
    """Decompose M = Rx*Ry*Rz into degrees (Minecraft's display rotation order)."""
    sy = max(-1.0, min(1.0, M[0, 2]))
    ry = math.asin(sy)
    if abs(math.cos(ry)) > 1e-6:
        rx = math.atan2(-M[1, 2], M[2, 2]); rz = math.atan2(-M[0, 1], M[0, 0])
    else:
        rx = math.atan2(M[2, 1], M[1, 1]); rz = 0.0
    return [round(math.degrees(v), 2) for v in (rx, ry, rz)]


def geo(p):                      # geo px -> item space (GeckoLib mirrors X)
    return np.array([-p[0], p[1], p[2]], dtype=float)


def bounds(name):
    g = json.loads((ROOT / "geo" / (name + ".geo.json")).read_text())["minecraft:geometry"][0]
    lo, hi = [1e9] * 3, [-1e9] * 3
    for b in g["bones"]:
        for c in b.get("cubes", []):
            for i in range(3):
                lo[i] = min(lo[i], c["origin"][i]); hi[i] = max(hi[i], c["origin"][i] + c["size"][i])
    return np.array(lo), np.array(hi)


def tf(R, s, anchor, target):
    T = np.asarray(target, float) - R @ (s * geo(anchor))
    if np.any(np.abs(T) > 80):
        raise ValueError("translation beyond Minecraft's +-80 clamp")
    return {"rotation": to_euler(R), "translation": [round(float(v), 3) for v in T], "scale": [round(s, 4)] * 3}


def frame_axes(x_to, y_to):
    """Rotation sending item +X -> x_to and +Y -> y_to (unit, orthogonal)."""
    x = np.asarray(x_to, float); y = np.asarray(y_to, float)
    x /= np.linalg.norm(x); y /= np.linalg.norm(y)
    return np.column_stack([x, y, np.cross(x, y)])


def melee(name):
    lo, hi = bounds(name)
    H = hi[1] - lo[1]
    centre = (lo + hi) / 2
    grip = MELEE_GRIP.get(name, (0, 0, 0))
    hs, fs = min(0.65, 30 / H), FP_SCALE.get(name, min(FP_CAP.get(name, 0.4), 12 / H))   # ~12 px tall, like a vanilla sword
    face_z = name in FACE_Z
    # third person: blade forward (H +Y), edge (geo -Z) down, flats in/out - exactly how vanilla holds a sword
    tp = tf(np.eye(3), hs, grip, HAND_3P)
    # first person (round 3: the blades showed edge-on): blade up, leaning to the crosshair and a little away, and its
    # BROAD FACE turned toward the camera
    b = np.array([-0.25, 0.9, -0.3]); b /= np.linalg.norm(b)
    v = np.array([-0.35, 0.25, 0.9]); n = v - v.dot(b) * b; n /= np.linalg.norm(n)
    Rf = np.column_stack([np.cross(b, n), b, n]) if face_z else frame_axes(n, b)
    if name in BOW_FP:
        # a BOW points INTO the screen (geo -Z = the arrow's way), limbs upright with a small cant, turned just enough
        # to show its curve - never broad-side like a blade (that laid the arrow across the screen, round 1)
        Rf = euler(*BOW_FP[name])
    fp = tf(Rf, fs, grip, HAND_1P + np.array([0, FP_RAISE.get(name, 0.0), 0]))
    # GUI / frames: broad face toward the viewer, blade up-right on the diagonal, centred
    diag = [0.7071, 0.7071, 0]
    Rg = frame_axes([0.7071, -0.7071, 0], diag) if face_z else frame_axes([0, 0, 1], diag)
    gs = 21 / H
    gui = tf(Rg, gs, centre, [0, 0, 0])
    fixed = tf(Ry(180) @ Rg, 46 / H, centre, [0, 0, 0])
    ground = tf(np.eye(3), 12 / H, centre, [0, 0, 0])
    return {"thirdperson_righthand": tp, "thirdperson_lefthand": tp, "firstperson_righthand": fp,
            "firstperson_lefthand": fp, "gui": gui, "ground": ground, "fixed": fixed}


def gun(name):
    lo, hi = bounds(name)
    L = hi[2] - lo[2]
    centre = (lo + hi) / 2
    grip = GUNS[name]
    ts = 0.8 * min(1.0, 36 / L)
    fs = 0.5 * min(1.0, 30 / L)
    # third person: barrel (geo -Z) forward = H +Y, grip (geo -Y) down = H -Z  -> Rx(90); grip in the fist
    tp = tf(Rx(90), ts, grip, HAND_3P)
    # first person: barrel into the screen, a few degrees toward the crosshair, held low on the right
    fp = tf(euler(0, 6, 0), fs, grip, [1.5, -1.0, 0.0])
    # GUI: side view, barrel to the right, slightly raised
    Rg = euler(15, -90, 0)
    gui = tf(Rg, 15 / L, centre, [0, 0, 0])
    fixed = tf(Ry(180) @ Rg, 28 / L, centre, [0, 0, 0])
    ground = tf(np.eye(3), 12 / L, centre, [0, 0, 0])
    return {"thirdperson_righthand": tp, "thirdperson_lefthand": tp, "firstperson_righthand": fp,
            "firstperson_lefthand": fp, "gui": gui, "ground": ground, "fixed": fixed}


def main():
    for n in list(GUNS) + MELEE:
        disp = gun(n) if n in GUNS else melee(n)
        out = {"parent": "builtin/entity", "gui_light": "front", "textures": {"particle": PARTICLE[n]}, "display": disp}
        (ROOT / "models/item" / (n + ".json")).write_text(json.dumps(out, indent=1), encoding="utf-8")
        print(f"{n:24s} 3p {disp['thirdperson_righthand']['rotation']} {disp['thirdperson_righthand']['translation']}"
              f"  gui {disp['gui']['rotation']} s{disp['gui']['scale'][0]}")


if __name__ == "__main__":
    main()
