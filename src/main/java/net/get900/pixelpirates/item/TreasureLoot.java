package net.get900.pixelpirates.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Central loot pool definitions for treasure chests.
 * Add or adjust entries here — no other files need touching.
 */
public class TreasureLoot {

    public record LootEntry(Item item, int minCount, int maxCount, float chance) {
        public boolean rolls(Random rng) { return rng.nextFloat() < chance; }
        public ItemStack roll(Random rng) {
            int count = minCount + (maxCount > minCount ? rng.nextInt(maxCount - minCount + 1) : 0);
            return new ItemStack(item, count);
        }
    }

    // -----------------------------------------------------------------------
    // COMMON pool — Zone 1 chests
    // -----------------------------------------------------------------------
    public static final List<LootEntry> COMMON = List.of(
        new LootEntry(ModItems.PIRATE_COIN,     5,  15, 1.0f),
        new LootEntry(ModItems.ROPE,            2,  6,  0.8f),
        new LootEntry(ModItems.CANNON_BALL,     1,  5,  0.6f),
        new LootEntry(ModItems.TATTERED_CLOTH,  1,  3,  0.5f),
        new LootEntry(ModItems.SEAFARERS_TOKEN, 1,  1,  0.35f)
    );

    // -----------------------------------------------------------------------
    // RARE pool — Zone 2-3 chests
    // -----------------------------------------------------------------------
    public static final List<LootEntry> RARE = List.of(
        new LootEntry(ModItems.PIRATE_COIN,     15, 30, 1.0f),
        new LootEntry(ModItems.SHIP_REPAIR_KIT, 1,  3,  0.9f),
        new LootEntry(ModItems.CANNON_BALL,     5,  15, 0.8f),
        new LootEntry(ModItems.ROPE,            3,  8,  0.6f),
        new LootEntry(ModItems.DRIFTWOOD,       2,  5,  0.5f),
        new LootEntry(ModItems.KRAKEN_INK,      1,  2,  0.3f)
    );

    // -----------------------------------------------------------------------
    // LEGENDARY pool — Zone 4-5 chests
    // -----------------------------------------------------------------------
    public static final List<LootEntry> LEGENDARY = List.of(
        new LootEntry(ModItems.PIRATE_COIN,     30, 60, 1.0f),
        new LootEntry(ModItems.SHIP_REPAIR_KIT, 3,  7,  1.0f),
        new LootEntry(ModItems.CANNON_BALL,     10, 20, 0.9f),
        new LootEntry(ModItems.KRAKEN_INK,      2,  5,  0.7f),
        new LootEntry(ModItems.ROPE,            5,  12, 0.6f),
        new LootEntry(ModItems.TATTERED_CLOTH,  3,  6,  0.5f)
    );

    /** Roll the pool and return all stacks that passed their chance. */
    public static List<ItemStack> roll(List<LootEntry> pool, Random rng) {
        List<ItemStack> results = new ArrayList<>();
        for (LootEntry entry : pool) {
            if (entry.rolls(rng)) results.add(entry.roll(rng));
        }
        return results;
    }
}
