package net.get900.pixelpirates.homestead.datagen;

import net.minecraft.block.Block;

import java.util.Set;

/** Per-feature block loot overrides (added to as features land). */
final class HomesteadLoot {
    private HomesteadLoot() {}

    static void custom(HomesteadLootProvider p, Set<Block> done) {
        // the 3x2 bounty board drops itself once, from its master part (BountyBoardBlock.MASTER)
        Block board = net.get900.pixelpirates.homestead.HomesteadBlocks.BOUNTY_BOARD;
        p.drop(board, net.minecraft.loot.LootTable.builder().pool(net.minecraft.loot.LootPool.builder()
                .rolls(net.minecraft.loot.provider.number.ConstantLootNumberProvider.create(1))
                .with(net.minecraft.loot.entry.ItemEntry.builder(board))
                .conditionally(net.minecraft.loot.condition.BlockStatePropertyLootCondition.builder(board)
                        .properties(net.minecraft.predicate.StatePredicate.Builder.create()
                                .exactMatch(net.get900.pixelpirates.homestead.bounty.BountyBoardBlock.PART, net.get900.pixelpirates.homestead.bounty.BountyBoardBlock.MASTER)))
                .conditionally(net.minecraft.loot.condition.SurvivesExplosionLootCondition.builder())));
        done.add(board);
        // hanging rope drops itself only when hand-placed; a thrown (DEPLOYED) line reels back as rope instead (RopeItem)
        Block rope = net.get900.pixelpirates.homestead.HomesteadBlocks.HANGING_ROPE;
        p.drop(rope, net.minecraft.loot.LootTable.builder().pool(net.minecraft.loot.LootPool.builder()
                .rolls(net.minecraft.loot.provider.number.ConstantLootNumberProvider.create(1))
                .with(net.minecraft.loot.entry.ItemEntry.builder(rope))
                .conditionally(net.minecraft.loot.condition.BlockStatePropertyLootCondition.builder(rope)
                        .properties(net.minecraft.predicate.StatePredicate.Builder.create()
                                .exactMatch(net.get900.pixelpirates.homestead.furniture.HangingRopeBlock.DEPLOYED, false)))
                .conditionally(net.minecraft.loot.condition.SurvivesExplosionLootCondition.builder())));
        done.add(rope);
    }
}
