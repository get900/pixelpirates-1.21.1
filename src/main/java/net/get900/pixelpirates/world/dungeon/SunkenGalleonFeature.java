package net.get900.pixelpirates.world.dungeon;

import com.mojang.serialization.Codec;
import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.SlabType;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.tag.FluidTags;
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
 * Phase 1 dungeon: SUNKEN GALLEON.
 * A warship lying on the seabed, bow toward -z. Swim in through the hull breach (+x side):
 * flooded hold (drowned spawner, supplies), gun deck with cannons at the ports, a partly
 * collapsed upper deck, snapped masts, and the captain's cabin in the stern with the treasure.
 * In deep water it lies fully sunken; in the shallows it is run aground with its upper hull
 * above the waves (flooded below the waterline, dry above).
 */
public final class SunkenGalleonFeature {
    private static final int HALF_LEN = 17;
    private static final int HOLD = 1, GUN = 4, UPPER = 7, CABIN_FLOOR = 7, CASTLE_TOP = 10;

    private SunkenGalleonFeature() {}

    /** Hull half-width at (z, y): full amidships, tapering toward bow and stern, narrow at the keel. */
    private static int halfWidth(int z, int y) {
        int w = y <= -1 ? 1 : y == 0 ? 3 : y == 1 ? 4 : 5;
        int end = z < 0 ? -z - 10 : z - 13;            // sharp bow, broad square stern
        if (end > 0) w -= (end + 1) / 2;
        return Math.max(0, w);
    }

    public static void build(DungeonBuilder b, int depth) {
        Random r = b.random;
        BlockState water = DungeonBuilder.water();
        BlockState hull = Blocks.DARK_OAK_PLANKS.getDefaultState();
        BlockState rot = ModBlocks.DESTROYED_PLANKS.getDefaultState();
        BlockState deck = Blocks.SPRUCE_PLANKS.getDefaultState();
        BlockState waleLog = Blocks.STRIPPED_DARK_OAK_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.Z);
        // Deep water: fully sunken (castle kept 3 below the surface). Shallows (the common case in
        // Temperate Shallows, only 0-5 deep): a RUN-AGROUND wreck whose hull breaks the waves -
        // everything below the waterline is flooded, everything above it is dry, boardable deck.
        boolean sunken = depth >= CASTLE_TOP + 3;
        int top = sunken ? Math.min(CASTLE_TOP, depth - 3) : CASTLE_TOP;
        java.util.function.IntFunction<BlockState> open = y -> y <= depth ? water : Blocks.AIR.getDefaultState();

        // ---------------------------------------------------------------- hull shell + flooded interior
        for (int z = -HALF_LEN; z <= HALF_LEN; z++) {
            boolean stern = z >= 10, bow = z <= -13;
            int topY = stern ? top : bow ? UPPER + 2 : UPPER;
            for (int y = -1; y <= topY; y++) {
                int hw = halfWidth(z, Math.min(y, 6));
                for (int x = -hw; x <= hw; x++) {
                    boolean side = Math.abs(x) == hw || y == -1 || Math.abs(z) == HALF_LEN && Math.abs(x) <= hw;
                    boolean deckLevel = y == HOLD || y == GUN || y == UPPER || (stern && y == topY);
                    BlockState s;
                    if (side) s = y == GUN - 1 && Math.abs(x) == hw ? waleLog : (r.nextInt(9) == 0 ? rot : hull);
                    else if (y == 0) s = Blocks.GRAVEL.getDefaultState();                      // ballast
                    else if (deckLevel) s = (y == UPPER && !stern && r.nextInt(5) == 0) ? open.apply(y) : deck;  // rotten holes up top
                    else s = open.apply(y);
                    // random stove-in hull planks
                    if (side && y > 1 && r.nextInt(14) == 0) s = open.apply(y);
                    b.set(x, y, z, s);
                }
            }
        }
        // the breach: a jagged hole on the +x side amidships, debris on the seabed
        for (int z = -4; z <= 1; z++)
            for (int y = HOLD; y <= GUN + 1; y++)
                if (r.nextInt(6) != 0 || (y > HOLD && y < GUN + 1)) b.set(halfWidth(z, y), y, z, open.apply(y));
        for (int i = 0; i < 10; i++) b.set(7 + r.nextInt(5), 0, -6 + r.nextInt(10), r.nextBoolean() ? rot : hull);

        // ---------------------------------------------------------------- hold
        b.spawner(0, HOLD + 1, -3, EntityType.DROWNED);
        b.chest(-3, HOLD + 1, 5, Direction.EAST, "chests/phase1_galleon_hold");
        for (int z = -9; z <= 8; z += 2) {
            if (z >= -4 && z <= 1) continue;
            if (r.nextBoolean()) b.set(-3, HOLD + 1, z, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.EAST));
            if (r.nextBoolean()) b.set(3, HOLD + 1, z, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.WEST));
        }
        b.barrelLoot(3, HOLD + 1, 7, "chests/phase1_galleon_hold");
        for (int z = -8; z <= 8; z += 8) {                                            // hatches between decks
            b.set(0, GUN, z, open.apply(GUN)); b.set(1, GUN, z, open.apply(GUN));
            b.set(0, UPPER, z, open.apply(UPPER));
        }

        // ---------------------------------------------------------------- gun deck: cannons at the ports
        for (int z = -8; z <= 8; z += 4) {
            for (int side : new int[]{-1, 1}) {
                int hw = halfWidth(z, GUN + 1);
                if (side == 1 && z >= -4 && z <= 1) continue;                            // breach side
                b.set(side * hw, GUN + 1, z, open.apply(GUN + 1));                       // gunport
                Direction out = side > 0 ? Direction.EAST : Direction.WEST;
                b.set(side * (hw - 1), GUN + 1, z, ModBlocks.SHIP_CANNON.getDefaultState()
                        .with(Properties.HORIZONTAL_FACING, out).with(net.get900.pixelpirates.block.custom.CannonBlock.LOADED, false));
                if (r.nextBoolean()) b.set(side * (hw - 2), GUN + 1, z + 1, Blocks.COAL_BLOCK.getDefaultState());   // shot pile
            }
        }
        for (int z = -6; z <= 6; z += 6)
            b.set(0, GUN + 3, z, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true).with(Properties.WATERLOGGED, GUN + 3 <= depth));

        // ---------------------------------------------------------------- masts: one snapped standing, one fallen
        int mainTop = sunken ? Math.min(UPPER + 3 + r.nextInt(5), depth - 2) : UPPER + 6 + r.nextInt(6);
        for (int y = HOLD; y <= mainTop; y++) b.set(0, y, 0, Blocks.SPRUCE_LOG.getDefaultState());
        b.set(0, mainTop, 1, Blocks.SPRUCE_FENCE.getDefaultState().with(Properties.WATERLOGGED, mainTop <= depth));
        for (int x = 3; x <= 15; x++) {
            b.set(Math.min(x, 15), 0, -9, Blocks.SPRUCE_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
            if (x > 7 && r.nextInt(3) != 0) b.set(x, 1, -8 - r.nextInt(2), r.nextBoolean() ? Blocks.WHITE_WOOL.getDefaultState() : Blocks.LIGHT_GRAY_WOOL.getDefaultState());
        }

        // ---------------------------------------------------------------- captain's cabin (stern castle)
        int cf = CABIN_FLOOR;
        for (int z = 11; z <= HALF_LEN - 1; z++)
            for (int x = -3; x <= 3; x++)
                for (int y = cf + 1; y < top; y++) if (Math.abs(x) < halfWidth(z, 6)) b.set(x, y, z, open.apply(y));
        b.set(0, cf + 1, 10, open.apply(cf + 1)); b.set(0, cf + 2, 10, open.apply(cf + 2));                        // cabin door from the deck
        for (int x = -2; x <= 2; x += 2)                                                  // stern windows
            b.set(x, cf + 2, HALF_LEN, Blocks.GLASS_PANE.getDefaultState().with(Properties.WATERLOGGED, cf + 2 <= depth));
        b.chest(0, cf + 1, 15, Direction.NORTH, "chests/phase1_galleon_captain");
        b.set(-2, cf + 1, 13, Blocks.DARK_OAK_SLAB.getDefaultState().with(Properties.SLAB_TYPE, SlabType.TOP).with(Properties.WATERLOGGED, cf + 1 <= depth));
        b.set(-2, cf + 2, 13, ModBlocks.MAP_BLOCK.getDefaultState());
        b.set(2, cf + 1, 13, Blocks.LECTERN.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.WEST));
        b.set(3, cf + 1, 15, Blocks.BARREL.getDefaultState());
        b.set(-3, cf + 1, 15, Blocks.SKELETON_SKULL.getDefaultState());
        b.set(0, top - 1, 13, Blocks.LANTERN.getDefaultState().with(Properties.HANGING, true).with(Properties.WATERLOGGED, top - 1 <= depth));
        b.set(0, cf + 1, 12, cf + 1 <= depth
                ? Blocks.SEA_PICKLE.getDefaultState().with(Properties.PICKLES, 4).with(Properties.WATERLOGGED, true)
                : Blocks.CANDLE.getDefaultState().with(Properties.CANDLES, 3).with(Properties.LIT, true));

        // bowsprit
        for (int z = -HALF_LEN - 1; z >= -HALF_LEN - 4; z--) b.set(0, UPPER + 2 + (-HALF_LEN - 1 - z) / 2, z,
                Blocks.SPRUCE_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.Z));
    }
}
