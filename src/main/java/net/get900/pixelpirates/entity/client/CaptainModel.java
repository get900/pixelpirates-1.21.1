package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.CaptainEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class CaptainModel extends GeoModel<CaptainEntity> {

    @Override
    public Identifier getModelResource(CaptainEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "geo/pirate.geo.json");
    }

    @Override
    public Identifier getTextureResource(CaptainEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "textures/entity/pirate.png");
    }

    @Override
    public Identifier getAnimationResource(CaptainEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "animations/pirate.animation.json");
    }
}
