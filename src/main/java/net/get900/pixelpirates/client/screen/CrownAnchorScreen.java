package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.homestead.tavern.CrownAnchorBlockEntity;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;

/**
 * CROWN & ANCHOR board (CrownAnchorBlockEntity): six symbol squares (click one to stake the chosen amount on it), the
 * stake picker, the banker's three dice (tumbling while the cup shakes) and your result for the last throw.
 */
@Environment(EnvType.CLIENT)
public class CrownAnchorScreen extends TavernGameScreen {
    private static final int[] AMOUNTS = {1, 2, 5, 10, 16};
    private static int amount = 1;                         // remembered between visits
    private static final int SQ_X = 8, SQ_Y = 36, SQ_W = 40, SQ_H = 48, SQ_GAP = 1;

    public CrownAnchorScreen(NbtCompound st) { super(Text.literal("Crown & Anchor"), st); }

    private String phase() { return st.getString("Phase"); }

    @Override
    protected void buttons() {
        for (int i = 0; i < AMOUNTS.length; i++) {
            final int a = AMOUNTS[i];
            btn(String.valueOf(a), 8 + i * 25, 98, 23, 16, () -> { amount = a; clearAndInit(); });
        }
        var clear = btn("Take back", 136, 98, 54, 16, () -> send("clear", 0, 0));
        clear.active = "BETTING".equals(phase()) && sum(st.getIntArray("Mine")) > 0;
        var roll = btn("Throw!", 194, 98, 54, 16, () -> send("roll", 0, 0));
        roll.active = "BETTING".equals(phase()) && sum(st.getIntArray("Mine")) > 0;
    }

    private static int sum(int[] a) { int n = 0; for (int v : a) n += v; return n; }

    private boolean open() { String p = phase(); return "IDLE".equals(p) || "BETTING".equals(p); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (open() && button == 0)
            for (int i = 0; i < 6; i++) {
                int x = left + SQ_X + i * (SQ_W + SQ_GAP), y = top + SQ_Y;
                if (mx >= x && mx < x + SQ_W && my >= y && my < y + SQ_H) { send("stake", i, amount); return true; }
            }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void draw(DrawContext ctx, int mx, int my, float delta) {
        String ph = phase();
        center(ctx, "Crown & Anchor", 7, 0xFFF0C048);
        String purse = st.getBoolean("Creative") ? "Creative - you play for free" : "Your purse: " + st.getInt("Coins") + " pirate coins";
        small(ctx, purse, (W - (int) (textRenderer.getWidth(purse) * 0.6f)) / 2, 18, 0xFFB8A880, 0.6f);
        String line = switch (ph) {
            case "BETTING" -> "The banker throws in " + secondsLeft() + "s - " + st.getInt("Players") + " at the board";
            case "ROLLING" -> "The cup rattles...";
            case "RESULT" -> "The dice are down!";
            default -> "Click a symbol to stake " + amount + " coin" + (amount == 1 ? "" : "s");
        };
        small(ctx, line, (W - (int) (textRenderer.getWidth(line) * 0.75f)) / 2, 27, 0xFFF3E2B0, 0.75f);

        int[] mine = st.getIntArray("Mine"), all = st.getIntArray("All"), dice = st.getIntArray("Dice");
        for (int i = 0; i < 6; i++) {
            int x = SQ_X + i * (SQ_W + SQ_GAP);
            boolean hot = open() && mx >= left + x && mx < left + x + SQ_W && my >= top + SQ_Y && my < top + SQ_Y + SQ_H;
            int hits = 0;
            if ("RESULT".equals(ph)) for (int d : dice) if (d == i) hits++;
            tex(ctx, x, SQ_Y, hot || hits > 0 ? 416 : 376, 64, SQ_W, SQ_H);
            tex(ctx, x + 6, SQ_Y + 3, 256 + i * 28, 32, 28, 28);
            small(ctx, CrownAnchorBlockEntity.SYMBOLS[i], x + 3, SQ_Y + 31, 0xFFF3E2B0, 0.6f);
            if (mine.length == 6 && mine[i] > 0) small(ctx, "you " + mine[i], x + 3, SQ_Y + 38, 0xFF8CF08C, 0.6f);
            if (all.length == 6 && all[i] > 0) small(ctx, "all " + all[i], x + 22, SQ_Y + 38, 0xFFC8B8A0, 0.6f);
            if (hits > 0) small(ctx, "x" + hits, x + 30, SQ_Y + 3, 0xFFF0C048, 0.75f);
        }
        small(ctx, "Stake per click:", 8, 89, 0xFFB8A880, 0.6f);

        // the banker's dice
        long t = System.currentTimeMillis() / 90;
        for (int k = 0; k < 3; k++) {
            int x = (W - 3 * 36 + 4) / 2 + k * 36, y = 124;
            int sym = "ROLLING".equals(ph) ? (int) ((t * 7 + k * 3) % 6) : dice.length == 3 ? dice[k] : -1;
            int jitter = "ROLLING".equals(ph) ? (int) ((t + k) % 3) - 1 : 0;
            tex(ctx, x, y + jitter, 256, 124, 32, 32);
            if (sym >= 0) tex(ctx, x + 2, y + 2 + jitter, 256 + sym * 28, 32, 28, 28);
        }
        if ("RESULT".equals(ph) && st.getBoolean("Played")) {
            int net = st.getInt("Net");
            center(ctx, net > 0 ? "You win " + net + " coins!" : net == 0 ? "You break even" : "You lose " + (-net) + " coins", 164, net > 0 ? 0xFF8CF08C : 0xFFFF8070);
        } else small(ctx, "Each die showing your symbol pays your stake again", 38, 166, 0xFFB8A880, 0.6f);
    }

    @Override
    protected void drawOver(DrawContext ctx, int mx, int my) {
        for (int i = 0; i < AMOUNTS.length; i++) if (AMOUNTS[i] == amount) {
            int x = left + 8 + i * 25, y = top + 98;
            ctx.fill(x, y + 15, x + 23, y + 16, 0xFFF0C048);
            ctx.fill(x, y, x + 23, y + 1, 0xFFF0C048);
        }
    }
}
