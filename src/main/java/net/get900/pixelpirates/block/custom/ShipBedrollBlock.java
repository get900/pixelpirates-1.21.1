package net.get900.pixelpirates.block.custom;

import net.get900.pixelpirates.util.AdvancementHelper;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.valkyrienskies.mod.api.ValkyrienSkies;

public class ShipBedrollBlock extends Block {

    private static final VoxelShape SHAPE = Block.createCuboidShape(1, 0, 0, 15, 5, 16);

    public ShipBedrollBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        if (world.isClient) return ActionResult.SUCCESS;

        if (!ValkyrienSkies.isBlockInShipyard(world, pos.getX(), pos.getY(), pos.getZ())) {
            player.sendMessage(Text.literal("§cThis bedroll must be placed on an assembled ship."), true);
            return ActionResult.FAIL;
        }

        ServerPlayerEntity spe = (ServerPlayerEntity) player;
        spe.setSpawnPoint(world.getRegistryKey(), pos, player.getYaw(), true, false);
        player.sendMessage(Text.literal("§a* Ship bedroll set as your respawn point."), true);
        AdvancementHelper.grant(spe, "home_at_sea");
        return ActionResult.SUCCESS;
    }
}
