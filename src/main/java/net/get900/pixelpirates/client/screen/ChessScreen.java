package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.homestead.chess.Chess;
import net.get900.pixelpirates.homestead.chess.ChessRules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * THE CHESS SCREEN (homestead/chess/Chess): the board from your side (black sees it turned round), your pieces' legal
 * moves shown when you pick one up, the last move marked, promotion choice, and the seats - sit as white or black, or put
 * the computer (easy / normal) in an empty seat; leave, resign, new game. Live: everyone watching sees each move.
 */
@Environment(EnvType.CLIENT)
public class ChessScreen extends Screen {
    private static final int SQ = 22, W = 348, H = 200;
    private static final int LIGHT = 0xFFE8D6B0, DARK = 0xFF9A6A42, PICK = 0xFFE0C040, LAST = 0x6648A0FF, DOT = 0xAA303030;
    public final BlockPos pos;
    private ChessRules game = ChessRules.start();
    private String[] names = {"", ""};
    private boolean[] seated = new boolean[2];
    private int[] ai = {0, 0};
    private int you = -1, lastMove = -1, selected = -1;
    private String result = "";
    private int[] promoting;                                                    // {from, to} while choosing a promotion

    public ChessScreen(BlockPos pos, NbtCompound n) {
        super(Text.literal("Chess"));
        this.pos = pos;
        update(n);
    }

    public void update(NbtCompound n) {
        game = ChessRules.fromArray(n.getIntArray("Game"));
        for (int i = 0; i < 2; i++) {
            names[i] = n.getString("Name" + i);
            seated[i] = n.containsUuid("Seat" + i);
            ai[i] = n.getInt("Ai" + i);
        }
        you = n.getInt("You");
        lastMove = n.contains("Last") ? n.getInt("Last") : -1;
        result = n.getString("Result");
        selected = -1;
        promoting = null;
        if (client != null) { clearChildren(); init(); }
    }

    private int bx() { return (width - W) / 2 + 8; }
    private int by() { return (height - H) / 2 + 12; }
    private boolean flipped() { return you == 1; }

    /** Screen cell (col, row from the top-left) -> square. */
    private int squareAt(int col, int row) {
        int f = flipped() ? 7 - col : col, r = flipped() ? row : 7 - row;
        return r * 8 + f;
    }

    private int[] cellOf(int s) {
        int f = s & 7, r = s >> 3;
        return new int[]{flipped() ? 7 - f : f, flipped() ? r : 7 - r};
    }

    @Override
    protected void init() {
        int x = bx() + 8 * SQ + 12, y = by();
        for (int side = 0; side < 2; side++) {
            int s = side, yy = y + 26 + side * 52;
            if (!seated[side] && ai[side] == 0) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Sit"), b -> send("sit", s)).dimensions(x, yy + 12, 34, 14).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("CPU easy"), b -> send("ai", s * 10 + 1)).dimensions(x + 36, yy + 12, 48, 14).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("CPU normal"), b -> send("ai", s * 10 + 2)).dimensions(x + 86, yy + 12, 58, 14).build());
            } else if (ai[side] > 0) {
                addDrawableChild(ButtonWidget.builder(Text.literal("Remove CPU"), b -> send("ai", s * 10)).dimensions(x, yy + 24, 70, 14).build());
            }
        }
        int ry = y + 136;
        if (you >= 0) {
            addDrawableChild(ButtonWidget.builder(Text.literal("Leave seat"), b -> send("leave", 0)).dimensions(x, ry, 70, 14).build());
            ButtonWidget res = ButtonWidget.builder(Text.literal("Resign"), b -> send("resign", 0)).dimensions(x + 74, ry, 70, 14).build();
            res.active = result.isEmpty();
            addDrawableChild(res);
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("New game"), b -> send("new", 0)).dimensions(x, ry + 17, 70, 14).build());
        if (promoting != null) {
            String[] names = {"Queen", "Rook", "Bishop", "Knight"};
            int[] pieces = {ChessRules.Q, ChessRules.R, ChessRules.B, ChessRules.N};
            for (int i = 0; i < 4; i++) {
                int pc = pieces[i];
                addDrawableChild(ButtonWidget.builder(Text.literal(names[i]), b -> {
                    send("move", ChessRules.move(promoting[0], promoting[1], pc));
                    promoting = null;
                }).dimensions(bx() + 8 * SQ / 2 - 90 + i * 45, by() + 8 * SQ / 2 - 7, 44, 14).build());
            }
        }
    }

    private void send(String what, int a) {
        var buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeString(what, 16);
        buf.writeVarInt(a);
        ClientPlayNetworking.send(Chess.ACT, buf);
    }

    private boolean myTurn() { return result.isEmpty() && you == (game.whiteToMove ? 0 : 1); }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c);
        int x0 = (width - W) / 2, y0 = (height - H) / 2;
        c.fill(x0 - 2, y0 - 2, x0 + W + 2, y0 + H + 2, 0xFF3A2414);
        c.fill(x0, y0, x0 + W, y0 + H, 0xFF5E3D24);
        int bx = bx(), by = by();
        c.fill(bx - 3, by - 3, bx + 8 * SQ + 3, by + 8 * SQ + 3, 0xFF2A180C);
        List<Integer> targets = new ArrayList<>();
        if (selected >= 0) for (int m : game.legal()) if (ChessRules.from(m) == selected) targets.add(ChessRules.to(m));
        for (int row = 0; row < 8; row++)
            for (int col = 0; col < 8; col++) {
                int s = squareAt(col, row), x = bx + col * SQ, y = by + row * SQ;
                c.fill(x, y, x + SQ, y + SQ, ((s & 7) + (s >> 3)) % 2 == 0 ? DARK : LIGHT);
                if (lastMove >= 0 && (s == ChessRules.from(lastMove) || s == ChessRules.to(lastMove))) c.fill(x, y, x + SQ, y + SQ, LAST);
                if (s == selected) c.fill(x, y, x + SQ, y + SQ, 0x88000000 | (PICK & 0xFFFFFF));
                int p = game.sq[s];
                if (p != 0) c.drawTexture(icon(p), x + 3, y + 3, 0, 0, 16, 16, 16, 16);
                if (targets.contains(s)) {
                    if (p != 0) c.drawBorder(x + 1, y + 1, SQ - 2, SQ - 2, DOT);
                    else c.fill(x + SQ / 2 - 3, y + SQ / 2 - 3, x + SQ / 2 + 3, y + SQ / 2 + 3, DOT);
                }
            }
        for (int i = 0; i < 8; i++) {                                                // a..h, 1..8 round the edge
            int f = flipped() ? 7 - i : i, r = flipped() ? i : 7 - i;
            c.drawText(textRenderer, "" + (char) ('a' + f), bx + i * SQ + SQ / 2 - 2, by + 8 * SQ + 4, 0xFFD8C098, false);
            c.drawText(textRenderer, "" + (char) ('1' + r), bx - 8, by + i * SQ + SQ / 2 - 4, 0xFFD8C098, false);
        }
        int x = bx + 8 * SQ + 12, y = by;
        String status;
        if (!result.isEmpty()) status = switch (result) {
            case "checkmate" -> "Checkmate - " + (game.whiteToMove ? "Black" : "White") + " wins";
            case "stalemate" -> "Stalemate - a draw";
            case "fifty moves" -> "Draw (fifty moves)";
            case "bare kings" -> "Draw (bare kings)";
            default -> result;
        };
        else status = (game.whiteToMove ? "White" : "Black") + " to move" + (game.inCheck() ? " - check!" : "");
        c.drawText(textRenderer, Text.literal("Chess").formatted(Formatting.BOLD), x, y, 0xFFF4E2B8, true);
        c.drawText(textRenderer, status, x, y + 12, result.isEmpty() ? 0xFFE8D8B0 : 0xFFFFD050, false);
        for (int side = 0; side < 2; side++) {
            int yy = y + 26 + side * 52;
            boolean turn = result.isEmpty() && (side == 0) == game.whiteToMove;
            c.drawText(textRenderer, (side == 0 ? "White" : "Black") + (turn ? "  <" : ""), x, yy, side == 0 ? 0xFFFFFFFF : 0xFFB0A090, true);
            if (seated[side] || ai[side] > 0)
                c.drawText(textRenderer, textRenderer.trimToWidth(names[side] + (you == side ? " (you)" : ""), 140), x, yy + 11, 0xFFE0D0B0, false);
        }
        if (lastMove >= 0) c.drawText(textRenderer, "Last: " + ChessRules.moveName(lastMove), x, y + 124, 0xFFB8A880, false);
        if (you < 0 && (seated[0] || ai[0] > 0) && (seated[1] || ai[1] > 0))
            c.drawText(textRenderer, "Watching", x + 80, y + 124, 0xFF908060, false);
        if (promoting != null) c.fill(bx, by + 8 * SQ / 2 - 12, bx + 8 * SQ, by + 8 * SQ / 2 + 12, 0xCC000000);
        super.render(c, mx, my, delta);
    }

    private static Identifier icon(int p) {
        return new Identifier("pixelpirates", "textures/gui/chess/" + (p > 0 ? "w" : "b") + "pnbrqk".charAt(Math.abs(p) - 1) + ".png");
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (promoting != null || !myTurn()) return false;
        int col = (int) Math.floor((mx - bx()) / SQ), row = (int) Math.floor((my - by()) / SQ);
        if (col < 0 || col > 7 || row < 0 || row > 7) return false;
        int s = squareAt(col, row), p = game.sq[s], sign = game.whiteToMove ? 1 : -1;
        if (selected >= 0) {
            List<Integer> moves = new ArrayList<>();
            for (int m : game.legal()) if (ChessRules.from(m) == selected && ChessRules.to(m) == s) moves.add(m);
            if (moves.size() == 1) { send("move", moves.get(0)); selected = -1; return true; }
            if (moves.size() > 1) { promoting = new int[]{selected, s}; clearChildren(); init(); return true; }
        }
        selected = p * sign > 0 ? s : -1;
        return true;
    }

    @Override
    public boolean shouldPause() { return false; }
}
