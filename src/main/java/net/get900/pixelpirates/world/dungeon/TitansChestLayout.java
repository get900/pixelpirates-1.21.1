package net.get900.pixelpirates.world.dungeon;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntBinaryOperator;

/**
 * THE TITAN'S CHEST - lair of the Abyssal Heart (boss 9/10), 2026-09-30. The ancients could not kill the Leviathan, so
 * they cut out the heart of its rival THALASSAR, the Tide Father, and sealed it inside the ribcage of a dead titan
 * buried under the Maw Depths. While it beats, the Leviathan sleeps.
 *
 * A deterministic block plan in coordinates RELATIVE to the site (x, z) and absolute y. Pure Java (no Minecraft
 * classes): {@link #main} renders preview PNGs; {@link TitansChest} renders it chunk by chunk.
 * <pre>
 *  THE CHEST       one flooded cavern ~100 x 112 x 56 (the titan lies on its back along z, head to the north):
 *                  11 bone ribs arching over the walls, the sternum along the ceiling, the spine along the floor
 *  THE HEART       hangs at the centre from six ARTERIES and the aorta (flesh tubes with glowing veins = beat lamps)
 *  BLOOD RING      a sunk channel r27..32 round the heart; its current pulses on every beat (AbyssalHeartEntity)
 *  GALVANIC PYLONS four copper coils at r17 - strike them on the beat
 *  EYE OF THALASSAR  the rival's eye in a flesh socket in the north wall, behind bars of bone
 *  THE WOUND       the entrance: the ancients' giant harpoon still stands in the titan's side (east), its shaft
 *                  breaking the surface as a landmark; swim down beside it
 *  THE RELIQUARY   the ancients' shrine behind the south ribs (loot)
 *  THE CHAMBERS    under the floor, sealed: the heart's four chambers (walls of flesh, air-filled) round the VALVE hub,
 *                  joined in a ring by five valve doorways - phase 2 teleports players in (never walked into)
 * </pre>
 */
public final class TitansChestLayout {
    public static final int X0 = -66, X1 = 100, Z0 = -76, Z1 = 76, Y0 = -62, Y1 = 84;
    static final int NX = X1 - X0 + 1, NZ = Z1 - Z0 + 1, NY = Y1 - Y0 + 1;
    public static final int F = -28;                         // the chest floor (first open y)
    static final double RX = 50, RZ = 56, RY = 56;           // the cavern: a half-ellipsoid on the floor
    public static final int HEART_Y = F + 24;                // the heart's feet (hitbox 8 tall -> centre F+28)
    static final int RING_IN = 27, RING_OUT = 32, RING_DEPTH = 4;
    static final int PYLON_R = 17, PYLON_Y = F + 20;
    static final int CH = -48;                               // the chambers' floor

    private final short[] grid = new short[NX * NY * NZ];
    private final BitSet cav = new BitSet(NX * NY * NZ), air = new BitSet(NX * NY * NZ), exempt = new BitSet(NX * NY * NZ);
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Short> ids = new HashMap<>();
    public final Map<Long, String> loot = new HashMap<>();
    // ---- what the Heart needs to know (all relative to the site; see AbyssalHeartEntity#setLair)
    public final int[] heartSpawn = {0, HEART_Y, 0};
    public final int[] eyeSpawn = {0, F + 11, -57};
    public final List<int[]> pylons = new ArrayList<>();         // the GALVANIC_PYLON cores
    public final List<int[]> lamps = new ArrayList<>();          // FLESH_VEIN blocks that flare on the beat
    public final List<double[]> arteries = new ArrayList<>();    // x1,y1,z1,x2,y2,z2 - Vein Lash lanes
    public final List<List<int[]>> valves = new ArrayList<>();   // the doorway cells of each chamber valve
    public final List<int[]> nodes = new ArrayList<>();          // where each chamber's node sits
    public final List<int[]> clotSpots = new ArrayList<>();
    public final List<int[]> hubSpots = new ArrayList<>();       // where phase 2 drops players (the valve hub)
    public final List<int[]> ejectSpots = new ArrayList<>();     // where the rupture throws them back out
    private final IntBinaryOperator terrain;
    private final Random rnd;

    private TitansChestLayout(long seed, IntBinaryOperator terrain) {
        this.terrain = terrain;
        this.rnd = new Random(seed);
    }

    /** Build the plan. {@code terrain} gives the top of the real terrain at a RELATIVE (x, z). */
    public static TitansChestLayout build(long seed, IntBinaryOperator terrain) {
        TitansChestLayout L = new TitansChestLayout(seed, terrain);
        L.carve();
        L.clampToTerrain();
        L.shell();
        L.fill();
        L.decorate();
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

    void put(int x, int y, int z, String block) { if (in(x, y, z)) grid[idx(x, y, z)] = id(block); }

    String at(int x, int y, int z) { int v = get(x, y, z); return v == 0 ? null : palette.get(v - 1); }

    boolean isCav(int x, int y, int z) { return in(x, y, z) && cav.get(idx(x, y, z)); }

    boolean isWater(int x, int y, int z) { String s = at(x, y, z); return s != null && s.equals(WATER); }

    boolean isAir(int x, int y, int z) { String s = at(x, y, z); return s != null && s.equals(AIR); }

    int top(int x, int z) { return terrain.applyAsInt(x, z); }

    void open(int x, int y, int z, boolean dry) {
        if (!in(x, y, z)) return;
        int i = idx(x, y, z);
        cav.set(i);
        if (dry) air.set(i); else air.clear(i);
    }

    static final String WATER = "minecraft:water", AIR = "minecraft:air";
    static final String FLESH = "pixelpirates:living_flesh", VEIN = "pixelpirates:flesh_vein[lit=false]", BONE = "minecraft:bone_block";

    private double noise3(double x, double y, double z) {
        return Math.sin(x * 0.21 + z * 0.13) * 0.5 + Math.sin(z * 0.27 - y * 0.19 + 1.7) * 0.35 + Math.sin(y * 0.31 + x * 0.17 + 4.2) * 0.25;
    }

    /** The cavern's ellipsoid norm (< 1 inside). */
    static double chest(double x, double y, double z) {
        double dx = x / RX, dy = (y - F) / RY, dz = z / RZ;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // =====================================================================================
    // 1. the open spaces
    // =====================================================================================
    static final int[] SOCKET = {0, F + 17, -58};
    static final double[] HARPOON_TIP = {30, F + 13, 30}, HARPOON_END = {96, 82, 46};
    // the chambers: right atrium (NE), right ventricle (SE), left ventricle (SW), left atrium (NW) round the hub
    static final int[][] CHAMBERS = {{15, -15}, {15, 15}, {-15, 15}, {-15, -15}};
    static final String[] CHAMBER_NAMES = {"right atrium", "right ventricle", "left ventricle", "left atrium"};

    private void carve() {
        // THE CHEST: a half-ellipsoid on a flat floor, its surface roughened a little
        for (int x = (int) -RX - 2; x <= RX + 2; x++) for (int z = (int) -RZ - 2; z <= RZ + 2; z++)
            for (int y = F; y <= F + RY + 2; y++)
                if (chest(x, y, z) < 1 + noise3(x, y, z) * 0.015) open(x, y, z, false);
        // the blood ring, sunk into the floor
        for (int x = -RING_OUT; x <= RING_OUT; x++) for (int z = -RING_OUT; z <= RING_OUT; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (r >= RING_IN && r <= RING_OUT) for (int y = F - RING_DEPTH; y < F; y++) open(x, y, z, false);
        }
        // the eye socket in the north wall
        for (int x = -12; x <= 12; x++) for (int y = -11; y <= 11; y++) for (int z = -8; z <= 8; z++) {
            double d = (x / 11.0) * (x / 11.0) + (y / 10.0) * (y / 10.0) + (z / 7.0) * (z / 7.0);
            if (d < 1) open(SOCKET[0] + x, SOCKET[1] + y, SOCKET[2] + z, false);
        }
        // THE WOUND: the tunnel beside the harpoon, from the chest up to the seabed
        double[] a = HARPOON_TIP, b = HARPOON_END;
        double len = dist(a, b);
        for (double t = 0; t <= len; t += 0.6) {
            double f = t / len, cx = a[0] + (b[0] - a[0]) * f, cy = a[1] + (b[1] - a[1]) * f, cz = a[2] + (b[2] - a[2]) * f;
            for (int dx = -6; dx <= 6; dx++) for (int dy = -6; dy <= 6; dy++) for (int dz = -6; dz <= 6; dz++) {
                if (dx * dx + dy * dy + dz * dz > 5.2 * 5.2) continue;
                int X = (int) Math.round(cx + dx), Y = (int) Math.round(cy + dy), Z = (int) Math.round(cz + dz);
                if (!in(X, Y, Z) || Y > Math.min(top(X, Z), 61) || Y < F) continue;
                open(X, Y, Z, false);
                exempt.set(idx(X, Y, Z));
            }
        }
        // THE RELIQUARY behind the south ribs
        for (int x = -7; x <= 7; x++) for (int z = 50; z <= 66; z++) for (int y = F; y <= F + 7; y++) open(x, y, z, false);
        // THE CHAMBERS: the valve hub and four chambers, air-filled, joined in a ring
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) for (int y = CH; y <= CH + 7; y++) {
            double r = Math.sqrt(x * x + z * z);
            if (r <= 5.5 - Math.max(0, y - CH - 4) * 0.9) open(x, y, z, true);
        }
        for (int[] c : CHAMBERS) {
            for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) for (int y = CH; y <= CH + 12; y++) {
                double d = (x / 8.0) * (x / 8.0) + ((y - CH - 3) / 7.0) * ((y - CH - 3) / 7.0) + (z / 8.0) * (z / 8.0);
                if (d < 1 + noise3(x + c[0], y, z + c[1]) * 0.12) open(c[0] + x, y, c[1] + z, true);
            }
            nodes.add(new int[]{c[0], CH, c[1]});
            for (int[] o : new int[][]{{4, 3}, {-4, -3}, {3, -4}, {-3, 4}}) clotSpots.add(new int[]{c[0] + o[0], CH, c[1] + o[1]});
        }
        int[][] ring = {{0, 0}, CHAMBERS[0], CHAMBERS[1], CHAMBERS[2], CHAMBERS[3], {0, 0}};
        for (int i = 0; i + 1 < ring.length; i++) passage(ring[i][0], ring[i][1], ring[i + 1][0], ring[i + 1][1]);
        for (int[] o : new int[][]{{2, 2}, {-2, 2}, {2, -2}, {-2, -2}}) hubSpots.add(new int[]{o[0], CH, o[1]});
        for (int k = 0; k < 4; k++) {
            double ang = Math.PI / 4 + k * Math.PI / 2;
            ejectSpots.add(new int[]{(int) Math.round(Math.cos(ang) * 11), F + 16, (int) Math.round(Math.sin(ang) * 11)});
        }
    }

    static double dist(double[] a, double[] b) {
        return Math.sqrt((b[0] - a[0]) * (b[0] - a[0]) + (b[1] - a[1]) * (b[1] - a[1]) + (b[2] - a[2]) * (b[2] - a[2]));
    }

    /** A 3-wide, 4-tall passage between two chamber centres; the cells across its middle are a VALVE doorway. */
    private void passage(int x1, int z1, int x2, int z2) {
        double dx = x2 - x1, dz = z2 - z1, len = Math.sqrt(dx * dx + dz * dz);
        List<int[]> door = new ArrayList<>();
        for (int x = Math.min(x1, x2) - 3; x <= Math.max(x1, x2) + 3; x++) for (int z = Math.min(z1, z2) - 3; z <= Math.max(z1, z2) + 3; z++) {
            double t = ((x - x1) * dx + (z - z1) * dz) / (len * len);
            if (t < 0 || t > 1) continue;
            double px = x1 + dx * t - x, pz = z1 + dz * t - z;
            if (px * px + pz * pz > 1.6 * 1.6) continue;
            boolean isDoor = Math.abs(t - 0.5) * len < 1.0;
            for (int y = CH; y <= CH + 3; y++) {
                if (!isCav(x, y, z)) open(x, y, z, true);
                if (isDoor) door.add(new int[]{x, y, z});
            }
        }
        valves.add(door);
    }

    // =====================================================================================
    // 2. never within 5 blocks of the real surface (except the wound)
    // =====================================================================================
    private void clampToTerrain() {
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            int t = top(x, z) - 5;
            for (int y = Math.max(Y0, t); y <= Y1; y++) {
                int i = idx(x, y, z);
                if (cav.get(i) && !exempt.get(i)) { cav.clear(i); air.clear(i); }
            }
        }
    }

    // =====================================================================================
    // 3. the 3-block shell: fossil rock round the chest, meat round the chambers; never over the open sea
    // =====================================================================================
    private void shell() {
        BitSet shell = new BitSet(NX * NY * NZ);
        BitSet first = new BitSet(NX * NY * NZ);
        BitSet frontier = (BitSet) cav.clone();
        int[][] nb = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int step = 0; step < 3; step++) {
            BitSet next = new BitSet(NX * NY * NZ);
            for (int i = frontier.nextSetBit(0); i >= 0; i = frontier.nextSetBit(i + 1)) {
                int y = i % NY + Y0, rest = i / NY, z = rest % NZ + Z0, x = rest / NZ + X0;
                for (int[] d : nb) {
                    int X = x + d[0], Y = y + d[1], Z = z + d[2];
                    if (!in(X, Y, Z) || Y > top(X, Z)) continue;             // the sea above the seabed stays sea
                    int j = idx(X, Y, Z);
                    if (!cav.get(j) && !shell.get(j)) { shell.set(j); next.set(j); if (step == 0) first.set(j); }
                }
            }
            frontier = next;
        }
        for (int i = shell.nextSetBit(0); i >= 0; i = shell.nextSetBit(i + 1)) {
            int y = i % NY + Y0, rest = i / NY, z = rest % NZ + Z0, x = rest / NZ + X0;
            grid[i] = id(y < F - 6 ? meat(x, y, z) : first.get(i) ? lining(x, y, z) : rock(x, y, z));
        }
    }

    String rock(int x, int y, int z) {
        double n = noise3(x * 1.7, y * 1.7, z * 1.7);
        if (n > 0.55) return "minecraft:tuff";
        if (n < -0.6) return "pixelpirates:abyssal_slate";
        if ((x * 7 + y * 13 + z * 3) % 31 == 0) return "pixelpirates:soul_barnacle";
        return "minecraft:deepslate";
    }

    /** The inner face of the chest: fossil bone and dripstone, patches of meat still clinging to it. */
    String lining(int x, int y, int z) {
        double n = noise3(x * 0.9 + 3, y * 0.9, z * 0.9);
        if (n > 0.5) return (x * 5 + y * 11 + z * 7) % 13 == 0 ? VEIN : FLESH;
        if (n > 0.2) return "minecraft:calcite";
        if (n < -0.55) return "minecraft:dripstone_block";
        if ((x * 3 + y * 7 + z * 11) % 41 == 0) return "pixelpirates:luminous_vein";
        return rock(x, y, z);
    }

    /** The heart's own walls. */
    String meat(int x, int y, int z) {
        double n = noise3(x * 1.3, y * 1.3, z * 1.3);
        if (n > 0.6) return "minecraft:nether_wart_block";
        if ((x * 7 + y * 5 + z * 3) % 17 == 0) return VEIN;
        return FLESH;
    }

    // =====================================================================================
    // 4. open space -> water / air
    // =====================================================================================
    private void fill() {
        for (int i = cav.nextSetBit(0); i >= 0; i = cav.nextSetBit(i + 1)) grid[i] = id(air.get(i) ? AIR : WATER);
    }

    // =====================================================================================
    // 5. everything built
    // =====================================================================================
    private void decorate() {
        floor();
        ring();
        ribs();
        spine();
        arteries();
        pylons();
        eyeSocket();
        harpoon();
        reliquary();
        chambers();
        scatter();
    }

    void fillB(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) put(x, y, z, b);
    }

    /** Only into water (keeps built things). */
    void pw(int x, int y, int z, String b) { if (isWater(x, y, z)) put(x, y, z, b); }

    void chest(int x, int y, int z, String facing, String table) {
        put(x, y, z, "minecraft:chest[facing=" + facing + ",waterlogged=true]");
        loot.put(key(x, y, z), table);
    }

    void lamp(int x, int y, int z) {
        put(x, y, z, VEIN);
        lamps.add(new int[]{x, y, z});
    }

    boolean nearSocket(int x, int y, int z) {
        double dx = (x - SOCKET[0]) / 13.0, dy = (y - SOCKET[1]) / 12.0, dz = (z - SOCKET[2]) / 9.0;
        return dx * dx + dy * dy + dz * dz < 1;
    }

    // ---------------------------------------------------------------- the floor: fossil slate, meat round the heart
    private void floor() {
        for (int x = (int) -RX; x <= RX; x++) for (int z = (int) -RZ; z <= RZ; z++) {
            if (!isCav(x, F, z) || isCav(x, F - 1, z)) continue;
            double r = Math.sqrt(x * x + z * z), n = noise3(x * 1.9, F, z * 1.9);
            String b = r < 9 ? (n > 0.2 ? VEIN : FLESH)
                    : r < 22 && n > 0.35 ? FLESH
                    : n > 0.55 ? "minecraft:calcite" : n < -0.5 ? "minecraft:gravel" : n < -0.1 ? "pixelpirates:abyssal_slate" : "minecraft:deepslate_tiles";
            put(x, F - 1, z, b);
        }
        // the heart's seat under it: a great shut valve in the floor
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (r <= 4.5) put(x, F - 1, z, "pixelpirates:heart_valve");
            else if (r <= 5.5) put(x, F - 1, z, VEIN);
        }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            lamp((int) Math.round(Math.cos(a) * 7), F - 1, (int) Math.round(Math.sin(a) * 7));
        }
    }

    // ---------------------------------------------------------------- the blood ring
    private void ring() {
        for (int x = -RING_OUT - 1; x <= RING_OUT + 1; x++) for (int z = -RING_OUT - 1; z <= RING_OUT + 1; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (r < RING_IN - 1 || r > RING_OUT + 1) continue;
            for (int y = F - RING_DEPTH - 1; y < F; y++) {
                if (isCav(x, y, z)) continue;
                put(x, y, z, (x * 3 + z * 5 + y) % 11 == 0 ? VEIN : FLESH);         // lined with meat
            }
        }
        for (int k = 0; k < 12; k++) {                                              // lamps in its bed
            double a = k * Math.PI / 6;
            lamp((int) Math.round(Math.cos(a) * 29.5), F - RING_DEPTH - 1, (int) Math.round(Math.sin(a) * 29.5));
        }
    }

    // ---------------------------------------------------------------- 11 ribs over the walls, the sternum along the top
    static final int RIB_STEP = 9;

    private void ribs() {
        for (int rz = -45; rz <= 45; rz += RIB_STEP) {
            for (int x = (int) -RX - 1; x <= RX + 1; x++) for (int y = F; y <= F + RY; y++) for (int z = rz - 1; z <= rz + 1; z++) {
                if (!isWater(x, y, z) || nearSocket(x, y, z)) continue;
                double c = chest(x, y, z);
                double inset = (z == rz ? 4.2 : 3.0) / RX;                          // a rounded bar, thickest in its middle plane
                if (c < 1 - inset) continue;
                put(x, y, z, (x * 7 + y * 3 + z) % 9 == 0 ? "minecraft:calcite" : BONE);
            }
        }
        // the sternum: a long bone plate down the ridge of the ceiling
        for (int z = -48; z <= 48; z++) for (int x = -5; x <= 5; x++) for (int y = F + 44; y <= F + RY; y++) {
            if (!isWater(x, y, z)) continue;
            if (chest(x, y, z) < 1 - (4.5 - Math.abs(x) * 0.5) / RY) continue;
            put(x, y, z, Math.abs(x) == 5 || (z % 7 == 0) ? "minecraft:calcite" : BONE);
        }
    }

    // ---------------------------------------------------------------- the spine along the floor (under the heart it gives way)
    private void spine() {
        for (int z0 = -54; z0 <= 50; z0 += 7) {
            if (Math.abs(z0 + 2) < 9) continue;
            fillB(-3, F, z0, 3, F + 2, z0 + 3, BONE);                                  // the centrum
            fillB(-2, F + 3, z0 + 1, 2, F + 3, z0 + 2, BONE);
            fillB(-1, F + 4, z0 + 1, 1, F + 7, z0 + 2, BONE);                           // the spinous process
            put(0, F + 8, z0 + 1, BONE);
            for (int s = -1; s <= 1; s += 2) {                                           // transverse processes
                fillB(s * 4, F + 1, z0 + 1, s * 7, F + 2, z0 + 2, BONE);
                put(s * 8, F + 1, z0 + 1, "minecraft:calcite");
            }
            fillB(-2, F, z0 + 4, 2, F + 1, z0 + 5, "minecraft:calcite");                // the disc to the next one
        }
    }

    // ---------------------------------------------------------------- six arteries + the aorta: the heart hangs from them
    private void arteries() {
        double hx = 0, hy = HEART_Y + 4.5, hz = 0;
        for (int k = 0; k < 6; k++) {
            double a = Math.PI / 6 + k * Math.PI / 3;
            double[] from = {hx + Math.cos(a) * 4.4, hy + 1.5, hz + Math.sin(a) * 4.4};
            double[] to = {Math.cos(a) * 46, F + 44, Math.sin(a) * 46};
            tube(from, to, 2.4, true);
            arteries.add(new double[]{from[0], from[1], from[2], to[0], to[1], to[2]});
        }
        tube(new double[]{0, HEART_Y + 8, 0}, new double[]{0, F + RY + 2, 0}, 3.0, true);   // the aorta, up into the sternum
        // two great veins down into the floor either side of the seat
        tube(new double[]{2.5, HEART_Y + 1, 0}, new double[]{9, F - 2, 3}, 1.8, false);
        tube(new double[]{-2.5, HEART_Y + 1, 0}, new double[]{-9, F - 2, -3}, 1.8, false);
    }

    /** A flesh tube along a segment; a glowing vessel spirals round it; every few blocks one is a beat lamp. */
    private void tube(double[] a, double[] b, double r, boolean lamps) {
        double len = dist(a, b);
        int lampEvery = 5, n = 0;
        for (double t = 0; t <= len; t += 0.5) {
            double f = t / len, cx = a[0] + (b[0] - a[0]) * f, cy = a[1] + (b[1] - a[1]) * f, cz = a[2] + (b[2] - a[2]) * f;
            double bulge = r * (1 + 0.12 * Math.sin(t * 0.9));
            for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) {
                if (dx * dx + dy * dy + dz * dz > bulge * bulge) continue;
                int X = (int) Math.round(cx + dx), Y = (int) Math.round(cy + dy), Z = (int) Math.round(cz + dz);
                if (!isWater(X, Y, Z)) continue;
                put(X, Y, Z, FLESH);
            }
            // the spiral vessel on its surface
            double ang = t * 0.8;
            int vx = (int) Math.round(cx + Math.cos(ang) * bulge), vy = (int) Math.round(cy - Math.abs(Math.sin(ang)) * bulge * 0.6 - bulge * 0.5),
                    vz = (int) Math.round(cz + Math.sin(ang) * bulge);
            String s = at(vx, vy, vz);
            if (s != null && s.equals(FLESH)) {
                if (lamps && (n++ % lampEvery) == 0 && (t > 3)) lamp(vx, vy, vz);
                else put(vx, vy, vz, VEIN);
            }
        }
    }

    // ---------------------------------------------------------------- four galvanic pylons + the ancients' copper walkway
    private void pylons() {
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            int px = (int) Math.round(Math.cos(a) * PYLON_R), pz = (int) Math.round(Math.sin(a) * PYLON_R);
            fillB(px - 2, F, pz - 2, px + 2, F, pz + 2, "minecraft:waxed_oxidized_cut_copper");
            fillB(px - 1, F + 1, pz - 1, px + 1, F + 2, pz + 1, "minecraft:waxed_oxidized_copper");
            for (int y = F + 3; y < PYLON_Y; y++) {
                put(px, y, pz, y % 3 == 0 ? "minecraft:waxed_exposed_cut_copper" : "minecraft:waxed_oxidized_cut_copper");
                if (y % 4 == 0) for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})       // coil collars
                    pw(px + d[0], y, pz + d[1], "minecraft:waxed_oxidized_cut_copper_slab[type=top,waterlogged=true]");
            }
            put(px, PYLON_Y, pz, "pixelpirates:galvanic_pylon[state=0]");
            put(px, PYLON_Y + 1, pz, "minecraft:lightning_rod[facing=up,waterlogged=true]");
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                pw(px + d[0], PYLON_Y - 1, pz + d[1], "minecraft:waxed_oxidized_cut_copper_stairs[facing=" + (d[0] == 1 ? "west" : d[0] == -1 ? "east" : d[1] == 1 ? "north" : "south") + ",half=top,waterlogged=true]");
            pylons.add(new int[]{px, PYLON_Y, pz});
        }
        // a broken ring walkway linking them, at the coils' height
        for (int x = -PYLON_R - 2; x <= PYLON_R + 2; x++) for (int z = -PYLON_R - 2; z <= PYLON_R + 2; z++) {
            double r = Math.sqrt(x * x + z * z);
            if (Math.abs(r - PYLON_R) > 1.0) continue;
            if (noise3(x * 2.1, 0, z * 2.1) < -0.35) continue;                          // collapsed sections
            pw(x, PYLON_Y - 2, z, "minecraft:waxed_oxidized_cut_copper_slab[type=top,waterlogged=true]");
        }
    }

    // ---------------------------------------------------------------- the Eye of Thalassar behind bars of bone
    private void eyeSocket() {
        for (int x = -13; x <= 13; x++) for (int y = -12; y <= 12; y++) for (int z = -10; z <= 10; z++) {
            int X = SOCKET[0] + x, Y = SOCKET[1] + y, Z = SOCKET[2] + z;
            if (isCav(X, Y, Z)) continue;
            double d = (x / 12.0) * (x / 12.0) + (y / 11.0) * (y / 11.0) + (z / 8.5) * (z / 8.5);
            if (d < 1.15 && in(X, Y, Z) && get(X, Y, Z) != 0) put(X, Y, Z, (X * 3 + Y * 7 + Z) % 9 == 0 ? VEIN : FLESH);   // the socket's meat
        }
        // four bars of bone across the front of the socket (the back ribs)
        int front = SOCKET[2] + 8;
        for (int bx : new int[]{-8, -3, 3, 8})
            for (int y = SOCKET[1] - 10; y <= SOCKET[1] + 10; y++) for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++)
                if (isWater(bx + dx, y, front + dz)) put(bx + dx, y, front + dz, (y % 5 == 0) ? "minecraft:calcite" : BONE);
        for (int x = -10; x <= 10; x++) for (int dz = 0; dz <= 1; dz++) {                // a crossbar top and bottom
            pw(x, SOCKET[1] + 10, front + dz, BONE);
            pw(x, SOCKET[1] - 10, front + dz, BONE);
        }
    }

    // ---------------------------------------------------------------- the ancients' harpoon standing in the wound
    private void harpoon() {
        double[] a = HARPOON_TIP, b = HARPOON_END;
        double len = dist(a, b);
        double ux = (b[0] - a[0]) / len, uy = (b[1] - a[1]) / len, uz = (b[2] - a[2]) / len;
        for (double t = 3; t <= len; t += 0.4) {
            double cx = a[0] + ux * t, cy = a[1] + uy * t, cz = a[2] + uz * t;
            boolean band = ((int) t) % 9 == 0;
            double r = band ? 1.9 : 1.3;
            for (int dx = -2; dx <= 2; dx++) for (int dy = -2; dy <= 2; dy++) for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dy * dy + dz * dz > r * r) continue;
                int X = (int) Math.round(cx + dx), Y = (int) Math.round(cy + dy), Z = (int) Math.round(cz + dz);
                if (!in(X, Y, Z)) continue;
                String s = at(X, Y, Z);
                boolean open = s == null ? Y > top(X, Z) : (s.equals(WATER) || s.equals(AIR));
                if (!open && s != null && !s.startsWith("minecraft:deepslate") && !s.startsWith("minecraft:tuff")) continue;
                put(X, Y, Z, band ? "minecraft:waxed_weathered_copper" : ((X + Y + Z) % 5 == 0 ? "minecraft:stripped_dark_oak_wood" : "minecraft:dark_oak_wood"));
            }
        }
        // the head, buried in the titan: a broad blade and two barbs raking back
        for (double t = -3; t <= 3; t += 0.4) {
            double w = (3 - Math.abs(t + 0.5)) * 0.9;
            for (double s = -w; s <= w; s += 0.5) {
                int X = (int) Math.round(a[0] + ux * t - uz * s), Y = (int) Math.round(a[1] + uy * t), Z = (int) Math.round(a[2] + uz * t + ux * s);
                put(X, Y, Z, "minecraft:iron_block");
            }
        }
        for (int s = -1; s <= 1; s += 2) for (double k = 0; k <= 4; k += 0.5) {
            int X = (int) Math.round(a[0] + ux * (3 + k) - uz * s * (2.5 + k * 0.4)), Y = (int) Math.round(a[1] + uy * (3 + k) - k * 0.3),
                    Z = (int) Math.round(a[2] + uz * (3 + k) + ux * s * (2.5 + k * 0.4));
            put(X, Y, Z, "minecraft:iron_block");
        }
        // rope still lashed round the shaft where it breaks the surface, frayed ends trailing
        for (double t = len - 30; t <= len - 18; t += 0.35) {
            double ang = t * 2.2, cx = a[0] + ux * t, cy = a[1] + uy * t, cz = a[2] + uz * t;
            int X = (int) Math.round(cx + Math.cos(ang) * 1.9), Y = (int) Math.round(cy + Math.sin(ang) * 1.2), Z = (int) Math.round(cz + Math.sin(ang) * 1.9);
            String s = at(X, Y, Z);
            if (s == null ? Y > top(X, Z) : (s.equals(WATER) || s.equals(AIR))) put(X, Y, Z, "minecraft:brown_wool");
        }
    }

    // ---------------------------------------------------------------- the ancients' reliquary (south)
    private void reliquary() {
        fillB(-7, F - 1, 50, 7, F - 1, 66, "minecraft:dark_prismarine");
        for (int z = 52; z <= 64; z += 4) for (int x : new int[]{-6, 6}) fillB(x, F, z, x, F + 6, z, "minecraft:prismarine_bricks");
        for (int z = 50; z <= 66; z++) for (int x = -7; x <= 7; x++) if (isWater(x, F + 7, z)) put(x, F + 7, z, "minecraft:dark_prismarine");
        for (int x = -2; x <= 2; x++) for (int z = 60; z <= 64; z++) put(x, F, z, "minecraft:prismarine_bricks");      // the altar
        put(0, F + 1, 62, "minecraft:conduit[waterlogged=true]");
        chest(-2, F + 1, 61, "north", "pixelpirates:chests/heart_hoard");
        chest(2, F + 1, 61, "north", "pixelpirates:chests/phase5_treasure");
        chest(-5, F, 57, "east", "pixelpirates:chests/phase5_common");
        chest(5, F, 57, "west", "pixelpirates:chests/phase5_common");
        for (int z = 52; z <= 64; z += 4) for (int x : new int[]{-5, 5}) put(x, F + 5, z, "minecraft:sea_lantern");
        // a mural of the sealing: the heart (vein) held in a cage of bone, on the back wall
        String[] art = {
                "..BBBBBBB..",
                ".B..VVV..B.",
                "B..VVVVV..B",
                "B..VVVVV..B",
                ".B..VVV..B.",
                "..B..V..B..",
                "...BBBBB..."};
        for (int r = 0; r < art.length; r++) for (int c = 0; c < art[r].length(); c++) {
            char ch = art[r].charAt(c);
            put(c - 5, F + 6 - r, 67, ch == 'B' ? BONE : ch == 'V' ? "pixelpirates:flesh_vein[lit=true]" : "minecraft:prismarine_bricks");
        }
    }

    // ---------------------------------------------------------------- the chambers: walls of flesh
    private void chambers() {
        // floor: meat, pooled blood (redstone dust) and the odd vein; hanging chordae (weeping vines)
        for (int x = -26; x <= 26; x++) for (int z = -26; z <= 26; z++) {
            for (int y = CH; y <= CH + 13; y++) {
                if (!isAir(x, y, z)) continue;
                if (!isAir(x, y - 1, z) && y == CH && (x * 13 + z * 7) % 9 == 0 && !isDoorCell(x, y, z)) put(x, y, z, "minecraft:redstone_wire");
                if (!isAir(x, y + 1, z) && at(x, y + 1, z) != null && !isDoorCell(x, y, z) && (x * 5 + z * 11 + y) % 7 == 0) {
                    int len = 2 + Math.floorMod(x * 3 + z, 4);
                    for (int k = 0; k < len && isAir(x, y - k, z) && y - k > CH + 1; k++)
                        put(x, y - k, z, k == len - 1 || !isAir(x, y - k - 1, z) || y - k - 1 <= CH + 1 ? "minecraft:weeping_vines" : "minecraft:weeping_vines_plant");
                }
            }
        }
        // trabeculae: fleshy columns in each chamber; the node's nest of veins; beat lamps in the walls
        for (int i = 0; i < CHAMBERS.length; i++) {
            int[] c = CHAMBERS[i];
            for (int[] o : new int[][]{{5, -2}, {-5, 2}, {-2, -5}, {2, 5}}) {
                int X = c[0] + o[0], Z = c[1] + o[1];
                for (int y = CH; y <= CH + 12 && isAir(X, y, Z); y++) put(X, y, Z, y % 4 == 1 ? VEIN : FLESH);
            }
            for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
                double r = Math.sqrt(x * x + z * z);
                if (r >= 1.5 && r <= 2.5) put(c[0] + x, CH - 1, c[1] + z, VEIN);
                else if (r < 1.5) put(c[0] + x, CH - 1, c[1] + z, "minecraft:shroomlight");
            }
            int placed = 0;
            for (int x = -9; x <= 9 && placed < 5; x += 3) for (int z = -9; z <= 9 && placed < 5; z += 3) {
                int X = c[0] + x, Z = c[1] + z, Y = CH + 4;
                String s = at(X, Y, Z);
                if (s != null && (s.equals(FLESH) || s.equals(VEIN)) && touchesAir(X, Y, Z)) { lamp(X, Y, Z); placed++; }
            }
        }
        // the hub: the underside of the great valve in its ceiling, lamps round the wall
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (x * x + z * z <= 9) for (int yy = CH + 5; yy <= CH + 9; yy++) if (!isAir(x, yy, z)) { put(x, yy, z, "pixelpirates:heart_valve"); break; }
        }
        for (int k = 0; k < 6; k++) {
            double a = k * Math.PI / 3;
            int X = (int) Math.round(Math.cos(a) * 6), Z = (int) Math.round(Math.sin(a) * 6);
            if (!isAir(X, CH + 2, Z)) lamp(X, CH + 2, Z);
        }
    }

    boolean isDoorCell(int x, int y, int z) {
        for (List<int[]> d : valves) for (int[] c : d) if (c[0] == x && c[1] == y && c[2] == z) return true;
        return false;
    }

    boolean touchesAir(int x, int y, int z) {
        return isAir(x + 1, y, z) || isAir(x - 1, y, z) || isAir(x, y, z + 1) || isAir(x, y, z - 1);
    }

    // ---------------------------------------------------------------- details: shroomlight on the ribs, dead divers, kelp, pickles
    private void scatter() {
        for (int i = 0; i < 900; i++) {
            int x = rnd.nextInt((int) (RX * 2)) - (int) RX, z = rnd.nextInt((int) (RZ * 2)) - (int) RZ, y = F + rnd.nextInt((int) RY);
            if (!isWater(x, y, z)) continue;
            String nb = neighbourSolid(x, y, z);
            if (nb == null) continue;
            if (nb.equals(BONE) && rnd.nextInt(3) == 0) put(x, y, z, "minecraft:shroomlight");
            else if (nb.equals(FLESH) && rnd.nextInt(5) == 0) put(x, y, z, "minecraft:nether_wart_block");
        }
        // the floor: kelp forests at the edges, sea pickles, the bones and helmets of divers who came before
        for (int i = 0; i < 700; i++) {
            int x = rnd.nextInt((int) (RX * 2)) - (int) RX, z = rnd.nextInt((int) (RZ * 2)) - (int) RZ;
            double r = Math.sqrt(x * x + z * z);
            if (r < 12 || !isWater(x, F, z) || isWater(x, F - 1, z)) continue;
            String below = at(x, F - 1, z);
            if (below == null || below.contains("valve") || below.contains("vein")) continue;
            int roll = rnd.nextInt(20);
            if (roll < 7 && r > 36) {
                int h = 3 + rnd.nextInt(9);
                for (int k = 0; k < h && isWater(x, F + k + 1, z); k++) put(x, F + k, z, k == h - 1 ? "minecraft:kelp[age=20]" : "minecraft:kelp_plant");
            } else if (roll < 10) put(x, F, z, "minecraft:sea_pickle[pickles=" + (1 + rnd.nextInt(4)) + ",waterlogged=true]");
            else if (roll < 12) put(x, F, z, "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
            else if (roll < 13) {                                                       // a dead diver: skull, ribs, his lead weight
                put(x, F, z, "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
                pw(x + 1, F, z, "minecraft:bone_block[axis=x]");
                pw(x + 1, F, z + 1, "minecraft:chain[axis=x,waterlogged=true]");
                pw(x + 2, F, z + 1, "minecraft:anvil[facing=north]");
            } else if (roll < 15) put(x, F, z, "minecraft:bone_block[axis=" + (rnd.nextBoolean() ? "x" : "z") + "]");
        }
    }

    String neighbourSolid(int x, int y, int z) {
        for (int[] d : new int[][]{{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
            String s = at(x + d[0], y + d[1], z + d[2]);
            if (s != null && !s.equals(WATER) && !s.equals(AIR)) return s;
        }
        return null;
    }

    // =====================================================================================
    // preview: floor plans and cross-sections (run standalone)
    // =====================================================================================
    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "chest";
        long t0 = System.currentTimeMillis();
        TitansChestLayout L = build(4242L, (x, z) -> 60 + (int) (2 * Math.sin(x * 0.03) * Math.cos(z * 0.025)));
        System.out.println("built in " + (System.currentTimeMillis() - t0) + " ms, palette " + L.palette.size() + ", pylons " + L.pylons.size()
                + ", lamps " + L.lamps.size() + ", valves " + L.valves.size() + " (" + L.valves.stream().mapToInt(List::size).sum() + " cells)"
                + ", nodes " + L.nodes.size() + ", loot " + L.loot.size());
        for (String p : L.palette) System.out.println("  " + p);
        int[] levels = {F - 2, F, F + 2, F + 12, PYLON_Y, HEART_Y + 4, F + 40, CH, CH + 2, CH + 6};
        for (int yl : levels) L.plan(yl, out + "_plan_y" + yl + ".png");
        L.slice(true, 0, out + "_slice_x0.png");
        L.slice(false, 0, out + "_slice_z0.png");
        L.slice(false, -8, out + "_slice_z-8.png");
        L.slice(false, 15, out + "_slice_z15.png");
    }

    static int color(String s) {
        if (s == null) return 0x101010;
        if (s.contains("water")) return 0x2a5ad8;
        if (s.equals(AIR)) return 0xd8d0c0;
        if (s.contains("galvanic")) return 0x40ff60;
        if (s.contains("heart_valve")) return 0x600010;
        if (s.contains("flesh_vein")) return 0xff8030;
        if (s.contains("living_flesh") || s.contains("wart")) return 0x9a2028;
        if (s.contains("copper") || s.contains("lightning")) return 0x3a8a72;
        if (s.contains("terracotta") || s.contains("mushroom") || s.contains("brain")) return 0xe07aa0;
        if (s.contains("bone") || s.contains("calcite") || s.contains("skull")) return 0xf0ecd8;
        if (s.contains("chest") || s.contains("conduit")) return 0xe0a030;
        if (s.contains("dark_oak") || s.contains("wool")) return 0x6a4a2a;
        if (s.contains("iron")) return 0xc0c4c8;
        if (s.contains("prismarine") || s.contains("lantern")) return 0x5ac0b0;
        if (s.contains("shroomlight")) return 0xffb040;
        if (s.contains("kelp") || s.contains("pickle")) return 0x3a7a3a;
        if (s.contains("redstone") || s.contains("vines")) return 0xd01010;
        return 0x2e3036;
    }

    void plan(int y, String file) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(NX * 2, NZ * 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            String s = at(x, y, z);
            int c = s == null ? (y <= top(x, z) ? 0x181818 : 0x0a2040) : color(s);
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
            int c = s == null ? (y <= top(x, z) ? 0x181818 : (y <= 62 ? 0x0a2040 : 0x80a0c0)) : color(s);
            for (int p = 0; p < 2; p++) for (int q = 0; q < 2; q++) img.setRGB(a * 2 + p, (Y1 - y) * 2 + q, c);
        }
        javax.imageio.ImageIO.write(img, "png", new java.io.File(file));
    }
}
