"""THE MERCHANTS OF WAVEBREAK PORT (rebuilt 2026-10-01: every merchant has his own body, props and animations).

    python tools/gen_mob_roster.py trader_quartermaster trader_fishmonger trader_barkeep trader_curio_dealer \\
                                   trader_gunsmith trader_chandler trader_cook map_merchant

Port traders (homestead/trade/PortTraderEntity, one model per Kind -> geo/trader_<kind>) + the Map Merchant.
Every model is a biped (mobs/common.py) with its own silhouette and clips:
  idle / move (biped_anims), talk (a sales pitch - played when a customer opens the trade screen),
  flourish (each merchant's own idle gesture, played now and then).

  quartermaster - stout, navy greatcoat + brass buttons, tricorn, spectacles, ledger + quill (licks thumb, turns a page)
  fishmonger    - yellow oilskin waders over a striped jersey, knit cap, a big fish over the shoulder, cleaver (slaps the fish)
  barkeep       - bald + bandana, handlebar moustache, big apron, towel on the shoulder, a tankard (polishes it)
  curio_dealer  - tall jewelled turban, long robe with gold trim, beard, rings, a glowing curio box (opens it)
  gunsmith      - leather apron, goggles pushed up, bandolier, musket on his back, soot (sights down a pistol)
  chandler      - old salt: sou'wester, white beard, peg leg, a coil of rope over the shoulder, lantern (coils rope)
  cook          - toque, round belly, stained apron, a big ladle (stirs and tastes)
  map_merchant  - a hooded cartographer: dark cloak, a frame pack stuffed with rolled maps, spyglass (unrolls a chart)
"""
from mobkit import A, Rig, bands, noise, over, solid, lit, spots, speckle, veins, anim, keys, wave, S
from mobs.common import biped, biped_anims

C = lambda *c: noise(list(c))


def face(skin, hair, eye, beard=None, stache=None, brows=None, glasses=None):
    """An 8x8 face: hair fringe, brows, eyes, nose, mouth; optional beard/moustache/spectacles."""
    rows = ["hhhhhhhh",
            "hhhhhhhh",
            "sbbssbbs",
            "sWEssWEs",
            "ssssssss",
            "sssnnsss",
            "ssmmmmss",
            "ssssssss"]
    if glasses:
        rows[3] = "gWEggWEg"
    if stache:
        rows[5] = "sssnnsss"; rows[6] = "tttmmttt"
    if beard:
        rows[6] = "ddmmmmdd" if not stache else "tttmmttt"; rows[7] = "dddddddd"
    pal = dict(h=hair, b=brows or hair, s=skin, W="#f0ece0", E=eye, n="#00000022" if False else skin, m="#6a3a2a",
               d=beard or skin, t=stache or skin, g=glasses or skin)
    return A("\n".join(rows), pal)


def side(skin, hair):
    return A("hhhhhhhh\nhhhhhhhh\nhhhhhhss\nhhhhssss\nhhhsssss\nhhssssss\nhsssssss\nssssssss", dict(h=hair, s=skin))


def talk_clip(m, extra=None):
    b = {"rarm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-60, -20, 10]), (1.0, [-55, 10, 15]), (1.6, [-60, -20, 10]), (2.0, [0, 0, 0]))},
         "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [8, 0, 0]), (0.7, [-4, 0, 0]), (1.0, [8, 0, 0]), (1.3, [0, 10, 0]), (2.0, [0, 0, 0]))},
         "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [4, 0, 0]), (1.5, [4, 0, 0]), (2.0, [0, 0, 0]))}}
    if extra:
        b.update(extra)
    anim(m, "talk", 2.0, b, loop=False)


# =====================================================================================
def quartermaster(seed):
    m = Rig("trader_quartermaster", 128, 128, seed)
    skin, hair = ["#c89a7a", "#b88a6a", "#d4a888"], "#6a6a6a"
    COAT = C("#1e3450", "#223a58", "#1a2e46")
    a = biped(m, head=noise(skin), body=bands(COAT, (9, 11, C("#2a1e14", "#3a2a1a"))),
              arm=bands(COAT, (0, 2, C("#c8a040", "#d8b050"))), leg=bands(C("#2a1e14"), (0, 7, C("#e8e0cc", "#d8d0bc"))),
              art={"head": {"north": face(skin[0], hair, "#2a3a6a", stache="#8a8a8a", glasses="#c8a040"), "east": side(skin[0], hair), "west": side(skin[0], hair)},
                   "body": {"north": A.at(8, 12, {**{(3, y): "B" for y in (2, 5, 8)}, **{(4, y): "B" for y in (2, 5, 8)}, **{(x, 0): "w" for x in range(2, 6)}},
                                          B="#e0b040", w="#e8e0cc")}})
    t = a["top"]
    m.cube("body", [-4.5, 12.25, -2.5], [9, 4.75, 5], COAT)                                     # a stout belly
    m.cube("body", [-4.5, 5, -2.6], [9, 7, 1], COAT)                                      # greatcoat skirts
    m.cube("body", [-4.5, 5, 1.6], [9, 7, 1], COAT)
    # tricorn
    m.cube("head", [-5, t + 8, -5], [10, 1, 10], C("#141214", "#1e1a1c"), art={"north": A.at(10, 1, {(x, 0): "g" for x in range(10)}, g="#c8a040")})
    m.cube("head", [-3.5, t + 9, -3.5], [7, 3, 7], C("#141214", "#1e1a1c"))
    m.cube("head", [-5.5, t + 9, -1], [1, 2, 2], C("#141214")); m.cube("head", [4.5, t + 9, -1], [1, 2, 2], C("#141214"))
    # ledger (left hand) + quill (right)
    m.cube("lhand", [4.5, t - 15, -3.5], [2, 6, 7], C("#6a3a20", "#5a3018"), art={"east": A.at(7, 6, {(x, 0): "p" for x in range(7)}, p="#e8e0c8")})
    m.cube("rhand", [-6.5, t - 13, -2.5], [1, 5, 1], C("#f0ece0"), rot=[20, 0, 0])
    m.cube("body", [4, 10, -1.5], [1, 3, 3], C("#8a8a8a", "#a8a8a8"))                        # key ring
    biped_anims(m, "claw")
    talk_clip(m)
    anim(m, "flourish", 3.0, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-70, 0, -20]), (0.8, [-85, 0, -10]), (1.3, [-60, 30, 20]), (2.0, [-60, 30, 20]), (3.0, [0, 0, 0]))},
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-45, -20, 0]), (2.4, [-45, -20, 0]), (3.0, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.8, [20, 0, 0]), (2.4, [20, 0, 0]), (3.0, [0, 0, 0]))}}, loop=False)
    return m


def fishmonger(seed):
    m = Rig("trader_fishmonger", 128, 128, seed)
    skin, hair = ["#d8a888", "#c8987a", "#e0b494"], "#8a5a3a"
    JERSEY = bands(C("#e8e8e0"), *[(y, y + 1, C("#2a4a7a")) for y in range(0, 12, 2)])
    WADERS = C("#d8b020", "#c8a018", "#e8c030")
    a = biped(m, head=noise(skin), body=bands(JERSEY, (0, 7, WADERS)), arm=bands(JERSEY, (0, 3, noise(skin))), leg=WADERS,
              art={"head": {"north": face(skin[0], hair, "#3a6a4a", beard="#8a5a3a"), "east": side(skin[0], hair), "west": side(skin[0], hair)}})
    t = a["top"]
    m.cube("body", [-2.5, 16.75, -2.4], [1, 7, 1], WADERS); m.cube("body", [1.5, 16.75, -2.4], [1, 7, 1], WADERS)      # braces
    m.cube("head", [-4.5, t + 6, -4.5], [9, 3, 9], C("#8a2a22", "#9a3228"))                                      # knit cap
    m.cube("head", [-1, t + 9, -1], [2, 1, 2], C("#e8e0cc"))                                                     # bobble
    # a big fish over the right shoulder
    m.bone("fish", [-4, t, 0], "body")
    FISH = over(C("#7a9aa8", "#8aaab8", "#6a8a98"), spots("#c8e0e8", 0.08))
    m.cube("fish", [-7, t - 1, -1.5], [3, 3, 9], FISH)
    m.cube("fish", [-6.5, t - 0.5, 7.5], [2, 2, 3], C("#5a7a88"))                                                # tail
    m.cube("fish", [-7, t - 1, -2.5], [3, 3, 1], C("#9ab8c4"), art={"north": A.at(3, 3, {(1, 1): "e"}, e="#101010")})
    # cleaver in the left hand
    m.cube("lhand", [5, t - 16, -3], [1, 4, 5], C("#b0b4bc", "#c8ccd4")); m.cube("lhand", [5.2, t - 13, -1], [0.6, 3, 1], C("#5a3a20"))
    biped_anims(m, "claw")
    talk_clip(m)
    anim(m, "flourish", 2.0, {
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-110, 0, 30]), (0.6, [-60, 0, 20]), (0.8, [-110, 0, 30]), (1.0, [-60, 0, 20]), (2.0, [0, 0, 0]))},
        "fish": {"rotation": keys((0, [0, 0, 0]), (0.6, [0, 0, -6]), (1.0, [0, 0, 6]), (2.0, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, -20, 0]), (1.5, [0, -20, 0]), (2.0, [0, 0, 0]))}}, loop=False)
    return m


def barkeep(seed):
    m = Rig("trader_barkeep", 128, 128, seed)
    skin = ["#b07a5a", "#a06a4a", "#bc8866"]
    VEST = C("#8a2020", "#7a1a1a", "#9a2828")
    a = biped(m, head=noise(skin), body=bands(C("#e0d8c4", "#d0c8b4"), (4, 12, VEST)), arm=bands(C("#e0d8c4", "#d0c8b4"), (0, 3, noise(skin))),
              leg=C("#2a2420", "#342c26"),
              art={"head": {"north": face(skin[0], skin[0], "#4a2a1a", stache="#2a1a10", brows="#2a1a10"), "east": side(skin[0], skin[0]), "west": side(skin[0], skin[0])}})
    t = a["top"]
    m.cube("body", [-4.5, 12.25, -3.5], [9, 5.5, 6], C("#e0d8c4", "#d0c8b4"))                      # belly
    m.cube("body", [-4, 4, -3.6], [8, 13.5, 1], C("#ece6d6", "#f4eee0"))                         # apron
    m.cube("head", [-4.5, t + 5.25, -4.5], [9, 3, 9], C("#2a6a3a", "#22583a"))                   # bandana
    m.cube("head", [-1, t + 5, 4.5], [2, 1, 3], C("#2a6a3a"), rot=[-30, 0, 0])                 # its knot tails
    m.cube("head", [-5, t + 2, -4.6], [1, 1, 1], C("#2a1a10")); m.cube("head", [4, t + 2, -4.6], [1, 1, 1], C("#2a1a10"))   # moustache tips
    m.cube("body", [-4.5, t - 4, -2.5], [3, 1, 5], C("#e8e8e8", "#d8d8d8"))                    # towel on the shoulder
    m.cube("body", [-4.6, t - 9, 1], [1, 5, 2], C("#e8e8e8", "#d8d8d8"))
    # the tankard in the right hand
    m.cube("rhand", [-8, t - 16, -2], [4, 5, 4], C("#8a6a3a", "#7a5a2e"), art={"north": A.at(4, 5, {(x, 0): "f" for x in range(4)}, f="#f8f4e8")})
    m.cube("rhand", [-4, t - 15, -0.5], [1, 3, 1], C("#6a6a6a"))
    biped_anims(m, "claw")
    talk_clip(m)
    anim(m, "flourish", 2.5, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-50, 20, 0]), (2.0, [-50, 20, 0]), (2.5, [0, 0, 0]))},
        "larm": {"rotation": wave(2.5, lambda q: [-55 + S(3 * q) * 10, -30, S(3 * q) * 15])},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [15, 10, 0]), (2.0, [15, 10, 0]), (2.5, [0, 0, 0]))}}, loop=False)
    return m


def curio_dealer(seed):
    m = Rig("trader_curio_dealer", 128, 128, seed)
    skin = ["#8a6048", "#7a543e", "#966a52"]
    ROBE = over(C("#4a2a6a", "#3e2258", "#56327a"), spots("#c8a040", 0.03))
    GOLD = C("#c8a040", "#e0b850", "#b89030")
    a = biped(m, head=noise(skin), body=bands(ROBE, (8, 9, GOLD)), arm=bands(ROBE, (0, 1, GOLD)), leg=ROBE,
              art={"head": {"north": face(skin[0], "#101010", "#e0a020", beard="#1a1a1a"), "east": side(skin[0], "#101010"), "west": side(skin[0], "#101010")}})
    t = a["top"]
    m.cube("body", [-4.5, 0, -2.5], [9, 12, 5], ROBE, art={"north": A.at(9, 12, {(4, y): "g" for y in range(12)}, g="#c8a040")})   # long robe
    TURBAN = C("#e0d8c0", "#d0c8b0", "#e8e0cc")
    m.cube("head", [-4.5, t + 6, -4.5], [9, 3, 9], TURBAN)
    m.cube("head", [-4, t + 9, -4], [8, 3, 8], TURBAN)
    m.cube("head", [-3, t + 12, -3], [6, 2, 6], TURBAN)
    m.cube("head", [-1, t + 8, -5], [2, 2, 1], lit("#3fe0c0"))                                  # the gem
    m.cube("head", [-0.5, t + 10, -5], [1, 3, 1], C("#e8e0ff"), rot=[-15, 0, 0])               # a feather in the turban
    for x in (-4.6, 3.6):
        m.cube("head", [x, t + 2, -1], [1, 2, 1], GOLD)                                        # earrings
    # the glowing curio box (left hand) with a lid bone
    m.cube("lhand", [4, t - 16, -3], [4, 4, 5], over(C("#2e2a3a", "#3a3448"), veins("#3fe0c0", 0.25, glow=True)))
    m.bone("lid", [6, t - 12, 2], "lhand")
    m.cube("lid", [4, t - 12, -3], [4, 1, 5], over(C("#3a3448"), spots("#c8a040", 0.2)))
    m.cube("lhand", [5, t - 12.5, -2], [2, 1, 3], lit("#3fe0c0"))                               # its glow
    biped_anims(m, "claw")
    talk_clip(m)
    anim(m, "flourish", 3.0, {
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-55, -20, 0]), (2.5, [-55, -20, 0]), (3.0, [0, 0, 0]))},
        "lid": {"rotation": keys((0, [0, 0, 0]), (0.8, [0, 0, 0]), (1.2, [-80, 0, 0]), (2.2, [-80, 0, 0]), (2.6, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.9, [20, 15, 0]), (2.2, [20, 15, 0]), (3.0, [0, 0, 0]))}}, loop=False)
    return m


def gunsmith(seed):
    m = Rig("trader_gunsmith", 128, 128, seed)
    skin = ["#c8906a", "#b8805a", "#d49c76"]
    LEATHER = C("#5a3a20", "#4a2e18", "#6a4426")
    SHIRT = over(C("#8a8070", "#7a7060"), spots("#2a2420", 0.06))
    a = biped(m, head=noise(skin), body=SHIRT, arm=bands(SHIRT, (0, 3, noise(skin))), leg=C("#3a3430", "#2e2a26"),
              art={"head": {"north": face(skin[0], "#3a2a1e", "#3a2a1e", stache="#3a2a1e"), "east": side(skin[0], "#3a2a1e"), "west": side(skin[0], "#3a2a1e")}})
    t = a["top"]
    m.cube("body", [-3.75, 4, -2.6], [7.5, 16, 1], LEATHER)                                         # leather apron
    m.cube("body", [-4.2, 12, -2.8], [1, 12, 1], C("#3a2414"), rot=[0, 0, -30], pivot=[0, 18, -2.8])   # bandolier
    for i in range(4):
        m.cube("body", [-2.8 + i * 1.6, 13.5 + i * 2.3, -3.4], [1, 1.5, 1], C("#c8a040"))
    m.cube("head", [-4.5, t + 6.25, -4.5], [9, 2, 9], C("#2a2420"))                               # skullcap
    m.cube("head", [-4, t + 6.5, -5], [8, 2, 1], C("#6a5a3a"), art={"north": A.at(8, 2, {(1, 0): "l", (2, 0): "l", (5, 0): "l", (6, 0): "l"}, l="#9ad0e8")})  # goggles up
    # a musket slung across the back
    m.cube("body", [-1, 10, 2.1], [2, 18, 1], C("#6a4426"), rot=[0, 0, 35], pivot=[0, 18, 2.6])
    m.cube("body", [-0.5, 22, 2.2], [1, 8, 1], C("#4a4e56"), rot=[0, 0, 35], pivot=[0, 18, 2.6])
    # pistol in the right hand
    m.cube("rhand", [-7, t - 15, -5], [2, 2, 6], C("#4a4e56", "#5a5e66")); m.cube("rhand", [-6.75, t - 17, -1.25], [1.5, 3, 2], C("#6a4426"))
    biped_anims(m, "claw")
    talk_clip(m)
    anim(m, "flourish", 2.5, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-90, -10, 0]), (1.8, [-90, -10, 0]), (2.0, [-110, -10, 0]), (2.5, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.5, [0, 12, 0]), (1.8, [0, 12, 0]), (2.5, [0, 0, 0]))}}, loop=False)
    return m


def chandler(seed):
    m = Rig("trader_chandler", 128, 128, seed)
    skin = ["#d0a080", "#c09070", "#dcae8c"]
    OIL = C("#c8a830", "#b89820", "#d0b038")
    a = biped(m, head=noise(skin), body=bands(C("#2a3a4a", "#223242"), (10, 12, C("#2a1e14"))), arm=C("#2a3a4a", "#223242"),
              leg=C("#3a3028", "#2e261e"),
              art={"head": {"north": face(skin[0], "#e8e8e8", "#3a5a8a", beard="#f0f0f0", stache="#f0f0f0"), "east": side(skin[0], "#e8e8e8"), "west": side(skin[0], "#e8e8e8")}})
    t = a["top"]
    m.cube("head", [-5, t + 6, -5], [10, 3, 10], OIL)                                          # sou'wester
    m.cube("head", [-5.5, t + 5, -5.5], [11, 1, 11], OIL)
    m.cube("head", [-4, t - 3, -4.8], [8, 3, 1], C("#f0f0f0"))                                 # long beard
    # coil of rope over the left shoulder
    m.bone("rope", [8, t - 2, 0], "body")
    for i, (dy, dz) in enumerate([(0, 0), (-2, 1), (-4, 1.5), (-6, 1)]):
        m.cube("rope", [8, t - 4 + dy, -3 + dz], [2, 1, 6], C("#b89a6a", "#a8895a"))
    # lantern in the right hand
    m.cube("rhand", [-7.5, t - 17, -1.5], [3, 4, 3], over(C("#3a3a3a"), spots("#ffcf6a", 0.25, glow=True)))
    m.cube("rhand", [-6.5, t - 16, -0.5], [1, 2, 1], lit("#ffd070"))
    biped_anims(m, "claw")
    talk_clip(m)
    anim(m, "flourish", 3.0, {
        "larm": {"rotation": wave(3.0, lambda q: [-40 + S(2 * q) * 20, 0, -10])},
        "rope": {"rotation": wave(3.0, lambda q: [0, S(2 * q) * 12, 0])},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.6, [15, -10, 0]), (2.4, [15, -10, 0]), (3.0, [0, 0, 0]))}}, loop=False)
    return m


def cook(seed):
    m = Rig("trader_cook", 128, 128, seed)
    skin = ["#c0886a", "#b0785a", "#cc9476"]
    WHITE = C("#f0ece4", "#e4e0d8", "#faf6ee")
    APRON = over(C("#ece6d6", "#f4eee0"), spots("#a86a3a", 0.05), spots("#8a2a1a", 0.02))
    a = biped(m, head=noise(skin), body=WHITE, arm=bands(WHITE, (0, 3, noise(skin))), leg=C("#3a3a44", "#30303a"),
              art={"head": {"north": face(skin[0], "#2a1a10", "#3a2a1a", stache="#2a1a10"), "east": side(skin[0], "#2a1a10"), "west": side(skin[0], "#2a1a10")}})
    t = a["top"]
    m.cube("body", [-5, 11, -4], [10, 8, 7], WHITE)                                            # the belly
    m.cube("body", [-4.5, 4.75, -4.1], [9, 14, 1], APRON)
    m.cube("head", [-4.5, t + 7, -4.5], [9, 2, 9], WHITE)                                      # toque band
    m.cube("head", [-5, t + 9, -5], [10, 5, 10], WHITE)                                        # its puff
    m.cube("head", [-4.6, t + 4, -4.6], [9, 1, 1], C("#c83a2a"))                               # neckerchief peeking
    # ladle in the right hand
    m.cube("rhand", [-6.5, t - 13, -1.5], [1, 11, 1], C("#a8a8b0"))                             # ladle held upright
    m.cube("rhand", [-7.5, t - 3, -2.5], [3, 2, 3], C("#a8a8b0", "#c8c8d0"))
    biped_anims(m, "claw")
    talk_clip(m)
    anim(m, "flourish", 3.0, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-50, 0, 0]), (0.8, [-50, 30, 0]), (1.3, [-50, -30, 0]), (1.8, [-50, 30, 0]),
                                  (2.2, [-110, 20, -10]), (2.7, [-110, 20, -10]), (3.0, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (2.2, [15, 0, 0]), (2.5, [-10, 0, 0]), (2.8, [15, 0, 0]), (3.0, [0, 0, 0]))}}, loop=False)
    return m


def map_merchant(seed):
    m = Rig("map_merchant", 128, 128, seed)
    skin = ["#e0c8b0", "#d0b8a0", "#ecd4bc"]
    CLOAK = over(C("#1e2436", "#242a3e", "#1a1e2e"), spots("#3a4058", 0.05))
    a = biped(m, head=noise(skin), body=bands(CLOAK, (10, 11, C("#6a4a2a"))), arm=CLOAK, leg=C("#2a2e3e", "#22263a"),
              art={"head": {"north": face(skin[0], "#1a1a22", "#6ad8ff"), "east": side(skin[0], "#1a1a22"), "west": side(skin[0], "#1a1a22")}})
    t = a["top"]
    m.cube("body", [-4.5, 0, -2.5], [9, 13, 5], CLOAK)                                         # long cloak skirts
    # the hood
    m.cube("head", [-4.6, t - 0.5, -4.6], [9.2, 9.5, 9.2], CLOAK, art={"north": A.at(9, 9, {(x, y): "_" for x in range(2, 7) for y in range(2, 9)})})
    m.cube("head", [-2, t + 8, 3], [4, 3, 3], CLOAK, rot=[-25, 0, 0])                           # hood point
    # the frame pack stuffed with rolled maps
    m.bone("pack", [0, t - 2, 2], "body")
    m.cube("pack", [-4, t - 12, 2.5], [8, 10, 4], C("#6a4a2a", "#5a3e22"))
    for i, (x, h, col) in enumerate([(-3.5, 14, "#e8dcb4"), (-1.2, 16, "#d8c894"), (1.1, 13, "#e8dcb4"), (3, 15, "#c8b884")]):
        m.cube("pack", [x - 0.25, t - 10 - i * 0.3, 3.1 + i * 0.2], [1.5, h, 1.5], C(col, "#b8a474"))
    m.cube("pack", [-4.5, t - 9, 2.2], [9, 1, 5], C("#3a2414"))                                  # straps
    # spyglass at the belt, a chart in the right hand (unrolled by the talk clip)
    m.cube("body", [3.5, 8, -2], [1.5, 1.5, 5], C("#c8a040", "#8a6a2a"), rot=[0, 20, 0])
    m.bone("chart", [-6, t - 12, -2], "rhand")
    m.cube("chart", [-6.5, t - 13, -4], [1, 2, 4], C("#e8dcb4", "#d8c894"))
    m.cube("chart", [-6.75, t - 18, -4], [0.5, 6, 7], C("#e8dcb4", "#d8c894"),
           art={"west": A.at(7, 6, {(1, 1): "r", (2, 2): "r", (3, 2): "r", (5, 3): "x", (4, 4): "r"}, r="#8a3a2a", x="#c81a1a")})
    biped_anims(m, "claw", cape="pack")
    talk_clip(m, {"chart": {"scale": keys((0, [1, 1, 1]), (0.3, [1, 1, 1]), (1.6, [1, 1, 1]), (2.0, [1, 1, 1]))},
                  "larm": {"rotation": keys((0, [0, 0, 0]), (0.4, [-55, 20, -10]), (1.6, [-55, 20, -10]), (2.0, [0, 0, 0]))}})
    anim(m, "flourish", 3.0, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-65, 20, 10]), (2.5, [-65, 20, 10]), (3.0, [0, 0, 0]))},
        "larm": {"rotation": keys((0, [0, 0, 0]), (0.5, [-65, -20, -10]), (2.5, [-65, -20, -10]), (3.0, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.6, [25, 0, 0]), (1.4, [25, 15, 0]), (2.2, [25, -15, 0]), (3.0, [0, 0, 0]))}}, loop=False)
    return m


def _with_sit(build):
    """Every merchant also sits of an evening in the inn (homestead/town/Lodging): legs forward, hips on the stool."""
    def b(seed):
        m = build(seed)
        anim(m, "sit", 4.0, {"root": {"position": keys((0, [0, -12, 0]))}, "rleg": {"rotation": keys((0, [-85, 8, 0]))},
                             "lleg": {"rotation": keys((0, [-85, -8, 0]))}, "rarm": {"rotation": keys((0, [-30, 0, 5]))},
                             "larm": {"rotation": keys((0, [-30, 0, -5]))},
                             "head": {"rotation": wave(4.0, lambda q: [5 + S(q) * 3, S(q * 0.5) * 18, 0])}})
        return m
    return b


MOBS = {"trader_quartermaster": quartermaster, "trader_fishmonger": fishmonger, "trader_barkeep": barkeep,
        "trader_curio_dealer": curio_dealer, "trader_gunsmith": gunsmith, "trader_chandler": chandler,
        "trader_cook": cook, "map_merchant": map_merchant}
SKINS = {}

MOBS = {k: _with_sit(v) for k, v in MOBS.items()}
