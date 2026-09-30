package net.get900.pixelpirates.homestead.grapple;

import net.get900.pixelpirates.homestead.HomesteadEntities;
import net.get900.pixelpirates.homestead.HomesteadItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * The flying / anchored grappling hook. Flies straight-ish (light gravity) for up to 28 blocks; on a block it bites
 * in and REELS its thrower toward it (1.1 b/t, no fall damage while reeling) until they are within 2 blocks, sneak,
 * use the hook again, or 5 s pass. On a creature it yanks it toward the thrower and falls away.
 * The rope is drawn by GrappleHookRenderer from the hook to the thrower's hand.
 */
public class GrappleHookEntity extends ThrownItemEntity {
    private static final TrackedData<Boolean> ANCHORED = DataTracker.registerData(GrappleHookEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final double RANGE = 28;
    private int reel;
    private Vec3d start = Vec3d.ZERO;

    public GrappleHookEntity(EntityType<? extends GrappleHookEntity> type, World world) { super(type, world); }

    public GrappleHookEntity(World world, LivingEntity owner) {
        super(HomesteadEntities.GRAPPLE_HOOK, owner, world);
        start = owner.getEyePos();
    }

    @Override
    protected Item getDefaultItem() { return HomesteadItems.GRAPPLING_HOOK; }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(ANCHORED, false);
    }

    public boolean anchored() { return this.dataTracker.get(ANCHORED); }

    @Override
    protected float getGravity() { return anchored() ? 0 : 0.02f; }

    @Override
    public void tick() {
        if (anchored()) {
            this.setVelocity(Vec3d.ZERO);
            this.baseTick();                                                  // stay put: no projectile movement
            if (!getWorld().isClient) reelOwner();
            return;
        }
        super.tick();
        if (!getWorld().isClient) {
            Entity o = getOwner();
            if (o == null || !o.isAlive() || this.getPos().distanceTo(start) > RANGE || this.age > 40) retract();
        }
    }

    private void reelOwner() {
        Entity o = getOwner();
        if (!(o instanceof PlayerEntity p) || !p.isAlive() || p.getWorld() != getWorld()) {
            retract();
            return;
        }
        Vec3d to = this.getPos().subtract(p.getPos().add(0, 0.6, 0));
        double d = to.length();
        if (d < 2.0 || p.isSneaking() || ++reel > 100) {
            if (d < 2.0) p.setVelocity(p.getVelocity().multiply(0.3).add(0, 0.35, 0));        // a little hop onto the ledge
            p.velocityModified = true;
            retract();
            return;
        }
        Vec3d v = to.normalize().multiply(Math.min(1.1, 0.25 + d * 0.12));
        p.setVelocity(v.add(0, 0.04, 0));
        p.velocityModified = true;
        p.fallDistance = 0;
        if (reel % 6 == 0) getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 0.4f, 1.6f);
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        super.onBlockHit(hit);
        if (getWorld().isClient) return;
        Vec3d p = hit.getPos().subtract(getVelocity().normalize().multiply(0.1));
        this.setPosition(p);
        this.dataTracker.set(ANCHORED, true);
        this.reel = 0;
        getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1f, 0.8f);
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        super.onEntityHit(hit);
        if (getWorld().isClient) return;
        Entity o = getOwner(), t = hit.getEntity();
        if (o != null && t != o && t instanceof LivingEntity) {
            Vec3d v = o.getPos().subtract(t.getPos()).normalize().multiply(1.2).add(0, 0.35, 0);
            t.setVelocity(v);
            t.velocityModified = true;
            getWorld().playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ENTITY_FISHING_BOBBER_RETRIEVE, SoundCategory.PLAYERS, 1f, 0.8f);
        }
        retract();
    }

    public void retract() {
        if (getOwner() instanceof PlayerEntity p) GrapplingHookItem.ACTIVE.remove(p.getUuid());
        this.discard();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Anchored", anchored());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(ANCHORED, nbt.getBoolean("Anchored"));
    }

    @Override
    public boolean shouldSave() { return false; }                            // never persists across a reload
}
