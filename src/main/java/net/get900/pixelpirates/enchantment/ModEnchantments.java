package net.get900.pixelpirates.enchantment;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.LivingEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;

public class ModEnchantments {

    public static final Enchantment FROZEN_SEEKER = Registry.register(
            Registries.ENCHANTMENT,
            PixelPirates.id("frozen_seeker"),
            new FrozenSeekerEnchantment()
    );

    public static void register() {
        // triggers static field initialization
    }

    public static int getFrozenSeekerLevel(LivingEntity entity) {
        return EnchantmentHelper.getEquipmentLevel(FROZEN_SEEKER, entity);
    }


}
