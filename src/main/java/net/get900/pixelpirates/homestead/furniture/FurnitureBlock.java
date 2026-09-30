package net.get900.pixelpirates.homestead.furniture;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;

import java.util.EnumMap;
import java.util.Map;

/**
 * Decorative furniture that faces the player on placement. Its outline/collision is given as boxes for the NORTH-facing
 * model (pixels 0-16) and rotated for the other facings. {@code solid = false} = walk through it (nets, ropes).
 */
public class FurnitureBlock extends HorizontalFacingBlock {
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
    private final boolean solid;

    public FurnitureBlock(Settings s, boolean solid, double[]... boxes) {
        super(s);
        this.solid = solid;
        for (Direction d : Direction.Type.HORIZONTAL) shapes.put(d, rotated(boxes, d));
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    /** Boxes (x1,y1,z1,x2,y2,z2 in pixels, as authored facing north) turned to face {@code d}. */
    public static VoxelShape rotated(double[][] boxes, Direction d) {
        VoxelShape s = VoxelShapes.empty();
        for (double[] b : boxes) {
            double x1 = b[0], z1 = b[2], x2 = b[3], z2 = b[5];
            double[] r = switch (d) {
                case EAST -> new double[]{16 - z2, x1, 16 - z1, x2};
                case SOUTH -> new double[]{16 - x2, 16 - z2, 16 - x1, 16 - z1};
                case WEST -> new double[]{z1, 16 - x2, z2, 16 - x1};
                default -> new double[]{x1, z1, x2, z2};
            };
            s = VoxelShapes.union(s, Block.createCuboidShape(r[0], b[1], r[1], r[2], b[4], r[3]));
        }
        return s;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) { return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite()); }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return shapes.get(s.get(FACING)); }

    @Override
    public VoxelShape getCollisionShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return solid ? shapes.get(s.get(FACING)) : VoxelShapes.empty(); }

    @Override
    public BlockState rotate(BlockState s, BlockRotation r) { return s.with(FACING, r.rotate(s.get(FACING))); }

    @Override
    public BlockState mirror(BlockState s, BlockMirror m) { return s.rotate(m.getRotation(s.get(FACING))); }
}
