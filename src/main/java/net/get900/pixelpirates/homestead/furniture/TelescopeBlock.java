package net.get900.pixelpirates.homestead.furniture;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemPlacementContext;

/** TELESCOPE: a brass tube on a tripod - unlike other furniture it points the way you look when you place it. */
public class TelescopeBlock extends FurnitureBlock {
    public TelescopeBlock(Settings s, double[]... boxes) { super(s, true, boxes); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) { return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing()); }
}
