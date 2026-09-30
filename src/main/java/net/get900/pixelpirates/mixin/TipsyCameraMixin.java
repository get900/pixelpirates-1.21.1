package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.homestead.HomesteadEffects;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TIPSY (rum): the view rolls and bobs gently, harder with each drink (amplifier 0-3). */
@Mixin(GameRenderer.class)
public abstract class TipsyCameraMixin {
    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"))
    private void pixelpirates$tipsy(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        StatusEffectInstance e = mc.player.getStatusEffect(HomesteadEffects.TIPSY);
        if (e == null) return;
        float amp = e.getAmplifier() + 1;
        float fade = Math.min(1f, e.getDuration() / 100f);
        float t = (mc.player.age + tickDelta) * 0.045f;
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.sin(t) * 2.2f * amp * fade));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(MathHelper.sin(t * 0.63f + 1.3f) * 0.9f * amp * fade));
    }
}
