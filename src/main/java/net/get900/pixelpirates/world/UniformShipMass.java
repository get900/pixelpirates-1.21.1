package net.get900.pixelpirates.world;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import org.valkyrienskies.core.internal.world.chunks.VsiBlockType;
import org.valkyrienskies.mod.common.BlockStateInfo;
import org.valkyrienskies.mod.common.BlockStateInfoProvider;

/**
 * EVERY BLOCK WEIGHS THE SAME on a ship (2026-10-05; the user: "some designs have gold blocks and other blocks that make
 * the ships sink pretty bad on one end"). VS2 gives each block a real mass from its data files (planks 50, stone bricks
 * 250, iron block 785, gold block 1930 - one gold block = 38 planks), so a gilded stern or a stone galley dragged one end
 * under. This provider sits above VS2's own (priority 100) and answers {@link #MASS} - a plank's weight, so wooden hulls
 * keep their old total mass and speed - for every solid block; air and fluids are left to VS2. The block TYPE (solid /
 * fluid / air for collision) is always left to VS2. New and re-assembled ships use it; an existing ship picks it up on
 * VS2's /vs remass.
 */
public final class UniformShipMass implements BlockStateInfoProvider {
    public static final double MASS = 50.0;

    public static void register() {
        Registry.register(BlockStateInfo.INSTANCE.getREGISTRY(), new Identifier(PixelPirates.MOD_ID, "uniform_mass"), new UniformShipMass());
    }

    @Override
    public int getPriority() { return 1000; }

    @Override
    public Double getBlockStateMass(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() && state.getBlock() instanceof net.minecraft.block.FluidBlock) return null;
        return MASS;
    }

    @Override
    public VsiBlockType getBlockStateType(BlockState state) { return null; }
}
