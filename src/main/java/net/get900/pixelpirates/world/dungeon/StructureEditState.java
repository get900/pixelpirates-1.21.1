package net.get900.pixelpirates.world.dungeon;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockArgumentParser;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structure editing (/ppstruct): the copies built with /ppdungeon (id, dimension, origin, rotation) and, for every spot a
 * player broke, placed or used a block at inside one of them (+ the 26 around it - door/bed halves), the block that was
 * there BEFORE the first change - so a save can tell a real edit from untouched terrain. Overworld saved data.
 */
public class StructureEditState extends PersistentState {
    private static final String KEY = "pixelpirates_structure_edits";

    public record Instance(String id, String dim, BlockPos origin, BlockRotation rot) {}

    final List<Instance> instances = new ArrayList<>();
    final Map<String, Map<Long, String>> before = new HashMap<>();          // dimension -> world pos -> state before

    public static StructureEditState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(StructureEditState::fromNbt, StructureEditState::new, KEY);
    }

    static StructureEditState fromNbt(NbtCompound nbt) {
        StructureEditState s = new StructureEditState();
        for (NbtElement e : nbt.getList("Instances", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            s.instances.add(new Instance(c.getString("Id"), c.getString("Dim"), BlockPos.fromLong(c.getLong("Origin")), BlockRotation.values()[c.getInt("Rot")]));
        }
        for (NbtElement e : nbt.getList("Before", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            s.before.computeIfAbsent(c.getString("Dim"), k -> new HashMap<>()).put(c.getLong("Pos"), c.getString("State"));
        }
        return s;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Instance i : instances) {
            NbtCompound c = new NbtCompound();
            c.putString("Id", i.id()); c.putString("Dim", i.dim()); c.putLong("Origin", i.origin().asLong()); c.putInt("Rot", i.rot().ordinal());
            list.add(c);
        }
        nbt.put("Instances", list);
        NbtList b = new NbtList();
        for (var d : before.entrySet())
            for (var e : d.getValue().entrySet()) {
                NbtCompound c = new NbtCompound();
                c.putString("Dim", d.getKey()); c.putLong("Pos", e.getKey()); c.putString("State", e.getValue());
                b.add(c);
            }
        nbt.put("Before", b);
        return nbt;
    }

    /** Remember a copy built for editing (replaces an older record at the same spot). */
    public void add(String id, World world, BlockPos origin, BlockRotation rot) {
        String dim = world.getRegistryKey().getValue().toString();
        instances.removeIf(i -> i.dim().equals(dim) && i.origin().equals(origin));
        instances.add(new Instance(id, dim, origin.toImmutable(), rot));
        markDirty();
    }

    public void remove(Instance i) {
        instances.remove(i);
        forgetEdits(i);
        markDirty();
    }

    /** The recorded copy nearest to pos (optionally only of one id), within maxDist blocks horizontally; null if none. */
    public Instance nearest(World world, BlockPos pos, String id, int maxDist) {
        String dim = world.getRegistryKey().getValue().toString();
        Instance best = null;
        double bd = (double) maxDist * maxDist;
        for (Instance i : instances) {
            if (!i.dim().equals(dim) || (id != null && !i.id().equals(id))) continue;
            double d = Math.pow(i.origin().getX() - pos.getX(), 2) + Math.pow(i.origin().getZ() - pos.getZ(), 2);
            if (d <= bd) { bd = d; best = i; }
        }
        return best;
    }

    public List<Instance> instances() { return List.copyOf(instances); }

    /** The before-states recorded inside one copy's reach (what a save compares touched cells against). */
    public Map<Long, String> beforeIn(Instance i) {
        Map<Long, String> out = new HashMap<>();
        Map<Long, String> m = before.get(i.dim());
        if (m == null) return out;
        for (var e : m.entrySet()) if (inReach(i, BlockPos.fromLong(e.getKey()))) out.put(e.getKey(), e.getValue());
        return out;
    }

    /** After a save: those spots are part of the layout now. */
    public void forgetEdits(Instance i) {
        Map<Long, String> m = before.get(i.dim());
        if (m != null) m.keySet().removeIf(l -> inReach(i, BlockPos.fromLong(l)));
        markDirty();
    }

    private static boolean inReach(Instance i, BlockPos p) {
        int dx = p.getX() - i.origin().getX(), dy = p.getY() - i.origin().getY(), dz = p.getZ() - i.origin().getZ();
        return Math.abs(dx) <= LayoutStructures.REACH + 1 && Math.abs(dz) <= LayoutStructures.REACH + 1 && dy >= -16 && dy <= 48;
    }

    /** A player is about to change pos (or just broke {@code brokenState} there): record the before-states once. */
    static void touch(World world, BlockPos p, BlockState brokenState) {
        if (world.isClient || world.getServer() == null) return;
        StructureEditState s = get(world.getServer());
        if (s.instances.isEmpty()) return;
        String dim = world.getRegistryKey().getValue().toString();
        boolean near = false;
        for (Instance i : s.instances) if (i.dim().equals(dim) && inReach(i, p)) { near = true; break; }
        if (!near) return;
        Map<Long, String> m = s.before.computeIfAbsent(dim, k -> new HashMap<>());
        for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
            BlockPos q = p.add(dx, dy, dz);
            BlockState st = (dx == 0 && dy == 0 && dz == 0 && brokenState != null) ? brokenState : world.getBlockState(q);
            m.putIfAbsent(q.asLong(), BlockArgumentParser.stringifyBlockState(st));
        }
        s.markDirty();
    }

    public static void init() {
        // BEFORE a break: the block is still there (AFTER would see air)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> { touch(world, pos, state); return true; });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            touch(world, hit.getBlockPos(), null);
            touch(world, hit.getBlockPos().offset(hit.getSide()), null);                    // where a placed block lands
            return ActionResult.PASS;
        });
    }
}
