package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.entity.custom.CannonBallEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
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
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * BOSS 4/10 - THE GHOST CAPTAIN of the Flying Dutchman (see world/GhostShipEncounter).
 *
 * BOUND: while his ship floats he stands on its poop deck (pinned to the moving VS2 ship every tick),
 * nothing can hurt him, and he only harries: Soul Beam and a Phantom Broadside on whoever is closest.
 * When the Dutchman sinks he BREAKS FREE and boards the nearest player's ship - then the full duel:
 *
 * Cursed Slash, Spectral Step (blinks behind you), Soul Beam, Anchor Toss (a spectral anchor on a chain
 * drags you to him), Phantom Broadside (red rings, then ghostly cannonballs rain down on them). At 50%
 * "Cursed Tide" (soul-fire skin): Ghost Crew (phantom pirates, max 4), Dead Calm (darkness) and
 * Drowning Curse (slowness + wither + mining fatigue).
 */
public class GhostCaptainEntity extends ModBoss {
    private static final TrackedData<Boolean> BOUND = DataTracker.registerData(GhostCaptainEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final Set<String> BOUND_ABILITIES = Set.of("soul_beam", "phantom_broadside");
    private static final Vector3f MARK = new Vector3f(0.3f, 1f, 0.85f);

    private long shipId = -1;
    private Vector3d deckShipSpace;
    private int missingTicks, hintCooldown;

    private static final class Strike {
        final Vec3d pos; int ticks;
        Strike(Vec3d pos, int ticks) { this.pos = pos; this.ticks = ticks; }
    }
    private final List<Strike> strikes = new ArrayList<>();

    public GhostCaptainEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(BOUND, false);
    }

    public boolean isBound() { return this.dataTracker.get(BOUND); }

    public void bindToShip(long id, Vector3d deck) {
        shipId = id;
        deckShipSpace = new Vector3d(deck);
        this.dataTracker.set(BOUND, true);
    }

    @Override
    protected List<String> extraAnims() { return List.of("board"); }

    @Override
    protected List<Abilities.Ability> activeAbilities() {
        List<Abilities.Ability> all = super.activeAbilities();
        if (!isBound()) return all;
        List<Abilities.Ability> out = new ArrayList<>();
        for (Abilities.Ability a : all) if (BOUND_ABILITIES.contains(a.name())) out.add(a);
        return out;
    }

    // ------------------------------------------------------------------ bound to the Dutchman
    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (hintCooldown > 0) hintCooldown--;
        if (isBound()) pinToShip(sw);
        tickStrikes(sw);
    }

    private void pinToShip(ServerWorld sw) {
        var ships = VSGameUtilsKt.getShipObjectWorld(sw);
        LoadedServerShip ship = ships == null || shipId < 0 ? null : ships.getLoadedShips().getById(shipId);
        if (ship == null || deckShipSpace == null) {
            if (++missingTicks > 200) breakFree();          // ship unloaded or gone: never stay invulnerable
            return;
        }
        missingTicks = 0;
        Vector3d w = ship.getTransform().getShipToWorld().transformPosition(new Vector3d(deckShipSpace), new Vector3d());
        this.refreshPositionAndAngles(w.x, w.y + 0.05, w.z, this.getYaw(), this.getPitch());
        this.setVelocity(Vec3d.ZERO);
        this.getNavigation().stop();
        if (this.age % 5 == 0) sw.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 0.2, getZ(), 2, 0.4, 0.1, 0.4, 0.01);
    }

    /** The Dutchman went down (or vanished): free him, then board the nearest player's ship. */
    public void breakFree() {
        net.get900.pixelpirates.PixelPirates.LOGGER.info("[Dutchman] captain breaks free at {} (bound={}, missing={})", this.getPos(), isBound(), missingTicks);
        if (!isBound()) return;
        this.dataTracker.set(BOUND, false);
        shipId = -1;
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        PlayerEntity target = sw.getClosestPlayer(getX(), getY(), getZ(), 128, p -> p.isAlive() && !p.isSpectator());
        if (target != null) board(sw, target);
    }

    private void board(ServerWorld sw, PlayerEntity target) {
        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 1.5, getZ(), 60, 0.8, 1.5, 0.8, 0.1);
        Vec3d behind = target.getPos().add(Vec3d.fromPolar(0, target.getYaw()).multiply(-2.5));
        this.refreshPositionAndAngles(behind.x, target.getY() + 0.1, behind.z, target.getYaw(), 0);
        LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(sw);
        if (bolt != null) { bolt.refreshPositionAfterTeleport(behind.x, target.getY(), behind.z); bolt.setCosmetic(true); sw.spawnEntity(bolt); }
        sw.spawnParticles(ParticleTypes.SOUL, behind.x, target.getY() + 1, behind.z, 80, 1.0, 1.2, 1.0, 0.08);
        sw.playSound(null, BlockPos.ofFloored(behind), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.HOSTILE, 1.2f, 1.4f);
        triggerAnim(ACTION, "board");
        setHome(BlockPos.ofFloored(behind));
        this.setTarget(target);
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(this) < 64 * 64))
            p.sendMessage(Text.literal("<Ghost Captain> Ye sank my ship. Now I'll take yours!").formatted(Formatting.DARK_AQUA), false);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (isBound() && !source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)) {
            if (source.getAttacker() instanceof ServerPlayerEntity p && hintCooldown == 0) {
                hintCooldown = 100;
                p.sendMessage(Text.literal("The captain is bound to the Dutchman - sink his ship!").formatted(Formatting.DARK_AQUA), true);
            }
            return false;
        }
        return super.damage(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return (isBound() && !source.isOf(net.minecraft.entity.damage.DamageTypes.OUT_OF_WORLD)) || super.isInvulnerableTo(source);
    }

    // ------------------------------------------------------------------ phantom broadside
    void startBroadside(LivingEntity t) {
        if (t == null) return;
        strikes.add(new Strike(t.getPos(), 40));
        strikes.add(new Strike(t.getPos().add(t.getVelocity().multiply(1, 0, 1).multiply(30)), 44));
        for (int i = 0; i < 4; i++) {
            double a = this.random.nextDouble() * Math.PI * 2, r = 2.5 + this.random.nextDouble() * 3.5;
            strikes.add(new Strike(t.getPos().add(Math.cos(a) * r, 0, Math.sin(a) * r), 40 + 3 * i));
        }
        this.playSound(SoundEvents.EVENT_RAID_HORN.value(), 1.5f, 0.6f);
    }

    private void tickStrikes(ServerWorld sw) {
        for (Iterator<Strike> it = strikes.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            s.ticks--;
            if (s.ticks % 4 == 0 && s.ticks > 0) {
                double r = 1.1 + 1.0 * s.ticks / 40.0;
                for (int i = 0; i < 14; i++) {
                    double a = Math.PI * 2 * i / 14;
                    sw.spawnParticles(new DustParticleEffect(MARK, 1.5f), s.pos.x + Math.cos(a) * r, s.pos.y + 0.15, s.pos.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            if (s.ticks == 16) {
                CannonBallEntity ball = CannonBallEntity.fort(sw, this, s.pos.x, s.pos.y + 18, s.pos.z, 8f);
                ball.setVelocity(0, -0.5, 0);
                sw.spawnEntity(ball);
            }
            if (s.ticks < 16 && s.ticks > 0)
                sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, s.pos.x, s.pos.y + s.ticks * 1.1, s.pos.z, 3, 0.2, 0.2, 0.2, 0.01);
            if (s.ticks <= -20) it.remove();
        }
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putBoolean("Bound", isBound());
        nbt.putLong("ShipId", shipId);
        if (deckShipSpace != null) { nbt.putDouble("DeckX", deckShipSpace.x); nbt.putDouble("DeckY", deckShipSpace.y); nbt.putDouble("DeckZ", deckShipSpace.z); }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        shipId = nbt.contains("ShipId") ? nbt.getLong("ShipId") : -1;
        if (nbt.contains("DeckX")) deckShipSpace = new Vector3d(nbt.getDouble("DeckX"), nbt.getDouble("DeckY"), nbt.getDouble("DeckZ"));
        this.dataTracker.set(BOUND, nbt.getBoolean("Bound"));
    }

    // =====================================================================================
    // ability effects (wired in MobSpecs)
    // =====================================================================================
    /** Anchor Toss: a spectral anchor flies out on its chain and drags the target to him. */
    public static Abilities.Effect anchorToss() {
        return (mob, t) -> {
            if (t == null || !mob.canSee(t)) return;
            ServerWorld sw = Abilities.sw(mob);
            Vec3d a = mob.getPos().add(0, mob.getHeight() * 0.6, 0), b = t.getPos().add(0, t.getHeight() * 0.5, 0);
            int n = (int) (a.distanceTo(b) * 2);
            for (int i = 0; i <= n; i++) {
                Vec3d q = a.lerp(b, i / (double) n);
                sw.spawnParticles(ParticleTypes.SOUL, q.x, q.y, q.z, 1, 0.03, 0.03, 0.03, 0);
            }
            sw.spawnParticles(ParticleTypes.SCULK_SOUL, b.x, b.y, b.z, 12, 0.3, 0.3, 0.3, 0.03);
            mob.playSound(SoundEvents.BLOCK_CHAIN_BREAK, 2.0f, 0.5f);
            if (t.damage(mob.getDamageSources().mobAttack(mob), 8f)) {
                Vec3d to = mob.getPos().subtract(t.getPos());
                t.setVelocity(to.normalize().multiply(Math.min(1.8, to.length() * 0.25)).add(0, 0.3, 0));
                t.velocityModified = true;
                t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 2), mob);
            }
        };
    }

    public static Abilities.Effect phantomBroadside() {
        return (mob, t) -> { if (mob instanceof GhostCaptainEntity g) g.startBroadside(t); };
    }

    public static Abilities.Effect ghostCrew() {
        Abilities.Effect spawn = Abilities.summon("pixelpirates:phantom_pirate", 3, 4);
        return (mob, t) -> {
            var type = ModMobs.TYPES.get("phantom_pirate");
            if (type != null && mob.getWorld().getEntitiesByType(type, mob.getBoundingBox().expand(24), LivingEntity::isAlive).size() >= 4) return;
            spawn.fire(mob, t);
        };
    }
}
