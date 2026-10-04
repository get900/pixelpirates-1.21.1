package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.item.RelicWeapons;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * SPECTRAL CANNONBALL (the Dutchman's Hand Cannon; its Phantom Broadside rains four down): an arcing shot that bursts in
 * ghost fire on impact - `damage` to everything within 3 blocks except the shooter. Never breaks blocks.
 */
public class SpectralShotEntity extends ThrownItemEntity {
    private float damage = 7f;

    public SpectralShotEntity(EntityType<? extends SpectralShotEntity> type, World world) { super(type, world); }

    public SpectralShotEntity(World world, LivingEntity owner, float damage) {
        super(ModEntities.SPECTRAL_SHOT, owner, world);
        this.damage = damage;
    }

    public static SpectralShotEntity at(World world, LivingEntity owner, Vec3d pos, Vec3d vel, float damage) {
        SpectralShotEntity s = new SpectralShotEntity(world, owner, damage);
        s.setPosition(pos);
        s.setVelocity(vel);
        return s;
    }

    @Override
    protected Item getDefaultItem() { return ModItems.CANNON_BALL; }

    @Override
    protected float getGravity() { return 0.045f; }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) this.getWorld().addParticle(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY(), getZ(), 0, 0.01, 0);
        if (!this.getWorld().isClient && this.age > 200) this.discard();
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        Vec3d at = hit.getPos();
        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, at.x, at.y + 0.3, at.z, 50, 1.2, 0.6, 1.2, 0.05);
        sw.spawnParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.3, at.z, 2, 0.4, 0.2, 0.4, 0);
        sw.spawnParticles(ParticleTypes.SCULK_SOUL, at.x, at.y + 0.3, at.z, 12, 0.8, 0.4, 0.8, 0.05);
        sw.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 1.4f, 1.3f);
        LivingEntity owner = getOwner() instanceof LivingEntity o ? o : null;
        for (LivingEntity e : RelicWeapons.around(sw, at, 3.0, owner)) {
            if (owner != null) RelicWeapons.hit(e, owner, damage);
            else e.damage(e.getDamageSources().magic(), damage);
            RelicWeapons.knock(e, e.getPos().subtract(at), 0.5, 0.35);
        }
        this.discard();
    }
}
