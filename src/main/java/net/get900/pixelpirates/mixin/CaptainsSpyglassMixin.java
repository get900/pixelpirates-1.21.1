package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.nav.CaptainsSpyglassItem;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla only zooms (FOV, scope overlay, sounds) for Items.SPYGLASS; the Captain's Spyglass counts too. */
@Mixin(PlayerEntity.class)
public abstract class CaptainsSpyglassMixin {
    @Inject(method = "isUsingSpyglass", at = @At("RETURN"), cancellable = true)
    private void pixelpirates$captainsSpyglass(CallbackInfoReturnable<Boolean> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (!cir.getReturnValueZ() && self.isUsingItem() && self.getActiveItem().getItem() instanceof CaptainsSpyglassItem) cir.setReturnValue(true);
    }
}
