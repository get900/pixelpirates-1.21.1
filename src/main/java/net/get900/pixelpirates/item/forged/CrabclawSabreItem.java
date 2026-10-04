package net.get900.pixelpirates.item.forged;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ToolMaterial;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;

/** CRABCLAW SABRE (P1, Boarding Sabre + crab shell): every third hit in a row on the same foe PINCHES - +3 damage, Slowness II 2 s. */
public class CrabclawSabreItem extends ForgedBlade {
    public CrabclawSabreItem(ToolMaterial m, int dmg, float speed, Settings s) {
        super(m, dmg, speed, s, "Every 3rd hit on the same foe pinches: +3 damage, slows");
    }

    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        if (!target.getWorld().isClient) {
            NbtCompound n = stack.getOrCreateNbt();
            int combo = n.getInt("PinchTarget") == target.getId() ? n.getInt("Pinch") + 1 : 1;
            n.putInt("PinchTarget", target.getId());
            if (combo >= 3) {
                combo = 0;
                bonusHit(target, attacker, 3.0f);
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
                target.getWorld().playSound(null, target.getBlockPos(), SoundEvents.ENTITY_TURTLE_EGG_CRACK, SoundCategory.PLAYERS, 1f, 0.7f);
                if (target.getWorld() instanceof ServerWorld sw)
                    sw.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
            }
            n.putInt("Pinch", combo);
        }
        return super.postHit(stack, target, attacker);
    }
}
