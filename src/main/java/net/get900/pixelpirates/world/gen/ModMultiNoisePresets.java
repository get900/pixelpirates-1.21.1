package net.get900.pixelpirates.world.gen;

import net.minecraft.util.Identifier;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;

import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;

public class ModMultiNoisePresets {
    public static final RegistryKey<MultiNoiseBiomeSourceParameterList> OVERWORLD_PRESET =
            RegistryKey.of(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, new Identifier("pixelpirates", "overworld"));

    public static void register() {
        // No-op: the JSON file provides the data, registration happens automatically.
        // This method can be called in PixelPiratesDataGenerator or left empty.
    }
}