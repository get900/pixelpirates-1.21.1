package net.get900.pixelpirates.homestead.datagen;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.HomesteadBlocks;
import net.minecraft.block.Block;
import net.minecraft.data.client.BlockStateModelGenerator;
import net.minecraft.data.client.ModelIds;
import net.minecraft.data.client.Models;
import net.minecraft.data.client.TextureMap;
import net.minecraft.util.Identifier;

/** Model helpers for homestead blocks that don't fit a vanilla texture pool. */
final class HomesteadModels {
    private HomesteadModels() {}

    static Identifier tex(String name) { return new Identifier(PixelPirates.MOD_ID, "block/" + name); }

    static void fenceFamily(BlockStateModelGenerator g, Block fence, Block gate, Identifier texture) {
        TextureMap t = TextureMap.texture(texture);
        Identifier post = Models.FENCE_POST.upload(fence, t, g.modelCollector);
        Identifier side = Models.FENCE_SIDE.upload(fence, t, g.modelCollector);
        g.blockStateCollector.accept(BlockStateModelGenerator.createFenceBlockState(fence, post, side));
        Models.FENCE_INVENTORY.upload(ModelIds.getItemModelId(fence.asItem()), t, g.modelCollector);
        Identifier open = Models.TEMPLATE_FENCE_GATE_OPEN.upload(gate, t, g.modelCollector);
        Identifier closed = Models.TEMPLATE_FENCE_GATE.upload(gate, t, g.modelCollector);
        Identifier wallOpen = Models.TEMPLATE_FENCE_GATE_WALL_OPEN.upload(gate, t, g.modelCollector);
        Identifier wall = Models.TEMPLATE_FENCE_GATE_WALL.upload(gate, t, g.modelCollector);
        g.blockStateCollector.accept(BlockStateModelGenerator.createFenceGateBlockState(gate, open, closed, wallOpen, wall, true));
    }

    static void building(BlockStateModelGenerator g) {
        fenceFamily(g, HomesteadBlocks.DRIFTWOOD_FENCE, HomesteadBlocks.DRIFTWOOD_FENCE_GATE, tex("driftwood_block"));
        Identifier mat = Models.CARPET.upload(HomesteadBlocks.WOVEN_MAT, TextureMap.wool(tex("woven_mat")), g.modelCollector);
        g.blockStateCollector.accept(BlockStateModelGenerator.createSingletonBlockState(HomesteadBlocks.WOVEN_MAT, mat));
        // WOVEN_PALM_SCREEN (pane), ROPE_LADDER, ROPE_BRIDGE, TIKI_TORCH: hand-authored in resources/ (tools/homestead_models.py)
    }
}
