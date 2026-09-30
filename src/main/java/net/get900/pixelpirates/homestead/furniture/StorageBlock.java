package net.get900.pixelpirates.homestead.furniture;

import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * SEA CHEST (keepsContents = true): a whole double chest in one block that keeps its cargo when you break it, like a
 * shulker box - carry your stash between ship and hideout. CARGO CRATE: an ordinary 27-slot crate that spills.
 */
public class StorageBlock extends FurnitureBlock implements BlockEntityProvider {
    final int rows;
    private final boolean keepsContents;

    public StorageBlock(Settings s, int rows, boolean keepsContents, double[]... boxes) {
        super(s, true, boxes);
        this.rows = rows;
        this.keepsContents = keepsContents;
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new StorageBlockEntity(pos, state); }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (world.getBlockEntity(pos) instanceof NamedScreenHandlerFactory f) {
            player.openHandledScreen(f);
            world.playSound(null, pos, keepsContents ? SoundEvents.BLOCK_CHEST_OPEN : SoundEvents.BLOCK_BARREL_OPEN, SoundCategory.BLOCKS, 0.6f, 0.9f);
        }
        return ActionResult.CONSUME;
    }

    /** Sea chest: broken by a player, it drops itself WITH its contents (and nothing spills). */
    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (keepsContents && !world.isClient && world.getBlockEntity(pos) instanceof StorageBlockEntity be) {
            ItemStack drop = new ItemStack(this);
            boolean full = !be.isEmptyInv();
            if (full) BlockItem.setBlockEntityNbt(drop, be.getType(), be.createNbt());
            if (!player.getAbilities().creativeMode || full) ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            be.clear();
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof StorageBlockEntity be) {
            ItemScatterer.spawn(world, pos, be);                                   // anything still inside (explosions, crates)
            world.updateComparators(pos, this);
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) { return true; }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) {
        return net.minecraft.screen.ScreenHandler.calculateComparatorOutput(world.getBlockEntity(pos));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        super.appendTooltip(stack, world, tooltip, options);
        if (keepsContents) tooltip.add(Text.literal("Keeps its cargo when broken").formatted(Formatting.GRAY));
        NbtCompound nbt = BlockItem.getBlockEntityNbt(stack);
        if (nbt != null && nbt.contains("Items")) {
            NbtList l = nbt.getList("Items", 10);
            tooltip.add(Text.literal(l.size() + " stacks inside").formatted(Formatting.GOLD));
        }
    }
}
