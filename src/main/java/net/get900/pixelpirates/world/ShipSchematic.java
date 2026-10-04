package net.get900.pixelpirates.world;

import net.fabricmc.loader.api.FabricLoader;
import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class ShipSchematic {

    private static final int MAX_BLOCKS = 4096;              // the flagships (tools/gen_fleet_ships.py) run 2-3k blocks

    private final List<Entry> entries;
    private final int mastCount;

    private ShipSchematic(List<Entry> entries, int mastCount) {
        this.entries = entries;
        this.mastCount = mastCount;
    }

    /** A schematic built in code (e.g. GhostShipDesign) rather than loaded from a saved blueprint file. */
    public static ShipSchematic of(List<Entry> entries, int mastCount) { return new ShipSchematic(entries, mastCount); }

    public List<Entry> getEntries() { return entries; }
    public int getMastCount()       { return mastCount; }

    // ── Save ──────────────────────────────────────────────────────────────────

    public static void save(ServerWorld world, Ship ship, BlockPos helmWorldPos, String name) throws IOException {
        List<Entry> entries = new ArrayList<>();
        int mastCount = 0;

        Set<Long> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        queue.add(helmWorldPos.toImmutable());
        visited.add(helmWorldPos.asLong());

        while (!queue.isEmpty() && entries.size() < MAX_BLOCKS) {
            BlockPos cur = queue.poll();
            BlockState state = world.getBlockState(cur);
            if (state.isAir()) continue;

            Ship blockShip = ValkyrienSkies.getShipManagingBlock(world, cur.getX(), cur.getY(), cur.getZ());
            if (blockShip == null || blockShip.getId() != ship.getId()) continue;

            BlockPos rel = cur.subtract(helmWorldPos);
            // NbtHelper.fromBlockState stores block name + all state properties (e.g. FACING)
            NbtCompound stateNbt = NbtHelper.fromBlockState(state);
            entries.add(new Entry(rel, stateNbt));
            if (state.isOf(ModBlocks.SHIP_MAST)) mastCount++;

            for (Direction dir : Direction.values()) {
                BlockPos neighbor = cur.offset(dir);
                long key = neighbor.asLong();
                if (!visited.contains(key) && !world.getBlockState(neighbor).isAir()) {
                    visited.add(key);
                    queue.add(neighbor.toImmutable());
                }
            }
        }

        NbtCompound root = new NbtCompound();
        root.putInt("mast_count", mastCount);
        NbtList list = new NbtList();
        for (Entry e : entries) {
            NbtCompound entry = new NbtCompound();
            entry.putInt("x", e.relPos().getX());
            entry.putInt("y", e.relPos().getY());
            entry.putInt("z", e.relPos().getZ());
            entry.put("state", e.stateNbt().copy());
            list.add(entry);
        }
        root.put("blocks", list);

        File dir = getSchematicDir().toFile();
        dir.mkdirs();
        NbtIo.writeCompressed(root, new File(dir, sanitize(name) + ".nbt"));
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    public static ShipSchematic load(String name) throws IOException {
        File file = new File(getSchematicDir().toFile(), sanitize(name) + ".nbt");
        if (!file.exists()) throw new IOException("No blueprint named '" + name + "'");

        NbtCompound root = NbtIo.readCompressed(file);
        int mastCount = root.getInt("mast_count");
        NbtList list = root.getList("blocks", NbtElement.COMPOUND_TYPE);

        List<Entry> entries = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            NbtCompound e = list.getCompound(i);
            BlockPos rel = new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z"));
            entries.add(new Entry(rel, e.getCompound("state")));
        }

        return new ShipSchematic(entries, mastCount);
    }

    /**
     * The blueprints that ship with the mod (data/pixelpirates/ships/*.nbt in the jar: the hand-built sloop, skipper and
     * brigantine, the faction ships + the fleet from tools/gen_*_ships.py, anything saved with /ppship capture) are copied
     * into the config folder. UPDATES (2026-10-04): config/pixelpirates/ships/.bundled remembers the hash of each file as
     * installed; when the mod brings a NEW version of a blueprint and the installed copy is still the one we installed,
     * it is replaced (the old one kept as .nbt.bak). A copy you changed locally (re-saved with a Ship Blueprint) is kept.
     * Copies from before this tracking existed are treated as installs and updated (with the .bak).
     */
    public static void installBundled() {
        var mod = FabricLoader.getInstance().getModContainer(net.get900.pixelpirates.PixelPirates.MOD_ID).orElse(null);
        if (mod == null) return;
        var src = mod.findPath("data/pixelpirates/ships").orElse(null);
        if (src == null) return;
        Path dir = getSchematicDir();
        java.util.Properties seen = loadInstalled();
        try (var files = java.nio.file.Files.list(src)) {
            java.nio.file.Files.createDirectories(dir);
            for (Path f : (Iterable<Path>) files::iterator) {
                String n = f.getFileName().toString();
                if (!n.endsWith(".nbt")) continue;
                Path to = dir.resolve(n);
                String bundled = hash(f);
                if (!java.nio.file.Files.exists(to)) {
                    java.nio.file.Files.copy(f, to);
                    net.get900.pixelpirates.PixelPirates.LOGGER.info("[Ships] Installed bundled blueprint {}", n);
                } else {
                    String installed = hash(to), recorded = seen.getProperty(n);
                    if (installed.equals(bundled)) { seen.setProperty(n, bundled); continue; }
                    if (recorded != null && !recorded.equals(installed)) {
                        net.get900.pixelpirates.PixelPirates.LOGGER.info("[Ships] Kept your edited blueprint {} (the mod has a newer one)", n);
                        continue;
                    }
                    java.nio.file.Files.copy(to, dir.resolve(n + ".bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    java.nio.file.Files.copy(f, to, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    net.get900.pixelpirates.PixelPirates.LOGGER.info("[Ships] Updated bundled blueprint {} (old copy kept as {}.bak)", n, n);
                }
                seen.setProperty(n, bundled);
            }
            saveInstalled(seen);
        } catch (IOException e) {
            net.get900.pixelpirates.PixelPirates.LOGGER.warn("[Ships] Could not install bundled blueprints: {}", e.toString());
        }
    }

    /** Mark a blueprint file in the config folder as matching the mod's version (ShipCapture writes both at once). */
    public static void recordInstalled(String fileName, Path file) {
        try {
            java.util.Properties seen = loadInstalled();
            seen.setProperty(fileName, hash(file));
            saveInstalled(seen);
        } catch (IOException ignored) { }
    }

    private static java.util.Properties loadInstalled() {
        java.util.Properties p = new java.util.Properties();
        Path f = getSchematicDir().resolve(".bundled");
        if (java.nio.file.Files.exists(f)) try (var r = java.nio.file.Files.newBufferedReader(f)) { p.load(r); } catch (IOException ignored) { }
        return p;
    }

    private static void saveInstalled(java.util.Properties p) throws IOException {
        try (var w = java.nio.file.Files.newBufferedWriter(getSchematicDir().resolve(".bundled"))) { p.store(w, "hashes of the bundled ship blueprints as installed"); }
    }

    private static String hash(Path f) throws IOException {
        java.util.zip.CRC32 c = new java.util.zip.CRC32();
        c.update(java.nio.file.Files.readAllBytes(f));
        return Long.toHexString(c.getValue());
    }

    /** config/pixelpirates/ships */
    public static Path schematicDir() { return getSchematicDir(); }

    public static List<String> listNames() {
        File dir = getSchematicDir().toFile();
        if (!dir.exists()) return List.of();
        String[] files = dir.list((d, n) -> n.endsWith(".nbt"));
        if (files == null) return List.of();
        List<String> names = new ArrayList<>();
        for (String f : files) names.add(f.substring(0, f.length() - 4));
        return names;
    }

    // ── BlockState restore ─────────────────────────────────────────────────────

    // Restores the full BlockState from an NbtCompound produced by NbtHelper.fromBlockState().
    // Applies properties individually to handle version differences without needing a registry lookup.
    public static BlockState restoreState(NbtCompound stateNbt) {
        String blockId = stateNbt.getString("Name");
        Block block = Registries.BLOCK.get(new Identifier(blockId));
        BlockState state = block.getDefaultState();

        if (stateNbt.contains("Properties", NbtElement.COMPOUND_TYPE)) {
            NbtCompound props = stateNbt.getCompound("Properties");
            for (Property<?> prop : block.getStateManager().getProperties()) {
                if (props.contains(prop.getName())) {
                    state = applyProperty(state, prop, props.getString(prop.getName()));
                }
            }
        }
        return state;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState applyProperty(BlockState state, Property<T> prop, String value) {
        return prop.parse(value).map(v -> state.with(prop, v)).orElse(state);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    // Stored in .minecraft/config/pixelpirates/ships/ — global across all worlds.
    private static Path getSchematicDir() {
        return FabricLoader.getInstance().getConfigDir()
                           .resolve("pixelpirates")
                           .resolve("ships");
    }

    public static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT)
                   .replaceAll("[^a-z0-9_\\-]", "_")
                   .replaceAll("_+", "_")
                   .replaceAll("^_|_$", "");
    }

    // ── Record ────────────────────────────────────────────────────────────────

    public record Entry(BlockPos relPos, NbtCompound stateNbt) {}
}
