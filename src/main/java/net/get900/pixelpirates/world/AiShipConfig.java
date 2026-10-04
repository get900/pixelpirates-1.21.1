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

    // ── Where it sails ────────────────────────────────────────────────────
    /** The zones (rings) this ship is sent to as an AI ship; -1 = use {@link #DEFAULT_ZONES} for its blueprint name. */
    public int minZone = -1;
    public int maxZone = -1;

    /** Built-in zone ranges {min, max} per blueprint (2026-10-04 fleet: each faction sails a small -> flagship ladder). */
    private static final java.util.Map<String, int[]> DEFAULT_ZONES = java.util.Map.ofEntries(
        java.util.Map.entry("sloop", new int[]{1, 1}), java.util.Map.entry("skipper", new int[]{1, 2}),
        java.util.Map.entry("brigantine", new int[]{2, 3}),
        java.util.Map.entry("pirate_cutter", new int[]{1, 2}), java.util.Map.entry("corsair_xebec", new int[]{2, 3}),
        java.util.Map.entry("pirate_brig", new int[]{3, 4}), java.util.Map.entry("pirate_galleon", new int[]{4, 99}),
        java.util.Map.entry("merchant_lugger", new int[]{1, 2}), java.util.Map.entry("merchantman", new int[]{1, 3}),
        java.util.Map.entry("merchant_fluyt", new int[]{2, 3}), java.util.Map.entry("merchant_indiaman", new int[]{3, 99}),
        java.util.Map.entry("navy_cutter", new int[]{1, 2}), java.util.Map.entry("navy_corvette", new int[]{2, 3}),
        java.util.Map.entry("navy_frigate", new int[]{3, 4}), java.util.Map.entry("navy_man_o_war", new int[]{5, 99}),
        java.util.Map.entry("undead_wraith", new int[]{3, 3}), java.util.Map.entry("undead_bone_galley", new int[]{3, 4}),
        java.util.Map.entry("drowned_hulk", new int[]{4, 5}), java.util.Map.entry("undead_phantom_galleon", new int[]{5, 99}));

    /** {min, max} zone for this blueprint: the JSON's values if set, else the built-in table, else everywhere. */
    public int[] zoneRange(String blueprintName) {
        int[] d = DEFAULT_ZONES.getOrDefault(blueprintName.toLowerCase(java.util.Locale.ROOT), new int[]{1, 99});
        return new int[]{minZone >= 0 ? minZone : d[0], maxZone >= 0 ? maxZone : d[1]};
    }

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

        // The faction ships (2026-10-01, tools/gen_ship_blueprints.py) - their faction decides who sails them
        AiShipConfig cog = new AiShipConfig();
        cog.faction = "merchants";
        cog.speedMult = 0.70f; cog.patrolSpeed = 0.28f;
        cog.engageRange = 45.0; cog.orbitRange = 40.0; cog.broadsideRange = 40.0; cog.broadsideMin = 22.0;
        cog.alignThreshold = 0.5; cog.reloadTicks = 150; cog.accuracySpread = 0.35f; cog.retreatHpFrac = 0.5;
        writeDefaultIfAbsent("merchantman", cog);

        AiShipConfig frigate = new AiShipConfig();
        frigate.faction = "navy";
        frigate.speedMult = 0.85f; frigate.patrolSpeed = 0.38f;
        frigate.engageRange = 65.0; frigate.orbitRange = 32.0; frigate.broadsideRange = 58.0; frigate.broadsideMin = 18.0;
        frigate.alignThreshold = 0.6; frigate.reloadTicks = 80; frigate.accuracySpread = 0.12f; frigate.retreatHpFrac = 0.1;
        writeDefaultIfAbsent("navy_frigate", frigate);

        AiShipConfig xebec = new AiShipConfig();
        xebec.faction = "pirates";
        xebec.speedMult = 1.0f; xebec.patrolSpeed = 0.45f;
        xebec.engageRange = 55.0; xebec.orbitRange = 26.0; xebec.broadsideRange = 45.0; xebec.broadsideMin = 14.0;
        xebec.alignThreshold = 0.55; xebec.reloadTicks = 100; xebec.accuracySpread = 0.2f; xebec.retreatHpFrac = 0.15;
        writeDefaultIfAbsent("corsair_xebec", xebec);

        AiShipConfig hulk = new AiShipConfig();
        hulk.faction = "undead";
        hulk.speedMult = 0.75f; hulk.patrolSpeed = 0.3f;
        hulk.engageRange = 60.0; hulk.orbitRange = 30.0; hulk.broadsideRange = 50.0; hulk.broadsideMin = 16.0;
        hulk.alignThreshold = 0.5; hulk.reloadTicks = 110; hulk.accuracySpread = 0.25f; hulk.retreatHpFrac = 0.0;
        writeDefaultIfAbsent("drowned_hulk", hulk);

        // THE FLEET (2026-10-04, tools/gen_fleet_ships.py): three more hulls per faction
        //    name                      faction      speed  patrol engage orbit bside bmin align reload spread retreat capHP capDmg capArm
        fleet("pirate_cutter",          "pirates",   0.95f, 0.42f, 45, 28, 40, 14, 0.50, 130, 0.30f, 0.30,  40,  6,  2);
        fleet("pirate_brig",            "pirates",   0.90f, 0.40f, 60, 30, 52, 16, 0.58,  90, 0.15f, 0.12,  90, 10,  6);
        fleet("pirate_galleon",         "pirates",   0.80f, 0.36f, 70, 34, 60, 18, 0.60,  75, 0.10f, 0.05, 160, 14, 10);
        fleet("merchant_lugger",        "merchants", 0.65f, 0.25f, 35, 40, 35, 22, 0.45, 170, 0.40f, 0.60,  30,  4,  0);
        fleet("merchant_fluyt",         "merchants", 0.70f, 0.28f, 45, 40, 42, 22, 0.50, 140, 0.30f, 0.50,  60,  6,  3);
        fleet("merchant_indiaman",      "merchants", 0.75f, 0.32f, 60, 34, 55, 18, 0.55,  95, 0.18f, 0.30, 110,  9,  8);
        fleet("navy_cutter",            "navy",      1.05f, 0.45f, 55, 28, 45, 14, 0.55, 110, 0.18f, 0.20,  55,  7,  5);
        fleet("navy_corvette",          "navy",      0.92f, 0.40f, 62, 30, 55, 16, 0.60,  85, 0.12f, 0.12,  90, 10,  8);
        fleet("navy_man_o_war",         "navy",      0.70f, 0.32f, 75, 36, 65, 20, 0.62,  65, 0.08f, 0.05, 200, 16, 14);
        fleet("undead_wraith",          "undead",    1.00f, 0.42f, 55, 26, 45, 14, 0.50, 100, 0.22f, 0.00,  70,  9,  4);
        fleet("undead_bone_galley",     "undead",    0.95f, 0.40f, 50, 24, 42, 12, 0.50,  95, 0.20f, 0.00, 100, 11,  6);
        fleet("undead_phantom_galleon", "undead",    0.85f, 0.36f, 70, 32, 60, 18, 0.60,  70, 0.10f, 0.00, 190, 15, 12);
    }

    private static void fleet(String name, String faction, float speed, float patrol, double engage, double orbit, double bside,
                              double bmin, double align, int reload, float spread, double retreat, double capHp, double capDmg, double capArmor) {
        AiShipConfig c = new AiShipConfig();
        c.faction = faction; c.speedMult = speed; c.patrolSpeed = patrol;
        c.engageRange = engage; c.orbitRange = orbit; c.broadsideRange = bside; c.broadsideMin = bmin;
        c.alignThreshold = align; c.reloadTicks = reload; c.accuracySpread = spread; c.retreatHpFrac = retreat;
        c.captainMaxHealth = capHp; c.captainAttackDamage = capDmg; c.captainArmor = capArmor;
        writeDefaultIfAbsent(name, c);
    }

    private static Path configPath(String blueprintName) {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("pixelpirates")
            .resolve("ai")
            .resolve(blueprintName.toLowerCase() + ".json");
    }
}
