package net.get900.pixelpirates.homestead.datagen;

import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagProvider;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.tag.TagKey;

import java.util.function.Function;

/** Tag entries for the later homestead features. */
final class HomesteadTagsMore {
    private HomesteadTagsMore() {}

    static void blocks(Function<TagKey<Block>, FabricTagProvider<Block>.FabricTagBuilder> tag) {
    }

    static void items(Function<TagKey<Item>, FabricTagProvider<Item>.FabricTagBuilder> tag) {
        // #24 region tools
        tag.apply(net.minecraft.registry.tag.ItemTags.PICKAXES).add(net.get900.pixelpirates.homestead.HomesteadItems.EMBER_PICKAXE,
                net.get900.pixelpirates.homestead.HomesteadItems.KRAKEN_PICKAXE, net.get900.pixelpirates.homestead.HomesteadItems.BONE_PICKAXE);
        tag.apply(net.minecraft.registry.tag.ItemTags.AXES).add(net.get900.pixelpirates.homestead.HomesteadItems.EMBER_AXE,
                net.get900.pixelpirates.homestead.HomesteadItems.KRAKEN_AXE, net.get900.pixelpirates.homestead.HomesteadItems.BONE_AXE);
        tag.apply(net.minecraft.registry.tag.ItemTags.SHOVELS).add(net.get900.pixelpirates.homestead.HomesteadItems.EMBER_SHOVEL,
                net.get900.pixelpirates.homestead.HomesteadItems.KRAKEN_SHOVEL, net.get900.pixelpirates.homestead.HomesteadItems.BONE_SHOVEL);
        tag.apply(net.minecraft.registry.tag.ItemTags.HOES).add(net.get900.pixelpirates.homestead.HomesteadItems.EMBER_HOE,
                net.get900.pixelpirates.homestead.HomesteadItems.KRAKEN_HOE, net.get900.pixelpirates.homestead.HomesteadItems.BONE_HOE);
    }
}
