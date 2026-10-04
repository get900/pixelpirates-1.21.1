package net.get900.pixelpirates.homestead.town;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * THE HARBOUR GULLS (2026-10-04, the user: "should we add seagulls to make this real" - for Agnes who feeds them). They wheel
 * over the harbour and the plaza, glide, drop onto the cobbles and roofs to strut and peck, and take off again when someone
 * comes too close. When Agnes Crumb throws her crumbs (TownLife.feedGulls) every gull nearby comes down round her feet.
 * Kept at a handful by TownLife (no natural spawning). Model: tools/mobs/gull.py.
 */
public class SeagullEntity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<Boolean> FLYING = DataTracker.registerData(SeagullEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable private Vec3d target, home;
    private int groundTime, retarget;
    private boolean landing;

    public SeagullEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return MobEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 6).add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(FLYING, true);
    }

    public boolean flying() { return this.dataTracker.get(FLYING); }

    void setHome(Vec3d h) { this.home = h; }

    /** Come down near here and eat (Agnes's crumbs). */
    void feed(Vec3d at) {
        double a = this.random.nextDouble() * Math.PI * 2, r = 1.5 + this.random.nextDouble() * 2.5;
        target = new Vec3d(at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r);
        landing = true;
        groundTime = 300 + this.random.nextInt(200);
        this.dataTracker.set(FLYING, true);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) return;
        if (home == null) home = this.getPos();
        if (flying()) fly();
        else walk();
    }

    private void fly() {
        this.setNoGravity(true);
        if (target == null || --retarget <= 0 || (!landing && this.getPos().squaredDistanceTo(target) < 4)) {
            retarget = 100 + this.random.nextInt(100);
            if (!landing && this.random.nextInt(5) == 0) {                         // time to come down somewhere
                int x = (int) (home.x + this.random.nextInt(40) - 20), z = (int) (home.z + this.random.nextInt(40) - 20);
                if (this.getWorld().isChunkLoaded(new BlockPos(x, 64, z))) {
                    target = new Vec3d(x + 0.5, this.getWorld().getTopY(Heightmap.Type.MOTION_BLOCKING, x, z), z + 0.5);
                    landing = true;
                    groundTime = 200 + this.random.nextInt(400);
                }
            } else if (!landing) {
                double a = this.random.nextDouble() * Math.PI * 2, r = 8 + this.random.nextDouble() * 28;
                target = new Vec3d(home.x + Math.cos(a) * r, Math.max(home.y, 78) + 6 + this.random.nextInt(18), home.z + Math.sin(a) * r);
            }
        }
        Vec3d to = target.subtract(this.getPos());
        double d = to.length();
        if (landing && d < 0.7) { this.dataTracker.set(FLYING, false); landing = false; this.setNoGravity(false); this.setVelocity(Vec3d.ZERO); return; }
        double speed = landing ? Math.min(0.32, 0.08 + d * 0.05) : 0.36;
        Vec3d v = this.getVelocity().multiply(0.85).add(to.normalize().multiply(speed * 0.15));
        this.setVelocity(v);
        try { this.move(MovementType.SELF, v); } catch (NullPointerException ignored) { }       // VS2 race (CLAUDE.md crash #5)
        if (v.horizontalLengthSquared() > 1e-4) {
            float yaw = (float) (MathHelper.atan2(v.z, v.x) * 180 / Math.PI) - 90f;
            this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
        }
        if (this.horizontalCollision) target = this.getPos().add(0, 3, 0);
    }

    private void walk() {
        this.setNoGravity(false);
        PlayerEntity p = this.getWorld().getClosestPlayer(this, 3.5);
        if (--groundTime <= 0 || (p != null && !p.isSneaking()) || this.isTouchingWater()) {       // off again (sneak up on them to get close)
            this.dataTracker.set(FLYING, true);
            this.setVelocity(0, 0.4, 0);
            target = null;
            this.playSound(SoundEvents.ENTITY_PARROT_FLY, 0.5f, 1.3f);
            return;
        }
        if (this.age % 40 == 0 && this.random.nextInt(3) == 0) {
            if (this.random.nextBoolean()) this.triggerAnim("action", "peck");
            else this.getNavigation().startMovingTo(getX() + this.random.nextInt(5) - 2, getY(), getZ() + this.random.nextInt(5) - 2, 0.8);
        }
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, net.minecraft.entity.damage.DamageSource source) { return false; }

    @Override
    protected void initGoals() { }

    @Override
    public boolean canImmediatelyDespawn(double d) { return false; }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() { return this.random.nextInt(3) == 0 ? SoundEvents.ENTITY_PARROT_AMBIENT : null; }

    @Override
    public float getSoundPitch() { return 1.5f + this.random.nextFloat() * 0.3f; }

    @Override
    public int getMinAmbientSoundDelay() { return 300; }

    @Override
    protected void dropLoot(net.minecraft.entity.damage.DamageSource source, boolean causedByPlayer) { this.dropItem(Items.FEATHER); }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (home != null) { nbt.putDouble("HomeX", home.x); nbt.putDouble("HomeY", home.y); nbt.putDouble("HomeZ", home.z); }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("HomeX")) home = new Vec3d(nbt.getDouble("HomeX"), nbt.getDouble("HomeY"), nbt.getDouble("HomeZ"));
    }

    // ------------------------------------------------------------------ GeckoLib
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle"), WALK = RawAnimation.begin().thenLoop("walk"),
            FLY = RawAnimation.begin().thenLoop("fly"), GLIDE = RawAnimation.begin().thenLoop("glide");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar c) {
        c.add(new AnimationController<>(this, "main", 4, st -> {
            if (flying()) return st.setAndContinue(this.getVelocity().y > 0.02 || (this.age / 60) % 3 == 0 ? FLY : GLIDE);
            return st.setAndContinue(st.isMoving() ? WALK : IDLE);
        }));
        c.add(new AnimationController<>(this, "action", 2, st -> PlayState.STOP).triggerableAnim("peck", RawAnimation.begin().thenPlay("peck")));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
