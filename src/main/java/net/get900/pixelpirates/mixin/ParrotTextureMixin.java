package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.parrot.ParrotTypeHolder;
import net.get900.pixelpirates.homestead.parrot.ParrotTypes;
import net.minecraft.client.render.entity.ParrotEntityRenderer;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** PARROT TYPES (client): a parrot with a custom type is drawn with that type's texture - in the world, and on a shoulder
 *  (ShoulderParrotTypeMixin sets SHOULDER_TYPE around the shoulder renderer's static getTexture(Variant) call). */
@Mixin(ParrotEntityRenderer.class)
public abstract class ParrotTextureMixin {
    @Inject(method = "getTexture(Lnet/minecraft/entity/passive/ParrotEntity;)Lnet/minecraft/util/Identifier;", at = @At("HEAD"), cancellable = true)
    private void pixelpirates$typeTexture(ParrotEntity p, CallbackInfoReturnable<Identifier> cir) {
        ParrotTypes.PType t = ParrotTypes.byId(((ParrotTypeHolder) p).pixelpirates$getParrotType());
        if (t != null && t.custom()) cir.setReturnValue(t.texture());
    }

    @Inject(method = "getTexture(Lnet/minecraft/entity/passive/ParrotEntity$Variant;)Lnet/minecraft/util/Identifier;", at = @At("HEAD"), cancellable = true)
    private static void pixelpirates$shoulderTexture(ParrotEntity.Variant v, CallbackInfoReturnable<Identifier> cir) {
        String id = ParrotTypeHolder.SHOULDER_TYPE.get();
        ParrotTypes.PType t = id == null ? null : ParrotTypes.byId(id);
        if (t != null && t.custom()) cir.setReturnValue(t.texture());
    }
}
