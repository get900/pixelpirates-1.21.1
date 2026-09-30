package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * Thrown bait: CHUM (or a chunk of BLOODFIN FLESH). In water it bobs at the surface in a spreading cloud of blood
 * for 12 s; the Bloodfin abandons whatever it is doing to swim over and gorge on it (BloodfinEntity#tickFeeding) -
 * its own flesh even heals it. On land it lies there for 3 s and rots away.
 */
public class ChumEntity extends ThrownItemEntity {
    private static final TrackedData<Boolean> FLESH = DataTracker.registerData(ChumEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final Vector3f BLOOD = new Vector3f(0.55f, 0.02f, 0.02f);
    private boolean settled;
    private int life = 240;

    public ChumEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public ChumEntity(World world, LivingEntity owner, boolean flesh) {
        super(ModEntities.CHUM, owner, world);
        this.dataTracker.set(FLESH, flesh);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(FLESH, false);
    }

    public boolean isFlesh() { return this.dataTracker.get(FLESH); }

    @Override
    protected Item getDefaultItem() { return isFlesh() ? ModItems.BLOODFIN_FLESH : ModItems.CHUM; }

    @Override
    public void tick() {
        if (settled || this.isTouchingWater()) {
            settled = true;
            this.baseTick();
            // bob at the surface
            double top = getBlockY() + 1;
            while (this.getWorld().getFluidState(net.minecraft.util.math.BlockPos.ofFloored(getX(), top, getZ())).isIn(net.minecraft.registry.tag.FluidTags.WATER) && top < getY() + 32) top++;
            double vy = Math.max(-0.08, Math.min(0.08, (top - 0.25 - getY()) * 0.2));
            this.setVelocity(this.getVelocity().multiply(0.8, 0, 0.8).add(0, vy, 0));
            this.setPosition(this.getPos().add(this.getVelocity()));
        } else {
            super.tick();
        }
        if (this.getWorld() instanceof ServerWorld sw) {
            if (this.age % 3 == 0) sw.spawnParticles(new DustParticleEffect(BLOOD, isFlesh() ? 2.2f : 1.6f),
                    getX(), getY() - 0.2, getZ(), isFlesh() ? 6 : 4, 1.0 + age * 0.004, 0.2, 1.0 + age * 0.004, 0);
            if (--life <= 0 || (!settled && this.isOnGround() && life < 180)) this.discard();
        }
    }

    @Override
    protected void onCollision(HitResult hit) {
        if (hit.getType() == HitResult.Type.BLOCK && !this.isTouchingWater()) {
            this.setVelocity(Vec3d.ZERO);
            life = Math.min(life, 60);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Flesh", isFlesh());
        nbt.putInt("Life", life);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(FLESH, nbt.getBoolean("Flesh"));
        life = nbt.getInt("Life");
    }
}
