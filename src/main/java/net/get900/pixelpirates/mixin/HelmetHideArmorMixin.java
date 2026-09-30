package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.item.HelmetToggle;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ArmorFeatureRenderer;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** HIDE HELMET: a head armor piece flagged hidden (item/HelmetToggle) is not drawn - vanilla or our GeckoLib 3D armor
 *  (GeckoLib only swaps the model inside this same method, so skipping it covers both). */
@Mixin(ArmorFeatureRenderer.class)
public abstract class HelmetHideArmorMixin<T extends LivingEntity, M extends BipedEntityModel<T>, A extends BipedEntityModel<T>> {
    @Inject(method = "renderArmor", at = @At("HEAD"), cancellable = true)
    private void pixelpirates$hideHelmet(MatrixStack matrices, VertexConsumerProvider vertexConsumers, T entity, EquipmentSlot slot, int light, A model, CallbackInfo ci) {
        if (slot == EquipmentSlot.HEAD && HelmetToggle.hidden(entity.getEquippedStack(EquipmentSlot.HEAD))) ci.cancel();
    }
}
