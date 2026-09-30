package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.mob.AbyssalKingEntity;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * DEPTH CHARGE - the underwater answer to dynamite (Abyssal King fight). Thrown, it sinks slowly; it bursts on
 * hitting a creature, or 2 s after it was thrown / came to rest on the bottom. The blast never breaks blocks and
 * never hurts players; it hits mobs hard, and next to the Abyssal King it SHATTERS his Reseal channel
 * (AbyssalKingEntity#onDepthCharge) or rocks him for 1.5 s.
 */
public class DepthChargeEntity extends ThrownItemEntity {
    private static final TrackedData<Boolean> LANDED = DataTracker.registerData(DepthChargeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final double RADIUS = 4.5;
    private int fuse = 40;

    public DepthChargeEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public DepthChargeEntity(World world, LivingEntity owner) {
        super(ModEntities.DEPTH_CHARGE, owner, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(LANDED, false);
    }

    @Override
    protected Item getDefaultItem() { return ModItems.DEPTH_CHARGE; }

    @Override
    protected float getGravity() { return this.isTouchingWater() ? 0.012f : 0.04f; }

    @Override
    public void tick() {
        if (this.dataTracker.get(LANDED)) this.baseTick();
        else super.tick();
        if (this.getWorld().isClient) {
            if (this.age % 2 == 0) this.getWorld().addParticle(this.isTouchingWater() ? ParticleTypes.BUBBLE : ParticleTypes.SMOKE,
                    getX(), getY() + 0.2, getZ(), 0, 0.03, 0);
            return;
        }
        if (this.age % 10 == 0) this.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.6f, 0.5f + (40 - fuse) / 60f);
        if (--fuse <= 0) {
            explode();
            this.discard();
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        if (this.getWorld().isClient) return;
        if (hit instanceof EntityHitResult eh && !(eh.getEntity() instanceof PlayerEntity)) {
            explode();
            this.discard();
            return;
        }
        if (hit.getType() == HitResult.Type.BLOCK) {
            this.setVelocity(Vec3d.ZERO);
            this.setPosition(hit.getPos().x, hit.getPos().y + 0.05, hit.getPos().z);
            this.dataTracker.set(LANDED, true);
            fuse = Math.min(fuse, 20);
        }
    }

    private void explode() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        boolean wet = this.isTouchingWater();
        sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 3, 0.6, 0.4, 0.6, 0);
        sw.spawnParticles(wet ? ParticleTypes.BUBBLE : ParticleTypes.LARGE_SMOKE, getX(), getY() + 0.4, getZ(), 60, 1.4, 1.0, 1.4, 0.15);
        if (wet) sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 0.5, getZ(), 30, 0.6, 1.5, 0.6, 0.2);
        sw.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 2.0f, wet ? 0.6f : 1.0f);
        if (wet) sw.playSound(null, getX(), getY(), getZ(), SoundEvents.AMBIENT_UNDERWATER_EXIT, SoundCategory.PLAYERS, 2.0f, 0.5f);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(RADIUS + 3),
                e -> e.isAlive() && !(e instanceof PlayerEntity))) {
            double d = Math.max(0, e.getPos().distanceTo(this.getPos()) - e.getWidth() * 0.5);
            if (e instanceof AbyssalKingEntity k && d <= RADIUS + 1.5) k.onDepthCharge();
            if (d > RADIUS) continue;
            if (e.damage(this.getDamageSources().explosion(this, this.getOwner()), (float) (12 * (1 - 0.5 * d / RADIUS)))) {
                Vec3d away = e.getPos().subtract(this.getPos());
                away = away.lengthSquared() < 1e-4 ? new Vec3d(0, 1, 0) : away.normalize();
                double kb = 1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                e.addVelocity(away.x * 0.8 * kb, 0.3 * kb, away.z * 0.8 * kb);
                e.velocityModified = true;
            }
        }
    }
}
