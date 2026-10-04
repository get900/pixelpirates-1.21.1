package net.get900.pixelpirates.world;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.util.ModTags;
import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.get900.pixelpirates.world.dungeon.DungeonPlacement;
import net.get900.pixelpirates.world.dungeon.GallowsGrotto;
import net.get900.pixelpirates.world.dungeon.TitansChest;
import net.get900.pixelpirates.world.leviathan.LeviathanRoute;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.WorldEvents;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.primitives.AABBdc;
import org.joml.primitives.AABBic;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * KEELBREAKER (Shipwright refit, 2026-10-01): a moving ship grinds away the seabed in its path so coral heads and
 * sandbars no longer stop it dead. Level I: {@link ModTags.Blocks#KEEL_SOFT} (sand, gravel, clay, coral, silt);
 * level II adds {@link ModTags.Blocks#KEEL_ROCK} (natural stone, ores - ores drop their loot).
 *
 * <p>A world block is ground away only when ALL of these hold:
 * <ul>
 *   <li>it is below sea level, and its column's top solid block (OCEAN_FLOOR heightmap) is at or below the water
 *       surface - so islands, beaches, piers and anything that breaks the surface are never touched (the ship still
 *       runs aground on real land);</li>
 *   <li>it is in the whitelist tag for the ship's level, has a collision box and no block entity;</li>
 *   <li>it touches the actual hull: mapped into ship space, a hull block sits in it, directly above it, beside it, or
 *       up to {@link #LOOKAHEAD} blocks behind it along the direction of travel (so the channel is hull-shaped);</li>
 *   <li>it is not in or near a dungeon / lair / Leviathan site (Pixel Pirates dimension only).</li>
 * </ul>
 * Ground blocks become water source. World terrain edits are reported to VS2 as terrain changes (MixinLevelChunk ->
 * onSetBlock), which is how its collision learns the channel is open.
 */
public final class KeelBreaker {
    private KeelBreaker() {}

    public static final String KEY = "keelbreaker";

    private static final int INTERVAL = 2;            // ticks between passes
    private static final int LOOKAHEAD = 3;           // blocks ahead of the hull cleared along the travel direction
    private static final int MAX_PER_PASS = 64;       // blocks per ship per pass (lag cap)
    private static final int FX_PER_PASS = 6;         // break particles/sounds per ship per pass
    private static final double MIN_SPEED = 0.6;      // m/s - slower than this counts as stopped (then helm input decides)
    private static final int SITE_PAD = 12;           // protection margin round dungeon footprints (+ half a chunk)

    /** Protected-chunk cache for the PP dimension (deterministic per seed). */
    private static final Map<Long, Boolean> PROTECTED = new HashMap<>();
    private static long protectedSeed;
    private static long lastErrorTick = -100000;

    public static void tick(MinecraftServer server) {
        if (server.getTicks() % INTERVAL != 0) return;
        ShipRegistryState reg = ShipRegistryState.get(server.getOverworld());
        for (ServerWorld world : server.getWorlds()) {
            VsiServerShipWorld shipWorld = VSGameUtilsKt.getShipObjectWorld(world);
            if (shipWorld == null) continue;
            for (LoadedServerShip ship : shipWorld.getLoadedShips().getIdToShipData().values()) {
                long id = ship.getId();
                int level = reg.getUpgradeLevel(id, KEY);
                if (level <= 0) continue;
                if (ShipHealthState.SINKING_SHIPS.getOrDefault(id, false) || ShipSteeringManager.ANCHORED_SHIPS.contains(id)) continue;
                try {
                    grind(world, ship, level);
                } catch (Exception e) {
                    if (server.getTicks() - lastErrorTick > 1200) {
                        lastErrorTick = server.getTicks();
                        PixelPirates.LOGGER.warn("[Keelbreaker] pass failed for ship {}", id, e);
                    }
                }
            }
        }
    }

    private static void grind(ServerWorld world, LoadedServerShip ship, int level) {
        AABBic hull = ship.getShipAABB();
        if (hull == null) return;
        var tf = ship.getTransform();
        Matrix4dc worldToShip = tf.getWorldToShip();

        // which way is the hull going, in ship space? (+Z = bow)
        Vector3d vel = new Vector3d(ship.getVelocity());
        double speed = Math.sqrt(vel.x * vel.x + vel.z * vel.z);
        float[] in = ShipSteeringManager.SHIP_INPUTS.get(ship.getId());
        float fwd = in != null ? in[0] : 0f, turn = in != null ? in[1] : 0f;
        Vector3d dir;
        if (speed >= MIN_SPEED) {
            dir = worldToShip.transformDirection(new Vector3d(vel.x, 0, vel.z), new Vector3d());
            dir.y = 0;
            if (dir.lengthSquared() < 1e-6) return;
            dir.normalize();
        } else if (Math.abs(fwd) > 0.01f) {
            dir = new Vector3d(0, 0, Math.signum(fwd));        // stuck but the helm is pushing: clear ahead/astern
        } else if (Math.abs(turn) > 0.01f) {
            dir = new Vector3d();                              // turning on the spot: only what touches the hull
        } else {
            return;                                            // drifting at rest - leave the seabed alone
        }

        int sea = world.getSeaLevel();
        AABBdc wb = ship.getWorldAABB();
        int minY = Math.max(world.getBottomY(), (int) Math.floor(wb.minY()) - 1);
        int maxY = Math.min(sea - 1, (int) Math.ceil(wb.maxY()));
        if (minY > maxY) return;                               // hull is entirely above the water
        int pad = LOOKAHEAD + 1;
        int x0 = (int) Math.floor(wb.minX()) - pad, x1 = (int) Math.ceil(wb.maxX()) + pad;
        int z0 = (int) Math.floor(wb.minZ()) - pad, z1 = (int) Math.ceil(wb.maxZ()) + pad;

        boolean ppDim = world.getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD);
        BlockPos.Mutable m = new BlockPos.Mutable();
        Vector3d p = new Vector3d();
        List<BlockPos> cut = new ArrayList<>();

        outer:
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                int cx = x >> 4, cz = z >> 4;
                if (!world.getChunkManager().isChunkLoaded(cx, cz)) continue;   // never load chunks from here
                int top = Integer.MIN_VALUE;                                   // column check, done lazily
                for (int y = minY; y <= maxY; y++) {
                    m.set(x, y, z);
                    BlockState state = world.getBlockState(m);
                    if (state.isAir() || state.isOf(Blocks.WATER)) continue;
                    if (!state.isIn(ModTags.Blocks.KEEL_SOFT) && !(level >= 2 && state.isIn(ModTags.Blocks.KEEL_ROCK))) continue;
                    if (state.hasBlockEntity() || state.getCollisionShape(world, m).isEmpty()) continue;
                    if (top == Integer.MIN_VALUE) {
                        top = world.getTopY(Heightmap.Type.OCEAN_FLOOR, x, z);  // y above the top solid block
                        if (top > sea) break;                                   // the column breaks the surface: land
                        if (ppDim && isProtected(world, cx, cz, maxY)) break;
                    }
                    worldToShip.transformPosition(p.set(x + 0.5, y + 0.5, z + 0.5));
                    if (!touchesHull(world, hull, p, dir)) continue;
                    cut.add(m.toImmutable());
                    if (cut.size() >= MAX_PER_PASS) break outer;
                }
            }
        }
        if (cut.isEmpty()) return;

        BlockState water = Blocks.WATER.getDefaultState();
        int fx = 0;
        for (BlockPos pos : cut) {
            BlockState old = world.getBlockState(pos);
            if (old.isIn(net.fabricmc.fabric.api.tag.convention.v1.ConventionalBlockTags.ORES)) Block.dropStacks(old, world, pos);
            world.setBlockState(pos, water, Block.NOTIFY_ALL);
            if (fx++ < FX_PER_PASS) world.syncWorldEvent(WorldEvents.BLOCK_BROKEN, pos, Block.getRawIdFromState(old));
        }
    }

    /** p = the world block's centre in ship space. Is a hull block in it, above it, beside it, or just behind it? */
    private static boolean touchesHull(ServerWorld world, AABBic hull, Vector3d p, Vector3d dir) {
        for (int k = 0; k <= LOOKAHEAD; k++) {
            double qx = p.x - dir.x * k, qz = p.z - dir.z * k;
            if (hullAt(world, hull, qx, p.y, qz) || hullAt(world, hull, qx, p.y + 1, qz)) return true;
            if (dir.lengthSquared() == 0) break;
        }
        return hullAt(world, hull, p.x + 1, p.y, p.z) || hullAt(world, hull, p.x - 1, p.y, p.z)
                || hullAt(world, hull, p.x, p.y, p.z + 1) || hullAt(world, hull, p.x, p.y, p.z - 1);
    }

    private static boolean hullAt(ServerWorld world, AABBic hull, double sx, double sy, double sz) {
        int x = (int) Math.floor(sx), y = (int) Math.floor(sy), z = (int) Math.floor(sz);
        if (x < hull.minX() || x > hull.maxX() || y < hull.minY() || y > hull.maxY() || z < hull.minZ() || z > hull.maxZ()) return false;
        BlockState s = world.getBlockState(new BlockPos(x, y, z));
        return !s.isAir() && !s.isOf(Blocks.WATER);
    }

    /** Is this chunk in or near a dungeon, lair or Leviathan site? (cached; errors count as protected) */
    private static boolean isProtected(ServerWorld world, int cx, int cz, int y) {
        if (protectedSeed != world.getSeed()) { PROTECTED.clear(); protectedSeed = world.getSeed(); }
        long key = ChunkPos.toLong(cx, cz);
        Boolean hit = PROTECTED.get(key);
        if (hit != null) return hit;
        boolean result;
        try {
            var gen = world.getChunkManager().getChunkGenerator();
            var ctx = new DungeonPlacement.Context(world.getSeed(), gen, world.getChunkManager().getNoiseConfig(), world, gen.getSeaLevel());
            BlockPos c = new BlockPos((cx << 4) + 8, y, (cz << 4) + 8);
            result = DungeonPlacement.nearAnySite(ctx, c, SITE_PAD)
                    || GallowsGrotto.nearFootprint(ctx, c, SITE_PAD)
                    || TitansChest.nearFootprint(ctx, c, SITE_PAD)
                    || LeviathanRoute.nearAnySite(world.getSeed(), c.getX(), c.getZ(), SITE_PAD);
        } catch (Exception e) {
            result = true;
        }
        if (PROTECTED.size() > 8192) PROTECTED.clear();
        PROTECTED.put(key, result);
        return result;
    }
}
