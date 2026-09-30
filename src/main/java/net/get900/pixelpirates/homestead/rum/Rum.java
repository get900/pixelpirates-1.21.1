package net.get900.pixelpirates.homestead.rum;

import net.get900.pixelpirates.homestead.HomesteadEffects;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;

/**
 * RUM (#12): molasses -> (Rum Still, over a fire) raw rum -> (Aging Cask) aged rum after 1 day -> vintage after 2 more.
 * Tier 0 raw, 1 aged, 2 vintage. Every drink stacks TIPSY (max 3); at 3 the world spins.
 */
public final class Rum {
    private Rum() {}

    public static final long AGE_1 = 24000, AGE_2 = 48000;          // ticks in the cask: raw -> aged, aged -> vintage

    public static Item item(int tier) {
        return switch (tier) { case 0 -> HomesteadItems.RAW_RUM; case 1 -> HomesteadItems.AGED_RUM; default -> HomesteadItems.VINTAGE_RUM; };
    }

    public static int tier(Item item) {
        if (item == HomesteadItems.RAW_RUM) return 0;
        if (item == HomesteadItems.AGED_RUM) return 1;
        if (item == HomesteadItems.VINTAGE_RUM) return 2;
        return -1;
    }

    /** The tier a bottle has reached after {@code aged} ticks in the cask, starting from {@code start}. */
    public static int aged(int start, long aged) {
        long t = aged;
        int tier = start;
        if (tier == 0 && t >= AGE_1) { tier = 1; t -= AGE_1; }
        if (tier == 1 && t >= AGE_2) tier = 2;
        return tier;
    }

    public static void drink(LivingEntity e, int tier) {
        int dur = switch (tier) { case 0 -> 900; case 1 -> 2400; default -> 3600; };
        e.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, dur, tier == 2 ? 1 : 0));
        if (tier >= 1) e.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur / 2, 0));
        if (tier == 2) e.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 400, 0));
        if (tier == 0) e.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0));
        StatusEffectInstance cur = e.getStatusEffect(HomesteadEffects.TIPSY);
        int amp = cur == null ? (tier == 2 ? 1 : 0) : Math.min(3, cur.getAmplifier() + 1);
        e.addStatusEffect(new StatusEffectInstance(HomesteadEffects.TIPSY, 1200 + tier * 600, amp));
        if (amp >= 3) e.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 300, 0));
    }
}
