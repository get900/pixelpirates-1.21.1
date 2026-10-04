package net.get900.pixelpirates.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.data.server.recipe.RecipeJsonProvider;
import net.minecraft.data.server.recipe.ShapedRecipeJsonBuilder;
import net.minecraft.data.server.recipe.ShapelessRecipeJsonBuilder;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.book.RecipeCategory;
import net.minecraft.registry.tag.ItemTags;

import java.util.List;
import java.util.function.Consumer;

public class ModRecipeProvider extends FabricRecipeProvider {
    public ModRecipeProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generate(Consumer<RecipeJsonProvider> exporter) {
        // RELIC WEAPONS: each boss relic is forged into that boss's weapon (item/RelicWeapons)
        relicWeapon(exporter, ModItems.RACKHAM_BLUNDERBUSS, ModItems.RACKHAMS_DIVING_CHARM, b -> b
                .input(Items.IRON_INGOT, 3).input(Ingredient.fromTag(ItemTags.PLANKS), 2).input(Items.FLINT_AND_STEEL));
        relicWeapon(exporter, ModItems.SERPENTSPINE_LONGBOW, ModItems.SERPENTS_TIDE_PEARL, b -> b
                .input(Items.BOW).input(Items.PRISMARINE_SHARD, 2).input(Items.STRING));
        relicWeapon(exporter, ModItems.EVERBURNING_FLAIL, ModItems.EVERBURNING_LANTERN, b -> b
                .input(ModItems.VOLCANIC_EMBER, 2).input(Items.CHAIN).input(ModItems.OBSIDIAN_SHARD, 2));
        relicWeapon(exporter, ModItems.DUTCHMANS_HAND_CANNON, ModItems.SPECTRAL_ANCHOR, b -> b
                .input(ModBlocks.GHOSTWOOD_PLANKS, 2).input(Items.IRON_BLOCK).input(Items.GUNPOWDER, 2));
        relicWeapon(exporter, ModItems.SUNKEN_TRIDENT, ModItems.ROYAL_TIDE_SIGIL, b -> b
                .input(ModItems.ABYSSAL_HARPOON).input(Items.GOLD_INGOT, 2).input(Items.PRISMARINE_CRYSTALS));
        relicWeapon(exporter, ModItems.BLOODFIN_MAW, ModItems.BLOODFIN_RAZOR_TOOTH, b -> b
                .input(ModItems.BLOODFIN_FLESH, 3).input(ModItems.ABYSSAL_PEARL, 2));
        relicWeapon(exporter, ModItems.KRAKENMAW_HARPOON_GUN, ModItems.KRAKENS_INK_HEART, b -> b
                .input(ModItems.KRAKEN_SCALE, 2).input(Items.CROSSBOW).input(ModItems.HARPOON, 2));
        relicWeapon(exporter, ModItems.CHAINBREAKER, ModItems.BROKEN_SHACKLE, b -> b
                .input(Items.CHAIN, 3).input(ModItems.CURSED_BONE, 2).input(ModItems.LOST_SOUL));
        relicWeapon(exporter, ModItems.HEARTSEEKER, ModItems.ABYSSAL_HEARTSTONE, b -> b
                .input(ModItems.KRAKEN_FANG).input(ModBlocks.LIVING_FLESH, 2).input(Items.HEART_OF_THE_SEA));      // forged from your old blade
        relicWeapon(exporter, ModItems.TIDEFATHERS_WRATH, ModItems.CROWN_OF_THE_DROWNED, b -> b
                .input(ModItems.STORMCALLER).input(ModItems.LEVIATHAN_SCALE, 4).input(ModItems.TIDAL_CORE));

        // Dynamite is an early-game key (blast rubble seals structure vaults): powder from pirate crew
        // and raft pirates, paper and string from any island.
        ShapelessRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.DYNAMITE, 2)
                .input(Items.GUNPOWDER).input(Items.PAPER).input(Items.STRING)
                .criterion(hasItem(Items.GUNPOWDER), conditionsFromItem(Items.GUNPOWDER))
                .offerTo(exporter, new net.minecraft.util.Identifier(net.get900.pixelpirates.PixelPirates.MOD_ID, "dynamite"));
        // SHIP & SEAFARING basics (2026-10-01): these lived in resources/data/pixelpirates/recipe/ - the 1.21 folder name
        // and result format, which 1.20.1 never loads - so none of them worked in game. Sails take cloth or wool (no sheep
        // spawn in our seas); the repair kit is driftwood's main use.
        String mod = net.get900.pixelpirates.PixelPirates.MOD_ID;
        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.ROPE)
                .pattern("K").pattern("K").pattern("K").input('K', Items.KELP)
                .criterion(hasItem(Items.KELP), conditionsFromItem(Items.KELP)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "rope"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.SAIL)
                .pattern("RRR").pattern("CCC").pattern("CCC").input('R', ModItems.ROPE)
                .input('C', Ingredient.ofItems(ModItems.TATTERED_CLOTH, Items.WHITE_WOOL))
                .criterion(hasItem(ModItems.ROPE), conditionsFromItem(ModItems.ROPE)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "sail"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.MAST)
                .pattern("RPR").pattern(" P ").pattern(" P ").input('R', ModItems.ROPE).input('P', ItemTags.PLANKS)
                .criterion(hasItem(ModItems.ROPE), conditionsFromItem(ModItems.ROPE)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "mast"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.MAST_WITH_SAILS)
                .pattern("SMS").pattern("SMS").pattern(" M ").input('S', ModItems.SAIL).input('M', ModItems.MAST)
                .criterion(hasItem(ModItems.SAIL), conditionsFromItem(ModItems.SAIL)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "mast_with_sails"));
        // Wreckers' Beacon props
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, ModBlocks.ROPE_BOLLARD, 2)
                .pattern(" R ").pattern("RFR").pattern(" P ").input('R', ModItems.ROPE).input('F', Items.SPRUCE_FENCE).input('P', Items.SPRUCE_PLANKS)
                .criterion(hasItem(ModItems.ROPE), conditionsFromItem(ModItems.ROPE)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "rope_bollard"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, ModBlocks.WRECKERS_SIGNAL_LANTERN)
                .pattern("CCC").pattern("GLG").pattern("CCC").input('C', Items.COPPER_INGOT).input('G', Items.GLASS_PANE).input('L', Items.LANTERN)
                .criterion(hasItem(Items.LANTERN), conditionsFromItem(Items.LANTERN)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "wreckers_signal_lantern"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.CANNON_BALL, 4)
                .pattern(" C ").pattern("CIC").pattern(" C ").input('C', Items.COBBLESTONE).input('I', Items.IRON_INGOT)
                .criterion(hasItem(Items.COBBLESTONE), conditionsFromItem(Items.COBBLESTONE)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "cannon_ball"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.CUTLASS)
                .pattern(" I").pattern("GI").pattern("S ").input('I', Items.IRON_INGOT).input('G', Items.GOLD_INGOT).input('S', Items.STICK)
                .criterion(hasItem(Items.IRON_INGOT), conditionsFromItem(Items.IRON_INGOT)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "cutlass"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.DAGGER)
                .pattern("I").pattern("G").pattern("S").input('I', Items.IRON_INGOT).input('G', Items.GOLD_INGOT).input('S', Items.STICK)
                .criterion(hasItem(Items.IRON_INGOT), conditionsFromItem(Items.IRON_INGOT)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "dagger"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.FOOD, ModItems.GROG)
                .pattern("W").pattern("B").input('W', Items.WHEAT).input('B', Items.GLASS_BOTTLE)
                .criterion(hasItem(Items.WHEAT), conditionsFromItem(Items.WHEAT)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "grog"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, ModBlocks.GROG_BARREL)
                .pattern("PP ").pattern("PGP").pattern("PPP").input('P', ItemTags.PLANKS).input('G', ModItems.GROG)
                .criterion(hasItem(ModItems.GROG), conditionsFromItem(ModItems.GROG)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "grog_barrel"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModBlocks.SHIP_CANNON)
                .pattern("III").pattern("III").pattern("PIP").input('I', Items.IRON_INGOT).input('P', ItemTags.PLANKS)
                .criterion(hasItem(Items.IRON_INGOT), conditionsFromItem(Items.IRON_INGOT)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "ship_cannon"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.TRANSPORTATION, ModBlocks.SHIP_HELM)
                .pattern("PIP").pattern("III").pattern("PIP").input('P', ItemTags.PLANKS).input('I', Items.IRON_INGOT)
                .criterion(hasItem(Items.IRON_INGOT), conditionsFromItem(Items.IRON_INGOT)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "ship_helm"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.TRANSPORTATION, ModBlocks.SHIP_MAST, 3)
                .pattern("RLR").pattern("RLR").pattern("RLR").input('R', ModItems.ROPE).input('L', ItemTags.LOGS)
                .criterion(hasItem(ModItems.ROPE), conditionsFromItem(ModItems.ROPE)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "ship_mast"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.TOOLS, ModItems.SHIP_REPAIR_KIT, 2)
                .pattern("DDD").pattern("RCR").pattern("DDD").input('D', ModItems.DRIFTWOOD).input('R', ModItems.ROPE).input('C', ModItems.TATTERED_CLOTH)
                .criterion(hasItem(ModItems.DRIFTWOOD), conditionsFromItem(ModItems.DRIFTWOOD)).offerTo(exporter, new net.minecraft.util.Identifier(mod, "ship_repair_kit"));
        // campfire + smoker versions (the furnace ones already exist below)
        offerFoodCookingRecipe(exporter, "campfire_cooking", net.minecraft.recipe.RecipeSerializer.CAMPFIRE_COOKING, 600, ModItems.RAW_SHARK_MEAT, ModItems.COOKED_SHARK_MEAT, 0.35f);
        offerFoodCookingRecipe(exporter, "smoking", net.minecraft.recipe.RecipeSerializer.SMOKING, 100, ModItems.RAW_SHARK_MEAT, ModItems.COOKED_SHARK_MEAT, 0.35f);
        offerFoodCookingRecipe(exporter, "campfire_cooking", net.minecraft.recipe.RecipeSerializer.CAMPFIRE_COOKING, 600, ModItems.RAW_SALTED_SWIMMER, ModItems.COOKED_SALTED_SWIMMER, 0.35f);
        offerFoodCookingRecipe(exporter, "smoking", net.minecraft.recipe.RecipeSerializer.SMOKING, 100, ModItems.RAW_SALTED_SWIMMER, ModItems.COOKED_SALTED_SWIMMER, 0.35f);

        // Bloodfin fight: harpoons for the winches, chum to bait it, the winch itself (build one on your ship)
        ShapelessRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.HARPOON, 2)
                .input(Items.IRON_INGOT).input(Items.STICK).input(Items.STICK).input(Items.STRING)
                .criterion(hasItem(Items.IRON_INGOT), conditionsFromItem(Items.IRON_INGOT))
                .offerTo(exporter, new net.minecraft.util.Identifier(net.get900.pixelpirates.PixelPirates.MOD_ID, "harpoon"));
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.CHUM, 3)
                .input(Items.ROTTEN_FLESH).input(Items.BONE_MEAL).input(Items.COD)
                .criterion(hasItem(Items.COD), conditionsFromItem(Items.COD))
                .offerTo(exporter, new net.minecraft.util.Identifier(net.get900.pixelpirates.PixelPirates.MOD_ID, "chum"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModBlocks.HARPOON_WINCH)
                .pattern("CHC").pattern("PLP").pattern("PPP")
                .input('C', Items.CHAIN).input('H', ModItems.HARPOON).input('P', ItemTags.PLANKS).input('L', Items.LEAD)
                .criterion(hasItem(ModItems.HARPOON), conditionsFromItem(ModItems.HARPOON))
                .offerTo(exporter, new net.minecraft.util.Identifier(net.get900.pixelpirates.PixelPirates.MOD_ID, "harpoon_winch"));
        // THE POISONED MEAL (Leviathan phase 2): a raft of powder barrels to float where it feeds (also in the Gullet's supply chests)
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.POWDER_BARGE)
                .pattern("GBG").pattern("GTG").pattern("SSS")
                .input('G', Items.GUNPOWDER).input('B', Items.BARREL).input('T', Items.TNT).input('S', ItemTags.WOODEN_SLABS)
                .criterion(hasItem(Items.GUNPOWDER), conditionsFromItem(Items.GUNPOWDER))
                .offerTo(exporter, new net.minecraft.util.Identifier(net.get900.pixelpirates.PixelPirates.MOD_ID, "powder_barge"));
        // Depth charges: the Abyssal King fight (also in Sunken Court armory barrels + Tide Warden drops)
        ShapelessRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.DEPTH_CHARGE, 3)
                .input(Items.GUNPOWDER).input(Items.GUNPOWDER).input(Items.PRISMARINE_CRYSTALS).input(Items.IRON_INGOT)
                .criterion(hasItem(Items.PRISMARINE_CRYSTALS), conditionsFromItem(Items.PRISMARINE_CRYSTALS))
                .offerTo(exporter, new net.minecraft.util.Identifier(net.get900.pixelpirates.PixelPirates.MOD_ID, "depth_charge"));
        List<ItemConvertible> RAW_SHARK_MEAT_SMELTABLES = List.of(ModItems.RAW_SHARK_MEAT);
        List<ItemConvertible> RAW_SALTED_SWIMMER_SMELTABLES = List.of(ModItems.RAW_SALTED_SWIMMER);

        offerSmelting(exporter, RAW_SHARK_MEAT_SMELTABLES, RecipeCategory.FOOD, ModItems.COOKED_SHARK_MEAT, 0.5f, 200, "cooked_shark_meat");
        offerSmelting(exporter, RAW_SALTED_SWIMMER_SMELTABLES, RecipeCategory.FOOD, ModItems.COOKED_SALTED_SWIMMER, 0.5f, 200, "cooked_salted_swimmer");

        offerReversibleCompactingRecipes(exporter, RecipeCategory.BUILDING_BLOCKS, ModItems.DRIFTWOOD, RecipeCategory.MISC, ModBlocks.DRIFTWOOD_BLOCK);

        // ===== Phase wood planks =====
        offerPlanks(exporter, ModBlocks.PALM_PLANKS, ModBlocks.PALM_LOG);
        offerPlanks(exporter, ModBlocks.TIDEWOOD_PLANKS, ModBlocks.TIDEWOOD_LOG);
        offerPlanks(exporter, ModBlocks.CHARRED_PLANKS, ModBlocks.CHARRED_LOG);
        offerPlanks(exporter, ModBlocks.WISPWOOD_PLANKS, ModBlocks.WISPWOOD_LOG);
        offerPlanks(exporter, ModBlocks.VOIDBLOOM_PLANKS, ModBlocks.VOIDBLOOM_LOG);

        // Shorewood/ashen have no plank block of their own - they share their zone's planks.
        // Group-suffixed ids so they don't collide with the palm/charred log recipes above.
        offerPlanksFrom(exporter, ModBlocks.PALM_PLANKS, "shorewood", ModBlocks.SHOREWOOD_LOG, ModBlocks.SHOREWOOD_WOOD,
                ModBlocks.STRIPPED_SHOREWOOD_LOG, ModBlocks.STRIPPED_SHOREWOOD_WOOD);
        offerPlanksFrom(exporter, ModBlocks.CHARRED_PLANKS, "ashen", ModBlocks.ASHEN_LOG, ModBlocks.ASHEN_WOOD,
                ModBlocks.STRIPPED_ASHEN_LOG, ModBlocks.STRIPPED_ASHEN_WOOD);
        offerBarkBlockRecipe(exporter, ModBlocks.SHOREWOOD_WOOD, ModBlocks.SHOREWOOD_LOG);
        offerBarkBlockRecipe(exporter, ModBlocks.STRIPPED_SHOREWOOD_WOOD, ModBlocks.STRIPPED_SHOREWOOD_LOG);
        offerBarkBlockRecipe(exporter, ModBlocks.ASHEN_WOOD, ModBlocks.ASHEN_LOG);
        offerBarkBlockRecipe(exporter, ModBlocks.STRIPPED_ASHEN_WOOD, ModBlocks.STRIPPED_ASHEN_LOG);

        // Ship furniture (previously uncraftable - see CLAUDE.md TODO)
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, ModBlocks.HAMMOCK)
                .pattern("S S").pattern("WWW")
                .input('S', Items.STRING).input('W', ItemTags.WOOL)
                .criterion(hasItem(Items.STRING), conditionsFromItem(Items.STRING)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, ModBlocks.SHIP_BEDROLL)
                .pattern("CCC").pattern("WWW")
                .input('C', ModItems.TATTERED_CLOTH).input('W', ItemTags.WOOL)
                .criterion(hasItem(ModItems.TATTERED_CLOTH), conditionsFromItem(ModItems.TATTERED_CLOTH)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.DECORATIONS, ModBlocks.SHIPWRIGHT_TABLE)
                .pattern("RRR").pattern("PCP").pattern("P P")
                .input('R', ModItems.ROPE).input('P', ItemTags.PLANKS).input('C', Items.CRAFTING_TABLE)
                .criterion(hasItem(ModItems.ROPE), conditionsFromItem(ModItems.ROPE)).offerTo(exporter);
        ShapelessRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, ModBlocks.SHIP_WATERLINE, 4)
                .input(ItemTags.PLANKS).input(ItemTags.PLANKS).input(ItemTags.PLANKS).input(ItemTags.PLANKS)
                .input(Items.WHITE_DYE).input(Items.RED_DYE)
                .criterion(hasItem(Items.RED_DYE), conditionsFromItem(Items.RED_DYE)).offerTo(exporter);
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.PIRATE_JOURNAL)
                .input(Items.BOOK).input(ModItems.PIRATE_COIN).input(Items.FEATHER)
                .criterion(hasItem(ModItems.PIRATE_COIN), conditionsFromItem(ModItems.PIRATE_COIN)).offerTo(exporter);

        // ===== Galley cooking =====
        ShapelessRecipeJsonBuilder.create(RecipeCategory.FOOD, ModItems.BANANA_BREAD)
                .input(ModItems.BANANA).input(ModItems.BANANA).input(Items.WHEAT)
                .criterion(hasItem(ModItems.BANANA), conditionsFromItem(ModItems.BANANA))
                .offerTo(exporter);
        ShapelessRecipeJsonBuilder.create(RecipeCategory.FOOD, ModItems.HARDTACK, 2)
                .input(Items.WHEAT).input(Items.WHEAT).input(Items.WHEAT)
                .criterion(hasItem(Items.WHEAT), conditionsFromItem(Items.WHEAT))
                .offerTo(exporter);
        offerSmelting(exporter, List.of(ModItems.KRAKEN_INK), RecipeCategory.FOOD, ModItems.KRAKEN_CALAMARI, 0.5f, 200, "kraken_calamari");
        // Early-game food + healing (2026-09-30 playtest): roast bananas anywhere (furnace, smoker, campfire)
        offerFoodCookingRecipe(exporter, "smelting", net.minecraft.recipe.RecipeSerializer.SMELTING, 200, ModItems.BANANA, ModItems.ROASTED_BANANA, 0.35f);
        offerFoodCookingRecipe(exporter, "smoking", net.minecraft.recipe.RecipeSerializer.SMOKING, 100, ModItems.BANANA, ModItems.ROASTED_BANANA, 0.35f);
        offerFoodCookingRecipe(exporter, "campfire_cooking", net.minecraft.recipe.RecipeSerializer.CAMPFIRE_COOKING, 600, ModItems.BANANA, ModItems.ROASTED_BANANA, 0.35f);
        ShapelessRecipeJsonBuilder.create(RecipeCategory.FOOD, ModItems.COCONUT_WATER)
                .input(ModItems.COCONUT).input(Items.GLASS_BOTTLE)
                .criterion(hasItem(ModItems.COCONUT), conditionsFromItem(ModItems.COCONUT))
                .offerTo(exporter);
        ShapelessRecipeJsonBuilder.create(RecipeCategory.FOOD, ModItems.ISLAND_SKEWER)
                .input(Items.STICK).input(ModItems.BANANA)
                .input(Ingredient.ofItems(Items.COOKED_COD, Items.COOKED_SALMON, ModItems.COOKED_SHARK_MEAT, ModItems.COOKED_SALTED_SWIMMER,
                        net.get900.pixelpirates.homestead.HomesteadItems.COOKED_FISH_FILLET))
                .criterion(hasItem(ModItems.BANANA), conditionsFromItem(ModItems.BANANA))
                .offerTo(exporter);
        // THE GALLEY: every dish in item/food/PirateFoods
        for (var spec : net.get900.pixelpirates.item.food.PirateFoods.SPECS) {
            Item dish = net.get900.pixelpirates.item.food.PirateFoods.item(spec.id());
            if (spec.ingredients() == null && spec.cookedFrom() == null) continue;      // drop-only (boss dishes, whale's bounty)
            if (spec.cookedFrom() != null) {
                Item raw = spec.cookedFrom().get();
                offerFoodCookingRecipe(exporter, "smelting", net.minecraft.recipe.RecipeSerializer.SMELTING, 200, raw, dish, 0.35f);
                offerFoodCookingRecipe(exporter, "smoking", net.minecraft.recipe.RecipeSerializer.SMOKING, 100, raw, dish, 0.35f);
                offerFoodCookingRecipe(exporter, "campfire_cooking", net.minecraft.recipe.RecipeSerializer.CAMPFIRE_COOKING, 600, raw, dish, 0.35f);
                continue;
            }
            var b = ShapelessRecipeJsonBuilder.create(RecipeCategory.FOOD, dish, spec.count());
            var ings = net.get900.pixelpirates.item.food.PirateFoods.ingredients(spec);
            for (Ingredient in : ings) b.input(in);
            Item key = ings.get(0).getMatchingStacks()[0].getItem();
            for (Ingredient in : ings) {                              // unlock with the first real ingredient, not the bowl
                Item i = in.getMatchingStacks()[0].getItem();
                if (i != Items.BOWL && i != Items.GLASS_BOTTLE && i != Items.STICK) { key = i; break; }
            }
            b.criterion(hasItem(key), conditionsFromItem(key)).offerTo(exporter);
        }
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.SEA_BANDAGE, 2)
                .input(Items.PAPER).input(Items.STRING).input(Items.KELP)
                .criterion(hasItem(Items.STRING), conditionsFromItem(Items.STRING))
                .offerTo(exporter);
        ShapelessRecipeJsonBuilder.create(RecipeCategory.FOOD, ModItems.COCONUT_GROG)
                .input(ModItems.COCONUT).input(ModItems.GROG)
                .criterion(hasItem(ModItems.GROG), conditionsFromItem(ModItems.GROG))
                .offerTo(exporter);
        ShapelessRecipeJsonBuilder.create(RecipeCategory.FOOD, ModItems.PIRATES_STEW)
                .input(Items.BOWL).input(ModItems.COOKED_SHARK_MEAT).input(ModItems.COCONUT).input(ModItems.BANANA)
                .criterion(hasItem(ModItems.COOKED_SHARK_MEAT), conditionsFromItem(ModItems.COOKED_SHARK_MEAT))
                .offerTo(exporter);

        // ===== Region crafting materials =====
        // Each phase's materials only drop in that phase (Materials & Gear Ladder, 2026-10-01): the old kraken-ink conversions
        // into cursed bone / kraken scale are gone. Brimstone + charcoal is the gunpowder supply (no creepers at sea).
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, Items.GUNPOWDER, 2)
                .input(ModItems.BRIMSTONE).input(Ingredient.ofItems(Items.CHARCOAL, Items.COAL))
                .criterion(hasItem(ModItems.BRIMSTONE), conditionsFromItem(ModItems.BRIMSTONE))
                .offerTo(exporter, new net.minecraft.util.Identifier(net.get900.pixelpirates.PixelPirates.MOD_ID, "gunpowder_from_brimstone"));
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.SHELL_BUCKLER)
                .pattern(" C ").pattern("CDC").pattern(" C ").input('C', ModItems.CRAB_SHELL).input('D', ModItems.DRIFTWOOD)
                .criterion(hasItem(ModItems.CRAB_SHELL), conditionsFromItem(ModItems.CRAB_SHELL)).offerTo(exporter);
        ShapelessRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.INK_BOMB, 2)
                .input(ModItems.KRAKEN_INK).input(Items.GLASS_BOTTLE).input(Items.GUNPOWDER)
                .criterion(hasItem(ModItems.KRAKEN_INK), conditionsFromItem(ModItems.KRAKEN_INK)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.BOARDING_AXE)
                .pattern("II").pattern("IC").pattern(" D").input('I', Items.IRON_INGOT).input('C', ModItems.CRAB_SHELL).input('D', ModItems.DRIFTWOOD)
                .criterion(hasItem(ModItems.CRAB_SHELL), conditionsFromItem(ModItems.CRAB_SHELL)).offerTo(exporter);
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.VOLCANIC_EMBER, 2)
                .input(Items.MAGMA_CREAM).input(Items.COAL)
                .criterion(hasItem(Items.MAGMA_CREAM), conditionsFromItem(Items.MAGMA_CREAM))
                .offerTo(exporter);

        // ===== Ring 1 — Starter Seas =====
        offerBlade(exporter, ModItems.MARLINSPIKE, Items.IRON_NUGGET, Items.IRON_NUGGET, ModItems.DRIFTWOOD);
        offerBlade(exporter, ModItems.BOARDING_SABRE, Items.IRON_INGOT, Items.IRON_INGOT, ModItems.DRIFTWOOD);
        offerArmorSet(exporter, ModItems.TATTERED_CLOTH,
                ModItems.CASTAWAY_HELMET, ModItems.CASTAWAY_CHESTPLATE, ModItems.CASTAWAY_LEGGINGS, ModItems.CASTAWAY_BOOTS);

        // ===== Ring 2 — Merchant Waters =====
        offerBlade(exporter, ModItems.NAVAL_RAPIER, Items.IRON_NUGGET, Items.IRON_INGOT, ModItems.REEF_PEARL);
        offerBlade(exporter, ModItems.OFFICERS_SABRE, Items.IRON_INGOT, Items.IRON_INGOT, ModItems.REEF_PEARL);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.THROWING_KNIFE, 4)
                .pattern("I").pattern("S")
                .input('I', Items.IRON_INGOT).input('S', Items.STICK)
                .criterion(hasItem(Items.IRON_INGOT), conditionsFromItem(Items.IRON_INGOT))
                .offerTo(exporter);
        offerMixedArmorSet(exporter, ModItems.TATTERED_CLOTH, ModItems.CRAB_SHELL,
                ModItems.NAVY_OFFICER_HELMET, ModItems.NAVY_OFFICER_CHESTPLATE, ModItems.NAVY_OFFICER_LEGGINGS, ModItems.NAVY_OFFICER_BOOTS);

        // ===== Ring 3 — Pirate Territory =====
        offerBlade(exporter, ModItems.CORSAIR_CUTLASS, ModItems.OBSIDIAN_SHARD, Items.IRON_INGOT, ModItems.ROPE);
        offerBlade(exporter, ModItems.BOARDING_PIKE, ModItems.OBSIDIAN_SHARD, Items.STICK, Items.STICK);
        offerMixedArmorSet(exporter, ModItems.SIREN_SCALE, ModItems.PIRATE_COIN,
                ModItems.CORSAIR_HELMET, ModItems.CORSAIR_CHESTPLATE, ModItems.CORSAIR_LEGGINGS, ModItems.CORSAIR_BOOTS);

        // ===== Volcanic Isles =====
        offerBlade(exporter, ModItems.EMBERBRAND, ModItems.VOLCANIC_EMBER, ModItems.OBSIDIAN_SHARD, ModItems.ROPE);
        offerTrimmedArmorSet(exporter, ModItems.VOLCANIC_EMBER, ModItems.BRIMSTONE,
                ModItems.ASHEN_HELMET, ModItems.ASHEN_CHESTPLATE, ModItems.ASHEN_LEGGINGS, ModItems.ASHEN_BOOTS);

        // ===== Ring 4 — Cursed Seas =====
        offerBlade(exporter, ModItems.SOULRENDER, ModItems.CURSED_BONE, ModItems.ECTOPLASM, ModItems.ROPE);
        offerBlade(exporter, ModItems.WRAITHBLADE, ModItems.ECTOPLASM, ModItems.ECTOPLASM, ModItems.ROPE);
        offerTrimmedArmorSet(exporter, ModItems.CURSED_BONE, ModItems.ECTOPLASM,
                ModItems.CURSED_BONE_HELMET, ModItems.CURSED_BONE_CHESTPLATE, ModItems.CURSED_BONE_LEGGINGS, ModItems.CURSED_BONE_BOOTS);

        // ===== Ring 5 — The Abyss =====
        offerBlade(exporter, ModItems.KRAKEN_FANG, ModItems.KRAKEN_SCALE, ModItems.ABYSSAL_PEARL, ModItems.ROPE);
        offerBlade(exporter, ModItems.STORMCALLER, ModItems.KRAKEN_SCALE, ModItems.ABYSSAL_PEARL, Items.GOLD_INGOT);
        offerBlade(exporter, ModItems.ABYSSAL_HARPOON, ModItems.ABYSSAL_PEARL, ModItems.KRAKEN_SCALE, Items.STICK);
        offerTrimmedArmorSet(exporter, ModItems.KRAKEN_SCALE, ModItems.ABYSSAL_PEARL,
                ModItems.KRAKEN_SCALE_HELMET, ModItems.KRAKEN_SCALE_CHESTPLATE, ModItems.KRAKEN_SCALE_LEGGINGS, ModItems.KRAKEN_SCALE_BOOTS);
    }

    /** Log → 4 planks, shapeless like vanilla. */
    private void offerPlanks(Consumer<RecipeJsonProvider> exporter, ItemConvertible planks, ItemConvertible log) {
        ShapelessRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, planks, 4)
                .input(log)
                .criterion(hasItem(log), conditionsFromItem(log))
                .offerTo(exporter);
    }

    /** Any of several logs -> 4 planks, under its own recipe id so it can coexist with offerPlanks. */
    private void offerPlanksFrom(Consumer<RecipeJsonProvider> exporter, ItemConvertible planks, String source,
                                 ItemConvertible... logs) {
        ShapelessRecipeJsonBuilder.create(RecipeCategory.BUILDING_BLOCKS, planks, 4)
                .input(Ingredient.ofItems(logs))
                .group("planks")
                .criterion(hasItem(logs[0]), conditionsFromItem(logs[0]))
                .offerTo(exporter, getItemPath(planks) + "_from_" + source);
    }

    /** Vertical 3-part blade: tip / blade / hilt. */
    private void offerBlade(Consumer<RecipeJsonProvider> exporter, Item result,
                            ItemConvertible tip, ItemConvertible blade, ItemConvertible hilt) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, result)
                .pattern("T").pattern("B").pattern("H")
                .input('T', tip).input('B', blade).input('H', hilt)
                .criterion(hasItem(blade), conditionsFromItem(blade))
                .offerTo(exporter);
    }

    /** Full armor set from a single material, vanilla armor shapes. */
    private void offerArmorSet(Consumer<RecipeJsonProvider> exporter, ItemConvertible material,
                               Item helmet, Item chestplate, Item leggings, Item boots) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, helmet)
                .pattern("MMM").pattern("M M").input('M', material)
                .criterion(hasItem(material), conditionsFromItem(material)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, chestplate)
                .pattern("M M").pattern("MMM").pattern("MMM").input('M', material)
                .criterion(hasItem(material), conditionsFromItem(material)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, leggings)
                .pattern("MMM").pattern("M M").pattern("M M").input('M', material)
                .criterion(hasItem(material), conditionsFromItem(material)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, boots)
                .pattern("M M").pattern("M M").input('M', material)
                .criterion(hasItem(material), conditionsFromItem(material)).offerTo(exporter);
    }

    /** Vanilla armor shapes in the main material, one accent per piece (two on boots): 19 main + 5 accent. */
    private void offerTrimmedArmorSet(Consumer<RecipeJsonProvider> exporter, ItemConvertible main, ItemConvertible accent,
                                      Item helmet, Item chestplate, Item leggings, Item boots) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, helmet)
                .pattern("MAM").pattern("M M").input('M', main).input('A', accent)
                .criterion(hasItem(main), conditionsFromItem(main)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, chestplate)
                .pattern("M M").pattern("MAM").pattern("MMM").input('M', main).input('A', accent)
                .criterion(hasItem(main), conditionsFromItem(main)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, leggings)
                .pattern("MAM").pattern("M M").pattern("M M").input('M', main).input('A', accent)
                .criterion(hasItem(main), conditionsFromItem(main)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, boots)
                .pattern("M M").pattern("A A").input('M', main).input('A', accent)
                .criterion(hasItem(main), conditionsFromItem(main)).offerTo(exporter);
    }

    /** Armor set from a main material with an accent material — patterns stay distinct from vanilla. */
    private void offerMixedArmorSet(Consumer<RecipeJsonProvider> exporter, ItemConvertible main, ItemConvertible accent,
                                    Item helmet, Item chestplate, Item leggings, Item boots) {
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, helmet)
                .pattern("MMM").pattern("A A").input('M', main).input('A', accent)
                .criterion(hasItem(main), conditionsFromItem(main)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, chestplate)
                .pattern("A A").pattern("MMM").pattern("MMM").input('M', main).input('A', accent)
                .criterion(hasItem(main), conditionsFromItem(main)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, leggings)
                .pattern("MMM").pattern("A A").pattern("A A").input('M', main).input('A', accent)
                .criterion(hasItem(main), conditionsFromItem(main)).offerTo(exporter);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, boots)
                .pattern("A A").pattern("M M").input('M', main).input('A', accent)
                .criterion(hasItem(main), conditionsFromItem(main)).offerTo(exporter);
    }

    private static void relicWeapon(Consumer<RecipeJsonProvider> exporter, Item weapon, Item relic,
                                    java.util.function.UnaryOperator<ShapelessRecipeJsonBuilder> rest) {
        rest.apply(ShapelessRecipeJsonBuilder.create(RecipeCategory.COMBAT, weapon).input(relic))
                .criterion(hasItem(relic), conditionsFromItem(relic))
                .offerTo(exporter, new net.minecraft.util.Identifier(net.get900.pixelpirates.PixelPirates.MOD_ID, getRecipeName(weapon)));
    }
}
