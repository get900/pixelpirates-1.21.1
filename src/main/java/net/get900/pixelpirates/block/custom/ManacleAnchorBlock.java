package net.get900.pixelpirates.block.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.state.StateManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * MANACLE ANCHOR - an iron plate with a hanging shackle, set in the Gallows Pit wall. When the Chained Revenant
 * sunders, his arms and legs fly to four of these and hang there (RevenantPartEntity). Unbreakable; faint soul-light.
 */
public class ManacleAnchorBlock extends HorizontalFacingBlock {
    public ManacleAnchorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(3) != 0) return;
        Direction f = state.get(FACING);
        world.addParticle(ParticleTypes.SOUL, pos.getX() + 0.5 + f.getOffsetX() * 0.6, pos.getY() + 0.3, pos.getZ() + 0.5 + f.getOffsetZ() * 0.6, 0, 0.01, 0);
    }
}
