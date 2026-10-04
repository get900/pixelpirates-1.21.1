package net.get900.pixelpirates.homestead.tattoo;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** THE TATTOO CHAIR (townhouse #33): a reclining leather chair - use it to open the tattooist's flash sheet (Tattoos.open). */
public class TattooChairBlock extends FurnitureBlock {
    public TattooChairBlock(Settings s) { super(s, true, new double[]{1, 0, 1, 15, 9, 15}, new double[]{1, 9, 11, 15, 16, 15}); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player instanceof ServerPlayerEntity sp) Tattoos.open(sp, pos);
        return ActionResult.success(world.isClient);
    }
}
