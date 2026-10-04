package net.get900.pixelpirates.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.get900.pixelpirates.enchantment.ModEnchantments;
import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;

@Environment(EnvType.CLIENT)
public class ZoneEffectsClient {

    private static final double ZONE3_INNER = 2500.0;
    private static final double ZONE3_OUTER = 3500.0;
    private static final double ZONE4_INNER = 3500.0;
    private static final double ZONE4_OUTER = 4500.0;
    private static final double ZONE5_INNER = 4500.0;

    /** Set by the server (/pptest clearsight): drop zone fog, zone particles and biome ambient particles. */
    public static volatile boolean clearSight = false;

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null || client.player == null) return;
            if (!client.world.getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD)) return;
            if (clearSight) return;                         // /pptest clearsight

            double px = client.player.getX();
            double py = client.player.getY();
            double pz = client.player.getZ();
            double dist = Math.sqrt(px * px + pz * pz);
            Random rng = client.player.getRandom();
            ClientWorld world = client.world;

            if (dist >= ZONE3_INNER && dist < ZONE3_OUTER) {
                spawnAshEmbers(world, px, py, pz, rng);
            } else if (dist >= ZONE4_INNER && dist < ZONE4_OUTER) {
                spawnSeaMist(world, px, py, pz, rng);
            } else if (dist >= ZONE5_INNER) {
                int frozenLevel = ModEnchantments.getFrozenSeekerLevel(client.player);
                spawnBlizzard(world, px, py, pz, rng, frozenLevel);
            }
        });
    }

    // Zone 3 — burning ash: rising cinders and ember sparks drifting through hot air
    private static void spawnAshEmbers(ClientWorld world, double x, double y, double z, Random rng) {
        // Rising ash cloud around player
        for (int i = 0; i < 6; i++) {
            double dx = (rng.nextDouble() - 0.5) * 28;
            double dz = (rng.nextDouble() - 0.5) * 28;
            world.addParticle(ParticleTypes.ASH,
                    x + dx, y - 1 + rng.nextDouble() * 6, z + dz,
                    (rng.nextDouble() - 0.5) * 0.06,
                    rng.nextDouble() * 0.10 + 0.03,
                    (rng.nextDouble() - 0.5) * 0.06);
        }
        // Ember sparks (less frequent)
        if (rng.nextInt(2) == 0) {
            for (int i = 0; i < 3; i++) {
                double dx = (rng.nextDouble() - 0.5) * 18;
                double dz = (rng.nextDouble() - 0.5) * 18;
                world.addParticle(ParticleTypes.FLAME,
                        x + dx, y + rng.nextDouble() * 5, z + dz,
                        (rng.nextDouble() - 0.5) * 0.08,
                        rng.nextDouble() * 0.12 + 0.02,
                        (rng.nextDouble() - 0.5) * 0.08);
            }
        }
        // Heavy smoke plumes
        if (rng.nextInt(3) == 0) {
            double dx = (rng.nextDouble() - 0.5) * 22;
            double dz = (rng.nextDouble() - 0.5) * 22;
            world.addParticle(ParticleTypes.LARGE_SMOKE,
                    x + dx, y + rng.nextDouble() * 4, z + dz,
                    (rng.nextDouble() - 0.5) * 0.04, 0.12, (rng.nextDouble() - 0.5) * 0.04);
        }
    }

    // Zone 4 — sea mist: cold wisps of cloud rising off the dark water surface
    private static void spawnSeaMist(ClientWorld world, double x, double y, double z, Random rng) {
        double seaY = 63.0;
        // Mist wisps rising from the sea surface
        for (int i = 0; i < 5; i++) {
            double dx = (rng.nextDouble() - 0.5) * 24;
            double dz = (rng.nextDouble() - 0.5) * 24;
            double startY = Math.min(seaY + rng.nextDouble() * 1.5, y + 2);
            world.addParticle(ParticleTypes.CLOUD,
                    x + dx, startY, z + dz,
                    (rng.nextDouble() - 0.5) * 0.02,
                    rng.nextDouble() * 0.03 + 0.01,
                    (rng.nextDouble() - 0.5) * 0.02);
        }
        // Drifting ash at mid-height
        if (rng.nextInt(2) == 0) {
            for (int i = 0; i < 2; i++) {
                double dx = (rng.nextDouble() - 0.5) * 20;
                double dz = (rng.nextDouble() - 0.5) * 20;
                world.addParticle(ParticleTypes.WHITE_ASH,
                        x + dx, y + 1 + rng.nextDouble() * 4, z + dz,
                        (rng.nextDouble() - 0.5) * 0.04,
                        (rng.nextDouble() - 0.5) * 0.02,
                        (rng.nextDouble() - 0.5) * 0.04);
            }
        }
    }

    // Zone 5 — blizzard: dense snowflakes driven by strong winds, wall of snow at 12-15 blocks.
    // Frozen Seeker enchantment reduces particle count: -9% per level, level X keeps 10%
    private static void spawnBlizzard(ClientWorld world, double x, double y, double z, Random rng, int frozenLevel) {
        // Multiplier: how many particles to skip based on level
        float chance = 1.0f - 0.09f * Math.min(frozenLevel, 10);

        // Dense field of driven snowflakes around player
        int field = Math.max(1, (int)(10 * chance));
        for (int i = 0; i < field; i++) {
            double dx = (rng.nextDouble() - 0.5) * 24;
            double dy = (rng.nextDouble() - 0.5) * 12;
            double dz = (rng.nextDouble() - 0.5) * 24;
            world.addParticle(ParticleTypes.SNOWFLAKE,
                    x + dx, y + dy, z + dz,
                    0.35 + rng.nextDouble() * 0.15,
                    -rng.nextDouble() * 0.25 - 0.05,
                    (rng.nextDouble() - 0.5) * 0.15);
        }
        // Visibility-wall particles at 12-15 blocks (skipped at higher levels)
        if (frozenLevel < 3) {
            int wall = Math.max(0, (int)(6 * chance));
            for (int i = 0; i < wall; i++) {
                double angle = rng.nextDouble() * Math.PI * 2;
                double r = 12.0 + rng.nextDouble() * 4.0;
                world.addParticle(ParticleTypes.SNOWFLAKE,
                        x + Math.cos(angle) * r,
                        y + (rng.nextDouble() - 0.5) * 10,
                        z + Math.sin(angle) * r,
                        0.35, -0.15, 0.0);
            }
        }
    }
}
