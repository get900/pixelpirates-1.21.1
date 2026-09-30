package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.tool.RegionTools;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** Region tool perks on block drops: EMBER tools smelt what they mine; BONE tools shed a little soul XP. */
@Mixin(Block.class)
public abstract class HomesteadDropsMixin {
    @Inject(method = "getDroppedStacks(Lnet/minecraft/block/BlockState;Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/entity/BlockEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/item/ItemStack;)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void pixelpirates$regionTools(BlockState state, ServerWorld world, BlockPos pos, BlockEntity be, Entity entity, ItemStack tool,
                                                 CallbackInfoReturnable<List<ItemStack>> cir) {
        RegionTools.Kind k = RegionTools.of(tool);
        if (k == RegionTools.Kind.EMBER) {
            List<ItemStack> smelted = RegionTools.smelt(world, cir.getReturnValue());
            boolean changed = false;
            for (int i = 0; i < smelted.size(); i++) changed |= smelted.get(i).getItem() != cir.getReturnValue().get(i).getItem();
            if (changed) world.spawnParticles(ParticleTypes.FLAME, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.25, 0.25, 0.25, 0.01);
            cir.setReturnValue(smelted);
        } else if (k == RegionTools.Kind.BONE && world.random.nextInt(5) == 0) {
            ExperienceOrbEntity.spawn(world, Vec3d.ofCenter(pos), 1 + world.random.nextInt(2));
            world.spawnParticles(ParticleTypes.SOUL, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.2, 0.2, 0.2, 0.01);
        }
    }
}
