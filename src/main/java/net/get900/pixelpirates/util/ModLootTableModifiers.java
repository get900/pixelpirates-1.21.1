package net.get900.pixelpirates.util;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.util.Identifier;

/**
 * Region material drops — makes cursed_bone / kraken_scale / volcanic_ember farmable
 * from the mobs that own their region, instead of only via conversion recipes.
 */
public class ModLootTableModifiers {
    private static final Identifier DROWNED_ID = EntityType.DROWNED.getLootTableId();
    private static final Identifier GUARDIAN_ID = EntityType.GUARDIAN.getLootTableId();
    private static final Identifier ELDER_GUARDIAN_ID = EntityType.ELDER_GUARDIAN.getLootTableId();
    private static final Identifier MAGMA_CUBE_ID = EntityType.MAGMA_CUBE.getLootTableId();

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            if (DROWNED_ID.equals(id)) {
                // Ghost-seas workhorse mob — 10% chance of a cursed bone
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(0.10f))
                        .with(ItemEntry.builder(ModItems.CURSED_BONE)));
            } else if (GUARDIAN_ID.equals(id)) {
                // Reef + abyss guardians — 20% chance of a kraken scale
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(0.20f))
                        .with(ItemEntry.builder(ModItems.KRAKEN_SCALE)));
            } else if (ELDER_GUARDIAN_ID.equals(id)) {
                // Mini-boss — guaranteed 2-4 kraken scales
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .with(ItemEntry.builder(ModItems.KRAKEN_SCALE)
                                .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(2.0f, 4.0f)))));
            } else if (MAGMA_CUBE_ID.equals(id)) {
                // Volcanic isles — 15% chance of a volcanic ember
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(0.15f))
                        .with(ItemEntry.builder(ModItems.VOLCANIC_EMBER)));
            }
        });
    }
}
