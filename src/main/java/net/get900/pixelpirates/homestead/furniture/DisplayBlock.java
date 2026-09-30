package net.get900.pixelpirates.homestead.furniture;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** TREASURE PEDESTAL: set any single item on it (right-click) and it turns slowly above the velvet; empty hand takes it back. */
public class DisplayBlock extends FurnitureBlock implements BlockEntityProvider {
    public DisplayBlock(Settings s, double[]... boxes) { super(s, true, boxes); }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new DisplayBlockEntity(pos, state); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof DisplayBlockEntity be)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack held = player.getStackInHand(hand);
        if (be.item().isEmpty() && !held.isEmpty()) {
            be.setItem(held.split(1));
            if (player.getAbilities().creativeMode) held.increment(1);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_ADD_ITEM, SoundCategory.BLOCKS, 1f, 1f);
        } else if (!be.item().isEmpty() && held.isEmpty()) {
            player.setStackInHand(hand, be.item());
            be.setItem(ItemStack.EMPTY);
            world.playSound(null, pos, SoundEvents.ENTITY_ITEM_FRAME_REMOVE_ITEM, SoundCategory.BLOCKS, 1f, 1f);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof DisplayBlockEntity be && !be.item().isEmpty())
            ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, be.item());
        super.onStateReplaced(state, world, pos, newState, moved);
    }
}
