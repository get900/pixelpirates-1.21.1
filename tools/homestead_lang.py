"""Merge the homestead lang entries into en_us.json (re-runnable; add entries per feature)."""
import json, collections, pathlib

LANG = pathlib.Path(__file__).resolve().parents[1] / "src/main/resources/assets/pixelpirates/lang/en_us.json"
E = {
    "itemgroup.pixelpirates.homestead": "Pixel Pirates: Homestead",
    # #11 crops + galley
    "block.pixelpirates.pineapple_crop": "Pineapples", "block.pixelpirates.lime_crop": "Lime Bush", "block.pixelpirates.chili_crop": "Chili Peppers",
    "item.pixelpirates.pineapple_crown": "Pineapple Crown", "item.pixelpirates.lime_seeds": "Lime Seeds", "item.pixelpirates.chili_seeds": "Chili Seeds",
    "item.pixelpirates.pineapple": "Pineapple", "item.pixelpirates.lime": "Lime", "item.pixelpirates.chili_pepper": "Chili Pepper",
    "item.pixelpirates.tropical_fruit_salad": "Tropical Fruit Salad", "item.pixelpirates.ceviche": "Ceviche",
    "item.pixelpirates.spicy_chowder": "Spicy Chowder", "item.pixelpirates.chocolate_doubloon": "Chocolate Doubloon",
    "item.pixelpirates.pineapple_grog": "Pineapple Grog",
    # #12 rum
    "block.pixelpirates.rum_still": "Rum Still", "block.pixelpirates.aging_cask": "Aging Cask",
    "item.pixelpirates.molasses": "Jug of Molasses", "item.pixelpirates.raw_rum": "Raw Rum", "item.pixelpirates.aged_rum": "Aged Rum",
    "item.pixelpirates.vintage_rum": "Vintage Rum", "effect.pixelpirates.tipsy": "Tipsy",
    # #5 building
    "block.pixelpirates.palm_stairs": "Palm Stairs", "block.pixelpirates.palm_slab": "Palm Slab", "block.pixelpirates.palm_fence": "Palm Fence",
    "block.pixelpirates.palm_fence_gate": "Palm Fence Gate", "block.pixelpirates.palm_door": "Palm Door", "block.pixelpirates.palm_trapdoor": "Palm Trapdoor",
    "block.pixelpirates.thatch": "Thatch", "block.pixelpirates.thatch_stairs": "Thatch Stairs", "block.pixelpirates.thatch_slab": "Thatch Slab",
    "block.pixelpirates.woven_palm_screen": "Woven Palm Screen", "block.pixelpirates.woven_mat": "Woven Mat",
    "block.pixelpirates.driftwood_fence": "Driftwood Fence", "block.pixelpirates.driftwood_fence_gate": "Driftwood Fence Gate",
    "block.pixelpirates.rope_ladder": "Rope Ladder", "block.pixelpirates.rope_bridge": "Rope Bridge", "block.pixelpirates.tiki_torch": "Tiki Torch",
    # #2 furniture
    "block.pixelpirates.captains_desk": "Captain's Desk", "block.pixelpirates.sea_chest": "Sea Chest", "block.pixelpirates.cargo_crate": "Cargo Crate",
    "block.pixelpirates.treasure_pedestal": "Treasure Pedestal", "block.pixelpirates.rum_rack": "Rum Rack", "block.pixelpirates.hanging_net": "Hanging Net",
    "block.pixelpirates.rope_coil": "Rope Coil", "block.pixelpirates.hanging_rope": "Hanging Rope", "block.pixelpirates.ships_wheel": "Ship's Wheel",
    "block.pixelpirates.display_cannon": "Display Cannon", "block.pixelpirates.map_table": "Map Table",
    "block.pixelpirates.captains_chair": "Captain's Chair", "block.pixelpirates.barrel_stool": "Barrel Stool", "entity.pixelpirates.seat": "Seat",
    # #21 guns
    "item.pixelpirates.flintlock_pistol": "Flintlock Pistol", "item.pixelpirates.blunderbuss": "Blunderbuss",
    "item.pixelpirates.paper_cartridge": "Paper Cartridge", "item.pixelpirates.scattershot": "Scattershot",
    "entity.pixelpirates.musket_ball": "Musket Ball",
    "block.pixelpirates.treasure_hoard": "Treasure Hoard",
    # #13 fishing
    "item.pixelpirates.parrotfish": "Parrotfish", "item.pixelpirates.red_snapper": "Red Snapper", "item.pixelpirates.mahi_mahi": "Mahi-Mahi", "item.pixelpirates.lionfish": "Lionfish", "item.pixelpirates.moonfish": "Moonfish", "item.pixelpirates.emberfin": "Emberfin", "item.pixelpirates.lava_eel": "Lava Eel", "item.pixelpirates.ghostfin": "Ghostfin", "item.pixelpirates.bonefish": "Bonefish", "item.pixelpirates.anglerfry": "Anglerfry", "item.pixelpirates.voidfin": "Voidfin",
    "item.pixelpirates.cooked_fish_fillet": "Cooked Fish Fillet", "item.pixelpirates.lobster": "Lobster", "item.pixelpirates.cooked_lobster": "Cooked Lobster",
    "item.pixelpirates.crab_claw": "Crab Claw", "item.pixelpirates.salvage_hook": "Salvage Hook",
    "block.pixelpirates.fish_trap": "Fish Trap Net", "block.pixelpirates.lobster_pot": "Lobster Pot",
    "block.pixelpirates.golden_marlin_trophy": "Golden Marlin", "block.pixelpirates.ghost_swordfish_trophy": "Ghost Swordfish",
    "block.pixelpirates.coelacanth_trophy": "Abyssal Coelacanth",
    "block.pixelpirates.salvage_crate": "Salvage Crate",
    "item.pixelpirates.captains_spyglass": "Captain's Spyglass", "item.pixelpirates.compass_of_desire": "Compass of Desire",
    "block.pixelpirates.jolly_roger": "Jolly Roger", "block.pixelpirates.mooring_post": "Mooring Post",
    "item.pixelpirates.grappling_hook": "Grappling Hook", "entity.pixelpirates.grapple_hook": "Grappling Hook",
    "item.pixelpirates.chain_shot": "Chain Shot", "item.pixelpirates.grape_shot": "Grape Shot",
    "block.pixelpirates.mermaid_figurehead": "Mermaid Figurehead", "block.pixelpirates.kraken_figurehead": "Kraken Figurehead",
    "block.pixelpirates.dread_skull_figurehead": "Dread Skull Figurehead", "block.pixelpirates.navy_eagle_figurehead": "Navy Eagle Figurehead",
    "block.pixelpirates.white_sail_canvas": "White Sail Canvas", "block.pixelpirates.black_sail_canvas": "Black Sail Canvas",
    "block.pixelpirates.crimson_sail_canvas": "Crimson Sail Canvas", "block.pixelpirates.striped_sail_canvas": "Striped Sail Canvas",
    "block.pixelpirates.jolly_roger_sail_canvas": "Jolly Roger Sail Canvas",
    "block.pixelpirates.roulette_table": "Roulette Table",
    "block.pixelpirates.trading_post": "Trading Post", "block.pixelpirates.bounty_board": "Bounty Board", "entity.pixelpirates.port_trader": "Port Trader",
    "item.pixelpirates.port_trader_spawn_egg": "Port Trader Spawn Egg",
    # #24 region tools
    "item.pixelpirates.ember_pickaxe": "Ember Pickaxe", "item.pixelpirates.ember_axe": "Ember Axe", "item.pixelpirates.ember_shovel": "Ember Shovel", "item.pixelpirates.ember_hoe": "Ember Hoe", "item.pixelpirates.kraken_pickaxe": "Kraken Scale Pickaxe", "item.pixelpirates.kraken_axe": "Kraken Scale Axe", "item.pixelpirates.kraken_shovel": "Kraken Scale Shovel", "item.pixelpirates.kraken_hoe": "Kraken Scale Hoe", "item.pixelpirates.bone_pickaxe": "Cursed Bone Pickaxe", "item.pixelpirates.bone_axe": "Cursed Bone Axe", "item.pixelpirates.bone_shovel": "Cursed Bone Shovel", "item.pixelpirates.bone_hoe": "Cursed Bone Hoe",
}

if __name__ == "__main__":
    d = json.loads(LANG.read_text(encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    d.update(E)
    LANG.write_text(json.dumps(d, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(len(E), "homestead entries")
