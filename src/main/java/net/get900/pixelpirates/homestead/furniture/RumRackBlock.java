package net.get900.pixelpirates.homestead.furniture;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.rum.Rum;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** RUM RACK: a wall rack of six cubbies. Right-click with any rum to shelve a bottle, empty hand to take the last one down. */
public class RumRackBlock extends FurnitureBlock implements BlockEntityProvider {
    public static final IntProperty BOTTLES = IntProperty.of("bottles", 0, 6);

    public RumRackBlock(Settings s, double[]... boxes) {
        super(s, true, boxes);
        setDefaultState(getDefaultState().with(BOTTLES, 0));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { super.appendProperties(b); b.add(BOTTLES); }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new Entity(pos, state); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof Entity be)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack held = player.getStackInHand(hand);
        int tier = Rum.tier(held.getItem());
        if (tier >= 0 && be.n < 6) {
            be.tiers[be.n++] = tier;
            if (!player.getAbilities().creativeMode) held.decrement(1);
            world.playSound(null, pos, SoundEvents.BLOCK_GLASS_PLACE, SoundCategory.BLOCKS, 0.8f, 1.2f);
        } else if (held.isEmpty() && be.n > 0) {
            player.setStackInHand(hand, new ItemStack(Rum.item(be.tiers[--be.n])));
            world.playSound(null, pos, SoundEvents.BLOCK_GLASS_HIT, SoundCategory.BLOCKS, 0.8f, 1.2f);
        } else return ActionResult.SUCCESS;
        be.markDirty();
        world.setBlockState(pos, state.with(BOTTLES, be.n), 3);
        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof Entity be)
            for (int i = 0; i < be.n; i++) ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(Rum.item(be.tiers[i])));
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    public static class Entity extends BlockEntity {
        final int[] tiers = new int[6];
        int n;

        public Entity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.RUM_RACK, pos, state); }

        @Override
        protected void writeNbt(NbtCompound nbt) { nbt.putInt("N", n); nbt.putIntArray("Tiers", tiers); }

        @Override
        public void readNbt(NbtCompound nbt) {
            n = Math.min(6, nbt.getInt("N"));
            int[] t = nbt.getIntArray("Tiers");
            for (int i = 0; i < 6; i++) tiers[i] = i < t.length ? t[i] : 0;
        }
    }
}
