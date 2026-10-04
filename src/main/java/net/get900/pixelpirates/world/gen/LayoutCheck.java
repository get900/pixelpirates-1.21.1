package net.get900.pixelpirates.world.gen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * THE BUILD CHECKER for the spawn island (2026-10-02). Pure Java like PortCityLayout - run it standalone, never in the game:
 *
 *   javac PortCityLayout.java SpawnIslandTerrain.java LayoutCheck.java ; java ...LayoutCheck out_dir
 *
 * Writes out_dir/report.txt (every issue, grouped by building, with coordinates) and out_dir/issues_map.png (the island
 * from above, an issue = a coloured dot). Checks - see docs/spawn-island.md "BUILD RULES":
 *  FLOAT    a group of placed blocks touching nothing that reaches the ground (water counts as ground)
 *  SUPPORT  a block that needs a block behind/above/below it has air there (wall banners, signs, boards, ladders,
 *           buttons, hooks, hanging + standing lanterns, carpets, plants, rails, candles, chandeliers, bells...)
 *  HALF     a door, bed or tall plant missing its other half
 *  DOORWAY  a door whose front or back is walled in
 *  CONNECT  a fence / wall / glass pane / iron bar whose arms don't match its neighbours (lone posts - see Connections)
 *  CLASH    a block one building placed that a later writer (another building, or a global pass) replaced
 */
public final class LayoutCheck {
    private LayoutCheck() {}

    record Issue(String kind, int x, int y, int z, String msg) {}

    static List<String> PAL;
    static short[] G;
    static int AIR_ID, WATER_ID;
    static final List<Issue> ISSUES = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "layout_check";
        PortCityLayout.TRACK = true;
        G = PortCityLayout.ensureBuilt();
        PAL = PortCityLayout.palette();
        AIR_ID = PAL.indexOf("minecraft:air") + 1;
        WATER_ID = PAL.indexOf("minecraft:water") + 1;
        floating();
        perBlock();
        connections();
        clashes();
        report(new java.io.File(out));
    }

    // ------------------------------------------------------------------ cells
    static int id(int x, int y, int z) {
        if (x < PortCityLayout.X0 || x > PortCityLayout.X1 || y < PortCityLayout.Y0 || y > PortCityLayout.Y1
                || z < PortCityLayout.Z0 || z > PortCityLayout.Z1) return -1;
        return PortCityLayout.get(x, y, z);
    }

    static String name(int id) { return id <= 0 ? "" : PAL.get(id - 1); }

    static String base(String desc) { int b = desc.indexOf('['); return b < 0 ? desc : desc.substring(0, b); }

    static Map<String, String> props(String desc) {
        Map<String, String> m = new HashMap<>();
        int b = desc.indexOf('[');
        if (b < 0) return m;
        for (String kv : desc.substring(b + 1, desc.length() - 1).split(",")) {
            String[] p = kv.split("=");
            if (p.length == 2) m.put(p[0], p[1]);
        }
        return m;
    }

    /** Untouched terrain (id 0) below the surface is solid ground. */
    static boolean terrainSolid(int x, int y, int z) { return PortCityLayout.terrainSolid(x, y, z); }

    /** Something is there (a placed block, ground or the sea). */
    static boolean present(int x, int y, int z) {
        int v = id(x, y, z);
        if (v < 0) return y < PortCityLayout.Y0;
        if (v == 0) return terrainSolid(x, y, z) || y <= 62;
        return v != AIR_ID;
    }

    /** Thin / walk-through blocks, matched on the id without "minecraft:" (exact names, or the suffixes below). */
    static final Set<String> SOFT_EXACT = Set.of("short_grass", "tall_grass", "grass", "fern", "large_fern", "poppy", "dandelion",
            "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley", "lily_pad", "rose_bush", "peony",
            "lilac", "sunflower", "vine", "snow", "seagrass", "tall_seagrass", "kelp", "kelp_plant", "sea_pickle", "tripwire", "lever",
            "ladder", "lantern", "soul_lantern", "chain", "redstone_wire", "scaffolding", "torch", "wall_torch", "candle", "rail",
            "pixelpirates:hanging_rope", "pixelpirates:hanging_net", "pixelpirates:bell_rope");
    static final String[] SOFT_SUFFIX = {"_carpet", "_sign", "_banner", "_button", "_pressure_plate", "_tulip", "_candle", "_sapling",
            "_coral", "_coral_fan", "_coral_wall_fan", "_torch", "_rail"};

    /** Air, water or a thin thing you can walk through / that can't hold another block up. */
    static boolean soft(String desc) {
        if (desc.isEmpty() || desc.equals("minecraft:air") || desc.startsWith("minecraft:water")) return true;
        String b = base(desc), n = b.startsWith("minecraft:") ? b.substring(10) : b;
        if (SOFT_EXACT.contains(n)) return true;
        for (String s : SOFT_SUFFIX) if (n.endsWith(s)) return true;
        return n.endsWith("_door") || (n.endsWith("_trapdoor") && desc.contains("open=true"));
    }

    static boolean holds(int x, int y, int z) {
        if (!present(x, y, z)) return false;
        int v = id(x, y, z);
        return v <= 0 || !soft(name(v));
    }

    static boolean passable(int x, int y, int z) {
        int v = id(x, y, z);
        if (v == 0) return !terrainSolid(x, y, z);
        return v < 0 || soft(name(v));
    }

    /** Walkable at foot level: passable, or a step (a bottom slab / a stair) with headroom over it. */
    static boolean step(int x, int y, int z) {
        String d = name(id(x, y, z));
        boolean low = d.endsWith("_stairs") || base(d).endsWith("_stairs") || (base(d).endsWith("_slab") && !d.contains("type=top") && !d.contains("type=double"));
        return passable(x, y, z) || (low && passable(x, y + 1, z));
    }

    static void add(String kind, int x, int y, int z, String msg) { ISSUES.add(new Issue(kind, x, y, z, msg)); }

    // ------------------------------------------------------------------ FLOAT: everything must connect to the ground
    static void floating() {
        int X0 = PortCityLayout.X0, Y0 = PortCityLayout.Y0, Z0 = PortCityLayout.Z0;
        int W = PortCityLayout.X1 - X0 + 1, H = PortCityLayout.Y1 - Y0 + 1, D = PortCityLayout.Z1 - Z0 + 1;
        boolean[] seen = new boolean[W * H * D];
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) for (int y = 0; y < H; y++) {
            int v = G[(z * H + y) * W + x];
            if (v == 0 && present(x + X0, y + Y0, z + Z0)) { seen[(z * H + y) * W + x] = true; q.add(new int[]{x, y, z}); }
            else if (y == 0 && v > 0 && v != AIR_ID) { seen[(z * H + y) * W + x] = true; q.add(new int[]{x, y, z}); }
        }
        int[][] n6 = NB;
        boolean[] stepish = new boolean[PAL.size() + 1];
        for (int i = 0; i < PAL.size(); i++) { String b = base(PAL.get(i)); stepish[i + 1] = b.endsWith("_stairs") || b.endsWith("_slab"); }
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : n6) {
                int x = c[0] + d[0], y = c[1] + d[1], z = c[2] + d[2];
                if (x < 0 || y < 0 || z < 0 || x >= W || y >= H || z >= D) continue;
                int i = (z * H + y) * W + x;
                if (seen[i]) continue;
                int v = G[i];
                if (v <= 0 || v == AIR_ID) continue;
                if (d[3] == 1 && !stepish[v] && !stepish[Math.max(0, G[(c[2] * H + c[1]) * W + c[0]])]) continue;
                seen[i] = true;
                q.add(new int[]{x, y, z});
            }
        }
        // what was not reached: group into islands of blocks
        boolean[] reached = seen.clone();
        for (int z = 0; z < D; z++) for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            int i = (z * H + y) * W + x;
            int v = G[i];
            if (seen[i] || v <= 0 || v == AIR_ID) continue;
            List<int[]> grp = new ArrayList<>();
            seen[i] = true;
            q.add(new int[]{x, y, z});
            while (!q.isEmpty()) {
                int[] c = q.poll();
                grp.add(c);
                for (int[] d : n6) {
                    int xx = c[0] + d[0], yy = c[1] + d[1], zz = c[2] + d[2];
                    if (xx < 0 || yy < 0 || zz < 0 || xx >= W || yy >= H || zz >= D) continue;
                    int j = (zz * H + yy) * W + xx;
                    int w = G[j];
                    if (seen[j] || w <= 0 || w == AIR_ID) continue;
                    if (d[3] == 1 && !stepish[w] && !stepish[Math.max(0, G[(c[2] * H + c[1]) * W + c[0]])]) continue;
                    seen[j] = true;
                    q.add(new int[]{xx, yy, zz});
                }
            }
            int[] c = grp.get(0);
            String what = base(name(G[(c[2] * H + c[1]) * W + c[0]])).replace("minecraft:", "").replace("pixelpirates:", "pp:");
            boolean diagonal = false;                                                  // touches the rest only by an edge or corner
            for (int[] g : grp) {
                for (int dx = -1; dx <= 1 && !diagonal; dx++) for (int dy = -1; dy <= 1 && !diagonal; dy++) for (int dz = -1; dz <= 1 && !diagonal; dz++) {
                    int xx = g[0] + dx, yy = g[1] + dy, zz = g[2] + dz;
                    if (xx < 0 || yy < 0 || zz < 0 || xx >= W || yy >= H || zz >= D) continue;
                    if (reached[(zz * H + yy) * W + xx]) diagonal = true;
                }
                if (diagonal) break;
            }
            if (diagonal) add("DIAGONAL", c[0] + X0, c[1] + Y0, c[2] + Z0, grp.size() + " block(s) joined only by an edge/corner, e.g. " + what);
            else add("FLOAT", c[0] + X0, c[1] + Y0, c[2] + Z0, grp.size() + " block(s) touching nothing, e.g. " + what);
        }
    }

    /** Neighbours: the 6 faces (flag 0) and the 12 edges (flag 1 - they join only when a stair or slab is involved). */
    static final int[][] NB;
    static {
        List<int[]> l = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            int n = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
            if (n == 1 || n == 2) l.add(new int[]{dx, dy, dz, n - 1});
        }
        NB = l.toArray(new int[0][]);
    }

    // ------------------------------------------------------------------ CONNECT: fences/walls/panes/bars joined to their neighbours
    /** The layout's connection pass (run last) should leave nothing to do; anything here was written after it. */
    static void connections() {
        System.out.println("connection pass: " + PortCityLayout.CONNECTED + " fence/wall/pane/bar cell(s) joined up in the build");
        for (int x = PortCityLayout.X0; x <= PortCityLayout.X1; x++)
            for (int z = PortCityLayout.Z0; z <= PortCityLayout.Z1; z++)
                for (int y = PortCityLayout.Y0; y <= PortCityLayout.Y1; y++) {
                    int v = id(x, y, z);
                    if (v <= 0 || v == AIR_ID) continue;
                    String desc = name(v);
                    if (Connections.kind(desc) == Connections.Kind.NONE) continue;
                    String want = Connections.connect(desc, x, y, z, PortCityLayout::stateAt);
                    if (props(want).equals(props(desc))) continue;
                    String sh = base(desc).replace("minecraft:", "");
                    add("CONNECT", x, y, z, sh + " not joined to its neighbours: is [" + (desc.indexOf('[') < 0 ? "" : desc.substring(desc.indexOf('[') + 1).replace("]", ""))
                            + "] should be [" + want.substring(want.indexOf('[') + 1).replace("]", "") + "]");
                }
    }

    // ------------------------------------------------------------------ SUPPORT / HALF / DOORWAY
    static int[] dir(String f) {
        return switch (f) { case "north" -> new int[]{0, 0, -1}; case "south" -> new int[]{0, 0, 1}; case "east" -> new int[]{1, 0, 0};
            case "west" -> new int[]{-1, 0, 0}; case "up" -> new int[]{0, 1, 0}; default -> new int[]{0, -1, 0}; };
    }

    /** Wall-mounted: the block they hang on is opposite their facing. */
    static final String[] WALL = {"wall_banner", "wall_sign", "wall_torch", "wall_skull", "wall_head", "ladder", "tripwire_hook",
            "wanted_poster", "weapon_rack", "pattern_board", "dues_board", "drinks_menu", "hymn_board", "memorial_plaque", "wall_cross",
            "governor_portrait", "sea_chart", "watch_sign", "smithy_sign", "park_sign", "wall_hanging_sign", "dock_sign", "fish_sign", "tavern_sign", "chandlery_sign", "bakery_sign", "inn_sign", "perch_branch",
            "harbour_sign", "warehouse_sign", "distillery_sign"};
    /** Must stand on something. */
    static final String[] FLOOR = {"carpet", "rail", "pressure_plate", "candle", "flower_pot", "potted_", "sapling", "short_grass",
            "fern", "poppy", "dandelion", "tulip", "blue_orchid", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of",
            "sea_pickle", "cake", "redstone_wire", "_banner", "skull", "_head", "map_block", "telescope", "anchor_block", "parrot_roost"};
    /** Hangs from the block above. */
    static final String[] CEILING = {"crystal_chandelier", "hanging_rope", "hanging_net", "_hanging_sign", "spore_blossom", "cave_vines"};

    static boolean has(String b, String[] list) { for (String s : list) if (b.contains(s)) return true; return false; }

    static void perBlock() {
        for (int x = PortCityLayout.X0; x <= PortCityLayout.X1; x++)
            for (int z = PortCityLayout.Z0; z <= PortCityLayout.Z1; z++)
                for (int y = PortCityLayout.Y0; y <= PortCityLayout.Y1; y++) {
                    int v = id(x, y, z);
                    if (v <= 0 || v == AIR_ID) continue;
                    String desc = name(v), b = base(desc);
                    Map<String, String> p = props(desc);
                    String sh = b.replace("minecraft:", "").replace("pixelpirates:", "pp:");
                    if (b.endsWith("_wall_hanging_sign")) {                                  // hangs from the side, not the back
                        int[] d = dir(p.get("facing"));
                        if (!present(x - d[2], y, z + d[0]) && !present(x + d[2], y, z - d[0])) add("SUPPORT", x, y, z, sh + " hangs from nothing");
                        continue;
                    }
                    // wall-mounted
                    if (has(b, WALL) && p.containsKey("facing")) {
                        int[] d = dir(p.get("facing"));
                        if (!present(x - d[0], y, z - d[2])) add("SUPPORT", x, y, z, sh + " on " + p.get("facing") + " has nothing behind it");
                        continue;
                    }
                    if (b.endsWith("_button") || b.equals("minecraft:lever")) {
                        String face = p.getOrDefault("face", "wall");
                        int[] d = face.equals("wall") ? dir(p.get("facing")) : face.equals("floor") ? new int[]{0, 1, 0} : new int[]{0, -1, 0};
                        if (!holds(x - d[0], y - d[1], z - d[2])) add("SUPPORT", x, y, z, sh + " mounted on nothing");
                        continue;
                    }
                    if (b.endsWith("lantern") && !b.contains("sea_lantern")) {
                        boolean hanging = "true".equals(p.get("hanging"));
                        if (hanging ? !present(x, y + 1, z) : !holds(x, y - 1, z))
                            add("SUPPORT", x, y, z, sh + (hanging ? " hangs from nothing" : " stands on nothing"));
                        continue;
                    }
                    if (b.equals("minecraft:bell")) {
                        String a = p.getOrDefault("attachment", "floor");
                        boolean ok = a.equals("ceiling") ? present(x, y + 1, z) : a.equals("floor") ? holds(x, y - 1, z) : true;
                        if (!ok) add("SUPPORT", x, y, z, "bell (" + a + ") attached to nothing");
                        continue;
                    }
                    if (has(b, CEILING)) { if (!present(x, y + 1, z)) add("SUPPORT", x, y, z, sh + " hangs from nothing"); continue; }
                    if (b.endsWith("torch") && !b.contains("wall")) { if (!holds(x, y - 1, z)) add("SUPPORT", x, y, z, sh + " stands on nothing"); continue; }
                    if (b.equals("minecraft:lily_pad")) { if (!(id(x, y - 1, z) == WATER_ID || (id(x, y - 1, z) == 0 && y - 1 <= 62))) add("SUPPORT", x, y, z, "lily pad not on water"); continue; }
                    // doors, beds, tall plants: both halves
                    if (b.endsWith("_door") && !b.endsWith("trapdoor")) {
                        boolean lower = "lower".equals(p.get("half"));
                        String other = name(id(x, y + (lower ? 1 : -1), z));
                        if (!base(other).equals(b) || !(lower ? "upper" : "lower").equals(props(other).get("half")))
                            add("HALF", x, y, z, sh + " (" + p.get("half") + ") without its other half");
                        else if (lower) doorway(x, y, z, p.get("facing"), sh);
                        if (lower && !holds(x, y - 1, z)) add("SUPPORT", x, y, z, sh + " stands on nothing");
                        continue;
                    }
                    if (b.endsWith("_bed")) {
                        int[] d = dir(p.getOrDefault("facing", "north"));
                        boolean head = "head".equals(p.get("part"));
                        int ox = head ? -d[0] : d[0], oz = head ? -d[2] : d[2];
                        String other = name(id(x + ox, y, z + oz));
                        if (!base(other).equals(b) || !(head ? "foot" : "head").equals(props(other).get("part")))
                            add("HALF", x, y, z, sh + " (" + p.get("part") + ") without its other half");
                        continue;
                    }
                    if (p.containsKey("half") && (p.get("half").equals("lower") || p.get("half").equals("upper")) && !b.endsWith("_door")) {
                        boolean lower = p.get("half").equals("lower");
                        String other = name(id(x, y + (lower ? 1 : -1), z));
                        if (!base(other).equals(b)) add("HALF", x, y, z, sh + " (" + p.get("half") + ") without its other half");
                        continue;
                    }
                    if (has(b, FLOOR) && !b.contains("wall_")) {
                        if (b.contains("candle") && !holdsOrCake(x, y - 1, z)) add("SUPPORT", x, y, z, sh + " stands on nothing");
                        else if (!b.contains("candle") && !holds(x, y - 1, z)) add("SUPPORT", x, y, z, sh + " stands on nothing");
                    }
                }
    }

    static boolean holdsOrCake(int x, int y, int z) { return holds(x, y, z) || name(id(x, y, z)).contains("cake"); }

    /** A door must open onto space on both sides. */
    static void doorway(int x, int y, int z, String facing, String sh) {
        if (facing == null) return;
        int[] d = dir(facing);
        for (int s : new int[]{1, -1}) {
            int fx = x + d[0] * s, fz = z + d[2] * s;
            if (!step(fx, y, fz) || !passable(fx, y + 1, fz)) {
                String what = base(name(id(fx, passable(fx, y, fz) ? y + 1 : y, fz))).replace("minecraft:", "").replace("pixelpirates:", "pp:");
                add("DOORWAY", x, y, z, sh + " opens " + (s > 0 ? "out" : "in") + " onto " + (what.isEmpty() ? "ground" : what));
            }
        }
    }

    // ------------------------------------------------------------------ CLASH
    /** Overlaps that are the design (the yard / piers / warehouse dock are built over the quay's edge on purpose). */
    static final Set<String> INTENDED = Set.of("Quay & Harbour <- Shipwright Yard", "Quay & Harbour <- Piers",
            "Quay & Harbour <- Warehouse (east)", "Quay & Harbour <- Warehouse (west)", "Piers <- Harbour Boats", "City Wall <- Gatehouse",
            "Countryside <- Ridge Lookout", "Countryside <- Chess Green", "Countryside <- Farmstead", "Countryside <- Shepherd's Hut", "Chapel <- Gatehouse", "Manor <- Gatehouse");                     // the chapel + manor gardens run into the wall's band
    /** The ground layers every building is meant to build over. */
    static final Set<String> GROUND_PASSES = Set.of("City Paving", "Streets", "Terraces");

    static void clashes() {
        // one line per (old building, new writer, block pair) group, with a sample cell and a count
        Map<String, int[]> groups = new LinkedHashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        for (int[] c : PortCityLayout.CLASHES) {
            int[] xyz = PortCityLayout.cell(c[0]);
            if (PortCityLayout.get(xyz[0], xyz[1], xyz[2]) != c[4]) continue;                       // overwritten again later
            String from = c[1] == 0 ? "(street/terrain pass)" : PortCityLayout.LABEL_ORDER.get(c[1] - 1);
            String by = c[2] == 0 ? "(unlabelled pass: furnishing / greenery / decor)" : PortCityLayout.LABEL_ORDER.get(c[2] - 1);
            if (from.equals(by) || GROUND_PASSES.contains(from) || INTENDED.contains(from + " <- " + by)) continue;
            String key = from + " <- " + by + " : " + base(name(c[3])).replace("minecraft:", "") + " -> " + base(name(c[4])).replace("minecraft:", "");
            groups.putIfAbsent(key, xyz);
            counts.merge(key, 1, Integer::sum);
        }
        for (var e : groups.entrySet()) {
            int[] c = e.getValue();
            add("CLASH", c[0], c[1], c[2], e.getKey() + "  x" + counts.get(e.getKey()));
        }
    }

    // ------------------------------------------------------------------ report
    /** The building a cell belongs to: the smallest labelled box that holds it. */
    static String owner(int x, int z) {
        String best = "(outside every building)";
        long area = Long.MAX_VALUE;
        for (var e : PortCityLayout.buildings().entrySet()) {
            int[] b = e.getValue();
            if (x < b[0] || x > b[2] || z < b[1] || z > b[3]) continue;
            long a = (long) (b[2] - b[0] + 1) * (b[3] - b[1] + 1);
            if (a < area) { area = a; best = e.getKey(); }
        }
        return best;
    }

    static void report(java.io.File dir) throws Exception {
        dir.mkdirs();
        List<String> names = new ArrayList<>(PortCityLayout.buildings().keySet());
        Map<String, List<Issue>> by = new TreeMap<>((a, b) -> Integer.compare(names.indexOf(a), names.indexOf(b)));
        // a lone block that is FLOAT/DIAGONAL only because its support is missing is already a SUPPORT issue
        java.util.Set<String> sup = new java.util.HashSet<>();
        for (Issue i : ISSUES) if (i.kind.equals("SUPPORT")) sup.add(i.x + "," + i.y + "," + i.z);
        ISSUES.removeIf(i -> (i.kind.equals("FLOAT") || i.kind.equals("DIAGONAL")) && i.msg.startsWith("1 block") && sup.contains(i.x + "," + i.y + "," + i.z));
        for (Issue i : ISSUES) by.computeIfAbsent(owner(i.x, i.z), k -> new ArrayList<>()).add(i);
        Map<String, Integer> kinds = new TreeMap<>();
        for (Issue i : ISSUES) kinds.merge(i.kind, 1, Integer::sum);
        try (var w = new java.io.PrintWriter(new java.io.File(dir, "report.txt"), "UTF-8")) {
            w.println("SPAWN ISLAND BUILD CHECK - " + ISSUES.size() + " issue(s): " + kinds);
            w.println();
            for (var e : by.entrySet()) {
                int n = names.indexOf(e.getKey()) + 1;
                w.println("== " + (n > 0 ? "#" + n + " " : "") + e.getKey() + " (" + e.getValue().size() + ")");
                for (Issue i : e.getValue()) w.printf("  %-8s %5d %3d %5d  %s%n", i.kind, i.x, i.y, i.z, i.msg);
                w.println();
            }
        }
        // the map: issues as dots over the labelled overview
        int s = 4, X0 = PortCityLayout.X0, Z0 = PortCityLayout.Z0;
        var img = new java.awt.image.BufferedImage((PortCityLayout.X1 - X0 + 1) * s, (PortCityLayout.Z1 - Z0 + 1) * s, java.awt.image.BufferedImage.TYPE_INT_RGB);
        var g = img.createGraphics();
        for (int x = X0; x <= PortCityLayout.X1; x++) for (int z = Z0; z <= PortCityLayout.Z1; z++) {
            int top = 0;
            for (int y = PortCityLayout.Y1; y >= PortCityLayout.Y0; y--) { int v = id(x, y, z); if (v > 0 && v != AIR_ID) { top = y; break; } }
            int c = top == 0 ? 0x2a3440 : 0x606060 + Math.min(0x30, (top - 60) * 3) * 0x010101;
            g.setColor(new java.awt.Color(c));
            g.fillRect((x - X0) * s, (z - Z0) * s, s, s);
        }
        Map<String, java.awt.Color> col = Map.of("FLOAT", java.awt.Color.RED, "SUPPORT", java.awt.Color.ORANGE, "HALF", java.awt.Color.MAGENTA,
                "DOORWAY", java.awt.Color.CYAN, "CLASH", java.awt.Color.YELLOW, "CONNECT", java.awt.Color.GREEN,
                "DIAGONAL", new java.awt.Color(120, 200, 255));
        for (Issue i : ISSUES) {
            g.setColor(col.get(i.kind));
            g.fillOval((i.x - X0) * s - 3, (i.z - Z0) * s - 3, 8, 8);
        }
        g.setColor(java.awt.Color.WHITE);
        g.drawString("red FLOAT  blue DIAGONAL  orange SUPPORT  magenta HALF  cyan DOORWAY  yellow CLASH  green CONNECT", 8, 16);
        javax.imageio.ImageIO.write(img, "png", new java.io.File(dir, "issues_map.png"));
        System.out.println("BUILD CHECK: " + ISSUES.size() + " issue(s) " + kinds + " -> " + new java.io.File(dir, "report.txt").getAbsolutePath());
        for (var e : by.entrySet()) System.out.println("  " + e.getValue().size() + "\t" + e.getKey());
    }
}
