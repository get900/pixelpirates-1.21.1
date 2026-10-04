package net.get900.pixelpirates.world;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import org.joml.Vector3d;

import java.util.*;
import java.util.UUID;

/**
 * Persists ship registration data so MAST_COUNTS and AI_SHIPS survive server restarts.
 * Saved to the overworld's PersistentStateManager (always available).
 */
public class ShipRegistryState extends PersistentState {

    private static final String KEY = "pixelpirates_ship_registry";

    // shipId -> mastCount for ALL registered ships (player and AI)
    private final Map<Long, Integer>        mastCounts        = new HashMap<>();
    // shipId -> cannon positions (ship-space) — only present for AI ships
    private final Map<Long, List<double[]>> aiCannonPositions = new HashMap<>();
    // shipId -> upgrade key -> level
    private final Map<Long, Map<String, Integer>> shipUpgrades = new HashMap<>();
    // player UUID -> owned shipId (one active ship per player)
    private final Map<UUID, Long> playerOwnedShips = new HashMap<>();
    // shipId -> per-ship buoyancy targetY set when a ShipWaterlineBlock is present
    private final Map<Long, Double> waterlineOffsets = new HashMap<>();
    // shipId -> blueprint name for AI ships (used to award zone unlock on capture)
    private final Map<Long, String> shipBlueprintNames = new HashMap<>();
    // shipId -> ship-yard positions of blocks players added after assembly (world/ShipBuilding)
    private final Map<Long, Set<Long>> customBlocks = new HashMap<>();

    /** Live set of a ship's player-added blocks (callers mark dirty after changing it). */
    public Set<Long> customBlocks(long shipId) { return customBlocks.computeIfAbsent(shipId, k -> new HashSet<>()); }

    // ── Write API ─────────────────────────────────────────────────────────────

    public void saveMastCount(long shipId, int count) {
        mastCounts.put(shipId, count);
        aiCannonPositions.remove(shipId); // ensure it's not marked as AI
        markDirty();
    }

    public void saveAiShip(long shipId, int mastCount, List<Vector3d> cannons) {
        mastCounts.put(shipId, mastCount);
        List<double[]> saved = new ArrayList<>();
        for (Vector3d v : cannons) saved.add(new double[]{v.x, v.y, v.z});
        aiCannonPositions.put(shipId, saved);
        markDirty();
    }

    /** Called when an AI ship is claimed by a player — it becomes a player ship. */
    public void convertToPlayerShip(long shipId) {
        aiCannonPositions.remove(shipId);
        // mastCounts entry is kept — the ship still needs physics
        markDirty();
    }

    public int getUpgradeLevel(long shipId, String key) {
        Map<String, Integer> u = shipUpgrades.get(shipId);
        return u == null ? 0 : u.getOrDefault(key, 0);
    }

    public Map<String, Integer> getShipUpgrades(long shipId) {
        return java.util.Collections.unmodifiableMap(
            shipUpgrades.getOrDefault(shipId, java.util.Collections.emptyMap()));
    }

    public int getEffectiveMaxHp(long shipId) {
        return ShipHealthState.MAX_HP + getUpgradeLevel(shipId, "hull") * 100;
    }

    public void setUpgradeLevel(long shipId, String key, int level) {
        shipUpgrades.computeIfAbsent(shipId, k -> new HashMap<>()).put(key, level);
        markDirty();
    }

    // ── Blueprint names ───────────────────────────────────────────────────────

    public void saveBlueprintName(long shipId, String name) {
        shipBlueprintNames.put(shipId, name);
        markDirty();
    }

    public String getBlueprintName(long shipId) {
        return shipBlueprintNames.get(shipId);
    }

    // ── Waterline ─────────────────────────────────────────────────────────────

    public void saveWaterlineOffset(long shipId, double targetY) {
        waterlineOffsets.put(shipId, targetY);
        markDirty();
    }

    // ── Ownership ─────────────────────────────────────────────────────────────

    public void setOwnership(UUID playerUuid, long shipId) {
        playerOwnedShips.put(playerUuid, shipId);
        markDirty();
    }

    /** The player who owns this ship, or null (AI ships, derelicts). */
    public UUID ownerOf(long shipId) {
        for (Map.Entry<UUID, Long> e : playerOwnedShips.entrySet()) if (e.getValue() == shipId) return e.getKey();
        return null;
    }

    public Long getOwnedShip(UUID playerUuid) {
        return playerOwnedShips.get(playerUuid);
    }

    public void clearOwnership(UUID playerUuid) {
        playerOwnedShips.remove(playerUuid);
        markDirty();
    }

    public void removeShip(long shipId) {
        mastCounts.remove(shipId);
        aiCannonPositions.remove(shipId);
        shipUpgrades.remove(shipId);
        waterlineOffsets.remove(shipId);
        shipBlueprintNames.remove(shipId);
        customBlocks.remove(shipId);
        playerOwnedShips.entrySet().removeIf(e -> e.getValue() == shipId);
        markDirty();
    }

    // ── Restore ───────────────────────────────────────────────────────────────

    /**
     * Repopulates MAST_COUNTS and AI_SHIPS from saved data after a server restart.
     * ppWorld must be the Pixel Pirates dimension world.
     */
    public void restoreToMaps(ServerWorld ppWorld) {
        // Restore all mast counts
        mastCounts.forEach(ShipSteeringManager.MAST_COUNTS::put);
        // Restore waterline offsets
        waterlineOffsets.forEach(ShipSteeringManager.WATERLINE_OFFSETS::put);

        // Restore AI ships directly (bypassing registerAiShip to avoid re-saving)
        aiCannonPositions.forEach((shipId, positions) -> {
            List<Vector3d> cannons = new ArrayList<>();
            for (double[] p : positions) cannons.add(new Vector3d(p[0], p[1], p[2]));
            AiShipController.AiShipData data = new AiShipController.AiShipData(shipId, ppWorld);
            data.cannonShipPositions.addAll(cannons);
            data.blueprintName = shipBlueprintNames.getOrDefault(shipId, "");
            data.config        = AiShipConfig.load(data.blueprintName);
            AiShipController.AI_SHIPS.put(shipId, data);
        });

        int playerShips = mastCounts.size() - aiCannonPositions.size();
        PixelPirates.LOGGER.info("[ShipRegistry] Restored {} player ship(s) and {} AI ship(s)",
            playerShips, aiCannonPositions.size());
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound mastsTag = new NbtCompound();
        mastCounts.forEach((id, count) -> mastsTag.putInt(Long.toString(id), count));
        nbt.put("mast_counts", mastsTag);

        NbtCompound aiTag = new NbtCompound();
        aiCannonPositions.forEach((id, positions) -> {
            NbtList list = new NbtList();
            for (double[] pos : positions) {
                NbtCompound c = new NbtCompound();
                c.putDouble("x", pos[0]);
                c.putDouble("y", pos[1]);
                c.putDouble("z", pos[2]);
                list.add(c);
            }
            aiTag.put(Long.toString(id), list);
        });
        nbt.put("ai_cannons", aiTag);

        NbtCompound upgradesTag = new NbtCompound();
        shipUpgrades.forEach((id, map) -> {
            NbtCompound entry = new NbtCompound();
            map.forEach(entry::putInt);
            upgradesTag.put(Long.toString(id), entry);
        });
        nbt.put("ship_upgrades", upgradesTag);

        NbtCompound ownershipTag = new NbtCompound();
        playerOwnedShips.forEach((uuid, id) -> ownershipTag.putLong(uuid.toString(), id));
        nbt.put("player_owned_ships", ownershipTag);

        NbtCompound waterlineTag = new NbtCompound();
        waterlineOffsets.forEach((id, targetY) -> waterlineTag.putDouble(Long.toString(id), targetY));
        nbt.put("waterline_offsets", waterlineTag);

        NbtCompound blueprintTag = new NbtCompound();
        shipBlueprintNames.forEach((id, name) -> blueprintTag.putString(Long.toString(id), name));
        nbt.put("ship_blueprint_names", blueprintTag);

        NbtCompound customTag = new NbtCompound();
        customBlocks.forEach((id, set) -> { if (!set.isEmpty()) customTag.putLongArray(Long.toString(id), set.stream().mapToLong(Long::longValue).toArray()); });
        nbt.put("custom_blocks", customTag);

        return nbt;
    }

    public static ShipRegistryState fromNbt(NbtCompound nbt) {
        ShipRegistryState state = new ShipRegistryState();

        NbtCompound mastsTag = nbt.getCompound("mast_counts");
        for (String key : mastsTag.getKeys()) {
            try { state.mastCounts.put(Long.parseLong(key), mastsTag.getInt(key)); }
            catch (NumberFormatException ignored) {}
        }

        NbtCompound aiTag = nbt.getCompound("ai_cannons");
        for (String key : aiTag.getKeys()) {
            try {
                long id = Long.parseLong(key);
                NbtList list = aiTag.getList(key, NbtElement.COMPOUND_TYPE);
                List<double[]> positions = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound c = list.getCompound(i);
                    positions.add(new double[]{c.getDouble("x"), c.getDouble("y"), c.getDouble("z")});
                }
                state.aiCannonPositions.put(id, positions);
            } catch (NumberFormatException ignored) {}
        }

        NbtCompound upgradesTag = nbt.getCompound("ship_upgrades");
        for (String key : upgradesTag.getKeys()) {
            try {
                long id = Long.parseLong(key);
                NbtCompound entry = upgradesTag.getCompound(key);
                Map<String, Integer> map = new HashMap<>();
                for (String upKey : entry.getKeys()) map.put(upKey, entry.getInt(upKey));
                state.shipUpgrades.put(id, map);
            } catch (NumberFormatException ignored) {}
        }

        NbtCompound ownershipTag = nbt.getCompound("player_owned_ships");
        for (String key : ownershipTag.getKeys()) {
            try {
                state.playerOwnedShips.put(UUID.fromString(key), ownershipTag.getLong(key));
            } catch (IllegalArgumentException ignored) {}
        }

        NbtCompound waterlineTag = nbt.getCompound("waterline_offsets");
        for (String key : waterlineTag.getKeys()) {
            try { state.waterlineOffsets.put(Long.parseLong(key), waterlineTag.getDouble(key)); }
            catch (NumberFormatException ignored) {}
        }

        NbtCompound blueprintTag = nbt.getCompound("ship_blueprint_names");
        for (String key : blueprintTag.getKeys()) {
            try { state.shipBlueprintNames.put(Long.parseLong(key), blueprintTag.getString(key)); }
            catch (NumberFormatException ignored) {}
        }

        NbtCompound customTag = nbt.getCompound("custom_blocks");
        for (String key : customTag.getKeys()) {
            try {
                Set<Long> set = new HashSet<>();
                for (long p : customTag.getLongArray(key)) set.add(p);
                state.customBlocks.put(Long.parseLong(key), set);
            } catch (NumberFormatException ignored) {}
        }

        return state;
    }

    public static ShipRegistryState get(ServerWorld overworld) {
        return overworld.getPersistentStateManager().getOrCreate(
            ShipRegistryState::fromNbt, ShipRegistryState::new, KEY);
    }
}
