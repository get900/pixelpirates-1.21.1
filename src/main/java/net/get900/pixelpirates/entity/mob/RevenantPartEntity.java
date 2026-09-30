package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.item.ModItems;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A torn-off piece of the Chained Revenant (ChainedRevenantEntity): an arm, a leg or the head.
 * LIMBS fly to a manacle anchor on the arena wall and hang there as turrets - the right arm hurls its chain hook
 * (drags you to the wall), the left throws shackles (roots you), the legs stomp rubble down from the ceiling (and,
 * enraged, kick a chain sweep along the floor). Every blow on a limb comes off the Revenant's own health; when a
 * limb has taken its share it FALLS to the floor, twitching. The HEAD hops after you, biting and shrieking, and cannot
 * be hurt - but strike it with a TIDESHACKLE and it is slung into one of the hanging gibbet cages and locked away.
 * When every limb has fallen they are reeled back to the head and the Revenant reforms.
 */
public class RevenantPartEntity extends PathAwareEntity implements GeoEntity {
    public enum Part { ARM_R("revenant_arm_r", 4.65), ARM_L("revenant_arm_l", 4.65), LEG_R("revenant_leg_r", 2.1), LEG_L("revenant_leg_l", 2.1), HEAD("revenant_head", 0);
        public final String model; final double hang;
        Part(String model, double hang) { this.model = model; this.hang = hang; }
    }
    public enum State { FLYING, MOUNTED, FALLEN, REELING, CAGED }

    private static final TrackedData<Integer> PART = DataTracker.registerData(RevenantPartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> STATE = DataTracker.registerData(RevenantPartEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> RAGE = DataTracker.registerData(RevenantPartEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final Vector3f WARN = new Vector3f(0.25f, 0.9f, 0.8f);
    private static final Vector3f CHAIN = new Vector3f(0.4f, 0.42f, 0.46f);
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private UUID owner;
    private Vec3d from = Vec3d.ZERO, to = Vec3d.ZERO;
    private int moveTicks, moveTotal, attackTimer, fallenTicks, hopTimer, shriekTimer = 160, biteCooldown;
    private float integrity;
    private BlockPos anchor;
    private int facing;
    // telegraphed attacks in flight
    private LivingEntity hookTarget; private int hookTicks;
    private Vec3d shackleAt; private LivingEntity shackleTarget; private int shackleTicks;
    private final List<Vec3d> stomps = new ArrayList<>(); private int stompTicks;
    private double sweepR = -1; private Vec3d sweepC;

    public RevenantPartEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
        this.setPersistent();
        this.setNoGravity(true);
    }

    public static DefaultAttributeContainer.Builder attributes() {
        return PathAwareEntity.createMobAttributes().add(EntityAttributes.GENERIC_MAX_HEALTH, 1000).add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3).add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(PART, 0);
        this.dataTracker.startTracking(STATE, 0);
        this.dataTracker.startTracking(RAGE, false);
    }

    public Part part() { return Part.values()[Math.max(0, Math.min(4, this.dataTracker.get(PART)))]; }

    public State state() { return State.values()[this.dataTracker.get(STATE)]; }

    public boolean raging() { return this.dataTracker.get(RAGE); }

    public boolean isHead() { return part() == Part.HEAD; }

    private void setState(State s) { this.dataTracker.set(STATE, s.ordinal()); }

    /** A limb flying from the body at {@code start} to the manacle at {@code anchor} (facing = which way it looks). */
    public void launchLimb(UUID owner, Part p, Vec3d start, BlockPos anchor, int facing, float integrity, boolean rage, Vec3d arenaCentre) {
        this.owner = owner;
        this.dataTracker.set(PART, p.ordinal());
        this.dataTracker.set(RAGE, rage);
        this.anchor = anchor.toImmutable();
        this.facing = facing;
        this.integrity = integrity;
        Vec3d mount = mountPoint(anchor, facing, p, arenaCentre);
        this.mountAt = mount;
        this.setPosition(start);
        flyTo(mount, 20);
        setState(State.FLYING);
        Vec3d in = mount.subtract(Vec3d.ofCenter(anchor)).multiply(1, 0, 1);
        float yaw = (float) Math.toDegrees(Math.atan2(-in.x, in.z));
        this.setYaw(yaw); this.bodyYaw = this.headYaw = yaw;
        attackTimer = 40 + this.random.nextInt(40);
    }

    public void launchHead(UUID owner, Vec3d start, boolean rage) {
        this.owner = owner;
        this.dataTracker.set(PART, Part.HEAD.ordinal());
        this.dataTracker.set(RAGE, rage);
        this.setPosition(start);
        this.setNoGravity(false);
        setState(State.FALLEN);                   // the head is "free" (FALLEN = on its own, hopping)
        this.setVelocity(0, 0.6, 0);
        hopTimer = 20;
    }

    private Vec3d mountAt;

    /** Where a limb hangs: 2 blocks out from its anchor along the TRUE direction to the pit centre (the wall is curved,
     *  so the anchor block's own facing often points sideways into rock), and far enough below it to clear the rim. */
    static Vec3d mountPoint(BlockPos anchor, int facing, Part p, Vec3d centre) {
        Vec3d a = Vec3d.ofCenter(anchor);
        Vec3d in;
        if (centre != null && centre.squaredDistanceTo(a.x, centre.y, a.z) > 4) in = new Vec3d(centre.x - a.x, 0, centre.z - a.z).normalize();
        else {
            Direction f = Direction.fromHorizontal(facing == 0 ? 2 : facing == 1 ? 3 : facing == 2 ? 0 : 1);
            in = new Vec3d(f.getOffsetX(), 0, f.getOffsetZ());
        }
        return a.add(in.multiply(2.0)).add(0, 0.5 - p.hang, 0);
    }

    private void flyTo(Vec3d target, int ticks) {
        from = getPos();
        to = target;
        moveTicks = 0;
        moveTotal = ticks;
    }

    private ChainedRevenantEntity owner() {
        return owner != null && getWorld() instanceof ServerWorld sw && sw.getEntity(owner) instanceof ChainedRevenantEntity r ? r : null;
    }

    // ------------------------------------------------------------------ owner commands
    /** Reel this part in to {@code target} (the head's spot) over {@code ticks}. */
    public void reelTo(Vec3d target, int ticks) {
        setNoGravity(true);
        flyTo(target, ticks);
        setState(State.REELING);
    }

    public void setRage(boolean r) { this.dataTracker.set(RAGE, r); }

    public boolean isDown() { return state() == State.FALLEN && !isHead() || state() == State.REELING; }

    // ------------------------------------------------------------------ ticking
    @Override
    public void tick() {
        super.tick();
        if (this.getWorld().isClient) {
            if (this.random.nextInt(4) == 0) this.getWorld().addParticle(raging() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SOUL,
                    getParticleX(0.5), getRandomBodyY(), getParticleZ(0.5), 0, 0.02, 0);
            return;
        }
        ServerWorld sw = (ServerWorld) this.getWorld();
        ChainedRevenantEntity o = owner();
        if (o == null || !o.isAlive()) { if (this.age > 40) this.discard(); return; }
        // flights
        if (state() == State.FLYING || state() == State.REELING) {
            moveTicks++;
            double t = Math.min(1, moveTicks / (double) Math.max(1, moveTotal));
            double s = t * t * (3 - 2 * t);
            Vec3d p = from.lerp(to, s).add(0, state() == State.FLYING && !isHead() ? Math.sin(t * Math.PI) * 3 : 0, 0);
            this.setVelocity(p.subtract(getPos()));
            this.setPosition(p);
            if (moveTicks % 2 == 0) {
                Vec3d c = o.getPos().add(0, 3, 0), d = p.subtract(c);
                for (double k = 0; k <= 1; k += 0.1) { Vec3d q = c.add(d.multiply(k)); sw.spawnParticles(new DustParticleEffect(CHAIN, 1.2f), q.x, q.y, q.z, 1, 0, 0, 0, 0); }
            }
            if (t >= 1) {
                if (state() == State.FLYING && !isHead()) {
                    setState(State.MOUNTED);
                    sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.HOSTILE, 2.0f, 0.5f);
                    sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.HOSTILE, 1.0f, 0.6f);
                } else if (state() == State.FLYING && isHead()) {
                    setState(State.CAGED);
                }
            }
        }
        if (isHead()) tickHead(sw, o);
        else if (state() == State.MOUNTED) tickLimbAttacks(sw, o);
        else if (state() == State.FALLEN) {
            fallenTicks++;
            if (fallenTicks % 20 == 0) sw.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 0.3, getZ(), 3, 0.4, 0.1, 0.4, 0.01);
            if (raging() && fallenTicks > 400 && anchor != null) {                // enraged: an unjoined limb is hoisted back up
                integrity = o.limbIntegrity() * 0.5f;
                setNoGravity(true);
                flyTo(mountAt != null ? mountAt : mountPoint(anchor, facing, part(), Vec3d.ofBottomCenter(o.home())), 30);
                setState(State.FLYING);
                fallenTicks = 0;
                sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
                broadcast(sw, Text.literal("The chains hoist a fallen limb back onto the wall!").formatted(Formatting.DARK_AQUA));
            }
        }
        tickTelegraphs(sw, o);
    }

    // ------------------------------------------------------------------ limb attacks
    private LivingEntity pickTarget(ChainedRevenantEntity o, double range) {
        LivingEntity best = null; double bd = range * range;
        for (PlayerEntity p : getWorld().getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative() || !o.canHunt(p)) continue;
            double d = p.squaredDistanceTo(this);
            if (d < bd && sees(p)) { bd = d; best = p; }
        }
        return best;
    }

    private boolean sees(Entity e) {
        Vec3d a = getPos().add(0, part().hang, 0), b = e.getEyePos();
        return getWorld().raycast(new RaycastContext(a, b, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this)).getType() == HitResult.Type.MISS
                || a.squaredDistanceTo(b) < 9;
    }

    private void tickLimbAttacks(ServerWorld sw, ChainedRevenantEntity o) {
        if (o.aoeActive()) { attackTimer = Math.max(attackTimer, 30); return; }         // all together now
        if (--attackTimer > 0) return;
        LivingEntity t = pickTarget(o, 40);
        attackTimer = (raging() ? 70 : 100) + this.random.nextInt(30);
        if (t == null) { attackTimer = 20; return; }
        triggerAnim("action", "attack");
        switch (part()) {
            case ARM_R -> { hookTarget = t; hookTicks = 12; sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.HOSTILE, 2.0f, 0.5f); }
            case ARM_L -> { shackleTarget = t; shackleAt = t.getPos(); shackleTicks = 16; sw.playSound(null, getBlockPos(), SoundEvents.ITEM_TRIDENT_THROW, SoundCategory.HOSTILE, 1.5f, 0.5f); }
            default -> {
                if (raging() && this.random.nextInt(3) == 0) { sweepR = 1; sweepC = floorBelow(sw, t.getPos()); }
                else {
                    stomps.clear();
                    stomps.add(t.getPos());
                    for (int i = 0; i < 2; i++) stomps.add(t.getPos().add(this.random.nextDouble() * 6 - 3, 0, this.random.nextDouble() * 6 - 3));
                    stompTicks = 22;
                    sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_RAVAGER_STEP, SoundCategory.HOSTILE, 3.0f, 0.4f);
                }
            }
        }
    }

    private Vec3d floorBelow(ServerWorld sw, Vec3d p) {
        BlockPos.Mutable m = BlockPos.ofFloored(p).mutableCopy();
        for (int i = 0; i < 20 && sw.getBlockState(m.down()).getCollisionShape(sw, m.down()).isEmpty(); i++) m.move(0, -1, 0);
        return new Vec3d(p.x, m.getY(), p.z);
    }

    private void tickTelegraphs(ServerWorld sw, ChainedRevenantEntity o) {
        Vec3d hand = getPos().add(0, 0.3, 0);
        // RIGHT ARM - the chain hook: a chain snakes toward you, then drags you to the wall
        if (hookTicks > 0 && hookTarget != null) {
            hookTicks--;
            Vec3d d = hookTarget.getEyePos().subtract(hand);
            double reach = 1 - hookTicks / 12.0;
            for (double k = 0; k <= reach; k += 0.05) { Vec3d q = hand.add(d.multiply(k)); sw.spawnParticles(new DustParticleEffect(CHAIN, 1.4f), q.x, q.y, q.z, 1, 0, 0, 0, 0); }
            if (hookTicks == 0) {
                if (hookTarget.isAlive() && sees(hookTarget) && hookTarget.squaredDistanceTo(this) < 45 * 45) {
                    Vec3d pull = hand.subtract(hookTarget.getPos()).normalize();
                    hookTarget.damage(this.getDamageSources().mobAttack(this), 5f);
                    hookTarget.setVelocity(pull.x * 1.7, 0.5 + pull.y * 0.6, pull.z * 1.7);
                    hookTarget.velocityModified = true;
                    sw.playSound(null, hookTarget.getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 2.0f, 0.6f);
                }
                hookTarget = null;
            }
        }
        // LEFT ARM - shackles: an arc of chain to where you stood; still there when it lands = rooted
        if (shackleTicks > 0 && shackleAt != null) {
            shackleTicks--;
            double t = 1 - shackleTicks / 16.0;
            Vec3d q = hand.lerp(shackleAt, t).add(0, Math.sin(t * Math.PI) * 4, 0);
            sw.spawnParticles(new DustParticleEffect(CHAIN, 1.8f), q.x, q.y, q.z, 3, 0.1, 0.1, 0.1, 0);
            for (int i = 0; i < 10; i++) {
                double a = Math.PI * 2 * i / 10;
                sw.spawnParticles(new DustParticleEffect(WARN, 1.1f), shackleAt.x + Math.cos(a) * 1.8, shackleAt.y + 0.1, shackleAt.z + Math.sin(a) * 1.8, 1, 0, 0, 0, 0);
            }
            if (shackleTicks == 0) {
                sw.playSound(null, BlockPos.ofFloored(shackleAt), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.HOSTILE, 2.0f, 0.4f);
                if (shackleTarget != null && shackleTarget.isAlive() && shackleTarget.getPos().squaredDistanceTo(shackleAt) < 1.8 * 1.8) {
                    shackleTarget.damage(this.getDamageSources().mobAttack(this), 5f);
                    shackleTarget.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 5), this);
                    shackleTarget.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 250), this);    // amplifier 250 = no jumping
                    if (shackleTarget instanceof ServerPlayerEntity sp) sp.sendMessage(Text.literal("SHACKLED!").formatted(Formatting.DARK_AQUA, Formatting.BOLD), true);
                }
                shackleTarget = null; shackleAt = null;
            }
        }
        // LEGS - stomps: rubble rains on the marked circles
        if (stompTicks > 0) {
            stompTicks--;
            for (Vec3d s : stomps) {
                if (stompTicks % 3 == 0) for (int i = 0; i < 12; i++) {
                    double a = Math.PI * 2 * i / 12;
                    sw.spawnParticles(new DustParticleEffect(WARN, 1.2f), s.x + Math.cos(a) * 2.2, s.y + 0.1, s.z + Math.sin(a) * 2.2, 1, 0, 0, 0, 0);
                }
                if (stompTicks < 8) sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, net.minecraft.block.Blocks.COBBLED_DEEPSLATE.getDefaultState()),
                        s.x, s.y + 8, s.z, 6, 1.0, 1.5, 1.0, 0);
            }
            if (stompTicks == 0) {
                for (Vec3d s : stomps) {
                    sw.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, net.minecraft.block.Blocks.COBBLED_DEEPSLATE.getDefaultState()),
                            s.x, s.y + 0.5, s.z, 40, 1.0, 0.4, 1.0, 0.2);
                    sw.playSound(null, BlockPos.ofFloored(s), SoundEvents.BLOCK_DEEPSLATE_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
                    for (PlayerEntity p : getWorld().getPlayers())
                        if (p.isAlive() && !p.isCreative() && p.getPos().squaredDistanceTo(s) < 2.3 * 2.3 && o.canHunt(p))
                            p.damage(this.getDamageSources().fallingBlock(this), 9f);
                }
                stomps.clear();
            }
        }
        // LEGS (enraged) - a chain sweep rolling across the floor: jump it
        if (sweepR >= 0 && sweepC != null) {
            sweepR += 0.8;
            if (sweepR > 30) { sweepR = -1; return; }
            int n = (int) (sweepR * 5);
            for (int i = 0; i < n; i++) {
                double a = Math.PI * 2 * i / n;
                sw.spawnParticles(new DustParticleEffect(CHAIN, 1.3f), sweepC.x + Math.cos(a) * sweepR, sweepC.y + 0.3, sweepC.z + Math.sin(a) * sweepR, 1, 0, 0, 0, 0);
            }
            for (PlayerEntity p : getWorld().getPlayers()) {
                if (!p.isAlive() || p.isCreative() || !o.canHunt(p)) continue;
                double d = Math.sqrt(p.squaredDistanceTo(sweepC.x, p.getY(), sweepC.z));
                if (Math.abs(d - sweepR) < 0.8 && p.getY() - sweepC.y < 0.7) p.damage(this.getDamageSources().mobAttack(this), 8f);
            }
        }
    }

    // ------------------------------------------------------------------ the head
    private void tickHead(ServerWorld sw, ChainedRevenantEntity o) {
        if (state() == State.CAGED) {
            this.setVelocity(Vec3d.ZERO);
            if (this.age % 10 == 0) sw.spawnParticles(ParticleTypes.SOUL, getX(), getY() + 0.5, getZ(), 2, 0.3, 0.3, 0.3, 0.01);
            return;
        }
        if (state() != State.FALLEN) return;
        if (biteCooldown > 0) biteCooldown--;
        LivingEntity t = pickTarget(o, 48);
        if (t == null) return;
        this.getLookControl().lookAt(t, 30, 30);
        float yaw = (float) Math.toDegrees(Math.atan2(-(t.getX() - getX()), t.getZ() - getZ()));
        this.setYaw(yaw); this.bodyYaw = this.headYaw = yaw;
        if (this.isOnGround() && --hopTimer <= 0) {
            Vec3d d = t.getPos().subtract(getPos()).multiply(1, 0, 1);
            d = d.lengthSquared() < 1e-4 ? Vec3d.ZERO : d.normalize();
            double sp = raging() ? 0.75 : 0.55;
            this.setVelocity(d.x * sp, 0.55, d.z * sp);
            this.velocityDirty = true;
            hopTimer = raging() ? 10 : 16;
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_SKELETON_STEP, SoundCategory.HOSTILE, 1.5f, 0.6f);
        }
        if (biteCooldown == 0 && this.squaredDistanceTo(t) < 2.2 * 2.2) {
            biteCooldown = 20;
            triggerAnim("action", "bite");
            if (t.damage(this.getDamageSources().mobAttack(this), 6f)) t.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 60, 1), this);
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.HOSTILE, 1.5f, 0.8f);
        }
        if (--shriekTimer <= 0) {
            shriekTimer = 160;
            sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WITHER_SKELETON_AMBIENT, SoundCategory.HOSTILE, 2.0f, 0.5f);
            sw.spawnParticles(ParticleTypes.SONIC_BOOM, getX(), getY() + 0.5, getZ(), 1, 0, 0, 0, 0);
            for (PlayerEntity p : getWorld().getPlayers())
                if (p.isAlive() && !p.isCreative() && p.squaredDistanceTo(this) < 25 && o.canHunt(p)) {
                    Vec3d away = p.getPos().subtract(getPos()).multiply(1, 0, 1).normalize();
                    p.setVelocity(away.x * 1.2, 0.5, away.z * 1.2);
                    p.velocityModified = true;
                    p.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 100, 0), this);
                }
        }
    }

    // ------------------------------------------------------------------ damage
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return false;
        ChainedRevenantEntity o = owner();
        if (o == null) return super.damage(source, amount);
        if (source.getAttacker() instanceof PlayerEntity p && !o.canHunt(p)) return false;
        if (isHead()) {
            if (state() == State.FALLEN && source.getAttacker() instanceof PlayerEntity p && p.getMainHandStack().isOf(ModItems.TIDESHACKLE)) {
                BlockPos cage = o.freeCage();
                ItemStack st = p.getMainHandStack();
                st.damage(1, p, pl -> pl.sendToolBreakStatus(Hand.MAIN_HAND));
                sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 2.5f, 0.6f);
                if (cage != null) {
                    setNoGravity(true);
                    flyTo(Vec3d.ofBottomCenter(cage).add(0, 0.1, 0), 20);
                    setState(State.FLYING);
                    broadcast(sw, Text.literal("The Tideshackle bites - the head is slung into a gibbet cage and locked away!").formatted(Formatting.AQUA, Formatting.BOLD));
                } else {
                    setVelocity(0, 0.2, 0);
                    hopTimer = 100;
                    broadcast(sw, Text.literal("The Tideshackle stuns the head!").formatted(Formatting.AQUA));
                }
                return false;
            }
            if (source.getAttacker() instanceof ServerPlayerEntity sp && this.age % 5 == 0)
                sp.sendMessage(Text.literal("The head cannot be harmed - break the limbs, or cage it with a Tideshackle").formatted(Formatting.GRAY), true);
            sw.spawnParticles(ParticleTypes.ENCHANTED_HIT, getX(), getY() + 0.5, getZ(), 6, 0.3, 0.3, 0.3, 0.1);
            return false;
        }
        if (state() != State.MOUNTED) return false;                                  // falling / fallen limbs are spent
        float dealt = o.takeLimbDamage(source, amount, integrity);
        integrity -= dealt;
        this.hurtTime = this.maxHurtTime = 10;
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_SKELETON_HURT, SoundCategory.HOSTILE, 1.5f, 0.5f);
        sw.spawnParticles(ParticleTypes.SOUL, getX(), getY() + part().hang * 0.5, getZ(), 6, 0.3, 0.5, 0.3, 0.02);
        if (integrity <= 0) {
            setState(State.FALLEN);
            setNoGravity(false);
            fallenTicks = 0;
            hookTarget = null; shackleTicks = 0; stompTicks = 0; sweepR = -1;
            sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.HOSTILE, 3.0f, 0.4f);
            sw.spawnParticles(ParticleTypes.SOUL_FIRE_FLAME, getX(), getY() + part().hang * 0.5, getZ(), 30, 0.4, 0.8, 0.4, 0.05);
            o.onLimbFallen();
        }
        return true;
    }

    private void broadcast(ServerWorld sw, Text t) {
        for (ServerPlayerEntity p : sw.getPlayers(p -> p.squaredDistanceTo(this) < 64 * 64)) p.sendMessage(t, false);
    }

    // ------------------------------------------------------------------ misc
    @Override
    public void travel(Vec3d input) {
        if (state() == State.FALLEN && (isHead() || !hasNoGravity())) {
            // free head / fallen limb: plain physics with a little drag
            super.travel(Vec3d.ZERO);
        }
    }

    @Override
    public boolean isPushable() { return false; }

    @Override
    protected void pushAway(Entity entity) { }

    @Override
    public boolean handleFallDamage(float dist, float mult, DamageSource src) { return false; }

    @Override
    public boolean canImmediatelyDespawn(double d) { return false; }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) { return SoundEvents.ENTITY_WITHER_SKELETON_HURT; }

    @Override
    public float getSoundPitch() { return 0.6f; }

    @Override
    protected SoundEvent getDeathSound() { return SoundEvents.ENTITY_SKELETON_DEATH; }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("Part", this.dataTracker.get(PART));
        nbt.putInt("State", this.dataTracker.get(STATE));
        nbt.putBoolean("Rage", raging());
        if (owner != null) nbt.putUuid("Owner", owner);
        nbt.putFloat("Integrity", integrity);
        if (anchor != null) nbt.putLong("Anchor", anchor.asLong());
        nbt.putInt("Facing", facing);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        this.dataTracker.set(PART, nbt.getInt("Part"));
        State s = State.values()[nbt.getInt("State")];
        this.dataTracker.set(STATE, (s == State.FLYING || s == State.REELING ? (isHead() ? State.FALLEN : State.MOUNTED) : s).ordinal());
        this.dataTracker.set(RAGE, nbt.getBoolean("Rage"));
        if (nbt.containsUuid("Owner")) owner = nbt.getUuid("Owner");
        integrity = nbt.getFloat("Integrity");
        if (nbt.contains("Anchor")) anchor = BlockPos.fromLong(nbt.getLong("Anchor"));
        facing = nbt.getInt("Facing");
        setNoGravity(state() != State.FALLEN);
    }

    // ------------------------------------------------------------------ GeckoLib
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle"), FALLEN = RawAnimation.begin().thenLoop("fallen"),
            FLY = RawAnimation.begin().thenLoop("fly"), REEL = RawAnimation.begin().thenLoop("reel"), HOP = RawAnimation.begin().thenLoop("hop"),
            CAGED = RawAnimation.begin().thenLoop("caged");

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "state", 4, st -> {
            if (isHead()) return st.setAndContinue(switch (state()) { case CAGED -> CAGED; case FLYING -> FLY; default -> isOnGround() ? IDLE : HOP; });
            return st.setAndContinue(switch (state()) { case FLYING -> FLY; case FALLEN -> FALLEN; case REELING -> REEL; default -> IDLE; });
        }));
        AnimationController<RevenantPartEntity> action = new AnimationController<>(this, "action", 1, st -> PlayState.STOP);
        action.triggerableAnim("attack", RawAnimation.begin().thenPlay("attack"));
        action.triggerableAnim("bite", RawAnimation.begin().thenPlay("bite"));
        controllers.add(action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
