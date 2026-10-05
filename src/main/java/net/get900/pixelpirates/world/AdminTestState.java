package net.get900.pixelpirates.world;

import net.get900.pixelpirates.world.faction.Faction;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Runtime-only admin/testing overrides. None of these are persisted — they reset on server restart.
 * Mutated by commands; read by the spawn loop and FactionManager.
 */
public final class AdminTestState {

    private AdminTestState() {}

    // ── Spawn loop overrides ──────────────────────────────────────────────────

    /** Override spawn interval in ticks. -1 = use default (2400). */
    public static int spawnRateOverride = -1;

    /** Override AI ship cap. -1 = use default (max(8, players*2)). */
    public static int capOverride = -1;

    /**
     * Server tick at which the next AI spawn attempt is allowed.
     * Updated by the spawn loop each time it fires; also reset by /ppai setrate.
     */
    public static long nextSpawnTick = 0;

    // ── Faction overrides ─────────────────────────────────────────────────────

    /**
     * Factions in this set are treated as always-hostile to all players,
     * regardless of their normal alwaysHostile flag or player reputation.
     * Toggled by /ppfaction hostile <faction>.
     */
    public static final Set<Faction> forcedHostile =
        Collections.synchronizedSet(EnumSet.noneOf(Faction.class));

    // ── Player test modes (/pptest) ───────────────────────────────────────────

    /** Players with god mode: invulnerable (mobs still target them), never hungry or burning. */
    public static final Set<java.util.UUID> godMode = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Players with clear sight: no zone fog/particles/hazards, no darkness/blindness/nausea, night vision. */
    public static final Set<java.util.UUID> clearSight = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** Players who see every page of the Weathered Chronicle (world/Chronicle). */
    public static final Set<java.util.UUID> chronicleAll = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Ticks between natural ship spawns: a minute for one player, faster with more (2026-10-05: was a flat 2 min). */
    public static int effectiveSpawnRate(int playerCount) {
        return spawnRateOverride > 0 ? spawnRateOverride : Math.max(400, 1200 / Math.max(1, playerCount));
    }

    public static int effectiveSpawnRate() { return effectiveSpawnRate(1); }

    public static int effectiveCap(int playerCount) {
        return capOverride > 0 ? capOverride : Math.max(8, playerCount * 2);
    }
}
