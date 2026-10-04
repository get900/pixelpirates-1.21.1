package net.get900.pixelpirates.world.gen;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.get900.pixelpirates.homestead.trade.PortTraders;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

/**
 * Remembers every spot on the spawn island a player has broken, placed or used a block at (and its neighbours - the other
 * half of a door or bed), so {@code /ppisland capture all} can save exactly what was changed by hand - never the old-layout
 * blocks or shifted random decor an older world still holds. Kept in the overworld's saved data, so it survives restarts;
 * cleared by a capture. Not seen: /fill and /setblock, buckets, explosions - capture that building instead.
 */
public class IslandEditTracker extends PersistentState {
    private static final String KEY = "pixelpirates_island_touched";
    final LongOpenHashSet touched = new LongOpenHashSet();

    public static IslandEditTracker get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(IslandEditTracker::fromNbt, IslandEditTracker::new, KEY);
    }

    static IslandEditTracker fromNbt(NbtCompound nbt) {
        IslandEditTracker t = new IslandEditTracker();
        for (long l : nbt.getLongArray("Touched")) t.touched.add(l);
        return t;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putLongArray("Touched", touched.toLongArray());
        return nbt;
    }

    /** Mark a spot (and the 26 around it) if it is inside the island plan. */
    static void touch(World world, BlockPos p) {
        if (world.isClient || world.getServer() == null || !world.getRegistryKey().equals(PortTraders.DIM)) return;
        if (p.getX() < PortCityLayout.X0 - 1 || p.getX() > PortCityLayout.X1 + 1 || p.getZ() < PortCityLayout.Z0 - 1 || p.getZ() > PortCityLayout.Z1 + 1
                || p.getY() < PortCityLayout.Y0 - 1 || p.getY() > PortCityLayout.Y1 + 1) return;
        IslandEditTracker t = get(world.getServer());
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++)
            t.touched.add(BlockPos.asLong(p.getX() + dx, p.getY() + dy, p.getZ() + dz));
        t.markDirty();
    }

    public static void init() {
        PlayerBlockBreakEvents.AFTER.register((world, player, pos, state, be) -> touch(world, pos));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            touch(world, hit.getBlockPos());
            touch(world, hit.getBlockPos().offset(hit.getSide()));                           // where a placed block lands
            return ActionResult.PASS;
        });
    }
}
