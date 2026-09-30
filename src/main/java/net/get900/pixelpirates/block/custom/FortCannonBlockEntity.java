package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.entity.custom.CannonBallEntity;
import net.get900.pixelpirates.entity.mob.ModMobs;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Brain of a fort wall cannon. While a fort captain (captain_rackham) is alive within
 * {@link #MANNED_RANGE}, it picks the nearest survival player inside its firing arc, with a clear
 * line of sight, lights the fuse (smoke + hiss = the telegraph) and lobs a terrain-safe
 * {@link CannonBallEntity#fort} shot on a solved ballistic arc with a little lead and scatter.
 * When the captain falls the fort goes quiet. Rackham's "Broadside!" ability also queues shots at
 * marked spots through {@link #queueShot}, ignoring arc and cooldown.
 */
public class FortCannonBlockEntity extends BlockEntity {
    public static final double RANGE = 44, MIN_RANGE = 5, MANNED_RANGE = 64;
    private static final double SPEED = 1.5, GRAVITY = 0.04, DRAG = 0.99;
    private static final double ARC_COS = Math.cos(Math.toRadians(80));
    private static final int FUSE = 24;
    private static final float DAMAGE = 7f;

    private int cooldown = 60;
    private int fuse = -1;
    private int mannedCheck;
    private boolean manned;
    @Nullable private PlayerEntity target;
    @Nullable private Vec3d forcedAim;

    public FortCannonBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.FORT_CANNON_BLOCK_ENTITY, pos, state);
    }

    public static void serverTick(World world, BlockPos pos, BlockState state, FortCannonBlockEntity be) {
        if (!(world instanceof ServerWorld sw)) return;
        if (be.fuse > 0) {
            be.fuse--;
            Vec3d m = be.muzzle(state);
            if (be.fuse % 3 == 0) sw.spawnParticles(ParticleTypes.SMOKE, m.x, m.y + 0.2, m.z, 2, 0.05, 0.05, 0.05, 0.01);
            if (be.fuse % 6 == 0) sw.spawnParticles(ParticleTypes.SMALL_FLAME, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5, 1, 0.05, 0, 0.05, 0);
            if (be.fuse == 0) be.fire(sw, state);
            return;
        }
        if (be.cooldown > 0) { be.cooldown--; return; }
        if (--be.mannedCheck <= 0) {
            be.mannedCheck = 20;
            be.manned = captainNear(sw, pos);
        }
        if (!be.manned) { be.cooldown = 20; return; }
        PlayerEntity p = be.pickTarget(sw, state);
        if (p == null) { be.cooldown = 10; return; }
        be.target = p;
        be.forcedAim = null;
        be.fuse = FUSE;
        be.cooldown = 90 + sw.random.nextInt(60);
        sw.playSound(null, pos, SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.HOSTILE, 1.0f, 1.2f);
    }

    /** Is a living fort captain close enough to man the guns? */
    public static boolean captainNear(ServerWorld sw, BlockPos pos) {
        var type = ModMobs.TYPES.get("captain_rackham");
        if (type == null) return false;
        return !sw.getEntitiesByType(type, new Box(pos).expand(MANNED_RANGE), Entity::isAlive).isEmpty();
    }

    /** Rackham's broadside: fire at a fixed point after {@code delay} ticks, regardless of arc. */
    public void queueShot(Vec3d aim, int delay) {
        if (world == null) return;
        this.forcedAim = aim;
        this.target = null;
        this.fuse = Math.max(1, delay);
        this.cooldown = 60;
        world.playSound(null, pos, SoundEvents.ENTITY_TNT_PRIMED, SoundCategory.HOSTILE, 1.0f, 1.0f);
    }

    public boolean isBusy() { return fuse > 0; }

    private Vec3d facingVec(BlockState state) {
        Direction d = state.get(FortCannonBlock.FACING);
        return new Vec3d(d.getOffsetX(), 0, d.getOffsetZ());
    }

    public Vec3d muzzle(BlockState state) {
        return Vec3d.ofCenter(pos).add(facingVec(state).multiply(0.9)).add(0, 0.2, 0);
    }

    @Nullable
    private PlayerEntity pickTarget(ServerWorld sw, BlockState state) {
        Vec3d m = muzzle(state), f = facingVec(state);
        PlayerEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (PlayerEntity p : sw.getPlayers()) {
            if (!p.isAlive() || p.isSpectator() || p.isCreative()) continue;
            Vec3d to = p.getPos().subtract(m);
            double h = Math.sqrt(to.x * to.x + to.z * to.z);
            if (h < MIN_RANGE || h > RANGE) continue;
            if ((to.x * f.x + to.z * f.z) / h < ARC_COS) continue;
            if (!clearShot(sw, m, p.getEyePos())) continue;
            if (h < bestD) { bestD = h; best = p; }
        }
        return best;
    }

    private boolean clearShot(ServerWorld sw, Vec3d from, Vec3d to) {
        var hit = sw.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, (Entity) null));
        return hit.getType() == HitResult.Type.MISS || hit.getPos().squaredDistanceTo(to) < 1.0;
    }

    private void fire(ServerWorld sw, BlockState state) {
        Vec3d m = muzzle(state);
        Vec3d aim;
        if (forcedAim != null) {
            aim = forcedAim;
        } else {
            if (target == null || !target.isAlive() || target.isSpectator() || target.squaredDistanceTo(m) > (RANGE + 8) * (RANGE + 8)) {
                target = null;
                return;
            }
            // lead the target a little, then scatter so a moving player is rarely hit dead-on
            double flight = target.getPos().distanceTo(m) / SPEED;
            aim = target.getPos().add(target.getVelocity().multiply(flight * 0.6))
                    .add((sw.random.nextDouble() - 0.5) * 2.4, 0, (sw.random.nextDouble() - 0.5) * 2.4);
        }
        CannonBallEntity ball = CannonBallEntity.fort(sw, null, m.x, m.y, m.z, DAMAGE);
        ball.setVelocity(solve(m, aim));
        sw.spawnEntity(ball);
        sw.spawnParticles(ParticleTypes.EXPLOSION, m.x, m.y, m.z, 1, 0, 0, 0, 0);
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, m.x, m.y, m.z, 10, 0.3, 0.3, 0.3, 0.05);
        sw.playSound(null, pos, SoundEvents.ENTITY_GENERIC_EXPLODE, SoundCategory.HOSTILE, 2.5f, 1.4f);
        target = null;
        forcedAim = null;
    }

    /**
     * Launch velocity that lands on {@code aim}: binary search on the pitch of a low arc, simulated
     * with the cannon ball's own physics (gravity before the move, 0.99 drag). Out of range -> the
     * longest shot along the same heading.
     */
    public static Vec3d solve(Vec3d from, Vec3d aim) {
        Vec3d d = aim.subtract(from);
        double dist = Math.sqrt(d.x * d.x + d.z * d.z);
        double hx = dist < 1e-3 ? 0 : d.x / dist, hz = dist < 1e-3 ? 1 : d.z / dist;
        double lo = Math.toRadians(-40), hi = Math.toRadians(44);
        if (heightAt(hi, dist) < d.y) return dir(hx, hz, hi);
        for (int i = 0; i < 24; i++) {
            double mid = (lo + hi) / 2;
            if (heightAt(mid, dist) < d.y) lo = mid; else hi = mid;
        }
        return dir(hx, hz, (lo + hi) / 2);
    }

    private static Vec3d dir(double hx, double hz, double pitch) {
        return new Vec3d(hx * Math.cos(pitch) * SPEED, Math.sin(pitch) * SPEED, hz * Math.cos(pitch) * SPEED);
    }

    private static double heightAt(double pitch, double dist) {
        double vx = Math.cos(pitch) * SPEED, vy = Math.sin(pitch) * SPEED, x = 0, y = 0;
        for (int t = 0; t < 200; t++) {
            vy -= GRAVITY;
            double nx = x + vx, ny = y + vy;
            if (nx >= dist) return y + (ny - y) * (dist - x) / Math.max(1e-6, nx - x);
            x = nx; y = ny;
            vx *= DRAG; vy *= DRAG;
            if (y < -80) break;
        }
        return -1e9;
    }

    /** Fort cannons within {@code radius} of {@code center} (loaded chunks only). */
    public static List<FortCannonBlockEntity> near(ServerWorld sw, BlockPos center, int radius) {
        List<FortCannonBlockEntity> out = new ArrayList<>();
        for (int cx = (center.getX() - radius) >> 4; cx <= (center.getX() + radius) >> 4; cx++)
            for (int cz = (center.getZ() - radius) >> 4; cz <= (center.getZ() + radius) >> 4; cz++) {
                if (!sw.isChunkLoaded(cx, cz)) continue;
                WorldChunk ch = sw.getChunk(cx, cz);
                for (BlockEntity be : ch.getBlockEntities().values())
                    if (be instanceof FortCannonBlockEntity f && be.getPos().isWithinDistance(center, radius)) out.add(f);
            }
        return out;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("Cooldown", cooldown);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        cooldown = nbt.getInt("Cooldown");
    }
}
