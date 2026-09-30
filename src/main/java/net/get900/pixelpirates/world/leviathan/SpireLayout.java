package net.get900.pixelpirates.world.leviathan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * LAIR 3 - THE DROWNING SPIRE (Leviathan phase 3, "The Unmaking"): a remote caldera atoll with an ancient watchtower -
 * the Spire - rising out of its lagoon. Until the Leviathan arrives it is just a spire; then the lagoon becomes THE
 * MAELSTROM (LeviathanEntity: whirlpool, the channel seals, the Last Tide rises up the Spire).
 * <pre>
 *   THE LAGOON       r 50, floor y 40 - the arena; the Last Tide raises it (contained by the caldera) toward the Spire top
 *   THE CALDERA      a rock wall r 50-62 up to y 104, one CHANNEL through it (facing Brightwater) under a SEA ARCH;
 *                    the arch collapses into the channel when the Leviathan arrives (sealCells) and clears when it dies
 *   THE SPIRE        the ancients' last watchtower, r 7 -> 5, a helical stair round it from the water to the top
 *   TIDE BELLS       four balconies up its side, each with a TIDE BELL - silence them to slow the tide
 *   THE CROWN        the top platform (y 100): THE BANE (the ancients' harpoon ballista) and the WATCHERS' HORN
 *   THE OUTER STAIR  cut into the caldera's outer face, so latecomers can climb over the rim and drop in
 * </pre>
 */
public class SpireLayout extends SiteLayout {
    public static final int LAGOON = 50, WALL = 62, FLOOR = 40, WALL_TOP = 104, TOP = 100, TIDE_MAX = 99;
    public final int[] spawn;
    public final int[] ballista = {0, TOP + 1, -4};
    public final int[] horn = {0, TOP + 1, 4};
    public final List<int[]> bells = new ArrayList<>();
    public final List<int[]> sealCells = new ArrayList<>();       // the channel, filled when the Leviathan seals it in
    private final double entry;                                    // the channel's bearing (radians, site-relative)

    public SpireLayout(long seed, IntBinaryOperator terrain, double entryAngle) {
        super(72, 0, 118, seed, terrain);
        this.entry = entryAngle;
        spawn = new int[]{(int) Math.round(Math.cos(entry + Math.PI) * 30), FLOOR + 6, (int) Math.round(Math.sin(entry + Math.PI) * 30)};
        shape();
        arch();
        spire();
        outerStair();
        rimRuins();
    }

    boolean inChannel(int x, int z) {
        double a = Math.atan2(z, x) - entry;
        a = Math.atan2(Math.sin(a), Math.cos(a));
        double d = Math.sqrt(x * x + z * z);
        return Math.abs(a * d) < 5.5 && d > LAGOON - 2;
    }

    // ---------------------------------------------------------------- lagoon, caldera, channel
    private void shape() {
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > R) continue;
            double n = noise(x * 0.5, 0, z * 0.5);
            if (d <= LAGOON || inChannel(x, z) && d <= R - 4) {
                int f = FLOOR + (int) Math.round(n * 2);
                ground(x, z, f, n > 0.4 ? "minecraft:gravel" : n < -0.5 ? "minecraft:brain_coral_block" : "minecraft:sand", "minecraft:sandstone", 4);
                if (inChannel(x, z) && d > LAGOON && d <= WALL)
                    for (int y = f + 1; y <= WALL_TOP; y++) sealCells.add(new int[]{x, y, z});
            } else if (d <= WALL) {
                int t = WALL_TOP - (int) Math.round(Math.abs(noise(x * 0.9, 1, z * 0.9)) * 5);
                ground(x, z, t, (x + z) % 7 == 0 ? "minecraft:mossy_cobblestone" : "minecraft:stone", "minecraft:andesite", 80);
            } else {
                double t = smooth((d - WALL) / (R - WALL));
                int f = (int) Math.round(WALL_TOP - t * (WALL_TOP - top(x, z)));
                ground(x, z, Math.max(top(x, z), f), "minecraft:stone", "minecraft:andesite", 60);
            }
        }
    }

    /** The sea arch over the channel (it breaks and fills the channel when the Leviathan comes). */
    private void arch() {
        double cs = Math.cos(entry), sn = Math.sin(entry);
        for (int d = LAGOON - 1; d <= WALL + 1; d++) for (int s = -8; s <= 8; s++) {
            int x = (int) Math.round(d * cs - s * sn), z = (int) Math.round(d * sn + s * cs);
            int y0 = 76 + (int) Math.round(Math.abs(s) * 0.6), y1 = 88 + (int) Math.round(Math.abs(s) * 0.2);
            for (int y = y0; y <= y1; y++) put(x, y, z, (y + s) % 6 == 0 ? "minecraft:mossy_stone_bricks" : "minecraft:stone");
        }
    }

    // ---------------------------------------------------------------- the Spire
    int spireR(int y) { return y < 64 ? 8 : 7 - (int) Math.round((y - 64) / 18.0); }

    private void spire() {
        for (int y = FLOOR - 3; y <= TOP - 1; y++) {
            int r = spireR(y);
            for (int x = -r - 1; x <= r + 1; x++) for (int z = -r - 1; z <= r + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > r + 0.5) continue;
                boolean band = (y - FLOOR) % 8 == 0;
                boolean window = y > 70 && (y % 12 == 5 || y % 12 == 6) && Math.abs(x) < 1 && d > r - 0.6;
                put(x, y, z, window ? "minecraft:iron_bars" : band ? "minecraft:chiseled_stone_bricks"
                        : (x * 3 + y + z * 5) % 9 == 0 ? "minecraft:cracked_stone_bricks" : (x + y * 2 + z) % 7 == 0 ? "minecraft:mossy_stone_bricks" : "minecraft:stone_bricks");
            }
        }
        // the crown: a platform, a parapet, braziers, the Bane and the Horn
        for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 9.5) continue;
            put(x, TOP, z, d > 8.5 ? "minecraft:chiseled_stone_bricks" : (x + z) % 2 == 0 ? "minecraft:polished_deepslate" : "minecraft:deepslate_tiles");
            if (d > 8.5 && (int) (Math.atan2(z, x) * 10) % 3 != 0) put(x, TOP + 1, z, "minecraft:stone_brick_wall");
            if (d <= 9.5 && d > 7.5) put(x, TOP - 1, z, "minecraft:stone_brick_stairs[facing=" + (Math.abs(x) > Math.abs(z) ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south")) + ",half=top]");
        }
        put(ballista[0], ballista[1], ballista[2], "pixelpirates:bane_ballista[facing=north]");
        put(horn[0], horn[1], horn[2], "pixelpirates:watchers_horn[facing=north]");
        for (int[] b : new int[][]{{6, 6}, {-6, 6}, {6, -6}, {-6, -6}}) {
            put(b[0], TOP + 1, b[1], "minecraft:polished_deepslate_wall");
            put(b[0], TOP + 2, b[1], "minecraft:soul_campfire[lit=true]");
        }
        // the helical stair: from the water up to the crown, 3.2 turns
        int lastY = Integer.MIN_VALUE;
        for (double t = 0; t <= 1; t += 0.0012) {
            double ang = stairAngle(t);
            int y = stairY(t);
            int r = spireR(y) + 1;
            boolean riser = y > lastY;                                   // the first block of each new step is a stair block
            lastY = y;
            double tx = -Math.sin(ang), tz = Math.cos(ang);             // the way up
            String up = Math.abs(tx) > Math.abs(tz) ? (tx > 0 ? "east" : "west") : (tz > 0 ? "south" : "north");
            for (int w = 0; w <= 1; w++) {
                int x = (int) Math.round(Math.cos(ang) * (r + w)), z = (int) Math.round(Math.sin(ang) * (r + w));
                String here = at(x, y, z);
                if (riser && t > 0) put(x, y, z, "minecraft:stone_brick_stairs[facing=" + up + "]");
                else if (here == null || !here.contains("stairs")) put(x, y, z, "minecraft:stone_bricks");
                // headroom - and where it meets the crown, a slot cut through the platform so the stair comes out on top
                for (int h = 1; h <= 3; h++) if (y + h <= TOP + 1 && (y + h >= TOP - 1 || open(x, y + h, z))) put(x, y + h, z, AIR);
            }
        }
        // four bell balconies built out from the stair itself: you pass each one on the way up
        int[] heights = {70, 78, 86, 94};
        for (int i = 0; i < 4; i++) {
            double t = (heights[i] - SEA - 1) / (double) (TOP - SEA - 1);
            double ang = stairAngle(t);
            int y = stairY(t), r = spireR(y) + 1;
            for (int s = -1; s <= 1; s++) {
                double a = ang + s / (r + 3.0);
                for (int rr = r + 2; rr <= r + 4; rr++) {
                    int x = (int) Math.round(Math.cos(a) * rr), z = (int) Math.round(Math.sin(a) * rr);
                    put(x, y, z, rr == r + 4 ? "minecraft:chiseled_stone_bricks" : "minecraft:polished_andesite");
                    put(x, y - 1, z, "minecraft:stone_brick_stairs[facing=" + facingIn(x, z) + ",half=top]");
                    for (int h = 1; h <= 3; h++) put(x, y + h, z, AIR);
                    if (rr == r + 4) put(x, y + 1, z, "minecraft:stone_brick_wall");
                }
            }
            int bx = (int) Math.round(Math.cos(ang) * (r + 3)), bz = (int) Math.round(Math.sin(ang) * (r + 3));
            put(bx, y + 1, bz, "pixelpirates:tide_bell");
            bells.add(new int[]{bx, y + 1, bz});
        }
    }

    double stairAngle(double t) { return t * 3.2 * Math.PI * 2 + entry; }

    int stairY(double t) { return SEA + 1 + (int) Math.floor(t * (TOP - SEA - 1)); }

    /** An under-stair facing back toward the Spire (for the balconies' corbels). */
    static String facingIn(int x, int z) { return Math.abs(x) > Math.abs(z) ? (x > 0 ? "west" : "east") : (z > 0 ? "north" : "south"); }

    // ---------------------------------------------------------------- the outer stair (latecomers climb over the rim)
    private void outerStair() {
        double ang = entry + Math.PI;                                              // opposite the channel
        for (int y = SEA + 1; y <= WALL_TOP; y++) {
            double t = (y - SEA - 1) / (double) (WALL_TOP - SEA - 1);
            double a = ang + (t - 0.5) * 0.9;
            double d = R - 1 - t * (R - WALL - 1) + 1;
            for (int w = 0; w <= 2; w++) {
                int x = (int) Math.round(Math.cos(a) * (d - w)), z = (int) Math.round(Math.sin(a) * (d - w));
                put(x, y, z, "minecraft:cobblestone");
                for (int h = 1; h <= 3; h++) if (y + h <= WALL_TOP + 3) put(x, y + h, z, AIR);
            }
        }
    }

    // ---------------------------------------------------------------- broken watch arches on the rim
    private void rimRuins() {
        for (int k = 0; k < 6; k++) {
            double a = entry + Math.PI / 6 + k * Math.PI / 3;
            int x = (int) Math.round(Math.cos(a) * 56), z = (int) Math.round(Math.sin(a) * 56);
            int base = WALL_TOP - 4;
            while (base < WALL_TOP + 3 && !open(x, base + 1, z)) base++;
            int h = 4 + rnd.nextInt(6);
            for (int y = base + 1; y <= base + h; y++) { put(x, y, z, "minecraft:stone_bricks"); put(x + 1, y, z, y % 3 == 0 ? "minecraft:cracked_stone_bricks" : "minecraft:stone_bricks"); }
            if (k % 2 == 0) chest(x, base + 1, z + 2, "north", "chests/leviathan_spire_ruin");
        }
    }

    public static void main(String[] a) throws Exception {
        SpireLayout L = new SpireLayout(5L, (x, z) -> 40 + (int) (10 * Math.sin(x * 0.04)), Math.toRadians(30));
        String o = a.length > 0 ? a[0] : "spire";
        System.out.println("palette " + L.palette().size() + ", bells " + L.bells.size() + ", seal cells " + L.sealCells.size() + ", loot " + L.loot.size());
        L.map(o + "_map.png", 118);
        L.slice(false, 0, o + "_slice.png");
    }
}
