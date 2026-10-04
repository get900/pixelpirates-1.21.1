package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.client.TelescopeView;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Looking through a telescope: the FOV cut to a tenth (a spyglass) and further by TelescopeView.zoom (1/2/4). */
@Mixin(GameRenderer.class)
public abstract class TelescopeFovMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void pixelpirates$telescopeZoom(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        // vanilla only zooms a spyglass that's being held up (isUsingItem), so the telescope does its own: x10, x20, x40
        if (TelescopeView.active && changingFov) cir.setReturnValue(cir.getReturnValue() * 0.1 / TelescopeView.zoom);
    }
}
