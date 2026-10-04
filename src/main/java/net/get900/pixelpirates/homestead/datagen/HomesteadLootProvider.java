package net.get900.pixelpirates.homestead.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricBlockLootTableProvider;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.minecraft.block.Block;
import net.minecraft.block.CropBlock;
import net.minecraft.item.Item;
import net.minecraft.loot.condition.BlockStatePropertyLootCondition;
import net.minecraft.predicate.StatePredicate;

import java.util.HashSet;
import java.util.Set;

/** Block loot for the homestead set (the main provider skips every block in HomesteadBlocks.OWNED). */
public class HomesteadLootProvider extends FabricBlockLootTableProvider {
    public HomesteadLootProvider(FabricDataOutput output) { super(output); }

    @Override
    public String getName() { return "Homestead Block Loot"; }

    @Override
    public void generate() {
        Set<Block> done = new HashSet<>();
        crop(done, HomesteadBlocks.PINEAPPLE_CROP, HomesteadItems.PINEAPPLE, HomesteadItems.PINEAPPLE_CROWN);
        crop(done, HomesteadBlocks.LIME_CROP, HomesteadItems.LIME, HomesteadItems.LIME_SEEDS);
        crop(done, HomesteadBlocks.CHILI_CROP, HomesteadItems.CHILI_PEPPER, HomesteadItems.CHILI_SEEDS);
        HomesteadLoot.custom(this, done);
        stacked(done, HomesteadBlocks.TANKARD, net.get900.pixelpirates.homestead.tavern.TavernDecor.COUNT3, 3);
        stacked(done, HomesteadBlocks.SPIRIT_BOTTLES, net.get900.pixelpirates.homestead.tavern.TavernDecor.COUNT4, 4);
        for (Block b : HomesteadBlocks.OWNED) if (!done.contains(b) && b.asItem() != net.minecraft.item.Items.AIR) addDrop(b);
    }

    private void crop(Set<Block> done, Block crop, Item product, Item seeds) {
        done.add(crop);
        addDrop(crop, cropDrops(crop, product, seeds, BlockStatePropertyLootCondition.builder(crop)
                .properties(StatePredicate.Builder.create().exactMatch(CropBlock.AGE, 7))));
    }

    /** Tankards / bottles: drop as many as are stacked on the block. */
    private void stacked(Set<Block> done, Block b, net.minecraft.state.property.IntProperty count, int max) {
        done.add(b);
        var entry = net.minecraft.loot.entry.ItemEntry.builder(b);
        for (int n = 2; n <= max; n++)
            entry.apply(net.minecraft.loot.function.SetCountLootFunction.builder(net.minecraft.loot.provider.number.ConstantLootNumberProvider.create(n))
                    .conditionally(BlockStatePropertyLootCondition.builder(b).properties(StatePredicate.Builder.create().exactMatch(count, n))));
        addDrop(b, net.minecraft.loot.LootTable.builder().pool(net.minecraft.loot.LootPool.builder()
                .rolls(net.minecraft.loot.provider.number.ConstantLootNumberProvider.create(1)).with(applyExplosionDecay(b, entry))));
    }

    // helpers the HomesteadLoot hooks can reach
    public void drop(Block b, net.minecraft.loot.LootTable.Builder t) { addDrop(b, t); }

    public net.minecraft.loot.LootTable.Builder self(Block b) { return drops(b); }

    public net.minecraft.loot.LootTable.Builder nothing() { return net.minecraft.loot.LootTable.builder(); }
}
