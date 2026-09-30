package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
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
 * BOSS 7/10 - THE KRAKEN (lair: world/dungeon/BossLairs#krakenMaw, "the Kraken's Maw").
 *
 * PHASE 1 - THE ARMS: the Kraken lurks at the bottom of the abyss in the middle of the pit, untouchable, its eyes
 * glowing up out of the dark. Six colossal arms (KrakenArmEntity) burst from the pit floor around it: slams, sweeps,
 * grabs that hurl you toward the abyss. The boss bar tracks the arms. Diving into the abyss gets you bitten.
 * THE RISE: when the last arm sinks, the Kraken surfaces out of the abyss (3 s) with a roar.
 * PHASE 2 - THE HEAD: the arms regrow twice (at 66% and 33% - 2 each, 3 enraged); while any arm lives the Kraken
 * takes half damage. SWALLOW: it gapes and inhales for 3 s, dragging every player toward its maw (the Royal Tide
 * Sigil halves the pull); one that reaches the beak is swallowed - 2.5 s inside, taking damage but dealing TRIPLE
 * damage to it from within - then SPAT up out of the sea like a cork, and a feeding tentacle erupts from the water
 * where you come down to swat you back. HARPOON SPIT: a barbed harpoon out of the maw (KrakenHarpoonEntity) runs
 * you through and carries you up to 20 blocks - into a wall and you are pinned there 5 s (3 harpoons enraged).
 * TENTACLE BULWARK: it cages itself in its arms for 2.6 s (x0.1 damage, pressure building), then BURSTS them outward -
 * a shockwave that hurls everyone within 16 blocks away. Also Beak Crush and Quake (every arm slams the floor: a
 * shockwave ring rolls out along the bottom - swim up over it).
 * Enraged "Abyssal Wrath" at 50% (ember-veined skin): Maelstrom - it spins and the whole pit becomes a whirlpool (7 s).
 */
public class KrakenEntity extends ModBoss {
    private static final TrackedData<Integer> PHASE = DataTracker.registerData(KrakenEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final int ARMS = 0, RISING = 1, HEAD = 2;
    public static final int ARM_COUNT = 6, RISE_TICKS = 60;
    private static final Vector3f WARN = new Vector3f(0.85f, 0.1f, 0.1f);
    private static final Vector3f INKY = new Vector3f(0.08f, 0.06f, 0.12f);

    private final List<UUID> arms = new ArrayList<>();
    private final List<BlockPos> armSpots = new ArrayList<>();
    private boolean armsSpawned, regrew66, regrew33;
    private double riseFrom, riseTo = Double.NaN;
    private int riseTicks, armCheck, hintCooldown, biteCooldown;
    private float armMaxTotal, armHp;
    // swallow -> spit -> swat
    private int inhaleTicks;
    private LivingEntity held; private int heldTicks;
    private LivingEntity jetter; private int jetTicks, jetGuard;
    private LivingEntity swatTarget; private Vec3d swatAt; private int swatTicks;
    // quake + maelstrom
    private double quakeR = -1; private Vec3d quakeC; private final Set<UUID> quakeHit = new HashSet<>();
    private int whirlTicks, shieldTicks;
    public static final int WHIRL_TICKS = 140;                                          // 7 s of whirlpool (was 2.5)
    private final Set<UUID> greeted = new HashSet<>();

    public KrakenEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PHASE, ARMS);
    }

    public int phase() { return this.dataTracker.get(PHASE); }

    @Override
    protected List<String> extraAnims() { return List.of("rise", "spit", "swat", "hurt_arm"); }

    @Override
    protected boolean scriptedMotion() { return true; }        // rooted: never pushed, never sinks; it moves only when it rises

    /** Worldgen (before the spawn): where the arms burst out, and the height it rises to. */
    public void setLair(List<BlockPos> spots, double riseToY) {
        armSpots.clear();
        for (BlockPos p : spots) armSpots.add(p.toImmutable());
        riseTo = riseToY;
    }

    private Vec3d mouth() {
        Vec3d f = Vec3d.fromPolar(0, this.bodyYaw);
        return getPos().add(f.multiply(2.4)).add(0, 1.3, 0);
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
        Vec3d m = mouth().add(Vec3d.fromPolar(0, this.bodyYaw).multiply(-0.8));
        positionUpdater.accept(passenger, m.x, m.y - 0.6, m.z);
    }

    /** Vanilla drops a dismounting passenger on TOP of the vehicle - out of the maw instead. */
    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) { return mouth().add(Vec3d.fromPolar(0, this.bodyYaw).multiply(1.5)); }

    // ------------------------------------------------------------------ abilities availability
    @Override
    protected List<Abilities.Ability> activeAbilities() {
        if (phase() != HEAD || inhaleTicks > 0 || held != null || whirlTicks > 0 || shieldTicks > 0) return List.of();
        return super.activeAbilities();
    }

    // ------------------------------------------------------------------ phase 1: the arms
    private void spawnArms(ServerWorld sw, int count, boolean regrowth) {
        List<BlockPos> spots = new ArrayList<>(armSpots);
        if (spots.isEmpty())
            for (int i = 0; i < ARM_COUNT; i++) {
                double a = Math.PI * 2 * i / ARM_COUNT;
                spots.add(BlockPos.ofFloored(getX() + Math.cos(a) * 9, getY(), getZ() + Math.sin(a) * 9));
            }
        java.util.Collections.shuffle(spots, new java.util.Random(this.random.nextLong()));
        var type = ModMobs.TYPES.get("kraken_arm");
        if (type == null) return;
        int made = 0;
        for (BlockPos p : spots) {
            if (made >= count) break;
            Entity e = type.create(sw);
            if (!(e instanceof KrakenArmEntity arm)) continue;
            arm.refreshPositionAndAngles(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, this.random.nextFloat() * 360, 0);
            arm.initialize(sw, sw.getLocalDifficulty(p), SpawnReason.MOB_SUMMONED, null, null);
            arm.setKraken(this.getUuid());
            sw.spawnEntity(arm);
            arms.add(arm.getUuid());
            made++;
        }
        armMaxTotal = arms.size() * (float) spec().health;       // replaced below by the arms' real max health
        float t = 0;
        for (UUID u : arms) if (sw.getEntity(u) instanceof LivingEntity le) t += le.getMaxHealth();
        if (t > 0) armMaxTotal = t;
        if (regrowth) {
            triggerAnim(ACTION, "hurt_arm");
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_EMERGE, SoundCategory.HOSTILE, 3.0f, 0.5f);
            broadcast(sw, Text.literal("New arms tear up out of the seabed! While they live, the Kraken is shielded.").formatted(Formatting.DARK_PURPLE), false);
        }
    }

    /** Living arms (arms in unloaded chunks count as alive, so the fight can't skip ahead). */
    private int livingArms(ServerWorld sw, float[] hp) {
        int n = 0;
        for (Iterator<UUID> it = arms.iterator(); it.hasNext(); ) {
            UUID u = it.next();
            Entity e = sw.getEntity(u);
            if (e instanceof LivingEntity le && le.isAlive()) { n++; hp[0] += le.getHealth(); }
            else if (e == null && !sw.isChunkLoaded(getBlockPos())) n++;
            else it.remove();
        }
        return n;
    }

    private void startRise(ServerWorld sw) {
        this.dataTracker.set(PHASE, RISING);
        riseTicks = RISE_TICKS;
        riseFrom = getY();
        if (Double.isNaN(riseTo)) riseTo = getY();
        triggerAnim(ACTION, "rise");
        bossBar().setName(this.getDisplayName());
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 3.0f, 0.4f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 3.0f, 0.5f);
        broadcast(sw, Text.literal("The last arm sinks away... and something VAST rises out of the abyss.").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), false);
    }

    private void tickRise(ServerWorld sw) {
        if (phase() != RISING) return;
        riseTicks--;
        double t = 1 - riseTicks / (double) RISE_TICKS;
        this.setPosition(getX(), riseFrom + (riseTo - riseFrom) * (t * t * (3 - 2 * t)), getZ());
        sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), getY() + 2, getZ(), 30, 3, 2, 3, 0.4);
        sw.spawnParticles(ParticleTypes.SQUID_INK, getX(), getY() + 3, getZ(), 12, 3, 2, 3, 0.05);
        if (riseTicks % 20 == 0) sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_BASALT_BREAK, SoundCategory.HOSTILE, 3.0f, 0.3f);
        if (riseTicks > 0) return;
        this.dataTracker.set(PHASE, HEAD);
        cooldowns.clear();
        for (ServerPlayerEntity p : nearbyPlayers(sw, 40))
            p.sendMessage(Text.literal("THE KRAKEN").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
    }

    // ------------------------------------------------------------------ SWALLOW -> spit -> swat
    void startInhale() {
        inhaleTicks = 60;
        this.playSound(SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, 3.0f, 0.4f);
        this.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, 3.0f, 0.4f);
    }

    private void tickInhale(ServerWorld sw) {
        if (inhaleTicks <= 0) return;
        inhaleTicks--;
        Vec3d m = mouth();
        // streams of water rushing into the maw
        for (int i = 0; i < 6; i++) {
            double a = this.random.nextDouble() * Math.PI * 2, r = 4 + this.random.nextDouble() * 12;
            Vec3d from = m.add(Math.cos(a) * r, (this.random.nextDouble() - 0.3) * 5, Math.sin(a) * r);
            Vec3d v = m.subtract(from).normalize().multiply(0.9);
            sw.spawnParticles(ParticleTypes.BUBBLE, from.x, from.y, from.z, 0, v.x, v.y, v.z, 1.0);
        }
        for (ServerPlayerEntity p : nearbyPlayers(sw, 20)) {
            if (p.isCreative() || !mayTarget(p) || p.hasVehicle()) continue;
            Vec3d to = m.subtract(p.getPos().add(0, 0.9, 0));
            double d = to.length();
            if (d < 2.8 && held == null) { swallow(sw, p); break; }
            double pull = (0.06 + 0.06 * (1 - d / 20)) * (BossProgression.relicActive(p, ModItems.ROYAL_TIDE_SIGIL) ? 0.5 : 1) * net.get900.pixelpirates.item.BossArmor.pullScale(p);
            p.addVelocity(to.normalize().multiply(pull));
            p.velocityModified = true;
        }
    }

    private void swallow(ServerWorld sw, LivingEntity p) {
        inhaleTicks = 0;
        held = p;
        heldTicks = 50;
        p.startRiding(this, true);
        triggerAnim(ACTION, "attack");
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.HOSTILE, 3.0f, 0.3f);
        if (p instanceof ServerPlayerEntity sp)
            sp.sendMessage(Text.literal("SWALLOWED! Hack at it from within - every blow lands three times as hard!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
    }

    private void tickHeld(ServerWorld sw) {
        if (held == null) return;
        if (!held.isAlive() || held.isRemoved()) { held = null; return; }
        if (!held.hasVehicle() && held.squaredDistanceTo(this) < 100 && !net.get900.pixelpirates.item.BossArmor.slipped(held)) held.startRiding(this, true);
        if (heldTicks % 10 == 0) {
            held.damage(this.getDamageSources().mobAttack(this), 2f);
            held.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 40, 0), this);
            sw.spawnParticles(ParticleTypes.SQUID_INK, held.getX(), held.getY() + 1, held.getZ(), 10, 0.4, 0.4, 0.4, 0.02);
        }
        if (--heldTicks > 0) return;
        // SPIT: up out of the sea like a cork
        LivingEntity v = held;
        held = null;
        v.stopRiding();
        triggerAnim(ACTION, "spit");
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 3.0f, 0.4f);
        sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, v.getX(), v.getY(), v.getZ(), 80, 0.6, 1, 0.6, 0.6);
        jetter = v;
        jetTicks = 8;                                                    // air ticks once it breaks the surface
        jetGuard = 60;
    }

    private void tickJet(ServerWorld sw) {
        if (jetter == null) return;
        if (!jetter.isAlive()) { jetter = null; return; }
        Vec3d away = Vec3d.fromPolar(0, bodyYaw).multiply(0.25);
        jetter.setVelocity(away.x, jetter.isTouchingWater() ? 1.2 : Math.max(jetter.getVelocity().y, 0.9), away.z);
        jetter.velocityModified = true;
        jetter.fallDistance = 0;
        sw.spawnParticles(ParticleTypes.BUBBLE, jetter.getX(), jetter.getY(), jetter.getZ(), 6, 0.3, 0.3, 0.3, 0.1);
        // keep blasting up until it clears the sea, then a few ticks of flight
        if (--jetGuard > 0 && (jetter.isTouchingWater() || --jetTicks > 0)) return;
        swatTarget = jetter;
        jetter = null;
        swatTicks = 16;                                                 // then a tentacle erupts from the sea to swat them
    }

    private void tickSwat(ServerWorld sw) {
        if (swatTarget == null) return;
        if (!swatTarget.isAlive()) { swatTarget = null; return; }
        swatTicks--;
        if (swatTicks == 10) {
            swatAt = swatTarget.getPos();
            triggerAnim(ACTION, "swat");
        }
        if (swatAt != null && swatTicks > 0 && swatTicks % 2 == 0) {
            for (int i = 0; i < 16; i++) {
                double a = Math.PI * 2 * i / 16;
                sw.spawnParticles(new DustParticleEffect(WARN, 1.8f), swatAt.x + Math.cos(a) * 2.5, swatAt.y + 0.5, swatAt.z + Math.sin(a) * 2.5, 1, 0, 0, 0, 0);
            }
        }
        if (swatTicks > 0) return;
        Vec3d at = swatAt != null ? swatAt : swatTarget.getPos();
        // the tentacle bursts up through the surface to the strike point
        double surface = at.y;
        for (int k = 0; k < 24 && sw.getFluidState(BlockPos.ofFloored(at.x, surface - 1, at.z)).isEmpty(); k++) surface--;
        for (double y = surface - 6; y <= at.y + 1; y += 0.5) {
            sw.spawnParticles(new DustParticleEffect(INKY, 3.0f), at.x, y, at.z, 3, 0.5, 0.1, 0.5, 0);
            sw.spawnParticles(ParticleTypes.SPLASH, at.x, y, at.z, 2, 0.6, 0.1, 0.6, 0.1);
        }
        sw.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, SoundCategory.HOSTILE, 3.0f, 0.4f);
        sw.playSound(null, BlockPos.ofFloored(at), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.5f, 0.8f);
        if (swatTarget.squaredDistanceTo(at) < 3.5 * 3.5 && swatTarget.damage(this.getDamageSources().mobAttack(this), isEnraged() ? 18f : 14f)) {
            Vec3d back = getPos().subtract(swatTarget.getPos()).multiply(1, 0, 1);
            back = back.lengthSquared() < 1e-4 ? Vec3d.ZERO : back.normalize().multiply(0.8);
            swatTarget.setVelocity(back.x, -1.4, back.z);
            swatTarget.velocityModified = true;
        }
        swatTarget = null;
        swatAt = null;
    }

    // ------------------------------------------------------------------ QUAKE: a shockwave rolls out along the floor
    void startQuake() {
        quakeR = 2;
        quakeC = getPos();
        quakeHit.clear();
    }

    private void tickQuake(ServerWorld sw) {
        if (quakeR < 0) return;
        if (quakeR == 2) sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 3.0f, 0.4f);
        quakeR += 0.7;
        if (quakeR > 22) { quakeR = -1; return; }
        int n = (int) (quakeR * 5);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double x = quakeC.x + Math.cos(a) * quakeR, z = quakeC.z + Math.sin(a) * quakeR;
            double y = floorAt(sw, x, quakeC.y, z);
            sw.spawnParticles(ParticleTypes.SQUID_INK, x, y + 0.3, z, 1, 0.1, 0.2, 0.1, 0.01);
            if (i % 3 == 0) sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, x, y + 0.2, z, 1, 0, 0.3, 0, 0.1);
        }
        for (LivingEntity e : Abilities.victims(this, 23)) {
            double dx = e.getX() - quakeC.x, dz = e.getZ() - quakeC.z, d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - quakeR) > 1.0) continue;
            double floor = floorAt(sw, e.getX(), quakeC.y, e.getZ());
            if (e.getY() - floor > 2.2 || !quakeHit.add(e.getUuid())) continue;      // swim up over the wave
            if (e.damage(this.getDamageSources().mobAttack(this), 12f)) { e.addVelocity(dx / d * 0.6, 1.0, dz / d * 0.6); e.velocityModified = true; }
        }
    }

    /** The top of the solid floor below (x, z), searching down from around y. */
    private static double floorAt(ServerWorld sw, double x, double y, double z) {
        BlockPos.Mutable p = BlockPos.ofFloored(x, y + 6, z).mutableCopy();
        for (int i = 0; i < 20; i++) {
            if (!sw.getBlockState(p).getCollisionShape(sw, p).isEmpty()) return p.getY() + 1;
            p.move(0, -1, 0);
        }
        return y;
    }

    // ------------------------------------------------------------------ HARPOON SPIT
    /** A barbed harpoon out of the maw at the target's lead point (3 in a fan when enraged). */
    void spitHarpoon(LivingEntity t) {
        if (t == null || !(this.getWorld() instanceof ServerWorld sw)) return;
        Vec3d from = mouth().add(Vec3d.fromPolar(0, this.bodyYaw).multiply(1.2));
        Vec3d aim = t.getPos().add(0, t.getHeight() * 0.5, 0);
        double time = aim.distanceTo(from) / net.get900.pixelpirates.entity.custom.KrakenHarpoonEntity.SPEED;
        aim = aim.add(t.getVelocity().multiply(1, 0.5, 1).multiply(time * 0.6));
        Vec3d dir = aim.subtract(from).normalize();
        int n = isEnraged() ? 3 : 1;
        for (int i = 0; i < n; i++) {
            double a = Math.toRadians((i - (n - 1) / 2.0) * 11);
            Vec3d d = new Vec3d(dir.x * Math.cos(a) - dir.z * Math.sin(a), dir.y, dir.x * Math.sin(a) + dir.z * Math.cos(a));
            sw.spawnEntity(net.get900.pixelpirates.entity.custom.KrakenHarpoonEntity.spit(sw, this, from, d, isEnraged() ? 10f : 8f));
        }
        sw.playSound(null, getBlockPos(), SoundEvents.ITEM_CROSSBOW_SHOOT, SoundCategory.HOSTILE, 3.0f, 0.4f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 2.0f, 0.5f);
        sw.spawnParticles(ParticleTypes.BUBBLE_POP, from.x, from.y, from.z, 30, 0.5, 0.5, 0.5, 0.2);
    }

    // ------------------------------------------------------------------ TENTACLE BULWARK -> BURST
    public void startBulwark() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        shieldTicks = 52;
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 3.0f, 0.4f);
        broadcast(sw, Text.literal("The Kraken coils its arms around itself... GET BACK!").formatted(Formatting.DARK_PURPLE, Formatting.BOLD), true);
    }

    private void tickBulwark(ServerWorld sw) {
        if (shieldTicks <= 0) return;
        shieldTicks--;
        if (shieldTicks % 3 == 0) {                                               // water drawn in, pressure building
            double r = 3 + shieldTicks * 0.2;
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12 + shieldTicks * 0.1;
                sw.spawnParticles(ParticleTypes.BUBBLE, getX() + Math.cos(a) * r, getY() + 1 + (i % 4), getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
        }
        if (shieldTicks % 10 == 0 && shieldTicks > 0)
            sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CONDUIT_AMBIENT_SHORT, SoundCategory.HOSTILE, 2.5f, 0.4f + (52 - shieldTicks) * 0.02f);
        if (shieldTicks > 0) return;
        // BURST
        triggerAnim(ACTION, "burst");
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 3.5f, 0.4f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0f, 0.6f);
        for (int ring = 2; ring <= 16; ring += 2) {
            int n = ring * 4;
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                sw.spawnParticles(ring % 4 == 0 ? ParticleTypes.SQUID_INK : ParticleTypes.BUBBLE_POP,
                        getX() + Math.cos(a) * ring, getY() + 1.5, getZ() + Math.sin(a) * ring, 1, 0, 0.3, 0, 0.05);
            }
        }
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 2, getZ(), 1, 0, 0, 0, 0);
        for (LivingEntity e : Abilities.victims(this, 16)) {
            Vec3d away = e.getPos().subtract(getPos()).multiply(1, 0, 1);
            double d = away.length();
            away = d < 1e-3 ? Vec3d.fromPolar(0, bodyYaw) : away.normalize();
            double f = Math.max(0, 1 - d / 16);
            e.damage(this.getDamageSources().mobAttack(this), (float) (5 + 13 * f));
            double kb = 1.0 - e.getAttributeValue(net.minecraft.entity.attribute.EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
            e.setVelocity(away.x * (1.0 + 1.8 * f) * kb, 0.5 + 0.5 * f, away.z * (1.0 + 1.8 * f) * kb);
            e.velocityModified = true;
        }
    }

    // ------------------------------------------------------------------ MAELSTROM (enraged)
    void startWhirl() {
        whirlTicks = WHIRL_TICKS;
        this.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 2.5f, 0.4f);
    }

    private void tickWhirl(ServerWorld sw) {
        if (whirlTicks <= 0) return;
        whirlTicks--;
        if (whirlTicks > 0 && whirlTicks % 50 == 0) triggerAnim(ACTION, "whirl");         // keep spinning
        for (int i = 0; i < 4; i++) {
            double a = this.age * 0.3 + i * Math.PI / 2, r = 4 + (this.age * 0.6 + i * 4) % 16;
            sw.spawnParticles(ParticleTypes.BUBBLE, getX() + Math.cos(a) * r, getY() + 1 + i, getZ() + Math.sin(a) * r, 3, 0.3, 0.3, 0.3, 0);
            sw.spawnParticles(ParticleTypes.SQUID_INK, getX() + Math.cos(a) * r, getY() + 2, getZ() + Math.sin(a) * r, 1, 0.2, 0.2, 0.2, 0);
        }
        for (ServerPlayerEntity p : nearbyPlayers(sw, 24)) {
            if (p.isCreative() || !mayTarget(p)) continue;
            Vec3d to = getPos().subtract(p.getPos()).multiply(1, 0, 1);
            double d = to.length();
            if (d < 0.5) continue;
            Vec3d in = to.normalize(), tan = new Vec3d(-in.z, 0, in.x);
            double k = (BossProgression.relicActive(p, ModItems.ROYAL_TIDE_SIGIL) ? 0.5 : 1) * net.get900.pixelpirates.item.BossArmor.pullScale(p);
            p.addVelocity(in.x * 0.05 * k + tan.x * 0.09, 0, in.z * 0.05 * k + tan.z * 0.09);
            p.velocityModified = true;
            if (d < 5 && whirlTicks % 10 == 0) p.damage(this.getDamageSources().mobAttack(this), 4f);
        }
    }

    // ------------------------------------------------------------------ ticking
    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (hintCooldown > 0) hintCooldown--;
        if (biteCooldown > 0) biteCooldown--;
        if (phase() == ARMS) {
            if (!armsSpawned && this.age > 20) {
                armsSpawned = true;
                spawnArms(sw, ARM_COUNT, false);
            }
            if (armsSpawned && ++armCheck % 10 == 0) {
                float[] hp = {0};
                int n = livingArms(sw, hp);
                armHp = hp[0];
                bossBar().setName(Text.literal("The Kraken's Arms (" + n + ")").formatted(Formatting.DARK_PURPLE));
                if (n == 0) startRise(sw);
            }
            if (phase() == ARMS) bossBar().setPercent(armMaxTotal > 0 ? Math.min(1f, armHp / armMaxTotal) : 1f);   // ModBoss sets health every tick
            // it watches from the abyss: glowing eyes, the odd groan, and a bite for anyone who dives down to it
            if (this.age % 200 == 0) sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.HOSTILE, 3.0f, 0.3f);
            if (biteCooldown == 0)
                for (LivingEntity e : Abilities.victims(this, 6)) {
                    biteCooldown = 30;
                    triggerAnim(ACTION, "attack");
                    e.damage(this.getDamageSources().mobAttack(this), 12f);
                    e.addVelocity(0, 1.0, 0);
                    e.velocityModified = true;
                    if (e instanceof ServerPlayerEntity p) p.sendMessage(Text.literal("The beak snaps out of the dark - kill the arms first!").formatted(Formatting.DARK_RED), true);
                    break;
                }
        } else if (phase() == HEAD) {
            // regrowth at 66% / 33%
            float f = getHealth() / getMaxHealth();
            if (!regrew66 && f < 0.66f) { regrew66 = true; spawnArms(sw, isEnraged() ? 3 : 2, true); }
            if (!regrew33 && f < 0.33f) { regrew33 = true; spawnArms(sw, 3, true); }
            if (!arms.isEmpty() && this.age % 10 == 0) livingArms(sw, new float[]{0});
            LivingEntity t = getTarget();
            if (t != null && held == null && inhaleTicks == 0 && whirlTicks == 0) {                 // turn its maw toward the prey
                float want = (float) Math.toDegrees(Math.atan2(-(t.getX() - getX()), t.getZ() - getZ()));
                float yaw = net.minecraft.util.math.MathHelper.stepUnwrappedAngleTowards(this.bodyYaw, want, 4f);
                this.setYaw(yaw);
                this.bodyYaw = this.headYaw = yaw;
            }
        }
        tickRise(sw);
        tickInhale(sw);
        tickHeld(sw);
        tickJet(sw);
        tickSwat(sw);
        tickQuake(sw);
        tickWhirl(sw);
        tickBulwark(sw);
        if (this.age % 20 == 0)
            for (ServerPlayerEntity p : nearbyPlayers(sw, 36))
                if (greeted.add(p.getUuid()))
                    p.sendMessage(Text.literal("Something enormous stirs in the abyss below. Its arms guard it - sever them all to draw it out.")
                            .formatted(Formatting.DARK_PURPLE), false);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (phase() != HEAD) {
            if (source.getAttacker() instanceof ServerPlayerEntity p && hintCooldown == 0) {
                hintCooldown = 100;
                p.sendMessage(Text.literal("It is buried too deep - sever its arms first!").formatted(Formatting.DARK_PURPLE), true);
            }
            return source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD) && super.damage(source, amount);
        }
        float mult = 1f;
        if (held != null && source.getAttacker() == held) mult *= 3f;                 // struck from within
        if (shieldTicks > 0) {
            mult *= 0.1f;                                                             // blows glance off the coiled arms
            if (this.getWorld() instanceof ServerWorld sw)
                sw.spawnParticles(ParticleTypes.CRIT, getX(), getY() + 3, getZ(), 8, 1.5, 1.5, 1.5, 0.2);
        }
        if (!arms.isEmpty()) {
            mult *= 0.5f;
            if (source.getAttacker() instanceof ServerPlayerEntity p && hintCooldown == 0) {
                hintCooldown = 160;
                p.sendMessage(Text.literal("Its arms shield it - sever them!").formatted(Formatting.DARK_PURPLE), true);
            }
        }
        return super.damage(source, amount * mult);
    }

    @Override
    protected void onEnrage() {
        if (this.getWorld() instanceof ServerWorld sw)
            broadcast(sw, Text.literal("The Kraken's veins burn like embers - ABYSSAL WRATH!").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
    }

    @Override
    public void onDeath(DamageSource source) {
        if (this.getWorld() instanceof ServerWorld sw) {
            if (held != null) { held.stopRiding(); held = null; }
            for (UUID u : arms) if (sw.getEntity(u) instanceof LivingEntity le) le.kill();
        }
        super.onDeath(source);
    }

    private List<ServerPlayerEntity> nearbyPlayers(ServerWorld sw, double r) {
        return sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(this) < r * r);
    }

    private void broadcast(ServerWorld sw, Text t, boolean actionbar) {
        for (ServerPlayerEntity p : nearbyPlayers(sw, 48)) p.sendMessage(t, actionbar);
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Phase", phase());
        nbt.putBoolean("ArmsSpawned", armsSpawned);
        nbt.putBoolean("Regrew66", regrew66);
        nbt.putBoolean("Regrew33", regrew33);
        nbt.putFloat("ArmMax", armMaxTotal);
        if (!Double.isNaN(riseTo)) nbt.putDouble("RiseTo", riseTo);
        NbtList a = new NbtList();
        for (UUID u : arms) a.add(NbtHelper.fromUuid(u));
        nbt.put("Arms", a);
        NbtList s = new NbtList();
        for (BlockPos p : armSpots) s.add(NbtHelper.fromBlockPos(p));
        nbt.put("ArmSpots", s);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        int ph = nbt.getInt("Phase");
        this.dataTracker.set(PHASE, ph == RISING ? HEAD : ph);             // a reload mid-rise just finishes it
        armsSpawned = nbt.getBoolean("ArmsSpawned");
        regrew66 = nbt.getBoolean("Regrew66");
        regrew33 = nbt.getBoolean("Regrew33");
        armMaxTotal = nbt.getFloat("ArmMax");
        if (nbt.contains("RiseTo")) riseTo = nbt.getDouble("RiseTo");
        if (ph == RISING && !Double.isNaN(riseTo)) this.setPosition(getX(), riseTo, getZ());
        arms.clear();
        for (NbtElement e : nbt.getList("Arms", NbtElement.INT_ARRAY_TYPE)) arms.add(NbtHelper.toUuid(e));
        armSpots.clear();
        for (NbtElement e : nbt.getList("ArmSpots", NbtElement.COMPOUND_TYPE)) armSpots.add(NbtHelper.toBlockPos((NbtCompound) e));
    }

    // ------------------------------------------------------------------ /ppboss kraken (testing)
    public String debugStatus() {
        return "Kraken at " + getBlockPos().toShortString() + ": phase " + switch (phase()) { case ARMS -> "ARMS"; case RISING -> "RISING"; default -> "HEAD"; }
                + ", hp " + (int) getHealth() + "/" + (int) getMaxHealth() + ", arms " + arms.size()
                + (held != null ? ", holding " + held.getName().getString() : "") + (jetter != null ? ", spitting" : "")
                + (inhaleTicks > 0 ? ", inhaling" : "") + (isEnraged() ? ", ABYSSAL WRATH" : "");
    }

    public void debugKillArms() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        for (UUID u : List.copyOf(arms)) if (sw.getEntity(u) instanceof LivingEntity le) le.kill();
    }

    /** Spit a harpoon at the nearest non-mob creature (tests the impale/carry/pin without a player). */
    public void debugHarpoon() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        LivingEntity pick = null;
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(30), e -> e != this && !(e instanceof ModMob) && e.isAlive()))
            if (pick == null || e.squaredDistanceTo(this) < pick.squaredDistanceTo(this)) pick = e;
        if (pick != null) spitHarpoon(pick);
    }

    public void debugSwallow() {
        if (!(this.getWorld() instanceof ServerWorld sw) || phase() != HEAD) return;
        LivingEntity pick = null;
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(20), e -> e != this && !(e instanceof ModMob) && e.isAlive()))
            if (pick == null || e.squaredDistanceTo(this) < pick.squaredDistanceTo(this)) pick = e;
        if (pick != null) { Vec3d m = mouth(); pick.refreshPositionAndAngles(m.x, m.y, m.z, 0, 0); swallow(sw, pick); }
    }

    // =====================================================================================
    // ability effects (wired in MobSpecs)
    // =====================================================================================
    public static Abilities.Effect inhale() { return (mob, t) -> { if (mob instanceof KrakenEntity k) k.startInhale(); }; }

    public static Abilities.Effect quake() { return (mob, t) -> { if (mob instanceof KrakenEntity k) k.startQuake(); }; }

    public static Abilities.Effect harpoonSpit() { return (mob, t) -> { if (mob instanceof KrakenEntity k) k.spitHarpoon(t); }; }

    public static Abilities.Effect bulwark() { return (mob, t) -> { if (mob instanceof KrakenEntity k) k.startBulwark(); }; }

    public static Abilities.Effect whirl() { return (mob, t) -> { if (mob instanceof KrakenEntity k) k.startWhirl(); }; }
}
