package net.get900.pixelpirates.world;

import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.projectile.SmallFireballEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.Random;

public class ZoneEffectsServer {

    private static final double ZONE3_INNER = 2500.0;
    private static final double ZONE3_OUTER = 3500.0;
    private static final double ZONE4_INNER = 3500.0;
    private static final double ZONE4_OUTER = 4500.0;

    public static void enforce(ServerPlayerEntity player, int serverTick) {
        if (!player.getWorld().getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD)) return;
        if (TestModes.clearSight(player)) return;          // /pptest clearsight

        double x = player.getX(), z = player.getZ();
        double dist = Math.sqrt(x * x + z * z);

        if (dist >= ZONE3_INNER && dist < ZONE3_OUTER) {
            // Falling fireballs every ~4 seconds, with jitter so not all players sync
            if ((serverTick + player.getId() * 7) % 80 == 0) {
                spawnFallingFireballs(player);
            }
        } else if (dist >= ZONE4_INNER && dist < ZONE4_OUTER) {
            // Lightning every ~7-9 seconds with per-player jitter
            if ((serverTick + player.getId() * 13) % 160 == 0) {
                spawnLightning(player);
            }
        }
    }

    // Zone 3 — 1-2 small fireballs fall from above at random positions around the player
    private static void spawnFallingFireballs(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Random rng = player.getRandom();
        int count = 1 + rng.nextInt(2);
        for (int i = 0; i < count; i++) {
            double fx = player.getX() + (rng.nextDouble() - 0.5) * 48;
            double fz = player.getZ() + (rng.nextDouble() - 0.5) * 48;
            double fy = player.getY() + 35 + rng.nextDouble() * 20;
            double vx = (rng.nextDouble() - 0.5) * 0.1;
            double vz = (rng.nextDouble() - 0.5) * 0.1;
            SmallFireballEntity fireball = new SmallFireballEntity(world, fx, fy, fz, vx, -0.9, vz);
            world.spawnEntity(fireball);
        }
    }

    // Zone 4 — cosmetic lightning strike at a random position nearby (not directly on player)
    private static void spawnLightning(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Random rng = player.getRandom();
        // Keep it 10-30 blocks away so it's visible but not always lethal
        double angle = rng.nextDouble() * Math.PI * 2;
        double r = 10.0 + rng.nextDouble() * 20.0;
        double lx = player.getX() + Math.cos(angle) * r;
        double lz = player.getZ() + Math.sin(angle) * r;
        double ly = world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING, (int) lx, (int) lz);

        LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
        lightning.refreshPositionAndAngles(lx, ly, lz, 0, 0);
        world.spawnEntity(lightning);
    }
}
