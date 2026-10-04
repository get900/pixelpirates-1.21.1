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
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * LIAR'S DICE (2026-10-01). Up to 6 seats - players and tavern regulars (bots). Everyone hides 5 dice under a cup.
 * In turn each seat raises the bid ("at least Q dice in the WHOLE TABLE show face F": more dice, or the same number of a
 * higher face) or calls "Liar!" on the last bid. All cups lift: if the bid was short the bidder loses a die, otherwise
 * the caller does. Lose your last die and you are out; the last seat with dice takes the pot.
 * Stakes: the HOST (first to sit) sets the ante (0/5/10/25/50 pirate coins) while alone; everyone who sits pays it; the
 * house matches it for every regular at the start. Turns time out after 30 s (an automatic minimum raise, or a call when
 * no raise is possible). Leaving mid-game forfeits your dice; antes come back only in the lobby.
 */
public class LiarsDiceBlockEntity extends GameTableBlockEntity {
    public static final int MAX_SEATS = 6, DICE = 5, TURN_TICKS = 600, REVEAL_TICKS = 110, OVER_TICKS = 140;
    public static final int[] ANTES = {0, 5, 10, 25, 50};
    static final String[] REGULARS = {"One-Eyed Jack", "Salty Sal", "Barnacle Bill", "Mad Molly", "Old Pew", "Dicey Dan"};

    public enum Phase { LOBBY, BIDDING, REVEAL, OVER }

    static final class Seat {
        UUID id;                       // null for a regular
        String name;
        boolean bot;
        int left = DICE, paid;
        int[] dice = new int[0];
    }

    private final List<Seat> seats = new ArrayList<>();
    private Phase phase = Phase.LOBBY;
    private UUID host;
    private int anteIdx = 1, pot, turn = -1, bidQ, bidF, bidder = -1, revealCount = -1, loser = -1;
    private long deadline, botAt;
    private String winner = "";

    public LiarsDiceBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.LIARS_DICE, pos, state); }

    @Override
    public String game() { return "liars"; }

    @Override
    protected boolean timed() { return phase != Phase.LOBBY; }

    private int seatOf(UUID id) {
        for (int i = 0; i < seats.size(); i++) if (id.equals(seats.get(i).id)) return i;
        return -1;
    }

    private int humans() { int n = 0; for (Seat s : seats) if (!s.bot) n++; return n; }
    private int ante() { return ANTES[anteIdx]; }
    private int totalDice() { int n = 0; for (Seat s : seats) n += s.left; return n; }
    private int alive() { int n = 0; for (Seat s : seats) if (s.left > 0) n++; return n; }
    private long now() { return world == null ? 0 : world.getTime(); }

    // ------------------------------------------------------------------ actions
    @Override
    public void act(ServerPlayerEntity p, String action, int a, int b) {
        int me = seatOf(p.getUuid());
        boolean isHost = p.getUuid().equals(host);
        switch (action) {
            case "join" -> join(p, me);
            case "leave" -> leave(p, me);
            case "ante" -> { if (phase == Phase.LOBBY && isHost && humans() == 1) setAnte(p, me, a); }
            case "bot+" -> { if (phase == Phase.LOBBY && isHost && seats.size() < MAX_SEATS) addRegular(); }
            case "bot-" -> { if (phase == Phase.LOBBY && isHost) removeRegular(); }
            case "start" -> { if (phase == Phase.LOBBY && isHost && seats.size() >= 2) start(); }
            case "bid" -> { if (phase == Phase.BIDDING && me == turn && me >= 0) bid(me, a, b); }
            case "liar" -> { if (phase == Phase.BIDDING && me == turn && me >= 0 && bidder >= 0) call(me); }
            default -> { }
        }
        broadcast();
    }

    private void join(ServerPlayerEntity p, int me) {
        if (phase != Phase.LOBBY || me >= 0) return;
        if (seats.size() >= MAX_SEATS) { p.sendMessage(Text.literal("The table is full.").formatted(Formatting.RED), true); return; }
        if (!TavernGames.take(p, ante())) { p.sendMessage(Text.literal("You need " + ante() + " pirate coins to sit in.").formatted(Formatting.RED), true); return; }
        Seat s = new Seat();
        s.id = p.getUuid();
        s.name = p.getName().getString();
        s.paid = ante();
        pot += s.paid;
        seats.add(s);
        if (host == null) host = p.getUuid();
        log(s.name + " sits down" + (ante() > 0 ? " (" + ante() + " coins in the pot)" : ""));
        sound(SoundEvents.BLOCK_WOOD_PLACE, 1.2f);
    }

    private void leave(ServerPlayerEntity p, int me) {
        if (me < 0) return;
        Seat s = seats.get(me);
        if (phase == Phase.LOBBY) {
            TavernGames.pay(p, s.paid);
            pot -= s.paid;
            seats.remove(me);
            log(s.name + " leaves the table");
            if (p.getUuid().equals(host)) {
                host = null;
                for (Seat o : seats) if (!o.bot) { host = o.id; log(o.name + " now runs the table"); break; }
            }
            if (humans() == 0) { seats.clear(); pot = 0; }
        } else if (s.left > 0) {
            s.left = 0;
            s.dice = new int[0];
            log(s.name + " walks away and forfeits");
            if (phase == Phase.BIDDING && turn == me) nextTurn();
            if (alive() <= 1) over((ServerWorld) world);
        }
    }

    private void setAnte(ServerPlayerEntity p, int me, int delta) {
        int ni = Math.max(0, Math.min(ANTES.length - 1, anteIdx + Integer.signum(delta)));
        if (ni == anteIdx || me < 0) return;
        Seat s = seats.get(me);
        int diff = ANTES[ni] - s.paid;
        if (diff > 0 && !TavernGames.take(p, diff)) { p.sendMessage(Text.literal("You need " + diff + " more pirate coins.").formatted(Formatting.RED), true); return; }
        if (diff < 0) TavernGames.pay(p, -diff);
        anteIdx = ni;
        s.paid = ANTES[ni];
        pot += diff;
        log("Ante set to " + ante() + " coins");
    }

    private void addRegular() {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (world instanceof ServerWorld sw) names.addAll(net.get900.pixelpirates.homestead.town.TownLife.regularsNear(sw, pos));   // the townsfolk at the table first
        names.addAll(java.util.List.of(REGULARS));
        for (String n : names) {
            boolean used = false;
            for (Seat s : seats) used |= n.equals(s.name);
            if (used) continue;
            Seat s = new Seat();
            s.bot = true;
            s.name = n;
            seats.add(s);
            log(n + " pulls up a stool");
            return;
        }
    }

    private void removeRegular() {
        for (int i = seats.size() - 1; i >= 0; i--) if (seats.get(i).bot) { log(seats.get(i).name + " wanders off"); seats.remove(i); return; }
    }

    /** Starts a game (also used by /pptavern with only regulars at the table). */
    public void start() {
        for (Seat s : seats) {
            if (s.bot) pot += ante();                  // the house stakes its regulars
            s.left = DICE;
        }
        rollAll();
        turn = world.random.nextInt(seats.size());
        bidQ = bidF = 0;
        bidder = -1;
        loser = -1;
        revealCount = -1;
        winner = "";
        phase = Phase.BIDDING;
        armTurn();
        log("Cups down! " + seats.get(turn).name + " opens the bidding");
        sound(SoundEvents.BLOCK_BAMBOO_WOOD_HIT, 0.8f);
    }

    private void rollAll() {
        Random r = world.random;
        for (Seat s : seats) {
            s.dice = new int[s.left];
            for (int i = 0; i < s.left; i++) s.dice[i] = 1 + r.nextInt(6);
        }
    }

    private boolean legal(int q, int f) {
        if (f < 1 || f > 6 || q < 1 || q > totalDice()) return false;
        return bidder < 0 || q > bidQ || (q == bidQ && f > bidF);
    }

    private void bid(int who, int q, int f) {
        if (!legal(q, f)) return;
        bidQ = q;
        bidF = f;
        bidder = who;
        log(seats.get(who).name + " bids " + q + " x " + f + (q == 1 ? "" : "s"));
        sound(SoundEvents.BLOCK_WOODEN_BUTTON_CLICK_ON, 1.0f);
        nextTurn();
    }

    private void call(int who) {
        int count = 0;
        for (Seat s : seats) for (int d : s.dice) if (d == bidF) count++;
        revealCount = count;
        boolean held = count >= bidQ;
        loser = held ? who : bidder;
        log(seats.get(who).name + " calls LIAR on " + bidQ + " x " + bidF + "s - there are " + count + "!");
        log(seats.get(loser).name + " loses a die");
        phase = Phase.REVEAL;
        deadline = now() + REVEAL_TICKS;
        sound(SoundEvents.ENTITY_VILLAGER_NO, 0.9f);
    }

    private int nextAlive(int from) {
        for (int k = 1; k <= seats.size(); k++) {
            int i = (from + k) % seats.size();
            if (seats.get(i).left > 0) return i;
        }
        return from;
    }

    private void nextTurn() {
        turn = nextAlive(turn);
        armTurn();
    }

    private void armTurn() {
        deadline = now() + TURN_TICKS;
        botAt = now() + 30 + world.random.nextInt(40);
    }

    // ------------------------------------------------------------------ the loop
    @Override
    protected void tickGame(ServerWorld w, long now) {
        switch (phase) {
            case BIDDING -> {
                if (turn < 0 || turn >= seats.size()) { phase = Phase.LOBBY; return; }
                Seat s = seats.get(turn);
                if (s.left <= 0) { nextTurn(); return; }
                if (s.bot ? now >= botAt : now >= deadline) {
                    if (s.bot) botMove(turn); else autoMove(turn);
                    broadcast();
                }
            }
            case REVEAL -> {
                if (now < deadline) return;
                Seat l = seats.get(loser);
                l.left = Math.max(0, l.left - 1);
                if (l.left == 0) log(l.name + " is out of dice!");
                if (alive() <= 1) { over(w); broadcast(); return; }
                rollAll();
                turn = l.left > 0 ? loser : nextAlive(loser);
                bidQ = bidF = 0;
                bidder = -1;
                revealCount = -1;
                loser = -1;
                phase = Phase.BIDDING;
                armTurn();
                log("New round - " + seats.get(turn).name + " opens");
                sound(SoundEvents.BLOCK_BAMBOO_WOOD_HIT, 0.8f);
                broadcast();
            }
            case OVER -> {
                if (now < deadline) return;
                seats.clear();
                host = null;
                pot = 0;
                phase = Phase.LOBBY;
                winner = "";
                log("The table is open - take a seat!");
                broadcast();
            }
            default -> { }
        }
    }

    private void over(ServerWorld w) {
        Seat win = null;
        for (Seat s : seats) if (s.left > 0) win = s;
        phase = Phase.OVER;
        deadline = now() + OVER_TICKS;
        if (win == null) { winner = "Nobody"; return; }
        winner = win.name;
        java.util.List<UUID> hs = new java.util.ArrayList<>();
        java.util.List<String> bots = new java.util.ArrayList<>();
        for (Seat s : seats) { if (s.bot) bots.add(s.name); else hs.add(s.id); }
        net.get900.pixelpirates.homestead.town.TownLife.diceOver(w, hs, bots, win.name, win.bot);              // the townsfolk remember
        log(win.name + " wins the pot of " + pot + " coins!");
        if (!win.bot) payOut(w, win.id, pot);
        announce(Text.literal("[Liar's Dice] " + win.name + " wins" + (pot > 0 ? " " + pot + " pirate coins!" : "!")).formatted(Formatting.GOLD));
        sound(win.bot ? SoundEvents.ENTITY_VILLAGER_CELEBRATE : SoundEvents.ENTITY_PLAYER_LEVELUP, 1f);
        pot = 0;
    }

    /** A human who ran out the clock: the smallest raise, or a call when no raise is possible. */
    private void autoMove(int who) {
        log(seats.get(who).name + " ran out of time");
        if (bidder >= 0 && bidQ >= totalDice() && bidF == 6) { call(who); return; }
        if (bidder < 0) bid(who, 1, 2);
        else if (bidF < 6) bid(who, bidQ, bidF + 1);
        else bid(who, bidQ + 1, 1);
    }

    /** The regulars: estimate how likely the standing bid is from their own dice and the rest of the table. */
    private void botMove(int who) {
        Seat s = seats.get(who);
        Random r = world.random;
        int unknown = totalDice() - s.left;
        if (bidder >= 0) {
            double p = atLeast(unknown, bidQ - count(s.dice, bidF));
            if (p < 0.30 + r.nextDouble() * 0.15) { call(who); return; }
        }
        int best = 1 + r.nextInt(6);
        for (int f = 1; f <= 6; f++) if (count(s.dice, f) > count(s.dice, best)) best = f;
        int q;
        if (bidder < 0) q = Math.max(1, count(s.dice, best) + (int) Math.floor(unknown / 6.0) - r.nextInt(2));
        else q = best > bidF ? bidQ : bidQ + 1;
        if (!legal(q, best)) {                                         // fall back to the smallest raise
            if (bidder >= 0 && bidF < 6) { q = bidQ; best = bidF + 1; } else q = bidQ + 1;
        }
        if (q > totalDice()) { call(who); return; }
        double pq = atLeast(unknown, q - count(s.dice, best));
        if (bidder >= 0 && pq < 0.35 && r.nextDouble() > 0.2) { call(who); return; }   // 20% of the time they bluff on
        bid(who, q, best);
    }

    private static int count(int[] dice, int f) { int n = 0; for (int d : dice) if (d == f) n++; return n; }

    /** P(at least k of n unseen dice show a given face). */
    static double atLeast(int n, int k) {
        if (k <= 0) return 1;
        if (k > n) return 0;
        double p = 0;
        for (int j = k; j <= n; j++) p += choose(n, j) * Math.pow(1 / 6.0, j) * Math.pow(5 / 6.0, n - j);
        return p;
    }

    private static double choose(int n, int k) { double c = 1; for (int i = 1; i <= k; i++) c = c * (n - k + i) / i; return c; }

    private void sound(net.minecraft.sound.SoundEvent e, float pitch) {
        if (world != null) world.playSound(null, pos, e, SoundCategory.BLOCKS, 0.7f, pitch);
    }

    // ------------------------------------------------------------------ test hook (/pptavern liars)
    /** Seats {@code n} regulars and starts a bots-only game - the server soak test. */
    public void debugBots(int n) {
        seats.clear();
        pot = 0;
        for (int i = 0; i < n; i++) addRegular();
        start();
        broadcast();
    }

    public String debugStatus() {
        StringBuilder b = new StringBuilder(phase.name() + " pot " + pot + " bid " + bidQ + "x" + bidF + " turn " + turn + " |");
        for (Seat s : seats) b.append(' ').append(s.name).append(':').append(s.left);
        if (!winner.isEmpty()) b.append(" winner ").append(winner);
        return b.toString();
    }

    public Phase phase() { return phase; }

    // ------------------------------------------------------------------ view + persistence
    @Override
    protected NbtCompound view(ServerPlayerEntity v) {
        NbtCompound n = new NbtCompound();
        int me = seatOf(v.getUuid());
        n.putString("Phase", phase.name());
        n.putInt("Ante", ante());
        int bots = 0;
        for (Seat s : seats) if (s.bot) bots++;
        n.putInt("Pot", pot + (phase == Phase.LOBBY ? bots * ante() : 0));
        n.putBoolean("Host", v.getUuid().equals(host));
        n.putBoolean("Alone", humans() == 1);
        n.putInt("Me", me);
        n.putInt("Turn", turn);
        n.putInt("BidQ", bidQ);
        n.putInt("BidF", bidF);
        n.putInt("Bidder", bidder);
        n.putInt("Total", totalDice());
        n.putInt("TimeLeft", (int) Math.max(0, deadline - now()));
        n.putInt("RevealCount", revealCount);
        n.putInt("Loser", loser);
        n.putString("Winner", winner);
        boolean open = phase == Phase.REVEAL || phase == Phase.OVER;
        NbtList l = new NbtList();
        for (int i = 0; i < seats.size(); i++) {
            Seat s = seats.get(i);
            NbtCompound c = new NbtCompound();
            c.putString("N", s.name);
            c.putBoolean("Bot", s.bot);
            c.putInt("Left", phase == Phase.LOBBY ? DICE : s.left);
            if (i == me || open) c.putIntArray("Dice", s.dice);
            l.add(c);
        }
        n.put("Seats", l);
        return n;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putString("Phase", phase.name());
        if (host != null) nbt.putUuid("Host", host);
        nbt.putInt("AnteIdx", anteIdx);
        nbt.putInt("PotC", pot);
        nbt.putInt("Turn", turn);
        nbt.putInt("BidQ", bidQ);
        nbt.putInt("BidF", bidF);
        nbt.putInt("Bidder", bidder);
        nbt.putInt("Loser", loser);
        nbt.putInt("RevealCount", revealCount);
        nbt.putLong("Deadline", deadline);
        nbt.putString("Winner", winner);
        NbtList l = new NbtList();
        for (Seat s : seats) {
            NbtCompound c = new NbtCompound();
            if (s.id != null) c.putUuid("Id", s.id);
            c.putString("N", s.name);
            c.putBoolean("Bot", s.bot);
            c.putInt("Left", s.left);
            c.putInt("Paid", s.paid);
            c.putIntArray("Dice", s.dice);
            l.add(c);
        }
        nbt.put("Seats", l);
        writeLog(nbt);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        try { phase = Phase.valueOf(nbt.getString("Phase")); } catch (IllegalArgumentException e) { phase = Phase.LOBBY; }
        host = nbt.containsUuid("Host") ? nbt.getUuid("Host") : null;
        anteIdx = Math.max(0, Math.min(ANTES.length - 1, nbt.contains("AnteIdx") ? nbt.getInt("AnteIdx") : 1));
        pot = nbt.getInt("PotC");
        turn = nbt.getInt("Turn");
        bidQ = nbt.getInt("BidQ");
        bidF = nbt.getInt("BidF");
        bidder = nbt.contains("Bidder") ? nbt.getInt("Bidder") : -1;
        loser = nbt.contains("Loser") ? nbt.getInt("Loser") : -1;
        revealCount = nbt.contains("RevealCount") ? nbt.getInt("RevealCount") : -1;
        deadline = nbt.getLong("Deadline");
        winner = nbt.getString("Winner");
        seats.clear();
        for (NbtElement e : nbt.getList("Seats", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            Seat s = new Seat();
            s.id = c.containsUuid("Id") ? c.getUuid("Id") : null;
            s.name = c.getString("N");
            s.bot = c.getBoolean("Bot");
            s.left = c.getInt("Left");
            s.paid = c.getInt("Paid");
            s.dice = c.getIntArray("Dice");
            seats.add(s);
        }
        readLog(nbt);
    }
}
