package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
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

/**
 * Phase 3 (volcanic ring) hostile crab. Immune to fire and lava, sets its victims alight,
 * and smoulders with ember particles.
 */
public class LavaCrabEntity extends HostileEntity implements GeoEntity {

    /** Controller that owns the one-shot claw strike; also the trigger key inside it. */
    public static final String ATTACK_CONTROLLER = "attack";

    // Animation names must match the keys in assets/pixelpirates/animations/lava_crab.animation.json
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation ATTACK_ANIMATION = RawAnimation.begin().thenPlay("attack");

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    public LavaCrabEntity(EntityType<? extends HostileEntity> type, World world) {
        super(type, world);
        // Lava is a road, not a hazard
        this.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.LAVA, 0.0F);
        this.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DAMAGE_FIRE, 0.0F);
        this.setPathfindingPenalty(net.minecraft.entity.ai.pathing.PathNodeType.DANGER_FIRE, 0.0F);
    }

    // ---------- attributes ----------

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 26.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.22)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.4)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    // ---------- goals ----------

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.7, 80));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 10.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));

        this.targetSelector.add(1, new RevengeGoal(this));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    // ---------- combat ----------

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit && !this.getWorld().isClient) {
            // Molten claws — a glancing hit still burns
            target.setOnFireFor(5);
            triggerAttackAnimation();
        }
        return hit;
    }

    /** Server-side only: plays the claw strike once on every tracking client. */
    public void triggerAttackAnimation() {
        triggerAnim(ATTACK_CONTROLLER, ATTACK_CONTROLLER);
    }

    @Override
    public boolean isFireImmune() { return true; }

    // ---------- tick ----------

    @Override
    public void tick() {
        super.tick();
        // Ember trail — client-side cosmetic only
        if (this.getWorld().isClient && this.random.nextInt(4) == 0) {
            this.getWorld().addParticle(ParticleTypes.SMALL_FLAME,
                    this.getParticleX(0.6), this.getRandomBodyY() - 0.1,
                    this.getParticleZ(0.6), 0.0, 0.01, 0.0);
        }
    }

    // ---------- drops ----------

    @Override
    protected void dropInventory() {
        super.dropInventory();
        int embers = this.random.nextInt(3); // 0-2
        if (embers > 0) {
            this.dropStack(new ItemStack(ModItems.VOLCANIC_EMBER, embers));
        }
        if (this.random.nextFloat() < 0.5f) this.dropStack(new ItemStack(ModItems.BRIMSTONE));
        if (this.random.nextFloat() < 0.4f) this.dropStack(new ItemStack(ModItems.RAW_LAVA_CRAB_CLAW));
        if (this.random.nextFloat() < 0.02f) this.dropStack(new ItemStack(net.get900.pixelpirates.homestead.HomesteadBlocks.LAVA_CRAB_TROPHY));
    }

    // ---------- sounds ----------

    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.LAVA_CRAB_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.LAVA_CRAB_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return ModSounds.LAVA_CRAB_DEATH; }
    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        this.playSound(ModSounds.LAVA_CRAB_STEP, 0.3f, 1.0f);
    }

    // ---------- GeckoLib ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 4, this::movementPredicate));

        // Claw strike plays over the locomotion cycle; bones it does not touch keep moving.
        controllers.add(new AnimationController<LavaCrabEntity>(this, ATTACK_CONTROLLER, 0,
                state -> PlayState.STOP)
                .triggerableAnim(ATTACK_CONTROLLER, ATTACK_ANIMATION));
    }

    private PlayState movementPredicate(AnimationState<LavaCrabEntity> state) {
        if (state.isMoving()) {
            return state.setAndContinue(WALK_ANIMATION);
        }
        return state.setAndContinue(IDLE_ANIMATION);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
