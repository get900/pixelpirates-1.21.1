package net.get900.pixelpirates.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.get900.pixelpirates.enchantment.ModEnchantments;
import net.get900.pixelpirates.world.dimension.ModDimensions;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {

    @Inject(method = "applyFog", at = @At("RETURN"))
    private static void pixelpirates_modifyZoneFog(Camera camera,
                                                    BackgroundRenderer.FogType fogType,
                                                    float viewDistance,
                                                    boolean thickFog,
                                                    float tickDelta,
                                                    CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        if (!client.world.getRegistryKey().equals(ModDimensions.PIXEL_PIRATES_WORLD)) return;
        if (net.get900.pixelpirates.world.ZoneEffectsClient.clearSight) return;   // /pptest clearsight

        double x = client.player.getX(), z = client.player.getZ();
        double dist = Math.sqrt(x * x + z * z);

        float ec = net.get900.pixelpirates.client.LeviathanClient.strength;
        if (ec > 0) {                                        // THE ECLIPSE: blood-red, closing in (Leviathan phases 2-3)
            float end = RenderSystem.getShaderFogEnd();
            RenderSystem.setShaderFogStart(0.0f);
            RenderSystem.setShaderFogEnd(end + (Math.min(end, 70f) - end) * ec);
            RenderSystem.setShaderFogColor(0.32f * ec + (1 - ec) * 0.1f, 0.02f, 0.04f, 1.0f);
            return;
        }
        if (net.get900.pixelpirates.client.LeviathanClient.near) return;

        if (dist >= 4500.0) {
            // Zone 5 — blizzard: Frozen Seeker reduces fog density each level
            // Level 0 = 15 blocks, +2.7 per level -> level X = 42 blocks (nearly clear)
            int level = ModEnchantments.getFrozenSeekerLevel(client.player);
            RenderSystem.setShaderFogStart(1.0f);
            RenderSystem.setShaderFogEnd(15.0f + Math.min(level, 10) * 2.7f);
            RenderSystem.setShaderFogColor(0.78f, 0.85f, 0.94f, 1.0f); // icy white-blue
        } else if (dist >= 3500.0) {
            // Zone 4 — dense sea-mist: dark, murky, claustrophobic
            RenderSystem.setShaderFogStart(4.0f);
            RenderSystem.setShaderFogEnd(26.0f);
            RenderSystem.setShaderFogColor(0.10f, 0.12f, 0.18f, 1.0f); // deep grey-blue mist
        }
    }
}
