package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.goal.OrbitRaftGoal;
import net.get900.pixelpirates.entity.goal.SharkAttackGoal;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.SwimAroundGoal;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.ai.pathing.SwimNavigation;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.get900.pixelpirates.sound.ModSounds;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class SharkEntity extends PathAwareEntity implements GeoEntity {

    /** Controller that owns the one-shot bite; also the trigger key inside it. */
    public static final String ATTACK_CONTROLLER = "attack";

    // Animation names must match the keys in assets/pixelpirates/animations/shark.animation.json
    private static final RawAnimation SWIM_ANIMATION = RawAnimation.begin().thenLoop("swim");
    private static final RawAnimation ATTACK_ANIMATION = RawAnimation.begin().thenPlay("attack");

    /** How far ahead (blocks) a move is probed for water before it is allowed. */
    private static final double WATER_PROBE_DISTANCE = 1.25;
    /** Grace period out of water before a stranded shark starts drying out — long enough to breach. */
    private static final int LAND_GRACE_TICKS = 100;

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private final double orbitPhaseOffset;

    private int ticksOutOfWater = 0;

    public SharkEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
        this.orbitPhaseOffset = this.random.nextDouble() * Math.PI * 2;
        this.moveControl = new SharkMoveControl(this);
        this.setPathfindingPenalty(PathNodeType.WATER, 0.0F);
        this.setPathfindingPenalty(PathNodeType.WATER_BORDER, 0.0F);
    }

    public double getOrbitPhaseOffset() { return orbitPhaseOffset; }

    // ---------- attributes ----------

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 30.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.18)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 6.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 80.0);
    }

    // ---------- goals ----------

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SharkAttackGoal(this));
        this.goalSelector.add(2, new OrbitRaftGoal(this, 7.0, 0.10f));
        this.goalSelector.add(3, new SwimAroundGoal(this, 0.8, 10));
        this.targetSelector.add(1, new ActiveTargetGoal<>(
                this, PlayerEntity.class, 10, true, false,
                player -> player.isTouchingWater() || player.isSubmergedIn(FluidTags.WATER)
        ));
    }

    // ---------- navigation ----------

    @Override
    protected EntityNavigation createNavigation(World world) {
        return new SwimNavigation(this, world);
    }

    // ---------- water confinement ----------

    /*
     * A shark must never end up on land. Three of its movement paths (SharkAttackGoal,
     * OrbitRaftGoal, and the breach below) drive it by hand with setVelocity + move, bypassing
     * the pathfinder entirely — so the guard has to live here, at the one method they all call.
     */

    /**
     * True if this position can be swum through: water, or air directly above water so a shark
     * riding the surface is not treated as beached.
     */
    private boolean isSwimmable(double x, double y, double z) {
        BlockPos pos = BlockPos.ofFloored(x, y, z);
        return this.getWorld().getFluidState(pos).isIn(FluidTags.WATER)
                || this.getWorld().getFluidState(pos.down()).isIn(FluidTags.WATER);
    }

    /**
     * Zeroes any horizontal component that would carry the shark into a non-water block.
     * Clamped per-axis so it slides along a shoreline instead of stalling against it.
     */
    public Vec3d clampToWater(Vec3d velocity) {
        double x = velocity.x;
        double z = velocity.z;
        if (x != 0 && !isSwimmable(this.getX() + Math.signum(x) * WATER_PROBE_DISTANCE, this.getY(), this.getZ())) {
            x = 0;
        }
        if (z != 0 && !isSwimmable(this.getX(), this.getY(), this.getZ() + Math.signum(z) * WATER_PROBE_DISTANCE)) {
            z = 0;
        }
        return new Vec3d(x, velocity.y, z);
    }

    /**
     * The only way a goal may propel this shark. Refuses to self-propel out of water (so a
     * breaching shark arcs ballistically instead of swimming through air) and never steps into
     * a dry block.
     */
    public void swim(Vec3d desiredVelocity) {
        if (!this.isTouchingWater()) return;
        // Velocity only: travel() below performs the single move per tick (moving here as well
        // made the goal and the physics fight each other).
        this.setVelocity(clampToWater(desiredVelocity));
    }

    /**
     * Water physics. Vanilla LivingEntity.travel gives a non-fish mob a fixed 0.02 acceleration
     * against 0.8 drag in water (~0.3 blocks/s) - the shark just drifted. Swim like a dolphin:
     * accelerate along the move control's 3D input, light drag, no gravity while in water.
     */
    @Override
    public void travel(Vec3d movementInput) {
        if (this.canMoveVoluntarily() && this.isTouchingWater()) {
            // The move control already scales the input by speed; normalise it so thrust is
            // speed x 0.15 (linear), not speed^2 - with a 0.18 speed attribute that squared to a crawl.
            Vec3d dir = movementInput.lengthSquared() > 1.0E-6 ? movementInput.normalize() : Vec3d.ZERO;
            this.updateVelocity(this.getMovementSpeed() * 0.15f, dir);
            this.setVelocity(clampToWater(this.getVelocity()));
            try {
                this.move(MovementType.SELF, this.getVelocity());
            } catch (NullPointerException ignored) {
                // VS2 physics thread races the server thread near ships - see CLAUDE.md crash cause #5
            }
            this.setVelocity(this.getVelocity().multiply(0.9));
        } else {
            super.travel(movementInput);
        }
    }

    /** Horizontal direction to the closest water, or null if there is none within range. */
    @Nullable
    private Vec3d directionToWater() {
        BlockPos origin = this.getBlockPos();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.iterateOutwards(origin, 8, 4, 8)) {
            if (!this.getWorld().getFluidState(candidate).isIn(FluidTags.WATER)) continue;
            double dist = candidate.getSquaredDistance(origin);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate.toImmutable();
            }
        }
        if (best == null) return null;
        Vec3d delta = Vec3d.ofCenter(best).subtract(this.getPos());
        return new Vec3d(delta.x, 0, delta.z).normalize();
    }

    // ---------- tick ----------

    @Override
    public void tick() {
        super.tick();
        if (this.isInsideWaterOrBubbleColumn()) {
            this.setAir(this.getMaxAir());
        }

        if (!this.getWorld().isClient) {
            enforceWaterConfinement();

            // Breach — only from fully submerged water, so it can never launch off a shallow shore
            if (this.isSubmergedInWater() && this.getTarget() == null && this.random.nextInt(800) == 0) {
                this.addVelocity(0, 0.8 + this.random.nextDouble() * 0.5, 0);
                this.velocityDirty = true;
                this.playSound(ModSounds.SHARK_BREACH, 1.0F, 0.9F + random.nextFloat() * 0.2f);
            }
        }
    }

    /** Last line of defence: flop a stranded shark back toward water, and dry it out if it stays. */
    private void enforceWaterConfinement() {
        if (this.isTouchingWater()) {
            ticksOutOfWater = 0;
            return;
        }

        ticksOutOfWater++;

        // Airborne mid-breach — leave it to gravity
        if (!this.isOnGround()) return;

        this.getNavigation().stop();
        this.setVelocity(this.getVelocity().multiply(0.6, 1.0, 0.6));

        // Flop toward the sea rather than wandering inland
        if (this.age % 10 == 0) {
            Vec3d toWater = directionToWater();
            if (toWater != null) {
                this.addVelocity(toWater.x * 0.08, 0.16, toWater.z * 0.08);
                this.velocityDirty = true;
            }
        }

        if (ticksOutOfWater > LAND_GRACE_TICKS && this.age % 20 == 0) {
            this.damage(this.getDamageSources().dryOut(), 2.0F);
        }
    }

    @Override
    public boolean isPushedByFluids() { return false; }

    // ---------- spawning ----------

    /** MobEntity's default refuses any box containing fluid, so natural spawns of a swimmer always failed (like WaterCreatureEntity). */
    @Override
    public boolean canSpawn(net.minecraft.world.WorldView world) { return world.doesNotIntersectEntities(this); }

    public static boolean canSpawn(EntityType<? extends SharkEntity> type, ServerWorldAccess world,
                                   SpawnReason reason, BlockPos pos, net.minecraft.util.math.random.Random random) {
        // Y-level removed: custom terrain can have shallow oceans where seaLevel-2 never passes
        return world.getFluidState(pos).isIn(FluidTags.WATER)
                && world.getFluidState(pos.up()).isIn(FluidTags.WATER);
    }

    // ---------- sounds ----------

    @Override
    protected SoundEvent getSwimSound() { return SoundEvents.ENTITY_DOLPHIN_SWIM; }
    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.SHARK_AMBIENT; }
    @Override
    public int getMinAmbientSoundDelay() { return 240; }   // sharks are mostly silent
    @Override
    protected SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) { return ModSounds.SHARK_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return ModSounds.SHARK_DEATH; }

    @Override
    public boolean tryAttack(net.minecraft.entity.Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) this.playSound(ModSounds.SHARK_BITE, 1.0f, 0.9f + this.random.nextFloat() * 0.2f);
        return hit;
    }
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {}

    // ---------- GeckoLib ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // A shark is never still — the swim cycle doubles as the idle pose.
        controllers.add(new AnimationController<>(this, "movement", 5,
                state -> state.setAndContinue(SWIM_ANIMATION)));

        // Bite plays over the swim cycle; the bones it does not touch keep swimming.
        controllers.add(new AnimationController<SharkEntity>(this, ATTACK_CONTROLLER, 0,
                state -> PlayState.STOP)
                .triggerableAnim(ATTACK_CONTROLLER, ATTACK_ANIMATION));
    }

    /** Server-side only: plays the bite once on every tracking client. */
    public void triggerAttackAnimation() {
        triggerAnim(ATTACK_CONTROLLER, ATTACK_CONTROLLER);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    // ---------- aquatic move control ----------

    private static class SharkMoveControl extends MoveControl {
        private final SharkEntity shark;

        SharkMoveControl(SharkEntity shark) {
            super(shark);
            this.shark = shark;
        }

        @Override
        public void tick() {
            if (!shark.isInsideWaterOrBubbleColumn()) {
                super.tick();
                return;
            }

            if (state == State.MOVE_TO && !shark.getNavigation().isIdle()) {
                double dx = targetX - shark.getX();
                double dy = targetY - shark.getY();
                double dz = targetZ - shark.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist < 1E-5) { dist = 1E-5; }

                float targetYaw   = (float)(Math.toDegrees(Math.atan2(dz, dx))) - 90.0f;
                float targetPitch = (float)(-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx*dx + dz*dz))));

                shark.setYaw(smoothAngle(shark.getYaw(), targetYaw, 8.0f));
                shark.setBodyYaw(shark.getYaw());
                shark.setPitch(smoothAngle(shark.getPitch(), targetPitch, 5.0f));

                float spd = (float)(this.speed * shark.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED));
                shark.setMovementSpeed(spd);
                // Split the thrust along the heading so the shark actually climbs/dives to its target
                float pitchRad = shark.getPitch() * MathHelper.RADIANS_PER_DEGREE;
                shark.forwardSpeed = MathHelper.cos(pitchRad) * spd;
                shark.upwardSpeed = -MathHelper.sin(pitchRad) * spd;
            } else {
                shark.setMovementSpeed(0.0f);
                shark.forwardSpeed = 0.0f;
                shark.upwardSpeed = 0.0f;
                shark.setVelocity(shark.getVelocity().multiply(0.9, 0.98, 0.9));
            }
        }

        private static float smoothAngle(float current, float target, float maxStep) {
            float delta = MathHelper.wrapDegrees(target - current);
            delta = MathHelper.clamp(delta, -maxStep, maxStep);
            return current + delta;
        }
    }
}
