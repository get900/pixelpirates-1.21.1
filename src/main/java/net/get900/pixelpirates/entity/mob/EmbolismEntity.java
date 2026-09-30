package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * EMBOLISM - a clot the Abyssal Heart throws into its blood ring. It rides the current round the ring (it never leaves
 * it), battering anyone swimming there; lasts 30 s, or hack it apart.
 */
public class EmbolismEntity extends ModMob {
    private double cx, cz, radius = -1, ringY, angle, speed = 0.36;
    private int life = 600;
    private java.util.UUID heart;

    public EmbolismEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    /** Place it on the ring (centre, radius, bed height) at `angle`, travelling `speed` blocks a tick. */
    public void setRing(double cx, double cz, double radius, double y, double angle, double speed) {
        this.cx = cx; this.cz = cz; this.radius = radius; this.ringY = y; this.angle = angle; this.speed = speed;
        this.refreshPositionAndAngles(cx + Math.cos(angle) * radius, y, cz + Math.sin(angle) * radius, 0, 0);
    }

    /** The heart that threw it: killing it wounds that heart (AbyssalHeartEntity#onEmbolismKilled). */
    public void setHeart(java.util.UUID h) { heart = h; }

    @Override
    public void onDeath(net.minecraft.entity.damage.DamageSource source) {
        super.onDeath(source);
        if (heart != null && source.getAttacker() instanceof net.minecraft.entity.player.PlayerEntity && this.getWorld() instanceof ServerWorld sw
                && sw.getEntity(heart) instanceof AbyssalHeartEntity h) h.onEmbolismKilled(this);
    }

    @Override
    protected boolean scriptedMotion() { return true; }

    @Override
    protected List<Abilities.Ability> activeAbilities() { return List.of(); }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (radius < 0 || --life <= 0) { this.discard(); return; }
        angle += speed / radius;
        double bob = Math.sin(this.age * 0.3) * 0.4;
        Vec3d to = new Vec3d(cx + Math.cos(angle) * radius, ringY + bob, cz + Math.sin(angle) * radius);
        this.setYaw((float) Math.toDegrees(Math.atan2(-Math.sin(angle), -Math.cos(angle))) - 90f);
        this.setBodyYaw(this.getYaw());
        this.setPosition(to);
        this.setVelocity(Vec3d.ZERO);
        if (this.age % 3 == 0) sw.spawnParticles(new DustParticleEffect(AbyssalHeartEntity.BLOOD, 1.8f), getX(), getY() + 0.6, getZ(), 4, 0.5, 0.4, 0.5, 0);
        if (this.age % 10 == 0)
            for (ServerPlayerEntity p : sw.getEntitiesByClass(ServerPlayerEntity.class, getBoundingBox().expand(0.4), p -> p.isAlive() && !p.isSpectator() && !p.isCreative())) {
                p.damage(this.getDamageSources().mobAttack(this), 7f);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1), this);
                p.addVelocity(-Math.sin(angle) * 0.8, 0.3, Math.cos(angle) * 0.8);
                p.velocityModified = true;
                triggerAnim(ACTION, "attack");
            }
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected SoundEvent sound(String kind) {
        return switch (kind) {
            case "hurt" -> SoundEvents.ENTITY_SLIME_HURT;
            case "death" -> SoundEvents.ENTITY_SLIME_DEATH;
            case "attack" -> SoundEvents.ENTITY_SLIME_ATTACK;
            default -> null;
        };
    }

    @Override
    public float getSoundPitch() { return 0.5f; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putDouble("RingX", cx); nbt.putDouble("RingZ", cz); nbt.putDouble("RingR", radius); nbt.putDouble("RingY", ringY);
        nbt.putDouble("Angle", angle); nbt.putDouble("Speed", speed); nbt.putInt("Life", life);
        if (heart != null) nbt.putUuid("Heart", heart);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        cx = nbt.getDouble("RingX"); cz = nbt.getDouble("RingZ"); radius = nbt.contains("RingR") ? nbt.getDouble("RingR") : -1;
        ringY = nbt.getDouble("RingY"); angle = nbt.getDouble("Angle"); speed = nbt.getDouble("Speed"); life = nbt.getInt("Life");
        if (nbt.containsUuid("Heart")) heart = nbt.getUuid("Heart");
    }
}
