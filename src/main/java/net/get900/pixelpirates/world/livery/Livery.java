package net.get900.pixelpirates.world.livery;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * SHIP LIVERIES (2026-10-04): a livery repaints a player's ship by MATERIAL ROLE (see {@link Liveries.Role}): the hull
 * and deck woods, the trim logs, the rails, the paint bands, the sails, the emblem (Jolly Roger canvas), the lanterns.
 * Every swap keeps the block's shape (facing, half, axis, fence connections...), so the ship looks and handles the same.
 * Most are BOUGHT at the Shipwright (faction ones also need Friendly standing); SPECIAL ones can only be EARNED.
 * Shared by server (applying, unlocking) and client (the Livery tab: names, prices, swatches).
 */
public record Livery(String id, String name, String blurb, Kind kind, int price, @Nullable String unlock,
                     Wood hull, Wood deck, String trim, String band, String band2, String gilt,
                     String sail, String sail2, String emblem, String light) {

    /** BUY = coins; FACTION = coins + Friendly with {@code unlock} (a faction id); SPECIAL = earned only ({@code unlock}:
     *  "boss:<id>" = beat that chain boss, "sharks:<n>" = kill n sharks, "parrots:<n>" = n parrot types in the Roost). */
    public enum Kind { BUY, FACTION, SPECIAL }

    /** A wood family: planks, stairs, slab, fence (the four shapes a ship's woodwork comes in). */
    public record Wood(String planks, String stairs, String slab, String fence) {
        static Wood mc(String w) { return new Wood("minecraft:" + w + "_planks", "minecraft:" + w + "_stairs", "minecraft:" + w + "_slab", "minecraft:" + w + "_fence"); }
    }

    static final Wood OAK = Wood.mc("oak"), SPRUCE = Wood.mc("spruce"), BIRCH = Wood.mc("birch"), JUNGLE = Wood.mc("jungle"),
            ACACIA = Wood.mc("acacia"), DARK_OAK = Wood.mc("dark_oak"), MANGROVE = Wood.mc("mangrove"), CHERRY = Wood.mc("cherry"),
            BAMBOO = Wood.mc("bamboo"), CRIMSON = Wood.mc("crimson"), WARPED = Wood.mc("warped"),
            PALM = new Wood("pixelpirates:palm_planks", "pixelpirates:palm_stairs", "pixelpirates:palm_slab", "pixelpirates:palm_fence"),
            GHOST = new Wood("pixelpirates:ghostwood_planks", "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab", "minecraft:warped_fence"),
            BONE = new Wood("pixelpirates:bone_planks", "minecraft:birch_stairs", "minecraft:birch_slab", "minecraft:birch_fence");

    private static final String CANVAS = "pixelpirates:white_sail_canvas", BLACK = "pixelpirates:black_sail_canvas",
            CRIMSON_C = "pixelpirates:crimson_sail_canvas", STRIPED = "pixelpirates:striped_sail_canvas", SPECTRAL = "pixelpirates:spectral_sail",
            ROGER = "pixelpirates:jolly_roger_sail_canvas", LANTERN = "minecraft:lantern", SOUL = "minecraft:soul_lantern";

    private static String w(String c) { return "minecraft:" + c + "_wool"; }
    private static String t(String c) { return "minecraft:" + c + "_terracotta"; }
    private static String c(String c) { return "minecraft:" + c + "_concrete"; }
    private static String mc(String id) { return "minecraft:" + id; }

    private static Livery buy(String id, String name, String blurb, int price, Wood hull, Wood deck, String trim, String band, String band2,
                              String gilt, String sail, String sail2, String emblem, String light) {
        return new Livery(id, name, blurb, Kind.BUY, price, null, hull, deck, trim, band, band2, gilt, sail, sail2, emblem, light);
    }

    private static Livery faction(String id, String name, String blurb, String faction, Wood hull, Wood deck, String trim, String band,
                                  String band2, String gilt, String sail, String sail2, String emblem, String light) {
        return new Livery(id, name, blurb, Kind.FACTION, 60, faction, hull, deck, trim, band, band2, gilt, sail, sail2, emblem, light);
    }

    private static Livery special(String id, String name, String blurb, String unlock, Wood hull, Wood deck, String trim, String band,
                                  String band2, String gilt, String sail, String sail2, String emblem, String light) {
        return new Livery(id, name, blurb, Kind.SPECIAL, 0, unlock, hull, deck, trim, band, band2, gilt, sail, sail2, emblem, light);
    }

    /** Every livery, in the order the Livery tab lists them. */
    public static final List<Livery> ALL = List.of(
        // ---- the factions (coins + Friendly standing)
        faction("crimson_corsairs", "Crimson Corsairs", "Tarred black and blood-red, the Jolly Roger aloft", "pirates",
                DARK_OAK, SPRUCE, mc("stripped_dark_oak_log"), mc("red_nether_bricks"), mc("blackstone"), mc("gold_block"), BLACK, CRIMSON_C, ROGER, LANTERN),
        faction("emerald_company", "Emerald Company", "The trading house's green and gold, striped canvas", "merchants",
                SPRUCE, OAK, mc("stripped_birch_log"), t("green"), t("yellow"), mc("gold_block"), CANVAS, w("green"), STRIPED, LANTERN),
        faction("iron_armada", "Iron Armada", "Navy blue and white bands, crisp white sails", "navy",
                DARK_OAK, SPRUCE, mc("stripped_dark_oak_log"), c("white"), t("blue"), mc("gold_block"), CANVAS, w("blue"), CANVAS, LANTERN),
        // ---- bought outright
        buy("royal_regalia", "Royal Regalia", "White and gold, purple sails - a king's yacht", 150,
                BIRCH, BIRCH, mc("stripped_birch_log"), c("white"), c("yellow"), mc("gold_block"), w("purple"), CANVAS, w("yellow"), LANTERN),
        buy("volcanic_ember", "Volcanic Ember", "Charred hull, ember trim, ash-grey sails", 90,
                CRIMSON, DARK_OAK, mc("stripped_crimson_stem"), mc("polished_blackstone_bricks"), t("orange"), mc("shroomlight"), w("gray"), w("red"), w("orange"), LANTERN),
        buy("tropical_paradise", "Tropical Paradise", "Palm and bamboo, bright striped sails", 70,
                PALM, BAMBOO, mc("bamboo_block"), t("cyan"), t("yellow"), mc("gold_block"), STRIPED, w("orange"), w("lime"), LANTERN),
        buy("midnight_raider", "Midnight Raider", "Black on black - you never saw her coming", 80,
                DARK_OAK, DARK_OAK, mc("stripped_dark_oak_log"), c("black"), c("gray"), c("black"), w("black"), w("blue"), w("white"), SOUL),
        buy("cherry_blossom", "Cherry Blossom", "Pink cherrywood and blossom sails", 70,
                CHERRY, CHERRY, mc("stripped_cherry_log"), t("pink"), t("white"), mc("gold_block"), w("pink"), CANVAS, w("magenta"), LANTERN),
        buy("frostbite", "Frostbite", "Pale birch, ice-blue bands, snow-white canvas", 70,
                BIRCH, SPRUCE, mc("stripped_birch_log"), t("light_blue"), c("white"), c("light_blue"), w("white"), w("light_blue"), w("cyan"), SOUL),
        buy("desert_sun", "Desert Sun", "Sun-bleached acacia, sails like a sunset", 60,
                ACACIA, ACACIA, mc("stripped_acacia_log"), t("orange"), t("yellow"), mc("gold_block"), w("orange"), w("red"), w("yellow"), LANTERN),
        buy("coral_reef", "Coral Reef", "Mangrove red, coral pinks and lagoon blues", 70,
                MANGROVE, MANGROVE, mc("stripped_mangrove_log"), t("cyan"), t("pink"), c("orange"), w("light_blue"), w("pink"), w("orange"), LANTERN),
        buy("jade_dragon", "Jade Dragon", "Lacquered jungle wood, jade and gold, red sails", 90,
                JUNGLE, JUNGLE, mc("stripped_jungle_log"), t("green"), c("red"), mc("gold_block"), w("red"), w("green"), w("yellow"), LANTERN),
        buy("bumblebee", "Bumblebee", "Honey-gold and black stripes - loud and proud", 60,
                OAK, SPRUCE, mc("stripped_oak_log"), c("yellow"), c("black"), c("yellow"), w("yellow"), w("black"), w("black"), LANTERN),
        buy("storm_petrel", "Storm Petrel", "Slate-grey hull and storm-cloud sails", 60,
                SPRUCE, SPRUCE, mc("stripped_spruce_log"), c("gray"), c("light_gray"), c("cyan"), w("light_gray"), w("gray"), w("cyan"), LANTERN),
        // ---- earned only
        special("drowned_fleet", "Drowned Fleet", "Ghostwood and spectral sails, soul fire in the lanterns", "boss:ghost_captain",
                GHOST, DARK_OAK, "pixelpirates:ghostwood_log", mc("dark_prismarine"), mc("prismarine"), mc("sea_lantern"), SPECTRAL, w("gray"), SPECTRAL, SOUL),
        special("rackhams_red", "Rackham's Red", "The colours Captain Rackham flew", "boss:captain_rackham",
                SPRUCE, SPRUCE, mc("stripped_spruce_log"), t("red"), t("black"), mc("gold_block"), CRIMSON_C, BLACK, ROGER, LANTERN),
        special("serpent_scale", "Serpent Scale", "Green as the Sea Serpent's hide", "boss:sea_serpent",
                WARPED, WARPED, mc("stripped_warped_stem"), c("green"), t("lime"), c("lime"), w("green"), w("lime"), w("yellow"), LANTERN),
        special("molten_forge", "Molten Forge", "Black iron and forge-fire, from the Warlord's cinders", "boss:molten_warlord",
                CRIMSON, CRIMSON, mc("stripped_crimson_stem"), mc("blackstone"), mc("magma_block"), mc("shroomlight"), w("black"), w("orange"), w("red"), LANTERN),
        special("tide_king", "Tide King", "Royal blue and gold of the Abyssal King", "boss:abyssal_king",
                DARK_OAK, BIRCH, mc("stripped_dark_oak_log"), c("blue"), c("yellow"), mc("gold_block"), w("blue"), w("yellow"), w("yellow"), SOUL),
        special("bloodfin", "Bloodfin", "Red water and white teeth", "boss:bloodfin",
                MANGROVE, MANGROVE, mc("stripped_mangrove_log"), c("red"), c("white"), c("red"), w("red"), w("light_gray"), w("black"), LANTERN),
        special("kraken_ink", "Kraken's Ink", "Inky purple, black-dipped sails", "boss:kraken",
                CRIMSON, DARK_OAK, mc("stripped_crimson_stem"), t("purple"), c("black"), c("purple"), w("purple"), w("black"), w("magenta"), SOUL),
        special("bone_reaper", "Bone Reaper", "Bone planks, blood-red sails - the Revenant's chains broken", "boss:chained_revenant",
                BONE, BONE, mc("bone_block"), mc("red_nether_bricks"), c("black"), mc("bone_block"), w("red"), w("black"), ROGER, SOUL),
        special("abyssal_heart", "Heart of the Abyss", "Deep violet and glowing cyan", "boss:abyssal_heart",
                WARPED, DARK_OAK, mc("stripped_warped_stem"), c("purple"), c("cyan"), mc("sea_lantern"), w("purple"), w("cyan"), w("cyan"), SOUL),
        special("leviathan", "Leviathan", "Sea-green scales and the Crown of the Drowned", "boss:leviathan",
                WARPED, WARPED, mc("stripped_warped_stem"), mc("prismarine_bricks"), mc("dark_prismarine"), mc("gold_block"), w("cyan"), w("light_blue"), w("lime"), SOUL),
        special("great_white", "Great White", "Shark-grey above, white below, a red grin", "sharks:20",
                SPRUCE, BIRCH, mc("stripped_spruce_log"), c("gray"), c("white"), c("red"), w("light_gray"), w("gray"), w("red"), LANTERN),
        special("parrot_plumage", "Parrot Plumage", "Every colour of the aviary", "parrots:8",
                JUNGLE, JUNGLE, mc("stripped_jungle_log"), c("lime"), c("red"), c("yellow"), w("blue"), w("yellow"), w("red"), LANTERN));

    @Nullable
    public static Livery byId(String id) {
        for (Livery l : ALL) if (l.id.equals(id)) return l;
        return null;
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).id.equals(id)) return i;
        return -1;
    }

    /** What the player must do for a SPECIAL livery (or the standing a FACTION one needs), for the screen. */
    public String unlockText() {
        if (unlock == null) return "";
        if (kind == Kind.FACTION) return "Friendly with the " + switch (unlock) { case "pirates" -> "Corsairs"; case "merchants" -> "Company"; case "navy" -> "Armada"; default -> unlock; };
        String[] p = unlock.split(":");
        return switch (p[0]) {
            case "boss" -> "Beat " + switch (p[1]) {
                case "captain_rackham" -> "Captain Rackham"; case "sea_serpent" -> "the Sea Serpent"; case "molten_warlord" -> "the Molten Warlord";
                case "ghost_captain" -> "the Ghost Captain"; case "abyssal_king" -> "the Abyssal King"; case "bloodfin" -> "the Bloodfin";
                case "kraken" -> "the Kraken"; case "chained_revenant" -> "the Chained Revenant"; case "abyssal_heart" -> "the Abyssal Heart";
                case "leviathan" -> "the Leviathan"; default -> p[1]; };
            case "sharks" -> "Kill " + p[1] + " sharks";
            case "parrots" -> "Collect " + p[1] + " parrot types";
            default -> unlock;
        };
    }
}
