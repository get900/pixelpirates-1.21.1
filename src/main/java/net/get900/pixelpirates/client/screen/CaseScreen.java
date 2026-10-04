package net.get900.pixelpirates.client.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.homestead.hoard.Strongboxes;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * STRONGBOX REEL (2026-10-01): a strip of prizes scrolls under a gold pointer, slows down and stops on what the server
 * already rolled (Strongboxes, S2C SPIN). Slots are tinted by rarity. Art: tools/gen_gui_textures.py strongbox().
 */
@Environment(EnvType.CLIENT)
public class CaseScreen extends Screen {
    private static final Identifier TEX = new Identifier("pixelpirates", "textures/gui/strongbox.png");
    private static final int TW = 256, TH = 128, W = 240, H = 96, SLOT = 26;
    private static final int[] RARITY_COL = {0xFF8A8A8A, 0xFF4AA03A, 0xFF3A7AD8, 0xFFA040D8, 0xFFE8B020};
    private static final float SPIN_MS = 5500f;
    private static final String[] TITLES = {"Common Strongbox", "Rare Strongbox", "Legendary Strongbox"};

    private final ItemStack[] reel;
    private final int[] rarity;
    private final int win, tier;
    private final long start = System.currentTimeMillis();
    private int px, py, lastSlot = -1;
    private boolean landed;

    public CaseScreen(NbtCompound data) {
        super(Text.literal("Strongbox"));
        NbtList l = data.getList("Reel", NbtElement.COMPOUND_TYPE);
        reel = new ItemStack[l.size()];
        rarity = new int[l.size()];
        for (int i = 0; i < l.size(); i++) {
            NbtCompound e = l.getCompound(i);
            reel[i] = new ItemStack(Registries.ITEM.get(new Identifier(e.getString("Id"))), Math.max(1, e.getInt("N")));
            rarity[i] = e.getInt("R");
        }
        win = data.getInt("Win");
        tier = Math.max(0, Math.min(2, data.getInt("Tier")));
    }

    @Override
    protected void init() { px = (width - W) / 2; py = (height - H) / 2; }

    @Override
    public boolean shouldPause() { return false; }

    /** Strip offset (px) at time t: ease-out cubic onto the winning slot's centre under the pointer. */
    private float offset(float t) {
        float k = Math.min(1f, t / SPIN_MS), e = 1 - (1 - k) * (1 - k) * (1 - k);
        return e * (win * SLOT);
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        renderBackground(ctx);
        ctx.drawTexture(TEX, px, py, 0, 0, W, H, TW, TH);
        ctx.drawCenteredTextWithShadow(textRenderer, TITLES[tier], px + W / 2, py + 8, 0xFFFFE8B0);
        float t = System.currentTimeMillis() - start;
        float off = offset(t);
        int windowL = px + 12, windowR = px + W - 12, cx = px + W / 2, y = py + 30;
        ctx.enableScissor(windowL, y - 2, windowR, y + SLOT + 2);
        for (int i = 0; i < reel.length; i++) {
            int x = (int) (cx - SLOT / 2f + i * SLOT - off);
            if (x + SLOT < windowL || x > windowR) continue;
            ctx.fill(x + 1, y, x + SLOT - 1, y + SLOT, 0xFF2A2018);
            ctx.fill(x + 1, y + SLOT - 3, x + SLOT - 1, y + SLOT, RARITY_COL[rarity[i]]);
            ctx.fill(x + 1, y, x + SLOT - 1, y + 1, (RARITY_COL[rarity[i]] & 0x00FFFFFF) | 0x80000000);
            ctx.drawItem(reel[i], x + 5, y + 4);
            ctx.drawItemInSlot(textRenderer, reel[i], x + 5, y + 4);
        }
        ctx.disableScissor();
        ctx.drawTexture(TEX, cx - 4, y - 6, 0, 100, 9, 6, TW, TH);                     // pointer above
        ctx.drawTexture(TEX, cx - 4, y + SLOT, 10, 100, 9, 6, TW, TH);                  // and below
        // a tick sound as each slot passes the pointer
        int slot = (int) ((off + SLOT / 2f) / SLOT);
        if (slot != lastSlot && client != null && !landed) {
            lastSlot = slot;
            client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), 1.6f, 0.4f));
        }
        if (t >= SPIN_MS) {
            if (!landed && client != null) {
                landed = true;
                client.getSoundManager().play(PositionedSoundInstance.master(rarity[win] >= 3 ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE
                        : SoundEvents.ENTITY_PLAYER_LEVELUP, 1f));
            }
            String name = Strongboxes.RARITY[rarity[win]] + ": " + reel[win].getCount() + " x " + reel[win].getName().getString();
            ctx.drawCenteredTextWithShadow(textRenderer, name, px + W / 2, py + 68, RARITY_COL[rarity[win]]);
            ctx.drawCenteredTextWithShadow(textRenderer, "Esc to close", px + W / 2, py + 80, 0xFFB0A080);
        } else {
            ctx.drawCenteredTextWithShadow(textRenderer, "The lock gives...", px + W / 2, py + 68, 0xFFD8C8A0);
        }
        super.render(ctx, mx, my, delta);
    }
}
