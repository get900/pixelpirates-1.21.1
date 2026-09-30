package net.get900.pixelpirates.entity.client;

import net.get900.pixelpirates.PixelPirates;
import net.get900.pixelpirates.entity.custom.PirateCrewEntity;
import net.minecraft.util.Identifier;
import software.bernie.geckolib.model.GeoModel;

public class PirateCrewModel extends GeoModel<PirateCrewEntity> {

    @Override
    public Identifier getModelResource(PirateCrewEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "geo/pirate.geo.json");
    }

    @Override
    public Identifier getTextureResource(PirateCrewEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "textures/entity/pirate.png");
    }

    @Override
    public Identifier getAnimationResource(PirateCrewEntity entity) {
        return new Identifier(PixelPirates.MOD_ID, "animations/pirate.animation.json");
    }
}
