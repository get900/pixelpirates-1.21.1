package net.get900.pixelpirates.world.dungeon;

import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.Attachment;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * DUTCHMAN'S REST - where the Flying Dutchman is summoned (boss 4/10, rebuilt 2026-09-29). The boss is not
 * spawned here: ringing the Drowned Bell (a bell standing on a PHANTOM_BUOY) raises the Dutchman nearby
 * (GhostShipEncounter). Design space: y 0 = seabed, the sea surface is y = depth.
 * <pre>
 *   centre   rock spire out of the sea -> ghostwood landing with the bell under a lantern arch
 *   around   a graveyard of broken masts leaning out of the water, tattered spectral sails
 *   seabed   a half-buried wreck holding the loot, bones and soul barnacles
 * </pre>
 */
public final class DutchmansRest {
    private DutchmansRest() {}

    private static BlockState wet(BlockState s, int y, int depth) {
        return s.contains(Properties.WATERLOGGED) ? s.with(Properties.WATERLOGGED, y <= depth) : s;
    }

    public static void build(DungeonBuilder b, int depth) {
        Random r = b.random;
        int top = depth + 1;                                   // the landing, just above the waves
        DungeonBuilder.Weathered rock = new DungeonBuilder.Weathered(Blocks.TUFF.getDefaultState(),
                Blocks.DEEPSLATE.getDefaultState(), ModBlocks.SOUL_BARNACLE.getDefaultState(), 0.35f);
        // ---------------------------------------------------------------- the bell spire
        for (int y = 0; y <= top; y++) {
            double rad = 4.2 - 1.4 * y / Math.max(1, top);
            for (int x = -5; x <= 5; x++)
                for (int z = -5; z <= 5; z++)
                    if (Math.sqrt(x * x + z * z) <= rad) b.set(x, y, z, y == top ? ModBlocks.GHOSTWOOD_PLANKS.getDefaultState() : rock.pick(r));
        }
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) b.set(x, top, z, ModBlocks.GHOSTWOOD_PLANKS.getDefaultState());
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) {
            b.set(c[0], top + 1, c[1], Blocks.DARK_OAK_FENCE.getDefaultState());
            b.set(c[0], top + 2, c[1], Blocks.SOUL_LANTERN.getDefaultState());
        }
        b.set(0, top, 0, ModBlocks.PHANTOM_BUOY.getDefaultState());
        b.set(0, top + 1, 0, Blocks.BELL.getDefaultState().with(Properties.ATTACHMENT, Attachment.FLOOR)
                .with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        for (int y = top + 1; y <= top + 3; y++) { b.set(-1, y, 1, ModBlocks.GHOSTWOOD_LOG.getDefaultState()); b.set(1, y, 1, ModBlocks.GHOSTWOOD_LOG.getDefaultState()); }
        for (int x = -1; x <= 1; x++) b.set(x, top + 4, 1, ModBlocks.GHOSTWOOD_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
        b.set(0, top + 3, 1, Blocks.SOUL_LANTERN.getDefaultState().with(Properties.HANGING, true));
        b.set(0, top + 5, 1, Blocks.WITHER_SKELETON_SKULL.getDefaultState());
        b.set(2, top + 1, 0, Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.X));

        // ---------------------------------------------------------------- the graveyard of masts
        int[][] masts = {{-12, -9}, {13, -6}, {-15, 7}, {9, 13}, {1, -17}, {17, 10}};
        for (int[] m : masts) {
            int h = top + 4 + r.nextInt(6);
            int lean = r.nextBoolean() ? 1 : -1;
            for (int y = 0; y <= h; y++) {
                int dx = y > h / 2 ? lean : 0;
                b.set(m[0] + dx, y, m[1], ModBlocks.GHOSTWOOD_LOG.getDefaultState());
            }
            int yard = h - 3;
            for (int x = -2; x <= 2; x++) b.set(m[0] + lean + x, yard, m[1], ModBlocks.GHOSTWOOD_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
            for (int y = yard - 1; y >= Math.max(top + 1, yard - 3); y--)
                for (int x = -1; x <= 2; x++) if (r.nextInt(3) != 0) b.set(m[0] + lean + x - 1, y, m[1] - 1, ModBlocks.SPECTRAL_SAIL.getDefaultState());
            b.set(m[0] + lean, h + 1, m[1], Blocks.SOUL_LANTERN.getDefaultState());
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (r.nextBoolean()) b.set(m[0] + dx, 0, m[1] + dz, rock.pick(r));
        }

        // ---------------------------------------------------------------- the sunken hull (loot)
        int hx = -8, hz = 12;
        for (int z = -4; z <= 4; z++)
            for (int x = -3; x <= 3; x++) {
                int y = Math.abs(x) == 3 ? 2 : Math.abs(x) == 2 ? 1 : 0;
                b.set(hx + x, y, hz + z, r.nextInt(4) == 0 ? Blocks.DARK_OAK_PLANKS.getDefaultState() : ModBlocks.GHOSTWOOD_PLANKS.getDefaultState());
                if (Math.abs(x) == 3 && z % 2 == 0 && depth > 3) b.set(hx + x, 3, hz + z, ModBlocks.GHOSTWOOD_LOG.getDefaultState());
            }
        b.chest(hx, 1, hz - 1, Direction.SOUTH, "chests/phase4_common");
        b.chest(hx, 1, hz + 2, Direction.NORTH, "chests/dutchman_hoard");
        b.set(hx + 1, 1, hz + 1, wet(Blocks.CHAIN.getDefaultState(), 1, depth));
        wrecks(b, r, depth);
        // flotsam on the surface
        for (int i = 0; i < 10; i++) {
            int x = r.nextInt(37) - 18, z = r.nextInt(37) - 18;
            if (x * x + z * z < 49) continue;
            b.set(x, depth, z, r.nextBoolean() ? ModBlocks.GHOSTWOOD_PLANKS.getDefaultState() : Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        }
        for (int i = 0; i < 14; i++) {
            int x = r.nextInt(33) - 16, z = r.nextInt(33) - 16;
            if (x * x + z * z < 36) continue;
            b.set(x, 1, z, r.nextInt(3) == 0 ? ModBlocks.SOUL_BARNACLE.getDefaultState() : Blocks.BONE_BLOCK.getDefaultState());
        }
    }

    /** More of the Dutchman's victims: a capsized bow breaking the surface, a stern section, ribs and flotsam. */
    private static void wrecks(DungeonBuilder b, Random r, int depth) {
        BlockState plank = ModBlocks.GHOSTWOOD_PLANKS.getDefaultState(), dark = Blocks.DARK_OAK_PLANKS.getDefaultState();
        // capsized bow: a hull wedge tilted up out of the water, keel toward the sky
        int bx = 12, bz = 9;
        for (int k = 0; k <= 8; k++) {
            int y = k, z = bz + k / 2, w = Math.max(0, 3 - k / 3);
            for (int x = -w; x <= w; x++) {
                b.set(bx + x, y, z, r.nextInt(4) == 0 ? dark : plank);
                if (Math.abs(x) == w) b.set(bx + x, y + 1, z, plank);
            }
            if (k == 8) b.set(bx, y + 1, z, ModBlocks.GHOSTWOOD_LOG.getDefaultState());
        }
        b.set(bx, 10, bz + 5, Blocks.SOUL_LANTERN.getDefaultState());
        // a stern section lying on its side, a ghost cannon spilled beside it
        int sx = 6, sz = -14;
        for (int x = -3; x <= 3; x++)
            for (int y = 0; y <= 4; y++) {
                boolean shell = y == 0 || Math.abs(x) == 3 || (y == 4 && r.nextBoolean());
                if (shell) b.set(sx + x, y, sz, r.nextInt(5) == 0 ? dark : plank);
                if (shell && x % 2 == 0) b.set(sx + x, y, sz - 1, plank);
            }
        b.set(sx, 1, sz + 2, ModBlocks.GHOST_CANNON.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
        b.barrelLoot(sx - 1, 1, sz + 1, "chests/phase4_common");
        // ribs of a hull picked clean, arching out of the sand
        int rx = -14, rz = -6;
        for (int k = 0; k < 5; k++) {
            int z = rz + k * 2;
            for (int y = 0; y <= 4; y++) { b.set(rx - 2, y, z, ModBlocks.GHOSTWOOD_LOG.getDefaultState()); b.set(rx + 2, y, z, ModBlocks.GHOSTWOOD_LOG.getDefaultState()); }
            for (int x = -1; x <= 1; x++) b.set(rx + x, 5, z, ModBlocks.GHOSTWOOD_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
        }
        for (int z = rz; z <= rz + 8; z++) b.set(rx, 0, z, ModBlocks.GHOSTWOOD_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.Z));   // keel
    }
}
