package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimAroundGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.FishEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.get900.pixelpirates.sound.ModSounds;
import net.minecraft.util.math.MathHelper;
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
 * Phase 5 deep-sea predator. Hunts fish and swimmers; its glowing lure trails light motes.
 * When a target is in sight it flares the lure (blinding players who are looking its way),
 * then lunges and snaps its jaws.
 */
public class AbyssalAnglerEntity extends AquaticHostileEntity implements GeoEntity {
    public static final String ACTION = "action";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("bite");
    private static final RawAnimation FLASH = RawAnimation.begin().thenPlay("lure_flash");

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private int flashCooldown = 60;
    private int lungeCooldown = 40;

    public AbyssalAnglerEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 15;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 45.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 1.2)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new LureFlashGoal());
        this.goalSelector.add(2, new LungeGoal());
        this.goalSelector.add(3, new MeleeAttackGoal(this, 1.3, true));
        this.goalSelector.add(5, new SwimAroundGoal(this, 1.0, 30));
        this.goalSelector.add(6, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, LivingEntity::isTouchingWater));
        this.targetSelector.add(3, new ActiveTargetGoal<>(this, FishEntity.class, 20, true, false, null));
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (flashCooldown > 0) flashCooldown--;
        if (lungeCooldown > 0) lungeCooldown--;
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) {
            triggerAnim(ACTION, "bite");
            this.playSound(ModSounds.ANGLER_BITE, 1.0f, 0.9f + this.random.nextFloat() * 0.2f);
        }
        return hit;
    }

    /** World position of the glowing lure bulb (front, above the head). */
    private Vec3d lurePos() {
        float yaw = this.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
        return this.getPos().add(-MathHelper.sin(yaw) * 0.65, 1.15, MathHelper.cos(yaw) * 0.65);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient && this.age % 3 == 0) {
            Vec3d l = lurePos();
            this.getWorld().addParticle(ParticleTypes.GLOW, l.x + (this.random.nextDouble() - 0.5) * 0.2, l.y,
                    l.z + (this.random.nextDouble() - 0.5) * 0.2, 0, -0.01, 0);
        }
    }

    private class LureFlashGoal extends Goal {
        private int ticks;

        LureFlashGoal() { this.setControls(EnumSet.of(Control.LOOK)); }

        @Override
        public boolean canStart() {
            LivingEntity t = getTarget();
            return flashCooldown <= 0 && t instanceof PlayerEntity && squaredDistanceTo(t) < 14 * 14 && canSee(t);
        }

        @Override
        public boolean shouldContinue() { return ticks < 20; }

        @Override
        public void start() {
            ticks = 0;
            triggerAnim(ACTION, "lure_flash");
        }

        @Override
        public void stop() { flashCooldown = 140 + random.nextInt(60); }

        @Override
        public void tick() {
            ticks++;
            if (getTarget() != null) getLookControl().lookAt(getTarget(), 30f, 30f);
            if (ticks != 5 || !(getWorld() instanceof ServerWorld sw)) return;
            Vec3d l = lurePos();
            sw.spawnParticles(ParticleTypes.FLASH, l.x, l.y, l.z, 1, 0, 0, 0, 0);
            sw.spawnParticles(ParticleTypes.GLOW, l.x, l.y, l.z, 20, 0.5, 0.5, 0.5, 0.1);
            sw.playSound(null, l.x, l.y, l.z, ModSounds.ANGLER_LURE, SoundCategory.HOSTILE, 1.2f, 1.0f);
            for (PlayerEntity p : sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative()
                    && p.squaredDistanceTo(l) < 12 * 12 && canSee(p))) {
                // Only players facing the lure are dazzled
                Vec3d toLure = l.subtract(p.getEyePos()).normalize();
                if (p.getRotationVec(1.0f).dotProduct(toLure) > 0.5) {
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0), AbyssalAnglerEntity.this);
                }
            }
            lungeCooldown = 0;   // the flash is the tell - it lunges right after
        }
    }

    private class LungeGoal extends Goal {
        private int ticks;

        LungeGoal() { this.setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }

        @Override
        public boolean canStart() {
            LivingEntity t = getTarget();
            if (lungeCooldown > 0 || t == null || !isTouchingWater() || !canSee(t)) return false;
            double d = squaredDistanceTo(t);
            return d > 3 * 3 && d < 10 * 10;
        }

        @Override
        public boolean shouldContinue() { return ticks < 12; }

        @Override
        public void start() {
            ticks = 0;
            LivingEntity t = getTarget();
            if (t == null) return;
            Vec3d dir = t.getPos().add(0, t.getHeight() * 0.5, 0).subtract(getPos()).normalize();
            setVelocity(dir.multiply(1.1));
            velocityDirty = true;
            playSound(SoundEvents.ENTITY_DOLPHIN_JUMP, 1.0f, 0.5f);
        }

        @Override
        public void stop() { lungeCooldown = 80 + random.nextInt(40); }

        @Override
        public void tick() {
            ticks++;
            LivingEntity t = getTarget();
            if (t == null) return;
            getLookControl().lookAt(t, 30f, 30f);
            if (getBoundingBox().expand(0.6).intersects(t.getBoundingBox())) {
                tryAttack(t);
                ticks = 12;
            }
        }
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        this.dropStack(new ItemStack(Items.GLOW_INK_SAC, 1 + this.random.nextInt(3)));
        int crystals = this.random.nextInt(3);
        if (crystals > 0) this.dropStack(new ItemStack(Items.PRISMARINE_CRYSTALS, crystals));
        if (this.random.nextFloat() < 0.25f) this.dropStack(new ItemStack(ModItems.KRAKEN_SCALE));
    }

    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.ANGLER_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.ANGLER_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return ModSounds.ANGLER_DEATH; }
    @Override
    public float getSoundPitch() { return 0.9f + this.random.nextFloat() * 0.2f; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movement));
        controllers.add(new AnimationController<AbyssalAnglerEntity>(this, ACTION, 0, s -> PlayState.STOP)
                .triggerableAnim("bite", BITE)
                .triggerableAnim("lure_flash", FLASH));
    }

    private PlayState movement(AnimationState<AbyssalAnglerEntity> state) {
        return state.setAndContinue(state.isMoving() ? SWIM : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
