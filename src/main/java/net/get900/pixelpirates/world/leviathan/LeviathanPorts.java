package net.get900.pixelpirates.world.leviathan;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;

/**
 * THE PORTS FALL. When the fleeing Leviathan passes Saltmarrow or Brightwater (LeviathanHunt), the port is rewritten
 * from its intact plan to its RUINED plan - only the cells that differ. Loaded chunks are wrecked live as a wave
 * rolling out from where it came through (splashes, blasts, collapsing timber); chunks nobody had loaded are rewritten
 * silently the next time they load (ServerChunkEvents.CHUNK_LOAD -> {@link #onChunkLoad}). LeviathanState remembers
 * which chunks are done, so it happens exactly once. (The ports can be rebuilt later - the forts.)
 */
public final class LeviathanPorts {
    private LeviathanPorts() {}

    public static final String[] IDS = {LeviathanRoute.SALTMARROW, LeviathanRoute.BRIGHTWATER};
    public static final String[] NAMES = {"Saltmarrow", "Brightwater"};

    /** A live wreck in progress: diff cells sorted by distance from the Leviathan's line, applied in a wave. */
    private record Wave(ServerWorld world, int port, List<long[]> cells, int[] cursor) {}
    private static final List<Wave> WAVES = new ArrayList<>();

    /** The Leviathan has come through port `i` (heading along dx, dz). */
    public static void ruin(ServerWorld world, int i, double fromX, double fromZ) {
        LeviathanState st = LeviathanState.get(world);
        if (st.portRuined[i]) return;
        st.portRuined[i] = true;
        st.markDirty();
        long seed = world.getSeed();
        LeviathanSites.Built intact = LeviathanSites.built(seed, IDS[i], false), ruined = LeviathanSites.built(seed, IDS[i], true);
        SiteLayout L = intact.layout();
        List<long[]> cells = new ArrayList<>();
        for (int cx = (intact.site().x() + L.X0) >> 4; cx <= (intact.site().x() + L.X1) >> 4; cx++)
            for (int cz = (intact.site().z() + L.Z0) >> 4; cz <= (intact.site().z() + L.Z1) >> 4; cz++) {
                long key = ChunkPos.toLong(cx, cz);
                if (!world.isChunkLoaded(cx, cz)) continue;                                 // done when it next loads
                st.ruinedChunks[i].add(key);
                diff(intact, ruined, cx << 4, cz << 4, (x, y, z, idx) -> cells.add(new long[]{x, y, z, idx}));
            }
        st.markDirty();
        cells.sort((a, b) -> Double.compare(Math.hypot(a[0] - fromX, a[2] - fromZ), Math.hypot(b[0] - fromX, b[2] - fromZ)));
        synchronized (WAVES) { WAVES.add(new Wave(world, i, cells, new int[]{0})); }
        PixelPirates.LOGGER.info("[Leviathan] {} falls: {} blocks rewritten live", NAMES[i], cells.size());
    }

    @FunctionalInterface
    interface Cell { void at(int x, int y, int z, int ruinedIndex); }

    /** Every cell of chunk (minX, minZ) where the ruin differs from the intact port. Index -1 = back to the sea / sky. */
    private static void diff(LeviathanSites.Built intact, LeviathanSites.Built ruined, int minX, int minZ, Cell out) {
        SiteLayout A = intact.layout(), B = ruined.layout();
        for (int x = minX; x < minX + 16; x++) for (int z = minZ; z < minZ + 16; z++) {
            int rx = x - intact.site().x(), rz = z - intact.site().z();
            if (rx < A.X0 || rx > A.X1 || rz < A.Z0 || rz > A.Z1) continue;
            for (int y = A.Y0; y <= A.Y1; y++) {
                String a = A.at(rx, y, rz), b = B.at(rx, y, rz);
                if (a == null && b == null || a != null && a.equals(b)) continue;
                out.at(x, y, z, b == null ? -1 : B.get(rx, y, rz) - 1);
            }
        }
    }

    private static void set(ServerWorld w, LeviathanSites.Built ruined, int x, int y, int z, int idx) {
        BlockPos p = new BlockPos(x, y, z);
        BlockState s = idx < 0 ? (y <= SiteLayout.SEA ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState()) : ruined.states()[idx];
        w.setBlockState(p, s, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
        String loot = ruined.layout().loot.get(SiteLayout.key(x - ruined.site().x(), y, z - ruined.site().z()));
        if (loot != null) LootableContainerBlockEntity.setLootTable(w, w.getRandom(), p, new Identifier(loot));
    }

    /** Server tick: roll the live waves on (~1500 blocks a tick, with the carnage to go with it). */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        ServerWorld pw = LeviathanHunt.world(server);
        if (pw != null) drainPending(pw);
        synchronized (WAVES) {
            WAVES.removeIf(wv -> {
                LeviathanSites.Built ruined = LeviathanSites.built(wv.world().getSeed(), IDS[wv.port()], true);
                int end = Math.min(wv.cells().size(), wv.cursor()[0] + 1500);
                for (int k = wv.cursor()[0]; k < end; k++) {
                    long[] c = wv.cells().get(k);
                    set(wv.world(), ruined, (int) c[0], (int) c[1], (int) c[2], (int) c[3]);
                    if (k % 90 == 0) {
                        wv.world().spawnParticles(k % 180 == 0 ? ParticleTypes.EXPLOSION : ParticleTypes.LARGE_SMOKE, c[0] + 0.5, c[1] + 0.5, c[2] + 0.5, 3, 1, 1, 1, 0.05);
                        wv.world().spawnParticles(ParticleTypes.SPLASH, c[0] + 0.5, c[1] + 1, c[2] + 0.5, 30, 2, 1, 2, 0.3);
                    }
                    if (k % 400 == 0) {
                        BlockPos bp = new BlockPos((int) c[0], (int) c[1], (int) c[2]);
                        wv.world().playSound(null, bp, k % 800 == 0 ? SoundEvents.ENTITY_GENERIC_EXPLODE : SoundEvents.BLOCK_WOOD_BREAK, SoundCategory.BLOCKS, 3.0f, 0.6f);
                    }
                }
                wv.cursor()[0] = end;
                return end >= wv.cells().size();
            });
        }
    }

    /** Port chunks that loaded after their port fell, waiting for the next server tick (port index, chunk). */
    private static final java.util.ArrayDeque<long[]> PENDING = new java.util.ArrayDeque<>();

    /**
     * A port chunk loading after its port fell: QUEUE it (rewritten on the next tick by {@link #drainPending}). Never write
     * blocks inside CHUNK_LOAD - a block at the chunk edge touches its neighbour, which may itself be mid-load, and the
     * server thread deadlocks (the watchdog killed the test server this way, 2026-09-30).
     */
    public static void onChunkLoad(ServerWorld world, WorldChunk chunk) {
        if (!world.getRegistryKey().equals(net.get900.pixelpirates.world.dimension.ModDimensions.PIXEL_PIRATES_WORLD)) return;
        LeviathanState st = LeviathanState.get(world);
        if (!st.portRuined[0] && !st.portRuined[1]) return;
        ChunkPos cp = chunk.getPos();
        LeviathanRoute.Route route = LeviathanRoute.of(world.getSeed());
        for (int i = 0; i < 2; i++) {
            if (!st.portRuined[i] || st.ruinedChunks[i].contains(cp.toLong())) continue;
            if (!LeviathanSites.nearChunk(route.byId(IDS[i]), cp.getStartX(), cp.getStartZ())) continue;
            synchronized (PENDING) { PENDING.add(new long[]{i, cp.toLong()}); }
        }
    }

    private static void drainPending(ServerWorld world) {
        LeviathanState st = LeviathanState.get(world);
        long seed = world.getSeed();
        for (int n = 0; n < 8; n++) {                                   // a few chunks a tick
            long[] job;
            synchronized (PENDING) { job = PENDING.poll(); }
            if (job == null) return;
            int i = (int) job[0];
            ChunkPos cp = new ChunkPos(job[1]);
            if (!st.portRuined[i] || st.ruinedChunks[i].contains(job[1]) || !world.isChunkLoaded(cp.x, cp.z)) continue;
            LeviathanSites.Built intact = LeviathanSites.built(seed, IDS[i], false), ruined = LeviathanSites.built(seed, IDS[i], true);
            st.ruinedChunks[i].add(job[1]);
            st.markDirty();
            diff(intact, ruined, cp.getStartX(), cp.getStartZ(), (x, y, z, idx) -> set(world, ruined, x, y, z, idx));
        }
    }

    /** /ppleviathan restore <port>: put a ruined port back as it was (testing - and, later, the rebuild). */
    public static int restore(ServerWorld world, int i) {
        LeviathanState st = LeviathanState.get(world);
        long seed = world.getSeed();
        LeviathanSites.Built intact = LeviathanSites.built(seed, IDS[i], false);
        SiteLayout L = intact.layout();
        int n = 0;
        st.portRuined[i] = false;                                        // first: nothing loading below may queue a ruin
        st.markDirty();
        for (int cx = (intact.site().x() + L.X0) >> 4; cx <= (intact.site().x() + L.X1) >> 4; cx++)
            for (int cz = (intact.site().z() + L.Z0) >> 4; cz <= (intact.site().z() + L.Z1) >> 4; cz++) {
                world.getChunk(cx, cz);
                if (LeviathanSites.renderChunk(world, intact, cx << 4, cz << 4, Block.NOTIFY_LISTENERS | Block.FORCE_STATE)) n++;
            }
        // cells the ruin added where the intact plan leaves the world alone (wreckage on roofs etc.) go back to sea/sky
        LeviathanSites.Built ruined = LeviathanSites.built(seed, IDS[i], true);
        for (int cx = (intact.site().x() + L.X0) >> 4; cx <= (intact.site().x() + L.X1) >> 4; cx++)
            for (int cz = (intact.site().z() + L.Z0) >> 4; cz <= (intact.site().z() + L.Z1) >> 4; cz++)
                diff(ruined, intact, cx << 4, cz << 4, (x, y, z, idx) -> { if (idx < 0) world.setBlockState(new BlockPos(x, y, z),
                        y <= SiteLayout.SEA ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE); });
        st.portRuined[i] = false;
        st.ruinedChunks[i].clear();
        st.markDirty();
        return n;
    }
}
