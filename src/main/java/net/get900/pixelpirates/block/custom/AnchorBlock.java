package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.world.ShipSteeringManager;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.valkyrienskies.core.api.ships.Ship;
import org.valkyrienskies.mod.api.ValkyrienSkies;

/** The ANCHOR (3D model, tools/gen_props_assets.py; faces the way it's placed): on an assembled ship, right-click drops or raises it. */
public class AnchorBlock extends net.minecraft.block.HorizontalFacingBlock {
    private static final net.minecraft.util.shape.VoxelShape SHAPE = Block.createCuboidShape(1, 0, 1, 15, 16, 15);

    public AnchorBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState().with(FACING, net.minecraft.util.math.Direction.NORTH));
    }

    @Override
    protected void appendProperties(net.minecraft.state.StateManager.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getPlacementState(net.minecraft.item.ItemPlacementContext ctx) {
        return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public net.minecraft.util.shape.VoxelShape getOutlineShape(BlockState s, net.minecraft.world.BlockView w, BlockPos p, net.minecraft.block.ShapeContext c) { return SHAPE; }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        Ship ship = ValkyrienSkies.getShipManagingBlock(world, pos.getX(), pos.getY(), pos.getZ());
        if (ship == null) {
            player.sendMessage(Text.literal("§cThis anchor must be on an assembled ship."), true);
            return ActionResult.FAIL;
        }

        long shipId     = ship.getId();
        long currentTick = ((ServerWorld) world).getServer().getTicks();

        if (ShipSteeringManager.ANCHORED_SHIPS.contains(shipId)) {
            Long unanchorAt = ShipSteeringManager.UNANCHOR_TIMERS.get(shipId);
            if (unanchorAt != null) {
                long secsLeft = Math.max(1, (unanchorAt - currentTick) / 20 + 1);
                player.sendMessage(Text.literal("§eRaising anchor in §f" + secsLeft + "§e second" + (secsLeft == 1 ? "" : "s") + "..."), true);
            } else {
                ShipSteeringManager.UNANCHOR_TIMERS.put(shipId, currentTick + 100L);
                player.sendMessage(Text.literal("§e[~] Raising anchor in §f5§e seconds..."), true);
            }
        } else {
            ShipSteeringManager.ANCHORED_SHIPS.add(shipId);
            ShipSteeringManager.UNANCHOR_TIMERS.remove(shipId);
            player.sendMessage(Text.literal("§9[~] Anchor dropped! Right-click again to raise."), true);
        }

        return ActionResult.SUCCESS;
    }
}
