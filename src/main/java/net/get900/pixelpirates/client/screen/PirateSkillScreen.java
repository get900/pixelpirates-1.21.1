package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.client.PirateLevelingClient;
import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.world.PirateLevelManager;
import net.get900.pixelpirates.world.PirateLevelingSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Five-tab pirate skill tree screen.
 * All data is read from PirateLevelingClient which is kept sync'd by the server.
 */
@Environment(EnvType.CLIENT)
public class PirateSkillScreen extends Screen {

    // ── Layout constants ──────────────────────────────────────────────────────
    private static final int PANEL_W = 324;
    private static final int PANEL_H = 242;

    private static final int HDR_H   = 20;  // header bar
    private static final int XP_H    = 14;  // xp bar row
    private static final int PTS_H   = 12;  // skill points row
    private static final int TAB_H   = 16;  // tab row
    private static final int CARD_H  = 36;  // each skill card

    // Within panel: y positions
    private static final int Y_HDR  = 0;
    private static final int Y_XP   = HDR_H;
    private static final int Y_PTS  = Y_XP  + XP_H;
    private static final int Y_TABS = Y_PTS + PTS_H;
    private static final int Y_CARDS= Y_TABS + TAB_H; // = 62

    // Colors (ARGB)
    private static final int C_PANEL_BG   = 0xF0120E1E;
    private static final int C_HDR_BG     = 0xFF1A1430;
    private static final int C_CARD_BG    = 0xFF1E1840;
    private static final int C_CARD_BG_H  = 0xFF26205A; // hover/even alt
    private static final int C_TAB_INACT  = 0xFF2A2244;
    private static final int C_BTN_ON     = 0xFF2E6B1E;
    private static final int C_BTN_OFF    = 0xFF2A2244;
    private static final int C_BTN_TXT    = 0xFFFFFFFF;
    private static final int C_DOT_EMPTY  = 0xFF444444;
    private static final int C_BORDER     = 0xFF3A3060;

    // Tab colors (packed RGB from SkillTree enum, forced opaque)
    private static final int[] TAB_COLORS = {
        0xFF993333, // BRAWLER  — red
        0xFF333333, // CANNONEER — dark
        0xFF3355BB, // NAVIGATOR — blue
        0xFFAA8800, // MERCHANT  — gold
        0xFF228833, // SURVIVOR  — green
    };

    private int panelX, panelY;
    private int selectedTab = 0;

    public PirateSkillScreen() {
        super(Text.literal("Pirate Journal"));
    }

    @Override
    protected void init() {
        panelX = (width  - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        drawPanel(ctx, mouseX, mouseY);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void drawPanel(DrawContext ctx, int mx, int my) {
        int px = panelX, py = panelY;

        // Outer border
        ctx.fill(px - 1, py - 1, px + PANEL_W + 1, py + PANEL_H + 1, C_BORDER);
        // Panel background
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, C_PANEL_BG);

        drawHeader(ctx, px, py);
        drawXpRow(ctx, px, py);
        drawSkillPointsRow(ctx, px, py);
        drawTabs(ctx, px, py, mx, my);
        drawSkillCards(ctx, px, py, mx, my);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void drawHeader(DrawContext ctx, int px, int py) {
        ctx.fill(px, py + Y_HDR, px + PANEL_W, py + Y_HDR + HDR_H, C_HDR_BG);

        int level = PirateLevelingClient.level;
        String rank  = PirateLevelingSystem.rankName(level);
        String color = PirateLevelingSystem.rankColor(level);

        ctx.drawCenteredTextWithShadow(textRenderer,
            "§6* §fPirate Journal", px + PANEL_W / 2, py + Y_HDR + 6, 0xFFFFFF);

        String lvlStr = color + rank + " §7[§fLv " + level + "§7]";
        ctx.drawTextWithShadow(textRenderer, lvlStr,
            px + PANEL_W - textRenderer.getWidth(lvlStr) - 6, py + Y_HDR + 6, 0xFFFFFF);
    }

    // ── XP bar ────────────────────────────────────────────────────────────────

    private void drawXpRow(DrawContext ctx, int px, int py) {
        int rowY = py + Y_XP;
        ctx.fill(px, rowY, px + PANEL_W, rowY + XP_H, 0xFF0D0B1A);

        int barX  = px + 60;
        int barY  = rowY + 3;
        int barW  = PANEL_W - 120;
        int barH  = 7;

        ctx.fill(barX, barY, barX + barW, barY + barH, 0xFF111111);
        float frac = PirateLevelingClient.xpFraction();
        int fill = (int)(barW * frac);
        if (fill > 0) ctx.fill(barX, barY, barX + fill, barY + barH, 0xFFFFCC00);
        // Bar border
        ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY,         0xFF555555);
        ctx.fill(barX - 1, barY + barH, barX + barW + 1, barY + barH + 1, 0xFF555555);

        int level  = PirateLevelingClient.level;
        int xp     = PirateLevelingClient.xp;
        int needed = PirateLevelingSystem.xpToNextLevel(level);
        String xpStr = (level >= PirateLevelingSystem.MAX_LEVEL)
            ? "§6MAX LEVEL"
            : "§7" + xp + " §8/ §7" + needed + " XP";
        ctx.drawCenteredTextWithShadow(textRenderer, xpStr, px + PANEL_W / 2, rowY + 3, 0xFFFFFF);
    }

    // ── Skill points row ──────────────────────────────────────────────────────

    private void drawSkillPointsRow(DrawContext ctx, int px, int py) {
        int rowY = py + Y_PTS;
        ctx.fill(px, rowY, px + PANEL_W, rowY + PTS_H, 0xFF0F0D1E);
        int pts = PirateLevelingClient.skillPoints;
        String msg = pts > 0
            ? "§a" + pts + " Skill Point" + (pts == 1 ? "" : "s") + " Available §7— Click §a+§7 to Spend"
            : "§7No unspent skill points";
        ctx.drawCenteredTextWithShadow(textRenderer, msg, px + PANEL_W / 2, rowY + 2, 0xFFFFFF);
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private static final int TAB_W = 64; // 5 × 64 = 320, leaving 4px padded (2 each side)

    private void drawTabs(DrawContext ctx, int px, int py, int mx, int my) {
        int rowY = py + Y_TABS;
        PirateLevelingSystem.SkillTree[] trees = PirateLevelingSystem.SkillTree.values();

        for (int t = 0; t < trees.length; t++) {
            int tx = px + 2 + t * TAB_W;
            boolean active = (t == selectedTab);
            boolean hover  = !active && mx >= tx && mx < tx + TAB_W - 1
                && my >= rowY && my < rowY + TAB_H;

            int bg = active ? TAB_COLORS[t] : (hover ? 0xFF302860 : C_TAB_INACT);
            ctx.fill(tx, rowY, tx + TAB_W - 1, rowY + TAB_H, bg);

            // Bottom highlight on active tab
            if (active) ctx.fill(tx, rowY + TAB_H - 2, tx + TAB_W - 1, rowY + TAB_H, 0x80FFFFFF);

            ctx.drawCenteredTextWithShadow(textRenderer,
                trees[t].name, tx + TAB_W / 2, rowY + 4, active ? 0xFFFFFF : 0xAAAAAA);
        }
    }

    // ── Skill cards ───────────────────────────────────────────────────────────

    private static final int BTN_W = 22;
    private static final int BTN_H = 14;

    private void drawSkillCards(DrawContext ctx, int px, int py, int mx, int my) {
        PirateLevelingSystem.SkillTree tree =
            PirateLevelingSystem.SkillTree.values()[selectedTab];
        List<PirateLevelingSystem.SkillDef> skills = PirateLevelingSystem.skillsForTree(tree);
        int treeColor = TAB_COLORS[selectedTab] | 0xFF000000;

        for (int i = 0; i < skills.size(); i++) {
            PirateLevelingSystem.SkillDef def = skills.get(i);
            int globalIdx  = PirateLevelManager.getSkillIndex(def.key());
            int skillLvl   = (globalIdx >= 0 && globalIdx < PirateLevelingClient.skillLevels.length)
                ? PirateLevelingClient.skillLevels[globalIdx] : 0;
            boolean maxed  = (skillLvl >= def.maxLevel());
            boolean canBuy = (PirateLevelingClient.skillPoints > 0 && !maxed);

            int cardTop = py + Y_CARDS + i * CARD_H;
            int cardBot = cardTop + CARD_H - 2;

            // Card background (alternating shade)
            int cardBg = (i % 2 == 0) ? C_CARD_BG : C_CARD_BG_H;
            ctx.fill(px + 2, cardTop, px + PANEL_W - 2, cardBot, cardBg);

            // Skill name
            ctx.drawTextWithShadow(textRenderer, def.displayName(),
                px + 8, cardTop + 5, treeColor);

            // Level indicator "Lv X / Y"
            String lvlStr = skillLvl == 0 ? "§7Unlearned"
                : (maxed ? "§6MAX" : "§b" + skillLvl + " §7/ §8" + def.maxLevel());
            int lvlX = px + PANEL_W - 2 - BTN_W - 4 - textRenderer.getWidth(lvlStr);
            ctx.drawTextWithShadow(textRenderer, lvlStr, lvlX, cardTop + 5, 0xFFFFFF);

            // Level dots (5 small squares)
            for (int d = 0; d < def.maxLevel(); d++) {
                int dotX = px + 8 + d * 9;
                int dotY = cardTop + 18;
                int dotColor = (d < skillLvl) ? treeColor : C_DOT_EMPTY;
                ctx.fill(dotX, dotY, dotX + 6, dotY + 4, dotColor);
            }

            // Description text (small, after dots)
            String desc = def.descAt().apply(skillLvl);
            String truncated = textRenderer.trimToWidth(desc, PANEL_W - 8 - 5 * 9 - 4 - BTN_W - 8);
            ctx.drawTextWithShadow(textRenderer, "§7" + truncated,
                px + 8 + 5 * 9 + 4, cardTop + 18, 0x888888);

            // "+1" button
            int bx = px + PANEL_W - 2 - BTN_W;
            int by = cardTop + 11;
            boolean btnHover = mx >= bx && mx < bx + BTN_W && my >= by && my < by + BTN_H;
            int btnBg = canBuy
                ? (btnHover ? 0xFF3E8B2E : C_BTN_ON)
                : C_BTN_OFF;
            ctx.fill(bx, by, bx + BTN_W, by + BTN_H, btnBg);
            ctx.drawCenteredTextWithShadow(textRenderer,
                canBuy ? "§a+" : "§8+", bx + BTN_W / 2, by + 3, C_BTN_TXT);
        }

        // Tree subtitle below the last card
        int subY = py + Y_CARDS + skills.size() * CARD_H + 2;
        if (subY < py + PANEL_H - 8) {
            PirateLevelingSystem.SkillTree t = PirateLevelingSystem.SkillTree.values()[selectedTab];
            ctx.drawCenteredTextWithShadow(textRenderer,
                "§8" + t.subtitle, px + PANEL_W / 2, subY, 0x666666);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        int px = panelX, py = panelY;

        // Tab click
        int tabRowY = py + Y_TABS;
        if (my >= tabRowY && my < tabRowY + TAB_H) {
            for (int t = 0; t < 5; t++) {
                int tx = px + 2 + t * TAB_W;
                if (mx >= tx && mx < tx + TAB_W - 1) {
                    selectedTab = t;
                    return true;
                }
            }
        }

        // Skill card "+" button click
        if (PirateLevelingClient.skillPoints > 0) {
            PirateLevelingSystem.SkillTree tree = PirateLevelingSystem.SkillTree.values()[selectedTab];
            List<PirateLevelingSystem.SkillDef> skills = PirateLevelingSystem.skillsForTree(tree);

            for (int i = 0; i < skills.size(); i++) {
                int cardTop = py + Y_CARDS + i * CARD_H;
                int bx = px + PANEL_W - 2 - BTN_W;
                int by = cardTop + 11;
                if (mx >= bx && mx < bx + BTN_W && my >= by && my < by + BTN_H) {
                    PirateLevelingSystem.SkillDef def = skills.get(i);
                    int globalIdx = PirateLevelManager.getSkillIndex(def.key());
                    int curLvl = (globalIdx >= 0 && globalIdx < PirateLevelingClient.skillLevels.length)
                        ? PirateLevelingClient.skillLevels[globalIdx] : 0;
                    if (curLvl < def.maxLevel()) {
                        var buf = PacketByteBufs.create();
                        buf.writeString(def.key(), 64);
                        ClientPlayNetworking.send(ModNetworking.C2S_SKILL_SPEND, buf);
                    }
                    return true;
                }
            }
        }

        return super.mouseClicked(mx, my, btn);
    }
}
