package net.get900.pixelpirates.homestead.art;

import net.get900.pixelpirates.homestead.TallFurniture;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** THE EASEL: two blocks tall (a tripod with the canvas on top); use it to paint (Art.open). The canvas lives on the lower block. */
public class EaselBlock extends TallFurniture implements BlockEntityProvider {
    public EaselBlock(Settings s) { super(s, new double[]{2, 0, 5, 14, 16, 11}, new double[]{1, 0, 6, 15, 14, 10}); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        BlockPos lower = state.get(HALF) == DoubleBlockHalf.LOWER ? pos : pos.down();
        if (player instanceof ServerPlayerEntity sp && world.getBlockEntity(lower) instanceof EaselBlockEntity be) Art.open(sp, lower, be);
        return ActionResult.success(world.isClient);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return state.get(HALF) == DoubleBlockHalf.LOWER ? new EaselBlockEntity(pos, state) : null;
    }
}
