package net.get900.pixelpirates.homestead.tavern;

import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadEffects;
import net.get900.pixelpirates.homestead.item.DrinkItem;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * THE GROG BARREL'S DRINKS (2026-10-01). Every one makes you TIPSY (stacks to III - the world sways; at III you also get
 * nausea), like the rum. Ale comes in a tankard and gives the tankard back; the rest are bottled.
 */
public final class TavernDrinks {
    private TavernDrinks() {}

    public enum Kind {
        ALE("Ale", 1, "Haste I (1:30)"),
        HONEY_MEAD("Honey Mead", 1, "Regeneration I (0:10), Saturation"),
        SPICED_WINE("Spiced Wine", 1, "Fire Resistance (1:30)"),
        BILGE_WHISKEY("Bilge-Rat Whiskey", 2, "Resistance I (1:30), Nausea (0:05)"),
        KRAKENS_KISS("Kraken's Kiss", 2, "Water Breathing + Night Vision (3:00)");

        public final String title, effects;
        public final int tipsy;

        Kind(String title, int tipsy, String effects) { this.title = title; this.tipsy = tipsy; this.effects = effects; }
    }

    public static void drink(LivingEntity e, Kind k) {
        switch (k) {
            case ALE -> e.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 1800, 0));
            case HONEY_MEAD -> { e.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0)); e.addStatusEffect(new StatusEffectInstance(StatusEffects.SATURATION, 4, 0)); }
            case SPICED_WINE -> e.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 1800, 0));
            case BILGE_WHISKEY -> { e.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 1800, 0)); e.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0)); }
            case KRAKENS_KISS -> { e.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 3600, 0)); e.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 3600, 0)); }
        }
        tipsy(e, k.tipsy, 1200);
    }

    /** Adds {@code add} levels of Tipsy (max III = amplifier 3), refreshing it to at least {@code ticks}. */
    public static void tipsy(LivingEntity e, int add, int ticks) {
        StatusEffect t = HomesteadEffects.TIPSY;
        StatusEffectInstance cur = e.getStatusEffect(t);
        int amp = Math.min(3, cur == null ? add - 1 : cur.getAmplifier() + add);
        e.addStatusEffect(new StatusEffectInstance(t, Math.max(ticks, cur == null ? 0 : cur.getDuration()), amp));
        if (amp >= 3) e.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 300, 0));
    }

    /** A tavern drink: drinks like a potion, gives back the tankard (ale) or the glass bottle. */
    public static class TavernDrinkItem extends DrinkItem {
        private final Kind kind;

        public TavernDrinkItem(Settings s, Kind kind) {
            super(s, e -> drink(e, kind));
            this.kind = kind;
        }

        public Kind kind() { return kind; }

        @Override
        public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
            ItemStack rest = super.finishUsing(stack, world, user);
            if (kind != Kind.ALE || !(user instanceof PlayerEntity p) || p.getAbilities().creativeMode) return rest;
            // DrinkItem hands back a glass bottle: swap it for the tankard
            Item tankard = HomesteadBlocks.TANKARD.asItem();
            if (rest.isOf(Items.GLASS_BOTTLE)) return new ItemStack(tankard);
            int slot = p.getInventory().getSlotWithStack(new ItemStack(Items.GLASS_BOTTLE));
            if (slot >= 0) { p.getInventory().getStack(slot).decrement(1); p.getInventory().offerOrDrop(new ItemStack(tankard)); }
            return rest;
        }

        @Override
        public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
            tooltip.add(Text.literal(kind.effects).formatted(Formatting.BLUE));
            tooltip.add(Text.literal("Tipsy +" + kind.tipsy).formatted(Formatting.GOLD));
        }
    }
}
