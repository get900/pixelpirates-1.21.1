package net.get900.pixelpirates.homestead.chess;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** THE CHESS TABLE: a one-block table with a board on top and a little set of pieces; use it to play (Chess.open). */
public class ChessTableBlock extends FurnitureBlock implements BlockEntityProvider {
    public ChessTableBlock(Settings s) { super(s, true, new double[]{0, 0, 0, 16, 15, 16}); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayerEntity sp && world.getBlockEntity(pos) instanceof ChessBoardEntity be) Chess.open(sp, be);
        return ActionResult.success(world.isClient);
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
