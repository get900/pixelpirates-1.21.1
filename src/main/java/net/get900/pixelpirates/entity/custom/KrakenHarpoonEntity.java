package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.mob.ModMob;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * A barnacle-crusted whaler's harpoon the Kraken swallowed long ago and now SPITS (KrakenEntity harpoon_spit).
 * Flies straight (water doesn't slow it). If it runs a player through, it keeps going with them impaled on it
 * (they ride it) for up to 20 blocks; if it strikes a wall on the way they are PINNED there for 5 s. Otherwise it
 * drops them at the end of its run. It never harms the Kraken or its kin.
 */
public class KrakenHarpoonEntity extends ProjectileEntity implements GeoEntity {
    public static final double SPEED = 1.4, CARRY = 20;
    public static final int PIN_TICKS = 100;
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private enum State { FLYING, CARRYING, PINNED, STUCK }
    private State state = State.FLYING;
    private LivingEntity victim;
    private double carried;
    private int ticks, pinned;
    private float damage = 8f;
    private boolean playerShot, dropped;           // fired from the Krakenmaw Harpoon Gun (item/relic)

    public KrakenHarpoonEntity(EntityType<? extends ProjectileEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    public static KrakenHarpoonEntity spit(World world, LivingEntity owner, Vec3d from, Vec3d dir, float damage) {
        KrakenHarpoonEntity h = new KrakenHarpoonEntity(ModEntities.KRAKEN_HARPOON, world);
        h.setOwner(owner);
        h.damage = damage;
        h.setPosition(from);
        h.setVelocity(dir.normalize().multiply(SPEED));
        h.face(dir);
        return h;
    }

    /** The Krakenmaw Harpoon Gun: a player's harpoon - hits mobs (never players), bosses only take the damage (too big to
     *  carry), and the harpoon drops back as an item when it's done. */
    public static KrakenHarpoonEntity fired(World world, LivingEntity owner, float damage) {
        Vec3d dir = owner.getRotationVec(1f);
        KrakenHarpoonEntity h = spit(world, owner, owner.getEyePos().add(dir.multiply(1.2)).add(0, -0.2, 0), dir, damage);
        h.playerShot = true;
        h.setVelocity(dir.normalize().multiply(2.2));
        return h;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (playerShot && !dropped && !this.getWorld().isClient && reason == RemovalReason.DISCARDED) {
            dropped = true;
            this.getWorld().spawnEntity(new net.minecraft.entity.ItemEntity(this.getWorld(), getX(), getY(), getZ(),
                    new net.minecraft.item.ItemStack(net.get900.pixelpirates.item.ModItems.HARPOON)));
        }
        super.remove(reason);
    }

    @Override
    protected void initDataTracker() { }

    private void face(Vec3d v) {
        double h = Math.sqrt(v.x * v.x + v.z * v.z);
        this.setYaw((float) Math.toDegrees(Math.atan2(v.x, v.z)));
        this.setPitch((float) Math.toDegrees(Math.atan2(v.y, h)));
        this.prevYaw = this.getYaw();
        this.prevPitch = this.getPitch();
    }

    @Override
    public void tick() {
        super.tick();
        ticks++;
        Vec3d v = this.getVelocity();
        if (this.getWorld().isClient) {
            if (this.isTouchingWater()) this.getWorld().addParticle(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 0, 0.02, 0);
            return;
        }
        ServerWorld sw = (ServerWorld) this.getWorld();
        switch (state) {
            case FLYING -> {
                if (ticks > 60) { this.discard(); return; }
                Vec3d from = getPos(), to = from.add(v);
                HitResult block = sw.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
                if (block.getType() != HitResult.Type.MISS) to = block.getPos();
                EntityHitResult eh = ProjectileUtil.getEntityCollision(sw, this, from, to, this.getBoundingBox().stretch(v).expand(1.0),
                        e -> e instanceof LivingEntity le && le.isAlive() && e != getOwner() && !e.isSpectator()
                                && (playerShot ? !(e instanceof net.minecraft.entity.player.PlayerEntity)
                                               : !(le instanceof ModMob) && !(e instanceof net.minecraft.entity.player.PlayerEntity p && p.isCreative())));
                if (eh != null && eh.getEntity() instanceof LivingEntity le) { impale(sw, le); return; }
                if (block.getType() != HitResult.Type.MISS) { stick(sw, (BlockHitResult) block); return; }
                this.setPosition(to);
            }
            case CARRYING -> {
                if (victim == null || !victim.isAlive()) { this.discard(); return; }
                if (!victim.hasVehicle()) { if (net.get900.pixelpirates.item.BossArmor.slipped(victim)) { this.discard(); return; } victim.startRiding(this, true); }
                Vec3d from = getPos(), to = from.add(v), probe = to.add(v.normalize().multiply(0.7));
                BlockHitResult wall = sw.raycast(new RaycastContext(from, probe, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
                if (wall.getType() != HitResult.Type.MISS) { pin(sw, wall); return; }
                this.setPosition(to);
                carried += SPEED;
                if (ticks % 2 == 0) sw.spawnParticles(ParticleTypes.BUBBLE, getX(), getY(), getZ(), 6, 0.3, 0.3, 0.3, 0.05);
                if (carried >= CARRY) { release(sw); this.discard(); }
            }
            case PINNED -> {
                if (victim == null || !victim.isAlive()) { this.discard(); return; }
                if (!victim.hasVehicle() && !net.get900.pixelpirates.item.BossArmor.slipped(victim)) victim.startRiding(this, true);   // no wriggling off (unless Tidecourt)
                victim.fallDistance = 0;
                if (pinned % 20 == 0) victim.damage(this.getDamageSources().mobProjectile(this, getOwner() instanceof LivingEntity o ? o : null), 2f);
                if (victim instanceof ServerPlayerEntity p && pinned % 20 == 0)
                    p.sendMessage(Text.literal("Pinned to the rock! (" + (pinned / 20 + 1) + " s)").formatted(Formatting.DARK_RED), true);
                if (--pinned <= 0) { release(sw); this.discard(); }
            }
            case STUCK -> { if (ticks > 60) this.discard(); }
        }
    }

    private void impale(ServerWorld sw, LivingEntity le) {
        if (playerShot && (le instanceof net.get900.pixelpirates.entity.mob.ModBoss || le.getWidth() > 2.2f
                || le instanceof net.get900.pixelpirates.entity.mob.LeviathanSegmentEntity)) {     // too big to carry off
            le.damage(this.getDamageSources().mobProjectile(this, getOwner() instanceof LivingEntity o ? o : null), damage);
            sw.playSound(null, getBlockPos(), SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 2.0f, 0.6f);
            this.discard();
            return;
        }
        victim = le;
        state = State.CARRYING;
        carried = 0;
        this.setPosition(le.getPos().add(0, le.getHeight() * 0.5, 0));
        le.damage(this.getDamageSources().mobProjectile(this, getOwner() instanceof LivingEntity o ? o : null), damage);
        if (le.hasVehicle()) le.stopRiding();
        le.startRiding(this, true);
        sw.playSound(null, getBlockPos(), SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.HOSTILE, 2.0f, 0.5f);
        sw.spawnParticles(ParticleTypes.DAMAGE_INDICATOR, le.getX(), le.getY() + 1, le.getZ(), 8, 0.3, 0.3, 0.3, 0.1);
        if (le instanceof ServerPlayerEntity p) p.sendMessage(Text.literal("IMPALED!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
    }

    private void pin(ServerWorld sw, BlockHitResult wall) {
        state = State.PINNED;
        pinned = playerShot ? 60 : PIN_TICKS;
        this.setVelocity(Vec3d.ZERO);
        Vec3d back = getVelocityDir().multiply(-0.6);
        this.setPosition(wall.getPos().add(back));
        victim.damage(this.getDamageSources().mobProjectile(this, getOwner() instanceof LivingEntity o ? o : null), 6f);
        BlockPos bp = wall.getBlockPos();
        sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, sw.getBlockState(bp)), wall.getPos().x, wall.getPos().y, wall.getPos().z, 30, 0.4, 0.4, 0.4, 0.2);
        sw.playSound(null, getBlockPos(), SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.HOSTILE, 2.5f, 0.4f);
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.5f, 0.5f);
    }

    private Vec3d dirCache = new Vec3d(0, 0, 1);

    private Vec3d getVelocityDir() {
        Vec3d v = getVelocity();
        if (v.lengthSquared() > 1e-6) dirCache = v.normalize();
        return dirCache;
    }

    private void stick(ServerWorld sw, BlockHitResult hit) {
        state = State.STUCK;
        ticks = 0;
        this.setPosition(hit.getPos().add(getVelocityDir().multiply(-0.3)));
        this.setVelocity(Vec3d.ZERO);
        sw.playSound(null, getBlockPos(), SoundEvents.ITEM_TRIDENT_HIT_GROUND, SoundCategory.HOSTILE, 1.5f, 0.6f);
    }

    private void release(ServerWorld sw) {
        if (victim == null) return;
        LivingEntity v = victim;
        victim = null;
        v.stopRiding();
        v.fallDistance = 0;
        if (v instanceof ServerPlayerEntity p) p.sendMessage(Text.literal("You wrench yourself off the harpoon").formatted(Formatting.GRAY), true);
    }

    @Override
    public void setVelocity(Vec3d v) {
        super.setVelocity(v);
        if (v.lengthSquared() > 1e-6) { dirCache = v.normalize(); face(v); }
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
        positionUpdater.accept(passenger, getX(), getY() - passenger.getHeight() * 0.5, getZ());
    }

    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) { return getPos().add(0, -passenger.getHeight() * 0.5, 0); }

    @Override
    public boolean shouldRender(double distance) { return distance < 96 * 96; }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) { }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) { this.discard(); }            // transient: never survives a reload

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "rope", 0, st -> st.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
