package net.get900.pixelpirates.world;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FluidBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * SHIP CAPTURE (2026-10-04) - edit a ship blueprint by hand in the world and save it back, like /ppisland capture:
 * <pre>
 *   /ppship place &lt;name&gt;     stamps the blueprint as plain blocks (helm 12 east of you, bow +Z) and remembers where
 *   ...build...                (no assembling needed)
 *   /ppship capture &lt;name&gt;   saves the blocks joined to that helm as blueprint &lt;name&gt; (a new name = a new ship)
 *   /ppship remove &lt;name&gt;    clears the placed ship away again (the same blocks capture saves)
 * </pre>
 * The save goes to config/pixelpirates/ships (the game you're in) AND, inside the dev workspace, to the mod's bundled
 * blueprints (src/main/resources/data/pixelpirates/ships) - so it ships with the mod, and {@link ShipSchematic#installBundled}
 * pushes the new version into existing worlds/servers on their next start. The name is also added to
 * ships/captured.txt there: tools/gen_ship_blueprints.py + gen_fleet_ships.py then skip that ship (unless --force),
 * so re-running a generator can't wipe a hand edit.
 * <p>
 * What counts as the ship: every block face-joined to the helm, inside the placed blueprint's box (+8 all round; or 48
 * around the helm if it wasn't placed this session) - never air, water/lava, or natural terrain (dirt, sand, stone,
 * gravel, seagrass, kelp...). Waterlogged blocks are saved dry.
 */
public final class ShipCapture {
    private ShipCapture() {}

    private record Placed(RegistryKey<World> world, BlockPos helm, BlockBox box) {}
    private static final Map<String, Placed> PLACED = new HashMap<>();
    private static final int MAX = 4096;

    /** /ppship place: remember where a blueprint went so capture can find it again. */
    public static void recordPlace(ServerWorld w, String name, BlockPos helm, ShipSchematic sc) {
        int x0 = 0, y0 = 0, z0 = 0, x1 = 0, y1 = 0, z1 = 0;
        for (var e : sc.getEntries()) {
            BlockPos r = e.relPos();
            x0 = Math.min(x0, r.getX()); y0 = Math.min(y0, r.getY()); z0 = Math.min(z0, r.getZ());
            x1 = Math.max(x1, r.getX()); y1 = Math.max(y1, r.getY()); z1 = Math.max(z1, r.getZ());
        }
        PLACED.put(name, new Placed(w.getRegistryKey(), helm.toImmutable(),
                new BlockBox(helm.getX() + x0 - 8, helm.getY() + y0 - 8, helm.getZ() + z0 - 8, helm.getX() + x1 + 8, helm.getY() + y1 + 8, helm.getZ() + z1 + 8)));
    }

    /** The result line for the command. */
    public static String capture(ServerPlayerEntity p, String name) throws IOException {
        ServerWorld w = p.getServerWorld();
        Placed pl = PLACED.get(name);
        BlockPos helm = null; BlockBox box = null;
        if (pl != null && pl.world == w.getRegistryKey() && w.getBlockState(pl.helm).isOf(ModBlocks.SHIP_HELM)) { helm = pl.helm; box = pl.box; }
        if (helm == null) helm = nearestHelm(w, p.getBlockPos());
        if (helm == null) return "No ship helm found - stand within 24 blocks of the helm (or /ppship place " + name + " first).";
        if (box == null) box = new BlockBox(helm.getX() - 48, helm.getY() - 32, helm.getZ() - 48, helm.getX() + 48, helm.getY() + 40, helm.getZ() + 48);

        // flood fill from the helm through ship blocks
        List<ShipSchematic.Entry> entries = new ArrayList<>();
        int masts = 0;
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(helm); seen.add(helm.asLong());
        while (!q.isEmpty()) {
            BlockPos c = q.poll();
            BlockState s = w.getBlockState(c);
            if (s.contains(Properties.WATERLOGGED)) s = s.with(Properties.WATERLOGGED, false);
            entries.add(new ShipSchematic.Entry(c.subtract(helm), NbtHelper.fromBlockState(s)));
            if (s.isOf(ModBlocks.SHIP_MAST)) masts++;
            if (entries.size() > MAX) return "Over " + MAX + " blocks are joined to the helm - is the ship touching land or another build?";
            for (Direction d : Direction.values()) {
                BlockPos n = c.offset(d);
                if (!box.contains(n) || !seen.add(n.asLong())) continue;
                if (isShipBlock(w.getBlockState(n))) q.add(n.toImmutable());
            }
        }
        boolean waterline = entries.stream().anyMatch(e -> e.stateNbt().getString("Name").equals("pixelpirates:ship_waterline"));
        List<String> wrote = write(name, entries, masts, w);
        return "Captured " + name + ": " + entries.size() + " blocks, " + masts + " mast blocks" + (waterline ? "" : " - WARNING: no ship_waterline block (it sets the sea level)")
                + ". Saved to " + String.join(" + ", wrote) + ".";
    }

    /** /ppship remove: clear a placed (unassembled) ship from the world - the same blocks capture would save. */
    public static String remove(ServerPlayerEntity p, String name) {
        ServerWorld w = p.getServerWorld();
        Placed pl = PLACED.get(name);
        BlockPos helm = null; BlockBox box = null;
        if (pl != null && pl.world == w.getRegistryKey() && w.getBlockState(pl.helm).isOf(ModBlocks.SHIP_HELM)) { helm = pl.helm; box = pl.box; }
        if (helm == null) helm = nearestHelm(w, p.getBlockPos());
        if (helm == null) return "No ship helm found within 24 blocks.";
        if (box == null) box = new BlockBox(helm.getX() - 48, helm.getY() - 32, helm.getZ() - 48, helm.getX() + 48, helm.getY() + 40, helm.getZ() + 48);
        List<BlockPos> all = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<BlockPos> q = new ArrayDeque<>();
        q.add(helm); seen.add(helm.asLong());
        while (!q.isEmpty() && all.size() <= MAX) {
            BlockPos c = q.poll();
            all.add(c);
            for (Direction d : Direction.values()) {
                BlockPos n = c.offset(d);
                if (box.contains(n) && seen.add(n.asLong()) && isShipBlock(w.getBlockState(n))) q.add(n.toImmutable());
            }
        }
        if (all.size() > MAX) return "Over " + MAX + " blocks are joined to the helm - not removing (is it touching land or another build?).";
        for (int i = all.size() - 1; i >= 0; i--) w.setBlockState(all.get(i), Blocks.AIR.getDefaultState(), 2 | 16);
        PLACED.remove(name);
        return "Removed the placed ship (" + all.size() + " blocks).";
    }

    /** Ship material: anything solid that isn't air, fluid or natural ground. */
    static boolean isShipBlock(BlockState s) {
        if (s.isAir() || s.getBlock() instanceof FluidBlock) return false;
        if (s.isIn(BlockTags.DIRT) || s.isIn(BlockTags.SAND) || s.isIn(BlockTags.BASE_STONE_OVERWORLD) || s.isIn(BlockTags.CORAL_BLOCKS)
                || s.isIn(BlockTags.CORALS) || s.isIn(BlockTags.WALL_CORALS)) return false;
        return !(s.isOf(Blocks.GRAVEL) || s.isOf(Blocks.CLAY) || s.isOf(Blocks.SEAGRASS) || s.isOf(Blocks.TALL_SEAGRASS) || s.isOf(Blocks.KELP)
                || s.isOf(Blocks.KELP_PLANT) || s.isOf(Blocks.GRASS) || s.isOf(Blocks.BEDROCK));
    }

    @Nullable
    private static BlockPos nearestHelm(ServerWorld w, BlockPos at) {
        BlockPos best = null; double bd = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.iterate(at.add(-24, -24, -24), at.add(24, 24, 24))) {
            if (!w.getBlockState(p).isOf(ModBlocks.SHIP_HELM)) continue;
            double d = p.getSquaredDistance(at);
            if (d < bd) { bd = d; best = p.toImmutable(); }
        }
        return best;
    }

    /** Write the blueprint to the config folder, and (dev workspace) the mod's bundle + captured.txt. */
    private static List<String> write(String name, List<ShipSchematic.Entry> entries, int masts, ServerWorld w) throws IOException {
        NbtCompound root = new NbtCompound();
        root.putInt("mast_count", masts);
        NbtList list = new NbtList();
        for (var e : entries) {
            NbtCompound c = new NbtCompound();
            c.putInt("x", e.relPos().getX()); c.putInt("y", e.relPos().getY()); c.putInt("z", e.relPos().getZ());
            c.put("state", e.stateNbt().copy());
            list.add(c);
        }
        root.put("blocks", list);
        String file = ShipSchematic.sanitize(name) + ".nbt";
        List<String> wrote = new ArrayList<>();
        Path cfg = ShipSchematic.schematicDir();
        Files.createDirectories(cfg);
        NbtIo.writeCompressed(root, cfg.resolve(file).toFile());
        ShipSchematic.recordInstalled(file, cfg.resolve(file));                 // our own save, not a stale install
        wrote.add("config/pixelpirates/ships");
        Path run = w.getServer().getRunDirectory().toPath().toAbsolutePath(), parent = run.getParent();
        if (parent != null && Files.isDirectory(parent.resolve("src/main/resources/data/pixelpirates"))) {
            Path bundle = parent.resolve("src/main/resources/data/pixelpirates/ships");
            Files.createDirectories(bundle);
            NbtIo.writeCompressed(root, bundle.resolve(file).toFile());
            Path cap = bundle.resolve("captured.txt");
            Set<String> names = new TreeSet<>(Files.exists(cap) ? Files.readAllLines(cap) : List.of());
            names.removeIf(s -> s.isBlank() || s.startsWith("#"));
            names.add(ShipSchematic.sanitize(name));
            List<String> out = new ArrayList<>();
            out.add("# Ships saved by hand with /ppship capture - tools/gen_ship_blueprints.py and gen_fleet_ships.py skip these unless --force.");
            out.addAll(names);
            Files.write(cap, out);
            wrote.add("the mod's bundled ships");
        }
        PixelPirates.LOGGER.info("[Ships] Captured blueprint {} ({} blocks) -> {}", name, entries.size(), wrote);
        return wrote;
    }
}
