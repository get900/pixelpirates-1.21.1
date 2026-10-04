package net.get900.pixelpirates.homestead.forge;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** THE SMITH'S PATTERN BOARD: right-click to read every forging pattern (Forging.PATTERNS) and how the forge works. */
public class PatternBoardBlock extends FurnitureBlock {
    public PatternBoardBlock(Settings s, double[]... boxes) { super(s, false, boxes); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient || hand != Hand.MAIN_HAND) return ActionResult.success(world.isClient);
        player.sendMessage(Text.literal("[~] The Smith's Patterns").formatted(Formatting.GOLD, Formatting.BOLD), false);
        player.sendMessage(Text.literal("Lay the blade on a Forge Anvil, add the materials, strike it with a Smith's Hammer by a lit Forge Hearth"
                + " (6 strikes, 4 with bellows at the hearth). A damaged piece + its repair material is mended free.").formatted(Formatting.GRAY), false);
        for (Forging.Pattern p : Forging.PATTERNS) player.sendMessage(Text.literal(" * ").formatted(Formatting.DARK_GRAY).append(Forging.describe(p)), false);
        return ActionResult.SUCCESS;
    }
}
