package net.get900.pixelpirates.world.dungeon;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.world.biome.ModBiomeKeys;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.PlacedFeature;

import java.util.List;

/**
 * Registry of every dungeon. ORDER MATTERS: this list is the canonical SURFACE_STRUCTURES
 * feature order, and each biome takes its subset in list order - that is what keeps worldgen
 * free of "feature order cycle" crashes. Append new dungeons at the end of their phase block
 * or at the end of the list; never reorder existing entries.
 */
public final class Dungeons {
    private Dungeons() {}

    public enum Site { LAND, SEABED }

    @FunctionalInterface
    public interface Builder { void build(DungeonBuilder b, int depth); }

    /**
     * @param minDepth for SEABED: water above the seabed required at the site
     * @param spacing  grid cell size in chunks; at most one of this dungeon per cell (DungeonPlacement)
     */
    public record Type(String id, int phase, Site site, int minDepth, int spacing, List<RegistryKey<Biome>> biomes, Builder builder) {
        public RegistryKey<ConfiguredFeature<?, ?>> configuredKey() {
            return RegistryKey.of(RegistryKeys.CONFIGURED_FEATURE, PixelPirates.id(id));
        }
        public RegistryKey<PlacedFeature> placedKey() {
            return RegistryKey.of(RegistryKeys.PLACED_FEATURE, PixelPirates.id(id + "_placed"));
        }
    }

    private static final List<RegistryKey<Biome>> P1_LAND = List.of(ModBiomeKeys.TEMPERATE_SHALLOWS, ModBiomeKeys.ISLAND_THICKETS);
    private static final List<RegistryKey<Biome>> P3 = List.of(ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN, ModBiomeKeys.MAGMA_SEA);

    // BOSS LAIRS each own a biome no other boss lair uses (rackham: island_thickets, bloodfin: open_ocean,
    // kraken: coral_bay, serpent: siren_sea, warlord: phase 3, ghost captain: phantom_wake, revenant:
    // shipgrave_depths + drowned_trench, king: abyssal_rings, heart: maw_depths, leviathan: pillar_sea).
    public static final List<Type> ALL = List.of(
            // ---------------- Phase 1 (grotto/shrine/galleon keep their original ids)
            new Type("smugglers_grotto", 1, Site.LAND, 0, 20, P1_LAND, (b, d) -> SmugglersGrottoFeature.build(b)),
            new Type("tidewater_shrine", 1, Site.LAND, 0, 22, P1_LAND, (b, d) -> TidewaterShrineFeature.build(b)),
            new Type("sunken_galleon", 1, Site.SEABED, 2, 24, List.of(ModBiomeKeys.TEMPERATE_SHALLOWS, ModBiomeKeys.OPEN_OCEAN), SunkenGalleonFeature::build),
            new Type("rackham_fort", 1, Site.LAND, 0, 36, List.of(ModBiomeKeys.ISLAND_THICKETS), BossLairs::rackhamFort),
            new Type("bloodfin_reef", 1, Site.SEABED, 7, 36, List.of(ModBiomeKeys.OPEN_OCEAN), BossLairs::bloodfinReef),
            // ---------------- Phase 2
            new Type("kraken_maw", 2, Site.SEABED, 5, 40, List.of(ModBiomeKeys.CORAL_BAY), BossLairs::krakenMaw),
            new Type("serpent_trench", 2, Site.SEABED, 6, 40, List.of(ModBiomeKeys.SIREN_SEA), BossLairs::serpentTrench),
            new Type("coral_temple", 2, Site.SEABED, 4, 24, List.of(ModBiomeKeys.CORAL_BAY, ModBiomeKeys.REEF_EDGE, ModBiomeKeys.SIREN_SEA), BossLairs::coralTemple),
            // ---------------- Phase 3
            new Type("cinder_forge", 3, Site.LAND, 0, 40, P3, BossLairs::cinderForge),
            new Type("obsidian_vault", 3, Site.LAND, 0, 28, P3, BossLairs::obsidianVault),
            new Type("fire_camp", 3, Site.LAND, 0, 24, P3, BossLairs::fireCamp),
            // ---------------- Phase 4
            new Type("ghost_ship", 4, Site.SEABED, 2, 40, List.of(ModBiomeKeys.PHANTOM_WAKE), BossLairs::ghostShip),
            new Type("revenant_crypt", 4, Site.SEABED, 4, 40, List.of(ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.DROWNED_TRENCH), BossLairs::revenantCrypt),
            new Type("drowned_graveyard", 4, Site.LAND, 0, 26, List.of(ModBiomeKeys.PHANTOM_WAKE, ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.DROWNED_TRENCH), BossLairs::drownedGraveyard),
            // ---------------- Phase 5
            new Type("abyssal_throne", 5, Site.SEABED, 6, 44, List.of(ModBiomeKeys.ABYSSAL_RINGS), BossLairs::abyssalThrone),
            new Type("abyssal_heart_lair", 5, Site.SEABED, 1, 44, List.of(ModBiomeKeys.MAW_DEPTHS), BossLairs::abyssalHeartLair),
            new Type("leviathan_rift", 5, Site.SEABED, 8, 48, List.of(ModBiomeKeys.PILLAR_SEA), BossLairs::leviathanRift),
            new Type("luminous_grotto", 5, Site.SEABED, 4, 28, List.of(ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.MAW_DEPTHS), BossLairs::luminousGrotto)
    );

    /**
     * Dungeons whose footprint is kept free of natural decoration (trees, boulders, flowers, kelp...),
     * with the protected half-size in blocks around the site chunk's centre. Opt in as each dungeon is
     * overhauled - see FeatureExclusionMixin / {@link DungeonPlacement#insideProtectedSite}.
     */
    public static final java.util.Map<String, Integer> PROTECTED = java.util.Map.of(
            "rackham_fort", 26,
            "serpent_trench", 24,
            "cinder_forge", 24,
            "ghost_ship", 24,
            "abyssal_throne", 24,
            "bloodfin_reef", 24,
            "kraken_maw", 23);

    public static Type byId(String id) {
        for (Type t : ALL) if (t.id().equals(id)) return t;
        return null;
    }

    /** Add this biome's dungeons (canonical order) to a biome builder. Call from PixelPiratesBiomes. */
    public static void inject(GenerationSettings.Builder generation, RegistryEntryLookup<PlacedFeature> placed, RegistryKey<Biome> biome) {
        for (Type t : ALL) {
            if (t.biomes().contains(biome)) generation.feature(GenerationStep.Feature.SURFACE_STRUCTURES, placed.getOrThrow(t.placedKey()));
        }
        // the Gallows Grotto spans hundreds of blocks - every biome renders its share (always after the dungeons)
        generation.feature(GenerationStep.Feature.SURFACE_STRUCTURES, placed.getOrThrow(net.get900.pixelpirates.world.ModPlacedFeatures.GALLOWS_GROTTO_PLACED_KEY));
        // ...and so does the Titan's Chest (always after the grotto)
        generation.feature(GenerationStep.Feature.SURFACE_STRUCTURES, placed.getOrThrow(net.get900.pixelpirates.world.ModPlacedFeatures.TITANS_CHEST_PLACED_KEY));
        // ...and the Leviathan's five sites (always last)
        generation.feature(GenerationStep.Feature.SURFACE_STRUCTURES, placed.getOrThrow(net.get900.pixelpirates.world.ModPlacedFeatures.LEVIATHAN_SITES_PLACED_KEY));
    }
}
