package net.get900.pixelpirates.homestead.furniture;

import net.get900.pixelpirates.entity.mob.BossProgression;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.homestead.Navigation;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * MAP TABLE: charts spread under a brass compass. Right-click to plot a course: bearing + distance to your hideout, your
 * last death, and the lair of the next boss on your chain (the same prediction worldgen uses).
 */
public class MapTableBlock extends FurnitureBlock {
    public MapTableBlock(Settings s, double[]... boxes) { super(s, true, boxes); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
        world.playSound(null, pos, SoundEvents.ITEM_BOOK_PAGE_TURN, SoundCategory.BLOCKS, 1f, 0.9f);
        sp.sendMessage(Text.literal("=== You unroll the charts ===").formatted(Formatting.GOLD), false);
        HomesteadState st = HomesteadState.get(sp.getServer());
        HomesteadState.Hideout h = st.hideout(sp.getUuid());
        sp.sendMessage(h == null ? Text.literal(" Hideout: none claimed (plant a Jolly Roger flag)").formatted(Formatting.GRAY)
                : Text.literal(" Hideout: " + Navigation.bearing(pos, h.pos())).formatted(Formatting.AQUA), false);
        BlockPos death = st.lastDeath(sp.getUuid());
        if (death != null) sp.sendMessage(Text.literal(" Where you last fell: " + Navigation.bearing(pos, death)).formatted(Formatting.RED), false);
        BossProgression.Step next = Navigation.nextBoss(sp);
        if (next == null) sp.sendMessage(Text.literal(" Every lair on the chart is crossed out. Nothing left to hunt.").formatted(Formatting.GREEN), false);
        else {
            BlockPos lair = Navigation.locate(sp.getServerWorld(), next.lair(), pos, 12);
            sp.sendMessage(lair == null ? Text.literal(" " + cap(next.name()) + ": not on these charts (wrong waters?)").formatted(Formatting.DARK_PURPLE)
                    : Text.literal(" " + cap(next.name()) + ": " + Navigation.bearing(pos, lair)).formatted(Formatting.LIGHT_PURPLE), false);
        }
        return ActionResult.SUCCESS;
    }

    static String cap(String s) { return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1); }
}
