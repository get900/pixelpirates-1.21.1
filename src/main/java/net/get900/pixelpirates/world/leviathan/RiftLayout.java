package net.get900.pixelpirates.world.leviathan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * LAIR 1 - THE RIFT (Leviathan phase 1, "The Waking"), under the Pillar Sea. A chasm ~170 long cut into the seabed down
 * to y -52, where the Leviathan slept for ten thousand years:
 * <pre>
 *   THE CHASM        along x, 26-40 wide, jagged walls veined with soul barnacles and luminous veins; pillars over it cleared
 *   THE RIBCAGE      nine colossal ribs arching over the chasm (the bones of the ancients' first binding) and a spine along the top
 *   ANCHOR PILLARS   four ancient posts on the rims, each with an ANCHOR WINCH (reinforce the binding chain) and a chain
 *                    hanging down into the chasm - the Leviathan is bound to them (LeviathanEntity: THE BINDINGS)
 *   STALACTITES      forests of dripstone hanging from the ribs and the undercut rims (Rift Quake drops them)
 *   BRIDGES          three ancient stone bridges across the chasm, broken in the middle
 *   THE RIFT SEAL    an altar on the north rim: wake the Leviathan here (the confirmation prompt) - a mural of it in chains
 *   THE VAULT        the ancients' hoard at the chasm's east end
 * </pre>
 */
public class RiftLayout extends SiteLayout {
    public static final int FLOOR = -52;
    public final int[] spawn = {0, FLOOR + 10, 0};
    public final int[] seal;
    public final List<int[]> winches = new ArrayList<>();      // the anchor winch blocks (order = chain 0..3)
    public final List<int[]> chainEnds = new ArrayList<>();    // where each chain leaves its pillar (particle chains start here)

    public RiftLayout(long seed, IntBinaryOperator terrain) {
        super(104, -64, 100, seed, terrain);
        int rim = rimY(0, 0);
        seal = new int[]{0, rim + 1, -(int) halfWidth(0) - 6};
        carve();
        ribs();
        pillars();
        bridges();
        stalactites();
        altar();
        vault();
    }

    double halfWidth(int x) {
        double taper = smooth((90 - Math.abs(x)) / 24.0);
        return (14 + 5 * Math.sin(x * 0.05) + 3 * Math.sin(x * 0.13 + 1)) * (0.25 + 0.75 * taper);
    }

    int floorAt(int x) { return FLOOR + (int) Math.round(4 * Math.sin(x * 0.07) + 2 * Math.sin(x * 0.19)); }

    int rimY(int x, int z) { return Math.min(top(x, z), 12); }

    // ---------------------------------------------------------------- the chasm
    private void carve() {
        for (int x = -92; x <= 92; x++) {
            double w = halfWidth(x);
            for (int z = -48; z <= 48; z++) {
                double edge = w + noise(x, 0, z) * 2.2;
                if (Math.abs(z) > edge + 16) continue;
                if (Math.abs(z) <= edge) {
                    // the chasm itself: open from its floor to the sky (pillars over it are cut away)
                    int f = floorAt(x) + (int) Math.round((Math.abs(z) / Math.max(1, edge)) * (Math.abs(z) / Math.max(1, edge)) * 18);
                    clearAbove(x, z, f + 1);
                    for (int y = f - 2; y <= f; y++) put(x, y, z, y == f ? (noise(x * 2, y, z * 2) > 0.4 ? "minecraft:bone_block[axis=y]" : "minecraft:blackstone") : "minecraft:basalt[axis=y]");
                } else if (top(x, z) > 30) {
                    clearAbove(x, z, rimY(x, z) + 1);                           // no pillar looms right over the rim
                }
            }
        }
        // the walls' faces: blackstone and deepslate, soul barnacles, glowing veins
        for (int x = -92; x <= 92; x++) for (int z = -48; z <= 48; z++) for (int y = FLOOR - 6; y <= 10; y++) {
            if (!world(x, y, z).equals("minecraft:stone")) continue;
            if (!(isWater(x, y, z + 1) || isWater(x, y, z - 1) || isWater(x + 1, y, z) || isWater(x - 1, y, z))) continue;
            put(x, y, z, (x * 7 + y * 3 + z) % 23 == 0 ? "pixelpirates:luminous_vein" : (x + y * 5 + z) % 17 == 0 ? "pixelpirates:soul_barnacle"
                    : noise(x, y, z) > 0.3 ? "minecraft:polished_blackstone" : "minecraft:deepslate");
        }
        // undercut shelves under the rims (stalactites hang from them)
        for (int x = -80; x <= 80; x++) {
            double w = halfWidth(x);
            for (int s = -1; s <= 1; s += 2) for (int d = 1; d <= 4; d++) for (int y = -8; y <= -2; y++) {
                int z = (int) Math.round(s * (w + d));
                if (noise(x * 1.7, y, z) > -0.2 && world(x, y, z).equals("minecraft:stone")) put(x, y, z, WATER);
            }
        }
    }

    // ---------------------------------------------------------------- the ribcage
    private void ribs() {
        for (int rx = -64; rx <= 64; rx += 16) {
            double span = halfWidth(rx) + 8;
            for (double t = -1; t <= 1; t += 0.01) {
                double z = t * span, y = 6 + Math.sqrt(Math.max(0, 1 - t * t)) * 38;
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++)
                    po(rx + dx, (int) Math.round(y) + dy, (int) Math.round(z), (dx == 0 && dy == 0 && (int) (t * 20) % 4 == 0) ? "minecraft:calcite" : "minecraft:bone_block[axis=y]");
            }
        }
        for (int x = -70; x <= 70; x++) {                                    // the spine along the top of the arches
            for (int dz = -1; dz <= 1; dz++) po(x, 44, dz, "minecraft:bone_block[axis=x]");
            if (Math.floorMod(x, 8) == 0) { po(x, 45, 0, "minecraft:bone_block[axis=y]"); po(x, 46, 0, "minecraft:bone_block[axis=y]"); po(x, 47, 0, "minecraft:calcite"); }
        }
    }

    // ---------------------------------------------------------------- the four anchor pillars
    private void pillars() {
        int k = 0;
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) {
            int px = sx * 44, pz = sz * ((int) halfWidth(px) + 12);
            int base = rimY(px, pz);
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
                ground(px + x, pz + z, base, "minecraft:polished_deepslate", "minecraft:deepslate_bricks", 6);
                if (Math.abs(x) <= 2 && Math.abs(z) <= 2)
                    for (int y = base + 1; y <= 48; y++) {
                        boolean band = (y - base) % 9 == 0;
                        put(px + x, y, pz + z, band ? "minecraft:chiseled_deepslate" : (Math.abs(x) == 2 || Math.abs(z) == 2) && (x + y + z) % 5 == 0 ? "minecraft:cracked_deepslate_bricks" : "minecraft:deepslate_bricks");
                    }
            }
            fill(px - 3, 49, pz - 3, px + 3, 49, pz + 3, "minecraft:polished_deepslate");            // the platform on top
            for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) if (Math.abs(x) == 3 || Math.abs(z) == 3)
                if ((x + z) % 2 == 0) put(px + x, 50, pz + z, "minecraft:deepslate_brick_wall");
            // the arm reaching out over the chasm, the chain hanging from it, the winch that works it
            int dir = -sz;                                                      // toward the chasm
            for (int d = 3; d <= 9; d++) put(px, 48, pz + dir * d, "minecraft:polished_deepslate");
            int cz = pz + dir * 9;
            for (int y = 47; y >= 12; y--) po(px, y, cz, wet(y, "minecraft:chain[axis=y]"));
            po(px, 11, cz, wet(11, "minecraft:iron_bars"));
            put(px, 50, pz + dir * 2, "pixelpirates:anchor_winch[facing=" + (dir > 0 ? "south" : "north") + "]");
            winches.add(new int[]{px, 50, pz + dir * 2});
            chainEnds.add(new int[]{px, 11, cz});
            k++;
        }
    }

    // ---------------------------------------------------------------- the broken bridges
    private void bridges() {
        for (int[] b : new int[][]{{-30, -14}, {10, -26}, {52, -6}}) {
            int bx = b[0], by = b[1];
            double w = halfWidth(bx) + 3;
            for (int z = (int) -w; z <= w; z++) {
                if (Math.abs(z) < 3 + (bx + 30) % 4) continue;                  // the broken middle
                for (int x = bx - 1; x <= bx + 1; x++) po(x, by, z, "minecraft:stone_bricks");
                po(bx - 2, by + 1, z, wet(by + 1, "minecraft:stone_brick_wall"));
                po(bx + 2, by + 1, z, wet(by + 1, "minecraft:stone_brick_wall"));
                if (Math.abs(z) > w - 6) for (int y = by - 1; y >= by - 4 + (int) (Math.abs(Math.abs(z) - w)); y--) po(bx, y, z, "minecraft:cracked_stone_bricks");
            }
        }
    }

    // ---------------------------------------------------------------- stalactite forests
    private void stalactites() {
        for (int i = 0; i < 2600; i++) {
            int x = rnd.nextInt(180) - 90, z = rnd.nextInt(96) - 48;
            int y = FLOOR + rnd.nextInt(90);
            if (!open(x, y, z) || open(x, y + 1, z)) continue;
            String above = world(x, y + 1, z);
            if (above.contains("chain") || above.contains("winch") || above.contains("wall")) continue;
            int len = 2 + rnd.nextInt(5);
            for (int k = 0; k < len; k++) {
                if (!open(x, y - k, z)) break;
                String th = k == len - 1 ? "tip" : k == len - 2 ? "frustum" : k == 0 ? "base" : "middle";
                put(x, y - k, z, "minecraft:pointed_dripstone[vertical_direction=down,thickness=" + th + ",waterlogged=" + (y - k <= SEA) + "]");
            }
        }
    }

    // ---------------------------------------------------------------- the Rift Seal altar (north rim)
    private void altar() {
        int ax = seal[0], ay = seal[1] - 1, az = seal[2];
        for (int x = -6; x <= 6; x++) for (int z = -5; z <= 5; z++) {
            ground(ax + x, az + z, ay, (x + z) % 2 == 0 ? "minecraft:prismarine_bricks" : "minecraft:dark_prismarine", "minecraft:deepslate_bricks", 5);
            for (int y = ay + 1; y <= ay + 12; y++) if (!open(ax + x, y, az + z)) put(ax + x, y, az + z, WATER);
        }
        put(ax, ay + 1, az, "pixelpirates:rift_seal");
        for (int s = -1; s <= 1; s += 2) {                                     // soul braziers either side
            put(ax + s * 4, ay + 1, az - 2, "minecraft:polished_deepslate_wall");
            put(ax + s * 4, ay + 2, az - 2, wet(ay + 2, "minecraft:soul_lantern[hanging=false]"));
            chest(ax + s * 5, ay + 1, az + 3, "north", "chests/leviathan_rift_altar");
        }
        // the mural behind: the Leviathan coiled in chains (bone = its body, chain = the bindings, vein = its eye)
        String[] art = {
                "...CC.....CC...",
                "..C..BBBBB..C..",
                ".C.BB.....BB.C.",
                "C.B..BBBBB..BVC",
                "C.B.B.....B.BBC",
                ".C.BB.BBB.BB.C.",
                "..C..BB..BB.C..",
                "...CC..BB..CC.."};
        for (int r = 0; r < art.length; r++) for (int c = 0; c < art[r].length(); c++) {
            char ch = art[r].charAt(c);
            put(ax + c - 7, ay + 9 - r, az - 6, ch == 'B' ? "minecraft:bone_block[axis=y]" : ch == 'C' ? "minecraft:chiseled_deepslate"
                    : ch == 'V' ? "pixelpirates:luminous_vein" : "minecraft:deepslate_tiles");
        }
    }

    // ---------------------------------------------------------------- the ancients' vault (chasm, east end)
    private void vault() {
        int vx = 76, vy = floorAt(76) + 2, vz = 0;
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) for (int y = 0; y <= 5; y++) {
            boolean shell = Math.abs(x) == 4 || Math.abs(z) == 4 || y == 0 || y == 5;
            put(vx + x, vy + y, vz + z, shell ? (y == 0 ? "minecraft:polished_blackstone_bricks" : "minecraft:gilded_blackstone") : WATER);
        }
        for (int y = 1; y <= 3; y++) for (int z = -1; z <= 1; z++) put(vx - 4, vy + y, vz + z, WATER);   // its mouth, facing the chasm
        chest(vx + 2, vy + 1, vz, "west", "chests/leviathan_rift_hoard");
        chest(vx + 2, vy + 1, vz + 2, "west", "chests/phase5_treasure");
        put(vx, vy + 4, vz, "minecraft:sea_lantern");
    }

    public static void main(String[] a) throws Exception {
        RiftLayout L = new RiftLayout(7L, (x, z) -> 5 + (int) (3 * Math.sin(x * 0.03)) + ((Math.abs(x - 30) < 4 && Math.abs(z - 40) < 4) ? 250 : 0));
        String o = a.length > 0 ? a[0] : "rift";
        System.out.println("palette " + L.palette().size() + ", winches " + L.winches.size() + ", loot " + L.loot.size());
        L.map(o + "_map.png", 100);
        L.slice(false, 0, o + "_slice_x.png");
        L.slice(true, 0, o + "_slice_z.png");
        L.plan(-20, o + "_plan_m20.png");
    }
}
