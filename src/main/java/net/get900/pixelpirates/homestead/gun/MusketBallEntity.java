package net.get900.pixelpirates.homestead.gun;

import net.get900.pixelpirates.homestead.HomesteadEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.world.World;

/**
 * A lead ball from a flintlock or a blunderbuss pellet: fast, nearly flat, a thin smoke trail, gone after its range.
 * Pellets clear the target's hurt cooldown so a whole blast can land at once.
 */
public class MusketBallEntity extends ThrownEntity {
    private float damage = 12f;
    private int life = 40;
    private boolean pellet;

    public MusketBallEntity(EntityType<? extends ThrownEntity> type, World world) { super(type, world); }

    public MusketBallEntity(World world, LivingEntity owner, float damage, int life, boolean pellet) {
        super(HomesteadEntities.MUSKET_BALL, owner, world);
        this.damage = damage;
        this.life = life;
        this.pellet = pellet;
        this.setPosition(owner.getX(), owner.getEyeY() - 0.12, owner.getZ());
    }

    @Override
    protected void initDataTracker() {}

    @Override
    protected float getGravity() { return pellet ? 0.02f : 0.008f; }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld() instanceof ServerWorld sw) {
            if (this.age % 2 == 0) sw.spawnParticles(pellet ? ParticleTypes.SMOKE : ParticleTypes.CRIT, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            if (this.age > life) this.discard();
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Entity t = hit.getEntity();
        Entity owner = this.getOwner();
        if (t == owner) return;
        if (pellet) t.timeUntilRegen = 0;
        t.damage(this.getDamageSources().mobProjectile(this, owner instanceof LivingEntity l ? l : null), damage);
        if (this.getWorld() instanceof ServerWorld sw) sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, getX(), getY(), getZ(), pellet ? 1 : 4, 0.1, 0.1, 0.1, 0.1);
        this.discard();
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        super.onBlockHit(hit);
        if (this.getWorld() instanceof ServerWorld sw) {
            var state = sw.getBlockState(hit.getBlockPos());
            sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, state), getX(), getY(), getZ(), pellet ? 3 : 8, 0.05, 0.05, 0.05, 0.1);
            if (!pellet) sw.playSound(null, hit.getBlockPos(), state.getSoundGroup().getHitSound(), SoundCategory.BLOCKS, 0.8f, 1.2f);
        }
        this.discard();
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putFloat("Damage", damage);
        nbt.putInt("Life", life);
        nbt.putBoolean("Pellet", pellet);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Damage")) damage = nbt.getFloat("Damage");
        if (nbt.contains("Life")) life = nbt.getInt("Life");
        pellet = nbt.getBoolean("Pellet");
    }
}
