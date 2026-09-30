package net.get900.pixelpirates.homestead.hideout;

import net.get900.pixelpirates.homestead.HomesteadBlockEntities;
import net.get900.pixelpirates.world.ShipSteeringManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

/**
 * MOORING POST (#4): an iron-capped bollard for a dock. Use it with a ship alongside (hull within 12 blocks) to MOOR
 * her: she is held like an anchored ship (ShipSteeringManager.ANCHORED_SHIPS - re-asserted every second, so it survives
 * restarts), and the dockyard patches her hull 8 HP every 30 s (16 in your HOME PORT: a post inside a hideout claim).
 * Use again to cast off.
 */
public class MooringPostBlock extends BlockWithEntity {
    private static final VoxelShape SHAPE = Block.createCuboidShape(4, 0, 4, 12, 14, 12);

    public MooringPostBlock(Settings s) { super(s); }

    @Override
    public BlockRenderType getRenderType(BlockState state) { return BlockRenderType.MODEL; }

    @Override
    public VoxelShape getOutlineShape(BlockState s, BlockView w, BlockPos p, ShapeContext c) { return SHAPE; }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) { return new MooringPostBlockEntity(pos, state); }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return world.isClient ? null : checkType(type, HomesteadBlockEntities.MOORING_POST, MooringPostBlockEntity::serverTick);
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (hand != Hand.MAIN_HAND) return ActionResult.PASS;
        if (world.isClient) return ActionResult.SUCCESS;
        if (!(world.getBlockEntity(pos) instanceof MooringPostBlockEntity be)) return ActionResult.PASS;
        if (be.ship() != null) {
            ShipSteeringManager.ANCHORED_SHIPS.remove(be.ship());
            be.setShip(null);
            world.playSound(null, pos, SoundEvents.ENTITY_LEASH_KNOT_BREAK, SoundCategory.BLOCKS, 1f, 0.8f);
            player.sendMessage(Text.literal("[~] Lines cast off.").formatted(Formatting.YELLOW), true);
            return ActionResult.CONSUME;
        }
        Ship ship = nearest((ServerWorld) world, pos);
        if (ship == null) {
            player.sendMessage(Text.literal("No hull alongside - bring a ship within 12 blocks of the post.").formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        be.setShip(ship.getId());
        ShipSteeringManager.ANCHORED_SHIPS.add(ship.getId());
        ShipSteeringManager.UNANCHOR_TIMERS.remove(ship.getId());
        world.playSound(null, pos, SoundEvents.ENTITY_LEASH_KNOT_PLACE, SoundCategory.BLOCKS, 1f, 0.8f);
        player.sendMessage(Text.literal("[~] Moored" + (MooringPostBlockEntity.homePort((ServerWorld) world, pos) ? " in your home port" : "")
                + ". The dockyard will see to her hull.").formatted(Formatting.AQUA), true);
        return ActionResult.CONSUME;
    }

    @Nullable
    static Ship nearest(ServerWorld w, BlockPos pos) {
        org.joml.primitives.AABBd box = new org.joml.primitives.AABBd(pos.getX() - 12, pos.getY() - 8, pos.getZ() - 12, pos.getX() + 13, pos.getY() + 12, pos.getZ() + 13);
        try {
            for (Ship s : ValkyrienSkies.getShipsIntersecting(w, box)) return s;
        } catch (Exception ignored) {
        }
        return null;
    }
}
