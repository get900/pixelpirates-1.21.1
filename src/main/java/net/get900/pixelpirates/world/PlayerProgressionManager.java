package net.get900.pixelpirates.world;

import net.get900.pixelpirates.item.ModItems;
import net.get900.pixelpirates.util.AdvancementHelper;
import net.get900.pixelpirates.util.PlayerProgressionComponent;
import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Set;

public class PlayerProgressionManager {

    // Outer block radius of each zone (walls sit at these boundaries)
    private static final int[] ZONE_RADII = {500, 1500, 2500, 3500, 4500};

    public static boolean hasRadar(PlayerEntity player) {
        return ((PlayerProgressionComponent) player).pp_hasRadar();
    }

    public static void unlockRadar(ServerPlayerEntity player) {
        ((PlayerProgressionComponent) player).pp_setHasRadar(true);
    }

    public static int getUnlockedZone(PlayerEntity player) {
        return ((PlayerProgressionComponent) player).pp_getUnlockedZone();
    }

    public static int getZoneKills(PlayerEntity player, int zone) {
        return ((PlayerProgressionComponent) player).pp_getZoneKills(zone);
    }

    public static void addZoneKill(PlayerEntity player, int zone) {
        ((PlayerProgressionComponent) player).pp_addZoneKill(zone);
    }

    public static void unlockZone(ServerPlayerEntity player, int zone) {
        int current = getUnlockedZone(player);
        if (zone > current) {
            ((PlayerProgressionComponent) player).pp_setUnlockedZone(zone);
            player.sendMessage(Text.literal("§aZone " + zone + " unlocked! Safe travels, pirate."), false);
            switch (zone) {
                case 1 -> AdvancementHelper.grant(player, "into_the_shallows");
                case 2 -> AdvancementHelper.grant(player, "beyond_the_reef");
                case 3 -> AdvancementHelper.grant(player, "the_pirate_seas");
                case 4 -> AdvancementHelper.grant(player, "the_cursed_seas");
                case 5 -> AdvancementHelper.grant(player, "the_abyss");
            }
        }
    }

    /** Returns which numbered zone (0=hub, 1-5=outer zones) the player is standing in. */
    public static int getZoneAt(Vec3d pos) {
        double dist = Math.sqrt(pos.x * pos.x + pos.z * pos.z);
        if (dist < ZONE_RADII[0]) return 0;
        for (int i = 0; i < ZONE_RADII.length - 1; i++) {
            if (dist < ZONE_RADII[i + 1]) return i + 1;
        }
        return 5;
    }

    /** Called every server tick cycle. Enforces zone access restrictions. */
    public static void enforce(ServerPlayerEntity player) {
        if (!player.getWorld().getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD)) return;

        int currentZone = getZoneAt(player.getPos());
        int unlocked = getUnlockedZone(player);

        if (currentZone <= unlocked) return;

        if (currentZone == 1) {
            // Zone 1 gate: must commission a ship at the port first
            applyWaveEffect(player, "§9Commission a ship at the port — you can't brave the open seas alone!");
        } else if (currentZone == 2) {
            // Zone 2 uses wave deterrent — Seafarer's Token grants temporary access
            if (!hasSeafarersToken(player)) {
                applyWaveEffect(player, "§9Violent waves push you back!");
            }
        } else {
            // Zones 3+ still teleport back
            pushBackToZone(player, unlocked);
        }
    }

    public static boolean hasSeafarersToken(ServerPlayerEntity player) {
        for (ItemStack stack : player.getInventory().main) {
            if (stack.isOf(ModItems.SEAFARERS_TOKEN)) return true;
        }
        if (player.getOffHandStack().isOf(ModItems.SEAFARERS_TOKEN)) return true;
        return false;
    }

    private static void applyWaveEffect(ServerPlayerEntity player, String message) {
        Vec3d pos = player.getPos();
        double dist = Math.sqrt(pos.x * pos.x + pos.z * pos.z);
        if (dist < 1.0) dist = 1.0;
        double nx = -(pos.x / dist);
        double nz = -(pos.z / dist);

        player.setVelocity(nx * 1.8, 0.5, nz * 1.8);
        player.velocityModified = true;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NAUSEA, 80, 0, false, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1, false, false));

        player.sendMessage(Text.literal(message), true);
    }

    private static void pushBackToZone(ServerPlayerEntity player, int safeZone) {
        Vec3d pos = player.getPos();
        double angle = Math.atan2(pos.z, pos.x);

        // Target radius = center of the safe zone
        int targetRadius;
        if (safeZone == 0) {
            targetRadius = ZONE_RADII[0] - 20; // inside hub
        } else {
            int innerEdge = ZONE_RADII[safeZone - 1];
            int outerEdge = ZONE_RADII[safeZone];
            targetRadius = (innerEdge + outerEdge) / 2; // midpoint of safe zone
        }

        double newX = Math.cos(angle) * targetRadius;
        double newZ = Math.sin(angle) * targetRadius;
        ServerWorld world = player.getServerWorld();
        player.teleport(world, newX, player.getY(), newZ, Set.of(), player.getYaw(), player.getPitch());
        player.sendMessage(Text.literal("§cThis zone is locked. Complete the current zone's challenge first!"), true);
    }
}
