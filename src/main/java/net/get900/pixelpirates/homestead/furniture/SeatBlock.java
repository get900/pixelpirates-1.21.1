package net.get900.pixelpirates.homestead.furniture;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** Furniture you can sit on (captain's chair, barrel stool). */
public class SeatBlock extends FurnitureBlock {
    private final double seatHeight;

    public SeatBlock(Settings s, double seatHeight, double[]... boxes) {
        super(s, true, boxes);
        this.seatHeight = seatHeight;
    }

    /** How high above the block's base a sitter sits (townsfolk sit here too - homestead/town). */
    public double seatHeight() { return seatHeight; }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player.isSneaking() || player.hasVehicle() || !player.getStackInHand(hand).isEmpty()) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        return SeatEntity.sit(world, pos, seatHeight, player) ? ActionResult.SUCCESS : ActionResult.PASS;
    }
}
