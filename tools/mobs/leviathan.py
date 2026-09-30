"""THE LEVIATHAN (boss 10/10, 2026-09-30) - from the reference D:\\Minecraft Modding\\mobrenders\\phase5\\Leviathan.png
("PHASE 2 - THE HUNGER"): a vast black sea-serpent, glossy jagged scales, red bioluminescent lights down its spine,
flanks and every tendril, a cavernous crimson maw of hooked fangs dripping red, rows of dorsal spines and a fringe of
tentacle-like tendrils along its body. Built as pieces that the game chains together (~78 blocks at renderScale 3.0):

  leviathan_head_pN   the head + neck (model faces -Z; ~56 px long -> ~10.5 blocks). Bones: neck > skull > jaw, tendrils,
                      crust (P1: hidden once blasted off), shaft (P3: the Bane Shaft in its crown, hidden once knocked loose)
  leviathan_body_pN   one body segment (28 px -> 5.25 blocks; the entity chain spaces them 5 apart)
  leviathan_tail_pN   the tail and its spiked fluke
  leviathan_fin       what breaks the surface while it flees between lairs (a back ridge of spines and lights)

Forms: 1 THE WAKING - the same body under a CRUST of barnacles, stone and coral, the ancients' chains still on it, milky
eyes, only a faint red in the cracks. 2 THE HUNGER - the reference. 3 THE UNMAKING - split open along the spine (burning
crimson cracks, ribs showing) and crowned with the wreckage of the ports it destroyed (masts, a harbour bell) with the
Bane Shaft rammed through the crown.
Clips - head: idle, move, attack, special, roar, yawn, breach, rake, gulp, spit, lure, rear, flinch, dive, reared (loop),
pinned (loop), stunned (loop). Pieces + fin: idle, move, swim.
"""
import math
from mobkit import A, Rig, lit, noise, over, solid, spots, speckle, veins, drips, anim, keys, wave, S

SCALE = ["#07070a", "#0d0d12", "#14141a", "#1b1b22"]
CRUST = ["#3a3e38", "#464a42", "#52564c", "#5e6256"]
BONE = ["#e6dcc6", "#d6ccb4", "#c8bea6"]


def skin(form):
    if form == 1:
        return over(noise(SCALE), veins("#6a1414", 0.02, glow=True, length=(2, 4)), speckle("#2a2a30", 0.06))
    if form == 2:
        return over(noise(SCALE), spots("#ff2020", 0.03, glow=True), veins("#8a0a14", 0.03, glow=False, length=(3, 7)),
                    speckle("#2e2e3a", 0.08))
    return over(noise(SCALE), veins("#ff3a1a", 0.06, glow=True, length=(3, 9)), veins("#ffd0a0", 0.015, glow=True, length=(2, 4)),
                spots("#ff2020", 0.02, glow=True))


def belly(form):
    base = ["#1e1418", "#261a1e", "#2e2024"] if form != 1 else ["#34302a", "#3e3a32", "#48443a"]
    return over(noise(base), spots("#ff2020", 0.04, glow=form >= 2))


def crust_mat():
    return over(noise(CRUST), spots("#2a2c28", 0.2, size=2), spots("#7a8a6a", 0.08), spots("#3aa08a", 0.03), spots("#c06a8a", 0.02),
                veins("#9a2a1a", 0.02, glow=True, length=(2, 3)))


def lights(form):
    return lit("#ff2a2a") if form >= 2 else lit("#8a2020")


def spine_mat(form):
    return noise(["#1a1a20", "#22222a", "#2a2a34"]) if form != 1 else noise(["#4a4a44", "#56564e"])


# =====================================================================================
# HEAD
# =====================================================================================
def head(seed, form=2, dark=False):
    m = Rig(f"leviathan_head_p{form}", 256, 256, seed)
    SK, BL = (noise(SCALE), noise(["#1e1418", "#261a1e"])) if dark else (skin(form), belly(form))
    eye = "#1a1a1e" if dark else {1: "#d8dcd0", 2: "!#ff1a1a", 3: "!#ffd8c0"}[form]
    LT = solid("#2a0a0a") if dark else lights(form)
    m.bone("root", [0, 0, 0])
    m.bone("neck", [0, 10, 10], "root")
    m.cube("neck", [-11, 1, 0], [22, 18, 16], SK)                               # the neck, reaching back to the first segment
    m.cube("neck", [-9, 0, 1], [18, 1, 14], BL)
    m.bone("skull", [0, 12, 0], "neck")
    m.cube("skull", [-12, 7, -26], [24, 13, 26], SK)                            # the cranium
    m.cube("skull", [-9, 7, -44], [18, 9, 18], SK)                              # the long snout
    m.cube("skull", [-6, 9, -50], [12, 6, 6], SK)                               # its tip
    m.cube("skull", [-13, 17, -24], [4, 4, 14], spine_mat(form))                # brow ridges (proud of the cranium)
    m.cube("skull", [9, 17, -24], [4, 4, 14], spine_mat(form))
    # the eyes: deep in the brow, burning (form 1: milky)
    ea = A.at(3, 2, {(0, 0): "e", (1, 0): "e", (2, 0): "e", (1, 1): "e"}, e=eye)
    m.cube("skull", [-13, 13, -22], [1, 2, 3], solid("#0a0a0e"), art={"east": ea})
    m.cube("skull", [12, 13, -22], [1, 2, 3], solid("#0a0a0e"), art={"west": ea})
    # glowing lights down its jawline
    for z in range(-42, -4, 6):
        m.cube("skull", [-12.5 if z > -26 else -9.5, 8, z], [1, 1, 2], LT)
        m.cube("skull", [11.5 if z > -26 else 8.5, 8, z], [1, 1, 2], LT)
    # dorsal spines along the crown and neck, raked back
    for i, z in enumerate((-20, -12, -4, 4, 12)):
        h = 10 - abs(i - 2) * 2
        m.cube("skull" if z < 0 else "neck", [-1.5, 20 if z < 0 else 19, z], [3, h, 3], spine_mat(form), rot=[-30, 0, 0], pivot=[0, 20, z + 1.5])
    # the upper fangs: a curtain of hooked teeth along the upper jaw
    TOOTH = over(noise(BONE), drips("#8a0a10", 0.4, maxlen=2))
    for z in range(-48, -8, 3):
        edge = -9 if z < -26 else -12
        L = 6 if z % 2 == 0 else 4
        m.cube("skull", [edge + 0.5, 7 - L, z], [1, L, 1], TOOTH)
        m.cube("skull", [-edge - 1.5, 7 - L, z], [1, L, 1], TOOTH)
    # the jaw: drops open (rest slightly agape - the reference's gaping maw)
    m.bone("jaw", [0, 8, -2], "skull", rotation=[8, 0, 0])
    m.cube("jaw", [-10, 1, -44], [20, 5, 42], SK)
    m.cube("jaw", [-9, 0, -42], [18, 1, 38], BL)
    m.cube("jaw", [-8, 6, -40], [16, 1, 34], lit("#c01020") if form >= 2 and not dark else solid("#4a1a1a"))    # the crimson throat
    for z in range(-42, -8, 3):
        L = 5 if z % 2 else 3
        m.cube("jaw", [-9.5, 6, z], [1, L, 1], TOOTH)
        m.cube("jaw", [8.5, 6, z], [1, L, 1], TOOTH)
    m.cube("jaw", [-3, 6, -46], [2, 6, 1], TOOTH)                               # the two great front fangs
    m.cube("jaw", [1, 6, -46], [2, 6, 1], TOOTH)
    # the tendril fringe under the jaw (the reference's tentacle beard)
    for i, x in enumerate((-8, -3, 3, 8)):
        b = f"tendril{i}"
        m.bone(b, [x, 1, -30 + abs(x)], "jaw", rotation=[20, 0, x * 1.5])
        m.cube(b, [x - 1, -9, -31 + abs(x)], [2, 10, 2], SK)
        m.bone(b + "_tip", [x, -9, -30 + abs(x)], b, rotation=[15, 0, 0])
        m.cube(b + "_tip", [x - 0.5, -17, -30.5 + abs(x)], [1, 8, 1], SK)
        m.cube(b + "_tip", [x - 0.5, -18, -30.5 + abs(x)], [1, 1, 1], LT)
    # ---- form 1: the CRUST and the ancients' chains
    m.bone("crust", [0, 12, 0], "skull")
    if form == 1:
        CR = crust_mat()
        m.cube("crust", [-13, 20, -26], [26, 3, 30], CR)
        m.cube("crust", [-14, 10, -24], [2, 9, 18], CR)
        m.cube("crust", [12, 10, -24], [2, 9, 18], CR)
        m.cube("crust", [-10, 16, -46], [20, 3, 18], CR)
        m.cube("crust", [-12, 19, -2], [24, 3, 16], CR)
        CH = noise(["#5a5e64", "#6a6e74", "#4a4e54"])
        for x in (-12, 12):
            for k in range(5):
                m.cube("crust", [x - 0.5, 3 - k * 2, 4 + k], [1, 2, 1], CH)
    # ---- form 3: the split crown, wreckage and the Bane Shaft
    m.bone("crown", [0, 20, -10], "skull")
    m.bone("shaft", [0, 20, -10], "crown")
    if form == 3:
        WOOD = noise(["#3a2a1a", "#4a3622", "#2e2014"])
        m.cube("crown", [-9, 20, -18], [2, 16, 2], WOOD, rot=[0, 0, 20], pivot=[-8, 20, -17])     # masts impaled on its crown
        m.cube("crown", [7, 20, -8], [2, 13, 2], WOOD, rot=[-15, 0, -25], pivot=[8, 20, -7])
        m.cube("crown", [-4, 20, 2], [2, 10, 2], WOOD, rot=[25, 0, 10], pivot=[-3, 20, 3])
        m.cube("crown", [-11, 32, -18], [6, 1, 3], noise(["#d8d0c0", "#c8c0b0"]), rot=[0, 0, 20], pivot=[-8, 32, -17])   # a scrap of sail
        m.cube("crown", [4, 24, -14], [4, 4, 4], noise(["#b8902a", "#c8a03a", "#a8801e"]))    # a harbour bell
        m.cube("crown", [5, 22, -13], [2, 2, 2], noise(["#b8902a", "#a8801e"]))
        m.cube("shaft", [-1, 20, -30], [2, 2, 40], noise(["#5a4a3a", "#6a5a44"]), rot=[-12, 0, 0], pivot=[0, 21, -10])   # THE BANE SHAFT
        m.cube("shaft", [-2, 19, -34], [4, 4, 4], noise(["#8a8e94", "#9aa0a6"]), rot=[-12, 0, 0], pivot=[0, 21, -10])
    head_anims(m)
    return m


def head_anims(m):
    tend = [f"tendril{i}" for i in range(4)]

    def sway(length, amp):
        d = {}
        for i, t in enumerate(tend):
            d[t] = {"rotation": wave(length, lambda q, i=i: [S(q + i) * amp, 0, S(q * 0.5 + i) * amp * 0.4])}
            d[t + "_tip"] = {"rotation": wave(length, lambda q, i=i: [S(q + i + 1) * amp * 1.3, 0, 0])}
        return d

    idle = {**sway(3.0, 10), "jaw": {"rotation": wave(3.0, lambda q: [3 + S(q) * 3, 0, 0])},
            "skull": {"rotation": wave(3.0, lambda q: [S(q) * 2, S(q * 0.5) * 3, 0])}}
    anim(m, "idle", 3.0, idle)
    anim(m, "move", 2.0, {**sway(2.0, 14), "skull": {"rotation": wave(2.0, lambda q: [0, S(q) * 6, S(q) * 2])},
                          "jaw": {"rotation": wave(2.0, lambda q: [2 + S(q * 2) * 2, 0, 0])}})
    anim(m, "swim", 2.0, {**sway(2.0, 14)})
    bite = {"jaw": {"rotation": keys((0, [0, 0, 0]), (0.2, [38, 0, 0]), (0.35, [-6, 0, 0]), (0.6, [0, 0, 0]))},
            "skull": {"rotation": keys((0, [0, 0, 0]), (0.2, [-10, 0, 0]), (0.35, [8, 0, 0]), (0.6, [0, 0, 0]))}}
    anim(m, "attack", 0.6, bite, loop=False)
    roar = {"jaw": {"rotation": keys((0, [0, 0, 0]), (0.3, [45, 0, 0]), (1.6, [48, 0, 0]), (2.0, [0, 0, 0]))},
            "skull": {"rotation": keys((0, [0, 0, 0]), (0.3, [-22, 0, 0]), (1.6, [-24, 0, 0]), (2.0, [0, 0, 0]))},
            **{t: {"rotation": keys((0, [0, 0, 0]), (0.3, [-40, 0, 0]), (1.6, [-40, 0, 0]), (2.0, [0, 0, 0]))} for t in tend}}
    anim(m, "roar", 2.0, roar, loop=False)
    anim(m, "special", 2.0, roar, loop=False)
    anim(m, "yawn", 2.2, {"jaw": {"rotation": keys((0, [0, 0, 0]), (0.8, [55, 0, 0]), (1.6, [55, 0, 0]), (2.2, [0, 0, 0]))},
                          "skull": {"rotation": keys((0, [0, 0, 0]), (0.8, [-15, 0, 0]), (2.2, [0, 0, 0]))}}, loop=False)
    anim(m, "breach", 1.6, {"jaw": {"rotation": keys((0, [0, 0, 0]), (0.2, [60, 0, 0]), (1.1, [60, 0, 0]), (1.4, [0, 0, 0]), (1.6, [0, 0, 0]))},
                            "skull": {"rotation": keys((0, [0, 0, 0]), (0.2, [-30, 0, 0]), (1.1, [-30, 0, 0]), (1.6, [0, 0, 0]))}}, loop=False)
    anim(m, "rake", 0.8, {"skull": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, -35, 0]), (0.5, [0, 30, 0]), (0.8, [0, 0, 0]))},
                          **{t: {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 40]), (0.5, [0, 0, -40]), (0.8, [0, 0, 0]))} for t in tend}}, loop=False)
    anim(m, "gulp", 2.5, {"jaw": {"rotation": keys((0, [0, 0, 0]), (0.3, [50, 0, 0]), (2.3, [50, 0, 0]), (2.5, [0, 0, 0]))}}, loop=False)
    anim(m, "spit", 0.7, {"jaw": {"rotation": keys((0, [50, 0, 0]), (0.15, [20, 0, 0]), (0.3, [55, 0, 0]), (0.7, [0, 0, 0]))},
                          "skull": {"rotation": keys((0, [0, 0, 0]), (0.15, [15, 0, 0]), (0.3, [-12, 0, 0]), (0.7, [0, 0, 0]))}}, loop=False)
    anim(m, "lure", 3.0, {**sway(1.0, 25), "jaw": {"rotation": keys((0, [0, 0, 0]), (0.5, [12, 0, 0]), (3.0, [0, 0, 0]))}}, loop=False)
    anim(m, "rear", 1.5, {"neck": {"rotation": keys((0, [0, 0, 0]), (0.6, [-40, 0, 0]), (1.5, [-35, 0, 0]))},
                          "jaw": {"rotation": keys((0, [0, 0, 0]), (0.6, [50, 0, 0]), (1.5, [30, 0, 0]))}}, loop=False)
    anim(m, "reared", 3.0, {"neck": {"rotation": wave(3.0, lambda q: [-35 + S(q) * 3, S(q * 0.5) * 5, 0])},
                            "jaw": {"rotation": wave(3.0, lambda q: [30 + S(q * 2) * 10, 0, 0])}, **sway(3.0, 18)})
    anim(m, "pinned", 1.0, {"neck": {"rotation": wave(1.0, lambda q: [-30 + S(q * 3) * 6, S(q * 5) * 10, S(q * 4) * 6], samples=20)},
                            "jaw": {"rotation": wave(1.0, lambda q: [40 + S(q * 3) * 12, 0, 0], samples=20)}, **sway(1.0, 30)})
    anim(m, "stunned", 2.0, {"jaw": {"rotation": wave(2.0, lambda q: [45 + S(q) * 4, 0, 0])},
                             "skull": {"rotation": wave(2.0, lambda q: [S(q) * 4, 0, 12 + S(q * 0.5) * 4])}, **sway(2.0, 6)})
    anim(m, "flinch", 0.4, {"skull": {"rotation": keys((0, [0, 0, 0]), (0.1, [-8, 6, 4]), (0.4, [0, 0, 0]))}}, loop=False)
    anim(m, "dive", 1.0, {"skull": {"rotation": keys((0, [0, 0, 0]), (0.5, [20, 0, 0]), (1.0, [0, 0, 0]))},
                          "jaw": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, 0]))}}, loop=False)


# =====================================================================================
# BODY SEGMENT + TAIL
# =====================================================================================
def body(seed, form=2):
    m = Rig(f"leviathan_body_p{form}", 256, 256, seed)
    SK, BL = skin(form), belly(form)
    m.bone("root", [0, 0, 0])
    m.bone("seg", [0, 9, 0], "root")
    m.cube("seg", [-11, 1, -14], [22, 17, 28], SK)
    m.cube("seg", [-9, 0, -12], [18, 1, 24], BL)
    for z in (-10, -2, 6):                                                      # flank lights
        m.cube("seg", [-11.5, 9, z], [0.5, 1, 3], lights(form))
        m.cube("seg", [11, 9, z], [0.5, 1, 3], lights(form))
    for z in (-9, 3):                                                          # dorsal spines, raked back
        m.cube("seg", [-2, 18, z], [4, 12, 4], spine_mat(form), rot=[-32, 0, 0], pivot=[0, 18, z + 2])
        m.cube("seg", [-0.5, 22, z + 4], [1, 1, 1], lights(form), rot=[-32, 0, 0], pivot=[0, 18, z + 2])
    # the tendril fringe hanging from its flanks
    for i, (x, z) in enumerate(((-11, -6), (11, -6), (-11, 8), (11, 8))):
        b = f"fringe{i}"
        m.bone(b, [x, 3, z], "seg", rotation=[10, 0, -25 if x < 0 else 25])
        m.cube(b, [x - 1, -9, z - 1], [2, 12, 2], SK)
        m.cube(b, [x - 0.5, -10, z - 0.5], [1, 1, 1], lights(form))
    m.bone("crust", [0, 9, 0], "seg")
    if form == 1:
        CR = crust_mat()
        m.cube("crust", [-12, 18, -13], [24, 3, 26], CR)
        m.cube("crust", [-13, 5, -12], [2, 12, 22], CR)
        m.cube("crust", [11, 5, -11], [2, 12, 20], CR)
        CH = noise(["#5a5e64", "#6a6e74", "#4a4e54"])
        for k in range(8):                                                     # a chain wrapped round it
            m.cube("crust", [-13 + k * 3.25, 21, 0], [2, 1, 2], CH)
    if form == 3:                                                              # split open: ribs through a burning crack
        RIB = noise(BONE)
        m.cube("seg", [-2, 18, -13], [4, 1, 26], lit("#ff4a1a"))
        for z in (-12, -6, 0, 6, 11):
            m.cube("seg", [-6, 18, z], [3, 3, 1], RIB)
            m.cube("seg", [3, 18, z], [3, 3, 1], RIB)
    body_anims(m, ["seg"], [f"fringe{i}" for i in range(4)])
    return m


def tail(seed, form=2):
    m = Rig(f"leviathan_tail_p{form}", 256, 256, seed)
    SK = skin(form)
    m.bone("root", [0, 0, 0])
    m.bone("seg", [0, 8, -12], "root")
    m.cube("seg", [-9, 1, -14], [18, 14, 14], SK)
    m.bone("tail1", [0, 8, 0], "seg")
    m.cube("tail1", [-6, 3, 0], [12, 10, 14], SK)
    m.cube("tail1", [-1.5, 13, 3], [3, 8, 3], spine_mat(form), rot=[-30, 0, 0], pivot=[0, 13, 4.5])
    m.bone("tail2", [0, 8, 14], "tail1")
    m.cube("tail2", [-4, 5, 14], [8, 6, 14], SK)
    m.cube("tail2", [-4.5, 7, 18], [0.5, 1, 3], lights(form))
    m.cube("tail2", [4, 7, 18], [0.5, 1, 3], lights(form))
    m.bone("fluke", [0, 8, 28], "tail2")
    FL = over(noise(["#0e0e14", "#16161e"]), veins("#ff2020" if form >= 2 else "#6a1414", 0.05, glow=True))
    m.cube("fluke", [-14, 7, 28], [28, 2, 8], FL)
    m.cube("fluke", [-10, 7, 36], [20, 2, 4], FL)
    for x in (-14, -7, 7, 13):
        m.cube("fluke", [x, 7.5, 36 if abs(x) > 8 else 40], [1, 1, 5], spine_mat(form))
    m.bone("crust", [0, 8, 0], "seg")
    if form == 1:
        m.cube("crust", [-10, 15, -13], [20, 2, 12], crust_mat())
    wag = {"tail1": {"rotation": wave(2.0, lambda q: [0, S(q) * 12, 0])},
           "tail2": {"rotation": wave(2.0, lambda q: [0, S(q - 0.8) * 18, 0])},
           "fluke": {"rotation": wave(2.0, lambda q: [S(q - 1.4) * 10, S(q - 1.6) * 14, 0])}}
    for n in ("idle", "move", "swim"):
        anim(m, n, 2.0, wag)
    return m


def body_anims(m, core, fringe):
    def sw(length, amp):
        d = {f: {"rotation": wave(length, lambda q, i=i: [S(q + i) * amp, 0, S(q * 0.5 + i) * amp * 0.5])} for i, f in enumerate(fringe)}
        d["seg"] = {"rotation": wave(length, lambda q: [0, 0, S(q) * 3])}
        return d
    anim(m, "idle", 3.0, sw(3.0, 10))
    anim(m, "move", 2.0, sw(2.0, 16))
    anim(m, "swim", 2.0, sw(2.0, 16))


# =====================================================================================
# THE WAKE - the fin that breaks the surface while it flees
# =====================================================================================
def fin(seed):
    m = Rig("leviathan_fin", 256, 128, seed)
    SK = skin(2)
    m.bone("root", [0, 0, 0])
    m.bone("ridge", [0, 0, 0], "root")
    m.cube("ridge", [-8, -4, -30], [16, 6, 60], SK)
    for i, z in enumerate(range(-26, 28, 9)):
        h = 18 - abs(i - 3) * 3
        m.cube("ridge", [-2, 2, z], [4, h, 4], spine_mat(2), rot=[-28, 0, 0], pivot=[0, 2, z + 2])
        m.cube("ridge", [-0.5, 2 + h - 2, z + 5], [1, 1, 1], lit("#ff2a2a"), rot=[-28, 0, 0], pivot=[0, 2, z + 2])
    for z in range(-26, 28, 6):
        m.cube("ridge", [-8.5, 0, z], [0.5, 1, 2], lit("#ff2a2a"))
        m.cube("ridge", [8, 0, z], [0.5, 1, 2], lit("#ff2a2a"))
    for n in ("idle", "move", "swim"):
        anim(m, n, 3.0, {"ridge": {"position": wave(3.0, lambda q: [0, S(q) * 1.2, 0]), "rotation": wave(3.0, lambda q: [S(q) * 2, 0, S(q * 0.5) * 3])}})
    return m


MOBS = {}
for _f in (1, 2, 3):
    MOBS[f"leviathan_head_p{_f}"] = (lambda seed, f=_f: head(seed, f))
    MOBS[f"leviathan_body_p{_f}"] = (lambda seed, f=_f: body(seed, f))
    MOBS[f"leviathan_tail_p{_f}"] = (lambda seed, f=_f: tail(seed, f))
MOBS["leviathan_fin"] = fin
SKINS = {"leviathan_head_p1": {"dark": lambda seed: head(seed, 1, True)}}
