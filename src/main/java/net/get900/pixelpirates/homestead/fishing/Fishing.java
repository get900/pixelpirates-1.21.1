package net.get900.pixelpirates.homestead.fishing;

import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.get900.pixelpirates.world.biome.ModBiomeKeys;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.AnyOfLootCondition;
import net.minecraft.loot.condition.InvertedLootCondition;
import net.minecraft.loot.condition.LocationCheckLootCondition;
import net.minecraft.loot.condition.LootCondition;
import net.minecraft.loot.condition.MatchToolLootCondition;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LootTableEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.predicate.entity.LocationPredicate;
import net.minecraft.predicate.item.ItemPredicate;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;

import java.util.List;

/**
 * FISHING (#13): every ring zone has its own fish, hooked through biome-conditioned entries added to the vanilla fish
 * table (vanilla fish stay in the pool). Rare TROPHY fish mount on the wall. The SALVAGE HOOK (#9) swaps the whole
 * fishing roll for the salvage table. Nets and lobster pots draw from the same tables (see {@link #catchFish}).
 */
public final class Fishing {
    private Fishing() {}

    static final Identifier FISH = new Identifier("minecraft", "gameplay/fishing/fish");
    static final Identifier FISHING = new Identifier("minecraft", "gameplay/fishing");
    public static final Identifier SALVAGE = new Identifier("pixelpirates", "gameplay/salvage");
    public static final Identifier LOBSTER_POT = new Identifier("pixelpirates", "gameplay/lobster_pot");

    @SafeVarargs
    static LootCondition.Builder inBiomes(RegistryKey<Biome>... keys) {
        LootCondition.Builder[] c = new LootCondition.Builder[keys.length];
        for (int i = 0; i < keys.length; i++) c[i] = LocationCheckLootCondition.builder(LocationPredicate.Builder.create().biome(keys[i]));
        return AnyOfLootCondition.builder(c);
    }

    static ItemEntry.Builder<?> fish(ItemConvertible item, int weight, LootCondition.Builder where) {
        return (ItemEntry.Builder<?>) ItemEntry.builder(item).weight(weight).conditionally(where);
    }

    public static void register() {
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, table, source) -> {
            if (!source.isBuiltin()) return;
            if (FISH.equals(id)) {
                var p1 = inBiomes(ModBiomeKeys.SPAWN_ISLAND, ModBiomeKeys.TEMPERATE_SHALLOWS, ModBiomeKeys.ISLAND_THICKETS, ModBiomeKeys.OPEN_OCEAN);
                var p2 = inBiomes(ModBiomeKeys.CORAL_BAY, ModBiomeKeys.REEF_EDGE, ModBiomeKeys.SIREN_SEA);
                var p3 = inBiomes(ModBiomeKeys.ASH_REEF, ModBiomeKeys.BOILING_BASIN, ModBiomeKeys.MAGMA_SEA);
                var p4 = inBiomes(ModBiomeKeys.PHANTOM_WAKE, ModBiomeKeys.SHIPGRAVE_DEPTHS, ModBiomeKeys.DROWNED_TRENCH);
                var p5 = inBiomes(ModBiomeKeys.ABYSSAL_RINGS, ModBiomeKeys.PILLAR_SEA, ModBiomeKeys.MAW_DEPTHS);
                table.modifyPools(pool -> pool
                        .with(fish(HomesteadItems.PARROTFISH, 40, p1)).with(fish(HomesteadItems.RED_SNAPPER, 35, p1)).with(fish(HomesteadItems.MAHI_MAHI, 25, p1))
                        // the salted swimmer had no source at all - a common catch in the starter and reef seas
                        .with(fish(net.get900.pixelpirates.item.ModItems.RAW_SALTED_SWIMMER, 35, p1)).with(fish(net.get900.pixelpirates.item.ModItems.RAW_SALTED_SWIMMER, 20, p2))
                        .with(fish(HomesteadItems.LIONFISH, 40, p2)).with(fish(HomesteadItems.MOONFISH, 30, p2))
                        .with(fish(HomesteadItems.EMBERFIN, 40, p3)).with(fish(HomesteadItems.LAVA_EEL, 30, p3))
                        .with(fish(HomesteadItems.GHOSTFIN, 40, p4)).with(fish(HomesteadItems.BONEFISH, 30, p4))
                        .with(fish(HomesteadItems.ANGLERFRY, 40, p5)).with(fish(HomesteadItems.VOIDFIN, 30, p5))
                        .with(fish(HomesteadBlocks.GOLDEN_MARLIN_TROPHY, 2, inBiomes(ModBiomeKeys.OPEN_OCEAN, ModBiomeKeys.CORAL_BAY, ModBiomeKeys.REEF_EDGE)))
                        .with(fish(HomesteadBlocks.GHOST_SWORDFISH_TROPHY, 2, p4))
                        .with(fish(HomesteadBlocks.COELACANTH_TROPHY, 2, p5)));
            } else if (FISHING.equals(id)) {
                // the salvage hook fishes up wreckage instead of fish
                LootCondition.Builder hook = MatchToolLootCondition.builder(ItemPredicate.Builder.create().items(HomesteadItems.SALVAGE_HOOK));
                table.modifyPools(pool -> pool.conditionally(InvertedLootCondition.builder(hook)));
                table.pool(LootPool.builder().rolls(ConstantLootNumberProvider.create(1)).conditionally(hook).with(LootTableEntry.builder(SALVAGE)));
            }
        });
    }

    /** One roll of a fishing-type table at {@code pos} (biome conditions apply) - for nets, pots and tests. */
    public static List<ItemStack> catchFish(ServerWorld world, BlockPos pos, Identifier tableId) {
        LootTable t = world.getServer().getLootManager().getLootTable(tableId);
        var params = new LootContextParameterSet.Builder(world).add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos))
                .add(LootContextParameters.TOOL, new ItemStack(Items.FISHING_ROD)).build(LootContextTypes.FISHING);
        return t.generateLoot(params);
    }

    public static boolean isFish(Item i) { return HomesteadItems.FISH.contains(i); }
}
