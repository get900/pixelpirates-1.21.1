package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.mob.LeviathanEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.UUID;

/**
 * THE BANE's bolt - the ancients' giant harpoon, fired from the Spire's ballista at the rearing Leviathan (phase 3). It
 * flies straight at its exposed core (it cannot miss a reared Leviathan - the Bane only fires then) and PINS it to the
 * Spire (LeviathanEntity#baneStrike). The harpoon model at 5x (HarpoonGeoRenderer).
 */
public class BaneBoltEntity extends ProjectileEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private UUID target;
    private int life = 100;

    public BaneBoltEntity(EntityType<? extends ProjectileEntity> type, World world) {
        super(type, world);
        setNoGravity(true);
    }

    public static BaneBoltEntity fire(World world, Vec3d from, LeviathanEntity at) {
        BaneBoltEntity b = new BaneBoltEntity(ModEntities.BANE_BOLT, world);
        b.setPosition(from);
        b.target = at.getUuid();
        b.aim(at.coreTarget());
        return b;
    }

    private void aim(Vec3d to) {
        Vec3d d = to.subtract(getPos()).normalize().multiply(2.4);
        setVelocity(d);
        double h = Math.sqrt(d.x * d.x + d.z * d.z);
        setYaw((float) Math.toDegrees(Math.atan2(d.x, d.z)));
        setPitch((float) Math.toDegrees(Math.atan2(d.y, h)));
    }

    @Override
    protected void initDataTracker() { }

    @Override
    public void tick() {
        super.tick();
        prevYaw = getYaw(); prevPitch = getPitch();
        if (getWorld() instanceof ServerWorld sw) {
            if (--life <= 0 || target == null) { discard(); return; }
            if (!(sw.getEntity(target) instanceof LeviathanEntity lev) || !lev.isAlive()) { discard(); return; }
            Vec3d core = lev.coreTarget();
            aim(core);
            sw.spawnParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 4, 0.2, 0.2, 0.2, 0.1);
            sw.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            if (getPos().distanceTo(core) < 3.0) { lev.baneStrike(); discard(); return; }
        }
        setPosition(getPos().add(getVelocity()));
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) { }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) { }

    @Override
    public boolean shouldSave() { return false; }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) { }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
