package net.get900.pixelpirates.homestead;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.homestead.effect.TipsyEffect;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public final class HomesteadEffects {
    private HomesteadEffects() {}

    public static final StatusEffect TIPSY = Registry.register(Registries.STATUS_EFFECT, new Identifier(PixelPirates.MOD_ID, "tipsy"), new TipsyEffect());

    public static void init() {}
}
