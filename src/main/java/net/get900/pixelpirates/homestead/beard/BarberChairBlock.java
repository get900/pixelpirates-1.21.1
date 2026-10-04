package net.get900.pixelpirates.homestead.beard;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** THE BARBER'S CHAIR (the barber-surgeon, #38): red leather on a brass pedestal - use it to open the barber's book (Beards.open). */
public class BarberChairBlock extends FurnitureBlock {
    public BarberChairBlock(Settings s) { super(s, true, new double[]{2, 0, 2, 14, 10, 14}, new double[]{2, 10, 12, 14, 20, 14}); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayerEntity sp) Beards.open(sp, pos);
        return ActionResult.success(world.isClient);
    }
}
