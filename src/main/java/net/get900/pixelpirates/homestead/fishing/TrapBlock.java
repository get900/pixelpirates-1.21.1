package net.get900.pixelpirates.homestead.fishing;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

/**
 * FISH TRAP NET and LOBSTER POT: set them in water and they fish for you. The net pulls from the local fish (open water
 * around it helps); the pot sits on the bottom, needs bait (chum or any raw fish, up to 8) and brings up lobster, crab
 * claws and the odd curio. Right-click with an empty hand to empty it (sneak: see how it's doing).
 */
public class TrapBlock extends BlockWithEntity implements Waterloggable {
    public static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;
    final boolean pot;
    private final VoxelShape shape;

    public TrapBlock(Settings s, boolean pot) {
        super(s);
        this.pot = pot;
        this.shape = pot ? Block.createCuboidShape(2, 0, 2, 14, 11, 14) : Block.createCuboidShape(0, 0, 0, 16, 12, 16);
        setDefaultState(getStateManager().getDefaultState().with(WATERLOGGED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(WATERLOGGED); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(WATERLOGGED, ctx.getWorld().getFluidState(ctx.getBlockPos()).getFluid() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState s) { return s.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(s); }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState s, Direction d, BlockState n, WorldAccess w, BlockPos p, BlockPos np) {
        if (s.get(WATERLOGGED)) w.scheduleFluidTick(p, Fluids.WATER, Fluids.WATER.getTickRate(w));
        return super.getStateForNeighborUpdate(s, d, n, w, p, np);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return shape; }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new TrapBlockEntity(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, HomesteadBlockEntities.TRAP, TrapBlockEntity::tick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof TrapBlockEntity be)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack held = player.getStackInHand(hand);
        if (pot && TrapBlockEntity.isBait(held) && be.bait < 8) {
            int n = Math.min(held.getCount(), 8 - be.bait);
            be.bait += n;
            if (!player.getAbilities().creativeMode) held.decrement(n);
            be.markDirty();
            world.playSound(null, pos, SoundEvents.ENTITY_FISHING_BOBBER_SPLASH, SoundCategory.BLOCKS, 0.6f, 1.2f);
            player.sendMessage(be.status(), true);
            return ActionResult.SUCCESS;
        }
        if (held.isEmpty() && !player.isSneaking() && !be.isEmpty()) {
            for (int i = 0; i < be.size(); i++) {
                ItemStack s = be.removeStack(i);
                if (!s.isEmpty()) player.getInventory().offerOrDrop(s);
            }
            world.playSound(null, pos, SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.BLOCKS, 1f, 0.9f);
            return ActionResult.SUCCESS;
        }
        player.sendMessage(be.status(), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof TrapBlockEntity be) ItemScatterer.spawn(world, pos, be);
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
