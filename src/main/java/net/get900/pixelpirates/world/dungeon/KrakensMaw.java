package net.get900.pixelpirates.world.dungeon;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.entity.mob.KrakenEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

/**
 * THE KRAKEN'S MAW - lair of the Kraken (boss 7/10), enlarged 2026-09-29 keeping the original design: a bowl carved
 * into the seabed with ship wreckage on its rim, bones, sea lanterns and treasure - now r20 instead of r13, with:
 * <pre>
 *   abyss    a shaft r5 dropping 16 below the bowl floor, where the Kraken lurks in phase 1 (eyes glowing up out
 *            of the dark); it rises to the bowl floor for phase 2
 *   burrows  6 cracked, ink-stained mounds on a ring (r12) where its arms burst out of the floor
 *   wrecks   three hulls dragged over the rim, one snapped in half and half-way down the slope
 *   eggs     amber-glowing egg clutches (shroomlight in gravel) around the abyss mouth
 * </pre>
 * Design space: y 0 = seabed, the bowl centre (0,-1,0) rx 20 ry 9, its floor ~y -10 in the middle.
 */
public final class KrakensMaw {
    private KrakensMaw() {}

    private static final String P = "pixelpirates:";
    static final double RX = 20, RY = 9;
    static final int CY = -1, ABYSS_R = 5, ABYSS_DEPTH = 16;

    private static BlockState fluid(int y, int depth) {
        return y <= depth ? DungeonBuilder.water() : Blocks.AIR.getDefaultState();
    }

    /** Bowl floor height (top solid block) at horizontal distance d from the centre. */
    static int floorAt(double d) {
        double t = Math.min(1, d / RX);
        return CY - (int) Math.round(RY * Math.sqrt(1 - t * t));
    }

    public static void build(DungeonBuilder b, int depth) {
        Random r = b.random;
        DungeonBuilder.Weathered rim = new DungeonBuilder.Weathered(Blocks.PRISMARINE.getDefaultState(), ModBlocks.CORAL_ROCK.getDefaultState(),
                Blocks.DARK_PRISMARINE.getDefaultState(), 0.5f);
        DungeonBuilder.Weathered floor = new DungeonBuilder.Weathered(Blocks.GRAVEL.getDefaultState(), Blocks.SAND.getDefaultState(),
                Blocks.BLACK_TERRACOTTA.getDefaultState(), 0.3f);
        int abyssFloor = floorAt(0) - ABYSS_DEPTH;
        // ---------------------------------------------------------------- the bowl: rim shell, gravel floor, open water
        for (int x = -22; x <= 22; x++)
            for (int z = -22; z <= 22; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > RX + 1.5) continue;
                int fl = floorAt(d);
                if (d <= RX) {
                    b.set(x, fl - 1, z, rim.pick(r));
                    b.set(x, fl, z, d < ABYSS_R + 3 && r.nextInt(3) == 0 ? Blocks.BLACK_TERRACOTTA.getDefaultState() : floor.pick(r));   // ink stains near the abyss
                    for (int y = fl + 1; y <= Math.max(depth, 1) + 2; y++) b.set(x, y, z, fluid(y, depth));
                } else {
                    for (int y = CY - 2; y <= 0; y++) b.set(x, y, z, rim.pick(r));                                   // the lip
                }
            }
        // ---------------------------------------------------------------- the abyss
        for (int x = -ABYSS_R - 1; x <= ABYSS_R + 1; x++)
            for (int z = -ABYSS_R - 1; z <= ABYSS_R + 1; z++) {
                double d = Math.sqrt(x * x + z * z);
                if (d > ABYSS_R + 1.2) continue;
                for (int y = abyssFloor; y <= floorAt(d); y++) {
                    boolean wall = d > ABYSS_R - 0.2 || y == abyssFloor;
                    b.set(x, y, z, wall ? (r.nextInt(5) == 0 ? ModBlocks.CORAL_ROCK.getDefaultState() : Blocks.DARK_PRISMARINE.getDefaultState())
                            : DungeonBuilder.water());
                }
            }
        for (int i = 0; i < 10; i++) b.set(r.nextInt(7) - 3, abyssFloor + 1, r.nextInt(7) - 3, Blocks.BONE_BLOCK.getDefaultState());
        // ---------------------------------------------------------------- arm burrows (r12, 6 of them)
        List<BlockPos> spots = new ArrayList<>();
        for (int k = 0; k < KrakenEntity.ARM_COUNT; k++) {
            double a = Math.PI * 2 * k / KrakenEntity.ARM_COUNT + 0.3;
            int x = (int) Math.round(Math.cos(a) * 12), z = (int) Math.round(Math.sin(a) * 12);
            int fl = floorAt(Math.sqrt(x * x + z * z));
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz > 5) continue;
                b.set(x + dx, fl, z + dz, r.nextBoolean() ? Blocks.BLACK_TERRACOTTA.getDefaultState() : Blocks.COBBLED_DEEPSLATE.getDefaultState());
                if ((dx * dx + dz * dz >= 4) && r.nextInt(2) == 0) b.set(x + dx, fl + 1, z + dz, Blocks.COBBLED_DEEPSLATE.getDefaultState());
            }
            spots.add(b.pos(x, fl + 1, z).toImmutable());
        }
        // ---------------------------------------------------------------- amber egg clutches round the abyss mouth
        for (int k = 0; k < 5; k++) {
            double a = r.nextDouble() * Math.PI * 2, dd = ABYSS_R + 2 + r.nextDouble() * 3;
            int x = (int) Math.round(Math.cos(a) * dd), z = (int) Math.round(Math.sin(a) * dd);
            int fl = floorAt(Math.sqrt(x * x + z * z));
            for (int[] o : new int[][]{{0, 0}, {1, 0}, {0, 1}, {-1, 0}}) if (r.nextInt(4) != 0) b.set(x + o[0], fl, z + o[1], Blocks.SHROOMLIGHT.getDefaultState());
            b.set(x, fl + 1, z, Blocks.SEA_PICKLE.getDefaultState().with(Properties.PICKLES, 3).with(Properties.WATERLOGGED, fl + 1 <= depth));
        }
        // ---------------------------------------------------------------- bones and sea lanterns (as before, farther out)
        for (int i = 0; i < 26; i++) {
            double a = r.nextDouble() * Math.PI * 2, dd = ABYSS_R + 2 + r.nextDouble() * 12;
            int x = (int) Math.round(Math.cos(a) * dd), z = (int) Math.round(Math.sin(a) * dd);
            b.set(x, floorAt(Math.sqrt(x * x + z * z)) + 1, z, Blocks.BONE_BLOCK.getDefaultState().with(Properties.AXIS, Direction.Axis.pickRandomAxis(r)));
        }
        for (int k = 0; k < 8; k++) {
            double a = Math.PI * 2 * k / 8;
            int x = (int) Math.round(Math.cos(a) * 16), z = (int) Math.round(Math.sin(a) * 16);
            b.set(x, floorAt(16), z, Blocks.SEA_LANTERN.getDefaultState());
        }
        wrecks(b, r, depth);
        // ---------------------------------------------------------------- treasure (what it pulled down)
        b.chest(0, floorAt(9) + 1, 9, Direction.NORTH, "chests/phase2_treasure");
        b.chest(9, floorAt(9) + 1, 0, Direction.WEST, "chests/phase2_common");
        b.chest(-15, floorAt(15) + 1, -4, Direction.EAST, "chests/phase2_treasure");
        // ---------------------------------------------------------------- the Kraken, deep in the abyss
        double riseTo = b.pos(0, floorAt(0) + 1, 0).getY();
        b.spawnMob(P + "kraken", 0, abyssFloor + 1, 0, e -> { if (e instanceof KrakenEntity k) k.setLair(spots, riseTo); });
    }

    /** Three hulls dragged over the rim: two on the lip, one snapped and sliding down the slope. */
    private static void wrecks(DungeonBuilder b, Random r, int depth) {
        BlockState plank = Blocks.DARK_OAK_PLANKS.getDefaultState(), torn = ModBlocks.DESTROYED_PLANKS.getDefaultState(),
                mast = Blocks.SPRUCE_LOG.getDefaultState();
        double[] angles = {0.9, 2.9, 4.7};
        for (int w = 0; w < angles.length; w++) {
            double a = angles[w];
            double ca = Math.cos(a), sa = Math.sin(a);
            // hull axis runs radially: from the rim (s = 21) down into the bowl (s = 21 - len)
            int len = w == 2 ? 12 : 9;
            for (int s = 0; s <= len; s++) {
                double rr = 21 - s;
                int half = s < 2 || s > len - 2 ? 1 : 2;
                int cx = (int) Math.round(ca * rr), cz = (int) Math.round(sa * rr);
                int fl = rr > RX ? 0 : floorAt(rr);
                for (int k = -half; k <= half; k++) {
                    int x = cx + (int) Math.round(-sa * k), z = cz + (int) Math.round(ca * k);
                    b.set(x, fl + 1, z, r.nextInt(3) == 0 ? torn : plank);
                    if (Math.abs(k) == half) for (int y = fl + 2; y <= fl + 3; y++) if (r.nextInt(3) != 0) b.set(x, y, z, r.nextInt(4) == 0 ? torn : plank);
                }
                if (s == len / 2) for (int y = fl + 2; y <= fl + 7; y++) if (r.nextInt(6) != 0) b.set(cx, y, cz, mast);
                if (w == 2 && s == len / 2 + 1) s += 2;                                   // snapped in two
            }
        }
    }
}
