package net.get900.pixelpirates.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.homestead.parrot.ParrotCollection;
import net.get900.pixelpirates.homestead.parrot.ParrotTypes;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * THE PARROT ROOST screen (phase 4): all 15 parrot types in a 5x3 grid - the ones you've unlocked as the living bird (it
 * watches your cursor), the rest as a black silhouette. Hover for the name + rarity; click an unlocked one to call it.
 */
@Environment(EnvType.CLIENT)
public class ParrotRoostScreen extends Screen {
    /** textures/gui/roost.png (tools/gen_gui_textures.py roost()): the 290x214 panel at 0,0; slot plates 50x56 at
     *  u=300, v=0 normal / 58 hover / 116 locked, the perch bar 45 px down each plate. */
    private static final net.minecraft.util.Identifier TEX = new net.minecraft.util.Identifier("pixelpirates", "textures/gui/roost.png");
    private static final int W = 290, H = 214, TW = 512, TH = 256, SLOT_U = 300, SLOT_W = 50, SLOT_H = 56;
    private static final int COLS = 5, CW = 52, CH = 58;
    private final Set<String> owned;
    private final List<ParrotEntity> birds = new ArrayList<>();

    public ParrotRoostScreen(Set<String> owned) {
        super(Text.literal("Parrot Roost"));
        this.owned = owned;
    }

    @Override
    protected void init() {
        birds.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        for (ParrotTypes.PType t : ParrotTypes.ALL) {
            ParrotEntity p = new ParrotEntity(EntityType.PARROT, mc.world);
            ParrotTypes.apply(p, t);
            birds.add(p);
        }
    }

    private int px() { return (width - W) / 2; }

    private int py() { return (height - H) / 2; }

    private int left() { return px() + (W - COLS * CW) / 2; }

    private int top() { return py() + 30; }

    private int at(double mx, double my) {
        int c = (int) Math.floor((mx - left()) / CW), r = (int) Math.floor((my - top()) / CH);
        return c < 0 || c >= COLS || r < 0 || r >= 3 ? -1 : r * COLS + c;
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        int x0 = left(), y0 = top();
        ctx.drawTexture(TEX, px(), py(), 0, 0, W, H, TW, TH);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Parrot Roost - " + owned.size() + " / " + ParrotTypes.ALL.size() + " found")
                .formatted(Formatting.GOLD), width / 2, py() + 12, 0xFFFFFF);
        int hover = at(mx, my);
        for (int i = 0; i < ParrotTypes.ALL.size(); i++) {
            ParrotTypes.PType t = ParrotTypes.ALL.get(i);
            int cx = x0 + (i % COLS) * CW, cy = y0 + (i / COLS) * CH;
            boolean have = owned.contains(t.id());
            ctx.drawTexture(TEX, cx + 1, cy + 1, SLOT_U, !have ? 116 : i == hover ? 58 : 0, SLOT_W, SLOT_H, TW, TH);
            ctx.fill(cx + 4, cy + SLOT_H - 4, cx + CW - 4, cy + SLOT_H - 2, 0xFF000000 | t.tier().colour.getColorValue());   // rarity stripe
            if (!have) RenderSystem.setShaderColor(0.06f, 0.06f, 0.08f, 1f);                                     // the silhouette
            InventoryScreen.drawEntity(ctx, cx + CW / 2, cy + 46, 30, cx + CW / 2f - mx, cy + 22 - my, birds.get(i));   // on the perch bar
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }
        super.render(ctx, mx, my, delta);
        if (hover >= 0) {
            ParrotTypes.PType t = ParrotTypes.ALL.get(hover);
            boolean have = owned.contains(t.id());
            ctx.drawTooltip(textRenderer, List.of(
                    have ? Text.literal(t.name()).formatted(t.tier().colour, Formatting.BOLD) : Text.literal("???").formatted(Formatting.DARK_GRAY),
                    Text.literal(t.tier().label).formatted(t.tier().colour),
                    Text.literal(have ? "Click to call it to you" : "Not found yet").formatted(Formatting.GRAY)), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int i = at(mx, my);
        if (i >= 0 && owned.contains(ParrotTypes.ALL.get(i).id())) {
            PacketByteBuf buf = PacketByteBufs.create();
            buf.writeString(ParrotTypes.ALL.get(i).id());
            ClientPlayNetworking.send(ParrotCollection.ROOST_RECALL, buf);
            close();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean shouldPause() { return false; }
}
