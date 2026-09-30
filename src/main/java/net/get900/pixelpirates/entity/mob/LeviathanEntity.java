package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.entity.ModEntities;
import net.get900.pixelpirates.entity.custom.DebrisEntity;
import net.get900.pixelpirates.entity.custom.PowderBargeEntity;
import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.leviathan.LeviathanHunt;
import net.get900.pixelpirates.world.leviathan.LeviathanRoute;
import net.get900.pixelpirates.world.leviathan.LeviathanSites;
import net.get900.pixelpirates.world.leviathan.LeviathanState;
import net.get900.pixelpirates.world.leviathan.RiftLayout;
import net.get900.pixelpirates.world.leviathan.SpireLayout;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
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
import net.minecraft.entity.vehicle.BoatEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
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
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * BOSS 10/10 - THE LEVIATHAN. One per world (world/leviathan/LeviathanHunt owns the hunt: where it is, the ports it
 * destroys, the Last Tide). This entity is its HEAD; the body is 12 segments + a tail (LeviathanSegmentEntity, ~78 blocks
 * in all) that follow the path it swims. Its motion is fully scripted (noClip, steered inside its lair's bounds).
 * One health bar across three lairs: 100% -> 66% (it RETREATS to the Gullet) -> 33% (retreats to the Spire) -> 0.
 * <ul>
 *   <li>FORM 1 THE WAKING (the Rift): CRUST - every part is armoured (x0.1) until blasted open (explosions, cannon, harpoons);
 *       THE BINDINGS - four chains from the anchor pillars hold it; winches reinforce them, its struggles strain them;
 *       while three hold it cannot dive. Tidal Yawn, Coil Crush, Chain Lash (snapped chains), Rift Quake, Sleepwalk Ambush.</li>
 *   <li>FORM 2 THE HUNGER (the Gullet, under the eclipse): HUNGER - it hunts the biggest meal (powder barges, chum, boats,
 *       you) and feeds; a swallowed POWDER BARGE blows up inside it (stunned, mouth open, x2.5). Devouring Breach, Tendril
 *       Rake, Blood Lure, Undertow Gulp, Blood Tide, Eclipse Hunt. Starve it and it weakens.</li>
 *   <li>FORM 3 THE UNMAKING (the Drowning Spire): the Last Tide rises (LeviathanHunt); it REARS at the Spire - knock the Bane
 *       Shaft out of its crown, load the Bane, fire it while it is reared and it is PINNED (x3). Tidal Apocalypse,
 *       Maelstrom Spin, Wreck Rain, Storm Call, Swallow the Sky, Drowned Memories.</li>
 * </ul>
 */
public class LeviathanEntity extends ModBoss {
    private static final TrackedData<Integer> FORM = DataTracker.registerData(LeviathanEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ECLIPSE = DataTracker.registerData(LeviathanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Integer> POSE = DataTracker.registerData(LeviathanEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> DARK = DataTracker.registerData(LeviathanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> CROWN_SHAFT = DataTracker.registerData(LeviathanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Boolean> HEAD_CRUST = DataTracker.registerData(LeviathanEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    public static final int P_SWIM = 0, P_REARED = 1, P_PINNED = 2, P_STUNNED = 3;
    static final Vector3f RED = new Vector3f(0.9f, 0.06f, 0.08f), WARN = new Vector3f(1.0f, 0.2f, 0.1f), CHAIN = new Vector3f(0.55f, 0.58f, 0.62f);
    public static final int SEGMENTS = 13;
    static final double FIRST = 5.5, HALF = 2.625, OVERLAP = 0.95;   // HALF = a full-size segment's half length (14 px x 3.0 / 16)
    private static final RawAnimation[] LOOPS = {null, RawAnimation.begin().thenLoop("reared"), RawAnimation.begin().thenLoop("pinned"), RawAnimation.begin().thenLoop("stunned")};

    // ---- the lair it is in (from the site layout; not saved - rebuilt from LeviathanSites)
    private String site = LeviathanRoute.RIFT;
    private Vec3d centre;
    private double radius = 60, floorY = -40, surfaceY = 58;
    // ---- motion
    private final ArrayDeque<Vec3d> path = new ArrayDeque<>();
    private Vec3d vel = new Vec3d(0.3, 0, 0);
    private final UUID[] segs = new UUID[SEGMENTS];
    private int segMissing;
    // ---- combat
    enum Act { CRUISE, YAWN, COIL, LASH, QUAKE, SLEEPWALK, BREACH, RAKE, LURE, GULP, BLOODTIDE, ECLIPSE_HUNT, REAR, APOCALYPSE, SPIN, WRECKRAIN, STORM, MEMORY, RETREAT, STUNNED, PINNED, SPIRE_COIL }
    private Act act = Act.CRUISE;
    private int actT, nextAbility = 80, biteCooldown;
    private Vec3d point;                                  // the action's target point (where it steers)
    private Vec3d anchorPt;                               // a fixed point the action works round (Coil Crush)
    @Nullable private UUID prey;
    private Act lastAbility;
    private boolean resolved, retreating;
    private boolean travel;                               // swimming between lairs (LeviathanHunt steers it; can't be hurt)
    // phase 2
    private float hunger = 30;
    private int starving;
    private boolean weak;
    // phase 3
    private int crownHits, rearCooldown = 400, perchTicks;
    private double coilA0;
    private final Map<Integer, Float> crustHp = new HashMap<>();
    private final List<Running> tasks = new ArrayList<>();

    public LeviathanEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(FORM, 1);
        this.dataTracker.startTracking(ECLIPSE, false);
        this.dataTracker.startTracking(POSE, P_SWIM);
        this.dataTracker.startTracking(DARK, false);
        this.dataTracker.startTracking(CROWN_SHAFT, false);
        this.dataTracker.startTracking(HEAD_CRUST, true);
    }

    public int form() { return this.dataTracker.get(FORM); }
    public boolean eclipse() { return this.dataTracker.get(ECLIPSE); }
    public int pose() { return this.dataTracker.get(POSE); }
    public boolean dark() { return this.dataTracker.get(DARK); }
    public boolean crownShaft() { return this.dataTracker.get(CROWN_SHAFT); }
    public boolean headCrusted() { return this.dataTracker.get(HEAD_CRUST); }
    public String site() { return site; }
    private void setPoseState(int p) { this.dataTracker.set(POSE, p); }

    /** LeviathanHunt, before the spawn: which lair and which form. */
    public void setup(String siteId, int form, float health) {
        this.site = siteId;
        this.dataTracker.set(FORM, form);
        this.dataTracker.set(ECLIPSE, form >= 2);
        this.dataTracker.set(CROWN_SHAFT, form == 3);
        // one bar in thirds: it never enters a lair above that phase's share (an early escape - every chain snapped - included)
        float cap = getMaxHealth() * (form == 1 ? 1f : form == 2 ? 0.66f : 0.33f);
        this.setHealth(health > 0 ? Math.min(cap, health) : cap);
    }

    // ------------------------------------------------------------------ renderer + GeckoLib hooks
    @Override
    public String skinVariant() { return dark() ? "dark" : null; }

    @Override
    protected RawAnimation movementOverride() { int p = pose(); return p > 0 && p < LOOPS.length ? LOOPS[p] : null; }

    @Override
    protected List<String> extraAnims() { return List.of("roar", "yawn", "breach", "rake", "gulp", "spit", "lure", "rear", "flinch", "dive"); }

    @Override
    protected List<Abilities.Ability> activeAbilities() { return List.of(); }

    @Override
    protected boolean scriptedMotion() { return true; }

    @Override
    protected boolean healthEnrage() { return false; }

    @Override
    protected double creditRange() { return 200; }

    @Override
    public boolean isPushedByFluids() { return false; }

    // Its heading is scripted: vanilla's look-at-player control would tilt the head toward whoever is above it (while
    // travelling it swam along staring straight up at the player) - it may not touch pitch or head yaw.
    @Override
    public int getMaxLookPitchChange() { return 0; }

    @Override
    public int getMaxHeadRotation() { return 0; }

    @Override
    public Box getVisibilityBoundingBox() { return getBoundingBox().expand(12); }

    @Override
    public boolean shouldRender(double distance) { return distance < 320 * 320; }

    @Override
    protected SoundEvent sound(String kind) {
        return switch (kind) {
            case "hurt" -> SoundEvents.ENTITY_ELDER_GUARDIAN_HURT;
            case "death" -> SoundEvents.ENTITY_ENDER_DRAGON_DEATH;
            case "ambient" -> SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT;
            case "special" -> SoundEvents.ENTITY_ENDER_DRAGON_GROWL;
            default -> null;
        };
    }

    @Override
    public float getSoundPitch() { return 0.4f + this.random.nextFloat() * 0.1f; }

    @Override
    public int getMinAmbientSoundDelay() { return 200; }

    @Override
    protected float getSoundVolume() { return 6.0f; }

    /** Its regeneration never lifts it back over the phase it is in. */
    @Override
    public void heal(float amount) {
        float cap = getMaxHealth() * (form() == 1 ? 1f : form() == 2 ? 0.66f : 0.33f);
        if (getHealth() >= cap) return;
        super.heal(Math.min(amount, cap - getHealth()));
    }

    // ------------------------------------------------------------------ the lair
    private void lair(ServerWorld sw) {
        if (centre != null) return;
        LeviathanRoute.Site s = LeviathanRoute.of(sw.getSeed()).byId(site);
        if (s == null) { centre = getPos(); return; }
        switch (site) {
            case LeviathanRoute.RIFT -> { centre = new Vec3d(s.x() + 0.5, -24, s.z() + 0.5); radius = 72; floorY = RiftLayout.FLOOR + 6; surfaceY = 58; }
            case LeviathanRoute.GULLET -> { centre = new Vec3d(s.x() + 0.5, 48, s.z() + 0.5); radius = 70; floorY = 40; surfaceY = 61; }
            default -> { centre = new Vec3d(s.x() + 0.5, 52, s.z() + 0.5); radius = SpireLayout.LAGOON - 5; floorY = SpireLayout.FLOOR + 3; surfaceY = 61; }
        }
    }

    /** The point the Bane must hit: its core (the split spine, just behind the head). */
    public Vec3d coreTarget() {
        LeviathanSegmentEntity s = segment(1);
        return s != null ? s.getPos().add(0, s.getHeight() * 0.5, 0) : getPos().add(0, getHeight() * 0.5, 0);
    }

    Vec3d mouth() { return getPos().add(vel.normalize().multiply(4.5)).add(0, getHeight() * 0.4, 0); }

    // =====================================================================================
    // the tick
    // =====================================================================================
    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (travel) {                                                        // between lairs: LeviathanHunt moves the head
            if (!LeviathanHunt.ownsTraveler(sw, this)) { discardBody(sw); this.discard(); return; }
            body(sw);
            return;
        }
        lair(sw);
        if (!LeviathanHunt.owns(sw, this)) { discardBody(sw); this.discard(); return; }       // a stale copy
        if (site.equals(LeviathanRoute.SPIRE)) surfaceY = 61 + LeviathanState.get(sw).tideLayers;   // THE LAST TIDE lifts the sea
        List<ServerPlayerEntity> fs = fighters(sw);
        for (ServerPlayerEntity p : fs) LeviathanState.get(sw).hunters.add(p.getUuid());
        if (biteCooldown > 0) biteCooldown--;
        tickAction(sw, fs);
        move(sw);
        body(sw);
        if (form() == 1) tickBindings(sw, fs);
        if (form() == 2) tickHunger(sw);
        for (int i = tasks.size() - 1; i >= 0; i--) { Running r = tasks.get(i); if (!r.task.tick(sw, r.t++)) tasks.remove(i); }
        // bite whoever gets in front of it
        if (biteCooldown == 0 && act != Act.STUNNED && act != Act.PINNED && act != Act.RETREAT)
            for (ServerPlayerEntity p : fs) if (p.getPos().add(0, 1, 0).distanceTo(mouth()) < 5) {
                p.damage(this.getDamageSources().mobAttack(this), weak ? 10f : 14f);
                triggerAnim(ACTION, "attack");
                sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 3.0f, 0.4f);
                biteCooldown = 25;
                if (form() == 2) feed(sw, 20, "a mouthful of you");
                break;
            }
    }

    List<ServerPlayerEntity> fighters(ServerWorld sw) {
        Vec3d c = centre != null ? centre : getPos();
        double r = radius + 48;
        return sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && mayTarget(p) && p.squaredDistanceTo(c.x, p.getY(), c.z) < r * r);
    }

    void broadcast(ServerWorld sw, Text t, boolean bar) {
        Vec3d c = centre != null ? centre : getPos();
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(c) < 220 * 220)) p.sendMessage(t, bar);
    }

    // =====================================================================================
    // MOTION: a steered head, a body that follows its path
    // =====================================================================================
    private double speed() {
        double base = switch (act) {
            case RETREAT -> 0.95; case SPIN, SPIRE_COIL -> 1.0; case BREACH, SLEEPWALK -> 0.9; case COIL -> 0.8; case STUNNED, PINNED -> 0; case REAR -> 0.6;
            default -> 0.42 + (form() - 1) * 0.08;
        };
        return base * (weak ? 0.7 : 1);
    }

    /** Where it wants to go this tick (a unit direction), by action. */
    private Vec3d desire(ServerWorld sw) {
        Vec3d here = getPos();
        switch (act) {
            case RETREAT, BREACH, SLEEPWALK, REAR, COIL, SPIN, STUNNED, PINNED, SPIRE_COIL -> { if (point != null) return point.subtract(here); }
            default -> { }
        }
        // cruise: a long loop round the lair, weaving up and down, drifting toward the fighters
        double a = (this.age * 0.006) % (Math.PI * 2);
        double r = radius * 0.62;
        Vec3d goal = centre.add(Math.cos(a) * r, Math.sin(this.age * 0.02) * (surfaceY - floorY) * 0.25, Math.sin(a) * r);
        if (site.equals(LeviathanRoute.RIFT)) goal = new Vec3d(centre.x + Math.cos(a) * radius * 0.8, centre.y + Math.sin(this.age * 0.02) * 12, centre.z + Math.sin(a * 2) * 8);
        ServerPlayerEntity p = sw.getPlayerByUuid(prey != null ? prey : new UUID(0, 0)) instanceof ServerPlayerEntity sp ? sp : null;
        if (p != null && this.age % 400 < 160) goal = p.getPos().add(0, -2, 0);                // stalking
        return goal.subtract(here);
    }

    private void move(ServerWorld sw) {
        if (act == Act.SPIRE_COIL && actT <= 64) return;                   // tickSpireCoil places it
        double sp = speed();
        Vec3d d = desire(sw);
        // keep inside the lair: a soft wall and a floor/ceiling
        Vec3d rel = getPos().subtract(centre);
        double horiz = Math.sqrt(rel.x * rel.x + rel.z * rel.z);
        double limit = site.equals(LeviathanRoute.RIFT) ? leash() : radius;
        boolean free = act == Act.RETREAT || act == Act.BREACH || act == Act.REAR || act == Act.SPIRE_COIL;
        if (!free && horiz > limit) d = d.normalize().add(new Vec3d(-rel.x, 0, -rel.z).normalize().multiply(1.5 * (horiz - limit) / 8));
        if (!free && getY() < floorY) d = d.add(0, 1, 0);
        if (!free && getY() > surfaceY - 2 && act != Act.YAWN) d = d.add(0, -0.8, 0);
        if (site.equals(LeviathanRoute.SPIRE) && !free && horiz < 12) d = d.add(new Vec3d(rel.x, 0, rel.z).normalize().multiply(1.2));   // never through the Spire
        if (sp <= 0 || d.lengthSquared() < 1e-6) { vel = vel.multiply(0.8); return; }
        Vec3d want = d.normalize();
        Vec3d cur = vel.lengthSquared() < 1e-6 ? want : vel.normalize();
        double turn = act == Act.BREACH || act == Act.SLEEPWALK ? 0.35 : act == Act.SPIN ? 0.16 : 0.07;
        Vec3d nd = slerp(cur, want, turn);
        // hard floor and ceiling: it swims through rock (noClip), and its slow turn used to carry it 50 blocks under a lair
        if (!free && getY() < floorY - 2 && nd.y < 0.25) nd = new Vec3d(nd.x, 0.25, nd.z).normalize();
        if (!free && act != Act.YAWN && getY() > surfaceY && nd.y > -0.25) nd = new Vec3d(nd.x, -0.25, nd.z).normalize();
        // and a hard wall: past the lair's edge it may not head further out (at the Spire it drifted 89 blocks, through the rim)
        if (!free && horiz > limit && horiz > 1e-3) {
            Vec3d out = new Vec3d(rel.x / horiz, 0, rel.z / horiz);
            double o = nd.dotProduct(out);
            if (o > -0.3) nd = nd.subtract(out.multiply(o + 0.3 + Math.min(1, (horiz - limit) / 10))).normalize();
        }
        // the Spire itself is rock: inside 14 it may only head outward (it cruised through its middle)
        if (!free && site.equals(LeviathanRoute.SPIRE) && horiz < 14 && horiz > 1e-3) {
            Vec3d out = new Vec3d(rel.x / horiz, 0, rel.z / horiz);
            double o = nd.dotProduct(out);
            if (o < 0.3) nd = nd.add(out.multiply(0.3 - o + 0.4)).normalize();
        }
        vel = nd.multiply(sp);
        this.setPosition(getPos().add(vel));
        double h = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
        this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
        this.setPitch((float) -Math.toDegrees(Math.atan2(vel.y, h)));
        if (this.age % 3 == 0 && getY() > surfaceY - 3 && getY() < surfaceY + 3)
            sw.spawnParticles(ParticleTypes.SPLASH, getX(), surfaceY + 1, getZ(), 12, 2, 0.2, 2, 0.2);
    }

    static Vec3d slerp(Vec3d a, Vec3d b, double maxAngle) {
        double dot = MathHelper.clamp(a.dotProduct(b), -1, 1), ang = Math.acos(dot);
        if (ang <= maxAngle || ang < 1e-4) return b;
        double t = maxAngle / ang;
        return a.multiply(1 - t).add(b.multiply(t)).normalize();
    }

    // ---- the body: every segment placed along the path the head swam
    private void body(ServerWorld sw) {
        Vec3d head = getPos();
        if (path.isEmpty()) {                                             // lay the body out straight behind it (front = newest)
            Vec3d back = vel.lengthSquared() < 1e-6 ? new Vec3d(-1, 0, 0) : vel.normalize().multiply(-1);
            path.addLast(head);
            for (int i = 1; i <= 90; i++) path.addLast(head.add(back.multiply(i)));
        }
        if (path.peekFirst().squaredDistanceTo(head) > 0.09) path.addFirst(head);
        while (path.size() > 420) path.removeLast();
        // walk back along the path
        double[] want = new double[SEGMENTS];
        // each piece sits just touching the one in front: the gap follows the taper (a fixed spacing left the shrunken rear
        // segments and the tail hanging loose behind the body)
        for (int i = 0; i < SEGMENTS; i++)
            want[i] = i == 0 ? FIRST : want[i - 1] + OVERLAP * HALF * (LeviathanSegmentEntity.sizeOf(i - 1) + LeviathanSegmentEntity.sizeOf(i));
        Vec3d[] at = new Vec3d[SEGMENTS + 1];
        at[0] = head;
        double acc = 0;
        int k = 0;
        Iterator<Vec3d> it = path.iterator();
        Vec3d prev = it.next();
        while (it.hasNext() && k < SEGMENTS) {
            Vec3d nx = it.next();
            double seg = prev.distanceTo(nx);
            while (k < SEGMENTS && acc + seg >= want[k]) {
                double t = seg < 1e-6 ? 0 : (want[k] - acc) / seg;
                at[k + 1] = prev.add(nx.subtract(prev).multiply(t));
                k++;
            }
            acc += seg;
            prev = nx;
        }
        for (; k < SEGMENTS; k++) at[k + 1] = (k == 0 ? head : at[k]).add(-1, 0, 0);
        // spawn / find the segments and place them
        boolean missing = false;
        for (int i = 0; i < SEGMENTS; i++) {
            LeviathanSegmentEntity s = segs[i] == null ? null : (sw.getEntity(segs[i]) instanceof LeviathanSegmentEntity e ? e : null);
            if (s == null) { missing = true; continue; }
            Vec3d d = at[i].subtract(at[i + 1]);
            double h = Math.sqrt(d.x * d.x + d.z * d.z);
            s.place(at[i + 1], (float) Math.toDegrees(Math.atan2(-d.x, d.z)), (float) -Math.toDegrees(Math.atan2(d.y, h)));
        }
        if (missing && ++segMissing > 10) { spawnBody(sw, at); segMissing = 0; }
    }

    private void spawnBody(ServerWorld sw, Vec3d[] at) {
        long crust = LeviathanState.get(sw).crustBroken;
        for (int i = 0; i < SEGMENTS; i++) {
            if (segs[i] != null && sw.getEntity(segs[i]) instanceof LeviathanSegmentEntity) continue;
            LeviathanSegmentEntity s = ModEntities.LEVIATHAN_SEGMENT.create(sw);
            if (s == null) continue;
            s.setup(this.getUuid(), i, form(), form() == 1 && (crust & (1L << i)) == 0);
            s.refreshPositionAndAngles(at[i + 1].x, at[i + 1].y, at[i + 1].z, getYaw(), 0);
            sw.spawnEntity(s);
            segs[i] = s.getUuid();
        }
    }

    @Nullable
    LeviathanSegmentEntity segment(int i) {
        return segs[i] != null && this.getWorld() instanceof ServerWorld sw && sw.getEntity(segs[i]) instanceof LeviathanSegmentEntity s ? s : null;
    }

    public void discardBody(ServerWorld sw) {
        for (UUID u : segs) if (u != null && sw.getEntity(u) instanceof LeviathanSegmentEntity s) s.discard();
    }

    // =====================================================================================
    // ACTIONS
    // =====================================================================================
    private void start(ServerWorld sw, Act a, @Nullable Vec3d at) {
        act = a; actT = 0; point = at; resolved = false; lastAbility = a;
    }

    private void end() { act = Act.CRUISE; point = null; setPoseState(P_SWIM); this.dataTracker.set(DARK, false); nextAbility = 70 + this.random.nextInt(50); }

    private void tickAction(ServerWorld sw, List<ServerPlayerEntity> fs) {
        actT++;
        if (act == Act.CRUISE) {
            if (!fs.isEmpty() && (prey == null || this.age % 300 == 0)) prey = fs.get(this.random.nextInt(fs.size())).getUuid();
            if (form() == 3 && !fs.isEmpty() && --rearCooldown <= 0) { rear(sw); return; }
            // nobody waits it out on the Spire: 6 s perched up there and it comes up after them
            if (form() == 3 && perched(sw, fs)) { if (++perchTicks > 120) { spireCoil(sw); return; } } else perchTicks = 0;
            if (fs.isEmpty() || --nextAbility > 0) return;
            pickAbility(sw, fs);
            return;
        }
        switch (act) {
            case YAWN -> tickYawn(sw, fs);
            case COIL -> tickCoil(sw, fs);
            case LASH, QUAKE, RAKE, LURE, GULP, BLOODTIDE, APOCALYPSE, WRECKRAIN, STORM, MEMORY -> { if (actT > 60) end(); }
            case SLEEPWALK -> tickSleepwalk(sw, fs);
            case BREACH, ECLIPSE_HUNT -> tickBreach(sw, fs);
            case REAR -> tickRear(sw, fs);
            case SPIN -> tickSpin(sw);
            case RETREAT -> tickRetreat(sw);
            case STUNNED -> tickStunned(sw);
            case PINNED -> tickPinned(sw);
            case SPIRE_COIL -> tickSpireCoil(sw, fs);
            default -> end();
        }
    }

    private void pickAbility(ServerWorld sw, List<ServerPlayerEntity> fs) {
        ServerPlayerEntity target = fs.get(this.random.nextInt(fs.size()));
        List<Act> pool = new ArrayList<>();
        switch (form()) {
            case 1 -> {
                pool.add(Act.YAWN); pool.add(Act.COIL); pool.add(Act.QUAKE); pool.add(Act.QUAKE);
                if (snapped(sw) > 0) pool.add(Act.LASH);
                if (snapped(sw) >= 2) { pool.add(Act.SLEEPWALK); pool.add(Act.SLEEPWALK); }
            }
            case 2 -> {
                Collections3.add(pool, Act.RAKE, 2); Collections3.add(pool, Act.LURE, 1); Collections3.add(pool, Act.GULP, 1);
                Collections3.add(pool, Act.BLOODTIDE, 1); Collections3.add(pool, Act.ECLIPSE_HUNT, 1); Collections3.add(pool, Act.BREACH, hunger >= 60 ? 5 : 2);
            }
            default -> {
                Collections3.add(pool, Act.APOCALYPSE, 2); Collections3.add(pool, Act.SPIN, 1); Collections3.add(pool, Act.WRECKRAIN, 2);
                Collections3.add(pool, Act.STORM, 2); Collections3.add(pool, Act.MEMORY, 2);
                if (perched(sw, fs)) Collections3.add(pool, Act.SPIRE_COIL, 5);
            }
        }
        pool.removeIf(x -> x == lastAbility && pool.stream().anyMatch(y -> y != lastAbility));
        Act a = pool.get(this.random.nextInt(pool.size()));
        switch (a) {
            case YAWN -> yawn(sw);
            case COIL -> coil(sw, target);
            case LASH -> lash(sw, fs);
            case QUAKE -> quake(sw, fs);
            case SLEEPWALK -> sleepwalk(sw, target);
            case BREACH -> breach(sw, meal(sw, target), false);
            case ECLIPSE_HUNT -> { for (ServerPlayerEntity p : fs) p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 140, 0), this);
                broadcast(sw, Text.literal("The eclipse swallows the light - it is circling beneath you...").formatted(Formatting.DARK_RED, Formatting.ITALIC), true);
                breach(sw, target.getPos(), true); }
            case RAKE -> rake(sw, fs);
            case LURE -> lure(sw);
            case GULP -> gulp(sw, fs);
            case BLOODTIDE -> bloodTide(sw);
            case APOCALYPSE -> apocalypse(sw);
            case SPIN -> spin(sw);
            case WRECKRAIN -> wreckRain(sw, fs);
            case STORM -> storm(sw, fs);
            case MEMORY -> memory(sw, fs);
            case SPIRE_COIL -> spireCoil(sw);
            default -> end();
        }
    }

    // ---------------------------------------------------------------- phase 1: THE WAKING
    private void yawn(ServerWorld sw) {
        start(sw, Act.YAWN, new Vec3d(getX(), surfaceY - 6, getZ()));
        triggerAnim(ACTION, "yawn");
        strain(sw, 14);
    }

    private void tickYawn(ServerWorld sw, List<ServerPlayerEntity> fs) {
        if (actT == 22) {
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 6.0f, 0.4f);
            broadcast(sw, Text.literal("It YAWNS - the whole sea heaves!").formatted(Formatting.DARK_AQUA, Formatting.BOLD), true);
            Vec3d c = getPos();
            tasks.add(new Running((w, t) -> {                                // the wave rolls outward
                double r = 4 + t * 2.5;
                int n = (int) (r * 0.8);
                for (int i = 0; i < n; i++) { double a = i * Math.PI * 2 / n; w.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 2, 0.3, 3, 0.3, 0.1); }
                for (ServerPlayerEntity p : fighters(w)) {
                    double d = Math.hypot(p.getX() - c.x, p.getZ() - c.z);
                    if (Math.abs(d - r) > 2.5) continue;
                    Vec3d u = new Vec3d(p.getX() - c.x, 0, p.getZ() - c.z).normalize();
                    p.setVelocity(u.x * 1.4, 0.5, u.z * 1.4); p.velocityModified = true;
                    p.damage(getDamageSources().mobAttack(LeviathanEntity.this), 4f);
                }
                return r < 48;
            }));
        }
        if (actT > 50) end();
    }

    private void coil(ServerWorld sw, ServerPlayerEntity target) {
        start(sw, Act.COIL, target.getPos());
        anchorPt = target.getPos();
        strain(sw, 16);
        Vec3d c = target.getPos();
        broadcast(sw, Text.literal("It COILS round you - get out of the ring!").formatted(Formatting.RED, Formatting.BOLD), true);
        tasks.add(new Running((w, t) -> {
            double r = 14 - t * 0.1;
            if (t % 3 == 0) for (int i = 0; i < 24; i++) { double a = i * Math.PI / 12; w.spawnParticles(new DustParticleEffect(WARN, 2f), c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r, 1, 0, 1.5, 0, 0); }
            return t < 100 && act == Act.COIL;
        }));
    }

    private void tickCoil(ServerWorld sw, List<ServerPlayerEntity> fs) {
        Vec3d c = anchorPt;
        double r = Math.max(4, 14 - actT * 0.1), a = actT * 0.12;
        point = c.add(Math.cos(a) * r, 0, Math.sin(a) * r);                 // steer round the ring, tightening
        if (actT == 100) {
            sw.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 4.0f, 0.4f);
            for (ServerPlayerEntity p : fs) if (p.getPos().distanceTo(c) < 6) {
                p.damage(getDamageSources().mobAttack(this), 14f);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2), this);
            }
            end();
        }
    }

    private void lash(ServerWorld sw, List<ServerPlayerEntity> fs) {
        start(sw, Act.LASH, null);
        RiftLayout L = rift(sw);
        LeviathanState st = LeviathanState.get(sw);
        if (L == null) { end(); return; }
        List<Vec3d[]> lanes = new ArrayList<>();
        for (int i = 0; i < 4 && i < L.chainEnds.size(); i++) {
            if (st.chains[i] > 0) continue;
            Vec3d from = Vec3d.ofCenter(world(L.chainEnds.get(i)));
            ServerPlayerEntity p = fs.get(this.random.nextInt(fs.size()));
            Vec3d to = from.add(p.getPos().subtract(from).normalize().multiply(40));
            lanes.add(new Vec3d[]{from, to});
        }
        broadcast(sw, Text.literal("CHAIN LASH - the snapped chains whip across the Rift!").formatted(Formatting.RED, Formatting.BOLD), true);
        tasks.add(new Running((w, t) -> {
            for (Vec3d[] l : lanes) {
                if (t < 25 && t % 2 == 0) line(w, l[0], l[1], new DustParticleEffect(t > 16 ? WARN : CHAIN, 2f), 1.0);
                if (t == 25) {
                    line(w, l[0], l[1], ParticleTypes.CRIT, 0.6);
                    w.playSound(null, BlockPos.ofFloored(l[0]), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 4.0f, 0.4f);
                    for (ServerPlayerEntity p : fighters(w)) if (DrownedMemories.closest(l[0], l[1], p.getPos().add(0, 1, 0)).distanceTo(p.getPos().add(0, 1, 0)) < 2.5) {
                        p.damage(getDamageSources().mobAttack(LeviathanEntity.this), 12f);
                        p.addVelocity(0, 0.6, 0); p.velocityModified = true;
                    }
                }
            }
            return t < 26;
        }));
    }

    private void quake(ServerWorld sw, List<ServerPlayerEntity> fs) {
        start(sw, Act.QUAKE, null);
        triggerAnim(ACTION, "roar");
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.HOSTILE, 4.0f, 0.4f);
        broadcast(sw, Text.literal("RIFT QUAKE - it slams the chasm wall; the stalactites are coming down!").formatted(Formatting.RED), true);
        List<Vec3d> spots = new ArrayList<>();
        for (ServerPlayerEntity p : fs) for (int k = 0; k < 2; k++) spots.add(p.getPos().add(this.random.nextGaussian() * 3, 0, this.random.nextGaussian() * 3));
        tasks.add(new Running((w, t) -> {
            for (Vec3d q : spots) {
                if (t < 24 && t % 3 == 0) for (int i = 0; i < 10; i++) { double a = i * Math.PI / 5; w.spawnParticles(new DustParticleEffect(WARN, 1.4f), q.x + Math.cos(a) * 1.6, q.y + 0.2, q.z + Math.sin(a) * 1.6, 1, 0, 0, 0, 0); }
                if (t == 12) w.spawnEntity(DebrisEntity.fling(w, LeviathanEntity.this, q.add(0, 16, 0), q, Blocks.POINTED_DRIPSTONE.getDefaultState(), 9f));
            }
            return t < 25;
        }));
    }

    private void sleepwalk(ServerWorld sw, ServerPlayerEntity target) {
        start(sw, Act.SLEEPWALK, new Vec3d(getX(), floorY, getZ()));
        this.dataTracker.set(DARK, true);
        prey = target.getUuid();
        triggerAnim(ACTION, "dive");
        broadcast(sw, Text.literal("Its lights go out. It is somewhere below you...").formatted(Formatting.DARK_GRAY, Formatting.ITALIC), true);
    }

    private void tickSleepwalk(ServerWorld sw, List<ServerPlayerEntity> fs) {
        ServerPlayerEntity target = prey != null && sw.getPlayerByUuid(prey) instanceof ServerPlayerEntity p ? p : null;
        if (actT == 40 && target != null) point = new Vec3d(target.getX(), floorY, target.getZ());
        if (actT >= 40 && actT < 80 && target != null && actT % 2 == 0)
            sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, point.x, target.getY() - 2, point.z, 20, 0.8, 1.5, 0.8, 0.1);
        if (actT == 80) {                                                   // ERUPT straight up through the mark
            this.setPosition(point.x, floorY, point.z);
            vel = new Vec3d(0, 1, 0);
            point = new Vec3d(point.x, surfaceY + 6, point.z);
            this.dataTracker.set(DARK, false);
            triggerAnim(ACTION, "breach");
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 5.0f, 0.6f);
            strain(sw, 20);
        }
        if (actT > 80) for (ServerPlayerEntity p : fs) if (Math.hypot(p.getX() - getX(), p.getZ() - getZ()) < 5 && Math.abs(p.getY() - getY()) < 6 && !resolved) {
            p.damage(getDamageSources().mobAttack(this), 16f);
            p.setVelocity(0, 1.6, 0); p.velocityModified = true;
            resolved = true;
        }
        if (actT > 120) end();
    }

    // ---------------------------------------------------------------- THE BINDINGS (phase 1)
    int snapped(ServerWorld sw) {
        int n = 0;
        for (int c : LeviathanState.get(sw).chains) if (c <= 0) n++;
        return n;
    }

    double leash() { return this.getWorld() instanceof ServerWorld sw ? 26 + 14 * snapped(sw) : 70; }

    @Nullable RiftLayout rift(ServerWorld sw) {
        return LeviathanSites.built(sw.getSeed(), LeviathanRoute.RIFT, false).layout() instanceof RiftLayout r ? r : null;
    }

    BlockPos world(int[] rel) {
        LeviathanRoute.Site s = LeviathanRoute.of(((ServerWorld) this.getWorld()).getSeed()).byId(site);
        return new BlockPos(s.x() + rel[0], rel[1], s.z() + rel[2]);
    }

    /** Its struggles strain the chain nearest its head. */
    void strain(ServerWorld sw, int amount) {
        if (form() != 1) return;
        RiftLayout L = rift(sw);
        LeviathanState st = LeviathanState.get(sw);
        if (L == null) return;
        int best = -1;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            if (st.chains[i] <= 0) continue;
            double d = Vec3d.ofCenter(world(L.chainEnds.get(i))).squaredDistanceTo(getPos());
            if (d < bd) { bd = d; best = i; }
        }
        if (best < 0) return;
        st.chains[best] = Math.max(0, st.chains[best] - amount);
        st.markDirty();
        BlockPos e = world(L.chainEnds.get(best));
        sw.playSound(null, e, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.HOSTILE, 3.0f, 0.4f);
        sw.spawnParticles(ParticleTypes.ELECTRIC_SPARK, e.getX() + 0.5, e.getY() + 0.5, e.getZ() + 0.5, 20, 0.5, 0.5, 0.5, 0.2);
        if (st.chains[best] == 0) {
            sw.playSound(null, e, SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 6.0f, 0.3f);
            sw.playSound(null, e, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 4.0f, 0.4f);
            int left = 4 - snapped(sw);
            broadcast(sw, Text.literal("A BINDING CHAIN SNAPS! " + left + " still hold it.").formatted(Formatting.GOLD, Formatting.BOLD), false);
            if (left == 0) retreat(sw);
        }
    }

    /** A player wound an anchor winch (LeviathanHunt). */
    public void reinforce(ServerWorld sw, int chain, PlayerEntity p) {
        LeviathanState st = LeviathanState.get(sw);
        if (st.chains[chain] <= 0) { p.sendMessage(Text.literal("This chain is snapped - it will never hold again.").formatted(Formatting.GRAY), true); return; }
        st.chains[chain] = Math.min(100, st.chains[chain] + 9);
        st.markDirty();
        p.sendMessage(Text.literal("You wind the winch - the chain draws tight (" + st.chains[chain] + "%)").formatted(Formatting.AQUA), true);
    }

    private void tickBindings(ServerWorld sw, List<ServerPlayerEntity> fs) {
        RiftLayout L = rift(sw);
        if (L == null) return;
        LeviathanState st = LeviathanState.get(sw);
        if (!fs.isEmpty() && this.age % 160 == 0) strain(sw, 5);                // it never stops pulling
        if (this.age % 4 != 0) return;
        for (int i = 0; i < 4 && i < L.chainEnds.size(); i++) {
            if (st.chains[i] <= 0) continue;
            LeviathanSegmentEntity s = segment(Math.min(SEGMENTS - 2, 2 + i * 3));
            Vec3d to = s != null ? s.getPos().add(0, 1.5, 0) : getPos();
            float g = st.chains[i] / 100f;
            line(sw, Vec3d.ofCenter(world(L.chainEnds.get(i))), to, new DustParticleEffect(new Vector3f(0.4f + 0.5f * (1 - g), 0.45f * g + 0.1f, 0.5f * g + 0.1f), 1.6f), 1.4);
        }
    }

    // ---------------------------------------------------------------- phase 2: THE HUNGER
    private void tickHunger(ServerWorld sw) {
        hunger = Math.min(100, hunger + 0.025f);
        if (hunger >= 95) { if (++starving == 600 && !weak) { weak = true; broadcast(sw, Text.literal("It is STARVING - slower, weaker. Keep it that way!").formatted(Formatting.GREEN), false); } }
        else starving = 0;
        if (this.age % 40 == 0 && hunger >= 60) sw.spawnParticles(new DustParticleEffect(RED, 2f), getX(), getY() + 2, getZ(), 20, 3, 2, 3, 0);
    }

    void feed(ServerWorld sw, float amount, String what) {
        hunger = Math.max(0, hunger - amount);
        heal(getMaxHealth() * 0.012f);
        if (weak) { weak = false; broadcast(sw, Text.literal("It has fed - its strength returns.").formatted(Formatting.RED), true); }
    }

    /** The biggest meal near a player: a powder barge, chum, a boat - or the player. */
    private Vec3d meal(ServerWorld sw, ServerPlayerEntity near) {
        Box b = new Box(centre.x - radius, surfaceY - 8, centre.z - radius, centre.x + radius, surfaceY + 4, centre.z + radius);
        List<PowderBargeEntity> barges = sw.getEntitiesByClass(PowderBargeEntity.class, b, Entity::isAlive);
        if (!barges.isEmpty()) return barges.get(0).getPos();
        List<net.get900.pixelpirates.entity.custom.ChumEntity> chum = sw.getEntitiesByClass(net.get900.pixelpirates.entity.custom.ChumEntity.class, b, Entity::isAlive);
        if (!chum.isEmpty()) return chum.get(0).getPos();
        List<BoatEntity> boats = sw.getEntitiesByClass(BoatEntity.class, b, Entity::isAlive);
        if (!boats.isEmpty() && this.random.nextBoolean()) return boats.get(0).getPos();
        return near.getPos();
    }

    private void breach(ServerWorld sw, Vec3d target, boolean silent) {
        start(sw, silent ? Act.ECLIPSE_HUNT : Act.BREACH, new Vec3d(target.x, floorY, target.z));
        triggerAnim(ACTION, "dive");
        Vec3d mark = new Vec3d(target.x, surfaceY + 1, target.z);
        if (!silent) broadcast(sw, Text.literal("DEVOURING BREACH - get out of the red ring!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
        tasks.add(new Running((w, t) -> {
            if (t % 2 == 0) {
                if (!silent) for (int i = 0; i < 28; i++) { double a = i * Math.PI / 14; w.spawnParticles(new DustParticleEffect(RED, 2.4f), mark.x + Math.cos(a) * 5, mark.y, mark.z + Math.sin(a) * 5, 1, 0, 0, 0, 0); }
                w.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, mark.x, mark.y - 3, mark.z, silent ? 10 : 20, 2, 2, 2, 0.1);
            }
            return t < 40;
        }));
    }

    private void tickBreach(ServerWorld sw, List<ServerPlayerEntity> fs) {
        Vec3d mark = new Vec3d(point.x, surfaceY + 1, point.z);
        if (actT == 40) {                                                   // up it comes, jaws wide
            this.setPosition(point.x, floorY, point.z);
            vel = new Vec3d(0, 1, 0);
            point = new Vec3d(point.x, surfaceY + 18, point.z);
            triggerAnim(ACTION, "breach");
            sw.playSound(null, BlockPos.ofFloored(mark), SoundEvents.ENTITY_GENERIC_SPLASH, SoundCategory.HOSTILE, 6.0f, 0.4f);
        }
        if (actT > 40 && !resolved && getY() >= surfaceY - 2) {
            resolved = true;
            sw.spawnParticles(ParticleTypes.SPLASH, mark.x, mark.y, mark.z, 400, 5, 2, 5, 0.6);
            sw.spawnParticles(ParticleTypes.BUBBLE_POP, mark.x, mark.y, mark.z, 200, 5, 2, 5, 0.4);
            sw.playSound(null, BlockPos.ofFloored(mark), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 6.0f, 0.5f);
            Box bite = new Box(mark.x - 5.5, mark.y - 6, mark.z - 5.5, mark.x + 5.5, mark.y + 5, mark.z + 5.5);
            for (PowderBargeEntity b : sw.getEntitiesByClass(PowderBargeEntity.class, bite, Entity::isAlive)) { b.swallowed(); poisoned(sw); return; }
            for (var c : sw.getEntitiesByClass(net.get900.pixelpirates.entity.custom.ChumEntity.class, bite, Entity::isAlive)) { c.discard(); feed(sw, 40, "chum"); }
            for (BoatEntity b : sw.getEntitiesByClass(BoatEntity.class, bite, Entity::isAlive)) { b.damage(getDamageSources().mobAttack(this), 1000); feed(sw, 25, "a boat"); }
            for (ServerPlayerEntity p : fs) if (bite.contains(p.getPos())) {
                p.damage(getDamageSources().mobAttack(this), weak ? 11f : 16f);
                p.setVelocity((this.random.nextDouble() - 0.5) * 0.6, 1.3, (this.random.nextDouble() - 0.5) * 0.6); p.velocityModified = true;
                feed(sw, 35, "you");
            }
        }
        if (actT > 40 && getY() >= surfaceY + 16) point = new Vec3d(getX() + vel.x * 20, floorY + 4, getZ() + vel.z * 20);   // arc over and dive
        if (actT > 110) end();
    }

    /** THE POISONED MEAL: it swallowed a powder barge. */
    private void poisoned(ServerWorld sw) {
        Vec3d m = mouth();
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, m.x, m.y, m.z, 2, 1, 1, 1, 0);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 120, 3, 3, 3, 0.1);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 6.0f, 0.5f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_HURT, SoundCategory.HOSTILE, 6.0f, 0.5f);
        broadcast(sw, Text.literal("THE POISONED MEAL - the barge goes off inside it! It chokes - STRIKE ITS THROAT!").formatted(Formatting.GOLD, Formatting.BOLD), false);
        hunger = 0;
        float before = getHealth();
        direct(getMaxHealth() * 0.05f);
        gate(sw, before);
        if (retreating) return;
        start(sw, Act.STUNNED, new Vec3d(getX(), surfaceY - 1, getZ()));
        setPoseState(P_STUNNED);
    }

    private void tickStunned(ServerWorld sw) {
        vel = vel.multiply(0.8);
        if (actT % 10 == 0) { Vec3d m = mouth(); sw.spawnParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 10, 1, 1, 1, 0.02); }
        if (actT > 160) { broadcast(sw, Text.literal("It shakes the blast off.").formatted(Formatting.GRAY), true); end(); }
    }

    private void rake(ServerWorld sw, List<ServerPlayerEntity> fs) {
        start(sw, Act.RAKE, null);
        triggerAnim(ACTION, "rake");
        Vec3d c = getPos(), fwd = vel.normalize();
        tasks.add(new Running((w, t) -> {
            if (t < 16 && t % 2 == 0) for (double a = -1; a <= 1; a += 0.1) {
                Vec3d d = rotY(fwd, a);
                for (double r = 4; r < 15; r += 2.5) w.spawnParticles(new DustParticleEffect(WARN, 1.6f), c.x + d.x * r, c.y, c.z + d.z * r, 1, 0, 0.5, 0, 0);
            }
            if (t == 16) {
                w.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 5.0f, 0.4f);
                for (ServerPlayerEntity p : fighters(w)) {
                    Vec3d d = p.getPos().subtract(c);
                    if (d.length() > 15 || Math.abs(d.y) > 8) continue;
                    if (new Vec3d(d.x, 0, d.z).normalize().dotProduct(new Vec3d(fwd.x, 0, fwd.z).normalize()) < Math.cos(Math.toRadians(58))) continue;
                    p.damage(getDamageSources().mobAttack(LeviathanEntity.this), weak ? 7f : 10f);
                    p.setVelocity(-fwd.z * 1.2, 0.4, fwd.x * 1.2); p.velocityModified = true;
                }
            }
            return t < 17;
        }));
    }

    static Vec3d rotY(Vec3d v, double a) { return new Vec3d(v.x * Math.cos(a) - v.z * Math.sin(a), 0, v.x * Math.sin(a) + v.z * Math.cos(a)); }

    private void lure(ServerWorld sw) {
        start(sw, Act.LURE, null);
        triggerAnim(ACTION, "lure");
        broadcast(sw, Text.literal("Its lights flicker in a slow, lovely pattern... you drift toward its mouth.").formatted(Formatting.LIGHT_PURPLE, Formatting.ITALIC), true);
        tasks.add(new Running((w, t) -> {
            Vec3d m = mouth();
            for (ServerPlayerEntity p : fighters(w)) {
                Vec3d d = m.subtract(p.getPos());
                double dist = d.length();
                if (dist > 34 || dist < 2) continue;
                double k = 0.05 * (BossProgression.relicActive(p, ModItems.ROYAL_TIDE_SIGIL) ? 0.5 : 1) * net.get900.pixelpirates.item.BossArmor.pullScale(p);
                p.addVelocity(d.x / dist * k, d.y / dist * k, d.z / dist * k); p.velocityModified = true;
                if (t % 10 == 0) w.spawnParticles(new DustParticleEffect(RED, 1.2f), p.getX(), p.getY() + 1, p.getZ(), 6, 0.4, 0.6, 0.4, 0);
            }
            return t < 60;
        }));
    }

    private void gulp(ServerWorld sw, List<ServerPlayerEntity> fs) {
        start(sw, Act.GULP, null);
        triggerAnim(ACTION, "gulp");
        broadcast(sw, Text.literal("UNDERTOW - it is sucking the bay in!").formatted(Formatting.RED), true);
        tasks.add(new Running((w, t) -> {
            Vec3d m = mouth();
            if (t < 50) for (ServerPlayerEntity p : fighters(w)) {
                Vec3d d = m.subtract(p.getPos());
                double dist = d.length();
                if (dist > 30 || dist < 2) continue;
                double g = 0.08 * net.get900.pixelpirates.item.BossArmor.pullScale(p);
                p.addVelocity(d.x / dist * g, d.y / dist * g, d.z / dist * g); p.velocityModified = true;
            }
            if (t < 50 && t % 3 == 0) w.spawnParticles(ParticleTypes.BUBBLE, m.x, m.y, m.z, 30, 8, 4, 8, -0.3);
            if (t == 50) {
                triggerAnim(ACTION, "spit");
                BlockState[] junk = {Blocks.SPRUCE_PLANKS.getDefaultState(), Blocks.BARREL.getDefaultState(), Blocks.DARK_OAK_LOG.getDefaultState(), Blocks.BONE_BLOCK.getDefaultState()};
                for (ServerPlayerEntity p : fighters(w)) w.spawnEntity(DebrisEntity.fling(w, LeviathanEntity.this, m, p.getPos(), junk[this.random.nextInt(junk.length)], 8f));
                w.playSound(null, BlockPos.ofFloored(m), SoundEvents.ENTITY_LLAMA_SPIT, SoundCategory.HOSTILE, 5.0f, 0.3f);
            }
            return t < 51;
        }));
    }

    private void bloodTide(ServerWorld sw) {
        start(sw, Act.BLOODTIDE, null);
        Vec3d c = getPos();
        broadcast(sw, Text.literal("BLOOD TIDE - the water around it turns red. Get out of it!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
        tasks.add(new Running((w, t) -> {
            if (t % 4 == 0) w.spawnParticles(new DustParticleEffect(RED, 3f), c.x, surfaceY - 4, c.z, 70, 11, 5, 11, 0);
            if (t % 20 == 0) for (ServerPlayerEntity p : fighters(w))
                if (p.isTouchingWater() && Math.hypot(p.getX() - c.x, p.getZ() - c.z) < 22) {
                    p.damage(getDamageSources().magic(), 1.5f);
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0), LeviathanEntity.this);
                }
            return t < 200;
        }));
    }

    // ---------------------------------------------------------------- phase 3: THE UNMAKING
    /** REAR: it wraps the Spire and rears up at the eclipse - its crown and core in reach (the Bane can fire). */
    private void rear(ServerWorld sw) {
        rearCooldown = 460;
        SpireLayout L = spire(sw);
        Vec3d top = L != null ? Vec3d.ofCenter(world(new int[]{0, SpireLayout.TOP, 0})) : getPos().add(0, 30, 0);
        Vec3d beside = top.add(new Vec3d(getX() - top.x, 0, getZ() - top.z).normalize().multiply(11)).add(0, -3, 0);
        start(sw, Act.REAR, beside);
        crownHits = 0;
        broadcast(sw, Text.literal("It RISES against the Spire and roars at the eclipse - its crown is in reach!").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
    }

    private void tickRear(ServerWorld sw, List<ServerPlayerEntity> fs) {
        if (pose() != P_REARED && getPos().distanceTo(point) < 4) {
            setPoseState(P_REARED);
            actT = 0;
            triggerAnim(ACTION, "rear");
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 8.0f, 0.35f);
            for (ServerPlayerEntity p : fs) p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 80, 0), this);   // SWALLOW THE SKY
        }
        if (pose() == P_REARED) {
            vel = point.subtract(getPos()).multiply(0.2);
            if (actT > 140) end();
        } else if (actT > 200) end();
    }

    public boolean reared() { return pose() == P_REARED; }

    /** The Bane struck home (BaneBoltEntity). */
    public void baneStrike() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        start(sw, Act.PINNED, getPos());
        setPoseState(P_PINNED);
        Vec3d c = coreTarget();
        sw.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(new DustParticleEffect(RED, 3f), c.x, c.y, c.z, 300, 3, 3, 3, 0);
        sw.playSound(null, BlockPos.ofFloored(c), SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.HOSTILE, 6.0f, 0.5f);
        sw.playSound(null, BlockPos.ofFloored(c), SoundEvents.ENTITY_ENDER_DRAGON_HURT, SoundCategory.HOSTILE, 6.0f, 0.4f);
        broadcast(sw, Text.literal("THE BANE STRIKES HOME - it is PINNED to the Spire! Everything you have - NOW!").formatted(Formatting.GOLD, Formatting.BOLD), false);
    }

    private void tickPinned(ServerWorld sw) {
        vel = Vec3d.ZERO;
        if (actT % 8 == 0) { Vec3d c = coreTarget(); sw.spawnParticles(new DustParticleEffect(RED, 2f), c.x, c.y, c.z, 20, 1.5, 1.5, 1.5, 0); }
        if (actT > 400) { broadcast(sw, Text.literal("It tears itself off the Bane!").formatted(Formatting.RED), true); end(); }
    }

    @Nullable SpireLayout spire(ServerWorld sw) {
        return LeviathanSites.built(sw.getSeed(), LeviathanRoute.SPIRE, false).layout() instanceof SpireLayout s ? s : null;
    }

    private void apocalypse(ServerWorld sw) {
        start(sw, Act.APOCALYPSE, null);
        triggerAnim(ACTION, "roar");
        double ang = this.random.nextDouble() * Math.PI * 2;
        Vec3d dir = new Vec3d(Math.cos(ang), 0, Math.sin(ang)), side = new Vec3d(-dir.z, 0, dir.x);
        Vec3d c = new Vec3d(centre.x, surfaceY, centre.z);
        double r = radius + 4;
        broadcast(sw, Text.literal("TIDAL APOCALYPSE - a wall of water is coming! Dive under it or ride it out - it breaks over the Spire!").formatted(Formatting.RED, Formatting.BOLD), true);
        java.util.Set<UUID> hit = new java.util.HashSet<>();
        tasks.add(new Running((w, t) -> {
            double along = -r + Math.max(0, t - 30) * (2 * r / 60.0);
            for (double s = -r; s <= r; s += 2.5) {
                Vec3d q = c.add(dir.multiply(t < 30 ? -r : along)).add(side.multiply(s));
                if (Math.hypot(q.x - centre.x, q.z - centre.z) > r) continue;
                w.spawnParticles(t < 30 ? ParticleTypes.BUBBLE : ParticleTypes.SPLASH, q.x, q.y + (t < 30 ? 0 : 2), q.z, t < 30 ? 1 : 6, 0.5, 1.5, 0.5, 0.2);
            }
            if (t >= 30) for (ServerPlayerEntity p : fighters(w)) {
                double pa = p.getPos().subtract(c).dotProduct(dir);
                boolean onSpire = Math.hypot(p.getX() - centre.x, p.getZ() - centre.z) < 16;       // the wave breaks right over the Spire
                if (Math.abs(pa - along) > 2.5 || (!onSpire && p.getY() > surfaceY + 4) || hit.contains(p.getUuid())) continue;
                hit.add(p.getUuid());
                p.damage(getDamageSources().mobAttack(LeviathanEntity.this), 8f);
                p.setVelocity(dir.x * 1.8, 0.5, dir.z * 1.8); p.velocityModified = true;
            }
            return t < 90;
        }));
    }

    /** Is anyone up on the Spire (more than 10 above the water, within 16 of its axis)? */
    private boolean perched(ServerWorld sw, List<ServerPlayerEntity> fs) {
        if (centre == null) return false;
        for (ServerPlayerEntity p : fs) if (p.getY() > surfaceY + 10 && Math.hypot(p.getX() - centre.x, p.getZ() - centre.z) < 16) return true;
        return false;
    }

    /** COIL THE SPIRE: it winds up the tower after whoever hides on it - rings of red climb the Spire, then it crushes
     *  every ledge at once and flings them off into the maelstrom. */
    private void spireCoil(ServerWorld sw) {
        perchTicks = 0;
        start(sw, Act.SPIRE_COIL, null);
        triggerAnim(ACTION, "roar");
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 8.0f, 0.4f);
        broadcast(sw, Text.literal("IT COILS UP THE SPIRE - get off the tower!").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
    }

    private void tickSpireCoil(ServerWorld sw, List<ServerPlayerEntity> fs) {
        double top = SpireLayout.TOP + 2;
        if (actT == 1) { anchorPt = getPos(); coilA0 = Math.atan2(getZ() - centre.z, getX() - centre.x); }
        if (actT <= 64) {                                                   // scripted: it winds up the tower, 1.2 turns in 3 s
            double t = Math.min(1, actT / 60.0), a = coilA0 + actT * 0.12;
            Vec3d helix = new Vec3d(centre.x + Math.cos(a) * 12, surfaceY - 2 + (top - surfaceY + 2) * t, centre.z + Math.sin(a) * 12);
            Vec3d next = actT < 12 ? anchorPt.lerp(helix, actT / 12.0) : helix;          // eases onto the spiral first
            vel = next.subtract(getPos());
            this.setPosition(next);
            double h = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
            float yaw = (float) Math.toDegrees(Math.atan2(-vel.x, vel.z));
            this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
            this.setPitch((float) -Math.toDegrees(Math.atan2(vel.y, h)));
        }
        if (actT < 60 && actT % 4 == 0) {                                   // the warning: rings climbing the tower ahead of it
            double y = surfaceY + (top - surfaceY) * Math.min(1, (actT + 20) / 60.0);
            for (int i = 0; i < 32; i++) { double b = i * Math.PI / 16; sw.spawnParticles(new DustParticleEffect(WARN, 2.2f), centre.x + Math.cos(b) * 11, y, centre.z + Math.sin(b) * 11, 1, 0, 0.3, 0, 0); }
        }
        if (actT == 60) {                                                   // THE CRUSH
            sw.playSound(null, BlockPos.ofFloored(centre.x, top, centre.z), SoundEvents.ENTITY_WARDEN_ATTACK_IMPACT, SoundCategory.HOSTILE, 6.0f, 0.4f);
            sw.spawnParticles(ParticleTypes.EXPLOSION, centre.x, top - 10, centre.z, 12, 6, 12, 6, 0);
            for (ServerPlayerEntity p : fs) {
                double r = Math.hypot(p.getX() - centre.x, p.getZ() - centre.z);
                if (r > 16 || p.getY() < surfaceY - 2) continue;
                p.damage(getDamageSources().mobAttack(this), 13f);
                Vec3d out = new Vec3d(p.getX() - centre.x, 0, p.getZ() - centre.z).normalize();
                p.setVelocity(out.x * 1.8, 0.7, out.z * 1.8); p.velocityModified = true;       // flung off into the lagoon
            }
        }
        if (actT > 80) end();
    }

    private void spin(ServerWorld sw) {
        start(sw, Act.SPIN, null);
        LeviathanHunt.maelstromBoost(sw, 160);
        broadcast(sw, Text.literal("MAELSTROM SPIN - the whirlpool doubles! Hold on to something!").formatted(Formatting.DARK_AQUA, Formatting.BOLD), true);
    }

    private void tickSpin(ServerWorld sw) {
        double a = actT * 0.03 + Math.atan2(getZ() - centre.z, getX() - centre.x);
        point = new Vec3d(centre.x + Math.cos(a + 0.4) * radius * 0.8, surfaceY - 6, centre.z + Math.sin(a + 0.4) * radius * 0.8);
        if (actT > 160) end();
    }

    private void wreckRain(ServerWorld sw, List<ServerPlayerEntity> fs) {
        start(sw, Act.WRECKRAIN, null);
        triggerAnim(ACTION, "spit");
        broadcast(sw, Text.literal("WRECK RAIN - it hurls what is left of Saltmarrow and Brightwater at you!").formatted(Formatting.RED), true);
        BlockState[] junk = {Blocks.SPRUCE_PLANKS.getDefaultState(), Blocks.BARREL.getDefaultState(), Blocks.STONE_BRICKS.getDefaultState(),
                Blocks.WHITE_TERRACOTTA.getDefaultState(), Blocks.DARK_OAK_LOG.getDefaultState(), Blocks.BELL.getDefaultState()};
        Vec3d m = mouth();
        for (ServerPlayerEntity p : fs) for (int k = 0; k < 2; k++) {
            Vec3d to = p.getPos().add(this.random.nextGaussian() * 3, 0, this.random.nextGaussian() * 3);
            sw.spawnEntity(DebrisEntity.fling(sw, this, m.add(0, 4, 0), to, junk[this.random.nextInt(junk.length)], 9f));
        }
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 5.0f, 0.4f);
    }

    private void storm(ServerWorld sw, List<ServerPlayerEntity> fs) {
        start(sw, Act.STORM, null);
        broadcast(sw, Text.literal("STORM CALL - lightning is gathering over you!").formatted(Formatting.YELLOW, Formatting.BOLD), true);
        List<Vec3d> spots = new ArrayList<>();
        for (ServerPlayerEntity p : fs) spots.add(p.getPos());
        tasks.add(new Running((w, t) -> {
            for (Vec3d q : spots) {
                if (t < 30 && t % 3 == 0) for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; w.spawnParticles(ParticleTypes.ELECTRIC_SPARK, q.x + Math.cos(a) * 2.5, q.y + 0.3, q.z + Math.sin(a) * 2.5, 1, 0, 0, 0, 0); }
                if (t == 30) {
                    LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(w);
                    if (bolt != null) { bolt.refreshPositionAfterTeleport(q); w.spawnEntity(bolt); }
                }
            }
            return t < 31;
        }));
    }

    private void memory(ServerWorld sw, List<ServerPlayerEntity> fs) {
        start(sw, Act.MEMORY, null);
        int m = this.random.nextInt(DrownedMemories.NAMES.length);
        broadcast(sw, Text.literal("The Heart's echo, one last time: " + DrownedMemories.NAMES[m] + "!").formatted(Formatting.DARK_AQUA, Formatting.BOLD), true);
        DrownedMemories.play(memoryHost, sw, m, fs, fs.get(this.random.nextInt(fs.size())));
    }

    private final DrownedMemories.Host memoryHost = new DrownedMemories.Host() {
        @Override public LivingEntity self() { return LeviathanEntity.this; }
        @Override public Vec3d centre() { return mouth(); }
        @Override public List<ServerPlayerEntity> fighters(ServerWorld sw) { return LeviathanEntity.this.fighters(sw); }
        @Override public void schedule(DrownedMemories.Task task) { tasks.add(new Running(task::tick)); }
        @Override public double reach() { return 44; }
    };

    // =====================================================================================
    // RETREAT (66% / 33%): it breaks out of its lair and swims for the next one
    // =====================================================================================
    // ---------------------------------------------------------------- BETWEEN LAIRS (LeviathanHunt drives it)
    public boolean traveling() { return travel; }

    /** It has left its lair: from now on LeviathanHunt moves it along the route. */
    public void beginTravel() {
        travel = true;
        retreating = false;
        tasks.clear();
        act = Act.CRUISE; point = null;
        setPoseState(P_SWIM);
        this.dataTracker.set(DARK, false);
        this.dataTracker.set(ECLIPSE, true);
    }

    /** One step along the route (eases onto the path from wherever the retreat left it). */
    public void travelTo(Vec3d pos, Vec3d dir) {
        Vec3d here = getPos();
        Vec3d next = here.squaredDistanceTo(pos) > 40 * 40 ? pos : here.add(pos.subtract(here).multiply(0.12)).add(dir.multiply(0.6 * 0.88));
        vel = next.subtract(here);
        this.setPosition(next);
        float yaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
        this.setYaw(yaw); this.setBodyYaw(yaw); this.setHeadYaw(yaw);
        this.setPitch(0);
        if (this.getWorld() instanceof ServerWorld sw && this.age % 3 == 0) {
            sw.spawnParticles(ParticleTypes.SPLASH, getX(), 63, getZ(), 20, 3, 0.3, 3, 0.3);
            sw.spawnParticles(new DustParticleEffect(RED, 2.5f), getX(), getY() + 3, getZ(), 8, 3, 1.5, 3, 0);
        }
    }

    /** It reached the next lair while someone watched: it becomes that lair's Leviathan, in its next form. */
    public void arrive(String siteId, int form, float health) {
        travel = false;
        centre = null;
        setup(siteId, form, health);
        for (UUID u : segs) if (u != null && this.getWorld() instanceof ServerWorld sw && sw.getEntity(u) instanceof LeviathanSegmentEntity s) { s.setForm(form); s.setCrusted(false); }
        end();
        nextAbility = 100;
    }

    @Override
    public boolean shouldSave() { return !travel && super.shouldSave(); }       // a restart puts a new one on the route

    public void retreat(ServerWorld sw) {
        if (retreating || form() == 3) return;
        retreating = true;
        LeviathanRoute.Route route = LeviathanRoute.of(sw.getSeed());
        LeviathanRoute.Site next = form() == 1 ? route.saltmarrow() : route.brightwater();
        Vec3d dir = new Vec3d(next.x() - getX(), 0, next.z() - getZ()).normalize();
        // toward the next lair but never out of its own: outside the loaded lair it would stop ticking and never hand over
        Vec3d exit = centre.add(dir.multiply(Math.max(10, (site.equals(LeviathanRoute.RIFT) ? leash() : radius) - 10)));
        start(sw, Act.RETREAT, new Vec3d(exit.x, surfaceY - 4, exit.z));
        setPoseState(P_SWIM);
        triggerAnim(ACTION, "roar");
        this.dataTracker.set(ECLIPSE, true);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.HOSTILE, 10.0f, 0.3f);
        broadcast(sw, Text.literal(form() == 1
                ? "The last chain gives. It tears free of the Rift - the sky goes dark as it rises - and it swims for SALTMARROW."
                : "Gorged and bleeding, it screams, spews up a wreck and flees the Gullet - toward BRIGHTWATER.").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
        if (form() == 1) {                                                   // the crust sloughs off in slabs
            for (int i = 0; i < 12; i++) sw.spawnEntity(DebrisEntity.fling(sw, this, getPos().add(0, 3, 0), getPos().add(this.random.nextGaussian() * 14, -20, this.random.nextGaussian() * 14),
                    i % 2 == 0 ? Blocks.TUFF.getDefaultState() : Blocks.DEAD_BRAIN_CORAL_BLOCK.getDefaultState(), 6f));
        } else sw.spawnEntity(DebrisEntity.fling(sw, this, mouth(), mouth().add(vel.multiply(20)).add(0, -10, 0), Blocks.DARK_OAK_PLANKS.getDefaultState(), 8f));
    }

    private void tickRetreat(ServerWorld sw) {
        if (actT % 5 == 0) sw.spawnParticles(new DustParticleEffect(RED, 3f), getX(), getY() + 2, getZ(), 30, 3, 2, 3, 0);
        if (actT > 140) {
            LeviathanState st = LeviathanState.get(sw);
            st.health = getHealth();
            st.markDirty();
            LeviathanHunt.retreated(sw, this);                               // it keeps swimming - now along the route
        }
    }

    // =====================================================================================
    // DAMAGE: the crust, the split spine, the throat, the Bane
    // =====================================================================================
    /** A blow on a body segment (LeviathanSegmentEntity). */
    public boolean onSegmentHit(LeviathanSegmentEntity seg, DamageSource source, float amount) {
        return hurtPart(seg.index(), source, amount, seg);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (travel && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.LIGHTNING_BOLT) || source.isIn(DamageTypeTags.IS_DROWNING)) return false;
        if (form() == 3 && reared() && source.getAttacker() instanceof PlayerEntity && crownShaft()
                && (++crownHits >= 3 || source.isIn(DamageTypeTags.IS_EXPLOSION))) knockShaft();
        return hurtPart(-1, source, amount, null);
    }

    private boolean directHit;

    /** Damage straight through every multiplier (the poisoned meal). */
    void direct(float amount) {
        directHit = true;
        this.timeUntilRegen = 0;
        super.damage(this.getDamageSources().generic(), amount);
        directHit = false;
    }

    /** `part`: -1 = the head, 0..12 = a segment. */
    private boolean hurtPart(int part, DamageSource source, float amount, @Nullable LeviathanSegmentEntity seg) {
        if (!(this.getWorld() instanceof ServerWorld sw) || act == Act.RETREAT || travel) return false;
        // it (and every segment) passes through rock: suffocation, cramming, falls and drowning never touch it
        if (source.isOf(net.minecraft.entity.damage.DamageTypes.IN_WALL) || source.isOf(net.minecraft.entity.damage.DamageTypes.CRAMMING)
                || source.isOf(net.minecraft.entity.damage.DamageTypes.FALL) || source.isIn(DamageTypeTags.IS_DROWNING)) return false;
        if (source.getAttacker() instanceof ServerPlayerEntity p && !BossProgression.eligible(p, chainIndex())) return super.damage(source, amount);   // ModBoss: sealed
        boolean boom = source.isIn(DamageTypeTags.IS_EXPLOSION), shot = source.isIn(DamageTypeTags.IS_PROJECTILE);
        float mult;
        switch (form()) {
            case 1 -> {
                boolean crusted = part < 0 ? headCrusted() : seg != null && seg.crusted();
                if (crusted) {
                    float hp = crustHp.getOrDefault(part, 40f) - amount * (boom ? 3f : shot ? 1.5f : 0.5f);
                    crustHp.put(part, hp);
                    if (hp <= 0) breakCrust(sw, part, seg);
                    else if (source.getAttacker() instanceof ServerPlayerEntity p && this.age % 20 == 0)
                        p.sendMessage(Text.literal("The crust takes the blow. Blast it off - dynamite, cannons, depth charges, harpoons.").formatted(Formatting.GRAY), true);
                    mult = 0.1f;
                } else mult = 1f;
            }
            case 2 -> mult = act == Act.STUNNED ? (part < 0 ? 2.5f : 1.2f) : part < 0 ? 1f : 0.7f;
            default -> mult = act == Act.PINNED ? 3f : part >= 2 && part <= 6 ? 1f : part < 0 ? 0.7f : 0.5f;
        }
        this.timeUntilRegen = 0;
        float before = getHealth();
        boolean hurt = super.damage(source, amount * mult);
        if (hurt && seg != null) triggerAnim(ACTION, "flinch");
        if (hurt) gate(sw, before);
        return hurt;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) { return !directHit && super.isInvulnerableTo(source); }

    /** Never below the phase's floor: at the floor it retreats. */
    private void gate(ServerWorld sw, float before) {
        float max = getMaxHealth();
        if (form() == 1 && getHealth() <= max * 0.66f) { setHealth(max * 0.66f); retreat(sw); }
        else if (form() == 2 && getHealth() <= max * 0.33f) { setHealth(max * 0.33f); retreat(sw); }
    }

    private void breakCrust(ServerWorld sw, int part, @Nullable LeviathanSegmentEntity seg) {
        LeviathanState st = LeviathanState.get(sw);
        Vec3d at = seg != null ? seg.getPos() : getPos();
        if (seg != null) { seg.setCrusted(false); st.crustBroken |= 1L << seg.index(); }
        else { this.dataTracker.set(HEAD_CRUST, false); st.crustBroken |= 1L << 13; }
        st.markDirty();
        sw.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.TUFF.getDefaultState()), at.x, at.y + 2, at.z, 120, 2.5, 2, 2.5, 0.3);
        sw.spawnParticles(new DustParticleEffect(RED, 2.5f), at.x, at.y + 2, at.z, 60, 2, 1.5, 2, 0);
        sw.playSound(null, BlockPos.ofFloored(at), SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 5.0f, 0.5f);
        broadcast(sw, Text.literal("A slab of crust shears away - raw red flesh beneath!").formatted(Formatting.GOLD), true);
    }

    private void knockShaft() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        this.dataTracker.set(CROWN_SHAFT, false);
        SpireLayout L = spire(sw);
        Vec3d drop = L != null ? Vec3d.ofCenter(world(new int[]{0, SpireLayout.TOP + 1, 0})) : getPos();
        ItemEntity item = new ItemEntity(sw, drop.x, drop.y + 1, drop.z, new ItemStack(ModItems.BANE_SHAFT));
        item.setNeverDespawn();
        item.setVelocity(0, 0.2, 0);
        sw.spawnEntity(item);
        sw.playSound(null, BlockPos.ofFloored(drop), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 4.0f, 0.6f);
        broadcast(sw, Text.literal("The BANE SHAFT is torn from its crown and clatters onto the Spire's top! Load the Bane!").formatted(Formatting.GOLD, Formatting.BOLD), false);
    }

    /** The shaft is lost (no item, not loaded): it wears another from the wreckage. */
    public void rearmCrown() { if (!crownShaft()) this.dataTracker.set(CROWN_SHAFT, true); }

    // =====================================================================================
    // death
    // =====================================================================================
    @Override
    public void onDeath(DamageSource source) {
        if (this.getWorld() instanceof ServerWorld sw) {
            discardBody(sw);
            tasks.clear();
            LeviathanHunt.slain(sw, this);
        }
        super.onDeath(source);
    }

    // =====================================================================================
    // helpers + persistence
    // =====================================================================================
    static void line(ServerWorld sw, Vec3d a, Vec3d b, net.minecraft.particle.ParticleEffect fx, double step) {
        double len = a.distanceTo(b);
        for (double s = 0; s <= len; s += step) { Vec3d q = a.add(b.subtract(a).multiply(s / len)); sw.spawnParticles(fx, q.x, q.y, q.z, 1, 0.1, 0.1, 0.1, 0); }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("Site", site);
        nbt.putInt("Form", form());
        nbt.putBoolean("CrownShaft", crownShaft());
        nbt.putBoolean("HeadCrust", headCrusted());
        nbt.putFloat("Hunger", hunger);
        nbt.putDouble("VX", vel.x); nbt.putDouble("VY", vel.y); nbt.putDouble("VZ", vel.z);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Site")) site = nbt.getString("Site");
        if (nbt.contains("Form")) { this.dataTracker.set(FORM, nbt.getInt("Form")); this.dataTracker.set(ECLIPSE, nbt.getInt("Form") >= 2); }
        this.dataTracker.set(CROWN_SHAFT, nbt.getBoolean("CrownShaft"));
        if (nbt.contains("HeadCrust")) this.dataTracker.set(HEAD_CRUST, nbt.getBoolean("HeadCrust"));
        hunger = nbt.getFloat("Hunger");
        vel = new Vec3d(nbt.getDouble("VX"), nbt.getDouble("VY"), nbt.getDouble("VZ"));
        if (vel.lengthSquared() < 1e-6) vel = new Vec3d(0.3, 0, 0);
    }

    public String debugStatus() {
        return "Leviathan (" + site + ", form " + form() + ") at " + getBlockPos().toShortString() + ": hp " + (int) getHealth() + "/" + (int) getMaxHealth()
                + ", act " + act + " t" + actT + ", pose " + pose() + (form() == 2 ? ", hunger " + (int) hunger + (weak ? " WEAK" : "") : "")
                + (form() == 3 ? ", crown shaft " + crownShaft() : "") + ", body " + java.util.Arrays.stream(segs).filter(java.util.Objects::nonNull).count() + "/" + SEGMENTS;
    }

    /** Test: force an action by name (yawn coil lash quake sleepwalk breach rake lure gulp bloodtide apocalypse spin wreckrain storm memory rear retreat poison). */
    public String debug(String op) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return "client";
        List<ServerPlayerEntity> fs = fighters(sw);
        if (fs.isEmpty()) fs = sw.getPlayers(p -> p.squaredDistanceTo(this) < 200 * 200);
        ServerPlayerEntity t = fs.isEmpty() ? null : fs.get(0);
        switch (op) {
            case "yawn" -> yawn(sw);
            case "coil" -> { if (t != null) coil(sw, t); }
            case "lash" -> { if (!fs.isEmpty()) lash(sw, fs); }
            case "quake" -> quake(sw, fs);
            case "sleepwalk" -> { if (t != null) sleepwalk(sw, t); }
            case "breach" -> breach(sw, t != null ? t.getPos() : getPos(), false);
            case "rake" -> rake(sw, fs);
            case "lure" -> lure(sw);
            case "gulp" -> gulp(sw, fs);
            case "bloodtide" -> bloodTide(sw);
            case "apocalypse" -> apocalypse(sw);
            case "spin" -> spin(sw);
            case "wreckrain" -> wreckRain(sw, fs);
            case "storm" -> storm(sw, fs);
            case "memory" -> { if (!fs.isEmpty()) memory(sw, fs); }
            case "spirecoil" -> spireCoil(sw);
            case "rear" -> rear(sw);
            case "retreat" -> retreat(sw);
            case "poison" -> poisoned(sw);
            case "pin" -> baneStrike();
            case "shaft" -> knockShaft();
            default -> { return "unknown: " + op; }
        }
        return debugStatus();
    }

    // ---- tasks
    static final class Running {
        final DrownedMemories.Task task;
        int t;
        Running(DrownedMemories.Task task) { this.task = task; }
    }

    static final class Collections3 {
        static <T> void add(List<T> l, T v, int w) { for (int i = 0; i < w; i++) l.add(v); }
    }
}
