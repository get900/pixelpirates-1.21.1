package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.block.custom.FleshVeinBlock;
import net.get900.pixelpirates.block.custom.GalvanicPylonBlock;
import net.get900.pixelpirates.item.ModItems;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * BOSS 9/10 - THE ABYSSAL HEART, the heart of THALASSAR the Tide Father, cut from him by the ancients and sealed in a
 * dead titan's chest (world/dungeon/TitansChestLayout). While it beats, the Leviathan - Thalassar's old rival - is too
 * afraid to wake. Killing it opens the Rift.
 *
 * THE RHYTHM: everything runs on its heartbeat. Every lub-dub flares the vein lamps, sends a pressure ring across the
 * chest and surges the blood ring's current; its attacks are telegraphed on one beat and land on the next. The tempo
 * rises as it weakens (40 -> 30 ticks, enraged 28 -> 20).
 * THE PACEMAKER: it shrugs off 85% of every blow. Strike a GALVANIC PYLON while it glows green (a few ticks either side
 * of a beat) and it holds a charge for 16 s; with all four charged the heart goes into CARDIAC ARREST - 12 s of dark
 * and silence at x2 damage - then restarts with a knockback beat.
 * PHASE 2 (50%) INTO THE HEART: it clots shut (invulnerable) and swallows every player into its chambers under the floor
 * (teleport to the valve hub). Valve doorways slam on every beat (crushing anyone in them); a node in each chamber can
 * only be hurt ON THE BEAT; blood clots crawl out of the walls. All four nodes burst -> it RUPTURES: players are thrown
 * back into the chest and it is enraged.
 * PHASE 3 (25%) FLATLINE: it stops. The Eye of Thalassar opens behind the northern ribs and shocks its heart back to life
 * on an erratic rhythm - each shock a beat, most carrying a DROWNED MEMORY (the attacks of the bosses you have beaten).
 * On death it turns to stone, the Eye screams, the floor splits and the Last Keeper rises to curse you.
 * Co-op: with two or more fighters in the chest their relics fall silent (BossProgression.silenceRelics) - only the
 * Diving Charm's water breathing still answers.
 */
public class AbyssalHeartEntity extends ModBoss {
    public static final int BEATING = 0, CLOTTED = 1, RUPTURED = 2, FLATLINE = 3;
    private static final TrackedData<Integer> PHASE = DataTracker.registerData(AbyssalHeartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ARREST = DataTracker.registerData(AbyssalHeartEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    static final Vector3f BLOOD = new Vector3f(0.62f, 0.02f, 0.06f), WARN = new Vector3f(1.0f, 0.18f, 0.1f),
            BOLT = new Vector3f(0.6f, 0.9f, 1.0f), SOUL = new Vector3f(0.3f, 0.95f, 0.85f), GREEN = new Vector3f(0.35f, 1.0f, 0.4f);
    static final int CHARGE_TICKS = 320, ARREST_TICKS = 240, WINDOW_BEFORE = 5, WINDOW_AFTER = 6;
    static final double ARENA = 72;
    private static final RawAnimation ARREST_LOOP = RawAnimation.begin().thenLoop("arrest_still");
    private static final RawAnimation CLOT_LOOP = RawAnimation.begin().thenLoop("clotted");
    private static final RawAnimation FLAT_LOOP = RawAnimation.begin().thenLoop("flat");

    /** Everything the chest tells the heart (world coordinates), set by TitansChest before the spawn. */
    public record Lair(BlockPos floor, List<BlockPos> pylons, List<BlockPos> lamps, List<double[]> arteries,
                       List<List<BlockPos>> valves, List<BlockPos> nodes, List<BlockPos> clotSpots, List<BlockPos> hubSpots,
                       List<BlockPos> ejectSpots, BlockPos eye) {}

    private Lair lair;
    private Vec3d anchor;
    // ---- the rhythm
    private int nextBeatAt = 40, lastBeatAt = -100, dubAt = -1, lampsOffAt = -1, surge, ringAge = -1;
    private boolean windowShown, lampsLit, fighting, ringPushes = true, backflow;
    private int flatBeats;
    private final Map<BlockPos, Integer> charged = new HashMap<>();
    private int arrestTicks;
    // ---- attacks
    enum Attack { SYSTOLE, LASH, EMBOLISM, HEMORRHAGE, TRIPLE }
    private Attack pending, last;
    private int beatsToAttack = 3, bleedTicks, tripleLeft, tripleAt;
    private final List<double[]> lashLanes = new ArrayList<>();
    private final List<Running> tasks = new ArrayList<>();
    // ---- phase 2
    private int clotDelay, clotTimer, valveOpenAt = -1;
    private boolean valvesShut;
    private final List<UUID> nodes = new ArrayList<>(), clots = new ArrayList<>(), spawned = new ArrayList<>();
    // ---- phase 3
    private int flatTicks, shockAt, chargeAt = -1, memoryBusy, lastMemory = -1;
    // ---- bookkeeping
    private int hintCooldown, idleTicks, testFight;
    private final Set<UUID> warned = new HashSet<>(), greeted = new HashSet<>();
    private UUID eyeId;
    private int nextEyeLookup;
    private boolean stirred;

    public AbyssalHeartEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PHASE, BEATING);
        this.dataTracker.startTracking(ARREST, false);
    }

    public int phase() { return this.dataTracker.get(PHASE); }

    private void setPhase(int p) { this.dataTracker.set(PHASE, p); }

    public boolean inArrest() { return this.dataTracker.get(ARREST); }

    /** Worldgen (before the spawn): the chest's pylons, lamps, arteries, chambers and the Eye. */
    public void setLair(Lair l) {
        this.lair = l;
    }

    @Nullable
    public static AbyssalHeartEntity nearest(ServerWorld sw, BlockPos pos, double range) {
        var type = ModMobs.TYPES.get("abyssal_heart");
        if (type == null) return null;
        AbyssalHeartEntity best = null;
        double bd = range * range;
        for (Entity e : sw.getEntitiesByType(type, new Box(pos).expand(range), Entity::isAlive)) {
            double d = e.squaredDistanceTo(Vec3d.ofCenter(pos));
            if (e instanceof AbyssalHeartEntity h && d < bd) { bd = d; best = h; }
        }
        return best;
    }

    // ------------------------------------------------------------------ what the renderer asks
    @Override
    public String skinVariant() {
        if (this.isDead() || this.getHealth() <= 0) return "stone";
        if (inArrest()) return "arrest";
        return switch (phase()) {
            case CLOTTED -> "clotted";
            case FLATLINE -> "flatline";
            case RUPTURED -> "enraged";
            default -> null;
        };
    }

    @Override
    protected RawAnimation movementOverride() {
        if (inArrest()) return ARREST_LOOP;
        return switch (phase()) {
            case CLOTTED -> CLOT_LOOP;
            case FLATLINE -> FLAT_LOOP;
            default -> null;
        };
    }

    @Override
    protected List<String> extraAnims() {
        return List.of("systole", "diastole", "bleed", "clot", "arrest", "flatline", "lash", "petrify");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        super.registerControllers(controllers);
        // the heartbeat has its own controller so it never cuts an attack clip short
        AnimationController<AbyssalHeartEntity> pulse = new AnimationController<>(this, "pulse", 0, st -> PlayState.STOP);
        for (String a : List.of("beat", "jolt", "triple")) pulse.triggerableAnim(a, RawAnimation.begin().thenPlay(a));
        controllers.add(pulse);
    }

    @Override
    protected List<Abilities.Ability> activeAbilities() { return List.of(); }       // it fights on its own rhythm

    @Override
    protected boolean scriptedMotion() { return true; }

    @Override
    protected boolean healthEnrage() { return false; }                              // it enrages when it ruptures

    @Override
    protected double creditRange() { return 96; }

    @Override
    protected SoundEvent sound(String kind) {
        return switch (kind) {
            case "hurt" -> SoundEvents.ENTITY_SLIME_SQUISH;
            case "death" -> SoundEvents.ENTITY_WARDEN_DEATH;
            case "special" -> SoundEvents.ENTITY_WARDEN_ROAR;
            default -> null;
        };
    }

    @Override
    public float getSoundPitch() { return 0.45f + this.random.nextFloat() * 0.1f; }

    @Override
    public void heal(float amount) {
        if (phase() == CLOTTED) return;                              // no creeping back up while its crew is inside
        super.heal(amount);
    }

    @Override
    public boolean isPushedByFluids() { return false; }

    // ------------------------------------------------------------------ geometry
    Vec3d centre() { return getPos().add(0, getHeight() * 0.55, 0); }

    private int floorY() { return lair != null ? lair.floor().getY() : (int) getY() - 24; }

    private List<ServerPlayerEntity> fighters(ServerWorld sw) {
        Vec3d c = anchor != null ? anchor : getPos();
        return sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && mayTarget(p) && p.squaredDistanceTo(c) < ARENA * ARENA);
    }

    private void broadcast(ServerWorld sw, Text t, boolean actionbar) {
        Vec3d c = anchor != null ? anchor : getPos();
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(c) < 96 * 96)) p.sendMessage(t, actionbar);
    }

    // =====================================================================================
    // the tick
    // =====================================================================================
    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        pin();
        List<ServerPlayerEntity> fs = fighters(sw);
        tickFight(sw, fs);
        if (hintCooldown > 0) hintCooldown--;
        if (phase() == CLOTTED) tickClotted(sw, fs);
        if (phase() == FLATLINE) tickFlatline(sw);
        else if (arrestTicks > 0) tickArrest(sw);
        else if (this.age >= nextBeatAt) beat(sw, false);
        if (tripleLeft > 0 && this.age >= tripleAt) {
            beat(sw, true);
            if (--tripleLeft > 0) tripleAt = this.age + 6;
            else hallucinate(sw, fs);
        }
        if (this.age == dubAt) sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 3.0f, 0.6f);
        if (this.age == lampsOffAt) lamps(sw, false);
        if (valvesShut && this.age >= valveOpenAt) setValves(sw, false);
        tickRing(sw);
        tickPylons(sw);
        tickCurrent(sw);
        tickBleed(sw, fs);
        for (int i = tasks.size() - 1; i >= 0; i--) {
            Running r = tasks.get(i);
            if (!r.task.tick(sw, r.t++)) tasks.remove(i);
        }
        if (memoryBusy > 0) memoryBusy--;
    }

    /** Hung in its arteries: never moves, always faces the wound (east). */
    private void pin() {
        if (anchor == null) anchor = getPos();
        this.setVelocity(Vec3d.ZERO);
        if (this.squaredDistanceTo(anchor) > 0.01) this.setPosition(anchor);
        this.setYaw(-90f); this.setBodyYaw(-90f); this.setHeadYaw(-90f);
    }

    private void tickFight(ServerWorld sw, List<ServerPlayerEntity> fs) {
        if (testFight > 0) testFight--;
        fighting = !fs.isEmpty() || testFight > 0;                       // /ppboss heart keeps it fighting a minute with no one there
        idleTicks = fighting ? 0 : idleTicks + 1;
        if (fighting && !stirred) {
            RivalEyeEntity eye = eye(sw);
            stirred = eye != null || lair == null;
            if (eye != null) eye.stir();
            if (stirred) broadcast(sw, Text.literal("Something vast is still beating down here... and behind the northern ribs, something STIRS.")
                    .formatted(Formatting.DARK_RED, Formatting.ITALIC), false);
        }
        for (ServerPlayerEntity p : fs)
            if (greeted.add(p.getUuid()))
                p.sendMessage(Text.literal("The Heart shrugs off your blows while it pumps. Strike the four GALVANIC PYLONS as they flash green - ON the beat"
                        + " - and stop it dead.").formatted(Formatting.AQUA), false);
        if (this.age % 20 == 0 && fs.size() >= 2) {                      // co-op: the heart drowns out their relics
            for (ServerPlayerEntity p : fs) {
                BossProgression.silenceRelics(p, 60);
                if (warned.add(p.getUuid()))
                    p.sendMessage(Text.literal("Your relics fall silent - the Heart drowns out their power. Only the Diving Charm's breath remains.")
                            .formatted(Formatting.LIGHT_PURPLE), false);
            }
        }
        if (idleTicks == 1200 && phase() == CLOTTED) {                    // everyone left mid-phase: stop the clots festering
            discardAll(sw, clots);
            clots.clear();
        }
    }

    // =====================================================================================
    // THE RHYTHM
    // =====================================================================================
    int interval() {
        if (!fighting) return 50;
        float hp = getHealth() / getMaxHealth();
        return switch (phase()) {
            case BEATING -> 30 + Math.round(10 * MathHelper.clamp((hp - 0.5f) / 0.5f, 0, 1));
            case CLOTTED -> 34;
            case RUPTURED -> 20 + Math.round(8 * MathHelper.clamp((hp - 0.25f) / 0.25f, 0, 1));
            default -> 30;
        };
    }

    /** A few ticks either side of every beat: when the pylons glow green and the nodes can be hurt. */
    public boolean inBeatWindow() {
        return this.age - lastBeatAt <= WINDOW_AFTER || (nextBeatAt - this.age <= WINDOW_BEFORE && arrestTicks == 0 && phase() != FLATLINE);
    }

    /** One heartbeat. {@code extra}: a Triple Beat's extra beats (no attack bookkeeping). */
    void beat(ServerWorld sw, boolean extra) {
        lastBeatAt = this.age;
        if (!extra) nextBeatAt = this.age + interval();
        dubAt = this.age + 6;
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 5.0f, 0.45f);
        triggerAnim("pulse", extra ? "triple" : phase() == FLATLINE ? "jolt" : "beat");
        lamps(sw, true);
        lampsOffAt = this.age + 8;
        sw.spawnParticles(new DustParticleEffect(BLOOD, 2.2f), getX(), getY() + getHeight() * 0.5, getZ(), 30, 2.2, 2.5, 2.2, 0);
        if (fighting) {
            ringAge = 0; surge = 10;
            ringPushes = phase() != FLATLINE || flatBeats++ % 2 == 0;       // the flatline's jolts only shove every second beat
        }
        if (phase() == CLOTTED && clotDelay == 0) {
            setValves(sw, true);
            valveOpenAt = this.age + 12;
            for (UUID u : nodes) if (sw.getEntity(u) instanceof HeartNodeEntity n) n.triggerAnim(ACTION, "pulse");
        }
        if (extra || !fighting || arrestTicks > 0 || (phase() != BEATING && phase() != RUPTURED)) return;
        if (pending != null) { resolve(sw, pending); return; }
        if (--beatsToAttack <= 0) startAttack(sw);
    }

    private void lamps(ServerWorld sw, boolean lit) {
        if (lair == null || lampsLit == lit) return;
        lampsLit = lit;
        for (BlockPos p : lair.lamps()) {
            BlockState s = sw.getBlockState(p);
            if (s.isOf(ModBlocks.FLESH_VEIN) && s.get(FleshVeinBlock.LIT) != lit)
                sw.setBlockState(p, s.with(FleshVeinBlock.LIT, lit), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        }
    }

    /** The pressure ring rolling out from every beat: a gentle shove outward, a ring of blood-dust. */
    private void tickRing(ServerWorld sw) {
        if (ringAge < 0) return;
        double r = 3 + ringAge * 3;
        Vec3d c = centre();
        int n = (int) (r * 1.2);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            sw.spawnParticles(ParticleTypes.BUBBLE, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 0.2, 0, 0);
        }
        for (ServerPlayerEntity p : ringPushes ? fighters(sw) : List.<ServerPlayerEntity>of()) {
            double dx = p.getX() - c.x, dz = p.getZ() - c.z, d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - r) > 1.6 || Math.abs(p.getY() - c.y) > 6 || d < 0.1) continue;
            p.addVelocity(dx / d * 0.35, 0.05, dz / d * 0.35);
            p.velocityModified = true;
        }
        if (++ringAge > 11) ringAge = -1;
    }

    /** The blood ring's current: always flowing round (counter-clockwise from above), surging on the beat. */
    private void tickCurrent(ServerWorld sw) {
        if (lair == null) return;
        BlockPos f = lair.floor();
        if (surge > 0) surge--;
        double push = surge > 0 ? 0.09 : 0.03;
        Box box = new Box(f.getX() - 33, f.getY() - 5, f.getZ() - 33, f.getX() + 34, f.getY() + 1.5, f.getZ() + 34);
        for (LivingEntity e : sw.getEntitiesByClass(LivingEntity.class, box, e -> e.isAlive() && e != this && e.isTouchingWater())) {
            double dx = e.getX() - f.getX() - 0.5, dz = e.getZ() - f.getZ() - 0.5, r = Math.sqrt(dx * dx + dz * dz);
            if (r < 26 || r > 33 || e instanceof EmbolismEntity) continue;
            e.addVelocity(-dz / r * push, 0, dx / r * push);
            if (e instanceof ServerPlayerEntity) e.velocityModified = true;
        }
        if (surge == 9)
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI / 12;
                sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, f.getX() + 0.5 + Math.cos(a) * 29.5, f.getY() - 1, f.getZ() + 0.5 + Math.sin(a) * 29.5,
                        3, 0.4, 0.3, 0.4, 0.05);
            }
    }

    // =====================================================================================
    // THE PACEMAKER
    // =====================================================================================
    private boolean pylonsLive() {
        return lair != null && !lair.pylons().isEmpty() && arrestTicks == 0 && (phase() == BEATING || phase() == RUPTURED);
    }

    private void tickPylons(ServerWorld sw) {
        if (lair == null) return;
        boolean show = pylonsLive() && fighting && inBeatWindow();
        if (show != windowShown) {
            windowShown = show;
            for (BlockPos p : lair.pylons()) if (!charged.containsKey(p)) setPylon(sw, p, show ? GalvanicPylonBlock.WINDOW : GalvanicPylonBlock.IDLE);
        }
        if (charged.isEmpty()) return;
        List<BlockPos> spent = new ArrayList<>();
        charged.replaceAll((p, t) -> t - 1);
        charged.forEach((p, t) -> { if (t <= 0) spent.add(p); });
        for (BlockPos p : spent) {
            charged.remove(p);
            setPylon(sw, p, GalvanicPylonBlock.IDLE);
            sw.spawnParticles(ParticleTypes.SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
            sw.playSound(null, p, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.5f, 1.4f);
        }
        if (this.age % 4 == 0)
            for (BlockPos p : charged.keySet())
                sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.4, 0.4, 0.4, 0.05);
    }

    private void setPylon(ServerWorld sw, BlockPos p, int state) {
        BlockState s = sw.getBlockState(p);
        if (s.isOf(ModBlocks.GALVANIC_PYLON) && s.get(GalvanicPylonBlock.STATE) != state)
            sw.setBlockState(p, s.with(GalvanicPylonBlock.STATE, state), Block.NOTIFY_LISTENERS);
    }

    /** A player struck a pylon (GalvanicPylonBlock). */
    public void onPylonStruck(BlockPos pos, PlayerEntity player) {
        if (!(this.getWorld() instanceof ServerWorld sw) || lair == null || !lair.pylons().contains(pos)) return;
        if (!player.isCreative() && !mayTarget(player)) { player.sendMessage(BossProgression.sealedMessage(player, chainIndex()), true); return; }
        String why = phase() == CLOTTED ? "The heart has clotted shut - the fight is inside it now."
                : phase() == FLATLINE ? "There is no rhythm left to catch. The Eye keeps it beating now."
                : arrestTicks > 0 ? "The heart has already stopped - STRIKE IT!"
                : charged.containsKey(pos) ? "This coil already holds its charge." : null;
        if (why != null) { player.sendMessage(Text.literal(why).formatted(Formatting.GRAY), true); return; }
        if (!inBeatWindow()) {                                            // off the beat: the coil bites back
            player.damage(this.getDamageSources().magic(), 3f);
            Vec3d away = player.getPos().subtract(Vec3d.ofCenter(pos)).normalize();
            player.addVelocity(away.x * 0.8, 0.3, away.z * 0.8);
            player.velocityModified = true;
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.2);
            sw.playSound(null, pos, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 1.5f, 1.8f);
            player.sendMessage(Text.literal("Off the beat! Wait for the coil to flash GREEN, then strike.").formatted(Formatting.RED), true);
            return;
        }
        charged.put(pos.toImmutable(), CHARGE_TICKS);
        setPylon(sw, pos, GalvanicPylonBlock.CHARGED);
        bolt(sw, Vec3d.ofCenter(pos), centre(), BOLT);
        sw.playSound(null, pos, SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.BLOCKS, 2.0f, 1.3f);
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.HOSTILE, 2.5f, 0.7f);
        RivalEyeEntity eye = eye(sw);
        if (eye != null) eye.flinch();
        int n = charged.size(), of = lair.pylons().size();
        broadcast(sw, Text.literal("Bolt struck! " + n + "/" + of + " coils charged").formatted(Formatting.AQUA, Formatting.BOLD), true);
        if (n >= of) arrest(sw);
    }

    private void arrest(ServerWorld sw) {
        arrestTicks = ARREST_TICKS;
        this.dataTracker.set(ARREST, true);
        pending = null;
        lashLanes.clear();
        tasks.clear();
        for (BlockPos p : List.copyOf(charged.keySet())) setPylon(sw, p, GalvanicPylonBlock.IDLE);
        charged.clear();
        windowShown = false;
        lamps(sw, false);
        triggerAnim(ACTION, "arrest");
        Vec3d c = centre();
        for (BlockPos p : lair.pylons()) bolt(sw, Vec3d.ofCenter(p), c, BOLT);
        sw.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 3.0f, 0.8f);
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 3.0f, 0.4f);
        for (ServerPlayerEntity p : fighters(sw)) p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 50, 0), this);
        RivalEyeEntity eye = eye(sw);
        if (eye != null) eye.glare();
        broadcast(sw, Text.literal("CARDIAC ARREST! The heart has stopped - strike it NOW!").formatted(Formatting.GOLD, Formatting.BOLD), true);
    }

    private void tickArrest(ServerWorld sw) {
        if (this.age % 10 == 0) {
            Vec3d c = centre();
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 6, 2, 2.5, 2, 0.05);
        }
        if (arrestTicks == 60) broadcast(sw, Text.literal("The heart twitches... it's coming back!").formatted(Formatting.RED), true);
        if (--arrestTicks > 0) return;
        this.dataTracker.set(ARREST, false);                    // RESTART: a huge beat that throws everyone back
        beat(sw, false);
        beatsToAttack = 2;
        Vec3d c = centre();
        sw.spawnParticles(ParticleTypes.SONIC_BOOM, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 3.0f, 0.6f);
        for (ServerPlayerEntity p : fighters(sw)) {
            Vec3d d = p.getPos().subtract(c);
            double dist = d.length();
            if (dist > 26) continue;
            Vec3d u = dist < 0.1 ? new Vec3d(1, 0, 0) : d.normalize();
            double k = 2.0 * (1 - dist / 28);
            p.setVelocity(u.x * k, 0.4 + u.y * k * 0.5, u.z * k);
            p.velocityModified = true;
            if (dist < 12) p.damage(this.getDamageSources().mobAttack(this), 6f);
        }
        broadcast(sw, Text.literal("The heart JOLTS back to life!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
    }

    // =====================================================================================
    // the attacks (telegraphed on one beat, landing on the next)
    // =====================================================================================
    private void startAttack(ServerWorld sw) {
        beatsToAttack = phase() == RUPTURED ? 2 : 3;
        Attack a = pick();
        if (a == null) return;
        last = a;
        pending = a;
        switch (a) {
            case SYSTOLE -> {
                triggerAnim(ACTION, "systole");
                sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.HOSTILE, 3.0f, 0.4f);
                broadcast(sw, Text.literal("SYSTOLE - the heart DRAWS IN! Swim away before it blows!").formatted(Formatting.RED, Formatting.BOLD), true);
                tasks.add(new Running((w, t) -> {
                    if (pending != Attack.SYSTOLE) return false;
                    Vec3d c = centre();
                    for (ServerPlayerEntity p : fighters(w)) {
                        Vec3d d = c.subtract(p.getPos());
                        double dist = d.length();
                        if (dist > 34 || dist < 1) continue;
                        double k = 0.05 * (1 - dist / 40) * (BossProgression.relicActive(p, ModItems.ROYAL_TIDE_SIGIL) ? 0.5 : 1);
                        p.addVelocity(d.x / dist * k, d.y / dist * k, d.z / dist * k);
                        p.velocityModified = true;
                        if (t % 3 == 0) w.spawnParticles(ParticleTypes.BUBBLE, p.getX(), p.getY() + 1, p.getZ(), 3, 0.5, 0.5, 0.5, 0);
                    }
                    return true;
                }));
            }
            case LASH -> {
                lashLanes.clear();
                List<double[]> all = new ArrayList<>(arteryLanes());
                java.util.Collections.shuffle(all, new java.util.Random(this.random.nextLong()));
                for (int i = 0; i < Math.min(all.size(), phase() == RUPTURED ? 3 : 2); i++) lashLanes.add(all.get(i));
                triggerAnim(ACTION, "lash");
                broadcast(sw, Text.literal("VEIN LASH - the marked arteries tense! Get clear of them!").formatted(Formatting.RED, Formatting.BOLD), true);
                tasks.add(new Running((w, t) -> {
                    if (pending != Attack.LASH) return false;
                    if (t % 2 == 0) for (double[] l : lashLanes) line(w, l, new DustParticleEffect(WARN, t > 18 ? 2.6f : 1.9f), 0.9, 2);
                    return true;
                }));
            }
            case HEMORRHAGE -> {
                triggerAnim(ACTION, "bleed");
                broadcast(sw, Text.literal("The heart shudders - it is going to BLEED...").formatted(Formatting.DARK_RED), true);
            }
            case EMBOLISM, TRIPLE -> resolve(sw, a);
        }
    }

    private void resolve(ServerWorld sw, Attack a) {
        pending = null;
        switch (a) {
            case SYSTOLE -> diastole(sw);
            case LASH -> lash(sw);
            case EMBOLISM -> embolism(sw);
            case HEMORRHAGE -> {
                bleedTicks = 160;
                broadcast(sw, Text.literal("HEMORRHAGE - blood pours from the heart and fills the water!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
            }
            case TRIPLE -> {
                beat(sw, true);
                tripleLeft = 2;
                tripleAt = this.age + 6;
            }
        }
    }

    @Nullable
    private Attack pick() {
        if (forced != null) return forced;
        List<Attack> pool = new ArrayList<>();
        add(pool, Attack.SYSTOLE, 3);
        add(pool, Attack.LASH, 3);
        if (lair != null) add(pool, Attack.EMBOLISM, 2);
        add(pool, Attack.HEMORRHAGE, 1);
        add(pool, Attack.TRIPLE, phase() == RUPTURED ? 2 : 1);
        pool.removeIf(x -> x == last && pool.stream().anyMatch(y -> y != last));
        return pool.isEmpty() ? null : pool.get(this.random.nextInt(pool.size()));
    }

    private static void add(List<Attack> pool, Attack a, int w) { for (int i = 0; i < w; i++) pool.add(a); }

    /** DIASTOLE: the blast after the draw - everyone thrown outward, hard hits close in. */
    private void diastole(ServerWorld sw) {
        triggerAnim(ACTION, "diastole");
        Vec3d c = centre();
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 2.5f, 0.5f);
        sw.spawnParticles(ParticleTypes.BUBBLE_POP, c.x, c.y, c.z, 200, 6, 6, 6, 0.6);
        for (ServerPlayerEntity p : fighters(sw)) {
            Vec3d d = p.getPos().subtract(c);
            double dist = d.length();
            if (dist > 32) continue;
            Vec3d u = dist < 0.1 ? new Vec3d(1, 0, 0) : d.normalize();
            double k = Math.max(0.3, 1.8 * (1 - dist / 34));
            p.setVelocity(u.x * k, u.y * k + 0.2, u.z * k);
            p.velocityModified = true;
            if (dist < 10) p.damage(this.getDamageSources().mobAttack(this), isEnraged() ? 11f : 9f);
        }
    }

    /** The artery segments (the chest's, or six made up for an egg-spawned heart). */
    private List<double[]> arteryLanes() {
        if (lair != null && !lair.arteries().isEmpty()) return lair.arteries();
        List<double[]> out = new ArrayList<>();
        Vec3d c = centre();
        for (int k = 0; k < 6; k++) {
            double a = Math.PI / 6 + k * Math.PI / 3;
            out.add(new double[]{c.x, c.y, c.z, c.x + Math.cos(a) * 30, c.y + 12, c.z + Math.sin(a) * 30});
        }
        return out;
    }

    private void lash(ServerWorld sw) {
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 3.0f, 0.5f);
        for (double[] l : lashLanes) line(sw, l, ParticleTypes.CRIT, 0.7, 4);
        for (ServerPlayerEntity p : fighters(sw)) {
            for (double[] l : lashLanes) {
                Vec3d a = new Vec3d(l[0], l[1], l[2]), b = new Vec3d(l[3], l[4], l[5]);
                Vec3d q = closest(a, b, p.getPos().add(0, 0.9, 0));
                Vec3d off = p.getPos().add(0, 0.9, 0).subtract(q);
                if (off.length() > 3.5) continue;
                p.damage(this.getDamageSources().mobAttack(this), isEnraged() ? 15f : 12f);
                Vec3d u = off.lengthSquared() < 1e-4 ? new Vec3d(0, 1, 0) : off.normalize();
                p.setVelocity(u.x * 1.3, 0.4 + u.y * 0.6, u.z * 1.3);
                p.velocityModified = true;
                break;
            }
        }
        lashLanes.clear();
    }

    static Vec3d closest(Vec3d a, Vec3d b, Vec3d p) {
        Vec3d ab = b.subtract(a);
        double t = MathHelper.clamp(p.subtract(a).dotProduct(ab) / ab.lengthSquared(), 0, 1);
        return a.add(ab.multiply(t));
    }

    private void line(ServerWorld sw, double[] l, ParticleEffect fx, double step, int count) {
        Vec3d a = new Vec3d(l[0], l[1], l[2]), b = new Vec3d(l[3], l[4], l[5]);
        double len = a.distanceTo(b);
        for (double s = 0; s <= len; s += step) {
            Vec3d q = a.add(b.subtract(a).multiply(s / len));
            sw.spawnParticles(fx, q.x, q.y, q.z, count, 0.3, 0.3, 0.3, 0);
        }
    }

    /** A zig-zag bolt of light between two points. */
    void bolt(ServerWorld sw, Vec3d a, Vec3d b, Vector3f color) {
        double len = a.distanceTo(b);
        Vec3d prev = a;
        int n = Math.max(4, (int) (len / 1.2));
        for (int i = 1; i <= n; i++) {
            Vec3d q = a.add(b.subtract(a).multiply((double) i / n));
            if (i < n) q = q.add((this.random.nextDouble() - 0.5) * 1.2, (this.random.nextDouble() - 0.5) * 1.2, (this.random.nextDouble() - 0.5) * 1.2);
            double seg = prev.distanceTo(q);
            for (double s = 0; s <= seg; s += 0.35) {
                Vec3d r = prev.add(q.subtract(prev).multiply(s / seg));
                sw.spawnParticles(new DustParticleEffect(color, 1.2f), r.x, r.y, r.z, 1, 0, 0, 0, 0);
            }
            sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, q.x, q.y, q.z, 2, 0.1, 0.1, 0.1, 0.05);
            prev = q;
        }
    }

    private void embolism(ServerWorld sw) {
        var type = ModMobs.TYPES.get("embolism");
        if (type == null || lair == null) return;
        BlockPos f = lair.floor();
        for (int i = 0; i < (phase() == RUPTURED ? 2 : 1); i++) {
            if (!(type.create(sw) instanceof EmbolismEntity e)) continue;
            e.setRing(f.getX() + 0.5, f.getZ() + 0.5, 29.5, f.getY() - 2, this.random.nextDouble() * Math.PI * 2, isEnraged() ? 0.5 : 0.36);
            e.setHeart(this.getUuid());
            e.initialize(sw, sw.getLocalDifficulty(e.getBlockPos()), SpawnReason.MOB_SUMMONED, null, null);
            sw.spawnEntity(e);
            spawned.add(e.getUuid());
        }
        sw.playSound(null, f, SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 3.0f, 0.4f);
        broadcast(sw, Text.literal("An EMBOLISM breaks loose into the blood ring!").formatted(Formatting.RED), true);
    }

    private void tickBleed(ServerWorld sw, List<ServerPlayerEntity> fs) {
        if (bleedTicks <= 0) return;
        bleedTicks--;
        double r = Math.min(22, 4 + (160 - bleedTicks) * 0.3);
        Vec3d c = centre();
        if (this.age % 4 == 0)
            sw.spawnParticles(new DustParticleEffect(BLOOD, 3.5f), c.x, c.y, c.z, 60, r * 0.5, r * 0.4, r * 0.5, 0);
        if (this.age % 10 == 0)
            for (ServerPlayerEntity p : fs) {
                if (p.getPos().distanceTo(c) > r) continue;
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 60, 0), this);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0), this);
            }
    }

    /** A player hacked an embolism apart: the backflow tears at the heart - 2% of its health, straight through its guard. */
    void onEmbolismKilled(EmbolismEntity e) {
        if (!(this.getWorld() instanceof ServerWorld sw) || !isAlive() || phase() == CLOTTED) return;
        bolt(sw, e.getPos().add(0, 0.6, 0), centre(), BLOOD);
        sw.spawnParticles(new DustParticleEffect(BLOOD, 3f), getX(), getY() + getHeight() * 0.5, getZ(), 80, 2, 2.5, 2, 0);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_HURT, SoundCategory.HOSTILE, 3.0f, 0.5f);
        broadcast(sw, Text.literal("The embolism bursts - the backflow tears at the heart!").formatted(Formatting.GOLD), true);
        backflow = true;
        this.timeUntilRegen = 0;                                             // never eaten by the hit cooldown
        damage(this.getDamageSources().magic(), getMaxHealth() * 0.02f);
        backflow = false;
    }

    // ---------------------------------------------------------------- TRIPLE BEAT: hallucinations
    private void hallucinate(ServerWorld sw, List<ServerPlayerEntity> fs) {
        var type = ModMobs.TYPES.get("heart_phantasm");
        if (type == null) return;
        for (ServerPlayerEntity p : fs) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 140, 0), this);
            p.sendMessage(Text.literal("Three beats... and suddenly you are not alone down here.").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC), true);
            for (int i = 0; i < 3; i++) {
                if (!(type.create(sw) instanceof HeartPhantasmEntity ph)) continue;
                Vec3d at = null;
                for (int k = 0; k < 8 && at == null; k++) {
                    double a = this.random.nextDouble() * Math.PI * 2, d = 5 + this.random.nextDouble() * 4;
                    BlockPos q = BlockPos.ofFloored(p.getX() + Math.cos(a) * d, p.getY() + this.random.nextInt(3) - 1, p.getZ() + Math.sin(a) * d);
                    if (sw.getBlockState(q).getCollisionShape(sw, q).isEmpty() && sw.getBlockState(q.up()).getCollisionShape(sw, q.up()).isEmpty())
                        at = Vec3d.ofBottomCenter(q);
                }
                if (at == null) continue;
                ph.refreshPositionAndAngles(at.x, at.y, at.z, this.random.nextFloat() * 360, 0);
                ph.haunt(p);
                sw.spawnEntity(ph);
                spawned.add(ph.getUuid());
            }
        }
    }

    // =====================================================================================
    // PHASE 2 - INTO THE HEART
    // =====================================================================================
    private void startClot(ServerWorld sw) {
        setPhase(CLOTTED);
        clotDelay = 50;
        arrestTicks = 0;
        this.dataTracker.set(ARREST, false);
        pending = null; lashLanes.clear(); tasks.clear(); bleedTicks = 0;
        for (BlockPos p : List.copyOf(charged.keySet())) setPylon(sw, p, GalvanicPylonBlock.IDLE);
        charged.clear();
        triggerAnim(ACTION, "clot");
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_ROAR, SoundCategory.HOSTILE, 3.0f, 0.5f);
        bossBar().setName(this.getDisplayName().copy().append(Text.literal(" - Clotted").formatted(Formatting.DARK_RED)));
        broadcast(sw, Text.literal("The heart CLOTS SHUT... and its valves open beneath it. It is pulling you IN!").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
    }

    private boolean inChambers(Entity p) {
        if (lair == null) return false;
        BlockPos f = lair.floor();
        return p.getY() < f.getY() - 10 && Math.abs(p.getX() - f.getX()) < 34 && Math.abs(p.getZ() - f.getZ()) < 34;
    }

    private void tickClotted(ServerWorld sw, List<ServerPlayerEntity> fs) {
        Vec3d c = centre();
        if (clotDelay > 0) {
            if (this.age % 2 == 0) sw.spawnParticles(ParticleTypes.BUBBLE, c.x, c.y - 4, c.z, 30, 8, 4, 8, 0.1);
            for (ServerPlayerEntity p : fs) {                                 // the undertow toward the valves under it
                Vec3d d = c.add(0, -4, 0).subtract(p.getPos());
                double dist = d.length();
                if (dist > 1 && dist < 40) { p.addVelocity(d.x / dist * 0.04, d.y / dist * 0.04, d.z / dist * 0.04); p.velocityModified = true; }
            }
            if (--clotDelay > 0) return;
            if (lair == null || lair.nodes().isEmpty() || lair.hubSpots().isEmpty()) { rupture(sw); return; }
            spawnNodes(sw);
            for (ServerPlayerEntity p : fs) swallow(sw, p);
            nextBeatAt = this.age + 20;
            return;
        }
        // latecomers (and the respawned) follow the rest in through the valve under the heart
        for (ServerPlayerEntity p : fs) {
            if (inChambers(p)) continue;
            double dx = p.getX() - c.x, dz = p.getZ() - c.z;
            if (dx * dx + dz * dz < 7 * 7 && p.getY() < getY() + 1 && p.getY() > floorY() - 2) swallow(sw, p);
            else if (this.age % 100 == 0) p.sendMessage(Text.literal("The heart has swallowed the fight - swim under it, through its valves.").formatted(Formatting.GRAY), true);
        }
        if (this.age % 5 == 0) sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, c.x, floorY() + 0.5, c.z, 8, 2.5, 0.2, 2.5, 0.1);
        // blood clots crawl out of the chamber walls
        if (++clotTimer >= 160 && lair != null && !lair.clotSpots().isEmpty() && fs.stream().anyMatch(this::inChambers)) {
            clotTimer = 0;
            clots.removeIf(u -> { Entity e = sw.getEntity(u); return e == null || !e.isAlive(); });
            var type = ModMobs.TYPES.get("blood_clot");
            if (type != null && clots.size() < 6) {
                BlockPos s = lair.clotSpots().get(this.random.nextInt(lair.clotSpots().size()));
                ModMob clot = type.create(sw);
                if (clot != null) {
                    clot.refreshPositionAndAngles(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, this.random.nextFloat() * 360, 0);
                    clot.initialize(sw, sw.getLocalDifficulty(s), SpawnReason.MOB_SUMMONED, null, null);
                    sw.spawnEntity(clot);
                    clots.add(clot.getUuid());
                    sw.spawnParticles(new DustParticleEffect(BLOOD, 2f), s.getX() + 0.5, s.getY() + 0.5, s.getZ() + 0.5, 30, 0.6, 0.6, 0.6, 0);
                    sw.playSound(null, s, SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 1.5f, 0.6f);
                }
            }
        }
        if (this.age % 40 == 0) {                                              // a node lost some other way still counts
            nodes.removeIf(u -> !(sw.getEntity(u) instanceof HeartNodeEntity n) || !n.isAlive());
            if (nodes.isEmpty() && isChunkLoaded(sw)) rupture(sw);
        }
    }

    private static void discardAll(ServerWorld sw, List<UUID> ids) {
        for (UUID u : ids) {
            Entity e = sw.getEntity(u);
            if (e != null) e.discard();
        }
    }

    private boolean isChunkLoaded(ServerWorld sw) {
        if (lair == null) return true;
        for (BlockPos n : lair.nodes()) if (!sw.isChunkLoaded(n)) return false;
        return true;
    }

    private void spawnNodes(ServerWorld sw) {
        var type = ModMobs.TYPES.get("heart_node");
        if (type == null) return;
        nodes.clear();
        for (BlockPos s : lair.nodes()) {
            if (!(type.create(sw) instanceof HeartNodeEntity n)) continue;
            n.refreshPositionAndAngles(s.getX() + 0.5, s.getY(), s.getZ() + 0.5, 0, 0);
            n.initialize(sw, sw.getLocalDifficulty(s), SpawnReason.MOB_SUMMONED, null, null);
            n.setHeart(this.getUuid());
            sw.spawnEntity(n);
            nodes.add(n.getUuid());
        }
        bossBar().setName(this.getDisplayName().copy().append(Text.literal(" - Clotted: " + nodes.size() + " nodes").formatted(Formatting.DARK_RED)));
    }

    private void swallow(ServerWorld sw, ServerPlayerEntity p) {
        BlockPos s = lair.hubSpots().get(this.random.nextInt(lair.hubSpots().size()));
        p.teleport(sw, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYaw(), 0);
        p.setVelocity(Vec3d.ZERO);
        p.velocityModified = true;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), this);
        sw.playSound(null, s, SoundEvents.ENTITY_SLIME_SQUISH, SoundCategory.HOSTILE, 2.0f, 0.4f);
        sw.playSound(null, s, SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.HOSTILE, 2.0f, 0.5f);
        p.sendMessage(Text.literal("You are swallowed into the heart. Burst its four nodes - they only yield ON THE BEAT.").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
    }

    /** The valve doorways slam shut on the beat (crushing anyone standing in them) and open 12 ticks later. */
    private void setValves(ServerWorld sw, boolean shut) {
        if (lair == null) return;
        valvesShut = shut;
        for (List<BlockPos> door : lair.valves()) {
            if (door.isEmpty()) continue;
            for (BlockPos p : door) {
                BlockState s = sw.getBlockState(p);
                if (shut && s.isAir()) sw.setBlockState(p, ModBlocks.HEART_VALVE.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
                else if (!shut && s.isOf(ModBlocks.HEART_VALVE)) sw.setBlockState(p, Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            }
            if (!shut) continue;
            BlockPos mid = door.get(door.size() / 2);
            sw.playSound(null, mid, SoundEvents.BLOCK_PISTON_CONTRACT, SoundCategory.HOSTILE, 1.2f, 0.5f);
            Box box = null;
            for (BlockPos p : door) box = box == null ? new Box(p) : box.union(new Box(p));
            for (ServerPlayerEntity pl : sw.getEntitiesByClass(ServerPlayerEntity.class, box, pl -> pl.isAlive() && !pl.isSpectator())) {
                boolean in = false;
                for (BlockPos p : door) if (pl.getBoundingBox().intersects(new Box(p))) { in = true; break; }
                if (!in) continue;
                pl.damage(this.getDamageSources().mobAttack(this), 8f);
                Vec3d away = pl.getPos().subtract(Vec3d.ofCenter(mid)).multiply(1, 0, 1);
                if (away.lengthSquared() < 1e-3) away = new Vec3d(this.random.nextDouble() - 0.5, 0, this.random.nextDouble() - 0.5);
                away = away.normalize();
                pl.setVelocity(away.x * 0.9, 0.3, away.z * 0.9);
                pl.velocityModified = true;
                pl.sendMessage(Text.literal("CRUSHED by the valve - pass between the beats!").formatted(Formatting.RED), true);
            }
        }
    }

    /** A node burst (HeartNodeEntity#onDeath). */
    void onNodeDestroyed(HeartNodeEntity n) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        nodes.remove(n.getUuid());
        int left = 0;
        for (UUID u : nodes) if (sw.getEntity(u) instanceof HeartNodeEntity x && x.isAlive()) left++;
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_HURT, SoundCategory.HOSTILE, 3.0f, 0.5f);
        broadcast(sw, Text.literal("A node BURSTS! " + (lair != null ? lair.nodes().size() - left : 0) + "/" + (lair != null ? lair.nodes().size() : 0))
                .formatted(Formatting.GOLD, Formatting.BOLD), true);
        bossBar().setName(this.getDisplayName().copy().append(Text.literal(" - Clotted: " + left + " nodes").formatted(Formatting.DARK_RED)));
        if (left == 0) rupture(sw);
    }

    /** All the nodes are gone: the heart RUPTURES, throws everyone back into the chest and is enraged. */
    private void rupture(ServerWorld sw) {
        if (phase() != CLOTTED) return;
        setPhase(RUPTURED);
        clotDelay = 0;
        setValves(sw, false);
        discardAll(sw, clots);
        clots.clear();
        discardAll(sw, nodes);
        nodes.clear();
        List<ServerPlayerEntity> inside = sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && inChambers(p));
        int i = 0;
        for (ServerPlayerEntity p : inside) {
            BlockPos s = lair != null && !lair.ejectSpots().isEmpty() ? lair.ejectSpots().get(i++ % lair.ejectSpots().size()) : getBlockPos().up(6);
            p.teleport(sw, s.getX() + 0.5, s.getY(), s.getZ() + 0.5, p.getYaw(), 0);
            p.setVelocity(0, 0.6, 0);
            p.velocityModified = true;
        }
        Vec3d c = centre();
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 2, 1, 1, 1, 0);
        sw.spawnParticles(new DustParticleEffect(BLOOD, 3f), c.x, c.y, c.z, 400, 6, 6, 6, 0);
        forceEnrage();
        beatsToAttack = 2;
        nextBeatAt = this.age + 20;
        broadcast(sw, Text.literal("The heart RUPTURES and spits you out - it is ENRAGED, and it is racing!").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
    }

    // =====================================================================================
    // PHASE 3 - FLATLINE, and the Eye of Thalassar
    // =====================================================================================
    private void startFlatline(ServerWorld sw) {
        setPhase(FLATLINE);
        flatTicks = 0;
        pending = null; lashLanes.clear(); tasks.clear(); bleedTicks = 0; arrestTicks = 0;
        this.dataTracker.set(ARREST, false);
        for (BlockPos p : List.copyOf(charged.keySet())) setPylon(sw, p, GalvanicPylonBlock.IDLE);
        charged.clear();
        lamps(sw, false);
        triggerAnim(ACTION, "flatline");
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.HOSTILE, 3.0f, 0.3f);
        bossBar().setName(this.getDisplayName().copy().append(Text.literal(" - FLATLINE").formatted(Formatting.GRAY)));
        broadcast(sw, Text.literal("The heart... stops. Silence.").formatted(Formatting.GRAY, Formatting.ITALIC), false);
    }

    private void tickFlatline(ServerWorld sw) {
        flatTicks++;
        RivalEyeEntity eye = eye(sw);
        if (flatTicks == 40) {
            if (eye != null) eye.open();
            broadcast(sw, Text.literal("Behind the northern ribs, the EYE OF THALASSAR opens. The Tide Father will not let his heart die!")
                    .formatted(Formatting.AQUA, Formatting.BOLD), false);
        }
        if (flatTicks < 100) return;
        if (flatTicks == 100) shockAt = this.age;
        if (this.age == chargeAt && eye != null) eye.charge();
        if (this.age < shockAt) return;
        shock(sw, eye);
        shockAt = this.age + 16 + this.random.nextInt(isEnraged() ? 30 : 40);   // erratic
        chargeAt = shockAt - 10;
    }

    /** The Eye shocks its heart: a bolt through the water, the heart jolts (a beat), and a drowned memory surfaces. */
    private void shock(ServerWorld sw, @Nullable RivalEyeEntity eye) {
        Vec3d c = centre();
        Vec3d from = eye != null ? eye.getPos().add(0, eye.getHeight() * 0.5, 0) : c.add(0, 22, 0);
        bolt(sw, from, c, BOLT);
        sw.spawnParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.HOSTILE, 2.5f, 1.3f);
        for (ServerPlayerEntity p : fighters(sw))
            if (p.getPos().distanceTo(c) < 7) p.damage(this.getDamageSources().magic(), 6f);          // live water round the heart
        heal(getMaxHealth() * 0.005f);
        beat(sw, false);
        if (memoryBusy == 0 && this.random.nextFloat() < 0.7f) memory(sw, -1);
    }

    // ---------------------------------------------------------------- DROWNED MEMORIES: the bosses you've beaten, one per shock
    static final String[] MEMORIES = DrownedMemories.NAMES;

    private final DrownedMemories.Host memoryHost = new DrownedMemories.Host() {
        @Override public LivingEntity self() { return AbyssalHeartEntity.this; }
        @Override public Vec3d centre() { return AbyssalHeartEntity.this.centre(); }
        @Override public List<ServerPlayerEntity> fighters(ServerWorld sw) { return AbyssalHeartEntity.this.fighters(sw); }
        @Override public void schedule(DrownedMemories.Task task) { tasks.add(new Running(task::tick)); }
    };

    void memory(ServerWorld sw, int which) {
        List<ServerPlayerEntity> fs = fighters(sw);
        if (fs.isEmpty()) return;
        int m = which;
        if (m < 0) { do m = this.random.nextInt(MEMORIES.length); while (m == lastMemory); }
        lastMemory = m;
        memoryBusy = 50;
        broadcast(sw, Text.literal("A drowned memory surfaces in the blood: " + MEMORIES[m] + "!").formatted(Formatting.DARK_AQUA, Formatting.BOLD), true);
        DrownedMemories.play(memoryHost, sw, m, fs, fs.get(this.random.nextInt(fs.size())));
    }

    // =====================================================================================
    // damage + phase gates
    // =====================================================================================
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (!(this.getWorld() instanceof ServerWorld sw)) return false;
        if (phase() == CLOTTED) {
            if (source.getAttacker() instanceof ServerPlayerEntity p && hintCooldown == 0) {
                hintCooldown = 60;
                p.sendMessage(Text.literal("The heart has clotted shut - the fight is INSIDE it now. Swim under it.").formatted(Formatting.GRAY), true);
            }
            return false;
        }
        float mult;
        if (backflow) mult = 1f;                                              // a burst embolism's backflow (onEmbolismKilled)
        else if (phase() == FLATLINE) mult = 0.8f;
        else if (arrestTicks > 0) mult = 2f;
        else mult = lair != null && !lair.pylons().isEmpty() ? 0.15f : 0.5f;
        if (mult < 1 && phase() != FLATLINE && !backflow && source.getAttacker() instanceof ServerPlayerEntity p && hintCooldown == 0 && mayTarget(p)) {
            hintCooldown = 80;
            p.sendMessage(Text.literal("The heart pumps too hard to wound - stop it with the Galvanic Pylons!").formatted(Formatting.GRAY), true);
        }
        boolean hurt = super.damage(source, amount * mult);
        if (!hurt || !isAlive()) return hurt;
        if (amount * mult >= 10) { RivalEyeEntity eye = eye(sw); if (eye != null) eye.flinch(); }
        float max = getMaxHealth();
        if (phase() == BEATING && getHealth() <= max * 0.5f) {
            setHealth(max * 0.5f);
            if (lair != null && !lair.nodes().isEmpty()) startClot(sw);
            else { setPhase(CLOTTED); rupture(sw); }                          // no chambers (egg): straight to the rupture
        } else if (phase() == RUPTURED && getHealth() <= max * 0.25f) {
            setHealth(max * 0.25f);
            startFlatline(sw);
        }
        return hurt;
    }

    // =====================================================================================
    // death: stone, the Eye's scream, the split floor and the Last Keeper
    // =====================================================================================
    @Override
    public void onDeath(DamageSource source) {
        if (this.getWorld() instanceof ServerWorld sw) {
            discardAll(sw, nodes);
            discardAll(sw, clots);
            discardAll(sw, spawned);
            tasks.clear();
            setValves(sw, false);
            lampsLit = true;
            lamps(sw, false);
            if (lair != null) for (BlockPos p : lair.pylons()) setPylon(sw, p, GalvanicPylonBlock.IDLE);
            triggerAnim(ACTION, "petrify");
            RivalEyeEntity eye = eye(sw);
            if (eye != null) eye.scream();
            sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_STONE_BREAK, SoundCategory.HOSTILE, 3.0f, 0.4f);
            broadcast(sw, Text.literal("The heart turns to STONE. Behind the ribs, the Eye of Thalassar SCREAMS.").formatted(Formatting.DARK_AQUA, Formatting.BOLD), false);
            summonKeeper(sw);
        }
        super.onDeath(source);
    }

    @Override
    protected void updatePostDeath() {
        super.updatePostDeath();
        if (this.deathTime == 18 && this.getWorld() instanceof ServerWorld sw) petrify(sw);
    }

    /** What is left: a heart of stone hanging in its arteries forever. */
    private void petrify(ServerWorld sw) {
        Vec3d c = centre();
        BlockPos.Mutable m = new BlockPos.Mutable();
        for (int x = -4; x <= 4; x++) for (int y = -5; y <= 5; y++) for (int z = -4; z <= 4; z++) {
            double d = (x / 3.2) * (x / 3.2) + ((y + 0.5) / 4.2) * ((y + 0.5) / 4.2) + (z / 3.0) * (z / 3.0);
            double atria = ((x + 1.5) / 1.8) * ((x + 1.5) / 1.8) + ((y - 3.5) / 1.6) * ((y - 3.5) / 1.6) + (z / 1.8) * (z / 1.8);
            if (d > 1 && atria > 1) continue;
            m.set(c.x + x, c.y + y, c.z + z);
            if (!sw.getBlockState(m).isOf(Blocks.WATER)) continue;
            int h = Math.floorMod(x * 7 + y * 13 + z * 5, 11);
            BlockState s = h < 5 ? Blocks.TUFF.getDefaultState() : h < 8 ? Blocks.POLISHED_DEEPSLATE.getDefaultState()
                    : h < 10 ? Blocks.CALCITE.getDefaultState() : ModBlocks.LUMINOUS_VEIN.getDefaultState();
            sw.setBlockState(m, s, Block.NOTIFY_LISTENERS);
        }
        sw.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.TUFF.getDefaultState()), c.x, c.y, c.z, 200, 3, 4, 3, 0.2);
    }

    private void summonKeeper(ServerWorld sw) {
        var type = ModMobs.TYPES.get("drowned_keeper");
        if (type == null || !(type.create(sw) instanceof DrownedKeeperEntity k)) return;
        BlockPos f = lair != null ? lair.floor() : BlockPos.ofFloored(getX(), floorY(), getZ());
        double a = this.random.nextDouble() * Math.PI * 2;
        Vec3d dir = new Vec3d(Math.cos(a), 0, Math.sin(a));
        Vec3d at = Vec3d.ofBottomCenter(f).add(dir.multiply(9));
        k.refreshPositionAndAngles(at.x, f.getY() - 2, at.z, (float) Math.toDegrees(Math.atan2(-dir.x, dir.z)) + 180, 0);
        k.setCrack(f.down(), dir);
        sw.spawnEntity(k);
    }

    // =====================================================================================
    // the Eye
    // =====================================================================================
    @Nullable
    RivalEyeEntity eye(ServerWorld sw) {
        if (eyeId != null && sw.getEntity(eyeId) instanceof RivalEyeEntity e && e.isAlive()) return e;
        if (lair == null || this.age < nextEyeLookup) return null;
        nextEyeLookup = this.age + 40;                                   // a missing eye is searched for at most every 2 s
        var type = ModMobs.TYPES.get("rival_eye");
        if (type == null) return null;
        for (Entity e : sw.getEntitiesByType(type, new Box(lair.eye()).expand(16), Entity::isAlive))
            if (e instanceof RivalEyeEntity r) { eyeId = r.getUuid(); return r; }
        return null;
    }

    // =====================================================================================
    // persistence
    // =====================================================================================
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Phase", phase());
        nbt.putInt("Arrest", arrestTicks);
        nbt.putInt("FlatTicks", flatTicks);
        nbt.putInt("ClotDelay", clotDelay);
        nbt.putBoolean("Stirred", stirred);
        if (anchor != null) { nbt.putDouble("AX", anchor.x); nbt.putDouble("AY", anchor.y); nbt.putDouble("AZ", anchor.z); }
        if (eyeId != null) nbt.putUuid("Eye", eyeId);
        nbt.put("Nodes", uuids(nodes));
        nbt.put("Clots", uuids(clots));
        if (lair == null) return;
        NbtCompound l = new NbtCompound();
        l.put("Floor", NbtHelper.fromBlockPos(lair.floor()));
        l.put("Pylons", positions(lair.pylons()));
        l.put("Lamps", positions(lair.lamps()));
        NbtList art = new NbtList();
        for (double[] a : lair.arteries()) { NbtCompound c = new NbtCompound(); for (int i = 0; i < 6; i++) c.putDouble("v" + i, a[i]); art.add(c); }
        l.put("Arteries", art);
        NbtList valves = new NbtList();
        for (List<BlockPos> v : lair.valves()) { NbtCompound c = new NbtCompound(); c.put("C", positions(v)); valves.add(c); }
        l.put("Valves", valves);
        l.put("NodeSpots", positions(lair.nodes()));
        l.put("ClotSpots", positions(lair.clotSpots()));
        l.put("Hub", positions(lair.hubSpots()));
        l.put("Eject", positions(lair.ejectSpots()));
        l.put("EyeAt", NbtHelper.fromBlockPos(lair.eye()));
        nbt.put("Lair", l);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setPhase(nbt.getInt("Phase"));
        arrestTicks = nbt.getInt("Arrest");
        this.dataTracker.set(ARREST, arrestTicks > 0);
        flatTicks = nbt.getInt("FlatTicks");
        clotDelay = nbt.getInt("ClotDelay");
        stirred = nbt.getBoolean("Stirred");
        if (nbt.contains("AX")) anchor = new Vec3d(nbt.getDouble("AX"), nbt.getDouble("AY"), nbt.getDouble("AZ"));
        if (nbt.containsUuid("Eye")) eyeId = nbt.getUuid("Eye");
        nodes.clear(); nodes.addAll(uuidList(nbt.getList("Nodes", NbtElement.INT_ARRAY_TYPE)));
        clots.clear(); clots.addAll(uuidList(nbt.getList("Clots", NbtElement.INT_ARRAY_TYPE)));
        if (nbt.contains("Lair")) {
            NbtCompound l = nbt.getCompound("Lair");
            List<double[]> art = new ArrayList<>();
            for (NbtElement e : l.getList("Arteries", NbtElement.COMPOUND_TYPE)) {
                double[] a = new double[6];
                for (int i = 0; i < 6; i++) a[i] = ((NbtCompound) e).getDouble("v" + i);
                art.add(a);
            }
            List<List<BlockPos>> valves = new ArrayList<>();
            for (NbtElement e : l.getList("Valves", NbtElement.COMPOUND_TYPE)) valves.add(posList(((NbtCompound) e).getList("C", NbtElement.COMPOUND_TYPE)));
            setLair(new Lair(NbtHelper.toBlockPos(l.getCompound("Floor")), posList(l.getList("Pylons", NbtElement.COMPOUND_TYPE)),
                    posList(l.getList("Lamps", NbtElement.COMPOUND_TYPE)), art, valves, posList(l.getList("NodeSpots", NbtElement.COMPOUND_TYPE)),
                    posList(l.getList("ClotSpots", NbtElement.COMPOUND_TYPE)), posList(l.getList("Hub", NbtElement.COMPOUND_TYPE)),
                    posList(l.getList("Eject", NbtElement.COMPOUND_TYPE)), NbtHelper.toBlockPos(l.getCompound("EyeAt"))));
        }
        String suffix = switch (phase()) { case CLOTTED -> " - Clotted"; case FLATLINE -> " - FLATLINE"; default -> null; };
        if (suffix != null) bossBar().setName(this.getDisplayName().copy().append(Text.literal(suffix).formatted(Formatting.DARK_RED)));
    }

    private static NbtList positions(List<BlockPos> ps) {
        NbtList l = new NbtList();
        for (BlockPos p : ps) l.add(NbtHelper.fromBlockPos(p));
        return l;
    }

    private static List<BlockPos> posList(NbtList l) {
        List<BlockPos> out = new ArrayList<>();
        for (NbtElement e : l) out.add(NbtHelper.toBlockPos((NbtCompound) e));
        return out;
    }

    private static NbtList uuids(List<UUID> us) {
        NbtList l = new NbtList();
        for (UUID u : us) l.add(NbtHelper.fromUuid(u));
        return l;
    }

    private static List<UUID> uuidList(NbtList l) {
        List<UUID> out = new ArrayList<>();
        for (NbtElement e : l) out.add(NbtHelper.toUuid(e));
        return out;
    }

    // =====================================================================================
    // /ppboss heart ... (testing)
    // =====================================================================================
    public String debugStatus() {
        String ph = switch (phase()) { case CLOTTED -> "CLOTTED"; case RUPTURED -> "RUPTURED"; case FLATLINE -> "FLATLINE"; default -> "BEATING"; };
        StringBuilder s = new StringBuilder("Heart at " + getBlockPos().toShortString() + ": " + ph + ", hp " + (int) getHealth() + "/" + (int) getMaxHealth()
                + ", interval " + interval() + ", fighting " + fighting);
        if (lair == null) s.append(", NO LAIR");
        else s.append(", pylons ").append(charged.size()).append("/").append(lair.pylons().size()).append(" charged, lamps ").append(lair.lamps().size())
                .append(", valves ").append(lair.valves().size());
        if (arrestTicks > 0) s.append(", ARREST ").append(arrestTicks);
        if (pending != null) s.append(", pending ").append(pending);
        if (phase() == CLOTTED && this.getWorld() instanceof ServerWorld sw) {
            int alive = 0;
            for (UUID u : nodes) if (sw.getEntity(u) instanceof HeartNodeEntity n && n.isAlive()) alive++;
            s.append(", nodes ").append(alive).append(", clots ").append(clots.size());
        }
        if (phase() == FLATLINE) s.append(", flat ").append(flatTicks);
        s.append(", eye ").append(this.getWorld() instanceof ServerWorld sw && eye(sw) != null ? eye(sw).stateName() : "?");
        return s.toString();
    }

    /** Test ops: arrest | clot | nodes | flatline | shock | triple | lash | bleed | systole | embolism | memory N */
    public String debug(String op, int arg) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return "client";
        testFight = 1200;
        fighting = true;
        switch (op) {
            case "arrest" -> { if (lair != null && phase() != CLOTTED && phase() != FLATLINE) arrest(sw); else return "no"; }
            case "clot" -> { if (phase() == BEATING) { setHealth(getMaxHealth() * 0.5f); if (lair != null) startClot(sw); } else return "not beating"; }
            case "nodes" -> { for (UUID u : List.copyOf(nodes)) if (sw.getEntity(u) instanceof HeartNodeEntity n) n.damage(this.getDamageSources().genericKill(), 1000f); }
            case "flatline" -> { if (phase() == RUPTURED || phase() == BEATING) { setPhase(RUPTURED); setHealth(getMaxHealth() * 0.25f); startFlatline(sw); } }
            case "shock" -> { if (phase() == FLATLINE) { flatTicks = Math.max(flatTicks, 100); shock(sw, eye(sw)); } }
            case "memory" -> memory(sw, Math.floorMod(arg, MEMORIES.length));
            default -> {
                Attack a;
                try { a = Attack.valueOf(op.toUpperCase()); } catch (IllegalArgumentException e) { return "unknown op " + op; }
                pending = null;
                forced = a;                                        // a telegraphed attack lands on the next beat
                startAttack(sw);
                forced = null;
            }
        }
        return debugStatus();
    }

    private Attack forced;

    // =====================================================================================
    // tasks
    // =====================================================================================
    @FunctionalInterface
    interface Task { boolean tick(ServerWorld sw, int t); }

    static final class Running {
        final Task task;
        int t;
        Running(Task task) { this.task = task; }
    }
}
