package net.get900.pixelpirates.world;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

import java.util.Set;

/**
 * BUILDING ON A SHIP (2026-10-01): players may place blocks on an assembled ship - even while it sails - but only a
 * limited number of them. Every block a survival player places on a ship is recorded (ShipRegistryState
 * "custom_blocks", ship-yard positions); the limit comes from the Shipwright refit "carpentry" (Ship's Carpenter):
 * {@link #SLOTS}. Breaking a custom block - by hand, cannon fire or anything else - frees its slot (stale entries are
 * pruned whenever the count is read). The blueprint's own blocks never count. Hooked from mixin/ShipBuildLimitMixin.
 */
public final class ShipBuilding {
    private ShipBuilding() {}

    /** Custom-block slots by Ship's Carpenter level (0 = no refit). */
    public static final int[] SLOTS = {3, 8, 16, 32, 64};

    public static int limit(ServerWorld world, long shipId) {
        int lv = ShipRegistryState.get(world.getServer().getOverworld()).getUpgradeLevel(shipId, "carpentry");
        return SLOTS[Math.max(0, Math.min(SLOTS.length - 1, lv))];
    }

    /** Custom blocks still standing on this ship (drops the ones that are gone). */
    public static int used(ServerWorld world, long shipId) {
        ShipRegistryState reg = ShipRegistryState.get(world.getServer().getOverworld());
        Set<Long> set = reg.customBlocks(shipId);
        if (set.removeIf(p -> world.getBlockState(BlockPos.fromLong(p)).isAir())) reg.markDirty();
        return set.size();
    }

    @Nullable
    public static Ship shipAt(World world, BlockPos pos) {
        return ValkyrienSkies.getShipManagingBlock(world, pos.getX(), pos.getY(), pos.getZ());
    }

    /** Before a block is placed at pos: false (and a message) when the ship has no free slot. */
    public static boolean mayPlace(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (player.isCreative()) return true;
        Ship ship = shipAt(world, pos);
        if (ship == null) return true;
        int used = used(world, ship.getId()), max = limit(world, ship.getId());
        if (used < max) return true;
        player.sendMessage(Text.literal("This ship has no room for more of your blocks (" + used + "/" + max
                + "). A Ship's Carpenter refit at the Shipwright adds more.").formatted(Formatting.RED), true);
        return false;
    }

    /** After a block was placed at pos. */
    public static void placed(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        if (player.isCreative()) return;
        Ship ship = shipAt(world, pos);
        if (ship == null) return;
        ShipRegistryState reg = ShipRegistryState.get(world.getServer().getOverworld());
        if (reg.customBlocks(ship.getId()).add(pos.asLong())) reg.markDirty();
        int used = used(world, ship.getId()), max = limit(world, ship.getId());
        player.sendMessage(Text.literal("Ship blocks: " + used + "/" + max).formatted(used >= max ? Formatting.GOLD : Formatting.GRAY), true);
    }

    /** After a block was broken at pos - frees its slot right away. */
    public static void broken(ServerWorld world, BlockPos pos) {
        Ship ship = shipAt(world, pos);
        if (ship == null) return;
        ShipRegistryState reg = ShipRegistryState.get(world.getServer().getOverworld());
        if (reg.customBlocks(ship.getId()).remove(pos.asLong())) reg.markDirty();
    }
}
