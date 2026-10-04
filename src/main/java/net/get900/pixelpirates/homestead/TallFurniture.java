package net.get900.pixelpirates.homestead;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Furniture two blocks tall (the easel, the swings): a LOWER and an UPPER half that place and break together, face the
 * player, drop one item (from either half). {@code hangs} = it hangs DOWN from the block it's placed under (the clicked
 * spot becomes the UPPER half, which needs something solid above it).
 */
public class TallFurniture extends HorizontalFacingBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = Properties.DOUBLE_BLOCK_HALF;
    private final Map<Direction, VoxelShape> lower = new EnumMap<>(Direction.class), upper = new EnumMap<>(Direction.class);
    protected final boolean hangs;

    public TallFurniture(Settings s, double[] lowerBox, double[] upperBox) { this(s, lowerBox, upperBox, false); }

    public TallFurniture(Settings s, double[] lowerBox, double[] upperBox, boolean hangs) {
        super(s);
        this.hangs = hangs;
        for (Direction d : Direction.Type.HORIZONTAL) {
            lower.put(d, FurnitureBlock.rotated(new double[][]{lowerBox}, d));
            upper.put(d, FurnitureBlock.rotated(new double[][]{upperBox}, d));
        }
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(FACING, HALF); }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) {
        return (s.get(HALF) == DoubleBlockHalf.LOWER ? lower : upper).get(s.get(FACING));
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        BlockPos pos = ctx.getBlockPos();
        World w = ctx.getWorld();
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        if (hangs) {
            if (pos.getY() <= w.getBottomY() + 1 || !w.getBlockState(pos.down()).canReplace(ctx)) return null;
            BlockState above = w.getBlockState(pos.up());
            if (!above.isSideSolidFullSquare(w, pos.up(), Direction.DOWN) && !(above.getBlock() instanceof net.minecraft.block.FenceBlock)) return null;
            return getDefaultState().with(FACING, facing).with(HALF, DoubleBlockHalf.UPPER);
        }
        if (pos.getY() >= w.getTopY() - 1 || !w.getBlockState(pos.up()).canReplace(ctx)) return null;
        return getDefaultState().with(FACING, facing).with(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (state.get(HALF) == DoubleBlockHalf.LOWER) world.setBlockState(pos.up(), state.with(HALF, DoubleBlockHalf.UPPER), 3);
        else world.setBlockState(pos.down(), state.with(HALF, DoubleBlockHalf.LOWER), 3);
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        if (!hangs || state.get(HALF) == DoubleBlockHalf.LOWER) return true;
        BlockState above = world.getBlockState(pos.up());
        return above.isSideSolidFullSquare(world, pos.up(), Direction.DOWN) || above.getBlock() instanceof net.minecraft.block.FenceBlock;
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction dir, BlockState other, WorldAccess world, BlockPos pos, BlockPos otherPos) {
        DoubleBlockHalf half = state.get(HALF);
        if (dir.getAxis() == Direction.Axis.Y && (half == DoubleBlockHalf.LOWER) == (dir == Direction.UP)) {
            if (!other.isOf(this) || other.get(HALF) == half) return Blocks.AIR.getDefaultState();
        }
        if (hangs && half == DoubleBlockHalf.UPPER && dir == Direction.UP && !canPlaceAt(state, world, pos)) return Blocks.AIR.getDefaultState();
        return super.getStateForNeighborUpdate(state, dir, other, world, pos, otherPos);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        // break the other half without a second drop
        BlockPos other = state.get(HALF) == DoubleBlockHalf.LOWER ? pos.up() : pos.down();
        BlockState o = world.getBlockState(other);
        if (!world.isClient && o.isOf(this) && o.get(HALF) != state.get(HALF)) world.setBlockState(other, Blocks.AIR.getDefaultState(), 35);
        super.onBreak(world, pos, state, player);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContextParameterSet.Builder builder) {
        return super.getDroppedStacks(state, builder);                                    // the half you broke drops the item; the other is removed silently
    }
}
