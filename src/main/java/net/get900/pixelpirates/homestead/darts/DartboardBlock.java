package net.get900.pixelpirates.homestead.darts;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * THE DARTBOARD (2026-10-05): hangs on a wall (authored facing north, back on the wall to the south). Use it to start a
 * game of 301 - a regular from the tavern takes you on, or you play alone; others can join before the first dart.
 * The game lives in {@link DartboardBlockEntity}; scoring + aim in {@link Darts}.
 */
public class DartboardBlock extends FurnitureBlock implements BlockEntityProvider {
    /** The back plate, then the board (its face at z = 16 - Darts.FACE_DEPTH). */
    public static final double[][] BOXES = {{0, 0, 15, 16, 16, 16}, {0.5, 0.5, 16 - Darts.FACE_DEPTH, 15.5, 15.5, 15}};

    public DartboardBlock(Settings s) { super(s, true, BOXES); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity sp && world.getBlockEntity(pos) instanceof DartboardBlockEntity be) be.use(sp);
        return ActionResult.success(world.isClient);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new DartboardBlockEntity(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient || type != HomesteadBlockEntities.DARTBOARD ? null
                : (w, p, s, be) -> ((DartboardBlockEntity) be).tick((ServerWorld) w);
    }
}
