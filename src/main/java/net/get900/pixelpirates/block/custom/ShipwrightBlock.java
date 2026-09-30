package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.network.ModNetworking;
import net.get900.pixelpirates.world.AiShipController;
import net.get900.pixelpirates.world.PlayerProgressionManager;
import net.get900.pixelpirates.world.ShipHealthState;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.get900.pixelpirates.world.ShipSchematic;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.get900.pixelpirates.world.ShipTiers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3dc;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.internal.world.VsiServerShipWorld;
import org.valkyrienskies.mod.common.VSGameUtilsKt;

import java.util.List;
import java.util.Map;

public class ShipwrightBlock extends Block {

    private static final double SEARCH_RADIUS = 150.0;

    public ShipwrightBlock(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        ServerWorld serverWorld = (ServerWorld) world;
        int playerZone = PlayerProgressionManager.getUnlockedZone((net.minecraft.server.network.ServerPlayerEntity) player);
        List<String> blueprints = ShipTiers.filterForZone(ShipSchematic.listNames(), playerZone);
        long shipId = findNearestPlayerShip(serverWorld, pos);

        ShipRegistryState registry = ShipRegistryState.get(serverWorld.getServer().getOverworld());
        int currentHp    = -1;
        int effectiveMax = -1;
        int mastCount    = -1;
        Map<String, Integer> upgrades = Map.of();

        if (shipId != -1L) {
            ShipHealthState health = ShipHealthState.get(serverWorld);
            currentHp    = health.getHealth(shipId);
            effectiveMax = registry.getEffectiveMaxHp(shipId);
            mastCount    = ShipSteeringManager.MAST_COUNTS.getOrDefault(shipId, 1);
            upgrades     = registry.getShipUpgrades(shipId);
        }

        ModNetworking.sendShipwrightMenu(
            (ServerPlayerEntity) player,
            blueprints, shipId,
            currentHp, effectiveMax, mastCount,
            upgrades
        );
        return ActionResult.SUCCESS;
    }

    private long findNearestPlayerShip(ServerWorld world, BlockPos pos) {
        VsiServerShipWorld sw = VSGameUtilsKt.getShipObjectWorld(world);
        if (sw == null) return -1L;

        double bestDist2 = SEARCH_RADIUS * SEARCH_RADIUS;
        long best = -1L;
        double cx = pos.getX(), cz = pos.getZ();

        for (long id : ShipSteeringManager.MAST_COUNTS.keySet()) {
            if (AiShipController.AI_SHIPS.containsKey(id)) continue;
            ServerShip ship = sw.getLoadedShips().getById(id);
            if (ship == null) continue;
            Vector3dc p = ship.getTransform().getPositionInWorld();
            double dx = p.x() - cx, dz = p.z() - cz;
            double dist2 = dx * dx + dz * dz;
            if (dist2 < bestDist2) { bestDist2 = dist2; best = id; }
        }
        return best;
    }
}
