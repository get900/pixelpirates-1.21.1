package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.client.PortTraderSkin;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.client.gui.widget.PressableWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The port counter's offer buttons: on a skinned MerchantScreen the 88x20 trade buttons draw a parchment card from
 * merchant.png (u 380, v 150 normal / 170 hovered / 190 sold out) instead of the grey vanilla button.
 */
@Mixin(PressableWidget.class)
public abstract class PressableWidgetSkinMixin {
    @Redirect(method = "renderButton", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawNineSlicedTexture(Lnet/minecraft/util/Identifier;IIIIIIIIII)V"))
    private void pp$button(DrawContext ctx, Identifier tex, int x, int y, int w, int h, int a, int b, int c, int d, int u, int v) {
        if (PortTraderSkin.active && w == 88 && h == 20 && MinecraftClient.getInstance().currentScreen instanceof MerchantScreen) {
            int row = v <= 46 ? 2 : v >= 86 ? 1 : 0;                  // vanilla: 46 disabled, 66 normal, 86 hovered
            ctx.drawTexture(PortTraderSkin.TEXTURE, x, y, 380, 150 + row * 20, 88, 20, 512, 256);
            return;
        }
        ctx.drawNineSlicedTexture(tex, x, y, w, h, a, b, c, d, u, v);
    }
}
