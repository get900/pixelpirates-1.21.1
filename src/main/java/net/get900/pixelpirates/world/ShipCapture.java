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
 * around the helm if it wasn't placed with /ppship place) - never air, water/lava, or natural terrain (dirt, sand, stone,
 * gravel, seagrass, kelp...). Waterlogged blocks are saved dry.
 * <p>
 * MOVING THE HELM is fine (2026-10-04, user report: the helm was in a poor spot, moving it lost the ship): placements are
 * remembered on disk (survive a restart), capture looks for the helm ANYWHERE in the placed box (nearest to where it
 * was), and the blueprint is re-centred on the helm's new spot (it is always the origin). Without a remembered placement
 * the nearest helm within 40 blocks of you is used.
 */
public final class ShipCapture {
    private ShipCapture() {}

    private record Placed(RegistryKey<World> world, BlockPos helm, BlockBox box) {}
    private static final int MAX = 4096;

    /** The remembered placements (name -> where it was placed), saved with the world. */
    public static final class Placements extends net.minecraft.world.PersistentState {
        final Map<String, Placed> map = new HashMap<>();

        static Placements get(net.minecraft.server.MinecraftServer s) {
            return s.getOverworld().getPersistentStateManager().getOrCreate(Placements::fromNbt, Placements::new, "pixelpirates_ship_placements");
        }

        void put(String name, Placed p) { map.put(name, p); markDirty(); }

        void remove(String name) { if (map.remove(name) != null) markDirty(); }

        @Override
        public NbtCompound writeNbt(NbtCompound nbt) {
            NbtList list = new NbtList();
            for (var e : map.entrySet()) {
                NbtCompound c = new NbtCompound();
                c.putString("Name", e.getKey());
                c.putString("Dim", e.getValue().world().getValue().toString());
                c.putLong("Helm", e.getValue().helm().asLong());
                BlockBox b = e.getValue().box();
                c.putIntArray("Box", new int[]{b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ()});
                list.add(c);
            }
            nbt.put("Placed", list);
            return nbt;
        }

        static Placements fromNbt(NbtCompound nbt) {
            Placements p = new Placements();
            NbtList list = nbt.getList("Placed", net.minecraft.nbt.NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < list.size(); i++) {
                NbtCompound c = list.getCompound(i);
                int[] b = c.getIntArray("Box");
                if (b.length != 6) continue;
                p.map.put(c.getString("Name"), new Placed(RegistryKey.of(net.minecraft.registry.RegistryKeys.WORLD, new net.minecraft.util.Identifier(c.getString("Dim"))),
                        BlockPos.fromLong(c.getLong("Helm")), new BlockBox(b[0], b[1], b[2], b[3], b[4], b[5])));
            }
            return p;
        }
    }

    /** Where the ship to capture/remove is: {helm, box, note} or a String error. */
    private static Object locate(ServerPlayerEntity p, String name) {
        ServerWorld w = p.getServerWorld();
        Placed pl = Placements.get(p.getServer()).map.get(name);
        if (pl != null && pl.world().equals(w.getRegistryKey())) {
            if (w.getBlockState(pl.helm()).isOf(ModBlocks.SHIP_HELM)) return new Object[]{pl.helm(), pl.box(), ""};
            BlockPos best = null; double bd = Double.MAX_VALUE;                       // the helm was moved: look through the box
            BlockBox b = pl.box();
            for (BlockPos q : BlockPos.iterate(b.getMinX(), b.getMinY(), b.getMinZ(), b.getMaxX(), b.getMaxY(), b.getMaxZ())) {
                if (!w.getBlockState(q).isOf(ModBlocks.SHIP_HELM)) continue;
                double d = q.getSquaredDistance(pl.helm());
                if (d < bd) { bd = d; best = q.toImmutable(); }
            }
            if (best != null) {
                BlockPos d = best.subtract(pl.helm());
                return new Object[]{best, pl.box(), " (the helm moved " + d.getX() + "," + d.getY() + "," + d.getZ() + " - the blueprint is re-centred on it)"};
            }
        }
        BlockPos helm = nearestHelm(w, p.getBlockPos());
        if (helm == null) return "No ship helm found - stand within 40 blocks of the helm (or /ppship place " + name + " first).";
        return new Object[]{helm, new BlockBox(helm.getX() - 48, helm.getY() - 32, helm.getZ() - 48, helm.getX() + 48, helm.getY() + 40, helm.getZ() + 48), ""};
    }

    /** /ppship place: remember where a blueprint went so capture can find it again. */
    public static void recordPlace(ServerWorld w, String name, BlockPos helm, ShipSchematic sc) {
        int x0 = 0, y0 = 0, z0 = 0, x1 = 0, y1 = 0, z1 = 0;
        for (var e : sc.getEntries()) {
            BlockPos r = e.relPos();
            x0 = Math.min(x0, r.getX()); y0 = Math.min(y0, r.getY()); z0 = Math.min(z0, r.getZ());
            x1 = Math.max(x1, r.getX()); y1 = Math.max(y1, r.getY()); z1 = Math.max(z1, r.getZ());
        }
        Placements.get(w.getServer()).put(name, new Placed(w.getRegistryKey(), helm.toImmutable(),
                new BlockBox(helm.getX() + x0 - 8, helm.getY() + y0 - 8, helm.getZ() + z0 - 8, helm.getX() + x1 + 8, helm.getY() + y1 + 8, helm.getZ() + z1 + 8)));
    }

    /** The result line for the command. */
    public static String capture(ServerPlayerEntity p, String name) throws IOException {
        ServerWorld w = p.getServerWorld();
        Object loc = locate(p, name);
        if (loc instanceof String err) return err;
        BlockPos helm = (BlockPos) ((Object[]) loc)[0];
        BlockBox box = (BlockBox) ((Object[]) loc)[1];
        String note = (String) ((Object[]) loc)[2];

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
        // remember the ship where it is now (the helm's new spot, a box round what was captured) for the next capture
        int x0 = 0, y0 = 0, z0 = 0, x1 = 0, y1 = 0, z1 = 0;
        for (var e : entries) {
            BlockPos r = e.relPos();
            x0 = Math.min(x0, r.getX()); y0 = Math.min(y0, r.getY()); z0 = Math.min(z0, r.getZ());
            x1 = Math.max(x1, r.getX()); y1 = Math.max(y1, r.getY()); z1 = Math.max(z1, r.getZ());
        }
        Placements.get(w.getServer()).put(name, new Placed(w.getRegistryKey(), helm,
                new BlockBox(helm.getX() + x0 - 8, helm.getY() + y0 - 8, helm.getZ() + z0 - 8, helm.getX() + x1 + 8, helm.getY() + y1 + 8, helm.getZ() + z1 + 8)));
        return "Captured " + name + note + ": " + entries.size() + " blocks, " + masts + " mast blocks" + (waterline ? "" : " - WARNING: no ship_waterline block (it sets the sea level)")
                + ". Saved to " + String.join(" + ", wrote) + ".";
    }

    /** /ppship remove: clear a placed (unassembled) ship from the world - the same blocks capture would save. */
    public static String remove(ServerPlayerEntity p, String name) {
        ServerWorld w = p.getServerWorld();
        Object loc = locate(p, name);
        if (loc instanceof String err) return err;
        BlockPos helm = (BlockPos) ((Object[]) loc)[0];
        BlockBox box = (BlockBox) ((Object[]) loc)[1];
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
        Placements.get(w.getServer()).remove(name);
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
        for (BlockPos p : BlockPos.iterate(at.add(-40, -40, -40), at.add(40, 40, 40))) {
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
