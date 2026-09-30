"""THE CHAINED REVENANT (boss 8/10) - rebuilt 2026-09-29 from the concept render (phase4/34005a38): a hulking wraith
of drowned stone-grey cloth, hooded and wrapped in chains, teal soul-light eyes and teeth, chains crossed over the
chest, a skirt of hanging chains, a weight dragged on a chain from the right hand, an anchor hook on the left.

The story that drives the fight: a pirate lord drawn and quartered, then HUNG IN CHAINS - only the chains hold him
together. Every joint (shoulders, hips, neck) is a glowing seam of chain-stitching; the torso has glowing SOCKETS
that blaze when a limb is torn away. On top: an iron gibbet collar with a padlock, wrist manacles, and a
ball-and-chain on the right ankle.

Each body part is its own builder (part_*), placed with an offset, so the SAME geometry makes:
  chained_revenant        - the whole body (~44 px x renderScale 2.4 = ~6.6 blocks)
  revenant_arm_r / _l     - the torn-off arms  (RevenantPartEntity, hung on manacle anchors on the walls)
  revenant_leg_r / _l     - the torn-off legs
  revenant_head           - the hopping head
All six share one enraged skin style ("Hanged Wrath": soul-fire bursting through the cloth).
Body clips: idle, move, attack, chain_whip, grave_slam, noose, gibbet, sunder, reform, wrath.
Part clips: idle, attack, fallen (loop), fly, reel; head: idle, hop, bite, caged, fly.
"""
from mobkit import A, Rig, lit, noise, over, solid, spots, speckle, veins, drips, anim, keys, wave, S


def mats(rage=False):
    M = {}
    if rage:
        M["body"] = lambda: over(noise(["#0e1214", "#141a1c", "#1a2124", "#20282b"]), veins("#6fffe8", 0.045, glow=True, length=(3, 8)))
        M["glow"] = "!#aafff0"
        M["eye"] = "!#ffffff"
    else:
        M["body"] = lambda: over(noise(["#15191b", "#1b2023", "#22282b", "#2a3134"]), speckle("#4a3a2a", 0.05), drips("#0e1112", 0.2, maxlen=3))
        M["glow"] = "!#3fe0c0"
        M["eye"] = "!#aafff0"
    M["iron"] = over(noise(["#2a2c30", "#34373c", "#3e4248"]), spots("#6a4a2a", 0.12))
    M["chain"] = noise(["#3a3e44", "#4a4f56", "#5a6068"])
    M["seam"] = lit(M["glow"].lstrip("!"))
    return M


def ring_h(m, bone, M, x, y, z, w, d, t=0.6):
    """A thin horizontal glowing ring (a seam) around a w x d footprint at height y - frame only, no cap."""
    m.cube(bone, [x, y, z], [w, t, t], M["seam"])
    m.cube(bone, [x, y, z + d - t], [w, t, t], M["seam"])
    m.cube(bone, [x + 0.02, y + 0.02, z + t], [t - 0.04, t - 0.04, d - 2 * t], M["seam"])
    m.cube(bone, [x + w - t + 0.02, y + 0.02, z + t], [t - 0.04, t - 0.04, d - 2 * t], M["seam"])


def ring_v(m, bone, M, x, y, z, h, d, t=0.6):
    """A thin vertical glowing ring on an x-facing side: the seam where an arm is chained onto the torso."""
    m.cube(bone, [x, y, z], [t, t, d], M["seam"])
    m.cube(bone, [x, y + h - t, z], [t, t, d], M["seam"])
    m.cube(bone, [x + 0.02, y + t, z + 0.02], [t - 0.04, h - 2 * t, t - 0.04], M["seam"])
    m.cube(bone, [x + 0.02, y + t, z + d - t + 0.02], [t - 0.04, h - 2 * t, t - 0.04], M["seam"])


def link(w, h):
    return A.at(w, h, {(x, y): "l" for x in range(w) for y in range(h) if (x + y) % 3 == 0}, l="#6a7078")


def chain_art(w, h):
    return {"north": link(w, h), "south": link(w, h), "east": link(w, h), "west": link(w, h)}


# =====================================================================================
# parts - each built at an offset (dx, dy, dz); `b` = the bone to hang it on (created by the caller)
# =====================================================================================
def part_leg(m, bone, M, x0, dx=0.0, dy=0.0, ball=False):
    """A leg whose hip joint is at y 14: thigh, boot, chain wraps, ankle shackle, glowing hip seam."""
    X = x0 + dx
    m.cube(bone, [X, 7 + dy, -3.5], [7, 7, 7], M["body"]())
    m.cube(bone, [X - 0.5, 0 + dy, -4], [8, 7, 8], M["body"]())
    m.cube(bone, [X - 0.5, 9.5 + dy, -4], [8, 1.5, 8], M["chain"], art=chain_art(8, 2))
    m.cube(bone, [X - 0.8, 1.5 + dy, -4.3], [8.6, 1.5, 8.6], M["iron"])                     # ankle shackle
    ring_h(m, bone, M, X - 0.25, 13.3 + dy, -3.75, 7.5, 7.5)                                  # the hip seam
    if ball:
        cb = bone + "_ball"
        m.bone(cb, [X - 0.5 + 0, 2 + dy, 1], bone)
        for i in range(3):                                                                    # chain to the ball
            m.cube(cb, [X - 2.5 - i * 2.2, 1.6 + dy, 2 + i * 1.2], [2, 1, 1], M["chain"])
        m.cube(cb, [X - 11, 0 + dy, 3.5], [5, 5, 5], over(noise(["#1e2024", "#26282c"]), spots("#4a4e56", 0.12)))    # the ball
        for (px, py, pz) in ((X - 9, 5 + dy, 5.5), (X - 11.5, 2 + dy, 5.5), (X - 8.5, 2 + dy, 8.5)):
            m.cube(cb, [px, py, pz], [1, 1, 1], M["iron"])                                   # rivets


def part_arm(m, bone, M, ax, sx, dx=0.0, dy=0.0, hand="weight"):
    """An arm whose shoulder joint is at y 31: sleeve, chain wraps, forearm + fist, wrist manacle, seam; then the
    right hand's dragging weight on a chain or the left hand's anchor hook."""
    X = ax + dx
    m.cube(bone, [X, 20 + dy, -3.5], [7, 11, 7], M["body"]())
    m.cube(bone, [X - 0.5, 26 + dy, -4], [8, 1.5, 8], M["chain"], art=chain_art(8, 2))
    m.cube(bone, [X - 0.5, 22 + dy, -4], [8, 1.5, 8], M["chain"], art=chain_art(8, 2))
    ring_v(m, bone, M, (X + 7) if sx < 0 else (X - 0.6), 24 + dy, -3.8, 7.6, 7.6)             # the shoulder seam (inner side)
    m.cube(bone, [X - 0.5, 29 + dy, -4], [8, 4, 8], M["body"]())                              # hunched shoulder mass
    fist = bone + "_fist"
    m.bone(fist, [X + 3.5, 20 + dy, 0], bone)
    m.cube(fist, [X + 0.5, 12 + dy, -3], [6, 8, 6], M["body"]())
    m.cube(fist, [X, 8 + dy, -3.5], [7, 4, 7], M["body"]())
    m.cube(fist, [X - 0.6, 12.5 + dy, -4.1], [8.2, 2, 8.2], M["iron"])                     # wrist manacle
    for k in range(4):                                                                       # claw fingers
        m.cube(fist, [X + 0.5 + k * 1.7, 6.5 + dy, -3.2], [1, 2, 1.5], solid("#c8c0a8"))
    ch = bone + "_chain"
    m.bone(ch, [X + 3.5, 8 + dy, 0], fist)
    if hand == "weight":
        for i in range(3):
            m.cube(ch, [X + 3 - (i % 2) * 0.5, 6 - i * 2 + dy, -0.5], [1 + (i % 2), 2, 1 + ((i + 1) % 2)], M["chain"])
        m.cube(ch, [X + 1, -3.5 + dy, -2.5], [5, 5, 5], over(noise(["#1e2024", "#26282c"]), spots("#4a4e56", 0.12)))   # the weight
    else:
        for i in range(2):
            m.cube(ch, [X + 3 - (i % 2) * 0.5, 6 - i * 2 + dy, -0.5], [1 + (i % 2), 2, 1 + ((i + 1) % 2)], M["chain"])
        HOOK = over(noise(["#3a3e44", "#4a4f56"]), spots("#6a4a2a", 0.15))
        a = X + 3
        barb = a + 3 if sx > 0 else a - 3
        m.cube(ch, [a, -3 + dy, -0.5], [1.5, 5, 1.5], HOOK)                                   # shank
        m.cube(ch, [min(a, barb) + 0.25, -4 + dy, -0.6], [4, 1, 2], HOOK)                    # the bend
        m.cube(ch, [barb + 0.25, -3 + dy, -0.25], [1, 3, 1], HOOK)                               # the barb
        m.cube(ch, [barb + 0.1, -0.5 + dy, -0.4], [1.3, 1, 1.3], solid("#8a9098"))             # its point


def part_head(m, bone, M, dx=0.0, dy=0.0):
    """The hooded skull: hood with a cut-out face, the dark face inside with soul-light eyes and teeth, chains
    wrapped round the hood, the gibbet collar + padlock, the glowing neck seam."""
    face = {(1, 3): "e", (2, 3): "E", (3, 3): "e", (5, 3): "e", (6, 3): "E", (7, 3): "e", (4, 5): "e",
            (2, 7): "t", (3, 7): "t", (5, 7): "t", (6, 7): "t", (4, 7): "t", (3, 6): "t", (5, 6): "t"}
    m.cube(bone, [-4.5 + dx, 31.5 + dy, -6], [9, 9, 8], noise(["#0a0c0e", "#101214", "#16181a"]),
           art={"north": A.at(9, 9, face, e=M["glow"], E=M["eye"], t=M["glow"])})
    m.cube(bone, [-5.5 + dx, 31 + dy, -6.5], [11, 11, 11], M["body"](),
           art={"north": A.at(11, 11, {(x, y): "_" for x in range(1, 10) for y in range(2, 11)})})
    m.cube(bone, [-3 + dx, 42 + dy, -4], [6, 2, 7], M["body"]())                              # hood peak
    m.cube(bone, [-6 + dx, 35 + dy, -7], [12, 1.5, 12], M["chain"], rot=[0, 0, 14], pivot=[0 + dx, 36 + dy, -1], art=chain_art(12, 2))
    m.cube(bone, [-6 + dx, 38.5 + dy, -7], [12, 1.5, 12], M["chain"], rot=[0, 0, -10], pivot=[0 + dx, 39 + dy, -1], art=chain_art(12, 2))
    m.cube(bone, [-5.2 + dx, 29.5 + dy, -6.2], [10.4, 1.5, 10.4], M["iron"])                # gibbet collar
    m.cube(bone, [-1 + dx, 27.2 + dy, -7.4], [2, 2.3, 1], solid("#6a5a2a"))                   # padlock
    ring_h(m, bone, M, -4.6 + dx, 31.0 + dy, -4.6, 9.2, 9.2, t=0.5)                           # the neck seam


# =====================================================================================
# the whole body
# =====================================================================================
def chained_revenant(seed, skin="normal"):
    M = mats(skin == "enraged")
    m = Rig("chained_revenant", 256, 256, seed)
    m.bone("root", [0, 0, 0])
    m.bone("rleg", [-4.5, 14, 0], "root")
    part_leg(m, "rleg", M, -8, ball=True)
    m.bone("lleg", [4.5, 14, 0], "root")
    part_leg(m, "lleg", M, 1)
    m.bone("body", [0, 14, 0], "root")
    m.cube("body", [-8, 11, -4.5], [16, 4, 9], M["body"]())                                  # hips
    for x in range(-7, 8, 2):                                                                 # chain skirt
        h = 5 - (abs(x) % 3)
        m.cube("body", [x - 0.5, 11 - h, -5.4], [1, h, 1], M["chain"], art=chain_art(1, h))
        m.cube("body", [x - 0.5, 11 - h + 1, 4.4], [1, h - 1, 1], M["chain"], art=chain_art(1, h - 1))
    m.bone("chest", [0, 20, 0], "body", rotation=[8, 0, 0])                                   # hunched
    m.cube("chest", [-9, 15, -5], [18, 17, 10], M["body"](),
           art={"north": A.at(18, 17, {(x, 16): "_" for x in (0, 17)})})
    m.cube("chest", [-9.5, 15.5, -5.5], [19, 2, 11], M["chain"], art=chain_art(19, 2))         # chain belt
    m.cube("chest", [-10, 23, -5.7], [20, 2, 1], M["chain"], rot=[0, 0, 30], pivot=[0, 24, -5.5], art={"north": link(20, 2)})
    m.cube("chest", [-10, 23, -5.8], [20, 2, 1], M["chain"], rot=[0, 0, -30], pivot=[0, 24, -5.5], art={"north": link(20, 2)})
    for (y, z) in ((18, 7.2), (22, 7.2), (26, 7.2), (29.5, 7.2)):                             # spine knobs through the cloak
        m.cube("chest", [-1, y, z], [2, 2, 1], solid("#c8c0a8"))
    # glowing SOCKETS where the limbs are chained on (they blaze when a limb is torn away)
    m.pair("chest", "chest", [-9.8, 25.5, -2.5], [0.8, 5, 5], M["seam"])
    m.cube("body", [-6, 10.4, -2.5], [4, 0.6, 5], M["seam"])
    m.cube("body", [2, 10.4, -2.5], [4, 0.6, 5], M["seam"])
    # the back: a curtain of broken chain
    m.bone("cloak", [0, 31, 5], "chest")
    tat = A.at(16, 22, {(x, y): "_" for x in range(16) for y in range(22) if y >= 16 + (x * 7 % 5) - (x % 3)})
    m.cube("cloak", [-8, 10, 5], [16, 22, 1], M["body"](), art={"south": tat, "north": tat})
    m.bone("backchain", [0, 30, 6.2], "chest")
    for x in (-6, -3, 0, 3, 6):
        m.cube("backchain", [x - 0.5, 20 - abs(x), 6.2], [1, 10 + abs(x) // 2, 1], M["chain"], art=chain_art(1, 10))
    m.bone("head", [0, 31, -1], "chest")
    part_head(m, "head", M)
    m.bone("rarm", [-12.5, 31, 0], "chest")
    part_arm(m, "rarm", M, -16, -1, hand="weight")
    m.bone("larm", [12.5, 31, 0], "chest")
    part_arm(m, "larm", M, 9, 1, hand="hook")
    body_anims(m)
    return m


RA, LA = [-6, 0, 4], [-6, 0, -4]


def body_anims(m):
    anim(m, "idle", 4.0, {
        "chest": {"rotation": wave(4.0, lambda q: [S(q) * 2, 0, 0]), "scale": wave(4.0, lambda q: [1 + 0.02 * S(q), 1 + 0.015 * S(q), 1 + 0.02 * S(q)])},
        "head": {"rotation": wave(4.0, lambda q: [S(q) * 3, S(q * 0.5) * 12, S(q * 0.5) * 4])},
        "rarm": {"rotation": wave(4.0, lambda q: [RA[0] + S(q) * 3, 0, RA[2]])},
        "larm": {"rotation": wave(4.0, lambda q: [LA[0] - S(q) * 3, 0, LA[2]])},
        "rarm_chain": {"rotation": wave(4.0, lambda q: [S(q + 0.5) * 8, 0, S(q) * 5])},
        "larm_chain": {"rotation": wave(4.0, lambda q: [S(q - 0.5) * 8, 0, S(q) * 5])},
        "backchain": {"rotation": wave(4.0, lambda q: [6 + S(q) * 4, 0, 0])},
        "cloak": {"rotation": wave(4.0, lambda q: [5 + S(q - 0.5) * 4, 0, 0])},
    })
    anim(m, "move", 1.6, {
        "rleg": {"rotation": wave(1.6, lambda q: [-S(q) * 22, 0, 0])},
        "lleg": {"rotation": wave(1.6, lambda q: [S(q) * 22, 0, 0])},
        "body": {"rotation": wave(1.6, lambda q: [4, S(q) * 4, S(q) * 3]), "position": wave(1.6, lambda q: [0, -abs(S(q)) * 1.0, 0])},
        "rarm": {"rotation": wave(1.6, lambda q: [RA[0] + S(q) * 14, 0, RA[2]])},
        "larm": {"rotation": wave(1.6, lambda q: [LA[0] - S(q) * 14, 0, LA[2]])},
        "rarm_chain": {"rotation": wave(1.6, lambda q: [25 + S(q) * 10, 0, 0])},                  # dragging the weight
        "head": {"rotation": wave(1.6, lambda q: [-4, -S(q) * 5, 0])},
        "backchain": {"rotation": wave(1.6, lambda q: [15 + S(q * 2) * 6, 0, 0])},
    })
    # HANG (loop, while DORMANT): strung up from the gallows by both wrists, head slumped, legs and the ball dangling,
    # turning slowly on the chains. The Java side hangs him with his feet ~5.5 blocks off the dais.
    anim(m, "hang", 8.0, {
        "root": {"rotation": wave(8.0, lambda q: [0, S(q) * 8, S(q + 1) * 1.5])},
        "chest": {"rotation": wave(8.0, lambda q: [-10 + S(q * 2) * 1.5, 0, 0])},
        "head": {"rotation": wave(8.0, lambda q: [38 + S(q * 2) * 3, S(q) * 6, 12])},
        "rarm": {"rotation": keys((0, [-176, 0, 14]), (8.0, [-176, 0, 14]))},
        "larm": {"rotation": keys((0, [-176, 0, -14]), (8.0, [-176, 0, -14]))},
        "rarm_chain": {"rotation": keys((0, [170, 0, 0]), (8.0, [170, 0, 0]))},
        "larm_chain": {"rotation": keys((0, [170, 0, 0]), (8.0, [170, 0, 0]))},
        "rleg": {"rotation": wave(8.0, lambda q: [10 + S(q * 2) * 3, 0, 4])},
        "lleg": {"rotation": wave(8.0, lambda q: [4 + S(q * 2 + 1) * 3, 0, -3])},
        "backchain": {"rotation": wave(8.0, lambda q: [S(q * 2) * 4, 0, 0])},
        "cloak": {"rotation": wave(8.0, lambda q: [2 + S(q * 2) * 2, 0, 0])},
    })
    # a hook swipe with the left arm
    anim(m, "attack", 0.8, {"larm": {"rotation": keys((0, LA), (0.3, [-130, 0, -30]), (0.45, [-30, 0, 20]), (0.8, LA))},
                            "chest": {"rotation": keys((0, [0, 0, 0]), (0.3, [-6, -20, 0]), (0.45, [8, 18, 0]), (0.8, [0, 0, 0]))}}, loop=False)
    # CHAIN WHIP: the weight swung round overhead and hurled out on its chain
    anim(m, "chain_whip", 1.2, {"rarm": {"rotation": keys((0, RA), (0.4, [-160, 0, 40]), (0.7, [-100, 0, -30]), (0.9, [-60, 0, 0]), (1.2, RA))},
                                "rarm_chain": {"rotation": keys((0, [0, 0, 0]), (0.4, [-60, 0, 0]), (0.7, [70, 0, 0]), (1.2, [0, 0, 0]))},
                                "chest": {"rotation": keys((0, [0, 0, 0]), (0.4, [-8, 30, 0]), (0.7, [10, -25, 0]), (1.2, [0, 0, 0]))}}, loop=False)
    # GRAVE SLAM: both fists overhead, down (impact at 0.7)
    anim(m, "grave_slam", 1.3, {"rarm": {"rotation": keys((0, RA), (0.45, [-175, 0, 10]), (0.7, [-30, 0, 0]), (1.3, RA))},
                                "larm": {"rotation": keys((0, LA), (0.45, [-175, 0, -10]), (0.7, [-30, 0, 0]), (1.3, LA))},
                                "chest": {"rotation": keys((0, [0, 0, 0]), (0.45, [-15, 0, 0]), (0.7, [28, 0, 0]), (1.3, [0, 0, 0]))},
                                "root": {"position": keys((0, [0, 0, 0]), (0.45, [0, 1, 0]), (0.7, [0, -1.5, 0]), (1.3, [0, 0, 0]))}}, loop=False)
    # THE NOOSE: hook cast up and over, then hauled back (hoist at 0.6)
    anim(m, "noose", 1.4, {"larm": {"rotation": keys((0, LA), (0.35, [-170, 0, -15]), (0.6, [-100, 0, 0]), (1.0, [-20, 0, -25]), (1.4, LA))},
                           "larm_chain": {"rotation": keys((0, [0, 0, 0]), (0.35, [-80, 0, 0]), (0.6, [40, 0, 0]), (1.4, [0, 0, 0]))},
                           "chest": {"rotation": keys((0, [0, 0, 0]), (0.6, [4, -10, 0]), (1.0, [-12, 12, 0]), (1.4, [0, 0, 0]))}}, loop=False)
    # GIBBET DROP: points at the victim, then drives both fists down
    anim(m, "gibbet", 1.6, {"rarm": {"rotation": keys((0, RA), (0.3, [-90, 20, 0]), (0.9, [-90, 20, 0]), (1.1, [-170, 0, 10]), (1.3, [-30, 0, 0]), (1.6, RA))},
                            "larm": {"rotation": keys((0, LA), (0.9, LA), (1.1, [-170, 0, -10]), (1.3, [-30, 0, 0]), (1.6, LA))},
                            "chest": {"rotation": keys((0, [0, 0, 0]), (1.1, [-12, 0, 0]), (1.3, [22, 0, 0]), (1.6, [0, 0, 0]))}}, loop=False)
    # SUNDER: arches back as the chains burst, every limb flung out (the parts take over at 0.6)
    anim(m, "sunder", 1.0, {"chest": {"rotation": keys((0, [0, 0, 0]), (0.4, [-25, 0, 0]), (0.6, [-30, 0, 0]), (1.0, [-30, 0, 0]))},
                            "rarm": {"rotation": keys((0, RA), (0.4, [-40, 0, 60]), (0.6, [-60, 0, 110]), (1.0, [-60, 0, 110]))},
                            "larm": {"rotation": keys((0, LA), (0.4, [-40, 0, -60]), (0.6, [-60, 0, -110]), (1.0, [-60, 0, -110]))},
                            "rleg": {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 0, 25]), (0.6, [0, 0, 45]), (1.0, [0, 0, 45]))},
                            "lleg": {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 0, -25]), (0.6, [0, 0, -45]), (1.0, [0, 0, -45]))},
                            "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [-30, 0, 0]), (1.0, [-30, 0, 0]))}}, loop=False)
    # REFORM: the parts snap back; he rises from a crouch, dazed (the stun window)
    anim(m, "reform", 4.0, {"body": {"position": keys((0, [0, -4, 0]), (0.6, [0, -2, 0]), (3.4, [0, -2, 0]), (4.0, [0, 0, 0])),
                                     "rotation": keys((0, [20, 0, 0]), (0.6, [14, 0, 0]), (3.4, [14, 0, 6]), (4.0, [0, 0, 0]))},
                            "head": {"rotation": keys((0, [30, 0, 0]), (1.0, [20, 20, 0]), (2.0, [20, -20, 0]), (3.0, [20, 15, 0]), (4.0, [0, 0, 0]))},
                            "rarm": {"rotation": keys((0, [-10, 0, 30]), (3.4, [-10, 0, 20]), (4.0, RA))},
                            "larm": {"rotation": keys((0, [-10, 0, -30]), (3.4, [-10, 0, -20]), (4.0, LA))},
                            "rleg": {"rotation": keys((0, [-40, 0, 0]), (3.4, [-30, 0, 0]), (4.0, [0, 0, 0]))},
                            "lleg": {"rotation": keys((0, [30, 0, 0]), (3.4, [20, 0, 0]), (4.0, [0, 0, 0]))}}, loop=False)
    # HANGED WRATH (enrage): head thrown back, arms spread, chains rattling
    anim(m, "wrath", 2.0, {"chest": {"rotation": keys((0, [0, 0, 0]), (0.5, [-22, 0, 0]), (1.6, [-22, 0, 0]), (2.0, [0, 0, 0]))},
                           "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [-40, 0, 0]), (1.6, [-40, 0, 0]), (2.0, [0, 0, 0]))},
                           "rarm": {"rotation": keys((0, RA), (0.5, [-70, 0, 70]), (1.6, [-70, 0, 70]), (2.0, RA))},
                           "larm": {"rotation": keys((0, LA), (0.5, [-70, 0, -70]), (1.6, [-70, 0, -70]), (2.0, LA))},
                           "rarm_chain": {"rotation": wave(2.0, lambda q: [S(q * 6) * 30, 0, 0])},
                           "larm_chain": {"rotation": wave(2.0, lambda q: [S(q * 6 + 1) * 30, 0, 0])},
                           "backchain": {"rotation": wave(2.0, lambda q: [20 + S(q * 5) * 15, 0, 0])}}, loop=False)


# =====================================================================================
# the separated parts
# =====================================================================================
def part_anims(m, joint, kind):
    """Limbs dangle from their manacle (joint bone at the top); fallen = lying on the floor, twitching."""
    anim(m, "idle", 3.0, {joint: {"rotation": wave(3.0, lambda q: [S(q) * 6, 0, S(q * 0.5) * 8])}})
    if kind == "arm":
        atk = {joint: {"rotation": keys((0, [0, 0, 0]), (0.35, [-60, 0, 0]), (0.55, [40, 0, 0]), (1.0, [0, 0, 0]))}}
    else:
        atk = {joint: {"rotation": keys((0, [0, 0, 0]), (0.4, [50, 0, 0]), (0.55, [-70, 0, 0]), (0.8, [-60, 0, 0]), (1.2, [0, 0, 0]))}}
    anim(m, "attack", 1.2 if kind == "leg" else 1.0, atk, loop=False)
    anim(m, "fallen", 2.0, {"root": {"rotation": keys((0, [0, 0, 90]), (2.0, [0, 0, 90])),
                                     "position": keys((0, [0, -2, 0]), (2.0, [0, -2, 0]))},
                            joint: {"rotation": keys((0, [0, 0, 0]), (0.3, [8, 0, 0]), (0.5, [0, 0, 0]), (1.6, [0, 0, 0]), (1.7, [-6, 0, 0]),
                                                     (1.8, [0, 0, 0]), (2.0, [0, 0, 0]))}})
    anim(m, "fly", 1.0, {"root": {"rotation": keys((0, [0, 0, 0]), (0.5, [180, 90, 0]), (1.0, [360, 180, 0]))}})
    anim(m, "reel", 1.0, {"root": {"rotation": keys((0, [0, 0, 90]), (1.0, [0, 0, 90])), "position": keys((0, [0, -2, 0]), (1.0, [0, -2, 0]))},
                          joint: {"rotation": wave(1.0, lambda q: [S(q * 3) * 14, 0, 0])}})


def revenant_arm(seed, side="r", skin="normal"):
    M = mats(skin == "enraged")
    name = "revenant_arm_" + side
    m = Rig(name, 128, 128, seed)
    sx = -1 if side == "r" else 1
    ax = -16 if side == "r" else 9
    m.bone("root", [0, 0, 0])
    m.bone("joint", [0, 31, 0], "root")
    m.cube("joint", [-2, 31, -2], [4, 3, 4], M["iron"])                                        # the manacle it hangs by
    part_arm(m, "joint", M, ax, sx, dx=-(ax + 3.5), hand="weight" if side == "r" else "hook")
    part_anims(m, "joint", "arm")
    return m


def revenant_leg(seed, side="r", skin="normal"):
    M = mats(skin == "enraged")
    name = "revenant_leg_" + side
    m = Rig(name, 128, 128, seed)
    x0 = -8 if side == "r" else 1
    m.bone("root", [0, 0, 0])
    m.bone("joint", [0, 14, 0], "root")
    m.cube("joint", [-2, 14.4, -2], [4, 3, 4], M["iron"])
    part_leg(m, "joint", M, x0, dx=-(x0 + 3.5), ball=(side == "r"))
    part_anims(m, "joint", "leg")
    return m


def revenant_head(seed, skin="normal"):
    M = mats(skin == "enraged")
    m = Rig("revenant_head", 128, 128, seed)
    m.bone("root", [0, 0, 0])
    m.bone("skull", [0, 5, 0], "root")
    part_head(m, "skull", M, dx=0, dy=-27.2)
    anim(m, "idle", 1.2, {"skull": {"rotation": wave(1.2, lambda q: [S(q) * 6, S(q * 0.5) * 15, 0])}})
    anim(m, "hop", 0.6, {"skull": {"scale": keys((0, [1.15, 0.85, 1.15]), (0.15, [0.9, 1.15, 0.9]), (0.45, [1, 1, 1]), (0.6, [1.15, 0.85, 1.15])),
                                   "rotation": keys((0, [0, 0, 0]), (0.3, [-15, 0, 0]), (0.6, [0, 0, 0]))}})
    anim(m, "bite", 0.5, {"skull": {"rotation": keys((0, [0, 0, 0]), (0.15, [-25, 0, 0]), (0.25, [20, 0, 0]), (0.5, [0, 0, 0])),
                                    "position": keys((0, [0, 0, 0]), (0.25, [0, 0, -4]), (0.5, [0, 0, 0]))}}, loop=False)
    anim(m, "caged", 1.0, {"skull": {"rotation": wave(1.0, lambda q: [S(q * 3) * 10, S(q * 2) * 25, S(q * 4) * 8])}})
    anim(m, "fly", 1.0, {"skull": {"rotation": keys((0, [0, 0, 0]), (1.0, [0, 720, 0]))}})
    return m
