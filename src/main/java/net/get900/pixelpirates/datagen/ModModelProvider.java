package net.get900.pixelpirates.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.block.custom.WaterLightBlock;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.data.client.*;
import net.minecraft.item.ArmorItem;
import net.minecraft.util.Identifier;

public class ModModelProvider extends FabricModelProvider {
    public ModModelProvider(FabricDataOutput output) {
        super(output);
    }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator blockStateModelGenerator) {
        // Barrel: staves on the sides, lid with bung on top (grog_barrel_side/_top/_bottom)
        blockStateModelGenerator.registerSingleton(ModBlocks.GROG_BARREL, TexturedModel.CUBE_BOTTOM_TOP);

        Identifier lampOffIdentifier = TexturedModel.CUBE_ALL.upload(ModBlocks.WATER_LIGHT_BLOCK, blockStateModelGenerator.modelCollector);
        Identifier lampOnIdentifier = blockStateModelGenerator.createSubModel(ModBlocks.WATER_LIGHT_BLOCK, "_on", Models.CUBE_ALL, TextureMap::all);
        blockStateModelGenerator.blockStateCollector.accept(VariantsBlockStateSupplier.create(ModBlocks.WATER_LIGHT_BLOCK)
                .coordinate(BlockStateModelGenerator.createBooleanModelMap(WaterLightBlock.CLICKED, lampOnIdentifier, lampOffIdentifier)));

        blockStateModelGenerator.registerLog(ModBlocks.DRIFTWOOD_BLOCK).log(ModBlocks.DRIFTWOOD_BLOCK);

        //TemperateShallows
        blockStateModelGenerator.registerLog(ModBlocks.SHOREWOOD_LOG).log(ModBlocks.SHOREWOOD_LOG);
        blockStateModelGenerator.registerLog(ModBlocks.STRIPPED_SHOREWOOD_LOG).log(ModBlocks.STRIPPED_SHOREWOOD_LOG);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.SHOREWOOD_LEAVES);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.SHOREWOOD_WOOD);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.STRIPPED_SHOREWOOD_WOOD);
        blockStateModelGenerator.registerFlowerPotPlant(ModBlocks.SHOREWOOD_SAPLING, ModBlocks.POTTED_SHOREWOOD_SAPLING,
                BlockStateModelGenerator.TintType.NOT_TINTED);
        // BANANA_BLOCK, COCONUT_BLOCK, HAMMOCK, SHIP_BEDROLL, SHIP_HELM, SHIP_CANNON, SHIP_MAST have shaped,
        // hand-authored models in src/main/resources - keep them out of datagen or the two copies fight.

        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.SHIP_WATERLINE);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.BLAST_RUBBLE);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.SERPENT_WARD);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.LIVING_FLESH);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.HEART_VALVE);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.QUENCH_VALVE);
        blockStateModelGenerator.registerLog(ModBlocks.GHOSTWOOD_LOG).log(ModBlocks.GHOSTWOOD_LOG);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.GHOSTWOOD_PLANKS);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.SPECTRAL_SAIL);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.PHANTOM_BUOY);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.GHOST_MAST);
        // GHOST_CANNON: hand-authored model (ship_cannon geometry, ghost textures) in resources/
        // FORT_CANNON reuses the hand-authored ship_cannon model (blockstate + item model in resources/)
        blockStateModelGenerator.registerSingleton(ModBlocks.SHIPWRIGHT_TABLE, TexturedModel.CUBE_BOTTOM_TOP);

        //Phase3
        blockStateModelGenerator.registerLog(ModBlocks.ASHEN_LOG).log(ModBlocks.ASHEN_LOG);
        blockStateModelGenerator.registerLog(ModBlocks.STRIPPED_ASHEN_LOG).log(ModBlocks.STRIPPED_ASHEN_LOG);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.ASHEN_WOOD);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.STRIPPED_ASHEN_WOOD);

        // Phase flora & ambience
        blockStateModelGenerator.registerLog(ModBlocks.PALM_LOG).log(ModBlocks.PALM_LOG);
        blockStateModelGenerator.registerLog(ModBlocks.TIDEWOOD_LOG).log(ModBlocks.TIDEWOOD_LOG);
        blockStateModelGenerator.registerLog(ModBlocks.CHARRED_LOG).log(ModBlocks.CHARRED_LOG);
        blockStateModelGenerator.registerLog(ModBlocks.WISPWOOD_LOG).log(ModBlocks.WISPWOOD_LOG);
        blockStateModelGenerator.registerLog(ModBlocks.VOIDBLOOM_LOG).log(ModBlocks.VOIDBLOOM_LOG);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.PALM_LEAVES);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.TIDEWOOD_LEAVES);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.EMBER_LEAVES);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.WISP_LEAVES);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.VOIDBLOOM_LEAVES);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.SHELL_BLOCK);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.TIDE_POOL_ROCK);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.CORAL_ROCK);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.PEARL_BLOCK);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.SCORCHED_SAND);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.SULFUR_BLOCK);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.GRAVE_SILT);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.SOUL_BARNACLE);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.ABYSSAL_SLATE);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.LUMINOUS_VEIN);
        // PALM_PLANKS: HomesteadModelProvider (texture pool: palm stairs/slab/fence/gate)
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.TIDEWOOD_PLANKS);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.CHARRED_PLANKS);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.WISPWOOD_PLANKS);
        blockStateModelGenerator.registerSimpleCubeAll(ModBlocks.VOIDBLOOM_PLANKS);
    }

    @Override
    public void generateItemModels(ItemModelGenerator itemModelGenerator) {
        itemModelGenerator.register(ModItems.SHIP_REPAIR_KIT, Models.GENERATED);
        itemModelGenerator.register(ModItems.TIDEBREAKER, Models.HANDHELD);
        itemModelGenerator.register(ModItems.DEPTH_CHARGE, Models.GENERATED);
        itemModelGenerator.register(ModItems.HARPOON, Models.HANDHELD);
        itemModelGenerator.register(ModItems.TIDESHACKLE, Models.HANDHELD);
        itemModelGenerator.register(ModItems.CHUM, Models.GENERATED);
        itemModelGenerator.register(ModItems.BLOODFIN_FLESH, Models.GENERATED);
        itemModelGenerator.register(ModItems.RACKHAMS_DIVING_CHARM, Models.GENERATED);
        itemModelGenerator.register(ModItems.SERPENTS_TIDE_PEARL, Models.GENERATED);
        itemModelGenerator.register(ModItems.EVERBURNING_LANTERN, Models.GENERATED);
        itemModelGenerator.register(ModItems.SPECTRAL_ANCHOR, Models.GENERATED);
        itemModelGenerator.register(ModItems.ROYAL_TIDE_SIGIL, Models.GENERATED);
        itemModelGenerator.register(ModItems.BLOODFIN_RAZOR_TOOTH, Models.GENERATED);
        itemModelGenerator.register(ModItems.KRAKENS_INK_HEART, Models.GENERATED);
        itemModelGenerator.register(ModItems.BROKEN_SHACKLE, Models.GENERATED);
        itemModelGenerator.register(ModItems.ABYSSAL_HEARTSTONE, Models.GENERATED);
        itemModelGenerator.register(ModItems.SHIP_BLUEPRINT, Models.GENERATED);
        itemModelGenerator.register(ModItems.BOARDING_AXE, Models.GENERATED);
        itemModelGenerator.register(ModItems.CANNON, Models.GENERATED);
        itemModelGenerator.register(ModItems.CANNON_BALL, Models.GENERATED);
        itemModelGenerator.register(ModItems.COIN, Models.GENERATED);
        itemModelGenerator.register(ModItems.COOKED_SALTED_SWIMMER, Models.GENERATED);
        itemModelGenerator.register(ModItems.COOKED_SHARK_MEAT, Models.GENERATED);
        // CUTLASS uses a GeckoLib GeoItem renderer; model JSON is managed manually in src/main/resources
        itemModelGenerator.register(ModItems.DAGGER, Models.GENERATED);
        itemModelGenerator.register(ModItems.DRIFTWOOD, Models.GENERATED);
        itemModelGenerator.register(ModItems.DYNAMITE, Models.GENERATED);
        itemModelGenerator.register(ModItems.GROG, Models.GENERATED);
        itemModelGenerator.register(ModItems.KRAKEN_INK, Models.GENERATED);
        itemModelGenerator.register(ModItems.MAST, Models.GENERATED);
        itemModelGenerator.register(ModItems.MAST_WITH_SAILS, Models.GENERATED);
        itemModelGenerator.register(ModItems.PIRATE_COIN, Models.GENERATED);
        itemModelGenerator.register(ModItems.RAW_SALTED_SWIMMER, Models.GENERATED);
        itemModelGenerator.register(ModItems.RAW_SHARK_MEAT, Models.GENERATED);
        itemModelGenerator.register(ModItems.ROPE, Models.GENERATED);
        itemModelGenerator.register(ModItems.RUSTED_CUTLASS, Models.GENERATED);
        itemModelGenerator.register(ModItems.SAIL, Models.GENERATED);
        itemModelGenerator.register(ModItems.BROKEN_SHOVEL, Models.GENERATED);
        itemModelGenerator.register(ModItems.TATTERED_CLOTH, Models.GENERATED);
        itemModelGenerator.register(ModItems.BANANA, Models.GENERATED);
        itemModelGenerator.register(ModItems.COCONUT, Models.GENERATED);
        itemModelGenerator.register(ModItems.PIRATE_JOURNAL, Models.GENERATED);
        // Spawn eggs use the vanilla tinted template - colours come from ModSpawnEggs
        Model spawnEgg = new Model(java.util.Optional.of(new Identifier("item/template_spawn_egg")), java.util.Optional.empty());
        for (net.minecraft.item.Item egg : net.get900.pixelpirates.item.ModSpawnEggs.ALL) {
            itemModelGenerator.register(egg, spawnEgg);
        }

        itemModelGenerator.registerArmor(((ArmorItem) ModItems.PIRATE_HELMET));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.PIRATE_CHESTPLATE));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.PIRATE_LEGGINGS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.PIRATE_BOOTS));

        // Galley cooking
        itemModelGenerator.register(ModItems.BANANA_BREAD, Models.GENERATED);
        itemModelGenerator.register(ModItems.HARDTACK, Models.GENERATED);
        itemModelGenerator.register(ModItems.KRAKEN_CALAMARI, Models.GENERATED);
        itemModelGenerator.register(ModItems.COCONUT_GROG, Models.GENERATED);
        itemModelGenerator.register(ModItems.PIRATES_STEW, Models.GENERATED);

        // Region crafting materials
        itemModelGenerator.register(ModItems.CURSED_BONE, Models.GENERATED);
        itemModelGenerator.register(ModItems.KRAKEN_SCALE, Models.GENERATED);
        itemModelGenerator.register(ModItems.VOLCANIC_EMBER, Models.GENERATED);

        // Region weapons — HANDHELD so they angle in the hand like vanilla swords
        itemModelGenerator.register(ModItems.MARLINSPIKE, Models.HANDHELD);
        itemModelGenerator.register(ModItems.BOSS_SLAYER, Models.HANDHELD);
        itemModelGenerator.register(ModItems.BOARDING_SABRE, Models.HANDHELD);
        itemModelGenerator.register(ModItems.NAVAL_RAPIER, Models.HANDHELD);
        itemModelGenerator.register(ModItems.OFFICERS_SABRE, Models.HANDHELD);
        itemModelGenerator.register(ModItems.THROWING_KNIFE, Models.HANDHELD);
        itemModelGenerator.register(ModItems.CORSAIR_CUTLASS, Models.HANDHELD);
        itemModelGenerator.register(ModItems.BOARDING_PIKE, Models.HANDHELD);
        itemModelGenerator.register(ModItems.EMBERBRAND, Models.HANDHELD);
        itemModelGenerator.register(ModItems.SOULRENDER, Models.HANDHELD);
        itemModelGenerator.register(ModItems.WRAITHBLADE, Models.HANDHELD);
        itemModelGenerator.register(ModItems.KRAKEN_FANG, Models.HANDHELD);
        itemModelGenerator.register(ModItems.STORMCALLER, Models.HANDHELD);
        itemModelGenerator.register(ModItems.ABYSSAL_HARPOON, Models.HANDHELD);

        // Region armor sets
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CASTAWAY_HELMET));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CASTAWAY_CHESTPLATE));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CASTAWAY_LEGGINGS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CASTAWAY_BOOTS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.NAVY_OFFICER_HELMET));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.NAVY_OFFICER_CHESTPLATE));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.NAVY_OFFICER_LEGGINGS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.NAVY_OFFICER_BOOTS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CORSAIR_HELMET));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CORSAIR_CHESTPLATE));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CORSAIR_LEGGINGS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CORSAIR_BOOTS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.ASHEN_HELMET));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.ASHEN_CHESTPLATE));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.ASHEN_LEGGINGS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.ASHEN_BOOTS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CURSED_BONE_HELMET));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CURSED_BONE_CHESTPLATE));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CURSED_BONE_LEGGINGS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.CURSED_BONE_BOOTS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.KRAKEN_SCALE_HELMET));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.KRAKEN_SCALE_CHESTPLATE));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.KRAKEN_SCALE_LEGGINGS));
        itemModelGenerator.registerArmor(((ArmorItem) ModItems.KRAKEN_SCALE_BOOTS));

    }
}
