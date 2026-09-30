package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.item.ModItems;
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
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
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

public class ChestCrabEntity extends PathAwareEntity implements GeoEntity {

    // Ticks after the last hit before the crab reveals itself again (4 seconds)
    private static final int UNHIDE_DELAY = 80;

    private static final TrackedData<Boolean> IS_HIDING = DataTracker.registerData(
            ChestCrabEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private static final RawAnimation IDLE_ANIM  = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIM  = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation HIDE_ANIM  = RawAnimation.begin().thenPlayAndHold("hide");
    private static final RawAnimation DEATH_ANIM = RawAnimation.begin().thenPlayAndHold("death");

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private int coinDropCount = 0;
    private int unhideTicks = 0;

    public ChestCrabEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 15.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.18)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new WanderAroundFarGoal(this, 0.6, 120));
        this.goalSelector.add(2, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(3, new LookAroundGoal(this));
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(IS_HIDING, false);
    }

    public boolean isHiding() {
        return this.dataTracker.get(IS_HIDING);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean hurt = super.damage(source, amount);
        if (hurt && !this.getWorld().isClient && source.getAttacker() instanceof PlayerEntity) {
            if (!isHiding()) this.playSound(ModSounds.CHEST_CRAB_HIDE, 1.0f, 1.0f);
            this.dataTracker.set(IS_HIDING, true);
            unhideTicks = 0;
        }
        return hurt;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.getWorld().isClient && isHiding()) {
            if (++unhideTicks >= UNHIDE_DELAY) {
                this.dataTracker.set(IS_HIDING, false);
                unhideTicks = 0;
            }
            // Freeze in place while hiding
            this.getNavigation().stop();
            this.setVelocity(0, this.getVelocity().y, 0);
        }
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (!this.getWorld().isClient && isHiding() && coinDropCount < 2) {
            this.dropStack(new ItemStack(ModItems.PIRATE_COIN, 1));
            coinDropCount++;
            this.playSound(ModSounds.CHEST_CRAB_COIN, 1.0f, 0.9f + random.nextFloat() * 0.2f);
            return ActionResult.SUCCESS;
        }
        return super.interactMob(player, hand);
    }

    @Override
    protected void dropInventory() {
        super.dropInventory();
        int coins = 1 + this.random.nextInt(3);
        this.dropStack(new ItemStack(ModItems.PIRATE_COIN, coins));
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("CrabCoinDrops", coinDropCount);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        coinDropCount = nbt.getInt("CrabCoinDrops");
    }

    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.CHEST_CRAB_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.CHEST_CRAB_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return ModSounds.CHEST_CRAB_DEATH; }
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ModSounds.CHEST_CRAB_STEP, 0.25f, 1.0f);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4, this::animPredicate));
    }

    private PlayState animPredicate(AnimationState<ChestCrabEntity> state) {
        if (this.isDead()) {
            return state.setAndContinue(DEATH_ANIM);
        }
        if (isHiding()) {
            return state.setAndContinue(HIDE_ANIM);
        }
        if (state.isMoving()) {
            return state.setAndContinue(WALK_ANIM);
        }
        return state.setAndContinue(IDLE_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
