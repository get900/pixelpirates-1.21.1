package net.get900.pixelpirates.entity.custom;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimAroundGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.get900.pixelpirates.sound.ModSounds;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.EnumSet;

/**
 * Phase 1 sea hostile. Treads water singing; her song drags nearby players toward her
 * (a helmet halves the pull, a turtle shell blocks it) and makes them queasy, then she
 * rakes them with her claws. Breaking line of sight ends the pull.
 */
public class SirenEntity extends AquaticHostileEntity implements GeoEntity {
    public static final String ACTION = "action";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation SING = RawAnimation.begin().thenPlay("sing");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");

    private static final TrackedData<Boolean> SINGING = DataTracker.registerData(SirenEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private int songCooldown = 60;

    public SirenEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 1.1)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(SINGING, false);
    }

    public boolean isSinging() { return this.dataTracker.get(SINGING); }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SongGoal());
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.2, true));
        this.goalSelector.add(5, new SwimAroundGoal(this, 1.0, 40));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (songCooldown > 0) songCooldown--;
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) {
            triggerAnim(ACTION, "attack");
            this.playSound(ModSounds.SIREN_ATTACK, 1.0f, 1.0f);
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            if (isSinging() && this.random.nextInt(3) == 0) {
                this.getWorld().addParticle(ParticleTypes.NOTE, this.getParticleX(0.8), this.getY() + 2.0 + this.random.nextDouble() * 0.5,
                        this.getParticleZ(0.8), this.random.nextInt(25) / 24.0, 0, 0);
            } else if (this.isTouchingWater() && this.random.nextInt(12) == 0) {
                this.getWorld().addParticle(ParticleTypes.BUBBLE, this.getParticleX(0.5), this.getRandomBodyY(),
                        this.getParticleZ(0.5), 0, 0.05, 0);
            }
        }
    }

    // ------------------------------------------------------------------ song
    private class SongGoal extends Goal {
        private int ticks;

        SongGoal() { this.setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }

        @Override
        public boolean canStart() {
            LivingEntity t = getTarget();
            return songCooldown <= 0 && t != null && t.isAlive() && squaredDistanceTo(t) > 9 && squaredDistanceTo(t) < 400 && canSee(t);
        }

        @Override
        public boolean shouldContinue() { return ticks < 60 && isAlive(); }

        @Override
        public void start() {
            ticks = 0;
            getNavigation().stop();
            dataTracker.set(SINGING, true);
            triggerAnim(ACTION, "sing");
        }

        @Override
        public void stop() {
            dataTracker.set(SINGING, false);
            songCooldown = 200 + random.nextInt(100);
        }

        @Override
        public void tick() {
            ticks++;
            LivingEntity t = getTarget();
            if (t != null) getLookControl().lookAt(t, 30f, 30f);
            if (ticks == 1) {
                // One ~3 s sung phrase covers the whole song (attenuation 32 blocks in sounds.json)
                getWorld().playSound(null, getX(), getY(), getZ(), ModSounds.SIREN_SONG, SoundCategory.HOSTILE, 2.0f, 1.0f);
            }
            if (!(getWorld() instanceof ServerWorld sw)) return;
            for (PlayerEntity p : sw.getPlayers(pl -> pl.isAlive() && !pl.isSpectator() && !pl.isCreative()
                    && pl.squaredDistanceTo(SirenEntity.this) < 16 * 16 && canSee(pl))) {
                ItemStack helm = p.getEquippedStack(EquipmentSlot.HEAD);
                if (helm.isOf(Items.TURTLE_HELMET)) continue;          // sea-wise sailors are immune
                double strength = helm.isEmpty() ? 0.05 : 0.025;
                Vec3d pull = getPos().add(0, 0.5, 0).subtract(p.getPos());
                if (pull.lengthSquared() < 4) continue;
                p.addVelocity(pull.normalize().multiply(strength));
                p.velocityModified = true;
                if (ticks == 20) p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 120, 0), SirenEntity.this);
                if (ticks % 10 == 0) {
                    sw.spawnParticles(ParticleTypes.NOTE, p.getX(), p.getY() + 2.2, p.getZ(), 1, 0.3, 0.1, 0.3, 1.0);
                }
            }
        }
    }

    // ------------------------------------------------------------------ drops & sounds
    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        int shards = this.random.nextInt(3);
        if (shards > 0) this.dropStack(new ItemStack(Items.PRISMARINE_SHARD, shards));
        if (this.random.nextFloat() < 0.6f) this.dropStack(new ItemStack(net.get900.pixelpirates.item.ModItems.SIREN_SCALE, 1 + this.random.nextInt(2)));
        if (causedByPlayer && this.random.nextFloat() < 0.05f) this.dropStack(new ItemStack(net.get900.pixelpirates.item.ModItems.SIREN_CONCH));   // her song, kept
    }

    @Override
    protected SoundEvent getAmbientSound() { return isSinging() ? null : ModSounds.SIREN_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.SIREN_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return ModSounds.SIREN_DEATH; }
    @Override
    public float getSoundPitch() { return 0.95f + this.random.nextFloat() * 0.1f; }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movement));
        controllers.add(new AnimationController<SirenEntity>(this, ACTION, 2, s -> PlayState.STOP)
                .triggerableAnim("sing", SING)
                .triggerableAnim("attack", ATTACK));
    }

    private PlayState movement(AnimationState<SirenEntity> state) {
        return state.setAndContinue(state.isMoving() && this.isTouchingWater() ? SWIM : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
