package net.get900.pixelpirates.entity.mob;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.get900.pixelpirates.PixelPirates;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Difficulty;
import net.minecraft.world.Heightmap;
import net.minecraft.world.ServerWorldAccess;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registers everything for every {@link MobSpec}: entity type (hitbox from the spec), default
 * attributes, the five sound events, natural spawns + spawn restrictions. Spawn eggs are added by
 * ModSpawnEggs, renderers by PixelPiratesClient - both iterate {@link #TYPES}.
 */
public final class ModMobs {
    private ModMobs() {}

    public static final Map<String, EntityType<? extends ModMob>> TYPES = new LinkedHashMap<>();
    public static final String[] SOUND_KINDS = {"ambient", "hurt", "death", "attack", "special"};

    public static void register() {
        for (MobSpec s : MobSpecs.all()) {
            for (MobSpec.Drop d : s.drops)       // a dish looked up before PirateFoods registered resolves to AIR
                if (d.item() == net.minecraft.item.Items.AIR) PixelPirates.LOGGER.error("[ModMobs] {} has a drop that resolved to AIR", s.id);
            FabricEntityTypeBuilder<? extends ModMob> b = s.factory != null
                    ? FabricEntityTypeBuilder.create(s.group, s.factory)
                    : s.boss ? FabricEntityTypeBuilder.create(s.group, ModBoss::new)
                    : FabricEntityTypeBuilder.create(s.group, ModMob::new);
            b.dimensions(EntityDimensions.fixed(s.width, s.height)).trackRangeChunks(s.boss ? 10 : 8);
            // the Leviathan is ~78 blocks long and seen from far off: tracked as far as its body segments, every tick
            if (s.id.equals("leviathan")) b.trackRangeBlocks(256).trackedUpdateRate(1);
            if (s.fireImmune) b.fireImmune();
            EntityType<? extends ModMob> type = Registry.register(Registries.ENTITY_TYPE, PixelPirates.id(s.id), b.build());
            TYPES.put(s.id, type);
            FabricDefaultAttributeRegistry.register(type, ModMob.attributes(s));
            for (String kind : SOUND_KINDS) {
                Identifier id = PixelPirates.id("entity." + s.id + "." + kind);
                Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
            }
            registerSpawns(s, type);
        }
        PixelPirates.LOGGER.info("[ModMobs] Registered {} data-driven mobs", TYPES.size());
    }

    @SuppressWarnings("unchecked")
    private static void registerSpawns(MobSpec s, EntityType<? extends ModMob> type) {
        if (s.biomes.isEmpty() || s.weight <= 0) return;
        BiomeModifications.addSpawn(BiomeSelectors.includeByKey(s.biomes), s.group, type, s.weight, s.minGroup, s.maxGroup);
        SpawnRestriction.Location loc = switch (s.kind) {
            case SWIM -> SpawnRestriction.Location.IN_WATER;
            case STATIONARY -> SpawnRestriction.Location.NO_RESTRICTIONS;
            default -> SpawnRestriction.Location.ON_GROUND;
        };
        SpawnRestriction.register((EntityType<ModMob>) type, loc, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, ModMobs::canSpawn);
    }

    /**
     * Light-independent on purpose (see CLAUDE.md SPAWNING rules - canMobSpawn/isSpawnDark would
     * silently block daytime spawns). Swimmers need two blocks of water (no sea-level check);
     * rooted mobs need a solid base, in water or air; walkers/flyers need solid ground and a dry cell.
     */
    public static boolean canSpawn(EntityType<ModMob> type, ServerWorldAccess world, SpawnReason reason, BlockPos pos, Random random) {
        MobSpec s = MobSpecs.get(Registries.ENTITY_TYPE.getId(type).getPath());
        if (s.hostile() && world.getDifficulty() == Difficulty.PEACEFUL) return false;
        if (s.awayFrom != null && (reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GENERATION)) {
            var sw = world.toServerWorld();
            var gen = sw.getChunkManager().getChunkGenerator();
            var ctx = new net.get900.pixelpirates.world.dungeon.DungeonPlacement.Context(sw.getSeed(), gen, sw.getChunkManager().getNoiseConfig(), sw, gen.getSeaLevel());
            if (net.get900.pixelpirates.world.dungeon.DungeonPlacement.nearSite(s.awayFrom, ctx, pos, s.awayRadius)) return false;
        }
        if (s.sparseRadius > 0 && (reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GENERATION)
                && world.getEntitiesByType(type, new net.minecraft.util.math.Box(pos).expand(s.sparseRadius), e -> true).size() >= s.sparseMax)
            return false;
        boolean water = world.getFluidState(pos).isIn(FluidTags.WATER);
        boolean solidBelow = world.getBlockState(pos.down()).isSolidBlock(world, pos.down());
        return switch (s.kind) {
            case SWIM -> water && world.getFluidState(pos.up()).isIn(FluidTags.WATER);
            case STATIONARY -> solidBelow && (water || world.getBlockState(pos).isAir());
            default -> solidBelow && !water && world.getBlockState(pos).isAir() && world.getBlockState(pos.up()).isAir();
        };
    }
}
