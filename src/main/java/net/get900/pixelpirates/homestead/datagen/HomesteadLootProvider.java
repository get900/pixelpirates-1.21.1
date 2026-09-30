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
        for (Block b : HomesteadBlocks.OWNED) if (!done.contains(b) && b.asItem() != net.minecraft.item.Items.AIR) addDrop(b);
    }

    private void crop(Set<Block> done, Block crop, Item product, Item seeds) {
        done.add(crop);
        addDrop(crop, cropDrops(crop, product, seeds, BlockStatePropertyLootCondition.builder(crop)
                .properties(StatePredicate.Builder.create().exactMatch(CropBlock.AGE, 7))));
    }

    // helpers the HomesteadLoot hooks can reach
    public void drop(Block b, net.minecraft.loot.LootTable.Builder t) { addDrop(b, t); }

    public net.minecraft.loot.LootTable.Builder self(Block b) { return drops(b); }

    public net.minecraft.loot.LootTable.Builder nothing() { return net.minecraft.loot.LootTable.builder(); }
}
