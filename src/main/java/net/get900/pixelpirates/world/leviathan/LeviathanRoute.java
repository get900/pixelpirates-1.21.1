package net.get900.pixelpirates.world.leviathan;

import java.util.List;
import java.util.Random;

/**
 * THE LEVIATHAN'S ROUTE (boss 10/10, 2026-09-30): there is ONE Leviathan per world, so its three lairs and the two ports
 * it destroys are single sites, not a dungeon grid. They lie along one seeded bearing through the southern-west sector
 * of the ring world (the Pillar Sea's slice of zone 5), swimming INWARD:
 * <pre>
 *   RIFT        r 5000   zone 5 / Pillar Sea        lair 1 - THE WAKING
 *   SALTMARROW  r 4150   zone 4 / Shipgrave Depths  port 1 (on the way to the Gullet)
 *   GULLET      r 3050   zone 3 / Boiling Basin     lair 2 - THE HUNGER
 *   BRIGHTWATER r 2150   zone 2 / Reef Edge         port 2 (on the way to the Spire)
 *   SPIRE       r 1850   zone 2, 25 deg off the line - lair 3 - THE UNMAKING (remote: ~1.8 km from spawn)
 * </pre>
 * Pure Java and a pure function of the world seed, so worldgen, /ppleviathan locate, the Heartstone and the hunt all
 * agree. Biomes are assigned by exact distance/angle (PixelPiratesBiomeSource), so the sector is known from the bearing.
 */
public final class LeviathanRoute {
    private LeviathanRoute() {}

    public static final String RIFT = "rift", SALTMARROW = "saltmarrow", GULLET = "gullet", BRIGHTWATER = "brightwater", SPIRE = "spire";

    /** A site: centre (x, z) and the radius it owns. */
    public record Site(String id, int x, int z, int radius) {
        public double dist(double px, double pz) { return Math.sqrt((px - x) * (px - x) + (pz - z) * (pz - z)); }
    }

    public record Route(Site rift, Site saltmarrow, Site gullet, Site brightwater, Site spire) {
        public List<Site> all() { return List.of(rift, saltmarrow, gullet, brightwater, spire); }

        public Site byId(String id) {
            for (Site s : all()) if (s.id().equals(id)) return s;
            return null;
        }

        /** The flight path after lair `phase` (1 or 2): lair -> port -> next lair. */
        public List<Site> flight(int fromPhase) {
            return fromPhase == 1 ? List.of(rift, saltmarrow, gullet) : List.of(gullet, brightwater, spire);
        }
    }

    private static long cachedSeed = Long.MIN_VALUE;
    private static Route cached;

    public static synchronized Route of(long seed) {
        if (seed == cachedSeed && cached != null) return cached;
        Random r = new Random(seed ^ 0x1EF1A7A7L);
        double bearing = Math.toRadians(155 + r.nextDouble() * 40);          // 155..195 deg: all five stay in sector 1
        Route route = new Route(
                site(RIFT, bearing, 5000, 104),
                site(SALTMARROW, bearing + wobble(r), 4150, 64),
                site(GULLET, bearing + wobble(r), 3050, 112),
                site(BRIGHTWATER, bearing + wobble(r), 2150, 72),
                site(SPIRE, bearing + Math.toRadians(25), 1850, 72));
        cachedSeed = seed;
        cached = route;
        return route;
    }

    private static double wobble(Random r) { return Math.toRadians((r.nextDouble() - 0.5) * 8); }

    private static Site site(String id, double bearing, double radius, int owns) {
        // snapped to a chunk centre, like every other dungeon site
        int x = (int) Math.round(Math.cos(bearing) * radius), z = (int) Math.round(Math.sin(bearing) * radius);
        return new Site(id, (x & ~15) + 8, (z & ~15) + 8, owns);
    }

    /** Is (x, z) within `pad` blocks of any site's own radius? (other dungeons keep clear of the route) */
    public static boolean nearAnySite(long seed, int x, int z, int pad) {
        for (Site s : of(seed).all()) if (s.dist(x, z) < s.radius() + pad) return true;
        return false;
    }
}
