package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.SharkEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class SharkModel extends GeoModel<SharkEntity> {
    @Override
    public Identifier getModelResource(SharkEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "geo/shark.geo.json");
    }

    @Override
    public Identifier getTextureResource(SharkEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "textures/entity/shark.png");
    }

    @Override
    public Identifier getAnimationResource(SharkEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "animations/shark.animation.json");
    }
}
