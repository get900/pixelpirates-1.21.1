package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.homestead.chapel.OrganNotes;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

/**
 * THE ORGAN CONSOLE's keyboard (2026-10-01): two octaves F#3..F#5 (the note-block range). Click the keys, or play on the
 * computer keyboard - lower row Z S X D C V G B H N J M , = G3..F#4, upper row Q 2 W 3 E R 5 T 6 Y 7 U I = G4..F#5.
 * Pick a stop (Diapason / Flute / Chimes) or let it play "Amazing Grace". Everyone near the organ hears every note
 * (OrganNotes plays them server-side). Art: textures/gui/organ.png (tools/gen_chapel_assets.py).
 */
@Environment(EnvType.CLIENT)
public class OrganScreen extends Screen {
    private static final Identifier TEX = new Identifier("pixelpirates", "textures/gui/organ.png");
    private static final int W = 256, H = 150, KEY_W = 16, KEY_H = 70, BLACK_W = 10, BLACK_H = 44, KX = 16, KY = 58;
    private static final String[] NAMES = {"F#", "G", "G#", "A", "A#", "B", "C", "C#", "D", "D#", "E", "F"};
    private static final int[] LOWER = {GLFW.GLFW_KEY_Z, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_X, GLFW.GLFW_KEY_D, GLFW.GLFW_KEY_C, GLFW.GLFW_KEY_V, GLFW.GLFW_KEY_G,
            GLFW.GLFW_KEY_B, GLFW.GLFW_KEY_H, GLFW.GLFW_KEY_N, GLFW.GLFW_KEY_J, GLFW.GLFW_KEY_M, GLFW.GLFW_KEY_COMMA};
    private static final int[] UPPER = {GLFW.GLFW_KEY_Q, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_E, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_5,
            GLFW.GLFW_KEY_T, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_Y, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_U, GLFW.GLFW_KEY_I};
    private static int stop = 0;
    private final BlockPos pos;
    private final long[] lit = new long[25];
    private int left, top;

    public OrganScreen(BlockPos pos) { super(Text.literal("Organ")); this.pos = pos; }

    private static boolean black(int n) { String s = NAMES[n % 12]; return s.endsWith("#"); }

    /** x of a key's left edge (white keys tile; black keys sit across the line between two whites). */
    private int keyX(int n) {
        int whites = 0;
        for (int i = 1; i < n; i++) if (!black(i)) whites++;
        if (n == 0) return KX - BLACK_W / 2;
        return black(n) ? KX + whites * KEY_W + KEY_W - BLACK_W / 2 : KX + whites * KEY_W;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        String[] stops = {"Diapason", "Flute", "Chimes"};
        for (int i = 0; i < 3; i++) {
            final int s = i;
            addDrawableChild(ButtonWidget.builder(Text.literal((stop == i ? "> " : "") + stops[i]), b -> { stop = s; clearAndInit(); })
                    .dimensions(left + 14 + i * 62, top + 32, 60, 16).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Play a hymn"), b -> {
            var buf = PacketByteBufs.create(); buf.writeBlockPos(pos); buf.writeVarInt(stop); ClientPlayNetworking.send(OrganNotes.HYMN, buf);
        }).dimensions(left + 202, top + 32, 44, 16).build());
    }

    private void play(int n) {
        if (n < 0 || n > 24) return;
        lit[n] = System.currentTimeMillis();
        var buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeVarInt(n);
        buf.writeVarInt(stop);
        ClientPlayNetworking.send(OrganNotes.NOTE, buf);
    }

    private int keyAt(double mx, double my) {
        int x = (int) mx - left, y = (int) my - top;
        if (y < KY || y > KY + KEY_H) return -1;
        if (y <= KY + BLACK_H) for (int n = 0; n <= 24; n++) if (black(n) && x >= keyX(n) && x < keyX(n) + BLACK_W) return n;
        for (int n = 1; n <= 24; n++) if (!black(n) && x >= keyX(n) && x < keyX(n) + KEY_W) return n;
        return -1;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int k = keyAt(mx, my);
        if (k >= 0 && button == 0) { play(k); return true; }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        for (int i = 0; i < LOWER.length; i++) if (LOWER[i] == key) { play(1 + i); return true; }
        for (int i = 0; i < UPPER.length; i++) if (UPPER[i] == key) { play(13 + i); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawTexture(TEX, left, top, 0, 0, W, H, 256, 256);
        ctx.drawCenteredTextWithShadow(textRenderer, "The Chapel Organ", left + W / 2, top + 10, 0xFFF0D890);
        ctx.drawCenteredTextWithShadow(textRenderer, "keys: Z S X D C V G B H N J M ,  and  Q 2 W 3 E R 5 T 6 Y 7 U I", left + W / 2, top + 21, 0xFFB8A880);
        super.render(ctx, mx, my, delta);
        long now = System.currentTimeMillis();
        int hover = keyAt(mx, my);
        for (int n = 1; n <= 24; n++) {
            if (black(n)) continue;
            boolean on = now - lit[n] < 180, hot = hover == n;
            ctx.drawTexture(TEX, left + keyX(n), top + KY, on ? 32 : hot ? 16 : 0, 150, KEY_W, KEY_H, 256, 256);
            if (NAMES[n % 12].equals("C")) ctx.drawTextWithShadow(textRenderer, "C" + (n < 12 ? 4 : 5), left + keyX(n) + 3, top + KY + KEY_H - 10, 0xFF605040);
        }
        for (int n = 0; n <= 24; n++) {
            if (!black(n)) continue;
            boolean on = now - lit[n] < 180, hot = hover == n;
            ctx.drawTexture(TEX, left + keyX(n), top + KY, 48 + (on ? 20 : hot ? 10 : 0), 150, BLACK_W, BLACK_H, 256, 256);
        }
    }

    @Override
    public boolean shouldPause() { return false; }
}
