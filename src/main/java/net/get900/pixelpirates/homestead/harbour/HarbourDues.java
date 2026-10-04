package net.get900.pixelpirates.homestead.harbour;

import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.world.ShipHealthState;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.get900.pixelpirates.world.ShipTiers;
import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.LoadedServerShip;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HARBOUR DUES (2026-10-01). Wavebreak harbour (the bay south of the spawn island, PP dimension) charges berthing dues:
 * a player's ship may sail through or lie in the harbour for {@link #GRACE} ticks of standing still for free; after that,
 * unless it holds a valid PERMIT, the harbourmaster's men CHAIN ITS HELM (HELM_STEER inputs are ignored, see ModNetworking)
 * until the dues are paid - at a DUES LEDGER (the Dock Office) or any BERTH BOLLARD. The fee follows the hull: a tenth of
 * its commission price (ShipTiers), 3..30 doubloons; a permit lasts {@link #PERMIT_TICKS} (3 days). Every ship gets its
 * first day free the first time it is seen. A ship MOORED at a berth bollard (made fast there) with a valid permit is
 * repaired by the harbour crew (+{@link #REPAIR_HP} hull every 10 s). Leaving the harbour clears the standing-still count.
 */
public class HarbourDues extends PersistentState {
    public static final String KEY = "pixelpirates_harbour";
    public static final int INTERVAL = 40, GRACE = 2400, REPAIR_HP = 8;
    public static final long PERMIT_TICKS = 72000L, FIRST_VISIT = 24000L;
    /** The harbour: the bay south of the city (x -150..175, z 76..230). */
    public static final int HX1 = -150, HX2 = 175, HZ1 = 76, HZ2 = 230;

    private final Map<Long, Long> permitUntil = new HashMap<>();
    private final Map<Long, Integer> stillTicks = new HashMap<>();
    private final Map<Long, Long> berth = new HashMap<>();             // ship -> bollard pos it is made fast to
    /** Ships whose helm is chained right now (recomputed every pass). */
    public static final Set<Long> CLAMPED = ConcurrentHashMap.newKeySet();
    private static final Map<Long, Long> NAGGED = new ConcurrentHashMap<>();

    public static HarbourDues get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(HarbourDues::fromNbt, HarbourDues::new, KEY);
    }

    public static boolean inHarbour(double x, double z) { return x >= HX1 && x <= HX2 && z >= HZ1 && z <= HZ2; }

    public static boolean clamped(long shipId) { return CLAMPED.contains(shipId); }

    private static long now(MinecraftServer s) { return s.getOverworld().getTime(); }

    public boolean permitValid(MinecraftServer s, long shipId) { return permitUntil.getOrDefault(shipId, 0L) > now(s); }

    /** Dues for this hull in doubloons. */
    public static int fee(MinecraftServer s, long shipId) {
        String bp = ShipRegistryState.get(s.getOverworld()).getBlueprintName(shipId);
        int cost = bp == null ? 50 : ShipTiers.commissionCost(bp);
        return Math.max(3, Math.min(30, Math.round(cost / 10f)));
    }

    // ------------------------------------------------------------------ the harbour watch
    public static void tick(MinecraftServer server) {
        if (server.getTicks() % INTERVAL != 0) return;
        ServerWorld pp = server.getWorld(ModDimensions.PIXEL_PIRATES_WORLD);
        if (pp == null) return;
        VsiServerShipWorld shipWorld;
        try { shipWorld = VSGameUtilsKt.getShipObjectWorld(pp); } catch (Exception e) { return; }
        if (shipWorld == null) return;
        HarbourDues st = get(server);
        ShipRegistryState reg = ShipRegistryState.get(server.getOverworld());
        long now = now(server);
        Set<Long> clampedNow = new java.util.HashSet<>();
        for (LoadedServerShip ship : shipWorld.getLoadedShips().getIdToShipData().values()) {
            long id = ship.getId();
            UUID owner = reg.ownerOf(id);
            if (owner == null) continue;
            if (!st.permitUntil.containsKey(id)) { st.permitUntil.put(id, now + FIRST_VISIT); st.markDirty(); }
            Vector3dc p = ship.getTransform().getPositionInWorld();
            if (!inHarbour(p.x(), p.z())) { st.stillTicks.remove(id); st.berth.remove(id); continue; }
            boolean still = ship.getVelocity().length() < 0.6;
            int t = still ? Math.min(GRACE * 4, st.stillTicks.getOrDefault(id, 0) + INTERVAL) : st.stillTicks.getOrDefault(id, 0);
            st.stillTicks.put(id, t);
            boolean valid = st.permitUntil.getOrDefault(id, 0L) > now;
            if (t >= GRACE && !valid) {
                clampedNow.add(id);
                if (!CLAMPED.contains(id)) {
                    ServerPlayerEntity o = server.getPlayerManager().getPlayer(owner);
                    if (o != null) {
                        o.sendMessage(Text.literal("[Harbour] Your ship has lain in Wavebreak harbour without paying dues - the harbourmaster's men have CHAINED ITS HELM.")
                                .formatted(Formatting.GOLD), false);
                        o.sendMessage(Text.literal("  Dues: " + fee(server, id) + " doubloons for 3 days. Pay at the Dock Office ledger or any Berth Bollard.")
                                .formatted(Formatting.YELLOW), false);
                    }
                }
            }
            // the harbour crew mend a paid-up ship that is made fast at a berth
            if (valid && st.berth.containsKey(id) && ShipSteeringManager.ANCHORED_SHIPS.contains(id) && server.getTicks() % 200 == 0) {
                ShipHealthState hs = ShipHealthState.get(pp);
                if (hs.getHealth(id) < reg.getEffectiveMaxHp(id)) hs.repair(pp, id, REPAIR_HP);
            }
        }
        CLAMPED.clear();
        CLAMPED.addAll(clampedNow);
    }

    /** HELM_STEER hook: a chained helm does nothing (and says why, at most every 5 s). */
    public static boolean blockSteering(ServerPlayerEntity p, long shipId) {
        if (!CLAMPED.contains(shipId)) return false;
        long t = p.getServer().getTicks();
        Long last = NAGGED.get(shipId);
        if (last == null || t - last > 100) {
            NAGGED.put(shipId, t);
            p.sendMessage(Text.literal("The helm is chained! Harbour dues owed: " + fee(p.getServer(), shipId) + " doubloons - pay at a Berth Bollard or the Dock Office.")
                    .formatted(Formatting.RED), true);
        }
        return true;
    }

    // ------------------------------------------------------------------ paying, mooring, asking
    private static int doubloons(PlayerEntity p) {
        int n = 0;
        for (int i = 0; i < p.getInventory().size(); i++) { ItemStack s = p.getInventory().getStack(i); if (s.isOf(ModItems.COIN)) n += s.getCount(); }
        return n;
    }

    private static void takeDoubloons(PlayerEntity p, int n) {
        for (int i = 0; i < p.getInventory().size() && n > 0; i++) {
            ItemStack s = p.getInventory().getStack(i);
            if (!s.isOf(ModItems.COIN)) continue;
            int k = Math.min(n, s.getCount());
            s.decrement(k);
            n -= k;
        }
    }

    /** Pays the dues of the player's ship: a 3-day permit (stacking on an unexpired one). */
    public static void pay(ServerPlayerEntity p, BlockPos at) {
        MinecraftServer s = p.getServer();
        Long ship = ShipRegistryState.get(s.getOverworld()).getOwnedShip(p.getUuid());
        if (ship == null) { p.sendMessage(Text.literal("You have no ship on the harbour books.").formatted(Formatting.GRAY), true); return; }
        int fee = fee(s, ship);
        if (!p.getAbilities().creativeMode) {
            if (doubloons(p) < fee) { p.sendMessage(Text.literal("Harbour dues are " + fee + " doubloons - you have " + doubloons(p) + ".").formatted(Formatting.RED), true); return; }
            takeDoubloons(p, fee);
        }
        HarbourDues st = get(s);
        long base = Math.max(now(s), st.permitUntil.getOrDefault(ship, 0L));
        st.permitUntil.put(ship, base + PERMIT_TICKS);
        st.markDirty();
        CLAMPED.remove(ship);
        p.getWorld().playSound(null, at, SoundEvents.ENTITY_VILLAGER_YES, SoundCategory.BLOCKS, 0.6f, 1.1f);
        p.getWorld().playSound(null, at, SoundEvents.BLOCK_CHAIN_BREAK, SoundCategory.BLOCKS, 0.8f, 1.0f);
        p.sendMessage(Text.literal("Dues paid (" + fee + " doubloons) - your berth is good for " + daysLeft(s, st, ship) + ". Fair winds!").formatted(Formatting.GOLD), true);
    }

    private static String daysLeft(MinecraftServer s, HarbourDues st, long ship) {
        long left = st.permitUntil.getOrDefault(ship, 0L) - now(s);
        if (left <= 0) return "nothing";
        long h = left * 24 / 24000;
        return h >= 24 ? (h / 24) + " day" + (h / 24 == 1 ? "" : "s") + (h % 24 > 0 ? " " + (h % 24) + "h" : "") : h + "h";
    }

    /** The ledger / bollard without coins: where your ship stands with the harbour. */
    public static void status(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        Long ship = ShipRegistryState.get(s.getOverworld()).getOwnedShip(p.getUuid());
        if (ship == null) { p.sendMessage(Text.literal("Harbour dues are paid per ship - you have none on the books.").formatted(Formatting.GRAY), false); return; }
        HarbourDues st = get(s);
        boolean valid = st.permitValid(s, ship);
        p.sendMessage(Text.literal("[Harbour Ledger] Dues for your ship: " + fee(s, ship) + " doubloons / 3 days.").formatted(Formatting.GOLD), false);
        p.sendMessage(Text.literal(valid ? "  Paid up - " + daysLeft(s, st, ship) + " left." : CLAMPED.contains(ship) ? "  OWED - the helm is chained until you pay." : "  No permit - you may lie in the harbour a short while before dues are owed.")
                .formatted(valid ? Formatting.GREEN : Formatting.RED), false);
        p.sendMessage(Text.literal("  Pay: use doubloons on this ledger or a Berth Bollard. Made fast at a bollard with dues paid, the harbour crew mend your hull.")
                .formatted(Formatting.GRAY), false);
    }

    /** Berth bollard: make the player's ship fast here (anchored, recorded as berthed) if it lies within reach. */
    public static void moor(ServerPlayerEntity p, BlockPos bollard) {
        MinecraftServer s = p.getServer();
        Long ship = ShipRegistryState.get(s.getOverworld()).getOwnedShip(p.getUuid());
        if (ship == null) { p.sendMessage(Text.literal("You have no ship to make fast.").formatted(Formatting.GRAY), true); return; }
        LoadedServerShip ls = loaded(s, ship);
        if (ls == null) { p.sendMessage(Text.literal("Your ship is not in these waters.").formatted(Formatting.GRAY), true); return; }
        Vector3dc c = ls.getTransform().getPositionInWorld();
        double dx = c.x() - (bollard.getX() + 0.5), dz = c.z() - (bollard.getZ() + 0.5);
        if (dx * dx + dz * dz > 18 * 18) { p.sendMessage(Text.literal("Bring your ship alongside first (within 18 blocks of the bollard).").formatted(Formatting.YELLOW), true); return; }
        HarbourDues st = get(s);
        ShipSteeringManager.ANCHORED_SHIPS.add(ship);
        ShipSteeringManager.UNANCHOR_TIMERS.remove(ship);
        st.berth.put(ship, bollard.asLong());
        st.markDirty();
        p.getWorld().playSound(null, bollard, SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.BLOCKS, 1f, 0.9f);
        p.sendMessage(Text.literal(st.permitValid(s, ship) ? "Made fast. Dues are paid - the harbour crew will see to your hull." : "Made fast. Dues of " + fee(s, ship) + " doubloons are owed before you sail.")
                .formatted(Formatting.GOLD), true);
    }

    /** Sneak-use on a bollard: cast off. */
    public static void castOff(ServerPlayerEntity p) {
        MinecraftServer s = p.getServer();
        Long ship = ShipRegistryState.get(s.getOverworld()).getOwnedShip(p.getUuid());
        HarbourDues st = get(s);
        if (ship == null || !st.berth.containsKey(ship)) { p.sendMessage(Text.literal("Nothing is made fast here.").formatted(Formatting.GRAY), true); return; }
        st.berth.remove(ship);
        st.markDirty();
        ShipSteeringManager.ANCHORED_SHIPS.remove(ship);
        p.sendMessage(Text.literal(CLAMPED.contains(ship) ? "Cast off - but the helm stays chained until the dues are paid." : "Cast off. Fair winds!").formatted(Formatting.GOLD), true);
    }

    private static LoadedServerShip loaded(MinecraftServer s, long id) {
        ServerWorld pp = s.getWorld(ModDimensions.PIXEL_PIRATES_WORLD);
        if (pp == null) return null;
        try {
            VsiServerShipWorld w = VSGameUtilsKt.getShipObjectWorld(pp);
            return w == null ? null : w.getLoadedShips().getById(id);
        } catch (Exception e) { return null; }
    }

    /** /ppharbour: status of every player ship the watch knows. */
    public static java.util.List<String> report(MinecraftServer s) {
        HarbourDues st = get(s);
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Map.Entry<Long, Long> e : st.permitUntil.entrySet()) {
            long id = e.getKey();
            out.add("ship " + id + " permit " + (e.getValue() > now(s) ? (e.getValue() - now(s)) / 20 + "s" : "none") + " still " + st.stillTicks.getOrDefault(id, 0) / 20 + "s"
                    + (CLAMPED.contains(id) ? " CLAMPED" : "") + (st.berth.containsKey(id) ? " berthed" : ""));
        }
        return out;
    }

    /** Test hook: forget a ship's permit and pretend it has been lying still past the grace. */
    public void debugExpire(long id) { permitUntil.put(id, 0L); stillTicks.put(id, GRACE); markDirty(); }

    // ------------------------------------------------------------------ persistence
    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound p = new NbtCompound(), t = new NbtCompound(), b = new NbtCompound();
        permitUntil.forEach((k, v) -> p.putLong(Long.toString(k), v));
        stillTicks.forEach((k, v) -> t.putInt(Long.toString(k), v));
        berth.forEach((k, v) -> b.putLong(Long.toString(k), v));
        nbt.put("Permits", p);
        nbt.put("Still", t);
        nbt.put("Berths", b);
        return nbt;
    }

    public static HarbourDues fromNbt(NbtCompound nbt) {
        HarbourDues st = new HarbourDues();
        NbtCompound p = nbt.getCompound("Permits"), t = nbt.getCompound("Still"), b = nbt.getCompound("Berths");
        for (String k : p.getKeys()) try { st.permitUntil.put(Long.parseLong(k), p.getLong(k)); } catch (NumberFormatException ignored) {}
        for (String k : t.getKeys()) try { st.stillTicks.put(Long.parseLong(k), t.getInt(k)); } catch (NumberFormatException ignored) {}
        for (String k : b.getKeys()) try { st.berth.put(Long.parseLong(k), b.getLong(k)); } catch (NumberFormatException ignored) {}
        return st;
    }
}
