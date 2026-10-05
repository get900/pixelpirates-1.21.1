package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.goal.ShipPatrolGoal;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.item.ItemStack;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.get900.pixelpirates.sound.ModSounds;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class PirateCrewEntity extends PathAwareEntity implements GeoEntity {

    private static final RawAnimation WALK_ANIM   = RawAnimation.begin().thenLoop("animation.pirate.walk");
    private static final RawAnimation IDLE_ANIM   = RawAnimation.begin().thenLoop("animation.pirate.idle");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlayAndHold("animation.pirate.attack");

    private static final TrackedData<Boolean> IS_ATTACKING = DataTracker.registerData(
            PirateCrewEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private int attackAnimTicks = 0;

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    // Ship patrol / tether system
    private ShipPatrolGoal patrolGoal;
    private static final float TETHER_RADIUS = 10.0f;

    public PirateCrewEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.23)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 4.0)
                .add(EntityAttributes.GENERIC_ARMOR, 2.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void initGoals() {
        patrolGoal = new ShipPatrolGoal(this, 0.8, 5.0f);
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.add(2, patrolGoal);
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 10, true, false, this::wouldAttack));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, CursedMonkeyEntity.class, true));
        this.targetSelector.add(3, new RevengeGoal(this));
    }

    /** Which players this crew goes for: pirates, every one (the Armada's marines override it). */
    protected boolean wouldAttack(net.minecraft.entity.LivingEntity e) { return true; }

    /** What they carry (set in initialize, so it is saved with them). */
    public ItemStack weapon() { return new ItemStack(ModItems.CUTLASS); }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty,
                                 SpawnReason spawnReason, EntityData entityData, NbtCompound entityNbt) {
        EntityData data = super.initialize(world, difficulty, spawnReason, entityData, entityNbt);
        this.equipStack(EquipmentSlot.MAINHAND, weapon());
        this.setEquipmentDropChance(EquipmentSlot.MAINHAND, 0.05f);
        return data;
    }

    /** Called from AiShipController after spawning to anchor the pirate to the ship deck. */
    public void setShipHome(double x, double y, double z) {
        patrolGoal.setHome(x, y, z);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(IS_ATTACKING, false);
    }

    public boolean isAttacking() {
        return this.dataTracker.get(IS_ATTACKING);
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit && !this.getWorld().isClient) {
            this.dataTracker.set(IS_ATTACKING, true);
            attackAnimTicks = 8;
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient) {
            // Animate attack flag
            if (attackAnimTicks > 0 && --attackAnimTicks <= 0) {
                this.dataTracker.set(IS_ATTACKING, false);
            }

            // Tether: if the pirate wanders too far from their home (ship deck) teleport back
            if (this.age % 40 == 0 && patrolGoal != null && patrolGoal.isHomeSet()) {
                double dx = this.getX() - patrolGoal.getHomeX();
                double dz = this.getZ() - patrolGoal.getHomeZ();
                double dy = this.getY() - patrolGoal.getHomeY();

                boolean tooFarXZ  = (dx * dx + dz * dz) > (TETHER_RADIUS * TETHER_RADIUS);
                boolean fell      = dy < -5.0;

                if (tooFarXZ || fell) {
                    this.teleport(patrolGoal.getHomeX(), patrolGoal.getHomeY() + 0.1, patrolGoal.getHomeZ());
                    this.getNavigation().stop();
                }
            }
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (patrolGoal != null && patrolGoal.isHomeSet()) {
            nbt.putDouble("ShipHomeX", patrolGoal.getHomeX());
            nbt.putDouble("ShipHomeY", patrolGoal.getHomeY());
            nbt.putDouble("ShipHomeZ", patrolGoal.getHomeZ());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("ShipHomeX")) {
            // patrolGoal is created in initGoals() which runs before readCustomDataFromNbt
            patrolGoal.setHome(
                nbt.getDouble("ShipHomeX"),
                nbt.getDouble("ShipHomeY"),
                nbt.getDouble("ShipHomeZ")
            );
        }
    }

    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.PIRATE_CREW_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.PIRATE_CREW_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return ModSounds.PIRATE_CREW_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        super.playStepSound(pos, state);   // real footsteps (this used to play a voice line every step)
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4, this::animPredicate));
    }

    private PlayState animPredicate(AnimationState<PirateCrewEntity> state) {
        if (isAttacking()) return state.setAndContinue(ATTACK_ANIM);
        if (state.isMoving()) return state.setAndContinue(WALK_ANIM);
        return state.setAndContinue(IDLE_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
