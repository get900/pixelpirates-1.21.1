package net.get900.pixelpirates.world.biome;

import net.get900.pixelpirates.datagen.biome.PixelPiratesBiomes;
import net.get900.pixelpirates.world.biome.hub.HubBiomes;
import net.get900.pixelpirates.world.biome.phase1.Phase1Biomes;
import net.get900.pixelpirates.world.biome.phase2.Phase2Biomes;
import net.get900.pixelpirates.world.biome.phase3.Phase3Biomes;
import net.get900.pixelpirates.world.biome.phase4.Phase4Biomes;
import net.get900.pixelpirates.world.biome.phase5.Phase5Biomes;
import net.minecraft.registry.Registerable;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.get900.pixelpirates.PixelPirates;

public class ModBiomes {

    // Hub
    public static final RegistryKey<Biome> SPAWN_ISLAND = HubBiomes.SPAWN_ISLAND;

    // Phase 1
    public static final RegistryKey<Biome> TEMPERATE_SHALLOWS = Phase1Biomes.TEMPERATE_SHALLOWS;
    public static final RegistryKey<Biome> ISLAND_THICKETS = Phase1Biomes.ISLAND_THICKETS;
    public static final RegistryKey<Biome> OPEN_OCEAN = Phase1Biomes.OPEN_OCEAN;

    // Phase 2
    public static final RegistryKey<Biome> CORAL_BAY = Phase2Biomes.CORAL_BAY;
    public static final RegistryKey<Biome> REEF_EDGE = Phase2Biomes.REEF_EDGE;
    public static final RegistryKey<Biome> SIREN_SEA = Phase2Biomes.SIREN_SEA;

    // Phase 3
    public static final RegistryKey<Biome> ASH_REEF = Phase3Biomes.ASH_REEF;
    public static final RegistryKey<Biome> BOILING_BASIN = Phase3Biomes.BOILING_BASIN;
    public static final RegistryKey<Biome> MAGMA_SEA = Phase3Biomes.MAGMA_SEA;

    // Phase 4
    public static final RegistryKey<Biome> PHANTOM_WAKE = Phase4Biomes.PHANTOM_WAKE;
    public static final RegistryKey<Biome> SHIPGRAVE_DEPTHS = Phase4Biomes.SHIPGRAVE_DEPTHS;
    public static final RegistryKey<Biome> DROWNED_TRENCH = Phase4Biomes.DROWNED_TRENCH;

    // Phase 5
    public static final RegistryKey<Biome> ABYSSAL_RINGS = Phase5Biomes.ABYSSAL_RINGS;
    public static final RegistryKey<Biome> PILLAR_SEA = Phase5Biomes.PILLAR_SEA;
    public static final RegistryKey<Biome> MAW_DEPTHS = Phase5Biomes.MAW_DEPTHS;

    public static void bootstrap(Registerable<Biome> context) {
        HubBiomes.bootstrap(context);
        Phase1Biomes.bootstrap(context);
        Phase2Biomes.bootstrap(context);
        Phase3Biomes.bootstrap(context);
        Phase4Biomes.bootstrap(context);
        Phase5Biomes.bootstrap(context);
    }
}

