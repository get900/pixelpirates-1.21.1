package net.get900.pixelpirates.homestead.parrot;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.get900.pixelpirates.homestead.HomesteadState;
import net.get900.pixelpirates.mixin.PlayerShoulderInvoker;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.ElderGuardianEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.WardenEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * PARROT TYPES phase 3 (2026-10-03, docs/parrot_ideas.md): the big abilities, from your ACTIVE parrot (ParrotCompanion.
 * activeType: a shoulder, else the nearest one following you - one at a time).
 *   KRAKEN'S PET - cheats a killing blow (not the void or /kill; a totem in hand goes first): half health + regeneration
 *     + absorption, and the parrot dives off for 10 minutes, then comes back to your shoulder (or lands by you).
 *   KAKAPO - pecks whatever you're fighting (your last target, or a monster after you) within 6 blocks, every 1.5 s.
 *   EMBER MACAW - every 4 s, a fire charge at a monster after you / your target within 16 that it can see; the charges
 *     never set blocks alight (the port is made of wood).
 *   GHOST PARROT (reworked 2026-10-03) - POSSESSES a lesser monster near you (no bosses, 60 health or less; vanilla monsters + our MONSTER-group mobs): the parrot
 *     leaves you, the mob can no longer target players and fights the monsters round you instead (follows you when
 *     there are none). When the body dies - or is lost (unloaded, 48+ blocks away, you log out) - the ghost drifts off
 *     for 10 minutes, then comes back.
 * Parrots that are away live in HomesteadState.parrotsAway: player -> {type id -> {Parrot: its data, Back: game time,
 * Possessing: the host's uuid while it possesses}} - kept through logging out.
 */
public final class ParrotAbilities {
    private ParrotAbilities() {}

    private static final long AWAY_TICKS = 12000;
    /** Possessed mob -> its ghost's player (rebuilt from parrotsAway every half second, so a restart loses nothing).
     *  MobPossessionMixin stops these mobs targeting players. */
    public static final Map<UUID, UUID> POSSESSED = new ConcurrentHashMap<>();

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (!(entity instanceof ServerPlayerEntity p) || !ParrotCompanion.active(p, "krakens_pet")) return true;
            if (source.isOf(DamageTypes.OUT_OF_WORLD) || source.isOf(DamageTypes.GENERIC_KILL)) return true;
            if (p.getMainHandStack().isOf(Items.TOTEM_OF_UNDYING) || p.getOffHandStack().isOf(Items.TOTEM_OF_UNDYING)) return true;
            krakenSave(p);
            return false;
        });
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            UUID owner = POSSESSED.get(entity.getUuid());
            if (owner == null) return;
            ServerPlayerEntity p = entity.getServer().getPlayerManager().getPlayer(owner);
            endPossession(entity.getServer(), owner, p, entity.getDisplayName().getString(), "falls - the ghost slips out and drifts away");
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long t = server.getTicks();
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                if (p.isSpectator() || !p.isAlive()) continue;
                if (t % 10 == 7) awayTick(server, p);
                if (t % 30 != 11 && t % 80 != 23 && t % 60 != 15) continue;
                String act = ParrotCompanion.activeType(p);
                if (act == null) continue;
                if (t % 30 == 11 && act.equals("kakapo")) kakapo(p);
                if (t % 80 == 23 && act.equals("ember_macaw")) ember(p);
                if (t % 60 == 15 && act.equals("ghost_parrot")) possess(p);
            }
        });
    }

    // ------------------------------------------------------------------ parrots away (one entry per type)
    public static NbtCompound away(MinecraftServer server, UUID u, String type) {
        NbtCompound all = HomesteadState.get(server).parrotsAway().get(u);
        return all == null || !all.contains(type) ? null : all.getCompound(type);
    }

    private static void setAway(MinecraftServer server, UUID u, String type, NbtCompound c) {
        HomesteadState st = HomesteadState.get(server);
        st.parrotsAway().computeIfAbsent(u, k -> new NbtCompound()).put(type, c);
        st.touch();
    }

    private static void clearAway(MinecraftServer server, UUID u, String type) {
        HomesteadState st = HomesteadState.get(server);
        NbtCompound all = st.parrotsAway().get(u);
        if (all == null) return;
        all.remove(type);
        if (all.isEmpty()) st.parrotsAway().remove(u);
        st.touch();
    }

    /** Take the player's active parrot of this type off its shoulder (or out of the world if it's following): its data. */
    private static NbtCompound takeBird(ServerPlayerEntity p, String type) {
        PlayerShoulderInvoker inv = (PlayerShoulderInvoker) p;
        if (type.equals(ParrotCollection.type(p.getShoulderEntityLeft()))) {
            NbtCompound b = p.getShoulderEntityLeft().copy();
            inv.pixelpirates$setShoulderLeft(new NbtCompound());
            return b;
        }
        if (type.equals(ParrotCollection.type(p.getShoulderEntityRight()))) {
            NbtCompound b = p.getShoulderEntityRight().copy();
            inv.pixelpirates$setShoulderRight(new NbtCompound());
            return b;
        }
        var f = ParrotCompanion.follower(p);
        NbtCompound b = new NbtCompound();
        if (f == null || !type.equals(ParrotTypes.of(f).id()) || !f.saveSelfNbt(b)) return null;
        f.discard();
        return b;
    }

    /** Every half second: steer possessed hosts, notice lost ones, bring back parrots whose time is up. */
    private static void awayTick(MinecraftServer server, ServerPlayerEntity p) {
        NbtCompound all = HomesteadState.get(server).parrotsAway().get(p.getUuid());
        if (all == null) return;
        long now = server.getOverworld().getTime();
        for (String type : java.util.List.copyOf(all.getKeys())) {
            NbtCompound a = all.getCompound(type);
            if (a.containsUuid("Possessing")) { steer(server, p, a.getUuid("Possessing")); continue; }
            if (now >= a.getLong("Back")) comeBack(p, type, a.getCompound("Parrot"));
        }
    }

    private static void comeBack(ServerPlayerEntity p, String type, NbtCompound bird) {
        PlayerShoulderInvoker inv = (PlayerShoulderInvoker) p;
        if (p.getShoulderEntityLeft().isEmpty()) inv.pixelpirates$setShoulderLeft(bird);
        else if (p.getShoulderEntityRight().isEmpty()) inv.pixelpirates$setShoulderRight(bird);
        else EntityType.getEntityFromNbt(bird, p.getServerWorld()).ifPresent(e -> {     // both shoulders taken: it lands by you
            e.refreshPositionAndAngles(p.getX(), p.getY() + 1, p.getZ(), p.getYaw(), 0f);
            p.getServerWorld().spawnEntity(e);
        });
        clearAway(p.getServer(), p.getUuid(), type);
        ParrotTypes.PType t = ParrotTypes.byId(type);
        p.getServerWorld().spawnParticles(type.equals("ghost_parrot") ? ParticleTypes.SOUL : ParticleTypes.BUBBLE_POP,
                p.getX(), p.getY() + 1.6, p.getZ(), 20, 0.3, 0.3, 0.3, 0.05);
        p.getServerWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PARROT_AMBIENT, SoundCategory.NEUTRAL, 1f, 0.8f);
        p.sendMessage(Text.literal("*SQUAWK* Your " + (t == null ? "parrot" : t.name()) + " is back with you.").formatted(Formatting.DARK_AQUA), false);
    }

    // ------------------------------------------------------------------ KRAKEN'S PET
    private static void krakenSave(ServerPlayerEntity p) {
        p.setHealth(p.getMaxHealth() / 2f);
        p.extinguish();
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 1));
        ServerWorld w = p.getServerWorld();
        w.spawnParticles(ParticleTypes.NAUTILUS, p.getX(), p.getY() + 1, p.getZ(), 60, 0.6, 0.9, 0.6, 0.4);
        w.spawnParticles(ParticleTypes.GLOW_SQUID_INK, p.getX(), p.getY() + 1.5, p.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.8f, 0.7f);
        NbtCompound bird = takeBird(p, "krakens_pet");
        if (bird == null) return;
        NbtCompound a = new NbtCompound();
        a.put("Parrot", bird);
        a.putLong("Back", p.getServer().getOverworld().getTime() + AWAY_TICKS);
        setAway(p.getServer(), p.getUuid(), "krakens_pet", a);
        p.sendMessage(Text.literal("*SQUAWK* Your Kraken's Pet drags you back from the brink - and dives into the deep. It'll be back in 10 minutes.")
                .formatted(Formatting.DARK_AQUA), false);
    }

    // ------------------------------------------------------------------ the fight: who the parrot goes for
    /** Your last target (hit in the last 5 s), else the nearest monster after you, within range. */
    static LivingEntity foe(ServerPlayerEntity p, double range) {
        LivingEntity t = p.getAttacking();
        if (t != null && t.isAlive() && t != p && !POSSESSED.containsKey(t.getUuid()) && p.age - p.getLastAttackTime() < 100
                && t.squaredDistanceTo(p) < range * range) return t;
        return p.getServerWorld().getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(range),
                        m -> isMonster(m) && m.isAlive() && m.getTarget() == p && !POSSESSED.containsKey(m.getUuid()))
                .stream().min(Comparator.comparingDouble(m -> m.squaredDistanceTo(p))).orElse(null);
    }

    // ------------------------------------------------------------------ KAKAPO
    private static void kakapo(ServerPlayerEntity p) {
        LivingEntity f = foe(p, 6);
        if (f == null) return;
        f.damage(p.getDamageSources().playerAttack(p), 3f);
        ServerWorld w = p.getServerWorld();
        w.spawnParticles(ParticleTypes.CRIT, f.getX(), f.getBodyY(0.7), f.getZ(), 8, 0.2, 0.2, 0.2, 0.2);
        w.playSound(null, f.getX(), f.getY(), f.getZ(), SoundEvents.ENTITY_PARROT_IMITATE_VINDICATOR, SoundCategory.NEUTRAL, 0.8f, 1.6f);
    }

    // ------------------------------------------------------------------ EMBER MACAW
    private static void ember(ServerPlayerEntity p) {
        LivingEntity f = foe(p, 16);
        if (f == null || !p.canSee(f)) return;
        ServerWorld w = p.getServerWorld();
        Vec3d from = p.getPos().add(0, 1.7, 0).add(p.getRotationVec(1f).multiply(0.8));
        Vec3d dir = new Vec3d(f.getX(), f.getBodyY(0.5), f.getZ()).subtract(from);
        SmallFireballEntity fb = new SmallFireballEntity(w, p, dir.x, dir.y, dir.z) {
            @Override
            protected void onBlockHit(BlockHitResult hit) { discard(); }                 // never lights the port
        };
        fb.refreshPositionAndAngles(from.x, from.y, from.z, p.getYaw(), p.getPitch());
        w.spawnEntity(fb);
        w.playSound(null, p.getX(), p.getY() + 1.6, p.getZ(), SoundEvents.ENTITY_BLAZE_SHOOT, SoundCategory.NEUTRAL, 0.6f, 1.6f);
    }

    // ------------------------------------------------------------------ GHOST PARROT: possession
    /** A monster: vanilla's Monster marker, or a MONSTER-group type (our ModMob roster is plain PathAwareEntity). */
    static boolean isMonster(MobEntity m) {
        return m instanceof net.minecraft.entity.mob.Monster || m.getType().getSpawnGroup() == net.minecraft.entity.SpawnGroup.MONSTER;
    }

    /** A lesser monster it may take: hostile, not a boss or a boss's part, 60 health or less, not ridden, not taken. */
    static boolean possessable(MobEntity m) {
        return isMonster(m) && m.isAlive() && m.getMaxHealth() <= 60 && !m.hasVehicle() && !m.hasPassengers() && !POSSESSED.containsKey(m.getUuid())
                && !(m instanceof net.get900.pixelpirates.entity.mob.ModBoss) && !(m instanceof net.get900.pixelpirates.entity.mob.KrakenArmEntity)
                && !(m instanceof net.get900.pixelpirates.entity.mob.LamplighterEntity) && !(m instanceof WitherEntity)
                && !(m instanceof WardenEntity) && !(m instanceof ElderGuardianEntity);
    }

    private static void possess(ServerPlayerEntity p) {
        if (away(p.getServer(), p.getUuid(), "ghost_parrot") != null) return;
        ServerWorld w = p.getServerWorld();
        MobEntity m = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(12), ParrotAbilities::possessable).stream()
                .min(Comparator.comparingDouble((MobEntity e) -> e.getTarget() == p ? 0 : 1).thenComparingDouble(e -> e.squaredDistanceTo(p)))
                .orElse(null);
        if (m == null) return;
        NbtCompound bird = takeBird(p, "ghost_parrot");
        if (bird == null) return;
        NbtCompound a = new NbtCompound();
        a.put("Parrot", bird);
        a.putUuid("Possessing", m.getUuid());
        setAway(p.getServer(), p.getUuid(), "ghost_parrot", a);
        POSSESSED.put(m.getUuid(), p.getUuid());
        m.setTarget(null);
        m.setPersistent();
        m.setCustomName(Text.literal("Possessed " + m.getName().getString()).formatted(Formatting.AQUA));
        m.setCustomNameVisible(true);
        w.spawnParticles(ParticleTypes.SOUL, m.getX(), m.getBodyY(0.6), m.getZ(), 30, 0.3, 0.5, 0.3, 0.05);
        w.playSound(null, m.getX(), m.getY(), m.getZ(), SoundEvents.ENTITY_VEX_CHARGE, SoundCategory.NEUTRAL, 1f, 0.7f);
        p.sendMessage(Text.literal("*SQUAWK* Your Ghost Parrot possesses the " + m.getName().getString() + " - it fights for you now.")
                .formatted(Formatting.AQUA), false);
    }

    /** Keep a host fighting for its player: the nearest monster round them, else back to their side. */
    private static void steer(MinecraftServer server, ServerPlayerEntity p, UUID hostId) {
        Entity e = p.getServerWorld().getEntity(hostId);
        if (!(e instanceof MobEntity m) || !m.isAlive() || m.squaredDistanceTo(p) > 48 * 48) {
            if (e instanceof MobEntity lost && lost.isAlive()) release(lost);
            endPossession(server, p.getUuid(), p, e == null ? "host" : e.getName().getString(), "is lost - the ghost drifts away");
            return;
        }
        POSSESSED.put(hostId, p.getUuid());
        ServerWorld w = p.getServerWorld();
        MobEntity foe = w.getEntitiesByClass(MobEntity.class, p.getBoundingBox().expand(16),
                        x -> x != m && isMonster(x) && x.isAlive() && !POSSESSED.containsKey(x.getUuid()))
                .stream().min(Comparator.comparingDouble(x -> x.squaredDistanceTo(m))).orElse(null);
        if (foe != null) m.setTarget(foe);
        else {
            m.setTarget(null);
            if (m.squaredDistanceTo(p) > 25) m.getNavigation().startMovingTo(p, 1.1);
        }
        w.spawnParticles(ParticleTypes.SOUL, m.getX(), m.getBodyY(0.8), m.getZ(), 2, 0.2, 0.3, 0.2, 0.01);
    }

    private static void release(MobEntity m) {
        POSSESSED.remove(m.getUuid());
        m.setCustomName(null);
        m.setCustomNameVisible(false);
    }

    /** The host died or was lost: the ghost goes away for 10 minutes. p may be null (offline). */
    private static void endPossession(MinecraftServer server, UUID owner, ServerPlayerEntity p, String host, String what) {
        NbtCompound a = away(server, owner, "ghost_parrot");
        if (a == null) return;
        if (a.containsUuid("Possessing")) POSSESSED.remove(a.getUuid("Possessing"));
        a.remove("Possessing");
        a.putLong("Back", server.getOverworld().getTime() + AWAY_TICKS);
        setAway(server, owner, "ghost_parrot", a);
        if (p != null) p.sendMessage(Text.literal("*SQUAWK* The possessed " + host.replace("Possessed ", "") + " " + what
                + ". Your Ghost Parrot will be back in 10 minutes.").formatted(Formatting.AQUA), false);
    }
}
