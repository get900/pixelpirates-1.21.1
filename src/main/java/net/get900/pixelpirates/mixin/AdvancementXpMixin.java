package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.world.PirateXp;
import net.minecraft.advancement.Advancement;
import net.minecraft.advancement.PlayerAdvancementTracker;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Pirate XP for completing a Pixel Pirates advancement. grantCriterion returns true only when a criterion is newly
 * granted, and the advancement is done only after its last one - so this pays exactly once per advancement.
 */
@Mixin(PlayerAdvancementTracker.class)
public abstract class AdvancementXpMixin {
    @Shadow private ServerPlayerEntity owner;

    @Shadow public abstract net.minecraft.advancement.AdvancementProgress getProgress(Advancement advancement);

    @Inject(method = "grantCriterion", at = @At("RETURN"))
    private void pp_advancementXp(Advancement advancement, String criterionName, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && this.owner != null && getProgress(advancement).isDone())
            PirateXp.advancement(this.owner, advancement);
    }
}
