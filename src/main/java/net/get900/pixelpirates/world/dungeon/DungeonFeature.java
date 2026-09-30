package net.get900.pixelpirates.world.dungeon;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * One feature class for every {@link Dungeons.Type}: only the chunk DungeonPlacement picked for its
 * grid cell proceeds; it snaps to the chunk centre, validates the
 * site (dry flat land, or seabed with enough water above), picks a rotation and builds - with any
 * exception logged and the site skipped so a design bug can never break chunk generation.
 */
public class DungeonFeature extends Feature<DefaultFeatureConfig> {
    private final Dungeons.Type type;

    public DungeonFeature(Dungeons.Type type) {
        super(DefaultFeatureConfig.CODEC);
        this.type = type;
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        StructureWorldAccess world = ctx.getWorld();
        if (!DungeonPlacement.isCandidate(type, placementContext(world, ctx.getGenerator()), new net.minecraft.util.math.ChunkPos(ctx.getOrigin()))) return false;
        BlockPos c = DungeonBuilder.chunkCentre(ctx.getOrigin());
        int top = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, c.getX(), c.getZ()) - 1;
        BlockPos origin = new BlockPos(c.getX(), top, c.getZ());
        int depth = waterDepth(world, origin);
        if (type.site() == Dungeons.Site.LAND ? !SmugglersGrottoFeature.validSite(world, origin) : depth < type.minDepth()) return false;
        try {
            type.builder().build(new DungeonBuilder(world, origin, BlockRotation.random(ctx.getRandom()), ctx.getRandom()), depth);
        } catch (Exception e) {
            PixelPirates.LOGGER.error("[Dungeon] {} failed at {}", type.id(), origin, e);
            return false;
        }
        return true;
    }

    public static DungeonPlacement.Context placementContext(StructureWorldAccess world, net.minecraft.world.gen.chunk.ChunkGenerator generator) {
        var sw = world.toServerWorld();
        return new DungeonPlacement.Context(world.getSeed(), generator, sw.getChunkManager().getNoiseConfig(), sw, generator.getSeaLevel());
    }

    public static int waterDepth(StructureWorldAccess world, BlockPos seabed) {
        int d = 0;
        while (d < 64 && world.getFluidState(seabed.up(d + 1)).isIn(FluidTags.WATER)) d++;
        return d;
    }
}
