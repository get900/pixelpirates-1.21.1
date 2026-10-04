package net.get900.pixelpirates.world;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps blueprint names to the minimum zone level required to commission them.
 * Zone 0 = available to everyone (the starter sloop unlocks zone 1 on commission).
 * Add new ships here as zones are built out.
 */
public class ShipTiers {

    private static final Map<String, Integer> REQUIRED_ZONE = Map.ofEntries(
        Map.entry("sloop",                  0),
        Map.entry("merchant_lugger",        1),   // the fleet (2026-10-04, tools/gen_fleet_ships.py) - three more per faction
        Map.entry("pirate_cutter",          1),
        Map.entry("navy_cutter",            1),
        Map.entry("merchantman",            1),   // Emerald Trading Co. cog - slow, roomy hold (tools/gen_ship_blueprints.py)
        Map.entry("skipper",                2),
        Map.entry("corsair_xebec",          2),   // Crimson Corsairs raider - light and fast
        Map.entry("merchant_fluyt",         2),
        Map.entry("navy_corvette",          2),
        Map.entry("brigantine",             3),
        Map.entry("pirate_brig",            3),
        Map.entry("merchant_indiaman",      3),
        Map.entry("undead_wraith",          3),
        Map.entry("undead_bone_galley",     3),
        Map.entry("navy_frigate",           4),   // Iron Armada three-master - 14 guns
        Map.entry("drowned_hulk",           4),   // Drowned Fleet wreck
        Map.entry("pirate_galleon",         4),
        Map.entry("navy_man_o_war",         5),
        Map.entry("undead_phantom_galleon", 5)
    );

    /** Commission price in DOUBLOONS (2026-10-01): by the hull's zone, so bigger ships cost more. */
    private static final int[] COST_BY_ZONE = {25, 50, 90, 140, 200, 260};

    public static int commissionCost(String blueprintName) {
        int z = getRequiredZone(blueprintName);
        return COST_BY_ZONE[Math.max(0, Math.min(COST_BY_ZONE.length - 1, z))];
    }

    public static int getRequiredZone(String blueprintName) {
        return REQUIRED_ZONE.getOrDefault(blueprintName.toLowerCase(Locale.ROOT), 1);
    }

    /** Returns only the blueprints the player's current zone allows them to commission. */
    public static List<String> filterForZone(List<String> all, int playerUnlockedZone) {
        return all.stream()
            .filter(name -> getRequiredZone(name) <= playerUnlockedZone)
            .collect(Collectors.toList());
    }
}
