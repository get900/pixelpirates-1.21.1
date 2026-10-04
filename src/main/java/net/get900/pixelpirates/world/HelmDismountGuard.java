package net.get900.pixelpirates.world;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.common.VSGameUtilsKt;
import org.valkyrienskies.mod.common.entity.ShipMountingEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Playtest bug 2026-09-30: standing up from the helm sometimes threw the player back to where they took the wheel.
 * The helm seat lives in shipyard space, and while riding, the server's copy of the player never moves with the ship,
 * so the dismount can resolve to that stale spot. For 10 ticks after leaving a helm seat we check the player against
 * the seat's CURRENT world position (ship transform); more than 4 blocks off = put them back beside the helm.
 */
public final class HelmDismountGuard {
    private HelmDismountGuard() {}

    private record Seat(ServerWorld world, Vec3d shipyard, int[] watch) {}
    private static final Map<UUID, Seat> SEATS = new HashMap<>();

    public static void tick(MinecraftServer server) {
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            if (p.getVehicle() instanceof ShipMountingEntity seat && p.getWorld() instanceof ServerWorld sw) {
                SEATS.put(p.getUuid(), new Seat(sw, seat.getPos(), new int[]{10}));
                continue;
            }
            Seat s = SEATS.get(p.getUuid());
            if (s == null) continue;
            if (--s.watch()[0] <= 0 || p.getWorld() != s.world()) { SEATS.remove(p.getUuid()); continue; }
            Vec3d at = worldPos(s.world(), s.shipyard());
            if (at == null) { SEATS.remove(p.getUuid()); continue; }
            if (p.getPos().squaredDistanceTo(at) > 16) {
                p.networkHandler.requestTeleport(at.x, at.y + 0.8, at.z, p.getYaw(), p.getPitch());
                p.fallDistance = 0;
            }
        }
        SEATS.keySet().removeIf(id -> server.getPlayerManager().getPlayer(id) == null);
    }

    /** The seat's position in the world right now (null if it isn't on a loaded ship). */
    private static Vec3d worldPos(ServerWorld sw, Vec3d shipyard) {
        Ship ship = VSGameUtilsKt.getShipManagingPos(sw, BlockPos.ofFloored(shipyard));
        if (ship == null) return null;
        Vector3d w = ship.getTransform().getShipToWorld().transformPosition(new Vector3d(shipyard.x, shipyard.y, shipyard.z), new Vector3d());
        return new Vec3d(w.x, w.y, w.z);
    }
}
