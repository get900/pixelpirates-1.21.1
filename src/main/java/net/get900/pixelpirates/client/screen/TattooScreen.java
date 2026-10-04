package net.get900.pixelpirates.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.homestead.client.TattooFeature;
import net.get900.pixelpirates.homestead.tattoo.Tattoos;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * THE TATTOOIST'S FLASH SHEET (homestead/tattoo/Tattoos): your pirate on the left (wearing what you've picked, so you
 * see it before you pay), the four spots, the twelve designs (earned ones dark until you've beaten their boss), and the
 * buttons: ink it, or have it covered up. Prices from the server's Tattoos constants.
 */
@Environment(EnvType.CLIENT)
public class TattooScreen extends Screen {
    private static final int W = 320, H = 210, CELL = 26, COLS = 5, ICON = 24;
    private static final int PAPER = 0xFFE8D9B5, EDGE = 0xFF5A3A22, INKC = 0xFF3A2416, DIM = 0xFF8C7558, PICK = 0xFFB0302A;
    private BlockPos chair;
    private NbtCompound current = new NbtCompound(), unlocked = new NbtCompound(), need = new NbtCompound();
    private int coins;
    private int slot = 0;
    private String design = null;
    private ButtonWidget inkButton, coverButton;
    /** The preview's turn (degrees): picking a spot turns your pirate to show it; the arrows / dragging turn it by hand. */
    private float spin = 0, shown = 0;
    private static final float[] SLOT_SPIN = {-90, 90, 0, 180};

    public TattooScreen(NbtCompound n) {
        super(Text.literal("The Tattooist"));
        update(n);
    }

    public void update(NbtCompound n) {
        chair = BlockPos.fromLong(n.getLong("Chair"));
        current = n.getCompound("Current");
        unlocked = n.getCompound("Unlocked");
        need = n.getCompound("Need");
        coins = n.getInt("Coins");
        design = null;
        refresh();
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }

    @Override
    protected void init() {
        inkButton = addDrawableChild(ButtonWidget.builder(Text.literal("Ink it"), b -> send(Tattoos.APPLY))
                .dimensions(px() + 120, py() + H - 28, 96, 20).build());
        coverButton = addDrawableChild(ButtonWidget.builder(Text.literal("Cover it up"), b -> send(Tattoos.REMOVE))
                .dimensions(px() + 220, py() + H - 28, 92, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> spin -= 45).dimensions(px() + 10, py() + H - 26, 20, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> spin += 45).dimensions(px() + 54, py() + H - 26, 20, 16).build());
        refresh();
    }

    private void refresh() {
        if (inkButton == null) return;
        String s = Tattoos.SLOTS.get(slot);
        Tattoos.Design d = design == null ? null : Tattoos.byId(design);
        boolean can = d != null && unlocked.getBoolean(d.id()) && !d.id().equals(current.getString(s));
        inkButton.active = can && (coins >= d.price() || client != null && client.player != null && client.player.isCreative());
        inkButton.setMessage(Text.literal(d == null ? "Ink it" : "Ink it - " + d.price() + " coins"));
        coverButton.active = current.contains(s);
        coverButton.setMessage(Text.literal("Cover it up - " + Tattoos.REMOVE_PRICE));
    }

    private void send(net.minecraft.util.Identifier channel) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(chair);
        buf.writeString(Tattoos.SLOTS.get(slot));
        if (channel.equals(Tattoos.APPLY)) buf.writeString(design == null ? "" : design);
        ClientPlayNetworking.send(channel, buf);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c);
        int x = px(), y = py();
        c.fill(x - 2, y - 2, x + W + 2, y + H + 2, EDGE);
        c.fill(x, y, x + W, y + H, PAPER);
        c.fill(x, y, x + W, y + 18, EDGE);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("The Tattooist's Flash Sheet").formatted(Formatting.BOLD), x + W / 2, y + 7, 0xFFF4E2B8);
        // the pirate, wearing the pick (a preview only - put back after drawing)
        var mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            NbtCompound real = TattooFeature.TATTOOS.get(mc.player.getUuid());
            NbtCompound show = real == null ? new NbtCompound() : real.copy();
            if (design != null) show.putString(Tattoos.SLOTS.get(slot), design);
            TattooFeature.TATTOOS.put(mc.player.getUuid(), show);
            c.fill(x + 8, y + 22, x + 76, y + H - 8, 0xFFD8C69C);
            shown += (spin - shown) * Math.min(1f, delta * 0.35f);                                   // ease round to the spot
            drawTurned(c, x + 42, y + H - 34, 56, shown, mc.player);
            if (real == null) TattooFeature.TATTOOS.remove(mc.player.getUuid()); else TattooFeature.TATTOOS.put(mc.player.getUuid(), real);
        }
        // the four spots
        for (int i = 0; i < Tattoos.SLOTS.size(); i++) {
            int sx = x + 84, sy = y + 24 + i * 30;
            boolean sel = i == slot, hover = mx >= sx && mx < sx + 92 && my >= sy && my < sy + 26;
            c.fill(sx, sy, sx + 92, sy + 26, sel ? PICK : hover ? 0xFFCDB68A : 0xFFDCC9A0);
            c.drawText(textRenderer, Tattoos.SLOT_NAMES.get(i), sx + 4, sy + 3, sel ? 0xFFFFF4DC : INKC, false);
            Tattoos.Design d = Tattoos.byId(current.getString(Tattoos.SLOTS.get(i)));
            c.drawText(textRenderer, d == null ? "- bare -" : d.name(), sx + 4, sy + 14, sel ? 0xFFF0D8A8 : DIM, false);
        }
        // the designs (4 x 3)
        Tattoos.Design hovered = null;
        for (int i = 0; i < Tattoos.DESIGNS.size(); i++) {
            Tattoos.Design d = Tattoos.DESIGNS.get(i);
            int dx = gx(i), dy = gy(i);
            boolean open = unlocked.getBoolean(d.id()), sel = d.id().equals(design);
            boolean hover = mx >= dx && mx < dx + ICON && my >= dy && my < dy + ICON;
            if (hover) hovered = d;
            c.fill(dx - 1, dy - 1, dx + ICON + 1, dy + ICON + 1, sel ? PICK : hover ? 0xFFB89A6A : 0xFFCDB68A);
            c.fill(dx, dy, dx + ICON, dy + ICON, 0xFFE9C7A6);
            if (!open) RenderSystem.setShaderColor(0.12f, 0.1f, 0.1f, 1f);
            c.drawTexture(new Identifier("pixelpirates", "textures/gui/tattoo/" + d.id() + ".png"), dx, dy, ICON, ICON, 0, 0, 32, 32, 32, 32);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        c.drawText(textRenderer, "Coins: " + coins, x + 84, y + H - 44, INKC, false);
        super.render(c, mx, my, delta);
        if (hovered != null) {
            List<Text> tip = new java.util.ArrayList<>();
            tip.add(Text.literal(hovered.name()).formatted(Formatting.GOLD));
            if (!unlocked.getBoolean(hovered.id())) tip.add(Text.literal("Earned: beat " + need.getString(hovered.id())).formatted(Formatting.RED));
            else tip.add(Text.literal(hovered.price() + " coins").formatted(Formatting.GRAY));
            c.drawTooltip(textRenderer, tip, mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int x = px(), y = py();
        for (int i = 0; i < Tattoos.SLOTS.size(); i++) {
            int sx = x + 84, sy = y + 24 + i * 30;
            if (mx >= sx && mx < sx + 92 && my >= sy && my < sy + 26) { slot = i; spin = SLOT_SPIN[i]; refresh(); return true; }
        }
        for (int i = 0; i < Tattoos.DESIGNS.size(); i++) {
            int dx = gx(i), dy = gy(i);
            if (mx >= dx && mx < dx + ICON && my >= dy && my < dy + ICON) {
                design = unlocked.getBoolean(Tattoos.DESIGNS.get(i).id()) ? Tattoos.DESIGNS.get(i).id() : null;
                refresh();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (mx >= px() + 8 && mx < px() + 76 && my >= py() + 22 && my < py() + H - 8) { spin += (float) dx * 2.5f; shown = spin; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    private int gx(int i) { return px() + 184 + (i % COLS) * CELL; }

    private int gy(int i) { return py() + 22 + (i / COLS) * CELL; }

    /** InventoryScreen.drawEntity, but turned by {@code turn} degrees instead of following the mouse (so you can see your back). */
    private static void drawTurned(DrawContext c, int x, int y, int size, float turn, net.minecraft.entity.LivingEntity e) {
        org.joml.Quaternionf q = new org.joml.Quaternionf().rotateZ((float) Math.PI), q2 = new org.joml.Quaternionf().rotateX(0.18f);
        q.mul(q2);
        float by = e.bodyYaw, yw = e.getYaw(), pt = e.getPitch(), ph = e.prevHeadYaw, hy = e.headYaw;
        e.bodyYaw = 180f + turn; e.setYaw(180f + turn); e.setPitch(0); e.headYaw = e.getYaw(); e.prevHeadYaw = e.getYaw();
        InventoryScreen.drawEntity(c, x, y, size, q, q2, e);
        e.bodyYaw = by; e.setYaw(yw); e.setPitch(pt); e.prevHeadYaw = ph; e.headYaw = hy;
    }

    @Override
    public boolean shouldPause() { return false; }
}
