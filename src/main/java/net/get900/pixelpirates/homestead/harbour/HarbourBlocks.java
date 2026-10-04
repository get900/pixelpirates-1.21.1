package net.get900.pixelpirates.homestead.harbour;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** The harbour's working blocks (see HarbourDues). */
public final class HarbourBlocks {
    private HarbourBlocks() {}

    /** DUES LEDGER (the Dock Office desk): doubloons in hand = pay your ship's dues; empty hand = your account. */
    public static class DuesLedger extends FurnitureBlock {
        public DuesLedger(Settings s) { super(s, true, new double[]{1, 0, 2, 15, 14, 14}); }

        @SuppressWarnings("deprecation")
        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            if (!(player instanceof ServerPlayerEntity p)) return ActionResult.PASS;
            if (p.getMainHandStack().isOf(ModItems.COIN)) HarbourDues.pay(p, pos); else HarbourDues.status(p);
            return ActionResult.CONSUME;
        }
    }

    /**
     * BERTH BOLLARD (on the piers and the East Berths): use = make your ship fast here (it must lie within 18 blocks);
     * with doubloons = pay the dues; sneak-use = cast off.
     */
    public static class BerthBollard extends FurnitureBlock {
        public BerthBollard(Settings s) { super(s, true, new double[]{4, 0, 4, 12, 13, 12}); }

        @SuppressWarnings("deprecation")
        @Override
        public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
            if (world.isClient) return ActionResult.SUCCESS;
            if (!(player instanceof ServerPlayerEntity p)) return ActionResult.PASS;
            if (p.isSneaking()) HarbourDues.castOff(p);
            else if (p.getMainHandStack().isOf(ModItems.COIN)) HarbourDues.pay(p, pos);
            else HarbourDues.moor(p, pos);
            return ActionResult.CONSUME;
        }
    }
}
