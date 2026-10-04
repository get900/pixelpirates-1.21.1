package net.get900.pixelpirates.world.gen;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fence / wall / glass pane / iron bar connections worked out from the neighbouring blocks, the way the game does when a
 * player places one (FenceBlock, WallBlock, PaneBlock in 1.20.1). The island is written straight into the chunk with no
 * neighbour updates, so a fence written as plain "minecraft:spruce_fence" stays a lone post next to its neighbours -
 * PortCityLayout runs {@link #connect} over every such cell at the end of the build, and LayoutCheck reports CONNECT for
 * any cell that still differs. Pure Java (no Minecraft classes) so the checker can run it outside the game.
 *
 * "Full side" is a name-based guess at Block.isSideSolidFullSquare: good for the stone/wood/terracotta the island is
 * built from; a block it gets wrong would only show as a missing/extra arm, and the game fixes that cell on its next update.
 */
final class Connections {
    private Connections() {}

    /** The block at a position as a state string ("minecraft:air" for nothing). */
    interface Lookup { String at(int x, int y, int z); }

    enum Kind { NONE, FENCE, NETHER_FENCE, WALL, PANE }

    static final String[] SIDES = {"north", "east", "south", "west"};
    private static final int[][] D = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}};

    static String base(String desc) { int b = desc.indexOf('['); return b < 0 ? desc : desc.substring(0, b); }

    static Map<String, String> props(String desc) {
        Map<String, String> m = new LinkedHashMap<>();
        int b = desc.indexOf('[');
        if (b < 0) return m;
        for (String kv : desc.substring(b + 1, desc.length() - 1).split(",")) {
            String[] p = kv.split("=");
            if (p.length == 2) m.put(p[0].trim(), p[1].trim());
        }
        return m;
    }

    static Kind kind(String desc) {
        String b = base(desc);
        if (b.equals("minecraft:nether_brick_fence")) return Kind.NETHER_FENCE;
        if (b.endsWith("_fence")) return Kind.FENCE;
        if (b.endsWith("_wall") && b.startsWith("minecraft:") && !b.contains("wall_")) return Kind.WALL;   // not wall_torch, wall_sign...
        if (b.endsWith("_pane") || b.equals("minecraft:iron_bars")) return Kind.PANE;
        return Kind.NONE;
    }

    /** The state with its connections recomputed, or the same string when it is not a connecting block. */
    static String connect(String desc, int x, int y, int z, Lookup l) {
        Kind k = kind(desc);
        if (k == Kind.NONE) return desc;
        Map<String, String> old = props(desc);
        boolean[] c = new boolean[4];
        for (int s = 0; s < 4; s++) {
            String n = l.at(x + D[s][0], y, z + D[s][2]);
            c[s] = connects(k, desc, n, s);
        }
        StringBuilder sb = new StringBuilder(base(desc)).append('[');
        if (k == Kind.WALL) {
            String above = l.at(x, y + 1, z);
            String[] shape = new String[4];
            for (int s = 0; s < 4; s++) shape[s] = !c[s] ? "none" : tallOver(above, s) ? "tall" : "low";
            sb.append("east=").append(shape[1]).append(",north=").append(shape[0]).append(",south=").append(shape[2])
              .append(",up=").append(wallPost(shape, above)).append(",west=").append(shape[3]);
        } else {
            sb.append("east=").append(c[1]).append(",north=").append(c[0]).append(",south=").append(c[2]).append(",west=").append(c[3]);
        }
        sb.append(",waterlogged=").append(old.getOrDefault("waterlogged", "false")).append(']');
        return sb.toString();
    }

    /** Does a block of kind k connect to neighbour n on side s (n is the block on that side)? */
    static boolean connects(Kind k, String self, String n, int s) {
        String nb = base(n);
        Kind nk = kind(n);
        boolean full = !cannotConnect(nb) && fullSide(n, opposite(s));
        boolean gate = nb.endsWith("_fence_gate") && gateFacesAcross(n, s);
        return switch (k) {
            case FENCE -> full || nk == Kind.FENCE || gate;
            case NETHER_FENCE -> full || nk == Kind.NETHER_FENCE || gate;
            case PANE -> full || nk == Kind.PANE || nk == Kind.WALL;
            case WALL -> full || nk == Kind.WALL || nk == Kind.PANE || gate;
            default -> false;
        };
    }

    /** A fence gate joins a fence/wall on side s when the gate faces across that line (its facing axis is not s's axis). */
    static boolean gateFacesAcross(String gate, int s) {
        String f = props(gate).getOrDefault("facing", "north");
        boolean gateAlongZ = f.equals("north") || f.equals("south");
        boolean sideAlongZ = s == 0 || s == 2;
        return gateAlongZ != sideAlongZ;
    }

    static int opposite(int s) { return (s + 2) % 4; }

    /** FenceBlock/PaneBlock.cannotConnect: leaves, barriers, pumpkins, melons, shulker boxes. */
    static boolean cannotConnect(String b) {
        return b.endsWith("_leaves") || b.equals("minecraft:barrier") || b.endsWith("pumpkin") || b.equals("minecraft:jack_o_lantern")
                || b.equals("minecraft:melon") || b.endsWith("shulker_box");
    }

    /** Not full-sided, by exact id (without "minecraft:")... */
    private static final java.util.Set<String> THIN_EXACT = java.util.Set.of("air", "cave_air", "water", "lava", "iron_bars", "lantern",
            "soul_lantern", "chain", "torch", "soul_torch", "redstone_torch", "ladder", "rail", "candle", "cake", "lectern", "bell",
            "brewing_stand", "hopper", "grindstone", "scaffolding", "lily_pad", "vine", "end_rod", "lightning_rod", "azalea",
            "flowering_azalea", "cactus", "bamboo", "sugar_cane", "sea_pickle", "snow", "short_grass", "grass", "tall_grass", "fern",
            "large_fern", "kelp", "kelp_plant", "seagrass", "tall_seagrass", "small_dripleaf", "big_dripleaf", "big_dripleaf_stem",
            "amethyst_cluster", "pointed_dripstone", "brown_mushroom", "red_mushroom", "dead_bush", "poppy", "dandelion",
            "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "rose_bush", "peony", "lilac",
            "sunflower", "spore_blossom", "cave_vines", "cave_vines_plant", "glow_lichen", "cobweb", "tripwire", "lever",
            "repeater", "comparator", "redstone_wire", "daylight_detector", "enchanting_table", "stonecutter", "conduit",
            "turtle_egg", "frogspawn", "sculk_vein", "sweet_berry_bush", "wheat", "carrots", "potatoes", "beetroots",
            "pumpkin_stem", "melon_stem", "attached_pumpkin_stem", "attached_melon_stem", "mangrove_propagule", "hanging_roots",
            "pink_petals", "fire", "soul_fire", "campfire", "soul_campfire", "anvil", "chipped_anvil", "damaged_anvil", "chest",
            "trapped_chest", "ender_chest", "flower_pot", "torchflower", "pitcher_plant", "decorated_pot", "dragon_egg");
    /** ...and by fragment (only ones no full block's id contains). */
    private static final String[] THIN_PART = {"_fence", "_wall", "_pane", "_door", "_trapdoor", "_sign", "_banner", "_carpet",
            "potted_", "_button", "_pressure_plate", "_candle", "_head", "_skull", "_bed", "_sapling", "_coral_fan", "_tulip", "_bud",
            "candle_cake", "_crop"};

    /** Is the face of block n on side `face` (0 N, 1 E, 2 S, 3 W) a full solid square? */
    static boolean fullSide(String n, int face) {
        String b = base(n);
        if (b.startsWith("pixelpirates:")) {                       // our blocks: only the plain building blocks are full
            String m = b.substring(13);
            return (m.endsWith("planks") || m.endsWith("_log") || m.endsWith("_wood") || m.endsWith("bricks") || m.endsWith("_stone")
                    || m.endsWith("_tiles") || m.endsWith("_sail_canvas") || m.equals("shell_block") || m.equals("pearl_block"))
                    && !m.contains("slab") && !m.contains("stairs");
        }
        if (b.endsWith("_slab")) return "double".equals(props(n).get("type"));
        if (b.endsWith("_stairs")) return SIDES[face].equals(props(n).getOrDefault("facing", "north"));   // the tall back only
        if (b.endsWith("_trapdoor"))                                // an open trapdoor is a full plate on the side opposite its facing
            return "true".equals(props(n).get("open")) && SIDES[opposite(face)].equals(props(n).getOrDefault("facing", "north"));
        String m = b.startsWith("minecraft:") ? b.substring(10) : b;
        if (THIN_EXACT.contains(m) || (m.endsWith("_coral") && !m.endsWith("_block"))) return false;
        for (String t : THIN_PART) if (m.contains(t)) return false;
        return true;
    }

    /** The block above covers this side's half of the top (a full block, a bottom slab, a wall/fence/pane reaching that side). */
    static boolean tallOver(String above, int s) {
        String b = base(above);
        Kind k = kind(above);
        if (k == Kind.WALL) return !"none".equals(props(above).getOrDefault(SIDES[s], "none"));
        if (k == Kind.FENCE || k == Kind.NETHER_FENCE || k == Kind.PANE) return "true".equals(props(above).get(SIDES[s]));
        return bottomFull(above, b);
    }

    private static boolean bottomFull(String desc, String b) {
        if (b.endsWith("_slab")) return !"top".equals(props(desc).get("type"));
        if (b.endsWith("_stairs")) return !"top".equals(props(desc).get("half"));
        if (b.endsWith("_carpet")) return true;
        return fullSide(desc, 0) && !b.endsWith("_trapdoor");
    }

    /** WallBlock.shouldHavePost: a post unless it is a straight run with nothing on top that needs one. */
    static boolean wallPost(String[] shape, String above) {
        if (kind(above) == Kind.WALL && "true".equals(props(above).get("up"))) return true;
        boolean n = shape[0].equals("none"), e = shape[1].equals("none"), s = shape[2].equals("none"), w = shape[3].equals("none");
        if ((n && s && e && w) || n != s || e != w) return true;               // alone, an end, a corner or a junction
        if ((shape[0].equals("tall") && shape[2].equals("tall")) || (shape[1].equals("tall") && shape[3].equals("tall"))) return false;
        String b = base(above);
        return b.contains("torch") || b.endsWith("_sign") || b.endsWith("_banner") || b.endsWith("pressure_plate")
                || b.contains("lantern") || b.endsWith("_fence") || b.contains("candle") || b.endsWith("flower_pot")
                || b.contains("potted_") || bottomFull(above, b);
    }
}
