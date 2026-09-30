package net.get900.pixelpirates.homestead.roulette;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The roulette table's game: IDLE -> BETTING (3 s after the first stake, anyone may join) -> SPINNING (5 s) -> pay out.
 * The result is rolled when the wheel starts and synced, so every client's wheel (RouletteRenderer) eases to rest with
 * that pocket under the marker. Bets survive a restart (NBT); winners who have left are paid onto the table as items.
 */
public class RouletteTableBlockEntity extends BlockEntity implements GeoBlockEntity {
    public enum Bet {
        RED(2), BLACK(2), ODD(2), EVEN(2), LOW(2), HIGH(2), ZERO(36);
        public final int pays;
        Bet(int pays) { this.pays = pays; }

        boolean wins(int n) {
            return switch (this) {
                case RED -> n != 0 && RED_NUMBERS.contains(n);
                case BLACK -> n != 0 && !RED_NUMBERS.contains(n);
                case ODD -> n != 0 && n % 2 == 1;
                case EVEN -> n != 0 && n % 2 == 0;
                case LOW -> n >= 1 && n <= 18;
                case HIGH -> n >= 19;
                case ZERO -> n == 0;
            };
        }
    }

    public static final Set<Integer> RED_NUMBERS = Set.of(1, 3, 5, 7, 9, 12, 14, 16, 18, 19, 21, 23, 25, 27, 30, 32, 34, 36);
    /** European wheel order - pocket i of the model (tools/mobs/roulette.py POCKETS). */
    public static final int[] POCKETS = {0, 32, 15, 19, 4, 21, 2, 25, 17, 34, 6, 27, 13, 36, 11, 30, 8, 23, 10, 5, 24, 16, 33, 1, 20, 14, 31, 9, 22,
            18, 29, 7, 28, 12, 35, 3, 26};
    public static final int BETTING_TICKS = 60, SPIN_TICKS = 100, MAX_STAKE = 16;

    private record Stake(UUID player, String name, Bet bet, int coins) {}

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final List<Stake> stakes = new ArrayList<>();
    private final Map<UUID, Bet> choice = new HashMap<>();
    private int phase;                 // 0 idle, 1 betting, 2 spinning
    private long phaseEnd, spinStart = -1000;
    private int result = -1;

    public RouletteTableBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.ROULETTE_TABLE, pos, state); }

    public long spinStart() { return spinStart; }

    public int result() { return result; }

    public static int pocketOf(int number) {
        for (int i = 0; i < POCKETS.length; i++) if (POCKETS[i] == number) return i;
        return 0;
    }

    public static String describe(int n) { return n + (n == 0 ? " GREEN" : RED_NUMBERS.contains(n) ? " RED" : " BLACK"); }

    // ------------------------------------------------------------------ player actions
    public void cycleBet(PlayerEntity p) {
        Bet b = Bet.values()[(choice.getOrDefault(p.getUuid(), Bet.RED).ordinal() + 1) % Bet.values().length];
        choice.put(p.getUuid(), b);
        p.sendMessage(Text.literal("Your bet: " + b.name() + " (pays " + b.pays + "x)").formatted(Formatting.GOLD), true);
    }

    public void help(PlayerEntity p) {
        Bet b = choice.getOrDefault(p.getUuid(), Bet.RED);
        p.sendMessage(Text.literal("Roulette - use with pirate coins to stake up to " + MAX_STAKE + " on " + b.name()
                + " (pays " + b.pays + "x). Sneak-use to change your bet: red, black, odd, even, low 1-18, high 19-36, zero (36x).")
                .formatted(Formatting.GRAY), false);
    }

    public void stake(ServerPlayerEntity p, ItemStack coins) {
        if (phase == 2) {
            p.sendMessage(Text.literal("No more bets - the wheel is turning!").formatted(Formatting.RED), true);
            return;
        }
        int n = Math.min(MAX_STAKE, coins.getCount());
        Bet b = choice.getOrDefault(p.getUuid(), Bet.RED);
        coins.decrement(n);
        stakes.add(new Stake(p.getUuid(), p.getName().getString(), b, n));
        World w = getWorld();
        w.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.7f, 1.8f);
        p.sendMessage(Text.literal("Staked " + n + " coins on " + b.name()).formatted(Formatting.GOLD), true);
        if (phase == 0) {
            phase = 1;
            phaseEnd = w.getTime() + BETTING_TICKS;
            announce(Text.literal("[Roulette] Place your bets!").formatted(Formatting.GOLD));
        }
        sync();
    }

    // ------------------------------------------------------------------ the game loop
    public static void serverTick(World world, BlockPos pos, BlockState state, RouletteTableBlockEntity be) {
        long now = world.getTime();
        if (be.phase == 1 && now >= be.phaseEnd) {
            be.phase = 2;
            be.spinStart = now;
            be.result = POCKETS[world.random.nextInt(POCKETS.length)];
            be.announce(Text.literal("[Roulette] No more bets! The wheel spins...").formatted(Formatting.YELLOW));
            world.playSound(null, pos, SoundEvents.BLOCK_GRINDSTONE_USE, SoundCategory.BLOCKS, 0.6f, 1.6f);
            be.sync();
        } else if (be.phase == 2) {
            long t = now - be.spinStart;
            if (t < SPIN_TICKS && t % Math.max(2, t / 10) == 0)
                world.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.35f, 1.8f);   // the ball clattering
            if (t >= SPIN_TICKS) be.resolve((ServerWorld) world);
        }
    }

    private void resolve(ServerWorld w) {
        announce(Text.literal("[Roulette] The ball lands on " + describe(result) + "!").formatted(result == 0 ? Formatting.GREEN
                : RED_NUMBERS.contains(result) ? Formatting.RED : Formatting.DARK_GRAY));
        boolean anyWin = false;
        for (Stake s : stakes) {
            if (!s.bet().wins(result)) continue;
            anyWin = true;
            int won = s.coins() * s.bet().pays;
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(s.player());
            if (p != null && p.getWorld() == w && p.squaredDistanceTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) < 32 * 32) {
                pay(p, won);
                p.sendMessage(Text.literal("You win " + won + " pirate coins!").formatted(Formatting.GOLD), true);
            } else {
                for (int left = won; left > 0; left -= 64)
                    w.spawnEntity(new ItemEntity(w, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, new ItemStack(ModItems.PIRATE_COIN, Math.min(64, left))));
            }
        }
        w.playSound(null, pos, anyWin ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.BLOCKS, 0.7f, 1f);
        stakes.clear();
        phase = 0;
        sync();
    }

    private static void pay(PlayerEntity p, int coins) {
        while (coins > 0) {
            int n = Math.min(64, coins);
            coins -= n;
            ItemStack s = new ItemStack(ModItems.PIRATE_COIN, n);
            if (!p.getInventory().insertStack(s)) p.dropItem(s, false);
        }
    }

    private void announce(Text t) {
        if (!(getWorld() instanceof ServerWorld w)) return;
        for (PlayerEntity p : w.getEntitiesByClass(PlayerEntity.class, new Box(pos).expand(16), e -> true)) p.sendMessage(t, false);
    }

    private void sync() {
        markDirty();
        if (world != null) world.updateListeners(pos, getCachedState(), getCachedState(), 3);
    }

    // ------------------------------------------------------------------ persistence + sync
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("Phase", phase);
        nbt.putLong("PhaseEnd", phaseEnd);
        nbt.putLong("SpinStart", spinStart);
        nbt.putInt("Result", result);
        NbtList l = new NbtList();
        for (Stake s : stakes) {
            NbtCompound c = new NbtCompound();
            c.putUuid("P", s.player());
            c.putString("N", s.name());
            c.putString("B", s.bet().name());
            c.putInt("C", s.coins());
            l.add(c);
        }
        nbt.put("Stakes", l);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        phase = nbt.getInt("Phase");
        phaseEnd = nbt.getLong("PhaseEnd");
        spinStart = nbt.contains("SpinStart") ? nbt.getLong("SpinStart") : -1000;
        result = nbt.contains("Result") ? nbt.getInt("Result") : -1;
        stakes.clear();
        for (NbtElement e : nbt.getList("Stakes", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            stakes.add(new Stake(c.getUuid("P"), c.getString("N"), Bet.valueOf(c.getString("B")), c.getInt("C")));
        }
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() { return createNbt(); }

    @Nullable
    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() { return BlockEntityUpdateS2CPacket.create(this); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", 0, st -> st.setAndContinue(RawAnimation.begin().thenLoop("idle"))));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
