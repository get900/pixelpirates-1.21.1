package net.get900.pixelpirates.homestead.tavern;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CROWN & ANCHOR (2026-10-01): the sailors' dice game. The board has six symbols - Crown, Anchor, Heart, Diamond, Club,
 * Spade - and three dice carry them. Stake pirate coins on any symbols (everyone at once); the first stake opens a 15 s
 * betting window, then the banker throws. Each die showing your symbol pays your stake again (1, 2 or 3 times) and you
 * keep the stake; no match loses it. Any bettor may hurry the throw ("roll" = 3 s).
 */
public class CrownAnchorBlockEntity extends GameTableBlockEntity {
    public static final String[] SYMBOLS = {"Crown", "Anchor", "Heart", "Diamond", "Club", "Spade"};
    public static final int BET_TICKS = 300, ROLL_TICKS = 50, RESULT_TICKS = 90, MAX_PER_SYMBOL = 64;

    public enum Phase { IDLE, BETTING, ROLLING, RESULT }

    private final Map<UUID, int[]> stakes = new LinkedHashMap<>();
    private final Map<UUID, String> names = new LinkedHashMap<>();
    private final Map<UUID, Integer> lastWin = new LinkedHashMap<>();
    private Phase phase = Phase.IDLE;
    private long phaseEnd;
    private int[] dice = {0, 1, 2};
    private boolean rolledOnce;

    public CrownAnchorBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.CROWN_ANCHOR, pos, state); }

    @Override
    public String game() { return "crown"; }

    @Override
    protected boolean timed() { return phase != Phase.IDLE; }

    private long now() { return world == null ? 0 : world.getTime(); }

    @Override
    public void act(ServerPlayerEntity p, String action, int a, int b) {
        UUID id = p.getUuid();
        switch (action) {
            case "stake" -> {
                if (phase == Phase.ROLLING || phase == Phase.RESULT) { p.sendMessage(Text.literal("Wait for the next throw.").formatted(Formatting.RED), true); break; }
                if (a < 0 || a >= 6) break;
                int[] mine = stakes.computeIfAbsent(id, k -> new int[6]);
                int amt = Math.max(1, Math.min(b, MAX_PER_SYMBOL - mine[a]));
                if (amt <= 0 || mine[a] >= MAX_PER_SYMBOL) break;
                if (!TavernGames.take(p, amt)) { p.sendMessage(Text.literal("You need " + amt + " pirate coins.").formatted(Formatting.RED), true); break; }
                mine[a] += amt;
                names.put(id, p.getName().getString());
                log(p.getName().getString() + " puts " + amt + " on the " + SYMBOLS[a]);
                world.playSound(null, pos, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.BLOCKS, 0.6f, 1.8f);
                if (phase == Phase.IDLE) { phase = Phase.BETTING; phaseEnd = now() + BET_TICKS; log("Place your bets - the banker throws soon!"); }
            }
            case "clear" -> {
                if (phase != Phase.BETTING) break;
                int[] mine = stakes.remove(id);
                if (mine == null) break;
                int back = 0;
                for (int s : mine) back += s;
                TavernGames.pay(p, back);
                log(p.getName().getString() + " takes back " + back + " coins");
                if (stakes.isEmpty()) phase = Phase.IDLE;
            }
            case "roll" -> {
                if (phase == Phase.BETTING && stakes.containsKey(id)) phaseEnd = Math.min(phaseEnd, now() + 60);
            }
            default -> { }
        }
        broadcast();
    }

    @Override
    protected void tickGame(ServerWorld w, long now) {
        if (phase == Phase.BETTING && now >= phaseEnd) {
            phase = Phase.ROLLING;
            phaseEnd = now + ROLL_TICKS;
            for (int i = 0; i < 3; i++) dice[i] = w.random.nextInt(6);
            log("The banker shakes the cup...");
            w.playSound(null, pos, SoundEvents.BLOCK_BAMBOO_WOOD_HIT, SoundCategory.BLOCKS, 0.8f, 0.7f);
            broadcast();
        } else if (phase == Phase.ROLLING) {
            if ((now - phaseEnd) % 6 == 0) w.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.4f, 1.4f + w.random.nextFloat() * 0.4f);
            if (now >= phaseEnd) resolve(w);
        } else if (phase == Phase.RESULT && now >= phaseEnd) {
            phase = Phase.IDLE;
            stakes.clear();
            broadcast();
        }
    }

    private void resolve(ServerWorld w) {
        rolledOnce = true;
        log("The dice: " + SYMBOLS[dice[0]] + ", " + SYMBOLS[dice[1]] + ", " + SYMBOLS[dice[2]]);
        lastWin.clear();
        boolean any = false;
        for (Map.Entry<UUID, int[]> e : stakes.entrySet()) {
            int won = 0, staked = 0;
            for (int s = 0; s < 6; s++) {
                int stake = e.getValue()[s];
                if (stake == 0) continue;
                staked += stake;
                int m = 0;
                for (int d : dice) if (d == s) m++;
                if (m > 0) won += stake * (m + 1);
            }
            lastWin.put(e.getKey(), won - staked);
            String n = names.getOrDefault(e.getKey(), "?");
            if (won > 0) { any = true; payOut(w, e.getKey(), won); log(n + " collects " + won + " coins"); }
            else log(n + " loses " + staked);
        }
        w.playSound(null, pos, any ? SoundEvents.ENTITY_PLAYER_LEVELUP : SoundEvents.ENTITY_VILLAGER_NO, SoundCategory.BLOCKS, 0.7f, 1.1f);
        phase = Phase.RESULT;
        phaseEnd = w.getTime() + RESULT_TICKS;
        broadcast();
    }

    // ------------------------------------------------------------------ view + persistence
    @Override
    protected NbtCompound view(ServerPlayerEntity v) {
        NbtCompound n = new NbtCompound();
        n.putString("Phase", phase.name());
        n.putInt("TimeLeft", (int) Math.max(0, phaseEnd - now()));
        n.putIntArray("Dice", phase == Phase.ROLLING || !rolledOnce ? new int[]{-1, -1, -1} : dice);
        int[] all = new int[6];
        for (int[] s : stakes.values()) for (int i = 0; i < 6; i++) all[i] += s[i];
        n.putIntArray("All", all);
        n.putIntArray("Mine", stakes.getOrDefault(v.getUuid(), new int[6]));
        n.putInt("Players", stakes.size());
        n.putInt("Net", lastWin.getOrDefault(v.getUuid(), 0));
        n.putBoolean("Played", lastWin.containsKey(v.getUuid()));
        return n;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("Phase", phase.name());
        nbt.putLong("PhaseEnd", phaseEnd);
        nbt.putIntArray("Dice", dice);
        nbt.putBoolean("Rolled", rolledOnce);
        NbtList l = new NbtList();
        for (Map.Entry<UUID, int[]> e : stakes.entrySet()) {
            NbtCompound c = new NbtCompound();
            c.putUuid("P", e.getKey());
            c.putString("N", names.getOrDefault(e.getKey(), "?"));
            c.putIntArray("S", e.getValue());
            l.add(c);
        }
        nbt.put("Stakes", l);
        writeLog(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        try { phase = Phase.valueOf(nbt.getString("Phase")); } catch (IllegalArgumentException e) { phase = Phase.IDLE; }
        phaseEnd = nbt.getLong("PhaseEnd");
        int[] d = nbt.getIntArray("Dice");
        if (d.length == 3) dice = d;
        rolledOnce = nbt.getBoolean("Rolled");
        stakes.clear();
        names.clear();
        for (NbtElement e : nbt.getList("Stakes", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            int[] s = c.getIntArray("S");
            if (s.length != 6) continue;
            stakes.put(c.getUuid("P"), s);
            names.put(c.getUuid("P"), c.getString("N"));
        }
        if (phase == Phase.RESULT) { phase = Phase.IDLE; stakes.clear(); }                      // already paid out
        if (phase == Phase.ROLLING) phase = stakes.isEmpty() ? Phase.IDLE : Phase.BETTING;      // a restart mid-throw rethrows
        readLog(nbt);
    }
}
