package net.get900.pixelpirates.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.condition.TableBonusLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LeafEntry;
import net.minecraft.loot.function.ApplyBonusLootFunction;
import net.minecraft.loot.function.SetCountLootFunction;
import net.minecraft.loot.provider.number.UniformLootNumberProvider;
import net.minecraft.registry.Registries;

import java.util.HashSet;
import java.util.Set;

public class ModLootTableProvider extends FabricBlockLootTableProvider {
    private static final float[] SAPLING_CHANCE = {0.05f, 0.0625f, 0.083333336f, 0.1f};
    private static final float[] FRUIT_CHANCE = {0.02f, 0.03f, 0.045f, 0.06f};

    public ModLootTableProvider(FabricDataOutput dataOutput) {
        super(dataOutput);
    }

    @Override
    public void generate() {
        Set<Block> custom = new HashSet<>();

        custom.add(ModBlocks.DRIFTWOOD_BLOCK);
        addDrop(ModBlocks.DRIFTWOOD_BLOCK, multipleOreDrops(ModBlocks.DRIFTWOOD_BLOCK, ModItems.DRIFTWOOD, 4, 8));
        custom.add(ModBlocks.BANANA_BLOCK);
        addDrop(ModBlocks.BANANA_BLOCK, createSingleItemDrop(ModItems.BANANA));
        custom.add(ModBlocks.COCONUT_BLOCK);
        addDrop(ModBlocks.COCONUT_BLOCK, createSingleItemDrop(ModItems.COCONUT));

        // Leaves: only drop themselves with shears/silk touch; otherwise sticks + sapling/fruit.
        custom.add(ModBlocks.SHOREWOOD_LEAVES);
        addDrop(ModBlocks.SHOREWOOD_LEAVES, leavesDrops(ModBlocks.SHOREWOOD_LEAVES, ModBlocks.SHOREWOOD_SAPLING, SAPLING_CHANCE));
        custom.add(ModBlocks.PALM_LEAVES);
        addDrop(ModBlocks.PALM_LEAVES, fruitLeaves(ModBlocks.PALM_LEAVES, ModItems.COCONUT));
        custom.add(ModBlocks.TIDEWOOD_LEAVES);
        addDrop(ModBlocks.TIDEWOOD_LEAVES, fruitLeaves(ModBlocks.TIDEWOOD_LEAVES, Items.STICK));
        custom.add(ModBlocks.EMBER_LEAVES);
        addDrop(ModBlocks.EMBER_LEAVES, fruitLeaves(ModBlocks.EMBER_LEAVES, ModItems.VOLCANIC_EMBER));
        custom.add(ModBlocks.WISP_LEAVES);
        addDrop(ModBlocks.WISP_LEAVES, fruitLeaves(ModBlocks.WISP_LEAVES, Items.STICK));
        custom.add(ModBlocks.VOIDBLOOM_LEAVES);
        addDrop(ModBlocks.VOIDBLOOM_LEAVES, fruitLeaves(ModBlocks.VOIDBLOOM_LEAVES, Items.STICK));

        custom.add(ModBlocks.POTTED_SHOREWOOD_SAPLING);
        addPottedPlantDrops(ModBlocks.POTTED_SHOREWOOD_SAPLING);

        // Fort cannons are bolted to the wall - you salvage a few balls; blast rubble drops nothing.
        custom.add(ModBlocks.FORT_CANNON);
        addDrop(ModBlocks.FORT_CANNON, drops(ModBlocks.FORT_CANNON, ModItems.CANNON_BALL, UniformLootNumberProvider.create(1, 3)));
        custom.add(ModBlocks.BLAST_RUBBLE);
        custom.add(ModBlocks.SERPENT_WARD);
        custom.add(ModBlocks.QUENCH_VALVE);
        custom.add(ModBlocks.TIDE_SLUICE);
        custom.add(ModBlocks.HARPOON_WINCH);
        custom.add(ModBlocks.MANACLE_ANCHOR);
        addDrop(ModBlocks.HARPOON_WINCH);
        custom.add(ModBlocks.PHANTOM_BUOY);
        custom.add(ModBlocks.HEART_VALVE);
        custom.add(ModBlocks.GALVANIC_PYLON);
        custom.add(ModBlocks.RIFT_SEAL);
        custom.add(ModBlocks.ANCHOR_WINCH);
        custom.add(ModBlocks.TIDE_BELL);
        custom.add(ModBlocks.BANE_BALLISTA);
        custom.add(ModBlocks.WATCHERS_HORN);

        // Everything else in the mod drops itself. Before this, ~70 decorative blocks
        // (shroud/volcanic/ethereal/pirate sets, water light, ship helm) had no loot table at all.
        for (Block block : Registries.BLOCK) {
            if (!Registries.BLOCK.getId(block).getNamespace().equals(PixelPirates.MOD_ID)) continue;
            if (custom.contains(block)) continue;
            if (net.get900.pixelpirates.homestead.HomesteadBlocks.OWNED.contains(block)) continue;   // HomesteadLootProvider
            addDrop(block);
        }
    }

    /** Silk/shears -> the leaves; otherwise 1-2 sticks at vanilla odds plus a rare bonus item. */
    private LootTable.Builder fruitLeaves(Block leaves, ItemConvertible bonus) {
        return dropsWithSilkTouchOrShears(leaves, applyExplosionDecay(leaves,
                        ItemEntry.builder(Items.STICK)
                                .apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(1.0f, 2.0f))))
                        .conditionally(TableBonusLootCondition.builder(Enchantments.FORTUNE, 0.02f, 0.022222223f, 0.025f, 0.033333335f, 0.1f)))
                .pool(LootPool.builder()
                        .conditionally(WITHOUT_SILK_TOUCH_NOR_SHEARS)
                        .with(addSurvivesExplosionCondition(leaves, ItemEntry.builder(bonus))
                                .conditionally(TableBonusLootCondition.builder(Enchantments.FORTUNE, FRUIT_CHANCE))));
    }

    public LootTable.Builder multipleOreDrops(Block drop, Item item, float minDrops, float maxDrops) {
        return this.dropsWithSilkTouch(drop, this.applyExplosionDecay(drop, ((LeafEntry.Builder<?>)
                ItemEntry.builder(item).apply(SetCountLootFunction.builder(UniformLootNumberProvider.create(minDrops, maxDrops))))
                .apply(ApplyBonusLootFunction.oreDrops(Enchantments.FORTUNE))));
    }

    public LootTable.Builder createSingleItemDrop(Item item) {
        LootPool.Builder pool = LootPool.builder().rolls(UniformLootNumberProvider.create(1.0f, 1.0f)).with(ItemEntry.builder(item));
        return LootTable.builder().pool(pool);
    }
}
