package net.get900.pixelpirates.homestead.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;

/** ROPE BRIDGE: a span of lashed slats with rope rails, laid along the way you face. Walk on it; it never falls. */
public class RopeBridgeBlock extends Block implements Waterloggable {
    public static final EnumProperty<Direction.Axis> AXIS = Properties.HORIZONTAL_AXIS;
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    private static final VoxelShape FLOOR = Block.createCuboidShape(0, 0, 0, 16, 2, 16);
    private static final VoxelShape Z = VoxelShapes.union(FLOOR, Block.createCuboidShape(0, 0, 0, 1, 12, 16), Block.createCuboidShape(15, 0, 0, 16, 12, 16));
    private static final VoxelShape X = VoxelShapes.union(FLOOR, Block.createCuboidShape(0, 0, 0, 16, 12, 1), Block.createCuboidShape(0, 0, 15, 16, 12, 16));

    public RopeBridgeBlock(Settings s) {
        super(s);
        setDefaultState(getStateManager().getDefaultState().with(AXIS, Direction.Axis.Z).with(WATERLOGGED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(AXIS, WATERLOGGED); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(AXIS, ctx.getHorizontalPlayerFacing().getAxis())
                .with(WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).getFluid() == Fluids.WATER);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return s.get(AXIS) == Direction.Axis.Z ? Z : X; }

    @Override
    public FluidState getFluidState(BlockState s) { return s.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(s); }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState s, Direction d, BlockState n, WorldAccess w, BlockPos p, BlockPos np) {
        if (s.get(WATERLOGGED)) w.scheduleFluidTick(p, Fluids.WATER, Fluids.WATER.getTickRate(w));
        return super.getStateForNeighborUpdate(s, d, n, w, p, np);
    }
}
