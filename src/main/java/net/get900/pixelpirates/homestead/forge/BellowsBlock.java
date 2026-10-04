package net.get900.pixelpirates.homestead.forge;

import net.get900.pixelpirates.homestead.furniture.FurnitureBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/** BELLOWS: touching a Forge Hearth they make every forging take 4 hammer strikes instead of 6. Right-click to pump (a puff, the fire flares). */
public class BellowsBlock extends FurnitureBlock {
    public BellowsBlock(Settings s, double[]... boxes) { super(s, true, boxes); }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world instanceof ServerWorld sw) {
            world.playSound(null, pos, SoundEvents.ENTITY_HORSE_BREATHE, SoundCategory.BLOCKS, 1.0f, 0.6f);
            sw.spawnParticles(ParticleTypes.POOF, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.2, 0.1, 0.2, 0.02);
            for (Direction d : Direction.values()) {
                BlockPos h = pos.offset(d);
                if (ForgeHearthBlock.lit(world.getBlockState(h))) {
                    sw.spawnParticles(ParticleTypes.FLAME, h.getX() + 0.5, h.getY() + 1.0, h.getZ() + 0.5, 16, 0.25, 0.15, 0.25, 0.05);
                    world.playSound(null, h, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.BLOCKS, 0.4f, 1.3f);
                }
            }
        }
        return ActionResult.success(world.isClient);
    }
}
