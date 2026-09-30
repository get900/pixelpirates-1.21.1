package net.get900.pixelpirates.world.dungeon;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.entity.mob.SeaSerpentEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * THE SERPENT'S HOLLOW - lair of the Sea Serpent (boss 2/10), rebuilt 2026-09-29.
 * Design space: y = 0 is the seabed, everything within |x|,|z| <= 22 (feature reach), so the size comes
 * from DEPTH: a flooded dome carved under the seabed, ~37 blocks across and ~20 tall above a flat
 * ~29-wide floor, so the ~11-block serpent can swim its circuits without scraping the walls.
 * <pre>
 *   seabed  THE SERPENT GATE: stone serpent head (maw over the sinkhole at x0 z-12), 2 coils, tail, obelisks, plaza
 *   dome    centre (0,-20,0), radii 18.5 x 13 x 18.5, 2-thick prismarine/coral shell, 6 rib arches
 *   floor   y -28 (standing -27): sand/gravel/coral, kelp, pickles, bones of the serpent's meals
 *   spire   coral column at the centre (to y -17), sea lanterns; the hoard nest at its foot
 *   wards   4 Tideward Stones: floor obelisks at x+-12, high ledges at (+-11, -14, 11)
 * </pre>
 */
public final class SerpentHollow {
    private SerpentHollow() {}

    private static final String P = "pixelpirates:";
    static final int CY = -20, FLOOR = -28;
    static final double RX = 18.5, RY = 13;

    private static BlockState water() { return DungeonBuilder.water(); }

    private static BlockState wet(BlockState s) {
        return s.contains(Properties.WATERLOGGED) ? s.with(Properties.WATERLOGGED, true) : s;
    }

    /** Horizontal radius of the dome's inside at height y. */
    static double radiusAt(int y) {
        double t = (y - CY) / RY;
        return t * t >= 1 ? 0 : RX * Math.sqrt(1 - t * t);
    }

    public static void build(DungeonBuilder b, int depth) {
        Random r = b.random;
        DungeonBuilder.Weathered shell = new DungeonBuilder.Weathered(Blocks.PRISMARINE_BRICKS.getDefaultState(),
                Blocks.DARK_PRISMARINE.getDefaultState(), ModBlocks.CORAL_ROCK.getDefaultState(), 0.45f);
        DungeonBuilder.Weathered floor = new DungeonBuilder.Weathered(Blocks.SAND.getDefaultState(),
                Blocks.GRAVEL.getDefaultState(), ModBlocks.CORAL_ROCK.getDefaultState(), 0.4f);

        // ---------------------------------------------------------------- the dome, flooded
        b.ellipsoid(0, CY, 0, RX, RY, RX, y -> water(), shell, 2.0);
        // flat arena floor
        for (int x = -19; x <= 19; x++)
            for (int z = -19; z <= 19; z++)
                for (int y = CY - 13; y <= FLOOR; y++) {
                    double t = (y - CY) / RY;
                    if ((x * x + z * z) / (RX * RX) + t * t < 1.0) b.set(x, y, z, y == FLOOR ? floor.pick(r) : Blocks.PRISMARINE.getDefaultState());
                }

        ribs(b, r);
        entrance(b, r, depth);
        spire(b, r);
        floorLife(b, r);

        // ---------------------------------------------------------------- Tideward Stones
        List<BlockPos> wards = new ArrayList<>();
        wards.add(floorWard(b, 12, 0));
        wards.add(floorWard(b, -12, 0));
        wards.add(ledgeWard(b, r, 11, 11));
        wards.add(ledgeWard(b, r, -11, 11));

        // ---------------------------------------------------------------- guards + the serpent
        b.spawnMob(P + "kraken_tentacle", 10, FLOOR + 1, 3);
        b.spawnMob(P + "kraken_tentacle", -10, FLOOR + 1, -3);
        b.spawnMob(P + "sea_serpent", 0, CY, 9, e -> { if (e instanceof SeaSerpentEntity s) s.setWards(wards); });
    }

    // ------------------------------------------------------------------ ribs: the tunnel-of-bones look
    private static void ribs(DungeonBuilder b, Random r) {
        BlockState rib = Blocks.DARK_PRISMARINE.getDefaultState();
        for (int k = 0; k < 6; k++) {
            double a = Math.PI * 2 * k / 6 + Math.PI / 6;
            for (int y = FLOOR + 1; y <= CY + 12; y++) {
                double rad = radiusAt(y) - 0.6;
                if (rad <= 1) continue;
                int x = (int) Math.round(Math.cos(a) * rad), z = (int) Math.round(Math.sin(a) * rad);
                b.set(x, y, z, rib);
                b.set(x + (int) Math.signum(Math.round(-Math.sin(a) * 1)), y, z + (int) Math.signum(Math.round(Math.cos(a) * 1)), rib);
                if ((y - FLOOR) % 5 == 0) b.set(x, y, z, Blocks.SEA_LANTERN.getDefaultState());
            }
        }
    }

    // ------------------------------------------------------------------ the Serpent Gate: shaft, plaza, stone head + coils
    /**
     * The entrance is a colossal stone serpent rising out of the seabed - jaws gaping over the sinkhole
     * (swim into the mouth and down the throat), glowing sea-lantern eyes, swept horns, a spined crest, two
     * body coils arching out of the sand behind it and four lit obelisks. Tall enough to break the surface
     * of most of the Siren Sea, so it can be spotted from a ship.
     */
    private static void entrance(DungeonBuilder b, Random r, int depth) {
        final int cz = -12;
        // shaft down into the dome roof, lined with lantern-banded dark prismarine
        for (int x = -4; x <= 4; x++)
            for (int z = cz - 4; z <= cz + 4; z++) {
                double d = Math.sqrt(x * x + (z - cz) * (z - cz));
                if (d > 4.2) continue;
                for (int y = -12; y <= 0; y++) {
                    if (d <= 2.6) b.set(x, y, z, water());
                    else if (y < 0) b.set(x, y, z, (y & 3) == 0 ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.DARK_PRISMARINE.getDefaultState());
                }
            }
        plaza(b, r, cz);
        head(b, r, cz);
        coil(b, 0, 8, 5.0, 4);
        coil(b, 0, 17, 4.0, 3);
        tail(b, 21);
        for (int[] o : new int[][]{{-10, -19}, {10, -19}, {-11, -5}, {11, -5}}) obelisk(b, o[0], o[1], Math.min(12, Math.max(7, depth)));
    }

    private static void plaza(DungeonBuilder b, Random r, int cz) {
        for (int x = -12; x <= 12; x++)
            for (int z = cz - 10; z <= cz + 12; z++) {
                double d = Math.sqrt(x * x + (z - cz) * (z - cz));
                if (d > 12 || d <= 2.6) continue;
                BlockState tile = d > 10.8 ? Blocks.DARK_PRISMARINE.getDefaultState()
                        : Math.abs(d - 8) < 0.5 && ((int) Math.round(Math.atan2(z - cz, x) * 4 / Math.PI) & 1) == 0 ? Blocks.SEA_LANTERN.getDefaultState()
                        : ((x + z) & 3) == 0 ? Blocks.PRISMARINE.getDefaultState() : Blocks.PRISMARINE_BRICKS.getDefaultState();
                if (r.nextInt(9) == 0 && d > 3.5) tile = ModBlocks.CORAL_ROCK.getDefaultState();
                b.set(x, 0, z, tile);
                b.set(x, -1, z, Blocks.PRISMARINE.getDefaultState());
            }
    }

    private static void head(DungeonBuilder b, Random r, int cz) {
        BlockState skin = Blocks.PRISMARINE_BRICKS.getDefaultState(), dark = Blocks.DARK_PRISMARINE.getDefaultState();
        BlockState fang = Blocks.CALCITE.getDefaultState();
        // the head tapers from a narrow snout (z-21) to a broad skull (z-3); the skull line slopes up from the snout
        for (int z = -21; z <= -3; z++) {
            double t = (z + 21) / 18.0;                                   // 0 at the snout tip, 1 at the back
            int hw = (int) Math.round(4 + 3 * Math.min(1, t * 1.6));      // half width 4 -> 7
            int top = (int) Math.round(8 + 5 * t);                         // skull top 8 -> 13
            // lower jaw floor (+ lips), throat hole cut through it
            for (int x = -hw; x <= hw; x++) {
                boolean throat = Math.sqrt(x * x + (z - cz) * (z - cz)) <= 2.6;
                b.set(x, 1, z, throat ? water() : dark);
                if (Math.abs(x) == hw) b.set(x, 2, z, skin);
            }
            // upper jaw: a shell from y7 to the sloping top, with stair-stepped edges
            for (int x = -hw; x <= hw; x++)
                for (int y = 7; y <= top; y++) {
                    boolean edge = Math.abs(x) == hw || y == 7 || y == top;
                    b.set(x, y, z, edge && r.nextInt(5) == 0 ? dark : skin);
                }
            for (int x = -hw + 1; x <= hw - 1; x++)
                b.set(x, top + 1, z, wet(Blocks.PRISMARINE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH)));
            b.set(-hw, top + 1, z, wet(Blocks.PRISMARINE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST)));
            b.set(hw, top + 1, z, wet(Blocks.PRISMARINE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST)));
            // cheeks close the rear of the mouth
            if (z >= -12) for (int y = 2; y <= 6; y++) { b.set(-hw, y, z, skin); b.set(hw, y, z, skin); }
        }
        for (int x = -6; x <= 6; x++) for (int y = 2; y <= 6; y++) b.set(x, y, -3, dark);
        // fangs: upper row hanging, lower row rising, plus side teeth
        for (int x : new int[]{-3, -1, 1, 3}) {
            b.set(x, 6, -21, fang);
            if (Math.abs(x) == 3) b.set(x, 5, -21, fang);
            b.set(x, 2, -21, fang);
        }
        for (int z : new int[]{-18, -15}) for (int sx = -1; sx <= 1; sx += 2) { b.set(sx * 4, 6, z, fang); b.set(sx * 4, 2, z, fang); }
        // yellow serpent eyes with a slit pupil, under a jutting brow; nostrils; swept horns
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int y = 9; y <= 10; y++) for (int z = -15; z <= -13; z++)
                b.set(sx * 7, y, z, z == -14 ? Blocks.OBSIDIAN.getDefaultState() : Blocks.OCHRE_FROGLIGHT.getDefaultState());
            for (int z = -16; z <= -12; z++) b.set(sx * 8, 11, z, dark);
            b.set(sx * 8, 11, -16, wet(Blocks.DARK_PRISMARINE_SLAB.getDefaultState()));
            b.set(sx * 2, 9, -21, Blocks.OBSIDIAN.getDefaultState());
            for (int i = 0; i <= 8; i++) b.set(sx * (6 + i / 3), 13 + i, -4 + i, i == 8 ? fang : dark);
        }
        // spined crest down the skull
        int[][] crest = {{-15, 2}, {-12, 3}, {-9, 4}, {-6, 4}, {-3, 3}};
        for (int[] c : crest) {
            int top = (int) Math.round(8 + 5 * ((c[0] + 21) / 18.0)) + 2;
            for (int y = top; y < top + c[1]; y++) b.set(0, y, c[0], wet(Blocks.PRISMARINE_WALL.getDefaultState()));
        }
        // the neck: from the back of the skull it bows down into the sand, where the first coil rises
        for (int z = -3; z <= 3; z++) {
            int yc = (int) Math.round(8 - (z + 3) * 1.1);
            for (int dx = -3; dx <= 3; dx++)
                for (int dy = -2; dy <= 2; dy++)
                    if (dx * dx + dy * dy <= 10 && yc + dy >= 1) b.set(dx, yc + dy, z, dy == 2 ? Blocks.DARK_PRISMARINE.getDefaultState() : skin);
        }
    }

    /** One body coil arching out of the sand: a half-ring of radius {@code rad} along z, spined on top. */
    private static void coil(DungeonBuilder b, int x0, int zc, double rad, int w) {
        BlockState skin = Blocks.PRISMARINE_BRICKS.getDefaultState(), belly = Blocks.PRISMARINE.getDefaultState();
        int half = w / 2 + 1;
        for (int step = 0; step <= 48; step++) {
            double a = Math.PI * step / 48;
            int z = zc + (int) Math.round(Math.cos(a) * rad), yc = (int) Math.round(Math.sin(a) * rad) + 1;
            for (int dx = -half; dx <= half; dx++)
                for (int dy = -2; dy <= 2; dy++) {
                    if (dx * dx + dy * dy > half * half + 1 || yc + dy < 1) continue;
                    b.set(x0 + dx, yc + dy, z, dy == 2 ? Blocks.DARK_PRISMARINE.getDefaultState() : dy <= -1 ? belly : skin);
                }
            if (step % 8 == 4) b.set(x0, yc + 3, z, wet(Blocks.PRISMARINE_WALL.getDefaultState()));
            if (step % 12 == 6) { b.set(x0 + half, yc, z, Blocks.SEA_LANTERN.getDefaultState()); b.set(x0 - half, yc, z, Blocks.SEA_LANTERN.getDefaultState()); }
        }
    }

    private static void tail(DungeonBuilder b, int z) {
        for (int y = 1; y <= 4; y++) b.set(0, y, z, Blocks.DARK_PRISMARINE.getDefaultState());
        for (int dx = -3; dx <= 3; dx++) {
            int h = 5 + (3 - Math.abs(dx));
            b.set(dx, h, z, wet(Blocks.PRISMARINE_WALL.getDefaultState()));
            if (Math.abs(dx) <= 1) b.set(dx, h - 1, z, Blocks.DARK_PRISMARINE.getDefaultState());
        }
    }

    private static void obelisk(DungeonBuilder b, int x, int z, int h) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            b.set(x + dx, 0, z + dz, Blocks.DARK_PRISMARINE.getDefaultState());
            b.set(x + dx, 1, z + dz, Math.abs(dx) + Math.abs(dz) == 2 ? wet(Blocks.DARK_PRISMARINE_SLAB.getDefaultState()) : Blocks.DARK_PRISMARINE.getDefaultState());
        }
        for (int y = 2; y < h; y++) b.set(x, y, z, (y % 4 == 0) ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.PRISMARINE_BRICKS.getDefaultState());
        b.set(x, h, z, Blocks.SEA_LANTERN.getDefaultState());
        b.set(x, h + 1, z, wet(Blocks.PRISMARINE_WALL.getDefaultState()));
    }

    // ------------------------------------------------------------------ central coral spire + hoard
    private static void spire(DungeonBuilder b, Random r) {
        for (int y = FLOOR + 1; y <= CY + 3; y++) {
            double rad = y < FLOOR + 5 ? 2.8 : y < CY - 2 ? 2.2 : 1.5;
            for (int x = -3; x <= 3; x++)
                for (int z = -3; z <= 3; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d > rad) continue;
                    BlockState s = d < 0.8 && (y % 4 == 0) ? Blocks.SEA_LANTERN.getDefaultState()
                            : r.nextInt(5) == 0 ? ModBlocks.CORAL_ROCK.getDefaultState()
                            : r.nextBoolean() ? Blocks.PRISMARINE_BRICKS.getDefaultState() : Blocks.DARK_PRISMARINE.getDefaultState();
                    b.set(x, y, z, s);
                }
        }
        b.set(0, CY + 4, 0, ModBlocks.PEARL_BLOCK.getDefaultState());
        for (Direction d : Direction.Type.HORIZONTAL)
            b.set(d.getOffsetX() * 2, CY + 3, d.getOffsetZ() * 2, Blocks.BRAIN_CORAL_BLOCK.getDefaultState());
        // the hoard nest at the spire's foot: bones, pearls, two chests
        for (int[] p : new int[][]{{-4, 3}, {-3, 4}, {4, 3}, {3, 4}, {0, 5}})
            b.set(p[0], FLOOR + 1, p[1], r.nextBoolean() ? Blocks.BONE_BLOCK.getDefaultState() : ModBlocks.PEARL_BLOCK.getDefaultState());
        b.chest(-2, FLOOR + 1, 4, Direction.SOUTH, "chests/phase2_treasure");
        b.chest(2, FLOOR + 1, 4, Direction.SOUTH, "chests/phase2_treasure");
        b.chest(0, FLOOR + 1, -4, Direction.NORTH, "chests/phase2_common");
        b.set(0, FLOOR + 1, 4, Blocks.GOLD_BLOCK.getDefaultState());
    }

    // ------------------------------------------------------------------ floor life
    private static void floorLife(DungeonBuilder b, Random r) {
        for (int i = 0; i < 90; i++) {
            int x = r.nextInt(29) - 14, z = r.nextInt(29) - 14;
            if (x * x + z * z < 16 || x * x + z * z > 13 * 13) continue;
            int y = FLOOR + 1;
            int roll = r.nextInt(10);
            if (roll < 4) b.set(x, y, z, Blocks.SEAGRASS.getDefaultState());
            else if (roll < 6) {
                int h = 3 + r.nextInt(8);
                for (int k = 0; k < h; k++) b.set(x, y + k, z, Blocks.KELP_PLANT.getDefaultState());
                b.set(x, y + h, z, Blocks.KELP.getDefaultState().with(Properties.AGE_25, 20));
            } else if (roll < 7) b.set(x, y, z, Blocks.SEA_PICKLE.getDefaultState().with(Properties.PICKLES, 1 + r.nextInt(4)).with(Properties.WATERLOGGED, true));
            else if (roll < 8) b.set(x, y, z, r.nextBoolean() ? Blocks.TUBE_CORAL_BLOCK.getDefaultState() : Blocks.HORN_CORAL_BLOCK.getDefaultState());
            else if (roll < 9) { b.set(x, y, z, Blocks.BONE_BLOCK.getDefaultState()); if (r.nextBoolean()) b.set(x, y + 1, z, Blocks.BONE_BLOCK.getDefaultState()); }
            else b.set(x, y, z, ModBlocks.PEARL_BLOCK.getDefaultState());
        }
        // a whale's ribcage half buried in the sand
        for (int k = 0; k < 5; k++) {
            int x = -8 + k * 2;
            for (int y = FLOOR + 1; y <= FLOOR + 4; y++) { b.set(x, y, -9 + (y - FLOOR) / 2, Blocks.BONE_BLOCK.getDefaultState()); }
            b.set(x, FLOOR + 5, -7, Blocks.BONE_BLOCK.getDefaultState());
        }
    }

    // ------------------------------------------------------------------ wards
    private static BlockPos floorWard(DungeonBuilder b, int x, int z) {
        int y = FLOOR + 1;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            b.set(x + dx, y, z + dz, Blocks.DARK_PRISMARINE.getDefaultState());
            if (Math.abs(dx) + Math.abs(dz) == 2) b.set(x + dx, y + 1, z + dz, wet(Blocks.PRISMARINE_BRICK_SLAB.getDefaultState()));
        }
        b.set(x, y + 1, z, Blocks.PRISMARINE_BRICKS.getDefaultState());
        b.set(x, y + 2, z, ModBlocks.SERPENT_WARD.getDefaultState());
        b.set(x, y + 3, z, Blocks.SEA_LANTERN.getDefaultState());
        return b.pos(x, y + 2, z);
    }

    private static BlockPos ledgeWard(DungeonBuilder b, Random r, int x, int z) {
        int y = -15;
        // a coral shelf grown out of the wall, reached by swimming up
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (Math.abs(dx) + Math.abs(dz) > 3) continue;
            b.set(x + dx, y, z + dz, r.nextInt(3) == 0 ? ModBlocks.CORAL_ROCK.getDefaultState() : Blocks.DARK_PRISMARINE.getDefaultState());
            b.set(x + dx, y - 1, z + dz, Blocks.PRISMARINE.getDefaultState());
        }
        b.set(x, y + 1, z, Blocks.PRISMARINE_BRICKS.getDefaultState());
        b.set(x, y + 2, z, ModBlocks.SERPENT_WARD.getDefaultState());
        b.set(x, y + 3, z, Blocks.SEA_LANTERN.getDefaultState());
        b.set(x + 1, y + 1, z, Blocks.FIRE_CORAL_BLOCK.getDefaultState());
        b.set(x - 1, y + 1, z + 1, Blocks.BUBBLE_CORAL_BLOCK.getDefaultState());
        return b.pos(x, y + 2, z);
    }
}
