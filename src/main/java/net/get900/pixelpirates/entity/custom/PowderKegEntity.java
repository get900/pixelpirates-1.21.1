package net.get900.pixelpirates.entity.custom;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.mob.ModMob;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Captain Rackham's rolling powder keg: a lit barrel that trundles along the ground in a straight
 * line and blows up on contact with a player (or when the fuse runs out, or it hits a wall).
 * Telegraphed by its fuse sparks and the white flash of its last second - side-step it. Never
 * breaks blocks.
 */
public class PowderKegEntity extends Entity {
    private static final TrackedData<Integer> FUSE = DataTracker.registerData(PowderKegEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final double ROLL_SPEED = 0.34, RADIUS = 4.0;
    private static final float DAMAGE = 10f;

    @Nullable private LivingEntity owner;
    private Vec3d heading = new Vec3d(0, 0, 1);

    public PowderKegEntity(EntityType<? extends PowderKegEntity> type, World world) {
        super(type, world);
    }

    public static PowderKegEntity roll(World world, LivingEntity owner, Vec3d from, Vec3d toward) {
        PowderKegEntity k = new PowderKegEntity(ModEntities.POWDER_KEG, world);
        k.owner = owner;
        Vec3d d = toward.subtract(from).multiply(1, 0, 1);
        k.heading = d.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : d.normalize();
        k.setPosition(from);
        k.setYaw((float) (MathHelper.atan2(k.heading.z, k.heading.x) * 180 / Math.PI) - 90f);
        k.setVelocity(k.heading.multiply(ROLL_SPEED).add(0, 0.2, 0));
        return k;
    }

    public int getFuse() { return this.dataTracker.get(FUSE); }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(FUSE, 70);
    }

    @Override
    public void tick() {
        super.tick();
        Vec3d v = this.getVelocity();
        if (this.isOnGround()) v = new Vec3d(heading.x * ROLL_SPEED, Math.max(0, v.y), heading.z * ROLL_SPEED);
        v = v.add(0, -0.04, 0);
        try {
            this.move(MovementType.SELF, v);
        } catch (NullPointerException ignored) {
            // VS2 physics-thread race (CLAUDE.md crash cause #5)
        }
        this.setVelocity(v.multiply(0.98));

        if (this.getWorld().isClient) {
            this.getWorld().addParticle(ParticleTypes.FLAME, getX(), getY() + 0.9, getZ(), 0, 0.02, 0);
            if (this.age % 2 == 0) this.getWorld().addParticle(ParticleTypes.SMOKE, getX(), getY() + 1.0, getZ(), 0, 0.03, 0);
            return;
        }
        int fuse = getFuse() - 1;
        this.dataTracker.set(FUSE, fuse);
        if (this.age % 12 == 0) this.playSound(SoundEvents.BLOCK_WOOD_STEP, 0.6f, 0.7f);
        boolean touching = !this.getWorld().getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(0.25),
                e -> e.isAlive() && e != owner && !(e instanceof ModMob) && !(e instanceof PirateCrewEntity) && !e.isSpectator()).isEmpty();
        if (touching || fuse <= 0 || (this.horizontalCollision && this.age > 5) || this.isSubmergedInWater()) explode();
    }

    private void explode() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        Vec3d c = this.getPos().add(0, 0.4, 0);
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 20, 1.0, 0.6, 1.0, 0.05);
        sw.spawnParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 24, 1.2, 0.4, 1.2, 0.04);
        sw.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.2f, 0.85f);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, this.getBoundingBox().expand(RADIUS),
                e -> e.isAlive() && !(e instanceof ModMob) && !(e instanceof PirateCrewEntity))) {
            double d = e.getPos().distanceTo(c);
            if (d > RADIUS) continue;
            if (e.damage(this.getDamageSources().explosion(this, owner), (float) (DAMAGE * (1 - 0.55 * d / RADIUS)))) {
                e.setOnFireFor(2);
                Vec3d away = e.getPos().subtract(c).multiply(1, 0, 1);
                away = away.lengthSquared() < 1e-4 ? heading : away.normalize();
                double kb = 1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                e.addVelocity(away.x * 0.9 * kb, 0.5 * kb, away.z * 0.9 * kb);
                e.velocityModified = true;
            }
        }
        this.discard();
    }

    @Override
    public boolean canHit() { return true; }

    @Override
    public boolean damage(net.minecraft.entity.damage.DamageSource source, float amount) {
        // shoot or smack it to set it off early, wherever it is
        if (!this.getWorld().isClient && !this.isRemoved()) explode();
        return true;
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.dataTracker.set(FUSE, nbt.getInt("Fuse"));
        heading = new Vec3d(nbt.getDouble("HX"), 0, nbt.getDouble("HZ"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putInt("Fuse", getFuse());
        nbt.putDouble("HX", heading.x);
        nbt.putDouble("HZ", heading.z);
    }

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
