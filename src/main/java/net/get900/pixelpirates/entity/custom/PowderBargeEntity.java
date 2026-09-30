package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * POWDER BARGE - a raft stacked with gunpowder barrels (THE POISONED MEAL, Leviathan phase 2). It floats and bobs where
 * you set it; the hungry Leviathan goes for it like any other meal (LeviathanEntity), and swallowed it detonates inside
 * it. Shoot it, set it alight or blow it up and it goes off where it floats instead (a big blast - keep your ship clear).
 * Rendered from real blocks (PowderBargeRenderer). Lasts 10 minutes.
 */
public class PowderBargeEntity extends Entity {
    private int life = 12000;

    public PowderBargeEntity(EntityType<? extends PowderBargeEntity> type, World world) {
        super(type, world);
    }

    public static PowderBargeEntity at(World world, Vec3d pos, float yaw) {
        PowderBargeEntity b = new PowderBargeEntity(ModEntities.POWDER_BARGE, world);
        b.refreshPositionAndAngles(pos.x, pos.y, pos.z, yaw, 0);
        return b;
    }

    @Override
    protected void initDataTracker() { }

    @Override
    public void tick() {
        super.tick();
        // float: hold the deck at the waterline, drift a little, fall when out of the water
        double surface = surfaceY();
        Vec3d v = getVelocity().multiply(0.9, 1, 0.9);
        if (Double.isNaN(surface)) v = v.add(0, -0.04, 0);
        else v = new Vec3d(v.x, Math.max(-0.1, Math.min(0.1, (surface - 0.35 - getY()) * 0.3)), v.z);
        setVelocity(v);
        try { move(MovementType.SELF, v); } catch (NullPointerException ignored) { }       // VS2 race (crash cause #5)
        setYaw(getYaw() + (float) Math.sin(age * 0.05) * 0.2f);
        if (!getWorld().isClient) {
            if (age % 20 == 0) ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.SMOKE, getX(), getY() + 1.6, getZ(), 1, 0.2, 0.1, 0.2, 0.01);
            if (isOnFire() || --life <= 0) blow();
        }
    }

    /** The top of the water column it is in (NaN if it isn't in water). */
    private double surfaceY() {
        BlockPos.Mutable p = getBlockPos().mutableCopy();
        if (!getWorld().getFluidState(p).isIn(FluidTags.WATER) && !getWorld().getFluidState(p.down()).isIn(FluidTags.WATER)) return Double.NaN;
        if (!getWorld().getFluidState(p).isIn(FluidTags.WATER)) return p.getY();
        for (int i = 0; i < 64 && getWorld().getFluidState(p).isIn(FluidTags.WATER); i++) p.move(0, 1, 0);
        return p.getY();
    }

    /** Set off where it floats. */
    public void blow() {
        if (getWorld().isClient || isRemoved()) return;
        getWorld().createExplosion(this, getX(), getY() + 0.5, getZ(), 5.0f, World.ExplosionSourceType.NONE);
        discard();
    }

    /** Swallowed: gone without a bang (the Leviathan takes the blast inside - LeviathanEntity). */
    public void swallowed() { discard(); }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) return false;
        if (!getWorld().isClient && (source.isIn(DamageTypeTags.IS_EXPLOSION) || source.isIn(DamageTypeTags.IS_FIRE) || source.isIn(DamageTypeTags.IS_PROJECTILE)
                || source.getAttacker() instanceof PlayerEntity && amount >= 6)) blow();
        return true;
    }

    @Override
    public boolean canHit() { return !isRemoved(); }

    @Override
    public boolean isCollidable() { return true; }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) { life = nbt.getInt("Life"); }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) { nbt.putInt("Life", life); }
}
