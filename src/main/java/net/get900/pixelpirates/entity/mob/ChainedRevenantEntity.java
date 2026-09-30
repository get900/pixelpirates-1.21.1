package net.get900.pixelpirates.entity.mob;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * BOSS 8/10 - THE CHAINED REVENANT, the hanged pirate lord of the Gallows Grotto (world/dungeon/GallowsGrotto).
 * Drawn and quartered, then hung in chains: only his chains hold him together.
 *
 * DORMANT: HUNG BY THE WRISTS from the central gallows in the pit, invulnerable, over THE GALLOWBRAND IN THE STONE
 * (block/custom/GallowbrandStoneBlock). Pulling the sword (only a player who has earned the fight) snaps the gallows
 * chains: he drops onto the dais with a shockwave and wakes. (Egg/summoned without a lair: wakes when a player comes
 * near, as before.) WHOLE, he fights with his
 * hook and his dragged weight: Chain Whip, Grave Slam, THE NOOSE (hoists you by the neck), GIBBET DROP (an iron cage
 * slams down around you - break out before his fists land).
 * THE SUNDERING: at a split pattern rolled at the start of each battle - 75/50/25% or 66/33% - the chains burst and
 * his arms and legs fly to four of the eight manacle anchors on the pit wall (RevenantPartEntity) while the head hops
 * loose, invulnerable. Every blow on a limb comes off his health; a limb that has taken its share (5% of his max,
 * 6% on the 66/33 pattern) falls. A TIDESHACKLE (Coral Temple) slings the head into a gibbet cage until the limbs are
 * down. When all four have fallen they are reeled back to the head and he REFORMS - dazed for 4 s, taking double
 * damage. Enraged ("Hanged Wrath", 50%): soul fire, wither aura, faster limbs, legs sweep the floor, and a fallen limb
 * left for 20 s is hoisted back onto the wall.
 */
public class ChainedRevenantEntity extends ModBoss {
    private static final TrackedData<Integer> MODE = DataTracker.registerData(ChainedRevenantEntity.class, TrackedDataHandlerRegistry.INTEGER);
    public static final int DORMANT = 0, WHOLE = 1, SPLIT = 2;
    private static final Vector3f CHAIN = new Vector3f(0.4f, 0.42f, 0.46f);
    private static final Vector3f WARN = new Vector3f(0.25f, 0.9f, 0.8f);
    private static final float[][] PATTERNS = {{0.75f, 0.5f, 0.25f}, {0.66f, 0.33f}};

    private final List<int[]> anchors = new ArrayList<>();          // x, y, z, facing (world)
    private final List<BlockPos> cages = new ArrayList<>();
    private int pattern = -1, splits, sunderTicks, reformTicks, stun, reelTicks, hintCooldown;
    private final List<UUID> parts = new ArrayList<>();
    private UUID head;
    // noose + gibbet
    private LivingEntity noosed; private int nooseTicks;
    private final List<BlockPos> cageBars = new ArrayList<>(); private int cageTicks; private Vec3d cageAt; private LivingEntity caged;
    private final Set<UUID> greeted = new HashSet<>();
    // the gallows: chain blocks he hangs from, where he hangs, the sword stone under him, and the drop once released
    private final List<BlockPos> hangChains = new ArrayList<>();
    private BlockPos bladeStone;
    private Vec3d hangAt;
    private boolean released;
    private int dropTicks;
    // THE WALL OF LIGHTS: sea-lantern pairs round the pit wall; one dies every LIGHT_TICKS of fighting, the last -> enrage
    private final List<BlockPos> lights = new ArrayList<>();
    private int lightsOut, lightTicks;
    private static final int LIGHT_TICKS = 300;
    // the limbs' AoE: 0 = THE QUARTERING (sweeping spokes of chain), 1 = THE HANGMAN'S FLOOR (checkerboard trapdoors)
    private int aoeTimer = 200, aoeKind, aoeTicks, aoeStage, aoeStages, aoeWindup, aoeParity;
    private double aoeRot;
    private Vec3d aoeCentre;
    private static final Vector3f DOOM = new Vector3f(0.95f, 0.12f, 0.08f);
    private static final software.bernie.geckolib.core.animation.RawAnimation HANG = software.bernie.geckolib.core.animation.RawAnimation.begin().thenLoop("hang");

    public ChainedRevenantEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(MODE, WHOLE);
    }

    public int mode() { return this.dataTracker.get(MODE); }

    private void setMode(int m) { this.dataTracker.set(MODE, m); }

    /** Worldgen (before the spawn): the manacle anchors + gibbet cages of the Gallows Pit; asleep on the gallows. */
    public void setLair(List<int[]> anchorsWorld, List<BlockPos> cagesWorld) {
        anchors.clear(); anchors.addAll(anchorsWorld);
        cages.clear(); cages.addAll(cagesWorld);
        setMode(DORMANT);
    }

    /** Worldgen (before the spawn, position already set): hung from these gallows chains over the Gallowbrand stone. */
    public void setGallows(List<BlockPos> chainsWorld, BlockPos stone, List<BlockPos> lightWall) {
        lights.clear(); lights.addAll(lightWall);
        hangChains.clear(); hangChains.addAll(chainsWorld);
        bladeStone = stone;
        hangAt = getPos();
        this.setNoGravity(true);
    }

    /** Hanging in the gallows chains, waiting for someone to pull the sword. */
    public boolean isHanging() { return mode() == DORMANT && bladeStone != null && !released; }

    public boolean canBeFreedBy(PlayerEntity p) { return p.isCreative() || mayTarget(p); }

    /** The Gallowbrand was pulled: the chains snap and he drops. He wakes when he lands (mobTick). */
    public void release(PlayerEntity by) {
        if (!(this.getWorld() instanceof ServerWorld sw) || released) return;
        released = true;
        dropTicks = 0;
        for (BlockPos c : hangChains) {
            if (sw.getBlockState(c).isOf(Blocks.CHAIN)) sw.breakBlock(c, false);
            sw.spawnParticles(new DustParticleEffect(CHAIN, 1.6f), c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0);
        }
        this.setNoGravity(false);
        this.setVelocity(0, -0.2, 0);
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 3.0f, 0.4f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 2.5f, 0.4f);
        broadcast(sw, Text.literal("...and the gallows chains SNAP. The Revenant falls!").formatted(Formatting.DARK_AQUA, Formatting.BOLD), false);
    }

    /** With a wall of lights the Revenant enrages when the last one dies, not at half health. */
    @Override
    protected boolean healthEnrage() { return lights.isEmpty(); }

    boolean aoeActive() { return aoeTicks > 0; }

    @Override
    protected software.bernie.geckolib.core.animation.RawAnimation movementOverride() { return mode() == DORMANT ? HANG : null; }

    // ------------------------------------------------------------------ what the renderer + parts ask
    @Override
    public List<String> toggleBones() {
        return List.of("root", "rleg", "rleg_ball", "lleg", "body", "chest", "cloak", "backchain", "head", "rarm", "rarm_fist", "rarm_chain",
                "larm", "larm_fist", "larm_chain");
    }

    @Override
    public boolean isBoneHidden(String bone) { return mode() == SPLIT; }

    @Override
    protected List<String> extraAnims() { return List.of("sunder", "reform", "noose", "gibbet"); }

    boolean canHunt(PlayerEntity p) { return mayTarget(p); }

    /** A hanged man's voice: wither-skeleton rasps and rattling chain, pitched down (the synthesized set was grating). */
    @Override
    protected net.minecraft.sound.SoundEvent sound(String kind) {
        if (mode() == SPLIT && !kind.equals("death")) return null;             // the body is gone while split
        return switch (kind) {
            case "ambient" -> SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT;
            case "hurt" -> SoundEvents.ENTITY_WITHER_SKELETON_HURT;
            case "death" -> SoundEvents.ENTITY_WITHER_SKELETON_DEATH;
            case "attack" -> SoundEvents.BLOCK_CHAIN_BREAK;
            default -> SoundEvents.BLOCK_CHAIN_PLACE;
        };
    }

    @Override
    public float getSoundPitch() { return 0.55f + this.random.nextFloat() * 0.1f; }

    @Override
    public int getMinAmbientSoundDelay() { return 160; }

    float limbIntegrity() { return getMaxHealth() * (pattern == 1 ? 0.06f : 0.05f); }

    /** A free gibbet cage (world pos of its inside), or null when the lair has none. */
    BlockPos freeCage() {
        if (cages.isEmpty()) return null;
        return cages.get(this.random.nextInt(cages.size()));
    }

    @Override
    protected List<Abilities.Ability> activeAbilities() {
        if (mode() != WHOLE || stun > 0 || sunderTicks > 0 || nooseTicks > 0) return List.of();
        return super.activeAbilities();
    }

    @Override
    protected boolean scriptedMotion() { return mode() != WHOLE || stun > 0 || sunderTicks > 0; }

    @Override
    public boolean isAttackable() { return mode() != SPLIT; }

    /** While split the (invisible) body rides on the head: it must not catch the crosshair or projectiles, or every
     *  swing at the head - Tideshackle included - lands on the body instead (the 2026-09-29 playtest bug). */
    @Override
    public boolean canHit() { return mode() != SPLIT && super.canHit(); }

    // ------------------------------------------------------------------ ticking
    @Override
    protected void mobTick() {
        if (mode() == DORMANT) {
            this.getNavigation().stop();
            this.setTarget(null);
            if (hintCooldown > 0) hintCooldown--;
            if (bladeStone != null && this.getWorld() instanceof ServerWorld sw) {
                if (!released) {                                                   // held fast in the chains
                    if (hangAt == null) hangAt = getPos();
                    this.setNoGravity(true);
                    this.setVelocity(Vec3d.ZERO);
                    if (this.squaredDistanceTo(hangAt) > 0.01) this.setPosition(hangAt);
                    if (this.age % 40 == 0) sw.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 7, getZ(), 2, 1.5, 0.5, 0.3, 0.01);
                } else if (this.isOnGround() || ++dropTicks > 60) land(sw);
                return;
            }
            if (this.age % 10 == 0 && this.getWorld() instanceof ServerWorld sw)
                for (ServerPlayerEntity p : sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && p.squaredDistanceTo(this) < 18 * 18))
                    if (mayTarget(p)) { wake(sw); break; }
            return;
        }
        if (stun > 0) { stun--; this.getNavigation().stop(); }
        super.mobTick();
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (hintCooldown > 0) hintCooldown--;
        tickLights(sw);
        if (sunderTicks > 0 && --sunderTicks == 0) doSunder(sw);
        if (mode() == SPLIT) tickSplit(sw);
        if (reformTicks > 0 && --reformTicks == 0) broadcast(sw, Text.literal("The Revenant shakes off its daze.").formatted(Formatting.GRAY), true);
        tickNoose(sw);
        tickGibbet(sw);
        if (isEnraged() && this.age % 40 == 0 && mode() == WHOLE)
            for (LivingEntity e : Abilities.victims(this, 10)) e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 0), this);
    }

    /** He hits the dais: a shockwave of chain and stone, then the roar. */
    private void land(ServerWorld sw) {
        sw.spawnParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.5, getZ(), 6, 2.5, 0.3, 2.5, 0);
        sw.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState()),
                getX(), getY() + 0.3, getZ(), 120, 3, 0.3, 3, 0.2);
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 3.0f, 0.4f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        for (PlayerEntity p : sw.getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative() || p.squaredDistanceTo(this) > 8 * 8) continue;
            Vec3d away = p.getPos().subtract(getPos()).multiply(1, 0, 1);
            away = away.lengthSquared() < 1e-3 ? new Vec3d(0, 0, -1) : away.normalize();
            p.setVelocity(away.x * 1.4, 0.6, away.z * 1.4);
            p.velocityModified = true;
            p.damage(this.getDamageSources().mobAttack(this), 6f);
        }
        wake(sw);
    }

    private void wake(ServerWorld sw) {
        setMode(WHOLE);
        if (pattern < 0) pattern = this.random.nextInt(PATTERNS.length);
        triggerAnim(ACTION, "wrath");
        stun = 40;
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 2.5f, 0.45f);
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 3.0f, 0.4f);
        sw.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 4, getZ(), 80, 1.5, 2, 1.5, 0.05);
        broadcast(sw, Text.literal("\"The rope was not enough. The chains were not enough. NOTHING holds me.\"").formatted(Formatting.DARK_AQUA, Formatting.BOLD), false);
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(this) < 48 * 48))
            if (greeted.add(p.getUuid()))
                p.sendMessage(Text.literal("Only his chains hold the Revenant together. When they burst, break his limbs off the walls - a Tideshackle"
                        + " can lock his head in a gibbet cage.").formatted(Formatting.AQUA), false);
    }

    // ------------------------------------------------------------------ THE SUNDERING
    private void startSunder(ServerWorld sw) {
        splits++;
        sunderTicks = 12;
        stun = 12;
        triggerAnim(ACTION, "sunder");
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 2.5f, 0.5f);
        broadcast(sw, Text.literal("His chains strain... and BURST!").formatted(Formatting.DARK_AQUA, Formatting.BOLD), true);
    }

    private void doSunder(ServerWorld sw) {
        var type = net.get900.pixelpirates.entity.ModEntities.REVENANT_PART;
        List<int[]> pool = new ArrayList<>(anchors);
        if (pool.size() < 4) {                                               // no lair (egg/summon): anchors on a ring around him
            pool.clear();
            for (int k = 0; k < 4; k++) {
                double a = k * Math.PI / 2;
                pool.add(new int[]{(int) (getX() + Math.cos(a) * 12), (int) getY() + 6, (int) (getZ() + Math.sin(a) * 12), k});
            }
        }
        Collections.shuffle(pool, new java.util.Random(this.random.nextLong()));
        RevenantPartEntity.Part[] limbs = {RevenantPartEntity.Part.ARM_R, RevenantPartEntity.Part.ARM_L, RevenantPartEntity.Part.LEG_R, RevenantPartEntity.Part.LEG_L};
        Vec3d fwd = Vec3d.fromPolar(0, bodyYaw), side = new Vec3d(-fwd.z, 0, fwd.x);
        double[][] starts = {{-1.9, 4.5}, {1.9, 4.5}, {-0.8, 1.5}, {0.8, 1.5}};
        parts.clear();
        for (int i = 0; i < 4; i++) {
            RevenantPartEntity p = type.create(sw);
            if (p == null) continue;
            int[] a = pool.get(i);
            Vec3d start = getPos().add(side.multiply(starts[i][0])).add(0, starts[i][1], 0);
            p.launchLimb(this.getUuid(), limbs[i], start, new BlockPos(a[0], a[1], a[2]), a[3], limbIntegrity(), isEnraged(), Vec3d.ofBottomCenter(home()));
            sw.spawnEntity(p);
            parts.add(p.getUuid());
        }
        RevenantPartEntity h = type.create(sw);
        if (h != null) {
            h.launchHead(this.getUuid(), getPos().add(0, 5.5, 0), isEnraged());
            sw.spawnEntity(h);
            head = h.getUuid();
        }
        setMode(SPLIT);
        this.setInvisible(true);
        this.getNavigation().stop();
        this.setTarget(null);
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 3.0f, 0.4f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.6f);
        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + 3, getZ(), 120, 1.5, 2, 1.5, 0.1);
    }

    // ------------------------------------------------------------------ THE WALL OF LIGHTS
    private void tickLights(ServerWorld sw) {
        if (lights.isEmpty() || lightsOut >= lights.size()) return;
        boolean anyone = !sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(this) < 48 * 48 && mayTarget(p)).isEmpty();
        if (!anyone || ++lightTicks < LIGHT_TICKS) return;
        lightTicks = 0;
        BlockPos b = lights.get(lightsOut++);
        for (int dy = 0; dy <= 1; dy++) {
            BlockPos q = b.up(dy);
            if (sw.getBlockState(q).isOf(Blocks.SEA_LANTERN)) sw.setBlockState(q, Blocks.DARK_PRISMARINE.getDefaultState());
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, q.getX() + 0.5, q.getY() + 0.5, q.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.02);
        }
        sw.playSound(null, b, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS, 2.5f, 0.5f);
        sw.playSound(null, b, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.BLOCKS, 2.0f, 0.6f);
        int left = lights.size() - lightsOut;
        if (left > 0) broadcast(sw, Text.literal("A light gutters out... " + left + " remain").formatted(left <= 3 ? Formatting.RED : Formatting.DARK_AQUA), true);
        else {
            broadcast(sw, Text.literal("The last light dies. In the dark, the Revenant laughs.").formatted(Formatting.DARK_RED, Formatting.BOLD), false);
            for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(this) < 64 * 64))
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.DARKNESS, 100, 0), this);
            forceEnrage();
        }
    }

    private void relight(ServerWorld sw) {
        for (BlockPos b : lights) for (int dy = 0; dy <= 1; dy++)
            if (sw.getBlockState(b.up(dy)).isOf(Blocks.DARK_PRISMARINE)) sw.setBlockState(b.up(dy), Blocks.SEA_LANTERN.getDefaultState());
        lightsOut = 0; lightTicks = 0;
    }

    private void tickSplit(ServerWorld sw) {
        this.setVelocity(Vec3d.ZERO);
        tickAoe(sw);
        // keep the (invisible) body under the head so the boss bar + leash follow the fight
        RevenantPartEntity h = headEntity(sw);
        if (h != null && reelTicks == 0) this.setPosition(h.getX(), h.getY(), h.getZ());
        if (reelTicks > 0 && --reelTicks == 0) reform(sw);
    }

    // ------------------------------------------------------------------ the limbs' AoE (only while split)
    private List<RevenantPartEntity> mountedLimbs(ServerWorld sw) {
        List<RevenantPartEntity> out = new ArrayList<>();
        for (UUID u : parts) if (sw.getEntity(u) instanceof RevenantPartEntity p && p.state() == RevenantPartEntity.State.MOUNTED) out.add(p);
        return out;
    }

    private Vec3d arenaFloor(ServerWorld sw) {
        if (bladeStone != null) return Vec3d.ofBottomCenter(bladeStone).subtract(0, 1, 0);
        return findFloor(sw, getPos());
    }

    private void tickAoe(ServerWorld sw) {
        if (aoeTicks == 0) {
            if (--aoeTimer > 0) return;
            List<RevenantPartEntity> limbs = mountedLimbs(sw);
            if (limbs.size() < 2) { aoeTimer = 40; return; }
            aoeKind = 1 - aoeKind;
            aoeCentre = arenaFloor(sw);
            aoeStage = 0;
            aoeStages = aoeKind == 0 ? (isEnraged() ? 4 : 3) : (isEnraged() ? 3 : 2);
            aoeRot = this.random.nextDouble() * Math.PI;
            aoeParity = this.random.nextInt(2);
            aoeWindup = aoeKind == 0 ? (isEnraged() ? 32 : 40) : (isEnraged() ? 40 : 50);
            aoeTicks = 1;
            for (RevenantPartEntity p : limbs) p.triggerAnim("action", "attack");
            sw.playSound(null, BlockPos.ofFloored(aoeCentre), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 3.0f, 0.4f);
            broadcast(sw, (aoeKind == 0 ? Text.literal("THE QUARTERING - the limbs cast their chains across the pit! Get between them!")
                    : Text.literal("THE HANGMAN'S FLOOR - the trapdoors are marked! Stand where the floor holds!")).formatted(Formatting.RED, Formatting.BOLD), true);
            return;
        }
        aoeTicks++;
        int t = aoeTicks;
        boolean strike = t == aoeWindup;
        if (aoeKind == 0) quartering(sw, t, strike); else hangmansFloor(sw, t, strike);
        if (strike) {
            aoeStage++;
            if (aoeStage >= aoeStages) { aoeTicks = 0; aoeTimer = isEnraged() ? 170 : 240; return; }
            aoeTicks = 1;
            aoeWindup = Math.max(18, aoeWindup - 10);                               // each wave comes faster
            if (aoeKind == 0) aoeRot += Math.PI / spokes();                              // the next spokes fall between the last
            else aoeParity ^= 1;                                                         // the other squares drop
            for (RevenantPartEntity p : mountedLimbs(sw)) p.triggerAnim("action", "attack");
        }
    }

    private int spokes() { return isEnraged() ? 6 : 4; }

    /** THE QUARTERING: chains cast from the wall limbs to the centre, then flung out along spokes that sweep the pit. */
    private void quartering(ServerWorld sw, int t, boolean strike) {
        int n = spokes();
        if (t % 3 == 0 || strike) {
            for (RevenantPartEntity p : mountedLimbs(sw)) {                               // chains from every limb to the centre
                Vec3d a = p.getPos().add(0, 1, 0), d = aoeCentre.add(0, 1, 0).subtract(a);
                for (double k = 0; k <= 1; k += 0.08) { Vec3d q = a.add(d.multiply(k)); sw.spawnParticles(new DustParticleEffect(CHAIN, 1.3f), q.x, q.y, q.z, 1, 0, 0, 0, 0); }
            }
        }
        for (int s = 0; s < n; s++) {
            double a = aoeRot + s * 2 * Math.PI / n, ux = Math.cos(a), uz = Math.sin(a);
            for (double r = 1.5; r <= 29; r += 0.5) {
                double x = aoeCentre.x + ux * r, z = aoeCentre.z + uz * r;
                if (strike) {
                    sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, aoeCentre.y + 0.3, z, 2, 0.2, 0.3, 0.2, 0.02);
                    if (((int) (r * 2)) % 3 == 0) sw.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK,
                            Blocks.COBBLED_DEEPSLATE.getDefaultState()), x, aoeCentre.y + 0.2, z, 4, 0.3, 0.1, 0.3, 0.1);
                } else if (t % 2 == 0) {
                    float size = t > aoeWindup - 12 ? 2.6f : 1.9f;                          // flares just before it lands
                    sw.spawnParticles(new DustParticleEffect(DOOM, size), x, aoeCentre.y + 0.15, z, 2, 0.35, 0, 0.35, 0);
                    if (t > aoeWindup - 12 && ((int) (r * 2)) % 4 == 0)
                        sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, aoeCentre.y + 0.2, z, 1, 0.1, 0.05, 0.1, 0.01);
                }
            }
        }
        if (!strike) return;
        sw.playSound(null, BlockPos.ofFloored(aoeCentre), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 3.0f, 0.4f);
        sw.playSound(null, BlockPos.ofFloored(aoeCentre), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.0f, 0.5f);
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && canHunt(p)
                && Math.abs(p.getY() - aoeCentre.y) < 4 && p.squaredDistanceTo(aoeCentre.x, p.getY(), aoeCentre.z) < 30 * 30)) {
            double px = p.getX() - aoeCentre.x, pz = p.getZ() - aoeCentre.z;
            for (int s = 0; s < n; s++) {
                double a = aoeRot + s * 2 * Math.PI / n, ux = Math.cos(a), uz = Math.sin(a);
                double along = px * ux + pz * uz, across = Math.abs(-px * uz + pz * ux);
                if (along > 0.5 && across < 1.8) {
                    p.damage(this.getDamageSources().mobAttack(this), isEnraged() ? 15f : 11f);
                    double side = (-px * uz + pz * ux) >= 0 ? 1 : -1;
                    p.setVelocity(-uz * side * 1.1, 0.45, ux * side * 1.1);
                    p.velocityModified = true;
                    break;
                }
            }
        }
    }

    /** THE HANGMAN'S FLOOR: the pit floor becomes a checkerboard of 5x5 trapdoors - the marked ones drop out. */
    private void hangmansFloor(ServerWorld sw, int t, boolean strike) {
        final int C = 5;
        if (!strike && t % 3 != 0) return;
        for (int i = -6; i <= 6; i++) for (int j = -6; j <= 6; j++) {
            if (((i + j) & 1) != aoeParity) continue;
            double x = aoeCentre.x + i * C, z = aoeCentre.z + j * C;
            if ((i * i + j * j) * C * C > 29 * 29) continue;
            if (strike) {
                sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, x, aoeCentre.y + 0.3, z, 10, 1.6, 0.2, 1.6, 0.03);
                sw.spawnParticles(new net.minecraft.particle.BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.DARK_OAK_PLANKS.getDefaultState()),
                        x, aoeCentre.y + 0.2, z, 12, 1.6, 0.1, 1.6, 0.15);
            } else {
                float size = t > aoeWindup - 14 ? 2.6f : 1.9f;
                sw.spawnParticles(new DustParticleEffect(DOOM, size), x, aoeCentre.y + 0.15, z, 26, 1.5, 0.02, 1.5, 0);
                if (t > aoeWindup - 16) sw.spawnParticles(new DustParticleEffect(CHAIN, 1.0f), x, aoeCentre.y + 3.5, z, 2, 1.2, 1.2, 1.2, 0);  // chains lowering
            }
        }
        if (!strike) return;
        sw.playSound(null, BlockPos.ofFloored(aoeCentre), SoundEvents.BLOCK_WOODEN_TRAPDOOR_OPEN, SoundCategory.HOSTILE, 3.0f, 0.5f);
        sw.playSound(null, BlockPos.ofFloored(aoeCentre), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 3.0f, 0.5f);
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && !p.isCreative() && canHunt(p)
                && Math.abs(p.getY() - aoeCentre.y) < 4 && p.squaredDistanceTo(aoeCentre.x, p.getY(), aoeCentre.z) < 30 * 30)) {
            int i = (int) Math.round((p.getX() - aoeCentre.x) / C), j = (int) Math.round((p.getZ() - aoeCentre.z) / C);
            if (((i + j) & 1) != aoeParity) continue;
            p.damage(this.getDamageSources().mobAttack(this), isEnraged() ? 16f : 12f);
            p.setVelocity(p.getVelocity().x * 0.2, -1.0, p.getVelocity().z * 0.2);      // yanked down by the noose
            p.velocityModified = true;
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 50, 2), this);
            p.sendMessage(Text.literal("The floor gives way beneath you - the rope jerks tight!").formatted(Formatting.DARK_RED), true);
        }
    }

    private RevenantPartEntity headEntity(ServerWorld sw) { return head != null && sw.getEntity(head) instanceof RevenantPartEntity p ? p : null; }

    /** Called by a limb as it falls. When all four are down: reel them in to the head. */
    void onLimbFallen() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        int down = 0, total = 0;
        for (UUID u : parts) if (sw.getEntity(u) instanceof RevenantPartEntity p) { total++; if (p.isDown()) down++; }
        broadcast(sw, Text.literal("A limb crashes to the floor (" + down + "/" + total + ")").formatted(Formatting.GOLD), true);
        if (down < total) return;
        RevenantPartEntity h = headEntity(sw);
        Vec3d at = h != null ? h.getPos() : getPos();
        if (h != null && h.state() == RevenantPartEntity.State.CAGED) {            // released from its cage, dropped to the floor
            BlockPos.Mutable m = BlockPos.ofFloored(at).mutableCopy();
            for (int i = 0; i < 30 && sw.getBlockState(m.down()).getCollisionShape(sw, m.down()).isEmpty(); i++) m.move(0, -1, 0);
            at = new Vec3d(at.x, m.getY() - 1, at.z);
            BlockPos below = BlockPos.ofFloored(h.getPos()).down();
            if (sw.getBlockState(below).isOf(Blocks.DARK_OAK_SLAB)) at = new Vec3d(at.x, at.y, at.z);
        }
        final Vec3d target = findFloor(sw, at);
        for (UUID u : parts) if (sw.getEntity(u) instanceof RevenantPartEntity p) p.reelTo(target.add(0, 1, 0), 30);
        if (h != null) h.reelTo(target, 20);
        reelTicks = 34;
        sw.playSound(null, BlockPos.ofFloored(target), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 3.0f, 0.3f);
        broadcast(sw, Text.literal("The chains drag his pieces back together...").formatted(Formatting.DARK_AQUA), false);
    }

    private Vec3d findFloor(ServerWorld sw, Vec3d p) {
        BlockPos.Mutable m = BlockPos.ofFloored(p).mutableCopy();
        for (int i = 0; i < 40 && sw.getBlockState(m.down()).getCollisionShape(sw, m.down()).isEmpty(); i++) m.move(0, -1, 0);
        return new Vec3d(p.x, m.getY(), p.z);
    }

    private void reform(ServerWorld sw) {
        RevenantPartEntity h = headEntity(sw);
        Vec3d at = h != null ? findFloor(sw, h.getPos()) : getPos();
        for (UUID u : parts) if (sw.getEntity(u) instanceof RevenantPartEntity p) p.discard();
        if (h != null) h.discard();
        parts.clear();
        head = null;
        aoeTicks = 0; aoeTimer = 200;
        this.refreshPositionAndAngles(at.x, at.y, at.z, getYaw(), 0);
        this.setInvisible(false);
        setMode(WHOLE);
        stun = 80;
        reformTicks = 80;
        wholeDamage = 0;
        while (pattern >= 0 && splits < PATTERNS[pattern].length && getHealth() < getMaxHealth() * PATTERNS[pattern][splits] - getMaxHealth() * WHOLE_GAP) splits++;
        triggerAnim(ACTION, "reform");
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.5f, 0.5f);
        sw.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 3, getZ(), 60, 1, 2, 1, 0.05);
        broadcast(sw, Text.literal("REFORGED - and reeling! Strike now!").formatted(Formatting.GOLD, Formatting.BOLD), true);
    }

    // ------------------------------------------------------------------ damage
    /** A mounted limb was hit: the blow comes off his health, but never more than that limb's remaining share
     *  ({@code cap}) - one huge blow used to punch straight through a limb into his next split. Returns what landed. */
    float takeLimbDamage(DamageSource source, float amount, float cap) {
        float before = getHealth();
        limbHit = true;
        this.timeUntilRegen = 0;                 // each limb is its own target: the body's hit cooldown must not eat blows on other limbs
        super.damage(source, amount);
        limbHit = false;
        if (isAlive() && before - getHealth() > cap) setHealth(Math.max(1f, before - Math.max(0, cap)));
        return Math.max(0, before - getHealth());
    }

    /** Damage taken WHOLE since he last reformed: the next sunder waits for a real fight first (see damage()). */
    private float wholeDamage = Float.MAX_VALUE;
    static final float WHOLE_GAP = 0.15f, NO_SPLIT_BELOW = 0.10f;

    private boolean limbHit;

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (mode() == DORMANT && bladeStone != null) {                             // hung in the gallows: untouchable
            if (source.isIn(net.minecraft.registry.tag.DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
            if (source.getAttacker() instanceof ServerPlayerEntity p && hintCooldown <= 0) {
                hintCooldown = 60;
                p.sendMessage(Text.literal("The gallows chains hold him fast. Only the blade in the stone beneath him can set him loose.")
                        .formatted(Formatting.GRAY), true);
            }
            return false;
        }
        if (mode() == DORMANT && source.getAttacker() instanceof ServerPlayerEntity p && mayTarget(p) && this.getWorld() instanceof ServerWorld sw) wake(sw);
        if (mode() == SPLIT && !limbHit) {
            if (source.getAttacker() instanceof ServerPlayerEntity p && hintCooldown == 0) {
                hintCooldown = 60;
                p.sendMessage(Text.literal("There's no body to strike - break the limbs on the walls!").formatted(Formatting.GRAY), true);
            }
            return false;
        }
        float mult = reformTicks > 0 ? 2f : 1f;
        float before = getHealth();
        boolean hurt = super.damage(source, amount * mult);
        if (hurt && mode() == WHOLE) wholeDamage += Math.max(0, before - getHealth());
        // the limbs take ~20% between them, which used to leave him ON his next threshold: one hit after reforming and he
        // split again. Now a split also needs 15% of his health taken whole since he reformed (and none below 10%).
        if (hurt && mode() == WHOLE && pattern >= 0 && splits < PATTERNS[pattern].length && getHealth() < getMaxHealth() * PATTERNS[pattern][splits]
                && wholeDamage >= getMaxHealth() * WHOLE_GAP && getHealth() > getMaxHealth() * NO_SPLIT_BELOW
                && this.getWorld() instanceof ServerWorld sw && sunderTicks == 0) startSunder(sw);
        return hurt;
    }

    // ------------------------------------------------------------------ THE NOOSE
    void startNoose(LivingEntity t) {
        if (t == null) return;
        noosed = t;
        nooseTicks = 50;
        if (t instanceof ServerPlayerEntity p) p.sendMessage(Text.literal("A noose of chain snaps tight around your neck!").formatted(Formatting.DARK_AQUA), true);
    }

    private void tickNoose(ServerWorld sw) {
        if (nooseTicks <= 0 || noosed == null) return;
        nooseTicks--;
        if (!noosed.isAlive()) { noosed = null; nooseTicks = 0; return; }
        Vec3d hand = getPos().add(Vec3d.fromPolar(0, bodyYaw).multiply(2)).add(0, 5, 0);
        Vec3d d = noosed.getEyePos().subtract(hand);
        if (nooseTicks % 2 == 0) for (double k = 0; k <= 1; k += 0.06) { Vec3d q = hand.add(d.multiply(k)); sw.spawnParticles(new DustParticleEffect(CHAIN, 1.2f), q.x, q.y, q.z, 1, 0, 0, 0, 0); }
        noosed.setVelocity(noosed.getVelocity().x * 0.5, 0.22, noosed.getVelocity().z * 0.5);        // hoisted
        noosed.velocityModified = true;
        noosed.fallDistance = 0;
        if (nooseTicks % 10 == 0) {
            noosed.damage(this.getDamageSources().mobAttack(this), 2f);
            noosed.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0), this);
        }
        if (nooseTicks == 0) {
            noosed.setVelocity(0, -1.2, 0);                                    // dropped
            noosed.velocityModified = true;
            sw.playSound(null, noosed.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 2.0f, 0.6f);
            noosed = null;
        }
    }

    // ------------------------------------------------------------------ GIBBET DROP
    void startGibbet(LivingEntity t) {
        if (t == null || !(this.getWorld() instanceof ServerWorld sw)) return;
        caged = t;
        cageAt = t.getPos();
        cageTicks = 100;
        BlockPos c = BlockPos.ofFloored(cageAt);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 3; y++) {
            boolean ring = Math.abs(x) == 1 || Math.abs(z) == 1;
            if (!(ring && y <= 2) && !(y == 3)) continue;
            BlockPos p = c.add(x, y, z);
            if (!sw.getBlockState(p).isAir()) continue;
            BlockState bars = Blocks.IRON_BARS.getDefaultState();
            sw.setBlockState(p, y == 3 ? Blocks.DARK_OAK_SLAB.getDefaultState() : bars);
            cageBars.add(p.toImmutable());
        }
        sw.playSound(null, c, SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 2.0f, 0.5f);
        sw.playSound(null, c, SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.HOSTILE, 2.0f, 0.5f);
        if (t instanceof ServerPlayerEntity p) p.sendMessage(Text.literal("A GIBBET CAGE SLAMS DOWN - break out before he strikes!").formatted(Formatting.RED, Formatting.BOLD), true);
    }

    private void tickGibbet(ServerWorld sw) {
        if (cageTicks <= 0) return;
        cageTicks--;
        if (cageTicks == 70) {                                               // the blow lands (the "gibbet" clip smashes at 1.3 s)
            sw.spawnParticles(ParticleTypes.EXPLOSION, cageAt.x, cageAt.y + 1, cageAt.z, 3, 0.8, 0.5, 0.8, 0);
            sw.playSound(null, BlockPos.ofFloored(cageAt), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.0f, 0.6f);
            if (caged != null && caged.isAlive() && caged.getPos().squaredDistanceTo(cageAt) < 1.6 * 1.6)
                caged.damage(this.getDamageSources().mobAttack(this), 16f);
        } else if (cageTicks > 70 && cageTicks % 3 == 0) {
            for (int i = 0; i < 12; i++) {
                double a = Math.PI * 2 * i / 12;
                sw.spawnParticles(new DustParticleEffect(WARN, 1.3f), cageAt.x + Math.cos(a) * 1.6, cageAt.y + 0.1, cageAt.z + Math.sin(a) * 1.6, 1, 0, 0, 0, 0);
            }
        }
        if (cageTicks == 0) {                                                // the cage rusts away
            for (BlockPos p : cageBars) {
                BlockState s = sw.getBlockState(p);
                if (s.isOf(Blocks.IRON_BARS) || s.isOf(Blocks.DARK_OAK_SLAB)) sw.setBlockState(p, Blocks.AIR.getDefaultState());
            }
            cageBars.clear();
            caged = null;
        }
    }

    @Override
    protected void onEnrage() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        for (UUID u : parts) if (sw.getEntity(u) instanceof RevenantPartEntity p) p.setRage(true);
        RevenantPartEntity h = headEntity(sw);
        if (h != null) h.setRage(true);
        broadcast(sw, Text.literal("Soul-fire pours out through every seam - HANGED WRATH!").formatted(Formatting.DARK_AQUA, Formatting.BOLD), false);
    }

    @Override
    public void onDeath(DamageSource source) {
        if (this.getWorld() instanceof ServerWorld sw) {
            if (!lights.isEmpty()) {
                relight(sw);
                broadcast(sw, Text.literal("Every light in the pit flares back to life. Far above, an old man lets out his breath.").formatted(Formatting.AQUA), false);
            }
            for (UUID u : parts) if (sw.getEntity(u) instanceof RevenantPartEntity p) p.discard();
            RevenantPartEntity h = headEntity(sw);
            if (h != null) { this.setPosition(h.getPos()); h.discard(); }
            this.setInvisible(false);
            for (BlockPos p : cageBars) if (sw.getBlockState(p).isOf(Blocks.IRON_BARS)) sw.setBlockState(p, Blocks.AIR.getDefaultState());
        }
        super.onDeath(source);
    }

    private void broadcast(ServerWorld sw, Text t, boolean actionbar) {
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(this) < 64 * 64)) p.sendMessage(t, actionbar);
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Mode", mode());
        nbt.putInt("Pattern", pattern);
        nbt.putInt("Splits", splits);
        nbt.putFloat("WholeDamage", wholeDamage);
        NbtList a = new NbtList();
        for (int[] an : anchors) { NbtCompound c = new NbtCompound(); c.putIntArray("A", an); a.add(c); }
        nbt.put("Anchors", a);
        NbtList c = new NbtList();
        for (BlockPos p : cages) c.add(NbtHelper.fromBlockPos(p));
        nbt.put("Cages", c);
        NbtList ps = new NbtList();
        for (UUID u : parts) ps.add(NbtHelper.fromUuid(u));
        nbt.put("Parts", ps);
        if (head != null) nbt.putUuid("HeadPart", head);
        NbtList hc = new NbtList();
        for (BlockPos p : hangChains) hc.add(NbtHelper.fromBlockPos(p));
        nbt.put("HangChains", hc);
        if (bladeStone != null) nbt.put("BladeStone", NbtHelper.fromBlockPos(bladeStone));
        if (hangAt != null) { nbt.putDouble("HangX", hangAt.x); nbt.putDouble("HangY", hangAt.y); nbt.putDouble("HangZ", hangAt.z); }
        nbt.putBoolean("Released", released);
        NbtList lw = new NbtList();
        for (BlockPos p : lights) lw.add(NbtHelper.fromBlockPos(p));
        nbt.put("Lights", lw);
        nbt.putInt("LightsOut", lightsOut);
        nbt.putInt("LightTicks", lightTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Mode")) setMode(nbt.getInt("Mode"));
        pattern = nbt.contains("Pattern") ? nbt.getInt("Pattern") : -1;
        splits = nbt.getInt("Splits");
        if (nbt.contains("WholeDamage")) wholeDamage = nbt.getFloat("WholeDamage");
        anchors.clear();
        for (NbtElement e : nbt.getList("Anchors", NbtElement.COMPOUND_TYPE)) anchors.add(((NbtCompound) e).getIntArray("A"));
        cages.clear();
        for (NbtElement e : nbt.getList("Cages", NbtElement.COMPOUND_TYPE)) cages.add(NbtHelper.toBlockPos((NbtCompound) e));
        parts.clear();
        for (NbtElement e : nbt.getList("Parts", NbtElement.INT_ARRAY_TYPE)) parts.add(NbtHelper.toUuid(e));
        if (nbt.containsUuid("HeadPart")) head = nbt.getUuid("HeadPart");
        hangChains.clear();
        for (NbtElement e : nbt.getList("HangChains", NbtElement.COMPOUND_TYPE)) hangChains.add(NbtHelper.toBlockPos((NbtCompound) e));
        bladeStone = nbt.contains("BladeStone") ? NbtHelper.toBlockPos(nbt.getCompound("BladeStone")) : null;
        hangAt = nbt.contains("HangX") ? new Vec3d(nbt.getDouble("HangX"), nbt.getDouble("HangY"), nbt.getDouble("HangZ")) : null;
        released = nbt.getBoolean("Released");
        lights.clear();
        for (NbtElement e : nbt.getList("Lights", NbtElement.COMPOUND_TYPE)) lights.add(NbtHelper.toBlockPos((NbtCompound) e));
        lightsOut = nbt.getInt("LightsOut");
        lightTicks = nbt.getInt("LightTicks");
        this.setInvisible(mode() == SPLIT);
    }

    // ------------------------------------------------------------------ /ppboss revenant (testing)
    public String debugStatus() {
        String m = switch (mode()) { case DORMANT -> "DORMANT"; case SPLIT -> "SPLIT"; default -> "WHOLE"; };
        if (mode() == DORMANT && bladeStone != null) m = released ? "FALLING" : "HANGING (chains " + hangChains.size() + ", stone " + bladeStone.toShortString() + ")";
        StringBuilder s = new StringBuilder("Revenant at " + getBlockPos().toShortString() + ": " + m + ", hp " + (int) getHealth() + "/" + (int) getMaxHealth()
                + ", pattern " + (pattern < 0 ? "?" : pattern == 0 ? "75/50/25" : "66/33") + ", splits " + splits + ", anchors " + anchors.size() + ", cages " + cages.size());
        if (this.getWorld() instanceof ServerWorld sw) {
            for (UUID u : parts) if (sw.getEntity(u) instanceof RevenantPartEntity p) s.append(", ").append(p.part()).append("=").append(p.state());
            RevenantPartEntity h = headEntity(sw);
            if (h != null) s.append(", HEAD=").append(h.state());
        }
        if (reformTicks > 0) s.append(", REFORGED x2");
        if (!lights.isEmpty()) s.append(", lights ").append(lights.size() - lightsOut).append("/").append(lights.size());
        if (aoeTicks > 0) s.append(aoeKind == 0 ? ", QUARTERING" : ", HANGMAN'S FLOOR").append(" wave ").append(aoeStage + 1);
        if (isEnraged()) s.append(", HANGED WRATH");
        return s.toString();
    }

    public void debugWake() {
        if (!(this.getWorld() instanceof ServerWorld sw) || mode() != DORMANT) return;
        if (isHanging()) {                                                          // as if the sword were pulled
            if (sw.getBlockState(bladeStone).getBlock() instanceof net.get900.pixelpirates.block.custom.GallowbrandStoneBlock)
                sw.setBlockState(bladeStone, sw.getBlockState(bladeStone).with(net.get900.pixelpirates.block.custom.GallowbrandStoneBlock.HAS_SWORD, false));
            release(null);
        } else wake(sw);
    }

    public void debugSunder() { if (this.getWorld() instanceof ServerWorld sw && mode() == WHOLE) { if (pattern < 0) pattern = 0; startSunder(sw); } }

    /** Test: fire the limbs' AoE now (kind 0 quartering, 1 floor), or burn the lights down to `left`. */
    public void debugAoe(int kind) { aoeKind = 1 - kind; aoeTimer = 1; }

    public void debugLights(int left) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        while (lights.size() - lightsOut > Math.max(0, left)) { lightTicks = LIGHT_TICKS; tickLightsForce(sw); }
    }

    private void tickLightsForce(ServerWorld sw) {
        int before = lightsOut;
        lightTicks = LIGHT_TICKS - 1;
        // tickLights needs an eligible player nearby; the test path skips that check
        BlockPos b = lights.get(lightsOut);
        lightsOut++;
        for (int dy = 0; dy <= 1; dy++) if (sw.getBlockState(b.up(dy)).isOf(Blocks.SEA_LANTERN)) sw.setBlockState(b.up(dy), Blocks.DARK_PRISMARINE.getDefaultState());
        if (lightsOut >= lights.size() && before < lights.size()) forceEnrage();
        lightTicks = 0;
    }

    public void debugBreakLimbs() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        for (UUID u : List.copyOf(parts))
            if (sw.getEntity(u) instanceof RevenantPartEntity p && p.state() == RevenantPartEntity.State.MOUNTED)
                p.damage(this.getDamageSources().generic(), limbIntegrity() * 2);
    }

    // =====================================================================================
    // ability effects (wired in MobSpecs)
    // =====================================================================================
    public static Abilities.Effect noose() { return (mob, t) -> { if (mob instanceof ChainedRevenantEntity r) r.startNoose(t); }; }

    public static Abilities.Effect gibbet() { return (mob, t) -> { if (mob instanceof ChainedRevenantEntity r) r.startGibbet(t); }; }
}
