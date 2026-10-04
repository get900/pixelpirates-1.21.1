package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.parrot.ParrotAbilities;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** GHOST PARROT possession (ParrotAbilities): a possessed mob never takes a player as its target - its own AI keeps
 *  trying, this keeps saying no; ParrotAbilities.steer points it at the monsters round its player instead. */
@Mixin(MobEntity.class)
public abstract class MobPossessionMixin {
    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void pixelpirates$possessed(LivingEntity target, CallbackInfo ci) {
        if (target instanceof PlayerEntity && ParrotAbilities.POSSESSED.containsKey(((MobEntity) (Object) this).getUuid())) ci.cancel();
    }
}
