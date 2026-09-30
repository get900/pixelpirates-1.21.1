package net.get900.pixelpirates.homestead.furniture;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import net.minecraft.item.ItemPlacementContext;

/**
 * HANGING ROPE: a knotted rope that hangs from whatever is above it (another rope, a beam, a mast) and can be climbed
 * (CLIMBABLE tag). The last rope in a run ends in a knot (END). Place it under a block or another rope.
 */
public class HangingRopeBlock extends Block {
    public static final BooleanProperty END = BooleanProperty.of("end");
    private static final VoxelShape SHAPE = Block.createCuboidShape(6.5, 0, 6.5, 9.5, 16, 9.5);

    public HangingRopeBlock(Settings s) {
        super(s);
        setDefaultState(getStateManager().getDefaultState().with(END, true));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(END); }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return SHAPE; }

    @Override
    public boolean canPlaceAt(BlockState s, WorldView w, BlockPos p) {
        BlockState up = w.getBlockState(p.up());
        return up.isOf(this) || up.isSideSolidFullSquare(w, p.up(), Direction.DOWN) || Block.sideCoversSmallSquare(w, p.up(), Direction.DOWN);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(END, !ctx.getWorld().getBlockState(ctx.getBlockPos().down()).isOf(this));
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState s, Direction d, BlockState n, WorldAccess w, BlockPos p, BlockPos np) {
        if (d == Direction.UP && !canPlaceAt(s, w, p)) return net.minecraft.block.Blocks.AIR.getDefaultState();
        if (d == Direction.DOWN) return s.with(END, !n.isOf(this));
        return s;
    }
}
