package net.get900.pixelpirates.world.gen;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HAND EDITS for the spawn island (2026-10-02): build in game, then save with
 *   /ppisland capture <building>  - that building's own area (its label box; a cell inside several boxes belongs to the
 *                                   smallest one)
 *   /ppisland capture grounds     - everything outside every building: streets, avenues, lawns, the quay edge... ("Streets &
 *                                   Grounds")
 *   /ppisland capture all         - every spot you broke, placed or used a block at since the last capture
 *                                   (IslandEditTracker), filed into its building or the grounds
 * Every block that differs from PortCityLayout's plan is saved - its state and, for signs, banners, containers, pedestals,
 * lecterns, heads and named blocks, its block-entity data. Each cell belongs to exactly one section. Written to
 * <game dir>/pixelpirates/island_edits.txt and, in the dev workspace, straight into src/main/resources/data/pixelpirates/
 * island/edits.txt (commit it and every new world gets them). The layout applies them last, so restamps, new chunks and the
 * build checker all see them. Not captured: entities (item frames, armor stands, paintings, mobs) and cells the plan never
 * touches (deep terrain). Natural drift (grass spreading onto dirt, flowing water) is not an edit.
 */
public final class IslandEdits {
    private IslandEdits() {}

    /** The section for every cell outside all building boxes. */
    public static final String GROUNDS = "Streets & Grounds";

    /** Properties that change by themselves or by use - a difference in only these is not an edit. */
    private static final Set<String> NOISE = Set.of("distance", "open", "powered", "occupied", "triggered", "age", "moisture",
            "power", "note", "instrument", "has_book", "lit", "signal_fire", "attached", "in_wall", "persistent", "fuel", "snowy");

    public record Result(int edits, int withData, int sections, int spots) {}

    /** The section a column belongs to. */
    public static String sectionOf(int x, int z) {
        String o = PortCityLayout.ownerOf(x, z);
        return o == null ? GROUNDS : o;
    }

    /** One building (or GROUNDS). */
    public static Result capture(ServerWorld w, String section) throws java.io.IOException {
        int[] box = section.equals(GROUNDS) ? new int[]{PortCityLayout.X0, PortCityLayout.Z0, PortCityLayout.X1, PortCityLayout.Z1}
                : PortCityLayout.buildings().get(section);
        Map<String, List<PortCityLayout.Edit>> found = scan(w, box, Set.of(section));
        List<PortCityLayout.Edit> out = found.getOrDefault(section, List.of());
        PortCityLayout.setEdits(section, out, targets(w));
        SpawnIslandFeature.refreshPalette();
        return new Result(out.size(), withData(out), out.isEmpty() ? 0 : 1, 0);
    }

    /**
     * /ppisland capture all: save every spot a player has touched since the last capture (IslandEditTracker) - each into its
     * building's section or GROUNDS, replacing what was saved there; a spot put back the way the plan has it is dropped.
     * Never a blanket compare: an older world still holds old-layout blocks and shifted random decor that are not edits.
     */
    public static Result captureAll(ServerWorld w) throws java.io.IOException {
        IslandEditTracker t = IslandEditTracker.get(w.getServer());
        BlockState[] plan = SpawnIslandFeature.paletteStates();
        Map<String, List<PortCityLayout.Edit>> sections = new java.util.LinkedHashMap<>();
        Map<Long, String> where = new java.util.HashMap<>();
        for (var e : PortCityLayout.edits().entrySet()) {
            sections.put(e.getKey(), new ArrayList<>(e.getValue()));
            for (PortCityLayout.Edit d : e.getValue()) where.put(BlockPos.asLong(d.x(), d.y(), d.z()), e.getKey());
        }
        Set<String> changed = new java.util.HashSet<>();
        int saved = 0, data = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (long l : t.touched) {
            pos.set(BlockPos.unpackLongX(l), BlockPos.unpackLongY(l), BlockPos.unpackLongZ(l));
            int x = pos.getX(), y = pos.getY(), z = pos.getZ();
            int id = PortCityLayout.planGet(x, y, z);
            if (id == 0) continue;                                                        // outside the plan
            String old = where.remove(l);
            if (old != null) { sections.get(old).removeIf(d -> d.x() == x && d.y() == y && d.z() == z); changed.add(old); }
            BlockState now = w.getBlockState(pos);
            BlockState was = plan[id - 1];
            if (drift(now, was)) continue;
            String nbt = edited(w.getBlockEntity(pos), x, y, z);
            if (same(now, was) && nbt == null) continue;                                  // put back as planned
            String sec = sectionOf(x, z);
            sections.computeIfAbsent(sec, k -> new ArrayList<>()).add(new PortCityLayout.Edit(x, y, z, BlockArgumentParser.stringifyBlockState(now), nbt));
            changed.add(sec);
            saved++;
            if (nbt != null) data++;
        }
        for (String sec : changed) PortCityLayout.setEdits(sec, sections.get(sec), List.of());
        PortCityLayout.saveEdits(targets(w));
        SpawnIslandFeature.refreshPalette();
        int spots = t.touched.size();
        t.touched.clear();
        t.markDirty();
        return new Result(saved, data, changed.size(), spots);
    }

    /** Compare the world with the plan over a box, chunk by chunk; `only` = the sections to keep (null = all). */
    private static Map<String, List<PortCityLayout.Edit>> scan(ServerWorld w, int[] box, Set<String> only) {
        BlockState[] plan = SpawnIslandFeature.paletteStates();
        Map<String, List<PortCityLayout.Edit>> out = new java.util.LinkedHashMap<>();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int x1 = Math.max(box[0], PortCityLayout.X0), x2 = Math.min(box[2], PortCityLayout.X1);
        int z1 = Math.max(box[1], PortCityLayout.Z0), z2 = Math.min(box[3], PortCityLayout.Z1);
        for (int cx = x1 >> 4; cx <= x2 >> 4; cx++)
            for (int cz = z1 >> 4; cz <= z2 >> 4; cz++) {
                net.minecraft.world.chunk.WorldChunk chunk = null;
                for (int x = Math.max(x1, cx << 4); x <= Math.min(x2, (cx << 4) + 15); x++)
                    for (int z = Math.max(z1, cz << 4); z <= Math.min(z2, (cz << 4) + 15); z++) {
                        String sec = sectionOf(x, z);
                        if (only != null && !only.contains(sec)) continue;
                        if (chunk == null) chunk = w.getChunk(cx, cz);
                        for (int y = PortCityLayout.Y0; y <= PortCityLayout.Y1; y++) {
                            int id = PortCityLayout.planGet(x, y, z);
                            if (id == 0) continue;
                            pos.set(x, y, z);
                            BlockState now = chunk.getBlockState(pos);
                            BlockState was = plan[id - 1];
                            if (drift(now, was)) continue;
                            String nbt = edited(chunk.getBlockEntity(pos), x, y, z);
                            if (same(now, was) && nbt == null) continue;
                            out.computeIfAbsent(sec, k -> new ArrayList<>())
                                    .add(new PortCityLayout.Edit(x, y, z, BlockArgumentParser.stringifyBlockState(now), nbt));
                        }
                    }
            }
        return out;
    }

    /** Changes the world makes by itself: flowing liquid, grass spreading onto (or dying back to) dirt. */
    public static boolean drift(BlockState now, BlockState was) {
        if (now.getBlock() instanceof net.minecraft.block.FluidBlock && !now.getFluidState().isEmpty() && !now.getFluidState().isStill()) return true;
        boolean g1 = now.isOf(net.minecraft.block.Blocks.GRASS_BLOCK) || now.isOf(net.minecraft.block.Blocks.DIRT);
        boolean g2 = was.isOf(net.minecraft.block.Blocks.GRASS_BLOCK) || was.isOf(net.minecraft.block.Blocks.DIRT);
        return g1 && g2;
    }

    private static int withData(List<PortCityLayout.Edit> l) {
        int n = 0;
        for (PortCityLayout.Edit e : l) if (e.nbt() != null) n++;
        return n;
    }

    public static int discard(ServerWorld w, String section) throws java.io.IOException {
        int n = PortCityLayout.edits().getOrDefault(section, List.of()).size();
        PortCityLayout.setEdits(section, List.of(), targets(w));
        return n;
    }

    public static int discardAll(ServerWorld w) throws java.io.IOException {
        int n = 0;
        for (String sec : new ArrayList<>(PortCityLayout.edits().keySet())) {
            n += PortCityLayout.edits().get(sec).size();
            PortCityLayout.setEdits(sec, List.of(), List.of());
        }
        PortCityLayout.saveEdits(targets(w));
        return n;
    }

    /** Same block and the same properties, ignoring the ones that change on their own. */
    public static boolean same(BlockState a, BlockState b) {
        if (a.getBlock() != b.getBlock()) return false;
        for (Property<?> p : a.getProperties()) {
            if (NOISE.contains(p.getName())) continue;
            if (!a.get(p).equals(b.get(p))) return false;
        }
        return true;
    }

    /** The block-entity data to save for a cell - null when it is uninteresting or exactly what the plan writes there. */
    static String edited(BlockEntity be, int x, int y, int z) {
        String d = data(be, PortCityLayout.lootAt(x, y, z) != null);
        String plan = PortCityLayout.planNbtAt(x, y, z);
        if (d == null || plan == null) return d;
        try {
            NbtCompound now = be.createNbt(), want = net.minecraft.nbt.StringNbtReader.parse(plan);
            for (String k : want.getKeys()) if (!want.get(k).equals(now.get(k))) return d;
            return null;
        } catch (Exception e) {
            return d;
        }
    }

    /** Block-entity data worth keeping: written text, banner patterns, contents, a held item, a book, a name, a head. */
    public static String data(BlockEntity be, boolean lootChest) {
        if (be == null) return null;
        NbtCompound n = be.createNbt();
        if (n.contains("SpawnData", NbtElement.COMPOUND_TYPE)) {                           // a spawner: its settings, not its countdown
            for (String k : new String[]{"x", "y", "z", "id", "Delay"}) n.remove(k);
            return n.toString();
        }
        boolean keep = false;
        if (n.contains("Items", NbtElement.LIST_TYPE) && !n.getList("Items", NbtElement.COMPOUND_TYPE).isEmpty() && !lootChest) keep = true;
        if (n.contains("Item", NbtElement.COMPOUND_TYPE) || n.contains("Book", NbtElement.COMPOUND_TYPE)) keep = true;
        if (n.contains("Patterns", NbtElement.LIST_TYPE) && !n.getList("Patterns", NbtElement.COMPOUND_TYPE).isEmpty()) keep = true;
        if (n.contains("CustomName") || n.contains("SkullOwner") || n.contains("note_block_sound")) keep = true;
        for (String side : new String[]{"front_text", "back_text"}) {
            if (!n.contains(side, NbtElement.COMPOUND_TYPE)) continue;
            NbtCompound t = n.getCompound(side);
            NbtList msgs = t.getList("messages", NbtElement.STRING_TYPE);
            for (int i = 0; i < msgs.size(); i++) {
                String m = msgs.getString(i);
                if (!m.equals("\"\"") && !m.equals("{\"text\":\"\"}")) keep = true;              // an empty line is either form
            }
            if (t.getBoolean("has_glowing_text") || !"black".equals(t.getString("color"))) keep = true;
        }
        return keep ? n.toString() : null;
    }

    /** Where the edits file is written: the game dir always, the dev workspace's resources when we run inside it. */
    static List<Path> targets(ServerWorld w) {
        List<Path> out = new ArrayList<>();
        Path run = w.getServer().getRunDirectory().toPath().toAbsolutePath();
        out.add(PortCityLayout.LOCAL_EDITS != null ? PortCityLayout.LOCAL_EDITS : run.resolve("pixelpirates/island_edits.txt"));
        Path parent = run.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("src/main/resources/data/pixelpirates")))
            out.add(parent.resolve("src/main/resources" + PortCityLayout.EDITS_RESOURCE));
        return out;
    }

    /** After a block is placed from the plan: give it its captured block-entity data. */
    public static void applyData(net.minecraft.world.WorldAccess world, BlockPos pos) {
        String nbt = PortCityLayout.nbtAt(pos.getX(), pos.getY(), pos.getZ());
        if (nbt == null) return;
        BlockEntity be = world.getBlockEntity(pos);
        if (be == null) return;
        try {
            be.readNbt(net.minecraft.nbt.StringNbtReader.parse(nbt));
            be.markDirty();
        } catch (Exception e) {
            net.get900.pixelpirates.PixelPirates.LOGGER.warn("[PixelPirates] bad island edit data at {}: {}", pos, e.getMessage());
        }
    }
}
