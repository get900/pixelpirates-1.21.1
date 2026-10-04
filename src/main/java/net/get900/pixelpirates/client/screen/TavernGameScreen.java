package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.homestead.tavern.TavernGames;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

/**
 * Base of the tavern game screens: the felt panel, the shared event log, the countdown, and the ACTION packets back to
 * the table. The server pushes a fresh state on every change (update -> rebuild the buttons). Art: tools/gen_tavern_assets.py.
 */
@Environment(EnvType.CLIENT)
public abstract class TavernGameScreen extends Screen {
    public static final Identifier TEX = new Identifier("pixelpirates", "textures/gui/tavern_games.png");
    public static final int W = 256, H = 232, TW = 512, TH = 256;
    public final BlockPos pos;
    protected NbtCompound st;
    protected int left, top;
    private long received = System.currentTimeMillis();

    protected TavernGameScreen(Text title, NbtCompound st) {
        super(title);
        this.st = st;
        this.pos = BlockPos.fromLong(st.getLong("Pos"));
    }

    public void update(NbtCompound n) {
        st = n;
        received = System.currentTimeMillis();
        clearAndInit();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        buttons();
    }

    protected abstract void buttons();

    protected abstract void draw(DrawContext ctx, int mx, int my, float delta);

    /** Drawn after the buttons (icons on top of them). */
    protected void drawOver(DrawContext ctx, int mx, int my) {}

    protected void send(String action, int a, int b) {
        var buf = PacketByteBufs.create();
        buf.writeBlockPos(pos);
        buf.writeString(action);
        buf.writeVarInt(a);
        buf.writeVarInt(b);
        ClientPlayNetworking.send(TavernGames.ACTION, buf);
    }

    protected ButtonWidget btn(String label, int x, int y, int w, int h, Runnable r) {
        ButtonWidget b = ButtonWidget.builder(Text.literal(label), x2 -> r.run()).dimensions(left + x, top + y, w, h).build();
        addDrawableChild(b);
        return b;
    }

    /** Seconds left on the server's countdown, counted down locally between packets. */
    protected int secondsLeft() {
        long ms = st.getInt("TimeLeft") * 50L - (System.currentTimeMillis() - received);
        return (int) Math.max(0, Math.ceil(ms / 1000.0));
    }

    protected void tex(DrawContext ctx, int x, int y, int u, int v, int w, int h) { ctx.drawTexture(TEX, left + x, top + y, u, v, w, h, TW, TH); }

    protected void center(DrawContext ctx, String s, int y, int color) {
        ctx.drawTextWithShadow(textRenderer, s, left + (W - textRenderer.getWidth(s)) / 2, top + y, color);
    }

    protected void small(DrawContext ctx, String s, int x, int y, int color, float scale) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(left + x, top + y, 0);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.drawTextWithShadow(textRenderer, s, 0, 0, color);
        ctx.getMatrices().pop();
    }

    protected String fit(String s, int px, float scale) {
        if (textRenderer.getWidth(s) * scale <= px) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "..") * scale > px) s = s.substring(0, s.length() - 1);
        return s + "..";
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawTexture(TEX, left, top, 0, 0, W, H, TW, TH);
        draw(ctx, mx, my, delta);
        super.render(ctx, mx, my, delta);
        drawOver(ctx, mx, my);
        // the log, newest at the bottom
        NbtList log = st.getList("Log", NbtElement.STRING_TYPE);
        int lines = Math.min(3, log.size());
        for (int i = 0; i < lines; i++) {
            String s = log.getString(log.size() - lines + i);
            small(ctx, fit(s, W - 20, 0.75f), 10, H - 25 + i * 7, i == lines - 1 ? 0xFFF3E2B0 : 0xFFB8A880, 0.75f);
        }
    }

    @Override
    public void removed() {
        send("close", 0, 0);
        super.removed();
    }

    @Override
    public boolean shouldPause() { return false; }
}
