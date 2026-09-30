package net.get900.pixelpirates.world.tree;

import net.get900.pixelpirates.world.ModConfiguredFeatures;
import net.minecraft.block.sapling.SaplingGenerator;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import org.jetbrains.annotations.Nullable;

/** Grows the same shorewood tree (banana/coconut decorators included) that worldgen places. */
public class ShorewoodSaplingGenerator extends SaplingGenerator {
    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    protected RegistryKey<ConfiguredFeature<?, ?>> getTreeFeature(Random random, boolean bees) {
        return (RegistryKey<ConfiguredFeature<?, ?>>) (Object) ModConfiguredFeatures.SHOREWOOD_TREE_KEY;
    }
}
