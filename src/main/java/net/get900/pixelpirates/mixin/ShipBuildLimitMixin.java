package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.world.ShipBuilding;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Limits the blocks a player can add to an assembled ship (world/ShipBuilding, Ship's Carpenter refit). */
@Mixin(BlockItem.class)
public abstract class ShipBuildLimitMixin {

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("HEAD"), cancellable = true)
    private void pp_limitShipBuilding(ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (!(ctx.getWorld() instanceof ServerWorld world) || !(ctx.getPlayer() instanceof ServerPlayerEntity player)) return;
        if (!ctx.canPlace()) return;
        if (!ShipBuilding.mayPlace(player, world, ctx.getBlockPos())) cir.setReturnValue(ActionResult.FAIL);
    }

    @Inject(method = "place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;", at = @At("RETURN"))
    private void pp_countShipBuilding(ItemPlacementContext ctx, CallbackInfoReturnable<ActionResult> cir) {
        if (!(ctx.getWorld() instanceof ServerWorld world) || !(ctx.getPlayer() instanceof ServerPlayerEntity player)) return;
        if (!cir.getReturnValue().isAccepted()) return;
        BlockPos pos = ctx.getBlockPos();
        if (!world.getBlockState(pos).isAir()) ShipBuilding.placed(player, world, pos);
    }
}
