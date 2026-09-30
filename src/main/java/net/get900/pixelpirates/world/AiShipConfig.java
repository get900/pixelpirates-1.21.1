package net.get900.pixelpirates.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.get900.pixelpirates.PixelPirates;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Per-blueprint AI tuning parameters, loaded from
 * config/pixelpirates/ai/<blueprintName>.json at runtime.
 *
 * Any field omitted from the JSON file keeps its default value here,
 * so partial configs work fine. Future boss ships can set extreme values
 * (very low accuracySpread, high speedMult, 0 retreatHpFrac) without code changes.
 */
public class AiShipConfig {

    // ── Engagement distances ───────────────────────────────────────────────
    public double detectionRange  = 200.0;   // spot player → APPROACH
    public double chaseRange      = 250.0;   // give up chase if farther than this
    public double engageRange     = 55.0;    // switch APPROACH → BROADSIDE
    public double orbitRange      = 35.0;    // ideal orbit radius during BROADSIDE
    public double broadsideRange  = 50.0;    // max effective cannon range
    public double broadsideMin    = 20.0;    // too close — hold position

    // ── Combat ────────────────────────────────────────────────────────────
    public double alignThreshold  = 0.55;    // dot product: beam must face target within ~56°
    public double retreatHpFrac   = 0.15;    // retreat below this fraction of max HP
    public int    reloadTicks     = 100;     // base ticks between volleys (±10 jitter applied)
    /** Magnitude of random aim deviation per axis (higher = less accurate). */
    public float  accuracySpread  = 0.20f;

    // ── Movement ──────────────────────────────────────────────────────────
    /** Multiplier on forward thrust. At 0.85 the AI reaches 85 % of a sprinting player's speed. */
    public float  speedMult       = 0.85f;
    public float  patrolSpeed     = 0.35f;

    // ── Faction ───────────────────────────────────────────────────────────
    /**
     * Faction this ship belongs to. Must match a Faction.id:
     * "pirates" | "merchants" | "navy" | "undead"
     * Controls crew type, targeting behaviour, and player rep changes.
     */
    public String faction = "pirates";

    // ── Captain ───────────────────────────────────────────────────────────
    /** Max HP of the captain mob (scales with zone difficulty). */
    public double captainMaxHealth    = 60.0;
    /** Melee attack damage of the captain mob. */
    public double captainAttackDamage = 8.0;
    /** Armor points (each 2 pts = 1 armour bar). */
    public double captainArmor        = 4.0;

    // ── Singleton defaults (used when no file exists for a blueprint) ──────
    public static final AiShipConfig DEFAULTS = new AiShipConfig();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ── Loading ───────────────────────────────────────────────────────────

    /** Load config for the given blueprint. Returns DEFAULTS if no file exists. */
    public static AiShipConfig load(String blueprintName) {
        if (blueprintName == null || blueprintName.isEmpty()) return DEFAULTS;
        Path path = configPath(blueprintName);
        if (!Files.exists(path)) return DEFAULTS;
        try (Reader r = Files.newBufferedReader(path)) {
            AiShipConfig cfg = GSON.fromJson(r, AiShipConfig.class);
            return cfg != null ? cfg : DEFAULTS;
        } catch (IOException e) {
            PixelPirates.LOGGER.warn("[AiShipConfig] Failed to load '{}': {}", path, e.getMessage());
            return DEFAULTS;
        }
    }

    /** Write a config file only if one doesn't already exist. */
    public static void writeDefaultIfAbsent(String blueprintName, AiShipConfig config) {
        Path path = configPath(blueprintName);
        if (Files.exists(path)) return;
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(config, w);
            }
            PixelPirates.LOGGER.info("[AiShipConfig] Wrote default AI config → {}", path.getFileName());
        } catch (IOException e) {
            PixelPirates.LOGGER.warn("[AiShipConfig] Could not write default for '{}': {}", blueprintName, e.getMessage());
        }
    }

    /**
     * Writes starter config files for the three base ship types if they don't exist yet.
     * Call once on SERVER_STARTED. Players/admins can then edit these files to tune
     * difficulty without recompiling.
     */
    public static void generateDefaults() {
        // Sloop — easy zone-1 fodder, inaccurate, flees quickly
        AiShipConfig sloop = new AiShipConfig();
        sloop.speedMult      = 0.70f;
        sloop.engageRange    = 50.0;
        sloop.orbitRange     = 38.0;
        sloop.broadsideRange = 45.0;
        sloop.broadsideMin   = 22.0;
        sloop.alignThreshold = 0.50;
        sloop.reloadTicks    = 140;
        sloop.accuracySpread = 0.35f;
        sloop.retreatHpFrac  = 0.35;
        sloop.patrolSpeed    = 0.30f;
        writeDefaultIfAbsent("sloop", sloop);

        // Skipper — moderate zone-1/2 ship, needs effort to capture
        AiShipConfig skipper = new AiShipConfig();
        skipper.speedMult      = 0.82f;
        skipper.engageRange    = 55.0;
        skipper.orbitRange     = 34.0;
        skipper.broadsideRange = 50.0;
        skipper.broadsideMin   = 20.0;
        skipper.alignThreshold = 0.55;
        skipper.reloadTicks    = 110;
        skipper.accuracySpread = 0.20f;
        skipper.retreatHpFrac  = 0.20;
        skipper.patrolSpeed    = 0.35f;
        writeDefaultIfAbsent("skipper", skipper);

        // Brigantine — hard zone-2/3 ship, accurate and aggressive
        AiShipConfig brig = new AiShipConfig();
        brig.speedMult      = 0.90f;
        brig.engageRange    = 62.0;
        brig.orbitRange     = 30.0;
        brig.broadsideRange = 55.0;
        brig.broadsideMin   = 18.0;
        brig.alignThreshold = 0.60;
        brig.reloadTicks    = 85;
        brig.accuracySpread = 0.10f;
        brig.retreatHpFrac  = 0.08;
        brig.patrolSpeed    = 0.40f;
        writeDefaultIfAbsent("brigantine", brig);
    }

    private static Path configPath(String blueprintName) {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("pixelpirates")
            .resolve("ai")
            .resolve(blueprintName.toLowerCase() + ".json");
    }
}
