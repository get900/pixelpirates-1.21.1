package net.get900.pixelpirates.homestead.rum;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * RUM STILL: a copper pot still. Needs a fire under it (campfire, fire, lava, magma). Right-click with molasses to fill
 * (up to 8, the bottles come back); every 20 s it distils one into rum; right-click with glass bottles to draw it off.
 * Empty hand: how it's getting on. LIT while it works.
 */
public class RumStillBlock extends BlockWithEntity {
    public static final BooleanProperty LIT = Properties.LIT;
    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 15, 15);

    public RumStillBlock(Settings s) {
        super(s);
        setDefaultState(getStateManager().getDefaultState().with(HorizontalFacingBlock.FACING, net.minecraft.util.math.Direction.NORTH).with(LIT, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(HorizontalFacingBlock.FACING, LIT); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(HorizontalFacingBlock.FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return SHAPE; }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new RumStillBlockEntity(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, HomesteadBlockEntities.RUM_STILL, RumStillBlockEntity::tick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof RumStillBlockEntity be)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack held = player.getStackInHand(hand);
        if (held.isOf(HomesteadItems.MOLASSES)) {
            int n = Math.min(held.getCount(), RumStillBlockEntity.CAP - be.molasses);
            if (n <= 0) { player.sendMessage(Text.literal("The still is full of molasses.").formatted(Formatting.GRAY), true); return ActionResult.SUCCESS; }
            be.molasses += n;
            if (!player.getAbilities().creativeMode) { held.decrement(n); player.getInventory().offerOrDrop(new ItemStack(Items.GLASS_BOTTLE, n)); }
            world.playSound(null, pos, SoundEvents.ITEM_BUCKET_EMPTY, SoundCategory.BLOCKS, 1f, 0.8f);
            be.markDirty();
            player.sendMessage(be.status(), true);
            return ActionResult.SUCCESS;
        }
        if (held.isOf(Items.GLASS_BOTTLE) && be.rum > 0) {
            int n = Math.min(held.getCount(), be.rum);
            be.rum -= n;
            if (!player.getAbilities().creativeMode) held.decrement(n);
            player.getInventory().offerOrDrop(new ItemStack(HomesteadItems.RAW_RUM, n));
            world.playSound(null, pos, SoundEvents.ITEM_BOTTLE_FILL, SoundCategory.BLOCKS, 1f, 0.9f);
            be.markDirty();
            return ActionResult.SUCCESS;
        }
        player.sendMessage(be.status(), true);
        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof RumStillBlockEntity be) {
            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(HomesteadItems.MOLASSES, be.molasses));
            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(HomesteadItems.RAW_RUM, be.rum));
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random r) {
        if (!state.get(LIT)) return;
        world.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.35 + r.nextDouble() * 0.3, pos.getY() + 1.0, pos.getZ() + 0.35 + r.nextDouble() * 0.3, 0, 0.03, 0);
        if (r.nextInt(3) == 0) world.addParticle(ParticleTypes.DRIPPING_HONEY, pos.getX() + 0.8, pos.getY() + 0.2, pos.getZ() + 0.2, 0, 0, 0);
        if (r.nextInt(6) == 0) world.playSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLOCK_BUBBLE_COLUMN_BUBBLE_POP,
                SoundCategory.BLOCKS, 0.6f, 0.7f + r.nextFloat() * 0.3f, false);
    }
}
