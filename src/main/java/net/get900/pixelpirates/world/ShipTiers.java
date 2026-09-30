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

    private static final Map<String, Integer> REQUIRED_ZONE = Map.of(
        "sloop",       0,
        "skipper",     2,
        "brigantine",  3
    );

    public static int getRequiredZone(String blueprintName) {
        return REQUIRED_ZONE.getOrDefault(blueprintName.toLowerCase(Locale.ROOT), 1);
    }

    /** Returns only the blueprints the player's current zone allows them to commission. */
    public static List<String> filterForZone(List<String> all, int playerUnlockedZone) {
        return all.stream()
            .filter(name -> getRequiredZone(name) <= playerUnlockedZone)
            .collect(Collectors.toList());
    }

    /**
     * Returns the zone number unlocked by capturing (claiming a derelict of) this ship type.
     * 0 means capturing this ship doesn't unlock any zone.
     */
    public static int getCaptureZoneUnlock(String blueprintName) {
        return switch (blueprintName.toLowerCase(Locale.ROOT)) {
            case "skipper"    -> 2;
            case "brigantine" -> 3;
            default           -> 0;
        };
    }
}
