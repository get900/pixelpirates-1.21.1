package net.get900.pixelpirates.world.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryFixedCodec;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;

import java.util.List;
import java.util.stream.Stream;

public class PixelPiratesBiomeSource extends BiomeSource {

    // Zone radii in biome coordinates (block radius / 4, since biome coords are quarter-block)
    private static final double HUB_RADIUS    = 500.0  / 4.0;
    private static final double ZONE1_RADIUS  = 1500.0 / 4.0;
    private static final double ZONE2_RADIUS  = 2500.0 / 4.0;
    private static final double ZONE3_RADIUS  = 3500.0 / 4.0;
    private static final double ZONE4_RADIUS  = 4500.0 / 4.0;

    // Biome list order (must match dimension JSON):
    //  [0]    = hub (spawn_island)
    //  [1..3] = zone 1 sectors 0,1,2
    //  [4..6] = zone 2 sectors 0,1,2
    //  [7..9] = zone 3 sectors 0,1,2
    // [10..12] = zone 4 sectors 0,1,2
    // [13..15] = zone 5 sectors 0,1,2
    private final List<RegistryEntry<Biome>> biomes;

    public static final MapCodec<PixelPiratesBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
        instance.group(
            RegistryFixedCodec.of(RegistryKeys.BIOME).listOf()
                .fieldOf("biomes")
                .forGetter(s -> s.biomes)
        ).apply(instance, PixelPiratesBiomeSource::new)
    );
    // Cached so that registration and getCodec() return the same object — required for
    // BiomeSource dispatch codec to find the registry key on encode (level.dat save).
    public static final Codec<PixelPiratesBiomeSource> CODEC_INSTANCE = CODEC.codec();

    public PixelPiratesBiomeSource(List<RegistryEntry<Biome>> biomes) {
        this.biomes = biomes;
    }

    @Override
    protected Codec<? extends BiomeSource> getCodec() {
        return CODEC_INSTANCE;
    }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return biomes.stream();
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        double dist = Math.sqrt((double) x * x + (double) z * z);

        if (dist < HUB_RADIUS) return biomes.get(0);

        int zone;
        if      (dist < ZONE1_RADIUS) zone = 0;
        else if (dist < ZONE2_RADIUS) zone = 1;
        else if (dist < ZONE3_RADIUS) zone = 2;
        else if (dist < ZONE4_RADIUS) zone = 3;
        else                          zone = 4;

        int sector = angleSector(x, z);
        return biomes.get(1 + zone * 3 + sector);
    }

    private static int angleSector(int x, int z) {
        double angle = Math.atan2(z, x);
        if (angle < 0) angle += 2 * Math.PI;
        return Math.min((int)(angle / (2.0 * Math.PI / 3.0)), 2);
    }
}
