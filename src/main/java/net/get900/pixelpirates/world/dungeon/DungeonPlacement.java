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
    private static final int TRIES = 16, COAST_TRIES = 48;

    /**
     * LAYOUT sites (the ChatGPT places) are made RARE without starving the search (2026-10-03). Their Dungeons.ALL spacing is
     * the WANTED average spacing (48-92 chunks); the search grid stays at most LAYOUT_GRID chunks wide and each cell keeps a
     * site with chance (grid/spacing)^2 - the same density, but a fine grid keeps finding the rare spots. With 80-92 chunk
     * cells and a few random picks per cell, a 2026-10-03 test found NO site for 7 of the 39 types although
     * /ppdungeon scan showed dozens of qualifying chunks near spawn. The original dungeons keep grid = spacing, keep = 1
     * and their fixed tries, so their sites never move.
     */
    static final int LAYOUT_GRID = 32;

    private static final Map<String, Boolean> LAYOUT = new ConcurrentHashMap<>();

    /** Cached: this runs for every feature placement (FeatureExclusionMixin -> insideProtectedSite -> grid), and a file +
     *  classpath lookup there stalled spawn generation (2026-10-03). */
    static boolean layout(Dungeons.Type t) { return LAYOUT.computeIfAbsent(t.id(), LayoutStructures::exists); }

    /** Grid cell size in chunks. */
    static int grid(Dungeons.Type t) { return layout(t) ? Math.min(t.spacing(), LAYOUT_GRID) : t.spacing(); }

    /** Chance a cell holds a site at all. */
    static double keep(Dungeons.Type t) {
        if (!layout(t)) return 1.0;
        double g = grid(t);
        return Math.min(1.0, (g * g) / ((double) t.spacing() * t.spacing()));
    }

    /** Random attempts per cell for the original dungeons (layout sites scan their cell instead - see compute). */
    static int tries(Dungeons.Type t) { return t.site() == Dungeons.Site.COAST ? COAST_TRIES : TRIES; }

    /** Everything needed to predict terrain without a loaded chunk. */
    public record Context(long seed, ChunkGenerator generator, NoiseConfig noise, HeightLimitView heightView, int seaLevel) {}

    private static final ChunkPos NONE = new ChunkPos(Integer.MIN_VALUE, Integer.MIN_VALUE);
    private static final Map<String, ChunkPos> CACHE = new ConcurrentHashMap<>();

    public static boolean isCandidate(Dungeons.Type t, Context ctx, ChunkPos chunk) {
        ChunkPos c = candidate(t, ctx, Math.floorDiv(chunk.x, grid(t)), Math.floorDiv(chunk.z, grid(t)));
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
            return Math.floorDiv(c.x, grid(t)) == cellX && Math.floorDiv(c.z, grid(t)) == cellZ ? c : NONE;
        }
        ChunkRandom r = new ChunkRandom(new CheckedRandom(0L));
        r.setRegionSeed(ctx.seed(), cellX, cellZ, t.id().hashCode());
        int sp = grid(t), span = Math.max(1, sp - 2 * MIN_GAP);
        int earlier = Dungeons.ALL.indexOf(t);
        if (layout(t)) {
            // a LAYOUT site: this cell holds one only with chance keep(t); then every 2nd chunk of the cell is checked (a
            // per-cell offset varies the lattice) and one of the spots that pass is picked - rare ground is never missed
            if (r.nextDouble() >= keep(t)) return NONE;
            int ox = r.nextInt(2), oz = r.nextInt(2);
            java.util.List<ChunkPos> ok = new java.util.ArrayList<>();
            for (int dx = ox; dx < span; dx += 2)
                for (int dz = oz; dz < span; dz += 2) {
                    ChunkPos c = new ChunkPos(cellX * sp + MIN_GAP + dx, cellZ * sp + MIN_GAP + dz);
                    if (!biomeMatches(t, ctx, c) || !siteLooksValid(t, ctx, c)) continue;
                    if (net.get900.pixelpirates.world.leviathan.LeviathanRoute.nearAnySite(ctx.seed(), c.getCenterX(), c.getCenterZ(), 96)) continue;
                    ok.add(c);
                }
            while (!ok.isEmpty()) {                                                    // conflicts last: the costly test
                ChunkPos c = ok.remove(r.nextInt(ok.size()));
                if (!conflicts(c, ctx, earlier)) return c;
            }
            return NONE;
        }
        int tries = tries(t);
        for (int i = 0; i < tries; i++) {
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
        if (t.site() == Dungeons.Site.SEABED) return water >= t.minDepth() && (!FLAT_SEABED.contains(t.id()) || seabedFlat(ctx, x, z, floor));
        if (t.site() == Dungeons.Site.ISLET) return isletOk(ctx, x, z);
        if (t.site() == Dungeons.Site.COAST) return coastRotation(ctx, x, z) != null;
        if (water > 0 || floor < ctx.seaLevel() + 1) return false;
        if (FLAT_LAND.contains(t.id()) && landRing(ctx, x, z, floor) > flatTolerance(t)) return false;
        for (int[] d : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) {
            int f = height(ctx, x + d[0], z + d[1], Heightmap.Type.OCEAN_FLOOR_WG);
            if (Math.abs(f - floor) > 3) return false;
            if (height(ctx, x + d[0], z + d[1], Heightmap.Type.WORLD_SURFACE_WG) != f) return false;   // water there
        }
        return true;
    }

    /** ISLET probes: the centre plus the corners and edge midpoints of a +-16 square. */
    private static final int[][] ISLET_PROBES = {{0, 0}, {16, 0}, {-16, 0}, {0, 16}, {0, -16}, {16, 16}, {16, -16}, {-16, 16}, {-16, -16}};

    /**
     * Site test for {@link Dungeons.Site#ISLET} designs (origin = sea level + 1, solid foundation down to -8, open
     * water ring at the edge). Every probe's ground must sit between the foundation bottom (sea level - 7) and two
     * blocks above the beach - so the slab never floats over deep water and never slices into a hillside - and
     * most of the rim must be water, so it is never an inland water square.
     */
    static boolean isletOk(Context ctx, int x, int z) {
        int wet = 0;
        for (int i = 0; i < ISLET_PROBES.length; i++) {
            int px = x + ISLET_PROBES[i][0], pz = z + ISLET_PROBES[i][1];
            int floor = height(ctx, px, pz, Heightmap.Type.OCEAN_FLOOR_WG);
            if (floor < ctx.seaLevel() - 6 || floor > ctx.seaLevel() + 3) return false;
            if (i > 0 && height(ctx, px, pz, Heightmap.Type.WORLD_SURFACE_WG) > floor) wet++;
        }
        return wet >= 5;
    }

    /** SEABED designs with a fixed 5-block foundation (the Phase 2 reef ruins): the seabed must not bridge a ravine
     *  or cut into a hill - every probe of the +-16 ring within 3 blocks of the centre's seabed, and under water. */
    static final java.util.Set<String> FLAT_SEABED = java.util.Set.of("sunken_counting_house", "sirens_bellcourt", "reefcutters_quarry", "tideglass_observatory",
            "drowned_customs_house", "keelbone_ossuary",
            "hushed_bell_court", "lantern_confluence", "ferrymans_balance", "tidewheel_oracle", "mnemonic_reliquary",
            "azure_mosaic_baths", "pearl_courier_waystation", "coral_conservatory", "sundered_prison_barge", "verdict_of_the_four",
            "processional_orrery", "measured_depths_reservoir");

    /** Wide LAND layouts (the Phase 3 + 4 sites, +-17..19 with a 5-block foundation): the +-16 ring must be dry ground
     *  within 4 blocks of the centre - the generic LAND test only looks 6 blocks out, which let a cliff or the sea in. */
    static final java.util.Set<String> FLAT_LAND = java.util.Set.of("cinderchain_tollgate", "sulfur_prospectors_camp", "ashglass_kilnworks",
            "basalt_signal_redoubt", "emberfall_cistern", "widows_lantern_hospice", "chainbreak_salvage_yard", "mourning_archive",
            "turtleback_orchard", "scoria_switchback", "cinderwake_caravansary", "brimstone_railhead", "last_echo_theatre", "blackwake_auction_court");

    static int FLAT_TOL = -1;                                           // -1 = per type (flatTolerance); the scan overrides it

    /** Allowed ring height difference: 8 on Phase 3's rough volcanic ground, 6 elsewhere. A 2026-10-03 /ppdungeon scan:
     *  at 4, only 5 volcanic chunks qualified (49 at 8) and 46 island-thickets ones (124 at 6) - mostly slope, rarely
     *  water; ground blending fills the small gaps under the foundation with natural rock. */
    static int flatTolerance(Dungeons.Type t) { return FLAT_TOL >= 0 ? FLAT_TOL : t.phase() == 3 ? 8 : 6; }

    /** The +-16 ring's largest height difference from the centre; 99 when any probe is under water or lava. */
    static int landRing(Context ctx, int x, int z, int floor) {
        int worst = 0;
        for (int i = 1; i < ISLET_PROBES.length; i++) {
            int px = x + ISLET_PROBES[i][0], pz = z + ISLET_PROBES[i][1];
            int f = height(ctx, px, pz, Heightmap.Type.OCEAN_FLOOR_WG);
            if (height(ctx, px, pz, Heightmap.Type.WORLD_SURFACE_WG) != f) return 99;
            worst = Math.max(worst, Math.abs(f - floor));
        }
        return worst;
    }

    private static boolean seabedFlat(Context ctx, int x, int z, int floor) {
        for (int i = 1; i < ISLET_PROBES.length; i++) {
            int px = x + ISLET_PROBES[i][0], pz = z + ISLET_PROBES[i][1];
            int f = height(ctx, px, pz, Heightmap.Type.OCEAN_FLOOR_WG);
            if (Math.abs(f - floor) > 3 || height(ctx, px, pz, Heightmap.Type.WORLD_SURFACE_WG) <= f) return false;
        }
        return true;
    }

    /**
     * COAST site test + orientation in one (so locate, generation and the build always agree): the first rotation, in a
     * fixed order, whose design-space probes read as a shoreline. Design -Z is the land. The centre line (u=0) must be a
     * real shore: dry low land behind (v=-12, sea+1..sea+5 - the clearing never slices a hillside), beach in the middle
     * (v=0, sea-3..sea+5), open water in front (v=+14, seabed sea-8..sea-1). The sides (u=+-14) only need to be near sea
     * level (sea-5..sea+6), and at least one front side under water: coasts curve, and the design lays its own beach and
     * foundation. Requiring all nine probes to match exactly passed ~0.5% of Phase 1 chunks (2026-10-02 /ppdungeon coastscan).
     */
    public static net.minecraft.util.BlockRotation coastRotation(Context ctx, int x, int z) {
        int sea = ctx.seaLevel();
        for (net.minecraft.util.BlockRotation r : net.minecraft.util.BlockRotation.values()) {
            net.minecraft.util.math.Direction d = r.rotate(net.minecraft.util.math.Direction.SOUTH), p = d.rotateYClockwise();
            boolean ok = true;
            int frontWetSides = 0;
            for (int v : new int[]{14, -12, 0}) {                                           // the sea first: cheapest rejection
                for (int u : new int[]{0, -14, 14}) {
                    int px = x + d.getOffsetX() * v + p.getOffsetX() * u, pz = z + d.getOffsetZ() * v + p.getOffsetZ() * u;
                    int f = height(ctx, px, pz, Heightmap.Type.OCEAN_FLOOR_WG);
                    boolean wet = height(ctx, px, pz, Heightmap.Type.WORLD_SURFACE_WG) > f;
                    if (u != 0) {
                        ok = f >= sea - 5 && f <= sea + 6;
                        if (v > 0 && wet) frontWetSides++;
                    } else ok = v < 0 ? !wet && f >= sea + 1 && f <= sea + 5
                            : v == 0 ? f >= sea - 3 && f <= sea + 5
                            : wet && f >= sea - 8 && f <= sea - 1;
                    if (!ok) break;
                }
                if (!ok) break;
            }
            if (ok && frontWetSides > 0) return r;
        }
        return null;
    }

    /** Op diagnostic (/ppdungeon scan <id> <r>): over chunk centres within r chunks (every 2nd), how many match the type's
     *  biomes, pass its site test, and keep clear of earlier types + the Leviathan's ground - why a type finds no site. */
    public static String siteScan(Dungeons.Type t, Context ctx, int cx, int cz, int r) {
        int n = 0, biome = 0, valid = 0, clear = 0, earlier = Dungeons.ALL.indexOf(t);
        for (int x = cx - r; x <= cx + r; x += 2)
            for (int z = cz - r; z <= cz + r; z += 2) {
                ChunkPos c = new ChunkPos(x, z);
                n++;
                if (!biomeMatches(t, ctx, c)) continue;
                biome++;
                if (!siteLooksValid(t, ctx, c)) continue;
                valid++;
                if (net.get900.pixelpirates.world.leviathan.LeviathanRoute.nearAnySite(ctx.seed(), c.getCenterX(), c.getCenterZ(), 96) || conflicts(c, ctx, earlier)) continue;
                clear++;
            }
        String ring = "";
        if (FLAT_LAND.contains(t.id())) {                              // why the flat test fails: water/lava vs height
            int[] h = new int[5];                                      // <=4, <=6, <=8, <=12, wet
            int saved = FLAT_TOL;
            FLAT_TOL = 99;
            for (int x = cx - r; x <= cx + r; x += 2)
                for (int z = cz - r; z <= cz + r; z += 2) {
                    ChunkPos c = new ChunkPos(x, z);
                    if (!biomeMatches(t, ctx, c) || !siteLooksValid(t, ctx, c)) continue;
                    int floor = height(ctx, c.getCenterX(), c.getCenterZ(), Heightmap.Type.OCEAN_FLOOR_WG);
                    int w = landRing(ctx, c.getCenterX(), c.getCenterZ(), floor);
                    h[w == 99 ? 4 : w <= 4 ? 0 : w <= 6 ? 1 : w <= 8 ? 2 : 3]++;
                }
            FLAT_TOL = saved;
            ring = " | ring <=4:" + h[0] + " <=6:" + h[1] + " <=8:" + h[2] + " >8:" + h[3] + " wet:" + h[4];
        }
        return t.id() + ": sampled " + n + " | in biome " + biome + " | site test " + valid + " | clear " + clear + ring;
    }

    /** Op diagnostic (/ppdungeon coastscan): over Phase 1 chunk centres within r chunks, how often each COAST row test
     *  passes, and how high the land is 12 blocks behind wherever the sea test passes - for tuning coastRotation. */
    public static String coastScan(Context ctx, int cx, int cz, int r) {
        Dungeons.Type t = Dungeons.byId("powderwatch_battery");
        int sea = ctx.seaLevel(), n = 0, front = 0, frontBack = 0, all = 0;
        int[] backHist = new int[16];
        for (int x = cx - r; x <= cx + r; x += 2)
            for (int z = cz - r; z <= cz + r; z += 2) {
                ChunkPos c = new ChunkPos(x, z);
                if (!biomeMatches(t, ctx, c)) continue;
                n++;
                if (coastRotation(ctx, c.getCenterX(), c.getCenterZ()) != null) all++;
                boolean f = false, fb = false;
                for (net.minecraft.util.BlockRotation rot : net.minecraft.util.BlockRotation.values()) {
                    net.minecraft.util.math.Direction d = rot.rotate(net.minecraft.util.math.Direction.SOUTH);
                    int fx = c.getCenterX() + d.getOffsetX() * 14, fz = c.getCenterZ() + d.getOffsetZ() * 14;
                    int ff = height(ctx, fx, fz, Heightmap.Type.OCEAN_FLOOR_WG);
                    if (!(height(ctx, fx, fz, Heightmap.Type.WORLD_SURFACE_WG) > ff && ff >= sea - 8)) continue;
                    f = true;
                    int bx = c.getCenterX() - d.getOffsetX() * 12, bz = c.getCenterZ() - d.getOffsetZ() * 12;
                    int bf = height(ctx, bx, bz, Heightmap.Type.OCEAN_FLOOR_WG);
                    boolean dry = height(ctx, bx, bz, Heightmap.Type.WORLD_SURFACE_WG) == bf;
                    if (dry) { backHist[Math.max(0, Math.min(15, bf - sea))]++; fb = true; }
                }
                if (f) front++;
                if (fb) frontBack++;
            }
        StringBuilder sb = new StringBuilder("P1 chunks " + n + " | sea in front " + front + " | + dry land behind " + frontBack + " | full COAST pass " + all + " | land-behind height above sea (0..15+):");
        for (int i = 0; i < 16; i++) sb.append(' ').append(backHist[i]);
        return sb.toString();
    }

    /** Rotation that turns design +Z (the pier side) toward the deepest of the four edge probes. Deterministic. */
    public static net.minecraft.util.BlockRotation seawardRotation(Context ctx, int x, int z) {
        net.minecraft.util.BlockRotation best = net.minecraft.util.BlockRotation.NONE;
        int lowest = Integer.MAX_VALUE;
        for (net.minecraft.util.BlockRotation r : net.minecraft.util.BlockRotation.values()) {
            net.minecraft.util.math.Direction d = r.rotate(net.minecraft.util.math.Direction.SOUTH);
            int f = height(ctx, x + d.getOffsetX() * 16, z + d.getOffsetZ() * 16, Heightmap.Type.OCEAN_FLOOR_WG);
            if (f < lowest) { lowest = f; best = r; }
        }
        return best;
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
            ChunkPos c = candidate(t, ctx, Math.floorDiv(cx, grid(t)), Math.floorDiv(cz, grid(t)));
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
            ChunkPos c = candidate(t, ctx, Math.floorDiv(cx, grid(t)), Math.floorDiv(cz, grid(t)));
            if (c != null && Math.abs(pos.getX() - c.getCenterX()) <= r && Math.abs(pos.getZ() - c.getCenterZ()) <= r) return true;
        }
        return false;
    }

    /** Is {@code pos} within {@code radius} blocks (horizontally) of a predicted site of dungeon type {@code id}?
     *  Sites keep MIN_GAP chunks (64 blocks) from their cell edge, so for radius < 64 only pos's own cell matters. */
    public static boolean nearSite(String id, Context ctx, BlockPos pos, double radius) {
        Dungeons.Type t = Dungeons.byId(id);
        if (t == null) return false;
        ChunkPos c = candidate(t, ctx, Math.floorDiv(pos.getX() >> 4, grid(t)), Math.floorDiv(pos.getZ() >> 4, grid(t)));
        if (c == null) return false;
        double dx = pos.getX() - c.getCenterX(), dz = pos.getZ() - c.getCenterZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    /** Too close to a site already claimed by a type earlier in Dungeons.ALL? */
    private static boolean conflicts(ChunkPos c, Context ctx, int upTo) {
        for (int i = 0; i < upTo; i++) {
            Dungeons.Type e = Dungeons.ALL.get(i);
            // candidates keep MIN_GAP from their cell edge, so only the cell containing c can hold one this close
            ChunkPos o = candidate(e, ctx, Math.floorDiv(c.x, grid(e)), Math.floorDiv(c.z, grid(e)));
            if (o != null && Math.abs(o.x - c.x) <= MIN_GAP && Math.abs(o.z - c.z) <= MIN_GAP) return true;
        }
        return false;
    }

    /** Nearest predicted site of this type to {@code from}, searching {@code maxRings} cells out; null if none. */
    public static BlockPos locate(Dungeons.Type t, Context ctx, BlockPos from, int maxRings) {
        int sp = grid(t);
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
