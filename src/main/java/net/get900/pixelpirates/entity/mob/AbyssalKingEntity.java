package net.get900.pixelpirates.entity.mob;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.block.custom.TideSluiceBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
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
 * BOSS 5/10 - THE ABYSSAL KING, drowned sovereign of the Sunken Court (world/dungeon/SunkenCourt).
 *
 * THE FLOODGATES: he fights in a sealed, flooded throne hall and is TIDE-BLESSED while it is full - he takes
 * only 35% damage. Four Tide Sluices (TideSluiceBlock) are set in the walls; each one a player turns open
 * drains the hall by a quarter (the water really drops, layer by layer) and weakens the blessing
 * (35 / 50 / 65 / 80%). With all four open the hall runs dry and he is STRANDED: 20 s (14 s enraged) on his
 * knees, slowed, salt-crusted ("stranded" skin), taking 150% damage. Then TIDE CALL: he roars, every sluice
 * slams shut and locks for 20 s, and the hall floods back.
 * He fights the drain: RESEAL levels his trident at the oldest open sluice for 2.5 s - deal 24 damage (or
 * land a Depth Charge) during the channel to break it and stagger him; Tide Wardens walk to open sluices
 * and wind them shut unless killed.
 *
 * Phase 1: trident jab, Riptide Charge (a bubbling lane telegraphs, then he rockets 14 blocks along it
 * spinning), Trident Volley, Crushing Depths (a shrinking ring of bubbles marks every player, then the sea
 * implodes there), Abyssal Decree (mining fatigue), Royal Guard (2 Tide Wardens, max 3), Reseal.
 * Stranded: only a desperate Gasping Sweep.
 * Phase 2 "Wrath of the Deep" at 50% (navy + violet skin): Maelstrom (spiral pull) and Undertow (a pressure
 * ring rolls out along his height - swim over or under it).
 */
public class AbyssalKingEntity extends ModBoss {
    private static final TrackedData<Boolean> STRANDED = DataTracker.registerData(AbyssalKingEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final UUID STRAND_SLOW = UUID.fromString("b3c7a0e2-6f1d-4d8e-9a35-0e7a4c1b2d90");
    private static final Vector3f TIDE = new Vector3f(0.25f, 0.95f, 0.9f);
    private static final float[] BLESSING = {0.35f, 0.5f, 0.65f, 0.8f};
    public static final int STRAND_TICKS = 400, STRAND_TICKS_ENRAGED = 280, LOCK_TICKS = 400;

    /** The flooded hall, in world coordinates: a cylinder (floor..cy) under a dome of height ry. */
    public record Court(int cx, int cz, int r, int floor, int cy, int ry) {
        public boolean inside(int x, int y, int z) {
            if (y < floor) return false;
            int dx = x - cx, dz = z - cz;
            double d2 = dx * dx + dz * dz;
            if (y <= cy) return d2 < r * r;
            double t = (y - cy) / (double) ry;
            return t < 1 && d2 < r * r * (1 - t * t);
        }

        public int top() { return cy + ry - 1; }

        int[] toArray() { return new int[]{cx, cz, r, floor, cy, ry}; }

        static Court of(int[] a) { return a.length == 6 ? new Court(a[0], a[1], a[2], a[3], a[4], a[5]) : null; }
    }

    private static final class Timed {
        final Vec3d pos; int ticks;
        Timed(Vec3d pos, int ticks) { this.pos = pos; this.ticks = ticks; }
    }

    private Court court;
    private final List<BlockPos> sluices = new ArrayList<>();
    private final Map<BlockPos, Long> openedAt = new HashMap<>();
    private final List<BlockPos> openNow = new ArrayList<>();
    private int level = Integer.MIN_VALUE;
    private boolean stranded;
    private int strandedTicks, lockTicks, stun, idleTicks, hintCooldown;
    private final Set<UUID> greeted = new HashSet<>();
    // reseal channel
    private BlockPos resealTarget; private int resealTicks; private float resealDamage;
    // riptide charge
    private Vec3d riptideDir; private int riptideTicks; private final Set<UUID> riptideHit = new HashSet<>();
    // crushing depths, undertow, maelstrom
    private final List<Timed> marks = new ArrayList<>();
    private double undertowR = -1; private Vec3d undertowC; private final Set<UUID> undertowHit = new HashSet<>();
    private int maelstromTicks;

    public AbyssalKingEntity(EntityType<? extends PathAwareEntity> type, World world) {
        super(type, world);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(STRANDED, false);
    }

    public boolean isStranded() { return this.dataTracker.get(STRANDED); }

    @Override
    public String skinVariant() { return isStranded() ? "stranded" : super.skinVariant(); }

    @Override
    protected List<String> extraAnims() { return List.of("stranded", "tide_call"); }

    // ------------------------------------------------------------------ court setup (worldgen: before the spawn)
    public void setCourt(Court c, List<BlockPos> sluicePositions) {
        this.court = c;
        this.sluices.clear();
        for (BlockPos p : sluicePositions) this.sluices.add(p.toImmutable());
        this.level = c.top();
        this.addCommandTag("sunken_court");
    }

    public boolean ownsSluice(BlockPos p) { return sluices.contains(p); }

    public boolean hasCourt() { return court != null; }

    public List<BlockPos> openSluices() { return List.copyOf(openNow); }

    private float blessing() {
        if (court == null) return 1f;
        if (stranded) return 1.5f;
        return BLESSING[Math.min(3, openNow.size())];
    }

    // ------------------------------------------------------------------ sluices
    /** Can this player turn a sluice right now? Sends the reason when not. */
    public boolean acceptsSluice(PlayerEntity p) {
        if (!fights(p)) {
            p.sendMessage(BossProgression.sealedMessage(p, chainIndex()), true);
            return false;
        }
        if (lockTicks > 0) {
            p.sendMessage(Text.literal("The King's tide holds the floodgates locked (" + (lockTicks / 20 + 1) + " s)").formatted(Formatting.DARK_AQUA), true);
            return false;
        }
        return true;
    }

    public void onSluiceOpened(BlockPos pos, PlayerEntity by) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        openedAt.put(pos.toImmutable(), sw.getTime());
        recountSluices(sw);
        sw.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_OPEN, SoundCategory.BLOCKS, 1.6f, 0.5f);
        sw.playSound(null, pos, SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.BLOCKS, 2.5f, 0.6f);
        int n = openNow.size();
        broadcast(sw, Text.literal(by.getName().getString() + " turns a Tide Sluice - " + n + "/4 open"
                + (n >= 4 ? ". THE COURT DRAINS!" : ", the water falls.")).formatted(Formatting.AQUA), false);
    }

    public void closeSluice(BlockPos pos, Text why) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        BlockState st = sw.getBlockState(pos);
        if (!st.isOf(ModBlocks.TIDE_SLUICE) || !st.get(TideSluiceBlock.OPEN)) return;
        sw.setBlockState(pos, st.with(TideSluiceBlock.OPEN, false));
        openedAt.remove(pos);
        sw.playSound(null, pos, SoundEvents.BLOCK_IRON_TRAPDOOR_CLOSE, SoundCategory.BLOCKS, 1.6f, 0.5f);
        sw.spawnParticles(ParticleTypes.BUBBLE_POP, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 20, 0.6, 0.6, 0.6, 0.05);
        recountSluices(sw);
        if (why != null) broadcast(sw, why, false);
    }

    private void recountSluices(ServerWorld sw) {
        openNow.clear();
        for (BlockPos p : sluices) {
            if (!sw.isChunkLoaded(p)) continue;
            BlockState st = sw.getBlockState(p);
            if (st.isOf(ModBlocks.TIDE_SLUICE) && st.get(TideSluiceBlock.OPEN)) {
                openNow.add(p);
                openedAt.putIfAbsent(p, sw.getTime());
            }
        }
        openedAt.keySet().retainAll(openNow);
    }

    private boolean resealReady() {
        if (court == null || stranded || openNow.isEmpty()) return false;
        long now = this.getWorld().getTime();
        for (BlockPos p : openNow) if (now - openedAt.getOrDefault(p, now) >= 60) return true;
        return false;
    }

    // ------------------------------------------------------------------ the water itself
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE;

    private int targetLevel() {
        if (stranded || openNow.size() >= 4) return court.floor() - 1;
        int h = court.top() - court.floor() + 1;
        return court.top() - openNow.size() * h / 4;
    }

    private void tickWater(ServerWorld sw) {
        if (court == null) return;
        if (!sw.isChunkLoaded(new BlockPos(court.cx() + court.r(), court.floor(), court.cz() + court.r()))
                || !sw.isChunkLoaded(new BlockPos(court.cx() - court.r(), court.floor(), court.cz() - court.r()))) return;
        if (level == Integer.MIN_VALUE) level = court.top();
        int target = targetLevel();
        if (level > target && this.age % 2 == 0) {
            layer(sw, level, false);
            level--;
            if (this.age % 10 == 0) sw.playSound(null, court.cx(), level, court.cz(), SoundEvents.BLOCK_WATER_AMBIENT, SoundCategory.BLOCKS, 3.0f, 0.5f);
        } else if (level < target) {
            layer(sw, level + 1, true);
            level++;
            if (this.age % 8 == 0) sw.playSound(null, court.cx(), level, court.cz(), SoundEvents.AMBIENT_UNDERWATER_ENTER, SoundCategory.BLOCKS, 3.0f, 0.6f);
        }
    }

    /** Drain (fill=false) or flood one horizontal slice of the hall. Only water/air and waterlogging change. */
    private void layer(ServerWorld sw, int y, boolean fill) {
        BlockPos.Mutable p = new BlockPos.Mutable();
        int r = court.r();
        for (int x = court.cx() - r; x <= court.cx() + r; x++)
            for (int z = court.cz() - r; z <= court.cz() + r; z++) {
                if (!court.inside(x, y, z)) continue;
                p.set(x, y, z);
                BlockState s = sw.getBlockState(p);
                if (fill) {
                    if (s.isAir()) sw.setBlockState(p, Blocks.WATER.getDefaultState(), FLAGS);
                    else if (s.contains(Properties.WATERLOGGED) && !s.get(Properties.WATERLOGGED)) sw.setBlockState(p, s.with(Properties.WATERLOGGED, true), FLAGS);
                } else {
                    if (s.contains(Properties.WATERLOGGED) && s.get(Properties.WATERLOGGED)) sw.setBlockState(p, s.with(Properties.WATERLOGGED, false), FLAGS);
                    else if (s.isOf(Blocks.WATER) || s.isOf(Blocks.BUBBLE_COLUMN) || s.isOf(Blocks.SEAGRASS) || s.isOf(Blocks.TALL_SEAGRASS)
                            || s.isOf(Blocks.KELP) || s.isOf(Blocks.KELP_PLANT)) sw.setBlockState(p, Blocks.AIR.getDefaultState(), FLAGS);
                }
            }
        if (!fill && this.random.nextInt(2) == 0)
            sw.spawnParticles(ParticleTypes.SPLASH, court.cx(), y + 0.1, court.cz(), 40, r * 0.5, 0.05, r * 0.5, 0.1);
    }

    // ------------------------------------------------------------------ stranded / tide call
    private void strand(ServerWorld sw) {
        stranded = true;
        this.dataTracker.set(STRANDED, true);
        strandedTicks = isEnraged() ? STRAND_TICKS_ENRAGED : STRAND_TICKS;
        stun = 60;
        resealTicks = 0; resealTarget = null; riptideTicks = 0; maelstromTicks = 0; undertowR = -1; marks.clear();
        this.getNavigation().stop();
        triggerAnim(ACTION, "stranded");
        var speed = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null && speed.getModifier(STRAND_SLOW) == null)
            speed.addTemporaryModifier(new EntityAttributeModifier(STRAND_SLOW, "stranded", -0.5, EntityAttributeModifier.Operation.MULTIPLY_TOTAL));
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_FLOP, SoundCategory.HOSTILE, 3.0f, 0.5f);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.5f, 0.6f);
        broadcast(sw, Text.literal("The court runs dry! The Abyssal King is STRANDED - strike him down!").formatted(Formatting.GOLD, Formatting.BOLD), false);
    }

    private void tideCall(ServerWorld sw) {
        stranded = false;
        this.dataTracker.set(STRANDED, false);
        var speed = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(STRAND_SLOW);
        lockTicks = LOCK_TICKS;
        stun = 40;
        triggerAnim(ACTION, "tide_call");
        for (BlockPos p : List.copyOf(sluices)) closeSluice(p, null);
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT, SoundCategory.HOSTILE, 3.0f, 0.5f);
        sw.playSound(null, getBlockPos(), SoundEvents.ITEM_TRIDENT_THUNDER, SoundCategory.HOSTILE, 2.0f, 0.7f);
        sw.spawnParticles(ParticleTypes.SPLASH, getX(), getY() + 1, getZ(), 200, 4, 1, 4, 0.4);
        Abilities.ring(sw, getPos(), 8, ParticleTypes.NAUTILUS, 48);
        for (LivingEntity e : Abilities.victims(this, 8)) {
            if (e.damage(this.getDamageSources().mobAttack(this), 8f)) {
                Vec3d away = e.getPos().subtract(getPos()).multiply(1, 0, 1);
                away = away.lengthSquared() < 1e-4 ? new Vec3d(0, 0, 1) : away.normalize();
                e.addVelocity(away.x * 1.3, 0.5, away.z * 1.3);
                e.velocityModified = true;
            }
        }
        broadcast(sw, Text.literal("\"THE TIDE ANSWERS ITS KING!\" - the sluices slam shut and the court floods.").formatted(Formatting.DARK_AQUA), false);
    }

    // ------------------------------------------------------------------ abilities availability
    @Override
    protected List<Abilities.Ability> activeAbilities() {
        if (stun > 0 || riptideTicks > 0 || resealTicks > 0 || maelstromTicks > 0) return List.of();
        List<Abilities.Ability> out = new ArrayList<>();
        for (Abilities.Ability a : super.activeAbilities()) {
            switch (a.name()) {
                case "gasping_sweep" -> { if (stranded) out.add(a); }
                case "reseal" -> { if (resealReady()) out.add(a); }
                case "maelstrom", "undertow", "crushing_depths" -> { if (!stranded && this.isTouchingWater()) out.add(a); }
                default -> { if (!stranded) out.add(a); }
            }
        }
        return out;
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
        if (lockTicks > 0) lockTicks--;
        if (court != null) {
            if (this.age % 10 == 0) recountSluices(sw);
            tickWater(sw);
            if (!stranded && openNow.size() >= 4) strand(sw);
            if (stranded) {
                if (this.age % 4 == 0) sw.spawnParticles(ParticleTypes.DRIPPING_WATER, getX(), getY() + 3.5, getZ(), 4, 0.6, 0.8, 0.6, 0);
                if (strandedTicks % 20 == 0)
                    for (ServerPlayerEntity p : nearbyPlayers(sw, 40))
                        p.sendMessage(Text.literal("The King is stranded - " + strandedTicks / 20 + " s until the tide returns").formatted(Formatting.GOLD), true);
                if (--strandedTicks <= 0) tideCall(sw);
            }
            // abandoned fight (nobody who can fight him within 48 blocks for 15 s): close the floodgates, flood the hall
            if (this.age % 20 == 0) {
                boolean anyone = false;
                for (ServerPlayerEntity p : nearbyPlayers(sw, 48)) if (!p.isCreative() && mayTarget(p)) { anyone = true; break; }
                idleTicks = anyone ? Math.min(idleTicks, 0) : idleTicks + 20;
            }
            if (idleTicks > 300 && (stranded || !openNow.isEmpty())) {
                if (stranded) { stranded = false; this.dataTracker.set(STRANDED, false);
                    var sp = this.getAttributeInstance(EntityAttributes.GENERIC_MOVEMENT_SPEED); if (sp != null) sp.removeModifier(STRAND_SLOW); }
                for (BlockPos p : List.copyOf(sluices)) closeSluice(p, null);
            }
        }
        if (this.age % 20 == 0)
            for (ServerPlayerEntity p : nearbyPlayers(sw, 30))
                if (greeted.add(p.getUuid()) && court != null)
                    p.sendMessage(Text.literal("The King is TIDE-BLESSED while his court is flooded. Turn the four Tide Sluices in the walls to drain it"
                            + " - he will try to reseal them. Hit him hard while he channels!").formatted(Formatting.AQUA), false);
        tickReseal(sw);
        tickRiptide(sw);
        tickMarks(sw);
        tickUndertow(sw);
        tickMaelstrom(sw);
    }

    private List<ServerPlayerEntity> nearbyPlayers(ServerWorld sw, double r) {
        return sw.getPlayers(p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(this) < r * r);
    }

    private void broadcast(ServerWorld sw, Text t, boolean actionbar) {
        for (ServerPlayerEntity p : nearbyPlayers(sw, 48)) p.sendMessage(t, actionbar);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.getAttacker() instanceof PlayerEntity p && fights(p)) {
            if (resealTicks > 0) {
                resealDamage += amount;
                if (resealDamage >= 24f) interruptReseal(Text.literal("The King's hold on the tide breaks - he staggers!").formatted(Formatting.GOLD));
            }
            if (court != null && !stranded && openNow.size() < 2 && hintCooldown == 0 && p instanceof ServerPlayerEntity sp) {
                hintCooldown = 200;
                sp.sendMessage(Text.literal("The flooded court shields its King - open the Tide Sluices!").formatted(Formatting.DARK_AQUA), true);
            }
        }
        return super.damage(source, amount * blessing());
    }

    /** A Depth Charge went off next to him (DepthChargeEntity). */
    public void onDepthCharge() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        if (resealTicks > 0) {
            interruptReseal(Text.literal("The depth charge shatters the King's focus!").formatted(Formatting.GOLD));
        } else if (!stranded && stun == 0) {
            stun = 30;
            this.getNavigation().stop();
            sw.spawnParticles(ParticleTypes.CRIT, getX(), getY() + 3, getZ(), 20, 0.6, 0.6, 0.6, 0.2);
        }
    }

    // ------------------------------------------------------------------ RESEAL (interruptible channel)
    void startReseal() {
        if (!(this.getWorld() instanceof ServerWorld sw) || openNow.isEmpty()) return;
        long now = sw.getTime();
        BlockPos pick = null; long oldest = Long.MAX_VALUE;
        for (BlockPos p : openNow) { long t = openedAt.getOrDefault(p, now); if (t < oldest) { oldest = t; pick = p; } }
        resealTarget = pick;
        resealTicks = 50;
        resealDamage = 0;
        this.getNavigation().stop();
        sw.playSound(null, getBlockPos(), SoundEvents.BLOCK_CONDUIT_ACTIVATE, SoundCategory.HOSTILE, 2.5f, 0.6f);
        broadcast(sw, Text.literal("The King levels his trident at a sluice - break his focus!").formatted(Formatting.YELLOW), true);
    }

    private void tickReseal(ServerWorld sw) {
        if (resealTicks <= 0 || resealTarget == null) return;
        resealTicks--;
        Vec3d tip = getPos().add(0, 4.2, 0);
        Vec3d to = Vec3d.ofCenter(resealTarget);
        this.getLookControl().lookAt(to.x, to.y, to.z, 30f, 30f);
        this.bodyYaw = this.headYaw = (float) Math.toDegrees(Math.atan2(-(to.x - getX()), to.z - getZ()));
        if (resealTicks % 2 == 0) {
            Vec3d d = to.subtract(tip);
            for (double s = 0; s <= 1; s += 0.06) {
                Vec3d q = tip.add(d.multiply(s));
                sw.spawnParticles(new DustParticleEffect(TIDE, 1.2f), q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0);
            }
            sw.spawnParticles(ParticleTypes.BUBBLE, to.x, to.y, to.z, 6, 0.4, 0.4, 0.4, 0.05);
        }
        if (resealTicks == 0) {
            closeSluice(resealTarget, Text.literal("The King reseals a Tide Sluice! The water rises.").formatted(Formatting.DARK_AQUA));
            resealTarget = null;
        }
    }

    private void interruptReseal(Text msg) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        resealTicks = 0;
        resealTarget = null;
        stun = 60;
        triggerAnim(ACTION, "stranded");
        sw.playSound(null, getBlockPos(), SoundEvents.ITEM_SHIELD_BREAK, SoundCategory.HOSTILE, 2.0f, 0.5f);
        sw.spawnParticles(ParticleTypes.CRIT, getX(), getY() + 3, getZ(), 30, 0.8, 0.8, 0.8, 0.3);
        broadcast(sw, msg, false);
    }

    // ------------------------------------------------------------------ RIPTIDE CHARGE
    void startRiptide(LivingEntity t) {
        if (t == null) return;
        Vec3d d = t.getPos().subtract(getPos()).multiply(1, 0, 1);
        riptideDir = d.lengthSquared() < 1e-4 ? Vec3d.fromPolar(0, getYaw()) : d.normalize();
        riptideTicks = 22;
        riptideHit.clear();
        this.setYaw((float) Math.toDegrees(Math.atan2(-riptideDir.x, riptideDir.z)));
        this.bodyYaw = this.headYaw = this.getYaw();
        this.getNavigation().stop();
    }

    private void tickRiptide(ServerWorld sw) {
        if (riptideTicks <= 0 || riptideDir == null) return;
        riptideTicks--;
        this.setYaw((float) Math.toDegrees(Math.atan2(-riptideDir.x, riptideDir.z)));
        this.bodyYaw = this.headYaw = this.getYaw();
        if (riptideTicks > 8) {                                                  // telegraph: the lane boils
            if (riptideTicks % 2 == 0)
                for (double s = 1.5; s <= 15; s += 1.0) {
                    Vec3d q = getPos().add(riptideDir.multiply(s));
                    sw.spawnParticles(ParticleTypes.BUBBLE_COLUMN_UP, q.x, q.y + 0.3, q.z, 2, 0.4, 0.1, 0.4, 0.05);
                    sw.spawnParticles(new DustParticleEffect(TIDE, 1.4f), q.x, q.y + 0.1, q.z, 1, 0.3, 0, 0.3, 0);
                }
            if (riptideTicks == 9) sw.playSound(null, getBlockPos(), SoundEvents.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.HOSTILE, 2.5f, 0.7f);
            return;
        }
        safeMove(riptideDir.multiply(1.7));
        this.setVelocity(Vec3d.ZERO);
        sw.spawnParticles(ParticleTypes.BUBBLE, getX(), getY() + 2, getZ(), 20, 0.8, 1.2, 0.8, 0.1);
        sw.spawnParticles(ParticleTypes.NAUTILUS, getX(), getY() + 2, getZ(), 6, 0.6, 1.0, 0.6, 0.3);
        for (LivingEntity e : Abilities.victims(this, 2.4)) {
            if (!riptideHit.add(e.getUuid())) continue;
            if (e.damage(this.getDamageSources().mobAttack(this), 14f)) {
                double kb = 1.0 - e.getAttributeValue(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE);
                e.addVelocity(riptideDir.x * 1.4 * kb, 0.5 * kb, riptideDir.z * 1.4 * kb);
                e.velocityModified = true;
            }
        }
        if (riptideTicks == 0) riptideDir = null;
    }

    // ------------------------------------------------------------------ CRUSHING DEPTHS
    void startPressure() {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        for (ServerPlayerEntity p : nearbyPlayers(sw, 24))
            if (!p.isCreative() && mayTarget(p)) marks.add(new Timed(p.getPos(), 30));
        sw.playSound(null, getBlockPos(), SoundEvents.ENTITY_WARDEN_SONIC_CHARGE, SoundCategory.HOSTILE, 1.5f, 0.5f);
    }

    private void tickMarks(ServerWorld sw) {
        for (Iterator<Timed> it = marks.iterator(); it.hasNext(); ) {
            Timed m = it.next();
            m.ticks--;
            if (m.ticks > 0 && m.ticks % 3 == 0) {
                double r = 1.0 + 1.8 * m.ticks / 30.0;
                for (int i = 0; i < 16; i++) {
                    double a = Math.PI * 2 * i / 16;
                    sw.spawnParticles(ParticleTypes.BUBBLE, m.pos.x + Math.cos(a) * r, m.pos.y + 0.6, m.pos.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0);
                    sw.spawnParticles(new DustParticleEffect(TIDE, 1.0f), m.pos.x + Math.cos(a) * r, m.pos.y + 0.1, m.pos.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
            }
            if (m.ticks > 0) continue;
            it.remove();
            if (!sw.getFluidState(BlockPos.ofFloored(m.pos).up()).isIn(FluidTags.WATER)) continue;     // no sea, no pressure
            sw.spawnParticles(ParticleTypes.EXPLOSION, m.pos.x, m.pos.y + 1, m.pos.z, 1, 0, 0, 0, 0);
            sw.spawnParticles(ParticleTypes.BUBBLE_POP, m.pos.x, m.pos.y + 1, m.pos.z, 40, 1.2, 1.2, 1.2, 0.2);
            sw.playSound(null, BlockPos.ofFloored(m.pos), SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 1.2f, 1.6f);
            for (LivingEntity e : Abilities.victims(this, 64)) {
                if (e.getPos().squaredDistanceTo(m.pos) > 2.6 * 2.6) continue;
                if (e.damage(this.getDamageSources().mobAttack(this), 10f))
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 2), this);
            }
        }
    }

    // ------------------------------------------------------------------ UNDERTOW (phase 2): a rolling pressure ring
    void startUndertow() {
        undertowR = 1.5;
        undertowC = getPos().add(0, 1.2, 0);
        undertowHit.clear();
        this.playSound(SoundEvents.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.6f);
    }

    private void tickUndertow(ServerWorld sw) {
        if (undertowR < 0) return;
        undertowR += 0.6;
        if (undertowR > 18) { undertowR = -1; return; }
        int n = (int) (undertowR * 6);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            double x = undertowC.x + Math.cos(a) * undertowR, z = undertowC.z + Math.sin(a) * undertowR;
            sw.spawnParticles(ParticleTypes.BUBBLE, x, undertowC.y, z, 1, 0, 0.4, 0, 0);
            if (i % 2 == 0) sw.spawnParticles(new DustParticleEffect(TIDE, 1.6f), x, undertowC.y, z, 1, 0, 0.3, 0, 0);
        }
        for (LivingEntity e : Abilities.victims(this, 19)) {
            double dx = e.getX() - undertowC.x, dz = e.getZ() - undertowC.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(d - undertowR) > 0.9 || Math.abs(e.getY() + 0.9 - undertowC.y) > 1.5) continue;   // swim over/under it
            if (!undertowHit.add(e.getUuid())) continue;
            if (e.damage(this.getDamageSources().mobAttack(this), 9f)) {
                e.addVelocity(dx / Math.max(0.1, d) * 1.2, 0.3, dz / Math.max(0.1, d) * 1.2);
                e.velocityModified = true;
            }
        }
    }

    // ------------------------------------------------------------------ MAELSTROM (phase 2)
    void startMaelstrom() {
        maelstromTicks = 50;
        this.getNavigation().stop();
        this.playSound(SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, 1.5f, 0.5f);
    }

    private void tickMaelstrom(ServerWorld sw) {
        if (maelstromTicks <= 0) return;
        maelstromTicks--;
        for (int i = 0; i < 3; i++) {
            double a = (this.age * 0.35 + i * 2.1), r = 3 + (this.age * 0.7 + i * 5) % 14;
            sw.spawnParticles(ParticleTypes.BUBBLE, getX() + Math.cos(a) * r, getY() + 1 + i, getZ() + Math.sin(a) * r, 3, 0.2, 0.3, 0.2, 0);
            sw.spawnParticles(ParticleTypes.NAUTILUS, getX() + Math.cos(a) * r, getY() + 1.5, getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0.2);
        }
        for (LivingEntity e : Abilities.victims(this, 20)) {
            Vec3d to = getPos().subtract(e.getPos()).multiply(1, 0, 1);
            double d = to.length();
            if (d < 0.5) continue;
            Vec3d in = to.normalize(), tangent = new Vec3d(-in.z, 0, in.x);
            e.addVelocity(in.x * 0.06 + tangent.x * 0.08, 0, in.z * 0.06 + tangent.z * 0.08);
            e.velocityModified = true;
            if (d < 3.2 && maelstromTicks % 10 == 0) e.damage(this.getDamageSources().mobAttack(this), 4f);
        }
    }

    @Override
    protected void onEnrage() {
        if (this.getWorld() instanceof ServerWorld sw)
            broadcast(sw, Text.literal("\"You have drowned your last hope. Feel the WRATH OF THE DEEP!\"").formatted(Formatting.DARK_PURPLE), false);
    }

    // ------------------------------------------------------------------ /ppboss court (testing)
    public void debugSetSluices(int n) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        for (int i = 0; i < sluices.size(); i++) {
            BlockPos p = sluices.get(i);
            BlockState st = sw.getBlockState(p);
            if (!st.isOf(ModBlocks.TIDE_SLUICE)) continue;
            sw.setBlockState(p, st.with(TideSluiceBlock.OPEN, i < n));
            if (i < n) openedAt.put(p, sw.getTime() - 100);
        }
        if (n == 0) lockTicks = 0;
        idleTicks = -2400;                                  // 2 min grace so a test with no players nearby isn't reset
        recountSluices(sw);
    }

    public String debugStatus() {
        if (court == null) return "Abyssal King at " + getBlockPos().toShortString() + ": no court (spawned outside the Sunken Court)";
        return "Abyssal King at " + getBlockPos().toShortString() + ": sluices " + openNow.size() + "/" + sluices.size() + " open, water level y="
                + level + " (floor " + court.floor() + ", top " + court.top() + ", target " + targetLevel() + "), blessing x" + blessing()
                + (stranded ? ", STRANDED " + strandedTicks / 20 + " s" : "") + (lockTicks > 0 ? ", locked " + lockTicks / 20 + " s" : "");
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        if (court != null) {
            nbt.putIntArray("Court", court.toArray());
            nbt.putLongArray("Sluices", sluices.stream().mapToLong(BlockPos::asLong).toArray());
            nbt.putInt("Level", level);
        }
        nbt.putBoolean("Stranded", stranded);
        nbt.putInt("StrandTicks", strandedTicks);
        nbt.putInt("Lock", lockTicks);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("Court")) {
            court = Court.of(nbt.getIntArray("Court"));
            sluices.clear();
            for (long l : nbt.getLongArray("Sluices")) sluices.add(BlockPos.fromLong(l));
            level = nbt.contains("Level") ? nbt.getInt("Level") : (court != null ? court.top() : Integer.MIN_VALUE);
            if (court != null) this.addCommandTag("sunken_court");
        }
        stranded = nbt.getBoolean("Stranded");
        this.dataTracker.set(STRANDED, stranded);
        strandedTicks = nbt.getInt("StrandTicks");
        lockTicks = nbt.getInt("Lock");
    }

    // =====================================================================================
    // ability effects (wired in MobSpecs)
    // =====================================================================================
    public static Abilities.Effect reseal() { return (mob, t) -> { if (mob instanceof AbyssalKingEntity k) k.startReseal(); }; }

    public static Abilities.Effect riptide() { return (mob, t) -> { if (mob instanceof AbyssalKingEntity k) k.startRiptide(t); }; }

    public static Abilities.Effect pressure() { return (mob, t) -> { if (mob instanceof AbyssalKingEntity k) k.startPressure(); }; }

    public static Abilities.Effect undertow() { return (mob, t) -> { if (mob instanceof AbyssalKingEntity k) k.startUndertow(); }; }

    public static Abilities.Effect maelstrom() { return (mob, t) -> { if (mob instanceof AbyssalKingEntity k) k.startMaelstrom(); }; }

    /** Royal Guard: two Tide Wardens, never more than three around him. */
    public static Abilities.Effect royalGuard() {
        Abilities.Effect spawn = Abilities.summon("pixelpirates:tide_warden", 2, 3);
        return (mob, t) -> {
            var type = ModMobs.TYPES.get("tide_warden");
            if (type != null && mob.getWorld().getEntitiesByType(type, mob.getBoundingBox().expand(32), LivingEntity::isAlive).size() >= 3) return;
            spawn.fire(mob, t);
        };
    }
}
