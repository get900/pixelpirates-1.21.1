package net.get900.pixelpirates.entity.mob;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BOSS 3/10 - THE MOLTEN WARLORD, lord of the Cinder Citadel. ~5 blocks tall, swinging a chain mace.
 *
 * QUENCH: his molten armour halves all damage. Four Quench Valves (QuenchValveBlock) in the Crucible's
 * walls each pour a cold cascade for 8 s; if he stands in one he is QUENCHED - stunned for 2 s, cooled
 * to grey stone (the "quenched" skin) and taking 125% damage for 10 s. Valves refill after 45 s.
 *
 * Phase 1: Mace Lash (a burning line on the floor marks it, then the chain shoots out ~11 blocks along
 * it), Flail Whirl (a fire ring marks the danger band 2.5-7 blocks out - hug him or back off), Fissure
 * Slam (three burning cracks race out across the floor), Chain Hook (yanks a player in).
 * Phase 2 "Molten Core" at 50% (white-hot skin): adds Meteor Rain (marked impact rings, then falling
 * fire), Forge Call (flame sprites, max 4) and Core Vent (weakness aura + resistance).
 */
public class MoltenWarlordEntity extends ModBoss {
    private static final TrackedData<Boolean> QUENCHED = DataTracker.registerData(MoltenWarlordEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final Vector3f WARN = new Vector3f(1f, 0.35f, 0.05f);
    public static final int CASCADE_TICKS = 160, QUENCH_TICKS = 200;

    private static final class Timed {
        final Vec3d pos; Vec3d dir; int ticks;
        Timed(Vec3d pos, Vec3d dir, int ticks) { this.pos = pos; this.dir = dir; this.ticks = ticks; }
    }

    private final List<Timed> cascades = new ArrayList<>();
    private final List<Timed> fissures = new ArrayList<>();
    private final List<Timed> meteors = new ArrayList<>();
    private final Set<UUID> greeted = new HashSet<>();
    private int quenchTicks, stun, hintCooldown;
    private Vec3d lashDir; private int lashTicks;
    private int whirlTicks;

    public MoltenWarlordEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(QUENCHED, false);
    }

    public boolean isQuenched() { return this.dataTracker.get(QUENCHED); }

    @Override
    public String skinVariant() { return isQuenched() ? "quenched" : super.skinVariant(); }

    @Override
    protected List<String> extraAnims() { return List.of("quenched"); }

    @Override
    protected List<Abilities.Ability> activeAbilities() {
        return stun > 0 ? List.of() : super.activeAbilities();
    }

    // ------------------------------------------------------------------ quench
    public void addCascade(Vec3d at) {
        cascades.add(new Timed(at, Vec3d.ZERO, CASCADE_TICKS));
    }

    public void quench() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        boolean fresh = !isQuenched();
        quenchTicks = QUENCH_TICKS;
        this.dataTracker.set(QUENCHED, true);
        if (!fresh) return;
        stun = 40;
        this.getNavigation().stop();
        triggerAnim(ACTION, "quenched");
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.HOSTILE, 3.0f, 0.5f);
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
        sw.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 2.5, getZ(), 80, 1.2, 1.5, 1.2, 0.08);
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(this) < 48 * 48))
            p.sendMessage(Text.literal("The Warlord's armour hisses and cracks to cold stone - strike now!").formatted(Formatting.AQUA), false);
    }

    /** Cold water crashing down + steam; shared with valves poured when no Warlord is near. */
    public static void cascadeFx(ServerWorld sw, Vec3d at) {
        for (int i = 0; i < 10; i++)
            sw.spawnParticles(ParticleTypes.FALLING_WATER, at.x + (sw.random.nextDouble() - 0.5) * 3, at.y + 5 + sw.random.nextDouble() * 3,
                    at.z + (sw.random.nextDouble() - 0.5) * 3, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.SPLASH, at.x, at.y + 0.2, at.z, 12, 1.2, 0.1, 1.2, 0.2);
        sw.spawnParticles(ParticleTypes.CLOUD, at.x, at.y + 0.5, at.z, 2, 1.0, 0.3, 1.0, 0.02);
        sw.spawnParticles(ParticleTypes.BUBBLE_POP, at.x, at.y + 0.3, at.z, 4, 1.2, 0.1, 1.2, 0.05);
    }

    // ------------------------------------------------------------------ ticking
    @Override
    protected void mobTick() {
        if (stun > 0) {
            stun--;
            this.getNavigation().stop();
            this.setVelocity(this.getVelocity().multiply(0, 1, 0));
        }
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (hintCooldown > 0) hintCooldown--;
        if (quenchTicks > 0 && --quenchTicks == 0) {
            this.dataTracker.set(QUENCHED, false);
            sw.playSound(null, getBlockPos(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.HOSTILE, 2.0f, 0.6f);
            sw.spawnParticles(ParticleTypes.LAVA, getX(), getY() + 2, getZ(), 30, 1, 1.5, 1, 0.1);
        }
        if (this.age % 20 == 0)
            for (ServerPlayerEntity p : sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(this) < 30 * 30))
                if (greeted.add(p.getUuid()))
                    p.sendMessage(Text.literal("Molten armour turns half of every blow. Open the Quench Valves in the walls and lure him under the water!")
                            .formatted(Formatting.GOLD), false);

        for (Iterator<Timed> it = cascades.iterator(); it.hasNext(); ) {
            Timed c = it.next();
            if (--c.ticks <= 0) { it.remove(); continue; }
            if (c.ticks % 2 == 0) cascadeFx(sw, c.pos);
            double dx = getX() - c.pos.x, dz = getZ() - c.pos.z;
            if (dx * dx + dz * dz < 2.8 * 2.8) quench();
        }
        tickLash(sw);
        tickWhirl(sw);
        tickFissures(sw);
        tickMeteors(sw);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!isQuenched() && source.getAttacker() instanceof ServerPlayerEntity p && hintCooldown == 0) {
            hintCooldown = 200;
            p.sendMessage(Text.literal("Your blow barely dents the molten armour. Quench him!").formatted(Formatting.GOLD), true);
        }
        return super.damage(source, amount * (isQuenched() ? 1.25f : 0.5f));
    }

    // ------------------------------------------------------------------ mace lash: telegraph line, then the chain shoots out
    void startLash(LivingEntity t) {
        if (t == null) return;
        Vec3d d = t.getPos().subtract(getPos()).multiply(1, 0, 1);
        lashDir = d.lengthSquared() < 1e-4 ? Vec3d.fromPolar(0, getYaw()) : d.normalize();
        lashTicks = 12;
        this.setYaw((float) Math.toDegrees(Math.atan2(-lashDir.x, lashDir.z)));
        this.bodyYaw = this.headYaw = this.getYaw();
    }

    private void tickLash(ServerWorld sw) {
        if (lashTicks <= 0 || lashDir == null) return;
        lashTicks--;
        Vec3d o = getPos().add(0, 0.15, 0);
        if (lashTicks % 2 == 0)
            for (double s = 1.5; s <= 11.5; s += 1.0) {
                Vec3d q = o.add(lashDir.multiply(s));
                sw.spawnParticles(new DustParticleEffect(WARN, 1.5f), q.x, q.y, q.z, 1, 0.15, 0, 0.15, 0);
            }
        if (lashTicks > 0) return;
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.0f, 0.5f);
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 2.0f, 0.6f);
        for (double s = 1; s <= 11.5; s += 0.5) {
            Vec3d q = getPos().add(lashDir.multiply(s)).add(0, 1.0, 0);
            sw.spawnParticles(ParticleTypes.FLAME, q.x, q.y, q.z, 2, 0.2, 0.3, 0.2, 0.02);
            if (s > 10) sw.spawnParticles(ParticleTypes.EXPLOSION, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
        for (LivingEntity e : Abilities.victims(this, 12.5)) {
            Vec3d rel = e.getPos().subtract(getPos()).multiply(1, 0, 1);
            double along = rel.dotProduct(lashDir);
            if (along < 0.5 || along > 12) continue;
            if (rel.subtract(lashDir.multiply(along)).length() > 1.5) continue;       // 3-wide lane
            if (e.damage(this.getDamageSources().mobAttack(this), 14f)) {
                e.setOnFireFor(4);
                double kb = 1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                e.addVelocity(lashDir.x * 1.2 * kb, 0.5 * kb, lashDir.z * 1.2 * kb);
                e.velocityModified = true;
            }
        }
        lashDir = null;
    }

    // ------------------------------------------------------------------ flail whirl: danger band 2.5..7
    void startWhirl() {
        whirlTicks = 26;
        this.playSound(SoundEvents.ITEM_TRIDENT_RIPTIDE_3, 1.5f, 0.5f);
    }

    private void tickWhirl(ServerWorld sw) {
        if (whirlTicks <= 0) return;
        whirlTicks--;
        Vec3d c = getPos().add(0, 0.2, 0);
        if (whirlTicks > 8 && whirlTicks % 3 == 0) {
            Abilities.ring(sw, c, 7.0, ParticleTypes.FLAME, 40);
            Abilities.ring(sw, c, 2.5, ParticleTypes.SMOKE, 16);
        }
        if (whirlTicks == 8 || whirlTicks == 0) {
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 2.0f, 0.4f);
            Abilities.ring(sw, c.add(0, 1, 0), 5.0, ParticleTypes.SWEEP_ATTACK, 20);
            for (LivingEntity e : Abilities.victims(this, 7.2)) {
                double d = Math.sqrt(e.squaredDistanceTo(c));
                if (d < 2.5) continue;                                                 // tucked inside the swing
                if (e.damage(this.getDamageSources().mobAttack(this), 11f)) {
                    Vec3d away = e.getPos().subtract(c).multiply(1, 0, 1).normalize();
                    double kb = 1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                    e.addVelocity(away.x * 1.1 * kb, 0.45 * kb, away.z * 1.1 * kb);
                    e.velocityModified = true;
                }
            }
        }
    }

    // ------------------------------------------------------------------ fissure slam: three burning cracks race outward
    void startFissures(LivingEntity t) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        Vec3d base = t == null ? Vec3d.fromPolar(0, getYaw()) : t.getPos().subtract(getPos()).multiply(1, 0, 1);
        base = base.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : base.normalize();
        for (int k = -1; k <= 1; k++) {
            double a = Math.toRadians(28 * k);
            Vec3d d = new Vec3d(base.x * Math.cos(a) - base.z * Math.sin(a), 0, base.x * Math.sin(a) + base.z * Math.cos(a));
            fissures.add(new Timed(getPos(), d, 0));
        }
        sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.3, getZ(), 3, 1, 0.1, 1, 0);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.5f);
    }

    private void tickFissures(ServerWorld sw) {
        for (Iterator<Timed> it = fissures.iterator(); it.hasNext(); ) {
            Timed f = it.next();
            f.ticks++;
            double dist = 1.5 + f.ticks * 0.9;
            if (dist > 15) { it.remove(); continue; }
            Vec3d q = f.pos.add(f.dir.multiply(dist));
            sw.spawnParticles(ParticleTypes.LAVA, q.x, q.y + 0.2, q.z, 2, 0.3, 0.05, 0.3, 0);
            sw.spawnParticles(ParticleTypes.FLAME, q.x, q.y + 0.3, q.z, 5, 0.4, 0.3, 0.4, 0.02);
            if (f.ticks % 3 == 0) sw.playSound(null, BlockPos.ofFloored(q), SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.HOSTILE, 1.0f, 0.6f);
            for (LivingEntity e : Abilities.victims(this, 16)) {
                if (e.getPos().squaredDistanceTo(q.x, e.getY(), q.z) > 1.3 * 1.3 || Math.abs(e.getY() - q.y) > 2) continue;
                if (e.damage(this.getDamageSources().mobAttack(this), 8f)) { e.setOnFireFor(3); e.addVelocity(0, 0.6, 0); e.velocityModified = true; }
            }
        }
    }

    // ------------------------------------------------------------------ meteor rain (phase 2)
    void startMeteors(LivingEntity t) {
        if (t == null) return;
        meteors.add(new Timed(t.getPos(), Vec3d.ZERO, 40));
        Vec3d lead = t.getPos().add(t.getVelocity().multiply(1, 0, 1).multiply(25));
        meteors.add(new Timed(lead, Vec3d.ZERO, 44));
        for (int i = 0; i < 4; i++) {
            double a = this.random.nextDouble() * Math.PI * 2, r = 3 + this.random.nextDouble() * 4;
            meteors.add(new Timed(t.getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r), Vec3d.ZERO, 40 + i * 4));
        }
        this.playSound(SoundEvents.ENTITY_BLAZE_SHOOT, 2.0f, 0.4f);
    }

    private void tickMeteors(ServerWorld sw) {
        for (Iterator<Timed> it = meteors.iterator(); it.hasNext(); ) {
            Timed m = it.next();
            m.ticks--;
            if (m.ticks % 4 == 0 && m.ticks > 0) {
                double r = 1.0 + 1.2 * m.ticks / 40.0;
                for (int i = 0; i < 14; i++) {
                    double a = Math.PI * 2 * i / 14;
                    sw.spawnParticles(new DustParticleEffect(WARN, 1.6f), m.pos.x + Math.cos(a) * r, m.pos.y + 0.1, m.pos.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            if (m.ticks > 0 && m.ticks < 12)
                sw.spawnParticles(ParticleTypes.FLAME, m.pos.x, m.pos.y + m.ticks * 1.2, m.pos.z, 6, 0.3, 0.3, 0.3, 0.01);
            if (m.ticks == 0) {
                sw.spawnParticles(ParticleTypes.EXPLOSION, m.pos.x, m.pos.y + 0.5, m.pos.z, 2, 0.5, 0.3, 0.5, 0);
                sw.spawnParticles(ParticleTypes.LAVA, m.pos.x, m.pos.y + 0.3, m.pos.z, 10, 0.6, 0.2, 0.6, 0);
                sw.playSound(null, BlockPos.ofFloored(m.pos), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.6f, 0.8f);
                for (LivingEntity e : Abilities.victims(this, 64)) {
                    if (e.getPos().squaredDistanceTo(m.pos) > 2.4 * 2.4) continue;
                    if (e.damage(this.getDamageSources().explosion(this, this), 9f)) e.setOnFireFor(4);
                }
                it.remove();
            }
        }
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Quench", quenchTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        quenchTicks = nbt.getInt("Quench");
        this.dataTracker.set(QUENCHED, nbt.getBoolean("Quenched") || quenchTicks > 0);
    }

    // =====================================================================================
    // ability effects (wired in MobSpecs)
    // =====================================================================================
    public static Abilities.Effect lash() { return (mob, t) -> { if (mob instanceof MoltenWarlordEntity w) w.startLash(t); }; }

    public static Abilities.Effect whirl() { return (mob, t) -> { if (mob instanceof MoltenWarlordEntity w) w.startWhirl(); }; }

    public static Abilities.Effect fissure() { return (mob, t) -> { if (mob instanceof MoltenWarlordEntity w) w.startFissures(t); }; }

    public static Abilities.Effect meteors() { return (mob, t) -> { if (mob instanceof MoltenWarlordEntity w) w.startMeteors(t); }; }

    /** Chain Hook: the mace wraps a player and hauls them in. */
    public static Abilities.Effect hook() {
        return (mob, t) -> {
            if (t == null) return;
            Vec3d to = mob.getPos().subtract(t.getPos());
            if (to.lengthSquared() > 4) { t.setVelocity(to.normalize().multiply(Math.min(1.8, to.length() * 0.22)).add(0, 0.35, 0)); t.velocityModified = true; }
            if (t.damage(mob.getDamageSources().mobAttack(mob), 6f)) t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 3), mob);
            Abilities.sw(mob).spawnParticles(ParticleTypes.SMOKE, t.getX(), t.getY() + 1, t.getZ(), 12, 0.3, 0.5, 0.3, 0.02);
            mob.playSound(SoundEvents.BLOCK_CHAIN_PLACE, 2.0f, 0.5f);
        };
    }

    /** Forge Call: three flame sprites, never more than four around him. */
    public static Abilities.Effect forgeCall() {
        Abilities.Effect spawn = Abilities.summon("pixelpirates:flame_sprite", 3, 4);
        return (mob, t) -> {
            var type = ModMobs.TYPES.get("flame_sprite");
            if (type != null && mob.getWorld().getEntitiesByType(type, mob.getBoundingBox().expand(24), LivingEntity::isAlive).size() >= 4) return;
            spawn.fire(mob, t);
        };
    }
}
