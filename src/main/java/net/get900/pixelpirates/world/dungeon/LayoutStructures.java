package net.get900.pixelpirates.world.dungeon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.world.gen.IslandEdits;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldAccess;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LAYOUT STRUCTURES - every structure authored as DATA (the ChatGPT/Codex packages, 2026-10-02 on) goes through here:
 * {@code data/pixelpirates/layouts/<id>.json} = a block-state palette + x-runs {@code [x1, x2, y, z, paletteIndex]} in design
 * space (rotated by DungeonBuilder), loot markers {@code {pos, table}}, mob markers {@code {id, pos}} (the beacon's older
 * {@code guards: [[x,y,z]]} = pirate crew) and saved block-entity data {@code nbt: [{pos, data}]}. Registering one is a single
 * line in Dungeons.ALL: {@code builder("id")} (or {@code seabed("id")}) - no Java class per structure.
 *
 * EDITING (the /ppisland capture idea for structures, see StructureEditState + /ppstruct): build a copy with /ppdungeon, change
 * it by hand, /ppstruct save - the world is read back into the layout (rotated back to design space) and written to
 * {@code <game dir>/pixelpirates/layouts/<id>.json}, plus straight into src/main/resources when running from the dev workspace.
 * A local file in the game dir overrides the shipped one.
 */
public final class LayoutStructures {
    private LayoutStructures() {}

    public static final int REACH = DungeonBuilder.MAX_REACH;

    record Marker(int x, int y, int z, String value) {}

    /** A parsed, validated layout. */
    public static final class Layout {
        final String id;
        final JsonObject json;
        final List<String> palette = new ArrayList<>();
        final List<BlockState> states = new ArrayList<>();
        final List<int[]> runs = new ArrayList<>();
        final int[] bounds;
        final boolean blend;                                              // "blend": false = build exactly, never adapt ground cells
        final List<Marker> loot = new ArrayList<>(), mobs = new ArrayList<>(), nbt = new ArrayList<>();
        final List<Marker> nodes = new ArrayList<>();                    // node_nbt: full block-entity data (puzzle stones)
        final Set<Long> occupied = new HashSet<>();                       // every cell the layout writes
        final Map<Long, Integer> cellPalette = new HashMap<>();           // cell -> palette index (last run wins)

        Layout(String id, JsonObject json, int[] bounds) {
            this.id = id; this.json = json; this.bounds = bounds;
            this.blend = !json.has("blend") || json.get("blend").getAsBoolean();
        }

        public int[] bounds() { return bounds.clone(); }
    }

    private static final Map<String, Layout> CACHE = new ConcurrentHashMap<>();

    static String resource(String id) { return "/data/pixelpirates/layouts/" + id + ".json"; }

    public static Path localFile(String id) {
        return FabricLoader.getInstance().getGameDir().resolve("pixelpirates/layouts/" + id + ".json");
    }

    /** Is {@code id} a layout structure (shipped or local)? */
    public static boolean exists(String id) {
        return Files.exists(localFile(id)) || LayoutStructures.class.getResource(resource(id)) != null;
    }

    public static Layout get(String id) { return CACHE.computeIfAbsent(id, LayoutStructures::read); }

    public static void reload(String id) { CACHE.remove(id); }

    private static Layout read(String id) {
        JsonObject json;
        try {
            Path local = localFile(id);
            if (Files.exists(local)) {
                try (Reader r = Files.newBufferedReader(local, StandardCharsets.UTF_8)) { json = JsonParser.parseReader(r).getAsJsonObject(); }
            } else {
                try (InputStream in = LayoutStructures.class.getResourceAsStream(resource(id))) {
                    if (in == null) throw new IllegalStateException("Missing layout " + resource(id));
                    json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read layout " + id, e);
        }
        return parse(id, json);
    }

    /** Parse + validate everything before anything is placed (a bad palette entry or run throws here, not mid-build). */
    static Layout parse(String id, JsonObject json) {
        int[] b = ints(json.getAsJsonArray("bounds"), 6);
        if (b[0] < -REACH || b[3] > REACH || b[2] < -REACH || b[5] > REACH || b[0] > b[3] || b[1] > b[4] || b[2] > b[5])
            throw new IllegalStateException("Layout " + id + ": bounds must stay within +-" + REACH);
        Layout l = new Layout(id, json, b);
        for (JsonElement e : json.getAsJsonArray("palette")) {
            String spec = e.getAsString();
            l.palette.add(spec);
            l.states.add(state(spec, id));
        }
        for (JsonElement e : json.getAsJsonArray("runs")) {
            int[] r = ints(e.getAsJsonArray(), 5);
            if (r[0] > r[1] || r[4] < 0 || r[4] >= l.states.size() || !inside(b, r[0], r[2], r[3]) || !inside(b, r[1], r[2], r[3]))
                throw new IllegalStateException("Layout " + id + ": run out of bounds " + Arrays.toString(r));
            l.runs.add(r);
            for (int x = r[0]; x <= r[1]; x++) { l.occupied.add(key(x, r[2], r[3])); l.cellPalette.put(key(x, r[2], r[3]), r[4]); }
        }
        if (json.has("loot")) for (JsonElement e : json.getAsJsonArray("loot")) {
            JsonObject o = e.getAsJsonObject();
            l.loot.add(marker(b, id, o.getAsJsonArray("pos"), new Identifier(o.get("table").getAsString()).toString()));
        }
        if (json.has("mobs")) for (JsonElement e : json.getAsJsonArray("mobs")) {
            JsonObject o = e.getAsJsonObject();
            l.mobs.add(marker(b, id, o.getAsJsonArray("pos"), new Identifier(o.get("id").getAsString()).toString()));
        }
        if (json.has("guards")) for (JsonElement e : json.getAsJsonArray("guards"))                // the beacon's older form
            l.mobs.add(marker(b, id, e.getAsJsonArray(), "pixelpirates:pirate_crew"));
        if (json.has("node_nbt")) for (JsonElement e : json.getAsJsonArray("node_nbt")) {       // Phase 5 puzzle stones
            JsonObject o = e.getAsJsonObject();
            l.nodes.add(marker(b, id, o.getAsJsonArray("pos"), o.getAsJsonObject("data").toString()));
        }
        if (json.has("nbt")) for (JsonElement e : json.getAsJsonArray("nbt")) {
            JsonObject o = e.getAsJsonObject();
            l.nbt.add(marker(b, id, o.getAsJsonArray("pos"), o.get("data").getAsString()));
        }
        return l;
    }

    // ------------------------------------------------------------------ building

    /** Dungeons.Builder for a layout structure (mobs included). */
    public static Dungeons.Builder builder(String id) { return (b, depth) -> build(b, id, true); }

    /** As {@link #builder} for SEABED designs: refuses a site with less water than the design's top + 2. */
    public static Dungeons.Builder seabed(String id) {
        return (b, depth) -> {
            if (depth < get(id).bounds[4] + 2) throw new IllegalArgumentException("Not enough water for " + id + " (" + depth + ")");
            build(b, id, true);
        };
    }

    public static void build(DungeonBuilder b, String id, boolean includeMobs) {
        Layout l = get(id);
        if (b.origin.getY() + l.bounds[1] < b.world.getBottomY() || b.origin.getY() + l.bounds[4] >= b.world.getTopY())
            throw new IllegalArgumentException(id + " exceeds world height at " + b.origin);
        Blend blend = sample(b, l);
        for (int[] r : l.runs) {
            BlockState s = l.states.get(r[4]);
            for (int x = r[0]; x <= r[1]; x++) {
                BlockState put = l.blend && ground(s, r[2]) ? blended(b, l, blend, x, r[2], r[3]) : s;
                if (put != null) b.set(x, r[2], r[3], placed(l, put, x, r[2], r[3]));
            }
        }
        for (Marker m : l.nbt) applyNbt(b.world, b.pos(m.x, m.y, m.z), m.value);
        for (Marker m : l.nodes) applyNode(b.world, b.pos(m.x, m.y, m.z), m.value, id);
        for (Marker m : l.loot) LootableContainerBlockEntity.setLootTable(b.world, b.random, b.pos(m.x, m.y, m.z), new Identifier(m.value));
        if (includeMobs) for (Marker m : l.mobs) {
            // DungeonBuilder.spawnMob initialises + persists BEFORE the worldgen chunk serialises the entity
            if (Registries.ENTITY_TYPE.containsId(new Identifier(m.value))) b.spawnMob(m.value, m.x, m.y, m.z);
        }
    }

    // ------------------------------------------------------------------ ground blending

    /*
     * GROUND BLENDING (2026-10-02; the user: "a structure that has just spawned on a cube of sand"). The ChatGPT layouts lay
     * their own slab - sandstone/stone/basalt from y-5 up, sand or coarse dirt on top - whatever island they land on. Now a
     * layout's GROUND cells (soil at y<=0; soil or foundation rock below y=0 - floors, paths and paving above are never
     * touched) adapt: where the island already has natural ground the island's block stays; a gap is filled with what that
     * column naturally has (its surface block where the cell is open above, the block 3 below the surface otherwise), the
     * site's most common one where the column had nothing usable, and the local SEABED block under water.
     */
    private static final Set<String> SOIL = Set.of("sand", "red_sand", "gravel", "coarse_dirt", "dirt", "grass_block", "podzol",
            "rooted_dirt", "mud", "clay", "suspicious_sand", "suspicious_gravel");
    private static final Set<String> ROCK = Set.of("sandstone", "red_sandstone", "stone", "basalt", "andesite", "diorite", "granite",
            "cobblestone", "mossy_cobblestone", "deepslate", "tuff", "blackstone");

    /** Is this layout cell ground (blendable) rather than architecture? */
    static boolean ground(BlockState s, int y) {
        if (y > 0) return false;
        Identifier id = Registries.BLOCK.getId(s.getBlock());
        return id.getNamespace().equals("minecraft") && (SOIL.contains(id.getPath()) || (y < 0 && ROCK.contains(id.getPath())));
    }

    private static boolean natural(BlockState s) {
        return !s.isAir() && s.getFluidState().isEmpty() && s.isOpaque() && s.getBlock() != net.minecraft.block.Blocks.BEDROCK;
    }

    /** Per design column (x, z): the natural surface block, the block 3 under it, and whether it lay under water. */
    record Blend(int x0, int z0, int depth, BlockState[] top, BlockState[] fill, boolean[] wet, BlockState landTop, BlockState landFill, BlockState seaTop) {
        int i(int x, int z) { return (x - x0) * depth + (z - z0); }
    }

    static Blend sample(DungeonBuilder b, Layout l) {
        int x0 = l.bounds[0], z0 = l.bounds[2], w = l.bounds[3] - x0 + 1, d = l.bounds[5] - z0 + 1;
        BlockState[] top = new BlockState[w * d], fill = new BlockState[w * d];
        boolean[] wet = new boolean[w * d];
        Map<BlockState, Integer> land = new HashMap<>(), under = new HashMap<>(), sea = new HashMap<>();
        // worldgen = a ChunkRegion with the _WG heightmaps; /ppdungeon + /ppstruct restamp = a live world without them
        net.minecraft.world.Heightmap.Type hm = b.world instanceof ServerWorld ? net.minecraft.world.Heightmap.Type.OCEAN_FLOOR
                : net.minecraft.world.Heightmap.Type.OCEAN_FLOOR_WG;
        for (int x = x0; x < x0 + w; x++)
            for (int z = z0; z < z0 + d; z++) {
                BlockPos c = b.pos(x, 0, z);
                BlockPos tp = new BlockPos(c.getX(), b.world.getTopY(hm, c.getX(), c.getZ()) - 1, c.getZ());
                BlockState t = b.world.getBlockState(tp);
                if (!natural(t)) continue;
                BlockState f = b.world.getBlockState(tp.down(3));
                if (!natural(f)) f = t;
                int i = (x - x0) * d + (z - z0);
                top[i] = t; fill[i] = f; wet[i] = !b.world.getFluidState(tp.up()).isEmpty();
                (wet[i] ? sea : land).merge(t, 1, Integer::sum);
                under.merge(f, 1, Integer::sum);
            }
        BlockState sand = net.minecraft.block.Blocks.SAND.getDefaultState();
        BlockState lt = most(land, most(sea, sand)), st = most(sea, lt);
        return new Blend(x0, z0, d, top, fill, wet, lt, most(under, net.minecraft.block.Blocks.STONE.getDefaultState()), st);
    }

    private static BlockState most(Map<BlockState, Integer> m, BlockState fallback) {
        BlockState best = fallback;
        int n = 0;
        for (var e : m.entrySet()) if (e.getValue() > n) { n = e.getValue(); best = e.getKey(); }
        return best;
    }

    /** What a ground cell becomes; null = leave the island's own block. */
    static BlockState blended(DungeonBuilder b, Layout l, Blend bl, int x, int y, int z) {
        Integer above = l.cellPalette.get(key(x, y + 1, z));
        BlockState up = above == null ? null : l.states.get(above);
        boolean open = up == null || up.isAir() || !up.getFluidState().isEmpty() || !up.isOpaque();
        boolean waterAbove = up != null ? !up.getFluidState().isEmpty() : !b.world.getFluidState(b.pos(x, y + 1, z)).isEmpty();
        int i = bl.i(x, z);
        if (!open) {
            if (natural(b.world.getBlockState(b.pos(x, y, z)))) return null;                        // the island's rock/soil stays
            return bl.fill[i] != null ? bl.fill[i] : bl.landFill;
        }
        if (waterAbove) return bl.top[i] != null && bl.wet[i] ? bl.top[i] : bl.seaTop;
        return bl.top[i] != null && !bl.wet[i] ? bl.top[i] : bl.landTop;
    }

    /**
     * Sand, gravel or concrete powder with nothing of the layout under it (its bottom layer) is placed as its stable twin -
     * over open water it would drop the moment anything nearby updated (a /ppdungeon test copy lost 46 sand blocks that way),
     * leaving holes in a foundation.
     */
    static BlockState placed(Layout l, BlockState s, int x, int y, int z) {
        if (!(s.getBlock() instanceof net.minecraft.block.FallingBlock) || l.occupied.contains(key(x, y - 1, z))) return s;
        String id = Registries.BLOCK.getId(s.getBlock()).getPath();
        String twin = id.equals("sand") || id.equals("suspicious_sand") ? "sandstone" : id.equals("red_sand") ? "red_sandstone"
                : id.endsWith("gravel") ? "cobblestone" : id.endsWith("_concrete_powder") ? id.replace("_powder", "") : null;
        return twin == null ? s : Registries.BLOCK.get(new Identifier("minecraft", twin)).getDefaultState();
    }

    /** Saved block-entity data (sign text, banner patterns, an item in a frame-like block...) onto whatever is at pos. */
    static void applyNbt(WorldAccess world, BlockPos pos, String snbt) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be == null) return;
        try {
            NbtCompound n = StringNbtReader.parse(snbt);
            for (String k : new String[]{"x", "y", "z", "id"}) n.remove(k);
            NbtCompound cur = be.createNbt();
            cur.copyFrom(n);
            be.readNbt(cur);
            be.markDirty();
        } catch (Exception e) {
            PixelPirates.LOGGER.warn("[Layout] bad block data at {}: {}", pos, e.getMessage());
        }
    }

    /** node_nbt: a JSON object of ints/strings read straight into the block entity there (its whole configuration - the
     *  puzzle stones' kind, role and design-space links). A missing block entity means the layout is broken: throw. */
    static void applyNode(WorldAccess world, BlockPos pos, String json, String id) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be == null) throw new IllegalStateException("Layout " + id + ": node_nbt at " + pos + " has no block entity");
        NbtCompound n = new NbtCompound();
        for (var e : JsonParser.parseString(json).getAsJsonObject().entrySet()) {
            if (e.getKey().equals("id")) continue;
            if (e.getValue().getAsJsonPrimitive().isNumber()) n.putInt(e.getKey(), e.getValue().getAsInt());
            else n.putString(e.getKey(), e.getValue().getAsString());
        }
        be.readNbt(n);
        be.markDirty();
    }

    // ------------------------------------------------------------------ saving hand edits back into the layout

    public record SaveResult(int changed, int added, int lootAdded, int lootLost, int withData, List<Path> files) {}

    /**
     * Read a built copy back into the layout. Every cell the layout owns is compared with the world (so /fill and
     * /setblock count too); any other cell in reach counts only if a player touched it and it no longer holds the block
     * it had before ({@code before}: world pos -> state string, from StructureEditState). A new EMPTY chest/barrel becomes a
     * loot container with the nearest existing loot table; a loot container that is gone loses its marker.
     */
    public static SaveResult save(ServerWorld w, String id, BlockPos origin, BlockRotation rot, Map<Long, String> before) throws IOException {
        Layout l = get(id);
        Map<Long, String> cells = new HashMap<>();
        for (int[] r : l.runs) for (int x = r[0]; x <= r[1]; x++) cells.put(key(x, r[2], r[3]), l.palette.get(r[4]));
        Map<Long, String> nbt = new HashMap<>(), loot = new LinkedHashMap<>();
        for (Marker m : l.nbt) nbt.put(key(m.x, m.y, m.z), m.value);
        for (Marker m : l.loot) loot.put(key(m.x, m.y, m.z), m.value);
        DungeonBuilder b = new DungeonBuilder(w, origin, rot, w.random);
        BlockRotation back = switch (rot) {
            case CLOCKWISE_90 -> BlockRotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> BlockRotation.CLOCKWISE_90;
            default -> rot;
        };
        Map<String, BlockState> parsed = new HashMap<>();
        int changed = 0, added = 0, lootAdded = 0, lootLost = 0;
        int x1 = Math.max(-REACH, l.bounds[0] - 4), x2 = Math.min(REACH, l.bounds[3] + 4);
        int z1 = Math.max(-REACH, l.bounds[2] - 4), z2 = Math.min(REACH, l.bounds[5] + 4);
        for (int x = x1; x <= x2; x++)
            for (int y = l.bounds[1] - 2; y <= l.bounds[4] + 8; y++)
                for (int z = z1; z <= z2; z++) {
                    long k = key(x, y, z);
                    BlockPos wp = b.pos(x, y, z);
                    String was = cells.get(k), touchedWas = before.get(wp.asLong());
                    if (was == null && touchedWas == null) continue;
                    // a ground cell was blended into the terrain when it was built: only a hand edit there counts
                    if (was != null && touchedWas == null && l.blend && ground(parsed.computeIfAbsent(was, s -> state(s, id)), y)) continue;
                    BlockState now = w.getBlockState(wp).rotate(back);
                    String ref = was != null ? was : touchedWas;
                    BlockState refState = was != null ? placed(l, parsed.computeIfAbsent(ref, s -> state(s, id)), x, y, z)
                            : parsed.computeIfAbsent("~" + ref, s -> state(ref, id).rotate(back));       // touched: a world state
                    boolean differs = !IslandEdits.same(now, refState) && !IslandEdits.drift(now, refState);
                    // gravity is not an edit: a falling block that dropped out of (or landed in) an untouched cell
                    if (differs && touchedWas == null && (refState.getBlock() instanceof net.minecraft.block.FallingBlock || now.getBlock() instanceof net.minecraft.block.FallingBlock)
                            && (now.isAir() || !now.getFluidState().isEmpty() || refState.isAir() || !refState.getFluidState().isEmpty())) differs = false;
                    if (differs) {
                        cells.put(k, BlockArgumentParser.stringifyBlockState(now));
                        if (was == null) added++; else changed++;
                    }
                    BlockEntity be = w.getBlockEntity(wp);
                    if (loot.containsKey(k) && !(be instanceof LootableContainerBlockEntity)) { loot.remove(k); lootLost++; }
                    if (differs && be instanceof LootableContainerBlockEntity c && !loot.containsKey(k) && !l.loot.isEmpty() && c.isEmpty()) {
                        loot.put(k, nearest(l.loot, x, y, z)); lootAdded++;
                    }
                    if (was != null || differs) {
                        String d = IslandEdits.data(be, loot.containsKey(k));
                        if (d != null) nbt.put(k, d); else nbt.remove(k);
                    }
                }
        // nothing changed at all: leave the file alone
        if (changed == 0 && added == 0 && lootAdded == 0 && lootLost == 0 && nbt.equals(markerMap(l.nbt)))
            return new SaveResult(0, 0, 0, 0, nbt.size(), List.of());
        String text = write(l, cells, loot, nbt);
        List<Path> files = new ArrayList<>();
        files.add(localFile(id));
        Path dev = FabricLoader.getInstance().getGameDir().toAbsolutePath().getParent();
        if (dev != null && Files.isDirectory(dev.resolve("src/main/resources/data/pixelpirates/layouts")))
            files.add(dev.resolve("src/main/resources/data/pixelpirates/layouts/" + id + ".json"));
        for (Path f : files) {
            Files.createDirectories(f.getParent());
            Files.write(f, text.getBytes(StandardCharsets.UTF_8));                                  // UTF-8, never a BOM
        }
        reload(id);
        get(id);                                                                                    // re-parse now: fail loudly here, not in worldgen
        return new SaveResult(changed, added, lootAdded, lootLost, nbt.size(), files);
    }

    /** Remove the game-dir copy of a layout (the shipped one applies again). */
    public static boolean revert(String id) throws IOException {
        boolean had = Files.deleteIfExists(localFile(id));
        reload(id);
        return had;
    }

    static String write(Layout l, Map<Long, String> cells, Map<Long, String> loot, Map<Long, String> nbt) {
        // runs: sorted by y, z, x; consecutive x with one state = one run. Palette in order of first use.
        List<Long> keys = new ArrayList<>(cells.keySet());
        keys.sort(Comparator.<Long>comparingInt(k -> ky(k)).thenComparingInt(k -> kz(k)).thenComparingInt(k -> kx(k)));
        List<String> palette = new ArrayList<>();
        Map<String, Integer> index = new HashMap<>();
        List<int[]> runs = new ArrayList<>();
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        int[] cur = null;
        for (long k : keys) {
            int x = kx(k), y = ky(k), z = kz(k);
            int p = index.computeIfAbsent(cells.get(k), s -> { palette.add(s); return palette.size() - 1; });
            if (cur != null && cur[2] == y && cur[3] == z && cur[1] == x - 1 && cur[4] == p) cur[1] = x;
            else { cur = new int[]{x, x, y, z, p}; runs.add(cur); }
            bounds[0] = Math.min(bounds[0], x); bounds[1] = Math.min(bounds[1], y); bounds[2] = Math.min(bounds[2], z);
            bounds[3] = Math.max(bounds[3], x); bounds[4] = Math.max(bounds[4], y); bounds[5] = Math.max(bounds[5], z);
        }
        JsonObject out = l.json.deepCopy();
        for (String k : new String[]{"palette", "runs", "bounds", "loot", "mobs", "guards", "nbt"}) out.remove(k);
        Gson g = new GsonBuilder().disableHtmlEscaping().create();
        StringBuilder sb = new StringBuilder("{\n");
        for (Map.Entry<String, JsonElement> e : out.entrySet())
            sb.append("  ").append(g.toJson(e.getKey())).append(": ").append(g.toJson(e.getValue())).append(",\n");
        sb.append("  \"bounds\": ").append(g.toJson(bounds)).append(",\n");
        sb.append("  \"loot\": [");
        int i = 0;
        for (Map.Entry<Long, String> e : loot.entrySet())
            sb.append(i++ == 0 ? "\n" : ",\n").append("    {\"pos\": ").append(pos(e.getKey())).append(", \"table\": ").append(g.toJson(e.getValue())).append("}");
        sb.append(loot.isEmpty() ? "],\n" : "\n  ],\n");
        sb.append("  \"mobs\": [");
        i = 0;
        for (Marker m : l.mobs)
            sb.append(i++ == 0 ? "\n" : ",\n").append("    {\"id\": ").append(g.toJson(m.value)).append(", \"pos\": [").append(m.x).append(", ").append(m.y).append(", ").append(m.z).append("]}");
        sb.append(l.mobs.isEmpty() ? "],\n" : "\n  ],\n");
        if (!nbt.isEmpty()) {
            sb.append("  \"nbt\": [");
            i = 0;
            for (Map.Entry<Long, String> e : new TreeMap<>(nbt).entrySet())
                sb.append(i++ == 0 ? "\n" : ",\n").append("    {\"pos\": ").append(pos(e.getKey())).append(", \"data\": ").append(g.toJson(e.getValue())).append("}");
            sb.append("\n  ],\n");
        }
        sb.append("  \"palette\": [");
        for (int j = 0; j < palette.size(); j++) sb.append(j == 0 ? "\n" : ",\n").append("    ").append(g.toJson(palette.get(j)));
        sb.append("\n  ],\n  \"runs\": [");
        for (int j = 0; j < runs.size(); j++) sb.append(j == 0 ? "\n" : ",\n").append("    ").append(g.toJson(runs.get(j)));
        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    // ------------------------------------------------------------------ helpers

    private static Map<Long, String> markerMap(List<Marker> ms) {
        Map<Long, String> m = new HashMap<>();
        for (Marker k : ms) m.put(key(k.x, k.y, k.z), k.value);
        return m;
    }

    private static String nearest(List<Marker> loot, int x, int y, int z) {
        Marker best = loot.get(0);
        double bd = Double.MAX_VALUE;
        for (Marker m : loot) {
            double d = Math.pow(m.x - x, 2) + Math.pow(m.y - y, 2) + Math.pow(m.z - z, 2);
            if (d < bd) { bd = d; best = m; }
        }
        return best.value;
    }

    static BlockState state(String spec, String id) {
        try {
            return BlockArgumentParser.block(Registries.BLOCK.getReadOnlyWrapper(), spec, false).blockState();
        } catch (Exception e) {
            throw new IllegalStateException("Layout " + id + ": bad block state " + spec, e);
        }
    }

    private static Marker marker(int[] b, String id, JsonArray pos, String value) {
        int[] p = ints(pos, 3);
        if (!inside(b, p[0], p[1], p[2])) throw new IllegalStateException("Layout " + id + ": marker outside bounds " + Arrays.toString(p));
        return new Marker(p[0], p[1], p[2], value);
    }

    private static boolean inside(int[] b, int x, int y, int z) {
        return x >= b[0] && x <= b[3] && y >= b[1] && y <= b[4] && z >= b[2] && z <= b[5];
    }

    private static int[] ints(JsonArray a, int n) {
        if (a == null || a.size() != n) throw new IllegalStateException("Expected " + n + " numbers");
        int[] v = new int[n];
        for (int i = 0; i < n; i++) v[i] = a.get(i).getAsInt();
        return v;
    }

    static long key(int x, int y, int z) { return ((long) (x + 512) << 22) | ((long) (y + 512) << 11) | (z + 512); }
    static int kx(long k) { return (int) (k >> 22) - 512; }
    static int ky(long k) { return (int) ((k >> 11) & 0x7FF) - 512; }
    static int kz(long k) { return (int) (k & 0x7FF) - 512; }
    private static String pos(long k) { return "[" + kx(k) + ", " + ky(k) + ", " + kz(k) + "]"; }
}
