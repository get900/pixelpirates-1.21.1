package net.get900.pixelpirates.homestead.trade;

import net.get900.pixelpirates.homestead.HomesteadEntities;
import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * TRADING POST (#15): a merchant's counter for your hideout.
 *  - use with anything the port buys: sells the whole stack on the spot for 75% of a trader's price (the counter's cut);
 *  - sneak-use holding 32 pirate coins: hires a Port Trader (one of the kinds not already within 16 blocks) who sets up
 *    shop beside the counter and stays there;
 *  - use empty-handed: tells you what the thing in your off hand is worth, or how the counter works.
 */
public class TradingPostBlock extends FurnitureBlock {
    public static final int HIRE_COST = 32;
    /** Items one player may sell across all Trading Posts per game day. */
    public static final int DAILY_LIMIT = 64;

    public TradingPostBlock(Settings s) {
        super(s, true, new double[]{0, 0, 3, 16, 14, 16});
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        ItemStack held = player.getStackInHand(hand);
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (player.isSneaking() && held.isOf(ModItems.COIN)) return hire((ServerWorld) world, pos, player, held);
        int price = Prices.priceOf(held.getItem());
        if (price <= 0) {
            player.sendMessage(Text.literal(held.isEmpty()
                    ? "Trading Post: use it with fish, rum, curios or monster parts to sell them (75% of a trader's price). Sneak-use with "
                    + HIRE_COST + " doubloons to hire a trader."
                    : "\"Nobody at this counter wants that.\"").formatted(Formatting.GRAY), false);
            return ActionResult.CONSUME;
        }
        // DAILY LIMIT (2026-10-01): a post buys at most DAILY_LIMIT items from each player per game day - it was an open money tap
        var st = net.get900.pixelpirates.homestead.HomesteadState.get(world.getServer());
        long day = world.getTimeOfDay() / 24000L;
        int left = DAILY_LIMIT - st.postSold(player.getUuid(), day);
        if (left <= 0) {
            player.sendMessage(Text.literal("\"I've bought all I can carry today, sailor. Come back tomorrow.\" (" + DAILY_LIMIT
                    + " items a day)").formatted(Formatting.RED), true);
            return ActionResult.CONSUME;
        }
        int count = Math.min(held.getCount(), left);
        int coins = net.get900.pixelpirates.world.SkillEffects.fence(player, Math.max(1, (int) Math.floor(count * price * 0.75)));
        String what = held.getName().getString();
        held.decrement(count);
        st.addPostSold(player.getUuid(), day, count);
        give(player, coins);
        world.playSound(null, pos, SoundEvents.ENTITY_VILLAGER_TRADE, SoundCategory.BLOCKS, 0.8f, 1.1f);
        world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.6f, 1.6f);
        player.sendMessage(Text.literal("Sold " + count + " x " + what + " for " + coins + " doubloons").formatted(Formatting.GOLD), true);
        return ActionResult.CONSUME;
    }

    static void give(PlayerEntity player, int coins) {
        while (coins > 0) {
            int n = Math.min(64, coins);
            coins -= n;
            ItemStack s = new ItemStack(ModItems.COIN, n);
            if (!player.getInventory().insertStack(s)) player.dropItem(s, false);
        }
    }

    private ActionResult hire(ServerWorld world, BlockPos pos, PlayerEntity player, ItemStack held) {
        if (held.getCount() < HIRE_COST && !player.getAbilities().creativeMode) {
            player.sendMessage(Text.literal("Hiring a trader costs " + HIRE_COST + " doubloons.").formatted(Formatting.RED), true);
            return ActionResult.CONSUME;
        }
        EnumSet<PortTraderEntity.Kind> free = EnumSet.allOf(PortTraderEntity.Kind.class);
        for (PortTraderEntity t : world.getEntitiesByClass(PortTraderEntity.class, new Box(pos).expand(16), e -> true)) free.remove(t.kind());
        if (free.isEmpty()) {
            player.sendMessage(Text.literal("Every kind of trader already works this counter.").formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        List<PortTraderEntity.Kind> pick = new ArrayList<>(free);
        PortTraderEntity.Kind kind = pick.get(world.random.nextInt(pick.size()));
        BlockPos at = standingSpot(world, pos);
        PortTraderEntity t = HomesteadEntities.PORT_TRADER.create(world);
        if (t == null) return ActionResult.CONSUME;
        t.setKind(kind);
        t.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, player.getYaw() + 180, 0);
        t.setHome(at);
        t.initialize(world, world.getLocalDifficulty(at), SpawnReason.MOB_SUMMONED, null, null);
        world.spawnEntity(t);
        if (!player.getAbilities().creativeMode) held.decrement(HIRE_COST);
        world.playSound(null, pos, SoundEvents.ENTITY_WANDERING_TRADER_YES, SoundCategory.NEUTRAL, 1f, 1f);
        player.sendMessage(Text.literal("A " + t.kindName() + " takes up the counter.").formatted(Formatting.GOLD), false);
        return ActionResult.CONSUME;
    }

    /** The first open two-high spot next to the counter (behind it first), else on top of it. */
    private static BlockPos standingSpot(World w, BlockPos pos) {
        BlockState s = w.getBlockState(pos);
        var behind = s.contains(FACING) ? s.get(FACING).getOpposite() : net.minecraft.util.math.Direction.SOUTH;
        List<BlockPos> tries = new ArrayList<>(List.of(pos.offset(behind), pos.offset(behind.rotateYClockwise()), pos.offset(behind.rotateYCounterclockwise()),
                pos.offset(behind.getOpposite())));
        for (BlockPos p : tries)
            if (w.getBlockState(p).getCollisionShape(w, p).isEmpty() && w.getBlockState(p.up()).getCollisionShape(w, p.up()).isEmpty()
                    && !w.getBlockState(p.down()).getCollisionShape(w, p.down()).isEmpty()) return p;
        return pos.up();
    }
}
