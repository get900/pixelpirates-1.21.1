package net.get900.pixelpirates.block.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/** A plain decorative block with a fixed non-full shape (outline = collision). No states, no block entity. */
public class PropBlock extends Block {
    private final VoxelShape shape;

    public PropBlock(Settings settings, VoxelShape shape) {
        super(settings);
        this.shape = shape;
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return shape; }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) { return shape; }
}
