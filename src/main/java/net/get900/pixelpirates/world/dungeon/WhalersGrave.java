package net.get900.pixelpirates.world.dungeon;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.block.custom.HarpoonWinchBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * THE WHALERS' GRAVE - lair of the Bloodfin (boss 6/10), rebuilt 2026-09-29 (was "Bloodfin's Reef").
 * A whaling station that tried to hunt the Bloodfin and became its larder. Design space: y 0 = seabed, sea surface
 * at y = depth (SEABED site, depth >= 7), everything within |x|,|z| <= 22.
 * <pre>
 *   atoll    a ring of jagged rock (r 18..22) breaking the surface, two boat channels (north/south) left open
 *   lagoon   "the Gut": the floor dug into a bowl 10 deep at the centre so the shark has room to dive and breach;
 *            blood-red fire coral on the walls, bones everywhere, the victims' hoard at the very bottom
 *   winches  4 whalers' platforms on the rocks (NE/NW/SE/SW) - a HARPOON WINCH facing the lagoon, a harpoon/chum
 *            barrel, rope coils, a lantern post
 *   whale    a colossal whale skeleton half-sunk across the lagoon, its ribs arching out of the water
 *   wreck    a whaler's hull run up on the east rocks: flensing rig (chains + hooks), tryworks (cauldrons on fire)
 * </pre>
 */
public final class WhalersGrave {
    private WhalersGrave() {}

    private static final String P = "pixelpirates:";
    static final int R_IN = 18, R_OUT = 22, GUT = 10;          // as wide as the +-22 feature reach allows

    private static BlockState fluid(int y, int depth) {
        return y <= depth ? DungeonBuilder.water() : Blocks.AIR.getDefaultState();
    }

    private static BlockState wet(BlockState s, int y, int depth) {
        return s.contains(Properties.WATERLOGGED) ? s.with(Properties.WATERLOGGED, y <= depth) : s;
    }

    public static void build(DungeonBuilder b, int depth) {
        Random r = b.random;
        DungeonBuilder.Weathered rock = new DungeonBuilder.Weathered(ModBlocks.TIDE_POOL_ROCK.getDefaultState(), Blocks.ANDESITE.getDefaultState(),
                Blocks.MOSSY_COBBLESTONE.getDefaultState(), 0.45f);
        DungeonBuilder.Weathered sand = new DungeonBuilder.Weathered(Blocks.SAND.getDefaultState(), Blocks.GRAVEL.getDefaultState(),
                Blocks.BONE_BLOCK.getDefaultState(), 0.18f);
        int top = depth;                                                   // platform decks flush with the waves: winches at water level
        // ---------------------------------------------------------------- atoll + lagoon bowl
        for (int x = -22; x <= 22; x++)
            for (int z = -22; z <= 22; z++) {
                double d = Math.sqrt(x * x + z * z);
                double ang = Math.atan2(x, z);
                boolean channel = Math.abs(Math.sin(ang)) < 0.2;          // north + south boat channels
                if (d < R_IN) {
                    int floor = -(int) Math.round(GUT * (1 - (d / R_IN) * (d / R_IN)));
                    for (int y = floor - 2; y < floor; y++) b.set(x, y, z, Blocks.STONE.getDefaultState());
                    b.set(x, floor, z, sand.pick(r));
                    for (int y = floor + 1; y <= depth + 12; y++) b.set(x, y, z, fluid(y, depth));
                } else if (d <= R_OUT && !channel) {
                    // jagged teeth: height varies around the ring, highest near the platforms
                    double n = Math.sin(ang * 7 + 1.3) * 0.5 + Math.sin(ang * 13) * 0.3 + r.nextDouble() * 0.4;
                    int h = depth - 1 + (int) Math.round(2 + n * 3 - Math.abs(d - 20));
                    for (int y = 0; y <= Math.max(1, h); y++) b.set(x, y, z, rock.pick(r));
                    for (int y = Math.max(1, h) + 1; y <= depth + 12; y++) b.set(x, y, z, fluid(y, depth));
                } else {
                    for (int y = 1; y <= depth + 12; y++) b.set(x, y, z, fluid(y, depth));
                }
            }
        // blood coral + bones on the lagoon walls and floor
        BlockState[] coral = {Blocks.FIRE_CORAL_BLOCK.getDefaultState(), Blocks.FIRE_CORAL_BLOCK.getDefaultState(), Blocks.BRAIN_CORAL_BLOCK.getDefaultState()};
        for (int i = 0; i < 60; i++) {
            double a = r.nextDouble() * Math.PI * 2, dd = 3 + r.nextDouble() * 12;
            int x = (int) Math.round(Math.sin(a) * dd), z = (int) Math.round(Math.cos(a) * dd);
            int floor = -(int) Math.round(GUT * (1 - (dd / R_IN) * (dd / R_IN)));
            if (r.nextInt(3) == 0) b.set(x, floor + 1, z, Blocks.BONE_BLOCK.getDefaultState().with(Properties.AXIS, Direction.Axis.pickRandomAxis(r)));
            else if (r.nextInt(2) == 0) { b.set(x, floor + 1, z, coral[r.nextInt(coral.length)]); b.set(x, floor + 2, z, Blocks.FIRE_CORAL_FAN.getDefaultState()); }
            else b.set(x, floor + 1, z, Blocks.FIRE_CORAL.getDefaultState());
        }
        // ---------------------------------------------------------------- the Gut: the victims' hoard
        int gf = -GUT;
        for (int[] p : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) b.set(p[0], gf, p[1], Blocks.SEA_LANTERN.getDefaultState());
        b.chest(0, gf + 1, 0, Direction.NORTH, "chests/whalers_hoard");
        b.set(1, gf + 1, 1, Blocks.SKELETON_SKULL.getDefaultState().with(Properties.ROTATION, 6));
        b.set(-1, gf + 1, -1, Blocks.BONE_BLOCK.getDefaultState());

        whale(b, r, depth);
        for (int[] p : new int[][]{{13, 13}, {-13, 13}, {13, -13}, {-13, -13}}) winch(b, r, p[0], p[1], top, depth);
        wreck(b, r, top, depth);

        // ---------------------------------------------------------------- the hunter and its pack
        // the Bloodfin is kept inside the lagoon (BloodfinEntity#setArena) so the winches can always reach it
        net.minecraft.util.math.BlockPos centre = b.pos(0, 0, 0).toImmutable();
        b.spawnMob(P + "bloodfin", 0, Math.min(-2, depth / 2 - GUT / 2), 0, e -> {
            if (e instanceof net.get900.pixelpirates.entity.mob.BloodfinEntity bf) bf.setArena(centre, R_IN - 1);
        });
    }

    /** A colossal whale skeleton lying across the lagoon: spine on the bowl, ribs arching out of the water, skull west. */
    private static void whale(DungeonBuilder b, Random r, int depth) {
        BlockState boneX = Blocks.BONE_BLOCK.getDefaultState().with(Properties.AXIS, Direction.Axis.X);
        BlockState boneY = Blocks.BONE_BLOCK.getDefaultState();
        int z0 = 5;
        for (int x = -11; x <= 8; x++) {
            double d = Math.sqrt(x * x + z0 * z0);
            int floor = -(int) Math.round(GUT * (1 - Math.min(1, (d / R_IN) * (d / R_IN))));
            b.set(x, floor + 1, z0, boneX);                                                  // spine
            if (x % 3 == 0 && x > -9) {                                                      // a pair of ribs arching up
                int reach = depth + 3 - floor;
                for (int k = 0; k <= reach; k++) {
                    double t = k / (double) reach;
                    int off = (int) Math.round(Math.sin(t * Math.PI * 0.85) * 5);
                    int y = floor + 1 + k;
                    if (r.nextInt(9) == 0 && k > 2) continue;                                 // broken ribs
                    b.set(x, y, z0 - off, boneY);
                    b.set(x, y, z0 + off, boneY);
                }
            }
        }
        // the skull: a heap of bone at the west end, jaw bones jutting out
        for (int x = -15; x <= -12; x++) for (int y = -3; y <= 0; y++) for (int z = z0 - 2; z <= z0 + 2; z++)
            if (r.nextInt(5) != 0 && Math.abs(z - z0) + Math.abs(y + 1) <= 3) b.set(x, y, z, boneY);
        for (int x = -18; x <= -15; x++) { b.set(x, -1, z0 - 2, boneX); b.set(x, -1, z0 + 2, boneX); }
    }

    /** A whalers' platform on the rocks with a harpoon winch aimed at the lagoon. */
    private static void winch(DungeonBuilder b, Random r, int cx, int cz, int top, int depth) {
        BlockState plank = Blocks.SPRUCE_PLANKS.getDefaultState(), log = Blocks.STRIPPED_SPRUCE_LOG.getDefaultState();
        int sx = Integer.signum(cx), sz = Integer.signum(cz);
        // stilts down to the seabed, a 5x5 deck
        for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}})
            for (int y = 0; y < top; y++) b.set(cx + c[0], y, cz + c[1], log);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) b.set(cx + x, top, cz + z, plank);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            for (int y = top + 1; y <= top + 4; y++) b.set(cx + x, y, cz + z, Blocks.AIR.getDefaultState());
        // the winch at the lagoon-side corner, facing the lagoon centre
        Direction face = Math.abs(cx) >= Math.abs(cz) ? (sx > 0 ? Direction.WEST : Direction.EAST) : (sz > 0 ? Direction.NORTH : Direction.SOUTH);
        int wx = cx - sx, wz = cz - sz;
        b.set(wx, top + 1, wz, ModBlocks.HARPOON_WINCH.getDefaultState().with(HarpoonWinchBlock.FACING, face));
        // rail on the outer sides, a lantern post, rope coils (barrels of harpoons + chum)
        for (int k = -2; k <= 2; k++) {
            b.set(cx + 2 * sx, top + 1, cz + k, Blocks.SPRUCE_FENCE.getDefaultState());
            b.set(cx + k, top + 1, cz + 2 * sz, Blocks.SPRUCE_FENCE.getDefaultState());
        }
        b.set(cx + 2 * sx, top + 2, cz + 2 * sz, Blocks.SPRUCE_FENCE.getDefaultState());
        b.set(cx + 2 * sx, top + 3, cz + 2 * sz, Blocks.LANTERN.getDefaultState());
        b.barrelLoot(cx + sx, top + 1, cz + sz, "chests/whalers_armory");
        b.set(cx, top + 1, cz + sz, Blocks.BROWN_WOOL.getDefaultState());                              // rope coil
        b.set(cx + sx, top + 1, cz, Blocks.WHITE_CARPET.getDefaultState());
        // a spare harpoon driven into the rock beside the deck
        for (int y = top; y <= top + 2; y++) b.set(cx + 3 * sx, y, cz, wet(Blocks.IRON_BARS.getDefaultState(), y, depth));
    }

    /** A whaler's hull run up on the east rocks, with the flensing rig and the tryworks. */
    private static void wreck(DungeonBuilder b, Random r, int top, int depth) {
        BlockState hull = Blocks.DARK_OAK_PLANKS.getDefaultState(), rib = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState();
        int x0 = 20;
        for (int z = -6; z <= 6; z++) {
            int w = Math.max(1, 2 - Math.abs(z) / 4);
            for (int x = -w; x <= w; x++) {
                int base = top - 2 + Math.abs(x);
                b.set(x0 + x, base, z, r.nextInt(6) == 0 ? rib : hull);
                if (Math.abs(x) == w) for (int y = base + 1; y <= top + 1; y++) if (r.nextInt(5) != 0) b.set(x0 + x, y, z, hull);
            }
            b.set(x0, top - 2, z, rib);                                                       // keel
        }
        for (int x = -2; x <= 2; x++) for (int z = -4; z <= 4; z++) b.set(x0 + x, top + 1, z, Blocks.SPRUCE_PLANKS.getDefaultState());   // deck
        // flensing rig: a gantry over the lagoon side with chains and hooks
        for (int y = top + 2; y <= top + 6; y++) { b.set(x0 - 2, y, -3, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState()); b.set(x0 - 2, y, 3, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState()); }
        for (int z = -3; z <= 3; z++) b.set(x0 - 2, top + 7, z, Blocks.STRIPPED_SPRUCE_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.Z));
        for (int z = -2; z <= 2; z += 2) {
            for (int y = top + 3; y <= top + 6; y++) b.set(x0 - 2, y, z, Blocks.CHAIN.getDefaultState());
            b.set(x0 - 2, top + 2, z, Blocks.TRIPWIRE_HOOK.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        }
        // tryworks: whale-oil kettles over fire
        for (int z = -1; z <= 1; z += 2) {
            b.set(x0 + 1, top + 1, z, Blocks.CAMPFIRE.getDefaultState());
            b.set(x0 + 1, top + 2, z, Blocks.CAULDRON.getDefaultState());
        }
        b.set(x0 + 1, top + 2, 0, Blocks.BRICKS.getDefaultState());
        b.chest(x0, top + 2, -3, Direction.WEST, "chests/whalers_armory");
        b.set(x0, top + 2, 3, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.set(x0 + 2, top + 2, 4, Blocks.LANTERN.getDefaultState());
    }
}
