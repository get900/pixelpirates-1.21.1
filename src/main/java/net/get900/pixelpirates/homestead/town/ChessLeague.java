package net.get900.pixelpirates.homestead.town;

import net.get900.pixelpirates.homestead.chess.Chess;
import net.get900.pixelpirates.homestead.chess.ChessBoardEntity;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * THE WAVEBREAK CHESS LEAGUE (2026-10-05). Every rated game - player v player, player v townsperson, townsperson v
 * townsperson, on either board - moves both Elo ratings (K 32; the computer is unrated). Townsfolk start from their
 * chess level (950 / 1150, a little either side by name), players from 1000.
 *
 * THE TOURNAMENT (TownCalendar.CHESS_TOURNAMENT, every 12-16 days, at dusk): four play - the players who signed up at
 * the League board (sneak + use; up to two, by rating) and the best-rated townsfolk who play chess. Seeds 1v4 on the
 * giant board, 2v3 at the toymaker's table, then the final on the giant board. A player always takes white against a
 * townsperson; a player who isn't seated within 2.5 minutes forfeits; a draw goes to the higher seed; a townsfolk game
 * still going at 17500 (or after 7.5 minutes) is adjudicated by rating. The champion is named on the board and in the
 * news; a player champion wins 30 coins (runner-up 10).
 */
public final class ChessLeague {
    private ChessLeague() {}

    static final String KEY = "pixelpirates_chess_league";
    static final int K = 32, SEAT_WAIT = 3000, GAME_CAP = 9000;

    // ------------------------------------------------------------------ the table
    public static final class Entry {
        String name;
        int rating, played, won, lost, drawn;
    }

    public static final class State extends PersistentState {
        final Map<String, Entry> table = new LinkedHashMap<>();
        final Set<UUID> signed = new HashSet<>();
        String champion = "", championName = "";
        long lastTourney = -1;

        static State read(NbtCompound n) {
            State s = new State();
            NbtCompound t = n.getCompound("Table");
            for (String k : t.getKeys()) {
                NbtCompound e = t.getCompound(k);
                Entry en = new Entry();
                en.name = e.getString("Name"); en.rating = e.getInt("R"); en.played = e.getInt("P");
                en.won = e.getInt("W"); en.lost = e.getInt("L"); en.drawn = e.getInt("D");
                s.table.put(k, en);
            }
            for (NbtElement el : n.getList("Signed", NbtElement.STRING_TYPE)) {
                try { s.signed.add(UUID.fromString(el.asString())); } catch (IllegalArgumentException ignored) { }
            }
            s.champion = n.getString("Champion");
            s.championName = n.getString("ChampionName");
            s.lastTourney = n.contains("Last") ? n.getLong("Last") : -1;
            return s;
        }

        @Override
        public NbtCompound writeNbt(NbtCompound n) {
            NbtCompound t = new NbtCompound();
            for (var en : table.entrySet()) {
                NbtCompound e = new NbtCompound();
                Entry x = en.getValue();
                e.putString("Name", x.name); e.putInt("R", x.rating); e.putInt("P", x.played);
                e.putInt("W", x.won); e.putInt("L", x.lost); e.putInt("D", x.drawn);
                t.put(en.getKey(), e);
            }
            n.put("Table", t);
            NbtList l = new NbtList();
            for (UUID u : signed) l.add(NbtString.of(u.toString()));
            n.put("Signed", l);
            n.putString("Champion", champion);
            n.putString("ChampionName", championName);
            n.putLong("Last", lastTourney);
            return n;
        }
    }

    static State state(MinecraftServer s) { return s.getOverworld().getPersistentStateManager().getOrCreate(State::read, State::new, KEY); }

    /** A townsperson's starting rating: their chess level, a little either side by name. */
    static int seedRating(String folk) { return 950 + 200 * (TownLife.chessLevel(folk) - 1) + Math.floorMod(Objects.hash(folk, "elo"), 81) - 40; }

    static Entry entry(State s, String key, String name) {
        Entry e = s.table.get(key);
        if (e == null) {
            e = new Entry();
            e.rating = key.startsWith("f:") ? seedRating(key.substring(2)) : 1000;
            s.table.put(key, e);
        }
        e.name = name;
        return e;
    }

    /** "f:<id>" / "p:<uuid>" for a side of the board, or null (the computer, an empty seat). */
    static String sideKey(ChessBoardEntity be, int side) {
        if (be.seat[side] != null) return "p:" + be.seat[side];
        String n = be.names[side];
        if (n == null || n.isEmpty()) return null;
        for (Townsfolk.Folk f : Townsfolk.ALL.values()) if (f.name().equals(n)) return "f:" + f.id();
        return null;
    }

    /** Chess.moved: a game has just ended on this board (result set). Rates it, and tells the tournament. */
    public static void gameOver(ServerWorld w, ChessBoardEntity be) {
        int win = Chess.winner(w, be.getPos());
        String a = sideKey(be, 0), b = sideKey(be, 1);
        if (a != null && b != null && !a.equals(b)) {
            State s = state(w.getServer());
            Entry ea = entry(s, a, be.names[0]), eb = entry(s, b, be.names[1]);
            double exp = 1 / (1 + Math.pow(10, (eb.rating - ea.rating) / 400.0));
            double score = win == 0 ? 1 : win == 1 ? 0 : 0.5;
            int d = (int) Math.round(K * (score - exp));
            ea.rating += d; eb.rating -= d;
            ea.played++; eb.played++;
            if (win == 0) { ea.won++; eb.lost++; } else if (win == 1) { eb.won++; ea.lost++; } else { ea.drawn++; eb.drawn++; }
            s.markDirty();
            tellRating(w, a, ea, d); tellRating(w, b, eb, -d);
        }
        Tourney t = live;
        if (t != null) t.onResult(w, be.getPos(), win);
    }

    private static void tellRating(ServerWorld w, String key, Entry e, int d) {
        if (!key.startsWith("p:")) return;
        ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(UUID.fromString(key.substring(2)));
        if (p != null) p.sendMessage(Text.literal("[League] Your rating: " + e.rating + " (" + (d >= 0 ? "+" : "") + d + ")").formatted(Formatting.AQUA), false);
    }

    // ------------------------------------------------------------------ the League board
    /** Use: the standings. Sneak + use: sign up for (or withdraw from) the next tournament. */
    public static void useBoard(ServerPlayerEntity p, boolean sneaking) {
        ServerWorld w = p.getServerWorld();
        State s = state(w.getServer());
        if (sneaking) {
            boolean in = !s.signed.remove(p.getUuid());
            if (in) s.signed.add(p.getUuid());
            s.markDirty();
            p.sendMessage(Text.literal(in ? "[League] Your name is on the list for the next tournament." : "[League] You've taken your name off the list.").formatted(Formatting.AQUA), false);
            return;
        }
        for (Townsfolk.Folk f : Townsfolk.ALL.values()) if (TownLife.canChallenge(f)) entry(s, "f:" + f.id(), f.name());   // everyone who plays is listed
        List<Map.Entry<String, Entry>> rows = new ArrayList<>(s.table.entrySet());
        rows.sort(Comparator.comparingInt((Map.Entry<String, Entry> e) -> -e.getValue().rating));
        p.sendMessage(Text.literal("=== THE WAVEBREAK CHESS LEAGUE ===").formatted(Formatting.GOLD, Formatting.BOLD), false);
        if (!s.championName.isEmpty()) p.sendMessage(Text.literal("Champion: " + s.championName).formatted(Formatting.YELLOW), false);
        String me = "p:" + p.getUuid();
        int mine = -1;
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).getKey().equals(me)) mine = i;
        for (int i = 0; i < Math.min(10, rows.size()); i++) p.sendMessage(row(i, rows.get(i), me, s), false);
        if (mine >= 10) p.sendMessage(row(mine, rows.get(mine), me, s), false);
        long days = TownCalendar.daysUntil(w, TownCalendar.Big.CHESS_TOURNAMENT);
        p.sendMessage(Text.literal((days == 0 ? "The tournament is TONIGHT at dusk" : "Next tournament in " + days + " day" + (days == 1 ? "" : "s"))
                + (s.signed.contains(p.getUuid()) ? " - you're signed up." : " - sneak + use the board to sign up.")).formatted(Formatting.GRAY), false);
        s.markDirty();
    }

    private static MutableText row(int i, Map.Entry<String, Entry> r, String me, State s) {
        Entry e = r.getValue();
        String crown = r.getKey().equals(s.champion) ? " *" : "";
        MutableText t = Text.literal(String.format("%2d. %-22s %4d   %d-%d-%d%s", i + 1, e.name, e.rating, e.won, e.lost, e.drawn, crown));
        return t.formatted(r.getKey().equals(me) ? Formatting.AQUA : i == 0 ? Formatting.YELLOW : Formatting.WHITE);
    }

    // ------------------------------------------------------------------ the tournament
    private static Tourney live;

    /** A townsperson with a tournament game: sat at their board. */
    static TownLife.Plan plan(ServerWorld w, String folk, Townsfolk.Phase phase) {
        Tourney t = live;
        if (t == null) return null;
        int[] at = t.seats.get(folk);
        return at == null ? null : TownLife.seatAt(w, phase, at[0], at[1]);
    }

    static boolean playing(String folk) { Tourney t = live; return t != null && t.seats.containsKey(folk); }

    static void tick(ServerWorld w) {
        long day = w.getTimeOfDay() / 24000L;
        int tod = (int) (w.getTimeOfDay() % 24000L);
        if (live != null) { live.tick(w, tod); return; }
        State s = state(w.getServer());
        if (TownCalendar.today(w) == TownCalendar.Big.CHESS_TOURNAMENT && tod >= 12500 && tod < 14000 && s.lastTourney != day) start(w);
    }

    /** /pptown league start, or the calendar. Returns a line for the command. */
    static String start(ServerWorld w) {
        if (live != null) return "A tournament is already on.";
        State s = state(w.getServer());
        s.lastTourney = w.getTimeOfDay() / 24000L;
        s.markDirty();
        List<String> keys = new ArrayList<>();
        List<String> players = new ArrayList<>();
        for (UUID u : s.signed) {
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(u);
            if (p != null && p.getServerWorld() == w) { entry(s, "p:" + u, p.getName().getString()); players.add("p:" + u); }
        }
        players.sort(Comparator.comparingInt(k -> -s.table.get(k).rating));
        keys.addAll(players.subList(0, Math.min(2, players.size())));
        List<String> folk = new ArrayList<>();
        for (Townsfolk.Folk f : Townsfolk.ALL.values())
            if (TownLife.canChallenge(f) && Townsfolk.scale(f.id()) >= 1 && townsfolk(w, f.id()) != null) { entry(s, "f:" + f.id(), f.name()); folk.add("f:" + f.id()); }
        folk.sort(Comparator.comparingInt(k -> -s.table.get(k).rating));
        for (String k : folk) if (keys.size() < 4) keys.add(k);
        if (keys.size() < 4) return "Not enough players (need 4, found " + keys.size() + ").";
        keys.sort(Comparator.comparingInt(k -> -s.table.get(k).rating));          // seeds 1..4
        live = new Tourney(keys);
        String names = String.join(", ", keys.stream().map(k -> s.table.get(k).name).toList());
        broadcast(w, "The Chess League tournament begins on the Chess Green! " + names + ".");
        TownMemory.news(w, "chess", "The Chess League tournament is on tonight - " + names + " are playing.", keys.get(0).startsWith("f:") ? keys.get(0).substring(2) : "");
        live.round(w, List.of(new String[]{keys.get(0), keys.get(3)}, new String[]{keys.get(1), keys.get(2)}), new int[]{TownLife.GREEN, TownLife.TABLE});
        return "Tournament started: " + names;
    }

    static final class Tourney {
        final List<String> seeds;
        final Map<String, int[]> seats = new LinkedHashMap<>();             // townsperson -> {board, side}
        final List<Match> matches = new ArrayList<>();
        boolean finalRound;

        Tourney(List<String> seeds) { this.seeds = seeds; }

        final class Match {
            final String[] key = new String[2];                              // white, black
            final int board;
            long since;
            boolean begun, started;                                          // begun: on the board; started: both sides there
            String winner;

            Match(String a, String b, int board) {
                // a player always takes white against a townsperson
                boolean swap = b.startsWith("p:") && a.startsWith("f:");
                key[0] = swap ? b : a; key[1] = swap ? a : b;
                this.board = board;
            }

            BlockPos pos() { return TownLife.boardPos(board); }
            boolean npc(int side) { return key[side].startsWith("f:"); }
            String id(int side) { return key[side].substring(2); }
        }

        /** The round's games; one whose board isn't there (an old world without the table) waits for the giant board. */
        void round(ServerWorld w, List<String[]> pairs, int[] boards) {
            matches.clear();
            for (int i = 0; i < pairs.size(); i++) {
                int b = w.getBlockEntity(TownLife.boardPos(boards[i])) instanceof ChessBoardEntity ? boards[i] : TownLife.GREEN;
                matches.add(new Match(pairs.get(i)[0], pairs.get(i)[1], b));
            }
            for (Match m : matches) if (boardFree(m)) begin(w, m);
        }

        private boolean boardFree(Match m) {
            return matches.stream().noneMatch(x -> x != m && x.begun && x.winner == null && x.board == m.board);
        }

        private void begin(ServerWorld w, Match m) {
            State s = state(w.getServer());
            m.begun = true;
            m.since = w.getTime();
            {
                for (int side = 0; side < 2; side++) if (m.npc(side)) {
                    seats.put(m.id(side), new int[]{m.board, side});
                    TownsfolkEntity e = townsfolk(w, m.id(side));
                    if (e != null) { TownLife.release(w, e); e.leaveSpot(); e.plan = null; }
                }
                String nw = s.table.get(m.key[0]).name, nb = s.table.get(m.key[1]).name;
                if (m.npc(0) && m.npc(1)) {
                    Chess.npcStart(w, m.pos(), nw, nb, TownLife.chessLevel(m.id(0)), TownLife.chessLevel(m.id(1)));
                    m.started = true;
                } else if (m.npc(1)) {
                    Chess.npcStartVs(w, m.pos(), nb, TownLife.chessLevel(m.id(1)));
                    m.started = true;
                } else Chess.npcStart(w, m.pos(), "", "", 0, 0);                    // two players: an empty board for them
                String where = m.board == TownLife.GREEN ? "the giant board on the Chess Green" : "the toymaker's chess table";
                broadcast(w, (finalRound ? "THE FINAL: " : "Semi-final: ") + nw + " (white) v " + nb + " (black) at " + where + ".");
                for (int side = 0; side < 2; side++) if (!m.npc(side)) {
                    ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(UUID.fromString(m.id(side)));
                    if (p != null) p.sendMessage(Text.literal("[League] Your game is at " + where + " - take " + (side == 0 ? "white" : "black")
                            + " (use the board). You have 2.5 minutes to sit down.").formatted(Formatting.AQUA), false);
                }
            }
        }

        void tick(ServerWorld w, int tod) {
            for (Match m : new ArrayList<>(matches)) {
                if (m.winner != null) continue;
                if (!m.begun) { if (boardFree(m)) begin(w, m); continue; }
                if (!(w.getBlockEntity(m.pos()) instanceof ChessBoardEntity be)) { decide(w, m, -2, "the board was lost"); continue; }
                for (int side = 0; side < 2; side++) {
                    if (m.npc(side)) continue;
                    UUID u = UUID.fromString(m.id(side));
                    boolean seated = u.equals(be.seat[side]);
                    boolean online = w.getServer().getPlayerManager().getPlayer(u) != null;
                    if (!seated && (!online || w.getTime() - m.since > SEAT_WAIT)) { decide(w, m, 1 - side, "forfeit"); break; }
                }
                if (m.winner != null) continue;
                if (!m.started && (m.npc(0) || u(be, m, 0)) && (m.npc(1) || u(be, m, 1))) m.started = true;
                if (m.npc(0) && m.npc(1) && (w.getTime() - m.since > GAME_CAP || tod >= 17500)) decide(w, m, -2, "adjudicated");
            }
        }

        private boolean u(ChessBoardEntity be, Match m, int side) { return UUID.fromString(m.id(side)).equals(be.seat[side]); }

        void onResult(ServerWorld w, BlockPos pos, int win) {
            for (Match m : new ArrayList<>(matches)) if (m.winner == null && m.begun && m.started && m.pos().equals(pos)) decide(w, m, win, "");
        }

        /** side 0/1 wins; -1 a draw (the higher seed goes through); -2 by rating (adjudicated). */
        private void decide(ServerWorld w, Match m, int side, String how) {
            State s = state(w.getServer());
            if (side < 0) {
                boolean byRating = side == -2;
                int a = byRating ? s.table.get(m.key[0]).rating : -seeds.indexOf(m.key[0]);
                int b = byRating ? s.table.get(m.key[1]).rating : -seeds.indexOf(m.key[1]);
                side = a >= b ? 0 : 1;
                if (how.isEmpty()) how = "a draw - the higher seed goes through";
            }
            m.winner = m.key[side];
            for (int i = 0; i < 2; i++) if (m.npc(i)) { seats.remove(m.id(i)); Chess.npcStop(w, m.pos(), s.table.get(m.key[i]).name); freeUp(w, m.id(i)); }
            broadcast(w, s.table.get(m.winner).name + " beats " + s.table.get(m.key[1 - side]).name + (how.isEmpty() ? "" : " (" + how + ")") + "!");
            if (matches.stream().anyMatch(x -> x.winner == null)) return;
            if (!finalRound) {
                finalRound = true;
                round(w, List.<String[]>of(new String[]{matches.get(0).winner, matches.get(1).winner}), new int[]{TownLife.GREEN});
            } else finish(w, m.winner, m.key[1 - side]);
        }

        private void freeUp(ServerWorld w, String folk) {
            TownsfolkEntity e = townsfolk(w, folk);
            if (e != null) e.plan = null;
        }

        private void finish(ServerWorld w, String champ, String runnerUp) {
            State s = state(w.getServer());
            s.champion = champ;
            s.championName = s.table.get(champ).name;
            s.markDirty();
            broadcast(w, s.championName + " is the CHAMPION of the Wavebreak Chess League!");
            TownMemory.news(w, "chess", (champ.startsWith("p:") ? "Captain " : "") + s.championName + " won the Chess League tournament!",
                    champ.startsWith("f:") ? champ.substring(2) : runnerUp.startsWith("f:") ? runnerUp.substring(2) : "");
            prize(w, champ, 30, "the champion's purse");
            prize(w, runnerUp, 10, "the runner-up's purse");
            for (ServerPlayerEntity p : w.getPlayers()) w.playSound(null, p.getBlockPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.NEUTRAL, 0.5f, 1f);
            for (String f : new ArrayList<>(seats.keySet())) freeUp(w, f);
            seats.clear();
            live = null;
        }

        private void prize(ServerWorld w, String key, int coins, String what) {
            if (!key.startsWith("p:")) return;
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(UUID.fromString(key.substring(2)));
            if (p == null) return;
            p.getInventory().offerOrDrop(new ItemStack(ModItems.COIN, coins));
            p.sendMessage(Text.literal("[League] You win " + what + ": " + coins + " coins.").formatted(Formatting.GOLD), false);
        }
    }

    /** /pptown league stop. */
    static String stop(ServerWorld w) {
        Tourney t = live;
        if (t == null) return "No tournament on.";
        for (String f : new ArrayList<>(t.seats.keySet())) { TownsfolkEntity e = townsfolk(w, f); if (e != null) e.plan = null; }
        live = null;
        return "Tournament called off.";
    }

    static void broadcast(ServerWorld w, String s) {
        for (ServerPlayerEntity p : w.getPlayers())
            p.sendMessage(Text.literal("[Chess League] ").formatted(Formatting.GOLD).append(Text.literal(s).formatted(Formatting.WHITE)), false);
    }

    static TownsfolkEntity townsfolk(ServerWorld w, String folk) {
        UUID u = TownLife.state(w.getServer()).owner.get(folk);
        Entity e = u == null ? null : w.getEntity(u);
        return e instanceof TownsfolkEntity t ? t : null;
    }
}
