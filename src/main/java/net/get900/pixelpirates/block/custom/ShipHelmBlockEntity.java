package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import org.valkyrienskies.core.api.ships.PhysShip;
import org.valkyrienskies.core.api.world.PhysLevel;
import org.valkyrienskies.mod.api.BlockEntityPhysicsListener;

public class ShipHelmBlockEntity extends BlockEntity implements BlockEntityPhysicsListener {

    private volatile String dimension = "";

    public ShipHelmBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SHIP_HELM_BLOCK_ENTITY, pos, state);
    }

    // All physics forces (buoyancy, drag, thrust, torque, sinking) are applied via
    // GameToPhysicsAdapter in ShipSteeringManager.tick() each server tick.
    // physTick is kept only to satisfy BlockEntityPhysicsListener; forces here would
    // be unreliable because VS2 registers the block entity before ship assembly,
    // leaving a null shipId so physShip is always null on first registration.
    @Override
    public void physTick(PhysShip physShip, PhysLevel physLevel) {}

    @Override
    public String getDimension() { return dimension; }

    @Override
    public void setDimension(String value) { this.dimension = value; }
}
