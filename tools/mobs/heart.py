"""THE TITAN'S CHEST cast (boss 9/10, 2026-09-30) - designed here, no concept render.

  abyssal_heart   THE HEART OF THALASSAR: a colossal anatomical heart (~64 px x renderScale 2.0 = 8 blocks), apex down,
                  hanging in the world's arteries (they plug into its sides; the aorta rises from its crown into the
                  sternum). Crimson muscle webbed with glowing coronary veins and pale fat, crusted with abyssal barnacles,
                  and girdled by the ancients' two iron SEAL BANDS carved with cyan runes. Bones: body > ventricles,
                  atrium_r/_l, aorta, pulmonary, vena, bands. Skins: normal, enraged (ruptured - cracks burn gold),
                  arrest (stopped - violet-grey, electric veins), clotted (black clot crust), flatline (drained blue-grey),
                  stone (the Heartstone). Clips: idle, move, attack, beat / jolt / triple (the pulse controller), systole,
                  diastole, lash, bleed, clot, clotted (loop), rupture (enrage), arrest, arrest_still (loop), flatline,
                  flat (loop), exhale, petrify.
  rival_eye       THE EYE OF THALASSAR (~56 px x 3.4 = 12 blocks): a vast eyeball in a ring of flesh, heavy lids that
                  slide shut, six tendrils; the IRIS bone is posed in code to follow each client's player (never animate
                  it). Skins: normal, dead. Clips: sleep / stir / watch / dead (loops), open, blink, flinch, glare, charge,
                  scream, idle, move, attack.
  heart_node      a node of the conduction system: a glowing sac in a cage of muscle fibres, rooted to the floor.
  blood_clot      a lump of clotted blood that hops.
  embolism        a spiked, rolling clot.
  heart_phantasm  a drowned pirate hallucination (translucent, Rig.alpha).
  drowned_keeper  THE LAST KEEPER: a hooded ancient in a kelp-grown robe with a tide lantern (translucent). Clips + talk, fade.
"""
from mobkit import A, Rig, lit, noise, over, solid, spots, speckle, veins, drips, anim, keys, wave, S
from mobs.common import biped, biped_anims

# ---------------------------------------------------------------------------------------------- the heart's skins
SKINS_HEART = {
    "normal": dict(mus=["#4a0a12", "#5e1018", "#741a20", "#8a2428"], vein="#ff8a3a", vglow=True, vd=0.05, fat="#d8b070",
                   band=["#2a2e34", "#343a42", "#3e464e"], rune="!#5ff0e0", barn=["#6a6a60", "#7a7a6e", "#8a8a7c"]),
    "enraged": dict(mus=["#6a0c10", "#8a1414", "#a82020", "#c02a24"], vein="#ffd060", vglow=True, vd=0.09, fat="#ffb040",
                    band=["#3a2a24", "#4a3228", "#5a3a2c"], rune="!#ffb040", barn=["#7a5a4a", "#8a6a58", "#9a7a66"]),
    "arrest": dict(mus=["#3a2a36", "#4a3444", "#584050", "#665060"], vein="#8ad8ff", vglow=True, vd=0.03, fat="#9a90a0",
                   band=["#2a2e34", "#343a42", "#3e464e"], rune="!#bff4ff", barn=["#5a5a60", "#6a6a70", "#7a7a80"]),
    "clotted": dict(mus=["#2a0608", "#3a0a0c", "#4a0e10", "#5a1216"], vein="#a02010", vglow=True, vd=0.015, fat="#120204",
                    band=["#22262c", "#2a2e34", "#343a42"], rune="!#2a8a80", barn=["#3a2a28", "#4a3432", "#5a3e3a"]),
    "flatline": dict(mus=["#4a4a58", "#565868", "#626676", "#6e7484"], vein="#6ae0ff", vglow=True, vd=0.012, fat="#8a8e9a",
                     band=["#2a2e34", "#343a42", "#3e464e"], rune="!#6ae0ff", barn=["#6a6e76", "#767a82", "#82868e"]),
    "stone": dict(mus=["#5a5a54", "#66665e", "#727268", "#7e7e72"], vein="#4a4a44", vglow=False, vd=0.03, fat="#8a8a80",
                  band=["#4a4a46", "#545450", "#5e5e5a"], rune="#6a6a64", barn=["#6e6e66", "#7a7a72", "#86867e"]),
}


def heart_mats(skin):
    k = SKINS_HEART[skin]
    mus = over(noise(k["mus"]), veins(k["vein"], k["vd"], glow=k["vglow"], length=(3, 9)), spots(k["fat"], 0.05, size=2),
               speckle(k["mus"][0], 0.06))
    if skin == "clotted":
        mus = over(mus, spots("#0a0102", 0.18, size=2))
    if skin == "enraged":
        mus = over(mus, veins("#fff0a0", 0.02, glow=True, length=(2, 5)))
    atr = over(noise([k["mus"][1], k["mus"][2], k["mus"][3]]), veins(k["vein"], k["vd"] * 0.7, glow=k["vglow"], length=(2, 6)))
    vessel = over(noise([k["mus"][2], k["mus"][3], k["fat"]]), speckle(k["mus"][0], 0.08))
    band = over(noise(k["band"]), spots("#5a3a1e", 0.08))
    barn = over(noise(k["barn"]), spots("#2a2a28", 0.3))
    return mus, atr, vessel, band, barn, k["rune"]


def abyssal_heart(seed, skin="normal"):
    m = Rig("abyssal_heart", 256, 256, seed)
    MUS, ATR, VES, BAND, BARN, RUNE = heart_mats(skin)
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 30, 0], "root")
    # ------------------------------------------------------------------ the ventricles: a great cone, apex down (toward its left)
    m.bone("ventricles", [0, 28, 0], "body")
    slabs = [([0, 0, -3], [6, 3, 6]), ([-4, 3, -6], [12, 4, 12]), ([-8, 7, -9], [18, 5, 17]), ([-11, 12, -11], [23, 6, 21]),
             ([-13, 18, -12], [26, 8, 24]), ([-14, 26, -13], [28, 8, 26]), ([-14, 34, -12], [27, 6, 24]), ([-12, 40, -10], [23, 4, 20])]
    groove = lambda w, h: A.at(w, h, {(w // 2 + (1 if y % 5 == 0 else 0), y): "g" for y in range(h)}, g="!" + SKINS_HEART[skin]["vein"].lstrip("!")
                               if SKINS_HEART[skin]["vglow"] else SKINS_HEART[skin]["vein"])
    for o, s in slabs:
        m.cube("ventricles", o, s, MUS, art={"north": groove(s[0], s[1])})
    # barnacle crusts and a pale scar where the ancients cut it free
    for (x, y, z, w, h, d) in ((-15, 28, -4, 1, 3, 3), (13, 20, 2, 2, 2, 3), (-10, 36, 12, 3, 2, 1), (6, 10, 8, 2, 2, 1), (-9, 14, -12, 3, 2, 1)):
        m.cube("ventricles", [x, y, z], [w, h, d], BARN)
    # ------------------------------------------------------------------ the ancients' SEAL BANDS: iron hoops with glowing runes
    m.bone("bands", [0, 30, 0], "ventricles")
    rune = lambda w, h: A.at(w, h, {(x, h // 2): "r" for x in range(1, w - 1) if x % 4 in (1, 2)}, r=RUNE)
    m.cube("bands", [-15, 24, -14], [30, 2, 28], BAND, art={s: rune(30 if s in ("north", "south") else 28, 2) for s in ("north", "south", "east", "west")})
    m.cube("bands", [-15, 36, -13], [29, 2, 26], BAND, art={s: rune(29 if s in ("north", "south") else 26, 2) for s in ("north", "south", "east", "west")})
    # ------------------------------------------------------------------ atria + auricles on its crown
    m.bone("atrium_r", [-8, 44, 2], "body")
    m.cube("atrium_r", [-14, 42, -4], [11, 8, 12], ATR)
    m.cube("atrium_r", [-18, 44, -8], [5, 5, 6], ATR, rot=[0, 0, 20], pivot=[-15, 46, -5])
    m.bone("atrium_l", [8, 46, 4], "body")
    m.cube("atrium_l", [3, 43, -1], [11, 8, 12], ATR)
    m.cube("atrium_l", [13, 46, -6], [5, 4, 5], ATR, rot=[0, 0, -18], pivot=[14, 47, -4])
    # ------------------------------------------------------------------ the great vessels
    m.bone("aorta", [1, 46, 1], "body")
    m.cube("aorta", [-2, 44, -2], [7, 14, 7], VES)
    m.cube("aorta", [-3, 58, -4], [9, 6, 10], VES)                                    # the arch, up into the world's aorta
    m.bone("pulmonary", [-4, 46, -6], "body")
    m.cube("pulmonary", [-7, 44, -10], [6, 10, 6], VES, rot=[-16, 0, 0], pivot=[-4, 44, -7])
    m.bone("vena", [-15, 40, 5], "body")
    m.cube("vena", [-21, 34, 2], [6, 12, 6], VES, rot=[0, 0, 14], pivot=[-18, 40, 5])
    heart_anims(m)
    return m


def heart_anims(m):
    V, B = "ventricles", "body"
    anim(m, "idle", 4.0, {B: {"rotation": wave(4.0, lambda q: [S(q) * 1.5, S(q * 0.5) * 2, S(q) * 1.0])},
                          "aorta": {"rotation": wave(4.0, lambda q: [S(q + 1) * 2, 0, S(q) * 2])},
                          "vena": {"rotation": wave(4.0, lambda q: [0, 0, S(q + 2) * 3])}})
    anim(m, "move", 4.0, {B: {"rotation": wave(4.0, lambda q: [S(q) * 1.5, S(q * 0.5) * 2, S(q) * 1.0])}})

    def beat(scale_lo, dur):
        return {V: {"scale": keys((0, [1, 1, 1]), (dur * 0.16, [scale_lo, scale_lo + 0.02, scale_lo]), (dur * 0.32, [1.05, 1.04, 1.05]),
                                  (dur * 0.52, [1 - (1 - scale_lo) * 0.5] * 3), (dur * 0.72, [1.02, 1.02, 1.02]), (dur, [1, 1, 1]))},
                "atrium_r": {"scale": keys((0, [1, 1, 1]), (dur * 0.12, [1.12, 1.12, 1.12]), (dur * 0.28, [0.92, 0.92, 0.92]), (dur * 0.6, [1.05, 1.05, 1.05]), (dur, [1, 1, 1]))},
                "atrium_l": {"scale": keys((0, [1, 1, 1]), (dur * 0.12, [1.12, 1.12, 1.12]), (dur * 0.28, [0.92, 0.92, 0.92]), (dur * 0.6, [1.05, 1.05, 1.05]), (dur, [1, 1, 1]))},
                "aorta": {"rotation": keys((0, [0, 0, 0]), (dur * 0.2, [-4, 0, 3]), (dur * 0.5, [2, 0, -2]), (dur, [0, 0, 0]))},
                B: {"position": keys((0, [0, 0, 0]), (dur * 0.16, [0, 0.8, 0]), (dur * 0.4, [0, -0.4, 0]), (dur, [0, 0, 0]))}}
    anim(m, "beat", 0.5, beat(0.9, 0.5), loop=False)
    anim(m, "attack", 0.5, beat(0.88, 0.5), loop=False)
    anim(m, "triple", 0.25, beat(0.92, 0.25), loop=False)
    jolt = beat(0.84, 0.45)
    jolt[B]["rotation"] = keys((0, [0, 0, 0]), (0.05, [6, 0, -5]), (0.1, [-5, 0, 6]), (0.16, [4, 0, -3]), (0.25, [-2, 0, 2]), (0.45, [0, 0, 0]))
    anim(m, "jolt", 0.45, jolt, loop=False)
    # SYSTOLE: swells, drawing the water in (lands on the next beat as DIASTOLE)
    anim(m, "systole", 1.5, {V: {"scale": keys((0, [1, 1, 1]), (0.4, [1.14, 1.1, 1.14]), (1.3, [1.17, 1.12, 1.17]), (1.5, [1.12, 1.1, 1.12]))},
                             "atrium_r": {"scale": keys((0, [1, 1, 1]), (0.4, [1.2, 1.2, 1.2]), (1.5, [1.2, 1.2, 1.2]))},
                             "atrium_l": {"scale": keys((0, [1, 1, 1]), (0.4, [1.2, 1.2, 1.2]), (1.5, [1.2, 1.2, 1.2]))}}, loop=False)
    anim(m, "diastole", 0.6, {V: {"scale": keys((0, [1.15, 1.1, 1.15]), (0.08, [0.78, 0.82, 0.78]), (0.3, [1.04, 1.04, 1.04]), (0.6, [1, 1, 1]))},
                              B: {"position": keys((0, [0, 0, 0]), (0.08, [0, 2, 0]), (0.6, [0, 0, 0]))}}, loop=False)
    # VEIN LASH: the great vessels wrench about
    anim(m, "lash", 1.0, {"aorta": {"rotation": keys((0, [0, 0, 0]), (0.3, [-14, 0, 10]), (0.6, [12, 0, -12]), (1.0, [0, 0, 0]))},
                          "pulmonary": {"rotation": keys((0, [0, 0, 0]), (0.3, [16, 0, -8]), (0.6, [-10, 0, 10]), (1.0, [0, 0, 0]))},
                          "vena": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 0, 18]), (0.6, [0, 0, -10]), (1.0, [0, 0, 0]))},
                          V: {"scale": keys((0, [1, 1, 1]), (0.3, [1.06, 1.02, 1.06]), (1.0, [1, 1, 1]))}}, loop=False)
    shudder = wave(1.0, lambda q: [S(q * 6) * 3, 0, S(q * 7 + 1) * 3], samples=24)
    anim(m, "bleed", 1.0, {B: {"rotation": shudder}, V: {"scale": keys((0, [1, 1, 1]), (0.5, [0.94, 0.96, 0.94]), (1.0, [1, 1, 1]))}}, loop=False)
    anim(m, "clot", 2.5, {V: {"scale": keys((0, [1, 1, 1]), (0.6, [0.84, 0.86, 0.84]), (2.5, [0.88, 0.88, 0.88]))},
                          B: {"rotation": wave(2.5, lambda q: [S(q * 8) * 2, 0, S(q * 9) * 2], samples=30)}}, loop=False)
    anim(m, "clotted", 2.0, {V: {"scale": wave(2.0, lambda q: [0.88 + S(q * 3) * 0.008] * 3)},
                             "atrium_r": {"scale": wave(2.0, lambda q: [0.86] * 3)}, "atrium_l": {"scale": wave(2.0, lambda q: [0.86] * 3)}})
    anim(m, "rupture", 1.5, {V: {"scale": keys((0, [0.88, 0.88, 0.88]), (0.45, [1.25, 1.2, 1.25]), (0.6, [1.1, 1.08, 1.1]), (1.5, [1, 1, 1]))},
                             B: {"rotation": wave(1.5, lambda q: [S(q * 5) * 5, 0, S(q * 6) * 5], samples=24)}}, loop=False)
    anim(m, "arrest", 1.0, {B: {"rotation": keys((0, [0, 0, 0]), (0.1, [6, 0, -6]), (0.2, [-5, 0, 4]), (0.35, [3, 0, -2]), (1.0, [0, 0, 0]))},
                            V: {"scale": keys((0, [1, 1, 1]), (0.1, [0.9, 0.9, 0.9]), (1.0, [0.97, 0.97, 0.97]))}}, loop=False)
    anim(m, "arrest_still", 2.0, {V: {"scale": keys((0, [0.97, 0.97, 0.97]), (2.0, [0.97, 0.97, 0.97]))},
                                  B: {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.05, [1.5, 0, -1]), (1.15, [0, 0, 0]), (2.0, [0, 0, 0]))}})
    anim(m, "flatline", 1.5, {B: {"rotation": keys((0, [0, 0, 0]), (1.5, [8, 0, 4]))}, V: {"scale": keys((0, [1, 1, 1]), (1.5, [0.95, 0.95, 0.95]))}}, loop=False)
    anim(m, "flat", 3.0, {B: {"rotation": wave(3.0, lambda q: [8 + S(q) * 0.6, 0, 4])}, V: {"scale": wave(3.0, lambda q: [0.95] * 3)}})
    anim(m, "petrify", 1.0, {B: {"rotation": keys((0, [0, 0, 0]), (0.15, [3, 0, -3]), (1.0, [0, 0, 0]))},
                             V: {"scale": keys((0, [1, 1, 1]), (0.2, [1.04, 1.04, 1.04]), (1.0, [1.02, 1.02, 1.02]))}}, loop=False)


HEART_SKINS = {s: (lambda seed, s=s: abyssal_heart(seed, s)) for s in SKINS_HEART if s != "normal"}


# =====================================================================================
# THE EYE OF THALASSAR
# =====================================================================================
def rival_eye(seed, skin="normal"):
    dead = skin == "dead"
    m = Rig("rival_eye", 256, 256, seed)
    SCL = over(noise(["#9a9888", "#a8a494", "#b4b0a0"] if dead else ["#c8ccb0", "#d4d8bc", "#dfe2c8"]),
               veins("#6a3a34" if dead else "#b0283a", 0.05, glow=False, length=(4, 10)), speckle("#8a8a78", 0.04))
    FLESH = over(noise(["#3a2a2a", "#4a3434", "#583e3c"] if dead else ["#4a0e16", "#5e141c", "#721c22"]),
                 veins("#3a4a4a" if dead else "#3ae0d0", 0.03, glow=not dead, length=(2, 6)), speckle("#2a0a0e", 0.08))
    LID = over(noise(["#3e2e2e", "#4a3838"] if dead else ["#521620", "#662028", "#7a2830"]), speckle("#2a0a0e", 0.1))
    RIM = noise(["#1a0e10", "#241416"])
    m.bone("root", [0, 0, 0])
    # the socket's ring of flesh (the lids retract into it)
    m.bone("socket", [0, 24, 0], "root")
    m.cube("socket", [-18, 44, -8], [36, 12, 14], FLESH)                  # brow (the upper lid hides in it)
    m.cube("socket", [-18, -6, -8], [36, 10, 14], FLESH)                  # cheek (the lower lid hides in it)
    m.cube("socket", [-24, 10, -7], [6, 28, 13], FLESH)                   # the corners of the eye
    m.cube("socket", [18, 10, -7], [6, 28, 13], FLESH)
    for (x, y) in ((-23, 38), (17, 38), (-23, 4), (17, 4)):               # rounded corner pads
        m.cube("socket", [x, y, -6], [6, 6, 12], FLESH)
    # the eyeball: a rounded front of stepped layers
    m.bone("ball", [0, 24, 6], "root")
    m.cube("ball", [-20, 4, 0], [40, 40, 14], SCL)
    # a rounded front: crossed layers stepping forward (each 1 px proud of the one it crosses - no shared planes)
    m.cube("ball", [-18, 9, -2], [36, 30, 2], SCL)
    m.cube("ball", [-15, 6, -3], [30, 36, 2], SCL)
    m.cube("ball", [-14, 12, -4], [28, 24, 1], SCL)
    m.cube("ball", [-12, 10, -5], [24, 28, 1], SCL)
    # the iris - posed per client to follow the viewer (GlowingMobRenderer.rivalEye); pivot 17 px behind its face
    m.bone("iris", [0, 24, 11], "ball")
    ring = {}
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d > 7.9:
                ring[(x, y)] = "_"
            elif d > 6.6:
                ring[(x, y)] = "k"
            elif d > 4.2:
                ring[(x, y)] = "a" if (x * 3 + y) % 4 else "b"
            else:
                ring[(x, y)] = "c"
    iris_art = A.at(16, 16, ring, k="#0a1414", a="#4a6a66" if dead else "!#2ad8c8", b="#3a5452" if dead else "!#e8c040",
                    c="#2a3a38" if dead else "!#7affee")
    m.cube("iris", [-8, 16, -6], [16, 16, 1], noise(["#0a1414", "#0e1a1a"]), art={"north": iris_art})
    m.bone("pupil", [0, 24, -6], "iris")
    m.cube("pupil", [-1, 17, -7], [2, 14, 1], solid("#262a2a" if dead else "#030606"))
    # the lids (rest = SHUT; the clips slide them apart)
    m.bone("lid_top", [0, 24, -8], "root")
    m.cube("lid_top", [-19, 24, -9], [38, 22, 3], LID)
    m.cube("lid_top", [-18, 24, -10], [36, 2, 1], RIM)
    m.bone("lid_bot", [0, 24, -8], "root")
    m.cube("lid_bot", [-19, 2, -9], [38, 22, 3], LID)
    m.cube("lid_bot", [-18, 22, -10], [36, 2, 1], RIM)
    # six tendrils reaching out of the socket
    tips = "#3a4a4a" if dead else "!#3ae0d0"
    for i, (x, y, rz) in enumerate(((-26, 44, 40), (26, 44, -40), (-28, 20, 80), (28, 20, -80), (-24, 0, 130), (24, 0, -130))):
        b = f"tend{i}"
        m.bone(b, [x, y, -2], "socket", rotation=[0, 0, rz])
        m.cube(b, [x - 2, y, -4], [4, 10, 4], FLESH)
        m.bone(b + "_tip", [x, y + 10, -2], b, rotation=[15, 0, 0])
        m.cube(b + "_tip", [x - 1.5, y + 10, -3.5], [3, 8, 3], FLESH)
        m.cube(b + "_tip", [x - 1, y + 18, -3], [2, 2, 2], lit(tips.lstrip("!")) if not dead else solid(tips))
    eye_anims(m)
    return m


def eye_anims(m):
    tend = [f"tend{i}" for i in range(6)]

    def writhe(length, amp):
        d = {}
        for i, t in enumerate(tend):
            d[t] = {"rotation": wave(length, lambda q, i=i: [S(q + i) * amp, 0, S(q * 0.7 + i * 1.3) * amp * 0.6])}
            d[t + "_tip"] = {"rotation": wave(length, lambda q, i=i: [S(q + i + 1) * amp * 1.2, 0, 0])}
        return d

    lids = lambda top, bot, length: {"lid_top": {"position": keys((0, [0, top, 0]), (length, [0, top, 0]))},
                                     "lid_bot": {"position": keys((0, [0, bot, 0]), (length, [0, bot, 0]))}}
    breathe = {"ball": {"scale": wave(4.0, lambda q: [1 + S(q) * 0.01] * 3)}}
    anim(m, "sleep", 4.0, {**writhe(4.0, 3), **lids(0, 0, 4.0), **breathe})
    anim(m, "idle", 4.0, {**writhe(4.0, 3), **lids(0, 0, 4.0), **breathe})
    anim(m, "move", 4.0, {**writhe(4.0, 3), **lids(0, 0, 4.0)})
    anim(m, "stir", 3.0, {**writhe(3.0, 6),
                          "lid_top": {"position": keys((0, [0, 0, 0]), (0.4, [0, 3, 0]), (0.7, [0, 1, 0]), (1.6, [0, 1, 0]), (1.9, [0, 6, 0]),
                                                       (2.3, [0, 2, 0]), (3.0, [0, 0, 0]))},
                          "lid_bot": {"position": keys((0, [0, 0, 0]), (0.4, [0, -2, 0]), (0.7, [0, -1, 0]), (1.9, [0, -4, 0]), (2.3, [0, -1, 0]), (3.0, [0, 0, 0]))}})
    anim(m, "watch", 4.0, {**writhe(4.0, 8),
                           "lid_top": {"position": wave(4.0, lambda q: [0, 16 + S(q) * 1.0, 0])},
                           "lid_bot": {"position": wave(4.0, lambda q: [0, -16 - S(q) * 1.0, 0])},
                           "pupil": {"scale": wave(4.0, lambda q: [1 + 0.25 * S(q * 2), 1, 1])}})
    anim(m, "dead", 4.0, {**lids(9, -7, 4.0), "pupil": {"scale": keys((0, [1.8, 1, 1]), (4.0, [1.8, 1, 1]))}})
    anim(m, "open", 1.2, {"lid_top": {"position": keys((0, [0, 0, 0]), (0.25, [0, 21, 0]), (1.2, [0, 16, 0]))},
                          "lid_bot": {"position": keys((0, [0, 0, 0]), (0.25, [0, -21, 0]), (1.2, [0, -16, 0]))},
                          "pupil": {"scale": keys((0, [2.4, 1, 1]), (0.4, [0.5, 1, 1]), (1.2, [1, 1, 1]))},
                          "ball": {"rotation": wave(1.2, lambda q: [S(q * 5) * 2, S(q * 4) * 2, 0], samples=20)}}, loop=False)
    anim(m, "blink", 0.4, {"lid_top": {"position": keys((0, [0, 16, 0]), (0.12, [0, 0, 0]), (0.18, [0, 0, 0]), (0.4, [0, 16, 0]))},
                           "lid_bot": {"position": keys((0, [0, -16, 0]), (0.12, [0, 0, 0]), (0.18, [0, 0, 0]), (0.4, [0, -16, 0]))}}, loop=False)
    anim(m, "flinch", 0.5, {"lid_top": {"position": keys((0, [0, 16, 0]), (0.08, [0, 6, 0]), (0.5, [0, 16, 0]))},
                            "lid_bot": {"position": keys((0, [0, -16, 0]), (0.08, [0, -6, 0]), (0.5, [0, -16, 0]))},
                            "pupil": {"scale": keys((0, [1, 1, 1]), (0.08, [0.45, 1, 1]), (0.5, [1, 1, 1]))},
                            "ball": {"rotation": keys((0, [0, 0, 0]), (0.06, [3, -4, 0]), (0.14, [-2, 3, 0]), (0.5, [0, 0, 0]))}}, loop=False)
    anim(m, "glare", 1.2, {"lid_top": {"position": keys((0, [0, 16, 0]), (0.2, [0, 8, 0]), (1.0, [0, 8, 0]), (1.2, [0, 16, 0]))},
                           "lid_bot": {"position": keys((0, [0, -16, 0]), (0.2, [0, -9, 0]), (1.0, [0, -9, 0]), (1.2, [0, -16, 0]))},
                           "pupil": {"scale": keys((0, [1, 1, 1]), (0.2, [0.4, 1, 1]), (1.0, [0.4, 1, 1]), (1.2, [1, 1, 1]))}}, loop=False)
    ch = writhe(0.6, 4)
    for i, t in enumerate(tend):
        ch[t] = {"rotation": keys((0, [0, 0, 0]), (0.3, [-35, 0, 0]), (0.6, [0, 0, 0]))}
    ch.update({"lid_top": {"position": keys((0, [0, 16, 0]), (0.3, [0, 19, 0]), (0.6, [0, 16, 0]))},
               "lid_bot": {"position": keys((0, [0, -16, 0]), (0.3, [0, -19, 0]), (0.6, [0, -16, 0]))},
               "ball": {"scale": keys((0, [1, 1, 1]), (0.3, [1.06, 1.06, 1.06]), (0.6, [1, 1, 1]))}})
    anim(m, "charge", 0.6, ch, loop=False)
    sc = writhe(3.5, 25)
    sc.update({"lid_top": {"position": keys((0, [0, 16, 0]), (0.2, [0, 22, 0]), (3.2, [0, 22, 0]), (3.5, [0, 8, 0]))},
               "lid_bot": {"position": keys((0, [0, -16, 0]), (0.2, [0, -22, 0]), (3.2, [0, -22, 0]), (3.5, [0, -6, 0]))},
               "pupil": {"scale": keys((0, [1, 1, 1]), (0.3, [2.6, 1, 1]), (3.5, [1.8, 1, 1]))},
               "ball": {"rotation": wave(3.5, lambda q: [S(q * 22) * 3, S(q * 19 + 1) * 3, S(q * 17) * 2], samples=70)}})
    anim(m, "scream", 3.5, sc, loop=False)
    anim(m, "attack", 0.4, {"ball": {"scale": keys((0, [1, 1, 1]), (0.2, [1.03, 1.03, 1.03]), (0.4, [1, 1, 1]))}}, loop=False)


EYE_SKINS = {"dead": lambda seed: rival_eye(seed, "dead")}


# =====================================================================================
# HEART NODE / BLOOD CLOT / EMBOLISM
# =====================================================================================
FIBRE = over(noise(["#5a0e14", "#6e141a", "#821c22"]), speckle("#3a080c", 0.1))


def heart_node(seed):
    m = Rig("heart_node", 128, 128, seed)
    CORE = over(lit("#9aff6a"), spots("#e8ffb0", 0.1, glow=True))
    m.bone("root", [0, 0, 0])
    for i, (x, z, ry) in enumerate(((0, -6, 0), (6, 0, 90), (0, 6, 180), (-6, 0, 270))):      # roots over the floor
        m.bone(f"root{i}", [x * 0.5, 1, z * 0.5], "root", rotation=[0, ry, 0])
        m.cube(f"root{i}", [-1.5, 0, -10], [3, 2, 8], FIBRE)
    m.cube("root", [-4, 0, -4], [8, 5, 8], FIBRE)
    m.bone("sac", [0, 15, 0], "root")
    m.cube("sac", [-6, 6, -6], [12, 14, 12], CORE)
    m.cube("sac", [-4, 20, -4], [8, 3, 8], FIBRE)
    m.cube("sac", [-1.5, 23, -1.5], [3, 9, 3], FIBRE)                                            # its vessel, up into the ceiling
    import math
    fib = []
    for i in range(6):
        a = i * math.pi / 3
        x, z = math.cos(a) * 7.5, math.sin(a) * 7.5
        b = f"fibre{i}"
        m.bone(b, [x, 5, z], "sac")
        m.cube(b, [x - 1, 5, z - 1], [2, 17, 2], FIBRE)
        fib.append((b, math.cos(a), math.sin(a)))
    anim(m, "idle", 2.0, {"sac": {"scale": wave(2.0, lambda q: [1 + S(q) * 0.05] * 3)}})
    anim(m, "move", 2.0, {"sac": {"scale": wave(2.0, lambda q: [1 + S(q) * 0.05] * 3)}})
    anim(m, "attack", 0.4, {"sac": {"scale": keys((0, [1, 1, 1]), (0.15, [1.1, 1.1, 1.1]), (0.4, [1, 1, 1]))}}, loop=False)
    anim(m, "pulse", 0.4, {"sac": {"scale": keys((0, [1, 1, 1]), (0.08, [1.25, 1.2, 1.25]), (0.4, [1, 1, 1]))}}, loop=False)
    cl = {"sac": {"scale": keys((0, [1, 1, 1]), (0.1, [0.8, 0.85, 0.8]), (0.5, [1, 1, 1]))}}
    for b, ca, sa in fib:                                                                      # the fibres flex inward
        cl[b] = {"rotation": keys((0, [0, 0, 0]), (0.1, [sa * 14, 0, -ca * 14]), (0.5, [0, 0, 0]))}
    anim(m, "clench", 0.5, cl, loop=False)
    return m


CLOT = over(noise(["#2a0406", "#3a060a", "#4a0a0e", "#5a0e12"]), veins("#ff3a2a", 0.05, glow=True, length=(2, 5)), spots("#e8b0b0", 0.03))


def blood_clot(seed):
    m = Rig("blood_clot", 64, 64, seed)
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 0, 0], "root")
    m.cube("body", [-6, 0, -6], [12, 7, 12], CLOT)
    m.cube("body", [-4, 7, -5], [9, 4, 8], CLOT)
    m.cube("body", [-8, 1, -2], [2, 4, 5], CLOT)
    m.cube("body", [6, 2, -3], [2, 3, 4], CLOT)
    m.cube("body", [-2, 11, -2], [3, 2, 3], CLOT)
    m.cube("body", [-3, 2, -7], [5, 3, 1], CLOT, art={"north": A.at(5, 3, {(1, 1): "e", (3, 1): "e"}, e="!#ffd0a0")})
    anim(m, "idle", 1.6, {"body": {"scale": wave(1.6, lambda q: [1 + S(q) * 0.04, 1 - S(q) * 0.05, 1 + S(q) * 0.04])}})
    anim(m, "move", 0.6, {"body": {"position": wave(0.6, lambda q: [0, max(0, S(q)) * 3, 0]),
                                   "scale": wave(0.6, lambda q: [1 - S(q) * 0.1, 1 + S(q) * 0.15, 1 - S(q) * 0.1])}})
    anim(m, "attack", 0.4, {"body": {"position": keys((0, [0, 0, 0]), (0.15, [0, 2, -4]), (0.4, [0, 0, 0])),
                                     "scale": keys((0, [1, 1, 1]), (0.15, [0.85, 1.2, 0.85]), (0.4, [1, 1, 1]))}}, loop=False)
    return m


def embolism(seed):
    m = Rig("embolism", 128, 128, seed)
    m.bone("root", [0, 0, 0])
    m.bone("ball", [0, 10, 0], "root")
    m.cube("ball", [-8, 2, -8], [16, 16, 16], CLOT)
    m.cube("ball", [-6, 18, -6], [12, 2, 12], CLOT)
    m.cube("ball", [-6, 0, -6], [12, 2, 12], CLOT)
    FIB = over(noise(["#d8a8a8", "#c89898", "#e8c0c0"]), speckle("#a06060", 0.2))
    for (o, s) in (([-2, 8, -11], [4, 4, 3]), ([-2, 8, 8], [4, 4, 3]), ([-11, 8, -2], [3, 4, 4]), ([8, 8, -2], [3, 4, 4]),
                   ([-1, 20, -1], [2, 3, 2]), ([-5, 14, -10], [2, 2, 2]), ([4, 5, 8], [2, 2, 2]), ([8, 13, 3], [2, 2, 2])):
        m.cube("ball", o, s, FIB)
    anim(m, "idle", 1.5, {"ball": {"rotation": keys((0, [0, 0, 0]), (0.75, [180, 0, 20]), (1.5, [360, 0, 0]))}})
    anim(m, "move", 1.5, {"ball": {"rotation": keys((0, [0, 0, 0]), (0.75, [180, 0, 20]), (1.5, [360, 0, 0]))}})
    anim(m, "attack", 0.4, {"ball": {"scale": keys((0, [1, 1, 1]), (0.15, [1.2, 1.2, 1.2]), (0.4, [1, 1, 1]))}}, loop=False)
    return m


# =====================================================================================
# the ghosts: PHANTASM + THE LAST KEEPER (translucent)
# =====================================================================================
def heart_phantasm(seed):
    m = Rig("heart_phantasm", 128, 128, seed)
    m.alpha = 150
    SKIN = over(noise(["#6a9a9a", "#7aaaa8", "#8ab8b4"]), speckle("#4a7a7a", 0.1))
    COAT = over(noise(["#1e3a44", "#26464e", "#2e5058"]), drips("#0e2226", 0.3), spots("#4a8a8a", 0.05))
    LEG = over(noise(["#2a3a3a", "#324444"]), drips("#0e1a1a", 0.4))
    face = A("""
........
........
.ee..ee.
........
...nn...
..mmmm..
..m..m..
........""", e="!#9ff4ff", n="#4a7070", m="#0a1a1a")
    k = biped(m, head=SKIN, body=COAT, arm=COAT, leg=LEG, art={"head": {"north": face}})
    top = k["top"]
    HAT = noise(["#141e22", "#1a262a"])
    m.cube("head", [-6, top + 8, -6], [12, 1, 12], HAT)                                   # tricorn brim
    m.cube("head", [-4, top + 9, -4], [8, 3, 8], HAT)
    m.bone("coat", [0, 12, 2], "body")
    m.cube("coat", [-4, 3, 2], [8, 9, 1], COAT)                                            # tattered tails
    BLADE = noise(["#7a9a9a", "#8aaaa8"])
    m.cube("rhand", [k["rx"] - 0.5, k["hand_y"] - 2, -8], [1, 2, 10], BLADE)             # a ghost cutlass
    biped_anims(m, style="sword", legs=False, cape="coat")
    return m


def drowned_keeper(seed):
    m = Rig("drowned_keeper", 128, 128, seed)
    m.alpha = 175
    ROBE = over(noise(["#1a3a3a", "#224646", "#2a5250"]), drips("#0a1e1e", 0.3), spots("#3ae0d0", 0.02, glow=True))
    SKIN = over(noise(["#7ab0a8", "#88bcb4"]), speckle("#5a8a84", 0.1))
    KELP = over(noise(["#2a5a2a", "#326a30", "#3a7a38"]), speckle("#1a3a1a", 0.2))
    face = A("""
hhhhhhhh
hhhhhhhh
h.ee.eeh
h......h
hkkkkkkh
hkkkkkkh
.kkkkkk.
..kkkk..""", h="#163232", e="!#9ff4ff", k="#2e6a2e")
    k = biped(m, head=SKIN, body=ROBE, arm=ROBE, leg=ROBE, art={"head": {"north": face}})
    top = k["top"]
    m.cube("head", [-5, top, -3], [10, 9, 8], ROBE, inflate=0.25)                         # the hood (behind the face)
    m.cube("head", [-5, top + 7, -5], [10, 2, 2], ROBE)                                   # its brow
    m.cube("head", [-5, top, -5], [1, 7, 2], ROBE)                                        # and cheeks
    m.cube("head", [4, top, -5], [1, 7, 2], ROBE)
    m.cube("body", [-5, 0, -3], [10, 12, 6], ROBE)                                        # the robe's skirt over the legs
    m.cube("head", [-3, top - 6, -5], [6, 6, 1], KELP)                                    # the kelp beard
    # the tide lantern on a staff in his left hand
    STAFF = noise(["#3a2a1a", "#4a3622"])
    m.cube("lhand", [k["lx"] - 1, k["hand_y"] - 10, -1], [2, 30, 2], STAFF)
    m.cube("lhand", [k["lx"] - 2.5, k["hand_y"] + 20, -2.5], [5, 6, 5], lit("#6ae0ff"))
    m.cube("lhand", [k["lx"] - 3, k["hand_y"] + 26, -3], [6, 1, 6], noise(["#8a6a2a", "#a07a30"]))
    biped_anims(m, style="sword", legs=False,
                extra_idle={"larm": {"rotation": wave(3.0, lambda q: [-20 + S(q) * 2, 0, -6])}},
                extra_move={"larm": {"rotation": wave(1.0, lambda q: [-20, 0, -6])}})
    anim(m, "talk", 2.0, {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-60, 0, 20]), (1.0, [-40, 20, 30]), (1.5, [-60, -10, 15]), (2.0, [0, 0, 0]))},
                          "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-8, 0, 0]), (1.0, [4, 10, 0]), (1.5, [-6, -10, 0]), (2.0, [0, 0, 0]))},
                          "larm": {"rotation": keys((0, [-20, 0, -6]), (2.0, [-20, 0, -6]))}}, loop=False)
    anim(m, "fade", 1.5, {"root": {"position": keys((0, [0, 0, 0]), (1.5, [0, -6, 0])), "scale": keys((0, [1, 1, 1]), (1.5, [0.3, 1.4, 0.3]))}}, loop=False)
    return m


MOBS = {"abyssal_heart": abyssal_heart, "rival_eye": rival_eye, "heart_node": heart_node, "blood_clot": blood_clot,
        "embolism": embolism, "heart_phantasm": heart_phantasm, "drowned_keeper": drowned_keeper}
SKINS = {"abyssal_heart": HEART_SKINS, "rival_eye": EYE_SKINS}
