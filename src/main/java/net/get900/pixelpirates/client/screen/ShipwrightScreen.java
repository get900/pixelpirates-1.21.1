package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.world.ShipUpgrades;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class ShipwrightScreen extends Screen {

    private static final int W = 270;
    private static final int CONTENT_H = 160;
    private static final int H = 32 + CONTENT_H + 24;
    private static final int PER_PAGE = 6;
    private static final int BW = 180;
    private static final int BH = 18;
    private static final int GAP = 3;

    // Data from server
    private final List<String> blueprints;
    private final long shipId;
    private final int currentHp;
    private final int effectiveMaxHp;
    private final int mastCount;
    private final int[] upgradeLevels; // index matches ShipUpgrades.ALL

    private int tab = 0; // 0=Commission, 1=Upgrades
    private int page = 0;

    public ShipwrightScreen(List<String> blueprints, long shipId,
                            int currentHp, int effectiveMaxHp, int mastCount, int[] upgradeLevels) {
        super(Text.literal("Shipwright"));
        this.blueprints     = blueprints;
        this.shipId         = shipId;
        this.currentHp      = currentHp;
        this.effectiveMaxHp = effectiveMaxHp;
        this.mastCount      = mastCount;
        this.upgradeLevels  = upgradeLevels;
    }

    private int panelX() { return (this.width  - W) / 2; }
    private int panelY() { return (this.height - H) / 2; }

    @Override
    protected void init() {
        int px = panelX(), py = panelY();

        // Tab buttons
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Commission"),
                btn -> { tab = 0; page = 0; rebuildContent(); })
            .position(px + 4, py + 4).size(100, 18).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Upgrade Ship"),
                btn -> { tab = 1; rebuildContent(); })
            .position(px + 110, py + 4).size(100, 18).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("[~] Scuttle Ship"),
                btn -> scuttle())
            .position(px + 4, py + H - 22).size(110, 18).build());
        this.addDrawableChild(ButtonWidget.builder(Text.literal("Close"),
                btn -> this.close())
            .position(px + W - 54, py + H - 22).size(50, 18).build());

        rebuildContent();
    }

    private void rebuildContent() {
        // Remove only content buttons (keep first 3: 2 tabs + close)
        var allChildren = new ArrayList<>(this.children());
        if (allChildren.size() > 3) {
            allChildren.subList(3, allChildren.size()).forEach(this::remove);
        }

        if (tab == 0) buildCommissionTab();
        else          buildUpgradeTab();
    }

    private void buildCommissionTab() {
        int px = panelX(), py = panelY();
        int contentY = py + 30;
        int btnX = px + (W - BW) / 2;

        int start = page * PER_PAGE;
        int end   = Math.min(start + PER_PAGE, blueprints.size());
        for (int i = start; i < end; i++) {
            String name = blueprints.get(i);
            int y = contentY + (i - start) * (BH + GAP);
            this.addDrawableChild(ButtonWidget.builder(Text.literal(name), btn -> commission(name))
                .position(btnX, y).size(BW, BH).build());
        }

        int navY = py + H - 44;
        if (page > 0) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("< Prev"),
                    btn -> { page--; rebuildContent(); })
                .position(px + 4, navY).size(55, 18).build());
        }
        if (end < blueprints.size()) {
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Next >"),
                    btn -> { page++; rebuildContent(); })
                .position(px + 64, navY).size(55, 18).build());
        }
    }

    private void buildUpgradeTab() {
        if (shipId == -1L) return; // no ship — nothing to click

        int px = panelX(), py = panelY();
        int contentY = py + 50; // leave room for HP bar
        List<ShipUpgrades.Def> defs = ShipUpgrades.ALL;

        for (int i = 0; i < defs.size(); i++) {
            ShipUpgrades.Def def = defs.get(i);
            int y = contentY + i * 30;
            boolean maxed = upgradeLevels[i] >= def.maxLevel();
            String btnLabel = maxed ? "MAX" : "Upgrade";
            this.addDrawableChild(ButtonWidget.builder(Text.literal(btnLabel),
                    btn -> sendUpgrade(def.key()))
                .position(px + W - 80, y).size(70, 18)
                .build());
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        this.renderBackground(ctx);
        int px = panelX(), py = panelY();

        // Panel background
        ctx.fill(px, py, px + W, py + H, 0xC0101010);
        ctx.fill(px, py,         px + W, py + 1,     0xFF886644);
        ctx.fill(px, py + H - 1, px + W, py + H,     0xFF886644);

        // Title
        ctx.drawCenteredTextWithShadow(textRenderer, "§6* §lShipwright",
            px + W / 2, py - 12, 0xFFFFFF);

        if (tab == 0) renderCommission(ctx, px, py);
        else          renderUpgrades(ctx, px, py);

        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderCommission(DrawContext ctx, int px, int py) {
        if (blueprints.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                "§7No blueprints saved yet.", px + W / 2, py + 70, 0xAAAAAA);
            ctx.drawCenteredTextWithShadow(textRenderer,
                "§7Save a ship with a Blueprint item first.", px + W / 2, py + 82, 0xAAAAAA);
        } else {
            ctx.drawTextWithShadow(textRenderer,
                "§7Cost: §e3x Gold Ingot §7per ship", px + 8, py + H - 44, 0xFFFFFF);
        }
        int totalPages = Math.max(1, (int) Math.ceil((double) blueprints.size() / PER_PAGE));
        if (totalPages > 1) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                "§7Page " + (page + 1) + "/" + totalPages,
                px + W / 2, py + H - 46, 0xAAAAAA);
        }
    }

    private void renderUpgrades(DrawContext ctx, int px, int py) {
        if (shipId == -1L) {
            ctx.drawCenteredTextWithShadow(textRenderer,
                "§cNo player ship within 150 blocks.", px + W / 2, py + 70, 0xFF5555);
            ctx.drawCenteredTextWithShadow(textRenderer,
                "§7Sail your ship close to the shipwright.", px + W / 2, py + 82, 0xAAAAAA);
            return;
        }

        int contentY = py + 50;

        // HP bar
        int barW = W - 16;
        int barX = px + 8;
        int barY = py + 32;
        int filled = effectiveMaxHp > 0 ? (int)((float) currentHp / effectiveMaxHp * barW) : 0;
        ctx.fill(barX, barY, barX + barW, barY + 8, 0xFF222222);
        int barColor = currentHp > effectiveMaxHp * 0.6 ? 0xFF44AA44
                     : currentHp > effectiveMaxHp * 0.25 ? 0xFFAAAA00 : 0xFFAA2222;
        ctx.fill(barX, barY, barX + filled, barY + 8, barColor);
        ctx.drawCenteredTextWithShadow(textRenderer,
            "§fHP: " + currentHp + "/" + effectiveMaxHp + "  §7Speed: " + mastCount + " mast(s)",
            px + W / 2, barY + 10, 0xFFFFFF);

        // Upgrade rows
        List<ShipUpgrades.Def> defs = ShipUpgrades.ALL;
        for (int i = 0; i < defs.size(); i++) {
            ShipUpgrades.Def def = defs.get(i);
            int y = contentY + i * 30;
            boolean maxed = upgradeLevels[i] >= def.maxLevel();
            String levelStr = "§7Lv." + upgradeLevels[i] + "/" + def.maxLevel();
            ctx.drawTextWithShadow(textRenderer,
                "§e" + def.displayName() + " " + levelStr, px + 8, y + 2, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer,
                "§8Cost: " + ShipUpgrades.costString(def) + "  §7" + def.description(),
                px + 8, y + 13, 0xFFFFFF);
            if (maxed) {
                ctx.drawTextWithShadow(textRenderer, "§a+ MAX", px + W - 78, y + 5, 0xFFFFFF);
            }
        }
    }

    private void scuttle() {
        ClientPlayNetworking.send(ModNetworking.SHIPWRIGHT_SCUTTLE, PacketByteBufs.empty());
        this.close();
    }

    private void commission(String name) {
        var buf = PacketByteBufs.create();
        buf.writeString(name, 64);
        ClientPlayNetworking.send(ModNetworking.SHIPWRIGHT_SPAWN, buf);
        this.close();
    }

    private void sendUpgrade(String key) {
        var buf = PacketByteBufs.create();
        buf.writeLong(shipId);
        buf.writeString(key, 32);
        ClientPlayNetworking.send(ModNetworking.SHIPWRIGHT_UPGRADE, buf);
        this.close();
    }

    @Override
    public boolean shouldPause() { return false; }
}
