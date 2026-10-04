package net.get900.pixelpirates.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.util.AdvancementHelper;
import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.server.network.ServerPlayerEntity;

public class ZoneAdvancementManager {

    public static void check(ServerPlayerEntity player) {
        if (!player.getWorld().getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD)) return;

        // Always grant root on dimension entry
        AdvancementHelper.grant(player, "root");

        // Full pirate kit: all four armour pieces equipped
        if (player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.PIRATE_HELMET)
                && player.getEquippedStack(EquipmentSlot.CHEST).isOf(ModItems.PIRATE_CHESTPLATE)
                && player.getEquippedStack(EquipmentSlot.LEGS).isOf(ModItems.PIRATE_LEGGINGS)
                && player.getEquippedStack(EquipmentSlot.FEET).isOf(ModItems.PIRATE_BOOTS)) {
            AdvancementHelper.grant(player, "full_pirate_kit");
        }
    }
}
