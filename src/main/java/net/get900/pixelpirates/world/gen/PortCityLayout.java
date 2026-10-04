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

    // ------------------------------------------------------------------ IN-GAME EDITS (/ppisland capture, world/gen/IslandEdits)
    /** A block changed by hand in game: the state (BlockArgumentParser syntax) and its block-entity data (SNBT) or null. */
    public record Edit(int x, int y, int z, String state, String nbt) {}

    /** Building name -> its hand edits, applied over the plan at the very end of build(). */
    private static final Map<String, List<Edit>> EDITS = new java.util.LinkedHashMap<>();
    /** Cell -> the plan's own id there before an edit replaced it (what a capture compares against). */
    private static final Map<Integer, Integer> EDIT_BASE = new HashMap<>();
    private static final Map<Integer, String> NBT = new HashMap<>();
    /** The shipped edits (committed to the repo) and the local file the game writes (set by the mod: <game dir>/...). */
    public static final String EDITS_RESOURCE = "/data/pixelpirates/island/edits.txt";
    public static java.nio.file.Path LOCAL_EDITS;

    /** Block-entity data for a cell: a hand edit's, else the plan's own (sign text the layout writes). */
    public static String nbtAt(int x, int y, int z) {
        int i = idx(x, y, z);
        String e = NBT.get(i);
        return e != null ? e : PLAN_NBT.get(i);
    }

    /** The plan's own block-entity data (without hand edits) - what a capture compares against. */
    public static String planNbtAt(int x, int y, int z) { return PLAN_NBT.get(idx(x, y, z)); }

    /** Block-entity data the layout itself writes (sign text...), applied by the feature + restamp like a hand edit's. */
    private static final Map<Integer, String> PLAN_NBT = new HashMap<>();

    private static void nbt(int x, int y, int z, String snbt) {
        if (x < X0 || x > X1 || y < Y0 || y > Y1 || z < Z0 || z > Z1) return;
        PLAN_NBT.put(idx(x, y, z), snbt);
    }

    /** Sign / hanging-sign text (up to 4 lines, both sides), in exactly the form the game saves it, so captures match. */
    private static void signText(int x, int y, int z, String... lines) {
        StringBuilder m = new StringBuilder("messages:[");
        for (int i = 0; i < 4; i++) {
            String t = i < lines.length ? lines[i].replace("\\", "").replace("\"", "").replace("'", "") : "";
            m.append(i > 0 ? "," : "").append("'{\"text\":\"").append(t).append("\"}'");
        }
        m.append("]");
        String side = "{color:\"black\",has_glowing_text:0b," + m + "}";
        nbt(x, y, z, "{back_text:" + side + ",front_text:" + side + ",is_waxed:0b}");
    }

    /** The plan without any hand edit (0 = untouched terrain). */
    public static int planGet(int x, int y, int z) {
        ensureBuilt();
        if (x < X0 || x > X1 || y < Y0 || y > Y1 || z < Z0 || z > Z1) return 0;
        Integer b = EDIT_BASE.get(idx(x, y, z));
        return b != null ? b : grid[idx(x, y, z)];
    }

    public static Map<String, List<Edit>> edits() { ensureBuilt(); return java.util.Collections.unmodifiableMap(EDITS); }

    /** The building a column belongs to: the smallest labelled box holding it, or null. */
    public static String ownerOf(int x, int z) {
        String best = null;
        long area = Long.MAX_VALUE;
        for (var e : LABELS.entrySet()) {
            int[] b = e.getValue();
            if (x < b[0] || x > b[2] || z < b[1] || z > b[3]) continue;
            long a = (long) (b[2] - b[0] + 1) * (b[3] - b[1] + 1);
            if (a < area) { area = a; best = e.getKey(); }
        }
        return best;
    }

    /** Replace one building's hand edits (empty list = drop them): the grid is updated at once and the edit files rewritten. */
    public static synchronized void setEdits(String building, List<Edit> edits, List<java.nio.file.Path> writeTo) throws java.io.IOException {
        ensureBuilt();
        for (Edit e : EDITS.getOrDefault(building, List.of())) {             // put the plan back under the old edits
            int i = idx(e.x(), e.y(), e.z());
            Integer b = EDIT_BASE.remove(i);
            if (b != null) grid[i] = (short) (int) b;
            NBT.remove(i);
        }
        if (edits.isEmpty()) EDITS.remove(building); else EDITS.put(building, new ArrayList<>(edits));
        for (Edit e : edits) applyEdit(e);
        saveEdits(writeTo);
    }

    /** Write every section to these files (setEdits with no targets + one save = a batch, e.g. /ppisland capture all). */
    public static synchronized void saveEdits(List<java.nio.file.Path> writeTo) throws java.io.IOException {
        for (java.nio.file.Path p : writeTo) writeEdits(p);
    }

    private static void applyEdit(Edit e) {
        if (e.x() < X0 || e.x() > X1 || e.y() < Y0 || e.y() > Y1 || e.z() < Z0 || e.z() > Z1) return;
        int i = idx(e.x(), e.y(), e.z());
        EDIT_BASE.putIfAbsent(i, (int) grid[i]);
        grid[i] = (short) id(e.state());
        if (e.nbt() != null && !e.nbt().isEmpty()) NBT.put(i, e.nbt()); else NBT.remove(i);
    }

    /** Load the shipped edits, then the local file (a building's section there replaces the shipped one), and apply them. */
    private static void applyEdits() {
        EDITS.clear(); EDIT_BASE.clear(); NBT.clear();
        try (var in = PortCityLayout.class.getResourceAsStream(EDITS_RESOURCE)) {
            if (in != null) readEdits(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception ignored) {}
        try {
            if (LOCAL_EDITS != null && java.nio.file.Files.exists(LOCAL_EDITS)) readEdits(java.nio.file.Files.readString(LOCAL_EDITS));
        } catch (Exception ignored) {}
        for (List<Edit> l : EDITS.values()) for (Edit e : l) applyEdit(e);
    }

    /** "[Building]" starts a section; then one edit per line: x TAB y TAB z TAB state [TAB snbt]. "#" = comment. */
    private static void readEdits(String text) {
        String cur = null;
        List<Edit> list = null;
        for (String line : text.split("\\r?\\n")) {
            if (line.isBlank() || line.startsWith("#")) continue;
            if (line.startsWith("[") && line.endsWith("]")) {
                cur = line.substring(1, line.length() - 1);
                list = new ArrayList<>();
                EDITS.put(cur, list);
                continue;
            }
            if (list == null) continue;
            String[] f = line.split("\t", 5);
            if (f.length < 4) continue;
            try { list.add(new Edit(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]), f[3], f.length > 4 ? f[4] : null)); }
            catch (NumberFormatException ignored) {}
        }
    }

    private static void writeEdits(java.nio.file.Path p) throws java.io.IOException {
        StringBuilder sb = new StringBuilder("# Pixel Pirates - spawn island hand edits, saved in game with /ppisland capture <building>.\n"
                + "# Applied over PortCityLayout's plan at the end of the build. One [Building] section each; x, y, z, block state, block-entity SNBT.\n");
        for (var e : EDITS.entrySet()) {
            sb.append("\n[").append(e.getKey()).append("]\n");
            for (Edit d : e.getValue())
                sb.append(d.x()).append('\t').append(d.y()).append('\t').append(d.z()).append('\t').append(d.state())
                        .append(d.nbt() != null ? "\t" + d.nbt() : "").append('\n');
        }
        java.nio.file.Files.createDirectories(p.getParent());
        java.nio.file.Files.writeString(p, sb.toString());
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
        if (TRACK) track(idx(x, y, z), blockId);
        grid[idx(x, y, z)] = (short) blockId;
        if (CUR != null && blockId != AIR) {                       // grow the current building's labelled box
            int[] b = LABELS.computeIfAbsent(CUR, k -> new int[]{x, z, x, z});
            b[0] = Math.min(b[0], x); b[1] = Math.min(b[1], z); b[2] = Math.max(b[2], x); b[3] = Math.max(b[3], z);
        }
    }

    // ------------------------------------------------------------------ LayoutCheck support (off in the game)
    /** Set by LayoutCheck before the build: remember which labelled building wrote each cell and every CLASH - a cell one
     *  building placed (not air) that a different writer later replaced. {cellIndex, oldOwner, newOwner, oldId, newId};
     *  owner 0 = an unlabelled pass (streets, greenery, furnishing), else LABEL_ORDER index + 1. */
    static boolean TRACK;
    static short[] OWNER;
    static final List<String> LABEL_ORDER = new ArrayList<>();
    static final List<int[]> CLASHES = new ArrayList<>();

    private static void track(int i, int blockId) {
        if (OWNER == null) OWNER = new short[grid.length];
        int cur = 0;
        String who = CUR != null ? CUR : PASS;
        if (who != null) {
            cur = LABEL_ORDER.indexOf(who) + 1;
            if (cur == 0) { LABEL_ORDER.add(who); cur = LABEL_ORDER.size(); }
        }
        int old = grid[i];
        if (old > 0 && old != AIR && old != blockId && OWNER[i] != 0 && OWNER[i] != cur)
            CLASHES.add(new int[]{i, OWNER[i], cur, old, blockId});
        OWNER[i] = (short) (blockId == AIR ? 0 : cur);
    }

    /** Who the checker blames for an unlabelled pass (a building's furnishing, the city wall, greenery...) - never a map label. */
    private static String PASS;

    private static void pass(String name, Runnable r) {
        String prev = PASS;
        PASS = name;
        r.run();
        PASS = prev;
    }

    /** Cell index -> {x, y, z}. */
    static int[] cell(int i) { return new int[]{i % W + X0, (i / W) % H + Y0, i / (W * H) + Z0}; }

    // ------------------------------------------------------------------ building labels (map + /ppisland restamp)
    /** Name -> {x1, z1, x2, z2}: everything a building function placed while it was the current name. */
    private static final Map<String, int[]> LABELS = new java.util.LinkedHashMap<>();
    private static String CUR;

    private static void named(String name, Runnable build) {
        String prev = CUR;
        CUR = name;
        build.run();
        CUR = prev;
    }

    /** Every labelled building and its footprint {x1, z1, x2, z2}, in map-number order. */
    public static Map<String, int[]> buildings() {
        ensureBuilt();
        return java.util.Collections.unmodifiableMap(LABELS);
    }

    private static void fill(int x1, int y1, int z1, int x2, int y2, int z2, int blockId) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(x, y, z, blockId);
    }

    // ------------------------------------------------------------------ fence / wall / pane / bar connections
    /** Cells the connection passes changed in the last build (LayoutCheck prints it). */
    static int CONNECTED;

    /**
     * The island is written into the chunk without neighbour updates, so a fence/wall/pane/bar keeps exactly the state the
     * layout gave it - a plain "spruce_fence" stands as a lone post beside its neighbours. This works out every such cell's
     * connections from the finished plan (Connections, the game's own rules). Hand-edited cells are redone too: the game
     * only re-joins a fence when a neighbour changes through a block update, and restamps don't send one, so ~6% of the
     * saved edits had arms into air or none against a log. Run before the edits (so EDIT_BASE holds the joined plan) and after.
     */
    private static void connectAll() {
        for (int round = 0; round < 3; round++) {                       // a wall's tall/low sides read the (re-connected) wall above
            boolean[] conn = new boolean[PALETTE.size() + 1];
            for (int p = 0; p < PALETTE.size(); p++) conn[p + 1] = Connections.kind(PALETTE.get(p)) != Connections.Kind.NONE;
            Map<Integer, Integer> changes = new HashMap<>();
            for (int i = 0; i < grid.length; i++) {
                int v = grid[i];
                if (v <= 0 || !conn[v]) continue;
                int[] c = cell(i);
                String now = PALETTE.get(v - 1), want = Connections.connect(now, c[0], c[1], c[2], PortCityLayout::stateAt);
                if (!want.equals(now)) changes.put(i, id(want));
            }
            for (var e : changes.entrySet()) grid[e.getKey()] = (short) (int) e.getValue();     // after the scan: neighbours as planned
            if (round == 0) CONNECTED += changes.size();
            if (changes.isEmpty()) break;
        }
    }

    /** The planned block at a position as a state string; untouched terrain counts as air (connections only ever look
     *  sideways at fence height, where a guess at the ground would join a fence to nothing - it did, 15 times). */
    static String stateAt(int x, int y, int z) {
        int v = getRaw(x, y, z);
        return v <= 0 ? "minecraft:air" : PALETTE.get(v - 1);
    }

    /** Untouched terrain (id 0) below the surface is solid ground. */
    static boolean terrainSolid(int x, int y, int z) {
        if (SpawnIslandTerrain.inCityFootprint(x, z)) return y < SpawnIslandTerrain.cityGroundY(x, z) - 3;
        return y <= Math.round(SpawnIslandTerrain.islandSurfaceY(x, z));
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
        set(x, gy + 4, z, id("pixelpirates:street_lamp[hanging=false,lit=false,waterlogged=false]"));   // lit at dusk by Ginny (homestead/town)
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
        LABELS.clear();
        PLAN_NBT.clear();
        HOUSE_NO = 0;
        UA_LOTS.clear();
        PATH_CELLS.clear();
        initPalette();

        Random rng = new Random(0x9E3779B9L);

        pass("City Paving", () -> paveCityGround());
        pass("Terraces", () -> terracesAndSteps());
        pass("Streets", () -> streets());
        named("Spawn Plaza", PortCityLayout::spawnPlaza);
        named("Terrace Stairs", PortCityLayout::terraceStairs);          // AFTER the paving: streets() used to overwrite each run's top stair (half-finished steps)

        named("Quay & Harbour", PortCityLayout::quayAndHarbor);
        named("Piers", PortCityLayout::piers);
        named("Shipwright Yard", PortCityLayout::shipwrightYard);

        named("Wavebreak Bazaar", PortCityLayout::marketSquare);
        named("Tavern - The Grog Barrel", PortCityLayout::tavern);
        named("Inn", PortCityLayout::inn);
        named("Chandlery", PortCityLayout::chandlery);
        named("Bakery", PortCityLayout::bakery);
        named("Harbourmaster", PortCityLayout::harbormaster);
        warehouses();
        named("Fish Market", PortCityLayout::fishMarket);
        named("Dock Office", PortCityLayout::dockOffice);

        named("Chapel", PortCityLayout::chapel);
        named("Manor", PortCityLayout::manor);
        named("Guardhouse", PortCityLayout::guardhouse);
        named("Smithy", PortCityLayout::smithy);
        named("Park", PortCityLayout::park);
        houseRows(rng);
        furnishBuildings();

        pass("City Wall", () -> cityWall());

        named("Lighthouse", PortCityLayout::lighthouse);
        named("Fort", PortCityLayout::fort);
        pass("North Paths", () -> northPaths());
        pass("Countryside", () -> countryside());
        named("Beach Wreck", PortCityLayout::beachWreck);
        pass("North Downs", () -> northDowns());
        pass("Harbour Boats", () -> harborBoats());
        pass("Buoys", () -> buoys());
        pass("North Palms", () -> northPalms(rng));
        pass("North Meadow", () -> northMeadow(rng));
        pass("Lawn Decor", () -> lawnDecor(rng));
        pass("City Greenery", () -> cityGreenery(rng));
        CONNECTED = 0;
        connectAll();                                                    // fences/walls/panes/bars joined up (see Connections)
        applyEdits();                                                    // LAST: the hand edits saved in game (/ppisland capture)
        connectAll();                                                    // ...and again round the edits
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

    /** The stair runs up each terrace step (avenues only), laid last so no street paving overwrites them. */
    private static void terraceStairs() {
        int zh = SpawnIslandTerrain.TERRACE_HARBOR_Z, zm = SpawnIslandTerrain.TERRACE_MID_Z;
        for (int x = SpawnIslandTerrain.CITY_X_MIN; x <= SpawnIslandTerrain.CITY_X_MAX; x++) {
            if (!isTerraceStairX(x)) continue;
            if (SpawnIslandTerrain.inCityFootprint(x, zh - 1) && SpawnIslandTerrain.inCityFootprint(x, zh)) {   // 65 -> 67
                set(x, 67, zh - 1, stairs("stone_brick", "north"));
                set(x, 66, zh, stairs("stone_brick", "north"));
                set(x, 68, zh - 1, AIR); set(x, 67, zh, AIR);
            }
            if (SpawnIslandTerrain.inCityFootprint(x, zm - 1) && SpawnIslandTerrain.inCityFootprint(x, zm)) {   // 67 -> 70
                set(x, 70, zm - 1, stairs("stone_brick", "north"));
                set(x, 69, zm, stairs("stone_brick", "north"));
                set(x, 68, zm + 1, stairs("stone_brick", "north"));
                set(x, 71, zm - 1, AIR); set(x, 70, zm, AIR); set(x, 69, zm + 1, AIR);
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

    /**
     * QUAY & HARBOUR (rebuilt 2026-10-01, building-by-building overhaul #3). The waterfront, x -92..92, z 82..97, top at
     * y65. West to east: THE SALUTING BATTERY (raised gun platform, crenellated parapet, display cannons, flag), THE CARGO
     * YARD in front of the east warehouse (a treadwheel crane whose jib swings a crate out over the water, crate stacks
     * under tarps, pallets, a hand cart - its door lane x -58..-50 is kept open), a landing stair, the fishermen's net
     * racks, THE ANCHOR PLAZA where the main avenue meets the grand pier (compass-rose paving, a weathered-copper anchor
     * on a plinth, banners flanking the pier), the promenade stalls (rope maker, net mender), a second landing stair and
     * the CUSTOMS HOUSE by the east pier. The sea wall has a barnacled face, buttresses and mooring rings, blackstone
     * bollards and a polished edge walk. The shipwright yard (x >= 52) overwrites the east end.
     */
    private static void quayAndHarbor() {
        final int gy = 65, q1 = 82, q2 = 97;
        int smooth = id("minecraft:smooth_stone");
        int prisBricks = id("minecraft:prismarine_bricks"), darkPris = id("minecraft:dark_prismarine");
        // the quay mass + paving: setts with wear, a kerb line, the polished edge walk and the coping
        for (int x = -92; x <= 92; x++) {
            for (int z = q1; z <= q2; z++) {
                fill(x, 56, z, x, 64, z, z >= 92 ? STONE_BRICKS : COBBLE);
                int h = Math.floorMod(x * 73 + z * 151 + x * z * 7, 23);
                int top = z == q2 ? (Math.floorMod(x, 8) == 0 ? CHISELED_STONE_BRICKS : smooth)
                        : z == 93 ? smooth
                        : z >= 94 ? POLISHED_ANDESITE
                        : h == 0 ? CRACKED_STONE_BRICKS : h == 1 ? MOSSY_STONE_BRICKS : h == 2 ? ANDESITE : STONE_BRICKS;
                set(x, gy, z, top);
                for (int y = gy + 1; y <= CLEAR_TO; y++) set(x, y, z, AIR);
            }
            // the sea-wall face: weathered above the tide, a barnacle course through it, weed below
            int h = Math.floorMod(x * 31, 7);
            set(x, 64, q2, h == 0 ? MOSSY_STONE_BRICKS : h == 3 ? CRACKED_STONE_BRICKS : STONE_BRICKS);
            set(x, 63, q2, h < 3 ? MOSSY_STONE_BRICKS : STONE_BRICKS);
            for (int y = 59; y <= 62; y++) set(x, y, q2, Math.floorMod(x + y, 3) == 0 ? darkPris : Math.floorMod(x * y, 5) == 0 ? prisBricks : MOSSY_STONE_BRICKS);
            fill(x, 56, q2, x, 58, q2, MOSSY_COBBLE);
            if (Math.floorMod(x, 8) == 4 && x < 50) set(x, 64, q2 + 1, id("minecraft:tripwire_hook[facing=south]"));   // mooring ring
            if ((x + 92) % 20 == 0) set(x, 61, 100, WATER_LIGHT);                                               // harbour lights
            // buttresses
            if (Math.floorMod(x, 16) == 8 && x < 50 && !nearQuayGap(x, 2) && Math.abs(x - 28) > 6 && Math.abs(x + 28) > 6) {
                fill(x, 56, q2 + 1, x, 62, q2 + 1, MOSSY_STONE_BRICKS);
                set(x, 63, q2 + 1, STONE_BRICKS);
                set(x, 64, q2 + 1, stairs("stone_brick", "north"));
            }
        }
        // bollards along the edge walk, a coil of line at every other one
        for (int x = -82; x < 50; x += 8) {
            int bx = x + 2;
            if (Math.abs(bx) <= 8 || nearQuayGap(bx, 2) || (bx >= -72 && bx <= -62)) continue;
            set(bx, gy + 1, 96, Math.floorMod(bx, 16) == 8 ? id("pixelpirates:berth_bollard[facing=south]") : BLACKSTONE_WALL);
            if (Math.floorMod(bx, 16) == 0) set(bx + 1, gy + 1, 96, id("pixelpirates:rope_coil[facing=south]"));
        }
        westBattery(gy);
        cargoYard(gy);
        landingStairs(-29, gy);
        landingStairs(27, gy);
        netRacks(gy);
        anchorPlaza(gy);
        quayStall(12, 83, 18, 87, "red", "white");          // the rope maker
        set(13, gy + 2, 87, id("pixelpirates:rope_coil[facing=south]"));
        set(15, gy + 2, 87, id("pixelpirates:rope_coil[facing=south]"));
        set(17, gy + 2, 87, id("pixelpirates:rope_coil[facing=south]"));
        set(13, gy + 1, 84, id("minecraft:loom[facing=south]"));
        set(17, gy + 1, 84, BARREL_UP); set(17, gy + 2, 84, id("pixelpirates:rope_coil[facing=south]"));
        set(15, gy + 1, 83, id("pixelpirates:cargo_crate[facing=south]"));
        quayStall(32, 83, 38, 87, "blue", "white");         // the net mender
        set(33, gy + 2, 87, id("pixelpirates:lobster_pot"));
        set(35, gy + 2, 87, id("pixelpirates:fish_trap"));
        set(37, gy + 2, 87, id("pixelpirates:lobster_pot"));
        for (int x = 33; x <= 37; x += 2) set(x, gy + 3, 83, id("pixelpirates:hanging_net"));
        set(34, gy + 1, 84, id("minecraft:loom[facing=south]"));
        set(36, gy + 1, 84, CAULDRON_WATER);
        customsHouse(gy);
        // palms in planters along the back of the quay
        for (int[] pl : new int[][]{{-76, 83}, {-33, 83}, {-10, 83}, {10, 83}, {28, 83}}) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(pl[0] + dx, gy + 1, pl[1] + dz, slab("stone_brick"));
            set(pl[0], gy, pl[1], GRASS);
            palm(pl[0], gy, pl[1], 6 + Math.floorMod(pl[0], 3));
        }
        // lamps along the edge walk
        for (int x : new int[]{-44, -36, -16, 16, 36, 48}) lamppost(x, gy, 94);
        // the east end keeps its old crane + anchor (inside the shipwright yard)
        set(88, 66, 90, STONE_BRICK_WALL);  set(88, 67, 90, ANCHOR_BLOCK);
        crane(58, 65, 92);
    }
    /** True near a pier head (where the piers meet the quay edge): x within {@code m} of -80..-76, -3..3, 40..44. */
    private static boolean nearQuayGap(int x, int m) {
        return (x >= -80 - m && x <= -76 + m) || (x >= -3 - m && x <= 3 + m) || (x >= 40 - m && x <= 44 + m);
    }
    /** Water stairs cut into the quay (x xa..xa+2): two steps down to a landing at the tide line and a stage outside. */
    private static void landingStairs(int xa, int gy) {
        for (int x = xa; x <= xa + 2; x++) {
            set(x, gy, 92, AIR); set(x, gy - 1, 92, stairs("stone_brick", "north"));
            set(x, gy, 93, AIR); set(x, gy - 1, 93, AIR); set(x, gy - 2, 93, stairs("stone_brick", "north"));
            for (int z = 94; z <= 97; z++) { fill(x, gy - 2, z, x, gy, z, AIR); set(x, gy - 3, z, smoothTop()); }
        }
        for (int x = xa - 1; x <= xa + 3; x++)
            for (int z = 98; z <= 99; z++) { fill(x, 56, z, x, 61, z, MOSSY_STONE_BRICKS); set(x, 62, z, smoothTop()); fill(x, 63, z, x, 65, z, AIR); }
        for (int z = 92; z <= 97; z++) { set(xa - 1, gy + 1, z, STONE_BRICK_WALL); set(xa + 3, gy + 1, z, STONE_BRICK_WALL); }
        set(xa - 1, 63, 99, SPRUCE_FENCE); set(xa - 1, 64, 99, LANTERN);
        set(xa + 3, 63, 99, SPRUCE_FENCE);
        set(xa + 3, 64, 99, id("pixelpirates:rope_coil[facing=south]"));
    }
    private static int smoothTop() { return id("minecraft:smooth_stone_slab[type=double]"); }
    /** THE SALUTING BATTERY at the west end: a raised gun platform with a crenellated parapet, cannons and a flag. */
    private static void westBattery(int gy) {
        final int x1 = -92, x2 = -84, z1 = 85, z2 = 97;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                set(x, gy + 1, z, x == x2 || z == z1 ? STONE_BRICKS : ((x + z) & 1) == 0 ? POLISHED_ANDESITE : smoothTop());
        // parapet: merlons on even cells along the sea and the west side
        for (int x = x1; x <= x2; x++) { set(x, gy + 2, z2, STONE_BRICKS); if ((x & 1) == 0) set(x, gy + 3, z2, STONE_BRICKS); }
        for (int z = z1; z <= z2; z++) { set(x1, gy + 2, z, STONE_BRICKS); if ((z & 1) == 0) set(x1, gy + 3, z, STONE_BRICKS); }
        for (int x = x1 + 1; x <= x2; x++) if (x != -86) set(x, gy + 2, z1, STONE_BRICK_WALL);
        set(x1, gy + 4, z2, LANTERN); set(x2, gy + 4, z2, LANTERN); set(x1, gy + 3, z1, LANTERN);
        // steps up from the quay on the east side
        for (int z = 89; z <= 91; z++) { set(x2 + 1, gy + 1, z, stairs("stone_brick", "west")); set(x2, gy + 2, z, AIR); }
        // the guns: three to sea, one covering the western approach
        for (int x : new int[]{-90, -87}) set(x, gy + 2, z2 - 1, id("pixelpirates:display_cannon[facing=south]"));
        set(x1 + 1, gy + 2, 92, id("pixelpirates:display_cannon[facing=west]"));
        set(-85, gy + 2, z2 - 1, id("pixelpirates:display_cannon[facing=south]"));
        // powder + shot by the gun line
        set(-89, gy + 2, 88, GUNPOWDER_BARREL); set(-88, gy + 2, 88, GUNPOWDER_BARREL); set(-89, gy + 3, 88, GUNPOWDER_BARREL);
        set(-87, gy + 2, 88, id("minecraft:coal_block")); set(-87, gy + 3, 88, id("minecraft:polished_blackstone_slab"));
        set(-86, gy + 2, 88, id("pixelpirates:rope_coil[facing=south]"));
        set(-86, gy + 2, 86, ANCHOR_BLOCK);
        // flagstaff with the port's colours
        for (int y = gy + 2; y <= gy + 12; y++) set(-91, y, 86, SPRUCE_FENCE);
        set(-91, gy + 11, 87, id("minecraft:blue_wall_banner[facing=south]"));
        set(-91, gy + 13, 86, LANTERN);
    }
    /** THE CARGO YARD in front of the east warehouse: treadwheel crane, crate stacks under tarps, pallets, a cart. */
    private static void cargoYard(int gy) {
        // the treadwheel: a ring of planks (axis along x) on an axle into the crane mast, spokes on the outer face
        final int wy = gy + 4, wz = 94;
        for (int x = -70; x <= -69; x++)
            for (int dy = -3; dy <= 3; dy++)
                for (int dz = -3; dz <= 3; dz++) {
                    double d = Math.sqrt(dy * dy + dz * dz);
                    if (Math.abs(d - 3) < 0.55) set(x, wy + dy, wz + dz, SPRUCE);
                    else if (x == -70 && d < 2.6 && (dy == 0 || dz == 0)) set(x, wy + dy, wz + dz, dy == 0 ? SPRUCE_LOG_Z : SPRUCE_LOG_Y);
                }
        for (int x = -72; x <= -65; x++) set(x, wy, wz, SPRUCE_LOG_X);
        for (int y = gy + 1; y < wy; y++) { set(-72, y, wz, SPRUCE_LOG_Y); set(-67, y, wz, SPRUCE_LOG_Y); }
        set(-72, gy + 1, wz - 1, stairs("spruce", "south")); set(-72, gy + 1, wz + 1, stairs("spruce", "north"));
        // the mast on a stone footing, jib out over the water, hoist line, a crate in the air, counterweight behind
        fill(-65, gy + 1, 93, -63, gy + 1, 95, STONE_BRICKS);
        for (int y = gy + 2; y <= gy + 14; y++) set(-64, y, 94, SPRUCE_LOG_Y);
        for (int x : new int[]{-65, -63}) set(x, gy + 2, 94, stairs("spruce", x < -64 ? "east" : "west"));
        for (int z : new int[]{93, 95}) set(-64, gy + 2, z, stairs("spruce", z < 94 ? "south" : "north"));
        for (int z = 90; z <= 103; z++) set(-64, gy + 13, z, SPRUCE_LOG_Z);
        for (int z = 96; z <= 102; z += 3) set(-64, gy + 14, z, SPRUCE_FENCE);
        fill(-64, gy + 11, 90, -64, gy + 12, 90, COBBLE);                    // counterweight
        for (int y = gy + 6; y <= gy + 12; y++) set(-64, y, 103, CHAIN);
        set(-64, gy + 5, 103, id("pixelpirates:cargo_crate[facing=south]"));
        set(-64, gy + 4, 103, id("pixelpirates:hanging_net"));
        // crate stacks under tarps either side of the warehouse door lane (x -58..-50 stays open)
        int[][] stacks = {{-62, 87, 2}, {-61, 87, 1}, {-62, 88, 1}, {-48, 88, 2}, {-47, 88, 2}, {-46, 88, 1},
                          {-48, 89, 1}, {-47, 89, 2}, {-44, 89, 1}};
        for (int[] st : stacks) {
            for (int k = 0; k < st[2]; k++) set(st[0], gy + 1 + k, st[1], id("pixelpirates:cargo_crate[facing=" + (k == 0 ? "south" : "east") + "]"));
            set(st[0], gy + 1 + st[2], st[1], id("minecraft:brown_carpet"));
        }
        // pallets with sacks and barrels, a hand cart, a rope coil
        for (int x = -42; x <= -39; x++) for (int z = 87; z <= 88; z++) set(x, gy + 1, z, id("minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]"));
        set(-42, gy + 1, 87, BARREL_UP); set(-41, gy + 1, 87, BARREL_UP); set(-42, gy + 2, 87, BARREL_UP);
        set(-40, gy + 1, 88, HAY); set(-39, gy + 1, 88, id("minecraft:brown_wool"));
        handCart(-48, gy, 92);
        set(-58, gy + 1, 91, id("pixelpirates:rope_coil[facing=east]"));
        set(-60, gy + 1, 91, BARREL_UP); set(-60, gy + 1, 92, BARREL_UP); set(-60, gy + 2, 91, BARREL_UP);
        lamppost(-50, gy, 90); lamppost(-58, gy, 88);
    }
    /** The fishermen's net-drying racks behind the west landing stair. */
    private static void netRacks(int gy) {
        for (int x = -24; x <= -16; x += 4) fill(x, gy + 1, 85, x, gy + 3, 85, SPRUCE_FENCE);
        for (int x = -23; x <= -17; x++) {
            if (x == -20) continue;
            set(x, gy + 3, 85, SPRUCE_FENCE);
            set(x, gy + 2, 85, id("pixelpirates:hanging_net"));
        }
        set(-23, gy + 1, 88, id("pixelpirates:lobster_pot")); set(-22, gy + 1, 88, id("pixelpirates:lobster_pot"));
        set(-23, gy + 2, 88, id("pixelpirates:lobster_pot"));
        set(-18, gy + 1, 88, slabTop("spruce")); set(-17, gy + 1, 88, slabTop("spruce"));      // gutting bench
        set(-16, gy + 1, 88, CAULDRON_WATER);
        set(-19, gy + 1, 88, BARREL_UP);
    }
    /** THE ANCHOR PLAZA where the main avenue meets the grand pier. */
    private static void anchorPlaza(int gy) {
        final int cz = 89;
        int ring = POLISHED_BLACKSTONE, ray = id("minecraft:polished_blackstone_bricks");
        for (int x = -12; x <= 12; x++)
            for (int z = 82; z <= 96; z++) {
                double r = Math.sqrt(x * x + (z - cz) * (z - cz));
                if (r >= 7.5) continue;
                int b = r >= 6.5 ? ring : (x == 0 || z == cz) ? ray : Math.abs(x) == Math.abs(z - cz) ? POLISHED_ANDESITE : smoothTop();
                set(x, gy, z, b);
            }
        // the monument: a stepped plinth, a weathered-copper anchor with a wooden stock, lanterns at the corners
        int cu = id("minecraft:waxed_weathered_cut_copper");
        fill(-3, gy + 1, cz - 2, 3, gy + 1, cz + 2, STONE_BRICKS);
        fill(-2, gy + 2, cz - 1, 2, gy + 2, cz + 1, CHISELED_STONE_BRICKS);
        for (int x = -2; x <= 2; x++) set(x, gy + 3, cz, cu);                  // the crown
        for (int x : new int[]{-3, 3}) {                                         // arms + flukes
            set(x, gy + 3, cz, id("minecraft:waxed_weathered_cut_copper_stairs[facing=" + (x < 0 ? "east" : "west") + ",half=top]"));
            set(x, gy + 4, cz, cu);
            set(x, gy + 5, cz, id("minecraft:waxed_weathered_cut_copper_slab"));
        }
        for (int y = gy + 4; y <= gy + 9; y++) set(0, y, cz, cu);               // the shank
        for (int z = cz - 2; z <= cz + 2; z++) set(0, gy + 8, z, SPRUCE_LOG_Z);  // the stock, across the arms
        set(0, gy + 10, cz, cu); set(-1, gy + 11, cz, cu); set(1, gy + 11, cz, cu); set(0, gy + 12, cz, cu);   // the ring
        for (int x : new int[]{-3, 3}) for (int z : new int[]{cz - 2, cz + 2}) set(x, gy + 2, z, LANTERN);
        // benches facing the anchor, banners flanking the grand pier, lamps at the plaza edge
        for (int z = cz - 1; z <= cz + 1; z++) { set(-6, gy + 1, z, stairs("spruce", "west")); set(6, gy + 1, z, stairs("spruce", "east")); }
        for (int x : new int[]{-5, 5}) {
            for (int y = gy + 1; y <= gy + 9; y++) set(x, y, 96, SPRUCE_FENCE);
            set(x, gy + 8, 95, id("minecraft:blue_wall_banner[facing=north]"));
            set(x, gy + 10, 96, LANTERN);
        }
        lamppost(-11, gy, 92); lamppost(11, gy, 92);
    }
    /** A waterfront stall: four posts, a counter along the front (z2), a striped wool awning. */
    private static void quayStall(int x1, int z1, int x2, int z2, String cA, String cB) {
        for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2}) fill(x, 66, z, x, 68, z, SPRUCE_FENCE);
        for (int x = x1 + 1; x < x2; x++) set(x, 66, z2, SPRUCE);
        for (int x = x1; x <= x2; x++) {
            int w = ((x - x1) & 1) == 0 ? wool(cA) : wool(cB);
            for (int z = z1; z <= z2; z++) set(x, 69, z, w);
        }
        set(x1 + 1, 68, z2, LANTERN_HANGING); set(x2 - 1, 68, z2, LANTERN_HANGING);
        for (int x = x1; x <= x2; x++) set(x, 69, z2 + 1, slab("spruce"));
    }
    /** THE CUSTOMS HOUSE by the east pier: a small stone-and-timber office with a bell to call the officer. */
    private static void customsHouse(int gy) {
        final int x1 = 42, x2 = 48, z1 = 83, z2 = 88;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(x1, gy, z1, x2, gy, z2, SPRUCE);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                for (int y = gy + 1; y <= gy + 4; y++)
                    set(x, y, z, !edge ? AIR : corner ? DARK_OAK_LOG_Y : y == gy + 1 ? STONE_BRICKS : y == gy + 4 ? DARK_OAK_LOG_X : SPRUCE);
            }
        for (int z = z1 + 1; z < z2; z++) { set(x1, gy + 4, z, DARK_OAK_LOG_Z); set(x2, gy + 4, z, DARK_OAK_LOG_Z); }
        gableRoofX(x1, x2, z1, z2, gy + 5, "dark_oak", DARK_OAK);
        for (int xe : new int[]{x1, x2}) gableEndsX(xe, z1, z2, gy + 5, SPRUCE);
        windowsAlongX(x1, x2, z1, gy + 3, 2);
        set(x1 + 2, gy + 3, z2, GLASS_PANE); set(x2 - 2, gy + 3, z2, GLASS_PANE);
        set(x1, gy + 3, 85, GLASS_PANE); set(x1, gy + 3, 86, GLASS_PANE); set(x2, gy + 3, 86, GLASS_PANE);
        placeDoor(45, gy, z2, "spruce", "south");
        set(45, gy, z2 + 1, POLISHED_ANDESITE);
        // inside: the officer's desk, the ledger, the tariff chest, a strongroom barrel, a lamp
        set(44, gy + 1, 84, LECTERN_N); set(46, gy + 1, 84, slabTop("dark_oak")); set(47, gy + 1, 84, slabTop("dark_oak"));
        set(47, gy + 1, 87, chest("west")); set(43, gy + 1, 87, BARREL_UP); set(43, gy + 1, 86, BARREL_UP);
        set(46, gy + 2, 84, id("minecraft:candle[candles=2,lit=true]"));
        set(45, gy + 4, 85, LANTERN_HANGING);
        // outside: a bell on a post, a banner, a crate of seized goods
        set(49, gy + 1, 89, SPRUCE_FENCE); set(49, gy + 2, 89, SPRUCE_FENCE); set(49, gy + 3, 89, id("minecraft:bell[attachment=floor,facing=south]"));
        set(44, gy + 3, z2 + 1, id("minecraft:blue_wall_banner[facing=south]"));
        set(41, gy + 1, 88, id("pixelpirates:cargo_crate[facing=south]")); set(41, gy + 2, 88, id("minecraft:brown_carpet"));
        keepClear(x1 - 1, z1 - 1, x2 + 1, z2 + 2);
    }
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

    /**
     * THE PIERS (rebuilt 2026-10-01, building-by-building overhaul #4). Three timber piers on pile bents (paired piles every
     * 4 blocks, cross beams under the deck, stringer logs along the edges, rope-rail fences with boarding gaps, arm lamps,
     * harbour lights under the water):
     *  - WEST = the FISHERMEN'S PIER: a T-head with a net shed, drying racks, stools along the end, pots and catch barrels.
     *  - GRAND = the SHIPWRIGHT'S PIER: a timber gate arch at the quay, a bench bay halfway, and the SHIPWRIGHT'S LANDING at
     *    the end - the shipwright_table stays at (-2,66,148) with its lectern; a striped canopy over it (posts 3+ away),
     *    timber stock + ship's fittings on the east half, and steps down to a water landing. Nothing near the end rises
     *    above y72 and nothing is built in the water around it, so purchased ships (spawned ~20 out at Y75) find open sea.
     *  - EAST = the CARGO PIER: a T-head with a jib crane swinging a crate over the water, crate stacks, a cart.
     * Gangplanks to the two moored sloops are laid in harborBoats() (the sloops are drawn after the piers).
     */
    private static void piers() {
        pier(-80, -76, 97, 123);        // west pier (T-head from 124)
        pier(-3, 3, 97, 139);           // grand pier (landing from 140)
        pier(40, 44, 97, 127);          // east pier (T-head from 128)
        fishermensHead();
        shipwrightsLanding();
        cargoHead();
        // the grand pier's gate at the quay: dark-oak posts on stone footings, a lintel with the port's anchor
        for (int x : new int[]{-4, 4}) {
            fill(x, 50, 98, x, 64, 98, STONE_BRICKS);
            set(x, 65, 98, CHISELED_STONE_BRICKS);
            for (int y = 66; y <= 71; y++) set(x, y, 98, DARK_OAK_LOG_Y);
        }
        for (int x = -4; x <= 4; x++) set(x, 72, 98, DARK_OAK_LOG_X);
        set(-5, 72, 98, stairs("dark_oak", "east")); set(5, 72, 98, stairs("dark_oak", "west"));
        set(-3, 71, 98, stairsTop("dark_oak", "east")); set(3, 71, 98, stairsTop("dark_oak", "west"));
        set(0, 73, 98, ANCHOR_BLOCK);
        set(-2, 71, 98, LANTERN_HANGING); set(2, 71, 98, LANTERN_HANGING);
        // the bench bay halfway down the grand pier
        for (int x = -6; x <= 6; x++)
            for (int z = 117; z <= 123; z++)
                if (x < -3 || x > 3) {
                    deckColumn(x, z, x == -6 || x == 6 || z == 117 || z == 123 ? id("minecraft:stripped_spruce_log[axis=" + (z == 117 || z == 123 ? "x" : "z") + "]") : SPRUCE);
                    if ((x == -6 || x == 6) && (z == 117 || z == 123)) fill(x, 50, z, x, 64, z, SPRUCE_LOG_Y);
                }
        for (int z = 118; z <= 122; z++) {
            set(-3, 66, z, AIR); set(3, 66, z, AIR);
            set(-5, 66, z, z == 120 ? AIR : stairs("spruce", "west"));
            set(5, 66, z, z == 120 ? AIR : stairs("spruce", "east"));
            set(-6, 66, z, railZ()); set(6, 66, z, railZ());
        }
        set(-5, 66, 120, id("minecraft:potted_red_tulip")); set(5, 66, 120, id("minecraft:potted_blue_orchid"));
        set(-6, 66, 117, SPRUCE_FENCE); set(-6, 67, 117, LANTERN); set(6, 66, 123, SPRUCE_FENCE); set(6, 67, 123, LANTERN);
    }
    private static int railZ() { return id("minecraft:spruce_fence[north=true,south=true]"); }
    private static int railX() { return id("minecraft:spruce_fence[east=true,west=true]"); }
    /**
     * A pier running south (+z) from the quay: spruce deck with dark-oak joint rows, stripped-log stringers on the
     * edges, a bent (two piles + a cross beam) every 4 blocks, rope rails with a boarding gap every 16, arm lamps
     * every 12 (alternating sides), harbour lights under the water.
     */
    private static void pier(int x1, int x2, int z1, int z2) {
        int stringer = id("minecraft:stripped_spruce_log[axis=z]"), mid = (x1 + x2) / 2;
        for (int z = z1; z <= z2; z++)
            for (int x = x1; x <= x2; x++)
                deckColumn(x, z, x == x1 || x == x2 ? stringer : z % 6 == 0 ? DARK_OAK : SPRUCE);
        for (int z = z1 + 3; z <= z2; z += 4) {
            fill(x1, 50, z, x1, 64, z, SPRUCE_LOG_Y);
            fill(x2, 50, z, x2, 64, z, SPRUCE_LOG_Y);
            for (int x = x1 + 1; x < x2; x++) set(x, 64, z, SPRUCE_LOG_X);
            set(x1 + 1, 63, z, stairsTop("spruce", "west")); set(x2 - 1, 63, z, stairsTop("spruce", "east"));
        }
        for (int z = z1 + 2; z <= z2; z++) {
            boolean gap = Math.floorMod(z - z1, 16) >= 13;                  // boarding gaps
            for (int x : new int[]{x1, x2}) {
                if (gap) { if (Math.floorMod(z - z1, 16) == 14) set(x, 66, z, id("pixelpirates:berth_bollard[facing=" + (x == x1 ? "west" : "east") + "]")); continue; }
                set(x, 66, z, Math.floorMod(z - z1, 4) == 3 ? SPRUCE_FENCE : railZ());
            }
        }
        for (int z = z1 + 7; z <= z2 - 2; z += 12) {
            boolean west = ((z - z1) / 12) % 2 == 0;
            int x = west ? x1 : x2, in = west ? 1 : -1;
            fill(x, 66, z, x, 69, z, SPRUCE_FENCE);
            set(x + in, 69, z, id("minecraft:spruce_fence[" + (west ? "west" : "east") + "=true]"));
            set(x, 69, z, id("minecraft:spruce_fence[" + (west ? "east" : "west") + "=true]"));
            set(x + in, 68, z, LANTERN_HANGING);
        }
        for (int z = z1 + 6; z <= z2; z += 12) set(mid, 61, z, WATER_LIGHT);
    }
    /** The west pier's T-head: net shed, drying racks, stools along the end, pots and catch barrels. */
    private static void fishermensHead() {
        final int x1 = -86, x2 = -70, z1 = 124, z2 = 130;
        int stringerX = id("minecraft:stripped_spruce_log[axis=x]"), stringerZ = id("minecraft:stripped_spruce_log[axis=z]");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                deckColumn(x, z, z == z1 || z == z2 ? stringerX : x == x1 || x == x2 ? stringerZ : (x % 6 == 0 ? DARK_OAK : SPRUCE));
        for (int x = x1; x <= x2; x += 4) for (int z : new int[]{z1, z2}) fill(x, 50, z, x, 64, z, SPRUCE_LOG_Y);
        // rails on the head, the stretch where the pier joins (x -80..-76 on z1) stays open
        for (int x = x1; x <= x2; x++) {
            if (x < -80 || x > -76) set(x, 66, z1, (x - x1) % 4 == 0 ? SPRUCE_FENCE : railX());
            set(x, 66, z2, (x - x1) % 4 == 0 ? SPRUCE_FENCE : railX());
        }
        for (int z = z1; z <= z2; z++) { set(x1, 66, z, z == z1 || z == z2 ? SPRUCE_FENCE : railZ()); set(x2, 66, z, z == z1 || z == z2 ? SPRUCE_FENCE : railZ()); }
        // the net shed (west end): plank walls on a log frame, slab roof, door to the east
        int sx1 = -85, sx2 = -81, sz1 = 125, sz2 = 129;
        for (int x = sx1; x <= sx2; x++)
            for (int z = sz1; z <= sz2; z++) {
                boolean edge = x == sx1 || x == sx2 || z == sz1 || z == sz2, corner = (x == sx1 || x == sx2) && (z == sz1 || z == sz2);
                for (int y = 66; y <= 68; y++) set(x, y, z, !edge ? AIR : corner ? SPRUCE_LOG_Y : SPRUCE);
                set(x, 69, z, slab("dark_oak"));
            }
        for (int x = sx1 - 1; x <= sx2 + 1; x++) { set(x, 69, sz1 - 1, slab("dark_oak")); set(x, 69, sz2 + 1, slab("dark_oak")); }
        placeDoor(sx2, 65, 127, "spruce", "east");
        set(sx1, 67, 127, GLASS_PANE); set(-83, 67, sz1, GLASS_PANE); set(-83, 67, sz2, GLASS_PANE);
        set(-84, 66, 126, BARREL_UP); set(-84, 66, 128, CAULDRON_WATER); set(-82, 66, 126, id("pixelpirates:lobster_pot"));
        set(-84, 68, 127, id("pixelpirates:hanging_net")); set(-83, 68, 128, LANTERN_HANGING);
        // drying racks with nets, catch barrels, pots, stools along the south rail for the anglers
        for (int x = -78; x <= -73; x++) {
            set(x, 68, 126, x == -78 || x == -73 ? SPRUCE_FENCE : railX());
            if (x == -78 || x == -73) { set(x, 66, 126, SPRUCE_FENCE); set(x, 67, 126, SPRUCE_FENCE); }
            else set(x, 67, 126, id("pixelpirates:hanging_net"));
        }
        set(-72, 66, 125, BARREL_UP); set(-71, 66, 125, BARREL_UP); set(-71, 67, 125, BARREL_UP);
        set(-72, 66, 128, id("pixelpirates:lobster_pot")); set(-71, 66, 128, id("pixelpirates:fish_trap"));
        for (int x : new int[]{-79, -77, -75}) set(x, 66, 129, stairs("spruce", "north"));
        set(-73, 66, 129, BARREL_UP); set(-73, 67, 129, id("minecraft:potted_dead_bush"));
        set(x2, 67, z2, LANTERN); set(x1, 67, z2, LANTERN);
        set(x2, 67, z1, LANTERN);
        for (int x = -84; x <= -72; x += 6) set(x, 61, 127, WATER_LIGHT);
        keepClear(x1, z1, x2, z2);
    }
    /**
     * THE SHIPWRIGHT'S LANDING at the end of the grand pier. The shipwright_table (-2,66,148) and its lectern (-3,66,148)
     * are where they always were, with nothing within 2 blocks of them; the canopy posts stand 3+ away.
     */
    private static void shipwrightsLanding() {
        final int x1 = -7, x2 = 7, z1 = 140, z2 = 152;
        int stringerX = id("minecraft:stripped_spruce_log[axis=x]"), stringerZ = id("minecraft:stripped_spruce_log[axis=z]");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                deckColumn(x, z, z == z2 || (z == z1 && Math.abs(x) > 3) ? stringerX : x == x1 || x == x2 ? stringerZ
                        : (Math.abs(x) + Math.abs(z - 146)) % 7 == 0 ? DARK_OAK : OAK);
        for (int x = x1; x <= x2; x += 7) for (int z = z1; z <= z2; z += 4) fill(x, 50, z, x, 64, z, SPRUCE_LOG_Y);
        for (int z = z1 + 2; z <= z2; z += 4) for (int x = x1 + 1; x < x2; x++) set(x, 64, z, SPRUCE_LOG_X);
        // corner posts + lanterns, rails round the rim (gap in the south rail for the water stair)
        for (int x : new int[]{x1, x2}) for (int z : new int[]{z1, z2}) { fill(x, 66, z, x, 68, z, SPRUCE_LOG_Y); set(x, 69, z, LANTERN); }
        for (int z = z1 + 1; z < z2; z++) { set(x1, 66, z, z % 4 == 0 ? SPRUCE_FENCE : railZ()); set(x2, 66, z, z % 4 == 0 ? SPRUCE_FENCE : railZ()); }
        for (int x = x1 + 1; x < x2; x++) {
            if (Math.abs(x) > 1) set(x, 66, z2, x % 3 == 0 ? SPRUCE_FENCE : railX());
            if (Math.abs(x) > 3) set(x, 66, z1, x % 3 == 0 ? SPRUCE_FENCE : railX());
        }
        // THE TABLE (unchanged spot) + lectern, the bell to ring for the shipwright
        set(-2, 66, 148, SHIPWRIGHT_TABLE);
        set(-3, 66, 148, LECTERN_E);
        set(0, 66, 151, BELL);
        // a striped canopy over the table: posts at least 3 away, roof at y70
        for (int x : new int[]{-6, 2}) for (int z : new int[]{144, 151}) fill(x, 66, z, x, 69, z, DARK_OAK_FENCE);
        for (int x = -6; x <= 2; x++)
            for (int z = 144; z <= 151; z++) set(x, 70, z, ((x + 6) & 1) == 0 ? wool("blue") : WHITE_WOOL);
        for (int x = -6; x <= 2; x++) { set(x, 70, 143, slab("dark_oak")); set(x, 70, 152, slab("dark_oak")); }
        set(-2, 69, 147, LANTERN_HANGING);
        set(-6, 68, 145, id("minecraft:blue_wall_banner[facing=south]"));
        // the east half: timber stock, a coil of hawser, spare anchor, ship's wheel parts on a bench, plan chests
        for (int z = 141; z <= 143; z++) { set(4, 66, z, SPRUCE_LOG_Z); set(5, 66, z, SPRUCE_LOG_Z); set(4, 67, z, SPRUCE_LOG_Z); }
        set(6, 66, 141, id("pixelpirates:rope_coil[facing=west]")); set(6, 67, 141, id("pixelpirates:rope_coil[facing=west]"));
        set(5, 66, 146, ANCHOR_BLOCK);
        set(5, 66, 148, slabTop("spruce")); set(6, 66, 148, slabTop("spruce"));
        set(5, 67, 148, id("minecraft:spruce_trapdoor[facing=west,half=bottom,open=false]"));
        set(6, 66, 150, BARREL_UP); set(5, 66, 150, id("pixelpirates:cargo_crate[facing=west]"));
        set(6, 66, 145, id("minecraft:cartography_table"));
        // west rim: mooring bollards
        set(x1, 66, 146, id("pixelpirates:berth_bollard[facing=west]")); set(x1, 66, 150, id("pixelpirates:berth_bollard[facing=west]"));
        // steps down to a water landing south of the platform
        for (int x = -1; x <= 1; x++) {
            set(x, 65, z2, stairs("oak", "north"));
            set(x, 64, z2 + 1, stairs("oak", "north"));
            fill(x, 65, z2 + 1, x, 66, z2 + 1, AIR);
        }
        for (int x = -2; x <= 2; x++)
            for (int z = z2 + 2; z <= z2 + 3; z++) { set(x, 63, z, OAK); fill(x, 64, z, x, 66, z, AIR); }
        for (int x : new int[]{-2, 2}) { fill(x, 50, z2 + 3, x, 62, z2 + 3, SPRUCE_LOG_Y); set(x, 64, z2 + 3, SPRUCE_FENCE); }
        set(2, 65, z2 + 3, LANTERN);
        keepClear(x1, z1, x2, z2 + 3);
    }
    /** The east pier's T-head: a jib crane over the water, crate stacks under tarps, a cart, a tall harbour lamp. */
    private static void cargoHead() {
        final int x1 = 34, x2 = 48, z1 = 128, z2 = 134;
        int stringerX = id("minecraft:stripped_spruce_log[axis=x]"), stringerZ = id("minecraft:stripped_spruce_log[axis=z]");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++)
                deckColumn(x, z, z == z1 || z == z2 ? stringerX : x == x1 || x == x2 ? stringerZ : (x % 5 == 0 ? DARK_OAK : SPRUCE));
        for (int x = x1; x <= x2; x += 7) for (int z : new int[]{z1, z2}) fill(x, 50, z, x, 64, z, SPRUCE_LOG_Y);
        for (int x = x1; x <= x2; x++) {
            if (x < 40 || x > 44) set(x, 66, z1, (x - x1) % 4 == 0 ? SPRUCE_FENCE : railX());
            set(x, 66, z2, (x - x1) % 4 == 0 ? SPRUCE_FENCE : railX());
        }
        for (int z = z1 + 1; z < z2; z++) { set(x1, 66, z, railZ()); set(x2, 66, z, railZ()); }
        crane(46, 65, 131);                                           // jib swings out south over the water
        set(46, 66, 130, AIR); set(47, 66, 130, AIR);                 // crane() braces on its north side; keep the deck walkable
        set(46, 67, 130, AIR); set(47, 67, 130, AIR); set(46, 68, 130, AIR); set(47, 68, 130, AIR);
        int[][] stacks = {{36, 129, 2}, {37, 129, 1}, {36, 130, 1}, {39, 133, 2}, {40, 133, 1}};
        for (int[] st : stacks) {
            for (int k = 0; k < st[2]; k++) set(st[0], 66 + k, st[1], id("pixelpirates:cargo_crate[facing=" + (k == 0 ? "south" : "east") + "]"));
            set(st[0], 66 + st[2], st[1], id("minecraft:brown_carpet"));
        }
        set(43, 66, 133, BARREL_UP); set(42, 66, 133, BARREL_UP); set(42, 67, 133, BARREL_UP);
        set(38, 66, 131, id("pixelpirates:rope_coil[facing=east]"));
        // the harbour lamp at the pier's south-west corner
        for (int y = 66; y <= 71; y++) set(x1, y, z2, SPRUCE_LOG_Y);
        set(x1, 72, z2, id("minecraft:glowstone")); set(x1, 73, z2, slab("dark_oak"));
        keepClear(x1, z1, x2, z2);
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
                if (SpawnIslandTerrain.inCityFootprint(x, z)) {
                    int h = Math.floorMod(x * 73 + z * 37 + x * z, 11);
                    set(x, 65, z, h < 4 ? GRAVEL : h < 7 ? id("minecraft:coarse_dirt") : h < 9 ? DIRT_PATH : id("minecraft:packed_mud"));
                }

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

        hallLoftAndCupola();
        coveredSlip();
        shipOnTheSlip();
        yardWorks();
    }

    /** The slipway surface height: 65 at the slip head (z86), dropping a block every 6 into the harbour. */
    private static int slipY(int z) { return 65 - Math.floorDiv(z - 86, 6); }

    /** The hall's sail loft (north side, y70, stair at x83..86) and a bell cupola on the ridge. */
    private static void hallLoftAndCupola() {
        for (int x = 59; x <= 87; x++) for (int z = 61; z <= 66; z++) set(x, 70, z, SPRUCE);
        for (int x = 80; x <= 82; x++) set(x, 70, 67, SPRUCE);
        for (int x = 59; x <= 79; x++) set(x, 71, 67, railX());
        for (int k = 0; k < 4; k++) set(86 - k, 66 + k, 67, stairs("spruce", "west"));
        for (int x = 64; x <= 76; x += 6) { set(x, 69, 66, stairsTop("dark_oak", "south")); }
        // up in the loft: canvas rolls, rope, spare blocks and pulleys
        for (int x = 60; x <= 66; x++) { set(x, 71, 62, WHITE_WOOL); if (x % 2 == 0) set(x, 72, 62, WHITE_WOOL); }
        for (int x = 69; x <= 75; x += 2) set(x, 71, 62, id("pixelpirates:rope_coil[facing=south]"));
        set(78, 71, 62, BARREL_UP); set(79, 71, 62, BARREL_UP); set(78, 72, 62, BARREL_UP);
        set(84, 71, 63, id("pixelpirates:cargo_crate[facing=west]")); set(84, 71, 64, id("pixelpirates:cargo_crate[facing=west]"));
        set(72, 72, 65, LANTERN_HANGING); set(62, 72, 65, LANTERN_HANGING);
        // cupola on the ridge (ridge y84 at z71)
        for (int x : new int[]{72, 74}) for (int z : new int[]{70, 72}) fill(x, 85, z, x, 87, z, DARK_OAK_FENCE);
        set(73, 85, 71, DARK_OAK); set(73, 86, 71, BELL);
        for (int x = 71; x <= 75; x++) for (int z = 69; z <= 73; z++) set(x, 88, z, slab("dark_oak"));
        for (int x = 72; x <= 74; x++) for (int z = 70; z <= 72; z++) set(x, 89, z, slab("dark_oak"));
        set(73, 90, 71, id("minecraft:lightning_rod"));
    }

    /**
     * The covered building slip: a timber ramp x62..82 from the slip head (z86) into the harbour (z124), sliding ways,
     * and a great open shed over it - posts x60/84 every 4, tie beams y83, a gable roof (ridge x72 y95) boarded over the
     * landward bays (z86..105) and bare trusses beyond, a hoist chain lowering a rib.
     */
    private static void coveredSlip() {
        for (int z = 86; z <= 124; z++) {
            int y = slipY(z);
            for (int x = 62; x <= 82; x++) {
                set(x, y, z, x == 62 || x == 82 ? SPRUCE_LOG_Z : x == 69 || x == 75 ? DARK_OAK : SPRUCE);
                for (int cy = y + 1; cy <= 100; cy++) set(x, cy, z, cy <= 62 ? WATER : AIR);
                if (z <= 97) fill(x, 56, z, x, y - 1, z, COBBLE);
            }
            if (z >= 98 && z % 4 == 2) for (int x = 62; x <= 82; x += 5) fill(x, 50, z, x, y - 1, z, SPRUCE_LOG_Y);
        }
        // the shed
        for (int z = 86; z <= 122; z += 4) {
            for (int x : new int[]{60, 84}) {
                if (z >= 98) fill(x, 50, z, x, 64, z, SPRUCE_LOG_Y); else fill(x, 56, z, x, 65, z, STONE_BRICKS);
                for (int y = 66; y <= 82; y++) set(x, y, z, SPRUCE_LOG_Y);
                set(x + (x == 60 ? 1 : -1), 81, z, stairsTop("spruce", x == 60 ? "west" : "east"));
            }
            for (int x = 60; x <= 84; x++) set(x, 83, z, SPRUCE_LOG_X);
            for (int x = 70; x <= 74; x++) if (x != 72) set(x, 84, z, SPRUCE_FENCE);   // king posts
            fill(72, 84, z, 72, 94, z, SPRUCE_LOG_Y);
        }
        for (int z = 86; z <= 122; z++) { set(60, 82, z, SPRUCE_LOG_Z); set(84, 82, z, SPRUCE_LOG_Z); }
        for (int z = 85; z <= 123; z++) {
            boolean boarded = z <= 105, truss = (z - 86) % 4 == 0 && z <= 122;
            if (!boarded && !truss && (z % 7 != 0)) continue;              // a few loose boards on the open bays
            for (int k = 0; k <= 12; k++) {
                if (!boarded && !truss && k > 4) break;
                set(59 + k, 83 + k, z, stairs("dark_oak", "east"));
                set(85 - k, 83 + k, z, stairs("dark_oak", "west"));
                if (boarded && k < 12) { set(59 + k, 82 + k, z, k == 0 ? AIR : DARK_OAK); set(85 - k, 82 + k, z, k == 0 ? AIR : DARK_OAK); }
            }
            set(72, 96, z, slab("dark_oak"));
            if (boarded) set(72, 95, z, DARK_OAK);
        }
        // re-seat the posts and plates the roof underside overwrote
        for (int z = 86; z <= 122; z += 4) { set(60, 82, z, SPRUCE_LOG_Y); set(84, 82, z, SPRUCE_LOG_Y); }
        for (int z = 86; z <= 105; z++) { set(60, 82, z, SPRUCE_LOG_Z); set(84, 82, z, SPRUCE_LOG_Z); }
        // gable end over the slip head: a board with the yard's anchor and banners
        set(72, 89, 85, slabTop("dark_oak")); set(72, 90, 85, ANCHOR_BLOCK);                          // the anchor on a bracket
        set(68, 86, 85, id("minecraft:blue_wall_banner[facing=north]")); set(76, 86, 85, id("minecraft:blue_wall_banner[facing=north]"));
        for (int x = 66; x <= 78; x++) set(x, 88 - Math.abs(x - 72) / 2, 86, DARK_OAK);
        // hoist: a rib being lowered into place
        for (int y = 82; y <= 94; y++) set(72, y, 100, CHAIN);
        for (int x = 70; x <= 74; x++) set(x, 81, 100, id("minecraft:stripped_oak_wood"));
        for (int z = 90; z <= 118; z += 14) { set(66, 82, z, LANTERN_HANGING); set(78, 82, z, LANTERN_HANGING); }
    }

    /**
     * The ship on the slip: a hull 23 long (stern z88, bow z110), keel y67 on a cradle of blocks and shores. The stern
     * half is planked to the wale with a quarterdeck; forward of z100 only the ribs (every 2) and two ribbands stand.
     * Scaffold towers x64/x80 with plank walks at y71 and y75, ladders up at the slip head.
     */
    private static void shipOnTheSlip() {
        final int cx = 72, zs = 88, zb = 110, ky = 67, hh = 8;
        int rib = id("minecraft:stripped_oak_wood"), plank = SPRUCE, ribband = id("minecraft:stripped_spruce_log[axis=z]");
        for (int z = zs; z <= zb; z++) {
            double t = (z - zs) / (double) (zb - zs);
            double b = t < 0.42 ? 5.2 * (1 - 0.35 * Math.pow((0.42 - t) / 0.42, 2))
                                : 5.2 * Math.sqrt(Math.max(0, 1 - Math.pow((t - 0.42) / 0.58, 2)));
            int top = hh + (int) Math.round(1.6 * Math.pow((t - 0.45) / 0.55, 2));
            boolean isRib = (z - zs) % 2 == 0, planked = z <= 99;
            int prev = 0;
            for (int h = 1; h <= top; h++) {
                int w = (int) Math.round(b * Math.pow(Math.min(h, hh) / (double) hh, 0.45));
                for (int xx = Math.min(prev, w); xx <= w; xx++)
                    for (int sx : new int[]{-1, 1}) {
                        int x = cx + sx * xx, y = ky + h;
                        if (planked) set(x, y, z, h == top - 1 ? DARK_OAK : isRib && h == top ? rib : h == top ? AIR : plank);
                        else if (isRib) set(x, y, z, rib);
                        else if (xx == w && (h == 4 || h == top)) set(x, y, z, ribband);
                    }
                prev = w;
            }
            if (planked && isRib) for (int xx = -(int) Math.round(b) + 1; xx <= (int) Math.round(b) - 1; xx++) set(cx + xx, ky + top - 1, z, SPRUCE_LOG_X);
            if (z <= 92) for (int xx = -(int) Math.round(b) + 1; xx <= (int) Math.round(b) - 1; xx++) set(cx + xx, ky + top - 1, z, slab("spruce"));
            set(cx, ky, z, DARK_OAK_LOG_Z);                                   // keel
            if ((z - zs) % 3 == 0) {                                          // cradle: keel blocks + bilge blocks + shores
                fill(cx, slipY(z) + 1, z, cx, ky - 1, z, SPRUCE_LOG_Y);
                for (int sx : new int[]{-3, 3}) fill(cx + sx, slipY(z) + 1, z, cx + sx, ky + 1, z, SPRUCE_LOG_Y);
                int sh = (int) Math.round(b) + 1;
                for (int sx : new int[]{-sh, sh}) fill(cx + sx, slipY(z) + 1, z, cx + sx, ky + 4, z, SPRUCE_FENCE);
            }
        }
        // stem (bow, sweeping up) and sternpost
        fill(cx, ky + 1, zb + 1, cx, ky + 3, zb + 1, DARK_OAK_LOG_Y);
        fill(cx, ky + 3, zb + 2, cx, ky + 6, zb + 2, DARK_OAK_LOG_Y);
        fill(cx, ky + 6, zb + 3, cx, ky + 11, zb + 3, DARK_OAK_LOG_Y);
        fill(cx, slipY(zb + 2) + 1, zb + 2, cx, ky + 2, zb + 2, SPRUCE_LOG_Y);
        fill(cx, ky, zs - 1, cx, ky + hh + 3, zs - 1, DARK_OAK_LOG_Y);
        // scaffolding: towers every 4, plank walks at y71 + y75, ladders at the slip head
        for (int x : new int[]{64, 80}) {
            for (int z = 87; z <= 111; z += 4) fill(x, slipY(z) + 1, z, x, 76, z, OAK_FENCE);
            int in = x == 64 ? 1 : -1;
            for (int z = 87; z <= 111; z++) for (int y : new int[]{71, 75}) { set(x, y, z, slab("oak")); set(x + in, y, z, slab("oak")); }
            for (int z = 87; z <= 111; z += 4) { set(x, 71, z, OAK_FENCE); set(x, 75, z, OAK_FENCE); }
            for (int y = slipY(86) + 1; y <= 75; y++) set(x - in, y, 87, id("minecraft:ladder[facing=" + (x == 64 ? "west" : "east") + "]"));
        }
        // tools left on the walks + a few planks waiting to go on
        set(65, 72, 95, id("pixelpirates:rope_coil[facing=east]")); set(79, 72, 91, BARREL_UP);
        for (int z = 101; z <= 104; z++) set(65, 76, z, SPRUCE_LOG_Z);
        set(79, 76, 103, id("minecraft:lantern"));
    }

    /** Yard works: gateway, timber lean-to, seasoning stacks, sawpit, steam box, pitch kettle, spare masts + anchors. */
    private static void yardWorks() {
        // gateway from the quay (west) between the hall and the slip
        for (int z : new int[]{83, 86}) { fill(57, 56, z, 57, 65, z, STONE_BRICKS); fill(57, 66, z, 57, 71, z, DARK_OAK_LOG_Y); }
        for (int z = 82; z <= 87; z++) set(57, 72, z, DARK_OAK_LOG_Z);
        set(57, 73, 84, id("pixelpirates:anchor_block[facing=west]")); set(57, 73, 85, id("pixelpirates:anchor_block[facing=west]"));
        set(57, 71, 84, LANTERN_HANGING); set(57, 71, 85, LANTERN_HANGING);
        for (int x = 52; x <= 61; x++) for (int z = 83; z <= 85; z++) set(x, 65, z, (x + z) % 3 == 0 ? STONE_BRICKS : POLISHED_ANDESITE);
        // west strip: seasoning stacks with spacers, sawpit with a log over it
        for (int z = 61; z <= 71; z++) {
            set(53, 66, z, OAK_LOG_Z); set(54, 66, z, OAK_LOG_Z); set(55, 66, z, OAK_LOG_Z);
            set(53, 67, z, z % 3 == 0 ? OAK_FENCE : AIR); set(54, 67, z, z % 3 == 0 ? OAK_FENCE : AIR);
            if (z >= 63 && z <= 69) { set(53, 68, z, SPRUCE_LOG_Z); set(54, 68, z, SPRUCE_LOG_Z); set(55, 68, z, SPRUCE_LOG_Z); }
        }
        for (int x = 54; x <= 55; x++) for (int z = 74; z <= 79; z++) { set(x, 65, z, AIR); set(x, 64, z, AIR); set(x, 63, z, SPRUCE); }
        for (int z = 73; z <= 80; z++) set(55, 66, z, z == 73 || z == 80 ? SPRUCE_FENCE : AIR);
        for (int z = 73; z <= 80; z++) set(55, 67, z, SPRUCE_LOG_Z);
        set(54, 64, 76, id("minecraft:ladder[facing=east]")); set(54, 65, 76, id("minecraft:ladder[facing=east]"));
        set(53, 66, 75, id("minecraft:grindstone[face=floor,facing=south]")); set(53, 66, 78, BARREL_UP);
        // east strip: timber lean-to against the hall
        for (int z = 60; z <= 70; z += 5) fill(94, 66, z, 94, 69, z, SPRUCE_LOG_Y);
        for (int z = 59; z <= 71; z++) for (int x = 89; x <= 95; x++) set(x, 70 - (x - 89) / 3, z, slab("spruce"));
        for (int z = 61; z <= 69; z++) { set(90, 66, z, SPRUCE_LOG_Z); set(91, 66, z, SPRUCE_LOG_Z); set(92, 66, z, OAK_LOG_Z); set(90, 67, z, SPRUCE_LOG_Z); set(91, 67, z, OAK_LOG_Z); }
        // steam box (planks over a fire, a cauldron feeding it), pitch kettle, bending jig
        for (int x = 89; x <= 94; x++) { set(x, 66, 76, x == 89 ? CAMPFIRE : SPRUCE); set(x, 67, 76, x == 89 ? CAULDRON_WATER : SPRUCE); }
        set(95, 66, 76, BARREL_UP);
        set(92, 66, 73, CAMPFIRE); set(92, 67, 73, id("minecraft:cauldron"));
        set(90, 66, 80, id("minecraft:stripped_oak_wood")); set(91, 66, 81, id("minecraft:stripped_oak_wood")); set(92, 66, 81, id("minecraft:stripped_oak_wood"));
        set(93, 66, 80, SPRUCE_FENCE); set(89, 66, 79, SPRUCE_FENCE);
        // spare masts and fittings by the slip head, anchors waiting for a ship
        for (int z = 84; z <= 90; z++) set(88, 66, z, id("pixelpirates:ship_mast[axis=z]"));
        set(86, 66, 88, ANCHOR_BLOCK); set(90, 66, 86, ANCHOR_BLOCK);
        set(89, 66, 84, id("pixelpirates:rope_coil[facing=west]"));
        // the yard fence along the east edge, now a rail
        for (int z = 58; z <= 88; z++) set(96, 66, z, z % 4 == 0 ? SPRUCE_FENCE : railZ());
        keepClear(52, 58, 57, 85);
        keepClear(89, 58, 96, 90);
    }

    // ------------------------------------------------------------------
    // Market square
    // ------------------------------------------------------------------

    /**
     * THE WAVEBREAK BAZAAR (rebuilt 2026-10-01): the market square with a booth for every trader - north row (facing
     * south) Quartermaster, Fishmonger, Barkeep, Gunsmith; south row (facing north) Ship Chandler, Galley Cook, Curio
     * Dealer, Map Merchant. Each booth is 11 wide x 5 deep plus the counter row, with a pitched striped canopy and its
     * own dressing. The trader stands at booth (u 5, v 2) = {@link #boothStand}. The grand fountain holds the middle;
     * flag masts with lantern strings frame the avenue, carts, crates, palms and benches fill the aisles.
     */
    public static final int MARKET_GY = 67;
    /** {x1, row (0 = north, 1 = south)} per booth in PortTraderEntity.Kind order, then the Map Merchant. */
    public static final int[][] MARKET_BOOTHS = {
            {-37, 0}, {-21, 0}, {11, 0}, {11, 1}, {27, 0}, {-37, 1}, {-21, 1}, {27, 1}};

    /** Where booth i's keeper stands: {x, y, z, yaw} (block coords; yaw 0 = facing south). */
    public static int[] boothStand(int i) {
        int[] b = MARKET_BOOTHS[i];
        return new int[]{b[0] + 5, MARKET_GY + 1, b[1] == 0 ? 19 : 41, b[1] == 0 ? 0 : 180};
    }

    // booth-local writer: u = 0..10 along +x, v = 0 (back wall) .. 4 (counter) .. 5 (front step), dy above the paving
    private static int BX;
    private static boolean BSOUTH;

    private static void bs(int u, int dy, int v, int blockId) { set(BX + u, MARKET_GY + dy, BSOUTH ? 43 - v : 17 + v, blockId); }
    /** The side facing the customers / the back wall. */
    private static String fr() { return BSOUTH ? "north" : "south"; }
    private static String bk() { return BSOUTH ? "south" : "north"; }

    private static void marketSquare() {
        int gy = MARKET_GY;
        // Paving: stone bricks with sandstone diagonals, a cobble border, a polished ring round the fountain
        for (int x = -38; x <= 38; x++) {
            for (int z = 16; z <= 44; z++) {
                if (!SpawnIslandTerrain.inCityFootprint(x, z)) continue;
                double d = Math.hypot(x, z - 30);
                int mat = STONE_BRICKS;
                if (x == -38 || x == 38 || z == 16 || z == 44) mat = COBBLE;
                else if (d > 7.5 && d < 9.0) mat = ((int) Math.floor(Math.atan2(z - 30, x) * 16 / Math.PI) & 1) == 0 ? POLISHED_DIORITE : POLISHED_ANDESITE;
                else if (d >= 9.0 && d < 9.8) mat = POLISHED_BLACKSTONE;
                else if (((x + z) & 7) == 0) mat = SMOOTH_SANDSTONE;
                else if (((x - z) & 7) == 0) mat = POLISHED_ANDESITE;
                set(x, gy, z, mat);
                for (int y = gy + 1; y <= gy + 12; y++) set(x, y, z, AIR);
            }
        }
        grandFountain(gy);

        // the booths
        for (int i = 0; i < MARKET_BOOTHS.length; i++) {
            BX = MARKET_BOOTHS[i][0];
            BSOUTH = MARKET_BOOTHS[i][1] == 1;
            FOOTPRINTS.add(new int[]{BX, BSOUTH ? 38 : 17, BX + 10, BSOUTH ? 43 : 22});
            switch (i) {
                case 0 -> quartermasterBooth();
                case 1 -> fishmongerBooth();
                case 2 -> barkeepBooth();
                case 3 -> curioBooth();
                case 4 -> gunsmithBooth();
                case 5 -> chandlerBooth();
                case 6 -> cookBooth();
                default -> mapBooth();
            }
        }

        // flag masts flanking the avenue at both ends of the aisle, lantern strings between them
        for (int sx : new int[]{-7, 7})
            for (int sz : new int[]{24, 36}) flagMast(sx, gy, sz, sz == 24 ? "red" : "blue");
        int chainX = id("minecraft:chain[axis=x]"), chainZ = id("minecraft:chain[axis=z]");
        for (int sz : new int[]{24, 36})
            for (int x = -6; x <= 6; x++) {
                set(x, gy + 8, sz, chainX);
                if (x % 3 == 0) set(x, gy + 7, sz, LANTERN_HANGING);
            }
        for (int sx : new int[]{-7, 7})
            for (int z = 25; z <= 35; z++) {
                set(sx, gy + 8, z, chainZ);
                if ((z - 25) % 3 == 1) set(sx, gy + 7, z, LANTERN_HANGING);
            }

        // palms in the gaps between the booths, in stone planters
        for (int[] p : new int[][]{{-24, 19}, {24, 19}, {-24, 41}, {24, 41}}) {
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) set(p[0] + dx, gy + 1, p[1] + dz, (dx == 0 && dz == 0) ? GRASS : slab("stone_brick"));
            set(p[0], gy, p[1], DIRT);
            palm(p[0], gy + 1, p[1], 6);
            set(p[0] + 1, gy + 2, p[1] + 1, id("minecraft:fern"));
        }
        // benches facing the fountain (4 diagonal pairs)
        for (int[] b : new int[][]{{-9, 26, 1}, {8, 26, 1}, {-9, 34, 0}, {8, 34, 0}}) {
            String f = b[2] == 1 ? "north" : "south";                 // back toward the booths, seat toward the fountain
            set(b[0], gy + 1, b[1], stairs("spruce", f));
            set(b[0] + 1, gy + 1, b[1], stairs("spruce", f));
            set(b[0] - 1, gy + 1, b[1], trapdoorOpen("spruce", "west"));
            set(b[0] + 2, gy + 1, b[1], trapdoorOpen("spruce", "east"));
        }

        // west aisle: the old well, a hand-cart of produce, crates
        int wx = -30, wz = 30;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                set(wx + dx, gy + 1, wz + dz, (dx == 0 && dz == 0) ? WATER : COBBLE_WALL);
        set(wx - 1, gy + 2, wz - 1, OAK_FENCE); set(wx + 1, gy + 2, wz - 1, OAK_FENCE);
        set(wx - 1, gy + 2, wz + 1, OAK_FENCE); set(wx + 1, gy + 2, wz + 1, OAK_FENCE);
        fill(wx - 1, gy + 3, wz - 1, wx + 1, gy + 3, wz + 1, slab("oak"));
        set(wx, gy + 3, wz, CHAIN);
        handCart(-36, gy, 27);
        set(-35, gy + 1, 33, id("pixelpirates:cargo_crate[facing=east]"));
        set(-35, gy + 2, 33, id("pixelpirates:cargo_crate[facing=south]"));
        set(-36, gy + 1, 33, id("pixelpirates:cargo_crate[facing=north]"));
        set(-36, gy + 1, 34, BARREL_UP);
        set(-24, gy + 1, 27, id("pixelpirates:rope_coil[facing=east]"));
        set(-23, gy + 1, 33, id("pixelpirates:lobster_pot"));
        set(-15, gy + 1, 30, id("pixelpirates:tiki_torch"));
        set(-37, gy + 1, 30, id("pixelpirates:tiki_torch"));

        // east aisle: the bounty board, the market bell, a crier's crate stage
        // the 3x2 bounty board (BountyBoardBlock): parts run along facing.rotateYClockwise() = north for a west-facing board
        for (int part = 0; part < 6; part++)
            set(36, gy + 1 + part / 3, 30 - (part % 3 - 1), id("pixelpirates:bounty_board[facing=west,part=" + part + "]"));
        set(37, gy + 1, 29, DARK_OAK_LOG_Y); set(37, gy + 1, 31, DARK_OAK_LOG_Y);
        set(37, gy + 2, 29, DARK_OAK_FENCE); set(37, gy + 2, 31, DARK_OAK_FENCE);
        set(33, gy + 1, 26, id("minecraft:bell[attachment=floor,facing=west]"));
        set(36, gy + 1, 36, id("pixelpirates:cargo_crate[facing=west]"));
        set(37, gy + 1, 36, id("pixelpirates:cargo_crate[facing=west]"));
        set(37, gy + 1, 37, id("pixelpirates:cargo_crate[facing=west]"));
        set(37, gy + 2, 36, id("pixelpirates:rope_coil[facing=west]"));
        set(24, gy + 1, 26, id("pixelpirates:barrel_stool[facing=west]"));
        set(24, gy + 1, 34, id("pixelpirates:barrel_stool[facing=west]"));
        set(15, gy + 1, 30, id("pixelpirates:tiki_torch"));
        set(37, gy + 1, 26, id("pixelpirates:tiki_torch"));
        set(37, gy + 1, 34, id("pixelpirates:tiki_torch"));
    }

    private static void grandFountain(int gy) {
        int cz = 30, rim = id("minecraft:smooth_stone_slab[type=bottom]");
        for (int x = -7; x <= 7; x++)
            for (int z = cz - 7; z <= cz + 7; z++) {
                double d = Math.hypot(x, z - cz);
                if (d > 6.6) continue;
                set(x, gy, z, d < 5.6 ? id("minecraft:prismarine_bricks") : CHISELED_STONE_BRICKS);
                if (d >= 5.6) { set(x, gy + 1, z, STONE_BRICKS); set(x, gy + 2, z, rim); }
                else set(x, gy + 1, z, WATER);
                if (d < 5.6 && d > 2) set(x, gy, z, ((x * 7 + z * 3) & 7) == 0 ? SEA_LANTERN : id("minecraft:prismarine_bricks"));
            }
        // the pillar, the upper basin and the anchor
        fill(-1, gy + 1, cz - 1, 1, gy + 2, cz + 1, CHISELED_STONE_BRICKS);
        for (int x = -2; x <= 2; x++)
            for (int z = cz - 2; z <= cz + 2; z++) {
                boolean edge = Math.abs(x) == 2 || Math.abs(z - cz) == 2;
                if (Math.abs(x) == 2 && Math.abs(z - cz) == 2) continue;
                set(x, gy + 3, z, edge ? id("minecraft:stone_brick_stairs[facing=" + (x == 2 ? "west" : x == -2 ? "east" : z > cz ? "north" : "south") + ",half=top]") : STONE_BRICKS);
                if (edge) set(x, gy + 4, z, rim);
                else set(x, gy + 4, z, WATER);
            }
        set(0, gy + 4, cz, STONE_BRICK_WALL);
        set(0, gy + 5, cz, STONE_BRICK_WALL);
        set(0, gy + 6, cz, ANCHOR_BLOCK);
        set(0, gy + 7, cz, id("minecraft:lightning_rod"));
        // four spouts: prismarine walls with a lantern each
        for (int[] s : new int[][]{{0, -5}, {0, 5}, {-5, 0}, {5, 0}}) {
            set(s[0], gy + 1, cz + s[1], id("minecraft:prismarine_wall"));
            set(s[0], gy + 2, cz + s[1], id("minecraft:prismarine_wall"));
            set(s[0], gy + 3, cz + s[1], LANTERN);
        }
    }

    private static void flagMast(int x, int gy, int z, String colour) {
        for (int y = gy + 1; y <= gy + 9; y++) set(x, y, z, STRIPPED_SPRUCE_Y);
        set(x, gy + 1, z, id("minecraft:stone_brick_wall"));
        set(x, gy + 10, z, id("minecraft:lightning_rod"));
        int dir = x < 0 ? -1 : 1;                                     // the flag streams away from the avenue
        for (int k = 1; k <= 3; k++)
            for (int y = gy + 8; y <= gy + 9; y++) set(x + dir * k, y, z, wool((k + y) % 2 == 0 ? colour : "white"));
        set(x + dir * 3, gy + 8, z, AIR);                              // a swallowtail
    }

    private static void handCart(int x, int gy, int z) {
        int deck = slabTop("spruce");
        for (int dx = 0; dx <= 2; dx++)
            for (int dz = 0; dz <= 1; dz++) set(x + dx, gy + 1, z + dz, deck);
        set(x + 1, gy + 1, z - 1, id("minecraft:grindstone[face=wall,facing=north]"));
        set(x + 1, gy + 1, z + 2, id("minecraft:grindstone[face=wall,facing=south]"));
        set(x + 3, gy + 1, z, SPRUCE_FENCE); set(x + 3, gy + 1, z + 1, SPRUCE_FENCE);
        set(x, gy + 2, z, MELON); set(x + 1, gy + 2, z, PUMPKIN); set(x + 2, gy + 2, z, HAY);
        set(x, gy + 2, z + 1, HAY); set(x + 1, gy + 2, z + 1, COCONUT_BLOCK);
        set(x + 2, gy + 2, z + 1, BARREL_UP);
    }

    /**
     * The shared booth: plank floor, corner posts, back wall, low side walls, a counter row at v 4, a pitched canopy
     * (ridge over v 0..2 one higher than the eave over v 3..5) in two-colour stripes, lanterns under the eave.
     */
    private static void booth(String wood, String postBlock, int wallId, int counterId, int canopyA, int canopyB) {
        int plank = id("minecraft:" + wood + "_planks"), post = id(postBlock + "[axis=y]"), fence = id("minecraft:" + wood + "_fence");
        for (int u = 0; u <= 10; u++)
            for (int v = 0; v <= 4; v++) bs(u, 0, v, plank);
        for (int u = 1; u <= 9; u++) bs(u, 0, 5, plank);
        for (int dy = 1; dy <= 4; dy++) { bs(0, dy, 0, post); bs(10, dy, 0, post); bs(0, dy, 4, post); bs(10, dy, 4, post); }
        for (int u = 1; u <= 9; u++)
            for (int dy = 1; dy <= 3; dy++) bs(u, dy, 0, wallId);
        for (int v = 1; v <= 3; v++) {
            bs(0, 1, v, plank); bs(10, 1, v, plank);
            bs(0, 2, v, fence); bs(10, 2, v, fence);
        }
        for (int u = 1; u <= 9; u++) bs(u, 1, 4, counterId);
        // canopy: stripes run front to back (every other u), overhanging the sides by one
        for (int u = -1; u <= 11; u++) {
            int c = ((u + 1) & 1) == 0 ? canopyA : canopyB;
            for (int v = 0; v <= 2; v++) bs(u, 5, v, c);
            for (int v = 3; v <= 5; v++) bs(u, 4, v, c);
            bs(u, 4, -1, c);                                              // a lip over the back wall
        }
        for (int v = 0; v <= 2; v++) { bs(0, 4, v, canopyA); bs(10, 4, v, canopyA); }
        bs(2, 3, 5, LANTERN_HANGING);
        bs(8, 3, 5, LANTERN_HANGING);
        bs(5, 4, 1, LANTERN_HANGING);
    }

    private static int f(String block, String facing) { return id("pixelpirates:" + block + "[facing=" + facing + "]"); }

    private static int mc(String block) { return id("minecraft:" + block); }

    /** Quartermaster: farm and camp goods - sacks, crates, seeds and lanterns. */
    private static void quartermasterBooth() {
        booth("spruce", "minecraft:spruce_log", SPRUCE, f("cargo_crate", "south"), wool("green"), wool("white"));
        for (int u = 1; u <= 9; u++) bs(u, 1, 4, u % 2 == 0 ? f("cargo_crate", fr()) : mc("spruce_planks"));
        bs(1, 1, 1, HAY); bs(1, 2, 1, HAY); bs(1, 1, 2, HAY);
        bs(2, 1, 1, BARREL_UP); bs(2, 2, 1, BARREL_UP);
        bs(8, 1, 1, f("cargo_crate", fr())); bs(8, 2, 1, f("cargo_crate", fr())); bs(9, 1, 1, PUMPKIN); bs(9, 1, 2, MELON);
        bs(9, 2, 1, id("pixelpirates:fish_trap"));
        for (int u = 3; u <= 7; u++) bs(u, 3, 1, id("minecraft:spruce_trapdoor[facing=" + fr() + ",half=top,open=true]"));
        bs(4, 2, 1, mc("composter")); bs(6, 2, 1, mc("barrel[facing=" + fr() + "]"));
        bs(2, 2, 4, LANTERN); bs(6, 2, 4, mc("potted_red_tulip")); bs(8, 2, 4, mc("flower_pot"));
        bs(0, 1, 5, BARREL_UP); bs(10, 1, 5, HAY);
        bs(5, 6, 1, HAY);
    }

    /** Fishmonger: an ice counter, the day's catch, nets and pots, a marlin over the stall. */
    private static void fishmongerBooth() {
        booth("birch", "minecraft:birch_log", BIRCH, mc("packed_ice"), wool("cyan"), wool("white"));
        for (int u = 1; u <= 9; u++) bs(u, 1, 4, u % 3 == 0 ? mc("blue_ice") : mc("packed_ice"));
        for (int u = 1; u <= 9; u += 2) if (u != 5) bs(u, 2, 4, mc("sea_pickle[pickles=" + (1 + u % 4) + ",waterlogged=false]"));
        bs(4, 2, 4, mc("dried_kelp_block")); bs(6, 2, 4, mc("dried_kelp_block"));
        bs(5, 3, 0, f("golden_marlin_trophy", fr()));
        bs(2, 2, 1, f("hanging_net", fr())); bs(8, 2, 1, f("hanging_net", fr()));
        bs(1, 1, 1, id("pixelpirates:lobster_pot")); bs(1, 1, 2, id("pixelpirates:lobster_pot")); bs(1, 2, 1, id("pixelpirates:fish_trap"));
        bs(9, 1, 1, CAULDRON_WATER); bs(9, 1, 2, BARREL_UP); bs(8, 1, 1, mc("dried_kelp_block"));
        bs(3, 1, 1, BARREL_UP); bs(7, 1, 1, BARREL_UP);
        bs(0, 1, 5, id("pixelpirates:lobster_pot")); bs(10, 1, 5, mc("barrel[facing=up]"));
        bs(5, 6, 1, mc("dried_kelp_block"));
    }

    /** Barkeep: the grog stand - casks, a rum rack, candles and stools along the counter. */
    private static void barkeepBooth() {
        booth("dark_oak", "minecraft:dark_oak_log", DARK_OAK, mc("stripped_dark_oak_log[axis=x]"), wool("red"), wool("yellow"));
        bs(1, 1, 1, GROG_BARREL); bs(2, 1, 1, GROG_BARREL); bs(1, 2, 1, GROG_BARREL);
        bs(3, 1, 1, f("rum_rack", fr())); bs(4, 1, 1, f("rum_rack", fr())); bs(3, 2, 1, f("rum_rack", fr())); bs(4, 2, 1, f("rum_rack", fr()));
        bs(7, 1, 1, mc("barrel[facing=" + fr() + "]")); bs(8, 1, 1, mc("barrel[facing=" + fr() + "]")); bs(9, 1, 1, mc("barrel[facing=" + fr() + "]"));
        bs(8, 2, 1, mc("barrel[facing=" + fr() + "]"));
        bs(6, 1, 1, BREWING);
        bs(2, 2, 4, mc("red_candle[candles=3,lit=true]")); bs(6, 2, 4, mc("brewing_stand")); bs(8, 2, 4, mc("orange_candle[candles=2,lit=true]"));
        for (int u : new int[]{2, 5, 8}) bs(u, 1, 6, f("barrel_stool", bk()));
        bs(5, 6, 1, GROG_BARREL);
    }

    /** Curio Dealer: shelves of books and oddities - skulls, amethyst, candles, a pot or two. */
    private static void curioBooth() {
        booth("dark_oak", "minecraft:dark_oak_log", BOOKSHELF, mc("dark_oak_planks"), wool("purple"), wool("magenta"));
        bs(1, 1, 1, mc("decorated_pot")); bs(9, 1, 1, mc("decorated_pot"));
        bs(1, 1, 2, mc("lodestone")); bs(1, 2, 2, mc("amethyst_cluster[facing=up]"));
        bs(9, 1, 2, BREWING);
        bs(2, 2, 4, mc("skeleton_skull[rotation=" + (BSOUTH ? 8 : 0) + "]"));
        bs(4, 2, 4, mc("amethyst_cluster[facing=up]"));
        bs(3, 2, 4, mc("purple_candle[candles=4,lit=true]"));
        bs(6, 2, 4, mc("large_amethyst_bud[facing=up]"));
        bs(8, 2, 4, mc("potted_wither_rose"));
        bs(3, 3, 1, LANTERN_HANGING);
        bs(5, 6, 1, mc("skeleton_skull[rotation=" + (BSOUTH ? 8 : 0) + "]"));
        bs(0, 1, 5, mc("decorated_pot")); bs(10, 1, 5, mc("amethyst_block"));
    }

    /** Gunsmith: blackstone counter, a forge corner, powder kegs and two display cannons at the front. */
    private static void gunsmithBooth() {
        booth("spruce", "minecraft:stripped_spruce_log", mc("polished_blackstone_bricks"), mc("polished_blackstone"), wool("gray"), wool("black"));
        bs(1, 1, 1, mc("blast_furnace[facing=" + fr() + "]"));
        bs(2, 1, 1, SMITHING); bs(3, 1, 2, mc("anvil[facing=east]"));
        bs(8, 1, 1, GUNPOWDER_BARREL); bs(9, 1, 1, GUNPOWDER_BARREL); bs(9, 2, 1, GUNPOWDER_BARREL); bs(9, 1, 2, GUNPOWDER_BARREL);
        bs(7, 1, 1, GRINDSTONE);
        for (int u = 4; u <= 6; u++) bs(u, 2, 1, IRON_BARS);
        bs(5, 3, 1, mc("chain[axis=y]"));
        bs(2, 2, 4, mc("polished_blackstone_pressure_plate")); bs(6, 2, 4, LANTERN); bs(8, 2, 4, mc("chain[axis=x]"));
        bs(0, 1, 5, f("display_cannon", fr())); bs(10, 1, 5, f("display_cannon", fr()));
        bs(5, 6, 1, f("display_cannon", fr()));
    }

    /** Ship Chandler: a wall of sailcloth with the ship's wheel on it, rope, chain, an anchor, pots of tar. */
    private static void chandlerBooth() {
        booth("spruce", "minecraft:spruce_log", id("pixelpirates:white_sail_canvas"), mc("spruce_planks"), wool("blue"), wool("white"));
        for (int u = 1; u <= 9; u++) bs(u, 2, 0, id(u % 2 == 0 ? "pixelpirates:striped_sail_canvas" : "pixelpirates:white_sail_canvas"));
        bs(5, 2, 1, f("ships_wheel", fr()));
        bs(1, 1, 1, ANCHOR_BLOCK); bs(2, 1, 1, f("rope_coil", fr())); bs(8, 1, 1, f("rope_coil", fr())); bs(9, 1, 1, BARREL_UP);
        bs(9, 2, 1, f("rope_coil", fr())); bs(9, 1, 2, id("pixelpirates:sea_chest[facing=" + fr() + "]"));
        bs(3, 3, 1, id("pixelpirates:hanging_rope[end=false]")); bs(3, 2, 1, id("pixelpirates:hanging_rope[end=true]"));
        bs(7, 3, 1, id("pixelpirates:hanging_rope[end=false]")); bs(7, 2, 1, id("pixelpirates:hanging_rope[end=true]"));
        bs(2, 2, 4, f("rope_coil", fr())); bs(6, 2, 4, LANTERN); bs(8, 2, 4, mc("chain[axis=x]"));
        bs(0, 1, 5, id("pixelpirates:mooring_post")); bs(10, 1, 5, id("pixelpirates:mooring_post"));
        bs(5, 6, 1, f("ships_wheel", fr()));
    }

    /** Galley Cook: a thatched cookshop - smoker and campfire, the stock-pot, fruit and a cake on the counter. */
    private static void cookBooth() {
        int thatch = id("pixelpirates:thatch");
        booth("oak", "pixelpirates:palm_log", PALM_PLANKS, mc("bricks"), thatch, thatch);
        bs(1, 1, 1, mc("smoker[facing=" + fr() + ",lit=true]")); bs(2, 1, 1, mc("furnace[facing=" + fr() + ",lit=true]"));
        bs(5, 1, 1, CAULDRON_WATER); bs(5, 2, 1, mc("chain[axis=y]"));
        bs(8, 1, 1, mc("campfire[lit=true,signal_fire=false]")); bs(9, 1, 1, BARREL_UP); bs(9, 1, 2, HAY);
        bs(1, 2, 1, mc("spruce_trapdoor[facing=" + fr() + ",half=top,open=true]"));
        bs(2, 2, 4, mc("cake")); bs(4, 2, 4, MELON); bs(6, 2, 4, PUMPKIN); bs(8, 2, 4, mc("flower_pot"));
        bs(0, 1, 5, id("pixelpirates:tiki_torch")); bs(10, 1, 5, id("pixelpirates:tiki_torch"));
        bs(5, 6, 1, COCONUT_BLOCK);
    }

    /** The Map Merchant's pavilion: navy and gold, map table on the counter, charts, a lectern and a compass rose. */
    private static void mapBooth() {
        booth("dark_oak", "minecraft:dark_oak_log", DARK_OAK, mc("dark_oak_planks"), wool("blue"), wool("yellow"));
        bs(5, 1, 4, f("map_table", fr()));
        bs(1, 1, 1, BOOKSHELF); bs(1, 2, 1, BOOKSHELF); bs(9, 1, 1, BOOKSHELF); bs(9, 2, 1, BOOKSHELF);
        bs(2, 1, 1, CARTOGRAPHY); bs(8, 1, 1, CARTOGRAPHY);
        bs(3, 1, 1, mc("lectern[facing=" + fr() + "]"));
        bs(5, 2, 0, MAP_BLOCK); bs(4, 2, 0, id("pixelpirates:pirate_diary_block")); bs(6, 2, 0, id("pixelpirates:treasure_block"));
        bs(2, 2, 4, mc("candle[candles=3,lit=true]")); bs(8, 2, 4, mc("potted_fern"));
        bs(0, 1, 5, mc("lantern")); bs(10, 1, 5, mc("lantern"));
        bs(5, 6, 1, MAP_BLOCK);
        // a compass rose inlaid in the paving in front of the pavilion
        for (int du = -3; du <= 3; du++)
            for (int dv = 6; dv <= 12; dv++) {
                int ddv = dv - 9;
                double d = Math.hypot(du, ddv);
                if (d > 3.3) continue;
                int m = (du == 0 || ddv == 0) ? (d < 0.5 ? mc("gold_block") : POLISHED_DIORITE) : (Math.abs(du) == Math.abs(ddv) ? POLISHED_ANDESITE : mc("smooth_sandstone"));
                bs(5 + du, 0, dv, m);
            }
    }

    // ------------------------------------------------------------------
    // Big buildings — mid terrace
    // ------------------------------------------------------------------

    /** "The Grog Barrel" — two-story tavern east of the market square. */
    // ================================================================ THE GROG BARREL (#7, rebuilt 2026-10-01)
    /*
     * The port's rowdy tavern, x 48..76, z 16..44. You come in through a GIANT BARREL lying against the west front (its
     * end is the door) under two hanging TAVERN_SIGNs. Inside: THE GREAT HALL open to the rafters (x49..62) with the long
     * BAR on the north side (kegs that pour for a doubloon, spirit bottles, the rum rack, casks, barrel stools), trestle
     * tables full of tankards, a minstrels' STAGE and the HEARTH on the south wall, a U-shaped GALLERY round the hall at
     * y72; east of it THE DEN under the upper floor - two LIAR'S DICE tables, CROWN & ANCHOR, the ROULETTE wheel, a card
     * table and the dartboard; the back room with the stair; a CELLAR under the bar (casks, the rum still's kit, kegs).
     * Upstairs: the HIGH ROLLERS' ROOM (a private dice table, a box over the hall) and the landlord's cabin. Outside: a
     * BEER GARDEN on the east side (picnic tables under striped umbrellas, a grill, lantern strings). Slate roof, red
     * plaster frame, dark shutters. Furnished through ip() like the inn.
     */
    private static final int TG = 67, TF2 = 72, TTOP = 77, TROOF = 78;

    private static void tavern() {
        final int x1 = 48, x2 = 76, z1 = 16, z2 = 44;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(42, TG + 1, z1 - 1, 84, 100, z2 + 1, AIR);
        fill(x1, TG - 2, z1, x2, TG - 1, z2, COBBLE);
        // floors: dark planks in the hall, stripped wood behind the bar, spruce in the den; upper floor over the den, the
        // back room and a U-shaped gallery round the hall (north z17..20, south z40..43, west x49..51)
        for (int x = x1 + 1; x < x2; x++)
            for (int z = z1 + 1; z < z2; z++) {
                boolean den = x >= 63, bar = !den && z <= 21;
                set(x, TG, z, den ? SPRUCE : bar ? id("minecraft:stripped_dark_oak_wood") : ((x + z) % 7 == 0 ? SPRUCE : DARK_OAK));
                boolean upper = den || z <= 20 || z >= 40 || x <= 51;
                if (upper) set(x, TF2, z, SPRUCE);
            }
        // outer walls
        int red = id("minecraft:red_terracotta");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) continue;
                tavWallColumn(x, z, ez ? x - x1 : z - z1, ex && ez, ez, red);
            }
        // partitions: back room / den (z21, both floors), High Rollers / cabin (z34), the cabin's hall side (x63)
        fill(64, TG + 1, 21, 75, TF2 - 1, 21, DARK_OAK); fill(64, TF2 + 1, 21, 75, TTOP - 1, 21, DARK_OAK);
        fill(64, TF2 + 1, 34, 75, TTOP - 1, 34, DARK_OAK);
        fill(63, TF2 + 1, 35, 63, TTOP - 1, 43, DARK_OAK);
        for (int z = 22; z <= 33; z++) set(63, TF2 + 1, z, railZ());                // the High Rollers' box over the hall
        for (int x = 52; x <= 62; x++) { set(x, TF2 + 1, 21, railX()); set(x, TF2 + 1, 39, railX()); }
        for (int z = 21; z <= 39; z++) set(52, TF2 + 1, z, railZ());
        set(52, TF2 + 1, 21, SPRUCE_FENCE); set(52, TF2 + 1, 39, SPRUCE_FENCE);
        for (int z = 23; z <= 43; z++) if (z < 29 || z > 32) set(63, TG + 1, z, z % 4 == 0 ? DARK_OAK_FENCE : id("minecraft:dark_oak_fence[north=true,south=true]"));
        // doors
        innDoor(70, TG, z1, "spruce", true); innDoor(70, TG, z2, "spruce", true);
        innDoor(70, TG, 21, "dark_oak", true); innDoor(70, TF2, 21, "dark_oak", true);
        innDoor(68, TF2, 34, "crimson", true); innDoor(63, TF2, 41, "dark_oak", false);
        for (int z = 30; z <= 31; z++) { set(x2, TG + 1, z, door("spruce", "east", false)); set(x2, TG + 2, z, door("spruce", "east", true)); innReserve(x2 - 1, TG + 1, z); innReserve(x2 + 1, TG + 1, z); }
        set(x2, TG + 3, 30, DARK_OAK_LOG_Z); set(x2, TG + 3, 31, DARK_OAK_LOG_Z);
        for (int z = 28; z <= 30; z++) { fill(x1, TG + 1, z, x1, TG + 3, z, AIR); innReserve(x1 + 1, TG + 1, z); innReserve(x1 + 1, TG + 2, z); }
        // the stair (back room, along z20, rising east) + its hole and rail
        for (int k = 0; k < 5; k++) set(64 + k, TG + 1 + k, 20, stairs("dark_oak", "east"));
        for (int x = 64; x <= 67; x++) { set(x, TF2, 20, AIR); set(x, TF2 + 1, 19, railX()); }
        innReserve(63, TG + 1, 20); innReserve(63, TG + 2, 20); innReserve(69, TF2 + 1, 20); innReserve(69, TF2 + 2, 20);
        // windows
        tavWindows();
        tavRoof();
        tavBarrelEntrance();
        tavCellar();
        tavBeerGarden();
        // hall rafters: tie beams across under the roof, wagon-wheel chandeliers hanging from them
        for (int x = 54; x <= 62; x += 4) for (int z = 21; z <= 39; z++) set(x, TTOP + 1, z, DARK_OAK_LOG_Z);
        for (int[] c : new int[][]{{56, 27}, {56, 33}, {60, 30}}) chandelierRing(c[0], TTOP + 1, c[1]);
        // the hearth on the south wall + its chimney
        fill(56, TG + 1, z2, 60, TG + 4, z2, id("minecraft:stone_bricks"));
        fill(57, TG - 1, z2 + 1, 59, 96, z2 + 2, id("minecraft:stone_bricks"));
        set(58, 97, z2 + 1, CAMPFIRE); set(58, 97, z2 + 2, CAMPFIRE);
        fill(57, TG + 1, z2 - 1, 59, TG + 2, z2 - 1, AIR);
        set(58, TG + 1, z2 - 1, CAMPFIRE); set(57, TG + 1, z2 - 1, id("minecraft:stone_brick_wall")); set(59, TG + 1, z2 - 1, id("minecraft:stone_brick_wall"));
        for (int x = 56; x <= 60; x++) set(x, TG + 3, z2 - 1, id("minecraft:stone_brick_slab[type=top]"));
        // two tavern signs on the west front, one over the garden gate
        set(x1 - 1, TF2 + 2, 21, id("pixelpirates:tavern_sign[facing=west]"));
        set(x1 - 1, TF2 + 2, 37, id("pixelpirates:tavern_sign[facing=west]"));
        set(x2 + 1, TF2 + 2, 24, id("pixelpirates:tavern_sign[facing=east]"));
        lamppost(44, TG, 22); lamppost(44, TG, 36);
    }

    private static void tavWallColumn(int x, int z, int u, boolean corner, boolean alongX, int infill) {
        int band = alongX ? DARK_OAK_LOG_X : DARK_OAK_LOG_Z;
        for (int y = TG + 1; y <= TTOP; y++) {
            int b;
            if (y < TF2) {
                int h = Math.floorMod(x * 31 + y * 17 + z * 7, 9);
                b = corner ? (y % 2 == 0 ? CHISELED_STONE_BRICKS : STONE_BRICKS) : y == TG + 1 ? COBBLE
                        : h == 0 ? MOSSY_STONE_BRICKS : h == 1 ? CRACKED_STONE_BRICKS : h == 2 ? COBBLE : STONE_BRICKS;
            } else if (y == TF2 || y == TTOP) b = corner ? DARK_OAK_LOG_Y : band;
            else if (corner || u % 4 == 0) b = DARK_OAK_LOG_Y;
            else b = (y == TF2 + 2 || y == TF2 + 3) && u % 4 == 2 ? infill : (u + y) % 5 == 0 ? id("minecraft:brown_terracotta") : infill;
            set(x, y, z, b);
        }
    }

    private static void tavWindows() {
        int[][] sides = {{48, 16, 1, 0, 0, -1}, {48, 44, 1, 0, 0, 1}, {48, 16, 0, 1, -1, 0}, {76, 16, 0, 1, 1, 0}};
        String[] out = {"north", "south", "west", "east"};
        for (int s = 0; s < 4; s++) {
            int[] sd = sides[s];
            boolean alongX = sd[2] == 1;
            int pane = id("minecraft:glass_pane[" + (alongX ? "east=true,west=true" : "north=true,south=true") + "]");
            int shutter = id("minecraft:dark_oak_trapdoor[facing=" + out[s] + ",half=bottom,open=true]");
            for (int u = 2; u <= 26; u += 4) {
                int x = sd[0] + sd[2] * u, z = sd[1] + sd[3] * u;
                for (int fy : new int[]{TG, TF2}) {
                    int y = fy + 2;
                    if (getRaw(x - sd[4], y, z - sd[5]) != AIR || isDoorCell(x, fy + 1, z) || getRaw(x, y, z) == AIR) continue;
                    set(x, y, z, pane); set(x, y + 1, z, pane);
                    if (fy == TF2) for (int k : new int[]{-1, 1}) {
                        int sx = x + sd[4] + (alongX ? k : 0), sz = z + sd[5] + (alongX ? 0 : k);
                        set(sx, y, sz, shutter); set(sx, y + 1, sz, shutter);
                    }
                }
            }
        }
    }

    /** Slate gable roof, ridge along x at z30, the underside boarded in spruce; gable ends with a round window. */
    private static void tavRoof() {
        int tile = id("minecraft:deepslate_tiles");
        for (int x = 47; x <= 77; x++)
            for (int z = 15; z <= 45; z++) {
                int h = Math.min(z - 15, 45 - z), y = TROOF + h;
                set(x, y, z, h == 15 ? tile : stairs("deepslate_tile", z < 30 ? "south" : "north"));
                if (h == 15) set(x, y + 1, z, id("minecraft:deepslate_tile_slab"));
                if (h >= 1 && x >= 48 && x <= 76) set(x, y - 1, z, SPRUCE);
                if (x == 48 || x == 76) {                                            // the gable ends
                    for (int yy = TROOF; yy < y - 1; yy++) {
                        int dz = Math.abs(z - 30), dy = Math.abs(yy - 84);
                        boolean round = dz * dz + dy * dy <= 5;
                        set(x, yy, z, round ? id("minecraft:glass_pane[north=true,south=true]") : (z % 4 == 0 ? DARK_OAK_LOG_Y : id("minecraft:red_terracotta")));
                    }
                }
            }
    }

    /** The giant barrel lying against the west front: hooped staves, the door in its end, lanterns either side. */
    private static void tavBarrelEntrance() {
        final double cy = 71.5, cz = 29, r = 4.6;
        int hoop = id("minecraft:polished_deepslate");
        for (int x = 43; x <= 47; x++)
            for (int y = TG; y <= 77; y++)
                for (int z = 24; z <= 34; z++) {
                    double d = Math.sqrt((y - cy) * (y - cy) + (z - cz) * (z - cz));
                    if (d > r) continue;
                    if (x == 43) {                                                     // the end, with its rings
                        set(x, y, z, d > 3.6 ? DARK_OAK : d > 2.2 ? SPRUCE : id("minecraft:stripped_spruce_wood"));
                        continue;
                    }
                    if (d > 3.6) {
                        double ang = Math.atan2(y - cy, z - cz);
                        int stave = (int) Math.floor((ang + Math.PI) / (Math.PI / 7));
                        set(x, y, z, x == 44 || x == 46 ? hoop : (stave % 2 == 0 ? SPRUCE : id("minecraft:stripped_spruce_wood")));
                    } else set(x, y, z, y == TG ? SPRUCE : AIR);
                }
        set(43, TG + 1, 29, door("spruce", "west", false)); set(43, TG + 2, 29, door("spruce", "west", true));
        set(43, TG + 3, 29, DARK_OAK_LOG_Z);
        set(42, TG + 4, 29, id("minecraft:cut_copper_stairs[facing=east,half=top]"));               // the bung + tap over the door
        set(42, TG + 5, 29, id("minecraft:cut_copper"));
        set(42, TG + 3, 27, LANTERN); set(42, TG + 3, 31, LANTERN);
        set(42, TG + 2, 27, SPRUCE_FENCE); set(42, TG + 2, 31, SPRUCE_FENCE); set(42, TG + 1, 27, SPRUCE_FENCE); set(42, TG + 1, 31, SPRUCE_FENCE);
        for (int z = 27; z <= 31; z++) set(42, TG, z, POLISHED_ANDESITE);
        innReserve(42, TG + 1, 29); innReserve(44, TG + 1, 29); innReserve(44, TG + 2, 29);
        hangLantern(46, TG + 4, 29);
        ip(45, TG + 1, 26, BARREL_UP); ip(45, TG + 1, 32, BARREL_UP); ip(45, TG + 2, 26, id("pixelpirates:tankard[facing=east,count=2]"));
    }

    /** The cellar under the bar: casks, kegs, crates, reached by a hatch + ladder behind the bar. */
    private static void tavCellar() {
        final int y0 = 62, y1 = 66;
        fill(49, y0 - 1, 17, 59, y1, 24, COBBLE);
        fill(50, y0, 18, 58, y1 - 1, 23, AIR);
        fill(50, y0 - 1, 18, 58, y0 - 1, 23, id("minecraft:stone_bricks"));
        for (int x = 50; x <= 58; x += 4) fill(x, y0, 18, x, y1 - 1, 18, DARK_OAK_LOG_Y);
        set(51, TG, 19, id("minecraft:spruce_trapdoor[facing=north,half=top,open=false]"));
        set(51, y1, 19, AIR);
        for (int y = y0; y <= y1; y++) set(51, y, 18, COBBLE);
        for (int y = y0; y <= y1; y++) set(51, y, 19, id("minecraft:ladder[facing=south]"));
        set(51, TG - 1, 18, COBBLE);
        // contents
        for (int x = 53; x <= 57; x += 2) set(x, y0, 18, id("pixelpirates:aging_cask[facing=south]"));
        for (int x = 52; x <= 58; x++) { set(x, y0, 23, barrel("north")); if (x % 2 == 0) set(x, y0 + 1, 23, barrel("north")); }
        set(56, y0, 21, id("pixelpirates:tavern_keg[facing=east,drink=1]")); set(56, y0, 20, id("pixelpirates:tavern_keg[facing=east,drink=2]"));
        set(50, y0, 23, id("pixelpirates:cargo_crate[facing=north]")); set(50, y0 + 1, 23, id("pixelpirates:cargo_crate[facing=east]"));
        set(58, y0, 20, id("pixelpirates:rum_rack[facing=west,bottles=6]"));
        set(54, y1 - 1, 21, LANTERN_HANGING);
    }

    /** The beer garden east of the tavern: picnic tables under striped umbrellas, kegs, a grill, lantern strings. */
    private static void tavBeerGarden() {
        for (int x = 77; x <= 83; x++) for (int z = 17; z <= 43; z++) set(x, TG, z, (x + z) % 3 == 0 ? GRAVEL : id("minecraft:coarse_dirt"));
        for (int z = 17; z <= 43; z++) set(83, TG + 1, z, z == 30 || z == 31 ? AIR : z % 4 == 0 ? SPRUCE_FENCE : railZ());
        for (int x = 77; x <= 83; x++) { set(x, TG + 1, 17, x % 4 == 0 ? SPRUCE_FENCE : railX()); set(x, TG + 1, 43, x % 4 == 0 ? SPRUCE_FENCE : railX()); }
        for (int x = 77; x <= 82; x++) for (int z = 29; z <= 32; z++) set(x, TG, z, POLISHED_ANDESITE);
        String[][] cols = {{"red", "white"}, {"lime", "white"}, {"blue", "yellow"}, {"orange", "white"}};
        int[][] tables = {{80, 21}, {80, 26}, {80, 35}, {80, 40}};
        for (int t = 0; t < 4; t++) {
            int tx = tables[t][0], tz = tables[t][1];
            for (int dz = -1; dz <= 1; dz++) { set(tx, TG + 1, tz + dz, slabTop("spruce")); set(tx - 1, TG + 1, tz + dz, stairs("spruce", "west")); set(tx + 1, TG + 1, tz + dz, stairs("spruce", "east")); }
            fill(tx, TG + 2, tz, tx, TG + 4, tz, SPRUCE_FENCE);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                set(tx + dx, TG + 5, tz + dz, wool(((dx + dz) & 1) == 0 ? cols[t][0] : cols[t][1]));
            set(tx, TG + 2, tz - 1, id("pixelpirates:tankard[facing=north,count=" + (1 + t % 3) + "]"));
            set(tx, TG + 2, tz + 1, t % 2 == 0 ? id("pixelpirates:spirit_bottles[facing=south,count=2]") : CANDLE_ID());
        }
        set(77, TG + 1, 18, id("pixelpirates:tavern_keg[facing=east,drink=0]")); set(77, TG + 1, 19, id("pixelpirates:tavern_keg[facing=east,drink=1]"));
        set(77, TG + 1, 42, id("minecraft:smoker[facing=east]")); set(78, TG + 1, 42, CAMPFIRE); set(79, TG + 1, 42, barrel("up"));
        for (int z : new int[]{24, 37}) {
            for (int x = 77; x <= 83; x++) set(x, TG + 6, z, id("minecraft:chain[axis=x]"));
            for (int x = 78; x <= 82; x += 2) set(x, TG + 5, z, LANTERN_HANGING);
            fill(83, TG + 2, z, 83, TG + 6, z, SPRUCE_FENCE);
        }
        lamppost(82, TG, 27); lamppost(82, TG, 34);
    }
    private static int CANDLE_ID() { return id("minecraft:candle[candles=3,lit=true]"); }

    /** A wagon-wheel chandelier hanging from a tie beam at (x, y, z): chain, a ring of fences and lanterns. */
    private static void chandelierRing(int x, int y, int z) {
        for (int k = 1; k <= 3; k++) set(x, y - k, z, CHAIN);
        int ry = y - 4;
        set(x, ry, z, DARK_OAK_FENCE);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            set(x + d[0], ry, z + d[1], id("minecraft:dark_oak_fence[" + (d[0] != 0 ? (d[0] > 0 ? "west" : "east") : (d[1] > 0 ? "north" : "south")) + "=true]"));
            set(x + 2 * d[0], ry, z + 2 * d[1], LANTERN);
        }
        set(x, ry - 1, z, LANTERN_HANGING);
    }


    /** "The Salty Siren" inn, west of the market square. */
    // ================================================================ THE MERMAID'S REST (#8, rebuilt 2026-10-01)
    /*
     * A coaching inn round a courtyard light well, x -76..-48, z 14..42. Ground floor y67 (stone), floors y72 + y77
     * (timber frame with pastel plaster - lime N, pink S, light blue W, yellow E - and coloured shutters + flower boxes),
     * ceiling y82, a hipped ring roof of mangrove round the well, two brick chimneys on the west face, a teal-roofed
     * belvedere over the east wing, a striped veranda on the market (east) side.
     * Ground: LOBBY (east, main doors x-48 z27..29, stair up along z25), TAPROOM (NE), GAMES SNUG (SE), COMMON ROOM
     * (south, hearth, stage, door to the avenue at x-70/-69), KITCHEN (west, range in the chimney, hatch to the common
     * room), BATHHOUSE (NW, sunken tub), LAUNDRY (north, back door to the street), the COURTYARD (fountain, beds, vines,
     * lantern strings). Floors 2 + 3: a corridor ring round the well, 7 rooms each (A/B/C north, D/E/F south, G west,
     * every room its own colours + door wood) and a lounge in the east wing with the stairs; floor 3's lounge climbs to
     * the belvedere. Furnished in furnishInn() through ip() (only into air, never into a door approach).
     */
    private static final int IG = 67, IF2 = 72, IF3 = 77, ITOP = 81, IROOF = 82;
    private static final String[] INN_PLASTER = {"lime", "pink", "light_blue", "yellow"};    // N S W E
    private static final String[] INN_SHUTTER = {"crimson", "birch", "cherry", "warped"};
    private static final java.util.Set<Long> INN_RES = new java.util.HashSet<>();

    private static long ik(int x, int y, int z) { return ((long) (x + 4096) << 32) | ((long) (y + 512) << 16) | (z + 4096); }
    private static void innReserve(int x, int y, int z) { INN_RES.add(ik(x, y, z)); }
    /** Inn furnishing: write only into air that is not a reserved approach cell. */
    private static boolean ip(int x, int y, int z, int b) {
        if (getRaw(x, y, z) != AIR || INN_RES.contains(ik(x, y, z))) return false;
        set(x, y, z, b);
        return true;
    }
    private static String opp(String d) {
        return switch (d) { case "north" -> "south"; case "south" -> "north"; case "east" -> "west"; default -> "east"; };
    }
    /** A door in a wall along x (alongX) or along z, on floor fy; both approach cells reserved. */
    private static void innDoor(int x, int fy, int z, String wood, boolean alongX) {
        String f = alongX ? "south" : "east";
        set(x, fy + 1, z, door(wood, f, false));
        set(x, fy + 2, z, door(wood, f, true));
        set(x, fy + 3, z, alongX ? DARK_OAK_LOG_X : DARK_OAK_LOG_Z);
        for (int s : new int[]{-1, 1}) for (int dy = 1; dy <= 2; dy++) innReserve(alongX ? x : x + s, fy + dy, alongX ? z + s : z);
    }
    private static boolean innCourt(int x, int z) { return x >= -65 && x <= -59 && z >= 24 && z <= 32; }

    private static void inn() {
        final int x1 = -76, x2 = -48, z1 = 14, z2 = 42;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(x1 - 1, IG + 1, z1 - 1, x2 + 4, 104, z2 + 1, AIR);
        fill(x1, IG - 2, z1, x2, IG - 1, z2, COBBLE);
        // floors, storey floors, ceiling (not over the light well)
        for (int x = x1 + 1; x < x2; x++)
            for (int z = z1 + 1; z < z2; z++) {
                if (innCourt(x, z)) { set(x, IG, z, ((x + z) & 1) == 0 ? STONE_BRICKS : MOSSY_STONE_BRICKS); continue; }
                set(x, IG, z, SPRUCE);
                set(x, IF2, z, SPRUCE); set(x, IF3, z, SPRUCE);
            }
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) if (!innCourt(x, z)) set(x, IROOF, z, SPRUCE);
        // kitchen tiles, bathhouse tiles, lobby floor
        for (int x = -75; x <= -67; x++) for (int z = 24; z <= 32; z++) set(x, IG, z, id(((x + z) & 1) == 0 ? "minecraft:white_terracotta" : "minecraft:cyan_terracotta"));
        for (int x = -75; x <= -68; x++) for (int z = 15; z <= 22; z++) set(x, IG, z, id("minecraft:prismarine_bricks"));
        for (int x = -57; x <= -49; x++) for (int z = 24; z <= 32; z++) set(x, IG, z, (x == -57 || x == -49 || z == 24 || z == 32) ? DARK_OAK : id("minecraft:stripped_dark_oak_wood"));
        // outer walls
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) continue;
                int side = z == z1 ? 0 : z == z2 ? 1 : x == x1 ? 2 : 3;
                innWallColumn(x, z, ez ? x - x1 : z - z1, ex && ez, ez, id("minecraft:" + INN_PLASTER[side] + "_terracotta"), true);
            }
        // the light well's walls (white plaster, all storeys)
        int calcite = id("minecraft:calcite");
        for (int x = -66; x <= -58; x++)
            for (int z = 23; z <= 33; z++) {
                boolean ex = x == -66 || x == -58, ez = z == 23 || z == 33;
                if (!ex && !ez) continue;
                innWallColumn(x, z, ez ? x + 66 : z - 23, ex && ez, ez, calcite, false);
            }
        // ground-floor partitions
        int pw = BIRCH;
        fill(-67, IG + 1, 15, -67, IF2 - 1, 22, pw); fill(-57, IG + 1, 15, -57, IF2 - 1, 22, pw);
        fill(-75, IG + 1, 23, -67, IF2 - 1, 23, pw); fill(-57, IG + 1, 23, -49, IF2 - 1, 23, pw);
        fill(-75, IG + 1, 33, -67, IF2 - 1, 33, pw); fill(-57, IG + 1, 33, -49, IF2 - 1, 33, pw);
        fill(-57, IG + 1, 34, -57, IF2 - 1, 41, pw);
        // upper partitions: corridor ring round the well, rooms beyond
        for (int fy : new int[]{IF2, IF3}) {
            int ya = fy + 1, yb = fy + 4;
            fill(-75, ya, 21, -49, yb, 21, pw); fill(-75, ya, 35, -49, yb, 35, pw);
            fill(-68, ya, 22, -68, yb, 34, pw); fill(-56, ya, 22, -56, yb, 34, pw);
            fill(-66, ya, 15, -66, yb, 20, pw); fill(-59, ya, 15, -59, yb, 20, pw);
            fill(-66, ya, 36, -66, yb, 41, pw); fill(-59, ya, 36, -59, yb, 41, pw);
        }
        // chimneys on the west face (kitchen z27..29, common room z36..38), up through every storey
        int brick = id("minecraft:bricks");
        for (int zc : new int[]{28, 37}) {
            fill(-77, IG - 2, zc - 1, -76, 92, zc + 1, brick);
            set(-77, 93, zc, CAMPFIRE); set(-76, 93, zc, CAMPFIRE);
            fill(-77, 93, zc - 1, -76, 93, zc - 1, id("minecraft:brick_wall")); fill(-77, 93, zc + 1, -76, 93, zc + 1, id("minecraft:brick_wall"));
            for (int fy : new int[]{IG, IF2, IF3}) {
                fill(-75, fy + 1, zc - 1, -75, fy + 3, zc - 1, brick); fill(-75, fy + 1, zc + 1, -75, fy + 3, zc + 1, brick);
                set(-75, fy + 3, zc, brick);
                set(-75, fy + 1, zc, CAMPFIRE);
                set(-74, fy + 4, zc - 1, id("minecraft:brick_slab[type=top]")); set(-74, fy + 4, zc, id("minecraft:brick_slab[type=top]")); set(-74, fy + 4, zc + 1, id("minecraft:brick_slab[type=top]"));
            }
        }
        // doors: street doors, ground floor, both upper floors
        for (int z = 27; z <= 29; z++) { set(x2, IG + 1, z, door("cherry", "east", false)); set(x2, IG + 2, z, door("cherry", "east", true)); innReserve(x2 - 1, IG + 1, z); innReserve(x2 - 1, IG + 2, z); }
        set(x2, IG + 3, 28, id("minecraft:light_blue_stained_glass"));
        innDoor(-62, IG, z1, "spruce", true);
        innDoor(-70, IG, z2, "oak", true); innDoor(-69, IG, z2, "oak", true);
        innDoor(-58, IG, 28, "cherry", false); innDoor(-66, IG, 28, "spruce", false);
        innDoor(-62, IG, 23, "birch", true); innDoor(-62, IG, 33, "oak", true);
        innDoor(-53, IG, 23, "dark_oak", true); innDoor(-53, IG, 33, "jungle", true);
        innDoor(-57, IG, 38, "jungle", false); innDoor(-57, IG, 19, "dark_oak", false);
        innDoor(-67, IG, 19, "warped", false); innDoor(-68, IG, 33, "spruce", true);
        set(-71, IG + 1, 33, slabTop("spruce")); set(-70, IG + 1, 33, slabTop("spruce"));      // kitchen hatch
        set(-71, IG + 2, 33, AIR); set(-70, IG + 2, 33, AIR);
        String[][] doorWood = {{"cherry", "birch", "warped", "birch", "crimson", "mangrove", "spruce"},
                               {"bamboo", "oak", "jungle", "spruce", "dark_oak", "cherry", "acacia"}};
        for (int f = 0; f < 2; f++) {
            int fy = f == 0 ? IF2 : IF3;
            String[] w = doorWood[f];
            innDoor(-67, fy, 21, w[0], true); innDoor(-62, fy, 21, w[1], true); innDoor(-57, fy, 21, w[2], true);
            innDoor(-67, fy, 35, w[3], true); innDoor(-62, fy, 35, w[4], true); innDoor(-57, fy, 35, w[5], true);
            innDoor(-68, fy, 28, w[6], false);
            for (int z = 27; z <= 29; z++) { fill(-56, fy + 1, z, -56, fy + 3, z, AIR); innReserve(-57, fy + 1, z); innReserve(-55, fy + 1, z); }
            set(-56, fy + 4, 27, DARK_OAK_LOG_Z); set(-56, fy + 4, 28, DARK_OAK_LOG_Z); set(-56, fy + 4, 29, DARK_OAK_LOG_Z);
        }
        // stairs: ground -> 2 (lobby, z25, rising west), 2 -> 3 (lounge, z31), 3 -> belvedere (z27, rising east)
        for (int k = 0; k < 5; k++) {
            set(-50 - k, IG + 1 + k, 25, stairs("dark_oak", "west"));
            set(-50 - k, IF2 + 1 + k, 31, stairs("dark_oak", "west"));
            set(-55 + k, IF3 + 1 + k, 27, stairs("dark_oak", "east"));
        }
        for (int x = -53; x <= -50; x++) { set(x, IF2, 25, AIR); set(x, IF3, 31, AIR); set(x, IF2 + 1, 26, railX()); set(x, IF3 + 1, 30, railX()); }
        set(-49, IF2 + 1, 25, railZ()); set(-49, IF3 + 1, 31, railZ());
        for (int x = -55; x <= -52; x++) set(x, IROOF, 27, AIR);
        innReserve(-49, IG + 1, 25); innReserve(-49, IG + 2, 25); innReserve(-55, IF2 + 1, 25); innReserve(-55, IF2 + 2, 25);
        innReserve(-49, IF2 + 1, 31); innReserve(-49, IF2 + 2, 31); innReserve(-55, IF3 + 1, 31); innReserve(-55, IF3 + 2, 31);
        // windows (outer + light well), with shutters and flower boxes outside
        innWindows(calcite);
        innRoof();
        for (int zc : new int[]{28, 37}) {                       // the roof ran over the chimney stacks: re-lay them
            fill(-77, IROOF, zc - 1, -76, 92, zc + 1, brick);
            set(-77, 93, zc, CAMPFIRE); set(-76, 93, zc, CAMPFIRE);
        }
        innBelvedere();
        innVeranda();
        innCourtyard();
        // street doors: hoods + lamps
        for (int x = -71; x <= -68; x++) set(x, IG + 4, z2 + 1, id("minecraft:mangrove_slab[type=top]"));
        for (int x = -63; x <= -61; x++) set(x, IG + 4, z1 - 1, id("minecraft:mangrove_slab[type=top]"));
        lamppost(-73, IG, z2 + 2); lamppost(-66, IG, z2 + 2);
        // INN_SIGNs over the veranda roof (market side, clear of the window shutters) and over the avenue doors
        set(x2 + 1, IF2 + 1, 26, id("pixelpirates:inn_sign[facing=east]"));
        set(-70, IF2 + 1, z2 + 1, id("pixelpirates:inn_sign[facing=south]"));
    }

    /** One outer/light-well wall column: stone ground storey, log bands at the floors, framed plaster above. */
    private static void innWallColumn(int x, int z, int u, boolean corner, boolean alongX, int infill, boolean outer) {
        int band = alongX ? DARK_OAK_LOG_X : DARK_OAK_LOG_Z;
        for (int y = IG + 1; y <= ITOP; y++) {
            int b;
            if (y < IF2) {
                int h = Math.floorMod(x * 31 + y * 17 + z * 7, 9);
                b = corner ? (y % 2 == 0 ? CHISELED_STONE_BRICKS : STONE_BRICKS) : y == IG + 1 ? COBBLE
                        : outer ? (h == 0 ? MOSSY_STONE_BRICKS : h == 1 ? CRACKED_STONE_BRICKS : STONE_BRICKS)
                        : (u % 2 == 0 ? DARK_OAK_LOG_Y : infill);
            } else if (y == IF2 || y == IF3 || y == ITOP) b = corner ? DARK_OAK_LOG_Y : band;
            else b = corner || u % (outer ? 6 : 2) == 0 ? DARK_OAK_LOG_Y : infill;
            set(x, y, z, b);
        }
    }

    private static void innWindows(int calcite) {
        // outer walls: windows at u%6==3 on every storey where the room behind is open; shutters + flower boxes outside
        int[][] sides = {{-76, 14, 1, 0, 0, -1}, {-76, 42, 1, 0, 0, 1}, {-76, 14, 0, 1, -1, 0}, {-48, 14, 0, 1, 1, 0}};   // x0,z0,dx,dz,outX,outZ
        String[] outName = {"north", "south", "west", "east"};
        for (int s = 0; s < 4; s++) {
            int[] sd = sides[s];
            boolean alongX = sd[2] == 1;
            int pane = id("minecraft:glass_pane[" + (alongX ? "east=true,west=true" : "north=true,south=true") + "]");
            int shutter = id("minecraft:" + INN_SHUTTER[s] + "_trapdoor[facing=" + outName[s] + ",half=bottom,open=true]");
            for (int u = 3; u <= 21; u += 6) {
                int x = sd[0] + sd[2] * u, z = sd[1] + sd[3] * u;
                for (int fy : new int[]{IG, IF2, IF3}) {
                    int y = fy + 2;
                    int wall = getRaw(x, y, z), inside = getRaw(x - sd[4], y, z - sd[5]);
                    if (inside != AIR || wall == getRaw(-77, 70, 28) || wall == AIR) continue;     // partition / chimney / opening
                    if (isDoorCell(x, fy + 1, z)) continue;
                    set(x, y, z, pane); set(x, y + 1, z, pane);
                    int ox = x + sd[4], oz = z + sd[5];
                    for (int k : new int[]{-1, 1}) {
                        int sx = ox + (alongX ? k : 0), sz = oz + (alongX ? 0 : k);
                        set(sx, y, sz, shutter); set(sx, y + 1, sz, shutter);
                    }
                    if (fy != IG) set(ox, y - 1, oz, id((u / 6) % 2 == 0 ? "minecraft:flowering_azalea_leaves[persistent=true]" : "minecraft:azalea_leaves[persistent=true]"));
                }
            }
        }
        // light well: a window in every framed bay upstairs (corridor), every third cell on the ground floor
        for (int x = -66; x <= -58; x++)
            for (int z = 23; z <= 33; z++) {
                boolean ex = x == -66 || x == -58, ez = z == 23 || z == 33;
                if (!ex && !ez || ex && ez) continue;
                int u = ez ? x + 66 : z - 23;
                String conn = ez ? "east=true,west=true" : "north=true,south=true";
                for (int fy : new int[]{IF2, IF3}) {
                    if (u % 2 == 0) continue;
                    int pane = id("minecraft:" + (((u / 2) % 2 == 0) ? "light_blue_stained_glass_pane" : "glass_pane") + "[" + conn + "]");
                    set(x, fy + 2, z, pane); set(x, fy + 3, z, pane);
                }
                if (u % 3 == 1 && !isDoorCell(x, IG + 1, z)) { int pane = id("minecraft:glass_pane[" + conn + "]"); set(x, IG + 2, z, pane); set(x, IG + 3, z, pane); }
            }
    }
    private static boolean isDoorCell(int x, int y, int z) {
        int b = getRaw(x, y, z);
        return b > 0 && PALETTE.get(b - 1).contains("_door");
    }

    /** Hipped ring roof round the light well: height = min(distance to the eaves, distance to the well) - mangrove. */
    private static void innRoof() {
        final int ox1 = -77, ox2 = -47, oz1 = 13, oz2 = 43, wx1 = -64, wx2 = -60, wz1 = 25, wz2 = 31;
        int nx = ox2 - ox1 + 1, nz = oz2 - oz1 + 1;
        int[][] h = new int[nx][nz];
        for (int x = ox1; x <= ox2; x++)
            for (int z = oz1; z <= oz2; z++) {
                if (x >= wx1 && x <= wx2 && z >= wz1 && z <= wz2) { h[x - ox1][z - oz1] = -1; continue; }
                int dOut = Math.min(Math.min(x - ox1, ox2 - x), Math.min(z - oz1, oz2 - z));
                int dx = x < wx1 ? wx1 - x : x > wx2 ? x - wx2 : 0, dz = z < wz1 ? wz1 - z : z > wz2 ? z - wz2 : 0;
                h[x - ox1][z - oz1] = Math.min(dOut, Math.max(dx, dz) - 1);
            }
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        String[] dn = {"east", "west", "south", "north"};
        for (int x = ox1; x <= ox2; x++)
            for (int z = oz1; z <= oz2; z++) {
                int hh = h[x - ox1][z - oz1];
                if (hh < 0) continue;
                String up = null;
                for (int d = 0; d < 4 && up == null; d++) {
                    int ax = x + dirs[d][0] - ox1, az = z + dirs[d][1] - oz1;
                    if (ax >= 0 && az >= 0 && ax < nx && az < nz && h[ax][az] == hh + 1) up = dn[d];
                }
                set(x, IROOF + hh, z, up != null ? stairs("mangrove", up) : id("minecraft:mangrove_planks"));
                if (up == null) set(x, IROOF + hh + 1, z, id("minecraft:mangrove_slab"));
            }
    }

    /** The belvedere over the east wing: open lookout with rails, telescope, teal pyramid roof + flag. */
    private static void innBelvedere() {
        final int bx1 = -53, bx2 = -49, bz1 = 26, bz2 = 30, fy = IROOF;
        fill(bx1, fy, bz1, bx2, 100, bz2, AIR);
        for (int x = bx1; x <= bx2; x++) for (int z = bz1; z <= bz2; z++) set(x, fy, z, SPRUCE);
        for (int x = -55; x <= -52; x++) set(x, fy, 27, AIR);
        set(-51, fy, 27, stairs("dark_oak", "east"));
        int yel = id("minecraft:yellow_terracotta");
        for (int x = bx1; x <= bx2; x++)
            for (int z = bz1; z <= bz2; z++) {
                boolean ex = x == bx1 || x == bx2, ez = z == bz1 || z == bz2;
                if (!ex && !ez) continue;
                boolean corner = ex && ez;
                for (int y = fy + 1; y <= fy + 5; y++) {
                    int b = corner ? DARK_OAK_LOG_Y : y == fy + 1 ? yel : y == fy + 2 ? (ez ? railX() : railZ()) : y == fy + 5 ? (ez ? DARK_OAK_LOG_X : DARK_OAK_LOG_Z) : AIR;
                    set(x, y, z, b);
                }
            }
        set(-53, fy + 1, 27, AIR); set(-53, fy + 2, 27, AIR);                     // the stair comes in through here
        // warped pyramid
        for (int k = 0; k <= 3; k++) {
            int ax1 = bx1 - 1 + k, ax2 = bx2 + 1 - k, az1 = bz1 - 1 + k, az2 = bz2 + 1 - k, y = fy + 6 + k;
            for (int x = ax1; x <= ax2; x++)
                for (int z = az1; z <= az2; z++) {
                    if (k == 3) { set(x, y, z, id("minecraft:warped_planks")); continue; }
                    if (x == ax1) set(x, y, z, stairs("warped", "east"));
                    else if (x == ax2) set(x, y, z, stairs("warped", "west"));
                    else if (z == az1) set(x, y, z, stairs("warped", "south"));
                    else if (z == az2) set(x, y, z, stairs("warped", "north"));
                }
        }
        fill(-51, fy + 10, 28, -51, fy + 14, 28, DARK_OAK_FENCE);
        set(-51, fy + 13, 29, id("minecraft:light_blue_wall_banner[facing=south]"));
        set(-51, fy + 15, 28, LANTERN);
        set(-51, fy + 5, 28, LANTERN_HANGING);
        // a telescope on a tripod, a bench, a sea chart
        set(-50, fy + 1, 29, id("pixelpirates:telescope[facing=east]"));
        set(-52, fy + 1, 29, stairs("dark_oak", "north"));
    }

    /** The striped veranda on the market side. */
    private static void innVeranda() {
        int cyan = wool("cyan"), white = WHITE_WOOL;
        for (int x = -47; x <= -44; x++)
            for (int z = 17; z <= 39; z++) {
                set(x, IG, z, x == -44 ? id("minecraft:stripped_spruce_log[axis=z]") : SPRUCE);
                set(x, IF2, z, (z & 1) == 0 ? cyan : white);
            }
        for (int z = 17; z <= 39; z++) set(-43, IF2, z, id("minecraft:mangrove_slab[type=top]"));
        for (int z = 18; z <= 38; z += 4) fill(-44, IG + 1, z, -44, IF2 - 1, z, DARK_OAK_LOG_Y);
        for (int z = 18; z <= 38; z++) {
            if (z % 4 == 2 || (z >= 26 && z <= 30)) continue;
            set(-44, IG + 1, z, railZ());
        }
        for (int z = 20; z <= 36; z += 4) set(-45, IF2 - 1, z, LANTERN_HANGING);
        set(-47, IG + 4, 26, id("minecraft:light_blue_wall_banner[facing=east]"));
        set(-47, IG + 4, 30, id("minecraft:pink_wall_banner[facing=east]"));
        // pots of flowers on barrels at the posts, benches against the wall, tables
        for (int z : new int[]{18, 38}) { set(-45, IG + 1, z, BARREL_UP); set(-45, IG + 2, z, id("minecraft:flowering_azalea")); }
        for (int z : new int[]{19, 20, 23, 24, 32, 33, 36, 37}) set(-47, IG + 1, z, stairs("spruce", "west"));
        for (int z : new int[]{21, 35}) { set(-46, IG + 1, z, slabTop("spruce")); set(-46, IG + 2, z, z == 21 ? id("minecraft:potted_red_tulip") : LANTERN); }
        lamppost(-42, IG, 25); lamppost(-42, IG, 31);
    }

    /** The courtyard: paving (laid with the floors), a lantern fountain, flower beds, benches, vines, lantern strings. */
    private static void innCourtyard() {
        for (int x = -63; x <= -61; x++) for (int z = 27; z <= 29; z++) set(x, IG, z, WATER);
        set(-62, IG, 28, CHISELED_STONE_BRICKS);
        set(-62, IG + 1, 28, id("minecraft:prismarine_wall")); set(-62, IG + 2, 28, SEA_LANTERN);
        for (int x = -64; x <= -60; x++) for (int z = 26; z <= 30; z++)
            if (x == -64 || x == -60 || z == 26 || z == 30) set(x, IG + 1, z, id("minecraft:mossy_stone_brick_slab"));
        int[][] beds = {{-65, 24}, {-59, 24}, {-65, 32}, {-59, 32}};
        for (int[] b : beds) {
            set(b[0], IG, b[1], GRASS);
            set(b[0], IG + 1, b[1], id(b[1] == 24 ? "minecraft:flowering_azalea" : "minecraft:azalea"));
        }
        for (int[] f : new int[][]{{-64, 24}, {-60, 24}, {-64, 32}, {-60, 32}}) { set(f[0], IG, f[1], GRASS); set(f[0], IG + 1, f[1], id(f[0] == -64 ? "minecraft:cornflower" : "minecraft:poppy")); }
        for (int z : new int[]{26, 27, 29, 30}) { set(-65, IG + 1, z, stairs("spruce", "west")); }
        // vines up the well walls, strings of lanterns across it
        for (int y = IG + 4; y <= ITOP; y++) {
            if (y % 3 != 0) { set(-65, y, 25, id("minecraft:vine[west=true]")); set(-59, y, 31, id("minecraft:vine[east=true]")); }
            if (y % 4 != 0) { set(-63, y, 24, id("minecraft:vine[north=true]")); set(-61, y, 32, id("minecraft:vine[south=true]")); }
        }
        for (int z : new int[]{26, 30}) {
            for (int x = -65; x <= -59; x++) set(x, 79, z, id("minecraft:chain[axis=x]"));
            for (int x = -64; x <= -60; x += 2) set(x, 78, z, LANTERN_HANGING);
        }
    }


    // ================================================================ THE CHANDLERY (#9, rebuilt 2026-10-01)
    /*
     * The ship chandler's, x -112..-86, z 14..36: dark clapboard with white trim (birch bands, calcite window frames) under
     * a verdigris copper roof. The east front has two BAY WINDOWS full of lanterns, a door with sidelights under a copper
     * hood, the CHANDLERY_SIGN, and the loft door with a HOIST BEAM swinging a crate. Ground floor = THE SHOP: a ceiling of
     * hanging lanterns (some soul-blue), the rope wall, racks of stock (barrels, crates, canvas bolts), the counter with the
     * keeper's shelves, the chart corner, ship's wheels + a display cannon, a row of FIGUREHEADS for sale, anchors and chain.
     * Upstairs = THE SAIL LOFT: a sail spread out on the floor, sails drying from the beams, canvas bolts, spools, the
     * sailmaker's benches. West of it, THE RIGGING YARD (spars, capstan, anchor, tar kettle, chain, a lean-to).
     */
    private static final int CG = 67, CF2 = 72, CTOP = 77, CROOF = 78;

    private static void chandlery() {
        final int x1 = -112, x2 = -86, z1 = 14, z2 = 36;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(-125, CG + 1, z1 - 1, -79, 100, z2 + 1, AIR);
        fill(x1, CG - 2, z1, x2, CG - 1, z2, COBBLE);
        for (int x = x1 + 1; x < x2; x++)
            for (int z = z1 + 1; z < z2; z++) {
                set(x, CG, z, (x + 2 * z) % 9 == 0 ? DARK_OAK : SPRUCE);
                set(x, CF2, z, SPRUCE);
            }
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (ex || ez) chWallColumn(x, z, ex && ez, ez);
            }
        // stair (along z16, rising east) + hole + rail
        for (int k = 0; k < 5; k++) set(-108 + k, CG + 1 + k, 16, stairs("spruce", "east"));
        for (int x = -108; x <= -105; x++) { set(x, CF2, 16, AIR); set(x, CF2 + 1, 17, railX()); }
        innReserve(-109, CG + 1, 16); innReserve(-109, CG + 2, 16); innReserve(-103, CF2 + 1, 16); innReserve(-103, CF2 + 2, 16);
        // doors: the shop door with sidelights + copper hood, the yard doors (west), the loft door (east, gated)
        placeDoor(x2, CG, 25, "spruce", "east");
        innReserve(x2 - 1, CG + 1, 25); innReserve(x2 - 1, CG + 2, 25); innReserve(x2 + 1, CG + 1, 25);
        set(x2, CG + 3, 25, id("minecraft:stripped_birch_log[axis=z]"));
        for (int z : new int[]{24, 26}) { set(x2, CG + 1, z, id("minecraft:glass_pane[north=true,south=true]")); set(x2, CG + 2, z, id("minecraft:glass_pane[north=true,south=true]")); }
        for (int z = 23; z <= 27; z++) set(x2 + 1, CG + 4, z, id("minecraft:waxed_oxidized_cut_copper_slab"));
        for (int z = 24; z <= 25; z++) {
            set(x1, CG + 1, z, door("spruce", "west", false)); set(x1, CG + 2, z, door("spruce", "west", true));
            innReserve(x1 + 1, CG + 1, z); innReserve(x1 + 1, CG + 2, z);
        }
        fill(x2, CF2 + 1, 25, x2, CF2 + 3, 25, AIR);
        set(x2, CF2 + 1, 25, id("minecraft:spruce_fence_gate[facing=east,open=false]"));
        set(x2 + 1, CF2 + 1, 24, id("minecraft:spruce_trapdoor[facing=east,half=bottom,open=true]"));
        set(x2 + 1, CF2 + 2, 24, id("minecraft:spruce_trapdoor[facing=east,half=bottom,open=true]"));
        set(x2 + 1, CF2 + 1, 26, id("minecraft:spruce_trapdoor[facing=east,half=bottom,open=true]"));
        set(x2 + 1, CF2 + 2, 26, id("minecraft:spruce_trapdoor[facing=east,half=bottom,open=true]"));
        // bay windows either side of the door
        for (int bz : new int[]{17, 29}) {
            for (int z = bz; z <= bz + 4; z++) {
                boolean end = z == bz || z == bz + 4;
                set(x2 + 1, CG, z, POLISHED_ANDESITE);
                set(x2 + 1, CG + 1, z, end ? id("minecraft:calcite") : DARK_OAK);
                for (int y = CG + 2; y <= CG + 3; y++) set(x2 + 1, y, z, end ? id("minecraft:calcite") : id("minecraft:glass_pane[north=true,south=true]"));
                set(x2 + 1, CG + 4, z, id("minecraft:waxed_oxidized_cut_copper_slab"));
                if (!end) { set(x2, CG + 2, z, AIR); set(x2, CG + 3, z, AIR); }
            }
        }
        chWindows();
        chRoof();
        // the hoist beam out of the east gable, a crate on the tackle
        for (int x = x2; x <= x2 + 3; x++) set(x, CTOP + 3, 25, DARK_OAK_LOG_X);
        set(x2 + 4, CTOP + 3, 25, DARK_OAK_FENCE);
        for (int y = CF2 + 4; y <= CTOP + 2; y++) set(x2 + 3, y, 25, CHAIN);
        set(x2 + 3, CF2 + 3, 25, id("pixelpirates:cargo_crate[facing=east]"));
        // signs: one over the street front, one over the shop door side
        set(x2 + 1, CF2 + 1, 21, id("pixelpirates:chandlery_sign[facing=east]"));
        set(-99, CF2 + 1, z1 - 1, id("pixelpirates:chandlery_sign[facing=north]"));
        chRiggingYard();
        // the front: barrels of oars and rope, a bench, lamps
        for (int z : new int[]{15, 35}) { set(-84, CG + 1, z, BARREL_UP); set(-84, CG + 2, z, id("pixelpirates:rope_coil[facing=east]")); }
        for (int z = 22; z <= 23; z++) set(-84, CG + 1, z, stairs("spruce", "west"));
        set(-84, CG + 1, 28, BARREL_UP); set(-84, CG + 2, 28, SPRUCE_FENCE); set(-84, CG + 3, 28, SPRUCE_FENCE);
        set(-83, CG + 1, 33, ANCHOR_BLOCK);
        lamppost(-82, CG, 20); lamppost(-82, CG, 30);
        for (int z = 15; z <= 35; z++) set(-85, CG, z, getRaw(-85, CG + 1, z) == AIR || z == 25 ? POLISHED_ANDESITE : getRaw(-85, CG, z));
    }

    private static void chWallColumn(int x, int z, boolean corner, boolean alongX) {
        int trim = id("minecraft:stripped_birch_log[axis=" + (alongX ? "x" : "z") + "]");
        for (int y = CG + 1; y <= CTOP; y++) {
            int b;
            if (corner) b = id("minecraft:stripped_birch_log[axis=y]");
            else if (y == CG + 1) b = COBBLE;
            else if (y == CF2 || y == CTOP) b = trim;
            else b = DARK_OAK;
            set(x, y, z, b);
        }
    }

    private static void chWindows() {
        int[][] sides = {{-112, 14, 1, 0, 0, -1}, {-112, 36, 1, 0, 0, 1}, {-112, 14, 0, 1, -1, 0}, {-86, 14, 0, 1, 1, 0}};
        int frame = id("minecraft:calcite");
        for (int[] sd : sides) {
            boolean alongX = sd[2] == 1;
            int pane = id("minecraft:glass_pane[" + (alongX ? "east=true,west=true" : "north=true,south=true") + "]");
            int len = alongX ? 26 : 22;
            for (int u = 3; u <= len - 3; u += 6) {
                int x = sd[0] + sd[2] * u, z = sd[1] + sd[3] * u;
                for (int fy : new int[]{CG, CF2}) {
                    int y = fy + 2;
                    if (getRaw(x, y, z) != DARK_OAK || getRaw(x - sd[4], y, z - sd[5]) != AIR || isDoorCell(x, fy + 1, z)) continue;
                    set(x, y, z, pane); set(x, y + 1, z, pane);
                    set(x, y + 2, z, frame);
                    if (fy == CF2) set(x, y - 1, z, frame);
                    for (int k : new int[]{-1, 1}) {
                        int fx = x + (alongX ? k : 0), fz = z + (alongX ? 0 : k);
                        if (getRaw(fx, y, fz) == DARK_OAK) { set(fx, y, fz, frame); set(fx, y + 1, fz, frame); }
                    }
                }
            }
        }
    }

    /** Verdigris copper gable roof, ridge along x at z25 (y90), boarded underside; clapboard gables with a round window. */
    private static void chRoof() {
        String cu = "waxed_oxidized_cut_copper";
        for (int x = -113; x <= -85; x++)
            for (int z = 13; z <= 37; z++) {
                int h = Math.min(z - 13, 37 - z), y = CROOF + h;
                set(x, y, z, h == 12 ? id("minecraft:" + cu) : stairs(cu, z < 25 ? "south" : "north"));
                if (h == 12) set(x, y + 1, z, id("minecraft:" + cu + "_slab"));
                if (h >= 1 && x >= -112 && x <= -86) set(x, y - 1, z, SPRUCE);
                if (x == -112 || x == -86)
                    for (int yy = CROOF; yy < y - 1; yy++) {
                        int dz = z - 25, dy = yy - 84;
                        boolean round = dz * dz + dy * dy <= 4;
                        set(x, yy, z, round ? id("minecraft:glass_pane[north=true,south=true]") : yy % 3 == 0 ? id("minecraft:stripped_birch_log[axis=z]") : DARK_OAK);
                    }
            }
        for (int x = -108; x <= -90; x += 6) for (int z = 15; z <= 35; z++) set(x, CTOP + 1, z, DARK_OAK_LOG_Z);
    }

    /** The rigging yard west of the chandlery: spars, a capstan, the big anchor, a tar kettle, chain, a lean-to shed. */
    private static void chRiggingYard() {
        for (int x = -124; x <= -113; x++) for (int z = 15; z <= 35; z++) set(x, CG, z, (x * 3 + z) % 4 == 0 ? GRAVEL : id("minecraft:coarse_dirt"));
        for (int z = 15; z <= 35; z++) set(-124, CG + 1, z, z % 4 == 3 ? SPRUCE_FENCE : railZ());
        for (int x = -124; x <= -113; x++) {
            if (x < -119 || x > -116) set(x, CG + 1, 15, x % 4 == 0 ? SPRUCE_FENCE : railX());
            set(x, CG + 1, 35, x % 4 == 0 ? SPRUCE_FENCE : railX());
        }
        int spar = id("minecraft:stripped_spruce_log[axis=x]");
        for (int x = -122; x <= -115; x++) { set(x, CG + 1, 18, spar); if (x > -122 && x < -116) set(x, CG + 2, 18, spar); }
        for (int x = -121; x <= -116; x++) set(x, CG + 1, 19, spar);
        set(-123, CG + 1, 18, SPRUCE_FENCE); set(-114, CG + 1, 18, SPRUCE_FENCE);
        // capstan
        set(-118, CG + 1, 25, id("minecraft:stripped_spruce_log[axis=y]")); set(-118, CG + 2, 25, id("minecraft:stripped_spruce_log[axis=y]"));
        set(-117, CG + 2, 25, id("minecraft:spruce_fence[west=true]")); set(-119, CG + 2, 25, id("minecraft:spruce_fence[east=true]"));
        set(-118, CG + 2, 24, id("minecraft:spruce_fence[south=true]")); set(-118, CG + 2, 26, id("minecraft:spruce_fence[north=true]"));
        set(-118, CG + 3, 25, id("pixelpirates:rope_coil[facing=north]"));
        // the big anchor on its block, chain run off it
        set(-121, CG + 1, 23, POLISHED_ANDESITE); set(-121, CG + 2, 23, ANCHOR_BLOCK);
        for (int z = 24; z <= 28; z++) set(-121, CG + 1, z, id("minecraft:chain[axis=z]"));
        // tar kettle + barrels
        set(-122, CG + 1, 32, CAMPFIRE); set(-122, CG + 2, 32, id("minecraft:cauldron"));
        set(-123, CG + 1, 33, BARREL_UP); set(-123, CG + 1, 31, BARREL_UP); set(-123, CG + 2, 33, BARREL_UP);
        // lean-to against the west wall: posts, slab roof, folded sails + spools under it
        for (int z : new int[]{27, 31, 34}) fill(-116, CG + 1, z, -116, CG + 4, z, SPRUCE_LOG_Y);
        for (int x = -117; x <= -113; x++) for (int z = 26; z <= 35; z++) set(x, CG + 5, z, slab("spruce"));
        for (int z = 28; z <= 33; z++) { if (z == 31) continue; set(-114, CG + 1, z, z % 2 == 0 ? WHITE_WOOL : id("pixelpirates:white_sail_canvas")); }
        set(-114, CG + 2, 28, WHITE_WOOL); set(-114, CG + 2, 30, id("pixelpirates:striped_sail_canvas"));
        set(-115, CG + 1, 34, id("minecraft:stripped_spruce_log[axis=x]")); set(-115, CG + 2, 34, id("pixelpirates:rope_coil[facing=east]"));
        lamppost(-114, CG, 16);
    }


    // ================================================================ THE BAKERY (#10, rebuilt 2026-10-01)
    /*
     * The Wavebreak Bakery, x 86..112, z 16..38: a thatched cottage-bakery - stone base, oak frame with cream plaster
     * (smooth sandstone), cherry-pink shutters, moss window boxes with flowers. SHOP FRONT on the street (north): a
     * striped awning, the door at x98..99, the BAKERY_SIGN. Ground: THE SHOP (counter z23, bread baskets + cakes behind it,
     * a cafe corner) and THE BAKEHOUSE behind (the BEEHIVE OVEN bulging out of the east wall with its chimney, kneading
     * island, flour sacks, cooling racks, firewood, mixing bowls, the stair z26). Upstairs: the baker's home round a
     * corridor (z26) - kitchen, bedroom, the children's room, the flour loft. Outside: a little WINDMILL (x116..120) with
     * canvas sails facing the street, and the BAKER'S GARDEN south (cafe tables, wheat, beehives).
     */
    private static final int BG = 67, BF2 = 72, BTOP = 77, BROOF = 78;

    private static void bakery() {
        final int x1 = 86, x2 = 112, z1 = 16, z2 = 38;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(x1 - 1, BG + 1, z1 - 2, 121, 102, 47, AIR);
        fill(x1, BG - 2, z1, x2, BG - 1, z2, COBBLE);
        for (int x = x1 + 1; x < x2; x++)
            for (int z = z1 + 1; z < z2; z++) {
                boolean shop = z <= 24;
                set(x, BG, z, shop ? ((x + z) % 2 == 0 ? id("minecraft:stripped_oak_wood") : OAK) : id(((x + z) & 1) == 0 ? "minecraft:bricks" : "minecraft:terracotta"));
                set(x, BF2, z, OAK);
            }
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (ex || ez) bkWallColumn(x, z, ez ? x - x1 : z - z1, ex && ez, ez);
            }
        // ground partition (shop | bakehouse) with a doorway; upstairs corridor walls + room walls
        fill(x1 + 1, BG + 1, 25, x2 - 1, BF2 - 1, 25, BIRCH);
        fill(98, BG + 1, 25, 99, BG + 3, 25, AIR);
        for (int z : new int[]{25, 27}) fill(x1 + 1, BF2 + 1, z, x2 - 1, BTOP - 1, z, BIRCH);
        fill(96, BF2 + 1, 17, 96, BTOP - 1, 24, BIRCH);
        fill(99, BF2 + 1, 28, 99, BTOP - 1, 37, BIRCH);
        innDoor(94, BF2, 25, "oak", true); innDoor(104, BF2, 25, "oak", true);
        innDoor(95, BF2, 27, "cherry", true); innDoor(105, BF2, 27, "spruce", true);
        // stair: along z26 rising east, into the corridor
        for (int k = 0; k < 5; k++) set(88 + k, BG + 1 + k, 26, stairs("oak", "east"));
        for (int x = 88; x <= 91; x++) set(x, BF2, 26, AIR);
        set(87, BF2 + 1, 26, railZ());
        innReserve(87, BG + 1, 26); innReserve(87, BG + 2, 26); innReserve(93, BF2 + 1, 26); innReserve(93, BF2 + 2, 26);
        // doors: the shop (street), the garden (south), a side door to the tavern garden (west)
        for (int x = 98; x <= 99; x++) { set(x, BG + 1, z1, door("oak", "north", false)); set(x, BG + 2, z1, door("oak", "north", true)); innReserve(x, BG + 1, z1 + 1); innReserve(x, BG + 2, z1 + 1); innReserve(x, BG + 1, z1 - 1); }
        innDoor(92, BG, z2, "oak", true);
        set(x1, BG + 1, 30, door("cherry", "west", false)); set(x1, BG + 2, 30, door("cherry", "west", true)); innReserve(x1 + 1, BG + 1, 30); innReserve(x1 + 1, BG + 2, 30);
        bkWindows();
        bkRoof();
        bkOven();
        bkWindmill();
        bkGarden();
        // the shop front: a striped awning, the sign, bread racks + a bench on the street
        for (int x = x1; x <= x2; x++) {
            int w = wool(((x - x1) & 1) == 0 ? "yellow" : "white");
            set(x, BF2, z1 - 1, w);
            set(x, BF2 - 1, z1 - 2, id("minecraft:" + (((x - x1) & 1) == 0 ? "yellow" : "white") + "_carpet"));
        }
        for (int x = x1; x <= x2; x++) set(x, BF2 - 2, z1 - 2, AIR);
        for (int x = x1; x <= x2; x++) if (getRaw(x, BF2 - 1, z1 - 2) != AIR) set(x, BF2 - 1, z1 - 2, wool(((x - x1) & 1) == 0 ? "yellow" : "white"));
        set(95, BF2 + 1, z1 - 1, id("pixelpirates:bakery_sign[facing=north]"));
        set(102, BF2 + 1, z1 - 1, id("pixelpirates:bakery_sign[facing=north]"));
        for (int x : new int[]{93, 104}) { set(x, BG + 1, z1 - 1, slabTop("oak")); set(x, BG + 2, z1 - 1, id("minecraft:composter[level=7]")); }
        set(95, BG + 1, z1 - 1, BARREL_UP); set(95, BG + 2, z1 - 1, id("minecraft:candle_cake[lit=true]"));
        for (int x = 89; x <= 90; x++) set(x, BG + 1, z1 - 1, stairs("oak", "south"));
        for (int x = 107; x <= 108; x++) set(x, BG + 1, z1 - 1, stairs("oak", "south"));
    }

    private static void bkWallColumn(int x, int z, int u, boolean corner, boolean alongX) {
        int band = id("minecraft:stripped_oak_log[axis=" + (alongX ? "x" : "z") + "]"), post = id("minecraft:stripped_oak_log[axis=y]");
        int plaster = id("minecraft:smooth_sandstone");
        for (int y = BG + 1; y <= BTOP; y++) {
            int b;
            if (y == BG + 1) b = corner ? STONE_BRICKS : (u % 3 == 0 ? MOSSY_COBBLE : COBBLE);
            else if (y == BF2 || y == BTOP) b = corner ? post : band;
            else b = corner || u % 4 == 0 ? post : plaster;
            set(x, y, z, b);
        }
    }

    private static void bkWindows() {
        int[][] sides = {{86, 16, 1, 0, 0, -1}, {86, 38, 1, 0, 0, 1}, {86, 16, 0, 1, -1, 0}, {112, 16, 0, 1, 1, 0}};
        String[] out = {"north", "south", "west", "east"};
        String[] flowers = {"minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy", "minecraft:allium"};
        int plaster = id("minecraft:smooth_sandstone");
        for (int s = 0; s < 4; s++) {
            int[] sd = sides[s];
            boolean alongX = sd[2] == 1;
            int pane = id("minecraft:glass_pane[" + (alongX ? "east=true,west=true" : "north=true,south=true") + "]");
            int shutter = id("minecraft:cherry_trapdoor[facing=" + out[s] + ",half=bottom,open=true]");
            int len = alongX ? 26 : 22;
            for (int u = 2; u <= len - 2; u += 4) {
                int x = sd[0] + sd[2] * u, z = sd[1] + sd[3] * u;
                for (int fy : new int[]{BG, BF2}) {
                    int y = fy + 2;
                    if (getRaw(x, y, z) != plaster || getRaw(x - sd[4], y, z - sd[5]) != AIR || isDoorCell(x, fy + 1, z)) continue;
                    if (s == 0 && fy == BG) continue;                                 // the shop front gets its big windows below
                    set(x, y, z, pane); set(x, y + 1, z, pane);
                    int ox = x + sd[4], oz = z + sd[5];
                    for (int k : new int[]{-1, 1}) {
                        int sx = ox + (alongX ? k : 0), sz = oz + (alongX ? 0 : k);
                        if (getRaw(sx, y, sz) == AIR) { set(sx, y, sz, shutter); set(sx, y + 1, sz, shutter); }
                    }
                    if (getRaw(ox, y - 1, oz) == AIR) {
                        set(ox, y - 1, oz, id("minecraft:moss_block"));
                        if (getRaw(ox, y, oz) == AIR) set(ox, y, oz, id(flowers[Math.floorMod(x + z, flowers.length)]));
                    }
                }
            }
        }
        // the shop front: wide display windows either side of the door
        int pane = id("minecraft:glass_pane[east=true,west=true]");
        for (int x = 88; x <= 109; x++) {
            if (x == 90 || x == 94 || x == 98 || x == 99 || x == 102 || x == 106) continue;
            if (getRaw(x, BG + 2, 16) == plaster) { set(x, BG + 2, 16, pane); set(x, BG + 3, 16, pane); }
        }
    }

    /** Thatched gable, ridge along x at z27 (y91), deep eaves; oak underside; plaster gables with a round window. */
    private static void bkRoof() {
        for (int x = 85; x <= 113; x++)
            for (int z = 14; z <= 40; z++) {
                int h = Math.min(z - 14, 40 - z), y = BROOF + h - 1;
                if (h == 0) { set(x, BROOF - 1, z, id("pixelpirates:thatch_slab[type=top]")); continue; }
                set(x, y, z, h == 13 ? id("pixelpirates:thatch") : id("pixelpirates:thatch_stairs[facing=" + (z < 27 ? "south" : "north") + "]"));
                if (h == 13) set(x, y + 1, z, id("pixelpirates:thatch_slab"));
                if (h >= 2 && x >= 86 && x <= 112) set(x, y - 1, z, OAK);
                if (x == 86 || x == 112)
                    for (int yy = BROOF; yy < y - 1; yy++) {
                        int dz = z - 27, dy = yy - 83;
                        boolean round = dz * dz + dy * dy <= 4;
                        set(x, yy, z, round ? id("minecraft:glass_pane[north=true,south=true]") : (z - 16) % 4 == 0 ? id("minecraft:stripped_oak_log[axis=y]") : id("minecraft:smooth_sandstone"));
                    }
            }
        // chimney for the upstairs hearth (west gable)
        fill(87, BF2 + 1, 33, 87, 93, 33, id("minecraft:bricks"));
        set(87, 94, 33, CAMPFIRE);
    }

    /** The beehive bread oven bulging out of the east wall (fire inside, mouth in the bakehouse) + its chimney. */
    private static void bkOven() {
        final double cx = 113.5, cy = 68.5, cz = 28;
        for (int x = 112; x <= 117; x++)
            for (int y = BG; y <= 73; y++)
                for (int z = 24; z <= 32; z++) {
                    double dx = (x - cx) / 3.6, dy = (y - cy) / 4.2, dz = (z - cz) / 4.0;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (x == 112 && y <= 71) { if (d <= 1) set(x, y, z, id("minecraft:bricks")); continue; }
                    if (d <= 1) set(x, y, z, d > 0.45 || y == BG ? id((x + y + z) % 5 == 0 ? "minecraft:mud_bricks" : "minecraft:bricks") : AIR);
                }
        for (int y = BG + 1; y <= BG + 2; y++) set(112, y, 28, AIR);                 // the mouth
        set(112, BG + 3, 28, id("minecraft:brick_stairs[facing=west,half=top]"));
        set(113, BG + 1, 28, CAMPFIRE); set(114, BG + 1, 28, CAMPFIRE);
        fill(114, 73, 28, 114, 84, 28, id("minecraft:bricks"));
        set(114, 85, 28, CAMPFIRE);
        // the bakehouse side: a hearth apron, firewood, the peel on its hooks
        set(111, BG, 28, id("minecraft:polished_andesite"));
    }

    /** A little windmill east of the bakery: whitewashed tower, thatch cap, four canvas sails turned to the street. */
    private static void bkWindmill() {
        int wash = id("minecraft:calcite");
        for (int x = 116; x <= 120; x++)
            for (int z = 34; z <= 38; z++) {
                boolean corner = (x == 116 || x == 120) && (z == 34 || z == 38);
                boolean edge = x == 116 || x == 120 || z == 34 || z == 38;
                set(x, BG, z, COBBLE);
                if (corner) continue;
                for (int y = BG + 1; y <= 80; y++) set(x, y, z, edge ? (y == BG + 1 ? COBBLE : y % 5 == 0 ? id("minecraft:stripped_oak_log[axis=y]") : wash) : AIR);
            }
        set(118, BG + 1, 34, door("oak", "north", false)); set(118, BG + 2, 34, door("oak", "north", true));
        set(118, 74, 34, id("minecraft:glass_pane[east=true,west=true]")); set(116, 76, 36, id("minecraft:glass_pane[north=true,south=true]"));
        set(118, BG, 33, POLISHED_ANDESITE);
        // thatch cap
        for (int k = 0; k <= 3; k++) {
            int y = 81 + k, a1 = 115 + k, a2 = 121 - k, b1 = 33 + k, b2 = 39 - k;
            for (int x = a1; x <= a2; x++) for (int z = b1; z <= b2; z++) {
                if (k == 3) { set(x, y, z, id("pixelpirates:thatch")); continue; }
                String f = x == a1 ? "east" : x == a2 ? "west" : z == b1 ? "south" : z == b2 ? "north" : null;
                if (f != null) set(x, y, z, id("pixelpirates:thatch_stairs[facing=" + f + "]"));
            }
        }
        set(118, 85, 36, id("minecraft:lightning_rod"));
        // the sails: hub on the north face, four arms in an X, canvas beside each arm
        int hx = 118, hy = 78, hz = 32;
        set(hx, hy, 33, id("minecraft:stripped_spruce_log[axis=z]"));
        set(hx, hy, hz, id("minecraft:stripped_spruce_log[axis=z]"));
        int canvas = id("pixelpirates:white_sail_canvas");
        for (int[] d : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}}) {
            for (int k = 1; k <= 5; k++) {
                int x = hx + d[0] * k, y = hy + d[1] * k;
                set(x, y, hz, SPRUCE_FENCE);
                if (k >= 2) set(x - d[0], y, hz, canvas);                              // canvas on the trailing side
            }
        }
    }

    /** The baker's garden behind the bakery: cafe tables under umbrellas, wheat on farmland, beehives, a fence. */
    private static void bkGarden() {
        for (int x = 86; x <= 112; x++) for (int z = 39; z <= 47; z++) set(x, BG, z, GRASS);
        for (int x = 86; x <= 112; x++) if (x < 98 || x > 99) set(x, BG + 1, 47, x % 4 == 2 ? OAK_FENCE : id("minecraft:oak_fence[east=true,west=true]"));
        for (int z = 39; z <= 47; z++) { set(86, BG + 1, z, z % 4 == 3 ? OAK_FENCE : id("minecraft:oak_fence[north=true,south=true]")); set(112, BG + 1, z, z % 4 == 3 ? OAK_FENCE : id("minecraft:oak_fence[north=true,south=true]")); }
        // the cafe terrace (west half)
        for (int x = 87; x <= 97; x++) for (int z = 39; z <= 46; z++) set(x, BG, z, (x + z) % 3 == 0 ? id("minecraft:bricks") : POLISHED_ANDESITE);
        String[][] cols = {{"pink", "white"}, {"yellow", "white"}, {"light_blue", "white"}};
        int[][] tables = {{89, 42}, {94, 42}, {91, 45}};
        for (int t = 0; t < 3; t++) {
            int tx = tables[t][0], tz = tables[t][1];
            set(tx, BG + 1, tz, OAK_FENCE); set(tx, BG + 2, tz, OAK_FENCE); set(tx, BG + 3, tz, OAK_FENCE);
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) set(tx + dx, BG + 4, tz + dz, wool(((dx + dz) & 1) == 0 ? cols[t][0] : cols[t][1]));
            set(tx - 1, BG + 1, tz, stairs("oak", "west")); set(tx + 1, BG + 1, tz, stairs("oak", "east"));
        }
        // wheat on farmland with a water channel, the hives, a scarecrow
        for (int x = 99; x <= 110; x++) for (int z = 40; z <= 45; z++) {
            boolean water = z == 42;
            set(x, BG, z, water ? WATER : id("minecraft:farmland[moisture=7]"));
            if (!water) set(x, BG + 1, z, id("minecraft:wheat[age=" + (5 + Math.floorMod(x * 3 + z, 3)) + "]"));
        }
        for (int x = 101; x <= 109; x += 4) { set(x, BG, 46, OAK_LOG_Y); set(x, BG + 1, 46, id("minecraft:beehive[facing=north,honey_level=" + (x % 6) + "]")); }
        set(98, BG + 1, 41, OAK_FENCE); set(98, BG + 2, 41, HAY); set(98, BG + 3, 41, PUMPKIN);
        set(98, BG + 2, 40, id("minecraft:oak_fence[south=true]")); set(98, BG + 2, 42, id("minecraft:oak_fence[north=true]"));
        lamppost(97, BG, 46);
    }


    // ------------------------------------------------------------------
    // Harbor-terrace buildings
    // ------------------------------------------------------------------

    // ================================================================ THE HARBOURMASTER (#11, rebuilt 2026-10-01)
    /*
     * The port authority, x -28..-12, z 60..76 (harbour terrace, floor y65): stone ground storey, white plaster above with
     * dark-prismarine bands, warped (teal) shutters, a HIPPED PRISMARINE ROOF; a CLOCK TOWER on the south-east corner
     * (clock faces south + east, a lookout gallery with a bell and a telescope, flag mast); a PILLARED PORTICO on the quay
     * side carrying the balcony; two salute guns; HARBOUR_SIGNs (new block); a SIGNAL MAST with flags in the west yard.
     * Ground: the PUBLIC OFFICE (clerks' counter z65, pigeon-hole records, waiting benches, a model ship, the chart wall),
     * the HARBOURMASTER'S OFFICE (x-16..-13), the STRONGROOM in the tower base (iron door + button). Upstairs: the RECORDS
     * ROOM, the harbourmaster's QUARTERS, the SIGNAL ROOM (flag lockers, signal lamps, the ladder up the tower, balcony).
     */
    private static final int HG = 65, HF2 = 70, HTOP = 75, HROOF = 76;

    private static void harbormaster() {
        final int x1 = -28, x2 = -12, z1 = 60, z2 = 76;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(-36, HG + 1, 58, -5, 110, 79, AIR);
        fill(x1, HG - 2, z1, x2, HG - 1, z2, COBBLE);
        for (int x = x1 + 1; x < x2; x++)
            for (int z = z1 + 1; z < z2; z++) {
                boolean hall = z >= 66 && x <= -17;
                set(x, HG, z, hall ? (((x + z) & 1) == 0 ? POLISHED_ANDESITE : id("minecraft:polished_diorite")) : SPRUCE);
                set(x, HF2, z, DARK_OAK);
                set(x, HTOP, z, SPRUCE);
            }
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (ex || ez) hmWallColumn(x, z, ez ? x - x1 : z - z1, ex && ez, ez);
            }
        // the tower's inner walls (it stands in the south-east corner, x-16..-12 z72..76)
        fill(-16, HG + 1, 72, -16, HTOP - 1, 76, STONE_BRICKS);
        fill(-16, HG + 1, 72, -12, HTOP - 1, 72, STONE_BRICKS);
        fill(-15, HG, 73, -13, HG, 75, id("minecraft:polished_andesite"));
        // partitions: clerks' counter (ground), office wall x-17, upstairs records / quarters / signal room
        fill(-17, HG + 1, 61, -17, HF2 - 1, 71, SPRUCE);
        fill(-21, HF2 + 1, 61, -21, HTOP - 1, 75, SPRUCE);
        fill(-20, HF2 + 1, 67, -17, HTOP - 1, 67, SPRUCE); fill(-16, HF2 + 1, 67, -13, HTOP - 1, 67, SPRUCE);
        innDoor(-17, HG, 68, "dark_oak", false);
        innDoor(-21, HF2, 70, "spruce", false); innDoor(-18, HF2, 67, "dark_oak", true);
        // the tower: strongroom iron door (with a button), the upstairs door, the ladder to the gallery
        set(-16, HG + 1, 74, id("minecraft:iron_door[facing=west,half=lower]")); set(-16, HG + 2, 74, id("minecraft:iron_door[facing=west,half=upper]"));
        set(-17, HG + 2, 73, id("minecraft:stone_button[face=wall,facing=west]"));
        innReserve(-17, HG + 1, 74); innReserve(-17, HG + 2, 74);
        innDoor(-16, HF2, 74, "dark_oak", false);
        for (int y = HF2 + 1; y <= 92; y++) set(-13, y, 73, id("minecraft:ladder[facing=south]"));
        set(-13, HTOP, 73, id("minecraft:ladder[facing=south]"));
        // stairs (west side of the hall, rising north) + hole + rail
        for (int k = 0; k < 5; k++) set(-27, HG + 1 + k, 74 - k, stairs("dark_oak", "north"));
        for (int z = 71; z <= 74; z++) { set(-27, HF2, z, AIR); set(-26, HF2 + 1, z, railZ()); }
        innReserve(-27, HG + 1, 75); innReserve(-27, HG + 2, 75); innReserve(-27, HF2 + 1, 69); innReserve(-27, HF2 + 2, 69);
        // doors: the main doors under the portico (south), the side door (west), the balcony door
        for (int x = -21; x <= -20; x++) {
            set(x, HG + 1, z2, door("dark_oak", "south", false)); set(x, HG + 2, z2, door("dark_oak", "south", true));
            set(x, HG + 3, z2, id("minecraft:glass_pane[east=true,west=true]"));
            innReserve(x, HG + 1, z2 - 1); innReserve(x, HG + 2, z2 - 1); innReserve(x, HG + 1, z2 + 1);
        }
        innDoor(x1, HG, 67, "spruce", false);
        innDoor(-19, HF2, z2, "spruce", true);
        hmWindows();
        hmRoof();
        hmTower();
        hmPortico();
        hmSignalMast();
        set(-11, HF2 + 1, 66, id("pixelpirates:harbour_sign[facing=east]"));
        set(-20, HF2 + 1, z1 - 1, id("pixelpirates:harbour_sign[facing=north]"));
        lamppost(-9, HG, 64); lamppost(-31, HG, 74);
    }

    private static void hmWallColumn(int x, int z, int u, boolean corner, boolean alongX) {
        int band = id("minecraft:dark_prismarine"), plaster = id("minecraft:calcite");
        for (int y = HG + 1; y <= HROOF; y++) {
            int b;
            if (y == HG + 1) b = POLISHED_ANDESITE;
            else if (y < HF2) b = corner ? (y % 2 == 0 ? CHISELED_STONE_BRICKS : POLISHED_ANDESITE) : Math.floorMod(x * 7 + y * 3 + z, 11) == 0 ? CRACKED_STONE_BRICKS : STONE_BRICKS;
            else if (y == HF2 || y == HTOP) b = band;
            else if (y == HROOF) b = plaster;
            else b = corner || u % 4 == 0 ? STONE_BRICKS : plaster;
            set(x, y, z, b);
        }
    }

    private static void hmWindows() {
        int[][] sides = {{-28, 60, 1, 0, 0, -1}, {-28, 76, 1, 0, 0, 1}, {-28, 60, 0, 1, -1, 0}, {-12, 60, 0, 1, 1, 0}};
        String[] out = {"north", "south", "west", "east"};
        int plaster = id("minecraft:calcite");
        for (int s = 0; s < 4; s++) {
            int[] sd = sides[s];
            boolean alongX = sd[2] == 1;
            int pane = id("minecraft:glass_pane[" + (alongX ? "east=true,west=true" : "north=true,south=true") + "]");
            int shutter = id("minecraft:warped_trapdoor[facing=" + out[s] + ",half=bottom,open=true]");
            for (int u = 2; u <= 14; u += 4) {
                int x = sd[0] + sd[2] * u, z = sd[1] + sd[3] * u;
                // ground: tall stone-framed windows
                if (getRaw(x - sd[4], HG + 2, z - sd[5]) == AIR && getRaw(x, HG + 2, z) == STONE_BRICKS && !isDoorCell(x, HG + 1, z)) {
                    set(x, HG + 2, z, pane); set(x, HG + 3, z, pane);
                    set(x, HG + 4, z, id("minecraft:stone_brick_stairs[facing=" + out[s] + ",half=top]"));
                }
                // upstairs: shuttered
                int y = HF2 + 2;
                if (getRaw(x, y, z) != plaster || getRaw(x - sd[4], y, z - sd[5]) != AIR || isDoorCell(x, HF2 + 1, z)) continue;
                set(x, y, z, pane); set(x, y + 1, z, pane);
                for (int k : new int[]{-1, 1}) {
                    int sx = x + sd[4] + (alongX ? k : 0), sz = z + sd[5] + (alongX ? 0 : k);
                    if (getRaw(sx, y, sz) == AIR) { set(sx, y, sz, shutter); set(sx, y + 1, sz, shutter); }
                }
            }
        }
    }

    /** Hipped prismarine roof (height = distance to the eaves), dark prismarine on the hips' crown. */
    private static void hmRoof() {
        final int ox1 = -29, ox2 = -11, oz1 = 59, oz2 = 77;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        String[] dn = {"east", "west", "south", "north"};
        for (int x = ox1; x <= ox2; x++)
            for (int z = oz1; z <= oz2; z++) {
                int h = Math.min(Math.min(x - ox1, ox2 - x), Math.min(z - oz1, oz2 - z));
                String up = null;
                for (int d = 0; d < 4 && up == null; d++) {
                    int ax = x + dirs[d][0], az = z + dirs[d][1];
                    if (ax < ox1 || ax > ox2 || az < oz1 || az > oz2) continue;
                    int ah = Math.min(Math.min(ax - ox1, ox2 - ax), Math.min(az - oz1, oz2 - az));
                    if (ah == h + 1) up = dn[d];
                }
                set(x, HROOF + h, z, up != null ? stairs("prismarine_brick", up) : id("minecraft:dark_prismarine"));
                if (up == null) set(x, HROOF + h + 1, z, id("minecraft:dark_prismarine_slab"));
            }
    }

    /** The clock tower (x-16..-12 z72..76): faces south + east, lookout gallery, bell, telescope, flag mast. */
    private static void hmTower() {
        int plaster = id("minecraft:calcite");
        for (int x = -16; x <= -12; x++)
            for (int z = 72; z <= 76; z++) {
                boolean ex = x == -16 || x == -12, ez = z == 72 || z == 76;
                for (int y = HROOF; y <= 91; y++) {
                    if (!ex && !ez) { if (!(x == -13 && z == 73)) set(x, y, z, AIR); continue; }
                    boolean corner = ex && ez;
                    set(x, y, z, corner ? (y % 2 == 0 ? CHISELED_STONE_BRICKS : POLISHED_ANDESITE) : y == 91 ? id("minecraft:dark_prismarine") : plaster);
                }
            }
        // clock faces: south (z76) and east (x-12), a 5x5 dial reading three o'clock
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                int b;
                boolean border = Math.abs(dx) == 2 || Math.abs(dy) == 2, corner = Math.abs(dx) == 2 && Math.abs(dy) == 2;
                if (corner) continue;
                if (border) b = (dx == 0 || dy == 0) ? id("minecraft:gold_block") : id("minecraft:polished_blackstone");
                else if ((dx == 0 && dy == 0) || (dx == 0 && dy == 1) || (dx == 1 && dy == 0)) b = id("minecraft:black_concrete");
                else b = id("minecraft:smooth_quartz");
                set(-14 + dx, 86 + dy, 76, b);
                set(-12, 86 + dy, 74 - dx, b);
            }
        // the lookout gallery: floor y86 overhanging, rail, corner piers, pyramid roof, bell, telescope, mast
        for (int x = -17; x <= -11; x++) for (int z = 71; z <= 77; z++) {
            set(x, 92, z, id("minecraft:smooth_stone"));
            boolean edge = x == -17 || x == -11 || z == 71 || z == 77;
            if (edge) set(x, 93, z, (x == -17 || x == -11) && (z == 71 || z == 77) ? DARK_OAK_FENCE
                    : id("minecraft:dark_oak_fence[" + ((x == -17 || x == -11) ? "north=true,south=true" : "east=true,west=true") + "]"));
        }
        set(-13, 92, 73, id("minecraft:ladder[facing=south]"));
        for (int[] c : new int[][]{{-16, 72}, {-12, 72}, {-16, 76}, {-12, 76}}) fill(c[0], 93, c[1], c[0], 95, c[1], STONE_BRICKS);
        for (int k = 0; k <= 3; k++) {
            int a1 = -17 + k, a2 = -11 - k, b1 = 71 + k, b2 = 77 - k, y = 96 + k;
            for (int x = a1; x <= a2; x++) for (int z = b1; z <= b2; z++) {
                if (k == 3) { set(x, y, z, id("minecraft:dark_prismarine")); continue; }
                if (x == a1) set(x, y, z, stairs("prismarine_brick", "east"));
                else if (x == a2) set(x, y, z, stairs("prismarine_brick", "west"));
                else if (z == b1) set(x, y, z, stairs("prismarine_brick", "south"));
                else if (z == b2) set(x, y, z, stairs("prismarine_brick", "north"));
            }
        }
        set(-14, 95, 74, id("minecraft:bell[attachment=ceiling,facing=north]"));
        set(-14, 93, 76, id("pixelpirates:telescope[facing=south]"));
        set(-12, 93, 74, id("pixelpirates:telescope[facing=east]"));
        fill(-14, 100, 74, -14, 106, 74, SPRUCE_FENCE);
        set(-13, 106, 74, wool("blue")); set(-12, 106, 74, wool("white")); set(-13, 105, 74, wool("white")); set(-12, 105, 74, wool("blue"));
        set(-14, 107, 74, id("minecraft:lightning_rod"));
        set(-14, 95, 73, LANTERN_HANGING);
    }

    /** The portico on the quay side: white columns carrying the balcony, salute guns, lanterns. */
    private static void hmPortico() {
        for (int x = -25; x <= -16; x++) for (int z = 77; z <= 79; z++) { set(x, HG, z, POLISHED_ANDESITE); set(x, HF2, z, id("minecraft:smooth_stone")); }
        for (int x = -25; x <= -16; x++) set(x, HF2 + 1, 79, x == -25 || x == -16 ? DARK_OAK_FENCE : id("minecraft:dark_oak_fence[east=true,west=true]"));
        for (int z = 77; z <= 78; z++) { set(-25, HF2 + 1, z, id("minecraft:dark_oak_fence[north=true,south=true]")); set(-16, HF2 + 1, z, id("minecraft:dark_oak_fence[north=true,south=true]")); }
        for (int x : new int[]{-25, -16}) {
            set(x, HG + 1, 79, id("minecraft:chiseled_quartz_block"));
            fill(x, HG + 2, 79, x, HG + 3, 79, id("minecraft:quartz_pillar[axis=y]"));
            set(x, HG + 4, 79, id("minecraft:chiseled_quartz_block"));
            set(x, HF2 + 2, 79, LANTERN);
        }
        set(-24, HG + 1, 78, id("pixelpirates:display_cannon[facing=south]"));
        set(-17, HG + 1, 78, id("pixelpirates:display_cannon[facing=south]"));
        set(-23, HF2 - 1, 78, LANTERN_HANGING); set(-18, HF2 - 1, 78, LANTERN_HANGING);
        // on the balcony: a telescope on its tripod and a bench
        set(-23, HF2 + 1, 78, id("pixelpirates:telescope[facing=south]"));
        set(-17, HF2 + 1, 77, stairs("dark_oak", "east")); set(-17, HF2 + 1, 78, stairs("dark_oak", "east"));
    }

    /** The signal mast in the west yard: a pole, a yardarm, signal flags. */
    private static void hmSignalMast() {
        set(-33, HG, 68, POLISHED_ANDESITE);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) set(-33 + d[0], HG + 1, 68 + d[1], stairs("stone_brick", d[0] > 0 ? "west" : d[0] < 0 ? "east" : d[1] > 0 ? "north" : "south"));
        fill(-33, HG + 1, 68, -33, 88, 68, SPRUCE_FENCE);
        for (int x = -36; x <= -30; x++) if (x != -33) set(x, 84, 68, id("minecraft:spruce_fence[east=true,west=true]"));
        set(-33, 84, 68, id("minecraft:spruce_fence[east=true,west=true,north=false,south=false]"));
        String[][] flags = {{"red", "yellow"}, {"blue", "white"}, {"yellow", "black"}};
        int[] fx = {-36, -30, -34};
        for (int f = 0; f < 3; f++) {
            int x = fx[f], top = f < 2 ? 83 : 87;
            if (f < 2) set(x, top, 68, CHAIN);
            set(x, top - 1, 68, wool(flags[f][0])); set(x, top - 2, 68, wool(flags[f][1]));
        }
        set(-33, 89, 68, wool("red")); set(-32, 89, 68, wool("red")); set(-31, 89, 68, wool("white"));
        set(-33, 90, 68, id("minecraft:lightning_rod"));
        for (int z = 65; z <= 71; z += 6) set(-35, HG + 1, z, stairs("spruce", "west"));
    }


    private static void warehouses() {
        named("Warehouse (east)", PortCityLayout::tradingWarehouse);
        named("Warehouse (west)", PortCityLayout::distillery);
    }

    // ================================================================ THE BLACKWATER RUM DISTILLERY (#13, rebuilt 2026-10-01)
    /*
     * The old west warehouse is now the port's rum distillery, x -128..-98, z 58..80 (floor y65, loft y72 over the west
     * half): rough stone ground storey, spruce-board upper storey on dark-oak posts, a cobbled-deepslate gable roof with a
     * copper PAGODA VENT over the still hall and a tall brick CHIMNEY. East half = THE STILL HALL, open to the rafters: two
     * big copper POT STILLS on brick fireboxes with swan necks to their worm tubs, three WORKING RUM STILLs over campfires
     * (homestead rum, bring molasses), two molasses VATS, sugar-cane bundles, copper pipe runs. West half: the TASTING ROOM
     * on the street (bar, tables, the DISTILLERY_SIGN outside), THE CASK HALL (rows of aging casks, rum racks), upstairs the
     * MASTER DISTILLER's office + lab and the cask loft whose rail overlooks the stills. Outside: a BARREL YARD with sugar
     * cane on a water channel (west) and a loading stage with a crane over the bay (south).
     */
    private static final int DG = 65, DF2 = 72, DTOP = 78, DROOF = 79;

    private static void distillery() {
        final int x1 = -128, x2 = -98, z1 = 58, z2 = 80;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(-136, DG + 1, z1 - 1, -93, 110, z2 + 1, AIR);
        fill(x1, DG - 2, z1, x2, DG - 1, z2, COBBLE);
        for (int x = x1 + 1; x < x2; x++)
            for (int z = z1 + 1; z < z2; z++) {
                boolean still = x >= -113;
                set(x, DG, z, still ? (((x + z) & 1) == 0 ? id("minecraft:bricks") : STONE_BRICKS) : SPRUCE);
                if (x <= -114) set(x, DF2, z, SPRUCE);
            }
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (ex || ez) dsWallColumn(x, z, ez ? x - x1 : z - z1, ex && ez, ez);
            }
        // partitions: the halls (x-114, ground), tasting room | cask hall (z65), office | loft upstairs (z66); the gallery rail
        fill(-114, DG + 1, z1 + 1, -114, DF2 - 1, z2 - 1, STONE_BRICKS);
        fill(-114, DG + 1, 69, -114, DG + 3, 70, AIR);
        for (int z = 69; z <= 70; z++) { innReserve(-115, DG + 1, z); innReserve(-113, DG + 1, z); innReserve(-115, DG + 2, z); innReserve(-113, DG + 2, z); }
        fill(x1 + 1, DG + 1, 65, -115, DF2 - 1, 65, SPRUCE);
        fill(x1 + 1, DF2 + 1, 66, -115, DTOP - 1, 66, SPRUCE);
        for (int z = z1 + 1; z < z2; z++) set(-114, DF2 + 1, z, railZ());
        innDoor(-121, DG, 65, "spruce", true); innDoor(-121, DF2, 66, "dark_oak", true);
        // stair (cask hall, west wall, rising north) + hole + rail
        for (int k = 0; k < 7; k++) set(-127, DG + 1 + k, 78 - k, stairs("spruce", "north"));
        for (int z = 73; z <= 78; z++) { set(-127, DF2, z, AIR); set(-126, DF2 + 1, z, railZ()); }
        innReserve(-127, DG + 1, 79); innReserve(-127, DG + 2, 79); innReserve(-127, DF2 + 1, 71); innReserve(-127, DF2 + 2, 71);
        // doors: tasting room (street), loading doors (south, the still hall), the yard door (west, cask hall)
        innDoor(-121, DG, z1, "dark_oak", true);
        for (int x = -106; x <= -105; x++) { set(x, DG + 1, z2, door("spruce", "south", false)); set(x, DG + 2, z2, door("spruce", "south", true)); innReserve(x, DG + 1, z2 - 1); innReserve(x, DG + 2, z2 - 1); }
        set(-106, DG + 3, z2, DARK_OAK_LOG_X); set(-105, DG + 3, z2, DARK_OAK_LOG_X);
        innDoor(x1, DG, 68, "spruce", false);
        dsWindows();
        dsRoof();
        dsYards();
        set(-121, DF2 + 1, z1 - 1, id("pixelpirates:distillery_sign[facing=north]"));
        set(x1 - 1, DF2 + 1, 64, id("pixelpirates:distillery_sign[facing=west]"));
        lamppost(-124, DG, z1 - 2); lamppost(-118, DG, z1 - 2);
    }

    private static void dsWallColumn(int x, int z, int u, boolean corner, boolean alongX) {
        for (int y = DG + 1; y <= DTOP; y++) {
            int b;
            int h = Math.floorMod(x * 31 + y * 17 + z * 7, 8);
            if (y < DF2) b = corner ? STONE_BRICKS : h == 0 ? MOSSY_COBBLE : h == 1 ? ANDESITE : h == 2 ? id("minecraft:stone") : COBBLE;
            else if (y == DF2 || y == DTOP) b = corner ? DARK_OAK_LOG_Y : POLISHED_ANDESITE;
            else b = corner || u % 4 == 0 ? DARK_OAK_LOG_Y : SPRUCE;
            set(x, y, z, b);
        }
    }

    private static void dsWindows() {
        int[][] sides = {{-128, 58, 1, 0, 0, -1}, {-128, 80, 1, 0, 0, 1}, {-128, 58, 0, 1, -1, 0}, {-98, 58, 0, 1, 1, 0}};
        for (int[] sd : sides) {
            boolean alongX = sd[2] == 1;
            int pane = id("minecraft:glass_pane[" + (alongX ? "east=true,west=true" : "north=true,south=true") + "]");
            int len = alongX ? 30 : 22;
            for (int u = 2; u <= len - 2; u += 4) {
                int x = sd[0] + sd[2] * u, z = sd[1] + sd[3] * u;
                if (getRaw(x - sd[4], DG + 3, z - sd[5]) == AIR && !isDoorCell(x, DG + 1, z) && getRaw(x, DG + 3, z) != AIR) {
                    set(x, DG + 3, z, pane); set(x, DG + 4, z, pane); set(x, DG + 5, z, STONE_BRICKS); set(x, DG + 2, z, STONE_BRICKS);
                }
                if (getRaw(x, DF2 + 2, z) == SPRUCE && getRaw(x - sd[4], DF2 + 2, z - sd[5]) == AIR) { set(x, DF2 + 2, z, pane); set(x, DF2 + 3, z, pane); }
            }
        }
    }

    /** Cobbled-deepslate gable (ridge along x at z69), spruce underside; the copper pagoda vent; the brick chimney. */
    private static void dsRoof() {
        int tile = id("minecraft:cobbled_deepslate");
        for (int x = -129; x <= -97; x++)
            for (int z = 57; z <= 81; z++) {
                int h = Math.min(z - 57, 81 - z), y = DROOF + h;
                set(x, y, z, h == 12 ? tile : stairs("cobbled_deepslate", z < 69 ? "south" : "north"));
                if (h == 12) set(x, y + 1, z, id("minecraft:cobbled_deepslate_slab"));
                if (h >= 1 && x >= -128 && x <= -98) set(x, y - 1, z, SPRUCE);
                if (x == -128 || x == -98)
                    for (int yy = DROOF; yy < y - 1; yy++) {
                        int dz = z - 69, dy = yy - 85;
                        set(x, yy, z, dz * dz + dy * dy <= 4 ? id("minecraft:glass_pane[north=true,south=true]") : (z - 58) % 4 == 0 ? DARK_OAK_LOG_Y : SPRUCE);
                    }
            }
        // the pagoda vent over the still hall: louvred walls through the roof, a flared copper roof, a vane
        for (int x = -109; x <= -103; x++)
            for (int z = 66; z <= 72; z++) {
                boolean ex = x == -109 || x == -103, ez = z == 66 || z == 72;
                for (int y = DROOF; y <= 93; y++) {
                    if (!ex && !ez) { set(x, y, z, AIR); continue; }
                    if (y < 86) { if (getRaw(x, y, z) != AIR) set(x, y, z, AIR); continue; }
                    set(x, y, z, ex && ez ? DARK_OAK_LOG_Y : y == 93 ? DARK_OAK : id("minecraft:spruce_trapdoor[facing=" + (ez ? (z == 66 ? "north" : "south") : (x == -109 ? "west" : "east")) + ",half=top,open=false]"));
                }
            }
        for (int x = -109; x <= -103; x++) for (int z = 66; z <= 72; z++) if (x == -109 || x == -103 || z == 66 || z == 72) for (int y = DROOF; y < 86; y++) {
            int h = Math.min(z - 57, 81 - z);
            if (y >= DROOF + h - 1) set(x, y, z, (x == -109 || x == -103) && (z == 66 || z == 72) ? DARK_OAK_LOG_Y : SPRUCE);
        }
        String cu = "waxed_cut_copper";
        for (int k = 0; k <= 3; k++) {
            int a1 = -110 + k, a2 = -102 - k, b1 = 65 + k, b2 = 73 - k, y = 94 + k;
            for (int x = a1; x <= a2; x++) for (int z = b1; z <= b2; z++) {
                if (k == 3) { set(x, y, z, id("minecraft:" + cu)); continue; }
                if (x == a1) set(x, y, z, stairs(cu, "east"));
                else if (x == a2) set(x, y, z, stairs(cu, "west"));
                else if (z == b1) set(x, y, z, stairs(cu, "south"));
                else if (z == b2) set(x, y, z, stairs(cu, "north"));
            }
        }
        set(-106, 98, 69, id("minecraft:" + cu + "_slab")); set(-106, 99, 69, id("minecraft:lightning_rod"));
        // the chimney (north-east corner of the still hall)
        fill(-100, DG + 1, 60, -99, 97, 61, id("minecraft:bricks"));
        set(-100, 98, 60, CAMPFIRE); set(-99, 98, 61, CAMPFIRE);
        set(-100, 98, 61, id("minecraft:brick_wall")); set(-99, 98, 60, id("minecraft:brick_wall"));
    }

    /** The barrel yard + sugar cane (west) and the loading stage with a crane over the bay (south). */
    private static void dsYards() {
        for (int x = -136; x <= -129; x++) for (int z = 58; z <= 80; z++) set(x, DG, z, (x + z) % 3 == 0 ? GRAVEL : id("minecraft:coarse_dirt"));
        // a pyramid of casks
        for (int x = -135; x <= -131; x++) for (int z = 59; z <= 60; z++) set(x, DG + 1, z, barrel("east"));
        for (int x = -134; x <= -132; x++) for (int z = 59; z <= 60; z++) set(x, DG + 2, z, barrel("east"));
        set(-133, DG + 3, 59, barrel("east")); set(-133, DG + 3, 60, barrel("east"));
        handCart(-135, DG, 64);
        // sugar cane along a water channel
        for (int x = -135; x <= -130; x++) {
            set(x, DG, 75, WATER);
            for (int z : new int[]{74, 76}) {
                set(x, DG, z, GRASS);
                int hgt = 1 + Math.floorMod(x * 3 + z, 3);
                for (int k = 1; k <= hgt; k++) set(x, DG + k, z, id("minecraft:sugar_cane"));
            }
        }
        for (int x = -136; x <= -129; x++) set(x, DG + 1, 78, x % 3 == 0 ? OAK_FENCE : id("minecraft:oak_fence[east=true,west=true]"));
        for (int z = 70; z <= 72; z++) set(-130, DG + 1, z, id("minecraft:bamboo_block[axis=y]"));
        set(-130, DG + 2, 71, id("minecraft:bamboo_block[axis=x]"));
        lamppost(-131, DG, 68);
        // loading stage over the bay: a deck on pilings, a crane, barrels waiting to go aboard
        for (int x = -111; x <= -100; x++) for (int z = 81; z <= 84; z++) { set(x, DG, z, (x + z) % 5 == 0 ? DARK_OAK : SPRUCE); fill(x, DG + 1, z, x, DG + 8, z, AIR); }
        for (int x = -111; x <= -100; x += 11) for (int z = 81; z <= 84; z += 3) fill(x, 52, z, x, DG - 1, z, SPRUCE_LOG_Y);
        for (int x = -111; x <= -100; x++) if (x < -107 || x > -104) set(x, DG + 1, 84, id("minecraft:spruce_fence[east=true,west=true]"));
        crane(-102, DG, 82);
        set(-109, DG + 1, 82, barrel("south")); set(-108, DG + 1, 82, barrel("south")); set(-109, DG + 2, 82, barrel("south"));
        set(-110, DG + 1, 83, id("pixelpirates:rope_coil[facing=south]"));
    }

    /** Big decorative copper pot still on a brick firebox; the swan neck runs south to its worm tub. */
    private static void potStill(int cx, int cz) {
        String cu = "waxed_cut_copper";
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            set(cx + dx, DG + 1, cz + dz, dx == 0 && dz == 0 ? CAMPFIRE : id("minecraft:bricks"));
            set(cx + dx, DG + 2, cz + dz, id("minecraft:" + cu));
            set(cx + dx, DG + 3, cz + dz, id("minecraft:" + cu));
        }
        set(cx, DG + 1, cz - 1, id("minecraft:iron_bars"));                     // the firebox door
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 1}, {0, 1, 2}, {0, -1, 3}}) {
            String f = d[0] > 0 ? "west" : d[0] < 0 ? "east" : d[1] > 0 ? "north" : "south";
            set(cx + d[0], DG + 4, cz + d[1], stairs(cu, f));
        }
        set(cx, DG + 4, cz, id("minecraft:" + cu));
        for (int y = DG + 5; y <= DG + 8; y++) set(cx, y, cz, id("minecraft:lightning_rod[facing=up]"));
        for (int z = cz + 1; z <= cz + 4; z++) set(cx, DG + 8, z, id("minecraft:lightning_rod[facing=south]"));
        for (int y = DG + 4; y <= DG + 7; y++) set(cx, y, cz + 4, id("minecraft:lightning_rod[facing=up]"));
        set(cx, DG + 1, cz + 4, barrel("up")); set(cx, DG + 2, cz + 4, barrel("up")); set(cx, DG + 3, cz + 4, CAULDRON_WATER);
        set(cx + 1, DG + 1, cz + 4, id("pixelpirates:spirit_bottles[facing=south,count=3]"));
    }

    // ================================================================ THE TRADING COMPANY WAREHOUSE (#12, rebuilt 2026-10-01)
    /*
     * The Wavebreak Trading Company's bonded warehouse, x -70..-38, z 60..84 (harbour terrace, floor y65; loft floor y72):
     * red brick with stone quoins and bands, round-headed windows, a low-pitched mud-tile roof with a glazed MONITOR along
     * the ridge. Quay side (south): the big loading door at x-56..-52 (on the quay's cargo-yard lane), a loading dock, two
     * stacks of LOOPHOLE DOORS with JIB BEAMS + hanging crates (x-67/-42). Street side (north): the office door under a
     * pediment, two WAREHOUSE_SIGNs, company banners. Inside: racks of SPICES, TEA + SILK, RUM + WINE and NAVAL STORES, a
     * rail track down the aisle, the weighbridge, the glass TALLY OFFICE, a freight hoist through the loft floor, the stair;
     * the LOFT (grain, tobacco, crates, the cooper's corner) under the monitor.
     */
    private static final int WG = 65, WF2 = 72, WTOP = 78, WROOF = 79;

    private static void tradingWarehouse() {
        final int x1 = -70, x2 = -38, z1 = 60, z2 = 84;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(x1 - 1, WG + 1, z1 - 1, x2 + 1, 100, z2, AIR);
        fill(x1, WG - 2, z1, x2, WG - 1, z2, COBBLE);
        for (int x = x1 + 1; x < x2; x++)
            for (int z = z1 + 1; z < z2; z++) {
                set(x, WG, z, (x + z) % 5 == 0 ? STONE_BRICKS : id("minecraft:smooth_stone"));
                set(x, WF2, z, SPRUCE);
            }
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (ex || ez) twWallColumn(x, z, ez ? x - x1 : z - z1, ex && ez);
            }
        // the big loading door (south), with a timber lintel and a sliding-door leaf parked beside it
        for (int x = -56; x <= -52; x++) for (int y = WG + 1; y <= WG + 4; y++) set(x, y, z2, AIR);
        fill(-57, WG + 5, z2, -51, WG + 5, z2, DARK_OAK_LOG_X);
        fill(-50, WG + 1, z2 + 1, -48, WG + 4, z2 + 1, id("minecraft:spruce_planks"));
        set(-50, WG + 5, z2 + 1, id("minecraft:chain[axis=x]")); set(-49, WG + 5, z2 + 1, id("minecraft:chain[axis=x]"));
        for (int x = -56; x <= -52; x++) { innReserve(x, WG + 1, z2 - 1); innReserve(x, WG + 2, z2 - 1); }
        // the loading dock either side of the door
        for (int x = x1; x <= x2; x++) if (x < -58 || x > -50) set(x, WG + 1, z2 + 1, id("minecraft:smooth_stone_slab"));
        for (int x = x1; x <= x2; x += 8) if (x < -58 || x > -50) set(x, WG + 1, z2 + 1, id("minecraft:polished_blackstone_wall"));
        // street door + pediment (north)
        set(-54, WG + 1, z1, door("dark_oak", "north", false)); set(-54, WG + 2, z1, door("dark_oak", "north", true));
        innReserve(-54, WG + 1, z1 + 1); innReserve(-54, WG + 2, z1 + 1);
        set(-54, WG + 3, z1, id("minecraft:glass_pane[east=true,west=true]"));
        for (int k = 0; k <= 3; k++) {
            set(-57 + k, WG + 4 + k, z1 - 1, id("minecraft:stone_brick_stairs[facing=east]"));
            set(-51 - k, WG + 4 + k, z1 - 1, id("minecraft:stone_brick_stairs[facing=west]"));
        }
        set(-54, WG + 7, z1 - 1, CHISELED_STONE_BRICKS);
        set(-55, WG + 3, z1 - 1, LANTERN); set(-53, WG + 3, z1 - 1, LANTERN);
        set(-55, WG + 2, z1 - 1, id("minecraft:stone_brick_wall")); set(-53, WG + 2, z1 - 1, id("minecraft:stone_brick_wall"));
        set(-55, WG + 1, z1 - 1, id("minecraft:stone_brick_wall")); set(-53, WG + 1, z1 - 1, id("minecraft:stone_brick_wall"));
        set(-60, WF2 + 1, z1 - 1, id("pixelpirates:warehouse_sign[facing=north]"));
        set(-48, WF2 + 1, z1 - 1, id("pixelpirates:warehouse_sign[facing=north]"));
        for (int x = -66; x <= -42; x += 6) if (Math.abs(x + 54) > 4) set(x, WF2 + 3, z1 - 1, wallBanner(x < -54 ? "brown" : "yellow", "north"));
        // loophole doors on the quay side (loft + attic), each with a jib beam and a crate on the hook
        for (int lx : new int[]{-67, -42}) {
            for (int x = lx; x <= lx + 1; x++) {
                fill(x, WF2 + 1, z2, x, WF2 + 3, z2, AIR);
                set(x, WF2 + 1, z2, id("minecraft:spruce_fence_gate[facing=south,open=false]"));
                set(x, WF2 + 4, z2, DARK_OAK_LOG_X);
            }
            set(lx - 1, WF2 + 2, z2 + 1, id("minecraft:spruce_trapdoor[facing=south,half=bottom,open=true]"));
            set(lx + 2, WF2 + 2, z2 + 1, id("minecraft:spruce_trapdoor[facing=south,half=bottom,open=true]"));
            for (int z = z2 - 1; z <= z2 + 3; z++) set(lx, WTOP + 2, z, DARK_OAK_LOG_Z);
            set(lx, WTOP + 1, z2 + 3, CHAIN); set(lx, WTOP, z2 + 3, CHAIN); set(lx, WTOP - 1, z2 + 3, CHAIN);
            set(lx, WTOP - 2, z2 + 3, id("pixelpirates:cargo_crate[facing=south]"));
            // the ground-floor bay under it
            for (int x = lx; x <= lx + 1; x++) {
                set(x, WG + 1, z2, door("spruce", "south", false)); set(x, WG + 2, z2, door("spruce", "south", true));
                innReserve(x, WG + 1, z2 - 1); innReserve(x, WG + 2, z2 - 1);
            }
        }
        // stair (west wall, rising north) + hole + rail; freight hatch in the loft floor
        for (int k = 0; k < 7; k++) set(-69, WG + 1 + k, 79 - k, stairs("spruce", "north"));
        for (int z = 74; z <= 79; z++) { set(-69, WF2, z, AIR); set(-68, WF2 + 1, z, railZ()); }
        innReserve(-69, WG + 1, 80); innReserve(-69, WG + 2, 80); innReserve(-69, WF2 + 1, 72); innReserve(-69, WF2 + 2, 72);
        for (int x = -56; x <= -53; x++) for (int z = 66; z <= 68; z++) set(x, WF2, z, AIR);
        for (int x = -57; x <= -52; x++) { set(x, WF2 + 1, 65, railX()); set(x, WF2 + 1, 69, railX()); }
        for (int z = 66; z <= 68; z++) { set(-57, WF2 + 1, z, railZ()); set(-52, WF2 + 1, z, railZ()); }
        twWindows();
        twRoof();
    }

    private static void twWallColumn(int x, int z, int u, boolean corner) {
        int brick = id("minecraft:bricks");
        for (int y = WG + 1; y <= WTOP; y++) {
            int b;
            if (y == WG + 1) b = STONE_BRICKS;
            else if (corner) b = (y % 2 == 0) ? STONE_BRICKS : POLISHED_ANDESITE;
            else if (y == WF2 || y == WTOP) b = STONE_BRICKS;
            else if (u % 8 == 0) b = id("minecraft:mud_bricks");                     // pilasters
            else b = Math.floorMod(x * 13 + y * 7 + z * 5, 17) == 0 ? id("minecraft:granite") : brick;
            set(x, y, z, b);
        }
    }

    private static void twWindows() {
        int brick = id("minecraft:bricks");
        int[][] sides = {{-70, 60, 1, 0, 0, -1}, {-70, 84, 1, 0, 0, 1}, {-70, 60, 0, 1, -1, 0}, {-38, 60, 0, 1, 1, 0}};
        String[] out = {"north", "south", "west", "east"};
        for (int s = 0; s < 4; s++) {
            int[] sd = sides[s];
            boolean alongX = sd[2] == 1;
            int pane = id("minecraft:glass_pane[" + (alongX ? "east=true,west=true" : "north=true,south=true") + "]");
            int len = alongX ? 32 : 24;
            for (int u = 4; u <= len - 4; u += 8) {
                int x = sd[0] + sd[2] * u, z = sd[1] + sd[3] * u;
                for (int[] w : new int[][]{{WG + 2, WG + 4}, {WF2 + 2, WF2 + 4}}) {
                    for (int k = -1; k <= 1; k++) {
                        int wx = x + (alongX ? k : 0), wz = z + (alongX ? 0 : k);
                        if (getRaw(wx, w[0], wz) != brick && getRaw(wx, w[0], wz) != id("minecraft:granite")) continue;
                        if (getRaw(wx - sd[4], w[0], wz - sd[5]) != AIR) continue;
                        for (int y = w[0]; y <= w[1]; y++) set(wx, y, wz, pane);
                        set(wx, w[1] + 1, wz, k == 0 ? id("minecraft:stone_bricks") : id("minecraft:stone_brick_stairs[facing=" + (alongX ? (k < 0 ? "east" : "west") : (k < 0 ? "south" : "north")) + ",half=top]"));
                        set(wx, w[0] - 1, wz, id("minecraft:stone_brick_slab[type=top]"));
                    }
                }
            }
        }
    }

    /** Low-pitched mud-tile roof (one block up every two rows), ridge along x at z72, and the glazed monitor on it. */
    private static void twRoof() {
        for (int x = -71; x <= -37; x++)
            for (int z = 59; z <= 85; z++) {
                int h = Math.min(z - 59, 85 - z), y = WROOF + h / 2;
                if (h == 13) { set(x, y, z, id("minecraft:mud_bricks")); set(x, y + 1, z, id("minecraft:mud_brick_slab")); continue; }
                set(x, y, z, id("minecraft:mud_brick_slab[type=" + ((h & 1) == 0 ? "bottom" : "top") + "]"));
                if (h >= 2 && x >= -70 && x <= -38) set(x, y - 1, z, SPRUCE);
                if (x == -70 || x == -38)
                    for (int yy = WROOF; yy < y; yy++) set(x, yy, z, (z - 60) % 8 == 0 ? id("minecraft:mud_bricks") : id("minecraft:bricks"));
            }
        // the monitor: glazed sides on the ridge, its own little slab roof
        for (int x = -64; x <= -44; x++) {
            for (int z : new int[]{69, 75}) {
                set(x, 85, z, (x + 64) % 4 == 0 ? STONE_BRICKS : id("minecraft:glass_pane[east=true,west=true]"));
                set(x, 86, z, (x + 64) % 4 == 0 ? STONE_BRICKS : id("minecraft:glass_pane[east=true,west=true]"));
                set(x, 84, z, STONE_BRICKS);
            }
            for (int z = 70; z <= 74; z++) for (int y = 84; y <= 86; y++) set(x, y, z, AIR);
            for (int z = 68; z <= 76; z++) set(x, 87, z, id("minecraft:mud_brick_slab"));
        }
        for (int z = 69; z <= 75; z++) for (int y = 84; y <= 86; y++) { set(-65, y, z, STONE_BRICKS); set(-43, y, z, STONE_BRICKS); }
        // open the loft ceiling under the monitor so the light comes down
        for (int x = -64; x <= -44; x++) for (int z = 70; z <= 74; z++) for (int y = WTOP; y <= 83; y++) set(x, y, z, AIR);
        for (int x = -64; x <= -44; x += 5) for (int z = 70; z <= 74; z++) set(x, 83, z, id("minecraft:spruce_log[axis=z]"));
    }

    private static void warehouse(int x1, int x2, int z1, int z2, String roofMat, int wallId, boolean loot) {
        named(x1 < -100 ? "Warehouse (west)" : "Warehouse (east)", () -> warehouseBody(x1, x2, z1, z2, roofMat, wallId, loot));
    }

    private static void warehouseBody(int x1, int x2, int z1, int z2, String roofMat, int wallId, boolean loot) {
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
    /**
     * THE WAVEBREAK FISH HALL (rebuilt 2026-10-01, building-by-building overhaul #1): a basilica-style market hall on a
     * stone plinth - a tall central nave (ridge along x, clerestory windows) between two low lean-to aisles. The aisles
     * hold the ice fish counters with the stock shelved behind; the nave has the smoking hearth, water troughs and nets.
     * Open arches at both nave ends (each crowned with a shark trophy as the shop sign), gaps through each aisle to the
     * street (north) and the quay (south), and a paved apron down to the quay.
     */
    private static void fishMarket() {
        final int x1 = 10, x2 = 34, z1 = 60, z2 = 74, gy = 65;
        final int nN = 64, nS = 70;                                   // the nave wall lines
        int dark = DARK_OAK_LOG_Y, planks = SPRUCE;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        // plinth + floor: stone-brick kerb, spruce boards, a polished centre aisle down the nave
        fill(x1 - 1, gy - 1, z1 - 1, x2 + 1, gy - 1, z2 + 1, COBBLE);
        fill(x1 - 1, gy, z1 - 1, x2 + 1, gy, z2 + 1, STONE_BRICKS);
        fill(x1, gy, z1, x2, gy, z2, planks);
        fill(x1, gy, 66, x2, gy, 68, POLISHED_ANDESITE);
        fill(x1 - 1, gy + 1, z1 - 1, x2 + 1, gy + 12, z2 + 1, AIR);
        // posts: low on the outer aisle lines, tall on the nave lines; upside-down stair corbels under the beams
        for (int x = x1; x <= x2; x += 6) {
            for (int z : new int[]{z1, z2}) fill(x, gy + 1, z, x, gy + 3, z, dark);
            for (int z : new int[]{nN, nS}) fill(x, gy + 1, z, x, gy + 6, z, dark);
            set(x, gy + 6, nN - 1, stairsTop("dark_oak", "south"));
            set(x, gy + 6, nS + 1, stairsTop("dark_oak", "north"));
        }
        // aisle beams + lean-to roofs sloping out to the eaves (one block overhang)
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            set(x, gy + 4, z1, SPRUCE_LOG_X); set(x, gy + 4, z2, SPRUCE_LOG_X);
            set(x, gy + 4, z1 - 1, stairs("spruce", "south")); set(x, gy + 4, z2 + 1, stairs("spruce", "north"));
            set(x, gy + 5, z1, stairs("spruce", "south")); set(x, gy + 5, z1 + 1, slab("spruce"));
            set(x, gy + 5, z2, stairs("spruce", "north")); set(x, gy + 5, z2 - 1, slab("spruce"));
            set(x, gy + 5, z1 + 2, stairs("spruce", "south")); set(x, gy + 5, z2 - 2, stairs("spruce", "north"));
            set(x, gy + 6, z1 + 3, stairs("spruce", "south")); set(x, gy + 6, z2 - 3, stairs("spruce", "north"));
        }
        // clerestory: the nave walls above the aisle roofs, windows between the posts
        for (int x = x1; x <= x2; x++)
            for (int z : new int[]{nN, nS})
                set(x, gy + 7, z, (x - x1) % 6 == 0 ? dark : (x - x1) % 6 == 3 ? GLASS_PANE : planks);
        for (int x : new int[]{x1 - 1, x2 + 1}) { set(x, gy + 7, nN, stairs("spruce", "south")); set(x, gy + 7, nS, stairs("spruce", "north")); }
        gableRoofX(x1, x2, nN + 1, nS - 1, gy + 8, "dark_oak", DARK_OAK);
        // nave gable ends: planks over a log lintel, an open arch below, the shark trophy as the shop sign
        for (int xe : new int[]{x1, x2}) {
            gableEndsX(xe, nN + 1, nS - 1, gy + 8, planks);
            fill(xe, gy + 7, nN + 1, xe, gy + 7, nS - 1, SPRUCE_LOG_Z);
            for (int z = nN + 1; z <= nS - 1; z++) for (int y = gy + 1; y <= gy + 6; y++) set(xe, y, z, AIR);
            set(xe, gy + 6, nN + 1, stairsTop("dark_oak", "south"));
            set(xe, gy + 6, nS - 1, stairsTop("dark_oak", "north"));
            set(xe + (xe == x1 ? -1 : 1), gy + 9, 67, id("pixelpirates:shark_trophy[facing=" + (xe == x1 ? "west" : "east") + "]"));
        }
        // aisle back walls (outer lines): half-height boards with a fence rail, open bays at the street/quay passage
        for (int x = x1 + 1; x < x2; x++) {
            if ((x - x1) % 6 == 0 || (x >= 20 && x <= 24)) continue;
            for (int z : new int[]{z1, z2}) { set(x, gy + 1, z, planks); set(x, gy + 2, z, SPRUCE_FENCE); }
        }
        // the fish counters: ice displays between board counters, the catch laid out on the ice
        for (int x = x1 + 2; x <= x2 - 2; x++) {
            if (x >= 20 && x <= 24) continue;
            for (int z : new int[]{z1 + 2, z2 - 2}) {
                boolean ice = (x & 1) == 0;
                set(x, gy + 1, z, ice ? id("minecraft:packed_ice") : slabTop("spruce"));
                if (ice) set(x, gy + 2, z, x % 4 == 0 ? id("minecraft:sea_pickle[pickles=3,waterlogged=false]")
                                                       : id("minecraft:dead_tube_coral_fan[waterlogged=false]"));
            }
        }
        // stock behind the counters, against the back walls
        for (int x = x1 + 2; x <= x2 - 3; x += 3) {
            if (x >= 18 && x <= 25) continue;
            set(x, gy + 1, z1 + 1, (x / 3) % 3 == 0 ? id("pixelpirates:lobster_pot") : BARREL_UP);
            set(x + 1, gy + 1, z1 + 1, id("pixelpirates:cargo_crate[facing=south]"));
            set(x, gy + 1, z2 - 1, (x / 3) % 3 == 1 ? id("pixelpirates:fish_trap") : id("minecraft:barrel[facing=north]"));
            set(x + 1, gy + 1, z2 - 1, BARREL_UP);
        }
        // the nave: smoking hearth (campfires on a stone bed, smokers either side), water troughs, gutting benches, ledger
        fill(20, gy + 1, 67, 24, gy + 1, 67, COBBLE);
        for (int x = 21; x <= 23; x++) set(x, gy + 2, 67, CAMPFIRE);
        set(19, gy + 1, 67, id("minecraft:smoker[facing=west]"));
        set(25, gy + 1, 67, id("minecraft:smoker[facing=east]"));
        set(14, gy + 1, 66, CAULDRON_WATER); set(14, gy + 1, 68, CAULDRON_WATER);
        set(30, gy + 1, 66, CAULDRON_WATER); set(30, gy + 1, 68, CAULDRON_WATER);
        set(13, gy + 1, 67, id("minecraft:spruce_trapdoor[facing=east,half=top,open=false]"));
        set(31, gy + 1, 67, id("minecraft:spruce_trapdoor[facing=west,half=top,open=false]"));
        set(17, gy + 1, 65, LECTERN_N);
        // nets and lamps hung from the nave beams; lamps over the counters
        for (int x = x1 + 3; x <= x2 - 3; x += 6) {
            set(x, gy + 7, 67, CHAIN); set(x, gy + 6, 67, LANTERN_HANGING);
            set(x, gy + 6, nN + 1, id("pixelpirates:hanging_net")); set(x, gy + 5, nN + 1, id("pixelpirates:hanging_net"));
            set(x, gy + 6, nS - 1, id("pixelpirates:hanging_net")); set(x, gy + 5, nS - 1, id("pixelpirates:hanging_net"));
            set(x, gy + 3, z1 + 1, LANTERN_HANGING); set(x, gy + 3, z2 - 1, LANTERN_HANGING);
        }
        set(22, gy + 4, nN + 1, id("pixelpirates:reefback_trophy[facing=south]"));   // on the hearth side of a nave post line
        // aprons: to the street (north) and down to the quay (south)
        fill(20, gy, z1 - 2, 24, gy, z1 - 1, POLISHED_ANDESITE);
        for (int z = z2 + 1; z <= 81; z++) for (int x = 20; x <= 24; x++) set(x, gy, z, x == 20 || x == 24 ? STONE_BRICKS : POLISHED_ANDESITE);
        lamppost(19, gy, z2 + 3); lamppost(25, gy, z2 + 3);
        keepClear(x1 - 1, z1 - 2, x2 + 1, z2 + 7);                    // nothing else furnishes in here
        fishMarketExtras();
    }

    /**
     * FISH HALL round 2 (2026-10-01): four sunken AQUARIUM TANKS in the nave floor (sand, coral, seagrass, sea pickles, a
     * sea lantern underneath, lily pads), FISH_SIGNs on both gables, the AUCTION YARD (podium with lectern + bell, benches,
     * the catch on pallets) and the DRYING YARD (net racks, dried kelp, salt piles) on the quay side, and two annexes
     * east of the hall: the SMOKEHOUSE (smokers, fires under hanging racks, chimney) and the turf-roofed ICE HOUSE.
     */
    private static void fishMarketExtras() {
        final int gy = 65;
        // aquarium tanks either side of the nave walkway (z67)
        String[] plants = {"minecraft:tube_coral[waterlogged=true]", "minecraft:seagrass", "minecraft:brain_coral[waterlogged=true]",
                "minecraft:sea_pickle[pickles=3,waterlogged=true]", "minecraft:fire_coral[waterlogged=true]", "minecraft:seagrass", "minecraft:horn_coral[waterlogged=true]"};
        for (int[] t : new int[][]{{15, 65}, {26, 65}, {15, 68}, {26, 68}})
            for (int x = t[0]; x <= t[0] + 3; x++)
                for (int z = t[1]; z <= t[1] + 1; z++) {
                    set(x, gy - 2, z, (x + z) % 4 == 0 ? id("minecraft:sea_lantern") : SAND);
                    set(x, gy - 1, z, id(plants[Math.floorMod(x * 3 + z * 5, plants.length)]));
                    set(x, gy, z, WATER);
                    set(x, gy + 1, z, (x + z) % 3 == 0 ? id("minecraft:lily_pad") : AIR);
                }
        // the signs, one on each gable under the shark trophy
        set(9, gy + 7, 66, id("pixelpirates:fish_sign[facing=west]"));
        set(35, gy + 7, 68, id("pixelpirates:fish_sign[facing=east]"));
        // ---- THE AUCTION YARD (quay side, west of the apron)
        for (int x = 10; x <= 19; x++) for (int z = 76; z <= 81; z++) set(x, gy, z, (x + z) % 2 == 0 ? COBBLE : POLISHED_ANDESITE);
        for (int x = 12; x <= 14; x++) { set(x, gy + 1, 80, STONE_BRICKS); set(x, gy + 1, 81, STONE_BRICKS); set(x, gy + 1, 79, stairs("stone_brick", "south")); }
        set(13, gy + 2, 81, id("minecraft:lectern[facing=north]")); set(14, gy + 2, 80, id("minecraft:bell[attachment=floor,facing=north]"));
        set(12, gy + 2, 81, SPRUCE_FENCE); set(12, gy + 3, 81, LANTERN);
        for (int x : new int[]{11, 12, 14, 15}) set(x, gy + 1, 77, stairs("spruce", "north"));
        for (int x = 16; x <= 18; x++) for (int z = 79; z <= 81; z++) set(x, gy + 1, z, id("minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]"));
        set(16, gy + 2, 80, id("pixelpirates:lobster_pot")); set(17, gy + 2, 80, BARREL_UP); set(18, gy + 2, 79, id("pixelpirates:fish_trap"));
        set(17, gy + 2, 81, id("minecraft:composter[level=7]")); set(18, gy + 2, 81, id("pixelpirates:lobster_pot"));
        set(10, gy + 1, 81, BARREL_UP); set(10, gy + 2, 81, id("pixelpirates:drinks_menu[facing=east]"));
        // ---- THE DRYING YARD (east of the apron): net racks, dried kelp, salt
        for (int x = 25; x <= 34; x++) for (int z = 76; z <= 81; z++) set(x, gy, z, (x * 3 + z) % 4 == 0 ? GRAVEL : POLISHED_ANDESITE);
        for (int x = 26; x <= 34; x += 4) fill(x, gy + 1, 77, x, gy + 3, 77, SPRUCE_FENCE);
        for (int x = 27; x <= 33; x++) {
            if ((x - 26) % 4 == 0) continue;
            set(x, gy + 3, 77, id("minecraft:spruce_fence[east=true,west=true]"));
            set(x, gy + 2, 77, id("pixelpirates:hanging_net"));
        }
        for (int x = 26; x <= 28; x++) { set(x, gy + 1, 80, id("minecraft:dried_kelp_block")); if (x == 27) set(x, gy + 2, 80, id("minecraft:dried_kelp_block")); }
        for (int x = 31; x <= 33; x++) for (int z = 80; z <= 81; z++) set(x, gy + 1, z, id("minecraft:white_concrete_powder"));
        set(32, gy + 2, 80, id("minecraft:white_concrete_powder")); set(34, gy + 1, 81, BARREL_UP); set(34, gy + 1, 80, BARREL_UP);
        // ---- the annexes east of the hall
        fill(36, gy + 1, 58, 45, gy + 15, 81, AIR);
        for (int x = 36; x <= 45; x++) for (int z = 58; z <= 81; z++) set(x, gy, z, (x + z) % 5 == 0 ? GRAVEL : DIRT_PATH);
        // THE SMOKEHOUSE x37..43 z60..67
        for (int x = 37; x <= 43; x++)
            for (int z = 60; z <= 67; z++) {
                boolean edge = x == 37 || x == 43 || z == 60 || z == 67;
                set(x, gy, z, edge ? COBBLE : STONE_BRICKS);
                for (int y = gy + 1; y <= gy + 4; y++) set(x, y, z, edge ? ((x + y + z) % 4 == 0 ? MOSSY_COBBLE : COBBLE) : AIR);
            }
        for (int k = 0; k <= 4; k++)
            for (int x = 36; x <= 44; x++) {
                set(x, gy + 5 + k, 59 + k, stairs("spruce", "south")); set(x, gy + 5 + k, 68 - k, stairs("spruce", "north"));
                if (k == 4) set(x, gy + 9, 63, SPRUCE); if (k == 4) set(x, gy + 9, 64, SPRUCE);
            }
        for (int z = 60; z <= 67; z++) for (int y = gy + 5; y <= gy + 8; y++) { if (getRaw(37, y, z) == AIR && y < gy + 5 + Math.min(z - 59, 68 - z)) set(37, y, z, COBBLE); if (getRaw(43, y, z) == AIR && y < gy + 5 + Math.min(z - 59, 68 - z)) set(43, y, z, COBBLE); }
        set(37, gy + 1, 64, door("spruce", "west", false)); set(37, gy + 2, 64, door("spruce", "west", true));
        fill(41, gy + 1, 61, 41, gy + 13, 61, COBBLE); set(41, gy + 14, 61, CAMPFIRE);
        for (int x = 38; x <= 42; x++) set(x, gy + 1, 61, x == 41 ? CAMPFIRE : id("minecraft:smoker[facing=south]"));
        for (int x = 39; x <= 41; x++) { set(x, gy + 1, 64, CAMPFIRE); set(x, gy + 4, 64, id("pixelpirates:hanging_net")); set(x, gy + 4, 65, id("pixelpirates:hanging_net")); }
        set(42, gy + 1, 66, id("minecraft:dried_kelp_block")); set(42, gy + 2, 66, id("minecraft:dried_kelp_block")); set(38, gy + 1, 66, BARREL_UP);
        set(43, gy + 3, 64, id("minecraft:iron_bars"));
        // THE ICE HOUSE x37..43 z70..79: stone walls lined with packed ice, a turf roof, blocks of blue ice, snow underfoot
        for (int x = 37; x <= 43; x++)
            for (int z = 70; z <= 79; z++) {
                boolean edge = x == 37 || x == 43 || z == 70 || z == 79, lining = !edge && (x == 38 || x == 42 || z == 71 || z == 78);
                set(x, gy, z, edge ? STONE_BRICKS : id("minecraft:snow_block"));
                for (int y = gy + 1; y <= gy + 3; y++) set(x, y, z, edge ? STONE_BRICKS : lining ? id("minecraft:packed_ice") : AIR);
                set(x, gy + 4, z, GRASS);
                if (!edge && (x * 7 + z * 3) % 5 == 0) set(x, gy + 5, z, id((x + z) % 2 == 0 ? "minecraft:grass" : "minecraft:dandelion"));
            }
        for (int y = gy + 1; y <= gy + 2; y++) { set(37, y, 74, y == gy + 1 ? door("spruce", "west", false) : door("spruce", "west", true)); set(38, y, 74, AIR); }
        for (int x = 39; x <= 41; x++) for (int z = 72; z <= 77; z += 2) { set(x, gy + 1, z, id("minecraft:blue_ice")); if (x == 40) set(x, gy + 2, z, id("minecraft:blue_ice")); }
        set(41, gy + 1, 76, BARREL_UP); set(39, gy + 3, 74, id("minecraft:soul_lantern[hanging=true]"));
        lamppost(44, gy, 69); lamppost(36, gy, 58);
    }

    /** Small dockside office at the east end of the quay. */
    // ================================================================ THE DOCK OFFICE + EAST BERTHS (#15, rebuilt 2026-10-01)
    /*
     * Where HARBOUR DUES are paid (homestead/harbour/HarbourDues). The office x 102..116, z 58..72 (floor y65, upstairs
     * y70): stone ground storey, dusty-blue boards above with white trim and birch shutters, a red hipped roof with a glazed
     * lantern cupola, DOCK_SIGNs. Inside: the public hall with two DUES LEDGERs in the counter (pay / ask), DUES BOARDs, the
     * berth chart, benches; behind the counter the dockmaster's desk, lockers, hawsers, the strongbox; upstairs the WATCH
     * ROOM (south, telescope, signal lamp) and the dockmaster's quarters. South: a veranda and a BOARDWALK (x108..111) down
     * the shore to THE EAST BERTHS - a jetty on pilings (x98..132, z96..99) with a BERTH BOLLARD every 6 blocks on its
     * seaward edge, a DUES KIOSK with its own ledger, lamps, a crane and supplies.
     */
    private static final int KG = 65, KF2 = 70, KTOP = 75, KROOF = 76;

    private static void dockOffice() {
        final int x1 = 102, x2 = 116, z1 = 58, z2 = 72;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(100, KG + 1, 56, 120, 96, 76, AIR);
        fill(x1, KG - 2, z1, x2, KG - 1, z2, COBBLE);
        for (int x = x1 + 1; x < x2; x++)
            for (int z = z1 + 1; z < z2; z++) {
                set(x, KG, z, z <= 63 ? (((x + z) & 1) == 0 ? POLISHED_ANDESITE : id("minecraft:polished_diorite")) : SPRUCE);
                set(x, KF2, z, SPRUCE);
                set(x, KTOP, z, SPRUCE);
            }
        int blue = id("minecraft:light_blue_terracotta"), white = id("minecraft:stripped_birch_log[axis=y]");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) continue;
                boolean corner = ex && ez;
                for (int y = KG + 1; y <= KROOF; y++) {
                    int b;
                    if (y == KG + 1) b = COBBLE;
                    else if (y < KF2) b = corner ? CHISELED_STONE_BRICKS : STONE_BRICKS;
                    else if (y == KF2 || y == KTOP) b = corner ? white : id("minecraft:stripped_birch_log[axis=" + (ez ? "x" : "z") + "]");
                    else if (y == KROOF) b = white;
                    else b = corner ? white : blue;
                    set(x, y, z, b);
                }
            }
        // partitions: counter line (z64) is furniture; upstairs the quarters | watch room (z65)
        fill(x1 + 1, KF2 + 1, 65, x2 - 1, KTOP - 1, 65, SPRUCE);
        innDoor(108, KF2, 65, "birch", true);
        // stair: east wall, rising north + hole + rail
        for (int k = 0; k < 5; k++) set(115, KG + 1 + k, 71 - k, stairs("spruce", "north"));
        for (int z = 68; z <= 71; z++) { set(115, KF2, z, AIR); set(114, KF2 + 1, z, railZ()); }
        innReserve(115, KG + 1, 72 - 0); innReserve(115, KF2 + 1, 66); innReserve(115, KF2 + 2, 66);
        // doors: street (north), the berths (south, double), windows
        innDoor(109, KG, z1, "spruce", true);
        for (int x = 109; x <= 110; x++) { set(x, KG + 1, z2, door("birch", "south", false)); set(x, KG + 2, z2, door("birch", "south", true)); innReserve(x, KG + 1, z2 - 1); innReserve(x, KG + 2, z2 - 1); innReserve(x, KG + 1, z2 + 1); }
        set(109, KG + 3, z2, id("minecraft:stripped_birch_log[axis=x]")); set(110, KG + 3, z2, id("minecraft:stripped_birch_log[axis=x]"));
        dkWindows();
        dkRoof();
        // the veranda over the boardwalk's head
        for (int x = 104; x <= 114; x++) for (int z = 73; z <= 75; z++) { set(x, KG, z, SPRUCE); set(x, KF2, z, id("minecraft:red_nether_brick_slab")); }
        for (int x = 104; x <= 114; x += 5) fill(x, KG + 1, 75, x, KF2 - 1, 75, white);
        for (int x : new int[]{105, 106, 113, 114}) set(x, KG + 1, 73, stairs("spruce", "north"));
        set(107, KF2 - 1, 74, LANTERN_HANGING); set(112, KF2 - 1, 74, LANTERN_HANGING);
        set(104, KG + 1, 73, id("pixelpirates:berth_bollard[facing=south]"));
        // signs
        set(105, KF2 + 1, z1 - 1, id("pixelpirates:dock_sign[facing=north]"));
        set(x2 + 1, KF2 + 1, 66, id("pixelpirates:dock_sign[facing=east]"));
        set(103, KF2 + 1, z2 + 1, id("pixelpirates:dock_sign[facing=south]"));
        lamppost(100, KG, 57); lamppost(118, KG, 57);
        eastBerths();
    }

    private static void dkWindows() {
        int[][] sides = {{102, 58, 1, 0, 0, -1}, {102, 72, 1, 0, 0, 1}, {102, 58, 0, 1, -1, 0}, {116, 58, 0, 1, 1, 0}};
        String[] out = {"north", "south", "west", "east"};
        int blue = id("minecraft:light_blue_terracotta");
        for (int s = 0; s < 4; s++) {
            int[] sd = sides[s];
            boolean alongX = sd[2] == 1;
            int pane = id("minecraft:glass_pane[" + (alongX ? "east=true,west=true" : "north=true,south=true") + "]");
            int shutter = id("minecraft:birch_trapdoor[facing=" + out[s] + ",half=bottom,open=true]");
            for (int u = 2; u <= 12; u += 4) {
                int x = sd[0] + sd[2] * u, z = sd[1] + sd[3] * u;
                if (getRaw(x, KG + 2, z) == STONE_BRICKS && getRaw(x - sd[4], KG + 2, z - sd[5]) == AIR && !isDoorCell(x, KG + 1, z)) {
                    set(x, KG + 2, z, pane); set(x, KG + 3, z, pane);
                }
                if (getRaw(x, KF2 + 2, z) != blue || getRaw(x - sd[4], KF2 + 2, z - sd[5]) != AIR) continue;
                set(x, KF2 + 2, z, pane); set(x, KF2 + 3, z, pane);
                if (s == 1) { set(x - 1, KF2 + 2, z, pane); set(x - 1, KF2 + 3, z, pane); set(x + 1, KF2 + 2, z, pane); set(x + 1, KF2 + 3, z, pane); continue; }   // the watch room's wide windows
                for (int k : new int[]{-1, 1}) {
                    int sx = x + sd[4] + (alongX ? k : 0), sz = z + sd[5] + (alongX ? 0 : k);
                    if (getRaw(sx, KF2 + 2, sz) == AIR) { set(sx, KF2 + 2, sz, shutter); set(sx, KF2 + 3, sz, shutter); }
                }
            }
        }
    }

    /** Red hipped roof (nether brick) with a glazed lantern cupola and a vane. */
    private static void dkRoof() {
        final int ox1 = 101, ox2 = 117, oz1 = 57, oz2 = 73;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        String[] dn = {"east", "west", "south", "north"};
        for (int x = ox1; x <= ox2; x++)
            for (int z = oz1; z <= oz2; z++) {
                int h = Math.min(Math.min(x - ox1, ox2 - x), Math.min(z - oz1, oz2 - z));
                String up = null;
                for (int d = 0; d < 4 && up == null; d++) {
                    int ax = x + dirs[d][0], az = z + dirs[d][1];
                    if (ax < ox1 || ax > ox2 || az < oz1 || az > oz2) continue;
                    if (Math.min(Math.min(ax - ox1, ox2 - ax), Math.min(az - oz1, oz2 - az)) == h + 1) up = dn[d];
                }
                set(x, KROOF + h, z, up != null ? stairs("red_nether_brick", up) : id("minecraft:red_nether_bricks"));
            }
        // the lantern cupola on the peak (109,65)
        for (int x = 108; x <= 110; x++) for (int z = 64; z <= 66; z++) {
            boolean corner = (x == 108 || x == 110) && (z == 64 || z == 66);
            for (int y = 84; y <= 86; y++) set(x, y, z, corner ? id("minecraft:stripped_birch_log[axis=y]") : (x == 109 && z == 65) ? AIR : id("minecraft:glass"));
            set(x, 87, z, id("minecraft:red_nether_brick_slab"));
        }
        set(109, 84, 65, id("minecraft:sea_lantern"));
        set(109, 88, 65, id("minecraft:lightning_rod"));
    }

    /** The boardwalk down the shore and the East Berths jetty, with berth bollards, a dues kiosk, lamps and a crane. */
    private static void eastBerths() {
        int stringer = id("minecraft:stripped_spruce_log[axis=z]"), stringerX = id("minecraft:stripped_spruce_log[axis=x]");
        // boardwalk x108..111, z76..95
        for (int z = 76; z <= 95; z++)
            for (int x = 108; x <= 111; x++) {
                set(x, KG, z, x == 108 || x == 111 ? stringer : (z % 6 == 0 ? DARK_OAK : SPRUCE));
                fill(x, KG + 1, z, x, KG + 8, z, AIR);
                if ((x == 108 || x == 111) && z % 4 == 0 && SpawnIslandTerrain.islandSurfaceY(x, z) < 64.5) fill(x, 50, z, x, KG - 1, z, SPRUCE_LOG_Y);
                if ((x == 108 || x == 111)) set(x, KG + 1, z, z % 4 == 0 ? SPRUCE_FENCE : railZ());
            }
        for (int z = 80; z <= 95; z += 8) { fill(108, KG + 1, z, 108, KG + 4, z, SPRUCE_FENCE); set(108, KG + 5, z, LANTERN); }
        // the jetty x98..132, z96..99
        for (int x = 98; x <= 132; x++)
            for (int z = 96; z <= 99; z++) {
                set(x, KG, z, z == 96 || z == 99 ? stringerX : ((x % 6 == 0) ? DARK_OAK : SPRUCE));
                fill(x, KG + 1, z, x, KG + 8, z, AIR);
            }
        for (int x = 98; x <= 132; x += 4) for (int z : new int[]{96, 99}) fill(x, 50, z, x, KG - 1, z, SPRUCE_LOG_Y);
        for (int x = 98; x <= 132; x += 4) for (int z = 97; z <= 98; z++) set(x, KG - 1, z, SPRUCE_LOG_Z);
        for (int x = 98; x <= 132; x++) {
            if (x < 108 || x > 111) set(x, KG + 1, 96, x % 4 == 2 ? SPRUCE_FENCE : railX());
            if (x % 6 == 4) set(x, KG + 1, 99, id("pixelpirates:berth_bollard[facing=south]"));
            else if (x % 6 == 1) set(x, KG + 1, 99, id("pixelpirates:rope_coil[facing=south]"));
        }
        for (int z = 96; z <= 99; z++) { set(98, KG + 1, z, z == 96 ? SPRUCE_FENCE : railZ()); set(132, KG + 1, z, z == 96 ? SPRUCE_FENCE : railZ()); }
        for (int x = 102; x <= 130; x += 12) { if (x >= 108 && x <= 111) continue; fill(x, KG + 1, 96, x, KG + 4, 96, SPRUCE_FENCE); set(x, KG + 5, 96, LANTERN); }
        for (int x = 100; x <= 130; x += 10) set(x, 61, 101, WATER_LIGHT);
        // the dues kiosk beside the boardwalk foot (x112..115, z92..95), served across the boardwalk's east rail
        for (int x = 112; x <= 115; x++) for (int z = 92; z <= 95; z++) {
            set(x, KG, z, SPRUCE);
            fill(x, KG + 1, z, x, KG + 6, z, AIR);
            if ((x == 112 || x == 115) && (z == 92 || z == 95)) { fill(x, 50, z, x, KG - 1, z, SPRUCE_LOG_Y); fill(x, KG + 1, z, x, KG + 3, z, white()); }
            set(x, KG + 4, z, id("minecraft:red_nether_brick_slab"));
        }
        for (int z = 93; z <= 94; z++) { set(115, KG + 1, z, SPRUCE); set(115, KG + 2, z, id("minecraft:glass_pane[north=true,south=true]")); set(111, KG + 1, z, AIR); }
        for (int x = 113; x <= 114; x++) { set(x, KG + 1, 92, SPRUCE); set(x, KG + 2, 92, id("minecraft:glass_pane[east=true,west=true]")); set(x, KG + 1, 95, SPRUCE); }
        set(112, KG + 1, 93, id("pixelpirates:dues_ledger[facing=west]"));
        set(112, KG + 1, 94, slabTop("spruce")); set(112, KG + 2, 94, id("minecraft:bell[attachment=floor,facing=west]"));
        set(114, KG + 1, 93, id("pixelpirates:captains_chair[facing=west]"));
        set(115, KG + 3, 93, id("pixelpirates:dues_board[facing=west]"));
        set(113, KG + 3, 94, LANTERN_HANGING);
        // supplies at the east end + a crane
        crane(129, KG, 97);
        set(126, KG + 1, 97, barrel("up")); set(125, KG + 1, 97, barrel("up")); set(126, KG + 2, 97, barrel("up"));
        set(124, KG + 1, 98, id("pixelpirates:cargo_crate[facing=south]")); set(123, KG + 1, 98, id("pixelpirates:cargo_crate[facing=east]"));
        set(100, KG + 1, 97, id("pixelpirates:rope_coil[facing=south]")); set(101, KG + 1, 97, BARREL_UP);
    }
    private static int white() { return id("minecraft:stripped_birch_log[axis=y]"); }


    // ------------------------------------------------------------------
    // Upper terrace
    // ------------------------------------------------------------------

    // ================================================================ THE SAILORS' CHAPEL (#16, rebuilt 2026-10-01)
    /*
     * The Chapel of the Safe Return, x -33..-17, z -80..-58 (upper terrace, floor y70) + its apse (north) and bell tower
     * (south): white stone (polished diorite) between stone-brick buttresses, tall stained-glass lancets, a steep polished
     * blackstone roof. Inside: a nave of columned arcades, PEWS (homestead CHAPEL_PEW, joined into benches), a red aisle,
     * the raised CHANCEL (altar + altar cross + candelabras, communion rail, pulpit, hymn boards), the APSE under a dome
     * with a wall cross and votive candles, the ORGAN in the east aisle (pipes + a playable console), the font by the door,
     * memorial plaques to those lost at sea, a VOTIVE SHIP hung over the nave, wagon-wheel chandeliers. The TOWER: entrance
     * under a ROSE WINDOW, a belfry of three bells rung by the BELL ROPE (it tolls every morning - ChapelBells), a spire with
     * a gilt cross. Outside: THE CHURCHYARD (stone wall, a roofed lychgate on the street, headstones, a sailors' memorial).
     */
    private static final int PG = 70;

    private static void chapel() {
        final int x1 = -33, x2 = -17, z1 = -80, z2 = -58, top = 80;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(-40, PG + 1, -88, -12, 110, -49, AIR);
        fill(x1, PG - 2, z1, x2, PG - 1, z2, COBBLE);
        int white = id("minecraft:polished_diorite");
        // floor: chequered stone; the chancel raised a step (z-79..-74)
        for (int x = x1 + 1; x < x2; x++)
            for (int z = z1 + 1; z < z2; z++) {
                set(x, PG, z, ((x + z) & 1) == 0 ? POLISHED_ANDESITE : white);
                if (z <= -74 && x >= -28 && x <= -22) set(x, PG + 1, z, z == -74 ? stairs("polished_andesite", "south") : POLISHED_ANDESITE);
            }
        // walls: stone plinth, white ashlar, stone quoins
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) continue;
                for (int y = PG + 1; y <= top; y++)
                    set(x, y, z, y == PG + 1 ? STONE_BRICKS : (ex && ez) || y == top ? CHISELED_STONE_BRICKS : white);
            }
        // buttresses + lancet windows along both sides
        for (int z = -76; z <= -60; z += 4)
            for (int bx : new int[]{x1 - 1, x2 + 1}) {
                fill(bx, PG + 1, z, bx, PG + 6, z, STONE_BRICKS);
                set(bx, PG + 7, z, stairs("stone_brick", bx < x1 ? "east" : "west"));
                fill(bx + (bx < x1 ? -1 : 1), PG + 1, z, bx + (bx < x1 ? -1 : 1), PG + 3, z, STONE_BRICKS);
                set(bx + (bx < x1 ? -1 : 1), PG + 4, z, stairs("stone_brick", bx < x1 ? "east" : "west"));
            }
        String[][] glass = {{"blue", "light_blue", "light_blue", "white", "yellow", "yellow"}, {"red", "orange", "yellow", "white", "light_blue", "blue"}};
        int w = 0;
        for (int z = -78; z <= -62; z += 4, w++)
            for (int wx : new int[]{x1, x2}) {
                String[] g = glass[w % 2];
                for (int k = 0; k < 6; k++) set(wx, PG + 3 + k, z, id("minecraft:" + g[k] + "_stained_glass_pane[north=true,south=true]"));
                set(wx, PG + 9, z, CHISELED_STONE_BRICKS);
            }
        // the arcades: diorite columns with capitals, round arches between them, tie beams above
        for (int z = -76; z <= -60; z += 4)
            for (int cx : new int[]{-29, -21}) {
                fill(cx, PG + 1, z, cx, PG + 7, z, id("minecraft:quartz_pillar[axis=y]"));
                set(cx, PG + 8, z, CHISELED_STONE_BRICKS);
            }
        for (int z = -79; z <= -59; z++)
            for (int cx : new int[]{-29, -21}) {
                int m = Math.floorMod(z + 76, 4);
                if (m == 0) continue;
                set(cx, PG + 8, z, m == 2 ? STONE_BRICKS : stairs("stone_brick", m == 1 ? "north" : "south") /* half=bottom forms the arch shoulders */);
                if (m == 1) set(cx, PG + 8, z, id("minecraft:stone_brick_stairs[facing=south,half=top]"));
                if (m == 3) set(cx, PG + 8, z, id("minecraft:stone_brick_stairs[facing=north,half=top]"));
            }
        for (int z = -76; z <= -60; z += 8) for (int x = x1 + 1; x < x2; x++) set(x, top + 1, z, DARK_OAK_LOG_X);
        // the chancel arch into the apse + the apse (half-dome, ring of windows)
        for (int x = -28; x <= -22; x++) for (int y = PG + 1; y <= PG + 8; y++) set(x, y, z1, AIR);
        set(-28, PG + 8, z1, id("minecraft:stone_brick_stairs[facing=east,half=top]")); set(-22, PG + 8, z1, id("minecraft:stone_brick_stairs[facing=west,half=top]"));
        for (int x = -27; x <= -23; x++) set(x, PG + 9, z1, CHISELED_STONE_BRICKS);
        for (int x = -31; x <= -19; x++)
            for (int z = -86; z <= -81; z++) {
                double r = Math.sqrt((x + 25) * (x + 25) + (z + 80.5) * (z + 80.5));
                if (r > 6.3) continue;
                set(x, PG - 1, z, COBBLE);
                set(x, PG, z, POLISHED_ANDESITE);
                set(x, PG + 1, z, POLISHED_ANDESITE);
                boolean wall = r > 5.3;
                for (int y = PG + 2; y <= top; y++) set(x, y, z, wall ? (y >= PG + 4 && y <= PG + 8 && (x + z) % 3 == 0 ? id("minecraft:" + (y > PG + 6 ? "yellow" : "light_blue") + "_stained_glass") : white) : AIR);
                int h = (int) Math.round(Math.sqrt(Math.max(0, 6.3 * 6.3 - r * r)));
                set(x, top + h / 2 + 1, z, id("minecraft:polished_blackstone_bricks"));
            }
        chRoof2();
        chTower();
        chYard();
    }

    /** Steep polished-blackstone gable over the nave (ridge along z at x-25), boarded underneath, gable crosses. */
    private static void chRoof2() {
        for (int x = -34; x <= -16; x++)
            for (int z = -81; z <= -57; z++) {
                int h = Math.min(x + 34, -16 - x), y = 81 + h;
                set(x, y, z, h == 9 ? id("minecraft:polished_blackstone_bricks") : stairs("polished_blackstone_brick", x < -25 ? "east" : "west"));
                if (h == 9) set(x, y + 1, z, id("minecraft:polished_blackstone_brick_slab"));
                if (h >= 1 && x >= -33 && x <= -17) set(x, y - 1, z, DARK_OAK);
                if (z == -80 || z == -58)
                    for (int yy = 81; yy < y - 1; yy++) set(x, yy, z, id("minecraft:polished_diorite"));
            }
        // a gilt cross on the north gable
        fill(-25, 91, -81, -25, 94, -81, id("minecraft:gold_block")); set(-26, 93, -81, id("minecraft:gold_block")); set(-24, 93, -81, id("minecraft:gold_block"));
    }

    /** The bell tower on the south front: entrance under a rose window, belfry with three bells, spire, gilt cross. */
    private static void chTower() {
        final int x1 = -28, x2 = -22, z1 = -58, z2 = -52, belfry = 92;
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                set(x, PG, z, POLISHED_ANDESITE);
                for (int y = PG + 1; y <= 98; y++) {
                    if (!ex && !ez) { set(x, y, z, AIR); continue; }
                    boolean corner = ex && ez;
                    set(x, y, z, corner || y == belfry - 1 || y == 98 ? STONE_BRICKS : y == PG + 1 ? STONE_BRICKS : id("minecraft:polished_diorite"));
                }
            }
        // the doorway (three doors under an arch) and the way through into the nave
        for (int x = -26; x <= -24; x++) {
            set(x, PG + 1, z2, door("dark_oak", "south", false)); set(x, PG + 2, z2, door("dark_oak", "south", true));
            set(x, PG + 3, z2, x == -25 ? CHISELED_STONE_BRICKS : stairs("stone_brick", x < -25 ? "east" : "west"));
            fill(x, PG + 1, z1, x, PG + 3, z1, AIR);
            innReserve(x, PG + 1, z2 - 1); innReserve(x, PG + 2, z2 - 1); innReserve(x, PG + 1, z2 + 1);
        }
        // the rose window over the door
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++) {
                double r = Math.sqrt(dx * dx + dy * dy);
                if (r > 2.4) continue;
                String c = r < 0.5 ? "yellow" : r < 1.5 ? ((dx + dy) % 2 == 0 ? "red" : "blue") : ((dx * dy) % 2 == 0 ? "light_blue" : "white");
                set(-25 + dx, 82 + dy, z2, id("minecraft:" + c + "_stained_glass"));
            }
        for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) {
            double r = Math.sqrt(dx * dx + dy * dy);
            if (r > 2.4 && r <= 3.3) set(-25 + dx, 82 + dy, z2, CHISELED_STONE_BRICKS);
        }
        // belfry: openings on all four faces, a beam with three bells, the bell rope down the shaft
        for (int y = belfry; y <= belfry + 4; y++)
            for (int k = -1; k <= 1; k++) {
                set(-25 + k, y, z1, AIR); set(-25 + k, y, z2, AIR); set(x1, y, -55 + k, AIR); set(x2, y, -55 + k, AIR);
            }
        for (int x = -26; x <= -24; x++) { set(x, belfry + 2, z1 + 0, stairs("stone_brick", "south")); }
        for (int x = x1 + 1; x < x2; x++) set(x, belfry + 4, -55, DARK_OAK_LOG_X);
        for (int x = -26; x <= -24; x++) set(x, belfry + 3, -55, id("minecraft:bell[attachment=ceiling,facing=north]"));
        set(-27, PG + 1, -55, id("pixelpirates:bell_rope[facing=east]"));
        for (int y = PG + 2; y <= belfry + 2; y++) set(-27, y, -55, CHAIN);
        set(-27, belfry + 3, -55, DARK_OAK_LOG_Y);
        for (int x = x1 + 1; x < x2; x++) for (int z = z1 + 1; z < z2; z++) if (!(x == -27 && z == -55)) set(x, belfry - 1, z, x == -25 && z == -55 ? AIR : DARK_OAK);
        // the spire: pyramid from 7x7 to a point, a gilt cross
        for (int k = 0; k <= 4; k++) {
            int a1 = x1 - 1 + k, a2 = x2 + 1 - k, b1 = z1 - 1 + k, b2 = z2 + 1 - k, y = 99 + k * 2;
            for (int yy = y; yy <= y + 1; yy++)
                for (int x = a1; x <= a2; x++) for (int z = b1; z <= b2; z++) {
                    if (k == 4) { set(x, yy, z, id("minecraft:polished_blackstone_bricks")); continue; }
                    if (x == a1) set(x, yy, z, stairs("polished_blackstone_brick", "east"));
                    else if (x == a2) set(x, yy, z, stairs("polished_blackstone_brick", "west"));
                    else if (z == b1) set(x, yy, z, stairs("polished_blackstone_brick", "south"));
                    else if (z == b2) set(x, yy, z, stairs("polished_blackstone_brick", "north"));
                    else if (yy == y) set(x, yy, z, id("minecraft:polished_blackstone_bricks"));
                }
        }
        fill(-25, 109, -55, -25, 113, -55, id("minecraft:gold_block"));
        set(-26, 112, -55, id("minecraft:gold_block")); set(-24, 112, -55, id("minecraft:gold_block"));
        set(-25, 114, -55, id("minecraft:lightning_rod"));
    }

    /** The churchyard: a low wall, the lychgate on the street, headstones with flowers, a sailors' memorial, cypresses. */
    private static void chYard() {
        for (int x = -40; x <= -12; x++)
            for (int z = -88; z <= -49; z++) {
                if (x >= -34 && x <= -16 && z >= -86 && z <= -52) continue;
                set(x, PG, z, GRASS);
            }
        for (int x = -26; x <= -24; x++) for (int z = -51; z <= -49; z++) set(x, PG, z, (x + z) % 2 == 0 ? GRAVEL : POLISHED_ANDESITE);
        int wall = id("minecraft:stone_brick_wall"), mossy = id("minecraft:mossy_stone_brick_wall");
        for (int x = -40; x <= -12; x++) { set(x, PG + 1, -88, (x % 3 == 0) ? mossy : wall); if (x < -27 || x > -23) set(x, PG + 1, -49, (x % 3 == 0) ? mossy : wall); }
        for (int z = -88; z <= -49; z++) { set(-40, PG + 1, z, (z % 3 == 0) ? mossy : wall); set(-12, PG + 1, z, (z % 3 == 0) ? mossy : wall); }
        // the lychgate: oak posts, a little slate roof over the path
        for (int x : new int[]{-27, -23}) for (int z : new int[]{-50, -48}) fill(x, PG + 1, z, x, PG + 3, z, DARK_OAK_LOG_Y);
        for (int x = -28; x <= -22; x++) {
            set(x, PG + 4, -51, id("minecraft:polished_blackstone_brick_stairs[facing=south]"));
            set(x, PG + 4, -47, id("minecraft:polished_blackstone_brick_stairs[facing=north]"));
            set(x, PG + 4, -50, DARK_OAK); set(x, PG + 4, -48, DARK_OAK);
            set(x, PG + 5, -50, id("minecraft:polished_blackstone_brick_stairs[facing=south]"));
            set(x, PG + 5, -48, id("minecraft:polished_blackstone_brick_stairs[facing=north]"));
            set(x, PG + 5, -49, id("minecraft:polished_blackstone_bricks"));
        }
        set(-25, PG + 3, -49, LANTERN_HANGING);
        for (int z = -50; z <= -48; z += 2) { set(-26, PG + 1, z, stairs("spruce", "west")); }
        // headstones in rows, each with a little grave and a few flowers
        String[] flowers = {"minecraft:poppy", "minecraft:cornflower", "minecraft:dandelion", "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:lily_of_the_valley"};
        String[] stones = {"minecraft:stone_brick_wall", "minecraft:andesite_wall", "minecraft:mossy_stone_brick_wall", "minecraft:cobblestone_wall"};
        Random gr = new Random(616L);
        for (int[] yard : new int[][]{{-39, -37}, {-13, -13}})
            for (int z = -86; z <= -54; z += 3) {
                for (int x = yard[0]; x <= yard[1]; x += 2) {
                    if (gr.nextInt(5) == 0) continue;
                    set(x, PG + 1, z, id(stones[gr.nextInt(stones.length)]));
                    if (gr.nextBoolean()) set(x, PG + 2, z, id("minecraft:stone_brick_slab"));
                    set(x, PG, z + 1, id("minecraft:coarse_dirt"));
                    if (gr.nextBoolean()) set(x, PG + 1, z + 1, id(flowers[gr.nextInt(flowers.length)]));
                }
            }
        // the sailors' memorial: an anchor on a stepped plinth, plaques, candles, wreath flowers
        set(-37, PG + 1, -51, STONE_BRICKS); set(-36, PG + 1, -51, STONE_BRICKS); set(-38, PG + 1, -51, STONE_BRICKS);
        set(-37, PG + 2, -51, CHISELED_STONE_BRICKS); set(-37, PG + 3, -51, ANCHOR_BLOCK);
        set(-36, PG + 2, -51, id("pixelpirates:candelabra[facing=south]")); set(-38, PG + 2, -51, id("pixelpirates:votive_rack[facing=south]"));
        for (int x = -39; x <= -35; x++) if (x != -37) set(x, PG + 1, -50, id(flowers[Math.floorMod(x, flowers.length)]));
        // cypresses at the corners
        for (int[] c : new int[][]{{-38, -86}, {-13, -86}, {-38, -60}, {-13, -62}}) {
            fill(c[0], PG + 1, c[1], c[0], PG + 7, c[1], id("minecraft:spruce_log[axis=y]"));
            for (int y = PG + 3; y <= PG + 9; y++) for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) if (y <= PG + 8 || d[0] == 0) set(c[0] + d[0], y, c[1] + d[1], id("minecraft:spruce_leaves[persistent=true]"));
            set(c[0], PG + 8, c[1], id("minecraft:spruce_leaves[persistent=true]")); set(c[0], PG + 9, c[1], id("minecraft:spruce_leaves[persistent=true]"));
        }
        lamppost(-29, PG, -50); lamppost(-21, PG, -50);
    }


    // ================================================================ THE GOVERNOR'S RESIDENCE (#17, rebuilt 2026-10-01)
    /*
     * The port governor's mansion, x 18..42, z -79..-61 (upper terrace, floor y70; first floor y76; flat ceiling y82):
     * white quartz-brick walls on a smooth-stone base, quartz pilasters and cornice, a giant-order PORTICO (six quartz columns,
     * a pediment with the port's anchor) on the south front, a weathered-copper HIPPED ROOF with a glazed CUPOLA and the
     * governor's flag. Side pavilions with balustraded roof terraces: the ORANGERY (west) and the KITCHEN + servants' hall
     * (east). Inside: the double-height ENTRANCE HALL (marble chequer, a grand stair to the landing gallery, crystal
     * chandelier, busts, the governor's portrait), the STATE DINING ROOM + the GUARDROOM (west), the BALLROOM (east);
     * upstairs the GOVERNOR'S SUITE (west, onto the west terrace), the STUDY + LIBRARY (east, onto the east terrace) and
     * the STRONGROOM (iron door + button). Grounds: a forecourt with the SEA GOD fountain, parterres with urns, a
     * wrought-iron fence on stone piers, the GATE between lion-crowned piers with two SENTRY BOXES (guard posts for later),
     * and the private garden behind (the GOVERNOR's statue, a reflecting pool, a copper-domed gazebo).
     */
    private static final int MG = 70, MF2 = 76, MCEIL = 82, MROOF = 83;

    private static void manor() {
        final int x1 = 18, x2 = 42, z1 = -79, z2 = -61;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(12, MG + 1, -88, 53, 110, -49, AIR);
        fill(x1, MG - 2, z1, x2, MG - 1, z2, COBBLE);
        int qb = id("minecraft:quartz_bricks"), sq = id("minecraft:smooth_quartz"), pil = id("minecraft:quartz_pillar[axis=y]");
        int marbleA = id("minecraft:quartz_block"), marbleB = id("minecraft:polished_blackstone");
        for (int x = x1 + 1; x < x2; x++)
            for (int z = z1 + 1; z < z2; z++) {
                boolean hall = x >= 26 && x <= 34;
                set(x, MG, z, hall ? (((x + z) & 1) == 0 ? marbleA : marbleB) : ((x + z) % 4 == 0 ? id("minecraft:stripped_dark_oak_wood") : DARK_OAK));
                boolean upper = !hall || z <= -72 || x <= 27 || x >= 33;
                if (upper) set(x, MF2, z, DARK_OAK);
                set(x, MCEIL, z, sq);
            }
        // the walls: base, quartz bricks, pilasters every 4, a band at the first floor, the cornice
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) continue;
                int u = ez ? x - x1 : z - z1;
                for (int y = MG + 1; y <= MCEIL; y++)
                    set(x, y, z, y == MG + 1 ? id("minecraft:smooth_stone") : y == MF2 || y == MCEIL ? sq : (ex && ez) || u % 4 == 0 ? pil : qb);
                set(x, MCEIL + 1, z, id("minecraft:diorite_wall"));                       // the parapet balustrade
            }
        // partitions: ground west (dining | guardroom), hall walls, upstairs wings
        fill(25, MG + 1, z1 + 1, 25, MF2 - 1, z2 - 1, qb); fill(35, MG + 1, z1 + 1, 35, MF2 - 1, z2 - 1, qb);
        fill(19, MG + 1, -70, 24, MF2 - 1, -70, qb);
        fill(25, MF2 + 1, z1 + 1, 25, MCEIL - 1, z2 - 1, qb); fill(35, MF2 + 1, z1 + 1, 35, MCEIL - 1, z2 - 1, qb);
        fill(36, MF2 + 1, -69, 41, MCEIL - 1, -69, qb);
        // the hall: grand stair (x29..31) to the landing (z-72..-78), side galleries, balustrades
        for (int k = 0; k <= 5; k++) for (int x = 29; x <= 31; x++) { set(x, MG + 1 + k, -66 - k, stairs("quartz", "north")); for (int y = MG + 1; y < MG + 1 + k; y++) set(x, y, -66 - k, qb); }
        for (int x = 27; x <= 33; x++) if (x < 29 || x > 31) set(x, MF2 + 1, -72, id("minecraft:diorite_wall"));
        for (int z = -71; z <= -62; z++) { set(27, MF2 + 1, z, z == -71 ? AIR : id("minecraft:diorite_wall")); set(33, MF2 + 1, z, z == -71 ? AIR : id("minecraft:diorite_wall")); }
        innReserve(30, MF2 + 1, -73);
        // doors
        for (int x = 29; x <= 31; x++) {
            set(x, MG + 1, z2, door("dark_oak", "south", false)); set(x, MG + 2, z2, door("dark_oak", "south", true));
            set(x, MG + 3, z2, id("minecraft:glass_pane[east=true,west=true]"));
            innReserve(x, MG + 1, z2 - 1); innReserve(x, MG + 2, z2 - 1); innReserve(x, MG + 1, z2 + 1);
        }
        innDoor(25, MG, -74, "dark_oak", false); innDoor(25, MG, -65, "dark_oak", false);
        innDoor(35, MG, -67, "dark_oak", false); innDoor(35, MG, -68, "dark_oak", false);
        innDoor(25, MF2, -66, "dark_oak", false); innDoor(35, MF2, -74, "dark_oak", false);
        set(35, MF2 + 1, -66, id("minecraft:iron_door[facing=east,half=lower]")); set(35, MF2 + 2, -66, id("minecraft:iron_door[facing=east,half=upper]"));
        set(34, MF2 + 2, -65, id("minecraft:stone_button[face=wall,facing=west]"));
        innReserve(34, MF2 + 1, -66); innReserve(36, MF2 + 1, -66);
        innDoor(x1, MG, -74, "dark_oak", false); innDoor(x2, MG, -70, "dark_oak", false);
        innDoor(x1, MF2, -70, "dark_oak", false); innDoor(x2, MF2, -73, "dark_oak", false);
        mnWindows();
        mnRoof();
        mnPortico();
        mnPavilions();
        mnGrounds();
    }

    private static void mnWindows() {
        int[][] sides = {{18, -79, 1, 0, 0, -1}, {18, -61, 1, 0, 0, 1}, {18, -79, 0, 1, -1, 0}, {42, -79, 0, 1, 1, 0}};
        String[] out = {"north", "south", "west", "east"};
        int qb = id("minecraft:quartz_bricks");
        for (int s = 0; s < 4; s++) {
            int[] sd = sides[s];
            boolean alongX = sd[2] == 1;
            int pane = id("minecraft:glass_pane[" + (alongX ? "east=true,west=true" : "north=true,south=true") + "]");
            int len = alongX ? 24 : 18;
            for (int u = 2; u <= len - 2; u += 4)
                for (int k = 0; k <= 1; k++) {
                    int x = sd[0] + sd[2] * (u + k), z = sd[1] + sd[3] * (u + k);
                    for (int[] w : new int[][]{{MG + 2, MG + 4}, {MF2 + 2, MF2 + 4}}) {
                        if (getRaw(x, w[0], z) != qb || getRaw(x - sd[4], w[0], z - sd[5]) != AIR) continue;
                        for (int y = w[0]; y <= w[1]; y++) set(x, y, z, pane);
                        set(x, w[1] + 1, z, id("minecraft:chiseled_quartz_block"));
                        if (!(s == 1 && x >= 22 && x <= 38)) set(x + sd[4], w[0] - 1, z + sd[5], id("minecraft:smooth_quartz_slab[type=top]"));
                    }
                }
        }
    }

    /** Weathered-copper hipped roof over the main block, a glazed cupola with a copper dome and the governor's flag. */
    private static void mnRoof() {
        final int ox1 = 17, ox2 = 43, oz1 = -80, oz2 = -60;
        String cu = "waxed_weathered_cut_copper";
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        String[] dn = {"east", "west", "south", "north"};
        for (int x = ox1; x <= ox2; x++)
            for (int z = oz1; z <= oz2; z++) {
                int h = Math.min(Math.min(x - ox1, ox2 - x), Math.min(z - oz1, oz2 - z));
                String up = null;
                for (int d = 0; d < 4 && up == null; d++) {
                    int ax = x + dirs[d][0], az = z + dirs[d][1];
                    if (ax < ox1 || ax > ox2 || az < oz1 || az > oz2) continue;
                    if (Math.min(Math.min(ax - ox1, ox2 - ax), Math.min(az - oz1, oz2 - az)) == h + 1) up = dn[d];
                }
                set(x, MROOF + h, z, up != null ? stairs(cu, up) : id("minecraft:" + cu));
            }
        // the cupola on the ridge
        for (int x = 28; x <= 32; x++) for (int z = -72; z <= -68; z++) {
            boolean ex = x == 28 || x == 32, ez = z == -72 || z == -68;
            if (!ex && !ez) { set(x, 92, z, id("minecraft:smooth_quartz")); for (int y = 93; y <= 96; y++) set(x, y, z, AIR); continue; }
            for (int y = 93; y <= 95; y++) set(x, y, z, ex && ez ? id("minecraft:quartz_pillar[axis=y]") : id("minecraft:glass_pane[" + (ez ? "east=true,west=true" : "north=true,south=true") + "]"));
            set(x, 92, z, id("minecraft:smooth_quartz")); set(x, 96, z, id("minecraft:smooth_quartz"));
        }
        for (int x = 29; x <= 31; x++) for (int z = -71; z <= -69; z++) set(x, 97, z, id("minecraft:" + cu));
        set(30, 98, -70, id("minecraft:" + cu));
        set(30, 94, -70, id("pixelpirates:crystal_chandelier[facing=north]"));
        fill(30, 99, -70, 30, 104, -70, id("minecraft:iron_bars"));
        for (int y = 102; y <= 104; y++) { set(31, y, -70, wool("blue")); set(32, y, -70, y == 103 ? wool("yellow") : wool("blue")); set(33, y, -70, wool("blue")); }
        set(30, 105, -70, id("minecraft:gold_block"));
    }

    /** The giant portico: a raised floor with steps, six quartz columns, entablature, the pediment and its anchor. */
    private static void mnPortico() {
        for (int x = 22; x <= 38; x++) for (int z = -60; z <= -56; z++) { set(x, MG - 1, z, COBBLE); set(x, MG, z, z == -56 ? id("minecraft:chiseled_quartz_block") : (((x + z) & 1) == 0 ? id("minecraft:smooth_quartz") : id("minecraft:polished_diorite"))); }
        for (int x : new int[]{23, 25, 27, 33, 35, 37}) {
            set(x, MG + 2, -57, id("minecraft:chiseled_quartz_block"));
            fill(x, MG + 3, -57, x, MCEIL - 2, -57, id("minecraft:quartz_pillar[axis=y]"));
            set(x, MCEIL - 1, -57, id("minecraft:chiseled_quartz_block"));
        }
        for (int x = 23; x <= 37; x++) for (int z = -60; z <= -57; z++) set(x, MCEIL, z, id("minecraft:smooth_quartz"));
        for (int x = 22; x <= 38; x++) set(x, MCEIL, -56, id("minecraft:smooth_quartz_slab[type=top]"));
        for (int x = 22; x <= 38; x++) {
            int h = Math.min(x - 22, 38 - x);
            for (int z = -61; z <= -56; z++) {
                set(x, MROOF + h, z, h == 8 ? id("minecraft:waxed_weathered_cut_copper") : stairs("waxed_weathered_cut_copper", x < 30 ? "east" : "west"));
                if (z == -56) for (int y = MROOF; y < MROOF + h; y++) set(x, y, -57, id("minecraft:quartz_bricks"));
            }
        }
        set(30, MROOF + 3, -57, id("minecraft:quartz_bricks")); set(30, MROOF + 2, -56, id("minecraft:smooth_quartz_slab[type=top]")); set(30, MROOF + 3, -56, id("pixelpirates:anchor_block[facing=south]")); set(29, MROOF + 3, -57, id("minecraft:gold_block")); set(31, MROOF + 3, -57, id("minecraft:gold_block"));
        for (int x : new int[]{24, 36}) set(x, MCEIL - 2, -58, LANTERN_HANGING);
        set(30, MCEIL - 1, -59, id("pixelpirates:crystal_chandelier[facing=north]"));
        for (int x : new int[]{24, 36}) { set(x, MG + 2, -59, id("pixelpirates:garden_urn[facing=south]")); }
    }

    /** The side pavilions: the ORANGERY (west, glazed) and the KITCHEN (east), roof terraces with balustrades and urns. */
    private static void mnPavilions() {
        for (int[] pv : new int[][]{{12, 17}, {43, 49}}) {
            boolean west = pv[0] == 12;
            for (int x = pv[0]; x <= pv[1]; x++)
                for (int z = -76; z <= -64; z++) {
                    boolean ex = x == pv[0] || x == pv[1], ez = z == -76 || z == -64;
                    set(x, MG - 1, z, COBBLE);
                    set(x, MG, z, west ? (((x + z) & 1) == 0 ? id("minecraft:quartz_block") : id("minecraft:polished_diorite")) : id("minecraft:smooth_stone"));
                    for (int y = MG + 1; y <= MF2; y++) {
                        int b;
                        if (!ex && !ez) b = y == MF2 ? id("minecraft:smooth_quartz") : AIR;
                        else if ((ex && ez) || y == MG + 1 || y == MF2 || (ez ? x - pv[0] : z + 76) % 3 == 0) b = west ? id("minecraft:quartz_pillar[axis=y]") : id("minecraft:quartz_bricks");
                        else b = west ? id("minecraft:glass_pane[" + (ez ? "east=true,west=true" : "north=true,south=true") + "]") : (y == MG + 3 ? id("minecraft:glass_pane[" + (ez ? "east=true,west=true" : "north=true,south=true") + "]") : id("minecraft:quartz_bricks"));
                        if (west && (ex && !ez || ez && !ex) && y == MG + 1) b = id("minecraft:quartz_pillar[axis=y]");
                        set(x, y, z, b);
                    }
                    set(x, MF2 + 1, z, (ex || ez) ? ((ex && ez) ? id("minecraft:chiseled_quartz_block") : id("minecraft:diorite_wall")) : id("minecraft:smooth_stone_slab"));
                }
            int ux = west ? pv[0] : pv[1];
            set(ux, MF2 + 2, -76, id("pixelpirates:garden_urn[facing=" + (west ? "west" : "east") + "]")); set(ux, MF2 + 2, -64, id("pixelpirates:garden_urn[facing=" + (west ? "west" : "east") + "]"));
            // the doorways through the pavilion wall (ground) and the parapet (the first-floor doors onto the terraces)
            int px = west ? 17 : 43, gz = west ? -74 : -70, tz = west ? -70 : -73;
            set(px, MG + 1, gz, AIR); set(px, MG + 2, gz, AIR);
            set(px, MF2 + 1, tz, id("minecraft:smooth_stone_slab"));
        }
    }

    /** The grounds: forecourt + sea god fountain, parterres, fence, gate with lions + sentry boxes, the private garden. */
    private static void mnGrounds() {
        int gravel = GRAVEL;
        for (int x = 12; x <= 53; x++)
            for (int z = -88; z <= -49; z++) {
                boolean house = x >= 12 && x <= 49 && z >= -80 && z <= -56;
                if (house) continue;
                set(x, MG, z, GRASS);
            }
        // the forecourt: a gravel sweep, the fountain, the drive to the gate
        for (int x = 20; x <= 40; x++) for (int z = -55; z <= -50; z++) set(x, MG, z, gravel);
        for (int x = 28; x <= 32; x++) for (int z = -50; z <= -49; z++) set(x, MG, z, gravel);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            double r = Math.sqrt(dx * dx + dz * dz);
            int x = 30 + dx, z = -52 + dz;
            if (r > 2.3) continue;
            if (r > 1.5) { set(x, MG, z, id("minecraft:smooth_quartz")); set(x, MG + 1, z, id("minecraft:smooth_quartz_slab")); }
            else { set(x, MG - 1, z, id("minecraft:smooth_quartz")); set(x, MG, z, WATER); }
        }
        set(30, MG, -52, id("minecraft:chiseled_quartz_block")); set(30, MG + 1, -52, id("minecraft:quartz_pillar[axis=y]"));
        set(30, MG + 2, -52, id("pixelpirates:sea_god_statue[facing=south]"));
        // parterres: box-hedge knots with flower beds and urns at the corners
        String[] fl = {"minecraft:red_tulip", "minecraft:white_tulip", "minecraft:allium", "minecraft:cornflower", "minecraft:lily_of_the_valley"};
        for (int[] pt : new int[][]{{13, 19}, {41, 47}})
            for (int x = pt[0]; x <= pt[1]; x++)
                for (int z = -58; z <= -50; z++) {
                    boolean edge = x == pt[0] || x == pt[1] || z == -58 || z == -50, cross = x == (pt[0] + pt[1]) / 2 || z == -54;
                    if (edge || cross) set(x, MG + 1, z, id("minecraft:azalea_leaves[persistent=true]"));
                    else set(x, MG + 1, z, id(fl[Math.floorMod(x * 3 + z, fl.length)]));
                }
        for (int[] u : new int[][]{{13, -58}, {19, -58}, {41, -58}, {47, -58}, {20, -55}, {40, -55}}) { set(u[0], MG + 1, u[1], id("minecraft:smooth_quartz")); set(u[0], MG + 2, u[1], id("pixelpirates:garden_urn[facing=south]")); }
        // the fence: stone piers every 4 with urns, iron railings between; the gate between lion-crowned piers
        for (int x = 12; x <= 48; x++) {
            if (x >= 28 && x <= 32) continue;
            boolean pier = (x - 12) % 4 == 0 || x == 27 || x == 33;
            if (pier) { set(x, MG + 1, -49, STONE_BRICKS); set(x, MG + 2, -49, id("minecraft:quartz_pillar[axis=y]")); set(x, MG + 3, -49, id("minecraft:chiseled_quartz_block")); }
            else { set(x, MG + 1, -49, STONE_BRICKS); set(x, MG + 2, -49, id("minecraft:iron_bars[east=true,west=true]")); set(x, MG + 3, -49, id("minecraft:iron_bars[east=true,west=true]")); }
        }
        for (int z = -88; z <= -50; z++) for (int x : new int[]{12, 53}) {
            if (x == 12 && z >= -76 && z <= -64) continue;                       // the orangery's own wall
            boolean pier = (z + 88) % 4 == 0;
            set(x, MG + 1, z, STONE_BRICKS);
            set(x, MG + 2, z, pier ? id("minecraft:quartz_pillar[axis=y]") : id("minecraft:iron_bars[north=true,south=true]"));
            if (!pier) set(x, MG + 3, z, id("minecraft:iron_bars[north=true,south=true]"));
        }
        for (int x : new int[]{27, 33}) {
            set(x, MG + 4, -49, id("minecraft:quartz_pillar[axis=y]")); set(x, MG + 5, -49, id("minecraft:chiseled_quartz_block"));
            set(x, MG + 6, -49, id("pixelpirates:lion_statue[facing=south]"));
        }
        for (int x = 28; x <= 32; x++) set(x, MG + 5, -49, id("minecraft:iron_bars[east=true,west=true]"));
        set(30, MG + 6, -49, id("minecraft:gold_block"));
        // the sentry boxes inside the gate (guard posts)
        for (int sx : new int[]{24, 35}) {
            for (int x = sx; x <= sx + 1; x++) for (int z = -52; z <= -51; z++) set(x, MG, z, id("minecraft:smooth_stone"));
            for (int y = MG + 1; y <= MG + 3; y++) {
                set(sx, y, -52, id("minecraft:quartz_bricks")); set(sx + 1, y, -52, id("minecraft:quartz_bricks"));
                set(sx == 24 ? sx : sx + 1, y, -51, id("minecraft:quartz_bricks"));
            }
            for (int x = sx; x <= sx + 1; x++) for (int z = -52; z <= -50; z++) set(x, MG + 4, z, id("minecraft:waxed_weathered_cut_copper_slab"));
            set(sx == 24 ? sx + 1 : sx, MG + 3, -51, LANTERN_HANGING);
            set(sx == 24 ? sx + 1 : sx, MG + 2, -51, wallBanner("blue", "south"));
        }
        // the private garden behind: the Governor's statue, a reflecting pool, a gazebo, roses + topiary
        for (int x = 14; x <= 46; x++) for (int z = -87; z <= -81; z++) if ((x + z) % 2 == 0 && (z == -87 || z == -81)) set(x, MG, z, gravel);
        for (int x = 24; x <= 36; x++) { set(x, MG - 1, -83, id("minecraft:smooth_quartz")); set(x, MG, -83, WATER); set(x, MG, -82, id("minecraft:smooth_quartz")); set(x, MG, -84, id("minecraft:smooth_quartz")); }
        set(30, MG + 1, -86, id("minecraft:smooth_quartz")); set(30, MG + 2, -86, id("minecraft:chiseled_quartz_block"));
        set(30, MG + 3, -86, id("pixelpirates:governor_statue[facing=south]"));
        for (int x : new int[]{27, 33}) { set(x, MG + 1, -86, id("minecraft:smooth_quartz")); set(x, MG + 2, -86, id("pixelpirates:marble_bust[facing=south]")); }
        // gazebo (west): eight quartz columns, a copper dome, a bench ring
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            int x = 18 + dx, z = -84 + dz;
            set(x, MG, z, id("minecraft:smooth_quartz"));
            boolean col = (Math.abs(dx) == 2 && Math.abs(dz) == 2) || (Math.abs(dx) == 2 && dz == 0) || (dx == 0 && Math.abs(dz) == 2);
            if (col) fill(x, MG + 1, z, x, MG + 3, z, id("minecraft:quartz_pillar[axis=y]"));
            set(x, MG + 4, z, Math.abs(dx) == 2 || Math.abs(dz) == 2 ? id("minecraft:waxed_weathered_cut_copper_slab") : id("minecraft:waxed_weathered_cut_copper"));
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) set(x, MG + 5, z, Math.abs(dx) + Math.abs(dz) == 0 ? id("minecraft:waxed_weathered_cut_copper") : id("minecraft:waxed_weathered_cut_copper_slab"));
        }
        set(18, MG + 6, -84, id("minecraft:lightning_rod"));
        set(18, MG + 3, -84, id("pixelpirates:crystal_chandelier[facing=north]"));
        for (int[] b : new int[][]{{17, -85, 0}, {19, -85, 0}, {17, -83, 1}, {19, -83, 1}}) set(b[0], MG + 1, b[1], stairs("dark_oak", b[2] == 0 ? "north" : "south"));
        // rose beds + topiary along the garden
        for (int x = 38; x <= 46; x++) { set(x, MG + 1, -86, id(x % 2 == 0 ? "minecraft:rose_bush[half=lower]" : "minecraft:peony[half=lower]")); set(x, MG + 2, -86, id(x % 2 == 0 ? "minecraft:rose_bush[half=upper]" : "minecraft:peony[half=upper]")); }
        for (int[] t : new int[][]{{14, -58 + 0}, {22, -82}, {38, -82}, {46, -82}}) {
            if (t[1] == -58) continue;
            set(t[0], MG + 1, t[1], id("minecraft:spruce_log[axis=y]"));
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 2; dy <= 3; dy++) if (Math.abs(dx) + Math.abs(dz) + (dy == 3 ? 1 : 0) <= 2) set(t[0] + dx, MG + dy, t[1] + dz, id("minecraft:azalea_leaves[persistent=true]"));
            set(t[0], MG + 4, t[1], id("minecraft:azalea_leaves[persistent=true]"));
        }
        for (int[] l : new int[][]{{16, -54}, {44, -54}, {22, -50}, {38, -50}}) {
            set(l[0], MG + 1, l[1], id("minecraft:smooth_quartz")); fill(l[0], MG + 2, l[1], l[0], MG + 3, l[1], id("minecraft:iron_bars")); set(l[0], MG + 4, l[1], LANTERN);
        }
    }


    /*
     * THE WAVEBREAK WATCH (rebuilt 2026-10-02): the town watch's garrison on the upper terrace, x-55..-5, z-41..-13
     * (floor y70; it used to be a plain 18x12 shed at x-28..-10). The WATCH HOUSE (x-43..-18, z-38..-27; F2 y75, roof deck
     * y80): grey ashlar on a deepslate plinth, machicolated parapet + roof walk (two salute guns). Ground: THE BRIG (west -
     * three barred cells with iron doors, buttons on the dividers, the gaoler's corner), the DUTY HALL (front doors
     * x-31/-30, the sergeant's counter, wanted posters, stair x-28), the ARMOURY (east, through the sergeant's pocket:
     * weapon racks, powder, shot, a cannon). Upstairs: the BARRACKS (bunks + lockers), the landing (roof ladder x-31 z-37)
     * and the CAPTAIN OF THE WATCH's office. The WATCHTOWER (x-17..-11, z-40..-34) is the compound gate (arched passage
     * N-S) and climbs by ladder (x-12 z-38) to a corbelled LOOKOUT at y91: alarm bell, signal brazier, the watch's flag; a
     * door at y81 onto the roof walk. The MESS (x-53..-43, slate roof, hearth + chimney west). The walled DRILL YARD
     * (gate east z-22..-20 to the avenue): archery butts, sparring ring, dummies, well, woodpile, kennel. Forecourt: a
     * working BOUNTY BOARD, the stocks + pillory, WATCH_SIGN, banners.
     */
    private static final int GHG = 70, GHF = 75, GHD = 80;

    /** Weathered ashlar: mostly stone bricks, the odd cracked or mossy one (deterministic). */
    private static int ghStone(int x, int y, int z) {
        int h = Math.floorMod(x * 73856093 ^ y * 19349663 ^ z * 83492791, 13);
        return h == 0 ? CRACKED_STONE_BRICKS : h == 1 ? MOSSY_STONE_BRICKS : STONE_BRICKS;
    }

    /** A window column in an outer wall: iron bars (slits) or glass, connected along the wall. */
    private static void ghWindow(int x, int z, int y1, int y2, boolean alongX, boolean bars) {
        String c = alongX ? "[east=true,west=true]" : "[north=true,south=true]";
        int b = id("minecraft:" + (bars ? "iron_bars" : "glass_pane") + c);
        for (int y = y1; y <= y2; y++) set(x, y, z, b);
    }

    private static void guardhouse() {
        final int x1 = -43, x2 = -18, z1 = -38, z2 = -27;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        FOOTPRINTS.add(new int[]{-17, -40, -11, -34});
        FOOTPRINTS.add(new int[]{-55, -37, -44, -28});
        fill(-55, GHG + 1, -41, -6, 110, -13, AIR);
        ghGround();
        fill(x1, GHG - 2, z1, x2, GHG - 1, z2, COBBLE);
        int pa = POLISHED_ANDESITE, db = id("minecraft:deepslate_bricks");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) {
                    boolean hall = x >= -32 && x <= -28, brig = x <= -34;
                    set(x, GHG, z, hall ? (((x + z) & 1) == 0 ? pa : STONE_BRICKS) : brig ? COBBLE : SPRUCE);
                    set(x, GHF, z, SPRUCE);
                    set(x, GHD, z, smoothTop());
                    continue;
                }
                set(x, GHG, z, STONE_BRICKS);
                for (int y = GHG + 1; y <= GHD; y++)
                    set(x, y, z, y == GHG + 1 ? db : (ex && ez) || y == GHF ? pa : ghStone(x, y, z));
            }
        // machicolations (north, south, east) and the parapet ring with merlons; the west side carries it on the wall
        for (int x = x1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean n = z == z1 - 1, s = z == z2 + 1, e = x == x2 + 1, w = x == x1;
                if (!n && !s && !e && !w) continue;
                if (n || s || e) set(x, GHD - 1, z, (n || s) && e ? STONE_BRICKS : stairsTop("stone_brick", n ? "south" : s ? "north" : "west"));
                set(x, GHD, z, STONE_BRICKS);
                set(x, GHD + 1, z, ghStone(x, GHD + 1, z));
                if (((x + z) & 1) == 0) set(x, GHD + 2, z, STONE_BRICKS);
            }
        // ground partitions: brig | hall | armoury; the cells along the north wall
        fill(-33, GHG + 1, -37, -33, GHF - 1, -28, STONE_BRICKS);
        fill(-27, GHG + 1, -37, -27, GHF - 1, -28, STONE_BRICKS);
        for (int x : new int[]{-39, -36}) fill(x, GHG + 1, -37, x, GHF - 1, -34, STONE_BRICKS);
        int bars = id("minecraft:iron_bars[east=true,west=true]");
        for (int x = -42; x <= -34; x++) {
            if (x == -39 || x == -36) continue;
            boolean door = x == -40 || x == -37 || x == -35;
            for (int y = GHG + 1; y <= GHF - 1; y++)
                set(x, y, -34, door && y <= GHG + 2 ? id("minecraft:iron_door[facing=south,half=" + (y == GHG + 1 ? "lower" : "upper") + ",hinge=" + (x == -40 ? "right" : "left") + "]") : bars);
            if (door) { innReserve(x, GHG + 1, -33); innReserve(x, GHG + 2, -33); innReserve(x, GHG + 1, -35); }
        }
        for (int x : new int[]{-39, -36}) set(x, GHG + 2, -33, id("minecraft:stone_button[face=wall,facing=south]"));
        innDoor(-33, GHG, -30, "spruce", false);
        innDoor(-27, GHG, -35, "spruce", false);
        // the upper floor: barracks | landing | office; the stair up the hall's east side (x-28, rising north)
        fill(-32, GHF + 1, -37, -32, GHD - 1, -28, STONE_BRICKS);
        fill(-26, GHF + 1, -37, -26, GHD - 1, -28, STONE_BRICKS);
        innDoor(-32, GHF, -30, "spruce", false);
        innDoor(-26, GHF, -35, "dark_oak", false);
        for (int k = 0; k <= 4; k++) {
            set(-28, GHG + 1 + k, -29 - k, stairs("stone_brick", "north"));
            for (int y = GHG + 1; y < GHG + 1 + k; y++) set(-28, y, -29 - k, STONE_BRICKS);
            if (k < 4) set(-28, GHF, -29 - k, AIR);
        }
        for (int z = -32; z <= -29; z++) { set(-29, GHF + 1, z, id("minecraft:spruce_fence[north=true,south=true]")); set(-27, GHF + 1, z, id("minecraft:spruce_fence[north=true,south=true]")); }
        set(-28, GHF + 1, -28, id("minecraft:spruce_fence[east=true,west=true]"));
        for (int y = GHF + 1; y <= GHD - 1; y++) set(-31, y, -37, LADDER_S);                 // roof ladder + hatch
        set(-31, GHD, -37, id("minecraft:spruce_trapdoor[facing=south,half=top,open=false]"));
        // doors: the front (double, north) and the yard door (double, south)
        for (int x : new int[]{-31, -30}) {
            String h = x == -31 ? "left" : "right";
            set(x, GHG + 1, z1, id("minecraft:dark_oak_door[facing=north,half=lower,hinge=" + h + "]"));
            set(x, GHG + 2, z1, id("minecraft:dark_oak_door[facing=north,half=upper,hinge=" + h + "]"));
            set(x, GHG + 3, z1, CHISELED_STONE_BRICKS);
            set(x, GHG + 1, z2, id("minecraft:dark_oak_door[facing=south,half=lower,hinge=" + (x == -31 ? "right" : "left") + "]"));
            set(x, GHG + 2, z2, id("minecraft:dark_oak_door[facing=south,half=upper,hinge=" + (x == -31 ? "right" : "left") + "]"));
            for (int z : new int[]{z1 - 1, z1 + 1, z2 - 1, z2 + 1}) { innReserve(x, GHG + 1, z); innReserve(x, GHG + 2, z); }
        }
        for (int y = GHG + 1; y <= GHG + 3; y++) { set(-32, y, z1, pa); set(-29, y, z1, pa); }
        fill(-32, GHG + 4, z1, -29, GHG + 4, z1, CHISELED_STONE_BRICKS);
        // windows: barred below, glass above
        for (int x : new int[]{-41, -37, -34, -25, -20}) ghWindow(x, z1, GHG + 2, GHG + 3, true, true);
        for (int x : new int[]{-41, -37, -24, -21}) ghWindow(x, z2, GHG + 2, GHG + 3, true, true);
        for (int x : new int[]{-41, -37, -29, -24, -20}) ghWindow(x, z1, GHF + 1, GHF + 3, true, false);
        for (int x : new int[]{-41, -37, -30, -24, -21}) ghWindow(x, z2, GHF + 1, GHF + 3, true, false);
        for (int z : new int[]{-31, -29}) { ghWindow(x2, z, GHG + 2, GHG + 3, false, true); ghWindow(x2, z, GHF + 1, GHF + 3, false, false); }
        // pilasters on both long fronts (they meet the corbel course)
        for (int x : new int[]{-35, -26})
            for (int z : new int[]{z1 - 1, z2 + 1}) {
                for (int y = GHG + 1; y <= GHD - 2; y++) set(x, y, z, y == GHG + 1 ? db : (y & 1) == 0 ? pa : ghStone(x, y, z));
                set(x, GHG, z, STONE_BRICKS);
            }
        // the hatch turret on the roof walk: a little slate-roofed hut over the ladder, door to the south
        for (int x = -33; x <= -29; x++)
            for (int z = -37; z <= -35; z++) {
                boolean edge = x == -33 || x == -29 || z == -37 || z == -35;
                for (int y = GHD + 1; y <= GHD + 3; y++) set(x, y, z, edge ? ((x == -33 || x == -29) && (z == -37 || z == -35) ? pa : ghStone(x, y, z)) : AIR);
                set(x, GHD + 4, z, id("minecraft:deepslate_tile_slab"));
            }
        for (int x = -34; x <= -28; x++) { set(x, GHD + 4, -38, stairs("deepslate_tile", "south")); set(x, GHD + 4, -34, stairs("deepslate_tile", "north")); }
        set(-31, GHD + 1, -35, door("spruce", "south", false)); set(-31, GHD + 2, -35, door("spruce", "south", true));
        innReserve(-31, GHD + 1, -34); innReserve(-31, GHD + 2, -34); innReserve(-31, GHD + 1, -36); innReserve(-31, GHD + 2, -36);
        set(-32, GHD + 2, -36, id("minecraft:lantern[hanging=true]"));
        ghTower();
        ghMess();
        ghYard();
        ghForecourt();
    }

    /** Ground of the whole plot: the forecourt paving, the drill yard's beaten earth, a lawn strip to the avenue. */
    private static void ghGround() {
        for (int x = -55; x <= -6; x++)
            for (int z = -41; z <= -13; z++) {
                int h = Math.floorMod(x * 31 + z * 17 + x * z, 7);
                int b;
                if (z <= -38 || (z == -41)) b = z == -41 ? STONE_BRICKS : (h == 0 ? ANDESITE : POLISHED_ANDESITE);
                else if (x <= -12 && x >= -52) b = h == 0 ? GRAVEL : h == 1 ? id("minecraft:dirt_path") : id("minecraft:coarse_dirt");
                else b = GRASS;
                set(x, GHG, z, b);
                set(x, GHG - 1, z, DIRT);
            }
    }

    /** The watchtower: the compound gate below, a ladder up five floors, the corbelled lookout with bell, brazier, flag. */
    private static void ghTower() {
        final int x1 = -17, x2 = -11, z1 = -40, z2 = -34, top = 90;
        fill(x1, GHG - 2, z1, x2, GHG - 1, z2, COBBLE);
        int pa = POLISHED_ANDESITE, db = id("minecraft:deepslate_bricks");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) {
                    set(x, GHG, z, STONE_BRICKS);
                    for (int y = GHG + 1; y <= top; y++) set(x, y, z, y == GHF || y == GHD || y == 86 ? SPRUCE : AIR);
                    continue;
                }
                set(x, GHG, z, STONE_BRICKS);
                for (int y = GHG + 1; y <= top; y++) set(x, y, z, y == GHG + 1 ? db : (ex && ez) || y == GHD || y == 86 ? pa : ghStone(x, y, z));
            }
        // a battered plinth round the three free faces (not across the arches)
        for (int x = x1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean n = z == z1 - 1, s = z == z2 + 1, e = x == x2 + 1;
                if (!(n || s || e) || (x >= -15 && x <= -13) || (e && (n || s))) continue;
                if (x == x1 && s) continue;
                set(x, GHG + 1, z, stairs("stone_brick", n ? "south" : s ? "north" : "west"));
            }
        // the gate passage: arches north + south, a portcullis raised in the wall
        for (int z : new int[]{z1, z2}) {
            for (int x = -15; x <= -13; x++) for (int y = GHG + 1; y <= GHG + 3; y++) set(x, y, z, AIR);
            set(-15, GHG + 3, z, stairsTop("stone_brick", "west"));
            set(-13, GHG + 3, z, stairsTop("stone_brick", "east"));
            for (int x = -15; x <= -13; x++) set(x, GHG + 4, z, x == -14 ? CHISELED_STONE_BRICKS : STONE_BRICKS);
        }
        for (int x = -15; x <= -13; x++) set(x, GHG + 4, -39, id("minecraft:iron_bars[east=true,west=true]"));
        // the ladder (x-12 z-38 on the east wall), holes in every floor, the hatch at the top
        for (int y = GHG + 1; y <= top; y++) set(-12, y, -38, id("minecraft:ladder[facing=west]"));
        set(-12, top + 1, -38, id("minecraft:spruce_trapdoor[facing=south,half=top,open=false]"));
        // arrow slits + the door onto the watch house roof walk
        for (int y : new int[]{83, 88}) {
            for (int dy = 0; dy <= 1; dy++) { set(-14, y + dy, z1, AIR); set(-14, y + dy, z2, AIR); set(x1, y + dy, -37, AIR); set(x2, y + dy, -36, AIR); }
        }
        for (int dy = 0; dy <= 1; dy++) { set(-14, 77 + dy, z2, AIR); set(x2, 77 + dy, -36, AIR); }
        set(x1, GHD + 1, -37, door("spruce", "west", false)); set(x1, GHD + 2, -37, door("spruce", "west", true));
        innReserve(-16, GHD + 1, -37); innReserve(-16, GHD + 2, -37); innReserve(-18, GHD + 1, -37); innReserve(-18, GHD + 2, -37);
        // corbels, the lookout platform, parapet + merlons
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                boolean w = x == x1 - 1, e = x == x2 + 1, n = z == z1 - 1, s = z == z2 + 1;
                boolean ring = w || e || n || s;
                if (ring) set(x, top, z, (w || e) && (n || s) ? STONE_BRICKS : stairsTop("stone_brick", n ? "south" : s ? "north" : w ? "east" : "west"));
                if (!(x == -12 && z == -38)) set(x, top + 1, z, ring ? STONE_BRICKS : smoothTop());
                if (ring) {
                    set(x, top + 2, z, ghStone(x, top + 2, z));
                    if (((x + z) & 1) == 0) set(x, top + 3, z, STONE_BRICKS);
                }
            }
        for (int[] c : new int[][]{{x1 - 1, z1 - 1}, {x2 + 1, z1 - 1}, {x1 - 1, z2 + 1}})
            set(c[0], top + 3, c[1], LANTERN);
        // the alarm bell under a little roofed frame, the signal brazier, the watch's flag
        int g = top + 2;
        for (int x : new int[]{-16, -12}) fill(x, g, -36, x, g + 2, -36, DARK_OAK_LOG_Y);
        fill(-16, g + 3, -36, -12, g + 3, -36, DARK_OAK_LOG_X);
        for (int x = -17; x <= -11; x++) { set(x, g + 4, -37, stairs("deepslate_tile", "south")); set(x, g + 4, -35, stairs("deepslate_tile", "north")); set(x, g + 4, -36, id("minecraft:deepslate_tile_slab")); }
        set(-14, g + 2, -36, id("minecraft:bell[attachment=ceiling,facing=north]"));
        set(-14, g, -39, id("minecraft:polished_blackstone"));
        set(-14, g + 1, -39, CAMPFIRE);
        for (int x : new int[]{-15, -13}) set(x, g, -39, id("minecraft:polished_blackstone_stairs[facing=" + (x < -14 ? "east" : "west") + "]"));
        flagMast(-10, top + 1, -33, "blue");
        set(-10, top + 2, -33, STONE_BRICKS);
        // banners on the street face
        set(-14, GHD - 1, z1 - 1, id("minecraft:blue_wall_banner[facing=north]"));
        set(-16, GHG + 3, z1 - 1, LANTERN_HANGING); set(-16, GHG + 4, z1 - 1, stairsTop("stone_brick", "south"));
        set(-12, GHG + 3, z1 - 1, LANTERN_HANGING); set(-12, GHG + 4, z1 - 1, stairsTop("stone_brick", "south"));
        set(-16, GHG + 3, z2 + 1, LANTERN_HANGING); set(-16, GHG + 4, z2 + 1, stairsTop("stone_brick", "north"));
        set(-12, GHG + 3, z2 + 1, LANTERN_HANGING); set(-12, GHG + 4, z2 + 1, stairsTop("stone_brick", "north"));
    }

    /** The mess hall: one storey against the watch house's west wall, slate gable roof, a hearth and stone chimney. */
    private static void ghMess() {
        final int x1 = -53, xe = -44, z1 = -37, z2 = -28;
        fill(x1, GHG - 2, z1, xe, GHG - 1, z2, COBBLE);
        int pa = POLISHED_ANDESITE, db = id("minecraft:deepslate_bricks");
        for (int x = x1; x <= xe; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || z == z1 || z == z2;
                set(x, GHG, z, edge ? STONE_BRICKS : (((x + z) & 3) == 0 ? id("minecraft:stripped_spruce_wood") : SPRUCE));
                if (!edge) continue;
                for (int y = GHG + 1; y <= GHG + 4; y++) set(x, y, z, y == GHG + 1 ? db : x == x1 && (z == z1 || z == z2) ? pa : ghStone(x, y, z));
            }
        fill(x1 + 1, GHG + 1, z1 + 1, xe, GHD, z2 - 1, AIR);
        gableRoofX(x1, -45, z1, z2, GHG + 5, "deepslate_tile", id("minecraft:deepslate_tiles"));
        gableEndsX(x1, z1 + 1, z2 - 1, GHG + 5, SPRUCE);
        fill(x1, GHG + 5, z1, x1, GHG + 5, z2, SPRUCE);
        for (int x = -51; x <= -45; x += 3) fill(x, GHG + 5, z1 + 1, x, GHG + 5, z2 - 1, SPRUCE_LOG_Z);   // tie beams
        for (int x : new int[]{-51, -47}) ghWindow(x, z1, GHG + 2, GHG + 3, true, false);
        for (int x : new int[]{-51, -45}) ghWindow(x, z2, GHG + 2, GHG + 3, true, false);
        innDoor(-43, GHG, -29, "spruce", false);
        innDoor(-48, GHG, z2, "spruce", true);
        // the hearth in the west gable, its chimney stack outside
        fill(-55, GHG + 1, -34, -54, 86, -32, STONE_BRICKS);
        fill(-54, GHG + 2, -33, -54, 86, -33, AIR);
        set(-54, GHG + 1, -33, CAMPFIRE);
        set(-53, GHG + 1, -33, AIR); set(-53, GHG + 2, -33, AIR);
        for (int z = -34; z <= -32; z++) set(-52, GHG + 3, z, stairsTop("stone_brick", "west"));
        set(-55, 87, -34, stairs("stone_brick", "south")); set(-55, 87, -32, stairs("stone_brick", "north"));
    }

    /** The drill yard: crenellated walls, a gate to the avenue, the archery butts, the ring, dummies and the service corner. */
    private static void ghYard() {
        int db = id("minecraft:deepslate_bricks"), crenel = id("minecraft:stone_brick_slab");
        java.util.List<int[]> wall = new java.util.ArrayList<>();
        for (int x = -53; x <= -11; x++) wall.add(new int[]{x, -13});
        for (int z = -27; z <= -14; z++) wall.add(new int[]{-53, z});
        for (int z = -33; z <= -14; z++) wall.add(new int[]{-11, z});
        for (int[] c : wall) {
            set(c[0], GHG, c[1], STONE_BRICKS);
            set(c[0], GHG + 1, c[1], db);
            set(c[0], GHG + 2, c[1], ghStone(c[0], GHG + 2, c[1]));
            set(c[0], GHG + 3, c[1], ghStone(c[0], GHG + 3, c[1]));
            set(c[0], GHG + 4, c[1], ((c[0] + c[1]) & 1) == 0 ? STONE_BRICKS : crenel);
        }
        // the gate (east wall, z-22..-20) and its path to the avenue
        for (int z = -22; z <= -20; z++) for (int y = GHG + 1; y <= GHG + 3; y++) set(-11, y, z, AIR);
        set(-11, GHG + 3, -22, stairsTop("stone_brick", "north")); set(-11, GHG + 3, -20, stairsTop("stone_brick", "south"));
        for (int z = -22; z <= -20; z++) set(-11, GHG + 4, z, z == -21 ? CHISELED_STONE_BRICKS : STONE_BRICKS);
        for (int z : new int[]{-23, -19}) { set(-11, GHG + 4, z, STONE_BRICKS); set(-11, GHG + 5, z, LANTERN); set(-10, GHG + 3, z, id("minecraft:blue_wall_banner[facing=east]")); }
        for (int x = -12; x <= -5; x++) for (int z = -22; z <= -20; z++) set(x, GHG, z, x >= -10 && z != -21 ? STONE_BRICKS : POLISHED_ANDESITE);
        // paths: the yard door -> east along z-25..-24 -> down to the gate; the mess door -> the well
        for (int z = -26; z <= -24; z++) for (int x = -32; x <= -29; x++) set(x, GHG, z, STONE_BRICKS);
        for (int x = -28; x <= -14; x++) for (int z = -25; z <= -24; z++) set(x, GHG, z, ((x + z) & 3) == 0 ? CRACKED_STONE_BRICKS : STONE_BRICKS);
        for (int x = -16; x <= -14; x++) for (int z = -23; z <= -20; z++) set(x, GHG, z, STONE_BRICKS);
        for (int z = -27; z <= -22; z++) set(-48, GHG, z, ((z & 1) == 0) ? STONE_BRICKS : POLISHED_ANDESITE);
        // ARCHERY: butts against the south wall, a shooting line
        for (int x : new int[]{-40, -36, -32}) {
            set(x, GHG + 1, -14, HAY); set(x, GHG + 2, -14, id("minecraft:target"));
            set(x - 1, GHG + 1, -14, HAY); set(x + 1, GHG + 1, -14, HAY);
        }
        for (int x = -42; x <= -31; x++) set(x, GHG, -24, POLISHED_ANDESITE);
        set(-43, GHG + 1, -25, barrel("up")); set(-43, GHG + 2, -25, id("minecraft:fletching_table")); set(-42, GHG + 1, -26, barrel("up"));
        // THE SPARRING RING: sand inside a rope (fence) ring, an opening to the path
        for (int x = -29; x <= -19; x++)
            for (int z = -23; z <= -15; z++) {
                double d = Math.hypot((x + 24) / 1.25, z + 19);
                if (d <= 3.4) set(x, GHG, z, SAND);
                else if (d <= 4.4 && !(z <= -22 && Math.abs(x + 24) <= 1)) set(x, GHG + 1, z, SPRUCE_FENCE);
            }
        // DUMMIES (fence, hay body, fence arms, pumpkin head)
        int[][] dummies = {{-15, -31, 0}, {-15, -28, 0}, {-16, -16, 1}, {-13, -16, 1}};
        for (int[] d : dummies) {
            set(d[0], GHG + 1, d[1], SPRUCE_FENCE); set(d[0], GHG + 2, d[1], HAY);
            set(d[0], GHG + 3, d[1], id("minecraft:carved_pumpkin[facing=" + (d[2] == 0 ? "west" : "north") + "]"));
            if (d[2] == 0) { set(d[0], GHG + 2, d[1] - 1, SPRUCE_FENCE); set(d[0], GHG + 2, d[1] + 1, SPRUCE_FENCE); }
            else { set(d[0] - 1, GHG + 2, d[1], SPRUCE_FENCE); set(d[0] + 1, GHG + 2, d[1], SPRUCE_FENCE); }
        }
        set(-12, GHG + 1, -27, CAULDRON_WATER); set(-12, GHG + 1, -26, CAULDRON_WATER);        // the horse trough
        set(-12, GHG + 1, -32, GRINDSTONE); set(-12, GHG + 1, -33, barrel("west"));
        // THE SERVICE CORNER: the well, the woodpile under a lean-to, the kennel, a chopping block, the washtub
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) { for (int y = GHG - 4; y <= GHG; y++) set(-48, y, -20, WATER); set(-48, GHG + 1, -20, AIR); continue; }
            set(-48 + dx, GHG + 1, -20 + dz, STONE_BRICKS);
        }
        for (int x : new int[]{-49, -47}) { set(x, GHG + 2, -20, SPRUCE_FENCE); set(x, GHG + 3, -20, SPRUCE_FENCE); }
        fill(-49, GHG + 4, -20, -47, GHG + 4, -20, SPRUCE_LOG_X);
        for (int x = -49; x <= -47; x++) { set(x, GHG + 5, -21, stairs("deepslate_tile", "south")); set(x, GHG + 5, -19, stairs("deepslate_tile", "north")); set(x, GHG + 5, -20, id("minecraft:deepslate_tile_slab")); }
        set(-48, GHG + 3, -20, CHAIN); set(-48, GHG + 2, -20, CHAIN);
        for (int z = -24; z <= -16; z++) {
            set(-52, GHG + 1, z, SPRUCE_LOG_Z); if (z % 3 != 0) set(-52, GHG + 2, z, SPRUCE_LOG_Z);
            set(-52, GHG + 3, z, slabTop("spruce")); set(-51, GHG + 3, z, slabTop("spruce"));
        }
        for (int z : new int[]{-24, -16}) { set(-51, GHG + 1, z, SPRUCE_FENCE); set(-51, GHG + 2, z, SPRUCE_FENCE); }
        set(-50, GHG + 1, -18, id("minecraft:stripped_spruce_log[axis=y]"));
        for (int x = -46; x <= -44; x++) for (int z = -16; z <= -14; z++) {
            boolean in = x == -45 && z == -15, door = x == -45 && z == -16;
            set(x, GHG + 1, z, in || door ? AIR : SPRUCE);
            set(x, GHG + 2, z, x == -45 ? SPRUCE : stairs("spruce", x == -46 ? "east" : "west"));
        }
        set(-45, GHG + 1, -15, id("minecraft:red_carpet"));
        set(-50, GHG + 1, -25, CAULDRON_WATER);
        // lamps
        lamppost(-43, GHG, -17); lamppost(-30, GHG, -16); lamppost(-17, GHG, -26);
        for (int x = -51; x <= -15; x += 12) set(x, GHG + 5, -13, LANTERN);
    }

    /** The forecourt on the street: banners and lanterns at the door, the WATCH sign, the bounty board, stocks + pillory. */
    private static void ghForecourt() {
        int z0 = -39;
        set(-33, GHG + 3, z0, id("pixelpirates:watch_sign[facing=north]"));
        for (int x : new int[]{-32, -29}) { set(x, GHG + 4, z0, stairsTop("stone_brick", "south")); set(x, GHG + 3, z0, LANTERN_HANGING); }
        for (int x : new int[]{-31, -30}) set(x, GHF + 3, z0, id("minecraft:blue_wall_banner[facing=north]"));
        for (int x : new int[]{-39, -22}) set(x, GHF + 3, z0, id("minecraft:blue_wall_banner[facing=north]"));
        // the BOUNTY BOARD (3 x 2, master bottom centre) against the armoury front
        for (int part = 0; part < 6; part++)
            set(-22 + (part % 3 - 1), GHG + 1 + part / 3, z0, id("pixelpirates:bounty_board[facing=north,part=" + part + "]"));
        // the PILLORY (post, a board with three holes, a cap) and the STOCKS (a board between posts, a bench behind)
        fill(-38, GHG + 1, -40, -38, GHG + 3, -40, SPRUCE_FENCE);
        for (int x : new int[]{-39, -37}) set(x, GHG + 3, -40, id("minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]"));
        fill(-39, GHG + 4, -40, -37, GHG + 4, -40, id("minecraft:spruce_slab"));
        set(-42, GHG + 1, -40, SPRUCE_FENCE); set(-40, GHG + 1, -40, SPRUCE_FENCE);
        set(-41, GHG + 1, -40, id("minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]"));
        set(-41, GHG + 1, z0, stairs("spruce", "south"));
        // lamps, benches and a horse post along the street
        lamppost(-52, GHG, -40); lamppost(-19, GHG, -40);
        for (int x = -50; x <= -48; x++) set(x, GHG + 1, -38, stairs("spruce", "south"));
        set(-46, GHG + 1, -38, barrel("north")); set(-45, GHG + 1, -38, barrel("up")); set(-46, GHG + 2, -38, id("minecraft:lantern"));
    }

    /*
     * THE WAVEBREAK FORGE (rebuilt 2026-10-02): the town smithy, x42..66 z-41..-12 on the upper terrace (floor y70), facing
     * the x=70 side avenue. (Until now it was a box at x54..76 z-44..-24 that sat across both the avenue and the upper
     * street - not building there is what reopens them.) FORGE HALL x47..65 z-38..-23: rough cobble + stone ground storey,
     * dark-oak frame with brick infill above, dark-oak gable roof (ridge y87) on tie beams, an OPEN ARCADE on the avenue
     * (posts every 4, braces). THE GREAT FORGE (north wall, x54..58): two working FORGE HEARTHs (55/57, z-36, kept lit),
     * BELLOWS each side, a lava crucible between, a brick hood and a chimney stack (x55..57 z-38..-36) to y93 that smokes;
     * two FORGE ANVILs at z-34. THE OPEN FORGE in the arcade (hearth 63,-25, bellows, anvil 63,-27) for passers-by.
     * PATTERN BOARDs (52,-37 / 60,-37), the quench trough, tool wall + weapon racks (west), benches (south), iron + coal
     * stock, the counter in the arcade. CHARCOAL SHED lean-to x42..46 west. SMITH'S YARD south (x42..66 z-21..-12): a trip
     * hammer, the cannon-casting pit with its gantry, a forged anchor, the scrap heap. SMITHY_SIGNs on the avenue + street.
     */
    private static final int SG = 70;

    private static int smWall(int x, int y, int z, int u) {
        if (y <= SG + 2) return Math.floorMod(x * 7 + y * 13 + z * 5, 5) == 0 ? MOSSY_COBBLE : COBBLE;
        if (y <= SG + 4) return ghStone(x, y, z);
        if (y == SG + 5 || y == SG + 8 || u % 4 == 0) return y == SG + 5 || y == SG + 8 ? id("minecraft:stripped_dark_oak_log[axis=" + "x]") : DARK_OAK_LOG_Y;
        return id("minecraft:bricks");
    }

    private static void smithy() {
        final int x1 = 47, x2 = 65, z1 = -38, z2 = -23, top = SG + 8;
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        FOOTPRINTS.add(new int[]{42, -37, 46, -24});
        fill(42, SG + 1, -41, 66, 110, -12, AIR);
        // ground: a cobbled apron on the street, the hall floor, the yard's beaten earth
        for (int x = 42; x <= 66; x++)
            for (int z = -41; z <= -12; z++) {
                int h = Math.floorMod(x * 31 + z * 17 + x * z, 7);
                int b = z <= -39 ? (h == 0 ? ANDESITE : COBBLE) : z >= -22 || x <= 46 ? (h == 0 ? GRAVEL : h == 1 ? id("minecraft:dirt_path") : id("minecraft:coarse_dirt")) : GRASS;
                set(x, SG, z, b);
                set(x, SG - 1, z, DIRT);
            }
        fill(x1, SG - 2, z1, x2, SG - 1, z2, COBBLE);
        int pbs = id("minecraft:polished_blackstone_bricks"), logX = id("minecraft:stripped_dark_oak_log[axis=x]"), logZ = id("minecraft:stripped_dark_oak_log[axis=z]");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) {
                    int h = Math.floorMod(x * 13 + z * 7, 6);
                    boolean nearFire = x >= 53 && x <= 59 && z <= -33;
                    set(x, SG, z, nearFire ? pbs : h == 0 ? ANDESITE : h == 1 ? GRAVEL : COBBLE);
                    continue;
                }
                set(x, SG, z, STONE_BRICKS);
                int u = ez ? x - x1 : z - z1;
                boolean arcade = x == x2 && !ez;
                for (int y = SG + 1; y <= top; y++) {
                    if (arcade && y <= SG + 4) { set(x, y, z, (z - z1) % 4 == 0 ? DARK_OAK_LOG_Y : AIR); continue; }
                    int b = (ex && ez) ? DARK_OAK_LOG_Y : smWall(x, y, z, u);
                    if (b == logX && ex) b = logZ;
                    set(x, y, z, b);
                }
            }
        // the arcade: the beam over the opening, braces at every post
        for (int z = z1 + 1; z < z2; z++) set(x2, SG + 5, z, logZ);
        for (int z = z1; z <= z2; z += 4)
            for (int s : new int[]{-1, 1})
                if (z + s > z1 && z + s < z2 && getRaw(x2, SG + 4, z + s) == AIR) set(x2, SG + 4, z + s, stairsTop("dark_oak", s < 0 ? "south" : "north"));
        // windows: tall panes in the frame on the street + yard sides, small ones in the west wall
        for (int x = x1 + 2; x <= x2 - 2; x += 4)
            for (int z : new int[]{z1, z2}) {
                if (x >= 54 && x <= 58 && z == z1) continue;                        // the forge's back wall
                for (int y = SG + 6; y <= SG + 7; y++) set(x, y, z, id("minecraft:glass_pane[east=true,west=true]"));
                if (z == z2 && x != 56) for (int y = SG + 2; y <= SG + 3; y++) set(x, y, z, id("minecraft:iron_bars[east=true,west=true]"));
            }
        for (int z = z1 + 3; z <= z2 - 3; z += 4) for (int y = SG + 6; y <= SG + 7; y++) set(x1, y, z, id("minecraft:glass_pane[north=true,south=true]"));
        // the yard door (south) and a side door to the shed (west)
        innDoor(56, SG, z2, "dark_oak", true);
        innDoor(x1, SG, -30, "spruce", false);
        // roof: gable along x over tie beams, brick gables
        gableRoofX(x1, x2, z1, z2, top + 1, "dark_oak", DARK_OAK);
        gableEndsX(x1, z1 + 1, z2 - 1, top + 1, id("minecraft:bricks"));
        gableEndsX(x2, z1 + 1, z2 - 1, top + 1, id("minecraft:bricks"));
        for (int x : new int[]{51, 59, 63}) fill(x, top + 1, z1 + 1, x, top + 1, z2 - 1, logZ);
        smGreatForge();
        smOpenForge();
        smShed();
        smYard();
        // the signs: on the avenue and on the street corner
        set(x2 + 1, SG + 6, -31, id("pixelpirates:smithy_sign[facing=east]"));
        set(63, SG + 6, z1 - 1, id("pixelpirates:smithy_sign[facing=north]"));
        lamppost(66, SG, -40); lamppost(44, SG, -40);
        // the old smithy stood at x54..76 z-44..-24 (over the avenue and the street): keep that inside this label's box so
        // `/ppisland restamp 19` in an older world also clears its walls off the avenue
        int[] box = LABELS.get(CUR);
        box[0] = Math.min(box[0], 54); box[1] = Math.min(box[1], -44); box[2] = Math.max(box[2], 76); box[3] = Math.max(box[3], -24);
    }

    /** The great forge on the north wall: two hearths + bellows, a lava crucible, a brick hood, the chimney stack. */
    private static void smGreatForge() {
        int bricks = id("minecraft:bricks"), bw = id("minecraft:brick_wall");
        fill(53, SG + 1, -37, 59, SG + 4, -37, bricks);                                     // the fireback
        set(55, SG + 1, -36, id("pixelpirates:forge_hearth[facing=south,fuel=8]"));
        set(57, SG + 1, -36, id("pixelpirates:forge_hearth[facing=south,fuel=8]"));
        set(56, SG + 1, -36, id("minecraft:lava_cauldron"));
        set(54, SG + 1, -36, id("pixelpirates:bellows[facing=east]"));
        set(58, SG + 1, -36, id("pixelpirates:bellows[facing=west]"));
        for (int x = 53; x <= 59; x++) {                                                   // the hood
            set(x, SG + 4, -35, stairsTop("brick", "south"));
            set(x, SG + 4, -36, bricks);
            set(x, SG + 5, -36, x == 53 || x == 59 ? stairs("brick", x == 53 ? "east" : "west") : bricks);
            set(x, SG + 5, -37, bricks);
        }
        for (int x = 54; x <= 58; x++) { set(x, SG + 6, -36, x == 54 || x == 58 ? stairs("brick", x == 54 ? "east" : "west") : bricks); set(x, SG + 6, -37, bricks); }
        for (int y = SG + 7; y <= 92; y++)                                                 // the stack, through the roof
            for (int x = 55; x <= 57; x++)
                for (int z = -38; z <= -36; z++) set(x, y, z, bricks);
        set(56, 92, -37, CAMPFIRE);                                                        // smoke from the top, the fire hidden in the ring
        for (int x = 55; x <= 57; x++) for (int z = -38; z <= -36; z++) if (x != 56 || z != -37) set(x, 93, z, bw);
        set(55, SG + 1, -34, id("pixelpirates:forge_anvil[facing=south]"));
        set(57, SG + 1, -34, id("pixelpirates:forge_anvil[facing=south]"));
        set(52, SG + 3, -37, id("pixelpirates:pattern_board[facing=south]"));
        set(60, SG + 3, -37, id("pixelpirates:pattern_board[facing=south]"));
        // the quench trough beside it
        for (int x = 61; x <= 63; x++) { set(x, SG + 1, -37, STONE_BRICKS); set(x, SG + 1, -36, x == 62 ? CAULDRON_WATER : id("minecraft:stone_brick_slab")); }
        set(61, SG + 2, -37, CAULDRON_WATER); set(63, SG + 2, -37, barrel("up"));
    }

    /** The open forge in the arcade, where anyone passing can work: hearth, bellows, anvil, its own little flue. */
    private static void smOpenForge() {
        int bricks = id("minecraft:bricks");
        set(63, SG + 1, -25, id("pixelpirates:forge_hearth[facing=west,fuel=8]"));
        set(63, SG + 1, -24, id("pixelpirates:bellows[facing=north]"));
        set(64, SG + 1, -25, bricks); set(64, SG + 2, -25, bricks);
        set(63, SG + 1, -27, id("pixelpirates:forge_anvil[facing=east]"));
        set(63, SG + 4, -25, stairsTop("brick", "west"));
        for (int y = SG + 3; y <= SG + 4; y++) set(64, y, -25, bricks);
        for (int y = SG + 5; y <= 90; y++) set(64, y, -24, bricks);
        set(64, SG + 4, -24, bricks);
        set(64, 91, -24, id("minecraft:brick_wall"));
        set(62, SG + 2, -24, id("pixelpirates:pattern_board[facing=north]"));
    }

    /** The charcoal shed: a lean-to on the hall's west wall, open to the west. */
    private static void smShed() {
        for (int z = -37; z <= -24; z++) {
            for (int x = 42; x <= 46; x++) set(x, SG + 4 + (x - 42) - (x == 46 ? 1 : 0), z, x == 46 ? slabTop("spruce") : stairs("spruce", "east"));
            if (z == -37 || z == -31 || z == -24) fill(42, SG + 1, z, 42, SG + 3, z, SPRUCE_LOG_Y);
        }
        for (int x = 43; x <= 46; x++) {                                                   // gable-less end walls up to the roof
            int roofY = SG + 4 + (x - 42) - (x == 46 ? 1 : 0);
            fill(x, SG + 1, -37, x, roofY - 1, -37, SPRUCE);
            fill(x, SG + 1, -24, x, roofY - 1, -24, SPRUCE);
        }
    }

    /** The smith's yard: trip hammer, cannon-casting pit + gantry, a forged anchor, the scrap heap, fence and gate. */
    private static void smYard() {
        for (int z = -21; z <= -12; z++) set(42, SG + 1, z, z == -17 ? id("minecraft:spruce_fence_gate[facing=west]") : SPRUCE_FENCE);
        for (int x = 43; x <= 46; x++) set(x, SG + 1, -22, SPRUCE_FENCE);
        // THE TRIP HAMMER: two uprights, the axle, the arm with an iron head over a block anvil
        for (int z : new int[]{-18, -16}) fill(49, SG + 1, z, 49, SG + 4, z, DARK_OAK_LOG_Y);
        set(49, SG + 4, -17, id("minecraft:dark_oak_log[axis=z]"));
        fill(46, SG + 4, -17, 48, SG + 4, -17, id("minecraft:dark_oak_log[axis=x]"));
        fill(50, SG + 4, -17, 52, SG + 4, -17, id("minecraft:dark_oak_log[axis=x]"));
        set(53, SG + 4, -17, id("minecraft:iron_block")); set(53, SG + 3, -17, id("minecraft:iron_block"));
        set(53, SG + 1, -17, id("minecraft:anvil[facing=north]")); set(46, SG + 3, -17, id("minecraft:chain")); set(46, SG + 2, -17, barrel("up"));
        // THE CASTING PIT: sand, two cannon barrels cast, a crucible, the gantry with chain and hook
        for (int x = 57; x <= 63; x++) for (int z = -19; z <= -14; z++) set(x, SG, z, SAND);
        set(58, SG + 1, -17, id("pixelpirates:display_cannon[facing=east]"));
        set(61, SG + 1, -15, id("pixelpirates:display_cannon[facing=west]"));
        set(62, SG + 1, -18, id("minecraft:lava_cauldron"));
        for (int z : new int[]{-19, -14}) fill(60, SG + 1, z, 60, SG + 5, z, DARK_OAK_LOG_Y);
        fill(60, SG + 6, -19, 60, SG + 6, -14, id("minecraft:dark_oak_log[axis=z]"));
        for (int y = SG + 3; y <= SG + 5; y++) set(60, y, -16, CHAIN);
        set(60, SG + 2, -16, id("minecraft:tripwire_hook[facing=north]"));
        // a forged anchor waiting for its ship, chain coiled; the scrap heap
        set(45, SG + 1, -14, ANCHOR_BLOCK); set(46, SG + 1, -14, id("minecraft:chain[axis=x]")); set(47, SG + 1, -14, id("minecraft:chain[axis=x]"));
        set(44, SG + 1, -19, id("minecraft:damaged_anvil[facing=east]")); set(45, SG + 1, -20, id("minecraft:iron_bars"));
        set(44, SG + 1, -20, id("minecraft:chipped_anvil[facing=north]")); set(45, SG + 1, -19, id("minecraft:cauldron"));
        set(54, SG + 1, -21, GRINDSTONE); set(55, SG + 1, -21, barrel("up")); set(57, SG + 1, -21, CAULDRON_WATER);
        lamppost(51, SG, -13); lamppost(64, SG, -12);
    }

    /*
     * THE WAVEBREAK GARDENS (rebuilt 2026-10-02): the town park on the upper terrace, x-89..-57 z-41..-12 (ground y70), on both
     * sides of the x=-70 side avenue - which now runs through it as a tree-lined PROMENADE (the old park's pond sat in the
     * road). North: a park railing on the street, the GARDEN GATE arch over the avenue with the GARDENS sign. WEST GARDEN:
     * the BOATING POND (3 deep - fishable) with an ISLAND GAZEBO, a FOOTBRIDGE from the promenade, a FISHING JETTY, lily
     * pads, reeds; FRUIT PALMS whose bananas can be picked (they regrow). EAST GARDEN: the BANDSTAND (raised, copper roof,
     * a working JUKEBOX, note blocks, music stands), the PLAY SHIP (a little climbable pirate ship), flower beds. South:
     * the OVERLOOK - benches facing the harbour over the terrace edge. A hedge closes the east side against the guardhouse.
     * Every palm here is drawn face-connected (gardenPalm, BUILD RULES #7).
     */
    private static final int PK = 70;

    private static boolean pkAvenue(int x) { return x >= -73 && x <= -67; }

    private static void park() {
        FOOTPRINTS.add(new int[]{-65, -30, -59, -24});                           // the bandstand
        for (int x = -89; x <= -57; x++)
            for (int z = -41; z <= -12; z++) {
                if (pkAvenue(x)) continue;
                for (int y = PK + 1; y <= 110; y++) set(x, y, z, AIR);
                int h = Math.floorMod(x * 31 + z * 17 + x * z, 9);
                set(x, PK, z, h == 0 ? id("minecraft:moss_block") : GRASS);
                set(x, PK - 1, z, DIRT);
            }
        pkPaths();
        pkGate();
        pkPond();
        pkBandstand();
        pkPlayShip();
        pkPromenade();
        pkBeds();
        // two swings on the west garden lawn, swinging north-south (homestead/swing)
        for (int[] s : new int[][]{{-76, -17}, {-85, -15}}) {
            if (getRaw(s[0], PK + 1, s[1]) > 0 && getRaw(s[0], PK + 1, s[1]) != AIR) continue;
            set(s[0], PK + 1, s[1], id("pixelpirates:swing[facing=south,half=lower,occupied=false]"));
            set(s[0], PK + 2, s[1], id("pixelpirates:swing[facing=south,half=upper,occupied=false]"));
        }
        // three blank easels on the lawns looking out over the harbour: the townsfolk paint here of an evening (homestead/town)
        int easels = 0;
        int moss = id("minecraft:moss_block");
        for (int[] s : new int[][]{{-80, -15}, {-62, -15}, {-83, -18}, {-59, -18}, {-87, -20}, {-64, -20}}) {
            if (easels >= 3) break;
            int under = getRaw(s[0], PK, s[1]);
            if (under != GRASS && under != moss) continue;
            boolean clear = true;
            for (int[] c : new int[][]{{0, 1, 0}, {0, 2, 0}, {0, 1, -1}, {0, 2, -1}}) {
                int v = getRaw(s[0] + c[0], PK + c[1], s[1] + c[2]);
                if (v > 0 && v != AIR) clear = false;
            }
            if (!clear) continue;
            set(s[0], PK + 1, s[1], id("pixelpirates:easel[facing=north,half=lower]"));
            set(s[0], PK + 2, s[1], id("pixelpirates:easel[facing=north,half=upper]"));
            easels++;
        }
        // the old park reached x-85..-55 z-43..-19 (over the street): keep it inside the label so a restamp clears it
        int[] box = LABELS.get(CUR);
        box[0] = Math.min(box[0], -89); box[1] = Math.min(box[1], -43); box[2] = Math.max(box[2], -55); box[3] = Math.max(box[3], -12);
    }

    /** Gravel walks: round the pond, to the bandstand and the ship, along the overlook. */
    private static void pkPaths() {
        int path = id("minecraft:dirt_path");
        for (int x = -89; x <= -57; x++)
            for (int z = -41; z <= -12; z++) {
                if (pkAvenue(x)) continue;
                double d = Math.hypot((x + 82) / 6.5, (z + 29.5) / 7.5);
                boolean ring = d >= 1.0 && d < 1.22;
                boolean overlook = z >= -14 && z <= -13;
                boolean east = x >= -66 && (z == -32 || z == -22 || x == -66);
                boolean north = z == -40;
                if (ring || overlook || east || north) set(x, PK, z, ((x + z) & 3) == 0 ? GRAVEL : path);
            }
    }

    /** The railing on the street and the garden gate arch over the promenade, with the GARDENS sign. */
    private static void pkGate() {
        int bars = id("minecraft:iron_bars[east=true,west=true]");
        for (int x = -89; x <= -57; x++) {
            if (pkAvenue(x)) continue;
            boolean gatePost = x == -82 || x == -79 || x == -63 || x == -60;
            boolean pier = (x + 89) % 6 == 0 || x == -74 || x == -66 || x == -57 || gatePost;
            boolean gap = x == -81 || x == -80 || x == -62 || x == -61;              // two garden gates
            if (gap) continue;
            set(x, PK + 1, -41, STONE_BRICKS);
            if (!pier) { set(x, PK + 2, -41, bars); continue; }
            set(x, PK + 2, -41, STONE_BRICKS);
            if (gatePost) { set(x, PK + 3, -41, STONE_BRICKS); set(x, PK + 4, -41, LANTERN); }
            else set(x, PK + 3, -41, id("minecraft:stone_brick_slab"));
        }
        // the arch: two tall piers either side of the avenue, an oak beam with a drop in the middle for the sign
        for (int x : new int[]{-74, -66}) {
            fill(x, PK + 1, -41, x, PK + 5, -41, STONE_BRICKS);
            set(x, PK + 1, -41, CHISELED_STONE_BRICKS);
            set(x, PK + 6, -41, id("minecraft:stone_brick_slab"));
            set(x, PK + 7, -41, LANTERN);
        }
        fill(-73, PK + 5, -41, -67, PK + 5, -41, id("minecraft:dark_oak_log[axis=x]"));
        set(-73, PK + 4, -41, stairsTop("stone_brick", "west"));
        set(-67, PK + 4, -41, stairsTop("stone_brick", "east"));
        for (int x : new int[]{-72, -68}) set(x, PK + 4, -41, id("minecraft:iron_bars[east=true,west=true]"));
        set(-70, PK + 4, -41, id("minecraft:dark_oak_log[axis=y]"));
        set(-70, PK + 4, -42, id("pixelpirates:park_sign[facing=north]"));
        set(-70, PK + 6, -41, id("minecraft:stone_brick_wall"));
        set(-70, PK + 7, -41, LANTERN);
    }

    /** The boating pond: 3 deep, a grassy island with the gazebo, the footbridge, the fishing jetty, lilies and reeds. */
    private static void pkPond() {
        int rim = id("minecraft:mossy_stone_bricks");
        for (int x = -89; x <= -75; x++)
            for (int z = -41; z <= -14; z++) {
                double d = Math.hypot((x + 82) / 6.5, (z + 29.5) / 7.5);
                if (d >= 1.0) continue;
                boolean island = Math.abs(x + 82) <= 2 && Math.abs(z + 30) <= 2;
                if (island) { set(x, PK, z, GRASS); for (int y = PK - 3; y < PK; y++) set(x, y, z, DIRT); continue; }
                if (d > 0.88) { set(x, PK, z, rim); set(x, PK - 1, z, rim); continue; }
                set(x, PK - 4, z, ((x + z) & 1) == 0 ? SAND : GRAVEL);
                for (int y = PK - 3; y <= PK - 1; y++) set(x, y, z, WATER);
                set(x, PK, z, AIR);
                int h = Math.floorMod(x * 7 + z * 13, 11);
                if (h == 0) set(x, PK, z, id("minecraft:lily_pad"));
                else if (h == 1) set(x, PK - 3, z, id("minecraft:seagrass"));
                else if (h == 2 && d > 0.7) { set(x, PK - 3, z, id("minecraft:sea_pickle[pickles=3,waterlogged=true]")); }
            }
        // THE ISLAND GAZEBO: four posts, a ring of benches, a copper roof, a lantern on a chain
        for (int[] p : new int[][]{{-84, -32}, {-80, -32}, {-84, -28}, {-80, -28}}) fill(p[0], PK + 1, p[1], p[0], PK + 3, p[1], id("minecraft:stripped_birch_log[axis=y]"));
        for (int x = -85; x <= -79; x++)
            for (int z = -33; z <= -27; z++) {
                int ring = Math.max(Math.abs(x + 82), Math.abs(z + 30));
                if (ring == 3) set(x, PK + 4, z, stairs("waxed_weathered_cut_copper", x == -85 ? "east" : x == -79 ? "west" : z == -33 ? "south" : "north"));
                else if (ring == 2) set(x, PK + 4, z, id("minecraft:waxed_weathered_cut_copper"));
                else if (ring == 1) set(x, PK + 5, z, id("minecraft:waxed_weathered_cut_copper_slab"));
            }
        for (int x = -84; x <= -80; x++) for (int z = -32; z <= -28; z++) if (Math.max(Math.abs(x + 82), Math.abs(z + 30)) == 1) set(x, PK + 4, z, id("minecraft:waxed_weathered_cut_copper"));
        set(-82, PK + 4, -30, id("minecraft:waxed_weathered_cut_copper"));
        set(-82, PK + 5, -30, id("minecraft:lightning_rod"));
        set(-82, PK + 3, -30, CHAIN); set(-82, PK + 2, -30, LANTERN_HANGING);
        for (int z = -31; z <= -29; z++) set(-84, PK + 1, z, stairs("birch", "west"));
        for (int x = -83; x <= -81; x++) set(x, PK + 1, -32, stairs("birch", "north"));
        set(-82, PK, -30, id("minecraft:polished_andesite"));
        // THE FOOTBRIDGE: from the island to the promenade - slab ends, a raised plank middle, rails, lamps
        for (int x = -79; x <= -76; x++) {
            boolean high = x == -78 || x == -77;
            for (int z = -31; z <= -29; z++) set(x, PK + 1, z, high ? SPRUCE : slab("spruce"));
            set(x, PK + 2, -31, SPRUCE_FENCE); set(x, PK + 2, -29, SPRUCE_FENCE);
        }
        set(-76, PK + 3, -31, LANTERN); set(-76, PK + 3, -29, LANTERN);
        // THE FISHING JETTY on the west shore: a little deck over the water, a bench, a barrel of tackle
        for (int x = -88; x <= -86; x++) for (int z = -30; z <= -28; z++) set(x, PK, z, SPRUCE);
        for (int z = -30; z <= -28; z++) set(-89, PK + 1, z, stairs("spruce", "west"));
        set(-86, PK + 1, -30, barrel("up")); set(-86, PK + 1, -28, SPRUCE_FENCE); set(-86, PK + 2, -28, LANTERN);
        for (int[] c : new int[][]{{-86, -31}, {-86, -27}}) set(c[0], PK - 1, c[1], SPRUCE_LOG_Y);
        // benches round the pond and the fruit palms (bananas hang under the fronds - pick them)
        for (int[] b : new int[][]{{-88, -38, 0}, {-79, -38, 0}, {-88, -19, 1}, {-83, -18, 1}}) {
            String face = b[2] == 0 ? "north" : "south";
            for (int k = 0; k < 3; k++) set(b[0] + k, PK + 1, b[1], stairs("spruce", face));
        }
        gardenPalm(-88, PK, -36, 6, true); gardenPalm(-88, PK, -22, 7, true); gardenPalm(-78, PK, -19, 6, false);
        lamppost(-83, PK, -38); lamppost(-84, PK, -19);
    }

    /** The bandstand: raised octagon-ish platform, white posts, a copper bell roof, a jukebox that plays your discs. */
    private static void pkBandstand() {
        int cx = -62, cz = -27;
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = cz - 3; z <= cz + 3; z++) {
                int dx = Math.abs(x - cx), dz = Math.abs(z - cz);
                if (dx + dz > 5) continue;
                boolean edge = dx == 3 || dz == 3 || dx + dz == 5;
                set(x, PK + 1, z, edge ? id("minecraft:polished_andesite") : (((x + z) & 1) == 0 ? id("minecraft:birch_planks") : id("minecraft:stripped_birch_wood")));
                if (edge && !(x == cx - 3 && Math.abs(z - cz) <= 1)) set(x, PK + 2, z, (dx + dz == 5 || (dx == 3 && dz == 0) || (dz == 3 && dx == 0))
                        ? id("minecraft:stripped_birch_log[axis=y]") : id("minecraft:birch_fence"));
                if (dx + dz == 5 || (dx == 3 && dz == 0) || (dz == 3 && dx == 0)) fill(x, PK + 2, z, x, PK + 4, z, id("minecraft:stripped_birch_log[axis=y]"));
            }
        for (int z = cz - 1; z <= cz + 1; z++) set(cx - 4, PK + 1, z, stairs("polished_andesite", "east"));   // the steps from the promenade
        for (int x = cx - 4; x <= cx + 4; x++)                                                         // the roof: a stepped copper bell
            for (int z = cz - 4; z <= cz + 4; z++) {
                int dx = Math.abs(x - cx), dz = Math.abs(z - cz), r = Math.max(dx, dz);
                if (dx + dz > 6) continue;
                set(x, PK + 5, z, r == 4 || dx + dz == 6 ? id("minecraft:waxed_weathered_cut_copper_slab") : id("minecraft:waxed_weathered_cut_copper"));
                if (r <= 2) set(x, PK + 6, z, id("minecraft:waxed_weathered_cut_copper"));
                if (r <= 1) set(x, PK + 7, z, id("minecraft:waxed_weathered_cut_copper"));
            }
        set(cx, PK + 8, cz, id("minecraft:waxed_weathered_cut_copper"));
        set(cx, PK + 9, cz, id("minecraft:lightning_rod"));
        set(cx, PK + 4, cz, id("pixelpirates:crystal_chandelier[facing=north]"));
        set(cx + 2, PK + 2, cz, JUKEBOX);
        set(cx + 2, PK + 2, cz - 1, NOTE_BLOCK); set(cx + 2, PK + 2, cz + 1, NOTE_BLOCK);
        for (int z : new int[]{cz - 2, cz + 2}) set(cx + 1, PK + 2, z, id("minecraft:lectern[facing=west]"));
        for (int z = cz - 1; z <= cz + 1; z += 2) set(cx, PK + 2, z, stairs("birch", "east"));
    }

    /** The play ship: a little pirate ship on the lawn for climbing - hull, a raised stern with a wheel, a mast and sail, a cannon. */
    private static void pkPlayShip() {
        for (int x = -65; x <= -58; x++) {
            int half = x <= -60 ? 2 : x == -59 ? 1 : 0;
            for (int z = -17 - half; z <= -17 + half; z++) {
                boolean wall = Math.abs(z + 17) == half || x == -65;
                set(x, PK, z, id("minecraft:spruce_planks"));
                set(x, PK + 1, z, wall ? (x == -58 ? stairs("spruce", "west") : id("minecraft:spruce_planks")) : AIR);
                if (wall && x != -58 && half > 0) set(x, PK + 2, z, id("minecraft:spruce_fence"));
            }
        }
        set(-57, PK + 1, -17, id("minecraft:spruce_fence"));                                      // the bowsprit
        set(-56, PK + 1, -17, id("minecraft:spruce_fence"));
        for (int z = -18; z <= -16; z++) { set(-65, PK + 1, z, AIR); set(-65, PK + 2, z, AIR); }    // the gangway in at the stern
        for (int x = -64; x <= -63; x++) for (int z = -19; z <= -15; z++) set(x, PK + 2, z, slabTop("spruce"));   // the quarterdeck
        for (int x = -64; x <= -63; x++) { set(x, PK + 3, -19, SPRUCE_FENCE); set(x, PK + 3, -15, SPRUCE_FENCE); }
        set(-62, PK + 1, -17, stairs("spruce", "west"));                                          // the steps up to it
        set(-64, PK + 3, -17, id("pixelpirates:ships_wheel[facing=east]"));
        fill(-61, PK + 1, -17, -61, PK + 7, -17, id("minecraft:spruce_fence"));                    // the mast + yard + sail
        fill(-61, PK + 6, -19, -61, PK + 6, -15, id("minecraft:spruce_fence[north=true,south=true]"));
        for (int y = PK + 3; y <= PK + 5; y++) for (int z = -18; z <= -16; z++) if (z != -17) set(-61, y, z, wool(y == PK + 4 ? "red" : "white"));
        set(-61, PK + 8, -17, id("minecraft:black_banner[rotation=4]"));
        set(-60, PK + 1, -19, id("pixelpirates:display_cannon[facing=north]"));
        set(-60, PK + 1, -15, id("pixelpirates:display_cannon[facing=south]"));
        set(-64, PK + 1, -18, barrel("up")); set(-64, PK + 1, -16, id("pixelpirates:cargo_crate[facing=east]"));
    }

    /** The promenade: palms and lamps either side of the avenue, benches facing it; the hedge on the guardhouse side. */
    private static void pkPromenade() {
        for (int z = -36; z <= -18; z += 6)
            for (int x : new int[]{-74, -66}) {
                if (x == -66 && z >= -30 && z <= -24) continue;                                  // the bandstand steps
                if (x == -74 && z >= -31 && z <= -29) continue;                                  // the footbridge
                if (((z + 36) / 6) % 2 == 0) gardenPalm(x, PK, z, 5 + Math.floorMod(z, 2), x == -74);
                else lamppost(x, PK, z);
            }
        for (int x : new int[]{-75, -65})                                                        // benches facing the avenue
            for (int z = -35; z <= -34; z++) set(x, PK + 1, z, stairs("spruce", x == -75 ? "west" : "east"));
        for (int z = -23; z <= -22; z++) set(-75, PK + 1, z, stairs("spruce", "west"));
        for (int z = -40; z <= -13; z++) set(-57, PK + 1, z, OAK_LEAVES);                         // the hedge on the guardhouse side
        for (int z = -40; z <= -13; z += 3) set(-57, PK + 2, z, OAK_LEAVES);
        // the overlook: benches along the terrace edge looking out over the harbour
        for (int x = -89; x <= -59; x += 4) {
            if (pkAvenue(x) || pkAvenue(x + 1)) continue;
            set(x, PK + 1, -12, stairs("spruce", "north")); set(x + 1, PK + 1, -12, stairs("spruce", "north"));
        }
    }

    /** Flower beds: a formal parterre north of the bandstand, wild borders by the pond. */
    private static void pkBeds() {
        int[] flowers = {POPPY, DANDELION, CORNFLOWER, OXEYE, id("minecraft:allium"), id("minecraft:red_tulip"), id("minecraft:orange_tulip"),
                id("minecraft:white_tulip"), id("minecraft:pink_tulip"), id("minecraft:azure_bluet")};
        for (int x = -65; x <= -58; x++)
            for (int z = -38; z <= -34; z++) {
                boolean border = x == -65 || x == -58 || z == -38 || z == -34;
                if (border) { set(x, PK + 1, z, id("minecraft:stone_brick_slab")); continue; }
                set(x, PK, z, GRASS);
                set(x, PK + 1, z, flowers[Math.floorMod(x * 3 + z * 5, flowers.length)]);
            }
        set(-62, PK + 1, -36, AIR); set(-61, PK + 1, -37, AIR);
        set(-62, PK + 1, -37, id("pixelpirates:garden_urn[facing=north]"));
        for (int i = 0; i < 26; i++) {
            int x = -89 + Math.floorMod(i * 37, 14), z = -40 + Math.floorMod(i * 23, 27);
            if (getRaw(x, PK, z) == GRASS && getRaw(x, PK + 1, z) == AIR) set(x, PK + 1, z, flowers[i % flowers.length]);
        }
    }

    /** A palm drawn face-connected (BUILD RULES #7): trunk, a crown whose fronds step down without corner-only joints;
     *  `fruit` hangs two pickable banana bunches under the fronds. */
    private static void gardenPalm(int x, int gy, int z, int h, boolean fruit) {
        for (int y = gy + 1; y <= gy + h; y++) set(x, y, z, PALM_LOG_Y);
        int t = gy + h;
        set(x, t + 1, z, PALM_LEAVES);
        int[][] arms = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] a : arms) {
            set(x + a[0], t + 1, z + a[1], PALM_LEAVES);
            set(x + 2 * a[0], t + 1, z + 2 * a[1], PALM_LEAVES);
            set(x + 2 * a[0], t, z + 2 * a[1], PALM_LEAVES);
            set(x + 3 * a[0], t, z + 3 * a[1], PALM_LEAVES);
            set(x + 3 * a[0], t - 1, z + 3 * a[1], PALM_LEAVES);
        }
        for (int[] d : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) {
            set(x + d[0], t + 1, z + d[1], PALM_LEAVES);
            set(x + 2 * d[0], t, z + d[1], PALM_LEAVES);
            set(x + 2 * d[0], t, z + 2 * d[1], PALM_LEAVES);
            set(x + 2 * d[0], t - 1, z + 2 * d[1], PALM_LEAVES);
        }
        if (fruit) {
            set(x + 2, t - 1, z, id("pixelpirates:banana_block[ripe=true]"));
            set(x, t - 1, z - 2, id("pixelpirates:banana_block[ripe=true]"));
        } else {
            set(x + 1, t, z, COCONUT_BLOCK);
            set(x, t, z - 1, COCONUT_BLOCK);
        }
    }

    private static void houseRows(Random rng) {
        // Mid-terrace row facing the market street (z -6..6, doors north onto z=8 street)
        int[][] midLots = {
            {-136, -124}, {-120, -108}, {-64, -52}, {-48, -36}, {-32, -20},
            {20, 32}, {36, 48}, {52, 64}, {108, 120}, {124, 136}
        };
        for (int i = 0; i < midLots.length; i++) {
            // rebuilt one by one, each for its own resident (they face the market street now)
            if (i == 0) { named("Townhouse " + (++HOUSE_NO), PortCityLayout::captainsHouse); continue; }
            if (i == 1) { named("Townhouse " + (++HOUSE_NO), PortCityLayout::herbalistsCottage); continue; }
            if (i == 2) { named("Townhouse " + (++HOUSE_NO), PortCityLayout::fortuneTellersParlour); continue; }
            if (i == 3) { named("Townhouse " + (++HOUSE_NO), PortCityLayout::instrumentMakersShop); continue; }
            if (i == 4) { named("Townhouse " + (++HOUSE_NO), PortCityLayout::parrotKeepersAviary); continue; }
            if (i == 5) { named("Townhouse " + (++HOUSE_NO), PortCityLayout::pearlDiversHouse); continue; }
            if (i == 6) { named("Townhouse " + (++HOUSE_NO), PortCityLayout::fireworksMakersTower); continue; }
            if (i == 7) { named("Townhouse " + (++HOUSE_NO), PortCityLayout::beekeepersSkep); continue; }
            if (i == 8) { named("Townhouse " + (++HOUSE_NO), PortCityLayout::glassblowersKiln); continue; }
            if (i == 9) { named("Townhouse " + (++HOUSE_NO), PortCityLayout::boatswainsHull); continue; }
            house(midLots[i][0], -6, midLots[i][1], 6, 67, "north", i, rng);
        }

        // Upper row A facing the upper street (doors north onto z=-42 street) - #31-#36, one character each (2026-10-04):
        // lots x-136..-124, -120..-108, -104..-92, 80..92, 96..108, 120..132
        Runnable[] rowA = {PortCityLayout::millersWindmill, PortCityLayout::sailmakersLoft, PortCityLayout::tattooParlour,
                PortCityLayout::treasureHuntersHouse, PortCityLayout::toymakersHouse, PortCityLayout::shipsCatKeeper};
        for (Runnable house : rowA) named("Townhouse " + (++HOUSE_NO), house);

        // Upper row B near the wall (doors south) - #37-#41, one character each (2026-10-04): the old lots x-70..-58 (now
        // -66..-56, off the west avenue), -54..-42, 56..68, 72..84, 96..108
        Runnable[] rowB = {PortCityLayout::coopersCask, PortCityLayout::barberSurgeon, PortCityLayout::spiceMerchant,
                PortCityLayout::laundress, PortCityLayout::marinePainter};
        for (Runnable house : rowB) named("Townhouse " + (++HOUSE_NO), house);
    }

    /** Parameterized townhouse with per-style materials; door on the given side. */
    private static int HOUSE_NO;

    private static void house(int x1, int z1, int x2, int z2, int gy, String doorSide, int styleIdx, Random rng) {
        named("Townhouse " + (++HOUSE_NO), () -> houseBody(x1, z1, x2, z2, gy, doorSide, styleIdx, rng));
    }

    private static void houseBody(int x1, int z1, int x2, int z2, int gy, String doorSide, int styleIdx, Random rng) {
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

    // ------------------------------------------------------------------ THE TOWNHOUSES, one character each (2026-10-02 on)
    // Each townhouse is being rebuilt as its own house for its own resident (the user is writing a character for each).
    // The mid-terrace row faces SOUTH onto the market street (z8..14) - the old generic houses had their doors on the
    // north side, facing the terrace wall. Lots: z-10 (behind, the back garden) .. z7 (the front fence).

    /** Clear a townhouse lot above the mid terrace and lay its ground: grass, a path to the door. */
    private static void thLot(int x1, int x2, int pathX) {
        for (int x = x1; x <= x2; x++)
            for (int z = -10; z <= 7; z++) {
                for (int y = 68; y <= 110; y++) set(x, y, z, AIR);
                set(x, 67, z, x == pathX && z >= 3 ? ((z & 1) == 0 ? GRAVEL : id("minecraft:dirt_path")) : GRASS);
                set(x, 66, z, DIRT);
            }
    }

    /*
     * #21 THE OLD CAPTAIN'S HOUSE (x-136..-124): a tall, narrow sea-captain's house - three storeys of navy clapboard
     * (blue terracotta) on a deepslate plinth, white birch trim, corner posts and shutters, a red door under the balcony
     * (a ship's wheel in its rail), a mermaid figurehead on the front, a hipped dark-oak roof carrying a WIDOW'S WALK
     * (railed deck, brass telescope, his old colours on a mast), a brick chimney on the west. Inside: the PARLOUR (fireplace,
     * captain's chair, map table, a model ship under the beams, sea chest, rum rack), the galley corner; upstairs his
     * cabin (bed, desk, logbook, sea chest); top floor the CHART ROOM (maps, cartography table, the ladder to the roof).
     * Front: picket fence, an anchor in the yard; behind: an upturned rowboat on trestles.
     */
    private static final int CPG = 67, CPF2 = 72, CPF3 = 77, CPC = 81;

    private static void captainsHouse() {
        final int x1 = -134, x2 = -126, z1 = -6, z2 = 3;
        thLot(-136, -124, -130);
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(x1, CPG - 2, z1, x2, CPG - 1, z2, COBBLE);
        int navy = id("minecraft:blue_terracotta"), trim = id("minecraft:birch_planks"), post = id("minecraft:stripped_birch_log[axis=y]");
        int db = id("minecraft:deepslate_bricks");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) {
                    set(x, CPG, z, DARK_OAK); set(x, CPF2, z, SPRUCE); set(x, CPF3, z, SPRUCE); set(x, CPC, z, SPRUCE);
                    continue;
                }
                set(x, CPG, z, STONE_BRICKS);
                for (int y = CPG + 1; y <= CPC; y++)
                    set(x, y, z, y == CPG + 1 ? db : (ex && ez) ? post : (y == CPF2 || y == CPF3 || y == CPC) ? trim : navy);
            }
        // windows with white shutters (open trapdoors flat on the wall either side)
        int[][] fronts = {{-132, 0}, {-128, 0}, {-132, 1}, {-130, 1}, {-128, 1}, {-132, 2}, {-128, 2}};
        int[] rows = {CPG + 2, CPF2 + 2, CPF3 + 2};
        for (int[] f : fronts)
            for (int z : new int[]{z1, z2}) {
                if (z == z2 && f[0] == -130 && f[1] == 1) continue;                     // the balcony door
                int y = rows[f[1]];
                for (int dy = 0; dy <= 1; dy++) set(f[0], y + dy, z, id("minecraft:glass_pane[east=true,west=true]"));
                String face = z == z2 ? "south" : "north";
                int out = z == z2 ? 1 : -1;
                for (int s : new int[]{-1, 1}) for (int dy = 0; dy <= 1; dy++)
                    set(f[0] + s, y + dy, z + out, id("minecraft:birch_trapdoor[facing=" + face + ",half=bottom,open=true]"));
                set(f[0], y - 1, z + out, id("minecraft:birch_slab[type=top]"));
            }
        for (int z = z1 + 2; z <= z2 - 2; z += 3)
            for (int y : rows) for (int x : new int[]{x1, x2}) {
                if (x == x1 && z >= -3 && z <= -1) continue;                               // the chimney breast
                for (int dy = 0; dy <= 1; dy++) set(x, y + dy, z, id("minecraft:glass_pane[north=true,south=true]"));
            }
        // the red front door, the balcony over it (posts, rail, the ship's wheel), the door out onto it
        innDoor(-130, CPG, z2, "mangrove", true);
        innDoor(-130, CPF2, z2, "mangrove", true);
        for (int x = -132; x <= -128; x++) for (int z = z2 + 1; z <= z2 + 2; z++) set(x, CPF2, z, SPRUCE);
        for (int x : new int[]{-132, -128}) fill(x, CPG + 1, z2 + 2, x, CPF2 - 1, z2 + 2, post);
        for (int x = -132; x <= -128; x++) set(x, CPF2 + 1, z2 + 2, x == -130 ? id("pixelpirates:ships_wheel[facing=north]") : id("minecraft:birch_fence"));
        for (int x : new int[]{-132, -128}) set(x, CPF2 + 1, z2 + 1, id("minecraft:birch_fence"));
        set(-130, CPF2 - 1, z2 + 1, LANTERN_HANGING);
        set(-129, CPF2 - 1, z2 + 2, id("minecraft:dark_oak_hanging_sign[rotation=0,attached=false]"));
        signText(-129, CPF2 - 1, z2 + 2, "", "The Old", "Captain", "");
        set(-130, CPF3 + 2, z2 + 1, id("pixelpirates:mermaid_figurehead[facing=south]"));
        // the hipped roof, the widow's walk on top
        roofHip(x1 - 1, z1 - 1, x2 + 1, z2 + 1, CPC + 1, "dark_oak", 3, id("minecraft:spruce_planks"));
        int deckY = CPC + 4;
        for (int x = -132; x <= -128; x++)
            for (int z = -4; z <= 1; z++)
                if (x == -132 || x == -128 || z == -4 || z == 1) set(x, deckY + 1, z, id("minecraft:birch_fence"));
        set(-131, deckY + 1, -3, id("pixelpirates:telescope[facing=south]"));                                      // the telescope
        fill(-129, deckY + 1, 0, -129, deckY + 8, 0, id("minecraft:stripped_birch_log[axis=y]"));    // his old colours
        for (int k = 1; k <= 3; k++) for (int y = deckY + 6; y <= deckY + 7; y++) set(-129 - k, y, 0, wool((k + y) % 2 == 0 ? "blue" : "white"));
        set(-129, deckY + 9, 0, id("minecraft:lightning_rod"));
        // the ladders: ground -> F3 up the west side; F3 -> the deck on a mast-post
        for (int y = CPG + 1; y <= CPF3; y++) set(x1 + 1, y, z1 + 1, id("minecraft:ladder[facing=east]"));
        fill(-128, CPF3 + 1, -3, -128, deckY - 1, -3, post);
        for (int y = CPF3 + 1; y <= deckY - 1; y++) set(-128, y, -2, id("minecraft:ladder[facing=south]"));
        set(-128, deckY, -2, id("minecraft:spruce_trapdoor[facing=south,half=top,open=false]"));
        // the chimney (west), its hearth inside
        fill(x1 - 2, CPG + 1, -3, x1 - 1, deckY + 2, -1, id("minecraft:bricks"));             // two deep: the fire stays inside
        fill(x1 - 1, CPG + 2, -2, x1 - 1, deckY + 2, -2, AIR);
        set(x1 - 1, CPG + 1, -2, CAMPFIRE);
        set(x1, CPG + 1, -2, AIR); set(x1, CPG + 2, -2, AIR);
        for (int z = -3; z <= -1; z++) set(x1 + 1, CPG + 3, z, stairsTop("brick", "west"));
        // the yard: picket fence + gate, the anchor, a bench; behind: the upturned rowboat
        for (int x = -136; x <= -124; x++) set(x, 68, 7, x == -130 ? id("minecraft:birch_fence_gate[facing=south]") : id("minecraft:birch_fence"));
        set(-134, 68, 6, ANCHOR_BLOCK); set(-133, 68, 6, id("minecraft:chain[axis=x]")); set(-132, 68, 6, id("minecraft:chain[axis=x]"));
        for (int x = -127; x <= -126; x++) set(x, 68, 5, stairs("spruce", "north"));
        set(-125, 68, 5, barrel("up")); set(-125, 69, 5, id("minecraft:potted_red_tulip"));
        for (int x : new int[]{-133, -129}) set(x, 68, -8, id("minecraft:spruce_fence"));
        for (int x = -133; x <= -129; x++) { set(x, 69, -8, stairsTop("spruce", "north")); set(x, 69, -9, stairsTop("spruce", "south")); }
        set(-131, 70, -8, id("minecraft:spruce_slab"));
        set(-128, 68, -9, id("pixelpirates:rope_coil[facing=south]"));
        set(-135, 68, 4, OAK_LEAVES); set(-135, 68, 5, OAK_LEAVES); set(-125, 68, -7, OAK_LEAVES);
        furnishCaptainsHouse(new Random(0xCA97L));
        int[] box = LABELS.get(CUR);                                                       // the old house + its eaves
        box[0] = Math.min(box[0], -137); box[1] = Math.min(box[1], -10); box[2] = Math.max(box[2], -123); box[3] = Math.max(box[3], 7);
    }

    /** The captain's house, room by room (see captainsHouse()). */
    private static void furnishCaptainsHouse(Random r) {
        final int g = CPG + 1, u = CPF2 + 1, t = CPF3 + 1;
        // THE PARLOUR: fireplace (west), his chair before it, a rug, the map table, the model ship under the beams
        ip(-131, g, -2, id("pixelpirates:captains_chair[facing=west]"));
        rug(-132, -4, -129, 0, g, "blue", "light_blue");
        ip(-128, g, -1, id("pixelpirates:map_table[facing=south]"));
        ip(-130, CPF2 - 1, -2, id("pixelpirates:votive_ship[facing=east]"));
        ip(-133, g, 2, id("pixelpirates:sea_chest[facing=east]"));
        ip(-127, g, 2, id("pixelpirates:rum_rack[facing=west,bottles=4]"));
        ip(-133, g + 2, -3, candle(2));
        // the galley corner (north-east): range, a barrel, a small table
        ip(-127, g, -5, id("minecraft:smoker[facing=west]")); ip(-128, g, -5, barrel("south")); ip(-129, g, -5, slabTop("spruce"));
        ip(-129, g + 1, -5, id("minecraft:lantern"));
        hangLantern(-130, CPF2 - 1, 1);
        // HIS CABIN (F2): bed, desk + chair, the logbook, a sea chest, wardrobe barrels
        ip(-127, u, -5, bed("blue", "north", true)); ip(-127, u, -4, bed("blue", "north", false));
        ip(-131, u, -5, id("pixelpirates:captains_desk[facing=south]")); ip(-131, u, -4, stairs("dark_oak", "south"));
        ip(-129, u, -5, id("minecraft:lectern[facing=south]"));
        ip(-127, u, 2, id("pixelpirates:sea_chest[facing=west]"));
        ip(-133, u, 2, barrel("east")); ip(-133, u + 1, 2, barrel("east"));
        rug(-132, -2, -128, 1, u, "white", "blue");
        hangLantern(-130, CPF3 - 1, -1);
        // THE CHART ROOM (F3): charts on the walls, the cartography table, a globe of sorts, the ladder-mast
        for (int x = -133; x <= -127; x += 2) ip(x, t + 1, -5, id("pixelpirates:sea_chart[facing=south]"));
        ip(-133, t, -1, CARTOGRAPHY); ip(-133, t, 1, id("minecraft:lectern[facing=east]"));
        ip(-127, t, 1, id("pixelpirates:map_table[facing=west]"));
        ip(-131, t, 2, id("minecraft:decorated_pot"));
        hangLantern(-131, CPC - 1, -1);
    }

    /*
     * #22 THE HERBALIST'S COTTAGE (x-120..-108): a low cottage for the island's healer - wattle-and-daub (packed mud)
     * between bamboo posts on a mossy-cobble plinth, a mossy roof cresting into flowering azalea, a fieldstone chimney
     * (west), a jungle-wood door under the REMEDIES sign. Inside one warm room: the hearth with a cauldron, a working
     * BREWING STAND, shelves of jars and potted herbs, herbs and glow-berries hanging from the loft, spore blossoms, the
     * loft bed. East: the GREENHOUSE (glass on bamboo, raised beds of chili, lime and pineapple, a hidden spring). Behind:
     * the HERB GARDEN (beehives, crop rows on a water channel, a composter, berry bushes) inside a bamboo fence.
     */
    private static final int HBG = 67;

    private static void herbalistsCottage() {
        final int x1 = -120, x2 = -113, z1 = -5, z2 = 3;
        thLot(-120, -108, -116);
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        FOOTPRINTS.add(new int[]{-112, -5, -108, 1});
        fill(x1, HBG - 2, z1, x2, HBG - 1, z2, COBBLE);
        int mud = id("minecraft:packed_mud"), bam = id("minecraft:bamboo_block[axis=y]");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) { set(x, HBG, z, ((x + z) & 3) == 0 ? id("minecraft:mossy_cobblestone") : id("minecraft:jungle_planks")); continue; }
                set(x, HBG, z, MOSSY_COBBLE);
                int u = ez ? x - x1 : z - z1;
                for (int y = HBG + 1; y <= HBG + 4; y++) set(x, y, z, y == HBG + 1 ? MOSSY_COBBLE : (ex && ez) || u % 3 == 0 ? bam : mud);
            }
        // the loft over the middle, the mossy roof (ridge along x), gables, the azalea crest
        fill(x1 + 1, HBG + 5, -3, x2 - 1, HBG + 5, 1, id("minecraft:jungle_planks"));
        gableRoofX(x1, x2, z1, z2, HBG + 5, "mossy_cobblestone", MOSSY_COBBLE);
        gableEndsX(x1, z1 + 1, z2 - 1, HBG + 5, mud);
        gableEndsX(x2, z1 + 1, z2 - 1, HBG + 5, mud);
        for (int x = x1 - 1; x <= x2 + 1; x++) if (Math.floorMod(x, 3) != 1) set(x, HBG + 11, -1, id("minecraft:flowering_azalea_leaves[persistent=true]"));
        // windows: little panes with flower boxes under them
        for (int x : new int[]{-118, -114})
            for (int z : new int[]{z1, z2}) {
                for (int y = HBG + 2; y <= HBG + 3; y++) set(x, y, z, id("minecraft:glass_pane[east=true,west=true]"));
                int out = z == z2 ? 1 : -1;
                set(x, HBG + 1, z + out, id("minecraft:jungle_trapdoor[facing=" + (z == z2 ? "south" : "north") + ",half=top,open=false]"));
                set(x, HBG + 2, z + out, id(x == -118 ? "minecraft:potted_azalea_bush" : "minecraft:potted_fern"));
            }
        for (int y = HBG + 2; y <= HBG + 3; y++) set(x1, y, 1, id("minecraft:glass_pane[north=true,south=true]"));
        // the door, the REMEDIES sign under the eave, a bench by the door
        innDoor(-116, HBG, z2, "jungle", true);
        set(-118, HBG + 4, z2 + 1, id("minecraft:bamboo_hanging_sign[rotation=0,attached=false]"));
        signText(-118, HBG + 4, z2 + 1, "", "Herbs &", "Remedies", "");
        for (int x = -112; x <= -111; x++) set(x, HBG + 1, z2 + 1, stairs("jungle", "north"));
        // the chimney (west gable): fieldstone, its hearth inside
        fill(x1 - 2, HBG + 1, -2, x1 - 1, HBG + 12, 0, id("minecraft:cobblestone"));          // two deep: the fire stays inside
        fill(x1 - 1, HBG + 2, -1, x1 - 1, HBG + 12, -1, AIR);
        set(x1 - 1, HBG + 1, -1, CAMPFIRE);
        set(x1, HBG + 1, -1, AIR); set(x1, HBG + 2, -1, AIR);
        for (int z = -2; z <= 0; z++) set(x1 + 1, HBG + 3, z, stairsTop("mossy_cobblestone", "west"));
        // the door to the greenhouse (east wall)
        innDoor(x2, HBG, -2, "jungle", false);
        hbGreenhouse();
        hbGardens();
        furnishHerbalistsCottage(new Random(0x4E2B5L));
        int[] box = LABELS.get(CUR);                                                       // the old house + its eaves
        box[0] = Math.min(box[0], -121); box[1] = Math.min(box[1], -10); box[2] = Math.max(box[2], -107); box[3] = Math.max(box[3], 7);
    }

    /** The greenhouse on the east side: glass on bamboo posts, a glass roof, raised beds round a hidden spring. */
    private static void hbGreenhouse() {
        final int x1 = -112, x2 = -108, z1 = -5, z2 = 1;
        int bam = id("minecraft:bamboo_block[axis=y]");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x2, ez = z == z1 || z == z2;
                set(x, HBG, z, MOSSY_COBBLE);
                set(x, HBG + 5, z, (x - x1) % 2 == 0 && !ez && !ex ? id("minecraft:bamboo_block[axis=z]") : id("minecraft:glass"));
                if (!ex && !ez) continue;
                for (int y = HBG + 1; y <= HBG + 4; y++)
                    set(x, y, z, (ex && ez) || (ez && (x - x1) % 2 == 0) || (ex && (z - z1) % 3 == 0) ? bam : y == HBG + 1 ? MOSSY_COBBLE : id("minecraft:glass"));
            }
        // raised beds: farmland of the island's crops round a spring
        String[] crops = {"pixelpirates:chili_crop[age=7]", "pixelpirates:lime_crop[age=7]", "pixelpirates:pineapple_crop[age=7]"};
        for (int x = -111; x <= -109; x++)
            for (int z = -4; z <= 0; z++) {
                if (x == -110 && z == -2) { set(x, HBG, z, WATER); set(x, HBG + 1, z, id("minecraft:lily_pad")); continue; }
                if (x == -111 && z == -2) continue;                                        // the path in from the cottage door
                set(x, HBG, z, id("minecraft:farmland[moisture=7]"));
                set(x, HBG + 1, z, id(crops[Math.floorMod(x + z, 3)]));
            }
        set(-111, HBG, -2, id("minecraft:mossy_cobblestone"));
        hangLantern(-110, HBG + 4, -4); hangLantern(-110, HBG + 4, 0);
    }

    /** The herb garden behind (beehives, crop rows on a channel, composter, berries) and the front garden; bamboo fences. */
    private static void hbGardens() {
        int fence = id("minecraft:bamboo_fence");
        for (int x = -120; x <= -108; x++) { set(x, 68, -10, fence); set(x, 68, 7, x == -116 ? id("minecraft:bamboo_fence_gate[facing=south]") : fence); }
        for (int z = -9; z <= -6; z++) { set(-120, 68, z, fence); set(-108, 68, z, fence); }
        for (int z = 2; z <= 6; z++) { set(-108, 68, z, fence); set(-120, 68, z + 2 > 6 ? 6 : z + 2, fence); }
        set(-119, 68, -9, id("minecraft:beehive[facing=south,honey_level=3]"));
        set(-119, 68, -7, id("minecraft:beehive[facing=south,honey_level=1]"));
        for (int x = -116; x <= -110; x++) {
            set(x, HBG, -8, WATER);
            for (int z : new int[]{-9, -7}) {
                set(x, HBG, z, id("minecraft:farmland[moisture=7]"));
                set(x, HBG + 1, z, id((x & 1) == 0 ? "pixelpirates:chili_crop[age=7]" : "minecraft:beetroots[age=3]"));
            }
        }
        set(-109, 68, -9, COMPOSTER); set(-109, 68, -7, barrel("up"));
        for (int x = -119; x <= -117; x++) set(x, 68, -6, id("minecraft:sweet_berry_bush[age=3]"));
        // the front garden: flowers either side of the path, a birdbath
        int[] fl = {id("minecraft:allium"), id("minecraft:azure_bluet"), id("minecraft:oxeye_daisy"), id("minecraft:cornflower"), id("minecraft:lily_of_the_valley"), POPPY};
        for (int x = -120; x <= -108; x++)
            for (int z = 4; z <= 6; z++) {
                if (x == -116 || getRaw(x, 68, z) != AIR || getRaw(x, 69, z) != AIR) continue;
                if (Math.floorMod(x * 5 + z * 3, 3) != 0) set(x, 68, z, fl[Math.floorMod(x + z * 2, fl.length)]);
            }
        set(-110, 68, 6, id("minecraft:stone_brick_wall")); set(-110, 69, 6, CAULDRON_WATER);
    }

    /** The herbalist's cottage, inside (see herbalistsCottage()). */
    private static void furnishHerbalistsCottage(Random r) {
        final int g = HBG + 1;
        // the hearth: a cauldron before it, a stool
        ip(-118, g, -1, CAULDRON_WATER); ip(-117, g, 0, id("pixelpirates:barrel_stool[facing=west]"));
        // the work bench (north wall): brewing stand, mortar, jars on a top-slab shelf over it
        for (int x = -118; x <= -115; x++) ip(x, g, -4, x == -117 ? id("minecraft:brewing_stand") : slabTop("jungle"));
        ip(-116, g + 1, -4, id("minecraft:flower_pot")); ip(-115, g + 1, -4, candle(3)); ip(-118, g + 1, -4, id("minecraft:decorated_pot"));
        for (int x = -119; x <= -114; x++) { ip(x, g + 2, -4, slabTop("jungle")); }
        String[] jars = {"minecraft:potted_red_mushroom", "minecraft:potted_brown_mushroom", "minecraft:potted_dead_bush", "minecraft:potted_fern",
                "minecraft:potted_flowering_azalea_bush", "minecraft:potted_cactus"};
        for (int i = 0; i < 6; i++) ip(-119 + i, g + 3, -4, id(jars[i]));
        // shelves of books and remedies (south-west), the recipe book on its lectern
        for (int x = -119; x <= -118; x++) { ip(x, g, 2, BOOKSHELF); ip(x, g + 1, 2, BOOKSHELF); }
        ip(-117, g, 2, id("minecraft:lectern[facing=north]"));
        // herbs and glow-berries hanging from the loft, spore blossoms under the beams
        for (int x = -118; x <= -115; x += 3) for (int z = -3; z <= 1; z += 2) ip(x, HBG + 4, z, id("minecraft:cave_vines[age=25,berries=" + (z == -1 ? "true" : "false") + "]"));
        ip(-116, HBG + 4, -1, id("minecraft:spore_blossom"));
        // the loft: the herbalist's bed, a chest, the ladder (east, by the greenhouse door)
        ip(-118, HBG + 6, -2, bed("green", "west", true)); ip(-117, HBG + 6, -2, bed("green", "west", false));
        ip(-119, HBG + 6, 0, chest("east"));
        for (int y = HBG + 1; y <= HBG + 5; y++) set(-114, y, -4, id("minecraft:ladder[facing=west]"));
        ip(-116, HBG + 6, 1, candle(1));
    }

    /** A shop sign hung on a bracket out from a wall that runs along x: `wood` hanging sign at (x, y, z), the wall at z-1. */
    private static void bracketSign(int x, int y, int z, String wood, String... lines) {
        set(x, y, z, id("minecraft:" + wood + "_wall_hanging_sign[facing=east]"));
        signText(x, y, z, lines);
    }

    /*
     * #23 THE FORTUNE TELLER'S PARLOUR (x-66..-52): the town's seer - plum-purple walls (purple terracotta) between dark-oak
     * posts on a polished-blackstone plinth, crimson trim and door, a teal (warped) roof, and a round-cornered TURRET on the
     * front-west corner (x-65..-61 z0..4) with magenta glass and a crystal-tipped spire. Inside: the READING ROOM (round
     * table, crystal ball, purple candles, curiosities, a beaded curtain at the door); upstairs her rooms; at the top of the
     * turret the STAR CHAMBER. The front garden: white moonflowers, a twisted tree and the MOON POOL (sea lanterns glowing
     * under the water).
     */
    private static final int FTG = 67, FTF2 = 72;

    private static void fortuneTellersParlour() {
        final int x1 = -62, x2 = -54, z1 = -6, z2 = 2;
        thLot(-64, -52, -58);
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(x1, FTG - 2, z1, x2, FTG - 1, z2, COBBLE);
        int plum = id("minecraft:purple_terracotta"), black = id("minecraft:polished_blackstone_bricks"), trim = id("minecraft:crimson_planks");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) { set(x, FTG, z, DARK_OAK); set(x, FTF2, z, SPRUCE); continue; }
                set(x, FTG, z, black);
                int u = ez ? x - x1 : z - z1;
                for (int y = FTG + 1; y <= FTG + 9; y++)
                    set(x, y, z, y == FTG + 1 ? black : (ex && ez) || u % 4 == 0 ? DARK_OAK_LOG_Y : y == FTF2 ? trim : plum);
            }
        gableRoofX(x1, x2, z1, z2, FTG + 10, "warped", id("minecraft:warped_planks"));
        gableEndsX(x1, z1 + 1, z2 - 1, FTG + 10, plum);
        gableEndsX(x2, z1 + 1, z2 - 1, FTG + 10, plum);
        // windows: plum-framed, crimson shutters
        for (int x : new int[]{-56, -60})
            for (int z : new int[]{z1, z2})
                for (int y : new int[]{FTG + 2, FTF2 + 2}) {
                    for (int dy = 0; dy <= 1; dy++) set(x, y + dy, z, id("minecraft:purple_stained_glass_pane[east=true,west=true]"));
                    int out = z == z2 ? 1 : -1;
                    for (int s : new int[]{-1, 1}) for (int dy = 0; dy <= 1; dy++)
                        set(x + s, y + dy, z + out, id("minecraft:crimson_trapdoor[facing=" + (z == z2 ? "south" : "north") + ",half=bottom,open=true]"));
                }
        for (int z = -4; z <= 0; z += 4) for (int y : new int[]{FTG + 2, FTF2 + 2}) for (int dy = 0; dy <= 1; dy++) set(x2, y + dy, z, id("minecraft:purple_stained_glass_pane[north=true,south=true]"));
        // the crimson door with its beaded curtain, the sign on its bracket
        innDoor(-58, FTG, z2, "crimson", true);
        set(-58, FTG + 3, z2 - 1, CHAIN); set(-58, FTG + 4, z2 - 1, CHAIN);
        bracketSign(-56, FTG + 4, z2 + 1, "crimson", "", "Fortunes", "Told", "");
        // THE TURRET: 5x5 round-cornered, three floors, magenta glass, a tall teal roof and a crystal on the spire
        final int tx1 = -65, tx2 = -61, tz1 = 0, tz2 = 4, top = FTG + 14;
        for (int x = tx1; x <= tx2; x++)
            for (int z = tz1; z <= tz2; z++) {
                boolean ex = x == tx1 || x == tx2, ez = z == tz1 || z == tz2, corner = ex && ez;
                set(x, FTG, z, black);
                for (int y = FTG + 1; y <= top; y++) set(x, y, z, corner ? AIR : (!ex && !ez) ? AIR : y == FTG + 1 ? black : (y == FTF2 || y == FTG + 10) ? trim : plum);
                if (!ex && !ez) { set(x, FTF2, z, SPRUCE); set(x, FTG + 10, z, SPRUCE); set(x, FTG, z, DARK_OAK); }
            }
        for (int y : new int[]{FTG + 3, FTF2 + 2, FTG + 12})
            for (int[] w : new int[][]{{-63, tz2, 1}, {tx1, 2, 0}, {-63, tz1, 1}})
                for (int dy = 0; dy <= 1; dy++) set(w[0], y + dy, w[1], id("minecraft:magenta_stained_glass_pane[" + (w[2] == 1 ? "east=true,west=true" : "north=true,south=true") + "]"));
        for (int z = tz1 + 1; z <= tz2 - 1; z++) for (int y = FTG + 1; y <= FTG + 9; y++) if (z <= z2) set(x1, y, z, AIR);          // open into the house
        set(x1, FTF2, 1, SPRUCE); set(x1, FTF2, 2, SPRUCE);
        for (int y = FTG + 1; y <= FTG + 10; y++) set(tx1 + 1, y, tz1 + 1, id("minecraft:ladder[facing=south]"));
        roofHip(tx1 - 1, tz1 - 1, tx2 + 1, tz2 + 1, top + 1, "warped", 3, id("minecraft:warped_planks"));
        set(-63, top + 5, 2, id("minecraft:warped_fence"));
        set(-63, top + 6, 2, id("minecraft:amethyst_cluster[facing=up]"));
        // the garden: moonflowers, a twisted tree, the moon pool
        for (int x = -63; x <= -60; x++) for (int z = 5; z <= 6; z++) set(x, FTG + 1, z, ((x + z) & 1) == 0 ? id("minecraft:lily_of_the_valley") : OXEYE);
        for (int x = -56; x <= -53; x++)
            for (int z = 4; z <= 6; z++) {
                boolean rim = x == -56 || x == -53 || z == 4 || z == 6;
                set(x, FTG, z, rim ? id("minecraft:mossy_stone_bricks") : WATER);
                if (!rim) { set(x, FTG - 1, z, id("minecraft:sea_lantern")); set(x, FTG + 1, z, x == -55 ? id("minecraft:lily_pad") : AIR); }
            }
        fill(-53, FTG + 1, -8, -53, FTG + 4, -8, DARK_OAK_LOG_Y);
        set(-54, FTG + 4, -8, id("minecraft:dark_oak_log[axis=x]"));
        for (int[] l : new int[][]{{-53, 5, -8}, {-54, 5, -8}, {-55, 4, -8}, {-53, 5, -9}, {-53, 5, -7}, {-52, 4, -8}, {-54, 5, -7}, {-55, 5, -8}})
            set(l[0], FTG + l[1], l[2], id("minecraft:dark_oak_leaves[persistent=true]"));
        for (int x = -64; x <= -52; x++) if (x != -58) set(x, FTG + 1, 7, id("minecraft:polished_blackstone_wall"));
        set(-59, FTG + 2, 7, id("minecraft:soul_lantern")); set(-57, FTG + 2, 7, id("minecraft:soul_lantern"));
        furnishFortuneTeller();
        int[] box = LABELS.get(CUR);
        box[0] = Math.min(box[0], -66); box[1] = Math.min(box[1], -10); box[2] = Math.max(box[2], -51); box[3] = Math.max(box[3], 7);
    }

    private static void furnishFortuneTeller() {
        final int g = FTG + 1, u = FTF2 + 1;
        // THE READING ROOM: the round table, the crystal ball, two chairs, candles; curiosities on the shelves
        for (int[] t : new int[][]{{-59, -2}, {-58, -2}, {-59, -1}, {-58, -1}}) { ip(t[0], g, t[1], slabTop("dark_oak")); ip(t[0], g + 1, t[1], carpet("purple")); }
        set(-58, g + 1, -2, id("minecraft:amethyst_cluster[facing=up]"));                     // the crystal ball on the cloth
        set(-59, g + 1, -1, id("minecraft:purple_candle[candles=3,lit=true]"));
        ip(-60, g, -2, stairs("crimson", "west")); ip(-57, g, -1, stairs("crimson", "east"));
        for (int x = -61; x <= -55; x++) { ip(x, g, -5, x % 2 == 0 ? BOOKSHELF : slabTop("dark_oak")); ip(x, g + 1, -5, slabTop("dark_oak")); }
        String[] cur = {"minecraft:skeleton_skull[rotation=8]", "minecraft:decorated_pot", "pixelpirates:spirit_bottles[facing=south,count=3]", "minecraft:purple_candle[candles=2,lit=true]",
                "minecraft:potted_crimson_fungus", "minecraft:potted_wither_rose", "minecraft:magenta_candle[candles=1,lit=true]"};
        for (int i = 0; i < 7; i++) ip(-61 + i, g + 2, -5, id(cur[i]));
        rug(-61, -4, -55, 1, g, "black", "purple");
        hangSoul(-58, FTF2 - 1, -3); hangSoul(-56, FTF2 - 1, 0);
        // upstairs: her bed, a wardrobe, the brew
        ip(-55, u, -5, bed("purple", "north", true)); ip(-55, u, -4, bed("purple", "north", false));
        ip(-61, u, -5, barrel("south")); ip(-61, u + 1, -5, barrel("south")); ip(-59, u, -5, id("minecraft:brewing_stand"));
        ip(-57, u, 1, CAULDRON_WATER); ip(-56, u, 1, id("pixelpirates:barrel_stool[facing=north]"));
        rug(-60, -3, -56, 0, u, "magenta", "purple");
        hangSoul(-58, FTG + 9, -2);
        // THE STAR CHAMBER (the turret top): charts, the telescope, the second crystal
        ip(-64, FTG + 11, 3, id("pixelpirates:sea_chart[facing=east]")); ip(-62, FTG + 11, 3, id("minecraft:lectern[facing=north]"));
        ip(-62, FTG + 11, 1, id("minecraft:amethyst_block")); ip(-62, FTG + 12, 1, id("minecraft:amethyst_cluster[facing=up]"));
    }

    private static void hangSoul(int x, int y, int z) {
        if (getRaw(x, y, z) != AIR) return;
        set(x, y, z, id("minecraft:soul_lantern[hanging=true]"));
        for (int yy = y + 1; yy < y + 24 && getRaw(x, yy, z) == AIR; yy++) set(x, yy, z, CHAIN);
    }

    /*
     * #24 THE INSTRUMENT MAKER'S SHOP (x-48..-36): compasses, sextants, chronometers. A neat red-brick front with andesite
     * quoins and smooth-stone bands, a glass BOW WINDOW onto the street, a CLOCK FACE in a copper frame above it, and a
     * bright copper mansard roof with a weathervane. Inside: the counter of instruments (lodestone, sundials, brass rods),
     * the grandfather clock, the workbench; upstairs his rooms under the copper and the ORRERY hanging from the roof.
     */
    private static final int IMG = 67, IMF2 = 72;

    private static void instrumentMakersShop() {
        final int x1 = -47, x2 = -37, z1 = -5, z2 = 2;
        thLot(-48, -36, -42);
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(x1, IMG - 2, z1, x2, IMG - 1, z2, COBBLE);
        int brick = id("minecraft:bricks"), quoin = POLISHED_ANDESITE, band = id("minecraft:smooth_stone");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) { set(x, IMG, z, ((x + z) & 1) == 0 ? id("minecraft:spruce_planks") : id("minecraft:stripped_spruce_wood")); set(x, IMF2, z, SPRUCE); continue; }
                set(x, IMG, z, STONE_BRICKS);
                for (int y = IMG + 1; y <= IMG + 9; y++) set(x, y, z, y == IMG + 1 ? STONE_BRICKS : (ex && ez) ? quoin : y == IMF2 ? band : brick);
            }
        roofHip(x1 - 1, z1 - 1, x2 + 1, z2 + 1, IMG + 10, "waxed_cut_copper", 3, id("minecraft:waxed_cut_copper"));
        // windows: tall sashes, stone sills and lintels
        for (int x = x1 + 2; x <= x2 - 2; x += 3)
            for (int z : new int[]{z1, z2}) {
                if (z == z2 && x <= -42) continue;                                          // the bow window + the clock
                int out = z == z2 ? 1 : -1;
                for (int y : new int[]{IMG + 2, IMF2 + 2}) {
                    for (int dy = 0; dy <= 1; dy++) set(x, y + dy, z, id("minecraft:glass_pane[east=true,west=true]"));
                    set(x, y - 1, z + out, id("minecraft:smooth_stone_slab[type=top]"));
                }
            }
        for (int z = -3; z <= 0; z += 3) for (int y : new int[]{IMG + 2, IMF2 + 2}) for (int x : new int[]{x1, x2}) for (int dy = 0; dy <= 1; dy++)
            set(x, y + dy, z, id("minecraft:glass_pane[north=true,south=true]"));
        // THE BOW WINDOW (x-46..-43): a glass bay a block out, a copper cap, the shop window opened behind it
        for (int x = -46; x <= -43; x++) {
            set(x, IMG, z2 + 1, STONE_BRICKS);
            set(x, IMG + 1, z2 + 1, brick);
            for (int y = IMG + 2; y <= IMG + 4; y++) { set(x, y, z2 + 1, id("minecraft:glass_pane[east=true,west=true]")); set(x, y, z2, AIR); }
            set(x, IMG + 5, z2 + 1, id("minecraft:waxed_cut_copper_slab"));
        }
        // the door, the sign
        innDoor(-40, IMG, z2, "dark_oak", true);
        bracketSign(-38, IMG + 4, z2 + 1, "dark_oak", "Compasses", "Sextants", "& Clocks", "");
        // THE CLOCK over the bow window: a white face, the hands at three o'clock, a copper frame
        int white = id("minecraft:white_concrete"), black = id("minecraft:black_concrete"), cu = id("minecraft:waxed_cut_copper");
        for (int x = -46; x <= -42; x++)
            for (int y = IMF2 + 1; y <= IMF2 + 5; y++) {
                boolean frame = x == -46 || x == -42 || y == IMF2 + 1 || y == IMF2 + 5;
                boolean hand = (x == -44 && y >= IMF2 + 3 && y <= IMF2 + 4) || (y == IMF2 + 3 && x == -43);
                set(x, y, z2, frame ? cu : hand ? black : white);
            }
        // the weathervane on the copper deck
        fill(-42, IMG + 14, -2, -42, IMG + 16, -2, id("minecraft:iron_bars"));
        set(-42, IMG + 17, -2, id("minecraft:lightning_rod"));
        set(-43, IMG + 16, -2, id("minecraft:waxed_cut_copper_slab[type=top]")); set(-41, IMG + 16, -2, id("minecraft:iron_bars[east=true,west=true]"));
        furnishInstrumentMaker();
        int[] box = LABELS.get(CUR);
        box[0] = Math.min(box[0], -49); box[1] = Math.min(box[1], -10); box[2] = Math.max(box[2], -35); box[3] = Math.max(box[3], 7);
    }

    private static void furnishInstrumentMaker() {
        final int g = IMG + 1, u = IMF2 + 1;
        // the display behind the bow window
        for (int x = -46; x <= -43; x++) { ip(x, g, 1, slabTop("spruce")); }
        ip(-46, g + 1, 1, id("minecraft:lodestone")); ip(-45, g + 1, 1, id("minecraft:daylight_detector")); ip(-44, g + 1, 1, id("minecraft:lightning_rod"));
        ip(-43, g + 1, 1, id("minecraft:daylight_detector"));
        // the counter, instruments on it, the workbench behind
        for (int x = -46; x <= -41; x++) { ip(x, g, -2, barrel("north")); ip(x, g + 1, -2, slabTop("dark_oak")); }
        ip(-45, g + 2, -2, id("minecraft:lightning_rod[facing=east]")); ip(-43, g + 2, -2, id("minecraft:daylight_detector")); ip(-42, g + 2, -2, candle(1));
        ip(-46, g, -4, CRAFTING); ip(-45, g, -4, CARTOGRAPHY); ip(-44, g, -4, GRINDSTONE); ip(-43, g, -4, SMITHING);
        for (int x = -42; x <= -40; x++) { ip(x, g + 2, -4, slabTop("spruce")); ip(x, g + 3, -4, x % 2 == 0 ? id("minecraft:lightning_rod") : id("minecraft:end_rod")); }
        // the grandfather clock (east wall)
        ip(-38, g, -1, id("minecraft:stripped_dark_oak_wood")); ip(-38, g + 1, -1, id("minecraft:observer[facing=west]")); ip(-38, g + 2, -1, id("minecraft:dark_oak_slab"));
        for (int y = g; y <= IMF2; y++) set(-38, y, -4, id("minecraft:ladder[facing=west]"));
        hangLantern(-44, IMF2 - 1, -1); hangLantern(-40, IMF2 - 1, 0);
        // upstairs: his bed, desk, chest; THE ORRERY hanging from the copper
        ip(-46, u, -4, bed("orange", "west", true)); ip(-45, u, -4, bed("orange", "west", false));
        ip(-46, u, 1, id("pixelpirates:captains_desk[facing=east]")); ip(-45, u, 1, stairs("dark_oak", "east"));
        ip(-40, u, 1, chest("north")); ip(-39, u, 1, BOOKSHELF); ip(-39, u + 1, 1, BOOKSHELF);
        int cx = -42, cz = -1, cy = IMF2 + 4;
        for (int y = cy + 1; y < IMG + 13 && getRaw(cx, y, cz) == AIR; y++) set(cx, y, cz, CHAIN);
        set(cx, cy, cz, id("minecraft:waxed_copper_block"));
        set(cx + 1, cy, cz, id("minecraft:lightning_rod[facing=east]")); set(cx - 1, cy, cz, id("minecraft:end_rod[facing=west]"));
        set(cx, cy, cz + 1, id("minecraft:lightning_rod[facing=south]")); set(cx, cy, cz - 1, id("minecraft:end_rod[facing=north]"));
    }

    /*
     * #25 THE PARROT KEEPER'S AVIARY (x-32..-20): a cheerful pink cherry-wood house (stripped cherry posts, jungle shutters,
     * a red-sandstone roof, bunting) with a veranda, and the AVIARY on the street side: a domed iron cage with a tree,
     * perches, feeders and water - stocked with real parrots once per world (homestead/parrot/Aviary; /ppaviary restocks).
     * Tame one with seeds and it becomes your ship's parrot (ParrotCompanion). Behind: a row of sunflowers.
     */
    private static final int AVG = 67, AVF2 = 72;
    public static final int[] AVIARY_CENTRE = {-23, 69, 3};

    private static void parrotKeepersAviary() {
        final int x1 = -32, x2 = -26, z1 = -8, z2 = -2;
        thLot(-32, -20, -29);
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        FOOTPRINTS.add(new int[]{-26, 0, -20, 6});
        fill(x1, AVG - 2, z1, x2, AVG - 1, z2, COBBLE);
        int pink = id("minecraft:cherry_planks"), post = id("minecraft:stripped_cherry_log[axis=y]");
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) { set(x, AVG, z, id("minecraft:jungle_planks")); set(x, AVF2, z, id("minecraft:jungle_planks")); continue; }
                set(x, AVG, z, id("minecraft:mud_bricks"));
                int u = ez ? x - x1 : z - z1;
                for (int y = AVG + 1; y <= AVG + 9; y++) set(x, y, z, y == AVG + 1 ? id("minecraft:mud_bricks") : (ex && ez) || u % 3 == 0 ? post : pink);
            }
        gableRoofZ(z1, z2, x1, x2, AVG + 10, "red_sandstone", id("minecraft:red_sandstone"));
        gableEndsZ(z1, x1 + 1, x2 - 1, AVG + 10, pink);
        gableEndsZ(z2, x1 + 1, x2 - 1, AVG + 10, pink);
        for (int z = z1 + 2; z <= z2 - 2; z += 2) for (int y : new int[]{AVG + 2, AVF2 + 2}) for (int x : new int[]{x1, x2}) {
            for (int dy = 0; dy <= 1; dy++) set(x, y + dy, z, id("minecraft:glass_pane[north=true,south=true]"));
            int out = x == x2 ? 1 : -1;
            for (int s : new int[]{-1, 1}) for (int dy = 0; dy <= 1; dy++) set(x + out, y + dy, z + s, id("minecraft:jungle_trapdoor[facing=" + (x == x2 ? "east" : "west") + ",half=bottom,open=true]"));
        }
        for (int y : new int[]{AVG + 2, AVF2 + 2}) for (int dy = 0; dy <= 1; dy++) set(-31 + 4, y + dy, z2, id("minecraft:glass_pane[east=true,west=true]"));
        innDoor(-29, AVG, z2, "jungle", true);
        // the veranda: posts, a red-sandstone roof, a hammock; bunting under the eaves
        for (int x : new int[]{x1, x2 - 1}) fill(x, AVG + 1, z2 + 2, x, AVG + 4, z2 + 2, post);
        for (int x = x1; x <= x2 - 1; x++) for (int z = z2 + 1; z <= z2 + 2; z++) set(x, AVG + 5, z, id("minecraft:red_sandstone_slab"));
        set(-31, AVG + 1, z2 + 1, hammock("east"));
        String[] flags = {"red", "yellow", "lime", "light_blue", "orange"};
        for (int x = x1 + 1; x <= x2 - 2; x++) set(x, AVG + 4, z2 + 2, carpetBunting(flags[Math.floorMod(x, 5)]));
        bracketSign(-27, AVG + 4, z2 + 1, "jungle", "", "Parrots", "& Seed", "");
        // the seed sacks by the door, sunflowers behind
        set(-30, AVG + 1, z2 + 1, HAY); set(-30, AVG + 2, z2 + 1, id("minecraft:composter[level=8]"));
        for (int x = -32; x <= -26; x++) { set(x, AVG + 1, -10, id("minecraft:sunflower[half=lower]")); set(x, AVG + 2, -10, id("minecraft:sunflower[half=upper]")); }
        avAviary();
        furnishParrotKeeper();
        int[] box = LABELS.get(CUR);
        box[0] = Math.min(box[0], -33); box[1] = Math.min(box[1], -10); box[2] = Math.max(box[2], -19); box[3] = Math.max(box[3], 7);
    }

    /** A strip of bunting: a wool flag hanging under the veranda roof (the slab above holds it). */
    private static int carpetBunting(String col) { return id("minecraft:" + col + "_wool"); }

    /** The aviary: a 7x7 iron cage round a tree, a stepped copper-and-bars dome, perches, feeders, water, a door. */
    private static void avAviary() {
        final int cx = -23, cz = 3;
        int bars = IRON_BARS;
        for (int x = cx - 3; x <= cx + 3; x++)
            for (int z = cz - 3; z <= cz + 3; z++) {
                int d = Math.max(Math.abs(x - cx), Math.abs(z - cz));
                set(x, AVG, z, d == 3 ? id("minecraft:mossy_stone_bricks") : GRASS);
                if (d == 3) for (int y = AVG + 1; y <= AVG + 5; y++) set(x, y, z, y == AVG + 1 ? id("minecraft:mossy_stone_bricks") : bars);
                // the dome, every course resting on the one below (face-connected): a copper ring + an inner copper ledge,
                // a ring of bars on the ledge, then a lid of bars over it all
                if (d == 3) set(x, AVG + 6, z, id("minecraft:waxed_oxidized_cut_copper"));
                if (d == 2) { set(x, AVG + 6, z, id("minecraft:waxed_oxidized_cut_copper_slab")); set(x, AVG + 7, z, bars); }
                if (d <= 2) set(x, AVG + 8, z, bars);
            }
        set(cx, AVG + 9, cz, id("minecraft:waxed_oxidized_copper"));
        set(cx, AVG + 10, cz, id("minecraft:lightning_rod"));
        // the door (west side, toward the house path)
        set(cx - 3, AVG + 1, cz, door("jungle", "west", false)); set(cx - 3, AVG + 2, cz, door("jungle", "west", true));
        innReserve(cx - 2, AVG + 1, cz); innReserve(cx - 2, AVG + 2, cz); innReserve(cx - 4, AVG + 1, cz); innReserve(cx - 4, AVG + 2, cz);
        // a jungle tree in the middle, perches, feeders, water, nest boxes
        fill(cx, AVG + 1, cz, cx, AVG + 4, cz, id("minecraft:jungle_log[axis=y]"));
        for (int x = cx - 1; x <= cx + 1; x++) for (int z = cz - 1; z <= cz + 1; z++) {
            if (x == cx && z == cz) { set(x, AVG + 5, z, id("minecraft:jungle_leaves[persistent=true]")); continue; }
            set(x, AVG + 5, z, id("minecraft:jungle_leaves[persistent=true]"));
            if ((x + z) % 2 == 0) set(x, AVG + 4, z, id("minecraft:jungle_leaves[persistent=true]"));
        }
        set(cx + 2, AVG + 1, cz - 2, id("minecraft:spruce_fence")); set(cx + 2, AVG + 2, cz - 2, id("minecraft:spruce_fence")); set(cx + 2, AVG + 3, cz - 2, slab("spruce"));
        set(cx - 2, AVG + 1, cz + 2, id("minecraft:spruce_fence")); set(cx - 2, AVG + 2, cz + 2, slab("spruce"));
        set(cx + 2, AVG + 1, cz + 2, id("minecraft:composter[level=7]"));
        set(cx - 2, AVG + 1, cz - 2, CAULDRON_WATER);
        set(cx + 2, AVG + 1, cz, barrel("up")); set(cx + 2, AVG + 2, cz, id("minecraft:spruce_trapdoor[facing=west,half=bottom,open=false]"));
        // PERCH BRANCHes out from the cage bars, over head height (facing = the way the branch points, away from its wall)
        set(cx, AVG + 4, cz - 2, id("pixelpirates:perch_branch[facing=south]"));
        set(cx + 2, AVG + 4, cz + 1, id("pixelpirates:perch_branch[facing=west]"));
        set(cx - 1, AVG + 4, cz + 2, id("pixelpirates:perch_branch[facing=north]"));
    }

    private static void furnishParrotKeeper() {
        final int g = AVG + 1, u = AVF2 + 1;
        // the keeper's parlour: perches, a chair, the seed jars, the ledger
        ip(-31, g, -7, id("pixelpirates:parrot_roost[facing=east]"));                      // the PARROT ROOST (your collection)
        ip(-27, g, -7, id("minecraft:spruce_fence")); ip(-27, g + 1, -7, slab("jungle"));
        ip(-29, g, -4, stairs("jungle", "north"));
        for (int x = -31; x <= -29; x++) { ip(x, g, -3, slabTop("jungle")); ip(x, g + 1, -3, id(x == -30 ? "minecraft:decorated_pot" : "minecraft:flower_pot")); }
        ip(-27, g, -3, id("minecraft:lectern[facing=west]"));
        rug(-30, -6, -28, -4, g, "yellow", "lime");
        hangLantern(-29, AVF2 - 1, -5);
        // upstairs: the bed, a window seat, a chest; the ladder
        ip(-31, u, -7, bed("lime", "east", false)); ip(-30, u, -7, bed("lime", "east", true));
        ip(-27, u, -4, chest("west")); ip(-27, u, -6, stairs("cherry", "east"));
        for (int y = g; y <= AVF2; y++) set(-27, y, -5, id("minecraft:ladder[facing=west]"));
        hangLantern(-29, AVG + 9, -5);
    }

    /** The old generic house's gable eaves stuck out one block past its lot: air there, so a restamp clears them. */
    private static void thEaves(int lx1, int lx2) {
        for (int x : new int[]{lx1 - 1, lx2 + 1})
            for (int z = -7; z <= 7; z++)
                for (int y = 68; y <= 90; y++) set(x, y, z, AIR);
    }

    /** Grow the current label box over a whole townhouse lot (+1 for the old eaves). */
    private static void thBox(int lx1, int lx2) {
        int[] box = LABELS.get(CUR);
        box[0] = Math.min(box[0], lx1 - 1); box[1] = Math.min(box[1], -10); box[2] = Math.max(box[2], lx2 + 1); box[3] = Math.max(box[3], 7);
    }

    /*
     * #26 THE PEARL DIVER'S HOUSE (x20..32): a whitewashed Mediterranean house - calcite walls on a prismarine plinth
     * studded with shells (and the odd glowing pearl), arched windows with blue (warped) shutters, a wrought-iron balcony
     * over the blue door, and a FLAT TERRACOTTA ROOF TERRACE (parapet, a striped parasol, deck chairs facing the sea, potted
     * plants) reached through a little blue-domed stair kiosk. Inside: chequered cyan/white tiles, the sorting table of
     * pearls and shells, the dive gear (copper helmets, a rinsing tub, rope), THE PEARL on its plinth; upstairs the diver's
     * room. The yard: a DIVING POOL lit from below with a copper DIVING BELL hanging from a davit, a sponge-drying rack,
     * bougainvillea up the corner, a low white wall.
     */
    private static final int PDG = 67, PDF2 = 72, PDR = 77;

    private static void pearlDiversHouse() {
        final int x1 = 22, x2 = 30, z1 = -6, z2 = 1;
        thLot(20, 32, 26);
        thEaves(20, 32);
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        fill(x1, PDG - 2, z1, x2, PDG - 1, z2, COBBLE);
        int white = id("minecraft:calcite"), base = id("minecraft:prismarine_bricks"), shell = id("pixelpirates:shell_block"),
                pearl = id("pixelpirates:pearl_block"), tileA = id("minecraft:cyan_terracotta"), tileB = id("minecraft:white_terracotta");
        Random r = new Random(0x9EA21L);
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) {
                    set(x, PDG, z, ((x + z) & 1) == 0 ? tileA : tileB);
                    set(x, PDF2, z, id("minecraft:birch_planks"));
                    set(x, PDR, z, id("minecraft:terracotta"));
                    continue;
                }
                set(x, PDG, z, base);
                for (int y = PDG + 1; y <= PDR + 1; y++) {
                    int b = y == PDG + 1 || y == PDR ? base : white;
                    if (y <= PDG + 2) { int k = r.nextInt(24); if (k < 5) b = shell; else if (k == 5) b = pearl; }   // shells set in the plinth
                    set(x, y, z, b);
                }
            }
        // arched windows (2 wide, quartz arch corners) with blue shutters
        for (int y : new int[]{PDG + 2, PDF2 + 1}) {
            if (y == PDG + 2) for (int x : new int[]{23, 28}) pdWindow(x, y, z2, true);
            else for (int x : new int[]{24, 25, 27, 28}) for (int dy = 0; dy <= 1; dy++) set(x, y + dy, z2, id("minecraft:glass_pane[east=true,west=true]"));   // glazed behind the balcony
            pdWindow(23, y, z1, true);
            if (y == PDG + 2) pdWindow(28, y, z1, true);                                      // not upstairs: the roof ladder is there
            for (int x : new int[]{x1, x2}) pdWindow(-3, y, x, false);
        }
        // the blue door, the balcony door over it, the wrought-iron balcony, the sign under it
        innDoor(26, PDG, z2, "warped", true);
        innDoor(26, PDF2, z2, "warped", true);
        for (int x = 24; x <= 28; x++) for (int z = z2 + 1; z <= z2 + 2; z++) set(x, PDF2, z, id("minecraft:smooth_quartz_slab[type=top]"));
        for (int x = 24; x <= 28; x++) set(x, PDF2 + 1, z2 + 2, id("minecraft:iron_bars[east=true,west=true]"));
        for (int x : new int[]{24, 28}) set(x, PDF2 + 1, z2 + 1, id("minecraft:iron_bars[north=true,south=true]"));
        set(25, PDF2 + 1, z2 + 1, id("minecraft:potted_red_tulip")); set(27, PDF2 + 1, z2 + 1, id("minecraft:potted_azure_bluet"));
        hangLantern(25, PDF2 - 1, z2 + 1);
        bracketSign(28, PDF2 - 1, z2 + 1, "warped", "", "Pearls &", "Diving", "");
        // the stairs up (birch, along the north wall, rising east) and their well in the upper floor
        for (int i = 0; i < 5; i++) set(23 + i, PDG + 1 + i, -5, stairs("birch", "east"));
        for (int x = 23; x <= 26; x++) set(x, PDF2, -5, AIR);
        for (int x = 23; x <= 26; x++) set(x, PDF2 + 1, -4, id("minecraft:birch_fence"));
        // THE ROOF TERRACE: the domed stair kiosk (ladder from the upper floor), parasol, chairs, pots
        for (int x = 27; x <= 29; x++)
            for (int z = -6; z <= -4; z++)
                if (x != 28 || z != -5) for (int y = PDR + 1; y <= PDR + 3; y++) set(x, y, z, white);
        set(28, PDR + 1, -4, door("warped", "south", false)); set(28, PDR + 2, -4, door("warped", "south", true));
        innReserve(28, PDR + 1, -3); innReserve(28, PDR + 2, -3);
        int dome = id("minecraft:dark_prismarine");
        set(28, PDR + 4, -5, dome);
        set(28, PDR + 4, -4, stairs("dark_prismarine", "north")); set(28, PDR + 4, -6, stairs("dark_prismarine", "south"));
        set(27, PDR + 4, -5, stairs("dark_prismarine", "east")); set(29, PDR + 4, -5, stairs("dark_prismarine", "west"));
        for (int[] c : new int[][]{{27, -6}, {29, -6}, {27, -4}, {29, -4}}) set(c[0], PDR + 4, c[1], id("minecraft:dark_prismarine_slab"));
        set(28, PDR + 5, -5, id("minecraft:prismarine_wall")); set(28, PDR + 6, -5, id("minecraft:lightning_rod"));
        for (int y = PDF2 + 1; y <= PDR + 2; y++) set(28, y, -5, id("minecraft:ladder[facing=south]"));
        fill(24, PDR + 1, -3, 24, PDR + 3, -3, id("minecraft:birch_fence"));                      // the parasol
        for (int x = 23; x <= 25; x++) for (int z = -4; z <= -2; z++) set(x, PDR + 4, z, wool(((x + z) & 1) == 0 ? "cyan" : "white"));
        set(23, PDR + 1, 0, stairs("birch", "north")); set(25, PDR + 1, 0, stairs("birch", "north"));
        set(24, PDR + 1, 0, slabTop("birch")); set(24, PDR + 2, 0, id("minecraft:potted_cactus"));
        String[] pots = {"minecraft:potted_azalea_bush", "minecraft:potted_flowering_azalea_bush", "minecraft:potted_red_tulip", "minecraft:potted_orange_tulip"};
        int pi = 0;
        for (int[] c : new int[][]{{x1, z1}, {x2, z2}, {x1, z2}, {x2, z1 + 3}}) set(c[0], PDR + 2, c[1], id(pots[pi++]));
        set(30, PDR + 1, -1, id("minecraft:decorated_pot"));
        // THE YARD: the diving pool (lit from below) with the bell on its davit
        for (int x = 27; x <= 31; x++)
            for (int z = 3; z <= 6; z++) {
                boolean rim = x == 27 || x == 31 || z == 3 || z == 6;
                if (rim) { set(x, PDG, z, base); set(x, PDG - 1, z, base); continue; }
                set(x, PDG, z, WATER); set(x, PDG - 1, z, WATER); set(x, PDG - 2, z, SEA_LANTERN);
            }
        set(28, PDG - 1, 4, id("minecraft:seagrass")); set(30, PDG - 1, 5, id("minecraft:sea_pickle[pickles=3,waterlogged=true]"));
        set(28, PDG - 1, 5, id("minecraft:kelp_plant")); set(28, PDG, 5, id("minecraft:kelp[age=25]"));
        fill(31, PDG + 1, 4, 31, PDG + 7, 4, DARK_OAK_LOG_Y);
        for (int x = 29; x <= 31; x++) set(x, PDG + 8, 4, DARK_OAK_LOG_X);
        for (int y = PDG + 5; y <= PDG + 7; y++) set(29, y, 4, CHAIN);
        set(29, PDG + 4, 4, id("minecraft:waxed_weathered_copper")); set(29, PDG + 3, 4, id("minecraft:waxed_weathered_cut_copper"));
        set(31, PDG + 1, 3, id("pixelpirates:rope_coil[facing=west]"));
        // the sponge rack, petals, the bougainvillea, the low white wall + gate
        for (int x = 21; x <= 24; x++) { set(x, PDG + 1, 4, slabTop("birch")); set(x, PDG + 2, 4, id((x & 1) == 0 ? "minecraft:wet_sponge" : "minecraft:sponge")); }
        for (int x : new int[]{21, 23}) set(x, PDG + 1, 6, id("minecraft:pink_petals[facing=north,flower_amount=4]"));
        for (int y = PDG + 1; y <= PDG + 6; y++) set(21, y, z2, id(y % 2 == 0 ? "minecraft:flowering_azalea_leaves[persistent=true]" : "minecraft:azalea_leaves[persistent=true]"));
        set(21, PDG + 6, z2 - 1, id("minecraft:flowering_azalea_leaves[persistent=true]"));
        for (int x = 20; x <= 32; x++) set(x, PDG + 1, 7, x == 26 ? id("minecraft:birch_fence_gate[facing=south]") : id("minecraft:diorite_wall"));
        // behind: a bench in the sun, the catch pots
        for (int x = 24; x <= 26; x++) set(x, PDG + 1, -8, stairs("birch", "north"));
        set(21, PDG + 1, -9, id("pixelpirates:lobster_pot")); set(22, PDG + 1, -9, id("pixelpirates:fish_trap")); set(31, PDG + 1, -9, barrel("up"));
        furnishPearlDiver();
        thBox(20, 32);
    }

    /** A 2-wide arched window with blue shutters: in a wall along x (alongX, panes at x..x+1) or along z (z..z+1). */
    private static void pdWindow(int a, int y, int wall, boolean alongX) {
        int x = alongX ? a : wall, z = alongX ? wall : a;
        String conn = alongX ? "east=true,west=true" : "north=true,south=true";
        for (int i = 0; i <= 1; i++)
            for (int dy = 0; dy <= 1; dy++) set(alongX ? x + i : x, y + dy, alongX ? z : z + i, id("minecraft:glass_pane[" + conn + "]"));
        if (alongX) {
            set(x, y + 2, z, stairsTop("quartz", "west")); set(x + 1, y + 2, z, stairsTop("quartz", "east"));
        } else {
            set(x, y + 2, z, stairsTop("quartz", "north")); set(x, y + 2, z + 1, stairsTop("quartz", "south"));
        }
        int out = alongX ? (z > -3 ? 1 : -1) : (x > 26 ? 1 : -1);
        String face = alongX ? (out > 0 ? "south" : "north") : (out > 0 ? "east" : "west");
        for (int dy = 0; dy <= 1; dy++) {
            int sh = id("minecraft:warped_trapdoor[facing=" + face + ",half=bottom,open=true]");
            if (alongX) { set(x - 1, y + dy, z + out, sh); set(x + 2, y + dy, z + out, sh); }
            else { set(x + out, y + dy, z - 1, sh); set(x + out, y + dy, z + 2, sh); }
        }
    }

    private static void furnishPearlDiver() {
        final int g = PDG + 1, u = PDF2 + 1;
        // the sorting table: pearls (white candles), a pot, shells; stools
        for (int x = 25; x <= 27; x++) ip(x, g, -2, slabTop("birch"));
        ip(25, g + 1, -2, id("minecraft:white_candle[candles=4,lit=false]"));
        ip(26, g + 1, -2, id("minecraft:decorated_pot"));
        ip(27, g + 1, -2, id("minecraft:dead_tube_coral_fan[waterlogged=false]"));
        ip(25, g, -1, id("pixelpirates:barrel_stool[facing=north]")); ip(27, g, -1, id("pixelpirates:barrel_stool[facing=north]"));
        // THE PEARL on its plinth by the window
        ip(29, g, 0, id("minecraft:quartz_pillar")); ip(29, g + 1, 0, id("pixelpirates:pearl_block"));
        // the dive gear (east wall): a copper helmet on a barrel, the rinsing tub, rope; nets hung to dry
        ip(29, g, -4, barrel("up")); ip(29, g + 1, -4, id("minecraft:waxed_copper_block"));
        ip(29, g, -3, CAULDRON_WATER); ip(29, g, -2, id("pixelpirates:rope_coil[facing=west]"));
        ip(27, PDF2 - 1, -3, id("pixelpirates:hanging_net")); ip(24, PDF2 - 1, -1, id("pixelpirates:hanging_net"));
        // the shell cabinet (west wall)
        for (int z = -1; z <= 0; z++) { ip(23, g, z, barrel("east")); ip(23, g + 1, z, id(z == 0 ? "minecraft:dead_brain_coral_fan[waterlogged=false]" : "pixelpirates:shell_block")); }
        hangLantern(26, PDF2 - 1, 0);
        // upstairs: the diver's bed, sea chest, wardrobe, a writing desk, a rug
        ip(29, u, -4, bed("light_blue", "north", true)); ip(29, u, -3, bed("light_blue", "north", false));
        ip(29, u, 0, id("pixelpirates:sea_chest[facing=west]"));
        ip(23, u, 0, barrel("east")); ip(23, u + 1, 0, barrel("east"));
        ip(23, u, -2, slabTop("birch")); ip(23, u + 1, -2, candle(2)); ip(24, u, -2, stairs("birch", "east"));
        rug(25, -3, 27, -1, u, "light_blue", "white");
        hangLantern(26, PDR - 1, -2);
    }

    /*
     * #27 THE FIREWORKS MAKER (x36..48): a half-timbered workshop (dark oak + orange plaster, a mangrove roof, a red and
     * yellow striped awning over a shop window of coloured "stars") built against THE POWDER TOWER - an octagon in yellow
     * with red and white bands, slit windows, three floors (powder store, mixing room, star room) and a crenellated
     * LAUNCH DECK on top: a rack of rockets, a mortar, a flag, scorch marks. In the yard a scorched TEST PIT.
     */
    private static final int FWG = 67, FWF2 = 72, FWT = 77, FWX = 45, FWZ = -6, FWD = 85;

    private static boolean fwIn(int x, int z) {
        int dx = Math.abs(x - FWX), dz = Math.abs(z - FWZ);
        return dx <= 3 && dz <= 3 && dx + dz <= 5;
    }

    private static boolean fwEdge(int x, int z) {
        return fwIn(x, z) && (!fwIn(x + 1, z) || !fwIn(x - 1, z) || !fwIn(x, z + 1) || !fwIn(x, z - 1));
    }

    private static void fireworksMakersTower() {
        final int x1 = 37, x2 = 42, z1 = -6, z2 = 3;
        thLot(36, 48, 41);
        thEaves(36, 48);
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        FOOTPRINTS.add(new int[]{43, -9, 48, -3});
        fill(x1, FWG - 2, z1, x2, FWG - 1, z2, COBBLE);
        Random r = new Random(0xF1BEL);
        int plaster = id("minecraft:orange_terracotta"), post = DARK_OAK_LOG_Y, scorch = id("minecraft:blackstone");
        // THE WORKSHOP
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) { set(x, FWG, z, SPRUCE); set(x, FWF2, z, SPRUCE); continue; }
                set(x, FWG, z, COBBLE);
                int u = ez ? x - x1 : z - z1;
                for (int y = FWG + 1; y < FWT; y++)
                    set(x, y, z, y == FWG + 1 ? (r.nextInt(4) == 0 ? scorch : COBBLE)
                            : (ex && ez) || u % 3 == 0 ? post : y == FWF2 ? id("minecraft:stripped_dark_oak_log[axis=" + (ez ? "x" : "z") + "]") : plaster);
            }
        gableRoofZ(z1, z2, x1, x2, FWT, "mangrove", id("minecraft:mangrove_planks"));
        gableEndsZ(z1, x1 + 1, x2 - 1, FWT, plaster);
        gableEndsZ(z2, x1 + 1, x2 - 1, FWT, plaster);
        // the shop window, the door, the striped awning, the sign; side + upper windows
        for (int x = 38; x <= 40; x++) for (int y = FWG + 2; y <= FWG + 3; y++) set(x, y, z2, id("minecraft:glass_pane[east=true,west=true]"));
        innDoor(41, FWG, z2, "mangrove", true);
        for (int x = x1; x <= x2; x++) set(x, FWG + 4, z2 + 1, wool((x & 1) == 0 ? "red" : "yellow"));
        bracketSign(38, FWF2, z2 + 1, "mangrove", "", "Fireworks", "& Flares", "");
        for (int x = 39; x <= 40; x++) for (int y = FWF2 + 1; y <= FWF2 + 2; y++) set(x, y, z2, id("minecraft:glass_pane[east=true,west=true]"));
        for (int z = -2; z <= -1; z++) for (int y : new int[]{FWG + 2, FWF2 + 1}) for (int dy = 0; dy <= 1; dy++)
            set(x1, y + dy, z, id("minecraft:glass_pane[north=true,south=true]"));
        for (int y = FWG + 1; y <= FWF2; y++) set(38, y, -5, id("minecraft:ladder[facing=east]"));
        // THE POWDER TOWER: yellow with red + white bands, floors 67/73/79, the launch deck at 85
        for (int x = FWX - 3; x <= FWX + 3; x++)
            for (int z = FWZ - 3; z <= FWZ + 3; z++) {
                if (!fwIn(x, z)) continue;
                if (!fwEdge(x, z)) {
                    for (int y = FWG + 1; y < FWD; y++) set(x, y, z, AIR);
                    set(x, FWG, z, STONE_BRICKS); set(x, FWG + 6, z, SPRUCE); set(x, FWG + 12, z, SPRUCE);
                    int k = r.nextInt(8);
                    set(x, FWD, z, k == 0 ? id("minecraft:coal_block") : k == 1 ? scorch : SPRUCE);
                    continue;
                }
                set(x, FWG, z, COBBLE);
                for (int y = FWG + 1; y <= FWD; y++) {
                    int b;
                    if (y <= FWG + 2) b = r.nextInt(3) == 0 ? scorch : COBBLE;
                    else if (y == 72 || y == 78 || y == 84 || y == FWD) b = id("minecraft:red_terracotta");
                    else if (y == 71 || y == 77 || y == 83) b = id("minecraft:white_terracotta");
                    else b = id("minecraft:yellow_terracotta");
                    set(x, y, z, b);
                }
                if (((x + z) & 1) == 0) set(x, FWD + 1, z, id("minecraft:red_terracotta"));            // merlons
            }
        // slit windows (south + east faces; the west only above the workshop roof)
        for (int y : new int[]{FWG + 3, FWG + 7, FWG + 13}) {
            for (int dy = 0; dy <= 1; dy++) {
                set(FWX, y + dy, FWZ + 3, id("minecraft:orange_stained_glass_pane[east=true,west=true]"));
                set(FWX + 3, y + dy, FWZ, id("minecraft:orange_stained_glass_pane[north=true,south=true]"));
            }
        }
        for (int dy = 0; dy <= 1; dy++) set(FWX - 3, FWG + 13 + dy, FWZ, id("minecraft:orange_stained_glass_pane[north=true,south=true]"));
        innDoor(42, FWG, -5, "mangrove", false);                                                  // workshop <-> tower
        for (int y = FWG + 1; y <= FWD; y++) set(FWX, y, FWZ - 2, id("minecraft:ladder[facing=south]"));
        // THE LAUNCH DECK: the rocket rack, the mortar, powder kegs, the flag
        String[] body = {"red", "white", "light_blue"};
        for (int i = 0; i < 3; i++) {
            int x = FWX - 1 + i;
            set(x, FWD + 1, FWZ, SPRUCE_FENCE); set(x, FWD + 2, FWZ, wool(body[i])); set(x, FWD + 3, FWZ, id("minecraft:lightning_rod"));
        }
        for (int x : new int[]{FWX - 2, FWX + 2}) fill(x, FWD + 1, FWZ, x, FWD + 3, FWZ, SPRUCE_FENCE);
        for (int x = FWX - 2; x <= FWX + 2; x++) set(x, FWD + 4, FWZ, DARK_OAK_LOG_X);                  // the rack's top bar
        set(FWX - 1, FWD + 1, FWZ + 2, id("minecraft:cauldron"));
        set(FWX + 1, FWD + 1, FWZ + 2, barrel("up")); set(FWX + 1, FWD + 2, FWZ + 2, id("minecraft:red_candle[candles=3,lit=false]"));
        fill(FWX + 2, FWD + 1, FWZ - 1, FWX + 2, FWD + 6, FWZ - 1, SPRUCE_FENCE);
        for (int x = FWX + 3; x <= FWX + 4; x++) for (int y = FWD + 5; y <= FWD + 6; y++) set(x, y, FWZ - 1, wool(((x + y) & 1) == 0 ? "red" : "yellow"));
        // THE TEST PIT in the yard: a scorched ring, a mortar tube in the middle; the fence + gate
        for (int x = 44; x <= 48; x++)
            for (int z = 3; z <= 6; z++) {
                boolean ring = x == 44 || x == 48 || z == 3 || z == 6;
                set(x, FWG, z, ring ? COBBLE : r.nextInt(3) == 0 ? id("minecraft:coal_block") : scorch);
            }
        set(46, FWG + 1, 4, id("minecraft:cauldron")); set(46, FWG + 1, 5, id("minecraft:campfire[lit=false]"));
        set(44, FWG + 1, 2, CAULDRON_WATER);
        for (int x = 36; x <= 48; x++) set(x, FWG + 1, 7, x == 41 ? id("minecraft:spruce_fence_gate[facing=south]") : SPRUCE_FENCE);
        // behind the workshop: crates of paper and powder
        set(38, FWG + 1, -9, id("pixelpirates:cargo_crate[facing=south]")); set(39, FWG + 1, -9, id("pixelpirates:cargo_crate[facing=south]"));
        set(38, FWG + 2, -9, barrel("up")); set(40, FWG + 1, -9, barrel("up"));
        furnishFireworksMaker();
        thBox(36, 48);
    }

    private static void furnishFireworksMaker() {
        final int g = FWG + 1, u = FWF2 + 1;
        // the shop window: coloured "stars" on a shelf; the counter + bell; stock shelves (west wall)
        String[] stars = {"red", "orange", "lime", "light_blue", "magenta"};
        for (int x = 38; x <= 40; x++) { ip(x, g, 2, slabTop("spruce")); ip(x, g + 1, 2, id("minecraft:" + stars[x - 38] + "_candle[candles=4,lit=true]")); }
        for (int x = 38; x <= 40; x++) ip(x, g, 0, slabTop("spruce"));
        ip(40, g + 1, 0, id("minecraft:bell[attachment=floor,facing=south]")); ip(38, g + 1, 0, id("minecraft:yellow_candle[candles=2,lit=true]"));
        // the bench at the back: crafting, loom (the paper), a fire tub by the scorched corner
        ip(39, g, -5, CRAFTING); ip(40, g, -5, id("minecraft:loom[facing=south]"));
        for (int x = 40; x <= 41; x++) for (int z = -3; z <= -2; z++) set(x, FWG, z, ((x + z) & 1) == 0 ? id("minecraft:blackstone") : id("minecraft:coal_block"));
        ip(41, g, -3, CAULDRON_WATER);
        hangLantern(40, FWF2 - 1, -1);
        // upstairs: his bed, a chest, banners, a rug
        ip(41, u, -5, bed("red", "north", true)); ip(41, u, -4, bed("red", "north", false));
        ip(39, u, 2, chest("north")); ip(41, u, 2, barrel("north"));
        ip(40, u + 1, -5, wallBanner("yellow", "south")); ip(39, u + 1, -5, wallBanner("red", "south"));
        rug(39, -3, 40, 1, u, "orange", "yellow");
        hangLantern(39, FWT, -1);
        // THE TOWER: powder store, mixing room (y74), star room (y80)
        ip(47, g, -7, barrel("west")); ip(47, g, -6, barrel("west")); ip(47, g, -5, barrel("west"));
        ip(46, g, -4, barrel("up")); ip(46, g + 1, -4, barrel("up")); ip(44, g, -8, barrel("up"));
        hangLantern(45, FWG + 5, -5);
        int m = FWG + 7;
        ip(44, m, -8, id("minecraft:brewing_stand")); ip(47, m, -6, CAULDRON_WATER); ip(47, m, -5, id("minecraft:cauldron"));
        String[] dye = {"red", "lime", "light_blue"};
        for (int i = 0; i < 3; i++) { ip(44 + i, m, -4, slabTop("spruce")); ip(44 + i, m + 1, -4, id("minecraft:" + dye[i] + "_candle[candles=3,lit=true]")); }
        hangLantern(45, FWG + 11, -5);
        int s = FWG + 13;
        ip(43, s, -6, id("minecraft:loom[facing=east]")); ip(47, s, -6, CRAFTING);
        ip(45, s, -4, slabTop("spruce")); ip(45, s + 1, -4, id("minecraft:purple_candle[candles=4,lit=true]"));
        ip(46, s, -4, slabTop("spruce")); ip(46, s + 1, -4, id("minecraft:yellow_candle[candles=3,lit=true]"));
        hangLantern(45, FWD - 1, -5);
    }

    /*
     * #28 THE BEEKEEPER & CHANDLER (x52..64): a round SKEP HOUSE - walls of coiled straw (hay, laid along the curve) on a
     * mud-brick plinth, honeycomb bands, a beehive dome capped with a bee nest, a thatched porch, round amber windows.
     * Inside: the candle workshop (wax vats, the dipping table, racks of coloured candles), the honey store (jars of honey
     * on barrels, the extractor), the counter; up a ladder the loft bed under the dome. Outside: hives on stands in a
     * wildflower meadow, lilacs and peonies along the sides, a birch picket fence.
     */
    private static final int HWG = 67, HWX = 58, HWZ = -3;
    private static final double HWR = 5.4;

    private static double hwD(int x, int z) { return Math.sqrt((x - HWX) * (x - HWX) + (z - HWZ) * (z - HWZ)); }
    private static boolean hwIn(int x, int z) { return hwD(x, z) <= HWR; }
    private static boolean hwEdge(int x, int z) {
        return hwIn(x, z) && (!hwIn(x + 1, z) || !hwIn(x - 1, z) || !hwIn(x, z + 1) || !hwIn(x, z - 1));
    }
    /** Dome radius at height y (0 above the top). */
    private static double hwDome(int y) {
        double t = (y - (HWG + 5)) / 8.0;
        return t >= 1 ? 0 : HWR * Math.sqrt(1 - t * t);
    }

    private static void beekeepersSkep() {
        thLot(52, 64, HWX);
        thEaves(52, 64);
        FOOTPRINTS.add(new int[]{HWX - 5, HWZ - 5, HWX + 5, HWZ + 5});
        int honey = id("minecraft:honeycomb_block"), mud = id("minecraft:mud_bricks");
        for (int x = HWX - 6; x <= HWX + 6; x++)
            for (int z = HWZ - 6; z <= HWZ + 6; z++) {
                if (!hwIn(x, z)) continue;
                double d = hwD(x, z);
                int coil = id("minecraft:hay_block[axis=" + (Math.abs(z - HWZ) >= Math.abs(x - HWX) ? "x" : "z") + "]");
                set(x, HWG - 1, z, COBBLE); set(x, HWG - 2, z, COBBLE);
                if (hwEdge(x, z)) {
                    set(x, HWG, z, mud);
                    for (int y = HWG + 1; y <= HWG + 5; y++) set(x, y, z, y == HWG + 1 ? mud : y == HWG + 4 ? honey : coil);
                } else {
                    set(x, HWG, z, ((x + z) & 1) == 0 ? id("minecraft:birch_planks") : id("minecraft:stripped_birch_wood"));
                    for (int y = HWG + 1; y <= HWG + 5; y++) set(x, y, z, AIR);
                }
                // the dome: each course covers from its own radius in to just under the next one, so it closes face-to-face
                for (int y = HWG + 6; y <= HWG + 14; y++) {
                    double rr = hwDome(y), next = hwDome(y + 1);
                    boolean shell = d <= rr && d > next - 1.0;
                    set(x, y, z, shell ? (y == HWG + 7 ? honey : coil) : AIR);
                }
            }
        set(HWX, HWG + 14, HWZ, id("minecraft:hay_block[axis=y]"));
        set(HWX, HWG + 15, HWZ, id("minecraft:bee_nest[facing=south,honey_level=5]"));
        // the door + its thatched porch, the sign; round amber windows (walls + dome)
        innDoor(HWX, HWG, HWZ + 5, "birch", true);
        for (int x : new int[]{HWX - 1, HWX + 1}) fill(x, HWG + 1, HWZ + 6, x, HWG + 3, HWZ + 6, id("minecraft:birch_fence"));
        for (int x = HWX - 1; x <= HWX + 1; x++) set(x, HWG + 4, HWZ + 6, id("pixelpirates:thatch_slab"));
        bracketSign(HWX + 2, HWG + 4, HWZ + 6, "birch", "Honey &", "Beeswax", "Candles", "");
        for (int x : new int[]{HWX - 5, HWX + 5}) for (int y = HWG + 2; y <= HWG + 3; y++) set(x, y, HWZ, id("minecraft:yellow_stained_glass_pane[north=true,south=true]"));
        for (int[] w : new int[][]{{HWX, HWZ + 5}, {HWX + 5, HWZ}, {HWX - 5, HWZ}}) set(w[0], HWG + 8, w[1], id("minecraft:yellow_stained_glass"));
        // the loft over the north half, its ladder (against the north wall) and rail
        for (int x = HWX - 4; x <= HWX + 4; x++)
            for (int z = HWZ - 5; z <= HWZ - 1; z++)
                if (hwIn(x, z) && !hwEdge(x, z) && getRaw(x, HWG + 6, z) == AIR) set(x, HWG + 6, z, id("minecraft:birch_planks"));
        for (int x = HWX - 4; x <= HWX + 4; x++) if (getRaw(x, HWG + 6, HWZ - 1) != AIR && getRaw(x, HWG + 7, HWZ - 1) == AIR) set(x, HWG + 7, HWZ - 1, id("minecraft:birch_fence"));
        for (int y = HWG + 1; y <= HWG + 7; y++) set(HWX, y, HWZ - 4, id("minecraft:ladder[facing=south]"));
        // THE MEADOW: hives on stands, wildflowers, lilacs + peonies down the sides, the picket fence + gate
        for (int[] h : new int[][]{{53, 4}, {53, 6}, {63, 4}, {63, 6}, {54, -9}, {56, -10}, {60, -10}, {62, -9}}) {
            set(h[0], HWG + 1, h[1], slabTop("spruce"));
            set(h[0], HWG + 2, h[1], id("minecraft:beehive[facing=" + (h[1] > 0 ? "south" : "north") + ",honey_level=" + (Math.floorMod(h[0], 3) + 2) + "]"));
        }
        String[] fl = {"minecraft:cornflower", "minecraft:allium", "minecraft:oxeye_daisy", "minecraft:azure_bluet", "minecraft:poppy",
                "minecraft:dandelion", "minecraft:pink_petals[facing=east,flower_amount=3]", "minecraft:lily_of_the_valley"};
        Random r = new Random(0xB33L);
        for (int x = 52; x <= 64; x++)
            for (int z = -10; z <= 6; z++) {
                if (x == HWX && z >= 3) continue;
                if (hwD(x, z) <= HWR + 0.6 || getRaw(x, HWG + 1, z) != AIR || getRaw(x, HWG, z) != GRASS) continue;
                if (r.nextInt(3) == 0) set(x, HWG + 1, z, id(fl[r.nextInt(fl.length)]));
            }
        for (int[] t : new int[][]{{52, -6}, {52, -2}, {64, -6}, {64, -2}, {52, -8}, {64, 0}}) {
            boolean lilac = t[1] < -3;
            set(t[0], HWG + 1, t[1], id("minecraft:" + (lilac ? "lilac" : "peony") + "[half=lower]"));
            set(t[0], HWG + 2, t[1], id("minecraft:" + (lilac ? "lilac" : "peony") + "[half=upper]"));
        }
        for (int x = 52; x <= 64; x++) set(x, HWG + 1, 7, x == HWX ? id("minecraft:birch_fence_gate[facing=south]") : id("minecraft:birch_fence"));
        furnishBeekeeper();
        thBox(52, 64);
    }

    private static void furnishBeekeeper() {
        final int g = HWG + 1;
        // the counter by the door: honey jars, the bell
        ip(60, g, 0, slabTop("birch")); ip(61, g, 0, slabTop("birch"));
        ip(60, g + 1, 0, id("minecraft:honey_block")); ip(61, g + 1, 0, id("minecraft:bell[attachment=floor,facing=south]"));
        // the dipping table in the middle: candles drying
        for (int x = 57; x <= 59; x++) { ip(x, g, -2, slabTop("birch")); ip(x, g + 1, -2, id("minecraft:" + (x == 58 ? "white" : "yellow") + "_candle[candles=4,lit=" + (x == 58) + "]")); }
        // the wax vats (west) and the extractor
        ip(54, g, -3, CAULDRON_WATER); ip(54, g, -1, id("minecraft:cauldron"));
        ip(55, g, -5, barrel("up")); ip(55, g + 1, -5, id("minecraft:grindstone[face=floor,facing=north]"));
        // racks of coloured candles (east): barrels with candles, a shelf over them
        String[] col = {"orange", "yellow", "white", "lime", "pink"};
        int i = 0;
        for (int[] c : new int[][]{{62, -5}, {62, -4}, {62, -2}, {62, -1}, {61, -6}}) {
            ip(c[0], g, c[1], barrel("up")); ip(c[0], g + 1, c[1], id("minecraft:" + col[i++ % col.length] + "_candle[candles=3,lit=true]"));
        }
        // the honey store (north-west): jars on barrels
        for (int[] c : new int[][]{{55, -6}, {56, -7}}) { ip(c[0], g, c[1], barrel("up")); ip(c[0], g + 1, c[1], id("minecraft:honey_block")); }
        ip(55, g, 0, CRAFTING); ip(56, g, 1, id("minecraft:honeycomb_block"));
        hangLantern(58, HWG + 5, 0);
        // the loft: the bed, a chest, a candle
        ip(56, HWG + 7, -6, bed("yellow", "west", true)); ip(57, HWG + 7, -6, bed("yellow", "west", false));
        ip(60, HWG + 7, -6, chest("west")); ip(59, HWG + 7, -5, id("minecraft:white_candle[candles=2,lit=true]"));
    }

    // ------------------------------------------------------------------ TOWNHOUSES #29-#36 (2026-10-04): the last two of the
    // market-street row and the six houses of the upper row (row A, doors NORTH onto the upper street z-48..-42).

    private static final int UG = 70;                                    // the upper terrace: floor blocks at 70, standing at 71

    /** Clear + grass an upper-row lot x1..x2 (z-40 front fence line .. z-12, the terrace edge is z-11). The old generic
     *  house's eaves (one block out all round, z-41..-27) go too where nothing earlier stands (the lampposts on z-41, the
     *  park's palms stay). */
    /** The rebuilt lots so far {x1, x2, z1, z2} (rows A + B): lawnDecor leaves their designed yards alone. */
    private static final List<int[]> UA_LOTS = new ArrayList<>();

    private static void uaLot(int x1, int x2, int pathX) {
        UA_LOTS.add(new int[]{x1, x2, -41, -12});
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = -41; z <= -12; z++) {
                if (x >= x1 && x <= x2 && z >= -40) {
                    for (int y = UG + 1; y <= 112; y++) set(x, y, z, AIR);
                    set(x, UG, z, x == pathX && z <= -38 ? ((z & 1) == 0 ? GRAVEL : DIRT_PATH) : GRASS);
                    set(x, UG - 1, z, DIRT);
                } else if (z <= -27) {                                                // the old eaves: only where no earlier pass built
                    for (int y = UG + 1; y <= 100; y++) if (getRaw(x, y, z) == 0) set(x, y, z, AIR);
                }
            }
        if (Math.floorMod(pathX, 16) != 0) set(pathX, UG, -41, DIRT_PATH);                 // on across the lamp row
    }

    /** Grow the current label box over a whole upper-row lot (+1 for the old eaves, the lamp row in front). */
    private static void uaBox(int lx1, int lx2) {
        int[] box = LABELS.get(CUR);
        box[0] = Math.min(box[0], lx1 - 1); box[1] = Math.min(box[1], -41); box[2] = Math.max(box[2], lx2 + 1); box[3] = Math.max(box[3], -12);
    }

    /** A gable roof (ridge along x) in any stair block (modded ones too): stairs climbing from both long sides. */
    private static void gableRoofXId(int x1, int x2, int z1, int z2, int yBase, String stairId, int planks) {
        int n = z1 - 1, s = z2 + 1, y = yBase;
        while (n <= s) {
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                if (n == s) { set(x, y, n, planks); continue; }
                set(x, y, n, id(stairId + "[facing=south]"));
                set(x, y, s, id(stairId + "[facing=north]"));
                if (n > z1) set(x, y - 1, n, planks);
                if (s < z2) set(x, y - 1, s, planks);
            }
            n++; s--; y++;
        }
    }

    /** A wall banner with patterns (block-entity data in the form the game saves; colours are dye ids). */
    private static void patternBanner(int x, int y, int z, String base, String facing, Object... colourPattern) {
        set(x, y, z, wallBanner(base, facing));
        StringBuilder sb = new StringBuilder("{Patterns:[");
        for (int i = 0; i < colourPattern.length; i += 2)
            sb.append(i > 0 ? "," : "").append("{Color:").append(colourPattern[i]).append(",Pattern:\"").append(colourPattern[i + 1]).append("\"}");
        nbt(x, y, z, sb.append("]}").toString());
    }

    /** A ceiling-hung hanging sign with text, readable from the street (both sides carry it). */
    private static void hangingSign(int x, int y, int z, String wood, String... lines) {
        set(x, y, z, id("minecraft:" + wood + "_hanging_sign[rotation=0,attached=false]"));
        signText(x, y, z, lines);
    }

    /*
     * #29 THE GLASSBLOWER (x108..120): a Venetian-looking glassworks - salmon walls (smooth red sandstone) on dark-oak posts,
     * a red clay-tile (brick) roof with its gable to the street, and a RAINBOW SHOP WINDOW full of ships in bottles. Behind
     * it the BOTTLE KILN, the town's odd landmark: a brick bottle oven (wide base with iron hoops, a curving shoulder, a tall
     * neck) with the glory-hole furnace in its heart - four lit blast furnaces round a fire whose smoke climbs out of the
     * neck. Inside the shop: the counter, the display shelves, the marver, the blowpipes, the annealing oven; upstairs the
     * colour room (stacks of coloured glass) and the glassblower's bed. The back yard: sand, the cullet heap, potash,
     * firewood; out front, glass sculptures on posts.
     */
    private static final int GBG = 67, GBF2 = 72, GBR = 77, GBKX = 117, GBKZ = -7;

    /** The bottle kiln's radius at height y (base, shoulder, neck; the rim is drawn on its own). */
    private static double gbR(int y) {
        int h = y - GBG;
        if (h <= 5) return 3.45;
        return switch (h) { case 6 -> 3.3; case 7 -> 3.05; case 8 -> 2.7; case 9 -> 2.3; case 10 -> 1.9; default -> h <= 16 ? 1.5 : h == 17 ? 2.0 : 0; };
    }

    private static void glassblowersKiln() {
        final int x1 = 109, x2 = 115, z1 = -3, z2 = 4;
        thLot(108, 120, 114);
        thEaves(108, 120);
        FOOTPRINTS.add(new int[]{x1, z1, x2, z2});
        FOOTPRINTS.add(new int[]{GBKX - 3, GBKZ - 3, GBKX + 3, GBKZ + 3});
        fill(x1, GBG - 2, z1, x2, GBG - 1, z2, COBBLE);
        int salmon = id("minecraft:smooth_red_sandstone"), cut = id("minecraft:cut_red_sandstone"), brick = id("minecraft:bricks");
        // THE SHOP: salmon walls, dark-oak posts, a beam at the upper floor
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean ex = x == x1 || x == x2, ez = z == z1 || z == z2;
                if (!ex && !ez) {
                    set(x, GBG, z, ((x + z) & 1) == 0 ? id("minecraft:polished_granite") : id("minecraft:terracotta"));
                    set(x, GBF2, z, SPRUCE);
                    continue;
                }
                set(x, GBG, z, STONE_BRICKS);
                for (int y = GBG + 1; y < GBR; y++)
                    set(x, y, z, (ex && ez) ? DARK_OAK_LOG_Y : y == GBG + 1 ? cut
                            : y == GBF2 ? id("minecraft:stripped_dark_oak_log[axis=" + (ez ? "x" : "z") + "]") : salmon);
            }
        gableRoofZ(z1, z2, x1, x2, GBR, "brick", brick);
        gableEndsZ(z1, x1 + 1, x2 - 1, GBR, salmon);
        gableEndsZ(z2, x1 + 1, x2 - 1, GBR, salmon);
        // the rainbow shop window (x110..113), the door, the awning + lantern, the sign; upper + side windows
        String[] glass = {"red", "orange", "yellow", "lime", "light_blue", "blue", "purple", "magenta"};
        for (int x = 110; x <= 113; x++)
            for (int y = GBG + 2; y <= GBG + 4; y++) set(x, y, z2, id("minecraft:" + glass[(x - 110 + y - GBG - 2) % glass.length] + "_stained_glass_pane"));
        for (int x = 110; x <= 113; x++) set(x, GBG + 1, z2 + 1, id("minecraft:cut_red_sandstone_slab[type=top]"));
        innDoor(114, GBG, z2, "dark_oak", true);
        for (int x = x1; x <= 114; x++) set(x, GBG + 5, z2 + 1, id("minecraft:dark_oak_slab[type=top]"));
        hangLantern(x1, GBG + 4, z2 + 1);
        bracketSign(x2, GBG + 4, z2 + 1, "dark_oak", "", "Glass &", "Bottles", "");
        for (int x : new int[]{110, 113})
            for (int dx = 0; dx <= 1; dx++) {
                for (int y = GBF2 + 1; y <= GBF2 + 2; y++) set(x + dx, y, z2, id("minecraft:glass_pane"));
                set(x + dx, GBF2 + 3, z2, stairsTop("red_sandstone", dx == 0 ? "west" : "east"));
            }
        for (int z : new int[]{0, 1})
            for (int y : new int[]{GBG + 2, GBF2 + 1}) {
                for (int dy = 0; dy <= 1; dy++) { set(x1, y + dy, z, id("minecraft:glass_pane")); set(x2, y + dy, z, id("minecraft:glass_pane")); }
            }
        for (int y = GBG + 2; y <= GBG + 3; y++) set(113, y, z1, id("minecraft:glass_pane"));
        innDoor(111, GBG, z1, "dark_oak", true);                                                  // out to the kiln yard
        // the ladder up (north-west corner)
        for (int y = GBG + 1; y <= GBF2; y++) set(110, y, -2, id("minecraft:ladder[facing=east]"));
        for (int y = GBG + 1; y <= GBF2 + 2; y++) innReserve(111, y, -2);

        // THE BOTTLE KILN: base with iron hoops, curving shoulder, the neck, a flared rim; the furnace in its heart
        for (int x = GBKX - 4; x <= GBKX + 4; x++)
            for (int z = GBKZ - 4; z <= GBKZ + 4; z++) {
                double d = Math.hypot(x - GBKX, z - GBKZ);
                if (d > 3.45) continue;
                set(x, GBG, z, brick); set(x, GBG - 1, z, COBBLE); set(x, GBG - 2, z, COBBLE);
                for (int y = GBG + 1; y <= GBG + 17; y++) {
                    int h = y - GBG;
                    double r = gbR(y), rn = gbR(y + 1);
                    boolean shell = h == 17 ? d <= 2.0 && d > 0.5
                            : h >= 11 ? d <= r && d > 0.5
                            : d <= r && (d > r - 1.0 || d > rn - 1.0);
                    if (shell) set(x, y, z, (h == 2 || h == 5) ? id("minecraft:polished_deepslate") : brick);
                    else if (d <= r) set(x, y, z, AIR);
                }
            }
        set(GBKX - 3, GBG + 1, GBKZ, AIR); set(GBKX - 3, GBG + 2, GBKZ, AIR);                        // the kiln's arch
        set(GBKX - 4, GBG + 3, GBKZ, stairsTop("brick", "east"));
        set(GBKX, GBG, GBKZ, HAY);                                                                   // the glory-hole furnace
        set(GBKX, GBG + 1, GBKZ, id("minecraft:campfire[lit=true,signal_fire=true]"));
        String[] fd = {"east", "west", "south", "north"};
        int[][] fo = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int i = 0; i < 4; i++) {
            set(GBKX + fo[i][0], GBG + 1, GBKZ + fo[i][1], id("minecraft:blast_furnace[facing=" + fd[i] + ",lit=true]"));
            set(GBKX + fo[i][0], GBG + 2, GBKZ + fo[i][1], id("minecraft:brick_slab"));
        }
        for (int[] c : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) { set(GBKX + c[0], GBG + 1, GBKZ + c[1], brick); set(GBKX + c[0], GBG + 2, GBKZ + c[1], id("minecraft:brick_slab")); }
        ip(GBKX + 1, GBG + 1, GBKZ - 2, CAULDRON_WATER);                                             // the quench
        ip(GBKX - 1, GBG + 1, GBKZ + 2, barrel("up"));
        for (int x = GBKX + 1; x <= GBKX + 2; x++) ip(x, GBG + 1, GBKZ + 2, id("minecraft:lightning_rod"));   // the blowpipes
        innReserve(GBKX - 2, GBG + 1, GBKZ); innReserve(GBKX - 2, GBG + 2, GBKZ);

        // THE YARDS: the kiln path, sand, the cullet heap, potash, firewood, the trough; out front the glass sculptures
        for (int[] p : new int[][]{{111, -4}, {111, -5}, {112, -5}, {112, -6}, {113, -6}, {113, -7}}) set(p[0], GBG, p[1], GRAVEL);
        set(109, GBG + 1, -9, id("minecraft:sand")); set(110, GBG + 1, -9, id("minecraft:sand")); set(109, GBG + 1, -8, id("minecraft:sand"));
        set(109, GBG + 2, -9, id("minecraft:sand"));
        set(112, GBG + 1, -10, id("minecraft:glass")); set(113, GBG + 1, -10, id("minecraft:tinted_glass")); set(113, GBG + 1, -9, id("minecraft:green_stained_glass"));
        set(112, GBG + 2, -10, id("minecraft:light_blue_stained_glass")); set(111, GBG + 1, -10, id("minecraft:glass"));
        set(109, GBG + 1, -6, barrel("up")); set(109, GBG + 2, -6, barrel("up")); set(109, GBG + 1, -5, barrel("up"));
        for (int z = -7; z <= -6; z++) for (int y = GBG + 1; y <= GBG + 2; y++) set(108, y, z, id("minecraft:oak_log[axis=z]"));   // firewood
        set(113, GBG + 1, -4, CAULDRON_WATER);
        for (int x = 108; x <= 120; x++) set(x, GBG + 1, 7, x == 114 ? id("minecraft:dark_oak_fence_gate[facing=south]") : id("minecraft:red_sandstone_wall"));
        for (int[] s : new int[][]{{110, 6, 0}, {118, 6, 1}, {120, 5, 2}}) {
            set(s[0], GBG + 1, s[1], id("minecraft:red_sandstone_wall"));
            set(s[0], GBG + 2, s[1], id(s[2] == 0 ? "minecraft:amethyst_cluster[facing=up]" : s[2] == 1 ? "minecraft:light_blue_stained_glass" : "minecraft:large_amethyst_bud[facing=up]"));
        }
        for (int x = 117; x <= 119; x++) set(x, GBG + 1, 2, barrel("up"));                           // stock on the side: crates of coloured glass
        set(117, GBG + 2, 2, id("minecraft:red_stained_glass")); set(118, GBG + 2, 2, id("minecraft:yellow_stained_glass")); set(119, GBG + 2, 2, id("minecraft:blue_stained_glass"));
        set(116, GBG + 1, 5, stairs("dark_oak", "north")); set(117, GBG + 1, 5, stairs("dark_oak", "north"));
        set(119, GBG + 1, 5, id("minecraft:potted_flowering_azalea_bush"));
        furnishGlassblower();
        thBox(108, 120);
    }

    private static void furnishGlassblower() {
        final int g = GBG + 1, u = GBF2 + 1;
        // the shop window: ships in bottles on a shelf behind the glass
        String[] ships = {"sloop", "brig", "galleon", "sloop"};
        for (int x = 110; x <= 113; x++) { ip(x, g, 3, slabTop("dark_oak")); ip(x, g + 1, 3, id("pixelpirates:ship_in_bottle_" + ships[x - 110] + "[facing=north]")); }
        // the counter (bell, bottles, a galleon in its bottle)
        for (int x = 110; x <= 112; x++) ip(x, g, 0, slabTop("dark_oak"));
        ip(110, g + 1, 0, id("minecraft:bell[attachment=floor,facing=south]"));
        ip(111, g + 1, 0, id("pixelpirates:spirit_bottles[facing=south,count=3]"));
        ip(112, g + 1, 0, id("pixelpirates:ship_in_bottle_galleon[facing=north]"));
        ip(111, g, 1, id("pixelpirates:barrel_stool[facing=north]"));
        // the workshop: the marver (a steel-topped bench), the annealing oven, the blowpipe rack
        ip(113, g, -2, id("minecraft:polished_blackstone_slab[type=top]")); ip(114, g, -2, id("minecraft:polished_blackstone_slab[type=top]"));
        ip(113, g + 1, -2, id("minecraft:light_blue_stained_glass_pane")); ip(114, g + 1, -2, id("minecraft:lightning_rod"));
        ip(114, g, 0, id("minecraft:smoker[facing=west,lit=true]")); ip(114, g + 1, 0, id("minecraft:brick_slab"));
        ip(114, g, 1, barrel("west")); ip(114, g + 1, 1, id("minecraft:amethyst_cluster[facing=up]"));
        ip(112, g, -2, CAULDRON_WATER);
        hangLantern(112, GBF2 - 1, 1);
        // upstairs: the colour room (coloured glass stacked like bars) + the glassblower's bed
        String[] col = {"red", "orange", "yellow", "lime", "cyan", "purple"};
        for (int z = 0; z <= 3; z++) for (int dy = 0; dy <= 1; dy++) ip(110, u + dy, z, id("minecraft:" + col[(z + dy * 2) % col.length] + "_stained_glass"));
        ip(114, u, -2, bed("orange", "north", true)); ip(114, u, -1, bed("orange", "north", false));
        ip(112, u, -2, chest("south")); ip(111, u, 3, slabTop("dark_oak")); ip(111, u + 1, 3, id("pixelpirates:ship_in_bottle_brig[facing=north]"));
        ip(113, u, 3, slabTop("dark_oak")); ip(113, u + 1, 3, candle("orange", 3));
        rug(111, 0, 113, 2, u, "red", "orange");
        hangLantern(112, GBR + 2, 0);
    }

    /*
     * #30 THE OLD BOATSWAIN (x124..136): the bosun of a ship long broken up lives under her - her UPTURNED HULL is his
     * roof: tarred strakes, the red-and-white painted sheer, barnacles on her bottom (now the top), the keel along the
     * ridge running down into the stem at the back, and the flat stern TRANSOM to the street with her stern windows, the
     * door, the stern lanterns on their beams. Low stone walls carry the gunwale. Inside, one vaulted room under the ribs:
     * his hammock, the stove with its pipe out through the planking, the sea chest, nets hung from the ribs, the chart.
     * The yard: a capstan, the ship's bell on a frame, the anchor, a flagpole of signal flags; behind, lobster pots.
     */
    private static final int BSG = 67, BSGW = 70, BSH = 7, BSX = 130, BST = 2, BSS = -10;     // floor, gunwale, hull height, keel x, transom z, stem z

    /** The hull's half-beam at the gunwale: full aft (the transom end), fining to the stem. */
    private static double bsW(int z) {
        if (z >= -3) return 5.0;
        return 5.0 * Math.cos((-3 - z) / 7.2 * Math.PI / 2);
    }

    /** Half-width of the upturned hull at height y (-1 = above the keel / below the gunwale). */
    private static double bsHw(int z, int y) {
        int h = y - BSGW;
        if (h < 0 || h > BSH) return -1;
        double f = h / (double) BSH;
        return bsW(z) * Math.sqrt(Math.max(0, 1 - f * f));
    }

    private static void boatswainsHull() {
        thLot(124, 136, BSX);
        thEaves(124, 136);
        FOOTPRINTS.add(new int[]{BSX - 5, BSS, BSX + 5, BST});
        Random r = new Random(0xB05L);
        int wale = DARK_OAK_LOG_Z, tar = DARK_OAK, deal = SPRUCE, keel = id("minecraft:stripped_spruce_log[axis=z]");
        // the low walls + floor (the footprint at the gunwale)
        for (int z = BSS; z <= BST; z++)
            for (int x = BSX - 5; x <= BSX + 5; x++) {
                int dx = Math.abs(x - BSX);
                if (dx > bsW(z)) continue;
                set(x, BSG - 1, z, COBBLE); set(x, BSG - 2, z, COBBLE);
                boolean edge = z == BST || z == BSS || dx + 1 > bsW(z) || dx > bsW(z - 1);
                set(x, BSG, z, edge ? COBBLE : SPRUCE);
                for (int y = BSG + 1; y < BSGW; y++) set(x, y, z, edge ? (r.nextInt(3) == 0 ? MOSSY_COBBLE : COBBLE) : AIR);
            }
        // THE HULL, slice by slice (the transom is a flat end; the stem a post)
        for (int z = BSS; z <= BST; z++)
            for (int x = BSX - 5; x <= BSX + 5; x++)
                for (int y = BSGW; y <= BSGW + BSH; y++) {
                    int dx = Math.abs(x - BSX);
                    double hw = bsHw(z, y), hn = bsHw(z, y + 1);
                    if (dx > hw) continue;
                    boolean shell = z == BST || z == BSS || dx > hw - 1.0 || dx > hn - 1.0 || dx > bsHw(z - 1, y);
                    if (!shell) { set(x, y, z, AIR); continue; }
                    int h = y - BSGW, b;
                    if (z == BST) b = h == 3 ? DARK_OAK : deal;                                     // the transom
                    else if (h == BSH) b = keel;
                    else if (h == 0) b = wale;
                    else if (h == 1) b = id("minecraft:red_terracotta");
                    else if (h == 2) b = id("minecraft:white_terracotta");
                    else b = r.nextInt(13) == 0 ? id("minecraft:oak_planks") : r.nextInt(17) == 0 && h >= 5 ? id("pixelpirates:shell_block") : (h & 1) == 1 ? tar : deal;
                    set(x, y, z, b);
                }
        fill(BSX, BSGW, BSS, BSX, BSGW + BSH + 1, BSS, id("minecraft:stripped_spruce_log[axis=y]"));     // the stem post
        set(BSX, BSGW + BSH, BSS + 1, keel);
        // ribs inside, every third frame: the air cells against the planking (found first, then set)
        List<int[]> ribs = new ArrayList<>();
        for (int z : new int[]{-7, -4, -1})
            for (int x = BSX - 5; x <= BSX + 5; x++)
                for (int y = BSGW + 1; y < BSGW + BSH; y++)
                    if (getRaw(x, y, z) == AIR && (getRaw(x - 1, y, z) != AIR || getRaw(x + 1, y, z) != AIR || getRaw(x, y + 1, z) != AIR))
                        ribs.add(new int[]{x, y, z});
        for (int[] c : ribs) set(c[0], c[1], c[2], id("minecraft:stripped_spruce_wood"));
        // the transom: door, stern windows, the sign, the stern lanterns on their beams
        innDoor(BSX, BSG, BST, "spruce", true);
        for (int x : new int[]{BSX - 3, BSX - 2, BSX + 2, BSX + 3})
            for (int y = BSGW + 1; y <= BSGW + 2; y++) set(x, y, BST, id("minecraft:glass_pane"));
        set(BSX, BSGW + 2, BST + 1, id("minecraft:spruce_wall_sign[facing=south]"));
        signText(BSX, BSGW + 2, BST + 1, "", "Rope &", "Rigging", "");
        for (int x : new int[]{BSX - 4, BSX + 4}) { set(x, BSGW + 4, BST + 1, DARK_OAK_LOG_Z); hangLantern(x, BSGW + 3, BST + 1); }
        // the stove + its pipe out through the planking
        set(BSX - 3, BSG + 1, -2, id("minecraft:smoker[facing=east,lit=true]"));
        fill(BSX - 3, BSG + 2, -2, BSX - 3, BSGW + 7, -2, id("minecraft:andesite_wall"));
        set(BSX - 3, BSGW + 8, -2, id("minecraft:andesite_slab"));

        // THE YARD: the bell on its frame, the capstan, the anchor, the flagpole, the fence + gate; lobster pots behind
        for (int x : new int[]{126, 128}) fill(x, BSG + 1, 5, x, BSG + 3, 5, SPRUCE_FENCE);
        for (int x = 126; x <= 128; x++) set(x, BSG + 4, 5, DARK_OAK_LOG_X);
        set(127, BSG + 3, 5, id("minecraft:bell[attachment=ceiling,facing=south]"));
        set(133, BSG + 1, 5, id("minecraft:stripped_dark_oak_log[axis=y]")); set(133, BSG + 2, 5, id("minecraft:dark_oak_slab"));
        set(134, BSG + 1, 5, id("minecraft:lightning_rod[facing=east]")); set(132, BSG + 1, 5, id("minecraft:lightning_rod[facing=west]"));
        set(133, BSG + 1, 6, id("minecraft:lightning_rod[facing=south]")); set(133, BSG + 1, 4, id("minecraft:lightning_rod[facing=north]"));
        set(135, BSG + 1, 3, ANCHOR_BLOCK); set(135, BSG + 1, 4, id("minecraft:chain[axis=z]")); set(135, BSG + 1, 5, id("minecraft:chain[axis=z]"));
        fill(124, BSG + 1, 6, 124, BSG + 11, 6, id("minecraft:stripped_spruce_log[axis=y]"));       // the flagpole: a hoist of signal flags
        String[] flags = {"yellow", "blue", "red", "white"};
        for (int i = 0; i < 4; i++) set(125, BSG + 10 - i, 6, wool(flags[i]));
        for (int x = 124; x <= 136; x++) set(x, BSG + 1, 7, x == BSX ? id("minecraft:spruce_fence_gate[facing=south]") : SPRUCE_FENCE);
        set(129, BSG + 1, 4, barrel("up")); set(129, BSG + 2, 4, id("pixelpirates:rope_coil[facing=south]"));
        set(131, BSG + 1, 6, id("pixelpirates:rope_coil[facing=east]"));
        set(125, BSG + 1, -8, id("pixelpirates:lobster_pot")); set(126, BSG + 1, -9, id("pixelpirates:lobster_pot"));
        set(135, BSG + 1, -8, id("pixelpirates:fish_trap")); set(134, BSG + 1, -9, barrel("up"));
        furnishBoatswain();
        thBox(124, 136);
    }

    private static void furnishBoatswain() {
        final int g = BSG + 1;
        ip(BSX + 1, g, -6, hammock("east"));
        ip(BSX + 3, g, 0, id("pixelpirates:sea_chest[facing=west]"));
        ip(BSX + 3, g, -2, barrel("west")); ip(BSX + 3, g + 1, -2, id("pixelpirates:rope_coil[facing=west]"));
        ip(BSX - 2, g, -5, slabTop("spruce")); ip(BSX - 2, g + 1, -5, LANTERN); ip(BSX - 1, g, -5, stairs("spruce", "east"));
        ip(BSX - 2, BSGW, 1, id("pixelpirates:sea_chart[facing=north]"));
        ip(BSX - 3, g, 0, barrel("up")); ip(BSX - 3, g + 1, 0, id("pixelpirates:spirit_bottles[facing=east,count=2]"));
        ip(BSX + 1, BSGW + 5, -5, id("pixelpirates:hanging_net")); ip(BSX - 1, BSGW + 5, -2, id("pixelpirates:hanging_net"));
        ip(BSX, g, -8, id("pixelpirates:rope_coil[facing=north]"));
        rug(BSX - 1, -3, BSX + 1, 0, g, "brown", "red");
        hangLantern(BSX, BSGW + 5, -2);
    }

    /*
     * #31 THE MILLER'S WINDMILL (x-136..-124, the upper row's west end): a whitewashed TOWER MILL - a round tower of
     * diorite on a cobbled foot, tapering as it climbs, a reefing STAGE (railed gallery) round its middle, a boat-shaped
     * dark-oak CAP, and four great SAILS (cloth on lattice frames) set as an X on the windshaft over the street. Inside, up
     * a ladder on the main post: the sack floor (flour, the sack hoist on its chain), the stone floor (two millstones fed
     * by hoppers), the bin floor (grain, the stage door) and in the cap the windshaft with its brake wheel. Behind the
     * mill the miller's thatched cottage (oven, flour bins, the bread on the table) and a strip of wheat with a scarecrow.
     * The gate arch carries the sign. The bakery is supplied from here.
     */
    private static final int WMX = -130, WMZ = -31, WMHY = 88, WMSZ = -38;            // tower centre, hub height, sail plane

    private static double wmR(int y) { return 4.4 - (y - 71) * 0.1; }

    private static void millersWindmill() {
        uaLot(-136, -124, WMX);
        FOOTPRINTS.add(new int[]{WMX - 4, WMZ - 4, WMX + 4, WMZ + 4});
        FOOTPRINTS.add(new int[]{-134, -23, -126, -16});
        Random r = new Random(0x3111L);
        int white = id("minecraft:diorite"), band = id("minecraft:polished_diorite"), plank = SPRUCE;
        // THE TOWER: floors 70 / 75 / 80 / 85 (the dust floor under the cap)
        for (int x = WMX - 5; x <= WMX + 5; x++)
            for (int z = WMZ - 5; z <= WMZ + 5; z++) {
                double d = Math.hypot(x - WMX, z - WMZ);
                if (d > wmR(71)) continue;
                set(x, UG - 1, z, COBBLE); set(x, UG - 2, z, COBBLE);
                set(x, UG, z, d > wmR(71) - 1.1 ? COBBLE : plank);
                for (int y = UG + 1; y <= 85; y++) {
                    double rr = wmR(y);
                    if (d > rr) continue;
                    if (d > rr - 1.1) set(x, y, z, y <= 73 ? (r.nextInt(3) == 0 ? MOSSY_COBBLE : COBBLE) : (y == 75 || y == 80 || y == 85) ? band : white);
                    else set(x, y, z, (y == 75 || y == 80 || y == 85) ? plank : AIR);
                }
            }
        // the main post, its ladder (west face, holes through the floors), the sack-hoist chain (east)
        fill(WMX, UG + 1, WMZ, WMX, 84, WMZ, id("minecraft:stripped_spruce_log[axis=y]"));
        for (int y = UG + 1; y <= 85; y++) { set(WMX - 1, y, WMZ, id("minecraft:ladder[facing=west]")); innReserve(WMX - 2, y, WMZ); }
        for (int y = UG + 2; y <= 84; y++) set(WMX + 1, y, WMZ, CHAIN);
        set(WMX + 1, UG + 1, WMZ, barrel("up"));
        // doors: north (the street), south (the cottage), the stage door
        innDoor(WMX, UG, WMZ - 4, "spruce", true);
        innDoor(WMX, UG, WMZ + 4, "spruce", true);
        innDoor(WMX, 80, WMZ - 3, "spruce", true);
        // small windows on each floor (east, west, south)
        for (int y : new int[]{77, 82}) {
            int rr = (int) Math.round(wmR(y) - 0.5);
            for (int[] w : new int[][]{{rr, 0}, {-rr, 0}, {0, rr}}) set(WMX + w[0], y, WMZ + w[1], id("minecraft:glass_pane"));
        }
        // THE STAGE: a railed gallery of slabs round the tower at the bin floor (y80)
        for (int x = WMX - 6; x <= WMX + 6; x++)
            for (int z = WMZ - 6; z <= WMZ + 6; z++) {
                double d = Math.hypot(x - WMX, z - WMZ);
                if (d <= wmR(80) || d > 5.3) continue;
                set(x, 80, z, id("minecraft:spruce_slab[type=top]"));
                if (d > 4.6) set(x, 81, z, SPRUCE_FENCE);
            }
        // THE CAP: a boat-shaped dark-oak roof over the tower top, the windshaft out through its north gable to the hub
        gableRoofZ(WMZ - 3, WMZ + 3, WMX - 3, WMX + 3, 86, "dark_oak", DARK_OAK);
        gableEndsZ(WMZ - 3, WMX - 2, WMX + 2, 86, DARK_OAK);
        gableEndsZ(WMZ + 3, WMX - 2, WMX + 2, 86, DARK_OAK);
        for (int z = WMZ - 5; z <= WMZ + 2; z++) set(WMX, WMHY, z, SPRUCE_LOG_Z_ID());
        set(WMX, WMHY, WMSZ + 1, id("minecraft:stripped_dark_oak_wood"));
        for (int[] c : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, -1}, {-1, -1}})                    // the brake wheel (its top corners would cut the roof)
            set(WMX + c[0], WMHY + c[1], WMZ, c[0] == 0 || c[1] == 0 ? id("minecraft:stripped_spruce_wood") : plank);
        // THE SAILS: four arms in an X on the plane z = WMSZ - stock, lattice bars, cloth
        for (int x = WMX - 8; x <= WMX + 8; x++)
            for (int y = WMHY - 8; y <= WMHY + 8; y++) {
                int dx = x - WMX, dy = y - WMHY;
                int put = 0;                                                                    // 0 none, 1 stock, 2 bar, 3 cloth
                for (int k = 0; k < 4 && put == 0; k++) {
                    double a = Math.toRadians(45 + 90 * k), cx = Math.cos(a), cy = Math.sin(a);
                    double along = dx * cx + dy * cy, perp = -dx * cy + dy * cx;
                    if (along < -0.3 || along > 7.6) continue;
                    if (Math.abs(perp) <= 0.75) put = 1;
                    else if (perp > 0.75 && perp <= 2.7 && along >= 1.8) put = (perp > 2.25 || along > 7.1 || Math.floorMod((int) Math.round(along), 3) == 0) ? 2 : 3;
                }
                if (put == 0) continue;
                set(x, y, WMSZ, put == 1 ? id("minecraft:stripped_dark_oak_wood") : put == 2 ? id("minecraft:stripped_spruce_wood") : WHITE_WOOL);
            }
        // THE GATE ARCH with the sign, the fence; the front: flour sacks waiting for the bakery cart
        for (int x : new int[]{WMX - 2, WMX + 2}) fill(x, UG + 1, -40, x, UG + 3, -40, SPRUCE_FENCE);
        for (int x = WMX - 2; x <= WMX + 2; x++) set(x, UG + 4, -40, DARK_OAK_LOG_X);
        hangingSign(WMX, UG + 3, -40, "spruce", "", "Mill &", "Flour", "");
        for (int x = -136; x <= -124; x++) if (Math.abs(x - WMX) != 2) set(x, UG + 1, -40, x == WMX ? id("minecraft:spruce_fence_gate[facing=north]") : SPRUCE_FENCE);
        for (int z = -37; z <= -36; z++) set(WMX, UG, z, ((z & 1) == 0) ? GRAVEL : DIRT_PATH);
        for (int[] s : new int[][]{{-134, -38}, {-133, -38}, {-134, -37}}) set(s[0], UG + 1, s[1], WHITE_WOOL);
        set(-134, UG + 2, -38, id("minecraft:white_carpet"));
        set(-126, UG + 1, -38, HAY); set(-125, UG + 1, -38, HAY); set(-126, UG + 2, -38, HAY);

        // THE MILLER'S COTTAGE (x-134..-126, z-23..-16): oak frame, white daub, a thatched roof, the chimney east
        final int cx1 = -134, cx2 = -126, cz1 = -23, cz2 = -16;
        int daub = id("minecraft:white_terracotta");
        fill(cx1, UG - 2, cz1, cx2, UG - 1, cz2, COBBLE);
        for (int x = cx1; x <= cx2; x++)
            for (int z = cz1; z <= cz2; z++) {
                boolean ex = x == cx1 || x == cx2, ez = z == cz1 || z == cz2;
                set(x, UG, z, ex || ez ? COBBLE : SPRUCE);
                for (int y = UG + 1; y <= UG + 4; y++)
                    set(x, y, z, !ex && !ez ? AIR : (ex && ez) || (ez && (x - cx1) % 4 == 0) || (ex && (z - cz1) % 4 == 0) ? id("minecraft:oak_log[axis=y]")
                            : y == UG + 1 ? COBBLE : y == UG + 4 ? id("minecraft:stripped_oak_log[axis=" + (ez ? "x" : "z") + "]") : daub);
            }
        gableRoofXId(cx1, cx2, cz1, cz2, UG + 5, "pixelpirates:thatch_stairs", id("pixelpirates:thatch"));
        gableEndsX(cx1, cz1 + 1, cz2 - 1, UG + 5, daub);
        gableEndsX(cx2, cz1 + 1, cz2 - 1, UG + 5, daub);
        innDoor(WMX, UG, cz1, "oak", true);
        for (int x : new int[]{-132, -128}) for (int y = UG + 2; y <= UG + 3; y++) { set(x, y, cz1, id("minecraft:glass_pane")); set(x, y, cz2, id("minecraft:glass_pane")); }
        fill(cx2 + 1, UG + 1, -21, cx2 + 1, UG + 10, -19, id("minecraft:cobblestone"));               // the chimney (east, 1 deep)
        set(cx2 + 1, UG + 1, -20, CAMPFIRE); fill(cx2 + 1, UG + 2, -20, cx2 + 1, UG + 10, -20, AIR);
        set(cx2, UG + 1, -20, AIR); set(cx2, UG + 2, -20, AIR);
        // the yard between: the path, a cart of sacks, the spare millstone; behind: the wheat strip + scarecrow
        for (int z = WMZ + 5; z < cz1; z++) set(WMX, UG, z, ((z & 1) == 0) ? GRAVEL : DIRT_PATH);
        set(-133, UG + 1, -25, id("pixelpirates:cargo_crate[facing=east]")); set(-133, UG + 2, -25, WHITE_WOOL);
        set(-134, UG + 1, -25, id("minecraft:dark_oak_trapdoor[facing=west,half=bottom,open=true]"));
        set(-126, UG + 1, -25, id("minecraft:grindstone[face=floor,facing=north]"));
        for (int x = -135; x <= -125; x++)
            for (int z = -14; z <= -12; z++) {
                if (z == -13) { set(x, UG, z, WATER); set(x, UG - 1, z, DIRT); continue; }
                set(x, UG, z, id("minecraft:farmland[moisture=7]"));
                set(x, UG + 1, z, id("minecraft:wheat[age=" + (5 + Math.floorMod(x * 7 + z, 3)) + "]"));
            }
        set(-130, UG + 1, -14, OAK_FENCE); set(-130, UG + 2, -14, HAY); set(-130, UG + 3, -14, id("minecraft:carved_pumpkin[facing=north]"));
        set(-131, UG + 2, -14, OAK_FENCE); set(-129, UG + 2, -14, OAK_FENCE);
        furnishWindmill(r);
        uaBox(-136, -124);
    }

    private static int SPRUCE_LOG_Z_ID() { return id("minecraft:spruce_log[axis=z]"); }

    private static void furnishWindmill(Random r) {
        // the sack floor (y71): sacks + barrels of flour round the wall, the scales
        for (int[] c : new int[][]{{WMX + 2, WMZ - 2}, {WMX + 3, WMZ - 1}, {WMX + 3, WMZ + 1}, {WMX - 2, WMZ + 2}})
            if (ip(c[0], UG + 1, c[1], WHITE_WOOL)) ip(c[0], UG + 2, c[1], id("minecraft:white_carpet"));
        ip(WMX - 2, UG + 1, WMZ - 2, barrel("up")); ip(WMX - 3, UG + 1, WMZ - 1, barrel("up")); ip(WMX - 3, UG + 1, WMZ + 1, HAY);
        hangLantern(WMX + 2, 74, WMZ + 2);
        // the stone floor (y76): two millstones fed by hoppers, a sack under each spout
        for (int[] c : new int[][]{{WMX + 2, WMZ}, {WMX, WMZ + 2}}) {
            ip(c[0], 76, c[1], id("minecraft:grindstone[face=floor,facing=north]"));
            ip(c[0], 77, c[1], id("minecraft:hopper[facing=down]"));
        }
        ip(WMX + 2, 76, WMZ - 2, WHITE_WOOL); ip(WMX - 2, 76, WMZ + 2, barrel("up"));
        hangLantern(WMX - 2, 79, WMZ - 2);
        // the bin floor (y81): grain bins, sacks
        ip(WMX + 2, 81, WMZ + 1, HAY); ip(WMX + 2, 82, WMZ + 1, HAY); ip(WMX - 2, 81, WMZ + 1, barrel("up")); ip(WMX + 1, 81, WMZ + 2, HAY);
        hangLantern(WMX + 1, 84, WMZ - 2);
        // the cottage: the bread oven, the table with a loaf (a cake), the bed, flour bins, the dresser
        final int g = UG + 1;
        ip(-133, g, -22, id("minecraft:smoker[facing=east,lit=true]")); ip(-133, g, -21, barrel("east")); ip(-133, g + 1, -21, id("minecraft:decorated_pot"));
        ip(-130, g, -19, slabTop("oak")); ip(-129, g, -19, slabTop("oak")); ip(-130, g + 1, -19, id("minecraft:cake"));
        ip(-129, g + 1, -19, candle(2)); ip(-130, g, -18, stairs("oak", "south")); ip(-129, g, -18, stairs("oak", "south"));
        ip(-133, g, -17, bed("white", "west", true)); ip(-132, g, -17, bed("white", "west", false));
        ip(-128, g, -17, chest("north")); ip(-127, g, -22, barrel("up")); ip(-127, g + 1, -22, HAY);
        rug(-131, -22, -128, -20, g, "yellow", "brown");
        hangLantern(-130, UG + 4, -20);
    }

    /*
     * #32 THE SAILMAKER'S LOFT (x-120..-108): a tall, narrow board-and-batten loft (spruce boards, stripped-spruce battens,
     * dark-oak floor bands) under a GAMBREL roof of slate (deepslate tiles) - the only barn-roofed house in town - its
     * gable to the street with the LOFT DOOR high up, a jib beam over it and a rolled sail on the hoist. Ground floor: the
     * store (bolts of canvas on racks, rope, the looms); first floor: the cutting floor (a long table spread with canvas);
     * the loft under the roof: THE SAIL LOFT, a half-made sail hung from its yard across the room. The back yard: sails
     * drying on lines between posts - a patched cream one and a red-and-white striped one.
     */
    private static final int SLX1 = -118, SLX2 = -110, SLZ1 = -38, SLZ2 = -24, SLC = -114;
    private static final int[] SL_TOP = {81, 83, 85, 86, 87, 87};          // gambrel: roof top per column k (0 = the eave) ..k5 the ridge
    private static final int[] SL_BOT = {81, 81, 84, 86, 87, 87};

    private static void sailmakersLoft() {
        uaLot(-120, -108, SLC);
        FOOTPRINTS.add(new int[]{SLX1, SLZ1, SLX2, SLZ2});
        int board = SPRUCE, batten = id("minecraft:stripped_spruce_log[axis=y]"), slate = id("minecraft:deepslate_tiles");
        fill(SLX1, UG - 2, SLZ1, SLX2, UG - 1, SLZ2, COBBLE);
        for (int x = SLX1; x <= SLX2; x++)
            for (int z = SLZ1; z <= SLZ2; z++) {
                boolean ex = x == SLX1 || x == SLX2, ez = z == SLZ1 || z == SLZ2;
                if (!ex && !ez) {
                    set(x, UG, z, id("minecraft:stone_bricks")); set(x, 75, z, SPRUCE); set(x, 80, z, id("minecraft:birch_planks"));
                    for (int y = UG + 1; y <= 79; y++) if (y != 75) set(x, y, z, AIR);
                    continue;
                }
                set(x, UG, z, STONE_BRICKS);
                int u = ez ? x - SLX1 : z - SLZ1;
                for (int y = UG + 1; y <= 80; y++)
                    set(x, y, z, (ex && ez) ? DARK_OAK_LOG_Y : y == UG + 1 ? STONE_BRICKS
                            : (y == 75 || y == 80) ? DARK_OAK : (u & 1) == 0 ? batten : board);
            }
        // THE GAMBREL ROOF (ridge along z): a steep lower slope, a shallow upper one; the gable ends filled under it
        for (int x = SLX1 - 1; x <= SLX2 + 1; x++) {
            int k = Math.min(x - (SLX1 - 1), (SLX2 + 1) - x);
            String up = x < SLC ? "east" : "west";
            for (int z = SLZ1 - 1; z <= SLZ2 + 1; z++) {
                for (int y = SL_BOT[k]; y <= SL_TOP[k]; y++) {
                    boolean top = y == SL_TOP[k];
                    set(x, y, z, k == 5 ? slate : top ? stairs("deepslate_tile", up) : slate);
                }
                if (k == 5) set(x, SL_TOP[k] + 1, z, id("minecraft:deepslate_tile_slab"));
                if ((z == SLZ1 || z == SLZ2) && k >= 1)                                       // the gable ends under the roof
                    for (int y = 81; y < SL_BOT[k]; y++) set(x, y, z, (k & 1) == 0 ? batten : board);
                if (z > SLZ1 && z < SLZ2 && k >= 1) for (int y = 81; y < SL_BOT[k]; y++) set(x, y, z, AIR);
            }
        }
        for (int z : new int[]{-34, -30, -26}) { set(SLX1, 82, z, id("minecraft:glass_pane")); set(SLX2, 82, z, id("minecraft:glass_pane")); }   // roof lights in the steep slope
        // the street gable: wagon doors, windows, the LOFT DOOR with its jib beam + a rolled sail on the hoist, the sign
        set(SLC - 1, UG + 1, SLZ1, id("minecraft:spruce_door[facing=north,half=lower,hinge=left]")); set(SLC - 1, UG + 2, SLZ1, id("minecraft:spruce_door[facing=north,half=upper,hinge=left]"));
        set(SLC, UG + 1, SLZ1, id("minecraft:spruce_door[facing=north,half=lower,hinge=right]")); set(SLC, UG + 2, SLZ1, id("minecraft:spruce_door[facing=north,half=upper,hinge=right]"));
        set(SLC - 1, UG + 3, SLZ1, DARK_OAK_LOG_X); set(SLC, UG + 3, SLZ1, DARK_OAK_LOG_X);
        for (int s : new int[]{-1, 1}) for (int dx : new int[]{-1, 0}) for (int dy = 1; dy <= 2; dy++) innReserve(SLC + dx, UG + dy, SLZ1 + s);
        for (int x : new int[]{SLX1 + 1, SLX2 - 1}) for (int y : new int[]{UG + 2, 76}) for (int dy = 0; dy <= 1; dy++) set(x, y + dy, SLZ1, id("minecraft:glass_pane"));
        for (int x = SLC - 1; x <= SLC + 1; x++) for (int y = 81; y <= 82; y++) set(x, y, SLZ1, AIR);
        for (int x : new int[]{SLC - 2, SLC + 2}) for (int y = 81; y <= 82; y++) set(x, y, SLZ1 - 1, id("minecraft:spruce_trapdoor[facing=north,half=bottom,open=true]"));
        for (int z = SLZ1 - 1; z >= SLZ1 - 2; z--) set(SLC, 84, z, DARK_OAK_LOG_Z);
        for (int y = 81; y <= 83; y++) set(SLC, y, SLZ1 - 2, CHAIN);
        set(SLC, 80, SLZ1 - 2, WHITE_WOOL); set(SLC, 79, SLZ1 - 2, id("pixelpirates:rope_coil[facing=north]"));
        bracketSign(SLX1 + 1, UG + 4, SLZ1 - 1, "spruce", "", "Sails &", "Canvas", "");
        set(SLX1 + 1, UG + 4, SLZ1, board);
        // side windows, the back door to the drying yard
        for (int z = SLZ1 + 3; z <= SLZ2 - 3; z += 4) for (int y : new int[]{UG + 2, 76}) for (int dy = 0; dy <= 1; dy++) {
            set(SLX1, y + dy, z, id("minecraft:glass_pane")); set(SLX2, y + dy, z, id("minecraft:glass_pane"));
        }
        innDoor(SLC, UG, SLZ2, "spruce", true);
        // the ladders up (back-west corner, through both floors)
        for (int y = UG + 1; y <= 80; y++) { set(SLX1 + 1, y, SLZ2 - 1, id("minecraft:ladder[facing=east]")); innReserve(SLX1 + 2, y, SLZ2 - 1); }
        // THE DRYING YARD: posts, lines, sails hung to dry; a bench to sew on; the fence + gate out front
        for (int z : new int[]{-20, -15}) {
            for (int x : new int[]{SLX1, SLX2}) fill(x, UG + 1, z, x, UG + 7, z, SPRUCE_FENCE);
            for (int x = SLX1 + 1; x <= SLX2 - 1; x++) set(x, UG + 7, z, id("minecraft:chain[axis=x]"));
            for (int x = SLX1 + 2; x <= SLX2 - 2; x++)
                for (int y = UG + 4; y <= UG + 6; y++)
                    set(x, y, z, z == -20 ? ((x == SLC + 1 && y == UG + 5) ? id("minecraft:light_gray_wool") : WHITE_WOOL)
                            : ((x & 1) == 0 ? id("minecraft:red_wool") : WHITE_WOOL));
        }
        for (int x = -119; x <= -117; x++) set(x, UG + 1, -13, stairs("spruce", "south"));
        set(-116, UG + 1, -13, barrel("up")); set(-116, UG + 2, -13, id("pixelpirates:rope_coil[facing=south]"));
        set(-111, UG + 1, -13, id("minecraft:loom[facing=north]"));
        for (int x = -120; x <= -108; x++) set(x, UG + 1, -40, x == SLC ? id("minecraft:spruce_fence_gate[facing=north]") : SPRUCE_FENCE);
        for (int z = -39; z <= -39; z++) set(SLC, UG, z, DIRT_PATH);
        furnishSailLoft();
        uaBox(-120, -108);
    }

    private static void furnishSailLoft() {
        final int g = UG + 1, u = 76, l = 81;
        // the store: bolts of canvas on racks (west), rope, the looms (east)
        for (int z = SLZ1 + 2; z <= SLZ2 - 3; z += 2) { ip(SLX1 + 1, g, z, slabTop("spruce")); ip(SLX1 + 1, g + 1, z, WHITE_WOOL); ip(SLX1 + 1, g + 2, z, id("minecraft:white_carpet")); }
        for (int z = SLZ1 + 3; z <= SLZ2 - 4; z += 2) { ip(SLX1 + 1, g, z, barrel("east")); ip(SLX1 + 1, g + 1, z, id("pixelpirates:rope_coil[facing=east]")); }
        ip(SLX2 - 1, g, SLZ1 + 4, id("minecraft:loom[facing=west]")); ip(SLX2 - 1, g, SLZ1 + 6, id("minecraft:loom[facing=west]"));
        ip(SLX2 - 1, g, SLZ1 + 8, chest("west")); ip(SLX2 - 1, g, SLZ2 - 2, barrel("west"));
        hangLantern(SLC, 74, SLZ1 + 6); hangLantern(SLC, 74, SLZ2 - 4);
        // the cutting floor: the long table spread with canvas, stools; the sailmaker's bed + chest
        for (int z = SLZ1 + 3; z <= SLZ1 + 9; z++) { ip(SLC, u, z, slabTop("birch")); ip(SLC, u + 1, z, id("minecraft:white_carpet")); }
        for (int z = SLZ1 + 4; z <= SLZ1 + 8; z += 2) { ip(SLC - 1, u, z, id("pixelpirates:barrel_stool[facing=east]")); ip(SLC + 1, u, z, id("pixelpirates:barrel_stool[facing=west]")); }
        ip(SLX2 - 1, u, SLZ2 - 2, bed("blue", "south", true)); ip(SLX2 - 1, u, SLZ2 - 3, bed("blue", "south", false));
        ip(SLX2 - 1, u, SLZ2 - 5, id("pixelpirates:sea_chest[facing=west]"));
        hangLantern(SLC, 79, SLZ1 + 6); hangLantern(SLC + 2, 79, SLZ2 - 3);
        // THE SAIL LOFT: a half-made sail on its yard across the room (hung from the ridge), bundles, the palm-and-needle bench
        int sz = SLZ1 + 8;
        for (int x = SLC - 2; x <= SLC + 2; x++) set(x, 84, sz, DARK_OAK_LOG_X);
        for (int y = 85; y <= 86; y++) set(SLC, y, sz, CHAIN);
        for (int x = SLC - 2; x <= SLC + 1; x++) for (int y = 82; y <= 83; y++) set(x, y, sz, x == SLC ? id("minecraft:red_wool") : WHITE_WOOL);   // a gap east to pass
        ip(SLC - 2, l, SLZ1 + 2, WHITE_WOOL); ip(SLC - 1, l, SLZ1 + 2, WHITE_WOOL); ip(SLC + 2, l, SLZ2 - 3, WHITE_WOOL);
        ip(SLC + 2, l, SLZ1 + 4, slabTop("spruce")); ip(SLC + 2, l + 1, SLZ1 + 4, candle(2));
        ip(SLC - 2, l, SLZ2 - 4, hammock("north"));
        hangLantern(SLC, 86, SLZ2 - 3);
    }

    /*
     * #33 THE TATTOOIST (x-104..-92): a dark half-timbered parlour whose upper floor JUTS over the street on carved
     * corbels: dark-oak frame, blackened ground storey on polished blackstone, red-lacquered (red terracotta) panels and
     * paper-screen windows above, a blackstone roof. Paper lanterns (shroomlights) hang under the jetty beside the sign.
     * Inside: the FLASH WALL (patterned banners - skulls, roses, a sea serpent, the globe), the tattoo chair, the tray of
     * needles and inks, the waiting bench; upstairs the tattooist's room with a tiger-stripe rug. The back yard: a bamboo
     * grove, a stone lantern, a pond with lily pads, a bench.
     */
    private static final int TTX1 = -102, TTX2 = -94, TTZ1 = -37, TTZ2 = -28, TTF = 75, TTR = 80;

    private static void tattooParlour() {
        uaLot(-104, -92, -96);
        FOOTPRINTS.add(new int[]{TTX1, TTZ1 - 1, TTX2, TTZ2});
        int lac = id("minecraft:red_terracotta"), black = id("minecraft:polished_blackstone_bricks");
        fill(TTX1, UG - 2, TTZ1 - 1, TTX2, UG - 1, TTZ2, COBBLE);
        // ground storey (z-37..-28) + the jettied upper storey (z-38..-28)
        for (int x = TTX1; x <= TTX2; x++)
            for (int z = TTZ1 - 1; z <= TTZ2; z++) {
                boolean ex = x == TTX1 || x == TTX2, post = ex || x == -98;
                boolean gz = z == TTZ1 || z == TTZ2, uz = z == TTZ1 - 1 || z == TTZ2;
                if (z >= TTZ1) {
                    if (!ex && !gz) { set(x, UG, z, DARK_OAK); for (int y = UG + 1; y < TTF; y++) set(x, y, z, AIR); }
                    else {
                        set(x, UG, z, black);
                        for (int y = UG + 1; y < TTF; y++) set(x, y, z, (ex && gz) || (gz && post) ? DARK_OAK_LOG_Y : y == UG + 1 ? black : DARK_OAK);
                    }
                }
                set(x, TTF, z, z == TTZ1 - 1 ? DARK_OAK_LOG_X : DARK_OAK);                             // the upper floor (+ the jetty)
                boolean uwall = ex || uz;
                for (int y = TTF + 1; y < TTR; y++)
                    set(x, y, z, !uwall ? AIR : (ex && uz) || (uz && post) || (ex && (z - TTZ1) % 3 == 0) ? DARK_OAK_LOG_Y
                            : y == TTF + 1 || y == TTR - 1 ? id("minecraft:stripped_dark_oak_log[axis=" + (uz ? "x" : "z") + "]") : lac);
            }
        for (int x : new int[]{TTX1, -98, TTX2}) set(x, TTF - 1, TTZ1 - 1, stairsTop("dark_oak", "south"));        // the corbels
        gableRoofX(TTX1, TTX2, TTZ1 - 1, TTZ2, TTR, "blackstone", POLISHED_BLACKSTONE);
        gableEndsX(TTX1, TTZ1, TTZ2 - 1, TTR, lac);
        gableEndsX(TTX2, TTZ1, TTZ2 - 1, TTR, lac);
        // the shop window + door; paper lanterns + the hanging sign under the jetty; paper-screen windows above
        for (int x = -101; x <= -99; x++) for (int y = UG + 2; y <= UG + 3; y++) set(x, y, TTZ1, id("minecraft:glass_pane"));
        innDoor(-96, UG, TTZ1, "dark_oak", true);
        for (int x : new int[]{-101, -95}) { set(x, TTF - 1, TTZ1 - 1, CHAIN); set(x, TTF - 2, TTZ1 - 1, id("minecraft:shroomlight")); }
        hangingSign(-99, TTF - 1, TTZ1 - 1, "dark_oak", "", "Ink &", "Needle", "");
        for (int x : new int[]{-101, -100, -96, -95}) for (int y = TTF + 2; y <= TTF + 3; y++) set(x, y, TTZ1 - 1, id("minecraft:white_stained_glass_pane"));
        for (int x : new int[]{-102, -94}) for (int y = TTF + 2; y <= TTF + 3; y++) set(x, y, -33, id("minecraft:white_stained_glass_pane"));
        innDoor(-98, UG, TTZ2, "dark_oak", true);
        for (int y = UG + 1; y <= TTF; y++) { set(-101, y, -29, id("minecraft:ladder[facing=east]")); innReserve(-100, y, -29); }
        // THE BACK YARD: bamboo grove, the stone lantern, the lily pond, a bench; the blackstone wall + gate in front
        for (int[] b : new int[][]{{-103, -20, 6}, {-102, -19, 7}, {-103, -17, 5}, {-101, -16, 6}, {-102, -15, 7}, {-103, -14, 6}, {-100, -14, 5}}) {
            for (int i = 0; i < b[2]; i++)
                set(b[0], UG + 1 + i, b[1], id("minecraft:bamboo[age=1,leaves=" + (i >= b[2] - 2 ? "large" : i == b[2] - 3 ? "small" : "none") + ",stage=0]"));
        }
        set(-99, UG + 1, -24, id("minecraft:chiseled_stone_bricks")); set(-99, UG + 2, -24, LANTERN); set(-99, UG + 3, -24, id("minecraft:stone_brick_slab"));
        for (int x = -97; x <= -94; x++)
            for (int z = -21; z <= -17; z++) {
                boolean rim = x == -97 || x == -94 || z == -21 || z == -17;
                if (rim) { set(x, UG, z, MOSSY_COBBLE); continue; }
                set(x, UG, z, WATER); set(x, UG - 1, z, WATER); set(x, UG - 2, z, id("minecraft:gravel"));
            }
        set(-96, UG + 1, -19, id("minecraft:lily_pad")); set(-95, UG + 1, -20, id("minecraft:lily_pad"));
        set(-96, UG - 1, -18, id("minecraft:seagrass"));
        for (int x = -97; x <= -95; x++) set(x, UG + 1, -14, stairs("dark_oak", "south"));
        for (int x = -104; x <= -92; x++) set(x, UG + 1, -40, x == -96 ? id("minecraft:dark_oak_fence_gate[facing=north]") : id("minecraft:polished_blackstone_wall"));
        for (int z = -39; z <= -38; z++) set(-96, UG, z, DIRT_PATH);
        furnishTattooist();
        uaBox(-104, -92);
    }

    private static void furnishTattooist() {
        final int g = UG + 1, u = TTF + 1;
        // THE FLASH WALL (west): banners of designs - a skull, a rose, a sea serpent, an eye, the globe
        patternBanner(-101, g + 2, -36, "white", "east", 15, "sku");
        patternBanner(-101, g + 2, -35, "white", "east", 14, "flo", 15, "bo");
        patternBanner(-101, g + 2, -34, "light_blue", "east", 11, "moj", 15, "bo");
        patternBanner(-101, g + 2, -33, "white", "east", 11, "mr", 15, "mc");
        patternBanner(-101, g + 2, -32, "cyan", "east", 4, "glb");
        // the tattoo chair (crimson, a footrest), the tray of needles + inks, the brazier, the waiting bench by the window
        ip(-97, g, -32, id("pixelpirates:tattoo_chair[facing=west]"));                       // THE TATTOO CHAIR: use it to get inked (homestead/tattoo)
        ip(-98, g, -31, slabTop("dark_oak")); ip(-98, g + 1, -31, id("minecraft:black_candle[candles=3,lit=true]"));
        ip(-98, g, -33, slabTop("dark_oak")); ip(-98, g + 1, -33, id("minecraft:lightning_rod"));
        ip(-95, g, -30, barrel("up")); ip(-95, g + 1, -30, id("minecraft:red_candle[candles=2,lit=true]"));
        for (int x = -101; x <= -99; x++) ip(x, g, -36, stairs("dark_oak", "south"));
        ip(-95, g, -35, id("minecraft:potted_bamboo"));
        ip(-95, g, -36, slabTop("dark_oak")); ip(-95, g + 1, -36, id("minecraft:decorated_pot"));
        rug(-100, -32, -99, -29, g, "red", "black");
        hangLantern(-98, TTF - 1, -34); hangLantern(-97, TTF - 1, -30);
        // upstairs: the tiger-stripe rug, the bed, a low tea table, the sea chest, bookshelf
        for (int x = -100; x <= -96; x++) for (int z = -34; z <= -32; z++) ip(x, u, z, carpet((x & 1) == 0 ? "orange" : "black"));
        ip(-95, u, -30, bed("black", "east", true)); ip(-96, u, -30, bed("black", "east", false));
        ip(-101, u, -36, BOOKSHELF); ip(-100, u, -36, BOOKSHELF); ip(-99, u, -36, id("pixelpirates:sea_chest[facing=south]"));
        ip(-95, u, -36, slabTop("dark_oak")); ip(-95, u + 1, -36, id("minecraft:decorated_pot"));
        hangLantern(-98, TTR + 1, -33);
    }

    /*
     * #34 THE TREASURE HUNTER (x80..92): an explorer home from far shores - a flat-roofed sandstone house (cut-sandstone
     * bands, an arched door with a keystone, amber windows) with a LOOKOUT TOWER on one corner under a little orange dome,
     * a roof terrace with a striped awning. Inside: the trophy hall (a gilded-mask sarcophagus, relics on pillars - the
     * heart of the sea, a strange egg, an old pot - charts and the globe on the walls, the map table); upstairs the camp
     * bed and his journal; the telescope in the tower. The back yard is THE DIG: an excavation pit with the half-exposed
     * skeleton of a sea serpent, a winch with its bucket, a camp tent, the sieve.
     */
    private static final int THX1 = 82, THX2 = 90, THZ1 = -37, THZ2 = -28;

    private static void treasureHuntersHouse() {
        uaLot(80, 92, 84);
        FOOTPRINTS.add(new int[]{THX1, THZ1, THX2, THZ2});
        int smooth = id("minecraft:smooth_sandstone"), cut = id("minecraft:cut_sandstone"), chis = id("minecraft:chiseled_sandstone");
        fill(THX1, UG - 2, THZ1, THX2, UG - 1, THZ2, COBBLE);
        for (int x = THX1; x <= THX2; x++)
            for (int z = THZ1; z <= THZ2; z++) {
                boolean ex = x == THX1 || x == THX2, ez = z == THZ1 || z == THZ2;
                if (!ex && !ez) {
                    set(x, UG, z, ((x + z) & 1) == 0 ? smooth : id("minecraft:orange_terracotta"));
                    set(x, 75, z, SPRUCE); set(x, 80, z, smooth);
                    for (int y = UG + 1; y <= 79; y++) if (y != 75) set(x, y, z, AIR);
                    continue;
                }
                set(x, UG, z, cut);
                for (int y = UG + 1; y <= 80; y++) set(x, y, z, y == UG + 1 || y == 80 ? cut : y == 75 ? chis : smooth);
                set(x, 81, z, id("minecraft:sandstone_wall"));                                        // the parapet
            }
        // THE LOOKOUT TOWER (x86..90 z-37..-33) on the roof, its dome + finial
        for (int x = 86; x <= 90; x++)
            for (int z = -37; z <= -33; z++) {
                boolean edge = x == 86 || x == 90 || z == -37 || z == -33, corner = (x == 86 || x == 90) && (z == -37 || z == -33);
                for (int y = 81; y <= 84; y++) set(x, y, z, edge ? (y == 84 ? cut : smooth) : AIR);
                set(x, 85, z, corner ? id("minecraft:cut_sandstone_slab") : id("minecraft:orange_terracotta"));
                if (!edge) set(x, 86, z, id("minecraft:orange_terracotta"));
            }
        set(88, 87, -35, id("minecraft:orange_terracotta")); set(88, 88, -35, id("minecraft:lightning_rod"));
        for (int[] w : new int[][]{{88, -37}, {88, -33}, {90, -35}}) for (int y = 82; y <= 83; y++) set(w[0], y, w[1], id("minecraft:orange_stained_glass_pane"));
        innDoor(86, 80, -35, "jungle", false);
        // the arched door (keystone), amber windows, lamps on posts, the sign; the back door to the dig
        innDoor(84, UG, THZ1, "jungle", true);
        set(84, UG + 3, THZ1, chis); set(83, UG + 3, THZ1, stairsTop("sandstone", "west")); set(85, UG + 3, THZ1, stairsTop("sandstone", "east"));
        for (int x : new int[]{83, 85}) { set(x, UG + 1, THZ1 - 1, id("minecraft:sandstone_wall")); set(x, UG + 2, THZ1 - 1, LANTERN); }
        bracketSign(86, UG + 4, THZ1 - 1, "jungle", "", "Maps &", "Relics", "");
        for (int x : new int[]{87, 88}) { for (int y = UG + 2; y <= UG + 3; y++) set(x, y, THZ1, id("minecraft:orange_stained_glass_pane")); }
        set(87, UG + 4, THZ1, stairsTop("sandstone", "west")); set(88, UG + 4, THZ1, stairsTop("sandstone", "east"));
        for (int z : new int[]{-33, -32}) for (int y : new int[]{UG + 2, 76}) for (int dy = 0; dy <= 1; dy++) {
            set(THX1, y + dy, z, id("minecraft:orange_stained_glass_pane")); set(THX2, y + dy, z, id("minecraft:orange_stained_glass_pane"));
        }
        for (int x : new int[]{84, 85, 87, 88}) for (int y = 76; y <= 77; y++) set(x, y, THZ2, id("minecraft:orange_stained_glass_pane"));
        for (int x : new int[]{84, 88}) for (int y = 76; y <= 77; y++) set(x, y, THZ1, id("minecraft:orange_stained_glass_pane"));
        innDoor(86, UG, THZ2, "jungle", true);
        // ladders: ground -> upper (north-east), upper -> the roof terrace (south-west, through a hatch)
        for (int y = UG + 1; y <= 75; y++) { set(89, y, -35, id("minecraft:ladder[facing=west]")); innReserve(88, y, -35); }
        for (int y = 76; y <= 80; y++) { set(83, y, -29, id("minecraft:ladder[facing=east]")); innReserve(84, y, -29); }
        // THE ROOF TERRACE: the striped awning on posts, deck chairs, crates of finds, potted cacti
        for (int[] p : new int[][]{{84, -31}, {84, -29}, {86, -31}, {86, -29}}) fill(p[0], 81, p[1], p[0], 83, p[1], id("minecraft:jungle_fence"));
        for (int x = 84; x <= 86; x++) for (int z = -31; z <= -29; z++) set(x, 84, z, wool(((x + z) & 1) == 0 ? "white" : "brown"));
        set(85, 81, -30, stairs("jungle", "south")); set(85, 81, -31, slabTop("jungle")); set(85, 82, -31, id("minecraft:potted_cactus"));
        set(88, 81, -30, id("pixelpirates:cargo_crate[facing=west]")); set(89, 81, -30, id("pixelpirates:cargo_crate[facing=west]")); set(89, 82, -30, barrel("up"));
        set(83, 81, -36, id("minecraft:potted_cactus")); set(83, 81, -32, id("minecraft:potted_dead_bush"));

        // THE DIG: an excavation pit (a shelf 1 deep round a pit 3 deep), the sea serpent's skeleton, the winch + bucket,
        // the ladder down, the camp tent, the sieve, the finds
        fill(82, UG - 2, -25, 90, UG - 1, -15, DIRT);
        for (int x = 83; x <= 89; x++)
            for (int z = -24; z <= -16; z++) {
                boolean inner = x >= 84 && x <= 88 && z >= -23 && z <= -17;
                set(x, UG, z, AIR);
                if (!inner) { set(x, UG - 1, z, id("minecraft:coarse_dirt")); continue; }
                set(x, UG - 1, z, AIR); set(x, UG - 2, z, AIR);
                set(x, UG - 3, z, ((x * 3 + z) & 3) == 0 ? GRAVEL : ((x + z) & 1) == 0 ? id("minecraft:coarse_dirt") : id("minecraft:sand"));
                set(x, UG - 4, z, DIRT);
            }
        for (int z = -23; z <= -18; z++) set(86, UG - 2, z, id("minecraft:bone_block[axis=z]"));        // the spine
        for (int z : new int[]{-22, -20, -18})                                                        // the ribs
            for (int x : new int[]{84, 85, 87, 88}) set(x, UG - 2, z, id("minecraft:bone_block[axis=x]"));
        set(86, UG - 2, -17, id("minecraft:skeleton_skull[rotation=8]"));
        set(84, UG - 2, -17, id("minecraft:decorated_pot"));
        for (int y = UG - 2; y <= UG - 1; y++) set(84, y, -23, id("minecraft:ladder[facing=south]"));
        fill(90, UG + 1, -20, 90, UG + 5, -20, id("minecraft:jungle_fence"));
        for (int x = 87; x <= 90; x++) set(x, UG + 6, -20, id("minecraft:stripped_jungle_log[axis=x]"));
        for (int y = UG; y <= UG + 5; y++) set(87, y, -20, CHAIN);
        set(87, UG - 1, -20, id("minecraft:cauldron"));
        for (int[] p : new int[][]{{87, -14}, {90, -14}, {87, -12}, {90, -12}}) fill(p[0], UG + 1, p[1], p[0], UG + 2, p[1], id("minecraft:jungle_fence"));
        for (int x = 87; x <= 90; x++) for (int z = -14; z <= -12; z++) set(x, UG + 3, z, wool(z == -13 ? "white" : "light_gray"));
        set(88, UG + 1, -13, slabTop("jungle")); set(88, UG + 2, -13, LANTERN); set(89, UG + 1, -13, id("pixelpirates:cargo_crate[facing=north]"));
        set(83, UG + 1, -14, COMPOSTER); set(84, UG + 1, -13, barrel("up")); set(84, UG + 2, -13, id("minecraft:decorated_pot"));
        for (int x = 80; x <= 92; x++) set(x, UG + 1, -40, x == 84 ? id("minecraft:jungle_fence_gate[facing=north]") : id("minecraft:sandstone_wall"));
        for (int z = -39; z <= -38; z++) set(84, UG, z, DIRT_PATH);
        set(81, UG + 1, -38, id("minecraft:potted_cactus")); set(91, UG + 1, -38, id("minecraft:potted_cactus"));
        furnishTreasureHunter();
        uaBox(80, 92);
    }

    private static void furnishTreasureHunter() {
        final int g = UG + 1, u = 76;
        // the sarcophagus with its gilded mask
        for (int z = -31; z <= -29; z++) ip(88, g, z, id("minecraft:chiseled_sandstone"));
        ip(88, g + 1, -31, id("minecraft:smooth_sandstone_slab")); ip(88, g + 1, -30, id("minecraft:smooth_sandstone_slab")); ip(88, g + 1, -29, id("minecraft:gold_block"));
        // relics on pillars, the globe, charts, the map table
        String[] relic = {"minecraft:conduit", "pixelpirates:pearl_block", "minecraft:decorated_pot"};
        for (int i = 0; i < 3; i++) { ip(83, g, -34 + i * 2, id("minecraft:quartz_pillar")); ip(83, g + 1, -34 + i * 2, id(relic[i])); }
        patternBanner(83, g + 2, -33, "light_blue", "east", 13, "glb");
        ip(84, g + 2, -29, id("pixelpirates:sea_chart[facing=north]")); ip(87, g + 2, -29, id("pixelpirates:sea_chart[facing=north]"));
        ip(86, g, -32, id("pixelpirates:map_table[facing=south]")); ip(86, g, -31, stairs("jungle", "south"));
        ip(89, g, -37 + 1, id("pixelpirates:cargo_crate[facing=west]")); ip(89, g + 1, -36, barrel("up"));
        hangLantern(86, 74, -34); hangLantern(85, 74, -30);
        // upstairs: the camp bed, his journal on the lectern, a sea chest, a skull on the shelf, a chart
        ip(89, u, -31, bed("brown", "south", true)); ip(89, u, -32, bed("brown", "south", false));
        ip(84, u, -35, id("minecraft:lectern[facing=south]")); ip(83, u, -35, BOOKSHELF); ip(83, u, -33, id("pixelpirates:sea_chest[facing=east]"));
        ip(87, u, -36, slabTop("jungle")); ip(87, u + 1, -36, id("minecraft:skeleton_skull[rotation=8]"));
        rug(85, -34, 87, -31, u, "orange", "brown");
        hangLantern(86, 79, -32);
        // the lookout: the telescope
        ip(88, 81, -35, id("pixelpirates:telescope[facing=north]"));
        hangLantern(88, 84, -36);
    }

    /*
     * #35 THE TOYMAKER (x96..108): a storybook house - peach walls between CANDY-STRIPED corner posts (red and white),
     * bamboo trim, a steep lilac (purpur) roof with a round attic window in each gable, a crooked chimney and a BAY WINDOW
     * of toys (toy soldiers, a ship in a bottle, marbles). Inside: a train set running round a little harbour town, the
     * puppet theatre, the counter with a music box, the workbench; upstairs the toymaker's room with a patchwork rug; the
     * attic of toy boxes with model ships hung from the rafters. The yard: a swing, a seesaw, a sandpit with a castle,
     * hopscotch on the path, and a kite flying high on its string.
     */
    private static final int TMX1 = 98, TMX2 = 106, TMZ1 = -37, TMZ2 = -28, TMR = 80;

    private static void toymakersHouse() {
        uaLot(96, 108, 105);
        FOOTPRINTS.add(new int[]{TMX1, TMZ1 - 1, TMX2, TMZ2});
        int peach = id("minecraft:white_terracotta"), trim = id("minecraft:bamboo_planks"), plinth = id("minecraft:bamboo_mosaic");
        fill(TMX1, UG - 2, TMZ1, TMX2, UG - 1, TMZ2, COBBLE);
        for (int x = TMX1; x <= TMX2; x++)
            for (int z = TMZ1; z <= TMZ2; z++) {
                boolean ex = x == TMX1 || x == TMX2, ez = z == TMZ1 || z == TMZ2;
                if (!ex && !ez) {
                    set(x, UG, z, ((x + z) & 1) == 0 ? id("minecraft:birch_planks") : id("minecraft:stripped_birch_wood"));
                    set(x, 75, z, SPRUCE); set(x, TMR, z, id("minecraft:birch_planks"));
                    for (int y = UG + 1; y < TMR; y++) if (y != 75) set(x, y, z, AIR);
                    continue;
                }
                set(x, UG, z, STONE_BRICKS);
                for (int y = UG + 1; y < TMR; y++)
                    set(x, y, z, (ex && ez) ? wool((y & 1) == 0 ? "red" : "white") : y == UG + 1 ? plinth : y == 75 ? trim : peach);
            }
        // THE STEEP ROOF (ridge along x): two blocks + a stair per step; the gable ends under it, a round window in each
        for (int i = 0; ; i++) {
            int n = TMZ1 - 1 + i, s = TMZ2 + 1 - i;
            if (n > s) break;
            int top = TMR + 2 * i + 1, bot = i == 0 ? TMR : i == 1 ? TMR : TMR + 2 * i - 1;
            for (int x = TMX1 - 1; x <= TMX2 + 1; x++)
                for (int z : new int[]{n, s})
                    for (int y = bot; y <= top; y++) set(x, y, z, y == top ? stairs("purpur", z == n ? "south" : "north") : id("minecraft:purpur_block"));
            if (i >= 2)
                for (int z : new int[]{n, s}) for (int x : new int[]{TMX1, TMX2}) for (int y = TMR; y < bot; y++) set(x, y, z, peach);
        }
        for (int x : new int[]{TMX1, TMX2}) {
            for (int y = 84; y <= 87; y++) for (int z = -33; z <= -32; z++) set(x, y, z, id("minecraft:glass_pane"));
            for (int y = 85; y <= 86; y++) { set(x, y, -34, id("minecraft:glass_pane")); set(x, y, -31, id("minecraft:glass_pane")); }
        }
        // the bay window of toys, the door, windows, the sign, the crooked chimney (east)
        for (int x = 100; x <= 104; x++) {
            boolean post = x == 100 || x == 104;
            set(x, UG, TMZ1 - 1, trim);
            set(x, UG + 1, TMZ1 - 1, trim);
            for (int y = UG + 2; y <= UG + 3; y++) set(x, y, TMZ1 - 1, post ? trim : id("minecraft:glass_pane"));
            set(x, UG + 4, TMZ1 - 1, trim);
            set(x, UG + 5, TMZ1 - 1, id("minecraft:purpur_slab"));
            if (!post) for (int y = UG + 2; y <= UG + 4; y++) set(x, y, TMZ1, AIR);
        }
        innDoor(105, UG, TMZ1, "bamboo", true);
        for (int x : new int[]{100, 101, 103, 104}) for (int y = 77; y <= 78; y++) set(x, y, TMZ1, id("minecraft:glass_pane"));
        for (int x : new int[]{TMX1, TMX2}) for (int z : new int[]{-35, -34}) for (int y = UG + 2; y <= UG + 3; y++) set(x, y, z, id("minecraft:glass_pane"));
        for (int x : new int[]{TMX1, TMX2}) for (int z : new int[]{-33, -32}) for (int y = 77; y <= 78; y++) set(x, y, z, id("minecraft:glass_pane"));
        for (int x = 100; x <= 102; x++) for (int y = UG + 2; y <= UG + 3; y++) set(x, y, TMZ2, id("minecraft:glass_pane"));
        innDoor(104, UG, TMZ2, "bamboo", true);
        bracketSign(99, UG + 4, TMZ1 - 1, "bamboo", "", "Toys &", "Puzzles", "");
        fill(107, UG + 1, -30, 107, 88, -29, id("minecraft:bricks"));                                // the crooked chimney
        fill(108, 87, -30, 108, 92, -30, id("minecraft:bricks"));
        set(108, 93, -30, id("minecraft:brick_wall"));
        // ladders: ground -> upper -> attic (back-east corner)
        for (int y = UG + 1; y <= TMR; y++) { set(105, y, -29, id("minecraft:ladder[facing=west]")); innReserve(104, y, -29); }
        // THE YARD: swing, seesaw, sandpit + castle, hopscotch, the kite on its string; the bamboo fence + gate
        for (int x : new int[]{99, 103}) fill(x, UG + 1, -22, x, UG + 4, -22, id("minecraft:bamboo_fence"));
        for (int x = 99; x <= 103; x++) set(x, UG + 5, -22, id("minecraft:bamboo_block[axis=x]"));
        for (int y = UG + 3; y <= UG + 4; y++) set(101, y, -22, CHAIN);
        set(101, UG + 2, -22, id("minecraft:bamboo_slab"));
        set(104, UG + 1, -17, id("minecraft:stripped_oak_log[axis=z]"));
        for (int x = 102; x <= 106; x++) set(x, UG + 2, -17, id("minecraft:oak_slab"));
        for (int x = 98; x <= 101; x++)
            for (int z = -16; z <= -13; z++) {
                boolean rim = x == 98 || x == 101 || z == -16 || z == -13;
                if (rim) set(x, UG + 1, z, id("minecraft:oak_slab")); else set(x, UG, z, id("minecraft:sand"));
            }
        set(99, UG + 1, -15, id("minecraft:sandstone_wall")); set(100, UG + 1, -14, id("minecraft:sandstone_slab")); set(100, UG + 1, -15, id("minecraft:cauldron"));
        String[] hop = {"white", "pink", "light_blue", "yellow", "lime", "orange"};
        for (int i = 0; i < 6; i++) set(99 + i % 2, UG, -26 + i / 2, id("minecraft:" + hop[i] + "_terracotta"));            // hopscotch
        int kx = 105, ky = 86, kz = -19;
        for (int dx = -2; dx <= 2; dx++)
            for (int dy = -2; dy <= 2; dy++)
                if (Math.abs(dx) + Math.abs(dy) <= 2) set(kx + dx, ky + dy, kz, wool(dx == 0 && dy == 0 ? "white" : dy > 0 || (dy == 0 && dx < 0) ? "red" : "yellow"));
        set(kx, ky - 3, kz, wool("blue")); set(kx, ky - 4, kz, wool("red"));
        for (int y = UG + 3; y <= ky - 5; y++) set(kx, y, kz, CHAIN);
        fill(kx, UG + 1, kz, kx, UG + 2, kz, id("minecraft:bamboo_fence"));
        for (int x = 96; x <= 108; x++) set(x, UG + 1, -40, x == 105 ? id("minecraft:bamboo_fence_gate[facing=north]") : id("minecraft:bamboo_fence"));
        for (int z = -39; z <= -38; z++) set(105, UG, z, DIRT_PATH);
        furnishToymaker(new Random(0x70E5L));
        uaBox(96, 108);
    }

    private static void furnishToymaker(Random r) {
        final int g = UG + 1, u = 76, a = TMR + 1;
        // the bay: toy soldiers, a ship in a bottle, marbles
        ip(101, UG + 2, TMZ1, id("minecraft:red_candle[candles=4,lit=false]"));
        ip(102, UG + 2, TMZ1, id("pixelpirates:ship_in_bottle_sloop[facing=north]"));
        ip(103, UG + 2, TMZ1, id("minecraft:turtle_egg[eggs=4]"));
        // the train set round a little harbour town (a lighthouse, a house, a tree)
        String[][] rail = {{"south_east", "east_west", "east_west", "east_west", "south_west"}, {"north_south", null, null, null, "north_south"},
                {"north_east", "east_west", "east_west", "east_west", "north_west"}};
        for (int i = 0; i < 3; i++) for (int j = 0; j < 5; j++) if (rail[i][j] != null) ip(99 + j, g, -36 + i, id("minecraft:rail[shape=" + rail[i][j] + "]"));
        ip(101, g, -35, WHITE_WOOL); ip(101, g + 1, -35, id("minecraft:red_wool")); ip(101, g + 2, -35, SEA_LANTERN);
        ip(100, g, -35, id("minecraft:spruce_slab")); ip(102, g, -35, OAK_LEAVES);
        // the puppet theatre (back-west), the counter with the music box, the workbench, a rocking horse, spinning tops
        for (int x = 99; x <= 101; x++) ip(x, g, -29, trimBlock());
        ip(99, g + 1, -29, id("minecraft:red_wool")); ip(101, g + 1, -29, id("minecraft:red_wool"));
        ip(100, g + 1, -29, id("minecraft:player_head[rotation=8]"));
        for (int x = 99; x <= 101; x++) ip(x, g + 2, -29, id("minecraft:red_wool"));
        ip(104, g, -32, slabTop("bamboo")); ip(105, g, -32, slabTop("bamboo"));
        ip(104, g + 1, -32, id("minecraft:jukebox")); ip(105, g + 1, -32, id("minecraft:bell[attachment=floor,facing=north]"));
        ip(105, g, -35, CRAFTING); ip(105, g, -34, id("minecraft:loom[facing=west]")); ip(105, g, -33, id("minecraft:stonecutter[facing=west]"));
        ip(102, g, -31, id("minecraft:dark_oak_slab")); ip(103, g, -31, stairs("oak", "west")); ip(102, g + 1, -31, id("minecraft:brown_wool"));
        ip(99, g, -32, slabTop("bamboo")); ip(99, g + 1, -32, id("minecraft:pointed_dripstone[vertical_direction=up,thickness=tip]"));
        hangLantern(102, 74, -33); hangLantern(102, 74, -30);
        // a chess table (homestead/chess - playable) with a stool each side; white sits on the west
        ip(101, g, -30, id("pixelpirates:chess_table[facing=west]"));
        ip(100, g, -30, id("pixelpirates:barrel_stool[facing=east]")); ip(102, g, -30, id("pixelpirates:barrel_stool[facing=west]"));
        // upstairs: the bed, the toy chest, the desk, a patchwork rug
        ip(100, u, -29, bed("yellow", "south", true)); ip(100, u, -30, bed("yellow", "south", false));
        ip(99, u, -36, chest("south")); ip(100, u, -36, barrel("south")); ip(105, u, -36, id("minecraft:lectern[facing=west]"));
        String[] pc = {"red", "yellow", "lime", "light_blue", "pink", "orange", "purple"};
        for (int x = 101; x <= 104; x++) for (int z = -35; z <= -32; z++) ip(x, u, z, carpet(pc[r.nextInt(pc.length)]));
        hangLantern(102, 79, -33);
        // the attic: toy boxes, model ships hung from the rafters
        ip(99, a, -33, chest("east")); ip(99, a, -32, barrel("east")); ip(103, a, -33, id("minecraft:note_block"));
        ip(101, 88, -33, id("pixelpirates:votive_ship[facing=east]")); ip(103, 88, -32, id("pixelpirates:votive_ship[facing=west]"));
        hangLantern(102, 87, -32);
    }

    private static int trimBlock() { return id("minecraft:bamboo_planks"); }

    /*
     * #36 THE SHIP'S-CAT KEEPER (x120..132, the upper row's east end): a cosy red-brick cottage under a deep thatch, teal
     * shutters, a fireplace in a brick chimney - and cats everywhere: a cat tower, baskets, bowls by the door, a cat flap
     * to THE CAT GARDEN behind (fenced all round so the cats stay): a little tree to climb, a lily pond, barrels to hide
     * in, a fish-drying rack, catnip. Every game day the keeper has four cats in (homestead/cat/Cattery: drawn by rarity,
     * some in coats seen nowhere else); tame one with fish and it's your ship's cat - Luck while it's with you.
     */
    public static final int[] CATTERY_CENTRE = {126, 71, -22};
    public static final int[] CATTERY_BOX = {120, 68, -38, 132, 82, -13};
    public static final double[][] CATTERY_SPOTS = {{123.5, 71, -24.5}, {128.5, 71, -21.5}, {124.5, 71, -17.5}, {130.5, 71, -27.5}};
    private static final int CKX1 = 121, CKX2 = 129, CKZ1 = -38, CKZ2 = -31;

    private static void shipsCatKeeper() {
        uaLot(120, 132, 125);
        FOOTPRINTS.add(new int[]{CKX1, CKZ1, CKX2, CKZ2});
        int brick = id("minecraft:bricks"), post = id("minecraft:stripped_oak_log[axis=y]");
        fill(CKX1, UG - 2, CKZ1, CKX2, UG - 1, CKZ2, COBBLE);
        for (int x = CKX1; x <= CKX2; x++)
            for (int z = CKZ1; z <= CKZ2; z++) {
                boolean ex = x == CKX1 || x == CKX2, ez = z == CKZ1 || z == CKZ2;
                if (!ex && !ez) { set(x, UG, z, OAK); for (int y = UG + 1; y <= UG + 4; y++) set(x, y, z, AIR); continue; }
                set(x, UG, z, COBBLE);
                for (int y = UG + 1; y <= UG + 4; y++) set(x, y, z, (ex && ez) ? post : y == UG + 4 ? id("minecraft:stripped_oak_log[axis=" + (ez ? "x" : "z") + "]") : brick);
            }
        gableRoofXId(CKX1, CKX2, CKZ1, CKZ2, UG + 5, "pixelpirates:thatch_stairs", id("pixelpirates:thatch"));
        gableEndsX(CKX1, CKZ1 + 1, CKZ2 - 1, UG + 5, brick);
        gableEndsX(CKX2, CKZ1 + 1, CKZ2 - 1, UG + 5, brick);
        // doors, the cat flap, windows with teal shutters, the chimney + fireplace (west), the sign
        innDoor(125, UG, CKZ1, "spruce", true);
        innDoor(125, UG, CKZ2, "spruce", true);
        set(127, UG + 1, CKZ2, id("minecraft:spruce_trapdoor[facing=south,half=bottom,open=true]"));
        for (int[] w : new int[][]{{122, CKZ1}, {127, CKZ1}, {122, CKZ2}})
            for (int dx = 0; dx <= 1; dx++) for (int y = UG + 2; y <= UG + 3; y++) set(w[0] + dx, y, w[1], id("minecraft:glass_pane"));
        for (int x : new int[]{121, 124}) for (int y = UG + 2; y <= UG + 3; y++) set(x, y, CKZ2 + 1, id("minecraft:warped_trapdoor[facing=south,half=bottom,open=true]"));
        for (int z = -35; z <= -34; z++) for (int y = UG + 2; y <= UG + 3; y++) set(CKX2, y, z, id("minecraft:glass_pane"));
        fill(CKX1 - 1, UG + 1, -36, CKX1 - 1, UG + 11, -34, brick);
        set(CKX1 - 1, UG + 1, -35, CAMPFIRE); fill(CKX1 - 1, UG + 2, -35, CKX1 - 1, UG + 11, -35, AIR);
        set(CKX1, UG + 1, -35, AIR); set(CKX1, UG + 2, -35, AIR);
        for (int z = -36; z <= -34; z++) set(CKX1 + 1, UG + 3, z, stairsTop("brick", "west"));
        bracketSign(124, UG + 3, CKZ1 - 1, "oak", "", "Ship's", "Cats", "");
        // THE CAT GARDEN: fenced all round (the cats can't get out), the gate east; inside the tree, pond, barrels, rack, catnip
        for (int z = -30; z <= -13; z++) { set(120, UG + 1, z, OAK_FENCE); set(132, UG + 1, z, z == -22 ? id("minecraft:oak_fence_gate[facing=east]") : OAK_FENCE); }
        for (int x = 120; x <= 132; x++) set(x, UG + 1, -13, OAK_FENCE);
        for (int x = 130; x <= 132; x++) set(x, UG + 1, -30, OAK_FENCE);
        set(120, UG + 1, -31, OAK_FENCE); set(130, UG + 1, -31, OAK_FENCE);                          // close the corners against the cottage (cats slip diagonally)
        for (int z = -30; z <= -29; z++) set(125, UG, z, GRAVEL);
        fill(126, UG + 1, -16, 126, UG + 3, -16, id("minecraft:oak_log[axis=y]"));
        for (int x = 125; x <= 127; x++) for (int z = -17; z <= -15; z++) set(x, UG + 4, z, OAK_LEAVES);
        set(126, UG + 5, -16, OAK_LEAVES);
        set(127, UG + 2, -16, slabTop("oak")); set(125, UG + 1, -16, slabTop("oak"));
        for (int x = 121; x <= 122; x++) for (int z = -21; z <= -20; z++) { set(x, UG, z, WATER); set(x, UG - 1, z, WATER); set(x, UG - 2, z, GRAVEL); }
        set(122, UG + 1, -21, id("minecraft:lily_pad"));
        for (int x = 122; x <= 123; x++) { set(x, UG + 1, -15, barrel("up")); set(x, UG + 2, -15, carpet(x == 122 ? "red" : "light_blue")); }   // 2 off the fence: no jumping out
        for (int x : new int[]{129, 131}) fill(x, UG + 1, -14, x, UG + 2, -14, OAK_FENCE);
        for (int x = 129; x <= 131; x++) set(x, UG + 3, -14, DARK_OAK_LOG_X);
        set(130, UG + 2, -14, id("pixelpirates:hanging_net"));
        for (int[] f : new int[][]{{128, -18}, {129, -18}, {130, -18}, {129, -17}})
            set(f[0], UG + 1, f[1], id(((f[0] + f[1]) & 1) == 0 ? "minecraft:allium" : "minecraft:azure_bluet"));
        for (int x = 125; x <= 126; x++) set(x, UG + 1, -28, stairs("oak", "north"));
        set(129, UG + 1, -25, id("minecraft:stripped_oak_log[axis=y]")); set(129, UG + 2, -25, carpet("white"));
        // the front: the picket fence + gate, bowls of water + flowers by the door
        for (int x = 120; x <= 132; x++) set(x, UG + 1, -40, x == 125 ? id("minecraft:oak_fence_gate[facing=north]") : OAK_FENCE);
        for (int z = -39; z <= -39; z++) set(125, UG, z, DIRT_PATH);
        set(123, UG + 1, -39, id("minecraft:potted_azure_bluet")); set(127, UG + 1, -39, id("minecraft:flower_pot"));
        furnishCatKeeper();
        uaBox(120, 132);
    }

    private static void furnishCatKeeper() {
        final int g = UG + 1;
        // the cat tower (a post with platforms + cushions), baskets (barrels with cushions), bowls, the keeper's chair by the fire
        fill(127, g, -36, 127, g + 3, -36, id("minecraft:stripped_oak_log[axis=y]"));
        ip(128, g + 1, -36, slabTop("oak")); ip(128, g + 2, -36, carpet("red"));
        ip(126, g + 2, -36, slabTop("oak")); ip(126, g + 3, -36, carpet("yellow"));
        ip(128, g, -37, barrel("up")); ip(128, g + 1, -37, carpet("light_gray"));
        ip(124, g, -36, id("pixelpirates:cattery_counter[facing=north]"));                      // THE CATTERY COUNTER: buy one of today's cats
        ip(123, g, -37, id("minecraft:flower_pot")); ip(124, g, -37, id("minecraft:flower_pot"));
        ip(122, g, -33, id("minecraft:spruce_stairs[facing=west]")); ip(122, g, -32, barrel("up")); ip(122, g + 1, -32, candle(2));
        ip(128, g, -33, bed("red", "east", true)); ip(127, g, -33, bed("red", "east", false));
        ip(123, g, -32, chest("north")); ip(128, g, -34, BOOKSHELF); ip(128, g + 1, -34, id("minecraft:decorated_pot"));
        rug(123, -35, 125, -33, g, "orange", "white");
        hangLantern(125, UG + 5, -34);
    }

    // ------------------------------------------------------------------ THE WALL ROW (row B, #37-#41, 2026-10-04): doors SOUTH onto long front
    // lawns that run down to the upper street (z-48); the city wall behind (z-86..-90 - build rule 6: nothing past z-85).

    /** Clear + grass a wall-row lot x1..x2 (z-84 against the wall - its towers reach z-85 - .. z-50, the front fence line), a path from the house
     *  (z-64) down to the street; the old generic house's eaves (z-81..-65, one block out) only where no earlier pass built. */
    private static void ubLot(int x1, int x2, int pathX) {
        UA_LOTS.add(new int[]{x1, x2, -84, -50});
        for (int x = x1 - 1; x <= x2 + 1; x++)
            for (int z = -84; z <= -49; z++) {
                if (x >= x1 && x <= x2 && z <= -50) {
                    for (int y = UG + 1; y <= 112; y++) set(x, y, z, AIR);
                    set(x, UG, z, x == pathX && z >= -64 ? ((z & 1) == 0 ? GRAVEL : DIRT_PATH) : GRASS);
                    set(x, UG - 1, z, DIRT);
                } else if (z >= -81 && z <= -65) {
                    for (int y = UG + 1; y <= 100; y++) if (getRaw(x, y, z) == 0) set(x, y, z, AIR);
                }
            }
        set(pathX, UG, -49, DIRT_PATH);
    }

    private static void ubBox(int lx1, int lx2) {
        int[] box = LABELS.get(CUR);
        box[0] = Math.min(box[0], lx1 - 1); box[1] = Math.min(box[1], -84); box[2] = Math.max(box[2], lx2 + 1); box[3] = Math.max(box[3], -49);
    }

    /** A front fence along z-50 with a gate at gateX. */
    private static void ubFence(int x1, int x2, int gateX, String fence, String gate) {
        for (int x = x1; x <= x2; x++) set(x, UG + 1, -50, x == gateX ? id("minecraft:" + gate + "[facing=south]") : id("minecraft:" + fence));
    }

    /** A standing banner (an easel's canvas, rotation 0 = facing south) with patterns, as patternBanner. */
    private static void standingBanner(int x, int y, int z, String base, int rotation, Object... colourPattern) {
        set(x, y, z, id("minecraft:" + base + "_banner[rotation=" + rotation + "]"));
        StringBuilder sb = new StringBuilder("{Patterns:[");
        for (int i = 0; i < colourPattern.length; i += 2)
            sb.append(i > 0 ? "," : "").append("{Color:").append(colourPattern[i]).append(",Pattern:\"").append(colourPattern[i + 1]).append("\"}");
        nbt(x, y, z, sb.append("]}").toString());
    }

    /*
     * #37 THE COOPER (x-66..-56, beside the west avenue - the old house stood on the avenue; its lot is narrowed and the
     * road re-paved): the barrel-maker lives INSIDE A GIANT CASK lying on its side - bulging oak staves, dark iron hoops,
     * spruce heads, a door in the south head up two steps, a porthole in the north one, a tap, chocks under it. Inside,
     * a curved little room: bed, table, chest, a lantern from the top stave. The front lawn is the COOPERAGE: a lean-to
     * shed (shaving horse, the hoop fire, the bench, staves), a pyramid of finished barrels, the sign on the shed post.
     */
    private static final double COX = -61, COY = 74.4;

    /** The cask's radius along its length: 4.6 in the belly, 3.7 at the heads. */
    private static double coR(int z) { double t = (z + 73) / 6.0; return 4.6 - 0.9 * t * t; }

    private static void coopersCask() {
        ubLot(-66, -56, -61);
        for (int x = -71; x <= -67; x++)                                                       // the west avenue, re-paved
            for (int z = -81; z <= -65; z++) {
                for (int y = UG + 1; y <= 100; y++) if (getRaw(x, y, z) == 0) set(x, y, z, AIR);
                sideAvenueColumn(x, z);
            }
        FOOTPRINTS.add(new int[]{-66, -79, -56, -67});
        int stave = id("minecraft:stripped_oak_log[axis=z]"), stave2 = id("minecraft:stripped_spruce_log[axis=z]"),
                hoop = id("minecraft:polished_deepslate"), rim = id("minecraft:dark_oak_planks");
        for (int z = -79; z <= -67; z++) {
            double r = coR(z);
            boolean head = z == -79 || z == -67, ring = z == -78 || z == -75 || z == -71 || z == -68;
            for (int x = -66; x <= -56; x++)
                for (int y = UG; y <= UG + 10; y++) {
                    double d = Math.hypot(x - COX, y - COY);
                    if (d > r) continue;
                    if (head) set(x, y, z, d > r - 1.0 ? rim : SPRUCE);
                    else if (d > r - 1.1) set(x, y, z, ring ? hoop : ((x + y) & 1) == 0 ? stave : stave2);
                    else if (y <= UG + 2) set(x, y, z, y == UG + 2 ? id("minecraft:oak_planks") : SPRUCE);
                    else set(x, y, z, AIR);
                }
        }
        innDoor(-61, UG + 2, -67, "spruce", true);
        set(-61, UG + 1, -65, stairs("spruce", "north"));
        set(-61, UG + 1, -66, SPRUCE); set(-61, UG + 2, -66, stairs("spruce", "north"));
        for (int x : new int[]{-63, -59}) set(x, UG + 5, -67, id("minecraft:glass_pane"));
        for (int y = UG + 4; y <= UG + 6; y++) set(-61, y, -79, id("minecraft:glass_pane"));
        set(-62, UG + 5, -79, id("minecraft:glass_pane")); set(-60, UG + 5, -79, id("minecraft:glass_pane"));
        set(-58, UG + 3, -66, id("minecraft:tripwire_hook[facing=south]"));                    // the tap
        for (int z : new int[]{-77, -69}) {                                                     // the chocks
            for (int x = -64; x <= -58; x++) if (getRaw(x, UG, z) == GRASS) set(x, UG, z, DARK_OAK_LOG_X);
            if (getRaw(-65, UG + 1, z) == AIR) set(-65, UG + 1, z, stairs("dark_oak", "east"));
            if (getRaw(-57, UG + 1, z) == AIR) set(-57, UG + 1, z, stairs("dark_oak", "west"));
        }
        // THE COOPERAGE: the lean-to shed, the hoop fire, the shaving horse; the barrel pyramid; the sign
        for (int[] p : new int[][]{{-66, -63}, {-63, -63}, {-66, -59}, {-63, -59}}) fill(p[0], UG + 1, p[1], p[0], UG + 3, p[1], SPRUCE_FENCE);
        for (int x = -66; x <= -63; x++) for (int z = -63; z <= -59; z++) set(x, UG + 4, z, id("minecraft:spruce_slab"));
        set(-65, UG + 1, -61, CAMPFIRE);
        for (int[] c : new int[][]{{-66, -61}, {-64, -61}}) set(c[0], UG + 1, c[1], id("minecraft:chain[axis=x]"));
        set(-64, UG + 1, -62, stairs("spruce", "west")); set(-63, UG + 1, -61, id("minecraft:spruce_slab"));
        set(-64, UG + 1, -60, CRAFTING); set(-65, UG + 1, -60, id("minecraft:smithing_table"));
        for (int y = UG + 1; y <= UG + 2; y++) set(-66, y, -60, id("minecraft:stripped_oak_log[axis=z]"));
        for (int x = -59; x <= -57; x++) set(x, UG + 1, -61, barrel("east"));
        set(-59, UG + 2, -61, barrel("east")); set(-58, UG + 2, -61, barrel("east")); set(-58, UG + 3, -61, barrel("east"));
        bracketSign(-63, UG + 3, -64, "spruce", "", "Casks &", "Barrels", "");
        set(-63, UG + 3, -63, SPRUCE_FENCE);
        for (int z = -84; z <= -82; z++) for (int y = UG + 1; y <= UG + 2; y++) set(-65, y, z, id("minecraft:oak_log[axis=x]"));   // seasoning staves
        set(-58, UG + 1, -83, barrel("up")); set(-57, UG + 1, -83, barrel("up"));
        ubFence(-66, -56, -61, "spruce_fence", "spruce_fence_gate");
        // inside the cask
        final int g = UG + 3;
        ip(-63, g, -77, bed("brown", "north", true)); ip(-63, g, -76, bed("brown", "north", false));
        ip(-59, g, -75, slabTop("spruce")); ip(-59, g + 1, -75, LANTERN); ip(-60, g, -75, stairs("spruce", "east"));
        ip(-59, g, -78, chest("west")); ip(-61, g, -78, barrel("south"));
        rug(-62, -74, -60, -71, g, "brown", "orange");
        hangLantern(-61, UG + 7, -72);
        ubBox(-66, -56);
    }

    /*
     * #38 THE BARBER-SURGEON (x-54..-42, beside the chapel): a brick shop with a white-tiled (quartz-brick) upper floor in a
     * dark-oak frame, a nether-brick roof, and a red-and-white BARBER'S POLE with its lamp by the door. Inside: the barber's
     * chair, the basin and the strop, shelves of apothecary jars (coloured glass, brewing stands), the operating table under
     * the window, a rack of bone saws, the anatomy skeleton; upstairs the surgeon's room. Out front: the waiting bench and
     * a bed of healing herbs.
     */
    private static final int BBX1 = -52, BBX2 = -44, BBZ1 = -79, BBZ2 = -68;

    private static void barberSurgeon() {
        ubLot(-54, -42, -46);
        FOOTPRINTS.add(new int[]{BBX1, BBZ1, BBX2, BBZ2});
        int brick = id("minecraft:bricks"), tile = id("minecraft:quartz_bricks");
        fill(BBX1, UG - 2, BBZ1, BBX2, UG - 1, BBZ2, COBBLE);
        for (int x = BBX1; x <= BBX2; x++)
            for (int z = BBZ1; z <= BBZ2; z++) {
                boolean ex = x == BBX1 || x == BBX2, ez = z == BBZ1 || z == BBZ2;
                if (!ex && !ez) {
                    set(x, UG, z, ((x + z) & 1) == 0 ? id("minecraft:white_terracotta") : id("minecraft:black_terracotta"));
                    set(x, 75, z, DARK_OAK);
                    for (int y = UG + 1; y <= 79; y++) if (y != 75) set(x, y, z, AIR);
                    continue;
                }
                set(x, UG, z, STONE_BRICKS);
                for (int y = UG + 1; y <= 79; y++)
                    set(x, y, z, ex && ez ? DARK_OAK_LOG_Y : y == UG + 1 ? STONE_BRICKS : y == 75 ? id("minecraft:stripped_dark_oak_log[axis=" + (ez ? "x" : "z") + "]")
                            : y < 75 ? brick : tile);
            }
        gableRoofZ(BBZ1, BBZ2, BBX1, BBX2, 80, "nether_brick", id("minecraft:nether_bricks"));
        gableEndsZ(BBZ1, BBX1 + 1, BBX2 - 1, 80, tile);
        gableEndsZ(BBZ2, BBX1 + 1, BBX2 - 1, 80, tile);
        // the shop window + sill, the door, the barber's pole + its lamp, the sign; upper + side windows
        for (int x = -51; x <= -48; x++) { for (int y = UG + 2; y <= UG + 3; y++) set(x, y, BBZ2, id("minecraft:glass_pane")); set(x, UG + 1, BBZ2 + 1, id("minecraft:brick_slab[type=top]")); }
        innDoor(-46, UG, BBZ2, "dark_oak", true);
        String[] pole = {"red", "white", "red", "white"};
        for (int i = 0; i < 4; i++) set(-45, UG + 1 + i, BBZ2 + 1, wool(pole[i]));
        set(-45, UG + 5, BBZ2 + 1, LANTERN);
        bracketSign(BBX1, UG + 4, BBZ2 + 1, "dark_oak", "", "Barber &", "Surgeon", "");
        for (int x : new int[]{-51, -50, -47, -46}) for (int y = 77; y <= 78; y++) set(x, y, BBZ2, id("minecraft:glass_pane"));
        for (int z = -74; z <= -73; z++) for (int y : new int[]{UG + 2, 77}) for (int dy = 0; dy <= 1; dy++) {
            set(BBX1, y + dy, z, id("minecraft:glass_pane")); set(BBX2, y + dy, z, id("minecraft:glass_pane"));
        }
        for (int y = UG + 1; y <= 75; y++) { set(-45, y, -78, id("minecraft:ladder[facing=west]")); innReserve(-46, y, -78); }
        // the front: the waiting bench, the herb bed, the gate
        for (int x = -51; x <= -49; x++) set(x, UG + 1, -63, stairs("dark_oak", "south"));
        String[] herbs = {"minecraft:lily_of_the_valley", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:allium"};
        for (int x = -53; x <= -48; x++) for (int z = -58; z <= -56; z++) set(x, UG + 1, z, id(herbs[Math.floorMod(x * 3 + z, herbs.length)]));
        ubFence(-54, -42, -46, "dark_oak_fence", "dark_oak_fence_gate");
        furnishBarber();
        ubBox(-54, -42);
    }

    private static void furnishBarber() {
        final int g = UG + 1, u = 76;
        // the barber's chair facing the window, the basin + strop, towels
        ip(-49, g, -71, id("pixelpirates:barber_chair[facing=south]")); ip(-48, g, -71, CAULDRON_WATER);   // the working chair (homestead/beard)
        ip(-47, g, -71, barrel("up")); ip(-47, g + 1, -71, id("minecraft:white_carpet"));
        // the apothecary shelves (west wall): jars of coloured glass + brewing stands on barrels
        String[] jars = {"lime", "green", "yellow", "light_blue", "red", "purple"};
        for (int z = -77; z <= -70; z++) {
            ip(BBX1 + 1, g, z, barrel("east"));
            ip(BBX1 + 1, g + 1, z, (z & 1) == 0 ? id("minecraft:brewing_stand") : id("minecraft:" + jars[Math.floorMod(z, jars.length)] + "_stained_glass"));
        }
        // the operating table, the rack of saws, the anatomy skeleton
        for (int x = -50; x <= -48; x++) { ip(x, g, -77, slabTop("birch")); ip(x, g + 1, -77, id("minecraft:white_carpet")); }
        ip(-49, g + 2, -78, id("pixelpirates:weapon_rack[facing=south]"));
        ip(-46, g, -76, id("minecraft:bone_block")); ip(-46, g + 1, -76, id("minecraft:bone_block")); ip(-46, g + 2, -76, id("minecraft:skeleton_skull[rotation=8]"));
        hangLantern(-48, 74, -74); hangLantern(-48, 74, -70);
        // upstairs: the surgeon's room
        ip(-50, u, -78, bed("white", "north", true)); ip(-50, u, -77, bed("white", "north", false));
        ip(-48, u, -78, id("minecraft:lectern[facing=south]")); ip(-51, u, -72, chest("east")); ip(-51, u, -71, BOOKSHELF);
        ip(-47, u, -70, slabTop("dark_oak")); ip(-47, u + 1, -70, id("minecraft:skeleton_skull[rotation=8]"));
        rug(-50, -75, -47, -72, u, "red", "white");
        hangLantern(-48, 80, -73);
    }

    /*
     * #39 THE SPICE MERCHANT (x56..68, beside the manor): a tall, narrow souk house of rosy granite with acacia trim and
     * striped orange-and-white AWNINGS, an open ARCADE on the ground floor heaped with spices (piles of coloured powder,
     * sacks, the grinder, the scales), store rooms above, the merchant's cushioned room on top - and a ROOF TERRACE: drying
     * mats of spice laid out in rows, an acacia pavilion with a hipped roof and strings of peppers hanging from its eaves.
     */
    private static final int SPX1 = 58, SPX2 = 66, SPZ1 = -79, SPZ2 = -70;

    private static void spiceMerchant() {
        ubLot(56, 68, 62);
        FOOTPRINTS.add(new int[]{SPX1, SPZ1, SPX2, SPZ2});
        int stone = id("minecraft:polished_granite"), acacia = id("minecraft:acacia_planks"), post = id("minecraft:stripped_acacia_log[axis=y]");
        fill(SPX1, UG - 2, SPZ1, SPX2, UG - 1, SPZ2, COBBLE);
        for (int x = SPX1; x <= SPX2; x++)
            for (int z = SPZ1; z <= SPZ2; z++) {
                boolean ex = x == SPX1 || x == SPX2, ez = z == SPZ1 || z == SPZ2;
                if (!ex && !ez) {
                    set(x, UG, z, ((x + z) & 1) == 0 ? id("minecraft:smooth_sandstone") : id("minecraft:orange_terracotta"));
                    set(x, 75, z, acacia); set(x, 80, z, acacia); set(x, 85, z, id("minecraft:smooth_sandstone"));
                    for (int y = UG + 1; y <= 84; y++) if (y != 75 && y != 80) set(x, y, z, AIR);
                    continue;
                }
                set(x, UG, z, id("minecraft:granite"));
                for (int y = UG + 1; y <= 85; y++) set(x, y, z, ex && ez ? post : (y == 75 || y == 80 || y == 85) ? acacia : stone);
                set(x, 86, z, id("minecraft:acacia_fence"));                                       // the terrace rail
            }
        // THE ARCADE (south front): three arches between acacia posts, the awning over it, the sign
        for (int x = SPX1 + 1; x <= SPX2 - 1; x++) {
            if (x == 62) { fill(x, UG + 1, SPZ2, x, UG + 4, SPZ2, post); continue; }
            for (int y = UG + 1; y <= UG + 3; y++) set(x, y, SPZ2, AIR);
            set(x, UG + 4, SPZ2, acacia);
        }
        for (int x : new int[]{59, 63}) set(x, UG + 3, SPZ2, stairsTop("acacia", "west"));
        for (int x : new int[]{61, 65}) set(x, UG + 3, SPZ2, stairsTop("acacia", "east"));
        for (int x = SPX1; x <= SPX2; x++) for (int dz = 1; dz <= 2; dz++) for (int y = UG + 4; y <= UG + 6 - dz; y++)
            set(x, y, SPZ2 + dz, wool(((x + dz) & 1) == 0 ? "orange" : "white"));                       // the sloping awning, face-joined
        bracketSign(SPX2, UG + 3, SPZ2 + 1, "acacia", "", "Spices &", "Teas", "");
        set(SPX2, UG + 3, SPZ2, post);
        // upper windows with their own striped awnings (south + sides)
        for (int y : new int[]{77, 82}) {
            for (int x : new int[]{60, 64}) {
                for (int dx = 0; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) set(x + dx, y + dy, SPZ2, id("minecraft:glass_pane"));
                for (int dx = -1; dx <= 2; dx++) set(x + dx, y + 2, SPZ2 + 1, wool(((x + dx) & 1) == 0 ? "orange" : "white"));
            }
            for (int z = -76; z <= -75; z++) for (int dy = 0; dy <= 1; dy++) { set(SPX1, y + dy, z, id("minecraft:glass_pane")); set(SPX2, y + dy, z, id("minecraft:glass_pane")); }
        }
        for (int y = UG + 1; y <= 85; y++) { set(SPX2 - 1, y, SPZ1 + 1, id("minecraft:ladder[facing=west]")); innReserve(SPX2 - 2, y, SPZ1 + 1); }
        // THE ROOF TERRACE: the pavilion (acacia posts, a hipped roof), strings of peppers from its eaves, spice drying mats
        for (int[] p : new int[][]{{59, -78}, {65, -78}, {59, -72}, {65, -72}}) fill(p[0], 86, p[1], p[0], 88, p[1], id("minecraft:acacia_fence"));   // posts under the eave ring
        roofHip(59, -78, 65, -72, 89, "acacia", 3, acacia);
        for (int[] v : new int[][]{{61, -78}, {63, -78}, {61, -72}, {63, -72}, {59, -75}, {65, -75}}) {
            set(v[0], 88, v[1], id("minecraft:cave_vines_plant[berries=true]"));
            set(v[0], 87, v[1], id("minecraft:cave_vines[age=25,berries=true]"));
        }
        String[] mats = {"orange", "red", "yellow", "brown", "lime"};
        for (int x = SPX1 + 1; x <= SPX2 - 1; x++) set(x, 86, -71, carpet(mats[(x - SPX1) % mats.length]));
        for (int z = -77; z <= -73; z++) set(SPX1 + 2, 86, z, carpet(mats[Math.floorMod(z, mats.length)]));
        set(62, 86, -75, id("minecraft:acacia_slab[type=top]")); set(62, 87, -75, id("minecraft:brewing_stand"));
        for (int x = 61; x <= 63; x += 2) set(x, 86, -75, carpet("red"));
        // the front lawn: a cart of sacks, potted palms, the acacia fence
        set(58, UG + 1, -62, barrel("up")); set(59, UG + 1, -62, barrel("up")); set(58, UG + 2, -62, carpet("orange")); set(59, UG + 2, -62, carpet("red"));
        set(57, UG + 1, -62, id("minecraft:spruce_trapdoor[facing=west,half=bottom,open=true]"));
        for (int x : new int[]{60, 64}) set(x, UG + 1, -68, id("minecraft:potted_bamboo"));
        ubFence(56, 68, 62, "acacia_fence", "acacia_fence_gate");
        furnishSpice();
        ubBox(56, 68);
    }

    private static void furnishSpice() {
        final int g = UG + 1;
        // the arcade: heaps of spice (coloured powder), sacks, the scales, the grinder
        String[] spice = {"orange", "red", "yellow", "brown", "lime", "red"};
        int i = 0;
        for (int x = SPX1 + 1; x <= SPX2 - 1; x++) {
            if (x == 62) continue;
            ip(x, g, -72, id("minecraft:" + spice[i++ % spice.length] + "_concrete_powder"));
        }
        for (int x : new int[]{59, 61, 63, 65}) { ip(x, g, -74, barrel("up")); ip(x, g + 1, -74, carpet(spice[x % spice.length])); }
        ip(60, g, -77, slabTop("acacia")); ip(60, g + 1, -77, id("minecraft:heavy_weighted_pressure_plate"));
        ip(61, g, -77, slabTop("acacia")); ip(61, g + 1, -77, candle("orange", 3));
        ip(59, g, -77, id("minecraft:grindstone[face=floor,facing=north]"));
        hangLantern(62, 74, -75);
        // the store rooms (y76): crates + barrels; the merchant's room (y81): cushions, a low table + the hookah, the bed
        for (int x = 59; x <= 63; x += 2) { ip(x, 76, -78, id("pixelpirates:cargo_crate[facing=south]")); ip(x + 1, 76, -78, barrel("up")); }
        ip(59, 76, -71, barrel("up")); ip(59, 77, -71, barrel("up"));
        hangLantern(62, 79, -74);
        for (int x = 60; x <= 64; x++) for (int z = -76; z <= -73; z++) ip(x, 81, z, carpet(((x + z) & 1) == 0 ? "red" : "orange"));
        ip(59, 81, -71, bed("orange", "south", true)); ip(59, 81, -72, bed("orange", "south", false));
        ip(65, 81, -71, chest("west"));
        hangLantern(62, 84, -74);
    }

    /*
     * #40 THE LAUNDRESS (x72..84): a blue-washed wash-house with a slate roof, a wide open arch to the yard, copper tubs,
     * washboards, the mangle, the copper boiler on its fire, baskets of linen, the ironing table; lines of washing out
     * front (shirts, trousers, sheets). And under a basket by the far wall, beneath a rug: a TRAPDOOR down a ladder to a
     * SMUGGLERS' CELLAR - casks, crates of contraband, bottles, a loot chest, and a tunnel north toward the wall that ends
     * in fallen rubble.
     */
    private static final int LDX1 = 74, LDX2 = 82, LDZ1 = -79, LDZ2 = -69;

    private static void laundress() {
        ubLot(72, 84, 78);
        FOOTPRINTS.add(new int[]{LDX1, LDZ1, LDX2, LDZ2});
        int wash = id("minecraft:light_blue_terracotta"), frame = id("minecraft:stripped_birch_log[axis=y]");
        fill(LDX1, UG - 2, LDZ1, LDX2, UG - 1, LDZ2, COBBLE);
        for (int x = LDX1; x <= LDX2; x++)
            for (int z = LDZ1; z <= LDZ2; z++) {
                boolean ex = x == LDX1 || x == LDX2, ez = z == LDZ1 || z == LDZ2;
                if (!ex && !ez) {
                    set(x, UG, z, ((x + z) & 1) == 0 ? STONE_BRICKS : id("minecraft:smooth_stone"));
                    set(x, 75, z, id("minecraft:birch_planks"));
                    for (int y = UG + 1; y <= 76; y++) if (y != 75) set(x, y, z, AIR);
                    continue;
                }
                set(x, UG, z, STONE_BRICKS);
                for (int y = UG + 1; y <= 76; y++)
                    set(x, y, z, (ex && ez) || (ez && (x - LDX1) % 4 == 0) ? frame : y == UG + 1 ? COBBLE : y == 75 ? id("minecraft:stripped_birch_log[axis=" + (ez ? "x" : "z") + "]") : wash);
            }
        gableRoofX(LDX1, LDX2, LDZ1, LDZ2, 77, "stone_brick", STONE_BRICKS);
        gableEndsX(LDX1, LDZ1 + 1, LDZ2 - 1, 77, wash);
        gableEndsX(LDX2, LDZ1 + 1, LDZ2 - 1, 77, wash);
        // the wide arch to the yard, the door to the parlour, windows, the sign
        for (int x = 76; x <= 80; x++) for (int y = UG + 1; y <= UG + 3; y++) set(x, y, LDZ2, AIR);
        set(76, UG + 3, LDZ2, stairsTop("stone_brick", "west")); set(80, UG + 3, LDZ2, stairsTop("stone_brick", "east"));
        for (int z = -76; z <= -74; z++) for (int y = UG + 2; y <= UG + 3; y++) { set(LDX1, y, z, id("minecraft:glass_pane")); set(LDX2, y, z, id("minecraft:glass_pane")); }
        for (int x : new int[]{76, 77, 79, 80}) for (int y = UG + 2; y <= UG + 3; y++) set(x, y, LDZ1, id("minecraft:glass_pane"));
        bracketSign(LDX2, UG + 4, LDZ2 + 1, "birch", "", "Washing &", "Mending", "");
        for (int y = UG + 1; y <= 75; y++) { set(75, y, LDZ2 - 1, id("minecraft:ladder[facing=east]")); innReserve(76, y, LDZ2 - 1); }
        // THE SMUGGLERS' CELLAR (x76..82 z-79..-73, y64..68) under the floor, the shaft (x80, z-73) + its hidden trapdoor
        for (int x = 76; x <= 82; x++)
            for (int z = -79; z <= -73; z++)
                for (int y = 64; y <= 68; y++) {
                    boolean shell = x == 76 || x == 82 || z == -79 || z == -73 || y == 64 || y == 68;
                    set(x, y, z, shell ? (y == 64 ? STONE_BRICKS : ((x * 7 + y * 3 + z) % 5 == 0 ? MOSSY_COBBLE : COBBLE)) : AIR);
                }
        for (int y = 65; y <= 69; y++) { set(80, y, -73, id("minecraft:ladder[facing=north]")); set(80, y, -72, y == 69 ? DIRT : COBBLE); }
        set(80, UG, -73, id("minecraft:spruce_trapdoor[facing=north,half=top,open=false]"));
        set(80, UG + 1, -73, carpet("white"));                                               // the rug over it
        // the tunnel north toward the wall, ending in fallen rubble
        for (int z = -84; z <= -80; z++)
            for (int x = 78; x <= 80; x++)
                for (int y = 64; y <= 67; y++) {
                    boolean shell = x != 79 || y == 64 || y == 67;
                    set(x, y, z, shell ? COBBLE : z <= -83 ? (y == 65 ? GRAVEL : COBBLE) : AIR);
                }
        set(79, 65, -79, AIR); set(79, 66, -79, AIR);
        set(79, 67, -81, id("minecraft:oak_log[axis=x]"));                                    // a pit prop overhead
        // the washing lines out front: shirts, trousers, a sheet
        for (int z : new int[]{-60, -55}) {
            for (int x : new int[]{73, 83}) fill(x, UG + 1, z, x, UG + 6, z, id("minecraft:birch_fence"));
            for (int x = 74; x <= 82; x++) set(x, UG + 6, z, id("minecraft:chain[axis=x]"));
        }
        String[] col = {"red", "white", "light_blue", "yellow", "pink", "white"};
        for (int k = 0; k < 2; k++) {                                                       // shirts: shoulders + body
            int x = 75 + k * 4; String c = col[k];
            for (int dx = -1; dx <= 1; dx++) set(x + dx, UG + 5, -60, wool(c));
            set(x, UG + 4, -60, wool(c));
        }
        set(82, UG + 5, -60, wool("blue")); set(81, UG + 5, -60, wool("blue")); set(81, UG + 4, -60, wool("blue")); set(82, UG + 4, -60, wool("blue"));  // trousers
        set(82, UG + 3, -60, wool("blue"));
        for (int x = 75; x <= 80; x++) for (int y = UG + 3; y <= UG + 5; y++) set(x, y, -55, wool((x & 1) == 0 ? "white" : "light_gray"));   // a sheet
        set(81, UG + 5, -55, wool("pink")); set(81, UG + 4, -55, wool("pink"));
        set(73, UG + 1, -63, barrel("up")); set(73, UG + 2, -63, carpet("white")); set(74, UG + 1, -63, CAULDRON_WATER);
        ubFence(72, 84, 78, "birch_fence", "birch_fence_gate");
        furnishLaundress();
        ubBox(72, 84);
    }

    private static void furnishLaundress() {
        final int g = UG + 1;
        // the wash-house: tubs + washboards along the back, the mangle, the copper boiler on its fire, baskets, ironing
        for (int x = 75; x <= 77; x++) { ip(x, g, -78, CAULDRON_WATER); ip(x, g + 1, -78, id("minecraft:birch_trapdoor[facing=south,half=bottom,open=true]")); }
        ip(79, g, -78, id("minecraft:grindstone[face=floor,facing=south]"));
        ip(81, g, -78, CAMPFIRE); ip(81, g + 1, -78, id("minecraft:waxed_cut_copper"));
        ip(81, g, -73, barrel("up")); ip(81, g + 1, -73, carpet("white"));                    // the basket beside the trapdoor
        ip(75, g, -74, barrel("up")); ip(75, g + 1, -74, id("minecraft:light_blue_wool"));
        for (int x = 77; x <= 78; x++) ip(x, g, -71, slabTop("birch"));
        ip(77, g + 1, -71, id("minecraft:heavy_weighted_pressure_plate")); ip(78, g + 1, -71, id("minecraft:white_carpet"));
        hangLantern(78, 74, -75);
        // the loft: her bed, a chest, a sewing corner
        ip(81, 76, -78, bed("light_blue", "east", true)); ip(80, 76, -78, bed("light_blue", "east", false));
        ip(75, 76, -78, chest("south")); ip(76, 76, -78, id("minecraft:loom[facing=south]"));
        rug(77, -76, 80, -73, 76, "light_blue", "white");
        // THE CELLAR: casks, contraband crates, bottles, a loot chest, a lantern
        ip(77, 65, -78, barrel("east")); ip(77, 66, -78, barrel("east")); ip(78, 65, -78, barrel("east"));
        ip(81, 65, -78, id("pixelpirates:cargo_crate[facing=west]")); ip(81, 66, -78, id("pixelpirates:cargo_crate[facing=west]")); ip(81, 65, -77, id("pixelpirates:cargo_crate[facing=west]"));
        ip(77, 65, -75, barrel("up")); ip(77, 66, -75, id("pixelpirates:spirit_bottles[facing=east,count=4]"));
        ip(78, 65, -74, slabTop("spruce")); ip(78, 66, -74, LANTERN);
        if (getRaw(81, 65, -75) == AIR) lootChest(81, 65, -75, "west", "pixelpirates:chests/phase1_smuggler");
    }

    /*
     * #41 THE MARINE PAINTER (x96..108): a studio under a lean-to roof of sea-green prismarine brick that rises to a tall
     * NORTH-LIGHT wall of glass (the painter's steady light), pale birch walls in a dark-oak frame. Inside, double height:
     * easels with seascapes on them (patterned banners - a calm sea with the sun, a sunset, a storm), finished paintings
     * hung on the walls, the paint table and palette, a galleon in a bottle for a model; a mezzanine bedroom over the door.
     * Out front an easel on the lawn facing the harbour, and a cottage garden.
     */
    private static final int PNX1 = 98, PNX2 = 106, PNZ1 = -79, PNZ2 = -69;

    /** The lean-to roof's height over row z: 84 at the north wall falling to 78 over the south eave. */
    private static int pnRoof(int z) { return 84 - (z - (PNZ1 - 1)) / 2; }

    private static void marinePainter() {
        ubLot(96, 108, 101);
        FOOTPRINTS.add(new int[]{PNX1, PNZ1, PNX2, PNZ2});
        int birch = id("minecraft:birch_planks"), post = id("minecraft:stripped_dark_oak_log[axis=y]"), prism = id("minecraft:prismarine_bricks");
        fill(PNX1, UG - 2, PNZ1, PNX2, UG - 1, PNZ2, COBBLE);
        for (int x = PNX1; x <= PNX2; x++)
            for (int z = PNZ1; z <= PNZ2; z++) {
                boolean ex = x == PNX1 || x == PNX2, ez = z == PNZ1 || z == PNZ2;
                int top = pnRoof(z) - 1;
                if (!ex && !ez) {
                    set(x, UG, z, ((x + z) & 1) == 0 ? SPRUCE : id("minecraft:stripped_spruce_wood"));
                    for (int y = UG + 1; y <= top; y++) set(x, y, z, AIR);
                    continue;
                }
                set(x, UG, z, STONE_BRICKS);
                for (int y = UG + 1; y <= top; y++)
                    set(x, y, z, (ex && ez) || (ez && (x - PNX1) % 4 == 0) || (ex && (z - PNZ1) % 5 == 0) ? post : y == UG + 1 ? STONE_BRICKS : birch);
            }
        // the lean-to roof: stairs where it steps down, blocks between; it overhangs one row each way
        for (int z = PNZ1 - 1; z <= PNZ2 + 1; z++)
            for (int x = PNX1 - 1; x <= PNX2 + 1; x++)
                set(x, pnRoof(z), z, pnRoof(z + 1) < pnRoof(z) ? stairs("prismarine_brick", "north") : prism);
        // the north-light wall: tall glass up under the roof
        for (int x = PNX1 + 1; x <= PNX2 - 1; x++) if ((x - PNX1) % 4 != 0) for (int y = 75; y <= pnRoof(PNZ1) - 2; y++) set(x, y, PNZ1, id("minecraft:glass_pane"));
        // the front: the door, a window, the sign; side windows
        innDoor(101, UG, PNZ2, "dark_oak", true);
        for (int x : new int[]{103, 104, 105}) for (int y = UG + 2; y <= UG + 3; y++) set(x, y, PNZ2, id("minecraft:glass_pane"));
        bracketSign(99, UG + 4, PNZ2 + 1, "dark_oak", "", "Seascapes", "& Portraits", "");
        for (int z = -77; z <= -76; z++) for (int y = UG + 2; y <= UG + 4; y++) { set(PNX1, y, z, id("minecraft:glass_pane")); set(PNX2, y, z, id("minecraft:glass_pane")); }
        // the mezzanine over the door (y76, z-73..-70) + its rail + ladder
        for (int x = PNX1 + 1; x <= PNX2 - 1; x++) for (int z = -73; z <= -70; z++) set(x, 76, z, SPRUCE);
        for (int x = PNX1 + 1; x <= PNX2 - 2; x++) set(x, 77, -74, SPRUCE_FENCE);
        for (int y = UG + 1; y <= 76; y++) { set(PNX2 - 1, y, -74, id("minecraft:ladder[facing=west]")); innReserve(PNX2 - 2, y, -74); }
        // the front lawn: an easel facing the harbour + a stool, a cottage garden, the fence
        set(103, UG + 1, -60, id("minecraft:spruce_fence"));
        standingBanner(103, UG + 2, -60, "light_blue", 0, 11, "hhb", 4, "mc", 12, "bo");
        set(103, UG + 1, -61, id("minecraft:spruce_slab"));
        String[] fl = {"minecraft:poppy", "minecraft:cornflower", "minecraft:azure_bluet", "minecraft:oxeye_daisy", "minecraft:pink_tulip"};
        for (int x = 97; x <= 100; x++) for (int z = -60; z <= -56; z++) set(x, UG + 1, z, id(fl[Math.floorMod(x * 5 + z, fl.length)]));
        ubFence(96, 108, 101, "dark_oak_fence", "dark_oak_fence_gate");
        furnishPainter();
        ubBox(96, 108);
    }

    /** The painter's work in progress (32 x 32 ARGB, as SNBT): a dawn sky, the sun on the horizon, the sea, a sail. */
    private static String seascape() {
        StringBuilder sb = new StringBuilder("[I;");
        for (int y = 0; y < 32; y++)
            for (int x = 0; x < 32; x++) {
                int c;
                if (y < 18) {                                                   // the sky: blue above, gold at the horizon
                    float f = y / 17f;
                    int r = (int) (110 + 140 * f), gg = (int) (160 + 50 * f), b = (int) (225 - 120 * f);
                    c = r << 16 | gg << 8 | b;
                    if ((x - 20) * (x - 20) + (y - 16) * (y - 16) <= 12) c = 0xFFF2C0;                 // the sun
                    if (y == 6 && x >= 4 && x <= 9 || y == 7 && x >= 3 && x <= 11) c = 0xF4EEE4;         // a cloud
                } else {                                                        // the sea, darker further down, a glitter path
                    float f = (y - 18) / 13f;
                    c = (int) (40 - 20 * f) << 16 | (int) (110 - 50 * f) << 8 | (int) (170 - 60 * f);
                    if (Math.abs(x - 20) <= 1 + (y - 18) / 3 && (x + y) % 3 == 0) c = 0xF6D890;
                    if ((x * 7 + y * 13) % 23 == 0) c = 0xD8E8F0;
                }
                if (x >= 6 && x <= 12 && y >= 15 && y <= 17 && x - 6 >= (17 - y) - 1) c = 0x5A3A22;            // a hull
                if (x == 9 && y >= 7 && y <= 14) c = 0x5A3A22;                                                     // its mast
                if (x >= 10 && x <= 13 && y >= 8 && y <= 13 && x - 10 <= (y - 8)) c = 0xF2EAD8;                    // the sail
                sb.append(c | 0xFF000000).append(y == 31 && x == 31 ? "" : ",");
            }
        return sb.append("]").toString();
    }

    private static void furnishPainter() {
        final int g = UG + 1;
        // the easels: a calm sea with the sun, a sunset, a storm
        Object[][] canvases = {{"light_blue", new Object[]{11, "hhb", 4, "mc", 12, "bo"}},
                {"orange", new Object[]{14, "gra", 11, "hhb", 4, "mc", 12, "bo"}},
                {"gray", new Object[]{15, "gra", 9, "hhb", 0, "bt", 12, "bo"}}};
        int[][] easel = {{100, -77}, {103, -76}, {105, -78}};
        for (int i = 0; i < 3; i++) {
            int x = easel[i][0], z = easel[i][1];
            if (getRaw(x, g, z) != AIR || getRaw(x, g + 1, z) != AIR) continue;
            if (i == 1) {                                                   // a real easel (homestead/art): paint on it
                set(x, g, z, id("pixelpirates:easel[facing=south,half=lower]"));
                set(x, g + 1, z, id("pixelpirates:easel[facing=south,half=upper]"));
                nbt(x, g, z, "{Size:0b,Title:\"Harbour at Dawn\",Npc:\"isadora\",Pixels:" + seascape() + "}");   // Npc: Isadora's own (homestead/town)
                ip(x, g, z + 1, stairs("spruce", "south"));
                continue;
            }
            set(x, g, z, id("minecraft:spruce_fence"));
            standingBanner(x, g + 1, z, (String) canvases[i][0], 0, (Object[]) canvases[i][1]);
            ip(x, g, z + 1, stairs("spruce", "south"));
        }
        // finished paintings on the side walls
        patternBanner(PNX1 + 1, g + 3, -71, "light_blue", "east", 11, "hhb", 0, "tt", 12, "bo");
        patternBanner(PNX2 - 1, g + 3, -72, "cyan", "west", 15, "gra", 0, "mc", 12, "bo");
        patternBanner(PNX1 + 1, g + 3, -78, "pink", "east", 1, "gru", 4, "mc", 12, "bo");
        // the paint table, the palette, the model
        ip(99, g, -73, slabTop("spruce")); ip(99, g + 1, -73, candle("red", 2));
        ip(100, g, -73, slabTop("spruce")); ip(100, g + 1, -73, candle("blue", 3));
        ip(101, g, -73, slabTop("spruce")); ip(101, g + 1, -73, id("pixelpirates:ship_in_bottle_galleon[facing=north]"));
        ip(99, g, -70, barrel("up")); ip(99, g + 1, -70, id("minecraft:decorated_pot"));
        hangLantern(102, 82, -77);
        // the mezzanine: bed, chest, a lantern
        ip(103, 77, -71, bed("cyan", "east", true)); ip(102, 77, -71, bed("cyan", "east", false));
        ip(99, 77, -71, chest("east")); ip(100, 77, -72, slabTop("spruce")); ip(100, 78, -72, LANTERN);
    }

    /** A hipped roof over x1..x2 / z1..z2 (the eaves included): `rings` courses of stairs climbing inward from y0, then a
     *  flat deck of `deck` over everything further in (a widow's walk / a lantern platform). */
    private static void roofHip(int x1, int z1, int x2, int z2, int y0, String mat, int rings, int deck) {
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        String[] dn = {"east", "west", "south", "north"};
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                int h = Math.min(Math.min(x - x1, x2 - x), Math.min(z - z1, z2 - z));
                if (h >= rings) { set(x, y0 + rings, z, deck); continue; }
                String up = null;
                for (int d = 0; d < 4 && up == null; d++) {
                    int ax = x + dirs[d][0], az = z + dirs[d][1];
                    if (ax < x1 || ax > x2 || az < z1 || az > z2) continue;
                    if (Math.min(Math.min(ax - x1, x2 - ax), Math.min(az - z1, z2 - az)) == h + 1) up = dn[d];
                }
                set(x, y0 + h, z, up != null ? stairs(mat, up) : id("minecraft:" + mat + (mat.contains("copper") ? "" : "_planks")));
            }
    }

    // ------------------------------------------------------------------
    // City wall + gatehouse
    // ------------------------------------------------------------------

    // ------------------------------------------------------------------
    // #42 THE NORTH WALL + THE GREAT NORTH GATE (rebuilt 2026-10-04 with the user: "refurbish and improve the wall greatly,
    // make sure the path can reach the top level of the island, make it look epic")
    // ------------------------------------------------------------------
    // The city sits in a bowl cut into the hill: inside the wall the ground is y70, outside it the hillside is ~y81. The old
    // wall (3 thick, top y76) was buried on its north side and the gate opened onto an 11-block bank of earth - no way
    // out. Now: a curtain wall 5 thick (z-91..-87; the city face stays at z-87 - the chapel and manor gardens touch z-86)
    // with its wall-walk at y85 (15 tall from the city, merlons standing clear of the hill), six D-shaped towers, THE GREAT
    // NORTH GATE between two drum towers, and THE PROCESSIONAL WAY: a ramp climbing 10 blocks through a walled cutting to
    // the hill, ending at a pair of brazier pylons - where the north paths now start.
    // Everything here is under the "Gatehouse" label (map #42) so /ppisland restamp 42 refreshes the whole wall.

    private static final int NWG = 70, NWT = 85, WZN = -91, WZS = -87;          // city ground, wall-walk floor, north + city faces

    /** The wall's face stone: stone brick weathered by height (mossier low down), with the odd pirate-stone block. */
    private static int wallStone(int x, int y, int z) {
        int h = Math.floorMod(x * 734287 + y * 912931 + z * 129241, 100);
        if (h < 6) return PIRATE_STONE_BRICKS;
        if (h < 6 + (y < NWG + 6 ? 18 : 6)) return MOSSY_STONE_BRICKS;
        if (h < 30) return CRACKED_STONE_BRICKS;
        return STONE_BRICKS;
    }

    private static void cityWall() {
        named("Gatehouse", () -> {
            clearOldWall();
            northWall();
            for (int tx : new int[]{-136, -96, -48, 48, 96, 136}) wallTowerD(tx, 5, NWT + 10);
            greatGate();
            processionalWay();
        });
    }

    /** The old towers stood out to z-85 and the old gate kept lanterns + banners at z-84: cleared, so a restamp removes them. */
    private static void clearOldWall() {
        for (int tx : new int[]{-136, -96, -48, -8, 8, 48, 96, 136})
            for (int x = tx - 3; x <= tx + 3; x++)
                for (int z = -86; z <= -84; z++) {
                    if (!SpawnIslandTerrain.inCityFootprint(x, z)) continue;
                    for (int y = NWG + 1; y <= NWG + 11; y++) if (getRaw(x, y, z) == 0) set(x, y, z, AIR);
                    if (z >= -86 && getRaw(x, NWG, z) == 0) set(x, NWG, z, GRASS);
                }
        for (int x = -5; x <= 5; x++) for (int y = NWG + 1; y <= NWG + 8; y++) for (int z = -86; z <= -84; z++) if (getRaw(x, y, z) == 0) set(x, y, z, AIR);
    }

    /** The curtain wall, x-136..136 (the gate complex owns x-15..15). */
    private static void northWall() {
        for (int x = -136; x <= 136; x++) {
            if (Math.abs(x) <= 15) continue;
            if (!SpawnIslandTerrain.isLand(x, -89)) continue;
            for (int z = WZN; z <= WZS; z++) {
                for (int y = NWG - 4; y < NWT; y++) set(x, y, z, (z == WZN || z == WZS) ? wallStone(x, y, z) : STONE_BRICKS);
                set(x, NWT, z, z == -89 ? POLISHED_ANDESITE : STONE_BRICKS);                                   // the wall-walk
                for (int y = NWT + 1; y <= 100; y++) set(x, y, z, AIR);
            }
            // the city face: a string course, the corbel table under the walk, pilasters every 12
            set(x, NWG + 8, WZS, POLISHED_ANDESITE);
            set(x, NWT - 1, WZS, id("minecraft:chiseled_stone_bricks"));
            if (free(x, NWT - 1, WZS + 1)) set(x, NWT - 1, WZS + 1, stairsTop("stone_brick", "north"));
            if (Math.floorMod(x, 12) == 6) {
                boolean clear = true;
                for (int y = NWG + 1; y <= NWT - 2; y++) if (!free(x, y, WZS + 1)) { clear = false; break; }
                if (clear) {
                    for (int y = NWG + 1; y <= NWT - 3; y++) set(x, y, WZS + 1, y <= NWG + 2 ? id("minecraft:chiseled_stone_bricks") : wallStone(x, y, WZS + 1));
                    set(x, NWT - 2, WZS + 1, stairs("stone_brick", "north"));
                }
            }
            // the parapets: merlons + crenels on the north, a low rail with lanterns on the city side
            set(x, NWT + 1, WZN, wallStone(x, NWT + 1, WZN));
            if (Math.floorMod(x, 4) < 2) { set(x, NWT + 2, WZN, wallStone(x, NWT + 2, WZN)); set(x, NWT + 3, WZN, id("minecraft:stone_brick_slab")); }
            set(x, NWT + 1, WZS, STONE_BRICK_WALL);
            if (Math.floorMod(x, 8) == 4) set(x, NWT + 2, WZS, LANTERN);
            // the north face: corbels under the parapet, arrow loops, a battered foot on the hillside
            int s = surf(x, WZN - 1);
            if (s < NWT - 1) set(x, NWT - 1, WZN - 1, stairsTop("stone_brick", "south"));
            if (Math.floorMod(x, 8) == 0 && s < NWT - 4) for (int y = NWT - 3; y <= NWT - 2; y++) set(x, y, WZN, IRON_BARS);
            if (s + 1 < NWT - 2 && SpawnIslandTerrain.isLand(x, WZN - 1)) set(x, s + 1, WZN - 1, stairs("stone_brick", "south"));
            // banners down the city face
            if (Math.floorMod(x, 24) == 12 && free(x, NWT - 3, WZS + 1)) jollyRoger(x, NWT - 3, WZS + 1, "south");
        }
    }

    /** The north wall, its towers' fronts and the processional way: the countryside passes keep off. */
    private static boolean wallZone(int x, int z) { return z >= -96 || (Math.abs(x) <= 6 && z >= -118); }

    /** Untouched or air: a decoration may go here. */
    private static boolean free(int x, int y, int z) { int v = getRaw(x, y, z); return v == 0 || v == AIR; }

    /** A black banner with the skull and crossed bones. */
    private static void jollyRoger(int x, int y, int z, String facing) {
        patternBanner(x, y, z, "black", facing, 0, "cr", 0, "sku");
    }

    /**
     * A D-shaped tower on the wall: its flat back is the city face (z-87), its round front pushes out over the hillside
     * (radius r round (tx, -89)). Floors at the city (y70), at the wall-walk (y85) and the top (top), doors from the city
     * and onto the walk both ways, a ladder up the back wall, arrow loops, crenellated top with a brazier and a flag.
     */
    private static void wallTowerD(int tx, int r, int top) {
        int cz = -89;
        double R = r + 0.4;
        for (int x = tx - r - 1; x <= tx + r + 1; x++)
            for (int z = cz - r - 1; z <= WZS; z++) {
                double d = Math.hypot(x - tx, z - cz);
                if (d > R) continue;
                boolean edge = d > R - 1.2 || z == WZS;
                for (int y = NWG - 4; y <= top; y++) {
                    boolean floor = y == NWG || y == NWT || y == top;
                    set(x, y, z, edge ? wallStone(x, y, z) : y <= NWG ? STONE_BRICKS : floor ? SPRUCE : AIR);
                }
                for (int y = top + 1; y <= 100; y++) set(x, y, z, AIR);
                if (edge) {
                    set(x, top + 1, z, wallStone(x, top + 1, z));
                    if (Math.floorMod(x + z, 2) == 0) set(x, top + 2, z, id("minecraft:stone_brick_slab"));
                }
                if (edge && y_band(x, z)) set(x, top - 1, z, id("minecraft:chiseled_stone_bricks"));
            }
        // doors: from the city (back wall), onto the wall-walk east + west
        innDoor(tx + 2, NWG, WZS, "spruce", true);
        for (int s : new int[]{-1, 1}) for (int y = NWT + 1; y <= NWT + 2; y++) for (int k = 0; k <= 1; k++) set(tx + s * (r - k), y, cz, AIR);
        // the ladder up the back wall
        for (int y = NWG + 1; y <= top; y++) { set(tx - 2, y, WZS - 1, LADDER_N); innReserve(tx - 2, y, WZS - 2); }
        // arrow loops round the front
        for (int y : new int[]{NWG + 9, NWT + 5})
            for (int[] o : new int[][]{{0, -r}, {-r, 0}, {r, 0}}) if (cz + o[1] < WZS) set(tx + o[0], y, cz + o[1], IRON_BARS);
        hangLantern(tx, NWT - 1, cz); hangLantern(tx, top - 1, cz);
        // windows in the flat back (the city side), a band of chiseled stone under the battlements
        for (int wx : r >= 6 ? new int[]{tx, tx + 3} : new int[]{tx})
            for (int y : new int[]{NWG + 8, NWG + 9, NWT + 4, NWT + 5}) set(wx, y, WZS, id("minecraft:glass_pane"));
        for (int x = tx - r; x <= tx + r; x++) if (getRaw(x, top - 1, WZS) != AIR) set(x, top - 1, WZS, id("minecraft:chiseled_stone_bricks"));
        // the top: a brazier, the flag
        set(tx, top + 1, cz, CAMPFIRE);
        fill(tx + 2, top + 1, cz - 2, tx + 2, top + 7, cz - 2, id("minecraft:stripped_spruce_log[axis=y]"));
        jollyRoger(tx + 3, top + 6, cz - 2, "east");
    }

    /** The decorative band near the tower tops: every other face cell, just under the crenellation. */
    private static boolean y_band(int x, int z) { return Math.floorMod(x * 3 + z, 2) == 0; }

    /*
     * THE GREAT NORTH GATE: two drum towers (D-shaped, r6, round (+-9, -89)) rising to y98, the GATEHOUSE between them over
     * a vaulted passage (x-2..2, z-95..-87): the portcullis half-raised on its chains, murder holes in the vault, the guard
     * room above (gun ports with cannons, the windlass, powder, racks), machicolations and the plaque WAVEBREAK PORT over
     * the outer arch, Jolly Rogers either side, braziers on the towers and a great black flag above it all.
     */
    private static final int GT_TOP = 97;

    private static void greatGate() {
        for (int s : new int[]{-1, 1}) wallTowerD(s * 9, 6, GT_TOP);
        // the gatehouse body between the towers (recessed: the drums stand out in front of it) over the passage
        for (int x = -2; x <= 2; x++)
            for (int z = WZN; z <= WZS; z++) {
                for (int y = NWG - 4; y <= NWT; y++) {
                    boolean passage = y > NWG && y <= NWG + 6, room = y >= NWG + 8 && y <= NWT - 1 && z > WZN && z < WZS;
                    int b = (z == WZN || z == WZS) ? wallStone(x, y, z) : STONE_BRICKS;
                    if (y == NWG) b = Math.abs(x) <= 1 ? POLISHED_ANDESITE : STONE_BRICKS;           // the road through
                    if (y == NWG + 7 && z > WZN && z < WZS) b = SPRUCE;                               // the guard room floor
                    if (y == NWT) b = x == 0 ? POLISHED_ANDESITE : STONE_BRICKS;                    // the walk over the gate
                    set(x, y, z, passage || room ? AIR : b);
                }
                for (int y = NWT + 1; y <= 100; y++) set(x, y, z, AIR);
            }
        // the vault: rounded corners, ribs at both mouths, murder holes; the portcullis half up on its chains
        for (int z = WZN; z <= WZS; z++) { set(-2, NWG + 6, z, stairsTop("stone_brick", "west")); set(2, NWG + 6, z, stairsTop("stone_brick", "east")); }
        for (int z : new int[]{WZN, WZS}) for (int x = -1; x <= 1; x++) set(x, NWG + 7, z, id("minecraft:chiseled_stone_bricks"));
        set(0, NWG + 7, -89, IRON_BARS);
        for (int x = -1; x <= 1; x++) for (int y = NWG + 5; y <= NWG + 6; y++) set(x, y, -90, IRON_BARS);
        hangLantern(0, NWG + 6, -88);
        // the guard room: the windlass + its chains, two cannons at gun ports, powder, racks
        for (int x = -2; x <= 2; x++) set(x, NWG + 11, -90, id("minecraft:stripped_spruce_log[axis=x]"));
        for (int x : new int[]{-1, 1}) for (int y = NWG + 7; y <= NWG + 10; y++) set(x, y, -90, CHAIN);
        for (int x : new int[]{-2, 2}) {
            set(x, NWG + 8, -90, id("pixelpirates:ship_cannon[facing=north,loaded=false]"));
            for (int y = NWG + 8; y <= NWG + 9; y++) set(x, y, WZN, AIR);                             // the gun ports
        }
        set(0, NWG + 8, -88, GUNPOWDER_BARREL); set(-2, NWG + 8, -88, barrel("up")); set(2, NWG + 8, -88, id("minecraft:coal_block"));
        set(0, NWG + 8, -89, slabTop("spruce")); set(0, NWG + 9, -89, LANTERN);
        // doors from the drum towers into the guard room, and a floor in the towers to meet it
        for (int s : new int[]{-1, 1}) for (int y = NWG + 8; y <= NWG + 9; y++) set(s * 3, y, -89, AIR);
        for (int s : new int[]{-1, 1})
            for (int x = s * 4; Math.abs(x) <= 14; x += s)
                for (int z = -95; z <= -88; z++) if (getRaw(x, NWG + 7, z) == AIR) set(x, NWG + 7, z, SPRUCE);
        // the parapet over the gate, machicolations, the plaque + Jolly Rogers on the outer face; the inner face
        for (int x = -2; x <= 2; x++) {
            set(x, NWT + 1, WZN, wallStone(x, NWT + 1, WZN));
            if ((x & 1) == 0) set(x, NWT + 2, WZN, wallStone(x, NWT + 2, WZN));
            set(x, NWT - 1, WZN - 1, stairsTop("stone_brick", "south"));
            set(x, NWT + 1, WZS, STONE_BRICK_WALL);
        }
        set(0, NWG + 9, WZN, id("minecraft:chiseled_stone_bricks"));
        set(0, NWG + 8, WZN - 1, id("minecraft:dark_oak_wall_sign[facing=north]"));
        signText(0, NWG + 8, WZN - 1, "", "WAVEBREAK", "PORT", "");
        for (int x : new int[]{-2, 2}) jollyRoger(x, NWG + 11, WZN - 1, "north");
        for (int x : new int[]{-2, 2}) jollyRoger(x, NWG + 11, WZS + 1, "south");
        set(0, NWG + 8, WZS + 1, id("minecraft:dark_oak_wall_sign[facing=south]"));
        signText(0, NWG + 8, WZS + 1, "", "To the", "North Downs", "");
        for (int s : new int[]{-1, 1}) for (int y = NWG + 1; y <= NWG + 6; y++)                           // the inner arch's frame + hood
            set(s * 3, y, WZS + 1, y == NWG + 1 || y == NWG + 6 ? id("minecraft:chiseled_stone_bricks") : id("minecraft:polished_andesite"));
        for (int x = -3; x <= 3; x++) set(x, NWG + 7, WZS + 1, stairsTop("stone_brick", "north"));
        for (int s : new int[]{-1, 1}) {                                                                  // braziers by the inner arch
            set(s * 4, NWG + 1, WZS + 2, id("minecraft:chiseled_stone_bricks"));
            set(s * 4, NWG + 2, WZS + 2, CAMPFIRE);
        }
        // the great flag over the gatehouse: a pole from the walk, the Jolly Roger in wool (7 x 5) above the towers
        fill(0, NWT + 1, -89, 0, GT_TOP + 11, -89, id("minecraft:stripped_spruce_log[axis=y]"));
        String[] flag = {"kkwwwkk", "kkwkwkk", "kkwwwkk", "wkkwkkw", "kwkkkwk"};
        for (int i = 0; i < flag.length; i++)
            for (int j = 0; j < 7; j++) set(1 + j, GT_TOP + 10 - i, -89, wool(flag[i].charAt(j) == 'w' ? "white" : "black"));
        set(0, GT_TOP + 12, -89, id("minecraft:lightning_rod"));
    }

    /** Inside one of the gate's drum towers (round (+-9, -89), r 6)? */
    private static boolean gateTower(int x, int z) { return z <= WZS && (Math.hypot(x - 9, z + 89) <= 6.4 || Math.hypot(x + 9, z + 89) <= 6.4); }

    /*
     * THE PROCESSIONAL WAY: from the gate's outer mouth (z-96) the road climbs at half a block per block - full block, then
     * slab - to the hill at z-116 (y80), between retaining walls that start 12 tall at the gate and sink into the hillside
     * at the top (lanterns on their parapets, ivy on their faces), and ends at two brazier pylons joined by a beam with the
     * port's sign. The north paths start there (northPaths / countryside).
     */
    private static void processionalWay() {
        for (int z = WZN - 1; z >= -117; z--) {
            int k = Math.max(0, Math.min(20, -96 - z));
            int base = NWG + k / 2;                                       // the full block of the road at this row
            boolean half = (k & 1) == 1 && k < 20;
            for (int x = -5; x <= 5; x++) {
                if (gateTower(x, z)) continue;                                       // the drums' fronts flank the court
                int t = surf(x, z);
                if (Math.abs(x) <= 3) {
                    for (int y = NWG - 3; y <= base; y++) set(x, y, z, y < base ? STONE_BRICKS : Math.abs(x) <= 1 ? POLISHED_ANDESITE : STONE_BRICKS);
                    if (half) set(x, base + 1, z, id(Math.abs(x) <= 1 ? "minecraft:polished_andesite_slab" : "minecraft:stone_brick_slab"));
                    int clear = z >= WZN - 1 ? NWG + 6 : Math.max(t, base) + 6;            // in front of the gate: keep its plaque + banners
                    for (int y = base + (half ? 2 : 1); y <= clear; y++) set(x, y, z, AIR);
                } else {
                    int wallTop = Math.max(t + 1, base + 2);
                    for (int y = NWG - 3; y <= wallTop; y++) set(x, y, z, wallStone(x, y, z));
                    set(x, wallTop, z, POLISHED_ANDESITE);
                    for (int y = wallTop + 1; y <= wallTop + 4; y++) if (getRaw(x, y, z) == 0) set(x, y, z, AIR);
                    if (Math.abs(x) == 5 && Math.floorMod(z, 4) == 0) { set(x, wallTop + 1, z, STONE_BRICK_WALL); set(x, wallTop + 2, z, LANTERN); }
                    if (Math.abs(x) == 4 && Math.floorMod(z * 7 + x, 5) == 0 && base + 3 < wallTop)
                        for (int y = base + 3; y < wallTop; y++) set(x - Integer.signum(x), y, z, id("minecraft:vine[" + (x > 0 ? "east" : "west") + "=true]"));
                }
            }
        }
        // the pylons at the top, the beam, the sign
        int pt = surf(0, -117) + 1;
        for (int s : new int[]{-1, 1}) {
            int px = s * 5;
            for (int y = pt - 3; y <= pt + 6; y++) set(px, y, -117, y >= pt + 5 ? id("minecraft:chiseled_stone_bricks") : wallStone(px, y, -117));
            set(px, pt + 7, -117, CAMPFIRE);
        }
        for (int x = -4; x <= 4; x++) set(x, pt + 5, -117, DARK_OAK_LOG_X);
        hangingSign(0, pt + 4, -117, "dark_oak", "", "Wavebreak", "Port", "");
        for (int x = -3; x <= 3; x++) for (int y = pt; y <= pt + 3; y++) if (getRaw(x, y, -117) == 0) set(x, y, -117, AIR);
    }


    // ------------------------------------------------------------------
    // North half: lighthouse, fort, paths, palms
    // ------------------------------------------------------------------

    /*
     * #43 THE WAVEBREAK LIGHT (rebuilt 2026-10-04): on the east headland, an octagonal stone BASTION (parapet, lamps, steps up
     * from the path on the west) carries a tall tapering tower painted in SPIRAL red-and-white bands. Inside, a real SPIRAL
     * STAIRCASE winds up round a stone newel to the watch room (the keeper's chart table, the logbook, the oil store); a
     * ladder climbs into the LANTERN ROOM - glass in an iron frame, the great lamp: a BEACON on its iron base shining up
     * through a yellow glass oculus in the copper dome, a golden beam into the night sky that ships can steer by. A railed
     * GALLERY runs round it. On the bastion: the keeper's cottage on the seaward side, the FOG BELL on its frame, a signal
     * cannon, a flagstaff of signal flags, the name plaque. The layout's grid stops at y118 - everything fits under it.
     */
    private static final int LHX = 108, LHZ = -102, LHB = 85;                     // centre, the bastion floor
    private static final int LHG = LHB + 21;                                       // the watch-room floor (top of the stair)

    /** The tower's radius at height y (tapering from 4.6 to 3.2). */
    private static double lhR(int y) { return 4.6 - (y - LHB) * (1.4 / 21.0); }

    private static void lighthouse() {
        int calcite = id("minecraft:calcite"), red = RED_TERRACOTTA, copper = id("minecraft:waxed_weathered_cut_copper");
        // clear the old lighthouse + its platform
        for (int x = LHX - 11; x <= LHX + 11; x++) for (int z = LHZ - 11; z <= LHZ + 11; z++)               // only over the bastion: the wall's
            if (Math.abs(x - LHX) + Math.abs(z - LHZ) <= 14 && z <= -93) for (int y = LHB + 1; y <= Y1; y++) set(x, y, z, AIR);   // tower + foot stay
        // THE BASTION: an octagon r10 at y85, a stone skirt down to the hillside, the parapet with lamps, steps in on the west
        for (int x = LHX - 10; x <= LHX + 10; x++)
            for (int z = LHZ - 10; z <= LHZ + 10; z++) {
                int dx = Math.abs(x - LHX), dz = Math.abs(z - LHZ);
                if (dx + dz > 14 || z > -93) continue;                                 // (z-92 is the wall's foot)
                boolean rim = dx == 10 || dz == 10 || dx + dz == 14 || z == -93;
                int s = surf(x, z);
                for (int y = Math.min(s, LHB) - 3; y < LHB; y++) set(x, y, z, rim ? wallStone(x, y, z) : COBBLE);
                set(x, LHB, z, rim ? STONE_BRICKS : ((dx + dz) % 4 == 0 ? POLISHED_ANDESITE : STONE_BRICKS));
                if (rim) {
                    boolean gap = x < LHX && dz <= 1;
                    if (!gap) set(x, LHB + 1, z, STONE_BRICK_WALL);
                    if (!gap && (dx == 10 || dz == 10) && (dx + dz) % 5 == 0) set(x, LHB + 2, z, LANTERN);
                }
            }
        for (int z = LHZ - 1; z <= LHZ + 1; z++) set(LHX - 11, LHB, z, stairs("stone_brick", "east"));           // the steps up from the path
        // THE TOWER: walls in spiral bands, the newel, the spiral stair (8-cell ring), windows, the door (west)
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        for (int y = LHB + 1; y <= LHG + 3; y++) {
            double r = lhR(Math.min(y, LHG));
            for (int x = LHX - 5; x <= LHX + 5; x++)
                for (int z = LHZ - 5; z <= LHZ + 5; z++) {
                    double d = Math.hypot(x - LHX, z - LHZ);
                    if (d > r) continue;
                    if (d > r - 1.2) {
                        double turn = (Math.atan2(z - LHZ, x - LHX) / (2 * Math.PI) + 0.5) * 4 + (y - LHB) / 6.0;
                        set(x, y, z, Math.floorMod((int) Math.floor(turn), 2) == 0 ? red : calcite);
                    } else set(x, y, z, AIR);
                }
            if (y <= LHG) set(LHX, y, LHZ, STONE_BRICKS);                                                       // the newel
        }
        for (int i = 0; i + LHB + 1 <= LHG; i++) {
            int y = LHB + 1 + i;
            int[] c = ring[i % 8], n = ring[(i + 1) % 8];
            int fx = n[0] - c[0], fz = n[1] - c[1];                                  // walk up toward the next step
            set(LHX + c[0], y, LHZ + c[1], stairs("stone_brick", fx > 0 ? "east" : fx < 0 ? "west" : fz > 0 ? "south" : "north"));
            for (int h = 1; h <= 3; h++) if (y + h <= LHG) set(LHX + c[0], y + h, LHZ + c[1], AIR);
        }
        set(LHX - 4, LHB + 1, LHZ, AIR); set(LHX - 4, LHB + 2, LHZ, AIR); set(LHX - 5, LHB + 1, LHZ, AIR); set(LHX - 5, LHB + 2, LHZ, AIR);
        set(LHX - 5, LHB + 3, LHZ, stairsTop("stone_brick", "west"));
        set(LHX - 6, LHB + 4, LHZ, id("minecraft:dark_oak_wall_sign[facing=west]"));
        signText(LHX - 6, LHB + 4, LHZ, "", "The", "Wavebreak Light", "");
        set(LHX - 5, LHB + 4, LHZ, calcite);
        for (int y = LHB + 4; y <= LHG - 2; y += 5) {
            double r = lhR(y);
            int k = (y - LHB) % 4;
            int[] w = new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}[k];
            set(LHX + (int) Math.round(w[0] * (r - 0.5)), y, LHZ + (int) Math.round(w[1] * (r - 0.5)), id("minecraft:glass_pane"));
        }
        // THE WATCH ROOM (y107-109): the floor over the stair (a hole where it arrives), the chart table, the logbook, the oil
        int last = LHG - LHB - 1;                                                    // the top step's index: it IS part of the floor
        java.util.Set<Long> open = new java.util.HashSet<>();
        for (int k = last - 2; k <= last; k++) { int[] c = ring[k % 8]; open.add((long) c[0] * 31 + c[1]); }
        for (int x = LHX - 3; x <= LHX + 3; x++) for (int z = LHZ - 3; z <= LHZ + 3; z++)
            if (Math.hypot(x - LHX, z - LHZ) <= lhR(LHG) - 1.2 && !open.contains((long) (x - LHX) * 31 + (z - LHZ))) set(x, LHG, z, SPRUCE);
        set(LHX, LHG, LHZ, STONE_BRICKS);
        // the lantern-room floor (y110) as the watch-room ceiling: iron for the beacon, a ladder hatch
        int lf = LHG + 4;
        for (int x = LHX - 3; x <= LHX + 3; x++) for (int z = LHZ - 3; z <= LHZ + 3; z++) {
            double d = Math.hypot(x - LHX, z - LHZ);
            if (d <= 3.4) set(x, lf, z, Math.abs(x - LHX) <= 1 && Math.abs(z - LHZ) <= 1 ? id("minecraft:iron_block") : copper);
        }
        for (int y = LHG + 1; y <= lf; y++) set(LHX + 2, y, LHZ, id("minecraft:ladder[facing=west]"));
        // THE LANTERN ROOM: the beacon + its lens, glass walls in an iron frame, the copper dome with the yellow oculus
        set(LHX, lf + 1, LHZ, id("minecraft:beacon"));
        for (int[] c : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) set(LHX + c[0], lf + 1, LHZ + c[1], id("minecraft:end_rod[facing=up]"));
        for (int y = lf + 1; y <= lf + 4; y++)
            for (int x = LHX - 3; x <= LHX + 3; x++)
                for (int z = LHZ - 3; z <= LHZ + 3; z++) {
                    int dx = Math.abs(x - LHX), dz = Math.abs(z - LHZ);
                    boolean edge = (dx == 3 || dz == 3) && dx + dz <= 5;
                    if (!edge) continue;
                    boolean post = dx + dz == 5 || (dx == 3 && dz == 0) || (dz == 3 && dx == 0);
                    set(x, y, z, post || y == lf + 4 ? id("minecraft:waxed_cut_copper") : id("minecraft:glass_pane"));
                }
        set(LHX + 3, lf + 1, LHZ, id("minecraft:spruce_door[facing=east,half=lower]")); set(LHX + 3, lf + 2, LHZ, id("minecraft:spruce_door[facing=east,half=upper]"));
        for (int y = lf + 5; y <= lf + 6; y++) {                                                                // the dome
            int rr = y == lf + 5 ? 3 : 2;
            for (int x = LHX - rr; x <= LHX + rr; x++) for (int z = LHZ - rr; z <= LHZ + rr; z++) {
                int dx = Math.abs(x - LHX), dz = Math.abs(z - LHZ);
                if (dx + dz > rr + (rr == 3 ? 2 : 1)) continue;
                set(x, y, z, dx == 0 && dz == 0 ? id("minecraft:yellow_stained_glass") : copper);
            }
        }
        set(LHX + 1, lf + 7, LHZ + 1, id("minecraft:lightning_rod"));                                         // never over the beam
        // THE GALLERY: a railed walk round the lantern room at its floor
        for (int x = LHX - 5; x <= LHX + 5; x++)
            for (int z = LHZ - 5; z <= LHZ + 5; z++) {
                double d = Math.hypot(x - LHX, z - LHZ);
                if (d > 4.9 || getRaw(x, lf, z) != AIR) continue;
                set(x, lf, z, id("minecraft:waxed_cut_copper_slab[type=top]"));
                if (d > 4.0) set(x, lf + 1, z, IRON_BARS);
            }
        // THE KEEPER'S COTTAGE (seaward, x114..119 z-106..-99): calcite + red trim, a slate roof, the door facing the tower
        int kx1 = 114, kx2 = 118, kz1 = -106, kz2 = -99;
        for (int x = kx1; x <= kx2; x++)
            for (int z = kz1; z <= kz2; z++) {
                boolean ex = x == kx1 || x == kx2, ez = z == kz1 || z == kz2;
                for (int y = LHB + 1; y <= LHB + 4; y++) set(x, y, z, !ex && !ez ? AIR : (ex && ez) ? red : y == LHB + 1 ? STONE_BRICKS : calcite);
            }
        gableRoofZ(kz1, kz2, kx1, kx2, LHB + 5, "deepslate_tile", id("minecraft:deepslate_tiles"));
        gableEndsZ(kz1, kx1 + 1, kx2 - 1, LHB + 5, calcite);
        gableEndsZ(kz2, kx1 + 1, kx2 - 1, LHB + 5, calcite);
        innDoor(kx1, LHB, -102, "spruce", false);
        for (int z : new int[]{-104, -100}) for (int y = LHB + 2; y <= LHB + 3; y++) set(kx2, y, z, id("minecraft:glass_pane"));
        // THE FOG BELL, the signal cannon, the flagstaff
        for (int x : new int[]{103, 105}) fill(x, LHB + 1, -110, x, LHB + 3, -110, DARK_OAK_FENCE);
        for (int x = 103; x <= 105; x++) set(x, LHB + 4, -110, DARK_OAK_LOG_X);
        set(104, LHB + 3, -110, id("minecraft:bell[attachment=ceiling,facing=south]"));
        set(113, LHB + 1, -95, id("pixelpirates:display_cannon[facing=east]"));
        fill(101, LHB + 1, -95, 101, LHB + 9, -95, id("minecraft:stripped_spruce_log[axis=y]"));
        String[] flags = {"yellow", "blue", "red", "white"};
        for (int i = 0; i < 4; i++) set(102, LHB + 8 - i, -95, wool(flags[i]));
        furnishLighthouse();
    }

    private static void furnishLighthouse() {
        final int g = LHB + 1, w = LHG + 1;
        // the cottage: the keeper's bed, stove, the table, oil casks, a telescope at the sea window
        ip(117, g, -105, bed("blue", "north", true)); ip(117, g, -104, bed("blue", "north", false));
        ip(115, g, -105, id("minecraft:smoker[facing=east,lit=true]")); ip(116, g, -105, barrel("up"));
        ip(117, g, -101, id("pixelpirates:telescope[facing=east]"));
        ip(116, g, -100, slabTop("spruce")); ip(116, g + 1, -100, LANTERN); ip(115, g, -100, stairs("spruce", "east"));
        ip(117, g, -100, barrel("up"));
        hangLantern(116, LHB + 4, -102);
        // the watch room: chart table, the logbook, oil casks, a lantern
        ip(LHX - 2, w, LHZ + 1, id("pixelpirates:map_table[facing=east]"));
        ip(LHX - 2, w, LHZ - 1, id("minecraft:lectern[facing=east]"));
        ip(LHX + 1, w, LHZ + 2, barrel("up")); ip(LHX - 1, w, LHZ + 2, barrel("up"));
        hangLantern(LHX - 1, LHG + 3, LHZ - 1);
    }

    /*
     * #44 THE GOVERNOR'S FORTRESS (rebuilt 2026-10-04): the seat of the island's master - the Iron Armada's governor (the
     * island belongs to the Armada: the FORT CANNONS (block/custom/FortCannonBlockEntity, island mode) fire on anyone with
     * Armada reputation below 0).
     * A STAR FORT on the west hill: a square of curtain walls (x-114..-82, z-136..-104, 3 thick, the walk at y95) with four
     * arrowhead BASTIONS rising out of the slopes, two guns on each. THE GATEHOUSE on the east between two gun towers, the
     * steps down to the path. Inside: THE GOVERNOR'S HALL - a three-storey keep (the war room with the great map table and
     * the four factions' colours; the governor's study + quarters with a balcony over the parade ground; the signal room)
     * with a roof terrace (two more guns, over the town) and THE GOVERNOR'S TOWER flying the Armada's colours; the barracks,
     * the armoury, the sunken powder magazine, the parade ground (flagpole, shot piles, drill dummies), a well.
     */
    private static final int FX = -98, FZ = -120, FG = 88, FW = 95;                 // centre, courtyard floor, wall-walk floor
    private static final int FX1 = -114, FX2 = -82, FZ1 = -136, FZ2 = -104;          // the curtain's outer square
    /** The fortress guns {x, y, z, facing 0N 1E 2S 3W}, filled by fort() (for tests + the docs; the guns find their own targets). */
    public static final List<int[]> FORT_GUNS = new ArrayList<>();
    private static final String[] FDIR = {"north", "east", "south", "west"};
    private static final int[][] FSTEP = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** The Iron Armada's banner: blue, a white cross, a light-grey border. */
    private static void armadaBanner(int x, int y, int z, String facing) { patternBanner(x, y, z, "blue", facing, 0, "sc", 8, "bo"); }

    private static void fortGun(int x, int y, int z, int dir) {
        set(x, y, z, id("pixelpirates:fort_cannon[facing=" + FDIR[dir] + "]"));
        FORT_GUNS.add(new int[]{x, y, z, dir});
    }

    /** Parapet on a wall/bastion edge cell: a stone course with alternate merlons. */
    private static void parapet(int x, int y, int z) {
        set(x, y, z, wallStone(x, y, z));
        set(x, y + 1, z, ((x + z) & 1) == 0 ? id("minecraft:stone_brick_slab") : AIR);
    }

    private static void fort() {
        FORT_GUNS.clear();
        int dark = POLISHED_ANDESITE, course = CHISELED_STONE_BRICKS;
        // THE GROUND: the courtyard levelled at FG (filled down to the hill), the old fort and the slopes round it cleared
        for (int x = FX1 - 8; x <= FX2 + 8; x++)
            for (int z = FZ1 - 8; z <= FZ2 + 7; z++) {
                if (!natural(x, z) || wallZone(x, z)) continue;
                int s = surf(x, z);
                if (x > FX1 + 2 && x < FX2 - 2 && z > FZ1 + 2 && z < FZ2 - 2)
                    ground(x, z, FG, ((x * 7 + z * 3) & 7) == 0 ? MOSSY_COBBLE : COBBLE, COBBLE, FG - Math.min(s, FG) + 3, Y1);
                else ground(x, z, s, GRASS, DIRT, 2, Y1);
            }
        // THE CURTAIN: 3 thick, from the hillside up to the walk, a string course, arrow loops, merlons, a rail inside
        for (int x = FX1; x <= FX2; x++)
            for (int z = FZ1; z <= FZ2; z++) {
                if (!(x <= FX1 + 2 || x >= FX2 - 2 || z <= FZ1 + 2 || z >= FZ2 - 2)) continue;
                boolean outer = x == FX1 || x == FX2 || z == FZ1 || z == FZ2, inner = !outer && (x == FX1 + 2 || x == FX2 - 2 || z == FZ1 + 2 || z == FZ2 - 2);
                for (int y = Math.min(surf(x, z), FG) - 3; y < FW; y++) set(x, y, z, outer || inner ? (y == FG + 3 ? course : wallStone(x, y, z)) : STONE_BRICKS);
                set(x, FW, z, outer ? STONE_BRICKS : dark);
                if (outer) parapet(x, FW + 1, z);
                if (inner) set(x, FW + 1, z, STONE_BRICK_WALL);
                if (outer && Math.floorMod(x + z, 6) == 0 && surf(x, z) < FG) set(x, FG + 5, z, IRON_BARS);
            }
        // THE BASTIONS: arrowheads round the corners, solid to the gun deck, a parapet round the point
        int[][] corner = {{FX1, FZ1}, {FX2, FZ1}, {FX1, FZ2}, {FX2, FZ2}};
        for (int[] c : corner)
            for (int x = c[0] - 7; x <= c[0] + 7; x++)
                for (int z = c[1] - 7; z <= c[1] + 7; z++) {
                    int d = Math.abs(x - c[0]) + Math.abs(z - c[1]);
                    if (d > 7 || wallZone(x, z)) continue;
                    boolean edge = d == 7, outside = x < FX1 || x > FX2 || z < FZ1 || z > FZ2;
                    for (int y = Math.min(surf(x, z), FG) - 3; y < FW; y++) set(x, y, z, edge ? (y == FG + 3 ? course : wallStone(x, y, z)) : STONE_BRICKS);
                    set(x, FW, z, edge ? STONE_BRICKS : ((x + z) & 1) == 0 ? dark : STONE_BRICKS);
                    for (int y = FW + 1; y <= FW + 3; y++) set(x, y, z, AIR);
                    if (edge && outside) parapet(x, FW + 1, z);
                    else if (edge) set(x, FW + 1, z, STONE_BRICK_WALL);                          // the courtyard edge: a rail
                }
        // the bastion guns, each in an embrasure, a shot pile + a powder keg beside it; a lantern on the salient
        int[][] guns = {{FX1, FZ1 - 5, 0}, {FX1 - 5, FZ1, 3}, {FX2, FZ1 - 5, 0}, {FX2 + 5, FZ1, 1},
                        {FX1 - 5, FZ2, 3}, {FX1, FZ2 + 5, 2}, {FX2 + 5, FZ2, 1}, {FX2, FZ2 + 5, 2}};
        for (int[] g : guns) {
            fortGun(g[0], FW + 1, g[1], g[2]);
            int sx = FSTEP[g[2]][0], sz = FSTEP[g[2]][1];
            for (int k = 1; k <= 2; k++) { set(g[0] + sx * k, FW + 1, g[1] + sz * k, AIR); set(g[0] + sx * k, FW + 2, g[1] + sz * k, AIR); }
            for (int s : new int[]{-1, 1}) {
                int px = g[0] + (sz != 0 ? s : 0), pz = g[1] + (sx != 0 ? s : 0);
                if (getRaw(px, FW + 1, pz) == AIR) set(px, FW + 1, pz, s < 0 ? id("minecraft:coal_block") : GUNPOWDER_BARREL);
            }
        }
        for (int[] c : corner) { set(c[0], FW + 1, c[1], STONE_BRICK_WALL); set(c[0], FW + 2, c[1], LANTERN); }
        gatehouse(dark, course);
        // the stair up onto the walls: inside the south curtain, climbing west from beside the armoury
        for (int i = 0; i < FW - FG; i++) {
            int x = FX2 - 6 - i, z = FZ2 - 3;
            for (int y = FG; y < FG + 1 + i; y++) set(x, y, z, STONE_BRICKS);
            set(x, FG + 1 + i, z, stairs("stone_brick", "west"));
            for (int y = FG + 2 + i; y <= FG + 4 + i; y++) set(x, y, z, AIR);
        }
        set(FX2 - 12, FW + 1, FZ2 - 2, AIR); set(FX2 - 11, FW + 1, FZ2 - 2, AIR);              // the gap in the rail at its head
        governorsHall();
        fortGrounds();
    }

    /** The gate through the east curtain (z FZ-1..FZ+1), the two gun towers either side, the plaque, the steps down. */
    private static void gatehouse(int dark, int course) {
        for (int z = FZ - 1; z <= FZ + 1; z++)
            for (int x = FX2 - 2; x <= FX2; x++) {
                set(x, FG, z, dark);
                for (int y = FG + 1; y <= FG + 4; y++) set(x, y, z, AIR);
            }
        for (int x = FX2 - 2; x <= FX2; x++) {
            set(x, FG + 4, FZ - 1, stairsTop("stone_brick", "south")); set(x, FG + 4, FZ + 1, stairsTop("stone_brick", "north"));
        }
        set(FX2 - 1, FG + 4, FZ, IRON_BARS);                                                         // the portcullis, raised
        set(FX2 - 1, FG + 5, FZ, course);
        // the gun towers: x FX2-2..FX2+2 (the walk runs in through their side doors), a room at the gate, a room at the walk
        for (int s : new int[]{-1, 1}) {
            int tz = FZ + s * 5;
            for (int x = FX2 - 2; x <= FX2 + 2; x++)
                for (int z = tz - 2; z <= tz + 2; z++) {
                    boolean edge = x == FX2 - 2 || x == FX2 + 2 || z == tz - 2 || z == tz + 2;
                    for (int y = Math.min(surf(x, z), FG) - 3; y <= FW + 3; y++)
                        set(x, y, z, edge || y <= FG ? (y == FG + 3 ? course : wallStone(x, y, z)) : y == FW ? dark : AIR);
                    set(x, FW + 4, z, edge ? STONE_BRICKS : dark);
                    if (edge) parapet(x, FW + 5, z);
                    else set(x, FW + 5, z, AIR);
                }
            set(FX2 - 2, FG + 1, tz, AIR); set(FX2 - 2, FG + 2, tz, AIR);                            // the door from the courtyard
            for (int z : new int[]{tz - 2, tz + 2}) { set(FX2 - 1, FW + 1, z, AIR); set(FX2 - 1, FW + 2, z, AIR); }   // doors onto the walk
            for (int y = FG + 1; y <= FW + 4; y++) set(FX2 + 1, y, tz - s, id("minecraft:ladder[facing=west]"));
            fortGun(FX2 + 1, FW + 5, tz + s, 1);
            set(FX2 + 2, FW + 5, tz + s, AIR); set(FX2 + 2, FW + 6, tz + s, AIR);
            for (int y : new int[]{FG + 3, FW - 2, FW + 2}) set(FX2 + 2, y, tz, IRON_BARS);       // arrow loops
            armadaBanner(FX2 + 3, FG + 6, tz, "east");
            hangLantern(FX2 - 1, FW - 1, tz); hangLantern(FX2 - 1, FW + 3, tz);
        }
        set(FX2 + 1, FG + 6, FZ, id("minecraft:dark_oak_wall_sign[facing=east]"));
        signText(FX2 + 1, FG + 6, FZ, "", "The Governor's", "Fortress", "");
        // the steps down to the path, walled, lanterns on the walls at the top
        for (int i = 0; i <= 8; i++) {
            int x = FX2 + 1 + i, sy = FG - i;
            for (int z = FZ - 1; z <= FZ + 1; z++) {
                for (int y = sy - 5; y < sy; y++) set(x, y, z, COBBLE);
                set(x, sy, z, stairs("stone_brick", "west"));
                for (int y = sy + 1; y <= sy + 4; y++) set(x, y, z, AIR);
            }
            for (int z : new int[]{FZ - 2, FZ + 2}) {
                for (int y = sy - 5; y <= sy; y++) set(x, y, z, STONE_BRICKS);
                set(x, sy + 1, z, STONE_BRICK_WALL);
                if (i == 0 || i == 8) set(x, sy + 2, z, LANTERN);
            }
        }
    }

    /** THE GOVERNOR'S HALL: the keep x-108..-88 z-132..-124, floors FG / FG+6 / FG+12, the roof terrace FG+18, the tower. */
    private static final int GKX1 = -108, GKX2 = -88, GKZ1 = -132, GKZ2 = -124;

    private static void governorsHall() {
        int quoin = POLISHED_DIORITE, panel = POLISHED_ANDESITE, band = CHISELED_STONE_BRICKS;
        for (int x = GKX1; x <= GKX2; x++)
            for (int z = GKZ1; z <= GKZ2; z++) {
                boolean ex = x == GKX1 || x == GKX2, ez = z == GKZ1 || z == GKZ2;
                boolean pil = (ex && ez) || (ez && Math.floorMod(x - GKX1, 5) == 0) || (ex && Math.floorMod(z - GKZ1, 4) == 0);
                for (int y = FG - 4; y <= FG + 18; y++) {
                    int b;
                    if (!ex && !ez) b = y == FG ? id("minecraft:polished_deepslate") : y == FG + 18 ? STONE_BRICKS : (y == FG + 6 || y == FG + 12) ? DARK_OAK : y < FG ? COBBLE : AIR;
                    else if (pil) b = quoin;
                    else b = (y == FG + 6 || y == FG + 12 || y == FG + 18) ? band : y <= FG + 1 ? STONE_BRICKS : panel;
                    set(x, y, z, b);
                }
                if (ex || ez) parapet(x, FG + 19, z);
                else { set(x, FG + 19, z, AIR); set(x, FG + 20, z, AIR); }
            }
        // tall windows on every floor (the long faces between the pilasters, the ends), the great door, the portico
        for (int x = GKX1 + 1; x < GKX2; x++) {
            if (Math.floorMod(x - GKX1, 5) == 0) continue;
            for (int f : new int[]{FG, FG + 6, FG + 12})
                for (int y = f + 2; y <= f + 4; y++) {
                    set(x, y, GKZ1, GLASS_PANE);
                    if (f > FG || Math.abs(x + 98) > 2) set(x, y, GKZ2, GLASS_PANE);
                }
        }
        for (int z = GKZ1 + 2; z <= GKZ2 - 2; z += 4)
            for (int f : new int[]{FG, FG + 6, FG + 12})
                for (int y = f + 2; y <= f + 4; y++) for (int x : new int[]{GKX1, GKX2}) set(x, y, z, GLASS_PANE);
        for (int x = -99; x <= -97; x++) {
            for (int y = FG + 1; y <= FG + 3; y++) set(x, y, GKZ2 + 1, AIR);
            set(x, FG, GKZ2 + 1, panel); set(x, FG, GKZ2 + 2, stairs("stone_brick", "north"));
            for (int y = FG + 1; y <= FG + 4; y++) set(x, y, GKZ2, band);
        }
        for (int x = -99; x <= -97; x++) for (int y = FG + 1; y <= FG + 3; y++) set(x, y, GKZ2, AIR);   // the open arch
        set(-99, FG + 3, GKZ2, stairsTop("stone_brick", "east")); set(-97, FG + 3, GKZ2, stairsTop("stone_brick", "west"));
        // the portico: two pillars, the balcony over it (the study's door opens onto it), iron railing, banners
        for (int s : new int[]{-1, 1}) for (int y = FG + 1; y <= FG + 5; y++) set(-98 + s * 2, y, GKZ2 + 2, id("minecraft:quartz_pillar"));
        for (int x = -100; x <= -96; x++) for (int z = GKZ2 + 1; z <= GKZ2 + 2; z++) set(x, FG + 6, z, id("minecraft:polished_diorite_slab[type=top]"));
        for (int x = -100; x <= -96; x++) set(x, FG + 7, GKZ2 + 2, IRON_BARS);
        set(-100, FG + 7, GKZ2 + 1, IRON_BARS); set(-96, FG + 7, GKZ2 + 1, IRON_BARS);
        set(-98, FG + 7, GKZ2, id("minecraft:dark_oak_door[facing=south,half=lower,hinge=left]")); set(-98, FG + 8, GKZ2, id("minecraft:dark_oak_door[facing=south,half=upper,hinge=left]"));
        set(-99, FG + 7, GKZ2, panel); set(-97, FG + 7, GKZ2, panel);
        armadaBanner(-101, FG + 10, GKZ2 + 1, "south"); armadaBanner(-95, FG + 10, GKZ2 + 1, "south");
        armadaBanner(-103, FG + 4, GKZ2 + 1, "south"); armadaBanner(-93, FG + 4, GKZ2 + 1, "south");
        // the stairs: three flights against the north wall, alternating direction (the floors open over them)
        for (int f = 0; f < 3; f++) {
            int y0 = FG + 1 + f * 6, z = GKZ1 + 1 + (f & 1);
            for (int i = 0; i < 6; i++) {
                int x = (f & 1) == 0 ? GKX1 + 2 + i : GKX1 + 7 - i;
                for (int y = y0 + i + 1; y <= y0 + i + 3; y++) if (y < FG + 19) set(x, y, z, AIR);
                set(x, y0 + i, z, stairs("dark_oak", (f & 1) == 0 ? "east" : "west"));
                for (int k = 1; k <= 3; k++) innReserve(x, y0 + i + k, z);
            }
        }
        for (int x = GKX1 + 1; x <= GKX1 + 6; x++) set(x, FG + 19, GKZ1 + 2, DARK_OAK_FENCE);     // a rail round the roof hatch
        set(GKX1 + 1, FG + 19, GKZ1 + 1, DARK_OAK_FENCE);
        // THE GOVERNOR'S TOWER (north-east corner, x-92..-88 z-132..-128) to FG+26, a ladder, the Armada's colours above
        for (int x = -92; x <= -88; x++)
            for (int z = GKZ1; z <= GKZ1 + 4; z++) {
                boolean edge = x == -92 || x == -88 || z == GKZ1 || z == GKZ1 + 4;
                boolean qc = (x == -92 || x == -88) && (z == GKZ1 || z == GKZ1 + 4);
                for (int y = FG + 19; y <= FG + 25; y++) set(x, y, z, edge ? (qc ? quoin : y == FG + 22 ? band : panel) : AIR);
                set(x, FG + 26, z, edge ? quoin : STONE_BRICKS);
                if (edge) parapet(x, FG + 27, z);
                else set(x, FG + 27, z, AIR);
            }
        set(-92, FG + 19, GKZ1 + 2, id("minecraft:dark_oak_door[facing=west,half=lower,hinge=left]")); set(-92, FG + 20, GKZ1 + 2, id("minecraft:dark_oak_door[facing=west,half=upper,hinge=left]"));
        for (int y = FG + 19; y <= FG + 26; y++) set(-89, y, GKZ1 + 1, id("minecraft:ladder[facing=south]"));
        for (int y : new int[]{FG + 20, FG + 23, FG + 24}) { set(-90, y, GKZ1 + 4, GLASS_PANE); set(-90, y, GKZ1, GLASS_PANE); }
        fill(-90, FG + 27, GKZ1 + 2, -90, Y1, GKZ1 + 2, STRIPPED_SPRUCE_Y);
        armadaBanner(-91, Y1 - 1, GKZ1 + 2, "west"); armadaBanner(-89, Y1 - 1, GKZ1 + 2, "east");
        set(-91, FG + 27, GKZ1 + 3, LANTERN);
        // two guns on the roof terrace, over the town
        for (int x : new int[]{-104, -94}) { set(x, FG + 20, GKZ2, AIR); fortGun(x, FG + 19, GKZ2, 2); }
        furnishGovernor();
    }

    private static void furnishGovernor() {
        final int g = FG + 1, s = FG + 7, t = FG + 13, r = FG + 19;
        // THE WAR ROOM: the great map table, the governor's chair at its head, the four factions' colours, charts, a bust
        for (int x = -101; x <= -95; x++) for (int z = -129; z <= -127; z++)
            ip(x, g, z, (x == -98 && z == -128) ? MAP_BLOCK : id("pixelpirates:map_table[facing=south]"));
        ip(-102, g, -128, id("pixelpirates:captains_chair[facing=east]"));
        ip(-94, g, -128, id("pixelpirates:captains_chair[facing=west]"));
        for (int x = -100; x <= -96; x += 2) { ip(x, g, -130, id("pixelpirates:captains_chair[facing=south]")); ip(x, g, -126, id("pixelpirates:captains_chair[facing=north]")); }
        patternBanner(GKX1 + 1, g + 3, -130, "red", "east", 15, "sku");                    // the Brethren
        patternBanner(GKX1 + 1, g + 3, -126, "green", "east", 4, "mc", 4, "bo");          // the Merchant League
        armadaBanner(GKX2 - 1, g + 3, -130, "west");                                         // the Iron Armada
        patternBanner(GKX2 - 1, g + 3, -126, "cyan", "west", 0, "sku", 15, "bo");          // the Drowned
        for (int x : new int[]{-98, -94}) ip(x, g + 2, GKZ1 + 1, id("pixelpirates:sea_chart[facing=south]"));
        ip(GKX2 - 1, g + 1, -128, id("pixelpirates:governor_portrait[facing=west]"));
        ip(-90, g, -130, id("pixelpirates:marble_bust[facing=west]"));
        ip(-106, g, -125, id("pixelpirates:candelabra[facing=north]")); ip(-90, g, -125, id("pixelpirates:candelabra[facing=north]"));
        ip(-91, g, -125, id("pixelpirates:display_cannon[facing=south]"));
        rug(-104, -130, -92, -126, g, "blue", "light_blue");
        hangLantern(-98, FG + 5, -128); hangLantern(-104, FG + 5, -128); hangLantern(-92, FG + 5, -128);
        // THE GOVERNOR'S STUDY + QUARTERS: the desk facing the balcony, bookcases, the strongbox, the bed, a sea chest
        ip(-98, s, -127, id("pixelpirates:captains_desk[facing=south]"));
        ip(-98, s, -128, id("pixelpirates:captains_chair[facing=south]"));
        ip(-97, s, GKZ2 + 1, id("pixelpirates:telescope[facing=south]"));
        for (int x = -103; x <= -101; x++) { ip(x, s, -131, BOOKSHELF); ip(x, s + 1, -131, BOOKSHELF); }
        ip(-105, s, -125, chest("east"));
        patternBanner(GKX1 + 1, s + 2, -127, "light_blue", "east", 11, "glb", 0, "bo");
        ip(-91, s, -130, bed("blue", "south", false)); ip(-91, s, -129, bed("blue", "south", true));
        ip(-90, s, -130, barrel("up")); ip(-90, s, -126, id("pixelpirates:sea_chest[facing=west]"));
        ip(-92, s, -126, id("pixelpirates:candelabra[facing=north]"));
        rug(-101, -128, -95, -125, s, "blue", "white");
        hangLantern(-98, FG + 11, -129); hangLantern(-104, FG + 11, -128); hangLantern(-91, FG + 11, -127);
        // THE SIGNAL ROOM: telescopes at the windows, the signal flags, the bell, the logbook
        ip(-98, t, -131, id("pixelpirates:telescope[facing=north]")); ip(-92, t, -131, id("pixelpirates:telescope[facing=north]"));
        ip(-98, t, -125, id("pixelpirates:telescope[facing=south]")); ip(-92, t, -125, id("pixelpirates:telescope[facing=south]"));
        String[] flags = {"red", "yellow", "blue", "white", "black"};
        for (int i = 0; i < flags.length; i++) ip(-105 + i, t, -125, wool(flags[i]));
        ip(-100, t, -125, id("minecraft:bell[attachment=floor,facing=south]"));
        ip(-95, t, -128, id("minecraft:lectern[facing=south]"));
        hangLantern(-98, FG + 17, -128); hangLantern(-92, FG + 17, -128);
        // the roof terrace: a telescope over the town, lanterns
        ip(-98, r, -126, id("pixelpirates:telescope[facing=south]"));
        ip(-102, r, -126, LANTERN); ip(-94, r, -127, LANTERN);
    }

    /** The courtyard: the barracks, the armoury, the sunken powder magazine, the parade ground, a well. */
    private static void fortGrounds() {
        int roof = id("minecraft:deepslate_tile_slab");
        // THE BARRACKS (west, x-111..-105 z-121..-110): stone, a slate roof, bunks, lockers, racks, the mess table
        fortShell(-111, FG, -121, -105, FG + 5, -110, STONE_BRICKS, SPRUCE);
        for (int x = -111; x <= -105; x++) for (int z = -121; z <= -110; z++) set(x, FG + 6, z, roof);
        for (int z = -119; z <= -112; z++) for (int y = FG + 2; y <= FG + 3; y++) if (z != -116 && z != -115) set(-105, y, z, GLASS_PANE);
        set(-105, FG + 1, -116, id("minecraft:spruce_door[facing=east,half=lower,hinge=left]")); set(-105, FG + 2, -116, id("minecraft:spruce_door[facing=east,half=upper,hinge=left]"));
        set(-105, FG + 1, -115, id("minecraft:spruce_door[facing=east,half=lower,hinge=right]")); set(-105, FG + 2, -115, id("minecraft:spruce_door[facing=east,half=upper,hinge=right]"));
        for (int z = -120; z <= -111; z += 3) {
            ip(-110, FG + 1, z, bed("blue", "west", true)); ip(-109, FG + 1, z, bed("blue", "west", false));
            ip(-110, FG + 3, z, id("pixelpirates:weapon_rack[facing=east]"));
            if (z + 1 <= -111) ip(-110, FG + 1, z + 1, barrel("east"));
        }
        for (int z = -120; z <= -118; z++) { ip(-107, FG + 1, z, slabTop("spruce")); ip(-106, FG + 1, z, stairs("spruce", "east")); }
        ip(-107, FG + 2, -119, LANTERN);
        hangLantern(-108, FG + 5, -115); hangLantern(-108, FG + 5, -119);
        armadaBanner(-104, FG + 4, -116, "east");
        // THE ARMOURY (east, x-91..-86 z-116..-109): racks of arms, the gunsmith's bench, shot + powder, the quartermaster's chest
        fortShell(-91, FG, -116, -86, FG + 5, -109, STONE_BRICKS, STONE_BRICKS);
        for (int x = -91; x <= -86; x++) for (int z = -116; z <= -109; z++) set(x, FG + 6, z, roof);
        set(-91, FG + 1, -113, id("minecraft:spruce_door[facing=west,half=lower,hinge=left]")); set(-91, FG + 2, -113, id("minecraft:spruce_door[facing=west,half=upper,hinge=left]"));
        set(-91, FG + 3, -111, IRON_BARS); set(-91, FG + 3, -115, IRON_BARS);
        for (int z = -114; z <= -110; z += 2) ip(-87, FG + 2, z, id("pixelpirates:weapon_rack[facing=west]"));
        for (int x = -90; x <= -88; x++) ip(x, FG + 2, -115, id("pixelpirates:weapon_rack[facing=south]"));
        ip(-90, FG + 1, -110, SMITHING); ip(-89, FG + 1, -110, GRINDSTONE);
        ip(-87, FG + 1, -110, GUNPOWDER_BARREL); ip(-87, FG + 1, -115, id("minecraft:coal_block"));
        if (getRaw(-89, FG + 1, -113) == AIR) lootChest(-89, FG + 1, -113, "west", "minecraft:chests/pillager_outpost");
        hangLantern(-89, FG + 5, -112);
        // THE POWDER MAGAZINE (x-101..-95 z-111..-107): a squat sunken deepslate vault, steps down through an iron door
        int vault = id("minecraft:deepslate_bricks");
        for (int x = -101; x <= -95; x++)
            for (int z = -111; z <= -107; z++) {
                boolean edge = x == -101 || x == -95 || z == -111 || z == -107;
                for (int y = FG - 3; y <= FG + 3; y++) set(x, y, z, edge || y <= FG - 2 || y == FG + 3 ? vault : AIR);
                set(x, FG + 4, z, id("minecraft:deepslate_brick_slab"));
            }
        set(-98, FG + 1, -111, id("minecraft:iron_door[facing=south,half=lower,hinge=left]")); set(-98, FG + 2, -111, id("minecraft:iron_door[facing=south,half=upper,hinge=left]"));
        set(-97, FG + 2, -112, id("minecraft:stone_button[face=wall,facing=north]"));
        set(-99, FG + 1, -110, id("minecraft:stone_button[face=wall,facing=south]"));
        set(-98, FG - 1, -110, vault); set(-98, FG, -110, stairs("deepslate_brick", "north"));
        set(-98, FG - 1, -109, stairs("deepslate_brick", "north"));
        for (int x = -100; x <= -96; x++) for (int z = -110; z <= -108; z++)
            if (x != -98 && !(x == -99 && z == -110) && getRaw(x, FG - 1, z) == AIR) {
                set(x, FG - 1, z, GUNPOWDER_BARREL);
                if (((x + z) & 1) == 0) set(x, FG, z, GUNPOWDER_BARREL);
            }
        hangLantern(-98, FG + 2, -108);
        armadaBanner(-98, FG + 3, -112, "north");
        // THE PARADE GROUND: a chequered square, the great flagpole with the Armada's ensign, shot piles, drill dummies
        for (int x = -103; x <= -93; x++) for (int z = -121; z <= -114; z++) set(x, FG, z, ((x + z) & 1) == 0 ? STONE_BRICKS : POLISHED_ANDESITE);
        fill(-98, FG + 1, -118, -98, FG + 17, -118, STRIPPED_SPRUCE_Y);
        set(-98, FG + 18, -118, LANTERN);
        for (int y = FG + 13; y <= FG + 16; y++)
            for (int x = -97; x <= -92; x++) set(x, y, -118, wool(x == -95 || y == FG + 14 ? "white" : "blue"));
        for (int[] p : new int[][]{{-103, -114}, {-94, -114}}) {
            set(p[0], FG + 1, p[1], id("minecraft:coal_block")); set(p[0] + 1, FG + 1, p[1], id("minecraft:coal_block")); set(p[0], FG + 2, p[1], id("minecraft:coal_block"));
        }
        for (int x : new int[]{-102, -100, -96, -94}) {
            set(x, FG + 1, -121, OAK_FENCE); set(x, FG + 2, -121, HAY); set(x, FG + 3, -121, id("minecraft:carved_pumpkin[facing=south]"));
        }
        // THE WELL (west of the keep): a stone ring, four posts, a little slab roof, a chain into the water
        for (int x = -111; x <= -109; x++)
            for (int z = -125; z <= -123; z++) {
                boolean rim = x != -110 || z != -124;
                if (rim) { set(x, FG, z, STONE_BRICKS); set(x, FG + 1, z, STONE_BRICK_WALL); }
                else for (int y = FG - 6; y <= FG; y++) set(x, y, z, WATER);
                set(x, FG + 3, z, slab("spruce"));
            }
        for (int[] p : new int[][]{{-111, -125}, {-109, -125}, {-111, -123}, {-109, -123}}) set(p[0], FG + 2, p[1], OAK_FENCE);
        set(-110, FG + 2, -124, CHAIN);
        // lamps round the yard
        for (int[] p : new int[][]{{-104, -122}, {-92, -122}, {-104, -111}, {-92, -110}}) {
            set(p[0], FG + 1, p[1], STONE_BRICK_WALL); set(p[0], FG + 2, p[1], STONE_BRICK_WALL); set(p[0], FG + 3, p[1], LANTERN);
        }
    }

    /** A plain building shell: walls of {@code wall}, a floor of {@code floor}, the inside cleared. */
    private static void fortShell(int x1, int y1, int z1, int x2, int y2, int z2, int wall, int floor) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
                set(x, y1, z, edge ? wall : floor);
                for (int y = y1 + 1; y <= y2; y++) set(x, y, z, edge ? wall : AIR);
            }
    }

    private static void northPaths() {
        // the path starts where the processional way from the gate tops out (the pylons at z-117)
        pathSegment(0, -119, 0, -121);
        // fork east to the lighthouse
        diagonalPath(0, -121, 95, -102);                                // ...to the steps of the lighthouse bastion (x97)
        // fork west to the fort
        diagonalPath(0, -121, -70, -120);                               // ...to the foot of the fortress steps (they climb x-73..-81)
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

    /** Every path cell laid this build (x, z, surface y): smoothPaths() puts a step where the path rises a block. */
    private static final List<int[]> PATH_CELLS = new ArrayList<>();

    /** A cobblestone-slab step on each path cell that has a path neighbour one block higher, so the north paths climb in
     *  half steps - walkable without jumping (2026-10-04). */
    private static void smoothPaths() {
        Map<Long, Integer> h = new HashMap<>();
        for (int[] c : PATH_CELLS) h.put(((long) c[0] << 32) ^ (c[1] & 0xffffffffL), c[2]);
        int slabId = id("minecraft:cobblestone_slab");
        for (int[] c : PATH_CELLS) {
            boolean up = false;
            for (int[] o : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                Integer n = h.get(((long) (c[0] + o[0]) << 32) ^ ((c[1] + o[1]) & 0xffffffffL));
                if (n != null && n == c[2] + 1) up = true;
            }
            if (up && getRaw(c[0], c[2] + 1, c[1]) == AIR) set(c[0], c[2] + 1, c[1], slabId);
        }
    }

    private static void pathBlob(int x, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int px = x + dx, pz = z + dz;
                if (!SpawnIslandTerrain.isLand(px, pz)) continue;
                if (SpawnIslandTerrain.inCityFootprint(px, pz)) continue;
                int gy = (int) Math.round(SpawnIslandTerrain.islandSurfaceY(px, pz));
                set(px, gy, pz, (dx == 0 && dz == 0) ? GRAVEL : DIRT_PATH);
                PATH_CELLS.add(new int[]{px, pz, gy});
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
            if (Math.abs(x + 98) < 26 && Math.abs(z + 120) < 27) continue;
            if (Math.abs(x) < 6) continue;
            boolean downs = false;                                          // the North Downs buildings + pond (2026-10-04)
            for (int[] b : new int[][]{{-67, -159, -29, -120}, {18, -144, 73, -116}, {-33, -160, -1, -141}, {-6, -150, 16, -128}, {57, -157, 80, -139}, {-34, -120, -18, -108}})
                downs |= x >= b[0] && x <= b[2] && z >= b[1] && z <= b[3];
            if (downs) continue;
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
                if (!natural(x, z) || wallZone(x, z)) continue;
                int s = surf(x, z);
                for (int yy = Math.min(s, y) - 3; yy < y; yy++) set(x, yy, z, DIRT);
                set(x, y, z, topId);
                for (int yy = y + 1; yy <= Math.max(s, y) + 3; yy++) set(x, yy, z, AIR);
            }
    }

    /*
     * THE NORTH DOWNS (rebuilt 2026-10-04): the open downland north of the wall (a plateau ~y76-81 falling to the north
     * coast), reached through the Great North Gate. From the signpost at the fork (0,-121) the paths run west to the fort
     * and THE DOWNS FARMSTEAD (#46: farmhouse, barn + hayloft, farmyard, well, coop, pigsty, paddock, potato + cabbage
     * plots), east to the lighthouse past THE ORCHARD (#47: blossoming fruit trees in rows, beehives, the cider house),
     * north up the ridge to THE RIDGE LOOKOUT (#49: a stone watchtower with working TELESCOPES on its gallery, a signal
     * fire, the flag) and on down the north slope to THE SHEPHERD'S HUT (#48: a hut on wheels, the dry-stone fold). Plus
     * the wheat + carrot fields and the windmill by the wall (kept), a pond with reeds and a jetty, the signpost.
     */
    private static void countryside() {
        farmField(-60, -108, -31, -99, "wheat");                        // kept clear of the wall + its towers (#42, 2026-10-04)
        farmField(24, -106, 53, -99, "carrots");
        windmill(-18, -101);
        // the old potato field (x-27..-10 z-126..-117) and the old lookout go back to downland (cells nothing else claims)
        for (int x = -30; x <= 16; x++)
            for (int z = -146; z <= -114; z++) {
                boolean field = x >= -29 && x <= -8 && z >= -128 && z <= -115, look = x >= -2 && x <= 13 && z >= -146 && z <= -130;
                if (!(field || look) || !natural(x, z) || getRaw(x, surf(x, z), z) != 0) continue;
                ground(x, z, surf(x, z), GRASS, DIRT, 2, surf(x, z) + 26);                // (the old flagpole stood ~20 up)
            }
        pathSegment(0, -121, 4, -129);                                     // up the ridge to the lookout...
        diagonalPath(2, -129, -6, -142);                                   // ...and on down the north slope to the shepherd
        diagonalPath(-4, -122, -31, -130);                                 // west to the farmstead gate
    }

    /** The North Downs buildings (#46-#49, after the Beach Wreck so the map numbers stay put), the pond, the signpost. */
    private static void northDowns() {
        named("Farmstead", PortCityLayout::farmstead);
        named("Orchard", PortCityLayout::orchard);
        named("Shepherd's Hut", PortCityLayout::shepherdsHut);
        named("Ridge Lookout", () -> lookout(5, -139));
        named("Chess Green", PortCityLayout::chessGreen);
        downsPond();
        signpost(3, -119);
        smoothPaths();
    }

    /** A pad levelled to the area's average height (grass on top); returns that height. */
    private static int downsPad(int x1, int z1, int x2, int z2) {
        int y = averageSurf(x1, z1, x2, z2);
        levelPad(x1, z1, x2, z2, y, GRASS);
        return y;
    }

    /** A timber + stone building shell: stone footing to y+1, walls of {@code wall} between log posts, floor, door gap. */
    private static void downsShell(int x1, int z1, int x2, int z2, int y, int h, int wall, int post, int floor) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean edge = x == x1 || x == x2 || z == z1 || z == z2, corner = (x == x1 || x == x2) && (z == z1 || z == z2);
                for (int yy = y - 3; yy < y; yy++) set(x, yy, z, COBBLE);
                set(x, y, z, edge ? COBBLE : floor);
                for (int yy = y + 1; yy <= y + h; yy++)
                    set(x, yy, z, !edge ? AIR : corner ? post : yy == y + 1 ? id("minecraft:mossy_cobblestone") : wall);
                if (!edge) for (int yy = y + h + 1; yy <= y + h + 8; yy++) set(x, yy, z, AIR);   // the roof space
            }
    }

    // ------------------------------------------------------------------ #46 THE DOWNS FARMSTEAD (x-66..-30, z-150..-112)
    private static void farmstead() {
        int y = downsPad(-64, -149, -32, -123);
        int white = id("minecraft:white_terracotta"), beam = STRIPPED_SPRUCE_Y, cob = COBBLE;
        // the farmyard: cobbles + gravel, a fence round it all, the gate on the east (the path comes in at z-131)
        for (int x = -62; x <= -34; x++)
            for (int z = -135; z <= -125; z++) set(x, y, z, ((x * 3 + z * 7) & 7) == 0 ? GRAVEL : ((x + z) & 3) == 0 ? MOSSY_COBBLE : cob);
        for (int x = -64; x <= -32; x++) { set(x, y + 1, -123, OAK_FENCE); set(x, y + 1, -149, OAK_FENCE); }
        for (int z = -149; z <= -123; z++) { set(-64, y + 1, z, OAK_FENCE); if (z < -133 || z > -129) set(-32, y + 1, z, OAK_FENCE); }
        set(-32, y + 1, -131, id("minecraft:oak_fence_gate[facing=east,open=true]")); set(-32, y + 1, -130, id("minecraft:oak_fence_gate[facing=east,open=true]"));
        for (int z : new int[]{-133, -128}) { set(-32, y + 1, z, OAK_LOG_Y); set(-32, y + 2, z, OAK_LOG_Y); set(-32, y + 3, z, LANTERN); }
        hangingSign(-32, y + 3, -131, "spruce", "", "DOWNS FARM", "", "");
        set(-32, y + 4, -131, id("minecraft:spruce_slab[type=bottom]"));
        for (int z = -132; z <= -129; z++) set(-32, y + 4, z, id("minecraft:spruce_slab[type=bottom]"));
        // THE FARMHOUSE (x-62..-52, z-147..-139): stone below, white plaster + spruce frame above, a gabled roof, chimney
        downsShell(-62, -147, -52, -139, y, 4, cob, OAK_LOG_Y, SPRUCE);
        for (int x = -62; x <= -52; x++)
            for (int z = -147; z <= -139; z++) {
                boolean edge = x == -62 || x == -52 || z == -147 || z == -139;
                set(x, y + 5, z, edge ? id("minecraft:stripped_spruce_log[axis=x]") : SPRUCE);
                if (!edge) { for (int yy = y + 6; yy <= y + 13; yy++) set(x, yy, z, AIR); continue; }
                for (int yy = y + 6; yy <= y + 8; yy++) set(x, yy, z, (x - -62) % 5 == 0 || (z == -147 || z == -139) && (x == -62 || x == -52) ? beam : white);
            }
        for (int x : new int[]{-59, -55}) for (int yy : new int[]{y + 2, y + 3, y + 7}) { set(x, yy, -139, GLASS_PANE); set(x, yy, -147, GLASS_PANE); }
        for (int yy : new int[]{y + 2, y + 7}) { set(-62, yy, -143, GLASS_PANE); set(-52, yy, -143, GLASS_PANE); }
        set(-57, y + 1, -139, id("minecraft:spruce_door[facing=south,half=lower,hinge=left]")); set(-57, y + 2, -139, id("minecraft:spruce_door[facing=south,half=upper,hinge=left]"));
        set(-57, y + 3, -138, id("minecraft:spruce_trapdoor[facing=south,half=top,open=false]"));
        gableRoofX(-62, -52, -147, -139, y + 9, "spruce", SPRUCE);
        gableEndsX(-62, -147, -139, y + 9, white); gableEndsX(-52, -147, -139, y + 9, white);
        for (int yy = y - 2; yy <= y + 15; yy++) set(-60, yy, -145, id("minecraft:bricks"));
        set(-60, y + 16, -145, CAMPFIRE);
        // inside: the kitchen (range, table, dresser) + the parlour, a ladder to the bedroom under the roof
        set(-61, y + 1, -145, id("minecraft:smoker[facing=east,lit=true]")); set(-61, y + 1, -144, id("minecraft:furnace[facing=east]"));
        set(-61, y + 1, -146, barrel("east")); set(-60, y + 1, -146, barrel("up")); set(-60, y + 2, -146, id("minecraft:flower_pot"));
        for (int x = -59; x <= -57; x++) set(x, y + 1, -143, slabTop("spruce"));
        set(-58, y + 2, -143, id("minecraft:candle[candles=3,lit=true]"));
        set(-59, y + 1, -142, stairs("spruce", "north")); set(-57, y + 1, -142, stairs("spruce", "north")); set(-58, y + 1, -144, stairs("spruce", "south"));
        set(-54, y + 1, -146, BOOKSHELF); set(-53, y + 1, -146, chest("south"));
        set(-53, y + 1, -141, id("pixelpirates:captains_chair[facing=west]"));
        rug(-56, -145, -54, -141, y + 1, "brown", "yellow");
        for (int yy = y + 1; yy <= y + 5; yy++) set(-53, yy, -145, id("minecraft:ladder[facing=west]"));
        set(-53, y + 5, -145, id("minecraft:ladder[facing=west]"));
        set(-61, y + 6, -146, bed("red", "east", false)); set(-60, y + 6, -146, bed("red", "east", true));
        set(-56, y + 6, -146, chest("south")); set(-61, y + 6, -140, barrel("up"));
        hangLantern(-57, y + 4, -143); hangLantern(-57, y + 8, -143);
        // THE BARN (x-48..-36, z-149..-137): dark wood on a stone plinth, big doors to the yard, a hayloft, a tall roof
        downsShell(-48, -149, -36, -137, y, 7, id("minecraft:dark_oak_planks"), DARK_OAK_LOG_Y, id("minecraft:packed_mud"));
        for (int x = -46; x <= -38; x++) for (int yy = y + 1; yy <= y + 5; yy++) if (x >= -43 && x <= -41) set(x, yy, -137, AIR);
        for (int x : new int[]{-44, -40}) for (int yy = y + 1; yy <= y + 5; yy++) set(x, yy, -136, id("minecraft:dark_oak_trapdoor[facing=south,half=bottom,open=true]"));
        for (int x = -47; x <= -37; x++) for (int z = -148; z <= -141; z++) set(x, y + 5, z, SPRUCE);         // the hayloft
        for (int x = -47; x <= -37; x++) for (int z = -148; z <= -142; z++) if (((x + z) & 1) == 0 || z < -145) set(x, y + 6, z, HAY);
        for (int yy = y + 1; yy <= y + 5; yy++) set(-37, yy, -140, id("minecraft:ladder[facing=west]"));
        gableRoofZ(-149, -137, -48, -36, y + 8, "dark_oak", DARK_OAK);
        gableEndsZ(-149, -48, -36, y + 8, id("minecraft:dark_oak_planks")); gableEndsZ(-137, -48, -36, y + 8, id("minecraft:dark_oak_planks"));
        set(-42, y + 11, -137, id("minecraft:spruce_trapdoor[facing=south,half=bottom,open=true]"));             // the loft door
        set(-42, y + 13, -136, id("minecraft:spruce_fence")); set(-42, y + 13, -137, id("minecraft:spruce_fence"));
        set(-42, y + 12, -136, CHAIN);
        // stalls inside, a cart, sacks, tools
        for (int z = -147; z <= -142; z += 5) for (int x = -47; x <= -45; x++) set(x, y + 1, z, OAK_FENCE);
        for (int x = -47; x <= -45; x++) { set(x, y + 1, -145, HAY); set(x, y + 1, -146, HAY); }
        set(-39, y + 1, -146, id("minecraft:composter")); set(-38, y + 1, -146, barrel("up")); set(-38, y + 1, -147, barrel("up"));
        set(-40, y + 1, -143, id("pixelpirates:cargo_crate[facing=south]")); set(-40, y + 2, -143, id("pixelpirates:cargo_crate[facing=east]"));
        set(-38, y + 1, -142, id("minecraft:grindstone[face=floor,facing=south]"));
        hangLantern(-42, y + 4, -143);
        // THE WELL in the yard, the CHICKEN COOP, the PIGSTY, hay stacks, a cart
        for (int x = -51; x <= -49; x++)
            for (int z = -132; z <= -130; z++) {
                boolean rim = x != -50 || z != -131;
                if (rim) { set(x, y, z, STONE_BRICKS); set(x, y + 1, z, id("minecraft:cobblestone_wall")); }
                else for (int yy = y - 6; yy <= y; yy++) set(x, yy, z, WATER);
            }
        for (int[] p : new int[][]{{-51, -132}, {-49, -130}}) { set(p[0], y + 2, p[1], OAK_FENCE); set(p[0], y + 3, p[1], OAK_FENCE); }
        for (int x = -51; x <= -49; x++) for (int z = -132; z <= -130; z++) set(x, y + 4, z, id("minecraft:spruce_slab[type=bottom]"));
        set(-50, y + 3, -131, CHAIN); set(-50, y + 2, -131, CHAIN);
        // the coop: a little raised hut + a run
        for (int x = -62; x <= -59; x++) for (int z = -135; z <= -132; z++) {
            boolean e = x == -62 || x == -59 || z == -135 || z == -132;
            set(x, y + 1, z, OAK_FENCE);
            set(x, y + 2, z, e ? id("minecraft:spruce_planks") : HAY);
            set(x, y + 3, z, e ? id("minecraft:spruce_planks") : AIR);
            set(x, y + 4, z, id("minecraft:spruce_slab[type=bottom]"));
        }
        set(-59, y + 2, -133, AIR); set(-59, y + 3, -133, AIR);
        set(-58, y + 1, -133, stairs("spruce", "west"));
        for (int x = -58; x <= -54; x++) { set(x, y + 1, -136, OAK_FENCE); set(x, y + 1, -128, OAK_FENCE); }
        for (int z = -136; z <= -128; z++) set(-54, y + 1, z, OAK_FENCE);
        set(-56, y + 1, -130, id("minecraft:composter")); set(-57, y + 1, -134, HAY);
        // the pigsty (east of the yard): a mud wallow in a stone-walled pen, a trough, a lean-to
        for (int x = -40; x <= -34; x++) for (int z = -135; z <= -127; z++) {
            boolean e = x == -40 || x == -34 || z == -135 || z == -127;
            if (e) { set(x, y + 1, z, id("minecraft:cobblestone_wall")); continue; }
            set(x, y, z, ((x + z) & 1) == 0 ? id("minecraft:mud") : id("minecraft:coarse_dirt"));
        }
        set(-40, y + 1, -131, id("minecraft:oak_fence_gate[facing=west]"));
        set(-36, y + 1, -134, CAULDRON_WATER); set(-35, y + 1, -134, id("minecraft:cauldron"));
        for (int z = -134; z <= -132; z++) { set(-35, y + 3, z, id("minecraft:spruce_slab[type=bottom]")); }
        set(-35, y + 1, -132, OAK_FENCE); set(-35, y + 2, -132, OAK_FENCE);
        set(-45, y + 1, -127, HAY); set(-44, y + 1, -127, HAY); set(-45, y + 2, -127, HAY);
        set(-47, y + 1, -126, barrel("up")); set(-48, y + 1, -126, id("pixelpirates:cargo_crate[facing=south]"));
        // the kitchen garden (beetroot + potatoes) behind the house
        int farmland = id("minecraft:farmland[moisture=7]");
        levelPad(-63, -157, -51, -150, y, GRASS);
        for (int x = -62; x <= -52; x++)
            for (int z = -155; z <= -150; z++) {
                set(x, y, z, x == -57 ? WATER : farmland);
                if (x != -57) set(x, y + 1, z, id(x < -57 ? "minecraft:potatoes[age=7]" : "minecraft:beetroots[age=3]"));
            }
        for (int x = -63; x <= -51; x++) { set(x, y + 1, -156, OAK_FENCE); }
        // a scarecrow over the garden
        set(-58, y + 2, -153, OAK_FENCE); set(-58, y + 3, -153, OAK_FENCE); set(-59, y + 3, -153, OAK_FENCE); set(-57, y + 3, -153, OAK_FENCE);
        set(-58, y + 4, -153, id("minecraft:carved_pumpkin[facing=south]"));
        set(-58, y + 1, -153, OAK_FENCE);
    }

    // ------------------------------------------------------------------ #47 THE ORCHARD (x20..70, z-142..-119)
    private static void orchard() {
        int y = downsPad(21, -141, 58, -119);
        Random r = new Random(4747);
        // rows of fruit trees in blossom, a grass ride down the middle, beehives on some trunks
        for (int x = 24; x <= 56; x += 5)
            for (int z = -138; z <= -122; z += 5) {
                if (x == 39) continue;                                                  // the ride
                int h = 3 + r.nextInt(2);
                for (int yy = y + 1; yy <= y + h; yy++) set(x, yy, z, OAK_LOG_Y);
                for (int dx = -2; dx <= 2; dx++)
                    for (int dz = -2; dz <= 2; dz++)
                        for (int dy = 0; dy <= 2; dy++) {
                            int d = Math.abs(dx) + Math.abs(dz) + dy;
                            if (d > 3 || (dx == 0 && dz == 0 && dy < 1)) continue;
                            if (dy == 0 && Math.abs(dx) + Math.abs(dz) < 2) continue;
                            set(x + dx, y + h + dy, z + dz, id(r.nextInt(3) == 0 ? "minecraft:azalea_leaves[persistent=true]" : "minecraft:flowering_azalea_leaves[persistent=true]"));
                        }
                set(x, y + h + 3, z, id("minecraft:flowering_azalea_leaves[persistent=true]"));
                if (r.nextInt(4) == 0) set(x, y + 2, z + 1, id("minecraft:bee_nest[facing=south,honey_level=3]"));
                if (r.nextInt(3) == 0) set(x + 1, y + 1, z + 1, id("minecraft:pink_petals[facing=north,flower_amount=4]"));
            }
        for (int z = -141; z <= -119; z++) set(39, y, z, DIRT_PATH);
        for (int x = 21; x <= 58; x++) set(x, y, -130, x == 39 ? DIRT_PATH : getRaw(x, y + 1, -130) == AIR || getRaw(x, y + 1, -130) == 0 ? DIRT_PATH : GRASS);
        for (int x = 21; x <= 58; x++) { set(x, y + 1, -141, OAK_FENCE); set(x, y + 1, -119, x >= 23 && x <= 25 ? AIR : OAK_FENCE); }
        for (int z = -141; z <= -119; z++) { set(21, y + 1, z, OAK_FENCE); set(58, y + 1, z, z >= -131 && z <= -129 ? AIR : OAK_FENCE); }
        for (int x : new int[]{22, 26}) { set(x, y + 1, -119, OAK_LOG_Y); set(x, y + 2, -119, LANTERN); }
        // the picking: crates of fruit, ladders against the trees, baskets
        set(38, y + 1, -128, id("pixelpirates:cargo_crate[facing=south]")); set(40, y + 1, -128, id("pixelpirates:cargo_crate[facing=east]"));
        set(38, y + 2, -128, id("pixelpirates:cargo_crate[facing=east]"));
        set(30, y + 1, -129, barrel("up")); set(45, y + 1, -131, barrel("up"));
        // THE CIDER HOUSE (x60..68, z-134..-126) outside the east gate
        int cy = downsPad(59, -136, 70, -124);
        downsShell(60, -134, 68, -126, cy, 4, id("minecraft:stripped_oak_log[axis=y]"), OAK_LOG_Y, id("minecraft:spruce_planks"));
        set(60, cy + 2, -132, GLASS_PANE); set(60, cy + 2, -128, GLASS_PANE);
        set(60, cy + 1, -130, id("minecraft:spruce_door[facing=west,half=lower,hinge=left]")); set(60, cy + 2, -130, id("minecraft:spruce_door[facing=west,half=upper,hinge=left]"));
        gableRoofX(60, 68, -134, -126, cy + 5, "spruce", SPRUCE);
        gableEndsX(60, -134, -126, cy + 5, id("minecraft:stripped_oak_log[axis=y]")); gableEndsX(68, -134, -126, cy + 5, id("minecraft:stripped_oak_log[axis=y]"));
        for (int z = -133; z <= -127; z++) { set(67, cy + 1, z, barrel("west")); if ((z & 1) == 0) set(67, cy + 2, z, barrel("west")); }
        set(63, cy + 1, -133, id("minecraft:piston[facing=down]")); set(63, cy + 2, -133, id("minecraft:oak_log[axis=y]"));         // the press
        set(63, cy + 1, -132, CAULDRON_WATER);
        for (int x = 62; x <= 65; x++) set(x, cy + 1, -127, slabTop("spruce"));
        set(64, cy + 2, -127, id("minecraft:candle[candles=2,lit=true]"));
        set(62, cy + 1, -129, id("pixelpirates:cargo_crate[facing=west]")); set(62, cy + 1, -131, id("pixelpirates:cargo_crate[facing=west]"));
        hangLantern(64, cy + 4, -130);
        hangingSign(59, cy + 3, -130, "oak", "", "CIDER", "HOUSE", "");
        set(59, cy + 4, -130, OAK_FENCE);
    }

    // ------------------------------------------------------------------ #48 THE SHEPHERD'S HUT + FOLD (x-30..-4, z-159..-143)
    private static void shepherdsHut() {
        int y = downsPad(-30, -158, -4, -144);
        // the fold: a dry-stone wall (mossy + plain cobble walls), a gate, hay racks, a water trough
        for (int x = -28; x <= -16; x++)
            for (int z = -157; z <= -147; z++) {
                boolean e = x == -28 || x == -16 || z == -157 || z == -147;
                if (!e) continue;
                set(x, y + 1, z, ((x * 5 + z) & 3) == 0 ? id("minecraft:mossy_cobblestone_wall") : id("minecraft:cobblestone_wall"));
            }
        set(-16, y + 1, -152, id("minecraft:oak_fence_gate[facing=east]"));
        for (int z = -155; z <= -153; z++) { set(-27, y + 1, z, HAY); }
        set(-27, y + 2, -154, HAY);
        for (int x = -24; x <= -21; x++) set(x, y + 1, -156, CAULDRON_WATER);
        // the hut on wheels (x-12..-8, z-154..-150): a curved roof, steps, a stove pipe
        int hy = y + 1;
        for (int x = -12; x <= -8; x++)
            for (int z = -154; z <= -150; z++) {
                set(x, hy, z, id("minecraft:spruce_planks"));                                         // the chassis
                boolean e = x == -12 || x == -8 || z == -154 || z == -150;
                for (int yy = hy + 1; yy <= hy + 3; yy++) set(x, yy, z, !e ? AIR : (z == -154 || z == -150) ? id("minecraft:green_terracotta") : id("minecraft:spruce_planks"));
                set(x, hy + 4, z, z == -152 ? id("minecraft:dark_oak_planks") : id("minecraft:dark_oak_slab[type=" + (z == -154 || z == -150 ? "bottom" : "top") + "]"));
            }
        for (int x = -12; x <= -8; x++) { set(x, hy + 4, -155, id("minecraft:dark_oak_stairs[facing=south,half=bottom]")); set(x, hy + 4, -149, id("minecraft:dark_oak_stairs[facing=north,half=bottom]")); }
        for (int[] w : new int[][]{{-12, -155}, {-8, -155}, {-12, -149}, {-8, -149}})
            set(w[0], hy, w[1], id("minecraft:dark_oak_trapdoor[facing=" + (w[1] < -152 ? "north" : "south") + ",half=bottom,open=true]"));   // the wheels
        set(-8, hy + 1, -152, id("minecraft:spruce_door[facing=east,half=lower]")); set(-8, hy + 2, -152, id("minecraft:spruce_door[facing=east,half=upper]"));
        set(-7, hy, -152, stairs("spruce", "west"));
        set(-10, hy + 2, -154, GLASS_PANE); set(-10, hy + 2, -150, GLASS_PANE);
        set(-11, hy + 1, -153, id("minecraft:smoker[facing=south,lit=true]"));
        for (int yy = hy + 2; yy <= hy + 6; yy++) set(-11, yy, -153, yy <= hy + 3 ? id("minecraft:chain") : id("minecraft:cobblestone_wall"));
        set(-11, hy + 7, -153, id("minecraft:campfire[lit=true]"));                                 // smoke from the stove
        set(-11, hy + 1, -151, bed("green", "west", true)); set(-10, hy + 1, -151, bed("green", "west", false));
        set(-9, hy + 1, -153, barrel("up"));
        // the dog's kennel, a crook against the hut, a bench to watch the flock from
        for (int[] k : new int[][]{{-6, -146}, {-5, -146}}) { set(k[0], y + 1, k[1], id("minecraft:spruce_planks")); set(k[0], y + 2, k[1], id("minecraft:spruce_stairs[facing=south]")); }
        set(-6, y + 1, -146, AIR);
        set(-13, hy + 1, -151, OAK_FENCE); set(-13, hy + 2, -151, id("minecraft:tripwire_hook[facing=west]"));
        set(-14, y + 1, -147, stairs("spruce", "north")); set(-13, y + 1, -147, stairs("spruce", "north"));
    }

    /** THE RIDGE LOOKOUT (#49): a round stone watchtower on the ridge - a stair inside, a gallery with four working
     *  TELESCOPES (north, east, south, west), a signal fire on the top, the flag; a ring of benches at its foot. */
    private static void lookout(int cx, int cz) {
        int y = averageSurf(cx - 4, cz - 4, cx + 4, cz + 4);
        levelPad(cx - 8, cz - 8, cx + 8, cz + 8, y, GRASS);
        for (int x = cx - 8; x <= cx + 8; x++)
            for (int z = cz - 8; z <= cz + 8; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d > 8.4) continue;
                if (d > 6.4) { set(x, y, z, ((x + z) & 3) == 0 ? MOSSY_COBBLE : COBBLE); continue; }       // the ring path
                for (int yy = y + 1; yy <= y + 16; yy++) set(x, yy, z, AIR);
                if (d > 4.6) continue;
                boolean wall = d > 3.6;
                for (int yy = y - 2; yy <= y + 11; yy++) set(x, yy, z, wall ? (yy % 4 == 0 ? CHISELED_STONE_BRICKS : wallStone(x, yy, z)) : yy == y ? STONE_BRICKS : AIR);
                set(x, y + 12, z, STONE_BRICKS);                                        // the gallery floor
                if (wall) { set(x, y + 13, z, STONE_BRICK_WALL); }
            }
        // the door (south), arrow-slit windows, a ladder up the inside to a hatch in the gallery
        set(cx, y + 1, cz + 4, AIR); set(cx, y + 2, cz + 4, AIR); set(cx, y + 1, cz + 5, stairs("stone_brick", "north")); set(cx, y, cz + 5, STONE_BRICKS);
        for (int[] w : new int[][]{{4, 0}, {-4, 0}, {0, -4}}) for (int yy : new int[]{y + 4, y + 8}) set(cx + w[0], yy, cz + w[1], IRON_BARS);
        for (int yy = y + 1; yy <= y + 12; yy++) set(cx, yy, cz - 3, id("minecraft:ladder[facing=south]"));
        set(cx, y + 12, cz - 3, id("minecraft:ladder[facing=south]"));
        hangLantern(cx, y + 11, cz);
        set(cx - 2, y + 1, cz - 2, id("minecraft:cartography_table")); set(cx + 2, y + 1, cz - 2, barrel("up"));
        // the gallery: four telescopes looking out, a signal brazier in the middle, the flag
        int g = y + 13;
        set(cx, g, cz - 3, AIR);
        int[][] scopes = {{0, -3, 0}, {3, 0, 1}, {0, 3, 2}, {-3, 0, 3}};
        String[] f = {"north", "east", "south", "west"};
        for (int[] s : scopes) set(cx + s[0] == cx && s[1] == -3 ? cx + 1 : cx + s[0], g, cz + s[1], id("pixelpirates:telescope[facing=" + f[s[2]] + "]"));
        set(cx, g, cz, id("minecraft:campfire[lit=true]"));
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}}) set(cx + d[0], g, cz + d[1], id("minecraft:stone_brick_slab[type=bottom]"));
        int fx = cx + 3, fz = cz - 2;
        for (int yy = g; yy <= g + 8; yy++) set(fx, yy, fz, SPRUCE_FENCE);
        for (int yy = g + 6; yy <= g + 8; yy++) for (int k = 1; k <= 3; k++) set(fx, yy, fz + k, BLACK_WOOL);
        set(fx, g + 7, fz + 2, WHITE_WOOL);
        // benches round the foot, a cairn, the bell for when a sail is sighted
        for (int[] b : new int[][]{{-6, 0, 1}, {6, 0, 3}, {0, -6, 2}}) {
            String face = new String[]{"north", "east", "south", "west"}[b[2]];
            set(cx + b[0], y + 1, cz + b[1], stairs("spruce", face));
            set(cx + b[0] + (b[1] != 0 ? 1 : 0), y + 1, cz + b[1] + (b[0] != 0 ? 1 : 0), stairs("spruce", face));
        }
        set(cx - 5, y + 1, cz + 5, COBBLE); set(cx - 5, y + 2, cz + 5, id("minecraft:cobblestone_wall")); set(cx - 6, y + 1, cz + 5, id("minecraft:cobblestone_slab"));
        set(cx + 5, y + 1, cz + 5, OAK_LOG_Y); set(cx + 5, y + 2, cz + 5, OAK_LOG_Y); set(cx + 5, y + 3, cz + 5, id("minecraft:bell[attachment=floor,facing=south]"));
    }

    /**
     * #50 THE CHESS GREEN (2026-10-04): GIANT CHESS on the green between the fort road and the wheat field, just out of the
     * North Gate - an 8 x 8 board of quartz + blackstone squares, the pedestal with its chess clock on the west (white plays
     * from there, facing east), benches on the far side for black and the onlookers, lamps, a sign. Use the pedestal to play
     * (homestead/chess); the pieces are drawn on the squares by ChessRenderer.
     */
    private static void chessGreen() {
        int y = averageSurf(-31, -118, -20, -111);
        levelPad(-31, -118, -20, -111, y, GRASS);
        for (int x = -29; x <= -22; x++)
            for (int z = -118; z <= -111; z++) {
                int f = z + 118, r = x + 29;                                        // file a..h north -> south, rank 1..8 west -> east
                set(x, y, z, (f + r) % 2 == 0 ? id("minecraft:polished_blackstone") : id("minecraft:smooth_quartz"));
                for (int yy = y + 1; yy <= y + 4; yy++) set(x, yy, z, AIR);
            }
        for (int z = -118; z <= -111; z++) { set(-30, y, z, POLISHED_ANDESITE); set(-21, y, z, POLISHED_ANDESITE); }
        set(-30, y + 1, -118, id("pixelpirates:giant_chess[facing=east]"));
        for (int z = -116; z <= -113; z++) set(-20, y + 1, z, stairs("spruce", "east"));             // black's bench (sit facing west)
        set(-31, y + 1, -116, stairs("spruce", "west")); set(-31, y + 1, -115, stairs("spruce", "west"));
        lamppost(-31, y, -111); lamppost(-20, y, -118);
        set(-31, y + 1, -113, id("minecraft:oak_sign[rotation=4]"));
        signText(-31, y + 1, -113, "", "THE CHESS", "GREEN", "use the clock");
    }

    /** A pond on the north-east downs: reeds, lily pads, a little jetty, a bench. */
    private static void downsPond() {
        int y = averageSurf(60, -152, 74, -142);
        levelPad(60, -154, 77, -142, y, GRASS);
        for (int x = 60; x <= 74; x++)
            for (int z = -152; z <= -142; z++) {
                double d = Math.pow((x - 67) / 7.0, 2) + Math.pow((z + 147) / 5.0, 2);
                if (d > 1) continue;
                int depth = d < 0.45 ? 2 : 1;
                for (int yy = y - depth + 1; yy <= y; yy++) set(x, yy, z, WATER);
                set(x, y - depth, z, d < 0.45 ? id("minecraft:clay") : SAND);
                if (d > 0.7 && ((x * 3 + z) % 4 == 0)) { set(x, y + 1, z, id("minecraft:lily_pad")); }
            }
        for (int[] c : new int[][]{{60, -147}, {61, -150}, {73, -145}, {72, -143}, {74, -148}})
            if (getRaw(c[0], y, c[1]) != WATER) { set(c[0], y, c[1], SAND); set(c[0], y + 1, c[1], id("minecraft:sugar_cane")); set(c[0], y + 2, c[1], id("minecraft:sugar_cane")); }
        for (int x = 64; x <= 66; x++) { set(x, y + 1, -142, id("minecraft:spruce_slab[type=bottom]")); set(x, y, -142, id("minecraft:spruce_planks")); }
        set(65, y + 1, -143, id("minecraft:spruce_slab[type=bottom]")); set(65, y, -143, SPRUCE_LOG_Y);
        set(63, y + 1, -140, stairs("spruce", "north")); set(64, y + 1, -140, stairs("spruce", "north"));
    }

    /** The signpost at the fork: a post with an arm sign each way. */
    private static void signpost(int x, int z) {
        int y = surf(x, z);
        set(x, y, z, COBBLE);
        for (int yy = y + 1; yy <= y + 3; yy++) set(x, yy, z, STRIPPED_SPRUCE_Y);
        set(x, y + 4, z, LANTERN);
        String[][] arms = {{"west", "<- Fort", "<- Farmstead"}, {"east", "Lighthouse ->", "Orchard ->"}, {"north", "Lookout", "Shepherd"}, {"south", "Wavebreak", "Port"}};
        for (String[] a : arms) {
            int dx = a[0].equals("east") ? 1 : a[0].equals("west") ? -1 : 0, dz = a[0].equals("south") ? 1 : a[0].equals("north") ? -1 : 0;
            set(x + dx, y + 2, z + dz, id("minecraft:spruce_wall_sign[facing=" + a[0] + "]"));
            signText(x + dx, y + 2, z + dz, "", a[1], a[2], "");
        }
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
        hangLantern(cx, y + 9, cz);
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

    /*
     * #45 THE WRECK OF THE MERRY WREN (rebuilt 2026-10-04) - an EASTER EGG. A merchant brig driven bow-first onto the
     * south-east beach and broken in two: the bow section lies silted up on the slope (x135..149, its hold full of sand,
     * the mermaid figurehead and the bowsprit still on her), a debris field in the gap (ribs, crates, her bell on the
     * sea floor), the stern section sunk upright in the shallows (x153..163) with the captain's cabin in the stern castle
     * just above the water. Ashore, the crew's camp (a tent made from the sail, a cold fire, a decoy salvage chest with
     * ordinary loot, the sign "Capn Wren went down with her"). The captain is still at his desk; his last words point
     * "under the mainmast" - a hole in the deck beside the mainmast stump shows suspicious sand, and under it lies
     * WRECK_CHEST: his last log (a written book), a few coins, and for every player the first time they open it,
     * CAPTAIN WREN'S MUSIC BOX (homestead/wreck/WreckSecret + MusicBoxBlock - it plays the Drunken Sailor).
     */
    private static final int WRZ = 80;                                                  // the wreck's centre line
    private static int wreckKeel(int x) { return x >= 153 ? 56 : (int) Math.round(SpawnIslandTerrain.islandSurfaceY(x, WRZ)) - 1; }
    /** Captain Wren's buried chest (the music box goes in here, once per player). */
    public static final int[] WRECK_CHEST = {145, wreckKeel(145) + 1, WRZ + 1};

    private static int wet(int y) { return y <= 62 ? WATER : AIR; }

    private static void beachWreck() {
        int dark = DARK_OAK, rot = id("pixelpirates:destroyed_planks");
        Random rng = new Random(1717L);
        // the old wreck's footprint goes back to the sea (only cells nothing else claims)
        for (int x = 134; x <= 158; x++)
            for (int z = 72; z <= 84; z++) {
                int s = surf(x, z);
                if (getRaw(x, s, z) == 0 && s <= 63) set(x, s, z, SAND);
                for (int y = s + 1; y <= 70; y++) if (getRaw(x, y, z) == 0) set(x, y, z, wet(y));
            }
        // THE HULL: bow section (silted) + stern section (flooded)
        for (int x = 135; x <= 163; x++) {
            if (x >= 150 && x <= 152) continue;
            boolean stern = x >= 153;
            int i = x - 135, yb = wreckKeel(x), deck = yb + 4;
            int half = stern ? 3 : i == 0 ? 0 : i == 1 ? 1 : i == 2 ? 2 : 3;
            for (int dz = -half; dz <= half; dz++) {
                int z = WRZ + dz;
                boolean side = Math.abs(dz) == half;
                for (int y = yb - 2; y <= yb + 9; y++) {
                    int b;
                    if (y < yb) b = SAND;                                                          // bedded in the sand
                    else if (half == 0) b = y <= deck + 2 ? DARK_OAK_LOG_Y : wet(y);              // the stem post
                    else if (side) {
                        boolean stove = !stern && dz > 0 && Math.floorMod(x, 4) == 1 && y >= yb + 2;   // starboard stove in
                        b = y <= deck + 1 && !stove ? (rng.nextInt(5) == 0 ? rot : dark) : wet(y);
                    } else if (y == yb) b = rng.nextInt(6) == 0 ? rot : dark;
                    else if (y < deck) b = stern ? WATER : SAND;
                    else if (y == deck) b = rng.nextInt(5) == 0 ? (stern ? WATER : SAND) : (rng.nextInt(4) == 0 ? rot : SPRUCE);
                    else b = wet(y);
                    set(x, y, z, b);
                }
            }
            if (!stern && i >= 3 && i % 3 == 0) set(x, deck + 2, WRZ - 3, stairsTop("dark_oak", "south"));   // ribs over the rail
        }
        // bowsprit + the mermaid figurehead
        int bd = wreckKeel(135) + 4;
        set(134, bd + 2, WRZ, SPRUCE_LOG_X); set(133, bd + 2, WRZ, SPRUCE_LOG_X); set(133, bd + 3, WRZ, SPRUCE_FENCE);
        set(134, bd + 1, WRZ, id("pixelpirates:mermaid_figurehead[facing=west]"));
        // the foremast stump, the MAINMAST stump (the secret beneath it), the mizzen stump on the stern
        int fd = wreckKeel(139) + 4, md = wreckKeel(145) + 4;
        fill(139, fd + 1, WRZ, 139, fd + 2, WRZ, SPRUCE_LOG_Y); set(139, fd + 3, WRZ, SPRUCE_FENCE);
        fill(145, md + 1, WRZ, 145, md + 4, WRZ, SPRUCE_LOG_Y); set(145, md + 5, WRZ, SPRUCE_FENCE);
        set(145, md + 1, WRZ - 1, id("pixelpirates:rope_coil[facing=north]"));
        fill(157, 61, WRZ, 157, 63, WRZ, SPRUCE_LOG_Y);
        // CAPTAIN WREN'S CHEST: under the deck beside the mainmast - a missing plank shows suspicious sand
        int[] c = WRECK_CHEST;
        set(c[0], c[1] - 1, c[2], dark);
        set(c[0], c[1], c[2], chest("north"));
        set(c[0], c[1] + 1, c[2], SAND);
        set(c[0], c[1] + 2, c[2], id("minecraft:suspicious_sand"));
        set(c[0], md, c[2], wet(md));
        nbt(c[0], c[1], c[2], "{Items:[{Slot:4b,id:\"minecraft:written_book\",Count:1b,tag:{title:\"Last Log of the Merry Wren\",author:\"Capt. A. Wren\",pages:["
                + "'{\"text\":\"Day 38.\\\\n\\\\nThe storm drove the Wren onto the shoals off Wavebreak. Her back is broken. The men are ashore and safe, thank the Tide.\"}',"
                + "'{\"text\":\"Day 40.\\\\n\\\\nThe salvagers will come for her by spring. They shall not have it. I have wrapped it in oilcloth and laid it where she was strongest - at the foot of her mainmast.\"}',"
                + "'{\"text\":\"Forty years it played us to sleep, every night, in every sea.\\\\n\\\\nWind it, whoever you are, and think of the Wren.\\\\n\\\\n- A. Wren, Master\"}'"
                + "]}},{Slot:12b,id:\"minecraft:gold_nugget\",Count:9b},{Slot:14b,id:\"minecraft:compass\",Count:1b}]}");
        // the debris field in the break: ribs standing out of the sand, planks, cargo, her bell on the sea floor
        for (int x = 150; x <= 152; x++) {
            int s = surf(x, WRZ);
            if (x != 151) for (int dz : new int[]{-3, 3}) {
                set(x, surf(x, WRZ + dz), WRZ + dz, rot);
                set(x, surf(x, WRZ + dz) + 1, WRZ + dz, DARK_OAK_FENCE);
                if (x == 150) set(x, surf(x, WRZ + dz) + 2, WRZ + dz, DARK_OAK_FENCE);
            }
            set(x, s, WRZ, rng.nextBoolean() ? rot : dark);
        }
        set(151, surf(151, WRZ + 1) + 1, WRZ + 1, id("minecraft:bell[attachment=floor,facing=east]"));
        set(151, surf(151, WRZ - 1) + 1, WRZ - 1, id("pixelpirates:cargo_crate[facing=south]"));
        set(148, surf(148, WRZ + 5) + 1, WRZ + 5, id("pixelpirates:cargo_crate[facing=east]"));
        set(155, surf(155, WRZ + 6) + 1, WRZ + 6, BARREL_UP);
        set(158, surf(158, WRZ - 5), WRZ - 5, rot);
        // THE STERN CASTLE: the captain's cabin x158..163, floor 62 (just clear of the sea), roof 66, a rail
        for (int x = 158; x <= 163; x++)
            for (int z = WRZ - 3; z <= WRZ + 3; z++) {
                boolean edge = x == 158 || x == 163 || z == WRZ - 3 || z == WRZ + 3;
                for (int y = 61; y <= 65; y++) set(x, y, z, edge ? (y == 61 || rng.nextInt(7) != 0 ? dark : rot) : y == 61 ? WATER : y == 62 ? SPRUCE : AIR);
                set(x, 66, z, rng.nextInt(6) == 0 ? rot : dark);
                if (edge && rng.nextInt(3) != 0) set(x, 67, z, DARK_OAK_FENCE);
            }
        set(158, 63, WRZ, AIR); set(158, 64, WRZ, AIR);                                   // the door from the drowned main deck
        for (int z = WRZ - 1; z <= WRZ + 1; z++) set(163, 64, z, GLASS_PANE);               // the stern windows
        for (int x = 160; x <= 161; x++) { set(x, 64, WRZ - 3, GLASS_PANE); set(x, 64, WRZ + 3, AIR); }   // one side broken out
        set(164, 63, WRZ, id("minecraft:dark_oak_wall_sign[facing=east]"));
        signText(164, 63, WRZ, "", "MERRY WREN", "of Wavebreak", "");
        // the cabin: the captain still at his desk, his last words on the wall, an emptied sea chest, cobwebs
        set(161, 63, WRZ, id("pixelpirates:captains_desk[facing=west]"));
        set(162, 63, WRZ, id("pixelpirates:captains_chair[facing=west]"));
        set(162, 64, WRZ, id("minecraft:skeleton_skull[rotation=4]"));
        set(161, 64, WRZ, id("minecraft:candle[candles=1,lit=false]"));
        set(159, 64, WRZ - 2, id("minecraft:oak_wall_sign[facing=south]"));
        signText(159, 64, WRZ - 2, "Capn Wren -", "My heart lies", "under the", "mainmast");
        set(162, 63, WRZ + 2, id("pixelpirates:sea_chest[facing=west]"));
        set(159, 63, WRZ + 2, BARREL_UP);
        set(162, 65, WRZ - 2, id("minecraft:cobweb")); set(159, 65, WRZ + 2, id("minecraft:cobweb"));
        hangLantern(160, 65, WRZ);
        // THE CREW'S CAMP on the beach (x140..147, z61..68): a tent cut from the sail, a cold fire, the salvage, a sign
        for (int x : new int[]{141, 145}) { int s = surf(x, 62); set(x, s + 1, 62, SPRUCE_FENCE); set(x, s + 2, 62, SPRUCE_FENCE); }
        for (int x = 141; x <= 145; x++) set(x, surf(x, 62) + 3, 62, WHITE_WOOL);
        for (int x = 142; x <= 144; x++) {
            for (int z : new int[]{61, 63}) { int s = surf(x, z); set(x, s + 1, z, WHITE_WOOL); set(x, s + 2, z, WHITE_WOOL); }
            set(x, surf(x, 62) + 1, 62, id("minecraft:red_carpet"));
        }
        set(143, surf(143, 66) + 1, 66, id("minecraft:campfire[lit=false,facing=north]"));
        set(141, surf(141, 66) + 1, 66, OAK_LOG_Z); set(145, surf(145, 66) + 1, 66, OAK_LOG_Z);
        lootChest(147, surf(147, 62) + 1, 62, "west", "minecraft:chests/shipwreck_supply");
        set(147, surf(147, 61) + 1, 61, id("pixelpirates:cargo_crate[facing=west]"));
        set(147, surf(147, 63) + 1, 63, BARREL_UP);
        set(140, surf(140, 64) + 1, 64, BARREL_UP);
        int ss = surf(146, 65);
        set(146, ss + 1, 65, id("minecraft:oak_sign[rotation=0]"));
        signText(146, ss + 1, 65, "Merry Wren crew", "- Day 41 -", "Capn Wren went", "down with her");
    }

    /** Two moored sloops alongside the side piers and rowboats at the quay steps. */
    private static void harborBoats() {
        sloop(-88, 106);   // west of the west pier
        sloop(52, 108);    // east of the east pier
        rowboat(-23, 98);  // moored at the west landing stage
        rowboat(33, 98);   // and the east one
        rowboat(-60, 101);
        rowboat(-73, 108);  // tied up beside the fishermen's pier
        rowboat(37, 112);   // and beside the cargo pier
        // gangplanks from the piers to the moored sloops (drawn after the sloops: they open the sloop's rail)
        gangplank(-81, -85, 116);
        gangplank(45, 49, 117);
    }
    /** A plank walk from a pier edge (xPier, deck top y66) down to a sloop's deck (xShip, deck top y64), along x. */
    private static void gangplank(int xPier, int xShip, int z) {
        int dir = xShip > xPier ? 1 : -1, n = Math.abs(xShip - xPier) + 1;
        for (int k = 0; k < n; k++) {
            int x = xPier + dir * k;
            int b = k < 2 ? slab("spruce") : k < 4 ? slabTop("spruce") : slab("spruce");
            set(x, k < 2 ? 65 : 64, z, b);
            fill(x, k < 2 ? 66 : 65, z, x, 67, z, AIR);
        }
        set(xShip + dir, 64, z, AIR);                                  // gap in the sloop's rail
        set(xPier - dir, 66, z, AIR);                                  // and in the pier's
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
        for (int[] l : UA_LOTS) if (cx + r >= l[0] - 1 && cx - r <= l[1] + 1 && cz + r >= l[2] - 1 && cz - r <= l[3] + 1) return false;   // a designed yard
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
        pass("Tavern - The Grog Barrel", () -> furnishTavern(r));
        pass("Inn", () -> furnishInn(r));
        pass("Chandlery", () -> furnishChandlery(r));
        pass("Bakery", () -> furnishBakery(r));
        pass("Harbourmaster", () -> furnishHarbormaster(r));
        pass("Warehouse (east)", () -> furnishTradingWarehouse(r));
        pass("Warehouse (west)", () -> furnishDistillery(r));
        pass("Fish Market", () -> furnishFishMarket(r));
        pass("Dock Office", () -> furnishDockOffice(r));
        pass("Chapel", () -> furnishChapel(r));
        pass("Manor", () -> furnishManor(r));
        pass("Guardhouse", () -> furnishGuardhouse(r));
        pass("Smithy", () -> furnishSmithy(r));
        pass("Shipwright Yard", () -> furnishShipwrightHall(r));
    }

    /** "The Grog Barrel": x48..76, z16..44, floors at 67 / 72. Door west (z29-30), stairs along z42. */
    /** The Grog Barrel, room by room (see tavern()). */
    private static void furnishTavern(Random r) {
        final int g = TG + 1, u = TF2 + 1;
        // ---- THE BAR: counter along z22 (barrels under a dark-oak top), a flap at x62; the back bar on the north wall:
        // kegs that pour for a doubloon, shelves of spirits, the rum rack, grog barrels; stools in front
        for (int x = 50; x <= 61; x++) { set(x, g, 22, barrel("south")); set(x, g + 1, 22, slabTop("dark_oak")); }
        String[] kegs = {"0", "1", "2", "0", "2"};
        for (int i = 0; i < 5; i++) set(51 + i * 2, g, 17, id("pixelpirates:tavern_keg[facing=south,drink=" + kegs[i] + "]"));
        for (int x = 50; x <= 60; x++) {
            set(x, g + 2, 17, slabTop("dark_oak"));
            set(x, g + 3, 17, x % 3 == 0 ? id("pixelpirates:spirit_bottles[facing=south,count=" + (2 + x % 3) + "]") : x % 3 == 1 ? id("pixelpirates:tankard[facing=south,count=3]") : id("minecraft:decorated_pot"));
        }
        set(61, g, 17, id("pixelpirates:rum_rack[facing=south,bottles=6]")); set(61, g + 1, 17, id("pixelpirates:rum_rack[facing=south,bottles=4]"));
        set(62, g, 17, GROG_BARREL); set(62, g + 1, 17, GROG_BARREL); set(62, g, 18, GROG_BARREL);
        set(49, g + 2, 20, id("pixelpirates:drinks_menu[facing=east]"));
        ip(50, g, 19, CAULDRON_WATER); ip(50, g, 20, BREWING);
        for (int x = 51; x <= 61; x += 2) ip(x, g, 23, id("pixelpirates:barrel_stool[facing=north]"));
        for (int x = 50; x <= 61; x += 3) ip(x, g + 2, 22, id("pixelpirates:tankard[facing=south,count=" + (1 + (x % 3)) + "]"));
        ip(53, g + 2, 22, id("pixelpirates:spirit_bottles[facing=south,count=2]")); ip(59, g + 2, 22, id("pixelpirates:dice_cup[facing=north]"));
        // ---- THE HALL: two trestle tables with benches, tankards, bottles, candles
        for (int z : new int[]{27, 33}) for (int x = 54; x <= 61; x++) {
            ip(x, g, z, slabTop("spruce"));
            ip(x, g, z - 1, stairs("spruce", "north")); ip(x, g, z + 1, stairs("spruce", "south"));
            int pick = (x + z) % 4;
            ip(x, g + 1, z, pick == 0 ? id("pixelpirates:tankard[facing=north,count=" + (1 + x % 3) + "]") : pick == 1 ? candle(1 + r.nextInt(3))
                    : pick == 2 ? id("pixelpirates:spirit_bottles[facing=east,count=" + (1 + x % 4) + "]") : AIR);
        }
        ip(53, g, 30, id("pixelpirates:barrel_stool[facing=east]")); ip(53, g, 31, id("pixelpirates:barrel_stool[facing=east]"));
        set(49, g + 2, 24, id("pixelpirates:drinks_menu[facing=east]")); set(49, g + 2, 34, id("pixelpirates:drinks_menu[facing=east]"));
        for (int z = 23; z <= 25; z++) ip(49, g, z, barrel("east")); for (int z = 33; z <= 35; z++) ip(49, g, z, barrel("east"));
        // ---- THE STAGE (south-west) + THE HEARTH (south wall)
        for (int x = 49; x <= 54; x++) for (int z = 39; z <= 43; z++) set(x, TG, z, x == 54 || z == 39 ? id("minecraft:dark_oak_slab[type=top]") : SPRUCE);
        for (int x = 49; x <= 54; x++) for (int z = 39; z <= 43; z++) if (x < 54 && z > 39) set(x, TG + 1, z, slab("spruce"));
        set(50, g, 43, id("minecraft:note_block")); set(51, g, 43, id("minecraft:note_block")); set(52, g, 43, JUKEBOX);
        set(50, g, 41, stairs("spruce", "west")); set(52, g, 41, stairs("spruce", "east"));
        for (int x = 49; x <= 53; x += 2) set(x, g + 2, 43, wallBanner(x == 51 ? "red" : "black", "north"));
        for (int x = 55; x <= 61; x++) for (int z = 40; z <= 42; z++) ip(x, g, z, carpet(x == 55 || x == 61 || z == 40 ? "brown" : "red"));
        ip(56, g, 41, stairs("dark_oak", "north")); ip(60, g, 41, stairs("dark_oak", "north"));
        ip(58, g, 40, slabTop("dark_oak")); ip(58, g + 1, 40, candle(3));
        // ---- THE DEN: green baize, two Liar's Dice tables, Crown & Anchor, the roulette wheel, a card table, the dartboard
        for (int x = 64; x <= 75; x++) for (int z = 22; z <= 43; z++) ip(x, g, z, carpet(((x + z) % 9 == 0) ? "lime" : "green"));
        int[][] liars = {{67, 26}, {67, 37}};
        for (int[] t : liars) {
            set(t[0], g, t[1], id("pixelpirates:liars_dice_table[facing=north]"));
            for (int[] d : new int[][]{{-1, 0, 0}, {1, 0, 0}, {0, -1, 0}, {0, 1, 0}})
                set(t[0] + d[0], g, t[1] + d[1], id("pixelpirates:barrel_stool[facing=" + (d[0] < 0 ? "east" : d[0] > 0 ? "west" : d[1] < 0 ? "south" : "north") + "]"));
            set(t[0], TF2 - 1, t[1], LANTERN_HANGING);
        }
        set(72, g, 26, id("pixelpirates:crown_anchor_table[facing=west]"));
        set(71, g, 25, id("pixelpirates:barrel_stool[facing=south]")); set(71, g, 27, id("pixelpirates:barrel_stool[facing=north]")); set(70, g, 26, id("pixelpirates:barrel_stool[facing=east]"));
        set(72, TF2 - 1, 26, LANTERN_HANGING);
        set(72, g, 32, id("pixelpirates:roulette_table[facing=west]"));
        set(70, g, 32, id("pixelpirates:barrel_stool[facing=east]")); set(72, g, 34, id("pixelpirates:barrel_stool[facing=north]"));
        set(72, TF2 - 1, 32, LANTERN_HANGING);
        // a card table with dice cups + coins in the corner, the dartboard + tally on the east wall
        set(73, g, 40, slabTop("dark_oak")); set(74, g, 40, slabTop("dark_oak"));
        set(73, g + 1, 40, id("pixelpirates:dice_cup[facing=north]")); set(74, g + 1, 40, id("minecraft:light_weighted_pressure_plate"));
        set(73, g, 41, stairs("dark_oak", "south")); set(74, g, 39, stairs("dark_oak", "north")); set(75, g, 40, stairs("dark_oak", "west"));
        set(76, g + 1, 36, id("minecraft:target"));
        set(75, g + 2, 34, wallBanner("yellow", "west")); set(75, g + 2, 38, wallBanner("green", "west"));
        for (int z = 22; z <= 43; z += 7) set(75, g + 2, z, wallSkull("west"));
        set(64, g, 43, id("pixelpirates:tavern_keg[facing=north,drink=0]")); set(65, g, 43, GROG_BARREL);
        // ---- BACK ROOM (north-east, the stair): crates, barrels, a desk with the ledger
        for (int x = 70; x <= 75; x++) { set(x, g, 17, barrel("south")); if (x % 2 == 0) set(x, g + 1, 17, id("pixelpirates:cargo_crate[facing=south]")); }
        ip(75, g, 19, slabTop("spruce")); ip(75, g + 1, 19, LECTERN_W); ip(74, g, 19, stairs("spruce", "west"));
        set(68, TF2 - 1, 18, LANTERN_HANGING);
        // ---- GALLERY (north + west + south walks): balcony tables over the hall, bunting under the rail
        for (int x = 54; x <= 61; x += 4) { ip(x, u, 18, slabTop("spruce")); ip(x, u + 1, 18, candle(2)); ip(x - 1, u, 18, stairs("spruce", "west")); ip(x + 1, u, 18, stairs("spruce", "east")); }
        for (int x = 54; x <= 61; x += 4) { ip(x, u, 42, slabTop("spruce")); ip(x, u + 1, 42, id("pixelpirates:tankard[facing=north,count=2]")); ip(x - 1, u, 42, stairs("spruce", "west")); ip(x + 1, u, 42, stairs("spruce", "east")); }
        for (int z = 22; z <= 38; z += 4) ip(50, u, z, id("pixelpirates:barrel_stool[facing=east]"));
        for (int z = 20; z <= 40; z += 5) hangLantern(50, TTOP - 1, z);
        for (int x = 53; x <= 61; x += 4) { hangLantern(x, TTOP - 1, 18); hangLantern(x, TTOP - 1, 42); }
        // ---- THE HIGH ROLLERS' ROOM (x64..75 z22..33): a private Liar's Dice table, a bar cart, plush chairs, gold
        for (int x = 64; x <= 75; x++) for (int z = 22; z <= 33; z++) ip(x, u, z, carpet(x == 64 || x == 75 || z == 22 || z == 33 ? "yellow" : "red"));
        set(70, u, 27, id("pixelpirates:liars_dice_table[facing=north]"));
        for (int[] d : new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}})
            set(70 + d[0], u, 27 + d[1], id("pixelpirates:captains_chair[facing=" + (d[0] < 0 ? "east" : d[0] > 0 ? "west" : d[1] < 0 ? "south" : "north") + "]"));
        hangLantern(70, TTOP - 1, 27);
        set(74, u, 23, slabTop("dark_oak")); set(74, u + 1, 23, id("pixelpirates:spirit_bottles[facing=west,count=4]"));
        set(75, u, 23, slabTop("dark_oak")); set(75, u + 1, 23, id("pixelpirates:tankard[facing=west,count=3]"));
        set(75, u, 31, id("minecraft:gold_block")); set(75, u + 1, 31, id("minecraft:potted_blue_orchid"));
        set(74, u, 31, stairs("dark_oak", "west")); set(74, u, 30, stairs("dark_oak", "west"));
        set(66, u, 31, id("pixelpirates:crown_anchor_table[facing=north]"));
        set(66, u, 32, id("pixelpirates:captains_chair[facing=north]"));
        set(64, u, 23, id("minecraft:potted_azalea_bush"));
        hangLantern(66, TTOP - 1, 31); hangLantern(73, TTOP - 1, 25);
        // ---- THE LANDLORD'S CABIN (x64..75 z35..43): bed, chests, desk, a strongbox of takings
        set(74, u, 42, bed("red", "south", true)); set(74, u, 41, bed("red", "south", false));
        set(75, u, 42, BARREL_UP); set(75, u + 1, 42, candle(1));
        set(72, u, 43, chest("north")); set(70, u, 43, BOOKSHELF); set(69, u, 43, BOOKSHELF); set(70, u + 1, 43, BOOKSHELF);
        set(66, u, 36, slabTop("dark_oak")); set(66, u + 1, 36, LANTERN); set(67, u, 36, slabTop("dark_oak")); set(67, u + 1, 36, MAP_BLOCK);
        set(66, u, 37, stairs("dark_oak", "south"));
        for (int x = 66; x <= 72; x++) for (int z = 38; z <= 41; z++) ip(x, u, z, carpet(x == 66 || x == 72 || z == 38 || z == 41 ? "black" : "gray"));
        hangLantern(70, TTOP - 1, 39);
    }
    /** A hanging lantern with a chain up to whatever is above it (under the open roof there is no ceiling close by). */
    private static void hangLantern(int x, int y, int z) {
        if (getRaw(x, y, z) != AIR) return;
        set(x, y, z, LANTERN_HANGING);
        for (int yy = y + 1; yy < y + 24 && getRaw(x, yy, z) == AIR; yy++) set(x, yy, z, CHAIN);
    }

    /** "The Salty Siren" inn: x-76..-48, z14..42, floors 67 / 72. Door east (z27-28), stairs along z16. */
    /** The Mermaid's Rest, room by room (see inn()). */
    private static void furnishInn(Random r) {
        final int g = IG + 1;
        // ---- LOBBY: reception counter + key hooks, guest book, armchairs, rug, chandelier
        for (int x = -56; x <= -52; x++) ip(x, g, 30, slabTop("dark_oak"));
        ip(-53, g + 1, 30, id("minecraft:bell[attachment=floor,facing=east]")); ip(-55, g + 1, 30, candle("yellow", 3));
        ip(-55, g, 31, LECTERN_N); ip(-56, g, 32, BARREL_UP); ip(-56, g + 1, 32, pot(r));
        for (int x = -56; x <= -52; x++) { ip(x, g + 2, 32, id("minecraft:tripwire_hook[facing=north]")); ip(x, g + 3, 32, shelf("north")); }
        for (int x = -55; x <= -50; x++) for (int z = 26; z <= 29; z++) ip(x, g, z, carpet(x == -55 || x == -50 || z == 26 || z == 29 ? "red" : "yellow"));
        ip(-56, g, 26, stairs("dark_oak", "west")); ip(-56, g, 27, slabTop("dark_oak")); ip(-56, g + 1, 27, pot(r));
        ip(-49, g, 31, id("minecraft:potted_azalea_bush")); ip(-49, g, 24, BOOKSHELF);
        set(-53, IF2 - 1, 28, LANTERN_HANGING); set(-53, IF2 - 1, 28, LANTERN_HANGING);
        // ---- TAPROOM: bar along x-54, casks + grog barrel behind, pots on the back bar, stools, tables by the windows
        for (int z = 16; z <= 20; z++) { ip(-54, g, z, slabTop("spruce")); ip(-53, g, z, OAK_FENCE); ip(-53, g + 1, z, carpet("red")); }
        for (int z = 15; z <= 21; z++) { ip(-56, g, z, barrel("east")); ip(-56, g + 1, z, z % 2 == 0 ? barrel("east") : id("minecraft:decorated_pot")); }
        ip(-55, g, 15, GROG_BARREL); ip(-55, g, 21, BREWING);
        ip(-54, g + 1, 17, id("minecraft:potted_dandelion")); ip(-54, g + 1, 19, candle("orange", 3));
        for (int z : new int[]{16, 21}) { ip(-50, g, z, slabTop("spruce")); ip(-50, g + 1, z, z == 16 ? LANTERN : pot(r)); ip(-51, g, z, stairs("spruce", "east")); ip(-49, g, z, stairs("spruce", "west")); }
        ip(-49, g, 18, BARREL_UP); ip(-49, g + 1, 18, id("minecraft:decorated_pot"));
        set(-52, IF2 - 1, 18, LANTERN_HANGING); set(-55, IF2 - 1, 18, LANTERN_HANGING);
        // ---- GAMES SNUG: chess table, card table, dartboard, jukebox, armchairs, bookshelves
        for (int x = -52; x <= -51; x++) for (int z = 37; z <= 38; z++) { ip(x, g, z, slabTop("dark_oak")); ip(x, g + 1, z, carpet(((x + z) & 1) == 0 ? "black" : "white")); }
        ip(-53, g, 37, stairs("dark_oak", "west")); ip(-50, g, 38, stairs("dark_oak", "east"));
        ip(-55, g, 40, slabTop("spruce")); ip(-55, g + 1, 40, candle("red", 2)); ip(-56, g, 40, stairs("spruce", "west")); ip(-54, g, 40, stairs("spruce", "east"));
        set(-52, g + 2, 42, id("minecraft:target"));
        ip(-49, g, 35, id("minecraft:jukebox")); ip(-49, g, 34, BOOKSHELF); ip(-49, g + 1, 34, BOOKSHELF);
        ip(-49, g, 41, BOOKSHELF); ip(-50, g, 41, BOOKSHELF);
        for (int x = -55; x <= -50; x++) for (int z = 35; z <= 39; z++) ip(x, g, z, carpet(x == -55 || x == -50 || z == 35 || z == 39 ? "green" : "lime"));
        set(-52, IF2 - 1, 37, LANTERN_HANGING);
        // ---- COMMON ROOM: two long tables, hearth corner with armchairs, a little stage, banners, chandeliers
        for (int z : new int[]{36, 40}) for (int x = -68; x <= (z == 36 ? -60 : -63); x++) {
            ip(x, g, z, slabTop("spruce"));
            ip(x, g + 1, z, (x & 1) == 0 ? candle(1 + r.nextInt(3)) : (r.nextBoolean() ? pot(r) : id("minecraft:decorated_pot")));
            ip(x, g, z - 1, stairs("spruce", "north")); ip(x, g, z + 1, stairs("spruce", "south"));
        }
        for (int z = 36; z <= 38; z++) ip(-72, g, z, stairs("dark_oak", "east"));
        for (int x = -74; x <= -73; x++) for (int z = 35; z <= 39; z++) ip(x, g, z, carpet(z == 35 || z == 39 ? "orange" : "red"));
        for (int x = -61; x <= -58; x++) for (int z = 40; z <= 41; z++) ip(x, g, z, slab("spruce"));
        ip(-58, g + 1, 41, id("minecraft:note_block")); ip(-60, g + 1, 41, stairs("spruce", "south"));
        String[] bannerCols = {"red", "yellow", "light_blue", "lime", "pink", "orange"};
        for (int i = 0; i < 6; i++) ip(-74 + i * 3, g + 2, 34, wallBanner(bannerCols[i], "south"));
        for (int x = -70; x <= -60; x += 5) set(x, IF2 - 1, 38, LANTERN_HANGING);
        ip(-75, g, 34, BARREL_UP); ip(-75, g + 1, 34, pot(r)); ip(-75, g, 41, BARREL_UP); ip(-75, g + 1, 41, pot(r));
        // ---- KITCHEN: range in the chimney, counters, an island with the baking, pantry barrels, sink
        ip(-75, g, 26, id("minecraft:smoker[facing=east]")); ip(-75, g, 30, id("minecraft:furnace[facing=east]")); ip(-74, g, 27, CAULDRON_WATER);
        for (int x = -74; x <= -68; x++) ip(x, g, 24, x % 3 == 0 ? CRAFTING : slabTop("spruce"));
        ip(-73, g + 1, 24, id("minecraft:cake")); ip(-70, g + 1, 24, id("minecraft:decorated_pot"));
        for (int x = -72; x <= -70; x++) for (int z = 27; z <= 29; z++) ip(x, g, z, slabTop("birch"));
        ip(-71, g + 1, 28, id("minecraft:cake")); ip(-72, g + 1, 27, MELON); ip(-70, g + 1, 29, PUMPKIN);
        for (int x = -75; x <= -70; x++) { ip(x, g, 32, barrel("north")); if (x % 2 == 0) ip(x, g + 1, 32, HAY); }
        ip(-75, g, 31, id("minecraft:dried_kelp_block")); ip(-75, g, 30, CAULDRON_WATER);
        set(-71, IF2 - 1, 26, LANTERN_HANGING); set(-71, IF2 - 1, 30, LANTERN_HANGING);
        // ---- BATHHOUSE: sunken tub, candles on the rim, basins, towels, plants
        for (int x = -74; x <= -71; x++) for (int z = 16; z <= 19; z++) set(x, IG, z, WATER);
        for (int x = -75; x <= -70; x += 5) for (int z = 16; z <= 19; z += 3) ip(x, g, z, candle("white", 3));
        ip(-69, g, 16, CAULDRON_WATER); ip(-69, g, 21, CAULDRON_WATER);
        for (int x = -75; x <= -73; x++) { ip(x, g, 22, BARREL_UP); ip(x, g + 1, 22, x == -74 ? WHITE_WOOL : carpet("white")); }
        ip(-75, g, 15, id("minecraft:potted_bamboo")); ip(-68, g, 15, id("minecraft:potted_fern"));
        set(-72, IF2 - 1, 18, LANTERN_HANGING);
        // ---- LAUNDRY: tubs, hampers, drying lines of colourful sheets, a loom for the mending
        for (int x = -66; x <= -63; x++) ip(x, g, 15, x % 2 == 0 ? CAULDRON_WATER : BARREL_UP);
        for (int x = -60; x <= -58; x++) ip(x, g, 15, barrel("south"));
        ip(-58, g, 22, id("minecraft:loom[facing=west]")); ip(-66, g, 22, BARREL_UP);
        String[] sheet = {"white", "pink", "light_blue", "yellow", "lime", "white"};
        for (int z : new int[]{17, 21}) {
            for (int x = -66; x <= -58; x++) set(x, g + 3, z, id("minecraft:chain[axis=x]"));
            for (int x = -65, i = 0; x <= -59; x += 2, i++) ip(x, g + 2, z, wool(sheet[(i + z) % sheet.length]));
        }
        set(-62, IF2 - 1, 19, LANTERN_HANGING);
        // ---- CORRIDORS (both floors): runners + lanterns
        for (int f = 0; f < 2; f++) {
            int fy = f == 0 ? IF2 : IF3;
            String run = f == 0 ? "red" : "blue";
            for (int x = -67; x <= -57; x++) for (int z = 22; z <= 34; z++) {
                boolean corr = (x == -67 || x == -57) || z == 22 || z == 34;
                if (!corr || innCourt(x, z) || (x > -67 && x < -57 && z > 22 && z < 34)) continue;
                ip(x, fy + 1, z, carpet(run));
            }
            for (int[] c : new int[][]{{-67, 22}, {-57, 22}, {-67, 34}, {-57, 34}, {-62, 22}, {-62, 34}, {-67, 28}, {-57, 25}})
                set(c[0], fy + 4, c[1], LANTERN_HANGING);
        }
        // ---- GUEST ROOMS: floor 2 then floor 3 (A B C north, D E F south, G west)
        guestRoom(-75, 15, -67, 20, IF2, 'S', "pink", "white", 2, r);           // the Rose Room
        washroom(-65, 15, -60, 20, IF2, r);
        guestRoom(-58, 15, -49, 20, IF2, 'S', "blue", "light_blue", 2, r);     // the Captain's Room
        ip(-50, IF2 + 1, 18, MAP_BLOCK);
        guestRoom(-75, 36, -67, 41, IF2, 'N', "yellow", "orange", 3, r);       // the Sunflower Room (hearth)
        guestRoom(-65, 36, -60, 41, IF2, 'N', "purple", "magenta", 1, r);      // the Lavender Room
        guestRoom(-58, 36, -49, 41, IF2, 'N', "orange", "red", 2, r);          // the Coral Room
        bunkroom(IF2, "lime", r);                                              // the Mariners' Bunkroom
        lounge(IF2, r);
        guestRoom(-75, 15, -67, 20, IF3, 'S', "cyan", "lime", 3, r);           // the Seafoam Room
        linenRoom(-65, 15, -60, 20, IF3, r);
        guestRoom(-58, 15, -49, 20, IF3, 'S', "red", "yellow", 2, r);          // the Merchant's Suite
        if (ip(-50, IF3 + 1, 18, id("minecraft:gold_block"))) ip(-50, IF3 + 2, 18, candle("yellow", 4));
        guestRoom(-75, 36, -67, 41, IF3, 'N', "brown", "green", 1, r);         // the Fisherman's Room (hearth)
        guestRoom(-65, 36, -60, 41, IF3, 'N', "light_blue", "blue", 1, r);     // the Moonlit Room
        guestRoom(-58, 36, -49, 41, IF3, 'N', "magenta", "pink", 2, r);        // the Honeymoon Room
        ip(-50, IF3 + 1, 40, id("minecraft:flowering_azalea")); ip(-49, IF3 + 1, 37, id("minecraft:potted_allium"));
        bunkroom(IF3, "cyan", r);                                              // the Navigators' Dorm
        lounge(IF3, r);
    }

    // room-local frame: u along the back wall, v out from it toward the door wall
    private static int RX1, RZ1, RX2, RZ2, RFY;
    private static char RD;
    private static int ru() { return RD == 'S' || RD == 'N' ? RX2 - RX1 + 1 : RZ2 - RZ1 + 1; }
    private static int rv() { return RD == 'S' || RD == 'N' ? RZ2 - RZ1 + 1 : RX2 - RX1 + 1; }
    private static int rxu(int u, int v) { return switch (RD) { case 'S' -> RX1 + u; case 'N' -> RX2 - u; case 'W' -> RX2 - v; default -> RX1 + v; }; }
    private static int rzu(int u, int v) { return switch (RD) { case 'S' -> RZ1 + v; case 'N' -> RZ2 - v; case 'W' -> RZ1 + u; default -> RZ2 - u; }; }
    private static String rBack() { return switch (RD) { case 'S' -> "north"; case 'N' -> "south"; case 'W' -> "east"; default -> "west"; }; }
    private static String rUPos() { return switch (RD) { case 'S' -> "east"; case 'N' -> "west"; case 'W' -> "south"; default -> "north"; }; }
    private static boolean rp(int u, int v, int dy, int b) { return ip(rxu(u, v), RFY + dy, rzu(u, v), b); }

    /** A guest room: beds (1 single, 2 double, 3 twin) against the back wall with banners over them, nightstands,
     *  a wardrobe + sea chest, a writing desk with chair and shelf, an armchair, plants, a rug, a hanging lantern. */
    private static void guestRoom(int x1, int z1, int x2, int z2, int fy, char door, String c1, String c2, int beds, Random r) {
        RX1 = x1; RZ1 = z1; RX2 = x2; RZ2 = z2; RFY = fy; RD = door;
        int uu = ru(), vv = rv(), c = uu / 2 - (beds == 2 ? 1 : 0);
        String back = rBack(), in = opp(back), up = rUPos(), un = opp(up);
        int[] bedU = beds == 3 ? new int[]{1, uu - 2} : beds == 2 ? new int[]{c, c + 1} : new int[]{c};
        for (int bu : bedU) {
            rp(bu, 0, 1, bed(c1, back, true)); rp(bu, 1, 1, bed(c1, back, false));
            rp(bu, 0, 3, wallBanner(c2, in));
        }
        for (int bu : bedU) {
            int nu = bu + 1;
            if (nu >= uu || nu == bedU[bedU.length - 1] && bedU.length == 2 && bu == bedU[0]) nu = bu - 1;
            if (nu >= 0 && rp(nu, 0, 1, BARREL_UP)) rp(nu, 0, 2, (bu & 1) == 0 ? candle(c2, 2) : LANTERN);
        }
        rp(0, 0, 1, barrel(up)); rp(0, 0, 2, barrel(up)); rp(0, 1, 1, chest(up));
        if (rp(uu - 1, vv - 2, 1, slabTop("birch"))) { rp(uu - 1, vv - 2, 2, r.nextBoolean() ? LANTERN : pot(r)); rp(uu - 2, vv - 2, 1, stairs("birch", un)); rp(uu - 1, vv - 2, 3, shelf(un)); }
        rp(0, vv - 2, 1, stairs("dark_oak", un)); if (rp(0, vv - 3, 1, slabTop("dark_oak"))) rp(0, vv - 3, 2, candle(c1, 1));
        rp(uu - 1, 0, 1, pot(r)); rp(uu - 1, vv - 1, 1, pot(r));
        for (int u = 1; u <= uu - 2; u++) for (int v = 2; v <= vv - 2; v++) rp(u, v, 1, carpet(u == 1 || u == uu - 2 || v == 2 || v == vv - 2 ? c2 : c1));
        rp(uu / 2, vv / 2, 4, LANTERN_HANGING);
    }
    /** Floor 2 north-middle: the washroom (a cauldron bathtub, basin, towels, mirror glass). */
    private static void washroom(int x1, int z1, int x2, int z2, int fy, Random r) {
        int y = fy + 1, tub = id("minecraft:water_cauldron[level=3]");
        for (int x = x1; x <= x1 + 2; x++) ip(x, y, z1, tub);
        ip(x1 + 3, y, z1, candle("white", 3)); ip(x2, y, z1, CAULDRON_WATER); ip(x2, y + 2, z1 - 0, id("minecraft:light_blue_stained_glass_pane[east=true,west=true]"));
        ip(x2, y, z1 + 2, BARREL_UP); ip(x2, y + 1, z1 + 2, WHITE_WOOL); ip(x1, y, z2 - 1, id("minecraft:potted_bamboo"));
        for (int x = x1; x <= x2; x++) for (int z = z1 + 1; z <= z2; z++) ip(x, y, z, carpet(((x + z) & 1) == 0 ? "white" : "light_blue"));
        ip((x1 + x2) / 2, fy + 4, (z1 + z2) / 2, LANTERN_HANGING);
    }
    /** Floor 3 north-middle: linen store (folded linen, brooms, the maid's chair). */
    private static void linenRoom(int x1, int z1, int x2, int z2, int fy, Random r) {
        int y = fy + 1;
        for (int x = x1; x <= x2; x++) { ip(x, y, z1, barrel("south")); ip(x, y + 1, z1, (x & 1) == 0 ? WHITE_WOOL : wool("light_gray")); ip(x, y + 2, z1, shelf("south")); }
        ip(x1, y, z1 + 2, CAULDRON_WATER); ip(x2, y, z1 + 3, stairs("spruce", "east"));
        ip(x1, y, z2 - 1, id("minecraft:composter"));
        ip((x1 + x2) / 2, fy + 4, (z1 + z2) / 2, LANTERN_HANGING);
    }
    /** The west wing (x-75..-69, z22..34): hammocks slung between posts either side of the hearth, sea chests. */
    private static void bunkroom(int fy, String col, Random r) {
        int y = fy + 1;
        for (int z : new int[]{23, 25, 31, 33}) {
            for (int px : new int[]{-74, -72, -70}) { ip(px, y, z, SPRUCE_FENCE); ip(px, y + 1, z, SPRUCE_FENCE); ip(px, y + 2, z, SPRUCE_FENCE); }
            ip(-73, y + 1, z, hammock("north")); ip(-71, y + 1, z, hammock("north"));
            ip(-69, y, z, barrel("west"));
        }
        for (int z = 26; z <= 30; z++) if (z != 28) ip(-73, y, z, stairs("spruce", "east"));
        for (int x = -72; x <= -70; x++) for (int z = 26; z <= 30; z++) ip(x, y, z, carpet(x == -72 || x == -70 || z == 26 || z == 30 ? col : "white"));
        ip(-69, y, 26, BARREL_UP); ip(-69, y + 1, 26, LANTERN); ip(-69, y, 30, BARREL_UP); ip(-69, y + 1, 30, pot(r));
        ip(-75, y + 3, 24, wallBanner(col, "east")); ip(-75, y + 3, 32, wallBanner(col, "east"));
        set(-72, fy + 4, 24, LANTERN_HANGING); set(-72, fy + 4, 32, LANTERN_HANGING); set(-71, fy + 4, 28, LANTERN_HANGING);
    }
    /** The east wing lounge on each upper floor: bookshelves, armchairs round a map table, window seats. */
    private static void lounge(int fy, Random r) {
        int y = fy + 1;
        for (int z : new int[]{22, 23, 33, 34}) { ip(-49, y, z, BOOKSHELF); ip(-49, y + 1, z, BOOKSHELF); }
        ip(-52, y, 28, MAP_BLOCK);
        ip(-53, y, 28, stairs("dark_oak", "west")); ip(-51, y, 28, stairs("dark_oak", "east"));
        ip(-52, y, 27, stairs("dark_oak", "north")); ip(-52, y, 29, stairs("dark_oak", "south"));
        for (int x = -54; x <= -50; x++) for (int z = 26; z <= 30; z++) ip(x, y, z, carpet(x == -54 || x == -50 || z == 26 || z == 30 ? "orange" : "yellow"));
        ip(-55, y, 22, pot(r)); ip(-55, y, 34, pot(r)); ip(-49, y, 28, id("minecraft:potted_blue_orchid"));
        set(-52, fy + 4, 28, LANTERN_HANGING); set(-52, fy + 4, 23, LANTERN_HANGING); set(-52, fy + 4, 33, LANTERN_HANGING);
    }

    /** The chandlery, room by room (see chandlery()). */
    private static void furnishChandlery(Random r) {
        final int g = CG + 1, u = CF2 + 1;
        // ---- the bay windows: lanterns, a soul lantern, coils of rope on the sills
        for (int bz : new int[]{17, 29}) {
            set(-86, g + 1, bz + 1, LANTERN); set(-86, g + 1, bz + 2, id("pixelpirates:rope_coil[facing=east]")); set(-86, g + 1, bz + 3, id("minecraft:soul_lantern"));
        }
        // ---- THE COUNTER (z33) + the keeper's shelves (south wall)
        for (int x = -99; x <= -90; x++) { set(x, g, 33, barrel("north")); set(x, g + 1, 33, slabTop("dark_oak")); }
        set(-94, g + 2, 33, id("minecraft:bell[attachment=floor,facing=north]")); set(-97, g + 2, 33, LANTERN); set(-91, g + 2, 33, candle(3));
        set(-92, g + 2, 33, id("pixelpirates:rope_coil[facing=north]"));
        for (int x = -99; x <= -90; x++) {
            set(x, g, 35, x % 2 == 0 ? barrel("north") : id("pixelpirates:cargo_crate[facing=north]"));
            set(x, g + 1, 35, shelf("north"));
            set(x, g + 2, 35, x % 3 == 0 ? LANTERN : x % 3 == 1 ? id("minecraft:soul_lantern") : id("minecraft:decorated_pot"));
            set(x, g + 3, 35, shelf("north"));
        }
        // ---- THE CHART CORNER (north-east)
        set(-92, g, 17, id("pixelpirates:map_table[facing=south]")); set(-95, g, 16, CARTOGRAPHY);
        set(-89, g, 16, LECTERN_W); set(-88, g, 18, id("pixelpirates:sea_chest[facing=west]"));
        set(-93, g, 18, id("pixelpirates:captains_chair[facing=north]"));
        // ---- THE ROPE WALL (north wall x-102..-96): coils on the floor and on a shelf, chains hung between
        for (int x = -102; x <= -96; x++) {
            set(x, g, 15, id("pixelpirates:rope_coil[facing=south]"));
            set(x, g + 1, 15, slabTop("spruce"));
            set(x, g + 2, 15, x % 2 == 0 ? id("pixelpirates:rope_coil[facing=south]") : CHAIN);
            set(x, g + 3, 15, CHAIN);
        }
        // ---- THE LANTERN CEILING over the shop floor
        for (int x = -101; x <= -88; x += 2)
            for (int z = 20; z <= 31; z += 2) {
                int zz = z + ((x / 2) & 1);
                if (zz > 31) continue;
                boolean soul = Math.floorMod(x * 7 + zz * 3, 5) == 0, low = Math.floorMod(x + zz, 3) == 0;
                int lamp = soul ? id("minecraft:soul_lantern[hanging=true]") : LANTERN_HANGING;
                if (low) { ip(x, CF2 - 1, zz, CHAIN); ip(x, CF2 - 2, zz, lamp); } else ip(x, CF2 - 1, zz, lamp);
            }
        // ---- the shop floor: ship's wheels, a display cannon with shot, coils, a rug
        set(-97, g, 22, id("pixelpirates:ships_wheel[facing=east]")); set(-97, g, 28, id("pixelpirates:ships_wheel[facing=east]"));
        set(-93, g, 29, id("pixelpirates:display_cannon[facing=east]"));
        set(-93, g, 30, id("minecraft:coal_block")); set(-93, g + 1, 30, id("minecraft:polished_blackstone_slab"));
        for (int x = -95; x <= -89; x++) for (int z = 21; z <= 29; z++) ip(x, g, z, carpet(x == -95 || x == -89 || z == 21 || z == 29 ? "blue" : "light_blue"));
        // ---- THE STOCK RACKS (west half): barrels, crates and canvas bolts on two-tier shelves
        int[] rackZ = {17, 18, 21, 22, 28, 29};
        for (int rz : rackZ)
            for (int x = -110; x <= -103; x++) {
                if (x == -110 || x == -103) { fill(x, g, rz, x, g + 3, rz, SPRUCE_FENCE); continue; }
                int pick = Math.floorMod(x * 5 + rz * 3, 6);
                set(x, g, rz, pick < 2 ? BARREL_UP : pick < 4 ? id("pixelpirates:cargo_crate[facing=" + (rz % 2 == 0 ? "north" : "south") + "]") : WHITE_WOOL);
                set(x, g + 1, rz, slabTop("spruce"));
                set(x, g + 2, rz, pick == 0 ? id("pixelpirates:rope_coil[facing=north]") : pick == 1 ? LANTERN : pick == 2 ? id("pixelpirates:striped_sail_canvas")
                        : pick == 3 ? id("minecraft:decorated_pot") : pick == 4 ? WHITE_WOOL : id("pixelpirates:tankard[facing=north,count=2]"));
                set(x, g + 3, rz, slabTop("spruce"));
            }
        // ---- FIGUREHEADS FOR SALE on plinths along the south wall, anchors + chain in the corner
        String[] heads = {"mermaid", "kraken", "dread_skull", "navy_eagle"};
        for (int i = 0; i < 4; i++) {
            int x = -110 + i * 3;
            set(x, g, 35, POLISHED_ANDESITE);
            set(x, g + 1, 35, id("pixelpirates:" + heads[i] + "_figurehead[facing=north]"));
            set(x, g + 2, 35, LANTERN);
        }
        set(-111, g, 31, ANCHOR_BLOCK); set(-111, g, 32, ANCHOR_BLOCK); set(-111, g + 1, 31, ANCHOR_BLOCK);
        for (int x = -110; x <= -108; x++) set(x, g, 32, id("minecraft:chain[axis=x]"));
        set(-104, CF2 - 1, 25, LANTERN_HANGING); set(-107, CF2 - 1, 20, LANTERN_HANGING); set(-107, CF2 - 1, 31, LANTERN_HANGING);
        // ---- THE SAIL LOFT (upstairs): a sail spread on the floor, sails drying from the beams, bolts, spools, benches
        for (int x = -104; x <= -94; x++) for (int z = 20; z <= 30; z++) {
            boolean edge = x == -104 || x == -94 || z == 20 || z == 30;
            set(x, CF2, z, id(edge ? "pixelpirates:striped_sail_canvas" : "pixelpirates:white_sail_canvas"));
            if (edge) ip(x, u, z, carpet("brown"));
        }
        for (int z = 22; z <= 28; z++) { for (int y = CTOP - 2; y <= CTOP; y++) { set(-108, y, z, id("pixelpirates:white_sail_canvas")); set(-90, y, z, id("pixelpirates:striped_sail_canvas")); } }
        for (int x = -110; x <= -106; x++) { set(x, u, 33, x % 2 == 0 ? WHITE_WOOL : id("pixelpirates:white_sail_canvas")); set(x, u + 1, 33, x % 2 == 0 ? id("pixelpirates:striped_sail_canvas") : WHITE_WOOL); set(x, u, 34, WHITE_WOOL); }
        for (int x = -101; x <= -98; x++) { set(x, u, 34, id("minecraft:stripped_spruce_log[axis=x]")); set(x, u + 1, 34, id("pixelpirates:rope_coil[facing=north]")); }
        for (int z = 22; z <= 28; z += 3) { ip(-106, u, z, stairs("spruce", "west")); ip(-92, u, z, stairs("spruce", "east")); }
        ip(-106, u, 23, slabTop("spruce")); ip(-106, u + 1, 23, id("minecraft:tripwire_hook[facing=east]"));
        set(-90, u, 16, id("pixelpirates:captains_desk[facing=south]")); set(-91, u, 17, id("pixelpirates:captains_chair[facing=north]"));
        for (int z = 22; z <= 28; z++) if (z != 25) { ip(-88, u, z, z % 2 == 0 ? id("pixelpirates:cargo_crate[facing=west]") : BARREL_UP); }
        for (int x = -106; x <= -92; x += 7) for (int z = 18; z <= 32; z += 7) hangLantern(x, CTOP - 1, z);
    }

    /** Bakery: x86..112, z16..38, floor 67. Door west z26. */
    /** The bakery, room by room (see bakery()). */
    private static void furnishBakery(Random r) {
        final int g = BG + 1, u = BF2 + 1;
        // ---- THE SHOP: the counter (z23) with cakes + bread baskets, shelves of loaves on the partition behind
        for (int x = 89; x <= 108; x++) {
            set(x, g, 23, x % 3 == 0 ? id("minecraft:glass") : OAK);
            set(x, g + 1, 23, slabTop("birch"));
            int pick = Math.floorMod(x, 5);
            set(x, g + 2, 23, pick == 0 ? id("minecraft:cake") : pick == 1 ? id("minecraft:composter[level=7]") : pick == 2 ? id("minecraft:candle_cake[lit=true]")
                    : pick == 3 ? id("minecraft:composter[level=6]") : id("minecraft:decorated_pot"));
        }
        set(99, g + 2, 23, id("minecraft:bell[attachment=floor,facing=north]"));
        for (int x = 87; x <= 111; x++) {                                   // wall shelves over the bakers' aisle (z24)
            if (x == 98 || x == 99) continue;
            set(x, g + 2, 24, id("minecraft:spruce_trapdoor[facing=north,half=top,open=false]"));
            set(x, g + 3, 24, x % 3 == 0 ? id("minecraft:composter[level=7]") : x % 3 == 1 ? id("minecraft:cake") : id("minecraft:decorated_pot"));
        }
        // the cafe corner (north-west): two little tables with chairs, the menu board
        for (int[] t : new int[][]{{89, 18}, {93, 20}}) {
            ip(t[0], g, t[1], OAK_FENCE); ip(t[0], g + 1, t[1], id("minecraft:white_carpet"));
            ip(t[0] - 1, g, t[1], stairs("oak", "west")); ip(t[0] + 1, g, t[1], stairs("oak", "east"));
            ip(t[0], g + 2, t[1], id("minecraft:candle_cake[lit=true]"));
        }
        set(87, g + 2, 21, id("pixelpirates:drinks_menu[facing=east]"));
        // display tables in the middle of the shop + flowers by the door
        for (int x = 103; x <= 106; x++) { ip(x, g, 19, slabTop("birch")); ip(x, g + 1, 19, x % 2 == 0 ? id("minecraft:cake") : id("minecraft:composter[level=7]")); }
        ip(97, g, 17, id("minecraft:potted_red_tulip")); ip(100, g, 17, id("minecraft:potted_oxeye_daisy"));
        for (int x = 88; x <= 110; x += 5) set(x, BF2 - 1, 20, LANTERN_HANGING);
        // ---- THE BAKEHOUSE: kneading island, flour sacks, hay, cooling racks, mixing bowls, firewood, the peel
        for (int x = 101; x <= 106; x++) for (int z = 30; z <= 31; z++) { set(x, g, z, slabTop("birch")); set(x, g + 1, z, id("minecraft:white_carpet")); }
        set(103, g + 1, 30, id("minecraft:cake")); set(105, g + 1, 31, id("minecraft:composter[level=4]"));
        for (int x = 88; x <= 90; x++) for (int z = 34; z <= 36; z++) { set(x, g, z, WHITE_WOOL); if ((x + z) % 2 == 0) set(x, g + 1, z, WHITE_WOOL); }
        set(88, g, 37, HAY); set(89, g, 37, HAY); set(88, g + 1, 37, HAY);
        for (int x = 100; x <= 108; x += 2) {
            set(x, g, 37, id("minecraft:iron_bars"));
            for (int y = g; y <= g + 2; y++) set(x + 1, y, 37, id("minecraft:spruce_trapdoor[facing=north,half=top,open=false]"));
            set(x + 1, g + 1, 37, id("minecraft:composter[level=7]"));
            set(x, g + 1, 37, id("minecraft:iron_bars")); set(x, g + 2, 37, id("minecraft:iron_bars"));
        }
        set(95, g, 37, CAULDRON_WATER); set(96, g, 37, CAULDRON_WATER); set(94, g, 37, BARREL_UP);
        for (int z = 31; z <= 34; z++) { set(111, g, z, id("minecraft:oak_log[axis=x]")); if (z < 34) set(111, g + 1, z, id("minecraft:oak_log[axis=x]")); }
        set(111, g + 2, 26, id("minecraft:spruce_fence")); set(111, g + 1, 26, id("minecraft:spruce_trapdoor[facing=west,half=bottom,open=true]"));
        set(111, g, 25 + 1, id("minecraft:smoker[facing=west]"));
        set(110, g, 28, id("minecraft:stone_brick_slab"));
        ip(94, g, 32, id("minecraft:crafting_table")); ip(95, g, 32, slabTop("birch")); ip(95, g + 1, 32, id("minecraft:honey_block"));
        set(99, BF2 - 1, 30, LANTERN_HANGING); set(106, BF2 - 1, 34, LANTERN_HANGING); set(92, BF2 - 1, 30, LANTERN_HANGING);
        // ---- UPSTAIRS: the kitchen-parlour (NW), the bedroom (NE), the children's room (SW), the flour loft (SE)
        set(88, u, 33, CAMPFIRE); set(88, u + 2, 33, id("minecraft:brick_slab[type=top]"));
        for (int x = 89; x <= 93; x++) { ip(x, u, 21, slabTop("oak")); ip(x, u + 1, 21, x % 2 == 0 ? candle(2) : id("minecraft:composter[level=7]")); ip(x, u, 20, stairs("oak", "north")); ip(x, u, 22, stairs("oak", "south")); }
        for (int x = 87; x <= 95; x++) { ip(x, u, 17, x % 2 == 0 ? BARREL_UP : id("minecraft:smoker[facing=south]")); ip(x, u + 2, 17, shelf("south")); }
        for (int x = 88; x <= 94; x++) for (int z = 19; z <= 23; z++) ip(x, u, z, carpet(x == 88 || x == 94 || z == 19 || z == 23 ? "red" : "white"));
        set(91, BTOP - 1, 21, LANTERN_HANGING);
        set(109, u, 17, bed("pink", "north", true)); set(109, u, 18, bed("pink", "north", false));
        set(110, u, 17, bed("pink", "north", true)); set(110, u, 18, bed("pink", "north", false));
        set(111, u, 17, BARREL_UP); set(111, u + 1, 17, candle(1)); set(108, u, 17, BARREL_UP); set(108, u + 1, 17, id("minecraft:potted_azalea_bush"));
        set(98, u, 17, chest("south")); set(98, u, 18, chest("south"));
        for (int x = 99; x <= 111; x++) for (int z = 19; z <= 23; z++) ip(x, u, z, carpet(x == 99 || x == 111 || z == 19 || z == 23 ? "pink" : "white"));
        set(104, BTOP - 1, 21, LANTERN_HANGING);
        set(89, u, 37, bed("yellow", "south", true)); set(89, u, 36, bed("yellow", "south", false));
        set(92, u, 37, bed("light_blue", "south", true)); set(92, u, 36, bed("light_blue", "south", false));
        set(90, u, 37, BARREL_UP); set(90, u + 1, 37, LANTERN); set(97, u, 37, chest("north")); set(96, u, 37, id("minecraft:note_block"));
        for (int x = 88; x <= 97; x++) for (int z = 29; z <= 34; z++) ip(x, u, z, carpet(((x + z) & 1) == 0 ? "yellow" : "light_blue"));
        set(93, BTOP - 1, 32, LANTERN_HANGING);
        for (int x = 101; x <= 111; x++) for (int z = 33; z <= 37; z++) if ((x + z) % 3 != 0) { set(x, u, z, WHITE_WOOL); if ((x * z) % 4 == 0) set(x, u + 1, z, WHITE_WOOL); }
        for (int x = 101; x <= 104; x++) { set(x, u, 29, HAY); set(x, u + 1, 29, HAY); }
        set(110, u, 29, BARREL_UP); set(111, u, 29, BARREL_UP); set(111, u + 1, 29, BARREL_UP);
        set(106, BTOP - 1, 31, LANTERN_HANGING);
        for (int x = 89; x <= 110; x += 7) set(x, BTOP - 1, 26, LANTERN_HANGING);
    }

    /** Harbormaster: x-28..-12, z60..76, floors 65 / 69. Door west z68, stairs along z62. */
    /** The harbourmaster's, room by room (see harbormaster()). */
    private static void furnishHarbormaster(Random r) {
        final int g = HG + 1, u = HF2 + 1;
        // ---- THE PUBLIC OFFICE: the clerks' counter, pigeon-hole records behind, benches, the model ship, the chart wall
        for (int x = -27; x <= -19; x++) { set(x, g, 65, barrel("south")); set(x, g + 1, 65, slabTop("dark_oak")); }
        set(-23, g + 2, 65, id("minecraft:bell[attachment=floor,facing=south]")); set(-26, g + 2, 65, candle(2));
        for (int x = -27; x <= -19; x++) {
            int occ = Math.floorMod(x * 5, 7);
            String slots = "slot_0_occupied=" + (occ % 2 == 0) + ",slot_1_occupied=" + (occ % 3 == 0) + ",slot_2_occupied=" + (occ < 4)
                    + ",slot_3_occupied=" + (occ % 2 == 1) + ",slot_4_occupied=true,slot_5_occupied=" + (occ > 2);
            set(x, g, 61, id("minecraft:chiseled_bookshelf[facing=south," + slots + "]"));
            set(x, g + 1, 61, id("minecraft:chiseled_bookshelf[facing=south," + slots + "]"));
        }
        ip(-24, g, 63, LECTERN_S()); ip(-21, g, 63, slabTop("spruce")); ip(-21, g + 1, 63, candle(1)); ip(-22, g, 63, stairs("spruce", "west"));
        ip(-27, g, 62, BARREL_UP); ip(-27, g, 63, BARREL_UP); ip(-27, g + 1, 62, BARREL_UP);
        for (int x = -26; x <= -23; x++) ip(x, g, 75, stairs("dark_oak", "south"));
        for (int z = 72; z <= 73; z++) ip(-18, g, z, stairs("dark_oak", "east"));
        set(-22, g, 70, DARK_OAK_FENCE); set(-22, g + 1, 70, slabTop("spruce")); set(-23, g + 1, 70, id("minecraft:spruce_stairs[facing=west,half=top]"));
        set(-21, g + 1, 70, id("minecraft:spruce_stairs[facing=east,half=top]")); set(-22, g + 2, 70, id("minecraft:white_banner[rotation=4]"));
        int hmWall = getRaw(-17, g + 3, 70);                                                        // the chart wall: framed charts on it
        for (int z = 69; z <= 71; z++) for (int y = g + 1; y <= g + 2; y++) { set(-17, y, z, hmWall); ip(-18, y, z, id("pixelpirates:sea_chart[facing=west]")); }
        for (int x = -25; x <= -19; x++) for (int z = 67; z <= 73; z++) ip(x, g, z, carpet(x == -25 || x == -19 || z == 67 || z == 73 ? "cyan" : "light_blue"));
        for (int[] c : new int[][]{{-23, 67}, {-23, 73}, {-20, 70}}) set(c[0], HF2 - 1, c[1], LANTERN_HANGING);
        // ---- THE HARBOURMASTER'S OFFICE (x-16..-13 z61..71)
        set(-14, g, 64, id("pixelpirates:captains_desk[facing=west]")); set(-13, g, 64, id("pixelpirates:captains_chair[facing=west]"));
        set(-14, g, 69, id("pixelpirates:map_table[facing=west]"));
        for (int z = 61; z <= 62; z++) { set(-13, g, z, BOOKSHELF); set(-13, g + 1, z, BOOKSHELF); set(-14, g, z, BOOKSHELF); }
        set(-13, g, 71, id("pixelpirates:ship_helm[facing=west]"));
        set(-13, g + 2, 67, wallBanner("blue", "west"));
        set(-16, g, 61, id("pixelpirates:sea_chest[facing=east]"));
        for (int x = -16; x <= -14; x++) for (int z = 65; z <= 68; z++) ip(x, g, z, carpet(x == -16 || z == 68 ? "blue" : "cyan"));
        set(-14, HF2 - 1, 66, LANTERN_HANGING);
        // ---- THE STRONGROOM (tower base): chests, crates, a gold bar or two
        set(-13, g, 75, chest("north")); set(-15, g, 75, id("pixelpirates:cargo_crate[facing=north]")); set(-15, g + 1, 75, id("pixelpirates:cargo_crate[facing=east]"));
        set(-13, g, 74, id("minecraft:gold_block")); set(-14, g, 75, BARREL_UP);
        set(-14, g + 1, 76, id("minecraft:iron_bars")); set(-14, g + 2, 76, id("minecraft:iron_bars"));
        set(-14, HF2 - 1, 74, LANTERN_HANGING);
        // ---- RECORDS ROOM (upstairs west): shelves, pigeon holes, lecterns, the reading table
        for (int z = 61; z <= 67; z++) { set(-27, u, z, BOOKSHELF); set(-27, u + 1, z, BOOKSHELF); set(-27, u + 2, z, BOOKSHELF); }
        for (int x = -26; x <= -22; x++) { set(x, u, 61, id("minecraft:chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_2_occupied=true,slot_4_occupied=true]")); set(x, u + 1, 61, BOOKSHELF); }
        for (int z = 64; z <= 66; z++) { ip(-24, u, z, slabTop("dark_oak")); ip(-25, u, z, stairs("spruce", "west")); ip(-23, u, z, stairs("spruce", "east")); }
        ip(-24, u + 1, 65, LANTERN); ip(-24, u + 1, 64, id("pixelpirates:map_block"));
        ip(-22, u, 74, LECTERN_N); ip(-25, u, 74, LECTERN_N); ip(-22, u, 69, BARREL_UP); ip(-22, u, 68, BARREL_UP);
        for (int x = -26; x <= -22; x++) for (int z = 68; z <= 72; z++) ip(x, u, z, carpet(((x + z) & 1) == 0 ? "brown" : "orange"));
        set(-24, HTOP - 1, 65, LANTERN_HANGING); set(-24, HTOP - 1, 71, LANTERN_HANGING);
        // ---- QUARTERS (upstairs north-east)
        set(-14, u, 61, bed("blue", "north", true)); set(-14, u, 62, bed("blue", "north", false));
        set(-13, u, 61, BARREL_UP); set(-13, u + 1, 61, candle(1));
        set(-20, u, 61, chest("south")); set(-19, u, 61, id("pixelpirates:sea_chest[facing=south]")); set(-17, u, 61, barrel("south")); set(-17, u + 1, 61, barrel("south"));
        ip(-19, u, 65, id("pixelpirates:captains_chair[facing=east]")); ip(-18, u, 65, slabTop("dark_oak")); ip(-18, u + 1, 65, id("minecraft:potted_blue_orchid"));
        for (int x = -19; x <= -14; x++) for (int z = 62; z <= 66; z++) ip(x, u, z, carpet(x == -19 || x == -14 || z == 62 || z == 66 ? "blue" : "white"));
        set(-16, HTOP - 1, 64, LANTERN_HANGING);
        // ---- SIGNAL ROOM (upstairs south-east): flag lockers, signal lamps, the chart desk, banners
        String[] fl = {"red", "yellow", "blue", "white", "black", "lime"};
        for (int x = -20; x <= -17; x++) if (ip(x, u, 68, barrel("south"))) ip(x, u + 1, 68, wool(fl[Math.floorMod(x, 6)]));
        for (int z = 68; z <= 71; z++) { set(-13, u, z, z % 2 == 0 ? id("minecraft:sea_lantern") : id("minecraft:redstone_lamp")); }
        set(-14, u, 68, slabTop("spruce")); set(-14, u + 1, 68, id("pixelpirates:map_block")); set(-15, u, 68, stairs("spruce", "west"));
        for (int i = 0; i < 3; i++) set(-20 + i, u + 2, 75, wallBanner(fl[i], "north"));
        set(-20, u, 74, id("pixelpirates:telescope[facing=south]"));
        for (int x = -20; x <= -17; x++) for (int z = 70; z <= 73; z++) ip(x, u, z, carpet(((x + z) & 1) == 0 ? "cyan" : "white"));
        set(-18, HTOP - 1, 71, LANTERN_HANGING); set(-14, HTOP - 1, 70, LANTERN_HANGING);
    }
    private static int LECTERN_S() { return id("minecraft:lectern[facing=south]"); }

    /** Warehouse interiors: storage racks (fence posts + slab shelves) in aisles, pallets, hoists. */
    /** The rum distillery, inside (see distillery()). */
    private static void furnishDistillery(Random r) {
        final int g = DG + 1, u = DF2 + 1;
        // ---- THE STILL HALL: two pot stills, three working rum stills over fires, the molasses vats, cane, pipes
        potStill(-110, 62); potStill(-104, 62);
        for (int z : new int[]{66, 69, 72}) {
            set(-100, g, z, CAMPFIRE); set(-100, g + 1, z, id("pixelpirates:rum_still[facing=west,lit=false]"));
            set(-100, g, z + 1, id("minecraft:bricks")); set(-99, g + 2, z, id("minecraft:lightning_rod[facing=east]"));
        }
        set(-101, g, 65, barrel("west")); set(-101, g + 1, 65, id("minecraft:honey_block"));
        for (int[] v : new int[][]{{-110, 76}, {-104, 76}}) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                boolean rim = dx != 0 || dz != 0;
                for (int y = g; y <= g + 1; y++) set(v[0] + dx, y, v[1] + dz, rim ? id("minecraft:stripped_spruce_log[axis=y]") : id("minecraft:honey_block"));
                if (rim) set(v[0] + dx, g + 2, v[1] + dz, slab("spruce"));
            }
            set(v[0] - 2, g, v[1], stairs("spruce", "east"));
        }
        for (int x = -101; x <= -99; x++) for (int z = 74; z <= 79; z++) if (z != 79 || x != -101) { set(x, g, z, id("minecraft:bamboo_block[axis=y]")); if ((x + z) % 2 == 0) set(x, g + 1, z, id("minecraft:bamboo_block[axis=x]")); }
        for (int x = -112; x <= -101; x++) set(x, g + 5, 59, id("minecraft:lightning_rod[facing=east]"));
        for (int z = 60; z <= 78; z++) set(-113, g + 5, z, id("minecraft:lightning_rod[facing=south]"));
        for (int[] c : new int[][]{{-107, 62}, {-107, 70}, {-107, 76}, {-102, 69}}) hangLantern(c[0], DTOP - 2, c[1]);
        // ---- THE TASTING ROOM (street side): bar along the west wall, bottles, tables, the menu
        for (int z = 59; z <= 64; z++) { set(-127, g, z, z % 2 == 0 ? id("pixelpirates:rum_rack[facing=east,bottles=6]") : barrel("east")); set(-127, g + 1, z, slabTop("dark_oak")); set(-127, g + 2, z, id("pixelpirates:spirit_bottles[facing=east,count=" + (1 + z % 4) + "]")); }
        for (int z = 60; z <= 63; z++) { set(-125, g, z, barrel("east")); set(-125, g + 1, z, slabTop("dark_oak")); }
        set(-125, g + 2, 61, id("pixelpirates:tankard[facing=east,count=2]")); set(-125, g + 2, 62, candle(3));
        for (int z = 60; z <= 63; z += 3) ip(-124, g, z, id("pixelpirates:barrel_stool[facing=west]"));
        for (int[] t : new int[][]{{-119, 61}, {-117, 63}}) {
            ip(t[0], g, t[1], id("pixelpirates:aging_cask[facing=north]")); ip(t[0], g + 1, t[1], id("pixelpirates:spirit_bottles[facing=north,count=2]"));
            ip(t[0] - 1, g, t[1], id("pixelpirates:barrel_stool[facing=east]")); ip(t[0] + 1, g, t[1], id("pixelpirates:barrel_stool[facing=west]"));
        }
        set(-115, g + 2, 61, id("pixelpirates:drinks_menu[facing=west]"));
        set(-121, DF2 - 1, 61, LANTERN_HANGING); set(-117, DF2 - 1, 60, LANTERN_HANGING);
        // ---- THE CASK HALL: rows of aging casks two high, a cross aisle at x-121, rum racks on the wall
        for (int z : new int[]{70, 73, 76, 79})
            for (int x = -125; x <= -116; x++) {
                if (x == -121) continue;
                set(x, g, z, id("pixelpirates:aging_cask[facing=east]"));
                set(x, g + 1, z, id("pixelpirates:aging_cask[facing=east]"));
            }
        for (int x = -126; x <= -116; x += 3) ip(x, g + 1, 66, id("pixelpirates:rum_rack[facing=south,bottles=" + (2 + Math.floorMod(x, 5)) + "]"));
        for (int z = 68; z <= 78; z += 5) for (int x = -123; x <= -117; x += 6) set(x, DF2 - 1, z, LANTERN_HANGING);
        // ---- UPSTAIRS: the master distiller's office + lab (north), the cask loft (south) over the cask hall
        set(-120, u, 61, id("pixelpirates:captains_desk[facing=south]")); set(-120, u, 60, id("pixelpirates:captains_chair[facing=south]"));
        set(-126, u, 59, BREWING); set(-125, u, 59, BREWING); set(-124, u, 59, CAULDRON_WATER); set(-123, u, 59, LECTERN_S());
        for (int z = 61; z <= 64; z++) { set(-127, u, z, BOOKSHELF); set(-127, u + 1, z, BOOKSHELF); }
        set(-116, u, 59, bed("brown", "north", true)); set(-116, u, 60, bed("brown", "north", false));
        set(-117, u, 59, BARREL_UP); set(-117, u + 1, 59, candle(1)); set(-115, u, 64, chest("west"));
        for (int x = -125; x <= -117; x++) for (int z = 62; z <= 64; z++) ip(x, u, z, carpet(x == -125 || x == -117 || z == 64 ? "brown" : "orange"));
        hangLantern(-121, DTOP - 2, 62);
        for (int z : new int[]{70, 74, 78})
            for (int x = -124; x <= -116; x++) {
                if (x == -121) continue;
                set(x, u, z, id("pixelpirates:aging_cask[facing=east]"));
                if ((x + z) % 3 != 0) set(x, u + 1, z, id("pixelpirates:aging_cask[facing=east]"));
            }
        for (int x = -125; x <= -116; x += 3) ip(x, u, 68, id("minecraft:decorated_pot"));
        hangLantern(-121, DTOP - 2, 72); hangLantern(-121, DTOP - 2, 76);
    }

    /** The trading company warehouse, inside (see tradingWarehouse()). */
    private static void furnishTradingWarehouse(Random r) {
        final int g = WG + 1, u = WF2 + 1;
        // ---- the racks: four two-sided aisles of three-tier shelving, one cargo per rack
        int[][] racks = {{-67, 63, 78}, {-62, 63, 78}, {-47, 67, 78}, {-42, 67, 78}};
        String[][] goods = {
                {"minecraft:orange_wool", "minecraft:red_wool", "minecraft:yellow_wool", "minecraft:brown_wool", "minecraft:barrel[facing=up]"},          // spices
                {"pixelpirates:cargo_crate[facing=east]", "minecraft:magenta_wool", "minecraft:cyan_wool", "minecraft:purple_wool", "minecraft:lime_wool"}, // tea + silk
                {"minecraft:barrel[facing=west]", "minecraft:barrel[facing=up]", "pixelpirates:spirit_bottles[facing=west,count=4]", "minecraft:barrel[facing=east]", "pixelpirates:tankard[facing=west,count=3]"}, // rum + wine
                {"pixelpirates:rope_coil[facing=west]", "minecraft:white_wool", "pixelpirates:cargo_crate[facing=west]", "pixelpirates:white_sail_canvas", "minecraft:chain[axis=y]"}}; // naval stores
        for (int i = 0; i < 4; i++) {
            int[] rk = racks[i];
            for (int x = rk[0]; x <= rk[0] + 1; x++)
                for (int z = rk[1]; z <= rk[2]; z++) {
                    if ((z - rk[1]) % 4 == 0) { fill(x, g, z, x, g + 4, z, SPRUCE_FENCE); continue; }
                    for (int t = 0; t < 3; t++) {
                        int y = g + t * 2;
                        set(x, y, z, id(goods[i][Math.floorMod(x * 3 + z * 7 + t * 5, goods[i].length)]));
                        set(x, y + 1, z, slabTop("spruce"));
                    }
                }
        }
        // section boards: a banner at each aisle head
        String[] cols = {"orange", "purple", "red", "white"};
        for (int i = 0; i < 4; i++) set(racks[i][0], g + 5, racks[i][2] + 1, wallBanner(cols[i], "south"));
        // ---- the rail track down the main aisle, buffers at the end, the weighbridge by the door
        for (int z = 62; z <= 80; z++) set(-54, g, z, id("minecraft:rail[shape=north_south]"));
        set(-54, g, 61, id("minecraft:stone_brick_wall"));
        for (int x = -57; x <= -51; x++) for (int z = 80; z <= 82; z++) if (x != -54) set(x, WG, z, id("minecraft:polished_blackstone"));
        set(-58, g, 81, LECTERN_E); set(-58, g, 80, id("minecraft:anvil[facing=east]")); set(-58, g, 82, id("minecraft:chipped_anvil[facing=east]"));
        set(-50, g, 81, id("minecraft:smithing_table"));
        // pallets + barrels waiting by the door
        for (int x = -66; x <= -60; x++) for (int z = 81; z <= 82; z++) ip(x, g, z, id("minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]"));
        for (int x = -48; x <= -40; x++) for (int z = 81; z <= 82; z++) ip(x, g, z, id("minecraft:spruce_trapdoor[facing=north,half=bottom,open=false]"));
        set(-65, g + 1, 81, BARREL_UP); set(-64, g + 1, 81, BARREL_UP); set(-63, g + 1, 82, id("pixelpirates:cargo_crate[facing=south]"));
        set(-46, g + 1, 81, id("pixelpirates:cargo_crate[facing=south]")); set(-45, g + 1, 81, id("pixelpirates:cargo_crate[facing=east]")); set(-46, g + 2, 81, id("pixelpirates:cargo_crate[facing=south]"));
        // ---- the tally office (north-east, glass walls)
        for (int x = -45; x <= -39; x++) { set(x, g, 66, DARK_OAK); set(x, g + 1, 66, id("minecraft:glass_pane[east=true,west=true]")); set(x, g + 2, 66, id("minecraft:glass_pane[east=true,west=true]")); set(x, g + 3, 66, DARK_OAK); }
        for (int z = 61; z <= 65; z++) { set(-45, g, z, DARK_OAK); set(-45, g + 1, z, id("minecraft:glass_pane[north=true,south=true]")); set(-45, g + 2, z, id("minecraft:glass_pane[north=true,south=true]")); set(-45, g + 3, z, DARK_OAK); }
        set(-45, g, 63, door("dark_oak", "west", false)); set(-45, g + 1, 63, door("dark_oak", "west", true));
        set(-41, g, 62, id("pixelpirates:captains_desk[facing=south]")); set(-41, g, 61, id("pixelpirates:captains_chair[facing=south]"));
        set(-39, g, 61, chest("west")); set(-39, g, 62, BOOKSHELF); set(-39, g + 1, 62, BOOKSHELF); set(-39, g, 63, LECTERN_W);
        set(-43, g, 61, id("minecraft:chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_1_occupied=true,slot_3_occupied=true,slot_4_occupied=true]"));
        set(-44, g, 61, id("minecraft:chiseled_bookshelf[facing=south,slot_2_occupied=true,slot_5_occupied=true]"));
        set(-42, g + 3, 64, LANTERN_HANGING);
        // ---- the freight hoist: a pallet hanging in the hatch on its chain
        for (int y = g + 4; y <= WTOP + 4; y++) set(-55, y, 67, CHAIN);
        set(-55, g + 3, 67, slabTop("spruce")); set(-54, g + 3, 67, slabTop("spruce"));
        set(-55, g + 4, 67, BARREL_UP); set(-54, g + 4, 67, id("pixelpirates:cargo_crate[facing=south]"));
        // lamps over the aisles
        for (int z = 64; z <= 78; z += 7) for (int x : new int[]{-64, -59, -54, -49, -44}) ip(x, WF2 - 1, z, LANTERN_HANGING);
        // ---- THE LOFT: grain + tobacco, crate stacks, sail bolts, the cooper's corner
        for (int x = -68; x <= -60; x++) for (int z = 61; z <= 64; z++) { set(x, u, z, (x + z) % 3 == 0 ? HAY : id("minecraft:brown_wool")); if ((x * z) % 3 == 0) set(x, u + 1, z, HAY); }
        for (int x = -50; x <= -40; x += 2) for (int z = 76; z <= 80; z += 2) { set(x, u, z, id("pixelpirates:cargo_crate[facing=south]")); if ((x + z) % 4 == 0) set(x, u + 1, z, id("pixelpirates:cargo_crate[facing=east]")); }
        for (int x = -66; x <= -61; x++) { set(x, u, 80, WHITE_WOOL); set(x, u, 81, id("pixelpirates:white_sail_canvas")); if (x % 2 == 0) set(x, u + 1, 80, WHITE_WOOL); }
        // cooper: staves (stripped logs), hoops (chain), finished barrels, a grindstone and a block to work on
        for (int z = 61; z <= 64; z++) set(-40, u, z, id("minecraft:stripped_oak_log[axis=x]"));
        set(-42, u, 63, id("minecraft:stripped_oak_wood")); set(-42, u + 1, 63, id("minecraft:chain[axis=x]"));
        set(-44, u, 61, id("minecraft:grindstone[face=floor,facing=south]"));
        for (int x = -48; x <= -45; x++) set(x, u, 61, BARREL_UP);
        set(-47, u + 1, 61, BARREL_UP);
        for (int x = -66; x <= -42; x += 8) hangLantern(x, WTOP - 1, 66);
        for (int x = -66; x <= -42; x += 8) hangLantern(x, WTOP - 1, 78);
    }
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
        // the Fish Hall furnishes itself (fishMarket)
    }

    /** Dock office: x102..116, z58..72, floor 65. Door west z65. */
    /** The dock office, inside (see dockOffice()). */
    private static void furnishDockOffice(Random r) {
        final int g = KG + 1, u = KF2 + 1;
        // ---- the public hall: the counter with two dues ledgers, the dues boards, the berth chart, benches
        for (int x = 103; x <= 115; x++) {
            if (x == 109 || x == 110) continue;
            if (x == 106 || x == 112) { set(x, g, 64, id("pixelpirates:dues_ledger[facing=north]")); continue; }
            set(x, g, 64, barrel("north")); set(x, g + 1, 64, slabTop("spruce"));
        }
        set(104, g + 2, 64, candle(2)); set(114, g + 2, 64, id("minecraft:bell[attachment=floor,facing=north]"));
        set(115, g + 1, 60, id("pixelpirates:dues_board[facing=west]")); set(103, g + 1, 60, id("pixelpirates:dues_board[facing=east]"));
        int dkWall = getRaw(116, g + 3, 60);                                                        // the berth charts, framed on the wall
        for (int z = 59; z <= 61; z++) for (int y = g + 1; y <= g + 2; y++) { set(116, y, z, dkWall); ip(115, y, z, id("pixelpirates:sea_chart[facing=west]")); }
        for (int x : new int[]{104, 105, 113, 114}) ip(x, g, 59, stairs("spruce", "north"));
        for (int x = 105; x <= 113; x++) for (int z = 60; z <= 62; z++) ip(x, g, z, carpet(x == 105 || x == 113 || z == 60 || z == 62 ? "blue" : "light_blue"));
        set(109, KF2 - 1, 61, LANTERN_HANGING); set(105, KF2 - 1, 61, LANTERN_HANGING); set(113, KF2 - 1, 61, LANTERN_HANGING);
        // ---- behind the counter: the dockmaster's desk, lockers, hawsers, keys, the strongbox
        set(105, g, 68, id("pixelpirates:captains_desk[facing=north]")); set(105, g, 69, id("pixelpirates:captains_chair[facing=north]"));
        for (int x = 103; x <= 107; x++) { set(x, g, 71, barrel("north")); if (x % 2 == 1) set(x, g + 1, 71, barrel("north")); }
        for (int x = 103; x <= 106; x++) set(x, g + 2, 65, id("minecraft:tripwire_hook[facing=south]"));
        set(112, g, 70, id("pixelpirates:rope_coil[facing=north]")); set(113, g, 70, id("pixelpirates:rope_coil[facing=north]")); set(112, g + 1, 70, id("pixelpirates:rope_coil[facing=north]"));
        set(103, g, 66, chest("east")); set(103, g, 67, LECTERN_E);
        set(107, KF2 - 1, 68, LANTERN_HANGING); set(112, KF2 - 1, 68, LANTERN_HANGING);
        // ---- upstairs: the watch room (south) and the dockmaster's quarters (north)
        set(109, u, 71, id("pixelpirates:telescope[facing=south]"));
        set(112, u, 71, slabTop("spruce")); set(112, u + 1, 71, id("pixelpirates:map_block")); set(112, u, 70, id("pixelpirates:captains_chair[facing=south]"));
        set(104, u, 71, id("minecraft:sea_lantern")); set(104, u + 1, 71, id("minecraft:iron_bars"));
        for (int x = 105; x <= 111; x++) for (int z = 67; z <= 69; z++) ip(x, u, z, carpet(((x + z) & 1) == 0 ? "blue" : "white"));
        set(107, KTOP - 1, 68, LANTERN_HANGING);
        set(114, u, 59, bed("blue", "north", true)); set(114, u, 60, bed("blue", "north", false));
        set(115, u, 59, BARREL_UP); set(115, u + 1, 59, candle(1)); set(103, u, 59, chest("south")); set(104, u, 59, id("pixelpirates:sea_chest[facing=south]"));
        set(106, u, 62, slabTop("spruce")); set(106, u + 1, 62, LANTERN); set(105, u, 62, stairs("spruce", "west"));
        for (int x = 107; x <= 113; x++) for (int z = 60; z <= 63; z++) ip(x, u, z, carpet(x == 107 || x == 113 || z == 60 || z == 63 ? "red" : "white"));
        set(110, KTOP - 1, 61, LANTERN_HANGING);
    }

    /** Chapel: x-36..-14, z-80..-56, floor 70. Door south (x-26..-25), altar at the north. */
    /** The chapel, inside (see chapel()). */
    private static void furnishChapel(Random r) {
        final int g = PG + 1;
        // ---- PEWS: the nave either side of the aisle, and the side aisles; all face the altar (north)
        for (int z = -71; z <= -61; z += 2)
            for (int[] run : new int[][]{{-28, -26}, {-24, -22}, {-32, -30}, {-20, -18}})
                for (int x = run[0]; x <= run[1]; x++)
                    set(x, g, z, id("pixelpirates:chapel_pew[facing=north,left=" + (x == run[0]) + ",right=" + (x == run[1]) + "]"));
        for (int z = -73; z <= -59; z++) set(-25, g, z, carpet("red"));
        // ---- THE CHANCEL: altar, cross, candelabras, the communion rail, pulpit, hymn boards
        set(-25, PG + 2, -78, id("pixelpirates:chapel_altar[facing=south]"));
        set(-25, PG + 3, -78, id("pixelpirates:altar_cross[facing=south]"));
        set(-27, PG + 2, -78, id("pixelpirates:candelabra[facing=south]")); set(-23, PG + 2, -78, id("pixelpirates:candelabra[facing=south]"));
        for (int x = -28; x <= -22; x++) if (x != -25) set(x, PG + 2, -75, x == -28 || x == -22 ? SPRUCE_FENCE : id("minecraft:spruce_fence[east=true,west=true]"));
        for (int x = -27; x <= -23; x++) set(x, PG + 2, -76, carpet("red"));
        set(-28, PG + 2, -77, POLISHED_ANDESITE); set(-28, PG + 3, -77, LECTERN_E); set(-28, PG + 2, -76, stairs("polished_andesite", "south"));
        set(-30, g + 3, -79, id("pixelpirates:hymn_board[facing=south]")); set(-20, g + 3, -79, id("pixelpirates:hymn_board[facing=south]"));
        set(-29, g + 3, -79, wallBanner("blue", "south")); set(-21, g + 3, -79, wallBanner("blue", "south"));
        // ---- THE APSE: a wall cross over the altar, votive candles either side, banners
        set(-25, PG + 6, -85, id("pixelpirates:wall_cross[facing=south]"));
        set(-29, PG + 2, -83, id("pixelpirates:votive_rack[facing=south]")); set(-21, PG + 2, -83, id("pixelpirates:votive_rack[facing=south]"));
        set(-27, PG + 2, -84, id("pixelpirates:candelabra[facing=south]")); set(-23, PG + 2, -84, id("pixelpirates:candelabra[facing=south]"));
        // ---- THE ORGAN (east aisle by the chancel): three courses of pipes against the wall, the console, its bench
        for (int z = -79; z <= -76; z++) {
            set(-18, g, z, id("pixelpirates:organ_pipes[facing=west,top=false]"));
            set(-18, g + 1, z, id("pixelpirates:organ_pipes[facing=west,top=false]"));
            set(-18, g + 2, z, id("pixelpirates:organ_pipes[facing=west,top=true]"));
        }
        set(-20, g, -77, id("pixelpirates:organ_console[facing=west]"));
        set(-21, g, -77, id("pixelpirates:chapel_pew[facing=east,left=true,right=true]"));
        set(-20, g, -79, id("pixelpirates:votive_rack[facing=west]"));
        // ---- by the door: the font; along the walls: memorial plaques, wall crosses between the windows
        set(-31, g, -60, id("pixelpirates:baptismal_font[facing=north]"));
        for (int z = -76; z <= -60; z += 8) { set(x1(), g + 3, z, id("pixelpirates:memorial_plaque[facing=east]")); if (z + 4 <= -60) set(-18, g + 3, z + 4, id("pixelpirates:memorial_plaque[facing=west]")); }
        for (int z = -72; z <= -64; z += 8) { set(-32, g + 3, z, id("pixelpirates:wall_cross[facing=east]")); }
        set(-25, g + 3, -59, id("pixelpirates:wall_cross[facing=north]"));
        // ---- the votive ship over the nave, chandeliers from the tie beams, lanterns on the columns
        set(-25, PG + 10, -68, id("pixelpirates:votive_ship[facing=south]"));
        for (int y = PG + 11; y < 90 && getRaw(-25, y, -68) == AIR; y++) set(-25, y, -68, CHAIN);
        chandelierRing(-25, 81, -76); chandelierRing(-25, 81, -60);
        for (int z = -74; z <= -62; z += 6) { hangLantern(-31, PG + 7, z); hangLantern(-19, PG + 7, z); }
    }
    private static int x1() { return -32; }

    /** The governor's residence, inside (see manor()). */
    private static void furnishManor(Random r) {
        final int g = MG + 1, u = MF2 + 1;
        // ---- THE ENTRANCE HALL: red carpet to the stair, busts in the corners, the chandelier, the portrait, urns
        for (int z = -65; z <= -62; z++) for (int x = 29; x <= 31; x++) ip(x, g, z, carpet(x == 30 ? "red" : "yellow"));
        for (int[] b : new int[][]{{26, -62}, {34, -62}, {26, -70}, {34, -70}}) set(b[0], g, b[1], id("pixelpirates:marble_bust[facing=" + (b[0] == 26 ? "east" : "west") + "]"));
        set(30, MCEIL - 1, -66, id("pixelpirates:crystal_chandelier[facing=north]"));
        set(30, u + 2, -78, id("pixelpirates:governor_portrait[facing=south]"));
        set(28, u, -78, id("pixelpirates:garden_urn[facing=south]")); set(32, u, -78, id("pixelpirates:garden_urn[facing=south]"));
        for (int x = 27; x <= 33; x++) for (int z = -77; z <= -73; z++) ip(x, u, z, carpet(x == 27 || x == 33 || z == -77 || z == -73 ? "yellow" : "red"));
        set(27, g + 2, -78, wallBanner("blue", "south")); set(33, g + 2, -78, wallBanner("blue", "south"));
        // ---- THE STATE DINING ROOM (west, z-78..-71): a long table, candelabras, a chandelier, sideboards
        for (int z = -77; z <= -72; z++) { set(22, g, z, slabTop("dark_oak")); set(21, g, z, stairs("dark_oak", "west")); set(23, g, z, stairs("dark_oak", "east")); }
        set(22, g + 1, -77, id("pixelpirates:candelabra[facing=north]")); set(22, g + 1, -74, id("minecraft:cake")); set(22, g + 1, -72, id("pixelpirates:candelabra[facing=north]"));
        set(22, g, -78, id("pixelpirates:captains_chair[facing=south]"));
        set(22, MF2 - 1, -75, id("pixelpirates:crystal_chandelier[facing=north]"));
        for (int z = -78; z <= -71; z += 7) { set(19, g, z, barrel("east")); set(19, g + 1, z, id("pixelpirates:spirit_bottles[facing=east,count=3]")); }
        set(19, g + 2, -75, id("pixelpirates:governor_portrait[facing=east]"));
        // ---- THE GUARDROOM (west, z-69..-62): weapon racks, a duty desk, the duty roster, bunks (guard posts for later)
        set(23, g, -69, id("pixelpirates:captains_desk[facing=south]")); set(23, g, -68, id("pixelpirates:captains_chair[facing=north]"));
        for (int z = -66; z <= -63; z++) { set(19, g, z, id("minecraft:barrel[facing=east]")); set(19, g + 1, z, id("pixelpirates:display_cannon[facing=east]")); }
        set(19, g, -62, id("minecraft:smithing_table")); set(20, g, -62, id("minecraft:grindstone[face=floor,facing=north]"));
        set(19, g + 2, -67, wallBanner("blue", "east"));
        set(22, MF2 - 1, -65, LANTERN_HANGING);
        // ---- THE BALLROOM (east): chandeliers, parquet rug, the musicians' corner, sofas along the walls, mirrors
        for (int z : new int[]{-75, -66}) set(38, MF2 - 1, z, id("pixelpirates:crystal_chandelier[facing=north]"));
        for (int x = 37; x <= 40; x++) for (int z = -77; z <= -63; z++) ip(x, g, z, carpet(((x + z) & 1) == 0 ? "white" : "light_gray"));
        set(41, g, -78, id("minecraft:note_block")); set(40, g, -78, id("minecraft:note_block")); set(41, g, -77, id("minecraft:jukebox"));
        set(39, g, -78, id("pixelpirates:organ_console[facing=south]"));
        for (int z = -75; z <= -64; z += 3) { set(41, g, z, stairs("dark_oak", "east")); set(41, g, z + 1, stairs("dark_oak", "east")); }
        set(36, g, -78, id("pixelpirates:marble_bust[facing=south]")); set(36, g, -62, id("pixelpirates:garden_urn[facing=north]"));
        // ---- THE GOVERNOR'S SUITE (upstairs west): a grand bed, wardrobes, a writing desk, sofa, the door to the terrace
        set(21, u, -78, bed("blue", "north", true)); set(21, u, -77, bed("blue", "north", false));
        set(22, u, -78, bed("blue", "north", true)); set(22, u, -77, bed("blue", "north", false));
        for (int x : new int[]{20, 23}) { set(x, u, -78, barrel("south")); set(x, u + 1, -78, id("pixelpirates:candelabra[facing=south]")); }
        for (int x : new int[]{21, 22}) set(x, u + 3, -78, wallBanner("blue", "south"));
        set(19, u, -75, chest("east")); set(19, u, -74, barrel("east")); set(19, u + 1, -74, barrel("east"));
        set(23, u, -63, id("pixelpirates:captains_desk[facing=north]")); set(23, u, -64, id("pixelpirates:captains_chair[facing=south]"));
        set(20, u, -63, stairs("dark_oak", "south")); set(21, u, -63, stairs("dark_oak", "south"));
        for (int x = 20; x <= 23; x++) for (int z = -75; z <= -66; z++) ip(x, u, z, carpet(x == 20 || x == 23 || z == -75 || z == -66 ? "yellow" : "blue"));
        set(22, MCEIL - 1, -72, id("pixelpirates:crystal_chandelier[facing=north]"));
        set(19, u + 2, -66, id("pixelpirates:governor_portrait[facing=east]"));
        // ---- THE STUDY + LIBRARY (upstairs east, z-78..-70): shelves, the map table, the governor's desk
        for (int z = -78; z <= -71; z++) { if (z == -73) continue; set(41, u, z, BOOKSHELF); set(41, u + 1, z, BOOKSHELF); set(41, u + 2, z, BOOKSHELF); }
        for (int x = 36; x <= 40; x++) { set(x, u, -78, BOOKSHELF); set(x, u + 1, -78, id("minecraft:chiseled_bookshelf[facing=south,slot_0_occupied=true,slot_2_occupied=true,slot_3_occupied=true,slot_5_occupied=true]")); }
        set(38, u, -75, id("pixelpirates:map_table[facing=south]")); set(38, u, -72, id("pixelpirates:captains_desk[facing=north]")); set(38, u, -73, id("pixelpirates:captains_chair[facing=north]"));
        set(36, u, -71, id("pixelpirates:marble_bust[facing=north]"));
        set(38, MCEIL - 1, -74, id("pixelpirates:crystal_chandelier[facing=north]"));
        // ---- THE STRONGROOM (upstairs east, z-68..-62): iron door + button; chests, gold, the treasury
        for (int z = -67; z <= -63; z++) { set(41, u, z, z % 2 == 0 ? chest("west") : barrel("west")); }
        set(37, u, -63, id("minecraft:gold_block")); set(38, u, -63, id("minecraft:gold_block")); set(37, u + 1, -63, id("minecraft:gold_block"));
        set(39, u, -67, id("pixelpirates:cargo_crate[facing=south]")); set(39, u + 1, -67, id("pixelpirates:cargo_crate[facing=east]"));
        set(38, MCEIL - 1, -65, LANTERN_HANGING);
        // ---- THE ORANGERY (west pavilion): orange trees in urns, a fountain, benches
        for (int x = 13; x <= 15; x += 2) for (int z = -74; z <= -66; z += 4) { set(x, g, z, id("pixelpirates:garden_urn[facing=north]")); }
        set(14, g, -70, id("minecraft:water_cauldron[level=3]")); set(13, g, -70, AIR); set(15, g, -70, id("pixelpirates:marble_bust[facing=west]"));
        for (int z = -75; z <= -65; z += 10) set(16, g, z, stairs("dark_oak", "east"));
        // ---- THE KITCHEN + servants' hall (east pavilion)
        for (int z = -75; z <= -65; z++) { set(48, g, z, z % 3 == 0 ? id("minecraft:smoker[facing=west]") : z % 3 == 1 ? id("minecraft:furnace[facing=west]") : slabTop("spruce")); }
        for (int x = 45; x <= 46; x++) for (int z = -73; z <= -67; z++) { set(x, g, z, slabTop("spruce")); }
        set(45, g + 1, -70, id("minecraft:cake")); set(46, g + 1, -72, id("minecraft:composter[level=7]"));
        for (int z = -73; z <= -67; z += 2) { set(44, g, z, stairs("spruce", "west")); set(47, g, z, stairs("spruce", "east")); }
        set(44, g, -75, CAULDRON_WATER); set(44, g, -65, barrel("up"));
        set(46, MF2 - 1, -70, LANTERN_HANGING);
    }

    /** The Wavebreak Watch, room by room (see guardhouse()). Everything goes through ip() - air only, door cells reserved. */
    private static void furnishGuardhouse(Random r) {
        final int g = GHG + 1, u = GHF + 1, t = GHD + 1;
        for (int z = -37; z <= -28; z++) for (int x = -31; x <= -30; x++) innReserve(x, g, z);   // the hall's walkway
        // ---- THE BRIG: cell A (the drunk tank), B, C; chains, cobwebs, a forgotten skull; the gaoler's corner
        ip(-42, g, -37, slabTop("spruce")); ip(-42, g, -36, slabTop("spruce")); ip(-41, g, -37, CAULDRON_WATER);
        ip(-41, GHF - 1, -36, CHAIN); ip(-41, GHF - 2, -36, CHAIN); ip(-42, GHF - 1, -35, id("minecraft:cobweb"));
        ip(-38, g, -37, slabTop("spruce")); ip(-37, g, -37, HAY); ip(-38, GHF - 1, -37, id("minecraft:cobweb"));
        ip(-34, g, -37, id("minecraft:skeleton_skull[rotation=6]")); ip(-35, g, -37, slabTop("spruce")); ip(-34, GHF - 1, -37, id("minecraft:cobweb"));
        ip(-42, g, -31, id("pixelpirates:captains_desk[facing=east]")); ip(-42, g + 1, -31, LANTERN);
        ip(-41, g, -31, stairs("spruce", "east"));
        ip(-42, g, -32, chest("east")); ip(-42, g, -33, barrel("east"));
        ip(-41, g + 2, -28, id("minecraft:tripwire_hook[facing=north]")); ip(-40, g + 2, -28, id("minecraft:tripwire_hook[facing=north]"));
        ip(-34, g, -28, barrel("north")); ip(-35, g, -28, slabTop("spruce")); ip(-36, g, -28, slabTop("spruce"));
        hangLantern(-38, GHF - 1, -31); hangLantern(-41, GHF - 1, -36);
        // ---- THE DUTY HALL: the sergeant's counter (x-29), the roster, wanted posters, a bench for complainants
        for (int z = -37; z <= -35; z++) { set(-29, g, z, barrel("west")); set(-29, g + 1, z, slabTop("spruce")); }
        ip(-29, g + 2, -36, id("minecraft:bell[attachment=floor,facing=west]")); ip(-29, g + 2, -35, candle(2)); ip(-29, g + 2, -37, LANTERN);
        ip(-28, g, -37, LECTERN_W); ip(-28, g, -36, stairs("spruce", "east"));
        for (int[] p : new int[][]{{-37, g + 1}, {-35, g + 1}, {-36, g + 2}, {-34, g + 2}}) ip(-32, p[1], p[0], id("pixelpirates:wanted_poster[facing=east]"));
        for (int z = -33; z <= -31; z++) ip(-32, g, z, stairs("spruce", "west"));
        for (int z = -37; z <= -29; z++) for (int x = -31; x <= -30; x++) if (getRaw(x, g, z) == AIR) set(x, g, z, carpet(z == -37 || z == -29 ? "light_blue" : "blue"));
        hangLantern(-30, GHF - 1, -35); hangLantern(-30, GHF - 1, -31);
        // ---- THE ARMOURY: racks on the north + east walls, the smith's bench, powder + shot, a cannon
        for (int x = -24; x <= -21; x++) { ip(x, g + 1, -37, id("pixelpirates:weapon_rack[facing=south]")); ip(x, g + 2, -37, id("pixelpirates:weapon_rack[facing=south]")); }
        for (int z = -37; z <= -33; z++) { ip(-19, g + 1, z, id("pixelpirates:weapon_rack[facing=west]")); }
        ip(-26, g, -28, SMITHING); ip(-25, g, -28, ANVIL_N); ip(-23, g, -28, GRINDSTONE); ip(-22, g, -28, CRAFTING); ip(-20, g, -28, barrel("north"));
        ip(-22, g, -32, id("pixelpirates:display_cannon[facing=south]"));
        for (int[] b : new int[][]{{-25, -33}, {-25, -32}, {-24, -33}, {-19, -31}, {-19, -30}, {-20, -30}}) { ip(b[0], g, b[1], GUNPOWDER_BARREL); }
        ip(-25, g + 1, -33, GUNPOWDER_BARREL);
        for (int[] c : new int[][]{{-26, -37}, {-26, -36}, {-19, -28}}) ip(c[0], g, c[1], id("pixelpirates:cargo_crate[facing=east]"));
        ip(-21, g, -32, id("minecraft:blackstone_slab")); ip(-21, g, -31, id("minecraft:blackstone_slab"));      // shot
        hangLantern(-23, GHF - 1, -34); hangLantern(-21, GHF - 1, -30);
        // ---- THE BARRACKS: bunks two high on both long walls, a locker at each foot, a card table in the aisle
        for (int x = -42; x <= -34; x += 2) {
            for (int[] s : new int[][]{{-37, -36, -35}, {-28, -29, -30}}) {
                String f = s[0] < s[1] ? "north" : "south", col = ((x / 2) & 1) == 0 ? "blue" : "light_gray";
                if (!ip(x, u, s[0], bed(col, f, true))) continue;
                set(x, u, s[1], bed(col, f, false));
                set(x, u + 1, s[0], slabTop("spruce")); set(x, u + 1, s[1], slabTop("spruce"));
                set(x, u + 2, s[0], bed(col, f, true)); set(x, u + 2, s[1], bed(col, f, false));
                ip(x, u, s[2], chest(f.equals("north") ? "south" : "north"));
            }
            hangLantern(x + 1, GHD - 1, -33);
        }
        for (int x = -40; x <= -38; x++) { ip(x, u, -33, slabTop("spruce")); ip(x, u, -32, id("pixelpirates:barrel_stool[facing=north]")); }
        ip(-39, u + 1, -33, id("pixelpirates:dice_cup[facing=north]")); ip(-40, u + 1, -33, id("pixelpirates:tankard[facing=south,count=2]"));
        ip(-41, u, -33, id("pixelpirates:barrel_stool[facing=east]")); ip(-37, u, -33, id("pixelpirates:barrel_stool[facing=west]"));
        ip(-33, u + 2, -37, wallBanner("blue", "south"));
        // ---- THE LANDING: a bench, the duty board, lanterns
        ip(-27, u, -37, stairs("spruce", "north")); ip(-28, u, -37, stairs("spruce", "north")); ip(-27, u + 2, -36, id("pixelpirates:wanted_poster[facing=west]"));
        hangLantern(-30, GHD - 1, -33);
        // ---- THE CAPTAIN OF THE WATCH: desk + chair, the governor's portrait, a map table, bookshelves, the bed, a sea chest
        ip(-22, u, -34, id("pixelpirates:captains_desk[facing=south]")); ip(-22, u + 1, -34, candle(3));
        ip(-22, u, -35, id("pixelpirates:captains_chair[facing=south]"));
        ip(-22, u + 2, -37, id("pixelpirates:governor_portrait[facing=south]"));
        ip(-24, u, -30, id("pixelpirates:map_table[facing=south]"));
        for (int z = -37; z <= -33; z++) { ip(-19, u, z, BOOKSHELF); ip(-19, u + 1, z, z == -35 ? pot(r) : BOOKSHELF); }
        ip(-19, u, -28, bed("red", "south", true)); ip(-19, u, -29, bed("red", "south", false));
        ip(-20, u, -28, id("pixelpirates:sea_chest[facing=west]"));
        ip(-25, u + 1, -31, id("pixelpirates:wanted_poster[facing=east]")); ip(-25, u + 1, -29, id("pixelpirates:wanted_poster[facing=east]"));
        rug(-24, -36, -20, -32, u, "blue", "light_blue");
        hangLantern(-22, GHD - 1, -32);
        // ---- THE MESS: the long table, the kitchen range on the north wall, stores on the south
        for (int x = -51; x <= -45; x++) {
            ip(x, g, -32, slabTop("spruce")); ip(x, g, -33, stairs("spruce", "north")); ip(x, g, -31, stairs("spruce", "south"));
            if ((x & 1) == 1) ip(x, g + 1, -32, id("pixelpirates:tankard[facing=south,count=" + (1 + (x & 3) % 3) + "]")); else ip(x, g + 1, -32, candle(1 + r.nextInt(3)));
        }
        for (int x = -51; x <= -45; x++) ip(x, g, -36, x == -48 ? id("minecraft:smoker[facing=south]") : x == -46 ? id("minecraft:furnace[facing=south]") : x == -50 ? CAULDRON_WATER : slabTop("spruce"));
        ip(-51, g + 1, -36, id("minecraft:cake")); ip(-49, g + 1, -36, id("minecraft:decorated_pot"));
        for (int x = -51; x <= -45; x += 2) hangLantern(x, GHG + 4, -32);
        for (int x = -52; x <= -50; x++) { ip(x, g, -29, barrel("north")); }
        ip(-46, g, -29, barrel("up")); ip(-45, g, -29, HAY); ip(-44, g, -29, barrel("north"));
        // ---- THE TOWER: a stool + lantern in the gate passage, the guard post, the powder store, the lookout's telescope corner
        ip(-16, g, -35, stairs("spruce", "west")); hangLantern(-14, GHF - 1, -37);
        ip(-16, u, -39, id("pixelpirates:weapon_rack[facing=east]")); ip(-15, u, -35, stairs("spruce", "south")); ip(-13, u, -35, barrel("up")); hangLantern(-14, GHD - 1, -37);
        ip(-16, t, -39, barrel("up")); ip(-16, t, -38, barrel("up")); ip(-15, t, -39, id("pixelpirates:rope_coil[facing=south]")); hangLantern(-14, 85, -36);
        ip(-16, 87, -39, GUNPOWDER_BARREL); ip(-16, 87, -35, GUNPOWDER_BARREL); ip(-15, 87, -35, id("pixelpirates:cargo_crate[facing=north]"));
        ip(-14, 87, -39, wool("blue")); ip(-13, 87, -39, wool("white")); ip(-14, 88, -39, wool("red"));          // signal flags, folded
        hangLantern(-14, 90, -36);
        // ---- THE ROOF WALK: two salute guns over the street, powder, lanterns on the parapet
        ip(-41, t, -38, id("pixelpirates:display_cannon[facing=north]")); ip(-35, t, -38, id("pixelpirates:display_cannon[facing=north]"));
        ip(-38, t, -38, GUNPOWDER_BARREL); ip(-42, t, -28, barrel("up")); ip(-42, t, -29, barrel("up"));
    }

    /** The Wavebreak Forge's loose furnishings (see smithy()); ip() = air only, door cells reserved. */
    private static void furnishSmithy(Random r) {
        final int g = SG + 1;
        // the tool wall (west): weapon racks of finished work, shelves of tongs and chain
        for (int z = -36; z <= -32; z++) { ip(48, g + 1, z, id("pixelpirates:weapon_rack[facing=east]")); ip(48, g + 2, z, id("pixelpirates:weapon_rack[facing=east]")); }
        for (int z = -28; z <= -25; z++) { ip(48, g + 2, z, shelf("east")); ip(48, g + 3, z, z % 2 == 0 ? CHAIN : pot(r)); }
        // the benches (south wall): smithing table, grindstone, bench, vice, stock
        ip(49, g, -24, SMITHING); ip(50, g, -24, GRINDSTONE); ip(51, g, -24, CRAFTING); ip(52, g, -24, barrel("north"));
        ip(53, g, -24, id("minecraft:stonecutter[facing=north]")); ip(58, g, -24, chest("north")); ip(59, g, -24, barrel("north")); ip(60, g, -24, barrel("up"));
        ip(59, g + 1, -24, id("minecraft:lantern"));
        // iron + coal stock in the middle of the floor, a stool by the forge
        ip(52, g, -30, id("minecraft:iron_block")); ip(52, g + 1, -30, id("minecraft:raw_iron_block"));
        ip(53, g, -30, id("minecraft:coal_block")); ip(53, g + 1, -30, id("minecraft:coal_block")); ip(53, g, -29, id("minecraft:coal_block"));
        ip(52, g, -29, barrel("up")); ip(54, g, -29, id("pixelpirates:cargo_crate[facing=south]"));
        ip(56, g, -32, id("pixelpirates:barrel_stool[facing=north]"));
        // the counter in the arcade, where the smith takes orders
        for (int z = -33; z <= -30; z++) { ip(63, g, z, barrel("east")); ip(63, g + 1, z, slabTop("dark_oak")); }
        ip(63, g + 2, -32, candle(2)); ip(63, g + 2, -30, id("minecraft:lantern"));
        // light: lanterns and hooks on chains from the tie beams
        for (int z = -34; z <= -26; z += 4) { hangLantern(51, SG + 8, z); hangLantern(59, SG + 8, z); }
        hangLantern(63, SG + 8, -36);
        ip(51, SG + 7, -36, CHAIN); ip(51, SG + 6, -36, id("minecraft:tripwire_hook[facing=north]"));
        // the charcoal shed: coal heaps, charcoal sacks, cordwood, raw iron
        for (int z = -36; z <= -34; z++) { ip(43, g, z, id("minecraft:coal_block")); ip(44, g, z, id("minecraft:coal_block")); if (z != -35) ip(43, g + 1, z, id("minecraft:coal_block")); }
        for (int z = -30; z <= -26; z++) { ip(43, g, z, SPRUCE_LOG_Z); ip(43, g + 1, z, SPRUCE_LOG_Z); if (z != -28) ip(44, g, z, SPRUCE_LOG_Z); }
        ip(45, g, -33, barrel("up")); ip(46, g, -33, barrel("up")); ip(45, g, -25, id("minecraft:raw_iron_block")); ip(46, g, -25, id("minecraft:raw_copper_block"));
    }

    /** Shipwright hall: x58..88, z60..82, floor 65. Slip doors south (x63-67, x77-81). */
    private static void furnishShipwrightHall(Random r) {
        int gy = 65;
        keepClear(62, 76, 68, 82);
        keepClear(76, 76, 82, 82);
        // drafting corner: tables with maps, blueprint wall, stools
        for (int x = 60; x <= 66; x += 3) { table(x, gy + 1, 72, "spruce", id("pixelpirates:map_block")); chair(x, gy + 1, 73, "spruce", "south"); }
        for (int x = 59; x <= 69; x++) put(x, gy + 3, 61, x % 2 == 0 ? id("pixelpirates:sea_chart[facing=south]") : shelf("south"));
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
            case 0 -> { put(tx, gy + 1, tz, id("pixelpirates:anchor_block[facing=east]"));
                        put(tx, gy + 1, tz + 1, CARTOGRAPHY); put(tx, gy + 2, tz + 1, id("pixelpirates:map_block")); }
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
        if (args.length > 0 && args[0].equals("map")) { labeledMap(args.length > 1 ? args[1] : "port_city_map.png"); return; }
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

    /** Labelled overview: map out.png - the top-down view at 4 px per block, every building outlined and numbered, with a
     *  legend (number, name, x/z range) down the right. North is up. */
    private static void labeledMap(String out) throws Exception {
        ensureBuilt();
        int s = 4, legendW = 360, w = (X1 - X0 + 1) * s, h = (Z1 - Z0 + 1) * s;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w + legendW, Math.max(h, 40 + LABELS.size() * 18), java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (int x = X0; x <= X1; x++)
            for (int z = Z0; z <= Z1; z++) {
                g.setColor(new java.awt.Color(columnColor(x, z)));
                g.fillRect((x - X0) * s, (z - Z0) * s, s, s);
            }
        g.setColor(new java.awt.Color(30, 26, 22));
        g.fillRect(w, 0, legendW, img.getHeight());
        g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 13));
        g.setColor(new java.awt.Color(255, 230, 160));
        g.drawString("WAVEBREAK PORT - buildings (north up)", w + 10, 20);
        int n = 0;
        java.awt.Color[] cols = {new java.awt.Color(255, 70, 70), new java.awt.Color(70, 200, 255), new java.awt.Color(255, 220, 60),
                new java.awt.Color(120, 255, 120), new java.awt.Color(255, 130, 255)};
        for (var e : LABELS.entrySet()) {
            n++;
            int[] b = e.getValue();
            java.awt.Color c = cols[n % cols.length];
            int rx = (b[0] - X0) * s, rz = (b[1] - Z0) * s, rw = (b[2] - b[0] + 1) * s, rh = (b[3] - b[1] + 1) * s;
            g.setColor(c);
            g.setStroke(new java.awt.BasicStroke(2f));
            g.drawRect(rx, rz, rw, rh);
            String num = String.valueOf(n);
            int tw = g.getFontMetrics().stringWidth(num);
            int cx = rx + rw / 2, cz = rz + rh / 2;
            g.setColor(new java.awt.Color(0, 0, 0, 190));
            g.fillRoundRect(cx - tw / 2 - 4, cz - 9, tw + 8, 17, 6, 6);
            g.setColor(c);
            g.drawString(num, cx - tw / 2, cz + 5);
            g.drawString(n + ". " + e.getKey(), w + 10, 40 + (n - 1) * 18);
            g.setColor(new java.awt.Color(170, 160, 140));
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 11));
            g.drawString("x " + b[0] + ".." + b[2] + "  z " + b[1] + ".." + b[3], w + 230, 40 + (n - 1) * 18);
            g.setFont(new java.awt.Font("SansSerif", java.awt.Font.BOLD, 13));
        }
        javax.imageio.ImageIO.write(img, "png", new java.io.File(out));
        System.out.println("Labelled map (" + LABELS.size() + " buildings) written to " + new java.io.File(out).getAbsolutePath());
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
