package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.mob.RevenantPartEntity;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The hurled Gallowbrand: spins end over end (geo gallowbrand, clip `spin`), bites into the first thing it meets, then
 * flies HOME like a loyalty trident - after a hit, after sticking in a block for a moment, or after ~28 blocks of
 * flight - and drops back into its thrower's inventory. Deals 14 (x2.5 = 35 to a Chained Revenant limb on the walls).
 * If the thrower is gone it falls as an item instead of being lost.
 */
public class ThrownGallowbrandEntity extends PersistentProjectileEntity implements GeoEntity {
    private static final TrackedData<Boolean> RETURNING = DataTracker.registerData(ThrownGallowbrandEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final RawAnimation SPIN = RawAnimation.begin().thenLoop("spin");
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final float DAMAGE = 14f, LIMB_MULT = 2.5f;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private ItemStack stack = new ItemStack(ModItems.GALLOWBRAND);
    private boolean dealtDamage;
    private int flightTicks, returnTicks;

    public ThrownGallowbrandEntity(EntityType<? extends ThrownGallowbrandEntity> type, World world) {
        super(type, world);
    }

    public ThrownGallowbrandEntity(World world, LivingEntity owner, ItemStack stack) {
        super(ModEntities.THROWN_GALLOWBRAND, owner, world);
        this.stack = stack.copy();
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(RETURNING, false);
    }

    public boolean returning() { return this.dataTracker.get(RETURNING); }

    public boolean stuck() { return this.inGround; }

    private void startReturn() {
        if (returning()) return;
        this.dataTracker.set(RETURNING, true);
        this.dealtDamage = true;
    }

    @Override
    public void tick() {
        if (this.inGroundTime > 6 || flightTicks > 24) startReturn();
        if (!this.inGround) flightTicks++;
        Entity owner = this.getOwner();
        if (returning() && owner != null) {
            if (!isOwnerAlive()) {
                if (!this.getWorld().isClient && this.pickupType == PickupPermission.ALLOWED) this.dropStack(this.asItemStack(), 0.1f);
                this.discard();
            } else {
                // loyalty-style homing (as TridentEntity with Loyalty IV), a little quicker
                this.setNoClip(true);
                Vec3d d = owner.getEyePos().subtract(this.getPos());
                this.setPos(this.getX(), this.getY() + d.y * 0.02 * 4, this.getZ());
                if (this.getWorld().isClient) this.lastRenderY = this.getY();
                this.setVelocity(this.getVelocity().multiply(0.95).add(d.normalize().multiply(0.07 * 4)));
                if (returnTicks++ == 0) this.playSound(SoundEvents.ITEM_TRIDENT_RETURN, 10.0f, 0.7f);
                if (returnTicks > 200 && !this.getWorld().isClient) {                   // something is very wrong: give up, drop it
                    this.dropStack(this.asItemStack(), 0.1f);
                    this.discard();
                }
            }
        } else if (returning() && owner == null && !this.getWorld().isClient) {
            this.dropStack(this.asItemStack(), 0.1f);
            this.discard();
            return;
        }
        super.tick();
        if (this.getWorld() instanceof ServerWorld sw && !this.inGround && this.age % 2 == 0)
            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 0.2, getZ(), 1, 0.1, 0.1, 0.1, 0.01);
    }

    private boolean isOwnerAlive() {
        Entity owner = this.getOwner();
        if (owner == null || !owner.isAlive()) return false;
        return !(owner instanceof ServerPlayerEntity sp) || !sp.isSpectator();
    }

    @Override
    protected ItemStack asItemStack() { return this.stack.copy(); }

    @Override
    protected EntityHitResult getEntityCollision(Vec3d from, Vec3d to) {
        return this.dealtDamage ? null : super.getEntityCollision(from, to);
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        Entity target = hit.getEntity();
        Entity owner = this.getOwner();
        float dmg = DAMAGE;
        boolean limb = target instanceof RevenantPartEntity p && !p.isHead();
        if (limb) dmg *= LIMB_MULT;
        DamageSource src = this.getDamageSources().trident(this, owner == null ? this : owner);
        this.dealtDamage = true;
        SoundEvent sound = SoundEvents.ITEM_TRIDENT_HIT;
        if (target.damage(src, dmg) && target instanceof LivingEntity le && owner instanceof LivingEntity lo) {
            net.minecraft.enchantment.EnchantmentHelper.onUserDamaged(le, lo);
            net.minecraft.enchantment.EnchantmentHelper.onTargetDamaged(lo, le);
            this.onHit(le);
        }
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(limb ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.CRIT, getX(), getY(), getZ(), limb ? 24 : 10, 0.3, 0.3, 0.3, 0.1);
            if (limb) sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.PLAYERS, 1.5f, 0.6f);
        }
        this.setVelocity(this.getVelocity().multiply(-0.01, -0.1, -0.01));
        this.playSound(sound, 1.0f, 0.7f);
        startReturn();
    }

    @Override
    protected boolean tryPickup(PlayerEntity player) {
        return super.tryPickup(player) || this.isNoClip() && this.isOwner(player) && player.getInventory().insertStack(this.asItemStack());
    }

    @Override
    protected SoundEvent getHitSound() { return SoundEvents.ITEM_TRIDENT_HIT_GROUND; }

    @Override
    public void onPlayerCollision(PlayerEntity player) {
        if (this.isOwner(player) || this.getOwner() == null) super.onPlayerCollision(player);
    }

    @Override
    public void age() {
        if (this.pickupType != PickupPermission.ALLOWED) super.age();
    }

    @Override
    public boolean shouldRender(double cameraX, double cameraY, double cameraZ) { return true; }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Blade")) this.stack = ItemStack.fromNbt(nbt.getCompound("Blade"));
        this.dealtDamage = nbt.getBoolean("DealtDamage");
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.put("Blade", this.stack.writeNbt(new NbtCompound()));
        nbt.putBoolean("DealtDamage", this.dealtDamage);
    }

    // ------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spin", 0, st -> st.setAndContinue(stuck() ? IDLE : SPIN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
