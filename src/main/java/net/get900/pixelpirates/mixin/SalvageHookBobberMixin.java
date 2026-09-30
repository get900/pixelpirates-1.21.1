package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.fishing.SalvageHookItem;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Vanilla discards a bobber unless its angler holds a vanilla fishing rod; the Salvage Hook counts as one. */
@Mixin(FishingBobberEntity.class)
public abstract class SalvageHookBobberMixin {
    @Redirect(method = "removeIfInvalid", at = @At(value = "INVOKE", target = "Lnet/minecraft/item/ItemStack;isOf(Lnet/minecraft/item/Item;)Z"))
    private boolean pixelpirates$salvageHookIsARod(ItemStack stack, Item item) {
        return stack.isOf(item) || stack.getItem() instanceof SalvageHookItem;
    }
}
