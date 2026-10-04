package net.get900.pixelpirates.item;

import net.get900.pixelpirates.item.food.PirateFoods;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Buried treasure from TREASURE MAPS - one tier per stage of the game (Materials & Gear Ladder 6.2, 2026-10-01):
 * common = phase 1, rare = phases 2-3, legendary = phases 4-5 (the dig sites are distance-gated the same way).
 * Every entry rolls on its own; an entry with several items hands out ONE of them.
 */
public class TreasureLoot {

    public record LootEntry(List<Item> items, int minCount, int maxCount, float chance) {
        public LootEntry(Item item, int minCount, int maxCount, float chance) { this(List.of(item), minCount, maxCount, chance); }

        public boolean rolls(Random rng) { return rng.nextFloat() < chance; }

        public ItemStack roll(Random rng) {
            int count = minCount + (maxCount > minCount ? rng.nextInt(maxCount - minCount + 1) : 0);
            return new ItemStack(items.get(rng.nextInt(items.size())), count);
        }
    }

    private static LootEntry oneOf(float chance, Item... items) { return new LootEntry(List.of(items), 1, 1, chance); }

    private static Item[] bossDishes() {
        return new Item[]{PirateFoods.item("rackhams_reserve"), PirateFoods.item("serpent_steak"), PirateFoods.item("molten_core_chili"),
                PirateFoods.item("phantom_hardtack"), PirateFoods.item("royal_tide_feast"), PirateFoods.item("bloodfin_fillet"),
                PirateFoods.item("kraken_platter"), PirateFoods.item("last_meal")};
    }

    // ----------------------------------------------------------------------- COMMON - phase 1
    public static final List<LootEntry> COMMON = List.of(
        new LootEntry(ModItems.COIN,            15, 40, 1.0f),
        new LootEntry(ModItems.PIRATE_COIN,     3,  10, 0.8f),
        new LootEntry(ModItems.TATTERED_CLOTH,  2,  5,  0.6f),
        new LootEntry(ModItems.CRAB_SHELL,      2,  4,  0.5f),
        new LootEntry(ModItems.DRIFTWOOD,       2,  5,  0.5f),
        new LootEntry(Items.IRON_INGOT,         1,  3,  0.6f),
        new LootEntry(ModItems.DYNAMITE,        1,  3,  0.4f),
        new LootEntry(List.of(ModItems.HARDTACK, ModItems.BANANA_BREAD, ModItems.COOKED_SHARK_MEAT), 2, 5, 0.6f),
        oneOf(0.10f, ModItems.CASTAWAY_HELMET, ModItems.CASTAWAY_CHESTPLATE, ModItems.CASTAWAY_LEGGINGS, ModItems.CASTAWAY_BOOTS,
                ModItems.NAVY_OFFICER_HELMET, ModItems.NAVY_OFFICER_CHESTPLATE, ModItems.NAVY_OFFICER_LEGGINGS, ModItems.NAVY_OFFICER_BOOTS),
        oneOf(0.08f, ModItems.MARLINSPIKE, ModItems.BOARDING_SABRE, ModItems.DAGGER, ModItems.CUTLASS),
        oneOf(0.05f, net.get900.pixelpirates.homestead.HomesteadBlocks.SHIP_IN_BOTTLE_SLOOP.asItem(), net.get900.pixelpirates.homestead.HomesteadBlocks.SHIP_IN_BOTTLE_BRIG.asItem()),
        new LootEntry(net.get900.pixelpirates.homestead.HomesteadItems.COMMON_STRONGBOX, 1, 1, 0.15f)
    );

    // ----------------------------------------------------------------------- RARE - phases 2-3
    public static final List<LootEntry> RARE = List.of(
        new LootEntry(ModItems.COIN,            40, 90, 1.0f),
        new LootEntry(ModItems.PIRATE_COIN,     10, 25, 0.9f),
        new LootEntry(List.of(ModItems.SIREN_SCALE, ModItems.REEF_PEARL), 2, 5, 0.7f),
        new LootEntry(List.of(ModItems.VOLCANIC_EMBER, ModItems.BRIMSTONE, ModItems.OBSIDIAN_SHARD), 2, 5, 0.7f),
        new LootEntry(ModItems.SHIP_REPAIR_KIT, 1,  3,  0.6f),
        new LootEntry(ModItems.CANNON_BALL,     5,  15, 0.5f),
        oneOf(0.10f, ModItems.CORSAIR_HELMET, ModItems.CORSAIR_CHESTPLATE, ModItems.CORSAIR_LEGGINGS, ModItems.CORSAIR_BOOTS,
                ModItems.ASHEN_HELMET, ModItems.ASHEN_CHESTPLATE, ModItems.ASHEN_LEGGINGS, ModItems.ASHEN_BOOTS),
        oneOf(0.08f, ModItems.NAVAL_RAPIER, ModItems.OFFICERS_SABRE, ModItems.CORSAIR_CUTLASS, ModItems.BOARDING_PIKE, ModItems.EMBERBRAND),
        new LootEntry(List.of(bossDishes()), 1, 1, 0.04f),
        oneOf(0.06f, net.get900.pixelpirates.homestead.HomesteadBlocks.SHIP_IN_BOTTLE_BRIG.asItem(), net.get900.pixelpirates.homestead.HomesteadBlocks.SHIP_IN_BOTTLE_GALLEON.asItem()),
        new LootEntry(net.get900.pixelpirates.homestead.HomesteadItems.RARE_STRONGBOX, 1, 1, 0.2f)
    );

    // ----------------------------------------------------------------------- LEGENDARY - phases 4-5
    public static final List<LootEntry> LEGENDARY = List.of(
        new LootEntry(ModItems.COIN,            90, 180, 1.0f),
        new LootEntry(ModItems.PIRATE_COIN,     25, 50, 1.0f),
        new LootEntry(List.of(ModItems.CURSED_BONE, ModItems.ECTOPLASM), 2, 5, 0.7f),
        new LootEntry(List.of(ModItems.KRAKEN_SCALE, ModItems.ABYSSAL_PEARL), 2, 4, 0.7f),
        new LootEntry(ModItems.SHIP_REPAIR_KIT, 2,  5,  0.8f),
        new LootEntry(ModItems.LOST_SOUL,       1,  1,  0.2f),
        oneOf(0.10f, ModItems.CURSED_BONE_HELMET, ModItems.CURSED_BONE_CHESTPLATE, ModItems.CURSED_BONE_LEGGINGS, ModItems.CURSED_BONE_BOOTS,
                ModItems.KRAKEN_SCALE_HELMET, ModItems.KRAKEN_SCALE_CHESTPLATE, ModItems.KRAKEN_SCALE_LEGGINGS, ModItems.KRAKEN_SCALE_BOOTS),
        oneOf(0.08f, ModItems.SOULRENDER, ModItems.WRAITHBLADE, ModItems.KRAKEN_FANG, ModItems.STORMCALLER, ModItems.ABYSSAL_HARPOON),
        new LootEntry(List.of(bossDishes()), 1, 1, 0.06f),
        new LootEntry(ModItems.SEAFARERS_TOKEN, 1,  1,  0.03f),
        oneOf(0.07f, net.get900.pixelpirates.homestead.HomesteadBlocks.SHIP_IN_BOTTLE_GALLEON.asItem(), net.get900.pixelpirates.homestead.HomesteadBlocks.SHIP_IN_BOTTLE_GHOST.asItem()),      // the ghost ship: loot only
        new LootEntry(net.get900.pixelpirates.homestead.HomesteadItems.LEGENDARY_STRONGBOX, 1, 1, 0.25f)
    );

    /** Roll the pool and return all stacks that passed their chance. */
    public static List<ItemStack> roll(List<LootEntry> pool, Random rng) { return roll(pool, rng, java.util.Set.of()); }

    /** ownedParrots: the opening player's parrot types (ParrotCollection) - a crate never holds one they already own. */
    public static List<ItemStack> roll(List<LootEntry> pool, Random rng, java.util.Set<String> ownedParrots) {
        List<ItemStack> results = new ArrayList<>();
        for (LootEntry entry : pool) {
            if (entry.rolls(rng)) results.add(entry.roll(rng));
        }
        // PARROT TYPES: rare+ parrots in a crate (with the aviary, the only places the top tiers turn up)
        var crate = net.get900.pixelpirates.homestead.HomesteadItems.PARROT_CRATE;
        var tier = pool == RARE ? net.get900.pixelpirates.homestead.parrot.ParrotTypes.Tier.RARE
                : pool == LEGENDARY ? net.get900.pixelpirates.homestead.parrot.ParrotTypes.Tier.VERY_RARE : null;
        if (tier != null && rng.nextFloat() < (pool == RARE ? 0.05f : 0.08f)) {
            var t = net.get900.pixelpirates.homestead.parrot.ParrotTypes.roll(tier, rng::nextInt, ownedParrots);
            if (t != null) results.add(net.get900.pixelpirates.homestead.parrot.ParrotCrateItem.of(crate, t));
        }
        return results;
    }
}
