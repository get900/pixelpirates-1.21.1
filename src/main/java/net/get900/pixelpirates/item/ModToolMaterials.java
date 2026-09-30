package net.get900.pixelpirates.item;

import com.google.common.base.Suppliers;
import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

import java.util.Objects;
import java.util.function.Supplier;

public enum ModToolMaterials implements ToolMaterial {
    PIRATE(2, 600, 7.0F, 3.0F, 15, () -> Ingredient.ofItems(ModItems.ROPE)),
    // Ring 1 — salvage-grade gear
    CASTAWAY(1, 200, 5.0F, 1.5F, 8, () -> Ingredient.ofItems(ModItems.DRIFTWOOD)),
    // Ring 2 — navy/merchant forged steel
    NAVAL(2, 750, 6.5F, 4.0F, 16, () -> Ingredient.ofItems(net.minecraft.item.Items.IRON_INGOT)),
    // Ring 3 — corsair gold-worked steel
    CORSAIR(3, 1200, 7.5F, 5.0F, 18, () -> Ingredient.ofItems(ModItems.PIRATE_COIN)),
    // Volcanic isles — ember-forged
    VOLCANIC(3, 1500, 8.0F, 5.5F, 15, () -> Ingredient.ofItems(ModItems.VOLCANIC_EMBER)),
    // Ring 4 — cursed bone
    CURSED(3, 1600, 8.0F, 6.0F, 20, () -> Ingredient.ofItems(ModItems.CURSED_BONE)),
    // Ring 5 — abyssal kraken-scale
    ABYSSAL(4, 2000, 9.0F, 7.0F, 22, () -> Ingredient.ofItems(ModItems.KRAKEN_SCALE));

    private final int miningLevel;
    private final int itemDurability;
    private final float miningSpeed;
    private final float attackDamage;
    private final int enchantability;
    private final Supplier<Ingredient> repairIngredient;

    ModToolMaterials(int miningLevel, int itemDurability, float miningSpeed, float attackDamage,
                     int enchantability, Supplier<Ingredient> repairIngredient) {
        this.miningLevel = miningLevel;
        this.itemDurability = itemDurability;
        this.miningSpeed = miningSpeed;
        this.attackDamage = attackDamage;
        this.enchantability = enchantability;
        Objects.requireNonNull(repairIngredient);
        this.repairIngredient = Suppliers.memoize(repairIngredient::get);
    }

    @Override
    public int getDurability() { return itemDurability; }

    @Override
    public float getMiningSpeedMultiplier() { return miningSpeed; }

    @Override
    public float getAttackDamage() { return attackDamage; }

    @Override
    public int getMiningLevel() { return miningLevel; }

    @Override
    public int getEnchantability() { return enchantability; }

    @Override
    public Ingredient getRepairIngredient() { return repairIngredient.get(); }
}
