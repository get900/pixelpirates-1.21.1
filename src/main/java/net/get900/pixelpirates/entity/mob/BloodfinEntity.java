package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.entity.custom.ChumEntity;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ItemStackParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BOSS 6/10 - THE BLOODFIN, the reef's apex hunter (lair: world/dungeon/WhalersGrave).
 *
 * THE WHALERS' HARPOONS: Harpoon Winches (HarpoonWinchBlock) stand on the rocks around its lagoon; a player aims
 * with their view and fires. Each hook tethers it to that winch (cranking the winch reels it in) and slows it 25%;
 * it thrashes a hook loose every 6 s. THREE hooks at once and it rolls onto its back - TONIC IMMOBILITY: 10 s limp at
 * the surface (7 s enraged), belly up, taking double damage. Then it snaps every line.
 * FLESH: its hide tears away in six chunks as it is hurt (bones flesh0..5 hidden, glowing wounds beneath). Torn chunks
 * float up as Bloodfin Flesh; it cannot resist blood - it swims to any floating flesh or thrown Chum and gorges for a
 * moment (its own flesh heals it 3%). Grab the chunks to deny it, or throw bait to line up a harpoon shot.
 * DEVOUR: it snaps a player into its jaws (smashing any boat they sit in), dives, then rockets 22 blocks out of the
 * water in a barrel roll and belly-flops back - 14 damage to the one in its mouth. 20 damage from anyone while it holds
 * someone makes it gag them out. It lands dazed for 1.5 s.
 * Phase 1: bite, Lunge, Tail Slap, Wake Slam (a surge that drags shore-standers into the sea), Devour, Frenzy Call.
 * Phase 2 "BLOOD FRENZY" at 50%: the last flesh tears off (flayed skin), the water clouds with blood, every player
 * within 40 hears its heartbeat - faster the closer it gets - and darkness pulses over the reef. THE HUNT: it sinks
 * out of sight (invisible) and circles below its prey, only a fin cutting the surface, then strikes from beneath
 * straight into a Devour. Its bites make victims bleed. Ghost School summons ghost sharks.
 */
public class BloodfinEntity extends ModBoss {
    private static final TrackedData<Integer> TORN = DataTracker.registerData(BloodfinEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final UUID HOOK_SLOW = UUID.fromString("5d0c9a61-2b7e-4f3c-8e41-7a9b3c2d1e05");
    private static final Vector3f BLOOD = new Vector3f(0.55f, 0.02f, 0.02f);
    private static final Vector3f ROPE = new Vector3f(0.6f, 0.5f, 0.35f);
    private static final float[] TEAR_AT = {0.9f, 0.8f, 0.7f, 0.62f, 0.55f};
    public static final int CHUNKS = 6;
    /** Lines needed to flip it (was 3 - near impossible to land at once, 2026-09-29 playtest). */
    public static final int HOOKS_TO_PIN = 1;
    /** After breaking free it shrugs off harpoons this long, so it can't be pinned back-to-back forever. */
    public static final int WARY_TICKS = 200;

    enum Mode { NONE, DIVE, RISE, LEAP, HUNT }

    private static final class Hook {
        final BlockPos winch; double len; int age;
        Hook(BlockPos winch, double len) { this.winch = winch; this.len = len; }
    }

    private final List<Hook> hooks = new ArrayList<>();
    private int wary;
    private BlockPos arena;
    private int arenaR;
    private int thrashTicks, pinnedTicks, stun, feedTicks, feedCooldown, hintCooldown, heartbeat;
    private Entity feedTarget;
    private int feedApproach;
    private Mode mode = Mode.NONE;
    private int modeTicks;
    private Vec3d leapFrom, leapDir;
    private double leapH, leapD;
    private LivingEntity held;
    private float heldDamage;
    private LivingEntity huntTarget;
    private final Map<UUID, Integer> bleeding = new HashMap<>();
    private final Set<UUID> greeted = new HashSet<>();
    private boolean announcedFlesh;

    public BloodfinEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(TORN, 0);
    }

    public int torn() { return this.dataTracker.get(TORN); }

    public boolean isPinned() { return pinnedTicks > 0; }

    @Override
    public List<String> toggleBones() { return List.of("flesh0", "flesh1", "flesh2", "flesh3", "flesh4", "flesh5"); }

    @Override
    public boolean isBoneHidden(String bone) {
        return bone.startsWith("flesh") && bone.length() == 6 && bone.charAt(5) - '0' < torn();
    }

    @Override
    protected List<String> extraAnims() { return List.of("breach", "thrash", "flipped", "feed", "gag", "hunt"); }

    @Override
    protected double leashRange() { return 72; }                // an apex hunter: it chases you well past its lagoon

    @Override
    protected boolean scriptedMotion() {
        return mode == Mode.DIVE || mode == Mode.RISE || mode == Mode.LEAP || pinnedTicks > 0 || stun > 0 || feedTicks > 0;
    }

    // ------------------------------------------------------------------ abilities availability
    @Override
    protected List<Abilities.Ability> activeAbilities() {
        if (stun > 0 || pinnedTicks > 0 || mode != Mode.NONE || feedTicks > 0 || feedTarget != null) return List.of();
        List<Abilities.Ability> out = new ArrayList<>();
        for (Abilities.Ability a : super.activeAbilities()) {
            if (!hooks.isEmpty() && !a.name().equals("tail_slap")) continue;         // tethered: it can only lash out
            out.add(a);
        }
        return out;
    }

    // ------------------------------------------------------------------ flesh
    private void tear(ServerWorld sw) { tear(sw, true); }

    private void tear(ServerWorld sw, boolean dropChunk) {
        int i = torn();
        if (i >= CHUNKS) return;
        this.dataTracker.set(TORN, i + 1);
        Vec3d fwd = Vec3d.fromPolar(0, this.bodyYaw), side = new Vec3d(-fwd.z, 0, fwd.x);
        double s = (i % 2 == 0) ? 1 : -1;
        Vec3d at = getPos().add(0, 1.0, 0).add(i < 4 ? side.multiply(s * 1.2) : Vec3d.ZERO).add(fwd.multiply(i < 2 || i == 4 ? 0.8 : -0.8));
        if (dropChunk) {
            ItemEntity chunk = new ItemEntity(sw, at.x, at.y, at.z, new ItemStack(ModItems.BLOODFIN_FLESH));
            Vec3d out = i < 4 ? side.multiply(s * 0.35) : new Vec3d(0, 0.1, 0);
            chunk.setVelocity(out.x, 0.35, out.z);
            chunk.setPickupDelay(10);
            sw.spawnEntity(chunk);
        }
        sw.spawnParticles(new ItemStackParticleEffect(ParticleTypes.ITEM, new ItemStack(ModItems.BLOODFIN_FLESH)), at.x, at.y, at.z, 16, 0.4, 0.4, 0.4, 0.2);
        sw.spawnParticles(new DustParticleEffect(BLOOD, 2.0f), at.x, at.y, at.z, 40, 0.8, 0.6, 0.8, 0.05);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 2.0f, 0.5f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, SoundCategory.HOSTILE, 1.5f, 0.5f);
        if (!announcedFlesh) {
            announcedFlesh = true;
            broadcast(sw, Text.literal("A chunk of the Bloodfin's flesh tears away and floats up - it will turn to feed on its own blood."
                    + " Grab the flesh, or use it as bait.").formatted(Formatting.RED), false);
        }
    }

    // ------------------------------------------------------------------ hooks (HarpoonWinchBlock / HarpoonEntity)
    /** The lagoon it is kept inside (worldgen, before the spawn). Null = free roaming (spawn egg, /summon). */
    public void setArena(BlockPos centre, int radius) {
        this.arena = centre.toImmutable();
        this.arenaR = radius;
    }

    private double arenaDist(Vec3d p) {
        double dx = p.x - (arena.getX() + 0.5), dz = p.z - (arena.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Soft wall at the lagoon edge: turn it back toward the middle. */
    private void confine() {
        if (arena == null || mode == Mode.LEAP) return;
        double d = arenaDist(getPos());
        if (d < arenaR - 2) return;
        Vec3d in = new Vec3d(arena.getX() + 0.5 - getX(), 0, arena.getZ() + 0.5 - getZ()).normalize();
        double push = Math.min(0.5, (d - (arenaR - 2)) * 0.12);
        double keep = d > arenaR ? 0.3 : 1;
        this.setVelocity(this.getVelocity().multiply(keep, 1, keep).add(in.multiply(push)));
        this.velocityDirty = true;
        if (d > arenaR + 6 && this.getNavigation().isIdle())
            this.getNavigation().startMovingTo(arena.getX() + 0.5, getY(), arena.getZ() + 0.5, 1.4);
    }

    private boolean inArena(Vec3d p, double margin) { return arena == null || arenaDist(p) <= arenaR - margin; }

    public boolean isHookedFrom(BlockPos winch) {
        for (Hook h : hooks) if (h.winch.equals(winch)) return true;
        return false;
    }

    public void addHook(BlockPos winch, Entity by) {
        if (!(this.getWorld() instanceof ServerWorld sw) || pinnedTicks > 0 || !isAlive()) return;
        if (wary > 0) {
            sw.playSound(null, getBlockPos(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.HOSTILE, 1.5f, 0.6f);
            broadcast(sw, Text.literal("The harpoon glances off - the Bloodfin is wary of the lines (" + (wary / 20 + 1) + " s)").formatted(Formatting.GRAY), true);
            return;
        }
        if (by instanceof PlayerEntity p && !BossProgression.eligible(p, chainIndex())) return;
        hooks.add(new Hook(winch.toImmutable(), Math.max(4, Math.sqrt(squaredDistanceTo(Vec3d.ofCenter(winch))) + 1)));
        if (hooks.size() == 1) thrashTicks = 120;
        updateHookSlow();
        sw.playSound(null, getBlockPos(), SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 2.0f, 0.6f);
        sw.spawnParticles(new DustParticleEffect(BLOOD, 1.8f), getX(), getY() + 1, getZ(), 30, 0.6, 0.5, 0.6, 0.05);
        if (hooks.size() >= HOOKS_TO_PIN) pin(sw);
    }

    /** Crank a winch: shorten its line by 2 blocks (drags the shark toward it). */
    public void reel(BlockPos winch) {
        for (Hook h : hooks) if (h.winch.equals(winch)) h.len = Math.max(3, h.len - 2);
    }

    private void updateHookSlow() {
        var speed = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed == null) return;
        speed.removeModifier(HOOK_SLOW);
        if (!hooks.isEmpty())
            speed.addTemporaryModifier(new EntityAttributeModifier(HOOK_SLOW, "harpooned", -0.25 * Math.min(3, hooks.size()), EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
    }

    private void tickHooks(ServerWorld sw) {
        if (hooks.isEmpty()) return;
        for (Iterator<Hook> it = hooks.iterator(); it.hasNext(); ) {
            Hook h = it.next();
            h.age++;
            Vec3d w = Vec3d.ofCenter(h.winch).add(0, 0.5, 0);
            Vec3d me = getPos().add(0, 0.8, 0);
            Vec3d d = me.subtract(w);
            double len = d.length();
            if (len > 48 || !sw.isChunkLoaded(h.winch)) { it.remove(); continue; }
            if (this.age % 3 == 0)
                for (double t = 0; t <= 1; t += 1.0 / Math.max(4, len)) {
                    Vec3d q = w.add(d.multiply(t)).add(0, -Math.sin(t * Math.PI) * Math.min(2, len * 0.05), 0);
                    sw.spawnParticles(new DustParticleEffect(ROPE, 0.8f), q.x, q.y, q.z, 1, 0, 0, 0, 0);
                }
            if (len > h.len && pinnedTicks == 0) {                                     // the line holds: drag it back
                Vec3d pull = d.normalize().multiply(-Math.min(0.35, (len - h.len) * 0.12));
                this.setVelocity(this.getVelocity().add(pull));
                this.velocityDirty = true;
            }
        }
        if (pinnedTicks > 0 || hooks.isEmpty()) { updateHookSlow(); return; }
        if (--thrashTicks == 20) triggerAnim(ACTION, "thrash");
        if (thrashTicks <= 0) {
            hooks.remove(0);
            thrashTicks = isEnraged() ? 90 : 120;
            updateHookSlow();
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_LEASH_KNOT_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
            sw.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 60, 1.5, 0.5, 1.5, 0.3);
            broadcast(sw, Text.literal("The Bloodfin thrashes a harpoon loose! (" + hooks.size() + "/3)").formatted(Formatting.YELLOW), true);
        }
    }

    private void pin(ServerWorld sw) {
        hooks.clear();
        updateHookSlow();
        releaseHeld(sw, false);
        mode = Mode.NONE;
        pinnedTicks = isEnraged() ? 140 : 200;
        this.getNavigation().stop();
        triggerAnim(ACTION, "flipped");
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_FLOP, SoundCategory.HOSTILE, 3.0f, 0.4f);
        broadcast(sw, Text.literal("Harpooned! The Bloodfin rolls onto its back, limp - strike its belly!").formatted(Formatting.GOLD, Formatting.BOLD), false);
    }

    private void tickPinned(ServerWorld sw) {
        if (pinnedTicks <= 0) return;
        pinnedTicks--;
        // float up to just under the surface, belly to the sky
        double top = waterSurfaceY();
        double vy = MathHelper.clamp((top - 1.2 - getY()) * 0.2, -0.2, 0.2);
        safeMove(new Vec3d(0, isTouchingWater() ? vy : -0.1, 0));
        this.setVelocity(Vec3d.ZERO);
        if (this.age % 5 == 0) sw.spawnParticles(new DustParticleEffect(BLOOD, 1.4f), getX(), getY() + 1, getZ(), 6, 0.8, 0.3, 0.8, 0.02);
        if (pinnedTicks == 0) {
            wary = WARY_TICKS;
            triggerAnim(ACTION, "thrash");
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.5f, 0.6f);
            sw.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 120, 2, 0.6, 2, 0.4);
            for (LivingEntity e : Abilities.victims(this, 5)) {
                Vec3d away = e.getPos().subtract(getPos()).multiply(1, 0, 1);
                away = away.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : away.normalize();
                e.damage(this.getDamageSources().mobAttack(this), 6f);
                e.addVelocity(away.x * 1.2, 0.5, away.z * 1.2);
                e.velocityModified = true;
            }
            broadcast(sw, Text.literal("The Bloodfin rights itself and snaps every line!").formatted(Formatting.RED), true);
        }
    }

    // ------------------------------------------------------------------ feeding on blood
    private void tickFeeding(ServerWorld sw) {
        if (feedCooldown > 0) feedCooldown--;
        if (feedTicks > 0) {
            if (feedTarget == null || !feedTarget.isAlive()) { feedTicks = 0; feedTarget = null; return; }
            this.setVelocity(Vec3d.ZERO);
            this.getLookControl().lookAt(feedTarget, 30, 30);
            if (feedTicks % 4 == 0)
                sw.spawnParticles(new DustParticleEffect(BLOOD, 1.6f), feedTarget.getX(), feedTarget.getY() + 0.3, feedTarget.getZ(), 8, 0.4, 0.3, 0.4, 0.02);
            if (feedTicks % 8 == 0) sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EAT, SoundCategory.HOSTILE, 1.5f, 0.5f);
            if (--feedTicks == 0) {
                boolean ownFlesh = feedTarget instanceof ItemEntity ie && ie.getStack().isOf(ModItems.BLOODFIN_FLESH)
                        || feedTarget instanceof ChumEntity c && c.isFlesh();
                feedTarget.discard();
                feedTarget = null;
                feedCooldown = isEnraged() ? 160 : 220;
                if (ownFlesh) {
                    this.heal(this.getMaxHealth() * 0.03f);
                    sw.spawnParticles(ParticleTypes.HEART, getX(), getY() + 1.5, getZ(), 3, 0.5, 0.3, 0.5, 0);
                }
            }
            return;
        }
        if (feedTarget != null) {
            if (!feedTarget.isAlive() || ++feedApproach > 140) { feedTarget = null; return; }
            this.getNavigation().startMovingTo(feedTarget.getX(), feedTarget.getY() - 0.5, feedTarget.getZ(), 1.6);
            if (squaredDistanceTo(feedTarget) < 3.2 * 3.2) {
                feedTicks = feedTarget instanceof ChumEntity c && !c.isFlesh() ? 32 : 44;
                this.getNavigation().stop();
                triggerAnim(ACTION, "feed");
            }
            return;
        }
        if (feedCooldown > 0 || this.age % 10 != 0 || !hooks.isEmpty() || mode != Mode.NONE || stun > 0) return;
        Box box = getBoundingBox().expand(24, 10, 24);
        Entity best = null; double bd = Double.MAX_VALUE;
        for (ChumEntity c : sw.getEntitiesByClass(ChumEntity.class, box, c -> c.isAlive() && c.isTouchingWater() && inArena(c.getPos(), 1))) {
            double d = c.squaredDistanceTo(this); if (d < bd) { bd = d; best = c; }
        }
        for (ItemEntity ie : sw.getEntitiesByClass(ItemEntity.class, box, e -> e.isAlive() && e.getStack().isOf(ModItems.BLOODFIN_FLESH) && e.isTouchingWater() && inArena(e.getPos(), 1))) {
            double d = ie.squaredDistanceTo(this); if (d < bd) { bd = d; best = ie; }
        }
        if (best != null) {
            feedTarget = best;
            feedApproach = 0;
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_DOLPHIN_EAT, SoundCategory.HOSTILE, 2.0f, 0.4f);
            if (hintCooldown == 0) { hintCooldown = 300; broadcast(sw, Text.literal("The Bloodfin smells blood and turns to feed - now's your shot!").formatted(Formatting.RED), true); }
        }
    }

    // ------------------------------------------------------------------ DEVOUR -> dive -> breach -> crash
    void devour(LivingEntity t) {
        if (!(this.getWorld() instanceof ServerWorld sw) || t == null || !t.isAlive() || mode != Mode.NONE) return;
        Vec3d mouth = mouthPos();
        if (t.squaredDistanceTo(mouth) > 4.2 * 4.2 && t.squaredDistanceTo(this) > 4.5 * 4.5) {
            t.damage(this.getDamageSources().mobAttack(this), 10f);      // the jaws close on water
            return;
        }
        if (t.getVehicle() instanceof BoatEntity boat) {
            t.stopRiding();
            sw.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.OAK_PLANKS.getDefaultState()),
                    boat.getX(), boat.getY() + 0.5, boat.getZ(), 40, 0.8, 0.4, 0.8, 0.2);
            sw.playSound(null, boat.getBlockPos(), SoundEvents.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, SoundCategory.HOSTILE, 2.0f, 0.6f);
            boat.kill();
            broadcast(sw, Text.literal("The Bloodfin bites the boat in two!").formatted(Formatting.RED), true);
        } else if (t.hasVehicle()) t.stopRiding();
        if (t instanceof ModBoss) return;
        held = t;
        heldDamage = 0;
        t.startRiding(this, true);
        t.damage(this.getDamageSources().mobAttack(this), 6f);
        mode = Mode.DIVE;
        modeTicks = 0;
        this.getNavigation().stop();
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 2.0f, 0.5f);
        if (t instanceof ServerPlayerEntity p)
            p.sendMessage(Text.literal("You are in the Bloodfin's jaws! Hurt it (20 damage) to make it spit you out!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
    }

    private Vec3d mouthPos() {
        Vec3d f = Vec3d.fromPolar(0, this.bodyYaw);
        return getPos().add(f.multiply(2.6)).add(0, 0.3, 0);
    }

    @Override
    protected void updatePassengerPosition(Entity passenger, Entity.PositionUpdater positionUpdater) {
        Vec3d m = mouthPos();
        positionUpdater.accept(passenger, m.x, m.y - 0.3, m.z);
    }

    /** Vanilla drops a dismounting passenger on TOP of the vehicle - out of the jaws instead. */
    @Override
    public Vec3d updatePassengerForDismount(LivingEntity passenger) { return mouthPos().add(Vec3d.fromPolar(0, this.bodyYaw).multiply(1.0)); }

    private void tickDevour(ServerWorld sw) {
        if (mode == Mode.NONE || mode == Mode.HUNT) return;
        modeTicks++;
        if (held != null) {
            if (!held.isAlive() || held.isRemoved()) held = null;
            else if (!held.hasVehicle() && held.squaredDistanceTo(this) < 36) held.startRiding(this, true);   // no shift-escape
            if (held != null && modeTicks % 20 == 0) {
                held.damage(this.getDamageSources().mobAttack(this), 3f);
                held.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), this);
                sw.spawnParticles(new DustParticleEffect(BLOOD, 1.8f), held.getX(), held.getY() + 1, held.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
            }
        }
        switch (mode) {
            case DIVE -> {
                safeMove(new Vec3d(0, -0.35, 0).add(Vec3d.fromPolar(0, bodyYaw).multiply(0.15)));
                if (modeTicks >= 14 || !isTouchingWater()) { mode = Mode.RISE; modeTicks = 0; }
            }
            case RISE -> {
                safeMove(new Vec3d(0, 0.9, 0));
                BlockPos head = BlockPos.ofFloored(getX(), getY() + 1.2, getZ());
                boolean surfaced = !sw.getFluidState(head).isIn(FluidTags.WATER);
                if (surfaced || modeTicks >= 30) startLeap(sw);
            }
            case LEAP -> tickLeap(sw);
            default -> { }
        }
    }

    private void startLeap(ServerWorld sw) {
        mode = Mode.LEAP;
        modeTicks = 0;
        double surface = waterSurfaceY();
        leapFrom = new Vec3d(getX(), Math.max(getY(), surface - 1), getZ());
        leapDir = Vec3d.fromPolar(0, bodyYaw);
        leapH = isEnraged() ? 28 : 22;
        leapD = 14;
        if (arena != null && !inArena(leapFrom.add(leapDir.multiply(leapD)), 3)) {
            // would come down outside the lagoon: arc toward its middle instead, landing short of the far side
            Vec3d toC = new Vec3d(arena.getX() + 0.5 - leapFrom.x, 0, arena.getZ() + 0.5 - leapFrom.z);
            double dc = toC.length();
            if (dc > 1e-3) leapDir = toC.normalize();
            leapD = Math.min(14, Math.max(0, dc + arenaR - 6));
            this.setYaw((float) Math.toDegrees(Math.atan2(-leapDir.x, leapDir.z)));
            this.bodyYaw = this.headYaw = this.getYaw();
        }
        Vec3d land = leapFrom.add(leapDir.multiply(leapD));
        boolean wet = false;                                                  // water within a few blocks under the landing spot?
        for (int k = -1; k <= 5 && !wet; k++) wet = sw.getFluidState(BlockPos.ofFloored(land.x, leapFrom.y - k, land.z)).isIn(FluidTags.WATER);
        if (!wet) leapD = 0;                                                   // never belly-flop onto rock: straight up and down
        triggerAnim(ACTION, "breach");
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.HOSTILE, 3.0f, 0.5f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.5f, 0.7f);
        sw.spawnParticles(ParticleTypes.SPLASH, getX(), surface, getZ(), 150, 1.5, 0.3, 1.5, 0.5);
        sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, getX(), surface - 1, getZ(), 60, 1, 1, 1, 0.3);
    }

    private static final int LEAP_T = 44;

    private void tickLeap(ServerWorld sw) {
        double t = Math.min(1.0, modeTicks / (double) LEAP_T);
        double y = leapFrom.y + 4 * leapH * t * (1 - t);
        Vec3d next = new Vec3d(leapFrom.x + leapDir.x * leapD * t, y, leapFrom.z + leapDir.z * leapD * t);
        this.setVelocity(next.subtract(getPos()));
        this.setPosition(next);
        this.velocityDirty = true;
        if (modeTicks % 2 == 0) sw.spawnParticles(ParticleTypes.FALLING_WATER, getX(), getY() + 0.5, getZ(), 10, 1.2, 0.5, 1.2, 0);
        if (torn() > 0 && modeTicks % 2 == 0) sw.spawnParticles(new DustParticleEffect(BLOOD, 1.6f), getX(), getY() + 0.8, getZ(), 6, 0.8, 0.4, 0.8, 0);
        if (modeTicks == LEAP_T / 2) sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.5f, 0.5f);
        if (modeTicks < LEAP_T) return;
        // THE CRASH
        mode = Mode.NONE;
        stun = 30;
        sw.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 300, 3, 0.6, 3, 0.8);
        sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 1, getZ(), 3, 1.5, 0.4, 1.5, 0);
        sw.spawnParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 30, 2, 0.5, 2, 0.1);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.5f, 0.5f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_SPLASH_HIGH_SPEED, SoundCategory.HOSTILE, 3.0f, 0.5f);
        if (held != null) {
            LivingEntity victim = held;
            releaseHeld(sw, false);
            victim.damage(this.getDamageSources().mobAttack(this), isEnraged() ? 18f : 14f);
            victim.addVelocity(0, 0.6, 0);
            victim.velocityModified = true;
        }
        for (LivingEntity e : Abilities.victims(this, 5)) {
            Vec3d away = e.getPos().subtract(getPos()).multiply(1, 0, 1);
            away = away.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : away.normalize();
            if (e.damage(this.getDamageSources().mobAttack(this), 8f)) { e.addVelocity(away.x * 1.3, 0.6, away.z * 1.3); e.velocityModified = true; }
        }
    }

    private void releaseHeld(ServerWorld sw, boolean gag) {
        if (held == null) return;
        LivingEntity v = held;
        held = null;
        v.stopRiding();
        if (gag) {
            Vec3d f = Vec3d.fromPolar(0, bodyYaw);
            v.setVelocity(f.x * 1.0, 0.5, f.z * 1.0);
            v.velocityModified = true;
            triggerAnim(ACTION, "gag");
            stun = 40;
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_PLAYER_HURT_DROWN, SoundCategory.HOSTILE, 2.0f, 0.4f);
            broadcast(sw, Text.literal("The Bloodfin gags and spits its prey out!").formatted(Formatting.GOLD), true);
        }
    }

    // ------------------------------------------------------------------ THE HUNT (phase 2)
    void startHunt(LivingEntity t) {
        if (t == null || !t.isTouchingWater() || mode != Mode.NONE || !hooks.isEmpty()) return;
        huntTarget = t;
        mode = Mode.HUNT;
        modeTicks = 0;
        this.setInvisible(true);
        this.getNavigation().stop();
        this.playSound(SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS_ULTRA_RARE, 3.0f, 0.5f);
    }

    private void tickHunt(ServerWorld sw) {
        if (mode != Mode.HUNT) return;
        modeTicks++;
        LivingEntity t = huntTarget;
        if (t == null || !t.isAlive() || modeTicks > 140) { endHunt(); return; }
        double a = modeTicks * 0.09, r = Math.max(2.5, 8 - modeTicks * 0.06);
        Vec3d want = t.getPos().add(Math.cos(a) * r, -5, Math.sin(a) * r);
        if (!inArena(want, 2)) { endHunt(); return; }                           // prey out of the lagoon: give up the stalk
        if (!sw.getFluidState(BlockPos.ofFloored(want)).isIn(FluidTags.WATER)) want = new Vec3d(want.x, t.getY() - 2, want.z);
        Vec3d step = want.subtract(getPos());
        if (step.lengthSquared() > 0.36) step = step.normalize().multiply(0.6);
        safeMove(step);
        this.setYaw((float) Math.toDegrees(Math.atan2(-step.x, step.z)));
        this.bodyYaw = this.headYaw = this.getYaw();
        // only the fin cuts the surface
        double top = waterSurfaceY();
        if (modeTicks % 2 == 0) {
            Vec3d back = Vec3d.fromPolar(0, bodyYaw).multiply(-0.4);
            for (int k = 0; k < 3; k++)
                sw.spawnParticles(new DustParticleEffect(new Vector3f(0.18f, 0.22f, 0.25f), 1.6f), getX() + back.x * k, top + 0.2 + (2 - k) * 0.25, getZ() + back.z * k, 1, 0, 0, 0, 0);
            sw.spawnParticles(ParticleTypes.SPLASH, getX(), top + 0.1, getZ(), 3, 0.2, 0, 0.2, 0.05);
        }
        if (modeTicks >= 80 && t.isTouchingWater()) {                              // strike from below
            endHunt();
            Vec3d up = t.getPos().subtract(getPos());
            safeMove(up.lengthSquared() > 25 ? up.normalize().multiply(5) : up);
            triggerAnim(ACTION, "devour");
            sw.playSound(null, t.getBlockPos(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 2.0f, 1.2f);
            devour(t);
            if (mode == Mode.DIVE) { mode = Mode.RISE; modeTicks = 0; }              // already below: straight up and out
        }
    }

    private void endHunt() {
        if (mode == Mode.HUNT) mode = Mode.NONE;
        huntTarget = null;
        this.setInvisible(false);
    }

    // ------------------------------------------------------------------ ticking
    @Override
    protected void mobTick() {
        if (stun > 0) { stun--; this.getNavigation().stop(); }
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (hintCooldown > 0) hintCooldown--;
        if (wary > 0) wary--;
        confine();
        tickHooks(sw);
        tickPinned(sw);
        tickDevour(sw);
        tickHunt(sw);
        if (pinnedTicks == 0 && mode == Mode.NONE && stun == 0) tickFeeding(sw);
        tickBleeding(sw);
        if (this.age % 20 == 0)
            for (ServerPlayerEntity p : nearbyPlayers(sw, 32))
                if (greeted.add(p.getUuid()))
                    p.sendMessage(Text.literal("The Bloodfin hunts these waters. Use the Harpoon Winches on the rocks - three lines at once will"
                            + " roll it helpless onto its back. Bait it with blood to line up your shot.").formatted(Formatting.RED), false);
        if (isEnraged()) frenzyAura(sw);
        else if (torn() > 0 && this.age % 3 == 0)
            sw.spawnParticles(new DustParticleEffect(BLOOD, 1.2f), getX(), getY() + 0.8, getZ(), torn(), 0.8, 0.4, 0.8, 0);
    }

    /** Blood Frenzy: blood clouds the water, the heartbeat closes in, darkness pulses. */
    private void frenzyAura(ServerWorld sw) {
        if (this.age % 2 == 0 && !isInvisible())
            sw.spawnParticles(new DustParticleEffect(BLOOD, 3.0f), getX(), getY() + 0.8, getZ(), 6, 3, 1.5, 3, 0);
        if (--heartbeat <= 0) {
            double nearest = 40;
            for (ServerPlayerEntity p : nearbyPlayers(sw, 40)) {
                double d = Math.sqrt(p.squaredDistanceTo(this));
                nearest = Math.min(nearest, d);
                sw.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE,
                        (float) (2.0 - d / 30), 0.6f);
            }
            heartbeat = (int) MathHelper.clamp(8 + nearest * 0.9, 8, 40);
        }
        if (this.age % 240 == 0)
            for (ServerPlayerEntity p : nearbyPlayers(sw, 32))
                if (!p.isCreative() && mayTarget(p)) p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0), this);
    }

    private void tickBleeding(ServerWorld sw) {
        if (bleeding.isEmpty() || this.age % 20 != 0) return;
        for (Iterator<Map.Entry<UUID, Integer>> it = bleeding.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Entity v = sw.getEntity(e.getKey());
            if (!(v instanceof LivingEntity le) || !le.isAlive() || e.getValue() <= 0) { it.remove(); continue; }
            le.damage(this.getDamageSources().magic(), 1.5f);
            sw.spawnParticles(new DustParticleEffect(BLOOD, 1.2f), le.getX(), le.getY() + 1, le.getZ(), 10, 0.3, 0.5, 0.3, 0);
            e.setValue(e.getValue() - 1);
        }
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit && isEnraged() && target instanceof LivingEntity) bleeding.put(target.getUuid(), 5);
        return hit;
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        float mult = pinnedTicks > 0 ? 2f : 1f;
        boolean hurt = super.damage(source, amount * mult);
        if (!hurt || !(this.getWorld() instanceof ServerWorld sw)) return hurt;
        if (held != null && source.getAttacker() instanceof PlayerEntity) {
            heldDamage += amount;
            if (heldDamage >= 20f) releaseHeld(sw, true);
        }
        if (mode == Mode.HUNT) endHunt();                                               // hitting it drags it into the open
        while (torn() < TEAR_AT.length && this.getHealth() < this.getMaxHealth() * TEAR_AT[torn()]) tear(sw);
        return hurt;
    }

    @Override
    protected void onEnrage() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        while (torn() < CHUNKS) tear(sw, torn() == CHUNKS - 1);        // only the last chunk floats free as bait
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 3.0f, 0.5f);
        sw.spawnParticles(new DustParticleEffect(BLOOD, 3.0f), getX(), getY() + 1, getZ(), 300, 5, 2, 5, 0.05);
        broadcast(sw, Text.literal("The Bloodfin tears the last of its own flesh away - the sea turns red. BLOOD FRENZY.").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
    }

    @Override
    public void onDeath(DamageSource source) {
        if (this.getWorld() instanceof ServerWorld sw) { releaseHeld(sw, false); endHunt(); }
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
        nbt.putInt("Torn", torn());
        nbt.putInt("Pinned", pinnedTicks);
        nbt.putInt("Wary", wary);
        if (arena != null) { nbt.putLong("Arena", arena.asLong()); nbt.putInt("ArenaR", arenaR); }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(TORN, MathHelper.clamp(nbt.getInt("Torn"), 0, CHUNKS));
        pinnedTicks = nbt.getInt("Pinned");
        wary = nbt.getInt("Wary");
        if (nbt.contains("Arena")) { arena = BlockPos.fromLong(nbt.getLong("Arena")); arenaR = nbt.getInt("ArenaR"); }
        announcedFlesh = torn() > 0;
    }

    /** Test hooks (/ppboss bloodfin ...). */
    public void debugPin() { if (this.getWorld() instanceof ServerWorld sw) pin(sw); }

    /** Feed the nearest non-boss creature within 16 blocks into the jaws (tests the breach without a player). */
    public void debugDevour() {
        LivingEntity pick = null;
        for (LivingEntity e : this.getWorld().getEntitiesByClass(LivingEntity.class, getBoundingBox().expand(16), e -> e != this && !(e instanceof ModMob) && !(e instanceof net.get900.pixelpirates.entity.custom.SharkEntity) && e.isAlive()))
            if (pick == null || e.squaredDistanceTo(this) < pick.squaredDistanceTo(this)) pick = e;
        if (pick == null) return;
        Vec3d m = mouthPos();
        pick.refreshPositionAndAngles(m.x, m.y, m.z, 0, 0);
        devour(pick);
    }

    public void debugTear() { if (this.getWorld() instanceof ServerWorld sw) tear(sw); }

    public String debugStatus() {
        return "Bloodfin at " + getBlockPos().toShortString() + ": hp " + (int) getHealth() + "/" + (int) getMaxHealth() + ", torn " + torn() + "/" + CHUNKS
                + ", hooks " + hooks.size() + (arena != null ? ", arena d" + (int) arenaDist(getPos()) + "/" + arenaR : "")
                + (wary > 0 ? ", wary " + wary / 20 + " s" : "") + (pinnedTicks > 0 ? ", PINNED " + pinnedTicks / 20 + " s" : "") + ", mode " + mode
                + (feedTarget != null ? ", feeding" : "") + (isEnraged() ? ", BLOOD FRENZY" : "")
                + (hasPassengers() ? ", holding " + getFirstPassenger().getName().getString() : "");
    }

    // =====================================================================================
    // ability effects (wired in MobSpecs)
    // =====================================================================================
    public static Abilities.Effect devourEffect() { return (mob, t) -> { if (mob instanceof BloodfinEntity b) b.devour(t); }; }

    public static Abilities.Effect hunt() { return (mob, t) -> { if (mob instanceof BloodfinEntity b) b.startHunt(t); }; }

    /** Summon capped: never more than `cap` of that type around it. */
    public static Abilities.Effect callCapped(String id, int count, int cap) {
        Abilities.Effect spawn = Abilities.summon("pixelpirates:" + id, count, 5);
        return (mob, t) -> {
            var type = ModMobs.TYPES.get(id);
            var list = type != null ? mob.getWorld().getEntitiesByType(type, mob.getBoundingBox().expand(32), LivingEntity::isAlive)
                    : mob.getWorld().getEntitiesByClass(net.get900.pixelpirates.entity.custom.SharkEntity.class, mob.getBoundingBox().expand(32), LivingEntity::isAlive);
            if (list.size() >= cap) return;
            spawn.fire(mob, t);
        };
    }
}
