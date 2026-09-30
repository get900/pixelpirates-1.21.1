package net.get900.pixelpirates.world.leviathan;

import java.util.function.IntBinaryOperator;

/**
 * PORT 1 - SALTMARROW, a whaling and fishing shanty town on stilts over the deep water of the Shipgrave Depths, on the
 * Leviathan's road from the Rift to the Gullet. Built twice from the same seed: intact (worldgen) and RUINED (after the
 * Leviathan passes - LeviathanPorts rewrites the difference chunk by chunk).
 * <pre>
 *   THE DECK         boardwalks at y 64 on spruce stilts to the seabed; a central plaza, spokes and a ring walk
 *   SALTED ANCHOR    the tavern (two storeys) - west
 *   THE CHAPEL       with a bell tower (the bell ends up in the Leviathan's crown) - north-east
 *   THE REFINERY     whale-oil: cauldrons, barrels, a flensing deck with a whale skeleton - south-east
 *   SMOKEHOUSES, NET LOFTS, fishermen's huts round the ring; three piers with mooring posts, a crane
 * RUINED: a great BITE out of the east side, buildings torn open and charred, boardwalks broken, a smashed boat on the
 * tavern roof, blood, scattered barrels, the chapel bell gone, one of the Leviathan's shed scales left in a chest.
 * </pre>
 */
public class SaltmarrowLayout extends SiteLayout {
    public static final int DECK = 64;
    public final int[] bell = {20, DECK + 15, -20};
    public final boolean ruined;

    public SaltmarrowLayout(long seed, IntBinaryOperator terrain, boolean ruined) {
        super(64, 0, 100, seed, terrain);
        this.ruined = ruined;
        decks();
        tavern();
        chapel();
        refinery();
        smokehouses();
        huts();
        piers();
        lamps();
        if (ruined) ruin();
    }

    void platform(int x1, int z1, int x2, int z2) {
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            put(x, DECK - 1, z, (x + z) % 5 == 0 ? "minecraft:stripped_spruce_wood[axis=y]" : "minecraft:spruce_planks");
            if (Math.floorMod(x - x1, 4) == 0 && Math.floorMod(z - z1, 4) == 0) stilt(x, z);
        }
    }

    void stilt(int x, int z) {
        for (int y = DECK - 2; y > top(x, z); y--) put(x, y, z, "minecraft:spruce_log[axis=y]");
    }

    void walk(int x1, int z1, int x2, int z2) {                                      // a 3-wide boardwalk with rope rails
        boolean alongX = Math.abs(x2 - x1) >= Math.abs(z2 - z1);
        int len = Math.max(Math.abs(x2 - x1), Math.abs(z2 - z1));
        for (int i = 0; i <= len; i++) {
            int x = x1 + (x2 - x1) * i / Math.max(1, len), z = z1 + (z2 - z1) * i / Math.max(1, len);
            for (int s = -1; s <= 1; s++) {
                int X = alongX ? x : x + s, Z = alongX ? z + s : z;
                if (get(X, DECK - 1, Z) == 0) put(X, DECK - 1, Z, "minecraft:spruce_planks");
                if (i % 5 == 0 && s != 0) stilt(X, Z);
            }
            if (i % 2 == 0) for (int s = -2; s <= 2; s += 4) {
                int X = alongX ? x : x + s, Z = alongX ? z + s : z;
                if (get(X, DECK - 1, Z) == 0) put(X, DECK, Z, "minecraft:spruce_fence");
            }
        }
    }

    // ---------------------------------------------------------------- the deck
    private void decks() {
        platform(-12, -12, 12, 12);                                                    // the plaza
        put(0, DECK, 0, "minecraft:water_cauldron[level=3]");                          // a rain cistern
        for (int[] s : new int[][]{{-30, 0}, {30, 0}, {0, -30}, {0, 30}, {21, -21}, {-21, 21}, {21, 21}, {-21, -21}}) walk(0, 0, s[0], s[1]);
        for (int k = 0; k < 64; k++) {                                                 // the ring walk
            double a = k * Math.PI * 2 / 64, b = (k + 1) * Math.PI * 2 / 64;
            walk((int) Math.round(Math.cos(a) * 30), (int) Math.round(Math.sin(a) * 30), (int) Math.round(Math.cos(b) * 30), (int) Math.round(Math.sin(b) * 30));
        }
    }

    // ---------------------------------------------------------------- the Salted Anchor (tavern)
    private void tavern() {
        platform(-44, -10, -26, 10);
        house(-42, -8, -28, 8, DECK, 5, "minecraft:spruce_planks", "minecraft:spruce_log[axis=y]", "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab[type=bottom]", 'e');
        fill(-41, DECK + 4, -7, -29, DECK + 4, 7, "minecraft:spruce_planks");            // the upper floor
        for (int x = -40; x <= -30; x += 5) { put(x, DECK, -5, "minecraft:spruce_fence"); put(x, DECK + 1, -5, "minecraft:spruce_pressure_plate"); put(x - 1, DECK, -5, "minecraft:spruce_stairs[facing=east]"); }
        fill(-41, DECK, 4, -34, DECK, 4, "minecraft:spruce_slab[type=top]");              // the bar
        for (int x = -41; x <= -34; x += 2) put(x, DECK + 1, 4, "minecraft:barrel[facing=up]");
        barrel(-41, DECK, 6, "chests/saltmarrow_tavern");
        put(-35, DECK + 1, 7, "minecraft:lantern[hanging=false]");
        for (int z = -6; z <= 6; z += 3) bed(-40, DECK + 5, z, 'e', "red");
    }

    // ---------------------------------------------------------------- the chapel + bell tower
    private void chapel() {
        platform(12, -34, 30, -16);
        house(14, -32, 26, -22, DECK, 6, "minecraft:white_terracotta", "minecraft:stripped_spruce_log[axis=y]", "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", 's');
        for (int z = -30; z <= -24; z += 2) { put(16, DECK, z, "minecraft:spruce_stairs[facing=east]"); put(24, DECK, z, "minecraft:spruce_stairs[facing=west]"); }
        put(20, DECK, -31, "minecraft:lectern[facing=south]");
        put(21, DECK, -31, "minecraft:white_banner[rotation=0]");
        // the bell tower over its door
        for (int y = DECK; y <= DECK + 16; y++) for (int x = 19; x <= 21; x++) for (int z = -21; z <= -19; z++)
            if (x != 20 || z != -20 || y > DECK + 13) put(x, y, z, y > DECK + 13 && x == 20 && z == -20 ? AIR : (y % 4 == 0 ? "minecraft:stripped_spruce_log[axis=y]" : "minecraft:spruce_planks"));
        for (int y = DECK + 14; y <= DECK + 15; y++) for (int x = 19; x <= 21; x++) for (int z = -21; z <= -19; z++) if ((x == 20) != (z == -20)) put(x, y, z, AIR);
        put(bell[0], bell[1], bell[2], "minecraft:bell[attachment=ceiling,facing=north]");
        fill(19, DECK + 16, -21, 21, DECK + 16, -19, "minecraft:spruce_slab[type=bottom]");
        put(20, DECK + 17, -20, "minecraft:lightning_rod[facing=up]");
    }

    // ---------------------------------------------------------------- the whale-oil refinery + flensing deck
    private void refinery() {
        platform(14, 12, 40, 32);
        for (int x = 16; x <= 22; x += 3) { put(x, DECK, 14, "minecraft:water_cauldron[level=2]"); put(x, DECK - 1, 14, "minecraft:magma_block"); }
        for (int x = 16; x <= 24; x++) put(x, DECK, 18, x % 2 == 0 ? "minecraft:barrel[facing=up]" : "minecraft:barrel[facing=north]");
        barrel(25, DECK, 18, "chests/saltmarrow_refinery");
        fill(28, DECK, 13, 32, DECK + 3, 17, "minecraft:iron_block");                     // the rendering vat
        fill(29, DECK + 3, 14, 31, DECK + 3, 16, "minecraft:water_cauldron[level=3]");
        // the flensing ramp: a whale skeleton hauled up out of the water
        for (int x = 26; x <= 44; x++) put(x, DECK - 1 - Math.max(0, x - 38), 26, "minecraft:spruce_planks");
        for (int s = 0; s < 16; s++) {
            int x = 24 + s, y = DECK + (s < 12 ? 0 : -(s - 11));
            put(x, y, 27, "minecraft:bone_block[axis=x]");
            if (s % 3 == 0 && s < 12) for (int r = 1; r <= 4; r++) { put(x, y + r, 27 - (r < 3 ? r : 2), "minecraft:bone_block[axis=y]"); put(x, y + r, 27 + (r < 3 ? r : 2), "minecraft:bone_block[axis=y]"); }
        }
        put(24, DECK, 29, "minecraft:grindstone[face=floor,facing=east]");
        put(26, DECK, 30, "minecraft:chain[axis=y]");
    }

    // ---------------------------------------------------------------- smokehouses + net lofts
    private void smokehouses() {
        for (int[] p : new int[][]{{-32, -30}, {-40, 22}}) {
            platform(p[0] - 5, p[1] - 5, p[0] + 5, p[1] + 5);
            house(p[0] - 3, p[1] - 3, p[0] + 3, p[1] + 3, DECK, 4, "minecraft:stripped_spruce_log[axis=x]", "minecraft:spruce_log[axis=y]", "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", 'e');
            put(p[0], DECK, p[1], "minecraft:campfire[lit=true]");
            for (int y = DECK + 7; y <= DECK + 9; y++) put(p[0], y, p[1], "minecraft:cobblestone_wall");
            for (int x = -2; x <= 2; x += 2) put(p[0] + x, DECK + 3, p[1] - 2, "minecraft:chain[axis=y]");
        }
        for (int[] p : new int[][]{{-14, 38}, {40, -8}}) {                           // net lofts: open-sided, nets (cobwebs) drying
            platform(p[0] - 5, p[1] - 4, p[0] + 5, p[1] + 4);
            for (int x = -4; x <= 4; x += 4) for (int z = -3; z <= 3; z += 6) for (int y = DECK; y <= DECK + 4; y++) put(p[0] + x, y, p[1] + z, "minecraft:spruce_fence");
            fill(p[0] - 5, DECK + 5, p[1] - 4, p[0] + 5, DECK + 5, p[1] + 4, "minecraft:spruce_slab[type=bottom]");
            for (int x = -3; x <= 3; x++) put(p[0] + x, DECK + 3, p[1], "minecraft:cobweb");
            barrel(p[0] + 4, DECK, p[1] + 2, "chests/saltmarrow_fishery");
        }
    }

    // ---------------------------------------------------------------- the fishermen's huts round the ring
    private void huts() {
        int k = 0;
        for (double a = 0.3; a < Math.PI * 2; a += Math.PI / 3.5) {
            int cx = (int) Math.round(Math.cos(a) * 40), cz = (int) Math.round(Math.sin(a) * 40);
            if (Math.abs(cx - 27) < 12 && Math.abs(cz - 22) < 12 || Math.abs(cx + 35) < 10 && Math.abs(cz) < 14) continue;
            platform(cx - 4, cz - 4, cx + 4, cz + 4);
            walk((int) Math.round(Math.cos(a) * 31), (int) Math.round(Math.sin(a) * 31), cx, cz);
            house(cx - 3, cz - 3, cx + 3, cz + 3, DECK, 4, k % 2 == 0 ? "minecraft:spruce_planks" : "minecraft:birch_planks", "minecraft:spruce_log[axis=y]",
                    "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", Math.abs(Math.cos(a)) > Math.abs(Math.sin(a)) ? (Math.cos(a) > 0 ? 'w' : 'e') : (Math.sin(a) > 0 ? 'n' : 's'));
            bed(cx - 2, DECK, cz + 2, 'n', "white");
            put(cx + 2, DECK, cz - 2, "minecraft:barrel[facing=up]");
            k++;
        }
    }

    // ---------------------------------------------------------------- piers, moorings, a crane
    private void piers() {
        for (double a : new double[]{Math.PI * 0.75, Math.PI * 1.25, Math.PI * 0.1}) {
            int x1 = (int) Math.round(Math.cos(a) * 31), z1 = (int) Math.round(Math.sin(a) * 31);
            int x2 = (int) Math.round(Math.cos(a) * 58), z2 = (int) Math.round(Math.sin(a) * 58);
            walk(x1, z1, x2, z2);
            for (int d = 36; d <= 56; d += 6) {
                int x = (int) Math.round(Math.cos(a) * d - Math.sin(a) * 2), z = (int) Math.round(Math.sin(a) * d + Math.cos(a) * 2);
                put(x, DECK, z, "minecraft:spruce_log[axis=y]");
                put(x, DECK + 1, z, "minecraft:chain[axis=y]");
            }
        }
        // the crane at the end of the east pier
        int cx = (int) Math.round(Math.cos(Math.PI * 0.1) * 56), cz = (int) Math.round(Math.sin(Math.PI * 0.1) * 56);
        for (int y = DECK; y <= DECK + 10; y++) put(cx, y, cz, "minecraft:spruce_log[axis=y]");
        for (int d = 0; d <= 6; d++) put(cx + d, DECK + 10, cz, "minecraft:spruce_log[axis=x]");
        for (int y = DECK + 4; y <= DECK + 9; y++) put(cx + 6, y, cz, "minecraft:chain[axis=y]");
        put(cx + 6, DECK + 3, cz, "minecraft:barrel[facing=up]");
    }

    private void lamps() {
        for (int k = 0; k < 16; k++) {
            double a = k * Math.PI / 8;
            int x = (int) Math.round(Math.cos(a) * 32), z = (int) Math.round(Math.sin(a) * 32);
            if (get(x, DECK - 1, z) == 0) continue;
            put(x, DECK, z, "minecraft:spruce_fence"); put(x, DECK + 1, z, "minecraft:spruce_fence"); put(x, DECK + 2, z, "minecraft:lantern[hanging=false]");
        }
    }

    // ---------------------------------------------------------------- after the Leviathan passes
    private void ruin() {
        bite(34, 6, 22, 30, DECK + 20);                                                   // the east side is simply gone
        collapse(-44, DECK, -10, -26, DECK + 12, 10, 0.55);                              // the tavern torn open
        collapse(12, DECK, -34, 30, DECK + 18, -16, 0.35);                               // the chapel + its tower
        erase(bell[0], bell[1], bell[2]);                                                 // the bell is gone (it wears it now)
        for (int[] p : new int[][]{{-32, -30}, {-40, 22}, {-14, 38}}) collapse(p[0] - 5, DECK, p[1] - 5, p[0] + 5, DECK + 10, p[1] + 5, 0.45);
        for (double a = 0.3; a < Math.PI * 2; a += Math.PI / 3.5) {
            int cx = (int) Math.round(Math.cos(a) * 40), cz = (int) Math.round(Math.sin(a) * 40);
            if (rnd.nextInt(3) > 0) collapse(cx - 4, DECK, cz - 4, cx + 4, DECK + 8, cz + 4, 0.3 + rnd.nextDouble() * 0.4);
        }
        // boardwalks broken all over
        for (int i = 0; i < 90; i++) {
            int x = rnd.nextInt(90) - 45, z = rnd.nextInt(90) - 45;
            String s = at(x, DECK - 1, z);
            if (s != null && s.contains("spruce")) for (int dx = 0; dx <= 1 + rnd.nextInt(2); dx++) for (int dz = 0; dz <= 1; dz++) put(x + dx, DECK - 1, z + dz, AIR);
        }
        // a boat smashed down onto the tavern's roof
        hull(-35, 0, DECK + 7, 12, 5, 3, 0.4, 0.25, 0.5, 0, false);
        strew(-45, -45, 45, 45, DECK - 1, DECK + 2, 140);
        chest(-6, DECK, 6, "north", "chests/saltmarrow_ruin");
        chest(8, DECK, -8, "south", "chests/saltmarrow_ruin");
        put(0, DECK, 0, "minecraft:cauldron");
    }

    public static void main(String[] a) throws Exception {
        IntBinaryOperator t = (x, z) -> 14 + (int) (6 * Math.sin(x * 0.05));
        String o = a.length > 0 ? a[0] : "salt";
        SaltmarrowLayout L = new SaltmarrowLayout(3L, t, false), R = new SaltmarrowLayout(3L, t, true);
        System.out.println("palette " + L.palette().size() + " / ruined " + R.palette().size() + ", loot " + L.loot.size() + " / " + R.loot.size());
        L.map(o + "_map.png", 100);
        R.map(o + "_ruin_map.png", 100);
    }
}
