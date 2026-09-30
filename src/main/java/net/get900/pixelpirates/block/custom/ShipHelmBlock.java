package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.block.ModBlocks;
import net.get900.pixelpirates.world.PlayerProgressionManager;
import net.get900.pixelpirates.world.ShipRegistryState;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.get900.pixelpirates.world.ShipTiers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.joml.primitives.AABBd;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.valkyrienskies.core.api.ships.ServerShip;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.assembly.ShipAssembler;
import org.valkyrienskies.mod.common.entity.ShipMountingEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.Set;

public class ShipHelmBlock extends Block implements BlockEntityProvider {

    private static final int MAX_SHIP_BLOCKS = 50000;

    /** Which way the wheel faces (towards the player who placed it). Purely visual. */
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 16, 15);

    public ShipHelmBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, Direction.NORTH));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ShipHelmBlockEntity(pos, state);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;
        ServerWorld serverWorld = (ServerWorld) world;

        boolean inShipyard = ValkyrienSkies.isBlockInShipyard(world, pos.getX(), pos.getY(), pos.getZ());

        if (!inShipyard) {
            // Not assembled — handle block counting and assembly
            if (player.isSneaking()) {
                ShipRegistryState reg = ShipRegistryState.get(serverWorld.getServer().getOverworld());
                if (!player.isCreative()) {
                    Long ownedShipId = reg.getOwnedShip(player.getUuid());
                    if (ownedShipId != null) {
                        if (ShipSteeringManager.MAST_COUNTS.containsKey(ownedShipId)) {
                            player.sendMessage(Text.literal(
                                "§cYou already own a ship. Scuttle it first before assembling another."), true);
                            return ActionResult.SUCCESS;
                        }
                        reg.clearOwnership(player.getUuid());
                    }
                }
                List<BlockPos> blocks = findConnectedBlocks(world, pos);
                if (blocks.isEmpty()) {
                    player.sendMessage(Text.literal("No blocks found to assemble."), true);
                    return ActionResult.SUCCESS;
                }
                // Count mast blocks before assembly (they move to shipyard coords after)
                int mastCount = (int) blocks.stream()
                    .filter(p -> world.getBlockState(p).isOf(ModBlocks.SHIP_MAST))
                    .count();
                int effectiveMasts = Math.max(1, mastCount); // helm provides baseline if no masts

                // Scan for waterline block BEFORE assembly — blocks move to ship chunks after
                double avgBlockY = blocks.stream().mapToInt(BlockPos::getY).average().orElse(pos.getY());
                double waterlineTargetY = Double.NaN;
                for (BlockPos bp : blocks) {
                    if (world.getBlockState(bp).isOf(ModBlocks.SHIP_WATERLINE)) {
                        waterlineTargetY = ShipSteeringManager.WATER_Y + avgBlockY - bp.getY();
                        break;
                    }
                }

                // Pre-flight: reject if any existing VS2 ship overlaps the blocks to be assembled.
                // VS2's assembleToShip crashes with IllegalStateException (Collectors.toMap duplicate key)
                // when another ship occupies the same world-space region — even on the server thread.
                {
                    int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
                    int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
                    for (BlockPos bp : blocks) {
                        if (bp.getX() < minX) minX = bp.getX();
                        if (bp.getY() < minY) minY = bp.getY();
                        if (bp.getZ() < minZ) minZ = bp.getZ();
                        if (bp.getX() + 1 > maxX) maxX = bp.getX() + 1;
                        if (bp.getY() + 1 > maxY) maxY = bp.getY() + 1;
                        if (bp.getZ() + 1 > maxZ) maxZ = bp.getZ() + 1;
                    }
                    AABBd aabb = new AABBd(minX - 10, minY - 10, minZ - 10, maxX + 10, maxY + 10, maxZ + 10);
                    if (ValkyrienSkies.getShipsIntersecting(serverWorld, aabb).iterator().hasNext()) {
                        player.sendMessage(Text.literal(
                            "§cCannot assemble: another ship is too close. Move it away first."), true);
                        return ActionResult.SUCCESS;
                    }
                }

                try {
                    ServerShip ship = ShipAssembler.INSTANCE.assembleToShip(world, blocks, true, 1.0, false);
                    ShipSteeringManager.MAST_COUNTS.put(ship.getId(), effectiveMasts);
                    reg.saveMastCount(ship.getId(), effectiveMasts);
                    reg.setOwnership(player.getUuid(), ship.getId());
                    if (!Double.isNaN(waterlineTargetY)) {
                        ShipSteeringManager.WATERLINE_OFFSETS.put(ship.getId(), waterlineTargetY);
                        reg.saveWaterlineOffset(ship.getId(), waterlineTargetY);
                    }
                    player.sendMessage(
                        Text.literal("Assembled! " + blocks.size() + " blocks — " + effectiveMasts + " mast(s)."),
                        true
                    );
                } catch (Exception e) {
                    player.sendMessage(Text.literal("§cAssembly failed: " + e.getMessage()), true);
                    PixelPirates.LOGGER.error("Ship assembly failed at {}", pos, e);
                }
            } else {
                List<BlockPos> blocks = findConnectedBlocks(world, pos);
                player.sendMessage(
                    Text.literal("Ship Helm — " + blocks.size() + " block(s) found. Sneak + right-click to assemble."),
                    true
                );
            }
            return ActionResult.SUCCESS;
        }

        // Block is in a VS2 shipyard — handle helm riding
        if (player.isSneaking()) {
            player.sendMessage(Text.literal("Ship is assembled. Right-click to take the helm."), true);
            return ActionResult.SUCCESS;
        }

        Ship ship = ValkyrienSkies.getShipManagingBlock(world, pos.getX(), pos.getY(), pos.getZ());
        if (ship == null) {
            player.sendMessage(Text.literal("Could not find the ship."), true);
            return ActionResult.SUCCESS;
        }

        // Claim a derelict AI ship — cancel deletion timer, register as player ship
        if (ShipSteeringManager.DERELICT_EXPIRY.containsKey(ship.getId())) {
            ShipRegistryState reg = ShipRegistryState.get(serverWorld.getServer().getOverworld());
            if (!player.isCreative()) {
                Long ownedShipId = reg.getOwnedShip(player.getUuid());
                if (ownedShipId != null) {
                    if (ShipSteeringManager.MAST_COUNTS.containsKey(ownedShipId)) {
                        player.sendMessage(Text.literal(
                            "§cYou already own a ship. Scuttle it first before claiming another."), true);
                        return ActionResult.SUCCESS;
                    }
                    reg.clearOwnership(player.getUuid());
                }
            }
            ShipSteeringManager.DERELICT_EXPIRY.remove(ship.getId());
            ShipSteeringManager.DERELICT_WORLDS.remove(ship.getId());
            int mastCount = ShipSteeringManager.MAST_COUNTS.getOrDefault(ship.getId(), 1);
            reg.saveMastCount(ship.getId(), mastCount);
            reg.setOwnership(player.getUuid(), ship.getId());

            // Award zone unlock for capturing this ship type
            String blueprintName = ShipSteeringManager.DERELICT_BLUEPRINT.remove(ship.getId());
            if (blueprintName != null && player instanceof net.minecraft.server.network.ServerPlayerEntity spe) {
                int zoneUnlock = ShipTiers.getCaptureZoneUnlock(blueprintName);
                if (zoneUnlock > 0) {
                    int currentZone = PlayerProgressionManager.getUnlockedZone(spe);
                    if (currentZone < zoneUnlock) {
                        PlayerProgressionManager.unlockZone(spe, zoneUnlock);
                    }
                }
            }

            player.sendMessage(Text.literal(
                "§aDerelict ship claimed! Use a Repair Kit to stop the sinking."), false);
        }

        // Look for an existing seat entity near the helm block (in ship space)
        Box searchBox = new Box(pos).expand(1.5);
        List<ShipMountingEntity> seats = serverWorld.getEntitiesByClass(
            ShipMountingEntity.class, searchBox, e -> true
        );

        ShipMountingEntity seat;
        if (!seats.isEmpty()) {
            seat = seats.get(0);
            if (seat.hasPassengers()) {
                player.sendMessage(Text.literal("Someone is already at the helm."), true);
                return ActionResult.SUCCESS;
            }
        } else {
            // Spawn a fresh seat entity at the helm's ship-space position
            seat = new ShipMountingEntity(
                ValkyrienSkiesMod.INSTANCE.getSHIP_MOUNTING_ENTITY_TYPE(), world
            );
            seat.refreshPositionAndAngles(
                pos.getX() + 0.5, pos.getY() + 0.3, pos.getZ() + 0.5,
                player.getYaw(), 0f
            );
            seat.setController(true);
            serverWorld.spawnEntity(seat);
        }

        player.startRiding(seat, true);
        player.sendMessage(Text.literal("At the helm! W/S = forward/back  A/D = turn  Shift = stand up"), true);
        return ActionResult.SUCCESS;
    }

    private List<BlockPos> findConnectedBlocks(World world, BlockPos start) {
        List<BlockPos> result = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();

        queue.add(start.toImmutable());
        visited.add(start.asLong());

        while (!queue.isEmpty() && result.size() < MAX_SHIP_BLOCKS) {
            BlockPos current = queue.poll();
            if (world.getBlockState(current).isAir()) continue;

            result.add(current);
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = current.offset(dir);
                long key = neighbor.asLong();
                if (!visited.contains(key) && !world.getBlockState(neighbor).isAir()) {
                    visited.add(key);
                    queue.add(neighbor.toImmutable());
                }
            }
        }

        return result;
    }
}
