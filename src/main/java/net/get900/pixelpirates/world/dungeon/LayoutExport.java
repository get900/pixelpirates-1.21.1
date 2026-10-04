package net.get900.pixelpirates.world.dungeon;

import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.get900.pixelpirates.PixelPirates;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Turns a CODE-built dungeon into a layout file (2026-10-04: the user wanted /ppstruct save on the Tidewater Shrine and
 * the Smuggler's Grotto). The Java builder runs against a {@link Recorder} - nothing touches a world - and every cell it
 * writes becomes a run; chests/barrels with a loot table become loot markers, spawners an nbt entry, spawned mobs mob
 * markers. The builder's random weathering is frozen into one fixed variant (fixed seed). Cells beyond
 * {@link DungeonBuilder#MAX_REACH} are dropped (reported). Ground blending is switched off ({@code "blend": false}) -
 * these designs lay their own cave floors and shells, which blending would turn into grass.
 */
public final class LayoutExport {
    private LayoutExport() {}

    public record Result(int cells, int clipped, int loot, int spawners, int mobs, List<Path> files) {}

    /** A DungeonBuilder that writes into maps instead of a world (reads come back from the same maps). */
    static final class Recorder extends DungeonBuilder {
        final Map<Long, String> cells = new HashMap<>();
        final Map<Long, BlockState> states = new HashMap<>();
        final Map<Long, String> loot = new LinkedHashMap<>(), nbt = new HashMap<>();
        final List<LayoutStructures.Marker> mobs = new ArrayList<>();
        int clipped;

        Recorder(long seed) { super(null, BlockPos.ORIGIN, BlockRotation.NONE, Random.create(seed)); }

        private static boolean inReach(int x, int z) { return Math.abs(x) <= MAX_REACH && Math.abs(z) <= MAX_REACH; }

        @Override public void set(int x, int y, int z, BlockState state) {
            if (!inReach(x, z)) { clipped++; return; }
            long k = LayoutStructures.key(x, y, z);
            cells.put(k, BlockArgumentParser.stringifyBlockState(state));
            states.put(k, state);
            loot.remove(k); nbt.remove(k);                                  // a later block replaces a container/spawner
        }

        /** Unwritten cells read as the ground they would usually be: stone underground, air above. */
        @Override public BlockState get(int x, int y, int z) {
            BlockState s = states.get(LayoutStructures.key(x, y, z));
            return s != null ? s : y < 0 ? Blocks.STONE.getDefaultState() : Blocks.AIR.getDefaultState();
        }

        @Override public boolean isWater(int x, int y, int z) { return !get(x, y, z).getFluidState().isEmpty(); }

        @Override public void chest(int x, int y, int z, Direction facing, String lootTable) {
            set(x, y, z, Blocks.CHEST.getDefaultState().with(Properties.HORIZONTAL_FACING, facing).with(Properties.WATERLOGGED, isWater(x, y, z)));
            if (inReach(x, z)) loot.put(LayoutStructures.key(x, y, z), new Identifier(PixelPirates.MOD_ID, lootTable).toString());
        }

        @Override public void barrelLoot(int x, int y, int z, String lootTable) {
            set(x, y, z, Blocks.BARREL.getDefaultState().with(Properties.FACING, Direction.UP));
            if (inReach(x, z)) loot.put(LayoutStructures.key(x, y, z), new Identifier(PixelPirates.MOD_ID, lootTable).toString());
        }

        @Override public void spawner(int x, int y, int z, EntityType<?> type) {
            set(x, y, z, Blocks.SPAWNER.getDefaultState());
            if (inReach(x, z)) nbt.put(LayoutStructures.key(x, y, z), spawnerNbt(type).toString());
        }

        @Override public net.minecraft.entity.Entity spawnMob(String id, int x, int y, int z, java.util.function.Consumer<net.minecraft.entity.Entity> setup) {
            if (inReach(x, z)) mobs.add(new LayoutStructures.Marker(x, y, z, id));
            return null;
        }
    }

    /** Record {@code type}'s Java builder and write it as layout {@code type.id()} (game dir + dev resources, like a save). */
    public static Result export(Dungeons.Type type, int depth, String name, String origin) throws IOException {
        Recorder r = new Recorder(type.id().hashCode());
        type.builder().build(r, depth);
        JsonObject json = new JsonObject();
        json.addProperty("format", 1);
        json.addProperty("name", name);
        json.addProperty("minecraft", "1.20.1");
        json.addProperty("origin", origin);
        json.addProperty("blend", false);
        LayoutStructures.Layout l = new LayoutStructures.Layout(type.id(), json, new int[6]);
        l.mobs.addAll(r.mobs);
        String text = LayoutStructures.write(l, r.cells, r.loot, r.nbt);
        LayoutStructures.parse(type.id(), com.google.gson.JsonParser.parseString(text).getAsJsonObject());   // must load back
        List<Path> files = new ArrayList<>();
        files.add(LayoutStructures.localFile(type.id()));
        Path dev = FabricLoader.getInstance().getGameDir().toAbsolutePath().getParent();
        if (dev != null && Files.isDirectory(dev.resolve("src/main/resources/data/pixelpirates/layouts")))
            files.add(dev.resolve("src/main/resources/data/pixelpirates/layouts/" + type.id() + ".json"));
        for (Path f : files) {
            Files.createDirectories(f.getParent());
            Files.write(f, text.getBytes(StandardCharsets.UTF_8));
        }
        LayoutStructures.reload(type.id());
        return new Result(r.cells.size(), r.clipped, r.loot.size(), r.nbt.size(), r.mobs.size(), files);
    }
}
