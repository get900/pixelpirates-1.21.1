"""MOLTEN WARLORD (boss 3/10) - rebuilt 2026-09-29.

A hulking plate-armoured warlord (~2 blocks of model x renderScale 2.4 = ~5 blocks in game): horned great-helm
with glowing visor slits, spiked pauldrons, heavy gauntlets (the left knuckled with magma), belt + tassets,
sabatons, back spikes - and a CHAIN MACE: handle in the right fist, chain root bone `chain` (hangs forward-down
at rest), four link bones `link1..4` and the spiked ball `mace_head`. The lash clip translates every link and
the ball out along the chain (in the chain bone's frame) so the chain visibly stretches ~11 blocks.

Three skins, one geometry (tools/gen_mob_roster.py SKINS verifies the UVs):
  normal   - black iron split by glowing orange lava cracks
  enraged  - "Molten Core": armour glowing white-hot, cracks everywhere
  quenched - cooled to dead grey stone, no glow at all (the glowmask is simply absent)
Clips: idle, move, attack, lash, whirl, slam, hook, meteors, forge_call, core_vent, molten_core (enrage), quenched.
"""
from mobkit import A, Rig, lit, noise, over, solid, spots, veins, drips, anim, keys, wave, S


def molten_warlord(seed, skin="normal"):
    m = Rig("molten_warlord", 128, 128, seed)
    if skin == "quenched":
        ARM = ["#4a4a48", "#565654", "#62625e", "#6e6e6a"]
        armor = lambda: over(noise(ARM), veins("#2e2e2c", 0.05, glow=False))
        trim = noise(["#5e5e5a", "#6e6e6a"])
        eye = "#2a2a2a"
        magma = noise(["#3a3a38", "#484846"])
        ball = over(noise(["#3e3e3c", "#4a4a48"]), veins("#2a2a28", 0.06, glow=False))
    elif skin == "enraged":
        ARM = ["#3a1a10", "#4a2012", "#5a2814", "#6a3016"]
        armor = lambda: over(noise(ARM), veins("#ffe08a", 0.12, glow=True, length=(3, 8)), spots("#ffb040", 0.05, glow=True))
        trim = over(noise(["#b8702a", "#d88a36"]), spots("#ffe08a", 0.1, glow=True))
        eye = "!#ffffff"
        magma = lit("#ffd070")
        ball = over(lit("#ff8a2a"), spots("#3a1a10", 0.2))
    else:
        ARM = ["#1c1818", "#262120", "#302a28", "#3a3230"]
        armor = lambda: over(noise(ARM), veins("#ff6a1a", 0.05, glow=True, length=(3, 7)))
        trim = noise(["#6a4420", "#7a4e24", "#8a5a28"])
        eye = "!#ffae3a"
        magma = over(noise(["#3a1a10", "#4a2012"]), spots("#ff8a2a", 0.3, glow=True))
        ball = over(noise(["#201c1c", "#2a2424"]), veins("#ff6a1a", 0.08, glow=True, length=(2, 4)))

    m.bone("root", [0, 0, 0])
    # ------------------------------------------------------------------ legs: greave + knee plate + sabaton
    for side, x0, pv in (("rleg", -5, -2.5), ("lleg", 0, 2.5)):
        m.bone(side, [pv, 12, 0], "root")
        m.cube(side, [x0, 3, -2.5], [5, 9, 5], armor())
        m.cube(side, [x0, 6, -3.5], [5, 2, 1], trim)                                        # knee plate
        m.cube(side, [x0 - 0.5, 0, -3.5], [6, 3, 7], armor())                               # sabaton
    # ------------------------------------------------------------------ torso
    m.bone("body", [0, 12, 0], "root")
    V = dict(c=ARM[1], G="#8a5a28", g="#6a4420", m="!#ff8a2a" if skin != "quenched" else "#3a3a38")
    m.cube("body", [-5, 12, -3], [10, 12, 6], armor(),
           art={"north": A("""
cGGGGGGGGc
cGccmmccGc
cGcmmmmcGc
cGccmmccGc
cGGccccGGc
ccGGGGGGcc
cccccccccc
cccccccccc
cccccccccc
cccccccccc
cccccccccc
cccccccccc""", V)})
    m.cube("body", [-5, 12, -3], [10, 2, 6], trim, inflate=0.5)                            # war belt
    m.cube("body", [-1, 12, -4.6], [2, 2, 1], solid("#c8962a") if skin == "normal" else trim, inflate=0.2)   # buckle
    m.cube("body", [-4, 7, -4.2], [8, 5, 1], armor())                                      # tassets: front
    m.cube("body", [-4, 7, 3.2], [8, 5, 1], armor())                                       # back
    m.pair("body", "body", [-6.2, 7, -2.5], [1, 5, 5], armor())                            # sides
    for i, y in enumerate((16, 19, 22)):                                                     # back spikes
        m.cube("body", [-0.5, y, 3], [1, 2, 2 + i % 2], trim, rot=[-35, 0, 0], pivot=[0, y, 3])
    # ------------------------------------------------------------------ head: horned great-helm
    m.bone("head", [0, 24, 0], "body")
    H = dict(h=ARM[2], d="#0c0a0a", E=eye, t=ARM[0], g="#8a5a28")
    m.cube("head", [-4, 24, -4], [8, 9, 8], armor(),
           art={"north": A("""
hhhhhhhh
hggggggh
hhhhhhhh
hEEddEEh
hddddddh
hhhdhhhh
hdhdhdhh
hhhhhhhh
hhhhhhhh""", H)})
    m.cube("head", [-4, 32, -4], [8, 1, 8], trim, inflate=0.3)                              # crown rim
    m.pair("head", "head", [-6, 28, -1.5], [2, 3, 3], trim)          # stepped horns: base -> bend -> tip
    m.pair("head", "head", [-8, 30, -1], [2, 3, 2], armor())
    m.pair("head", "head", [-8, 33, -0.5], [1, 4, 1], trim)
    # ------------------------------------------------------------------ arms: pauldrons + gauntlets
    m.bone("rarm", [-7.5, 22, 0], "body")
    m.cube("rarm", [-10, 12, -2.5], [5, 12, 5], armor())
    m.cube("rarm", [-11, 21, -3.5], [7, 5, 7], armor())                                    # pauldron
    m.cube("rarm", [-10.5, 11, -3], [6, 5, 6], armor())                                    # gauntlet
    m.bone("larm", [7.5, 22, 0], "body")
    m.cube("larm", [5, 12, -2.5], [5, 12, 5], armor())
    m.cube("larm", [4, 21, -3.5], [7, 5, 7], armor())
    m.cube("larm", [4.5, 11, -3], [6, 5, 6], armor())
    m.cube("larm", [4.5, 11, -4], [6, 2, 1], magma)                                        # magma knuckles
    for sx, bone, x in ((-1, "rarm", -8), (1, "larm", 7)):                                  # pauldron spikes
        for k, z in enumerate((-2, 1)):
            m.cube(bone, [x, 26, z], [1, 3, 1], trim, rot=[0, 0, -sx * 20], pivot=[x + 0.5, 26, z + 0.5])
    # ------------------------------------------------------------------ the chain mace
    m.bone("rhand", [-7.5, 13, 0], "rarm")
    m.cube("rhand", [-8.5, 12, -9], [2, 2, 8], noise(["#3a2616", "#4a3220"]))              # haft, held forward
    m.cube("rhand", [-9, 11.5, -10], [3, 3, 1], trim)                                       # iron cap
    m.bone("chain", [-7.5, 13, -10], "rhand", rotation=[55, 0, 0])
    LINK = noise(["#2e2a2a", "#3a3434", "#4a4444"]) if skin != "quenched" else noise(["#555553", "#61615f"])
    for k in range(1, 5):
        m.bone(f"link{k}", [-7.5, 13, -10 - 2.5 * k], "chain")
        z0 = -10 - 2.5 * k - 1
        if k % 2:
            m.cube(f"link{k}", [-8, 12.5, z0], [1, 1, 3], LINK)
        else:
            m.cube(f"link{k}", [-8.5, 13, z0], [2, 1, 3], LINK, inflate=-0.1)
    m.bone("mace_head", [-7.5, 13, -21], "chain")
    m.cube("mace_head", [-10.5, 10, -26], [6, 6, 6], ball)
    for pos, size in (([-8, 12, -27], [1, 2, 1]), ([-8, 16, -24], [1, 1, 2]), ([-8, 9, -24], [1, 1, 2]),
                      ([-11.5, 12, -24], [1, 2, 2]), ([-4.5, 12, -24], [1, 2, 2]), ([-8, 12, -20], [1, 2, 1])):
        m.cube("mace_head", pos, size, trim)                                                # spikes on every face

    animations(m)
    return m


SW = -35     # mace arm carried forward
LA = -8


def ext(z):
    """Positions for link1..4 + the ball when the chain is stretched so the ball sits z px further out."""
    return {f"link{k}": z * k / 5 for k in range(1, 5)} | {"mace_head": z}


def chain_keys(frames):
    """frames: [(t, ball_z_offset)] -> position channels for every link + ball."""
    out = {}
    for name in [f"link{k}" for k in range(1, 5)] + ["mace_head"]:
        out[name] = {"position": keys(*[(t, [0, 0, ext(z)[name]]) for t, z in frames])}
    return out


def animations(m):
    anim(m, "idle", 3.0, {
        "body": {"rotation": wave(3.0, lambda q: [S(q) * 1.5, 0, 0])},
        "head": {"rotation": wave(3.0, lambda q: [S(q) * 2, S(q * 0.5) * 8, 0])},
        "rarm": {"rotation": wave(3.0, lambda q: [SW + S(q) * 2, 0, 4])},
        "larm": {"rotation": wave(3.0, lambda q: [LA - S(q) * 2, 0, -5])},
        "chain": {"rotation": wave(3.0, lambda q: [S(q) * 5, 0, S(q * 0.5) * 4])},
    })
    anim(m, "move", 1.2, {
        "rleg": {"rotation": wave(1.2, lambda q: [-S(q) * 24, 0, 0])},
        "lleg": {"rotation": wave(1.2, lambda q: [S(q) * 24, 0, 0])},
        "body": {"rotation": wave(1.2, lambda q: [4, S(q) * 3, S(q) * 2]), "position": wave(1.2, lambda q: [0, -abs(S(q)) * 0.8, 0])},
        "rarm": {"rotation": wave(1.2, lambda q: [SW + S(q) * 10, 0, 4])},
        "larm": {"rotation": wave(1.2, lambda q: [LA - S(q) * 18, 0, -5])},
        "chain": {"rotation": wave(1.2, lambda q: [S(q) * 14, 0, 0])},
        "head": {"rotation": wave(1.2, lambda q: [-3, -S(q) * 3, 0])},
    })
    # overhead mace smash (short range melee)
    anim(m, "attack", 0.8, {
        "rarm": {"rotation": keys((0, [SW, 0, 0]), (0.3, [-160, 0, 10]), (0.45, [-30, 0, 0]), (0.8, [SW, 0, 0]))},
        "chain": {"rotation": keys((0, [0, 0, 0]), (0.3, [-120, 0, 0]), (0.45, [30, 0, 0]), (0.8, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-10, 10, 0]), (0.45, [14, -8, 0]), (0.8, [0, 0, 0]))},
    }, loop=False)
    # MACE LASH: wind back 0.1-0.6, the chain shoots out at 0.7 (14 ticks) along the telegraphed line
    lash = {"rarm": {"rotation": keys((0, [SW, 0, 0]), (0.5, [-140, 0, 25]), (0.7, [-85, 0, 0]), (0.9, [-85, 0, 0]), (1.2, [SW, 0, 0]))},
            "chain": {"rotation": keys((0, [0, 0, 0]), (0.5, [-120, 0, 0]), (0.7, [35, 0, 0]), (0.95, [35, 0, 0]), (1.2, [0, 0, 0]))},
            "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [-8, 25, 0]), (0.7, [10, -10, 0]), (1.2, [0, 0, 0]))}}
    lash.update(chain_keys([(0, 0), (0.62, 0), (0.72, -70), (0.9, -70), (1.15, 0), (1.2, 0)]))
    anim(m, "lash", 1.2, lash, loop=False)
    # FLAIL WHIRL: arm overhead, the chain stretched and spinning (hits at 0.9 and 1.3)
    whirl = {"rarm": {"rotation": keys((0, [SW, 0, 0]), (0.3, [-170, 0, 5]), (1.4, [-170, 0, 5]), (1.5, [SW, 0, 0]))},
             "chain": {"rotation": keys((0, [0, 0, 0]), (0.3, [-55, 0, 0]), (0.6, [-55, 360, 0]),
                                        (0.9, [-55, 720, 0]), (1.2, [-55, 1080, 0]), (1.4, [-55, 1260, 0]), (1.5, [0, 0, 0]))},
             "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-6, 0, 0]), (1.4, [-6, 0, 0]), (1.5, [0, 0, 0]))}}
    whirl.update(chain_keys([(0, 0), (0.3, -20), (1.4, -20), (1.5, 0)]))
    anim(m, "whirl", 1.5, whirl, loop=False)
    # FISSURE SLAM: both fists and the mace come down at 0.7
    anim(m, "slam", 1.2, {
        "rarm": {"rotation": keys((0, [SW, 0, 0]), (0.4, [-170, 0, 10]), (0.7, [-40, 0, 0]), (1.2, [SW, 0, 0]))},
        "larm": {"rotation": keys((0, [LA, 0, 0]), (0.4, [-170, 0, -10]), (0.7, [-40, 0, 0]), (1.2, [LA, 0, 0]))},
        "chain": {"rotation": keys((0, [0, 0, 0]), (0.4, [-150, 0, 0]), (0.7, [20, 0, 0]), (1.2, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [-12, 0, 0]), (0.7, [22, 0, 0]), (1.2, [0, 0, 0]))},
        "root": {"position": keys((0, [0, 0, 0]), (0.4, [0, 1, 0]), (0.7, [0, -1, 0]), (1.2, [0, 0, 0]))}}, loop=False)
    # CHAIN HOOK: a quick throw of the chain (grab at 0.5) and a heave back
    hook = {"rarm": {"rotation": keys((0, [SW, 0, 0]), (0.35, [-90, 0, 0]), (0.5, [-90, 0, 0]), (0.8, [-10, 0, 20]), (1.0, [SW, 0, 0]))},
            "chain": {"rotation": keys((0, [0, 0, 0]), (0.35, [35, 0, 0]), (0.8, [20, 0, 0]), (1.0, [0, 0, 0]))},
            "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [6, 0, 0]), (0.8, [-12, 0, 0]), (1.0, [0, 0, 0]))}}
    hook.update(chain_keys([(0, 0), (0.3, 0), (0.45, -55), (0.55, -55), (0.85, -5), (1.0, 0)]))
    anim(m, "hook", 1.0, hook, loop=False)
    anim(m, "meteors", 1.3, {
        "larm": {"rotation": keys((0, [LA, 0, 0]), (0.4, [-175, 0, -10]), (1.0, [-175, 0, -10]), (1.3, [LA, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-30, 0, 0]), (1.0, [-30, 0, 0]), (1.3, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [-8, 0, 0]), (1.0, [-8, 0, 0]), (1.3, [0, 0, 0]))}}, loop=False)
    anim(m, "forge_call", 1.1, {
        "rarm": {"rotation": keys((0, [SW, 0, 0]), (0.4, [-60, 0, 60]), (0.8, [-60, 0, 60]), (1.1, [SW, 0, 0]))},
        "larm": {"rotation": keys((0, [LA, 0, 0]), (0.4, [-60, 0, -60]), (0.8, [-60, 0, -60]), (1.1, [LA, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-25, 0, 0]), (0.8, [-25, 0, 0]), (1.1, [0, 0, 0]))}}, loop=False)
    anim(m, "core_vent", 1.1, {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [18, 0, 0]), (0.6, [-12, 0, 0]), (1.1, [0, 0, 0]))},
        "rarm": {"rotation": keys((0, [SW, 0, 0]), (0.3, [-20, 0, 10]), (0.6, [-40, 0, 55]), (1.1, [SW, 0, 0]))},
        "larm": {"rotation": keys((0, [LA, 0, 0]), (0.3, [-20, 0, -10]), (0.6, [-40, 0, -55]), (1.1, [LA, 0, 0]))}}, loop=False)
    # MOLTEN CORE (enrage): drop to a knee, then rise roaring with arms thrown wide
    anim(m, "molten_core", 1.8, {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.4, [28, 0, 0]), (0.9, [-20, 0, 0]), (1.4, [-20, 0, 0]), (1.8, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (0.4, [0, -3, 0]), (0.9, [0, 0, 0]), (1.8, [0, 0, 0]))},
        "rleg": {"rotation": keys((0, [0, 0, 0]), (0.4, [-50, 0, 0]), (0.9, [0, 0, 0]), (1.8, [0, 0, 0]))},
        "lleg": {"rotation": keys((0, [0, 0, 0]), (0.4, [30, 0, 0]), (0.9, [0, 0, 0]), (1.8, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [25, 0, 0]), (0.9, [-35, 0, 0]), (1.4, [-35, 0, 0]), (1.8, [0, 0, 0]))},
        "rarm": {"rotation": keys((0, [SW, 0, 0]), (0.4, [-10, 0, 10]), (0.9, [-70, 0, 70]), (1.4, [-70, 0, 70]), (1.8, [SW, 0, 0]))},
        "larm": {"rotation": keys((0, [LA, 0, 0]), (0.4, [-10, 0, -10]), (0.9, [-70, 0, -70]), (1.4, [-70, 0, -70]), (1.8, [LA, 0, 0]))}}, loop=False)
    # QUENCHED: steam-choked, he staggers to one knee, head bowed, then hauls himself up
    anim(m, "quenched", 2.0, {
        "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [30, 0, 8]), (1.6, [30, 0, 8]), (2.0, [0, 0, 0])),
                 "position": keys((0, [0, 0, 0]), (0.3, [0, -4, 0]), (1.6, [0, -4, 0]), (2.0, [0, 0, 0]))},
        "rleg": {"rotation": keys((0, [0, 0, 0]), (0.3, [-70, 0, 0]), (1.6, [-70, 0, 0]), (2.0, [0, 0, 0]))},
        "lleg": {"rotation": keys((0, [0, 0, 0]), (0.3, [40, 0, 0]), (1.6, [40, 0, 0]), (2.0, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [30, 0, 0]), (1.6, [30, 0, 0]), (2.0, [0, 0, 0]))},
        "rarm": {"rotation": keys((0, [SW, 0, 0]), (0.3, [-10, 0, 15]), (1.6, [-10, 0, 15]), (2.0, [SW, 0, 0]))},
        "larm": {"rotation": keys((0, [LA, 0, 0]), (0.3, [-50, 0, -10]), (1.6, [-50, 0, -10]), (2.0, [LA, 0, 0]))},
        "chain": {"rotation": keys((0, [0, 0, 0]), (0.3, [35, 0, 0]), (1.6, [35, 0, 0]), (2.0, [0, 0, 0]))}}, loop=False)
