package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.util.AdvancementHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animatable.instance.SingletonAnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

public class MapMerchantEntity extends PathAwareEntity implements GeoEntity {

    // clips from tools/mobs/traders.py (map_merchant): idle, move, talk (unrolls a chart), flourish (studies it)
    private static final RawAnimation IDLE_ANIMATION = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK_ANIMATION = RawAnimation.begin().thenLoop("move");
    @org.jetbrains.annotations.Nullable private net.minecraft.util.math.BlockPos home;
    private int flourishIn = 400;
    /** Evenings in the inn's common room, nights in an inn bed (homestead/town/Lodging). */
    private final net.get900.pixelpirates.homestead.town.Lodging.Mover lodging = new net.get900.pixelpirates.homestead.town.Lodging.Mover();
    private static final RawAnimation SIT_ANIMATION = RawAnimation.begin().thenLoop("sit");

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    public MapMerchantEntity(EntityType<? extends PathAwareEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (this.isSleeping()) {
            if (!player.getWorld().isClient) player.sendMessage(net.minecraft.text.Text.literal("The Map Merchant is asleep. The market opens at dawn."), true);
            return ActionResult.SUCCESS;
        }
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            this.playSound(net.get900.pixelpirates.sound.ModSounds.MAP_MERCHANT_TRADE, 1.0f, 1.0f);
            this.triggerAnim("action", "talk");
            ModNetworking.sendMapMerchantMenu(serverPlayer);
            AdvancementHelper.grant(serverPlayer, "meet_the_merchant");
        }
        return ActionResult.SUCCESS;
    }

    public void setHome(net.minecraft.util.math.BlockPos p) { this.home = p.toImmutable(); this.setPersistent(); }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (lodging.tick(this, "map_merchant")) return;                                    // off duty: the inn
        if (home != null && this.age % 20 == 0 && this.getBlockPos().getSquaredDistance(home) > 4)
            this.getNavigation().startMovingTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, 0.5);
        if (--flourishIn <= 0) {
            flourishIn = 400 + this.random.nextInt(400);
            this.triggerAnim("action", "flourish");
        }
    }

    @Override
    public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
        if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        return false;                                                    // the port's merchants are not for stabbing
    }

    @Override
    public void writeCustomDataToNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (home != null) nbt.put("Home", net.minecraft.nbt.NbtHelper.fromBlockPos(home));
    }

    @Override
    public void readCustomDataFromNbt(net.minecraft.nbt.NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Home")) home = net.minecraft.nbt.NbtHelper.toBlockPos(nbt.getCompound("Home"));
    }

    @Override
    protected net.minecraft.sound.SoundEvent getAmbientSound() { return net.get900.pixelpirates.sound.ModSounds.MAP_MERCHANT_AMBIENT; }
    @Override
    protected net.minecraft.sound.SoundEvent getHurtSound(net.minecraft.entity.damage.DamageSource source) { return net.get900.pixelpirates.sound.ModSounds.MAP_MERCHANT_HURT; }
    @Override
    protected net.minecraft.sound.SoundEvent getDeathSound() { return net.get900.pixelpirates.sound.ModSounds.MAP_MERCHANT_DEATH; }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.5, 0.0005f));   // a homebody: he minds his booth (mobTick)
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
    }

    // ---------- GeckoLib ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4, this::animPredicate));
        controllers.add(new AnimationController<>(this, "action", 3, st -> PlayState.STOP)
                .triggerableAnim("talk", RawAnimation.begin().thenPlay("talk"))
                .triggerableAnim("flourish", RawAnimation.begin().thenPlay("flourish")));
    }

    private PlayState animPredicate(AnimationState<MapMerchantEntity> state) {
        if (this.hasVehicle()) return state.setAndContinue(SIT_ANIMATION);
        if (state.isMoving()) {
            return state.setAndContinue(WALK_ANIMATION);
        }
        return state.setAndContinue(IDLE_ANIMATION);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
