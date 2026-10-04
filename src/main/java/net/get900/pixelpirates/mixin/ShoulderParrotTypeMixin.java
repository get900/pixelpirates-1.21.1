package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.parrot.ParrotTypeHolder;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ShoulderParrotFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.get900.pixelpirates.homestead.parrot.ParrotTypes;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.entity.model.ParrotEntityModel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** PARROT TYPES (client): the shoulder parrot is drawn from its saved data, not an entity - pass its PPType to
 *  ParrotTextureMixin for the length of the draw (method_17958 = the lambda that renders one shoulder parrot). */
@Mixin(ShoulderParrotFeatureRenderer.class)
public abstract class ShoulderParrotTypeMixin {
    @Shadow @Final private ParrotEntityModel model;

    /** Phase 2: a glowing type's glow layer, drawn again in the same shoulder pose right after the parrot itself. */
    @Inject(method = "method_17958", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/render/entity/model/ParrotEntityModel;poseOnShoulder(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;IIFFFFI)V"))
    private void pixelpirates$glow(MatrixStack m, boolean left, PlayerEntity player, NbtCompound nbt, VertexConsumerProvider v, int light,
                                   float a, float b, float c, float d, EntityType<?> type, CallbackInfo ci) {
        ParrotTypes.PType t = ParrotTypes.byId(nbt.getString("PPType"));
        if (t == null || !t.glows()) return;
        model.poseOnShoulder(m, v.getBuffer(RenderLayer.getEyes(t.glowTexture())), 0xF00000, OverlayTexture.DEFAULT_UV, a, b, c, d, player.age);
    }

    @Inject(method = "method_17958", at = @At("HEAD"))
    private void pixelpirates$begin(MatrixStack m, boolean left, PlayerEntity player, NbtCompound nbt, VertexConsumerProvider v, int light,
                                    float a, float b, float c, float d, EntityType<?> type, CallbackInfo ci) {
        String t = nbt.getString("PPType");
        ParrotTypeHolder.SHOULDER_TYPE.set(t.isEmpty() ? null : t);
    }

    @Inject(method = "method_17958", at = @At("RETURN"))
    private void pixelpirates$end(MatrixStack m, boolean left, PlayerEntity player, NbtCompound nbt, VertexConsumerProvider v, int light,
                                  float a, float b, float c, float d, EntityType<?> type, CallbackInfo ci) {
        ParrotTypeHolder.SHOULDER_TYPE.remove();
    }
}
