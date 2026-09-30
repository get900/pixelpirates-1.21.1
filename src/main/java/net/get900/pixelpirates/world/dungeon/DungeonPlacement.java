package net.get900.pixelpirates.world.dungeon;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.util.math.random.CheckedRandom;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Structure-style spacing for dungeons (replaces the old 1-in-N-chunks rarity roll, which clustered
 * and overlapped). The world is cut into a grid of {@code spacing x spacing} chunk cells per dungeon
 * type; each cell holds AT MOST ONE site. The site is chosen up front from the chunk generator's own
 * predictions (biome at the chunk centre + worldgen heightmaps), trying several chunks in the cell, so
 * a cell only goes empty when it genuinely has no suitable ground. It is deterministic per seed, so
 * {@link #locate} can find dungeons that have not been generated yet.
 *
 * Different dungeon types never share a site: a candidate within {@link #MIN_GAP} chunks of an
 * earlier type's candidate (earlier in {@link Dungeons#ALL}) is rejected.
 */
public final class DungeonPlacement {
    private DungeonPlacement() {}

    /** Chunks kept between a candidate and its cell edge, and between two dungeons of different types. */
    public static final int MIN_GAP = 4;
    static final String RIFT = "leviathan_rift";
    private static final int TRIES = 16;

    /** Everything needed to predict terrain without a loaded chunk. */
    public record Context(long seed, ChunkGenerator generator, NoiseConfig noise, HeightLimitView heightView, int seaLevel) {}

    private static final ChunkPos NONE = new ChunkPos(Integer.MIN_VALUE, Integer.MIN_VALUE);
    private static final Map<String, ChunkPos> CACHE = new ConcurrentHashMap<>();

    public static boolean isCandidate(Dungeons.Type t, Context ctx, ChunkPos chunk) {
        ChunkPos c = candidate(t, ctx, Math.floorDiv(chunk.x, t.spacing()), Math.floorDiv(chunk.z, t.spacing()));
        return c != null && c.equals(chunk);
    }

    /** The chosen chunk for one grid cell, or null when the cell has no usable site. */
    public static ChunkPos candidate(Dungeons.Type t, Context ctx, int cellX, int cellZ) {
        String key = ctx.seed() + "|" + t.id() + "|" + cellX + "|" + cellZ;
        ChunkPos cached = CACHE.get(key);
        if (cached == null) {
            if (CACHE.size() > 50_000) CACHE.clear();
            cached = compute(t, ctx, cellX, cellZ);
            CACHE.put(key, cached);
        }
        return cached == NONE ? null : cached;
    }

    private static ChunkPos compute(Dungeons.Type t, Context ctx, int cellX, int cellZ) {
        if (t.id().equals(RIFT)) {                                   // ONE rift per world: the Leviathan's route decides it
            var rift = net.get900.pixelpirates.world.leviathan.LeviathanRoute.of(ctx.seed()).rift();
            ChunkPos c = new ChunkPos(rift.x() >> 4, rift.z() >> 4);
            return Math.floorDiv(c.x, t.spacing()) == cellX && Math.floorDiv(c.z, t.spacing()) == cellZ ? c : NONE;
        }
        ChunkRandom r = new ChunkRandom(new CheckedRandom(0L));
        r.setRegionSeed(ctx.seed(), cellX, cellZ, t.id().hashCode());
        int sp = t.spacing(), span = Math.max(1, sp - 2 * MIN_GAP);
        int earlier = Dungeons.ALL.indexOf(t);
        for (int i = 0; i < TRIES; i++) {
            ChunkPos c = new ChunkPos(cellX * sp + MIN_GAP + r.nextInt(span), cellZ * sp + MIN_GAP + r.nextInt(span));
            if (!biomeMatches(t, ctx, c) || !siteLooksValid(t, ctx, c)) continue;
            // the Leviathan's lairs and ports own their ground: every other dungeon keeps 96 blocks clear of them
            if (net.get900.pixelpirates.world.leviathan.LeviathanRoute.nearAnySite(ctx.seed(), c.getCenterX(), c.getCenterZ(), 96)) continue;
            if (conflicts(c, ctx, earlier)) continue;
            return c;
        }
        return NONE;
    }

    private static boolean biomeMatches(Dungeons.Type t, Context ctx, ChunkPos c) {
        RegistryEntry<Biome> b = ctx.generator().getBiomeSource().getBiome(
                BiomeCoords.fromBlock(c.getCenterX()), BiomeCoords.fromBlock(ctx.seaLevel()), BiomeCoords.fromBlock(c.getCenterZ()),
                ctx.noise().getMultiNoiseSampler());
        for (RegistryKey<Biome> k : t.biomes()) if (b.matchesKey(k)) return true;
        return false;
    }

    /** Heightmap prediction of DungeonFeature's real checks (dry flat land / enough water). */
    private static boolean siteLooksValid(Dungeons.Type t, Context ctx, ChunkPos c) {
        int x = c.getCenterX(), z = c.getCenterZ();
        int floor = height(ctx, x, z, Heightmap.Type.OCEAN_FLOOR_WG);
        int surface = height(ctx, x, z, Heightmap.Type.WORLD_SURFACE_WG);
        int water = surface - floor;
        if (t.id().equals(TitansChest.ID) && !TitansChest.terrainOk(x, z)) return false;     // no maw pit under the chest
        if (t.site() == Dungeons.Site.SEABED) return water >= t.minDepth();
        if (water > 0 || floor < ctx.seaLevel() + 1) return false;
        for (int[] d : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) {
            int f = height(ctx, x + d[0], z + d[1], Heightmap.Type.OCEAN_FLOOR_WG);
            if (Math.abs(f - floor) > 3) return false;
            if (height(ctx, x + d[0], z + d[1], Heightmap.Type.WORLD_SURFACE_WG) != f) return false;   // water there
        }
        return true;
    }

    private static int height(Context ctx, int x, int z, Heightmap.Type type) {
        return ctx.generator().getHeight(x, z, type, ctx.heightView(), ctx.noise());
    }

    /**
     * Is this block inside the footprint of a (predicted) protected dungeon site? Sites keep MIN_GAP chunks
     * from their cell edge, so only the cell containing {@code pos} can hold one - one cached lookup per type.
     */
    public static boolean insideProtectedSite(Context ctx, BlockPos pos) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        for (var e : Dungeons.PROTECTED.entrySet()) {
            Dungeons.Type t = Dungeons.byId(e.getKey());
            if (t == null) continue;
            ChunkPos c = candidate(t, ctx, Math.floorDiv(cx, t.spacing()), Math.floorDiv(cz, t.spacing()));
            if (c == null) continue;
            int r = e.getValue();
            if (Math.abs(pos.getX() - c.getCenterX()) <= r && Math.abs(pos.getZ() - c.getCenterZ()) <= r) return true;
        }
        return false;
    }

    /** Is {@code pos} inside ANY predicted dungeon site's +-22 footprint grown by {@code pad} (any depth)? Sites keep
     *  MIN_GAP chunks (64 blocks) from their cell edge, so for 22 + pad < 64 only pos's own cell can hold one per type. */
    public static boolean nearAnySite(Context ctx, BlockPos pos, int pad) {
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4, r = 22 + pad;
        for (Dungeons.Type t : Dungeons.ALL) {
            ChunkPos c = candidate(t, ctx, Math.floorDiv(cx, t.spacing()), Math.floorDiv(cz, t.spacing()));
            if (c != null && Math.abs(pos.getX() - c.getCenterX()) <= r && Math.abs(pos.getZ() - c.getCenterZ()) <= r) return true;
        }
        return false;
    }

    /** Is {@code pos} within {@code radius} blocks (horizontally) of a predicted site of dungeon type {@code id}?
     *  Sites keep MIN_GAP chunks (64 blocks) from their cell edge, so for radius < 64 only pos's own cell matters. */
    public static boolean nearSite(String id, Context ctx, BlockPos pos, double radius) {
        Dungeons.Type t = Dungeons.byId(id);
        if (t == null) return false;
        ChunkPos c = candidate(t, ctx, Math.floorDiv(pos.getX() >> 4, t.spacing()), Math.floorDiv(pos.getZ() >> 4, t.spacing()));
        if (c == null) return false;
        double dx = pos.getX() - c.getCenterX(), dz = pos.getZ() - c.getCenterZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    /** Too close to a site already claimed by a type earlier in Dungeons.ALL? */
    private static boolean conflicts(ChunkPos c, Context ctx, int upTo) {
        for (int i = 0; i < upTo; i++) {
            Dungeons.Type e = Dungeons.ALL.get(i);
            // candidates keep MIN_GAP from their cell edge, so only the cell containing c can hold one this close
            ChunkPos o = candidate(e, ctx, Math.floorDiv(c.x, e.spacing()), Math.floorDiv(c.z, e.spacing()));
            if (o != null && Math.abs(o.x - c.x) <= MIN_GAP && Math.abs(o.z - c.z) <= MIN_GAP) return true;
        }
        return false;
    }

    /** Nearest predicted site of this type to {@code from}, searching {@code maxRings} cells out; null if none. */
    public static BlockPos locate(Dungeons.Type t, Context ctx, BlockPos from, int maxRings) {
        int sp = t.spacing();
        int ccx = Math.floorDiv(from.getX() >> 4, sp), ccz = Math.floorDiv(from.getZ() >> 4, sp);
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (int ring = 0; ring <= maxRings; ring++) {
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                    ChunkPos c = candidate(t, ctx, ccx + dx, ccz + dz);
                    if (c == null) continue;
                    BlockPos p = new BlockPos(c.getCenterX(), 0, c.getCenterZ());
                    double d = p.getSquaredDistance(from.getX(), 0, from.getZ());
                    if (d < bestD) { bestD = d; best = p; }
                }
            // a hit in ring n can only be beaten by ring n+1 (cells are square), so stop one ring later
            if (best != null && ring > 0 && bestD < Math.pow((ring) * sp * 16.0, 2)) break;
        }
        return best;
    }
}
