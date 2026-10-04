package net.get900.pixelpirates.homestead.tavern;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** A tavern game table: watchers, the shared event log, per-viewer state packets, the tick hook. See TavernGames. */
public abstract class GameTableBlockEntity extends BlockEntity {
    protected final Set<UUID> watchers = new HashSet<>();
    protected final List<String> log = new ArrayList<>();
    private int lastPulse;

    protected GameTableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }

    /** "liars" or "crown" - picks the client screen. */
    public abstract String game();

    /** The state as {@code viewer} may see it. */
    protected abstract NbtCompound view(ServerPlayerEntity viewer);

    public abstract void act(ServerPlayerEntity p, String action, int a, int b);

    protected abstract void tickGame(ServerWorld w, long now);

    /** True while a countdown runs - watchers then get a refresh every second. */
    protected abstract boolean timed();

    public void open(ServerPlayerEntity p) {
        watchers.add(p.getUuid());
        send(p, true);
    }

    public void close(ServerPlayerEntity p) { watchers.remove(p.getUuid()); }

    protected void send(ServerPlayerEntity p, boolean open) {
        NbtCompound n = view(p);
        n.putString("Game", game());
        n.putLong("Pos", pos.asLong());
        n.putBoolean("Open", open);
        n.putInt("Coins", TavernGames.coins(p));
        n.putBoolean("Creative", p.getAbilities().creativeMode);
        NbtList l = new NbtList();
        for (String s : log) l.add(NbtString.of(s));
        n.put("Log", l);
        TavernGames.sendState(p, n);
    }

    /** Saves and refreshes every watcher still near the table. */
    protected void broadcast() {
        markDirty();
        if (!(world instanceof ServerWorld w)) return;
        Vec3d c = Vec3d.ofCenter(pos);
        watchers.removeIf(id -> {
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(id);
            return p == null || p.getWorld() != w || p.squaredDistanceTo(c) > 14 * 14;
        });
        for (UUID id : watchers) send(w.getServer().getPlayerManager().getPlayer(id), false);
    }

    protected void log(String s) {
        log.add(s);
        while (log.size() > 8) log.remove(0);
    }

    /** Chat line to everyone within 12 blocks (big results only). */
    protected void announce(Text t) {
        if (!(world instanceof ServerWorld w)) return;
        for (PlayerEntity p : w.getEntitiesByClass(PlayerEntity.class, new Box(pos).expand(12), e -> true)) p.sendMessage(t, false);
    }

    /** Pays a winner who is still about, or leaves the coins on the table for them. */
    protected void payOut(ServerWorld w, UUID id, int coins) {
        if (coins <= 0) return;
        ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(id);
        if (p != null && p.getWorld() == w && p.squaredDistanceTo(Vec3d.ofCenter(pos)) < 32 * 32) { TavernGames.pay(p, coins); return; }
        for (int left = coins; left > 0; left -= 64)
            w.spawnEntity(new ItemEntity(w, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
                    new ItemStack(net.get900.pixelpirates.item.ModItems.PIRATE_COIN, Math.min(64, left))));
    }

    public static void tick(World world, BlockPos pos, BlockState state, GameTableBlockEntity be) {
        if (!(world instanceof ServerWorld w)) return;
        long now = w.getTime();
        be.tickGame(w, now);
        if (be.timed() && !be.watchers.isEmpty() && ++be.lastPulse >= 20) { be.lastPulse = 0; be.broadcast(); }
    }

    protected void writeLog(NbtCompound nbt) {
        NbtList l = new NbtList();
        for (String s : log) l.add(NbtString.of(s));
        nbt.put("Log", l);
    }

    protected void readLog(NbtCompound nbt) {
        log.clear();
        for (NbtElement e : nbt.getList("Log", NbtElement.STRING_TYPE)) log.add(e.asString());
    }
}
