package net.get900.pixelpirates.homestead.hideout;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.world.ShipHealthState;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.Ship;

/** Holds the moored ship id; keeps it anchored and repairs it. Lets go if the ship drifts out of reach or is gone. */
public class MooringPostBlockEntity extends BlockEntity {
    @Nullable private Long ship;

    public MooringPostBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.MOORING_POST, pos, state); }

    @Nullable
    public Long ship() { return ship; }

    public void setShip(@Nullable Long id) {
        ship = id;
        markDirty();
    }

    static boolean homePort(ServerWorld w, BlockPos pos) { return HomesteadState.get(w.getServer()).hideoutAt(w.getRegistryKey(), pos) != null; }

    public static void serverTick(World world, BlockPos pos, BlockState state, MooringPostBlockEntity be) {
        if (be.ship == null || world.getTime() % 20 != 3) return;
        ServerWorld w = (ServerWorld) world;
        Ship near = MooringPostBlock.nearest(w, pos);
        if (near == null || near.getId() != be.ship) {                       // gone, sunk, or dragged away
            if (w.getServer().getTicks() > 600) {                        // give VS time to load ships after a restart
                ShipSteeringManager.ANCHORED_SHIPS.remove(be.ship);
                be.setShip(null);
            }
            return;
        }
        ShipSteeringManager.ANCHORED_SHIPS.add(be.ship);
        if (world.getTime() % 600 == 3) {
            boolean home = homePort(w, pos);
            ShipHealthState hs = ShipHealthState.get(w);
            int before = hs.getHealth(be.ship);
            int after = hs.repair(w, be.ship, home ? 16 : 8);
            if (after > before) w.spawnParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 6, 0.3, 0.3, 0.3, 0);
        }
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (ship != null) nbt.putLong("Ship", ship);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        ship = nbt.contains("Ship") ? nbt.getLong("Ship") : null;
    }
}
