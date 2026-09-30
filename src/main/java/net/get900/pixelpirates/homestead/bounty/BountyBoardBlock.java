package net.get900.pixelpirates.homestead.bounty;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** BOUNTY BOARD (#16): a post-and-plank notice board of wanted posters. Use it to see, deliver and collect contracts. */
public class BountyBoardBlock extends FurnitureBlock {
    public BountyBoardBlock(Settings s) {
        super(s, true, new double[]{0, 0, 6, 16, 16, 10});
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity sp) Bounties.useBoard(sp);
        return ActionResult.success(world.isClient);
    }
}
