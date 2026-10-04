package net.get900.pixelpirates.client.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.client.PirateLevelingClient;
import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.world.PirateLevelManager;
import net.get900.pixelpirates.world.PirateLevelingSystem;
import net.get900.pixelpirates.world.PirateLevelingSystem.SkillDef;
import net.get900.pixelpirates.world.PirateLevelingSystem.SkillTree;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * THE PIRATE JOURNAL (redesigned 2026-10-01): an open leather journal. Ribbon bookmarks pick one of the five trees; the
 * LEFT page draws the tree growing upward - tier I (two skills), tier II (two), tier III (one) and the capstone - as
 * medallions joined by rope; the RIGHT page shows the pirate (level, rank, XP, points) and the selected skill: what it
 * does now, what the next rank adds, and what is still needed (tree points, a boss for ranks IV/V and capstones).
 * Art: tools/gen_gui_textures.py -> textures/gui/journal.png; the J_* rectangles below match its atlas.
 * Server rules are the same function (PirateLevelingSystem.blockedReason), so the page never promises what the server refuses.
 */
@Environment(EnvType.CLIENT)
public class PirateSkillScreen extends Screen {
    private static final Identifier TEX = new Identifier(PixelPirates.MOD_ID, "textures/gui/journal.png");
    private static final int TW = 512, TH = 256;
    private static final int W = 320, H = 210;
    // atlas rectangles
    private static final int J_NODE_V = 212, J_NODE = 26, J_CAP = 34, J_CAP_U = 104, J_RING_U = 206;
    private static final int J_BTN_U = 330, J_BTN_W = 64, J_BTN_H = 18, J_SBTN_V = 56, J_SBTN_W = 84, J_SBTN_H = 14;
    private static final int J_TAB_U = 330, J_TAB_V = 100, J_TAB_W = 28;
    private static final int J_XP_U = 330, J_XP_V = 130, J_XPF_V = 140, J_COIN_V = 150;

    private static final int INK = 0xFF3B2A1A, INK_DIM = 0xFF7A6040, RED = 0xFF9A1C1C, GOLD = 0xFF8A6410;

    // node centres on the left page (panel coordinates), in skillsForTree order: I, I, II, II, III, capstone
    private static final int[][] NODE = {{48, 160}, {118, 160}, {48, 120}, {118, 120}, {83, 82}, {83, 44}};

    private int px, py;
    private int tab = 0;
    private int selected = 0;           // index within the tree

    public PirateSkillScreen() {
        super(Text.literal("Pirate Journal"));
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2 + 8;
    }

    @Override
    public boolean shouldPause() { return false; }

    private SkillTree tree() { return SkillTree.values()[tab]; }

    private List<SkillDef> skills() { return PirateLevelingSystem.skillsForTree(tree()); }

    private static ItemStack icon(String id) {
        Item i = Registries.ITEM.get(new Identifier(id));
        return new ItemStack(i);
    }

    private static int rankOf(SkillDef d) { return PirateLevelingClient.level(d.key()); }

    private String blocked(SkillDef d) {
        return PirateLevelingSystem.blockedReason(d, rankOf(d), PirateLevelingClient.pointsInTree(d.tree()),
                PirateLevelingClient.bossesBeaten, PirateLevelingClient.skillPoints);
    }

    /** Locked = its tier (or boss gate) is not open yet - different from "open, but no points". */
    private boolean locked(SkillDef d) {
        return PirateLevelingClient.pointsInTree(d.tree()) < PirateLevelingSystem.TIER_POINTS[d.tier()]
                || d.bossGate() > PirateLevelingClient.bossesBeaten && rankOf(d) == 0;
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        // ribbons behind the book (inactive) and over it (active)
        drawTabs(ctx, mx, my, false);
        ctx.drawTexture(TEX, px, py, 0, 0, W, H, TW, TH);
        drawTabs(ctx, mx, my, true);
        drawTree(ctx, mx, my);
        drawPage(ctx, mx, my);
        super.render(ctx, mx, my, delta);
        // tooltip for hovered nodes
        int h = hoveredNode(mx, my);
        if (h >= 0) {
            SkillDef d = skills().get(h);
            ctx.drawTooltip(textRenderer, List.of(Text.literal(d.displayName()).formatted(Formatting.GOLD),
                    Text.literal(d.tier() == 4 ? "Capstone" : "Rank " + rankOf(d) + "/" + d.maxLevel()).formatted(Formatting.GRAY)), mx, my);
        }
    }

    private int tabX(int t) { return px + 14 + t * 30; }

    private void drawTabs(DrawContext ctx, int mx, int my, boolean activeLayer) {
        for (int t = 0; t < SkillTree.values().length; t++) {
            boolean active = t == tab;
            if (active != activeLayer) continue;
            SkillTree tr = SkillTree.values()[t];
            int x = tabX(t), h = active ? 26 : 20, y = py - h + (active ? 8 : 4);
            float r = ((tr.color >> 16) & 255) / 255f, g = ((tr.color >> 8) & 255) / 255f, b = (tr.color & 255) / 255f;
            boolean hover = mx >= x && mx < x + J_TAB_W && my >= y && my < y + h;
            float k = active ? 1f : hover ? 0.85f : 0.65f;
            RenderSystem.setShaderColor(r * k, g * k, b * k, 1f);
            ctx.drawTexture(TEX, x, y, J_TAB_U + (active ? 30 : 0), J_TAB_V, J_TAB_W, h, TW, TH);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            ctx.drawItem(icon(tr.icon), x + 6, y + 2);
        }
    }

    private void drawTree(DrawContext ctx, int mx, int my) {
        SkillTree tr = tree();
        List<SkillDef> sk = skills();
        int pts = PirateLevelingClient.pointsInTree(tr);
        // title
        text(ctx, Text.literal(tr.name).formatted(Formatting.BOLD), px + 83, py + 13, INK, true);
        // tier labels in the margin
        String[] tiers = {"", "I", "II", "III", "*"};
        int[] ys = {0, 160, 120, 82, 44};
        for (int t = 1; t <= 4; t++) {
            boolean open = pts >= PirateLevelingSystem.TIER_POINTS[t];
            int col = open ? GOLD : INK_DIM;
            text(ctx, Text.literal(tiers[t]).formatted(Formatting.BOLD), px + 21, py + ys[t] - 6, col, true);
            if (PirateLevelingSystem.TIER_POINTS[t] > 0)
                small(ctx, PirateLevelingSystem.TIER_POINTS[t] + " pts", px + 22, py + ys[t] + 4, col);
        }
        // ropes: I -> II (straight up), II -> III (a yoke), III -> capstone
        boolean open2 = pts >= PirateLevelingSystem.TIER_POINTS[2], open3 = pts >= PirateLevelingSystem.TIER_POINTS[3],
                open4 = pts >= PirateLevelingSystem.TIER_POINTS[4];
        rope(ctx, 48, 133, 48, 147, open2); rope(ctx, 118, 133, 118, 147, open2);
        rope(ctx, 48, 100, 48, 107, open3); rope(ctx, 118, 100, 118, 107, open3);
        rope(ctx, 48, 100, 118, 101, open3); rope(ctx, 83, 95, 83, 100, open3);
        rope(ctx, 83, 61, 83, 69, open4);
        // nodes
        for (int i = 0; i < sk.size() && i < NODE.length; i++) drawNode(ctx, sk.get(i), i, px + NODE[i][0], py + NODE[i][1]);
    }

    private void rope(DrawContext ctx, int x0, int y0, int x1, int y1, boolean open) {
        int c = open ? 0xFFC8962A : 0xFF6A4A26, c2 = open ? 0xFF7A5A14 : 0xFF3A2414;
        int ax = px + Math.min(x0, x1), ay = py + Math.min(y0, y1), bx = px + Math.max(x0, x1), by = py + Math.max(y0, y1);
        if (bx - ax < 2) { ctx.fill(ax - 1, ay, ax + 1, by, c); ctx.fill(ax + 1, ay, ax + 2, by, c2); }
        else { ctx.fill(ax, ay - 1, bx, ay + 1, c); ctx.fill(ax, ay + 1, bx, ay + 2, c2); }
    }

    private void drawNode(DrawContext ctx, SkillDef d, int idx, int cx, int cy) {
        boolean cap = d.tier() == 4;
        int rank = rankOf(d);
        boolean lock = locked(d), can = blocked(d) == null;
        int size = cap ? J_CAP : J_NODE;
        int u;
        if (cap) u = J_CAP_U + (rank > 0 ? 2 : lock ? 0 : 1) * J_CAP;
        else u = (rank >= d.maxLevel() ? 3 : rank > 0 ? 2 : lock ? 0 : 1) * J_NODE;
        if (idx == selected) ctx.drawTexture(TEX, cx - 15, cy - 15, J_RING_U, J_NODE_V, 30, 30, TW, TH);
        ctx.drawTexture(TEX, cx - size / 2, cy - size / 2, u, J_NODE_V, size, size, TW, TH);
        ctx.drawItem(icon(d.icon()), cx - 8, cy - 8);
        if (lock) ctx.fill(cx - 8, cy - 8, cx + 8, cy + 8, 0xA0201818);
        else if (can && (System.currentTimeMillis() / 400) % 2 == 0)       // gently blinks when you can learn it
            ctx.fill(cx - 9, cy + (cap ? 12 : 9), cx + 9, cy + (cap ? 13 : 10), 0xFFFFE08A);
        if (!cap) {                                                        // rank pips
            for (int r = 0; r < d.maxLevel(); r++) {
                int x = cx - 12 + r * 5, y = cy + 14;
                ctx.fill(x, y, x + 4, y + 3, 0xFF3A2414);
                ctx.fill(x + 1, y, x + 3, y + 2, r < rank ? 0xFFE0B040 : 0xFF6A4A30);
            }
        }
    }

    private void drawPage(DrawContext ctx, int mx, int my) {
        int x0 = px + 170, cx = px + 237;
        String rank = PirateLevelingSystem.rankName(PirateLevelingClient.level);
        text(ctx, Text.literal("Level " + PirateLevelingClient.level + "  ").append(Text.literal(rank).formatted(Formatting.BOLD)), cx, py + 13, INK, true);
        // XP bar
        ctx.drawTexture(TEX, cx - 52, py + 25, J_XP_U, J_XP_V, 104, 8, TW, TH);
        int fill = Math.round(PirateLevelingClient.xpFraction() * 100);
        if (fill > 0) ctx.drawTexture(TEX, cx - 50, py + 27, J_XP_U, J_XPF_V, fill, 4, TW, TH);
        small(ctx, PirateLevelingClient.level >= PirateLevelingSystem.MAX_LEVEL ? "max level"
                : PirateLevelingClient.xp + " / " + PirateLevelingClient.xpToNextLevel() + " xp", cx, py + 36, INK_DIM);
        // points
        ctx.drawTexture(TEX, cx - 34, py + 45, J_XP_U, J_COIN_V, 9, 9, TW, TH);
        int pts = PirateLevelingClient.skillPoints;
        ctx.drawText(textRenderer, Text.literal(pts + (pts == 1 ? " skill point" : " skill points")), cx - 22, py + 46, pts > 0 ? GOLD : INK_DIM, false);
        ctx.fill(x0 + 2, py + 58, px + 304, py + 59, 0x60704A20);

        // the selected skill
        List<SkillDef> sk = skills();
        SkillDef d = sk.get(Math.min(selected, sk.size() - 1));
        int rankNow = rankOf(d);
        ctx.drawItem(icon(d.icon()), x0 + 2, py + 64);
        ctx.drawText(textRenderer, Text.literal(d.displayName()).formatted(Formatting.BOLD), x0 + 22, py + 64, INK, false);
        String sub = d.tier() == 4 ? (rankNow > 0 ? "Capstone - learned" : "Capstone")
                : "Rank " + rankNow + " / " + d.maxLevel() + "   -   Tier " + PirateLevelingSystem.roman(d.tier());
        small(ctx, sub, x0 + 22, py + 74, INK_DIM, false);

        int y = py + 86;
        if (rankNow == 0) y = para(ctx, d.descAt().apply(0), x0 + 2, y, INK_DIM);
        if (rankNow > 0) {
            ctx.drawText(textRenderer, Text.literal("Now").formatted(Formatting.BOLD), x0 + 2, y, GOLD, false);
            y = para(ctx, d.descAt().apply(rankNow), x0 + 2, y + 10, INK);
        }
        if (rankNow < d.maxLevel()) {
            ctx.drawText(textRenderer, Text.literal(rankNow == 0 ? "Learn" : "Next").formatted(Formatting.BOLD), x0 + 2, y + 2, GOLD, false);
            y = para(ctx, d.descAt().apply(rankNow + 1), x0 + 2, y + 12, INK);
        }
        String why = blocked(d);
        if (why != null && !why.equals("Mastered")) {                  // keep the gate line clear of the button (py + 160)
            int lines = textRenderer.wrapLines(Text.literal(why), 132).size();
            para(ctx, why, x0 + 2, Math.min(y + 3, py + 158 - lines * 9), RED);
        }

        // buttons
        boolean can = why == null;
        int bx = cx - J_BTN_W / 2, by = py + 160;
        boolean hov = in(mx, my, bx, by, J_BTN_W, J_BTN_H);
        ctx.drawTexture(TEX, bx, by, J_BTN_U, (can ? (hov ? 1 : 0) : 2) * J_BTN_H, J_BTN_W, J_BTN_H, TW, TH);
        ctx.drawCenteredTextWithShadow(textRenderer, why != null && why.equals("Mastered") ? "Mastered" : rankNow == 0 ? "Learn" : "Improve",
                cx, by + 5, can ? 0xFFFFF0D0 : 0xFFB0A898);
        int rx = cx - J_SBTN_W / 2, ry = py + 184;
        boolean rh = in(mx, my, rx, ry, J_SBTN_W, J_SBTN_H);
        ctx.drawTexture(TEX, rx, ry, J_BTN_U, J_SBTN_V + (rh ? 1 : 0) * J_SBTN_H, J_SBTN_W, J_SBTN_H, TW, TH);
        small(ctx, "Retrain (" + PirateLevelManager.RESPEC_COST + " doubloons)", cx, ry + 4, 0xFFFFF0D0);
    }

    // ------------------------------------------------------------------ text helpers (ink on parchment: no shadow)
    private void text(DrawContext ctx, Text t, int x, int y, int col, boolean centred) {
        int w = textRenderer.getWidth(t);
        ctx.drawText(textRenderer, t, centred ? x - w / 2 : x, y, col, false);
    }

    private void small(DrawContext ctx, String s, int x, int y, int col) { small(ctx, s, x, y, col, true); }

    private void small(DrawContext ctx, String s, int x, int y, int col, boolean centred) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1f);
        int w = textRenderer.getWidth(s);
        ctx.drawText(textRenderer, s, centred ? -w / 2 : 0, 0, col, false);
        ctx.getMatrices().pop();
    }

    /** Word-wrapped paragraph in the right page's width; returns the y below it. */
    private int para(DrawContext ctx, String s, int x, int y, int col) {
        for (OrderedText line : textRenderer.wrapLines(Text.literal(s), 132)) {
            ctx.drawText(textRenderer, line, x, y, col, false);
            y += 9;
        }
        return y;
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int hoveredNode(double mx, double my) {
        List<SkillDef> sk = skills();
        for (int i = 0; i < sk.size() && i < NODE.length; i++) {
            int r = sk.get(i).tier() == 4 ? 17 : 13;
            if (Math.hypot(mx - (px + NODE[i][0]), my - (py + NODE[i][1])) <= r) return i;
        }
        return -1;
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        for (int t = 0; t < SkillTree.values().length; t++) {
            int x = tabX(t);
            if (in(mx, my, x, py - 22, J_TAB_W, 24)) {
                if (tab != t) { tab = t; selected = 0; click(); }
                return true;
            }
        }
        int n = hoveredNode(mx, my);
        if (n >= 0) { selected = n; click(); return true; }
        int cx = px + 237;
        SkillDef d = skills().get(Math.min(selected, skills().size() - 1));
        if (in(mx, my, cx - J_BTN_W / 2, py + 160, J_BTN_W, J_BTN_H) && blocked(d) == null) {
            var buf = PacketByteBufs.create();
            buf.writeString(d.key(), 64);
            ClientPlayNetworking.send(ModNetworking.C2S_SKILL_SPEND, buf);
            if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ENTITY_PLAYER_LEVELUP, 1.6f, 0.6f));
            return true;
        }
        if (in(mx, my, cx - J_SBTN_W / 2, py + 184, J_SBTN_W, J_SBTN_H)) {
            ClientPlayNetworking.send(ModNetworking.C2S_SKILL_RESPEC, PacketByteBufs.empty());
            click();
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key >= '1' && key < '1' + SkillTree.values().length) { tab = key - '1'; selected = 0; return true; }
        return super.keyPressed(key, scan, mods);
    }

    private void click() {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1f));
    }
}
