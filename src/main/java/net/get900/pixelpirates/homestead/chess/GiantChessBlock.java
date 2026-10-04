package net.get900.pixelpirates.homestead.chess;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * GIANT CHESS: a stone pedestal with a chess clock. The board is the 8 x 8 floor in FRONT of it (the way it faces):
 * a1 is the square straight in front, the files run to the right as you stand at the pedestal, white plays from the
 * pedestal's side. The pieces stand on top of that floor (knee- to waist-high); use the pedestal to play.
 */
public class GiantChessBlock extends FurnitureBlock implements BlockEntityProvider {
    public GiantChessBlock(Settings s) { super(s, true, new double[]{3, 0, 3, 13, 14, 13}); }

    /** The block a square stands on top of... rather the air cell the piece occupies: (file f, rank r) -> world pos. */
    public static BlockPos square(BlockPos pedestal, Direction facing, int f, int r) {
        return pedestal.offset(facing, 1 + r).offset(facing.rotateYClockwise(), f);
    }

    @Override
    public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing());          // faces away from you: the board goes in front
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayerEntity sp && world.getBlockEntity(pos) instanceof ChessBoardEntity be) Chess.open(sp, be);
        return ActionResult.success(world.isClient);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable BlockView world, List<Text> tooltip, TooltipContext options) {
        tooltip.add(Text.literal("The board: the 8 x 8 floor in front of it").formatted(Formatting.GRAY));
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new ChessBoardEntity(pos, state); }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient || type != HomesteadBlockEntities.CHESS ? null : (BlockEntityTicker<T>) (BlockEntityTicker<ChessBoardEntity>) ChessBoardEntity::serverTick;
    }
}
