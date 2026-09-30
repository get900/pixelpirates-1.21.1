package net.get900.pixelpirates.block.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Fruit that hangs under palm/shorewood canopies (banana bunch, coconut).
 * Placed by the tree decorators; only the outline is reduced so the hanging model
 * is what the player targets, not a full invisible cube.
 */
public class HangingFruitBlock extends Block {
    private final VoxelShape shape;

    public HangingFruitBlock(VoxelShape shape, Settings settings) {
        super(settings);
        this.shape = shape;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return shape;
    }
}
