package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * A thrown relic weapon (the Trident of the Sunken Court): flies straight, hits for `damage`, then flies home to the
 * thrower like a Loyalty trident (after a hit, 6 ticks stuck or 24 ticks of flight). Carries its ItemStack (synced,
 * so the renderer draws the real 3D weapon). Dropped as an item if the thrower is gone.
 */
public class ThrownRelicEntity extends PersistentProjectileEntity {
    private static final TrackedData<ItemStack> STACK = DataTracker.registerData(ThrownRelicEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final TrackedData<Boolean> RETURNING = DataTracker.registerData(ThrownRelicEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private float damage = 10f;
    private boolean dealtDamage;
    private int stuck;

    public ThrownRelicEntity(EntityType<? extends ThrownRelicEntity> type, World world) { super(type, world); }

    public ThrownRelicEntity(World world, LivingEntity owner, ItemStack stack, float damage) {
        super(ModEntities.THROWN_RELIC, owner, world);
        this.dataTracker.set(STACK, stack.copy());
        this.damage = damage;
        this.pickupType = owner instanceof PlayerEntity p && p.isCreative() ? PickupPermission.CREATIVE_ONLY : PickupPermission.ALLOWED;
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(STACK, ItemStack.EMPTY);
        this.dataTracker.startTracking(RETURNING, false);
    }

    public ItemStack stack() { return this.dataTracker.get(STACK); }

    public boolean returning() { return this.dataTracker.get(RETURNING); }

    @Override
    protected ItemStack asItemStack() { return stack().copy(); }

    @Override
    public void tick() {
        if (this.inGroundTime > 6 || (!returning() && this.age > 24 && !this.inGround)) this.dataTracker.set(RETURNING, true);
        Entity owner = this.getOwner();
        if (returning()) {
            if (owner == null || !owner.isAlive()) {
                if (!this.getWorld().isClient) { this.dropStack(this.asItemStack(), 0.1f); this.discard(); }
                return;
            }
            this.setNoClip(true);
            Vec3d to = owner.getEyePos().subtract(this.getPos());
            this.setPos(this.getX(), this.getY() + to.y * 0.03, this.getZ());
            this.setVelocity(this.getVelocity().multiply(0.9).add(to.normalize().multiply(0.25)));
            if (to.length() < 1.6) {
                if (!this.getWorld().isClient) {
                    if (owner instanceof PlayerEntity p && !p.isCreative() && !p.getInventory().insertStack(this.asItemStack()))
                        this.dropStack(this.asItemStack(), 0.1f);
                    this.getWorld().playSound(null, owner.getBlockPos(), SoundEvents.ITEM_TRIDENT_RETURN, SoundCategory.PLAYERS, 1.0f, 1.0f);
                }
                this.discard();
                return;
            }
        }
        super.tick();
        if (this.getWorld().isClient && !this.inGround) this.getWorld().addParticle(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Nullable
    @Override
    protected EntityHitResult getEntityCollision(Vec3d from, Vec3d to) {
        return dealtDamage || returning() ? null : super.getEntityCollision(from, to);
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Entity target = hit.getEntity();
        Entity owner = this.getOwner();
        this.dealtDamage = true;
        target.damage(this.getDamageSources().trident(this, owner == null ? this : owner), damage);
        this.setVelocity(this.getVelocity().multiply(-0.01, -0.1, -0.01));
        this.playSound(SoundEvents.ITEM_TRIDENT_HIT, 1.0f, 1.0f);
        this.dataTracker.set(RETURNING, true);
    }

    @Override
    protected boolean tryPickup(PlayerEntity player) {
        return super.tryPickup(player) || this.isNoClip() && this.isOwner(player) && player.getInventory().insertStack(this.asItemStack());
    }

    @Override
    protected net.minecraft.sound.SoundEvent getHitSound() { return SoundEvents.ITEM_TRIDENT_HIT_GROUND; }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        if (this.isOwner(player) || this.getOwner() == null) super.onPlayerCollision(player);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Weapon")) this.dataTracker.set(STACK, ItemStack.fromNbt(nbt.getCompound("Weapon")));
        this.dealtDamage = nbt.getBoolean("DealtDamage");
        this.damage = nbt.contains("Dmg") ? nbt.getFloat("Dmg") : 10f;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("Weapon", stack().writeNbt(new NbtCompound()));
        nbt.putBoolean("DealtDamage", this.dealtDamage);
        nbt.putFloat("Dmg", damage);
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) { return true; }
}
