package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.RangedAttackMob;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.ProjectileAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.get900.pixelpirates.sound.ModSounds;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;

/**
 * A marooned sailor. Neutral: sits nursing his grog until someone takes a swing at him,
 * at which point he throws whatever is to hand.
 */
public class CastawayEntity extends PathAwareEntity implements GeoEntity, RangedAttackMob {

    /** Controller holding both one-shot animations; the trigger keys match the JSON keys. */
    public static final String ACTION_CONTROLLER = "action";
    private static final String DRINK_TRIGGER = "drink";
    private static final String THROW_TRIGGER = "throw";

    /** Ticks between swigs — the drink clip runs 2.5s, so this keeps it occasional. */
    private static final int DRINK_COOLDOWN = 200;

    // Animation names must match the keys in assets/pixelpirates/animations/castaway_man.animation.json.
    // There is no idle clip: a castaway sits, so `sit` doubles as the standing-still pose.
    private static final RawAnimation SIT_ANIMATION = RawAnimation.begin().thenLoop("sit");
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation DRINK_ANIMATION = RawAnimation.begin().thenPlay("drink");
    private static final RawAnimation THROW_ANIMATION = RawAnimation.begin().thenPlay("throw");

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    private int drinkCooldown = 0;

    /**
     * Held prisoner in a structure (Rackham's Hold brig). The first player to talk to a captive
     * castaway frees him and gets a thank-you gift; he is then an ordinary castaway.
     */
    private boolean captive;

    public void setCaptive(boolean c) { this.captive = c; }

    public CastawayEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    // ---------- attributes ----------

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 24.0);
    }

    // ---------- goals ----------

    @Override
    protected void initGoals() {
        // 40-tick interval, 12-block range — deliberately slow, he is not a soldier
        this.goalSelector.add(1, new ProjectileAttackGoal(this, 1.0, 40, 12.0f));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.6));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));

        // Neutral — never targets unprovoked, only retaliates
        this.targetSelector.add(1, new RevengeGoal(this));
    }

    // ---------- spawning ----------

    /**
     * Deliberately does NOT delegate to {@code MobEntity::canMobSpawn}. That calls
     * {@code isSpawnDark()}, which the CREATURE spawn cycle can never satisfy because it only
     * ever offers lit positions — every attempt would silently fail. See CLAUDE.md, SPAWNING.
     */
    public static boolean canSpawn(EntityType<? extends CastawayEntity> type, ServerWorldAccess world,
                                   SpawnReason reason, BlockPos pos,
                                   net.minecraft.util.math.random.Random random) {
        return world.getBlockState(pos.down()).isSolidBlock(world, pos.down())
                && world.getLightLevel(pos, 0) > 8;
    }

    // ---------- rescue ----------

    @Override
    protected net.minecraft.util.ActionResult interactMob(PlayerEntity player, net.minecraft.util.Hand hand) {
        if (!captive) return super.interactMob(player, hand);
        if (!this.getWorld().isClient) {
            captive = false;
            this.setPersistent();
            net.minecraft.util.math.random.Random r = this.random;
            java.util.List<net.minecraft.item.ItemStack> gifts = new java.util.ArrayList<>();
            gifts.add(new net.minecraft.item.ItemStack(net.get900.pixelpirates.item.ModItems.PIRATE_COIN, 4 + r.nextInt(7)));
            gifts.add(r.nextBoolean()
                    ? new net.minecraft.item.ItemStack(net.get900.pixelpirates.item.ModItems.DYNAMITE, 2 + r.nextInt(3))
                    : new net.minecraft.item.ItemStack(net.get900.pixelpirates.item.ModItems.TREASURE_MAP_COMMON));
            for (net.minecraft.item.ItemStack g : gifts) player.getInventory().offerOrDrop(g);
            player.sendMessage(net.minecraft.text.Text.literal("<Castaway> Bless ye! Take this - I hid it from Rackham's lot.")
                    .formatted(net.minecraft.util.Formatting.AQUA), false);
            this.playSound(ModSounds.CASTAWAY_AMBIENT, 1.0f, 1.2f);
            ((net.minecraft.server.world.ServerWorld) this.getWorld()).spawnParticles(net.minecraft.particle.ParticleTypes.HAPPY_VILLAGER,
                    getX(), getY() + 1.2, getZ(), 10, 0.4, 0.5, 0.4, 0);
        }
        return net.minecraft.util.ActionResult.success(this.getWorld().isClient);
    }

    @Override
    public void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Captive", captive);
    }

    @Override
    public void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        captive = nbt.getBoolean("Captive");
    }

    // ---------- ranged attack ----------

    @Override
    public void attack(LivingEntity target, float pullProgress) {
        if (this.getWorld().isClient) return;

        ThrownKnifeEntity knife = new ThrownKnifeEntity(ModEntities.THROWN_KNIFE, this.getWorld(), this);
        double dx = target.getX() - this.getX();
        double dy = target.getBodyY(0.3333) - knife.getY();
        double dz = target.getZ() - this.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        // Arc the throw so it drops onto the target rather than firing flat
        knife.setVelocity(dx, dy + horizontalDistance * 0.2, dz, 1.2f, 6.0f);

        this.playSound(ModSounds.CASTAWAY_THROW, 1.0f, 0.9f + this.random.nextFloat() * 0.2f);
        this.getWorld().spawnEntity(knife);
        triggerAnim(ACTION_CONTROLLER, THROW_TRIGGER);
    }

    // ---------- tick ----------

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;

        if (drinkCooldown > 0) {
            drinkCooldown--;
        } else if (this.getTarget() == null && this.random.nextInt(300) == 0) {
            triggerAnim(ACTION_CONTROLLER, DRINK_TRIGGER);
            this.playSound(ModSounds.CASTAWAY_DRINK, 0.8f, 0.9f + this.random.nextFloat() * 0.2f);
            drinkCooldown = DRINK_COOLDOWN;
        }
    }

    // ---------- sounds ----------

    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.CASTAWAY_AMBIENT; }
    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.CASTAWAY_HURT; }
    @Override
    protected SoundEvent getDeathSound() { return ModSounds.CASTAWAY_DEATH; }

    // ---------- GeckoLib ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "movement", 5, this::movementPredicate));

        // Drink and throw ride over the locomotion cycle on their own controller
        controllers.add(new AnimationController<CastawayEntity>(this, ACTION_CONTROLLER, 0,
                state -> PlayState.STOP)
                .triggerableAnim(DRINK_TRIGGER, DRINK_ANIMATION)
                .triggerableAnim(THROW_TRIGGER, THROW_ANIMATION));
    }

    private PlayState movementPredicate(AnimationState<CastawayEntity> state) {
        if (state.isMoving()) {
            return state.setAndContinue(WALK_ANIMATION);
        }
        return state.setAndContinue(SIT_ANIMATION);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
