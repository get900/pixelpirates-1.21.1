package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * One piece of the Leviathan's body (boss 10/10): 12 body segments and the tail follow the head along the path it swam
 * (LeviathanEntity keeps the path and places every piece each tick). A segment is its own hitbox - cannons, harpoons,
 * swords - but has no health of its own: every blow goes to the head (LeviathanEntity#onSegmentHit), which applies the
 * crust (phase 1) and the split spine (phase 3). Never saved: the head rebuilds its body whenever it loads.
 * Tracked: INDEX (0..12, 12 = the tail), FORM (the phase's model 1..3), CRUSTED (phase 1 plates still on), SIZE (taper).
 */
public class LeviathanSegmentEntity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<Integer> INDEX = DataTracker.registerData(LeviathanSegmentEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> FORM = DataTracker.registerData(LeviathanSegmentEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> CRUSTED = DataTracker.registerData(LeviathanSegmentEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> SIZE = DataTracker.registerData(LeviathanSegmentEntity.class, TrackedDataHandlerRegistry.FLOAT);
    public static final int TAIL = 12;
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID owner;
    private int orphan;

    public LeviathanSegmentEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
        this.setInvulnerable(false);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(INDEX, 0);
        this.dataTracker.startTracking(FORM, 1);
        this.dataTracker.startTracking(CRUSTED, false);
        this.dataTracker.startTracking(SIZE, 1f);
    }

    public void setup(UUID owner, int index, int form, boolean crusted) {
        this.owner = owner;
        this.dataTracker.set(INDEX, index);
        this.dataTracker.set(FORM, form);
        this.dataTracker.set(CRUSTED, crusted);
        this.dataTracker.set(SIZE, sizeOf(index));
        calculateDimensions();
    }

    /** The taper: full size to segment 6, then 5% smaller each; the tail 0.7. (LeviathanEntity spaces them by it.) */
    public static float sizeOf(int index) { return index == TAIL ? 0.7f : 1f - Math.max(0, index - 6) * 0.05f; }

    public int index() { return this.dataTracker.get(INDEX); }
    public int form() { return this.dataTracker.get(FORM); }
    public boolean crusted() { return this.dataTracker.get(CRUSTED); }
    public boolean isTail() { return index() == TAIL; }
    public float size() { return this.dataTracker.get(SIZE); }
    public UUID owner() { return owner; }

    public void setForm(int f) { this.dataTracker.set(FORM, f); }
    public void setCrusted(boolean c) { this.dataTracker.set(CRUSTED, c); }

    @Override
    public void onTrackedDataSet(TrackedData<?> data) {
        super.onTrackedDataSet(data);
        if (SIZE.equals(data)) calculateDimensions();
    }

    @Override
    public EntityDimensions getDimensions(EntityPose pose) {
        float s = this.dataTracker == null ? 1f : size();
        return EntityDimensions.fixed(4.6f * s, 3.8f * s);
    }

    /** Placed by the head every tick. */
    public void place(Vec3d pos, float yaw, float pitch) {
        this.setPosition(pos);
        this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
        this.setPitch(pitch);
        this.setVelocity(Vec3d.ZERO);
        orphan = 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld() instanceof ServerWorld && ++orphan > 60) this.discard();        // the head is gone (or unloaded)
    }

    @Override
    public void travel(Vec3d input) { }                                                     // the head moves it

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY) && source.getAttacker() == null) { this.discard(); return true; }
        if (!(this.getWorld() instanceof ServerWorld sw) || owner == null) return false;
        if (source.isIn(DamageTypeTags.IS_DROWNING) || source.isIn(DamageTypeTags.IS_FALL) || source.isOf(net.minecraft.entity.damage.DamageTypes.IN_WALL)) return false;
        if (sw.getEntity(owner) instanceof LeviathanEntity head) {
            boolean hit = head.onSegmentHit(this, source, amount);
            if (hit) { this.hurtTime = this.maxHurtTime = 10; this.timeUntilRegen = 0; }
            return hit;
        }
        return false;
    }

    @Override public boolean isPushable() { return false; }
    @Override public boolean isPushedByFluids() { return false; }
    @Override public boolean isCollidable() { return false; }
    @Override public boolean canBreatheInWater() { return true; }
    @Override public boolean isFireImmune() { return true; }
    @Override public boolean shouldSave() { return false; }
    @Override public boolean cannotDespawn() { return true; }
    @Override protected boolean isDisallowedInPeaceful() { return false; }
    @Override public boolean canImmediatelyDespawn(double d) { return false; }

    /** Huge: never culled while any of it could be on screen. */
    @Override
    public Box getVisibilityBoundingBox() { return getBoundingBox().expand(8); }

    @Override
    public boolean shouldRender(double distance) { return distance < 320 * 320; }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) { }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) { }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, st -> st.setAndContinue(SWIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
