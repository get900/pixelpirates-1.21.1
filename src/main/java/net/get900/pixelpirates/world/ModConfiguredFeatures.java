package net.get900.pixelpirates.world;

import net.minecraft.world.gen.foliage.CherryFoliagePlacer;
import net.minecraft.world.gen.foliage.BushFoliagePlacer;
import net.minecraft.world.gen.foliage.RandomSpreadFoliagePlacer;
import net.minecraft.world.gen.foliage.SpruceFoliagePlacer;
import net.minecraft.world.gen.foliage.LargeOakFoliagePlacer;
import net.minecraft.world.gen.trunk.CherryTrunkPlacer;
import net.minecraft.world.gen.trunk.LargeOakTrunkPlacer;
import net.minecraft.world.gen.trunk.BendingTrunkPlacer;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.world.gen.ModFeatures;
import net.get900.pixelpirates.world.tree.BananaTreeDecorator;
import net.get900.pixelpirates.world.tree.CoconutTreeDecorator;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.property.Properties;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.intprovider.ConstantIntProvider;
import net.minecraft.util.math.intprovider.UniformIntProvider;
import net.minecraft.world.gen.blockpredicate.BlockPredicate;
import net.minecraft.world.gen.feature.*;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.size.TwoLayersFeatureSize;
import net.minecraft.world.gen.foliage.AcaciaFoliagePlacer;
import net.minecraft.world.gen.foliage.BlobFoliagePlacer;
import net.minecraft.world.gen.stateprovider.BlockStateProvider;
import net.minecraft.world.gen.stateprovider.PredicatedStateProvider;
import net.minecraft.world.gen.treedecorator.BeehiveTreeDecorator;
import net.minecraft.world.gen.trunk.ForkingTrunkPlacer;
import net.minecraft.world.gen.trunk.StraightTrunkPlacer;

import java.util.List;


public class ModConfiguredFeatures {
    // CF (Configure Feature, What) -> PF (Place Feature, How) -> BM (Biome Modifications, Where)
    public static final RegistryKey<ConfiguredFeature<?, ?>> DRIFTWOOD_BLOCK_KEY = registerKey("driftwood_block");
    public static final RegistryKey<ConfiguredFeature<?, ?>> SPAWN_ISLAND_BUILDINGS_KEY = registerKey("spawn_island_buildings");
    public static final RegistryKey<ConfiguredFeature<?, ?>> GALLOWS_GROTTO_KEY = registerKey("gallows_grotto");
    public static final RegistryKey<ConfiguredFeature<?, ?>> TITANS_CHEST_KEY = registerKey("titans_chest");
    public static final RegistryKey<ConfiguredFeature<?, ?>> LEVIATHAN_SITES_KEY = registerKey("leviathan_sites");

    // Phase trees v2 — one leafy signature tree per phase
    public static final RegistryKey<ConfiguredFeature<?, ?>> PALM_TREE_KEY = registerKey("palm_tree");
    public static final RegistryKey<ConfiguredFeature<?, ?>> TIDEWOOD_TREE_KEY = registerKey("tidewood_tree");
    public static final RegistryKey<ConfiguredFeature<?, ?>> CINDER_TREE_KEY = registerKey("cinder_tree");
    public static final RegistryKey<ConfiguredFeature<?, ?>> WISPWOOD_TREE_KEY = registerKey("wispwood_tree");
    public static final RegistryKey<ConfiguredFeature<?, ?>> VOIDBLOOM_TREE_KEY = registerKey("voidbloom_tree");

    // Ambient decoration — boulders (FOREST_ROCK) and ground patches (DISK)
    public static final RegistryKey<ConfiguredFeature<?, ?>> TIDE_POOL_ROCK_KEY = registerKey("tide_pool_rock_boulder");
    public static final RegistryKey<ConfiguredFeature<?, ?>> CORAL_ROCK_KEY = registerKey("coral_rock_boulder");
    public static final RegistryKey<ConfiguredFeature<?, ?>> PEARL_ROCK_KEY = registerKey("pearl_boulder");
    public static final RegistryKey<ConfiguredFeature<?, ?>> SULFUR_BOULDER_KEY = registerKey("sulfur_boulder");
    public static final RegistryKey<ConfiguredFeature<?, ?>> SOUL_BARNACLE_KEY = registerKey("soul_barnacle_boulder");
    public static final RegistryKey<ConfiguredFeature<?, ?>> LUMINOUS_VEIN_KEY = registerKey("luminous_vein_boulder");
    public static final RegistryKey<ConfiguredFeature<?, ?>> SHELL_PATCH_KEY = registerKey("shell_patch");
    public static final RegistryKey<ConfiguredFeature<?, ?>> SCORCHED_SAND_PATCH_KEY = registerKey("scorched_sand_patch");
    public static final RegistryKey<ConfiguredFeature<?, ?>> GRAVE_SILT_PATCH_KEY = registerKey("grave_silt_patch");
    public static final RegistryKey<ConfiguredFeature<?, ?>> ABYSSAL_SLATE_PATCH_KEY = registerKey("abyssal_slate_patch");

    public static void bootstrap(Registerable<ConfiguredFeature<?, ?>> context) {
        register(context, SPAWN_ISLAND_BUILDINGS_KEY, ModFeatures.SPAWN_ISLAND, DefaultFeatureConfig.INSTANCE);
        for (var t : net.get900.pixelpirates.world.dungeon.Dungeons.ALL) {
            register(context, t.configuredKey(), ModFeatures.DUNGEONS.get(t.id()), DefaultFeatureConfig.INSTANCE);
        }
        register(context, GALLOWS_GROTTO_KEY, net.get900.pixelpirates.world.gen.ModFeatures.GALLOWS_GROTTO, DefaultFeatureConfig.INSTANCE);
        register(context, TITANS_CHEST_KEY, net.get900.pixelpirates.world.gen.ModFeatures.TITANS_CHEST, DefaultFeatureConfig.INSTANCE);
        register(context, LEVIATHAN_SITES_KEY, net.get900.pixelpirates.world.gen.ModFeatures.LEVIATHAN_SITES, DefaultFeatureConfig.INSTANCE);

      //  RuleTest stoneReplaceable = new TagMatchRuleTest(BlockTags.STONE_ORE_REPLACEABLES);
       // RuleTest deepslateReplaceable = new TagMatchRuleTest(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
       // RuleTest netherReplaceable = new TagMatchRuleTest(BlockTags.BASE_STONE_NETHER);
       // RuleTest endReplaceable = new BlockMatchRuleTest(Blocks.END_STONE);

        //Phase 1
        register(context, DRIFTWOOD_BLOCK_KEY, Feature.BLOCK_PILE, new BlockPileFeatureConfig(BlockStateProvider.of(ModBlocks.DRIFTWOOD_BLOCK)));
        // TREE VARIANTS (2026-10-01, "they all look the same"): every signature tree key is now a random pick of three
        // shapes (variants(...)). Placed-feature keys are unchanged, so the feature-order rule is untouched.
        var shoreLog = BlockStateProvider.of(ModBlocks.SHOREWOOD_LOG);
        var shoreLeaves = BlockStateProvider.of(ModBlocks.SHOREWOOD_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
        List<net.minecraft.world.gen.treedecorator.TreeDecorator> fruit =
                List.of(new BananaTreeDecorator(0.06f), new CoconutTreeDecorator(0.04f), new BeehiveTreeDecorator(0.05f));
        context.register((RegistryKey<ConfiguredFeature<?, ?>>)(Object) SHOREWOOD_TREE_KEY, variants(
                new TreeFeatureConfig.Builder(shoreLog, new StraightTrunkPlacer(5, 2, 1), shoreLeaves,
                        new BlobFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(0), 3),
                        new TwoLayersFeatureSize(1, 0, 1)).decorators(fruit).ignoreVines().build(),
                new TreeFeatureConfig.Builder(shoreLog, new LargeOakTrunkPlacer(8, 4, 0), shoreLeaves,          // great shorewood
                        new LargeOakFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(4), 4),
                        new TwoLayersFeatureSize(0, 0, 0, java.util.OptionalInt.of(4))).decorators(fruit).ignoreVines().build(),
                new TreeFeatureConfig.Builder(shoreLog, new StraightTrunkPlacer(3, 1, 0), shoreLeaves,         // squat and wide
                        new BlobFoliagePlacer(ConstantIntProvider.create(3), ConstantIntProvider.create(0), 2),
                        new TwoLayersFeatureSize(1, 0, 1)).decorators(fruit).ignoreVines().build()));

        //Phase 3
        TreeFeatureConfig ashenTreeConfig = new TreeFeatureConfig.Builder(
                BlockStateProvider.of(ModBlocks.ASHEN_LOG),
                new StraightTrunkPlacer(8, 2, 1),
                BlockStateProvider.of(ModBlocks.SHOREWOOD_LEAVES.getDefaultState().with(Properties.PERSISTENT, true)),
                new BlobFoliagePlacer(ConstantIntProvider.create(0), ConstantIntProvider.create(0), 0),
                new TwoLayersFeatureSize(0, 0, 0))
                .ignoreVines().build();
        context.register((RegistryKey<ConfiguredFeature<?, ?>>)(Object) ASHEN_TREE_KEY,
                new ConfiguredFeature<>(Feature.TREE, ashenTreeConfig));

        // Burning tree — volcanic scorched trunk, no foliage, tall
        TreeFeatureConfig burningTreeConfig = new TreeFeatureConfig.Builder(
                BlockStateProvider.of(ModBlocks.BURNING_LOG),
                new StraightTrunkPlacer(6, 3, 2),
                BlockStateProvider.of(ModBlocks.SHOREWOOD_LEAVES.getDefaultState().with(Properties.PERSISTENT, true)),
                new BlobFoliagePlacer(ConstantIntProvider.create(0), ConstantIntProvider.create(0), 0),
                new TwoLayersFeatureSize(0, 0, 0))
                .ignoreVines().build();
        context.register((RegistryKey<ConfiguredFeature<?, ?>>)(Object) BURNING_TREE_KEY,
                new ConfiguredFeature<>(Feature.TREE, burningTreeConfig));

        // Shrouded tree — ghost zone, very tall bare haunted trunk
        TreeFeatureConfig shroudedTreeConfig = new TreeFeatureConfig.Builder(
                BlockStateProvider.of(ModBlocks.SHROUDED_LOG),
                new StraightTrunkPlacer(9, 4, 1),
                BlockStateProvider.of(ModBlocks.SHOREWOOD_LEAVES.getDefaultState().with(Properties.PERSISTENT, true)),
                new BlobFoliagePlacer(ConstantIntProvider.create(0), ConstantIntProvider.create(0), 0),
                new TwoLayersFeatureSize(0, 0, 0))
                .ignoreVines().build();
        context.register((RegistryKey<ConfiguredFeature<?, ?>>)(Object) SHROUDED_TREE_KEY,
                new ConfiguredFeature<>(Feature.TREE, shroudedTreeConfig));

        // Ethereal tree — glowing abyss trunk crowned with pulsing stone
        TreeFeatureConfig etherealTreeConfig = new TreeFeatureConfig.Builder(
                BlockStateProvider.of(ModBlocks.ETHEREAL_LOG),
                new StraightTrunkPlacer(5, 2, 1),
                BlockStateProvider.of(ModBlocks.ETHEREAL_PULSING_STONE.getDefaultState()),
                new BlobFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(0), 3),
                new TwoLayersFeatureSize(1, 0, 1))
                .ignoreVines().build();
        context.register((RegistryKey<ConfiguredFeature<?, ?>>)(Object) ETHEREAL_TREE_KEY,
                new ConfiguredFeature<>(Feature.TREE, etherealTreeConfig));

        // ===== Phase trees v2 — every phase gets a signature tree with a real canopy =====
        // Phase 1: tall bare trunk, high tight canopy — reads as a palm on the beaches
        var palmLog = BlockStateProvider.of(ModBlocks.PALM_LOG);
        var palmLeaves = BlockStateProvider.of(ModBlocks.PALM_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
        List<net.minecraft.world.gen.treedecorator.TreeDecorator> coconuts = List.of(new CoconutTreeDecorator(0.08f));
        context.register(PALM_TREE_KEY, variants(
                new TreeFeatureConfig.Builder(palmLog, new StraightTrunkPlacer(6, 3, 0), palmLeaves,
                        new BlobFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(0), 2),
                        new TwoLayersFeatureSize(1, 0, 1)).decorators(coconuts).ignoreVines().build(),
                new TreeFeatureConfig.Builder(palmLog, new BendingTrunkPlacer(6, 2, 1, 4, UniformIntProvider.create(1, 2)), palmLeaves,   // curved
                        new AcaciaFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(0)),
                        new TwoLayersFeatureSize(1, 0, 1)).decorators(coconuts).ignoreVines().build(),
                new TreeFeatureConfig.Builder(palmLog, new BendingTrunkPlacer(4, 1, 1, 3, ConstantIntProvider.create(1)), palmLeaves,     // leaning
                        new RandomSpreadFoliagePlacer(ConstantIntProvider.create(3), ConstantIntProvider.create(0), ConstantIntProvider.create(2), 40),
                        new TwoLayersFeatureSize(1, 0, 1)).decorators(coconuts).ignoreVines().build()));
        // Phase 2: forked trunk with a flat acacia-style canopy — windswept reef tree
        var tideLog = BlockStateProvider.of(ModBlocks.TIDEWOOD_LOG);
        var tideLeaves = BlockStateProvider.of(ModBlocks.TIDEWOOD_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
        context.register(TIDEWOOD_TREE_KEY, variants(
                new TreeFeatureConfig.Builder(tideLog, new ForkingTrunkPlacer(5, 2, 2), tideLeaves,
                        new AcaciaFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(0)),
                        new TwoLayersFeatureSize(1, 0, 2)).ignoreVines().build(),
                new TreeFeatureConfig.Builder(tideLog, new ForkingTrunkPlacer(7, 2, 3), tideLeaves,              // big windswept
                        new AcaciaFoliagePlacer(ConstantIntProvider.create(3), ConstantIntProvider.create(0)),
                        new TwoLayersFeatureSize(1, 0, 2)).ignoreVines().build(),
                new TreeFeatureConfig.Builder(tideLog, new StraightTrunkPlacer(3, 1, 0), tideLeaves,             // low reef bush
                        new BushFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(1), 2),
                        new TwoLayersFeatureSize(0, 0, 0)).ignoreVines().build()));
        // Phase 3: charred trunk crowned in glowing embers
        var cinderLog = BlockStateProvider.of(ModBlocks.CHARRED_LOG);
        var cinderLeaves = BlockStateProvider.of(ModBlocks.EMBER_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
        context.register(CINDER_TREE_KEY, variants(
                new TreeFeatureConfig.Builder(cinderLog, new StraightTrunkPlacer(5, 2, 1), cinderLeaves,
                        new BlobFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(0), 3),
                        new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build(),
                new TreeFeatureConfig.Builder(cinderLog, new BendingTrunkPlacer(5, 2, 1, 3, UniformIntProvider.create(1, 2)), cinderLeaves,  // heat-bent
                        new BlobFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(0), 2),
                        new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build(),
                new TreeFeatureConfig.Builder(cinderLog, new LargeOakTrunkPlacer(7, 3, 0), cinderLeaves,            // tall and sparse
                        new LargeOakFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(4), 3),
                        new TwoLayersFeatureSize(0, 0, 0, java.util.OptionalInt.of(4))).ignoreVines().build()));
        // Phase 4: tall pale trunk with faint glowing wisp foliage
        var wispLog = BlockStateProvider.of(ModBlocks.WISPWOOD_LOG);
        var wispLeaves = BlockStateProvider.of(ModBlocks.WISP_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
        context.register(WISPWOOD_TREE_KEY, variants(
                new TreeFeatureConfig.Builder(wispLog, new StraightTrunkPlacer(7, 3, 1), wispLeaves,
                        new BlobFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(1), 3),
                        new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build(),
                new TreeFeatureConfig.Builder(wispLog, new StraightTrunkPlacer(9, 3, 2), wispLeaves,             // ghostly spire
                        new SpruceFoliagePlacer(UniformIntProvider.create(2, 3), UniformIntProvider.create(0, 1), UniformIntProvider.create(3, 5)),
                        new TwoLayersFeatureSize(2, 0, 2)).ignoreVines().build(),
                new TreeFeatureConfig.Builder(wispLog, new BendingTrunkPlacer(6, 2, 2, 4, UniformIntProvider.create(1, 3)), wispLeaves,   // weeping
                        new RandomSpreadFoliagePlacer(ConstantIntProvider.create(3), ConstantIntProvider.create(0), ConstantIntProvider.create(3), 60),
                        new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build()));
        // Phase 5: short alien tree glowing violet in the abyss
        var voidLog = BlockStateProvider.of(ModBlocks.VOIDBLOOM_LOG);
        var voidLeaves = BlockStateProvider.of(ModBlocks.VOIDBLOOM_LEAVES.getDefaultState().with(Properties.PERSISTENT, true));
        context.register(VOIDBLOOM_TREE_KEY, variants(
                new TreeFeatureConfig.Builder(voidLog, new StraightTrunkPlacer(4, 2, 0), voidLeaves,
                        new BlobFoliagePlacer(ConstantIntProvider.create(2), ConstantIntProvider.create(0), 3),
                        new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build(),
                new TreeFeatureConfig.Builder(voidLog, new CherryTrunkPlacer(6, 1, 0, ConstantIntProvider.create(2),          // branching bloom
                        UniformIntProvider.create(2, 4), UniformIntProvider.create(-4, -3), UniformIntProvider.create(-1, 0)), voidLeaves,
                        new CherryFoliagePlacer(ConstantIntProvider.create(4), ConstantIntProvider.create(0), ConstantIntProvider.create(5),
                                0.25f, 0.5f, 0.17f, 0.33f),
                        new TwoLayersFeatureSize(1, 0, 2)).ignoreVines().build(),
                new TreeFeatureConfig.Builder(voidLog, new StraightTrunkPlacer(3, 1, 0), voidLeaves,             // mushroom cap
                        new AcaciaFoliagePlacer(ConstantIntProvider.create(3), ConstantIntProvider.create(0)),
                        new TwoLayersFeatureSize(1, 0, 1)).ignoreVines().build()));

        // ===== Ambient decoration — boulders =====
        register(context, TIDE_POOL_ROCK_KEY, Feature.FOREST_ROCK,
                new SingleStateFeatureConfig(ModBlocks.TIDE_POOL_ROCK.getDefaultState()));
        register(context, CORAL_ROCK_KEY, Feature.FOREST_ROCK,
                new SingleStateFeatureConfig(ModBlocks.CORAL_ROCK.getDefaultState()));
        register(context, PEARL_ROCK_KEY, Feature.FOREST_ROCK,
                new SingleStateFeatureConfig(ModBlocks.PEARL_BLOCK.getDefaultState()));
        register(context, SULFUR_BOULDER_KEY, Feature.FOREST_ROCK,
                new SingleStateFeatureConfig(ModBlocks.SULFUR_BLOCK.getDefaultState()));
        register(context, SOUL_BARNACLE_KEY, Feature.FOREST_ROCK,
                new SingleStateFeatureConfig(ModBlocks.SOUL_BARNACLE.getDefaultState()));
        register(context, LUMINOUS_VEIN_KEY, Feature.FOREST_ROCK,
                new SingleStateFeatureConfig(ModBlocks.LUMINOUS_VEIN.getDefaultState()));

        // ===== Ambient decoration — seabed / surface patches =====
        List<Block> patchTargets = List.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.SAND, Blocks.GRAVEL, Blocks.STONE);
        register(context, SHELL_PATCH_KEY, Feature.DISK, new DiskFeatureConfig(
                PredicatedStateProvider.of(ModBlocks.SHELL_BLOCK),
                BlockPredicate.matchingBlocks(patchTargets),
                UniformIntProvider.create(2, 4), 2));
        register(context, SCORCHED_SAND_PATCH_KEY, Feature.DISK, new DiskFeatureConfig(
                PredicatedStateProvider.of(ModBlocks.SCORCHED_SAND),
                BlockPredicate.matchingBlocks(patchTargets),
                UniformIntProvider.create(2, 5), 2));
        register(context, GRAVE_SILT_PATCH_KEY, Feature.DISK, new DiskFeatureConfig(
                PredicatedStateProvider.of(ModBlocks.GRAVE_SILT),
                BlockPredicate.matchingBlocks(patchTargets),
                UniformIntProvider.create(2, 5), 2));
        register(context, ABYSSAL_SLATE_PATCH_KEY, Feature.DISK, new DiskFeatureConfig(
                PredicatedStateProvider.of(ModBlocks.ABYSSAL_SLATE),
                BlockPredicate.matchingBlocks(patchTargets),
                UniformIntProvider.create(2, 5), 2));
    }

    //TemperateShallows
    @SuppressWarnings("unchecked")
    /** A random pick of tree shapes (equal odds) - the tree variants. */
    private static ConfiguredFeature<?, ?> variants(TreeFeatureConfig... shapes) {
        List<net.minecraft.registry.entry.RegistryEntry<net.minecraft.world.gen.feature.PlacedFeature>> entries = new java.util.ArrayList<>();
        for (TreeFeatureConfig c : shapes) entries.add(net.minecraft.world.gen.feature.PlacedFeatures.createEntry(Feature.TREE, c));
        return new ConfiguredFeature<>(Feature.SIMPLE_RANDOM_SELECTOR,
                new net.minecraft.world.gen.feature.SimpleRandomFeatureConfig(net.minecraft.registry.entry.RegistryEntryList.of(entries)));
    }

    public static final RegistryKey<ConfiguredFeature<TreeFeatureConfig, ?>> SHOREWOOD_TREE_KEY =
            (RegistryKey<ConfiguredFeature<TreeFeatureConfig, ?>>)(Object)
                    RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE,
                            new Identifier(PixelPirates.MOD_ID,"shorewood_tree"));

    //Phase 3
    public static final RegistryKey<ConfiguredFeature<TreeFeatureConfig, ?>> ASHEN_TREE_KEY =
            (RegistryKey<ConfiguredFeature<TreeFeatureConfig, ?>>)(Object)
                    RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE,
                            new Identifier(PixelPirates.MOD_ID,"ashen_tree"));

    public static final RegistryKey<ConfiguredFeature<TreeFeatureConfig, ?>> BURNING_TREE_KEY =
            (RegistryKey<ConfiguredFeature<TreeFeatureConfig, ?>>)(Object)
                    RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE,
                            new Identifier(PixelPirates.MOD_ID,"burning_tree"));

    //Phase 4
    public static final RegistryKey<ConfiguredFeature<TreeFeatureConfig, ?>> SHROUDED_TREE_KEY =
            (RegistryKey<ConfiguredFeature<TreeFeatureConfig, ?>>)(Object)
                    RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE,
                            new Identifier(PixelPirates.MOD_ID,"shrouded_tree"));

    //Phase 5
    public static final RegistryKey<ConfiguredFeature<TreeFeatureConfig, ?>> ETHEREAL_TREE_KEY =
            (RegistryKey<ConfiguredFeature<TreeFeatureConfig, ?>>)(Object)
                    RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE,
                            new Identifier(PixelPirates.MOD_ID,"ethereal_tree"));
    //Helpers

    public static RegistryKey<ConfiguredFeature<?, ?>> registerKey(String name) {
        return RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, new Identifier(PixelPirates.MOD_ID,name));
    }

    private static <FC extends FeatureConfig, F extends Feature<FC>> void register(Registerable<ConfiguredFeature<?, ?>> context,
                                                                                   RegistryKey<ConfiguredFeature<?, ?>> key, F feature, FC configuration) {
        context.register(key, new ConfiguredFeature<>(feature, configuration));
    }
}
