package net.get900.pixelpirates.homestead.building;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;

/** TIKI TORCH: a palm pole with a woven basket of burning oil on top - island lighting that stands on its own. */
public class TikiTorchBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(5.5, 0, 5.5, 10.5, 16, 10.5);

    public TikiTorchBlock(Settings s) { super(s); }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return SHAPE; }

    @Override
    public boolean canPlaceAt(BlockState s, WorldView w, BlockPos p) { return Block.sideCoversSmallSquare(w, p.down(), Direction.UP); }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState s, Direction d, BlockState n, WorldAccess w, BlockPos p, BlockPos np) {
        return d == Direction.DOWN && !canPlaceAt(s, w, p) ? Blocks.AIR.getDefaultState() : super.getStateForNeighborUpdate(s, d, n, w, p, np);
    }

    @Override
    public void randomDisplayTick(BlockState s, World w, BlockPos p, Random r) {
        double x = p.getX() + 0.5, y = p.getY() + 1.05, z = p.getZ() + 0.5;
        w.addParticle(ParticleTypes.FLAME, x + r.nextGaussian() * 0.06, y, z + r.nextGaussian() * 0.06, 0, 0.01, 0);
        if (r.nextInt(3) == 0) w.addParticle(ParticleTypes.SMOKE, x, y + 0.2, z, 0, 0.02, 0);
    }
}
