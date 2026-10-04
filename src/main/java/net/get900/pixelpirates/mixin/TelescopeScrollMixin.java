package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.client.TelescopeView;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** While looking through a telescope the scroll wheel zooms instead of changing the hotbar slot. */
@Mixin(Mouse.class)
public abstract class TelescopeScrollMixin {
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void pixelpirates$telescopeScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (TelescopeView.active && net.minecraft.client.MinecraftClient.getInstance().currentScreen == null) {
            TelescopeView.scroll(vertical);
            ci.cancel();
        }
    }
}
