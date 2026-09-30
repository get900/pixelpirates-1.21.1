package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.network.ModNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

@Environment(EnvType.CLIENT)
public class BlueprintSelectScreen extends Screen {

    private static final int PANEL_W  = 220;
    private static final int BUTTON_W = 196;
    private static final int BUTTON_H = 20;
    private static final int PER_PAGE = 7;
    private static final int GAP      = 4;

    private final List<String> blueprints;
    private int page = 0;

    public BlueprintSelectScreen(List<String> blueprints) {
        super(Text.literal("Ship Blueprints"));
        this.blueprints = blueprints;
    }

    @Override
    protected void init() {
        int panelH  = 28 + PER_PAGE * (BUTTON_H + GAP) + 28;
        int panelX  = (this.width  - PANEL_W) / 2;
        int panelY  = (this.height - panelH)  / 2;
        int buttonX = panelX + (PANEL_W - BUTTON_W) / 2;

        int start = page * PER_PAGE;
        int end   = Math.min(start + PER_PAGE, blueprints.size());

        for (int i = start; i < end; i++) {
            String name = blueprints.get(i);
            int y = panelY + 28 + (i - start) * (BUTTON_H + GAP);
            this.addDrawableChild(
                ButtonWidget.builder(Text.literal(name), btn -> select(name))
                    .position(buttonX, y)
                    .size(BUTTON_W, BUTTON_H)
                    .build()
            );
        }

        int navY = panelY + panelH - 24;
        if (page > 0) {
            this.addDrawableChild(
                ButtonWidget.builder(Text.literal("< Prev"), btn -> changePage(-1))
                    .position(panelX, navY).size(60, 20).build()
            );
        }
        if (end < blueprints.size()) {
            this.addDrawableChild(
                ButtonWidget.builder(Text.literal("Next >"), btn -> changePage(1))
                    .position(panelX + 70, navY).size(60, 20).build()
            );
        }
        this.addDrawableChild(
            ButtonWidget.builder(Text.literal("Cancel"), btn -> this.close())
                .position(panelX + PANEL_W - 64, navY).size(60, 20).build()
        );
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int panelH = 28 + PER_PAGE * (BUTTON_H + GAP) + 28;
        int panelX = (this.width  - PANEL_W) / 2;
        int panelY = (this.height - panelH)  / 2;

        context.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xC0101010);
        context.fill(panelX, panelY,     panelX + PANEL_W, panelY + 1,          0xFF886644);
        context.fill(panelX, panelY + panelH - 1, panelX + PANEL_W, panelY + panelH, 0xFF886644);

        context.drawCenteredTextWithShadow(
            this.textRenderer, Text.literal("§6* §lShip Blueprints"), this.width / 2, panelY + 9, 0xFFFFFF);

        int totalPages = (int) Math.ceil((double) blueprints.size() / PER_PAGE);
        if (totalPages > 1) {
            context.drawCenteredTextWithShadow(
                this.textRenderer, Text.literal("§7Page " + (page + 1) + "/" + totalPages),
                this.width / 2, panelY + panelH - 38, 0xAAAAAA);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private void changePage(int delta) {
        page += delta;
        this.init(this.client, this.width, this.height);
    }

    private void select(String name) {
        var buf = PacketByteBufs.create();
        buf.writeString(name, 64);
        ClientPlayNetworking.send(ModNetworking.SELECT_BLUEPRINT, buf);
        this.close();
    }

    @Override
    public boolean shouldPause() { return false; }
}
