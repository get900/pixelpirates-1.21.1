package net.get900.pixelpirates.world;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.feature.*;
import net.minecraft.world.gen.placementmodifier.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ModPlacedFeatures {

    public static final RegistryKey<PlacedFeature> DRIFTWOOD_BLOCK_PLACED_KEY =
            registerKey("driftwood_block_placed");
    public static final RegistryKey<PlacedFeature> SPAWN_ISLAND_BUILDINGS_PLACED_KEY =
            registerKey("spawn_island_buildings_placed");
    public static final RegistryKey<PlacedFeature> GALLOWS_GROTTO_PLACED_KEY = registerKey("gallows_grotto_placed");
    public static final RegistryKey<PlacedFeature> TITANS_CHEST_PLACED_KEY = registerKey("titans_chest_placed");
    public static final RegistryKey<PlacedFeature> LEVIATHAN_SITES_PLACED_KEY = registerKey("leviathan_sites_placed");
    public static final RegistryKey<PlacedFeature> SHOREWOOD_TREE_PLACED_KEY =
            registerKey("shorewood_tree_placed");
    public static final RegistryKey<PlacedFeature> ASHEN_TREE_PLACED_KEY =
            registerKey("ashen_tree_placed");
    public static final RegistryKey<PlacedFeature> BURNING_TREE_PLACED_KEY =
            registerKey("burning_tree_placed");
    public static final RegistryKey<PlacedFeature> SHROUDED_TREE_PLACED_KEY =
            registerKey("shrouded_tree_placed");
    public static final RegistryKey<PlacedFeature> ETHEREAL_TREE_PLACED_KEY =
            registerKey("ethereal_tree_placed");

    // Phase trees v2
    public static final RegistryKey<PlacedFeature> PALM_TREE_PLACED_KEY = registerKey("palm_tree_placed");
    public static final RegistryKey<PlacedFeature> TIDEWOOD_TREE_PLACED_KEY = registerKey("tidewood_tree_placed");
    public static final RegistryKey<PlacedFeature> CINDER_TREE_PLACED_KEY = registerKey("cinder_tree_placed");
    public static final RegistryKey<PlacedFeature> WISPWOOD_TREE_PLACED_KEY = registerKey("wispwood_tree_placed");
    public static final RegistryKey<PlacedFeature> VOIDBLOOM_TREE_PLACED_KEY = registerKey("voidbloom_tree_placed");

    // Ambient decoration
    public static final RegistryKey<PlacedFeature> TIDE_POOL_ROCK_PLACED_KEY = registerKey("tide_pool_rock_placed");
    public static final RegistryKey<PlacedFeature> CORAL_ROCK_PLACED_KEY = registerKey("coral_rock_placed");
    public static final RegistryKey<PlacedFeature> PEARL_ROCK_PLACED_KEY = registerKey("pearl_boulder_placed");
    public static final RegistryKey<PlacedFeature> SULFUR_BOULDER_PLACED_KEY = registerKey("sulfur_boulder_placed");
    public static final RegistryKey<PlacedFeature> SOUL_BARNACLE_PLACED_KEY = registerKey("soul_barnacle_placed");
    public static final RegistryKey<PlacedFeature> LUMINOUS_VEIN_PLACED_KEY = registerKey("luminous_vein_placed");
    public static final RegistryKey<PlacedFeature> SHELL_PATCH_PLACED_KEY = registerKey("shell_patch_placed");
    public static final RegistryKey<PlacedFeature> SCORCHED_SAND_PATCH_PLACED_KEY = registerKey("scorched_sand_patch_placed");
    public static final RegistryKey<PlacedFeature> GRAVE_SILT_PATCH_PLACED_KEY = registerKey("grave_silt_patch_placed");
    public static final RegistryKey<PlacedFeature> ABYSSAL_SLATE_PATCH_PLACED_KEY = registerKey("abyssal_slate_patch_placed");


    public static void bootstrap(Registerable<PlacedFeature> context) {
        var configuredFeatures = context.getRegistryLookup(RegistryKeys.CONFIGURED_FEATURE);

        // Spawn island buildings — runs once per chunk, checks internally for origin chunk
        register(context, SPAWN_ISLAND_BUILDINGS_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.SPAWN_ISLAND_BUILDINGS_KEY),
                List.of(CountPlacementModifier.of(1)));

        // Dungeons: runs in every chunk of a listed biome; DungeonFeature itself admits only the one
        // chosen chunk per grid cell (DungeonPlacement spacing), so no rarity/biome modifiers here.
        for (var t : net.get900.pixelpirates.world.dungeon.Dungeons.ALL) {
            register(context, t.placedKey(), configuredFeatures.getOrThrow(t.configuredKey()),
                    List.of(HeightmapPlacementModifier.of(Heightmap.Type.OCEAN_FLOOR_WG)));
        }
        // the Gallows Grotto: once per chunk in every biome; the feature itself renders only chunks in a footprint
        register(context, GALLOWS_GROTTO_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.GALLOWS_GROTTO_KEY),
                List.of(CountPlacementModifier.of(1)));
        register(context, TITANS_CHEST_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.TITANS_CHEST_KEY),
                List.of(CountPlacementModifier.of(1)));
        register(context, LEVIATHAN_SITES_PLACED_KEY, configuredFeatures.getOrThrow(ModConfiguredFeatures.LEVIATHAN_SITES_KEY),
                List.of(CountPlacementModifier.of(1)));

        //Phase 1
        register(context, DRIFTWOOD_BLOCK_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.DRIFTWOOD_BLOCK_KEY), modifiersWithCount(1,
                        SquarePlacementModifier.of(), HeightmapPlacementModifier.of(Heightmap.Type.WORLD_SURFACE), BiomePlacementModifier.of()));
        register(context, SHOREWOOD_TREE_PLACED_KEY,
                configuredFeatures.getOrThrow((RegistryKey<ConfiguredFeature<?, ?>>)(Object) ModConfiguredFeatures.SHOREWOOD_TREE_KEY), List.of(
                        CountPlacementModifier.of(5), SquarePlacementModifier.of(), SurfaceWaterDepthFilterPlacementModifier.of(0), // ✅ Prevent placement in water
                        HeightmapPlacementModifier.of(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES),
                        BiomePlacementModifier.of()));

        //Phase 3
        register(context, ASHEN_TREE_PLACED_KEY,
                configuredFeatures.getOrThrow((RegistryKey<ConfiguredFeature<?, ?>>)(Object) ModConfiguredFeatures.ASHEN_TREE_KEY), List.of(
                        CountPlacementModifier.of(5), SquarePlacementModifier.of(), SurfaceWaterDepthFilterPlacementModifier.of(0),
                        HeightmapPlacementModifier.of(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES),
                        BiomePlacementModifier.of()));

        register(context, BURNING_TREE_PLACED_KEY,
                configuredFeatures.getOrThrow((RegistryKey<ConfiguredFeature<?, ?>>)(Object) ModConfiguredFeatures.BURNING_TREE_KEY), List.of(
                        CountPlacementModifier.of(4), SquarePlacementModifier.of(), SurfaceWaterDepthFilterPlacementModifier.of(0),
                        HeightmapPlacementModifier.of(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES),
                        BiomePlacementModifier.of()));

        //Phase 4
        register(context, SHROUDED_TREE_PLACED_KEY,
                configuredFeatures.getOrThrow((RegistryKey<ConfiguredFeature<?, ?>>)(Object) ModConfiguredFeatures.SHROUDED_TREE_KEY), List.of(
                        CountPlacementModifier.of(3), SquarePlacementModifier.of(), SurfaceWaterDepthFilterPlacementModifier.of(0),
                        HeightmapPlacementModifier.of(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES),
                        BiomePlacementModifier.of()));

        //Phase 5
        register(context, ETHEREAL_TREE_PLACED_KEY,
                configuredFeatures.getOrThrow((RegistryKey<ConfiguredFeature<?, ?>>)(Object) ModConfiguredFeatures.ETHEREAL_TREE_KEY), List.of(
                        CountPlacementModifier.of(3), SquarePlacementModifier.of(), SurfaceWaterDepthFilterPlacementModifier.of(0),
                        HeightmapPlacementModifier.of(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES),
                        BiomePlacementModifier.of()));

        // ===== Phase trees v2 =====
        register(context, PALM_TREE_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.PALM_TREE_KEY), treeModifiers(2));
        register(context, TIDEWOOD_TREE_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.TIDEWOOD_TREE_KEY), treeModifiers(3));
        register(context, CINDER_TREE_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.CINDER_TREE_KEY), treeModifiers(3));
        register(context, WISPWOOD_TREE_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.WISPWOOD_TREE_KEY), treeModifiers(2));
        register(context, VOIDBLOOM_TREE_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.VOIDBLOOM_TREE_KEY), treeModifiers(2));

        // ===== Ambient decoration — boulders sit on the solid floor (works on land and seabed) =====
        register(context, TIDE_POOL_ROCK_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.TIDE_POOL_ROCK_KEY), boulderModifiers(2));
        register(context, CORAL_ROCK_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.CORAL_ROCK_KEY), List.of(
                        CountPlacementModifier.of(2), SquarePlacementModifier.of(),
                        HeightmapPlacementModifier.of(Heightmap.Type.OCEAN_FLOOR_WG), BiomePlacementModifier.of()));
        register(context, PEARL_ROCK_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.PEARL_ROCK_KEY), boulderModifiers(4));
        register(context, SULFUR_BOULDER_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.SULFUR_BOULDER_KEY), boulderModifiers(2));
        register(context, SOUL_BARNACLE_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.SOUL_BARNACLE_KEY), boulderModifiers(2));
        register(context, LUMINOUS_VEIN_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.LUMINOUS_VEIN_KEY), boulderModifiers(2));

        // ===== Ambient decoration — ground patches =====
        register(context, SHELL_PATCH_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.SHELL_PATCH_KEY), patchModifiers(2));
        register(context, SCORCHED_SAND_PATCH_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.SCORCHED_SAND_PATCH_KEY), patchModifiers(2));
        register(context, GRAVE_SILT_PATCH_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.GRAVE_SILT_PATCH_KEY), patchModifiers(2));
        register(context, ABYSSAL_SLATE_PATCH_PLACED_KEY,
                configuredFeatures.getOrThrow(ModConfiguredFeatures.ABYSSAL_SLATE_PATCH_KEY), patchModifiers(2));
    }

    /** Standard tree placement used by every phase tree. */
    private static List<PlacementModifier> treeModifiers(int count) {
        return List.of(CountPlacementModifier.of(count), SquarePlacementModifier.of(),
                SurfaceWaterDepthFilterPlacementModifier.of(0),
                HeightmapPlacementModifier.of(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES),
                BiomePlacementModifier.of());
    }

    /** Boulders — roughly one per {@code rarity} chunks, snapped to the solid floor. */
    private static List<PlacementModifier> boulderModifiers(int rarity) {
        return List.of(RarityFilterPlacementModifier.of(rarity), SquarePlacementModifier.of(),
                HeightmapPlacementModifier.of(Heightmap.Type.OCEAN_FLOOR_WG),
                BiomePlacementModifier.of());
    }

    /** Disk patches replacing dirt/sand/gravel/stone at the solid floor. */
    private static List<PlacementModifier> patchModifiers(int count) {
        return List.of(CountPlacementModifier.of(count), SquarePlacementModifier.of(),
                HeightmapPlacementModifier.of(Heightmap.Type.OCEAN_FLOOR_WG),
                BiomePlacementModifier.of());
    }

    public static RegistryKey<PlacedFeature> registerKey(String name) {
        return RegistryKey.of(RegistryKeys.PLACED_FEATURE, new Identifier(PixelPirates.MOD_ID,name));
    }

    public static List<PlacementModifier> modifiersWithCount(int count, PlacementModifier... modifiers) {
        List<PlacementModifier> list = new ArrayList<>();
        list.add(CountPlacementModifier.of(count));
        Collections.addAll(list, modifiers);
        return list;
    }

    private static void register(Registerable<PlacedFeature> context, RegistryKey<PlacedFeature> key, RegistryEntry<ConfiguredFeature<?, ?>> configuration,
                                 List<PlacementModifier> modifiers) {
        context.register(key, new PlacedFeature(configuration, List.copyOf(modifiers)));
    }

    private static <FC extends FeatureConfig, F extends Feature<FC>> void register(Registerable<PlacedFeature> context, RegistryKey<PlacedFeature> key,
                                                                                   RegistryEntry<ConfiguredFeature<?, ?>> configuration,
                                                                                   PlacementModifier... modifiers) {
        register(context, key, configuration, List.of(modifiers));
    }
}
