package net.get900.pixelpirates.homestead.effect;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;

/**
 * TIPSY (rum): the world sways (client camera roll - mixin/TipsyCameraMixin) and now and then your legs lurch you
 * sideways while you walk. Stacks up to amplifier 3 with each drink; at 3 you also get nausea (RumItem).
 */
public class TipsyEffect extends StatusEffect {
    public TipsyEffect() { super(StatusEffectCategory.NEUTRAL, 0xD8942A); }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) { return true; }

    @Override
    public void applyUpdateEffect(LivingEntity e, int amplifier) {
        if (e.getWorld().isClient || !e.isOnGround()) return;
        Vec3d v = e.getVelocity();
        if (v.horizontalLengthSquared() < 0.002) return;                          // only while walking
        if (e.getRandom().nextInt(90) > 3 + amplifier * 3) return;
        double side = e.getRandom().nextBoolean() ? 1 : -1, s = 0.12 + 0.07 * amplifier;
        Vec3d dir = v.multiply(1, 0, 1).normalize();
        e.setVelocity(v.add(-dir.z * side * s, 0, dir.x * side * s));
        if (e instanceof PlayerEntity p) p.velocityModified = true;
    }
}
