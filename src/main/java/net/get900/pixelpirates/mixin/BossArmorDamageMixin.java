package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.item.BossArmor;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * BOSS SETS: Boss Ward + the damage side of the set bonuses (item/BossArmor#modify), applied to players AFTER armor and
 * Protection (LivingEntity#applyDamage calls modifyAppliedDamage last, just before absorption) - so it also reaches
 * the magic damage armor points never touch.
 */
@Mixin(LivingEntity.class)
public abstract class BossArmorDamageMixin {
    @Inject(method = "modifyAppliedDamage", at = @At("RETURN"), cancellable = true)
    private void pixelpirates$bossWard(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.getWorld().isClient) return;
        float dmg = net.get900.pixelpirates.world.SkillEffects.modifyDamage(self, source, cir.getReturnValueF());   // pirate skills
        if (self instanceof PlayerEntity p) {
            dmg = net.get900.pixelpirates.world.MobDamageScale.scale(p, source, dmg);     // mob damage pass (gear ladder)
            dmg = BossArmor.modify(p, source, dmg);
        }
        cir.setReturnValue(dmg);
    }
}
