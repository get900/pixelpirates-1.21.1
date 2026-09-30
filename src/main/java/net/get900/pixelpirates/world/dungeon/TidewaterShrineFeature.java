package net.get900.pixelpirates.world.dungeon;

import com.mojang.serialization.Codec;
import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.EntityType;
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
 * Phase 1 dungeon: TIDEWATER SHRINE.
 * Surface: mossy ruined platform, broken pillars, an arched stair down.
 * Crypt layout (design space, floor y = -10). The treasure chamber is sealed with BLAST RUBBLE (dynamite
 * only, 2026-09-29) and holds the Tidebreaker; the lever room is now just a hidden loot room:
 * <pre>
 *   hall        x -5..5,   z 6..14   (skeleton spawner, urns)       stair enters at z 6
 *   corridor    x -12..-5, z 8..12   narrows to z10 at x -11..-10 -> iron door at (-13, z10)
 *   chamber     x -21..-13, z 3..11  (tidal channel, gold pedestal, treasure)
 *   passage     x -11..-5, z 12..16  reached by breaking the cracked panel at (-5, z13)
 *   lever room  x -16..-11, z 11..17 lever at (-13, z12) on block W (-13, z11), which is
 *               adjacent to the door, so pulling it powers W and opens the door.
 * </pre>
 * Boxes only share wall planes, never interiors (checked when laying it out).
 */
public final class TidewaterShrineFeature {
    private static final int HF = -10;          // crypt floor blocks
    private static final int HY = HF + 1;       // standing level

    private TidewaterShrineFeature() {}

    private static BlockState stairs(Direction up) {
        return Blocks.STONE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, up);
    }

    private static BlockState candle(int n) {
        return Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, n).with(Properties.LIT, true);
    }

    public static void build(DungeonBuilder b) {
        Random r = b.random;
        BlockState air = Blocks.AIR.getDefaultState();
        DungeonBuilder.Weathered brick = DungeonBuilder.MOSSY_BRICKS;
        DungeonBuilder.Weathered sea = new DungeonBuilder.Weathered(Blocks.PRISMARINE_BRICKS.getDefaultState(),
                Blocks.DARK_PRISMARINE.getDefaultState(), Blocks.PRISMARINE.getDefaultState(), 0.4f);

        // ---------------------------------------------------------------- rooms
        b.room(-5, HF, 6, 5, HF + 6, 14, brick, air);                 // hall
        b.room(-12, HF, 8, -5, HF + 4, 12, brick, air);               // corridor
        for (int x = -11; x <= -10; x++)                               // narrow to one block wide
            for (int y = HY; y <= HY + 2; y++) { b.set(x, y, 9, brick.pick(r)); b.set(x, y, 11, brick.pick(r)); }
        b.room(-21, HF, 3, -13, HF + 8, 11, sea, air);                // treasure chamber
        b.room(-16, HF, 11, -11, HF + 4, 17, brick, air);             // secret lever room
        b.room(-11, HF, 12, -5, HF + 4, 16, brick, air);              // hidden passage

        // ---------------------------------------------------------------- openings
        b.fill(-5, HY, 9, -5, HY + 2, 11, air);                        // hall -> corridor
        b.fill(-12, HY, 10, -12, HY + 1, 10, air);                     // corridor -> door
        b.fill(-11, HY, 14, -11, HY + 1, 15, air);                     // passage -> lever room
        b.set(-5, HY, 13, Blocks.CRACKED_STONE_BRICKS.getDefaultState());       // THE SECRET: break this panel
        b.set(-5, HY + 1, 13, Blocks.CRACKED_STONE_BRICKS.getDefaultState());

        // ---------------------------------------------------------------- the vault seal (2026-09-29: was an iron door + lever)
        // collapsed rubble - only thrown dynamite clears it. Behind it: the Tidebreaker (Sea Serpent objective).
        b.set(-13, HY, 10, ModBlocks.BLAST_RUBBLE.getDefaultState());
        b.set(-13, HY + 1, 10, ModBlocks.BLAST_RUBBLE.getDefaultState());
        b.set(-12, HY, 10, ModBlocks.BLAST_RUBBLE.getDefaultState());
        b.set(-13, HY, 11, Blocks.CHISELED_STONE_BRICKS.getDefaultState());

        // ---------------------------------------------------------------- hall
        b.spawner(3, HY, 12, EntityType.SKELETON);
        b.chest(-3, HY, 8, Direction.EAST, "chests/phase1_shrine");
        b.set(3, HY, 8, Blocks.DECORATED_POT.getDefaultState());
        b.set(-3, HY, 11, Blocks.DECORATED_POT.getDefaultState());
        b.set(-3, HY + 1, 11, candle(2));
        for (int[] p : new int[][]{{-4, 7}, {4, 7}, {4, 13}, {-4, 13}}) b.set(p[0], HY + 4, p[1], Blocks.COBWEB.getDefaultState());
        b.set(0, HY + 4, 10, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        b.set(-4, HY + 2, 10, Blocks.SKELETON_WALL_SKULL.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));

        // ---------------------------------------------------------------- corridor niches: urns, skulls, candles
        for (int x : new int[]{-6, -8}) {
            for (int z : new int[]{9, 11}) {
                b.set(x, HY, z, Blocks.DECORATED_POT.getDefaultState());
                b.set(x, HY + 1, z, r.nextBoolean() ? Blocks.SKELETON_SKULL.getDefaultState() : candle(1 + r.nextInt(3)));
            }
        }
        b.set(-9, HY + 2, 10, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));

        // ---------------------------------------------------------------- lever room + passage
        b.spawner(-14, HY, 15, EntityType.SKELETON);
        b.set(-12, HY, 16, Blocks.COBWEB.getDefaultState());
        b.set(-15, HY, 16, Blocks.COBWEB.getDefaultState());
        b.set(-8, HY + 2, 14, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true));
        b.set(-6, HY, 15, Blocks.SKELETON_SKULL.getDefaultState());

        // ---------------------------------------------------------------- treasure chamber (centre -17, z7)
        for (int x = -19; x <= -15; x++)
            for (int z = 5; z <= 9; z++) {
                boolean ring = x == -19 || x == -15 || z == 5 || z == 9;
                if (ring) { b.set(x, HF, z, DungeonBuilder.water()); b.set(x, HF - 1, z, Blocks.SEA_LANTERN.getDefaultState()); }
            }
        b.set(-17, HY, 7, Blocks.GOLD_BLOCK.getDefaultState());
        b.set(-17, HY + 1, 7, ModBlocks.TREASURE_BLOCK.getDefaultState());
        b.set(-17, HY + 2, 7, candle(4));
        b.chest(-17, HY, 6, Direction.NORTH, "chests/phase1_shrine_treasure");
        b.chest(-17, HY, 8, Direction.SOUTH, "chests/phase1_shrine");
        b.chest(-20, HY, 7, Direction.EAST, "chests/phase1_shrine_tidebreaker");      // the key to the Sea Serpent
        for (int[] p : new int[][]{{-20, 4}, {-20, 10}, {-14, 4}}) {
            for (int y = HY; y <= HF + 7; y++) b.set(p[0], y, p[1], Blocks.PRISMARINE_BRICKS.getDefaultState());
            b.set(p[0], HF + 7, p[1], Blocks.SEA_LANTERN.getDefaultState());
        }
        for (int x = -19; x <= -15; x += 2) b.set(x, HY, 4, Blocks.DECORATED_POT.getDefaultState());
        b.set(-17, HF + 8, 7, Blocks.SEA_LANTERN.getDefaultState());

        // ---------------------------------------------------------------- stairwell (surface -> hall at z6)
        for (int k = 0; k <= 9; k++) {
            int y = -k, z = -3 + k;
            for (int x = -2; x <= 2; x++) {
                b.set(x, y - 1, z, brick.pick(r));
                if (Math.abs(x) == 2) { for (int h = 0; h <= 3; h++) b.set(x, y + h, z, brick.pick(r)); }
                else {
                    b.set(x, y, z, stairs(Direction.NORTH));                       // rises toward the entrance (-z)
                    for (int h = 1; h <= 3; h++) b.set(x, y + h, z, air);
                    if (y + 4 <= 0) b.set(x, y + 4, z, brick.pick(r));
                }
            }
        }

        // ---------------------------------------------------------------- surface ruin
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                boolean trench = Math.abs(x) <= 2 && z >= -3 && z <= 0;         // open part of the stairwell
                boolean overTunnel = Math.abs(x) <= 2 && z >= -3 && z <= 6;
                if (trench) continue;
                b.set(x, 0, z, (Math.abs(x) + Math.abs(z)) % 7 == 0 ? sea.pick(r) : brick.pick(r));
                if (!overTunnel) for (int y = -3; y < 0; y++) b.set(x, y, z, Blocks.STONE_BRICKS.getDefaultState());
                for (int y = 1; y <= 6; y++) b.set(x, y, z, air);
            }
        for (int[] p : new int[][]{{-5, -5}, {5, -5}, {-5, 5}, {5, 5}, {-5, 1}, {5, 1}, {0, 5}}) {
            int h = 1 + r.nextInt(5);
            for (int y = 1; y <= h; y++) b.set(p[0], y, p[1], y == h && r.nextBoolean() ? Blocks.CHISELED_STONE_BRICKS.getDefaultState() : brick.pick(r));
            b.set(p[0], h + 1, p[1], r.nextBoolean() ? Blocks.PRISMARINE_BRICK_SLAB.getDefaultState() : Blocks.DARK_PRISMARINE.getDefaultState());
        }
        // arch over the stair mouth
        for (int y = 1; y <= 3; y++) { b.set(-2, y, -4, Blocks.PRISMARINE_BRICKS.getDefaultState()); b.set(2, y, -4, Blocks.PRISMARINE_BRICKS.getDefaultState()); }
        for (int x = -2; x <= 2; x++) b.set(x, 4, -4, Blocks.DARK_PRISMARINE.getDefaultState());
        b.set(-1, 3, -4, stairs(Direction.EAST).with(Properties.BLOCK_HALF, BlockHalf.TOP));
        b.set(1, 3, -4, stairs(Direction.WEST).with(Properties.BLOCK_HALF, BlockHalf.TOP));
        b.set(0, 5, -4, Blocks.SEA_LANTERN.getDefaultState());
        b.set(-3, 1, -5, candle(2));
        b.set(3, 1, -5, Blocks.DECORATED_POT.getDefaultState());
    }
}
