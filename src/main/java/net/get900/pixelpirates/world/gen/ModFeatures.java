package net.get900.pixelpirates.world.gen;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;

public class ModFeatures {
    public static final Feature<DefaultFeatureConfig> SPAWN_ISLAND =
            Registry.register(Registries.FEATURE, PixelPirates.id("spawn_island"),
                    new SpawnIslandFeature(DefaultFeatureConfig.CODEC));

    /** The Chained Revenant's lair: a cave system hundreds of blocks across, rendered chunk by chunk. */
    public static final Feature<DefaultFeatureConfig> GALLOWS_GROTTO =
            Registry.register(Registries.FEATURE, PixelPirates.id("gallows_grotto"),
                    new net.get900.pixelpirates.world.dungeon.GallowsGrotto(DefaultFeatureConfig.CODEC));

    /** The Abyssal Heart's lair: the Titan's Chest, a flooded ribcage ~170 x 150 blocks, rendered chunk by chunk. */
    public static final Feature<DefaultFeatureConfig> TITANS_CHEST =
            Registry.register(Registries.FEATURE, PixelPirates.id("titans_chest"),
                    new net.get900.pixelpirates.world.dungeon.TitansChest(DefaultFeatureConfig.CODEC));

    /** The Leviathan's three lairs and the two ports on its road - one of each per world, rendered chunk by chunk. */
    public static final Feature<DefaultFeatureConfig> LEVIATHAN_SITES =
            Registry.register(Registries.FEATURE, PixelPirates.id("leviathan_sites"),
                    new net.get900.pixelpirates.world.leviathan.LeviathanSites(DefaultFeatureConfig.CODEC));

    /** Every dungeon in Dungeons.ALL: one generic DungeonFeature each, registered under its id. */
    public static final java.util.Map<String, Feature<DefaultFeatureConfig>> DUNGEONS = new java.util.LinkedHashMap<>();
    static {
        for (var t : net.get900.pixelpirates.world.dungeon.Dungeons.ALL) {
            DUNGEONS.put(t.id(), Registry.register(Registries.FEATURE, PixelPirates.id(t.id()),
                    new net.get900.pixelpirates.world.dungeon.DungeonFeature(t)));
        }
    }

    public static void register() {
        PixelPirates.LOGGER.info("Registering mod features for " + PixelPirates.MOD_ID);
    }
}
