package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.mob.BloodfinEntity;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/**
 * A winch-fired harpoon (HarpoonWinchBlock). Flies fast and straight, barely slowed by water, trailing its rope
 * back to the winch. Into the Bloodfin it sets a hook (BloodfinEntity#addHook); anything else just takes the hit.
 */
public class HarpoonEntity extends ThrownItemEntity implements software.bernie.geckolib.animatable.GeoEntity {
    private final software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache cache =
            software.bernie.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    @Override
    public void registerControllers(software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new software.bernie.geckolib.core.animation.AnimationController<>(this, "rope", 0,
                st -> st.setAndContinue(software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("idle"))));
    }

    @Override
    public software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    private static final Vector3f ROPE = new Vector3f(0.6f, 0.5f, 0.35f);
    public static final double SPEED = 2.2;
    private BlockPos winch = BlockPos.ORIGIN;

    public HarpoonEntity(EntityType<? extends ThrownItemEntity> type, World world) {
        super(type, world);
    }

    public HarpoonEntity(World world, LivingEntity owner, BlockPos winch) {
        super(ModEntities.HARPOON, owner, world);
        this.winch = winch.toImmutable();
    }

    @Override
    protected Item getDefaultItem() { return ModItems.HARPOON; }

    @Override
    protected float getGravity() { return 0.01f; }

    @Override
    public void tick() {
        super.tick();
        if (this.isTouchingWater()) this.setVelocity(this.getVelocity().multiply(0.97 / 0.8));   // cancel most of the water drag
        if (this.getWorld() instanceof ServerWorld sw) {
            Vec3d w = Vec3d.ofCenter(winch).add(0, 0.6, 0), d = getPos().subtract(w);
            if (this.age % 2 == 0 && d.lengthSquared() < 64 * 64)
                for (double t = 0; t <= 1; t += 0.12) {
                    Vec3d q = w.add(d.multiply(t));
                    sw.spawnParticles(new DustParticleEffect(ROPE, 0.7f), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            if (this.isTouchingWater()) sw.spawnParticles(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 4, 0.05, 0.05, 0.05, 0);
            else sw.spawnParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            // homing: a harpoon passing within 4 blocks of the Bloodfin bends onto it
            var sharks = sw.getEntitiesByClass(BloodfinEntity.class, getBoundingBox().expand(4), LivingEntity::isAlive);
            if (!sharks.isEmpty()) {
                Vec3d to = sharks.get(0).getBoundingBox().getCenter().subtract(getPos()).normalize();
                Vec3d v = getVelocity();
                double sp = Math.max(1.2, v.length());
                this.setVelocity(v.normalize().multiply(0.6).add(to.multiply(0.4)).normalize().multiply(sp));
                this.velocityDirty = true;
            }
            if (this.age > 50) this.discard();
        }
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        if (!(hit.getEntity() instanceof LivingEntity le) || le == this.getOwner()) return;
        le.damage(this.getDamageSources().thrown(this, this.getOwner()), le instanceof BloodfinEntity ? 6f : 8f);
        if (le instanceof BloodfinEntity b) b.addHook(winch, this.getOwner());
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (this.getWorld().isClient) return;
        if (hit.getType() == HitResult.Type.BLOCK)
            this.getWorld().playSound(null, getBlockPos(), SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.PLAYERS, 1.0f, 0.7f);
        if (!(hit instanceof EntityHitResult eh) || eh.getEntity() != this.getOwner()) this.discard();
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("Winch", NbtHelper.fromBlockPos(winch));
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Winch")) winch = NbtHelper.toBlockPos(nbt.getCompound("Winch"));
    }
}
