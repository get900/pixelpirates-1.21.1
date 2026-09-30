package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class CannonBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    public static final BooleanProperty LOADED = BooleanProperty.of("loaded");

    public CannonBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
            .with(FACING, Direction.NORTH)
            .with(LOADED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, LOADED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
    }

    // ── Block entity ──────────────────────────────────────────────────────────

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CannonBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        if (world.isClient || type != ModBlocks.CANNON_BLOCK_ENTITY) return null;
        return (BlockEntityTicker<T>) (BlockEntityTicker<CannonBlockEntity>) CannonBlockEntity::serverTick;
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                              PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof CannonBlockEntity be)) return ActionResult.PASS;

        if (!state.get(LOADED)) {
            ItemStack held = player.getStackInHand(hand);
            int ammo = net.get900.pixelpirates.homestead.ship.Shot.of(held);
            if (ammo >= 0) {
                if (!player.isCreative()) held.decrement(1);
                be.setAmmo(ammo);
                world.setBlockState(pos, state.with(LOADED, true));
                world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_IRON, SoundCategory.BLOCKS, 1.0f, 0.8f);
                player.sendMessage(Text.literal("§eLoaded" + net.get900.pixelpirates.homestead.ship.Shot.label(ammo) + "! Right-click again to charge."), true);
            } else {
                player.sendMessage(Text.literal("§7Hold a cannonball to load."), true);
            }
            return ActionResult.SUCCESS;
        }

        if (!be.isCharging()) {
            // First click on a loaded cannon — light the fuse
            be.startCharge(player);
            world.playSound(null, pos, SoundEvents.BLOCK_FIRE_AMBIENT, SoundCategory.BLOCKS, 0.5f, 1.5f);
        } else {
            // Second click — fire at current charge level
            be.fireFromPlayer((ServerWorld) world, pos, state, player);
        }
        return ActionResult.SUCCESS;
    }
}
