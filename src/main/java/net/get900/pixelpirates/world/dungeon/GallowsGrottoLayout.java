package net.get900.pixelpirates.world.dungeon;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntBinaryOperator;

/**
 * THE GALLOWS GROTTO - lair of the Chained Revenant (boss 8/10), 2026-09-29. A cave system ~180 x 240 blocks carved
 * under the Shipgrave seabed, as a deterministic block plan in coordinates RELATIVE to the site (x, z) and absolute y.
 * Pure Java (no Minecraft classes): {@link #main} renders preview PNGs; GallowsGrotto renders it chunk by chunk.
 * <pre>
 *  N (-z)
 *   HANGMAN'S ROCK   (0,-104)  a crag breaking the surface: the great gallows, and the ruined WELL-HOUSE over the Drop
 *   GALLOWS LANDING            round its foot: stilted quay, 3 piers, sunk sloop, "The Last Rope" tavern, net shack,
 *                              warehouse, Old Wick's lamp house (NPC) on the south pier-head, channel buoys, cliff stair
 *   HANGMAN'S DROP             a 9-wide shaft spiralling ~110 blocks down (or leap into the plunge pool)
 *   GIBBET GALLERY   z -100..-40  a vaulted cavern of hanging cages, split by a chasm + underground river, 2 bridges
 *   THE GAOL         z -32..8     cell blocks, the barracks (west), the warden's office + question room (east)
 *     WEEPING GARDEN (-72,-8)     the buried: grave silt, wispwood trees, headstones, a mausoleum   (west wing)
 *     OUBLIETTE      (74,-8)      a flooded pit prison                                              (east wing)
 *   HANGING CHAPEL   z 16..44     pews under nooses, a bone organ, the altar; THE HANGMAN'S GATE to the south
 *   THE GALLOWS PIT  (0,80) r30   the arena: 8 manacle anchors, 4 gibbet cages, 4 pillars, the central gallows
 *   THE HOARD        z 112..122   behind blast rubble
 * </pre>
 * Everything open is sealed in a 3-block rock shell, and nothing is carved within 5 blocks of the real terrain
 * surface ({@code terrain}) except the entrance, so the sea can never get in.
 */
public final class GallowsGrottoLayout {
    public static final int X0 = -92, X1 = 92, Z0 = -124, Z1 = 124, Y0 = -62, Y1 = 98;
    static final int NX = X1 - X0 + 1, NZ = Z1 - Z0 + 1, NY = Y1 - Y0 + 1;
    public static final int G = -40;                 // the grotto's main floor level
    public static final int PIT_FLOOR = -48, PIT_CZ = 80, PIT_R = 30;

    private final short[] grid = new short[NX * NY * NZ];
    private final BitSet cav = new BitSet(NX * NY * NZ), water = new BitSet(NX * NY * NZ), exempt = new BitSet(NX * NY * NZ);
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Short> ids = new HashMap<>();
    public final Map<Long, String> loot = new HashMap<>();
    public final Map<Long, String> spawners = new HashMap<>();
    public final List<int[]> anchors = new ArrayList<>();      // x, y, z, facing(0 N,1 E,2 S,3 W) - manacle anchors
    public final List<int[]> cages = new ArrayList<>();        // x, y, z - the inside of each arena gibbet cage
    public int[] bossSpawn;                                     // x, y, z (x on a block EDGE: he hangs between two chains)
    public final List<int[]> hangChains = new ArrayList<>();   // the gallows chain blocks he hangs from (snap on release)
    public int[] bladeStone;                                    // the Gallowbrand in the Stone under him
    public final List<int[]> lightWall = new ArrayList<>();    // lower block of each sea-lantern pair (the enrage clock)
    private final IntBinaryOperator terrain;
    private final Random rnd;

    private GallowsGrottoLayout(long seed, IntBinaryOperator terrain) {
        this.terrain = terrain;
        this.rnd = new Random(seed);
    }

    /** Build the plan. {@code terrain} gives the top of the real terrain at a RELATIVE (x, z). */
    public static GallowsGrottoLayout build(long seed, IntBinaryOperator terrain) {
        GallowsGrottoLayout L = new GallowsGrottoLayout(seed, terrain);
        L.carve();
        L.clampToTerrain();
        L.shell();
        L.fill();
        L.decorate();
        L.finishBars();
        return L;
    }

    // =====================================================================================
    // grid + palette
    // =====================================================================================
    static int idx(int x, int y, int z) { return ((x - X0) * NZ + (z - Z0)) * NY + (y - Y0); }

    static boolean in(int x, int y, int z) { return x >= X0 && x <= X1 && y >= Y0 && y <= Y1 && z >= Z0 && z <= Z1; }

    public static long key(int x, int y, int z) { return ((long) (x & 0x3FFFFF) << 42) | ((long) (z & 0x3FFFFF) << 20) | (y & 0xFFFFF); }

    short id(String block) {
        Short s = ids.get(block);
        if (s != null) return s;
        palette.add(block);
        short n = (short) palette.size();
        ids.put(block, n);
        return n;
    }

    public List<String> palette() { return palette; }

    /** Palette id + 1 at (x,y,z); 0 = leave the world alone. */
    public int get(int x, int y, int z) { return in(x, y, z) ? grid[idx(x, y, z)] : 0; }

    void set(int x, int y, int z, String block) { if (in(x, y, z)) grid[idx(x, y, z)] = id(block); }

    String at(int x, int y, int z) { int v = get(x, y, z); return v == 0 ? null : palette.get(v - 1); }

    boolean isCav(int x, int y, int z) { return in(x, y, z) && cav.get(idx(x, y, z)); }

    boolean isAirAt(int x, int y, int z) { String s = at(x, y, z); return s != null && (s.equals("minecraft:air") || s.startsWith("minecraft:cave_air")); }

    boolean solidAt(int x, int y, int z) {
        String s = at(x, y, z);
        if (s == null) return y < terrain.applyAsInt(x, z);           // untouched world: rock below the terrain surface
        return !(s.startsWith("minecraft:air") || s.startsWith("minecraft:cave_air") || s.startsWith("minecraft:water"));
    }

    // =====================================================================================
    // shapes
    // =====================================================================================
    private double noise3(double x, double y, double z) {
        return Math.sin(x * 0.21 + z * 0.13) * 0.5 + Math.sin(z * 0.27 - y * 0.19 + 1.7) * 0.35 + Math.sin(y * 0.31 + x * 0.17 + 4.2) * 0.25;
    }

    /** An ellipsoid of open space with a rough, noisy boundary; y below {@code floor} stays solid (flat floor). */
    void cavern(double cx, double cy, double cz, double rx, double ry, double rz, int floor, double wobble) {
        for (int x = (int) (cx - rx - 3); x <= cx + rx + 3; x++)
            for (int z = (int) (cz - rz - 3); z <= cz + rz + 3; z++)
                for (int y = Math.max(floor, (int) (cy - ry - 3)); y <= cy + ry + 3; y++) {
                    if (!in(x, y, z)) continue;
                    double dx = (x - cx) / rx, dy = (y - cy) / ry, dz = (z - cz) / rz;
                    double d = dx * dx + dy * dy + dz * dz;
                    if (d < 1 + noise3(x, y, z) * wobble) cav.set(idx(x, y, z));
                }
    }

    /** A tunnel of radius r (flattened floor) between two points. */
    void tunnel(double x1, double y1, double z1, double x2, double y2, double z2, double r) {
        double len = Math.sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1) + (z2 - z1) * (z2 - z1));
        for (double t = 0; t <= len; t += 0.7) {
            double f = t / len, x = x1 + (x2 - x1) * f, y = y1 + (y2 - y1) * f, z = z1 + (z2 - z1) * f;
            for (int dx = (int) -r - 1; dx <= r + 1; dx++)
                for (int dz = (int) -r - 1; dz <= r + 1; dz++)
                    for (int dy = 0; dy <= r * 1.6; dy++) {
                        double rr = dx * dx + dz * dz + (dy - r * 0.6) * (dy - r * 0.6) * 0.6;
                        if (rr <= r * r) { int X = (int) Math.round(x + dx), Y = (int) Math.round(y) + dy, Z = (int) Math.round(z + dz); if (in(X, Y, Z)) cav.set(idx(X, Y, Z)); }
                    }
        }
    }

    void box(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++) if (in(x, y, z)) cav.set(idx(x, y, z));
    }

    void waterBox(int x1, int y1, int z1, int x2, int y2, int z2) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++)
            if (in(x, y, z) && cav.get(idx(x, y, z))) water.set(idx(x, y, z));
    }

    // =====================================================================================
    // 1. the open spaces
    // =====================================================================================
    static final int ROCK_X = 0, ROCK_Z = -104, ROCK_TOP = 72, SHAFT_R = 8;
    static final int WELL_R = 5;                        // the open drop down the middle of the grand stair (-> plunge pool)

    private void carve() {
        // the Hangman's Drop: a shaft from the crag's top down to the gallery floor
        for (int x = -SHAFT_R; x <= SHAFT_R; x++) for (int z = -SHAFT_R; z <= SHAFT_R; z++)
            for (int y = G; y <= ROCK_TOP; y++) {
                if (x * x + z * z > SHAFT_R * SHAFT_R + 2) continue;
                int i = idx(ROCK_X + x, y, ROCK_Z + z);
                cav.set(i); exempt.set(i);
            }
        // GIBBET GALLERY: a long vaulted cavern (two overlapping lobes), the shaft opens into its north end
        cavern(0, G + 9, -86, 24, 18, 20, G, 0.25);
        cavern(0, G + 10, -58, 28, 20, 22, G, 0.25);
        // the chasm across it and the river at the bottom
        for (int x = -34; x <= 34; x++) for (int z = -73; z <= -63; z++) {
            double w = 5 + Math.sin(x * 0.2) * 1.5;
            if (Math.abs(z + 68) > w) continue;
            for (int y = G - 18; y < G + 2; y++) if (in(x, y, z)) cav.set(idx(x, y, z));
        }
        waterBox(-34, G - 18, -74, 34, G - 15, -62);
        // plunge pool under the shaft
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++)
            if (x * x + z * z <= 30) for (int y = G - 4; y < G; y++) { cav.set(idx(x, y, ROCK_Z + z)); water.set(idx(x, y, ROCK_Z + z)); }
        // tunnel gallery -> gaol
        tunnel(0, G, -40, 0, G, -30, 3.5);
        // THE GAOL: central hall + cell rows + two wings
        box(-3, G, -32, 3, G + 6, 8);
        for (int k = 0; k < 7; k++) {
            int z0 = -30 + k * 5;
            box(-13, G, z0, -5, G + 4, z0 + 3);
            box(5, G, z0, 13, G + 4, z0 + 3);
        }
        box(-38, G, -22, -16, G + 7, -2);             // barracks
        box(16, G, -22, 30, G + 6, -2);               // warden's office
        box(32, G, -22, 40, G + 5, -8);               // question room
        box(-15, G, -14, -4, G + 4, -10);             // passage to the barracks
        box(4, G, -14, 15, G + 4, -10);               // passage to the office
        box(31, G, -16, 31, G + 3, -14);
        // west wing: tunnel -> WEEPING GARDEN
        tunnel(-38, G, -12, -58, G - 2, -10, 3);
        cavern(-72, G + 8, -8, 18, 16, 20, G - 2, 0.3);
        // east wing: tunnel down -> OUBLIETTE (flooded)
        tunnel(40, G, -15, 60, G - 5, -10, 3);
        cavern(74, G - 2, -8, 15, 12, 16, G - 12, 0.3);
        waterBox(58, G - 12, -26, 92, G - 6, 10);
        // gaol -> chapel
        tunnel(0, G, 8, 0, G, 18, 3.5);
        // HANGING CHAPEL: a tall nave
        cavern(0, G + 9, 30, 17, 14, 15, G, 0.12);
        box(-14, G, 18, 14, G + 12, 42);
        // the Hangman's Gate and the grand stair down into the pit
        box(-4, G, 43, 4, G + 9, 49);
        for (int s = 0; s < 8; s++) box(-4, G - s - 1, 50 + s, 4, G + 9, 50 + s);
        // THE GALLOWS PIT: vertical walls to -24, then a dome
        for (int x = -PIT_R; x <= PIT_R; x++) for (int z = -PIT_R; z <= PIT_R; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d >= PIT_R) continue;
            int top = -24 + (int) Math.round(12 * Math.sqrt(Math.max(0, 1 - (d / PIT_R) * (d / PIT_R))));
            for (int y = PIT_FLOOR; y <= top; y++) cav.set(idx(x, y, PIT_CZ + z));
        }
        // the hoard, behind the south wall
        box(-6, PIT_FLOOR, 112, 6, PIT_FLOOR + 6, 121);
        box(-1, PIT_FLOOR, 109, 1, PIT_FLOOR + 2, 111);           // its doorway (sealed with blast rubble below)
    }

    // =====================================================================================
    // 2. never within 5 blocks of the real surface (except the entrance)
    // =====================================================================================
    private void clampToTerrain() {
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            int t = terrain.applyAsInt(x, z) - 5;
            for (int y = Math.max(Y0, t); y <= Y1; y++) {
                int i = idx(x, y, z);
                if (cav.get(i) && !exempt.get(i)) { cav.clear(i); water.clear(i); }
            }
        }
    }

    // =====================================================================================
    // 3. the 3-block rock shell that seals it all in
    // =====================================================================================
    private void shell() {
        BitSet shell = new BitSet(NX * NY * NZ);
        BitSet frontier = (BitSet) cav.clone();
        for (int step = 0; step < 3; step++) {
            BitSet next = new BitSet(NX * NY * NZ);
            for (int i = frontier.nextSetBit(0); i >= 0; i = frontier.nextSetBit(i + 1)) {
                int y = i % NY + Y0, rest = i / NY, z = rest % NZ + Z0, x = rest / NZ + X0;
                int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                for (int[] d : nb) {
                    int X = x + d[0], Y = y + d[1], Z = z + d[2];
                    if (!in(X, Y, Z)) continue;
                    int j = idx(X, Y, Z);
                    if (!cav.get(j) && !shell.get(j)) { shell.set(j); next.set(j); }
                }
            }
            frontier = next;
        }
        for (int i = shell.nextSetBit(0); i >= 0; i = shell.nextSetBit(i + 1)) {
            int y = i % NY + Y0, rest = i / NY, z = rest % NZ + Z0, x = rest / NZ + X0;
            grid[i] = id(rock(x, y, z));
        }
    }

    String rock(int x, int y, int z) {
        double n = noise3(x * 1.7, y * 1.7, z * 1.7);
        if (n > 0.55) return "minecraft:tuff";
        if (n < -0.6) return "minecraft:cobbled_deepslate";
        if ((x * 7 + y * 13 + z * 3) % 29 == 0) return "pixelpirates:soul_barnacle";
        return "minecraft:deepslate";
    }

    // =====================================================================================
    // 4. open space -> air / water, floors
    // =====================================================================================
    private void fill() {
        for (int i = cav.nextSetBit(0); i >= 0; i = cav.nextSetBit(i + 1)) grid[i] = id(water.get(i) ? "minecraft:water" : "minecraft:cave_air");
        // natural floors: gravel + grave silt + soul soil where a cave floor meets the shell
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) for (int y = Y0 + 1; y <= Y1; y++) {
            int i = idx(x, y, z);
            if (!cav.get(i) || water.get(i) || cav.get(idx(x, y - 1, z))) continue;
            if (grid[idx(x, y - 1, z)] == 0) continue;
            double n = noise3(x * 2.3, y, z * 2.3);
            set(x, y - 1, z, n > 0.45 ? "pixelpirates:grave_silt" : n < -0.55 ? "minecraft:soul_soil" : n < -0.1 ? "minecraft:gravel" : "minecraft:deepslate");
        }
    }

    // =====================================================================================
    // 5. everything built
    // =====================================================================================
    private void decorate() {
        hangmansRock();
        drop();
        gallery();
        gaol();
        garden();
        oubliette();
        chapel();
        pit();
        hoard();
        furnish();
        dressCaves();
    }

    // ---------------------------------------------------------------- helpers
    int floorY(int x, int z, int from, int to) {                      // first open cell above a solid one, scanning up
        for (int y = from; y <= to; y++) if (isCav(x, y, z) && !isCav(x, y - 1, z)) return y;
        return Integer.MIN_VALUE;
    }

    int ceilY(int x, int z, int from, int to) {                       // last open cell below a solid one, scanning up
        for (int y = from; y <= to; y++) if (isCav(x, y, z) && !isCav(x, y + 1, z)) return y;
        return Integer.MIN_VALUE;
    }

    void put(int x, int y, int z, String b) { if (in(x, y, z)) set(x, y, z, b); }

    void fillB(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) put(x, y, z, b);
    }

    void chest(int x, int y, int z, String facing, String table) {
        put(x, y, z, "minecraft:chest[facing=" + facing + "]");
        loot.put(key(x, y, z), table);
    }

    void barrel(int x, int y, int z, String table) {
        put(x, y, z, "minecraft:barrel[facing=up]");
        loot.put(key(x, y, z), table);
    }

    void spawner(int x, int y, int z, String entity) {
        put(x, y, z, "minecraft:spawner");
        spawners.put(key(x, y, z), entity);
    }

    static final String BARS = "minecraft:iron_bars";

    /** A hanging gibbet cage: 3x3 iron bars, `h` tall, slab floor + roof, chains up to the ceiling. Returns inside centre. */
    int[] cage(int cx, int by, int cz, int h, int chainTo, String inside) {
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            put(cx + x, by, cz + z, "minecraft:dark_oak_slab[type=top]");
            put(cx + x, by + h + 1, cz + z, "minecraft:dark_oak_slab[type=bottom]");
            for (int y = by + 1; y <= by + h; y++)
                put(cx + x, y, cz + z, (x == 0 && z == 0) ? "minecraft:cave_air" : BARS);
        }
        for (int y = by + h + 2; y <= chainTo; y++) put(cx, y, cz, "minecraft:chain[axis=y]");
        if (inside != null) put(cx, by + 1, cz, inside);
        return new int[]{cx, by + 1, cz};
    }

    // ---------------------------------------------------------------- HANGMAN'S ROCK + GALLOWS LANDING
    // A crag breaking the surface, and round its foot the rotting port that grew up to serve the gallows: a stilted quay,
    // three piers, a sunk sloop at her moorings, the tavern "The Last Rope", a net-mender's shack, a warehouse, and at the
    // south pier-head OLD WICK's lamp house. A stair cut into the cliff climbs to the plateau: the great gallows, and the
    // ruined WELL-HOUSE over the Drop - its door stands open. Channel buoys lead ships in from the south.
    static final int DECK = 63;                                        // plank level (the sea's surface is the top of y 62)
    public final Map<Long, String[]> signs = new HashMap<>();
    public final List<Object[]> npcs = new ArrayList<>();             // {int[] xyz, String entity id}

    void sign(int x, int y, int z, String state, String... lines) {
        put(x, y, z, state);
        signs.put(key(x, y, z), lines);
    }

    /** A rotten plank at deck level over water/land (not into the crag), on posts every 3 blocks. */
    void deck(int x, int z) {
        if (!in(x, DECK, z)) return;
        String s = at(x, DECK, z);
        if (s != null && !s.contains("air")) return;                  // crag / a building already here
        int r = rnd.nextInt(100);
        if (r < 9) return;                                             // a missing board
        put(x, DECK, z, r < 17 ? "pixelpirates:destroyed_planks" : r < 24 ? "minecraft:spruce_slab[type=top]"
                : ((x * 3 + z) & 3) == 0 ? "minecraft:dark_oak_planks" : "minecraft:spruce_planks");
        if (Math.floorMod(x, 3) == 0 && Math.floorMod(z, 3) == 0) post(x, z, DECK - 1);
    }

    void post(int x, int z, int top) {
        int t = terrain.applyAsInt(x, z);
        for (int y = top; y >= t && y > top - 30; y--) if (at(x, y, z) == null) put(x, y, z, "minecraft:dark_oak_log[axis=y]");
    }

    void deckBox(int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) deck(x, z);
    }

    /** Rope railing on a pier edge: fence posts, a lantern post every 6 (some long dead), nets hung between. */
    void railing(int x1, int z1, int x2, int z2) {
        int n = 0;
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++, n++) {
            if (rnd.nextInt(6) == 0) continue;                         // broken
            put(x, DECK, z, "minecraft:dark_oak_planks");
            put(x, DECK + 1, z, "minecraft:dark_oak_fence");
            if (n % 6 == 0) { put(x, DECK + 2, z, "minecraft:dark_oak_fence"); if (rnd.nextInt(3) != 0) put(x, DECK + 3, z, "minecraft:soul_lantern"); }
            else if (n % 6 == 3 && rnd.nextBoolean()) put(x, DECK + 2, z, "minecraft:cobweb");      // drying nets
        }
    }

    /** A timber hut on the deck: corner logs, plank walls with gaps, a door gap, gable roof with holes. Returns nothing. */
    void hut(int x1, int z1, int x2, int z2, int h, String doorSide, boolean roofless) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            put(x, DECK, z, ((x + z) & 1) == 0 ? "minecraft:spruce_planks" : "minecraft:dark_oak_planks");
            if (Math.floorMod(x - x1, 3) == 0 && Math.floorMod(z - z1, 3) == 0) post(x, z, DECK - 1);
            boolean wall = x == x1 || x == x2 || z == z1 || z == z2;
            boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
            for (int y = DECK + 1; y <= DECK + h; y++) {
                if (!wall) { put(x, y, z, "minecraft:air"); continue; }
                if (corner) { put(x, y, z, "minecraft:stripped_spruce_log[axis=y]"); continue; }
                boolean window = y == DECK + 2 && ((x + z) % 3 == 0);
                if (rnd.nextInt(9) == 0) put(x, y, z, "minecraft:air");                        // rotted through
                else put(x, y, z, window ? (rnd.nextBoolean() ? "minecraft:glass_pane" : "minecraft:air") : rnd.nextInt(7) == 0 ? "pixelpirates:destroyed_planks" : "minecraft:spruce_planks");
            }
        }
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        switch (doorSide) {
            case "south" -> { put(mx, DECK + 1, z2, "minecraft:air"); put(mx, DECK + 2, z2, "minecraft:air"); }
            case "north" -> { put(mx, DECK + 1, z1, "minecraft:air"); put(mx, DECK + 2, z1, "minecraft:air"); }
            case "east" -> { put(x2, DECK + 1, mz, "minecraft:air"); put(x2, DECK + 2, mz, "minecraft:air"); }
            default -> { put(x1, DECK + 1, mz, "minecraft:air"); put(x1, DECK + 2, mz, "minecraft:air"); }
        }
        if (roofless) return;
        // gable roof along x, ridge at the middle z
        int half = (z2 - z1) / 2 + 1;
        for (int k = 0; k < half; k++) for (int x = x1 - 1; x <= x2 + 1; x++) {
            if (rnd.nextInt(7) == 0) continue;                                                  // holes
            int y = DECK + h + 1 + k;
            put(x, y, z1 - 1 + k, "minecraft:dark_oak_stairs[facing=south]");
            put(x, y, z2 + 1 - k, "minecraft:dark_oak_stairs[facing=north]");
        }
        if ((z2 - z1) % 2 == 0) for (int x = x1 - 1; x <= x2 + 1; x++) if (rnd.nextInt(5) != 0) put(x, DECK + h + half, mz, "minecraft:dark_oak_slab[type=bottom]");
        for (int k = 0; k < half; k++) for (int z = z1 + k; z <= z2 - k; z++) {                // gable ends
            put(x1, DECK + h + 1 + k, z, "minecraft:spruce_planks"); put(x2, DECK + h + 1 + k, z, "minecraft:spruce_planks");
        }
    }

    private void hangmansRock() {
        int cx = ROCK_X, cz = ROCK_Z;
        // the crag: from below the seabed up to the plateau (r ~13 on top), narrowing, rough
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) {
            int t = terrain.applyAsInt(cx + x, cz + z);
            double d = Math.sqrt(x * x + z * z);
            for (int y = Math.min(t - 3, ROCK_TOP - 30); y <= ROCK_TOP; y++) {
                double f = (y - (t - 3.0)) / Math.max(1, ROCK_TOP - (t - 3.0));
                double r = 18 - 5 * f + noise3(x, y, z) * 1.6;
                if (d > r) continue;
                int i = idx(cx + x, y, cz + z);
                if (!in(cx + x, y, cz + z) || cav.get(i)) continue;
                double n = noise3(x * 2, y * 2, z * 2);
                grid[i] = id(y == ROCK_TOP ? (n > 0.3 ? "minecraft:moss_block" : n < -0.4 ? "minecraft:coarse_dirt" : "minecraft:cobblestone")
                        : n > 0.5 ? "minecraft:andesite" : n < -0.5 ? "minecraft:mossy_cobblestone" : "minecraft:stone");
                exempt.set(i);
            }
        }
        for (int i = 0; i < 50; i++) {                                                     // tufts on the plateau
            int x = rnd.nextInt(27) - 13, z = rnd.nextInt(27) - 13;
            if (x * x + z * z < 121 || !solidAt(cx + x, ROCK_TOP, cz + z) || at(cx + x, ROCK_TOP + 1, cz + z) != null) continue;
            String below = at(cx + x, ROCK_TOP, cz + z);
            if ("minecraft:moss_block".equals(below)) put(cx + x, ROCK_TOP + 1, cz + z, "minecraft:fern");
            else if ("minecraft:coarse_dirt".equals(below)) put(cx + x, ROCK_TOP + 1, cz + z, "minecraft:dead_bush");
        }

        // ================= THE WELL-HOUSE over the Drop (x/z +-10, a floor ring round the shaft; door on the south side)
        String[] WALLS = {"minecraft:deepslate_bricks", "minecraft:cracked_deepslate_bricks", "minecraft:mossy_cobblestone", "minecraft:deepslate_bricks"};
        final int H = 10;
        for (int x = -H; x <= H; x++) for (int z = -H; z <= H; z++) {
            boolean shaft = x * x + z * z <= SHAFT_R * SHAFT_R + 2;
            boolean wall = Math.abs(x) == H || Math.abs(z) == H;
            if (!shaft) put(cx + x, ROCK_TOP, cz + z, wall ? "minecraft:polished_deepslate" : ((x + z) & 1) == 0 ? "minecraft:deepslate_tiles" : "minecraft:polished_deepslate");
            for (int y = ROCK_TOP + 1; y <= ROCK_TOP + 7; y++) {
                if (wall) {
                    boolean corner = Math.abs(x) == H && Math.abs(z) == H;
                    int ruin = 7 - Math.abs(x + z) / 3;                                          // the SE corner has fallen in
                    if (!corner && y > ROCK_TOP + ruin && x + z > 5) continue;
                    boolean slit = (y == ROCK_TOP + 3 || y == ROCK_TOP + 4) && !corner && Math.floorMod(x + z, 5) == 0 && z != H;
                    put(cx + x, y, cz + z, corner ? "minecraft:polished_blackstone_bricks" : slit ? "minecraft:iron_bars" : WALLS[Math.floorMod(x * 7 + y * 3 + z, 4)]);
                } else put(cx + x, y, cz + z, "minecraft:air");        // (over the shaft too: the rock shell used to cap it here)
            }
        }
        for (int x = -2; x <= 2; x++) for (int y = ROCK_TOP + 1; y <= ROCK_TOP + 4; y++) put(cx + x, y, cz + H, "minecraft:air");   // THE DOOR
        for (int x = -3; x <= 3; x++) put(cx + x, ROCK_TOP + 5, cz + H, Math.abs(x) == 3 ? "minecraft:polished_blackstone_bricks" : "minecraft:chiseled_deepslate");
        put(cx - 3, ROCK_TOP + 4, cz + H, "minecraft:deepslate_brick_stairs[facing=west,half=top]");
        put(cx + 3, ROCK_TOP + 4, cz + H, "minecraft:deepslate_brick_stairs[facing=east,half=top]");
        put(cx, ROCK_TOP + 6, cz + H, "minecraft:skeleton_skull[rotation=0]");
        // grand columns round the rim, each with a soul lantern
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4 + Math.PI / 8;
            int x = (int) Math.round(Math.cos(a) * 9.2), z = (int) Math.round(Math.sin(a) * 9.2);
            for (int y = ROCK_TOP + 1; y <= ROCK_TOP + 6; y++) put(cx + x, y, cz + z, y == ROCK_TOP + 3 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone_bricks");
            put(cx + x, ROCK_TOP + 7, cz + z, "minecraft:soul_lantern");
        }
        // the roof: a half-collapsed slab lid, a winch beam over the shaft, the hangman's chain hanging from it
        for (int x = -H; x <= H; x++) for (int z = -H; z <= H; z++)
            if (x + z < 4 && rnd.nextInt(5) != 0 && x * x + z * z > 30) put(cx + x, ROCK_TOP + 8, cz + z, "minecraft:deepslate_tile_slab[type=bottom]");
        for (int x = -H; x <= H; x++) put(cx + x, ROCK_TOP + 8, cz, "minecraft:dark_oak_log[axis=x]");
        put(cx - 1, ROCK_TOP + 7, cz, "minecraft:dark_oak_fence"); put(cx + 1, ROCK_TOP + 7, cz, "minecraft:dark_oak_fence");
        // a rail round the shaft - open where the grand stair begins, just inside the door (south)
        for (int x = -10; x <= 10; x++) for (int z = -10; z <= 10; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d <= Math.sqrt(SHAFT_R * SHAFT_R + 2) || d > SHAFT_R + 1.25) continue;
            if (z > 0 && Math.abs(x) <= 2) continue;
            put(cx + x, ROCK_TOP + 1, cz + z, (x + z) % 3 == 0 ? "minecraft:polished_blackstone_brick_wall" : BARS);
        }
        put(cx - 3, ROCK_TOP + 1, cz + 9, "minecraft:soul_lantern"); put(cx + 3, ROCK_TOP + 1, cz + 9, "minecraft:soul_lantern");
        put(cx - 8, ROCK_TOP + 1, cz - 9, "minecraft:barrel[facing=up]"); put(cx - 7, ROCK_TOP + 1, cz - 9, "minecraft:barrel[facing=up]");
        barrel(cx + 8, ROCK_TOP + 1, cz - 9, "pixelpirates:chests/grotto_gallery");
        put(cx + 9, ROCK_TOP + 1, cz + 7, "minecraft:dark_oak_fence"); put(cx + 9, ROCK_TOP + 2, cz + 7, "minecraft:dark_oak_fence");    // the old winch
        put(cx - 9, ROCK_TOP + 2, cz + 6, "minecraft:cobweb"); put(cx + 8, ROCK_TOP + 7, cz - 8, "minecraft:cobweb");
        for (int z = -6; z <= 6; z += 6) put(cx - 9, ROCK_TOP + 1, cz + z, "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
        sign(cx, ROCK_TOP + 3, cz - H + 1, "minecraft:dark_oak_wall_sign[facing=south]", "THE DROP", "", "the hangman's way", "down");
        sign(cx + 3, ROCK_TOP + 1, cz + 8, "minecraft:dark_oak_sign[rotation=0]", "the stair", "winds down", "- or jump.", "the well is deep");
        // outside the door: two lantern pillars, skulls on pikes, the warning
        for (int sx : new int[]{-4, 4}) {
            for (int y = ROCK_TOP + 1; y <= ROCK_TOP + 3; y++) put(cx + sx, y, cz + H + 2, "minecraft:polished_blackstone_brick_wall");
            put(cx + sx, ROCK_TOP + 4, cz + H + 2, "minecraft:soul_lantern");
        }
        for (int[] p : new int[][]{{-7, H + 1}, {7, H + 2}, {-12, 3}}) {
            put(cx + p[0], ROCK_TOP + 1, cz + p[1], "minecraft:dark_oak_fence");
            put(cx + p[0], ROCK_TOP + 2, cz + p[1], "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
        }
        sign(cx + 3, ROCK_TOP + 1, cz + H + 2, "minecraft:dark_oak_sign[rotation=0]", "NEVER TREAD", "DOWN INTO", "THE DWELLING", "- W.");

        // ================= THE GREAT GALLOWS (north of the well-house): posts, crossbeam, nooses, a cage
        int gz = cz - 14;
        for (int y = ROCK_TOP + 1; y <= ROCK_TOP + 13; y++) { put(cx - 6, y, gz, "minecraft:dark_oak_log[axis=y]"); put(cx + 6, y, gz, "minecraft:dark_oak_log[axis=y]"); }
        for (int x = -7; x <= 7; x++) put(cx + x, ROCK_TOP + 14, gz, "minecraft:dark_oak_log[axis=x]");
        put(cx - 5, ROCK_TOP + 13, gz, "minecraft:dark_oak_fence"); put(cx + 5, ROCK_TOP + 13, gz, "minecraft:dark_oak_fence");
        for (int k = -3; k <= 3; k += 2) {
            int len = 4 + Math.abs(k) / 2;
            for (int y = ROCK_TOP + 13; y > ROCK_TOP + 13 - len; y--) put(cx + k, y, gz, "minecraft:chain[axis=y]");
            put(cx + k, ROCK_TOP + 13 - len, gz, k == -1 ? "minecraft:skeleton_skull" : "minecraft:tripwire_hook[facing=north]");
        }
        cage(cx - 5, ROCK_TOP + 4, gz - 2, 3, ROCK_TOP + 13, "minecraft:skeleton_skull");
        for (int y = ROCK_TOP + 9; y <= ROCK_TOP + 13; y++) put(cx - 5, y, gz - 1, "minecraft:air");
        put(cx + 7, ROCK_TOP + 15, gz, "minecraft:soul_lantern"); put(cx - 7, ROCK_TOP + 15, gz, "minecraft:soul_lantern");
        fillB(cx - 8, ROCK_TOP, gz - 2, cx + 8, ROCK_TOP, gz + 2, "minecraft:dark_oak_planks");
        for (int x = -3; x <= 3; x++) put(cx + x, ROCK_TOP, gz, "minecraft:dark_oak_trapdoor[half=top,facing=south,open=false]");   // the drop
        for (int x = -8; x <= 8; x += 4) for (int z : new int[]{gz - 2, gz + 2}) post(cx + x, z, ROCK_TOP - 1);
        for (int x = -2; x <= 2; x++) put(cx + x, ROCK_TOP + 1, gz + 3, "minecraft:dark_oak_stairs[facing=north]");        // steps up

        // ================= THE CLIFF STAIR: from the south quay (y 64) up to the well-house door (y 73), on stone piers
        for (int k = 0; k <= 9; k++) {
            int z = cz + 20 - k, y = DECK + k;
            for (int x = -2; x <= 2; x++) {
                boolean edge = Math.abs(x) == 2;
                put(cx + x, y, z, edge ? "minecraft:stone_bricks" : k == 0 ? "minecraft:stone_bricks" : k == 9 ? "minecraft:polished_deepslate" : "minecraft:stone_brick_stairs[facing=north]");
                for (int a = 1; a <= 4; a++) if (!edge && y + a <= ROCK_TOP + 4) put(cx + x, y + a, z, "minecraft:air");
                for (int yy = y - 1; yy >= DECK - 1 && !solidAt(cx + x, yy, z); yy--) put(cx + x, yy, z, (yy & 1) == 0 ? "minecraft:stone_bricks" : "minecraft:cracked_stone_bricks");
                if (k < 9) put(cx + x, y - 1, z, "minecraft:stone_bricks");
            }
            for (int sx : new int[]{-2, 2}) {
                put(cx + sx, y + 1, z, k % 3 == 1 ? "minecraft:stone_brick_wall" : "minecraft:iron_bars");
                if (k % 3 == 1) put(cx + sx, y + 2, z, "minecraft:soul_lantern");
            }
        }
        sign(cx + 3, DECK + 1, cz + 22, "minecraft:dark_oak_sign[rotation=0]", "GALLOWS", "LANDING", "", "turn back");

        // ================= THE QUAY: a stilted boardwalk round the south half of the crag
        for (int x = -26; x <= 26; x++) for (int z = -118; z <= -80; z++) {
            double d = Math.sqrt(x * x + (z - cz) * (z - cz));
            if (d >= 16.5 && d <= 22.5) deck(cx + x, z);
        }
        // SOUTH PIER + its T-head with the lamp house
        deckBox(-2, -82, 2, -60);
        deckBox(-9, -62, 9, -54);
        railing(-3, -80, -3, -63); railing(3, -80, 3, -63);
        railing(-9, -53, 9, -53);
        lampHouse(3, -61);
        for (int x : new int[]{-8, -4, 8}) { put(x, DECK + 1, -54, "minecraft:dark_oak_log[axis=y]"); put(x, DECK + 2, -54, "minecraft:dark_oak_slab[type=bottom]"); }
        for (int y = DECK; y >= DECK - 4; y--) put(-6, y, -52, "minecraft:chain[axis=y]");
        rowboat(-8, DECK - 5, -50, true);
        // EAST PIER, and the sloop that sank at her moorings
        deckBox(20, -104, 46, -101);
        railing(22, -105, 46, -105);
        for (int x = 24; x <= 46; x += 8) { put(x, DECK + 1, -100, "minecraft:dark_oak_log[axis=y]"); put(x, DECK + 2, -100, "minecraft:dark_oak_slab[type=bottom]"); }
        sunkSloop(27, -94);
        // WEST: "The Last Rope" on stilts, reached by a boardwalk off the quay
        deckBox(-26, -101, -20, -98);
        hut(-37, -104, -27, -94, 4, "east", false);
        lastRope(-37, -104, -27, -94);
        // NORTH-EAST: the net-mender's shack and his racks of rotten net
        deckBox(14, -124, 27, -115);
        hut(16, -123, 22, -118, 3, "south", false);
        put(17, DECK + 1, -122, "minecraft:barrel[facing=up]"); barrel(21, DECK + 1, -122, "pixelpirates:chests/grotto_gallery");
        put(19, DECK + 1, -120, "minecraft:spruce_trapdoor[half=bottom,facing=north,open=false]"); put(17, DECK + 1, -119, "minecraft:cauldron");
        for (int x = 15; x <= 25; x += 2) { put(x, DECK + 1, -116, "minecraft:dark_oak_fence"); put(x, DECK + 2, -116, "minecraft:dark_oak_fence"); if (x < 25) { put(x + 1, DECK + 2, -116, "minecraft:cobweb"); put(x + 1, DECK + 1, -116, rnd.nextBoolean() ? "minecraft:cobweb" : "minecraft:air"); } }
        // SOUTH-WEST: the warehouse - roof gone, walls half down, crates everywhere
        deckBox(-29, -89, -16, -77);
        hut(-28, -88, -18, -80, 4, "north", true);
        for (int x = -27; x <= -19; x++) for (int z = -87; z <= -81; z++) {
            if (rnd.nextInt(3) != 0) continue;
            put(x, DECK + 1, z, rnd.nextBoolean() ? "minecraft:barrel[facing=" + (rnd.nextBoolean() ? "up" : "east") + "]" : "minecraft:composter");
            if (rnd.nextInt(3) == 0) put(x, DECK + 2, z, "minecraft:barrel[facing=up]");
        }
        barrel(-20, DECK + 1, -82, "pixelpirates:chests/gaol_barracks");
        for (int x = -28; x <= -18; x++) if (rnd.nextBoolean()) put(x, DECK + 4, -88 + rnd.nextInt(2), "minecraft:air");
        // clutter on the quay: overturned boats, lobster pots, a crane
        for (int[] b : new int[][]{{-16, -94}, {15, -92}}) rowboat(b[0], DECK + 1, b[1], false);
        for (int[] p : new int[][]{{9, -86}, {-8, -85}, {19, -100}, {-20, -104}}) put(p[0], DECK + 1, p[1], rnd.nextBoolean() ? "minecraft:composter" : "minecraft:barrel[facing=up]");
        for (int y = DECK + 1; y <= DECK + 7; y++) put(19, y, -94, "minecraft:stripped_dark_oak_log[axis=y]");         // cargo crane
        for (int x = 19; x <= 23; x++) put(x, DECK + 8, -94, "minecraft:dark_oak_log[axis=x]");
        for (int y = DECK + 7; y >= DECK + 3; y--) put(23, y, -94, "minecraft:chain[axis=y]");
        put(23, DECK + 2, -94, "minecraft:barrel[facing=up]");
        // CHANNEL BUOYS: pairs of soul-lantern posts leading ships in from the south
        for (int z = -44; z <= 8; z += 13) for (int sx : new int[]{-7, 7}) {
            int x = sx + (z % 2 == 0 ? 1 : -1), t = terrain.applyAsInt(x, z);
            if (t >= DECK - 1 || DECK - t > 14) continue;
            for (int y = t; y <= DECK + 1; y++) put(x, y, z, y <= DECK - 1 ? "minecraft:dark_oak_log[axis=y]" : "minecraft:dark_oak_fence");
            if (rnd.nextInt(4) != 0) put(x, DECK + 2, z, "minecraft:soul_lantern");
        }
        // wrecks impaled on the crag's flanks
        for (int[] w : new int[][]{{-17, -8}, {16, -12}}) {
            int wx = cx + w[0], wz = cz + w[1];
            int t = terrain.applyAsInt(wx, wz);
            for (int k = 0; k < 7; k++) {
                int y = t + 2 + k;
                put(wx + (w[0] < 0 ? -k / 2 : k / 2), y, wz, "minecraft:dark_oak_planks");
                put(wx + (w[0] < 0 ? -k / 2 : k / 2), y, wz + 1, k % 2 == 0 ? "minecraft:dark_oak_planks" : "pixelpirates:destroyed_planks");
            }
        }
    }

    /** OLD WICK's lamp house on the south pier-head: a stilted timber tower, lamp gallery on top, his cot inside. */
    void lampHouse(int x1, int z1) {
        int x2 = x1 + 5, z2 = z1 + 5;
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            put(x, DECK, z, "minecraft:spruce_planks");
            boolean wall = x == x1 || x == x2 || z == z1 || z == z2, corner = (x == x1 || x == x2) && (z == z1 || z == z2);
            for (int y = DECK + 1; y <= DECK + 10; y++) {
                if (!wall) { put(x, y, z, y == DECK + 5 ? "minecraft:spruce_planks" : "minecraft:air"); continue; }
                boolean window = (y == DECK + 3 || y == DECK + 7) && !corner && (x == x1 + 2 || z == z1 + 2);
                put(x, y, z, corner ? "minecraft:stripped_spruce_log[axis=y]" : window ? "minecraft:glass_pane" : y % 5 == 0 ? "minecraft:dark_oak_planks" : "minecraft:spruce_planks");
            }
            if (Math.floorMod(x - x1, 5) == 0 && Math.floorMod(z - z1, 5) == 0) post(x, z, DECK - 1);
        }
        put(x1, DECK + 1, z1 + 2, "minecraft:air"); put(x1, DECK + 2, z1 + 2, "minecraft:air");                 // door (west, onto the pier)
        for (int y = DECK + 1; y <= DECK + 10; y++) put(x2 - 1, y, z2 - 1, "minecraft:ladder[facing=north]");
        put(x2 - 1, DECK + 5, z2 - 1, "minecraft:ladder[facing=north]");
        // ground floor: his cot, a stove, oil barrels, candles
        put(x1 + 1, DECK + 1, z2 - 1, "minecraft:brown_carpet"); put(x1 + 2, DECK + 1, z2 - 1, "minecraft:brown_carpet");
        put(x1 + 1, DECK + 1, z1 + 1, "minecraft:smoker[facing=south]");
        put(x1 + 2, DECK + 1, z1 + 1, "minecraft:barrel[facing=up]"); put(x1 + 3, DECK + 1, z1 + 1, "minecraft:barrel[facing=up]");
        put(x1 + 2, DECK + 2, z1 + 1, "minecraft:candle[candles=3,lit=true]");
        put(x1 + 3, DECK + 4, z1 + 3, "minecraft:soul_lantern[hanging=true]");
        // upper floor: the lamp store
        barrel(x1 + 1, DECK + 6, z1 + 1, "pixelpirates:chests/grotto_gallery");
        put(x1 + 2, DECK + 6, z1 + 1, "minecraft:barrel[facing=up]"); put(x1 + 1, DECK + 6, z1 + 2, "minecraft:lectern[facing=east]");
        put(x1 + 3, DECK + 9, z1 + 3, "minecraft:soul_lantern[hanging=true]");
        // the lamp gallery: railed platform, a soul brazier and lanterns, a little roof
        for (int x = x1 - 1; x <= x2 + 1; x++) for (int z = z1 - 1; z <= z2 + 1; z++) {
            put(x, DECK + 11, z, "minecraft:dark_oak_planks");
            boolean edge = x == x1 - 1 || x == x2 + 1 || z == z1 - 1 || z == z2 + 1;
            put(x, DECK + 12, z, edge ? "minecraft:dark_oak_fence" : "minecraft:air");
            for (int y = DECK + 13; y <= DECK + 14; y++) put(x, y, z, "minecraft:air");
        }
        put(x2 - 1, DECK + 11, z2 - 1, "minecraft:spruce_trapdoor[half=top,facing=north,open=true]");
        put(x1 + 2, DECK + 12, z1 + 2, "minecraft:soul_campfire[lit=true]");
        for (int[] c : new int[][]{{x1 - 1, z1 - 1}, {x2 + 1, z1 - 1}, {x1 - 1, z2 + 1}, {x2 + 1, z2 + 1}}) {
            put(c[0], DECK + 13, c[1], "minecraft:dark_oak_fence"); put(c[0], DECK + 14, c[1], "minecraft:soul_lantern");
        }
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) put(x, DECK + 15, z, "minecraft:dark_oak_slab[type=bottom]");
        put(x1 + 2, DECK + 16, z1 + 2, "minecraft:lightning_rod");
        sign(x1 - 1, DECK + 3, z1 + 1, "minecraft:dark_oak_wall_sign[facing=west]", "LAMP HOUSE", "", "keep them lit", "");
        npcs.add(new Object[]{new int[]{x1 - 2, DECK + 1, z1 + 3}, "pixelpirates:lamplighter"});
    }

    /** "The Last Rope" - the landing's tavern: bar, tables, a noose over the hearth, its sign swinging over the door. */
    void lastRope(int x1, int z1, int x2, int z2) {
        int mz = (z1 + z2) / 2;
        for (int z = z1 + 2; z <= z2 - 2; z++) put(x2 - 3, DECK + 1, z, "minecraft:spruce_stairs[facing=east,half=top]");      // the bar
        put(x2 - 3, DECK + 2, z1 + 3, "minecraft:candle[candles=2,lit=true]");
        for (int z = z1 + 1; z <= z2 - 1; z += 2) put(x2 - 1, DECK + 1, z, "minecraft:barrel[facing=west]");
        for (int[] t : new int[][]{{x1 + 2, z1 + 2}, {x1 + 2, z2 - 3}, {x1 + 5, mz}}) {
            put(t[0], DECK + 1, t[1], "minecraft:dark_oak_fence"); put(t[0], DECK + 2, t[1], "minecraft:dark_oak_pressure_plate");
            put(t[0] + 1, DECK + 1, t[1], "minecraft:spruce_stairs[facing=west]");
            if (rnd.nextBoolean()) put(t[0], DECK + 1, t[1] + 1, "minecraft:spruce_stairs[facing=north,half=top]");    // overturned
        }
        put(x1 + 1, DECK + 1, mz, "minecraft:campfire[lit=false]");
        for (int y = DECK + 2; y <= DECK + 4; y++) put(x1 + 1, y, mz, y == DECK + 4 ? "minecraft:chain[axis=y]" : "minecraft:air");
        put(x1 + 1, DECK + 3, mz, "minecraft:tripwire_hook[facing=east]");
        put(x1 + 4, DECK + 4, mz, "minecraft:lantern[hanging=true]");
        put(x1 + 1, DECK + 4, z1 + 1, "minecraft:cobweb"); put(x2 - 1, DECK + 4, z2 - 1, "minecraft:cobweb");
        barrel(x2 - 1, DECK + 1, z1 + 2, "pixelpirates:chests/grotto_gallery");
        put(x1 + 3, DECK + 1, z2 - 1, "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
        sign(x2 + 1, DECK + 4, mz, "minecraft:dark_oak_wall_sign[facing=east]", "THE LAST ROPE", "", "grog - beds -", "last rites");
    }

    /** A small boat: right way up and sunk to the gunwales (water), or overturned on the quay. */
    void rowboat(int x, int y, int z, boolean sunk) {
        for (int k = -2; k <= 2; k++) {
            int w = Math.abs(k) == 2 ? 0 : 1;
            for (int s = -w; s <= w; s++) {
                if (sunk) {
                    put(x + k, y, z + s, "minecraft:spruce_planks");
                    if (Math.abs(s) == w && w > 0 || Math.abs(k) == 2) put(x + k, y + 1, z + s, "minecraft:spruce_slab[type=bottom,waterlogged=true]");
                } else put(x + k, y, z + s, Math.abs(k) == 2 ? "minecraft:spruce_slab[type=bottom]" : "minecraft:spruce_stairs[facing=" + (s < 0 ? "north" : s > 0 ? "south" : "east") + ",half=top]");
            }
        }
    }

    /** The sloop that sank at her moorings off the east pier: hull settled on the bottom, deck awash, mast snapped. */
    void sunkSloop(int x0, int z0) {
        int t = terrain.applyAsInt(x0 + 8, z0 + 2);
        int keel = Math.max(t, DECK - 9), deckY = Math.min(keel + 5, DECK - 1);
        for (int k = 0; k <= 16; k++) {
            double f = k / 16.0;
            int half = (int) Math.round(3 * Math.sin(Math.PI * Math.min(1, f * 1.15)));                  // bow pinched, stern full
            int tilt = k / 6;                                                                            // she lies stern-down
            for (int s = -half; s <= half; s++) {
                int x = x0 + k, z = z0 + s;
                for (int y = keel - tilt; y <= deckY - tilt; y++) {
                    boolean shell = Math.abs(s) == half || y == keel - tilt;
                    if (shell) put(x, y, z, rnd.nextInt(6) == 0 ? "minecraft:water" : (y + k) % 3 == 0 ? "pixelpirates:destroyed_planks" : "minecraft:dark_oak_planks");
                    else put(x, y, z, "minecraft:water");
                }
                if (Math.abs(s) < half && rnd.nextInt(3) != 0) put(x, deckY - tilt, z, "minecraft:spruce_slab[type=bottom,waterlogged=true]");
            }
        }
        // the snapped mast, leaning over the pier, a tangle of rotten sail
        for (int i = 0; i < 12; i++) {
            int y = deckY - 1 + i;
            if (y > DECK + 7) break;
            put(x0 + 7 + i / 3, y, z0 - i / 4, y <= DECK - 1 ? "minecraft:spruce_log[axis=y]" : "minecraft:spruce_log[axis=y]");
        }
        put(x0 + 10, DECK + 5, z0 - 3, "minecraft:white_wool"); put(x0 + 11, DECK + 4, z0 - 3, "minecraft:cobweb"); put(x0 + 10, DECK + 4, z0 - 2, "minecraft:white_carpet");
        barrel(x0 + 12, deckY - 2, z0, "pixelpirates:chests/grotto_gallery");
    }

    // ---------------------------------------------------------------- HANGMAN'S DROP: THE GRAND STAIR
    // A three-wide spiral stair of deepslate round the shaft wall (r 5..8), falling 18 blocks per three-quarter turn, then
    // a flat landing with an alcove carved into the wall; a balustrade with gaps on the inner edge; the open well in the
    // middle (r < 5) still drops straight into the plunge pool, so you can jump from any landing. Soul lanterns in the
    // wall, the hangman's chain down the middle, banners, and something grim in every alcove.
    static final int REV = 18;

    /** Stair height below the top at total angle t (radians walked down from the door). */
    static int stairDrop(double t) {
        int n = (int) Math.floor(t / (2 * Math.PI));
        double f = t - n * 2 * Math.PI;
        return n * REV + (f < 1.5 * Math.PI ? (int) Math.round(f / (1.5 * Math.PI) * REV) : REV);
    }

    private void drop() {
        int cx = ROCK_X, cz = ROCK_Z, y0 = ROCK_TOP - 1;
        double a0 = Math.PI / 2;                                                          // the stair starts south, inside the door
        String[] FACES = {"east", "south", "west", "north"};
        for (int x = -SHAFT_R; x <= SHAFT_R; x++) for (int z = -SHAFT_R; z <= SHAFT_R; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d < WELL_R - 0.4 || x * x + z * z > SHAFT_R * SHAFT_R + 2) continue;
            double th = Math.atan2(z, x), phi = ((th - a0) % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI);
            boolean inner = d < WELL_R + 0.6;
            for (int n = 0; n < 10; n++) {
                double t = phi + n * 2 * Math.PI;
                int y = y0 - stairDrop(t);
                if (y < G) break;
                double f = phi;
                boolean landing = f >= 1.5 * Math.PI;
                // ascending = back toward smaller angle: direction (sin th, -cos th)
                double ax = Math.sin(th), az = -Math.cos(th);
                String face = Math.abs(ax) > Math.abs(az) ? (ax > 0 ? "east" : "west") : (az > 0 ? "south" : "north");
                boolean flatHere = landing || stairDrop(t) == stairDrop(t + 0.18);
                put(cx + x, y, cz + z, landing ? (((x + z) & 1) == 0 ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles")
                        : flatHere ? "minecraft:polished_deepslate" : "minecraft:polished_deepslate_stairs[facing=" + face + "]");
                for (int k = 1; k <= 2; k++) if (isAirAt(cx + x, y - k, cz + z)) put(cx + x, y - k, cz + z, k == 1 ? "minecraft:deepslate_bricks" : "minecraft:deepslate_tile_slab[type=top]");
                if (inner) {                                                               // the balustrade, with leaps in it
                    int seg = (int) Math.floor(t * 6);
                    if (landing) { if ((seg & 3) == 0) put(cx + x, y + 1, cz + z, "minecraft:polished_blackstone_brick_wall"); }
                    else if (seg % 5 == 0) { put(cx + x, y + 1, cz + z, "minecraft:polished_blackstone_brick_wall"); put(cx + x, y + 2, cz + z, "minecraft:soul_lantern"); }
                    else if (seg % 5 != 3) put(cx + x, y + 1, cz + z, "minecraft:polished_blackstone_brick_wall");
                }
            }
        }
        // soul lanterns set into the shaft wall along the stair, and black banners
        for (int n = 0; n < 10; n++) for (int k = 0; k < 12; k++) {
            double t = n * 2 * Math.PI + k * Math.PI / 6 + 0.2;
            int y = y0 - stairDrop(t) + 3;
            if (y < G + 2) break;
            double th = a0 + t;
            int x = (int) Math.round(Math.cos(th) * (SHAFT_R + 1)), z = (int) Math.round(Math.sin(th) * (SHAFT_R + 1));
            if (at(cx + x, y, cz + z) == null || isAirAt(cx + x, y, cz + z)) continue;
            put(cx + x, y, cz + z, k % 3 == 0 ? "minecraft:soul_lantern" : k % 3 == 1 ? "pixelpirates:soul_barnacle" : "minecraft:chiseled_deepslate");
        }
        // the landings: an alcove in the wall off each one, each with its own horror
        for (int n = 0; n < 10; n++) {
            double t = n * 2 * Math.PI + 1.75 * Math.PI;
            int y = y0 - stairDrop(t);
            if (y < G + 4) break;
            double th = a0 + t, ux = Math.cos(th), uz = Math.sin(th), vx = -uz, vz = ux;
            for (double r = SHAFT_R - 0.5; r <= SHAFT_R + 3.5; r += 0.5) for (int w = -2; w <= 2; w++) {
                int x = (int) Math.round(ux * r + vx * w), z = (int) Math.round(uz * r + vz * w);
                if (x * x + z * z <= SHAFT_R * SHAFT_R + 2) continue;
                put(cx + x, y, cz + z, "minecraft:polished_deepslate");
                for (int h = 1; h <= 4; h++) put(cx + x, y + h, cz + z, Math.abs(w) == 2 && h <= 3 ? "minecraft:deepslate_tiles" : "minecraft:cave_air");
                put(cx + x, y + 5, cz + z, "minecraft:polished_blackstone_bricks");
            }
            int bx = cx + (int) Math.round(ux * (SHAFT_R + 2.5)), bz = cz + (int) Math.round(uz * (SHAFT_R + 2.5));
            switch (n % 5) {
                case 0 -> {                                                                 // a hanged man
                    for (int h = 4; h >= 3; h--) put(bx, y + h, bz, "minecraft:chain[axis=y]");
                    put(bx, y + 2, bz, "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
                    put(bx, y + 1, bz, "minecraft:cobweb");
                }
                case 1 -> {                                                                 // a shrine of candles
                    put(bx, y + 1, bz, "minecraft:polished_blackstone_bricks"); put(bx, y + 2, bz, "minecraft:skeleton_skull");
                    put(bx + 1, y + 1, bz, "minecraft:candle[candles=4,lit=true]"); put(bx - 1, y + 1, bz, "minecraft:candle[candles=3,lit=true]");
                }
                case 2 -> {                                                                 // a gibbet cage in the wall
                    put(bx, y + 1, bz, BARS); put(bx, y + 2, bz, BARS); put(bx, y + 3, bz, "minecraft:dark_oak_slab[type=bottom]");
                    put(bx, y + 1, bz + 1, "minecraft:skeleton_skull");
                }
                case 3 -> {                                                                 // a resting place: bench, barrel
                    put(bx, y + 1, bz, "minecraft:dark_oak_stairs[facing=north]"); barrel(bx + 1, y + 1, bz, "pixelpirates:chests/grotto_gallery");
                    put(bx - 1, y + 1, bz, "minecraft:soul_lantern");
                }
                default -> put(bx, y + 3, bz, "minecraft:soul_lantern[hanging=true]");
            }
            if (n == 2) sign(bx, y + 1, bz - 1, "minecraft:dark_oak_sign[rotation=0]", "halfway", "down", "", "no one climbs back");
        }
        // the hangman's chain down the middle, with a lantern every twenty blocks, ending over the pool
        for (int yy = ROCK_TOP + 7; yy > G + 2; yy--) put(cx, yy, cz, (ROCK_TOP - yy) % 20 == 10 ? "minecraft:soul_lantern[hanging=true]" : "minecraft:chain[axis=y]");
        put(cx, G + 2, cz, "minecraft:soul_lantern[hanging=true]");
        // the plunge pool: a rim of stone round the water at the bottom
        for (int x = -7; x <= 7; x++) for (int z = -7; z <= 7; z++) {
            int r2 = x * x + z * z;
            if (r2 > 30 && r2 <= 42 && isAirAt(cx + x, G, cz + z)) put(cx + x, G, cz + z, "minecraft:polished_blackstone_brick_slab[type=bottom]");
        }
    }

    // ---------------------------------------------------------------- GIBBET GALLERY
    private void gallery() {
        // bridges over the chasm
        for (int bx : new int[]{-12, 12})
            for (int z = -76; z <= -60; z++) for (int x = bx - 1; x <= bx + 1; x++) {
                if (isCav(x, G - 1, z)) put(x, G - 1, z, z % 3 == 0 ? "minecraft:dark_oak_planks" : "minecraft:spruce_slab[type=top]");
                if (x != bx && isCav(x, G, z) && z % 2 == 0) put(x, G, z, "minecraft:chain[axis=y]");
            }
        // soul fire glowing in the river below
        for (int i = 0; i < 20; i++) {
            int x = rnd.nextInt(60) - 30, z = -68 + rnd.nextInt(7) - 3;
            if (isCav(x, G - 14, z)) put(x, G - 14, z, "minecraft:sea_pickle[pickles=4,waterlogged=false]");
        }
        // a forest of hanging cages
        for (int i = 0; i < 70; i++) {
            int x = rnd.nextInt(52) - 26, z = -100 + rnd.nextInt(60);
            if (Math.abs(z + 68) < 8 && rnd.nextBoolean()) continue;
            int c = ceilY(x, z, G + 6, G + 32);
            if (c == Integer.MIN_VALUE || c < G + 12) continue;
            if (!isCav(x - 1, c, z) || !isCav(x + 1, c, z) || !isCav(x, c, z - 1) || !isCav(x, c, z + 1)) continue;
            int by = c - 5 - rnd.nextInt(Math.max(1, c - G - 11));
            if (by < G + 4) continue;
            String in = rnd.nextInt(4) == 0 ? "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]" : rnd.nextInt(6) == 0 ? "LOOT" : null;
            int[] ins = cage(x, by, z, 3, c, "LOOT".equals(in) ? null : in);
            if ("LOOT".equals(in)) barrel(ins[0], ins[1], ins[2], "pixelpirates:chests/grotto_gallery");
            i += 0;
        }
        // hanging soul lanterns and a few camp braziers
        for (int i = 0; i < 26; i++) {
            int x = rnd.nextInt(54) - 27, z = -100 + rnd.nextInt(60);
            int c = ceilY(x, z, G + 4, G + 32);
            if (c == Integer.MIN_VALUE) continue;
            int len = 2 + rnd.nextInt(5);
            for (int k = 0; k < len; k++) put(x, c - k, z, "minecraft:chain[axis=y]");
            put(x, c - len, z, "minecraft:soul_lantern[hanging=true]");
        }
        for (int[] p : new int[][]{{-8, -90}, {9, -84}, {-14, -50}, {15, -46}, {0, -44}}) {
            int f = floorY(p[0], p[1], G - 2, G + 4);
            if (f != Integer.MIN_VALUE) { put(p[0], f, p[1], "minecraft:soul_campfire[lit=true]"); }
        }
        spawner(-18, G, -88, "pixelpirates:skeleton_pirate");
        spawner(18, G, -48, "pixelpirates:skeleton_pirate");
        chest(-20, G, -46, "east", "pixelpirates:chests/grotto_gallery");
    }

    // ---------------------------------------------------------------- THE GAOL
    private void gaol() {
        String WALL = "minecraft:deepslate_bricks", CRACK = "minecraft:cracked_deepslate_bricks", TILE = "minecraft:deepslate_tiles";
        // dress the hall: tiled floor, brick walls around every carved gaol room
        for (int x = -42; x <= 42; x++) for (int z = -34; z <= 10; z++) for (int y = G - 1; y <= G + 8; y++) {
            if (!in(x, y, z)) continue;
            String s = at(x, y, z);
            if (s == null || s.contains("air") || s.contains("water")) continue;
            boolean nearGaol = isCav(x + 1, y, z) || isCav(x - 1, y, z) || isCav(x, y, z + 1) || isCav(x, y, z - 1) || isCav(x, y + 1, z) || isCav(x, y - 1, z);
            if (!nearGaol) continue;
            if (y == G - 1) put(x, y, z, ((x + z) & 1) == 0 ? TILE : "minecraft:polished_deepslate");
            else put(x, y, z, rnd.nextInt(6) == 0 ? CRACK : WALL);
        }
        // cells: iron-bar fronts (a gap for a door), a prisoner's lot inside
        for (int k = 0; k < 7; k++) {
            int z0 = -30 + k * 5;
            for (int side : new int[]{-1, 1}) {
                int fx = side < 0 ? -4 : 4;
                for (int z = z0; z <= z0 + 3; z++) for (int y = G; y <= G + 4; y++) put(fx, y, z, (z == z0 + 1 && y <= G + 1) ? "minecraft:cave_air" : BARS);
                for (int y = G; y <= G + 4; y++) { put(fx, y, z0 - 1, WALL); put(fx, y, z0 + 4, WALL); }
                int ix = side < 0 ? -11 : 11;
                switch ((k + (side < 0 ? 0 : 3)) % 5) {
                    case 0 -> { put(ix, G, z0 + 1, "minecraft:bone_block"); put(ix, G + 1, z0 + 1, "minecraft:skeleton_skull[rotation=4]"); }
                    case 1 -> { put(ix, G, z0 + 2, "minecraft:white_carpet"); put(ix + side, G, z0 + 2, "minecraft:white_carpet"); barrel(ix, G, z0, "pixelpirates:chests/grotto_gallery"); }
                    case 2 -> { for (int y = G + 1; y <= G + 3; y++) put(ix + side, y, z0 + 1, "minecraft:chain[axis=y]"); put(ix + side, G, z0 + 1, "minecraft:skeleton_skull"); }
                    case 3 -> { put(ix, G + 3, z0 + 1, "minecraft:cobweb"); put(ix - side, G + 3, z0 + 2, "minecraft:cobweb"); put(ix, G, z0 + 2, "minecraft:cauldron"); }
                    default -> put(ix, G + 4, z0 + 1, "minecraft:soul_lantern[hanging=true]");
                }
            }
        }
        spawner(-9, G, -3, "pixelpirates:skeleton_pirate");
        spawner(9, G, -23, "pixelpirates:skeleton_pirate");
        // hall lanterns + the gaol gate
        for (int z = -30; z <= 6; z += 6) put(0, G + 6, z, "minecraft:soul_lantern[hanging=true]");
        for (int x = -3; x <= 3; x++) for (int y = G + 4; y <= G + 6; y++) put(x, y, -33, BARS);                     // raised portcullis
        // BARRACKS (west): bunks, tables, racks
        for (int z = -21; z <= -4; z += 4) {
            fillB(-37, G, z, -34, G, z, "minecraft:spruce_slab[type=bottom]");
            fillB(-37, G + 2, z, -34, G + 2, z, "minecraft:spruce_slab[type=bottom]");
            put(-37, G + 1, z, "minecraft:spruce_fence"); put(-34, G + 1, z, "minecraft:spruce_fence");
            put(-36, G + 1, z, "minecraft:brown_carpet");
        }
        for (int x = -28; x <= -22; x++) { put(x, G, -13, "minecraft:spruce_fence"); put(x, G + 1, -13, "minecraft:spruce_pressure_plate"); }
        for (int x = -28; x <= -22; x += 2) { put(x, G, -12, "minecraft:spruce_stairs[facing=north]"); put(x, G, -14, "minecraft:spruce_stairs[facing=south]"); }
        chest(-17, G, -21, "west", "pixelpirates:chests/gaol_barracks");
        chest(-17, G, -5, "west", "pixelpirates:chests/gaol_barracks");
        for (int z = -20; z <= -6; z += 7) barrel(-38, G, z, "pixelpirates:chests/gaol_barracks");
        spawner(-27, G, -5, "pixelpirates:skeleton_pirate");
        for (int x = -34; x <= -20; x += 7) put(x, G + 7, -12, "minecraft:soul_lantern[hanging=true]");
        // WARDEN'S OFFICE (east): bookshelves, desk, lectern, strongbox
        for (int z = -21; z <= -3; z++) { if (z >= -17 && z <= -13) continue; put(30, G, z, "minecraft:bookshelf"); put(30, G + 1, z, "minecraft:bookshelf"); put(30, G + 2, z, z % 3 == 0 ? "minecraft:soul_lantern" : "minecraft:bookshelf"); }
        for (int x = 20; x <= 24; x++) put(x, G, -12, "minecraft:dark_oak_stairs[facing=west,half=top]");
        put(22, G + 1, -12, "minecraft:candle[candles=3,lit=true]");
        put(22, G, -10, "minecraft:dark_oak_stairs[facing=north]");
        put(18, G, -18, "minecraft:lectern[facing=east]");
        chest(28, G, -4, "west", "pixelpirates:chests/gaol_warden");
        for (int x = 17; x <= 29; x++) for (int z = -21; z <= -3; z++) if ((x + z) % 2 == 0 && isCav(x, G, z) && at(x, G, z).contains("air")) put(x, G - 1, z, "minecraft:dark_oak_planks");
        put(23, G + 6, -12, "minecraft:soul_lantern[hanging=true]");
        // the question room: stocks, an anvil, chains
        for (int z = -20; z <= -10; z += 5) {
            put(35, G, z, "minecraft:dark_oak_fence"); put(35, G + 1, z, "minecraft:dark_oak_trapdoor[half=bottom,facing=east,open=false]");
            put(36, G, z, "minecraft:dark_oak_fence"); put(36, G + 1, z, "minecraft:dark_oak_trapdoor[half=bottom,facing=west,open=false]");
        }
        put(38, G, -15, "minecraft:damaged_anvil[facing=north]");
        for (int y = G + 1; y <= G + 5; y++) { put(33, y, -21, "minecraft:chain[axis=y]"); put(39, y, -9, "minecraft:chain[axis=y]"); }
        put(36, G + 5, -15, "minecraft:soul_lantern[hanging=true]");
    }

    // ---------------------------------------------------------------- WEEPING GARDEN (west)
    private void garden() {
        int cx = -72, cz = -8;
        for (int i = 0; i < 9; i++) {                                                    // wispwood trees
            int x = cx + rnd.nextInt(26) - 13, z = cz + rnd.nextInt(28) - 14;
            int f = floorY(x, z, G - 4, G + 6);
            if (f == Integer.MIN_VALUE) continue;
            int h = 5 + rnd.nextInt(4);
            for (int y = f; y < f + h; y++) put(x, y, z, "pixelpirates:wispwood_log[axis=y]");
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = -1; dy <= 1; dy++) {
                if (Math.abs(dx) + Math.abs(dz) + Math.abs(dy) > 3 || rnd.nextInt(4) == 0) continue;
                if (isCav(x + dx, f + h + dy, z + dz) && at(x + dx, f + h + dy, z + dz).contains("air"))
                    put(x + dx, f + h + dy, z + dz, "pixelpirates:wisp_leaves[persistent=true]");
            }
        }
        for (int i = 0; i < 26; i++) {                                                   // headstones
            int x = cx + rnd.nextInt(28) - 14, z = cz + rnd.nextInt(30) - 15;
            int f = floorY(x, z, G - 4, G + 6);
            if (f == Integer.MIN_VALUE || !at(x, f, z).contains("air")) continue;
            put(x, f - 1, z, "pixelpirates:grave_silt");
            put(x, f, z, rnd.nextBoolean() ? "minecraft:cobblestone_wall" : "minecraft:mossy_cobblestone_wall");
            if (rnd.nextBoolean()) put(x, f + 1, z, "minecraft:stone_slab[type=bottom]");
            if (rnd.nextInt(4) == 0) put(x, f, z + 1, "minecraft:candle[candles=2,lit=true]");
        }
        // the mausoleum
        int mx = cx - 4, mz = cz + 6, f = floorY(mx, mz, G - 4, G + 6);
        if (f != Integer.MIN_VALUE) {
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = f; y <= f + 4; y++) {
                boolean wall = Math.abs(x) == 3 || Math.abs(z) == 3;
                put(mx + x, y, mz + z, !wall ? "minecraft:cave_air" : (z == -3 && x == 0 && y <= f + 1) ? "minecraft:cave_air" : "minecraft:polished_blackstone_bricks");
            }
            fillB(mx - 3, f + 5, mz - 3, mx + 3, f + 5, mz + 3, "minecraft:polished_blackstone_brick_slab[type=bottom]");
            chest(mx, f, mz + 2, "north", "pixelpirates:chests/grotto_garden");
            put(mx, f + 3, mz, "minecraft:soul_lantern[hanging=true]");
            put(mx - 2, f, mz + 2, "minecraft:skeleton_skull"); put(mx + 2, f, mz + 2, "minecraft:skeleton_skull");
        }
        spawner(cx + 8, G - 1, cz - 8, "pixelpirates:phantom_pirate");
        for (int i = 0; i < 14; i++) {
            int x = cx + rnd.nextInt(30) - 15, z = cz + rnd.nextInt(30) - 15;
            int c = ceilY(x, z, G, G + 26);
            if (c != Integer.MIN_VALUE) put(x, c, z, "minecraft:glow_lichen[up=true]");
        }
    }

    // ---------------------------------------------------------------- OUBLIETTE (east, flooded)
    private void oubliette() {
        int cx = 74, cz = -8;
        for (int k = 0; k < 5; k++) {                                                    // sunken cages on the bottom
            int x = cx + rnd.nextInt(18) - 9, z = cz + rnd.nextInt(20) - 10;
            int f = floorY(x, z, G - 14, G - 4);
            if (f == Integer.MIN_VALUE) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int y = f; y <= f + 2; y++)
                if (dx != 0 || dz != 0) put(x + dx, y, z + dz, BARS + "[waterlogged=true]");
            put(x, f, z, k % 2 == 0 ? "minecraft:skeleton_skull" : "minecraft:water");
        }
        chest(cx, floorY(cx, cz, G - 14, G - 4), cz, "north", "pixelpirates:chests/grotto_oubliette");
        spawner(cx - 6, G - 11, cz + 4, "pixelpirates:drowned_sailor");
        for (int x = cx - 10; x <= cx + 10; x += 5) {
            int c = ceilY(x, cz, G - 6, G + 12);
            if (c != Integer.MIN_VALUE) { put(x, c, cz, "minecraft:chain[axis=y]"); put(x, c - 1, cz, "minecraft:soul_lantern[hanging=true]"); }
        }
    }

    // ---------------------------------------------------------------- HANGING CHAPEL
    private void chapel() {
        String PEW = "minecraft:dark_oak_stairs[facing=north]";
        for (int x = -12; x <= 12; x++) for (int z = 18; z <= 42; z++)
            if (isCav(x, G, z) && !isCav(x, G - 1, z)) put(x, G - 1, z, Math.abs(x) <= 1 ? "minecraft:crimson_planks" : ((x + z) & 1) == 0 ? "minecraft:deepslate_tiles" : "minecraft:polished_deepslate");
        for (int z = 22; z <= 36; z += 3) for (int x = -10; x <= 10; x++) if (Math.abs(x) > 2) put(x, G, z, PEW);
        // nooses over the pews
        for (int z = 21; z <= 37; z += 4) for (int x = -8; x <= 8; x += 4) {
            int c = ceilY(x, z, G + 6, G + 24);
            if (c == Integer.MIN_VALUE) continue;
            for (int y = c; y > G + 5; y--) put(x, y, z, "minecraft:chain[axis=y]");
            put(x, G + 5, z, "minecraft:tripwire_hook[facing=south]");
        }
        // the altar, the sentence; the bone organ now flanks the gate
        fillB(-4, G, 38, 4, G, 40, "minecraft:polished_blackstone_bricks");
        put(-2, G + 1, 39, "minecraft:lectern[facing=north]");
        put(-3, G + 1, 39, "minecraft:candle[candles=4,lit=true]"); put(3, G + 1, 39, "minecraft:candle[candles=4,lit=true]");
        sign(2, G + 1, 38, "minecraft:dark_oak_sign[rotation=8]", "HERE THE", "SENTENCE IS", "CARRIED OUT", "");
        for (int x : new int[]{-14, -13, -12, -11, 11, 12, 13, 14}) {
            int h = 6 + (14 - Math.abs(x)) * 2;
            for (int y = G; y < G + h; y++) if (isCav(x, y, 41) || y < G + 6) put(x, y, 41, "minecraft:bone_block[axis=y]");
        }
        for (int z = 22; z <= 38; z += 8) put(0, G + 11, z, "minecraft:soul_lantern[hanging=true]");
        spawner(-11, G, 30, "pixelpirates:drowned_hands");
        spawner(11, G, 26, "pixelpirates:drowned_hands");
        skullGate();
        for (int st = 0; st < 8; st++) for (int x = -4; x <= 4; x++) put(x, G - st - 1, 50 + st - 1 < 50 ? 50 : 50 + st, "minecraft:deepslate_brick_stairs[facing=north]");
    }

    /**
     * THE SKULL GATE: the chapel's south wall is a colossal skull (21 x 17, three blocks deep) - soul fire burning deep in
     * its eye sockets, chains weeping from them, a cracked cranium, and the doorway down to the pit is its fanged jaw.
     * Behind the teeth, the throat: a passage ribbed with bone arches, candles guttering, nooses overhead.
     */
    private void skullGate() {
        // drawn as the LEFT half + the centre column and mirrored, so the skull is exactly symmetrical (x = col - 10)
        String[] HALF = {
                "......BBBBB",
                "....BBBBBBB",
                "...BBBBBBBC",          // the sagittal crack down the crown
                "..BBBBBBBBC",
                "..BBBCBBBBB",
                ".BBBEEEEBBB",
                ".BBEEEEEEBB",
                ".BBEEEEEEBB",
                ".BBBEEEEBBB",
                "..BBBBBBBBN",
                "..BBBBBBBNN",
                "..BBBBBBBBB",
                "..BTFTTTFTT",          // teeth; the fangs (F) sit over x = -6, -2, 2, 6
                "..BMMMMMMMM",          // the jaw doorway, 15 wide (x -7..7)
                "..BMMMMMMMM",
                "..BMMMMMMMM",
                "..BtMMMMMMM"};
        String[] ART = new String[HALF.length];
        for (int r = 0; r < HALF.length; r++) ART[r] = HALF[r] + new StringBuilder(HALF[r].substring(0, 10)).reverse();
        int top = G + ART.length - 1;
        for (int row = 0; row < ART.length; row++) {
            int y = top - row;
            for (int col = 0; col < 21; col++) {
                int x = col - 10;
                char c = ART[row].charAt(col);
                for (int z = 42; z <= 45; z++) {
                    String b = switch (c) {
                        case 'B' -> z == 42 ? null : ((x * 3 + y + z) % 7 == 0 ? "minecraft:calcite" : "minecraft:bone_block[axis=y]");
                        case 'C' -> z == 42 ? null : "minecraft:polished_blackstone";
                        case 'E' -> z <= 44 ? "minecraft:cave_air" : "minecraft:crying_obsidian";
                        case 'N' -> z <= 43 ? "minecraft:cave_air" : "minecraft:blackstone";
                        case 'T' -> z == 43 ? "minecraft:calcite" : z == 42 ? null : "minecraft:bone_block[axis=y]";
                        case 'F' -> z == 43 ? "minecraft:calcite" : z == 42 ? null : "minecraft:bone_block[axis=y]";
                        case 't' -> z == 43 ? "minecraft:calcite" : "minecraft:cave_air";
                        case 'M' -> "minecraft:cave_air";
                        default -> z == 42 ? null : "minecraft:deepslate_bricks";                  // the wall the skull is set in
                    };
                    if (b != null) put(x, y, z, b);
                }
            }
        }
        // fangs hanging into the jaw, and the jaw's floor
        for (int x : new int[]{-6, -2, 2, 6}) put(x, top - 13, 43, "minecraft:calcite");
        for (int x = -7; x <= 7; x++) for (int z = 42; z <= 45; z++) put(x, G - 1, z, "minecraft:calcite");
        // soul fire in the sockets, chains weeping down from them
        for (int sx : new int[]{-1, 1}) {
            int ex = sx * 5;
            put(ex, top - 8, 44, "minecraft:soul_soil"); put(ex + sx, top - 8, 44, "minecraft:soul_soil");
            put(ex, top - 7, 44, "minecraft:soul_fire"); put(ex + sx, top - 7, 44, "minecraft:soul_fire");
            put(ex, top - 5, 43, "minecraft:soul_lantern[hanging=true]");
            for (int y = top - 9; y >= G + 5; y--) put(ex, y, 41, "minecraft:chain[axis=y]");
        }
        // braziers on bone plinths, hanged men either side, skulls on pikes along the approach
        for (int sx : new int[]{-1, 1}) {
            put(sx * 8, G, 40, "minecraft:bone_block[axis=y]"); put(sx * 8, G + 1, 40, "minecraft:soul_campfire[lit=true]");
            for (int y = G + 9; y >= G + 4; y--) put(sx * 6, y, 38, "minecraft:chain[axis=y]");
            put(sx * 6, G + 3, 38, "minecraft:skeleton_skull[rotation=0]");
            for (int z = 33; z <= 37; z += 4) { put(sx * 3, G, z, "minecraft:dark_oak_fence"); put(sx * 3, G + 1, z, "minecraft:wither_skeleton_skull[rotation=" + (sx < 0 ? 12 : 4) + "]"); }
        }
        // THE THROAT: bone ribs arching over the passage to the stair, candles, a noose
        for (int z = 46; z <= 49; z++) {
            boolean rib = (z & 1) == 0;
            for (int x = -4; x <= 4; x++) {
                if (rib) {
                    if (Math.abs(x) == 4) for (int y = G; y <= G + 5; y++) put(x, y, z, "minecraft:bone_block[axis=y]");
                    put(x, G + 6 + (Math.abs(x) <= 2 ? 1 : 0), z, "minecraft:bone_block[axis=x]");
                }
                put(x, G - 1, z, ((x + z) & 1) == 0 ? "minecraft:bone_block[axis=z]" : "minecraft:polished_blackstone_bricks");
            }
            pa(-3, G, z, "minecraft:candle[candles=" + (1 + (z % 4)) + ",lit=true]");
            pa(3, G, z, "minecraft:candle[candles=" + (1 + ((z + 1) % 4)) + ",lit=true]");
        }
        for (int y = G + 6; y >= G + 4; y--) put(0, y, 47, "minecraft:chain[axis=y]");
        put(0, G + 3, 47, "minecraft:tripwire_hook[facing=north]");
        put(0, G + 6, 49, "minecraft:soul_lantern[hanging=true]");
    }

    // ---------------------------------------------------------------- THE GALLOWS PIT (arena)
    private void pit() {
        int cz = PIT_CZ;
        // floor: tiles in rings, grave silt, soul soil, chains lying about
        for (int x = -PIT_R; x <= PIT_R; x++) for (int z = -PIT_R; z <= PIT_R; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d >= PIT_R) continue;
            String f = Math.abs(d - 10) < 0.6 || Math.abs(d - 22) < 0.6 ? "minecraft:polished_blackstone_bricks"
                    : noise3(x * 1.3, 0, z * 1.3) > 0.5 ? "pixelpirates:grave_silt" : noise3(x * 1.3, 0, z * 1.3) < -0.6 ? "minecraft:soul_soil"
                    : ((x + z) & 1) == 0 ? "minecraft:deepslate_tiles" : "minecraft:polished_deepslate";
            put(x, PIT_FLOOR - 1, cz + z, f);
        }
        // the central dais and gallows (the Revenant hangs here, asleep in his chains, until you come close)
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 6.3) continue;
            put(x, PIT_FLOOR, cz + z, d > 5.3 ? "minecraft:polished_blackstone_brick_stairs[facing=" + (Math.abs(x) > Math.abs(z) ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south")) + "]"
                    : "minecraft:polished_blackstone_bricks");
            if (d <= 5.3) put(x, PIT_FLOOR + 1, cz + z, d > 4.4 ? "minecraft:polished_blackstone_brick_slab[type=bottom]" : "minecraft:cave_air");
        }
        // THE GALLOWS: two great posts with braces, a crossbeam 18 up; he hangs by both wrists from two chains, his feet
        // ~6.5 blocks over the dais - right above the GALLOWBRAND IN THE STONE, whose hilt nearly touches his feet
        for (int y = PIT_FLOOR + 1; y <= PIT_FLOOR + 17; y++) {
            put(-6, y, cz, "minecraft:dark_oak_log[axis=y]"); put(5, y, cz, "minecraft:dark_oak_log[axis=y]");
        }
        for (int x = -7; x <= 6; x++) put(x, PIT_FLOOR + 18, cz, "minecraft:dark_oak_log[axis=x]");
        put(-5, PIT_FLOOR + 17, cz, "minecraft:dark_oak_fence"); put(4, PIT_FLOOR + 17, cz, "minecraft:dark_oak_fence");     // braces
        put(-7, PIT_FLOOR + 17, cz, "minecraft:soul_lantern[hanging=true]"); put(6, PIT_FLOOR + 17, cz, "minecraft:soul_lantern[hanging=true]");
        for (int s : new int[]{-1, 1}) {                                                // post feet: iron-shod plinths
            int px = s < 0 ? -6 : 5;
            put(px, PIT_FLOOR + 1, cz - 1, "minecraft:polished_blackstone_brick_stairs[facing=south]");
            put(px, PIT_FLOOR + 1, cz + 1, "minecraft:polished_blackstone_brick_stairs[facing=north]");
        }
        // he grips a horizontal bar of chain (x -4..3 at +14) slung from the crossbeam by two chains outside his fists
        for (int y = PIT_FLOOR + 17; y >= PIT_FLOOR + 15; y--) {
            put(-4, y, cz, "minecraft:chain[axis=y]"); put(3, y, cz, "minecraft:chain[axis=y]");
            hangChains.add(new int[]{-4, y, cz}); hangChains.add(new int[]{3, y, cz});
        }
        for (int x = -4; x <= 3; x++) { put(x, PIT_FLOOR + 14, cz, "minecraft:chain[axis=x]"); hangChains.add(new int[]{x, PIT_FLOOR + 14, cz}); }
        bossSpawn = new int[]{0, PIT_FLOOR + 6, cz};                                     // + 0.5 y in GallowsGrotto
        put(0, PIT_FLOOR + 1, cz, "pixelpirates:gallowbrand_stone[has_sword=true]");
        bladeStone = new int[]{0, PIT_FLOOR + 1, cz};
        // a ring of dead men's candles and bones round the stone
        for (int[] p : new int[][]{{-3, -2}, {3, -2}, {-3, 2}, {3, 2}, {0, -3}, {0, 3}})
            put(p[0], PIT_FLOOR + 1, cz + p[1], Math.abs(p[0]) == 3 ? "minecraft:candle[candles=3,lit=true]" : "minecraft:skeleton_skull[rotation=" + (p[1] < 0 ? 8 : 0) + "]");
        // THE WALL OF LIGHTS: 16 pairs of sea lanterns round the pit wall - one goes dark every 15 s of the fight, and when
        // the last dies the Revenant enrages (ChainedRevenantEntity). The lamp-keeper's story, made into a clock.
        for (int k = 0; k < 16; k++) {
            double a = -Math.PI / 2 + Math.PI / 16 + k * Math.PI / 8;
            int ly = PIT_FLOOR + 4;
            int x = (int) Math.round(Math.cos(a) * (PIT_R - 1.5)), z = (int) Math.round(Math.sin(a) * (PIT_R - 1.5));
            while (isCav(x, ly, cz + z) && x * x + z * z < (PIT_R + 3) * (PIT_R + 3)) {
                double d = Math.sqrt(x * x + z * z);
                x = (int) Math.round(x * (d + 1) / d); z = (int) Math.round(z * (d + 1) / d);
            }
            if (Math.abs(x) <= 5 && z < 0) continue;                                         // not in the gate
            put(x, ly - 1, cz + z, "minecraft:polished_blackstone_bricks");
            put(x, ly, cz + z, "minecraft:sea_lantern");
            put(x, ly + 1, cz + z, "minecraft:sea_lantern");
            put(x, ly + 2, cz + z, "minecraft:chiseled_polished_blackstone");
            lightWall.add(new int[]{x, ly, cz + z});
        }
        // four pillars (cover), chains draped between their tops and the dome
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            int px = (int) Math.round(Math.cos(a) * 17), pz = cz + (int) Math.round(Math.sin(a) * 17);
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                if (x * x + z * z > 5) continue;
                for (int y = PIT_FLOOR; y <= -10; y++) if (isCav(px + x, y, pz + z) || y < PIT_FLOOR + 30)
                    put(px + x, y, pz + z, (y - PIT_FLOOR) % 7 == 0 ? "minecraft:polished_blackstone_bricks" : noise3(px + x, y, pz + z) > 0.4 ? "minecraft:cracked_deepslate_bricks" : "minecraft:deepslate_bricks");
            }
            put(px, PIT_FLOOR + 6, pz + 3, "minecraft:soul_lantern[hanging=false]");
        }
        // soul-fire braziers round the edge
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI / 6;
            int x = (int) Math.round(Math.cos(a) * 26), z = cz + (int) Math.round(Math.sin(a) * 26);
            if (z < cz - 22 && Math.abs(x) < 7) continue;                                // keep the stair clear
            put(x, PIT_FLOOR, z, "minecraft:polished_blackstone_bricks");
            put(x, PIT_FLOOR + 1, z, "minecraft:soul_campfire[lit=true]");
        }
        // THE MANACLE ANCHORS: eight iron plates set in the wall at three heights, facing the centre
        int[] heights = {PIT_FLOOR + 8, PIT_FLOOR + 13, PIT_FLOOR + 18, PIT_FLOOR + 10, PIT_FLOOR + 15, PIT_FLOOR + 8, PIT_FLOOR + 18, PIT_FLOOR + 12};
        for (int k = 0; k < 8; k++) {
            double a = Math.PI / 2 + (k + 0.5) * (2 * Math.PI - 0.9) / 8 + 0.45;          // skips the north (gate) sector
            int x = (int) Math.round(Math.cos(a) * (PIT_R - 0.5)), z = (int) Math.round(Math.sin(a) * (PIT_R - 0.5));
            // push out to the first solid cell (the wall)
            while (isCav(x, heights[k], cz + z) && x * x + z * z < (PIT_R + 3) * (PIT_R + 3)) {
                double d = Math.sqrt(x * x + z * z);
                x = (int) Math.round(x * (d + 1) / d); z = (int) Math.round(z * (d + 1) / d);
            }
            int y = heights[k];
            int facing = Math.abs(x) > Math.abs(z) ? (x > 0 ? 3 : 1) : (z > 0 ? 0 : 2);
            String f = new String[]{"north", "east", "south", "west"}[facing];
            put(x, y, cz + z, "pixelpirates:manacle_anchor[facing=" + f + "]");
            put(x, y - 1, cz + z, "minecraft:polished_blackstone_bricks");
            put(x, y + 1, cz + z, "minecraft:polished_blackstone_bricks");
            anchors.add(new int[]{x, y, cz + z, facing});
        }
        // FOUR GIBBET CAGES hanging from the dome - where the Tideshackle slings the head
        for (int k = 0; k < 4; k++) {
            double a = k * Math.PI / 2 + Math.PI / 4 + 0.3;
            int x = (int) Math.round(Math.cos(a) * 22), z = (int) Math.round(Math.sin(a) * 22);
            int c = ceilY(x, cz + z, PIT_FLOOR + 10, -8);
            if (c == Integer.MIN_VALUE) c = -26;
            int[] ins = cage(x, PIT_FLOOR + 11, cz + z, 3, c, null);
            cages.add(ins);
        }
        // bones and fallen chains
        for (int i = 0; i < 40; i++) {
            int x = rnd.nextInt(56) - 28, z = rnd.nextInt(56) - 28;
            if (x * x + z * z > 27 * 27 || x * x + z * z < 49) continue;
            if (!isAirAt(x, PIT_FLOOR, cz + z)) continue;
            put(x, PIT_FLOOR, cz + z, rnd.nextInt(3) == 0 ? "minecraft:bone_block[axis=" + (rnd.nextBoolean() ? "x" : "z") + "]" : "minecraft:chain[axis=" + (rnd.nextBoolean() ? "x" : "z") + "]");
        }
        // dome lanterns
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int x = (int) Math.round(Math.cos(a) * 12), z = (int) Math.round(Math.sin(a) * 12);
            int c = ceilY(x, cz + z, PIT_FLOOR + 10, -8);
            if (c == Integer.MIN_VALUE) continue;
            for (int y = c; y > c - 4; y--) put(x, y, cz + z, "minecraft:chain[axis=y]");
            put(x, c - 4, cz + z, "minecraft:soul_lantern[hanging=true]");
        }
    }

    // ---------------------------------------------------------------- THE HOARD
    private void hoard() {
        for (int x = -1; x <= 1; x++) for (int y = PIT_FLOOR; y <= PIT_FLOOR + 2; y++) put(x, y, 109, "pixelpirates:blast_rubble");
        fillB(-6, PIT_FLOOR - 1, 112, 6, PIT_FLOOR - 1, 121, "minecraft:gold_block");
        for (int x = -6; x <= 6; x++) for (int z = 112; z <= 121; z++) if ((x * 3 + z) % 5 == 0) put(x, PIT_FLOOR - 1, z, "minecraft:polished_blackstone_bricks");
        chest(-3, PIT_FLOOR, 120, "north", "pixelpirates:chests/revenant_hoard");
        chest(0, PIT_FLOOR, 120, "north", "pixelpirates:chests/revenant_hoard");
        chest(3, PIT_FLOOR, 120, "north", "pixelpirates:chests/phase4_treasure");
        for (int x = -5; x <= 5; x += 5) put(x, PIT_FLOOR + 5, 116, "minecraft:soul_lantern[hanging=true]");
        put(-5, PIT_FLOOR, 114, "minecraft:skeleton_skull"); put(5, PIT_FLOOR, 114, "minecraft:skeleton_skull");
    }

    // ---------------------------------------------------------------- DETAIL PASS (2026-09-30, after the fullbright review)
    // Every room got a second layer of dressing: the gaol's vaulting, drain and lived-in cells; the barracks hearth and
    // dummies; the warden's rug, fireplace and cabinets; the question room's rack, hot irons and blood; a walkway round
    // the oubliette and a drowned garden under it; the gallery's abandoned camp; the garden's railed graves; the hoard's
    // mounds of gold and its empty throne. pa() only ever writes into open air, so nothing structural is disturbed.
    void pa(int x, int y, int z, String b) { if (isAirAt(x, y, z)) put(x, y, z, b); }

    boolean isWaterAt(int x, int y, int z) { String s = at(x, y, z); return s != null && s.equals("minecraft:water"); }

    /** A hearth against a wall: brick surround, a (lit) campfire, a chimney to the ceiling. `dz` = direction into the room. */
    void hearth(int x, int y, int z, int dz, boolean soul) {
        for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy <= 3; dy++) {
            boolean opening = Math.abs(dx) <= 1 && dy <= 1;
            if (!opening) put(x + dx, y + dy, z, dy == 3 ? "minecraft:bricks" : Math.abs(dx) == 2 ? "minecraft:polished_blackstone_bricks" : "minecraft:bricks");
        }
        for (int dx = -1; dx <= 1; dx++) put(x + dx, y + 2, z + dz, "minecraft:brick_slab[type=top]");                 // mantel
        put(x, y, z, soul ? "minecraft:soul_campfire[lit=true]" : "minecraft:campfire[lit=true]");
        put(x - 1, y, z, "minecraft:air"); put(x + 1, y, z, "minecraft:air");
        for (int dy = 4; dy < 12 && isAirAt(x, y + dy, z); dy++) put(x, y + dy, z, "minecraft:bricks");                  // chimney
        pa(x - 1, y + 3, z + dz, "minecraft:candle[candles=2,lit=true]");
        pa(x + 1, y + 3, z + dz, "minecraft:skeleton_skull[rotation=" + (dz > 0 ? 0 : 8) + "]");
    }

    /** A hanging lantern ring: chain from the ceiling + four lanterns round it. */
    void chandelier(int x, int z, int from, int to) {
        int c = ceilY(x, z, from, to);
        if (c == Integer.MIN_VALUE) return;
        for (int y = c; y > c - 2; y--) pa(x, y, z, "minecraft:chain[axis=y]");
        pa(x, c - 2, z, "minecraft:polished_blackstone_wall");
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) pa(x + d[0], c - 2, z + d[1], "minecraft:soul_lantern[hanging=true]");
    }

    private void furnish() {
        // ================= THE GAOL HALL: rib vaulting, a drain grate, soul torches and black banners, the guard post
        for (int z = -30; z <= 6; z += 4) {
            pa(-3, G + 6, z, "minecraft:deepslate_brick_stairs[facing=east,half=top]");
            pa(3, G + 6, z, "minecraft:deepslate_brick_stairs[facing=west,half=top]");
            for (int x = -2; x <= 2; x++) pa(x, G + 6, z, "minecraft:deepslate_tile_slab[type=top]");
        }
        for (int z = -31; z <= 7; z++) if (Math.floorMod(z, 3) == 0) put(0, G - 1, z, "minecraft:iron_trapdoor[half=top,facing=north]");
        for (int k = 0; k < 7; k++) {
            int zw = -30 + k * 5 - 1;
            for (int s : new int[]{-1, 1}) {
                if (((k + (s < 0 ? 0 : 1)) & 1) == 0) pa(3 * s, G + 3, zw, "minecraft:soul_wall_torch[facing=" + (s < 0 ? "east" : "west") + "]");
                else pa(3 * s, G + 4, zw, "minecraft:black_wall_banner[facing=" + (s < 0 ? "east" : "west") + "]");
            }
        }
        // the guard post by the portcullis: desk, stool, the key hook, the alarm bell
        pa(2, G, -31, "minecraft:dark_oak_stairs[facing=west,half=top]"); pa(2, G, -30, "minecraft:dark_oak_stairs[facing=west,half=top]");
        pa(2, G + 1, -31, "minecraft:candle[candles=1,lit=true]"); pa(1, G, -30, "minecraft:spruce_stairs[facing=east]");
        pa(3, G + 2, -31, "minecraft:tripwire_hook[facing=west]");
        pa(-2, G + 5, -31, "minecraft:bell[attachment=ceiling,facing=east]");
        pa(-3, G, -31, "minecraft:barrel[facing=up]"); pa(-3, G + 1, -31, "minecraft:barrel[facing=up]"); pa(-3, G, -30, "minecraft:barrel[facing=east]");
        for (int i = 0; i < 18; i++) {
            int x = rnd.nextInt(7) - 3, z = -31 + rnd.nextInt(39);
            if (x == 0 || isAirAt(x, G - 1, z)) continue;
            if (rnd.nextBoolean()) pa(x, G + 5, z, "minecraft:cobweb");
            else pa(x, G, z, rnd.nextBoolean() ? "minecraft:bone_block[axis=x]" : "minecraft:chain[axis=z]");
        }
        // ================= THE CELLS: straw beds, slop buckets, wall manacles, what the last prisoner left
        for (int k = 0; k < 7; k++) {
            int z0 = -30 + k * 5;
            for (int s : new int[]{-1, 1}) {
                int back = 13 * s, mid = 9 * s;
                pa(back, G, z0 + 3, "minecraft:hay_block[axis=" + (rnd.nextBoolean() ? "x" : "z") + "]");                  // the straw
                pa(back - s, G, z0 + 3, "minecraft:brown_carpet");
                pa(back, G, z0, "minecraft:cauldron");                                                                   // the slop bucket
                for (int y = G + 1; y <= G + 3; y++) pa(back, y, z0 + 1, "minecraft:chain[axis=y]");                     // manacles
                pa(back, G + 1, z0 + 2, "minecraft:chain[axis=y]"); pa(back, G + 2, z0 + 2, "minecraft:chain[axis=y]");
                pa(back, G + 4, z0 + 1, "minecraft:tripwire_hook[facing=" + (s < 0 ? "east" : "west") + "]");
                switch (rnd.nextInt(5)) {
                    case 0 -> { pa(mid, G, z0 + 2, "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]"); pa(mid + s, G, z0 + 2, "minecraft:bone_block[axis=x]"); }
                    case 1 -> { pa(mid, G, z0 + 1, "minecraft:candle[candles=1,lit=true]"); pa(mid + s, G, z0 + 3, "minecraft:flower_pot"); }
                    case 2 -> { pa(mid, G, z0 + 3, "minecraft:spruce_trapdoor[half=bottom,facing=north,open=false]"); pa(mid, G + 1, z0 + 3, "minecraft:candle[candles=2,lit=true]"); }
                    case 3 -> { pa(mid, G, z0, "minecraft:cobweb"); pa(back, G + 3, z0 + 3, "minecraft:cobweb"); }
                    default -> pa(mid, G, z0 + 1, "minecraft:decorated_pot");
                }
                if (rnd.nextInt(3) == 0) pa(back - 2 * s, G + 4, z0 + 2, "minecraft:soul_lantern[hanging=true]");
            }
        }
        // ================= BARRACKS: the hearth, a rack of crates, training dummies, a rug, a chandelier, banners
        hearth(-27, G, -22, 1, false);
        for (int x = -31; x <= -23; x++) for (int z = -9; z <= -5; z++) pa(x, G, z, (x + z) % 4 == 0 ? "minecraft:black_carpet" : "minecraft:red_carpet");
        for (int[] d : new int[][]{{-21, -19}, {-19, -19}}) {
            pa(d[0], G, d[1], "minecraft:dark_oak_fence"); pa(d[0], G + 1, d[1], "minecraft:hay_block[axis=y]"); pa(d[0], G + 2, d[1], "minecraft:skeleton_skull[rotation=8]");
        }
        for (int x = -24; x <= -18; x++) { pa(x, G, -3, "minecraft:barrel[facing=north]"); if (x % 2 == 0) pa(x, G + 1, -3, "minecraft:barrel[facing=up]"); }
        pa(-19, G, -6, "minecraft:smithing_table"); pa(-20, G, -6, "minecraft:grindstone[face=floor,facing=north]"); pa(-21, G, -6, "minecraft:fletching_table");
        chandelier(-27, -12, G + 3, G + 8);
        for (int z = -20; z <= -4; z += 8) pa(-16, G + 5, z, "minecraft:black_wall_banner[facing=west]");
        // ================= WARDEN'S OFFICE: rug, fireplace, cabinets, the map table, a chandelier
        for (int x = 18; x <= 26; x++) for (int z = -17; z <= -7; z++) pa(x, G, z, x == 18 || x == 26 || z == -17 || z == -7 ? "minecraft:black_carpet" : "minecraft:red_carpet");
        hearth(23, G, -22, 1, true);
        for (int z = -21; z <= -18; z++) { pa(17, G, z, "minecraft:barrel[facing=east]"); pa(17, G + 1, z, "minecraft:barrel[facing=east]"); }
        for (int x = 18; x <= 24; x += 2) { pa(x, G, -3, "minecraft:bookshelf"); pa(x, G + 1, -3, "minecraft:bookshelf"); pa(x + 1, G, -3, "minecraft:chiseled_bookshelf[facing=north]"); }
        pa(27, G, -16, "minecraft:cartography_table"); pa(27, G + 1, -16, "minecraft:candle[candles=3,lit=true]");
        pa(22, G, -11, "minecraft:dark_oak_stairs[facing=south]");
        chandelier(22, -12, G + 3, G + 8);
        pa(29, G, -21, "minecraft:iron_bars"); pa(29, G + 1, -21, "minecraft:iron_bars"); pa(28, G + 2, -21, "minecraft:tripwire_hook[facing=east]");
        // ================= THE QUESTION ROOM: the rack, hot irons, a brazier, hooks, blood on the floor
        fillB(37, G, -20, 39, G, -18, "minecraft:dark_oak_slab[type=bottom]");
        pa(37, G + 1, -20, "minecraft:chain[axis=x]"); pa(39, G + 1, -18, "minecraft:chain[axis=x]");
        pa(38, G, -21, "minecraft:lever[face=floor,facing=north]");
        pa(33, G, -20, "minecraft:lava_cauldron"); pa(33, G, -19, "minecraft:grindstone[face=floor,facing=east]");
        pa(34, G, -9, "minecraft:soul_campfire[lit=true]");
        for (int[] h : new int[][]{{34, -17}, {38, -13}, {36, -10}}) {
            int c = ceilY(h[0], h[1], G + 2, G + 8);
            if (c == Integer.MIN_VALUE) continue;
            for (int y = c; y > c - 2; y--) pa(h[0], y, h[1], "minecraft:chain[axis=y]");
            pa(h[0], c - 2, h[1], "minecraft:tripwire_hook[facing=north]");
        }
        for (int i = 0; i < 14; i++) pa(33 + rnd.nextInt(7), G, -21 + rnd.nextInt(12), "minecraft:redstone_wire");                 // blood
        pa(40, G, -9, "minecraft:iron_bars"); pa(40, G + 1, -9, "minecraft:iron_bars"); pa(39, G, -8, "minecraft:skeleton_skull[rotation=6]");
        // ================= THE TUNNEL DOWN TO THE OUBLIETTE: proper stairs wherever the floor steps down
        for (int x = 40; x <= 62; x++) for (int z = -20; z <= -5; z++) {
            int f = floorY(x, z, G - 8, G + 2), f2 = floorY(x + 1, z, G - 8, G + 2);
            if (f == Integer.MIN_VALUE || f2 == Integer.MIN_VALUE || f2 != f - 1) continue;
            if (isAirAt(x + 1, f2, z) && !isWaterAt(x + 1, f2 - 1, z)) put(x + 1, f2, z, "minecraft:deepslate_brick_stairs[facing=west]");
        }
        // ================= THE OUBLIETTE: a slab walkway on the water round the edge, railed; a drowned garden below
        int ox = 74, oz = -8, surf = G - 6;
        for (int x = ox - 20; x <= ox + 20; x++) for (int z = oz - 20; z <= oz + 20; z++) {
            double d = Math.sqrt((x - ox) * (x - ox) + (z - oz) * (z - oz));
            if (d < 10.5 || !isWaterAt(x, surf, z) || !isAirAt(x, surf + 1, z)) continue;
            boolean edge = !isWaterAt(x + 1, surf, z) || !isWaterAt(x - 1, surf, z) || !isWaterAt(x, surf, z + 1) || !isWaterAt(x, surf, z - 1)
                    || !isWaterAt(x + 2, surf, z) || !isWaterAt(x - 2, surf, z) || !isWaterAt(x, surf, z + 2) || !isWaterAt(x, surf, z - 2);
            if (!edge) continue;
            put(x, surf, z, "minecraft:deepslate_tile_slab[type=top,waterlogged=true]");
            if (d < 12.5 && Math.floorMod(x + z, 2) == 0) pa(x, surf + 1, z, (x + z) % 6 == 0 ? "minecraft:soul_lantern" : BARS);
        }
        for (int i = 0; i < 160; i++) {
            int x = ox + rnd.nextInt(34) - 17, z = oz + rnd.nextInt(34) - 17;
            int f = Integer.MIN_VALUE;
            for (int y = G - 14; y < surf; y++) if (isWaterAt(x, y, z) && solidAt(x, y - 1, z)) { f = y; break; }
            if (f == Integer.MIN_VALUE) continue;
            int r = rnd.nextInt(10);
            if (r < 3) {
                int h = 2 + rnd.nextInt(Math.max(1, surf - f - 2));
                for (int y = f; y < f + h && y < surf - 1; y++) put(x, y, z, "minecraft:kelp_plant");
                put(x, Math.min(f + h, surf - 1), z, "minecraft:kelp[age=20]");
            } else if (r < 6) put(x, f, z, "minecraft:seagrass");
            else if (r < 8) put(x, f, z, "minecraft:sea_pickle[pickles=" + (1 + rnd.nextInt(4)) + ",waterlogged=true]");
            else put(x, f, z, "minecraft:bone_block[axis=" + (rnd.nextBoolean() ? "x" : "z") + "]");                        // (a skull would leave an air bubble)
        }
        // ================= THE GIBBET GALLERY: an expedition that never left - tent, bedrolls, crates, a dead fire
        int cx0 = -12, cz0 = -52, f0 = floorY(cx0, cz0, G - 2, G + 4);
        if (f0 != Integer.MIN_VALUE) {
            for (int x = -2; x <= 2; x++) for (int dy = 0; dy <= 2; dy++) {
                int w = 2 - dy;
                pa(cx0 + x, f0 + dy, cz0 - w, "minecraft:white_wool"); pa(cx0 + x, f0 + dy, cz0 + w, "minecraft:white_wool");
            }
            for (int x = -2; x <= 2; x++) pa(cx0 + x, f0 + 3, cz0, "minecraft:spruce_fence");
            pa(cx0 - 1, f0, cz0, "minecraft:brown_carpet"); pa(cx0 + 1, f0, cz0, "minecraft:brown_carpet");
            pa(cx0 + 4, f0, cz0 + 2, "minecraft:campfire[lit=false]");
            pa(cx0 + 5, f0, cz0 - 1, "minecraft:barrel[facing=up]"); pa(cx0 + 5, f0 + 1, cz0 - 1, "minecraft:lantern");
            pa(cx0 + 6, f0, cz0, "minecraft:composter"); pa(cx0 + 3, f0, cz0 - 3, "minecraft:skeleton_skull[rotation=3]");
            barrel(cx0 - 4, f0, cz0 + 2, "pixelpirates:chests/grotto_gallery");
        }
        for (int i = 0; i < 30; i++) {                                                            // loose bones and old rope underfoot
            int x = rnd.nextInt(50) - 25, z = -98 + rnd.nextInt(56);
            int f = floorY(x, z, G - 2, G + 4);
            if (f != Integer.MIN_VALUE) pa(x, f, z, rnd.nextInt(3) == 0 ? "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]" : rnd.nextBoolean() ? "minecraft:bone_block[axis=z]" : "minecraft:chain[axis=x]");
        }
        // ================= WEEPING GARDEN: railed family plots, wither roses, lantern posts, a path of old flagstones
        int gx = -72, gz = -8;
        for (int i = 0; i < 4; i++) {
            int px = gx - 10 + rnd.nextInt(20), pz = gz - 10 + rnd.nextInt(20), f = floorY(px, pz, G - 4, G + 6);
            if (f == Integer.MIN_VALUE) continue;
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                boolean rim = Math.abs(x) == 2 || Math.abs(z) == 2;
                if (rim && !(x == 0 && z == 2)) pa(px + x, f, pz + z, "minecraft:iron_bars");
                else if (!rim && rnd.nextInt(3) == 0) pa(px + x, f, pz + z, "minecraft:wither_rose");
            }
            pa(px, f, pz - 1, "minecraft:mossy_stone_brick_wall"); pa(px, f + 1, pz - 1, "minecraft:skeleton_skull[rotation=0]");
        }
        for (int i = 0; i < 6; i++) {
            int x = gx - 12 + rnd.nextInt(24), z = gz - 12 + rnd.nextInt(24), f = floorY(x, z, G - 4, G + 6);
            if (f == Integer.MIN_VALUE || !isAirAt(x, f, z)) continue;
            pa(x, f, z, "minecraft:dark_oak_fence"); pa(x, f + 1, z, "minecraft:dark_oak_fence"); pa(x, f + 2, z, "minecraft:soul_lantern");
        }
        for (int z = gz - 14; z <= gz + 14; z++) {
            int x = gx + (int) Math.round(Math.sin(z * 0.25) * 3);
            for (int dx = 0; dx <= 1; dx++) {
                int f = floorY(x + dx, z, G - 4, G + 6);
                if (f != Integer.MIN_VALUE && isAirAt(x + dx, f, z)) put(x + dx, f - 1, z, rnd.nextInt(3) == 0 ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone");
            }
        }
        // ================= THE HOARD: mounds of gold, pots, an empty throne, pillars
        for (int[] m : new int[][]{{-5, 113}, {5, 113}, {-5, 118}, {5, 118}}) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                pa(m[0] + dx, PIT_FLOOR, m[1] + dz, rnd.nextInt(3) == 0 ? "minecraft:raw_gold_block" : "minecraft:gold_block");
                if (dx == 0 && dz == 0) pa(m[0], PIT_FLOOR + 1, m[1], rnd.nextBoolean() ? "minecraft:gold_block" : "minecraft:raw_gold_block");
            }
            pa(m[0], PIT_FLOOR + 2, m[1], rnd.nextBoolean() ? "minecraft:decorated_pot" : "minecraft:candle[candles=4,lit=true]");
        }
        for (int x = -3; x <= 3; x += 2) pa(x, PIT_FLOOR, 116, "minecraft:decorated_pot");
        pa(-1, PIT_FLOOR, 121, "minecraft:polished_blackstone_wall"); pa(1, PIT_FLOOR, 121, "minecraft:polished_blackstone_wall");
        pa(0, PIT_FLOOR + 1, 121, "minecraft:polished_blackstone_brick_wall"); pa(0, PIT_FLOOR + 2, 121, "minecraft:skeleton_skull[rotation=0]");
        for (int[] c : new int[][]{{-6, 112}, {6, 112}, {-6, 121}, {6, 121}})
            for (int y = PIT_FLOOR; y <= PIT_FLOOR + 6; y++) put(c[0], y, c[1], y == PIT_FLOOR + 3 ? "minecraft:chiseled_polished_blackstone" : "minecraft:polished_blackstone_bricks");
        for (int x = -4; x <= 4; x += 4) pa(x, PIT_FLOOR + 5, 121, "minecraft:black_wall_banner[facing=north]");
        pa(-2, PIT_FLOOR, 113, "minecraft:emerald_block"); pa(2, PIT_FLOOR, 119, "minecraft:emerald_block");
    }

    // ---------------------------------------------------------------- natural dressing: dripstone, cobwebs, bones
    /** The skull gate and its jaw stay exactly as drawn (symmetrical) - no random dripstone or clutter in them. */
    static boolean skullFace(int x, int z) { return Math.abs(x) <= 12 && z >= 40 && z <= 46; }

    private void dressCaves() {
        for (int n = 0; n < 900; n++) {
            int x = X0 + rnd.nextInt(NX), z = Z0 + rnd.nextInt(NZ);
            if (skullFace(x, z)) continue;
            int c = ceilY(x, z, G - 20, G + 34);
            if (c == Integer.MIN_VALUE || !isAirAt(x, c, z)) continue;
            int len = 1 + rnd.nextInt(3);
            for (int k = 0; k < len; k++) {
                if (!isAirAt(x, c - k, z)) break;
                String thick = k == len - 1 ? "tip" : k == len - 2 ? "frustum" : "middle";
                put(x, c - k, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=" + (len == 1 ? "tip" : thick) + "]");
            }
        }
        for (int n = 0; n < 500; n++) {
            int x = X0 + rnd.nextInt(NX), z = Z0 + rnd.nextInt(NZ);
            if (skullFace(x, z)) continue;
            int f = floorY(x, z, G - 20, G + 20);
            if (f == Integer.MIN_VALUE || !isAirAt(x, f, z)) continue;
            int r = rnd.nextInt(10);
            if (r < 3) put(x, f, z, "minecraft:cobweb");
            else if (r < 5) put(x, f, z, "minecraft:bone_block[axis=" + (rnd.nextBoolean() ? "x" : "z") + "]");
            else if (r < 6) put(x, f, z, "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
            else if (r < 8) put(x, f, z, "minecraft:candle[candles=" + (1 + rnd.nextInt(4)) + ",lit=true]");
        }
    }

    // =====================================================================================
    // 6. iron bars connect to their neighbours (no block updates at worldgen, so do it here)
    // =====================================================================================
    private void finishBars() {
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) for (int y = Y0; y <= Y1; y++) {
            String s = at(x, y, z);
            if (s == null || !s.startsWith(BARS)) continue;
            boolean wet = s.contains("waterlogged=true");
            String props = "east=" + conn(x + 1, y, z) + ",north=" + conn(x, y, z - 1) + ",south=" + conn(x, y, z + 1) + ",west=" + conn(x - 1, y, z)
                    + ",waterlogged=" + wet;
            set(x, y, z, BARS + "[" + props + "]");
        }
    }

    private boolean conn(int x, int y, int z) {
        String s = at(x, y, z);
        if (s == null) return false;
        if (s.startsWith(BARS)) return true;
        return !(s.contains("air") || s.contains("water") || s.contains("chain") || s.contains("skull") || s.contains("slab") || s.contains("lantern")
                || s.contains("candle") || s.contains("carpet") || s.contains("cobweb") || s.contains("stairs") || s.contains("barrel") || s.contains("chest"));
    }

    // =====================================================================================
    // preview: python-free PNGs of floor plans and cross-sections (run standalone)
    // =====================================================================================
    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "grotto";
        long t0 = System.currentTimeMillis();
        GallowsGrottoLayout L = build(12345L, (x, z) -> 18 + (int) (6 * Math.sin(x * 0.03) * Math.cos(z * 0.025)));
        System.out.println("built in " + (System.currentTimeMillis() - t0) + " ms, palette " + L.palette.size() + ", anchors " + L.anchors.size()
                + ", cages " + L.cages.size() + ", loot " + L.loot.size() + ", spawners " + L.spawners.size());
        int[] levels = {G - 16, G - 10, G, G + 3, G + 8, PIT_FLOOR, PIT_FLOOR + 11, ROCK_TOP + 1, DECK, DECK + 1};
        for (int yl : levels) L.plan(yl, out + "_plan_y" + yl + ".png");
        L.slice(true, 0, out + "_slice_x0.png");
        L.slice(false, -8, out + "_slice_z-8.png");
        L.slice(false, PIT_CZ, out + "_slice_z" + PIT_CZ + ".png");
    }

    static int color(String s) {
        if (s == null) return 0x101010;
        if (s.contains("water")) return 0x2a5ad8;
        if (s.contains("air")) return 0xd8d0c0;
        if (s.contains("iron_bars")) return 0x9aa0a8;
        if (s.contains("chain")) return 0x6a7078;
        if (s.contains("lantern") || s.contains("campfire") || s.contains("candle")) return 0x40f0e0;
        if (s.contains("chest") || s.contains("barrel")) return 0xe0a030;
        if (s.contains("spawner")) return 0xff2020;
        if (s.contains("manacle")) return 0xff40ff;
        if (s.contains("gold")) return 0xffd700;
        if (s.contains("bone") || s.contains("skull")) return 0xf0ecd8;
        if (s.contains("dark_oak") || s.contains("spruce") || s.contains("planks") || s.contains("bookshelf") || s.contains("lectern")) return 0x6a4a2a;
        if (s.contains("wisp")) return 0x60c0a0;
        if (s.contains("blackstone")) return 0x2a2228;
        if (s.contains("bricks") || s.contains("tiles") || s.contains("polished")) return 0x4a4e58;
        if (s.contains("silt") || s.contains("soul_soil") || s.contains("gravel")) return 0x6a6058;
        if (s.contains("dripstone")) return 0x8a7060;
        if (s.contains("moss")) return 0x4a7a3a;
        return 0x2e3036;
    }

    void plan(int y, String file) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(NX * 2, NZ * 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            String s = at(x, y, z);
            int c = s == null ? (y < terrain.applyAsInt(x, z) ? 0x181818 : 0x0a2040) : color(s);
            // show what stands on the floor from above: first non-air cell within 3 above
            if (s != null && s.contains("air")) for (int k = 1; k <= 3; k++) { String u = at(x, y + k, z); if (u != null && !u.contains("air")) { c = color(u) & 0xbfbfbf; break; } }
            for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) img.setRGB((x - X0) * 2 + a, (z - Z0) * 2 + b, c);
        }
        javax.imageio.ImageIO.write(img, "png", new java.io.File(file));
    }

    void slice(boolean alongZ, int at, String file) throws Exception {
        int w = alongZ ? NZ : NX;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w * 2, NY * 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int a = 0; a < w; a++) for (int y = Y0; y <= Y1; y++) {
            int x = alongZ ? at : X0 + a, z = alongZ ? Z0 + a : at;
            String s = at(x, y, z);
            int c = s == null ? (y < terrain.applyAsInt(x, z) ? 0x181818 : (y <= 63 ? 0x0a2040 : 0x80a0c0)) : color(s);
            for (int p = 0; p < 2; p++) for (int q = 0; q < 2; q++) img.setRGB(a * 2 + p, (Y1 - y) * 2 + q, c);
        }
        javax.imageio.ImageIO.write(img, "png", new java.io.File(file));
    }
}
