package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;

/**
 * LIAR'S DICE table screen (LiarsDiceBlockEntity). Seats across the top - cups, or the dice once the cups lift - the
 * standing bid in the middle, your own dice below it, and the controls: the lobby (join, ante, regulars, start) or,
 * on your turn, quantity +/-, a face to bid on, BID and LIAR!.
 */
@Environment(EnvType.CLIENT)
public class LiarsDiceScreen extends TavernGameScreen {
    private int qty = 1, face = 2;

    public LiarsDiceScreen(NbtCompound st) { super(Text.literal("Liar's Dice"), st); }

    private String phase() { return st.getString("Phase"); }
    private int me() { return st.getInt("Me"); }
    private NbtList seats() { return st.getList("Seats", NbtElement.COMPOUND_TYPE); }
    private boolean myTurn() { return "BIDDING".equals(phase()) && me() >= 0 && st.getInt("Turn") == me(); }

    private boolean legal(int q, int f) {
        int bq = st.getInt("BidQ"), bf = st.getInt("BidF");
        if (q < 1 || q > st.getInt("Total") || f < 1 || f > 6) return false;
        return st.getInt("Bidder") < 0 || q > bq || (q == bq && f > bf);
    }

    @Override
    protected void buttons() {
        String ph = phase();
        boolean host = st.getBoolean("Host");
        int me = me();
        if ("LOBBY".equals(ph)) {
            if (me < 0) btn("Sit in - ante " + st.getInt("Ante"), 63, 186, 130, 18, () -> send("join", 0, 0));
            else btn("Leave (refund)", 8, 186, 80, 18, () -> send("leave", 0, 0));
            if (host) {
                if (st.getBoolean("Alone")) {
                    btn("Ante -", 8, 164, 44, 18, () -> send("ante", -1, 0));
                    btn("Ante +", 54, 164, 44, 18, () -> send("ante", 1, 0));
                }
                btn("+ Regular", 104, 164, 52, 18, () -> send("bot+", 0, 0));
                btn("- Regular", 158, 164, 52, 18, () -> send("bot-", 0, 0));
                var start = btn("Start!", 194, 186, 54, 18, () -> send("start", 0, 0));
                start.active = seats().size() >= 2;
            }
            return;
        }
        if (me >= 0 && "BIDDING".equals(ph)) btn("Leave", 8, 186, 40, 18, () -> send("leave", 0, 0));
        if (!myTurn()) return;
        if (!legal(qty, face)) {                                  // start from the smallest raise
            int bq = st.getInt("BidQ"), bf = st.getInt("BidF");
            if (st.getInt("Bidder") < 0) { qty = 1; face = 2; }
            else if (bf < 6) { qty = bq; face = bf + 1; }
            else { qty = bq + 1; face = 2; }
        }
        btn("-", 8, 162, 16, 18, () -> { if (qty > 1) qty--; clearAndInit(); });
        btn("+", 48, 162, 16, 18, () -> { if (qty < st.getInt("Total")) qty++; clearAndInit(); });
        for (int f = 1; f <= 6; f++) {
            final int ff = f;
            btn("", 70 + (f - 1) * 21, 161, 20, 20, () -> { face = ff; clearAndInit(); });
        }
        var bid = btn("Bid " + qty + " x " + face, 198, 162, 50, 18, () -> send("bid", qty, face));
        bid.active = legal(qty, face);
        var liar = btn("LIAR!", 198, 184, 50, 18, () -> send("liar", 0, 0));
        liar.active = st.getInt("Bidder") >= 0;
    }

    @Override
    protected void draw(DrawContext ctx, int mx, int my, float delta) {
        String ph = phase();
        NbtList seats = seats();
        int me = me(), turn = st.getInt("Turn"), bidder = st.getInt("Bidder"), bidF = st.getInt("BidF");
        boolean lifted = "REVEAL".equals(ph) || "OVER".equals(ph);
        center(ctx, "Liar's Dice", 7, 0xFFF0C048);
        small(ctx, "Ante " + st.getInt("Ante"), 10, 9, 0xFFE8D8A8, 0.75f);
        String pot = "Pot " + st.getInt("Pot");
        small(ctx, pot, W - 10 - (int) (textRenderer.getWidth(pot) * 0.75f), 9, 0xFFF0C048, 0.75f);
        String purse = st.getBoolean("Creative") ? "Creative - you play for free" : "Your purse: " + st.getInt("Coins") + " pirate coins";
        small(ctx, purse, (W - (int) (textRenderer.getWidth(purse) * 0.6f)) / 2, 18, 0xFFB8A880, 0.6f);

        // the seats
        int n = seats.size(), x0 = (W - (n * 42 - 2)) / 2;
        for (int i = 0; i < n; i++) {
            NbtCompound s = seats.getCompound(i);
            int x = x0 + i * 42, y = 26, dl = s.getInt("Left");
            boolean out = !"LOBBY".equals(ph) && dl == 0;
            int u = out ? 336 : (i == turn && "BIDDING".equals(ph)) || (i == st.getInt("Loser") && lifted) ? 296 : 256;
            tex(ctx, x, y, u, 64, 40, 56);
            String name = fit(s.getString("N"), 36, 0.6f);
            small(ctx, name, x + 3, y + 3, i == me ? 0xFF8CF08C : s.getBoolean("Bot") ? 0xFFC8B8E8 : 0xFFF3E2B0, 0.6f);
            int[] dice = s.getIntArray("Dice");
            for (int j = 0; j < dl; j++) {
                int dx = x + 4 + (j % 3) * 11, dy = y + 13 + (j / 3) * 11;
                if (dice.length > j) {
                    if (lifted && dice[j] == bidF) ctx.fill(left(dx) - 1, top(dy) - 1, left(dx) + 11, top(dy) + 11, 0xFFF0C048);
                    tex(ctx, dx, dy, 256 + (dice[j] - 1) * 10, 20, 10, 10);
                } else tex(ctx, dx, dy, 316, 20, 10, 10);
            }
            small(ctx, s.getBoolean("Bot") ? "regular" : "x" + dl, x + 3, y + 46, 0xFFB8A880, 0.6f);
            if (i == bidder && "BIDDING".equals(ph)) small(ctx, "BID", x + 26, y + 46, 0xFFF0C048, 0.6f);
        }
        if (n == 0) center(ctx, "An empty table - sit in to start one", 50, 0xFFB8A880);

        // the bid / the call
        int y = 90;
        int bq = st.getInt("BidQ");
        switch (ph) {
            case "LOBBY" -> {
                center(ctx, n + "/6 seated" + (me >= 0 ? (st.getBoolean("Host") ? " - you run this table" : " - waiting for the host") : ""), y, 0xFFF3E2B0);
                if (st.getBoolean("Host")) small(ctx, "Add tavern regulars to play against, then Start", 40, y + 12, 0xFFB8A880, 0.75f);
            }
            case "BIDDING" -> {
                String who = turn >= 0 && turn < n ? seats.getCompound(turn).getString("N") : "?";
                if (bidder < 0) center(ctx, who + " opens the bidding", y, 0xFFF3E2B0);
                else {
                    String b = seats.getCompound(bidder).getString("N") + " bids " + bq + " x";
                    int w = textRenderer.getWidth(b) + 24, bx = (W - w) / 2;
                    ctx.drawTextWithShadow(textRenderer, b, left + bx, top + y, 0xFFF3E2B0);
                    tex(ctx, bx + w - 20, y - 6, 256 + (bidF - 1) * 20, 0, 20, 20);
                }
                center(ctx, (myTurn() ? "Your move" : who + " is thinking") + " - " + secondsLeft() + "s", y + 14, myTurn() ? 0xFF8CF08C : 0xFFB8A880);
            }
            case "REVEAL" -> {
                center(ctx, "LIAR!  " + st.getInt("RevealCount") + " x " + bidF + " on the table (bid " + bq + ")", y, 0xFFFF7060);
                int l = st.getInt("Loser");
                if (l >= 0 && l < n) center(ctx, seats.getCompound(l).getString("N") + " loses a die", y + 12, 0xFFF3E2B0);
            }
            case "OVER" -> center(ctx, st.getString("Winner") + " wins the pot!", y, 0xFFF0C048);
            default -> { }
        }

        // your dice
        if (me >= 0 && me < n) {
            int[] dice = seats.getCompound(me).getIntArray("Dice");
            int dx0 = (W - (dice.length * 24 - 4)) / 2;
            for (int j = 0; j < dice.length; j++) {
                if (lifted && dice[j] == bidF) ctx.fill(left + dx0 + j * 24 - 2, top + 120, left + dx0 + j * 24 + 22, top + 144, 0xFFF0C048);
                tex(ctx, dx0 + j * 24, 122, 256 + (dice[j] - 1) * 20, 0, 20, 20);
            }
            if (dice.length > 0) small(ctx, "your dice", (W - 30) / 2, 146, 0xFFB8A880, 0.6f);
        } else if (!"LOBBY".equals(ph)) center(ctx, "You are watching", 128, 0xFFB8A880);

    }

    @Override
    protected void drawOver(DrawContext ctx, int mx, int my) {
        if (myTurn()) {
            String q = String.valueOf(qty);
            ctx.drawTextWithShadow(textRenderer, q, left + 36 - textRenderer.getWidth(q) / 2, top + 167, 0xFFFFFFFF);
            for (int f = 1; f <= 6; f++) {
                int fx = 70 + (f - 1) * 21;
                if (f == face) ctx.fill(left + fx - 1, top + 160, left + fx + 21, top + 182, 0xFFF0C048);
                tex(ctx, fx, 161, 256 + (f - 1) * 20, 0, 20, 20);
            }
        }
    }

    private int left(int x) { return left + x; }
    private int top(int y) { return top + y; }
}
