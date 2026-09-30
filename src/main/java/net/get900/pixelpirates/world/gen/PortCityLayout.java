package net.get900.pixelpirates.world.gen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic block plan for the spawn-island port city ("Wavebreak Port").
 *
 * Pure Java on purpose — no Minecraft imports. The plan is a palette-indexed
 * 3D short grid in absolute world coordinates; {@code SpawnIslandFeature}
 * renders one chunk-sized slice of it per chunk. Palette entries are block
 * state strings ("minecraft:oak_stairs[facing=north]") parsed once by the
 * feature. Id 0 = untouched terrain; AIR is an explicit palette entry used to
 * clear the city airspace.
 *
 * Run {@link #main} to render a top-down preview PNG without booting the game.
 */
public final class PortCityLayout {

    private PortCityLayout() {}

    // ------------------------------------------------------------------
    // Bounds (absolute world coordinates, inclusive)
    // ------------------------------------------------------------------
    public static final int X0 = -165, X1 = 165;
    public static final int Z0 = -160, Z1 = 160;
    public static final int Y0 = 48,  Y1 = 118;

    private static final int W = X1 - X0 + 1;
    private static final int H = Y1 - Y0 + 1;
    private static final int D = Z1 - Z0 + 1;

    private static volatile short[] grid;
    private static final List<String> PALETTE = new ArrayList<>();
    private static final Map<String, Integer> PALETTE_IDS = new HashMap<>();
    /** grid index -> loot table id, for chests that should carry loot. */
    private static final Map<Integer, String> LOOT = new HashMap<>();

    // ------------------------------------------------------------------
    // Public API (used by SpawnIslandFeature)
    // ------------------------------------------------------------------

    public static short[] ensureBuilt() {
        short[] g = grid;
        if (g == null) {
            synchronized (PortCityLayout.class) {
                if (grid == null) build();
                g = grid;
            }
        }
        return g;
    }

    public static boolean chunkIntersects(int chunkMinX, int chunkMinZ) {
        return chunkMinX + 15 >= X0 && chunkMinX <= X1
            && chunkMinZ + 15 >= Z0 && chunkMinZ <= Z1;
    }

    /** Palette id at world pos, or 0 for untouched terrain. */
    public static int get(int x, int y, int z) {
        if (x < X0 || x > X1 || y < Y0 || y > Y1 || z < Z0 || z > Z1) return 0;
        return grid[idx(x, y, z)];
    }

    public static List<String> palette() {
        ensureBuilt();
        return PALETTE;
    }

    /** Loot table for a chest at world pos, or null. */
    public static String lootAt(int x, int y, int z) {
        return LOOT.get(idx(x, y, z));
    }

    private static int idx(int x, int y, int z) {
        return ((z - Z0) * H + (y - Y0)) * W + (x - X0);
    }

    // ------------------------------------------------------------------
    // Palette
    // ------------------------------------------------------------------

    private static int id(String desc) {
        Integer existing = PALETTE_IDS.get(desc);
        if (existing != null) return existing;
        PALETTE.add(desc);
        int i = PALETTE.size(); // ids start at 1; 0 = untouched
        PALETTE_IDS.put(desc, i);
        return i;
    }

    // Core materials — resolved once in build()
    private static int AIR, WATER;
    private static int STONE_BRICKS, CRACKED_STONE_BRICKS, CHISELED_STONE_BRICKS, MOSSY_STONE_BRICKS;
    private static int COBBLE, MOSSY_COBBLE, ANDESITE, POLISHED_ANDESITE, GRAVEL, DIRT, GRASS, DIRT_PATH, SAND;
    private static int OAK, SPRUCE, DARK_OAK, BIRCH, PIRATE_PLANKS, DARK_PIRATE_PLANKS, PALM_PLANKS;
    private static int OAK_LOG_Y, OAK_LOG_X, OAK_LOG_Z, SPRUCE_LOG_Y, SPRUCE_LOG_X, SPRUCE_LOG_Z;
    private static int DARK_OAK_LOG_Y, DARK_OAK_LOG_X, DARK_OAK_LOG_Z, STRIPPED_SPRUCE_Y;
    private static int PALM_LOG_Y;
    private static int OAK_FENCE, SPRUCE_FENCE, DARK_OAK_FENCE;
    private static int STONE_BRICK_WALL, COBBLE_WALL;
    private static int GLASS_PANE;
    private static int LANTERN, LANTERN_HANGING, SEA_LANTERN, GLOWSTONE, CHAIN;
    private static int BARREL_UP, BARREL_N, CRAFTING, CARTOGRAPHY, SMITHING, ANVIL_N, GRINDSTONE, STONECUTTER_S,
            BLAST_FURNACE_S, SMOKER_S, FURNACE_S, LECTERN_N, LECTERN_E, LECTERN_W, JUKEBOX, BOOKSHELF,
            BREWING, CAULDRON_WATER, COMPOSTER, BELL, CAMPFIRE, HAY, PUMPKIN, MELON, LADDER_N, LADDER_S,
            IRON_BARS, NOTE_BLOCK;
    private static int GROG_BARREL, GUNPOWDER_BARREL, ANCHOR_BLOCK, SHIPWRIGHT_TABLE, MAP_BLOCK, WATER_LIGHT,
            COCONUT_BLOCK, PALM_LEAVES, OAK_LEAVES;
    private static int POLISHED_BLACKSTONE, BLACKSTONE_WALL, BLACK_WOOL, WHITE_WOOL, RED_WOOL,
            PIRATE_STONE_BRICKS, SMOOTH_SANDSTONE, RED_TERRACOTTA, POLISHED_DIORITE;
    private static int BLACK_BANNER_S, BLACK_BANNER_N;
    private static int SHORT_GRASS, POPPY, DANDELION, OXEYE, CORNFLOWER;
    private static int POTTED_TULIP, POTTED_DANDELION;

    private static int stairs(String mat, String facing)          { return id("minecraft:" + mat + "_stairs[facing=" + facing + "]"); }
    private static int stairsTop(String mat, String facing)       { return id("minecraft:" + mat + "_stairs[facing=" + facing + ",half=top]"); }
    private static int slab(String mat)                           { return id("minecraft:" + mat + "_slab"); }
    private static int slabTop(String mat)                        { return id("minecraft:" + mat + "_slab[type=top]"); }
    private static int door(String mat, String facing, boolean up){ return id("minecraft:" + mat + "_door[facing=" + facing + ",half=" + (up ? "upper" : "lower") + "]"); }
    private static int bed(String color, String facing, boolean head) { return id("minecraft:" + color + "_bed[facing=" + facing + ",part=" + (head ? "head" : "foot") + "]"); }
    private static int chest(String facing)                       { return id("minecraft:chest[facing=" + facing + "]"); }
    private static int trapdoorOpen(String mat, String facing)    { return id("minecraft:" + mat + "_trapdoor[facing=" + facing + ",open=true]"); }
    private static int wool(String color)                         { return id("minecraft:" + color + "_wool"); }

    private static void initPalette() {
        AIR = id("minecraft:air");
        WATER = id("minecraft:water");

        STONE_BRICKS = id("minecraft:stone_bricks");
        CRACKED_STONE_BRICKS = id("minecraft:cracked_stone_bricks");
        CHISELED_STONE_BRICKS = id("minecraft:chiseled_stone_bricks");
        MOSSY_STONE_BRICKS = id("minecraft:mossy_stone_bricks");
        COBBLE = id("minecraft:cobblestone");
        MOSSY_COBBLE = id("minecraft:mossy_cobblestone");
        ANDESITE = id("minecraft:andesite");
        POLISHED_ANDESITE = id("minecraft:polished_andesite");
        GRAVEL = id("minecraft:gravel");
        DIRT = id("minecraft:dirt");
        GRASS = id("minecraft:grass_block");
        DIRT_PATH = id("minecraft:dirt_path");
        SAND = id("minecraft:sand");

        OAK = id("minecraft:oak_planks");
        SPRUCE = id("minecraft:spruce_planks");
        DARK_OAK = id("minecraft:dark_oak_planks");
        BIRCH = id("minecraft:birch_planks");
        PIRATE_PLANKS = id("pixelpirates:pirate_planks");
        DARK_PIRATE_PLANKS = id("pixelpirates:dark_pirate_planks");
        PALM_PLANKS = id("pixelpirates:palm_planks");

        OAK_LOG_Y = id("minecraft:oak_log[axis=y]");
        OAK_LOG_X = id("minecraft:oak_log[axis=x]");
        OAK_LOG_Z = id("minecraft:oak_log[axis=z]");
        SPRUCE_LOG_Y = id("minecraft:spruce_log[axis=y]");
        SPRUCE_LOG_X = id("minecraft:spruce_log[axis=x]");
        SPRUCE_LOG_Z = id("minecraft:spruce_log[axis=z]");
        DARK_OAK_LOG_Y = id("minecraft:dark_oak_log[axis=y]");
        DARK_OAK_LOG_X = id("minecraft:dark_oak_log[axis=x]");
        DARK_OAK_LOG_Z = id("minecraft:dark_oak_log[axis=z]");
        STRIPPED_SPRUCE_Y = id("minecraft:stripped_spruce_log[axis=y]");
        PALM_LOG_Y = id("pixelpirates:palm_log[axis=y]");

        OAK_FENCE = id("minecraft:oak_fence");
        SPRUCE_FENCE = id("minecraft:spruce_fence");
        DARK_OAK_FENCE = id("minecraft:dark_oak_fence");
        STONE_BRICK_WALL = id("minecraft:stone_brick_wall");
        COBBLE_WALL = id("minecraft:cobblestone_wall");
        GLASS_PANE = id("minecraft:glass_pane");

        LANTERN = id("minecraft:lantern");
        LANTERN_HANGING = id("minecraft:lantern[hanging=true]");
        SEA_LANTERN = id("minecraft:sea_lantern");
        GLOWSTONE = id("minecraft:glowstone");
        CHAIN = id("minecraft:chain[axis=y]");

        BARREL_UP = id("minecraft:barrel[facing=up]");
        BARREL_N = id("minecraft:barrel[facing=north]");
        CRAFTING = id("minecraft:crafting_table");
        CARTOGRAPHY = id("minecraft:cartography_table");
        SMITHING = id("minecraft:smithing_table");
        ANVIL_N = id("minecraft:anvil[facing=north]");
        GRINDSTONE = id("minecraft:grindstone[face=floor,facing=south]");
        STONECUTTER_S = id("minecraft:stonecutter[facing=south]");
        BLAST_FURNACE_S = id("minecraft:blast_furnace[facing=south]");
        SMOKER_S = id("minecraft:smoker[facing=south]");
        FURNACE_S = id("minecraft:furnace[facing=south]");
        LECTERN_N = id("minecraft:lectern[facing=north]");
        LECTERN_E = id("minecraft:lectern[facing=east]");
        LECTERN_W = id("minecraft:lectern[facing=west]");
        JUKEBOX = id("minecraft:jukebox");
        BOOKSHELF = id("minecraft:bookshelf");
        BREWING = id("minecraft:brewing_stand");
        CAULDRON_WATER = id("minecraft:water_cauldron[level=3]");
        COMPOSTER = id("minecraft:composter");
        BELL = id("minecraft:bell");
        CAMPFIRE = id("minecraft:campfire");
        HAY = id("minecraft:hay_block");
        PUMPKIN = id("minecraft:pumpkin");
        MELON = id("minecraft:melon");
        LADDER_N = id("minecraft:ladder[facing=north]");
        LADDER_S = id("minecraft:ladder[facing=south]");
        IRON_BARS = id("minecraft:iron_bars");
        NOTE_BLOCK = id("minecraft:note_block");

        GROG_BARREL = id("pixelpirates:grog_barrel");
        // gunpowder_barrel exists only as a texture (no registered block) — use plain barrels
        GUNPOWDER_BARREL = id("minecraft:barrel[facing=up]");
        ANCHOR_BLOCK = id("pixelpirates:anchor_block");
        SHIPWRIGHT_TABLE = id("pixelpirates:shipwright_table");
        MAP_BLOCK = id("pixelpirates:map_block");
        WATER_LIGHT = id("pixelpirates:water_light_block");
        COCONUT_BLOCK = id("pixelpirates:coconut_block");
        PALM_LEAVES = id("pixelpirates:palm_leaves[persistent=true]");
        OAK_LEAVES = id("minecraft:oak_leaves[persistent=true]");

        POLISHED_BLACKSTONE = id("minecraft:polished_blackstone");
        BLACKSTONE_WALL = id("minecraft:blackstone_wall");
        BLACK_WOOL = wool("black");
        WHITE_WOOL = wool("white");
        RED_WOOL = wool("red");
        PIRATE_STONE_BRICKS = id("pixelpirates:pirate_stone_bricks");
        SMOOTH_SANDSTONE = id("minecraft:smooth_sandstone");
        RED_TERRACOTTA = id("minecraft:red_terracotta");
        POLISHED_DIORITE = id("minecraft:polished_diorite");
        BLACK_BANNER_S = id("minecraft:black_wall_banner[facing=south]");
        BLACK_BANNER_N = id("minecraft:black_wall_banner[facing=north]");

        SHORT_GRASS = id("minecraft:grass");
        POPPY = id("minecraft:poppy");
        DANDELION = id("minecraft:dandelion");
        OXEYE = id("minecraft:oxeye_daisy");
        CORNFLOWER = id("minecraft:cornflower");
        POTTED_TULIP = id("minecraft:potted_red_tulip");
        POTTED_DANDELION = id("minecraft:potted_dandelion");
    }

    // ------------------------------------------------------------------
    // Grid primitives
    // ------------------------------------------------------------------

    private static void set(int x, int y, int z, int blockId) {
        if (x < X0 || x > X1 || y < Y0 || y > Y1 || z < Z0 || z > Z1) return;
        grid[idx(x, y, z)] = (short) blockId;
    }

    private static void fill(int x1, int y1, int z1, int x2, int y2, int z2, int blockId) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, blockId);
    }

    private static int getRaw(int x, int y, int z) {
        if (x < X0 || x > X1 || y < Y0 || y > Y1 || z < Z0 || z > Z1) return -1;
        return grid[idx(x, y, z)];
    }

    /** Ground column: surface block at y, filler below, air cleared above. */
    private static void ground(int x, int z, int surfaceY, int surfaceId, int fillerId, int depth, int clearTo) {
        set(x, surfaceY, z, surfaceId);
        for (int y = surfaceY - depth; y < surfaceY; y++) set(x, y, z, fillerId);
        for (int y = surfaceY + 1; y <= clearTo; y++) set(x, y, z, AIR);
    }

    private static void lamppost(int x, int gy, int z) {
        set(x, gy + 1, z, SPRUCE_FENCE);
        set(x, gy + 2, z, SPRUCE_FENCE);
        set(x, gy + 3, z, SPRUCE_FENCE);
        set(x, gy + 4, z, LANTERN);
    }

    /** Grid of hanging lanterns lighting one interior floor (y = ceiling height). */
    private static void ceilingLanterns(int x1, int z1, int x2, int z2, int y) {
        boolean placed = false;
        for (int x = x1 + 3; x <= x2 - 3; x += 6)
            for (int z = z1 + 3; z <= z2 - 3; z += 6) {
                set(x, y, z, LANTERN_HANGING);
                placed = true;
            }
        if (!placed) set((x1 + x2) / 2, y, (z1 + z2) / 2, LANTERN_HANGING);
    }

    /** Simple rectangular shell: foundation, floor, walls with corner frame logs, cleared interior. */
    /** Every building footprint, for the overlap check in main() (two shells must never intersect). */
    private static final List<int[]> FOOTPRINTS = new ArrayList<>();

    private static void shell(int x1, int z1, int x2, int z2, int gy, int wallH,
                              int floorId, int wallId, int frameId, int baseId) {
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        // Foundation + floor
        fill(x1, gy - 2, z1, x2, gy, z2, COBBLE);
        fill(x1 + 1, gy, z1 + 1, x2 - 1, gy, z2 - 1, floorId);
        // Clear interior
        fill(x1 + 1, gy + 1, z1 + 1, x2 - 1, gy + wallH + 3, z2 - 1, AIR);
        // Walls
        for (int y = gy + 1; y <= gy + wallH; y++) {
            int wall = (y <= gy + 1) ? baseId : wallId;
            for (int x = x1; x <= x2; x++) { set(x, y, z1, wall); set(x, y, z2, wall); }
            for (int z = z1 + 1; z <= z2 - 1; z++) { set(x1, y, z, wall); set(x2, y, z, wall); }
        }
        // Corner frame columns
        for (int y = gy + 1; y <= gy + wallH; y++) {
            set(x1, y, z1, frameId); set(x2, y, z1, frameId);
            set(x1, y, z2, frameId); set(x2, y, z2, frameId);
        }
    }

    /**
     * Gable roof with the ridge running along X. Covers [x1-1, x2+1] with a
     * 1-block eave overhang; slopes rise from both z edges toward the middle.
     */
    private static void gableRoofX(int x1, int x2, int z1, int z2, int yBase,
                                   String stairMat, int planksId) {
        int n = z1 - 1, s = z2 + 1;
        int y = yBase;
        int southStairs = stairs(stairMat, "south");
        int northStairs = stairs(stairMat, "north");
        while (n <= s) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                if (n == s) { set(x, y, n, planksId); }
                else {
                    set(x, y, n, southStairs);
                    set(x, y, s, northStairs);
                    // seal the underside so the attic isn't drafty
                    if (n > z1) set(x, y - 1, n, planksId);
                    if (s < z2) set(x, y - 1, s, planksId);
                }
            }
            n++; s--; y++;
        }
    }

    /** Fills the triangular gable ends (walls under a gableRoofX). */
    private static void gableEndsX(int xEnd, int z1, int z2, int yBase, int wallId) {
        int n = z1, s = z2;
        int y = yBase;
        while (n <= s) {
            for (int z = n; z <= s; z++) set(xEnd, y, z, wallId);
            n++; s--; y++;
        }
    }

    /** Gable roof with the ridge running along Z. */
    private static void gableRoofZ(int z1, int z2, int x1, int x2, int yBase,
                                   String stairMat, int planksId) {
        int w = x1 - 1, e = x2 + 1;
        int y = yBase;
        int eastStairs = stairs(stairMat, "east");
        int westStairs = stairs(stairMat, "west");
        while (w <= e) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                if (w == e) { set(w, y, z, planksId); }
                else {
                    set(w, y, z, eastStairs);
                    set(e, y, z, westStairs);
                    if (w > x1) set(w, y - 1, z, planksId);
                    if (e < x2) set(e, y - 1, z, planksId);
                }
            }
            w++; e--; y++;
        }
    }

    private static void gableEndsZ(int zEnd, int x1, int x2, int yBase, int wallId) {
        int w = x1, e = x2;
        int y = yBase;
        while (w <= e) {
            for (int x = w; x <= e; x++) set(x, y, zEnd, wallId);
            w++; e--; y++;
        }
    }

    private static void windowsAlongX(int x1, int x2, int z, int y, int spacing) {
        for (int x = x1 + 2; x <= x2 - 2; x += spacing) set(x, y, z, GLASS_PANE);
    }

    private static void windowsAlongZ(int z1, int z2, int x, int y, int spacing) {
        for (int z = z1 + 2; z <= z2 - 2; z += spacing) set(x, y, z, GLASS_PANE);
    }

    private static void doorway(int x, int gy, int z) {
        set(x, gy + 1, z, AIR);
        set(x, gy + 2, z, AIR);
    }

    private static void placeDoor(int x, int gy, int z, String mat, String facing) {
        set(x, gy + 1, z, door(mat, facing, false));
        set(x, gy + 2, z, door(mat, facing, true));
    }

    private static void lootChest(int x, int y, int z, String facing, String lootTable) {
        set(x, y, z, chest(facing));
        LOOT.put(idx(x, y, z), lootTable);
    }

    private static void palm(int x, int gy, int z, int height) {
        for (int y = gy + 1; y <= gy + height; y++) set(x, y, z, PALM_LOG_Y);
        int t = gy + height;
        // canopy: cross arms with drooping tips
        set(x, t + 1, z, PALM_LEAVES);
        int[][] arms = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] a : arms) {
            set(x + a[0], t + 1, z + a[1], PALM_LEAVES);
            set(x + 2 * a[0], t, z + 2 * a[1], PALM_LEAVES);
            set(x + 3 * a[0], t - 1, z + 3 * a[1], PALM_LEAVES);
        }
        int[][] diag = {{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] a : diag) {
            set(x + a[0], t, z + a[1], PALM_LEAVES);
            set(x + 2 * a[0], t - 1, z + 2 * a[1], PALM_LEAVES);
        }
        // coconuts
        set(x + 1, t, z, COCONUT_BLOCK);
        set(x, t, z - 1, COCONUT_BLOCK);
    }

    private static void barrelCluster(int x, int gy, int z, Random rng) {
        set(x, gy + 1, z, BARREL_UP);
        if (rng.nextBoolean()) set(x + 1, gy + 1, z, BARREL_UP);
        if (rng.nextBoolean()) set(x, gy + 1, z + 1, BARREL_UP);
        if (rng.nextBoolean()) set(x, gy + 2, z, BARREL_UP);
    }

    // ------------------------------------------------------------------
    // Build orchestration
    // ------------------------------------------------------------------

    private static void build() {
        grid = new short[W * H * D];
        PALETTE.clear();
        PALETTE_IDS.clear();
        LOOT.clear();
        KEEP_CLEAR.clear();
        FOOTPRINTS.clear();
        initPalette();

        Random rng = new Random(0x9E3779B9L);

        paveCityGround();
        terracesAndSteps();
        streets();
        spawnPlaza();

        quayAndHarbor();
        piers();
        shipwrightYard();

        marketSquare();
        tavern();
        inn();
        chandlery();
        bakery();
        harbormaster();
        warehouses();
        fishMarket();
        dockOffice();

        chapel();
        manor();
        guardhouse();
        smithy();
        park();
        houseRows(rng);
        furnishBuildings();

        cityWall();

        lighthouse();
        fort();
        northPaths();
        countryside();
        beachWreck();
        harborBoats();
        buoys();
        northPalms(rng);
        northMeadow(rng);
        lawnDecor(rng);
        cityGreenery(rng);
    }

    // ------------------------------------------------------------------
    // Ground & streets
    // ------------------------------------------------------------------

    private static final int CLEAR_TO = 96; // city airspace cleared up to here

    private static void paveCityGround() {
        for (int x = SpawnIslandTerrain.CITY_X_MIN; x <= SpawnIslandTerrain.CITY_X_MAX; x++) {
            for (int z = SpawnIslandTerrain.CITY_Z_MIN; z <= SpawnIslandTerrain.CITY_Z_MAX; z++) {
                if (!SpawnIslandTerrain.inCityFootprint(x, z)) continue;
                int gy = SpawnIslandTerrain.cityGroundY(x, z);
                ground(x, z, gy, GRASS, DIRT, 4, CLEAR_TO);
            }
        }
    }

    /** Retaining faces + railings where the terraces step down, with grand stairs on the avenue. */
    private static void terracesAndSteps() {
        int zh = SpawnIslandTerrain.TERRACE_HARBOR_Z; // 50: z>=50 → Y65, z<50 → Y67
        int zm = SpawnIslandTerrain.TERRACE_MID_Z;    // -10: z>=-10 → Y67, z<-10 → Y70

        for (int x = SpawnIslandTerrain.CITY_X_MIN; x <= SpawnIslandTerrain.CITY_X_MAX; x++) {
            // Harbor step (67 → 65), face on the z=zh-1 column
            if (SpawnIslandTerrain.inCityFootprint(x, zh - 1) && SpawnIslandTerrain.inCityFootprint(x, zh)) {
                set(x, 66, zh - 1, STONE_BRICKS);
                set(x, 67, zh - 1, STONE_BRICKS);
                boolean onStairs = isTerraceStairX(x);
                if (!onStairs) set(x, 68, zh - 1, STONE_BRICK_WALL);
                else {
                    set(x, 67, zh - 1, stairs("stone_brick", "north"));
                    set(x, 66, zh, stairs("stone_brick", "north"));
                }
            }
            // Upper step (70 → 67), face on the z=zm-1 column
            if (SpawnIslandTerrain.inCityFootprint(x, zm - 1) && SpawnIslandTerrain.inCityFootprint(x, zm)) {
                fill(x, 68, zm - 1, x, 70, zm - 1, STONE_BRICKS);
                boolean onStairs = isTerraceStairX(x);
                if (!onStairs) set(x, 71, zm - 1, STONE_BRICK_WALL);
                else {
                    set(x, 70, zm - 1, stairs("stone_brick", "north"));
                    set(x, 69, zm, stairs("stone_brick", "north"));
                    set(x, 68, zm + 1, stairs("stone_brick", "north"));
                }
            }
        }
    }

    private static boolean isTerraceStairX(int x) {
        return (x >= -6 && x <= 6)      // main avenue — grand stairs
            || (x >= -73 && x <= -67)   // west side avenue
            || (x >= 67 && x <= 73);    // east side avenue
    }

    private static void streets() {
        // Main avenue (north-south spine): polished center, stone brick body, cobble edges
        for (int z = SpawnIslandTerrain.CITY_Z_MIN; z <= 97; z++) {
            if (!SpawnIslandTerrain.inCityFootprint(0, z)) continue;
            int gy = SpawnIslandTerrain.cityGroundY(0, z);
            for (int x = -4; x <= 4; x++) {
                int mat = (Math.abs(x) <= 1) ? POLISHED_ANDESITE : (Math.abs(x) == 4 ? COBBLE : STONE_BRICKS);
                if (SpawnIslandTerrain.inCityFootprint(x, z)) set(x, gy, z, mat);
            }
        }
        // Cross streets
        crossStreetX(52, 58, 65);   // harbor row
        crossStreetX(8, 14, 67);    // market street
        crossStreetX(-48, -42, 70); // upper row
        // Side avenues (x = ±70)
        for (int z = SpawnIslandTerrain.CITY_Z_MIN; z <= 58; z++) {
            for (int x = -73; x <= -67; x++) sideAvenueColumn(x, z);
            for (int x = 67; x <= 73; x++) sideAvenueColumn(x, z);
        }
        // Lampposts along the avenue and cross streets
        for (int z = -80; z <= 90; z += 12) {
            int gy = SpawnIslandTerrain.cityGroundY(0, z);
            if (SpawnIslandTerrain.inCityFootprint(-6, z)) lamppost(-6, gy, z);
            if (SpawnIslandTerrain.inCityFootprint(6, z + 6)) lamppost(6, SpawnIslandTerrain.cityGroundY(6, z + 6), z + 6);
        }
        for (int x = -128; x <= 128; x += 16) {
            if (Math.abs(x) < 8) continue;
            if (SpawnIslandTerrain.inCityFootprint(x, 51)) lamppost(x, 65, 51);
            if (SpawnIslandTerrain.inCityFootprint(x, 15)) lamppost(x, 67, 15);
            if (SpawnIslandTerrain.inCityFootprint(x, -41)) lamppost(x, 70, -41);
        }
    }

    private static void crossStreetX(int zA, int zB, int gy) {
        for (int x = SpawnIslandTerrain.CITY_X_MIN; x <= SpawnIslandTerrain.CITY_X_MAX; x++) {
            for (int z = zA; z <= zB; z++) {
                if (!SpawnIslandTerrain.inCityFootprint(x, z)) continue;
                int mat = (z == zA || z == zB) ? COBBLE : STONE_BRICKS;
                set(x, gy, z, mat);
            }
        }
    }

    private static void sideAvenueColumn(int x, int z) {
        if (!SpawnIslandTerrain.inCityFootprint(x, z)) return;
        int gy = SpawnIslandTerrain.cityGroundY(x, z);
        set(x, gy, z, (Math.abs(x) == 73 || Math.abs(x) == 67) ? COBBLE : STONE_BRICKS);
    }

    /** Compass-rose plaza at world spawn (0,0) on the mid terrace. */
    private static void spawnPlaza() {
        int gy = 67;
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > 8.4) continue;
                if (!SpawnIslandTerrain.inCityFootprint(x, z)) continue;
                int mat = STONE_BRICKS;
                if (d > 7.2) mat = POLISHED_BLACKSTONE;
                else if ((x == 0 || z == 0) && d > 1.5) mat = POLISHED_DIORITE; // rose spokes
                else if (d <= 1.5) mat = CHISELED_STONE_BRICKS;
                set(x, gy, z, mat);
            }
        }
        // Anchor monument pair flanking the plaza
        set(-8, gy + 1, -8, STONE_BRICK_WALL); set(-8, gy + 2, -8, ANCHOR_BLOCK);
        set(8, gy + 1, -8, STONE_BRICK_WALL);  set(8, gy + 2, -8, ANCHOR_BLOCK);
        lamppost(-8, gy, 8); lamppost(8, gy, 8);
    }

    // ------------------------------------------------------------------
    // Harbor: quay, piers, cranes
    // ------------------------------------------------------------------

    private static void quayAndHarbor() {
        // Quay: solid stone platform from z=82 to the waterfront at z=97
        for (int x = -92; x <= 92; x++) {
            for (int z = 82; z <= 97; z++) {
                // Fill down to the seabed so the quay wall reads as a solid mass
                fill(x, 56, z, x, 64, z, z >= 92 ? STONE_BRICKS : COBBLE);
                int top = (x + z) % 7 == 0 ? CRACKED_STONE_BRICKS : STONE_BRICKS;
                set(x, 65, z, top);
                for (int y = 66; y <= CLEAR_TO; y++) set(x, y, z, AIR);
            }
        }
        // Waterfront face accents + bollards + lighting
        for (int x = -92; x <= 92; x++) {
            if (x % 8 == 0) {
                set(x, 64, 97, CHISELED_STONE_BRICKS);
                set(x, 63, 97, CHISELED_STONE_BRICKS);
            }
            if (x % 12 == 0 && Math.abs(x) > 6) {
                set(x, 66, 96, SPRUCE_FENCE);           // mooring bollard
                if (x % 24 == 0) set(x, 67, 96, LANTERN);
            }
            // underwater harbor lights along the quay
            if ((x + 92) % 20 == 0) set(x, 61, 100, WATER_LIGHT);
        }
        // Anchor plinths at the quay ends
        set(-88, 66, 90, STONE_BRICK_WALL); set(-88, 67, 90, ANCHOR_BLOCK);
        set(88, 66, 90, STONE_BRICK_WALL);  set(88, 67, 90, ANCHOR_BLOCK);

        // Cargo cranes on the quay
        crane(-52, 65, 92);
        crane(58, 65, 92);

        // Scattered cargo on the quay
        Random rng = new Random(4242L);
        for (int i = 0; i < 14; i++) {
            int x = -85 + rng.nextInt(170);
            int z = 84 + rng.nextInt(10);
            if (Math.abs(x) < 7 || Math.abs(x + 52) < 4 || Math.abs(x - 58) < 4) continue;
            barrelCluster(x, 65, z, rng);
        }
    }

    /** Timber cargo crane with a jib arm reaching over the water. */
    private static void crane(int x, int gy, int z) {
        // tower
        for (int y = gy + 1; y <= gy + 7; y++) {
            set(x, y, z, SPRUCE_LOG_Y);
            set(x + 1, y, z, SPRUCE_LOG_Y);
        }
        fill(x, gy + 1, z - 1, x + 1, gy + 3, z - 1, SPRUCE);
        // jib arm reaching south over the harbor
        for (int dz = 0; dz <= 6; dz++) set(x, gy + 7, z + dz, SPRUCE_LOG_Z);
        set(x, gy + 7, z + 7, SPRUCE_FENCE);
        // chain + cargo
        set(x, gy + 6, z + 6, CHAIN);
        set(x, gy + 5, z + 6, CHAIN);
        set(x, gy + 4, z + 6, BARREL_UP);
        // counterweight + brace
        set(x, gy + 6, z - 1, stairs("spruce", "north"));
        set(x, gy + 7, z - 1, COBBLE);
    }

    private static void piers() {
        pierSouth(-80, -76, 97, 130);   // west pier
        pierSouth(-3, 3, 97, 148);      // grand pier
        pierSouth(40, 44, 97, 134);     // east pier

        // Grand pier end platform
        for (int x = -7; x <= 7; x++)
            for (int z = 140; z <= 152; z++)
                deckColumn(x, z, OAK);
        for (int y = 66; y <= 68; y++) {
            set(-7, y, 152, SPRUCE_LOG_Y); set(7, y, 152, SPRUCE_LOG_Y);
            set(-7, y, 140, SPRUCE_LOG_Y); set(7, y, 140, SPRUCE_LOG_Y);
        }
        set(-7, 69, 152, LANTERN); set(7, 69, 152, LANTERN);
        set(-7, 69, 140, LANTERN); set(7, 69, 140, LANTERN);
        set(0, 66, 150, BELL);
        // Shipwright table on the pier-end platform: ships spawn ~20 blocks in the
        // direction the buyer faces, so out here they land in open harbor water
        set(-2, 66, 148, SHIPWRIGHT_TABLE);
        set(-3, 66, 148, LECTERN_E);
        // railings on the platform rim
        for (int x = -6; x <= 6; x++) if (x % 2 == 0) set(x, 66, 152, OAK_FENCE);

        // West pier: fishing clutter
        set(-78, 66, 126, BARREL_UP);
        set(-77, 66, 122, BARREL_UP);
        set(-79, 66, 118, CAULDRON_WATER);
        // East pier: cargo
        set(42, 66, 128, BARREL_UP); set(41, 66, 128, BARREL_UP);
        set(42, 67, 128, BARREL_UP);
    }

    private static void pierSouth(int x1, int x2, int z1, int z2) {
        for (int z = z1; z <= z2; z++)
            for (int x = x1; x <= x2; x++)
                deckColumn(x, z, (x + z) % 5 == 0 ? PIRATE_PLANKS : OAK);
        // support posts every 5
        for (int z = z1 + 2; z <= z2; z += 5) {
            fill(x1, 56, z, x1, 64, z, SPRUCE_LOG_Y);
            fill(x2, 56, z, x2, 64, z, SPRUCE_LOG_Y);
        }
        // railing + lamps
        for (int z = z1 + 2; z <= z2 - 2; z++) {
            if (z % 3 == 0) { set(x1, 66, z, OAK_FENCE); set(x2, 66, z, OAK_FENCE); }
            if (z % 14 == 0) {
                set(x1, 66, z, OAK_FENCE); set(x1, 67, z, OAK_FENCE); set(x1, 68, z, LANTERN);
                set(x2, 66, z, OAK_FENCE); set(x2, 67, z, OAK_FENCE); set(x2, 68, z, LANTERN);
            }
        }
        // end bollards
        set(x1, 66, z2, SPRUCE_FENCE); set(x1, 67, z2, LANTERN);
        set(x2, 66, z2, SPRUCE_FENCE); set(x2, 67, z2, LANTERN);
    }

    private static void deckColumn(int x, int z, int plankId) {
        set(x, 65, z, plankId);
        for (int y = 66; y <= 78; y++) set(x, y, z, AIR);
    }

    // ------------------------------------------------------------------
    // Shipwright yard — hall, slipway, ship skeleton
    // ------------------------------------------------------------------

    private static void shipwrightYard() {
        // Yard ground: gravel work surface
        for (int x = 52; x <= 96; x++)
            for (int z = 58; z <= 90; z++)
                if (SpawnIslandTerrain.inCityFootprint(x, z)) set(x, 65, z, (x * 7 + z * 3) % 5 == 0 ? DIRT_PATH : GRAVEL);

        // Hall
        int x1 = 58, x2 = 88, z1 = 60, z2 = 82, gy = 65, wallH = 6;
        shell(x1, z1, x2, z2, gy, wallH, SPRUCE, SPRUCE, DARK_OAK_LOG_Y, COBBLE);
        // frame columns every 5 along the long walls
        for (int x = x1; x <= x2; x += 5)
            for (int y = gy + 1; y <= gy + wallH; y++) { set(x, y, z1, DARK_OAK_LOG_Y); set(x, y, z2, DARK_OAK_LOG_Y); }
        // windows
        windowsAlongX(x1, x2, z1, gy + 3, 5);
        windowsAlongX(x1, x2, z1, gy + 4, 5);
        windowsAlongZ(z1, z2, x1, gy + 3, 4);
        windowsAlongZ(z1, z2, x2, gy + 3, 4);
        // Two tall slip doors on the south wall (toward the water)
        for (int x = 63; x <= 67; x++) for (int y = gy + 1; y <= gy + 4; y++) set(x, y, z2, AIR);
        for (int x = 77; x <= 81; x++) for (int y = gy + 1; y <= gy + 4; y++) set(x, y, z2, AIR);
        fill(62, gy + 5, z2, 68, gy + 5, z2, DARK_OAK_LOG_X);
        fill(76, gy + 5, z2, 82, gy + 5, z2, DARK_OAK_LOG_X);
        // roof
        gableRoofX(x1, x2, z1, z2, gy + wallH + 1, "dark_oak", DARK_OAK);
        gableEndsX(x1, z1 + 1, z2 - 1, gy + wallH + 1, SPRUCE);
        gableEndsX(x2, z1 + 1, z2 - 1, gy + wallH + 1, SPRUCE);

        // Interior — drafting desk. The shipwright table itself sits out on the
        // grand pier (see piers()) so purchased ships spawn over open water.
        set(72, gy + 1, z2 - 3, LECTERN_E);
        // work row on the north wall
        set(x1 + 2, gy + 1, z1 + 1, BLAST_FURNACE_S);
        set(x1 + 3, gy + 1, z1 + 1, ANVIL_N);
        set(x1 + 4, gy + 1, z1 + 1, SMITHING);
        set(x1 + 5, gy + 1, z1 + 1, GRINDSTONE);
        set(x1 + 6, gy + 1, z1 + 1, STONECUTTER_S);
        set(x1 + 8, gy + 1, z1 + 1, CRAFTING);
        set(x1 + 9, gy + 1, z1 + 1, CARTOGRAPHY);
        // timber stores + loot
        fill(x2 - 2, gy + 1, z1 + 2, x2 - 1, gy + 1, z1 + 6, OAK_LOG_Z);
        fill(x2 - 2, gy + 2, z1 + 3, x2 - 1, gy + 2, z1 + 5, OAK_LOG_Z);
        set(x2 - 2, gy + 1, z1 + 9, BARREL_UP);
        set(x2 - 2, gy + 2, z1 + 9, BARREL_UP);
        set(x2 - 2, gy + 1, z1 + 10, BARREL_UP);
        lootChest(x2 - 2, gy + 1, z2 - 6, "west", "minecraft:chests/shipwreck_supply");
        // hanging lanterns from the ridge
        for (int x = x1 + 6; x <= x2 - 6; x += 8) {
            set(x, gy + wallH + 2, 71, CHAIN);
            set(x, gy + wallH + 1, 71, LANTERN_HANGING);
        }
        ceilingLanterns(x1, z1, x2, z2, gy + 5);
        // map table
        set(x1 + 3, gy + 1, z2 - 4, MAP_BLOCK);

        // Slipway: ramp descending into the harbor
        for (int z = 90; z <= 116; z++) {
            int y = 65 - (z - 90) / 4;
            for (int x = 64; x <= 80; x++) {
                set(x, y, z, (x == 64 || x == 80) ? SPRUCE_LOG_Z : SPRUCE);
                for (int cy = y + 1; cy <= 80; cy++) set(x, cy, z, AIR);
            }
            // support posts
            if (z % 4 == 0) {
                fill(64, 56, z, 64, y - 1, z, SPRUCE_LOG_Y);
                fill(80, 56, z, 80, y - 1, z, SPRUCE_LOG_Y);
            }
        }

        // Ship under construction: keel + ribs on the slipway
        for (int z = 94; z <= 112; z++) {
            int y = 65 - (z - 90) / 4 + 1;
            set(72, y, z, DARK_OAK_LOG_Z);   // keel
        }
        for (int z = 96; z <= 110; z += 4) {
            int y = 65 - (z - 90) / 4 + 1;
            // rib pair rising from the keel
            set(69, y + 2, z, DARK_OAK_LOG_Y); set(75, y + 2, z, DARK_OAK_LOG_Y);
            set(69, y + 1, z, DARK_OAK_LOG_Y); set(75, y + 1, z, DARK_OAK_LOG_Y);
            set(70, y + 3, z, stairs("dark_oak", "east")); set(74, y + 3, z, stairs("dark_oak", "west"));
            set(71, y + 3, z, DARK_OAK); set(73, y + 3, z, DARK_OAK);
            set(70, y, z, stairsTop("dark_oak", "west")); set(74, y, z, stairsTop("dark_oak", "east"));
            set(71, y, z, DARK_OAK_LOG_X); set(73, y, z, DARK_OAK_LOG_X);
        }
        // bow post
        set(72, 63, 114, DARK_OAK_LOG_Y);
        set(72, 64, 114, DARK_OAK_LOG_Y);
        // scaffolding alongside
        for (int z = 96; z <= 108; z += 6) {
            int y = 65 - (z - 90) / 4;
            set(66, y + 1, z, OAK_FENCE); set(66, y + 2, z, OAK_FENCE);
            set(66, y + 3, z, slab("oak"));
            set(78, y + 1, z, OAK_FENCE); set(78, y + 2, z, OAK_FENCE);
            set(78, y + 3, z, slab("oak"));
        }

        // Yard clutter: log piles + sawhorses
        fill(53, 66, 62, 54, 66, 70, OAK_LOG_Z);
        fill(53, 67, 64, 54, 67, 68, OAK_LOG_Z);
        set(92, 66, 62, SPRUCE_FENCE); set(92, 67, 62, slab("spruce"));
        set(93, 66, 62, SPRUCE_FENCE);
        set(91, 66, 76, BARREL_UP); set(91, 66, 77, BARREL_UP); set(91, 67, 76, BARREL_UP);
        // yard fence along the east edge
        for (int z = 58; z <= 88; z += 1) if (z % 2 == 0) set(96, 66, z, SPRUCE_FENCE);
    }

    // ------------------------------------------------------------------
    // Market square
    // ------------------------------------------------------------------

    private static void marketSquare() {
        int gy = 67;
        // Paving: stone bricks with sandstone diagonals and a cobble border
        for (int x = -38; x <= 38; x++) {
            for (int z = 16; z <= 44; z++) {
                if (!SpawnIslandTerrain.inCityFootprint(x, z)) continue;
                int mat = STONE_BRICKS;
                if (x == -38 || x == 38 || z == 16 || z == 44) mat = COBBLE;
                else if (((x + z) & 7) == 0) mat = SMOOTH_SANDSTONE;
                else if (((x - z) & 7) == 0) mat = POLISHED_ANDESITE;
                set(x, gy, z, mat);
            }
        }
        // Fountain at (0, 30)
        for (int x = -3; x <= 3; x++)
            for (int z = 27; z <= 33; z++) {
                boolean rim = (Math.abs(x) == 3 || z == 27 || z == 33);
                set(x, gy, z, POLISHED_ANDESITE);
                if (rim) set(x, gy + 1, z, CHISELED_STONE_BRICKS);
                else set(x, gy + 1, z, WATER);
            }
        set(0, gy + 1, 30, STONE_BRICK_WALL);
        set(0, gy + 2, 30, STONE_BRICK_WALL);
        set(0, gy + 3, 30, SEA_LANTERN);

        // Market stalls — striped canopies, varied colors
        String[][] canopies = {
            {"red", "white"}, {"cyan", "white"}, {"yellow", "white"}, {"lime", "white"},
            {"purple", "white"}, {"orange", "white"}, {"blue", "white"}, {"magenta", "white"}
        };
        int[][] stallSpots = { // {x1, z1} of each 7x4 stall
            {-34, 18}, {-22, 18}, {12, 18}, {24, 18},
            {-34, 39}, {-20, 39}, {12, 39}, {26, 39}
        };
        int[] goods = {PUMPKIN, MELON, HAY, BARREL_UP, GROG_BARREL, HAY, MELON, BARREL_UP};
        for (int i = 0; i < stallSpots.length; i++) {
            stall(stallSpots[i][0], gy, stallSpots[i][1], canopies[i][0], canopies[i][1], goods[i]);
        }

        // Well west of the fountain
        int wx = -30, wz = 30;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                set(wx + dx, gy + 1, wz + dz, (dx == 0 && dz == 0) ? WATER : COBBLE_WALL);
        set(wx - 1, gy + 2, wz - 1, OAK_FENCE); set(wx + 1, gy + 2, wz - 1, OAK_FENCE);
        set(wx - 1, gy + 2, wz + 1, OAK_FENCE); set(wx + 1, gy + 2, wz + 1, OAK_FENCE);
        fill(wx - 1, gy + 3, wz - 1, wx + 1, gy + 3, wz + 1, slab("oak"));
        set(wx, gy + 3, wz, CHAIN);

        // Notice board on the east side
        set(30, gy + 1, 28, DARK_OAK_LOG_Y); set(30, gy + 1, 32, DARK_OAK_LOG_Y);
        fill(30, gy + 2, 29, 30, gy + 3, 31, DARK_OAK);
        set(29, gy + 1, 30, LECTERN_W);
    }

    private static void stall(int x1, int gy, int z1, String colorA, String colorB, int goodsId) {
        int x2 = x1 + 6, z2 = z1 + 3;
        for (int dy = 1; dy <= 3; dy++) {
            set(x1, gy + dy, z1, SPRUCE_FENCE); set(x2, gy + dy, z1, SPRUCE_FENCE);
            set(x1, gy + dy, z2, SPRUCE_FENCE); set(x2, gy + dy, z2, SPRUCE_FENCE);
        }
        int a = wool(colorA), b = wool(colorB);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                set(x, gy + 4, z, ((x - x1) % 2 == 0) ? a : b);
        // counter with goods
        int mid = (x1 + x2) / 2;
        set(mid - 1, gy + 1, z1 + 1, goodsId);
        set(mid, gy + 1, z1 + 1, BARREL_UP);
        set(mid + 1, gy + 1, z1 + 1, goodsId);
        set(mid, gy + 1, z2 - 1, chest("south"));
    }

    // ------------------------------------------------------------------
    // Big buildings — mid terrace
    // ------------------------------------------------------------------

    /** "The Grog Barrel" — two-story tavern east of the market square. */
    private static void tavern() {
        int x1 = 48, x2 = 76, z1 = 16, z2 = 44, gy = 67, wallH = 5;
        shell(x1, z1, x2, z2, gy, wallH, DARK_OAK, SPRUCE, DARK_OAK_LOG_Y, COBBLE);
        for (int x = x1; x <= x2; x += 4)
            for (int y = gy + 1; y <= gy + wallH; y++) { set(x, y, z1, DARK_OAK_LOG_Y); set(x, y, z2, DARK_OAK_LOG_Y); }
        windowsAlongX(x1, x2, z1, gy + 3, 4);
        windowsAlongX(x1, x2, z2, gy + 3, 4);
        windowsAlongZ(z1, z2, x2, gy + 3, 4);

        // Second floor
        fill(x1 + 1, gy + 5, z1 + 1, x2 - 1, gy + 5, z2 - 1, SPRUCE);
        for (int y = gy + 6; y <= gy + 9; y++) {
            for (int x = x1; x <= x2; x++) { set(x, y, z1, SPRUCE); set(x, y, z2, SPRUCE); }
            for (int z = z1 + 1; z <= z2 - 1; z++) { set(x1, y, z, SPRUCE); set(x2, y, z, SPRUCE); }
        }
        fill(x1 + 1, gy + 6, z1 + 1, x2 - 1, gy + 9, z2 - 1, AIR);
        windowsAlongX(x1, x2, z1, gy + 7, 4);
        windowsAlongX(x1, x2, z2, gy + 7, 4);
        gableRoofX(x1, x2, z1, z2, gy + 10, "dark_oak", DARK_OAK);
        gableEndsX(x1, z1 + 1, z2 - 1, gy + 10, SPRUCE);
        gableEndsX(x2, z1 + 1, z2 - 1, gy + 10, SPRUCE);

        // Door faces the market square (west wall), with a hanging grog-barrel sign
        doorway(x1, gy, 29); doorway(x1, gy, 30);
        set(x1, gy + 3, 29, AIR); set(x1, gy + 3, 30, AIR);
        set(x1 - 1, gy + 4, 30, CHAIN);
        set(x1 - 1, gy + 3, 30, GROG_BARREL);
        lamppost(x1 - 2, gy, 26); lamppost(x1 - 2, gy, 34);

        // Ground floor: bar along the east wall
        for (int z = 22; z <= 36; z++) {
            set(x2 - 4, gy + 1, z, DARK_OAK_FENCE);
            set(x2 - 4, gy + 2, z, slabTop("dark_oak"));
        }
        for (int z = 22; z <= 36; z += 3) { set(x2 - 1, gy + 1, z, GROG_BARREL); set(x2 - 1, gy + 2, z, GROG_BARREL); }
        set(x2 - 1, gy + 1, 20, BREWING);
        set(x2 - 2, gy + 1, 20, CAULDRON_WATER);
        // hearth on the north wall
        fill(58, gy + 1, z1 + 1, 62, gy + 3, z1 + 1, COBBLE);
        set(60, gy + 1, z1 + 2, CAMPFIRE);
        fill(60, gy + 4, z1 + 1, 60, gy + 11, z1 + 1, COBBLE); // chimney
        // tables + seating
        int[][] tables = {{52, 24}, {52, 32}, {56, 28}, {62, 24}, {62, 34}, {66, 28}};
        for (int[] t : tables) {
            set(t[0], gy + 1, t[1], DARK_OAK_FENCE);
            set(t[0], gy + 2, t[1], id("minecraft:spruce_pressure_plate"));
            set(t[0] - 1, gy + 1, t[1], stairs("spruce", "east"));
            set(t[0] + 1, gy + 1, t[1], stairs("spruce", "west"));
        }
        set(x1 + 2, gy + 1, z2 - 2, JUKEBOX);
        set(x1 + 3, gy + 1, z2 - 2, NOTE_BLOCK);
        lootChest(x1 + 2, gy + 1, z1 + 2, "south", "minecraft:chests/spawn_bonus_chest");
        // stairs up along the south wall
        for (int i = 0; i < 5; i++) set(x1 + 4 + i, gy + 1 + i, z2 - 2, stairs("spruce", "east"));
        fill(x1 + 4, gy + 5, z2 - 2, x1 + 8, gy + 5, z2 - 2, AIR);
        // second floor: guest beds
        String[] bedColors = {"red", "blue", "green", "cyan", "purple", "white"};
        for (int i = 0; i < 6; i++) {
            int bx = x1 + 4 + i * 4;
            set(bx, gy + 6, z1 + 2, bed(bedColors[i], "south", true));
            set(bx, gy + 6, z1 + 3, bed(bedColors[i], "south", false));
            set(bx + 1, gy + 6, z1 + 2, BARREL_UP);
        }
        // hanging lanterns — both floors
        ceilingLanterns(x1, z1, x2, z2, gy + 4);
        ceilingLanterns(x1, z1, x2, z2, gy + 9);
    }

    /** "The Salty Siren" inn, west of the market square. */
    private static void inn() {
        int x1 = -76, x2 = -48, z1 = 14, z2 = 42, gy = 67, wallH = 5;
        shell(x1, z1, x2, z2, gy, wallH, OAK, BIRCH, DARK_OAK_LOG_Y, COBBLE);
        for (int x = x1; x <= x2; x += 4)
            for (int y = gy + 1; y <= gy + wallH; y++) { set(x, y, z1, DARK_OAK_LOG_Y); set(x, y, z2, DARK_OAK_LOG_Y); }
        windowsAlongX(x1, x2, z1, gy + 3, 4);
        windowsAlongX(x1, x2, z2, gy + 3, 4);
        windowsAlongZ(z1, z2, x1, gy + 3, 4);
        // second floor
        fill(x1 + 1, gy + 5, z1 + 1, x2 - 1, gy + 5, z2 - 1, OAK);
        for (int y = gy + 6; y <= gy + 9; y++) {
            for (int x = x1; x <= x2; x++) { set(x, y, z1, BIRCH); set(x, y, z2, BIRCH); }
            for (int z = z1 + 1; z <= z2 - 1; z++) { set(x1, y, z, BIRCH); set(x2, y, z, BIRCH); }
        }
        fill(x1 + 1, gy + 6, z1 + 1, x2 - 1, gy + 9, z2 - 1, AIR);
        windowsAlongX(x1, x2, z1, gy + 7, 4);
        windowsAlongX(x1, x2, z2, gy + 7, 4);
        gableRoofX(x1, x2, z1, z2, gy + 10, "spruce", SPRUCE);
        gableEndsX(x1, z1 + 1, z2 - 1, gy + 10, BIRCH);
        gableEndsX(x2, z1 + 1, z2 - 1, gy + 10, BIRCH);
        // door faces the square (east wall)
        doorway(x2, gy, 27); doorway(x2, gy, 28);
        set(x2, gy + 3, 27, AIR); set(x2, gy + 3, 28, AIR);
        // reception
        set(x2 - 3, gy + 1, 24, LECTERN_E);
        set(x2 - 3, gy + 1, 22, BARREL_UP);
        set(x2 - 2, gy + 1, 33, BOOKSHELF); set(x2 - 2, gy + 2, 33, BOOKSHELF);
        // stairs up
        for (int i = 0; i < 5; i++) set(x1 + 3 + i, gy + 1 + i, z1 + 2, stairs("oak", "east"));
        fill(x1 + 3, gy + 5, z1 + 2, x1 + 7, gy + 5, z1 + 2, AIR);
        // guest rooms: beds along both walls
        String[] colors = {"white", "light_blue", "red", "lime", "purple", "orange", "cyan", "yellow"};
        for (int i = 0; i < 4; i++) {
            int bx = x1 + 5 + i * 6;
            set(bx, gy + 6, z1 + 2, bed(colors[i], "south", true));
            set(bx, gy + 6, z1 + 3, bed(colors[i], "south", false));
            set(bx, gy + 6, z2 - 2, bed(colors[i + 4], "north", true));
            set(bx, gy + 6, z2 - 3, bed(colors[i + 4], "north", false));
        }
        ceilingLanterns(x1, z1, x2, z2, gy + 4);
        ceilingLanterns(x1, z1, x2, z2, gy + 9);
    }

    private static void chandlery() {
        int x1 = -112, x2 = -86, z1 = 14, z2 = 36, gy = 67, wallH = 5;
        shell(x1, z1, x2, z2, gy, wallH, SPRUCE, PIRATE_PLANKS, SPRUCE_LOG_Y, COBBLE);
        windowsAlongX(x1, x2, z2, gy + 3, 4);
        windowsAlongZ(z1, z2, x2, gy + 3, 5);
        gableRoofX(x1, x2, z1, z2, gy + wallH + 1, "spruce", SPRUCE);
        gableEndsX(x1, z1 + 1, z2 - 1, gy + wallH + 1, PIRATE_PLANKS);
        gableEndsX(x2, z1 + 1, z2 - 1, gy + wallH + 1, PIRATE_PLANKS);
        doorway(x2, gy, 25);
        placeDoor(x2, gy, 25, "spruce", "east");
        // rope + supply interior
        for (int z = z1 + 3; z <= z2 - 3; z += 4) {
            set(x1 + 2, gy + 3, z, CHAIN);
            set(x1 + 2, gy + 2, z, CHAIN);
        }
        fill(x1 + 1, gy + 1, z1 + 2, x1 + 1, gy + 2, z1 + 6, BARREL_N);
        set(x1 + 4, gy + 1, z1 + 2, CARTOGRAPHY);
        set(x1 + 6, gy + 1, z1 + 2, MAP_BLOCK);
        set(x1 + 6, gy + 2, z1 + 2, MAP_BLOCK);
        set(x1 + 8, gy + 1, z1 + 2, chest("south"));
        set(x1 + 4, gy + 1, z2 - 3, COMPOSTER);
        ceilingLanterns(x1, z1, x2, z2, gy + 4);
    }

    private static void bakery() {
        int x1 = 86, x2 = 112, z1 = 16, z2 = 38, gy = 67, wallH = 5;
        shell(x1, z1, x2, z2, gy, wallH, OAK, OAK, OAK_LOG_Y, COBBLE);
        windowsAlongX(x1, x2, z1, gy + 3, 4);
        windowsAlongZ(z1, z2, x1, gy + 3, 5);
        gableRoofX(x1, x2, z1, z2, gy + wallH + 1, "oak", OAK);
        gableEndsX(x1, z1 + 1, z2 - 1, gy + wallH + 1, OAK);
        gableEndsX(x2, z1 + 1, z2 - 1, gy + wallH + 1, OAK);
        doorway(x1, gy, 26);
        placeDoor(x1, gy, 26, "oak", "west");
        // ovens + chimney
        set(x2 - 2, gy + 1, z1 + 2, SMOKER_S);
        set(x2 - 3, gy + 1, z1 + 2, SMOKER_S);
        set(x2 - 4, gy + 1, z1 + 2, FURNACE_S);
        fill(x2 - 3, gy + 2, z1 + 1, x2 - 3, gy + 10, z1 + 1, COBBLE); // chimney
        set(x2 - 2, gy + 1, z2 - 3, HAY); set(x2 - 3, gy + 1, z2 - 3, HAY); set(x2 - 2, gy + 2, z2 - 3, HAY);
        set(x1 + 3, gy + 1, z1 + 3, CRAFTING);
        set(x1 + 3, gy + 1, z1 + 5, id("minecraft:cake"));
        set(x1 + 3, gy + 1, z1 + 4, BARREL_UP);
        ceilingLanterns(x1, z1, x2, z2, gy + 4);
    }

    // ------------------------------------------------------------------
    // Harbor-terrace buildings
    // ------------------------------------------------------------------

    private static void harbormaster() {
        int x1 = -28, x2 = -12, z1 = 60, z2 = 76, gy = 65, wallH = 4;
        shell(x1, z1, x2, z2, gy, wallH, DARK_OAK, SPRUCE, DARK_OAK_LOG_Y, STONE_BRICKS);
        windowsAlongX(x1, x2, z1, gy + 3, 3);
        // 2nd floor with harbor-facing balcony
        fill(x1 + 1, gy + 4, z1 + 1, x2 - 1, gy + 4, z2 - 1, DARK_OAK);
        for (int y = gy + 5; y <= gy + 8; y++) {
            for (int x = x1; x <= x2; x++) { set(x, y, z1, SPRUCE); set(x, y, z2, SPRUCE); }
            for (int z = z1 + 1; z <= z2 - 1; z++) { set(x1, y, z, SPRUCE); set(x2, y, z, SPRUCE); }
        }
        fill(x1 + 1, gy + 5, z1 + 1, x2 - 1, gy + 8, z2 - 1, AIR);
        // balcony over the quay
        fill(x1 + 3, gy + 4, z2 + 1, x2 - 3, gy + 4, z2 + 3, DARK_OAK);
        for (int x = x1 + 3; x <= x2 - 3; x++) set(x, gy + 5, z2 + 3, OAK_FENCE);
        set(x1 + 3, gy + 5, z2 + 1, OAK_FENCE); set(x2 - 3, gy + 5, z2 + 1, OAK_FENCE);
        // balcony door
        set(-20, gy + 5, z2, AIR); set(-20, gy + 6, z2, AIR);
        windowsAlongX(x1, x2, z2, gy + 6, 3);
        gableRoofX(x1, x2, z1, z2, gy + 9, "dark_oak", DARK_OAK);
        gableEndsX(x1, z1 + 1, z2 - 1, gy + 9, SPRUCE);
        gableEndsX(x2, z1 + 1, z2 - 1, gy + 9, SPRUCE);
        // flag on the roof
        set(-20, gy + 12, 68, SPRUCE_FENCE); set(-20, gy + 13, 68, SPRUCE_FENCE);
        set(-20, gy + 14, 68, BLACK_WOOL); set(-19, gy + 14, 68, BLACK_WOOL);
        // door + desk
        doorway(x1, gy, 68);
        set(x1 + 2, gy + 1, 68, LECTERN_E);
        set(x1 + 2, gy + 1, z1 + 2, chest("east"));
        set(x1 + 2, gy + 1, z1 + 3, BARREL_UP);
        // interior stairs
        for (int i = 0; i < 4; i++) set(x2 - 2 - i, gy + 1 + i, z1 + 2, stairs("spruce", "west"));
        fill(x2 - 2, gy + 4, z1 + 2, x2 - 5, gy + 4, z1 + 2, AIR);
        // lighting — both floors
        ceilingLanterns(x1, z1, x2, z2, gy + 3);
        ceilingLanterns(x1, z1, x2, z2, gy + 8);
    }

    private static void warehouses() {
        warehouse(-70, -38, 60, 84, "dark_oak", DARK_PIRATE_PLANKS, true);
        warehouse(-128, -98, 58, 80, "spruce", SPRUCE, false);
    }

    private static void warehouse(int x1, int x2, int z1, int z2, String roofMat, int wallId, boolean loot) {
        int gy = 65, wallH = 6;
        shell(x1, z1, x2, z2, gy, wallH, SPRUCE, wallId, SPRUCE_LOG_Y, COBBLE);
        for (int x = x1; x <= x2; x += 6)
            for (int y = gy + 1; y <= gy + wallH; y++) { set(x, y, z1, SPRUCE_LOG_Y); set(x, y, z2, SPRUCE_LOG_Y); }
        // big loading door facing the quay
        int mid = (x1 + x2) / 2;
        for (int x = mid - 2; x <= mid + 2; x++)
            for (int y = gy + 1; y <= gy + 4; y++) set(x, y, z2, AIR);
        fill(mid - 3, gy + 5, z2, mid + 3, gy + 5, z2, DARK_OAK_LOG_X);
        windowsAlongX(x1, x2, z1, gy + 4, 5);
        gableRoofX(x1, x2, z1, z2, gy + wallH + 1, roofMat, SPRUCE);
        gableEndsX(x1, z1 + 1, z2 - 1, gy + wallH + 1, wallId);
        gableEndsX(x2, z1 + 1, z2 - 1, gy + wallH + 1, wallId);
        // cargo inside
        Random rng = new Random(x1 * 31L + z1);
        for (int i = 0; i < 16; i++) {
            int cx = x1 + 2 + rng.nextInt(x2 - x1 - 4);
            int cz = z1 + 2 + rng.nextInt(z2 - z1 - 6);
            barrelCluster(cx, gy, cz, rng);
        }
        fill(x1 + 2, gy + 1, z1 + 2, x1 + 3, gy + 2, z1 + 4, HAY);
        if (loot) lootChest(x1 + 2, gy + 1, z2 - 3, "east", "minecraft:chests/shipwreck_supply");
        ceilingLanterns(x1, z1, x2, z2, gy + 5);
    }

    /** Open-sided fish market pavilion on the quay. */
    private static void fishMarket() {
        int x1 = 10, x2 = 34, z1 = 60, z2 = 74, gy = 65;
        // posts
        for (int x = x1; x <= x2; x += 6)
            for (int z : new int[]{z1, z2})
                for (int y = gy + 1; y <= gy + 4; y++) set(x, y, z, SPRUCE_LOG_Y);
        gableRoofX(x1, x2, z1, z2, gy + 5, "spruce", SPRUCE);
        // counters
        for (int x = x1 + 2; x <= x2 - 2; x++) {
            set(x, gy + 1, z1 + 3, slabTop("spruce"));
            set(x, gy + 1, z2 - 3, slabTop("spruce"));
        }
        set(x1 + 3, gy + 1, 67, BARREL_UP);
        set(x1 + 9, gy + 1, 67, CAMPFIRE);   // fish smoker
        set(x1 + 15, gy + 1, 67, CAMPFIRE);
        set(x1 + 21, gy + 1, 67, BARREL_UP);
        ceilingLanterns(x1, z1, x2, z2, gy + 4);
    }

    /** Small dockside office at the east end of the quay. */
    private static void dockOffice() {
        int x1 = 102, x2 = 116, z1 = 58, z2 = 72, gy = 65, wallH = 4;
        shell(x1, z1, x2, z2, gy, wallH, OAK, PIRATE_PLANKS, SPRUCE_LOG_Y, COBBLE);
        windowsAlongZ(z1, z2, x1, gy + 3, 4);
        windowsAlongX(x1, x2, z2, gy + 3, 4);
        gableRoofZ(z1, z2, x1, x2, gy + wallH + 1, "spruce", SPRUCE);
        gableEndsZ(z1, x1 + 1, x2 - 1, gy + wallH + 1, PIRATE_PLANKS);
        gableEndsZ(z2, x1 + 1, x2 - 1, gy + wallH + 1, PIRATE_PLANKS);
        doorway(x1, gy, 65);
        placeDoor(x1, gy, 65, "spruce", "west");
        set(x1 + 3, gy + 1, z1 + 2, LECTERN_N);
        set(x2 - 2, gy + 1, z1 + 2, BARREL_UP);
        set(x2 - 2, gy + 1, z2 - 2, chest("west"));
        ceilingLanterns(x1, z1, x2, z2, gy + 3);
    }

    // ------------------------------------------------------------------
    // Upper terrace
    // ------------------------------------------------------------------

    private static void chapel() {
        int x1 = -36, x2 = -14, z1 = -80, z2 = -56, gy = 70, wallH = 6;
        shell(x1, z1, x2, z2, gy, wallH, DARK_OAK, STONE_BRICKS, CHISELED_STONE_BRICKS, STONE_BRICKS);
        // tall stained windows
        for (int z = z1 + 4; z <= z2 - 4; z += 4) {
            set(x1, gy + 3, z, id("minecraft:light_blue_stained_glass_pane"));
            set(x1, gy + 4, z, id("minecraft:white_stained_glass_pane"));
            set(x2, gy + 3, z, id("minecraft:light_blue_stained_glass_pane"));
            set(x2, gy + 4, z, id("minecraft:white_stained_glass_pane"));
        }
        gableRoofX(x1, x2, z1, z2, gy + wallH + 1, "dark_oak", DARK_OAK);
        gableEndsX(x1, z1 + 1, z2 - 1, gy + wallH + 1, STONE_BRICKS);
        gableEndsX(x2, z1 + 1, z2 - 1, gy + wallH + 1, STONE_BRICKS);
        // door on the south end toward the street
        doorway(-25, gy, z2);
        set(-26, gy + 1, z2, AIR); set(-26, gy + 2, z2, AIR);
        set(-25, gy + 3, z2, AIR); set(-26, gy + 3, z2, AIR);
        // bell tower on the south-west corner
        int tx1 = -36, tx2 = -30, tz1 = z2 - 2, tz2 = z2 + 4;
        for (int y = gy + 1; y <= gy + 12; y++) {
            for (int x = tx1; x <= tx2; x++) { set(x, y, tz1, STONE_BRICKS); set(x, y, tz2, STONE_BRICKS); }
            for (int z = tz1; z <= tz2; z++) { set(tx1, y, z, STONE_BRICKS); set(tx2, y, z, STONE_BRICKS); }
        }
        fill(tx1 + 1, gy + 1, tz1 + 1, tx2 - 1, gy + 12, tz2 - 1, AIR);
        // belfry openings + bell
        for (int y = gy + 10; y <= gy + 11; y++) {
            set(tx1 + 3, y, tz1, AIR); set(tx1 + 3, y, tz2, AIR);
            set(tx1, y, tz1 + 3, AIR); set(tx2, y, tz1 + 3, AIR);
        }
        set(tx1 + 3, gy + 11, tz1 + 3, BELL);
        fill(tx1, gy + 13, tz1, tx2, gy + 13, tz2, slab("stone_brick"));
        set(tx1 + 3, gy + 14, tz1 + 3, LANTERN);
        // pews + altar
        for (int z = z1 + 6; z <= z2 - 8; z += 3) {
            for (int x = x1 + 3; x <= -27; x++) set(x, gy + 1, z, stairs("spruce", "south"));
            for (int x = -23; x <= x2 - 3; x++) set(x, gy + 1, z, stairs("spruce", "south"));
        }
        fill(-27, gy + 1, z1 + 2, -23, gy + 1, z1 + 2, POLISHED_DIORITE);
        set(-25, gy + 2, z1 + 2, LECTERN_N);
        set(-27, gy + 2, z1 + 2, id("minecraft:candle[candles=3,lit=true]"));
        set(-23, gy + 2, z1 + 2, id("minecraft:candle[candles=3,lit=true]"));
        ceilingLanterns(x1, z1, x2, z2, gy + 5);
    }

    private static void manor() {
        int x1 = 14, x2 = 46, z1 = -82, z2 = -54, gy = 70, wallH = 5;
        // timber-frame: birch infill, dark oak frame
        shell(x1, z1, x2, z2, gy, wallH, DARK_OAK, BIRCH, DARK_OAK_LOG_Y, STONE_BRICKS);
        for (int x = x1; x <= x2; x += 4)
            for (int y = gy + 1; y <= gy + wallH; y++) { set(x, y, z1, DARK_OAK_LOG_Y); set(x, y, z2, DARK_OAK_LOG_Y); }
        for (int z = z1; z <= z2; z += 4)
            for (int y = gy + 1; y <= gy + wallH; y++) { set(x1, y, z, DARK_OAK_LOG_Y); set(x2, y, z, DARK_OAK_LOG_Y); }
        windowsAlongX(x1, x2, z1, gy + 3, 4);
        windowsAlongX(x1, x2, z2, gy + 3, 4);
        windowsAlongZ(z1, z2, x1, gy + 3, 4);
        windowsAlongZ(z1, z2, x2, gy + 3, 4);
        // second story
        fill(x1 + 1, gy + 5, z1 + 1, x2 - 1, gy + 5, z2 - 1, DARK_OAK);
        for (int y = gy + 6; y <= gy + 9; y++) {
            for (int x = x1; x <= x2; x++) { set(x, y, z1, BIRCH); set(x, y, z2, BIRCH); }
            for (int z = z1 + 1; z <= z2 - 1; z++) { set(x1, y, z, BIRCH); set(x2, y, z, BIRCH); }
        }
        fill(x1 + 1, gy + 6, z1 + 1, x2 - 1, gy + 9, z2 - 1, AIR);
        for (int x = x1; x <= x2; x += 4)
            for (int y = gy + 6; y <= gy + 9; y++) { set(x, y, z1, DARK_OAK_LOG_Y); set(x, y, z2, DARK_OAK_LOG_Y); }
        windowsAlongX(x1, x2, z2, gy + 7, 4);
        gableRoofX(x1, x2, z1, z2, gy + 10, "dark_oak", DARK_OAK);
        gableEndsX(x1, z1 + 1, z2 - 1, gy + 10, BIRCH);
        gableEndsX(x2, z1 + 1, z2 - 1, gy + 10, BIRCH);
        // grand door facing south toward the city
        doorway(29, gy, z2); doorway(30, gy, z2);
        set(29, gy + 3, z2, AIR); set(30, gy + 3, z2, AIR);
        set(28, gy + 3, z2, LANTERN); set(31, gy + 3, z2, LANTERN);
        // interior: hall, table, bookshelves, loot upstairs
        for (int x = 24; x <= 34; x++) set(x, gy + 1, -68, DARK_OAK_FENCE);
        for (int x = 24; x <= 34; x++) set(x, gy + 2, -68, id("minecraft:spruce_pressure_plate"));
        fill(x1 + 1, gy + 1, z1 + 1, x1 + 1, gy + 3, z1 + 6, BOOKSHELF);
        set(x1 + 4, gy + 1, z1 + 2, BREWING);
        for (int i = 0; i < 5; i++) set(x2 - 2 - i, gy + 1 + i, z1 + 2, stairs("dark_oak", "west"));
        fill(x2 - 2, gy + 5, z1 + 2, x2 - 6, gy + 5, z1 + 2, AIR);
        set(x1 + 3, gy + 6, z1 + 2, bed("red", "south", true));
        set(x1 + 3, gy + 6, z1 + 3, bed("red", "south", false));
        lootChest(x1 + 3, gy + 6, z2 - 3, "north", "minecraft:chests/shipwreck_treasure");
        ceilingLanterns(x1, z1, x2, z2, gy + 4);
        ceilingLanterns(x1, z1, x2, z2, gy + 9);
        // garden hedges out front
        for (int x = 18; x <= 42; x++) {
            if (x >= 27 && x <= 32) continue;
            set(x, gy + 1, z2 + 3, OAK_LEAVES);
        }
        set(20, gy + 1, z2 + 2, POTTED_TULIP);
        set(40, gy + 1, z2 + 2, POTTED_DANDELION);
    }

    /**
     * Watch house beside the main avenue, door north onto the upper street. (Until 2026-09-28 it sat
     * at z-84..-72, inside the chapel's altar end - the two shells overlapped; main() now checks.)
     */
    private static void guardhouse() {
        int x1 = -28, x2 = -10, z1 = -38, z2 = -26, gy = 70, wallH = 4;
        shell(x1, z1, x2, z2, gy, wallH, SPRUCE, COBBLE, SPRUCE_LOG_Y, COBBLE);
        windowsAlongX(x1, x2, z2, gy + 3, 4);
        gableRoofX(x1, x2, z1, z2, gy + wallH + 1, "spruce", SPRUCE);
        gableEndsX(x1, z1 + 1, z2 - 1, gy + wallH + 1, COBBLE);
        gableEndsX(x2, z1 + 1, z2 - 1, gy + wallH + 1, COBBLE);
        doorway(-19, gy, z1);
        placeDoor(-19, gy, z1, "spruce", "north");
        for (int z = -41; z < z1; z++) for (int x = -20; x <= -18; x++) set(x, gy, z, x == -19 ? POLISHED_ANDESITE : COBBLE);
        set(-21, gy + 1, z1 - 1, LANTERN); set(-17, gy + 1, z1 - 1, LANTERN);
        // bunks + rack
        set(x1 + 2, gy + 1, z1 + 2, bed("gray", "east", false));
        set(x1 + 3, gy + 1, z1 + 2, bed("gray", "east", true));
        set(x1 + 2, gy + 1, z1 + 4, bed("gray", "east", false));
        set(x1 + 3, gy + 1, z1 + 4, bed("gray", "east", true));
        set(x2 - 2, gy + 1, z1 + 2, chest("west"));
        set(x2 - 2, gy + 1, z1 + 4, BARREL_UP);
        ceilingLanterns(x1, z1, x2, z2, gy + 3);
    }

    private static void smithy() {
        int x1 = 54, x2 = 76, z1 = -44, z2 = -24, gy = 70, wallH = 5;
        shell(x1, z1, x2, z2, gy, wallH, COBBLE, SPRUCE, DARK_OAK_LOG_Y, COBBLE);
        windowsAlongX(x1, x2, z2, gy + 3, 4);
        gableRoofX(x1, x2, z1, z2, gy + wallH + 1, "dark_oak", DARK_OAK);
        gableEndsX(x1, z1 + 1, z2 - 1, gy + wallH + 1, SPRUCE);
        gableEndsX(x2, z1 + 1, z2 - 1, gy + wallH + 1, SPRUCE);
        doorway(65, gy, z2);
        // forge corner
        fill(x1 + 1, gy + 1, z1 + 1, x1 + 4, gy + 1, z1 + 3, COBBLE);
        set(x1 + 2, gy + 2, z1 + 2, CAMPFIRE);
        fill(x1 + 2, gy + 3, z1 + 1, x1 + 2, gy + 10, z1 + 1, COBBLE); // chimney
        set(x1 + 6, gy + 1, z1 + 2, BLAST_FURNACE_S);
        set(x1 + 7, gy + 1, z1 + 2, ANVIL_N);
        set(x1 + 8, gy + 1, z1 + 2, SMITHING);
        set(x1 + 9, gy + 1, z1 + 2, GRINDSTONE);
        set(x2 - 3, gy + 1, z1 + 2, CAULDRON_WATER);
        set(x2 - 2, gy + 1, z2 - 3, chest("west"));
        ceilingLanterns(x1, z1, x2, z2, gy + 4);
    }

    private static void park() {
        int gy = 70;
        // gravel loop path
        for (int x = -84; x <= -56; x++) {
            set(x, gy, -42, GRAVEL); set(x, gy, -20, GRAVEL);
        }
        for (int z = -42; z <= -20; z++) {
            set(-84, gy, z, GRAVEL); set(-56, gy, z, GRAVEL);
        }
        // pond
        for (int x = -76; x <= -66; x++)
            for (int z = -36; z <= -28; z++) {
                boolean rim = (x == -76 || x == -66 || z == -36 || z == -28);
                if (rim) set(x, gy, z, MOSSY_COBBLE);
                else { set(x, gy, z, WATER); set(x, gy - 1, z, DIRT); }
            }
        // palms + benches
        palm(-82, gy, -40, 6);
        palm(-58, gy, -40, 7);
        palm(-82, gy, -24, 7);
        palm(-58, gy, -22, 5);
        set(-70, gy + 1, -41, stairs("oak", "south"));
        set(-69, gy + 1, -41, stairs("oak", "south"));
        set(-70, gy + 1, -21, stairs("oak", "north"));
        set(-69, gy + 1, -21, stairs("oak", "north"));
        lamppost(-70, gy, -31);
    }

    private static void houseRows(Random rng) {
        // Mid-terrace row facing the market street (z -6..6, doors north onto z=8 street)
        int[][] midLots = {
            {-136, -124}, {-120, -108}, {-64, -52}, {-48, -36}, {-32, -20},
            {20, 32}, {36, 48}, {52, 64}, {108, 120}, {124, 136}
        };
        for (int i = 0; i < midLots.length; i++)
            house(midLots[i][0], -6, midLots[i][1], 6, 67, "north", i, rng);

        // Upper row A facing the upper street (doors north onto z=-42 street)
        int[][] rowA = {{-136, -124}, {-120, -108}, {-104, -92}, {80, 92}, {96, 108}, {120, 132}};
        for (int i = 0; i < rowA.length; i++)
            house(rowA[i][0], -40, rowA[i][1], -28, 70, "north", i + 3, rng);

        // Upper row B near the wall (doors south)
        int[][] rowB = {{-70, -58}, {-54, -42}, {56, 68}, {72, 84}, {96, 108}};
        for (int i = 0; i < rowB.length; i++)
            house(rowB[i][0], -80, rowB[i][1], -66, 70, "south", i + 1, rng);
    }

    /** Parameterized townhouse with per-style materials; door on the given side. */
    private static void house(int x1, int z1, int x2, int z2, int gy, String doorSide, int styleIdx, Random rng) {
        int[][] styles = { // {wall, frame, floor}, roof material by name below
            {OAK, DARK_OAK_LOG_Y, SPRUCE},
            {SPRUCE, STRIPPED_SPRUCE_Y, OAK},
            {BIRCH, DARK_OAK_LOG_Y, DARK_OAK},
            {PIRATE_PLANKS, SPRUCE_LOG_Y, SPRUCE},
            {SPRUCE, DARK_OAK_LOG_Y, OAK},
        };
        String[] roofs = {"dark_oak", "spruce", "dark_oak", "spruce", "oak"};
        int s = styleIdx % styles.length;
        int wall = styles[s][0], frame = styles[s][1], floor = styles[s][2];
        int wallH = 4;
        boolean twoStory = (styleIdx % 3 == 0);

        shell(x1, z1, x2, z2, gy, wallH, floor, wall, frame, COBBLE);
        windowsAlongX(x1, x2, z1, gy + 2, 3);
        windowsAlongX(x1, x2, z2, gy + 2, 3);

        int topY = gy + wallH;
        if (twoStory) {
            fill(x1 + 1, gy + 4, z1 + 1, x2 - 1, gy + 4, z2 - 1, floor);
            for (int y = gy + 5; y <= gy + 7; y++) {
                for (int x = x1; x <= x2; x++) { set(x, y, z1, wall); set(x, y, z2, wall); }
                for (int z = z1 + 1; z <= z2 - 1; z++) { set(x1, y, z, wall); set(x2, y, z, wall); }
            }
            for (int y = gy + 5; y <= gy + 7; y++) {
                set(x1, y, z1, frame); set(x2, y, z1, frame);
                set(x1, y, z2, frame); set(x2, y, z2, frame);
            }
            fill(x1 + 1, gy + 5, z1 + 1, x2 - 1, gy + 7, z2 - 1, AIR);
            windowsAlongX(x1, x2, z1, gy + 6, 3);
            windowsAlongX(x1, x2, z2, gy + 6, 3);
            topY = gy + 7;
            // ladder to the loft
            for (int y = gy + 1; y <= gy + 4; y++) set(x2 - 1, y, z1 + 1, LADDER_S);
        }
        gableRoofX(x1, x2, z1, z2, topY + 1, roofs[s], wall);
        gableEndsX(x1, z1 + 1, z2 - 1, topY + 1, wall);
        gableEndsX(x2, z1 + 1, z2 - 1, topY + 1, wall);

        // door + a lantern beside it
        int doorX = (x1 + x2) / 2;
        int doorZ = doorSide.equals("north") ? z1 : z2;
        doorway(doorX, gy, doorZ);
        placeDoor(doorX, gy, doorZ, s == 3 ? "spruce" : "oak", doorSide.equals("north") ? "north" : "south");
        set(doorX + 1, gy + 3, doorZ, LANTERN);
        // interior basics
        String[] bedColors = {"red", "cyan", "lime", "orange", "light_blue", "purple", "yellow"};
        String bc = bedColors[styleIdx % bedColors.length];
        set(x1 + 2, gy + 1, z1 + 2, bed(bc, "east", false));
        set(x1 + 3, gy + 1, z1 + 2, bed(bc, "east", true));
        set(x2 - 2, gy + 1, z2 - 2, BARREL_UP);
        set(x1 + 2, gy + 1, z2 - 2, CRAFTING);
        ceilingLanterns(x1, z1, x2, z2, gy + 3);
        if (twoStory) ceilingLanterns(x1, z1, x2, z2, gy + 7);
        // window box
        set(doorX - 2, gy + 1, doorZ + (doorSide.equals("north") ? -1 : 1), POTTED_TULIP);
        furnishHouse(x1, z1, x2, z2, gy, doorSide.equals("north"), twoStory, styleIdx);
    }

    // ------------------------------------------------------------------
    // City wall + gatehouse
    // ------------------------------------------------------------------

    private static void cityWall() {
        int gy = 70;
        int zw1 = -90, zw2 = -86; // wall band
        for (int x = -136; x <= 136; x++) {
            if (x >= -10 && x <= 10) continue; // gatehouse handles this span
            if (!SpawnIslandTerrain.isLand(x, zw1) || !SpawnIslandTerrain.isLand(x, zw2)) continue;
            for (int z = zw1 + 1; z <= zw2 - 1; z++) {
                // level the ground under the wall, then raise it
                ground(x, z, gy, PIRATE_STONE_BRICKS, COBBLE, 5, CLEAR_TO);
                fill(x, gy + 1, z, x, gy + 5, z, (x % 9 == 0) ? PIRATE_STONE_BRICKS : STONE_BRICKS);
                set(x, gy + 6, z, slab("stone_brick"));
            }
            // crenellations above the walkway on both edges
            if (x % 2 == 0) {
                set(x, gy + 7, zw1 + 1, STONE_BRICK_WALL);
                set(x, gy + 7, zw2 - 1, STONE_BRICK_WALL);
            }
        }
        // towers along the wall
        for (int tx : new int[]{-96, -48, 48, 96}) wallTower(tx, gy, -88);
        // gatehouse
        gatehouse(gy);
        // wall-end bastions
        wallTower(-136, gy, -88);
        wallTower(136, gy, -88);
    }

    private static void wallTower(int cx, int gy, int cz) {
        int r = 3;
        fill(cx - r, gy - 2, cz - r, cx + r, gy, cz + r, STONE_BRICKS);
        for (int y = gy + 1; y <= gy + 9; y++) {
            for (int x = cx - r; x <= cx + r; x++) { set(x, y, cz - r, PIRATE_STONE_BRICKS); set(x, y, cz + r, PIRATE_STONE_BRICKS); }
            for (int z = cz - r; z <= cz + r; z++) { set(cx - r, y, z, PIRATE_STONE_BRICKS); set(cx + r, y, z, PIRATE_STONE_BRICKS); }
        }
        fill(cx - r + 1, gy + 1, cz - r + 1, cx + r - 1, gy + 8, cz + r - 1, AIR);
        fill(cx - r, gy + 9, cz - r, cx + r, gy + 9, cz + r, STONE_BRICKS);
        // crenels
        for (int x = cx - r; x <= cx + r; x += 2) { set(x, gy + 10, cz - r, STONE_BRICK_WALL); set(x, gy + 10, cz + r, STONE_BRICK_WALL); }
        for (int z = cz - r; z <= cz + r; z += 2) { set(cx - r, gy + 10, z, STONE_BRICK_WALL); set(cx + r, gy + 10, z, STONE_BRICK_WALL); }
        // ladder + door to the wall walk
        doorway(cx, gy, cz + r);
        for (int y = gy + 1; y <= gy + 8; y++) set(cx, y, cz - r + 1, LADDER_S);
        set(cx, gy + 8, cz, LANTERN_HANGING);   // interior light
        set(cx, gy + 10, cz, LANTERN);
        // banner
        set(cx, gy + 8, cz + r + 1, BLACK_BANNER_S);
    }

    private static void gatehouse(int gy) {
        // twin towers flanking the avenue
        wallTower(-8, gy, -88);
        wallTower(8, gy, -88);
        // arch over the road
        for (int x = -4; x <= 4; x++) {
            fill(x, gy + 5, -89, x, gy + 6, -85, STONE_BRICKS);
            set(x, gy + 7, -87, STONE_BRICK_WALL);
        }
        set(-4, gy + 4, -87, stairs("stone_brick", "east"));
        set(4, gy + 4, -87, stairs("stone_brick", "west"));
        // portcullis look
        for (int x = -3; x <= 3; x++) set(x, gy + 4, -87, IRON_BARS);
        // road through the gate
        for (int z = -90; z <= -84; z++)
            for (int x = -4; x <= 4; x++) {
                set(x, gy, z, (Math.abs(x) <= 1) ? POLISHED_ANDESITE : STONE_BRICKS);
                for (int y = gy + 1; y <= gy + 3; y++) set(x, y, z, AIR);
            }
        // lanterns + banners
        set(-5, gy + 4, -84, LANTERN); set(5, gy + 4, -84, LANTERN);
        set(-5, gy + 6, -84, BLACK_BANNER_S); set(5, gy + 6, -84, BLACK_BANNER_S);
    }

    // ------------------------------------------------------------------
    // North half: lighthouse, fort, paths, palms
    // ------------------------------------------------------------------

    private static void lighthouse() {
        int cx = 108, cz = -102;
        int base = (int) Math.round(SpawnIslandTerrain.islandSurfaceY(cx, cz));
        // platform
        for (int x = cx - 6; x <= cx + 6; x++)
            for (int z = cz - 6; z <= cz + 6; z++) {
                fill(x, base - 4, z, x, base, z, STONE_BRICKS);
                for (int y = base + 1; y <= base + 34; y++) set(x, y, z, AIR);
            }
        for (int x = cx - 6; x <= cx + 6; x += 2) { set(x, base + 1, cz - 6, STONE_BRICK_WALL); set(x, base + 1, cz + 6, STONE_BRICK_WALL); }
        for (int z = cz - 6; z <= cz + 6; z += 2) { set(cx - 6, base + 1, z, STONE_BRICK_WALL); set(cx + 6, base + 1, z, STONE_BRICK_WALL); }

        // tapered tower: radius 3 then 2, banded white/red
        int towerTop = base + 26;
        for (int y = base + 1; y <= towerTop; y++) {
            int r = (y < base + 14) ? 3 : 2;
            boolean band = ((y - base) % 7 < 3);
            int mat = band ? RED_TERRACOTTA : POLISHED_DIORITE;
            for (int x = cx - r; x <= cx + r; x++)
                for (int z = cz - r; z <= cz + r; z++) {
                    boolean edge = (Math.abs(x - cx) == r || Math.abs(z - cz) == r);
                    // knock the corners off for a rounder silhouette
                    boolean corner = (Math.abs(x - cx) == r && Math.abs(z - cz) == r);
                    if (corner && r == 3) continue;
                    if (edge) set(x, y, z, mat);
                    else set(x, y, z, AIR);
                }
        }
        // interior ladder + window slits
        for (int y = base + 1; y <= towerTop + 1; y++) set(cx, y, cz - 1, LADDER_S);
        for (int y = base + 5; y <= towerTop - 3; y += 6) set(cx + (y % 2 == 0 ? 3 : 2), y, cz, GLASS_PANE);
        // door facing the path (west)
        set(cx - 3, base + 1, cz, AIR); set(cx - 3, base + 2, cz, AIR);
        set(cx - 3, base + 3, cz, stairs("stone_brick", "west"));

        // catwalk
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = cz - 3; z <= cz + 3; z++) {
                boolean corner = (Math.abs(x - cx) == 3 && Math.abs(z - cz) == 3);
                if (!corner && (Math.abs(x - cx) == 3 || Math.abs(z - cz) == 3)) {
                    set(x, towerTop + 1, z, slab("stone_brick"));
                    set(x, towerTop + 2, z, IRON_BARS);
                }
            }
        // lamp room
        for (int y = towerTop + 1; y <= towerTop + 3; y++) {
            for (int x = cx - 2; x <= cx + 2; x++)
                for (int z = cz - 2; z <= cz + 2; z++) {
                    boolean edge = (Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2);
                    if (edge) set(x, y, z, (y == towerTop + 1 || y == towerTop + 3) ? POLISHED_DIORITE : GLASS_PANE);
                }
        }
        set(cx, towerTop + 1, cz, SEA_LANTERN);
        set(cx, towerTop + 2, cz, GLOWSTONE);
        set(cx, towerTop + 3, cz, SEA_LANTERN);
        // roof cap
        fill(cx - 2, towerTop + 4, cz - 2, cx + 2, towerTop + 4, cz + 2, RED_TERRACOTTA);
        set(cx, towerTop + 5, cz, STONE_BRICK_WALL);
        set(cx, towerTop + 6, cz, LANTERN);
    }

    private static void fort() {
        int cx = -98, cz = -112;
        int gy = (int) Math.round(SpawnIslandTerrain.islandSurfaceY(cx, cz));
        int x1 = cx - 13, x2 = cx + 13, z1 = cz - 13, z2 = cz + 13;
        // level courtyard
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                ground(x, z, gy, (x + z) % 6 == 0 ? MOSSY_COBBLE : COBBLE, COBBLE, 6, gy + 22);
        // ramparts
        for (int y = gy + 1; y <= gy + 5; y++) {
            for (int x = x1; x <= x2; x++) { set(x, y, z1, PIRATE_STONE_BRICKS); set(x, y, z2, PIRATE_STONE_BRICKS); }
            for (int z = z1; z <= z2; z++) { set(x1, y, z, PIRATE_STONE_BRICKS); set(x2, y, z, PIRATE_STONE_BRICKS); }
        }
        // walk on the rampart + crenels
        for (int x = x1; x <= x2; x++) {
            set(x, gy + 5, z1, STONE_BRICKS); set(x, gy + 5, z2, STONE_BRICKS);
            if (x % 2 == 0) { set(x, gy + 6, z1, STONE_BRICK_WALL); set(x, gy + 6, z2, STONE_BRICK_WALL); }
        }
        for (int z = z1; z <= z2; z++) {
            set(x1, gy + 5, z, STONE_BRICKS); set(x2, gy + 5, z, STONE_BRICKS);
            if (z % 2 == 0) { set(x1, gy + 6, z, STONE_BRICK_WALL); set(x2, gy + 6, z, STONE_BRICK_WALL); }
        }
        // corner turrets
        for (int[] c : new int[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) {
            for (int y = gy + 1; y <= gy + 8; y++)
                for (int dx = -1; dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        if (Math.abs(dx) == 1 || Math.abs(dz) == 1) set(c[0] + dx, y, c[1] + dz, PIRATE_STONE_BRICKS);
                        else set(c[0], y, c[1], AIR);
            fill(c[0] - 1, gy + 9, c[1] - 1, c[0] + 1, gy + 9, c[1] + 1, STONE_BRICKS);
            set(c[0], gy + 10, c[1], LANTERN);
        }
        // gate on the east side toward the path
        for (int z = cz - 1; z <= cz + 1; z++)
            for (int y = gy + 1; y <= gy + 3; y++) set(x2, y, z, AIR);
        set(x2, gy + 4, cz, STONE_BRICK_WALL);
        set(x2 + 1, gy + 1, cz - 2, LANTERN); // gate lanterns on plinths
        set(x2 + 1, gy + 1, cz + 2, LANTERN);
        // stairs up to the rampart
        for (int i = 0; i < 4; i++) set(x1 + 2 + i, gy + 1 + i, z1 + 2, stairs("stone_brick", "east"));
        fill(x1 + 2, gy + 6, z1 + 2, x1 + 5, gy + 6, z1 + 2, AIR);
        // seaward cannon battery (south + west ramparts)
        cannonSculpture(cx - 6, gy + 6, z2, "south");
        cannonSculpture(cx + 2, gy + 6, z2, "south");
        cannonSculpture(x1, gy + 6, cz - 4, "west");
        cannonSculpture(x1, gy + 6, cz + 4, "west");
        // powder store + barracks lean-to in the courtyard
        set(cx - 8, gy + 1, cz - 8, GUNPOWDER_BARREL);
        set(cx - 7, gy + 1, cz - 8, GUNPOWDER_BARREL);
        set(cx - 8, gy + 2, cz - 8, GUNPOWDER_BARREL);
        set(cx - 8, gy + 1, cz - 6, BARREL_UP);
        fill(cx + 4, gy + 4, cz - 10, cx + 10, gy + 4, cz - 5, slab("spruce"));
        for (int y = gy + 1; y <= gy + 3; y++) { set(cx + 4, y, cz - 10, SPRUCE_LOG_Y); set(cx + 10, y, cz - 10, SPRUCE_LOG_Y);
            set(cx + 4, y, cz - 5, SPRUCE_LOG_Y); set(cx + 10, y, cz - 5, SPRUCE_LOG_Y); }
        set(cx + 6, gy + 1, cz - 8, bed("gray", "east", false));
        set(cx + 7, gy + 1, cz - 8, bed("gray", "east", true));
        lootChest(cx + 9, gy + 1, cz - 7, "west", "minecraft:chests/pillager_outpost");
        // flag mast
        for (int y = gy + 1; y <= gy + 12; y++) set(cx, y, cz, SPRUCE_FENCE);
        fill(cx + 1, gy + 12, cz, cx + 3, gy + 12, cz, BLACK_WOOL);
        fill(cx + 1, gy + 11, cz, cx + 2, gy + 11, cz, BLACK_WOOL);
        set(cx + 1, gy + 10, cz, WHITE_WOOL);
        set(cx, gy + 13, cz, LANTERN);
    }

    /** Decorative shore cannon: blackstone barrel on a spruce carriage. */
    private static void cannonSculpture(int x, int y, int z, String facing) {
        if (facing.equals("south")) {
            set(x, y, z - 1, stairs("spruce", "north"));
            set(x, y, z, POLISHED_BLACKSTONE);
            set(x, y + 1, z - 1, POLISHED_BLACKSTONE);
            set(x, y + 1, z, BLACKSTONE_WALL);
        } else { // west
            set(x + 1, y, z, stairs("spruce", "east"));
            set(x, y, z, POLISHED_BLACKSTONE);
            set(x + 1, y + 1, z, POLISHED_BLACKSTONE);
            set(x, y + 1, z, BLACKSTONE_WALL);
        }
    }

    private static void northPaths() {
        // main path from the gate heading north
        pathSegment(0, -91, 0, -112);
        // fork east to the lighthouse
        diagonalPath(0, -112, 100, -102);
        // fork west to the fort
        diagonalPath(0, -112, -83, -112);
    }

    private static void pathSegment(int x1, int z1, int x2, int z2) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / Math.max(steps, 1);
            int z = z1 + (z2 - z1) * i / Math.max(steps, 1);
            pathBlob(x, z);
        }
    }

    private static void diagonalPath(int x1, int z1, int x2, int z2) {
        pathSegment(x1, z1, x2, z2);
    }

    private static void pathBlob(int x, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int px = x + dx, pz = z + dz;
                if (!SpawnIslandTerrain.isLand(px, pz)) continue;
                if (SpawnIslandTerrain.inCityFootprint(px, pz)) continue;
                int gy = (int) Math.round(SpawnIslandTerrain.islandSurfaceY(px, pz));
                set(px, gy, pz, (dx == 0 && dz == 0) ? GRAVEL : DIRT_PATH);
                set(px, gy - 1, pz, DIRT);
                fill(px, gy + 1, pz, px, gy + 4, pz, AIR);
            }
    }

    private static void northPalms(Random rng) {
        int placed = 0;
        for (int attempt = 0; attempt < 300 && placed < 22; attempt++) {
            int x = -150 + rng.nextInt(301);
            int z = -155 + rng.nextInt(60); // north half only
            if (!SpawnIslandTerrain.isLand(x, z)) continue;
            if (SpawnIslandTerrain.inCityFootprint(x, z)) continue;
            // keep clear of the lighthouse, fort, and paths
            if (Math.abs(x - 108) < 12 && Math.abs(z + 102) < 12) continue;
            if (Math.abs(x + 98) < 18 && Math.abs(z + 112) < 18) continue;
            if (Math.abs(x) < 6) continue;
            int gy = (int) Math.round(SpawnIslandTerrain.islandSurfaceY(x, z));
            if (!openSky(x, gy, z, 3)) continue;   // keep clear of fields, windmill, lookout
            palm(x, gy, z, 5 + rng.nextInt(4));
            placed++;
        }
    }

    /** Nothing placed in the (2r+1)^2 column block above gy (palm canopy clearance). */
    private static boolean openSky(int cx, int gy, int cz, int r) {
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++)
                for (int y = gy - 1; y <= gy + 10; y++)
                    if (getRaw(x, y, z) > 0) return false;
        return true;
    }

    private static void cityGreenery(Random rng) {
        // scatter grass tufts + flowers on unpaved city ground
        for (int i = 0; i < 700; i++) {
            int x = SpawnIslandTerrain.CITY_X_MIN + rng.nextInt(SpawnIslandTerrain.CITY_X_MAX - SpawnIslandTerrain.CITY_X_MIN + 1);
            int z = SpawnIslandTerrain.CITY_Z_MIN + rng.nextInt(SpawnIslandTerrain.CITY_Z_MAX - SpawnIslandTerrain.CITY_Z_MIN + 1);
            if (!SpawnIslandTerrain.inCityFootprint(x, z)) continue;
            int gy = SpawnIslandTerrain.cityGroundY(x, z);
            if (getRaw(x, gy, z) != GRASS) continue;      // only on lawn
            if (getRaw(x, gy + 1, z) != AIR) continue;    // only if empty above
            int roll = rng.nextInt(10);
            int plant = roll < 6 ? SHORT_GRASS : (roll < 7 ? POPPY : (roll < 8 ? DANDELION : (roll < 9 ? OXEYE : CORNFLOWER)));
            set(x, gy + 1, z, plant);
        }
    }

    // ------------------------------------------------------------------
    // Countryside north of the wall: farms, windmill, hilltop lookout
    // ------------------------------------------------------------------

    private static int surf(int x, int z) {
        return (int) Math.round(SpawnIslandTerrain.islandSurfaceY(x, z));
    }

    /** Natural (non-city) island land. */
    private static boolean natural(int x, int z) {
        return SpawnIslandTerrain.isLand(x, z) && !SpawnIslandTerrain.inCityFootprint(x, z);
    }

    private static int averageSurf(int x1, int z1, int x2, int z2) {
        long sum = 0; int n = 0;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                if (natural(x, z)) { sum += surf(x, z); n++; }
        return n == 0 ? 70 : (int) Math.round(sum / (double) n);
    }

    /**
     * Levels natural terrain to {@code y}: dirt packed underneath, {@code topId} on top and the
     * hillside cut away above - so a structure never floats or buries its doorstep.
     */
    private static void levelPad(int x1, int z1, int x2, int z2, int y, int topId) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (!natural(x, z)) continue;
                int s = surf(x, z);
                for (int yy = Math.min(s, y) - 3; yy < y; yy++) set(x, yy, z, DIRT);
                set(x, y, z, topId);
                for (int yy = y + 1; yy <= Math.max(s, y) + 3; yy++) set(x, yy, z, AIR);
            }
    }

    private static void countryside() {
        farmField(-60, -108, -31, -95, "wheat");
        farmField(24, -104, 53, -93, "carrots");
        farmField(-27, -126, -10, -117, "potatoes");
        windmill(-18, -101);
        // track from the north path up to the ridge-top lookout
        pathSegment(0, -112, 4, -131);
        lookout(5, -138);
    }

    /** Irrigated crop field: log border, a water channel every 5th column, fully grown crops, scarecrow. */
    private static void farmField(int x1, int z1, int x2, int z2, String crop) {
        int y = averageSurf(x1, z1, x2, z2);
        levelPad(x1 - 2, z1 - 2, x2 + 2, z2 + 2, y, GRASS);
        int farmland = id("minecraft:farmland[moisture=7]");
        int cropId = id("minecraft:" + crop + "[age=7]");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (x == x1 || x == x2) { set(x, y, z, OAK_LOG_Z); continue; }
                if (z == z1 || z == z2) { set(x, y, z, OAK_LOG_X); continue; }
                if ((x - x1) % 5 == 0) { set(x, y, z, WATER); continue; }
                set(x, y, z, farmland);
                set(x, y + 1, z, cropId);
            }
        // scarecrow on a farmland column in the middle of the field
        int sx = x1 + 7, sz = (z1 + z2) / 2;
        set(sx, y + 1, sz, OAK_FENCE);
        set(sx, y + 2, sz, OAK_FENCE);
        set(sx - 1, y + 2, sz, OAK_FENCE);
        set(sx + 1, y + 2, sz, OAK_FENCE);
        set(sx, y + 3, sz, id("minecraft:carved_pumpkin[facing=south]"));
        // hay + composter at the field corner
        set(x2 + 1, y + 1, z2 + 1, HAY);
        set(x2 + 1, y + 2, z2 + 1, HAY);
        set(x2 + 2, y + 1, z2 + 1, COMPOSTER);
    }

    /** Stone-and-spruce tower windmill with four canvas sails turned toward the city. */
    private static void windmill(int cx, int cz) {
        int y = averageSurf(cx - 4, cz - 4, cx + 4, cz + 4);
        levelPad(cx - 6, cz - 6, cx + 6, cz + 6, y, GRASS);
        Random rng = new Random(771L);
        int roofH = 4;
        for (int dy = 0; dy <= 10 + roofH; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                for (int dz = -3; dz <= 3; dz++) {
                    int x = cx + dx, z = cz + dz, yy = y + dy;
                    if (dy > 10) { // conical dark-oak roof
                        int k = dy - 10;
                        if (Math.abs(dx) + Math.abs(dz) <= 5 - k && Math.max(Math.abs(dx), Math.abs(dz)) <= 3 - k / 2)
                            set(x, yy, z, DARK_OAK);
                        continue;
                    }
                    if (Math.abs(dx) + Math.abs(dz) > 5) continue;              // octagonal plan
                    boolean edge = Math.abs(dx) + Math.abs(dz) == 5 || Math.abs(dx) == 3 || Math.abs(dz) == 3;
                    if (dy == 0) { set(x, yy, z, edge ? COBBLE : SPRUCE); continue; }
                    if (!edge) { set(x, yy, z, AIR); continue; }
                    int wall = dy <= 6 ? (rng.nextInt(5) == 0 ? MOSSY_COBBLE : COBBLE) : SPRUCE;
                    if (dy == 6 || dy == 10) wall = SPRUCE_LOG_Y;
                    set(x, yy, z, wall);
                }
            }
        }
        // door (south) + windows
        placeDoor(cx, y, cz + 3, "spruce", "south");
        set(cx - 3, y + 4, cz, GLASS_PANE); set(cx + 3, y + 4, cz, GLASS_PANE);
        set(cx, y + 8, cz - 3, GLASS_PANE);
        // interior: loft floor, ladder, millstones
        fill(cx - 2, y + 6, cz - 2, cx + 2, y + 6, cz + 2, SPRUCE);
        set(cx + 2, y + 6, cz - 2, AIR);
        for (int yy = y + 1; yy <= y + 6; yy++) set(cx + 2, yy, cz - 2, LADDER_S);
        set(cx - 2, y + 1, cz - 2, GRINDSTONE);
        set(cx - 2, y + 1, cz, BARREL_UP); set(cx - 2, y + 1, cz + 1, HAY); set(cx - 1, y + 1, cz - 2, HAY);
        set(cx, y + 5, cz, LANTERN_HANGING);
        set(cx, y + 9, cz, LANTERN_HANGING);
        // axle + pinwheel sails on the south face
        int hubY = y + 9, sz = cz + 4;
        set(cx, hubY, cz + 3, SPRUCE_LOG_Z);
        set(cx, hubY, sz, SPRUCE_LOG_Z);
        for (int i = 1; i <= 6; i++) {
            set(cx, hubY + i, sz, SPRUCE_FENCE);          // up arm, cloth to the east
            set(cx, hubY - i, sz, SPRUCE_FENCE);          // down arm, cloth to the west
            set(cx + i, hubY, sz, SPRUCE_FENCE);          // east arm, cloth below
            set(cx - i, hubY, sz, SPRUCE_FENCE);          // west arm, cloth above
            if (i >= 2) {
                set(cx + 1, hubY + i, sz, WHITE_WOOL);
                set(cx - 1, hubY - i, sz, WHITE_WOOL);
                set(cx + i, hubY - 1, sz, WHITE_WOOL);
                set(cx - i, hubY + 1, sz, WHITE_WOOL);
            }
        }
    }

    /** Timber gazebo on the north ridge with a view over the whole port, and the Jolly Roger. */
    private static void lookout(int cx, int cz) {
        int y = averageSurf(cx - 4, cz - 4, cx + 4, cz + 4) + 1;
        levelPad(cx - 5, cz - 5, cx + 5, cz + 5, y - 1, GRASS);
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++) {
                boolean rim = Math.abs(dx) == 4 || Math.abs(dz) == 4;
                set(cx + dx, y, cz + dz, rim ? POLISHED_ANDESITE : STONE_BRICKS);
                fill(cx + dx, y + 1, cz + dz, cx + dx, y + 7, cz + dz, AIR);
            }
        set(cx, y, cz + 4, stairs("stone_brick", "north"));  // step up from the path side
        for (int[] c : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            for (int yy = y + 1; yy <= y + 4; yy++) set(cx + c[0], yy, cz + c[1], SPRUCE_LOG_Y);
        }
        // hipped roof
        for (int k = 0; k <= 4; k++) {
            int r = 5 - k;
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++)
                    if (Math.abs(dx) == r || Math.abs(dz) == r) set(cx + dx, y + 5 + k, cz + dz, k == 4 ? DARK_OAK : slab("spruce"));
        }
        set(cx, y + 5, cz, LANTERN_HANGING);
        // rail with a gap facing the path, benches, a chart table
        for (int d = -3; d <= 3; d++) {
            set(cx + d, y + 1, cz - 4, SPRUCE_FENCE);
            set(cx - 4, y + 1, cz + d, SPRUCE_FENCE);
            set(cx + 4, y + 1, cz + d, SPRUCE_FENCE);
            if (Math.abs(d) >= 2) set(cx + d, y + 1, cz + 4, SPRUCE_FENCE);
        }
        for (int d = -2; d <= 2; d++) {
            set(cx + d, y + 1, cz - 3, stairs("spruce", "north"));
        }
        set(cx - 3, y + 1, cz, stairs("spruce", "west"));
        set(cx - 3, y + 1, cz + 1, stairs("spruce", "west"));
        set(cx + 3, y + 1, cz, stairs("spruce", "east"));
        set(cx + 3, y + 1, cz + 1, stairs("spruce", "east"));
        set(cx, y + 1, cz, CARTOGRAPHY);
        set(cx, y + 2, cz, id("minecraft:potted_fern"));
        // flagpole with a black-and-white Jolly Roger flying over the island
        int fx = cx + 6, fz = cz - 1;
        set(fx, y - 1, fz, COBBLE);
        for (int yy = y; yy <= y + 12; yy++) set(fx, yy, fz, SPRUCE_FENCE);
        for (int dx = 1; dx <= 4; dx++)
            for (int yy = y + 9; yy <= y + 12; yy++)
                set(fx + dx, yy, fz, BLACK_WOOL);
        set(fx + 2, y + 11, fz, WHITE_WOOL); set(fx + 3, y + 11, fz, WHITE_WOOL);   // skull
        set(fx + 2, y + 10, fz, WHITE_WOOL); set(fx + 3, y + 10, fz, WHITE_WOOL);
        set(fx + 1, y + 9, fz, WHITE_WOOL); set(fx + 4, y + 9, fz, WHITE_WOOL);     // crossbones
    }

    /** Scatter tall grass, ferns and wildflowers over the untouched northern hills. */
    private static void northMeadow(Random rng) {
        int fern = id("minecraft:fern");
        int allium = id("minecraft:allium");
        for (int i = 0; i < 2600; i++) {
            int x = X0 + 4 + rng.nextInt(W - 8);
            int z = Z0 + 4 + rng.nextInt(80);
            if (!natural(x, z)) continue;
            int gy = surf(x, z);
            if (gy <= SpawnIslandTerrain.SEA_LEVEL + 1) continue;              // not on the beach
            if (getRaw(x, gy, z) != 0 || getRaw(x, gy + 1, z) != 0 || getRaw(x, gy + 2, z) != 0) continue;
            int roll = rng.nextInt(20);
            int plant = roll < 11 ? SHORT_GRASS : roll < 15 ? fern : roll < 17 ? DANDELION : roll < 18 ? POPPY
                    : roll < 19 ? OXEYE : allium;
            set(x, gy, z, GRASS);
            set(x, gy - 1, z, DIRT);
            set(x, gy + 1, z, plant);
        }
    }

    // ------------------------------------------------------------------
    // Coast & harbor dressing
    // ------------------------------------------------------------------

    /** A merchant sloop run aground on the south-east beach, with salvage aboard. */
    private static void beachWreck() {
        int x0 = 141, x1 = 156, zc = 76;
        int darkPlanks = DARK_OAK, rot = id("pixelpirates:destroyed_planks");
        Random rng = new Random(1717L);
        for (int x = x0; x <= x1; x++) {
            int i = x - x0;
            int yb = 63 - i / 3;                                   // bow on the sand, stern sinking
            int half = i == 0 ? 0 : i == 1 ? 1 : 2;                // pointed bow
            for (int dz = -half; dz <= half; dz++) {
                int z = zc + dz;
                boolean side = Math.abs(dz) == half && half > 0;
                set(x, yb, z, rng.nextInt(6) == 0 ? rot : darkPlanks);   // keel/bottom
                int sideTop = yb + (i < 3 ? 2 : 3) - (x % 4 == 1 && dz > 0 ? 2 : 0); // stove-in starboard planks
                for (int yy = yb + 1; yy <= yb + 3; yy++) {
                    if (side && yy <= sideTop) set(x, yy, z, rng.nextInt(5) == 0 ? rot : darkPlanks);
                    else set(x, yy, z, yy <= 62 ? WATER : AIR);
                }
            }
            if (i >= 2 && i % 3 == 0) set(x, yb + 1, zc, stairs("dark_oak", "west")); // exposed ribs/thwarts
        }
        // snapped mast lying across the sand + a scrap of sail
        for (int yy = 64; yy <= 66; yy++) set(148, yy, zc, SPRUCE_LOG_Y);
        for (int x = 134; x <= 140; x++) set(x, 64, zc + 3, SPRUCE_LOG_X);
        set(135, 64, zc + 4, WHITE_WOOL); set(136, 64, zc + 4, WHITE_WOOL); set(136, 64, zc + 5, WHITE_WOOL);
        // salvage
        lootChest(143, 64, zc, "west", "minecraft:chests/shipwreck_treasure");
        set(144, 64, zc - 1, BARREL_UP);
        set(139, 64, zc - 3, BARREL_UP);
        set(138, 64, zc - 2, CAMPFIRE);                              // a survivor's camp
        set(137, 64, zc - 3, OAK_LOG_X);
    }

    /** Two moored sloops alongside the side piers and rowboats at the quay steps. */
    private static void harborBoats() {
        sloop(-88, 106);   // west of the west pier
        sloop(52, 108);    // east of the east pier
        rowboat(-24, 100);
        rowboat(20, 100);
        rowboat(-60, 101);
    }

    /** Decorative moored sloop, hull along +Z (bow at zBow), deck at sea level. */
    private static void sloop(int cx, int zBow) {
        int len = 15;
        for (int i = 0; i < len; i++) {
            int z = zBow + i;
            int half = i == 0 ? 0 : i == 1 ? 1 : 2;
            for (int dx = -half; dx <= half; dx++) {
                int x = cx + dx;
                fill(x, 59, z, x, 62, z, SPRUCE);                   // hull body (sits in the water)
                if (Math.abs(dx) == half) set(x, 62, z, DARK_OAK);  // dark wale stripe at the waterline
                set(x, 63, z, Math.abs(dx) == half || i == len - 1 ? DARK_OAK : SPRUCE);
                if (Math.abs(dx) == half || i == len - 1) set(x, 64, z, DARK_OAK_FENCE);
                else set(x, 64, z, AIR);
                fill(x, 65, z, x, 76, z, AIR);
            }
        }
        // bowsprit
        set(cx, 64, zBow - 1, DARK_OAK_FENCE);
        set(cx, 64, zBow - 2, DARK_OAK_FENCE);
        // stern cabin
        for (int z = zBow + 11; z <= zBow + 13; z++)
            for (int dx = -1; dx <= 1; dx++) {
                set(cx + dx, 64, z, DARK_PIRATE_PLANKS);
                set(cx + dx, 65, z, slab("dark_oak"));
            }
        set(cx, 64, zBow + 11, AIR);
        set(cx, 66, zBow + 13, LANTERN);
        // mast, yard and a square sail (spans the beam, facing the bow)
        int mz = zBow + 6;
        for (int yy = 64; yy <= 75; yy++) set(cx, yy, mz, SPRUCE_FENCE);
        for (int dx = -3; dx <= 3; dx++) set(cx + dx, 74, mz, SPRUCE_FENCE);
        for (int dx = -3; dx <= 3; dx++)
            for (int yy = 67; yy <= 73; yy++)
                if (dx != 0) set(cx + dx, yy, mz + 1, WHITE_WOOL);
        for (int yy = 67; yy <= 73; yy++) set(cx, yy, mz + 1, WHITE_WOOL);
        set(cx, 76, mz, BLACK_WOOL);                                 // pennant
        set(cx, 64, zBow + 3, BARREL_UP);
        set(cx + 1, 64, zBow + 3, BARREL_UP);
    }

    private static void rowboat(int cx, int zBow) {
        for (int i = 0; i < 4; i++) {
            int z = zBow + i;
            int half = (i == 0 || i == 3) ? 0 : 1;
            for (int dx = -half; dx <= half; dx++) {
                set(cx + dx, 61, z, OAK);
                boolean rim = Math.abs(dx) == half || i == 0 || i == 3;
                set(cx + dx, 62, z, rim ? OAK : AIR);
                set(cx + dx, 63, z, AIR);
            }
        }
        set(cx, 62, zBow + 2, slab("oak"));   // thwart
        set(cx, 63, zBow - 1, AIR);
    }

    /** Channel buoys marking the approach to the harbor mouth. */
    private static void buoys() {
        int[][] spots = {{-28, 128}, {28, 128}, {-30, 156}, {30, 156}};
        for (int[] s : spots) {
            int color = s[0] < 0 ? RED_WOOL : wool("lime");
            set(s[0], 61, s[1], color);
            set(s[0], 62, s[1], color);
            set(s[0], 63, s[1], WHITE_WOOL);
            set(s[0], 64, s[1], LANTERN);
            for (int yy = 50; yy <= 60; yy++) set(s[0], yy, s[1], CHAIN);
        }
    }

    // ------------------------------------------------------------------
    // City lawns: fill empty green space with gardens, palms and benches
    // ------------------------------------------------------------------

    /** True if the (2r+1)^2 area is open lawn on one terrace with nothing built above it. */
    private static boolean clearLawn(int cx, int cz, int r) {
        int gy0 = SpawnIslandTerrain.cityGroundY(cx, cz);
        for (int x = cx - r; x <= cx + r; x++)
            for (int z = cz - r; z <= cz + r; z++) {
                if (!SpawnIslandTerrain.inCityFootprint(x, z)) return false;
                if (SpawnIslandTerrain.cityGroundY(x, z) != gy0) return false;
                if (getRaw(x, gy0, z) != GRASS) return false;
                for (int y = gy0 + 1; y <= gy0 + 9; y++) {
                    int v = getRaw(x, y, z);
                    if (v != AIR && v != 0) return false;
                }
            }
        return true;
    }

    private static void lawnDecor(Random rng) {
        int bench = stairs("spruce", "north");
        int bedSlab = slab("stone_brick");
        int[] flowers = {POPPY, DANDELION, CORNFLOWER, OXEYE, id("minecraft:allium"), id("minecraft:red_tulip"),
                id("minecraft:orange_tulip"), id("minecraft:blue_orchid")};
        for (int x = SpawnIslandTerrain.CITY_X_MIN + 4; x <= SpawnIslandTerrain.CITY_X_MAX - 4; x += 8) {
            for (int z = SpawnIslandTerrain.CITY_Z_MIN + 4; z <= SpawnIslandTerrain.CITY_Z_MAX - 4; z += 8) {
                int cx = x + rng.nextInt(3) - 1, cz = z + rng.nextInt(3) - 1;
                if (!clearLawn(cx, cz, 4)) continue;
                int gy = SpawnIslandTerrain.cityGroundY(cx, cz);
                switch (rng.nextInt(5)) {
                    case 0, 1 -> {                                    // palm with a stone planter ring
                        for (int dx = -1; dx <= 1; dx++)
                            for (int dz = -1; dz <= 1; dz++)
                                if (dx != 0 || dz != 0) set(cx + dx, gy + 1, cz + dz, bedSlab);
                        palm(cx, gy, cz, 4 + rng.nextInt(3));
                    }
                    case 2 -> {                                       // raised flower bed
                        for (int dx = -2; dx <= 2; dx++)
                            for (int dz = -2; dz <= 2; dz++) {
                                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) set(cx + dx, gy + 1, cz + dz, bedSlab);
                                else set(cx + dx, gy + 1, cz + dz, flowers[rng.nextInt(flowers.length)]);
                            }
                    }
                    case 3 -> {                                       // bench nook under a lamp
                        set(cx - 1, gy + 1, cz, bench);
                        set(cx, gy + 1, cz, bench);
                        set(cx + 1, gy + 1, cz, bench);
                        set(cx - 2, gy + 1, cz, OAK_LEAVES);
                        set(cx + 2, gy + 1, cz, OAK_LEAVES);
                        lamppost(cx, gy, cz - 1);
                        set(cx - 1, gy + 1, cz + 2, flowers[rng.nextInt(flowers.length)]);
                        set(cx + 1, gy + 1, cz + 2, flowers[rng.nextInt(flowers.length)]);
                    }
                    default -> {                                      // leafy hedge clump
                        for (int dx = -1; dx <= 1; dx++)
                            for (int dz = -1; dz <= 1; dz++) {
                                if (rng.nextInt(4) != 0) set(cx + dx, gy + 1, cz + dz, OAK_LEAVES);
                            }
                        set(cx, gy + 1, cz, OAK_LEAVES);
                        set(cx, gy + 2, cz, OAK_LEAVES);
                        set(cx + 2, gy + 1, cz, flowers[rng.nextInt(flowers.length)]);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Interiors (2026-09-28) - furnishing pass over every building.
    //
    // SAFETY: every furnishing call goes through put(), which only writes into a cell that
    // is currently explicit interior AIR and is outside every keepClear() box. So furniture
    // can never overwrite a wall, a door, a stair or a bed, and never blocks a doorway or a
    // staircase run (register those with keepClear before furnishing a building).
    // ------------------------------------------------------------------

    private static final List<int[]> KEEP_CLEAR = new ArrayList<>();

    private static void keepClear(int x1, int z1, int x2, int z2) {
        KEEP_CLEAR.add(new int[]{Math.min(x1, x2), Math.min(z1, z2), Math.max(x1, x2), Math.max(z1, z2)});
    }

    private static boolean reserved(int x, int z) {
        for (int[] r : KEEP_CLEAR) if (x >= r[0] && x <= r[2] && z >= r[1] && z <= r[3]) return true;
        return false;
    }

    /** Furnishing write: only into empty interior air, never into a reserved (walkway) column. */
    private static boolean put(int x, int y, int z, int blockId) {
        if (getRaw(x, y, z) != AIR || reserved(x, z)) return false;
        set(x, y, z, blockId);
        return true;
    }

    /** Stack on top of something (candle on a table, pot on a barrel) - only if the base went in. */
    private static void putOn(int x, int y, int z, int base, int top) {
        if (put(x, y, z, base)) put(x, y + 1, z, top);
    }

    // ---- palette helpers for furnishings
    private static int carpet(String color)      { return id("minecraft:" + color + "_carpet"); }
    private static int candle(int n)              { return id("minecraft:candle[candles=" + n + ",lit=true]"); }
    private static int candle(String c, int n)    { return id("minecraft:" + c + "_candle[candles=" + n + ",lit=true]"); }
    private static int barrel(String facing)      { return id("minecraft:barrel[facing=" + facing + "]"); }
    private static int shelf(String facing)       { return id("minecraft:spruce_trapdoor[facing=" + facing + ",half=top,open=true]"); }
    private static int wallBanner(String color, String facing) { return id("minecraft:" + color + "_wall_banner[facing=" + facing + "]"); }
    private static int wallSkull(String facing)   { return id("minecraft:skeleton_wall_skull[facing=" + facing + "]"); }
    private static int hammock(String facing)     { return id("pixelpirates:hammock[facing=" + facing + "]"); }

    private static final String[] POTS = {"potted_fern", "potted_red_tulip", "potted_oxeye_daisy", "potted_cornflower",
            "potted_azalea_bush", "potted_blue_orchid", "potted_allium", "potted_dandelion"};

    private static int pot(Random r) { return id("minecraft:" + POTS[r.nextInt(POTS.length)]); }

    /** Patterned rug: border colour around a fill colour, one block above the floor. */
    private static void rug(int x1, int z1, int x2, int z2, int y, String border, String fill) {
        int b = carpet(border), f = carpet(fill);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                put(x, y, z, (x == x1 || x == x2 || z == z1 || z == z2) ? b : f);
    }

    /** Small table (top slab) with something on it. */
    private static void table(int x, int y, int z, String wood, int onTop) {
        putOn(x, y, z, slabTop(wood), onTop);
    }

    /** Chair = stair whose backrest is on {@code back} side (so the sitter faces the other way). */
    private static void chair(int x, int y, int z, String wood, String back) {
        put(x, y, z, stairs(wood, back));
    }

    /** Dining set: a row of tables along X with chairs on both long sides and candles/lanterns on top. */
    private static void diningTable(int x1, int x2, int z, int y, String wood, Random r) {
        for (int x = x1; x <= x2; x++) {
            table(x, y, z, wood, (x - x1) % 2 == 0 ? candle(1 + r.nextInt(3)) : (r.nextBoolean() ? LANTERN : pot(r)));
            chair(x, y, z - 1, wood, "north");
            chair(x, y, z + 1, wood, "south");
        }
    }

    /** Line of shelving 2 high along a wall run: bookshelves, barrels and shelf-trapdoors with pots. */
    private static void shelvesX(int x1, int x2, int z, int y, String facing, Random r) {
        for (int x = x1; x <= x2; x++) {
            int k = r.nextInt(5);
            if (k < 2) { put(x, y, z, BOOKSHELF); put(x, y + 1, z, BOOKSHELF); }
            else if (k == 2) { put(x, y, z, barrel(facing)); put(x, y + 1, z, shelf(facing)); }
            else if (k == 3) { put(x, y, z, BOOKSHELF); put(x, y + 1, z, pot(r)); }
            else { put(x, y, z, barrel("up")); put(x, y + 1, z, candle(1 + r.nextInt(3))); }
        }
    }

    private static void shelvesZ(int z1, int z2, int x, int y, String facing, Random r) {
        for (int z = z1; z <= z2; z++) {
            int k = r.nextInt(5);
            if (k < 2) { put(x, y, z, BOOKSHELF); put(x, y + 1, z, BOOKSHELF); }
            else if (k == 2) { put(x, y, z, barrel(facing)); put(x, y + 1, z, shelf(facing)); }
            else if (k == 3) { put(x, y, z, BOOKSHELF); put(x, y + 1, z, pot(r)); }
            else { put(x, y, z, barrel("up")); put(x, y + 1, z, candle(1 + r.nextInt(3))); }
        }
    }

    /** Crate stack: barrels 1-3 high, facing out, with the odd hay bale. */
    private static void crates(int x, int y, int z, Random r) {
        int h = 1 + r.nextInt(3);
        for (int i = 0; i < h; i++) put(x, y + i, z, r.nextInt(5) == 0 ? HAY : barrel(r.nextBoolean() ? "north" : "east"));
    }

    // ------------------------------------------------------------------
    private static void furnishBuildings() {
        Random r = new Random(0xF00D5EEDL);
        furnishTavern(r);
        furnishInn(r);
        furnishChandlery(r);
        furnishBakery(r);
        furnishHarbormaster(r);
        furnishWarehouse(-70, -38, 60, 84, r);
        furnishWarehouse(-128, -98, 58, 80, r);
        furnishFishMarket(r);
        furnishDockOffice(r);
        furnishChapel(r);
        furnishManor(r);
        furnishGuardhouse(r);
        furnishSmithy(r);
        furnishShipwrightHall(r);
    }

    /** "The Grog Barrel": x48..76, z16..44, floors at 67 / 72. Door west (z29-30), stairs along z42. */
    private static void furnishTavern(Random r) {
        int gy = 67;
        keepClear(49, 27, 53, 32);          // front door approach
        keepClear(50, 40, 59, 43);          // staircase run + landing
        keepClear(73, 18, 74, 42);          // bartender's lane behind the bar
        // candles replace the pressure-plate table tops; big rug under the tables
        int[][] tables = {{52, 24}, {52, 32}, {56, 28}, {62, 24}, {62, 34}, {66, 28}};
        for (int[] t : tables) set(t[0], gy + 2, t[1], candle(1 + r.nextInt(3)));
        rug(54, 21, 68, 37, gy + 1, "brown", "red");
        // bar stools + bottles on the bar top
        for (int z = 23; z <= 35; z += 2) put(71, gy + 1, z, slab("spruce"));
        for (int z = 22; z <= 36; z += 3) set(72, gy + 3, z, r.nextBoolean() ? id("minecraft:decorated_pot") : candle(2));
        // bottle shelves on the wall behind the barrels
        for (int z = 21; z <= 37; z++) put(75, gy + 3, z, z % 3 == 1 ? id("minecraft:brewing_stand") : shelf("west"));
        // fireside: armchairs + side table + rug in front of the hearth
        rug(57, 19, 63, 22, gy + 1, "black", "orange");
        chair(58, gy + 1, 20, "spruce", "south");
        chair(62, gy + 1, 20, "spruce", "south");
        table(60, gy + 1, 21, "spruce", candle(3));
        // trophies & decor: dartboard, skulls, banners, a keg rack by the door
        set(48, gy + 3, 36, id("minecraft:target"));
        put(49, gy + 3, 22, wallSkull("east"));
        put(49, gy + 3, 38, wallSkull("east"));
        put(53, gy + 3, 17, wallBanner("red", "south"));
        put(67, gy + 3, 17, wallBanner("black", "south"));
        for (int z = 19; z <= 25; z++) { put(49, gy + 1, z, barrel("east")); if (z % 2 == 0) put(49, gy + 2, z, barrel("east")); }
        put(49, gy + 1, 26, pot(r)); put(49, gy + 1, 33, pot(r));
        // cellar hatch + stage by the jukebox
        put(55, gy + 1, 39, id("minecraft:spruce_trapdoor[facing=north,half=bottom]"));
        rug(50, 39, 54, 39, gy + 1, "purple", "purple");

        // ---- upstairs: rooms with nightstands, rugs, wardrobes; lounge along the south wall
        int u = 72;
        for (int i = 0; i < 6; i++) {
            int bx = 52 + i * 4;
            put(bx + 1, u + 2, 18, candle(r.nextBoolean() ? "white" : "red", 1));   // on the barrel nightstand
            rug(bx - 1, 20, bx + 1, 21, u + 1, "gray", new String[]{"red", "blue", "green", "cyan", "purple", "white"}[i]);
            put(bx + 2, u + 1, 18, barrel("south"));                               // wardrobe
            put(bx + 2, u + 2, 18, barrel("south"));
            if (i < 5) for (int z = 17; z <= 20; z++) put(bx + 3, u + 1, z, SPRUCE_FENCE);   // room divider
        }
        // lounge: bookshelves on the south wall, sofas + tables, a card table
        shelvesX(61, 73, 43, u + 1, "north", r);
        rug(62, 34, 72, 40, u + 1, "brown", "light_gray");
        for (int x = 63; x <= 71; x += 4) {
            chair(x, u + 1, 37, "dark_oak", "south");
            table(x + 1, u + 1, 37, "dark_oak", r.nextBoolean() ? LANTERN : candle(2));
            chair(x + 2, u + 1, 37, "dark_oak", "south");
        }
        table(66, u + 1, 30, "spruce", id("minecraft:decorated_pot"));
        chair(65, u + 1, 30, "spruce", "west"); chair(67, u + 1, 30, "spruce", "east");
        put(74, u + 1, 26, hammock("east")); put(74, u + 1, 30, hammock("east"));  // cheap berths
    }

    /** "The Salty Siren" inn: x-76..-48, z14..42, floors 67 / 72. Door east (z27-28), stairs along z16. */
    private static void furnishInn(Random r) {
        int gy = 67;
        keepClear(-53, 25, -49, 30);        // door approach
        keepClear(-75, 15, -66, 18);        // stairs + landing
        // lobby: reception desk, waiting area, fireplace
        for (int z = 21; z <= 25; z++) if (z != 24) put(-52, gy + 1, z, slabTop("spruce"));
        put(-52, gy + 2, 22, candle(2));
        put(-50, gy + 1, 21, BARREL_N); put(-50, gy + 2, 21, shelf("west"));
        put(-50, gy + 1, 20, id("minecraft:bell[attachment=floor,facing=east]"));
        rug(-64, 22, -56, 33, gy + 1, "cyan", "light_blue");
        diningTable(-62, -58, 28, gy + 1, "oak", r);
        for (int z = 35; z <= 40; z += 5) { chair(-57, gy + 1, z, "birch", "east"); table(-58, gy + 1, z, "birch", pot(r)); chair(-59, gy + 1, z, "birch", "west"); }
        // fireplace on the west wall with a chimney through the roof
        fill(-75, gy + 1, 26, -75, gy + 3, 30, COBBLE);
        set(-74, gy + 1, 28, CAMPFIRE);
        set(-74, gy + 1, 27, COBBLE); set(-74, gy + 1, 29, COBBLE);
        fill(-75, gy + 4, 28, -75, gy + 12, 28, COBBLE);
        chair(-71, gy + 1, 27, "oak", "east"); chair(-71, gy + 1, 29, "oak", "east");
        rug(-73, 26, -70, 30, gy + 1, "red", "orange");
        shelvesZ(34, 41, -75, gy + 1, "east", r);
        shelvesX(-70, -60, 41, gy + 1, "north", r);
        put(-75, gy + 1, 20, pot(r)); put(-49, gy + 1, 40, pot(r)); put(-49, gy + 1, 16, pot(r));
        // ---- upstairs: 8 rooms (beds at x-71,-65,-59,-53 on both walls): nightstands, rugs, desks
        int u = 72;
        String[] cs = {"white", "light_blue", "red", "lime", "purple", "orange", "cyan", "yellow"};
        for (int i = 0; i < 4; i++) {
            int bx = -71 + i * 6;
            putOn(bx + 1, u + 1, 16, BARREL_UP, candle(1));
            putOn(bx + 1, u + 1, 40, BARREL_UP, candle(1));
            rug(bx - 1, 18, bx + 1, 19, u + 1, "white", cs[i]);
            rug(bx - 1, 37, bx + 1, 38, u + 1, "white", cs[i + 4]);
            table(bx - 2, u + 1, 16, "birch", pot(r));
            table(bx - 2, u + 1, 40, "birch", LANTERN);
            // partitions between rooms
            for (int z = 15; z <= 19; z++) put(bx + 3, u + 1, z, BIRCH);
            for (int z = 37; z <= 41; z++) put(bx + 3, u + 1, z, BIRCH);
        }
        // corridor runner
        rug(-72, 25, -50, 31, u + 1, "blue", "light_blue");
    }

    /** Chandlery (ship supplies): x-112..-86, z14..36, floor 67. Door east z25. */
    private static void furnishChandlery(Random r) {
        int gy = 67;
        keepClear(-90, 23, -87, 27);
        // sales counter across the room with goods on it
        for (int z = 19; z <= 31; z++) if (z != 25) put(-94, gy + 1, z, slabTop("spruce"));
        for (int z = 19; z <= 31; z += 3) put(-94, gy + 2, z, r.nextBoolean() ? LANTERN : id("minecraft:decorated_pot"));
        // wall of goods: rope, sailcloth, lanterns, chains
        shelvesX(-110, -96, 35, gy + 1, "north", r);
        for (int x = -110; x <= -100; x += 2) { put(x, gy + 1, 15, WHITE_WOOL); put(x, gy + 2, 15, WHITE_WOOL); } // bolts of sailcloth
        for (int x = -109; x <= -101; x += 2) put(x, gy + 1, 15, HAY);
        put(-104, gy + 1, 26, id("pixelpirates:anchor_block"));
        put(-104, gy + 1, 22, id("pixelpirates:ship_helm[facing=east]"));
        put(-100, gy + 1, 30, id("pixelpirates:ship_cannon[facing=east,loaded=false]"));
        crates(-110, gy + 1, 28, r); crates(-110, gy + 1, 30, r); crates(-109, gy + 1, 32, r);
        for (int x = -108; x <= -98; x += 3) { put(x, gy + 4, 20, CHAIN); put(x, gy + 3, 20, LANTERN_HANGING); }
        rug(-102, 21, -97, 29, gy + 1, "brown", "yellow");
    }

    /** Bakery: x86..112, z16..38, floor 67. Door west z26. */
    private static void furnishBakery(Random r) {
        int gy = 67;
        keepClear(87, 24, 90, 28);
        // display counter with cakes and bread "loaves" (hay), flour sacks, kneading tables
        for (int z = 20; z <= 32; z++) if (z != 26) put(93, gy + 1, z, slabTop("birch"));
        for (int z = 20; z <= 32; z += 3) put(93, gy + 2, z, r.nextBoolean() ? id("minecraft:cake") : candle("yellow", 2));
        for (int x = 98; x <= 104; x++) { put(x, gy + 1, 36, WHITE_WOOL); if (x % 2 == 0) put(x, gy + 2, 36, WHITE_WOOL); }
        put(105, gy + 1, 36, COMPOSTER);
        for (int x = 97; x <= 105; x += 4) { put(x, gy + 1, 24, CRAFTING); put(x + 1, gy + 1, 24, slabTop("oak")); put(x + 1, gy + 2, 24, pot(r)); }
        for (int z = 26; z <= 34; z += 2) crates(111, gy + 1, z, r);
        put(107, gy + 1, 17, CAULDRON_WATER);
        put(106, gy + 1, 17, barrel("south"));
        shelvesZ(18, 22, 111, gy + 1, "west", r);
        rug(96, 27, 104, 32, gy + 1, "orange", "white");
        diningTable(98, 102, 30, gy + 1, "oak", r);
    }

    /** Harbormaster: x-28..-12, z60..76, floors 65 / 69. Door west z68, stairs along z62. */
    private static void furnishHarbormaster(Random r) {
        int gy = 65;
        keepClear(-27, 66, -24, 70);
        keepClear(-19, 61, -13, 63);
        // office: chart table, filing shelves, map wall, a ship's wheel trophy
        table(-20, gy + 1, 70, "dark_oak", id("pixelpirates:map_block"));
        put(-21, gy + 1, 70, CARTOGRAPHY);
        chair(-20, gy + 1, 72, "spruce", "south");
        shelvesX(-26, -14, 75, gy + 1, "north", r);
        for (int z = 64; z <= 73; z += 3) put(-13, gy + 2, z, id("pixelpirates:map_block"));
        put(-13, gy + 1, 66, id("pixelpirates:ship_helm[facing=west]"));
        rug(-24, 66, -16, 73, gy + 1, "blue", "light_blue");
        put(-27, gy + 1, 74, pot(r)); put(-13, gy + 1, 74, pot(r));
        // upstairs: harbormaster's quarters
        int u = 69;
        put(-26, u + 1, 64, bed("blue", "south", true)); put(-26, u + 1, 65, bed("blue", "south", false));
        putOn(-25, u + 1, 64, BARREL_UP, candle(1));
        table(-16, u + 1, 72, "dark_oak", LANTERN);
        chair(-16, u + 1, 71, "dark_oak", "north");
        put(-14, u + 1, 72, id("pixelpirates:pirate_diary_block"));
        shelvesZ(66, 72, -27, u + 1, "east", r);
        rug(-24, 66, -17, 72, u + 1, "blue", "white");
    }

    /** Warehouse interiors: storage racks (fence posts + slab shelves) in aisles, pallets, hoists. */
    private static void furnishWarehouse(int x1, int x2, int z1, int z2, Random r) {
        int gy = 65, mid = (x1 + x2) / 2;
        keepClear(mid - 2, z2 - 6, mid + 2, z2);      // loading door lane
        keepClear(mid - 1, z1 + 1, mid + 1, z2);      // central aisle
        for (int x = x1 + 3; x <= x2 - 3; x += 5) {
            if (Math.abs(x - mid) <= 2) continue;
            for (int z = z1 + 3; z <= z2 - 8; z++) {
                boolean post = (z - z1) % 4 == 3;
                if (post) { put(x, gy + 1, z, SPRUCE_FENCE); put(x, gy + 2, z, SPRUCE_FENCE); put(x, gy + 3, z, slabTop("spruce")); }
                else {
                    put(x, gy + 1, z, r.nextInt(4) == 0 ? HAY : barrel("east"));
                    put(x, gy + 2, z, slabTop("spruce"));
                    if (r.nextBoolean()) put(x, gy + 3, z, barrel("up"));
                }
            }
        }
        // pallets by the door + a hoist chain with a hanging crate
        for (int x = mid - 5; x <= mid + 5; x++) if (Math.abs(x - mid) > 2) put(x, gy + 1, z2 - 3, slab("spruce"));
        for (int y = gy + 3; y <= gy + 6; y++) put(mid, y, z2 - 8, CHAIN);
        put(mid, gy + 2, z2 - 8, barrel("up"));
    }

    private static void furnishFishMarket(Random r) {
        int gy = 65;
        // ice for the catch, nets (cobweb-free: white carpet "nets" on counters), cod barrels, scales
        for (int x = 12; x <= 32; x += 4) { put(x, gy + 2, 63, id("minecraft:packed_ice")); put(x + 1, gy + 2, 63, BARREL_UP); }
        for (int x = 12; x <= 32; x += 4) { put(x, gy + 2, 71, id("minecraft:blue_ice")); put(x + 2, gy + 2, 71, carpet("white")); }
        put(22, gy + 1, 67, CAULDRON_WATER);
        put(28, gy + 1, 67, COMPOSTER);
        put(16, gy + 1, 67, id("minecraft:smoker[facing=south]"));
    }

    /** Dock office: x102..116, z58..72, floor 65. Door west z65. */
    private static void furnishDockOffice(Random r) {
        int gy = 65;
        keepClear(103, 63, 106, 67);
        table(108, gy + 1, 62, "spruce", LANTERN);
        chair(108, gy + 1, 63, "spruce", "south");
        table(109, gy + 1, 62, "spruce", id("pixelpirates:map_block"));
        shelvesX(104, 114, 71, gy + 1, "north", r);
        shelvesZ(60, 69, 115, gy + 1, "west", r);
        rug(107, 64, 113, 69, gy + 1, "gray", "light_gray");
        put(103, gy + 1, 70, pot(r));
    }

    /** Chapel: x-36..-14, z-80..-56, floor 70. Door south (x-26..-25), altar at the north. */
    private static void furnishChapel(Random r) {
        int gy = 70;
        keepClear(-27, -60, -24, -57);
        // red aisle runner to the altar, candles on every pew end, altar cloth, organ pipes
        for (int z = -77; z <= -57; z++) { put(-26, gy + 1, z, carpet("red")); put(-25, gy + 1, z, carpet("red")); }
        for (int z = -74; z <= -63; z += 3) { put(-27, gy + 2, z, candle("white", 2)); put(-23, gy + 2, z, candle("white", 2)); }
        for (int x = -28; x <= -22; x++) put(x, gy + 2, -79, carpet("white"));
        for (int x = -34; x <= -30; x++) { int h = 3 + Math.abs(x + 32); for (int y = gy + 1; y <= gy + h; y++) put(x, y, -79, id("minecraft:quartz_pillar[axis=y]")); }
        for (int x = -20; x <= -16; x++) { int h = 3 + Math.abs(x + 18); for (int y = gy + 1; y <= gy + h; y++) put(x, y, -79, id("minecraft:quartz_pillar[axis=y]")); }
        put(-29, gy + 1, -78, id("minecraft:note_block")); put(-21, gy + 1, -78, id("minecraft:note_block"));
        put(-35, gy + 1, -78, pot(r)); put(-15, gy + 1, -78, pot(r));
        put(-35, gy + 1, -58, pot(r)); put(-15, gy + 1, -58, pot(r));
        put(-30, gy + 3, -79, wallBanner("light_blue", "south")); put(-20, gy + 3, -79, wallBanner("light_blue", "south"));
    }

    /** Manor: x14..46, z-82..-54, floors 70 / 75. Grand door south (x29-30); stairs along z-80 (east). */
    private static void furnishManor(Random r) {
        int gy = 70;
        keepClear(27, -58, 32, -55);
        keepClear(38, -81, 45, -78);
        // great hall: the long table gets chairs + candelabras, a grand rug
        set(24, gy + 2, -68, candle(3)); set(29, gy + 2, -68, LANTERN); set(34, gy + 2, -68, candle(3));
        for (int x = 25; x <= 33; x += 2) { set(x, gy + 2, -68, candle(1 + r.nextInt(3))); }
        for (int x = 24; x <= 34; x++) { chair(x, gy + 1, -69, "dark_oak", "north"); chair(x, gy + 1, -67, "dark_oak", "south"); }
        rug(21, -72, 37, -64, gy + 1, "red", "black");
        // fireplace on the east wall
        fill(45, gy + 1, -70, 45, gy + 4, -66, STONE_BRICKS);
        set(44, gy + 1, -68, CAMPFIRE); set(44, gy + 1, -69, STONE_BRICKS); set(44, gy + 1, -67, STONE_BRICKS);
        set(44, gy + 3, -68, wallSkull("west"));
        fill(45, gy + 5, -68, 45, gy + 13, -68, STONE_BRICKS);
        // study (north-west): bookshelves already on the west wall; add desk, globe (map), armchair
        table(18, gy + 1, -78, "dark_oak", LANTERN);
        put(17, gy + 1, -78, id("pixelpirates:pirate_diary_block"));
        put(19, gy + 1, -78, id("pixelpirates:map_block"));
        chair(18, gy + 1, -77, "dark_oak", "south");
        shelvesX(16, 26, -81, gy + 1, "south", r);
        rug(16, -79, 24, -74, gy + 1, "green", "lime");
        // treasure vault nook + trophy weapons
        put(43, gy + 1, -62, id("pixelpirates:treasure_block"));
        put(43, gy + 2, -62, id("pixelpirates:emerald_token"));
        put(44, gy + 1, -60, id("pixelpirates:sword_block"));
        for (int x = 16; x <= 44; x += 7) put(x, gy + 1, -55, pot(r));
        // upstairs: master bedroom + gallery
        int u = 75;
        put(20, u + 1, -80, bed("red", "south", true)); put(20, u + 1, -79, bed("red", "south", false));
        put(21, u + 1, -80, bed("red", "south", true)); put(21, u + 1, -79, bed("red", "south", false));
        putOn(19, u + 1, -80, BARREL_UP, candle("red", 2)); putOn(22, u + 1, -80, BARREL_UP, candle("red", 2));
        rug(17, -78, 25, -72, u + 1, "red", "pink");
        shelvesZ(-77, -60, 15, u + 1, "east", r);
        for (int x = 26; x <= 36; x += 5) { table(x, u + 1, -62, "dark_oak", pot(r)); chair(x - 1, u + 1, -62, "dark_oak", "west"); }
        for (int z = -78; z <= -60; z += 3) put(45, u + 2, z, wallBanner(z % 2 == 0 ? "red" : "black", "west"));
        rug(28, -72, 40, -66, u + 1, "black", "gray");
    }

    /** Guardhouse: x-28..-10, z-38..-26, floor 70. Door north x-19. Bunks at the west end (z-36/-34). */
    private static void furnishGuardhouse(Random r) {
        int gy = 70;
        keepClear(-21, -37, -17, -35);
        // hammock berths, weapon rack, training dummy, map table, lockers
        put(-22, gy + 1, -28, hammock("south")); put(-15, gy + 1, -28, hammock("south"));
        table(-13, gy + 1, -34, "spruce", id("pixelpirates:map_block"));
        chair(-14, gy + 1, -34, "spruce", "west");
        for (int x = -26; x <= -23; x++) put(x, gy + 1, -27, x % 2 == 0 ? GRINDSTONE : barrel("north"));
        put(-12, gy + 1, -30, HAY); put(-12, gy + 2, -30, HAY); put(-12, gy + 3, -30, id("minecraft:carved_pumpkin[facing=west]"));
        put(-11, gy + 1, -31, id("minecraft:target"));
        put(-11, gy + 3, -28, wallBanner("black", "west"));
        rug(-25, -33, -17, -30, gy + 1, "gray", "black");
    }

    /** Smithy: x54..76, z-44..-24, floor 70. Door south x65. */
    private static void furnishSmithy(Random r) {
        int gy = 70;
        keepClear(63, -27, 67, -25);
        // coal + iron stock, quench trough, tool wall, extra anvils
        put(55, gy + 1, -39, id("minecraft:coal_block")); put(55, gy + 1, -38, id("minecraft:coal_block")); put(55, gy + 2, -39, id("minecraft:coal_block"));
        put(56, gy + 1, -39, id("minecraft:iron_block"));
        for (int x = 70; x <= 74; x++) put(x, gy + 1, -40, CAULDRON_WATER);
        for (int x = 58; x <= 74; x++) put(x, gy + 3, -43, shelf("south"));
        put(62, gy + 1, -36, id("minecraft:chipped_anvil[facing=east]"));
        put(66, gy + 1, -36, ANVIL_N);
        put(64, gy + 1, -32, id("minecraft:blast_furnace[facing=south]"));
        for (int z = -38; z <= -28; z += 2) crates(75, gy + 1, z, r);
        put(55, gy + 1, -26, id("minecraft:fletching_table"));
        put(56, gy + 1, -26, id("minecraft:loom"));
        for (int y = gy + 2; y <= gy + 4; y++) put(62, y, -30, CHAIN);
        put(62, gy + 1, -30, id("minecraft:iron_bars"));
        rug(58, -34, 70, -28, gy + 1, "black", "gray");
    }

    /** Shipwright hall: x58..88, z60..82, floor 65. Slip doors south (x63-67, x77-81). */
    private static void furnishShipwrightHall(Random r) {
        int gy = 65;
        keepClear(62, 76, 68, 82);
        keepClear(76, 76, 82, 82);
        // drafting corner: tables with maps, blueprint wall, stools
        for (int x = 60; x <= 66; x += 3) { table(x, gy + 1, 72, "spruce", id("pixelpirates:map_block")); chair(x, gy + 1, 73, "spruce", "south"); }
        for (int x = 59; x <= 69; x++) put(x, gy + 3, 61, x % 2 == 0 ? id("pixelpirates:map_block") : shelf("south"));
        // sail loft: stacked canvas + rope coils, spare masts, a helm and cannon waiting to be fitted
        for (int x = 80; x <= 86; x++) { put(x, gy + 1, 66, WHITE_WOOL); if (x % 2 == 0) put(x, gy + 2, 66, WHITE_WOOL); }
        for (int x = 80; x <= 86; x += 2) put(x, gy + 1, 68, HAY);
        for (int z = 62; z <= 70; z++) put(70, gy + 1, z, id("pixelpirates:ship_mast[axis=z]"));
        put(74, gy + 1, 68, id("pixelpirates:ship_helm[facing=south]"));
        put(76, gy + 1, 68, id("pixelpirates:ship_cannon[facing=south,loaded=false]"));
        put(78, gy + 1, 68, id("pixelpirates:ship_cannon[facing=south,loaded=false]"));
        for (int z = 64; z <= 74; z += 5) crates(59, gy + 1, z, r);
        rug(71, 71, 79, 74, gy + 1, "brown", "orange");
    }

    // ------------------------------------------------------------------
    // Townhouses - each gets a randomized, themed layout
    // ------------------------------------------------------------------

    /**
     * Furnishes one townhouse. Existing fixtures (house()): bed at x1+2..x1+3 on row z1+2, barrel at
     * (x2-2, z2-2), crafting table at (x1+2, z2-2), ladder at (x2-1, z1+1) when two-story.
     */
    private static void furnishHouse(int x1, int z1, int x2, int z2, int gy, boolean doorNorth, boolean twoStory, int styleIdx) {
        Random r = new Random(x1 * 7919L + z1 * 104729L + styleIdx);
        int doorX = (x1 + x2) / 2;
        int doorZ = doorNorth ? z1 : z2;
        int in = doorNorth ? 1 : -1;                 // step from the door into the house
        keepClear(doorX - 1, doorZ + in, doorX + 1, doorZ + 3 * in);
        if (twoStory) keepClear(x2 - 2, z1 + 1, x2 - 1, z1 + 2);
        int backZ = doorNorth ? z2 - 1 : z1 + 1;     // wall row opposite the door
        String backFacing = doorNorth ? "north" : "south";
        String[] woods = {"spruce", "oak", "dark_oak", "birch", "spruce"};
        String wood = woods[styleIdx % woods.length];
        String[][] rugs = {{"brown", "red"}, {"blue", "light_blue"}, {"green", "lime"}, {"gray", "white"},
                {"purple", "magenta"}, {"orange", "yellow"}, {"cyan", "light_blue"}};
        String[] rc = rugs[r.nextInt(rugs.length)];
        int theme = r.nextInt(5);                    // 0 sailor, 1 fisher, 2 scholar, 3 merchant, 4 family

        // nightstand + wardrobe beside the bed (bed occupies x1+2..x1+3 on row z1+2)
        putOn(x1 + 4, gy + 1, z1 + 2, BARREL_UP, candle(1 + r.nextInt(2)));
        put(x1 + 1, gy + 1, z1 + 4, barrel("east")); put(x1 + 1, gy + 2, z1 + 4, barrel("east"));
        // kitchen along the back wall from the east side
        int kx = x2 - 3;
        put(kx, gy + 1, backZ, id("minecraft:smoker[facing=" + (doorNorth ? "north" : "south") + "]"));
        put(kx - 1, gy + 1, backZ, CAULDRON_WATER);
        put(kx - 2, gy + 1, backZ, barrel(backFacing));
        put(kx - 2, gy + 2, backZ, shelf(backFacing));
        put(kx - 1, gy + 3, backZ, shelf(backFacing));
        // dining in the middle on a rug
        int cx = (x1 + x2) / 2 + 1, cz = (z1 + z2) / 2;
        rug(cx - 2, cz - 1, cx + 2, cz + 2, gy + 1, rc[0], rc[1]);
        table(cx, gy + 1, cz, wood, r.nextBoolean() ? candle(2 + r.nextInt(2)) : LANTERN);
        table(cx + 1, gy + 1, cz, wood, pot(r));
        chair(cx, gy + 1, cz - 1, wood, "north"); chair(cx + 1, gy + 1, cz + 1, wood, "south");
        // themed corner
        int tx = x1 + 1, tz = doorNorth ? z2 - 3 : z1 + 5;
        switch (theme) {
            case 0 -> { put(tx, gy + 1, tz, id("pixelpirates:map_block")); put(tx, gy + 2, tz, id("pixelpirates:anchor_block"));
                        put(tx, gy + 1, tz + 1, CARTOGRAPHY); }
            case 1 -> { put(tx, gy + 1, tz, BARREL_UP); put(tx, gy + 1, tz + 1, BARREL_UP); put(tx, gy + 2, tz, carpet("white"));
                        put(tx, gy + 1, tz - 1, COMPOSTER); }
            case 2 -> { put(tx, gy + 1, tz, BOOKSHELF); put(tx, gy + 2, tz, BOOKSHELF); put(tx, gy + 1, tz + 1, BOOKSHELF);
                        put(tx, gy + 2, tz + 1, candle(3)); put(tx + 1, gy + 1, tz, LECTERN_E); }
            case 3 -> { put(tx, gy + 1, tz, chest("east")); put(tx, gy + 1, tz + 1, chest("east"));
                        put(tx, gy + 2, tz, id("pixelpirates:emerald_token")); put(tx, gy + 2, tz + 1, id("minecraft:decorated_pot")); }
            default -> { put(tx, gy + 1, tz, id("minecraft:loom")); put(tx, gy + 1, tz + 1, pot(r));
                         put(tx + 1, gy + 1, tz + 1, id("minecraft:note_block")); }
        }
        put(x2 - 1, gy + 1, doorNorth ? z2 - 1 : z1 + 1, pot(r));
        put(x1 + 1, gy + 1, doorNorth ? z1 + 1 : z2 - 1, pot(r));
        // loft: second bedroom / study
        if (twoStory) {
            int u = gy + 4;
            put(x1 + 2, u + 1, z2 - 2, bed(rc[1], "north", true)); put(x1 + 2, u + 1, z2 - 1, bed(rc[1], "north", false));
            putOn(x1 + 3, u + 1, z2 - 2, BARREL_UP, candle(1));
            table(x1 + 2, u + 1, z1 + 2, wood, LANTERN);
            chair(x1 + 3, u + 1, z1 + 2, wood, "east");
            shelvesX(x1 + 4, x2 - 3, backZ == z2 - 1 ? z2 - 1 : z1 + 1, u + 1, backFacing, r);
            rug(x1 + 2, cz - 1, x2 - 3, cz + 1, u + 1, rc[1], rc[0]);
        }
    }

    // ------------------------------------------------------------------
    // Preview renderer (standalone; no Minecraft required)
    // ------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("slice")) { slice(args); return; }
        long t0 = System.currentTimeMillis();
        ensureBuilt();
        long t1 = System.currentTimeMillis();
        int nonEmpty = 0;
        for (short v : grid) if (v != 0) nonEmpty++;
        for (int i = 0; i < FOOTPRINTS.size(); i++)
            for (int j = i + 1; j < FOOTPRINTS.size(); j++) {
                int[] a = FOOTPRINTS.get(i), b = FOOTPRINTS.get(j);
                if (a[0] <= b[2] && b[0] <= a[2] && a[1] <= b[3] && b[1] <= a[3])
                    System.out.println("OVERLAP: " + java.util.Arrays.toString(a) + " x " + java.util.Arrays.toString(b));
            }
        System.out.println("Layout built in " + (t1 - t0) + " ms; palette=" + PALETTE.size()
                + " entries; placed=" + nonEmpty + " cells; loot chests=" + LOOT.size());

        int scale = 2;
        int imgW = W * scale, imgH = D * scale;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(imgW, imgH, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = X0; x <= X1; x++) {
            for (int z = Z0; z <= Z1; z++) {
                int rgb = columnColor(x, z);
                for (int dx = 0; dx < scale; dx++)
                    for (int dz = 0; dz < scale; dz++)
                        img.setRGB((x - X0) * scale + dx, (z - Z0) * scale + dz, rgb);
            }
        }
        java.io.File out = new java.io.File(args.length > 0 ? args[0] : "port_city_preview.png");
        javax.imageio.ImageIO.write(img, "png", out);
        System.out.println("Preview written to " + out.getAbsolutePath());
    }

    /** Floor plan: slice y x1 z1 x2 z2 out.png - furniture layer (y) over the floor (y-1), 8px per block. */
    private static void slice(String[] a) throws Exception {
        ensureBuilt();
        int y = Integer.parseInt(a[1]), x1 = Integer.parseInt(a[2]), z1 = Integer.parseInt(a[3]);
        int x2 = Integer.parseInt(a[4]), z2 = Integer.parseInt(a[5]);
        int s = 8;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage((x2 - x1 + 1) * s, (z2 - z1 + 1) * s, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                int top = get(x, y, z), floor = get(x, y - 1, z);
                boolean empty = top == 0 || top == AIR;
                int col = empty ? (floor > 0 && floor != AIR ? paletteColor(PALETTE.get(floor - 1), 60) : 0x202020)
                                : paletteColor(PALETTE.get(top - 1), 110);
                g.setColor(new java.awt.Color(col));
                g.fillRect((x - x1) * s, (z - z1) * s, s, s);
                if (!empty) { g.setColor(new java.awt.Color(0, 0, 0, 90)); g.drawRect((x - x1) * s, (z - z1) * s, s - 1, s - 1); }
                if (reserved(x, z)) { g.setColor(new java.awt.Color(255, 255, 0, 70)); g.fillRect((x - x1) * s, (z - z1) * s, s, s); }
            }
        javax.imageio.ImageIO.write(img, "png", new java.io.File(a[6]));
    }

    private static int columnColor(int x, int z) {
        // topmost placed block wins
        for (int y = Y1; y >= Y0; y--) {
            int v = grid[idx(x, y, z)];
            if (v != 0 && v != AIR) {
                if (v == WATER) break; // fall through to terrain water shading
                return paletteColor(PALETTE.get(v - 1), y);
            }
        }
        // terrain fallback
        double surf = SpawnIslandTerrain.islandSurfaceY(x, z);
        double r = SpawnIslandTerrain.radius(x, z);
        if (r >= 1.30) return rgb(24, 48, 110);
        if (surf < SpawnIslandTerrain.SEA_LEVEL - 0.2) {
            double depth = SpawnIslandTerrain.SEA_LEVEL - surf;
            int b = (int) Math.max(90, 190 - depth * 12);
            return rgb(30, 80, b);
        }
        if (surf < 65.2) return rgb(215, 200, 150); // beach sand
        double shade = Math.min(1.0, (surf - 64) / 30.0);
        return rgb((int) (90 - 30 * shade), (int) (160 - 60 * shade), (int) (70 - 25 * shade));
    }

    private static int paletteColor(String desc, int y) {
        int base;
        if (desc.contains("water")) base = rgb(40, 90, 190);
        else if (desc.contains("dark_oak") || desc.contains("dark_pirate")) base = rgb(80, 55, 30);
        else if (desc.contains("spruce")) base = rgb(110, 80, 48);
        else if (desc.contains("birch")) base = rgb(200, 185, 140);
        else if (desc.contains("pirate_planks") || desc.contains("palm_planks")) base = rgb(150, 110, 65);
        else if (desc.contains("oak") || desc.contains("barrel") || desc.contains("planks")) base = rgb(160, 130, 80);
        else if (desc.contains("chiseled") || desc.contains("diorite")) base = rgb(215, 215, 215);
        else if (desc.contains("blackstone") || desc.contains("black_wool") || desc.contains("black_banner")) base = rgb(30, 30, 34);
        else if (desc.contains("stone_brick") || desc.contains("andesite") || desc.contains("cobble")) base = rgb(140, 140, 140);
        else if (desc.contains("red_terracotta") || desc.contains("red_wool")) base = rgb(180, 60, 50);
        else if (desc.contains("white")) base = rgb(235, 235, 235);
        else if (desc.contains("sandstone") || desc.contains("sand")) base = rgb(220, 205, 150);
        else if (desc.contains("gravel")) base = rgb(130, 125, 120);
        else if (desc.contains("dirt_path")) base = rgb(150, 120, 70);
        else if (desc.contains("grass_block")) base = rgb(95, 160, 70);
        else if (desc.contains("leaves")) base = rgb(50, 130, 45);
        else if (desc.contains("lantern") || desc.contains("glowstone")) base = rgb(255, 220, 120);
        else if (desc.contains("sea_lantern")) base = rgb(190, 235, 225);
        else if (desc.contains("glass")) base = rgb(190, 220, 235);
        else if (desc.contains("wool") || desc.contains("carpet")) base = colorFromWool(desc);
        else if (desc.contains("candle")) base = rgb(255, 240, 180);
        else if (desc.contains("_bed")) base = rgb(200, 40, 200);
        else if (desc.contains("potted") || desc.contains("decorated_pot")) base = rgb(170, 90, 60);
        else if (desc.contains("hay")) base = rgb(210, 180, 60);
        else if (desc.contains("pumpkin")) base = rgb(220, 130, 30);
        else if (desc.contains("melon")) base = rgb(110, 170, 60);
        else if (desc.contains("chain") || desc.contains("iron_bars") || desc.contains("anvil")) base = rgb(90, 95, 105);
        else if (desc.contains("pixelpirates:")) base = rgb(170, 120, 90);
        else base = rgb(170, 130, 110);
        // subtle height shading
        double f = 0.75 + 0.25 * Math.min(1.0, (y - 60) / 45.0);
        return rgb((int) (((base >> 16) & 0xFF) * f), (int) (((base >> 8) & 0xFF) * f), (int) ((base & 0xFF) * f));
    }

    private static int colorFromWool(String desc) {
        if (desc.contains("light_blue")) return rgb(110, 170, 220);
        if (desc.contains("light_gray")) return rgb(160, 160, 155);
        if (desc.contains("brown")) return rgb(110, 75, 45);
        if (desc.contains("black")) return rgb(30, 30, 34);
        if (desc.contains("gray")) return rgb(80, 80, 85);
        if (desc.contains("pink")) return rgb(230, 150, 170);
        if (desc.contains("green")) return rgb(80, 110, 40);
        if (desc.contains("red")) return rgb(180, 60, 50);
        if (desc.contains("cyan")) return rgb(60, 150, 160);
        if (desc.contains("yellow")) return rgb(220, 200, 60);
        if (desc.contains("lime")) return rgb(120, 190, 50);
        if (desc.contains("purple")) return rgb(130, 70, 170);
        if (desc.contains("orange")) return rgb(220, 130, 40);
        if (desc.contains("blue")) return rgb(55, 75, 170);
        if (desc.contains("magenta")) return rgb(190, 80, 180);
        return rgb(230, 230, 230);
    }

    private static int rgb(int r, int g, int b) {
        return (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }
}
