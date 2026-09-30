package net.get900.pixelpirates.block.custom;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * A plain block that occasionally emits a particle from one of its exposed faces
 * (glow motes off luminous veins, fumes off sulfur...). Client-side only - no gameplay effect.
 */
public class AmbientParticleBlock extends Block {
    private final ParticleEffect particle;
    private final int oneIn;
    private final boolean topOnly;

    public AmbientParticleBlock(ParticleEffect particle, int oneIn, boolean topOnly, Settings settings) {
        super(settings);
        this.particle = particle;
        this.oneIn = oneIn;
        this.topOnly = topOnly;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(oneIn) != 0) return;
        Direction face = topOnly ? Direction.UP : Direction.random(random);
        emitFromFace(world, pos, face, particle, random);
    }

    /** Spawns just outside {@code face} if that face is visible; shared with the leaves variant. */
    static void emitFromFace(World world, BlockPos pos, Direction face, ParticleEffect particle, Random random) {
        BlockPos side = pos.offset(face);
        if (world.getBlockState(side).isOpaqueFullCube(world, side)) return;
        double x = pos.getX() + 0.5 + face.getOffsetX() * 0.55 + (face.getOffsetX() == 0 ? random.nextDouble() - 0.5 : 0);
        double y = pos.getY() + 0.5 + face.getOffsetY() * 0.55 + (face.getOffsetY() == 0 ? random.nextDouble() - 0.5 : 0);
        double z = pos.getZ() + 0.5 + face.getOffsetZ() * 0.55 + (face.getOffsetZ() == 0 ? random.nextDouble() - 0.5 : 0);
        world.addParticle(particle, x, y, z, face.getOffsetX() * 0.01, 0.01 + face.getOffsetY() * 0.01, face.getOffsetZ() * 0.01);
    }
}
