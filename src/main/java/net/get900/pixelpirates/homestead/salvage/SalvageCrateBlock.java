package net.get900.pixelpirates.homestead.salvage;

import net.get900.pixelpirates.homestead.furniture.StorageBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Waterloggable;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;

/** SALVAGE CRATE (#9): a barnacled cargo crate lying on the seabed, still sealed; 27 slots of whatever went down with it. */
public class SalvageCrateBlock extends StorageBlock implements Waterloggable {
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    public SalvageCrateBlock(Settings s) {
        super(s, 3, false, new double[]{1, 0, 1, 15, 14, 15});
        setDefaultState(getDefaultState().with(WATERLOGGED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { super.appendProperties(b); b.add(WATERLOGGED); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return super.getPlacementState(ctx).with(WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).getFluid() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState s) { return s.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(s); }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState s, Direction d, BlockState n, WorldAccess w, BlockPos p, BlockPos np) {
        if (s.get(WATERLOGGED)) w.scheduleFluidTick(p, Fluids.WATER, Fluids.WATER.getTickRate(w));
        return super.getStateForNeighborUpdate(s, d, n, w, p, np);
    }
}
