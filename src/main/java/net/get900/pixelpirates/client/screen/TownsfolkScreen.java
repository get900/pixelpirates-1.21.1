package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.homestead.town.TownTalk;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/**
 * Talking to a townsperson (homestead/town/TownTalk): a parchment card with their portrait (the live model), name and
 * title, what they say, and the buttons the server sent - Chat, Trade, their service, Goodbye.
 */
@Environment(EnvType.CLIENT)
public class TownsfolkScreen extends Screen {
    private static final int W = 300, PAPER = 0xFFEADBB8, EDGE = 0xFF5A3A1E, INK = 0xFF3A2414;
    private final int entityId;
    private final String name, title, say;
    private String friendship = "";
    private final List<String[]> opts;
    private int h;

    public TownsfolkScreen(int entityId, String name, String title, String say, List<String[]> opts) {
        super(Text.literal(name));
        this.entityId = entityId; this.name = name; this.title = title; this.say = say; this.opts = opts;
    }

    public int entityId() { return entityId; }

    public TownsfolkScreen friendship(String f) { this.friendship = f; return this; }

    private List<OrderedText> lines() { return textRenderer.wrapLines(Text.literal("\"" + say + "\""), W - 92); }

    @Override
    protected void init() {
        int text = Math.max(70, 34 + lines().size() * 10);
        h = text + 8 + opts.size() * 22 + 6;
        int x0 = (width - W) / 2, y0 = (height - h) / 2, by = y0 + text + 8;
        for (String[] o : opts) {
            String key = o[0];
            addDrawableChild(ButtonWidget.builder(Text.literal(o[1]), b -> {
                send(key);
                if (key.equals("bye") || key.equals("trade") || key.equals("barber") || key.equals("tattoo") || key.equals("cats")) close();
            }).dimensions(x0 + 12, by, W - 24, 20).build());
            by += 22;
        }
    }

    private void send(String key) {
        var buf = PacketByteBufs.create();
        buf.writeVarInt(entityId);
        buf.writeString(key, 64);
        ClientPlayNetworking.send(TownTalk.ACT, buf);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c);
        int x0 = (width - W) / 2, y0 = (height - h) / 2;
        c.fill(x0 - 3, y0 - 3, x0 + W + 3, y0 + h + 3, EDGE);
        c.fill(x0, y0, x0 + W, y0 + h, PAPER);
        c.fill(x0 + 6, y0 + 6, x0 + 72, y0 + 72, 0xFFD8C69C);
        c.drawBorder(x0 + 6, y0 + 6, 66, 66, 0xFF8A6A40);
        if (client != null && client.world != null && client.world.getEntityById(entityId) instanceof LivingEntity e)
            InventoryScreen.drawEntity(c, x0 + 39, y0 + 68, 30, (float) (x0 + 39 - mx), (float) (y0 + 20 - my), e);
        c.drawText(textRenderer, Text.literal(name).formatted(Formatting.BOLD), x0 + 80, y0 + 8, INK, false);
        c.drawText(textRenderer, Text.literal(title).formatted(Formatting.ITALIC), x0 + 80, y0 + 19, 0xFF7A5A34, false);
        if (!friendship.isEmpty()) c.drawText(textRenderer, Text.literal("<3 " + friendship), x0 + W - 8 - textRenderer.getWidth("<3 " + friendship), y0 + 8, 0xFFB04060, false);
        int y = y0 + 34;
        for (OrderedText l : lines()) { c.drawText(textRenderer, l, x0 + 80, y, INK, false); y += 10; }
        super.render(c, mx, my, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
