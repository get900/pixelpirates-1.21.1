package net.get900.pixelpirates.homestead.cat;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** THE CATTERY COUNTER (townhouse #36): the keeper's counter - use it to see today's cats and buy one (Cattery.openCounter). */
public class CatteryCounterBlock extends FurnitureBlock {
    public CatteryCounterBlock(Settings s) { super(s, true, new double[]{0, 0, 2, 16, 14, 14}); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayerEntity sp) Cattery.openCounter(sp, pos);
        return ActionResult.success(world.isClient);
    }
}
