package net.get900.pixelpirates.homestead.chess;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.get900.pixelpirates.homestead.town.ChessLeague;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** THE CHESS LEAGUE BOARD (2026-10-05): use for the standings, sneak + use to sign up for the next tournament. */
public class LeagueBoardBlock extends FurnitureBlock {
    public LeagueBoardBlock(Settings s) { super(s, true, new double[]{1, 0, 7, 3, 16, 9}, new double[]{13, 0, 7, 15, 16, 9}, new double[]{0, 5, 7.5, 16, 15, 8.5}); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity sp) ChessLeague.useBoard(sp, sp.isSneaking());
        return ActionResult.success(world.isClient);
    }
}
