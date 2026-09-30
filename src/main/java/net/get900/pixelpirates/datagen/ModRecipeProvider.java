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
        // Dynamite is an early-game key (blast rubble seals structure vaults): powder from pirate crew
        // and raft pirates, paper and string from any island.
        ShapelessRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.DYNAMITE, 2)
                .input(Items.GUNPOWDER).input(Items.PAPER).input(Items.STRING)
                .criterion(hasItem(Items.GUNPOWDER), conditionsFromItem(Items.GUNPOWDER))
                .offerTo(exporter, new net.minecraft.util.Identifier(net.get900.pixelpirates.PixelPirates.MOD_ID, "dynamite"));
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
        ShapelessRecipeJsonBuilder.create(RecipeCategory.FOOD, ModItems.COCONUT_GROG)
                .input(ModItems.COCONUT).input(ModItems.GROG)
                .criterion(hasItem(ModItems.GROG), conditionsFromItem(ModItems.GROG))
                .offerTo(exporter);
        ShapelessRecipeJsonBuilder.create(RecipeCategory.FOOD, ModItems.PIRATES_STEW)
                .input(Items.BOWL).input(ModItems.COOKED_SHARK_MEAT).input(ModItems.COCONUT).input(ModItems.BANANA)
                .criterion(hasItem(ModItems.COOKED_SHARK_MEAT), conditionsFromItem(ModItems.COOKED_SHARK_MEAT))
                .offerTo(exporter);

        // ===== Region crafting materials (conversion recipes until region loot drops are wired) =====
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.CURSED_BONE, 2)
                .input(Items.BONE).input(Items.BONE).input(ModItems.KRAKEN_INK)
                .criterion(hasItem(ModItems.KRAKEN_INK), conditionsFromItem(ModItems.KRAKEN_INK))
                .offerTo(exporter);
        ShapelessRecipeJsonBuilder.create(RecipeCategory.MISC, ModItems.KRAKEN_SCALE, 2)
                .input(Items.PRISMARINE_SHARD).input(Items.PRISMARINE_SHARD).input(ModItems.KRAKEN_INK)
                .criterion(hasItem(Items.PRISMARINE_SHARD), conditionsFromItem(Items.PRISMARINE_SHARD))
                .offerTo(exporter);
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
        offerBlade(exporter, ModItems.NAVAL_RAPIER, Items.IRON_NUGGET, Items.IRON_INGOT, Items.STICK);
        offerBlade(exporter, ModItems.OFFICERS_SABRE, Items.IRON_INGOT, Items.IRON_INGOT, Items.GOLD_NUGGET);
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.THROWING_KNIFE, 4)
                .pattern("I").pattern("S")
                .input('I', Items.IRON_INGOT).input('S', Items.STICK)
                .criterion(hasItem(Items.IRON_INGOT), conditionsFromItem(Items.IRON_INGOT))
                .offerTo(exporter);
        offerMixedArmorSet(exporter, Items.IRON_INGOT, ModItems.TATTERED_CLOTH,
                ModItems.NAVY_OFFICER_HELMET, ModItems.NAVY_OFFICER_CHESTPLATE, ModItems.NAVY_OFFICER_LEGGINGS, ModItems.NAVY_OFFICER_BOOTS);

        // ===== Ring 3 — Pirate Territory =====
        offerBlade(exporter, ModItems.CORSAIR_CUTLASS, Items.DIAMOND, Items.IRON_INGOT, ModItems.ROPE);
        offerBlade(exporter, ModItems.BOARDING_PIKE, Items.IRON_INGOT, Items.STICK, Items.STICK);
        offerMixedArmorSet(exporter, Items.GOLD_INGOT, ModItems.PIRATE_COIN,
                ModItems.CORSAIR_HELMET, ModItems.CORSAIR_CHESTPLATE, ModItems.CORSAIR_LEGGINGS, ModItems.CORSAIR_BOOTS);

        // ===== Volcanic Isles =====
        offerBlade(exporter, ModItems.EMBERBRAND, ModItems.VOLCANIC_EMBER, ModItems.VOLCANIC_EMBER, ModItems.ROPE);
        offerArmorSet(exporter, ModItems.VOLCANIC_EMBER,
                ModItems.ASHEN_HELMET, ModItems.ASHEN_CHESTPLATE, ModItems.ASHEN_LEGGINGS, ModItems.ASHEN_BOOTS);

        // ===== Ring 4 — Cursed Seas =====
        offerBlade(exporter, ModItems.SOULRENDER, ModItems.CURSED_BONE, ModItems.CURSED_BONE, ModItems.ROPE);
        offerBlade(exporter, ModItems.WRAITHBLADE, ModItems.CURSED_BONE, ModItems.KRAKEN_INK, ModItems.ROPE);
        offerArmorSet(exporter, ModItems.CURSED_BONE,
                ModItems.CURSED_BONE_HELMET, ModItems.CURSED_BONE_CHESTPLATE, ModItems.CURSED_BONE_LEGGINGS, ModItems.CURSED_BONE_BOOTS);

        // ===== Ring 5 — The Abyss =====
        ShapedRecipeJsonBuilder.create(RecipeCategory.COMBAT, ModItems.KRAKEN_FANG)
                .pattern("K").pattern("R")
                .input('K', ModItems.KRAKEN_SCALE).input('R', ModItems.ROPE)
                .criterion(hasItem(ModItems.KRAKEN_SCALE), conditionsFromItem(ModItems.KRAKEN_SCALE))
                .offerTo(exporter);
        offerBlade(exporter, ModItems.STORMCALLER, ModItems.KRAKEN_SCALE, ModItems.KRAKEN_SCALE, Items.GOLD_INGOT);
        offerBlade(exporter, ModItems.ABYSSAL_HARPOON, ModItems.KRAKEN_SCALE, Items.IRON_INGOT, Items.STICK);
        offerArmorSet(exporter, ModItems.KRAKEN_SCALE,
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
}
