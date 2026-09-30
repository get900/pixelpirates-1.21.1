package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/**
 * KRAKEN ARM - phase 1 of the Kraken fight (KrakenEntity). A colossal tentacle (~9 blocks) bursting out of the pit
 * floor. It turns to face its prey and:
 *   SLAM  - a red line races along the floor toward you, then the arm crashes down along it (0.9 s)
 *   SWEEP - it winds to one side and lashes across everything in front of it (0.7 s)
 *   GRAB  - it seizes a player and holds them high (2.2 s, 12 damage to the arm breaks the grip), then hurls
 *           them down toward the abyss where the Kraken waits
 * On death it writhes and sinks back into the seabed in a burst of ink.
 */
public class KrakenArmEntity extends ModMob {
    private static final Vector3f WARN = new Vector3f(0.85f, 0.1f, 0.1f);
    private Vec3d slamAt; private int slamTicks;
    private Vec3d sweepDir; private int sweepTicks;
    private int grabWindup; private LivingEntity grabTarget;
    private LivingEntity held; private int heldTicks; private float heldDamage;
    private boolean rose;
    private UUID kraken;

    public KrakenArmEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
    }

    public void setKraken(UUID id) { this.kraken = id; }

    @Override
    protected List<String> extraAnims() { return List.of("sweep", "grab", "fling", "rise", "retract"); }

    @Override
    protected List<Abilities.Ability> activeAbilities() {
        return busy() ? List.of() : super.activeAbilities();
    }

    private boolean busy() { return slamTicks > 0 || sweepTicks > 0 || grabWindup > 0 || held != null || isDead(); }

    private float scale() { return spec().renderScale(); }

    private Vec3d tip() {
        Vec3d f = Vec3d.fromPolar(0, this.bodyYaw);
        return getPos().add(f.multiply(1.4 * scale() / 2)).add(0, 6.2 * scale() / 2, 0);
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
        Vec3d t = tip();
        positionUpdater.accept(passenger, t.x, t.y - 1.2, t.z);
    }

    /** Vanilla drops a dismounting passenger on TOP of the vehicle - let go from the coil instead. */
    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) { return tip(); }

    private void face(Vec3d p) {
        float yaw = (float) Math.toDegrees(Math.atan2(-(p.x - getX()), p.z - getZ()));
        this.setYaw(yaw);
        this.bodyYaw = this.headYaw = yaw;
    }

    // ------------------------------------------------------------------ attacks (wired in MobSpecs)
    void startSlam(LivingEntity t) {
        if (t == null) return;
        Vec3d d = t.getPos().subtract(getPos()).multiply(1, 0, 1);
        double len = Math.min(8.5, Math.max(2, d.length()));
        slamAt = getPos().add(d.lengthSquared() < 1e-4 ? Vec3d.fromPolar(0, bodyYaw).multiply(len) : d.normalize().multiply(len));
        slamAt = new Vec3d(slamAt.x, t.getY(), slamAt.z);
        slamTicks = 18;
        face(slamAt);
    }

    void startSweep(LivingEntity t) {
        if (t == null) return;
        Vec3d d = t.getPos().subtract(getPos()).multiply(1, 0, 1);
        sweepDir = d.lengthSquared() < 1e-4 ? Vec3d.fromPolar(0, bodyYaw) : d.normalize();
        sweepTicks = 14;
        face(t.getPos());
    }

    void startGrab(LivingEntity t) {
        if (t == null) return;
        grabTarget = t;
        grabWindup = 6;
        face(t.getPos());
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (!rose && this.age >= 3) {
            rose = true;
            triggerAnim(ACTION, "rise");
            sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.GRAVEL.getDefaultState()),
                    getX(), getY() + 0.5, getZ(), 80, 1.5, 0.5, 1.5, 0.3);
            sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 1, getZ(), 60, 1.2, 2, 1.2, 0.3);
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 2.0f, 0.6f);
        }
        if (!busy() && getTarget() != null) face(getTarget().getPos());
        tickSlam(sw);
        tickSweep(sw);
        tickGrab(sw);
    }

    private void tickSlam(ServerWorld sw) {
        if (slamTicks <= 0) return;
        slamTicks--;
        Vec3d base = getPos();
        Vec3d line = slamAt.subtract(base);
        if (slamTicks % 2 == 0) {
            double reach = 1 - slamTicks / 18.0;                         // the warning races out along the floor
            for (double s = 0.15; s <= reach; s += 0.06) {
                Vec3d q = base.add(line.multiply(s));
                sw.spawnParticles(new DustParticleEffect(WARN, 1.6f), q.x, slamAt.y + 0.15, q.z, 1, 0.3, 0, 0.3, 0);
            }
        }
        if (slamTicks > 0) return;
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 3.0f, 0.4f);
        for (double s = 0.2; s <= 1.0; s += 0.1) {
            Vec3d q = base.add(line.multiply(s));
            sw.spawnParticles(ParticleTypes.EXPLOSION, q.x, slamAt.y + 0.3, q.z, 1, 0.3, 0.1, 0.3, 0);
            sw.spawnParticles(ParticleTypes.SQUID_INK, q.x, slamAt.y + 0.5, q.z, 4, 0.5, 0.3, 0.5, 0.05);
        }
        for (LivingEntity e : Abilities.victims(this, 10)) {
            Vec3d rel = e.getPos().subtract(base).multiply(1, 0, 1);
            Vec3d dir = line.multiply(1, 0, 1).normalize();
            double along = rel.dotProduct(dir);
            if (along < 0.5 || along > line.multiply(1, 0, 1).length() + 1.5) continue;
            if (rel.subtract(dir.multiply(along)).length() > 2.2 || Math.abs(e.getY() - slamAt.y) > 3.5) continue;
            if (e.damage(this.getDamageSources().mobAttack(this), 14f)) { e.addVelocity(0, -0.6, 0); e.velocityModified = true; }
        }
    }

    private void tickSweep(ServerWorld sw) {
        if (sweepTicks <= 0) return;
        sweepTicks--;
        if (sweepTicks % 3 == 0)
            for (int i = -6; i <= 6; i++) {
                double a = Math.atan2(sweepDir.z, sweepDir.x) + i * Math.PI / 12;
                sw.spawnParticles(ParticleTypes.BUBBLE, getX() + Math.cos(a) * 7, getY() + 2, getZ() + Math.sin(a) * 7, 2, 0.3, 1.2, 0.3, 0);
            }
        if (sweepTicks > 0) return;
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.5f, 0.4f);
        Vec3d side = new Vec3d(-sweepDir.z, 0, sweepDir.x);
        for (LivingEntity e : Abilities.victims(this, 9)) {
            Vec3d rel = e.getPos().subtract(getPos()).multiply(1, 0, 1);
            if (rel.dotProduct(sweepDir) < -0.5 || Math.abs(e.getY() - (getY() + 3)) > 5) continue;
            if (e.damage(this.getDamageSources().mobAttack(this), 9f)) {
                double kb = 1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                e.addVelocity(side.x * 1.4 * kb, 0.4 * kb, side.z * 1.4 * kb);
                e.velocityModified = true;
            }
        }
    }

    private void tickGrab(ServerWorld sw) {
        if (grabWindup > 0 && --grabWindup == 0) {
            LivingEntity t = grabTarget;
            grabTarget = null;
            if (t != null && t.isAlive() && t.squaredDistanceTo(this) < 9 * 9 && !(t instanceof ModBoss)) {
                if (t.hasVehicle()) t.stopRiding();
                held = t;
                heldTicks = 44;
                heldDamage = 0;
                t.startRiding(this, true);
                sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 2.0f, 0.5f);
                if (t instanceof ServerPlayerEntity p)
                    p.sendMessage(Text.literal("The arm coils around you! Hit it (12 damage) to break free!").formatted(Formatting.DARK_PURPLE), true);
            }
        }
        if (held == null) return;
        if (!held.isAlive()) { held = null; return; }
        if (!held.hasVehicle() && held.squaredDistanceTo(this) < 100) held.startRiding(this, true);
        if (--heldTicks % 20 == 0) held.damage(this.getDamageSources().mobAttack(this), 2f);
        if (heldTicks > 0) return;
        // FLING toward the abyss
        LivingEntity v = held;
        held = null;
        v.stopRiding();
        triggerAnim(ACTION, "fling");
        Vec3d toward = krakenPos().subtract(getPos()).multiply(1, 0, 1);
        toward = toward.lengthSquared() < 1 ? Vec3d.fromPolar(0, bodyYaw) : toward.normalize();
        v.setVelocity(toward.x * 1.5, -0.3, toward.z * 1.5);
        v.velocityModified = true;
        v.damage(this.getDamageSources().mobAttack(this), 6f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 2.0f, 0.5f);
    }

    private Vec3d krakenPos() {
        if (kraken != null && getWorld() instanceof ServerWorld sw && sw.getEntity(kraken) instanceof KrakenEntity k) return k.getPos();
        var type = ModMobs.TYPES.get("kraken");
        if (type != null)
            for (Entity e : getWorld().getEntitiesByType(type, getBoundingBox().expand(32), Entity::isAlive)) return e.getPos();
        return getPos().add(Vec3d.fromPolar(0, bodyYaw).multiply(8));
    }

    private void release(ServerWorld sw) {
        if (held == null) return;
        LivingEntity v = held;
        held = null;
        v.stopRiding();
        v.addVelocity(0, 0.3, 0);
        v.velocityModified = true;
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_SLIME_HURT, SoundCategory.HOSTILE, 2.0f, 0.5f);
        if (v instanceof ServerPlayerEntity p) p.sendMessage(Text.literal("You tear free of its grip!").formatted(Formatting.GOLD), true);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean hurt = super.damage(source, amount);
        if (hurt && held != null && source.getAttacker() instanceof PlayerEntity && this.getWorld() instanceof ServerWorld sw) {
            heldDamage += amount;
            if (heldDamage >= 12f) release(sw);
        }
        return hurt;
    }

    // ------------------------------------------------------------------ death: writhe and sink into the seabed
    @Override
    public void onDeath(DamageSource source) {
        if (this.getWorld() instanceof ServerWorld sw) {
            release(sw);
            triggerAnim(ACTION, "retract");
            sw.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 4, getZ(), 200, 1.5, 4, 1.5, 0.1);
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_DEATH, SoundCategory.HOSTILE, 2.0f, 0.5f);
        }
        super.onDeath(source);
    }

    @Override
    protected void updatePostDeath() {
        ++this.deathTime;
        if (this.deathTime >= 36 && !this.getWorld().isClient() && !this.isRemoved()) {
            this.getWorld().sendEntityStatus(this, (byte) 60);
            this.remove(RemovalReason.KILLED);
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Rose", rose);
        if (kraken != null) nbt.putUuid("Kraken", kraken);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        rose = nbt.getBoolean("Rose");
        if (nbt.containsUuid("Kraken")) kraken = nbt.getUuid("Kraken");
    }

    public static Abilities.Effect slam() { return (mob, t) -> { if (mob instanceof KrakenArmEntity a) a.startSlam(t); }; }

    public static Abilities.Effect sweep() { return (mob, t) -> { if (mob instanceof KrakenArmEntity a) a.startSweep(t); }; }

    public static Abilities.Effect grab() { return (mob, t) -> { if (mob instanceof KrakenArmEntity a) a.startGrab(t); }; }
}
