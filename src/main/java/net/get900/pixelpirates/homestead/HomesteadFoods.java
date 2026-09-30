package net.get900.pixelpirates.homestead;

import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.FoodComponent;

/** Island produce and the new galley dishes. Seconds x 20 = ticks. */
public final class HomesteadFoods {
    private HomesteadFoods() {}

    public static final FoodComponent PINEAPPLE = new FoodComponent.Builder().hunger(4).saturationModifier(0.4f).build();
    public static final FoodComponent LIME = new FoodComponent.Builder().hunger(2).saturationModifier(0.2f).snack()
            .statusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0), 1f).build();
    public static final FoodComponent CHILI_PEPPER = new FoodComponent.Builder().hunger(1).saturationModifier(0.1f).snack().alwaysEdible()
            .statusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 200, 0), 1f)
            .statusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), 0.3f).build();
    public static final FoodComponent FRUIT_SALAD = new FoodComponent.Builder().hunger(8).saturationModifier(0.7f)
            .statusEffect(new StatusEffectInstance(StatusEffects.SPEED, 1200, 0), 1f).build();
    public static final FoodComponent CEVICHE = new FoodComponent.Builder().hunger(7).saturationModifier(0.8f)
            .statusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, 1200, 0), 1f).build();
    public static final FoodComponent SPICY_CHOWDER = new FoodComponent.Builder().hunger(10).saturationModifier(0.9f)
            .statusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 1800, 0), 1f).build();
    public static final FoodComponent CHOCOLATE_DOUBLOON = new FoodComponent.Builder().hunger(2).saturationModifier(0.3f).snack()
            .statusEffect(new StatusEffectInstance(StatusEffects.LUCK, 2400, 0), 1f).build();
    public static final FoodComponent PINEAPPLE_GROG = new FoodComponent.Builder().hunger(3).saturationModifier(0.3f).alwaysEdible()
            .statusEffect(new StatusEffectInstance(StatusEffects.HASTE, 2400, 0), 1f).build();

    // #13 fish
    public static final FoodComponent PARROTFISH = raw(2, 0.2);
    public static final FoodComponent RED_SNAPPER = raw(3, 0.3);
    public static final FoodComponent MAHI_MAHI = raw(3, 0.3);
    public static final FoodComponent LIONFISH = rawFx(2, 0.2, StatusEffects.POISON, 100, 0, 1f);
    public static final FoodComponent MOONFISH = rawFx(2, 0.2, StatusEffects.NIGHT_VISION, 600, 0, 1f);
    public static final FoodComponent EMBERFIN = rawFx(2, 0.2, StatusEffects.FIRE_RESISTANCE, 600, 0, 1f);
    public static final FoodComponent LAVA_EEL = rawFx(3, 0.3, StatusEffects.REGENERATION, 100, 0, 1f);
    public static final FoodComponent GHOSTFIN = rawFx(2, 0.2, StatusEffects.INVISIBILITY, 300, 0, 1f);
    public static final FoodComponent BONEFISH = raw(3, 0.3);
    public static final FoodComponent ANGLERFRY = rawFx(2, 0.2, StatusEffects.WATER_BREATHING, 600, 0, 1f);
    public static final FoodComponent VOIDFIN = rawFx(3, 0.3, StatusEffects.NAUSEA, 160, 0, 0.5f);
    public static final FoodComponent COOKED_FILLET = new FoodComponent.Builder().hunger(6).saturationModifier(0.7f).build();
    public static final FoodComponent LOBSTER = rawFx(2, 0.2, StatusEffects.HUNGER, 300, 0, 0.3f);
    public static final FoodComponent COOKED_LOBSTER = new FoodComponent.Builder().hunger(8).saturationModifier(0.9f).build();
    public static final FoodComponent CRAB_CLAW = new FoodComponent.Builder().hunger(3).saturationModifier(0.4f).snack().build();

    static FoodComponent raw(int h, double s) { return new FoodComponent.Builder().hunger(h).saturationModifier((float) s).build(); }

    static FoodComponent rawFx(int h, double s, net.minecraft.entity.effect.StatusEffect e, int t, int a, float p) {
        return new FoodComponent.Builder().hunger(h).saturationModifier((float) s).statusEffect(new StatusEffectInstance(e, t, a), p).build();
    }
}
