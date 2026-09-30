package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.CursedMonkeyEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class CursedMonkeyModel extends GeoModel<CursedMonkeyEntity> {
    @Override
    public Identifier getModelResource(CursedMonkeyEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "geo/cursed_monkey.geo.json");
    }

    @Override
    public Identifier getTextureResource(CursedMonkeyEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "textures/entity/cursed_monkey.png");
    }

    @Override
    public Identifier getAnimationResource(CursedMonkeyEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "animations/cursed_monkey.animation.json");
    }
}
