package net.get900.pixelpirates.world.leviathan;

import com.mojang.serialization.Codec;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.world.gen.density.ZoneTerrainFunction;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders the Leviathan's five sites (LeviathanRoute) chunk by chunk, like the Gallows Grotto: every chunk inside a
 * site copies its own column slice of that site's {@link SiteLayout}. Placed in EVERY biome (after the Titan's Chest).
 * Ports are always generated INTACT; LeviathanPorts rewrites them to the ruin once the Leviathan has passed.
 * Nothing is spawned here - the Leviathan only ever exists through LeviathanHunt.
 */
public class LeviathanSites extends Feature<DefaultFeatureConfig> {
    public LeviathanSites(Codec<DefaultFeatureConfig> codec) { super(codec); }

    /** A built layout, where it stands, and its parsed palette. */
    public record Built(LeviathanRoute.Site site, SiteLayout layout, BlockState[] states) {
        public BlockPos world(int[] rel) { return new BlockPos(site.x() + rel[0], rel[1], site.z() + rel[2]); }
    }

    private static final Map<String, Built> CACHE = new HashMap<>();
    private static long cacheSeed = Long.MIN_VALUE;

    /** The layout for a site (ports: intact or ruined). Built once per world seed. */
    public static Built built(long seed, String id, boolean ruined) {
        String key = id + (ruined ? "#ruin" : "");
        synchronized (CACHE) {
            if (seed != cacheSeed) { CACHE.clear(); cacheSeed = seed; }
            Built b = CACHE.get(key);
            if (b != null) return b;
        }
        LeviathanRoute.Route route = LeviathanRoute.of(seed);
        LeviathanRoute.Site s = route.byId(id);
        java.util.function.IntBinaryOperator t = (x, z) -> ZoneTerrainFunction.terrainTop(s.x() + x, s.z() + z);
        long ls = seed ^ id.hashCode() * 0x9E3779B97F4A7C15L;
        SiteLayout L = switch (id) {
            case LeviathanRoute.RIFT -> new RiftLayout(ls, t);
            case LeviathanRoute.GULLET -> new GulletLayout(ls, t);
            case LeviathanRoute.SPIRE -> {
                LeviathanRoute.Site bw = route.brightwater();
                yield new SpireLayout(ls, t, Math.atan2(bw.z() - s.z(), bw.x() - s.x()));
            }
            case LeviathanRoute.SALTMARROW -> new SaltmarrowLayout(ls, t, ruined);
            default -> new BrightwaterLayout(ls, t, ruined);
        };
        BlockState[] states = new BlockState[L.palette().size()];
        for (int i = 0; i < states.length; i++) states[i] = parse(L.palette().get(i));
        Built b = new Built(s, L, states);
        synchronized (CACHE) { CACHE.put(key, b); }
        return b;
    }

    static BlockState parse(String desc) {
        try {
            return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), desc, false).blockState();
        } catch (Exception e) {
            PixelPirates.LOGGER.error("[Leviathan] bad palette entry '{}' - using stone", desc);
            return Blocks.STONE.getDefaultState();
        }
    }

    /** Is (x, z) inside a site's box? */
    static boolean overlaps(LeviathanRoute.Site s, SiteLayout probe, int minX, int minZ) {
        return !(minX + 15 < s.x() + probe.X0 || minX > s.x() + probe.X1 || minZ + 15 < s.z() + probe.Z0 || minZ > s.z() + probe.Z1);
    }

    static boolean nearChunk(LeviathanRoute.Site s, int minX, int minZ) {
        int r = s.radius() + 8;
        return !(minX + 15 < s.x() - r || minX > s.x() + r || minZ + 15 < s.z() - r || minZ > s.z() + r);
    }

    /** Inside a site's built volume (or standing on it): natural decoration and natural spawns keep out. */
    public static boolean insideVolume(long seed, BlockPos pos) {
        for (LeviathanRoute.Site s : LeviathanRoute.of(seed).all()) {
            if (s.dist(pos.getX(), pos.getZ()) > s.radius() + 8) continue;
            SiteLayout L = built(seed, s.id(), false).layout();
            int rx = pos.getX() - s.x(), rz = pos.getZ() - s.z();
            if (L.get(rx, pos.getY(), rz) != 0 || L.get(rx, pos.getY() - 1, rz) != 0 || pos.getY() >= L.clearFrom(rx, rz)) return true;
        }
        return false;
    }

    /** Inside one of the three LAIRS (not the ports - towns may have their wildlife): no natural spawns. */
    public static boolean inLair(long seed, BlockPos pos) {
        LeviathanRoute.Route r = LeviathanRoute.of(seed);
        for (LeviathanRoute.Site s : List.of(r.rift(), r.gullet(), r.spire())) if (s.dist(pos.getX(), pos.getZ()) < s.radius()) return true;
        return false;
    }

    @Override
    public boolean generate(FeatureContext<DefaultFeatureConfig> ctx) {
        var world = ctx.getWorld();
        int minX = ctx.getOrigin().getX() & ~15, minZ = ctx.getOrigin().getZ() & ~15;
        long seed = world.getSeed();
        boolean any = false;
        try {
            for (LeviathanRoute.Site s : LeviathanRoute.of(seed).all())
                if (nearChunk(s, minX, minZ)) any |= renderChunk(world, built(seed, s.id(), false), minX, minZ, Block.NOTIFY_LISTENERS);
        } catch (Exception e) {
            PixelPirates.LOGGER.error("[Leviathan] site chunk {} {} failed", minX, minZ, e);
        }
        return any;
    }

    /** Copy one chunk's slice of a site into the world (+ loot tables, + carving pillars above the plan). */
    public static boolean renderChunk(WorldAccess world, Built b, int minX, int minZ, int flags) {
        SiteLayout L = b.layout();
        BlockPos.Mutable p = new BlockPos.Mutable();
        boolean any = false;
        for (int x = minX; x < minX + 16; x++) for (int z = minZ; z < minZ + 16; z++) {
            int rx = x - b.site().x(), rz = z - b.site().z();
            if (rx < L.X0 || rx > L.X1 || rz < L.Z0 || rz > L.Z1) continue;
            for (int y = L.Y0; y <= L.Y1; y++) {
                int id = L.get(rx, y, rz);
                if (id == 0) continue;
                p.set(x, y, z);
                world.setBlockState(p, b.states()[id - 1], flags);
                any = true;
                String loot = L.loot.get(SiteLayout.key(rx, y, rz));
                if (loot != null) LootableContainerBlockEntity.setLootTable(world, world.getRandom(), p, new Identifier(loot));
            }
            // natural rock towering above the plan (the Pillar Sea's pillars over the Rift): carved to the sky
            int from = L.clearFrom(rx, rz);
            if (from != Integer.MAX_VALUE) {
                int top = Math.min(world.getTopY() - 1, L.top(rx, rz));
                for (int y = Math.max(from, L.Y1 + 1); y <= top; y++) { p.set(x, y, z); world.setBlockState(p, Blocks.AIR.getDefaultState(), flags); any = true; }
            }
        }
        return any;
    }

    /** Test: how much of a site's plan actually stands in the (loaded part of the) world. */
    public static String check(net.minecraft.server.world.ServerWorld world, String id, boolean ruined) {
        Built b = built(world.getSeed(), id, ruined);
        SiteLayout L = b.layout();
        int plan = 0, match = 0, unloaded = 0;
        java.util.Map<String, Integer> wrong = new java.util.TreeMap<>();
        BlockPos.Mutable p = new BlockPos.Mutable();
        for (int rx = L.X0; rx <= L.X1; rx++) for (int rz = L.Z0; rz <= L.Z1; rz++) {
            int x = b.site().x() + rx, z = b.site().z() + rz;
            if (!world.isChunkLoaded(x >> 4, z >> 4)) { unloaded++; continue; }
            for (int y = L.Y0; y <= L.Y1; y++) {
                int v = L.get(rx, y, rz);
                if (v == 0) continue;
                plan++;
                BlockState want = b.states()[v - 1], got = world.getBlockState(p.set(x, y, z));
                if (got.getBlock() == want.getBlock()) match++;
                else wrong.merge(Registries.BLOCK.getId(want.getBlock()).getPath() + "->" + Registries.BLOCK.getId(got.getBlock()).getPath(), 1, Integer::sum);
            }
        }
        StringBuilder out = new StringBuilder("CHECK " + id + (ruined ? " ruined" : "") + " at " + b.site().x() + "," + b.site().z()
                + ": " + match + "/" + plan + " plan blocks match, unloaded columns " + unloaded);
        wrong.entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(6).forEach(e -> out.append(" | ").append(e.getKey()).append(" x").append(e.getValue()));
        return out.toString();
    }

    /** /ppleviathan build <site>: build a whole site with its centre here (live world; takes a few seconds). */
    public static int buildAt(net.minecraft.server.world.ServerWorld world, String id, BlockPos centre, boolean ruined) {
        Built real = built(world.getSeed(), id, ruined);
        LeviathanRoute.Site moved = new LeviathanRoute.Site(id, centre.getX(), centre.getZ(), real.site().radius());
        Built b = new Built(moved, real.layout(), real.states());
        int n = 0;
        SiteLayout L = b.layout();
        for (int cx = (centre.getX() + L.X0) >> 4; cx <= (centre.getX() + L.X1) >> 4; cx++)
            for (int cz = (centre.getZ() + L.Z0) >> 4; cz <= (centre.getZ() + L.Z1) >> 4; cz++) {
                world.getChunk(cx, cz);
                if (renderChunk(world, b, cx << 4, cz << 4, Block.NOTIFY_LISTENERS | Block.FORCE_STATE)) n++;
            }
        return n;
    }
}
