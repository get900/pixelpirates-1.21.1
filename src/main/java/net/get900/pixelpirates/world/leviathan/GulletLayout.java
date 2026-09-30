package net.get900.pixelpirates.world.leviathan;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * LAIR 2 - THE GULLET (Leviathan phase 2, "The Hunger"): a shipbreaker's bay in the Boiling Basin where it has fed
 * before. Under the eclipse while it lives (LeviathanEntity).
 * <pre>
 *   THE BAY          r 78 of open water ~26 deep over black sand and ash
 *   THE RING         cliffs and sea stacks of basalt and blackstone r 78-100 (up to y 90), one gap facing the open sea (+x)
 *   SEA STACKS       six inside the bay - the swimmers' footholds
 *   THE WRECKS       fourteen ships: floating, half-sunk with their bows in the air, and a sunken fleet on the floor
 *   BONES            two whale skeletons and a serpent's spine on the bay floor
 *   THE LIGHTHOUSE   gutted, on the tallest stack
 *   THE OUTPOST      a pirate outpost on a stack, half EATEN (a bite out of the stack); its supply chests hold powder
 *                    barges, gunpowder and chum - THE POISONED MEAL starts here
 * </pre>
 */
public class GulletLayout extends SiteLayout {
    public static final int BAY = 78, BAY_FLOOR = 36;
    public final int[] spawn = {0, BAY_FLOOR + 8, 0};
    public final List<int[]> stacks = new ArrayList<>();      // x, z, radius, top
    public final List<int[]> wrecks = new ArrayList<>();      // x, z (for Wreck Rain / Undertow targets)

    public GulletLayout(long seed, IntBinaryOperator terrain) {
        super(112, 0, 112, seed, terrain);
        shape();
        placeStacks();
        wrecks();
        bones();
        lighthouse();
        outpost();
    }

    // ---------------------------------------------------------------- bay + cliff ring
    private void shape() {
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > R) continue;
            double ang = Math.atan2(z, x);
            boolean gap = Math.abs(ang) < 0.13;
            int floor;
            String top, body = "minecraft:basalt[axis=y]";
            if (d <= BAY || gap && d <= 104) {
                floor = BAY_FLOOR + (int) Math.round(noise(x * 0.7, 0, z * 0.7) * 3);
                double n = noise(x * 2.1, 3, z * 2.1);
                top = n > 0.5 ? "minecraft:gravel" : n < -0.8 ? "minecraft:nether_wart_block" : n < 0 ? "minecraft:black_concrete_powder" : "minecraft:sand";
                if (top.contains("concrete_powder")) top = "minecraft:blackstone";
                if (gap && d > BAY) floor = BAY_FLOOR - 2;
            } else if (d <= 100) {
                double rise = smooth((d - BAY) / 6.0) * smooth((100 - d) / 6.0 + 0.5);
                floor = (int) Math.round(BAY_FLOOR + rise * (40 + 14 * Math.abs(noise(x * 0.35, 0, z * 0.35))));
                top = floor > SEA + 6 ? (noise(x, 1, z) > 0.2 ? "minecraft:tuff" : "minecraft:blackstone") : "minecraft:basalt[axis=y]";
            } else {
                floor = blend(x, z, BAY_FLOOR + 30, 12);
                top = "minecraft:tuff";
            }
            ground(x, z, floor, top, body, 6);
        }
    }

    // ---------------------------------------------------------------- sea stacks in the bay
    private void placeStacks() {
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + rnd.nextDouble() * 0.6, r = 32 + rnd.nextInt(32);
            int sx = (int) (Math.cos(a) * r), sz = (int) (Math.sin(a) * r), rad = 4 + rnd.nextInt(4), topY = 68 + rnd.nextInt(14);
            if (i == 0) topY = 84;                                               // the lighthouse stack
            stacks.add(new int[]{sx, sz, rad, topY});
            for (int x = -rad - 2; x <= rad + 2; x++) for (int z = -rad - 2; z <= rad + 2; z++) {
                double d = Math.sqrt(x * x + z * z) + noise(sx + x, 0, sz + z) * 1.2;
                if (d > rad + 1.5) continue;
                int t = d <= rad ? topY - (int) Math.max(0, d - rad + 2) : BAY_FLOOR + 8 + rnd.nextInt(6);
                for (int y = BAY_FLOOR - 2; y <= t; y++) put(sx + x, y, sz + z, y == t ? (t > SEA ? "minecraft:tuff" : "minecraft:basalt[axis=y]")
                        : (y * 3 + x) % 11 == 0 ? "minecraft:shroomlight" : (y + z) % 5 == 0 ? "minecraft:blackstone" : "minecraft:basalt[axis=y]");
            }
        }
    }

    boolean onStack(int x, int z, int pad) {
        for (int[] s : stacks) if ((x - s[0]) * (x - s[0]) + (z - s[1]) * (z - s[1]) < (s[2] + pad) * (s[2] + pad)) return true;
        return false;
    }

    // ---------------------------------------------------------------- the wrecks
    private void wrecks() {
        int placed = 0;
        for (int tries = 0; tries < 200 && placed < 14; tries++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = 12 + rnd.nextDouble() * 60;
            int cx = (int) (Math.cos(a) * r), cz = (int) (Math.sin(a) * r);
            if (onStack(cx, cz, 16)) continue;
            boolean close = false;
            for (int[] w : wrecks) if ((w[0] - cx) * (w[0] - cx) + (w[1] - cz) * (w[1] - cz) < 22 * 22) close = true;
            if (close) continue;
            int kind = placed % 3, L = 14 + rnd.nextInt(16), W = 5 + L / 5, D = 4 + L / 8;
            double yaw = rnd.nextDouble() * Math.PI * 2;
            switch (kind) {
                case 0 -> hull(cx, cz, SEA - D + 1, L, W, D, yaw, 0, 0.35 + rnd.nextDouble() * 0.3, rnd.nextInt(3), rnd.nextBoolean());   // floating
                case 1 -> hull(cx, cz, BAY_FLOOR + 2, L, W, D, yaw, 0.55 + rnd.nextDouble() * 0.3, 0.5, 1, false);                     // bow in the air
                default -> hull(cx, cz, BAY_FLOOR + 1, L, W, D, yaw, 0.05, 0.6, rnd.nextInt(2), false);                                  // sunken fleet
            }
            if (kind != 0 || rnd.nextBoolean()) {
                int y = kind == 0 ? SEA - D + 3 : BAY_FLOOR + 3;
                chest(cx, y, cz, "north", "chests/leviathan_gullet_wreck");
            }
            wrecks.add(new int[]{cx, cz});
            placed++;
        }
    }

    // ---------------------------------------------------------------- bones on the bay floor
    private void bones() {
        for (int k = 0; k < 2; k++) {                                            // whale skeletons
            double a = rnd.nextDouble() * Math.PI * 2;
            int bx = (int) (Math.cos(a) * (25 + k * 25)), bz = (int) (Math.sin(a) * (25 + k * 25));
            double yaw = rnd.nextDouble() * Math.PI;
            double cs = Math.cos(yaw), sn = Math.sin(yaw);
            for (int s = -12; s <= 12; s++) {
                int x = (int) Math.round(bx + s * cs), z = (int) Math.round(bz + s * sn);
                po(x, BAY_FLOOR + 1, z, "minecraft:bone_block[axis=y]");
                if (s % 3 == 0 && Math.abs(s) < 10)                              // ribs arching up either side
                    for (double t = 0; t <= Math.PI; t += 0.2) {
                        int rx = (int) Math.round(x - Math.cos(t) * 5 * sn), rz = (int) Math.round(z + Math.cos(t) * 5 * cs), ry = BAY_FLOOR + 1 + (int) Math.round(Math.sin(t) * 6);
                        po(rx, ry, rz, "minecraft:bone_block[axis=y]");
                    }
            }
            for (int x = -2; x <= 2; x++) for (int y = 1; y <= 3; y++) po((int) Math.round(bx + 14 * cs) + x, BAY_FLOOR + y, (int) Math.round(bz + 14 * sn), "minecraft:bone_block[axis=y]");
        }
        for (double t = 0; t < Math.PI * 1.6; t += 0.05) {                         // a serpent's spine curling across the floor
            int x = (int) Math.round(Math.cos(t) * (20 + t * 6) - 10), z = (int) Math.round(Math.sin(t) * (20 + t * 6) + 5);
            if (onStack(x, z, 2)) continue;
            po(x, BAY_FLOOR + 1, z, "minecraft:bone_block[axis=y]");
            if ((int) (t * 20) % 4 == 0) { po(x, BAY_FLOOR + 2, z, "minecraft:bone_block[axis=y]"); po(x, BAY_FLOOR + 3, z, "minecraft:calcite"); }
        }
    }

    // ---------------------------------------------------------------- the gutted lighthouse (stack 0)
    private void lighthouse() {
        int[] s = stacks.get(0);
        int base = s[3] + 1;
        tower(s[0], s[1], 3, base, base + 18, "minecraft:stone_bricks", 6);
        for (int y = base; y <= base + 18; y++) for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            if (noise(s[0] + x, y * 0.7, s[1] + z) > 0.55 || (y > base + 12 && x > 0)) erase(s[0] + x, y, s[1] + z);     // gutted, torn open
        }
        put(s[0], base + 12, s[1], "minecraft:campfire[lit=false]");
    }

    // ---------------------------------------------------------------- the half-eaten outpost (stack 1)
    private void outpost() {
        int[] s = stacks.get(1);
        int y = s[3] + 1, cx = s[0], cz = s[1];
        fill(cx - s[2] - 3, y - 1, cz - s[2] - 3, cx + s[2] + 3, y - 1, cz + s[2] + 3, "minecraft:spruce_planks");
        for (int x = -s[2] - 3; x <= s[2] + 3; x += 3) for (int z = -s[2] - 3; z <= s[2] + 3; z += 3)
            for (int yy = y - 2; yy >= BAY_FLOOR && open(cx + x, yy, cz + z); yy--) put(cx + x, yy, cz + z, "minecraft:spruce_log[axis=y]");
        house(cx - 5, cz - 5, cx, cz - 1, y, 4, "minecraft:spruce_planks", "minecraft:stripped_spruce_log[axis=y]", "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", 's');
        house(cx + 1, cz + 1, cx + 6, cz + 5, y, 4, "minecraft:spruce_planks", "minecraft:stripped_spruce_log[axis=y]", "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", 'n');
        chest(cx - 3, y, cz - 3, "south", "chests/leviathan_gullet_supplies");
        chest(cx + 3, y, cz + 3, "north", "chests/leviathan_gullet_supplies");
        barrel(cx + 5, y, cz - 4, "chests/leviathan_gullet_supplies");
        put(cx - 4, y, cz + 4, "minecraft:black_banner[rotation=0]");
        // the BITE: a crescent torn out of the stack and the outpost with it
        bite(cx + s[2] + 4, cz + 2, s[2] + 5, BAY_FLOOR + 10, y + 10);
    }

    public static void main(String[] a) throws Exception {
        GulletLayout L = new GulletLayout(11L, (x, z) -> 50 + (int) (20 * Math.sin(x * 0.02) * Math.cos(z * 0.02)));
        String o = a.length > 0 ? a[0] : "gullet";
        System.out.println("palette " + L.palette().size() + ", wrecks " + L.wrecks.size() + ", stacks " + L.stacks.size() + ", loot " + L.loot.size());
        L.map(o + "_map.png", 112);
        L.slice(false, 0, o + "_slice.png");
    }
}
