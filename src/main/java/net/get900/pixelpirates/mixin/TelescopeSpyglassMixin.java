package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.client.TelescopeView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Looking through a TELESCOPE (homestead/client/TelescopeView): the local player counts as using a spyglass - scope, zoom, slow aim. */
@Mixin(PlayerEntity.class)
public abstract class TelescopeSpyglassMixin {
    @Inject(method = "isUsingSpyglass", at = @At("RETURN"), cancellable = true)
    private void pixelpirates$telescope(CallbackInfoReturnable<Boolean> cir) {
        if (TelescopeView.active && (Object) this == MinecraftClient.getInstance().player) cir.setReturnValue(true);
    }
}
