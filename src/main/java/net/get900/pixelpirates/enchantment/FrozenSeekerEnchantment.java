package net.get900.pixelpirates.enchantment;

import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.entity.EquipmentSlot;

public class FrozenSeekerEnchantment extends Enchantment {

    public FrozenSeekerEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentTarget.ARMOR_HEAD, new EquipmentSlot[]{EquipmentSlot.HEAD});
    }

    @Override
    public int getMinPower(int level) {
        return 10 + (level - 1) * 20;
    }

    @Override
    public int getMaxPower(int level) {
        return getMinPower(level) + 20;
    }

    @Override
    public int getMaxLevel() {
        return 10;            // 2026-10-01: found on zone 3/4 chest headgear (FrozenSeekerLootFunction); each level = 10% protection
    }

    @Override
    public boolean isTreasure() {
        return true;
    }
}
