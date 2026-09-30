package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.entity.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;

/**
 * One projectile for every mob ranged attack. Its look is the item it carries (fire charge,
 * trident, ink sac, prismarine shard...); damage, status effect, fire and trail particle are set
 * by the {@link Abilities#projectile} that fires it. Never damages blocks.
 */
public class MobProjectileEntity extends ThrownItemEntity {
    private float damage = 4f;
    private StatusEffect effect;
    private int effectTicks, effectAmp, fireSeconds;
    private ParticleEffect trail = ParticleTypes.SMOKE;
    private float gravity = 0.01f;

    public MobProjectileEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public MobProjectileEntity(World world, LivingEntity owner, Abilities.Shot shot) {
        super(ModEntities.MOB_PROJECTILE, owner, world);
        this.setItem(new ItemStack(shot.look()));
        this.damage = shot.damage();
        this.effect = shot.effect();
        this.effectTicks = shot.effectTicks();
        this.effectAmp = shot.effectAmp();
        this.fireSeconds = shot.fireSeconds();
        this.trail = shot.trail();
        this.gravity = shot.gravity();
        this.setNoGravity(shot.gravity() <= 0f);
    }

    @Override
    protected Item getDefaultItem() { return Items.FIRE_CHARGE; }

    @Override
    protected float getGravity() { return gravity; }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld() instanceof ServerWorld sw && this.age % 2 == 0) {
            sw.spawnParticles(trail, this.getX(), this.getY(), this.getZ(), 1, 0.02, 0.02, 0.02, 0.0);
        }
        if (this.age > 100) this.discard();
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        Entity target = hit.getEntity();
        Entity owner = this.getOwner();
        if (target == owner || target instanceof ModMob && owner instanceof ModMob) return;   // no friendly fire between mod mobs
        target.damage(this.getDamageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null), damage);
        if (target instanceof LivingEntity le) {
            if (effect != null) le.addStatusEffect(new StatusEffectInstance(effect, effectTicks, effectAmp), owner);
            if (fireSeconds > 0) le.setOnFireFor(fireSeconds);
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!this.getWorld().isClient) {
            if (this.getWorld() instanceof ServerWorld sw) {
                sw.spawnParticles(trail, this.getX(), this.getY(), this.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            }
            this.discard();
        }
    }
}
