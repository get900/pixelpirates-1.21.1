package net.get900.pixelpirates.item.custom;

import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.furniture.HangingRopeBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * ROPE you can throw down (2026-10-01, user note: "we need to find a real use for the rope"). Use it on the top or side
 * of a ledge, cliff, shaft or ship's rail and a climbable line unrolls straight down - {@link #PER_ROPE} blocks per rope,
 * up to {@link #MAX} - stopping at the first solid block or water. Break any of it and the line reels back in as rope
 * (HangingRopeBlock.DEPLOYED, PixelPirates block-break hook). Climb cliffs, drop into dungeon shafts, board ships from the sea.
 */
public class RopeItem extends Item {
    public static final int PER_ROPE = 4, MAX = 24;

    public RopeItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext ctx) {
        World world = ctx.getWorld();
        PlayerEntity player = ctx.getPlayer();
        BlockPos clicked = ctx.getBlockPos();
        Direction side = ctx.getSide();
        if (world.getBlockState(clicked).isOf(HomesteadBlocks.HANGING_ROPE)) return ActionResult.PASS;
        // where the line starts: over the edge you clicked
        BlockPos start = switch (side) {
            case UP -> clicked.offset(ctx.getHorizontalPlayerFacing());          // the top of a ledge: hang over its far edge
            case DOWN -> clicked.down();
            default -> clicked.offset(side);
        };
        if (!world.getBlockState(start).isAir()) return ActionResult.PASS;
        ItemStack stack = ctx.getStack();
        boolean creative = player != null && player.getAbilities().creativeMode;
        int budget = creative ? MAX : Math.min(MAX, stack.getCount() * PER_ROPE);
        int placed = 0;
        BlockPos p = start;
        while (placed < budget && p.getY() > world.getBottomY() && world.getBlockState(p).isAir()) {
            placed++;
            p = p.down();
        }
        if (placed == 0) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        BlockState rope = HomesteadBlocks.HANGING_ROPE.getDefaultState().with(HangingRopeBlock.DEPLOYED, true);
        for (int i = 0; i < placed; i++)
            world.setBlockState(start.down(i), rope.with(HangingRopeBlock.END, i == placed - 1), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        if (!creative) stack.decrement((placed + PER_ROPE - 1) / PER_ROPE);
        world.playSound(null, start, SoundEvents.BLOCK_WOOL_PLACE, SoundCategory.BLOCKS, 1f, 0.7f);
        world.playSound(null, start, SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.BLOCKS, 1f, 0.9f);
        return ActionResult.CONSUME;
    }

    /** Breaking a deployed line: everything from here down goes, and comes back as rope (4 blocks = 1 rope). */
    public static boolean reel(World world, PlayerEntity player, BlockPos pos, BlockState state) {
        if (!state.isOf(HomesteadBlocks.HANGING_ROPE) || !state.get(HangingRopeBlock.DEPLOYED)) return true;
        int n = 0;
        BlockPos p = pos;
        while (world.getBlockState(p).isOf(HomesteadBlocks.HANGING_ROPE) && world.getBlockState(p).get(HangingRopeBlock.DEPLOYED)) {
            world.setBlockState(p, net.minecraft.block.Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL | Block.SKIP_DROPS);
            n++;
            p = p.down();
        }
        int back = Math.max(1, (n + PER_ROPE - 1) / PER_ROPE);
        if (!player.getAbilities().creativeMode) player.getInventory().offerOrDrop(new ItemStack(net.get900.pixelpirates.item.ModItems.ROPE, back));
        world.playSound(null, pos, SoundEvents.ENTITY_LEASH_KNOT_BREAK, SoundCategory.BLOCKS, 1f, 1f);
        return false;                                                            // handled - no normal break
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("Use on a ledge, cliff or ship's rail: a climbing line unrolls down").formatted(Formatting.GRAY));
        tooltip.add(Text.literal(PER_ROPE + " blocks per rope, up to " + MAX + " - break it to reel it back").formatted(Formatting.DARK_GRAY));
    }
}
