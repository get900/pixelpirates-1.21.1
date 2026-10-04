package net.get900.pixelpirates.homestead.tavern;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/** A tavern game table (Liar's Dice, Crown & Anchor): right-click opens the shared game screen. */
public class GameTableBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    private final BiFunction<BlockPos, BlockState, BlockEntity> factory;
    private final Supplier<BlockEntityType<?>> type;
    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    public GameTableBlock(Settings s, BiFunction<BlockPos, BlockState, BlockEntity> factory, Supplier<BlockEntityType<?>> type, double[]... boxes) {
        super(s);
        this.factory = factory;
        this.type = type;
        for (Direction d : Direction.Type.HORIZONTAL) shapes.put(d, FurnitureBlock.rotated(boxes, d));
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) { return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite()); }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return shapes.get(s.get(FACING)); }

    @Override
    public BlockState rotate(BlockState s, BlockRotation r) { return s.with(FACING, r.rotate(s.get(FACING))); }

    @Override
    public BlockState mirror(BlockState s, BlockMirror m) { return s.rotate(m.getRotation(s.get(FACING))); }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return factory.apply(pos, state); }

    @SuppressWarnings("unchecked")
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> t) {
        return world.isClient ? null : checkType(t, (BlockEntityType<GameTableBlockEntity>) type.get(), GameTableBlockEntity::tick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (world.getBlockEntity(pos) instanceof GameTableBlockEntity be && player instanceof ServerPlayerEntity p) be.open(p);
        return ActionResult.CONSUME;
    }
}
