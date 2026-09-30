package net.get900.pixelpirates.homestead.rum;

import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * AGING CASK: a charred oak cask on a cradle. Right-click with raw or aged rum to lay a bottle down (up to 4); the rum
 * mellows with time (1 day -> aged, 2 more -> vintage). Empty hand draws every bottle off at whatever it has become;
 * sneak + empty hand just tells you how it's coming along.
 */
public class AgingCaskBlock extends BlockWithEntity {
    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 0, 15, 15, 16);
    private static final VoxelShape SHAPE_X = Block.createCuboidShape(0, 0, 1, 16, 15, 15);

    public AgingCaskBlock(Settings s) {
        super(s);
        setDefaultState(getStateManager().getDefaultState().with(HorizontalFacingBlock.FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(HorizontalFacingBlock.FACING); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(HorizontalFacingBlock.FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) {
        return s.get(HorizontalFacingBlock.FACING).getAxis() == Direction.Axis.Z ? SHAPE : SHAPE_X;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new AgingCaskBlockEntity(pos, state); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof AgingCaskBlockEntity be)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack held = player.getStackInHand(hand);
        int tier = Rum.tier(held.getItem());
        if (tier >= 0 && tier < 2) {
            if (!be.insert(tier, world.getTime())) { player.sendMessage(be.status(world.getTime()), true); return ActionResult.SUCCESS; }
            if (!player.getAbilities().creativeMode) held.decrement(1);
            world.playSound(null, pos, SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS, 1f, 0.8f);
            player.sendMessage(be.status(world.getTime()), true);
            return ActionResult.SUCCESS;
        }
        if (held.isEmpty() && !player.isSneaking() && be.count() > 0) {
            for (ItemStack s : be.drain(world.getTime())) player.getInventory().offerOrDrop(s);
            world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS, 1f, 0.7f);
            return ActionResult.SUCCESS;
        }
        player.sendMessage(be.status(world.getTime()), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof AgingCaskBlockEntity be)
            for (ItemStack s : be.drain(world.getTime())) ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
