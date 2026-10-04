package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.client.PirateLevelingClient;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.world.ShipTiers;
import net.get900.pixelpirates.world.ShipUpgrades;
import net.get900.pixelpirates.world.livery.Liveries;
import net.get900.pixelpirates.world.livery.Livery;
import net.minecraft.registry.Registries;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * THE SHIPYARD LEDGER (redesigned 2026-10-01): a blueprint sheet pinned to a timber board. Two brass tab plates:
 * COMMISSION - the saved ship blueprints this captain's seas allow, as cards (2 x 3 per page) showing the sea a hull
 * belongs to; UPGRADE - the nearest ship's hull bar and its four refits with level pips and material costs; LIVERY
 * (2026-10-04) - repaint the nearest ship: cards with colour swatches, bought / owned / earned (world/livery).
 * Costs shown are what the server charges (ShipTiers.commissionCost per hull / ShipUpgrades, less the Haggler skill).
 * Art: tools/gen_gui_textures.py -> textures/gui/shipyard.png; the S_* rectangles below match its atlas.
 */
@Environment(EnvType.CLIENT)
public class ShipwrightScreen extends Screen {
    private static final Identifier TEX = new Identifier(PixelPirates.MOD_ID, "textures/gui/shipyard.png");
    private static final int TW = 512, TH = 256, W = 300, H = 206;
    private static final int S_CARD_U = 310, S_CARD_W = 132, S_CARD_H = 28;
    private static final int S_BTN_U = 310, S_BTN_V = 120, S_BTN_W = 64, S_BTN_H = 18;
    private static final int S_TAB_U = 380, S_TAB_V = 120, S_TAB_W = 96, S_TAB_H = 18;
    private static final int S_BAR_U = 310, S_BAR_V = 180, S_BARF_V = 190, S_BARR_V = 196, S_PIP_V = 204;
    private static final int LIGHT = 0xFFD8F0FF, DIM = 0xFF8AB0D8, GOLD = 0xFFFFE08A, RED = 0xFFFF8A7A;
    private static final int PER_PAGE = 6;

    private final List<String> blueprints;
    private final long shipId;
    private final int currentHp, effectiveMaxHp, mastCount;
    private final int[] upgradeLevels;
    private final int livery;            // index into Livery.ALL the ship wears, -1 = her own colours
    private final int[] liveryStatus;    // per livery: 0 locked, 1 can buy, 2 yours

    private int px, py, tab = 0, page = 0;

    public ShipwrightScreen(List<String> blueprints, long shipId, int currentHp, int effectiveMaxHp, int mastCount, int[] upgradeLevels,
                            int livery, int[] liveryStatus) {
        super(Text.literal("Shipwright"));
        this.blueprints = blueprints;
        this.shipId = shipId;
        this.currentHp = currentHp;
        this.effectiveMaxHp = effectiveMaxHp;
        this.mastCount = mastCount;
        this.upgradeLevels = upgradeLevels;
        this.livery = livery;
        this.liveryStatus = liveryStatus;
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2 + 8;
    }

    @Override
    public boolean shouldPause() { return false; }

    /** Haggler, as the server applies it (world/SkillEffects.haggle). */
    private static int haggle(int price) {
        if (price <= 0) return price;
        return Math.max(1, (int) Math.round(price * (1 - 0.04 * PirateLevelingClient.level("haggler"))));
    }

    private static String seaName(int zone) {
        return switch (zone) { case 0, 1 -> "Starter Seas"; case 2 -> "Merchant Seas"; case 3 -> "Pirate Seas"; case 4 -> "Cursed Seas"; default -> "The Abyss"; };
    }

    private static String pretty(String bp) {
        StringBuilder b = new StringBuilder();
        for (String w : bp.replace('-', '_').split("_")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        return b.toString().trim();
    }

    // ------------------------------------------------------------------ render
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawTexture(TEX, px, py, 0, 0, W, H, TW, TH);
        // brass tab plates on top of the board
        String[] tabs = {"Commission", "Upgrade Ship", "Livery"};
        for (int t = 0; t < 3; t++) {
            int x = tabX(t), y = py - 14;
            ctx.drawTexture(TEX, x, y, S_TAB_U, S_TAB_V + (t == tab ? S_TAB_H : 0), S_TAB_W, S_TAB_H, TW, TH);
            ctx.drawCenteredTextWithShadow(textRenderer, tabs[t], x + S_TAB_W / 2, y + 5, t == tab ? 0xFFFFF0C0 : 0xFFC8A870);
        }
        if (tab == 0) drawCommission(ctx, mx, my); else if (tab == 1) drawUpgrades(ctx, mx, my); else drawLiveries(ctx, mx, my);
        // scuttle (bottom-left) - the brass button, a red label
        int sx = px + 12, sy = py + H - 26;
        boolean sh = in(mx, my, sx, sy, S_BTN_W, S_BTN_H);
        ctx.drawTexture(TEX, sx, sy, S_BTN_U, S_BTN_V + (sh ? S_BTN_H : 0), S_BTN_W, S_BTN_H, TW, TH);
        ctx.drawCenteredTextWithShadow(textRenderer, "Scuttle", sx + S_BTN_W / 2, sy + 5, 0xFFFFB0A0);
        super.render(ctx, mx, my, delta);
        if (tab != 0 && tooltipLines != null) ctx.drawTooltip(textRenderer, tooltipLines, tooltipX, tooltipY);
        if (sh) ctx.drawTooltip(textRenderer, List.of(Text.literal("Sink your current ship").formatted(Formatting.RED),
                Text.literal("(you can only own one)").formatted(Formatting.GRAY)), mx, my);
    }

    private int tabX(int t) { return px + 4 + t * 98; }

    private int cardX(int i) { return px + 16 + (i % 2) * (S_CARD_W + 4); }

    private int cardY(int i) { return py + 30 + (i / 2) * (S_CARD_H + 6); }

    private void drawCommission(DrawContext ctx, int mx, int my) {
        ctx.drawText(textRenderer, Text.literal("Commission a hull").formatted(Formatting.BOLD), px + 16, py + 14, LIGHT, false);
        String cost = "Paid in doubloons";
        int cw = textRenderer.getWidth(cost);
        ctx.drawItem(new ItemStack(ModItems.COIN), px + W - 34 - cw, py + 10);
        ctx.drawText(textRenderer, cost, px + W - 16 - cw, py + 14, GOLD, false);
        if (blueprints.isEmpty()) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No blueprints for your seas yet.", px + W / 2, py + 80, LIGHT);
            ctx.drawCenteredTextWithShadow(textRenderer, "Save a ship with a Ship Blueprint first.", px + W / 2, py + 92, DIM);
            return;
        }
        int start = page * PER_PAGE, end = Math.min(blueprints.size(), start + PER_PAGE);
        for (int i = start; i < end; i++) {
            int k = i - start, x = cardX(k), y = cardY(k);
            boolean hov = in(mx, my, x, y, S_CARD_W, S_CARD_H);
            ctx.drawTexture(TEX, x, y, S_CARD_U, (hov ? 1 : 0) * S_CARD_H, S_CARD_W, S_CARD_H, TW, TH);
            ctx.drawItem(new ItemStack(ModItems.SHIP_BLUEPRINT), x + 5, y + 6);
            String name = pretty(blueprints.get(i));
            ctx.drawText(textRenderer, textRenderer.trimToWidth(name, S_CARD_W - 30), x + 25, y + 5, hov ? 0xFFFFFFFF : LIGHT, false);
            small(ctx, seaName(ShipTiers.getRequiredZone(blueprints.get(i))), x + 25, y + 17, DIM);
            String price = haggle(ShipTiers.commissionCost(blueprints.get(i))) + " doubloons";      // per hull (bigger = dearer)
            small(ctx, price, x + S_CARD_W - 6 - (int) (textRenderer.getWidth(price) * 0.75f), y + 17, GOLD);
        }
        int pages = (blueprints.size() + PER_PAGE - 1) / PER_PAGE;
        if (pages > 1) {
            ctx.drawCenteredTextWithShadow(textRenderer, (page > 0 ? "<  " : "   ") + "Page " + (page + 1) + " / " + pages + (page < pages - 1 ? "  >" : "   "),
                    px + W / 2, py + H - 42, DIM);
        }
        small(ctx, "Ships spawn beside you over open water. Bigger hulls open as you clear the seas.", px + W / 2, py + H - 30, DIM, true);
    }

    /** Refit rows: name + pips, description; the cost shows as a tooltip (six refits no longer fit three lines each). */
    private static final int ROW = 19, ROW_Y0 = 45;          // 7 rows end at py+178, above the Scuttle button (py+180)
    private List<Text> tooltipLines;
    private int tooltipX, tooltipY;

    private static final ItemStack[] UPGRADE_ICONS = {new ItemStack(Items.IRON_INGOT), new ItemStack(ModItems.CANNON_BALL),
            new ItemStack(ModItems.SAIL), new ItemStack(Items.IRON_BLOCK), new ItemStack(Items.GUNPOWDER), new ItemStack(Items.SCAFFOLDING),
            new ItemStack(Items.IRON_PICKAXE)};

    private void drawUpgrades(DrawContext ctx, int mx, int my) {
        if (shipId == -1L) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No ship of yours within 150 blocks.", px + W / 2, py + 80, RED);
            ctx.drawCenteredTextWithShadow(textRenderer, "Sail her up to the shipyard for a refit.", px + W / 2, py + 92, DIM);
            return;
        }
        ctx.drawText(textRenderer, Text.literal("Your ship").formatted(Formatting.BOLD), px + 16, py + 14, LIGHT, false);
        ctx.drawText(textRenderer, mastCount + " mast" + (mastCount == 1 ? "" : "s"), px + W - 16 - textRenderer.getWidth(mastCount + " masts"), py + 14, DIM, false);
        // hull bar
        int bx = px + (W - 150) / 2, by = py + 28;
        ctx.drawTexture(TEX, bx, by, S_BAR_U, S_BAR_V, 150, 8, TW, TH);
        float f = effectiveMaxHp > 0 ? Math.max(0, Math.min(1, (float) currentHp / effectiveMaxHp)) : 0;
        int fw = Math.round(146 * f);
        if (fw > 0) ctx.drawTexture(TEX, bx + 2, by + 2, S_BAR_U, f > 0.35f ? S_BARF_V : S_BARR_V, fw, 4, TW, TH);
        small(ctx, "Hull " + currentHp + " / " + effectiveMaxHp, px + W / 2, by + 11, LIGHT, true);

        List<ShipUpgrades.Def> defs = ShipUpgrades.ALL;
        int costTip = -1;
        tooltipLines = null;
        for (int i = 0; i < defs.size(); i++) {
            ShipUpgrades.Def def = defs.get(i);
            int y = py + ROW_Y0 + i * ROW, lv = upgradeLevels[i];
            ctx.drawItem(UPGRADE_ICONS[i % UPGRADE_ICONS.length], px + 16, y + 3);
            ctx.drawText(textRenderer, Text.literal(def.displayName()).formatted(Formatting.BOLD), px + 38, y, LIGHT, false);
            for (int p = 0; p < def.maxLevel(); p++)
                ctx.drawTexture(TEX, px + 38 + textRenderer.getWidth(def.displayName()) + 8 + p * 8, y + 1, S_BAR_U + (p < lv ? 8 : 0), S_PIP_V, 6, 6, TW, TH);
            small(ctx, def.description(), px + 38, y + 11, DIM, false);
            int ux = px + W - 16 - S_BTN_W, uy = y + 1;
            boolean max = lv >= def.maxLevel(), hov = !max && in(mx, my, ux, uy, S_BTN_W, S_BTN_H);
            ctx.drawTexture(TEX, ux, uy, S_BTN_U, S_BTN_V + (max ? 2 : hov ? 1 : 0) * S_BTN_H, S_BTN_W, S_BTN_H, TW, TH);
            ctx.drawCenteredTextWithShadow(textRenderer, max ? "MAX" : "Refit", ux + S_BTN_W / 2, uy + 5, max ? 0xFFB0A898 : 0xFFFFF4D0);
            if (!max && (hov || in(mx, my, px + 14, y - 1, ux - px - 16, ROW - 2))) costTip = i;
        }
        // the hovered refit's cost (Haggler applied), red where you are short
        if (costTip >= 0 && client != null && client.player != null) {
            List<Text> lines = new java.util.ArrayList<>();
            lines.add(Text.literal("Refit cost").formatted(Formatting.GOLD));
            for (ShipUpgrades.CostEntry c : defs.get(costTip).costPerLevel()) {
                int need = haggle(c.count()), have = client.player.getInventory().count(c.item());
                lines.add(Text.literal(need + "x ").append(c.item().getName()).formatted(have >= need ? Formatting.GRAY : Formatting.RED));
            }
            tooltipLines = lines; tooltipX = mx; tooltipY = my;
        }
    }

    // ------------------------------------------------------------------ the livery tab
    private static final int LIV_PER_PAGE = 8, LIV_ROW = 33;
    private int livPage = 0;

    private int livCount() { return Livery.ALL.size() + 1; }                      // + "her own colours" first

    private int livX(int k) { return px + 16 + (k % 2) * (S_CARD_W + 4); }

    private int livY(int k) { return py + 30 + (k / 2) * LIV_ROW; }

    private static int mapColour(String id) {
        var b = Registries.BLOCK.get(new Identifier(id));
        return 0xFF000000 | b.getDefaultMapColor().color;
    }

    private void drawLiveries(DrawContext ctx, int mx, int my) {
        if (shipId == -1L) {
            ctx.drawCenteredTextWithShadow(textRenderer, "No ship of yours within 150 blocks.", px + W / 2, py + 80, RED);
            ctx.drawCenteredTextWithShadow(textRenderer, "Sail her up to the shipyard to repaint her.", px + W / 2, py + 92, DIM);
            return;
        }
        ctx.drawText(textRenderer, Text.literal("Livery").formatted(Formatting.BOLD), px + 16, py + 14, LIGHT, false);
        String wear = "Wearing: " + (livery < 0 ? "her own colours" : Livery.ALL.get(livery).name());
        small(ctx, wear, px + W - 16 - (int) (textRenderer.getWidth(wear) * 0.75f), py + 16, GOLD);
        List<Text> tip = null;
        int start = livPage * LIV_PER_PAGE, end = Math.min(livCount(), start + LIV_PER_PAGE);
        for (int i = start; i < end; i++) {
            int k = i - start, x = livX(k), y = livY(k), li = i - 1;
            Livery l = li < 0 ? null : Livery.ALL.get(li);
            int status = l == null ? 2 : liveryStatus[li];
            boolean hov = in(mx, my, x, y, S_CARD_W, S_CARD_H), wearing = li == livery;
            ctx.drawTexture(TEX, x, y, S_CARD_U, (hov && status > 0 ? 1 : 0) * S_CARD_H, S_CARD_W, S_CARD_H, TW, TH);
            // the swatch: hull wood | paint band | sail
            String[] sw = l == null ? new String[]{"minecraft:dark_oak_planks", "minecraft:white_concrete", "pixelpirates:white_sail_canvas"}
                    : new String[]{l.hull().planks(), l.band(), l.sail()};
            for (int s = 0; s < 3; s++) ctx.fill(x + 5 + s * 6, y + 5, x + 10 + s * 6, y + 23, status == 0 ? 0xFF555555 : mapColour(sw[s]));
            ctx.fill(x + 4, y + 4, x + 24, y + 5, 0xFF2A1A10); ctx.fill(x + 4, y + 23, x + 24, y + 24, 0xFF2A1A10);
            String name = l == null ? "Her own colours" : l.name();
            ctx.drawText(textRenderer, textRenderer.trimToWidth(name, S_CARD_W - 32), x + 28, y + 5, status == 0 ? DIM : hov ? 0xFFFFFFFF : LIGHT, false);
            String line; int col;
            if (wearing) { line = "Wearing"; col = GOLD; }
            else if (l == null) { line = "Free"; col = DIM; }
            else if (status == 0) { line = l.unlockText(); col = RED; }
            else if (status == 2) { line = (l.kind() == Livery.Kind.SPECIAL ? "Earned" : "Owned") + " - " + haggle(Liveries.SWITCH_FEE) + " to switch"; col = DIM; }
            else { line = haggle(l.price()) + " doubloons"; col = GOLD; }
            small(ctx, textRenderer.trimToWidth(line, (int) ((S_CARD_W - 32) / 0.75f)), x + 28, y + 17, col);
            if (hov && l != null) {
                tip = new java.util.ArrayList<>();
                tip.add(Text.literal(l.name()).formatted(Formatting.GOLD));
                tip.add(Text.literal(l.blurb()).formatted(Formatting.GRAY));
                tip.add(Text.literal(switch (l.kind()) { case BUY -> "Bought at the shipyard"; case FACTION -> "Bought - needs " + l.unlockText();
                    case SPECIAL -> "Earned only - " + l.unlockText(); }).formatted(l.kind() == Livery.Kind.SPECIAL ? Formatting.LIGHT_PURPLE : Formatting.DARK_GRAY));
            }
        }
        int pages = (livCount() + LIV_PER_PAGE - 1) / LIV_PER_PAGE;
        ctx.drawCenteredTextWithShadow(textRenderer, (livPage > 0 ? "<  " : "   ") + "Page " + (livPage + 1) + " / " + pages + (livPage < pages - 1 ? "  >" : "   "),
                px + W / 2, py + H - 42, DIM);
        tooltipLines = tip; tooltipX = mx; tooltipY = my;
    }

    // ------------------------------------------------------------------ helpers
    private void small(DrawContext ctx, String s, int x, int y, int col) { small(ctx, s, x, y, col, false); }

    private void small(DrawContext ctx, String s, int x, int y, int col, boolean centred) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 0);
        ctx.getMatrices().scale(0.75f, 0.75f, 1f);
        int w = textRenderer.getWidth(s);
        ctx.drawText(textRenderer, s, centred ? -w / 2 : 0, 0, col, false);
        ctx.getMatrices().pop();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void click() {
        if (client != null) client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.ITEM_BOOK_PAGE_TURN, 1f));
    }

    // ------------------------------------------------------------------ input
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);
        for (int t = 0; t < 3; t++) {
            if (in(mx, my, tabX(t), py - 14, S_TAB_W, S_TAB_H)) { tab = t; click(); return true; }
        }
        if (in(mx, my, px + 12, py + H - 26, S_BTN_W, S_BTN_H)) {
            ClientPlayNetworking.send(ModNetworking.SHIPWRIGHT_SCUTTLE, PacketByteBufs.empty());
            close();
            return true;
        }
        if (tab == 0) {
            int start = page * PER_PAGE, end = Math.min(blueprints.size(), start + PER_PAGE);
            for (int i = start; i < end; i++) {
                int k = i - start;
                if (in(mx, my, cardX(k), cardY(k), S_CARD_W, S_CARD_H)) {
                    var buf = PacketByteBufs.create();
                    buf.writeString(blueprints.get(i), 64);
                    ClientPlayNetworking.send(ModNetworking.SHIPWRIGHT_SPAWN, buf);
                    close();
                    return true;
                }
            }
            int pages = (blueprints.size() + PER_PAGE - 1) / PER_PAGE;
            if (pages > 1 && my >= py + H - 44 && my < py + H - 32) {
                if (mx < px + W / 2 && page > 0) { page--; click(); return true; }
                if (mx >= px + W / 2 && page < pages - 1) { page++; click(); return true; }
            }
        } else if (tab == 2 && shipId != -1L) {
            int start = livPage * LIV_PER_PAGE, end = Math.min(livCount(), start + LIV_PER_PAGE);
            for (int i = start; i < end; i++) {
                int k = i - start, li = i - 1;
                if (!in(mx, my, livX(k), livY(k), S_CARD_W, S_CARD_H)) continue;
                if (li == livery || (li >= 0 && liveryStatus[li] == 0)) return true;
                var buf = PacketByteBufs.create();
                buf.writeLong(shipId);
                buf.writeString(li < 0 ? "original" : Livery.ALL.get(li).id(), 48);
                ClientPlayNetworking.send(Liveries.APPLY, buf);
                close();
                return true;
            }
            int pages = (livCount() + LIV_PER_PAGE - 1) / LIV_PER_PAGE;
            if (my >= py + H - 44 && my < py + H - 32) {
                if (mx < px + W / 2 && livPage > 0) { livPage--; click(); return true; }
                if (mx >= px + W / 2 && livPage < pages - 1) { livPage++; click(); return true; }
            }
        } else if (tab == 1 && shipId != -1L) {
            List<ShipUpgrades.Def> defs = ShipUpgrades.ALL;
            for (int i = 0; i < defs.size(); i++) {
                int ux = px + W - 16 - S_BTN_W, uy = py + ROW_Y0 + i * ROW + 1;          // same as drawUpgrades
                if (upgradeLevels[i] < defs.get(i).maxLevel() && in(mx, my, ux, uy, S_BTN_W, S_BTN_H)) {
                    var buf = PacketByteBufs.create();
                    buf.writeLong(shipId);
                    buf.writeString(defs.get(i).key(), 32);
                    ClientPlayNetworking.send(ModNetworking.SHIPWRIGHT_UPGRADE, buf);
                    close();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, btn);
    }
}
