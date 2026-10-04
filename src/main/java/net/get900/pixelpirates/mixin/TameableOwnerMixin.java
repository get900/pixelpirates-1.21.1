package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.parrot.ParrotCollection;
import net.get900.pixelpirates.homestead.parrot.ParrotTypes;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PARROT TYPES phase 4: a parrot becoming a player's (tamed with seeds, out of a crate, recalled) unlocks its type in
 *  their collection (ParrotCollection). */
@Mixin(TameableEntity.class)
public abstract class TameableOwnerMixin {
    @Inject(method = "setOwner", at = @At("TAIL"))
    private void pixelpirates$unlockParrot(PlayerEntity player, CallbackInfo ci) {
        if ((Object) this instanceof ParrotEntity p && player instanceof ServerPlayerEntity sp) ParrotCollection.unlock(sp, ParrotTypes.of(p));
    }
}
