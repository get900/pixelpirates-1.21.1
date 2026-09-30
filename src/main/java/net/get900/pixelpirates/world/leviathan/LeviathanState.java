package net.get900.pixelpirates.world.leviathan;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtLong;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The world's ONE Leviathan hunt (boss 10/10), saved with the Pixel Pirates dimension. The Leviathan entity only exists
 * while it is in a lair; everything that must survive between lairs lives here: the stage, its health (one bar across
 * the three phases), where it is on its flight, which ports it has destroyed (and which of their chunks have been
 * rewritten), what the Bane has been loaded with, and the spire horn's cooldown after a failed Last Tide.
 * Once SLAIN it never returns.
 */
public class LeviathanState extends PersistentState {
    public static final String KEY = "pixelpirates_leviathan";
    public static final int SLEEPING = 0, PHASE1 = 1, FLIGHT1 = 2, PHASE2 = 3, FLIGHT2 = 4, PHASE3 = 5, FLED = 6, SLAIN = 7;
    public static final String[] STAGE_NAMES = {"SLEEPING", "PHASE 1 (the Rift)", "FLEEING to the Gullet", "PHASE 2 (the Gullet)",
            "FLEEING to the Spire", "PHASE 3 (the Spire)", "FLED into the depths (horn)", "SLAIN"};

    public int stage = SLEEPING;
    /** Its health, carried between lairs (-1 = full). */
    public float health = -1;
    /** Blocks travelled along the current flight path. */
    public double flight;
    public final boolean[] portRuined = new boolean[2];
    /** Chunks of each port already rewritten to the ruin (ChunkPos longs). */
    @SuppressWarnings("unchecked")
    public final Set<Long>[] ruinedChunks = new Set[]{new HashSet<Long>(), new HashSet<Long>()};
    /** The four binding chains in the Rift (100 = whole, 0 = snapped). */
    public final int[] chains = {100, 100, 100, 100};
    /** Segments whose crust is broken (bit i = segment i). */
    public long crustBroken;
    // THE BANE
    public boolean baneHeartstone, baneShaft;
    public int banePowder;
    /** Server tick after which the spire's horn can call it back (after a failed Last Tide). */
    public long hornReadyAt;
    public int fails;
    /** The Last Tide: layers the Spire's lagoon has risen (so a reload can drain them). */
    public int tideLayers;
    /** The Spire's channel is (partly) filled with the collapsed arch's rubble. */
    public boolean channelSealed;
    /** The one living Leviathan entity (any other copy that loads discards itself). */
    public UUID leviathan;
    /** Everyone who has fought it (credited at the kill wherever they are). */
    public final Set<UUID> hunters = new LinkedHashSet<>();

    public static LeviathanState get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(LeviathanState::fromNbt, LeviathanState::new, KEY);
    }

    public boolean baneLoaded() { return baneHeartstone && baneShaft && banePowder >= BANE_POWDER; }

    public static final int BANE_POWDER = 16;

    public int phase() {
        return switch (stage) { case PHASE1 -> 1; case PHASE2, FLIGHT1 -> 2; case PHASE3, FLIGHT2, FLED -> 3; default -> 0; };
    }

    public void setStage(int s) { stage = s; markDirty(); }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.putInt("Stage", stage);
        nbt.putFloat("Health", health);
        nbt.putDouble("Flight", flight);
        for (int i = 0; i < 2; i++) {
            nbt.putBoolean("Ruined" + i, portRuined[i]);
            NbtList l = new NbtList();
            for (long c : ruinedChunks[i]) l.add(NbtLong.of(c));
            nbt.put("RuinedChunks" + i, l);
        }
        nbt.putIntArray("Chains", chains);
        nbt.putLong("Crust", crustBroken);
        nbt.putBoolean("BaneHeart", baneHeartstone);
        nbt.putBoolean("BaneShaft", baneShaft);
        nbt.putInt("BanePowder", banePowder);
        nbt.putLong("HornAt", hornReadyAt);
        nbt.putInt("Fails", fails);
        nbt.putInt("Tide", tideLayers);
        nbt.putBoolean("Sealed", channelSealed);
        NbtList h = new NbtList();
        for (UUID u : hunters) h.add(NbtHelper.fromUuid(u));
        nbt.put("Hunters", h);
        if (leviathan != null) nbt.putUuid("Leviathan", leviathan);
        return nbt;
    }

    public static LeviathanState fromNbt(NbtCompound nbt) {
        LeviathanState s = new LeviathanState();
        s.stage = nbt.getInt("Stage");
        s.health = nbt.contains("Health") ? nbt.getFloat("Health") : -1;
        s.flight = nbt.getDouble("Flight");
        for (int i = 0; i < 2; i++) {
            s.portRuined[i] = nbt.getBoolean("Ruined" + i);
            for (NbtElement e : nbt.getList("RuinedChunks" + i, NbtElement.LONG_TYPE)) s.ruinedChunks[i].add(((NbtLong) e).longValue());
        }
        int[] c = nbt.getIntArray("Chains");
        if (c.length == 4) System.arraycopy(c, 0, s.chains, 0, 4);
        s.crustBroken = nbt.getLong("Crust");
        s.baneHeartstone = nbt.getBoolean("BaneHeart");
        s.baneShaft = nbt.getBoolean("BaneShaft");
        s.banePowder = nbt.getInt("BanePowder");
        s.hornReadyAt = nbt.getLong("HornAt");
        s.fails = nbt.getInt("Fails");
        s.tideLayers = nbt.getInt("Tide");
        s.channelSealed = nbt.getBoolean("Sealed");
        for (NbtElement e : nbt.getList("Hunters", NbtElement.INT_ARRAY_TYPE)) s.hunters.add(NbtHelper.toUuid(e));
        if (nbt.containsUuid("Leviathan")) s.leviathan = nbt.getUuid("Leviathan");
        return s;
    }
}
