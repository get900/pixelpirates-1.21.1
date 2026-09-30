package net.get900.pixelpirates.homestead.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricDataOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricModelProvider;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.minecraft.block.CropBlock;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ItemModelGenerator;
import net.minecraft.data.client.Models;
import net.minecraft.item.Item;

/** Block states + models for the homestead set. Blocks with hand-authored shaped models live in resources/ instead. */
public class HomesteadModelProvider extends FabricModelProvider {
    public HomesteadModelProvider(FabricDataOutput output) { super(output); }

    @Override
    public String getName() { return "Homestead Models"; }

    @Override
    public void generateBlockStateModels(BlockStateModelGenerator g) {
        g.registerCrop(HomesteadBlocks.PINEAPPLE_CROP, CropBlock.AGE, 0, 0, 1, 1, 2, 2, 2, 3);
        g.registerCrop(HomesteadBlocks.LIME_CROP, CropBlock.AGE, 0, 0, 1, 1, 2, 2, 2, 3);
        g.registerCrop(HomesteadBlocks.CHILI_CROP, CropBlock.AGE, 0, 0, 1, 1, 2, 2, 2, 3);
        // #5 building
        g.registerCubeAllModelTexturePool(net.get900.pixelpirates.block.ModBlocks.PALM_PLANKS).stairs(HomesteadBlocks.PALM_STAIRS)
                .slab(HomesteadBlocks.PALM_SLAB).fence(HomesteadBlocks.PALM_FENCE).fenceGate(HomesteadBlocks.PALM_FENCE_GATE);
        g.registerDoor(HomesteadBlocks.PALM_DOOR);
        g.registerOrientableTrapdoor(HomesteadBlocks.PALM_TRAPDOOR);
        for (net.minecraft.block.Block b : new net.minecraft.block.Block[]{HomesteadBlocks.WHITE_SAIL_CANVAS, HomesteadBlocks.BLACK_SAIL_CANVAS,
                HomesteadBlocks.CRIMSON_SAIL_CANVAS, HomesteadBlocks.STRIPED_SAIL_CANVAS, HomesteadBlocks.JOLLY_ROGER_SAIL_CANVAS}) g.registerSimpleCubeAll(b);
        g.registerCubeAllModelTexturePool(HomesteadBlocks.THATCH).stairs(HomesteadBlocks.THATCH_STAIRS).slab(HomesteadBlocks.THATCH_SLAB);
        HomesteadModels.building(g);
    }

    @Override
    public void generateItemModels(ItemModelGenerator g) {
        for (Item i : HomesteadItems.FLAT) if (!(i instanceof net.minecraft.item.AliasedBlockItem)) g.register(i, Models.GENERATED);   // crops write their seeds
        for (Item i : HomesteadItems.HANDHELD) g.register(i, Models.HANDHELD);
    }
}
