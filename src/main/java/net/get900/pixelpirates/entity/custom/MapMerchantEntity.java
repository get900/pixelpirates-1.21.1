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

    private static final RawAnimation IDLE_ANIMATION =
            RawAnimation.begin().thenLoop("animation.map_merchant.idle");
    private static final RawAnimation WALK_ANIMATION =
            RawAnimation.begin().thenLoop("animation.map_merchant.walk");

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
        if (!player.getWorld().isClient && player instanceof ServerPlayerEntity serverPlayer) {
            this.playSound(net.get900.pixelpirates.sound.ModSounds.MAP_MERCHANT_TRADE, 1.0f, 1.0f);
            ModNetworking.sendMapMerchantMenu(serverPlayer);
            AdvancementHelper.grant(serverPlayer, "meet_the_merchant");
        }
        return ActionResult.SUCCESS;
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
        this.goalSelector.add(2, new WanderAroundFarGoal(this, 0.6));
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
    }

    // ---------- GeckoLib ----------

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4, this::animPredicate));
    }

    private PlayState animPredicate(AnimationState<MapMerchantEntity> state) {
        if (state.isMoving()) {
            return state.setAndContinue(WALK_ANIMATION);
        }
        return state.setAndContinue(IDLE_ANIMATION);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
