package net.get900.pixelpirates.homestead.darts;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * A dart (2026-10-05): use to throw. In a game it counts only on your turn, and only from at least
 * {@link #MIN_RANGE} blocks out (the oche is 3 blocks from the board); the board hands your darts back after the turn.
 */
public class DartItem extends Item {
    public static final double MIN_RANGE = 2.0;

    public DartItem(Settings s) { super(s); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        user.getItemCooldownManager().set(this, 12);
        if (user instanceof ServerPlayerEntity sp) {
            BlockPos game = DartboardBlockEntity.gameOf(sp);
            DartboardBlockEntity board = game != null && world.getBlockEntity(game) instanceof DartboardBlockEntity b ? b : null;
            if (board != null && (board.current() == null || !sp.getUuid().equals(board.current().player))) {
                sp.sendMessage(Text.literal("[Darts] " + (board.current() == null ? "Wait for the game to start." : "It's " + board.current().name + "'s turn.")).formatted(Formatting.GRAY), true);
                return TypedActionResult.fail(stack);
            }
            boolean foul = board != null && sp.getEyePos().distanceTo(Vec3d.ofCenter(game)) < MIN_RANGE;
            world.spawnEntity(DartEntity.thrown(world, sp, board != null ? game : null, foul));
            world.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 0.4f, 1.5f);
        }
        user.swingHand(hand, true);
        if (!user.getAbilities().creativeMode) stack.decrement(1);
        return TypedActionResult.success(stack, world.isClient());
    }
}
