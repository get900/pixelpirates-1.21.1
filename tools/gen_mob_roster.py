"""Pixel Pirates - generate the mob roster models (geo + texture + glowmask + animations).

    python tools/gen_mob_roster.py                 # every mob
    python tools/gen_mob_roster.py sea_serpent siren

Each mob is hand-authored against its concept render in D:\\Minecraft Modding\\mobrenders, one
builder function per mob in tools/mobs/p1.py .. p5.py, using the modelling kit in tools/mobkit.py
(rotated cubes, pixel-art faces with glow + cutout, banded/noise materials).

Contract with the Java side (ModMob): every roster mob exports idle, move, attack, special and
special2. The siren (SirenEntity) and coral jelly (CoralJellyEntity) keep their own clip names.
Rest poses live on the bones; animations are OFFSETS (GeckoLib adds keyframes to the rest rotation).
The older magma_brute / mimic / abyssal_angler still come from gen_mob_assets.py.
"""
import importlib
import sys

REGISTRY = {}
SKINS = {}
for _mod in ("p1", "p2", "p3", "p4", "p5"):
    _m = importlib.import_module("mobs." + _mod)
    REGISTRY.update(_m.MOBS)
    SKINS.update(getattr(_m, "SKINS", {}))


def write_skin(base, name, skin, build, seed):
    """Paint an alternate texture for `base`'s geometry. The skin builder must add the very same
    cubes in the same order - verified here, since any difference would scramble the UVs in game."""
    from gen_mob_assets import ASSETS
    alt = build(seed)
    alt.tw, alt.th = base.tw, base.th
    alt.pack()
    a = [(c.bone, c.size, c.uv) for c in base.cubes]
    b = [(c.bone, c.size, c.uv) for c in alt.cubes]
    if a != b:
        raise SystemExit(f"{name}/{skin}: skin geometry differs from the base model")
    img, gimg = alt.paint()
    stem = f"{name}_{skin}"
    img.save(ASSETS / "textures/entity" / f"{stem}.png")
    gpath = ASSETS / "textures/entity" / f"{stem}_glowmask.png"
    if gimg.getbbox() is None:
        gpath.unlink(missing_ok=True)
    else:
        gimg.save(gpath)
    print(f"  skin {stem}")


if __name__ == "__main__":
    names = sys.argv[1:] or list(REGISTRY)
    for n in names:
        seed = sum(map(ord, n))
        m = REGISTRY[n](seed)
        m.write((None,))
        for skin, build in SKINS.get(n, {}).items():
            write_skin(m, n, skin, build, seed)
