package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.homestead.cat.Cattery;
import net.get900.pixelpirates.homestead.cat.CatCoats;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.CatEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** THE CATTERY COUNTER (homestead/cat/Cattery): today's cats from the garden, each alive in its coat, with its rarity,
 *  its price and a Buy button. Buying makes it yours (tame) on the spot. */
@Environment(EnvType.CLIENT)
public class CatteryScreen extends Screen {
    private static final int W = 300, H = 170, CW = 70;
    private final BlockPos counter;
    private final int coins;
    private final List<UUID> ids = new ArrayList<>();
    private final List<CatCoats.Coat> coats = new ArrayList<>();
    private final List<Integer> prices = new ArrayList<>();
    private final List<CatEntity> models = new ArrayList<>();

    public CatteryScreen(NbtCompound n) {
        super(Text.literal("Ship's Cats"));
        counter = BlockPos.fromLong(n.getLong("Counter"));
        coins = n.getInt("Coins");
        for (NbtElement e : n.getList("Cats", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            CatCoats.Coat coat = CatCoats.byId(c.getString("Coat"));
            if (coat == null) continue;
            ids.add(c.getUuid("Id")); coats.add(coat); prices.add(c.getInt("Price"));
        }
    }

    private int px() { return (width - W) / 2; }
    private int py() { return (height - H) / 2; }
    private int cx(int i) { return px() + (W - coats.size() * CW) / 2 + i * CW; }

    @Override
    protected void init() {
        models.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        for (int i = 0; i < coats.size(); i++) {
            CatEntity cat = new CatEntity(EntityType.CAT, mc.world);
            CatCoats.apply(cat, coats.get(i));
            models.add(cat);
            int idx = i;
            boolean afford = coins >= prices.get(i) || mc.player != null && mc.player.isCreative();
            ButtonWidget b = addDrawableChild(ButtonWidget.builder(Text.literal("Buy - " + prices.get(i)), w -> buy(idx))
                    .dimensions(cx(i) + 4, py() + H - 28, CW - 8, 20).build());
            b.active = afford;
        }
    }

    private void buy(int i) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBlockPos(counter);
        buf.writeUuid(ids.get(i));
        ClientPlayNetworking.send(Cattery.COUNTER_BUY, buf);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        renderBackground(c);
        int x = px(), y = py();
        c.fill(x - 2, y - 2, x + W + 2, y + H + 2, 0xFF5A3A22);
        c.fill(x, y, x + W, y + H, 0xFFE8D9B5);
        c.fill(x, y, x + W, y + 18, 0xFF5A3A22);
        c.drawCenteredTextWithShadow(textRenderer, Text.literal("The Ship's-Cat Keeper - today's cats").formatted(Formatting.BOLD), x + W / 2, y + 5, 0xFFF4E2B8);
        if (coats.isEmpty())
            c.drawCenteredTextWithShadow(textRenderer, Text.literal("No cats left today - come back tomorrow."), x + W / 2, y + H / 2, 0xFF3A2416);
        for (int i = 0; i < coats.size(); i++) {
            int x0 = cx(i);
            c.fill(x0 + 3, y + 24, x0 + CW - 3, y + H - 32, 0xFFD8C69C);
            InventoryScreen.drawEntity(c, x0 + CW / 2, y + 92, 34, x0 + CW / 2 - mx, y + 70 - my, models.get(i));
            CatCoats.Coat coat = coats.get(i);
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(coat.name()).formatted(coat.tier().colour), x0 + CW / 2, y + 100, 0xFFFFFFFF);
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(coat.tier().label).formatted(coat.tier().colour), x0 + CW / 2, y + 112, 0xFFFFFFFF);
            c.drawCenteredTextWithShadow(textRenderer, Text.literal(coat.luck() > 0 ? "Luck II" : "Luck I"), x0 + CW / 2, y + 124, 0xFFE0D0A0);
        }
        c.drawText(textRenderer, "Coins: " + coins, x + W - 6 - textRenderer.getWidth("Coins: " + coins), y + 22, 0xFF3A2416, false);
        super.render(c, mx, my, delta);
    }

    @Override
    public boolean shouldPause() { return false; }
}
