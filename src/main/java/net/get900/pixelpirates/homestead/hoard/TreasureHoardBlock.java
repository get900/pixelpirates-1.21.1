package net.get900.pixelpirates.homestead.hoard;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.IntProperty;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * TREASURE HOARD (#3): your pile of doubloons, in the open for all to see. Right-click with pirate coins to add the stack
 * (sneak: every coin you carry); empty hand takes a stack of 64 back. The pile grows with it (LEVEL 0-7). A fat hoard
 * lifts the crew: Luck within 12 blocks at 500 coins, Luck II + Hero of the Village at 2000. Break it and the coins stay
 * in the item.
 */
public class TreasureHoardBlock extends BlockWithEntity {
    public static final IntProperty LEVEL = IntProperty.of("level", 0, 7);
    static final long[] STEPS = {0, 1, 16, 64, 192, 512, 1280, 3000};

    public TreasureHoardBlock(Settings s) {
        super(s);
        setDefaultState(getStateManager().getDefaultState().with(LEVEL, 0));
    }

    public static int levelFor(long coins) {
        int l = 0;
        for (int i = 0; i < STEPS.length; i++) if (coins >= STEPS[i]) l = i;
        return l;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(LEVEL); }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) {
        return Block.createCuboidShape(1, 0, 1, 15, Math.max(2, 2 + s.get(LEVEL) * 2), 15);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new TreasureHoardBlockEntity(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, HomesteadBlockEntities.TREASURE_HOARD, TreasureHoardBlockEntity::tick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world.getBlockEntity(pos) instanceof TreasureHoardBlockEntity be)) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        ItemStack held = player.getStackInHand(hand);
        long moved = 0;
        if (held.isOf(ModItems.PIRATE_COIN)) {
            moved = held.getCount();
            if (!player.getAbilities().creativeMode) held.setCount(0);
        } else if (held.isEmpty() && player.isSneaking()) {
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack s = player.getInventory().getStack(i);
                if (s.isOf(ModItems.PIRATE_COIN)) { moved += s.getCount(); s.setCount(0); }
            }
        } else if (held.isEmpty() && be.coins > 0) {
            int take = (int) Math.min(64, be.coins);
            be.add(-take);
            player.setStackInHand(hand, new ItemStack(ModItems.PIRATE_COIN, take));
            world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_GOLD, SoundCategory.BLOCKS, 1f, 1.3f);
            player.sendMessage(be.status(), true);
            return ActionResult.SUCCESS;
        }
        if (moved > 0) {
            be.add(moved);
            world.playSound(null, pos, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.BLOCKS, 1f, 0.8f);
            world.playSound(null, pos, SoundEvents.ITEM_ARMOR_EQUIP_GOLD, SoundCategory.BLOCKS, 1f, 1.1f);
        }
        player.sendMessage(be.status(), true);
        return ActionResult.SUCCESS;
    }

    /** Broken by a player: the coins stay inside the item. */
    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && world.getBlockEntity(pos) instanceof TreasureHoardBlockEntity be) {
            ItemStack drop = new ItemStack(this);
            if (be.coins > 0) {
                NbtCompound nbt = new NbtCompound();
                nbt.putLong("Coins", be.coins);
                BlockItem.setBlockEntityNbt(drop, be.getType(), nbt);
            }
            if (!player.getAbilities().creativeMode || be.coins > 0) ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop);
            be.coins = 0;
        }
        super.onBreak(world, pos, state, player);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock()) && world.getBlockEntity(pos) instanceof TreasureHoardBlockEntity be && be.coins > 0) {
            long left = be.coins;                                                   // blown up: the coins scatter
            while (left > 0 && left < 100_000) {
                int n = (int) Math.min(64, left);
                ItemScatterer.spawn(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, new ItemStack(ModItems.PIRATE_COIN, n));
                left -= n;
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Override
    public boolean hasComparatorOutput(BlockState state) { return true; }

    @Override
    public int getComparatorOutput(BlockState state, World world, BlockPos pos) { return state.get(LEVEL) * 2; }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        NbtCompound nbt = BlockItem.getBlockEntityNbt(stack);
        long c = nbt == null ? 0 : nbt.getLong("Coins");
        tooltip.add(Text.literal(c > 0 ? c + " pirate coins" : "Empty - fill it with pirate coins").formatted(Formatting.GOLD));
        tooltip.add(Text.literal("500+: Luck nearby. 2000+: Luck II + Hero of the Village").formatted(Formatting.GRAY));
    }
}
