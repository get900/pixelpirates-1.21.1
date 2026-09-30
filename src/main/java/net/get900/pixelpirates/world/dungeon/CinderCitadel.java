package net.get900.pixelpirates.world.dungeon;

import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.block.custom.QuenchValveBlock;
import net.get900.pixelpirates.entity.custom.CastawayEntity;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.enums.BlockHalf;
import net.minecraft.block.enums.SlabType;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

/**
 * THE CINDER CITADEL - Molten Warlord's fortress (boss 3/10), rebuilt 2026-09-29. Gate faces -z, ground y 0.
 * The way in is four DYNAMITE doors (blast rubble), each section stocking enough powder for the next:
 * <pre>
 *   z-21  GATEHOUSE ........ DOOR 1 (x-1..1)
 *   z-16..-9  forge yard: lava channel + bridge, forges, fire-pirate spawner, cache (dynamite)
 *   z-8  cross wall + KEEP front ..... DOOR 2       keep x-11..11, z-8..8: great hall, wraith spawner, cache
 *   z 9..19  back yard: obsidian golem, caged castaway, FOUNDRY TOWER (x-4..4, z11..19) ..... DOOR 3 (z11)
 *   foundry: half-slab spiral stair round a pillar, down to y-25 ..... DOOR 4 (z11..13) into
 *   THE CRUCIBLE: underground arena, centre (0,-3), radius 14, floor y-26 (stand y-25), 14 tall;
 *                 4 Quench Valves on copper pipes, 4 pillars, lava pits, the Warlord's throne + hoard (north)
 * </pre>
 */
public final class CinderCitadel {
    private CinderCitadel() {}

    private static final String P = "pixelpirates:";
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private static final DungeonBuilder.Weathered WALL = new DungeonBuilder.Weathered(Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(),
            Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Blocks.BASALT.getDefaultState(), 0.35f);
    private static final DungeonBuilder.Weathered BASE = new DungeonBuilder.Weathered(Blocks.BLACKSTONE.getDefaultState(),
            Blocks.BASALT.getDefaultState(), Blocks.MAGMA_BLOCK.getDefaultState(), 0.3f);
    private static final DungeonBuilder.Weathered FLOOR = new DungeonBuilder.Weathered(Blocks.POLISHED_BLACKSTONE.getDefaultState(),
            Blocks.BLACKSTONE.getDefaultState(), Blocks.MAGMA_BLOCK.getDefaultState(), 0.22f);
    private static final DungeonBuilder.Weathered NETHER = new DungeonBuilder.Weathered(Blocks.NETHER_BRICKS.getDefaultState(),
            Blocks.CRACKED_NETHER_BRICKS.getDefaultState(), Blocks.RED_NETHER_BRICKS.getDefaultState(), 0.3f);

    static final int ACY = -26, ACZ = -3, AR = 14;          // arena floor y, centre z, radius
    static final int TX = 0, TZ = 15;                         // foundry tower / stair centre

    public static void build(DungeonBuilder b) {
        Random r = b.random;
        site(b, r);
        outerWalls(b, r);
        gatehouse(b, r);
        forgeYard(b, r);
        keep(b, r);
        backYard(b, r);
        crucible(b, r);
        foundry(b, r);
    }

    // ------------------------------------------------------------------ helpers
    private static BlockState stairs(Direction up, boolean top) {
        return Blocks.POLISHED_BLACKSTONE_BRICK_STAIRS.getDefaultState().with(Properties.HORIZONTAL_FACING, up)
                .with(Properties.BLOCK_HALF, top ? BlockHalf.TOP : BlockHalf.BOTTOM);
    }

    private static BlockState slab(boolean top) {
        return Blocks.POLISHED_BLACKSTONE_BRICK_SLAB.getDefaultState().with(Properties.SLAB_TYPE, top ? SlabType.TOP : SlabType.BOTTOM);
    }

    private static BlockState lantern(boolean hanging) {
        return Blocks.LANTERN.getDefaultState().with(Properties.HANGING, hanging);
    }

    private static void foundation(DungeonBuilder b, int x, int z, Random r) {
        for (int y = -1; y >= -10; y--) {
            BlockState s = b.get(x, y, z);
            if (!(s.isAir() || s.isReplaceable() || !s.getFluidState().isEmpty() || s.isIn(BlockTags.LEAVES) || s.isIn(BlockTags.LOGS))) return;
            b.set(x, y, z, BASE.pick(r));
        }
    }

    /** A sealed doorway: blast rubble filling x1..x2 at z, from y1 up h blocks (DynamiteEntity clears it). */
    private static void rubbleDoor(DungeonBuilder b, int x1, int x2, int y1, int h, int z) {
        for (int x = x1; x <= x2; x++) for (int y = y1; y < y1 + h; y++) b.set(x, y, z, ModBlocks.BLAST_RUBBLE.getDefaultState());
    }

    private static void brazier(DungeonBuilder b, int x, int y, int z) {
        b.set(x, y, z, Blocks.NETHERRACK.getDefaultState());
        b.set(x, y + 1, z, Blocks.FIRE.getDefaultState());
    }

    private static void castawayCage(DungeonBuilder b, int x, int z) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            for (int y = 1; y <= 2; y++) b.set(x + dx, y, z + dz, Blocks.IRON_BARS.getDefaultState()
                    .with(Properties.NORTH, dx == 0 || dz == 1).with(Properties.SOUTH, dx == 0 || dz == -1)
                    .with(Properties.EAST, dz == 0 || dx == -1).with(Properties.WEST, dz == 0 || dx == 1));
            b.set(x + dx, 3, z + dz, slab(false));
        }
        b.set(x, 3, z, slab(false));
        b.spawnMob(P + "castaway", x, 1, z, e -> { if (e instanceof CastawayEntity c) c.setCaptive(true); });
    }

    // ------------------------------------------------------------------ site
    private static void site(DungeonBuilder b, Random r) {
        for (int x = -22; x <= 22; x++)
            for (int z = -22; z <= 22; z++) {
                boolean inside = Math.max(Math.abs(x), Math.abs(z)) <= 19;
                b.set(x, 0, z, inside ? FLOOR.pick(r) : (r.nextInt(3) == 0 ? ModBlocks.SCORCHED_SAND.getDefaultState() : Blocks.BASALT.getDefaultState()));
                foundation(b, x, z, r);
                for (int y = 1; y <= 26; y++) if (!b.get(x, y, z).isAir()) b.set(x, y, z, AIR);
            }
        // the approach: a basalt causeway lined with braziers
        for (int z = -22; z <= -21; z++) for (int x = -2; x <= 2; x++) b.set(x, 0, z, Blocks.POLISHED_BASALT.getDefaultState());
    }

    // ------------------------------------------------------------------ outer ring
    private static void outerWalls(DungeonBuilder b, Random r) {
        for (int x = -21; x <= 21; x++)
            for (int z = -21; z <= 21; z++) {
                int k = Math.max(Math.abs(x), Math.abs(z));
                if (k < 20) continue;
                for (int y = 1; y <= 10; y++) b.set(x, y, z, y <= 2 ? BASE.pick(r) : WALL.pick(r));
                if (k == 21) {
                    b.set(x, 11, z, WALL.pick(r));
                    if (((x + z) & 1) == 0) b.set(x, 12, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState());
                }
                if (k == 20 && y12Magma(x, z)) b.set(x, 10, z, Blocks.MAGMA_BLOCK.getDefaultState());
            }
        for (int sx = -1; sx <= 1; sx += 2)
            for (int sz = -1; sz <= 1; sz += 2) {
                int cx = sx * 18, cz = sz * 18;
                for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
                    double d = Math.sqrt(x * x + z * z);
                    if (d > 3.8) continue;
                    foundation(b, cx + x, cz + z, r);
                    for (int y = 1; y <= 15; y++) b.set(cx + x, y, cz + z, d > 2.8 ? (y % 5 == 0 ? NETHER.pick(r) : WALL.pick(r)) : AIR);
                    b.set(cx + x, 15, cz + z, d > 2.8 ? WALL.pick(r) : Blocks.MAGMA_BLOCK.getDefaultState());
                    if (d > 2.8 && ((x + z) & 1) == 0) b.set(cx + x, 16, cz + z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState());
                }
                brazier(b, cx, 15, cz);
                b.set(cx, 16, cz, Blocks.FIRE.getDefaultState());
                for (int y = 4; y <= 12; y += 4) b.set(cx - sx * 3, y, cz, AIR);                    // arrow slits
            }
    }

    private static boolean y12Magma(int x, int z) { return ((x * 7 + z * 3) & 7) == 0; }

    // ------------------------------------------------------------------ gatehouse + DOOR 1
    private static void gatehouse(DungeonBuilder b, Random r) {
        for (int x = -6; x <= 6; x++) for (int z = -21; z <= -16; z++) {
            foundation(b, x, z, r);
            for (int y = 1; y <= 14; y++) {
                boolean shell = Math.abs(x) == 6 || z == -21 || z == -16 || y == 14;
                b.set(x, y, z, shell ? (y % 6 == 0 ? NETHER.pick(r) : WALL.pick(r)) : AIR);
            }
            if ((Math.abs(x) == 6 || z == -21 || z == -16) && ((x + z) & 1) == 0) b.set(x, 15, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState());
        }
        // passage x-1..1 through the gatehouse, sealed at the outer face
        for (int z = -21; z <= -16; z++) for (int y = 1; y <= 4; y++) for (int x = -1; x <= 1; x++) b.set(x, y, z, AIR);
        for (int z = -20; z <= -17; z++) for (int y = 1; y <= 4; y++) { b.set(-2, y, z, WALL.pick(r)); b.set(2, y, z, WALL.pick(r)); }
        for (int x = -1; x <= 1; x++) b.set(x, 5, -21, stairs(Direction.SOUTH, true));
        rubbleDoor(b, -1, 1, 1, 4, -21);                                                 // DOOR 1
        // gate trim: a gilded brow over the door, horns on the roof, braziers and red banners outside
        for (int x = -3; x <= 3; x++) b.set(x, 6, -21, Blocks.GILDED_BLACKSTONE.getDefaultState());
        for (int sx = -1; sx <= 1; sx += 2) {
            for (int i = 0; i < 4; i++) b.set(sx * (4 + i), 15 + i, -21, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
            b.set(sx * 4, 3, -22, Blocks.RED_WALL_BANNER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
            brazier(b, sx * 3, 1, -22);
        }
        b.set(0, 13, -18, lantern(true));
    }

    // ------------------------------------------------------------------ forge yard (between gate and keep)
    private static void forgeYard(DungeonBuilder b, Random r) {
        // lava channel across the yard, spanned by a bridge in front of the gate
        for (int x = -19; x <= 19; x++)
            for (int z = -13; z <= -12; z++) {
                if (Math.abs(x) <= 1) { b.set(x, 0, z, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState()); continue; }
                b.set(x, 0, z, Blocks.LAVA.getDefaultState());
                b.set(x, -1, z, Blocks.BASALT.getDefaultState());
            }
        for (int sx = -1; sx <= 1; sx += 2) for (int z = -14; z <= -11; z += 3) b.set(sx * 2, 1, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState());
        // forges: blast furnaces, anvils, a quenching trough (empty - the smiths used lava), weapon racks
        for (int sx = -1; sx <= 1; sx += 2) {
            int x = sx * 12;
            b.set(x, 1, -15, Blocks.BLAST_FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH).with(Properties.LIT, true));
            b.set(x + sx, 1, -15, Blocks.BLAST_FURNACE.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.SOUTH).with(Properties.LIT, true));
            b.set(x - sx, 1, -14, Blocks.ANVIL.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.EAST));
            b.set(x, 1, -10, Blocks.SMITHING_TABLE.getDefaultState());
            for (int y = 1; y <= 3; y++) b.set(sx * 17, y, -15, Blocks.CHAIN.getDefaultState());
            b.set(sx * 17, 4, -15, lantern(false));
            for (int dz = 0; dz < 3; dz++) b.set(sx * 19, 1, -15 + dz, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState());
        }
        b.set(-9, 1, -10, Blocks.CAULDRON.getDefaultState());
        b.chest(8, 1, -10, Direction.WEST, "chests/citadel_outer");
        b.barrelLoot(-17, 1, -10, "chests/citadel_outer");
        b.spawner(14, 1, -10, net.minecraft.registry.Registries.ENTITY_TYPE.get(net.get900.pixelpirates.PixelPirates.id("fire_pirate")));
        for (int[] g : new int[][]{{-6, -14}, {6, -14}, {0, -10}}) b.spawnMob(P + "fire_pirate", g[0], 1, g[1]);
    }

    // ------------------------------------------------------------------ keep + cross wall + DOOR 2
    private static void keep(DungeonBuilder b, Random r) {
        // cross wall splits the yards - the keep is the only way through
        for (int x = -19; x <= 19; x++) for (int y = 1; y <= 10; y++) for (int z = -9; z <= -8; z++) b.set(x, y, z, y <= 2 ? BASE.pick(r) : WALL.pick(r));
        for (int x = -19; x <= 19; x += 2) b.set(x, 11, -9, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState());
        // the keep
        for (int x = -11; x <= 11; x++) for (int z = -8; z <= 8; z++) {
            foundation(b, x, z, r);
            for (int y = 0; y <= 17; y++) {
                boolean shell = Math.abs(x) == 11 || Math.abs(z) == 8 || y == 0 || y == 17;
                BlockState s = shell ? (y % 6 == 3 ? NETHER.pick(r) : WALL.pick(r)) : AIR;
                if (y == 0 && !shell) s = FLOOR.pick(r);
                if (y == 0) s = Math.abs(x) <= 1 ? Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState() : FLOOR.pick(r);
                b.set(x, y, z, s);
            }
            if ((Math.abs(x) == 11 || Math.abs(z) == 8) && ((x + z) & 1) == 0) b.set(x, 18, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState());
        }
        for (int[] c : new int[][]{{-11, -8}, {11, -8}, {-11, 8}, {11, 8}}) {
            for (int y = 1; y <= 20; y++) b.set(c[0], y, c[1], Blocks.CHISELED_POLISHED_BLACKSTONE.getDefaultState());
            brazier(b, c[0], 20, c[1]);
        }
        // front: DOOR 2 under a gilded arch, braziers, banners
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 4; y++) { b.set(x, y, -8, AIR); b.set(x, y, -9, AIR); }
        rubbleDoor(b, -1, 1, 1, 4, -9);                                                  // DOOR 2
        for (int x = -2; x <= 2; x++) b.set(x, 5, -10, Blocks.GILDED_BLACKSTONE.getDefaultState());
        for (int sx = -1; sx <= 1; sx += 2) {
            brazier(b, sx * 3, 1, -10);
            b.set(sx * 5, 6, -10, Blocks.RED_WALL_BANNER.getDefaultState().with(Properties.HORIZONTAL_FACING, Direction.NORTH));
        }
        // great hall: lava troughs, statue pillars, chandeliers, the lieutenant's throne, a wraith spawner
        for (int z = -6; z <= 6; z++) for (int sx = -1; sx <= 1; sx += 2) {
            b.set(sx * 8, 0, z, Blocks.LAVA.getDefaultState());
            b.set(sx * 8, -1, z, Blocks.BASALT.getDefaultState());
        }
        for (int z = -5; z <= 5; z += 5)
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int y = 1; y <= 12; y++) b.set(sx * 5, y, z, y % 4 == 0 ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.POLISHED_BASALT.getDefaultState());
                b.set(sx * 10, 3, z, Blocks.WITHER_SKELETON_WALL_SKULL.getDefaultState().with(Properties.HORIZONTAL_FACING, sx > 0 ? Direction.WEST : Direction.EAST));
            }
        for (int z = -4; z <= 4; z += 4) { b.set(0, 12, z, Blocks.CHAIN.getDefaultState()); b.set(0, 11, z, Blocks.CHAIN.getDefaultState()); b.set(0, 10, z, lantern(true)); }
        b.set(0, 1, 6, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
        b.set(0, 2, 6, stairs(Direction.SOUTH, false));
        b.set(-1, 2, 6, Blocks.GILDED_BLACKSTONE.getDefaultState());
        b.set(1, 2, 6, Blocks.GILDED_BLACKSTONE.getDefaultState());
        b.chest(-9, 1, 6, Direction.EAST, "chests/citadel_hall");
        b.chest(9, 1, -6, Direction.WEST, "chests/phase3_common");
        b.spawner(0, 1, 3, net.minecraft.registry.Registries.ENTITY_TYPE.get(net.get900.pixelpirates.PixelPirates.id("ember_wraith")));
        // back door (open) to the rear yard
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++) b.set(x, y, 8, AIR);
        for (int[] g : new int[][]{{-3, -4}, {3, 0}}) b.spawnMob(P + "fire_pirate", g[0], 1, g[1]);
    }

    // ------------------------------------------------------------------ back yard
    private static void backYard(DungeonBuilder b, Random r) {
        for (int[] s : new int[][]{{-15, 12}, {-13, 16}, {15, 13}, {12, 17}}) {                  // slag heaps
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) b.set(s[0] + dx, 1, s[1] + dz, BASE.pick(r));
            b.set(s[0], 2, s[1], Blocks.MAGMA_BLOCK.getDefaultState());
        }
        for (int x = -9; x <= -7; x++) for (int z = 10; z <= 11; z++) b.set(x, 0, z, Blocks.LAVA.getDefaultState());
        castawayCage(b, 9, 11);
        b.spawnMob(P + "obsidian_golem", -6, 1, 16);
    }

    // ------------------------------------------------------------------ foundry tower + spiral + DOOR 3
    private static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};

    private static void foundry(DungeonBuilder b, Random r) {
        // tower shell 9x9 above ground
        for (int x = -4; x <= 4; x++) for (int z = -4; z <= 4; z++) {
            int wx = TX + x, wz = TZ + z;
            foundation(b, wx, wz, r);
            boolean shell = Math.abs(x) == 4 || Math.abs(z) == 4;
            for (int y = 1; y <= 16; y++) b.set(wx, y, wz, shell ? (y % 5 == 0 ? NETHER.pick(r) : WALL.pick(r)) : AIR);
            b.set(wx, 16, wz, shell ? WALL.pick(r) : Blocks.MAGMA_BLOCK.getDefaultState());
            if (shell && ((x + z) & 1) == 0) b.set(wx, 17, wz, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState());
        }
        brazier(b, TX, 16, TZ);
        b.set(TX, 17, TZ, Blocks.FIRE.getDefaultState());
        for (int x = -1; x <= 1; x++) for (int y = 1; y <= 3; y++) b.set(TX + x, y, TZ - 4, AIR);
        rubbleDoor(b, TX - 1, TX + 1, 1, 3, TZ - 4);                                        // DOOR 3
        for (int y = 4; y <= 12; y += 4) for (int sx = -1; sx <= 1; sx += 2) b.set(TX + sx * 3, y, TZ - 3, lantern(false));
        b.chest(TX + 3, 1, TZ + 3, Direction.WEST, "chests/citadel_foundry");
        b.spawnMob(P + "flame_sprite", TX - 2, 3, TZ + 2);

        // shaft: 5x5 walls around a 3x3 well with a central pillar, from the ground down into the Crucible's depth
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            for (int y = ACY; y <= 0; y++) {
                boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2;
                boolean pillar = x == 0 && z == 0;
                b.set(TX + x, y, TZ + z, wall ? (y % 5 == 0 ? Blocks.MAGMA_BLOCK.getDefaultState() : WALL.pick(r))
                        : pillar ? (y % 6 == 0 ? Blocks.SHROOMLIGHT.getDefaultState() : Blocks.POLISHED_BASALT.getDefaultState()) : AIR);
            }
        // half-slab spiral: surface drops 1/2 block per ring cell, from the ground (1) to the arena floor (ACY + 1)
        int cells = (1 - (ACY + 1)) * 2;
        for (int i = 0; i <= cells; i++) {
            int[] c = RING[i % 8];
            int x = TX + c[0], z = TZ + c[1];
            int h2 = 2 - i;                                         // surface height * 2
            int y = Math.floorDiv(h2, 2);
            boolean half = (h2 & 1) != 0;                           // x.5 surface -> bottom slab at floor(x.5)
            if (half) b.set(x, y, z, slab(false));
            else b.set(x, y - 1, z, slab(true));
        }
        // exit: through the shaft wall and the Crucible wall, sealed by DOOR 4
        for (int z = TZ - 2; z >= AR + ACZ - 1; z--) for (int x = -1; x <= 1; x++) {
            for (int y = ACY + 1; y <= ACY + 3; y++) b.set(x, y, z, AIR);
            b.set(x, ACY, z, FLOOR.pick(r));
        }
        rubbleDoor(b, -1, 1, ACY + 1, 3, AR + ACZ);                                          // DOOR 4
    }

    // ------------------------------------------------------------------ the Crucible
    private static void crucible(DungeonBuilder b, Random r) {
        int top = ACY + 15;
        for (int x = -AR - 2; x <= AR + 2; x++)
            for (int z = ACZ - AR - 2; z <= ACZ + AR + 2; z++) {
                double d = Math.sqrt(x * x + (z - ACZ) * (z - ACZ));
                if (d > AR + 1.9) continue;
                for (int y = ACY - 1; y <= top + 1; y++) {
                    boolean shell = d > AR || y == ACY - 1 || y == ACY || y >= top;
                    BlockState s = !shell ? AIR : y == ACY ? FLOOR.pick(r) : (y % 5 == 0 ? Blocks.MAGMA_BLOCK.getDefaultState() : WALL.pick(r));
                    b.set(x, y, z, s);
                }
                if (d > AR - 1 && d <= AR) b.set(x, ACY + 1, z, Blocks.POLISHED_BLACKSTONE_BRICK_WALL.getDefaultState());   // wall skirting
            }
        // four pillars (cover from the mace) with magma veins
        for (int[] p : new int[][]{{-7, ACZ - 7}, {7, ACZ - 7}, {-7, ACZ + 6}, {7, ACZ + 6}})
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++)
                for (int y = ACY + 1; y < top; y++) b.set(p[0] + dx, y, p[1] + dz, (y + dx + dz) % 5 == 0 ? Blocks.MAGMA_BLOCK.getDefaultState() : Blocks.POLISHED_BASALT.getDefaultState());
        // lava pits
        for (int[] p : new int[][]{{-10, ACZ}, {10, ACZ}, {0, ACZ + 8}})
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                b.set(p[0] + dx, ACY, p[1] + dz, Blocks.LAVA.getDefaultState());
                b.set(p[0] + dx, ACY - 1, p[1] + dz, Blocks.BASALT.getDefaultState());
            }
        // hanging chandeliers
        for (int[] c : new int[][]{{0, ACZ}, {-5, ACZ - 3}, {5, ACZ + 3}}) {
            for (int y = top - 1; y >= top - 3; y--) b.set(c[0], y, c[1], Blocks.CHAIN.getDefaultState());
            b.set(c[0], top - 4, c[1], Blocks.SHROOMLIGHT.getDefaultState());
        }
        // THE QUENCH VALVES: valve at eye level, a copper pipe up to the ceiling, a grate spout above each cascade point
        int[][] valves = {{-AR, ACZ, 0}, {AR, ACZ, 1}, {-8, ACZ - 11, 2}, {8, ACZ + 11, 3}};
        Direction[] faces = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
        for (int[] v : valves) {
            Direction f = faces[v[2]];
            int vx = v[0], vz = v[1], vy = ACY + 3;
            for (int y = ACY + 1; y < top; y++) b.set(vx, y, vz, WALL.pick(r));
            b.set(vx, vy, vz, ModBlocks.QUENCH_VALVE.getDefaultState().with(QuenchValveBlock.FACING, f));
            for (int y = vy + 1; y < top; y++) b.set(vx, y, vz, Blocks.OXIDIZED_CUT_COPPER.getDefaultState());
            for (int k = 1; k <= 3; k++) b.set(vx + f.getOffsetX() * k, top - 1, vz + f.getOffsetZ() * k, Blocks.OXIDIZED_CUT_COPPER.getDefaultState());
            b.set(vx + f.getOffsetX() * 3, top - 2, vz + f.getOffsetZ() * 3, Blocks.IRON_BARS.getDefaultState());
            // a cool blue ring on the floor marks where the water lands
            int cx = vx + f.getOffsetX() * 3, cz = vz + f.getOffsetZ() * 3;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
                b.set(cx + dx, ACY, cz + dz, (dx == 0 && dz == 0) ? Blocks.WARPED_WART_BLOCK.getDefaultState() : Blocks.DARK_PRISMARINE.getDefaultState());
        }
        // the Warlord's throne on a dais at the north end, the hoard beside it
        for (int x = -3; x <= 3; x++) for (int z = ACZ - 13; z <= ACZ - 10; z++) b.set(x, ACY + 1, z, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
        for (int x = -3; x <= 3; x++) b.set(x, ACY + 1, ACZ - 9, stairs(Direction.NORTH, false));
        b.set(0, ACY + 2, ACZ - 12, Blocks.GILDED_BLACKSTONE.getDefaultState());
        b.set(0, ACY + 3, ACZ - 12, stairs(Direction.NORTH, false));
        for (int y = ACY + 2; y <= ACY + 6; y++) { b.set(-1, y, ACZ - 13, Blocks.GILDED_BLACKSTONE.getDefaultState()); b.set(1, y, ACZ - 13, Blocks.GILDED_BLACKSTONE.getDefaultState()); }
        b.set(0, ACY + 6, ACZ - 13, Blocks.GILDED_BLACKSTONE.getDefaultState());
        b.set(0, ACY + 7, ACZ - 13, Blocks.WITHER_SKELETON_SKULL.getDefaultState());
        b.chest(-3, ACY + 2, ACZ - 12, Direction.EAST, "chests/citadel_hoard");
        b.chest(3, ACY + 2, ACZ - 12, Direction.WEST, "chests/phase3_treasure");
        b.spawnMob(P + "molten_warlord", 0, ACY + 1, ACZ - 2);
    }
}
