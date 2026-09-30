package net.get900.pixelpirates.homestead.datagen;

import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.item.Items;
import net.minecraft.recipe.book.RecipeCategory;

import java.util.function.Consumer;

/** Recipes for the later homestead features (kept apart so each feature adds a block of recipes here). */
final class HomesteadRecipes {
    private HomesteadRecipes() {}

    /** Furnace + smoker + campfire. */
    static void cookAll(Consumer<RecipeJsonProvider> ex, net.minecraft.item.ItemConvertible in, net.minecraft.item.ItemConvertible out, String name) {
        var ing = net.minecraft.recipe.Ingredient.ofItems(in);
        net.minecraft.data.server.recipe.CookingRecipeJsonBuilder.createSmelting(ing, RecipeCategory.FOOD, out, 0.35f, 200)
                .criterion(HomesteadRecipeProvider.hasName(in), HomesteadRecipeProvider.has(in)).offerTo(ex, HomesteadRecipeProvider.id(name + "_smelting"));
        net.minecraft.data.server.recipe.CookingRecipeJsonBuilder.createSmoking(ing, RecipeCategory.FOOD, out, 0.35f, 100)
                .criterion(HomesteadRecipeProvider.hasName(in), HomesteadRecipeProvider.has(in)).offerTo(ex, HomesteadRecipeProvider.id(name + "_smoking"));
        net.minecraft.data.server.recipe.CookingRecipeJsonBuilder.createCampfireCooking(ing, RecipeCategory.FOOD, out, 0.35f, 600)
                .criterion(HomesteadRecipeProvider.hasName(in), HomesteadRecipeProvider.has(in)).offerTo(ex, HomesteadRecipeProvider.id(name + "_campfire"));
    }

    /** Shaped recipe: pattern rows, then (char, ItemConvertible) pairs; unlocked by the first key item. */
    static void shaped(Consumer<RecipeJsonProvider> ex, net.minecraft.item.ItemConvertible out, int count, String[] rows, Object... keys) {
        ShapedRecipeJsonBuilder b = ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, out, count);
        for (String r : rows) b.pattern(r);
        for (int i = 0; i < keys.length; i += 2) b.input((Character) keys[i], (net.minecraft.item.ItemConvertible) keys[i + 1]);
        net.minecraft.item.ItemConvertible first = (net.minecraft.item.ItemConvertible) keys[1];
        b.criterion(HomesteadRecipeProvider.hasName(first), HomesteadRecipeProvider.has(first));
        b.offerTo(ex, HomesteadRecipeProvider.id(net.minecraft.registry.Registries.ITEM.getId(out.asItem()).getPath()));
    }

    static void more(HomesteadRecipeProvider p, Consumer<RecipeJsonProvider> ex) {
        // ---------------- #12 rum
        p.shapeless(RecipeCategory.FOOD, HomesteadItems.MOLASSES, 1, Items.SUGAR_CANE, Items.SUGAR_CANE, Items.SUGAR_CANE, Items.GLASS_BOTTLE)
                .offerTo(ex, HomesteadRecipeProvider.id("molasses"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, HomesteadBlocks.RUM_STILL)
                .pattern(" C ").pattern("CBC").pattern("I I")
                .input('C', Items.COPPER_INGOT).input('B', Items.BUCKET).input('I', Items.IRON_INGOT)
                .criterion(HomesteadRecipeProvider.hasName(Items.COPPER_INGOT), HomesteadRecipeProvider.has(Items.COPPER_INGOT))
                .offerTo(ex, HomesteadRecipeProvider.id("rum_still"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, HomesteadBlocks.AGING_CASK)
                .pattern("I").pattern("B").pattern("S")
                .input('I', Items.IRON_INGOT).input('B', Items.BARREL).input('S', Items.DARK_OAK_SLAB)
                .criterion(HomesteadRecipeProvider.hasName(Items.BARREL), HomesteadRecipeProvider.has(Items.BARREL))
                .offerTo(ex, HomesteadRecipeProvider.id("aging_cask"));

        // ---------------- #5 building
        net.minecraft.item.ItemConvertible PLANKS = net.get900.pixelpirates.block.ModBlocks.PALM_PLANKS;
        net.minecraft.recipe.Ingredient planks = net.minecraft.recipe.Ingredient.ofItems(PLANKS);
        net.minecraft.data.server.recipe.RecipeProvider.createStairsRecipe(HomesteadBlocks.PALM_STAIRS, planks)
                .criterion(HomesteadRecipeProvider.hasName(PLANKS), HomesteadRecipeProvider.has(PLANKS)).offerTo(ex, HomesteadRecipeProvider.id("palm_stairs"));
        net.minecraft.data.server.recipe.RecipeProvider.createSlabRecipe(RecipeCategory.BUILDING_BLOCKS, HomesteadBlocks.PALM_SLAB, planks)
                .criterion(HomesteadRecipeProvider.hasName(PLANKS), HomesteadRecipeProvider.has(PLANKS)).offerTo(ex, HomesteadRecipeProvider.id("palm_slab"));
        net.minecraft.data.server.recipe.RecipeProvider.createFenceRecipe(HomesteadBlocks.PALM_FENCE, planks)
                .criterion(HomesteadRecipeProvider.hasName(PLANKS), HomesteadRecipeProvider.has(PLANKS)).offerTo(ex, HomesteadRecipeProvider.id("palm_fence"));
        net.minecraft.data.server.recipe.RecipeProvider.createFenceGateRecipe(HomesteadBlocks.PALM_FENCE_GATE, planks)
                .criterion(HomesteadRecipeProvider.hasName(PLANKS), HomesteadRecipeProvider.has(PLANKS)).offerTo(ex, HomesteadRecipeProvider.id("palm_fence_gate"));
        net.minecraft.data.server.recipe.RecipeProvider.createDoorRecipe(HomesteadBlocks.PALM_DOOR, planks)
                .criterion(HomesteadRecipeProvider.hasName(PLANKS), HomesteadRecipeProvider.has(PLANKS)).offerTo(ex, HomesteadRecipeProvider.id("palm_door"));
        net.minecraft.data.server.recipe.RecipeProvider.createTrapdoorRecipe(HomesteadBlocks.PALM_TRAPDOOR, planks)
                .criterion(HomesteadRecipeProvider.hasName(PLANKS), HomesteadRecipeProvider.has(PLANKS)).offerTo(ex, HomesteadRecipeProvider.id("palm_trapdoor"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, HomesteadBlocks.THATCH, 2).pattern("WW").pattern("WW").input('W', Items.WHEAT)
                .criterion(HomesteadRecipeProvider.hasName(Items.WHEAT), HomesteadRecipeProvider.has(Items.WHEAT)).offerTo(ex, HomesteadRecipeProvider.id("thatch"));
        net.minecraft.item.ItemConvertible PL = net.get900.pixelpirates.block.ModBlocks.PALM_LEAVES;
        ShapedRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, HomesteadBlocks.THATCH, 2).pattern("LL").pattern("LL").input('L', PL)
                .criterion(HomesteadRecipeProvider.hasName(PL), HomesteadRecipeProvider.has(PL)).offerTo(ex, HomesteadRecipeProvider.id("thatch_from_palm_leaves"));
        net.minecraft.recipe.Ingredient th = net.minecraft.recipe.Ingredient.ofItems(HomesteadBlocks.THATCH);
        net.minecraft.data.server.recipe.RecipeProvider.createStairsRecipe(HomesteadBlocks.THATCH_STAIRS, th)
                .criterion(HomesteadRecipeProvider.hasName(HomesteadBlocks.THATCH), HomesteadRecipeProvider.has(HomesteadBlocks.THATCH)).offerTo(ex, HomesteadRecipeProvider.id("thatch_stairs"));
        net.minecraft.data.server.recipe.RecipeProvider.createSlabRecipe(RecipeCategory.BUILDING_BLOCKS, HomesteadBlocks.THATCH_SLAB, th)
                .criterion(HomesteadRecipeProvider.hasName(HomesteadBlocks.THATCH), HomesteadRecipeProvider.has(HomesteadBlocks.THATCH)).offerTo(ex, HomesteadRecipeProvider.id("thatch_slab"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, HomesteadBlocks.WOVEN_PALM_SCREEN, 8).pattern("TST").pattern("TST")
                .input('T', HomesteadBlocks.THATCH).input('S', Items.STICK)
                .criterion(HomesteadRecipeProvider.hasName(HomesteadBlocks.THATCH), HomesteadRecipeProvider.has(HomesteadBlocks.THATCH)).offerTo(ex, HomesteadRecipeProvider.id("woven_palm_screen"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, HomesteadBlocks.WOVEN_MAT, 3).pattern("TT").input('T', HomesteadBlocks.THATCH)
                .criterion(HomesteadRecipeProvider.hasName(HomesteadBlocks.THATCH), HomesteadRecipeProvider.has(HomesteadBlocks.THATCH)).offerTo(ex, HomesteadRecipeProvider.id("woven_mat"));
        net.minecraft.item.ItemConvertible DW = net.get900.pixelpirates.item.ModItems.DRIFTWOOD;
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, HomesteadBlocks.DRIFTWOOD_FENCE, 3).pattern("DSD").pattern("DSD")
                .input('D', DW).input('S', Items.STICK).criterion(HomesteadRecipeProvider.hasName(DW), HomesteadRecipeProvider.has(DW)).offerTo(ex, HomesteadRecipeProvider.id("driftwood_fence"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.REDSTONE, HomesteadBlocks.DRIFTWOOD_FENCE_GATE).pattern("SDS").pattern("SDS")
                .input('D', DW).input('S', Items.STICK).criterion(HomesteadRecipeProvider.hasName(DW), HomesteadRecipeProvider.has(DW)).offerTo(ex, HomesteadRecipeProvider.id("driftwood_fence_gate"));
        net.minecraft.item.ItemConvertible ROPE = net.get900.pixelpirates.item.ModItems.ROPE;
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, HomesteadBlocks.ROPE_LADDER, 4).pattern("R R").pattern("RSR").pattern("R R")
                .input('R', ROPE).input('S', Items.STICK).criterion(HomesteadRecipeProvider.hasName(ROPE), HomesteadRecipeProvider.has(ROPE)).offerTo(ex, HomesteadRecipeProvider.id("rope_ladder"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, HomesteadBlocks.ROPE_BRIDGE, 4).pattern("R R").pattern("PPP")
                .input('R', ROPE).input('P', net.minecraft.registry.tag.ItemTags.PLANKS).criterion(HomesteadRecipeProvider.hasName(ROPE), HomesteadRecipeProvider.has(ROPE)).offerTo(ex, HomesteadRecipeProvider.id("rope_bridge"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, HomesteadBlocks.TIKI_TORCH, 2).pattern("C").pattern("T").pattern("S")
                .input('C', net.minecraft.registry.tag.ItemTags.COALS).input('T', HomesteadBlocks.THATCH).input('S', Items.STICK)
                .criterion(HomesteadRecipeProvider.hasName(HomesteadBlocks.THATCH), HomesteadRecipeProvider.has(HomesteadBlocks.THATCH)).offerTo(ex, HomesteadRecipeProvider.id("tiki_torch"));

        // ---------------- #2 furniture
        shaped(ex, HomesteadBlocks.CAPTAINS_DESK, 1, new String[]{"SSS", "P P", "P P"}, 'S', Items.DARK_OAK_SLAB, 'P', Items.DARK_OAK_PLANKS);
        shaped(ex, HomesteadBlocks.SEA_CHEST, 1, new String[]{"IGI", "PCP", "IPI"}, 'I', Items.IRON_INGOT, 'G', Items.GOLD_INGOT, 'P', Items.DARK_OAK_PLANKS, 'C', Items.CHEST);
        shaped(ex, HomesteadBlocks.CARGO_CRATE, 2, new String[]{"SPS", "P P", "SPS"}, 'S', Items.STICK, 'P', Items.SPRUCE_PLANKS);
        shaped(ex, HomesteadBlocks.TREASURE_PEDESTAL, 1, new String[]{" W ", "GBG", "BBB"}, 'W', Items.RED_WOOL, 'G', Items.GOLD_INGOT, 'B', Items.POLISHED_BLACKSTONE);
        shaped(ex, HomesteadBlocks.RUM_RACK, 1, new String[]{"PPP", "S S", "PPP"}, 'P', Items.DARK_OAK_PLANKS, 'S', Items.DARK_OAK_SLAB);
        shaped(ex, HomesteadBlocks.HANGING_NET, 2, new String[]{"SSS", "SSS"}, 'S', Items.STRING);
        shaped(ex, HomesteadBlocks.ROPE_COIL, 1, new String[]{"RR", "RR"}, 'R', net.get900.pixelpirates.item.ModItems.ROPE);
        shaped(ex, HomesteadBlocks.HANGING_ROPE, 4, new String[]{"R", "R"}, 'R', net.get900.pixelpirates.item.ModItems.ROPE);
        shaped(ex, HomesteadBlocks.SHIPS_WHEEL, 1, new String[]{"PSP", "SGS", "PSP"}, 'P', Items.DARK_OAK_PLANKS, 'S', Items.STICK, 'G', Items.GOLD_NUGGET);
        shaped(ex, HomesteadBlocks.DISPLAY_CANNON, 1, new String[]{"III", "ICI", "P P"}, 'I', Items.IRON_INGOT, 'C', net.get900.pixelpirates.item.ModItems.CANNON_BALL, 'P', Items.DARK_OAK_PLANKS);
        shaped(ex, HomesteadBlocks.MAP_TABLE, 1, new String[]{"CM", "SS", "LL"}, 'C', Items.COMPASS, 'M', Items.PAPER, 'S', Items.DARK_OAK_SLAB, 'L', Items.DARK_OAK_LOG);
        shaped(ex, HomesteadBlocks.CAPTAINS_CHAIR, 1, new String[]{"PG ", "PWP", "L L"}, 'P', Items.DARK_OAK_PLANKS, 'G', Items.GOLD_INGOT, 'W', Items.RED_WOOL, 'L', Items.STICK);
        shaped(ex, HomesteadBlocks.BARREL_STOOL, 1, new String[]{"W", "B"}, 'W', Items.RED_WOOL, 'B', Items.BARREL);

        // ---------------- #21 guns
        p.shapeless(RecipeCategory.COMBAT, HomesteadItems.PAPER_CARTRIDGE, 4, Items.GUNPOWDER, Items.PAPER, Items.IRON_NUGGET)
                .offerTo(ex, HomesteadRecipeProvider.id("paper_cartridge"));
        p.shapeless(RecipeCategory.COMBAT, HomesteadItems.SCATTERSHOT, 4, Items.GUNPOWDER, Items.PAPER, Items.FLINT, Items.IRON_NUGGET, Items.IRON_NUGGET)
                .offerTo(ex, HomesteadRecipeProvider.id("scattershot"));
        shaped(ex, HomesteadItems.FLINTLOCK_PISTOL, 1, new String[]{"IIF", "  P"}, 'I', Items.IRON_INGOT, 'F', Items.FLINT_AND_STEEL, 'P', Items.DARK_OAK_PLANKS);
        shaped(ex, HomesteadItems.BLUNDERBUSS, 1, new String[]{"CCF", " IP", "  P"}, 'C', Items.COPPER_INGOT, 'F', Items.FLINT_AND_STEEL,
                'I', Items.IRON_INGOT, 'P', Items.DARK_OAK_PLANKS);

        // ---------------- #3 hoard
        shaped(ex, HomesteadBlocks.TREASURE_HOARD, 1, new String[]{"CGC", "CCC"}, 'C', net.get900.pixelpirates.item.ModItems.PIRATE_COIN, 'G', Items.GOLD_INGOT);

        // ---------------- #13 fishing
        cookAll(ex, HomesteadItems.PARROTFISH, HomesteadItems.COOKED_FISH_FILLET, "parrotfish");
        cookAll(ex, HomesteadItems.RED_SNAPPER, HomesteadItems.COOKED_FISH_FILLET, "red_snapper");
        cookAll(ex, HomesteadItems.MAHI_MAHI, HomesteadItems.COOKED_FISH_FILLET, "mahi_mahi");
        cookAll(ex, HomesteadItems.LIONFISH, HomesteadItems.COOKED_FISH_FILLET, "lionfish");
        cookAll(ex, HomesteadItems.MOONFISH, HomesteadItems.COOKED_FISH_FILLET, "moonfish");
        cookAll(ex, HomesteadItems.EMBERFIN, HomesteadItems.COOKED_FISH_FILLET, "emberfin");
        cookAll(ex, HomesteadItems.LAVA_EEL, HomesteadItems.COOKED_FISH_FILLET, "lava_eel");
        cookAll(ex, HomesteadItems.GHOSTFIN, HomesteadItems.COOKED_FISH_FILLET, "ghostfin");
        cookAll(ex, HomesteadItems.BONEFISH, HomesteadItems.COOKED_FISH_FILLET, "bonefish");
        cookAll(ex, HomesteadItems.ANGLERFRY, HomesteadItems.COOKED_FISH_FILLET, "anglerfry");
        cookAll(ex, HomesteadItems.VOIDFIN, HomesteadItems.COOKED_FISH_FILLET, "voidfin");
        cookAll(ex, HomesteadItems.LOBSTER, HomesteadItems.COOKED_LOBSTER, "lobster");
        shaped(ex, HomesteadBlocks.FISH_TRAP, 1, new String[]{"SSS", "S S", "SSS"}, 'S', Items.STRING);
        shaped(ex, HomesteadBlocks.LOBSTER_POT, 1, new String[]{"PSP", "S S", "PSP"}, 'P', Items.STICK, 'S', Items.STRING);
        shaped(ex, HomesteadBlocks.BOUNTY_BOARD, 1, new String[]{"PPP", "LPL", "S S"}, 'P', Items.PAPER, 'L', Items.SPRUCE_PLANKS, 'S', Items.STICK);
        shaped(ex, HomesteadItems.CAPTAINS_SPYGLASS, 1, new String[]{" G ", "GSG", " L "}, 'G', Items.GOLD_INGOT, 'S', Items.SPYGLASS, 'L', Items.LEATHER);
        shaped(ex, HomesteadItems.COMPASS_OF_DESIRE, 1, new String[]{"GEG", "RCR", "GAG"}, 'G', Items.GOLD_NUGGET, 'E', Items.ENDER_EYE, 'R', Items.REDSTONE, 'C', Items.COMPASS, 'A', Items.AMETHYST_SHARD);
        shaped(ex, HomesteadBlocks.JOLLY_ROGER, 1, new String[]{"SBB", "SBB", "S  "}, 'S', Items.STICK, 'B', Items.BLACK_WOOL);
        shaped(ex, HomesteadBlocks.MOORING_POST, 1, new String[]{" I ", "LSL", " L "}, 'I', Items.IRON_INGOT, 'L', Items.SPRUCE_LOG, 'S', Items.STRING);
        shaped(ex, HomesteadItems.GRAPPLING_HOOK, 1, new String[]{"I I", " I ", " R "}, 'I', Items.IRON_INGOT, 'R', net.get900.pixelpirates.item.ModItems.ROPE);
        shaped(ex, HomesteadItems.CHAIN_SHOT, 2, new String[]{"BCB"}, 'B', net.get900.pixelpirates.item.ModItems.CANNON_BALL, 'C', Items.CHAIN);
        shaped(ex, HomesteadItems.GRAPE_SHOT, 2, new String[]{"NNN", "NGN", "NLN"}, 'N', Items.IRON_NUGGET, 'G', Items.GUNPOWDER, 'L', Items.LEATHER);
        shaped(ex, HomesteadBlocks.MERMAID_FIGUREHEAD, 1, new String[]{" P ", "LHL", " L "}, 'P', Items.PRISMARINE_SHARD, 'H', Items.HEART_OF_THE_SEA, 'L', Items.SPRUCE_LOG);
        shaped(ex, HomesteadBlocks.KRAKEN_FIGUREHEAD, 1, new String[]{" I ", "LSL", " L "}, 'I', net.get900.pixelpirates.item.ModItems.KRAKEN_INK, 'S', net.get900.pixelpirates.item.ModItems.KRAKEN_SCALE, 'L', Items.SPRUCE_LOG);
        shaped(ex, HomesteadBlocks.DREAD_SKULL_FIGUREHEAD, 1, new String[]{" K ", "LBL", " L "}, 'K', Items.SKELETON_SKULL, 'B', net.get900.pixelpirates.item.ModItems.CURSED_BONE, 'L', Items.DARK_OAK_LOG);
        shaped(ex, HomesteadBlocks.NAVY_EAGLE_FIGUREHEAD, 1, new String[]{" F ", "LGL", " L "}, 'F', Items.FEATHER, 'G', Items.GOLD_INGOT, 'L', Items.SPRUCE_LOG);
        shaped(ex, HomesteadBlocks.WHITE_SAIL_CANVAS, 4, new String[]{"WW", "WW"}, 'W', Items.WHITE_WOOL);
        shaped(ex, HomesteadBlocks.BLACK_SAIL_CANVAS, 4, new String[]{"WW", "WW"}, 'W', Items.BLACK_WOOL);
        shaped(ex, HomesteadBlocks.CRIMSON_SAIL_CANVAS, 4, new String[]{"WW", "WW"}, 'W', Items.RED_WOOL);
        shaped(ex, HomesteadBlocks.STRIPED_SAIL_CANVAS, 4, new String[]{"WR", "RW"}, 'W', Items.WHITE_WOOL, 'R', Items.RED_WOOL);
        shaped(ex, HomesteadBlocks.JOLLY_ROGER_SAIL_CANVAS, 4, new String[]{"BB", "BK"}, 'B', Items.BLACK_WOOL, 'K', Items.BONE);
        shaped(ex, HomesteadItems.CAPTAINS_LOGBOOK, 1, new String[]{" F ", "IBC"}, 'F', Items.FEATHER, 'I', Items.INK_SAC, 'B', Items.BOOK, 'C', Items.COMPASS);
        shaped(ex, HomesteadBlocks.ROULETTE_TABLE, 1, new String[]{"GCW", "PPP", "S S"}, 'G', Items.GOLD_INGOT, 'C', Items.COMPASS, 'W', Items.GREEN_CARPET,
                'P', Items.DARK_OAK_PLANKS, 'S', Items.STICK);
        shaped(ex, HomesteadBlocks.TRADING_POST, 1, new String[]{"GCB", "SSS", "P P"}, 'G', Items.GOLD_INGOT, 'C', net.get900.pixelpirates.item.ModItems.PIRATE_COIN, 'B', Items.BOOK, 'S', Items.SPRUCE_SLAB, 'P', Items.SPRUCE_PLANKS);
        shaped(ex, HomesteadItems.SALVAGE_HOOK, 1, new String[]{"  S", " SL", "S I"}, 'S', Items.STICK, 'L', Items.STRING, 'I', Items.IRON_INGOT);

        // ---------------- #24 region tools
        shaped(ex, HomesteadItems.EMBER_PICKAXE, 1, new String[]{"MMM", " S ", " S "}, 'M', net.get900.pixelpirates.item.ModItems.VOLCANIC_EMBER, 'S', Items.STICK);
        shaped(ex, HomesteadItems.EMBER_AXE, 1, new String[]{"MM", "MS", " S"}, 'M', net.get900.pixelpirates.item.ModItems.VOLCANIC_EMBER, 'S', Items.STICK);
        shaped(ex, HomesteadItems.EMBER_SHOVEL, 1, new String[]{"M", "S", "S"}, 'M', net.get900.pixelpirates.item.ModItems.VOLCANIC_EMBER, 'S', Items.STICK);
        shaped(ex, HomesteadItems.EMBER_HOE, 1, new String[]{"MM", " S", " S"}, 'M', net.get900.pixelpirates.item.ModItems.VOLCANIC_EMBER, 'S', Items.STICK);
        shaped(ex, HomesteadItems.KRAKEN_PICKAXE, 1, new String[]{"MMM", " S ", " S "}, 'M', net.get900.pixelpirates.item.ModItems.KRAKEN_SCALE, 'S', Items.STICK);
        shaped(ex, HomesteadItems.KRAKEN_AXE, 1, new String[]{"MM", "MS", " S"}, 'M', net.get900.pixelpirates.item.ModItems.KRAKEN_SCALE, 'S', Items.STICK);
        shaped(ex, HomesteadItems.KRAKEN_SHOVEL, 1, new String[]{"M", "S", "S"}, 'M', net.get900.pixelpirates.item.ModItems.KRAKEN_SCALE, 'S', Items.STICK);
        shaped(ex, HomesteadItems.KRAKEN_HOE, 1, new String[]{"MM", " S", " S"}, 'M', net.get900.pixelpirates.item.ModItems.KRAKEN_SCALE, 'S', Items.STICK);
        shaped(ex, HomesteadItems.BONE_PICKAXE, 1, new String[]{"MMM", " S ", " S "}, 'M', net.get900.pixelpirates.item.ModItems.CURSED_BONE, 'S', Items.STICK);
        shaped(ex, HomesteadItems.BONE_AXE, 1, new String[]{"MM", "MS", " S"}, 'M', net.get900.pixelpirates.item.ModItems.CURSED_BONE, 'S', Items.STICK);
        shaped(ex, HomesteadItems.BONE_SHOVEL, 1, new String[]{"M", "S", "S"}, 'M', net.get900.pixelpirates.item.ModItems.CURSED_BONE, 'S', Items.STICK);
        shaped(ex, HomesteadItems.BONE_HOE, 1, new String[]{"MM", " S", " S"}, 'M', net.get900.pixelpirates.item.ModItems.CURSED_BONE, 'S', Items.STICK);
    }
}
