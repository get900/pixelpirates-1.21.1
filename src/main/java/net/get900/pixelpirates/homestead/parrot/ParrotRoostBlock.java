package net.get900.pixelpirates.homestead.parrot;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** THE PARROT ROOST (phase 4): a perch stand - use it to open your parrot collection (ParrotCollection.open). */
public class ParrotRoostBlock extends FurnitureBlock {
    public ParrotRoostBlock(Settings s) { super(s, true, new double[]{3, 0, 3, 13, 15, 13}); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayerEntity sp) ParrotCollection.open(sp);
        return ActionResult.success(world.isClient);
    }
}
