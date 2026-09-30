package net.get900.pixelpirates.world;

import net.get900.pixelpirates.block.ModBlocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.joml.primitives.AABBd;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ShipSpawner {

    public static ServerShip spawn(ServerWorld world, ShipSchematic schematic, BlockPos origin) throws Exception {
        // Pre-flight: reject if any loaded VS2 ship's world-space AABB overlaps the schematic
        // placement volume. VS2's assembleToShip fails with IllegalStateException (Collectors.toMap
        // duplicate key) when it finds blocks that are simultaneously registered to an existing
        // ship and to the new ship — this happens because VS2's getBlockState mixin serves moving
        // ship blocks at their current world-space positions, making them "virtual" world blocks.
        // getShipManagingBlock checks by chunk (x>>4, z>>4) and misses world chunks, so we must
        // use the AABB intersection API instead.
        {
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            for (ShipSchematic.Entry entry : schematic.getEntries()) {
                BlockPos wp = origin.add(entry.relPos());
                if (wp.getX() < minX) minX = wp.getX();
                if (wp.getY() < minY) minY = wp.getY();
                if (wp.getZ() < minZ) minZ = wp.getZ();
                if (wp.getX() + 1 > maxX) maxX = wp.getX() + 1;
                if (wp.getY() + 1 > maxY) maxY = wp.getY() + 1;
                if (wp.getZ() + 1 > maxZ) maxZ = wp.getZ() + 1;
            }
            // Expand by 10 blocks to absorb VS2 physics-thread movement between this check
            // and the assembleToShip call (VS2 runs physics concurrently with the server thread).
            AABBd spawnBox = new AABBd(minX - 50, minY - 50, minZ - 50, maxX + 50, maxY + 50, maxZ + 50);
            if (ValkyrienSkies.getShipsIntersecting(world, spawnBox).iterator().hasNext()) {
                throw new Exception("Spawn location overlaps an existing ship — try a different position");
            }
        }

        // Layer 2 — per-block pre-scan using VS2's own getBlockState mixin.
        // VS2 intercepts world.getBlockState and returns the ship block's BlockState at its current
        // world-space position. Scanning every target position here catches any ship that entered
        // the area between the AABB check above and this call (the physics-thread race window).
        // We run this BEFORE placing anything, so no cleanup is needed on rejection.
        for (ShipSchematic.Entry entry : schematic.getEntries()) {
            BlockPos worldPos = origin.add(entry.relPos());
            BlockState existing = world.getBlockState(worldPos);
            // Air is safe. Fluids (water) are safe to overwrite. Any solid block at this altitude
            // over open ocean is almost certainly a VS2 virtual ship block — abort immediately.
            if (!existing.isAir() && existing.getFluidState().isEmpty()) {
                throw new Exception("Pre-scan: " + worldPos + " occupied (" +
                    existing.getBlock() + ") — VS2 ship block or terrain in spawn footprint");
            }
        }

        ServerShip ship;

        // VS2's getBlockState mixin intercepts world.setBlockState and calls its internal tracking
        // update (onSetBlock) synchronously on the server thread. If an existing ship's block is
        // "virtually" at the target position (because VS2 serves ship blocks at their current
        // world-space positions), VS2 CORE throws IllegalStateException (Collectors.toMap duplicate
        // key) during the setBlockState call — before we even reach assembleToShip. Wrapping only
        // assembleToShip was insufficient; the entire placement loop must be inside the catch.
        List<BlockPos> placed = new ArrayList<>();
        double sumY = 0;
        BlockPos waterlinePos = null;

        try {
            for (ShipSchematic.Entry entry : schematic.getEntries()) {
                BlockPos worldPos = origin.add(entry.relPos());
                BlockState state = ShipSchematic.restoreState(entry.stateNbt());
                if (state.isAir()) continue;
                world.setBlockState(worldPos, state, 3);
                placed.add(worldPos.toImmutable());
                sumY += worldPos.getY();
                if (waterlinePos == null && state.isOf(ModBlocks.SHIP_WATERLINE)) {
                    waterlinePos = worldPos.toImmutable();
                }
            }

            if (placed.isEmpty()) throw new Exception("Schematic has no valid blocks");

            // Deduplicate as a safety net
            List<BlockPos> uniquePlaced = new ArrayList<>(new LinkedHashSet<>(placed));
            ship = ShipAssembler.INSTANCE.assembleToShip(world, uniquePlaced, true, 1.0, false);

        } catch (IllegalStateException e) {
            // Clean up every block we managed to place before the crash. Blocks that VS2 already
            // moved to ship-space during a partial assembleToShip are already air in world-space,
            // so the isAir guard makes those setBlockState calls no-ops.
            for (BlockPos pos : placed) {
                if (!world.getBlockState(pos).isAir()) {
                    world.setBlockState(pos, Blocks.AIR.getDefaultState(), Block.NOTIFY_ALL);
                }
            }
            throw new Exception("Ship assembly failed (VS2 overlap — ship in spawn area)", e);
        }
        int effectiveMasts = Math.max(1, schematic.getMastCount());
        ShipSteeringManager.MAST_COUNTS.put(ship.getId(), effectiveMasts);

        if (waterlinePos != null) {
            double avgY = sumY / placed.size();
            double targetY = ShipSteeringManager.WATER_Y + avgY - waterlinePos.getY();
            ShipSteeringManager.WATERLINE_OFFSETS.put(ship.getId(), targetY);
            ShipRegistryState.get(world.getServer().getOverworld()).saveWaterlineOffset(ship.getId(), targetY);
        }

        return ship;
    }
}
