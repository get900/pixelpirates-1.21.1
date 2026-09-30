package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.LavaCrabEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class LavaCrabModel extends GeoModel<LavaCrabEntity> {
    @Override
    public Identifier getModelResource(LavaCrabEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "geo/lava_crab.geo.json");
    }

    @Override
    public Identifier getTextureResource(LavaCrabEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "textures/entity/lava_crab.png");
    }

    @Override
    public Identifier getAnimationResource(LavaCrabEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "animations/lava_crab.animation.json");
    }
}
