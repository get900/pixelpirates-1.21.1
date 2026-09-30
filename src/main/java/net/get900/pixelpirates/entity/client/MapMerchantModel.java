package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.MapMerchantEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class MapMerchantModel extends GeoModel<MapMerchantEntity> {

    @Override
    public Identifier getModelResource(MapMerchantEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "geo/map_merchant.geo.json");
    }

    @Override
    public Identifier getTextureResource(MapMerchantEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "textures/entity/map_merchant.png");
    }

    @Override
    public Identifier getAnimationResource(MapMerchantEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "animations/map_merchant.animation.json");
    }
}
