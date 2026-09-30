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

    private static final int MAX_BLOCKS = 2048;

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
