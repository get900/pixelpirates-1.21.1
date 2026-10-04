package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.AreaEffectCloudEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/** Thrown INK BOMB (kraken ink, after the Kraken): bursts into a blinding ink cloud, 4 blocks across, for 6 s. */
public class InkBombEntity extends ThrownItemEntity {
    public InkBombEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public InkBombEntity(World world, LivingEntity owner) {
        super(ModEntities.INK_BOMB, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.INK_BOMB;
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (this.getWorld() instanceof ServerWorld sw) {
            AreaEffectCloudEntity cloud = new AreaEffectCloudEntity(sw, getX(), getY(), getZ());
            if (getOwner() instanceof LivingEntity o) cloud.setOwner(o);
            cloud.setRadius(2.0f);
            cloud.setRadiusOnUse(0);
            cloud.setDuration(120);
            cloud.setWaitTime(0);
            cloud.setParticleType(ParticleTypes.SQUID_INK);
            cloud.addEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 100, 0));
            cloud.addEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 0));
            sw.spawnEntity(cloud);
            sw.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY(), getZ(), 40, 1.2, 0.6, 1.2, 0.05);
            sw.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_SQUID_SQUIRT, SoundCategory.PLAYERS, 1.2f, 0.7f);
            this.discard();
        }
    }
}
