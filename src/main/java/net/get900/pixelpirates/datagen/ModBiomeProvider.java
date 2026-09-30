package net.get900.pixelpirates.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricDynamicRegistryProvider;
import net.get900.pixelpirates.world.biome.hub.HubBiomes;
import net.get900.pixelpirates.world.biome.phase1.Phase1Biomes;
import net.get900.pixelpirates.world.biome.phase2.Phase2Biomes;
import net.get900.pixelpirates.world.biome.phase3.Phase3Biomes;
import net.get900.pixelpirates.world.biome.phase4.Phase4Biomes;
import net.get900.pixelpirates.world.biome.phase5.Phase5Biomes;
import net.minecraft.registry.RegistryWrapper;

import java.util.concurrent.CompletableFuture;

public class ModBiomeProvider extends FabricDynamicRegistryProvider {
    public ModBiomeProvider(FabricDataOutput output, CompletableFuture<RegistryWrapper.WrapperLookup> lookup) {
        super(output, lookup);
    }

    @Override
    public void configure(RegistryWrapper.WrapperLookup lookup, Entries entries) {
        HubBiomes.register(entries, lookup);
        Phase1Biomes.register(entries, lookup);
        Phase2Biomes.register(entries, lookup);
        Phase3Biomes.register(entries, lookup);
        Phase4Biomes.register(entries, lookup);
        Phase5Biomes.register(entries, lookup);
    }

    @Override
    public String getName() {
        return "Pixel Pirates Biomes";
    }
}