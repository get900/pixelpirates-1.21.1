package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.goal.ShipPatrolGoal;
import net.get900.pixelpirates.world.AiShipController;
import net.minecraft.block.BlockState;
import net.minecraft.entity.*;
import net.minecraft.entity.ai.goal.*;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.get900.pixelpirates.sound.ModSounds;
import net.minecraft.text.Text;
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

public class CaptainEntity extends PathAwareEntity implements GeoEntity {

    private static final RawAnimation WALK_ANIM   = RawAnimation.begin().thenLoop("animation.pirate.walk");
    private static final RawAnimation IDLE_ANIM   = RawAnimation.begin().thenLoop("animation.pirate.idle");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlayAndHold("animation.pirate.attack");

    private final AnimatableInstanceCache cache = new SingletonAnimatableInstanceCache(this);

    // The VS2 ship this captain commands; -1L = unassigned
    private long shipId = -1L;

    // Ship patrol / tether system
    private ShipPatrolGoal patrolGoal;
    private static final float TETHER_RADIUS = 10.0f;

    public CaptainEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setCustomName(Text.literal("[~] Captain"));
        this.setCustomNameVisible(true);
    }

    // ── Attributes ────────────────────────────────────────────────────────────

    public static DefaultAttributeContainer.Builder createAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 60.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 8.0)
                .add(EntityAttributes.GENERIC_ARMOR, 4.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 32.0);
    }

    // ── Goals ─────────────────────────────────────────────────────────────────

    @Override
    protected void initGoals() {
        patrolGoal = new ShipPatrolGoal(this, 0.8, 5.0f);
        this.goalSelector.add(1, new MeleeAttackGoal(this, 1.2, false));
        this.goalSelector.add(2, patrolGoal);
        this.goalSelector.add(3, new LookAtEntityGoal(this, PlayerEntity.class, 8.0f));
        this.goalSelector.add(4, new LookAroundGoal(this));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        this.targetSelector.add(2, new ActiveTargetGoal<>(this, CursedMonkeyEntity.class, true));
        this.targetSelector.add(3, new RevengeGoal(this));
    }

    /** Called from AiShipController after spawning to anchor the captain to the ship deck. */
    public void setShipHome(double x, double y, double z) {
        patrolGoal.setHome(x, y, z);
    }

    // ── Tether tick ───────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && this.age % 40 == 0 && patrolGoal != null && patrolGoal.isHomeSet()) {
            double dx = this.getX() - patrolGoal.getHomeX();
            double dz = this.getZ() - patrolGoal.getHomeZ();
            double dy = this.getY() - patrolGoal.getHomeY();
            boolean tooFarXZ = (dx * dx + dz * dz) > (TETHER_RADIUS * TETHER_RADIUS);
            boolean fell      = dy < -5.0;
            if (tooFarXZ || fell) {
                this.teleport(patrolGoal.getHomeX(), patrolGoal.getHomeY() + 0.1, patrolGoal.getHomeZ());
                this.getNavigation().stop();
            }
        }
    }

    // ── Ship binding ──────────────────────────────────────────────────────────

    public void setShipId(long id) { this.shipId = id; }

    public long getShipId() { return shipId; }

    // ── Death / conquest ──────────────────────────────────────────────────────

    @Override
    public void onDeath(DamageSource source) {
        super.onDeath(source);
        if (!this.getWorld().isClient && shipId != -1L) {
            Entity attacker = source.getAttacker();
            if (attacker instanceof ServerPlayerEntity killer) {
                AiShipController.onCaptainKilled(shipId, killer);
            }
        }
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putLong("ship_id", shipId);
        if (patrolGoal != null && patrolGoal.isHomeSet()) {
            nbt.putDouble("ShipHomeX", patrolGoal.getHomeX());
            nbt.putDouble("ShipHomeY", patrolGoal.getHomeY());
            nbt.putDouble("ShipHomeZ", patrolGoal.getHomeZ());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        shipId = nbt.getLong("ship_id");
        if (nbt.contains("ShipHomeX")) {
            patrolGoal.setHome(
                nbt.getDouble("ShipHomeX"),
                nbt.getDouble("ShipHomeY"),
                nbt.getDouble("ShipHomeZ")
            );
        }
        // Re-link captain UUID in live AI data if the ship entry is already loaded
        if (shipId != -1L && !this.getWorld().isClient) {
            AiShipController.AiShipData data = AiShipController.AI_SHIPS.get(shipId);
            if (data != null && data.captainEntityId == null) {
                data.captainEntityId = this.getUuid();
            }
        }
    }

    // ── Sounds ────────────────────────────────────────────────────────────────

    @Override
    protected SoundEvent getAmbientSound() { return ModSounds.SHIP_CAPTAIN_AMBIENT; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return ModSounds.SHIP_CAPTAIN_HURT; }

    @Override
    protected SoundEvent getDeathSound() { return ModSounds.SHIP_CAPTAIN_DEATH; }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        super.playStepSound(pos, state);   // real footsteps (this used to play a voice line every step)
    }

    // ── GeckoLib ──────────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4, this::animPredicate));
    }

    private PlayState animPredicate(AnimationState<CaptainEntity> state) {
        if (this.handSwingProgress > 0.0f) return state.setAndContinue(ATTACK_ANIM);
        if (state.isMoving()) return state.setAndContinue(WALK_ANIM);
        return state.setAndContinue(IDLE_ANIM);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
