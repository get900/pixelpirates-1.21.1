package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.util.AdvancementHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public class HammockBlock extends HorizontalFacingBlock {
    // The sling runs left-right of FACING (between the two trees), so N/S hammocks span X.
    private static final VoxelShape SHAPE_X = Block.createCuboidShape(0, 2, 2, 16, 7, 14);
    private static final VoxelShape SHAPE_Z = Block.createCuboidShape(2, 2, 0, 14, 7, 16);

    public HammockBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(FACING).getAxis() == Direction.Axis.Z ? SHAPE_X : SHAPE_Z;
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        if (!isValidPlacement(world, pos, state)) {
            player.sendMessage(Text.literal("§cThe hammock must be strung between two trees!"), true);
            return ActionResult.FAIL;
        }

        ServerPlayerEntity spe = (ServerPlayerEntity) player;
        spe.setSpawnPoint(world.getRegistryKey(), pos, player.getYaw(), true, false);
        player.sendMessage(Text.literal("§a* Hammock set as your respawn point."), true);
        AdvancementHelper.grant(spe, "hammock_time");
        return ActionResult.SUCCESS;
    }

    private boolean isValidPlacement(World world, BlockPos pos, BlockState state) {
        Direction facing = state.get(FACING);
        Direction left  = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        boolean hasLeft = false, hasRight = false;
        for (int i = 1; i <= 4 && !hasLeft; i++) {
            if (world.getBlockState(pos.offset(left, i)).isIn(BlockTags.LOGS)) hasLeft = true;
        }
        for (int i = 1; i <= 4 && !hasRight; i++) {
            if (world.getBlockState(pos.offset(right, i)).isIn(BlockTags.LOGS)) hasRight = true;
        }
        return hasLeft && hasRight;
    }
}
