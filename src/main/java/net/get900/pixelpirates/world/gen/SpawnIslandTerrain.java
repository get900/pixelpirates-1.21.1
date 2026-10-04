package net.get900.pixelpirates.world.gen;

/**
 * Analytic shape of the spawn island — the single source of truth shared by
 * {@code ZoneTerrainFunction} (terrain density) and {@code PortCityLayout}
 * (the port city block plan). Pure Java on purpose: no Minecraft imports, so
 * the layout can be compiled and previewed standalone.
 *
 * Geometry: a superellipse island roughly 380x340 blocks centred slightly
 * north of the origin, with a harbor bay carved into the south coast. The
 * south half of the island is a flat, terraced city plateau; the north half
 * rises into grassy hills with two headlands (lighthouse east, fort west).
 */
public final class SpawnIslandTerrain {

    private SpawnIslandTerrain() {}

    public static final double SEA_LEVEL = 63.0;

    // Island ellipse
    public static final double CX = 0.0;
    public static final double CZ = -30.0;
    public static final double RX = 190.0;
    public static final double RZ = 170.0;
    private static final double POWER = 2.6;      // superellipse exponent — flat south coast

    // Radial bands (r is the normalized superellipse radius)
    private static final double LAND_R  = 0.93;   // land inside this
    private static final double BEACH_R = 1.04;   // beach slope ends here
    private static final double EDGE_R  = 1.35;   // island influence fades out here

    // Harbor bay carved into the south coast (adds to r)
    private static final double BAY_STRENGTH = 0.52;
    private static final double BAY_CENTER_Z = 145.0;
    private static final double BAY_SX = 140.0;
    private static final double BAY_SZ = 70.0;

    // City footprint (southern half of the island)
    public static final int CITY_X_MIN = -136, CITY_X_MAX = 136;
    public static final int CITY_Z_MIN = -86,  CITY_Z_MAX = 97;

    // Terrace ground levels (flat city plateau)
    public static final int HARBOR_Y = 65;   // z >= 50
    public static final int MID_Y    = 67;   // -10 <= z < 50
    public static final int UPPER_Y  = 70;   // z < -10
    public static final int TERRACE_HARBOR_Z = 50;
    public static final int TERRACE_MID_Z    = -10;

    // ------------------------------------------------------------------
    // Radius / masks
    // ------------------------------------------------------------------

    /** Normalized superellipse radius including coast noise and the harbor bay. */
    public static double radius(double x, double z) {
        double nx = Math.abs((x - CX) / RX);
        double nz = Math.abs((z - CZ) / RZ);
        double r = Math.pow(Math.pow(nx, POWER) + Math.pow(nz, POWER), 1.0 / POWER);
        // Gentle coastline irregularity
        r += octaveNoise(x, z, 2, 0.010) * 0.055;
        // Harbor bay
        double bx = x / BAY_SX;
        double bz = (z - BAY_CENTER_Z) / BAY_SZ;
        r += BAY_STRENGTH * Math.exp(-(bx * bx + bz * bz));
        return r;
    }

    public static boolean isLand(double x, double z) {
        return radius(x, z) < LAND_R;
    }

    /** True where the city plateau owns the ground (flat terraces). */
    public static boolean inCityFootprint(double x, double z) {
        if (x < CITY_X_MIN || x > CITY_X_MAX || z < CITY_Z_MIN || z > CITY_Z_MAX) return false;
        return radius(x, z) < LAND_R;
    }

    /** Terrace ground level for a city column. */
    public static int cityGroundY(double x, double z) {
        if (z >= TERRACE_HARBOR_Z) return HARBOR_Y;
        if (z >= TERRACE_MID_Z)    return MID_Y;
        return UPPER_Y;
    }

    // ------------------------------------------------------------------
    // Surface height
    // ------------------------------------------------------------------

    /**
     * Blends the island into the surrounding zone terrain.
     * @param baseY the zone terrain height this column would otherwise have
     */
    public static double apply(double x, double z, double baseY) {
        double r = radius(x, z);
        if (r >= DEEP_R) return baseY;

        // THE DEEP WATER round the island (2026-10-05, the user: the seas off the harbour were so shallow the regatta
        // ships ran aground): from DEEP_START - clear of the island's shelf and the harbour (the grand pier's landing
        // reaches r 1.55; the piers' piles are drawn to the old floor) - the floor drops to DEEP_Y over DROP and rises
        // back to the zone floor by DEEP_R. The regatta course (r 1.6-1.95) lies in it. It only ever deepens. New chunks only.
        if (r >= EDGE_R) {
            if (r < DEEP_START) return baseY;
            double down = lerp(fade(Math.min(1, (r - DEEP_START) / DROP)), baseY, DEEP_Y);
            double up = fade(Math.max(0, Math.min(1, (r - (DEEP_R - 0.3)) / 0.3)));
            return Math.min(baseY, lerp(up, down, baseY));
        }

        double islandY = surfaceY(x, z, r, baseY);
        if (r <= BEACH_R) return islandY;

        // Fade the island shelf into the surrounding bay floor
        double t = fade((EDGE_R - r) / (EDGE_R - BEACH_R));
        return lerp(t, baseY, islandY);
    }

    /** The deep water round the island: floor at most this, out to this normalized radius. */
    private static final double DEEP_Y = 50.0, DEEP_START = 1.58, DROP = 0.08, DEEP_R = 2.4;

    /** Island surface height (only meaningful for r < EDGE_R). */
    public static double surfaceY(double x, double z, double r, double baseY) {
        if (r < LAND_R) {
            if (inCityFootprint(x, z)) {
                // Flat terraces; soften the terrace steps slightly so the
                // low-resolution density sampling doesn't leave overhangs —
                // the city plan paves the exact ground on top of this.
                double y = cityGroundY(x, z);
                double d1 = Math.abs(z - TERRACE_HARBOR_Z);
                double d2 = Math.abs(z - TERRACE_MID_Z);
                if (d1 < 4) y = lerp(fade(0.5 + (z - TERRACE_HARBOR_Z) / 8.0), MID_Y, HARBOR_Y);
                else if (d2 < 4) y = lerp(fade(0.5 + (z - TERRACE_MID_Z) / 8.0), UPPER_Y, MID_Y);
                return y;
            }
            return naturalY(x, z, r);
        }
        // Beach: land edge down to a shallow shelf
        double t = (r - LAND_R) / (BEACH_R - LAND_R);
        return lerp(t, 64.5, 58.0);
    }

    /** Natural (non-city) island terrain: rolling hills rising northward. */
    public static double naturalY(double x, double z, double r) {
        double inland = (LAND_R - r) / LAND_R;             // 0 at coast → ~0.93 centre
        double y = 64.5 + inland * 10.0;
        // Rolling hills, stronger inland
        double hn = octaveNoise(x + 4096, z - 4096, 3, 0.012) * 0.5 + 0.5;
        y += hn * inland * 26.0;
        // Headlands
        y += bump(x, z, 108, -102, 24.0) * 13.0;           // lighthouse headland (east)
        y += bump(x, z, -98, -112, 26.0) * 15.0;           // fort hill (west)
        y += bump(x, z, 5, -138, 40.0) * 9.0;              // central north ridge
        return y;
    }

    /** Convenience: full surface height with no zone-terrain fallback (land/beach only). */
    public static double islandSurfaceY(double x, double z) {
        double r = radius(x, z);
        return surfaceY(x, z, r, 56.0);
    }

    private static double bump(double x, double z, double cx, double cz, double sigma) {
        double dx = (x - cx) / sigma, dz = (z - cz) / sigma;
        return Math.exp(-(dx * dx + dz * dz));
    }

    // ------------------------------------------------------------------
    // Noise (identical style to ZoneTerrainFunction — deterministic, seedless)
    // ------------------------------------------------------------------

    public static double octaveNoise(double x, double z, int octaves, double baseFreq) {
        double val = 0, amp = 1, totalAmp = 0, freq = baseFreq;
        for (int i = 0; i < octaves; i++) {
            val      += valueNoise(x * freq, z * freq) * amp;
            totalAmp += amp;
            amp      *= 0.5;
            freq     *= 2.1;
        }
        return val / totalAmp;
    }

    private static double valueNoise(double x, double z) {
        int ix = (int) Math.floor(x), iz = (int) Math.floor(z);
        double fx = x - ix, fz = z - iz;
        return lerp(fade(fz),
            lerp(fade(fx), hash(ix,     iz    ), hash(ix + 1, iz    )),
            lerp(fade(fx), hash(ix,     iz + 1), hash(ix + 1, iz + 1)));
    }

    public static double fade(double t) {
        t = t < 0 ? 0 : Math.min(t, 1);
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    public static double lerp(double t, double a, double b) { return a + t * (b - a); }

    /** Deterministic integer hash → [-1, 1]. */
    public static double hash(int x, int z) {
        int h = x * 1619 + z * 31337;
        h ^= (h >> 17); h *= 0x45d9f3b; h ^= (h >> 15);
        return ((h & 0x7FFFFFFF) / (double) 0x7FFFFFFF) * 2.0 - 1.0;
    }
}
