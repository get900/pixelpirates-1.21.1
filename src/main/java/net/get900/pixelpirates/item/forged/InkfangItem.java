package net.get900.pixelpirates.item.forged;

import net.get900.pixelpirates.entity.mob.ModBoss;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * INKFANG (P5, Kraken Fang + kraken ink + luminous ichor - so only after the Kraken): hits blind for 2 s in a squirt of ink.
 * Right-click VANISHES in an ink cloud: 5 s invisibility, every non-boss mob within 12 that hunts you loses you. 30 s cooldown.
 */
public class InkfangItem extends ForgedBlade {
    public InkfangItem(ToolMaterial m, int dmg, float speed, Settings s) {
        super(m, dmg, speed, s, "Hits blind the foe", "Right-click: vanish in ink - foes lose you (30 s)");
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (target.getWorld() instanceof ServerWorld sw) {
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0));
            sw.spawnParticles(ParticleTypes.SQUID_INK, target.getX(), target.getBodyY(0.6), target.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
        }
        return super.postHit(stack, target, attacker);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world instanceof ServerWorld sw) {
            user.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 100, 0));
            for (MobEntity m : sw.getEntitiesByClass(MobEntity.class, user.getBoundingBox().expand(12), m -> m.getTarget() == user && !(m instanceof ModBoss))) {
                m.setTarget(null);
                m.getNavigation().stop();
            }
            sw.spawnParticles(ParticleTypes.SQUID_INK, user.getX(), user.getBodyY(0.5), user.getZ(), 60, 1.2, 0.8, 1.2, 0.05);
            sw.playSound(null, user.getBlockPos(), SoundEvents.ENTITY_GLOW_SQUID_SQUIRT, SoundCategory.PLAYERS, 1.2f, 0.7f);
            user.getItemCooldownManager().set(this, 600);
        }
        return TypedActionResult.success(stack, world.isClient);
    }
}
