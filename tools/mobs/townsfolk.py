"""THE TOWNSFOLK OF WAVEBREAK PORT (2026-10-04, the user: "we want a lot of unique looking NPCs because we want this town
to look lively").

    python tools/gen_mob_roster.py townsfolk            # every townsperson (shortcut handled in gen_mob_roster)
    python tools/gen_mob_roster.py folk_polly folk_snip # just these

Every character is a biped (mobs/common.py) with their OWN face, hair, hat, clothes and tools - `person()` builds the body
from a spec, then each character's function adds what makes them them. Java side: homestead/town (TownsfolkEntity reads
geo/folk_<id>, textures/entity/folk_<id>.png, animations/folk_<id>.animation.json; ids match Townsfolk.java).

Bones the Java renderer shows/hides by activity (TownsfolkRenderer): kit_r / kit_l (the work tools in each hand - hidden
off duty), tankard (drinking), cup (the dice cup, gambling), brush + palette (painting).
Clips: idle, move, talk, flourish, wave, cheer, sit, sit_drink, sit_chess, sit_pray, stand_chess, gamble, paint, sleep.
"""
from mobkit import A, bands, noise, over, solid, lit, spots, speckle, anim, keys, wave, S, Rig
from mobs.common import biped, biped_anims

C = lambda *c: noise(list(c))


# =====================================================================================
# faces + hair
# =====================================================================================
def face(skin, hair, eye, *, style="fringe", beard=None, stache=None, brows=None, glasses=None, lips=None, rosy=None,
         patch=None, scar=None, freckles=None, wrinkles=False, monocle=None, goatee=None, closed=False):
    rows = [list("hhhhhhhh"), list("hhhhhhhh"), list("sbbssbbs"), list("sWEssWEs"), list("ssssssss"), list("sssnnsss"),
            list("ssmmmmss"), list("ssssssss")]
    if style == "bald":
        rows[0] = list("ssssssss"); rows[1] = list("ssssssss")
    elif style == "receding":
        rows[0] = list("hhsssshh"); rows[1] = list("hsssssss")
    elif style == "parted":
        rows[1] = list("hhhshhhh")
    elif style == "swept":
        rows[1] = list("hhhhhhss")
    elif style == "long":
        for r in range(2, 8):
            rows[r][0] = "h"; rows[r][7] = "h"
    elif style == "fringe_long":
        for r in range(2, 6):
            rows[r][0] = "h"; rows[r][7] = "h"
    if closed:
        rows[3] = list("sllsslls")
    if wrinkles:
        rows[4] = list("swssssws")
    if rosy:
        rows[4][1] = "r"; rows[4][6] = "r"
    if freckles:
        rows[4][1] = "f"; rows[4][2] = "f"; rows[4][5] = "f"; rows[4][6] = "f"
    if glasses:
        rows[3] = list("gWEggWEg")
    if monocle:
        rows[3][5] = "o"; rows[3][6] = "E"; rows[2][5] = "o"; rows[2][6] = "o"
    if patch:
        rows[3][1] = "P"; rows[3][2] = "P"; rows[2][1] = "P"; rows[2][2] = "P"
        rows[1][0] = "P"; rows[2][0] = "P"; rows[3][3] = "P"; rows[2][3] = "P"
    if scar:
        rows[2][6] = "c"; rows[3][6] = "c"; rows[4][6] = "c"
    if lips:
        rows[6] = list("sslLLlss")
    if stache:
        rows[6] = list("tttmmttt")
    if goatee:
        rows[7] = list("ssGGGGss")
    if beard:
        rows[4][0] = "d"; rows[4][7] = "d"
        rows[5][0] = "d"; rows[5][1] = "d"; rows[5][6] = "d"; rows[5][7] = "d"
        rows[6] = list("ddmmmmdd") if not stache else list("dttmmttd")
        rows[7] = list("dddddddd")
    pal = dict(h=hair, b=brows or hair, s=skin, W="#f0ece0", E=eye, n=skin, m="#7a3a2e", d=beard or skin,
               t=stache or beard or skin, g=glasses or skin, l=lips or "#a04040", L=lips or "#a04040", r=rosy or skin,
               P=patch or "#141010", c=scar or skin, f=freckles or skin, w=_shade(skin, 0.86), o=monocle or skin,
               G=goatee or skin)
    if closed:
        pal["l"] = "#3a2a24"
    return A(["".join(r) for r in rows], pal)


def _shade(hexs, f):
    h = hexs.lstrip("#")
    r, g, b = int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)
    return "#%02x%02x%02x" % (int(r * f), int(g * f), int(b * f))


def side(skin, hair, long=False, bald=False):
    if bald:
        return A("ssssssss\nssssssss\nssssssss\nssssssss\nssssssss\nssssssss\nssssssss\nssssssss", dict(s=skin))
    if long:
        return A("hhhhhhhh\nhhhhhhhh\nhhhhhhhh\nhhhhhhss\nhhhhhsss\nhhhhssss\nhhhhssss\nhhhhssss", dict(h=hair, s=skin))
    return A("hhhhhhhh\nhhhhhhhh\nhhhhhhss\nhhhhssss\nhhhsssss\nhhssssss\nhsssssss\nssssssss", dict(h=hair, s=skin))


def back(skin, hair, bald=False, short=True):
    if bald:
        return None
    rows = ["hhhhhhhh"] * (6 if short else 8) + ["ssssssss"] * (2 if short else 0)
    return A("\n".join(rows), dict(h=hair, s=skin))


# =====================================================================================
# the body
# =====================================================================================
def person(name, seed, *, skin, hair, eye, top, sleeve=None, legs, boots="#3a2a1e", hair_style="fringe", facekw=None,
           belly=False, skirt=None, coat=None, cuffs=None, collar=None, apron=None, belt=None, buttons=None, head_size=8):
    """A townsperson: skin (3 tones), hair colour + style, eye colour; top = shirt/dress body material; sleeve = arm
    material (defaults to top); legs = trouser material; boots colour; skirt = a long skirt material (hides the legs);
    coat = a long coat's material (skirts front and back); apron; belt colour; buttons colour."""
    m = Rig(name, 128, 128, seed)
    facekw = dict(facekw or {})
    bald = hair_style in ("bald",)
    long = hair_style in ("long", "pony_long", "braids")
    fstyle = {"long": "long", "braids": "fringe_long", "pony_long": "fringe_long", "bald": "bald", "receding": "receding",
              "parted": "parted", "swept": "swept", "bun": "parted", "pony": "swept", "curly": "fringe", "wild": "fringe",
              "fringe": "fringe", "crop": "fringe"}[hair_style]
    skin0 = skin[0]
    head_art = {"north": face(skin0, hair, eye, style=fstyle, **facekw), "east": side(skin0, hair, long, bald), "west": side(skin0, hair, long, bald),
                "up": A("hhhhhhhh\n" * 8, dict(h=hair)) if not bald else None}
    b = back(skin0, hair, bald, short=not long)
    if b:
        head_art["south"] = b
    head_art = {k: v for k, v in head_art.items() if v is not None}
    leg = bands(legs, (-3, 12, C(boots, _shade(boots, 1.1))))
    body_art = {}
    if buttons:
        body_art["north"] = A.at(8, 12, {(4, y): "B" for y in (1, 3, 5, 7)}, B=buttons)
    a = biped(m, head=noise(skin), body=top, arm=bands(sleeve or top, (-2, 12, noise(skin)))
              if not cuffs else bands(sleeve or top, (-3, -2, C(cuffs)), (-2, 12, noise(skin))), leg=leg,
              art={"head": head_art, "body": body_art}, head_size=head_size)
    t = a["top"]
    if belly:
        m.cube("body", [-4.5, 13, -3.25], [9, 6, 1.25], top)
    if skirt:
        m.cube("body", [-4.5, 0.5, -2.75], [9, 11.5, 5.5], skirt)
    if coat:
        m.cube("body", [-4.4, 4, -2.55], [3.4, 8, 0.6], coat); m.cube("body", [1.0, 4, -2.55], [3.4, 8, 0.6], coat)
        m.cube("body", [-4.4, 4, 2.0], [8.8, 8, 0.6], coat)
    if apron:
        m.cube("body", [-3.5, 4, -2.6], [7, 14, 0.5], apron)
    if belt:
        m.cube("body", [-4.2, 12, -2.2], [8.4, 1.25, 4.4], C(belt, _shade(belt, 1.15)),
               art={"north": A.at(8, 1, {(3, 0): "k", (4, 0): "k"}, k="#d8b040")})
    if collar:
        m.cube("body", [-4.2, t - 1.25, -2.2], [8.4, 1.25, 4.4], collar)
    # hair volumes
    H = C(hair, _shade(hair, 0.85), _shade(hair, 1.12))
    if hair_style == "long":
        m.cube("head", [-4.4, t - 3, 2.6], [8.8, 10.5, 1.6], H)
        m.cube("head", [-4.6, t - 1, -2], [0.6, 8, 4.6], H); m.cube("head", [4.0, t - 1, -2], [0.6, 8, 4.6], H)
    elif hair_style == "braids":
        m.cube("head", [-4.4, t + 1, 2.6], [8.8, 6.5, 1.0], H)
        m.cube("head", [-5.2, t - 6, -1.2], [1.4, 9, 1.4], H); m.cube("head", [3.8, t - 6, -1.2], [1.4, 9, 1.4], H)
        m.cube("head", [-5.3, t - 7, -1.3], [1.6, 1, 1.6], C("#c83030")); m.cube("head", [3.7, t - 7, -1.3], [1.6, 1, 1.6], C("#c83030"))
    elif hair_style == "bun":
        m.cube("head", [-2, t + 6, 3], [4, 4, 3], H)
    elif hair_style == "pony":
        m.cube("head", [-1, t + 1, 4], [2, 6, 1.5], H, rot=[18, 0, 0])
    elif hair_style == "pony_long":
        m.cube("head", [-1.25, t - 5, 4.1], [2.5, 11, 1.5], H, rot=[10, 0, 0])
        m.cube("head", [-1.5, t + 5, 4.0], [3, 1, 1.6], C("#2a2a6a"))
    elif hair_style == "curly":
        for (x, z) in ((-4.6, -3), (-4.6, 0.5), (3.4, -3), (3.4, 0.5), (-2.5, 3.4), (0.5, 3.4)):
            m.cube("head", [x, t + 3, z], [1.2 if abs(x) > 4 else 2.2, 4, 2.4 if abs(x) > 4 else 1.2], H)
        m.cube("head", [-4.4, t + 7.6, -4.4], [8.8, 1.2, 8.8], H)
    elif hair_style == "wild":
        m.cube("head", [-4.8, t + 5, -4.8], [9.6, 4.2, 9.6], H)
        for (x, z, r) in ((-5, -2, [0, 0, 25]), (4, 1, [0, 0, -25]), (-1, 4.5, [30, 0, 0])):
            m.cube("head", [x, t + 6, z], [1.2, 3, 1.2], H, rot=r)
    elif hair_style == "receding":
        m.cube("head", [-4.3, t + 2, 1], [8.6, 4, 3.3], H)
    return m, a


# =====================================================================================
# hats + props
# =====================================================================================
def tricorn(m, t, col="#141214", trim="#c8a040"):
    H = C(col, _shade(col, 1.2))
    m.cube("head", [-5.5, t + 8, -5.5], [11, 1, 11], H, art={"north": A.at(11, 1, {(x, 0): "g" for x in range(11)}, g=trim)})
    m.cube("head", [-3.75, t + 9, -3.75], [7.5, 2.75, 7.5], H)
    m.cube("head", [-6, t + 9, -1], [1, 2, 2], H); m.cube("head", [5, t + 9, -1], [1, 2, 2], H)
    m.cube("head", [-1, t + 9, -6], [2, 2, 1], H)


def bicorne(m, t, col="#14141e", trim="#e0c050", plume="#e8e8f0"):
    H = C(col, _shade(col, 1.2))
    m.cube("head", [-6.5, t + 8, -2], [13, 4, 4], H, art={"north": A.at(13, 4, {(x, 0): "g" for x in range(13)}, g=trim)})
    m.cube("head", [-5, t + 11.5, -1.5], [10, 1.5, 3], H)
    m.cube("head", [-0.5, t + 9, -2.4], [1.5, 1.5, 0.5], C("#c83030"))                        # cockade
    m.cube("head", [-4, t + 12.5, -0.5], [8, 1.5, 1], C(plume), rot=[0, 0, -8])


def brim_hat(m, t, col, band="#5a2a1a", wide=6.5, crown=3.5, h=3):
    H = C(col, _shade(col, 0.9), _shade(col, 1.08))
    m.cube("head", [-wide, t + 7.5, -wide], [2 * wide, 1, 2 * wide], H)
    m.cube("head", [-crown, t + 8.5, -crown], [2 * crown, h, 2 * crown], H,
           art={"sides": A("\n".join(["." * 8] * (h - 1) + ["k" * 8]), k=band)} if h > 1 else None)


def top_hat(m, t, col="#1a1618", band="#7a1a20"):
    H = C(col, _shade(col, 1.25))
    m.cube("head", [-5, t + 8, -5], [10, 1, 10], H)
    m.cube("head", [-3.5, t + 9, -3.5], [7, 6, 7], H, art={"sides": A(".......\n.......\n.......\n.......\nkkkkkkk\n.......", k=band)})


def cap(m, t, col, peak=None):
    H = C(col, _shade(col, 0.88))
    m.cube("head", [-4.4, t + 6.5, -4.4], [8.8, 2, 8.8], H)
    m.cube("head", [-4, t + 6.5, -6.5], [8, 0.75, 2.4], C(peak or _shade(col, 0.7)))


def beanie(m, t, col, bobble=None):
    m.cube("head", [-4.5, t + 5.5, -4.5], [9, 3.5, 9], C(col, _shade(col, 0.9)),
           art={"sides": A("........\n........\nkkkkkkkk", k=_shade(col, 0.7))})
    if bobble:
        m.cube("head", [-1, t + 9, -1], [2, 1.5, 2], C(bobble))


def kerchief(m, t, col, knot=True):
    m.cube("head", [-4.5, t + 5.25, -4.5], [9, 3, 9], C(col, _shade(col, 0.88)))
    if knot:
        m.cube("head", [-1, t + 5, 4.5], [2, 1, 2.5], C(col), rot=[-30, 0, 0])


def beret(m, t, col):
    m.cube("head", [-4.75, t + 7, -4.25], [9.5, 2, 9.5], C(col, _shade(col, 0.9)), rot=[0, 0, -8])
    m.cube("head", [-0.5, t + 9, -0.5], [1, 1, 1], C(_shade(col, 0.7)))


def bonnet(m, t, col, ribbon):
    m.cube("head", [-4.75, t + 2, -2], [9.5, 7.25, 7], C(col, _shade(col, 0.9)))
    m.cube("head", [-5, t + 3, -4.6], [10, 6.5, 2.6], C(col), rot=[-12, 0, 0])
    m.cube("head", [-4.9, t + 1, -1.5], [0.6, 2, 1], C(ribbon)); m.cube("head", [4.3, t + 1, -1.5], [0.6, 2, 1], C(ribbon))


def tankard(m, a):
    """The drinking prop (shown only while drinking)."""
    t = a["top"]
    m.bone("tankard", [a["rx"], a["hand_y"], 0], "rhand")
    m.cube("tankard", [-7.75, t - 15.5, -3], [3.5, 4.5, 3.5], C("#8a6a3a", "#7a5a2e"),
           art={"north": A.at(4, 5, {(x, 0): "f" for x in range(4)}, f="#f8f4e8"), "up": A("ffff\nffff\nffff\nffff", f="#f0e4c0")})


def dice_cup(m, a):
    t = a["top"]
    m.bone("cup", [a["rx"], a["hand_y"], 0], "rhand")
    m.cube("cup", [-7.5, t - 15.5, -3.25], [3, 4, 3], C("#3a2418", "#4a2e1e"), art={"sides": A("...\n...\n...\nkkk", k="#c8a040")})


def paint_kit(m, a):
    t = a["top"]
    m.bone("brush", [a["rx"], a["hand_y"], 0], "rhand")
    m.cube("brush", [-6.5, t - 14, -4.5], [0.75, 0.75, 6], C("#c8a060"), rot=[25, 0, 0])
    m.cube("brush", [-6.6, t - 15.5, -7.5], [0.95, 0.95, 1.5], C("#c83a3a"), rot=[25, 0, 0])
    m.bone("palette", [a["lx"], a["hand_y"], 0], "lhand")
    m.cube("palette", [5, t - 14, -5.5], [5, 0.5, 6], C("#c8a070", "#b89060"),
           art={"up": A.at(5, 6, {(1, 1): "r", (3, 1): "b", (1, 3): "y", (3, 4): "g", (2, 2): "w"},
                              r="#d83030", b="#3050c8", y="#e8c830", g="#40a040", w="#f0f0f0")})


def leisure_props(m, a):
    tankard(m, a); dice_cup(m, a); paint_kit(m, a)


def kit(m, a, side="r"):
    """The bone for the character's work tool in that hand (hidden off duty)."""
    hand = "rhand" if side == "r" else "lhand"
    m.bone("kit_" + side, [a["rx"] if side == "r" else a["lx"], a["hand_y"], 0], hand)
    return "kit_" + side


# =====================================================================================
# the clips every townsperson shares
# =====================================================================================
FLOURISHES = {
    "tip_hat": lambda: {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-150, 0, -15]), (0.9, [-150, 0, -15]), (1.4, [0, 0, 0]))},
                        "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [12, 0, 0]), (0.9, [12, 0, 0]), (1.4, [0, 0, 0]))}},
    "stretch": lambda: {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.6, [-170, 0, 15]), (1.6, [-170, 0, 15]), (2.2, [0, 0, 0]))},
                        "larm": {"rotation": keys((0, [0, 0, 0]), (0.6, [-170, 0, -15]), (1.6, [-170, 0, -15]), (2.2, [0, 0, 0]))},
                        "body": {"rotation": keys((0, [0, 0, 0]), (0.6, [-10, 0, 0]), (1.6, [-10, 0, 0]), (2.2, [0, 0, 0]))},
                        "head": {"rotation": keys((0, [0, 0, 0]), (0.6, [-25, 0, 0]), (1.6, [-25, 0, 0]), (2.2, [0, 0, 0]))}},
    "scratch": lambda: {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-150, 0, -40]), (0.6, [-140, 0, -45]), (0.8, [-150, 0, -40]),
                                                  (1.0, [-140, 0, -45]), (1.4, [0, 0, 0]))},
                        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 0, 12]), (1.0, [0, 0, 12]), (1.4, [0, 0, 0]))}},
    "pocketwatch": lambda: {"larm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-70, 30, 0]), (1.8, [-70, 30, 0]), (2.2, [0, 0, 0]))},
                            "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [30, -10, 0]), (1.8, [30, -10, 0]), (2.2, [0, 0, 0]))}},
    "cross_arms": lambda: {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-70, 0, -50]), (2.6, [-70, 0, -50]), (3.0, [0, 0, 0]))},
                           "larm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-60, 0, 50]), (2.6, [-60, 0, 50]), (3.0, [0, 0, 0]))},
                           "head": {"rotation": keys((0, [0, 0, 0]), (0.6, [0, 25, 0]), (1.6, [0, -25, 0]), (3.0, [0, 0, 0]))}},
    "look_around": lambda: {"head": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 50, 0]), (1.2, [0, 50, 0]), (1.7, [0, -50, 0]), (2.4, [0, -50, 0]), (3.0, [0, 0, 0]))},
                            "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 12, 0]), (1.7, [0, -12, 0]), (3.0, [0, 0, 0]))}},
    "yawn": lambda: {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-130, 0, -35]), (1.5, [-130, 0, -35]), (2.0, [0, 0, 0]))},
                     "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [-20, 0, 0]), (1.5, [-20, 0, 0]), (2.0, [0, 0, 0]))},
                     "larm": {"rotation": keys((0, [0, 0, 0]), (0.6, [-20, 0, -40]), (1.5, [-20, 0, -40]), (2.0, [0, 0, 0]))}},
    "laugh": lambda: {"body": {"rotation": keys((0, [0, 0, 0]), (0.2, [-8, 0, 0]), (0.4, [4, 0, 0]), (0.6, [-8, 0, 0]), (0.8, [4, 0, 0]), (1.0, [-8, 0, 0]), (1.4, [0, 0, 0]))},
                      "head": {"rotation": keys((0, [0, 0, 0]), (0.2, [-25, 0, 0]), (0.6, [-25, 0, 0]), (1.0, [-20, 0, 0]), (1.4, [0, 0, 0]))},
                      "larm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-40, 0, 30]), (1.0, [-40, 0, 30]), (1.4, [0, 0, 0]))}},
    "count_coins": lambda: {"larm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-55, 25, 0]), (2.2, [-55, 25, 0]), (2.6, [0, 0, 0]))},
                            "rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-60, -25, 0]), (0.7, [-50, -20, 0]), (1.0, [-60, -25, 0]),
                                                      (1.3, [-50, -20, 0]), (1.6, [-60, -25, 0]), (2.2, [-60, -25, 0]), (2.6, [0, 0, 0]))},
                            "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [30, 0, 0]), (2.2, [30, 0, 0]), (2.6, [0, 0, 0]))}},
    "bless": lambda: {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-100, 0, 0]), (0.8, [-100, 30, 0]), (1.2, [-100, -30, 0]), (1.6, [-100, 0, 0]), (2.0, [0, 0, 0]))},
                      "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [15, 0, 0]), (1.6, [15, 0, 0]), (2.0, [0, 0, 0]))}},
    "spyglass": lambda: {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-95, 25, 0]), (2.5, [-95, 25, 0]), (3.0, [0, 0, 0]))},
                         "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [-5, 0, 0]), (1.2, [-5, 30, 0]), (2.0, [-5, -30, 0]), (2.5, [-5, 0, 0]), (3.0, [0, 0, 0]))}},
    "fiddle": lambda: {"larm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-80, 40, -30]), (2.7, [-80, 40, -30]), (3.0, [0, 0, 0]))},
                       "rarm": {"rotation": wave(3.0, lambda q: [-70 + S(4 * q) * 5, -30 + S(4 * q) * 20, 0])},
                       "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [10, 0, 20]), (2.7, [10, 0, 20]), (3.0, [0, 0, 0]))},
                       "body": {"rotation": wave(3.0, lambda q: [0, 0, S(2 * q) * 4])}},
    "hammer": lambda: {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-140, 0, 0]), (0.45, [-50, 0, 0]), (0.75, [-140, 0, 0]), (0.9, [-50, 0, 0]),
                                                 (1.2, [-140, 0, 0]), (1.35, [-50, 0, 0]), (1.8, [0, 0, 0]))},
                       "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [25, 0, 0]), (1.4, [25, 0, 0]), (1.8, [0, 0, 0]))}},
    "shush": lambda: {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-115, 0, -30]), (1.6, [-115, 0, -30]), (2.0, [0, 0, 0]))},
                      "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [0, 30, 0]), (1.0, [0, -30, 0]), (1.6, [0, 0, 0]))}},
    "sweep": lambda: {"rarm": {"rotation": wave(2.4, lambda q: [-40, S(2 * q) * 25, 0])}, "larm": {"rotation": wave(2.4, lambda q: [-30, S(2 * q) * 25, 0])},
                      "body": {"rotation": wave(2.4, lambda q: [10, S(2 * q) * 10, 0])}},
    "crystal": lambda: {"rarm": {"rotation": wave(3.0, lambda q: [-70 + C_(q) * 8, S(q) * 20, 0])},
                        "larm": {"rotation": wave(3.0, lambda q: [-70 - C_(q) * 8, S(q) * 20, 0])},
                        "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [25, 0, 0]), (2.5, [25, 0, 0]), (3.0, [0, 0, 0]))}},
}


def C_(q):
    import math
    return math.cos(q)


def town_anims(m, flourish="stretch", hold=None):
    """idle / move (biped_anims) + talk, wave, cheer, flourish and the leisure clips."""
    biped_anims(m, "claw", hold=hold)
    m.anims.pop("attack", None); m.anims.pop("special", None); m.anims.pop("special2", None)
    anim(m, "talk", 2.0, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-55, -20, 10]), (1.0, [-50, 10, 15]), (1.6, [-55, -20, 10]), (2.0, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [8, 0, 0]), (0.7, [-4, 0, 0]), (1.0, [8, 0, 0]), (1.3, [0, 10, 0]), (2.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [4, 0, 0]), (1.5, [4, 0, 0]), (2.0, [0, 0, 0]))}}, loop=False)
    anim(m, "wave", 1.4, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-160, 0, 20]), (0.5, [-160, 0, 40]), (0.7, [-160, 0, 15]), (0.9, [-160, 0, 40]), (1.1, [-160, 0, 20]), (1.4, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.3, [-8, 0, 6]), (1.1, [-8, 0, 6]), (1.4, [0, 0, 0]))}}, loop=False)
    anim(m, "throw", 0.9, {                                              # a dart (homestead/darts): cock by the ear, snap, point
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.25, [-135, 0, 8]), (0.38, [-85, 0, 5]), (0.6, [-80, 0, 5]), (0.9, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.25, [-3, 0, 0]), (0.4, [6, 0, 0]), (0.9, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.2, [4, 0, 0]), (0.9, [0, 0, 0]))}}, loop=False)
    anim(m, "cheer", 1.2, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.25, [-170, 0, 25]), (0.6, [-150, 0, 20]), (0.9, [-170, 0, 25]), (1.2, [0, 0, 0]))},
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.25, [-170, 0, -25]), (0.6, [-150, 0, -20]), (0.9, [-170, 0, -25]), (1.2, [0, 0, 0]))},
        "root": {"position": keys((0, [0, 0, 0]), (0.25, [0, 3, 0]), (0.45, [0, 0, 0]), (0.7, [0, 3, 0]), (0.9, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.25, [-20, 0, 0]), (0.9, [-20, 0, 0]), (1.2, [0, 0, 0]))}}, loop=False)
    anim(m, "flourish", 3.0, FLOURISHES[flourish](), loop=False)

    sit = {"root": {"position": keys((0, [0, -12, 0]))}, "rleg": {"rotation": keys((0, [-85, 8, 0]))},
           "lleg": {"rotation": keys((0, [-85, -8, 0]))}}

    def with_sit(extra):
        d = {k: dict(v) for k, v in sit.items()}
        d.update(extra)
        return d

    anim(m, "sit", 4.0, with_sit({"body": {"rotation": wave(4.0, lambda q: [S(q) * 1.5, 0, 0])},
                                  "head": {"rotation": wave(4.0, lambda q: [5 + S(q) * 3, S(q * 0.5) * 18, 0])},
                                  "rarm": {"rotation": keys((0, [-30, 0, 5]))}, "larm": {"rotation": keys((0, [-30, 0, -5]))}}))
    # drinking: the tankard comes up to the mouth now and then, the rest of the time it rests on the knee
    anim(m, "sit_drink", 6.0, with_sit({
        "rarm": {"rotation": keys((0, [-40, 0, 0]), (1.6, [-40, 0, 0]), (2.1, [-125, 0, -28]), (3.2, [-135, 0, -28]), (3.7, [-40, 0, 0]), (6.0, [-40, 0, 0]))},
        "head": {"rotation": keys((0, [0, 15, 0]), (1.6, [0, 15, 0]), (2.1, [-22, 0, 0]), (3.2, [-30, 0, 0]), (3.7, [0, -10, 0]), (4.8, [5, -25, 0]), (6.0, [0, 15, 0]))},
        "larm": {"rotation": keys((0, [-30, 0, -5]), (4.4, [-30, 0, -5]), (4.8, [-55, 0, -25]), (5.4, [-55, 0, -25]), (6.0, [-30, 0, -5]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (2.1, [-6, 0, 0]), (3.2, [-8, 0, 0]), (3.7, [0, 0, 0]), (6.0, [0, 0, 0]))}}))
    # chess: chin in hand, thinking - then reach out and move a piece
    anim(m, "sit_chess", 5.0, with_sit({
        "body": {"rotation": keys((0, [10, 0, 0]))},
        "larm": {"rotation": keys((0, [-110, 0, -25]))},
        "head": {"rotation": keys((0, [22, 0, 0]), (2.0, [22, 10, 0]), (3.0, [26, -8, 0]), (5.0, [22, 0, 0]))},
        "rarm": {"rotation": keys((0, [-35, 0, 0]), (3.2, [-35, 0, 0]), (3.6, [-75, -10, 0]), (4.2, [-70, 10, 0]), (4.6, [-35, 0, 0]), (5.0, [-35, 0, 0]))}}))
    anim(m, "sit_pray", 4.0, with_sit({
        "rarm": {"rotation": keys((0, [-75, 0, -22]))}, "larm": {"rotation": keys((0, [-75, 0, 22]))},
        "head": {"rotation": wave(4.0, lambda q: [28 + S(q) * 3, 0, 0])},
        "body": {"rotation": keys((0, [8, 0, 0]))}}))
    # standing at the giant board: hands behind the back, pacing the head, the odd stroke of the chin
    anim(m, "stand_chess", 6.0, {
        "rarm": {"rotation": keys((0, [22, 0, 8]), (3.6, [22, 0, 8]), (4.0, [-120, 0, -35]), (5.2, [-120, 0, -35]), (5.6, [22, 0, 8]), (6.0, [22, 0, 8]))},
        "larm": {"rotation": keys((0, [22, 0, -8]))},
        "head": {"rotation": keys((0, [18, 0, 0]), (1.5, [20, 20, 0]), (3.0, [18, -20, 0]), (4.0, [10, 0, 0]), (5.6, [10, 0, 0]), (6.0, [18, 0, 0]))},
        "body": {"rotation": wave(6.0, lambda q: [4, S(q) * 6, 0])}})
    # gambling at a dice table: shake the cup by the ear, slam it down, peek
    anim(m, "gamble", 4.0, {
        "rarm": {"rotation": keys((0, [-60, 0, 0]), (0.4, [-130, 0, -10]), (0.6, [-120, 0, -15]), (0.8, [-135, 0, -10]), (1.0, [-120, 0, -15]),
                                  (1.2, [-135, 0, -10]), (1.5, [-55, 0, 0]), (2.6, [-55, 0, 0]), (3.0, [-70, -10, 0]), (4.0, [-60, 0, 0]))},
        "larm": {"rotation": keys((0, [-45, 0, -10]), (1.5, [-45, 0, -10]), (2.0, [-60, 0, 10]), (2.6, [-45, 0, -10]), (4.0, [-45, 0, -10]))},
        "head": {"rotation": keys((0, [10, 0, 0]), (0.4, [0, 0, 10]), (1.2, [0, 0, 10]), (1.6, [30, 0, 0]), (2.6, [32, 0, 0]), (3.2, [5, 20, 0]), (4.0, [10, 0, 0]))},
        "body": {"rotation": keys((0, [6, 0, 0]), (1.5, [12, 0, 0]), (2.6, [12, 0, 0]), (4.0, [6, 0, 0]))}})
    # gambling sat at the table: the same cup-shake, slam and peek, from a stool
    g = m.anims["gamble"]["bones"]
    anim(m, "sit_gamble", 4.0, with_sit({"rarm": g["rarm"], "larm": g["larm"], "head": g["head"], "body": g["body"]}))
    # painting: the palette held out, brush strokes, step back and squint
    anim(m, "paint", 5.0, {
        "larm": {"rotation": keys((0, [-45, 10, -15]))},
        "rarm": {"rotation": keys((0, [-75, 0, 0]), (0.4, [-85, 15, 0]), (0.8, [-70, -10, 0]), (1.2, [-88, 10, 0]), (1.6, [-72, -15, 0]),
                                  (2.0, [-85, 5, 0]), (2.6, [-30, 0, 5]), (3.6, [-30, 0, 5]), (4.0, [-80, 10, 0]), (4.5, [-70, -10, 0]), (5.0, [-75, 0, 0]))},
        "head": {"rotation": keys((0, [5, 0, 0]), (2.0, [8, 5, 0]), (2.6, [0, 0, 14]), (3.6, [0, 0, -10]), (4.0, [5, 0, 0]), (5.0, [5, 0, 0]))},
        "body": {"rotation": keys((0, [6, 0, 0]), (2.2, [6, 0, 0]), (2.6, [-6, 0, 0]), (3.6, [-6, 0, 0]), (4.0, [6, 0, 0]), (5.0, [6, 0, 0]))}})
    # at the organ: both hands on the manuals, playing, swaying with the hymn
    anim(m, "organ", 3.0, {
        "rarm": {"rotation": wave(3.0, lambda q: [-62 + S(6 * q) * 6, -12 + S(3 * q) * 10, 0])},
        "larm": {"rotation": wave(3.0, lambda q: [-62 - S(6 * q + 1) * 6, 12 - S(3 * q) * 10, 0])},
        "head": {"rotation": wave(3.0, lambda q: [12 + S(q) * 4, S(q) * 8, S(2 * q) * 5])},
        "body": {"rotation": wave(3.0, lambda q: [6, S(q) * 5, S(2 * q) * 3])}})
    # preaching from the lectern: open hands, a raised finger, a look round the pews
    anim(m, "preach", 6.0, {
        "rarm": {"rotation": keys((0, [-40, 0, 0]), (1.0, [-110, 0, -10]), (2.0, [-60, 20, 20]), (3.0, [-150, 0, -10]), (4.0, [-150, 0, -10]),
                                  (5.0, [-60, -20, 10]), (6.0, [-40, 0, 0]))},
        "larm": {"rotation": keys((0, [-40, 0, 0]), (1.5, [-60, 0, 25]), (3.5, [-40, 0, 0]), (5.0, [-70, 0, 20]), (6.0, [-40, 0, 0]))},
        "head": {"rotation": keys((0, [5, 0, 0]), (1.5, [0, 30, 0]), (3.0, [-12, 0, 0]), (4.5, [0, -30, 0]), (6.0, [5, 0, 0]))},
        "body": {"rotation": wave(6.0, lambda q: [S(q) * 4, 0, 0])}})
    # dancing (music night, festivals): a hornpipe - hops, arms swinging, a turn
    anim(m, "dance", 2.0, {
        "root": {"position": wave(2.0, lambda q: [0, abs(S(2 * q)) * 2.5, 0]), "rotation": wave(2.0, lambda q: [0, S(q) * 25, 0])},
        "rleg": {"rotation": wave(2.0, lambda q: [S(2 * q) * 35, 0, 0])}, "lleg": {"rotation": wave(2.0, lambda q: [-S(2 * q) * 35, 0, 0])},
        "rarm": {"rotation": wave(2.0, lambda q: [-90 - S(2 * q) * 50, 0, 20])}, "larm": {"rotation": wave(2.0, lambda q: [-20 + S(2 * q) * 40, 0, -40])},
        "head": {"rotation": wave(2.0, lambda q: [-10, S(2 * q) * 15, 0])}})
    # fishing from the pier: rod held out, a twitch, the odd big strike + reel in
    anim(m, "fish", 6.0, {
        "rarm": {"rotation": keys((0, [-55, 0, 0]), (2.0, [-58, 0, 0]), (2.2, [-50, 0, 0]), (4.0, [-55, 0, 0]), (4.3, [-120, 0, 0]),
                                  (4.8, [-130, 0, 0]), (5.4, [-70, 0, 0]), (6.0, [-55, 0, 0]))},
        "larm": {"rotation": keys((0, [-40, 0, 20]), (4.3, [-40, 0, 20]), (4.6, [-70, 20, 10]), (5.4, [-60, 0, 20]), (6.0, [-40, 0, 20]))},
        "head": {"rotation": keys((0, [15, 0, 0]), (4.3, [15, 0, 0]), (4.8, [-10, 0, 0]), (6.0, [15, 0, 0]))},
        "body": {"rotation": keys((0, [4, 0, 0]), (4.3, [4, 0, 0]), (4.8, [-8, 0, 0]), (6.0, [4, 0, 0]))}})
    anim(m, "sit_sing", 3.0, {"root": {"position": keys((0, [0, -12, 0]))}, "rleg": {"rotation": keys((0, [-85, 8, 0]))},
                              "lleg": {"rotation": keys((0, [-85, -8, 0]))},
                              "rarm": {"rotation": keys((0, [-70, 0, -18]))}, "larm": {"rotation": keys((0, [-70, 0, 18]))},
                              "head": {"rotation": wave(3.0, lambda q: [-12 + S(2 * q) * 4, S(q) * 6, 0])},
                              "body": {"rotation": wave(3.0, lambda q: [0, 0, S(q) * 3])}})
    anim(m, "sleep", 4.0, {"head": {"rotation": wave(4.0, lambda q: [S(q) * 2, 0, 0])},
                           "body": {"position": wave(4.0, lambda q: [0, 0, S(q) * 0.3])},
                           "rarm": {"rotation": keys((0, [0, 0, 6]))}, "larm": {"rotation": keys((0, [0, 0, -6]))}})


def finish(m, a, flourish, hold=None):
    leisure_props(m, a)
    town_anims(m, flourish, hold)
    return m


# =====================================================================================
# THE CHARACTERS (ids match homestead/town/Townsfolk.java)
# =====================================================================================
TAN = ["#c8986e", "#b8885e", "#d4a47a"]
FAIR = ["#ecc8aa", "#e0bc9e", "#f4d2b4"]
ROSE = ["#e8b89c", "#dcac90", "#f0c4a8"]
OLIVE = ["#c09068", "#b0825c", "#cc9c74"]
BROWN = ["#9a6648", "#8a5a3e", "#a67052"]
DARK = ["#6a4430", "#5c3a28", "#764c36"]
WEATHER = ["#b88262", "#a87456", "#c48e6c"]


def polly(seed):                                  # Polly Marlowe, the parrot keeper (#25): feathers everywhere
    m, a = person("folk_polly", seed, skin=FAIR, hair="#c8642a", eye="#3a7a3a", hair_style="braids",
                  top=bands(C("#2a8a5a", "#248050", "#309a64"), (0, 2, C("#e8e0c8"))), legs=C("#5a3a22", "#4e321e"), skirt=C("#e0b030", "#d4a428", "#e8bc3a"),
                  apron=over(C("#ece4cc", "#e0d8c0"), spots("#6a5a3a", 0.04)), facekw=dict(freckles="#b06a3a", lips="#c05050", rosy="#e89a8a"))
    t = a["top"]
    brim_hat(m, t, "#d8c088", band="#c83030", wide=6, crown=3.5, h=2)
    for i, (c, r) in enumerate((("#e83030", 20), ("#3070e8", -5), ("#f0d020", 35))):     # feathers in the hat band
        m.cube("head", [2 + i * 0.6, t + 9, 2.5], [0.6, 5, 1.6], C(c), rot=[r, 0, -15])
    # a little green parrot on her shoulder
    m.bone("pet", [5, t, 0], "body")
    m.cube("pet", [4.5, t, -1.5], [2.5, 3, 3], C("#3ab040", "#2ea038"), art={"north": A.at(3, 3, {(0, 0): "e", (2, 0): "e", (1, 1): "k"}, e="#101010", k="#f0c020")})
    m.cube("pet", [5, t + 3, -1.25], [2, 2, 2.5], C("#3ab040"), art={"north": A.at(2, 2, {(0, 0): "e", (1, 1): "k"}, e="#101010", k="#e0a020")})
    m.cube("pet", [5.25, t - 2, 1], [1.5, 2.5, 1], C("#e83030"))
    k = kit(m, a, "r")
    m.cube(k, [-7, t - 15, -3.5], [2, 3, 3], C("#a07a3a"), art={"up": A("ss\nss\nss", s="#d8c060")})          # a seed scoop
    m.anims.clear()
    finish(m, a, "look_around")
    anim(m, "idle", 3.0, {**m.anims["idle"]["bones"], "pet": {"rotation": wave(3.0, lambda q: [0, S(2 * q) * 20, S(q) * 6])}})
    return m


def snip(seed):                                   # Bartholomew Snip, the barber (#38): waxed moustache, striped waistcoat
    m, a = person("folk_snip", seed, skin=ROSE, hair="#1a1410", eye="#3a2a1a", hair_style="parted",
                  top=bands(over(C("#e8e4dc"), ), (3, 12, bands(C("#b82828"), *[(y, y + 1, C("#ece4d8")) for y in range(3, 12, 2)]))),
                  sleeve=C("#ece8e0", "#e0dcd2"), legs=C("#2a2a30", "#323238"), boots="#141414",
                  facekw=dict(stache="#1a1410", brows="#1a1410"), buttons="#d8c060", collar=C("#f4f0e8"))
    t = a["top"]
    m.cube("head", [-6, t + 2, -4.6], [2, 1, 1], C("#1a1410"), rot=[0, 0, 25]); m.cube("head", [4, t + 2, -4.6], [2, 1, 1], C("#1a1410"), rot=[0, 0, -25])
    m.cube("body", [-1.5, t - 2, -2.6], [3, 1.5, 0.5], C("#2a2a6a"))                                      # bow tie
    m.cube("body", [-1, 13, 2.2], [2, 4, 1], C("#e8e8e8"))                                                 # comb in the back pocket
    k = kit(m, a, "r")
    m.cube(k, [-6.5, t - 15, -4.5], [0.8, 1, 5], C("#c8ccd4", "#e0e4ec")); m.cube(k, [-6.5, t - 15, -0.5], [1.6, 2, 1.2], C("#c8ccd4"))  # scissors
    k = kit(m, a, "l")
    m.cube(k, [5.5, t - 15, -4.5], [1.6, 1.4, 4], C("#ece4d4")); m.cube(k, [5.4, t - 14, -5.5], [1.8, 1.8, 1.2], C("#f8f8f0"))           # shaving brush
    return finish(m, a, "scratch")


def inka(seed):                                   # Inka Rook, the tattooist (#33): sleeves of ink, a shaved side, rings
    INK = over(noise(DARK), spots("#2a3a6a", 0.25), spots("#6a1a2a", 0.08))
    m, a = person("folk_inka", seed, skin=DARK, hair="#1a1214", eye="#c87a2a", hair_style="swept",
                  top=C("#1e1e22", "#26262a"), sleeve=C("#1e1e22"), legs=C("#3a2a22", "#2e221c"), boots="#1a1414",
                  facekw=dict(lips="#5a2030", scar="#4a2a20"), belt="#5a3a22")
    t = a["top"]
    m.cube("rarm", [-8, t - 12, -2], [4, 10, 4], INK, inflate=0.05); m.cube("larm", [4, t - 12, -2], [4, 10, 4], INK, inflate=0.05)
    m.cube("head", [-4.5, t + 5, -4.5], [6, 4, 9.2], C("#1a1214", "#241a1c"))                                # the long side, swept over
    m.cube("head", [-2, t + 8.5, -4.6], [3, 1.5, 9], C("#7a1a6a"))                                           # a dyed streak
    for y in (2, 4):
        m.cube("head", [4.1, t + y, -0.5], [0.5, 1, 1], C("#d8b040"))                                     # earrings
    k = kit(m, a, "r")
    m.cube(k, [-6.6, t - 15, -5], [0.9, 0.9, 5], C("#2a2a2a")); m.cube(k, [-6.7, t - 15.1, -5.6], [1.1, 1.1, 0.8], C("#c8ccd4"))       # needle
    return finish(m, a, "cross_arms")


def agatha(seed):                                 # Agatha Purr, the cat keeper (#36): shawl, round spectacles, a cat in her arms
    m, a = person("folk_agatha", seed, skin=FAIR, hair="#d8d4cc", eye="#5a7a9a", hair_style="bun",
                  top=C("#6a4a7a", "#5e4270"), legs=C("#3a2a2a"), skirt=C("#4a3a5a", "#42344f"), boots="#2a1e1a",
                  facekw=dict(glasses="#8a7a5a", wrinkles=True, rosy="#e0a090", lips="#b06060"))
    t = a["top"]
    m.cube("body", [-4.6, t - 6, -2.7], [9.2, 6, 5.4], over(C("#c87a8a", "#b86e7e"), spots("#f0d0d8", 0.1)))   # knitted shawl
    m.cube("body", [-1, t - 9, -2.9], [2, 4, 0.5], C("#c87a8a"))
    m.cube("head", [-2, t + 6.5, 3], [4, 3, 2], C("#d8d4cc")); m.cube("head", [-0.25, t + 8, 4], [0.5, 0.5, 4], C("#8a6a3a"), rot=[30, 0, 0])
    k = kit(m, a, "l")                                                                                    # a ginger cat held at the hip
    CAT = over(C("#d8822a", "#c87420"), spots("#f0b060", 0.15))
    m.cube(k, [3, t - 15, -6], [6, 3, 3], CAT); m.cube(k, [3.5, t - 13.5, -8], [3, 3, 3], CAT,
                                                      art={"north": A.at(3, 3, {(0, 0): "e", (2, 0): "e", (1, 2): "n"}, e="#3a8a30", n="#e08080")})
    m.cube(k, [3.5, t - 10.5, -8], [1, 1, 1], CAT); m.cube(k, [5.5, t - 10.5, -8], [1, 1, 1], CAT)
    m.cube(k, [8.5, t - 14, -3.5], [1, 1, 4], CAT, rot=[-30, 0, 0])
    return finish(m, a, "yawn")


def isadora(seed):                                # Isadora Vane, the marine painter (#41): beret, smock spattered with paint
    SMOCK = over(C("#e8e0d0", "#ddd4c2"), spots("#d83030", 0.03), spots("#3050c8", 0.03), spots("#e8c830", 0.03), spots("#40a040", 0.02))
    m, a = person("folk_isadora", seed, skin=OLIVE, hair="#2a1a14", eye="#2a5a8a", hair_style="long",
                  top=SMOCK, legs=C("#2a3a5a", "#24324e"), boots="#3a2418", facekw=dict(lips="#b04848", brows="#1a100c"))
    t = a["top"]
    beret(m, t, "#a02838")
    m.cube("body", [-4.6, 3, -2.7], [9.2, 9.5, 5.4], SMOCK)                                                 # the long smock
    m.cube("body", [-2, t - 2.5, -2.7], [4, 1.5, 0.5], C("#3050c8"))                                       # a neckerchief
    m.cube("head", [3.5, t + 7.5, -1], [0.6, 0.6, 5], C("#c8a060"), rot=[0, 20, 0])                         # brush behind the ear
    return finish(m, a, "look_around")


def gideon(seed):                                 # Gideon Tock, the toymaker (#35): magnifier goggles, a wind-up toy
    m, a = person("folk_gideon", seed, skin=ROSE, hair="#e8e4dc", eye="#4a6a3a", hair_style="wild",
                  top=C("#5a6a3a", "#4e5e34"), sleeve=C("#e8e0cc"), legs=C("#5a4a3a"), belly=True,
                  apron=over(C("#8a5a32", "#7a4e2a"), spots("#c8a040", 0.03)), facekw=dict(stache="#e8e4dc", rosy="#e88a80", wrinkles=True))
    t = a["top"]
    m.cube("head", [-4.5, t + 4.5, -4.9], [9, 1, 0.6], C("#3a2a1a"))
    m.cube("head", [-3.5, t + 4, -5.6], [2.5, 2.5, 1], C("#c8a040"), art={"north": A("gg\ngg", g="#9ad8e8")})
    m.cube("head", [1, t + 4, -5.6], [2.5, 2.5, 1], C("#c8a040"), art={"north": A("gg\ngg", g="#9ad8e8")})
    m.cube("body", [1, 5, -3], [2, 3, 0.6], C("#a8a8a8"))                                                  # pocket of tools
    k = kit(m, a, "r")                                                                                    # a wind-up soldier
    m.cube(k, [-7.5, t - 16, -3], [2.5, 4, 2], C("#c82828"), art={"north": A.at(3, 4, {(1, 0): "f", (1, 1): "b"}, f="#f0c8a0", b="#d8c040")})
    m.cube(k, [-7.25, t - 12, -2.75], [2, 1.5, 1.5], C("#1a1a1a"))
    m.cube(k, [-6.6, t - 15, -1], [0.6, 0.6, 1.5], C("#d8c040"))
    return finish(m, a, "laugh")


def aldous(seed):                                 # Governor Aldous Thorne (#44 Fort): bicorne, epaulettes, sash, sword
    COAT = C("#1e2a5a", "#18234e", "#243266")
    m, a = person("folk_aldous", seed, skin=FAIR, hair="#e8e8e4", eye="#3a4a6a", hair_style="pony",
                  top=bands(COAT, (0, 1, C("#e0c050"))), sleeve=COAT, legs=C("#ece8dc", "#e0dccc"), boots="#141214",
                  coat=COAT, cuffs="#e0c050", buttons="#e0c050", facekw=dict(brows="#c8c8c4", wrinkles=True))
    t = a["top"]
    bicorne(m, t)
    m.cube("rarm", [-8.5, t - 2.5, -2.5], [5, 1.5, 5], C("#e0c050", "#d0b040")); m.cube("larm", [3.5, t - 2.5, -2.5], [5, 1.5, 5], C("#e0c050", "#d0b040"))
    m.cube("body", [-1.25, 11, -2.45], [2.5, 14, 0.5], C("#b02030", "#a01a28"), rot=[0, 0, 38], pivot=[0, 18, -2.2])                    # the sash
    m.cube("body", [-4.3, 18, -2.6], [2, 2, 0.6], C("#e0c050"))                                                # a medal
    m.cube("body", [4.2, 3, -1], [0.8, 10, 1.5], C("#c8ccd4", "#e0e4ec"), rot=[15, 0, 0])                      # the sword at his hip
    m.cube("body", [4, 12, -1.5], [1.2, 2, 2.5], C("#e0c050"))
    k = kit(m, a, "r")
    m.cube(k, [-7, t - 16, -3], [2, 5, 2], C("#e8e0c8"), art={"up": A("ss\nss", s="#c83030")})                 # a rolled decree with a seal
    return finish(m, a, "pocketwatch")


def quill(seed):                                  # Harbourmaster Edwina Quill (#11): oilskin coat, tide tables, pipe
    m, a = person("folk_quill", seed, skin=WEATHER, hair="#7a7a78", eye="#2a4a5a", hair_style="bun",
                  top=C("#2a3a4a", "#24323f"), legs=C("#3a3a3a"), coat=C("#3a4a2a", "#33422a"), boots="#1e1a16",
                  belt="#3a2418", facekw=dict(lips="#904848", brows="#5a5a58", wrinkles=True))
    t = a["top"]
    cap(m, t, "#1e2a3a", peak="#0e141e")
    m.cube("head", [-1, t + 7.6, -4.7], [2, 1.2, 0.4], C("#d8b040"))                                         # cap badge (an anchor)
    m.cube("head", [-3.5, t + 0.5, -6], [0.8, 0.8, 2.5], C("#5a3a20")); m.cube("head", [-3.9, t + 0.4, -7], [1.6, 1.8, 1.4], C("#3a2418"))  # pipe
    k = kit(m, a, "l")
    m.cube(k, [5, t - 16, -4], [1.5, 6, 5], C("#e8e0c8"), art={"east": A.at(5, 6, {(x, y): "k" for x in range(5) for y in (1, 3, 5)}, k="#4a5a7a")})
    return finish(m, a, "spyglass")


def martha(seed):                                 # Martha Goodbarrel, the innkeeper (#8): mob cap, rosy cheeks, keys
    m, a = person("folk_martha", seed, skin=ROSE, hair="#8a4a2a", eye="#5a3a2a", hair_style="curly",
                  top=C("#3a6a8a", "#346080"), sleeve=C("#e8e0cc"), legs=C("#3a2a2a"), skirt=C("#2a4a6a", "#244260"), belly=True,
                  apron=C("#f4efe2", "#ece6d6"), facekw=dict(rosy="#e07a6a", lips="#c05050"))
    t = a["top"]
    m.cube("head", [-4.75, t + 6, -4.75], [9.5, 3, 9.5], C("#f4efe2", "#e8e2d4"))                         # mob cap
    m.cube("head", [-5, t + 5.5, -5], [10, 0.75, 10], C("#f4efe2"))
    m.cube("body", [3.6, 9, -1], [1, 3, 1], C("#c8a040"))                                                     # the key ring
    for i in range(3):
        m.cube("body", [4.2, 6 + i * 0.6, -1.5 + i], [0.5, 2.5, 0.5], C("#a8a8a8"))
    k = kit(m, a, "r")
    m.cube(k, [-7.5, t - 15.5, -4], [3, 0.5, 5], C("#d8d8d0")); m.cube(k, [-7, t - 15, -3], [2, 1.5, 2], C("#c8803a"))   # a plate with a pie
    return finish(m, a, "sweep")


def silas(seed):                                  # Old Silas Lamp, the lighthouse keeper (#43): sou'wester, a lit lantern
    m, a = person("folk_silas", seed, skin=WEATHER, hair="#d8d4cc", eye="#4a6a8a", hair_style="fringe",
                  top=bands(C("#2a3a5a"), *[(y, y + 1, C("#e8e4dc")) for y in range(0, 12, 3)]), sleeve=C("#d8b020", "#c8a018"),
                  legs=C("#d8b020", "#c8a018"), boots="#1e1e1e", coat=C("#d8b020", "#c8a018"),
                  facekw=dict(beard="#e8e4dc", stache="#e8e4dc", wrinkles=True, closed=False))
    t = a["top"]
    m.cube("head", [-5, t + 6.5, -5.5], [10, 2.5, 10.5], C("#d8b020", "#c8a018"))
    m.cube("head", [-5.5, t + 6, 1.5], [11, 1, 5.5], C("#d8b020"), rot=[18, 0, 0])                           # the long back brim
    m.cube("head", [-4.4, t - 4, -4.6], [8.8, 4, 1], C("#e8e4dc", "#dcd8d0"))                               # a long white beard
    k = kit(m, a, "l")
    m.cube(k, [4.5, t - 18, -4], [3, 4, 3], C("#2a2a2a"), art={"sides": A("...\n.g.\n.g.\n...", g="!f0c060")})
    m.cube(k, [5.5, t - 14, -3], [1, 1.5, 1], C("#2a2a2a"))
    return finish(m, a, "spyglass")


def anselm(seed):                                 # Father Anselm, the chapel priest (#16): black cassock, white collar
    m, a = person("folk_anselm", seed, skin=FAIR, hair="#8a8a88", eye="#5a6a7a", hair_style="receding",
                  top=C("#1a1a1e", "#202024"), legs=C("#1a1a1e"), skirt=C("#1a1a1e", "#202024"), boots="#101010",
                  facekw=dict(glasses="#a8a8a8", brows="#7a7a78", wrinkles=True), buttons="#4a4a50")
    t = a["top"]
    m.cube("body", [-1, t - 1.4, -2.3], [2, 1.2, 0.5], C("#f4f4f0"))                                        # the white tab
    m.cube("body", [-0.5, 12, -2.4], [1, 5, 0.4], C("#d8b040")); m.cube("body", [-1.5, 15, -2.4], [3, 1, 0.4], C("#d8b040"))   # a cross
    m.cube("head", [-2, t + 8, -2], [4, 0.6, 4], C("#1a1a1e"))
    k = kit(m, a, "l")
    m.cube(k, [5, t - 15.5, -4], [2, 4, 3], C("#6a1a1a"), art={"east": A.at(3, 4, {(1, 1): "g", (1, 2): "g"}, g="#d8b040")})      # a prayer book
    return finish(m, a, "bless")


def brannoc(seed):                                # Brannoc Ironhand, the blacksmith (#19): leather apron, huge arms, a hammer
    m, a = person("folk_brannoc", seed, skin=BROWN, hair="#2a1a10", eye="#3a2a1a", hair_style="bald",
                  top=C("#5a4a3a", "#4e4032"), sleeve=noise(BROWN), legs=C("#3a3028"), boots="#1e1612",
                  apron=over(C("#4a2e1a", "#3e2616"), spots("#1a1a1a", 0.05)), facekw=dict(beard="#2a1a10", brows="#1a1008", scar="#7a4a3a"))
    t = a["top"]
    m.cube("rarm", [-8.5, t - 7, -2.5], [5, 5, 5], noise(BROWN)); m.cube("larm", [3.5, t - 7, -2.5], [5, 5, 5], noise(BROWN))
    m.cube("rarm", [-8.6, t - 12.2, -2.6], [5.2, 3, 5.2], C("#3a2418"))                                     # leather bracers
    m.cube("larm", [3.4, t - 12.2, -2.6], [5.2, 3, 5.2], C("#3a2418"))
    m.cube("head", [-4.4, t - 3, -4.6], [8.8, 3, 1], C("#2a1a10")); m.cube("head", [-1, t - 4.5, -4.6], [2, 2, 1], C("#2a1a10"))   # forked beard
    m.cube("head", [-4.2, t + 3, -4.4], [8.4, 1, 0.3], C("#5a3a20"))
    k = kit(m, a, "r")
    m.cube(k, [-6.5, t - 17, -1.5], [1, 6, 1], C("#5a3a22")); m.cube(k, [-8, t - 18.5, -2.5], [4, 2, 3], C("#6a6a70", "#7a7a80"))   # hammer
    return finish(m, a, "hammer")


def hob(seed):                                    # Hob Furrow, the farmer (#46): straw hat, smock, a pitchfork
    m, a = person("folk_hob", seed, skin=TAN, hair="#a8743a", eye="#4a6a2a", hair_style="fringe",
                  top=C("#c8b890", "#bcac84"), legs=C("#5a6a3a", "#4e5e34"), boots="#4a2e1a", belt="#5a3a20",
                  facekw=dict(freckles="#a8643a", goatee="#a8743a"))
    t = a["top"]
    brim_hat(m, t, "#e0c878", band="#a03030", wide=7, crown=3.5, h=3)
    m.cube("body", [-2.5, 12.5, -2.4], [1, 11, 0.5], C("#3a2a1a")); m.cube("body", [1.5, 12.5, -2.4], [1, 11, 0.5], C("#3a2a1a"))   # braces
    m.cube("head", [-0.4, t + 1.5, -5.6], [0.4, 0.4, 3], C("#e8d880"), rot=[0, 25, 0])                     # a stalk of wheat
    k = kit(m, a, "r")
    m.cube(k, [-6.5, t - 22, -1], [1, 20, 1], C("#8a6a40")); m.cube(k, [-7.5, t - 3, -1], [3, 1, 1], C("#6a6a6a"))
    for x in (-7.5, -6.5, -5.5):
        m.cube(k, [x, t - 1.5, -1], [0.5, 3, 0.5], C("#7a7a7a"))
    return finish(m, a, "stretch", hold=[-15, 0, 0])


def elspeth(seed):                                # Elspeth Fleece, the shepherdess (#48): woolly shawl, crook
    WOOL = over(C("#f0ece0", "#e4e0d2"), speckle("#d0ccc0", 0.2))
    m, a = person("folk_elspeth", seed, skin=FAIR, hair="#e0c060", eye="#3a6aa8", hair_style="pony_long",
                  top=C("#7a8a4a", "#6e7e42"), legs=C("#4a3a2a"), skirt=C("#6a4a2a", "#5e4226"), boots="#3a2414",
                  facekw=dict(rosy="#f0a090", lips="#c06060", freckles="#d8a080"))
    t = a["top"]
    m.cube("body", [-4.6, t - 6, -2.7], [9.2, 6, 5.4], WOOL, inflate=0.1)
    kerchief(m, t, "#5a7ac8")
    k = kit(m, a, "r")
    m.cube(k, [-6.5, t - 18, -1], [1, 22, 1], C("#a07a4a", "#906a3e"))
    m.cube(k, [-6.5, t + 4, -1], [1, 1, 3], C("#a07a4a")); m.cube(k, [-6.5, t + 2, 1], [1, 2, 1], C("#a07a4a"))
    return finish(m, a, "look_around", hold=[-15, 0, 0])


def cobb(seed):                                   # Cobb Appleby, the cider maker (#39): ruddy, round, a jug of cider
    m, a = person("folk_cobb", seed, skin=ROSE, hair="#6a3a1a", eye="#4a3a2a", hair_style="receding",
                  top=C("#8a3a2a", "#7a3224"), sleeve=C("#e8e0cc"), legs=C("#4a3a2a"), belly=True, belt="#2a1a10",
                  apron=C("#d8c8a0", "#ccbc94"), facekw=dict(rosy="#d85a4a", stache="#6a3a1a", brows="#5a2a10"))
    t = a["top"]
    m.cube("body", [-4.8, 13.5, -3.6], [9.6, 5, 1], C("#8a3a2a"))
    m.cube("head", [-4.5, t + 6, -4.5], [9, 1.5, 9], C("#5a7a3a"))                                           # a green cap with an apple
    m.cube("head", [-1, t + 7.5, -1], [2, 2, 2], C("#c82828")); m.cube("head", [-0.25, t + 9.5, -0.25], [0.5, 1, 0.5], C("#5a3a1a"))
    k = kit(m, a, "r")
    m.cube(k, [-8, t - 17, -2.5], [4, 5, 4], C("#c8a878", "#b89868"), art={"north": A.at(4, 5, {(1, 2): "x", (2, 2): "x"}, x="#3a2a1a")})
    m.cube(k, [-7, t - 12, -1.5], [2, 1.5, 2], C("#c8a878")); m.cube(k, [-4.5, t - 15.5, -0.5], [1, 3, 1], C("#c8a878"))
    return finish(m, a, "laugh")


def wilma(seed):                                  # Wilma Grist, the miller (#31): flour-dusted, kerchief, a sack
    FLOUR = lambda base: over(C(base, _shade(base, 0.9)), speckle("#f4f0e8", 0.18))
    m, a = person("folk_wilma", seed, skin=TAN, hair="#3a2a1a", eye="#5a4a2a", hair_style="fringe",
                  top=FLOUR("#8a6a4a"), sleeve=FLOUR("#e0d8c4"), legs=C("#4a3a2a"), skirt=FLOUR("#6a5a4a"),
                  apron=FLOUR("#f0e8d8"), facekw=dict(lips="#a04a40", rosy="#d8907a"))
    t = a["top"]
    kerchief(m, t, "#c8b080")
    m.cube("head", [-4.3, t + 3, -4.4], [1, 1, 0.3], C("#f4f0e8")); m.cube("head", [2.5, t + 1.5, -4.4], [1, 1, 0.3], C("#f4f0e8"))   # flour on her face
    k = kit(m, a, "l")
    m.cube(k, [4, t - 17, -3.5], [5, 6, 5], FLOUR("#d8c8a0"), art={"north": A.at(5, 6, {(2, 2): "w", (1, 3): "w", (2, 3): "w", (3, 3): "w"}, w="#c84040")})
    m.cube(k, [5, t - 11, -2.5], [3, 1.5, 3], C("#a08a60"))
    return finish(m, a, "sweep")


def zora(seed):                                   # Madame Zora, the fortune teller (#32): coin headscarf, shawl, crystal ball
    m, a = person("folk_zora", seed, skin=OLIVE, hair="#140e0e", eye="#7a3a8a", hair_style="long",
                  top=C("#6a1a4a", "#5e163f"), legs=C("#2a1a2a"), skirt=over(C("#3a1a5a", "#341650"), spots("#d8b040", 0.04)),
                  boots="#1a1014", facekw=dict(lips="#8a1a3a", brows="#0e0808"))
    t = a["top"]
    m.cube("head", [-4.75, t + 5.5, -4.75], [9.5, 3.5, 9.5], C("#c83a6a", "#b83060"),
           art={"north": A.at(10, 4, {(x, 3): "g" for x in range(0, 10, 2)}, g="#e8c040")})
    m.cube("head", [-1, t + 4.7, -5], [2, 1.5, 0.5], lit("#5ae8d0"))                                     # a jewel on the brow
    for y in (1, 3):
        m.cube("head", [-4.6, t + y, -0.5], [0.5, 1.5, 1], C("#e8c040")); m.cube("head", [4.1, t + y, -0.5], [0.5, 1.5, 1], C("#e8c040"))
    m.cube("body", [-4.6, t - 5, -2.7], [9.2, 5, 5.4], over(C("#2a1a6a"), spots("#e8c040", 0.08)))       # a starry shawl
    for i in range(3):
        m.cube("rarm", [-8.2, t - 11 + i * 0.8, -2.2], [4.4, 0.5, 4.4], C("#e8c040"))                       # bangles
    k = kit(m, a, "l")
    m.cube(k, [4, t - 17, -5], [4, 4, 4], lit("#a8c8f0"), art={"all": A.at(4, 4, {(1, 1): "w"}, w="!ffffff")})
    m.cube(k, [4.5, t - 18, -4.5], [3, 1, 3], C("#5a3a20"))
    return finish(m, a, "crystal")


def ned(seed):                                    # Ned Barlow, Captain Wren's old first mate (#21): one leg, a long coat
    m, a = person("folk_ned", seed, skin=WEATHER, hair="#a8a8a0", eye="#5a7a8a", hair_style="pony",
                  top=C("#e0d4b4", "#d4c8a8"), sleeve=C("#5a3a22"), legs=C("#3a2e24"), coat=C("#5a3a22", "#4e321e"),
                  boots="#2a1e14", facekw=dict(beard="#a8a8a0", stache="#a8a8a0", wrinkles=True, scar="#8a5a4a"), belt="#2a1e14", cuffs="#c8a040")
    t = a["top"]
    tricorn(m, t, "#3a2a20", trim="#8a6a3a")
    m.cube("lleg", [0.5, 0, -1.5], [3, 6.5, 3], C("#8a6a40", "#7a5e38"))                                     # the peg leg
    m.cube("body", [-4.2, 16, -2.6], [3, 3, 0.6], C("#c8a040"))                                              # a music box key on a cord
    k = kit(m, a, "r")
    m.cube(k, [-7, t - 22, -1], [1.2, 12, 1.2], C("#6a4a2a")); m.cube(k, [-7.4, t - 11, -1.4], [2, 1.5, 2], C("#c8a040"))   # a stick
    return finish(m, a, "look_around", hold=[-8, 0, 0])


def rufus(seed):                                  # Captain Rufus Brine, a retired pirate (#22): eyepatch, red coat, a hook
    COAT = C("#8a1a1a", "#7a1616", "#9a2222")
    m, a = person("folk_rufus", seed, skin=TAN, hair="#3a2010", eye="#4a3a2a", hair_style="long",
                  top=C("#e8e0c8"), sleeve=COAT, legs=C("#2a2420"), coat=COAT, cuffs="#d8b040", boots="#1a1410",
                  facekw=dict(patch="#141010", beard="#3a2010", stache="#3a2010", scar="#7a4a3a"), belt="#2a1a10")
    t = a["top"]
    tricorn(m, t, "#1a1214", trim="#e0c050")
    m.cube("head", [-1, t + 10, -6.2], [2, 2, 0.5], C("#e8e4dc"), art={"north": A("kk\nkk", k="#e8e4dc")})   # a skull on the hat
    m.cube("larm", [5.25, t - 15, -0.75], [1.5, 3, 1.5], C("#a8acb4")); m.cube("larm", [5.25, t - 16.5, -2.5], [1.5, 1.5, 3], C("#c8ccd4"))   # the hook
    m.cube("body", [-4, 12.5, -3], [3, 3, 1], C("#d8b040"))                                                  # belt buckle
    m.cube("body", [-4.6, 5, -1.2], [0.8, 8, 1.2], C("#c8ccd4"), rot=[0, 0, -10])                            # an old cutlass
    k = kit(m, a, "r")
    m.cube(k, [-7.5, t - 16, -3], [3, 3, 3], C("#3a2a14"), art={"up": A("rrr\nrrr\nrrr", r="#a85a20")})      # a cup of rum
    return finish(m, a, "laugh")


def finn(seed):                                   # Finn Gale, fisherman (#23): beanie, gansey, a rod
    m, a = person("folk_finn", seed, skin=TAN, hair="#c8a060", eye="#4a7a9a", hair_style="fringe",
                  top=over(C("#1e3a5a", "#1a3450"), speckle("#2a4a6a", 0.2)), legs=C("#d8b020"), boots="#1a1a1a",
                  facekw=dict(goatee="#c8a060", freckles="#a8784a"))
    t = a["top"]
    beanie(m, t, "#c84a2a", bobble=None)
    k = kit(m, a, "r")
    m.cube(k, [-6.5, t - 15, -2], [1, 1, 18], C("#8a6a40"), rot=[-50, 0, 0], pivot=[-6, t - 14.5, -1.5])
    m.cube(k, [-7.5, t - 16, -2], [2, 2, 2], C("#5a5a5a"))
    return finish(m, a, "yawn")


def nell(seed):                                   # Nell Suds, the washerwoman (#24): rolled sleeves, a basket of laundry
    m, a = person("folk_nell", seed, skin=BROWN, hair="#1a1210", eye="#4a2a1a", hair_style="curly",
                  top=C("#c86a8a", "#b85e7e"), sleeve=noise(BROWN), legs=C("#3a2a2a"), skirt=C("#5a4a8a", "#50427e"),
                  apron=C("#f0ece0"), facekw=dict(lips="#7a2a3a", rosy="#a85a4a"))
    t = a["top"]
    kerchief(m, t, "#e8c040")
    k = kit(m, a, "l")
    m.cube(k, [3.5, t - 18, -5.5], [6, 4, 6], C("#c8a060", "#b89050"), art={"up": A("wwwwww\nwbbwww\nwwwwrw\nwwbwww\nwwwwww\nwrwwww", w="#f4f4f0", b="#8aa8d8", r="#d88a8a")})
    return finish(m, a, "sweep")


def jack(seed):                                   # Jack Tarr, a sailor ashore (#26): striped shirt, bell-bottoms, a sea bag
    m, a = person("folk_jack", seed, skin=FAIR, hair="#1a1a1a", eye="#3a5a8a", hair_style="crop",
                  top=bands(C("#f0f0ec"), *[(y, y + 1, C("#1e2e6a")) for y in range(0, 12, 2)]), legs=C("#1e2e6a", "#1a2860"),
                  boots="#141414", facekw=dict(scar="#b88a7a"))
    t = a["top"]
    m.cube("head", [-4.4, t + 7, -4.4], [8.8, 1.6, 8.8], C("#f4f4f0")); m.cube("head", [-4.6, t + 7, -4.6], [9.2, 0.6, 9.2], C("#1e2e6a"))   # sailor's cap
    m.cube("body", [-4.2, t - 3, 2.1], [8.4, 3, 0.5], C("#1e2e6a"), art={"south": A.at(8, 3, {(x, 1): "w" for x in range(8)}, w="#f0f0ec")})   # sailor collar
    m.cube("body", [-1.25, t - 4, -2.4], [2.5, 2, 0.5], C("#202020"))
    m.cube("larm", [4.1, t - 7, -2.1], [3.9, 3, 0.2], C("#1e2e6a"), art={"north": A("....\n.rr.\n....", r="#c83030")})   # an anchor tattoo
    k = kit(m, a, "l")
    m.cube(k, [4, t - 24, -3], [5, 10, 5], C("#d8ccaa", "#ccc09e"), art={"up": A("kkkkk\nk...k\nk...k\nk...k\nkkkkk", k="#5a4a30")})
    return finish(m, a, "stretch")


def pettigrew(seed):                              # Commodore Pettigrew (ret.), a navy man (#27): monocle, white whiskers
    COAT = C("#1a2440", "#16203a")
    m, a = person("folk_pettigrew", seed, skin=ROSE, hair="#f0f0ec", eye="#3a4a6a", hair_style="receding",
                  top=COAT, legs=C("#ece8dc"), coat=COAT, cuffs="#d8b040", buttons="#d8b040", boots="#101010",
                  facekw=dict(monocle="#d8b040", stache="#f0f0ec", rosy="#e07a6a", wrinkles=True))
    t = a["top"]
    m.cube("head", [-5.25, t + 0.5, -4.6], [1.5, 3, 2], C("#f0f0ec")); m.cube("head", [3.75, t + 0.5, -4.6], [1.5, 3, 2], C("#f0f0ec"))   # mutton chops
    m.cube("head", [-0.5, t + 3, -4.7], [0.25, 3, 0.25], C("#d8b040"))                                       # monocle chain
    k = kit(m, a, "r")
    m.cube(k, [-6.5, t - 22, -1], [1, 12, 1], C("#2a1a10")); m.cube(k, [-7, t - 11, -1.5], [2, 1.5, 2], C("#e0c050"))       # cane
    return finish(m, a, "pocketwatch", hold=[-8, 0, 0])


def rosalind(seed):                               # Rosalind Fairweather, a widow with a parasol (#28)
    m, a = person("folk_rosalind", seed, skin=FAIR, hair="#5a2a1a", eye="#5a3a6a", hair_style="bun",
                  top=C("#5a2a4a", "#4e2440"), legs=C("#2a1a2a"), skirt=C("#4a2040", "#401a38"), boots="#1a1018",
                  facekw=dict(lips="#a03a4a", brows="#4a2010"), collar=C("#f0e8e0"), buttons="#e8d8c0")
    t = a["top"]
    bonnet(m, t, "#3a1a30", "#e8d8c0")
    m.cube("head", [-4.9, t + 9, -3.5], [1, 2, 2], C("#e8b0c0")); m.cube("head", [-5, t + 10.5, -3], [1.2, 1.2, 1.2], C("#f0d0e0"))   # a silk flower
    k = kit(m, a, "r")
    m.cube(k, [-6.5, t - 18, -1], [0.6, 18, 0.6], C("#2a2a2a"))
    m.cube(k, [-10, t, -4.5], [8, 1.5, 8], C("#e8b8c8", "#dcacbc"), art={"up": A("rrrrrrrr\nrwwwwwwr\nrwrrrrwr\nrwrwwrwr\nrwrwwrwr\nrwrrrrwr\nrwwwwwwr\nrrrrrrrr", r="#c87890", w="#f0d0dc")})
    m.cube(k, [-8, t + 1.5, -2.5], [4, 1, 4], C("#e8b8c8"))
    return finish(m, a, "tip_hat")


def dan(seed):                                    # Fiddler Dan (#29): patched waistcoat, a fiddle
    m, a = person("folk_dan", seed, skin=TAN, hair="#8a3a1a", eye="#3a6a3a", hair_style="wild",
                  top=over(C("#5a6a2a", "#4e5e24"), spots("#8a4a2a", 0.05, size=2)), sleeve=C("#e0d8c0"), legs=C("#4a3a5a"),
                  facekw=dict(freckles="#a8603a", stache="#8a3a1a"), belt="#3a2414", boots="#2a1a12")
    t = a["top"]
    brim_hat(m, t, "#3a2a2a", band="#c8a040", wide=5.5, crown=3.25, h=4)
    m.cube("head", [3, t + 10, 1], [0.6, 4, 1.6], C("#4a8a3a"), rot=[0, 0, -20])
    k = kit(m, a, "l")
    m.cube(k, [4, t - 15, -5.5], [3, 1.5, 7], C("#a0582a", "#904e24"), art={"up": A("kkk\n...\n.k.\n...\n.k.\n...\n...", k="#2a1a10")})
    m.cube(k, [4.75, t - 14.8, -10], [1.5, 1, 4.5], C("#2a1a10"))
    k = kit(m, a, "r")
    m.cube(k, [-7, t - 15, -8], [0.5, 0.5, 9], C("#c8a060"), rot=[0, 30, 0])
    return finish(m, a, "fiddle")


def ptolemy(seed):                                # Ptolemy Inkwell, astronomer + cartographer (#34): star robe, tall hat
    ROBE = over(C("#1a2a5a", "#16244e"), spots("#e8e0a0", 0.05))
    m, a = person("folk_ptolemy", seed, skin=FAIR, hair="#c8c4bc", eye="#4a5a7a", hair_style="long",
                  top=ROBE, legs=C("#1a2a5a"), skirt=ROBE, boots="#1a1418", facekw=dict(glasses="#c8a040", beard="#c8c4bc", stache="#c8c4bc"))
    t = a["top"]
    m.cube("head", [-4.4, t - 5, -4.6], [8.8, 5, 1], C("#c8c4bc", "#bcb8b0"))
    m.cube("head", [-4.5, t + 7.5, -4.5], [9, 1, 9], ROBE); m.cube("head", [-3, t + 8.5, -3], [6, 4, 6], ROBE); m.cube("head", [-1.75, t + 12.5, -1.75], [3.5, 3, 3.5], ROBE)
    m.cube("head", [-0.75, t + 15.5, -0.75], [1.5, 1.5, 1.5], lit("#f0e080"))
    k = kit(m, a, "r")
    m.cube(k, [-7.5, t - 17, -3], [3, 3, 7], C("#c8a040", "#b89030"), rot=[-20, 0, 0])                   # a brass telescope
    k = kit(m, a, "l")
    m.cube(k, [5, t - 17, -2], [1.5, 7, 1.5], C("#e8e0c8"))                                              # a rolled chart
    return finish(m, a, "spyglass")


def hal(seed):                                    # Big Hal Grogan, the Grog Barrel's landlord (#7): huge, apron, a cloth
    m, a = person("folk_hal", seed, skin=ROSE, hair="#c84a1a", eye="#4a3a2a", hair_style="bald",
                  top=C("#3a5a3a", "#345034"), sleeve=C("#e8e0cc"), legs=C("#3a2e24"), belly=True,
                  apron=C("#ece6d6", "#e0dac8"), facekw=dict(beard="#c84a1a", stache="#c84a1a", rosy="#e07060"))
    t = a["top"]
    m.cube("body", [-5, 12, -3.75], [10, 7, 1.5], C("#3a5a3a"))
    m.cube("head", [-4.4, t - 4, -4.6], [8.8, 4, 1], C("#c84a1a", "#b84418"))
    m.cube("head", [4, t + 3, -0.5], [0.6, 1, 1], C("#d8b040"))
    m.cube("body", [-4.6, t - 4, -2.6], [2, 7, 0.5], C("#f0f0ec"))                                         # bar cloth on the shoulder
    k = kit(m, a, "r")
    m.cube(k, [-8, t - 16, -2], [4, 5, 4], C("#8a6a3a", "#7a5a2e"), art={"north": A.at(4, 5, {(x, 0): "f" for x in range(4)}, f="#f8f4e8")})
    return finish(m, a, "laugh")


def morwenna(seed):                               # Morwenna Salt, the net-mender + gossip (#31's neighbour, #20 Park regular)
    m, a = person("folk_morwenna", seed, skin=WEATHER, hair="#5a5a58", eye="#3a5a4a", hair_style="bun",
                  top=C("#4a6a7a", "#42606e"), legs=C("#2a2a2a"), skirt=C("#3a3a4a"), boots="#1a1a1a", apron=C("#c8b890"),
                  facekw=dict(wrinkles=True, lips="#8a4a4a", glasses="#5a4a3a"))
    t = a["top"]
    m.cube("body", [-4.6, t - 6, -2.7], [9.2, 6, 5.4], C("#7a3a2a", "#6e3426"))
    k = kit(m, a, "l")
    m.cube(k, [4, t - 18, -5], [5, 3, 5], over(C("#c8b080"), spots("#5a4a2a", 0.3)))
    k = kit(m, a, "r")
    m.cube(k, [-6.6, t - 15, -4], [0.6, 0.6, 4], C("#d8d0b8"))
    return finish(m, a, "shush")


# ---- the lodgers at the inn + the innkeeper's children (2026-10-04, round 2: "a few more people that just talk")
def harmonia(seed):                               # Harmonia Bellweather, the chapel organist: lace collar, music in hand
    m, a = person("folk_harmonia", seed, skin=FAIR, hair="#b8b4b0", eye="#5a4a7a", hair_style="bun",
                  top=C("#3a2a4a", "#33253f"), legs=C("#2a1e2a"), skirt=C("#2a1e3a", "#251a33"), boots="#141014",
                  facekw=dict(glasses="#c8a040", lips="#9a4a5a", wrinkles=True), collar=over(C("#f4f0e8"), spots("#d8d0c0", 0.3)), buttons="#c8a040")
    t = a["top"]
    m.cube("head", [-0.5, t + 9, 3.5], [1, 1, 1], C("#c8a040"))                                           # a hairpin
    m.cube("body", [-1, t - 6, -2.6], [2, 2.5, 0.4], C("#c8a040"), art={"north": A("kk\nkk", k="#c8a040")})   # a lyre brooch
    k = kit(m, a, "l")
    m.cube(k, [5, t - 16, -4.5], [1.2, 5, 4], C("#ece4cc"), art={"east": A.at(4, 5, {(x, y): "k" for x in range(4) for y in (1, 2, 3)}, k="#3a3a3a")})  # hymn sheets
    return finish(m, a, "fiddle")


def kid(name, seed, **kw):
    m, a = person(name, seed, head_size=9.5, **kw)
    return m, a


def pip(seed):                                    # Pip Goodbarrel (8): wooden sword, paper hat, scabbed knees
    m, a = kid("folk_pip", seed, skin=ROSE, hair="#8a4a2a", eye="#5a3a2a", hair_style="crop", top=C("#c8b890", "#bcac84"),
               sleeve=C("#c8b890"), legs=over(C("#5a4a3a"), spots("#c86a5a", 0.05)), boots="#4a2e1a", belt="#5a3a20",
               facekw=dict(freckles="#b06a3a", rosy="#e88a80"))
    t = a["top"]
    m.cube("head", [-4, t + 9.5, -3], [8, 3, 6], C("#f0ece0"), art={"north": A("....k...\n...kkk..\n..kkkkk.", k="#e8e4d8")})  # a paper hat
    k = kit(m, a, "r")
    m.cube(k, [-6.5, t - 14, -11], [1, 1, 9], C("#a07a4a")); m.cube(k, [-7.5, t - 14, -3], [3, 1, 1], C("#6a4a2a"))         # a wooden sword
    return finish(m, a, "laugh", hold=[-20, 0, 0])


def molly(seed):                                  # Molly Goodbarrel (6): pigtails with ribbons, a rag doll
    m, a = kid("folk_molly", seed, skin=ROSE, hair="#a85a2a", eye="#3a6a3a", hair_style="fringe", top=C("#d86a7a", "#c85e6e"),
               legs=C("#f0ece0"), skirt=C("#c84a5a", "#b8404e"), boots="#5a2a1a", apron=C("#f4f0e8"), facekw=dict(freckles="#c07a4a", rosy="#f08a80"))
    t = a["top"]
    H = C("#a85a2a")
    m.cube("head", [-6.5, t + 4, -1], [1.6, 4, 1.6], H); m.cube("head", [4.9, t + 4, -1], [1.6, 4, 1.6], H)                   # pigtails
    m.cube("head", [-6.6, t + 7, -1.1], [1.8, 1, 1.8], C("#f0d040")); m.cube("head", [4.8, t + 7, -1.1], [1.8, 1, 1.8], C("#f0d040"))
    k = kit(m, a, "l")
    m.cube(k, [5, t - 17, -3.5], [3, 4, 2], C("#d8b090"), art={"north": A.at(3, 4, {(0, 0): "e", (2, 0): "e", (1, 2): "m"}, e="#202020", m="#c04040")})  # the doll
    m.cube(k, [5, t - 13, -3.5], [3, 1, 2], C("#e0c040"))
    return finish(m, a, "look_around")


def tobias(seed):                                 # Tobias Penn, the town crier: red coat, bicorne, a big handbell
    COAT = C("#a01a1a", "#8e1616")
    m, a = person("folk_tobias", seed, skin=ROSE, hair="#5a3a22", eye="#3a3a5a", hair_style="pony", top=C("#e8d8a8"), sleeve=COAT,
                  legs=C("#1e1e2a"), coat=COAT, cuffs="#e0c050", buttons="#e0c050", boots="#141414", belly=True,
                  facekw=dict(stache="#5a3a22", rosy="#e07060"))
    t = a["top"]
    bicorne(m, t, col="#1a1a1a", trim="#e0c050", plume="#f0f0ec")
    m.cube("body", [-4.3, 18, -2.6], [8.6, 1, 0.5], C("#e0c050"))
    k = kit(m, a, "r")
    m.cube(k, [-7.5, t - 18, -2.5], [3, 3, 3], C("#d8b040", "#c8a030"), art={"sides": A("...\n...\nkkk", k="#a07a20")})       # the bell
    m.cube(k, [-6.5, t - 15, -1.5], [1, 3, 1], C("#3a2414"))
    k = kit(m, a, "l")
    m.cube(k, [5, t - 17, -2], [1.5, 7, 1.5], C("#ece4cc")); m.cube(k, [5, t - 18, -2.5], [1.5, 1, 2.5], C("#c83030"))           # a scroll + seal
    return finish(m, a, "tip_hat")


def ginny(seed):                                  # Ginny Tallow, the lamplighter: a long pole with a flame, a soot-smudged face
    m, a = person("folk_ginny", seed, skin=TAN, hair="#2a1a14", eye="#7a5a2a", hair_style="pony_long", top=C("#3a4a3a", "#344234"),
                  legs=C("#2a2a2a"), boots="#1a1410", belt="#3a2414", coat=C("#4a3a2a"), facekw=dict(freckles="#3a2a20", lips="#9a4a40"))
    t = a["top"]
    cap(m, t, "#2a2a2a")
    k = kit(m, a, "r")
    m.cube(k, [-6.5, t - 20, -1], [1, 30, 1], C("#6a4a2a"))
    m.cube(k, [-7, t + 10, -1.5], [2, 2, 2], C("#2a2a2a"), art={"sides": A("..\n.g", g="!f0b040")})
    m.cube(k, [-6.75, t + 12, -1.25], [1.5, 1.5, 1.5], lit("#ffc040"))
    return finish(m, a, "look_around", hold=[-15, 0, 0])


def brask(seed):                                  # Sergeant Brask, of the fort garrison (off duty): red tunic unbuttoned, a scar
    TUNIC = C("#a01818", "#8e1414")
    m, a = person("folk_brask", seed, skin=BROWN, hair="#1a1210", eye="#3a2a1a", hair_style="crop", top=bands(TUNIC, (0, 1, C("#f0f0ec"))),
                  sleeve=TUNIC, legs=C("#ece8dc"), boots="#141414", cuffs="#f0f0ec", belt="#f0f0ec",
                  facekw=dict(stache="#1a1210", scar="#5a3424", brows="#0e0a08"))
    t = a["top"]
    m.cube("head", [-4.4, t + 6.5, -4.4], [8.8, 3.5, 8.8], C("#1a1a1a"), art={"north": A.at(9, 4, {(4, 1): "g", (4, 2): "g"}, g="#e0c050")})  # shako
    m.cube("head", [-4.4, t + 6.5, -6], [8.8, 0.6, 2], C("#101010"))
    m.cube("body", [-4.6, 8, -1.5], [0.8, 3, 3], C("#3a2a1a"))                                                      # cartridge box
    k = kit(m, a, "l")
    m.cube(k, [5, t - 16, -3], [3, 4, 3], C("#8a6a3a"), art={"up": A("fff\nfff\nfff", f="#f4ecd4")})                     # his pint
    return finish(m, a, "cross_arms")


def agnes(seed):                                  # Agnes Crumb, feeder of gulls: patched shawl, a bag of crumbs, a gull on her hat
    m, a = person("folk_agnes", seed, skin=FAIR, hair="#e0dcd4", eye="#6a8a9a", hair_style="bun", top=C("#7a6a5a", "#6e5e50"),
                  legs=C("#3a3a3a"), skirt=over(C("#5a5a6a"), spots("#8a7a6a", 0.06, size=2)), boots="#2a2420",
                  facekw=dict(wrinkles=True, rosy="#e8a090", lips="#a06060"))
    t = a["top"]
    m.cube("body", [-4.6, t - 6, -2.7], [9.2, 6, 5.4], over(C("#8a5a3a"), spots("#c8a060", 0.1, size=2)))
    brim_hat(m, t, "#5a4a3a", band="#3a2a1a", wide=5.5, crown=3.25, h=3)
    m.bone("gull", [0, t + 11, 0], "head")
    m.cube("gull", [-1, t + 11, -2.5], [2, 2, 5], C("#f4f4f0"), art={"north": A.at(2, 2, {(0, 0): "e", (1, 1): "b"}, e="#101010", b="#e8b030")})
    m.cube("gull", [-3, t + 12.5, -1], [6, 0.5, 2.5], C("#c8ccd0"))
    k = kit(m, a, "l")
    m.cube(k, [4.5, t - 18, -3], [4, 4, 3], C("#c8b080", "#b8a070"), art={"up": A("....\n.cc.\n....", c="#e0c890")})
    return finish(m, a, "sweep")


def seraphine(seed):                              # Seraphine Vale, a traveller: a hooded grey cloak, a silver locket, a sealed letter
    CLOAK = C("#4a4e56", "#42464e", "#52565e")
    m, a = person("folk_seraphine", seed, skin=OLIVE, hair="#0e0c10", eye="#3ab0a0", hair_style="long", top=C("#1e2a3a"),
                  legs=C("#1a1e26"), boots="#121216", coat=CLOAK, facekw=dict(lips="#7a3a4a", brows="#0a0808"))
    t = a["top"]
    m.cube("head", [-4.75, t - 1, -4.75], [9.5, 9.8, 9.5], CLOAK, art={"north": A("\n".join(["kkkkkkkkkk"] + ["k________k"] * 7 + ["kkkkkkkkkk"] * 2), k="#4a4e56")})
    m.cube("body", [-4.6, 2, 2.3], [9.2, 21, 0.8], CLOAK)
    m.cube("body", [-0.5, t - 7, -2.6], [1, 1.5, 0.4], lit("#9ae8e0"))                                              # the locket
    k = kit(m, a, "r")
    m.cube(k, [-7, t - 15.5, -4], [1, 3, 4], C("#ece4cc"), art={"west": A("....\n.rr.\n....", r="#8a1a1a")})
    return finish(m, a, "look_around")


# ---- round 3 (2026-10-04, from the user's idea list): the surgeon, the smuggler, the fort guards, two more children,
# the travelling merchant of market day
def elias(seed):                                  # Dr Elias Marrow, the ship's surgeon: black coat, bloody apron, a saw
    m, a = person("folk_elias", seed, skin=FAIR, hair="#2a2a2a", eye="#3a4a5a", hair_style="parted", top=C("#e8e4dc"),
                  legs=C("#1e1e22"), coat=C("#1a1a1e", "#202024"), boots="#101010", apron=over(C("#ece6d6"), spots("#8a1a1a", 0.06)),
                  facekw=dict(glasses="#a8a8a8", stache="#2a2a2a", brows="#1a1a1a"))
    t = a["top"]
    top_hat(m, t, col="#141214", band="#3a3a3a")
    k = kit(m, a, "r")
    m.cube(k, [-6.5, t - 15, -8], [0.6, 2.5, 6], C("#c8ccd4", "#e0e4ec")); m.cube(k, [-7, t - 15.5, -3], [1.5, 3, 2], C("#5a3a20"))   # a bone saw
    k = kit(m, a, "l")
    m.cube(k, [4.5, t - 18, -3], [4, 4, 3], C("#3a2418", "#2e1c12"), art={"north": A.at(4, 4, {(1, 1): "c", (2, 1): "c", (1, 2): "c", (2, 2): "c"}, c="#c83030")})   # the bag
    return finish(m, a, "pocketwatch")


def lazlo(seed):                                  # Lazlo Quick, a smuggler: dark coat, scarf over the face, a lantern hooded low
    m, a = person("folk_lazlo", seed, skin=OLIVE, hair="#1a1210", eye="#c8a040", hair_style="fringe", top=C("#2a2a30"),
                  legs=C("#1a1a1e"), coat=C("#1e1e24", "#24242a"), boots="#0e0e10", belt="#3a2414",
                  facekw=dict(brows="#0e0a08", scar="#5a3a2a"))
    t = a["top"]
    m.cube("head", [-4.4, t, -4.6], [8.8, 3.4, 1], C("#3a1a1a", "#2e1414"))                                    # the scarf over his mouth
    brim_hat(m, t, "#1a1a1a", band="#3a1a1a", wide=6, crown=3.5, h=3)
    k = kit(m, a, "l")
    m.cube(k, [4.5, t - 18, -3], [3, 4, 3], C("#2a2a2a"), art={"sides": A("...\n.g.\n...\n...", g="!c88030")})
    k = kit(m, a, "r")
    m.cube(k, [-8, t - 17, -3], [4, 4, 4], C("#6a4a2a"), art={"north": A("....\n.xx.\n.xx.\n....", x="#2a1a10")})       # a crate of contraband
    return finish(m, a, "look_around")


def soldier(name, seed, skin, hair, eye, rank, extra=None, coat=("#a01818", "#8e1414"), **fk):
    """The fort garrison: red coats, white cross-belts, a shako; their rank in the trim. The Governor's Guard wear
    Armada blue (coat=GUARD_BLUE)."""
    TUNIC = C(*coat)
    m, a = person(name, seed, skin=skin, hair=hair, eye=eye, hair_style="crop", top=bands(TUNIC, (0, 1, C("#f0f0ec"))),
                  sleeve=TUNIC, legs=C("#ece8dc"), boots="#141414", cuffs="#f0f0ec", belt="#f0f0ec", facekw=fk)
    t = a["top"]
    for r in (34, -34):                                                                                       # white cross-belts
        m.cube("body", [-0.75, 11.5, -2.45], [1.5, 13, 0.45], C("#f0f0ec"), rot=[0, 0, r], pivot=[0, 18, -2.2])
    m.cube("head", [-4.4, t + 6.5, -4.4], [8.8, 4, 8.8], C("#1a1a1a"), art={"north": A.at(9, 4, {(4, 1): "g", (4, 2): "g"}, g=rank)})
    m.cube("head", [-4.4, t + 6.5, -6], [8.8, 0.6, 2], C("#101010"))
    m.cube("head", [-0.5, t + 10.5, -4.6], [1, 2.5, 1], C("#f0f0ec"))                                         # the plume
    k = kit(m, a, "r")
    m.cube(k, [-6.5, t - 24, -1], [1, 26, 1], C("#5a3a20")); m.cube(k, [-6.75, t + 2, -1.25], [1.5, 5, 1.5], C("#c8ccd4"))   # musket + bayonet
    k = kit(m, a, "l")
    m.cube(k, [4.5, t - 18, -2.5], [3, 4, 3], C("#2a2a2a"), art={"sides": A("...\n.g.\n.g.\n...", g="!f0c060")})      # the watch lantern
    if extra:
        extra(m, a)
    return m, a


def ashby(seed):                                  # Lieutenant Ashby: gold rank, a sword, sash
    def ex(m, a):
        m.cube("body", [4.2, 3, -1], [0.8, 10, 1.5], C("#c8ccd4"), rot=[15, 0, 0])
        m.cube("rarm", [-8.5, a["top"] - 2.5, -2.5], [5, 1.5, 5], C("#e0c050"))
    m, a = soldier("folk_ashby", seed, FAIR, "#c8a060", "#3a5a8a", "#e0c050", ex, stache="#c8a060")
    return finish(m, a, "pocketwatch")


def hale(seed):                                   # Corporal Hale: big, grey whiskers
    m, a = soldier("folk_hale", seed, WEATHER, "#8a8a88", "#4a4a3a", "#f0f0ec", None, beard="#8a8a88", wrinkles=True)
    return finish(m, a, "cross_arms", hold=[-10, 0, 0])


def dobbs(seed):                                  # Private Dobbs: young, freckles, ears
    def ex(m, a):
        m.cube("head", [-5, a["top"] + 3, -0.5], [1, 2, 1], noise(ROSE)); m.cube("head", [4, a["top"] + 3, -0.5], [1, 2, 1], noise(ROSE))
    m, a = soldier("folk_dobbs", seed, ROSE, "#c86a2a", "#3a6a3a", "#f0f0ec", ex, freckles="#b06a3a", rosy="#e88a80")
    return finish(m, a, "yawn", hold=[-10, 0, 0])


def finch(seed):                                  # Private Finch: dark skin, a drummer's drum
    def ex(m, a):
        m.cube("body", [-4.6, 5, -5], [5, 4, 4], C("#e8e4dc"), art={"sides": A("kkkkk\n.....\n.....\nkkkkk", k="#a01818")})
    m, a = soldier("folk_finch", seed, DARK, "#140e0c", "#3a2a1a", "#f0f0ec", ex, lips="#5a2a2a")
    return finish(m, a, "look_around", hold=[-10, 0, 0])


GUARD_BLUE = ("#1e3a78", "#182f62")


def pell(seed):                                   # Private Pell, Governor's Guard: lanky, a long nose, sandy hair
    def ex(m, a):
        m.cube("head", [-0.5, a["top"] + 2.5, -5.2], [1, 2, 1], noise(FAIR))
    m, a = soldier("folk_pell", seed, FAIR, "#c8a868", "#4a6a8a", "#f0f0ec", ex, coat=GUARD_BLUE)
    return finish(m, a, "look_around", hold=[-10, 0, 0])


def quayle(seed):                                 # Private Quayle, Governor's Guard: round-faced, rosy, black hair
    m, a = soldier("folk_quayle", seed, OLIVE, "#1a1412", "#3a2a1a", "#f0f0ec", None, coat=GUARD_BLUE, rosy="#d0806a")
    return finish(m, a, "yawn", hold=[-10, 0, 0])


def crane(seed):                                  # Sergeant Crane, Governor's Guard: grey, mutton-chops, the keys
    def ex(m, a):
        m.cube("body", [3.6, 9, -2.6], [1.5, 2, 0.6], C("#c8a040"), art={"sides": A("g.\n.g", g="#f0d070")})   # the strongroom keys
    m, a = soldier("folk_crane", seed, WEATHER, "#9a9a96", "#3a4a5a", "#e0c050", ex, coat=GUARD_BLUE, stache="#9a9a96", wrinkles=True)
    return finish(m, a, "cross_arms", hold=[-10, 0, 0])


def ruddock(seed):                                # Corporal Ruddock, Fort Garrison: stocky, red beard, a scar
    m, a = soldier("folk_ruddock", seed, TAN, "#a04a20", "#4a3a2a", "#f0f0ec", None, beard="#a04a20", scar="#7a4a3a")
    return finish(m, a, "pocketwatch", hold=[-10, 0, 0])


def tom(seed):                                    # Tom, a foundling at the inn: patched clothes, a kite
    m, a = kid("folk_tom", seed, skin=BROWN, hair="#1a1210", eye="#3a2a1a", hair_style="curly", top=over(C("#4a6a8a"), spots("#8a6a4a", 0.06, size=2)),
               legs=C("#5a4a3a"), boots="#3a2414", facekw=dict(rosy="#a85a4a"))
    t = a["top"]
    k = kit(m, a, "r")
    m.cube(k, [-9, t - 4, -1], [5, 6, 0.5], C("#e8c040"), art={"north": A("..r..\n.rrr.\nrrrrr\n.rrr.\n..r..\n..r..", r="#c83030")}, rot=[0, 0, 20])
    m.cube(k, [-6.5, t - 15, -0.5], [0.4, 12, 0.4], C("#f0f0ec"))
    return finish(m, a, "laugh")


def bella(seed):                                  # Bella, a foundling at the inn: straw hat, a jar for crabs
    m, a = kid("folk_bella", seed, skin=FAIR, hair="#e0c060", eye="#3a6aa8", hair_style="fringe", top=C("#7aa85a", "#6e9a50"),
               legs=C("#f0ece0"), skirt=C("#5a8a3a"), boots="#4a2e1a", facekw=dict(freckles="#c0905a", rosy="#f0a090"))
    t = a["top"]
    brim_hat(m, t + 1.5, "#e0c878", band="#5a8a3a", wide=6.5, crown=4, h=2)
    k = kit(m, a, "l")
    m.cube(k, [4.5, t - 17, -3], [3, 4, 3], lit("#c8e8f0"), art={"sides": A("...\n.r.\n...\n...", r="#c84030")})
    return finish(m, a, "look_around")


def marco(seed):                                  # Marco Venn, the travelling merchant (market day): striped coat, huge pack
    COAT = bands(C("#6a2a7a"), *[(y, y + 1, C("#e0c050")) for y in range(0, 12, 3)])
    m, a = person("folk_marco", seed, skin=OLIVE, hair="#2a1a10", eye="#4a3a1a", hair_style="wild", top=COAT, sleeve=C("#6a2a7a"),
                  legs=C("#3a2a1a"), boots="#2a1a10", belt="#c8a040", facekw=dict(stache="#2a1a10", goatee="#2a1a10", rosy="#c87060"))
    t = a["top"]
    m.cube("head", [-4.75, t + 6, -4.75], [9.5, 3, 9.5], C("#c83a3a"), rot=[0, 0, 6])                        # a red cap, at an angle
    m.cube("body", [-4, 8, 2.2], [8, 18, 6], C("#8a6a40", "#7a5e38"), art={"south": A("kkkk\n....\n.bb.\n.bb.\n....\nkkkk", k="#5a3a20", b="#c8a040")})   # the pack
    m.cube("body", [-3, 26, 3], [6, 3, 4], C("#d8ccaa"))
    m.cube("body", [3.5, 18, 2.5], [2, 6, 2], C("#c8a040"))                                                     # a hanging pan
    k = kit(m, a, "r")
    m.cube(k, [-8, t - 16, -3], [3, 3, 3], C("#e0c050", "#c8a040"))
    return finish(m, a, "count_coins")


CHARACTERS = {"polly": polly, "snip": snip, "inka": inka, "agatha": agatha, "isadora": isadora, "gideon": gideon,
              "aldous": aldous, "quill": quill, "martha": martha, "silas": silas, "anselm": anselm, "brannoc": brannoc,
              "hob": hob, "elspeth": elspeth, "cobb": cobb, "wilma": wilma, "zora": zora, "ned": ned, "rufus": rufus,
              "finn": finn, "nell": nell, "jack": jack, "pettigrew": pettigrew, "rosalind": rosalind, "dan": dan,
              "ptolemy": ptolemy, "hal": hal, "morwenna": morwenna, "harmonia": harmonia, "pip": pip, "molly": molly,
              "tobias": tobias, "ginny": ginny, "brask": brask, "agnes": agnes, "seraphine": seraphine,
              "elias": elias, "lazlo": lazlo, "ashby": ashby, "hale": hale, "dobbs": dobbs, "finch": finch, "pell": pell, "quayle": quayle, "crane": crane, "ruddock": ruddock, "tom": tom,
              "bella": bella, "marco": marco}

MOBS = {"folk_" + k: v for k, v in CHARACTERS.items()}
SKINS = {}
