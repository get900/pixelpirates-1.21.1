package net.get900.pixelpirates.homestead;

import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.world.dungeon.DungeonPlacement;
import net.get900.pixelpirates.world.dungeon.Dungeons;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

/** Shared "where is it" queries for the map table, the Compass of Desire and the logbook. */
public final class Navigation {
    private Navigation() {}

    /** Nearest predicted site of a dungeon type (same grid prediction as worldgen / /ppdungeon locate), or null. */
    @Nullable
    public static BlockPos locate(ServerWorld world, String dungeonId, BlockPos from, int rings) {
        Dungeons.Type t = Dungeons.byId(dungeonId);
        if (t == null) return null;
        var gen = world.getChunkManager().getChunkGenerator();
        var ctx = new DungeonPlacement.Context(world.getSeed(), gen, world.getChunkManager().getNoiseConfig(), world, gen.getSeaLevel());
        try {
            return DungeonPlacement.locate(t, ctx, from, rings);
        } catch (Exception e) {
            return null;                                                     // wrong dimension / biome source
        }
    }

    /** The next boss on this player's chain: {name, lair id}, or null when they have beaten them all. */
    @Nullable
    public static BossProgression.Step nextBoss(ServerPlayerEntity p) { return BossProgression.next(p); }

    @Nullable
    public static BlockPos nextLair(ServerPlayerEntity p) {
        BossProgression.Step s = nextBoss(p);
        return s == null ? null : locate(p.getServerWorld(), s.lair(), p.getBlockPos(), 12);
    }

    /** "north-east, 1234 blocks" */
    public static String bearing(BlockPos from, BlockPos to) {
        double dx = to.getX() - from.getX(), dz = to.getZ() - from.getZ();
        String[] dirs = {"south", "south-west", "west", "north-west", "north", "north-east", "east", "south-east"};
        double ang = Math.toDegrees(Math.atan2(-dx, dz));
        int i = (int) Math.round(((ang % 360) + 360) % 360 / 45.0) % 8;
        return dirs[i] + ", " + (int) Math.sqrt(dx * dx + dz * dz) + " blocks";
    }
}
