package net.get900.pixelpirates.item;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.util.function.Supplier;

public enum ModArmorMaterials implements ArmorMaterial {
    PIRATE_ARMOR("pirate_armor", 15, new int[]{2, 3, 4, 2}, 20,
            SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 0, 0,
            () -> Ingredient.ofItems(ModItems.TATTERED_CLOTH)),
    // Ring 1 — stitched salvage cloth
    CASTAWAY("castaway", 8, new int[]{1, 2, 3, 1}, 8,
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0, 0,
            () -> Ingredient.ofItems(ModItems.TATTERED_CLOTH)),
    // Ring 2 — pressed navy steel + wool coat
    NAVY_OFFICER("navy_officer", 16, new int[]{1, 3, 4, 2}, 12,
            SoundEvents.ITEM_ARMOR_EQUIP_IRON, 0, 0,
            () -> Ingredient.ofItems(net.minecraft.item.Items.IRON_INGOT)),
    // Ring 3 — gold-trimmed corsair leathers
    CORSAIR("corsair", 20, new int[]{2, 4, 5, 2}, 15,
            SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 1.0f, 0,
            () -> Ingredient.ofItems(ModItems.PIRATE_COIN)),
    // Volcanic isles — ember-hardened plate
    ASHEN("ashen", 22, new int[]{2, 5, 6, 2}, 15,
            SoundEvents.ITEM_ARMOR_EQUIP_IRON, 1.0f, 0,
            () -> Ingredient.ofItems(ModItems.VOLCANIC_EMBER)),
    // Ring 4 — lacquered cursed bone
    CURSED_BONE("cursed_bone", 26, new int[]{3, 5, 6, 3}, 20,
            SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 1.5f, 0,
            () -> Ingredient.ofItems(ModItems.CURSED_BONE)),
    // Ring 5 — abyssal kraken scale
    KRAKEN_SCALE("kraken_scale", 30, new int[]{3, 5, 7, 3}, 18,
            SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, 2.0f, 0.05f,
            () -> Ingredient.ofItems(ModItems.KRAKEN_SCALE)),

    // ---- BOSS SETS (item/BossArmor): from boss hoards + kills, one per pair of chain bosses.
    // Toughness and knockback resistance are PER PIECE (x4 for the set).
    // I  Rackham + Serpent: 15 armor, tough 1, kb 0.05/pc (= the design's +0.2 set knockback resistance)
    POWDER_MONKEY("powder_monkey", 22, new int[]{2, 3, 5, 2}, 14,
            SoundEvents.ITEM_ARMOR_EQUIP_LEATHER, 0.5f, 0.05f,
            () -> Ingredient.ofItems(net.minecraft.item.Items.GUNPOWDER)),
    // II Warlord + Ghost Captain: 19 armor, tough 2
    FORGEGUARD("forgeguard", 28, new int[]{2, 5, 6, 3}, 14,
            SoundEvents.ITEM_ARMOR_EQUIP_IRON, 1.5f, 0,
            () -> Ingredient.ofItems(ModItems.VOLCANIC_EMBER)),
    // III King + Bloodfin: 21 armor, tough 2.5
    TIDECOURT("tidecourt", 32, new int[]{3, 6, 7, 3}, 16,
            SoundEvents.ITEM_ARMOR_EQUIP_CHAIN, 2.0f, 0,
            () -> Ingredient.ofItems(net.minecraft.item.Items.PRISMARINE_CRYSTALS)),
    // IV Kraken + Revenant: 22 armor, tough 3, kb 0.1
    GALLOWBREAKER("gallowbreaker", 36, new int[]{3, 6, 8, 3}, 16,
            SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, 2.5f, 0.1f,
            () -> Ingredient.ofItems(ModItems.KRAKEN_SCALE)),
    // V Heart + Leviathan: 23 armor, tough 3.5, kb 0.15
    THALASSAR("thalassar", 40, new int[]{3, 6, 8, 3}, 18,
            SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, 3.0f, 0.1f,
            () -> Ingredient.ofItems(ModItems.LEVIATHAN_SCALE));

    private static final int[] BASE_DURABILITY = {13, 15, 16, 11}; // boots, leggings, chestplate, helmet

    private final String name;
    private final int durabilityMultiplier;
    private final int[] protectionAmounts;
    private final int enchantability;
    private final SoundEvent equipSound;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repairIngredientSupplier;

    ModArmorMaterials(String name, int durabilityMultiplier, int[] protectionAmounts, int enchantability,
                      SoundEvent equipSound, float toughness, float knockbackResistance,
                      Supplier<Ingredient> repairIngredientSupplier) {
        this.name = name;
        this.durabilityMultiplier = durabilityMultiplier;
        this.protectionAmounts = protectionAmounts;
        this.enchantability = enchantability;
        this.equipSound = equipSound;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairIngredientSupplier = repairIngredientSupplier;
    }

    @Override
    public int getDurability(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> BASE_DURABILITY[0] * durabilityMultiplier;
            case LEGGINGS -> BASE_DURABILITY[1] * durabilityMultiplier;
            case CHESTPLATE -> BASE_DURABILITY[2] * durabilityMultiplier;
            case HELMET -> BASE_DURABILITY[3] * durabilityMultiplier;
        };
    }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return switch (type) {
            case BOOTS -> protectionAmounts[0];
            case LEGGINGS -> protectionAmounts[1];
            case CHESTPLATE -> protectionAmounts[2];
            case HELMET -> protectionAmounts[3];
        };
    }

    @Override
    public int getEnchantability() { return enchantability; }

    @Override
    public SoundEvent getEquipSound() { return equipSound; }

    @Override
    public Ingredient getRepairIngredient() { return repairIngredientSupplier.get(); }

    @Override
    public String getName() { return name; }

    @Override
    public float getToughness() { return toughness; }

    @Override
    public float getKnockbackResistance() { return knockbackResistance; }
}
