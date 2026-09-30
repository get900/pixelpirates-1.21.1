package net.get900.pixelpirates.mixin;

import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FULL BRIGHTNESS for testing and screenshots: while /pptest clearsight (or /pptest all) is on, the lightmap is
 * painted solid white after vanilla builds it, so every block renders fully lit - caves, interiors, night.
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapFullbrightMixin {
    @Shadow @Final private NativeImageBackedTexture texture;
    @Shadow @Final private NativeImage image;

    @Inject(method = "update", at = @At("TAIL"))
    private void pixelpirates$fullbright(float delta, CallbackInfo ci) {
        if (!net.get900.pixelpirates.world.ZoneEffectsClient.clearSight) return;
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) this.image.setColor(x, y, 0xFFFFFFFF);
        this.texture.upload();
    }
}
