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

        int zone = PlayerProgressionManager.getZoneAt(player.getPos());

        if (zone >= 1) AdvancementHelper.grant(player, "into_the_shallows");

        // Brave the Waves: standing in zone 2 while holding a Seafarer's Token
        if (zone >= 2 && PlayerProgressionManager.hasSeafarersToken(player)) {
            AdvancementHelper.grant(player, "brave_the_waves");
        }

        // Full pirate kit: all four armour pieces equipped
        if (player.getEquippedStack(EquipmentSlot.HEAD).isOf(ModItems.PIRATE_HELMET)
                && player.getEquippedStack(EquipmentSlot.CHEST).isOf(ModItems.PIRATE_CHESTPLATE)
                && player.getEquippedStack(EquipmentSlot.LEGS).isOf(ModItems.PIRATE_LEGGINGS)
                && player.getEquippedStack(EquipmentSlot.FEET).isOf(ModItems.PIRATE_BOOTS)) {
            AdvancementHelper.grant(player, "full_pirate_kit");
        }
    }
}
