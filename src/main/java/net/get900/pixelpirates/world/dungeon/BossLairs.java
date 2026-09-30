package net.get900.pixelpirates.world.dungeon;

import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.block.enums.WallMountLocation;
import net.minecraft.entity.EntityType;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.util.function.IntFunction;

/**
 * Builders for the dungeons in {@link Dungeons} (Phase 1 grotto/shrine/galleon live in their own
 * classes). Design space: y = 0 is the ground / seabed block, +z "forward", every block within
 * |x|,|z| <= 22 (DungeonBuilder.MAX_REACH). For seabed sites {@code depth} is the water column
 * above y=0, so interiors are flooded up to the real waterline via {@link #fluid}.
 * Bosses are spawned persistent and adopt their spawn spot as their arena (ModBoss).
 */
public final class BossLairs {
    private BossLairs() {}

    private static final BlockState AIR = Blocks.AIR.getDefaultState();

    private static IntFunction<BlockState> fluid(int depth) {
        return y -> y <= depth ? DungeonBuilder.water() : AIR;
    }

    private static DungeonBuilder.Weathered w(BlockState a, BlockState b, BlockState c, float chance) {
        return new DungeonBuilder.Weathered(a, b, c, chance);
    }

    private static BlockState lantern(boolean hanging, boolean wet) {
        return Blocks.LANTERN.getDefaultState().with(Properties.HANGING, hanging).with(Properties.WATERLOGGED, wet);
    }

    private static BlockState soulLantern(boolean hanging) {
        return Blocks.SOUL_LANTERN.getDefaultState().with(Properties.HANGING, hanging);
    }

    private static final String P = "pixelpirates:";

    // =====================================================================================
    // PHASE 1
    // =====================================================================================

    /** Captain Rackham's Hold - see {@link RackhamFort} (boss 1/10, overhauled 2026-09-29). */
    public static void rackhamFort(DungeonBuilder b, int depth) {
        RackhamFort.build(b);
    }

    /** The Whalers' Grave - lair of the Bloodfin (rebuilt 2026-09-29), see {@link WhalersGrave}. */
    public static void bloodfinReef(DungeonBuilder b, int depth) {
        WhalersGrave.build(b, depth);
    }

    // =====================================================================================
    // PHASE 2
    // =====================================================================================

    /** The Kraken's Maw (enlarged 2026-09-29) - see {@link KrakensMaw}. */
    public static void krakenMaw(DungeonBuilder b, int depth) {
        KrakensMaw.build(b, depth);
    }

    /** The Serpent's Hollow - see {@link SerpentHollow} (boss 2/10, overhauled 2026-09-29). */
    public static void serpentTrench(DungeonBuilder b, int depth) {
        SerpentHollow.build(b, depth);
    }

    /** Sunken Coral Temple: a flooded prismarine shrine overgrown with coral; void squid guard it. */
    public static void coralTemple(DungeonBuilder b, int depth) {
        Random r = b.random;
        DungeonBuilder.Weathered pb = w(Blocks.PRISMARINE_BRICKS.getDefaultState(), Blocks.PRISMARINE.getDefaultState(), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), 0.4f);
        IntFunction<BlockState> f = fluid(depth);
        b.fill(-9, 0, -9, 9, 0, 9, Blocks.PRISMARINE_BRICKS.getDefaultState());
        b.room(-6, 0, -6, 6, 7, 6, pb, f.apply(3));
        for (int y = 1; y <= 6; y++) for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) b.set(x, y, z, f.apply(y));
        b.fill(-1, 1, -6, 1, 3, -6, f.apply(2));                                        // doorway
        for (int x = -6; x <= 6; x += 3) if (r.nextBoolean()) b.set(x, 7, r.nextBoolean() ? -6 : 6, f.apply(7));   // crumbled roof
        for (int[] c : new int[][]{{-8, -8}, {8, -8}, {-8, 8}, {8, 8}, {-8, 0}, {8, 0}}) {
            int h = 3 + r.nextInt(4);
            for (int y = 1; y <= h; y++) b.set(c[0], y, c[1], Blocks.PRISMARINE_BRICKS.getDefaultState());
            b.set(c[0], h + 1, c[1], Blocks.SEA_LANTERN.getDefaultState());
        }
        BlockState[] corals = {Blocks.TUBE_CORAL_BLOCK.getDefaultState(), Blocks.BRAIN_CORAL_BLOCK.getDefaultState(),
                Blocks.BUBBLE_CORAL_BLOCK.getDefaultState(), Blocks.FIRE_CORAL_BLOCK.getDefaultState(), Blocks.HORN_CORAL_BLOCK.getDefaultState()};
        for (int i = 0; i < 26; i++) b.set(r.nextInt(19) - 9, 1 + r.nextInt(2), r.nextInt(19) - 9, corals[r.nextInt(corals.length)]);
        b.fill(-5, 1, -5, 5, 1, 5, f.apply(1));                                          // keep the hall floor clear
        b.set(0, 1, 3, Blocks.DARK_PRISMARINE.getDefaultState());                        // altar
        b.chest(0, 2, 3, Direction.NORTH, "chests/phase2_treasure");
        b.chest(-4, 1, -2, Direction.EAST, "chests/phase2_temple_tideshackle");   // the Tideshackle (Chained Revenant)
        b.chest(4, 1, -2, Direction.WEST, "chests/phase2_common");
        b.set(0, 6, 0, lantern(true, 6 <= depth));
        b.spawner(0, 1, -3, net.minecraft.registry.Registries.ENTITY_TYPE.get(net.get900.pixelpirates.PixelPirates.id("void_squid")));
        b.spawnMob(P + "kraken_tentacle", -3, 1, 4);
        b.spawnMob(P + "kraken_tentacle", 3, 1, 4);
    }

    // =====================================================================================
    // PHASE 3
    // =====================================================================================

    /** The Cinder Citadel - see {@link CinderCitadel} (boss 3/10, overhauled 2026-09-29). */
    public static void cinderForge(DungeonBuilder b, int depth) {
        CinderCitadel.build(b);
    }

    /** Obsidian Vault: a half-buried obsidian cube with lava-in-glass pillars and a golem guardian. */
    public static void obsidianVault(DungeonBuilder b, int depth) {
        Random r = b.random;
        DungeonBuilder.Weathered obs = w(Blocks.OBSIDIAN.getDefaultState(), Blocks.CRYING_OBSIDIAN.getDefaultState(), Blocks.BLACKSTONE.getDefaultState(), 0.25f);
        b.room(-7, -6, -7, 7, 4, 7, obs, AIR);
        for (int z = -9; z <= -7; z++) for (int x = -1; x <= 1; x++) for (int y = -5; y <= 3; y++) b.set(x, y, z, AIR);   // entrance cut
        for (int k = 0; k <= 5; k++) for (int x = -1; x <= 1; x++)
            b.set(x, -k, -9 + k, Blocks.POLISHED_BLACKSTONE_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6; z++) b.set(x, -6, z, (x + z) % 2 == 0 ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.POLISHED_BLACKSTONE.getDefaultState());
        for (int[] p : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            for (int y = -5; y <= 3; y++) {
                b.set(p[0], y, p[1], y % 3 == 0 ? Blocks.LAVA.getDefaultState() : Blocks.OBSIDIAN.getDefaultState());
            }
            b.set(p[0] + 1, -2, p[1], Blocks.ORANGE_STAINED_GLASS.getDefaultState());
        }
        b.chest(-2, -5, 5, Direction.NORTH, "chests/phase3_treasure");
        b.chest(2, -5, 5, Direction.NORTH, "chests/phase3_common");
        b.set(0, -5, 6, ModBlocks.TREASURE_BLOCK.getDefaultState());
        b.set(0, 3, 0, Blocks.SHROOMLIGHT.getDefaultState());
        b.spawner(-5, -5, 0, net.minecraft.registry.Registries.ENTITY_TYPE.get(net.get900.pixelpirates.PixelPirates.id("flame_sprite")));
        b.spawnMob(P + "obsidian_golem", 0, -5, 1);
    }

    /** Fire Pirate Camp: scorched tents around a burnt-out hull, a caged prisoner and plundered loot. */
    public static void fireCamp(DungeonBuilder b, int depth) {
        Random r = b.random;
        for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
            if (x * x + z * z > 150) continue;
            b.set(x, 0, z, r.nextInt(3) == 0 ? ModBlocks.SCORCHED_SAND.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState());
            for (int y = 1; y <= 8; y++) b.set(x, y, z, AIR);
        }
        // burnt hull
        for (int z = -9; z <= 1; z++) {
            int hw = z < -6 ? 1 : 2;
            b.set(-hw - 6, 1, z, Blocks.BLACKSTONE.getDefaultState()); b.set(hw - 6, 1, z, Blocks.BLACKSTONE.getDefaultState());
            if (r.nextBoolean()) b.set(-hw - 6, 2, z, ModBlocks.CHARRED_PLANKS.getDefaultState());
            b.set(-6, 0, z, ModBlocks.CHARRED_PLANKS.getDefaultState());
        }
        for (int y = 1; y <= 5; y++) b.set(-6, y, -3, ModBlocks.CHARRED_LOG.getDefaultState());
        // tents
        for (int[] t : new int[][]{{5, -5}, {6, 4}}) {
            for (int dz = -2; dz <= 2; dz++) {
                b.set(t[0] - 1, 1, t[1] + dz, Blocks.BLACK_WOOL.getDefaultState()); b.set(t[0] + 1, 1, t[1] + dz, Blocks.BLACK_WOOL.getDefaultState());
                b.set(t[0], 2, t[1] + dz, Blocks.RED_WOOL.getDefaultState());
            }
        }
        b.set(0, 1, 0, Blocks.CAMPFIRE.getDefaultState()); b.set(0, 1, 2, Blocks.CAMPFIRE.getDefaultState());
        for (int[] s : new int[][]{{-2, 0}, {2, 0}, {0, -2}}) b.set(s[0], 1, s[1], ModBlocks.CHARRED_LOG.getDefaultState().with(Properties.AXIS, Direction.Axis.X));
        // prisoner cage
        for (int y = 1; y <= 3; y++) for (int[] p : new int[][]{{-2, 7}, {-1, 7}, {0, 7}, {-2, 8}, {0, 8}, {-2, 9}, {-1, 9}, {0, 9}})
            b.set(p[0], y, p[1], Blocks.IRON_BARS.getDefaultState());
        b.set(-1, 4, 8, Blocks.BLACKSTONE_SLAB.getDefaultState());
        b.spawnMob(P + "castaway", -1, 1, 8);
        b.chest(8, 1, 0, Direction.WEST, "chests/phase3_common");
        b.barrelLoot(-9, 1, 5, "chests/phase3_common");
        b.spawner(3, 1, -9, net.minecraft.registry.Registries.ENTITY_TYPE.get(net.get900.pixelpirates.PixelPirates.id("fire_pirate")));
        b.spawnMob(P + "fire_pirate", 3, 1, 2);
        b.spawnMob(P + "fire_pirate", -3, 1, -5);
    }

    // =====================================================================================
    // PHASE 4
    // =====================================================================================

    /** Dutchman's Rest - see {@link DutchmansRest}; the Flying Dutchman is summoned by its bell (boss 4/10). */
    public static void ghostShip(DungeonBuilder b, int depth) {
        DutchmansRest.build(b, depth);
    }

    /** Revenant's Crypt: a mausoleum over a chained ossuary; the Chained Revenant waits below. */
    /** The Chained Revenant's lair is the Gallows Grotto, rendered chunk by chunk by {@link GallowsGrotto} - nothing here. */
    public static void revenantCrypt(DungeonBuilder b, int depth) { }

    /** Drowned Graveyard: gravestones, dead trees, a ruined chapel - and some of the chests bite. */
    public static void drownedGraveyard(DungeonBuilder b, int depth) {
        Random r = b.random;
        for (int x = -11; x <= 11; x++) for (int z = -11; z <= 11; z++) {
            b.set(x, 0, z, r.nextInt(3) == 0 ? Blocks.PODZOL.getDefaultState() : Blocks.COARSE_DIRT.getDefaultState());
            for (int y = 1; y <= 8; y++) b.set(x, y, z, AIR);
            if (Math.abs(x) == 11 || Math.abs(z) == 11) {
                b.set(x, 1, z, (x + z) % 4 == 0 ? Blocks.COBBLESTONE_WALL.getDefaultState() : Blocks.IRON_BARS.getDefaultState());
            }
        }
        b.fill(-1, 1, -11, 1, 1, -11, AIR);                                              // gate
        for (int x = -8; x <= 8; x += 3) for (int z = -7; z <= 3; z += 4) {
            if (r.nextInt(5) == 0) continue;
            b.set(x, 1, z, Blocks.MOSSY_STONE_BRICK_WALL.getDefaultState());
            if (r.nextBoolean()) b.set(x, 2, z, Blocks.STONE_BRICK_WALL.getDefaultState());
            b.set(x, 0, z + 1, Blocks.ROOTED_DIRT.getDefaultState());
        }
        for (int[] t : new int[][]{{-9, 6}, {9, -8}}) {                                   // dead trees
            for (int y = 1; y <= 4; y++) b.set(t[0], y, t[1], Blocks.DARK_OAK_LOG.getDefaultState());
            b.set(t[0] + 1, 4, t[1], Blocks.DARK_OAK_FENCE.getDefaultState()); b.set(t[0] - 1, 5, t[1], Blocks.DARK_OAK_FENCE.getDefaultState());
        }
        // ruined chapel at the back
        DungeonBuilder.Weathered ms = DungeonBuilder.MOSSY_BRICKS;
        for (int x = -4; x <= 4; x++) for (int y = 1; y <= 4; y++) { if (r.nextInt(5) != 0) b.set(x, y, 10, ms.pick(r)); }
        for (int z = 6; z <= 10; z++) for (int y = 1; y <= 3; y++) { if (r.nextInt(4) != 0) { b.set(-4, y, z, ms.pick(r)); b.set(4, y, z, ms.pick(r)); } }
        b.set(0, 1, 9, Blocks.LECTERN.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        b.spawner(0, 1, 7, net.minecraft.registry.Registries.ENTITY_TYPE.get(net.get900.pixelpirates.PixelPirates.id("skeleton_pirate")));
        for (int[] l : new int[][]{{-6, -9}, {6, -9}, {-6, 8}, {6, 8}}) { b.set(l[0], 1, l[1], Blocks.COBBLESTONE_WALL.getDefaultState()); b.set(l[0], 2, l[1], soulLantern(false)); }
        // two real chests... and two mimics that look exactly like them
        b.chest(-6, 1, 1, Direction.SOUTH, "chests/phase4_common");
        b.chest(6, 1, -3, Direction.SOUTH, "chests/phase4_treasure");
        b.spawnMob(P + "mimic", 6, 1, 1);
        b.spawnMob(P + "mimic", -6, 1, -3);
        b.spawnMob(P + "drowned_hands", -2, 1, -2);
        b.spawnMob(P + "drowned_hands", 3, 1, 4);
    }

    // =====================================================================================
    // PHASE 5
    // =====================================================================================

    /** The Sunken Court of the Abyssal King (rebuilt 2026-09-29) - see {@link SunkenCourt}. */
    public static void abyssalThrone(DungeonBuilder b, int depth) {
        SunkenCourt.build(b, depth);
    }

    /** The Abyssal Heart's lair is the Titan's Chest, rendered chunk by chunk by {@link TitansChest} - nothing here. */
    public static void abyssalHeartLair(DungeonBuilder b, int depth) { }

    /** The Leviathan's Rift is one site per world (LeviathanRoute), rendered by LeviathanSites - nothing here. */
    public static void leviathanRift(DungeonBuilder b, int depth) { }

    /** Luminous Grotto: a flooded crystal geode guarded by a Crystal Golem and anemone eyes. */
    public static void luminousGrotto(DungeonBuilder b, int depth) {
        Random r = b.random;
        b.ellipsoid(0, -2, 0, 10, 7, 10, y -> DungeonBuilder.water(),
                w(Blocks.AMETHYST_BLOCK.getDefaultState(), Blocks.CALCITE.getDefaultState(), ModBlocks.LUMINOUS_VEIN.getDefaultState(), 0.45f), 2.0);
        for (int z = -14; z <= -9; z++) for (int x = -1; x <= 1; x++) for (int y = -3; y <= 0; y++) b.set(x, y, z, DungeonBuilder.water());
        for (int i = 0; i < 18; i++) {
            int x = r.nextInt(15) - 7, z = r.nextInt(15) - 7;
            b.set(x, -8, z, r.nextBoolean() ? Blocks.AMETHYST_CLUSTER.getDefaultState().with(Properties.WATERLOGGED, true) : ModBlocks.PEARL_BLOCK.getDefaultState());
        }
        b.chest(0, -8, 6, Direction.NORTH, "chests/phase5_treasure");
        b.chest(5, -8, 0, Direction.WEST, "chests/phase5_common");
        b.spawnMob(P + "crystal_golem", 0, -8, 0);
        b.spawnMob(P + "anemone_eye", -5, -8, 3);
        b.spawnMob(P + "anemone_eye", 4, -8, -4);
    }
}
