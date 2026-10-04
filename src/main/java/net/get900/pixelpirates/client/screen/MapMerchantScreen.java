package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.client.PirateLevelingClient;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.network.ModNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * THE MAP MERCHANT'S SEA CHART (redesigned 2026-10-01): his wares pinned to an old chart as 3 x 3 parchment cards -
 * icon, name, price (Haggler applied, as the server charges it) or FREE; owned wares carry a wax seal. Click a card to
 * buy. Art: tools/gen_gui_textures.py `chart()` -> textures/gui/chart.png (the S_* rectangles below match its atlas).
 * Indices are the server's ModNetworking.SHOP_COSTS order - never reorder ENTRIES.
 */
@Environment(EnvType.CLIENT)
public class MapMerchantScreen extends Screen {
    private static final Identifier TEX = new Identifier(PixelPirates.MOD_ID, "textures/gui/chart.png");
    private static final int TW = 512, TH = 256, W = 300, H = 210;
    private static final int S_CARD_U = 320, S_CARD_W = 92, S_CARD_H = 44, S_SEAL_V = 140;
    private static final int INK = 0xFF3A2410, INK_L = 0xFF7A5A34, RED = 0xFF8A2A1A, GREEN = 0xFF2E5A20;

    private record ShopEntry(String name, String desc, int cost, int index, ItemStack icon) {}

    private static final List<ShopEntry> ENTRIES = List.of(
        new ShopEntry("Ship Radar",      "A brass compass that shows ships", 15, 0, new ItemStack(net.minecraft.item.Items.COMPASS)),
        new ShopEntry("Common Map",      "Buried loot, the starter seas",   10, 1, new ItemStack(ModItems.TREASURE_MAP_COMMON)),
        new ShopEntry("Rare Map",        "Richer loot, zones 2-3",          30, 2, new ItemStack(ModItems.TREASURE_MAP_RARE)),
        new ShopEntry("Legendary Map",   "Legendary loot, zones 4-5",       75, 3, new ItemStack(ModItems.TREASURE_MAP_LEGENDARY)),
        new ShopEntry("Bounty Map",      "Track an active enemy ship",      10, 4, new ItemStack(ModItems.BOUNTY_MAP)),
        new ShopEntry("Cannon Balls",    "64 of them, on the house",       0, 5, new ItemStack(ModItems.CANNON_BALL, 64)),
        new ShopEntry("Shipwright Table","On the house - set up your dock!",         0, 6, new ItemStack(ModBlocks.SHIPWRIGHT_TABLE)),
        new ShopEntry("Cutlass",         "On the house - every pirate needs one",    0, 7, new ItemStack(ModItems.CUTLASS)),
        new ShopEntry("Starter Rations", "On the house - 8 bananas + 3 grog",        0, 8, new ItemStack(ModItems.BANANA, 8))
    );

    private static final String[] QUIPS = {
        "\"Every X marks a story, friend. Most end badly.\"",
        "\"The sea's big. My charts make it smaller.\"",
        "\"Legendary maps? Bring coin and a strong stomach.\"",
        "\"Radar's the finest thing I sell. Don't ask where it came from.\"",
        "\"Haggle all you like - I've already priced it in.\"",
    };

    private final boolean hasRadar;
    private int coins, claimed, px, py;
    private final String quip;

    public MapMerchantScreen(boolean hasRadar, int coins, int claimed) {
        super(Text.literal("Map Merchant"));
        this.hasRadar = hasRadar;
        this.coins = coins;
        this.claimed = claimed;
        this.quip = QUIPS[(int) (System.currentTimeMillis() / 1000 % QUIPS.length)];
    }

    @Override
    protected void init() {
        px = (width - W) / 2;
        py = (height - H) / 2;
    }

    @Override
    public boolean shouldPause() { return false; }

    /** Haggler, as the server applies it (world/SkillEffects.haggle). */
    private static int price(ShopEntry e) {
        if (e.cost() <= 0) return 0;
        return Math.max(1, (int) Math.round(e.cost() * (1 - 0.04 * PirateLevelingClient.level("haggler"))));
    }

    private int cardX(int i) { return px + 10 + (i % 3) * (S_CARD_W + 2); }

    private int cardY(int i) { return py + 30 + (i / 3) * (S_CARD_H + 3); }

    /** The radar once bought, or a free starter card already claimed (one per player). */
    private boolean owned(ShopEntry e) { return (e.index() == 0 && hasRadar) || (claimed & (1 << e.index())) != 0; }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawTexture(TEX, px, py, 0, 0, W, H, TW, TH);
        // title cartouche + purse
        ctx.drawText(textRenderer, Text.literal("Charts & Curios").formatted(Formatting.BOLD), px + 12, py + 12, INK, false);
        small(ctx, "the Map Merchant's wares", px + 12, py + 22, INK_L, 0.7f);
        ItemStack coin = new ItemStack(ModItems.COIN);
        ctx.drawItem(coin, px + W - 50, py + 8);
        ctx.drawText(textRenderer, String.valueOf(coins), px + W - 32, py + 12, INK, false);

        ShopEntry hovered = null;
        for (int i = 0; i < ENTRIES.size(); i++) {
            ShopEntry e = ENTRIES.get(i);
            int x = cardX(i), y = cardY(i), p = price(e);
            boolean own = owned(e), afford = coins >= p, hov = in(mx, my, x, y, S_CARD_W, S_CARD_H);
            if (hov) hovered = e;
            int row = own || !afford ? 2 : hov ? 1 : 0;
            ctx.drawTexture(TEX, x, y, S_CARD_U, row * S_CARD_H, S_CARD_W, S_CARD_H, TW, TH);
            ctx.drawItem(e.icon(), x + 5, y + 6);
            small(ctx, e.name(), x + 24, y + 7, own ? INK_L : INK);
            small(ctx, fit(e.desc(), 84), x + 24, y + 16, INK_L);        // the full line is in the tooltip
            // price line
            if (own) {
                ctx.drawTexture(TEX, x + S_CARD_W - 19, y + S_CARD_H - 19, S_CARD_U, S_SEAL_V, 14, 14, TW, TH);
                small(ctx, e.cost() == 0 ? "CLAIMED" : "OWNED", x + 6, y + 32, INK_L);
            } else if (p == 0) {
                small(ctx, "FREE", x + 6, y + 32, GREEN);
            } else {
                ctx.getMatrices().push();
                ctx.getMatrices().translate(x + 5, y + 28, 0);
                ctx.getMatrices().scale(0.7f, 0.7f, 1);
                ctx.drawItem(coin, 0, 0);
                ctx.getMatrices().pop();
                String s = p < e.cost() ? p + "  (" + e.cost() + ")" : String.valueOf(p);
                small(ctx, s, x + 18, y + 32, afford ? INK : RED);
            }
        }
        // the merchant's word, at the foot of the chart
        int qw = (int) (textRenderer.getWidth(quip) * 0.8f);
        small(ctx, quip, px + (W - qw) / 2, py + H - 26, INK_L, 0.8f);
        small(ctx, "Click a card to buy  -  Esc to leave", px + 12, py + H - 14, INK_L, 0.7f);
        super.render(ctx, mx, my, delta);
        if (hovered != null) {
            int p = price(hovered);
            ctx.drawTooltip(textRenderer, List.of(
                    Text.literal(hovered.name()).formatted(Formatting.GOLD),
                    Text.literal(hovered.desc()).formatted(Formatting.GRAY),
                    owned(hovered) ? Text.literal(hovered.cost() == 0 ? "Already claimed" : "Already yours").formatted(Formatting.DARK_GRAY)
                            : p == 0 ? Text.literal("Free - once per sailor").formatted(Formatting.GREEN)
                            : Text.literal(p + " doubloons" + (p < hovered.cost() ? " (Haggler: -" + (hovered.cost() - p) + ")" : ""))
                                    .formatted(coins >= p ? Formatting.YELLOW : Formatting.RED)), mx, my);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) for (int i = 0; i < ENTRIES.size(); i++) {
            ShopEntry e = ENTRIES.get(i);
            if (!in(mx, my, cardX(i), cardY(i), S_CARD_W, S_CARD_H)) continue;
            int p = price(e);
            if (owned(e) || coins < p) {
                client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f));
                return true;
            }
            var buf = PacketByteBufs.create();
            buf.writeInt(e.index());
            ClientPlayNetworking.send(ModNetworking.MAP_MERCHANT_BUY, buf);
            coins -= p;                                   // the server re-checks; this only keeps the cards honest
            if (e.cost() == 0) claimed |= 1 << e.index();
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1f));
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    /** Trims s (with "..") to fit w unscaled pixels - 0.75-scale text in a card's 65 px text column. */
    private String fit(String s, int w) {
        if (textRenderer.getWidth(s) <= w) return s;
        while (s.length() > 1 && textRenderer.getWidth(s + "..") > w) s = s.substring(0, s.length() - 1);
        return s.trim() + "..";
    }

    private void small(DrawContext ctx, String s, int x, int y, int col) { small(ctx, s, x, y, col, 0.75f); }

    private void small(DrawContext ctx, String s, int x, int y, int col, float scale) {
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x, y, 200);
        ctx.getMatrices().scale(scale, scale, 1);
        ctx.drawText(textRenderer, s, 0, 0, col, false);
        ctx.getMatrices().pop();
    }

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
