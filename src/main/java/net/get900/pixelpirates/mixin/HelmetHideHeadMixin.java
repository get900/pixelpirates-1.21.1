package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.item.HelmetToggle;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HIDE HELMET for non-armor head items (the Crown of the Drowned, skulls, pumpkins) - item/HelmetToggle. */
@Mixin(HeadFeatureRenderer.class)
public abstract class HelmetHideHeadMixin<T extends LivingEntity> {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void pixelpirates$hideHeadItem(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, T entity,
                                           float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch, CallbackInfo ci) {
        if (HelmetToggle.hidden(entity.getEquippedStack(EquipmentSlot.HEAD))) ci.cancel();
    }
}
