package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * THE WAKE: what you can see of the Leviathan while it swims between its lairs - a fin the size of a ship and a ridge of
 * red lights breaking the surface, a wake of foam behind. Only a visual (it can't be hit): LeviathanHunt keeps its
 * position on the flight path and only keeps one while someone is close enough to see it. Carries the eclipse with it.
 */
public class LeviathanWakeEntity extends Entity implements GeoEntity {
    private static final TrackedData<Boolean> ECLIPSE = DataTracker.registerData(LeviathanWakeEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final Vector3f RED = new Vector3f(0.9f, 0.06f, 0.08f);
    private static final RawAnimation SWIM = RawAnimation.begin().thenLoop("swim");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int orphan;

    public LeviathanWakeEntity(EntityType<? extends LeviathanWakeEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker() { this.dataTracker.startTracking(ECLIPSE, true); }

    public boolean eclipse() { return this.dataTracker.get(ECLIPSE); }

    public void moveTo(Vec3d pos, Vec3d dir) {
        Vec3d prev = getPos();
        setPosition(pos);
        setYaw((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
        orphan = 0;
        if (getWorld() instanceof ServerWorld sw && age % 2 == 0) {
            Vec3d back = pos.subtract(dir.multiply(6));
            sw.spawnParticles(ParticleTypes.SPLASH, pos.x, pos.y + 1, pos.z, 30, 3, 0.5, 3, 0.3);
            sw.spawnParticles(ParticleTypes.CLOUD, back.x, back.y + 0.5, back.z, 6, 4, 0.2, 4, 0.02);
            sw.spawnParticles(new DustParticleEffect(RED, 2.5f), pos.x, pos.y + 2, pos.z, 12, 3, 1.5, 3, 0);
            if (prev.squaredDistanceTo(pos) > 400) sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, pos.x, pos.y - 2, pos.z, 60, 4, 3, 4, 0.2);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld && ++orphan > 40) discard();
    }

    @Override public boolean canHit() { return false; }
    @Override public boolean isCollidable() { return false; }
    @Override public boolean shouldSave() { return false; }
    @Override public Box getVisibilityBoundingBox() { return getBoundingBox().expand(24); }
    @Override public boolean shouldRender(double distance) { return distance < 320 * 320; }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) { }
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) { }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 4, st -> st.setAndContinue(SWIM)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
