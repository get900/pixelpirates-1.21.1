"""PORT TRADERS (homestead #15, 2026-09-30): one biped geometry, four painted professions.

  port_trader              - the QUARTERMASTER (base texture): navy waistcoat over a linen shirt, tricorn, a ledger
                             under the left arm, keys + coin purse at the belt.
  port_trader_fishmonger   - oilskin apron over a striped jersey, knitted cap, the ledger becomes a fish.
  port_trader_barkeep      - red waistcoat, white apron, a bald head with a bandana, the ledger is a rum bottle.
  port_trader_curio_dealer - purple robe with gold trim, turban, the ledger is a glowing curio box.

Skins must add the SAME cubes in the SAME order (gen_mob_roster checks), so every profession is built by one function
that only swaps materials/art. Clips: idle, move (from biped_anims), talk (a sales pitch: palm up, nod).
"""
from mobkit import A, Rig, bands, noise, over, solid, lit, spots, speckle, veins, anim, keys, wave, S
from mobs.common import biped, biped_anims

LOOKS = {
    "quartermaster": dict(
        skin=["#c89a7a", "#b88a6a", "#d4a888"], coat=["#223a50", "#1c3246", "#2a4458"], shirt=["#e8e0cc", "#d8d0bc"],
        trou=["#4a3a2a", "#3e3022"], boot=["#2a1e14", "#342618"], hat=["#1e1a18", "#2a2420"], hat_trim="#c8a040",
        apron=["#223a50", "#1c3246"], held=["#6a3a20", "#5a3018"], held_glow=None, hair="#4a3222", eye="#2a3a6a"),
    "fishmonger": dict(
        skin=["#d8a888", "#c8987a", "#e0b494"], coat=["#e8e8e0", "#2a4a7a", "#e8e8e0", "#2a4a7a"], shirt=["#e8e8e0", "#2a4a7a"],
        trou=["#3a4048", "#30363e"], boot=["#e0c030", "#c8aa28"], hat=["#8a2a22", "#9a3228"], hat_trim="#6a1e18",
        apron=["#c8a830", "#b89820", "#d0b038"], held=["#7a9aa8", "#8aaab8", "#6a8a98"], held_glow=None, hair="#8a5a3a", eye="#3a6a4a"),
    "barkeep": dict(
        skin=["#b07a5a", "#a06a4a", "#bc8866"], coat=["#8a2020", "#7a1a1a", "#9a2828"], shirt=["#e0d8c4", "#d0c8b4"],
        trou=["#2a2420", "#342c26"], boot=["#3a2618", "#2e1e12"], hat=["#2a6a3a", "#22583a"], hat_trim="#e0d0a0",
        apron=["#ece6d6", "#e0dac8", "#f4eee0"], held=["#5a2a10", "#6a3414", "#4a220c"], held_glow=None, hair="#1e1410", eye="#4a2a1a"),
    "curio_dealer": dict(
        skin=["#8a6048", "#7a543e", "#966a52"], coat=["#4a2a6a", "#3e2258", "#56327a"], shirt=["#c8a040", "#b89030"],
        trou=["#3e2258", "#34204a"], boot=["#2a1e2e", "#221826"], hat=["#e0d8c0", "#d0c8b0", "#e8e0cc"], hat_trim="#3fe0c0",
        apron=["#4a2a6a", "#3e2258"], held=["#2e2a3a", "#3a3448"], held_glow="#3fe0c0", hair="#101010", eye="#e0a020"),
}


def trader(seed, look="quartermaster"):
    L = LOOKS[look]
    m = Rig("port_trader", 64, 64, seed)
    face = A("""hhhhhhhh
hhhhhhhh
hbbhhbbh
swEsswEs
ssssssss
sssnnsss
ssmmmmss
ssssssss""", dict(h=L["hair"], b=L["hair"], s=L["skin"][0], w="#f0ece0", E=L["eye"], n=L["skin"][1], m="#6a3a2a"))
    side = A("hhhhhhhh\nhhhhhhhh\nhhhhhhss\nhhhhssss\nhhhsssss\nhhssssss\nhsssssss\nssssssss", dict(h=L["hair"], s=L["skin"][0]))
    body = bands(noise(L["coat"]), (10, 11, noise(["#2a1e14", "#3a2a1a"])))             # belt at the waist
    shirt = A.at(8, 12, {**{(x, y): "s" for x in (3, 4) for y in range(1, 12)}, (3, 3): "k", (4, 6): "k", (3, 9): "k"},
                 s=L["shirt"][0], k=L["hat_trim"])
    arm = bands(noise(L["skin"]), (0, 9, noise(L["coat"])))
    leg = bands(noise(L["boot"]), (0, 7, noise(L["trou"])))
    a = biped(m, head=noise(L["skin"]), body=body, arm=arm, leg=leg,
              art={"head": {"north": face, "east": side, "west": side}, "body": {"north": shirt}})
    t = a["top"]
    # the hat: crown + brim (a tricorn, cap, bandana or turban depending on the paint)
    m.cube("head", [-4.5, t + 7, -4.5], [9, 2, 9], noise(L["hat"]))
    m.cube("head", [-3.5, t + 9, -3.5], [7, 2, 7], noise(L["hat"]),
           art={"north": A.at(7, 2, {(3, 1): "k", (3, 0): "k"}, k=L["hat_trim"])})
    m.cube("head", [-5.5, t + 6, -5.5], [11, 1, 1], noise(L["hat"]), art={"north": A.at(11, 1, {(x, 0): "k" for x in range(11)}, k=L["hat_trim"])})
    # the apron / waistcoat skirt
    m.bone("apron", [0, 12, -2], "body")
    m.cube("apron", [-3.5, 5, -2.6], [7, 8, 1], noise(L["apron"]), art={"north": A.at(7, 8, {(3, 1): "p", (3, 2): "p"}, p="#2a1e14")})
    # coin purse + keys at the belt
    m.cube("body", [4, 10, -1], [1, 3, 2], noise(["#6a4a22", "#7a5628"]))
    m.cube("body", [-5, 10, -1], [1, 2, 2], noise(["#8a8a8a", "#a8a8a8"]))
    # the thing he holds tucked in the left hand (ledger / fish / bottle / curio box)
    held = noise(L["held"]) if not L["held_glow"] else over(noise(L["held"]), veins(L["held_glow"], 0.25, glow=True))
    m.cube("lhand", [4.5, t - 14, -3], [3, 4, 6], held)
    biped_anims(m, "claw")
    anim(m, "talk", 2.0, {
        "rarm": {"rotation": keys((0, [0, 0, 0]), (0.3, [-60, -20, 10]), (1.0, [-55, 10, 15]), (1.6, [-60, -20, 10]), (2.0, [0, 0, 0]))},
        "head": {"rotation": keys((0, [0, 0, 0]), (0.4, [8, 0, 0]), (0.7, [-4, 0, 0]), (1.0, [8, 0, 0]), (1.3, [0, 10, 0]), (2.0, [0, 0, 0]))},
        "body": {"rotation": keys((0, [0, 0, 0]), (0.5, [4, 0, 0]), (1.5, [4, 0, 0]), (2.0, [0, 0, 0]))}}, loop=False)
    return m


MOBS = {"port_trader": trader}
SKINS = {"port_trader": {k: (lambda seed, k=k: trader(seed, k)) for k in ("fishmonger", "barkeep", "curio_dealer")}}
