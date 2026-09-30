package net.get900.pixelpirates.world.gen;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.get900.pixelpirates.world.ModPlacedFeatures;
import net.get900.pixelpirates.world.biome.ModBiomes;
import net.minecraft.world.gen.GenerationStep;

public class ModWorldGeneration {
    public static void generateModWorldGen() {
        // NOTE: trees are baked into the biome JSONs via PixelPiratesBiomes (datagen).
        // The old runtime BiomeModifications.addFeature calls for shorewood/ashen were
        // removed — they duplicated baked features and risked "feature order cycle"
        // crashes once biomes started sharing multiple tree features.
        ModEntitySpawns.addSpawns();
    }
}
