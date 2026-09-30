"""BOSS SET textures (item/BossArmor, 2026-09-30): 20 inventory icons + 10 worn layers, repainted from an existing set's
shapes with each boss set's palette (luminance -> palette ramp, brightest band -> trim accent). A FIRST PASS for balance
testing - the proper per-set art is part of the armour visual overhaul (see HANDOFF.md).
    python tools/gen_boss_armor.py        (needs pillow + numpy)"""
import pathlib
import numpy as np
from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parent.parent / "src/main/resources/assets"
ITEM = ROOT / "pixelpirates/textures/item"
WORN = ROOT / "minecraft/textures/models/armor"

# set id: (template set, dark->light ramp, trim accent)
SETS = {
    "powder_monkey": ("corsair", ["#1a1410", "#3a2a1c", "#5c4128", "#7a5a38", "#2f6f6a"], "#c8a24a"),   # tarred leather, teal scale, brass
    "forgeguard":    ("ashen", ["#101014", "#22222a", "#3a3a44", "#56565f", "#7a7a84"], "#ff7a26"),       # black iron, ember seams
    "tidecourt":     ("kraken_scale", ["#0c2430", "#15475a", "#1f6f7e", "#3aa3a0", "#9fe3d0"], "#f2e6c8"),# coral scale, pearl
    "gallowbreaker": ("cursed_bone", ["#0e0e12", "#1e1e26", "#34343e", "#50505c", "#6e6e7a"], "#9aa0a8"), # black plate, chains
    "thalassar":     ("kraken_scale", ["#041a16", "#0b3a30", "#11584a", "#1c7a64", "#2fa086"], "#ffd24a"),# sea-green hide, gold veins
}


def hexrgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) for i in (0, 2, 4)], dtype=np.float32)


def repaint(src: Image.Image, ramp, accent) -> Image.Image:
    a = np.asarray(src.convert("RGBA")).astype(np.float32)
    rgb, alpha = a[..., :3], a[..., 3]
    lum = rgb @ np.array([0.299, 0.587, 0.114], dtype=np.float32)
    m = alpha > 0
    out = np.zeros_like(a)
    if m.any():
        lo, hi = np.percentile(lum[m], 3), np.percentile(lum[m], 97)
        t = np.clip((lum - lo) / max(hi - lo, 1e-3), 0, 1)
        cols = np.stack([hexrgb(c) for c in ramp])
        idx = t * (len(cols) - 1)
        i0 = np.floor(idx).astype(int).clip(0, len(cols) - 1)
        i1 = (i0 + 1).clip(0, len(cols) - 1)
        f = (idx - i0)[..., None]
        col = cols[i0] * (1 - f) + cols[i1] * f
        top = t > 0.9                                                     # the brightest band becomes the trim
        col[top] = hexrgb(accent)
        out[..., :3] = col
    out[..., 3] = alpha
    return Image.fromarray(out.clip(0, 255).astype(np.uint8), "RGBA")


def main():
    for sid, (tpl, ramp, accent) in SETS.items():
        for piece in ("helmet", "chestplate", "leggings", "boots"):
            repaint(Image.open(ITEM / f"{tpl}_{piece}.png"), ramp, accent).save(ITEM / f"{sid}_{piece}.png")
        for layer in (1, 2):
            repaint(Image.open(WORN / f"{tpl}_layer_{layer}.png"), ramp, accent).save(WORN / f"{sid}_layer_{layer}.png")
        print("wrote", sid)


if __name__ == "__main__":
    main()
