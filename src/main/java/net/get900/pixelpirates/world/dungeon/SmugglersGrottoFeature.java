package net.get900.pixelpirates.world.dungeon;

import com.mojang.serialization.Codec;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.entity.ModEntities;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.DefaultFeatureConfig;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * Phase 1 dungeon: SMUGGLER'S GROTTO.
 * A boulder on the surface hides a trapdoor hatch -> a spiral stair around a log column ->
 * a timbered mine tunnel with rails -> a sealed sea cave with a smugglers' dock, a moored
 * rowboat, contraband, a Pirate Crew spawner and a vault hidden behind a barrel stack.
 * Design space: +z is "forward" from the hatch; everything stays within |x|,|z| <= 22.
 */
public final class SmugglersGrottoFeature {
    // cave ellipsoid
    private static final int CX = 0, CY = -13, CZ = 16, RX = 11, RY = 6, RZ = 6;
    private static final int FLOOR = -17;   // walkable floor level (top of floor blocks)
    private static final int POOL_TOP = -16; // water surface cells

    private SmugglersGrottoFeature() {}

    /** Dry, roughly flat land above sea level. */
    static boolean validSite(StructureWorldAccess world, BlockPos o) {
        if (o.getY() < world.getSeaLevel() + 1) return false;
        if (!world.getFluidState(o.up()).isEmpty() || !world.getBlockState(o).isSolidBlock(world, o)) return false;
        for (int[] d : new int[][]{{4, 4}, {-4, 4}, {4, -4}, {-4, -4}}) {
            int h = world.getTopY(Heightmap.Type.OCEAN_FLOOR_WG, o.getX() + d[0], o.getZ() + d[1]) - 1;
            if (Math.abs(h - o.getY()) > 3) return false;
            if (!world.getFluidState(new BlockPos(o.getX() + d[0], h + 1, o.getZ() + d[1])).isEmpty()) return false;
        }
        return true;
    }

    public static void build(DungeonBuilder b) {
        Random r = b.random;
        BlockState air = Blocks.AIR.getDefaultState();
        BlockState log = Blocks.SPRUCE_LOG.getDefaultState();
        BlockState planks = Blocks.SPRUCE_PLANKS.getDefaultState();

        // ---------------------------------------------------------------- cave (sealed shell first)
        for (int x = CX - RX - 2; x <= CX + RX + 2; x++)
            for (int y = CY - RY - 2; y <= CY + RY + 2; y++)
                for (int z = CZ - RZ - 2; z <= CZ + RZ + 2; z++) {
                    double dx = (x - CX) / (double) RX, dy = (y - CY) / (double) RY, dz = (z - CZ) / (double) RZ;
                    double d = dx * dx + dy * dy + dz * dz + (r.nextFloat() - 0.5) * 0.08;
                    if (d < 1.0) {
                        if (y < FLOOR) b.set(x, y, z, z > CZ - 1 ? (y <= POOL_TOP - 1 ? DungeonBuilder.water() : air) : DungeonBuilder.COBBLE_MIX.pick(r));
                        else if (y == FLOOR) b.set(x, y, z, z > CZ - 1 ? DungeonBuilder.water() : (r.nextInt(4) == 0 ? Blocks.GRAVEL.getDefaultState() : Blocks.STONE.getDefaultState()));
                        else if (y == POOL_TOP && z > CZ - 1) b.set(x, y, z, DungeonBuilder.water());
                        else b.set(x, y, z, air);
                    } else if (d < 1.45) {
                        b.set(x, y, z, DungeonBuilder.COBBLE_MIX.pick(r));    // seal: the cave can never breach the sea
                    }
                }
        // pool bed + glowing sea lanterns under the water
        for (int x = -8; x <= 8; x++)
            for (int z = CZ; z <= CZ + 5; z++)
                if (b.isWater(x, POOL_TOP, z)) {
                    b.set(x, FLOOR - 3, z, (x + z) % 5 == 0 ? Blocks.SEA_LANTERN.getDefaultState() : Blocks.SAND.getDefaultState());
                    for (int y = FLOOR - 2; y <= POOL_TOP; y++) b.set(x, y, z, DungeonBuilder.water());
                }

        // ---------------------------------------------------------------- dock + rowboat
        for (int x = -8; x <= 8; x++) {
            b.set(x, POOL_TOP, CZ - 1, planks);
            b.set(x, POOL_TOP, CZ, x % 2 == 0 ? planks : Blocks.SPRUCE_SLAB.getDefaultState().with(Properties.SLAB_TYPE, net.minecraft.block.enums.SlabType.TOP));
            if (x % 4 == 0) for (int y = FLOOR - 3; y < POOL_TOP; y++) b.set(x, y, CZ, log);
        }
        for (int x : new int[]{-8, -4, 4, 8}) { b.set(x, POOL_TOP + 1, CZ, Blocks.SPRUCE_FENCE.getDefaultState()); b.set(x, POOL_TOP + 2, CZ, Blocks.LANTERN.getDefaultState()); }
        int bx = 2, bz = CZ + 2;
        for (int i = 0; i < 4; i++) {
            int half = (i == 0 || i == 3) ? 0 : 1;
            for (int dx = -half; dx <= half; dx++) {
                b.set(bx + dx, POOL_TOP - 1, bz + i, Blocks.OAK_PLANKS.getDefaultState());
                boolean rim = Math.abs(dx) == half || i == 0 || i == 3;
                b.set(bx + dx, POOL_TOP, bz + i, rim ? Blocks.OAK_PLANKS.getDefaultState() : air);
            }
        }
        b.set(bx, POOL_TOP, bz + 1, Blocks.BARREL.getDefaultState());
        b.set(bx, POOL_TOP, bz + 2, Blocks.OAK_SLAB.getDefaultState());

        // ---------------------------------------------------------------- contraband on the ledge
        int ledgeY = FLOOR + 1;
        for (int[] p : new int[][]{{-9, 11}, {-8, 11}, {-9, 12}, {-7, 10}, {7, 11}, {8, 12}, {9, 11}, {6, 10}}) {
            int h = 1 + r.nextInt(3);
            for (int i = 0; i < h; i++) if (b.get(p[0], ledgeY + i, p[1]).isAir())
                b.set(p[0], ledgeY + i, p[1], r.nextInt(4) == 0 ? Blocks.HAY_BLOCK.getDefaultState() : Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.NORTH));
        }
        b.chest(-6, ledgeY, 12, Direction.EAST, "chests/phase1_smuggler");
        b.barrelLoot(5, ledgeY, 12, "chests/phase1_smuggler");
        b.set(-4, ledgeY, 11, Blocks.CAMPFIRE.getDefaultState().with(Properties.LIT, false));
        b.set(-3, ledgeY, 11, Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        b.set(-5, ledgeY, 11, Blocks.SPRUCE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        b.set(3, ledgeY, 11, ModBlocks.GROG_BARREL.getDefaultState());
        b.set(3, ledgeY + 1, 11, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 3).with(Properties.LIT, true));
        b.spawner(0, ledgeY, 13, ModEntities.PIRATE_CREW);

        // lanterns on chains from the cave roof
        for (int x : new int[]{-6, 0, 6}) {
            int top = CY + RY - 1;
            while (top > ledgeY + 3 && !b.get(x, top, 13).isAir()) top--;
            for (int y = top; y > top - 3; y--) b.set(x, y, 13, Blocks.CHAIN.getDefaultState());
            b.set(x, top - 3, 13, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        }

        // ---------------------------------------------------------------- hidden vault (behind barrels, +x wall)
        DungeonBuilder.Weathered brick = DungeonBuilder.MOSSY_BRICKS;
        b.room(12, FLOOR, 11, 18, FLOOR + 5, 17, brick, air);
        b.fill(11, ledgeY, 14, 12, ledgeY + 1, 14, air);                            // doorway through the cave wall
        b.set(10, ledgeY, 14, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.WEST));   // ...hidden behind a barrel stack
        b.set(10, ledgeY + 1, 14, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.WEST));
        b.chest(17, ledgeY, 14, Direction.WEST, "chests/phase1_smuggler_vault");
        b.set(17, ledgeY, 12, ModBlocks.TREASURE_BLOCK.getDefaultState());
        b.set(17, ledgeY, 16, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(17, ledgeY + 1, 12, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 2).with(Properties.LIT, true));
        b.set(17, ledgeY + 1, 16, Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 2).with(Properties.LIT, true));
        b.set(15, ledgeY, 12, Blocks.SKELETON_SKULL.getDefaultState());
        for (int x = 13; x <= 16; x++) b.set(x, ledgeY, 16, Blocks.COBWEB.getDefaultState());

        // ---------------------------------------------------------------- tunnel (shaft -> cave)
        for (int z = 3; z <= CZ - RZ + 1; z++) {
            for (int x = -2; x <= 2; x++)
                for (int y = FLOOR; y <= FLOOR + 5; y++) {
                    boolean wall = Math.abs(x) == 2 || y == FLOOR || y == FLOOR + 5;
                    b.set(x, y, z, wall ? DungeonBuilder.COBBLE_MIX.pick(r) : air);
                }
            b.set(0, ledgeY, z, Blocks.RAIL.getDefaultState().with(Properties.RAIL_SHAPE, net.minecraft.block.enums.RailShape.NORTH_SOUTH));
            if (z % 3 == 0) {   // timber set: posts + beam + lantern
                for (int y = ledgeY; y <= ledgeY + 2; y++) { b.set(-1, y, z, log); b.set(1, y, z, log); }
                b.set(-1, ledgeY + 3, z, Blocks.SPRUCE_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
                b.set(0, ledgeY + 3, z, Blocks.SPRUCE_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
                b.set(1, ledgeY + 3, z, Blocks.SPRUCE_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
                b.set(0, ledgeY + 2, z, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
            }
        }

        // ---------------------------------------------------------------- spiral stair shaft
        int[][] ring = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        for (int y = FLOOR; y <= 0; y++)
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 2; z++) {
                    boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                    if (x == 0 && z == 0) b.set(x, y, z, log);
                    else if (y == FLOOR) b.set(x, y, z, Blocks.COBBLESTONE.getDefaultState());
                    else b.set(x, y, z, wall ? DungeonBuilder.COBBLE_MIX.pick(r) : air);
                }
        for (int i = 0; i < 16; i++) {
            int y = -1 - i;
            if (y <= FLOOR) break;
            int[] cell = ring[i % 8], next = ring[(i + 1) % 8];
            boolean corner = cell[0] != 0 && cell[1] != 0;
            if (corner) {
                b.set(cell[0], y, cell[1], Blocks.COBBLESTONE.getDefaultState());
            } else {
                // stairs rise toward the previous step (= away from the next one)
                Direction up = Direction.fromVector(cell[0] - next[0], 0, cell[1] - next[1]);
                if (up == null) up = Direction.NORTH;
                b.set(cell[0], y, cell[1], Blocks.COBBLESTONE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, up));
            }
        }
        b.fill(-1, FLOOR + 1, 2, 1, FLOOR + 3, 2, air);                              // exit into the tunnel
        b.set(-1, -6, -2, Blocks.LANTERN.getDefaultState()); b.set(1, -12, 2, Blocks.LANTERN.getDefaultState());
        b.set(1, -6, -2, air); b.set(-1, -12, 2, air);

        // ---------------------------------------------------------------- surface: boulder + hatch
        for (int[] cell : ring) b.set(cell[0], 0, cell[1],
                Blocks.SPRUCE_TRAPDOOR.getDefaultState().with(Properties.BLOCK_HALF, BlockHalf.TOP).with(Properties.OPEN, false));
        b.set(0, 0, 0, log);
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++)
                for (int y = 1; y <= 3; y++) {
                    double d = Math.sqrt(x * x + z * z) + y * 0.9 + r.nextFloat() * 0.8;
                    boolean frame = Math.abs(x) >= 2 || Math.abs(z) >= 2;
                    if (frame && d < 4.2 && !(z <= -2 && Math.abs(x) <= 1)) b.set(x, y, z, DungeonBuilder.COBBLE_MIX.pick(r));
                    else if (!frame) b.set(x, y, z, air);
                }
        b.set(0, 1, -3, Blocks.LANTERN.getDefaultState());                          // a lantern left by the hatch
        b.set(2, 1, -3, Blocks.BARREL.getDefaultState());
    }
}
