package net.get900.pixelpirates.datagen.biome;

import net.get900.pixelpirates.world.biome.ModBiomeKeys;
import net.get900.pixelpirates.world.ModPlacedFeatures;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.sound.BiomeAdditionsSound;
import net.minecraft.sound.BiomeMoodSound;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeEffects;
import net.minecraft.world.biome.BiomeParticleConfig;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.biome.SpawnSettings;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.PlacedFeature;

public class PixelPiratesBiomes {

    /**
     * Every biome spawn goes through here so an entity is always listed under its OWN spawn group.
     * The spawn cap counts mobs by their type's group, so listing e.g. salmon (WATER_AMBIENT) under
     * WATER_CREATURE spawned them past every cap - thousands of salmon in island_thickets and glow
     * squid in maw_depths (fixed 2026-09-29).
     */
    private static void spawn(SpawnSettings.Builder spawns, SpawnSettings.SpawnEntry entry) {
        spawns.spawn(entry.type.getSpawnGroup(), entry);
    }

    // ── Hub ─────────────────────────────────────────────────────────────────

    public static Biome buildSpawnIsland(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildSpawnIsland(placed);
    }

    public static Biome buildSpawnIsland(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        generation.feature(GenerationStep.Feature.SURFACE_STRUCTURES,
                placedFeatures.getOrThrow(ModPlacedFeatures.SPAWN_ISLAND_BUILDINGS_PLACED_KEY));
        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.HUB);
        return new Biome.Builder()
                .precipitation(true)
                .temperature(0.8f)
                .downfall(0.4f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x3EB5F1).waterFogColor(0x50BCFF)
                        .fogColor(0xC0D8FF).skyColor(0x76C4FF)
                        .moodSound(BiomeMoodSound.CAVE)
                        .build())
                .spawnSettings(new SpawnSettings.Builder().build())
                .generationSettings(generation.build())
                .build();
    }

    // ── Phase 1 — Starter Seas ───────────────────────────────────────────────
    // Warm tropical shallows. Gentle, tutorial-friendly, vibrant colour palette.

    public static Biome buildTemperateShallows(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildTemperateShallows(placed);
    }

    public static Biome buildTemperateShallows(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.TEMPERATE_SHALLOWS);
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.SHOREWOOD_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.PALM_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.TIDE_POOL_ROCK_PLACED_KEY));
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.SHELL_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.TROPICAL);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.DOLPHIN, 2, 1, 2));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SALMON, 3, 2, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.COD, 15, 4, 7));

        return new Biome.Builder()
                .precipitation(true)
                .temperature(0.9f)
                .downfall(0.5f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x29C8E8).waterFogColor(0x18B0D4)
                        .fogColor(0xC8EEFF).skyColor(0x7DD4FF)
                        .grassColor(0x5DC93E).foliageColor(0x3DA828)
                        .moodSound(BiomeMoodSound.CAVE)
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    public static Biome buildIslandThickets(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildIslandThickets(placed);
    }

    public static Biome buildIslandThickets(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.ISLAND_THICKETS);
        // Dense shorewood forest with coconuts and bananas, palms along the sand
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.SHOREWOOD_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.PALM_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.SHELL_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.TROPICAL);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.COD, 4, 2, 4));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SALMON, 3, 2, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.PARROT, 4, 1, 2));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.CHICKEN, 6, 2, 4));

        return new Biome.Builder()
                .precipitation(true)
                .temperature(0.95f)
                .downfall(0.7f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x2DB87A).waterFogColor(0x1A9A60)
                        .fogColor(0xC8EDD8).skyColor(0x78E0B0)
                        .grassColor(0x32D44A).foliageColor(0x28B838)
                        .moodSound(BiomeMoodSound.CAVE)
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    public static Biome buildOpenOcean(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildOpenOcean(placed);
    }

    public static Biome buildOpenOcean(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.OPEN_OCEAN);
        // Scattered shell beds on the sea floor
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.SHELL_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.OPEN_SEA);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SQUID, 4, 1, 4));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.DOLPHIN, 3, 1, 2));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.COD, 12, 3, 6));

        return new Biome.Builder()
                .precipitation(true)
                .temperature(0.5f)
                .downfall(0.3f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x1F6BD4).waterFogColor(0x0E50A8)
                        .fogColor(0xA8C8F0).skyColor(0x6AACFF)
                        .moodSound(BiomeMoodSound.CAVE)
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    // ── Phase 2 — Merchant Seas ──────────────────────────────────────────────
    // Deeper, richer. Coral reefs, trading routes, first encounters with danger.

    public static Biome buildCoralBay(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildCoralBay(placed);
    }

    public static Biome buildCoralBay(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.CORAL_BAY);
        // Windswept tidewood trees on the islets, coral heads and rare pearl boulders below
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.TIDEWOOD_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.CORAL_ROCK_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.PEARL_ROCK_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.REEF);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SQUID, 4, 1, 4));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.TROPICAL_FISH, 25, 8, 8));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.PUFFERFISH, 5, 1, 3));

        return new Biome.Builder()
                .precipitation(true)
                .temperature(0.6f)
                .downfall(0.1f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x0079BF).waterFogColor(0x005A8F)
                        .fogColor(0x88C4D9).skyColor(0x4EB8D9)
                        .moodSound(BiomeMoodSound.CAVE)
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    public static Biome buildReefEdge(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildReefEdge(placed);
    }

    public static Biome buildReefEdge(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.REEF_EDGE);
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.TIDEWOOD_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.CORAL_ROCK_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.PEARL_ROCK_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.REEF);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SALMON, 3, 2, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.TROPICAL_FISH, 20, 5, 8));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.PUFFERFISH, 8, 1, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.DROWNED, 3, 1, 2));

        return new Biome.Builder()
                .precipitation(true)
                .temperature(0.6f)
                .downfall(0.1f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x0D7CC2).waterFogColor(0x0A5C91)
                        .fogColor(0x9ECFE0).skyColor(0x5EC5E0)
                        .moodSound(BiomeMoodSound.CAVE)
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    public static Biome buildSirenSea(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildSirenSea(placed);
    }

    public static Biome buildSirenSea(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.SIREN_SEA);
        // Drowned tidewood stands — the sirens' hunting perches
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.TIDEWOOD_TREE_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.REEF);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SQUID, 3, 1, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.COD, 8, 2, 5));
        // Sirens have lured sailors here — drowned roam these waters
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.DROWNED, 15, 2, 4));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SKELETON, 5, 1, 2));

        return new Biome.Builder()
                .precipitation(true)
                .temperature(0.5f)
                .downfall(0.1f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x1F4E8A).waterFogColor(0x0E2F57)
                        .fogColor(0x1E3560).skyColor(0x1A3E8F)
                        .moodSound(new BiomeMoodSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 6000, 8, 2.0))
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    // ── Phase 3 — Volcanic / Pirate Seas ────────────────────────────────────
    // Ash, lava, death. Scorched islands with burning trees. Fire-hardened foes.

    public static Biome buildAshReef(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildAshReef(placed);
    }

    public static Biome buildAshReef(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.ASH_REEF);
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.ASHEN_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.CINDER_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.SULFUR_BOULDER_PLACED_KEY));
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.SCORCHED_SAND_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.VOLCANIC);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SKELETON, 50, 2, 4));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.ZOMBIE, 30, 2, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.MAGMA_CUBE, 15, 1, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SQUID, 2, 1, 2));

        return new Biome.Builder()
                .precipitation(false)
                .temperature(1.2f)
                .downfall(0.0f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x6E3B3B).waterFogColor(0x4A1E1E)
                        .fogColor(0x4A2A2A).skyColor(0x7A4040)
                        .grassColor(0x8B5E2A).foliageColor(0x6B4010)
                        .particleConfig(new BiomeParticleConfig(ParticleTypes.ASH, 0.00118093f))
                        .moodSound(new BiomeMoodSound(SoundEvents.AMBIENT_BASALT_DELTAS_MOOD, 6000, 8, 2.0))
                        .loopSound(SoundEvents.AMBIENT_BASALT_DELTAS_LOOP)
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    public static Biome buildBoilingBasin(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildBoilingBasin(placed);
    }

    public static Biome buildBoilingBasin(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.BOILING_BASIN);
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.BURNING_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.CINDER_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.SULFUR_BOULDER_PLACED_KEY));
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.SCORCHED_SAND_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.VOLCANIC);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SKELETON, 30, 1, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.MAGMA_CUBE, 30, 2, 4));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.ZOMBIE, 20, 2, 3));

        return new Biome.Builder()
                .precipitation(false)
                .temperature(1.5f)
                .downfall(0.0f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0xA03020).waterFogColor(0x6E1510)
                        .fogColor(0x4D1515).skyColor(0x7A1515)
                        .grassColor(0x6B3010).foliageColor(0x4A1A05)
                        .particleConfig(new BiomeParticleConfig(ParticleTypes.ASH, 0.00625f))
                        .moodSound(new BiomeMoodSound(SoundEvents.AMBIENT_BASALT_DELTAS_MOOD, 6000, 8, 2.0))
                        .loopSound(SoundEvents.AMBIENT_BASALT_DELTAS_LOOP)
                        .additionsSound(new BiomeAdditionsSound(SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS, 0.0111))
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    public static Biome buildMagmaSea(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildMagmaSea(placed);
    }

    public static Biome buildMagmaSea(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.MAGMA_SEA);
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.BURNING_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.CINDER_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.SULFUR_BOULDER_PLACED_KEY));
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.SCORCHED_SAND_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.VOLCANIC);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.MAGMA_CUBE, 40, 3, 5));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SKELETON, 30, 1, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.ZOMBIE, 15, 1, 2));

        return new Biome.Builder()
                .precipitation(false)
                .temperature(1.8f)
                .downfall(0.0f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0xC04010).waterFogColor(0x8A2000)
                        .fogColor(0x5A1500).skyColor(0x8A2800)
                        .grassColor(0x5A2005).foliageColor(0x380D00)
                        .particleConfig(new BiomeParticleConfig(ParticleTypes.CRIMSON_SPORE, 0.00825f))
                        .moodSound(new BiomeMoodSound(SoundEvents.AMBIENT_BASALT_DELTAS_MOOD, 6000, 8, 2.0))
                        .loopSound(SoundEvents.AMBIENT_BASALT_DELTAS_LOOP)
                        .additionsSound(new BiomeAdditionsSound(SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS, 0.0222))
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    // ── Phase 4 — Cursed / Ghost Seas ───────────────────────────────────────
    // Perpetual darkness. Ghost ships. White ash drifts through pale dead air.

    public static Biome buildPhantomWake(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildPhantomWake(placed);
    }

    public static Biome buildPhantomWake(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.PHANTOM_WAKE);
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.SHROUDED_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.WISPWOOD_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.SOUL_BARNACLE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.GRAVE_SILT_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.CURSED);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SKELETON, 40, 2, 4));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.DROWNED, 30, 2, 4));

        return new Biome.Builder()
                .precipitation(false)
                .temperature(0.2f)
                .downfall(0.0f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x3E3E5E).waterFogColor(0x1C1C3C)
                        .fogColor(0x1A1A2E).skyColor(0x202040)
                        .grassColor(0x5A6A4A).foliageColor(0x3A4A2A)
                        .particleConfig(new BiomeParticleConfig(ParticleTypes.WHITE_ASH, 0.00825f))
                        .moodSound(new BiomeMoodSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 6000, 8, 2.0))
                        .loopSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP)
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    public static Biome buildShipgraveDepths(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildShipgraveDepths(placed);
    }

    public static Biome buildShipgraveDepths(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.SHIPGRAVE_DEPTHS);
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.SHROUDED_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.WISPWOOD_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.SOUL_BARNACLE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.GRAVE_SILT_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.CURSED);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.DROWNED, 50, 3, 5));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SKELETON, 30, 2, 4));

        return new Biome.Builder()
                .precipitation(false)
                .temperature(0.1f)
                .downfall(0.0f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x2A3050).waterFogColor(0x101828)
                        .fogColor(0x141820).skyColor(0x1A1C2E)
                        .grassColor(0x3A4A34).foliageColor(0x222C1C)
                        .particleConfig(new BiomeParticleConfig(ParticleTypes.WHITE_ASH, 0.0118f))
                        .moodSound(new BiomeMoodSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 6000, 8, 2.0))
                        .loopSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP)
                        .additionsSound(new BiomeAdditionsSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, 0.0111))
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    public static Biome buildDrownedTrench(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildDrownedTrench(placed);
    }

    public static Biome buildDrownedTrench(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.DROWNED_TRENCH);
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.SHROUDED_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.WISPWOOD_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.SOUL_BARNACLE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.GRAVE_SILT_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.CURSED);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.DROWNED, 70, 4, 6));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.ZOMBIE, 20, 2, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SKELETON, 10, 1, 2));

        return new Biome.Builder()
                .precipitation(false)
                .temperature(0.0f)
                .downfall(0.0f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x1A1C2E).waterFogColor(0x0A0C16)
                        .fogColor(0x0A0C10).skyColor(0x101020)
                        .grassColor(0x202C1A).foliageColor(0x101808)
                        .particleConfig(new BiomeParticleConfig(ParticleTypes.WHITE_ASH, 0.0155f))
                        .moodSound(new BiomeMoodSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_MOOD, 6000, 8, 2.0))
                        .loopSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_LOOP)
                        .additionsSound(new BiomeAdditionsSound(SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS, 0.0222))
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    // ── Phase 5 — Ethereal Abyss ─────────────────────────────────────────────
    // Bioluminescent deep. Ancient guardians. Glowing trees of an alien world.

    public static Biome buildAbyssalRings(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildAbyssalRings(placed);
    }

    public static Biome buildAbyssalRings(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.ABYSSAL_RINGS);
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.ETHEREAL_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.VOIDBLOOM_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.LUMINOUS_VEIN_PLACED_KEY));
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.ABYSSAL_SLATE_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.ABYSS);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.GLOW_SQUID, 6, 1, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.SQUID, 4, 1, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.DROWNED, 20, 2, 4));

        return new Biome.Builder()
                .precipitation(true)
                .temperature(-0.3f)
                .downfall(0.5f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x2A5F8F).waterFogColor(0x1A3F6F)
                        .fogColor(0x8FAFC8).skyColor(0xA0C8E0)
                        .grassColor(0x3B5090).foliageColor(0x2A3870)
                        .particleConfig(new BiomeParticleConfig(ParticleTypes.WARPED_SPORE, 0.01f))
                        .moodSound(new BiomeMoodSound(SoundEvents.AMBIENT_NETHER_WASTES_MOOD, 6000, 8, 2.0))
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    public static Biome buildPillarSea(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildPillarSea(placed);
    }

    public static Biome buildPillarSea(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.PILLAR_SEA);
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.ETHEREAL_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.VOIDBLOOM_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.LUMINOUS_VEIN_PLACED_KEY));
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.ABYSSAL_SLATE_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.ABYSS);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.GLOW_SQUID, 5, 1, 3));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.DROWNED, 20, 2, 4));

        return new Biome.Builder()
                .precipitation(true)
                .temperature(-0.4f)
                .downfall(0.5f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x3A4A7F).waterFogColor(0x1A2060)
                        .fogColor(0x606EA0).skyColor(0x8090C0)
                        .grassColor(0x2A3A80).foliageColor(0x1A2A60)
                        .particleConfig(new BiomeParticleConfig(ParticleTypes.WARPED_SPORE, 0.01428f))
                        .moodSound(new BiomeMoodSound(SoundEvents.AMBIENT_NETHER_WASTES_MOOD, 6000, 8, 2.0))
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }

    public static Biome buildMawDepths(RegistryWrapper.WrapperLookup lookup) {
        RegistryWrapper<PlacedFeature> placed = lookup.getWrapperOrThrow(RegistryKeys.PLACED_FEATURE);
        return buildMawDepths(placed);
    }

    public static Biome buildMawDepths(RegistryEntryLookup<PlacedFeature> placedFeatures) {
        GenerationSettings.Builder generation = new GenerationSettings.Builder();
        // Dungeons & boss lairs for this biome, in Dungeons.ALL canonical order
        net.get900.pixelpirates.world.dungeon.Dungeons.inject(generation, placedFeatures, ModBiomeKeys.MAW_DEPTHS);
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.ETHEREAL_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.VEGETAL_DECORATION,
                placedFeatures.getOrThrow(ModPlacedFeatures.VOIDBLOOM_TREE_PLACED_KEY));
        generation.feature(GenerationStep.Feature.LOCAL_MODIFICATIONS,
                placedFeatures.getOrThrow(ModPlacedFeatures.LUMINOUS_VEIN_PLACED_KEY));
        generation.feature(GenerationStep.Feature.UNDERGROUND_ORES,
                placedFeatures.getOrThrow(ModPlacedFeatures.ABYSSAL_SLATE_PATCH_PLACED_KEY));

        VanillaBiomeExtras.add(generation, placedFeatures, VanillaBiomeExtras.Profile.ABYSS);

        SpawnSettings.Builder spawns = new SpawnSettings.Builder();
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.GLOW_SQUID, 4, 1, 2));
        spawn(spawns, new SpawnSettings.SpawnEntry(EntityType.DROWNED, 30, 3, 5));

        return new Biome.Builder()
                .precipitation(true)
                .temperature(-0.5f)
                .downfall(0.5f)
                .effects(new BiomeEffects.Builder()
                        .waterColor(0x1A1A3A).waterFogColor(0x08080A)
                        .fogColor(0x101028).skyColor(0x181820)
                        .grassColor(0x1A2050).foliageColor(0x0A1040)
                        .particleConfig(new BiomeParticleConfig(ParticleTypes.WARPED_SPORE, 0.02f))
                        .moodSound(new BiomeMoodSound(SoundEvents.AMBIENT_NETHER_WASTES_MOOD, 6000, 8, 2.0))
                        .additionsSound(new BiomeAdditionsSound(SoundEvents.AMBIENT_NETHER_WASTES_ADDITIONS, 0.0111))
                        .build())
                .spawnSettings(spawns.build())
                .generationSettings(generation.build())
                .build();
    }
}
