package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.homestead.bounty.Bounties;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * The BOUNTY BOARD menu (2026-10-01): today's contracts pinned to a cork board as wanted posters (picture, task,
 * progress, reward), and a wax "Hand in & collect" button (C2S Bounties.HAND_IN; the server answers with a fresh
 * OPEN_BOARD packet). Art: tools/gen_gui_textures.py bounty().
 */
@Environment(EnvType.CLIENT)
public class BountyScreen extends Screen {
    private static final Identifier TEX = new Identifier("pixelpirates", "textures/gui/bounty.png");
    private static final int TW = 512, TH = 256, W = 300, H = 200;
    private static final int POSTER_U = 400, POSTER_W = 88, POSTER_H = 120, BTN_W = 120, BTN_H = 18, BTN_V = 210;
    private static final int INK = 0xFF3B2A1A, INK_L = 0xFF6A5030, RED = 0xFF8A2A1A, GREEN = 0xFF2A6A2A, CREAM = 0xFFF6E8C8;

    private NbtCompound data;
    private int px, py;

    public BountyScreen(NbtCompound data) {
        super(Text.literal("Bounty Board"));
        this.data = data;
    }

    /** A fresh packet while the screen is open: just swap the data. */
    public void update(NbtCompound data) { this.data = data; }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
    }

    @Override
    public boolean shouldPause() { return false; }

    private int buttonX() { return px + (W - BTN_W) / 2; }

    private int buttonY() { return py + H - 30; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawTexture(TEX, px, py, 0, 0, W, H, TW, TH);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("WANTED"), px + W / 2, py + 14, CREAM);
        small(ctx, "Day " + data.getLong("Day") + "  -  " + data.getInt("Done") + " contracts done", px + W / 2, py + 25, 0xFFE8D0A0, 0.7f, true);

        NbtList cs = data.getList("C", NbtElement.COMPOUND_TYPE);
        int shown = Math.min(3, cs.size());
        int gap = 6, total = shown * POSTER_W + (shown - 1) * gap, x0 = px + (W - total) / 2;
        boolean anyDone = false;
        for (int i = 0; i < shown; i++) {
            NbtCompound c = cs.getCompound(i);
            int x = x0 + i * (POSTER_W + gap), y = py + 34;
            int have = c.getInt("Have"), need = c.getInt("Need");
            boolean done = have >= need;
            anyDone |= done || c.getString("T").equals(Bounties.DELIVER);
            ctx.drawTexture(TEX, x, y, POSTER_U, done ? 122 : 0, POSTER_W, POSTER_H, TW, TH);
            String kind = switch (c.getString("T")) { case Bounties.HUNT -> "HUNT"; case Bounties.DELIVER -> "DELIVER"; default -> "SINK"; };
            small(ctx, kind, x + POSTER_W / 2, y + 8, RED, 0.85f, true);
            // the poster's picture, drawn big
            ItemStack icon = new ItemStack(Registries.ITEM.get(new Identifier(c.getString("Icon"))));
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + POSTER_W / 2f - 12, y + 24, 0);
            ctx.getMatrices().scale(1.5f, 1.5f, 1);
            ctx.drawItem(icon, 0, 0);
            ctx.getMatrices().pop();
            // the task, wrapped to the poster
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(c.getString("Desc")), (int) ((POSTER_W - 10) / 0.75f));
            for (int l = 0; l < Math.min(3, lines.size()); l++) {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x + 5, y + 52 + l * 8, 200);
                ctx.getMatrices().scale(0.75f, 0.75f, 1);
                ctx.drawText(textRenderer, lines.get(l), 0, 0, INK, false);
                ctx.getMatrices().pop();
            }
            // progress bar + numbers
            int bx = x + 6, by = y + 80, bw = POSTER_W - 12;
            ctx.fill(bx, by, bx + bw, by + 4, 0xFF6A5030);
            ctx.fill(bx + 1, by + 1, bx + 1 + (int) ((bw - 2) * Math.min(1f, have / (float) Math.max(1, need))), by + 3, done ? 0xFF4AA03A : 0xFFC8902A);
            small(ctx, have + " / " + need, x + POSTER_W / 2, by + 6, INK_L, 0.7f, true);
            small(ctx, "+" + c.getInt("Coins") + " doubloons", x + 6, y + 98, done ? GREEN : INK, 0.7f, false);
            small(ctx, "rep: " + c.getString("Rep"), x + 6, y + 106, INK_L, 0.6f, false);
        }
        if (cs.isEmpty()) small(ctx, "No more work today, sailor. New contracts at dawn.", px + W / 2, py + 90, CREAM, 0.8f, true);
        else if (cs.size() > shown) small(ctx, "+" + (cs.size() - shown) + " more", px + W - 40, py + 26, CREAM, 0.6f, false);

        // the last hand-in, a line or two on the frame
        NbtList msg = data.getList("Msg", NbtElement.STRING_TYPE);
        for (int i = 0; i < Math.min(2, msg.size()); i++)
            small(ctx, msg.getString(i), px + W / 2, py + 157 + i * 7, i == 0 ? CREAM : 0xFFE8D0A0, 0.6f, true);

        int bx = buttonX(), by = buttonY();
        boolean hov = anyDone && in(mx, my, bx, by, BTN_W, BTN_H);
        ctx.drawTexture(TEX, bx, by, (anyDone ? (hov ? 1 : 0) : 2) * 122, BTN_V, BTN_W, BTN_H, TW, TH);
        ctx.drawCenteredTextWithShadow(textRenderer, "Hand in & collect", bx + BTN_W / 2, by + 5, anyDone ? 0xFFFFF0D0 : 0xFFB0A898);
        super.render(ctx, mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && in(mx, my, buttonX(), buttonY(), BTN_W, BTN_H)) {
            ClientPlayNetworking.send(Bounties.HAND_IN, PacketByteBufs.create());
            if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1f));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    private void small(DrawContext ctx, String s, int x, int y, int col, float scale, boolean centred) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(centred ? x - textRenderer.getWidth(s) * scale / 2f : x, y, 200);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.drawText(textRenderer, s, 0, 0, col, false);
        ctx.getMatrices().pop();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
