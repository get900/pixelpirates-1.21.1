package net.get900.pixelpirates.world;

import net.get900.pixelpirates.util.ModTags;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ShipHealthState extends PersistentState {

    public static final int MAX_HP = 500;
    private static final String KEY = "pixelpirates_ship_health";

    // Read by VS2 physics thread — must be ConcurrentHashMap
    public static final ConcurrentHashMap<Long, Boolean> SINKING_SHIPS = new ConcurrentHashMap<>();

    private static final int SCAN_RADIUS = 6;

    // How many blocks to remove when crossing each damage threshold
    private static int blocksForThreshold(int t) {
        return switch (t) {
            case 1 -> 3;   // 75% HP crossed
            case 2 -> 5;   // 50% HP crossed
            case 3 -> 7;   // 25% HP crossed
            case 4 -> 7;   // 0 HP — ship sinks
            default -> 0;
        };
    }

    // 0 = healthy (>75%), 1 = light (50–75%), 2 = moderate (25–50%), 3 = heavy (1–25%), 4 = sinking (0) - of THIS ship's max
    private static int getThreshold(int hp, int max) {
        if (hp >= max * 3 / 4) return 0;
        if (hp >= max / 2)     return 1;
        if (hp >= max / 4)     return 2;
        if (hp > 0)            return 3;
        return 4;
    }

    /** The world this state belongs to (set by get) - for each ship's own max HP. */
    private ServerWorld world;

    /** This ship's max hull: her size + hull refits (ShipRegistryState.getEffectiveMaxHp). */
    public int maxHp(long shipId) {
        return world == null ? MAX_HP : ShipRegistryState.get(world.getServer().getOverworld()).getEffectiveMaxHp(shipId);
    }

    private final Map<Long, Integer>        shipHealth   = new HashMap<>();
    private final Map<Long, Deque<Damaged>> removedBlocks = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    public int getHealth(long shipId) {
        Integer hp = shipHealth.get(shipId);
        return hp != null ? hp : maxHp(shipId);
    }

    /** Admin override — directly sets HP without triggering damage thresholds. */
    public void setHealth(long shipId, int hp) {
        shipHealth.put(shipId, Math.max(0, hp));
        SINKING_SHIPS.remove(shipId);
        markDirty();
    }

    public int damage(ServerWorld world, long shipId, BlockPos hitPos, int amount) {
        int armorLevel = ShipRegistryState.get(world.getServer().getOverworld()).getUpgradeLevel(shipId, "armor");
        if (armorLevel > 0) {
            amount = (int)(amount * (1.0 - armorLevel * 0.10));
            amount = Math.max(1, amount);
        }
        int before = getHealth(shipId);
        int after  = Math.max(0, before - amount);
        shipHealth.put(shipId, after);
        markDirty();

        int max = maxHp(shipId);
        int tBefore = getThreshold(before, max);
        int tAfter  = getThreshold(after, max);

        for (int t = tBefore + 1; t <= tAfter; t++) {
            applyStructuralDamage(world, shipId, hitPos, blocksForThreshold(t));
        }

        if (after == 0) SINKING_SHIPS.put(shipId, true);

        return after;
    }

    public int repair(ServerWorld world, long shipId, int amount) {
        int before = getHealth(shipId);
        int effectiveMax = ShipRegistryState.get(world.getServer().getOverworld()).getEffectiveMaxHp(shipId);
        int after  = Math.min(effectiveMax, before + amount);
        shipHealth.put(shipId, after);
        markDirty();

        SINKING_SHIPS.remove(shipId);

        int max = maxHp(shipId);
        int tBefore = getThreshold(before, max);
        int tAfter  = getThreshold(after, max);

        for (int t = tBefore; t > tAfter; t--) {
            restoreStructuralBlocks(world, shipId, blocksForThreshold(t));
        }

        return after;
    }

    // ── Structural damage helpers ─────────────────────────────────────────────

    private void applyStructuralDamage(ServerWorld world, long shipId, BlockPos near, int count) {
        List<BlockPos> candidates = findBreakableBlocks(world, shipId, near);
        Collections.shuffle(candidates);

        Deque<Damaged> deque = removedBlocks.computeIfAbsent(shipId, k -> new ArrayDeque<>());
        int removed = 0;
        for (BlockPos pos : candidates) {
            if (removed >= count) break;
            BlockState state = world.getBlockState(pos);
            if (state.isAir()) continue;
            String id = Registries.BLOCK.getId(state.getBlock()).toString();
            deque.addLast(new Damaged(pos.toImmutable(), id));
            world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
            removed++;
        }

        markDirty();
    }

    private void restoreStructuralBlocks(ServerWorld world, long shipId, int count) {
        Deque<Damaged> deque = removedBlocks.get(shipId);
        if (deque == null || deque.isEmpty()) return;

        int restored = 0;
        while (!deque.isEmpty() && restored < count) {
            Damaged d = deque.removeLast();
            BlockState state = Registries.BLOCK.get(new Identifier(d.blockId)).getDefaultState();
            world.setBlockState(d.pos, state, 3);
            restored++;
        }

        markDirty();
    }

    private static List<BlockPos> findBreakableBlocks(ServerWorld world, long shipId, BlockPos near) {
        List<BlockPos> result = new ArrayList<>();
        int r = SCAN_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r * r) continue;
                    BlockPos p = near.add(dx, dy, dz);
                    BlockState s = world.getBlockState(p);
                    if (s.isAir()) continue;
                    if (s.isIn(ModTags.Blocks.SHIP_STRUCTURAL)) continue;
                    Ship ship = ValkyrienSkies.getShipManagingBlock(world, p.getX(), p.getY(), p.getZ());
                    if (ship != null && ship.getId() == shipId) {
                        result.add(p.toImmutable());
                    }
                }
            }
        }
        return result;
    }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound healthMap = new NbtCompound();
        shipHealth.forEach((id, hp) -> healthMap.putInt(Long.toString(id), hp));
        nbt.put("ship_health", healthMap);

        NbtCompound removedMap = new NbtCompound();
        removedBlocks.forEach((shipId, deque) -> {
            NbtList list = new NbtList();
            for (Damaged d : deque) {
                NbtCompound entry = new NbtCompound();
                entry.putInt("x", d.pos.getX());
                entry.putInt("y", d.pos.getY());
                entry.putInt("z", d.pos.getZ());
                entry.putString("block", d.blockId);
                list.add(entry);
            }
            removedMap.put(Long.toString(shipId), list);
        });
        nbt.put("removed_blocks", removedMap);

        return nbt;
    }

    public static ShipHealthState fromNbt(NbtCompound nbt) {
        ShipHealthState state = new ShipHealthState();

        NbtCompound healthMap = nbt.getCompound("ship_health");
        for (String key : healthMap.getKeys()) {
            try {
                long id = Long.parseLong(key);
                int hp = healthMap.getInt(key);
                state.shipHealth.put(id, hp);
                if (hp == 0) SINKING_SHIPS.put(id, true);
            } catch (NumberFormatException ignored) {}
        }

        NbtCompound removedMap = nbt.getCompound("removed_blocks");
        for (String key : removedMap.getKeys()) {
            try {
                long id = Long.parseLong(key);
                NbtList list = removedMap.getList(key, NbtElement.COMPOUND_TYPE);
                Deque<Damaged> deque = new ArrayDeque<>();
                for (int i = 0; i < list.size(); i++) {
                    NbtCompound e = list.getCompound(i);
                    BlockPos pos = new BlockPos(e.getInt("x"), e.getInt("y"), e.getInt("z"));
                    deque.addLast(new Damaged(pos, e.getString("block")));
                }
                state.removedBlocks.put(id, deque);
            } catch (NumberFormatException ignored) {}
        }

        return state;
    }

    public static ShipHealthState get(ServerWorld world) {
        ShipHealthState s = world.getPersistentStateManager().getOrCreate(
            ShipHealthState::fromNbt,
            ShipHealthState::new,
            KEY
        );
        s.world = world;
        return s;
    }

    // ── Inner record ──────────────────────────────────────────────────────────

    private record Damaged(BlockPos pos, String blockId) {}
}
