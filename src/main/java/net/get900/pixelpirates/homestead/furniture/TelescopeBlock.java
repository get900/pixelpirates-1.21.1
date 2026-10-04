package net.get900.pixelpirates.homestead.furniture;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** TELESCOPE: a brass tube on a tripod - it points the way you look when you place it. Use it to look through it (homestead/nav/Telescopes). */
public class TelescopeBlock extends FurnitureBlock {
    public TelescopeBlock(Settings s, double[]... boxes) { super(s, true, boxes); }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) { return getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing()); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (player.isSneaking()) return ActionResult.PASS;
        if (player instanceof ServerPlayerEntity sp) net.get900.pixelpirates.homestead.nav.Telescopes.start(sp, pos);
        return ActionResult.success(world.isClient);
    }
}
