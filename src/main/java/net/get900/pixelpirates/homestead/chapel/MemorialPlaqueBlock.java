package net.get900.pixelpirates.homestead.chapel;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.List;

/**
 * The chapel's MEMORIAL PLAQUE (2026-10-04, round 3): read it to see the roll of captains lost at sea (homestead/town -
 * TownEvents.lostAtSea adds a name each time a player dies at sea; a memorial service is held the next morning).
 */
public class MemorialPlaqueBlock extends FurnitureBlock {
    public MemorialPlaqueBlock(Settings s, boolean solid, double[]... boxes) { super(s, solid, boxes); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!(world instanceof ServerWorld w)) return ActionResult.SUCCESS;
        List<String> roll = net.get900.pixelpirates.homestead.town.TownEvents.roll(w);
        player.sendMessage(Text.literal("-- In memory of those lost at sea --").formatted(Formatting.GRAY, Formatting.ITALIC), false);
        if (roll.isEmpty()) player.sendMessage(Text.literal("  (no names yet - may it stay that way)").formatted(Formatting.DARK_GRAY), false);
        int from = Math.max(0, roll.size() - 12);
        for (int i = roll.size() - 1; i >= from; i--) player.sendMessage(Text.literal("  " + roll.get(i)).formatted(Formatting.GRAY), false);
        if (from > 0) player.sendMessage(Text.literal("  ...and " + from + " more").formatted(Formatting.DARK_GRAY), false);
        return ActionResult.SUCCESS;
    }
}
