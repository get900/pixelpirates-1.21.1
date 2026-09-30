package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.ChestCrabEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class ChestCrabModel extends GeoModel<ChestCrabEntity> {
    @Override
    public Identifier getModelResource(ChestCrabEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "geo/chestcrab.geo.json");
    }

    @Override
    public Identifier getTextureResource(ChestCrabEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "textures/entity/chestcrab.png");
    }

    @Override
    public Identifier getAnimationResource(ChestCrabEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "animations/chestcrab.animation.json");
    }
}
