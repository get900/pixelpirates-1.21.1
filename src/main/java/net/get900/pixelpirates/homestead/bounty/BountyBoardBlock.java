package net.get900.pixelpirates.homestead.bounty;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * BOUNTY BOARD (#16, rebuilt 2026-10-01 as a 3 wide x 2 high notice board with a menu). Six blocks: PART = (dx+1) + 3*dy
 * with dx along {@code facing.rotateYClockwise()} and dy up; PART 1 (bottom centre) is the MASTER and draws the whole
 * board (a 48x32 px model that fits the -16..32 element range), the other five are invisible collision parts.
 * Breaking any part breaks the master (which drops the board once) and the rest go with it. Use = BountyScreen.
 */
public class BountyBoardBlock extends HorizontalFacingBlock {
    public static final IntProperty PART = IntProperty.of("part", 0, 5);
    public static final int MASTER = 1;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);
    static {
        for (Direction d : Direction.Type.HORIZONTAL)
            SHAPES.put(d, net.get900.pixelpirates.homestead.furniture.FurnitureBlock.rotated(new double[][]{{0, 0, 6.5, 16, 16, 9.5}}, d));
    }

    public BountyBoardBlock(Settings s) {
        super(s);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH).with(PART, MASTER));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(FACING, PART); }

    /** World position of part {@code part} of the board whose master stands at {@code master}. */
    public static BlockPos partPos(BlockPos master, Direction facing, int part) {
        int dx = part % 3 - 1, dy = part / 3;
        return master.offset(facing.rotateYClockwise(), dx).up(dy);
    }

    public static BlockPos masterOf(BlockPos pos, BlockState state) {
        int part = state.get(PART), dx = part % 3 - 1, dy = part / 3;
        return pos.offset(state.get(FACING).rotateYClockwise(), -dx).down(dy);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        Direction facing = ctx.getHorizontalPlayerFacing().getOpposite();
        BlockPos master = ctx.getBlockPos();
        for (int part = 0; part < 6; part++) {
            BlockPos p = partPos(master, facing, part);
            if (p.getY() >= ctx.getWorld().getTopY() || !ctx.getWorld().getBlockState(p).canReplace(ctx)) return null;
        }
        return getDefaultState().with(FACING, facing).with(PART, MASTER);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        if (world.isClient) return;
        for (int part = 0; part < 6; part++)
            if (part != MASTER) world.setBlockState(partPos(pos, state.get(FACING), part), state.with(PART, part), Block.NOTIFY_ALL);
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        // a non-master part broken by a player: break the master properly so the board drops exactly once
        if (!world.isClient && state.get(PART) != MASTER) {
            BlockPos m = masterOf(pos, state);
            if (world.getBlockState(m).isOf(this)) world.breakBlock(m, !player.isCreative(), player);
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && !world.isClient) {
            BlockPos m = masterOf(pos, state);
            for (int part = 0; part < 6; part++) {
                BlockPos p = partPos(m, state.get(FACING), part);
                if (!p.equals(pos) && world.getBlockState(p).isOf(this))
                    world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return SHAPES.get(s.get(FACING)); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity sp) Bounties.openBoard(sp);
        return ActionResult.success(world.isClient);
    }
}
