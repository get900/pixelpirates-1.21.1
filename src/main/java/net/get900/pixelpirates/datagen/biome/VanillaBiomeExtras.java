package net.get900.pixelpirates.datagen.biome;

import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.MiscPlacedFeatures;
import net.minecraft.world.gen.feature.OceanPlacedFeatures;
import net.minecraft.world.gen.feature.OrePlacedFeatures;
import net.minecraft.world.gen.feature.PlacedFeature;
import net.minecraft.world.gen.feature.UndergroundPlacedFeatures;
import net.minecraft.world.gen.feature.VegetationPlacedFeatures;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla decoration layered on top of each Pixel Pirates biome - seagrass, kelp, coral,
 * island flora, seabed disks, ores. Before this the biomes carried only the mod's own
 * trees/boulders, so oceans were bare sand and the dimension had no ores at all.
 *
 * FEATURE-ORDER SAFETY: Minecraft crashes ("feature order cycle") if two biomes in one
 * dimension list the same features in a different relative order within a step. Every
 * biome here appends its vanilla extras AFTER its custom features, and always in the
 * single canonical order of {@link #ORDER}. Profiles only choose a subset, never an order,
 * so the invariant holds no matter which profile a biome uses. Add new features to ORDER.
 */
public final class VanillaBiomeExtras {
    private VanillaBiomeExtras() {}

    /** Canonical per-step order. Never reorder an existing entry; append new ones. */
    private static final Map<GenerationStep.Feature, List<RegistryKey<PlacedFeature>>> ORDER = Map.of(
            GenerationStep.Feature.LOCAL_MODIFICATIONS, List.of(
                    UndergroundPlacedFeatures.AMETHYST_GEODE),
            GenerationStep.Feature.UNDERGROUND_ORES, List.of(
                    MiscPlacedFeatures.DISK_SAND, MiscPlacedFeatures.DISK_CLAY, MiscPlacedFeatures.DISK_GRAVEL,
                    OrePlacedFeatures.ORE_DIRT, OrePlacedFeatures.ORE_GRAVEL,
                    OrePlacedFeatures.ORE_GRANITE_UPPER, OrePlacedFeatures.ORE_GRANITE_LOWER,
                    OrePlacedFeatures.ORE_DIORITE_UPPER, OrePlacedFeatures.ORE_DIORITE_LOWER,
                    OrePlacedFeatures.ORE_ANDESITE_UPPER, OrePlacedFeatures.ORE_ANDESITE_LOWER,
                    OrePlacedFeatures.ORE_TUFF,
                    OrePlacedFeatures.ORE_COAL_UPPER, OrePlacedFeatures.ORE_COAL_LOWER,
                    OrePlacedFeatures.ORE_IRON_UPPER, OrePlacedFeatures.ORE_IRON_MIDDLE, OrePlacedFeatures.ORE_IRON_SMALL,
                    OrePlacedFeatures.ORE_GOLD, OrePlacedFeatures.ORE_GOLD_LOWER, OrePlacedFeatures.ORE_GOLD_EXTRA,
                    OrePlacedFeatures.ORE_REDSTONE, OrePlacedFeatures.ORE_REDSTONE_LOWER,
                    OrePlacedFeatures.ORE_DIAMOND, OrePlacedFeatures.ORE_DIAMOND_LARGE, OrePlacedFeatures.ORE_DIAMOND_BURIED,
                    OrePlacedFeatures.ORE_LAPIS, OrePlacedFeatures.ORE_LAPIS_BURIED,
                    OrePlacedFeatures.ORE_COPPER),
            GenerationStep.Feature.UNDERGROUND_DECORATION, List.of(
                    UndergroundPlacedFeatures.GLOW_LICHEN),
            GenerationStep.Feature.FLUID_SPRINGS, List.of(
                    MiscPlacedFeatures.SPRING_WATER, MiscPlacedFeatures.SPRING_LAVA),
            GenerationStep.Feature.VEGETAL_DECORATION, List.of(
                    VegetationPlacedFeatures.FLOWER_WARM, VegetationPlacedFeatures.FLOWER_DEFAULT,
                    VegetationPlacedFeatures.PATCH_GRASS_JUNGLE, VegetationPlacedFeatures.PATCH_GRASS_NORMAL,
                    VegetationPlacedFeatures.PATCH_GRASS_TAIGA,
                    VegetationPlacedFeatures.PATCH_DEAD_BUSH, VegetationPlacedFeatures.PATCH_SUGAR_CANE,
                    VegetationPlacedFeatures.PATCH_MELON_SPARSE,
                    VegetationPlacedFeatures.BROWN_MUSHROOM_NORMAL, VegetationPlacedFeatures.RED_MUSHROOM_NORMAL,
                    OceanPlacedFeatures.WARM_OCEAN_VEGETATION,
                    OceanPlacedFeatures.SEAGRASS_WARM, OceanPlacedFeatures.SEAGRASS_DEEP_WARM,
                    OceanPlacedFeatures.SEAGRASS_NORMAL, OceanPlacedFeatures.SEAGRASS_DEEP,
                    OceanPlacedFeatures.SEAGRASS_DEEP_COLD,
                    OceanPlacedFeatures.SEA_PICKLE,
                    OceanPlacedFeatures.KELP_WARM, OceanPlacedFeatures.KELP_COLD));

    /** Always-on geology: seabed disks + stone variety. NO ORES (2026-10-01, user decision): mining is not what this mod
     *  is about - iron, gold, copper and coal come from salvage, loot and the port traders instead. ORDER still lists the
     *  ore features so the canonical order never changes; nothing selects them. */
    private static final Set<RegistryKey<PlacedFeature>> BASE = Set.of(
            MiscPlacedFeatures.DISK_SAND, MiscPlacedFeatures.DISK_CLAY, MiscPlacedFeatures.DISK_GRAVEL,
            OrePlacedFeatures.ORE_DIRT, OrePlacedFeatures.ORE_GRAVEL,
            OrePlacedFeatures.ORE_GRANITE_UPPER, OrePlacedFeatures.ORE_GRANITE_LOWER,
            OrePlacedFeatures.ORE_DIORITE_UPPER, OrePlacedFeatures.ORE_DIORITE_LOWER,
            OrePlacedFeatures.ORE_ANDESITE_UPPER, OrePlacedFeatures.ORE_ANDESITE_LOWER,
            OrePlacedFeatures.ORE_TUFF);

    public enum Profile {
        /** Spawn island: gentle tropical flora + warm shallows. */
        HUB(Set.of(VegetationPlacedFeatures.FLOWER_WARM, VegetationPlacedFeatures.PATCH_GRASS_NORMAL,
                VegetationPlacedFeatures.PATCH_SUGAR_CANE, MiscPlacedFeatures.SPRING_WATER,
                OceanPlacedFeatures.SEAGRASS_WARM, OceanPlacedFeatures.KELP_WARM)),
        /** Phase 1 islands and shallows: lush jungle grass, melons, cane, warm seagrass. */
        TROPICAL(Set.of(VegetationPlacedFeatures.FLOWER_WARM, VegetationPlacedFeatures.PATCH_GRASS_JUNGLE,
                VegetationPlacedFeatures.PATCH_SUGAR_CANE, VegetationPlacedFeatures.PATCH_MELON_SPARSE,
                MiscPlacedFeatures.SPRING_WATER,
                OceanPlacedFeatures.SEAGRASS_WARM, OceanPlacedFeatures.SEA_PICKLE, OceanPlacedFeatures.KELP_WARM)),
        /** Deep open water: seagrass meadows and kelp forests. */
        OPEN_SEA(Set.of(OceanPlacedFeatures.SEAGRASS_DEEP, OceanPlacedFeatures.KELP_COLD)),
        /** Phase 2 reefs: vanilla coral, sea pickles, warm seagrass. */
        REEF(Set.of(VegetationPlacedFeatures.FLOWER_WARM, VegetationPlacedFeatures.PATCH_GRASS_NORMAL,
                OceanPlacedFeatures.WARM_OCEAN_VEGETATION, OceanPlacedFeatures.SEAGRASS_DEEP_WARM,
                OceanPlacedFeatures.SEA_PICKLE)),
        /** Phase 3 volcanic: dead brush, lava springs. */
        VOLCANIC(Set.of(VegetationPlacedFeatures.PATCH_DEAD_BUSH, MiscPlacedFeatures.SPRING_LAVA)),
        /** Phase 4 cursed seas: gloomy ferns, mushrooms, cold kelp. */
        CURSED(Set.of(VegetationPlacedFeatures.PATCH_GRASS_TAIGA, VegetationPlacedFeatures.BROWN_MUSHROOM_NORMAL,
                VegetationPlacedFeatures.RED_MUSHROOM_NORMAL, OceanPlacedFeatures.SEAGRASS_DEEP_COLD,
                OceanPlacedFeatures.KELP_COLD)),
        /** Phase 5 abyss: amethyst geodes and glow lichen in the luminous deep. */
        ABYSS(Set.of(UndergroundPlacedFeatures.AMETHYST_GEODE, UndergroundPlacedFeatures.GLOW_LICHEN,
                OceanPlacedFeatures.SEAGRASS_DEEP_COLD, OceanPlacedFeatures.KELP_COLD));

        private final Set<RegistryKey<PlacedFeature>> features;

        Profile(Set<RegistryKey<PlacedFeature>> features) {
            this.features = features;
        }
    }

    /** Call AFTER a biome's own features have been added. */
    public static void add(GenerationSettings.Builder generation, RegistryEntryLookup<PlacedFeature> placed, Profile profile) {
        for (GenerationStep.Feature step : GenerationStep.Feature.values()) {
            List<RegistryKey<PlacedFeature>> order = ORDER.get(step);
            if (order == null) continue;
            for (RegistryKey<PlacedFeature> key : order) {
                if (BASE.contains(key) || profile.features.contains(key)) {
                    generation.feature(step, placed.getOrThrow(key));
                }
            }
        }
    }
}
