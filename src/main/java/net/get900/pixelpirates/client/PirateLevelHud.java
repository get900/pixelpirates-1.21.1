package net.get900.pixelpirates.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.world.PirateLevelingSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/**
 * Top-left HUD: rank name, level number, and XP progress bar.
 * Shown whenever the player has been sync'd (level >= 1).
 */
@Environment(EnvType.CLIENT)
public final class PirateLevelHud {

    private static final int BAR_W  = 90;
    private static final int BAR_H  = 5;
    private static final int MARGIN = 4;

    private static final int COL_BG_BAR  = 0xB0111111;
    private static final int COL_XP_FILL = 0xFFFFCC00;
    private static final int COL_RANK    = 0xFFFFFFFF;
    private static final int COL_XP_TEXT = 0xFF999999;
    private static final int COL_PTS     = 0xFFAAFF55;

    public static void render(DrawContext ctx, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (client.options.hudHidden) return;
        if (client.currentScreen != null) return;

        TextRenderer tr = client.textRenderer;
        int level       = PirateLevelingClient.level;
        int xp          = PirateLevelingClient.xp;
        int pts         = PirateLevelingClient.skillPoints;
        float fraction  = PirateLevelingClient.xpFraction();

        String rank     = PirateLevelingSystem.rankName(level);
        String rankColor= PirateLevelingSystem.rankColor(level);

        int x = MARGIN;
        int y = MARGIN;

        // Row 1: rank name + level
        String header = rankColor + rank + "  §7Lv §f" + level;
        ctx.drawTextWithShadow(tr, header, x, y, COL_RANK);

        // Row 2: XP progress bar
        int barY = y + 11;
        ctx.fill(x, barY, x + BAR_W, barY + BAR_H, COL_BG_BAR);
        int fill = (int)(BAR_W * fraction);
        if (fill > 0) ctx.fill(x, barY, x + fill, barY + BAR_H, COL_XP_FILL);

        // XP numbers to the right of bar
        int needed = PirateLevelingSystem.xpToNextLevel(level);
        String xpText = (level >= PirateLevelingSystem.MAX_LEVEL) ? "MAX" : xp + " / " + needed + " XP";
        ctx.drawTextWithShadow(tr, xpText, x + BAR_W + 4, barY - 1, COL_XP_TEXT);

        // Row 3: skill points available
        if (pts > 0) {
            ctx.drawTextWithShadow(tr,
                "§a" + pts + " skill point" + (pts == 1 ? "" : "s") + " §7(J)",
                x, barY + BAR_H + 3, COL_PTS);
        }
    }
}
