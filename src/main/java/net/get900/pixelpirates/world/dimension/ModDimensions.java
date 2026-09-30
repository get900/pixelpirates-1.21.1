package net.get900.pixelpirates.world.dimension;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;

public class ModDimensions {
    public static final RegistryKey<World> PIXEL_PIRATES_WORLD =
            RegistryKey.of(RegistryKeys.WORLD, PixelPirates.id("pixel_pirates"));

    public static final RegistryKey<DimensionType> PIXEL_PIRATES_DIMENSION_TYPE =
            RegistryKey.of(RegistryKeys.DIMENSION_TYPE, PixelPirates.id("pixel_pirates"));
}
