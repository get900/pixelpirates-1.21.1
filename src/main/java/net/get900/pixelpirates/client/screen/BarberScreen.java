package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.homestead.beard.Beards;
import net.get900.pixelpirates.homestead.client.BeardFeature;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.List;

/**
 * THE BARBER'S BOOK (homestead/beard/Beards): your pirate on the left wearing the style you're looking at (turn it with
 * the arrows), how far your beard has grown, the styles (the ones you haven't grown enough for are greyed, with what they
 * need), the colours, and Trim / Shave / Stay clean-shaven.
 */
@Environment(EnvType.CLIENT)
public class BarberScreen extends Screen {
    private static final int W = 330, H = 214;
    private static final int PAPER = 0xFFEAE0CC, EDGE = 0xFF5A2A22, INK = 0xFF3A2416, DIM = 0xFF9A8A70, PICK = 0xFFB0302A;
    private BlockPos chair;
    private long growth;
    private boolean keep;
    private String look = "", colour = "brown", chosen = "";
    private int coins, price;
    private String style;                                  // the style being looked at ("" = their own / let it grow)
    private ButtonWidget trim, shave, keepBtn;
    private float spin = 25, shown = 25;

    public BarberScreen(NbtCompound n) {
        super(Text.literal("The Barber"));
        update(n);
    }

    public void update(NbtCompound n) {
        chair = BlockPos.fromLong(n.getLong("Chair"));
        growth = n.getLong("G");
        keep = n.getBoolean("K");
        look = n.getString("Look");
        colour = n.getString("C").isEmpty() ? "brown" : n.getString("C");
        chosen = n.getString("S");
        coins = n.getInt("Coins");
        price = n.getInt("Price");
        style = look;
        refresh();
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }

    @Override
    protected void init() {
        trim = addDrawableChild(ButtonWidget.builder(Text.literal("Trim"), b -> send("style", style)).dimensions(px() + 92, py() + H - 26, 100, 18).build());
        shave = addDrawableChild(ButtonWidget.builder(Text.literal("Shave clean"), b -> send("shave", "")).dimensions(px() + 196, py() + H - 26, 62, 18).build());
        keepBtn = addDrawableChild(ButtonWidget.builder(Text.literal("Stay shaven"), b -> send("keep", "")).dimensions(px() + 262, py() + H - 26, 62, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> spin -= 45).dimensions(px() + 10, py() + H - 26, 20, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> spin += 45).dimensions(px() + 56, py() + H - 26, 20, 16).build());
        refresh();
    }

    private void refresh() {
        if (trim == null) return;
        Beards.Style s = Beards.byId(style);
        int have = Beards.stage(growth);
        boolean creative = client != null && client.player != null && client.player.isCreative();
        if (s == null) {
            trim.setMessage(Text.literal(chosen.isEmpty() ? "Letting it grow" : "Let it grow"));
            trim.active = !chosen.isEmpty() && !keep;
        } else {
            trim.setMessage(Text.literal("Trim - " + price + " coins"));
            trim.active = !keep && have >= s.stage() && (coins >= price || creative) && !(s.id().equals(chosen));
        }
        shave.active = growth > 0 && !keep;
        keepBtn.setMessage(Text.literal(keep ? "Let it grow" : "Stay shaven"));
    }

    private void send(String what, String arg) {
        var buf = PacketByteBufs.create();
        buf.writeBlockPos(chair);
        buf.writeString(what, 16);
        buf.writeString(arg == null || arg.isEmpty() ? "grow" : arg, 32);
        buf.writeString(colour, 16);
        ClientPlayNetworking.send(Beards.ACT, buf);
    }

    @Override
    public void removed() { BeardFeature.PREVIEW = null; }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c);
        int x = px(), y = py();
        c.fill(x - 2, y - 2, x + W + 2, y + H + 2, EDGE);
        c.fill(x, y, x + W, y + H, PAPER);
        c.fill(x, y, x + W, y + 18, EDGE);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("The Barber's Book").formatted(Formatting.BOLD), x + W / 2, y + 7, 0xFFF4E2B8);
        // the pirate, trying the style on
        var mc = MinecraftClient.getInstance();
        BeardFeature.PREVIEW = new String[]{keep ? "" : style, colour};
        if (mc.player != null) {
            c.fill(x + 8, y + 22, x + 78, y + H - 32, 0xFFD8C8A8);
            shown += (spin - shown) * Math.min(1f, delta * 0.35f);
            drawTurned(c, x + 43, y + H - 40, 60, shown, mc.player);
        }
        // growth
        int st = Beards.stage(growth);
        c.drawText(textRenderer, Text.literal(keep ? "Staying clean-shaven" : "Growth: " + Beards.STAGE_NAMES[st]).formatted(Formatting.BOLD), x + 88, y + 24, INK, false);
        if (!keep && st < 3) {
            long a = Beards.STAGE[st], b = Beards.STAGE[st + 1];
            float f = (float) (growth - a) / (b - a);
            c.fill(x + 88, y + 36, x + 238, y + 41, 0xFF8A7A60);
            c.fill(x + 88, y + 36, x + 88 + Math.round(150 * f), y + 41, 0xFF6A3A22);
            long left = (b - growth) / 1200;                                              // minutes of play
            c.drawText(textRenderer, "next: " + Beards.STAGE_NAMES[st + 1] + " in ~" + (left >= 60 ? left / 60 + "h " : "") + left % 60 + "m of play",
                    x + 88, y + 44, DIM, false);
        }
        // the styles: "let it grow" + the twelve
        List<Text> tip = null;
        for (int i = -1; i < Beards.STYLES.size(); i++) {
            int bx = sx(i), by = sy(i);
            Beards.Style s = i < 0 ? null : Beards.STYLES.get(i);
            String id = s == null ? "" : s.id();
            boolean open = s == null || (!keep && st >= s.stage()), sel = id.equals(style) || (s == null && style.isEmpty()), cur = id.equals(chosen) || (s == null && chosen.isEmpty());
            boolean hover = mx >= bx && mx < bx + 76 && my >= by && my < by + 12;
            c.fill(bx, by, bx + 76, by + 12, sel ? PICK : hover ? 0xFFD8C8A0 : 0xFFE0D2B4);
            String name = s == null ? "Let it grow" : s.name();
            c.drawText(textRenderer, textRenderer.trimToWidth(name, 70) + (cur ? " *" : ""), bx + 3, by + 2, sel ? 0xFFFFF4DC : open ? INK : DIM, false);
            if (hover && s != null && !open)
                tip = List.of(Text.literal(s.name()).formatted(Formatting.GOLD), Text.literal("Needs a " + Beards.STAGE_NAMES[s.stage()].toLowerCase() + " - keep growing").formatted(Formatting.RED));
        }
        // colours
        c.drawText(textRenderer, "Colour", x + 88, y + 150, INK, false);
        for (int i = 0; i < Beards.COLOURS.length; i++) {
            int cx = x + 128 + i * 18, cy = y + 148;
            c.fill(cx - 1, cy - 1, cx + 15, cy + 13, Beards.COLOURS[i].equals(colour) ? PICK : 0xFF5A4A3A);
            c.fill(cx, cy, cx + 14, cy + 12, 0xFF000000 | Beards.COLOUR_RGB[i]);
            if (mx >= cx && mx < cx + 14 && my >= cy && my < cy + 12) tip = List.of(Text.literal(Beards.COLOUR_NAMES[i]));
        }
        c.drawText(textRenderer, "Coins: " + coins + "    * = what you wear now", x + 88, y + 168, DIM, false);
        super.render(c, mx, my, delta);
        if (tip != null) c.drawTooltip(textRenderer, tip, mx, my);
    }

    private int sx(int i) { int k = i + 1; return px() + 88 + (k % 3) * 79; }

    private int sy(int i) { int k = i + 1; return py() + 58 + (k / 3) * 15; }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = -1; i < Beards.STYLES.size(); i++) {
            int bx = sx(i), by = sy(i);
            if (mx >= bx && mx < bx + 76 && my >= by && my < by + 12) { style = i < 0 ? "" : Beards.STYLES.get(i).id(); refresh(); return true; }
        }
        for (int i = 0; i < Beards.COLOURS.length; i++) {
            int cx = px() + 128 + i * 18, cy = py() + 148;
            if (mx >= cx && mx < cx + 14 && my >= cy && my < cy + 12) {
                colour = Beards.COLOURS[i];
                if (style.equals(look) && !look.isEmpty()) send("colour", "");                   // a dye job on what you wear: free
                refresh();
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (mx >= px() + 8 && mx < px() + 78 && my >= py() + 22 && my < py() + H - 32) { spin += (float) dx * 2.5f; shown = spin; return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    /** InventoryScreen.drawEntity turned by {@code turn} degrees, the camera a little closer to the face. */
    private static void drawTurned(DrawContext c, int x, int y, int size, float turn, net.minecraft.entity.LivingEntity e) {
        org.joml.Quaternionf q = new org.joml.Quaternionf().rotateZ((float) Math.PI), q2 = new org.joml.Quaternionf().rotateX(0.1f);
        q.mul(q2);
        float by = e.bodyYaw, yw = e.getYaw(), pt = e.getPitch(), ph = e.prevHeadYaw, hy = e.headYaw;
        e.bodyYaw = 180f + turn; e.setYaw(180f + turn); e.setPitch(0); e.headYaw = e.getYaw(); e.prevHeadYaw = e.getYaw();
        InventoryScreen.drawEntity(c, x, y, size, q, q2, e);
        e.bodyYaw = by; e.setYaw(yw); e.setPitch(pt); e.prevHeadYaw = ph; e.headYaw = hy;
    }

    @Override
    public boolean shouldPause() { return false; }
}
