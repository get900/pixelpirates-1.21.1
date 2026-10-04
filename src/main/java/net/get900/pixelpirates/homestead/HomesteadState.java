package net.get900.pixelpirates.homestead;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player homestead data, saved with the world (overworld data/pixelpirates_homestead.dat): hideout (flag pos,
 * dimension, tier), last death, logbook discoveries, bounty progress. Hideouts are also indexed for the no-spawn check.
 */
public class HomesteadState extends PersistentState {
    public record Hideout(BlockPos pos, RegistryKey<World> dim, int tier) {
        public int radius() { return tier >= 3 ? 64 : tier == 2 ? 48 : 32; }
    }

    private final Map<UUID, Hideout> hideouts = new HashMap<>();
    private final Map<UUID, BlockPos> deaths = new HashMap<>();
    private final Map<UUID, Set<String>> discoveries = new HashMap<>();
    private final Map<UUID, NbtCompound> bounties = new HashMap<>();
    private final Map<UUID, NbtCompound> tattoos = new HashMap<>();
    private final Map<UUID, NbtCompound> beards = new HashMap<>();                      // growth, style, colour (homestead/beard)                     // slot -> design (homestead/tattoo)

    public static HomesteadState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(HomesteadState::fromNbt, HomesteadState::new, "pixelpirates_homestead");
    }

    // ------------------------------------------------------------------ hideouts
    @Nullable
    public Hideout hideout(UUID p) { return hideouts.get(p); }

    public void setHideout(UUID p, @Nullable Hideout h) {
        if (h == null) hideouts.remove(p); else hideouts.put(p, h);
        markDirty();
    }

    public Map<UUID, Hideout> allHideouts() { return hideouts; }

    /** The hideout whose claim covers this spot, or null. */
    @Nullable
    public Hideout hideoutAt(RegistryKey<World> dim, BlockPos pos) {
        for (Hideout h : hideouts.values())
            if (h.dim().equals(dim) && h.pos().getSquaredDistance(pos.getX(), h.pos().getY(), pos.getZ()) <= (double) h.radius() * h.radius()) return h;
        return null;
    }

    @Nullable
    public UUID ownerOf(RegistryKey<World> dim, BlockPos flag) {
        for (var e : hideouts.entrySet()) if (e.getValue().dim().equals(dim) && e.getValue().pos().equals(flag)) return e.getKey();
        return null;
    }

    // ------------------------------------------------------------------ deaths
    @Nullable
    public BlockPos lastDeath(UUID p) { return deaths.get(p); }

    public void setLastDeath(UUID p, BlockPos pos) { deaths.put(p, pos.toImmutable()); markDirty(); }

    // ------------------------------------------------------------------ logbook
    public Set<String> discoveries(UUID p) { return discoveries.computeIfAbsent(p, k -> new HashSet<>()); }

    /** True the first time. */
    public boolean discover(UUID p, String key) {
        boolean added = discoveries(p).add(key);
        if (added) markDirty();
        return added;
    }

    // ------------------------------------------------------------------ bounties
    public NbtCompound bounty(UUID p) { return bounties.computeIfAbsent(p, k -> new NbtCompound()); }

    /** The player's tattoos, slot -> design id (live: change it, then touch()). */
    public NbtCompound tattoos(UUID p) { return tattoos.computeIfAbsent(p, k -> new NbtCompound()); }

    /** The player's facial hair {G growth ticks, S style, C colour, K keep clean} (live: change it, then markDirty()). */
    public NbtCompound beard(UUID p) { return beards.computeIfAbsent(p, k -> new NbtCompound()); }

    public void touch() { markDirty(); }

    // ------------------------------------------------------------------ one-off world flags
    private final Set<String> flags = new HashSet<>();

    public boolean flag(String k) { return flags.contains(k); }

    public void setFlag(String k) { if (flags.add(k)) markDirty(); }

    // ------------------------------------------------------------------ named numbers (e.g. the day the aviary was last stocked)
    private final Map<String, Long> numbers = new HashMap<>();

    public long number(String k, long def) { return numbers.getOrDefault(k, def); }

    public void setNumber(String k, long v) { numbers.put(k, v); markDirty(); }

    // ------------------------------------------------------------------ parrots away (Kraken's Pet, parrot types phase 3)
    private final Map<UUID, NbtCompound> parrotsAway = new HashMap<>();             // uuid -> {Parrot: shoulder nbt, Back: game time}

    public Map<UUID, NbtCompound> parrotsAway() { return parrotsAway; }

    // ------------------------------------------------------------------ Trading Post: items sold per player per day
    private final Map<UUID, long[]> postSales = new HashMap<>();          // uuid -> {day, items sold that day}

    /** Items this player has sold at Trading Posts on game day {@code day}. */
    public int postSold(UUID u, long day) { long[] v = postSales.get(u); return v == null || v[0] != day ? 0 : (int) v[1]; }

    public void addPostSold(UUID u, long day, int n) { postSales.put(u, new long[]{day, postSold(u, day) + n}); markDirty(); }

    // ------------------------------------------------------------------ persistence
    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList hl = new NbtList();
        hideouts.forEach((u, h) -> {
            NbtCompound c = new NbtCompound();
            c.putUuid("P", u);
            c.putLong("Pos", h.pos().asLong());
            c.putString("Dim", h.dim().getValue().toString());
            c.putInt("Tier", h.tier());
            hl.add(c);
        });
        nbt.put("Hideouts", hl);
        NbtList dl = new NbtList();
        deaths.forEach((u, p) -> { NbtCompound c = new NbtCompound(); c.putUuid("P", u); c.putLong("Pos", p.asLong()); dl.add(c); });
        nbt.put("Deaths", dl);
        NbtList disc = new NbtList();
        discoveries.forEach((u, s) -> {
            NbtCompound c = new NbtCompound();
            c.putUuid("P", u);
            NbtList l = new NbtList();
            for (String k : s) l.add(NbtString.of(k));
            c.put("K", l);
            disc.add(c);
        });
        nbt.put("Discoveries", disc);
        NbtList bl = new NbtList();
        bounties.forEach((u, b) -> { NbtCompound c = new NbtCompound(); c.putUuid("P", u); c.put("B", b); bl.add(c); });
        nbt.put("Bounties", bl);
        NbtList tl = new NbtList();
        tattoos.forEach((u, t) -> { if (!t.isEmpty()) { NbtCompound c = new NbtCompound(); c.putUuid("P", u); c.put("T", t); tl.add(c); } });
        nbt.put("Tattoos", tl);
        NbtList bdl = new NbtList();
        beards.forEach((u, b) -> { NbtCompound c = new NbtCompound(); c.putUuid("P", u); c.put("B", b); bdl.add(c); });
        nbt.put("Beards", bdl);
        NbtList fl = new NbtList();
        for (String f : flags) fl.add(NbtString.of(f));
        nbt.put("Flags", fl);
        NbtList ps = new NbtList();
        postSales.forEach((u, v) -> { NbtCompound c = new NbtCompound(); c.putUuid("P", u); c.putLong("D", v[0]); c.putLong("N", v[1]); ps.add(c); });
        nbt.put("PostSales", ps);
        NbtCompound nums = new NbtCompound();
        numbers.forEach(nums::putLong);
        nbt.put("Numbers", nums);
        NbtList away = new NbtList();
        parrotsAway.forEach((u, c) -> { NbtCompound e = c.copy(); e.putUuid("P", u); away.add(e); });
        nbt.put("ParrotsAway", away);
        return nbt;
    }

    public static HomesteadState fromNbt(NbtCompound nbt) {
        HomesteadState s = new HomesteadState();
        for (NbtElement e : nbt.getList("Hideouts", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            s.hideouts.put(c.getUuid("P"), new Hideout(BlockPos.fromLong(c.getLong("Pos")),
                    RegistryKey.of(RegistryKeys.WORLD, new Identifier(c.getString("Dim"))), c.getInt("Tier")));
        }
        for (NbtElement e : nbt.getList("Deaths", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            s.deaths.put(c.getUuid("P"), BlockPos.fromLong(c.getLong("Pos")));
        }
        for (NbtElement e : nbt.getList("Discoveries", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            Set<String> set = new HashSet<>();
            for (NbtElement k : c.getList("K", NbtElement.STRING_TYPE)) set.add(k.asString());
            s.discoveries.put(c.getUuid("P"), set);
        }
        for (NbtElement e : nbt.getList("Bounties", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            s.bounties.put(c.getUuid("P"), c.getCompound("B"));
        }
        for (NbtElement e : nbt.getList("Tattoos", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            s.tattoos.put(c.getUuid("P"), c.getCompound("T"));
        }
        for (NbtElement e : nbt.getList("Beards", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            s.beards.put(c.getUuid("P"), c.getCompound("B"));
        }
        for (NbtElement e : nbt.getList("Flags", NbtElement.STRING_TYPE)) s.flags.add(e.asString());
        NbtCompound nums = nbt.getCompound("Numbers");
        for (String k : nums.getKeys()) s.numbers.put(k, nums.getLong(k));
        for (NbtElement e : nbt.getList("ParrotsAway", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = ((NbtCompound) e).copy();
            UUID u = c.getUuid("P");
            c.remove("P");
            s.parrotsAway.put(u, c);
        }
        for (NbtElement e : nbt.getList("PostSales", NbtElement.COMPOUND_TYPE)) {
            NbtCompound c = (NbtCompound) e;
            s.postSales.put(c.getUuid("P"), new long[]{c.getLong("D"), c.getLong("N")});
        }
        return s;
    }
}
