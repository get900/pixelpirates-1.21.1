package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.tool.RegionTools;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** KRAKEN SCALE tools undo vanilla's two mining penalties: submerged (x1/5) and not standing on ground (x1/5). */
@Mixin(PlayerEntity.class)
public abstract class KrakenMiningMixin {
    @Inject(method = "getBlockBreakingSpeed", at = @At("RETURN"), cancellable = true)
    private void pixelpirates$krakenTools(BlockState state, CallbackInfoReturnable<Float> cir) {
        PlayerEntity self = (PlayerEntity) (Object) this;
        if (RegionTools.of(self.getMainHandStack()) != RegionTools.Kind.KRAKEN) return;
        float f = cir.getReturnValue();
        if (self.isSubmergedIn(FluidTags.WATER) && !EnchantmentHelper.hasAquaAffinity(self)) f *= 5f;
        if (!self.isOnGround()) f *= 5f;
        cir.setReturnValue(f);
    }
}
