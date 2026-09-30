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
 * Verdigris cannon of the Flying Dutchman (ship_cannon model, ghost textures). It has no player
 * controls: GhostShipEncounter records each one's ship-space position so the AI fires broadsides from
 * them. Soul fire smoulders at the muzzle.
 */
public class GhostCannonBlock extends HorizontalFacingBlock {
    public GhostCannonBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(3) != 0) return;
        Direction f = state.get(FACING);
        world.addParticle(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5 + f.getOffsetX() * 0.7, pos.getY() + 0.45,
                pos.getZ() + 0.5 + f.getOffsetZ() * 0.7, 0, 0.01, 0);
    }
}
