package net.get900.pixelpirates.client;

import net.get900.pixelpirates.PixelPirates;
import net.minecraft.util.Identifier;

/** Client state of the port-trader counter skin (mixin/MerchantScreenSkinMixin + PressableWidgetSkinMixin). */
public final class PortTraderSkin {
    public static final Identifier TEXTURE = new Identifier(PixelPirates.MOD_ID, "textures/gui/merchant.png");
    /** Set when a MerchantScreen initialises: true = it was opened from a port trader. */
    public static boolean active;

    private PortTraderSkin() { }
}
