package net.get900.pixelpirates.homestead.chess;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.*;

/**
 * CHESS (2026-10-04, the user: "a chess board where you can actually play chess and it gets animated too"): a CHESS TABLE
 * (one block, in the toymaker's shop #35) and GIANT CHESS (an 8 x 8 floor board with big pieces, in the park). Use either
 * to open the chess screen: take white or black, or set the computer on a side (easy / normal) - so two players, or one
 * against the computer. Full rules (castling, en passant, promotion, check, mate, stalemate, the fifty-move rule).
 * Moves animate on the board itself (client/ChessRenderer). Everyone with the screen open sees the game live.
 */
public final class Chess {
    private Chess() {}

    public static final Identifier STATE = new Identifier("pixelpirates", "chess_state");
    public static final Identifier ACT = new Identifier("pixelpirates", "chess_act");
    /** board pos (per dimension) -> players watching it. */
    private static final Map<String, Set<UUID>> VIEWERS = new HashMap<>();

    private static String key(ServerWorld w, BlockPos p) { return w.getRegistryKey().getValue() + "@" + p.asLong(); }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ACT, (server, player, handler, buf, sender) -> {
            BlockPos pos = buf.readBlockPos();
            String what = buf.readString(16);
            int a = buf.readVarInt();
            server.execute(() -> act(player, pos, what, a));
        });
    }

    public static void open(ServerPlayerEntity p, ChessBoardEntity be) {
        VIEWERS.computeIfAbsent(key(p.getServerWorld(), be.getPos()), k -> new HashSet<>()).add(p.getUuid());
        send(p, be, true);
    }

    static void send(ServerPlayerEntity p, ChessBoardEntity be, boolean open) {
        NbtCompound n = be.createNbt();
        n.putBoolean("Open", open);
        n.putInt("You", p.getUuid().equals(be.seat[0]) ? 0 : p.getUuid().equals(be.seat[1]) ? 1 : -1);
        n.putBoolean("Giant", be.giant());
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(be.getPos());
        buf.writeNbt(n);
        ServerPlayNetworking.send(p, STATE, buf);
    }

    /** Push the game to everyone watching it (and forget watchers who walked off). */
    static void refresh(ServerWorld w, ChessBoardEntity be) {
        Set<UUID> v = VIEWERS.get(key(w, be.getPos()));
        if (v == null) return;
        v.removeIf(u -> {
            ServerPlayerEntity p = w.getServer().getPlayerManager().getPlayer(u);
            if (p == null || p.getServerWorld() != w || p.squaredDistanceTo(Vec3d.ofCenter(be.getPos())) > 16 * 16) return true;
            send(p, be, false);
            return false;
        });
    }

    private static void act(ServerPlayerEntity p, BlockPos pos, String what, int a) {
        ServerWorld w = p.getServerWorld();
        if (p.squaredDistanceTo(Vec3d.ofCenter(pos)) > 12 * 12 || !(w.getBlockEntity(pos) instanceof ChessBoardEntity be)) return;
        int mine = p.getUuid().equals(be.seat[0]) ? 0 : p.getUuid().equals(be.seat[1]) ? 1 : -1;
        switch (what) {
            case "sit" -> {                                                         // a = 0 white, 1 black
                if (a < 0 || a > 1 || be.seat[a] != null) return;
                if (mine >= 0) { be.seat[mine] = null; be.names[mine] = ""; }
                be.seat[a] = p.getUuid(); be.names[a] = p.getName().getString(); be.ai[a] = 0;
                be.scheduleAi();
                be.changed();
            }
            case "leave" -> {
                if (mine < 0) return;
                be.seat[mine] = null; be.names[mine] = "";
                be.changed();
            }
            case "ai" -> {                                                          // a = side * 10 + level (0 = off)
                int side = a / 10, level = a % 10;
                if (side < 0 || side > 1 || level > 2 || be.seat[side] != null) return;
                be.ai[side] = level;
                be.names[side] = level == 0 ? "" : level == 1 ? "Computer (easy)" : "Computer (normal)";
                be.scheduleAi();
                be.changed();
            }
            case "move" -> {
                int side = be.game.whiteToMove ? 0 : 1;
                if (!be.result.isEmpty() || mine != side || !be.game.legal().contains(a)) return;
                be.play(a);
                moved(w, be, a);
            }
            case "new" -> {
                boolean seated = mine >= 0, empty = be.seat[0] == null && be.seat[1] == null;
                if (!seated && !empty) { p.sendMessage(Text.literal("Only the players at this board can start a new game.").formatted(Formatting.RED), true); return; }
                be.reset();
            }
            case "resign" -> {
                if (mine < 0 || !be.result.isEmpty()) return;
                be.result = (mine == 0 ? "White" : "Black") + " resigned";
                be.changed();
                announce(w, be, be.result + " - " + (mine == 0 ? "black" : "white") + " wins.");
            }
            default -> { }
        }
    }

    /** /ppchess <pos> demo (op, testing): the computer plays both sides, from a fresh board. */
    public static boolean demo(ServerWorld w, BlockPos pos) {
        if (!(w.getBlockEntity(pos) instanceof ChessBoardEntity be)) return false;
        be.seat[0] = be.seat[1] = null;
        be.ai[0] = 2; be.ai[1] = 1; be.names[0] = "Computer (normal)"; be.names[1] = "Computer (easy)";
        be.reset();
        return true;
    }

    // ------------------------------------------------------------------ townsfolk games (homestead/town/TownLife)
    /** Nobody at the board: no player seated, no computer set. */
    public static boolean idle(ServerWorld w, BlockPos pos) {
        return w.getBlockEntity(pos) instanceof ChessBoardEntity be && be.seat[0] == null && be.seat[1] == null && be.ai[0] == 0 && be.ai[1] == 0;
    }

    /** Two townsfolk sit down to a game: the computer plays each side under their name, at their own strength (1-2). */
    public static void npcStart(ServerWorld w, BlockPos pos, String white, String black, int lw, int lb) {
        if (!(w.getBlockEntity(pos) instanceof ChessBoardEntity be)) return;
        be.seat[0] = be.seat[1] = null;
        be.ai[0] = lw; be.ai[1] = lb; be.names[0] = white; be.names[1] = black;
        be.reset();
    }

    /** A player challenged a townsperson: the townsperson plays black (the computer under their name); white waits for the player. */
    public static void npcStartVs(ServerWorld w, BlockPos pos, String name, int level) {
        if (!(w.getBlockEntity(pos) instanceof ChessBoardEntity be)) return;
        be.seat[0] = be.seat[1] = null;
        be.ai[0] = 0; be.names[0] = "";
        be.ai[1] = level; be.names[1] = name;
        be.reset();
    }

    /** The player sitting on that side (null = nobody). */
    public static java.util.UUID seated(ServerWorld w, BlockPos pos, int side) {
        return w.getBlockEntity(pos) instanceof ChessBoardEntity be ? be.seat[side] : null;
    }

    /** Is this townsperson still playing here (a player may have taken over a seat)? */
    public static boolean npcPlaying(ServerWorld w, BlockPos pos, String name) {
        if (!(w.getBlockEntity(pos) instanceof ChessBoardEntity be)) return false;
        for (int i = 0; i < 2; i++) if (name.equals(be.names[i]) && be.ai[i] > 0 && be.seat[i] == null) return true;
        return false;
    }

    /** The game's result ("" while it goes on) and who won: 0 white, 1 black, -1 a draw. */
    public static String result(ServerWorld w, BlockPos pos) { return w.getBlockEntity(pos) instanceof ChessBoardEntity be ? be.result : ""; }

    public static int winner(ServerWorld w, BlockPos pos) {
        if (!(w.getBlockEntity(pos) instanceof ChessBoardEntity be) || !be.result.equals("checkmate")) return -1;
        return be.game.whiteToMove ? 1 : 0;
    }

    /** They got up: the computer leaves their side(s) of the board. */
    public static void npcStop(ServerWorld w, BlockPos pos, String name) {
        if (!(w.getBlockEntity(pos) instanceof ChessBoardEntity be)) return;
        boolean any = false;
        for (int i = 0; i < 2; i++) if (name.equals(be.names[i]) && be.seat[i] == null) { be.ai[i] = 0; be.names[i] = ""; any = true; }
        if (any) { be.scheduleAi(); be.changed(); }
    }

    static void moved(ServerWorld w, ChessBoardEntity be, int m) {
        w.playSound(null, be.getPos(), be.giant() ? SoundEvents.BLOCK_STONE_PLACE : SoundEvents.BLOCK_WOOD_PLACE, SoundCategory.BLOCKS,
                be.giant() ? 1f : 0.5f, be.captured != 0 ? 0.8f : 1.3f);
        if (be.result.isEmpty()) {
            if (be.game.inCheck()) announce(w, be, "Check!");
            return;
        }
        String r = switch (be.result) {
            case "checkmate" -> "Checkmate - " + (be.game.whiteToMove ? "black" : "white") + " wins!";
            case "stalemate" -> "Stalemate - a draw.";
            case "fifty moves" -> "Fifty moves without a capture - a draw.";
            case "bare kings" -> "Not enough left to mate - a draw.";
            default -> be.result;
        };
        announce(w, be, r);
        w.playSound(null, be.getPos(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.BLOCKS, 0.6f, 1f);
        net.get900.pixelpirates.homestead.town.ChessLeague.gameOver(w, be);   // ratings + the tournament
    }

    private static void announce(ServerWorld w, ChessBoardEntity be, String s) {
        for (ServerPlayerEntity p : w.getPlayers())
            if (p.squaredDistanceTo(Vec3d.ofCenter(be.getPos())) < 16 * 16) p.sendMessage(Text.literal("[Chess] " + s).formatted(Formatting.GOLD), true);
    }
}
