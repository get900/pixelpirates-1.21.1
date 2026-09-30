package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.goal.MonkeyThiefGoal;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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

public class CursedMonkeyEntity extends PathAwareEntity implements GeoEntity {

    public enum Phase { IDLE, STALKING, LEAPING, FLEEING }

    private static final TrackedData<Integer> PHASE = DataTracker.registerData(
            CursedMonkeyEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> IS_SITTING = DataTracker.registerData(
            CursedMonkeyEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // ── animations ────────────────────────────────────────────────────────────
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.model.idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.model.walking");
    private static final RawAnimation RUN_ANIM  = RawAnimation.begin().thenLoop("animation.model.run");
    private static final RawAnimation SWIM_ANIM = RawAnimation.begin().thenLoop("animation.model.swim");
    private static final RawAnimation JUMP_ANIM = RawAnimation.begin().thenPlayAndHold("animation.model.swim2");
    private static final RawAnimation SIT_ANIM  = RawAnimation.begin()
            .thenPlay("animation.model.sit").thenLoop("animation.model.sitting");

    // ── state ─────────────────────────────────────────────────────────────────
    private int idleTicks = 0;
    private ItemStack stolenItem = ItemStack.EMPTY;

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    public CursedMonkeyEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.WATER, 0.0f);
        this.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.WATER_BORDER, 0.0f);
    }

    // ── attributes ────────────────────────────────────────────────────────────

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    // ── goals ─────────────────────────────────────────────────────────────────

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MonkeyThiefGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.add(3, new WanderAroundFarGoal(this, 0.8, 120));
        this.goalSelector.add(4, new LookAtEntityGoal(this, PlayerEntity.class, 6.0f));
        this.goalSelector.add(5, new LookAroundGoal(this));
        this.targetSelector.add(1, new RevengeGoal(this)); // fight back when hit
    }

    // ── data tracker ──────────────────────────────────────────────────────────

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PHASE, Phase.IDLE.ordinal());
        this.dataTracker.startTracking(IS_SITTING, false);
    }

    public Phase getMonkeyPhase() {
        return Phase.values()[this.dataTracker.get(PHASE)];
    }

    public void setMonkeyPhase(Phase phase) {
        this.dataTracker.set(PHASE, phase.ordinal());
        if (phase != Phase.IDLE) this.dataTracker.set(IS_SITTING, false);
    }

    public boolean isSittingDown() {
        return this.dataTracker.get(IS_SITTING);
    }

    // ── tick ──────────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        // Buoyancy — prevent sinking while swimming toward ships or fleeing
        if (this.isTouchingWater()) {
            this.setAir(this.getMaxAir());
            this.addVelocity(0, 0.05, 0);
        }

        // Idle sit timer — server side only, result synced via IS_SITTING tracked data
        if (!this.getWorld().isClient) {
            if (getMonkeyPhase() == Phase.IDLE
                    && this.getVelocity().horizontalLengthSquared() < 0.003) {
                if (!isSittingDown() && ++idleTicks > 100) {
                    this.dataTracker.set(IS_SITTING, true);
                }
            } else {
                idleTicks = 0;
                if (isSittingDown()) this.dataTracker.set(IS_SITTING, false);
            }
        }
    }

    @Override
    public boolean isPushedByFluids() { return false; }

    // ── stolen item ───────────────────────────────────────────────────────────

    public void setStolenItem(ItemStack item) { this.stolenItem = item.copy(); }
    public ItemStack getStolenItem() { return stolenItem; }

    @Override
    protected void dropInventory() {
        super.dropInventory();
        if (!stolenItem.isEmpty()) {
            this.dropStack(stolenItem);
            stolenItem = ItemStack.EMPTY;
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("MonkeyPhase", getMonkeyPhase().ordinal());
        if (!stolenItem.isEmpty()) nbt.put("StolenItem", stolenItem.writeNbt(new NbtCompound()));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        int ordinal = nbt.getInt("MonkeyPhase");
        setMonkeyPhase(ordinal < Phase.values().length ? Phase.values()[ordinal] : Phase.IDLE);
        if (nbt.contains("StolenItem")) stolenItem = ItemStack.fromNbt(nbt.getCompound("StolenItem"));
    }

    // ── sounds ────────────────────────────────────────────────────────────────

    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.CURSED_MONKEY_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.CURSED_MONKEY_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return ModSounds.CURSED_MONKEY_DEATH; }
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ModSounds.CURSED_MONKEY_STEP, 0.3f, 1.0f);
    }

    // ── GeckoLib ──────────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, this::movementPredicate));
    }

    private PlayState movementPredicate(AnimationState<CursedMonkeyEntity> state) {
        Phase phase = getMonkeyPhase();

        if (phase == Phase.LEAPING) {
            return state.setAndContinue(JUMP_ANIM);
        }
        if (phase == Phase.STALKING || phase == Phase.FLEEING) {
            return state.setAndContinue(this.isTouchingWater() ? SWIM_ANIM : RUN_ANIM);
        }
        if (isSittingDown()) {
            return state.setAndContinue(SIT_ANIM);
        }
        if (state.isMoving()) {
            return state.setAndContinue(WALK_ANIM);
        }
        return state.setAndContinue(IDLE_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
