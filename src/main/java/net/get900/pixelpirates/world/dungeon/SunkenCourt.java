package net.get900.pixelpirates.world.dungeon;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.block.custom.TideSluiceBlock;
import net.get900.pixelpirates.entity.mob.AbyssalKingEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

/**
 * THE SUNKEN COURT - lair of the Abyssal King (boss 5/10), rebuilt 2026-09-29. Design space: y 0 = seabed,
 * everything within |x|,|z| <= 22. Three parts, top to bottom:
 * <pre>
 *   palace   the walled forecourt, gate towers, corner towers, coral gardens and the great hall with its (empty)
 *            throne + treasury - the approach, guarded by corrupted divers and drowned sailors
 *   well     THE WELL OF TIDES in the forecourt at (0,-13): a pillared well-head over a 5x5 shaft, a spiral of
 *            steps round a lantern column down 30 blocks to a short passage and THE COURT DOOR (a door holds
 *            the ocean back when the hall is drained - doors never let water through)
 *   court    THE KING'S COURT: sealed flooded hall under the palace, centre (0, CZ), radius 14, walls to y CY then
 *            a dome (ry 9); floor y -30 (stand -29), ~17 tall. Six columns, dome ribs, a three-tier dais with the
 *            throne and the royal hoard, conduits, and the FOUR TIDE SLUICES in the walls at (+-10, -28, CZ+-10)
 * </pre>
 * The King is given the hall's exact shape ({@link AbyssalKingEntity.Court}) and the sluice positions in world
 * coordinates before he spawns, and drains/floods exactly that interior. Keep {@link #inside} and
 * {@code AbyssalKingEntity.Court#inside} identical, or the drain would eat walls or leave water pockets.
 */
public final class SunkenCourt {
    private SunkenCourt() {}

    private static final String P = "pixelpirates:";
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    static final int CZ = 6, R = 14, FLOOR = -30, CY = -20, RY = 9;
    static final int WX = 0, WZ = -13;                   // the Well of Tides

    private static IntFunction<BlockState> fluid(int depth) {
        return y -> y <= depth ? DungeonBuilder.water() : AIR;
    }

    private static DungeonBuilder.Weathered w(BlockState a, BlockState b, BlockState c, float chance) {
        return new DungeonBuilder.Weathered(a, b, c, chance);
    }

    private static BlockState wet(BlockState s) {
        return s.contains(Properties.WATERLOGGED) ? s.with(Properties.WATERLOGGED, true) : s;
    }

    /** The hall interior (design coords): a cylinder from the floor up to CY under an elliptical dome. */
    static boolean inside(int x, int y, int z, double r, double ry) {
        if (y <= FLOOR) return false;
        double d2 = x * x + (z - CZ) * (z - CZ);
        if (y <= CY) return d2 < r * r;
        double t = (y - CY) / ry;
        return t < 1 && d2 < r * r * (1 - t * t);
    }

    public static void build(DungeonBuilder b, int depth) {
        palace(b, depth);
        court(b);                // before the well: its shell would otherwise wall up the passage + door
        well(b, depth);
    }

    // =====================================================================================
    // THE WELL OF TIDES + the spiral down to the Court Door
    // =====================================================================================
    private static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};

    private static void well(DungeonBuilder b, int depth) {
        Random r = b.random;
        BlockState DP = Blocks.DARK_PRISMARINE.getDefaultState(), PB = Blocks.PRISMARINE_BRICKS.getDefaultState(),
                SL = Blocks.SEA_LANTERN.getDefaultState(), VEIN = ModBlocks.LUMINOUS_VEIN.getDefaultState();
        DungeonBuilder.Weathered wall = w(DP, ModBlocks.ABYSSAL_SLATE.getDefaultState(), PB, 0.3f);
        IntFunction<BlockState> f = fluid(depth);
        // shaft: 7x7 walls, 5x5 water, from the plaza down to the passage floor
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++)
            for (int y = FLOOR; y <= 0; y++) {
                boolean shell = Math.abs(x) == 3 || Math.abs(z) == 3 || y == FLOOR;
                b.set(WX + x, y, WZ + z, shell ? (y % 6 == 0 && !(y == FLOOR) ? SL : wall.pick(r)) : DungeonBuilder.water());
            }
        // lantern column + one step per ring cell, dropping a block each (swim or walk down)
        for (int y = FLOOR + 1; y <= 0; y++) b.set(WX, y, WZ, y % 4 == 0 ? SL : DP);
        for (int i = 0; i <= 28; i++) {
            int[] c = RING[i % 8];
            b.set(WX + c[0], -1 - i, WZ + c[1], i % 8 == 0 ? VEIN : PB);
        }
        // well-head: rim, four pillars, a crown ring with lanterns, a chained lantern over the shaft
        for (int x = -3; x <= 3; x++) for (int z = -3; z <= 3; z++) {
            boolean rim = Math.abs(x) == 3 || Math.abs(z) == 3;
            if (!rim) continue;
            b.set(WX + x, 0, WZ + z, DP);
            boolean corner = Math.abs(x) == 3 && Math.abs(z) == 3;
            b.set(WX + x, 1, WZ + z, corner ? DP : wet(Blocks.PRISMARINE_BRICK_SLAB.getDefaultState()).with(Properties.WATERLOGGED, 1 <= depth));
            if (corner) {
                for (int y = 2; y <= 5; y++) b.set(WX + x, y, WZ + z, y == 5 ? SL : f.apply(y) == AIR ? Blocks.PRISMARINE_WALL.getDefaultState()
                        : Blocks.PRISMARINE_WALL.getDefaultState().with(Properties.WATERLOGGED, true));
            }
            b.set(WX + x, 6, WZ + z, (x + z) % 2 == 0 ? DP : PB);
        }
        for (int y = 3; y <= 5; y++) b.set(WX, y, WZ, Blocks.CHAIN.getDefaultState().with(Properties.WATERLOGGED, y <= depth));
        b.set(WX, 2, WZ, SL);
        b.set(WX, 7, WZ, VEIN);
        b.barrelLoot(WX - 2, 1, WZ + 4, "chests/court_armory");
        b.barrelLoot(WX + 2, 1, WZ + 4, "chests/court_armory");
        // passage south out of the shaft, through the court wall, to the Court Door
        for (int z = WZ + 3; z <= CZ - R - 1; z++) for (int x = -2; x <= 2; x++)
            for (int y = FLOOR; y <= FLOOR + 4; y++) {
                boolean hole = Math.abs(x) <= 1 && y >= FLOOR + 1 && y <= FLOOR + 3;
                b.set(x, y, z, hole ? DungeonBuilder.water() : wall.pick(r));
            }
        int dz = CZ - R;                                     // the door plane, just outside the drained interior
        for (int x = -2; x <= 2; x++) for (int y = FLOOR; y <= FLOOR + 4; y++) b.set(x, y, dz, y == FLOOR + 3 && x == 0 ? SL : DP);
        b.set(0, FLOOR + 1, dz, Blocks.DARK_OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.NORTH).with(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        b.set(0, FLOOR + 2, dz, Blocks.DARK_OAK_DOOR.getDefaultState().with(DoorBlock.FACING, Direction.NORTH).with(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    // =====================================================================================
    // THE KING'S COURT
    // =====================================================================================
    private static void court(DungeonBuilder b) {
        Random r = b.random;
        BlockState DP = Blocks.DARK_PRISMARINE.getDefaultState(), PB = Blocks.PRISMARINE_BRICKS.getDefaultState(),
                PR = Blocks.PRISMARINE.getDefaultState(), SL = Blocks.SEA_LANTERN.getDefaultState(),
                GOLD = Blocks.GOLD_BLOCK.getDefaultState(), VEIN = ModBlocks.LUMINOUS_VEIN.getDefaultState();
        DungeonBuilder.Weathered shell = w(PB, DP, ModBlocks.ABYSSAL_SLATE.getDefaultState(), 0.3f);
        // ---- shell + water + floor
        for (int x = -R - 2; x <= R + 2; x++)
            for (int z = CZ - R - 2; z <= CZ + R + 2; z++) {
                double d = Math.sqrt(x * x + (z - CZ) * (z - CZ));
                double ang = Math.atan2(x, z - CZ);
                boolean rib = Math.abs(Math.sin(4 * ang)) < 0.14;
                for (int y = FLOOR - 1; y <= CY + RY + 2; y++) {
                    if (inside(x, y, z, R, RY)) { b.set(x, y, z, DungeonBuilder.water()); continue; }
                    boolean inShell = y >= FLOOR - 1 && (y <= FLOOR ? d < R + 2 : inside(x, y, z, R + 2, RY + 2));
                    if (!inShell) continue;
                    if (y == FLOOR && d < R) { b.set(x, y, z, floor(x, z - CZ, d, r)); continue; }
                    boolean inner = inside(x + (int) Math.signum(-x), y, z + (int) Math.signum(CZ - z), R, RY) || inside(x, y - 1, z, R, RY);
                    b.set(x, y, z, rib ? (inner && y % 4 == 0 ? SL : DP) : shell.pick(r));
                }
            }
        // ---- six columns with lantern capitals (cover from tridents and the riptide)
        for (int k = 0; k < 6; k++) {
            double a = Math.toRadians(30 + 60 * k);
            int cx = (int) Math.round(Math.sin(a) * 9), cz = CZ + (int) Math.round(Math.cos(a) * 9);
            for (int y = FLOOR + 1; y <= CY; y++)
                for (int[] o : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}})
                    b.set(cx + o[0], y, cz + o[1], o[0] == 0 && o[1] == 0 ? (y % 5 == 0 ? SL : DP) : (y == FLOOR + 1 || y == CY - 1 ? DP : PB));
            for (int[] o : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) b.set(cx + o[0], CY - 1, cz + o[1], wet(Blocks.PRISMARINE_BRICK_SLAB.getDefaultState()
                    .with(Properties.SLAB_TYPE, net.minecraft.block.enums.SlabType.TOP)));
        }
        // ---- the dais (three tiers), the throne, the royal hoard
        int[][] tiers = {{6, CZ + 8}, {4, CZ + 10}, {2, CZ + 12}};
        for (int t = 0; t < 3; t++) {
            int y = FLOOR + 1 + t, hx = tiers[t][0], z0 = tiers[t][1];
            for (int x = -hx; x <= hx; x++) for (int z = z0; z <= CZ + 14; z++) {
                if (x * x + (z - CZ) * (z - CZ) >= (R + 1) * (R + 1)) continue;
                b.set(x, y, z, z == z0 ? wet(Blocks.PRISMARINE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH))
                        : (Math.abs(x) == hx ? DP : PB));
            }
        }
        int ty = FLOOR + 4, tz = CZ + 13;
        b.set(0, ty, tz, wet(Blocks.DARK_PRISMARINE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH)));
        b.set(-1, ty, tz, GOLD); b.set(1, ty, tz, GOLD);
        for (int y = ty; y <= ty + 6; y++) { b.set(0, y, tz + 1, y == ty + 6 ? SL : GOLD); b.set(-1, y, tz + 1, y >= ty + 4 ? DP : GOLD); b.set(1, y, tz + 1, y >= ty + 4 ? DP : GOLD); }
        b.set(0, ty + 7, tz + 1, VEIN);
        b.chest(-2, ty, tz, Direction.NORTH, "chests/court_hoard");
        b.chest(2, ty, tz, Direction.NORTH, "chests/phase5_treasure");
        for (int sx : new int[]{-5, 5}) {                                                    // conduits on pedestals
            b.set(sx, FLOOR + 1, CZ + 7, DP);
            b.set(sx, FLOOR + 2, CZ + 7, Blocks.CONDUIT.getDefaultState().with(Properties.WATERLOGGED, true));
        }
        for (int i = 0; i < 26; i++) {                                                       // sea pickles in the joints
            int x = r.nextInt(25) - 12, z = CZ + r.nextInt(25) - 12;
            if (!inside(x, FLOOR + 1, z, R - 1, RY) || !b.isWater(x, FLOOR + 1, z)) continue;
            b.set(x, FLOOR + 1, z, Blocks.SEA_PICKLE.getDefaultState().with(Properties.PICKLES, 1 + r.nextInt(4)).with(Properties.WATERLOGGED, true));
        }
        // ---- THE TIDE SLUICES
        List<BlockPos> sluices = new ArrayList<>();
        for (int[] s : new int[][]{{10, CZ - 10}, {-10, CZ - 10}, {10, CZ + 10}, {-10, CZ + 10}}) sluices.add(sluice(b, s[0], s[1]));
        // ---- the court: the King and two wardens
        BlockPos c = b.pos(0, 0, CZ);
        int oy = b.pos(0, 0, 0).getY();
        AbyssalKingEntity.Court court = new AbyssalKingEntity.Court(c.getX(), c.getZ(), R, oy + FLOOR + 1, oy + CY, RY);
        b.spawnMob(P + "tide_warden", -4, FLOOR + 1, CZ + 5);
        b.spawnMob(P + "tide_warden", 4, FLOOR + 1, CZ + 5);
        b.spawnMob(P + "abyssal_king", 0, FLOOR + 1, CZ + 3, e -> { if (e instanceof AbyssalKingEntity k) k.setCourt(court, sluices); });
    }

    private static BlockState floor(int x, int dz, double d, Random r) {
        if (d < 1.5) return ModBlocks.LUMINOUS_VEIN.getDefaultState();
        if (Math.abs(d - 5) < 0.5 || Math.abs(d - 10.5) < 0.5) return Blocks.DARK_PRISMARINE.getDefaultState();
        double ang = Math.atan2(x, dz);
        if (Math.abs(Math.sin(4 * ang)) < 0.08 && d < 10) return ModBlocks.LUMINOUS_VEIN.getDefaultState();
        return ((x + dz) & 1) == 0 ? Blocks.PRISMARINE_BRICKS.getDefaultState() : Blocks.PRISMARINE.getDefaultState();
    }

    /** A sluice set into the wall at (x, eye level, z), facing the hall centre, framed, with a drain grate and a pipe. */
    private static BlockPos sluice(DungeonBuilder b, int x, int z) {
        int y = FLOOR + 2;
        Direction face = x > 0 ? Direction.WEST : Direction.EAST;
        int sx = Integer.signum(x);
        for (int dy = -1; dy <= 1; dy++) for (int dzz = -1; dzz <= 1; dzz++)
            b.set(x, y + dy, z + dzz, dy == 1 && dzz == 0 ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.DARK_PRISMARINE.getDefaultState());
        for (int yy = y + 2; yy <= CY; yy++) b.set(x + sx, yy, z, (yy & 1) == 0 ? Blocks.OXIDIZED_CUT_COPPER.getDefaultState() : Blocks.WEATHERED_CUT_COPPER.getDefaultState());
        b.set(x, y, z, ModBlocks.TIDE_SLUICE.getDefaultState().with(TideSluiceBlock.FACING, face));
        b.set(x - sx, FLOOR, z, Blocks.IRON_BARS.getDefaultState());                      // the drain grate
        b.set(x - sx, FLOOR - 1, z, Blocks.DARK_PRISMARINE.getDefaultState());
        return b.pos(x, y, z).toImmutable();
    }

    /**
     * The Sunken Court of the Abyssal King - the endgame palace, filling the whole +-22 footprint.
     * Front (-z) to back: walled forecourt (gate + twin gate towers + guardian statues, corner towers
     * with glowing spires, coral gardens, a lamp-lined avenue) -> palace facade (pilasters, pediment,
     * grand arch) -> great hall sunk two blocks into the seabed (colonnades, chandeliers, runner,
     * guard spawners in the aisles) -> three-tier dais and throne under a rose window, conduits either
     * side -> a treasury hidden under the dais, reached by a ladder shaft. Everything open floods to the
     * real waterline (y <= depth), so it reads the same in a trench or a shallow shelf.
     */
    static void palace(DungeonBuilder b, int depth) {
        Random r = b.random;
        IntFunction<BlockState> f = fluid(depth);
        BlockState PB = Blocks.PRISMARINE_BRICKS.getDefaultState(), DP = Blocks.DARK_PRISMARINE.getDefaultState(),
                PR = Blocks.PRISMARINE.getDefaultState(), SL = Blocks.SEA_LANTERN.getDefaultState(),
                GOLD = Blocks.GOLD_BLOCK.getDefaultState(), VEIN = ModBlocks.LUMINOUS_VEIN.getDefaultState(),
                SLATE = ModBlocks.ABYSSAL_SLATE.getDefaultState();
        DungeonBuilder.Weathered wall = w(PB, PR, DP, 0.35f);
        DungeonBuilder.Weathered dark = w(DP, SLATE, PB, 0.25f);
        java.util.function.BiFunction<BlockState, Integer, BlockState> wl =
                (s, y) -> s.contains(Properties.WATERLOGGED) ? s.with(Properties.WATERLOGGED, y <= depth) : s;
        final int PX = 13, PZ0 = -5, PZ1 = 21, FLOOR = -2, ROOF = 12;

        // ---- site: foundation plaza, clear the water column above ----------------------------------
        for (int x = -21; x <= 21; x++) for (int z = -22; z <= 22; z++) {
            for (int y = -4; y <= -1; y++) b.set(x, y, z, SLATE);
            boolean axis = x == 0 || z == -13;
            boolean grid = Math.floorMod(x, 6) == 0 || Math.floorMod(z, 6) == 0;
            b.set(x, 0, z, axis ? VEIN : grid ? PB : dark.pick(r));
            for (int y = 1; y <= 22; y++) b.set(x, y, z, f.apply(y));
        }
        // ---- outer wall with crenellations + gate ---------------------------------------------------
        for (int i = -20; i <= 20; i++)
            for (int[] p : new int[][]{{i, -21}, {i, 21}, {-20, i}, {20, i}}) {
                for (int y = 1; y <= 5; y++) b.set(p[0], y, p[1], y == 5 ? DP : wall.pick(r));
                if ((i & 1) == 0) b.set(p[0], 6, p[1], DP);
                if (Math.floorMod(i, 8) == 4) b.set(p[0], 3, p[1], SL);
            }
        for (int x = -3; x <= 3; x++) for (int y = 1; y <= 4; y++) b.set(x, y, -21, f.apply(y));
        b.set(-3, 4, -21, DP); b.set(3, 4, -21, DP);                                          // arch shoulders
        for (int sx : new int[]{-5, 5}) {                                                     // gate towers
            for (int x = sx - 1; x <= sx + 1; x++) for (int z = -22; z <= -20; z++) for (int y = 1; y <= 9; y++)
                b.set(x, y, z, (x == sx - 1 || x == sx + 1 || z == -22 || z == -20) ? wall.pick(r) : f.apply(y));
            b.set(sx, 10, -21, DP); b.set(sx, 11, -21, SL);
            b.set(sx, 6, -22, SL);
        }
        // ---- corner towers with domes and glowing spires --------------------------------------------
        for (int[] c : new int[][]{{-17, -18}, {17, -18}, {-17, 18}, {17, 18}}) {
            b.cylinder(c[0], c[1], 3.2, 1, 12, wall, f, true);
            for (int a = 0; a < 8; a++) {
                double ang = a * Math.PI / 4;
                b.set(c[0] + (int) Math.round(Math.cos(ang) * 2.7), 9, c[1] + (int) Math.round(Math.sin(ang) * 2.7), SL);
            }
            b.cylinder(c[0], c[1], 3.4, 13, 13, dark, f, false);
            b.cylinder(c[0], c[1], 2.3, 14, 14, w(PR, PB, DP, 0.3f), f, false);
            b.cylinder(c[0], c[1], 1.2, 15, 15, w(PR, PB, DP, 0.3f), f, false);
            b.set(c[0], 16, c[1], DP); b.set(c[0], 17, c[1], SL);
            b.set(c[0], 18, c[1], wl.apply(Blocks.PRISMARINE_WALL.getDefaultState(), 18));
        }
        // ---- processional avenue: lamp posts, flush lights --------------------------------------------
        for (int z = -20; z <= PZ0 - 3; z++) {
            for (int x = -2; x <= 2; x++) b.set(x, 0, z, Math.abs(x) == 2 ? DP : PR);
            if (Math.floorMod(z, 4) == 0) b.set(0, 0, z, SL);
        }
        for (int z = -18; z <= PZ0 - 4; z += 5)
            for (int sx : new int[]{-4, 4}) {
                b.set(sx, 1, z, DP);
                b.set(sx, 2, z, wl.apply(Blocks.PRISMARINE_WALL.getDefaultState(), 2));
                b.set(sx, 3, z, wl.apply(Blocks.PRISMARINE_WALL.getDefaultState(), 3));
                b.set(sx, 4, z, SL);
            }
        // ---- guardian statues flanking the gate (trident-bearing drowned knights) -----------------------
        for (int sx : new int[]{-7, 7}) {
            b.fill(sx - 1, 1, -18, sx + 1, 1, -16, DP);
            b.set(sx, 2, -17, wl.apply(Blocks.PRISMARINE_WALL.getDefaultState(), 2));
            b.set(sx, 3, -17, PB); b.set(sx, 4, -17, PB);
            b.set(sx - 1, 4, -17, wl.apply(Blocks.PRISMARINE_BRICK_SLAB.getDefaultState().with(Properties.SLAB_TYPE, net.minecraft.block.enums.SlabType.TOP), 4));
            b.set(sx + 1, 4, -17, wl.apply(Blocks.PRISMARINE_BRICK_SLAB.getDefaultState().with(Properties.SLAB_TYPE, net.minecraft.block.enums.SlabType.TOP), 4));
            b.set(sx, 5, -17, DP);                                                           // helmed head
            b.set(sx, 6, -17, wl.apply(Blocks.PRISMARINE_WALL.getDefaultState(), 6));        // crest
            int tx = sx + (sx < 0 ? 2 : -2);
            for (int y = 1; y <= 7; y++) b.set(tx, y, -17, wl.apply(Blocks.IRON_BARS.getDefaultState(), y));   // trident shaft
            b.set(tx, 8, -17, GOLD);
            b.set(tx, 9, -17, wl.apply(Blocks.IRON_BARS.getDefaultState(), 9));
            b.set(tx, 8, -18, wl.apply(Blocks.IRON_BARS.getDefaultState(), 8)); b.set(tx, 8, -16, wl.apply(Blocks.IRON_BARS.getDefaultState(), 8));
            b.set(sx, 5, -18, VEIN);                                                         // glowing visor
        }
        // ---- coral gardens in the forecourt corners -----------------------------------------------------
        BlockState[] coral = {Blocks.TUBE_CORAL_BLOCK.getDefaultState(), Blocks.BRAIN_CORAL_BLOCK.getDefaultState(),
                Blocks.BUBBLE_CORAL_BLOCK.getDefaultState(), Blocks.HORN_CORAL_BLOCK.getDefaultState()};
        BlockState[] fans = {Blocks.TUBE_CORAL_FAN.getDefaultState(), Blocks.BRAIN_CORAL_FAN.getDefaultState(),
                Blocks.BUBBLE_CORAL_FAN.getDefaultState(), Blocks.HORN_CORAL_FAN.getDefaultState()};
        for (int sx : new int[]{-1, 1}) {
            for (int x = 8; x <= 16; x++) for (int z = -16; z <= -9; z++) {
                int gx = sx * x;
                if (r.nextInt(3) == 0) {
                    int h = 1 + r.nextInt(3);
                    for (int y = 1; y <= h; y++) b.set(gx, y, z, coral[r.nextInt(coral.length)]);
                    if (h + 1 <= depth) b.set(gx, h + 1, z, fans[r.nextInt(fans.length)]);
                } else if (r.nextInt(3) == 0 && 1 <= depth) {
                    b.set(gx, 1, z, Blocks.SEA_PICKLE.getDefaultState().with(Properties.PICKLES, 1 + r.nextInt(4)).with(Properties.WATERLOGGED, true));
                } else if (r.nextInt(2) == 0 && 2 <= depth) {
                    b.set(gx, 1, z, Blocks.SEAGRASS.getDefaultState());
                }
            }
        }
        // ---- the palace -----------------------------------------------------------------------------------
        // walls + sunken interior
        for (int x = -PX; x <= PX; x++) for (int z = PZ0; z <= PZ1; z++) {
            boolean edge = x == -PX || x == PX || z == PZ0 || z == PZ1;
            b.set(x, FLOOR - 1, z, SLATE);
            for (int y = FLOOR; y <= ROOF; y++) {
                if (edge) {
                    boolean band = y == 7 || y == ROOF - 1;
                    boolean pilaster = (x == -PX || x == PX || Math.floorMod(x, 4) == 1) && z == PZ0;
                    b.set(x, y, z, band ? DP : pilaster ? DP : wall.pick(r));
                } else if (y == FLOOR) {
                    b.set(x, y, z, Math.abs(x) <= 2 && z < 13 ? (Math.abs(x) == 2 ? Blocks.LIGHT_BLUE_TERRACOTTA.getDefaultState() : Blocks.CYAN_TERRACOTTA.getDefaultState())
                            : ((x + z) & 1) == 0 ? DP : PB);
                } else {
                    b.set(x, y, z, f.apply(y));
                }
            }
            b.set(x, ROOF, z, edge ? DP : wall.pick(r));
        }
        // windows along the sides
        for (int z = 0; z <= 17; z += 4) for (int sx : new int[]{-PX, PX}) for (int y = 3; y <= 5; y++)
            b.set(sx, y, z, wl.apply(Blocks.CYAN_STAINED_GLASS_PANE.getDefaultState(), y));
        // stepped roof with a central dome, broken by two collapses
        for (int step = 1; step <= 3; step++) {
            int y = ROOF + step, ix = PX - step * 3, z0 = PZ0 + step * 3, z1 = PZ1 - step * 3;
            for (int x = -ix; x <= ix; x++) for (int z = z0; z <= z1; z++) {
                boolean rim = x == -ix || x == ix || z == z0 || z == z1;
                if (rim) b.set(x, y, z, step == 3 ? PR : DP);
            }
        }
        b.cylinder(0, 8, 2.6, ROOF + 4, ROOF + 4, w(PR, PB, DP, 0.3f), f, false);
        b.set(0, ROOF + 5, 8, SL);
        b.set(0, ROOF + 6, 8, wl.apply(Blocks.PRISMARINE_WALL.getDefaultState(), ROOF + 6));
        for (int[] hole : new int[][]{{-8, 2}, {7, 12}})
            for (int x = hole[0] - 1; x <= hole[0] + 1; x++) for (int z = hole[1] - 1; z <= hole[1] + 1; z++)
                if (r.nextInt(4) != 0) { b.set(x, ROOF, z, f.apply(ROOF)); b.set(x, FLOOR + 1, z, r.nextBoolean() ? PB : DP); }   // rubble below
        // facade: grand arch, steps down into the hall, pediment with a glowing crest
        for (int x = -2; x <= 2; x++) for (int y = FLOOR + 1; y <= 5; y++) b.set(x, y, PZ0, f.apply(y));
        b.set(-2, 5, PZ0, DP); b.set(2, 5, PZ0, DP);
        for (int x = -3; x <= 3; x++) {
            b.set(x, -1, PZ0 - 1, wl.apply(Blocks.PRISMARINE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH), -1));
            b.set(x, 0, PZ0 - 2, wl.apply(Blocks.PRISMARINE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH), 0));
            b.set(x, 0, PZ0 - 1, f.apply(0));
        }
        for (int i = 0; i <= 5; i++) for (int x = -6 + i; x <= 6 - i; x++) b.set(x, ROOF + 1 + i, PZ0, i == 5 ? SL : DP);
        b.set(0, ROOF + 3, PZ0 - 1, VEIN);
        for (int x = -PX; x <= PX; x += 2) b.set(x, 8, PZ0 - 1, SL);
        // colonnades with lantern capitals, chandeliers on chains
        for (int z = -1; z <= 14; z += 5) for (int sx : new int[]{-7, 7}) {
            b.set(sx, FLOOR + 1, z, DP);
            for (int y = FLOOR + 2; y <= ROOF - 2; y++) b.set(sx, y, z, PB);
            b.set(sx, ROOF - 1, z, SL);
        }
        for (int z : new int[]{2, 9}) {
            for (int y = 8; y <= ROOF - 1; y++) b.set(0, y, z, wl.apply(Blocks.CHAIN.getDefaultState(), y));
            b.set(0, 7, z, SL);
            for (Direction d : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
                b.set(d.getOffsetX(), 7, z + d.getOffsetZ(), wl.apply(Blocks.PRISMARINE_BRICK_SLAB.getDefaultState(), 7));
        }
        // guard alcoves in the side aisles
        b.spawner(-10, FLOOR + 1, 6, net.minecraft.registry.Registries.ENTITY_TYPE.get(net.get900.pixelpirates.PixelPirates.id("corrupted_diver")));
        b.spawner(10, FLOOR + 1, 6, net.minecraft.registry.Registries.ENTITY_TYPE.get(net.get900.pixelpirates.PixelPirates.id("drowned_sailor")));
        for (int sx : new int[]{-12, 12}) for (int z = 4; z <= 8; z += 4)
            b.set(sx, FLOOR + 1, z, Blocks.SEA_PICKLE.getDefaultState().with(Properties.PICKLES, 4).with(Properties.WATERLOGGED, FLOOR + 1 <= depth));
        // ---- dais (three tiers) and throne ----------------------------------------------------------------
        int[][] tiers = {{-9, 13}, {-6, 15}, {-4, 17}};
        for (int t = 0; t < tiers.length; t++) {
            int y = FLOOR + 1 + t, hx = -tiers[t][0], z0 = tiers[t][1];
            for (int x = -hx; x <= hx; x++) for (int z = z0; z <= PZ1 - 1; z++)
                b.set(x, y, z, z == z0 ? wl.apply(Blocks.PRISMARINE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH), y)
                        : (Math.abs(x) == hx ? DP : PB));
        }
        int ty = FLOOR + 4;                                                                    // throne seat level
        b.set(0, ty, 19, wl.apply(Blocks.DARK_PRISMARINE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH), ty));
        b.set(-1, ty, 19, GOLD); b.set(1, ty, 19, GOLD);
        b.set(-1, ty + 1, 19, wl.apply(Blocks.PRISMARINE_BRICK_SLAB.getDefaultState(), ty + 1));
        b.set(1, ty + 1, 19, wl.apply(Blocks.PRISMARINE_BRICK_SLAB.getDefaultState(), ty + 1));
        for (int y = ty; y <= ty + 5; y++) { b.set(0, y, 20, y == ty + 5 ? SL : GOLD); b.set(-1, y, 20, y >= ty + 3 ? DP : GOLD); b.set(1, y, 20, y >= ty + 3 ? DP : GOLD); }
        b.set(-1, ty + 6, 20, wl.apply(Blocks.PRISMARINE_WALL.getDefaultState(), ty + 6));
        b.set(1, ty + 6, 20, wl.apply(Blocks.PRISMARINE_WALL.getDefaultState(), ty + 6));
        b.set(0, ty + 6, 20, VEIN);
        for (int sx : new int[]{-4, 4}) {                                                      // conduits on pedestals
            b.set(sx, ty - 1, 18, DP);
            b.set(sx, ty, 18, Blocks.CONDUIT.getDefaultState().with(Properties.WATERLOGGED, ty <= depth));
        }
        // rose window in the back wall
        for (int x = -4; x <= 4; x++) for (int y = 3; y <= 11; y++) {
            double d = Math.sqrt(x * x + (y - 7) * (y - 7));
            if (d <= 4.2 && Math.abs(x) >= 2 || (d <= 4.2 && y >= 11)) {
                b.set(x, y, PZ1, d > 3.4 ? DP : ((x + y) & 1) == 0 ? SL : wl.apply(Blocks.CYAN_STAINED_GLASS_PANE.getDefaultState(), y));
            }
        }
        // ---- the treasury under the dais, reached by a ladder shaft beside it -------------------------------
        for (int x = -4; x <= 4; x++) for (int z = 14; z <= 20; z++) for (int y = -7; y <= -4; y++)
            b.set(x, y, z, (x == -4 || x == 4 || z == 14 || z == 20 || y == -7) ? dark.pick(r) : f.apply(y));
        for (int y = -6; y <= FLOOR; y++) b.set(-6, y, 17, f.apply(y));
        for (int y = -6; y <= FLOOR - 1; y++) b.set(-6, y, 18, SLATE);
        for (int y = -6; y <= FLOOR; y++)
            b.set(-6, y, 17, Blocks.LADDER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH).with(Properties.WATERLOGGED, y <= depth));
        for (int y = -6; y <= -5; y++) b.set(-5, y, 17, f.apply(y));                          // doorway into the vault
        b.set(-5, -4, 17, dark.pick(r));
        b.chest(0, -6, 19, Direction.NORTH, "chests/phase5_treasure");
        b.chest(-2, -6, 19, Direction.NORTH, "chests/phase5_treasure");
        b.chest(2, -6, 19, Direction.NORTH, "chests/phase5_common");
        for (int x = -3; x <= 3; x += 6) b.set(x, -6, 16, GOLD);
        b.set(0, -6, 16, Blocks.DIAMOND_BLOCK.getDefaultState());
        b.set(0, -5, 17, SL);
        b.chest(-11, FLOOR + 1, 19, Direction.EAST, "chests/court_armory");                    // the court armory: depth charges
        // ---- the court --------------------------------------------------------------------------------------
        b.spawnMob(P + "corrupted_diver", -5, FLOOR + 1, 6);
        b.spawnMob(P + "corrupted_diver", 5, FLOOR + 1, 6);
        b.spawnMob(P + "drowned_sailor", -3, 1, -17);
        b.spawnMob(P + "drowned_sailor", 3, 1, -17);
    }

}
