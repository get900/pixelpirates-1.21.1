package net.get900.pixelpirates.world;

import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * THE FLYING DUTCHMAN - the Ghost Captain's ship (boss 4/10), generated in code as a ShipSchematic so it
 * spawns through ShipSpawner like any blueprint and sails with the normal AI (AiShipController).
 * Authored with the bow toward -z, then MIRRORED on export so the bow faces +z - the AI and the thrust in
 * ShipSteeringManager treat ship-space +z as forward (the first version sailed stern-first). Keel y 0, main deck y 5 (the SHIP_WATERLINE block at y 2 sits at sea level,
 * so the deck rides 3 above the water), poop deck y 9.
 * <pre>
 *   hull     z -14..13, V bottom, rotted ghostwood with a log gunwale and glowing soul barnacles
 *   masts    11 GHOST_MAST drive blocks (5 under the masts, 6 in the keel), each worth two masts of thrust
 *   guns     8 GHOST_CANNONs on the main deck, 4 each side (their positions become the AI's broadside)
 *   stern    captain's cabin z 8..12 under the poop deck; the captain stands at {@link #CAPTAIN_SPOT}
 * </pre>
 */
public final class GhostShipDesign {
    private GhostShipDesign() {}

    public static final String BLUEPRINT = "flying_dutchman";
    /** Where the Ghost Captain stands (poop deck, by the stern rail). */
    public static final BlockPos CAPTAIN_SPOT = new BlockPos(0, 10, -10);          // (exported, bow-forward coordinates)
    /** Crew posts on the main deck. */
    public static final BlockPos[] CREW_SPOTS = {new BlockPos(-2, 6, 4), new BlockPos(2, 6, 4), new BlockPos(-2, 6, -3), new BlockPos(2, 6, -3)};
    /** Thrust per ghost mast, in ship-mast units: the Dutchman is heavy. */
    public static final int THRUST_PER_GHOST_MAST = 2;

    private static final BlockState PLANK = ModBlocks.GHOSTWOOD_PLANKS.getDefaultState();
    private static final BlockState LOG_Y = ModBlocks.GHOSTWOOD_LOG.getDefaultState();
    private static final BlockState LOG_X = ModBlocks.GHOSTWOOD_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X);
    private static final BlockState LOG_Z = ModBlocks.GHOSTWOOD_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.Z);
    private static final BlockState SAIL = ModBlocks.SPECTRAL_SAIL.getDefaultState();

    private static int halfWidth(int z) {
        if (z < -6) return Math.max(1, 4 - (-6 - z + 1) / 2);
        if (z > 9) return Math.max(2, 4 - (z - 9 + 1) / 2);
        return 4;
    }

    /** The whole ship as a schematic (deterministic - same ship every time). */
    public static ShipSchematic schematic() {
        Map<BlockPos, BlockState> b = new LinkedHashMap<>();
        Random r = Random.create(0x0D07C4L);
        // ---------------------------------------------------------------- hull
        for (int z = -14; z <= 13; z++) {
            int hw = halfWidth(z);
            for (int y = 0; y <= 4; y++) {
                int w = Math.min(hw, y + 1);
                for (int x = -w; x <= w; x++) {
                    boolean shell = Math.abs(x) == w || y <= 1 || z == -14 || z == 13 || Math.abs(x) > halfWidth(z + (z < 0 ? -1 : 1));
                    if (!shell) continue;
                    BlockState s = y == 4 && Math.abs(x) == w ? LOG_Z
                            : r.nextInt(14) == 0 && Math.abs(x) == w && y >= 2 ? ModBlocks.SOUL_BARNACLE.getDefaultState()
                            : r.nextInt(6) == 0 ? Blocks.DARK_OAK_PLANKS.getDefaultState() : PLANK;
                    b.put(new BlockPos(x, y, z), s);
                }
            }
            for (int x = -hw; x <= hw; x++) b.put(new BlockPos(x, 5, z), (x == 0 && z % 4 == 0) ? LOG_Z : PLANK);   // main deck
        }
        b.put(new BlockPos(0, 2, 0), ModBlocks.SHIP_WATERLINE.getDefaultState());                          // floats at sea level
        // six more ghost masts sunk in the keel: the Dutchman is heavy, and speed scales with mast blocks
        for (int z : new int[]{-9, -6, -3, 3, 6, 9}) b.put(new BlockPos(0, 1, z), ModBlocks.GHOST_MAST.getDefaultState());
        // ---------------------------------------------------------------- rails, lanterns
        for (int z = -13; z <= 12; z++) {
            int hw = halfWidth(z);
            for (int sx = -1; sx <= 1; sx += 2) {
                boolean post = z % 5 == 0;
                b.put(new BlockPos(sx * hw, 6, z), post ? Blocks.DARK_OAK_FENCE.getDefaultState() : Blocks.CHAIN.getDefaultState().with(Properties.AXIS, Direction.Axis.Z));
                if (post) b.put(new BlockPos(sx * hw, 7, z), Blocks.SOUL_LANTERN.getDefaultState());
            }
        }
        // ---------------------------------------------------------------- ghost cannons (replace the rail)
        for (int z : new int[]{-6, -2, 2, 6})
            for (int sx = -1; sx <= 1; sx += 2)
                b.put(new BlockPos(sx * halfWidth(z), 6, z), ModBlocks.GHOST_CANNON.getDefaultState()
                        .with(Properties.HORIZONTAL_FACING, sx > 0 ? Direction.EAST : Direction.WEST));
        // ---------------------------------------------------------------- stern castle: cabin + poop deck
        for (int z = 8; z <= 13; z++) {
            int hw = halfWidth(z);
            for (int x = -hw; x <= hw; x++) {
                for (int y = 6; y <= 8; y++) {
                    boolean wall = Math.abs(x) == hw || z == 8 || z == 13;
                    boolean door = z == 8 && Math.abs(x) <= 0 && y <= 7;
                    boolean window = (Math.abs(x) == hw && y == 7 && (z == 10 || z == 12)) || (z == 13 && y == 7 && Math.abs(x) == 1);
                    b.remove(new BlockPos(x, y, z));
                    if (wall && !door && !window) b.put(new BlockPos(x, y, z), y == 8 ? LOG_X : PLANK);
                }
                b.put(new BlockPos(x, 9, z), PLANK);
                if (Math.abs(x) == hw || z == 13) b.put(new BlockPos(x, 10, z), Blocks.DARK_OAK_FENCE.getDefaultState());
            }
        }
        b.put(new BlockPos(0, 7, 11), Blocks.SOUL_LANTERN.getDefaultState());
        b.put(new BlockPos(1, 6, 12), Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
        b.put(new BlockPos(-2, 11, 13), Blocks.SOUL_LANTERN.getDefaultState());
        b.put(new BlockPos(2, 11, 13), Blocks.SOUL_LANTERN.getDefaultState());
        b.put(new BlockPos(0, 10, 13), Blocks.SOUL_CAMPFIRE.getDefaultState());
        // stairs from the main deck up to the poop deck (port side)
        for (int k = 0; k < 3; k++)
            b.put(new BlockPos(-3, 6 + k, 7 - k), Blocks.DARK_OAK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH).with(Properties.BLOCK_HALF, BlockHalf.BOTTOM));
        // ---------------------------------------------------------------- masts, yards, tattered sails
        int[][] masts = {{-10, 6, 15}, {-5, 6, 19}, {0, 6, 21}, {5, 6, 17}, {11, 10, 19}};   // z, base y, top (last = mizzen on the poop deck)
        for (int[] mt : masts) {
            int z = mt[0], base = mt[1], top = mt[2];
            b.put(new BlockPos(0, base, z), ModBlocks.GHOST_MAST.getDefaultState());
            for (int y = base + 1; y <= top; y++) b.put(new BlockPos(0, y, z), LOG_Y);
            int lo = Math.max(base + 3, top - 9), hi = top - 1;
            for (int yard : new int[]{lo, hi})
                for (int x = -5; x <= 5; x++) if (x != 0) b.put(new BlockPos(x, yard, z), LOG_X);
            for (int y = lo + 1; y < hi; y++)
                for (int x = -4; x <= 4; x++) {
                    if (x == 0) continue;
                    boolean ragged = (y == lo + 1 && r.nextInt(2) == 0) || r.nextInt(9) == 0;   // torn hem + holes
                    if (!ragged) b.put(new BlockPos(x, y, z - 1), SAIL);
                }
            b.put(new BlockPos(-5, hi - 1, z), Blocks.COBWEB.getDefaultState());
            b.put(new BlockPos(5, lo + 1, z), Blocks.CHAIN.getDefaultState());
            b.put(new BlockPos(0, top + 1, z), Blocks.SOUL_LANTERN.getDefaultState());
        }
        // crow's nest + black flag on the main mast
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) if (x != 0 || z != 0) b.put(new BlockPos(x, 17, z), PLANK);
        for (int y = 22; y <= 23; y++) for (int z = 1; z <= 3; z++) b.put(new BlockPos(0, y, z), Blocks.BLACK_WOOL.getDefaultState());
        // ---------------------------------------------------------------- bowsprit + skull figurehead
        b.put(new BlockPos(0, 6, -14), LOG_Z);
        b.put(new BlockPos(0, 6, -15), LOG_Z);
        b.put(new BlockPos(0, 7, -16), LOG_Z);
        b.put(new BlockPos(0, 7, -17), LOG_Z);
        b.put(new BlockPos(0, 5, -15), Blocks.WITHER_SKELETON_SKULL.getDefaultState());
        b.put(new BlockPos(0, 8, -17), Blocks.SOUL_LANTERN.getDefaultState());
        b.put(new BlockPos(0, 6, 8), Blocks.CHAIN.getDefaultState());

        // export mirrored along z (bow -> +z); LEFT_RIGHT swaps north/south facings to match
        List<ShipSchematic.Entry> entries = new ArrayList<>(b.size());
        for (var e : b.entrySet()) {
            BlockPos p = e.getKey();
            BlockState st = e.getValue().mirror(net.minecraft.util.BlockMirror.LEFT_RIGHT);
            entries.add(new ShipSchematic.Entry(new BlockPos(p.getX(), p.getY(), -p.getZ()), NbtHelper.fromBlockState(st)));
        }
        int ghostMasts = (int) b.values().stream().filter(st -> st.isOf(ModBlocks.GHOST_MAST)).count();
        return ShipSchematic.of(entries, ghostMasts * THRUST_PER_GHOST_MAST);
    }
}
