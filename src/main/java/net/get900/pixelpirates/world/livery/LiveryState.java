package net.get900.pixelpirates.world.livery;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Per ship: the livery it wears and, from its first repaint on, every paintable block's shipyard position, role and
 * ORIGINAL state (so any livery can be swapped for any other, or back to the original). PersistentState
 * "pixelpirates_liveries" on the overworld.
 */
public class LiveryState extends PersistentState {
    public static final class Paint {
        public String current = "";
        public long[] pos;
        public byte[] role;
        public int[] orig;
        public List<NbtCompound> palette;
    }

    private final Map<Long, Paint> ships = new HashMap<>();

    public static LiveryState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(LiveryState::fromNbt, LiveryState::new, "pixelpirates_liveries");
    }

    /** The livery id a ship wears ("" = its own colours). */
    public String current(long shipId) {
        Paint p = ships.get(shipId);
        return p == null ? "" : p.current;
    }

    @Nullable
    public Paint paint(long shipId) { return ships.get(shipId); }

    /** Store a fresh survey ({pos, role, original state} rows) for a ship. */
    Paint record(long shipId, List<Object[]> rows) {
        Paint p = new Paint();
        p.pos = new long[rows.size()]; p.role = new byte[rows.size()]; p.orig = new int[rows.size()];
        p.palette = new ArrayList<>();
        Map<BlockState, Integer> idx = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            BlockState s = (BlockState) r[2];
            p.pos[i] = (Long) r[0];
            p.role[i] = (byte) ((Liveries.Role) r[1]).ordinal();
            p.orig[i] = idx.computeIfAbsent(s, k -> { p.palette.add(Liveries.stateNbt(k)); return p.palette.size() - 1; });
        }
        ships.put(shipId, p);
        markDirty();
        return p;
    }

    /** A ship that no longer exists (scuttled, sunk) - forget it. */
    public void forget(long shipId) { if (ships.remove(shipId) != null) markDirty(); }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (var e : ships.entrySet()) {
            Paint p = e.getValue();
            NbtCompound c = new NbtCompound();
            c.putLong("Ship", e.getKey());
            c.putString("Current", p.current);
            c.putLongArray("Pos", p.pos);
            c.putByteArray("Role", p.role);
            c.putIntArray("Orig", p.orig);
            NbtList pal = new NbtList();
            pal.addAll(p.palette);
            c.put("Palette", pal);
            list.add(c);
        }
        nbt.put("Ships", list);
        return nbt;
    }

    public static LiveryState fromNbt(NbtCompound nbt) {
        LiveryState st = new LiveryState();
        NbtList list = nbt.getList("Ships", NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound c = list.getCompound(i);
            Paint p = new Paint();
            p.current = c.getString("Current");
            p.pos = c.getLongArray("Pos");
            p.role = c.getByteArray("Role");
            p.orig = c.getIntArray("Orig");
            p.palette = new ArrayList<>();
            NbtList pal = c.getList("Palette", NbtElement.COMPOUND_TYPE);
            for (int k = 0; k < pal.size(); k++) p.palette.add(pal.getCompound(k));
            st.ships.put(c.getLong("Ship"), p);
        }
        return st;
    }
}
