package net.get900.pixelpirates.entity.custom;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WaterCreatureEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.get900.pixelpirates.sound.ModSounds;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Phase 2 reef drifter. Passive - it never chases - but it jets along in pulses (in time with
 * its 2 s bell animation) and anything that brushes its tentacles is stung and poisoned.
 * Glows (emissive fins/tips) and sheds glow motes; drops glow ink.
 */
public class CoralJellyEntity extends WaterCreatureEntity implements GeoEntity {
    private static final RawAnimation PULSE = RawAnimation.begin().thenLoop("pulse");
    private static final RawAnimation STING = RawAnimation.begin().thenPlay("sting");
    private static final int PULSE_TICKS = 40;   // == pulse animation length (2.0 s)

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private Vec3d heading = Vec3d.ZERO;
    private int stingCooldown;

    public CoralJellyEntity(EntityType<? extends WaterCreatureEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 8.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2);
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (this.getWorld().isClient) {
            if (this.random.nextInt(8) == 0) {
                this.getWorld().addParticle(ParticleTypes.GLOW, this.getParticleX(0.6), this.getRandomBodyY(),
                        this.getParticleZ(0.6), 0, 0.01, 0);
            }
            return;
        }
        if (this.isTouchingWater()) {
            if (this.age % PULSE_TICKS == 8) {       // contraction frame of the pulse animation
                if (heading == Vec3d.ZERO || this.random.nextInt(3) == 0) {
                    heading = new Vec3d(this.random.nextGaussian(), 0.4 + this.random.nextDouble() * 0.6, this.random.nextGaussian()).normalize();
                }
                // Don't jet out of the water: flatten upward thrust near the surface
                BlockPos above = this.getBlockPos().up(2);
                Vec3d thrust = this.getWorld().getFluidState(above).isIn(FluidTags.WATER) ? heading : new Vec3d(heading.x, -0.2, heading.z);
                this.setVelocity(this.getVelocity().add(thrust.multiply(0.22)));
                ((ServerWorld) this.getWorld()).spawnParticles(ParticleTypes.BUBBLE, this.getX(), this.getY() + 0.2, this.getZ(), 4, 0.2, 0.1, 0.2, 0.02);
            }
            this.setVelocity(this.getVelocity().multiply(0.94).add(0, -0.004, 0));
            try {
                this.move(MovementType.SELF, this.getVelocity());
            } catch (NullPointerException ignored) { }
            float yaw = (float) Math.toDegrees(Math.atan2(-heading.x, heading.z));
            this.setYaw(yaw);
            this.bodyYaw = yaw;
        }
        if (stingCooldown > 0) stingCooldown--;
        else stingTouching();
    }

    private void stingTouching() {
        boolean stung = false;
        for (LivingEntity e : this.getWorld().getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(0.25),
                e -> e != this && !(e instanceof CoralJellyEntity) && e.isAlive()
                        && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())))) {
            if (e.damage(this.getDamageSources().mobAttack(this), 2.0f)) {
                e.addStatusEffect(new StatusEffectInstance(StatusEffects.POISON, 100, 0), this);
                stung = true;
            }
        }
        if (stung) {
            stingCooldown = 20;
            triggerAnim("action", "sting");
            this.getWorld().playSound(null, this.getBlockPos(), ModSounds.CORAL_JELLY_STING, SoundCategory.NEUTRAL, 0.9f, 0.9f + this.random.nextFloat() * 0.2f);
        }
    }

    @Override
    public void travel(Vec3d input) {
        // Propulsion is handled by the pulse in tickMovement; out of water it just falls and flops.
        if (!this.isTouchingWater()) super.travel(input);
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        this.dropStack(new ItemStack(Items.GLOW_INK_SAC, 1 + this.random.nextInt(2)));
        if (this.random.nextFloat() < 0.3f) this.dropStack(new ItemStack(Items.SLIME_BALL));
    }

    public static boolean canSpawn(EntityType<CoralJellyEntity> type, ServerWorldAccess world, SpawnReason reason,
                                   BlockPos pos, Random random) {
        return world.getFluidState(pos).isIn(FluidTags.WATER) && world.getFluidState(pos.up()).isIn(FluidTags.WATER)
                && world.getFluidState(pos.down()).isIn(FluidTags.WATER);
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.CORAL_JELLY_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return ModSounds.CORAL_JELLY_DEATH; }
    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.CORAL_JELLY_AMBIENT; }
    @Override
    protected float getSoundVolume() { return 0.6f; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "pulse", 0, s -> s.setAndContinue(PULSE)));
        controllers.add(new AnimationController<CoralJellyEntity>(this, "action", 0, s -> PlayState.STOP)
                .triggerableAnim("sting", STING));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
