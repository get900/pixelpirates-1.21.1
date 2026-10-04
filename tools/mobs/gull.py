"""THE HARBOUR GULLS (2026-10-04, the user: "should we add seagulls to make this real" - for Agnes who feeds them).

    python tools/gen_mob_roster.py seagull

A herring gull: white head + belly, grey back and wings with black tips, yellow beak with the red spot, pink legs.
Java: homestead/town/SeagullEntity (clips: idle, walk, fly, glide, peck). Faces -Z like every model here.
"""
from mobkit import A, Rig, noise, bands, anim, keys, wave, S

C = lambda *c: noise(list(c))
WHITE = C("#f4f4f0", "#ecece8", "#fafaf6")
GREY = C("#a8b0b8", "#9aa2aa", "#b4bcc4")
TIP = C("#1a1a1e", "#26262a")


def seagull(seed):
    m = Rig("seagull", 64, 64, seed)
    m.bone("root", [0, 0, 0])
    m.bone("body", [0, 4, 0], "root")
    m.cube("body", [-2, 3, -3], [4, 3.5, 7], WHITE, art={"up": A("gggg\n" * 7, g="#a8b0b8")})
    m.bone("tail", [0, 5, 4], "body")
    m.cube("tail", [-1.5, 4.5, 4], [3, 1, 3], bands(GREY, (0, 1, TIP)), art={"up": A("ggg\nggg\nkkk", g="#a8b0b8", k="#1a1a1e")})
    m.bone("head", [0, 6.5, -3], "body")
    m.cube("head", [-1.5, 6, -5.5], [3, 3, 3], WHITE,
           art={"west": A("...\n.e.\n...", e="#101010"), "east": A("...\n.e.\n...", e="#101010")})
    m.cube("head", [-0.5, 6.5, -7.5], [1, 1, 2], C("#e8c030"), art={"down": A("rr", r="#c83a2a")})
    for side, x in (("rwing", -2), ("lwing", 2)):
        m.bone(side, [x, 6.25, -1], "body", rotation=[0, 80 if x < 0 else -80, 0])       # folded back along the body at rest
        ox = x - 6 if x < 0 else x
        m.cube(side, [ox, 5.75, -2], [6, 0.5, 4.5], bands(GREY, (0, 1, TIP)),
               art={"up": A(("kgggggg\n" * 5) if x < 0 else ("ggggggk\n" * 5), g="#a8b0b8", k="#1a1a1e")})
    for side, x in (("rleg", -1), ("lleg", 1)):
        m.bone(side, [x, 3, 0], "root")
        m.cube(side, [x - 0.25, 0, -0.25], [0.5, 3, 0.5], C("#e8a0a0"))
        m.cube(side, [x - 0.75, 0, -1.25], [1.5, 0.25, 1.5], C("#e8a0a0"))

    anim(m, "idle", 3.0, {"head": {"rotation": keys((0, [0, 0, 0]), (0.8, [0, 30, 0]), (1.4, [0, 30, 0]), (2.0, [0, -25, 0]), (3.0, [0, 0, 0]))},
                          "tail": {"rotation": wave(3.0, lambda q: [S(q) * 4, 0, 0])},
                          })
    anim(m, "walk", 0.6, {"rleg": {"rotation": wave(0.6, lambda q: [S(q) * 35, 0, 0])}, "lleg": {"rotation": wave(0.6, lambda q: [-S(q) * 35, 0, 0])},
                          "body": {"rotation": wave(0.6, lambda q: [0, 0, S(q) * 6])}, "head": {"rotation": wave(0.6, lambda q: [S(2 * q) * 8, 0, 0])}})
    anim(m, "fly", 0.5, {"rwing": {"rotation": wave(0.5, lambda q: [0, -80, -S(q) * 50])}, "lwing": {"rotation": wave(0.5, lambda q: [0, 80, S(q) * 50])},
                         "body": {"position": wave(0.5, lambda q: [0, S(q) * 0.6, 0])},
                         "rleg": {"rotation": keys((0, [70, 0, 0]))}, "lleg": {"rotation": keys((0, [70, 0, 0]))}})
    anim(m, "glide", 2.0, {"rwing": {"rotation": wave(2.0, lambda q: [0, -80, -6 - S(q) * 4])}, "lwing": {"rotation": wave(2.0, lambda q: [0, 80, 6 + S(q) * 4])},
                           "body": {"rotation": wave(2.0, lambda q: [0, 0, S(q) * 6])},
                           "rleg": {"rotation": keys((0, [70, 0, 0]))}, "lleg": {"rotation": keys((0, [70, 0, 0]))}})
    anim(m, "peck", 0.8, {"body": {"rotation": keys((0, [0, 0, 0]), (0.2, [35, 0, 0]), (0.35, [25, 0, 0]), (0.5, [38, 0, 0]), (0.8, [0, 0, 0]))},
                          "head": {"rotation": keys((0, [0, 0, 0]), (0.2, [25, 0, 0]), (0.8, [0, 0, 0]))}}, loop=False)
    return m


MOBS = {"seagull": seagull}
SKINS = {}
