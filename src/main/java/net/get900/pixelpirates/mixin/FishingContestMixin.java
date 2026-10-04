package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.town.FishingContest;
import net.minecraft.entity.projectile.FishingBobberEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.Collection;

/**
 * Finn's fishing contest (homestead/town/FishingContest): the loot a reeled-in bobber hands its angler passes through
 * here just before it flies to them (the FISHING_ROD_HOOKED trigger, ordinal 1 = the loot branch; 0 is a hooked mob).
 */
@Mixin(FishingBobberEntity.class)
public abstract class FishingContestMixin {
    @ModifyArg(method = "use", index = 3, at = @At(value = "INVOKE", ordinal = 1,
            target = "Lnet/minecraft/advancement/criterion/FishingRodHookedCriterion;trigger(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/projectile/FishingBobberEntity;Ljava/util/Collection;)V"))
    private Collection<ItemStack> pixelpirates$weighTheCatch(Collection<ItemStack> loot) {
        FishingBobberEntity self = (FishingBobberEntity) (Object) this;
        if (self.getPlayerOwner() instanceof ServerPlayerEntity p) FishingContest.caught(p, loot);
        return loot;
    }
}
