package net.get900.pixelpirates.homestead.datagen;

import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.TagKey;

import java.util.function.Function;

/** Homestead tag entries, merged into the main tag providers (one file per tag - two providers can't both write it). */
public final class HomesteadTags {
    private HomesteadTags() {}

    public static void blocks(Function<TagKey<Block>, FabricTagProvider<Block>.FabricTagBuilder> tag) {
        tag.apply(BlockTags.WOODEN_STAIRS).add(HomesteadBlocks.PALM_STAIRS);
        tag.apply(BlockTags.WOODEN_SLABS).add(HomesteadBlocks.PALM_SLAB);
        tag.apply(BlockTags.WOODEN_FENCES).add(HomesteadBlocks.PALM_FENCE, HomesteadBlocks.DRIFTWOOD_FENCE);
        tag.apply(BlockTags.FENCE_GATES).add(HomesteadBlocks.PALM_FENCE_GATE, HomesteadBlocks.DRIFTWOOD_FENCE_GATE);
        tag.apply(BlockTags.WOODEN_DOORS).add(HomesteadBlocks.PALM_DOOR);
        tag.apply(BlockTags.WOODEN_TRAPDOORS).add(HomesteadBlocks.PALM_TRAPDOOR);
        tag.apply(BlockTags.CLIMBABLE).add(HomesteadBlocks.ROPE_LADDER, HomesteadBlocks.HANGING_ROPE);
        tag.apply(BlockTags.HOE_MINEABLE).add(HomesteadBlocks.THATCH, HomesteadBlocks.THATCH_STAIRS, HomesteadBlocks.THATCH_SLAB);
        HomesteadTagsMore.blocks(tag);
    }

    public static void items(Function<TagKey<Item>, FabricTagProvider<Item>.FabricTagBuilder> tag) {
        tag.apply(ItemTags.WOODEN_STAIRS).add(HomesteadBlocks.PALM_STAIRS.asItem());
        tag.apply(ItemTags.WOODEN_SLABS).add(HomesteadBlocks.PALM_SLAB.asItem());
        tag.apply(ItemTags.WOODEN_FENCES).add(HomesteadBlocks.PALM_FENCE.asItem(), HomesteadBlocks.DRIFTWOOD_FENCE.asItem());
        tag.apply(ItemTags.FENCE_GATES).add(HomesteadBlocks.PALM_FENCE_GATE.asItem(), HomesteadBlocks.DRIFTWOOD_FENCE_GATE.asItem());
        tag.apply(ItemTags.WOODEN_DOORS).add(HomesteadBlocks.PALM_DOOR.asItem());
        tag.apply(ItemTags.WOODEN_TRAPDOORS).add(HomesteadBlocks.PALM_TRAPDOOR.asItem());
        HomesteadTagsMore.items(tag);
    }
}
