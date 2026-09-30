package net.get900.pixelpirates.world.dungeon;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.entity.custom.CastawayEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * RACKHAM'S HOLD - Captain Rackham's fort (boss 1/10). Design space: gate faces -z, ground y = 0,
 * standing level y = 1, everything within |x|,|z| <= 22.
 * <pre>
 *            z=+22  back towers (S cannons)
 *   [tower]==========wall===========[tower]
 *     |        KEEP x-9..9 z6..15          |   keep: war room / captain's cabin / roof + Jolly Roger
 *     |   (vault under it, sealed by        |   vault: stair at x-8..-7 -> BLAST RUBBLE (dynamite only)
 *     |    blast rubble -> Rackham's hoard) |
 *   MAGAZINE     courtyard arena       BRIG   magazine x-16..-10 z-3..8 (dynamite loot, powder, TNT)
 *   x-16..-10   (Rackham at 0,1,-2)  x10..16  brig: 3 lever-opened cells (2 castaways, map merchant)
 *   BARRACKS                         GALLEY   barracks x-16..-9 z-15..-8 (bunks, crew spawner)
 *   [tower]====[GATEHOUSE x-6..6]====[tower]  galley x9..16 z-15..-8 (food, grog)
 *            z=-22  gate + approach
 * </pre>
 * Eight FORT_CANNONs (gatehouse x2, every tower x1, east/west walls x1) shoot at approaching
 * players while Rackham lives; he can also order a Broadside on the courtyard.
 */
public final class RackhamFort {
    private RackhamFort() {}

    private static final String P = "pixelpirates:";
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final DungeonBuilder.Weathered WALL = new DungeonBuilder.Weathered(Blocks.STONE_BRICKS.getDefaultState(),
            Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Blocks.CRACKED_STONE_BRICKS.getDefaultState(), 0.38f);
    private static final DungeonBuilder.Weathered BASE = new DungeonBuilder.Weathered(Blocks.COBBLESTONE.getDefaultState(),
            Blocks.MOSSY_COBBLESTONE.getDefaultState(), Blocks.ANDESITE.getDefaultState(), 0.45f);
    private static final DungeonBuilder.Weathered BUNKER = new DungeonBuilder.Weathered(Blocks.POLISHED_ANDESITE.getDefaultState(),
            Blocks.ANDESITE.getDefaultState(), Blocks.COBBLESTONE.getDefaultState(), 0.4f);
    private static final DungeonBuilder.Weathered PLANK = new DungeonBuilder.Weathered(Blocks.SPRUCE_PLANKS.getDefaultState(),
            Blocks.SPRUCE_PLANKS.getDefaultState(), Blocks.DARK_OAK_PLANKS.getDefaultState(), 0.2f);
    private static final DungeonBuilder.Weathered YARD = new DungeonBuilder.Weathered(Blocks.COARSE_DIRT.getDefaultState(),
            Blocks.GRAVEL.getDefaultState(), Blocks.PACKED_MUD.getDefaultState(), 0.5f);

    // =====================================================================================
    public static void build(DungeonBuilder b) {
        Random r = b.random;
        site(b, r);
        curtainWalls(b, r);
        gatehouse(b, r);
        for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) tower(b, r, sx, sz);
        keep(b, r);
        vault(b, r);
        magazine(b, r);
        brig(b, r);
        barracks(b, r);
        galley(b, r);
        courtyard(b, r);
        garrison(b);
    }

    // ------------------------------------------------------------------ helpers
    private static BlockState stairs(Block s, Direction up, boolean top) {
        return s.getDefaultState().with(Properties.HORIZONTAL_FACING, up).with(Properties.BLOCK_HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM);
    }

    private static BlockState slab(Block s, boolean top) {
        return s.getDefaultState().with(Properties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    private static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.getDefaultState().with(Properties.HANGING, hanging);
    }

    private static BlockState facing(Block blk, Direction d) {
        return blk.getDefaultState().with(Properties.HORIZONTAL_FACING, d);
    }

    /** Bed with both halves: foot at (x,z), head one block toward {@code toHead}. */
    private static void bed(DungeonBuilder b, int x, int y, int z, Direction toHead, Block bed) {
        BlockState s = bed.getDefaultState().with(Properties.HORIZONTAL_FACING, toHead);
        b.set(x, y, z, s.with(Properties.BED_PART, BedPart.FOOT));
        b.set(x + toHead.getOffsetX(), y, z + toHead.getOffsetZ(), s.with(Properties.BED_PART, BedPart.HEAD));
    }

    private static void door(DungeonBuilder b, int x, int y, int z, Direction facing, Block door) {
        BlockState s = door.getDefaultState().with(Properties.HORIZONTAL_FACING, facing);
        b.set(x, y, z, s.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.LOWER));
        b.set(x, y + 1, z, s.with(Properties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER));
    }

    /** Iron bars / fences / panes along a straight line with their connections set explicitly (worldgen does no shape updates). */
    private static void lineX(DungeonBuilder b, Block blk, int x1, int x2, int y, int z) {
        for (int x = x1; x <= x2; x++)
            b.set(x, y, z, blk.getDefaultState().with(Properties.EAST, true).with(Properties.WEST, true));
    }

    private static void lineZ(DungeonBuilder b, Block blk, int x, int y, int z1, int z2) {
        for (int z = z1; z <= z2; z++)
            b.set(x, y, z, blk.getDefaultState().with(Properties.NORTH, true).with(Properties.SOUTH, true));
    }

    /** Replace a column down from y=0 until it reaches solid ground (max 10): no floating foundations. */
    private static void foundation(DungeonBuilder b, int x, int z, DungeonBuilder.Weathered mat, Random r) {
        for (int y = -1; y >= -10; y--) {
            BlockState s = b.get(x, y, z);
            boolean soft = s.isAir() || s.isReplaceable() || !s.getFluidState().isEmpty() || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS);
            if (!soft) return;
            b.set(x, y, z, mat.pick(r));
        }
    }

    private static int ring(int x, int z) { return Math.max(Math.abs(x), Math.abs(z)); }

    // ------------------------------------------------------------------ site
    private static void site(DungeonBuilder b, Random r) {
        for (int x = -22; x <= 22; x++)
            for (int z = -22; z <= 22; z++) {
                int k = ring(x, z);
                boolean inside = k <= 16;
                boolean approach = Math.abs(x) <= 3 && z < -16;
                BlockState top = inside || approach ? ((Math.abs(x) <= 1 && z < 6) || approach ? Blocks.GRAVEL.getDefaultState() : YARD.pick(r))
                        : (r.nextInt(6) == 0 ? Blocks.COARSE_DIRT.getDefaultState() : Blocks.GRASS_BLOCK.getDefaultState());
                if (approach && (x + z) % 3 == 0) top = Blocks.COBBLESTONE.getDefaultState();
                b.set(x, 0, z, top);
                if (b.get(x, -1, z).isAir() || b.isWater(x, -1, z)) b.set(x, -1, z, Blocks.DIRT.getDefaultState());
                foundation(b, x, z, k <= 18 ? BASE : new DungeonBuilder.Weathered(Blocks.DIRT.getDefaultState(),
                        Blocks.DIRT.getDefaultState(), Blocks.COARSE_DIRT.getDefaultState(), 0.3f), r);
                for (int y = 1; y <= 24; y++) {
                    BlockState s = b.get(x, y, z);
                    if (!s.isAir()) b.set(x, y, z, AIR);
                }
            }
        // a couple of palms flanking the approach
        for (int sx = -1; sx <= 1; sx += 2) palm(b, sx * 10, -21, 7);
    }

    private static void palm(DungeonBuilder b, int x, int z, int h) {
        BlockState log = ModBlocks.PALM_LOG.getDefaultState();
        BlockState leaf = ModBlocks.PALM_LEAVES.getDefaultState().with(Properties.PERSISTENT, true);
        for (int y = 1; y <= h; y++) b.set(x + (y > h - 2 ? 1 : 0), y, z, log);
        int tx = x + 1, ty = h + 1;
        b.set(tx, ty, z, leaf);
        for (Direction d : Direction.Type.HORIZONTAL)
            for (int i = 1; i <= 3; i++) b.set(tx + d.getOffsetX() * i, ty - (i == 3 ? 1 : 0), z + d.getOffsetZ() * i, leaf);
    }

    // ------------------------------------------------------------------ curtain walls
    private static void curtainWalls(DungeonBuilder b, Random r) {
        for (int x = -18; x <= 18; x++)
            for (int z = -18; z <= 18; z++) {
                int k = ring(x, z);
                if (k < 17) continue;
                for (int y = 1; y <= 7; y++) b.set(x, y, z, y == 1 ? BASE.pick(r) : WALL.pick(r));
                if (k == 18) {
                    b.set(x, 8, z, WALL.pick(r));                                          // parapet
                    if (((x + z) & 1) == 0) b.set(x, 9, z, WALL.pick(r));                  // merlons
                    if (((x + z) % 8 == 0) && Math.abs(x) != Math.abs(z)) b.set(x, 10, z, lantern(false));
                }
            }
        // buttresses on the outer face
        for (int i = -12; i <= 12; i += 6)
            for (int[] p : new int[][]{{i, -19}, {i, 19}, {-19, i}, {19, i}}) {
                if (p[1] == -19 && Math.abs(i) <= 7) continue;                             // gatehouse
                for (int y = 1; y <= 4; y++) b.set(p[0], y, p[1], y == 1 ? BASE.pick(r) : WALL.pick(r));
                Direction out = p[1] == -19 ? Direction.NORTH : p[1] == 19 ? Direction.SOUTH : p[0] == -19 ? Direction.WEST : Direction.EAST;
                b.set(p[0], 5, p[1], stairs(Blocks.STONE_BRICK_STAIRS, out.getOpposite(), false));
            }
        // side-wall cannons in embrasures
        for (int sx = -1; sx <= 1; sx += 2) {
            b.set(sx * 18, 8, 0, AIR);
            b.set(sx * 18, 9, 0, AIR);
            b.set(sx * 17, 8, 0, facing(ModBlocks.FORT_CANNON, sx > 0 ? Direction.EAST : Direction.WEST));
        }
    }

    // ------------------------------------------------------------------ gatehouse
    private static void gatehouse(DungeonBuilder b, Random r) {
        b.room(-6, 0, -21, 6, 10, -15, WALL, AIR);
        for (int x = -6; x <= 6; x++) for (int z = -21; z <= -15; z++) {
            b.set(x, 0, z, Math.abs(x) <= 2 ? Blocks.GRAVEL.getDefaultState() : BASE.pick(r));
            foundation(b, x, z, BASE, r);
        }
        // crenellated roof
        for (int x = -6; x <= 6; x++) for (int z = -21; z <= -15; z++)
            if ((x == -6 || x == 6 || z == -21 || z == -15) && ((x + z) & 1) == 0) b.set(x, 11, z, WALL.pick(r));
        // gate passage through the middle, side rooms either side, upper guard room over it
        b.fill(-2, 1, -21, 2, 4, -15, AIR);
        for (int z = -20; z <= -16; z++) for (int y = 1; y <= 4; y++) { b.set(-3, y, z, WALL.pick(r)); b.set(3, y, z, WALL.pick(r)); }
        for (int x = -5; x <= 5; x++) for (int z = -20; z <= -16; z++) b.set(x, 5, z, Blocks.SPRUCE_PLANKS.getDefaultState());
        for (int x = -2; x <= 2; x++) { b.set(x, 5, -21, WALL.pick(r)); b.set(x, 5, -15, WALL.pick(r)); }
        // arch trim + a half-raised portcullis
        b.set(-2, 4, -21, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
        b.set(2, 4, -21, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
        b.set(-2, 4, -15, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
        b.set(2, 4, -15, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
        lineX(b, Blocks.IRON_BARS, -2, 2, 4, -19);
        lineX(b, Blocks.IRON_BARS, -1, 1, 3, -19);
        // side-room doors from the courtyard, ladders up to the guard room and roof
        for (int sx = -1; sx <= 1; sx += 2) {
            b.fill(sx * 4, 1, -15, sx * 4, 2, -15, AIR);
            for (int y = 1; y <= 10; y++) b.set(sx * 5, y, -20, facing(Blocks.LADDER, Direction.SOUTH));
            b.set(sx * 5, 5, -20, facing(Blocks.LADDER, Direction.SOUTH));
            b.set(sx * 5, 10, -20, facing(Blocks.LADDER, Direction.SOUTH));
            b.set(sx * 4, 4, -18, lantern(true));
            b.set(sx * 6, 3, -18, AIR);                                                   // arrow slits
            b.set(sx * 4, 9, -18, lantern(true));
            // wall-walk doors (walk is ring 17, standing y8)
            b.fill(sx * 6, 8, -17, sx * 6, 9, -17, AIR);
            // gate cannons, embrasures cut in the front crenel line
            b.set(sx * 4, 11, -21, AIR);
            b.set(sx * 4, 11, -20, facing(ModBlocks.FORT_CANNON, Direction.NORTH));
            // outside: lanterns and banners by the gate
            b.set(sx * 3, 3, -22, facing(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
            b.set(sx * 3, 5, -22, facing(Blocks.WALL_TORCH, Direction.NORTH));
        }
        for (int x = -3; x <= 3; x += 3) for (int z : new int[]{-21, -15}) { b.set(x, 7, z, AIR); b.set(x, 8, z, AIR); }   // guard room windows
        b.set(0, 6, -18, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.set(0, 9, -18, lantern(true));
    }

    // ------------------------------------------------------------------ corner towers
    private static void tower(DungeonBuilder b, Random r, int sx, int sz) {
        int cx = sx * 18, cz = sz * 18;
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            double d = Math.sqrt(x * x + z * z);
            if (d > 4.2) continue;
            foundation(b, cx + x, cz + z, BASE, r);
            b.set(cx + x, 0, cz + z, BASE.pick(r));
            for (int y = 1; y <= 11; y++) {
                boolean rim = d > 3.2;
                if (rim) b.set(cx + x, y, cz + z, y <= 2 ? BASE.pick(r) : WALL.pick(r));
                else b.set(cx + x, y, cz + z, (y == 7 || y == 11) ? Blocks.SPRUCE_PLANKS.getDefaultState() : AIR);
            }
            if (d > 3.2 && ((x + z) & 1) == 0) b.set(cx + x, 12, cz + z, WALL.pick(r));
        }
        // central post + ladder, with holes through the two floors
        for (int y = 1; y <= 11; y++) b.set(cx, y, cz, Blocks.SPRUCE_LOG.getDefaultState());
        for (int y = 1; y <= 11; y++) b.set(cx - sx, y, cz, facing(Blocks.LADDER, sx > 0 ? Direction.WEST : Direction.EAST));
        b.set(cx, 12, cz, lantern(false));
        // ground door toward the courtyard, wall-walk doors at y8
        b.fill(sx * 15, 1, sz * 16, sx * 15, 2, sz * 16, AIR);
        b.fill(sx * 16, 1, sz * 15, sx * 16, 2, sz * 15, AIR);
        b.fill(sx * 17, 8, sz * 14, sx * 17, 9, sz * 14, AIR);
        b.fill(sx * 14, 8, sz * 17, sx * 14, 9, sz * 17, AIR);
        b.set(cx - sx * 2, 1, cz - sz * 2, lantern(false));
        b.set(cx - sx * 2, 8, cz - sz * 2, lantern(false));
        // roof cannon facing away from the fort (front towers -> north, back towers -> south)
        Direction out = sz < 0 ? Direction.NORTH : Direction.SOUTH;
        b.set(cx, 12, cz + sz * 3, facing(ModBlocks.FORT_CANNON, out));
        b.set(cx, 12, cz + sz * 4, AIR);
        b.set(cx + sx * 2, 12, cz - sz * 2, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.set(cx + sx * 2, 13, cz - sz * 2, Blocks.COAL_BLOCK.getDefaultState());
    }

    // ------------------------------------------------------------------ keep
    private static void keep(DungeonBuilder b, Random r) {
        b.room(-9, 0, 6, 9, 12, 15, WALL, AIR);
        for (int x = -9; x <= 9; x++) for (int z = 6; z <= 15; z++) foundation(b, x, z, BASE, r);
        // base course, corner quoins, floors
        for (int x = -9; x <= 9; x++) for (int z = 6; z <= 15; z++) {
            boolean edge = x == -9 || x == 9 || z == 6 || z == 15;
            if (edge) b.set(x, 1, z, BASE.pick(r));
            else { b.set(x, 0, z, Blocks.SPRUCE_PLANKS.getDefaultState()); b.set(x, 6, z, Blocks.DARK_OAK_PLANKS.getDefaultState()); }
        }
        for (int[] c : new int[][]{{-9, 6}, {9, 6}, {-9, 15}, {9, 15}})
            for (int y = 1; y <= 12; y++) b.set(c[0], y, c[1], Blocks.CHISELED_STONE_BRICKS.getDefaultState());
        // timber belt between the floors
        for (int x = -8; x <= 8; x++) { b.set(x, 6, 6, Blocks.DARK_OAK_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X)); }
        // roof: crenels, lookout lantern, ladder hatch, flag
        for (int x = -9; x <= 9; x++) for (int z = 6; z <= 15; z++)
            if ((x == -9 || x == 9 || z == 6 || z == 15) && ((x + z) & 1) == 0) b.set(x, 13, z, WALL.pick(r));
        for (int y = 7; y <= 11; y++) b.set(-8, y, 14, facing(Blocks.LADDER, Direction.NORTH));
        b.set(-8, 12, 14, AIR);
        for (int y = 13; y <= 21; y++) b.set(0, y, 13, Blocks.DARK_OAK_FENCE.getDefaultState());
        jollyRoger(b, 1, 17, 13);
        b.set(5, 13, 9, lantern(false));
        b.set(-5, 13, 9, lantern(false));

        // entrance: arched 3-wide doorway, banners, lanterns
        b.fill(-1, 1, 6, 1, 3, 6, AIR);
        b.set(-1, 3, 6, stairs(Blocks.STONE_BRICK_STAIRS, Direction.EAST, true));
        b.set(1, 3, 6, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
        for (int sx = -1; sx <= 1; sx += 2) {
            b.set(sx * 3, 4, 5, facing(Blocks.BLACK_WALL_BANNER, Direction.NORTH));
            b.set(sx * 2, 2, 5, facing(Blocks.WALL_TORCH, Direction.NORTH));
        }
        // windows: open shutters on the upper floor, slits below
        for (int x = -6; x <= 6; x += 3) {
            if (x == 0) continue;
            for (int z : new int[]{6, 15}) {
                b.set(x, 8, z, AIR); b.set(x, 9, z, AIR);
                b.set(x, 3, z, AIR);
            }
        }
        for (int z = 9; z <= 12; z += 3) for (int x : new int[]{-9, 9}) { b.set(x, 8, z, AIR); b.set(x, 9, z, AIR); }

        // ---- ground floor: the war room
        for (int x = -2; x <= 2; x++) for (int z = 9; z <= 11; z++) {
            b.set(x, 1, z, Blocks.DARK_OAK_FENCE.getDefaultState());
            b.set(x, 2, z, slab(Blocks.DARK_OAK_SLAB, false));
        }
        b.set(0, 2, 10, ModBlocks.MAP_BLOCK.getDefaultState());
        b.set(-2, 3, 11, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 2).with(Properties.LIT, true));
        b.set(2, 3, 9, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 3).with(Properties.LIT, true));
        for (int x = -2; x <= 2; x += 2) {
            b.set(x, 1, 8, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
            b.set(x, 1, 12, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        }
        b.set(-3, 1, 10, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        b.set(3, 1, 10, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
        for (int z = 11; z <= 14; z++) for (int y = 1; y <= 3; y++) b.set(-5, y, z, y == 3 ? Blocks.CHISELED_BOOKSHELF.getDefaultState() : Blocks.BOOKSHELF.getDefaultState());
        b.set(-4, 1, 14, Blocks.CARTOGRAPHY_TABLE.getDefaultState());
        b.chest(4, 1, 7, Direction.SOUTH, "chests/rackham_barracks");
        b.set(5, 1, 7, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.set(-7, 1, 7, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        for (int x = -4; x <= 4; x += 4) b.set(x, 5, 10, lantern(true));
        for (int x = -8; x <= 8; x++) for (int z = 7; z <= 14; z++)
            if (x > -6 && x < 6 && z > 7 && z < 14 && (x == -5 || x == 5 || z == 8 || z == 13)) b.set(x, 0, z, Blocks.DARK_OAK_PLANKS.getDefaultState());
        // stairs up (x7..8, rising toward -z), hole + railing in the upper floor
        for (int k = 0; k <= 5; k++) {
            int z = 14 - k, y = 1 + k;
            for (int x = 7; x <= 8; x++) {
                b.set(x, y, z, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
                for (int yy = 1; yy < y; yy++) b.set(x, yy, z, Blocks.SPRUCE_PLANKS.getDefaultState());
            }
        }
        for (int x = 7; x <= 8; x++) { b.set(x, 6, 10, AIR); b.set(x, 6, 11, AIR); }
        lineZ(b, Blocks.SPRUCE_FENCE, 6, 7, 9, 12);
        lineX(b, Blocks.SPRUCE_FENCE, 7, 8, 7, 12);

        // ---- upper floor: the captain's cabin
        for (int x = -6; x <= 4; x++) for (int z = 8; z <= 13; z++) b.set(x, 7, z, Blocks.RED_CARPET.getDefaultState());
        for (int x = -5; x <= 3; x++) for (int z = 9; z <= 12; z++)
            if (x == -5 || x == 3 || z == 9 || z == 12) b.set(x, 7, z, Blocks.YELLOW_CARPET.getDefaultState());
        bed(b, -6, 7, 13, Direction.SOUTH, Blocks.RED_BED);
        bed(b, -5, 7, 13, Direction.SOUTH, Blocks.RED_BED);
        b.set(-7, 7, 14, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.chest(-4, 7, 14, Direction.NORTH, "chests/rackham_captain");
        // desk + chair by the front windows
        for (int x = 1; x <= 3; x++) b.set(x, 7, 8, stairs(Blocks.DARK_OAK_STAIRS, Direction.NORTH, true));
        b.set(2, 8, 8, Blocks.LECTERN.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH));
        b.set(1, 8, 8, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 1).with(Properties.LIT, true));
        b.set(2, 7, 9, stairs(Blocks.DARK_OAK_STAIRS, Direction.SOUTH, false));
        for (int z = 9; z <= 13; z++) for (int y = 7; y <= 8; y++) b.set(8, y, z, z > 11 ? Blocks.BOOKSHELF.getDefaultState() : AIR);
        b.set(5, 7, 14, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.set(5, 8, 14, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.set(-8, 7, 8, Blocks.BREWING_STAND.getDefaultState());
        b.set(-8, 7, 9, Blocks.CAULDRON.getDefaultState());
        b.set(-1, 11, 11, Blocks.CHAIN.getDefaultState());
        b.set(-1, 10, 11, lantern(true));
        b.set(-5, 11, 9, lantern(true));
        b.set(4, 11, 12, lantern(true));
    }

    /** A black 4x3 flag with a white skull, hanging off the pole at (x-1, y, z). */
    private static void jollyRoger(DungeonBuilder b, int x, int y, int z) {
        String[] rows = {"KWWK", "KWKW", "KKWK"};
        for (int i = 0; i < 3; i++)
            for (int j = 0; j < 4; j++)
                b.set(x + j, y + 2 - i, z, rows[i].charAt(j) == 'W' ? Blocks.WHITE_WOOL.getDefaultState() : Blocks.BLACK_WOOL.getDefaultState());
    }

    // ------------------------------------------------------------------ vault (under the keep)
    /** Stairwell x-8..-7 from the war room floor (z8, y0) down to z14; hoard room x-5..7 z8..14, standing y-6. */
    private static void vault(DungeonBuilder b, Random r) {
        final int SY = -6;
        DungeonBuilder.Weathered cellar = new DungeonBuilder.Weathered(Blocks.DEEPSLATE_BRICKS.getDefaultState(),
                Blocks.CRACKED_DEEPSLATE_BRICKS.getDefaultState(), Blocks.COBBLED_DEEPSLATE.getDefaultState(), 0.35f);
        b.room(-9, -7, 7, -6, -1, 15, cellar, AIR);          // stairwell
        b.room(-6, -7, 7, 8, -1, 15, cellar, AIR);           // hoard room (shares the x=-6 wall)
        // stair: y0 at z8 down to y-6 at z14, solid underneath, headroom cut through floor/ceiling
        for (int k = 0; k <= 6; k++)
            for (int x = -8; x <= -7; x++) {
                b.set(x, -k, 8 + k, stairs(Blocks.DEEPSLATE_BRICK_STAIRS, Direction.NORTH, false));
                for (int y = -k - 1; y >= -6; y--) b.set(x, y, 8 + k, cellar.pick(r));
            }
        for (int x = -8; x <= -7; x++) {
            b.set(x, 0, 9, AIR); b.set(x, 0, 10, AIR);
            b.set(x, -1, 10, AIR); b.set(x, -1, 11, AIR);
        }
        lineZ(b, Blocks.SPRUCE_FENCE, -6, 1, 8, 10);
        b.set(-8, -2, 12, lantern(true));
        b.set(-7, -2, 8, Blocks.COBWEB.getDefaultState());
        // THE SEAL: collapsed rubble in the vault doorway - dynamite only
        for (int z = 13; z <= 14; z++) for (int y = SY; y <= SY + 2; y++) b.set(-6, y, z, ModBlocks.BLAST_RUBBLE.getDefaultState());

        // hoard: pillars, gold, treasure pedestal, two chests
        for (int x = -5; x <= 7; x++) for (int z = 8; z <= 14; z++) b.set(x, SY - 1, z, (x + z) % 4 == 0 ? Blocks.POLISHED_DEEPSLATE.getDefaultState() : cellar.pick(r));
        for (int[] c : new int[][]{{-3, 9}, {5, 9}, {-3, 13}, {5, 13}})
            for (int y = SY; y <= -2; y++) b.set(c[0], y, c[1], Blocks.DARK_OAK_LOG.getDefaultState());
        b.set(1, SY, 11, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(1, SY + 1, 11, ModBlocks.TREASURE_BLOCK.getDefaultState());
        b.set(1, SY + 2, 11, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 4).with(Properties.LIT, true));
        b.chest(0, SY, 14, Direction.NORTH, "chests/rackham_hoard");
        b.chest(2, SY, 14, Direction.NORTH, "chests/rackham_hoard");
        for (int[] g : new int[][]{{6, 8}, {7, 8}, {7, 9}, {6, 14}, {7, 14}, {-5, 8}})
            b.set(g[0], SY, g[1], r.nextInt(3) == 0 ? Blocks.RAW_GOLD_BLOCK.getDefaultState() : Blocks.GOLD_BLOCK.getDefaultState());
        b.set(7, SY + 1, 8, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(-5, SY, 14, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.set(-4, SY, 14, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.set(-5, SY + 1, 14, Blocks.EMERALD_BLOCK.getDefaultState());
        b.set(7, SY, 11, Blocks.SKELETON_SKULL.getDefaultState().with(Properties.ROTATION, 12));
        b.set(7, SY + 2, 11, Blocks.CHAIN.getDefaultState());
        b.set(7, SY + 3, 11, Blocks.CHAIN.getDefaultState());
        for (int[] l : new int[][]{{-1, 9}, {3, 13}, {1, 8}, {-4, 11}, {6, 11}, {-1, 13}}) b.set(l[0], -2, l[1], lantern(true));
        b.set(-5, SY, 12, Blocks.SOUL_LANTERN.getDefaultState());   // a cold light by the seal
    }

    // ------------------------------------------------------------------ powder magazine
    private static void magazine(DungeonBuilder b, Random r) {
        b.room(-16, 0, -3, -10, 6, 8, BUNKER, AIR);
        for (int x = -16; x <= -10; x++) for (int z = -3; z <= 8; z++) { foundation(b, x, z, BASE, r); b.set(x, 7, z, slab(Blocks.STONE_BRICK_SLAB, false)); }
        for (int x = -15; x <= -11; x++) for (int z = -2; z <= 7; z++) b.set(x, 0, z, Blocks.SPRUCE_PLANKS.getDefaultState());
        b.fill(-10, 1, 2, -10, 2, 3, AIR);                                                      // double doorway
        b.set(-9, 3, 1, facing(Blocks.BLACK_WALL_BANNER, Direction.EAST));
        // powder kegs (plain barrels), loot barrels, a TNT rack and cannonball stacks
        for (int z = -2; z <= 7; z++) {
            if (z == 2 || z == 3) continue;
            b.set(-15, 1, z, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.EAST));
            if ((z & 1) == 0) b.set(-15, 2, z, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.EAST));
        }
        b.barrelLoot(-15, 1, 2, "chests/rackham_armory");
        b.barrelLoot(-15, 1, 3, "chests/rackham_armory");
        b.chest(-12, 1, 7, Direction.NORTH, "chests/rackham_armory");
        for (int x = -14; x <= -13; x++) b.set(x, 1, 7, Blocks.TNT.getDefaultState());
        b.set(-13, 2, 7, Blocks.TNT.getDefaultState());
        for (int[] c : new int[][]{{-12, -2}, {-11, -2}, {-12, -1}}) b.set(c[0], 1, c[1], Blocks.COAL_BLOCK.getDefaultState());
        b.set(-12, 2, -2, Blocks.COAL_BLOCK.getDefaultState());
        b.set(-14, 1, -2, Blocks.ANVIL.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        b.set(-13, 1, -2, Blocks.GRINDSTONE.getDefaultState().with(Properties.WALL_MOUNT_LOCATION, WallMountLocation.FLOOR).with(Properties.HORIZONTAL_FACING, Direction.EAST));
        b.set(-11, 1, 7, Blocks.SMITHING_TABLE.getDefaultState());
        b.set(-13, 5, 1, lantern(true));
        b.set(-13, 5, 5, lantern(true));
    }

    // ------------------------------------------------------------------ brig
    private static void brig(DungeonBuilder b, Random r) {
        b.room(10, 0, -3, 16, 6, 8, WALL, AIR);
        for (int x = 10; x <= 16; x++) for (int z = -3; z <= 8; z++) {
            foundation(b, x, z, BASE, r);
            if (x > 10 && x < 16 && z > -3 && z < 8) b.set(x, 0, z, BASE.pick(r));
            if ((x == 10 || x == 16 || z == -3 || z == 8) && ((x + z) & 1) == 0) b.set(x, 7, z, WALL.pick(r));
        }
        b.fill(10, 1, 2, 10, 2, 2, AIR);                                                      // doorway from the courtyard
        b.set(10, 3, 2, stairs(Blocks.STONE_BRICK_STAIRS, Direction.WEST, true));
        // three cells behind a barred front at x = 13: [pillar, door, bars...] per cell
        int[][] cells = {{-2, 0}, {2, 4}, {6, 7}};                                           // z ranges
        for (int[] c : cells) {
            int z0 = c[0], z1 = c[1];
            if (z1 + 1 <= 7) for (int x = 13; x <= 15; x++) for (int y = 1; y <= 5; y++) b.set(x, y, z1 + 1, WALL.pick(r));   // dividing wall
            for (int y = 1; y <= 3; y++) b.set(13, y, z0, Blocks.STONE_BRICKS.getDefaultState());                          // pillar (lever block)
            door(b, 13, 1, z0 + 1, Direction.WEST, Blocks.IRON_DOOR);
            for (int y = 1; y <= 3; y++) if (z1 >= z0 + 2) lineZ(b, Blocks.IRON_BARS, 13, y, z0 + 2, z1);
            b.set(13, 3, z0 + 1, Blocks.IRON_BARS.getDefaultState().with(Properties.NORTH, true).with(Properties.SOUTH, true));
            for (int z = z0; z <= z1; z++) for (int y = 4; y <= 5; y++) b.set(13, y, z, WALL.pick(r));
            b.set(12, 2, z0, Blocks.LEVER.getDefaultState().with(Properties.WALL_MOUNT_LOCATION, WallMountLocation.WALL)
                    .with(Properties.HORIZONTAL_FACING, Direction.WEST).with(Properties.POWERED, false));
            // squalor
            b.set(15, 1, z1, Blocks.HAY_BLOCK.getDefaultState());
            b.set(15, 3, z0, Blocks.CHAIN.getDefaultState());
            b.set(15, 4, z0, Blocks.CHAIN.getDefaultState());
            b.set(14, 4, z1, Blocks.COBWEB.getDefaultState());
        }
        b.set(15, 1, -2, Blocks.CAULDRON.getDefaultState());
        // guard corridor
        b.set(11, 1, -2, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(11, 2, -2, slab(Blocks.SPRUCE_SLAB, false));
        b.set(12, 1, -2, stairs(Blocks.SPRUCE_STAIRS, Direction.EAST, false));
        b.set(11, 3, -2, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 1).with(Properties.LIT, true));
        b.barrelLoot(11, 1, 7, "chests/rackham_barracks");
        b.set(11, 5, 0, lantern(true));
        b.set(11, 5, 5, lantern(true));

        // prisoners: two castaways and a map merchant
        b.spawnMob(P + "castaway", 14, 1, -1, e -> { if (e instanceof CastawayEntity c) c.setCaptive(true); });
        b.spawnMob(P + "castaway", 14, 1, 3, e -> { if (e instanceof CastawayEntity c) c.setCaptive(true); });
        b.spawnMob(P + "map_merchant", 14, 1, 7);
    }

    // ------------------------------------------------------------------ barracks
    private static void timberHall(DungeonBuilder b, Random r, int x1, int z1, int x2, int z2) {
        b.room(x1, 0, z1, x2, 5, z2, PLANK, AIR);
        for (int x = x1; x <= x2; x++) for (int z = z1; z <= z2; z++) {
            foundation(b, x, z, BASE, r);
            boolean edge = x == x1 || x == x2 || z == z1 || z == z2;
            if (edge) b.set(x, 1, z, BASE.pick(r));
            b.set(x, 5, z, Blocks.DARK_OAK_PLANKS.getDefaultState());
            if (edge) b.set(x, 6, z, slab(Blocks.DARK_OAK_SLAB, false));
        }
        for (int[] c : new int[][]{{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}})
            for (int y = 1; y <= 5; y++) b.set(c[0], y, c[1], Blocks.DARK_OAK_LOG.getDefaultState());
        for (int x = x1; x <= x2; x++) { b.set(x, 4, z1, Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
            b.set(x, 4, z2, Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X)); }
    }

    private static void barracks(DungeonBuilder b, Random r) {
        timberHall(b, r, -16, -15, -9, -8);
        b.fill(-9, 1, -12, -9, 2, -11, AIR);
        for (int z = -14; z <= -10; z += 2) {
            bed(b, -14, 1, z, Direction.WEST, Blocks.BROWN_BED);
            b.set(-15, 2, z, slab(Blocks.SPRUCE_SLAB, true));
            b.set(-14, 2, z, slab(Blocks.SPRUCE_SLAB, true));
            bed(b, -14, 3, z, Direction.WEST, Blocks.BROWN_BED);
            b.set(-13, 1, z, Blocks.SPRUCE_TRAPDOOR.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST).with(Properties.OPEN, true));
        }
        b.chest(-10, 1, -14, Direction.WEST, "chests/rackham_barracks");
        b.chest(-15, 1, -9, Direction.EAST, "chests/rackham_barracks");
        b.set(-12, 1, -9, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(-12, 2, -9, slab(Blocks.SPRUCE_SLAB, false));
        b.set(-11, 1, -9, stairs(Blocks.SPRUCE_STAIRS, Direction.WEST, false));
        b.spawner(-11, 1, -13, net.minecraft.registry.Registries.ENTITY_TYPE.get(net.get900.pixelpirates.PixelPirates.id("pirate_crew")));
        b.set(-12, 4, -11, lantern(true));
    }

    private static void galley(DungeonBuilder b, Random r) {
        timberHall(b, r, 9, -15, 16, -8);
        b.fill(9, 1, -12, 9, 2, -11, AIR);
        b.set(15, 1, -14, Blocks.SMOKER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        b.set(15, 1, -13, Blocks.FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        b.set(15, 1, -12, Blocks.CAMPFIRE.getDefaultState());
        b.set(15, 1, -11, Blocks.WATER_CAULDRON.getDefaultState().with(Properties.LEVEL_3, 3));
        b.barrelLoot(15, 1, -10, "chests/rackham_galley");
        b.barrelLoot(15, 1, -9, "chests/rackham_galley");
        b.set(10, 1, -14, ModBlocks.GROG_BARREL.getDefaultState());
        b.set(11, 1, -14, ModBlocks.GROG_BARREL.getDefaultState());
        b.set(10, 2, -14, ModBlocks.GROG_BARREL.getDefaultState());
        // long mess table with benches
        for (int x = 11; x <= 13; x++) {
            b.set(x, 1, -11, Blocks.SPRUCE_FENCE.getDefaultState());
            b.set(x, 2, -11, slab(Blocks.SPRUCE_SLAB, false));
            b.set(x, 1, -12, stairs(Blocks.SPRUCE_STAIRS, Direction.NORTH, false));
            b.set(x, 1, -10, stairs(Blocks.SPRUCE_STAIRS, Direction.SOUTH, false));
        }
        b.set(12, 3, -11, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 3).with(Properties.LIT, true));
        b.set(12, 4, -12, lantern(true));
    }

    // ------------------------------------------------------------------ courtyard
    private static void courtyard(DungeonBuilder b, Random r) {
        // parked field cannons guarding the gate, cannonball stacks, training dummies
        for (int sx = -1; sx <= 1; sx += 2) {
            b.set(sx * 4, 1, -12, facing(ModBlocks.SHIP_CANNON, Direction.NORTH));
            b.set(sx * 5, 1, -14, Blocks.COAL_BLOCK.getDefaultState());
            b.set(sx * 6, 1, -14, Blocks.COAL_BLOCK.getDefaultState());
            b.set(sx * 5, 2, -14, Blocks.COAL_BLOCK.getDefaultState());
            // lamp posts round the arena
            for (int z : new int[]{-10, 0}) {
                for (int y = 1; y <= 3; y++) b.set(sx * 8, y, z, Blocks.DARK_OAK_FENCE.getDefaultState());
                b.set(sx * 8, 4, z, lantern(false));
            }
        }
        for (int x : new int[]{6, 7}) {
            b.set(x, 1, -8, Blocks.SPRUCE_FENCE.getDefaultState());
            b.set(x, 2, -8, Blocks.HAY_BLOCK.getDefaultState());
            b.set(x, 3, -8, Blocks.CARVED_PUMPKIN.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        }
        // the well
        for (int x = -7; x <= -5; x++) for (int z = -9; z <= -7; z++) {
            boolean mid = x == -6 && z == -8;
            b.set(x, 0, z, mid ? DungeonBuilder.water() : BASE.pick(r));
            b.set(x, 1, z, mid ? AIR : Blocks.COBBLESTONE_WALL.getDefaultState());
            if (mid) for (int y = -1; y >= -3; y--) b.set(x, y, z, DungeonBuilder.water());
        }
        for (int[] c : new int[][]{{-7, -9}, {-5, -7}}) { b.set(c[0], 2, c[1], Blocks.SPRUCE_FENCE.getDefaultState()); b.set(c[0], 3, c[1], Blocks.SPRUCE_FENCE.getDefaultState()); }
        for (int x = -7; x <= -5; x++) b.set(x, 4, -8, slab(Blocks.SPRUCE_SLAB, false));
        b.set(-6, 3, -8, Blocks.CHAIN.getDefaultState());
        // the gallows by the keep
        for (int y = 1; y <= 4; y++) { b.set(-6, y, 3, Blocks.DARK_OAK_LOG.getDefaultState()); b.set(-4, y, 3, Blocks.DARK_OAK_LOG.getDefaultState()); }
        for (int x = -6; x <= -4; x++) b.set(x, 5, 3, Blocks.DARK_OAK_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
        b.set(-5, 4, 3, Blocks.CHAIN.getDefaultState());
        b.set(-5, 1, 3, slab(Blocks.SPRUCE_SLAB, false));
        // supply crates
        for (int[] c : new int[][]{{7, 3}, {7, 4}, {6, 4}}) b.set(c[0], 1, c[1], Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.set(7, 2, 4, Blocks.SPRUCE_TRAPDOOR.getDefaultState().with(Properties.BLOCK_HALF, BlockHalf.BOTTOM));
        b.set(-8, 1, -14, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.set(-8, 2, -14, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
    }

    // ------------------------------------------------------------------ the garrison
    private static void garrison(DungeonBuilder b) {
        for (int[] g : new int[][]{{-4, -13}, {4, -6}, {-12, 4}, {0, -19}}) b.spawnMob(P + "pirate_crew", g[0], 1, g[1]);
        b.spawnMob(P + "pirate_crew", 1, 6, -18);        // in the gate guard room
        b.spawnMob(P + "captain_rackham", 0, 1, -2);
    }
}
