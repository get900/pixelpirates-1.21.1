package net.get900.pixelpirates.block.custom;

import net.minecraft.block.BlockState;
import net.minecraft.block.LeavesBlock;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Leaves that shed a signature particle from their underside - ember drips under cinder
 * trees, drifting souls under wispwood, void motes under voidbloom. Keeps vanilla leaf
 * behaviour (decay, rain drips) via super.
 */
public class AmbientLeavesBlock extends LeavesBlock {
    private final ParticleEffect particle;
    private final int oneIn;

    public AmbientLeavesBlock(ParticleEffect particle, int oneIn, Settings settings) {
        super(settings);
        this.particle = particle;
        this.oneIn = oneIn;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        super.randomDisplayTick(state, world, pos, random);
        if (random.nextInt(oneIn) == 0) {
            AmbientParticleBlock.emitFromFace(world, pos, Direction.DOWN, particle, random);
        }
    }
}
