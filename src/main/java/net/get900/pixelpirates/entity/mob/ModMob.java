package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.ai.control.AquaticMoveControl;
import net.minecraft.entity.ai.control.FlightMoveControl;
import net.minecraft.entity.ai.control.YawAdjustingLookControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.FlyGoal;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimAroundGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.pathing.BirdNavigation;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.ai.pathing.SwimNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One entity class for every data-driven mob. Behaviour comes from the {@link MobSpec} registered
 * under this entity's id (looked up lazily, because MobEntity's constructor calls initGoals()
 * before subclass fields exist). Animations: "idle"/"move" loop on the movement controller;
 * "attack" and each ability's anim are triggerable on the action controller.
 * Sounds: pixelpirates:entity.ID.{ambient,hurt,death,attack,special} (ModMobs registers them).
 */
public class ModMob extends PathAwareEntity implements GeoEntity {
    public static final String ACTION = "action";
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation MOVE = RawAnimation.begin().thenLoop("move");

    private MobSpec spec;
    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    final Map<String, Integer> cooldowns = new HashMap<>();
    int dashing;
    private int dryTicks;

    public ModMob(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        MobSpec s = spec();
        this.experiencePoints = s.xp;
        switch (s.kind) {
            case SWIM -> {
                this.moveControl = new AquaticMoveControl(this, 85, 10, 0.02f, 0.1f, true);
                this.lookControl = new YawAdjustingLookControl(this, 10);
                this.setPathfindingPenalty(PathNodeType.WATER, 0.0f);
            }
            case FLY -> {
                this.moveControl = new FlightMoveControl(this, 20, true);
                this.setNoGravity(true);
                this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, -1.0f);
            }
            default -> { }
        }
        if (s.fireImmune) {
            this.setPathfindingPenalty(PathNodeType.LAVA, 0.0f);
            this.setPathfindingPenalty(PathNodeType.DAMAGE_FIRE, 0.0f);
            this.setPathfindingPenalty(PathNodeType.DANGER_FIRE, 0.0f);
        }
        if (s.hurtByWater) this.setPathfindingPenalty(PathNodeType.WATER, -1.0f);
        if (s.seabed) {
            this.setPathfindingPenalty(PathNodeType.WATER, 0.0f);
            this.setPathfindingPenalty(PathNodeType.WATER_BORDER, 0.0f);
            this.setStepHeight(1.0f);
        }
    }

    public MobSpec spec() {
        if (spec == null) spec = MobSpecs.get(Registries.ENTITY_TYPE.getId(this.getType()).getPath());
        return spec;
    }

    public static DefaultAttributeContainer.Builder attributes(MobSpec s) {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, s.health)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, s.damage)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, s.speed)
                .add(EntityAttributes.GENERIC_FLYING_SPEED, s.speed * 1.6)
                .add(EntityAttributes.GENERIC_ARMOR, s.armor)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, s.knockbackRes)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, s.follow)
                .add(EntityAttributes.GENERIC_ATTACK_KNOCKBACK, s.boss ? 1.2 : 0.3);
    }

    // ------------------------------------------------------------------ navigation & goals
    @Override
    protected EntityNavigation createNavigation(World world) {
        return switch (spec().kind) {
            case SWIM -> new SwimNavigation(this, world);
            case FLY -> {
                BirdNavigation nav = new BirdNavigation(this, world);
                nav.setCanPathThroughDoors(false);
                nav.setCanSwim(true);
                nav.setCanEnterOpenDoors(true);
                yield nav;
            }
            default -> super.createNavigation(world);
        };
    }

    @Override
    protected void initGoals() {
        MobSpec s = spec();
        if (s.kind == MobSpec.Kind.GROUND && !s.seabed) this.goalSelector.add(0, new SwimGoal(this));
        this.goalSelector.add(1, new AbilityGoal());
        if (s.melee && s.kind != MobSpec.Kind.STATIONARY && s.temper != MobSpec.Temper.PASSIVE)
            this.goalSelector.add(2, new MeleeAttackGoal(this, s.chaseSpeed, true));
        switch (s.kind) {
            case GROUND -> this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8, 80));
            case SWIM -> this.goalSelector.add(5, new SwimAroundGoal(this, 1.0, 40));
            case FLY -> this.goalSelector.add(5, new FlyGoal(this, 1.0));
            default -> { }
        }
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        if (s.kind != MobSpec.Kind.STATIONARY) this.goalSelector.add(7, new LookAroundGoal(this));
        if (s.temper != MobSpec.Temper.PASSIVE) this.targetSelector.add(1, new RevengeGoal(this));
        if (s.temper == MobSpec.Temper.HOSTILE) {
            this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true,
                    p -> mayTarget(p) && (s.kind != MobSpec.Kind.SWIM || s.floats || p.isTouchingWater() || p.squaredDistanceTo(this) < s.reach * s.reach)));
        }
    }

    /** Alternate skin to render (textures/entity/ID_VARIANT.png, same geometry), or null for the base skin. */
    public String skinVariant() { return null; }

    /** Model bones this mob shows/hides at runtime (GlowingMobRenderer applies {@link #isBoneHidden} each frame). */
    public List<String> toggleBones() { return List.of(); }

    public boolean isBoneHidden(String bone) { return false; }

    /** Additional triggerable clips a subclass fires itself (not tied to an Ability). */
    protected List<String> extraAnims() { return List.of(); }

    /** A loop that replaces idle/move on the movement controller while non-null (e.g. the Revenant hanging in chains). */
    protected RawAnimation movementOverride() { return null; }

    /** Extra gate on hunting a player (sealed bosses ignore players who haven't reached them). */
    protected boolean mayTarget(LivingEntity player) { return true; }

    /** Abilities currently available (bosses add their phase-2 set when enraged). */
    protected List<Abilities.Ability> activeAbilities() { return spec().abilities; }

    // ------------------------------------------------------------------ ability goal
    class AbilityGoal extends Goal {
        private Abilities.Ability current;
        private int ticks;
        private boolean fired;

        AbilityGoal() { this.setControls(EnumSet.of(Control.MOVE, Control.LOOK)); }

        @Override
        public boolean canStart() {
            if (age % 5 != 0) return false;
            LivingEntity t = getTarget();
            List<Abilities.Ability> list = activeAbilities();
            if (list.isEmpty()) return false;
            int start = random.nextInt(list.size());
            for (int i = 0; i < list.size(); i++) {
                Abilities.Ability a = list.get((start + i) % list.size());
                if (cooldowns.getOrDefault(a.name(), 0) > 0) continue;
                if (t == null || !t.isAlive()) {
                    if (a.selfCast() && getHealth() < getMaxHealth() * 0.6f && getAttacker() != null) { current = a; return true; }
                    continue;
                }
                double d = Math.sqrt(squaredDistanceTo(t));
                if (d < a.minRange() || d > a.maxRange()) continue;
                if (a.maxRange() > 4 && !canSee(t)) continue;
                current = a;
                return true;
            }
            return false;
        }

        @Override
        public boolean shouldContinue() { return current != null && ticks < current.channel() && isAlive(); }

        @Override
        public void start() {
            ticks = 0;
            fired = false;
            if (spec().kind != MobSpec.Kind.FLY) getNavigation().stop();
            triggerAnim(ACTION, current.anim());
            playSound(sound("special"), getSoundVolume(), getSoundPitch());
        }

        @Override
        public void stop() {
            if (current != null) cooldowns.put(current.name(), current.cooldown());
            current = null;
        }

        @Override
        public void tick() {
            ticks++;
            LivingEntity t = getTarget();
            if (t != null) getLookControl().lookAt(t, 30f, 30f);
            if (!fired && ticks >= current.windup()) {
                fired = true;
                current.effect().fire(ModMob.this, t);
            }
            // dash contact damage
            if (dashing > 0 && t != null && getBoundingBox().expand(0.5).intersects(t.getBoundingBox())) {
                tryAttack(t);
                dashing = 0;
            }
        }
    }

    // ------------------------------------------------------------------ ticking
    @Override
    protected void mobTick() {
        super.mobTick();
        MobSpec s = spec();
        cooldowns.replaceAll((k, v) -> Math.max(0, v - 1));
        if (dashing > 0) dashing--;
        if (s.kind == MobSpec.Kind.SWIM) {
            if (this.isTouchingWater()) dryTicks = 0;
            else if (++dryTicks > 120 && dryTicks % 20 == 0) this.damage(this.getDamageSources().dryOut(), 2.0f);
        }
        if (s.hurtByWater && this.isWet() && this.age % 10 == 0) {
            this.damage(this.getDamageSources().drown(), 2.0f);
            ((net.minecraft.server.world.ServerWorld) this.getWorld()).spawnParticles(net.minecraft.particle.ParticleTypes.CLOUD,
                    getX(), getY() + getHeight() * 0.6, getZ(), 5, 0.4, 0.4, 0.4, 0.02);
            this.playSound(net.minecraft.sound.SoundEvents.BLOCK_FIRE_EXTINGUISH, 0.6f, 1.0f);
        }
    }

    @Override
    public void tick() {
        super.tick();
        MobSpec s = spec();
        if (this.getWorld().isClient && s.trail != null && this.random.nextInt(s.trailChance) == 0) {
            this.getWorld().addParticle(s.trail, this.getParticleX(0.6), this.getRandomBodyY(), this.getParticleZ(0.6), 0, 0.02, 0);
        }
    }

    @Override
    public void tickMovement() {
        if (spec().kind == MobSpec.Kind.SWIM && !this.isTouchingWater() && this.isOnGround() && this.verticalCollision) {
            this.setVelocity(this.getVelocity().add((this.random.nextFloat() * 2 - 1) * 0.05, 0.4, (this.random.nextFloat() * 2 - 1) * 0.05));
            this.setOnGround(false);
            this.velocityDirty = true;
        }
        super.tickMovement();
    }

    /**
     * Final placement check for natural spawns AND spawners. MobEntity's default rejects any box
     * containing fluid, which silently blocked every swimmer (and underwater rooted mob) from ever
     * spawning; vanilla fish avoid it the same way (WaterCreatureEntity).
     */
    @Override
    public boolean canSpawn(net.minecraft.world.WorldView world) {
        MobSpec.Kind k = spec().kind;
        if (k == MobSpec.Kind.SWIM || k == MobSpec.Kind.STATIONARY) return world.doesNotIntersectEntities(this);
        return super.canSpawn(world);
    }

    @Override
    public void travel(Vec3d input) {
        MobSpec.Kind k = spec().kind;
        if (scriptedMotion()) return;                    // the subclass moves itself this tick (leaps, grabs)
        if (k == MobSpec.Kind.STATIONARY) {
            super.travel(Vec3d.ZERO);
            return;
        }
        if (k == MobSpec.Kind.SWIM && this.canMoveVoluntarily() && this.isTouchingWater()) {
            this.updateVelocity(this.getMovementSpeed(), input);
            safeMove(this.getVelocity());
            this.setVelocity(this.getVelocity().multiply(0.9));
            if (spec().floats) {
                // ride the surface: ease the feet toward the waterline, whatever the swim controller wants
                double vy = Math.max(-0.15, Math.min(0.15, (waterSurfaceY() - 0.15 - this.getY()) * 0.25));
                this.setVelocity(this.getVelocity().x, vy, this.getVelocity().z);
            } else if (this.getTarget() == null) this.setVelocity(this.getVelocity().add(0.0, -0.005, 0.0));
            return;
        }
        if (spec().seabed && this.isTouchingWater() && this.canMoveVoluntarily()) {
            // walk the floor: land-style acceleration and friction (30% water drag), half gravity, no buoyancy
            this.updateVelocity(this.getMovementSpeed() * 0.216f / 0.163f * 0.7f, input);
            safeMove(this.getVelocity());
            Vec3d v = this.getVelocity();
            double vy = this.horizontalCollision && this.jumping ? 0.2 : (v.y - 0.04) * 0.9;
            this.setVelocity(v.x * 0.546, vy, v.z * 0.546);
            this.updateLimbs(false);
            return;
        }
        if (k == MobSpec.Kind.FLY && this.canMoveVoluntarily()) {
            this.updateVelocity(this.getMovementSpeed(), input);
            safeMove(this.getVelocity());
            this.setVelocity(this.getVelocity().multiply(0.91));
            return;
        }
        super.travel(input);
    }

    /** True while a subclass drives the position itself (vanilla travel() is skipped). */
    protected boolean scriptedMotion() { return false; }

    /** Y of the top of the water column this mob is in (the surface). */
    protected double waterSurfaceY() {
        BlockPos.Mutable p = this.getBlockPos().mutableCopy();
        for (int i = 0; i < 48 && this.getWorld().getFluidState(p).isIn(net.minecraft.registry.tag.FluidTags.WATER); i++) p.move(0, 1, 0);
        return p.getY();
    }

    protected void safeMove(Vec3d v) {
        try {
            this.move(MovementType.SELF, v);
        } catch (NullPointerException ignored) {
            // VS2 physics-thread race (CLAUDE.md crash cause #5)
        }
    }

    // ------------------------------------------------------------------ combat
    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) {
            MobSpec s = spec();
            if (target instanceof LivingEntity le) {
                if (s.onHitEffect != null) le.addStatusEffect(new StatusEffectInstance(s.onHitEffect, s.onHitTicks, s.onHitAmp), this);
                if (s.lifesteal > 0) this.heal((float) (s.damage * s.lifesteal));
            }
            if (s.onHitFire > 0) target.setOnFireFor(s.onHitFire);
            triggerAnim(ACTION, "attack");
            this.playSound(sound("attack"), getSoundVolume(), getSoundPitch());
        }
        return hit;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return super.damage(source, amount * spec().damageTaken * relicBonus(source));
    }

    /** Boss relics that counter this kind of mob (see BossRelicItem). */
    protected float relicBonus(DamageSource source) {
        if (!(source.getAttacker() instanceof PlayerEntity p)) return 1f;
        MobSpec s = spec();
        float mult = 1f;
        if (s.hurtByWater && BossProgression.relicActive(p, net.get900.pixelpirates.item.ModItems.SERPENTS_TIDE_PEARL)) mult *= 1.5f;
        if (s.kind == MobSpec.Kind.STATIONARY && BossProgression.relicActive(p, net.get900.pixelpirates.item.ModItems.BLOODFIN_RAZOR_TOOTH)) mult *= 1.5f;
        return mult;
    }

    @Override
    public boolean isFireImmune() { return spec().fireImmune || super.isFireImmune(); }

    @Override
    public boolean canBreatheInWater() { return spec().kind == MobSpec.Kind.SWIM || spec().kind == MobSpec.Kind.STATIONARY || spec().seabed; }

    @Override
    public boolean handleFallDamage(float dist, float mult, DamageSource src) {
        return spec().kind == MobSpec.Kind.GROUND && super.handleFallDamage(dist, mult, src);
    }

    @Override
    protected void fall(double heightDifference, boolean onGround, BlockState state, BlockPos landedPosition) {
        if (spec().kind != MobSpec.Kind.FLY) super.fall(heightDifference, onGround, state, landedPosition);
    }

    @Override
    public boolean isPushable() { return spec().kind != MobSpec.Kind.STATIONARY && super.isPushable(); }

    @Override
    public boolean isPushedByFluids() { return spec().kind == MobSpec.Kind.GROUND && !spec().seabed; }

    @Override
    protected boolean isDisallowedInPeaceful() { return spec().hostile(); }

    @Override
    public SoundCategory getSoundCategory() { return spec().hostile() ? SoundCategory.HOSTILE : SoundCategory.NEUTRAL; }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return spec().temper != MobSpec.Temper.PASSIVE || distanceSquared > 128 * 128;
    }

    // ------------------------------------------------------------------ drops & sounds
    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        for (MobSpec.Drop d : spec().drops) {
            if (this.random.nextFloat() < d.chance()) {
                this.dropStack(new ItemStack(d.item(), d.min() + (d.max() > d.min() ? this.random.nextInt(d.max() - d.min() + 1) : 0)));
            }
        }
    }

    protected SoundEvent sound(String kind) {
        return Registries.SOUND_EVENT.get(new Identifier(PixelPirates.MOD_ID, "entity." + spec().id + "." + kind));
    }

    @Override
    protected SoundEvent getAmbientSound() { return sound("ambient"); }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return sound("hurt"); }
    @Override
    protected SoundEvent getDeathSound() { return sound("death"); }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        if (spec().kind == MobSpec.Kind.GROUND) super.playStepSound(pos, state);
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5,
                st -> { RawAnimation o = movementOverride(); return st.setAndContinue(o != null ? o
                        : st.isMoving() || (spec().kind == MobSpec.Kind.SWIM && this.getVelocity().lengthSquared() > 0.002) ? MOVE : IDLE); }));
        AnimationController<ModMob> action = new AnimationController<>(this, ACTION, 1, st -> PlayState.STOP);
        Set<String> anims = new LinkedHashSet<>();
        anims.add("attack");
        for (Abilities.Ability a : spec().abilities) anims.add(a.anim());
        for (Abilities.Ability a : spec().phase2) anims.add(a.anim());
        if (spec().boss) anims.add(spec().enrageAnim);
        anims.addAll(extraAnims());
        for (String a : anims) action.triggerableAnim(a, RawAnimation.begin().thenPlay(a));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
