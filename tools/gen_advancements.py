"""The Pixel Pirates advancement tab (rewritten 2026-09-30 to follow the game as it is now).

    python tools/gen_advancements.py   -> data/pixelpirates/advancements/*.json + lang entries; deletes OBSOLETE ones

Five branches off the root: SEAFARING (raft -> ship -> cannons), SURVIVAL & THE GALLEY (food, healing, 40 dishes),
THE BOSS CHAIN (the 10 bosses in order, each naming the zone it opens; relic weapons + boss armor hang off it),
LIFE ASHORE (homestead: fishing, rum, guns, hideout) and the MERCHANT (maps).
Trigger "code" = minecraft:impossible, granted from Java via AdvancementHelper.grant(player, id) - criterion "got_here".
Boss advancements are "boss_<chain boss id>", granted in BossProgression.onBossKilled for every credited player.
"""
import json, collections, pathlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ADV = ROOT / "src/main/resources/data/pixelpirates/advancements"
LANG = ROOT / "src/main/resources/assets/pixelpirates/lang/en_us.json"

OBSOLETE = ["into_the_shallows", "beyond_the_reef", "the_pirate_seas", "the_cursed_seas", "the_abyss",
            "borrowed_time", "brave_the_waves"]

P = "pixelpirates:"
FISH = ["parrotfish", "red_snapper", "mahi_mahi", "lionfish", "moonfish", "emberfin", "lava_eel", "ghostfin", "bonefish",
        "anglerfry", "voidfin"]
RELIC_WEAPONS = ["rackham_blunderbuss", "serpentspine_longbow", "everburning_flail", "dutchmans_hand_cannon", "sunken_trident",
                 "bloodfin_maw", "krakenmaw_harpoon_gun", "chainbreaker", "heartseeker", "tidefathers_wrath"]
DISHES = ["banana_fritters", "banana_pudding", "coconut_macaroon", "coconut_cream_pie", "seaweed_salad", "kelp_crisps",
          "plum_duff", "biscuit_porridge", "tropical_smoothie", "hot_cocoa", "salmagundi", "grog_glazed_pork", "shark_jerky",
          "shark_fin_soup", "fish_and_chips", "crab_cakes", "seafood_boil", "lobster_thermidor", "kraken_ink_pasta",
          "grilled_pineapple", "pineapple_upside_down_cake", "pineapple_salsa", "key_lime_pie", "limeade", "scurvy_tonic",
          "fire_roasted_chili", "chili_con_shark", "coconut_curry", "blackened_fillet", "fish_taco", "rum_cake",
          "molasses_cookie", "parrotfish_poke", "mahi_pineapple_skewer", "moonfish_sashimi", "ember_eel_skewer",
          "ghostfin_broth", "bone_broth", "anglerfry_chowder", "voidfin_roe"]
# one signature dish from each part of the menu
FEAST = ["banana_pudding", "seaweed_salad", "shark_jerky", "fish_and_chips", "coconut_curry", "key_lime_pie",
         "scurvy_tonic", "parrotfish_poke", "ember_eel_skewer", "anglerfry_chowder"]


def code():
    return {"got_here": {"trigger": "minecraft:impossible"}}


def has(*items):                      # obtain ANY of these
    return {"got_here": {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [P + i if ":" not in i else i for i in items]}]}}}


def has_each(items):                  # obtain ALL of these (one criterion each)
    return {i.split(":")[-1]: {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": [P + i if ":" not in i else i]}]}}
            for i in items}


def eat(*items):
    return {"got_here": {"trigger": "minecraft:consume_item", "conditions": {"item": {"items": [P + i if ":" not in i else i for i in items]}}}}


def eat_each(items):
    return {i: {"trigger": "minecraft:consume_item", "conditions": {"item": {"items": [P + i]}}} for i in items}


def placed(block):
    return {"got_here": {"trigger": "minecraft:placed_block", "conditions": {"location": [{"condition": "minecraft:location_check",
            "predicate": {"block": {"blocks": [P + block]}}}]}}}


# id: (parent, icon, frame, title, description, criteria[, hidden])
A = collections.OrderedDict()


def add(id, parent, icon, frame, title, desc, criteria, hidden=False):
    A[id] = (parent, icon, frame, title, desc, criteria, hidden)


add("root", None, "pirate_coin", "task", "Pixel Pirates", "Ten bosses, five seas, one Leviathan. Set sail.",
    {"got_here": {"trigger": "minecraft:tick"}})

# ---- SEAFARING
add("got_sea_legs", "root", "raft_item", "task", "Got Your Sea Legs?", "Craft a raft and take to the water", has("raft_item"))
add("money_well_spent", "got_sea_legs", "ship_blueprint", "task", "Money Well Spent", "Commission a ship from the Shipwright on the grand pier", code())
add("captain_now", "money_well_spent", "ship_helm", "goal", "Captain Now", "Take the helm and steer your own ship", code())
add("broadside", "captain_now", "cannon_ball", "task", "Fire!", "Load a cannon and fire a broadside", code())
add("she_holds", "captain_now", "ship_repair_kit", "task", "She'll Hold Together", "Patch your hull with a Ship Repair Kit", code())
add("home_at_sea", "money_well_spent", "ship_bedroll", "task", "Home at Sea", "Set your respawn on a Ship Bedroll aboard your vessel", code())
add("hammock_time", "got_sea_legs", "hammock", "task", "Hammock Time", "Sling a hammock and set your spawn", code())

# ---- SURVIVAL & THE GALLEY
add("island_fare", "root", "banana", "task", "Island Fare", "Eat a banana - pick a ripe bunch with right-click and it grows back", eat("banana"))
add("patched_up", "island_fare", "sea_bandage", "task", "Patched Up", "Make a Sea Bandage from paper, string and kelp", has("sea_bandage"))
add("coconut_water", "island_fare", "coconut_water", "task", "Fresh From the Shell", "Drink Coconut Water", eat("coconut_water"))
add("ship_cook", "island_fare", "banana_fritters", "task", "Ship's Cook", "Eat any dish from the galley", eat(*DISHES))
add("pirates_feast", "ship_cook", "salmagundi", "goal", "A Pirate's Feast", "Eat a Salmagundi - a bit of everything", eat("salmagundi"))
add("master_of_the_galley", "pirates_feast", "coconut_curry", "challenge", "Master of the Galley",
    "Eat ten signature dishes: banana pudding, seaweed salad, shark jerky, fish and chips, coconut curry, key lime pie, "
    "scurvy tonic, parrotfish poke, ember eel skewer and anglerfry chowder", eat_each(FEAST))
add("monster_of_the_deep", "root", "raw_shark_meat", "task", "Monster of the Deep", "Kill a shark", code())
add("shark_bait", "monster_of_the_deep", "cooked_shark_meat", "challenge", "Shark Bait", "Get eaten by a shark. You really thought you could swim out here?", code(), hidden=True)
add("full_pirate_kit", "monster_of_the_deep", "pirate_chestplate", "task", "Full Pirate Kit", "Suit up in a complete set of pirate armour", code())

# ---- THE BOSS CHAIN
add("powder_key", "root", "dynamite", "task", "The Powder Key", "Get your hands on dynamite - only a blast clears Blast Rubble, and every vault is sealed with it", has("dynamite"))
BOSSES = [  # chain id, icon, frame, title, description
    ("captain_rackham", "rackhams_diving_charm", "goal", "Powder-Mad", "Defeat Captain Rackham in his island fort. The Merchant Seas (Zone 2) open"),
    ("sea_serpent", "serpents_tide_pearl", "goal", "Scales and Tides", "Break the Tideward Stones and defeat the Sea Serpent. The Pirate Seas (Zone 3) open"),
    ("molten_warlord", "everburning_lantern", "goal", "Quenched", "Defeat the Molten Warlord in the Cinder Citadel. The Cursed Seas (Zone 4) open"),
    ("ghost_captain", "spectral_anchor", "goal", "Sink the Dutchman", "Ring the phantom bell, sink the Flying Dutchman and defeat its captain. The Abyss (Zone 5) opens"),
    ("abyssal_king", "royal_tide_sigil", "goal", "Low Tide", "Drain the Sunken Court and defeat the Abyssal King"),
    ("bloodfin", "bloodfin_razor_tooth", "goal", "Harpooned", "Pin the Bloodfin with a whaler's harpoon and kill it"),
    ("kraken", "krakens_ink_heart", "goal", "Release the Kraken", "Sever its arms, then defeat the Kraken"),
    ("chained_revenant", "broken_shackle", "goal", "Drawn and Quartered", "Pull the Gallowbrand and defeat the Chained Revenant"),
    ("abyssal_heart", "abyssal_heartstone", "challenge", "Cardiac Arrest", "Stop the Abyssal Heart. The Leviathan's Rift opens"),
    ("leviathan", "crown_of_the_drowned", "challenge", "There Is Only One", "Hunt the Leviathan across three lairs and slay it"),
]
prev = "powder_key"
for bid, icon, frame, title, desc in BOSSES:
    add("boss_" + bid, prev, icon, frame, title, desc, code())
    prev = "boss_" + bid
add("forged_in_victory", "boss_captain_rackham", "rackham_blunderbuss", "task", "Forged in Victory",
    "Forge a boss's relic into its weapon - it keeps the relic's power", has(*RELIC_WEAPONS))
add("arsenal_of_the_deep", "forged_in_victory", "tidefathers_wrath", "challenge", "Arsenal of the Deep",
    "Own all ten relic weapons", has_each(RELIC_WEAPONS))
add("boss_ward", "boss_captain_rackham", "powder_monkey_chestplate", "task", "Boss Ward",
    "Find a piece of boss armour in a boss's hoard",
    has(*[f"{s}_{p}" for s in ("powder_monkey", "forgeguard", "tidecourt", "gallowbreaker", "thalassar")
          for p in ("helmet", "chestplate", "leggings", "boots")]))
add("mantle_of_thalassar", "boss_ward", "thalassar_helmet", "challenge", "Mantle of Thalassar",
    "Own the full Mantle of Thalassar", has_each([f"thalassar_{p}" for p in ("helmet", "chestplate", "leggings", "boots")]))
add("pulled_from_the_stone", "boss_kraken", "gallowbrand", "task", "Pulled From the Stone", "Claim the Gallowbrand", has("gallowbrand"))

# ---- LIFE ASHORE (homestead)
add("gone_fishing", "root", "red_snapper", "task", "Gone Fishin'", "Catch one of the seas' own fish", has(*FISH))
add("master_angler", "gone_fishing", "voidfin", "challenge", "Master Angler", "Catch every fish of the five seas", has_each(FISH))
add("yo_ho_ho", "gone_fishing", "vintage_rum", "goal", "Yo Ho Ho", "Age a bottle of rum to Vintage", has("vintage_rum"))
add("sharpshooter", "gone_fishing", "flintlock_pistol", "task", "Sharpshooter", "Get a flintlock pistol", has("flintlock_pistol", "blunderbuss"))
add("swing_aboard", "gone_fishing", "grappling_hook", "task", "Swing Aboard!", "Get a grappling hook", has("grappling_hook"))
add("hideout", "gone_fishing", "jolly_roger", "goal", "Pirate Hideout", "Raise a Jolly Roger to claim a hideout", placed("jolly_roger"))
add("forged_weapon", "gone_fishing", "smiths_hammer", "task", "Hammer and Tongs", "Forge a blade on a Forge Anvil", code())
add("master_smith", "forged_weapon", "obsidian_halberd", "challenge", "Master of the Forge", "Forge every one of the smith's patterns",
    has_each(["crabclaw_sabre", "pearlguard_rapier", "pistol_cutlass", "obsidian_halberd", "soulreaver", "inkfang"]))

# ---- THE MERCHANT
add("meet_the_merchant", "root", "treasure_map_common", "task", "I Know a Guy...", "Find and speak to the Map Merchant", code())
add("x_marks_the_spot", "meet_the_merchant", "treasure_map_legendary", "task", "X Marks the Spot", "Dig up treasure with a treasure map", code())
add("eyes_on_the_horizon", "meet_the_merchant", "minecraft:compass", "goal", "Eyes on the Horizon", "Buy the Ship Radar from the Map Merchant", code())


def main():
    for f in OBSOLETE:
        (ADV / (f + ".json")).unlink(missing_ok=True)
    lang = json.loads(LANG.read_text(encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    for f in OBSOLETE:
        lang.pop(f"advancements.pixelpirates.{f}.title", None)
        lang.pop(f"advancements.pixelpirates.{f}.description", None)
    for id, (parent, icon, frame, title, desc, crit, hidden) in A.items():
        disp = {"icon": {"item": icon if ":" in icon else P + icon},
                "title": {"translate": f"advancements.pixelpirates.{id}.title"},
                "description": {"translate": f"advancements.pixelpirates.{id}.description"},
                "frame": frame, "show_toast": id != "root", "announce_to_chat": frame != "task", "hidden": hidden}
        if id == "root":
            disp["background"] = "minecraft:textures/gui/advancements/backgrounds/adventure.png"
        doc = {"display": disp}
        if parent:
            doc["parent"] = P + parent
        doc["criteria"] = crit
        if len(crit) > 1:
            doc["requirements"] = [[k] for k in crit]          # all of them
        (ADV / (id + ".json")).write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
        lang[f"advancements.pixelpirates.{id}.title"] = title
        lang[f"advancements.pixelpirates.{id}.description"] = desc
    LANG.write_text(json.dumps(lang, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(len(A), "advancements;", "removed", OBSOLETE)


if __name__ == "__main__":
    main()
