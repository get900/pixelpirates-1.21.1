package net.get900.pixelpirates.homestead.darts;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.homestead.town.TownDarts;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A game of 301 at one board (2026-10-05). Not saved: a game in progress is forgotten if the chunk unloads.
 * LOBBY (players join by using the board; a recruited regular walks over) -> PLAY (three darts a turn, exactly 0 wins,
 * going below 0 is a bust and the turn's darts don't count) -> OVER (a short pause) -> IDLE.
 * Townsfolk throw on their turn through {@link TownDarts#throwDart}; every dart, thrown by anyone, ends in {@link #landed}.
 */
public class DartboardBlockEntity extends BlockEntity {
    public enum Phase { IDLE, LOBBY, PLAY, OVER }

    public static final class Thrower {
        public final String folk;          // townsperson id, or null for a player
        public final UUID player;
        public final String name;
        public int left = Darts.START, turnStart = Darts.START;
        public final List<String> turn = new ArrayList<>();

        Thrower(String folk, UUID player, String name) { this.folk = folk; this.player = player; this.name = name; }

        public boolean isNpc() { return folk != null; }
    }

    /** Player -> the board they're in a game at (DartItem tags the dart with it). */
    static final Map<UUID, BlockPos> ACTIVE = new HashMap<>();

    public Phase phase = Phase.IDLE;
    public final List<Thrower> throwers = new ArrayList<>();
    public int cur, darts;
    private long phaseAt, turnAt, nextNpcThrow, turnEndAt;
    private boolean bust;
    private final List<Integer> stuck = new ArrayList<>();
    private final Map<UUID, Integer> owed = new HashMap<>();             // darts to hand back after the turn

    public DartboardBlockEntity(BlockPos pos, BlockState state) { super(HomesteadBlockEntities.DARTBOARD, pos, state); }

    public Direction facing() { return getCachedState().get(DartboardBlock.FACING); }

    /** Where a thrower stands: three blocks out from the board, on the floor. */
    public BlockPos oche() { return pos.offset(facing(), 3).down(); }

    public Thrower current() { return phase == Phase.PLAY && cur < throwers.size() ? throwers.get(cur) : null; }

    public static BlockPos gameOf(ServerPlayerEntity p) { return ACTIVE.get(p.getUuid()); }

    // ------------------------------------------------------------------ joining
    void use(ServerPlayerEntity p) {
        ServerWorld w = (ServerWorld) world;
        Thrower me = throwers.stream().filter(t -> p.getUuid().equals(t.player)).findFirst().orElse(null);
        switch (phase) {
            case IDLE -> {
                reset(w);
                throwers.add(new Thrower(null, p.getUuid(), p.getName().getString()));
                ACTIVE.put(p.getUuid(), pos);
                handDarts(p);
                phase = Phase.LOBBY;
                phaseAt = w.getTime();
                String regular = TownDarts.recruit(w, this);
                if (regular != null) say(w, regular + " puts down a tankard - \"301, captain? You're on.\" (Others can join: use the board.)");
                else say(w, "Nobody's free for a game - practice it is. Starting in a moment (others can join: use the board).");
            }
            case LOBBY -> {
                if (me != null) { if (!TownDarts.npcComing(this)) start(w); return; }       // the host starts it early
                if (throwers.size() >= 4) { p.sendMessage(Text.literal("[Darts] The game's full.").formatted(Formatting.GRAY), true); return; }
                throwers.add(new Thrower(null, p.getUuid(), p.getName().getString()));
                ACTIVE.put(p.getUuid(), pos);
                handDarts(p);
                say(w, p.getName().getString() + " joins the game.");
            }
            case PLAY, OVER -> {
                if (me != null && p.isSneaking()) { leave(w, me, " throws in the towel."); return; }
                p.sendMessage(Text.literal("[Darts] " + scores() + (me != null ? "   (sneak + use to leave)" : "")).formatted(Formatting.GOLD), false);
            }
        }
    }

    /** A townsperson waiting at the board for an opponent (TownDarts, regulars playing each other). */
    public void npcWaiting(ServerWorld w, String folk, String name) {
        reset(w);
        throwers.add(new Thrower(folk, null, name));
        phase = Phase.LOBBY;
        phaseAt = w.getTime();
    }

    /** A second townsperson (or the recruited regular) joins the lobby. */
    public void npcJoin(String folk, String name) {
        if (phase != Phase.LOBBY || throwers.size() >= 4) return;
        throwers.add(new Thrower(folk, null, name));
    }

    /** A townsperson walked away from the board (TownDarts: bedtime, the service). */
    public void npcLeft(ServerWorld w, String folk) {
        throwers.stream().filter(t -> folk.equals(t.folk)).findFirst().ifPresent(t -> leave(w, t, " heads off."));
    }

    private void handDarts(ServerPlayerEntity p) {
        int have = p.getInventory().count(HomesteadItems.DART);
        if (have < 3) {
            p.getInventory().offerOrDrop(new ItemStack(HomesteadItems.DART, 3 - have));
            p.sendMessage(Text.literal("[Darts] The barkeep slides you a set of darts.").formatted(Formatting.GRAY), true);
        }
    }

    private void start(ServerWorld w) {
        if (throwers.isEmpty()) { reset(w); return; }
        phase = Phase.PLAY;
        cur = 0;
        newTurn(w);
        say(w, "Game on! 301, three darts a turn, finish on exactly nothing. " + throwers.get(0).name + " to throw first.");
    }

    // ------------------------------------------------------------------ the game
    void tick(ServerWorld w) {
        long now = w.getTime();
        if (phase == Phase.IDLE) return;
        if (phase == Phase.LOBBY) {
            boolean anyPlayer = throwers.stream().anyMatch(t -> !t.isNpc());
            if (anyPlayer && !TownDarts.npcComing(this) && now - phaseAt > 160) start(w);
            else if (!anyPlayer && throwers.size() >= 2 && TownDarts.allAtOche(w, this)) start(w);
            else if (now - phaseAt > 900) {                                      // the regular never came / nobody else did
                if (anyPlayer) { throwers.removeIf(Thrower::isNpc); TownDarts.releaseAll(w, this); start(w); }
                else if (throwers.size() == 1) { say(w, throwers.get(0).name + " gives up waiting and practises alone."); start(w); }
            }
            return;
        }
        if (phase == Phase.OVER) {
            if (now - phaseAt > 80) reset(w);
            return;
        }
        Thrower t = current();
        if (t == null) { reset(w); return; }
        if (turnEndAt > 0) {
            if (now >= turnEndAt) endTurn(w);
            return;
        }
        if (t.isNpc()) {
            if (!TownDarts.atOche(w, this, t.folk)) {
                if (now - turnAt > 600) leave(w, t, " wanders off.");
                return;
            }
            if (now >= nextNpcThrow) {
                nextNpcThrow = now + 30 + w.random.nextInt(20);
                TownDarts.throwDart(w, this, t.folk, t.left);
            }
        } else {
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(t.player);
            if (p == null || p.squaredDistanceTo(Vec3d.ofCenter(pos)) > 20 * 20) { leave(w, t, " has left the tavern."); return; }
            if (now - turnAt > 900) {
                say(w, t.name + " takes too long - the turn passes.");
                turnEndAt = now;
            }
        }
    }

    /** A dart has stopped: in the board (onBoard) or anywhere else (a miss). */
    public void landed(ServerWorld w, DartEntity dart, Vec3d hit, boolean onBoard) {
        Thrower t = current();
        boolean ours = t != null && turnEndAt == 0 && darts < 3
                && (t.isNpc() ? t.folk.equals(dart.folk()) : t.player.equals(dart.thrower()));
        if (!ours) {
            if (dart.thrower() != null && phase == Phase.PLAY) { owe(dart.thrower()); dart.discard(); }
            return;
        }
        stuck.add(dart.getId());
        if (!t.isNpc()) owe(t.player);
        Darts.Hit h;
        if (dart.foul()) h = new Darts.Hit(0, "Foul - step back to the oche");
        else if (!onBoard) h = new Darts.Hit(0, "Miss");
        else { double[] uv = Darts.uv(pos, facing(), hit); h = Darts.score(uv[0], uv[1]); }
        darts++;
        t.turn.add(h.label());
        int after = t.left - h.value();
        if (h.value() >= 50) w.playSound(null, pos, SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), SoundCategory.BLOCKS, 0.6f, 1.6f);
        if (after < 0) {
            bust = true;
            t.left = t.turnStart;
            say(w, t.name + ": " + h.label() + " - BUST! Back to " + t.left + ".");
            turnEndAt = w.getTime() + 30;
        } else if (after == 0) {
            t.left = 0;
            win(w, t, h);
        } else {
            t.left = after;
            if (darts >= 3) turnEndAt = w.getTime() + 30;
            String line = t.name + ": " + h.label() + (h.value() > 0 && !h.label().equals(String.valueOf(h.value())) ? " (" + h.value() + ")" : "") + " - " + t.left + " left";
            sayNear(w, line);
        }
    }

    private void win(ServerWorld w, Thrower t, Darts.Hit h) {
        say(w, t.name + " checks out with " + h.label() + " and wins! (" + String.join(", ", t.turn) + ")");
        w.playSound(null, pos, SoundEvents.ENTITY_PLAYER_LEVELUP, SoundCategory.BLOCKS, 0.7f, 1.2f);
        TownDarts.result(w, this, t);
        phase = Phase.OVER;
        phaseAt = w.getTime();
        clearDarts(w);
    }

    private void endTurn(ServerWorld w) {
        Thrower t = current();
        turnEndAt = 0;
        if (t != null && !bust && darts > 0) {
            int scored = t.turnStart - t.left;
            if (scored >= 100) say(w, t.name + " scores " + (scored == 180 ? "ONE HUNDRED AND EIGHTY!" : scored + "!"));
        }
        clearDarts(w);
        if (throwers.isEmpty()) { reset(w); return; }
        cur = (cur + 1) % throwers.size();
        newTurn(w);
        if (throwers.size() > 1 || throwers.get(0).isNpc()) sayNear(w, throwers.get(cur).name + " to throw - " + throwers.get(cur).left + " left.");
    }

    private void newTurn(ServerWorld w) {
        Thrower t = throwers.get(cur);
        t.turnStart = t.left;
        t.turn.clear();
        darts = 0;
        bust = false;
        turnAt = w.getTime();
        nextNpcThrow = turnAt + 40;
    }

    private void leave(ServerWorld w, Thrower t, String why) {
        int i = throwers.indexOf(t);
        if (i < 0) return;
        say(w, t.name + why);
        throwers.remove(i);
        if (t.player != null) { ACTIVE.remove(t.player); owe(t.player); }
        if (t.isNpc()) TownDarts.release(w, t.folk);
        if (throwers.isEmpty()) { reset(w); return; }
        if (phase == Phase.PLAY) {
            if (i < cur) cur--;
            if (cur >= throwers.size()) cur = 0;
            if (i == cur || i == throwers.size()) { clearDarts(w); newTurn(w); }
        }
    }

    private void owe(UUID player) { owed.merge(player, 1, Integer::sum); }

    /** Pull the darts out of the board; the players' darts go back to them. */
    private void clearDarts(ServerWorld w) {
        for (int id : stuck) { Entity e = w.getEntityById(id); if (e != null) e.discard(); }
        stuck.clear();
        for (var en : owed.entrySet()) {
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(en.getKey());
            if (p != null) p.getInventory().offerOrDrop(new ItemStack(HomesteadItems.DART, en.getValue()));
        }
        owed.clear();
    }

    public void reset(ServerWorld w) {
        clearDarts(w);
        for (Thrower t : throwers) if (t.player != null) ACTIVE.remove(t.player);
        TownDarts.releaseAll(w, this);
        throwers.clear();
        phase = Phase.IDLE;
        cur = darts = 0;
        turnEndAt = 0;
    }

    @Override
    public void markRemoved() {
        if (world instanceof ServerWorld w) reset(w);
        super.markRemoved();
    }

    // ------------------------------------------------------------------ talk
    public String scores() {
        if (throwers.isEmpty()) return "No game on.";
        return throwers.stream().map(t -> (t == current() ? "> " : "") + t.name + " " + t.left).collect(Collectors.joining("   "));
    }

    /** Everyone within 16 blocks (the game's news). */
    public void say(ServerWorld w, String s) {
        for (ServerPlayerEntity p : w.getPlayers(p -> p.squaredDistanceTo(Vec3d.ofCenter(pos)) < 16 * 16))
            p.sendMessage(Text.literal("[Darts] ").formatted(Formatting.GOLD).append(Text.literal(s).formatted(Formatting.WHITE)), false);
    }

    /** Dart-by-dart calls go to the action bar of those close by (chat would flood). */
    private void sayNear(ServerWorld w, String s) {
        for (ServerPlayerEntity p : w.getPlayers(p -> p.squaredDistanceTo(Vec3d.ofCenter(pos)) < 16 * 16))
            p.sendMessage(Text.literal("[Darts] " + s).formatted(Formatting.GOLD), true);
    }
}
