package net.get900.pixelpirates.mixin;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.trade.PortTraderEntity;
import net.minecraft.client.gui.screen.ingame.MerchantScreen;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The port traders' counter: a MerchantScreen opened right after right-clicking a PortTraderEntity draws from
 * textures/gui/merchant.png (tools/gen_gui_textures.py `merchant()`, vanilla villager2 layout repainted pixel for pixel)
 * instead of the villager's grey panel. Vanilla villagers are untouched. The offer buttons: PressableWidgetSkinMixin.
 */
@Mixin(MerchantScreen.class)
public abstract class MerchantScreenSkinMixin {
    @Unique
    private static final Identifier PP_TEXTURE = new Identifier(PixelPirates.MOD_ID, "textures/gui/merchant.png");
    @Unique
    private boolean pp$skinned;

    @Shadow @org.spongepowered.asm.mixin.Final
    private static Identifier TEXTURE;

    @Inject(method = "init", at = @At("HEAD"))
    private void pp$decide(CallbackInfo ci) {
        pp$skinned = PortTraderEntity.skinRecent();
        net.get900.pixelpirates.client.PortTraderSkin.active = pp$skinned;
    }

    @Redirect(method = {"drawBackground", "drawLevelInfo", "renderScrollbar", "renderArrow", "renderFirstBuyItem"},
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/gui/screen/ingame/MerchantScreen;TEXTURE:Lnet/minecraft/util/Identifier;"))
    private Identifier pp$texture() {
        return pp$skinned ? PP_TEXTURE : TEXTURE;
    }
}
