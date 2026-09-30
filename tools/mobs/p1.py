"""Phase 1 roster: raft_pirate, captain_rackham, bloodfin."""
from mobkit import A, Rig, bands, counter, lit, noise, over, solid, spots, veins, drips, anim, keys, wave, S
from mobs.common import biped, biped_anims

# ---------------------------------------------------------------- shared palettes
SKIN = ["#b27a52", "#c08660", "#c98f65", "#d29a70"]
NAVY = ["#16283a", "#1c3246", "#223a50", "#2a4458"]
BROWN = ["#3a2618", "#452e1d", "#503522", "#5c3e27"]
BOOT = ["#1a1412", "#221a16", "#2a211b"]
WOOD = ["#5a3c22", "#6a4628", "#78512e", "#855b34"]
STEEL = ["#8a9098", "#9aa0a8", "#aab0b8", "#bcc2ca"]


def _sword(m, bone, x, y, blade=STEEL, length=11, guard="#6a4a2a", tilt=-40):
    """Cutlass in a hand: grip + crossguard + blade pointing forward and up by `tilt`."""
    pv = [x, y, 0]
    m.cube(bone, [x - 0.5, y - 1, -1], [1, 3, 2], solid("#3a2616"), rot=[tilt, 0, 0], pivot=pv)
    m.cube(bone, [x - 1.5, y - 1.5, -3], [3, 3, 1], solid(guard), rot=[tilt, 0, 0], pivot=pv)
    m.cube(bone, [x - 0.5, y - 1, -3 - length], [1, 2, length], over(noise(blade), spots("#dfe4ea", 0.08)),
           rot=[tilt, 0, 0], pivot=pv)


# =====================================================================================
# RAFT PIRATE - marooned brawler paddling a log raft
# =====================================================================================
def raft_pirate(seed):
    m = Rig("raft_pirate", 64, 64, seed)
    P = dict(s="#c98f65", S="#a8704c", k="#2b2420", w="#e8e4dc", p="#2a1c14", n="#b57a55", h="#77736a", H="#58544c",
             m="#3a1c16", t="#b0584c", b="#a8322c", B="#7e2420", e="#b27a52")
    head = A("""
BbbbbbbB
bbbBbbbb
skkssskk
swpsspws
sSsnnsSs
hhmmmmhh
hHmttmHh
hhhhhhhh""", P)
    head_side = A("""
BbbbbbbB
bbbbbbbb
ssssssss
ssssesss
sssseess
Hsssssss
hHssssss
hhhsssss""", P)
    head_side_l = A("""
BbbbbbbB
bbbbbbbb
ssssssss
ssseSsss
sssesess
sssssssH
ssssssHh
ssssshhh""", P)
    head_back = A("""
BbbbbbbB
bbbbbbbb
BbbbbbbB
rRrrRrrR
rrRrrRrr
RrrRrrRr
srRrrRrs
ssssssss""", dict(P, r="#3a2a1e", R="#2a1e16"))
    shirt = A("""
cccHHccc
ccchhccc
cccchccc
cccccccc
cccccccc
cccccccc
cCcccccc
cccccccc
LLLGGLLL
LLLGgLLL
cccccccc
cccccccc""", dict(c="#223a50", C="#16283a", h="#77736a", H="#58544c", L="#3a2618", G="#d4a53a", g="#8a6a22"))
    body = bands(noise(NAVY), (8, 10, noise(BROWN)))
    arm = bands(noise(SKIN), (0, 4, noise(NAVY)))
    leg = bands(noise(BROWN), (9, 12, noise(BOOT)))
    a = biped(m, head=noise(SKIN), body=body, arm=arm, leg=leg, y=3,
              art={"head": {"north": head, "east": head_side, "west": head_side_l, "south": head_back,
                            "up": A("BbbbbbbB\nbbbbbbbb\nbbBbbbbb\nbbbbbbbb\nbbbbbBbb\nbbbbbbbb\nbbbbbbbb\nBbbbbbbB", P)},
                   "body": {"north": shirt}})
    t = a["top"]
    # beard hanging over the chest + bandana knot and tails at the back
    m.cube("head", [-3, t - 3, -4.6], [6, 3, 1], noise(["#58544c", "#6a665e", "#77736a"]))
    m.cube("head", [-2, t - 5, -4.4], [4, 2, 1], noise(["#58544c", "#6a665e"]))
    m.cube("head", [-4, t + 5, 4], [2, 2, 1], noise(["#7e2420", "#a8322c"]))
    m.cube("head", [-5, t + 1, 4.2], [1, 4, 1], noise(["#7e2420", "#a8322c"]), rot=[0, 0, -20])
    m.cube("head", [-3, t + 1, 4.3], [1, 3, 1], noise(["#7e2420", "#a8322c"]), rot=[0, 0, 15])
    # rolled sleeve cuffs
    m.pair("rarm", "larm", [-8.2, t - 5, -2.2], [4, 1, 4], noise(NAVY), inflate=0.2)
    _sword(m, "rhand", a["rx"], a["hand_y"], tilt=-55)
    # the raft: five lashed logs + two cross beams + a loot barrel
    m.bone("raft", [0, 0, 0], "root")
    for i, x in enumerate(range(-10, 10, 4)):
        m.cube("raft", [x, -(i % 2), -10 - (i % 2)], [4, 3, 20 + (i % 2) * 2], over(noise(WOOD), veins("#3e2a16", 0.05, glow=False)),
               art={"north": A("rRRr\nRrrR\nrRRr", dict(r="#8a6038", R="#6a4628")),
                    "south": A("rRRr\nRrrR\nrRRr", dict(r="#8a6038", R="#6a4628"))})
    for z in (-7, 6):
        m.cube("raft", [-11, 2.5, z], [22, 1, 2], noise(["#3e2a18", "#4a3220"]))
    m.cube("raft", [5, 3, 4], [4, 5, 4], bands(noise(WOOD), (0, 1, noise(["#4a4a4a", "#5a5a5a"])),
                                                 (4, 5, noise(["#4a4a4a", "#5a5a5a"]))))
    biped_anims(m, "sword", hold=[-20, 0, 0])
    # bob on the waves
    m.anims["idle"]["bones"]["raft"] = {"rotation": wave(3.0, lambda q: [S(q) * 2, 0, S(q * 0.5) * 2])}
    return m


# =====================================================================================
# CAPTAIN RACKHAM - boss; crimson greatcoat, tricorn, cutlass + flintlock
# =====================================================================================
CRIMSON = ["#4e0e12", "#5e1216", "#6e181c", "#7c1e22"]
GOLD = ["#a87a1e", "#c8962a", "#e0b84a"]


def captain_rackham(seed):
    m = Rig("captain_rackham", 64, 64, seed)
    P = dict(s="#c89a70", S="#a67a52", k="#141010", K="#2a2220", w="#ece6dc", p="#1a1210", e="#101010",
             E="#2a2a2a", n="#b8845c", h="#1a1412", H="#2e2420", m="#4a1a16", g="#e0b84a", t="#d8d2c4")
    head = A("""
KkkkkkkK
kSssssSk
sEEEssKK
eeeEswps
sssnnsss
hhmmmmhh
hHhttHhh
hhhhhhhh""", P)
    side_r = A("""
kkkkkkkk
Kkssssss
KEEEssss
kksssess
kksgsess
Hhssssss
hHhsssss
hhhhssss""", P)
    side_l = A("""
kkkkkkkk
ssssssKk
sssssssK
ssesskkk
ssesssKk
sssssshH
ssssshHh
sssshhhh""", P)
    back = A("""
kKkkkKkk
kkkkkkkk
kkKkkkKk
kkkkkkkk
kKkkkkkk
kkkkKkkk
KkkkkkkK
ssssssss""", P)
    vest = A("""
cGsssGcc
cGshsGcc
cGWWWGcc
cGWVWGcc
cGWWWGcc
cGWVWGcc
cGWWWGcc
cGWWWGcc
LLLBBLLL
LLLBbLLL
cGcccGcc
cGcccGcc""", dict(c="#6e181c", G="#e0b84a", s="#c89a70", h="#1a1412", W="#d8d2c4", V="#9a948a",
                  L="#2a1a12", B="#e0b84a", b="#8a6a22"))
    coat = bands(noise(CRIMSON), (8, 10, noise(["#1e1410", "#2a1a12"])))
    arm = bands(noise(CRIMSON), (8, 10, noise(GOLD)), (10, 12, noise(["#e6e0d4", "#d8d2c4"])))
    leg = bands(noise(["#1c1618", "#241c1e", "#2c2224"]), (6, 12, noise(["#141010", "#1e1816", "#2a221e"])))
    a = biped(m, head=noise(["#b88a60", "#c89a70", "#d0a478"]), body=coat, arm=arm, leg=leg,
              art={"head": {"north": head, "east": side_r, "west": side_l, "south": back},
                   "body": {"north": vest}})
    t = a["top"]
    # tricorn: brim + crown + raised front/side flaps + gold trim + skull badge
    HAT = noise(["#141012", "#1c1618", "#241e20"])
    m.bone("hat", [0, t + 8, 0], "head")
    m.cube("hat", [-6, t + 8, -6], [12, 1, 12], HAT, art={"sides": A("g", dict(g="#e0b84a"))})
    m.cube("hat", [-4.5, t + 9, -4.5], [9, 3, 9], HAT)
    m.cube("hat", [-5, t + 9, -6.5], [10, 3, 1], HAT, rot=[-18, 0, 0], pivot=[0, t + 9, -6],
           art={"north": A("gggggggggg\n...kkk....\n...kWk....", dict(g="#e0b84a", k="#141012", W="#e6e0d4"))})
    m.pair("hat", "hat", [-6.5, t + 9, -5], [1, 3, 10], HAT, rot=[0, 0, 18], pivot=[-6, t + 9, 0])
    m.cube("hat", [-1, t + 12, -5], [2, 1, 2], solid("#e0b84a"))
    # beard, earring
    m.cube("head", [-3, t - 3, -4.6], [6, 3, 1], noise(["#141010", "#1e1816"]))
    m.cube("head", [-2, t - 5, -4.5], [4, 2, 1], noise(["#141010", "#1e1816"]))
    m.cube("head", [-4.6, t + 2, -1], [1, 1, 1], lit("#e0b84a"))
    # greatcoat tails (own bone - flaps with the stride) and shoulder epaulettes
    m.bone("coat", [0, t - 12, 0], "body")
    m.cube("coat", [-4.5, t - 22, 1.5], [9, 10, 1], noise(CRIMSON),
           art={"south": A("GcccccccG\nGcccccccG\nGcccccccG\nGcccccccG\nGcccccccG\nGcccccccG\nGcccccccG\nGcccccccG\nGcccccccG\nGGGGGGGGG",
                           dict(c="#6e181c", G="#e0b84a"))})
    m.pair("coat", "coat", [-4.6, t - 21, -2.4], [1, 9, 4], noise(CRIMSON))
    m.pair("rarm", "larm", [-8.4, t - 1, -2.4], [5, 2, 5], noise(GOLD))
    _sword(m, "rhand", a["rx"], a["hand_y"], blade=["#9aa0a8", "#b8bec6", "#c8ced6"], length=12, guard="#e0b84a", tilt=-50)
    # flintlock in the left hand
    lx, hy = a["lx"], a["hand_y"]
    m.cube("lhand", [lx - 0.5, hy - 2, -1], [1, 3, 2], solid("#3a2616"), rot=[-50, 0, 0], pivot=[lx, hy, 0])
    m.cube("lhand", [lx - 0.5, hy - 0.5, -8], [1, 1, 7], solid("#4a4e56"), rot=[-50, 0, 0], pivot=[lx, hy, 0])
    biped_anims(m, "sword", cape="coat", hold=[-25, 0, 0],
                extra_move={"coat": {"rotation": wave(1.0, lambda q: [14 + abs(S(q)) * 10, 0, 0])}})
    m.anims["idle"]["bones"]["larm"] = {"rotation": wave(3.0, lambda q: [-30 + S(q) * 3, 0, -4])}
    return m


# =====================================================================================
# BLOODFIN - boss shark; slate back, bone-white belly, scarred, blood-red fin edges
# =====================================================================================
def shark_body(m, *, L, W, H, y, back, belly, fin, fin_edge=None, teeth="#ece6d2", eye="#101010",
               scars=None, gill="#2a3036", dorsal=10, tail=12, eye_glow=False):
    """A shark in the concept's style: box body, tapered snout, hinged jaw, rotated fins."""
    FIN = noise(fin)
    edge = (lambda mat: over(mat, drips(fin_edge, 0.6, faces=("north", "south", "east", "west", "up"), maxlen=1))) if fin_edge else (lambda mat: mat)
    m.bone("root", [0, y, 0])
    m.bone("body", [0, y + H / 2, 0], "root")
    ov = [veins(scars, 0.05, glow=False, faces=("east", "west", "up"), length=(2, 4))] if scars else []
    body = over(counter(back, belly, 0.58), *ov)
    m.cube("body", [-W / 2, y, -L / 2 + 6], [W, H, L - 14], body,
           art={"east": A("." * (L - 14) + "\n" + ".." + "g." * 3, dict(g=gill)),
                "west": A("." * (L - 14) + "\n" + "..." + ".g" * 3, dict(g=gill))})
    # head: slightly narrower, snout overhang
    m.bone("head", [0, y + H / 2, -L / 2 + 6], "body")
    ek = "!" + eye if eye_glow else eye
    hh = int(H - 1)
    m.cube("head", [-W / 2 + 0.5, y + 1, -L / 2 - 4], [W - 1, H - 1, 10], counter(back, belly, 0.5),
           art={"east": A.at(10, hh, {(7, 2): "e", (7, 3): "e", (6, 2): "E"}, e=ek, E=ek),
                "west": A.at(10, hh, {(2, 2): "e", (2, 3): "e", (3, 2): "E"}, e=ek, E=ek)})
    m.cube("head", [-W / 2 + 1.5, y + H - 3, -L / 2 - 7], [W - 3, 3, 3], noise(back))
    # jaw with teeth
    m.bone("jaw", [0, y + 2, -L / 2 + 4], "head")
    m.cube("jaw", [-W / 2 + 1, y - 1, -L / 2 - 5], [W - 2, 3, 9], noise(belly))
    for i, x in enumerate(range(int(-W / 2 + 2), int(W / 2 - 1), 2)):
        m.cube("jaw", [x, y + 2, -L / 2 - 5], [1, 2, 1], solid(teeth, edge=0))
    for x in range(int(-W / 2 + 2), int(W / 2 - 1), 2):
        m.cube("head", [x + 1, y - 0.5, -L / 2 - 4], [1, 2, 1], solid(teeth, edge=0))
    # tail stock + two-lobed caudal fin
    m.bone("tail", [0, y + H / 2, L / 2 - 8], "body")
    m.cube("tail", [-W / 2 + 1.5, y + 2, L / 2 - 8], [W - 3, H - 4, 8], counter(back, belly, 0.6))
    m.bone("tailfin", [0, y + H / 2, L / 2], "tail")
    m.cube("tailfin", [-W / 2 + 2.5, y + 3, L / 2], [W - 5, H - 6, 5], noise(back))
    m.cube("tailfin", [-0.5, y + H / 2, L / 2 + 3], [1, tail, 3], edge(FIN), rot=[-35, 0, 0], pivot=[0, y + H / 2, L / 2 + 4])
    m.cube("tailfin", [-0.5, y + H / 2 - tail * 0.55, L / 2 + 3], [1, int(tail * 0.55), 3], edge(FIN), rot=[35, 0, 0],
           pivot=[0, y + H / 2, L / 2 + 4])
    # dorsal (swept back), small second dorsal, pectorals (down + out), pelvic
    m.cube("body", [-0.5, y + H - 1, -2], [1, dorsal, 5], edge(FIN), rot=[-28, 0, 0], pivot=[0, y + H, 0])
    m.cube("tail", [-0.5, y + H - 3, L / 2 - 6], [1, 4, 3], FIN, rot=[-30, 0, 0], pivot=[0, y + H - 3, L / 2 - 5])
    m.bone("fin_r", [-W / 2, y + 2, -L / 2 + 10], "body")
    m.bone("fin_l", [W / 2, y + 2, -L / 2 + 10], "body")
    m.cube("fin_r", [-W / 2 - 9, y + 1.5, -L / 2 + 8], [9, 1, 5], edge(FIN), rot=[0, 18, -24], pivot=[-W / 2, y + 2, -L / 2 + 10])
    m.mirror("fin_l", [-W / 2 - 9, y + 1.5, -L / 2 + 8], [9, 1, 5], edge(FIN), rot=[0, 18, -24], pivot=[-W / 2, y + 2, -L / 2 + 10])
    m.pair("tail", "tail", [-W / 2 - 3, y + 1, L / 2 - 7], [3, 1, 3], FIN, rot=[0, 20, -30], pivot=[-W / 2, y + 1.5, L / 2 - 6])


def shark_anims(m, speed=1.0, amp=1.0):
    def swim(length, a):
        return {"tail": {"rotation": wave(length, lambda q: [0, S(q) * 16 * a, 0])},
                "tailfin": {"rotation": wave(length, lambda q: [0, S(q - 0.9) * 24 * a, 0])},
                "body": {"rotation": wave(length, lambda q: [0, -S(q) * 4 * a, S(q) * 1.5])},
                "head": {"rotation": wave(length, lambda q: [0, -S(q + 0.6) * 3 * a, 0])},
                "fin_r": {"rotation": wave(length, lambda q: [0, 0, S(q) * 6 * a])},
                "fin_l": {"rotation": wave(length, lambda q: [0, 0, -S(q) * 6 * a])},
                "jaw": {"rotation": wave(length, lambda q: [3 + S(q * 0.5) * 2, 0, 0])}}
    anim(m, "idle", 2.4 * speed, swim(2.4 * speed, 0.5 * amp))
    anim(m, "move", 1.0 * speed, swim(1.0 * speed, 1.0 * amp))
    anim(m, "attack", 0.6, {"jaw": {"rotation": keys((0, [3, 0, 0]), (0.15, [42, 0, 0]), (0.3, [0, 0, 0]), (0.6, [3, 0, 0]))},
                            "head": {"rotation": keys((0, [0, 0, 0]), (0.15, [-14, 0, 0]), (0.3, [8, 0, 0]), (0.6, [0, 0, 0]))},
                            "root": {"position": keys((0, [0, 0, 0]), (0.15, [0, 0, 2]), (0.3, [0, 0, -5]), (0.6, [0, 0, 0]))}}, loop=False)
    anim(m, "special", 1.2, {"body": {"rotation": keys((0, [0, 0, 0]), (0.4, [-30, 0, 0]), (0.8, [15, 0, 180]), (1.2, [0, 0, 360]))},
                             "jaw": {"rotation": keys((0, [3, 0, 0]), (0.4, [40, 0, 0]), (1.2, [3, 0, 0]))}}, loop=False)
    anim(m, "special2", 0.8, {"tail": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 45, 0]), (0.5, [0, -55, 0]), (0.8, [0, 0, 0]))},
                              "tailfin": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, 35, 0]), (0.5, [0, -45, 0]), (0.8, [0, 0, 0]))},
                              "body": {"rotation": keys((0, [0, 0, 0]), (0.3, [0, -15, 0]), (0.5, [0, 15, 0]), (0.8, [0, 0, 0]))}}, loop=False)


def bloodfin(seed):
    m = Rig("bloodfin", 128, 128, seed)
    shark_body(m, L=40, W=11, H=10, y=0, back=["#2e3a42", "#36444e", "#3e4e58", "#46565f"],
               belly=["#c8c4ba", "#d6d2c8", "#e2ded4"], fin=["#2a343c", "#323e46", "#3a4750"],
               fin_edge="#8a1a1a", scars="#b8c0c4", dorsal=12, tail=14, eye="#ff3a2a", eye_glow=True)
    shark_anims(m, speed=1.1, amp=1.1)
    return m


from mobs.rackham import captain_rackham as _rackham  # noqa: E402  (rebuilt 2026-09-29, own module)

from mobs.bloodfin import bloodfin as _bloodfin  # noqa: E402  (rebuilt 2026-09-29, own module)

MOBS = {"raft_pirate": raft_pirate, "captain_rackham": _rackham, "bloodfin": _bloodfin}
# extra skins sharing a mob's geometry: written as textures/entity/<id>_<skin>.png (+ glowmask)
SKINS = {"captain_rackham": {"enraged": lambda seed: _rackham(seed, "enraged")},
         "bloodfin": {"enraged": lambda seed: _bloodfin(seed, "enraged")}}
