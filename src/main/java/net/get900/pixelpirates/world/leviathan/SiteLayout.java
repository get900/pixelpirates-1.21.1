package net.get900.pixelpirates.world.leviathan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntBinaryOperator;

/**
 * Shared kit for the Leviathan's five sites (LeviathanRoute): a palette-indexed block plan in coordinates RELATIVE to
 * the site centre (x, z) and absolute y, plus what the site owns of the natural terrain. Pure Java (no Minecraft
 * classes) so every site has a {@code main()} preview. 0 = leave the world alone.
 *
 * Terrain: {@link #ground} reconciles the natural surface ({@code terrain}) with the designed one - rock where the design
 * is higher, water/air where it is lower - so a site only writes what actually changes. {@link #clearAbove} marks
 * columns whose natural rock towers above the plan (the Pillar Sea's pillars): the renderer carves them to the sky.
 */
public abstract class SiteLayout {
    public static final int SEA = 62;                      // the top water block (sea level 63)
    public final int R, X0, X1, Z0, Z1, Y0, Y1;
    final int NX, NY, NZ;
    private final short[] grid;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Short> ids = new HashMap<>();
    public final Map<Long, String> loot = new HashMap<>();
    /** Per column (x, z): carve everything natural from this y up to the natural top (Integer.MAX_VALUE = no). */
    private final int[] clear;
    protected final IntBinaryOperator terrain;
    protected final Random rnd;

    protected SiteLayout(int radius, int y0, int y1, long seed, IntBinaryOperator terrain) {
        R = radius; X0 = -radius - 6; X1 = radius + 6; Z0 = -radius - 6; Z1 = radius + 6; Y0 = y0; Y1 = y1;
        NX = X1 - X0 + 1; NY = Y1 - Y0 + 1; NZ = Z1 - Z0 + 1;
        grid = new short[NX * NY * NZ];
        clear = new int[NX * NZ];
        java.util.Arrays.fill(clear, Integer.MAX_VALUE);
        this.terrain = terrain;
        this.rnd = new Random(seed);
    }

    // =====================================================================================
    // grid
    // =====================================================================================
    int idx(int x, int y, int z) { return ((x - X0) * NZ + (z - Z0)) * NY + (y - Y0); }

    public boolean in(int x, int y, int z) { return x >= X0 && x <= X1 && y >= Y0 && y <= Y1 && z >= Z0 && z <= Z1; }

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

    public void put(int x, int y, int z, String block) { if (in(x, y, z)) grid[idx(x, y, z)] = id(block); }

    public void erase(int x, int y, int z) { if (in(x, y, z)) grid[idx(x, y, z)] = 0; }

    public String at(int x, int y, int z) { int v = get(x, y, z); return v == 0 ? null : palette.get(v - 1); }

    /** The natural terrain's top solid y at (x, z). */
    public int top(int x, int z) { return terrain.applyAsInt(x, z); }

    /** What will stand at (x,y,z): the plan, or the untouched world (rock below the natural top, water/air above). */
    public String world(int x, int y, int z) {
        String s = at(x, y, z);
        if (s != null) return s;
        if (y <= top(x, z) && y < clearFrom(x, z)) return "minecraft:stone";
        return y <= SEA ? WATER : AIR;
    }

    public boolean open(int x, int y, int z) { String s = world(x, y, z); return s.equals(AIR) || s.equals(WATER); }

    public boolean isAir(int x, int y, int z) { return world(x, y, z).equals(AIR); }

    public boolean isWater(int x, int y, int z) { return world(x, y, z).equals(WATER); }

    public int clearFrom(int x, int z) { return x < X0 || x > X1 || z < Z0 || z > Z1 ? Integer.MAX_VALUE : clear[(x - X0) * NZ + (z - Z0)]; }

    /** Carve this column's natural rock from y up to its (possibly sky-high) natural top. */
    public void clearAbove(int x, int z, int y) {
        if (x < X0 || x > X1 || z < Z0 || z > Z1) return;
        int i = (x - X0) * NZ + (z - Z0);
        clear[i] = Math.min(clear[i], y);
        for (int yy = Math.max(y, Y0); yy <= Math.min(Y1, top(x, z)); yy++) if (get(x, yy, z) == 0) put(x, yy, z, yy <= SEA ? WATER : AIR);
    }

    static final String WATER = "minecraft:water", AIR = "minecraft:air";

    // =====================================================================================
    // shaping helpers
    // =====================================================================================
    protected double noise(double x, double y, double z) {
        return Math.sin(x * 0.21 + z * 0.13) * 0.5 + Math.sin(z * 0.27 - y * 0.19 + 1.7) * 0.35 + Math.sin(y * 0.31 + x * 0.17 + 4.2) * 0.25;
    }

    static double smooth(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    /**
     * Make the ground at (x, z) end at {@code floor} with {@code topBlock} on top and {@code body} below it (down to
     * {@code depth} blocks, or to the natural rock). Where the natural ground is higher it is carved (water up to the
     * sea, air above - pillars and all, via clearAbove).
     */
    public void ground(int x, int z, int floor, String topBlock, String body, int depth) {
        int n = top(x, z);
        if (n > floor) clearAbove(x, z, floor + 1);
        // re-skin the top `depth` blocks, and fill any gap down to the natural rock (no floating ground)
        for (int y = Math.max(Y0, Math.min(n + 1, floor - depth)); y <= floor; y++) put(x, y, z, y == floor ? topBlock : body);
    }

    /** Design floor blended back to the natural surface over the last {@code band} blocks of the site. */
    public int blend(int x, int z, int design, double band) {
        double d = Math.sqrt(x * x + z * z);
        double t = smooth((d - (R - band)) / band);
        return (int) Math.round(design + (top(x, z) - design) * t);
    }

    public void fill(int x1, int y1, int z1, int x2, int y2, int z2, String b) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) put(x, y, z, b);
    }

    /** Only into open (air or water) cells. */
    public void po(int x, int y, int z, String b) { if (in(x, y, z) && open(x, y, z)) put(x, y, z, b); }

    public void chest(int x, int y, int z, String facing, String table) {
        boolean wet = y <= SEA && isWater(x, y, z);
        put(x, y, z, "minecraft:chest[facing=" + facing + ",waterlogged=" + wet + "]");
        loot.put(key(x, y, z), "pixelpirates:" + table);
    }

    public void barrel(int x, int y, int z, String table) {
        put(x, y, z, "minecraft:barrel[facing=up]");
        loot.put(key(x, y, z), "pixelpirates:" + table);
    }

    /** A whole bed (foot at x,z; the head one block toward `facing`: n/s/e/w) - a lone half pops off on the first update. */
    public void bed(int x, int y, int z, char facing, String color) {
        String f = switch (facing) { case 'n' -> "north"; case 's' -> "south"; case 'e' -> "east"; default -> "west"; };
        int hx = x + (facing == 'e' ? 1 : facing == 'w' ? -1 : 0), hz = z + (facing == 's' ? 1 : facing == 'n' ? -1 : 0);
        put(x, y, z, "minecraft:" + color + "_bed[facing=" + f + ",part=foot]");
        put(hx, y, hz, "minecraft:" + color + "_bed[facing=" + f + ",part=head]");
    }

    /** A block that sits in water at or below the sea gets waterlogged=true (only for states that have it). */
    public String wet(int y, String state) {
        if (y > SEA || !state.endsWith("]")) return state;
        return state.substring(0, state.length() - 1) + ",waterlogged=true]";
    }

    // =====================================================================================
    // the building kit
    // =====================================================================================
    /** A timber building: floor at y, walls h tall, a pitched roof along x; door on the given side ('n','s','e','w'). */
    public void house(int x1, int z1, int x2, int z2, int y, int h, String wall, String frame, String roofStairs, String roofSlab, char door) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            put(x, y - 1, z, frame.contains("log") ? "minecraft:spruce_planks" : frame);
            boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
            boolean corner = (x == x1 || x == x2) && (z == z1 || z == z2);
            for (int yy = y; yy < y + h; yy++) put(x, yy, z, !edge ? AIR : corner ? frame : (yy == y + 1 && (x + z) % 3 == 0 && !corner) ? "minecraft:glass_pane" : wall);
        }
        // door
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2;
        int[] d = switch (door) { case 'n' -> new int[]{mx, z1}; case 's' -> new int[]{mx, z2}; case 'e' -> new int[]{x2, mz}; default -> new int[]{x1, mz}; };
        put(d[0], y, d[1], AIR); put(d[0], y + 1, d[1], AIR);
        // pitched roof along x (ridge over mz)
        int half = (z2 - z1) / 2 + 1;
        for (int k = 0; k <= half; k++) {
            int yy = y + h + k;
            for (int x = x1 - 1; x <= x2 + 1; x++) {
                int za = z1 - 1 + k, zb = z2 + 1 - k;
                if (za > zb) continue;
                if (za == zb || za + 1 == zb) { put(x, yy, za, roofSlab); put(x, yy, zb, roofSlab); continue; }
                put(x, yy, za, roofStairs + "[facing=south,half=bottom]");
                put(x, yy, zb, roofStairs + "[facing=north,half=bottom]");
                if (x == x1 - 1 || x == x2 + 1) continue;
                if (x == x1 || x == x2) for (int z = za + 1; z < zb; z++) put(x, yy, z, wall);           // gable ends
            }
        }
        put(mx, y + h - 1, mz, "minecraft:lantern[hanging=true]");
    }

    /** A round stone tower: centre (cx, cz), radius r, from y0 to y1 hollow, with a floor every `storey`. */
    public void tower(int cx, int cz, int r, int y0, int y1, String wall, int storey) {
        for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > r + 0.5) continue;
            for (int y = y0; y <= y1; y++) {
                boolean shell = d > r - 0.5;
                boolean floor = (y - y0) % storey == 0;
                put(cx + x, y, cz + z, shell ? wall : floor ? "minecraft:spruce_planks" : AIR);
            }
        }
    }

    /**
     * A ship's hull (wrecks + the fleets at anchor): length L along `yaw` (radians), beam W, keel at y, deck at y + D.
     * `tilt` lifts the bow (blocks per block), `broken` smashes holes, `masts` = mast count (0 = dismasted).
     */
    public void hull(int cx, int cz, int y, int L, int W, int D, double yaw, double tilt, double broken, int masts, boolean sails) {
        double cs = Math.cos(yaw), sn = Math.sin(yaw);
        String plank = broken > 0.3 ? "minecraft:dark_oak_planks" : "minecraft:spruce_planks";
        for (double a = -L / 2.0; a <= L / 2.0; a += 0.5) {
            double t = (a + L / 2.0) / L;                                          // 0 stern .. 1 bow
            double beam = W / 2.0 * Math.sqrt(Math.max(0, 1 - Math.pow(Math.abs(t - 0.45) / 0.55, 3)));
            int lift = (int) Math.round(a * tilt);
            for (double b = -beam; b <= beam; b += 0.5) {
                int x = (int) Math.round(cx + a * cs - b * sn), z = (int) Math.round(cz + a * sn + b * cs);
                double side = Math.abs(b) / Math.max(0.5, beam);
                int bottom = y + (int) Math.round(D * side * side * 0.8);
                for (int yy = bottom; yy <= y + D; yy++) {
                    int Y = yy + lift;
                    boolean skin = Math.abs(Math.abs(b) - beam) < 0.6 || yy == bottom || yy == y + D;
                    if (rnd.nextDouble() < broken * (yy == y + D ? 0.6 : 0.25)) { if (skin && Y <= SEA) put(x, Y, z, WATER); continue; }
                    if (skin) put(x, Y, z, yy == y + D ? plank : ((yy - bottom) % 3 == 1 ? "minecraft:stripped_dark_oak_log[axis=y]" : plank));
                    else put(x, Y, z, Y <= SEA ? WATER : AIR);
                }
            }
        }
        for (int m = 0; m < masts; m++) {
            double a = -L / 2.0 + L * (m + 1.0) / (masts + 1);
            int x = (int) Math.round(cx + a * cs), z = (int) Math.round(cz + a * sn), base = y + D + (int) Math.round(a * tilt);
            int mh = broken > 0.3 ? 4 + rnd.nextInt(6) : 14;
            for (int k = 1; k <= mh; k++) put(x, base + k, z, "minecraft:spruce_log[axis=y]");
            if (sails && mh >= 10)
                for (int k = 4; k <= mh - 2; k++) for (int s = -3; s <= 3; s++)
                    put((int) Math.round(x - s * sn), base + k, (int) Math.round(z + s * cs), broken > 0 && rnd.nextDouble() < broken ? AIR : "minecraft:white_wool");
        }
    }

    // =====================================================================================
    // ruin kit (the ports after the Leviathan passes)
    // =====================================================================================
    /** A crescent BITE: everything within radius r of (cx, cz) between y0 and y1 goes (water below the sea, air above). */
    public void bite(int cx, int cz, double r, int y0, int y1) {
        for (int x = (int) (cx - r); x <= cx + r; x++) for (int z = (int) (cz - r); z <= cz + r; z++) {
            double d = Math.sqrt((x - cx) * (x - cx) + (z - cz) * (z - cz));
            if (d > r + noise(x, 0, z) * 1.5) continue;
            for (int y = y0; y <= y1; y++) if (get(x, y, z) != 0 || y > top(x, z)) put(x, y, z, y <= SEA ? WATER : AIR);
        }
    }

    /** Knock a building down: roofs gone, walls broken to stumps, rubble and char inside the box. */
    public void collapse(int x1, int y1, int z1, int x2, int y2, int z2, double keep) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            int stump = y1 + (int) Math.round((y2 - y1) * keep * (0.4 + 0.6 * Math.abs(noise(x * 1.3, 0, z * 1.3))));
            for (int y = y1; y <= y2; y++) {
                String s = at(x, y, z);
                if (s == null || s.equals(WATER)) continue;
                if (y > stump) { put(x, y, z, y <= SEA ? WATER : AIR); continue; }
                if (s.contains("planks") || s.contains("log") || s.contains("wool")) put(x, y, z, rnd.nextInt(3) == 0 ? "pixelpirates:charred_planks" : s.contains("log") ? "minecraft:stripped_dark_oak_log[axis=y]" : "minecraft:dark_oak_planks");
                else if (s.contains("glass") || s.contains("lantern") || s.contains("bell")) put(x, y, z, AIR);
            }
            if (rnd.nextInt(6) == 0 && isAir(x, y1, z) && !isAir(x, y1 - 1, z))
                put(x, y1, z, rnd.nextBoolean() ? "minecraft:cobblestone" : "minecraft:campfire[lit=true]");
        }
    }

    /** Blood stains + scattered barrels / planks over the open ground in a box. */
    public void strew(int x1, int z1, int x2, int z2, int yFrom, int yTo, int n) {
        for (int i = 0; i < n; i++) {
            int x = x1 + rnd.nextInt(x2 - x1 + 1), z = z1 + rnd.nextInt(z2 - z1 + 1);
            for (int y = yTo; y >= yFrom; y--) {
                if (!isAir(x, y, z) || isAir(x, y - 1, z) || isWater(x, y - 1, z)) continue;
                int k = rnd.nextInt(10);
                if (k < 4) put(x, y - 1, z, "minecraft:red_terracotta");
                else if (k < 6) put(x, y, z, "minecraft:barrel[facing=" + (rnd.nextBoolean() ? "east" : "north") + "]");
                else if (k < 8) put(x, y, z, "minecraft:dark_oak_slab[type=bottom]");
                else put(x, y, z, "minecraft:skeleton_skull[rotation=" + rnd.nextInt(16) + "]");
                break;
            }
        }
    }

    // =====================================================================================
    // preview
    // =====================================================================================
    protected int color(String s) {
        if (s == null) return 0x101010;
        if (s.contains("water")) return 0x2a5ad8;
        if (s.equals(AIR)) return 0xd8d0c0;
        if (s.contains("chain")) return 0x6a7078;
        if (s.contains("lantern") || s.contains("campfire") || s.contains("fire")) return 0xffb040;
        if (s.contains("chest") || s.contains("barrel")) return 0xe0a030;
        if (s.contains("pixelpirates:") && !s.contains("planks") && !s.contains("slate") && !s.contains("vein")) return 0xff40ff;
        if (s.contains("bone") || s.contains("calcite")) return 0xf0ecd8;
        if (s.contains("planks") || s.contains("log") || s.contains("stairs") || s.contains("slab") || s.contains("fence")) return 0x7a5a32;
        if (s.contains("wool")) return 0xf0f0f0;
        if (s.contains("brick") || s.contains("polished") || s.contains("tiles")) return 0x8a8e98;
        if (s.contains("sand") || s.contains("gravel")) return 0xd8c890;
        if (s.contains("terracotta")) return 0xa03028;
        if (s.contains("grass") || s.contains("moss") || s.contains("leaves")) return 0x4a8a3a;
        if (s.contains("obsidian") || s.contains("basalt") || s.contains("blackstone")) return 0x2a2230;
        return 0x5a5e66;
    }

    public void plan(int y, String file) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(NX * 2, NZ * 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            int c = color(world(x, y, z));
            if (world(x, y, z).equals(AIR)) for (int k = 1; k <= 3; k++) { String u = world(x, y - k, z); if (!u.equals(AIR)) { c = color(u) & 0xbfbfbf; break; } }
            for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) img.setRGB((x - X0) * 2 + a, (z - Z0) * 2 + b, c);
        }
        javax.imageio.ImageIO.write(img, "png", new java.io.File(file));
    }

    /** Top-down view of the tallest non-air block in every column (a map). */
    public void map(String file, int yMax) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(NX * 2, NZ * 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            int c = 0;
            boolean wet = false;
            for (int y = yMax; y >= Y0; y--) {
                String s = world(x, y, z);
                if (s.equals(AIR)) continue;
                if (s.equals(WATER)) { wet = true; continue; }                  // look through the sea to what is under it
                c = color(s);
                double shade = 0.45 + 0.55 * Math.max(0, Math.min(1, (y + 60) / 160.0));
                c = ((int) (((c >> 16) & 255) * shade) << 16) | ((int) (((c >> 8) & 255) * shade) << 8) | (int) ((c & 255) * shade);
                if (wet) c = ((((c >> 16) & 255) * 5 / 8) << 16) | ((((c >> 8) & 255) * 6 / 8) << 8) | Math.min(255, (c & 255) + 60);
                break;
            }
            for (int a = 0; a < 2; a++) for (int b = 0; b < 2; b++) img.setRGB((x - X0) * 2 + a, (z - Z0) * 2 + b, c);
        }
        javax.imageio.ImageIO.write(img, "png", new java.io.File(file));
    }

    public void slice(boolean alongZ, int at, String file) throws Exception {
        int w = alongZ ? NZ : NX;
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w * 2, NY * 2, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int a = 0; a < w; a++) for (int y = Y0; y <= Y1; y++) {
            int x = alongZ ? at : X0 + a, z = alongZ ? Z0 + a : at;
            String s = world(x, y, z);
            int c = s.equals(AIR) ? 0x80a0c0 : color(s);
            for (int p = 0; p < 2; p++) for (int q = 0; q < 2; q++) img.setRGB(a * 2 + p, (Y1 - y) * 2 + q, c);
        }
        javax.imageio.ImageIO.write(img, "png", new java.io.File(file));
    }
}
