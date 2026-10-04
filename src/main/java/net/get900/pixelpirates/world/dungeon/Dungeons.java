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

    /** ISLET: built at sea level + 1 in shallow water / on a low coast, whatever the ground height
     *  (DungeonPlacement#isletOk), turned so design +Z faces the deepest water (DungeonPlacement#seawardRotation).
     *  COAST: also built at sea level + 1, on a real shoreline - low dry ground behind (design -Z), open water in front
     *  (design +Z); DungeonPlacement#coastRotation picks the (deterministic) rotation that fits, or rejects the site. */
    public enum Site { LAND, SEABED, ISLET, COAST }

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
            // ---------------- Phase 1 (grotto/shrine/galleon keep their original ids; grotto + shrine are layouts since 2026-10-04,
            // exported from their old Java builders by /ppstruct export - see LayoutExport)
            new Type("smugglers_grotto", 1, Site.LAND, 0, 20, P1_LAND, LayoutStructures.builder("smugglers_grotto")),
            new Type("tidewater_shrine", 1, Site.LAND, 0, 22, P1_LAND, LayoutStructures.builder("tidewater_shrine")),
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
            new Type("luminous_grotto", 5, Site.SEABED, 4, 28, List.of(ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.MAW_DEPTHS), BossLairs::luminousGrotto),
            // ---------------- appended after the phase blocks (order rule): the LAYOUT sites (ChatGPT packages). Their
            // spacing was doubled on 2026-10-02 (the user: spawning far too often) - about a quarter as many as authored.
            new Type("wreckers_beacon", 1, Site.ISLET, 0, 56, P1_LAND, LayoutStructures.builder("wreckers_beacon")),
            // Phase 1 coastal landmarks (authored externally 2026-10-02; layout files, LayoutStructures)
            new Type("powderwatch_battery", 1, Site.COAST, 0, 60, P1_LAND, LayoutStructures.builder("powderwatch_battery")),
            new Type("castaways_refuge", 1, Site.COAST, 0, 48, P1_LAND, LayoutStructures.builder("castaways_refuge")),
            new Type("pearl_divers_camp", 1, Site.COAST, 0, 52, P1_LAND, LayoutStructures.builder("pearl_divers_camp")),
            new Type("the_saltworks", 1, Site.COAST, 0, 60, P1_LAND, LayoutStructures.builder("the_saltworks")),
            // Phase 2 reef ruins (authored externally 2026-10-02; layout files): minDepth = tallest part + 2,
            // and DungeonPlacement.FLAT_SEABED keeps them off steep seabed
            new Type("sunken_counting_house", 2, Site.SEABED, 14, 56, List.of(ModBiomeKeys.CORAL_BAY, ModBiomeKeys.REEF_EDGE), LayoutStructures.seabed("sunken_counting_house")),
            new Type("sirens_bellcourt", 2, Site.SEABED, 15, 60, List.of(ModBiomeKeys.SIREN_SEA), LayoutStructures.seabed("sirens_bellcourt")),
            new Type("reefcutters_quarry", 2, Site.SEABED, 12, 56, List.of(ModBiomeKeys.REEF_EDGE, ModBiomeKeys.CORAL_BAY), LayoutStructures.seabed("reefcutters_quarry")),
            new Type("tideglass_observatory", 2, Site.SEABED, 17, 64, List.of(ModBiomeKeys.CORAL_BAY, ModBiomeKeys.SIREN_SEA), LayoutStructures.seabed("tideglass_observatory")),
            // Phase 3 volcanic sites (layout files, 2026-10-02): dry LAND islands only; DungeonPlacement.FLAT_LAND checks the footprint
            new Type("cinderchain_tollgate", 3, Site.LAND, 0, 56, List.of(ModBiomeKeys.ASH_REEF, ModBiomeKeys.MAGMA_SEA), LayoutStructures.builder("cinderchain_tollgate")),
            new Type("sulfur_prospectors_camp", 3, Site.LAND, 0, 52, List.of(ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN), LayoutStructures.builder("sulfur_prospectors_camp")),
            new Type("ashglass_kilnworks", 3, Site.LAND, 0, 60, List.of(ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN), LayoutStructures.builder("ashglass_kilnworks")),
            new Type("basalt_signal_redoubt", 3, Site.LAND, 0, 60, List.of(ModBiomeKeys.ASH_REEF, ModBiomeKeys.MAGMA_SEA), LayoutStructures.builder("basalt_signal_redoubt")),
            new Type("emberfall_cistern", 3, Site.LAND, 0, 64, List.of(ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN, ModBiomeKeys.MAGMA_SEA), LayoutStructures.builder("emberfall_cistern")),
            // Phase 4 cursed sites (layout files, 2026-10-02)
            new Type("widows_lantern_hospice", 4, Site.LAND, 0, 56, List.of(ModBiomeKeys.PHANTOM_WAKE), LayoutStructures.builder("widows_lantern_hospice")),
            new Type("chainbreak_salvage_yard", 4, Site.LAND, 0, 56, List.of(ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.PHANTOM_WAKE), LayoutStructures.builder("chainbreak_salvage_yard")),
            new Type("mourning_archive", 4, Site.LAND, 0, 60, List.of(ModBiomeKeys.PHANTOM_WAKE, ModBiomeKeys.SHIPGRAVE_DEPTHS), LayoutStructures.builder("mourning_archive")),
            new Type("drowned_customs_house", 4, Site.SEABED, 15, 60, List.of(ModBiomeKeys.DROWNED_TRENCH, ModBiomeKeys.SHIPGRAVE_DEPTHS), LayoutStructures.seabed("drowned_customs_house")),
            new Type("keelbone_ossuary", 4, Site.SEABED, 16, 64, List.of(ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.DROWNED_TRENCH), LayoutStructures.seabed("keelbone_ossuary")),
            // Phase 5 abyssal PUZZLE sites (layout files + AbyssPuzzleNodes, 2026-10-02): one-time seal chest per site
            new Type("hushed_bell_court", 5, Site.SEABED, 16, 64, List.of(ModBiomeKeys.ABYSSAL_RINGS), LayoutStructures.seabed("hushed_bell_court")),
            new Type("lantern_confluence", 5, Site.SEABED, 16, 64, List.of(ModBiomeKeys.MAW_DEPTHS, ModBiomeKeys.PILLAR_SEA), LayoutStructures.seabed("lantern_confluence")),
            new Type("ferrymans_balance", 5, Site.SEABED, 16, 68, List.of(ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.MAW_DEPTHS), LayoutStructures.seabed("ferrymans_balance")),
            new Type("tidewheel_oracle", 5, Site.SEABED, 16, 64, List.of(ModBiomeKeys.PILLAR_SEA), LayoutStructures.seabed("tidewheel_oracle")),
            new Type("mnemonic_reliquary", 5, Site.SEABED, 16, 68, List.of(ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.ABYSSAL_RINGS), LayoutStructures.seabed("mnemonic_reliquary")),
            // The "fifteen-location expansion" (ChatGPT package, 2026-10-02 night): one more set for every phase, authored
            // sparse (80-92 chunk spacing); Phase 5 adds puzzle kinds 5-7 (AbyssPuzzleLogic)
            // turtleback: authored 80 + island_thickets only = no site in most worlds -> 48 + temperate_shallows
            new Type("turtleback_orchard", 1, Site.LAND, 0, 48, List.of(ModBiomeKeys.ISLAND_THICKETS, ModBiomeKeys.TEMPERATE_SHALLOWS), LayoutStructures.builder("turtleback_orchard")),
            new Type("driftwood_ropewalk", 1, Site.COAST, 0, 84, List.of(ModBiomeKeys.TEMPERATE_SHALLOWS, ModBiomeKeys.ISLAND_THICKETS), LayoutStructures.builder("driftwood_ropewalk")),
            new Type("gullwing_boathouse", 1, Site.COAST, 0, 84, List.of(ModBiomeKeys.TEMPERATE_SHALLOWS, ModBiomeKeys.ISLAND_THICKETS), LayoutStructures.builder("gullwing_boathouse")),
            new Type("azure_mosaic_baths", 2, Site.SEABED, 14, 84, List.of(ModBiomeKeys.CORAL_BAY, ModBiomeKeys.REEF_EDGE), LayoutStructures.seabed("azure_mosaic_baths")),
            new Type("pearl_courier_waystation", 2, Site.SEABED, 15, 84, List.of(ModBiomeKeys.SIREN_SEA, ModBiomeKeys.REEF_EDGE), LayoutStructures.seabed("pearl_courier_waystation")),
            // coral_conservatory: + reef_edge (coral_bay alone too scarce for a 15-deep flat seabed)
            new Type("coral_conservatory", 2, Site.SEABED, 15, 84, List.of(ModBiomeKeys.CORAL_BAY, ModBiomeKeys.REEF_EDGE), LayoutStructures.seabed("coral_conservatory")),
            new Type("scoria_switchback", 3, Site.LAND, 0, 88, List.of(ModBiomeKeys.ASH_REEF, ModBiomeKeys.MAGMA_SEA), LayoutStructures.builder("scoria_switchback")),
            new Type("cinderwake_caravansary", 3, Site.LAND, 0, 88, List.of(ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN), LayoutStructures.builder("cinderwake_caravansary")),
            // brimstone_railhead: + ash_reef (boiling_basin/magma_sea have almost no dry flat land)
            new Type("brimstone_railhead", 3, Site.LAND, 0, 88, List.of(ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN, ModBiomeKeys.MAGMA_SEA), LayoutStructures.builder("brimstone_railhead")),
            new Type("last_echo_theatre", 4, Site.LAND, 0, 88, List.of(ModBiomeKeys.PHANTOM_WAKE), LayoutStructures.builder("last_echo_theatre")),
            new Type("sundered_prison_barge", 4, Site.SEABED, 14, 88, List.of(ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.DROWNED_TRENCH), LayoutStructures.seabed("sundered_prison_barge")),
            new Type("blackwake_auction_court", 4, Site.LAND, 0, 88, List.of(ModBiomeKeys.PHANTOM_WAKE, ModBiomeKeys.SHIPGRAVE_DEPTHS), LayoutStructures.builder("blackwake_auction_court")),
            new Type("verdict_of_the_four", 5, Site.SEABED, 16, 92, List.of(ModBiomeKeys.ABYSSAL_RINGS), LayoutStructures.seabed("verdict_of_the_four")),
            new Type("processional_orrery", 5, Site.SEABED, 16, 92, List.of(ModBiomeKeys.PILLAR_SEA), LayoutStructures.seabed("processional_orrery")),
            new Type("measured_depths_reservoir", 5, Site.SEABED, 16, 92, List.of(ModBiomeKeys.MAW_DEPTHS, ModBiomeKeys.ABYSSAL_RINGS), LayoutStructures.seabed("measured_depths_reservoir"))
    );

    /**
     * Dungeons whose footprint is kept free of natural decoration (trees, boulders, flowers, kelp...),
     * with the protected half-size in blocks around the site chunk's centre. Opt in as each dungeon is
     * overhauled - see FeatureExclusionMixin / {@link DungeonPlacement#insideProtectedSite}.
     */
    public static final java.util.Map<String, Integer> PROTECTED = java.util.Map.ofEntries(
            java.util.Map.entry("rackham_fort", 26),
            java.util.Map.entry("serpent_trench", 24),
            java.util.Map.entry("cinder_forge", 24),
            java.util.Map.entry("ghost_ship", 24),
            java.util.Map.entry("abyssal_throne", 24),
            java.util.Map.entry("bloodfin_reef", 24),
            java.util.Map.entry("kraken_maw", 23),
            java.util.Map.entry("wreckers_beacon", 19),
            java.util.Map.entry("powderwatch_battery", 19),
            java.util.Map.entry("castaways_refuge", 14),
            java.util.Map.entry("pearl_divers_camp", 17),
            java.util.Map.entry("the_saltworks", 19),
            java.util.Map.entry("sunken_counting_house", 19),
            java.util.Map.entry("sirens_bellcourt", 19),
            java.util.Map.entry("reefcutters_quarry", 21),
            java.util.Map.entry("tideglass_observatory", 20),
            java.util.Map.entry("cinderchain_tollgate", 20),
            java.util.Map.entry("sulfur_prospectors_camp", 19),
            java.util.Map.entry("ashglass_kilnworks", 21),
            java.util.Map.entry("basalt_signal_redoubt", 19),
            java.util.Map.entry("emberfall_cistern", 21),
            java.util.Map.entry("widows_lantern_hospice", 19),
            java.util.Map.entry("chainbreak_salvage_yard", 21),
            java.util.Map.entry("mourning_archive", 20),
            java.util.Map.entry("drowned_customs_house", 20),
            java.util.Map.entry("keelbone_ossuary", 21),
            java.util.Map.entry("hushed_bell_court", 21),
            java.util.Map.entry("lantern_confluence", 21),
            java.util.Map.entry("ferrymans_balance", 21),
            java.util.Map.entry("tidewheel_oracle", 21),
            java.util.Map.entry("mnemonic_reliquary", 21),
            java.util.Map.entry("turtleback_orchard", 18),
            java.util.Map.entry("driftwood_ropewalk", 20),
            java.util.Map.entry("gullwing_boathouse", 19),
            java.util.Map.entry("azure_mosaic_baths", 20),
            java.util.Map.entry("pearl_courier_waystation", 19),
            java.util.Map.entry("coral_conservatory", 21),
            java.util.Map.entry("scoria_switchback", 20),
            java.util.Map.entry("cinderwake_caravansary", 21),
            java.util.Map.entry("brimstone_railhead", 21),
            java.util.Map.entry("last_echo_theatre", 21),
            java.util.Map.entry("sundered_prison_barge", 21),
            java.util.Map.entry("blackwake_auction_court", 20),
            java.util.Map.entry("verdict_of_the_four", 21),
            java.util.Map.entry("processional_orrery", 21),
            java.util.Map.entry("measured_depths_reservoir", 21));

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
