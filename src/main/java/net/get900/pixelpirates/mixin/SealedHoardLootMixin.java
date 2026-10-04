package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.entity.mob.BossHoards;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A sealed boss hoard never rolls its loot for a hopper, an explosion or a player who isn't ready (BossHoards). */
@Mixin(LootableContainerBlockEntity.class)
public abstract class SealedHoardLootMixin {
    @Inject(method = "checkLootInteraction", at = @At("HEAD"), cancellable = true)
    private void pixelpirates$sealed(@Nullable PlayerEntity player, CallbackInfo ci) {
        LootableContainerBlockEntity self = (LootableContainerBlockEntity) (Object) this;
        if (self.getWorld() == null || self.getWorld().isClient) return;
        if (BossHoards.sealedFor(self, player)) ci.cancel();
    }
}
