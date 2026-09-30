package net.get900.pixelpirates.world.gen.density;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

public class ZoneTerrainFunction implements DensityFunction.Base {

    public static final MapCodec<ZoneTerrainFunction> MAP_CODEC = MapCodec.unit(new ZoneTerrainFunction());
    public static final CodecHolder<? extends DensityFunction> CODEC_HOLDER = CodecHolder.of(MAP_CODEC);

    private static final double[] ZONE_BOUNDARIES = {500.0, 1500.0, 2500.0, 3500.0, 4500.0};
    private static final double BLEND_ZONE   = 150.0;
    private static final double SECTOR_ANGLE = 2.0 * Math.PI / 3.0;
    private static final double BLEND_SECTOR = 0.18;

    private static final double SEA_LEVEL = 63.0;

    private static final double WARP_FREQ     = 0.0022;
    private static final double WARP_STRENGTH = 110.0;

    // Per-biome seed offsets — keeps each biome in its own region of noise space
    private static final double[][] SEED = {
        {      0,      0 }, // hub
        {  11000,  22000 }, // zone1 s0 — Temperate Shallows
        {  33000,  44000 }, // zone1 s1 — Island Thickets
        {  55000,  66000 }, // zone1 s2 — Open Ocean
        {  77000,  88000 }, // zone2 s0 — Coral Bay
        {  99000,  11000 }, // zone2 s1 — Reef Edge
        {  22000,  33000 }, // zone2 s2 — Siren Sea
        {  44000,  55000 }, // zone3 s0 — Ash Reef
        {  66000,  77000 }, // zone3 s1 — Boiling Basin
        {  88000,  99000 }, // zone3 s2 — Magma Sea
        {  12000,  23000 }, // zone4 s0 — Phantom Wake
        {  34000,  45000 }, // zone4 s1 — Shipgrave Depths
        {  56000,  67000 }, // zone4 s2 — Drowned Trench
        {  78000,  89000 }, // zone5 s0 — Abyssal Rings
        {  91000,  12000 }, // zone5 s1 — Pillar Sea
        {  23000,  34000 }, // zone5 s2 — Maw Depths
    };

    // -----------------------------------------------------------------------

    @Override
    public double sample(DensityFunction.NoisePos pos) {
        double bx = pos.blockX();
        double bz = pos.blockZ();
        double by = pos.blockY();
        double targetY = computeTargetY(Math.sqrt(bx * bx + bz * bz), bx, bz);
        return (targetY - by) / 20.0;
    }

    @Override public double minValue() { return -200.0; }
    @Override public double maxValue() { return  200.0; }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() { return CODEC_HOLDER; }

    // -----------------------------------------------------------------------
    // Blending
    // -----------------------------------------------------------------------

    /** The terrain surface height at (x, z) - this density is a pure heightfield (GallowsGrotto keeps its caves under it). */
    public static int terrainTop(int x, int z) {
        return (int) Math.floor(computeTargetY(Math.sqrt((double) x * x + (double) z * z), x, z));
    }

    private static double computeTargetY(double dist, double x, double z) {
        double wx = x + octaveNoise(x * WARP_FREQ + 17.31, z * WARP_FREQ + 92.17, 2, 1.0) * WARP_STRENGTH;
        double wz = z + octaveNoise(x * WARP_FREQ + 54.73, z * WARP_FREQ + 31.91, 2, 1.0) * WARP_STRENGTH;

        double rawAngle = Math.atan2(z, x);
        if (rawAngle < 0) rawAngle += 2.0 * Math.PI;

        // Zone blend
        int zoneA, zoneB;
        double zoneT;
        {
            int ni = nearestBoundaryIndex(dist);
            double nd = Math.abs(dist - ZONE_BOUNDARIES[ni]);
            if (nd >= BLEND_ZONE) {
                zoneA = zoneB = zoneIndex(dist); zoneT = 0;
            } else {
                zoneA = ni; zoneB = ni + 1;
                zoneT = fade(clamp01(0.5 + (dist - ZONE_BOUNDARIES[ni]) / (2.0 * BLEND_ZONE)));
            }
        }

        // Sector blend
        int sectorA, sectorB;
        double sectorW;
        {
            int primary     = Math.min((int)(rawAngle / SECTOR_ANGLE), 2);
            double inSector = rawAngle - primary * SECTOR_ANGLE;
            if (inSector < BLEND_SECTOR) {
                sectorA = primary; sectorB = (primary + 2) % 3;
                sectorW = 1.0 - fade(clamp01(0.5 + inSector / (2.0 * BLEND_SECTOR)));
            } else if (inSector > SECTOR_ANGLE - BLEND_SECTOR) {
                sectorA = primary; sectorB = (primary + 1) % 3;
                sectorW = 1.0 - fade(clamp01(0.5 + (SECTOR_ANGLE - inSector) / (2.0 * BLEND_SECTOR)));
            } else {
                sectorA = sectorB = primary; sectorW = 0;
            }
        }

        double wZA = 1.0 - zoneT, wZB = zoneT;
        double wSA = 1.0 - sectorW, wSB = sectorW;

        double y = wZA * wSA * rawTerrainY(zoneA, sectorA, wx, wz);
        if (wSB > 0) y += wZA * wSB * rawTerrainY(zoneA, sectorB, wx, wz);
        if (wZB > 0) y += wZB * wSA * rawTerrainY(zoneB, sectorA, wx, wz);
        if (wZB > 0 && wSB > 0) y += wZB * wSB * rawTerrainY(zoneB, sectorB, wx, wz);

        // Spawn island + port city plateau at world origin (shape shared with PortCityLayout)
        y = net.get900.pixelpirates.world.gen.SpawnIslandTerrain.apply(x, z, y);

        return y;
    }

    private static int nearestBoundaryIndex(double dist) {
        int best = 0;
        double bestD = Math.abs(dist - ZONE_BOUNDARIES[0]);
        for (int i = 1; i < ZONE_BOUNDARIES.length; i++) {
            double d = Math.abs(dist - ZONE_BOUNDARIES[i]);
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    private static int zoneIndex(double dist) {
        if (dist < 500)  return 0;
        if (dist < 1500) return 1;
        if (dist < 2500) return 2;
        if (dist < 3500) return 3;
        if (dist < 4500) return 4;
        return 5;
    }

    // -----------------------------------------------------------------------
    // Terrain dispatch
    // -----------------------------------------------------------------------

    private static double rawTerrainY(int zone, int sector, double wx, double wz) {
        int idx = (zone == 0) ? 0 : (zone - 1) * 3 + sector + 1;
        if (idx >= SEED.length) idx = SEED.length - 1;
        double sx = wx + SEED[idx][0];
        double sz = wz + SEED[idx][1];
        return switch (zone) {
            case 0 -> hubTerrain(sx, sz);
            case 1 -> zone1Terrain(sector, sx, sz);
            case 2 -> zone2Terrain(sector, sx, sz);
            case 3 -> zone3Terrain(sector, sx, sz);
            case 4 -> zone4Terrain(sector, sx, sz);
            default -> zone5Terrain(sector, sx, sz);
        };
    }

    // -----------------------------------------------------------------------
    // Hub
    // -----------------------------------------------------------------------

    // Gentle shallow bay — safe spawn area with small sandy bumps
    private static double hubTerrain(double x, double z) {
        double broad  = octaveNoise(x, z, 3, 0.007);
        double detail = octaveNoise(x, z, 2, 0.028);
        return SEA_LEVEL - 5.0 + broad * 9.0 + detail * 3.5;
        // Floor ≈57, island bumps ≈70
    }

    // -----------------------------------------------------------------------
    // Zone 1 — 500-1500 blocks
    // -----------------------------------------------------------------------

    private static double zone1Terrain(int sector, double x, double z) {
        return switch (sector) {

            // Temperate Shallows — ultra-shallow sandy floor, rare low atolls
            // Sea floor sits only 1-5 blocks under water; wading rather than swimming.
            case 0 -> {
                double shelf = octaveNoise(x, z, 3, 0.009);
                double atoll = octaveNoise(x, z, 2, 0.022);
                // Atolls only where atoll noise exceeds 0.35 threshold
                double atollBump = Math.max(0.0, atoll - 0.35) / 0.65 * 11.0;
                yield SEA_LEVEL - 4.0 + shelf * 3.5 + atollBump;
                // Floor: 55-60, atoll peaks: 70
            }

            // Island Thickets — dense packed landmasses with narrow carved channels
            case 1 -> {
                double mass        = octaveNoise(x, z, 3, 0.009);
                double peak        = octaveNoise(x, z, 4, 0.024);
                double peakEffect  = peak > 0 ? peak * 26.0 : peak * 4.0;
                double channelN    = octaveNoise(x, z, 2, 0.014);
                double proximity   = Math.max(0.0, 0.22 - Math.abs(channelN));
                double t           = proximity / 0.22;
                double channelCut  = t * t * 56.0;
                yield SEA_LEVEL + 11.0 + mass * 13.0 + peakEffect - channelCut;
                // Land: 65-113, channel centres: ≈18
            }

            // Open Ocean — near-featureless abyss for ship battles
            default -> {
                double n = octaveNoise(x, z, 2, 0.005);
                yield 20.0 + n * 4.0;
                // Floor: 16-24, almost perfectly flat
            }
        };
    }

    // -----------------------------------------------------------------------
    // Zone 2 — 1500-2500 blocks
    // -----------------------------------------------------------------------

    private static double zone2Terrain(int sector, double x, double z) {
        return switch (sector) {

            // Coral Bay — shallow sandy shelf punctured by dense coral spires
            // The spires are high-frequency and sharp; many reach just below or break the surface.
            case 0 -> {
                double shelf = octaveNoise(x, z, 3, 0.010);
                double spire = octaveNoise(x, z, 3, 0.052); // very high freq for tight coral
                double spireH = Math.max(0.0, spire - 0.20) / 0.80 * 15.0;
                yield SEA_LEVEL - 9.0 + shelf * 4.0 + spireH;
                // Shelf: 50-58, spire tips: up to 73
            }

            // Reef Edge — dramatic underwater cliff: shallow shelf drops to deep wall
            // Uses a steep sigmoid to create a concentrated cliff face.
            case 1 -> {
                double shelf   = octaveNoise(x, z, 2, 0.006);
                double erosion = octaveNoise(x, z, 3, 0.028);
                double t       = fade(clamp01((shelf + 0.13) / 0.26));
                yield lerp(t, 24.0, 57.0) + erosion * 9.0;
                // Deep side: 15-33, shallow side: 48-66, cliff spans ≈30 blocks horizontally
            }

            // Siren Sea — sweeping slow underwater hills, deceptively calm
            default -> {
                double hills = octaveNoise(x, z, 3, 0.004); // very long wavelength
                double surf  = octaveNoise(x, z, 2, 0.016);
                yield 36.0 + hills * 14.0 + surf * 4.0;
                // Gentle rolling floor: 18-54
            }
        };
    }

    // -----------------------------------------------------------------------
    // Zone 3 — 2500-3500 blocks (water causes damage without heat protection)
    // -----------------------------------------------------------------------

    private static double zone3Terrain(int sector, double x, double z) {
        return switch (sector) {

            // Ash Reef — volcanic ridged crags using ridge noise
            // Ridge noise (1−|n|) puts peaks at the sign-change seams of the raw noise,
            // creating a connected network of sharp angular ridges above a deep baseline.
            case 0 -> {
                double n      = octaveNoise(x, z, 3, 0.014);
                double ridge  = 1.0 - Math.abs(n);         // [0,1] — peaks at noise zeros
                double ridgeH = ridge * ridge * 38.0;       // sharpen; max +38
                double detail = octaveNoise(x, z, 2, 0.042);
                yield SEA_LEVEL - 17.0 + ridgeH + detail * 6.0;
                // Lava-rock floor: 40-46, ridge crests: up to 90 (island pinnacles)
            }

            // Boiling Basin — volcanic caldera structures: elevated rims, sunken hot centres
            // Positive noise → caldera rim (high). Strongly negative noise → basin floor (deep).
            case 1 -> {
                double basin  = octaveNoise(x, z, 2, 0.009);
                double detail = octaveNoise(x, z, 2, 0.036);
                double rim    = Math.max(0.0, basin);
                double rimH   = rim * rim * 28.0;
                double dep    = Math.max(0.0, -basin - 0.15) / 0.85;
                double depH   = dep * dep * 26.0;
                yield SEA_LEVEL - 8.0 + rimH - depH + detail * 4.0;
                // Caldera rim: up to 87 (dry land), neutral slope: 51-59, basin floor: 25-31
            }

            // Magma Sea — deep flat abyss broken by isolated volcanic cone peaks
            // Cones form only where noise exceeds 0.62; below that the floor is nearly flat.
            default -> {
                double floor   = octaveNoise(x, z, 2, 0.004);
                double volcano = octaveNoise(x, z, 3, 0.015);
                double cf      = Math.max(0.0, volcano - 0.62) / 0.38;
                double cone    = cf * cf * 72.0;
                yield 18.0 + floor * 4.0 + cone;
                // Abyss floor: 14-22, volcanic peaks: up to 94
            }
        };
    }

    // -----------------------------------------------------------------------
    // Zone 4 — 3500-4500 blocks
    // -----------------------------------------------------------------------

    private static double zone4Terrain(int sector, double x, double z) {
        return switch (sector) {

            // Phantom Wake — elongated streak ridges like frozen ghost-ship wakes
            // Anisotropic scaling (compress x, stretch z) creates directional features.
            // Raised base so roughly half the streaks break the surface as ghost-land.
            case 0 -> {
                double streak = octaveNoise(x * 0.38, z * 2.1, 3, 0.010);
                double ripple = octaveNoise(x, z, 2, 0.026);
                yield 58.0 + streak * 24.0 + ripple * 9.0;
                // Streaks run ≈5× longer in one axis; floor: 25-91, land mass above 63
            }

            // Shipgrave Depths — jagged reef-crags where countless hulls have been torn apart
            // Double ridge noise (two frequencies) creates a chaotic network of sharp spires
            // rising from deep sea floor — the ships never made it through.
            case 1 -> {
                double n1     = octaveNoise(x, z, 3, 0.012);
                double n2     = octaveNoise(x, z, 2, 0.038);
                double ridge1 = 1.0 - Math.abs(n1);        // main jagged ridges
                double ridge2 = 1.0 - Math.abs(n2);        // finer crag detail
                double base   = octaveNoise(x, z, 2, 0.006);
                yield 15.0 + base * 7.0 + ridge1 * ridge1 * 48.0 + ridge2 * ridge2 * 22.0;
                // Deep floor: 8-22, major rock spires: up to 85, dense crag networks
            }

            // Drowned Trench — mostly flat deep floor sliced by razor-narrow deep trenches
            default -> {
                double floor   = octaveNoise(x, z, 2, 0.005);
                double trench  = octaveNoise(x, z, 2, 0.013);
                double prox    = Math.max(0.0, 0.17 - Math.abs(trench));
                double t       = prox / 0.17;
                double cut     = t * t * 42.0;
                yield 27.0 + floor * 4.0 - cut;
                // Floor: 23-31, trench centres: down to −15 (hits world floor bedrock)
            }
        };
    }

    // -----------------------------------------------------------------------
    // Zone 5 — 4500+ blocks (presence causes madness without sanity protection)
    // -----------------------------------------------------------------------

    private static double zone5Terrain(int sector, double x, double z) {
        return switch (sector) {

            // Abyssal Rings — eerie concentric ring structures from wave interference.
            // Raised to create actual land masses: ring crests become islands of dark stone,
            // ring troughs are deep cold water, giving a genuinely alien archipelago feel.
            case 0 -> {
                double w1    = Math.sin(x * 0.018 + z * 0.007);
                double w2    = Math.sin(x * 0.007 - z * 0.018);
                double rings = (w1 + w2) * 0.5;              // [-1,1] interference arcs
                double noise = octaveNoise(x, z, 2, 0.006);  // breaks perfect symmetry
                yield 55.0 + rings * 32.0 + noise * 10.0;
                // Ring crests: up to 97 (land islands), ring troughs: down to 13 (deep water)
            }

            // Pillar Sea — void floor with pillars that vary regionally in height and thickness.
            // Height patches (220-310 blocks) make some pillars merely tall and some nearly
            // reach the world ceiling. Thickness patches (thin needles to wide fins) add
            // further variety. All tops disappear into the zone's heavy blizzard fog.
            case 1 -> {
                double base      = octaveNoise(x, z, 2, 0.005);
                double pillar    = octaveNoise(x, z, 2, 0.020);
                double heightMod = octaveNoise(x, z, 1, 0.0018) * 0.5 + 0.5; // [0,1] regional patch
                double thickMod  = octaveNoise(x, z, 1, 0.0025) * 0.5 + 0.5; // [0,1] regional patch
                double threshold = 0.055 + thickMod * 0.065; // 0.055-0.12: needles to blades
                double maxSpike  = 220.0 + heightMod * 90.0;  // 220-310: dramatic height variation
                double prox  = Math.max(0.0, threshold - Math.abs(pillar));
                double t     = prox / threshold;
                double spike = t * t * t * maxSpike; // cubic: wide variance, always sharp peak
                yield 5.0 + base * 4.0 + spike;
                // Void floor: 1-9; shortest pillars: ~225, tallest: ~315 (near world ceiling)
            }

            // Maw Depths — deceptively shallow floor right at sea level; the chasms beneath
            // are enormous. Players walk into what looks like a ford and fall into the abyss.
            // Pits only form where broad noise exceeds 0.72 (~15% of area) but go very deep.
            default -> {
                double floor = octaveNoise(x, z, 2, 0.003); // near-flat baseline
                double maw   = octaveNoise(x, z, 1, 0.005); // very broad — each maw is massive
                double mf    = Math.max(0.0, maw - 0.72) / 0.28;
                double pit   = mf * mf * 140.0;
                yield 61.0 + floor * 2.0 - pit;
                // Visible floor: 59-63 (right at/just under sea level, looks walkable);
                // maw centres: down to −79 (near void — a sudden catastrophic drop)
            }
        };
    }

    // -----------------------------------------------------------------------
    // Noise utilities
    // -----------------------------------------------------------------------

    private static double octaveNoise(double x, double z, int octaves, double baseFreq) {
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

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }
    private static double lerp(double t, double a, double b) { return a + t * (b - a); }
    private static double clamp01(double v) { return v < 0 ? 0 : Math.min(v, 1); }

    private static double hash(int x, int z) {
        int h = x * 1619 + z * 31337;
        h ^= (h >> 17); h *= 0x45d9f3b; h ^= (h >> 15);
        return ((h & 0x7FFFFFFF) / (double) 0x7FFFFFFF) * 2.0 - 1.0;
    }
}
