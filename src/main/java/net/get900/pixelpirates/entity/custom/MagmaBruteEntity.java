package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
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
import net.minecraft.util.math.BlockPos;
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
 * Phase 3 volcanic tank. Slow, heavy-hitting, immune to fire and lava. Every few seconds in
 * close range it raises both fists and slams the ground: a fiery shockwave that damages,
 * ignites and hurls everything within 4 blocks. Water is its weakness - rain or a splash
 * makes it hiss and take damage.
 */
public class MagmaBruteEntity extends HostileEntity implements GeoEntity {
    public static final String ACTION = "action";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("attack");
    private static final RawAnimation SLAM = RawAnimation.begin().thenPlay("slam");
    /** Ticks from slam trigger to impact - matches the fists hitting the ground at 0.8 s. */
    private static final int SLAM_IMPACT_TICK = 16;

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private int slamCooldown = 60;

    public MagmaBruteEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 20;
        this.setPathfindingPenalty(PathNodeType.LAVA, 0.0f);
        this.setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 0.0f);
        this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, 0.0f);
        this.setPathfindingPenalty(PathNodeType.WATER, -1.0f);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 90.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.2)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 11.0)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, 1.5)
                .add(EntityAttributes.GENERIC_ARMOR, 10.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 28.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SlamGoal());
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.6, 100));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(7, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public boolean isFireImmune() { return true; }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) {
            target.setOnFireFor(4);
            triggerAnim(ACTION, "attack");
        }
        return hit;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (slamCooldown > 0) slamCooldown--;
        // Doused: steam + damage, the counterplay for a mob that shrugs off everything else
        if (this.isWet() && this.age % 10 == 0) {
            this.damage(this.getDamageSources().drown(), 2.0f);
            ((ServerWorld) this.getWorld()).spawnParticles(ParticleTypes.CLOUD, this.getX(), this.getY() + 1.5, this.getZ(),
                    6, 0.6, 0.8, 0.6, 0.02);
            this.playSound(SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.7f, 0.8f + this.random.nextFloat() * 0.3f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            if (this.random.nextInt(3) == 0) {
                this.getWorld().addParticle(ParticleTypes.SMOKE, this.getParticleX(0.7), this.getY() + 1.6 + this.random.nextDouble() * 0.8,
                        this.getParticleZ(0.7), 0, 0.03, 0);
            }
            if (this.random.nextInt(14) == 0) {
                this.getWorld().addParticle(ParticleTypes.LAVA, this.getParticleX(0.6), this.getRandomBodyY(), this.getParticleZ(0.6), 0, 0, 0);
            }
        }
    }

    private class SlamGoal extends Goal {
        private int ticks;

        SlamGoal() { this.setControls(EnumSet.of(Control.MOVE, Control.LOOK, Control.JUMP)); }

        @Override
        public boolean canStart() {
            LivingEntity t = getTarget();
            return slamCooldown <= 0 && isOnGround() && t != null && t.isAlive() && squaredDistanceTo(t) < 4.5 * 4.5;
        }

        @Override
        public boolean shouldContinue() { return ticks < 32 && isAlive(); }

        @Override
        public void start() {
            ticks = 0;
            getNavigation().stop();
            triggerAnim(ACTION, "slam");
            playSound(ModSounds.MAGMA_BRUTE_ROAR, 1.5f, 1.0f);
        }

        @Override
        public void stop() { slamCooldown = 100 + random.nextInt(40); }

        @Override
        public void tick() {
            ticks++;
            LivingEntity t = getTarget();
            if (t != null) getLookControl().lookAt(t, 30f, 30f);
            if (ticks == SLAM_IMPACT_TICK) impact();
        }

        private void impact() {
            if (!(getWorld() instanceof ServerWorld sw)) return;
            Vec3d c = getPos();
            sw.playSound(null, c.x, c.y, c.z, ModSounds.MAGMA_BRUTE_SLAM, SoundCategory.HOSTILE, 2.5f, 0.9f + random.nextFloat() * 0.2f);
            sw.spawnParticles(ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 2, 0.5, 0.1, 0.5, 0);
            for (int ring = 1; ring <= 3; ring++) {
                int n = ring * 10;
                for (int i = 0; i < n; i++) {
                    double a = 2 * Math.PI * i / n, r = ring * 1.3;
                    sw.spawnParticles(ParticleTypes.FLAME, c.x + Math.cos(a) * r, c.y + 0.15, c.z + Math.sin(a) * r, 1, 0, 0.05, 0, 0.02);
                }
            }
            sw.spawnParticles(ParticleTypes.LAVA, c.x, c.y + 0.3, c.z, 14, 1.5, 0.2, 1.5, 0);
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y + 0.4, c.z, 16, 2.0, 0.3, 2.0, 0.02);
            for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(4.0, 1.5, 4.0),
                    e -> e != MagmaBruteEntity.this && e.isAlive() && !(e instanceof MagmaBruteEntity)
                            && !(e instanceof PlayerEntity p && (p.isCreative() || p.isSpectator())))) {
                double dist = e.distanceTo(MagmaBruteEntity.this);
                if (dist > 4.5) continue;
                float dmg = (float) (12.0 - dist * 1.5);
                if (e.damage(getDamageSources().mobAttack(MagmaBruteEntity.this), dmg)) {
                    e.setOnFireFor(3);
                    Vec3d away = e.getPos().subtract(c).multiply(1, 0, 1);
                    away = away.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : away.normalize();
                    double kb = 1.1 * (1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE));
                    e.addVelocity(away.x * kb, 0.55, away.z * kb);
                    e.velocityModified = true;
                }
            }
        }
    }

    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        this.dropStack(new ItemStack(ModItems.VOLCANIC_EMBER, 1 + this.random.nextInt(3)));
        int cream = this.random.nextInt(3);
        if (cream > 0) this.dropStack(new ItemStack(Items.MAGMA_CREAM, cream));
        if (causedByPlayer && this.random.nextFloat() < 0.15f) this.dropStack(new ItemStack(Items.OBSIDIAN));
    }

    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.MAGMA_BRUTE_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.MAGMA_BRUTE_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return ModSounds.MAGMA_BRUTE_DEATH; }
    @Override
    public float getSoundPitch() { return 0.9f + this.random.nextFloat() * 0.2f; }
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) { this.playSound(ModSounds.MAGMA_BRUTE_STEP, 0.8f, 0.9f + this.random.nextFloat() * 0.2f); }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movement));
        controllers.add(new AnimationController<MagmaBruteEntity>(this, ACTION, 0, s -> PlayState.STOP)
                .triggerableAnim("attack", ATTACK)
                .triggerableAnim("slam", SLAM));
    }

    private PlayState movement(AnimationState<MagmaBruteEntity> state) {
        return state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
