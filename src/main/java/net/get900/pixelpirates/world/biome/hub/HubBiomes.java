package net.get900.pixelpirates.world.biome.hub;

import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.datagen.biome.PixelPiratesBiomes;
import net.minecraft.registry.*;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.feature.PlacedFeature;

public class HubBiomes {
    public static final RegistryKey<Biome> SPAWN_ISLAND = key("spawn_island");

    public static void register(FabricDynamicRegistryProvider.Entries entries, RegistryWrapper.WrapperLookup lookup) {
        entries.add(SPAWN_ISLAND, PixelPiratesBiomes.buildSpawnIsland(lookup));
    }

    public static void bootstrap(Registerable<Biome> context) {
        RegistryEntryLookup<PlacedFeature> placed = context.getRegistryLookup(RegistryKeys.PLACED_FEATURE);
        context.register(SPAWN_ISLAND, PixelPiratesBiomes.buildSpawnIsland(placed));
    }

    private static RegistryKey<Biome> key(String name) {
        return RegistryKey.of(RegistryKeys.BIOME, PixelPirates.id(name));
    }
}
