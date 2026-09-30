"""Shared rigs + animation sets for the roster models."""
from mobkit import A, Rig, anim, keys, wave, S, C  # noqa: F401


# =====================================================================================
# BIPED  (standard Minecraft skin proportions: 2 blocks tall)
#   bones: root > rleg, lleg, body > head, rarm > rhand, larm > lhand
#   the character's RIGHT side is -X in the geo file
# =====================================================================================
def biped(m: Rig, *, head, body, arm, leg, arm_l=None, leg_l=None, art=None, arm_w=4, y=0, scale=1,
          head_size=8, parent_root=True):
    """Build a humanoid. `art` maps part -> {face: A}; parts: head, body, rarm, larm, rleg, lleg.
    `y` lifts the whole rig (e.g. standing on a raft). Returns a dict of useful anchor points."""
    art = art or {}
    arm_l = arm_l or arm
    leg_l = leg_l or leg
    k = scale
    hs = head_size
    m.bone("root", [0, y, 0])
    # legs
    m.bone("rleg", [-2 * k, y + 12 * k, 0], "root")
    m.cube("rleg", [-4 * k, y, -2 * k], [4 * k, 12 * k, 4 * k], leg, art=art.get("rleg"))
    m.bone("lleg", [2 * k, y + 12 * k, 0], "root")
    m.cube("lleg", [0, y, -2 * k], [4 * k, 12 * k, 4 * k], leg_l, art=art.get("lleg"))
    # torso pivots at the hips so a lean bends from the waist
    m.bone("body", [0, y + 12 * k, 0], "root")
    m.cube("body", [-4 * k, y + 12 * k, -2 * k], [8 * k, 12 * k, 4 * k], body, art=art.get("body"))
    top = y + 24 * k
    m.bone("head", [0, top, 0], "body")
    m.cube("head", [-hs / 2 * k, top, -hs / 2 * k], [hs * k, hs * k, hs * k], head, art=art.get("head"))
    aw = arm_w * k
    m.bone("rarm", [-(4 * k + aw / 2), top - 2 * k, 0], "body")
    m.cube("rarm", [-(4 * k + aw), top - 12 * k, -2 * k], [aw, 12 * k, 4 * k], arm, art=art.get("rarm"))
    m.bone("larm", [4 * k + aw / 2, top - 2 * k, 0], "body")
    m.cube("larm", [4 * k, top - 12 * k, -2 * k], [aw, 12 * k, 4 * k], arm_l, art=art.get("larm"))
    # hand anchors: weapon bones hang off these
    m.bone("rhand", [-(4 * k + aw / 2), top - 11 * k, 0], "rarm")
    m.bone("lhand", [4 * k + aw / 2, top - 11 * k, 0], "larm")
    return {"top": top, "hand_y": top - 11 * k, "rx": -(4 * k + aw / 2), "lx": 4 * k + aw / 2, "k": k}


def biped_anims(m: Rig, style="sword", legs=True, cape=None, extra_idle=None, extra_move=None, hold=None):
    """idle / move / attack / special / special2 for a biped.
    style: sword (overhead slash), thrust (spear jab), heavy (two-hand smash), claw (swipe), cast (spell).
    `hold` = base pose added to the right arm in every clip (e.g. a raised sword): [x, y, z]."""
    h = hold or [0, 0, 0]
    bones = {b["name"] for b in m.bones}

    def ra(v):   # right arm pose relative to the hold pose
        return [v[0] + h[0], v[1] + h[1], v[2] + h[2]]

    idle = {"body": {"rotation": wave(3.0, lambda q: [S(q) * 1.2, 0, 0])},
            "head": {"rotation": wave(3.0, lambda q: [S(q) * 2.5, S(q * 0.5) * 10, 0])},
            "rarm": {"rotation": wave(3.0, lambda q: ra([-S(q) * 3, 0, 3 + S(q) * 2]))},
            "larm": {"rotation": wave(3.0, lambda q: [S(q) * 3, 0, -3 - S(q) * 2])}}
    if not legs:
        idle["root"] = {"position": wave(3.0, lambda q: [0, S(q) * 1.4, 0])}
    if cape and cape in bones:
        idle[cape] = {"rotation": wave(3.0, lambda q: [6 + S(q) * 3, 0, 0])}
    if extra_idle:
        idle.update(extra_idle)
    anim(m, "idle", 3.0, idle)

    mv = {"rarm": {"rotation": wave(1.0, lambda q: ra([S(q) * 30, 0, 3]))},
          "larm": {"rotation": wave(1.0, lambda q: [-S(q) * 30, 0, -3])},
          "body": {"position": wave(1.0, lambda q: [0, -abs(S(q)) * 0.5, 0]),
                   "rotation": wave(1.0, lambda q: [2, S(q) * 3, 0])},
          "head": {"rotation": wave(1.0, lambda q: [-2, -S(q) * 3, 0])}}
    if legs:
        mv["rleg"] = {"rotation": wave(1.0, lambda q: [-S(q) * 34, 0, 0])}
        mv["lleg"] = {"rotation": wave(1.0, lambda q: [S(q) * 34, 0, 0])}
    else:
        mv["root"] = {"position": wave(1.0, lambda q: [0, 1.5 + S(q) * 1.5, 0])}
        mv["body"] = {"rotation": wave(1.0, lambda q: [12, 0, S(q) * 3])}
    if cape and cape in bones:
        mv[cape] = {"rotation": wave(1.0, lambda q: [24 + S(q * 2) * 5, 0, 0])}
    if extra_move:
        mv.update(extra_move)
    anim(m, "move", 1.0, mv)

    if style == "thrust":
        atk = {"rarm": {"rotation": keys((0, ra([0, 0, 0])), (0.15, ra([-30, 0, 10])), (0.28, ra([-85, 0, 0])), (0.5, ra([0, 0, 0])))},
               "body": {"rotation": keys((0, [0, 0, 0]), (0.15, [0, 20, 0]), (0.28, [6, -12, 0]), (0.5, [0, 0, 0]))}}
    elif style == "heavy":
        atk = {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-150, 0, 10]), (0.45, [-20, 0, 0]), (0.8, [0, 0, 0]))},
               "larm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-150, 0, -10]), (0.45, [-20, 0, 0]), (0.8, [0, 0, 0]))},
               "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [-12, 0, 0]), (0.45, [18, 0, 0]), (0.8, [0, 0, 0]))}}
    elif style == "claw":
        atk = {"rarm": {"rotation": keys((0, ra([0, 0, 0])), (0.15, ra([-100, -30, 20])), (0.3, ra([-40, 40, -10])), (0.5, ra([0, 0, 0])))},
               "body": {"rotation": keys((0, [0, 0, 0]), (0.15, [0, -20, 0]), (0.3, [5, 18, 0]), (0.5, [0, 0, 0]))}}
    else:  # sword: wind up over the shoulder, slash down across
        atk = {"rarm": {"rotation": keys((0, ra([0, 0, 0])), (0.15, [-150, 10, 25]), (0.3, [-20, -20, -5]), (0.5, ra([0, 0, 0])))},
               "body": {"rotation": keys((0, [0, 0, 0]), (0.15, [-4, 18, 0]), (0.3, [8, -14, 0]), (0.5, [0, 0, 0]))}}
    anim(m, "attack", 0.5 if style != "heavy" else 0.8, atk, loop=False)

    # special: a rally / roar / spell with both arms raised
    anim(m, "special", 1.2, {
        "rarm": {"rotation": keys((0, ra([0, 0, 0])), (0.35, [-165, 0, 20]), (0.8, [-165, 0, 20]), (1.0, [-20, 0, 0]), (1.2, ra([0, 0, 0])))},
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.35, [-165, 0, -20]), (0.8, [-165, 0, -20]), (1.0, [-20, 0, 0]), (1.2, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.35, [-12, 0, 0]), (1.0, [8, 0, 0]), (1.2, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.35, [-25, 0, 0]), (1.0, [5, 0, 0]), (1.2, [0, 0, 0]))}}, loop=False)
    # special2: a throw / point / shot with the right arm
    anim(m, "special2", 0.8, {
        "rarm": {"rotation": keys((0, ra([0, 0, 0])), (0.25, [-170, 0, 15]), (0.4, [-80, 0, 0]), (0.8, ra([0, 0, 0])))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.25, [-6, 25, 0]), (0.4, [8, -15, 0]), (0.8, [0, 0, 0]))},
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.25, [-40, 0, -20]), (0.4, [20, 0, -5]), (0.8, [0, 0, 0]))}}, loop=False)


# =====================================================================================
# face helpers
# =====================================================================================
def face8(rows, **pal):
    return A(rows, pal)
