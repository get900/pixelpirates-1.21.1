package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.get900.pixelpirates.sound.ModSounds;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Phase 4 ambusher. Sits motionless as an ordinary chest (grid-aligned, silent, no glowing
 * eyes - the model swaps to a plain texture) until a player gets within 2.5 blocks, opens it,
 * or hits it. Then the lid gapes, it hops after its prey and bites. Crouching players can
 * sneak past. It goes back to sleep after losing its target for 10 seconds.
 */
public class MimicEntity extends HostileEntity implements GeoEntity {
    public static final String ACTION = "action";
    private static final RawAnimation DORMANT = RawAnimation.begin().thenLoop("dormant");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation AWAKEN = RawAnimation.begin().thenPlay("awaken");
    private static final RawAnimation BITE = RawAnimation.begin().thenPlay("bite");

    private static final TrackedData<Boolean> DORMANT_FLAG = DataTracker.registerData(MimicEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);
    private int noTargetTicks;

    public MimicEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 12;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_ARMOR, 6.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 16.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(DORMANT_FLAG, true);
    }

    public boolean isDormant() { return this.dataTracker.get(DORMANT_FLAG); }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.3, false) {
            @Override public boolean canStart() { return !isDormant() && super.canStart(); }
            @Override public boolean shouldContinue() { return !isDormant() && super.shouldContinue(); }
        });
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8, 60) {
            @Override public boolean canStart() { return !isDormant() && super.canStart(); }
        });
        this.goalSelector.add(6, new LookAroundGoal(this) {
            @Override public boolean canStart() { return !isDormant() && super.canStart(); }
        });
        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true) {
            @Override public boolean canStart() { return !isDormant() && super.canStart(); }
        });
    }

    // ------------------------------------------------------------------ disguise
    private void awaken(LivingEntity prey) {
        if (!isDormant()) return;
        this.dataTracker.set(DORMANT_FLAG, false);
        triggerAnim(ACTION, "awaken");
        this.playSound(ModSounds.MIMIC_AWAKEN, 1.2f, 1.0f);
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.8, this.getZ(), 10, 0.4, 0.3, 0.4, 0.02);
        }
        if (prey != null) this.setTarget(prey);
        noTargetTicks = 0;
    }

    private void fallAsleep() {
        this.dataTracker.set(DORMANT_FLAG, true);
        this.getNavigation().stop();
        this.setTarget(null);
        this.playSound(SoundEvents.BLOCK_CHEST_CLOSE, 1.0f, 0.8f);
        snapToGrid();
    }

    private void snapToGrid() {
        float yaw = Math.round(this.getYaw() / 90f) * 90f;
        this.setYaw(yaw); this.setHeadYaw(yaw); this.setBodyYaw(yaw);
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (isDormant()) {
            this.getNavigation().stop();
            snapToGrid();
            if (this.age % 5 == 0) {
                PlayerEntity p = this.getWorld().getClosestPlayer(this, 2.5);
                if (p != null && !p.isCreative() && !p.isSpectator() && !p.isSneaking()) awaken(p);
            }
        } else {
            noTargetTicks = this.getTarget() == null ? noTargetTicks + 1 : 0;
            if (noTargetTicks > 200) fallAsleep();
        }
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (isDormant()) {
            if (!this.getWorld().isClient) {
                awaken(player);
                this.tryAttack(player);               // that's no treasure chest...
            }
            return ActionResult.success(this.getWorld().isClient);
        }
        return super.interactMob(player, hand);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean hurt = super.damage(source, amount);
        if (hurt && isDormant() && !this.getWorld().isClient) {
            awaken(source.getAttacker() instanceof LivingEntity le ? le : null);
        }
        return hurt;
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit) {
            triggerAnim(ACTION, "bite");
            this.playSound(ModSounds.MIMIC_BITE, 1.0f, 0.9f + this.random.nextFloat() * 0.2f);
            if (target instanceof LivingEntity le && this.random.nextFloat() < 0.35f) {
                le.addStatusEffect(new StatusEffectInstance(StatusEffects.HUNGER, 120, 1), this);
            }
        }
        return hit;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient && !isDormant() && this.random.nextInt(10) == 0) {
            float yaw = this.bodyYaw * MathHelper.RADIANS_PER_DEGREE;
            this.getWorld().addParticle(ParticleTypes.SMOKE, this.getX() - MathHelper.sin(yaw) * 0.5, this.getY() + 0.7,
                    this.getZ() + MathHelper.cos(yaw) * 0.5, 0, 0.02, 0);
        }
    }

    @Override
    public boolean isPushable() { return !isDormant(); }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) { return !isDormant() && super.canImmediatelyDespawn(distanceSquared); }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Dormant", isDormant());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Dormant")) this.dataTracker.set(DORMANT_FLAG, nbt.getBoolean("Dormant"));
    }

    // ------------------------------------------------------------------ loot & sounds
    @Override
    protected void dropLoot(DamageSource source, boolean causedByPlayer) {
        super.dropLoot(source, causedByPlayer);
        this.dropStack(new ItemStack(ModItems.PIRATE_COIN, 2 + this.random.nextInt(4)));
        this.dropStack(new ItemStack(Items.GOLD_NUGGET, 1 + this.random.nextInt(4)));
        if (this.random.nextFloat() < 0.25f) this.dropStack(new ItemStack(ModItems.CURSED_BONE));
        if (causedByPlayer && this.random.nextFloat() < 0.1f) this.dropStack(new ItemStack(ModItems.TREASURE_MAP_COMMON));
    }

    @Override
    protected SoundEvent getAmbientSound() { return isDormant() ? null : ModSounds.MIMIC_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.MIMIC_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return ModSounds.MIMIC_DEATH; }
    @Override
    public float getSoundPitch() { return 0.9f + this.random.nextFloat() * 0.2f; }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 3, this::movement));
        controllers.add(new AnimationController<MimicEntity>(this, ACTION, 0, s -> PlayState.STOP)
                .triggerableAnim("awaken", AWAKEN)
                .triggerableAnim("bite", BITE));
    }

    private PlayState movement(AnimationState<MimicEntity> state) {
        if (isDormant()) return state.setAndContinue(DORMANT);
        return state.setAndContinue(state.isMoving() ? WALK : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
