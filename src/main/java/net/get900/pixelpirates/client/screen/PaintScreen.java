package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.homestead.art.Art;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * THE EASEL (client): paint a canvas of up to 32 x 32 pixels in any colour. Tools: pencil, a 2x2 brush, fill bucket,
 * colour picker (or right-click the canvas to pick). Colours: 32 swatches, a hue/shade picker, the last colours used.
 * Shapes 1x1, 2x2, 2x1, 1x2 (changing shape starts a fresh canvas). Save keeps the canvas on the easel; Finish turns it
 * into a painting (uses a Blank Canvas); closing the screen saves too.
 */
@Environment(EnvType.CLIENT)
public class PaintScreen extends Screen {
    private static final int[] SWATCHES = {
            0xFFFFFFFF, 0xFFF4EEDC, 0xFFC8C8C8, 0xFF8A8A8A, 0xFF4A4A4A, 0xFF1A1A1A, 0xFF5C3A1E, 0xFF8B5A2B,
            0xFFE23B2E, 0xFFFF8A3D, 0xFFFFD23F, 0xFF9BE04A, 0xFF2E9E4A, 0xFF1F5E3A, 0xFF4FC3E8, 0xFF2A6FDB,
            0xFF1B2E7A, 0xFF7B4FD6, 0xFFC04FD6, 0xFFFF7FB8, 0xFFF2C9A0, 0xFFD69A6A, 0xFF7A1F1F, 0xFF3B2A55,
            0xFF6EC6B0, 0xFF2E6E6E, 0xFFB8D8F8, 0xFFE8D8A8, 0xFFA0783C, 0xFF6B8E23, 0xFF003F5C, 0xFFFFE8E8};
    private enum Tool { PENCIL, BRUSH, FILL, PICK }

    private final BlockPos easel;
    private Art.Size size;
    private int[] px;
    private final boolean hasCanvas;
    private String title;
    private int colour = 0xFF1A1A1A;
    private float hue = 0, sat = 0, val = 0.1f;
    private Tool tool = Tool.PENCIL;
    private final ArrayDeque<int[]> undo = new ArrayDeque<>();
    private final int[] recent = new int[8];
    private TextFieldWidget titleBox;
    private int ox, oy, cell, gx, gy;
    private boolean painting, sentFinish;

    public PaintScreen(BlockPos easel, int size, String title, int[] pixels, boolean hasCanvas) {
        super(Text.literal("Easel"));
        this.easel = easel;
        this.size = Art.Size.of(size);
        this.px = pixels.length == this.size.pw * this.size.ph ? pixels : Art.blank(this.size);
        this.title = title;
        this.hasCanvas = hasCanvas;
        Arrays.fill(recent, Art.WHITE);
    }

    @Override
    public boolean shouldPause() { return false; }

    @Override
    protected void init() {
        ox = (width - 340) / 2; oy = (height - 220) / 2;
        cell = Math.min(192 / size.pw, 192 / size.ph);
        gx = ox + 8 + (192 - cell * size.pw) / 2; gy = oy + 18 + (192 - cell * size.ph) / 2;
        int rx = ox + 210;
        titleBox = new TextFieldWidget(textRenderer, rx, oy + 18, 122, 14, Text.literal("Title"));
        titleBox.setMaxLength(32);
        titleBox.setText(title);
        titleBox.setPlaceholder(Text.literal("Title..."));
        addDrawableChild(titleBox);
        String[] tools = {"Pencil", "Brush", "Fill", "Pick"};
        for (int i = 0; i < 4; i++) {
            Tool t = Tool.values()[i];
            addDrawableChild(ButtonWidget.builder(Text.literal(tools[i]), b -> tool = t).dimensions(rx + i * 31, oy + 150, 30, 14).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Undo"), b -> { if (!undo.isEmpty()) px = undo.pop(); }).dimensions(rx, oy + 167, 40, 14).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Clear"), b -> { push(); px = Art.blank(size); }).dimensions(rx + 41, oy + 167, 40, 14).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(size.label.split(" \\(")[0]), b -> {
            Art.Size n = Art.Size.of((size.ordinal() + 1) % Art.Size.values().length);
            push(); size = n; px = Art.blank(n);
            title = titleBox.getText();
            clearChildren(); init();
        }).dimensions(rx + 82, oy + 167, 40, 14).tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal("Shape: " + size.label + " - changing it starts a fresh canvas"))).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), b -> send(false)).dimensions(rx, oy + 186, 60, 16).build());
        ButtonWidget fin = ButtonWidget.builder(Text.literal("Finish"), b -> { send(true); sentFinish = true; close(); }).dimensions(rx + 62, oy + 186, 60, 16)
                .tooltip(net.minecraft.client.gui.tooltip.Tooltip.of(Text.literal(hasCanvas ? "Make it a painting (uses a Blank Canvas)" : "You need a Blank Canvas to finish"))).build();
        fin.active = hasCanvas;
        addDrawableChild(fin);
    }

    private void push() { undo.push(px.clone()); while (undo.size() > 40) undo.removeLast(); }

    private void send(boolean finish) {
        var buf = PacketByteBufs.create();
        buf.writeBlockPos(easel);
        buf.writeByte(size.ordinal());
        buf.writeString(titleBox == null ? title : titleBox.getText(), 32);
        buf.writeIntArray(px);
        buf.writeBoolean(finish);
        ClientPlayNetworking.send(Art.SAVE, buf);
    }

    @Override
    public void removed() { if (!sentFinish) send(false); }

    // ------------------------------------------------------------------ drawing
    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c);
        c.fill(ox - 4, oy - 4, ox + 344, oy + 216, 0xFF3B2A1A);
        c.fill(ox - 2, oy - 2, ox + 342, oy + 214, 0xFF6B4A2B);
        c.drawText(textRenderer, Text.literal("The Easel"), ox + 8, oy + 5, 0xFFF4E4C0, true);
        // the canvas
        c.fill(gx - 2, gy - 2, gx + cell * size.pw + 2, gy + cell * size.ph + 2, 0xFF2A1A0E);
        for (int y = 0; y < size.ph; y++)
            for (int x = 0; x < size.pw; x++) c.fill(gx + x * cell, gy + y * cell, gx + (x + 1) * cell, gy + (y + 1) * cell, px[y * size.pw + x]);
        int hx = (mx - gx) / cell, hy = (my - gy) / cell;
        if (mx >= gx && my >= gy && hx < size.pw && hy < size.ph) {
            int b = tool == Tool.BRUSH ? 2 : 1;
            c.drawBorder(gx + hx * cell, gy + hy * cell, cell * b, cell * b, 0xAAFFFFFF);
        }
        int rx = ox + 210;
        // the colour: shade square + hue bar + the current colour
        int sx = rx, sy = oy + 38;
        for (int i = 0; i < 32; i++)
            for (int j = 0; j < 32; j++) c.fill(sx + i * 2, sy + j * 2, sx + i * 2 + 2, sy + j * 2 + 2, hsv(hue, i / 31f, 1 - j / 31f));
        c.drawBorder(sx + Math.round(sat * 62) - 1, sy + Math.round((1 - val) * 62) - 1, 4, 4, 0xFFFFFFFF);
        for (int j = 0; j < 64; j++) c.fill(rx + 68, sy + j, rx + 78, sy + j + 1, hsv(j / 64f, 1, 1));
        c.fill(rx + 66, sy + Math.round(hue * 63), rx + 80, sy + Math.round(hue * 63) + 1, 0xFFFFFFFF);
        c.fill(rx + 84, sy, rx + 122, sy + 24, 0xFF000000);
        c.fill(rx + 85, sy + 1, rx + 121, sy + 23, colour);
        c.drawText(textRenderer, String.format("#%06X", colour & 0xFFFFFF), rx + 84, sy + 27, 0xFFF4E4C0, false);
        // the swatches + recent colours
        for (int i = 0; i < SWATCHES.length; i++) {
            int x = rx + (i % 8) * 15, y = oy + 106 + (i / 8) * 9;
            c.fill(x, y, x + 14, y + 8, 0xFF000000); c.fill(x + 1, y + 1, x + 13, y + 7, SWATCHES[i]);
        }
        c.drawText(textRenderer, "Tool: " + tool.name().charAt(0) + tool.name().substring(1).toLowerCase(), rx + 84, sy + 40, 0xFFD8C8A0, false);
        for (int i = 0; i < recent.length; i++) { int x = rx + 84 + (i % 4) * 10, y = sy + 52 + (i / 4) * 7; c.fill(x, y, x + 9, y + 6, recent[i]); }
        super.render(c, mx, my, delta);
    }

    private static int hsv(float h, float s, float v) {
        int rgb = MathHelper.hsvToRgb(MathHelper.clamp(h, 0, 0.999f), s, v);
        return 0xFF000000 | rgb;
    }

    private void setColour(int c) {
        colour = 0xFF000000 | c;
        float r = (c >> 16 & 255) / 255f, g = (c >> 8 & 255) / 255f, b = (c & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        val = max; sat = max == 0 ? 0 : d / max;
        if (d > 0) {
            float hh = max == r ? (g - b) / d : max == g ? 2 + (b - r) / d : 4 + (r - g) / d;
            hue = ((hh / 6f) % 1 + 1) % 1;
        }
    }

    private void use(int c) {
        for (int i = 0; i < recent.length; i++) if (recent[i] == c) return;
        System.arraycopy(recent, 0, recent, 1, recent.length - 1);
        recent[0] = c;
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        int rx = ox + 210, sy = oy + 38;
        if (onCanvas(mx, my)) {
            if (button == 1 || tool == Tool.PICK) { setColour(px[cellIdx(mx, my)]); return true; }
            push();
            if (tool == Tool.FILL) { fill(cellX(mx), cellY(my)); use(colour); return true; }
            painting = true; paint(mx, my); use(colour);
            return true;
        }
        if (mx >= sx(rx) && mx < rx + 64 && my >= sy && my < sy + 64) { sat = (float) (mx - rx) / 63f; val = 1 - (float) (my - sy) / 63f; colour = hsv(hue, sat, val); return true; }
        if (mx >= rx + 68 && mx < rx + 78 && my >= sy && my < sy + 64) { hue = (float) (my - sy) / 64f; colour = hsv(hue, sat, val); return true; }
        for (int i = 0; i < SWATCHES.length; i++) {
            int x = rx + (i % 8) * 15, y = oy + 106 + (i / 8) * 9;
            if (mx >= x && mx < x + 14 && my >= y && my < y + 8) { setColour(SWATCHES[i]); return true; }
        }
        for (int i = 0; i < recent.length; i++) {
            int x = rx + 84 + (i % 4) * 10, y = sy + 52 + (i / 4) * 7;
            if (mx >= x && mx < x + 9 && my >= y && my < y + 6) { setColour(recent[i]); return true; }
        }
        return false;
    }

    private static int sx(int rx) { return rx; }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        int rx = ox + 210, sy = oy + 38;
        if (painting && onCanvas(mx, my)) { paint(mx, my); return true; }
        if (!painting && mx >= rx && mx < rx + 64 && my >= sy && my < sy + 64) { sat = MathHelper.clamp((float) (mx - rx) / 63f, 0, 1); val = MathHelper.clamp(1 - (float) (my - sy) / 63f, 0, 1); colour = hsv(hue, sat, val); return true; }
        if (!painting && mx >= rx + 66 && mx < rx + 80 && my >= sy && my < sy + 64) { hue = MathHelper.clamp((float) (my - sy) / 64f, 0, 1); colour = hsv(hue, sat, val); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) { painting = false; return super.mouseReleased(mx, my, button); }

    private boolean onCanvas(double mx, double my) { return mx >= gx && my >= gy && mx < gx + cell * size.pw && my < gy + cell * size.ph; }

    private int cellX(double mx) { return MathHelper.clamp((int) ((mx - gx) / cell), 0, size.pw - 1); }

    private int cellY(double my) { return MathHelper.clamp((int) ((my - gy) / cell), 0, size.ph - 1); }

    private int cellIdx(double mx, double my) { return cellY(my) * size.pw + cellX(mx); }

    private void paint(double mx, double my) {
        int x = cellX(mx), y = cellY(my), b = tool == Tool.BRUSH ? 2 : 1;
        for (int dy = 0; dy < b; dy++)
            for (int dx = 0; dx < b; dx++)
                if (x + dx < size.pw && y + dy < size.ph) px[(y + dy) * size.pw + x + dx] = colour;
    }

    private void fill(int x, int y) {
        int target = px[y * size.pw + x];
        if (target == colour) return;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{x, y});
        while (!q.isEmpty()) {
            int[] p = q.poll();
            if (p[0] < 0 || p[1] < 0 || p[0] >= size.pw || p[1] >= size.ph || px[p[1] * size.pw + p[0]] != target) continue;
            px[p[1] * size.pw + p[0]] = colour;
            q.add(new int[]{p[0] + 1, p[1]}); q.add(new int[]{p[0] - 1, p[1]}); q.add(new int[]{p[0], p[1] + 1}); q.add(new int[]{p[0], p[1] - 1});
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (titleBox != null && titleBox.isFocused() && key != 256) return titleBox.keyPressed(key, scan, mods) || true;
        if (key == 90 && (mods & 2) != 0 && !undo.isEmpty()) { px = undo.pop(); return true; }    // ctrl+Z
        return super.keyPressed(key, scan, mods);
    }
}
