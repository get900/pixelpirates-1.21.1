package net.get900.pixelpirates.item;


import net.minecraft.item.FoodComponent;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;

public class ModFoodComponents {
    public static final FoodComponent COOKED_SHARK_MEAT = new FoodComponent.Builder().hunger(8).saturationModifier(0.3f).build();
    public static final FoodComponent RAW_SHARK_MEAT = new FoodComponent.Builder().hunger(4).saturationModifier(0.3F)
            .statusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 200, 0), 0.4f).build();
    public static final FoodComponent RAW_SALTED_SWIMMER = new FoodComponent.Builder().hunger(2).saturationModifier(0.3F)
            .statusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 200, 0), 0.4f).build();
    public static final FoodComponent COOKED_SALTED_SWIMMER = new FoodComponent.Builder().hunger(5).saturationModifier(0.3F).build();
    public static final FoodComponent BANANA = new FoodComponent.Builder().hunger(2).saturationModifier(0.1F).build();
    public static final FoodComponent COCONUT = new FoodComponent.Builder().hunger(2).saturationModifier(0.1F).build();

    // Galley cooking
    public static final FoodComponent BANANA_BREAD = new FoodComponent.Builder().hunger(6).saturationModifier(0.6f).build();
    public static final FoodComponent HARDTACK = new FoodComponent.Builder().hunger(4).saturationModifier(0.3f).build();
    public static final FoodComponent KRAKEN_CALAMARI = new FoodComponent.Builder().hunger(8).saturationModifier(0.8f)
            .statusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 1200, 0), 1.0f).build();
    public static final FoodComponent COCONUT_GROG = new FoodComponent.Builder().hunger(2).saturationModifier(0.2f).alwaysEdible()
            .statusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 1200, 0), 1.0f).build();
    public static final FoodComponent PIRATES_STEW = new FoodComponent.Builder().hunger(10).saturationModifier(0.8f)
            .statusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 0), 1.0f).build();
}