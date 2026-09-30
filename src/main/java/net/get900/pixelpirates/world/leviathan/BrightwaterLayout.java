package net.get900.pixelpirates.world.leviathan;

import java.util.function.IntBinaryOperator;

/**
 * PORT 2 - BRIGHTWATER, the biggest trading port outside Wavebreak, on its own island in the Reef Edge - on the
 * Leviathan's road from the Gullet to the Spire. Built twice from the same seed: intact and RUINED (LeviathanPorts).
 * <pre>
 *   THE ISLAND       r 50, ground y 64, beaches, a hill to the north (y ~78) with the GOVERNOR'S HOUSE on top
 *   THE QUAYS        stone along the south shore, three piers, the FLEET AT ANCHOR (three ships), the HARBOUR BELL tower
 *   THE LIGHTHOUSE   on the east point
 *   THE TOWN         market square + fountain, three warehouses, the harbourmaster's office, the shipyard (slipway +
 *                    a ship's frame), the Lantern & Keel tavern, eight houses
 * RUINED: the lighthouse snapped in half, a bite out of the quays, the fleet smashed (one ship thrown onto a warehouse
 * roof), houses collapsed and burning, the harbour bell gone, blood and wreckage everywhere, a shed scale in a chest.
 * </pre>
 */
public class BrightwaterLayout extends SiteLayout {
    public static final int GROUND = 64, ISLAND = 50;
    public final int[] bell = {0, GROUND + 12, 38};
    public final boolean ruined;

    public BrightwaterLayout(long seed, IntBinaryOperator terrain, boolean ruined) {
        super(72, 0, 120, seed, terrain);
        this.ruined = ruined;
        island();
        quays();
        fleet(false);
        lighthouse(false);
        market();
        warehouses();
        shipyard();
        tavern();
        houses();
        governor();
        bellTower();
        paths();
        if (ruined) ruin();
    }

    int hill(int x, int z) {
        double d = Math.sqrt(x * x + (z + 26) * (z + 26));
        return (int) Math.round(14 * smooth((18 - d) / 18.0));
    }

    // ---------------------------------------------------------------- the island
    private void island() {
        for (int x = X0; x <= X1; x++) for (int z = Z0; z <= Z1; z++) {
            double d = Math.sqrt(x * x + z * z) + noise(x * 0.4, 0, z * 0.4) * 2;
            if (d > R) continue;
            if (d <= ISLAND - 6) ground(x, z, GROUND + hill(x, z), hill(x, z) > 2 ? "minecraft:grass_block" : "minecraft:grass_block", "minecraft:dirt", 4);
            else if (d <= ISLAND) ground(x, z, GROUND - (int) Math.round((d - (ISLAND - 6)) * 0.9), "minecraft:sand", "minecraft:sandstone", 4);
            else ground(x, z, blend(x, z, 52, R - ISLAND), "minecraft:sand", "minecraft:sandstone", 3);
            if (d <= ISLAND) for (int y = top(x, z) + 1; y < GROUND - 8; y++) if (get(x, y, z) == 0) put(x, y, z, "minecraft:stone");   // the island's root to the seabed
        }
    }

    // ---------------------------------------------------------------- quays + piers
    private void quays() {
        for (int x = -40; x <= 40; x++) for (int z = 34; z <= 44; z++) {
            for (int y = 50; y < GROUND; y++) put(x, y, z, "minecraft:stone_bricks");
            put(x, GROUND - 1, z, z == 44 ? "minecraft:chiseled_stone_bricks" : (x + z) % 2 == 0 ? "minecraft:stone_bricks" : "minecraft:polished_andesite");
            for (int y = GROUND; y <= GROUND + 3; y++) if (get(x, y, z) == 0 || isAir(x, y, z)) put(x, y, z, AIR);
            if (z == 44 && x % 2 == 0) put(x, GROUND, z, "minecraft:stone_brick_wall");
        }
        for (int px : new int[]{-26, 0, 26}) for (int z = 45; z <= 64; z++) for (int x = px - 2; x <= px + 2; x++) {
            for (int y = 40; y < GROUND; y++) if (Math.abs(x - px) == 2 && z % 5 == 0 || y == GROUND - 1) put(x, y, z, y == GROUND - 1 ? "minecraft:spruce_planks" : "minecraft:stone_brick_wall");
            if (Math.abs(x - px) == 2 && z % 4 == 0) { put(x, GROUND, z, "minecraft:spruce_fence"); put(x, GROUND + 1, z, "minecraft:lantern[hanging=false]"); }
        }
    }

    private void fleet(boolean smashed) {
        double[][] ships = {{-13, 60, 0.05}, {13, 62, -0.08}, {40, 60, 0.3}};
        for (double[] s : ships)
            if (smashed) hull((int) s[0], (int) s[1], SEA - 6, 26, 8, 6, Math.PI / 2 + s[2] + 0.6, 0.35, 0.7, 1, false);
            else hull((int) s[0], (int) s[1], SEA - 3, 26, 8, 6, Math.PI / 2 + s[2], 0, 0, 2, true);
    }

    // ---------------------------------------------------------------- the lighthouse (east point)
    private void lighthouse(boolean snapped) {
        int cx = 44, cz = -6, base = GROUND;
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) if (x * x + z * z <= 36) ground(cx + x, cz + z, base - 1, "minecraft:stone", "minecraft:stone", 20);
        tower(cx, cz, 4, base, base + 34, "minecraft:white_terracotta", 8);
        for (int y = base; y <= base + 34; y += 6) for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++)
            if (Math.abs(Math.sqrt(x * x + z * z) - 4) < 0.5) put(cx + x, y, cz + z, "minecraft:red_terracotta");
        fill(cx - 5, base + 35, cz - 5, cx + 5, base + 35, cz + 5, "minecraft:stone_bricks");
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) for (int y = base + 36; y <= base + 39; y++) {
            boolean edge = Math.abs(x) == 3 || Math.abs(z) == 3;
            put(cx + x, y, cz + z, edge ? (y == base + 39 ? "minecraft:stone_bricks" : "minecraft:glass") : y == base + 36 ? "minecraft:sea_lantern" : AIR);
        }
        put(cx, base + 37, cz, "minecraft:glowstone");
    }

    // ---------------------------------------------------------------- the market square
    private void market() {
        fill(-14, GROUND - 1, 2, 14, GROUND - 1, 20, "minecraft:polished_andesite");
        for (int x = -2; x <= 2; x++) for (int z = 9; z <= 13; z++) {                   // the fountain
            boolean rim = Math.abs(x) == 2 || z == 9 || z == 13;
            put(x, GROUND, z, rim ? "minecraft:stone_brick_wall" : "minecraft:water");
        }
        put(0, GROUND + 1, 11, "minecraft:stone_brick_wall"); put(0, GROUND + 2, 11, "minecraft:water_cauldron[level=3]");
        String[] canopy = {"red", "yellow", "blue", "green", "orange", "cyan"};
        int k = 0;
        for (int[] st : new int[][]{{-11, 5}, {-11, 11}, {-11, 17}, {9, 5}, {9, 11}, {9, 17}}) {
            for (int[] c : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) { put(st[0] + c[0], GROUND, st[1] + c[1], "minecraft:spruce_fence"); put(st[0] + c[0], GROUND + 1, st[1] + c[1], "minecraft:spruce_fence"); }
            fill(st[0], GROUND + 2, st[1], st[0] + 2, GROUND + 2, st[1] + 2, "minecraft:" + canopy[k++ % canopy.length] + "_wool");
            put(st[0] + 1, GROUND, st[1] + 1, "minecraft:barrel[facing=up]");
        }
        barrel(-10, GROUND, 6, "chests/brightwater_market");
    }

    private void warehouses() {
        for (int[] w : new int[][]{{-40, 22}, {-26, 22}, {14, 22}}) {
            house(w[0], w[1], w[0] + 11, w[1] + 9, GROUND, 6, "minecraft:stone_bricks", "minecraft:spruce_log[axis=y]", "minecraft:stone_brick_stairs", "minecraft:stone_brick_slab[type=bottom]", 's');
            for (int x = w[0] + 2; x <= w[0] + 9; x += 2) for (int y = GROUND; y <= GROUND + 1; y++) put(x, y, w[1] + 2, "minecraft:barrel[facing=up]");
            barrel(w[0] + 2, GROUND, w[1] + 7, "chests/brightwater_warehouse");
        }
        house(-12, 22, -2, 30, GROUND, 5, "minecraft:bricks", "minecraft:stripped_dark_oak_log[axis=y]", "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab[type=bottom]", 's');   // the harbourmaster
        put(-7, GROUND, 28, "minecraft:cartography_table");
        chest(-10, GROUND, 28, "south", "chests/brightwater_harbourmaster");
    }

    private void shipyard() {
        fill(28, GROUND - 1, 8, 44, GROUND - 1, 32, "minecraft:spruce_planks");
        for (int z = 12; z <= 44; z++) {                                                   // the slipway down into the sea
            int y = GROUND - 1 - Math.max(0, (z - 30) / 2);
            for (int x = 33; x <= 39; x++) put(x, y, z, "minecraft:stripped_spruce_log[axis=z]");
        }
        for (int z = 14; z <= 28; z += 2) for (double t = 0; t <= Math.PI; t += 0.25) {    // a ship's frame on the stocks
            int x = 36 + (int) Math.round(Math.cos(t) * 4), y = GROUND + (int) Math.round(Math.sin(t) * 5);
            put(x, y, z, "minecraft:stripped_oak_log[axis=y]");
        }
        for (int z = 14; z <= 28; z++) put(36, GROUND, z, "minecraft:stripped_oak_log[axis=z]");
        house(28, 8, 32, 14, GROUND, 4, "minecraft:spruce_planks", "minecraft:spruce_log[axis=y]", "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", 'e');
        chest(29, GROUND, 12, "east", "chests/brightwater_shipyard");
    }

    private void tavern() {
        house(-40, 0, -26, 14, GROUND, 6, "minecraft:spruce_planks", "minecraft:dark_oak_log[axis=y]", "minecraft:dark_oak_stairs", "minecraft:dark_oak_slab[type=bottom]", 'e');
        fill(-38, GROUND, 11, -30, GROUND, 11, "minecraft:dark_oak_slab[type=top]");
        for (int x = -37; x <= -31; x += 3) { put(x, GROUND, 4, "minecraft:dark_oak_fence"); put(x, GROUND + 1, 4, "minecraft:dark_oak_pressure_plate"); }
        barrel(-38, GROUND, 12, "chests/brightwater_tavern");
    }

    private void houses() {
        int[][] spots = {{-34, -14}, {-22, -12}, {-36, -28}, {18, -12}, {30, -14}, {20, -34}, {-20, 0}, {18, 0}};
        String[] walls = {"minecraft:white_terracotta", "minecraft:birch_planks", "minecraft:spruce_planks", "minecraft:light_gray_terracotta"};
        for (int i = 0; i < spots.length; i++) {
            int x = spots[i][0], z = spots[i][1], y = GROUND + hill(x + 3, z + 3);
            for (int dx = -1; dx <= 8; dx++) for (int dz = -1; dz <= 7; dz++) ground(x + dx, z + dz, y - 1, "minecraft:dirt_path", "minecraft:dirt", 3);
            house(x, z, x + 7, z + 6, y, 4, walls[i % walls.length], "minecraft:oak_log[axis=y]", "minecraft:spruce_stairs", "minecraft:spruce_slab[type=bottom]", i % 2 == 0 ? 's' : 'n');
            bed(x + 1, y, z + 1, 's', i % 2 == 0 ? "blue" : "red");
            put(x + 6, y, z + 5, "minecraft:crafting_table");
        }
    }

    private void governor() {
        int y = GROUND + hill(0, -26);
        for (int x = -10; x <= 10; x++) for (int z = -34; z <= -18; z++) ground(x, z, y - 1, "minecraft:polished_andesite", "minecraft:stone", 3);
        house(-8, -32, 8, -20, y, 7, "minecraft:quartz_bricks", "minecraft:stripped_birch_log[axis=y]", "minecraft:deepslate_tile_stairs", "minecraft:deepslate_tile_slab[type=bottom]", 's');
        fill(-7, y + 3, -31, 7, y + 3, -21, "minecraft:birch_planks");                           // the upper floor
        put(0, y, -30, "minecraft:lectern[facing=south]");
        chest(-6, y, -30, "south", "chests/brightwater_governor");
        bed(6, y + 4, -30, 's', "blue");
        for (int x = -2; x <= 2; x += 4) { put(x, y, -19, "minecraft:potted_azalea_bush"); }
    }

    private void bellTower() {
        for (int y = GROUND; y <= GROUND + 13; y++) for (int x = -1; x <= 1; x++) for (int z = 37; z <= 39; z++) {
            boolean open = y >= GROUND + 11 && y <= GROUND + 12 && (x == 0) != (z == 38);
            put(x, y, z, open || x == 0 && z == 38 && y > GROUND + 10 ? AIR : (y % 4 == 0 ? "minecraft:chiseled_stone_bricks" : "minecraft:stone_bricks"));
        }
        put(bell[0], bell[1], bell[2], "minecraft:bell[attachment=ceiling,facing=south]");
        fill(-1, GROUND + 14, 37, 1, GROUND + 14, 39, "minecraft:stone_brick_slab[type=bottom]");
    }

    private void paths() {
        for (int z = 20; z <= 34; z++) for (int x = -2; x <= 2; x++) if (get(x, GROUND - 1, z) == 0 || at(x, GROUND - 1, z).contains("grass")) put(x, GROUND - 1, z, "minecraft:dirt_path");
        for (int z = -18; z <= 2; z++) for (int x = -1; x <= 1; x++) {
            int y = GROUND + hill(x, z) - 1;
            if (at(x, y, z) != null && at(x, y, z).contains("grass")) put(x, y, z, "minecraft:dirt_path");
        }
        for (int x = -40; x <= 40; x += 8) { put(x, GROUND, 33, "minecraft:oak_fence"); put(x, GROUND + 1, 33, "minecraft:lantern[hanging=false]"); }
    }

    // ---------------------------------------------------------------- after the Leviathan passes
    private void ruin() {
        // the lighthouse snapped: everything above y+14 gone, its top lying broken on the rocks below
        for (int x = 39; x <= 50; x++) for (int z = -12; z <= 0; z++) for (int y = GROUND + 14 + (x % 3); y <= GROUND + 40; y++) if (get(x, y, z) != 0) put(x, y, z, AIR);
        for (int k = 0; k < 18; k++) po(52 + k / 3, GROUND - 4 + (k % 3 == 0 ? 1 : 0), -6 + (k % 5) - 2, k % 2 == 0 ? "minecraft:white_terracotta" : "minecraft:red_terracotta");
        bite(22, 44, 17, 36, GROUND + 14);                                                     // a bite out of the quays
        for (double[] s : new double[][]{{-13, 60}, {13, 62}, {40, 60}})                       // the fleet: smashed, half sunk
            for (int x = (int) s[0] - 16; x <= s[0] + 16; x++) for (int z = (int) s[1] - 10; z <= s[1] + 10; z++) for (int y = SEA - 8; y <= SEA + 24; y++) {
                String b = at(x, y, z);
                if (b != null && !b.contains("stone") && !b.contains("planks[") && z > 45) put(x, y, z, y <= SEA ? WATER : AIR);
            }
        fleet(true);
        hull(-20, 27, GROUND + 9, 18, 6, 4, 0.9, 0.2, 0.55, 1, false);                          // a ship thrown onto the warehouse roofs
        collapse(-40, GROUND, 22, -2, GROUND + 14, 32, 0.4);
        collapse(-40, GROUND, 0, -26, GROUND + 14, 14, 0.5);
        for (int[] h : new int[][]{{-34, -14}, {-22, -12}, {18, -12}, {30, -14}, {-20, 0}, {18, 0}})
            if (rnd.nextInt(4) > 0) collapse(h[0], GROUND, h[1], h[0] + 7, GROUND + 16, h[1] + 6, 0.3 + rnd.nextDouble() * 0.4);
        collapse(28, GROUND, 8, 44, GROUND + 8, 32, 0.5);
        for (int x = -1; x <= 1; x++) for (int z = 37; z <= 39; z++) for (int y = GROUND + 7; y <= GROUND + 14; y++) put(x, y, z, AIR);   // the bell tower broken, the bell gone
        for (int[] st : new int[][]{{-11, 5}, {-11, 11}, {9, 11}, {9, 17}}) fill(st[0], GROUND, st[1], st[0] + 2, GROUND + 2, st[1] + 2, AIR);   // stalls flattened
        strew(-45, -40, 45, 45, GROUND - 2, GROUND + 18, 220);
        chest(3, GROUND, 16, "north", "chests/brightwater_ruin");
        chest(-18, GROUND, 36, "north", "chests/brightwater_ruin");
    }

    public static void main(String[] a) throws Exception {
        IntBinaryOperator t = (x, z) -> 30 + (int) (8 * Math.sin(x * 0.04));
        String o = a.length > 0 ? a[0] : "bright";
        BrightwaterLayout L = new BrightwaterLayout(9L, t, false), R = new BrightwaterLayout(9L, t, true);
        System.out.println("palette " + L.palette().size() + " / ruined " + R.palette().size() + ", loot " + L.loot.size() + " / " + R.loot.size());
        L.map(o + "_map.png", 120);
        R.map(o + "_ruin_map.png", 120);
    }
}
