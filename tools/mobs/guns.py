"""HOMESTEAD guns (2026-09-30): GeckoLib ITEM models (textures/entity/NAME.png via NamedGeoModel).

Convention (matches the item display transforms in models/item/NAME.json): the hand grips the origin, the barrel
points -Z (forward), the grip hangs down -Y, lock on the +X side (GeckoLib mirrors X: the lock shows on the right of
a right-handed shooter). Whole-pixel sizes only.

  flintlock_pistol - walnut stock, blued octagonal barrel with a brass muzzle cap, flint lock + hammer, brass trigger
                     guard, a bird's-head grip with a brass butt cap, ramrod under the barrel.
  blunderbuss      - a bell-mouthed brass barrel on a long walnut stock with iron bands, big lock, heavy butt.
"""
from mobkit import A, Rig, noise, over, solid, spots, speckle, anim, keys

WOOD = lambda: over(noise(["#3a2414", "#4a2e18", "#5a3a20", "#442a16"]), speckle("#2a1a0e", 0.08))
IRON = lambda: noise(["#2a2e36", "#343a44", "#3e4550"])
BRASS = lambda: over(noise(["#9a7a2a", "#b8923a", "#d0aa4a"]), spots("#7a5a1a", 0.06))
FLINT = solid("#5a5a60")


def flintlock_pistol(seed):
    m = Rig("flintlock_pistol", 64, 64, seed)
    m.bone("root", [0, 0, 0])
    c = m.cube
    c("root", [-1, 1, -12], [2, 2, 10], IRON())                        # the barrel
    c("root", [-1, 0, -13], [2, 4, 1], BRASS())                         # muzzle cap
    c("root", [-1, 0, -9], [2, 1, 7], WOOD())                           # forestock under the barrel
    c("root", [-1, -1, -10], [1, 1, 8], IRON())                         # ramrod
    c("root", [-1, -1, -2], [2, 4, 4], WOOD())                          # the lock body / breech
    c("root", [1, 0, -2], [1, 2, 3], IRON())                            # lock plate (right side)
    c("root", [1, 2, 0], [1, 2, 1], IRON())                             # the cock (hammer)
    c("root", [1, 3, -1], [1, 1, 1], FLINT)                             # its flint
    c("root", [1, 2, -2], [1, 1, 1], BRASS())                           # frizzen / pan
    c("root", [0, -3, -3], [1, 1, 3], BRASS())                          # trigger guard (the loop under it)
    c("root", [0, -2, -1], [1, 1, 1], IRON())                           # trigger
    c("root", [-1, -7, 1], [2, 6, 2], WOOD(), rot=[-24, 0, 0], pivot=[0, -1, 2])       # bird's-head grip, raked back
    c("root", [-1, -9, 2], [2, 2, 3], BRASS(), rot=[-24, 0, 0], pivot=[0, -1, 2])      # brass butt cap
    anim(m, "idle", 1.0, {"root": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, 0]))}})
    return m


def blunderbuss(seed):
    m = Rig("blunderbuss", 64, 64, seed)
    m.bone("root", [0, 0, 0])
    c = m.cube
    c("root", [-1, 1, -18], [3, 3, 14], BRASS())                        # the brass barrel
    c("root", [-2, 0, -20], [5, 5, 2], BRASS())                         # the bell mouth
    c("root", [-1, 1, -21], [3, 3, 1], solid("#1a1410"))                # its dark bore
    for z in (-15, -10, -6):
        c("root", [-1, -2, z], [3, 1, 1], IRON())                       # iron bands under the stock
    c("root", [-1, -1, -14], [3, 2, 12], WOOD())                         # full-length stock
    c("root", [-1, -1, -2], [3, 5, 4], WOOD())                          # breech
    c("root", [2, 0, -2], [1, 3, 3], IRON())                            # lock plate
    c("root", [2, 3, 0], [1, 2, 1], IRON())                             # cock
    c("root", [2, 4, -1], [1, 1, 1], FLINT)
    c("root", [0, -2, -2], [1, 1, 3], BRASS())                          # trigger guard
    c("root", [-1, -6, 2], [3, 6, 3], WOOD(), rot=[-30, 0, 0], pivot=[0, -1, 3])       # the heavy butt
    c("root", [-1, -9, 3], [3, 3, 4], WOOD(), rot=[-30, 0, 0], pivot=[0, -1, 3])
    c("root", [-1, -10, 6], [3, 1, 1], BRASS(), rot=[-30, 0, 0], pivot=[0, -1, 3])     # butt plate edge
    anim(m, "idle", 1.0, {"root": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, 0]))}})
    return m


MOBS = {"flintlock_pistol": flintlock_pistol, "blunderbuss": blunderbuss}
