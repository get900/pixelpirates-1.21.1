package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.parrot.ParrotCompanion;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PARROT COMPANION: a ship's parrot holds on through water, falls and blows; only sleep, death or our own set-down drop it. */
@Mixin(PlayerEntity.class)
public abstract class ParrotShoulderMixin {
    @Inject(method = "dropShoulderEntities", at = @At("HEAD"), cancellable = true)
    private void pixelpirates$holdOn(CallbackInfo ci) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (self.getWorld().isClient || ParrotCompanion.ALLOW_DROP.get() || self.isSleeping() || self.isDead() || self.isSpectator()
                || self.getAbilities().flying) return;
        ci.cancel();
    }
}
