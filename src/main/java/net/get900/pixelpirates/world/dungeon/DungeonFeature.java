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
        DungeonPlacement.Context pctx = placementContext(world, ctx.getGenerator());
        if (!DungeonPlacement.isCandidate(type, pctx, new net.minecraft.util.math.ChunkPos(ctx.getOrigin()))) return false;
        BlockPos c = DungeonBuilder.chunkCentre(ctx.getOrigin());
        boolean islet = type.site() == Dungeons.Site.ISLET || type.site() == Dungeons.Site.COAST;
        // ISLET + COAST are fixed-height: the beach sits at sea level + 1 whatever the (already vetted) ground below
        int top = islet ? pctx.seaLevel() + 1 : world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, c.getX(), c.getZ()) - 1;
        BlockPos origin = new BlockPos(c.getX(), top, c.getZ());
        int depth = waterDepth(world, origin);
        if (!islet && (type.site() == Dungeons.Site.LAND ? !landSiteOk(world, origin) : depth < type.minDepth())) return false;
        BlockRotation rot = type.site() == Dungeons.Site.ISLET ? DungeonPlacement.seawardRotation(pctx, c.getX(), c.getZ())
                : type.site() == Dungeons.Site.COAST ? DungeonPlacement.coastRotation(pctx, c.getX(), c.getZ())
                : BlockRotation.random(ctx.getRandom());
        if (rot == null) return false;                                                       // (cannot happen: placement vetted it)
        try {
            type.builder().build(new DungeonBuilder(world, origin, rot, ctx.getRandom()), depth);
        } catch (Exception e) {
            PixelPirates.LOGGER.error("[Dungeon] {} failed at {}", type.id(), origin, e);
            return false;
        }
        return true;
    }

    /** LAND sites: dry, roughly flat land above sea level. */
    static boolean landSiteOk(StructureWorldAccess world, BlockPos o) {
        if (o.getY() < world.getSeaLevel() + 1) return false;
        if (!world.getFluidState(o.up()).isEmpty() || !world.getBlockState(o).isSolidBlock(world, o)) return false;
        for (int[] d : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
            int h = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, o.getX() + d[0], o.getZ() + d[1]) - 1;
            if (Math.abs(h - o.getY()) > 3) return false;
            if (!world.getFluidState(new BlockPos(o.getX() + d[0], h + 1, o.getZ() + d[1])).isEmpty()) return false;
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
