package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.block.custom.FortCannonBlockEntity;
import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.custom.CannonBallEntity;
import net.get900.pixelpirates.entity.custom.DynamiteEntity;
import net.get900.pixelpirates.entity.custom.PirateCrewEntity;
import net.get900.pixelpirates.entity.custom.PowderKegEntity;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BOSS 1/10 - CAPTAIN RACKHAM, master of Rackham's Hold.
 *
 * Phase 1 (duellist): Flintlock Shot (0.7 s aim, fast straight bullet), Cutlass Flurry (lunge + three
 * frontal slashes), Powder Keg (a lit barrel rolls at you - side-step or shoot it), Polly! (his parrot
 * pecks your face: damage + slow), Bosun's Whistle (pirate crew, capped at 4 alive).
 *
 * Phase 2 at 50% - POWDER-MAD: skin swaps to the scorched/lit-fuse texture, a powder blast throws
 * melee players back, and he adds Dynamite Barrage (3 fused sticks around you) and BROADSIDE! - red
 * target rings appear around the player and 2 s later the fort's wall cannons (or, away from the
 * fort, falling shells) hit every ring. Once, below 25%, he downs a Grog Swig (heal + strength).
 *
 * All explosives here are terrain-safe. Voice lines go to chat for players within 40 blocks.
 */
public class CaptainRackhamEntity extends ModBoss {
    private static final Vector3f MARK = new Vector3f(0.9f, 0.1f, 0.05f);
    private static final int BROADSIDE_TICKS = 44;

    /** A marked broadside impact point and the ticks until it lands. */
    private static final class Strike {
        final Vec3d pos; int ticks; boolean fromSky;
        Strike(Vec3d pos, int ticks) { this.pos = pos; this.ticks = ticks; }
    }

    private final List<Strike> strikes = new ArrayList<>();
    private final Set<UUID> greeted = new HashSet<>();
    /** Remaining cutlass-flurry slashes and ticks to the next one. */
    private int slashes, slashTimer;
    private boolean swigged;
    private int shoutCooldown;

    public CaptainRackhamEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected List<String> extraAnims() { return List.of("grog"); }

    // ------------------------------------------------------------------ ticking
    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (shoutCooldown > 0) shoutCooldown--;

        // greet newcomers
        if (this.age % 20 == 0) {
            for (ServerPlayerEntity p : sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(this) < 28 * 28)) {
                if (greeted.add(p.getUuid()))
                    p.sendMessage(voice(greeted.size() == 1 ? "Who dares storm Rackham's Hold? Man the guns!" : "Another one for the brig!"), false);
            }
        }
        tickFlurry(sw);
        tickStrikes(sw);

        // powder-mad: the lit fuses on his bandolier smoke and spit sparks
        if (isEnraged() && this.age % 3 == 0) {
            double yaw = Math.toRadians(this.bodyYaw);
            sw.spawnParticles(ParticleTypes.SMOKE, getX() - Math.cos(yaw) * 0.5, getY() + 2.3, getZ() - Math.sin(yaw) * 0.5, 1, 0.1, 0.1, 0.1, 0.01);
            if (this.random.nextInt(3) == 0)
                sw.spawnParticles(ParticleTypes.SMALL_FLAME, getX(), getY() + 2.0, getZ(), 1, 0.35, 0.3, 0.35, 0.01);
        }
        // last stand: one grog swig below 25%
        if (!swigged && this.getHealth() < this.getMaxHealth() * 0.25f) {
            swigged = true;
            triggerAnim(ACTION, "grog");
            shout("Ye'll not sink me yet!");
            this.heal(this.getMaxHealth() * 0.12f);
            this.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 200, 0));
            this.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 200, 0));
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_DRINK, SoundCategory.HOSTILE, 1.5f, 0.8f);
            sw.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(ModItems.GROG)),
                    getX(), getY() + 2.4, getZ(), 12, 0.2, 0.2, 0.2, 0.05);
        }
    }

    @Override
    protected void onEnrage() {
        shout("Ye've lit my powder now! FIRE AT WILL!");
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        // powder blast: shoves anyone hugging him back out of melee range
        sw.spawnParticles(ParticleTypes.FLAME, getX(), getY() + 1, getZ(), 40, 1.5, 0.8, 1.5, 0.08);
        for (LivingEntity e : Abilities.victims(this, 5)) {
            e.damage(this.getDamageSources().explosion(this, this), 5f);
            Vec3d away = e.getPos().subtract(this.getPos()).multiply(1, 0, 1);
            away = away.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : away.normalize();
            double kb = 1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
            e.addVelocity(away.x * 1.4 * kb, 0.6 * kb, away.z * 1.4 * kb);
            e.velocityModified = true;
        }
    }

    // ------------------------------------------------------------------ voice
    private Text voice(String line) {
        return Text.literal("<Captain Rackham> ").formatted(Formatting.GOLD).append(Text.literal(line).formatted(Formatting.YELLOW));
    }

    void shout(String line) {
        if (shoutCooldown > 0 || !(this.getWorld() instanceof ServerWorld sw)) return;
        shoutCooldown = 60;
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(this) < 40 * 40)) p.sendMessage(voice(line), false);
    }

    // ------------------------------------------------------------------ cutlass flurry
    void startFlurry(LivingEntity t) {
        if (t != null) {
            Vec3d dir = t.getPos().subtract(this.getPos()).multiply(1, 0, 1);
            if (dir.lengthSquared() > 1e-4) {
                this.setVelocity(dir.normalize().multiply(Math.min(1.0, 0.25 + dir.length() * 0.12)).add(0, 0.15, 0));
                this.velocityDirty = true;
            }
        }
        slashes = 3;
        slashTimer = 5;
    }

    private void tickFlurry(ServerWorld sw) {
        if (slashes <= 0 || --slashTimer > 0) return;
        slashTimer = 5;
        slashes--;
        Vec3d look = Vec3d.fromPolar(0, this.getHeadYaw()).normalize();
        Vec3d c = this.getPos().add(look.multiply(1.4)).add(0, 1.2, 0);
        sw.spawnParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 2, 0.5, 0.2, 0.5, 0);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.0f, 0.8f + slashes * 0.1f);
        for (LivingEntity e : Abilities.victims(this, 3.8)) {
            Vec3d to = e.getPos().subtract(this.getPos()).multiply(1, 0, 1);
            if (to.lengthSquared() > 1e-4 && to.normalize().dotProduct(look) < 0.2) continue;   // ~155 degree frontal arc
            if (e.damage(this.getDamageSources().mobAttack(this), slashes == 0 ? 7f : 5f)) {
                e.addVelocity(look.x * 0.35, 0.12, look.z * 0.35);
                e.velocityModified = true;
            }
        }
    }

    // ------------------------------------------------------------------ broadside
    void startBroadside(LivingEntity t) {
        if (!(this.getWorld() instanceof ServerWorld sw) || t == null) return;
        shout("FIRE THE BROADSIDE!");
        List<Vec3d> points = new ArrayList<>();
        points.add(ground(sw, t.getPos()));
        Vec3d lead = t.getPos().add(t.getVelocity().multiply(1, 0, 1).multiply(30));
        points.add(ground(sw, lead));
        for (int i = 0; i < 4; i++) {
            double a = this.random.nextDouble() * Math.PI * 2, r = 3 + this.random.nextDouble() * 3;
            points.add(ground(sw, t.getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r)));
        }
        List<FortCannonBlockEntity> guns = new ArrayList<>(FortCannonBlockEntity.near(sw, this.getBlockPos(), 40));
        guns.removeIf(FortCannonBlockEntity::isBusy);
        for (Vec3d p : points) {
            Strike s = new Strike(p, BROADSIDE_TICKS);
            FortCannonBlockEntity gun = nearestGun(guns, p);
            if (gun != null) {
                guns.remove(gun);
                // light the fuse so the ball lands about when the ring runs out (~1.3 blocks/tick average)
                int flight = (int) (Vec3d.ofCenter(gun.getPos()).distanceTo(p) / 1.3);
                gun.queueShot(p, MathHelper.clamp(BROADSIDE_TICKS - flight, 6, BROADSIDE_TICKS));
            } else {
                s.fromSky = true;
            }
            strikes.add(s);
        }
        sw.playSound(null, getBlockPos(), SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.HOSTILE, 1.2f, 1.3f);
    }

    private static FortCannonBlockEntity nearestGun(List<FortCannonBlockEntity> guns, Vec3d p) {
        FortCannonBlockEntity best = null;
        double bd = Double.MAX_VALUE;
        for (FortCannonBlockEntity g : guns) {
            double d = g.getPos().getSquaredDistance(p);
            if (d < bd && d > 25) { bd = d; best = g; }
        }
        return best;
    }

    private static Vec3d ground(ServerWorld sw, Vec3d p) {
        BlockPos b = BlockPos.ofFloored(p);
        // walk down to the first solid block under the point (courtyard floor), max 8
        for (int i = 0; i < 8 && sw.isAir(b.down()); i++) b = b.down();
        return new Vec3d(p.x, b.getY() + 0.05, p.z);
    }

    private void tickStrikes(ServerWorld sw) {
        for (Iterator<Strike> it = strikes.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            s.ticks--;
            if (s.ticks % 4 == 0) {
                // red target ring, tightening as it counts down
                double r = 1.2 + 1.0 * s.ticks / BROADSIDE_TICKS;
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16;
                    sw.spawnParticles(new DustParticleEffect(MARK, 1.6f), s.pos.x + Math.cos(a) * r, s.pos.y + 0.1, s.pos.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                sw.spawnParticles(new DustParticleEffect(MARK, 2.0f), s.pos.x, s.pos.y + 0.1, s.pos.z, 1, 0, 0, 0, 0);
            }
            if (s.fromSky && s.ticks == 16) {
                CannonBallEntity ball = CannonBallEntity.fort(sw, this, s.pos.x, s.pos.y + 18, s.pos.z, 7f);
                ball.setVelocity(0, -0.5, 0);
                sw.spawnEntity(ball);
            }
            if (s.ticks <= -20) it.remove();
        }
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Swigged", swigged);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        swigged = nbt.getBoolean("Swigged");
    }

    // =====================================================================================
    // Ability effects (wired up in MobSpecs)
    // =====================================================================================
    private static final Abilities.Shot BULLET = Abilities.Shot.of(net.minecraft.item.Items.IRON_NUGGET, 6f, ParticleTypes.SMOKE).withGravity(0f);

    /** Flintlock: muzzle flash + a fast straight bullet. */
    public static Abilities.Effect flintlock() {
        Abilities.Effect shot = Abilities.projectile(BULLET, 1, 0, 2.8f);
        return (mob, t) -> {
            if (t == null) return;
            ServerWorld sw = Abilities.sw(mob);
            Vec3d look = t.getPos().subtract(mob.getPos()).multiply(1, 0, 1).normalize();
            Vec3d m = mob.getPos().add(look.multiply(1.0)).add(0, mob.getHeight() * 0.62, 0);
            sw.spawnParticles(ParticleTypes.FLASH, m.x, m.y, m.z, 1, 0, 0, 0, 0);
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 6, 0.15, 0.15, 0.15, 0.03);
            sw.playSound(null, mob.getBlockPos(), SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST, SoundCategory.HOSTILE, 2.0f, 0.5f);
            shot.fire(mob, t);
        };
    }

    public static Abilities.Effect flurry() {
        return (mob, t) -> { if (mob instanceof CaptainRackhamEntity r) r.startFlurry(t); };
    }

    public static Abilities.Effect powderKeg() {
        return (mob, t) -> {
            if (t == null) return;
            Vec3d look = t.getPos().subtract(mob.getPos()).multiply(1, 0, 1).normalize();
            Vec3d from = mob.getPos().add(look.multiply(1.3)).add(0, 0.3, 0);
            mob.getWorld().spawnEntity(PowderKegEntity.roll(mob.getWorld(), mob, from, t.getPos()));
            mob.playSound(SoundEvents.ENTITY_TNT_PRIMED, 1.2f, 0.9f);
            if (mob instanceof CaptainRackhamEntity r) r.shout("Catch!");
        };
    }

    /** Polly flies at the target's face: small damage, slow, a burst of feathers. */
    public static Abilities.Effect polly() {
        return (mob, t) -> {
            if (t == null || !mob.canSee(t)) return;
            ServerWorld sw = Abilities.sw(mob);
            Vec3d a = mob.getPos().add(0, mob.getHeight() * 0.8, 0), b = t.getEyePos();
            for (int i = 0; i <= 12; i++) {
                Vec3d q = a.lerp(b, i / 12.0);
                sw.spawnParticles(new DustParticleEffect(new Vector3f(0.1f, 0.75f, 0.2f), 1.2f), q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
            }
            sw.spawnParticles(ParticleTypes.CLOUD, b.x, b.y, b.z, 6, 0.3, 0.3, 0.3, 0.02);
            sw.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_PARROT_HURT, SoundCategory.HOSTILE, 1.4f, 1.3f);
            if (t.damage(mob.getDamageSources().mobAttack(mob), 3f))
                t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 1), mob);
            if (mob instanceof CaptainRackhamEntity r) r.shout("Get 'em, Polly!");
        };
    }

    /** Bosun's whistle: two crew, but never more than four alive around him. */
    public static Abilities.Effect whistle() {
        Abilities.Effect crew = Abilities.summon("pixelpirates:pirate_crew", 2, 4);
        return (mob, t) -> {
            int alive = mob.getWorld().getEntitiesByClass(PirateCrewEntity.class, mob.getBoundingBox().expand(24), LivingEntity::isAlive).size();
            if (alive >= 4) return;
            mob.playSound(SoundEvents.ENTITY_VILLAGER_CELEBRATE, 1.5f, 1.6f);
            if (mob instanceof CaptainRackhamEntity r) r.shout("All hands! Repel boarders!");
            crew.fire(mob, t);
        };
    }

    /** Three fused sticks lobbed onto and around the target. */
    public static Abilities.Effect dynamiteBarrage() {
        return (mob, t) -> {
            if (t == null) return;
            if (mob instanceof CaptainRackhamEntity r) r.shout("Have some powder!");
            Vec3d from = mob.getPos().add(0, mob.getHeight() * 0.8, 0);
            for (int i = 0; i < 3; i++) {
                double a = mob.getRandom().nextDouble() * Math.PI * 2, rr = i == 0 ? 0 : 2.5 + mob.getRandom().nextDouble() * 1.5;
                Vec3d to = t.getPos().add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
                DynamiteEntity d = DynamiteEntity.lobbed(mob.getWorld(), mob, 30 + i * 4, 9f);
                d.setPosition(from);
                d.setVelocity(lob(from, to, 18 + i * 2));
                mob.getWorld().spawnEntity(d);
            }
            mob.playSound(SoundEvents.ENTITY_SNOWBALL_THROW, 1.2f, 0.6f);
        };
    }

    /** Velocity that carries a thrown item (gravity 0.03, drag 0.99) from a to b in about {@code ticks}. */
    static Vec3d lob(Vec3d a, Vec3d b, int ticks) {
        double drag = (1 - Math.pow(0.99, ticks)) / (1 - 0.99);        // distance covered per unit velocity
        double g = 0.03;
        double vx = (b.x - a.x) / drag, vz = (b.z - a.z) / drag;
        double vy = (b.y - a.y + 0.5 * g * ticks * ticks) / drag;
        return new Vec3d(vx, vy, vz);
    }

    public static Abilities.Effect broadside() {
        return (mob, t) -> { if (mob instanceof CaptainRackhamEntity r) r.startBroadside(t); };
    }
}
