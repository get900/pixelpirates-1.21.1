package net.get900.pixelpirates.item;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Spawn eggs for the mod's mobs (there were none before). Registered from onInitialize AFTER
 * ModEntities so the entity types exist; item models are the vanilla template_spawn_egg
 * (datagen), so no textures are needed - just the two colours.
 */
public final class ModSpawnEggs {
    private ModSpawnEggs() {}

    public static final List<Item> ALL = new ArrayList<>();

    public static Item SIREN, CORAL_JELLY, MAGMA_BRUTE, MIMIC, ABYSSAL_ANGLER;

    public static void register() {
        SIREN = egg("siren", ModEntities.SIREN, 0x2F8F86, 0xE0B84A);
        CORAL_JELLY = egg("coral_jelly", ModEntities.CORAL_JELLY, 0x249C96, 0xFFD84A);
        MAGMA_BRUTE = egg("magma_brute", ModEntities.MAGMA_BRUTE, 0x2A2727, 0xFF7A1E);
        MIMIC = egg("mimic", ModEntities.MIMIC, 0x9A6A32, 0x7FF6FF);
        ABYSSAL_ANGLER = egg("abyssal_angler", ModEntities.ABYSSAL_ANGLER, 0x22343A, 0xFFD84A);
        egg("shark", ModEntities.SHARK, 0x5A6E7E, 0xD8DDE2);
        egg("chest_crab", ModEntities.CHEST_CRAB, 0x8B5A2B, 0xD9B04A);
        egg("lava_crab", ModEntities.LAVA_CRAB, 0x3A1A10, 0xFF6A1A);
        egg("castaway", ModEntities.CASTAWAY, 0xC8A070, 0x3A5A7A);
        egg("cursed_monkey", ModEntities.CURSED_MONKEY, 0x4A3526, 0x6BFF6B);
        egg("map_merchant", ModEntities.MAP_MERCHANT, 0x1F2A4A, 0x5FD0E0);
        egg("port_trader", net.get900.pixelpirates.homestead.HomesteadEntities.PORT_TRADER, 0x223A50, 0xC8A040);

        // every data-driven mob from MobSpecs (bosses included - handy for testing fights)
        net.get900.pixelpirates.entity.mob.ModMobs.TYPES.forEach((id, type) -> {
            var spec = net.get900.pixelpirates.entity.mob.MobSpecs.get(id);
            egg(id, type, spec.eggPrimary(), spec.eggSecondary());
        });

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.SPAWN_EGGS).register(entries -> ALL.forEach(entries::add));
    }

    private static Item egg(String mob, EntityType<? extends MobEntity> type, int primary, int secondary) {
        Item item = Registry.register(Registries.ITEM, new Identifier(PixelPirates.MOD_ID, mob + "_spawn_egg"),
                new SpawnEggItem(type, primary, secondary, new Item.Settings()));
        ALL.add(item);
        return item;
    }
}
