package net.get900.pixelpirates.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.network.ModNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.List;

public class MapMerchantScreen extends Screen {

    private static final int W = 220;
    private static final int H = 338; // 42 header + 9 rows × 32 + 22 footer + padding

    // Shop entries: {display name, description, cost, item index sent to server}
    private record ShopEntry(String name, String desc, int cost, int index, ItemStack icon) {}

    private static final List<ShopEntry> ENTRIES = List.of(
        new ShopEntry("Ship Radar",             "Reveals ships on your HUD",       15, 0, new ItemStack(ModItems.TREASURE_MAP_COMMON)),
        new ShopEntry("Common Treasure Map",    "Buried loot, Zone 1–2",            5, 1, new ItemStack(ModItems.TREASURE_MAP_COMMON)),
        new ShopEntry("Rare Treasure Map",      "Richer loot, Zone 3",             15, 2, new ItemStack(ModItems.TREASURE_MAP_RARE)),
        new ShopEntry("Legendary Treasure Map", "Legendary loot, Zone 4–5",        30, 3, new ItemStack(ModItems.TREASURE_MAP_LEGENDARY)),
        new ShopEntry("Bounty Map",             "Track an active enemy ship",      10, 4, new ItemStack(ModItems.BOUNTY_MAP)),
        new ShopEntry("Cannon Balls (x64)",     "Free — for now, sailor!",          0, 5, new ItemStack(ModItems.CANNON_BALL)),
        new ShopEntry("Shipwright Table",       "Free — set up your dock!",         0, 6, new ItemStack(ModBlocks.SHIPWRIGHT_TABLE)),
        new ShopEntry("Cutlass",                "Free — every pirate needs one!",   0, 7, new ItemStack(ModItems.CUTLASS)),
        new ShopEntry("Starter Rations",        "Free — 8 Bananas + 3 Grog",        0, 8, new ItemStack(ModItems.BANANA))
    );

    private final boolean hasRadar;
    private int coins;

    public MapMerchantScreen(boolean hasRadar, int coins) {
        super(Text.literal("Map Merchant"));
        this.hasRadar = hasRadar;
        this.coins    = coins;
    }

    @Override
    protected void init() {
        int px = (this.width  - W) / 2;
        int py = (this.height - H) / 2;
        int rowStart = py + 42;

        for (int i = 0; i < ENTRIES.size(); i++) {
            ShopEntry entry = ENTRIES.get(i);
            final int index = entry.index();
            int rowY = rowStart + i * 32;

            boolean alreadyOwned = (index == 0 && hasRadar);
            boolean canAfford    = coins >= entry.cost();

            ButtonWidget btn = ButtonWidget.builder(
                    Text.literal(alreadyOwned ? "Owned" : canAfford ? "Buy" : "§7Buy"),
                    b -> buy(index))
                .position(px + W - 58, rowY + 7)
                .size(52, 16)
                .build();

            btn.active = !alreadyOwned && canAfford;
            this.addDrawableChild(btn);
        }

        // Close button
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> this.close())
            .position(px + W / 2 - 40, py + H - 22)
            .size(80, 16)
            .build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);

        int px = (this.width  - W) / 2;
        int py = (this.height - H) / 2;

        // Panel background
        ctx.fill(px, py, px + W, py + H, 0xD0100808);
        // Border
        ctx.fill(px,         py,         px + W,     py + 1,     0xFF886644);
        ctx.fill(px,         py + H - 1, px + W,     py + H,     0xFF886644);
        ctx.fill(px,         py,         px + 1,     py + H,     0xFF886644);
        ctx.fill(px + W - 1, py,         px + W,     py + H,     0xFF886644);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, "§6Map Merchant", px + W / 2, py + 8, 0xFFFFFF);
        // Divider
        ctx.fill(px + 6, py + 18, px + W - 6, py + 19, 0xFF886644);
        // Coin balance
        ctx.drawCenteredTextWithShadow(textRenderer,
            "§7Doubloons: §e" + coins, px + W / 2, py + 23, 0xFFFFFF);
        // Divider
        ctx.fill(px + 6, py + 34, px + W - 6, py + 35, 0xFF886644);

        // Shop rows
        int rowStart = py + 42;
        for (int i = 0; i < ENTRIES.size(); i++) {
            ShopEntry entry = ENTRIES.get(i);
            int rowY = rowStart + i * 32;
            boolean alreadyOwned = (entry.index() == 0 && hasRadar);

            // Row separator
            if (i > 0) ctx.fill(px + 6, rowY - 2, px + W - 6, rowY - 1, 0x40FFFFFF);

            // Icon
            ctx.drawItem(entry.icon(), px + 8, rowY + 6);

            // Name + description
            String nameColor = alreadyOwned ? "§7" : "§f";
            ctx.drawTextWithShadow(textRenderer, nameColor + entry.name(), px + 28, rowY + 5, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer, "§8" + entry.desc(), px + 28, rowY + 15, 0xFFFFFF);

            // Price
            String priceColor = coins >= entry.cost() ? "§e" : "§c";
            ctx.drawTextWithShadow(textRenderer,
                priceColor + entry.cost() + " §7coins", px + W - 58, rowY, 0xFFFFFF);
        }

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void buy(int index) {
        ShopEntry entry = ENTRIES.get(index);
        var buf = PacketByteBufs.create();
        buf.writeInt(index);
        ClientPlayNetworking.send(ModNetworking.MAP_MERCHANT_BUY, buf);
        // Deduct locally so buttons update immediately
        coins -= entry.cost();
        // Rebuild buttons with updated state
        this.clearChildren();
        this.init();
    }

    @Override
    public boolean shouldPause() { return false; }
}
